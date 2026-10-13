#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""MySQL empty-history migration guards; cleans only schemas created in this run."""
import argparse
from pathlib import Path
import re
import uuid

import pymysql
from test_notice_version_recovery import connect
import test_notice_content_capacity as fixtures
from order_history_repair import statements


def run(container, source_schema):
    source = connect(container, source_schema)
    tables = ['zsjos_schema_version', 'zsjos_module_schema_version', 'zsjos_lead',
              'zsjos_order', 'zsjos_order_approval_round', 'zsjos_data_repair_backup',
              'zsjos_performance_attribution', 'system_dict_data', 'system_users', 'zsjos_partner_account']
    definitions = []
    with source.cursor() as cur:
        for table in tables:
            cur.execute(f'SHOW CREATE TABLE `{table}`')
            definitions.append(cur.fetchone()['Create Table'])
    source.close()
    checks = fixtures.Checks(container)
    for version in (298, 299, 302, 303):
        name = checks.create('guard_' + str(version) + '_' + uuid.uuid4().hex[:6])
        conn = connect(container, name)
        try:
            with conn.cursor() as cur:
                for definition in definitions:
                    cur.execute(definition)
            path = next(fixtures.SQL.glob(f'migrations/V{version}__*'))

            def apply():
                with conn.cursor() as cur:
                    for sql in statements(path.read_text()):
                        cur.execute(sql)

            def marker_count():
                with conn.cursor() as cur:
                    cur.execute("SELECT COUNT(*) n FROM zsjos_schema_version WHERE version=%s", (f'V{version}',))
                    a = cur.fetchone()['n']
                    cur.execute("SELECT COUNT(*) n FROM zsjos_module_schema_version WHERE version=%s", (f'V{version}',))
                    return a, cur.fetchone()['n']

            def reject():
                try:
                    apply()
                    raise AssertionError('Expected migration rejection')
                except pymysql.MySQLError:
                    pass
                assert marker_count() == (0, 0)

            reject()
            with conn.cursor() as cur:
                cur.execute("INSERT INTO zsjos_schema_version(version,description) VALUES(%s,'fixture')", (f'V{version-1}',))
                cur.execute("INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version) VALUES('core',%s,'fixture','fixture','fixture')", (f'V{version-1}',))
                cur.execute("CREATE TRIGGER fail_second BEFORE INSERT ON zsjos_module_schema_version FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='injected second ledger failure'")
            reject()
            with conn.cursor() as cur:
                cur.execute('DROP TRIGGER fail_second')
            apply()
            assert marker_count() == (1, 1)
            apply()
            assert marker_count() == (1, 1)
            with conn.cursor() as cur:
                cur.execute('SELECT COUNT(*) n FROM system_dict_data')
                assert cur.fetchone()['n'] == 0
                # Start a new first-application fixture with one synthetic active business row.
                cur.execute('DELETE FROM zsjos_schema_version WHERE version=%s', (f'V{version}',))
                cur.execute('DELETE FROM zsjos_module_schema_version WHERE version=%s', (f'V{version}',))
                table = 'zsjos_order' if version == 299 else 'zsjos_lead'
                cur.execute(f'SHOW COLUMNS FROM `{table}`')
                values = {}
                for column in cur.fetchall():
                    if column['Null'] == 'NO' and column['Default'] is None and 'auto_increment' not in column['Extra'] and 'GENERATED' not in column['Extra']:
                        kind = column['Type'].lower()
                        values[column['Field']] = ('fixture' if re.match(r'(?:var)?char|.*text', kind)
                                                  else '{}' if kind == 'json'
                                                  else '2026-01-01 00:00:00' if 'time' in kind or kind == 'date'
                                                  else 1)
                values['deleted'] = 0
                cur.execute(f"INSERT INTO `{table}` (" + ','.join('`'+k+'`' for k in values) + ') VALUES (' + ','.join(['%s']*len(values)) + ')', tuple(values.values()))
            reject()
            print('PASS', version, 'empty first/repeat, missing prerequisite, atomic failure/recovery, populated guard, no dictionary seeds', flush=True)
        finally:
            conn.close()
            checks.query(None, f'DROP DATABASE `{name}`')
            print('CLEANED', name, flush=True)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--container', required=True)
    parser.add_argument('--source-schema', required=True, help='Existing isolated fully migrated fixture schema')
    args = parser.parse_args()
    if not args.source_schema.startswith('nc313_'):
        parser.error('Use the isolated nc313 fixture, never the business schema')
    run(args.container, args.source_schema)
