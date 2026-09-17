#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Controlled fresh/upgrade replay in an existing local MySQL container; retains test DBs.

Uses the container's MYSQL_ROOT_PASSWORD without printing it. Never targets development
data, starts/stops services or deletes databases. Run with --container yudao-mysql.
"""
import argparse
import datetime
import re
import subprocess
import zsjos_db as db


def expand(path):
    return re.sub(r'(?im)^[ \t]*SOURCE[ \t]+([^;\r\n]+\.sql)[ \t]*;',
                  lambda m: expand(db.ROOT / m[1].strip()), path.read_text(encoding='utf-8-sig'))


class Client:
    def __init__(self, container, database):
        self.container, self.database = container, database

    def query(self, sql):
        result = subprocess.run(['docker', 'exec', '-i', self.container, 'sh', '-c',
            'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql --default-character-set=utf8mb4 '
            '-uroot --batch --raw --skip-column-names ' + self.database],
            input=('SET NAMES utf8mb4;\n' + sql).encode('utf-8'), capture_output=True)
        if result.returncode:
            errors = [line for line in result.stderr.decode('utf-8', errors='replace').splitlines()
                      if line.startswith('ERROR')]
            raise RuntimeError('\n'.join(errors) or 'MySQL execution failed')
        return result.stdout.decode('utf-8').strip()

    def table_exists(self, table):
        return self.query("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() "
                          f"AND table_name={db.sql_literal(table)}") == '1'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--container', required=True)
    parser.add_argument('--resume', help='Resume only a retained gp_MMDDHHMMSS test database')
    parser.add_argument('--resume-after', type=int, help='Last successfully executed Core version in the retained test run')
    args = parser.parse_args()
    db.static_check()
    name = args.resume or 'gp_' + datetime.datetime.now().strftime('%m%d%H%M%S')
    if not re.fullmatch(r'gp_\d{10}', name):
        parser.error('Only a gp_MMDDHHMMSS controlled test database is permitted')
    admin = Client(args.container, '')
    if not args.resume:
        admin.query(f'CREATE DATABASE `{name}` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;')
    client = Client(args.container, name)
    print('Controlled database (retained):', name, flush=True)
    if not args.resume:
        client.query(expand(db.SQL_ROOT / 'bootstrap.sql'))
    assert client.query('SELECT COUNT(*) FROM system_role_menu') == '0'
    print('PASS: fresh baseline has no role-menu assignments', flush=True)
    manifests = db.load_manifests()
    pending = db.pending_migrations(manifests, db.installed_versions(client))
    if args.resume:
        if args.resume_after is None:
            parser.error('--resume requires --resume-after; legacy scripts may not register both ledgers')
        pending = [m for m in pending if m.module != 'core' or m.number > args.resume_after]
    for index, migration in enumerate(pending, 1):
        try:
            client.query(expand(migration.path))
        except RuntimeError as error:
            raise RuntimeError(f'{migration.path.name}: {error}') from None
        if index % 20 == 0:
            print(f'Applied {index}/{len(pending)}: {migration.path.name}', flush=True)
    assert client.query('SELECT COUNT(*) FROM system_role_menu') == '0'
    result = client.query(expand(db.SQL_ROOT / 'verify/core.sql'))
    failures = [line for line in result.splitlines() if re.search(r'(?:^|\t)(FAIL|MISSING)$', line)]
    print('Fresh verification failures:', len(failures), flush=True)
    for line in failures:
        print(line, flush=True)
    # Deliberately include deleted and cross-tenant assignments: migration replay must
    # leave administrator-owned rows byte-for-byte unchanged, even when audits flag them.
    client.query("""INSERT INTO system_role_menu(id,role_id,menu_id,tenant_id,creator,updater,deleted)
        VALUES (990000001,1,6735,1,'policy-fixture','policy-fixture',0),
               (990000002,1,6736,2,'policy-fixture','policy-fixture',1);""")
    before = client.query('SELECT * FROM system_role_menu ORDER BY id')
    for pattern in ['V246__*','V249__*','V250__*','V251__*','V252__*','V254__*']:
        path = next((db.SQL_ROOT / 'migrations').glob(pattern))
        for _ in range(2):
            client.query(expand(path))
            assert client.query('SELECT * FROM system_role_menu ORDER BY id') == before, path.name
    client.query(expand(db.SQL_ROOT / 'permissions/media-account-profile-query.sql'))
    assert client.query('SELECT * FROM system_role_menu ORDER BY id') == before
    assert client.query("SELECT COUNT(*) FROM zsjos_schema_version WHERE version IN ('V251','V252')") == '2'
    assert client.query("SELECT HEX(name) FROM system_menu WHERE permission='zsjos:media-account:query' AND deleted=0") == '查看账号档案'.encode().hex().upper()
    print('PASS: upgrade/repeat preserves grants; V251/V252 ledger; query-menu UTF-8 HEX', flush=True)
    if failures:
        raise SystemExit(1)


if __name__ == '__main__':
    main()
