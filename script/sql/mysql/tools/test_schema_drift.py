#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Protect exact operational-backup exceptions and fail-closed schema checks."""
import tempfile
import unittest
from pathlib import Path
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
    'zsjos_media_account_bak_create_time_20261008',
    'zsjos_media_account_bak_create_time_20261008_pt2',
    'zsjos_person_bak_mobile_20261008',
)


class DesiredSchemaPrimaryKeyTest(unittest.TestCase):
    def parse(self, sql):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'schema.sql'
            path.write_text(sql, encoding='utf-8')
            return db.desired_schema(path)

    def test_inline_primary_key_and_implicit_not_null(self):
        for definition, column_type in (
            ('id bigint NOT NULL AUTO_INCREMENT PRIMARY KEY', 'bigint'),
            ('`ID` BIGINT UNSIGNED pRiMaRy\nKeY', 'bigint unsigned'),
        ):
            with self.subTest(definition=definition):
                columns, indexes, _ = self.parse(f'CREATE TABLE probe ({definition});')
                self.assertEqual('NO', columns[('probe', 'id')][1])
                self.assertEqual(column_type, columns[('probe', 'id')][0])
                self.assertEqual({('probe', 'primary'): (0, ('id',))}, indexes)

    def test_primary_key_text_in_literals_does_not_create_index(self):
        columns, indexes, _ = self.parse("""CREATE TABLE probe (
            id bigint COMMENT 'PRIMARY KEY',
            label varchar(64) DEFAULT 'PRIMARY KEY' COMMENT 'owner''s PRIMARY KEY',
            other varchar(64) COMMENT "PRIMARY KEY NOT NULL"
        );""")
        self.assertEqual({}, indexes)
        self.assertTrue(all(nullable == 'YES' for _, nullable in columns.values()))

    def test_table_level_composite_primary_key_is_preserved(self):
        _, indexes, _ = self.parse('''CREATE TABLE probe (
            tenant_id bigint NOT NULL, id bigint NOT NULL,
            PRIMARY KEY (tenant_id, id), KEY id_lookup (id)
        );''')
        self.assertEqual({('probe', 'primary'): (0, ('tenant_id', 'id')),
                          ('probe', 'id_lookup'): (1, ('id',))}, indexes)

    def test_diagnosis_primary_keys_in_schema_baseline_and_migration(self):
        for relative in ('schema/core.sql', '00-bootstrap-schema.sql',
                         'migrations/V346__weekly_account_diagnosis.sql'):
            with self.subTest(path=relative):
                _, indexes, _ = db.desired_schema(db.SQL_ROOT / relative)
                for table in ('zsjos_account_diagnosis_metric', 'zsjos_account_diagnosis_member'):
                    self.assertEqual((0, ('id',)), indexes[(table, 'primary')])


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

    def test_all_exact_backups_are_optional(self):
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
                      'zsjos_unknown_bak_20260930012653',
                      'zsjos_media_account_bak_create_time_20261009',
                      'zsjos_media_account_bak_create_time_20261008_pt3',
                      'zsjos_person_bak_mobile_20261008_extra'):
            with self.subTest(table=table):
                self.add_table(table)
                self.assertIn(f'unexpected table {table}', self.drift())

    def test_allowed_backups_do_not_hide_missing_or_changed_business_columns(self):
        for table in REPAIR_BACKUPS:
            self.add_table(table)
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
        for table in REPAIR_BACKUPS:
            self.add_table(table)
        with patch.object(db, 'enabled_manifests', return_value=self.manifests), \
                patch.object(db, 'info'):
            db.verify_database('production', self.client)
            self.add_table('zsjos_unreviewed_bak_20260930012653')
            with self.assertRaisesRegex(db.CommandError, 'unexpected table zsjos_unreviewed_bak'):
                db.verify_database('production', self.client)

    def test_data_findings_remain_visible_without_masking_schema_failure(self):
        self.client.execute_file.return_value = 'historical\tFAIL\nabsent\tMISSING\nvalid\tPASS\n'
        with patch.object(db, 'enabled_manifests', return_value=self.manifests), \
                patch.object(db, 'info'), patch.object(db, 'warn') as warning:
            db.verify_database('production', self.client)
            self.assertIn('core: historical\tFAIL', warning.call_args.args[0])
            self.assertIn('core: absent\tMISSING', warning.call_args.args[0])
            self.assertNotIn('core: valid', warning.call_args.args[0])
            self.actual_indexes = []
            with self.assertRaisesRegex(db.CommandError, 'missing index zsjos_order.primary'):
                db.verify_database('production', self.client)
            self.assertEqual(2, warning.call_count)


