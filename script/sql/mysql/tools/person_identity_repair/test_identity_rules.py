import unittest
from identity_rules import classify_group

def p(i,name='甲',mobile='13800000001',wechat='wx'):
    return dict(id=i,name=name,mobile=mobile,wechat_id=wechat)
def o(i,pid,name,**extra):
    return dict(id=i,person_id=pid,student_name=name,creator='operator',deleted=False,**extra)
def lead(i,pid,status,deleted=False):
    return dict(id=i,person_id=pid,status=status,deleted=deleted)

class Rules(unittest.TestCase):
    def test_exact(self):
        r=classify_group([p(1),p(2)],[])
        self.assertEqual(('AUTO_MERGE','EXACT_NAME_MOBILE_WECHAT',[]),(r['identityDecision'],r['identityRule'],r['executionBlockers']))
    def test_complementary(self):
        self.assertEqual(classify_group([p(1,wechat=''),p(2)],[])['identityRule'],'SAME_NAME_COMPATIBLE_CONTACTS')
    def test_cross(self):
        r=classify_group([p(1,wechat=''),p(2,mobile='',wechat='13800000001')],[])
        self.assertEqual(r['identityDecision'],'AUTO_MERGE')
    def test_swap_needs_execution_normalization_not_identity_review(self):
        r=classify_group([p(1),p(2,mobile='wx',wechat='13800000001')],[])
        self.assertEqual(r['identityDecision'],'AUTO_MERGE')
        self.assertIn('CONTACT_NORMALIZATION_REQUIRED',r['executionBlockers'])
    def test_order_contradiction_overrides_exact(self):
        self.assertEqual(classify_group([p(1),p(2)],[o(1,1,'甲'),o(2,2,'乙')])['identityDecision'],'REVIEW')
    def test_blank_order_does_not_reject_exact(self):
        self.assertEqual(classify_group([p(1),p(2)],[o(1,1,'')])['identityDecision'],'AUTO_MERGE')
    def test_order_name_authoritative(self):
        r=classify_group([p(1),p(2,name='备注')],[o(1,2,'正式姓名')])
        self.assertEqual((r['identityDecision'],r['canonicalName'],r['targetPersonId']),('AUTO_MERGE','正式姓名',2))
    def test_name_alone(self):
        self.assertEqual(classify_group([p(1),p(2,mobile='13900000000',wechat='other')],[])['identityDecision'],'REVIEW')
    def test_chain(self):
        r=classify_group([p(1,mobile='a',wechat='b'),p(2,mobile='b',wechat='c'),p(3,mobile='c',wechat='d')],[])
        self.assertEqual(r['identityDecision'],'REVIEW')
    def test_same_phone_different_nonempty_wechat_is_not_cross_match(self):
        self.assertEqual(classify_group([p(1),p(2,wechat='other')],[])['identityDecision'],'REVIEW')
    def test_whole_group(self):
        r=classify_group([p(1),p(2),p(3)],[])
        self.assertEqual(r['identityDecision'],'AUTO_MERGE');self.assertIn('ATOMIC_GROUP_EXECUTION_REQUIRED',r['executionBlockers'])
    def test_only_open_lead_selects_its_person_not_smallest_id(self):
        r=classify_group([p(1,name='历史备注'),p(2,name='当前姓名')],[],[lead(10,1,'closed'),lead(20,2,'valid')])
        self.assertEqual((r['identityRule'],r['targetPersonId'],r['canonicalName'],r['primaryLeadId']),('SINGLE_OPEN_LEAD_NO_ORDER',2,'当前姓名',20))
    def test_only_open_lead_overrides_same_name_default_target(self):
        self.assertEqual(classify_group([p(1),p(2)],[],[lead(10,1,'closed'),lead(20,2,'suspended')])['targetPersonId'],2)
    def test_both_closed_do_not_trigger_new_rule(self):
        self.assertEqual(classify_group([p(1),p(2,name='乙')],[],[lead(10,1,'closed'),lead(20,2,'closed')])['identityDecision'],'REVIEW')
    def test_multiple_nonclosed_do_not_trigger_new_rule(self):
        self.assertEqual(classify_group([p(1),p(2,name='乙')],[],[lead(10,1,'invalid'),lead(20,2,'valid')])['identityDecision'],'REVIEW')
    def test_deleted_leads_are_excluded(self):
        self.assertEqual(classify_group([p(1),p(2,name='乙')],[],[lead(10,1,'closed',True),lead(20,2,'valid')])['identityDecision'],'REVIEW')
    def test_existing_order_blocks_no_order_rule_even_with_empty_name(self):
        self.assertEqual(classify_group([p(1),p(2,name='乙')],[o(1,1,'')],[lead(10,1,'closed'),lead(20,2,'valid')])['identityDecision'],'REVIEW')
    def test_no_contact_link_does_not_merge(self):
        self.assertEqual(classify_group([p(1),p(2,name='乙',mobile='other',wechat='other')],[],[lead(10,1,'closed'),lead(20,2,'valid')])['identityDecision'],'REVIEW')

if __name__=='__main__':unittest.main()
