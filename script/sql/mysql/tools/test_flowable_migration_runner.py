#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""V377 runner regression; optional retained MySQL fixtures, never writes the source DB."""
import dataclasses
import os
import subprocess
import tempfile
import unittest
import uuid
from pathlib import Path
from unittest.mock import Mock, patch

import zsjos_db as db


V377 = db.SQL_ROOT / 'migrations/V377__flowable_8_schema_metadata.sql'


class FlowableRunnerTest(unittest.TestCase):
    def setUp(self):
        self.config = db.DbConfig('localhost', 3306, 'fixture', 'fixture', '', 'test', Path('/tmp'))

    def client(self, mode=''):
        with patch.object(db.shutil, 'which', side_effect=lambda name: '/fixture/' + name):
            return db.MysqlClient(dataclasses.replace(self.config, flowable_id_generator=mode))

    def test_uuid_declaration_and_sql_share_one_connection_without_changing_file(self):
        original = V377.read_bytes()
        with patch.object(db, 'run', return_value=Mock(stdout='')) as run:
            self.client('strong-uuid').execute_file(V377)
        run.assert_called_once()
        self.assertEqual("SET @zsjos_v377_id_generator='strong-uuid';\n" + original.decode(),
                         run.call_args.kwargs['input_text'])
        self.assertIn('--skip-force', run.call_args.args[0])
        self.assertEqual(original, V377.read_bytes())

    def test_default_and_database_modes_preserve_sql_guard(self):
        for mode in ('', 'database'):
            with self.subTest(mode=mode), patch.object(db, 'run', return_value=Mock(stdout='')) as run:
                self.client(mode).execute_file(V377)
            prefix = "SET @zsjos_v377_id_generator='database';\n" if mode else ''
            self.assertEqual(prefix + V377.read_text(encoding='utf-8'), run.call_args.kwargs['input_text'])

    def test_unknown_mode_rejected_before_any_mysql_process(self):
        for mode in ('uuid', "strong-uuid'; SELECT 1; --"):
            with self.subTest(mode=mode), patch.object(db, 'run') as run:
                with self.assertRaisesRegex(db.CommandError, 'ZSJOS_DB_FLOWABLE_ID_GENERATOR'):
                    self.client(mode)
                run.assert_not_called()

    def test_mode_is_not_injected_into_queries_or_other_sql_files(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / V377.name
            path.write_text('SELECT 1;\n', encoding='utf-8')
            with patch.object(db, 'run', return_value=Mock(stdout='')) as run:
                client = self.client('strong-uuid')
                client.execute_file(path)
                client.query('SELECT 2;')
            self.assertEqual('SELECT 1;\n', run.call_args_list[0].kwargs['input_text'])
            self.assertNotIn('input_text', run.call_args_list[1].kwargs)

    def test_environment_config_reaches_client(self):
        with patch.object(db, 'read_env_file', return_value={}), patch.dict(os.environ, {
                'ZSJOS_DB_PASSWORD': 'fixture-only', 'ZSJOS_DB_FLOWABLE_ID_GENERATOR': 'strong-uuid',
                'ZSJOS_DB_PASSWORD_FILE': ''}):
            self.assertEqual('strong-uuid', db.db_config('fixture').flowable_id_generator)

    def preflight(self, mode='', has_counter=False, populated=True, pending=True):
        client = Mock(spec=db.MysqlClient)
        client.config = dataclasses.replace(self.config, flowable_id_generator=mode)
        client.table_exists.return_value = True
        client.query.side_effect = ['1' if has_counter else '0', 'ACT_RU_TASK\n', '1' if populated else '0']
        migration = db.Migration('core', 'V377', 377, 'fixture', V377, db.sha256(V377))
        with patch.object(db, 'installed_versions', return_value={}), \
                patch.object(db, 'pending_migrations', return_value=[migration] if pending else []):
            result = db.migration_preflight(client, {})
        return client, result

    def test_preflight_blocks_populated_engine_without_verified_uuid(self):
        for mode in ('', 'database'):
            with self.subTest(mode=mode):
                _, (_, blockers) = self.preflight(mode)
                self.assertIn('ZSJOS_DB_FLOWABLE_ID_GENERATOR=strong-uuid', blockers[0])

    def test_preflight_allows_empty_engine_existing_counter_and_verified_uuid(self):
        for options in ({'populated': False}, {'has_counter': True}, {'mode': 'strong-uuid'}, {'pending': False}):
            with self.subTest(options=options):
                client, (_, blockers) = self.preflight(**options)
                self.assertEqual([], blockers)
                if options.get('mode') or options.get('pending') is False:
                    client.query.assert_not_called()


class ContainerClient(db.MysqlClient):
    def base(self, executable):
        return ['docker', 'exec', '-i', os.environ['ZSJOS_V377_TEST_CONTAINER'], 'sh', '-c',
                'MYSQL_PWD="$(cat /run/secrets/mysql-root-password)" exec mysql '
                '--default-character-set=utf8mb4 --batch --raw --skip-column-names --skip-force -uroot "$@"',
                'mysql']


@unittest.skipUnless(os.environ.get('ZSJOS_V377_TEST_CONTAINER') and os.environ.get('ZSJOS_V377_TEST_DATABASE'),
                     'set ZSJOS_V377_TEST_CONTAINER and ZSJOS_V377_TEST_DATABASE for retained MySQL fixtures')
class FlowableMysqlTest(unittest.TestCase):
    def test_real_runner_refusal_uuid_replay_failure_and_recovery(self):
        source = os.environ['ZSJOS_V377_TEST_DATABASE']
        config = db.DbConfig('', 3306, source, '', '', 'fixture', Path('/tmp'))
        with patch.object(db.shutil, 'which', side_effect=lambda name: name):
            client = ContainerClient(config)
        tables = client.query("SELECT table_name FROM information_schema.tables WHERE table_schema=DATABASE() "
                              "AND (UPPER(table_name) REGEXP '^(ACT|FLW)_' "
                              "OR table_name IN ('zsjos_schema_version','zsjos_module_schema_version'))").splitlines()
        before = client.query('CHECKSUM TABLE ' + ','.join('`' + name + '`' for name in tables))
        command = ['docker', 'exec', os.environ['ZSJOS_V377_TEST_CONTAINER'], 'sh', '-c',
                   'MYSQL_PWD="$(cat /run/secrets/mysql-root-password)" exec mysqldump -uroot '
                   '--no-data --skip-add-drop-table --skip-triggers --set-gtid-purged=OFF "$@"', 'dump', source] + tables
        schema = subprocess.check_output(command).decode('utf-8')
        prefix = 'v377r_' + uuid.uuid4().hex[:8]

        def fixture(suffix, mode='', populated=True):
            name = prefix + suffix
            client.query('CREATE DATABASE `' + name + '` CHARACTER SET utf8mb4')
            with patch.object(db.shutil, 'which', side_effect=lambda command: command):
                target = ContainerClient(dataclasses.replace(config, database=name, flowable_id_generator=mode))
            # File execution goes through the actual MysqlClient path and a real MySQL subprocess.
            with tempfile.TemporaryDirectory() as directory:
                path = Path(directory) / 'fixture.sql'
                path.write_text(schema, encoding='utf-8')
                target.execute_file(path)
            if populated:
                target.query("INSERT INTO ACT_RU_TASK(ID_,REV_,NAME_) VALUES ('fixture-uuid',1,'fixture')")
            return target

        def state(target):
            return target.query('CHECKSUM TABLE ACT_GE_PROPERTY,ACT_ID_PROPERTY,zsjos_schema_version,'
                                'zsjos_module_schema_version,ACT_RU_TASK')

        def reject(target):
            original = state(target)
            with self.assertRaises(db.CommandError):
                target.execute_file(V377)
            self.assertEqual(original, state(target))

        unknown = fixture('_unknown')
        reject(unknown)
        unknown.config = dataclasses.replace(unknown.config, flowable_id_generator='database')
        reject(unknown)
        unknown.config = dataclasses.replace(unknown.config, flowable_id_generator='strong-uuid')
        unknown.execute_file(V377)
        self.assertEqual('0', unknown.query("SELECT COUNT(*) FROM ACT_GE_PROPERTY WHERE NAME_='next.dbid'").strip())
        original = state(unknown)
        unknown.execute_file(V377)
        self.assertEqual(original, state(unknown))
        self.assertEqual('1\t1', unknown.query(
            "SELECT (SELECT COUNT(*) FROM zsjos_schema_version WHERE version='V377'),"
            "(SELECT COUNT(*) FROM zsjos_module_schema_version WHERE module_code='core' AND version='V377')").strip())

        existing = fixture('_counter', 'strong-uuid')
        existing.query("INSERT INTO ACT_GE_PROPERTY VALUES ('next.dbid','54321',7)")
        existing.execute_file(V377)
        self.assertEqual('54321\t7', existing.query("SELECT VALUE_,REV_ FROM ACT_GE_PROPERTY WHERE NAME_='next.dbid'").strip())
        existing.query("UPDATE ACT_GE_PROPERTY SET VALUE_='bad' WHERE NAME_='next.dbid'")
        reject(existing)

        empty = fixture('_empty', populated=False)
        empty.execute_file(V377)
        self.assertEqual('1', empty.query("SELECT VALUE_ FROM ACT_GE_PROPERTY WHERE NAME_='next.dbid'").strip())

        ledger = fixture('_ledger', 'strong-uuid')
        ledger.query("CREATE TRIGGER refuse_marker BEFORE INSERT ON zsjos_module_schema_version "
                     "FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='fixture ledger failure'")
        reject(ledger)
        ledger.query('DROP TRIGGER refuse_marker')
        ledger.execute_file(V377)

        partial = fixture('_partial', 'strong-uuid')
        partial.query("INSERT INTO ACT_GE_PROPERTY VALUES ('schema.version','8.0.0.0',1)")
        partial.execute_file(V377)
        self.assertEqual('3', partial.query("SELECT COUNT(*) FROM ACT_GE_PROPERTY WHERE "
            "NAME_ IN ('schema.version','common.schema.version','eventregistry.schema.version') AND VALUE_='8.0.0.0'").strip())

        shape = fixture('_shape', 'strong-uuid')
        shape.query('ALTER TABLE ACT_RU_TASK ADD COLUMN fixture_extra INT')
        reject(shape)
        self.assertEqual(before, client.query('CHECKSUM TABLE ' + ','.join('`' + name + '`' for name in tables)))
        print('PASS real runner MySQL fixtures retained:', prefix)


if __name__ == '__main__':
    unittest.main()
