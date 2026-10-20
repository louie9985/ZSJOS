#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Read target MySQL configuration and generate an enrollment XLSX. Standard library only."""
import argparse
from collections import Counter
from datetime import datetime, timezone, timedelta
import getpass
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import zipfile
import xml.etree.ElementTree as ET
from xml.sax.saxutils import escape, quoteattr

NS = 'http://schemas.openxmlformats.org/spreadsheetml/2006/main'
SCENES = ('registration_manager_study_planner', 'registration_specialist_study_planner',
          'study_planner_content_director', 'study_planner_career_planner', 'content_director_operator')

def query(table, pairs, where, order='id'):
    args = ','.join("'%s',%s" % (key, expression) for key, expression in pairs.items())
    return 'SELECT JSON_OBJECT(%s) FROM %s WHERE %s ORDER BY %s' % (args, table, where, order)

def queries():
    tenant = 'tenant_id=1 AND deleted=0'
    active = tenant + ' AND status=0'
    scenes = ','.join("'%s'" % x for x in SCENES)
    return {
        'products': query('zsjos_product', dict(id='id', name='name', categoryId='category_id'), active, 'sort,id'),
        'categories': query('zsjos_product_category', dict(id='id', name='name', parentId='parent_id', status='status'), tenant, 'sort,id'),
        'skus': query('zsjos_product_sku', dict(id='id', productId='spu_id', name='sku_name', attrs='attr_values_json'), active, 'spu_id,sort,id'),
        'attrs': query('zsjos_product_attr', dict(id='id', productId='spu_id', key='attr_key', name='attr_name'), active, 'spu_id,sort,id'),
        'attrValues': query('zsjos_product_attr_value', dict(attrId='attr_id', value='value', label='label'), active, 'attr_id,sort,id'),
        'users': query('system_users', dict(id='id', name='nickname', username='username'), active),
        'relations': query('zsjos_user_relation', dict(scene='scene', sourceId='source_user_id', targetId='target_user_id'), active + ' AND scene IN (' + scenes + ')', 'scene,source_user_id,target_user_id'),
        'classes': query('zsjos_delivery_class', dict(id='id', name='class_name', number='class_no', system='system_class+0', categoryId='category_id', productId='product_id'), tenant + " AND status='SERVING'", 'class_name,id'),
        'areas': query('system_area', dict(id='id', name='name', parentId='parent_id', type='type', leaf='leaf_selectable+0'), 'deleted=0 AND status=0', 'sort,id'),
        'configs': query('zsjos_student_info_form_config', dict(version='version_no', status='status', fields='CAST(fields_json AS JSON)'), tenant + " AND status='PUBLISHED'", 'version_no DESC'),
        'dicts': query('system_dict_data d JOIN system_dict_type t ON t.type=d.dict_type', dict(type='d.dict_type', value='d.value', label='d.label'), 'd.deleted=0 AND d.status=0 AND t.deleted=0 AND t.status=0', 'd.dict_type,d.sort,d.id'),
    }

def sql_snapshot():
    # All reads share one snapshot; no data-changing SQL and no mysql --force.
    statements = ['SET NAMES utf8mb4', 'SET SESSION TRANSACTION ISOLATION LEVEL REPEATABLE READ',
                  'START TRANSACTION WITH CONSISTENT SNAPSHOT, READ ONLY']
    for key, sql in queries().items():
        statements += ["SELECT 'SECTION:%s'" % key, sql]
    return ';\n'.join(statements + ['COMMIT']) + ';\n'

