#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Authorized test tenant-1 ORDER receipt repair, exactly 11 audited facts.

Requires existing schema and proven ASSIGNMENT histories. No DDL on business
tables, no migration version writes, no order/Lead/service mutation or deletion.
prepare captures private full images and rehearses on MySQL temporary tables;
apply requires the plan hash, guards dependencies, updates in one transaction.
Replay skips exact after-images; partial application resumes safely. Restore
refuses any subsequent edits and is an explicit operator action.
"""
import argparse
import copy
import datetime as dt
import hashlib
import json
import os
from pathlib import Path
from transfer_student_20261015 import connect, encoded, decoded, require, rows

ROOT = Path(__file__).resolve().parents[4]
TABLE = 'zsjos_performance_attribution'
MARK = 'order-receipt-repair-20261016'
# fact_id, order business number, legacy event, proven receipt. Internal IDs
# are technical guards, never customer-facing Lead identifiers.
TARGETS = {
    3467: ('OD202609240833480001', 2181, 73498),
    13474: ('OD202609282047360014', 4775, 6766),
    14973: ('OD202609301107210005', 5951, 7651),
    15056: ('OD202609301142080008', 5951, 7651),
    15764: ('OD202609301646250013', 2976, 72368),
    15861: ('OD202609302306590021', 3293, 73535),
    18925: ('OD202610091403220007', 5426, 6981),
    19847: ('OD202610101455100005', 1044, 72892),
    19895: ('OD202610101523310006', 1044, 72892),
    20204: ('OD202610101821370007', 5426, 6981),
    21645: ('OD202610131119360005', 962, 74542),
}
TRANSITIONS = "'accept','claim','transfer','recycle','release_to_claim_pool','public_pool'"


def digest(value):
    return hashlib.sha256(encoded(value).encode('utf-8')).hexdigest()


def save(path, value):
    raw = encoded(value).encode('utf-8')
    fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(fd, 'wb') as f:
        f.write(raw); f.flush(); os.fsync(f.fileno())
    return hashlib.sha256(raw).hexdigest()


def fetch(q, table, rid, lock=False):
    found = rows(q, f'SELECT * FROM {table} WHERE tenant_id=1 AND id=%s'
                 + (' FOR UPDATE' if lock else ''), (rid,))
    require(len(found) == 1, 'Missing/ambiguous target')
    return found[0]


def validate(q, fact, original=False):
    no, bad, candidate = TARGETS[fact['id']]
    require(fact['fact_type'] == 'ORDER' and fact['deleted'] == b'\x00', 'Not a live ORDER fact')
    order = fetch(q, 'zsjos_order', fact['fact_id'], True)
    lead = fetch(q, 'zsjos_lead', fact['lead_id'], True)
    require(order['order_no'] == no and order['deleted'] == b'\x00'
            and order['lead_id'] == lead['id'] and lead['deleted'] == b'\x00'
            and order['formal_sales_user_id'] == fact['user_id'] == lead['owner_user_id']
            and order['status'] in ('effective', 'superseded'), 'Order/Lead guard changed')
    old = fetch(q, 'zsjos_lead_assignment_history', bad, True)
    real = fetch(q, 'zsjos_lead_assignment_history', candidate, True)
    require(old['lead_id'] == real['lead_id'] == lead['id']
            and old['deleted'] == real['deleted'] == b'\x00' and old['action_type'] == 'other'
            and real['action_type'] in ('accept', 'claim', 'transfer')
            and real['to_owner_user_id'] == fact['user_id'], 'Receipt evidence changed')
    for bound in (order['submitted_at'], None):
        found = rows(q, 'SELECT id FROM zsjos_lead_assignment_history WHERE tenant_id=1 AND deleted=0 '
                     f'AND lead_id=%s AND action_type IN ({TRANSITIONS}) '
                     + ('AND occurred_at<=%s ' if bound else '')
                     + 'ORDER BY occurred_at DESC,id DESC LIMIT 1',
                     (lead['id'], bound) if bound else (lead['id'],))
        require(len(found) == 1 and found[0]['id'] == candidate, 'Receipt is no longer the submitted/current round')
    facts = rows(q, f"SELECT * FROM {TABLE} WHERE tenant_id=1 AND deleted=0 AND fact_type='ASSIGNMENT' AND fact_id=%s FOR UPDATE", (candidate,))
    require(len(facts) == 1 and facts[0]['user_id'] == fact['user_id']
            and facts[0]['lead_id'] == lead['id']
            and facts[0]['received_at'] == real['occurred_at'], 'Receipt snapshot missing/ambiguous')
    linked = lead['current_assignment_history_id']
    if linked != candidate:
        current = fetch(q, 'zsjos_lead_assignment_history', linked, True)
        require(current['lead_id'] == lead['id'] and current['action_type'] == 'other'
                and current['deleted'] == b'\x00', 'Current link cannot normalize to receipt')
    if original:
        require(fact['assignment_id'] == bad, 'Legacy preimage changed')
    else:
        require(fact['assignment_id'] in (bad, candidate), 'Unexpected assignment')
    return candidate, real['occurred_at']


def protected(q, plan):
    # Scope only direct dependencies; the running application may change unrelated data.
    orders = sorted({op['before']['fact_id'] for op in plan['ops']})
    leads = sorted({op['before']['lead_id'] for op in plan['ops']})
    placeholders = lambda ids: ','.join(['%s'] * len(ids))
    queries = {
        'orders': (f'SELECT * FROM zsjos_order WHERE tenant_id=1 AND id IN ({placeholders(orders)}) ORDER BY id', orders),
        'leads': (f'SELECT * FROM zsjos_lead WHERE tenant_id=1 AND id IN ({placeholders(leads)}) ORDER BY id', leads),
        'histories': (f'SELECT * FROM zsjos_lead_assignment_history WHERE tenant_id=1 AND lead_id IN ({placeholders(leads)}) ORDER BY id', leads),
        'services': (f'SELECT * FROM zsjos_service_relation WHERE tenant_id=1 AND order_id IN ({placeholders(orders)}) ORDER BY id', orders),
        'other_facts': (f'SELECT * FROM {TABLE} WHERE tenant_id=1 AND lead_id IN ({placeholders(leads)}) AND id NOT IN ({placeholders(TARGETS)}) ORDER BY id', leads + list(TARGETS)),
    }
    # Order/Lead/service rows contain Decimal; hash SQL JSON to preserve exact values.
    result = {}
    for name, (sql, args) in queries.items():
        data = rows(q, sql, args)
        canonical = json.dumps(data, default=lambda x: encoded(x) if isinstance(x, (bytes, dt.datetime, dt.date)) else str(x), sort_keys=True, ensure_ascii=False)
        result[name] = {'rows': len(data), 'sha256': hashlib.sha256(canonical.encode()).hexdigest()}
    return result


def mutate(q, plan, table=TABLE, restore=False, fail_after=None, limit=None):
    actual = [fetch(q, table, op['before']['id'], True) for op in plan['ops']]
    for row, op in zip(actual, plan['ops']):
        require(row in (op['before'], op['after']) and (not restore or row == op['after']), 'Stale preimage; refusing overwrite')
    count = 0
    for row, op in zip(actual, plan['ops']):
        target = op['before'] if restore else op['after']
        if row == target:
            continue
        columns = ['assignment_id', 'received_at', 'updater', 'update_time']
        q.execute(f'UPDATE {table} SET ' + ','.join('`'+x+'`=%s' for x in columns)
                  + ' WHERE tenant_id=1 AND id=%s', (*[target[x] for x in columns], row['id']))
        require(q.rowcount == 1, 'Unexpected update count')
        count += 1
        if count == fail_after:
            raise RuntimeError('injected_failure')
        if count == limit:
            break
    return count


def verify(q, plan, table=TABLE, before=False):
    for op in plan['ops']:
        require(fetch(q, table, op['before']['id']) == op['before' if before else 'after'], 'Exact image differs')


def rehearse(c, q, plan):
    temp = 'tmp_order_receipt_repair'
    q.execute(f'CREATE TEMPORARY TABLE {temp} LIKE {TABLE}')
    q.execute(f'INSERT INTO {temp} SELECT * FROM {TABLE} WHERE tenant_id=1 AND id IN (' + ','.join(['%s']*11)+')', list(TARGETS))
    c.commit()
    try:
        mutate(q, plan, temp, fail_after=4)
    except RuntimeError as exc:
        require(str(exc) == 'injected_failure', 'Unexpected failure')
        c.rollback()
    else:
        raise RuntimeError('Failure injection absent')
    verify(q, plan, temp, before=True)
    require(mutate(q, plan, temp) == 11, 'Initial update count')
    verify(q, plan, temp)
    require(mutate(q, plan, temp) == 0, 'Repeat must skip')
    mutate(q, plan, temp, restore=True);verify(q, plan, temp, before=True);c.commit()
    require(mutate(q, plan, temp, limit=4) == 4, 'Partial fixture failed');c.commit()
    require(mutate(q, plan, temp) == 7, 'Partial recovery failed')
    verify(q, plan, temp);mutate(q, plan, temp, restore=True);c.commit()
    q.execute(f"UPDATE {temp} SET updater='concurrent-change' WHERE id=%s", (next(iter(TARGETS)),))
    for restore in (False, True):
        try:
            mutate(q, plan, temp, restore=restore)
        except RuntimeError as exc:
            require('Stale' in str(exc), 'Unexpected refusal')
        else:
            raise RuntimeError('Concurrent edit accepted')
    c.rollback();verify(q, plan, temp, before=True);c.rollback()
    q.execute(f'DROP TEMPORARY TABLE {temp}')
    return ['initial-11', 'repeat-0', 'failure-rollback', 'partial-recovery-7', 'stale-apply/restore-refused', 'exact-restore']


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('mode', choices=('prepare', 'apply', 'verify', 'restore'))
    p.add_argument('--evidence', type=Path, required=True)
    p.add_argument('--expected-sha256')
    a = p.parse_args()
    env = os.environ.get('ZSJOS_AGENT_ENV')
    if env is None:
        f = Path('/etc/zsjos/agent-environment');env = f.read_text().strip() if f.exists() else 'local'
    require(env == 'test', 'Only the explicitly authorized test environment is supported')
    evidence = a.evidence.resolve();require(not evidence.is_relative_to(ROOT), 'Private backup must be outside repository')
    c = connect('zsjos-mysql-1')
    try:
        with c.cursor() as q:
            q.execute('SET NAMES utf8mb4');q.execute("SET time_zone='+08:00'")
            require(rows(q, "SELECT GET_LOCK('order-receipt-repair-20261016',10) ok")[0]['ok'] == 1, 'Repair lock unavailable')
            engine = rows(q, 'SELECT ENGINE engine FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name=%s', (TABLE,))
            require(engine[0]['engine'] == 'InnoDB', 'InnoDB required');c.rollback()
            if a.mode == 'prepare':
                evidence.mkdir(mode=0o700, exist_ok=False)
                ops = [];now = rows(q, 'SELECT NOW() now')[0]['now']
                for rid in TARGETS:
                    before = fetch(q, TABLE, rid, True);candidate, at = validate(q, before, True)
                    after = copy.deepcopy(before);after.update(assignment_id=candidate, received_at=at, updater=MARK, update_time=now)
                    ops.append({'before': before, 'after': after})
                plan = {'marker': MARK, 'tenant': 1, 'targets': list(TARGETS), 'ops': ops}
                plan['protected'] = protected(q, plan);c.rollback()
                sha = save(evidence/'plan.json', plan);checks = rehearse(c, q, plan)
                save(evidence/'rehearsal.json', {'sha256': sha, 'checks': checks})
                print(json.dumps({'targets': 11, 'sha256': sha, 'checks': checks}));return
            raw = (evidence/'plan.json').read_bytes()
            require(hashlib.sha256(raw).hexdigest() == a.expected_sha256, 'Exact reviewed plan SHA256 required')
            plan = decoded(raw.decode('utf-8'))
            require(plan['marker'] == MARK and plan['tenant'] == 1 and plan['targets'] == list(TARGETS), 'Scope mismatch')
            rehearsal = decoded((evidence/'rehearsal.json').read_text())
            require(rehearsal['sha256'] == a.expected_sha256, 'Rehearsal mismatch')
            for op in plan['ops']:
                fact = fetch(q, TABLE, op['before']['id'], True);validate(q, fact)
            require(protected(q, plan) == plan['protected'], 'Direct dependency changed; inspect and prepare new plan')
            if a.mode == 'verify':
                changed = 0;verify(q, plan);c.rollback()
            else:
                changed = mutate(q, plan, restore=a.mode == 'restore')
                verify(q, plan, before=a.mode == 'restore')
                require(protected(q, plan) == plan['protected'], 'Unintended dependent mutation')
                c.commit()
            verify(q, plan, before=a.mode == 'restore')
            require(protected(q, plan) == plan['protected'], 'Post-commit dependency difference')
            result = {'mode': a.mode, 'changed': changed, 'exact_images': 11, 'dependencies_verified': list(plan['protected'])}
            save(evidence/(a.mode+'-'+dt.datetime.now().strftime('%H%M%S%f')+'.json'), result)
            print(json.dumps(result))
    except Exception:
        c.rollback();raise
    finally:
        c.close()


if __name__ == '__main__':
    main()
