#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Fresh acceptance contract: helper unit tests by default; real MySQL with --mysql.

--mysql extends only the lifetime of db.with_test_mysql's newly created private
container. It first executes the real full test_fresh path, then adversarial
probes, without mocking SQL results or bypassing any acceptance assertion.
No application database/ledger or repository migration file is changed.
"""
import argparse
import json
import re
import subprocess
import sys
import unittest
from types import SimpleNamespace
from unittest.mock import patch

import role_menu_policy
import zsjos_db as db


TERMINOLOGY_CHECK = 'V159 public sea terminology'
VERSION_CHECK = 'student_delivery_stages_checksums'


def owned_container(name):
    if not re.fullmatch(r'zsjos-db-test-[0-9a-f]{10}', name):
        raise ValueError('Only a newly owned zsjos-db-test container is permitted')
    return name


def extract_check(text, label):
    """Locate one real SELECT while ignoring comments and quoted semicolons."""
    pattern = re.compile(r"^\s*SELECT\s+'" + re.escape(label.replace("'", "''"))
                         + r"'\s+AS\s+check_(?:name|item)\b", re.M | re.I)
    masked = role_menu_policy.executable_sql(text)
    starts = []
    for match in pattern.finditer(text):
        start = match.start() + len(match.group(0)) - len(match.group(0).lstrip())
        if masked[start:start + 6].upper() == 'SELECT':
            starts.append(start)
    if len(starts) != 1:
        raise ValueError(f'Expected one executable assertion: {label}')
    end = masked.find(';', starts[0])
    if end < 0:
        raise ValueError(f'Unterminated assertion: {label}')
    return text[starts[0]:end + 1]


def assert_result(output, label, expected):
    rows = [line.split('\t') for line in output.splitlines()
            if line.split('\t')[0] == label]
    if len(rows) != 1 or rows[0][-1] != expected:
        raise AssertionError(f'{label}: expected exactly one {expected} result, got {rows}')



class FixtureClient(db.DockerTestClient):
    def __init__(self, container):
        super().__init__(owned_container(container))

    def query(self, statement):
        # Each call is a single connection, so START/UPDATE/assertion/ROLLBACK
        # probes observe their own changes without committing synthetic damage.
        result = subprocess.run([
            'docker', 'exec', '-i', self.container, 'mysql',
            '--default-character-set=utf8mb4', '-uroot', '--batch', '--raw',
            '--skip-column-names', 'zsjos_test',
        ], input=('SET NAMES utf8mb4;\n' + statement).encode('utf-8'), capture_output=True)
        if result.returncode:
            errors = [line for line in result.stderr.decode('utf-8', errors='replace').splitlines()
                      if line.startswith('ERROR')]
            raise db.CommandError('\n'.join(errors) or 'Fixture query failed')
        return result.stdout.decode('utf-8')


def negative_cases(container, results):
    client = FixtureClient(container)
    manifests = db.load_manifests()
    text = (db.SQL_ROOT / 'verify-bootstrap.sql').read_text(encoding='utf-8-sig')
    terminology = extract_check(text, TERMINOLOGY_CHECK)
    version = extract_check(text, VERSION_CHECK)
    assert_result(client.query(terminology), TERMINOLOGY_CHECK, 'PASS')
    assert_result(client.query(version), VERSION_CHECK, 'PASS')
    assert client.query("SELECT COUNT(*) FROM zsjos_module_schema_version WHERE module_code='core' AND version='V358';").strip() == '1'

    def transaction_probe(case, mutation, assertion, label):
        output = client.query('START TRANSACTION;\n' + mutation + '\n' + assertion + '\nROLLBACK;')
        assert_result(output, label, 'FAIL')
        assert_result(client.query(assertion), label, 'PASS')
        results.append({'case': case, 'passed': True, 'restoration': 'ROLLBACK then positive assertion'})

    transaction_probe('wrong_menu_name_rejected',
                      "UPDATE system_menu SET name='fixture_wrong_name' WHERE id=6794;",
                      terminology, TERMINOLOGY_CHECK)
    transaction_probe('wrong_menu_permission_rejected',
                      "UPDATE system_menu SET permission='fixture:wrong:permission' WHERE id=6794;",
                      terminology, TERMINOLOGY_CHECK)
    transaction_probe('missing_module_version_rejected',
                      "UPDATE zsjos_module_schema_version SET version='V114_fixture_wrong' "
                      "WHERE module_code='core' AND version='V114';", version, VERSION_CHECK)

    # Integrity verification opens separate read connections. Commit this one
    # synthetic field, verify the actual persisted tampering is rejected without
    # auto-repair, then restore exactly the captured value in this private fixture.
    query = "SELECT checksum FROM zsjos_module_schema_version WHERE module_code='core' AND version='V114';"
    original = client.query(query).strip()
    assert re.fullmatch(r'[0-9a-f]{64}', original), 'Fresh runner did not record a file checksum'
    db.verify_applied_migration_integrity(client, manifests)
    try:
        client.query("UPDATE zsjos_module_schema_version SET checksum=REPEAT('0',64) "
                     "WHERE module_code='core' AND version='V114';")
        assert client.query(query).strip() == '0' * 64
        try:
            db.verify_applied_migration_integrity(client, manifests)
        except db.CommandError as error:
            assert 'checksum changed' in str(error) and 'V114' in str(error), str(error)
        else:
            raise AssertionError('Read-only integrity accepted the tampered real ledger')
        assert client.query(query).strip() == '0' * 64, 'Verifier silently repaired the synthetic ledger'
    finally:
        client.query('UPDATE zsjos_module_schema_version SET checksum=' + db.sql_literal(original)
                     + " WHERE module_code='core' AND version='V114';")
    assert client.query(query).strip() == original
    db.verify_applied_migration_integrity(client, manifests)
    results.append({'case': 'tampered_checksum_rejected_without_repair', 'passed': True,
                    'restoration': 'exact synthetic checksum restored and integrity passed'})

    # Deliberately last: MySQL DDL auto-commits. The full fixture is disposable
    # and has no Lead business rows; db.with_test_mysql removes only its container.
    assert client.query('SELECT COUNT(*) FROM zsjos_lead;').strip() == '0'
    before = db.schema_drift(client, manifests)
    assert not before, 'Unexpected pre-existing fixture drift: ' + json.dumps(before)
    expected = 'missing column zsjos_lead.remark'
    client.query('ALTER TABLE zsjos_lead DROP COLUMN remark;')
    drift = db.schema_drift(client, manifests)
    assert set(drift) == {expected}, drift
    results.append({'case': 'missing_column_rejected', 'passed': True,
                    'newly_detected': [expected]})


def mysql_contract():
    results = []
    lifecycle = db.with_test_mysql

    def extended_lifecycle(callback):
        def execute(container):
            owned_container(container)
            callback(container)  # Real full fresh chain, SQL assertions, integrity and drift.
            results.append({'case': 'full_fresh_positive', 'passed': True})
            negative_cases(container, results)
        return lifecycle(execute)

    completed = False
    try:
        # Only lifecycle composition is patched: no connection, result, SQL,
        # migration, integrity checker or acceptance predicate is substituted.
        with patch.object(db, 'with_test_mysql', side_effect=extended_lifecycle):
            db.test_fresh()
        completed = True
    finally:
        print(json.dumps({'completed': completed, 'image': db.MYSQL_IMAGE,
                          'application_database_touched': False, 'results': results},
                         ensure_ascii=False, indent=2))


class FreshVerificationContractTest(unittest.TestCase):
    def test_query_transport_preserves_trailing_empty_metadata_fields(self):
        # Wire-format unit regression only; MySQL acceptance below uses real processes.
        result = SimpleNamespace(returncode=0, stdout=b'fixture\tfield\t\n', stderr=b'')
        with patch.object(subprocess, 'run', return_value=result):
            output = FixtureClient('zsjos-db-test-0123456789').query('SELECT fixture;')
        self.assertEqual('fixture\tfield\t\n', output)

    def test_extract_ignores_comment_and_quoted_semicolons(self):
        sql = "/*\nSELECT 'target' AS check_name, 'FAIL';\n*/\n" \
              "SELECT 'target' AS check_name, IF('a;b'='a;b','PASS','FAIL');\nSELECT 2;"
        selected = extract_check(sql, 'target')
        self.assertTrue(selected.startswith("SELECT 'target'"))
        self.assertIn("'a;b'", selected)
        self.assertNotIn('SELECT 2', selected)

    def test_extract_rejects_absent_duplicate_or_unterminated_assertion(self):
        for sql in ('SELECT 1;', "SELECT 'target' AS check_name,'PASS'",
                    "SELECT 'target' AS check_name,'PASS';\nSELECT 'target' AS check_name,'FAIL';"):
            with self.assertRaises(ValueError):
                extract_check(sql, 'target')

    def test_result_requires_exact_single_expected_status(self):
        assert_result('other\tPASS\ntarget\tFAIL', 'target', 'FAIL')
        for output in ('target\tPASS', '', 'target\tFAIL\ntarget\tFAIL'):
            with self.assertRaises(AssertionError):
                assert_result(output, 'target', 'FAIL')

    def test_application_container_is_not_an_allowed_target(self):
        self.assertEqual('zsjos-db-test-0123456789', owned_container('zsjos-db-test-0123456789'))
        for name in ('yudao-mysql', 'zsjos-db-test-', 'zsjos-db-test-0123456789; mysql'):
            with self.assertRaises(ValueError):
                owned_container(name)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--mysql', action='store_true', help='Run a real owned full-fresh MySQL fixture')
    options = parser.parse_args()
    if options.mysql:
        mysql_contract()
    else:
        unittest.main(argv=[sys.argv[0]], verbosity=2)
