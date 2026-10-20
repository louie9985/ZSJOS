# -*- coding: utf-8 -*-
import tempfile
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET
import zipfile
from unittest.mock import patch
from types import SimpleNamespace
import generate as g

def fixture():
    return dict(products=[dict(id=1,name='示例课程',categoryId=10)],
      categories=[dict(id=10,name='示例分类',parentId=0,status=0)],
      skus=[dict(id=20,productId=1,name='标准规格',attrs={'level':'a'})],
      attrs=[dict(id=30,productId=1,key='level',name='级别')],
      attrValues=[dict(attrId=30,value='a',label='标准')],
      users=[dict(id=i,name='员工'+str(i),username='user'+str(i)) for i in range(1,7)],
      relations=[dict(scene=g.SCENES[0],sourceId=1,targetId=2),dict(scene=g.SCENES[0],sourceId=6,targetId=5),
                 dict(scene=g.SCENES[2],sourceId=2,targetId=3),dict(scene=g.SCENES[4],sourceId=3,targetId=4)],
      classes=[dict(id=40,name='待分班',number='PENDING',system=1,categoryId=None,productId=None)],
      areas=[dict(id=1,name='中国',parentId=0,type=1,leaf=0),dict(id=2,name='示例省',parentId=1,type=2,leaf=0),dict(id=3,name='示例市',parentId=2,type=3,leaf=0)],
      configs=[dict(version=9,status='PUBLISHED',fields=[dict(key='ethnicity',enabled=True,dictType='production_ethnicity')])],
      dicts=[dict(type='system_user_sex',value='1',label='男'),dict(type='production_ethnicity',value='real',label='生产字典选项'),dict(type='unrelated_local_type',value='wrong',label='不应出现')])

