#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Source-backed test-only repair; see docs/operations/partner-lead-source-account-repair.md."""
import argparse
import datetime
import hashlib
import json
import os
from pathlib import Path

import pymysql


SOURCE = """
SELECT l.id,l.tenant_id,l.lead_no,l.partner_id,l.source_user_id,
       o.id AS original_lead_id,o.submitter_id AS original_submitter_id,
       a.id AS account_id,HEX(p.name) AS partner_name_hex
FROM zsjos_lead l
JOIN ptml.leads_lead o ON BINARY o.lead_no=BINARY l.lead_no
JOIN ptml.accounts_user u ON u.id=o.submitter_id
JOIN zsjos_partner_account a ON BINARY a.mobile=BINARY u.phone
 AND a.tenant_id=l.tenant_id AND a.partner_id=l.partner_id
 AND a.deleted=0 AND a.status=0
JOIN zsjos_partner p ON p.id=l.partner_id AND p.tenant_id=l.tenant_id AND p.deleted=0
WHERE l.deleted=0 AND l.source_type='partner' AND l.provider_owner_type='partner'
 AND l.provider_owner_id=l.partner_id
 AND l.submission_idempotency_key REGEXP '^legacy-parttimecrm(3)?-[0-9]+$'
 AND CAST(SUBSTRING_INDEX(l.submission_idempotency_key,'-',-1) AS UNSIGNED)=o.id
 AND (l.source_user_id IS NULL OR l.source_user_id=a.id)
ORDER BY l.id
"""


def require(ok, message):
    if not ok:
        raise RuntimeError(message)


def encoded(value):
    def default(item):
        if isinstance(item, (datetime.datetime, datetime.date)):
            return item.isoformat()
        if isinstance(item, bytes):
            return {'hex': item.hex()}
        return str(item)
    return json.dumps(value, ensure_ascii=False, sort_keys=True, default=default, indent=2)


def query(cur, sql, params=None):
    cur.execute(sql, params)
    return cur.fetchall()


def update(cur, table, plan):
    count = 0
    for row in plan:
        current = query(cur, f'SELECT * FROM {table} WHERE id=%s AND tenant_id=%s FOR UPDATE',
                        (row['id'], row['tenant_id']))
        require(len(current) == 1, 'Missing or ambiguous target')
        value = current[0]['source_user_id']
        require(value is None or value == row['account_id'], 'Conflicting target source account')
        if value is None:
            cur.execute(f'UPDATE {table} SET source_user_id=%s,update_time=update_time '
                        'WHERE id=%s AND tenant_id=%s AND source_user_id IS NULL',
                        (row['account_id'], row['id'], row['tenant_id']))
            require(cur.rowcount == 1, 'Concurrent target update')
            count += 1
    return count


