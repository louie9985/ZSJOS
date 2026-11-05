#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Reverse one proven placeholder-only merge, preserving subsequent business data.

Fixed test-only scope. Historical full rollback would erase later repairs, so
only the reviewed identity fields and the erroneous order profile are corrected.
"""
import argparse
import copy
import json
import os
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'tools/person_identity_repair'))
from server_target import read as historical_read
from repair import LIVE
from group_repair import capture as capture_group
from transfer_student_20261015 import connect, require
from backfill_order_self_source_20261103 import encode, decode, digest, save, rows
from link_approved_repurchase_leads_20261105 import environment, in_list
from backfill_repurchase_profiles_20261105 import profile

LIVE['zsjos_student_info_task'] = ['person_id']
MARK = 'false-person-merge-20261105'
GROUP = '33f436a1563eadf0a892e95f7558e086f28e7b12aa2ddd9fd69f543e776dd9a3'
ARCHIVE = Path('/opt/zsjos-runtime/backups/person-unification-test-20261030/approved-maintenance-apply')
ROUND = 'zsjos_order_approval_round'
TARGET_ORDER = 'OD202610150855120003'
OLD_LEAD = 'LD202607220004'
RIGHT_LEAD = 'KZ202610130845270001'
WRITE_TABLES = {'zsjos_person', 'zsjos_lead', 'zsjos_order', ROUND,
                'zsjos_person_contact_alias', 'zsjos_person_contact_claim', 'zsjos_person_merge_event'}


def history():
    return (historical_read(ARCHIVE / (GROUP + '-before.json.gz')),
            historical_read(ARCHIVE / (GROUP + '-after.json.gz'))['after'])


def normalize(row):
    result = dict(row)
    if 'order_snapshot' in result:
        result['order_snapshot'] = json.loads(result['order_snapshot'])
    return result


def capture(q, original, lock=False):
    state, meta = capture_group(q, original['memberIds'], original['key'], lock=lock)
    # Capture all tenant orders/rounds: only three exact rows may change below.
    state['zsjos_order'] = rows(q, 'SELECT * FROM zsjos_order WHERE tenant_id=1 ORDER BY id')
    state[ROUND] = rows(q, 'SELECT * FROM zsjos_order_approval_round WHERE tenant_id=1 ORDER BY id')
    user_ids = sorted({l[k] for l in state['zsjos_lead'] for k in ('owner_user_id', 'source_user_id') if l[k]})
    state['system_users'] = rows(q, 'SELECT id,tenant_id,nickname,deleted FROM system_users '
                               'WHERE tenant_id=1 AND id IN (' + in_list(user_ids) + ') ORDER BY id'
                               + (' FOR SHARE NOWAIT' if lock else ''), user_ids)
    # Explicit order/Lead dependents beyond the Person graph remain read-only.
    ids = sorted(o['id'] for o in state['zsjos_order'] if o['person_id'] in original['memberIds'])
    for table in ('zsjos_order_item', 'zsjos_order_command', 'zsjos_cashback', 'zsjos_registration_case',
                  'zsjos_order_payment_allocation', 'zsjos_payment_refund'):
        state[table] = rows(q, f'SELECT * FROM {table} WHERE tenant_id=1 AND order_id IN ('
                           + in_list(ids) + ') ORDER BY id', ids)
    state['zsjos_performance_attribution'] = rows(q,
        "SELECT * FROM zsjos_performance_attribution WHERE tenant_id=1 AND fact_type='ORDER' AND fact_id IN ("
        + in_list(ids) + ') ORDER BY id', ids)
    return state


def prepare(q):
    original, merged = history()
    before = capture(q, original)
    source = original['sourcePersonIds'][0]
    target = original['targetPersonId']
    people = {p['id']: p for p in before['zsjos_person']}
    require(len(original['memberIds']) == 2 and len(people) == 2, 'Group changed')
    require(people[source]['deleted'] == b'\1' and people[target]['deleted'] == b'\0', 'Identity state changed')
    require(all(p['wechat_id'] == '无' for p in people.values())
            and people[source]['name'] != people[target]['name']
            and people[source]['mobile'] != people[target]['mobile'], 'False identity evidence changed')
    # No unreviewed new owning rows; historical original amounts and states retained.
    for table, oldrows in merged.items():
        current = {r['id']: r for r in before[table]}
        if table != 'zsjos_order':
            require(set(current) == {r['id'] for r in oldrows}, 'New group relationship requires review: ' + table)
        for old in oldrows:
            got = current[old['id']]
            allowed = {'lead_id', 'version', 'updater', 'update_time'} if old.get('order_no') == TARGET_ORDER else set()
            require(all(got[k] == v for k, v in old.items() if k not in allowed), 'Post-merge business drift: ' + table)
    known = {r['id'] for r in merged['zsjos_order']}
    require(all(r['id'] in known for r in before['zsjos_order'] if r['person_id'] in original['memberIds']),
            'New order in merged identity requires review')
    leads = {r['lead_no']: r for r in before['zsjos_lead']}
    require(set(leads) == {OLD_LEAD, RIGHT_LEAD}, 'Lead set changed')
    orders = {r['order_no']: r for r in before['zsjos_order']}
    reported = orders[TARGET_ORDER]
    require(reported['lead_id'] == leads[OLD_LEAD]['id'] and reported['status'] == 'effective'
            and reported['person_id'] == target, 'Reported order changed')
    now = rows(q, 'SELECT NOW() t')[0]['t']
    changes = []

    def add(table, row, values):
        after = copy.deepcopy(row)
        values = dict(values, updater=MARK, update_time=now)
        if 'version' in row:
            values['version'] = row['version'] + 1
        after.update(values)
        if table == 'zsjos_lead':
            after['active_person_id'] = after['person_id']
        changes.append(dict(table=table, before=row, after=after, fields=list(values)))

    add('zsjos_person', people[source], dict(deleted=b'\0', wechat_id=None))
    add('zsjos_person', people[target], dict(wechat_id=None))
    add('zsjos_lead', leads[OLD_LEAD], dict(person_id=source))
    original_source_orders = [o for o in original['before']['zsjos_order'] if o['person_id'] == source]
    require([o['order_no'] for o in original_source_orders] == ['OR202607220002'], 'Historical source orders changed')
    add('zsjos_order', orders['OR202607220002'], dict(person_id=source))
    add('zsjos_order', reported, dict(lead_id=leads[RIGHT_LEAD]['id']))
    round_row = next(r for r in before[ROUND] if r['id'] == reported['current_approval_round_id'])
    require(round_row['status'] == 'approved', 'Approval changed')
    snapshot = json.loads(round_row['order_snapshot'])
    require(snapshot['leadProfile']['leadNo'] == OLD_LEAD
            and snapshot['leadProfileBackfill']['repairKey'] == 'repurchase-profile-20261105',
            'Snapshot provenance changed')
    users = {u['id']: u for u in before['system_users']}
    snapshot['leadProfile'] = profile(leads[RIGHT_LEAD], users)
    snapshot['leadProfileBackfill'] = dict(
        repairKey=MARK, capturedAt=now.isoformat(), historicalAtSubmission=False,
        basis='explicit_correction_of_placeholder_only_false_merge', leadNo=RIGHT_LEAD,
        supersedesRepairKey='repurchase-profile-20261105', priorSnapshotSha256=digest(round_row))
    add(ROUND, round_row, dict(order_snapshot=json.dumps(snapshot, ensure_ascii=False)))
    for row in before['zsjos_person_contact_alias']:
        require(row['merge_key'] == original['key'], 'Unrelated alias in group')
        if row['contact_value'] == '无':
            add('zsjos_person_contact_alias', row, dict(deleted=b'\1'))
        elif row['source_person_id'] == source:
            require(row['contact_value'] == people[source]['mobile'], 'Unreviewed alias')
            add('zsjos_person_contact_alias', row, dict(person_id=source))
    for row in before['zsjos_person_contact_claim']:
        require(not row['reservation_key'], 'Reserved contact')
        if row['contact_value'] == '无':
            add('zsjos_person_contact_claim', row, dict(deleted=b'\1'))
        elif row['contact_value'] == people[source]['mobile']:
            add('zsjos_person_contact_claim', row, dict(person_id=source))
    require(len(before['zsjos_person_merge_event']) == 1, 'Mapping scope changed')
    add('zsjos_person_merge_event', before['zsjos_person_merge_event'][0], dict(deleted=b'\1'))
    require(len(changes) == 12, 'Reviewed mutation count changed')
    for table in WRITE_TABLES:
        require(not rows(q, 'SELECT trigger_name FROM information_schema.triggers '
                        'WHERE trigger_schema=DATABASE() AND event_object_table=%s', (table,)), 'Unexpected trigger')
    return dict(key=MARK, original=original, before=before, changes=changes, source=source, target=target)


def read_targets(q, plan, prefix='', lock=False):
    result = {}
    for table in sorted({c['table'] for c in plan['changes']}):
        require(table in WRITE_TABLES and prefix in ('', 'fm_'), 'Invalid write scope')
        ids = sorted(c['before']['id'] for c in plan['changes'] if c['table'] == table)
        for row in rows(q, f'SELECT * FROM `{prefix}{table}` WHERE tenant_id=1 AND id IN ('
                        + in_list(ids) + ') ORDER BY id' + (' FOR UPDATE NOWAIT' if lock else ''), ids):
            result[table, row['id']] = row
    require(len(result) == len(plan['changes']), 'Missing target')
    return result


def execute(q, plan, prefix='', fail_at=None, limit=None, restore=False):
    require(not restore or prefix == 'fm_', 'Live restore not exposed')
    actual = read_targets(q, plan, prefix, True)
    pending = []
    for change in plan['changes']:
        start, end = (change['after'], change['before']) if restore else (change['before'], change['after'])
        got = actual[change['table'], start['id']]
        if normalize(got) == normalize(end):
            continue
        require(normalize(got) == normalize(start), 'Concurrent change; abort whole repair')
        pending.append((change, end))
    pending = pending[:limit] if limit else pending
    for index, (change, end) in enumerate(pending, 1):
        fields = change['fields']
        q.execute(f"UPDATE `{prefix}{change['table']}` SET " + ','.join('`'+f+'`=%s' for f in fields)
                  + ' WHERE tenant_id=1 AND id=%s', [end[f] for f in fields] + [end['id']])
        require(q.rowcount == 1, 'Unexpected affected count')
        if index == fail_at:
            raise RuntimeError('injected_failure')
    actual = read_targets(q, plan, prefix)
    require(all(normalize(actual[c['table'], end['id']]) == normalize(end) for c, end in pending), 'After-image mismatch')
    return len(pending)


def expected_state(plan):
    expected = copy.deepcopy(plan['before'])
    for c in plan['changes']:
        expected[c['table']] = [c['after'] if r['id'] == c['after']['id'] else r for r in expected[c['table']]]
    return expected


def same_state(a, b):
    return {t: [normalize(r) for r in rs] for t, rs in a.items()} == {t: [normalize(r) for r in rs] for t, rs in b.items()}


def verify(q, plan):
    current = capture(q, plan['original'])
    require(same_state(current, expected_state(plan)), 'Protected/full state mismatch')
    persons = {p['id']: p for p in current['zsjos_person']}
    require(all(p['deleted'] == b'\0' and p['wechat_id'] is None for p in persons.values()), 'Persons not separated')
    require(not any(e['deleted'] == b'\0' for e in current['zsjos_person_merge_event']), 'Old redirect remains')
    leads = {l['id']: l for l in current['zsjos_lead']}
    orders = [o for o in current['zsjos_order'] if o['person_id'] in persons]
    require(all(not o['lead_id'] or leads[o['lead_id']]['person_id'] == o['person_id'] for o in orders),
            'Order/Lead identity mismatch')
    for table, column in [('zsjos_person', 'name'), ('zsjos_lead', 'submitted_name')]:
        ids = [r['id'] for r in current[table]]
        for r in rows(q, f'SELECT `{column}` val,HEX(`{column}`) hx FROM `{table}` WHERE tenant_id=1 AND id IN ('
                      + in_list(ids) + ')', ids):
            require(r['hx'] == r['val'].encode('utf-8').hex().upper(), 'UTF-8 HEX mismatch')
    return dict(persons_separated=True, order_link_and_profile_correct=True,
                protected_rows_match=True, utf8_hex=True, total_verified_rows=sum(map(len, current.values())))


def rehearse(c, q, plan):
    for table in sorted({x['table'] for x in plan['changes']}):
        q.execute(f'CREATE TEMPORARY TABLE `fm_{table}` LIKE `{table}`')
        cols = [r['Field'] for r in rows(q, f'SHOW COLUMNS FROM `{table}`')
                if not any(s in r['Extra'] for s in ('VIRTUAL GENERATED', 'STORED GENERATED'))]
        data = [x['before'] for x in plan['changes'] if x['table'] == table]
        q.executemany(f"INSERT INTO `fm_{table}` ({','.join('`'+x+'`' for x in cols)}) VALUES ("
                      + in_list(cols) + ')', [[r[k] for k in cols] for r in data])
    c.commit()
    n = len(plan['changes'])
    require(execute(q, plan, 'fm_') == n and execute(q, plan, 'fm_') == 0, 'Apply/replay failed')
    require(execute(q, plan, 'fm_', restore=True) == n, 'Restore failed')
    c.commit()
    for position in range(1, n+1):
        try:
            execute(q, plan, 'fm_', fail_at=position)
            raise AssertionError('Failure not reached')
        except RuntimeError as e:
            require(str(e) == 'injected_failure', 'Unexpected test failure')
        c.rollback()
        state = read_targets(q, plan, 'fm_')
        require(all(normalize(state[x['table'], x['before']['id']]) == normalize(x['before'])
                    for x in plan['changes']), 'Rollback lost original state')
        c.rollback()
    require(execute(q, plan, 'fm_', limit=4) == 4, 'Partial failed')
    c.commit()
    require(execute(q, plan, 'fm_') == n-4, 'Recovery failed')
    require(execute(q, plan, 'fm_', restore=True) == n, 'Restore after recovery failed')
    c.commit()
    last = plan['changes'][-1]
    q.execute(f"UPDATE `fm_{last['table']}` SET updater='conflict-test' WHERE id=%s", (last['before']['id'],))
    before = read_targets(q, plan, 'fm_')
    try:
        execute(q, plan, 'fm_')
        raise AssertionError('Conflict accepted')
    except RuntimeError as e:
        require(str(e) == 'Concurrent change; abort whole repair', 'Unexpected conflict')
    require(read_targets(q, plan, 'fm_') == before, 'Conflict caused partial writes')
    c.rollback()
    require(execute(q, plan, 'fm_') == n, 'Reapply failed')
    c.commit()
    return dict(initial=n, replay=0, failure_rollback_positions=n, partial_recovery=True,
                conflict_refused=True, restore_reapply=True)


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('mode', choices=('prepare', 'apply', 'verify'))
    p.add_argument('--plan', type=Path)
    p.add_argument('--evidence-dir', type=Path, required=True)
    args = p.parse_args()
    os.umask(0o077)
    args.evidence_dir.mkdir(mode=0o700, parents=True, exist_ok=False)
    c = connect('zsjos-mysql-1')
    try:
        with c.cursor() as q:
            environment(q)
            q.execute('SET NAMES utf8mb4')
            q.execute("SET time_zone='+08:00'")
            q.execute('SET SESSION innodb_lock_wait_timeout=2')
            c.rollback()
            if args.mode == 'prepare':
                q.execute('START TRANSACTION WITH CONSISTENT SNAPSHOT, READ ONLY')
                plan = prepare(q)
                c.rollback()
                save(args.evidence_dir/'plan.json', plan)
                result = rehearse(c, q, plan)
                result['plan_sha256'] = digest(plan)
                save(args.evidence_dir/'rehearsal.json', result)
            else:
                require(args.plan is not None, 'Frozen plan required')
                plan = decode(args.plan.read_text())
                proof = decode((args.plan.parent/'rehearsal.json').read_text())
                require(plan['key'] == MARK and proof['plan_sha256'] == digest(plan), 'Unrehearsed plan')
                if args.mode == 'apply':
                    current = capture(q, plan['original'], lock=True)
                    if same_state(current, expected_state(plan)):
                        result = verify(q, plan)
                        result['changed'] = 0
                        c.rollback()
                    else:
                        require(same_state(current, plan['before']), 'Source changed after rehearsal')
                        save(args.evidence_dir/'before.json', current)
                        count = execute(q, plan)
                        result = verify(q, plan)
                        save(args.evidence_dir/'after-precommit.json', capture(q, plan['original']))
                        c.commit()
                        result['changed'] = count
                    other = connect('zsjos-mysql-1')
                    try:
                        with other.cursor() as oq:
                            oq.execute('START TRANSACTION WITH CONSISTENT SNAPSHOT, READ ONLY')
                            result['independent'] = verify(oq, plan)
                    finally:
                        other.rollback(); other.close()
                else:
                    q.execute('START TRANSACTION WITH CONSISTENT SNAPSHOT, READ ONLY')
                    result = verify(q, plan)
                result['plan_sha256'] = digest(plan)
                save(args.evidence_dir/'result.json', result)
            print(encode(result))
    finally:
        c.rollback(); c.close()


if __name__ == '__main__':
    main()
