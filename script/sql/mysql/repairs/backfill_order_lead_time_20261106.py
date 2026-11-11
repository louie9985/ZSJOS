#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Scoped test-tenant missing Lead time repair; see the dated operation document.

Prepare freezes private row images and rehearses the exact SQL writer in temporary
MySQL tables. Apply requires that tested plan; verify performs read-only checks.
"""
import argparse
import copy
import json
import os
from pathlib import Path
from zoneinfo import ZoneInfo

from transfer_student_20261015 import connect, require
from backfill_order_self_source_20261103 import (
    ROUND, ORDER, LEAD, encode, decode, digest, save, rows, execute,
    normalized, capture_rounds,
)
from link_approved_repurchase_leads_20261105 import environment

MARK = 'order-lead-time-20261106'
COUNT = 659
TEMP = 'verify_' + ROUND


def state(q, lock=False):
    suffix = ' FOR SHARE NOWAIT' if lock else ''
    result = {t: rows(q, f'SELECT * FROM {t} WHERE tenant_id=1 ORDER BY id' + suffix)
              for t in (ORDER, ROUND)}
    result[LEAD] = rows(q, 'SELECT id,tenant_id,deleted,lead_no,person_id,create_time '
                       'FROM zsjos_lead WHERE tenant_id=1 AND id IN '
                       '(SELECT lead_id FROM zsjos_order WHERE tenant_id=1) ORDER BY id' + suffix)
    require(not rows(q, 'SELECT trigger_name FROM information_schema.triggers '
                     "WHERE trigger_schema=DATABASE() AND event_object_table='zsjos_order_approval_round'"),
            'Unexpected approval-round trigger')
    return result


def prepare(q):
    before = state(q)
    orders = {r['id']: r for r in before[ORDER]}
    leads = {r['id']: r for r in before[LEAD]}
    now = rows(q, 'SELECT NOW() t')[0]['t']
    changes = []
    for original in before[ROUND]:
        order = orders.get(original['order_id'])
        if not order or order['deleted'] != b'\0' or original['deleted'] != b'\0':
            continue
        snapshot = json.loads(original['order_snapshot'] or '{}')
        profile = snapshot.get('leadProfile')
        if not isinstance(profile, dict) or profile.get('submittedAt') is not None:
            continue
        lead = leads.get(order['lead_id'])
        require(lead is not None and lead['deleted'] == b'\0' and lead['create_time'] is not None,
                'Missing source; review scope')
        require(profile.get('leadNo') == lead['lead_no'] and lead['person_id'] == order['person_id']
                and snapshot.get('associatedLeadId') in (None, lead['id'])
                and order['current_approval_round_id'] == original['id'], 'Association changed')
        profile['submittedAt'] = int(lead['create_time'].replace(tzinfo=ZoneInfo('Asia/Shanghai')).timestamp() * 1000)
        after = copy.deepcopy(original)
        after.update(order_snapshot=json.dumps(snapshot, ensure_ascii=False),
                     version=original['version'] + 1, updater=MARK, update_time=now)
        changes.append(dict(before=original, after=after, lead_id=lead['id']))
    require(len(changes) == COUNT, 'Reviewed scope changed')
    return dict(repair_key=MARK, created_at=now, before=before, changes=changes)


def protected(value, plan):
    result = copy.deepcopy(value)
    ids = {x['before']['id'] for x in plan['changes']}
    result[ROUND] = [r for r in result[ROUND] if r['id'] not in ids]
    return result


def verify(q, plan, table=ROUND):
    actual = capture_rounds(q, plan, table)
    for change in plan['changes']:
        require(normalized(actual[change['after']['id']]) == normalized(change['after']),
                'Full after-image mismatch')
    data = rows(q, f"SELECT id, HEX(order_snapshot->>'$.leadProfile.submittedName') name_hex "
                f'FROM {table} WHERE tenant_id=1 AND id IN ('
                + ','.join(['%s'] * len(actual)) + ')', sorted(actual))
    for row in data:
        name = json.loads(actual[row['id']]['order_snapshot'])['leadProfile'].get('submittedName')
        if name is not None:
            require(row['name_hex'] == name.encode('utf-8').hex().upper(), 'UTF-8 mismatch')
    return dict(rounds=len(actual), full_after_images=True, utf8_hex=True)


def rehearse(c, q, plan):
    q.execute(f'CREATE TEMPORARY TABLE {TEMP} LIKE {ROUND}')
    columns = list(plan['changes'][0]['before'])
    q.executemany(f"INSERT INTO {TEMP} ({','.join('`'+k+'`' for k in columns)}) VALUES ("
                  + ','.join(['%s'] * len(columns)) + ')',
                  [[x['before'][k] for k in columns] for x in plan['changes']])
    c.commit()
    require(execute(q, plan, TEMP) == COUNT, 'Initial count')
    verify(q, plan, TEMP)
    require(execute(q, plan, TEMP) == 0, 'Repeat count')
    require(execute(q, plan, TEMP, restore=True) == COUNT, 'Restore count')
    c.commit()
    points = (1, COUNT // 2, COUNT)
    for index in points:
        try:
            execute(q, plan, TEMP, fail_at=index)
            raise AssertionError('Failure not injected')
        except RuntimeError as error:
            require(str(error) == 'injected_failure', 'Unexpected failure')
        c.rollback()
        actual = capture_rounds(q, plan, TEMP)
        require(all(normalized(actual[x['before']['id']]) == normalized(x['before'])
                    for x in plan['changes']), 'Rollback failed')
        c.rollback()
    partial = 17
    require(execute(q, plan, TEMP, limit=partial) == partial, 'Partial count')
    c.commit()
    require(execute(q, plan, TEMP) == COUNT - partial, 'Recovery count')
    require(execute(q, plan, TEMP, restore=True) == COUNT, 'Recovery restore')
    c.commit()
    q.execute(f'UPDATE {TEMP} SET version=version+1 WHERE id=%s', (plan['changes'][-1]['before']['id'],))
    conflict = capture_rounds(q, plan, TEMP)
    try:
        execute(q, plan, TEMP)
        raise AssertionError('Conflict accepted')
    except RuntimeError as error:
        require(str(error) == 'Round changed after plan; stop', 'Unexpected conflict')
    require(capture_rounds(q, plan, TEMP) == conflict, 'Conflict caused partial write')
    c.rollback()
    require(execute(q, plan, TEMP) == COUNT, 'Reapply count')
    verify(q, plan, TEMP)
    c.commit()
    return dict(initial=COUNT, repeat=0, failures_rolled_back=points,
                partial=partial, recovery=COUNT-partial, conflict_refused=True, restore_reapply=True)


def load_plan(path):
    plan = decode(path.read_text(encoding='utf-8'))
    proof = decode((path.parent / 'rehearsal.json').read_text(encoding='utf-8'))
    require(plan['repair_key'] == MARK and len(plan['changes']) == COUNT
            and proof['plan_sha256'] == digest(plan), 'Invalid/unrehearsed plan')
    leads = {l['id']: l for l in plan['before'][LEAD]}
    for change in plan['changes']:
        before, after = change['before'], change['after']
        require(before['tenant_id'] == 1 and before['deleted'] == b'\0', 'Wrong target')
        require({k for k in before if before[k] != after[k]}
                <= {'order_snapshot', 'version', 'updater', 'update_time'}, 'Unauthorized fields')
        old, new = json.loads(before['order_snapshot']), json.loads(after['order_snapshot'])
        expected = copy.deepcopy(old)
        require(expected['leadProfile'].get('submittedAt') is None, 'Existing time')
        lead = leads[change['lead_id']]
        expected['leadProfile']['submittedAt'] = int(lead['create_time'].replace(
            tzinfo=ZoneInfo('Asia/Shanghai')).timestamp() * 1000)
        require(new == expected, 'Unrelated snapshot changed')
    return plan


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('mode', choices=('prepare', 'apply', 'verify'))
    parser.add_argument('--plan', type=Path)
    parser.add_argument('--evidence-dir', type=Path, required=True)
    args = parser.parse_args()
    os.umask(0o077)
    args.evidence_dir.mkdir(parents=True, exist_ok=False, mode=0o700)
    c = connect('zsjos-mysql-1')
    try:
        with c.cursor() as q:
            environment(q)
            q.execute('SET NAMES utf8mb4')
            q.execute("SET time_zone='+08:00'")
            q.execute('SET SESSION innodb_lock_wait_timeout=3')
            c.rollback()
            if args.mode == 'prepare':
                q.execute('START TRANSACTION WITH CONSISTENT SNAPSHOT, READ ONLY')
                plan = prepare(q)
                c.rollback()
                save(args.evidence_dir / 'plan.json', plan)
                result = rehearse(c, q, plan)
            else:
                require(args.plan is not None, 'Plan required')
                plan = load_plan(args.plan)
                if args.mode == 'apply':
                    actual = state(q, lock=True)
                    require(protected(actual, plan) == protected(plan['before'], plan), 'Protected source changed')
                    save(args.evidence_dir / 'before.json', actual)
                    count = execute(q, plan)
                    result = verify(q, plan)
                    after = state(q)
                    require(protected(after, plan) == protected(actual, plan), 'Protected data changed')
                    save(args.evidence_dir / 'after-precommit.json', after)
                    c.commit()
                    result.update(changed=count, protected_sha256=digest(protected(actual, plan)))
                else:
                    q.execute('START TRANSACTION WITH CONSISTENT SNAPSHOT, READ ONLY')
                    result = verify(q, plan)
                    require(protected(state(q), plan) == protected(plan['before'], plan), 'Protected data changed')
                    result['protected_match'] = True
            result['plan_sha256'] = digest(plan)
            save(args.evidence_dir / ('rehearsal.json' if args.mode == 'prepare' else 'result.json'), result)
            print(encode(result))
    finally:
        c.rollback()
        c.close()


if __name__ == '__main__':
    main()
