# -*- coding: utf-8 -*-
import unittest
from test_generate import fixture
import generate as g
from editable_config import configure

class EditableTest(unittest.TestCase):
    def test_visible_sources_and_no_orphan_names(self):
        w,_=g.build(fixture(),3,extra_class_names=['往期已拿证']);configure(w,3)
        visible=[s['name'] for s in w.sheets if not s['hidden']]
        self.assertEqual(visible[:2],['学员导入填写','使用与配置说明'])
        self.assertEqual(len([s for s in w.sheets if s.get('config')]),5)
        self.assertNotIn('选择数据',[s['name'] for s in w.sheets])
        for value in w.names.values():self.assertIn(value.split("'")[1],[s['name'] for s in w.sheets])
        formulae=[v[2] for s in w.sheets for v in s['cells'].values() if v[2]]
        self.assertTrue(any('MATCH(' in f and 'Sequence_' in f for f in formulae))
        self.assertFalse(any(any(token in f for token in ['FILTER','UNIQUE','ANCHORARRAY']) for f in formulae+list(w.names.values())))
        self.assertIn(':INDEX(',w.names['LiveList_3'])
        helper=next(s for s in w.sheets if s['name']=='动态下拉_通用')
        self.assertEqual(helper['cached'][2,3],'待分班')
        self.assertEqual(helper['cached'][3,3],'往期已拿证')
        self.assertTrue(any('COUNTIFS' in f and '待系统确认' in f for f in formulae))
        snapshot=next(s for s in w.sheets if s['name']=='原始配置快照')
        self.assertNotIn('往期已拿证',str(snapshot['rows']))
        self.assertIn('往期已拿证',str(next(s for s in w.sheets if s['name']=='配置_班级字典')['rows']))
        self.assertEqual(len(next(s for s in w.sheets if s['name']=='共用_规格')['headers']),6)
        self.assertEqual(len(next(s for s in w.sheets if s['name']=='共用_规格')['rows']),300)

    def test_helpers_do_not_scale_with_student_rows(self):
        small,_=g.build(fixture(),2);configure(small,2)
        large,_=g.build(fixture(),200);configure(large,200)
        count=lambda w:sum(len(s['cells']) for s in w.sheets if s['hidden'])
        self.assertEqual(count(small),count(large))
        self.assertEqual(len(small.sheets[0]['validations']),len(large.sheets[0]['validations']))

    def test_blank_config_rows_are_inside_named_ranges(self):
        w,_=g.build(fixture(),2);configure(w,2)
        courses=next(s for s in w.sheets if s['name']=='配置_课程')
        self.assertEqual(len(courses['rows']),300)
        self.assertTrue(any(v=="'配置_课程'!$B$2:$B$301" for v in w.names.values()))
        self.assertTrue(any(v[0]=='A2:A301' for v in courses['validations']))
        self.assertFalse(any(v[1]=='custom' for v in w.sheets[0]['validations']))
        self.assertEqual(w.sheets[0]['cells'][1,5][1],4)

    def test_dependent_validation_indirect_address(self):
        w,_=g.build(fixture(),3);configure(w,3)
        for column,parent,kind in [('E','D','规格'),('AB','G','编导'),('AC','G','生涯规划师'),('AD','AB','运营')]:
            formula=next(v[2] for v in w.sheets[0]['validations'] if v[0]==column+'2:'+column+'4')
            self.assertTrue(formula.startswith('INDIRECT(IF('))
            self.assertIn('$'+parent+'2',formula)
            self.assertIn("'共用_"+kind+"'!$B$",formula)
            self.assertNotIn('IFERROR(INDEX(',formula)

if __name__=='__main__':unittest.main()
