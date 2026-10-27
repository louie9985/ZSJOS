#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""One-time test tenant repair; see stale-qualification-cleanup-20261027.md.

Prepare before apply. Private evidence contains preimages; never print row payloads.
No schema, migration ledger, notifications or missing historical facts are created.
"""
import argparse
import copy
import json
import os
from pathlib import Path

from transfer_student_20261015 import connect, encoded, decoded, require, rows, active

MARKER = 'stale-qualification-20261027'
REASON = '清理转派后遗留的旧轮次判定任务，保留历史记录'
ROOT = Path('/opt/zsjos-runtime/backups/stale-qualification-cleanup-20261027-test')
LEAD = 'zsjos_lead'
TASK = 'zsjos_business_task'
HISTORY = 'zsjos_lead_assignment_history'
PERF = 'zsjos_performance_attribution'
TABLES = (LEAD, HISTORY, TASK, PERF)
PREDICATE = """
 t.tenant_id=1 AND t.deleted=0 AND l.deleted=0
 AND t.task_type='lead_qualification' AND t.biz_type='lead' AND t.status='pending'
 AND t.assignee_id<>l.owner_user_id
 AND JSON_TYPE(JSON_EXTRACT(t.payload,'$.roundNo'))='INTEGER'
 AND CAST(JSON_UNQUOTE(JSON_EXTRACT(t.payload,'$.roundNo')) AS UNSIGNED)<l.qualification_round_no
 AND t.idempotency_key=CONCAT('lead-qualification:',l.id,':',JSON_UNQUOTE(JSON_EXTRACT(t.payload,'$.roundNo')))
 AND EXISTS(SELECT 1 FROM zsjos_lead_assignment_history h
   WHERE h.tenant_id=t.tenant_id AND h.lead_id=t.biz_id AND h.deleted=0
   AND h.action_type='transfer' AND h.from_owner_user_id=t.assignee_id
   AND h.to_owner_user_id<>h.from_owner_user_id AND h.occurred_at>=t.create_time)
