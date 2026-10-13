# -*- coding: utf-8 -*-
"""Order-actor repair cannot approve or overwrite acquisition attribution."""
import copy
import hashlib
from pathlib import Path
import unittest
from unittest.mock import Mock
from order_history_repair import execute,SQL_PATH
from order_history_repair_model import VERSION,KEY


class OrderSourceGuardTest(unittest.TestCase):
    def plan(self):
        lead={'id':1,'tenant_id':1,'lead_no':'LD-fixture','source_type':'internal_new_media',
              'submission_source_type':'sales_self_sourced','source_provider_recorded':1,
              'source_provider_user_id':20,'provider_owner_type':'system_user','provider_owner_id':20,
              'source_user_id':20,'contribution_user_id_snapshot':20}
        return {'version':VERSION,'repairKey':KEY,'scriptSha256':hashlib.sha256(SQL_PATH.read_bytes()).hexdigest(),
                'builderSha256':hashlib.sha256(Path(__file__).with_name('order_history_repair_model.py').read_bytes()).hexdigest(),
                'operations':[{'table':'zsjos_lead','before':lead,'after':copy.deepcopy(lead)}]}

    def test_actor_repair_cannot_repoint_consistent_provider_even_when_both_ids_change(self):
        plan=self.plan();plan['operations'][0]['after'].update(source_provider_user_id=17,provider_owner_id=17,source_user_id=17,contribution_user_id_snapshot=17)
        with self.assertRaisesRegex(ValueError,'cannot change source attribution'):execute(None,plan,'fixture')

    def test_unchanged_unreviewed_source_allows_actor_plan_to_reach_database_guards(self):
        plan=self.plan()
        for side in ['before','after']:plan['operations'][0][side].update(source_type='sales_self_sourced',source_provider_recorded=0)
        plan['operations'][0]['after']['source_user_id']=17
        conn=Mock()
        conn.cursor.side_effect=RuntimeError('database guard reached')
        with self.assertRaisesRegex(RuntimeError,'database guard reached'):execute(conn,plan,'fixture')
        conn.cursor.assert_called_once_with()

    def test_actor_repair_cannot_approve_or_reclassify_source(self):
        for field,value in [('source_provider_recorded',0),('source_type','sales_self_sourced'),
                            ('submission_source_type','education_self_sourced'),
                            ('provider_owner_name_snapshot','changed'),
                            ('contribution_dept_id_snapshot',99)]:
            with self.subTest(field=field):
                plan=self.plan();plan['operations'][0]['after'][field]=value
                with self.assertRaisesRegex(ValueError,'cannot change source attribution'):execute(None,plan,'fixture')

    def test_old_generator_plan_is_rejected_before_database_access(self):
        plan=self.plan();plan['builderSha256']='old'
        with self.assertRaisesRegex(ValueError,'Builder changed'):execute(None,plan,'fixture')


if __name__=='__main__':unittest.main()
