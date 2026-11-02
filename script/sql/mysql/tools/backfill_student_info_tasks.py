#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Preview or transactionally repair missing tenant-1 collection tasks after V359."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import sys

import pymysql

ROOT = Path(__file__).resolve().parents[4]
STATES = ('pending_approval',)
MARKER = 'student-info-backfill-v1'


def connect(local=False):
    if local:
        config = (ROOT / 'backend/yudao-server/src/main/resources/application-local.yaml').read_text(encoding='utf-8')
        match = re.search(r'url: jdbc:mysql://([^:/]+):(\d+)/([^?\s]+).*?\n\s*username:\s*([^\s#]+)\s*\n\s*password:\s*([^\s#]+)', config, re.S)
        if not match:
            raise ValueError('Cannot resolve local datasource')
        host, port, database, user, password = match.groups()
        password = password.strip('\"\'')
    else:
        host, database, user, password = [os.environ[k] for k in
            ('ZSJOS_DB_HOST', 'ZSJOS_DB_NAME', 'ZSJOS_DB_USER', 'ZSJOS_DB_PASSWORD')]
        port = os.environ.get('ZSJOS_DB_PORT', '3306')
    connection = pymysql.connect(host=host, port=int(port), database=database, user=user,
        password=password, charset='utf8mb4', autocommit=False,
        connect_timeout=10, read_timeout=120, write_timeout=120,
        cursorclass=pymysql.cursors.DictCursor)
    return connection, dict(host=host, port=int(port), database=database)


def preflight(q):
    q.execute('SELECT VERSION() AS version')
    if 'mariadb' in q.fetchone()['version'].lower():
        raise ValueError('This tool requires MySQL')
    for table, condition in [('zsjos_schema_version', ''),
            ('zsjos_module_schema_version', " AND module_code='core'")]:
        q.execute(f"SELECT COUNT(*) n FROM {table} WHERE version='V359'{condition}")
        if q.fetchone()['n'] != 1:
            raise ValueError('V359 required in both ledgers')
    tables = ('zsjos_order', 'zsjos_order_item', 'zsjos_student_info_task',
              'zsjos_student_info_form', 'zsjos_registration_case', 'zsjos_person')
    q.execute('SELECT table_name,engine FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN (' + ','.join(['%s']*len(tables)) + ')', tables)
    rows = q.fetchall()
    if len(rows) != len(tables) or any(r['ENGINE' if 'ENGINE' in r else 'engine'] != 'InnoDB' for r in rows):
        raise ValueError('Required tables must exist and use InnoDB')
    q.execute("SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index) cols FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='zsjos_student_info_task' AND non_unique=0 GROUP BY index_name")
    if not any(r['cols'] == 'tenant_id,order_item_id' for r in q.fetchall()):
        raise ValueError('Missing unique tenant/order-item index')


