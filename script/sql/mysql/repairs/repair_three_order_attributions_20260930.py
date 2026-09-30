#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Scoped, approved test-environment ORDER attribution repair; not a migration.

Prerequisites: V279+, tenant 1, the three exact effective orders, existing matching
current-sourced receipt snapshots and System department/center mapping. Source
organization is explicitly current, never evidence of event-time affiliation.
Run without --apply first to freeze a private plan and rehearse using temporary
tables on the target MySQL engine. Then --apply repeats verification and inserts
only absent matching facts in one transaction; changed inputs/conflicts abort.
No orders, existing facts, permissions, dictionaries, or version ledgers change.
Failures before commit roll back. Post-commit removal requires separate approval
and must match the saved inserted IDs and complete after-images; no bulk rollback.
The evidence directory contains private snapshots and must not be committed.
"""
import argparse
import datetime
import hashlib
import json
import os
from pathlib import Path

import pymysql


TABLE = 'zsjos_performance_attribution'
ORDERS = ('OR202609050002', 'OR202609050005', 'OR202609180021')
MARK = 'three-order-attribution-20260930'
SOURCE = """
SELECT 'ORDER' fact_type,o.id fact_id,o.formal_sales_user_id user_id,
 a.user_name,a.dept_id,a.dept_name,a.center_id,a.center_name,o.lead_id,
 a.assignment_id,a.received_at,a.source_group,a.channel_code,a.channel_label,
 'current' org_source,%s creator,%s updater,1 tenant_id
FROM zsjos_order o
JOIN zsjos_performance_attribution a ON a.tenant_id=o.tenant_id
 AND a.lead_id=o.lead_id AND a.fact_type='ASSIGNMENT' AND a.deleted=0
 AND a.user_id=o.formal_sales_user_id AND a.org_source='current'
JOIN zsjos_lead_assignment_history h ON h.id=a.fact_id AND h.tenant_id=o.tenant_id
 AND h.deleted=0 AND h.lead_id=o.lead_id AND h.to_owner_user_id=o.formal_sales_user_id
 AND h.action_type IN ('accept','claim','transfer') AND h.occurred_at<=o.submitted_at
 AND a.received_at=h.occurred_at AND a.assignment_id=h.id
JOIN system_users u ON u.id=o.formal_sales_user_id AND u.tenant_id=o.tenant_id
 AND u.deleted=0 AND u.dept_id=a.dept_id
JOIN system_dept d ON d.id=a.dept_id AND d.tenant_id=o.tenant_id AND d.deleted=0
JOIN zsjos_performance_org m ON m.dept_id=d.id AND m.tenant_id=o.tenant_id AND m.deleted=0
 AND m.center_id=a.center_id
JOIN system_dept c ON c.id=a.center_id AND c.tenant_id=o.tenant_id AND c.deleted=0
WHERE o.tenant_id=1 AND o.deleted=0 AND o.status='effective'
 AND o.order_type='first_purchase' AND o.formal_sales_user_id=61 AND ((o.id=562 AND o.lead_id=4433 AND o.total_amount=2380.00) OR (o.id=565 AND o.lead_id=4379 AND o.total_amount=2380.00) OR (o.id=8852 AND o.lead_id=5005 AND o.total_amount=4980.00))
 AND o.order_no IN (%s,%s,%s) AND a.dept_id=1021 AND a.center_id=1020
 AND a.dept_name=d.name AND a.center_name=c.name
