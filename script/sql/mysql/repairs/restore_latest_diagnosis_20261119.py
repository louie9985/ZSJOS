#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Restore the latest open diagnosis nodes to current directors in test tenant 1.

See docs/operations/restore-latest-diagnosis-20261119.md. No runtime or DDL changes.
"""
import argparse
import datetime as dt
import json
import os
from pathlib import Path

import retire_diagnosis_tasks_20261116 as original
import transfer_media_team_20261101 as shared

MARKER = 'restore-latest-diagnosis-20261119'
REASON = '按用户要求恢复各学员账号最新已开放周期诊断任务，并交给当前责任编导'
TASK, EVENT = original.TASK, original.EVENT
ACCOUNT, SERVICE, USER = 'zsjos_media_account', 'zsjos_service_relation', 'system_users'
INPUT_COLUMNS = {
    ACCOUNT: 'id,tenant_id,deleted,student_person_id,create_service_relation_id,director_user_id,run_status,detail_values_json',
    SERVICE: 'id,tenant_id,deleted,person_id,status,acceptance_status,media_ready,content_director_user_id',
    USER: 'id,tenant_id,deleted,status',
}
SOURCE = Path('/opt/zsjos-runtime/backups/retire-diagnosis-tasks-20261116/prepared')
require, read = shared.require, shared.rows


def capture(q):
    state = {t: list(read(q, f'SELECT * FROM {t} ORDER BY id')) for t in (TASK, *original.PROTECTED)}
    for table, cols in INPUT_COLUMNS.items():
        state[table] = list(read(q, f'SELECT {cols} FROM {table} WHERE tenant_id=1 ORDER BY id'))
    state['cancellation_events'] = list(read(q, f'SELECT * FROM {EVENT} WHERE tenant_id=1 AND idempotency_key LIKE %s ORDER BY id', (original.MARKER + ':%',)))
    state[EVENT] = list(read(q, f'SELECT * FROM {EVENT} WHERE tenant_id=1 AND idempotency_key LIKE %s ORDER BY id', (MARKER + ':%',)))
    return state


def context(account):
    values = json.loads(account['detail_values_json'] or '{}')
    value = values.get('_diagnosisContext') or '{}'
    return json.loads(value) if isinstance(value, str) else value


def latest_nodes(before, as_of):
    accounts = {r['id']: r for r in before[ACCOUNT]}
    groups = {}
    for task in before[TASK]:
        if task['tenant_id'] != 1 or not shared.active(task) or task['biz_type'] != 'media_account_diagnosis':
            continue
        account = accounts.get(task['biz_id'])
        if not account or not shared.active(account) or account['run_status'] != 'active' or not account['director_user_id']:
            continue
        ctx = context(account)
        payload = json.loads(task['payload'] or '{}')
        if not ctx.get('anchorAt') or payload.get('roundKey') is None or str(ctx.get('roundKey')) != str(payload['roundKey']):
            continue
        due = task['due_at']
        if not due or due - dt.timedelta(days=7) > as_of:
            continue
        anchor = dt.datetime.fromisoformat(ctx['anchorAt']).date()
        days = (due.date() - anchor).days
        require(days > 0 and days % 7 == 0, 'Invalid proven weekly deadline')
        intervals = (7, 14, 28) if days // 7 % 4 == 0 else (7, 14) if days // 7 % 2 == 0 else (7,)
        if payload.get('templateType') not in {f'diagnosis_{n}d' for n in intervals}:
            continue
        group = groups.setdefault(account['id'], [])
        group.append(task)
    return {aid: [t for t in rows if t['due_at'] == max(x['due_at'] for x in rows)] for aid, rows in groups.items()}


def select_plan(before, as_of):
    old_plan = shared.decoded((SOURCE / 'plan.json').read_text(encoding='utf-8'))
    old_map = {r['id']: r for r in old_plan}
    receipts = {e['aggregate_id']: e for e in before['cancellation_events']}
    accounts = {r['id']: r for r in before[ACCOUNT]}
    services = {r['id']: r for r in before[SERVICE]}
    users = {r['id']: r for r in before[USER]}
    changes = []
    for aid, nodes in latest_nodes(before, as_of).items():
        account = accounts[aid]
        for task in nodes:
            if task['id'] not in old_map or task['status'] != 'cancelled':
                continue
            receipt = receipts.get(task['id'])
            require(receipt and receipt['reason'] == original.REASON and receipt['operator_user_id'] is None
                    and receipt['idempotency_key'] == f'{original.MARKER}:{task["id"]}', 'Missing original cancellation receipt')
            require(task == original.expected(old_map[task['id']], receipt['occurred_at']), 'Cancelled task changed since original cleanup')
            director = account['director_user_id']
            user = users.get(director)
            require(user and shared.active(user) and user['status'] == 0, 'Current director unavailable')
            relation = services.get(account['create_service_relation_id'])
            require(relation and shared.active(relation) and relation['person_id'] == account['student_person_id']
                    and relation['status'] == 'active' and relation['content_director_user_id'] == director
                    and (relation['media_ready'] in (True, b'\x01') if relation['media_ready'] is not None
                         else relation['acceptance_status'] == 'accepted'), 'Account/service director or readiness conflict')
            require(task['assignee_type'] == 'user' and task['task_type'] in original.TYPES, 'Unexpected task contract')
            changes.append(dict(before=task, director=director, account=account, service=relation, user=user,
                                originalCancelAt=receipt['occurred_at']))
    require(len(changes) == 170 and len({c['account']['id'] for c in changes}) == 168
            and len({c['account']['student_person_id'] for c in changes}) == 85, 'Reviewed scope changed')
    return dict(asOf=as_of, changes=sorted(changes, key=lambda c: c['before']['id']))


def expected(change, when):
    old = change['before']
    return dict(old, status='pending', assignee_id=change['director'], cancelled_at=None, cancel_reason=None,
                version=old['version'] + 1, updater=MARKER, update_time=when)


def lock_inputs(q, plan):
    # Match business commands' account-first lock order, then hold responsibility inputs through commit.
    for table, key in ((ACCOUNT, 'account'), (SERVICE, 'service'), (USER, 'user')):
        values = {c[key]['id']: c[key] for c in plan['changes']}
        for rid, wanted in sorted(values.items()):
            current = read(q, f'SELECT {INPUT_COLUMNS[table]} FROM {table} WHERE tenant_id=1 AND id=%s FOR UPDATE', (rid,))
            require(len(current) == 1 and current[0] == wanted, 'Responsibility or round changed; replan required')


def apply(q, plan, fail_after=None, limit=None):
    lock_inputs(q, plan)
    count = 0
    for change in plan['changes']:
        old = change['before']
        key = f'{MARKER}:{old["id"]}'
        current = read(q, f'SELECT * FROM {TASK} WHERE tenant_id=1 AND id=%s FOR UPDATE', (old['id'],))
        require(len(current) == 1, 'Missing original task')
        receipt = read(q, f'SELECT * FROM {EVENT} WHERE tenant_id=1 AND idempotency_key=%s', (key,))
        if receipt:
            require(current[0] == expected(change, receipt[0]['occurred_at']), 'Restored task has subsequent edits')
            require(receipt[0]['aggregate_id'] == old['id'] and receipt[0]['reason'] == REASON
                    and receipt[0]['operator_user_id'] is None and receipt[0]['from_status'] == 'cancelled'
                    and receipt[0]['to_status'] == 'pending', 'Invalid restore receipt')
            continue
        require(current[0] == old, 'Stale task preimage; refusing overwrite')
        when = read(q, 'SELECT NOW() now')[0]['now']
        q.execute(f'''UPDATE {TASK} SET status='pending',assignee_id=%s,cancelled_at=NULL,cancel_reason=NULL,
            version=version+1,updater=%s,update_time=%s
            WHERE tenant_id=1 AND id=%s AND deleted=0 AND status='cancelled' AND version=%s''',
                  (change['director'], MARKER, when, old['id'], old['version']))
        require(q.rowcount == 1, 'Concurrent task edit')
        refs = dict(oldAssigneeId=old['assignee_id'], newAssigneeId=change['director'],
                    accountId=old['biz_id'], serviceRelationId=change['service']['id'],
                    sourceCancellationKey=f'{original.MARKER}:{old["id"]}')
        q.execute(f'''INSERT INTO {EVENT}
            (event_type,aggregate_type,aggregate_id,operator_user_id,from_status,to_status,
             reason,related_object_refs,occurred_at,idempotency_key,creator,updater,tenant_id)
            VALUES ('state_changed','business_task',%s,NULL,'cancelled','pending',%s,%s,%s,%s,%s,%s,1)''',
                  (old['id'], REASON, json.dumps(refs), when, key, MARKER, MARKER))
        count += 1
        if count == fail_after:
            raise RuntimeError('injected_failure')
        if count == limit:
            break
    return count


def verify(before, after, plan):
    receipts = {e['aggregate_id']: e for e in after[EVENT]}
    changes = {c['before']['id']: c for c in plan['changes']}
    require(len(receipts) == len(after[EVENT]) == len(changes), 'Incomplete restore audit')
    require(len(before[TASK]) == len(after[TASK]), 'Tasks inserted or deleted')
    for old, new in zip(before[TASK], after[TASK]):
        change = changes.get(old['id'])
        wanted = expected(change, receipts[old['id']]['occurred_at']) if change else old
        require(new == wanted, 'Unexpected task/field edit')
    for table in (*original.PROTECTED, ACCOUNT, SERVICE, USER, 'cancellation_events'):
        require(before[table] == after[table], 'Protected input/history changed: ' + table)
    for rid, event in receipts.items():
        c = changes[rid]
        require(event['event_type'] == 'state_changed' and event['aggregate_type'] == 'business_task'
                and event['operator_user_id'] is None and event['reason'] == REASON
                and event['from_status'] == 'cancelled' and event['to_status'] == 'pending'
                and event['idempotency_key'] == f'{MARKER}:{rid}', 'Invalid SYSTEM restore event')
        refs = json.loads(event['related_object_refs'])
        require(refs == dict(oldAssigneeId=c['before']['assignee_id'], newAssigneeId=c['director'],
                            accountId=c['account']['id'], serviceRelationId=c['service']['id'],
                            sourceCancellationKey=f'{original.MARKER}:{rid}'), 'Invalid responsibility audit')


def rehearse(conn, q, before, plan):
    for table in (TASK, EVENT, *INPUT_COLUMNS):
        if table in INPUT_COLUMNS:
            q.execute(f'CREATE TEMPORARY TABLE {table} ENGINE=InnoDB AS SELECT {INPUT_COLUMNS[table]} FROM {table} WHERE 1=0')
        else:
            ddl = read(q, f'SHOW CREATE TABLE {table}')[0]['Create Table']
            q.execute(ddl.replace('CREATE TABLE', 'CREATE TEMPORARY TABLE', 1))
        rows = before['cancellation_events'] + before[EVENT] if table == EVENT else before[table]
        if rows:
            cols = list(rows[0])
            q.executemany(f'INSERT INTO {table} ({",".join(cols)}) VALUES ({",".join(["%s"] * len(cols))})',
                          [[r[k] for k in cols] for r in rows])
    conn.commit()
    require(capture(q) == before, 'MySQL fixtures differ')
    for fail_at in (1, 85, 170):
        try:
            apply(q, plan, fail_after=fail_at)
            raise AssertionError('Failure injection missed')
        except RuntimeError as exc:
            require(str(exc) == 'injected_failure', 'Unexpected failure injection result')
        conn.rollback()
        require(capture(q) == before, 'Failure did not roll back task and audit')
    require(apply(q, plan) == 170, 'Initial restore failed')
    after = capture(q)
    verify(before, after, plan)
    require(apply(q, plan) == 0 and capture(q) == after, 'Replay mutated restored rows')
    conn.rollback()
    q.execute(f'UPDATE {ACCOUNT} SET director_user_id=director_user_id+1 WHERE id=%s', (plan['changes'][0]['account']['id'],))
    try:
        apply(q, plan)
        raise AssertionError('Changed responsibility accepted')
    except RuntimeError as exc:
        require(str(exc).startswith('Responsibility'), 'Unexpected ownership check')
    conn.rollback()
    q.execute(f'UPDATE {TASK} SET version=version+1 WHERE id=%s', (plan['changes'][0]['before']['id'],))
    try:
        apply(q, plan)
        raise AssertionError('Stale preimage accepted')
    except RuntimeError as exc:
        require(str(exc).startswith('Stale task'), 'Unexpected stale check')
    conn.rollback()
    require(apply(q, plan, limit=3) == 3, 'Partial restore failed')
    conn.commit()
    require(apply(q, plan) == 167, 'Partial restore recovery failed')
    verify(before, capture(q), plan)
    conn.commit()
    q.execute(f'UPDATE {TASK} SET version=version+1 WHERE id=%s', (plan['changes'][0]['before']['id'],))
    try:
        apply(q, plan)
        raise AssertionError('Subsequent edit accepted')
    except RuntimeError as exc:
        require('subsequent edits' in str(exc), 'Unexpected post-restore conflict')
    conn.rollback()
    # Compensation is rehearsal-only; preserve restore events and append reversing events.
    for change in reversed(plan['changes']):
        old = change['before']
        q.execute(f'''UPDATE {TASK} SET status='cancelled',assignee_id=%s,cancelled_at=%s,cancel_reason=%s,
            version=version+1,updater=%s,update_time=NOW() WHERE id=%s AND tenant_id=1''',
                  (old['assignee_id'], old['cancelled_at'], old['cancel_reason'], MARKER + '-rollback', old['id']))
        require(q.rowcount == 1, 'Missing compensation row')
        q.execute(f'''INSERT INTO {EVENT}
            (event_type,aggregate_type,aggregate_id,operator_user_id,from_status,to_status,
             reason,occurred_at,idempotency_key,tenant_id)
            VALUES ('state_changed','business_task',%s,NULL,'pending','cancelled',%s,NOW(),%s,1)''',
                  (old['id'], '受控回退最新诊断任务恢复，保留恢复审计', f'{MARKER}:rollback:{old["id"]}'))
    restored = {r['id']: r for r in capture(q)[TASK]}
    for change in plan['changes']:
        old = change['before']
        row = restored[old['id']]
        require(row == dict(old, version=old['version'] + 2, updater=MARKER + '-rollback', update_time=row['update_time']), 'Compensation differs')
    conn.rollback()
    for table in reversed((TASK, EVENT, *INPUT_COLUMNS)):
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
    envfile = Path('/etc/zsjos/agent-environment')
    env = os.environ.get('ZSJOS_AGENT_ENV', envfile.read_text().strip() if envfile.exists() else 'local')
    require(env == 'test', 'Only the authorized test environment is supported')
    require(not (args.apply and args.verify) and bool(args.plan_dir) == (args.apply or args.verify), 'Apply/verify require original prepared plan')
    os.umask(0o077)
    args.evidence_dir.mkdir(mode=0o700, parents=True, exist_ok=False)
    conn = shared.connect('zsjos-mysql-1')
    try:
        with session(conn) as q:
            if args.plan_dir:
                before = shared.decoded((args.plan_dir / 'before.json').read_text(encoding='utf-8'))
                plan = shared.decoded((args.plan_dir / 'plan.json').read_text(encoding='utf-8'))
                require(select_plan(before, plan['asOf']) == plan, 'Frozen plan scope differs')
            else:
                before = capture(q)
                as_of = read(q, 'SELECT NOW() now')[0]['now']
                plan = select_plan(before, as_of)
                require(not before[EVENT], 'Use the original plan for replay')
                shared.persist(args.evidence_dir / 'before.json', before)
                shared.persist(args.evidence_dir / 'plan.json', plan)
                conn.rollback()
            if not args.verify:
                rehearse(conn, q, before, plan)
                shared.persist(args.evidence_dir / 'rehearsal.json', dict(initial=170, replay=0, rollback=[1,85,170],
                    partial=3, recovery=167, staleRejected=True, ownerChangeRejected=True, compensation=170))
                print('MySQL rehearsal passed: initial170/replay0/rollback/partial recovery/stale and ownership conflicts/compensation', flush=True)
            if not args.apply and not args.verify:
                return
            if args.apply:
                # The shared server keeps generating unrelated work. Rebase protection evidence,
                # but never the reviewed target rows, ownership or weekly-node membership.
                before = capture(q)
                require(not before[EVENT] and select_plan(before, plan['asOf']) == plan,
                        'Reviewed targets, ownership or latest-node membership changed')
                shared.persist(args.evidence_dir / 'before.json', before)
                shared.persist(args.evidence_dir / 'plan.json', plan)
                lock_inputs(q, plan)
                # A later read still checks membership at the frozen time; no future-node expansion.
                require(select_plan(capture(q), plan['asOf']) == plan, 'Latest open node selection changed')
                count = apply(q, plan)
                after = capture(q)
                verify(before, after, plan)
                shared.persist(args.evidence_dir / 'after.json', after)
                conn.commit()
                shared.persist(args.evidence_dir / 'commit.json', dict(committed=True, restored=count))
    finally:
        conn.rollback()
        conn.close()
    independent = shared.connect('zsjos-mysql-1')
    try:
        with session(independent) as q:
            after = capture(q)
            verify(before, after, plan)
            for row in read(q, f'SELECT reason,HEX(reason) h FROM {EVENT} WHERE tenant_id=1 AND idempotency_key LIKE %s', (MARKER + ':%',)):
                require(row['h'] == REASON.encode('utf-8').hex().upper(), 'Restore reason UTF-8 HEX mismatch')
            require(apply(q, plan) == 0, 'Committed replay mutated data')
            independent.rollback()
            result = dict(restored=170, accounts=168, students=85, assignedToCurrentDirector=True,
                          sourceCancellationPreserved=986, historyUnchanged=True, independentComparison=True,
                          replay=0, utf8Hex=True)
            shared.persist(args.evidence_dir / 'independent-after.json', after)
            shared.persist(args.evidence_dir / 'result.json', result)
            print(shared.encoded(result))
    finally:
        independent.rollback()
        independent.close()


if __name__ == '__main__':
    main()