def plan(q, order_no=None, lock=False):
    # Same order-first lock order as application submission/termination. All repair
    # decisions are recomputed under locks when applying; previews never take locks.
    suffix = ' FOR UPDATE' if lock else ''
    args = list(STATES)
    where = ''
    if order_no:
        where = ' AND order_no=%s'
        args.append(order_no)
    q.execute("SELECT id,order_no,person_id,status FROM zsjos_order WHERE tenant_id=1 AND deleted=0 AND status IN (" + ','.join(['%s'] * len(STATES)) + ')' + where + ' ORDER BY id' + suffix, args)
    result, blocked = [], []
    for order in q.fetchall():
        q.execute('SELECT id FROM zsjos_person WHERE tenant_id=1 AND deleted=0 AND id=%s' + suffix, (order['person_id'],))
        valid_person = q.fetchone() is not None
        q.execute('SELECT id FROM zsjos_order_item WHERE tenant_id=1 AND deleted=0 AND order_id=%s ORDER BY id' + suffix, (order['id'],))
        items = q.fetchall()
        if not items:
            blocked.append(dict(order=order['order_no'], reason='no-active-items'))
        for item in items:
            q.execute('SELECT id,order_id,person_id,deleted FROM zsjos_student_info_task WHERE tenant_id=1 AND order_item_id=%s' + suffix, (item['id'],))
            task = q.fetchone()
            if task:
                if task['deleted'] not in (0, b'\x00') or task['order_id'] != order['id'] or task['person_id'] != order['person_id']:
                    blocked.append(dict(order=order['order_no'], item=item['id'], reason='existing-task-binding-conflict'))
                continue
            if not valid_person:
                blocked.append(dict(order=order['order_no'], item=item['id'], reason='missing-person'))
                continue
            # Legacy forms may only name the registration case. Never replace or
            # guess their binding: ambiguous/unbound records require manual review.
            q.execute('SELECT id,order_item_id,person_id FROM zsjos_student_info_form WHERE tenant_id=1 AND deleted=0 AND (order_item_id=%s OR registration_case_id IN (SELECT id FROM zsjos_registration_case WHERE tenant_id=1 AND order_id=%s AND order_item_id=%s))' + suffix,
                      (item['id'], order['id'], item['id']))
            forms = q.fetchall()
            if len(forms) > 1 or any(f['person_id'] != order['person_id'] or f['order_item_id'] != item['id'] for f in forms):
                blocked.append(dict(order=order['order_no'], item=item['id'], reason='historical-form-binding-conflict'))
                continue
            result.append(dict(order_id=order['id'], order_no=order['order_no'],
                order_item_id=item['id'], person_id=order['person_id'], form_id=forms[0]['id'] if forms else None))
    return result, blocked


def digest(target, rows, blocked):
    return hashlib.sha256(json.dumps([target, rows, blocked], sort_keys=True).encode()).hexdigest()


def insert(q, rows):
    for row in rows:
        q.execute('INSERT INTO zsjos_student_info_task (order_id,order_item_id,person_id,form_id,tenant_id,creator,updater) VALUES (%s,%s,%s,%s,1,%s,%s)',
            (row['order_id'], row['order_item_id'], row['person_id'], row['form_id'], MARKER, MARKER))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--local', action='store_true', help='Explicitly use application-local.yaml')
    parser.add_argument('--order-no', help='Optional single business order number')
    parser.add_argument('--apply', action='store_true')
    parser.add_argument('--expect-plan', help='SHA256 returned by preview, required for apply')
    args = parser.parse_args()
    if args.apply and not args.expect_plan:
        parser.error('--apply requires --expect-plan from preview')
    conn, target = connect(args.local)
    try:
        with conn.cursor() as q:
            preflight(q)
            conn.rollback()
            q.execute('SET SESSION innodb_lock_wait_timeout=15')
            q.execute('START TRANSACTION' if args.apply else 'START TRANSACTION READ ONLY')
            rows, blocked = plan(q, args.order_no, args.apply)
            fingerprint = digest(target, rows, blocked)
            print(json.dumps(dict(target=target, missing=len(rows), existing_forms=sum(r['form_id'] is not None for r in rows),
                blocked=blocked, plan=fingerprint), ensure_ascii=False))
            if args.apply:
                if blocked:
                    raise ValueError('Conflicts found; no writes committed')
                if fingerprint != args.expect_plan:
                    raise ValueError('Plan changed; preview again')
                insert(q, rows)
                remaining, conflicts = plan(q, args.order_no, True)
                if remaining or conflicts:
                    raise ValueError('Postcondition failed; transaction rolled back')
                conn.commit()
                print(json.dumps(dict(committed=len(rows), remaining=0, audit_marker=MARKER)))
            else:
                conn.rollback()
    finally:
        conn.rollback()
        conn.close()


if __name__ == '__main__':
    try:
        main()
    except Exception as exc:
        # DB exception payloads can contain sensitive values. Emit type/code only.
        if isinstance(exc, (ValueError, KeyError)):
            print(str(exc), file=sys.stderr)
        else:
            print('Database operation failed: ' + type(exc).__name__ + ' code=' + str(exc.args[0] if exc.args and isinstance(exc.args[0], int) else 'unknown'), file=sys.stderr)
        sys.exit(1)