ORDER BY o.id
"""


def require(condition, message):
    if not condition:
        raise RuntimeError(message)


def serialized(value):
    return json.dumps(value, ensure_ascii=False, sort_keys=True,
                      default=lambda x: x.hex() if isinstance(x, bytes) else str(x))


def query(cursor, sql, args=()):
    cursor.execute(sql, args)
    return cursor.fetchall()


def digest(value):
    return hashlib.sha256(serialized(value).encode('utf-8')).hexdigest()


def insert_missing(cursor, table, rows):
    require(table in (TABLE, 'verify_attribution'), 'Invalid target table')
    changed = 0
    for row in rows:
        existing = query(cursor, f"SELECT * FROM {table} WHERE tenant_id=1 "
                         "AND fact_type='ORDER' AND fact_id=%s AND deleted=0 FOR UPDATE",
                         (row['fact_id'],))
        if existing:
            require(len(existing) == 1 and all(existing[0][k] == v for k, v in row.items()),
                    'Existing attribution differs; preserve it and stop')
            continue
        columns = ','.join(row)
        placeholders = ','.join(['%s'] * len(row))
        cursor.execute(f'INSERT INTO {table} ({columns}) VALUES ({placeholders})', tuple(row.values()))
        changed += cursor.rowcount
    return changed


def save(path, value):
    path.write_text(serialized(value) + '\n', encoding='utf-8')
    path.chmod(0o600)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--host', required=True)
    parser.add_argument('--port', type=int, required=True)
    parser.add_argument('--database', required=True)
    parser.add_argument('--user', required=True)
    parser.add_argument('--password-file', type=Path, required=True)
    parser.add_argument('--evidence-dir', type=Path, required=True)
    parser.add_argument('--apply', action='store_true')
    args = parser.parse_args()
    os.umask(0o077)
    args.evidence_dir.mkdir(parents=True, exist_ok=True, mode=0o700)
    connection = pymysql.connect(host=args.host, port=args.port, database=args.database,
                                 user=args.user, password=args.password_file.read_text().strip(),
                                 charset='utf8mb4', autocommit=False,
                                 cursorclass=pymysql.cursors.DictCursor)
    try:
        with connection.cursor() as cur:
            cur.execute('SET NAMES utf8mb4')
            rows = query(cur, SOURCE, (MARK, MARK, *ORDERS))
            require(len(rows) == 3 and len({r['fact_id'] for r in rows}) == 3,
                    'Expected exactly three source-backed candidates')
            plan_path = args.evidence_dir / 'plan.json'
            if plan_path.exists():
                require(json.loads(plan_path.read_text()) == json.loads(serialized(rows)),
                        'Frozen plan changed; stop for review')
            else:
                require(not args.apply, 'Run rehearsal to freeze the plan before applying')
                save(plan_path, rows)
            cur.execute(f'CREATE TEMPORARY TABLE verify_attribution LIKE {TABLE}')
            require(insert_missing(cur, 'verify_attribution', rows[:1]) == 1, 'Initial insertion failed')
            require(insert_missing(cur, 'verify_attribution', rows) == 2, 'Partial recovery failed')
            require(insert_missing(cur, 'verify_attribution', rows) == 0, 'Repeat was not a no-op')
            conflict = dict(rows[0], dept_id=-1)
            try:
                insert_missing(cur, 'verify_attribution', [conflict])
            except RuntimeError:
                pass
            else:
                raise RuntimeError('Conflict guard failed')
            hex_rows = query(cur, 'SELECT fact_id,HEX(user_name) user_name,HEX(dept_name) dept_name,'
                             'HEX(center_name) center_name,HEX(channel_label) channel_label '
                             'FROM verify_attribution ORDER BY fact_id')
            for row, stored in zip(rows, hex_rows):
                for key in ('user_name', 'dept_name', 'center_name', 'channel_label'):
                    expected = row[key].encode('utf-8').hex().upper() if row[key] is not None else None
                    require(stored[key] == expected, 'UTF-8 byte check failed')
            connection.rollback()
            print('PASS MySQL initial insertion, partial recovery, repeat no-op, conflict guard and UTF-8 HEX')
            if not args.apply:
                save(args.evidence_dir / 'rehearsal.json', {'passed': True, 'plan_sha256': digest(rows)})
                return
            require(query(cur, SOURCE + ' FOR UPDATE', (MARK, MARK, *ORDERS)) == rows,
                    'Source changed after rehearsal')
            order_before = query(cur, 'SELECT * FROM zsjos_order WHERE tenant_id=1 '
                                 'AND order_no IN (%s,%s,%s) ORDER BY id FOR UPDATE', ORDERS)
            facts_before = query(cur, f'SELECT * FROM {TABLE} ORDER BY id')
            evidence = args.evidence_dir / ('execution-' + datetime.datetime.now().strftime('%Y%m%d%H%M%S'))
            evidence.mkdir(mode=0o700)
            save(evidence / 'before.json', {'order_sha256': digest(order_before),
                 'attribution_sha256': digest(facts_before),
                 'existing_targets': [r for r in facts_before if r['tenant_id'] == 1
                     and r['fact_type'] == 'ORDER' and r['fact_id'] in {x['fact_id'] for x in rows}]})
            changed = insert_missing(cur, TABLE, rows)
            require(insert_missing(cur, TABLE, rows) == 0, 'Live repeat was not a no-op')
            order_after = query(cur, 'SELECT * FROM zsjos_order WHERE tenant_id=1 '
                                'AND order_no IN (%s,%s,%s) ORDER BY id', ORDERS)
            require(order_after == order_before, 'Order preservation failed')
            facts_after = query(cur, f'SELECT * FROM {TABLE} ORDER BY id')
            old_ids = {r['id'] for r in facts_before}
            require([r for r in facts_after if r['id'] in old_ids] == facts_before,
                    'Pre-existing attribution changed')
            new_rows = [r for r in facts_after if r['id'] not in old_ids]
            require(len(new_rows) == changed, 'Unexpected new attribution rows')
            save(evidence / 'after-before-commit.json', {'inserted': new_rows, 'insert_count': changed,
                 'orders_unchanged': True, 'preexisting_attribution_unchanged': True})
            connection.commit()
            require(insert_missing(cur, TABLE, rows) == 0, 'Committed readback failed')
            connection.rollback()
            save(evidence / 'committed.json', {'committed': True, 'insert_count': changed})
            print(f'PASS committed {changed} attribution inserts; existing facts and orders preserved')
    finally:
        connection.rollback()
        connection.close()


if __name__ == '__main__':
    main()
