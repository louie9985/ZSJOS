import unittest
from group_repair import confirmed_rule

class ManualConfirmationTest(unittest.TestCase):
    def setUp(self):
        self.state={'zsjos_person':[{'id':1,'name':'旧称'},{'id':2,'name':'另一旧称'}],
                    'zsjos_order':[{'student_name':'旧称'},{'student_name':'另一旧称'}]}
        self.confirmation={'memberIds':[1,2],'canonicalName':'确认姓名','targetPersonId':2,'reviewReference':'explicit-test-decision'}
    def test_explicit_name_overrides_old_name_disagreement(self):
        rule=confirmed_rule(self.state,self.confirmation)
        self.assertEqual((rule['canonicalName'],rule['targetPersonId'],rule['identityRule']),('确认姓名',2,'USER_CONFIRMED_NAME'))
        self.assertEqual(self.state['zsjos_order'][0]['student_name'],'旧称')
    def test_confirmation_cannot_be_reused_for_another_group(self):
        with self.assertRaises(ValueError):confirmed_rule(self.state,{**self.confirmation,'memberIds':[1,3]})
    def test_target_must_belong_to_confirmed_group(self):
        with self.assertRaises(ValueError):confirmed_rule(self.state,{**self.confirmation,'targetPersonId':3})
    def test_explicit_review_reference_required(self):
        with self.assertRaises(ValueError):confirmed_rule(self.state,{**self.confirmation,'reviewReference':''})

if __name__=='__main__':unittest.main()
