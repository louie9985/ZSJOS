# -*- coding: utf-8 -*-
import datetime as dt
from decimal import Decimal
import os
import unittest
from unittest.mock import patch
from types import SimpleNamespace
from server_target import ServerTarget, encode, decode, environment
from server_audit import components, decide
from server_apply import verify_files, maintenance_check


def person(i, no=None, **kwargs):
    return {'id':i,'person_no':no or str(i),'name':'合成客户','mobile':'','wechat_id':'','deleted':b'\0',**kwargs}


class ServerSafetyTest(unittest.TestCase):
    def test_invalid_explicit_environment_does_not_fall_back(self):
        for v in ('','prod','invalid'):
            with patch.dict(os.environ,{'ZSJOS_AGENT_ENV':v}):
                with self.assertRaises(ValueError):environment()

    def test_binding_cannot_switch_endpoint(self):
        b={'environment':'test','host':'different','port':3306,'database':'app','serverUuid':'u','tenantId':1}
        with patch('server_target.environment',return_value='test'),patch('server_target.db_config',return_value=SimpleNamespace(host='configured',port=3306,database='app')):
            with self.assertRaisesRegex(ValueError,'endpoint'):ServerTarget(b)

    def test_isolated_connection_rejects_application_and_unowned_database(self):
        target=object.__new__(ServerTarget);target.created={'person_verify_owned'};target.config=SimpleNamespace(database='app')
        for db in ('app','person_verify_other'):
            with self.assertRaises(ValueError):target.connect_isolated(db)

    def test_private_disk_roundtrip_preserves_types(self):
        value={'bit':b'\0','decimal':Decimal('1.20'),'date':dt.date(2026,10,30),'time':dt.datetime(2026,10,30,0,1),'name':'合成客户'}
        self.assertEqual(value,decode(encode(value)))

    def test_components_include_third_claim_and_alias_owner(self):
        p=[person(1,mobile='A'),person(2,mobile='A'),person(3)]
        groups,_=components(p,[{'person_id':3,'contact_value':'a'}],[],[])
        self.assertEqual([[1,2,3]],groups)
        groups,_=components(p,[],[{'person_id':3,'contact_value':'a'}],[])
        self.assertEqual([[1,2,3]],groups)

    def test_components_accept_empty_database_result_tuple(self):
        p=[person(1,mobile='x'),person(2,mobile='x')]
        self.assertEqual([[1,2]],components(p,[],(),[])[0])
        self.assertEqual([[1,2]],components(p,(),[],())[0])

    def test_archived_contact_resolves_mapping_without_recreating_source(self):
        p=[person(1,deleted=b'\1',mobile='old'),person(2),person(3,mobile='old')]
        groups,resolve=components(p,[],[],[{'from_person_id':1,'to_person_id':2}])
        self.assertEqual([[2,3]],groups);self.assertEqual(2,resolve(1))

    def test_mapping_cycle_is_rejected(self):
        with self.assertRaises(ValueError):
            components([person(1,deleted=b'\1'),person(2,deleted=b'\1')],[],[],
                       [{'from_person_id':1,'to_person_id':2},{'from_person_id':2,'to_person_id':1}])

    def test_confirmation_never_expands_to_new_third_member(self):
        c={'deferPersonNos':[],'names':[{'personNos':['A','B'],'canonicalName':'合成确认'}],'reviewReference':'test'}
        p=[person(1,'A',mobile='x'),person(2,'B',mobile='x'),person(3,'C',mobile='x')]
        self.assertEqual('REVIEW',decide([1,2,3],p,[],[],c)['status'])
        self.assertEqual('READY',decide([1,2],p,[],[],c)['status'])

    def test_defer_member_blocks_entire_expanded_group(self):
        c={'deferPersonNos':['A'],'names':[],'reviewReference':'test'}
        self.assertEqual('DEFER',decide([1,2,3],[person(1,'A'),person(2),person(3)],[],[],c)['status'])

    def test_batch_size_is_data_driven(self):
        c={'deferPersonNos':[],'names':[],'reviewReference':'test'}
        p=[person(1,mobile='x'),person(2,mobile='x')]
        self.assertEqual('READY',decide([1,2],p,[],[],c)['status'])

    def test_backup_from_another_target_is_rejected(self):
        with self.assertRaisesRegex(ValueError,'exact target'):
            verify_files({'target':{'database':'one'}},{'target':{'database':'two'},'consistent':True})

    def test_backup_from_previous_service_stop_is_rejected(self):
        with patch('server_apply.maintenance_check',return_value={'window':'current'}):
            with self.assertRaisesRegex(ValueError,'different maintenance window'):
                verify_files({'target':{'database':'one'}},{'target':{'database':'one'},'consistent':True,
                             'maintenanceToken':{'window':'old'}})

    def test_active_service_rejected_before_connection(self):
        with patch('server_apply.subprocess.check_output',return_value=b'active\n'):
            with self.assertRaisesRegex(ValueError,'must be stopped'):maintenance_check()

if __name__=='__main__':unittest.main()
