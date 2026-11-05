#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Fixed, explicitly approved test-tenant repair; never a bootstrap migration.

Only order Lead references and their version/audit fields change. Historical
snapshots, ownership, money and approval state are protected. See the matching
operation document. Prepare/rehearse precede apply; verify is read-only.
"""
import argparse
import collections
import copy
import os
from pathlib import Path

from transfer_student_20261015 import connect, require
from backfill_order_self_source_20261103 import encode, decode, digest, save, rows

MARK = 'repurchase-lead-link-20261105'
SERVER = '31aace8b-a55a-11f1-8c2b-a284c2b554a0'
ORDER = 'zsjos_order'
FIXTURE = 'verify_repurchase_lead_order'
# Business identifiers freeze the reviewed scope; no names or contact data.
PAIRS = {
    'OD202609211435200019': 'LD202608140002',
    'OD202609221356340006': 'LD202607260014',
    'OD202609231811290011': 'LD202606180009',
    'OD202609241423370005': 'LD202609200020',
    'OD202609260901440003': 'LD202609170066',
    'OD202609281653090012': 'LD202609040045',
    'OD202609291557070003': 'LD202609180104',
    'OD202609301917450016': 'LD202608110024',
    'OD202610080936380001': 'KZ202609230912000005',
    'OD202610081849390016': 'LD202608230008',
    'OD202610091203500006': 'KZ202609241130550006',
    'OD202610121621080009': 'KZ202609211543490031',
    'OD202610150855120003': 'LD202607220004',
    'OD202610191003270002': 'KZ202610131301400018',
    'OD202610191025510003': 'LD202609130101',
    'OD202610201758190009': 'KZ202610201731120047',
    'OD202610280949430004': 'LD202609070006',
    'OD202610291114570006': 'KZ202610281052350022',
    'OD202610291155270008': 'LD202609140138',
    'OD202610311744300005': 'KZ202610101002580011',
    'OD202611010852280001': 'KZ202610111513550012',
    'OD202611020841550001': 'LD202607180012',
    'OD202611021716320007': 'KZ202609281625490021',
    'OD202611031028170001': 'KZ202610121717450033',
}
PROTECTED_ORDER_TABLES = (
    'zsjos_order_approval_round', 'zsjos_order_item', 'zsjos_order_command',
    'zsjos_order_payment_allocation', 'zsjos_order_supervisor_confirmation',
    'zsjos_cashback', 'zsjos_payment_refund', 'zsjos_registration_case',
    'zsjos_service_relation',
)


def in_list(values):
    require(bool(values), 'Empty selection refused')
    return ','.join(['%s'] * len(values))


def environment(q):
    marker = Path('/etc/zsjos/agent-environment')
    env = os.environ.get('ZSJOS_AGENT_ENV', marker.read_text().strip() if marker.exists() else 'local')
    require(env == 'test', 'Only test environment authorized')
    identity = rows(q, 'SELECT @@server_uuid uuid,DATABASE() db,VERSION() version')[0]
    require(identity['uuid'] == SERVER and identity['db'] == 'zsjos', 'Database target changed')
    require(not rows(q, "SELECT trigger_name FROM information_schema.triggers "
                     "WHERE trigger_schema=DATABASE() AND event_object_table='zsjos_order'"),
            'Unexpected order write trigger')
    return identity


def capture(q, lock=False):
    suffix = ' FOR UPDATE NOWAIT' if lock else ''
    orders = rows(q, "SELECT * FROM zsjos_order WHERE tenant_id=1 AND deleted=0 "
                  "AND order_type='repurchase' AND status='effective' ORDER BY id" + suffix)
    require(len(orders) == 39, 'Approved order inventory changed')
    people = sorted({o['person_id'] for o in orders})
    leads = rows(q, 'SELECT * FROM zsjos_lead WHERE tenant_id=1 AND deleted=0 AND person_id IN ('
                 + in_list(people) + ') ORDER BY id' + suffix, people)
    persons = rows(q, 'SELECT * FROM zsjos_person WHERE tenant_id=1 AND id IN ('
                   + in_list(people) + ') ORDER BY id' + suffix, people)
    require(len(persons) == len(people) and all(p['deleted'] == b'\0' for p in persons),
            'Customer prerequisite changed')
    ids = sorted(o['id'] for o in orders)
    protected = {'zsjos_lead': leads, 'zsjos_person': persons}
    for table in PROTECTED_ORDER_TABLES:
        protected[table] = rows(q, f'SELECT * FROM {table} WHERE tenant_id=1 AND order_id IN ('
                                + in_list(ids) + ') ORDER BY id', ids)
    protected['zsjos_performance_attribution'] = rows(q,
        "SELECT * FROM zsjos_performance_attribution WHERE tenant_id=1 AND fact_type='ORDER' AND fact_id IN ("
        + in_list(ids) + ') ORDER BY id', ids)
    # Whole excluded order images include non-effective repurchases and first purchases.
    protected['excluded_orders'] = rows(q, 'SELECT * FROM zsjos_order WHERE tenant_id=1 AND order_no NOT IN ('
                                      + in_list(PAIRS) + ') ORDER BY id', sorted(PAIRS))
    lead_ids = sorted(l['id'] for l in leads)
    protected['zsjos_lead_public_sea_pool_cycle'] = rows(q,
        'SELECT * FROM zsjos_lead_public_sea_pool_cycle WHERE tenant_id=1 AND lead_id IN ('
        + in_list(lead_ids) + ') ORDER BY id', lead_ids)
    return {'orders': orders, 'protected': protected}


def select_candidate(order, leads):
    same = [l for l in leads if l['person_id'] == order['person_id']]
    if order['lead_id'] is not None:
        found = [l for l in same if l['id'] == order['lead_id']]
        require(len(found) == 1, 'Existing reference inconsistent')
        return found[0], 'preserve'
    if not same:
        return None, 'no_lead'
    owned = [l for l in same if l['owner_user_id'] == order['submitter_user_id']]
    pool = owned or same
    if len(pool) == 1:
        return pool[0], 'unique_owner' if owned else 'unique_other_owner'
    best = ([l for l in pool if l['status'] == 'won']
            or [l for l in pool if l['status'] == 'valid'] or pool)
    if len(best) == 1:
        return best[0], 'ranked'
    if order['order_no'] == 'OD202610150855120003':
        chosen = [l for l in best if l['lead_no'] == 'LD202607220004']
        require(len(chosen) == 1, 'User-decided candidate changed')
        return chosen[0], 'user_decision'
    raise RuntimeError('Unreviewed ambiguous association')


def prepare(q):
    identity = environment(q)
    before = capture(q)
    when = rows(q, 'SELECT NOW() t')[0]['t']
    rounds = {r['id']: r for r in before['protected']['zsjos_order_approval_round']}
    leads = before['protected']['zsjos_lead']
    summary = collections.Counter()
    changes = []
    for order in before['orders']:
        round_row = rounds.get(order['current_approval_round_id'])
        require(round_row and round_row['order_id'] == order['id']
                and round_row['deleted'] == b'\0' and round_row['status'] == 'approved',
                'Current approval not passed')
        candidate, reason = select_candidate(order, leads)
        summary[reason] += 1
        if reason in ('preserve', 'no_lead'):
            require(order['order_no'] not in PAIRS, 'Reviewed target lost')
            continue
        require(PAIRS.get(order['order_no']) == candidate['lead_no'], 'Manifest candidate changed')
        require(candidate['partner_id'] is None, 'New partner statistics impact requires review')
        require(not any(r['lead_id'] == candidate['id'] and r['status'] == 'active'
                        and r['deleted'] == b'\0'
                        for r in before['protected']['zsjos_lead_public_sea_pool_cycle']),
                'New public-sea relationship requires review')
        after = copy.deepcopy(order)
        after.update(lead_id=candidate['id'], version=order['version'] + 1, updater=MARK, update_time=when)
        changes.append({'before': order, 'after': after, 'lead_no': candidate['lead_no'],
                        'reason': reason, 'other_owner': candidate['owner_user_id'] != order['submitter_user_id']})
    require(len(changes) == 24 and summary['preserve'] == 5 and summary['no_lead'] == 10,
            'Reviewed counts changed')
    return {'repair': MARK, 'identity': identity, 'before': before, 'changes': changes,
            'summary': dict(summary), 'pairs': PAIRS}


def targets(q, plan, table, lock=False):
    require(table in (ORDER, FIXTURE), 'Invalid target table')
    ids = sorted(c['before']['id'] for c in plan['changes'])
    result = rows(q, f'SELECT * FROM {table} WHERE tenant_id=1 AND id IN ('
                  + in_list(ids) + ') ORDER BY id' + (' FOR UPDATE NOWAIT' if lock else ''), ids)
    require(len(result) == len(ids), 'Target row missing')
    return {r['id']: r for r in result}


def execute(q, plan, table, limit=None, fail_at=None, restore=False):
    require(table in (ORDER, FIXTURE) and (not restore or table == FIXTURE), 'Invalid write target')
    actual = targets(q, plan, table, lock=True)
    pending = []
    for change in plan['changes']:
        start, end = (change['after'], change['before']) if restore else (change['before'], change['after'])
        current = actual[start['id']]
        if current == end:
            continue
        require(current == start, 'Order differs from exact reviewed image')
        pending.append((current, end))
    pending = pending[:limit] if limit else pending
    for index, (current, end) in enumerate(pending, 1):
        q.execute(f'UPDATE {table} SET lead_id=%s,version=%s,updater=%s,update_time=%s '
                  'WHERE tenant_id=1 AND id=%s AND version=%s AND deleted=0 '
                  "AND order_type='repurchase' AND status='effective' AND lead_id <=> %s",
                  (end['lead_id'], end['version'], end['updater'], end['update_time'],
                   current['id'], current['version'], current['lead_id']))
        require(q.rowcount == 1, 'Unexpected affected count')
        if index == fail_at:
            raise RuntimeError('injected_failure')
    actual = targets(q, plan, table)
    require(all(actual[end['id']] == end for _, end in pending), 'Complete postimage mismatch')
    return len(pending)


def rehearsal(conn, plan):
    with conn.cursor() as q:
        q.execute(f'CREATE TEMPORARY TABLE {FIXTURE} LIKE {ORDER}')
        columns = [r['COLUMN_NAME'] for r in rows(q,
            "SELECT COLUMN_NAME,EXTRA FROM information_schema.columns WHERE table_schema=DATABASE() "
            "AND table_name='zsjos_order' ORDER BY ORDINAL_POSITION")
            if not any(x in r['EXTRA'] for x in ('VIRTUAL GENERATED', 'STORED GENERATED'))]
        quoted = ','.join('`' + col + '`' for col in columns)
        for change in plan['changes']:
            q.execute(f'INSERT INTO {FIXTURE} ({quoted}) VALUES ({in_list(columns)})',
                      [change['before'][col] for col in columns])
    conn.commit()
    with conn.cursor() as q:
        require(execute(q, plan, FIXTURE) == 24, 'Initial rehearsal count')
        require(execute(q, plan, FIXTURE) == 0, 'Replay not empty')
        require(execute(q, plan, FIXTURE, restore=True) == 24, 'Restore count')
    conn.commit()
    for position in (1, 12, 24):
        try:
            with conn.cursor() as q:
                execute(q, plan, FIXTURE, fail_at=position)
            raise AssertionError('Injection did not execute')
        except RuntimeError as error:
            require(str(error) == 'injected_failure', 'Unexpected rehearsal failure')
        conn.rollback()
        with conn.cursor() as q:
            actual = targets(q, plan, FIXTURE)
            require(all(actual[c['before']['id']] == c['before'] for c in plan['changes']),
                    'Failure rollback did not restore original images')
        conn.rollback()
    with conn.cursor() as q:
        require(execute(q, plan, FIXTURE, limit=7) == 7, 'Partial fixture count')
    conn.commit()
    with conn.cursor() as q:
        require(execute(q, plan, FIXTURE) == 17, 'Partial recovery count')
        require(execute(q, plan, FIXTURE, restore=True) == 24, 'Recovery restore count')
    conn.commit()
    with conn.cursor() as q:
        q.execute(f'UPDATE {FIXTURE} SET version=version+1 WHERE id=%s',
                  (plan['changes'][-1]['before']['id'],))
        conflict = targets(q, plan, FIXTURE)
        try:
            execute(q, plan, FIXTURE)
            raise AssertionError('Conflicting preimage accepted')
        except RuntimeError as error:
            require(str(error) == 'Order differs from exact reviewed image', 'Unexpected conflict failure')
        require(targets(q, plan, FIXTURE) == conflict, 'Conflict refusal partially wrote')
    conn.rollback()
    with conn.cursor() as q:
        require(execute(q, plan, FIXTURE) == 24, 'Reapply count')
    conn.commit()
    return dict(initial=24, replay=0, rollback_positions=[1, 12, 24],
                partial=7, recovery=17, conflict_refused=True, restore_and_reapply=True)


def load_plan(path):
    plan = decode(path.read_text(encoding='utf-8'))
    require(plan['repair'] == MARK and plan['pairs'] == PAIRS and len(plan['changes']) == 24,
            'Unrecognized repair manifest')
    require({c['before']['order_no']: c['lead_no'] for c in plan['changes']} == PAIRS,
            'Frozen pair mapping differs')
    for change in plan['changes']:
        before, after = change['before'], change['after']
        require(before['tenant_id'] == 1 and before['status'] == 'effective'
                and before['order_type'] == 'repurchase' and before['lead_id'] is None,
                'Invalid before-image')
        changed = {k for k in before if before[k] != after[k]}
        require(changed <= {'lead_id', 'version', 'updater', 'update_time'}
                and after['version'] == before['version'] + 1 and after['updater'] == MARK,
                'Manifest exceeds authorized field scope')
        lead = next(l for l in plan['before']['protected']['zsjos_lead'] if l['lead_no'] == change['lead_no'])
        require(after['lead_id'] == lead['id'] and lead['person_id'] == before['person_id'],
                'Frozen reference mismatch')
    return plan


def verify_images(q, plan):
    actual = targets(q, plan, ORDER)
    require(all(actual[c['after']['id']] == c['after'] for c in plan['changes']),
            'Committed reference/full postimage verification failed')
    ids = sorted(actual)
    values = rows(q, 'SELECT student_name,HEX(student_name) hex_value FROM zsjos_order '
                  'WHERE tenant_id=1 AND id IN (' + in_list(ids) + ')', ids)
    for value in values:
        require(value['student_name'] is None or bytes.fromhex(value['hex_value']).decode('utf-8')
                == value['student_name'], 'UTF-8 byte verification failed')
    return {'orders': len(actual), 'full_images_match': True, 'utf8_hex': True}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('mode', choices=('prepare', 'apply', 'verify'))
    parser.add_argument('--evidence-dir', type=Path, required=True)
    parser.add_argument('--plan', type=Path)
    args = parser.parse_args()
    os.umask(0o077)
    args.evidence_dir.mkdir(parents=True, exist_ok=False, mode=0o700)
    conn = connect('zsjos-mysql-1')
    try:
        with conn.cursor() as q:
            q.execute('SET NAMES utf8mb4')
            q.execute("SET time_zone='+08:00'")
            q.execute('SET SESSION innodb_lock_wait_timeout=3')
            environment(q)
        conn.rollback()
        if args.mode == 'prepare':
            with conn.cursor() as q:
                q.execute('SET TRANSACTION READ ONLY')
                q.execute('START TRANSACTION WITH CONSISTENT SNAPSHOT')
                plan = prepare(q)
            conn.rollback()
            save(args.evidence_dir / 'plan.json', plan)
            result = rehearsal(conn, plan)
            result.update(plan_sha256=digest(plan), summary=plan['summary'])
            save(args.evidence_dir / 'rehearsal.json', result)
        else:
            require(args.plan is not None, 'Frozen plan required')
            plan = load_plan(args.plan)
            proof = decode((args.plan.parent / 'rehearsal.json').read_text())
            require(proof['plan_sha256'] == digest(plan), 'Rehearsal belongs to another plan')
            if args.mode == 'apply':
                with conn.cursor() as q:
                    current = capture(q, lock=True)
                    require(current['protected'] == plan['before']['protected'], 'Protected/candidate rows changed')
                    actual = {o['id']: o for o in current['orders']}
                    changes = {c['before']['id']: c for c in plan['changes']}
                    for old in plan['before']['orders']:
                        allowed = [changes[old['id']]['after'], old] if old['id'] in changes else [old]
                        require(actual.get(old['id']) in allowed, 'Order inventory drift')
                    save(args.evidence_dir / 'before.json', current)
                    count = execute(q, plan, ORDER)
                    result = verify_images(q, plan)
                    after = capture(q)
                    require(after['protected'] == current['protected'], 'Protected row changed')
                    save(args.evidence_dir / 'after-precommit.json', after)
                conn.commit()
                result.update(changed=count, protected_rows=sum(len(x) for x in current['protected'].values()),
                              protected_sha256=digest(current['protected']))
                independent = connect('zsjos-mysql-1')
                try:
                    with independent.cursor() as q:
                        q.execute('SET TRANSACTION READ ONLY')
                        q.execute('START TRANSACTION WITH CONSISTENT SNAPSHOT')
                        result['independent'] = verify_images(q, plan)
                        require(capture(q)['protected'] == current['protected'],
                                'Independent protected-row comparison changed; inspect concurrent work')
                finally:
                    independent.rollback()
                    independent.close()
            else:
                with conn.cursor() as q:
                    q.execute('SET TRANSACTION READ ONLY')
                    q.execute('START TRANSACTION WITH CONSISTENT SNAPSHOT')
                    result = verify_images(q, plan)
                    result['protected_match'] = capture(q)['protected'] == plan['before']['protected']
            result['plan_sha256'] = digest(plan)
            save(args.evidence_dir / 'result.json', result)
        print(encode(result))
    finally:
        conn.rollback()
        conn.close()


if __name__ == '__main__':
    main()