def fetch(args):
    client = []
    if args.defaults_file:
        client.append('--defaults-extra-file=' + str(Path(args.defaults_file).resolve()))
    elif not args.docker_container and (not args.host or not args.user):
        raise ValueError('Provide --defaults-file, or explicit --host and --user.')
    for key in ('host', 'user', 'port'):
        if getattr(args, key) is not None:
            client.append('--%s=%s' % (key, getattr(args, key)))
    client += ['--default-character-set=utf8mb4', '--batch', '--raw', '--skip-column-names',
               '--connect-timeout=10', '--database=' + args.database]
    env = os.environ.copy()
    if args.password_file:
        env['MYSQL_PWD'] = Path(args.password_file).read_text(encoding='utf-8').rstrip('\r\n')
    if args.ask_password:
        env['MYSQL_PWD'] = getpass.getpass('MySQL password: ')
    if args.docker_container:
        if not args.container_user:
            raise ValueError('--docker-container requires --container-user.')
        if args.defaults_file or args.password_file or args.ask_password:
            raise ValueError('Docker mode uses the existing container password environment variable only.')
        if not args.container_password_env.replace('_', '').isalnum():
            raise ValueError('Invalid container password environment variable name.')
        shell = 'export MYSQL_PWD="$(printenv "$1")"; shift; exec mysql "$@"'
        cmd = ['docker', 'exec', '-i', args.docker_container, 'sh', '-c', shell,
               'template-reader', args.container_password_env, '--user=' + args.container_user] + client
    else:
        cmd = [args.mysql] + client
    result = subprocess.run(cmd, input=sql_snapshot().encode('utf-8'), capture_output=True, env=env, timeout=args.timeout)
    if result.returncode:
        raise ValueError('MySQL read failed. Check connection, SELECT permissions and target schema. No template generated.')
    data, current = {}, None
    for line in result.stdout.decode('utf-8', errors='strict').splitlines():
        if line.startswith('SECTION:'):
            current = line[8:]; data[current] = []
        elif line:
            if current is None: raise ValueError('Unexpected MySQL output.')
            data[current].append(json.loads(line))
    if set(data) != set(queries()): raise ValueError('Incomplete database snapshot.')
    return data

def col(i):
    result = ''
    while i:
        i, rem = divmod(i - 1, 26); result = chr(65 + rem) + result
    return result

def xtext(value):
    value = str(value)
    if len(value) > 32767 or any(ord(c) < 32 and c not in '\t\r\n' for c in value):
        raise ValueError('Unsupported character or oversized cell in source data.')
    return escape(value)

