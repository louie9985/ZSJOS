#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Confirmed single-item test database repair; no runtime or migration changes."""
import argparse
import os
from pathlib import Path

from transfer_student_20261015 import connect, require
from backfill_order_self_source_20261103 import decode, digest, rows, save
from link_approved_repurchase_leads_20261105 import environment

MARK = 'collection-case1244-20261113'
TABLE = 'zsjos_student_info_task'
TEMP = 'verify_collection_case1244'
ORDER_NO = 'OD202611021716320007'
ORDER_ID, ITEM_ID, CASE_ID = 9158, 9664, 1244


def capture(q, lock=False):
    suffix = ' FOR UPDATE NOWAIT' if lock else ''
    conditions = {
        'zsjos_order': 'id=9158',
        'zsjos_order_item': 'id=9664',
        'zsjos_registration_case': 'id=1244',
        'zsjos_registration_case_checklist_item': 'registration_case_id=1244',
        'zsjos_order_approval_round': 'order_id=9158',
    }
    result = {table: rows(q, f'SELECT * FROM {table} WHERE tenant_id=1 AND {where} ORDER BY id' + suffix)
              for table, where in conditions.items()}
    order = result['zsjos_order']
    require(len(order) == 1 and order[0]['order_no'] == ORDER_NO
            and order[0]['status'] == 'effective' and order[0]['deleted'] == b'\0'
            and order[0]['person_id'] is not None, 'Order precondition changed')
    person = order[0]['person_id']
    result['zsjos_person'] = rows(q, 'SELECT * FROM zsjos_person WHERE tenant_id=1 AND id=%s' + suffix, (person,))
    result['zsjos_student_info_form'] = rows(q, 'SELECT * FROM zsjos_student_info_form WHERE tenant_id=1 '
        'AND (person_id=%s OR order_item_id=9664 OR registration_case_id=1244) ORDER BY id' + suffix, (person,))
    result[TABLE] = rows(q, f'SELECT * FROM {TABLE} WHERE tenant_id=1 '
        'AND (person_id=%s OR order_item_id=9664) ORDER BY id' + suffix, (person,))
    item, case = result['zsjos_order_item'], result['zsjos_registration_case']
    require(len(item) == len(case) == 1 and item[0]['order_id'] == ORDER_ID
            and item[0]['deleted'] == b'\0' and case[0]['order_id'] == ORDER_ID
            and case[0]['order_item_id'] == ITEM_ID and case[0]['status'] == 'processing'
            and case[0]['deleted'] == b'\0', 'Item/fulfillment precondition changed')
    require(len(result['zsjos_person']) == 1 and result['zsjos_person'][0]['deleted'] == b'\0', 'Customer unavailable')
    require(not any(f['deleted'] == b'\0' and (f['order_item_id'] == ITEM_ID or f['registration_case_id'] == CASE_ID)
                    for f in result['zsjos_student_info_form']), 'Existing form needs review')
    return result


def target(state):
    return [r for r in state[TABLE] if r['order_item_id'] == ITEM_ID]


def insert(q, person, table=TABLE):
    require(table in (TABLE, TEMP), 'Invalid writer target')
    existing = rows(q, f'SELECT * FROM {table} WHERE tenant_id=1 AND order_item_id=%s', (ITEM_ID,))
    if existing:
        r = existing[0]
        require(len(existing) == 1 and r['order_id'] == ORDER_ID and r['person_id'] == person
                and r['creator'] == MARK and r['updater'] == MARK and r['deleted'] == b'\0'
                and r['version'] == 0 and all(r[k] is None for k in
                ('form_id', 'source_form_id', 'source_revision', 'operator_user_id',
                 'last_command_key', 'last_command_fingerprint')), 'Existing task differs; do not overwrite')
        return 0
    q.execute(f'INSERT INTO {table} (order_id,order_item_id,person_id,tenant_id,creator,updater) '
              'VALUES (%s,%s,%s,1,%s,%s)', (ORDER_ID, ITEM_ID, person, MARK, MARK))
    require(q.rowcount == 1, 'Unexpected inserted count')
    return 1


def rehearsal(c, q, person):
    q.execute(f'CREATE TEMPORARY TABLE {TEMP} LIKE {TABLE}')
    c.commit()
    require(insert(q, person, TEMP) == 1, 'Initial insert failed')
    c.rollback()
    require(not rows(q, f'SELECT id FROM {TEMP}'), 'Rollback failed')
    require(insert(q, person, TEMP) == 1, 'Recovery insert failed')
    c.commit()
    require(insert(q, person, TEMP) == 0, 'Replay inserted again')
    c.rollback()
    q.execute(f'UPDATE {TEMP} SET version=1')
    try:
        insert(q, person, TEMP)
    except RuntimeError:
        pass
    else:
        raise RuntimeError('Changed task was not rejected')
    c.rollback()
    require(insert(q, person, TEMP) == 0, 'Recovery replay failed')
    c.rollback()
    q.execute(f'DROP TEMPORARY TABLE {TEMP}')


