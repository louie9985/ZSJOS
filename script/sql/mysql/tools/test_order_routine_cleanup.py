# -*- coding: utf-8 -*-
"""MySQL lifecycle regression; only schema/one-row reads against the source database."""
import argparse
import copy
import hashlib
import uuid
from pathlib import Path

import order_history_repair as repair
from test_notice_version_recovery import connect


def run(container, source_database):
    name = 'zsjos_v305_cleanup_' + uuid.uuid4().hex[:12]
    source = connect(container, source_database)
    target = None
    try:
        with source.cursor() as q:
            q.execute('START TRANSACTION READ ONLY')
            admin = connect(container, source_database)
            with admin.cursor() as a:
                a.execute('CREATE DATABASE ' + repair.ident(name) + ' CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci')
            admin.close()
            target = connect(container, name)
            tables = repair.TABLES + ['zsjos_data_repair_backup', 'zsjos_schema_version', 'zsjos_module_schema_version']
            with target.cursor() as out:
                out.execute('SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci')
                for table in tables:
                    q.execute('SHOW CREATE TABLE ' + repair.ident(table))
                    out.execute(q.fetchone()['Create Table'])
                for table in ['zsjos_schema_version', 'zsjos_module_schema_version']:
                    q.execute('SELECT * FROM ' + repair.ident(table) + " WHERE version='V304'")
                    for row in q.fetchall():
                        out.execute('INSERT INTO ' + repair.ident(table) + ' (' + ','.join(repair.ident(k) for k in row)
                                    + ') VALUES (' + ','.join(['%s'] * len(row)) + ')', tuple(row.values()))
                q.execute('SELECT * FROM zsjos_opportunity WHERE tenant_id=1 ORDER BY id LIMIT 1')
                row = q.fetchone()
                assert row, 'Requires one existing opportunity for the isolated lifecycle fixture'
                q.execute('SHOW COLUMNS FROM zsjos_opportunity')
                columns = [r['Field'] for r in q.fetchall()
                           if not any(kind in r['Extra'] for kind in ('STORED GENERATED', 'VIRTUAL GENERATED'))]
                out.execute('INSERT INTO zsjos_opportunity (' + ','.join(repair.ident(k) for k in columns)
                            + ') VALUES (' + ','.join(['%s'] * len(columns)) + ')', tuple(row[k] for k in columns))
        source.rollback()
        with target.cursor() as q:
            types = repair.schema(q)
            before = repair.fetch(q, 'zsjos_opportunity', row['id'], types)
        after = copy.deepcopy(before)
        after['owner_user_id'] = 30 if before['owner_user_id'] != 30 else 529
        # Freeze the audit timestamp as real repair plans do, avoiding ON UPDATE drift.
        after['update_time'] = '2000-01-01 00:00:00'
        plan = {'version': repair.VERSION, 'repairKey': repair.KEY,
                'scriptSha256': hashlib.sha256(repair.SQL_PATH.read_bytes()).hexdigest(),
                'builderSha256': hashlib.sha256(Path(repair.__file__).with_name('order_history_repair_model.py').read_bytes()).hexdigest(),
                'operations': [{'table': 'zsjos_opportunity', 'before': before, 'after': after}],
                'dependencies': {}, 'counts': {'update:zsjos_opportunity': 1}, 'notes': [],
                'source': {'sha256': 'a' * 64}}
        sha = 'b' * 64

        def check_clean(expected):
            with target.cursor() as q:
                q.execute('SELECT COUNT(*) n FROM information_schema.routines WHERE routine_schema=DATABASE()')
                assert q.fetchone()['n'] == 0, 'V305 routine survived executor cleanup'
                assert repair.fetch(q, 'zsjos_opportunity', before['id'], types) == expected, 'Transaction outcome changed'
                q.execute("SELECT IS_FREE_LOCK(CONCAT('v305:',DATABASE())) free")
                assert q.fetchone()['free'] == 1, 'Repair lock leaked'

        with target.cursor() as q:
            q.execute("UPDATE zsjos_module_schema_version SET version='V304_hidden' WHERE module_code='core' AND version='V304'")
        try:
            repair.execute(target, plan, sha)
        except Exception as error:
            assert 'requires V304 in both ledgers' in str(error)
        else:
            raise AssertionError('Expected preparation failure')
        check_clean(before)
        with target.cursor() as q:
            q.execute("UPDATE zsjos_module_schema_version SET version='V304' WHERE module_code='core' AND version='V304_hidden'")
        # DDL cleanup must follow rollback, or it would commit the failed mutation.
        try:
            repair.execute(target, plan, sha, fail_after=1)
        except ValueError as error:
            assert str(error) == 'Injected failure for isolated test'
        else:
            raise AssertionError('Expected injected failure')
        check_clean(before)
        assert repair.execute(target, plan, sha, dry_run=True)['dryRun']
        check_clean(before)
        assert repair.execute(target, plan, sha)['changedOperations'] == 1
        check_clean(after)
        assert repair.execute(target, plan, sha)['changedOperations'] == 0
        check_clean(after)
        assert repair.execute(target, plan, sha, rollback_mode=True)['rollbackRehearsal']
        check_clean(after)
        print('PASS failure/dry-run/commit/repeat/recovery: clean routines, correct transaction outcome, released lock')
    finally:
        source.rollback()
        source.close()
        if target:
            target.close()
            admin = connect(container, source_database)
            with admin.cursor() as q:
                q.execute('DROP DATABASE ' + repair.ident(name))
            admin.close()
            print('CLEANED ' + name)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--container', required=True)
    parser.add_argument('--source-database', required=True)
    args = parser.parse_args()
    run(args.container, args.source_database)