class PendingNamingDriftTest(unittest.TestCase):
    def setUp(self):
        self.manifests = {'core': db.load_manifests()['core']}
        self.migration = next(item for item in db.migrations_for('core', self.manifests['core'])
                              if item.version == 'V329')
        self.old = 'zsjos_lead_aging_pool_cycle'
        self.canonical = 'zsjos_lead_public_sea_pool_cycle'
        self.rule = 'zsjos_lead_follow_up_rule'
        self.columns = {
            (self.canonical, 'id'): ('bigint', 'NO'),
            (self.canonical, 'lead_id'): ('bigint', 'NO'),
            ('zsjos_lead', 'id'): ('bigint', 'NO'),
            (self.rule, 'public_sea_pool_timeout_days'): ('int', 'NO'),
        }
        self.indexes = {
            (self.canonical, 'primary'): (0, ('id',)),
            (self.canonical, 'uk_tenant_lead_cycle'): (0, ('lead_id',)),
            ('zsjos_lead', 'idx_tenant_public_sea_pool_scan'): (1, ('id',)),
            (self.rule, 'timeout_lookup'): (1, ('public_sea_pool_timeout_days',)),
        }
        self.fks = {('zsjos_lead', 'cycle_ref'): (
            ('id',), self.canonical, ('id',), 'RESTRICT', 'RESTRICT')}
        self.actual_columns = {key: value for key, value in self.columns.items()}
        self.actual_indexes = {key: value for key, value in self.indexes.items()}
        self.actual_fks = dict(self.fks)
        reverse = {new.lower(): old.lower() for old, new in
                   self.manifests['core']['baselineNamingUpgrade']['replacements'].items()}
        self.actual_columns = {(reverse.get(table, table), reverse.get(name, name)): value
                               for (table, name), value in self.actual_columns.items()}
        self.actual_indexes = {
            (reverse.get(table, table), reverse.get(name, name)):
                (unique, tuple(reverse.get(column, column) for column in columns))
            for (table, name), (unique, columns) in self.actual_indexes.items()}
        self.actual_fks = {key: (columns, reverse.get(table, table), refs, delete, update)
                           for key, (columns, table, refs, delete, update) in self.actual_fks.items()}
        self.client = Mock(spec=db.MysqlClient)
        self.client.query.side_effect = self.query
        self.client.table_count.return_value = 3
        schema = patch.object(db, 'desired_schema', return_value=(self.columns, self.indexes, self.fks))
        schema.start()
        self.addCleanup(schema.stop)

    def query(self, sql):
        if 'information_schema.columns' in sql:
            rows = [(*key, *value) for key, value in self.actual_columns.items()]
        elif 'information_schema.statistics' in sql:
            rows = [(*key, str(unique), ','.join(columns))
                    for key, (unique, columns) in self.actual_indexes.items()]
        elif 'information_schema.key_column_usage' in sql:
            rows = [(*key, ','.join(columns), table, ','.join(refs), delete, update)
                    for key, (columns, table, refs, delete, update) in self.actual_fks.items()]
        else:
            self.fail('Unexpected schema query: ' + sql)
        return '\n'.join('\t'.join(row) for row in rows)

    def plan(self, *, pending=True, strict=False):
        with patch.object(db, 'enabled_manifests', return_value=self.manifests), \
                patch.object(db, 'db_config'), patch.object(db, 'MysqlClient', return_value=self.client), \
                patch.object(db, 'installed_versions', return_value={}), \
                patch.object(db, 'migration_preflight', return_value=([self.migration] if pending else [], [])), \
                patch.object(db, 'info'):
            return db.print_plan('production', allow_pending_drift=not strict)[2]

    def test_pending_rename_checks_old_signatures_without_writes(self):
        self.assertEqual([], self.plan())
        self.client.execute_file.assert_not_called()
        self.client.backup.assert_not_called()

    def test_absent_mapping_or_other_pending_version_does_not_normalize(self):
        other = db.Migration('core', 'V328', 328, 'fixture', self.migration.path, '')
        self.assertIn(f'missing column {self.canonical}.id',
                      db.schema_drift(self.client, self.manifests, pending=[other]))
        self.manifests['core'].pop('baselineNamingUpgrade')
        self.assertIn(f'missing column {self.canonical}.id',
                      db.schema_drift(self.client, self.manifests, pending=[self.migration]))

    def test_installed_rename_and_strict_verification_reject_legacy_names(self):
        for kwargs in ({'pending': False}, {'strict': True}):
            with self.subTest(kwargs=kwargs):
                self.assertIn(f'missing column {self.canonical}.id', self.plan(**kwargs))

    def test_partial_table_rename_still_checks_old_column_and_index(self):
        self.actual_columns = {(self.canonical if table == self.old else table, name): value
                               for (table, name), value in self.actual_columns.items()}
        self.actual_indexes = {(self.canonical if table == self.old else table, name): value
                               for (table, name), value in self.actual_indexes.items()}
        self.actual_fks[('zsjos_lead', 'cycle_ref')] = self.fks[('zsjos_lead', 'cycle_ref')]
        self.assertEqual([], self.plan())

    def test_wrong_old_column_type_is_not_excused_by_rename_sql(self):
        self.actual_columns[(self.old, 'lead_id')] = ('varchar(20)', 'YES')
        self.assertIn(f'column differs {self.canonical}.lead_id expected=bigint/NO actual=varchar(20)/YES',
                      self.plan())

    def test_missing_table_column_and_index_stay_blocked(self):
        self.actual_columns.pop((self.old, 'lead_id'))
        self.actual_indexes.pop((self.old, 'uk_tenant_lead_cycle'))
        self.assertIn(f'missing column {self.canonical}.lead_id', self.plan())
        self.assertIn(f'missing index {self.canonical}.uk_tenant_lead_cycle', self.plan())
        self.actual_columns.pop((self.old, 'id'))
        self.assertIn(f'missing column {self.canonical}.id', self.plan())

    def test_duplicate_old_and_new_names_cannot_hide_each_other(self):
        self.actual_columns[(self.canonical, 'id')] = ('bigint', 'NO')
        self.assertIn(f'naming conflict tables {self.old} and {self.canonical}', self.plan())
        self.actual_columns[(self.rule, 'public_sea_pool_timeout_days')] = ('int', 'NO')
        self.assertIn(f'naming conflict columns {self.rule}.aging_pool_timeout_days '
                      f'and {self.rule}.public_sea_pool_timeout_days', self.plan())
        self.actual_indexes[('zsjos_lead', 'idx_tenant_public_sea_pool_scan')] = (1, ('id',))
        self.assertIn('naming conflict indexes zsjos_lead.idx_tenant_aging_pool_scan '
                      'and zsjos_lead.idx_tenant_public_sea_pool_scan', self.plan())

    def test_wrong_index_and_foreign_key_remain_visible(self):
        self.actual_indexes[(self.old, 'uk_tenant_lead_cycle')] = (1, ('id',))
        self.actual_fks[('zsjos_lead', 'cycle_ref')] = (
            ('id',), self.old, ('lead_id',), 'RESTRICT', 'RESTRICT')
        messages = self.plan()
        self.assertTrue(any(item.startswith(f'index differs {self.canonical}.uk_tenant_lead_cycle')
                            for item in messages))
        self.assertTrue(any(item.startswith('foreign key differs zsjos_lead.cycle_ref')
                            for item in messages))

    def test_unknown_columns_and_tables_are_not_suppressed(self):
        self.actual_columns[(self.old, 'unreviewed')] = ('text', 'YES')
        self.actual_columns[('unreviewed_table', 'id')] = ('int', 'NO')
        self.assertIn(f'unexpected column {self.canonical}.unreviewed', self.plan())
        self.assertIn('unexpected table unreviewed_table', self.plan())


if __name__ == '__main__':
    unittest.main()