class Workbook:
    """Small OOXML writer scoped to this portable template, without Excel dependencies."""
    def __init__(self):
        self.sheets = []; self.names = {}
    def sheet(self, name, headers, rows, widths=None, hidden=False):
        item = dict(name=name, headers=headers, rows=rows, widths=widths or [28]*len(headers),
                    hidden=hidden, cells={}, validations=[], conditions=[], input=False)
        self.sheets.append(item)
        return item
    def define(self, name, sheet, column, start, end):
        self.names[name] = "'%s'!$%s$%s:$%s$%s" % (sheet, column, start, column, end)
    def dropdown(self, sheet, target, formula):
        sheet['validations'].append((target, 'list', formula, '请从下拉选择有效选项'))
    def blank_only(self, sheet, target):
        sheet['validations'].append((target, 'custom', 'LEN(%s)=0' % target.split(':')[0], '当前无有效配置，只能留空'))
    def xml_sheet(self, s):
        total_rows, total_cols = len(s['rows'])+1, len(s['headers'])
        pane = '<pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/>'
        if s['input']: pane = '<pane xSplit="7" ySplit="1" topLeftCell="H2" activePane="bottomRight" state="frozen"/>'
        parts = ['<?xml version="1.0" encoding="UTF-8" standalone="yes"?>', '<worksheet xmlns="%s">' % NS,
                 '<dimension ref="A1:%s%s"/>' % (col(total_cols), total_rows),
                 '<sheetViews><sheetView showGridLines="0" workbookViewId="0">'+pane+'</sheetView></sheetViews>',
                 '<sheetFormatPr defaultRowHeight="24"/><cols>']
        for i, width in enumerate(s['widths'], 1):
            parts.append('<col min="%d" max="%d" width="%s" customWidth="1"/>' % (i, i, width))
        parts.append('</cols><sheetData>')
        for r, values in enumerate([s['headers']] + s['rows'], 1):
            height = 45 if r == 1 else (27 if s['input'] or s.get('config') else 54)
            parts.append('<row r="%d" ht="%d" customHeight="1">' % (r, height))
            for c in range(1, total_cols+1):
                value = values[c-1] if c <= len(values) else None
                style = 3 if r == 1 else 1
                value, style, formula = s['cells'].get((r,c), (value, style, None)); addr = col(c)+str(r)
                if formula is not None:
                    cached=s.get('cached',{}).get((r,c),'')
                    parts.append('<c r="%s" s="%d" t="str"><f>%s</f><v>%s</v></c>' % (addr, style, xtext(formula),xtext(cached)))
                elif value is None: parts.append('<c r="%s" s="%d"/>' % (addr, style))
                else:
                    # Admin labels remain literal, even if they begin with '='.
                    parts.append('<c r="%s" s="%d" t="inlineStr"><is><t xml:space="preserve">%s</t></is></c>' % (addr, style, xtext(value)))
            parts.append('</row>')
        parts.append('</sheetData>')
        if s['input'] or s.get('config'): parts.append('<autoFilter ref="A1:%s%s"/>' % (col(total_cols),total_rows))
        for priority,(target,formula) in enumerate(s['conditions'],1):
            parts.append('<conditionalFormatting sqref=%s><cfRule type="expression" dxfId="0" priority="%s"><formula>%s</formula></cfRule></conditionalFormatting>' % (quoteattr(target),priority,xtext(formula)))
        if s['validations']:
            parts.append('<dataValidations count="%s">' % len(s['validations']))
            for target,kind,formula,message in s['validations']:
                parts.append('<dataValidation type=%s allowBlank="1" errorStyle="stop" showErrorMessage="1" errorTitle="填写值无效" error=%s sqref=%s><formula1>%s</formula1></dataValidation>' % (quoteattr(kind),quoteattr(message),quoteattr(target),xtext(formula)))
            parts.append('</dataValidations>')
        return ''.join(parts) + '<pageMargins left="0.3" right="0.3" top="0.4" bottom="0.4" header="0.2" footer="0.2"/></worksheet>'

    def save(self, output):
        styles = '<styleSheet xmlns="'+NS+'">'
        styles += '<numFmts count="2"><numFmt numFmtId="164" formatCode="yyyy-mm-dd hh:mm"/><numFmt numFmtId="165" formatCode="yyyy-mm-dd"/></numFmts><fonts count="4">'
        for red,bold in [(False,False),(True,False),(False,True),(True,True)]:
            styles += '<font>'+('<b/>' if bold else '')+'<sz val="11"/><name val="Microsoft YaHei"/><color rgb="'+('FFC00000' if red else 'FF000000')+'"/></font>'
        styles += '</fonts><fills count="5"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill>'
        for color in ['FFE8EEF5','FFE7E6E6','FFE7EEF4']:
            styles += '<fill><patternFill patternType="solid"><fgColor rgb="'+color+'"/><bgColor indexed="64"/></patternFill></fill>'
        styles += '</fills><borders count="2"><border/><border>'
        for edge in ['left','right','top','bottom']: styles += '<'+edge+' style="thin"><color rgb="FFB8C2CC"/></'+edge+'>'
        styles += '</border></borders><cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs><cellXfs count="9">'
        for number,font,fill,border in [(0,0,0,0),(49,0,0,1),(49,1,0,1),(49,2,2,1),(49,3,2,1),(49,0,3,1),(0,0,4,1),(164,0,0,1),(165,0,0,1)]:
            styles += '<xf numFmtId="%s" fontId="%s" fillId="%s" borderId="%s" xfId="0" applyAlignment="1" applyNumberFormat="1"><alignment vertical="center" wrapText="1"/></xf>' % (number,font,fill,border)
        styles += '</cellXfs><cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles><dxfs count="1"><dxf><fill><patternFill patternType="solid"><fgColor rgb="FFFFC7CE"/><bgColor indexed="64"/></patternFill></fill></dxf></dxfs></styleSheet>'
        workbook = '<workbook xmlns="%s" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>' % NS
        rels = '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">'
        content = '<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>'
        for i,s in enumerate(self.sheets,1):
            workbook += '<sheet name=%s sheetId="%s" r:id="rId%s"%s/>' % (quoteattr(s['name']),i,i,' state="hidden"' if s['hidden'] else '')
            rels += '<Relationship Id="rId%s" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet%s.xml"/>' % (i,i)
            content += '<Override PartName="/xl/worksheets/sheet%s.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>' % i
        workbook += '</sheets><definedNames>'+''.join('<definedName name=%s>%s</definedName>'%(quoteattr(k),xtext(v)) for k,v in self.names.items())+'</definedNames><calcPr calcId="191029" fullCalcOnLoad="1" forceFullCalc="1"/></workbook>'
        rels += '<Relationship Id="styles" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>'
        root = '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>'
        with zipfile.ZipFile(output,'w',zipfile.ZIP_DEFLATED) as z:
            for path,value in {'[Content_Types].xml':content+'</Types>', '_rels/.rels':root, 'xl/workbook.xml':workbook,'xl/_rels/workbook.xml.rels':rels,'xl/styles.xml':styles}.items(): z.writestr(path,value.encode('utf-8'))
            for i,s in enumerate(self.sheets,1): z.writestr('xl/worksheets/sheet%s.xml'%i,self.xml_sheet(s).encode('utf-8'))

