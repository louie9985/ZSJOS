#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""One-time authorized November attribution repair; see dated operation record.

prepare only reads persistent tables and rehearses on a connection-local table.
apply requires the frozen, rehearsed plan; verify performs independent readback.
"""
import argparse
import copy
import datetime as dt
import os
from pathlib import Path

from transfer_student_20261015 import connect, require
from backfill_order_self_source_20261103 import rows, save, digest, decode
from link_approved_repurchase_leads_20261105 import environment

MARK = 'partner-month-attribution-20261106'
TABLE = 'zsjos_lead'
TEMP = 'verify_partner_month_lead'
FIELDS = ('partner_owner_user_id_snapshot', 'partner_owner_name_snapshot',
          'contribution_user_id_snapshot', 'contribution_user_name_snapshot',
          'version', 'updater', 'update_time')
PARTNERS = ('SELECT partner_id FROM zsjos_partner_ownership_log '
            'WHERE tenant_id=1 AND id BETWEEN 168 AND 190 AND deleted=0')
START, END = dt.datetime(2026, 11, 1), dt.datetime(2026, 12, 1)


def capture(q, lock=False):
    suffix = ' FOR UPDATE NOWAIT' if lock else ''
    queries = {
        'logs': 'SELECT * FROM zsjos_partner_ownership_log WHERE tenant_id=1 AND id BETWEEN 168 AND 190 ORDER BY id',
        'owners': f'SELECT * FROM zsjos_partner_ownership WHERE tenant_id=1 AND deleted=0 AND partner_id IN ({PARTNERS}) ORDER BY id',
        'user': 'SELECT id,nickname,dept_id,status,deleted FROM system_users WHERE tenant_id=1 AND id=45',
        'dept': 'SELECT id,name,leader_user_id,status,deleted FROM system_dept WHERE tenant_id=1 AND id=1013',
        'supervisor': 'SELECT id,nickname,deleted FROM system_users WHERE tenant_id=1 AND id=12',
        'leads': f'SELECT * FROM {TABLE} WHERE tenant_id=1 AND partner_id IN ({PARTNERS}) ORDER BY id',
    }
    return {k: rows(q, sql + suffix) for k, sql in queries.items()}


def make_plan(q):
    state = capture(q)
    require(len(state['logs']) == len(state['owners']) == 23, 'Assignment scope changed')
    owners = {r['partner_id']: r for r in state['owners']}
    for log in state['logs']:
        require(log['deleted'] == b'\0' and log['action_type'] == 'reassign'
                and log['occurred_at'].date() == dt.date(2026, 11, 6)
                and log['employee_user_id'] == owners[log['partner_id']]['employee_user_id'] == 45,
                'Reassignment no longer matches')
    user, dept, supervisor = (state[k][0] for k in ('user', 'dept', 'supervisor'))
    require(user['status'] == dept['status'] == 0 and user['deleted'] == dept['deleted']
            == supervisor['deleted'] == b'\0' and user['dept_id'] == dept['id']
            and dept['leader_user_id'] == supervisor['id'], 'Target organization unavailable')
    now = rows(q, 'SELECT NOW() t')[0]['t']
    changes = []
    for lead in state['leads']:
        if lead['deleted'] != b'\0' or not START <= lead['create_time'] < END:
            continue
        require(lead['create_time'] == lead['submitted_at'] == lead['counted_at'], 'Month boundary ambiguity')
        require(lead['provider_owner_type'] == lead['source_type'] == 'partner'
                and lead['provider_owner_id'] == lead['partner_id'], 'Provider mismatch')
        owner = owners[lead['partner_id']]
        require(owner['employee_name_snapshot'] == user['nickname'], 'Target name mismatch')
        require(lead['partner_owner_user_id_snapshot'] == lead['contribution_user_id_snapshot']
                and lead['contribution_dept_id_snapshot'] == dept['id']
                and lead['contribution_dept_name_snapshot'] == dept['name']
                and lead['contribution_supervisor_user_id_snapshot'] == supervisor['id']
                and lead['contribution_supervisor_name_snapshot'] == supervisor['nickname'],
                'Unexpected attribution scope')
        after = copy.deepcopy(lead)
        after.update(partner_owner_user_id_snapshot=user['id'],
                     partner_owner_name_snapshot=owner['employee_name_snapshot'],
                     contribution_user_id_snapshot=user['id'],
                     contribution_user_name_snapshot=owner['employee_name_snapshot'],
                     version=lead['version'] + 1, updater=MARK, update_time=now)
        changes.append({'before': lead, 'after': after})
    require(len(changes) == 8 and len({x['before']['partner_id'] for x in changes}) == 3,
            'Reviewed Lead scope changed')
    return {'mark': MARK, 'state': state, 'changes': changes}


def current(q, plan, table):
    require(table in (TABLE, TEMP), 'Invalid table')
    return rows(q, f'SELECT * FROM {table} WHERE tenant_id=1 AND id IN ('
                + ','.join(['%s'] * len(plan['changes'])) + ') ORDER BY id FOR UPDATE NOWAIT',
                [x['before']['id'] for x in plan['changes']])


def execute(q, plan, table=TABLE, restore=False, limit=None, fail_at=None):
    actual = {r['id']: r for r in current(q, plan, table)}
    pending = []
    for change in plan['changes']:
        before, after = change['before'], change['after']
        require({k for k in before if before[k] != after[k]} <= set(FIELDS), 'Unexpected field')
        require(actual.get(before['id']) in (before, after), 'Lead changed after plan; stop')
        target = before if restore else after
        if actual[before['id']] != target:
            pending.append((actual[before['id']], target))
    for index, (before, target) in enumerate(pending[:limit], 1):
        q.execute(f'UPDATE {table} SET ' + ','.join('`'+k+'`=%s' for k in FIELDS)
                  + ' WHERE tenant_id=1 AND id=%s AND version=%s',
                  [target[k] for k in FIELDS] + [target['id'], before['version']])
        require(q.rowcount == 1, 'Concurrent write')
        if index == fail_at:
            raise RuntimeError('injected_failure')
    return len(pending[:limit])


def verify(q, plan, table=TABLE):
    require(current(q, plan, table) == [x['after'] for x in plan['changes']], 'After-image mismatch')
    for change in plan['changes']:
        r = rows(q, f'SELECT HEX(partner_owner_name_snapshot) p, '
                 f'HEX(contribution_user_name_snapshot) c FROM {table} WHERE tenant_id=1 AND id=%s',
                 (change['after']['id'],))[0]
        expected = change['after']['partner_owner_name_snapshot'].encode('utf-8').hex().upper()
        require(r['p'] == r['c'] == expected, 'UTF-8 mismatch')
    return {'verified_rows': 8, 'full_after_images': True, 'utf8_hex': True}


def protected(state, plan):
    result = copy.deepcopy(state)
    ids = {x['before']['id'] for x in plan['changes']}
    result['leads'] = [r for r in state['leads'] if r['id'] not in ids]
    return result


def rehearse(c, q, plan):
    q.execute(f'CREATE TEMPORARY TABLE {TEMP} LIKE {TABLE}')
    # MySQL must recompute generated identity keys when copying the rehearsal fixture.
    columns = rows(q, 'SELECT column_name name FROM information_schema.columns '
                   "WHERE table_schema=DATABASE() AND table_name=%s AND generation_expression='' "
                   'ORDER BY ordinal_position', (TABLE,))
    names = ','.join('`' + r['name'] + '`' for r in columns)
    q.execute(f'INSERT INTO {TEMP} ({names}) SELECT {names} FROM {TABLE} '
              f'WHERE tenant_id=1 AND partner_id IN ({PARTNERS})')
    c.commit()
    require(execute(q, plan, TEMP) == 8, 'Initial count'); verify(q, plan, TEMP)
    require(execute(q, plan, TEMP) == 0, 'Repeat count')
    require(execute(q, plan, TEMP, restore=True) == 8, 'Restore count'); c.commit()
    for index in range(1, 9):
        try:
            execute(q, plan, TEMP, fail_at=index)
            raise AssertionError('Failure not injected')
        except RuntimeError as error:
            require(str(error) == 'injected_failure', 'Unexpected failure')
        c.rollback()
        require(current(q, plan, TEMP) == [x['before'] for x in plan['changes']], 'Rollback mismatch')
        c.rollback()
    require(execute(q, plan, TEMP, limit=3) == 3, 'Partial count'); c.commit()
    require(execute(q, plan, TEMP) == 5, 'Recovery count')
    require(execute(q, plan, TEMP, restore=True) == 8, 'Recovery restore'); c.commit()
    q.execute(f'UPDATE {TEMP} SET version=version+1 WHERE id=%s', (plan['changes'][-1]['before']['id'],))
    conflict = current(q, plan, TEMP)
    try:
        execute(q, plan, TEMP)
        raise AssertionError('Conflict accepted')
    except RuntimeError as error:
        require(str(error) == 'Lead changed after plan; stop', 'Unexpected conflict')
    require(current(q, plan, TEMP) == conflict, 'Conflict caused writes'); c.rollback()
    require(execute(q, plan, TEMP) == 8, 'Reapply'); verify(q, plan, TEMP)
    ids = {x['before']['id'] for x in plan['changes']}
    require([r for r in rows(q, f'SELECT * FROM {TEMP} ORDER BY id') if r['id'] not in ids]
            == protected(plan['state'], plan)['leads'], 'Out-of-month rows changed')
    c.commit()
    return {'initial': 8, 'repeat': 0, 'rollback_cases': 8, 'partial': 3, 'recovery': 5,
            'conflict_refused': True, 'restore_reapply': True, 'protected_leads': 205}


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
            identity = environment(q)
            q.execute('SET NAMES utf8mb4'); q.execute("SET time_zone='+08:00'")
            require(not rows(q, "SELECT trigger_name FROM information_schema.triggers WHERE "
                             "trigger_schema=DATABASE() AND event_object_table='zsjos_lead'"), 'Unexpected trigger')
            c.rollback()
            if args.mode == 'prepare':
                q.execute('START TRANSACTION WITH CONSISTENT SNAPSHOT, READ ONLY')
                plan = make_plan(q); c.rollback()
                save(args.evidence_dir / 'plan.json', plan)
                result = rehearse(c, q, plan)
            else:
                require(args.plan is not None, 'Plan required')
                plan = decode(args.plan.read_text(encoding='utf-8'))
                proof = decode((args.plan.parent / 'rehearsal.json').read_text(encoding='utf-8'))
                require(plan['mark'] == MARK and len(plan['changes']) == 8
                        and proof['plan_sha256'] == digest(plan), 'Invalid/unrehearsed plan')
                actual = capture(q, lock=args.mode == 'apply')
                require(protected(actual, plan) == protected(plan['state'], plan), 'Protected source changed')
                if args.mode == 'apply':
                    save(args.evidence_dir / 'before.json', actual)
                    changed = execute(q, plan)
                    result = verify(q, plan)
                    after = capture(q)
                    require(protected(after, plan) == protected(actual, plan), 'Protected data changed')
                    save(args.evidence_dir / 'after-precommit.json', after)
                    c.commit(); result['changed'] = changed
                else:
                    result = verify(q, plan)
                result['protected_leads'] = len(protected(actual, plan)['leads'])
            result.update(plan_sha256=digest(plan), mysql_version=identity['version'])
            save(args.evidence_dir / ('rehearsal.json' if args.mode == 'prepare' else 'result.json'), result)
            print(result)
    finally:
        c.rollback(); c.close()


if __name__ == '__main__':
    main()
