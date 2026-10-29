#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Exact authorized test-data closure; see registration-legacy-close-20261028.md."""
import argparse
import datetime as dt
import decimal
import json
import os
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'tools'))
from zsjos_db import db_config
import pymysql

MARKER = 'registration-legacy-close-20261028'
REASON = '旧数据处理20261028'
IDS = (799,806,807,808,809,810,812,814,815,816,819,821,822,823,825,827,
       843,844,845,846,849,850,851,852,853,854,856,857,860,865,866,
       1118,1119,1120,1121,1122,1123,1124,1125,1126)
ID_SQL = ','.join(map(str, IDS))
TABLE = 'zsjos_registration_case'
DEPENDENTS = ('zsjos_service_relation', 'zsjos_student_info_form',
              'zsjos_registration_command', 'zsjos_registration_case_checklist_item',
              'zsjos_registration_case_route', 'zsjos_registration_class_assignment',
              'zsjos_registration_item_attachment', 'zsjos_registration_item')


def require(ok, message):
    if not ok:
        raise RuntimeError(message)


def encoded(value):
    def default(x):
        if isinstance(x, bytes): return {'$bytes': x.hex()}
        if isinstance(x, dt.datetime): return {'$datetime': x.isoformat()}
        if isinstance(x, dt.date): return {'$date': x.isoformat()}
        if isinstance(x, decimal.Decimal): return {'$decimal': str(x)}
        raise TypeError(type(x).__name__)
    return json.dumps(value, default=default, ensure_ascii=False, sort_keys=True, indent=2)


def read(path):
    def hook(x):
        if set(x) == {'$bytes'}: return bytes.fromhex(x['$bytes'])
        if set(x) == {'$datetime'}: return dt.datetime.fromisoformat(x['$datetime'])
        if set(x) == {'$date'}: return dt.date.fromisoformat(x['$date'])
        if set(x) == {'$decimal'}: return decimal.Decimal(x['$decimal'])
        return x
    return json.loads(path.read_text(encoding='utf-8'), object_hook=hook)


def save(path, value):
    with path.open('x', encoding='utf-8') as f:
        f.write(encoded(value)); f.flush(); os.fsync(f.fileno())


def connect():
    c = db_config('test')
    db = pymysql.connect(host=c.host, port=c.port, user=c.user, password=c.password,
                         database=c.database, charset='utf8mb4', autocommit=False,
                         cursorclass=pymysql.cursors.DictCursor)
    with db.cursor() as q:
        q.execute('SET NAMES utf8mb4')
        q.execute("SET time_zone = '+08:00'")
        q.execute('SET SESSION innodb_lock_wait_timeout=10')
    return db


def rows(q, sql, args=None):
    q.execute(sql, args)
    return list(q.fetchall())


def cases(q, table=TABLE):
    return rows(q, f'SELECT * FROM {table} WHERE tenant_id=1 AND deleted=0 ORDER BY id')


def capture(q, lock=False):
    suffix = ' FOR UPDATE' if lock else ''
    # Follow the application's order -> registration lock order.
    orders = rows(q, f'SELECT * FROM zsjos_order WHERE tenant_id=1 AND id IN '
                  f'(SELECT order_id FROM {TABLE} WHERE tenant_id=1 AND id IN ({ID_SQL})) ORDER BY id' + suffix)
    rows(q, f'SELECT * FROM {TABLE} WHERE tenant_id=1 AND id IN ({ID_SQL}) ORDER BY id' + suffix)
    state = {'cases': cases(q), 'orders': orders}
    order_ids = ','.join(str(r['id']) for r in orders)
    for table in ('zsjos_order_item', 'zsjos_order_approval_round'):
        state[table] = rows(q, f'SELECT * FROM {table} WHERE tenant_id=1 AND order_id IN ({order_ids}) ORDER BY id')
    for table in DEPENDENTS:
        state[table] = rows(q, f'SELECT * FROM {table} WHERE tenant_id=1 AND registration_case_id IN ({ID_SQL}) ORDER BY id' + suffix)
    state['tasks'] = rows(q, "SELECT * FROM zsjos_business_task WHERE tenant_id=1 "
                          f"AND biz_type IN ('registration_case','registration-case') AND biz_id IN ({ID_SQL}) ORDER BY id" + suffix)
    return state


def targets(state):
    return [r for r in state['cases'] if r['id'] in IDS]


def validate(state):
    selected = targets(state)
    require(len(selected) == 40, 'Target count changed')
    orders = {r['id']: r for r in state['orders']}
    require(len(orders) == 31, 'Parent order count changed')
    require(sum(orders[r['order_id']]['status'] == 'superseded' for r in selected) == 39
            and sum(orders[r['order_id']]['status'] == 'terminated' for r in selected) == 1,
            'Parent order statuses changed')
    for r in selected:
        require(r['status'] == 'pending' and r['order_item_id'] is not None
                and r['creator'] == 'legacy-increment-20260921' and r['updater'] == 'V317'
                and r['assignment_mode'] == 'auto' and r['checklist_version_id'] is None
                and r['completed_by_user_id'] is None and r['cancelled_at'] is None
                and orders[r['order_id']]['deleted'] == b'\x00', 'Target prerequisite changed')
    # No live dependent lifecycle may be skipped by this narrow database correction.
    for table in (*DEPENDENTS, 'tasks'):
        require(not any(r['deleted'] == b'\x00' for r in state[table]), 'Active dependent found: ' + table)


def expected(before, now):
    return [dict(r, status='ended', cancel_reason=REASON, cancelled_at=now, completed_at=now,
                 version=r['version']+1, updater=MARKER, update_time=now)
            if r['id'] in IDS else dict(r) for r in before]