def protected(state):
    result = dict(state)
    result[TABLE] = [r for r in state[TABLE] if r['order_item_id'] != ITEM_ID]
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('mode', choices=('prepare', 'apply', 'verify'))
    parser.add_argument('--evidence-dir', type=Path, required=True)
    parser.add_argument('--plan', type=Path)
    args = parser.parse_args()
    require(args.mode == 'prepare' or args.plan is not None, 'Prepared plan required')
    os.umask(0o077)
    args.evidence_dir.mkdir(parents=True, mode=0o700, exist_ok=False)
    c = connect('zsjos-mysql-1')
    try:
        with c.cursor() as q:
            q.execute('SET NAMES utf8mb4')
            q.execute("SET time_zone='+08:00'")
            q.execute('SET SESSION innodb_lock_wait_timeout=5')
            identity = environment(q)
            require(not rows(q, 'SELECT trigger_name FROM information_schema.triggers '
                    'WHERE trigger_schema=DATABASE() AND event_object_table=%s', (TABLE,)), 'Unexpected task trigger')
            require(rows(q, 'SELECT COUNT(*) n FROM information_schema.statistics WHERE table_schema=DATABASE() '
                    "AND table_name=%s AND index_name='uk_tenant_item' AND non_unique=0", (TABLE,))[0]['n'] == 2,
                    'Missing task unique key')
            c.rollback()
            if args.mode == 'prepare':
                q.execute('START TRANSACTION READ ONLY')
                before = capture(q)
                require(not target(before), 'Target is no longer missing')
                plan = dict(marker=MARK, identity=identity, before=before)
                save(args.evidence_dir / 'plan.json', plan)
                c.rollback()
                rehearsal(c, q, before['zsjos_order'][0]['person_id'])
                save(args.evidence_dir / 'receipt.json', dict(plan_sha256=digest(plan), rehearsal='PASS'))
                print('Prepared one missing task; MySQL initial/rollback/recovery/replay/conflict checks PASS')
                return
            plan = decode(args.plan.read_text(encoding='utf-8'))
            require(plan['marker'] == MARK and plan['identity'] == identity, 'Plan target changed')
            receipt = decode((args.plan.parent / 'receipt.json').read_text(encoding='utf-8'))
            require(receipt == dict(plan_sha256=digest(plan), rehearsal='PASS'), 'Plan lacks matching rehearsal evidence')
            q.execute('START TRANSACTION' if args.mode == 'apply' else 'START TRANSACTION READ ONLY')
            before = capture(q, lock=args.mode == 'apply')
            require(digest(protected(before)) == digest(protected(plan['before'])), 'Protected preimage changed')
            count = 0
            if args.mode == 'apply':
                save(args.evidence_dir / 'before.json', before)
                count = insert(q, before['zsjos_order'][0]['person_id'])
            else:
                require(len(target(before)) == 1, 'Repaired task missing')
                r = target(before)[0]
                require(r['creator'] == MARK and r['order_id'] == ORDER_ID and r['deleted'] == b'\0'
                        and r['person_id'] == before['zsjos_order'][0]['person_id']
                        and r['form_id'] is None and r['version'] == 0, 'Unexpected repaired task state')
            after = capture(q)
            require(digest(protected(after)) == digest(protected(before)), 'Protected rows changed')
            require(len(target(after)) == 1, 'Task postcondition failed')
            name_hex = rows(q, 'SELECT HEX(student_name) value FROM zsjos_order WHERE id=9158 AND tenant_id=1')[0]['value']
            require(name_hex == after['zsjos_order'][0]['student_name'].encode('utf-8').hex().upper(), 'UTF-8 mismatch')
            save(args.evidence_dir / 'after.json', after)
            save(args.evidence_dir / 'precommit.json', dict(plan_sha256=digest(plan), inserted=count,
                 task_id=target(after)[0]['id'], protected_sha256=digest(protected(after)), utf8_hex=True))
            if args.mode == 'apply':
                c.commit()
            else:
                c.rollback()
            save(args.evidence_dir / 'receipt.json', dict(mode=args.mode, inserted=count, task_id=target(after)[0]['id']))
            print(f'{args.mode}: inserted={count}, task_id={target(after)[0]["id"]}, protected rows and UTF-8 PASS')
    except BaseException:
        c.rollback()
        raise
    finally:
        c.close()


if __name__ == '__main__':
    try:
        main()
    except Exception as error:
        print('Repair stopped: ' + type(error).__name__)
        raise SystemExit(1)
