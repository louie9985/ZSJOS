#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Verify zero-mutation bootstrap rejection; --mysql uses only owned containers.

Default mode exercises transport/snapshot helpers without a database. Integration
mode executes real bootstrap SQL and the full fresh runner, never mock SQL results.
Snapshots compare actual metadata, object DDL and row dumps in memory; logs contain
only hashes/counts, not data dumps. No application database is selected.
"""
import argparse
import hashlib
import json
import re
import subprocess
import sys
import unittest
from unittest.mock import patch

import zsjos_db as db


PREFIX = 'ZSJOS_BOOTSTRAP_REQUIRES_EMPTY_DATABASE'
FORCE_FILE = '/tmp/zsjos-bootstrap-guard-force.cnf'


def validate_target(container, schema):
    if not re.fullmatch(r'zsjos-db-test-[0-9a-f]{10}', container):
        raise ValueError('Only the newly created test container is allowed')
    if schema is not None and schema != 'zsjos_test' and not re.fullmatch(r'boot_guard_[a-z_]+', schema):
        raise ValueError('Only private fixture schemas are allowed')


def mysql_command(container, schema, extra_defaults=None):
    validate_target(container, schema)
    command = ['docker', 'exec', '-i', '-w', '/workspace', container, 'mysql']
    if extra_defaults:
        if extra_defaults != FORCE_FILE:
            raise ValueError('Unexpected fixture option file')
        command.append('--defaults-extra-file=' + extra_defaults)
    command += ['--skip-force', '--default-character-set=utf8mb4', '-uroot',
                '--batch', '--raw', '--skip-column-names']
    if schema is not None:
        command.append(schema)
    return command


def stable_rows(data):
    # --skip-extended-insert makes each actual row one escaped INSERT line.
    # Retain duplicates; normalize only the physical enumeration order.
    return b'\n'.join(sorted(data.splitlines())) + b'\n'


def assert_unchanged(before, after):
    changed = sorted(key for key in before.keys() | after.keys() if before.get(key) != after.get(key))
    if changed:
        raise AssertionError('Bootstrap changed persistent snapshot component(s): ' + ', '.join(changed))


def digest(snapshot):
    hashes = {key: hashlib.sha256(value).hexdigest() for key, value in sorted(snapshot.items())}
    return hashlib.sha256(json.dumps(hashes, sort_keys=True).encode()).hexdigest()


class Client:
    def __init__(self, container, schema='zsjos_test'):
        validate_target(container, schema)
        self.container, self.schema = container, schema

    def query(self, sql):
        result = subprocess.run(mysql_command(self.container, self.schema),
                                input=('SET NAMES utf8mb4;\n' + sql).encode('utf-8'), capture_output=True)
        if result.returncode:
            raise db.CommandError(result.stderr.decode('utf-8', errors='replace'))
        return result.stdout

    def bootstrap(self, extra_defaults=None):
        return subprocess.run(mysql_command(self.container, self.schema, extra_defaults),
                              input=(db.SQL_ROOT / 'bootstrap.sql').read_bytes(), capture_output=True)

    def dump(self, structure):
        validate_target(self.container, self.schema)
        assert self.schema is not None
        command = ['docker', 'exec', self.container, 'mysqldump', '-uroot',
                   '--default-character-set=utf8mb4', '--compact', '--skip-comments',
                   '--skip-dump-date', '--skip-lock-tables', '--set-gtid-purged=OFF',
                   '--column-statistics=0', '--hex-blob']
        if structure:
            command += ['--no-data', '--routines', '--events', '--triggers']
        else:
            command += ['--no-create-info', '--skip-triggers', '--skip-routines', '--skip-events',
                        '--skip-extended-insert', '--order-by-primary']
        result = subprocess.run(command + [self.schema], capture_output=True)
        if result.returncode:
            raise db.CommandError(result.stderr.decode('utf-8', errors='replace'))
        return result.stdout if structure else stable_rows(result.stdout)

    def snapshot(self):
        queries = {
            'database': 'SHOW CREATE DATABASE `' + self.schema + '`;',
            'tables': "SELECT table_name,table_type,engine,table_collation,create_options,HEX(table_comment),"
                      "auto_increment,create_time FROM information_schema.tables "
                      "WHERE table_schema=DATABASE() ORDER BY table_name;",
            'columns': 'SELECT * FROM information_schema.columns WHERE table_schema=DATABASE() '
                       'ORDER BY table_name,ordinal_position;',
            'indexes': 'SELECT table_name,index_name,non_unique,seq_in_index,column_name,collation,sub_part,'
                       'packed,nullable,index_type,comment,index_comment,is_visible,`expression` '
                       'FROM information_schema.statistics WHERE table_schema=DATABASE() '
                       'ORDER BY table_name,index_name,seq_in_index;',
            'constraints': 'SELECT * FROM information_schema.table_constraints WHERE table_schema=DATABASE() '
                           'ORDER BY table_name,constraint_name;',
            'foreign_keys': 'SELECT * FROM information_schema.referential_constraints WHERE constraint_schema=DATABASE() '
                            'ORDER BY table_name,constraint_name;',
            'routines': 'SELECT * FROM information_schema.routines WHERE routine_schema=DATABASE() '
                        'ORDER BY routine_type,routine_name;',
            'parameters': 'SELECT * FROM information_schema.parameters WHERE specific_schema=DATABASE() '
                          'ORDER BY specific_name,ordinal_position;',
            'events': 'SELECT * FROM information_schema.events WHERE event_schema=DATABASE() ORDER BY event_name;',
            'triggers': 'SELECT * FROM information_schema.triggers WHERE trigger_schema=DATABASE() ORDER BY trigger_name;',
            'views': 'SELECT * FROM information_schema.views WHERE table_schema=DATABASE() ORDER BY table_name;',
        }
        result = {key: self.query(sql) for key, sql in queries.items()}
        result['object_ddl'] = self.dump(True)
        result['rows'] = self.dump(False)
        return result


def legacy_names():
    core = db.load_manifests()['core']
    tables = {table for table, _ in db.desired_schema(db.resolve_sql_path(core['schema']))[0]}
    return {old: new for old, new in core['baselineNamingUpgrade']['replacements'].items()
            if new in tables and re.fullmatch(r'[a-z0-9_]+', old)}


def reject_unchanged(client, case, results, extra_defaults=None):
    before = client.snapshot()
    rejected = client.bootstrap(extra_defaults)
    assert rejected.returncode != 0, f'{case}: non-empty bootstrap was accepted'
    assert PREFIX.encode() in rejected.stdout + rejected.stderr, f'{case}: missing clear guard diagnostic'
    after = client.snapshot()
    assert_unchanged(before, after)
    results.append({'case': case, 'passed': True, 'snapshot_components': len(before),
                    'persistent_signature': digest(before)})
    print('PASS ' + case, flush=True)


def small_fixtures(container, results):
    server = Client(container)

    def create(case, statements):
        name = 'boot_guard_' + case
        server.query(f'CREATE DATABASE `{name}` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;')
        client = Client(container, name)
        client.query(statements)
        return client

    common = """
