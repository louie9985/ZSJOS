#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Bootstrap entry structure and official client flags; real MySQL coverage is separate."""
import re
import subprocess
import tempfile
import unittest
from pathlib import Path
from unittest.mock import Mock, patch

import zsjos_db as db


class BootstrapClientGuardTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self.sql = self.root / 'fixture.sql'
        self.sql.write_text('SELECT 1;\n', encoding='utf-8')
        config = db.DbConfig('127.0.0.1', 3306, 'owned_fixture', 'fixture_user', '', 'test', self.root)
        with patch.object(db.shutil, 'which', side_effect=lambda command: '/fixture/' + command):
            self.client = db.MysqlClient(config)

    def test_guard_precedes_every_source_without_persistent_object_creation(self):
        text = (db.SQL_ROOT / 'bootstrap.sql').read_text(encoding='utf-8')
        executable = re.sub(r'^\s*--.*$', '', text, flags=re.MULTILINE)
        prefix = executable[:re.search(r'^SOURCE\s', executable, re.MULTILINE).start()]
        self.assertTrue(prefix.lstrip().startswith('SET NAMES utf8mb4;'))
        self.assertIsNone(re.search(r'\b(CREATE|DROP|ALTER|INSERT|UPDATE|DELETE|TRUNCATE|CALL)\b', prefix, re.I))
        self.assertEqual(2, len(re.findall(r'^SELECT\b', prefix, re.MULTILINE)))
        self.assertIn('DATABASE() IS NULL', prefix)
        self.assertIn('DATABASE() IS NOT NULL', prefix)
        for table, column in (('tables', 'table_schema'), ('routines', 'routine_schema'), ('events', 'event_schema')):
            self.assertIn(f'information_schema.{table} WHERE {column}=DATABASE()', prefix)
        self.assertIn('JSON_EXTRACT(IF(', prefix)
        self.assertIn("'{}', 'ZSJOS_BOOTSTRAP_REQUIRES_EMPTY_DATABASE'", prefix)
        self.assertIn('no database selected', prefix)
        self.assertIn('--skip-force', text)
        self.assertIn('Never use --force', text)

    def test_native_file_and_query_paths_override_mysql_force_defaults(self):
        with patch.object(db, 'run', return_value=Mock(stdout='1\n')) as run:
            self.assertEqual('1\n', self.client.execute_file(self.sql))
            self.assertEqual('1\n', self.client.query('SELECT 1;'))
        for call in run.call_args_list:
            command = call.args[0]
            self.assertEqual('/fixture/mysql', command[0])
            self.assertIn('--skip-force', command)
            self.assertNotIn('--force', command)
            self.assertIn('--batch', command)
        self.assertEqual('SELECT 1;\n', run.call_args_list[0].kwargs['input_text'])

    def test_native_file_failure_propagates_without_retry(self):
        with patch.object(db, 'run', side_effect=db.CommandError('ERROR 3141 bootstrap refused')) as run:
            with self.assertRaisesRegex(db.CommandError, 'bootstrap refused'):
                self.client.execute_file(self.sql)
        run.assert_called_once()

    def test_docker_file_path_disables_force_and_preserves_fatal_exit(self):
        failure = subprocess.CompletedProcess([], 1, b'ZSJOS_BOOTSTRAP_REQUIRES_EMPTY_DATABASE\n', b'ERROR 3141')
        with patch.object(db.subprocess, 'run', return_value=failure) as run:
            with self.assertRaisesRegex(db.CommandError, 'ERROR 3141'):
                db.docker_mysql_file('owned-fixture', self.sql)
            self.assertIs(failure, db.docker_mysql_file('owned-fixture', self.sql, expect_success=False))
        for call in run.call_args_list:
            command = call.args[0]
            self.assertIn('--skip-force', command)
            self.assertNotIn('--force', command)
            self.assertLess(command.index('--skip-force'), command.index('zsjos_test'))
            self.assertEqual('rb', call.kwargs['stdin'].mode)

    def test_mysql_only_flag_is_not_added_to_mysqldump(self):
        with patch.object(db.subprocess, 'run', return_value=subprocess.CompletedProcess([], 0, b'', b'')) as run:
            backup = self.client.backup()
        self.assertTrue(backup.exists())
        command = run.call_args.args[0]
        self.assertEqual('/fixture/mysqldump', command[0])
        self.assertNotIn('--skip-force', command)
        self.assertNotIn('--batch', command)

    def bootstrap_fixture(self):
        path = self.root / 'bootstrap.sql'
        path.write_text('CREATE TABLE must_not_run(id INT);\n', encoding='utf-8')
        return path

    def test_native_bootstrap_refuses_nonempty_no_schema_and_unexpected_probe_results(self):
        path = self.bootstrap_fixture()
        for output in ('0\n', 'NULL\n', '', '1\n0\n', 'probe warning\n1\n', '01\n', 'true\n'):
            with self.subTest(output=output), patch.object(db, 'SQL_ROOT', self.root), \
                    patch.object(db, 'run', return_value=Mock(stdout=output)) as run:
                with self.assertRaisesRegex(db.CommandError, '^ZSJOS_BOOTSTRAP_REQUIRES_EMPTY_DATABASE'):
                    self.client.execute_file(path)
                run.assert_called_once()
                self.assertNotIn('input_text', run.call_args.kwargs)
                command = run.call_args.args[0]
                self.assertIn('--execute', command)
                query = command[command.index('--execute') + 1]
                self.assertTrue(query.startswith('SELECT (DATABASE() IS NOT NULL'))
                for table in ('tables', 'routines', 'events'):
                    self.assertIn('information_schema.' + table, query)

    def test_native_bootstrap_probe_failure_has_stable_error_and_never_sends_file(self):
        path = self.bootstrap_fixture()
        with patch.object(db, 'SQL_ROOT', self.root), \
                patch.object(db, 'run', side_effect=db.CommandError('connection or metadata read failed')) as run:
            with self.assertRaisesRegex(db.CommandError, '^ZSJOS_BOOTSTRAP_REQUIRES_EMPTY_DATABASE'):
                self.client.execute_file(path)
            run.assert_called_once()
            self.assertNotIn('input_text', run.call_args.kwargs)

    def test_native_bootstrap_exact_empty_result_allows_file_after_read_only_probe(self):
        path = self.bootstrap_fixture()
        with patch.object(db, 'SQL_ROOT', self.root), \
                patch.object(db, 'run', side_effect=[Mock(stdout='1\n'), Mock(stdout='created\n')]) as run:
            self.assertEqual('created\n', self.client.execute_file(path))
        self.assertEqual(2, run.call_count)
        self.assertNotIn('input_text', run.call_args_list[0].kwargs)
        self.assertEqual(path.read_text(encoding='utf-8'), run.call_args_list[1].kwargs['input_text'])
        self.assertIn('--skip-force', run.call_args_list[1].args[0])

    def test_non_bootstrap_migration_does_not_require_an_empty_database(self):
        with patch.object(db, 'SQL_ROOT', self.root), patch.object(self.client, 'query') as probe, \
                patch.object(db, 'run', return_value=Mock(stdout='migrated\n')) as run:
            self.assertEqual('migrated\n', self.client.execute_file(self.sql))
        probe.assert_not_called()
        run.assert_called_once()
        self.assertEqual(self.sql.read_text(encoding='utf-8'), run.call_args.kwargs['input_text'])


if __name__ == '__main__':
    unittest.main()
