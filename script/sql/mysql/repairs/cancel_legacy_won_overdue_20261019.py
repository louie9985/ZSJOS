#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Cancel only the explicitly approved 116 imported tasks. No schema/ledger writes.

prepare -> controlled tests -> apply --sha256 -> verify. Private plans contain
full before/after task images and dependency hashes. Restore is a separately
authorized compensating action; it refuses intervening task modifications.
"""
import argparse
import datetime as dt
import decimal
import hashlib
import json
import os
from pathlib import Path

from transfer_student_20261015 import connect, require

TABLE = 'zsjos_business_task'
MARK = 'legacy-won-overdue-20261019'
REASON = '按确认清理：已成交客资历史导入逾期跟进提醒，无后续跟进记录'
LEADS = '''
LD202606230023 LD202606240020 LD202606250041 LD202606260035
LD202606280030 LD202606290013 LD202606300016 LD202606300031
LD202607030012 LD202607060020 LD202607080023 LD202607120001
LD202607120002 LD202607130009 LD202607150048 LD202607170032
LD202607180012 LD202607190022 LD202607200037 LD202607230008
LD202607240039 LD202607270023 LD202607270038 LD202607280032
LD202607280038 LD202607280047 LD202608040014 LD202608060014
LD202608100016 LD202608100017 LD202608120021 LD202608130013
LD202608140009 LD202608180019 LD202608250072 LD202608260025
LD202608290019 LD202609030100 LD202609030102 LD202609040017
LD202609070081 LD202609140077 LD202609170063 LD202609180018
LD202606170007 LD202606240017 LD202606250018 LD202606250019
LD202606260012 LD202606260029 LD202606280025 LD202606290033
LD202606300013 LD202606300020 LD202606300025 LD202607050009
LD202607060023 LD202607070014 LD202607140026 LD202607200036
LD202607210040 LD202607230001 LD202607230016 LD202607230017
LD202607240022 LD202607250022 LD202607250029 LD202607260004
LD202607260025 LD202607270034 LD202607300006 LD202607300012
LD202607310003 LD202608020007 LD202608080008 LD202608120007
LD202608120011 LD202608130017 LD202608140016 LD202608160004
LD202608160024 LD202608180006 LD202608180039 LD202608200047
LD202608210006 LD202608240055 LD202608250009 LD202608270041
LD202608280017 LD202608280037 LD202608290006 LD202608290008
LD202608300016 LD202608300026 LD202608310022 LD202609010015
LD202609020033 LD202609090005 LD202609090036 LD202609100082
LD202609110010 LD202609110048 LD202609110064
LD202606200003 LD202606200007 LD202606220004 LD202606270017
LD202606270020 LD202606300028 LD202607060022 LD202607070029
LD202607160016 LD202608020003 LD202608150010 LD202608200041
LD202609070014
'''.split()
FIELDS = ('status', 'cancelled_at', 'cancel_reason', 'version', 'updater', 'update_time')


def encode(value):
    def custom(x):
        if isinstance(x, (dt.datetime, dt.date)):
            return {'$type': type(x).__name__, '$value': x.isoformat()}
        if isinstance(x, bytes):
            return {'$type': 'bytes', '$value': x.hex()}
        if isinstance(x, decimal.Decimal):
            return {'$type': 'Decimal', '$value': str(x)}
        raise TypeError(type(x).__name__)
    return json.dumps(value, default=custom, ensure_ascii=False, sort_keys=True, indent=2)


def decode(raw):
    def hook(x):
        if set(x) == {'$type', '$value'}:
            if x['$type'] in ('datetime', 'date'):
                return getattr(dt, x['$type']).fromisoformat(x['$value'])
            if x['$type'] == 'bytes':
                return bytes.fromhex(x['$value'])
            if x['$type'] == 'Decimal':
                return decimal.Decimal(x['$value'])
        return x
    return json.loads(raw, object_hook=hook)


def digest(value):
    return hashlib.sha256(encode(value).encode('utf-8')).hexdigest()


def save(path, value):
    fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(fd, 'w', encoding='utf-8') as f:
        f.write(encode(value)); f.flush(); os.fsync(f.fileno())


def select(q, sql, args=()):
    q.execute(sql, args or None)
    return list(q.fetchall())


def placeholders(items):
    return ','.join(['%s'] * len(items))


def capture(q, lock=False):
    suffix = ' FOR UPDATE' if lock else ''
    leads = select(q, 'SELECT * FROM zsjos_lead WHERE tenant_id=1 AND lead_no IN ('
                   + placeholders(LEADS) + ') ORDER BY id' + suffix, LEADS)
    require(len(leads) == len(set(LEADS)) == 116, 'Approved Lead set changed')
    require(all(x['status'] == 'won' and not int.from_bytes(x['deleted'])
                for x in leads), 'Lead is no longer active/won')
    ids = [x['id'] for x in leads]
    result = {'leads': leads}
    for key, table in [('orders', 'zsjos_order'), ('followups', 'zsjos_lead_follow_up_record'),
                       ('opportunity_followups', 'zsjos_opportunity_follow_up_record')]:
        result[key] = select(q, f'SELECT * FROM {table} WHERE tenant_id=1 AND lead_id IN ('
                             + placeholders(ids) + ') ORDER BY id' + suffix, ids)
    result['tasks'] = select(q, "SELECT * FROM zsjos_business_task WHERE tenant_id=1 AND biz_type='lead' AND biz_id IN ("
                            + placeholders(ids) + ') ORDER BY id' + suffix, ids)
    return result


def active(row):
    return row['deleted'] in (b'\x00', 0, False)


def protected(snapshot, target_ids):
    return {k: digest([r for r in rows if k != 'tasks' or r['id'] not in target_ids])
            for k, rows in snapshot.items()}


def build_plan(q):
    now = select(q, 'SELECT NOW() AS now')[0]['now']
    snap = capture(q)
    rows = [r for r in snap['tasks'] if active(r) and r['status'] == 'pending'
            and r['task_type'] == 'lead_follow_up_reminder'
            and r['creator'] == 'legacy-migration'
            and r['create_time'] == dt.datetime(2026, 9, 28, 15, 20, 27)
            and r['due_at'] and r['due_at'] < now]
    require(len(rows) == len({r['biz_id'] for r in rows}) == 116, 'Expected exactly 116 original tasks')
    followups = {r['id']: r for r in snap['followups'] if active(r)}
    for row in rows:
        payload = json.loads(row['payload'])
        require(payload.get('followUpRecordScope') == 'lead', 'Unexpected record scope')
        record = followups.get(payload.get('followUpRecordId'))
        require(record is not None and record['lead_id'] == row['biz_id']
                and record['idempotency_key'].startswith('legacy-followup-')
                and row['idempotency_key'] == f"lead-follow-up-reminder:lead:{record['id']}",
                'Import source mismatch')
        require(not any(active(f) and f['lead_id'] == row['biz_id']
                        and (f['occurred_at'] > record['occurred_at'] or f['create_time'] > row['create_time'])
                        for f in snap['followups'] + snap['opportunity_followups']), 'Subsequent follow-up exists')
        require(sum(active(o) and o['lead_id'] == row['biz_id'] and o['status'] == 'effective'
                    and o['order_type'] == 'first_purchase' for o in snap['orders']) == 1,
                'Effective first order changed')
        require(row['cancelled_at'] is None and row['completed_at'] is None, 'Unexpected terminal timestamp')
    after = [dict(r, status='cancelled', cancelled_at=now, cancel_reason=REASON,
                  version=r['version'] + 1, updater=MARK, update_time=now) for r in rows]
    return {'marker': MARK, 'tenant': 1, 'lead_nos': sorted(LEADS), 'prepared_at': now,
            'before': rows, 'after': after, 'protected': protected(snap, {r['id'] for r in rows})}


def apply_rows(q, plan, table=TABLE, restore=False, fail_after=None):
    require(table in (TABLE, 'verify_won_overdue_tasks'), 'Unexpected write table')
    changed = 0
    for before, after in zip(plan['before'], plan['after'], strict=True):
        source, target = (after, before) if restore else (before, after)
        found = select(q, f'SELECT * FROM {table} WHERE tenant_id=1 AND id=%s FOR UPDATE', (source['id'],))
        require(len(found) == 1, 'Target missing')
        if found[0] == target:
            continue
        require(found[0] == source, 'Task changed after preparation; refuse overwrite')
        q.execute(f'UPDATE {table} SET ' + ','.join(f'{f}=%s' for f in FIELDS)
                  + ' WHERE tenant_id=1 AND id=%s AND status=%s AND version=%s',
                  tuple(target[f] for f in FIELDS) + (source['id'], source['status'], source['version']))
        require(q.rowcount == 1, 'Concurrent task change')
        changed += 1
        if changed == fail_after:
            raise RuntimeError('Injected failure')
    return changed


def verify(q, plan, restored=False):
    snap = capture(q)
    ids = {r['id'] for r in plan['before']}
    actual = [r for r in snap['tasks'] if r['id'] in ids]
    require(actual == plan['before' if restored else 'after'], 'Committed row verification failed')
    require(protected(snap, ids) == plan['protected'], 'Protected dependency changed')
    if not restored:
        count = select(q, 'SELECT COUNT(*) n FROM zsjos_business_task WHERE tenant_id=1 AND id IN ('
                       + placeholders(ids) + ") AND status='pending' AND due_at<NOW()", list(ids))[0]['n']
        require(count == 0, 'Approved tasks remain overdue')
        hex_value = select(q, 'SELECT HEX(cancel_reason) h FROM zsjos_business_task WHERE tenant_id=1 AND id=%s',
                           (plan['before'][0]['id'],))[0]['h']
        require(hex_value == REASON.encode('utf-8').hex().upper(), 'UTF-8 stored reason mismatch')


def other_followups(q, ids):
    rows = select(q, "SELECT * FROM zsjos_business_task WHERE biz_type='lead' "
                  "AND task_type IN ('lead_first_follow_up','lead_follow_up_reminder') AND id NOT IN ("
                  + placeholders(ids) + ') ORDER BY id', list(ids))
    return {'count': len(rows), 'sha256': digest(rows)}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('mode', choices=('prepare', 'apply', 'verify', 'restore'))
    parser.add_argument('--plan', type=Path, required=True)
    parser.add_argument('--sha256')
    args = parser.parse_args()
    env = os.environ.get('ZSJOS_AGENT_ENV')
    if env is None:
        marker = Path('/etc/zsjos/agent-environment')
        env = marker.read_text().strip() if marker.exists() else 'local'
    require(env == 'test', 'Only authorized test environment supported')
    c = connect('zsjos-mysql-1')
    try:
        with c.cursor() as q:
            q.execute('SET NAMES utf8mb4')
            if args.mode == 'prepare':
                q.execute('SET TRANSACTION READ ONLY'); q.execute('START TRANSACTION WITH CONSISTENT SNAPSHOT')
                plan = build_plan(q)
                save(args.plan, plan)
                print(json.dumps({'targets': len(plan['before']), 'plan_sha256': digest(plan)}, ensure_ascii=False))
                c.rollback(); return
            plan = decode(args.plan.read_text(encoding='utf-8'))
            require(plan['marker'] == MARK and plan['tenant'] == 1 and plan['lead_nos'] == sorted(LEADS), 'Wrong plan')
            require(len(plan['before']) == len(plan['after']) == 116, 'Invalid plan count')
            if args.mode == 'verify':
                q.execute('SET TRANSACTION READ ONLY'); q.execute('START TRANSACTION WITH CONSISTENT SNAPSHOT')
                verify(q, plan); print('Verified: 116 cancelled, 0 target overdue, protected data unchanged, UTF-8 HEX correct')
                c.rollback(); return
            require(args.sha256 == digest(plan), 'Explicit reviewed plan hash required')
            require(select(q, 'SELECT GET_LOCK(%s,0) acquired', (MARK,))[0]['acquired'] == 1, 'Repair already running')
            q.execute('SET TRANSACTION ISOLATION LEVEL SERIALIZABLE'); q.execute('START TRANSACTION')
            snap = capture(q, lock=True)
            ids = {r['id'] for r in plan['before']}
            require(protected(snap, ids) == plan['protected'], 'Dependencies changed; re-audit before writing')
            other_before = other_followups(q, ids)
            changed = apply_rows(q, plan, restore=args.mode == 'restore')
            verify(q, plan, restored=args.mode == 'restore')
            require(other_followups(q, ids) == other_before, 'Non-target follow-up tasks changed')
            c.commit()
            q.execute('SET TRANSACTION READ ONLY'); q.execute('START TRANSACTION WITH CONSISTENT SNAPSHOT')
            verify(q, plan, restored=args.mode == 'restore')
            c.rollback()
            print(json.dumps({'mode': args.mode, 'changed': changed, 'verified': True,
                              'unchanged_non_target_followups': other_before}))
    finally:
        c.rollback(); c.close()


if __name__ == '__main__':
    main()
