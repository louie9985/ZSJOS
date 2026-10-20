# -*- coding: utf-8 -*-
"""Visible configuration with scalar helper cells and ordinary range validation."""
import re
from generate import FIELDS, col

def configure(w, row_count):
    original={s['name']:s for s in w.sheets}
    sheet=original['学员导入填写'];end=row_count+1
    # Keep immutable origin rows for comparison, not as a second editable option source.
    for s in w.sheets[1:]:s['hidden']=True
    w.names={};sheet['validations']=[];sheet['conditions']=[]
    refs={n:col(i) for i,(n,_) in enumerate(FIELDS,1)}
    origin=w.sheet('原始配置快照',['配置类型','启用','名称','上级或类型','补充信息','附加信息'],[],hidden=True)
    config_sheets=[]
    def config(name,headers,rows,widths):
        capacity=max(len(rows)+100,300)
        s=w.sheet(name,headers+['与系统快照比较'],rows+[[None]*len(headers) for _ in range(capacity-len(rows))],widths+[28]);s['config']=True
        config_sheets.append(s)
        pending_classes={r[1] for r in original['系统映射']['rows'] if r[0]=='待映射班级'}
        for values in rows:
            if name=='配置_班级字典' and values[2]=='班级' and values[1] in pending_classes:continue
            origin['rows'].append([name]+values+[None]*(5-len(values)))
        # Compare all business values (including enabled flag) with immutable snapshot.
        for r in range(2,capacity+2):
            args=['\'原始配置快照\'!$A$2:$A$20000','"'+name+'"']
            for i in range(len(headers)):
                args += ["'原始配置快照'!$%s$2:$%s$20000"%(col(i+2),col(i+2)),col(i+1)+str(r)]
            formula='IF(B%d="","",IF(COUNTIFS(%s)>0,"与生成时一致","待系统确认"))'%(r,','.join(args))
            s['cells'][r,len(headers)+1]=(None,6,formula)
        s['validations'].append(('A2:A'+str(capacity+1),'list','"是,否"','请选择是或否'))
        return s,capacity+1
    courses=[[ '是',r[0],r[1]] for r in original['课程选项']['rows']]
    cp,ce=config('配置_课程',['启用','课程名称','分类路径'],courses,[12,55,65])
    attrs={r[0]:r[1:] for r in original['规格属性']['rows'] if r[0]}
    specs=[]
    for course,sku in original['规格选项']['rows']:
        if not sku:continue
        a=attrs.get(course+'｜'+sku,['','']);specs.append(['是',sku,course,a[0],a[1]])
    sp,se=config('配置_规格',['启用','规格名称','所属课程','规格说明','属性说明'],specs,[12,55,55,65,65])
    personnel=[['是',r[0],'学习规划师',''] for r in original['规划师选项']['rows']]
    for title,kind in [('编导选项','编导'),('生涯规划师选项','生涯规划师'),('运营选项','运营')]:
        for parent,name in original[title]['rows']:
            if name:personnel.append(['是',name,kind,parent])
    ps,pe=config('配置_人员',['启用','姓名选项','类型','上级姓名'],personnel,[12,35,22,35])
    basics=[['是',r[0],'班级','', ''] for r in original['班级选项']['rows']]
    for field,dtype,label,code in original['字典选项']['rows']:
        if code is not None:basics.append(['是',label,field,dtype,code])
    bs,be=config('配置_班级字典',['启用','显示名称','字段类型','字典类型','字典编码'],basics,[12,55,22,38,25])
    regions=[['是',r[0]] for r in original['地区选项']['rows']]
    ar,ae=config('配置_地区',['启用','完整地区路径'],regions,[12,95])
    ps['validations'].append(('C2:C'+str(pe),'list','"学习规划师,编导,生涯规划师,运营"','请选择人员类型'))
    bs['validations'].append(('C2:C'+str(be),'list','"班级,性别,民族,是否复训,现学历,报名层次"','请选择字段类型'))
    ranges={}
    def define_ranges(s,limit):
        for i in range(1,len(s['headers'])):
            key='Cfg%d_%s'%(w.sheets.index(s),col(i));w.names[key]="'%s'!$%s$2:$%s$%d"%(s['name'],col(i),col(i),limit);ranges[s['name'],i]=key
    for s,limit in [(cp,ce),(sp,se),(ps,pe),(bs,be),(ar,ae)]:define_ranges(s,limit)
    def cfg(s,i):return ranges[s['name'],i]
    counters=w.sheet('计算_筛选序号',[],[],hidden=True)
    count_maps={}
    config_limits={cfg(s,i):limit-1 for s,limit in [(cp,ce),(sp,se),(ps,pe),(bs,be),(ar,ae)] for i in range(1,len(s['headers']))}
    def counter(source,condition):
        pair=(source,condition)
        if pair in count_maps:return count_maps[pair]
        c=len(counters['headers'])+1;counters['headers'].append('筛选'+str(c));counters['widths'].append(12)
        limit=config_limits[source]
        while len(counters['rows'])<limit:counters['rows'].append([])
        for k in range(1,limit+1):
            qualified=re.sub(r'(?<![\w!$])([DE])([0-9]+)',lambda m:"'学员导入填写'!$%s$%s"%(m.group(1),m.group(2)),condition)
            expr=re.sub(r'Cfg[0-9]+_[A-Z]+',lambda m:'INDEX(%s,%d)'%(m.group(),k),qualified)
            prior='0' if k==1 else col(c)+str(k)
            f='%s+IF(AND(%s,INDEX(%s,%d)<>""),1,0)'%(prior,expr,source,k)
            counters['cells'][k+1,c]=(None,6,f)
        name='Sequence_'+str(c);w.names[name]="'计算_筛选序号'!$%s$2:$%s$%d"%(col(c),col(c),limit+1)
        count_maps[pair]=name
        return name
    def scalar(source,condition,k):
        return 'IFERROR(INDEX(%s,MATCH(%d,%s,0)),"")'%(source,k,counter(source,condition))
    def normal_range(key,helper,c,limit):
        base="'%s'!$%s$2:$%s$%d"%(helper['name'],col(c),col(c),limit+1)
        w.names[key]="'%s'!$%s$2:INDEX(%s,MAX(1,COUNTIF(%s,\"?*\")))"%(helper['name'],col(c),base,base)
    helpers=w.sheet('动态下拉_通用',[],[[]],hidden=True)
    def static(name,source,condition,limit,initial):
        c=len(helpers['headers'])+1;helpers['headers'].append(name);helpers['widths'].append(25)
        while len(helpers['rows'])<limit:helpers['rows'].append([])
        for k in range(1,limit+1):
            helpers['cells'][k+1,c]=(None,1,scalar(source,condition,k))
            helpers.setdefault('cached',{})[k+1,c]=initial[k-1] if k<=len(initial) else ''
        key='LiveList_'+str(c);normal_range(key,helpers,c,limit)
        w.dropdown(sheet,refs[name]+'2:'+refs[name]+str(end),key)
        sheet['conditions'].append((refs[name]+'2:'+refs[name]+str(end),'AND(%s2<>"",COUNTIF(%s,%s2)=0)'%(refs[name],key,refs[name])))
        return key
    course_list=static('课程名称',cfg(cp,2),cfg(cp,1)+'="是"',ce-1,[r[1] for r in courses])
    planner_list=static('学习规划师姓名',cfg(ps,2),'('+cfg(ps,1)+'="是")*('+cfg(ps,3)+'="学习规划师")',pe-1,[r[1] for r in personnel if r[2]=='学习规划师'])
    for label in ['班级名称','性别','民族','是否复训','现学历','报名层次']:
        kind='班级' if label=='班级名称' else label
        static(label,cfg(bs,2),'('+cfg(bs,1)+'="是")*('+cfg(bs,3)+'="'+kind+'")',be-1,[r[1] for r in basics if r[2]==kind])
    static('户籍所在地',cfg(ar,2),cfg(ar,1)+'="是"',ae-1,[r[1] for r in regions])
    w.dropdown(sp,'C2:C'+str(se),course_list)
    # A single sorted list per relationship kind serves all input rows.
    shared=[]
    empty=w.sheet('共用_空列表',['空'],[[None]],hidden=True)
    w.names['EmptyShared']="'共用_空列表'!$A$2:$A$2"
    shared.append(empty)
    sku_lookup=None
    for name,parent,kind in [('课程规格名称','课程名称','规格'),('编导姓名','学习规划师姓名','编导'),('生涯规划师姓名','学习规划师姓名','生涯规划师'),('运营姓名','编导姓名','运营')]:
        source_sheet=sp if kind=='规格' else ps
        limit=(se if kind=='规格' else pe)-1
        parent_column=3 if kind=='规格' else 4
        source=cfg(source_sheet,2);parent_source=cfg(source_sheet,parent_column)
        rank=w.sheet('分组排序_'+kind,['有效上级','组内排序'],[[] for _ in range(limit)],hidden=True)
        helper=w.sheet('共用_'+kind,['上级','选项','配置行','匹配键','说明','属性'],[[] for _ in range(limit)],hidden=True)
        shared.extend([rank,helper])
        rank_col="'%s'!$B$2:$B$%d"%(rank['name'],limit+1)
        for k in range(1,limit+1):
            rr=k+1
            enabled='AND(INDEX(%s,%d)="是",INDEX(%s,%d)<>"",INDEX(%s,%d)<>"")'%(cfg(source_sheet,1),k,source,k,parent_source,k)
            if kind!='规格':enabled='AND(%s,INDEX(%s,%d)="%s")'%(enabled,cfg(ps,3),k,kind)
            rank['cells'][rr,1]=(None,1,'IF(%s,INDEX(%s,%d),"")'%(enabled,parent_source,k))
            rank['cells'][rr,2]=(None,6,'IF(A%d="","",COUNTIF($A$2:$A$%d,"<"&A%d)-COUNTIF($A$2:$A$%d,"")+COUNTIF($A$2:A%d,A%d))'%(rr,limit+1,rr,limit+1,rr,rr))
            match='MATCH(%d,%s,0)'%(k,rank_col)
            helper['cells'][rr,3]=(None,6,'IFERROR(%s,"")'%match)
            for c,src in [(1,parent_source),(2,source)]:
                helper['cells'][rr,c]=(None,1,'IF(C%d="","",INDEX(%s,C%d))'%(rr,src,rr))
            helper['cells'][rr,4]=(None,1,'IF(C%d="","",A%d&"｜"&B%d)'%(rr,rr,rr))
            if kind=='规格':
                for dest,source_c in [(5,4),(6,5)]:
                    helper['cells'][rr,dest]=(None,1,'IF(C%d="","",IF(INDEX(%s,C%d)="","",INDEX(%s,C%d)))'%(rr,cfg(sp,source_c),rr,cfg(sp,source_c),rr))
        pname='SharedParent_'+kind;vname='SharedValue_'+kind
        w.names[pname]="'%s'!$A$2:$A$%d"%(helper['name'],limit+1)
        w.names[vname]="'%s'!$B$2:$B$%d"%(helper['name'],limit+1)
        # Resolve an address before INDIRECT. IFERROR around a reference can coerce
        # the range to a scalar/array in desktop spreadsheet validation engines.
        first='MATCH($%s2,%s,0)'%(refs[parent],pname)
        count='COUNTIF(%s,$%s2)'%(pname,refs[parent])
        address='"\'%s\'!$B$"&(%s+1)&":$B$"&(%s+%s)'%(helper['name'],first,first,count)
        formula='INDIRECT(IF($%s2="","\'共用_空列表\'!$A$2",IFERROR(%s,"\'共用_空列表\'!$A$2")))'%(refs[parent],address)
        w.dropdown(sheet,refs[name]+'2:'+refs[name]+str(end),formula)
        sheet['conditions'].append((refs[name]+'2:'+refs[name]+str(end),'AND(%s2<>"",COUNTIFS(%s,$%s2,%s,%s2)=0)'%(refs[name],pname,refs[parent],vname,refs[name])))
        if kind=='规格':
            sku_lookup="'%s'!$D$2:$F$%d"%(helper['name'],limit+1)
            sheet['conditions'].append(('E2:E'+str(end),'AND(D2<>"",E2="",COUNTIF(%s,D2)<>1)'%pname))
            for r in range(2,end+1):
                effective='IF(E%d<>"",E%d,IF(COUNTIF(%s,D%d)=1,INDEX(%s,MATCH(D%d,%s,0)),""))'%(r,r,pname,r,vname,r,pname)
                cat='IF(D%d="","",IF(COUNTIFS(%s,D%d,%s,"是")=1,IFERROR(INDEX(%s,MATCH(D%d,%s,0)),""),"配置重复或停用"))'%(r,cfg(cp,2),r,cfg(cp,1),cfg(cp,3),r,cfg(cp,2))
                sheet['cells'][r,25]=(None,6,cat)
                for dest,lookup_col in [(26,2),(27,3)]:
                    f='IF(OR(D%d="",%s=""),"",IF(COUNTIFS(%s,D%d,%s,%s)=1,IFERROR(VLOOKUP(D%d&"｜"&%s,%s,%d,FALSE),""),"规格配置不唯一"))'%(r,effective,pname,r,vname,effective,r,effective,sku_lookup,lookup_col)
                    sheet['cells'][r,dest]=(None,6,f)
    # Remove stale frozen-source rules and gray input styles: configuration pages now own all choices.
    for i,(name,required) in enumerate(FIELDS,1):
        if name not in ['产品分类路径','课程规格说明','课程属性说明','服务开始时间','考试日期']:
            for r in range(2,end+1):sheet['cells'][r,i]=(None,2 if required else 1,None)
    sheet['conditions'].append(('A2:G'+str(end),'AND(COUNTA($A2:$X2)>0,OR($A2="",AND($B2="",$C2=""),$D2="",$F2="",$G2=""))'))
    # Duplicate active identities are highlighted in configuration, never silently trusted for import.
    for s,limit,key_columns in [(cp,ce,[2]),(sp,se,[2,3]),(ps,pe,[2,3,4]),(bs,be,[2,3]),(ar,ae,[2])]:
        args=['$A$2:$A$'+str(limit),'"是"']
        for c in key_columns:args += ['$'+col(c)+'$2:$'+col(c)+'$'+str(limit),'$'+col(c)+'2']
        s['conditions'].append(('A2:'+col(len(s['headers']))+str(limit),'AND($A2="是",$B2<>"",COUNTIFS(%s)>1)'%','.join(args)))
    notes=[
      ['使用顺序','只在学员导入填写录学员；需要改变下拉时，到后面的五张配置页操作。配置页首列选“是”启用，“否”停用。'],
      ['新增选项','在配置页预留空行中填写名称和所属项，再将启用设为“是”。下拉随之更新，无需运行脚本；可在中间留空，无须连续排序。不要改表头或删除整张表。'],
      ['课程与规格','配置_课程维护课程和分类；配置_规格维护规格、所属课程及说明。先建课程再建规格；一个有效规格可自动确定，多个规格必须选。'],
      ['人员','配置_人员：类型为学习规划师时上级留空；编导/生涯规划师的上级填学习规划师姓名；运营的上级填编导姓名。名称须与选项完全一致。'],
      ['班级和字典','配置_班级字典：班级的字段类型选“班级”，字典类型/编码留空；资料字段选择对应字段类型，字典类型和编码应与系统配置一致。往期已拿证保留为待系统确认选项。'],
      ['地区','配置_地区直接维护完整路径，不拆分学员填写列；可筛选查找。停用选项后，填写页已有的失效值会标红。'],
      ['配置范围','课程到第%d行；规格到第%d行；人员到第%d行；班级字典到第%d行；地区到第%d行。超出范围需重新生成更大容量版本。'%(ce,se,pe,be,ae)],
      ['颜色','填写页红字*必填，红字†条件必填；黑字选填，浅蓝色自动带出。配置页最后一列浅蓝色为自动比较结果，请勿编辑。红底表示重复配置或失效选择。'],
      ['系统匹配','与系统快照比较仅表示是否匹配生成时的配置，不是实时授权。新增、改名、改关系和待映射班级均须在真正导入前确认；编辑Excel不会写入系统或授予权限。'],
      ['兼容性','使用普通辅助单元格、逐行计数、INDEX/MATCH和范围下拉，不依赖动态数组或数组筛选。请开启自动计算；Excel/WPS各版本桌面交互仍需实际验证。'],
      ['保留原始资料','修改选项名称后不会自动重写已经填写的学员数据；旧值会标红，请重新选择。停用请用“否”，不删除历史来源。'],
      ['导入边界','本文件用于数据采集，一键导入器尚未实现；空字典可以在表中先拟定，但导入前必须配置到系统并核验，不以表格选项冒充系统数据。'],
    ]
    guide=w.sheet('使用与配置说明',['事项','操作说明'],notes,[27,112])
    field_sheet=original['字段说明'];field_sheet['hidden']=False
    for row in field_sheet['rows']:
        if row[0] in ['课程名称','班级名称','学习规划师姓名','课程规格名称','编导姓名','生涯规划师姓名','运营姓名','性别','民族','是否复训','现学历','报名层次','户籍所在地']:
            row[2]='从配置页实时生成的下拉选择；选项为空时先配置。编辑不等于系统已确认，正式导入需重新校验。'
    visible=[sheet,guide,cp,sp,ps,bs,ar,field_sheet]
    for config_sheet in config_sheets:
        for key,(value,style,formula) in list(config_sheet['cells'].items()):
            if formula:config_sheet['cells'][key]=(value,style,formula.replace('$20000','$'+str(len(origin['rows'])+1)))
    # Keep origin mapping, not obsolete generated options that could be edited accidentally.
    keep=[origin,original['系统映射'],helpers,counters]+shared
    w.sheets=visible+keep