FIELDS = [
 ('姓名',1),('手机号码',2),('微信号',2),('课程名称',1),('课程规格名称',2),('班级名称',1),('学习规划师姓名',1),
 ('性别',0),('身份证号码',0),('年龄',0),('民族',0),('户籍所在地',0),('是否复训',0),
 ('现学历',0),('毕业院校',0),('毕业专业',0),('毕业时间',0),('报名层次',0),('拟报名院校',0),('拟报名专业',0),
 ('工作单位',0),('从事职业',0),('目前持有证书',0),('拟报名学习的目的',0),
 ('产品分类路径',0),('课程规格说明',0),('课程属性说明',0),
 ('编导姓名',0),('生涯规划师姓名',0),('运营姓名',0),('服务开始时间',0),('考试日期',0),
 ('证件照片',0),('身份证正面',0),('身份证反面',0),('毕业证',0),('学历认证报备',0),('报名申请表',0),
 ('工作证明',0),('社保流水',0),('劳动合同',0),('前置证书',0),('前置证书的网查截图',0),('承诺书',0)]
DICT_FIELDS = [('性别','gender','system_user_sex'),('民族','ethnicity',None),('是否复训','retraining',None),('现学历','education_level',None),('报名层次','registration_level',None)]

def decoded(value):
    return json.loads(value) if isinstance(value,str) else value

