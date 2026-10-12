#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Batch preflight and fail-fast regression. --mysql retains an isolated MySQL schema."""
import contextlib
import os
import sys
import tempfile
import unittest
import uuid
from pathlib import Path
from unittest.mock import Mock, patch

import zsjos_db as db


class MigrationExecutionTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.manifests = {'core': {'dependsOn': [], 'migrations': 'migrations'}}
        (self.root / 'migrations').mkdir()
        for name, value in [('SQL_ROOT', self.root)]:
            p = patch.object(db, name, value)
            p.start()
            self.addCleanup(p.stop)
        self.client = Mock(spec=db.MysqlClient)
        self.client.table_exists.return_value = False
        self.client.table_count.return_value = 1

    def migration(self, number, sql='SELECT 1;\n'):
        path = self.root / 'migrations' / f'V{number:03d}__fixture.sql'
        path.write_text(sql, encoding='utf-8')
        return db.Migration('core', f'V{number:03d}', number, 'fixture', path, db.sha256(path))

    def plan_context(self, installed):
        stack = contextlib.ExitStack()
        for name, value in [('enabled_manifests', self.manifests), ('db_config', None),
                            ('MysqlClient', self.client), ('installed_versions', installed),
                            ('schema_drift', [])]:
            stack.enter_context(patch.object(db, name, return_value=value))
        stack.enter_context(patch.dict(os.environ, {'ZSJOS_DB_SKIP_CHECKSUM': 'false'}))
        return stack

    def test_checksum_conflict_is_blocked_in_plan_and_before_any_write(self):
        self.migration(1)
        installed = {('core', 'V001'): ('changed', 'release')}
        with self.plan_context(installed), patch.object(db, 'info') as output, \
                patch.object(db, 'static_check'), patch.object(db, 'MigrationLock') as lock, \
                patch.object(db, 'reconcile_checksums') as reconcile:
            _, pending, blockers = db.print_plan('local')
            self.assertEqual([], pending)
            self.assertIn('checksum changed', blockers[0])
            output.assert_any_call('Status: BLOCKED')
            self.assertNotIn(unittest.mock.call('Status: READY'), output.call_args_list)
            with self.assertRaisesRegex(db.CommandError, 'blocked'):
                db.migrate_database('local')
            with patch.object(sys, 'argv', ['zsjos-db', 'plan', 'local']):
                self.assertEqual(2, db.main())
            lock.assert_not_called()
            reconcile.assert_not_called()
        self.client.backup.assert_not_called()
        self.client.execute_file.assert_not_called()

    def test_applied_repairs_are_not_replayed_and_pending_versions_are_ordered(self):
        for n in (4, 2, 1, 3):
            self.migration(n)
        installed = {('core', 'V001'): ('historical-tag', 'baseline'),
                     ('core', 'V002'): ('historical-tag', 'baseline')}
        with self.plan_context(installed):
            pending, blockers = db.migration_preflight(self.client, self.manifests)
        self.assertEqual(['V003', 'V004'], [m.version for m in pending])
        self.assertEqual([], blockers)

    def test_v305_evidence_requirement_is_checked_only_when_pending(self):
        migration = self.migration(305)
        self.client.table_exists.return_value = True
        with patch.object(db, 'installed_versions', return_value={}), \
                patch.object(db, 'pending_migrations', return_value=[migration]):
            self.client.query.return_value = '1\n'
            _, blockers = db.migration_preflight(self.client, self.manifests)
            self.assertIn('order_history_repair.py', blockers[0])
            self.assertIn('tenant_id=1', self.client.query.call_args.args[0])
            self.client.query.return_value = '0\n'
            self.assertEqual([], db.migration_preflight(self.client, self.manifests)[1])
            self.client.table_exists.return_value = False
            self.client.query.reset_mock()
            self.assertEqual([], db.migration_preflight(self.client, self.manifests)[1])
            self.client.query.assert_not_called()
        with patch.object(db, 'installed_versions', return_value={}), \
                patch.object(db, 'pending_migrations', return_value=[]):
            self.client.query.reset_mock()
            self.assertEqual(([], []), db.migration_preflight(self.client, self.manifests))
            self.client.query.assert_not_called()

    def test_lock_recheck_blocks_new_conflict_before_backup(self):
        with patch.object(db, 'static_check'), \
                patch.object(db, 'enabled_manifests', return_value=self.manifests), \
                patch.object(db, 'print_plan', return_value=(self.client, [], [])), \
                patch.object(db, 'MigrationLock'), \
                patch.object(db, 'migration_preflight', return_value=([], ['changed after plan'])):
            with self.assertRaisesRegex(db.CommandError, 'changed after plan'):
                db.migrate_database('local')
        self.client.backup.assert_not_called()
        self.client.execute_file.assert_not_called()