"""


def private_write(name, value):
    with (ROOT / name).open('x', encoding='utf-8') as f:
        f.write(encoded(value))
        f.flush()
        os.fsync(f.fileno())


def ids(values):
    return ','.join(str(int(v)) for v in sorted(set(values))) or '-1'


def candidates(q):
    return rows(q, f'SELECT t.id,t.biz_id FROM {TASK} t JOIN {LEAD} l '
                'ON l.id=t.biz_id AND l.tenant_id=t.tenant_id WHERE ' + PREDICATE + ' ORDER BY t.id')


def capture(q, lead_ids, lock=False):
    conditions = {LEAD: 'id', HISTORY: 'lead_id', TASK: 'biz_id', PERF: 'lead_id'}
    result = {}
    for table in TABLES:
        condition = f'{conditions[table]} IN ({ids(lead_ids)})'
        if table == TASK:
            condition += " AND biz_type='lead'"
        result[table] = rows(q, f'SELECT * FROM {table} WHERE tenant_id=1 AND {condition} ORDER BY id'
                             + (' FOR UPDATE' if lock else ''))
    return result


def expected(plan, before):
    after = copy.deepcopy(before)
    task_ids = set(plan['task_ids'])
    for row in after[TASK]:
        if row['id'] in task_ids:
            require(active(row) and row['status'] == 'pending', 'Target task is not pending')
            row.update(status='cancelled', cancelled_at=plan['at'], cancel_reason=REASON,
                       version=row['version'] + 1, updater=MARKER, update_time=plan['at'])
    for row in after[PERF]:
        if row['id'] in plan['performance_ids']:
            require(active(row) and row['outcome'] == 'pending' and row['completed_at'] is None,
                    'Performance outcome already settled')
            # End the superseded assignment; do not assign today's Lead result to an old owner.
            row.update(outcome='ended', completed_at=plan['at'], updater=MARKER, update_time=plan['at'])
    return after


def mutate(q, plan, fail=False):
    q.execute(f"UPDATE {TASK} SET status='cancelled',cancelled_at=%s,cancel_reason=%s,"
              "version=version+1,updater=%s,update_time=%s WHERE tenant_id=1 AND deleted=0 "
              f"AND status='pending' AND task_type='lead_qualification' AND biz_type='lead' AND id IN ({ids(plan['task_ids'])})",
              (plan['at'], REASON, MARKER, plan['at']))
    require(q.rowcount == len(plan['task_ids']), 'Task update count mismatch')
    if fail:
        raise RuntimeError('injected failure')
    q.execute(f"UPDATE {PERF} SET outcome='ended',completed_at=%s,updater=%s,update_time=%s "
              "WHERE tenant_id=1 AND deleted=0 AND outcome='pending' AND completed_at IS NULL "
              f"AND fact_type='QUALIFICATION' AND id IN ({ids(plan['performance_ids'])})",
              (plan['at'], MARKER, plan['at']))
    require(q.rowcount == len(plan['performance_ids']), 'Performance update count mismatch')


def guarded(q, plan):
    before = capture(q, plan['lead_ids'], lock=True)
    after = expected(plan, plan['before'])
    if before == after:
        return 0
    require(before == plan['before'], 'Scoped data changed since preparation; refusing write')
    require({r['id'] for r in candidates(q)} == set(plan['task_ids']), 'Candidate scope changed')
    mutate(q, plan)
    require(capture(q, plan['lead_ids']) == after, 'Unexpected scoped changes')
    require(not candidates(q), 'Stale tasks remain')
    hex_rows = rows(q, f'SELECT HEX(cancel_reason) value FROM {TASK} WHERE tenant_id=1 AND id IN ({ids(plan["task_ids"])})')
    require(len(hex_rows) == 30 and all(r['value'] == REASON.encode('utf-8').hex().upper() for r in hex_rows),
            'UTF-8 verification failed')
    return len(plan['task_ids'])


def rehearse(c, plan):
    with c.cursor() as q:
        # Session-local InnoDB copies shadow live tables only on this connection.
        for table in TABLES:
            ddl = rows(q, f'SHOW CREATE TABLE {table}')[0]['Create Table']
            q.execute(ddl.replace('CREATE TABLE ', 'CREATE TEMPORARY TABLE ', 1))
            data = plan['before'][table]
            if data:
                generated = {r['Field'] for r in rows(q, f'SHOW COLUMNS FROM {table}')
                             if 'VIRTUAL GENERATED' in r['Extra'] or 'STORED GENERATED' in r['Extra']}
                columns = [k for k in data[0] if k not in generated]
                names = ','.join('`' + x + '`' for x in columns)
                q.executemany(f'INSERT INTO {table} ({names}) VALUES ({",".join(["%s"] * len(columns))})',
                              [tuple(r[k] for k in columns) for r in data])
    c.commit()
    with c.cursor() as q:
        try:
            mutate(q, plan, fail=True)
        except RuntimeError as e:
            require(str(e) == 'injected failure', 'Unexpected failure')
            c.rollback()
        require(capture(q, plan['lead_ids']) == plan['before'], 'Failed repair did not roll back')
        require(guarded(q, plan) == 30, 'Recovery/initial application failed')
        require(guarded(q, plan) == 0, 'Repeat was not a no-op')
        c.rollback()
        q.execute(f'UPDATE {TASK} SET version=version+1 WHERE id=%s', (plan['task_ids'][0],))
        try:
            guarded(q, plan)
            raise AssertionError('Stale plan accepted')
        except RuntimeError as e:
            require('changed since preparation' in str(e), 'Unexpected stale-plan failure')
        c.rollback()
    # Closing removes temporary tables without any live DDL or live row writes.


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('mode', choices=('prepare', 'rehearse', 'apply', 'verify'))
    args = parser.parse_args()
    env = os.environ.get('ZSJOS_AGENT_ENV')
    if env is None:
        marker = Path('/etc/zsjos/agent-environment')
        env = marker.read_text().strip() if marker.exists() else 'local'
    require(env == 'test', 'This repair is authorized only for test')
    os.umask(0o077)
    c = connect('zsjos-mysql-1')
    try:
        with c.cursor() as q:
            q.execute('SET NAMES utf8mb4')
            q.execute("SET SESSION time_zone='+08:00'")
            q.execute('SET SESSION innodb_lock_wait_timeout=10')
        if args.mode == 'prepare':
            ROOT.mkdir(mode=0o700, parents=True, exist_ok=False)
            with c.cursor() as q:
                q.execute('START TRANSACTION WITH CONSISTENT SNAPSHOT, READ ONLY')
                found = candidates(q)
                plan = {'task_ids': [r['id'] for r in found], 'lead_ids': sorted({r['biz_id'] for r in found}),
                        'at': rows(q, 'SELECT NOW() at')[0]['at']}
                require(len(found) == 30 and len(plan['lead_ids']) == 29, 'Reviewed scope changed')
                plan['before'] = capture(q, plan['lead_ids'])
                perf = [r for r in plan['before'][PERF] if active(r) and r['fact_type'] == 'QUALIFICATION'
                        and r['fact_id'] in plan['task_ids']]
                require(len(perf) == 29 and len({r['fact_id'] for r in perf}) == 29, 'Performance scope changed')
                plan['performance_ids'] = [r['id'] for r in perf]
                expected(plan, plan['before'])
            c.rollback()
            private_write('plan.json', plan)
            rehearse(c, plan)
            private_write('rehearsal.json', {'initial': 'passed', 'repeat': 'zero writes',
                          'failure_rollback_recovery': 'passed', 'stale_plan_refused': True,
                          'chinese_hex': 'passed', 'scoped_comparison': 'passed'})
            print('Prepared 30 task cancellations, 29 performance endings; MySQL rehearsal passed.')
        elif args.mode == 'rehearse':
            plan = decoded((ROOT / 'plan.json').read_text(encoding='utf-8'))
            rehearse(c, plan)
            private_write('rehearsal.json', {'initial': 'passed', 'repeat': 'zero writes',
                          'failure_rollback_recovery': 'passed', 'stale_plan_refused': True,
                          'chinese_hex': 'passed', 'scoped_comparison': 'passed'})
            print('MySQL rehearsal passed for the saved plan.')
        else:
            plan = decoded((ROOT / 'plan.json').read_text(encoding='utf-8'))
            require((ROOT / 'rehearsal.json').exists(), 'Rehearsal evidence missing')
            with c.cursor() as q:
                if args.mode == 'apply':
                    q.execute('SET TRANSACTION ISOLATION LEVEL SERIALIZABLE')
                    c.begin()
                    count = guarded(q, plan)
                    if count:
                        private_write('precommit.json', {'changed_tasks': count, 'after': capture(q, plan['lead_ids'])})
                    c.commit()
                    print(f'Committed {count} task cancellations and {len(plan["performance_ids"]) if count else 0} performance endings.')
                else:
                    q.execute('START TRANSACTION READ ONLY')
                    after = capture(q, plan['lead_ids'])
                    require(after == expected(plan, plan['before']), 'Postcommit state mismatch')
                    require(not candidates(q), 'New stale tasks detected')
                    stats = rows(q, f"SELECT status,COUNT(*) n FROM {TASK} WHERE tenant_id=1 AND deleted=0 AND task_type='lead_qualification' GROUP BY status")
                    private_write('verified.json', {'independent_connection': True, 'exact_postimages': True,
                                  'remaining_stale': 0, 'status_counts': stats})
                    print(json.dumps({'remaining_stale': 0, 'status_counts': stats}))
                    c.rollback()
    except Exception:
        c.rollback()
        raise
    finally:
        c.close()


if __name__ == '__main__':
    main()
