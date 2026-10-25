# -*- coding: utf-8 -*-
import copy
import json
import unittest
from pathlib import Path

from prepare_transfer_model_repair import prepare


class TransferModelRepairTest(unittest.TestCase):
    def setUp(self):
        self.model = json.loads((Path(__file__).parent /
            'zsjos_lead_transfer_request/2.0.0/process-model.json').read_text(encoding='utf-8'))
        self.model.update(id='fixture-model', formId=731, managerUserIds=[81], startDeptIds=[92],
                          bpmnXml='old generated XML', description='管理员保留的配置')
        review = self.model['simpleModel']['childNode']
        review.update(candidateStrategy=60, candidateParam='${coll_userList}',
                      rejectHandler={'type': 1}, buttonsSetting=[{'id': 1, 'enable': True}])

    def test_changes_only_candidate_contract_and_generated_xml(self):
        before = copy.deepcopy(self.model)
        fixed = prepare({'code': 0, 'data': self.model}, 'fixture-model')
        expected = copy.deepcopy(before)
        expected['simpleModel']['childNode'].update(candidateStrategy=35, candidateParam=None)
        expected['bpmnXml'] = None
        self.assertEqual(expected, fixed)
        self.assertEqual(before, self.model)
        self.assertEqual(fixed, prepare(fixed, 'fixture-model'))

    def test_rejects_failed_export_or_wrong_identity(self):
        for exported, model_id in [({'code': 403, 'data': self.model}, 'fixture-model'),
                                   (self.model, 'other-model'), (None, 'fixture-model')]:
            with self.subTest(model_id=model_id), self.assertRaises(ValueError):
                prepare(exported, model_id)

    def test_rejects_unrecognized_strategy_or_topology(self):
        for field, value in [('candidateStrategy', 22), ('candidateParam', '${otherUsers}'),
                             ('id', 'anotherReview'), ('childNode', None)]:
            model = copy.deepcopy(self.model)
            model['simpleModel']['childNode'][field] = value
            with self.subTest(field=field), self.assertRaises(ValueError):
                prepare(model, 'fixture-model')
        self.model['key'] = 'another_process'
        with self.assertRaises(ValueError):
            prepare(self.model, 'fixture-model')


if __name__ == '__main__':
    unittest.main()
