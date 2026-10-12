# -*- coding: utf-8 -*-
import unittest
from unittest.mock import patch
import lead_source_contract as contract
import merge_legacy_business_data as merge


class SourceContractTest(unittest.TestCase):
    def setUp(self):
        self.pools={'staff':{'17'},'partner_account':{'17','18'},'partner_main':{'17','19'},'dept':set()}

    def test_reference_domains_are_not_inferred_from_equal_numbers(self):
        mapping={'17':'117'}
        self.assertEqual(('117',None),merge.resolve_reference('zsjos_lead','source_provider_user_id','17','partner',self.pools,mapping))
        self.assertEqual(('17',None),merge.resolve_reference('zsjos_lead','provider_owner_id','17','partner',self.pools,mapping))
        self.assertEqual(('17',None),merge.resolve_reference('zsjos_lead','source_user_id','17','partner',self.pools,mapping))
        with self.assertRaises(ValueError):merge.resolve_reference('zsjos_lead','source_provider_user_id','17','system_user',self.pools,{})
        with self.assertRaises(ValueError):merge.resolve_reference('zsjos_lead','source_provider_user_id','18','partner',self.pools,mapping)
        with self.assertRaises(ValueError):merge.resolve_reference('zsjos_lead','provider_owner_id','18','partner',self.pools,mapping)

    def test_legacy_and_conflicting_associations_cannot_be_imported(self):
        row={'lead_no':'LD-test','source_type':'sales_self_sourced','source_provider_recorded':'0','source_provider_user_id':'17','provider_owner_type':'system_user','provider_owner_id':'17'}
        with self.assertRaisesRegex(ValueError,'LD-test'):contract.validate(row)
        row.update(source_provider_recorded='1',source_provider_user_id=None,provider_owner_type=None,provider_owner_id=None)
        contract.validate(row)
        row.update(source_type='internal_new_media',submission_source_type='sales_self_sourced',source_provider_user_id='17',provider_owner_type='system_user',provider_owner_id='18')
        with self.assertRaises(ValueError):contract.validate(row)
        row.update(provider_owner_id='17',source_user_id='17',contribution_user_id_snapshot='17');contract.validate(row)
        contract.validate({'source_type':'partner','source_provider_recorded':'0'})
        contract.validate({'source_type':'internal_new_media','source_provider_recorded':'0'})

    def test_equal_wrong_ids_do_not_replace_original_or_confirmed_evidence(self):
        row=dict(lead_no='LD-test',source_type='internal_new_media',source_provider_recorded=1,
                 source_provider_user_id=60,provider_owner_type='system_user',provider_owner_id=60,
                 source_user_id=60,contribution_user_id_snapshot=60)
        contract.validate(row)
        with self.assertRaisesRegex(ValueError,'original/confirmed'):
            contract.validate_import(row,{'LD-test':26})
        with self.assertRaisesRegex(ValueError,'lacks reviewed'):
            contract.validate_import(row,{})
        with self.assertRaisesRegex(ValueError,'unassisted'):
            contract.validate_import(row,{'LD-test':None})
        for field in ('source_provider_user_id','provider_owner_id','source_user_id','contribution_user_id_snapshot'):
            row[field]=26
        row['provider_owner_name_snapshot']='旧姓名'
        contract.validate_import(row,{'LD-test':26})

    def test_stale_payload_and_failed_mysql_are_rejected(self):
        expected=contract.metadata('SELECT 1',merge.__file__,'source','target','before-source','before-target')
        contract.verify_metadata(expected,dict(expected))
        for field in expected:
            changed=dict(expected);changed[field]='stale'
            with self.assertRaises(ValueError):contract.verify_metadata(changed,expected)
        with patch('builtins.open',unittest.mock.mock_open(read_data='test-fixture')),patch.object(merge.subprocess,'run') as run:
            run.return_value.returncode=1
            with self.assertRaises(RuntimeError):merge._mysql_script('SELECT 1','test-fixture')


if __name__=='__main__':unittest.main()
