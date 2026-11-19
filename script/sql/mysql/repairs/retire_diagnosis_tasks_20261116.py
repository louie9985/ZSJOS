#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Cancel the reviewed 986 test-tenant diagnosis tasks without deleting history.

See docs/operations/retire-diagnosis-tasks-20261116.md for prerequisites,
execution order, repeatability and recovery. No schema or version-ledger writes.
"""
import argparse
import os
from pathlib import Path

import transfer_media_team_20261101 as shared

MARKER = 'retire-diagnosis-tasks-20261116'
REASON = '按用户确认停用账号周期数据诊断，取消未完成任务并保留历史诊断记录'
TASK = 'zsjos_business_task'
EVENT = 'zsjos_business_event'
PROTECTED = ('zsjos_media_account_profile_entry', 'zsjos_account_diagnosis_metric',
             'zsjos_account_diagnosis_member', 'zsjos_business_task_notify_stage')
TYPES = {'media_account_diagnosis_7d', 'media_account_diagnosis_14d', 'media_account_diagnosis_28d'}
require = shared.require
read = shared.rows


def capture(q):
    state = {t: list(read(q, f'SELECT * FROM {t} ORDER BY id')) for t in (TASK, *PROTECTED)}
    state[EVENT] = list(read(q, f'SELECT * FROM {EVENT} WHERE tenant_id=1 AND idempotency_key LIKE %s ORDER BY id', (MARKER + ':%',)))
    return state


def pending(row):
    return row['tenant_id'] == 1 and shared.active(row) and row['biz_type'] == 'media_account_diagnosis' and row['status'] == 'pending'


def build_plan(before):
    targets = [r for r in before[TASK] if pending(r)]
    require(len(targets) == 986 and len({r['biz_id'] for r in targets}) == 188, 'Reviewed task population changed')
    require(all(r['task_type'] in TYPES and r['version'] is not None for r in targets), 'Unknown task type or version')
    require(not before[EVENT], 'Use the original plan for replay, not a new registration')
    return targets


def expected(before, when):
    return dict(before, status='cancelled', cancelled_at=when, cancel_reason=REASON,
                version=before['version'] + 1, updater=MARKER, update_time=when)


def apply(q, plan, fail_after=None, limit=None):
    count = 0
    for old in plan:
        require(pending(old) and old['task_type'] in TYPES, 'Mutation outside diagnosis whitelist')
        key = f'{MARKER}:{old["id"]}'
        current = read(q, f'SELECT * FROM {TASK} WHERE tenant_id=1 AND id=%s FOR UPDATE', (old['id'],))
        require(len(current) == 1, 'Missing task')
        receipt = read(q, f'SELECT * FROM {EVENT} WHERE tenant_id=1 AND idempotency_key=%s', (key,))
        if receipt:
            require(current[0] == expected(old, receipt[0]['occurred_at']), 'Previously cancelled task has subsequent edits')
            require(receipt[0]['aggregate_id'] == old['id'] and receipt[0]['reason'] == REASON
                    and receipt[0]['operator_user_id'] is None and receipt[0]['from_status'] == 'pending'
                    and receipt[0]['to_status'] == 'cancelled', 'Invalid cancellation receipt')
            continue
        require(current[0] == old, 'Stale preimage; refusing overwrite')
        when = read(q, 'SELECT NOW() now')[0]['now']
        q.execute(f'''UPDATE {TASK} SET status='cancelled',cancelled_at=%s,cancel_reason=%s,
            version=version+1,updater=%s,update_time=%s
            WHERE tenant_id=1 AND id=%s AND deleted=0 AND status='pending' AND version=%s''',
                  (when, REASON, MARKER, when, old['id'], old['version']))
        require(q.rowcount == 1, 'Concurrent task update')
        q.execute(f'''INSERT INTO {EVENT}
            (event_type,aggregate_type,aggregate_id,operator_user_id,from_status,to_status,
             reason,occurred_at,idempotency_key,creator,updater,tenant_id)
            VALUES ('state_changed','business_task',%s,NULL,'pending','cancelled',%s,%s,%s,%s,%s,1)''',
                  (old['id'], REASON, when, key, MARKER, MARKER))
        count += 1
        if count == fail_after:
            raise RuntimeError('injected_failure')
        if count == limit:
            break
    return count


def verify(before, after, plan):
    receipts = {e['aggregate_id']: e for e in after[EVENT]}
    require(len(receipts) == len(after[EVENT]) == len(plan), 'Cancellation audit count mismatch')
    changes = {r['id']: r for r in plan}
    require(len(before[TASK]) == len(after[TASK]), 'Task rows inserted or deleted')
    for old, new in zip(before[TASK], after[TASK]):
        wanted = expected(old, receipts[old['id']]['occurred_at']) if old['id'] in changes else old
        require(new == wanted, 'Unexpected task or field change')
    require(not any(pending(r) for r in after[TASK]), 'Pending diagnosis tasks remain')
    for table in PROTECTED:
        require(before[table] == after[table], 'Historical data or notification stages changed: ' + table)
    require(all(e['event_type'] == 'state_changed' and e['aggregate_type'] == 'business_task'
                and e['from_status'] == 'pending' and e['to_status'] == 'cancelled'
                and e['reason'] == REASON and e['operator_user_id'] is None
                and e['idempotency_key'] == f'{MARKER}:{e["aggregate_id"]}' for e in after[EVENT]), 'Invalid SYSTEM audit')


def compensate(q, plan):
    require(apply(q, plan) == 0, 'Compensation requires unchanged cancelled tasks')
    for old in reversed(plan):
        q.execute(f'''UPDATE {TASK} SET status=%s,cancelled_at=%s,cancel_reason=%s,
            version=version+1,updater=%s,update_time=NOW() WHERE tenant_id=1 AND id=%s''',
                  (old['status'], old['cancelled_at'], old['cancel_reason'], MARKER + '-rollback', old['id']))
        require(q.rowcount == 1, 'Missing compensation task')
        q.execute(f'''INSERT INTO {EVENT}
            (event_type,aggregate_type,aggregate_id,operator_user_id,from_status,to_status,
             reason,occurred_at,idempotency_key,creator,updater,tenant_id)
            VALUES ('state_changed','business_task',%s,NULL,'cancelled','pending',%s,NOW(),%s,%s,%s,1)''',
                  (old['id'], '受控回退诊断任务取消，保留取消审计', f'{MARKER}:rollback:{old["id"]}', MARKER, MARKER))


def rehearse(conn, q, before, plan):
    # Connection-local copies shadow only the two writable tables; live rows stay untouched.
    for table in (TASK, EVENT):
        ddl = read(q, f'SHOW CREATE TABLE {table}')[0]['Create Table']
        q.execute(ddl.replace('CREATE TABLE', 'CREATE TEMPORARY TABLE', 1))
        rows = before[table]
        if rows:
            cols = list(rows[0])
            q.executemany(f'INSERT INTO {table} ({",".join(cols)}) VALUES ({",".join(["%s"] * len(cols))})',
                          [[r[c] for c in cols] for r in rows])
    conn.commit()
    require(capture(q) == before, 'Temporary fixtures differ')
    for fail_at in (1, len(plan) // 2, len(plan)):
        try:
            apply(q, plan, fail_after=fail_at)
            raise AssertionError('Expected injected failure')
        except RuntimeError as exc:
            require(str(exc) == 'injected_failure', 'Unexpected rehearsal failure')
        conn.rollback()
        require(capture(q) == before, 'Failed cancellation did not roll back')
    require(apply(q, plan) == len(plan), 'Initial cancellation failed')
    after = capture(q)
    verify(before, after, plan)
    require(apply(q, plan) == 0 and capture(q) == after, 'Repeat changed cancelled tasks')
    conn.rollback()
    # Also reject a stale not-yet-cancelled task, before its row or audit is overwritten.
    q.execute(f'UPDATE {TASK} SET version=version+1 WHERE id=%s', (plan[0]['id'],))
    try:
        apply(q, plan)
        raise AssertionError('Stale task accepted')
    except RuntimeError as exc:
        require(str(exc).startswith('Stale preimage'), 'Unexpected stale failure')
    conn.rollback()
    require(apply(q, plan, limit=3) == 3, 'Partial cancellation failed')
    conn.commit()
    require(apply(q, plan) == len(plan) - 3, 'Partial recovery failed')
    verify(before, capture(q), plan)
    conn.commit()
    q.execute(f'UPDATE {TASK} SET version=version+1 WHERE id=%s', (plan[0]['id'],))
    try:
        apply(q, plan)
        raise AssertionError('Subsequent task edit accepted')
    except RuntimeError as exc:
        require('subsequent edits' in str(exc), 'Unexpected post-cancellation conflict')
    conn.rollback()
    compensate(q, plan)
    restored = capture(q)
    for new in restored[TASK]:
        old = next((r for r in plan if r['id'] == new['id']), None)
        if old:
            require(new == dict(old, version=old['version'] + 2, updater=MARKER + '-rollback', update_time=new['update_time']), 'Compensation mismatch')
    require(len(restored[EVENT]) == 2 * len(plan), 'Compensation audit missing')
    conn.rollback()
    for table in (EVENT, TASK):
        q.execute(f'DROP TEMPORARY TABLE {table}')
    conn.commit()


def session(conn):
    q = conn.cursor()
    q.execute('SET NAMES utf8mb4')
    q.execute("SET time_zone='+08:00'")
    q.execute('SET SESSION innodb_lock_wait_timeout=10')
    return q


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--evidence-dir', type=Path, required=True)
    parser.add_argument('--plan-dir', type=Path)
    parser.add_argument('--apply', action='store_true')
    parser.add_argument('--verify', action='store_true')
    args = parser.parse_args()
    marker = Path('/etc/zsjos/agent-environment')
    env = os.environ.get('ZSJOS_AGENT_ENV', marker.read_text().strip() if marker.exists() else 'local')
    require(env == 'test', 'Only the authorized test environment is supported')
    require(not (args.apply and args.verify) and bool(args.plan_dir) == (args.apply or args.verify), 'Apply/verify require the original prepared plan')
    os.umask(0o077)
    args.evidence_dir.mkdir(mode=0o700, parents=True, exist_ok=False)
    conn = shared.connect('zsjos-mysql-1')
    try:
        with session(conn) as q:
            if args.plan_dir:
                before = shared.decoded((args.plan_dir / 'before.json').read_text(encoding='utf-8'))
                plan = shared.decoded((args.plan_dir / 'plan.json').read_text(encoding='utf-8'))
                require(plan == build_plan(before), 'Plan differs from reviewed original population')
            else:
                before = capture(q)
                plan = build_plan(before)
                shared.persist(args.evidence_dir / 'before.json', before)
                shared.persist(args.evidence_dir / 'plan.json', plan)
                conn.rollback()
            if not args.verify:
                rehearse(conn, q, before, plan)
                shared.persist(args.evidence_dir / 'rehearsal.json', dict(initial=986, replay=0, rollback=[1,493,986], partial=3, recovery=983, staleRejected=True, compensation=986))
                print('MySQL rehearsal passed: initial/repeat/failure rollback/partial recovery/stale rejection/compensation', flush=True)
            if not args.apply and not args.verify:
                return
            if args.apply:
                # Fixed rows are locked in ascending ID order, revalidated and audited in one transaction.
                require(capture(q) == before, 'Live prerequisite changed since preparation')
                count = apply(q, plan)
                after = capture(q)
                verify(before, after, plan)
                shared.persist(args.evidence_dir / 'after.json', after)
                conn.commit()
                shared.persist(args.evidence_dir / 'commit.json', dict(committed=True, cancelled=count))
    finally:
        conn.rollback()
        conn.close()
    independent = shared.connect('zsjos-mysql-1')
    try:
        with session(independent) as q:
            after = capture(q)
            verify(before, after, plan)
            for r in read(q, f'SELECT cancel_reason,HEX(cancel_reason) h FROM {TASK} WHERE tenant_id=1 AND updater=%s', (MARKER,)):
                require(r['h'] == REASON.encode('utf-8').hex().upper(), 'Cancellation Chinese HEX mismatch')
            for r in read(q, f'SELECT reason,HEX(reason) h FROM {EVENT} WHERE tenant_id=1 AND idempotency_key LIKE %s', (MARKER + ':%',)):
                require(r['h'] == REASON.encode('utf-8').hex().upper(), 'Audit Chinese HEX mismatch')
            require(apply(q, plan) == 0, 'Committed replay wrote data')
            independent.rollback()
            shared.persist(args.evidence_dir / 'independent-after.json', after)
            result = dict(cancelled=986, pending=0, completedPreserved=1167, existingCancelledPreserved=333,
                          systemEvents=986, historyUnchanged=True, replay=0, utf8Hex=True, independentComparison=True)
            shared.persist(args.evidence_dir / 'result.json', result)
            print(shared.encoded(result))
    finally:
        independent.rollback()
        independent.close()


if __name__ == '__main__':
    main()