CREATE TABLE sentinel(id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,label VARCHAR(80),
 stamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,KEY ix_label(label));
INSERT INTO sentinel(id,label) VALUES(7,'sentinel-preserved');
CREATE TABLE sentinel_child(id INT PRIMARY KEY,parent_id BIGINT,
 CONSTRAINT fk_sentinel FOREIGN KEY(parent_id) REFERENCES sentinel(id));
INSERT INTO sentinel_child VALUES(1,7);
CREATE VIEW fixture_view AS SELECT id,label FROM sentinel;
CREATE PROCEDURE fixture_routine() SELECT 1;
CREATE EVENT fixture_event ON SCHEDULE AT '2099-01-01 00:00:00' DISABLE DO SET @guard_event=1;
CREATE TRIGGER fixture_trigger BEFORE INSERT ON sentinel FOR EACH ROW SET NEW.label=CONCAT(NEW.label,'!');
CREATE TABLE zsjos_schema_version(version VARCHAR(64) PRIMARY KEY,checksum VARCHAR(128));
INSERT INTO zsjos_schema_version VALUES('V329','sentinel-core-checksum');
CREATE TABLE zsjos_module_schema_version(module_code VARCHAR(64),version VARCHAR(64),checksum VARCHAR(128),
 release_version VARCHAR(64),PRIMARY KEY(module_code,version));
