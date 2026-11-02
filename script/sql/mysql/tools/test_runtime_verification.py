# -*- coding: utf-8 -*-
"""Fail-closed runtime reporting without any database mutation."""
import unittest
from unittest.mock import Mock, patch
import zsjos_db as db

class RuntimeVerificationTest(unittest.TestCase):
    def run_verify(self, output, drift=()):
        client = Mock()
        client.execute_file.return_value = output
        with patch.object(db, 'enabled_manifests', return_value={'core': {'verify': 'verify/core.sql'}}), \
             patch.object(db, 'module_order', return_value=['core']), \
             patch.object(db, 'resolve_sql_path'), \
             patch.object(db, 'verify_applied_migration_integrity'), \
             patch.object(db, 'schema_drift', return_value=list(drift)), \
             patch.object(db, 'info') as info:
            try:
                db.verify_database('local', client)
            finally:
                self.messages = [call.args[0] for call in info.call_args_list]
                client.execute_file.assert_called_once()
                self.assertEqual('SET @zsjos_verify_runtime=1;\n', client.execute_file.call_args.kwargs['preamble'])
    def test_clean_and_explicit_fresh_skip(self):
        self.run_verify('integrity\tPASS\ninitial_empty\tSKIP')
        self.assertIn('PASS=1, SKIP=1', self.messages[0])
        self.assertTrue(self.messages[-1].startswith('PASS:'))
    def test_data_failure_blocks_success(self):
        for status in ('FAIL', 'MISSING'):
            with self.assertRaises(db.CommandError):
                self.run_verify('contacts\t' + status)
            self.assertFalse(any(m.startswith('PASS:') for m in self.messages))
    def test_schema_failure_blocks_success(self):
        with self.assertRaises(db.CommandError):
            self.run_verify('integrity\tPASS', ['missing table'])
        self.assertFalse(any(m.startswith('PASS:') for m in self.messages))

if __name__ == '__main__':
    unittest.main()
