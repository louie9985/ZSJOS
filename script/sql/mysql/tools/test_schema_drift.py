#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Protect exact operational-backup exceptions and fail-closed schema checks."""
import unittest
from unittest.mock import Mock, patch

import zsjos_db as db


REPAIR_BACKUPS = (
    'zsjos_case_bak_approve_20260930031902',
    'zsjos_item_bak_seedamt_20260930033438',
    'zsjos_order_approval_round_bak_20260930012653',
    'zsjos_order_bak_20260930012653',
    'zsjos_order_bak_432_20260930041710',
    'zsjos_order_bak_approve_20260930031902',
    'zsjos_order_bak_apv_20260930040452',
    'zsjos_order_bak_insert_20260930034736',
    'zsjos_order_bak_seedamt_20260930033438',
    'zsjos_order_bak_submit2_20260930015245',
    'zsjos_order_bak_submit_20260930013844',
    'zsjos_round_bak_approve_20260930031902',
)


class SchemaDriftTest(unittest.TestCase):
    def setUp(self):
        self.manifests = {'core': db.load_manifests()['core']}
        self.columns = {('zsjos_order', 'id'): ('bigint', 'NO')}
        self.indexes = {('zsjos_order', 'primary'): (0, ('id',))}
        self.foreign_keys = {}
        self.actual_columns = [('zsjos_order', 'id', 'bigint', 'NO')]
        self.actual_indexes = [('zsjos_order', 'PRIMARY', '0', 'id')]
        self.actual_foreign_keys = []
        self.client = Mock(spec=db.MysqlClient)
        self.client.query.side_effect = self.query
        self.client.table_count.return_value = 1
        self.client.execute_file.return_value = ''
        schema = patch.object(db, 'desired_schema', return_value=(
            self.columns, self.indexes, self.foreign_keys))
        schema.start()
        self.addCleanup(schema.stop)

    def query(self, sql):
        if 'information_schema.columns' in sql:
            rows = self.actual_columns
        elif 'information_schema.statistics' in sql:
            rows = self.actual_indexes
        elif 'information_schema.key_column_usage' in sql:
            rows = self.actual_foreign_keys
        else:
            self.fail(f'Unexpected query in read-only schema fixture: {sql}')
        return '\n'.join('\t'.join(row) for row in rows)

    def add_table(self, name):
        self.actual_columns.extend([
            (name, 'id', 'bigint', 'NO'),
            (name, 'snapshot', 'text', 'YES'),
        ])
        self.actual_indexes.append((name, 'PRIMARY', '0', 'id'))

    def drift(self):
        return db.schema_drift(self.client, self.manifests)

    def test_all_twelve_exact_backups_are_optional(self):
        self.assertEqual([], self.drift())
        for table in REPAIR_BACKUPS:
            self.assertIn(table, self.manifests['core']['allowedExtraTables'])
            self.add_table(table)
        self.add_table('zsjos_data_repair_backup')
        self.assertEqual([], self.drift())

    def test_unknown_tables_are_sorted_and_reported_once(self):
        self.add_table('zsjos_unknown_z')
        self.add_table('zsjos_unknown_a')
        self.assertEqual(['unexpected table zsjos_unknown_a',
                          'unexpected table zsjos_unknown_z'], self.drift())

    def test_similar_backup_names_remain_blocked(self):
        for table in ('zsjos_order_bak_20260930012654',
                      'zsjos_order_bak_20260930012653_extra',
                      'zsjos_unknown_bak_20260930012653'):
            with self.subTest(table=table):
                self.add_table(table)
                self.assertIn(f'unexpected table {table}', self.drift())

    def test_allowed_backups_do_not_hide_missing_or_changed_business_columns(self):
        self.add_table(REPAIR_BACKUPS[0])
        self.actual_columns[0] = ('zsjos_order', 'id', 'varchar(20)', 'YES')
        self.columns[('zsjos_order', 'required_value')] = ('bigint', 'YES')
        drift = self.drift()
        self.assertIn('column differs zsjos_order.id expected=bigint/NO actual=varchar(20)/YES', drift)
        self.assertIn('missing column zsjos_order.required_value', drift)

    def test_allowlisted_business_tables_still_validate_columns_indexes_and_foreign_keys(self):
        self.manifests['core']['allowedExtraTables'].append('zsjos_order')
        self.actual_columns.append(('zsjos_order', 'unreviewed', 'text', 'YES'))
        self.actual_indexes = [('zsjos_order', 'unreviewed_idx', '1', 'id')]
        self.foreign_keys[('zsjos_order', 'expected_fk')] = (
            ('id',), 'zsjos_parent', ('id',), 'RESTRICT', 'RESTRICT')
        self.actual_foreign_keys.append((
            'zsjos_order', 'unreviewed_fk', 'id', 'zsjos_parent', 'id', 'RESTRICT', 'RESTRICT'))
        drift = self.drift()
        self.assertIn('unexpected column zsjos_order.unreviewed', drift)
        self.assertIn('missing index zsjos_order.primary', drift)
        self.assertIn('unexpected index zsjos_order.unreviewed_idx', drift)
        self.assertIn('missing foreign key zsjos_order.expected_fk', drift)
        self.assertIn('unexpected foreign key zsjos_order.unreviewed_fk', drift)

    def plan_context(self):
        for name, value in (
            ('enabled_manifests', self.manifests),
            ('db_config', None),
            ('MysqlClient', self.client),
            ('installed_versions', {}),
            ('pending_migrations', []),
        ):
            mocked = patch.object(db, name, return_value=value)
            mocked.start()
            self.addCleanup(mocked.stop)

    def test_plan_accepts_exact_backups_without_database_writes(self):
        for table in REPAIR_BACKUPS:
            self.add_table(table)
        self.plan_context()
        with patch.object(db, 'info') as output:
            _, pending, unexplained = db.print_plan('production')
        self.assertEqual([], pending)
        self.assertEqual([], unexplained)
        output.assert_any_call('Status: READY')
        self.client.execute_file.assert_not_called()
        self.client.backup.assert_not_called()

    def test_migrate_stops_before_backup_or_execution_for_unknown_table(self):
        self.add_table('zsjos_unreviewed_bak_20260930012653')
        self.plan_context()
        with patch.object(db, 'static_check'), patch.object(db, 'info'), \
                patch.object(db, 'MigrationLock') as lock:
            with self.assertRaisesRegex(db.CommandError, 'blocked by unexpected schema drift'):
                db.migrate_database('production')
        lock.assert_not_called()
        self.client.backup.assert_not_called()
        self.client.execute_file.assert_not_called()

    def test_verify_accepts_exact_backups_but_rejects_unknown_table(self):
        self.add_table(REPAIR_BACKUPS[0])
        with patch.object(db, 'enabled_manifests', return_value=self.manifests), \
                patch.object(db, 'info'):
            db.verify_database('production', self.client)
            self.add_table('zsjos_unreviewed_bak_20260930012653')
            with self.assertRaisesRegex(db.CommandError, 'unexpected table zsjos_unreviewed_bak'):
                db.verify_database('production', self.client)


if __name__ == '__main__':
    unittest.main()