INSERT INTO zsjos_module_schema_version VALUES('core','V329','sentinel-module-checksum','fixture');
"""
    names = legacy_names()
    for case, objects in [('legacy_schema', names.keys()), ('canonical_schema', names.values())]:
        ddl = '\n'.join(f'CREATE TABLE `{name}`(id INT PRIMARY KEY,label VARCHAR(40));' for name in objects)
        client = create(case, common + ddl)
        reject_unchanged(client, case, results)
        if case == 'canonical_schema':
            for old in names:
                assert client.query("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() "
                                    'AND table_name=' + db.sql_literal(old)).strip() == b'0'

    isolated = {
        'table_only': "CREATE TABLE sentinel(id INT PRIMARY KEY,label VARCHAR(40));INSERT INTO sentinel VALUES(1,'keep');",
        'routine_only': 'CREATE PROCEDURE fixture_routine() SELECT 1;',
        'function_only': 'CREATE FUNCTION fixture_function(arg_value INT) RETURNS INT DETERMINISTIC NO SQL RETURN arg_value;',
        'view_only': 'CREATE VIEW fixture_view AS SELECT 1 AS sentinel;',
        'event_only': "CREATE EVENT fixture_event ON SCHEDULE AT '2099-01-01 00:00:00' DISABLE DO SET @guard_event=1;",
    }
    for case, statements in isolated.items():
        reject_unchanged(create(case, statements), case, results)

    force = create('force_option', isolated['table_only'])
    written = subprocess.run(['docker', 'exec', '-i', container, 'sh', '-c', 'cat > ' + FORCE_FILE],
                             input=b'[mysql]\nforce\n', capture_output=True)
    assert written.returncode == 0
    reject_unchanged(force, 'defaults_force_overridden_by_skip_force', results, FORCE_FILE)

    before = server.snapshot()
    rejected = Client(container, None).bootstrap()
    assert rejected.returncode != 0 and PREFIX.encode() in rejected.stdout + rejected.stderr
    assert b'no database selected' in rejected.stdout + rejected.stderr
    assert_unchanged(before, server.snapshot())
    results.append({'case': 'no_database_selected_rejected', 'passed': True})


def mysql_contract():
    results = []
    lifecycle = db.with_test_mysql

    def extend(callback):
        def execute(container):
            small_fixtures(container, results)
            # Other non-empty databases must not prevent this genuinely empty
            # selected database from bootstrapping and completing every migration.
            client = Client(container)
            assert client.query('SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE();').strip() == b'0'
            callback(container)
            results.append({'case': 'empty_database_full_fresh', 'passed': True})
            assert not db.schema_drift(db.DockerTestClient(container), db.load_manifests())
            reject_unchanged(client, 'upgraded_full_fresh_replay', results)
        return lifecycle(execute)

    completed = False
    try:
        with patch.object(db, 'with_test_mysql', side_effect=extend):
            db.test_fresh()
        completed = True
    finally:
        print(json.dumps({'completed': completed, 'image': db.MYSQL_IMAGE,
                          'application_database_touched': False, 'results': results},
                         ensure_ascii=False, indent=2))


class BootstrapEntryGuardTest(unittest.TestCase):
    def test_only_owned_container_and_fixture_schemas_are_allowed(self):
        validate_target('zsjos-db-test-0123456789', 'boot_guard_view_only')
        for container, schema in [('yudao-mysql','zsjos_test'), ('zsjos-db-test-0123456789','ruoyi-vue-pro')]:
            with self.assertRaises(ValueError):
                validate_target(container, schema)

    def test_force_config_is_overridden_by_official_client_argument(self):
        command = mysql_command('zsjos-db-test-0123456789','zsjos_test',FORCE_FILE)
        self.assertLess(command.index('--defaults-extra-file=' + FORCE_FILE), command.index('--skip-force'))

    def test_snapshot_comparison_detects_each_persistent_component(self):
        before = {'rows': b'private fixture row', 'routines': b'body', 'indexes': b'unique'}
        assert_unchanged(before, dict(before))
        for component in before:
            changed = dict(before);changed[component] = b'changed'
            with self.assertRaisesRegex(AssertionError, component) as error:
                assert_unchanged(before, changed)
            self.assertNotIn('private fixture row', str(error.exception))

    def test_row_order_normalization_preserves_duplicates(self):
        self.assertEqual(stable_rows(b'b\na\na\n'),stable_rows(b'a\nb\na\n'))
        self.assertNotEqual(stable_rows(b'a\na\n'),stable_rows(b'a\n'))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--mysql', action='store_true')
    options = parser.parse_args()
    if options.mysql:
        mysql_contract()
    else:
        unittest.main(argv=[sys.argv[0]], verbosity=2)