def verify(cur, plan):
    cur.execute('CREATE TEMPORARY TABLE verify_partner_source '
                '(id BIGINT PRIMARY KEY,tenant_id BIGINT,source_user_id BIGINT NULL,'
                'update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP '
                'ON UPDATE CURRENT_TIMESTAMP) ENGINE=InnoDB')
    cur.executemany('INSERT INTO verify_partner_source(id,tenant_id,source_user_id) VALUES(%s,%s,NULL)',
                    [(r['id'], r['tenant_id']) for r in plan])
    cur.execute('SAVEPOINT before_repair')
    require(update(cur, 'verify_partner_source', plan) == len(plan), 'Initial repair failed')
    require(update(cur, 'verify_partner_source', plan) == 0, 'Repeat changed rows')
    cur.execute('ROLLBACK TO SAVEPOINT before_repair')
    require(update(cur, 'verify_partner_source', plan[:1]) == 1, 'Partial repair failed')
    require(update(cur, 'verify_partner_source', plan) == len(plan)-1, 'Recovery failed')
    cur.execute('SAVEPOINT before_conflict')
    cur.execute('UPDATE verify_partner_source SET source_user_id=-1 WHERE id=%s', (plan[0]['id'],))
    try:
        update(cur, 'verify_partner_source', plan)
    except RuntimeError as error:
        require(str(error) == 'Conflicting target source account', 'Unexpected conflict failure')
    else:
        raise RuntimeError('Conflict was not rejected')
    cur.execute('ROLLBACK TO SAVEPOINT before_conflict')
    require(update(cur, 'verify_partner_source', plan) == 0, 'Conflict recovery failed')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--password-file', type=Path, required=True)
    parser.add_argument('--evidence-dir', type=Path, required=True)
    parser.add_argument('--apply', action='store_true')
    args = parser.parse_args()
    env = os.getenv('ZSJOS_AGENT_ENV')
    if not env:
        marker = Path('/etc/zsjos/agent-environment')
        env = marker.read_text().strip() if marker.exists() else 'local'
    require(env == 'test', 'This repair is authorized only for test')
    os.umask(0o077)
    args.evidence_dir.mkdir(mode=0o700, parents=True, exist_ok=True)
    connection = pymysql.connect(host='127.0.0.1', port=3306, user='root', database='zsjos',
                                 password=args.password_file.read_text().strip(), charset='utf8mb4',
                                 autocommit=False, cursorclass=pymysql.cursors.DictCursor)
    try:
        with connection.cursor() as cur:
            cur.execute('SET NAMES utf8mb4')
            cur.execute('SET SESSION TRANSACTION ISOLATION LEVEL SERIALIZABLE')
            path = args.evidence_dir / 'plan.json'
            available = query(cur, SOURCE)
            if path.exists():
                plan = json.loads(path.read_text(encoding='utf-8'))
            else:
                require(not args.apply, 'Prepare and verify the plan first')
                plan = [r for r in available if r['source_user_id'] is None]
                require(len(plan) == 115 and len({r['id'] for r in plan}) == 115,
                        'Expected exact 115-row source-backed scope')
                path.write_text(encoded(plan), encoding='utf-8')
            require(len(plan) == 115 and len({r['id'] for r in plan}) == 115, 'Invalid frozen scope')
            verify(cur, plan)
            connection.rollback()
            # Recheck original identity sources inside the same transaction as the guarded writes.
            available = query(cur, SOURCE + ' FOR SHARE')
            by_id = {r['id']: r for r in available}
            for row in plan:
                actual = by_id.get(row['id'])
                require(actual is not None, 'Original identity source changed')
                actual = dict(actual, source_user_id=None)
                require(actual == row, 'Frozen mapping changed')
                bytes.fromhex(row['partner_name_hex']).decode('utf-8', errors='strict')
            ids = [r['id'] for r in plan]
            placeholders = ','.join(['%s'] * len(ids))
            before = query(cur, f'SELECT * FROM zsjos_lead WHERE id IN ({placeholders}) ORDER BY id FOR UPDATE', ids)
            require(len(before) == len(plan), 'Target count changed')
            backup = args.evidence_dir / 'before.json'
            if not backup.exists():
                require(not args.apply, 'Prepare before-images first')
                backup.write_text(encoded(before), encoding='utf-8')
            original = json.loads(backup.read_text(encoding='utf-8'))
            normalized = json.loads(encoded(before))
            for a, b in zip(normalized, original):
                require({k:v for k,v in a.items() if k != 'source_user_id'} ==
                        {k:v for k,v in b.items() if k != 'source_user_id'}, 'Target changed since preparation')
            changed = update(cur, 'zsjos_lead', plan)
            require(changed in (0, 115), 'Unexpected partial live state; transaction aborted')
            after = query(cur, f'SELECT * FROM zsjos_lead WHERE id IN ({placeholders}) ORDER BY id', ids)
            expected = {r['id']:r['account_id'] for r in plan}
            for old, new in zip(before, after):
                require(new['source_user_id'] == expected[new['id']], 'Postcondition account mismatch')
                require({k:v for k,v in old.items() if k != 'source_user_id'} ==
                        {k:v for k,v in new.items() if k != 'source_user_id'}, 'Unrelated field changed')
            require(update(cur, 'zsjos_lead', plan) == 0, 'Live repeat changed rows')
            if args.apply:
                connection.commit()
                # Separate transaction proves committed state, rather than only pending writes.
                committed = query(cur, f'SELECT * FROM zsjos_lead WHERE id IN ({placeholders}) ORDER BY id', ids)
                require(committed == after, 'Committed readback differs')
                (args.evidence_dir / 'after.json').write_text(encoded(committed), encoding='utf-8')
            else:
                connection.rollback()
                restored = query(cur, f'SELECT * FROM zsjos_lead WHERE id IN ({placeholders}) ORDER BY id', ids)
                require(restored == before, 'Rehearsal rollback failed')
            print(json.dumps({'mode':'apply' if args.apply else 'rehearsal', 'planned':len(plan),
                              'changed':changed, 'temporary_tests':'passed', 'all_other_columns':'preserved',
                              'plan_sha256':hashlib.sha256(path.read_bytes()).hexdigest()}))
    except BaseException:
        connection.rollback()
        raise
    finally:
        connection.close()


if __name__ == '__main__':
    main()