def build(data, rows=200, actor_id=None, extra_class_names=None):
    w=Workbook(); warnings=[]; mappings=[]; end=rows+1
    sheet=w.sheet('学员导入填写',[n+(' *' if r==1 else ' †' if r==2 else '') for n,r in FIELDS],[[None]*len(FIELDS) for _ in range(rows)],[23]*len(FIELDS));sheet['input']=True
    index={n:i for i,(n,_) in enumerate(FIELDS,1)}
    refs={n:col(i) for n,i in index.items()}
    ranges={n:refs[n]+'2:'+refs[n]+str(end) for n in index}
    rules={n:'选填，留空不影响基础建档。' for n in index}
    for i,(name,required) in enumerate(FIELDS,1):
        sheet['cells'][1,i]=(sheet['headers'][i-1],4 if required else 3,None)
        for r in range(2,end+1):sheet['cells'][r,i]=(None,2 if required else 1,None)
    for name,style in [('服务开始时间',7),('考试日期',8)]:
        for r in range(2,end+1):sheet['cells'][r,index[name]]=(None,style,None)
    rules.update({'姓名':'必填，真实姓名，最多100字符。','手机号码':'与微信至少填写一项；11位大陆手机号，用文本保存。','微信号':'与手机至少填写一项；最多64字符。','身份证号码':'选填，填写时校验18位身份证；不单独凭身份证合并Person。', '年龄':'选填，可由身份证及实际提交日期计算。','服务开始时间':'选填，已知历史时间照实填写；为空时由导入预检明确生成时间。','考试日期':'选填，yyyy-mm-dd。','毕业时间':'选填，按现有业务契约保留文本，例如2020-06。'})
    for name,_ in FIELDS[32:]:rules[name]='选填，填写随附材料包中的相对路径，多张用分号分隔；仅JPG/JPEG/PNG。不能用本机绝对路径或带令牌链接。'
    options=w.sheet('选择数据',['选项名称（请勿修改）'],[[None]],hidden=True);w.define('EmptyList','选择数据','A',2,2)
    def list_name(values):
        values=list(values)
        if any(not isinstance(v,str) or not v.strip() for v in values):raise ValueError('Empty or invalid option label in source configuration.')
        if len(values)!=len(set(values)):raise ValueError('Ambiguous duplicate display labels; correct source configuration before generating.')
        if not values:return 'EmptyList'
        start=len(options['rows'])+2;options['rows'] += [[v] for v in values]
        name='Choices_%s'%len(w.names);w.define(name,'选择数据','A',start,start+len(values)-1);return name
    def empty(name,reason):
        w.blank_only(sheet,ranges[name]);rules[name]=reason+'；暂时只能留空，配置后重新生成。';warnings.append(name+'：'+reason)
        for r in range(2,end+1):sheet['cells'][r,index[name]]=(None,5,None)
    def static(name,values):
        if not values:empty(name,'当前没有有效选项');return
        w.dropdown(sheet,ranges[name],list_name(values));rules[name]='从目标数据库生成的下拉选项选择；不接受自由输入。'
    def linked(name,parent,groups,title):
        table_rows=[[key,list_name(values)] for key,values in groups.items()]
        visible=[[key,value] for key,values in groups.items() for value in values]
        w.sheet(title,[parent,name],visible or [['当前没有有效配置',None]],[48,65])
        if not visible:empty(name,'当前没有有效关联配置');return
        map_sheet=w.sheet('关联_'+str(len(w.sheets)),['上级','列表名称'],table_rows,hidden=True)
        range_name='Map_'+str(len(w.names));w.names[range_name]="'%s'!$A$2:$B$%s"%(map_sheet['name'],len(table_rows)+1)
        formula='INDIRECT(IFERROR(VLOOKUP($%s2,%s,2,FALSE),"EmptyList"))'%(refs[parent],range_name)
        w.dropdown(sheet,ranges[name],formula);rules[name]='先选%s，再选择其有效关联项；更换上级后重新选择本项。'%parent
        sheet['conditions'].append((ranges[name],'AND(%s2<>"",COUNTIF(%s,%s2)=0)'%(refs[name],formula,refs[name])))
    users={u['id']:u for u in data['users']}
    relations=[r for r in data['relations'] if r['sourceId'] in users and r['targetId'] in users]
    counts=Counter(u['name'] for u in users.values())
    def uname(id):
        u=users[id];return u['name'] if counts[u['name']]==1 else '%s（%s）'%(u['name'],u['username'])
    planner_ids=sorted({r['targetId'] for r in relations if r['scene'] in SCENES[:2] and (actor_id is None or r['sourceId']==actor_id)})
    if not planner_ids:raise ValueError('No enabled study-planner candidates for selected scope. No usable template can be generated.')
    static('学习规划师姓名',[uname(i) for i in planner_ids]);w.sheet('规划师选项',['姓名'],[[uname(i)] for i in planner_ids],[40])
    for id in planner_ids:mappings.append(['人员',uname(id),str(id),''])
    director_ids=set()
    for scene,name,parent,title in [(SCENES[2],'编导姓名','学习规划师姓名','编导选项'),(SCENES[3],'生涯规划师姓名','学习规划师姓名','生涯规划师选项'),(SCENES[4],'运营姓名','编导姓名','运营选项')]:
        sources=director_ids if scene==SCENES[4] else set(planner_ids)
        groups={}
        for source in sorted(sources):
            targets=sorted({r['targetId'] for r in relations if r['scene']==scene and r['sourceId']==source})
            groups[uname(source)]=[uname(t) for t in targets]
            if scene==SCENES[2]:director_ids.update(targets)
            for target in targets:mappings.append(['人员',uname(target),str(target),''])
        linked(name,parent,groups,title)
    categories={c['id']:c for c in data['categories']};parents={c['parentId'] for c in data['categories']}
    def path(id):
        if id in parents:return None
        values=[];seen=set()
        while id!=0:
            c=categories.get(id)
            if c is None or c['status']!=0 or id in seen:return None
            seen.add(id);values.insert(0,c['name']);id=c['parentId']
        return ' / '.join(values)
    products=[p for p in data['products'] if path(p['categoryId'])]
    pc=Counter(p['name'] for p in products)
    def pname(p):return p['name'] if pc[p['name']]==1 else p['name']+'（'+path(p['categoryId'])+'）'
    if not products:raise ValueError('No enabled products with a valid leaf-category chain.')
    product_rows=[[pname(p),path(p['categoryId'])] for p in products];static('课程名称',[r[0] for r in product_rows])
    w.sheet('课程选项',['课程名称','分类路径'],product_rows,[55,65]);sku_groups={};sku_rows=[]
    for p in products:
        name=pname(p);mappings.append(['课程',name,str(p['id']),''])
        skus=[s for s in data['skus'] if s['productId']==p['id']];names=Counter(s['name'] for s in skus);sku_groups[name]=[]
        for sku in skus:
            attrs=decoded(sku['attrs']) or {};details=[]
            for key,value in attrs.items():
                a=next((a for a in data['attrs'] if a['productId']==p['id'] and a['key']==key),None)
                v=next((v for v in data['attrValues'] if a and v['attrId']==a['id'] and v['value']==value),None)
                if not a or not v:raise ValueError('An enabled SKU references missing/disabled attributes; repair target configuration first.')
                details.append(a['name']+'：'+v['label'])
            text='；'.join(details);label=sku['name'] if names[sku['name']]==1 else sku['name']+'（'+text+'）'
            sku_groups[name].append(label);sku_rows.append([name+'｜'+label,text,text]);mappings.append(['规格',label,str(sku['id']),name])
    linked('课程规格名称','课程名称',sku_groups,'规格选项')
    sku_summary=[[name,str(len(values)),values[0] if len(values)==1 else ''] for name,values in sku_groups.items()]
    w.sheet('规格判定',['课程名称','有效规格数','唯一规格（自动确定）'],sku_summary,[55,22,60])
    w.names['SkuResolution']="'规格判定'!$A$2:$C$%s"%(len(sku_summary)+1)
    rules['课程规格名称']='条件必填：多个有效规格时必须选择；仅一个有效规格时可留空，自动按唯一规格带出属性。无有效规格时不能确定交付规格，须先核实配置。更换课程后重新选择。'
    sheet['conditions'].append((ranges['课程规格名称'],'AND($D2<>"",$E2="",IFERROR(VLOOKUP($D2,SkuResolution,2,FALSE),0)<>1)'))
    if any(not values for values in sku_groups.values()):warnings.append('部分课程无有效规格，不能自动确定交付规格；请核实配置后再导入')
    if len({r[0] for r in sku_rows})!=len(sku_rows):raise ValueError('SKU lookup keys are ambiguous.')
    w.sheet('规格属性',['匹配键（自动读取）','规格说明','属性说明'],sku_rows or [[None,None,None]],[65,75,75])
    cn=Counter(c['name'] for c in data['classes'])
    def cname(c):return c['name'] if cn[c['name']]==1 else c['name']+'（'+c['number']+'）'
    class_rows=[[cname(c),'待分班' if c['system'] else '业务班级'] for c in data['classes']]
    existing_names={c['name'] for c in data['classes']}
    for extra in dict.fromkeys(extra_class_names or []):
        if not extra or not extra.strip() or len(extra)>100:raise ValueError('Extra class name must be 1..100 nonblank characters.')
        if extra in existing_names or any(row[0]==extra for row in class_rows):continue
        class_rows.append([extra,'用户指定历史归类；待映射真实班级'])
        mappings.append(['待映射班级',extra,'','未创建数据库班级；导入前须明确真实班级映射'])
        warnings.append('班级“'+extra+'”仅为用户指定模板选项，尚未创建系统班级；导入前须明确映射，不得据此自动更改交付状态')
    if not class_rows:raise ValueError('No serving delivery classes in target database.')
    static('班级名称',[row[0] for row in class_rows]);w.sheet('班级选项',['班级名称','用途'],class_rows,[70,55])
    for c in data['classes']:mappings.append(['班级',cname(c),str(c['id']),''])
    configs=[c for c in data['configs'] if c['status']=='PUBLISHED']
    if len(configs)>1:raise ValueError('Multiple PUBLISHED student form configs; target state is ambiguous.')
    definitions=decoded(configs[0]['fields']) if configs else []
    definitions={f['key']:f for f in definitions};dict_rows=[]
    for name,key,fixed in DICT_FIELDS:
        config=definitions.get(key);dtype=fixed or (config or {}).get('dictType')
        values=[d for d in data['dicts'] if d['type']==dtype] if dtype and (not config or config.get('enabled')) else []
        if not fixed and not config:values=[]
        if values:
            duplicate=Counter(v['label'] for v in values)
            displays=[v['label'] if duplicate[v['label']]==1 else v['label']+'（'+str(v['value'])+'）' for v in values]
            static(name,displays)
            for display,v in zip(displays,values):dict_rows.append([name,dtype,display,v['value']]);mappings.append(['字典',display,str(v['value']),dtype])
        else:empty(name,'未绑定已发布字典、字段停用或字典无启用选项');dict_rows.append([name,dtype or '未绑定','无有效选项',None])
    w.sheet('字典选项',['字段','实际字典类型','显示选项','编码（自动读取）'],dict_rows,[26,40,35,30])
    areas={a['id']:a for a in data['areas']};area_values={}
    for a in data['areas']:
        if (a['type'] or 0)<3 and not a['leaf']:continue
        current=a;values=[];seen=set()
        while current and current['id']!=1:
            if current['id'] in seen:break
            seen.add(current['id']);values.insert(0,current['name']);current=areas.get(current['parentId'])
        if current and current['id']==1:
            display=' / '.join(values)
            if display in area_values:raise ValueError('Ambiguous area path in target configuration.')
            area_values[display]=a['id']
    static('户籍所在地',sorted(area_values));w.sheet('地区选项',['户籍完整路径'],[[s] for s in sorted(area_values)],[90])
    for display,id in area_values.items():mappings.append(['地区',display,str(id),''])
    w.sheet('系统映射',['类型','选择值','内部标识（自动读取）','所属项'],mappings,hidden=True)
    automatic=['产品分类路径','课程规格说明','课程属性说明']
    for name in automatic:rules[name]='自动带出，无需填写；请勿覆盖公式。'
    for r in range(2,end+1):
        effective='IF(E%d<>"",E%d,IFERROR(VLOOKUP(D%d,SkuResolution,3,FALSE),""))'%(r,r,r)
        formulas=[
            'IF(D%d="","",IFERROR(VLOOKUP(D%d,\'课程选项\'!$A$2:$B$%d,2,FALSE),""))'%(r,r,len(product_rows)+1),
            'IF(D%d="","",IFERROR(VLOOKUP(D%d&"｜"&%s,\'规格属性\'!$A$2:$C$%d,2,FALSE),""))'%(r,r,effective,max(2,len(sku_rows)+1)),
            'IF(D%d="","",IFERROR(VLOOKUP(D%d&"｜"&%s,\'规格属性\'!$A$2:$C$%d,3,FALSE),""))'%(r,r,effective,max(2,len(sku_rows)+1))]
        for name,formula in zip(automatic,formulas):sheet['cells'][r,index[name]]=(None,6,formula)
    sheet['conditions'].append(('A2:G'+str(end),'AND(COUNTA($A2:$X2)>0,OR($A2="",AND($B2="",$C2=""),$D2="",$F2="",$G2=""))'))
    w.sheet('字段说明',['字段','填写要求','规则'],[[n,'自动带出' if n in automatic else '必填' if r==1 else '条件必填' if n=='课程规格名称' else '至少选填一项' if r==2 else '选填',rules[n]] for n,r in FIELDS],[30,23,105])
    stamp=datetime.now(timezone(timedelta(hours=8))).isoformat(timespec='seconds')
    notes=[['生成时间',stamp],['数据来源','本次命令指定的数据库，只读一致性快照；无本地选项或旧文件回退。租户固定为1。'],
      ['必填项','前七列：姓名、课程名称、班级名称、学习规划师姓名必填；手机号和微信至少填写一项；课程规格为条件必填。其余全部选填，浅蓝色列自动带出。'],
      ['规格规则','多个有效规格时必须选择；只有一个时可留空，属性按唯一规格自动带出，后续导入器须解析为该规格。无有效规格不能确定交付内容，需核实配置。空缺或失配项标红；不根据班级名称猜测规格。'],
      ['符号','红色 * 表示必填；红色 † 表示条件必填（联系方式二选一、多个规格时必选）；黑色为选填。灰色格没有有效配置，只允许留空。'],
      ['一行含义','一位学员的一项课程；多课程分多行，同一学员联系方式一致，无需填写编号或部门。'],
      ['选择顺序','先课程再规格；先学习规划师再编导/生涯规划师，运营跟随编导。换上级后重新选下级，不匹配的旧选择标红。'],
      ['人员范围','按指定source-user-id的报名关系生成' if actor_id is not None else '未指定source-user-id，使用有效报名关系并集；不代表某个登录人的操作权限。'],
      ['资料选填','收集表资料全部选填，不变更在线收集表配置。填写后仍须校验格式、字典及材料归属，不可将未提交资料标为已提交。'],
      ['发布配置版本',str(configs[0]['version']) if configs else '无PUBLISHED配置；相关可选字典字段禁填'],
      ['后续导入','当前只生成模板，不导入数据。一键导入器仍需实现认证/对象范围、人员候选、班级课程约束、状态前置条件、匹配冲突及幂等校验。'],
      ['历史订单','不制造或自动猜配订单。无订单历史服务的资料落库需独立接入，不能伪造报名任务。'],
      ['Excel限制','验证范围第2行到第%s行。粘贴能绕过验证或覆盖公式，服务端必须重验。普通xlsx打开后自动重算。'%end],
      ['选项缺失','找不到课程、人员或地区时先核实目标系统配置，修改后重新生成，不随意添加到参考表。'],
      ['配置提示','；'.join(warnings) if warnings else '无缺失选项'] ]
    w.sheet('填写说明',['事项','说明'],notes,[29,112])
    return w,dict(products=len(products),skus=len(sku_rows),planners=len(planner_ids),areas=len(area_values),warnings=warnings,configVersion=configs[0]['version'] if configs else None)