def apply(q, before, now, table=TABLE, fail_after=None):
    current = cases(q, table)
    after = expected(before, now)
    if current == after: return 0
    require(current == before, 'Before-image changed; refusing partial or concurrent overwrite')
    count = 0
    for r in before:
        if r['id'] not in IDS: continue
        q.execute(f"UPDATE {table} SET status='ended',cancel_reason=%s,cancelled_at=%s,completed_at=%s,"
                  "version=version+1,updater=%s,update_time=%s WHERE id=%s AND tenant_id=1 AND deleted=0 "
                  "AND status='pending' AND version=%s AND order_item_id=%s",
                  (REASON,now,now,MARKER,now,r['id'],r['version'],r['order_item_id']))
        require(q.rowcount == 1, 'Concurrent target change')
        count += 1
        if count == fail_after: raise RuntimeError('Injected fixture failure')
    require(cases(q, table) == after, 'Field-whitelist verification failed')
    return count


def fixtures(db, before, now):
    table = 'tmp_registration_close_20261028'
    with db.cursor() as q:
        q.execute(f'CREATE TEMPORARY TABLE {table} LIKE {TABLE}')
        q.execute(f'INSERT INTO {table} SELECT * FROM {TABLE} WHERE tenant_id=1 AND deleted=0')
    db.commit()
    with db.cursor() as q:
        require(cases(q, table) == before, 'Fixture snapshot changed')
        for n in (1,20,40):
            try:
                apply(q, before, now, table, n)
                raise RuntimeError('Failure injection did not run')
            except RuntimeError as error:
                require(str(error) == 'Injected fixture failure', 'Unexpected fixture failure')
                db.rollback()
            require(cases(q, table) == before, 'Fixture rollback failed')
        require(apply(q, before, now, table) == 40, 'Fixture first application failed')
        require(apply(q, before, now, table) == 0, 'Fixture repeat failed')
        db.commit()
        q.execute(f'UPDATE {table} SET version=version+1 WHERE id=%s', (IDS[0],))
        try:
            apply(q, before, now, table)
            raise RuntimeError('Post-edit rejection did not run')
        except RuntimeError as error:
            require(str(error).startswith('Before-image changed'), 'Unexpected post-edit failure')
        db.rollback()
        require(cases(q, table) == expected(before, now), 'Post-edit rollback failed')
    return {'first':40, 'repeat':0, 'failure_rollback':[1,20,40], 'recovery':True, 'post_edit_rejected':True}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--evidence-dir', required=True, type=Path)
    parser.add_argument('--apply', action='store_true')
    args = parser.parse_args()
    env_file = Path('/etc/zsjos/agent-environment')
    env = os.environ.get('ZSJOS_AGENT_ENV', env_file.read_text().strip() if env_file.exists() else 'local')
    require(env == 'test', 'Only the authorized test environment is allowed')
    os.umask(0o077)
    args.evidence_dir.mkdir(parents=True, exist_ok=True, mode=0o700)
    db = connect()
    try:
        with db.cursor() as q:
            state = capture(q)
            if (args.evidence_dir/'pending-after.json').exists():
                planned = read(args.evidence_dir/'pending-after.json')
                if state == planned:
                    print('Already applied; exact post-image verified; zero writes')
                    return
                require(state == read(args.evidence_dir/'before.json'), 'Existing evidence differs; inspect before recovery')
                raise RuntimeError('Rolled-back evidence exists; use a new evidence directory after review')
            validate(state)
            q.execute('SELECT NOW() now'); now = q.fetchone()['now']
        db.rollback()
        test_result = fixtures(db, state['cases'], now)
        save(args.evidence_dir/'fixture-results.json', test_result)
        db.rollback()
        if not args.apply:
            print('Verified 40 targets and controlled MySQL fixtures; no business writes')
            return
        with db.cursor() as q:
            before = capture(q, lock=True)
            validate(before)
            require(before == state, 'Live prerequisites changed during verification')
            q.execute('SELECT NOW() now'); now = q.fetchone()['now']
            after = dict(before, cases=expected(before['cases'], now))
            save(args.evidence_dir/'before.json', before)
            save(args.evidence_dir/'pending-after.json', after)
            count = apply(q, before['cases'], now)
            require(capture(q) == after, 'Related records changed; refusing commit')
            result = rows(q, f'SELECT id,HEX(cancel_reason) reason_hex FROM {TABLE} WHERE tenant_id=1 AND id IN ({ID_SQL})')
            require(len(result) == 40 and all(r['reason_hex'] == REASON.encode('utf-8').hex().upper() for r in result), 'UTF-8 check failed')
        db.commit()
        # An independent connection distinguishes a commit from session-local results.
        check = connect()
        try:
            with check.cursor() as q:
                require(capture(q) == after, 'Committed post-image differs; inspect evidence')
                readback = rows(q, f'SELECT id,status,cancel_reason,HEX(cancel_reason) reason_hex FROM {TABLE} WHERE tenant_id=1 AND id IN ({ID_SQL})')
                require(all(r['status']=='ended' and r['cancel_reason']==REASON
                            and r['reason_hex']==REASON.encode('utf-8').hex().upper() for r in readback), 'Independent UTF-8 readback failed')
            save(args.evidence_dir/'verified.json', {'count':count, 'time':now, 'status':'ended', 'reason':REASON,
                                                  'independent_readback':True, 'utf8_hex':True, 'unchanged_related':True})
        finally:
            check.rollback(); check.close()
        print('Committed and independently verified: 40 ended; exact reason/UTF-8; protected records unchanged')
    finally:
        db.rollback(); db.close()


if __name__ == '__main__':
    try: main()
    except Exception as error:
        print('Closure stopped: ' + (str(error) if type(error) is RuntimeError else type(error).__name__), file=sys.stderr)
        raise SystemExit(1)