def mysql_regression():
    from test_calendar_notifications import query, table
    name = 'migration_batch_' + uuid.uuid4().hex[:12]
    query(None, f'CREATE DATABASE `{name}` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;')
    query(name, table('zsjos_schema_version') + table('zsjos_module_schema_version') +
          'CREATE TABLE probe(id INT PRIMARY KEY,label VARCHAR(30));')

    class DockerClient(db.MysqlClient):
        def __init__(self, config):
            self.config = config
            self.mysql = 'mysql'

        def base(self, executable):
            # Exercise the production query/execute_file code with the existing
            # container's CLI; credentials stay inside the container environment.
            return ['docker', 'exec', '-i', 'yudao-mysql', 'sh', '-c',
                    'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot '
                    '--default-character-set=utf8mb4 --batch --raw --unbuffered '
                    '--skip-column-names "$@"', 'mysql-client']

    with tempfile.TemporaryDirectory() as directory, contextlib.ExitStack() as stack:
        root = Path(directory)
        (root / 'migrations').mkdir()
        manifests = {'core': {'dependsOn': [], 'migrations': 'migrations'}}
        config = db.DbConfig('localhost', 3306, name, 'root', '', 'fixture', root)
        client = DockerClient(config)
        # Save the small fixture before-state; production logical backup is not
        # replaced. This test targets execution, preflight and ledger behavior.
        backup = root / 'fixture-before.txt'
        backup.write_text(query(name, 'SELECT * FROM probe;')[0], encoding='utf-8')
        for key, value in [('SQL_ROOT', root)]:
            stack.enter_context(patch.object(db, key, value))
        for key, value in [('enabled_manifests', manifests), ('db_config', config),
                           ('MysqlClient', client), ('schema_drift', [])]:
            stack.enter_context(patch.object(db, key, return_value=value))
        stack.enter_context(patch.object(db, 'static_check'))
        stack.enter_context(patch.object(db, 'verify_database'))
        stack.enter_context(patch.object(client, 'backup', return_value=backup))
        stack.enter_context(patch.dict(os.environ, {'ZSJOS_DB_SKIP_CHECKSUM': 'false'}))
        def write(n, sql):
            p = root / 'migrations' / f'V{n:03d}__fixture.sql'
            p.write_text(sql, encoding='utf-8')
            return p
        write(3, 'INSERT INTO probe VALUES(3,\'完成\');\n')
        write(1, 'INSERT INTO probe VALUES(1,\'开始\');\n')
        write(2, "SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='fixture failure';\n"
                 "INSERT INTO probe VALUES(2,'must not execute');\n")
        try:
            db.migrate_database('fixture')
            raise AssertionError('Failure was swallowed')
        except db.CommandError as error:
            assert 'fixture failure' in str(error)
        assert query(name, 'SELECT id FROM probe ORDER BY id;')[0] == '1'
        assert query(name, 'SELECT version FROM zsjos_module_schema_version;')[0] == 'V001'
        write(2, 'INSERT INTO probe VALUES(2,\'恢复\');\n')
        db.migrate_database('fixture')
        before = query(name, 'SELECT id,HEX(label) FROM probe ORDER BY id;'
                       'SELECT * FROM zsjos_module_schema_version ORDER BY version;')[0]
        db.migrate_database('fixture')
        assert before == query(name, 'SELECT id,HEX(label) FROM probe ORDER BY id;'
                               'SELECT * FROM zsjos_module_schema_version ORDER BY version;')[0]
        assert '恢复'.encode().hex().upper() in before
        write(1, 'SELECT 99;\n')
        _, _, blockers = db.print_plan('fixture')
        assert any('checksum changed' in b for b in blockers)
        try:
            db.migrate_database('fixture')
            raise AssertionError('Checksum conflict was swallowed')
        except db.CommandError:
            pass
        assert before == query(name, 'SELECT id,HEX(label) FROM probe ORDER BY id;'
                               'SELECT * FROM zsjos_module_schema_version ORDER BY version;')[0]
        # Run V305's real prepare/finalize guards through the same fail-fast CLI.
        query(name, "INSERT INTO zsjos_schema_version(version,description) VALUES('V304','fixture');"
              "INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version) "
              "VALUES('core','V304','fixture','fixture','fixture');"
              "CREATE TABLE zsjos_order(tenant_id BIGINT,deleted BIT,status VARCHAR(30),creator VARCHAR(30));"
              "INSERT INTO zsjos_order VALUES(1,0,'effective','fixture');")
        v305 = db.ROOT / 'script/sql/mysql/migrations/V305__order_history_repair.sql'
        pending = db.Migration('core', 'V305', 305, 'fixture', v305, db.sha256(v305))
        with patch.object(db, 'pending_migrations', return_value=[pending]):
            assert 'order_history_repair.py' in db.migration_preflight(client, manifests)[1][0]
        try:
            client.execute_file(v305)
            raise AssertionError('V305 direct execution unexpectedly passed')
        except db.CommandError as error:
            assert 'requires reviewed private evidence' in str(error)
        assert query(name, "SELECT COUNT(*) FROM zsjos_schema_version WHERE version='V305';"
                     "SELECT COUNT(*) FROM zsjos_module_schema_version WHERE version='V305';")[0] == '0\n0'
    print('PASS MySQL first/failure/recovery/repeat/checksum/V305/UTF-8; retained schema:', name)


if __name__ == '__main__':
    if '--mysql' in sys.argv:
        mysql_regression()
    else:
        unittest.main()
