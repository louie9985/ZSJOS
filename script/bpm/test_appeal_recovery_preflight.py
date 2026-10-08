#!/usr/bin/env python3
# -*- coding: utf-8 -*-
import unittest
from appeal_recovery_preflight import assess, query


class AppealRecoveryPreflightTest(unittest.TestCase):
    def row(self):
        xml = '''<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:f="http://flowable.org/bpmn"><process id="p"><userTask id="appealReview"><extensionElements><f:candidateStrategy>60</f:candidateStrategy><f:candidateParam>${coll_userList}</f:candidateParam></extensionElements></userTask></process></definitions>'''
        return dict(leadNo='KZfixture', tenantId=1, appealId=1, status='sales_manager_reviewing', roundNo=1,
                    reviewStage='sales_manager', reviewers='[21]', taskCount=1, taskKey='StartUserNode',
                    taskStatus=2, suspensionState=1, businessKey='lead-appeal:1', processTenant='1', endTime=None,
                    jobs=0, reviewHistory=0, compatibilityVariables=0, eligibleReviewers=1,
                    xmlHex=xml.encode().hex(), instanceId='instance', definitionId='definition', taskId='task')

    def test_candidate_is_not_execution_authorization(self):
        row = self.row(); row['reviewers'] = [21]
        report = assess(row)
        self.assertEqual('CANDIDATE', report['state'])
        self.assertEqual('READ_ONLY', report['mode'])
        self.assertEqual(64, len(report['definitionXmlSha256']))
        self.assertNotIn('xmlHex', report)
        self.assertTrue(report['remainingChecks'])

    def test_conflicts_block(self):
        for key, value in [('jobs', 1), ('reviewHistory', 1), ('taskStatus', 1), ('processTenant', '2'),
                           ('eligibleReviewers', 0), ('compatibilityVariables', 1), ('reviewers', '[]')]:
            with self.subTest(key=key):
                row = self.row(); row[key] = value
                self.assertEqual('BLOCKED', assess(row)['state'])

    def test_query_is_read_only_and_rejects_injection(self):
        self.assertIn('START TRANSACTION READ ONLY;', query(1, 'KZfixture'))
        for lead in ["x' OR 1=1--", 'x;DROP TABLE x', '']:
            with self.assertRaises(ValueError): query(1, lead)
        with self.assertRaises(ValueError): query(0, 'KZfixture')


if __name__ == '__main__':
    unittest.main()