def validate_file(path):
    with zipfile.ZipFile(path) as z:
        if z.testzip() is not None:raise ValueError('XLSX integrity check failed.')
        for name in z.namelist():
            if name.endswith(('.xml','.rels')):ET.fromstring(z.read(name))

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--database',required=True,help='Explicit target database name; never inferred')
    parser.add_argument('--mysql',default='mysql');parser.add_argument('--defaults-file')
    parser.add_argument('--host');parser.add_argument('--port',type=int);parser.add_argument('--user')
    secret=parser.add_mutually_exclusive_group();secret.add_argument('--password-file');secret.add_argument('--ask-password',action='store_true')
    parser.add_argument('--docker-container');parser.add_argument('--container-user')
    parser.add_argument('--container-password-env',default='MYSQL_ROOT_PASSWORD')
    parser.add_argument('--source-user-id',type=int,help='Optional actual importing user ID to scope planner relationships')
    parser.add_argument('--extra-class-name',action='append',default=[],help='Explicit template-only class label; no DB creation or invented ID')
    parser.add_argument('--editable-config',action='store_true',help='Visible live option configuration using scalar helpers and ordinary ranges')
    parser.add_argument('--rows',type=int,default=200);parser.add_argument('--timeout',type=int,default=120)
    parser.add_argument('--output',required=True);parser.add_argument('--overwrite',action='store_true')
    args=parser.parse_args()
    if not 1<=args.rows<=10000:parser.error('--rows must be 1..10000')
    if args.source_user_id is not None and args.source_user_id<=0:parser.error('--source-user-id must be positive')
    output=Path(args.output).resolve()
    if output.suffix.lower()!='.xlsx':parser.error('--output must end in .xlsx')
    if output.exists() and not args.overwrite:parser.error('Output exists; choose another path or explicitly use --overwrite')
    temp=None
    try:
        data=fetch(args);workbook,summary=build(data,args.rows,args.source_user_id,args.extra_class_name)
        if args.editable_config:
            from editable_config import configure
            configure(workbook,args.rows)
        output.parent.mkdir(parents=True,exist_ok=True)
        fd,temp=tempfile.mkstemp(prefix='.student-template-',suffix='.xlsx',dir=output.parent);os.close(fd)
        workbook.save(temp);validate_file(temp)
        if output.exists() and not args.overwrite:raise ValueError('Output appeared during generation; refusing to overwrite.')
        os.replace(temp,output);temp=None
        print('Generated:',output)
        print('Rows: %s; products: %s; SKUs: %s; planners: %s; areas: %s; PUBLISHED config: %s' % (args.rows,summary['products'],summary['skus'],summary['planners'],summary['areas'],summary['configVersion']))
        for warning in summary['warnings']:print('Configuration:',warning)
        return 0
    except (ValueError,OSError,subprocess.SubprocessError) as exc:
        print('Generation failed:',str(exc),file=sys.stderr);return 1
    finally:
        if temp and Path(temp).exists():Path(temp).unlink()

if __name__=='__main__':sys.exit(main())