class GeneratorTest(unittest.TestCase):
    def test_explicit_template_class_is_unmapped_and_deduplicated(self):
        w,info=g.build(fixture(),2,extra_class_names=['往期已拿证','往期已拿证'])
        classes=next(s for s in w.sheets if s['name']=='班级选项')['rows']
        self.assertEqual(sum(r[0]=='往期已拿证' for r in classes),1)
        mapping=next(s for s in w.sheets if s['name']=='系统映射')['rows']
        row=next(r for r in mapping if r[0]=='待映射班级')
        self.assertEqual(row[2],'')
        self.assertTrue(any('尚未创建系统班级' in x for x in info['warnings']))
        data=fixture();data['classes'][0]['name']='往期已拿证'
        w,_=g.build(data,2,extra_class_names=['往期已拿证'])
        mapping=next(s for s in w.sheets if s['name']=='系统映射')['rows']
        self.assertFalse(any(r[0]=='待映射班级' for r in mapping))
        self.assertTrue(any(r[:3]==['班级','往期已拿证','40'] for r in mapping))

    def test_published_binding_and_no_local_fallback(self):
        w,info=g.build(fixture(),2)
        dictionary=next(s for s in w.sheets if s['name']=='字典选项')
        self.assertIn(['民族','production_ethnicity','生产字典选项','real'],dictionary['rows'])
        self.assertNotIn('不应出现',str(dictionary['rows']));self.assertEqual(info['configVersion'],9)
        data=fixture();data['configs'][0]['fields'][0]['enabled']=False
        w,_=g.build(data,2)
        self.assertTrue(any(v[0]=='K2:K3' and v[1]=='custom' for v in w.sheets[0]['validations']))

    def test_scope_relationships_and_required_order(self):
        w,info=g.build(fixture(),3,1);self.assertEqual(info['planners'],1)
        self.assertEqual(w.sheets[0]['headers'][:7],['姓名 *','手机号码 †','微信号 †','课程名称 *','课程规格名称 †','班级名称 *','学习规划师姓名 *'])
        self.assertTrue(all(required==0 for _,required in g.FIELDS[7:]))
        dvs={v[0]:v for v in w.sheets[0]['validations']}
        self.assertIn('$G2',dvs['AB2:AB4'][2]);self.assertIn('$AB2',dvs['AD2:AD4'][2]);self.assertIn('$D2',dvs['E2:E4'][2])

    def test_missing_required_candidates_fails(self):
        for key in ['products','classes','relations']:
            data=fixture();data[key]=[]
            with self.assertRaises(ValueError):g.build(data,2)
        with self.assertRaises(ValueError):g.build(fixture(),2,999)

    def test_single_multiple_and_zero_spec_resolution(self):
        for count in [1,2,0]:
            data=fixture()
            if count==2:data['skus'].append(dict(id=21,productId=1,name='另一规格',attrs={'level':'a'}))
            if count==0:data['skus']=[]
            w,summary=g.build(data,2)
            row=next(s for s in w.sheets if s['name']=='规格判定')['rows'][0]
            self.assertEqual(row[1],str(count))
            self.assertEqual(row[2],'标准规格' if count==1 else '')
            self.assertTrue(any(target=='E2:E3' and 'SkuResolution' in expression and '<>1' in expression for target,expression in w.sheets[0]['conditions']))
            self.assertTrue(any('无有效规格' in x for x in summary['warnings']) if count==0 else True)

    def test_bad_category_and_sku_reference_fail(self):
        data=fixture();data['categories'][0]['status']=1
        with self.assertRaises(ValueError):g.build(data,2)
        data=fixture();data['attrValues']=[]
        with self.assertRaises(ValueError):g.build(data,2)

    def test_duplicate_config_and_labels(self):
        data=fixture();data['configs']*=2
        with self.assertRaises(ValueError):g.build(data,2)
        data=fixture();data['products'].append(dict(id=2,name='示例课程',categoryId=10))
        with self.assertRaises(ValueError):g.build(data,2)

    def test_duplicate_person_display_is_distinct(self):
        data=fixture();data['users'][1]['name']=data['users'][4]['name']='同名'
        w,_=g.build(data,2);planner=next(s for s in w.sheets if s['name']=='规划师选项')
        self.assertEqual(len(set(row[0] for row in planner['rows'])),2)

    def test_read_only_sql_contract(self):
        sql=g.sql_snapshot();self.assertIn('START TRANSACTION WITH CONSISTENT SNAPSHOT, READ ONLY',sql)
        self.assertIn("status='PUBLISHED'",sql);self.assertIn('t.status=0',sql)
        for statement in sql.split(';'):
            statement=statement.strip();self.assertTrue(not statement or statement.startswith(('SET ','START ','SELECT ','COMMIT')))

    def test_xlsx_native_features_and_literal_labels(self):
        data=fixture();data['products'][0]['name']='=HYPERLINK("https://invalid.example")';w,_=g.build(data,3)
        with tempfile.TemporaryDirectory() as folder:
            path=Path(folder)/'test.xlsx';w.save(path);g.validate_file(path)
            with zipfile.ZipFile(path) as z:
                n={'m':g.NS};s=ET.fromstring(z.read('xl/worksheets/sheet1.xml'))
                self.assertEqual(len(s.findall('.//m:f',n)),9)
                self.assertTrue(all(c.get('errorStyle')=='stop' for c in s.findall('m:dataValidations/m:dataValidation',n)))
                formulas=[f.text for f in s.findall('.//m:f',n)];self.assertIn('D2',formulas[0]);self.assertIn('E2',formulas[1]);self.assertIn('SkuResolution',formulas[1])
                names=ET.fromstring(z.read('xl/workbook.xml')).find('m:definedNames',n)
                self.assertTrue(any(x.get('name')=='EmptyList' for x in names))
                catalog=next(i for i,sheet in enumerate(w.sheets,1) if sheet['name']=='课程选项')
                r=ET.fromstring(z.read('xl/worksheets/sheet%d.xml'%catalog));self.assertEqual(len(r.findall('.//m:f',n)),0)
                self.assertIn('=HYPERLINK',z.read('xl/worksheets/sheet%d.xml'%catalog).decode())

    def test_mysql_failure_never_creates_output(self):
        with tempfile.TemporaryDirectory() as folder:
            output=Path(folder)/'output.xlsx'
            argv=['generate.py','--host','db.example','--user','reader','--database','target','--output',str(output)]
            with patch('sys.argv',argv),patch('generate.subprocess.run',return_value=SimpleNamespace(returncode=1,stdout=b'',stderr=b'secret diagnostic')):
                with patch('sys.stderr') as error:
                    self.assertEqual(g.main(),1)
                    self.assertNotIn('secret diagnostic',str(error.write.call_args_list))
            self.assertFalse(output.exists())

    def test_existing_output_not_overwritten_or_queried(self):
        with tempfile.TemporaryDirectory() as folder:
            output=Path(folder)/'filled.xlsx';output.write_bytes(b'keep user data')
            with patch('sys.argv',['generate.py','--database','target','--output',str(output)]),patch('generate.fetch') as fetch,patch('sys.stderr'):
                with self.assertRaises(SystemExit):g.main()
                fetch.assert_not_called()
            self.assertEqual(output.read_bytes(),b'keep user data')

if __name__=='__main__':unittest.main()
