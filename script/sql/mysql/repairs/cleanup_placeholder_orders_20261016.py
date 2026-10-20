#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Explicitly authorized test tenant 1 cleanup; never cancel/delete a service.

Prepare/rehearse before apply. Full private preimages bind the plan to current data.
Restore refuses subsequent edits. No DDL, migration ledger, BPM or notification writes.
"""
import argparse
import collections
import copy
import datetime as dt
import hashlib
import json
import os
from pathlib import Path
import sys
from decimal import Decimal

from transfer_student_20261015 import connect

ROOT = Path(__file__).resolve().parents[4]
sys.path.insert(0, str(ROOT / 'script/sql/mysql/tools'))
from student_virtual_order_cleanup import guard_references

TENANT = 1
MARKER = 'placeholder-cleanup-20261016'
NOS = tuple('OD20260920140734' + str(i).zfill(4)
            for i in range(37, 93) if i not in (40, 91))
ORDER, ITEM, CASE, SERVICE = TABLES = (
    'zsjos_order', 'zsjos_order_item', 'zsjos_registration_case', 'zsjos_service_relation')
PROTECTED_TABLES = TABLES + (
    'zsjos_collaboration_group', 'zsjos_media_account', 'zsjos_positioning_card',
    'zsjos_positioning_card_submission', 'zsjos_positioning_service_card',
    'zsjos_student_delivery_plan', 'zsjos_student_positioning_interview',
    'zsjos_student_contact_record', 'zsjos_student_contact_extension',
    'zsjos_student_collaborator_assignment_log', 'zsjos_registration_case_checklist_item',
    'zsjos_registration_item', 'zsjos_registration_command',
    'zsjos_registration_class_assignment', 'zsjos_refund_item')


def encoded(value):
    def default(item):
        if isinstance(item, Decimal):
            return {'$decimal': str(item)}
        if isinstance(item, (dt.datetime, dt.date)):
            return {'$date': item.isoformat(), '$type': type(item).__name__}
        if isinstance(item, bytes):
            return {'$bytes': item.hex()}
        raise TypeError(type(item).__name__)
    return json.dumps(value, ensure_ascii=False, sort_keys=True, indent=2, default=default)


def decoded(text):
    def hook(item):
        if '$decimal' in item:
            return Decimal(item['$decimal'])
        if '$date' in item:
            return getattr(dt, item['$type']).fromisoformat(item['$date'])
        if '$bytes' in item:
            return bytes.fromhex(item['$bytes'])
        return item
    return json.loads(text, object_hook=hook)


def require(ok, message):
    if not ok:
        raise RuntimeError(message)


def rows(q, sql, args=None):
    q.execute(sql, args)
    return list(q.fetchall())


def marks(values):
    return ','.join(['%s'] * len(values))


def digest(value):
    return hashlib.sha256(encoded(value).encode('utf-8')).hexdigest()


def private_write(path, value):
    payload = encoded(value).encode('utf-8')
    fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(fd, 'wb') as out:
        out.write(payload)
        out.flush()
        os.fsync(out.fileno())
    return hashlib.sha256(payload).hexdigest()


def active(row):
    return row['deleted'] in (b'\x00', 0, False)


def capture(q, lock=False):
    suffix = ' FOR UPDATE' if lock else ''
    orders = rows(q, f'SELECT * FROM {ORDER} WHERE tenant_id=1 AND order_no IN ({marks(NOS)}) ORDER BY id' + suffix, NOS)
    require(len(orders) == 54 and {r['order_no'] for r in orders} == set(NOS), 'Exact order whitelist missing or duplicated')
    ids = [r['id'] for r in orders]
    result = {ORDER: orders}
    for table in (ITEM, CASE, SERVICE):
        result[table] = rows(q, f'SELECT * FROM {table} WHERE tenant_id=1 AND order_id IN ({marks(ids)}) ORDER BY id' + suffix, ids)
    return result


def prepare(q, state):
    for table in TABLES:
        require(len(state[table]) == 54 and all(active(r) for r in state[table]), 'Expected 54 live rows: ' + table)
    mutations = []
    now = rows(q, 'SELECT NOW() now')[0]['now']
    for order in state[ORDER]:
        require(order['creator'] == 'seed-students' and order['status'] == 'effective'
                and order['total_amount'] == 0 and order['payable_amount'] == 0
                and order['current_approval_round_id'] is None and order['lead_id'] is None, 'Order no longer matches approved evidence')
        chain = {}
        for table in (ITEM, CASE, SERVICE):
            linked = [r for r in state[table] if r['order_id'] == order['id']]
            require(len(linked) == 1, 'Non-unique chain: ' + table)
            chain[table] = linked[0]
        service, item, case = chain[SERVICE], chain[ITEM], chain[CASE]
        require(service['person_id'] == order['person_id'] and service['order_item_id'] == item['id']
                and service['registration_case_id'] == case['id'] and case['status'] == 'completed'
                and service['status'] == 'active' and service['acceptance_status'] == 'accepted', 'Service origin/state changed')
        require(service['service_snapshot'] and json.loads(service['service_snapshot']) == json.loads(item['product_snapshot']), 'Course snapshot unavailable or differs')
    # Detach first, then soft-delete dependents and finally orders. Preserve status and all domain fields.
    for table in (SERVICE, CASE, ITEM, ORDER):
        for before in state[table]:
            after = copy.deepcopy(before)
            if table == SERVICE:
                after.update(order_id=None, order_item_id=None, registration_case_id=None)
            else:
                after['deleted'] = b'\x01'
            if 'version' in after:
                after['version'] = (after['version'] or 0) + 1
            after.update(updater=MARKER, update_time=now)
            mutations.append({'table': table, 'before': before, 'after': after})
    return {'marker': MARKER, 'tenant': TENANT, 'orders': list(NOS), 'created_at': now, 'mutations': mutations}


def check_schema(q):
    cols = rows(q, "SELECT column_name FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_service_relation' AND column_name IN ('order_id','order_item_id','registration_case_id') AND is_nullable='YES'")
    require(len(cols) == 3, 'Nullable service origin prerequisite absent')
    for table in TABLES:
        engine = rows(q, 'SELECT engine FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name=%s', (table,))
        require(len(engine) == 1 and engine[0]['ENGINE'] == 'InnoDB', 'Transactional engine required')


def guards(c, q, plan):
    ids = {op['before']['id'] for op in plan['mutations'] if op['table'] == ORDER}
    items = {op['before']['id'] for op in plan['mutations'] if op['table'] == ITEM}
    cases = {op['before']['id'] for op in plan['mutations'] if op['table'] == CASE}
    guard_references(c, ids, items, cases)
    # Core-chain tables are excluded by the reusable generic guard. Check their cross-chain references explicitly.
    for table in TABLES:
        cols = {r['Field'] for r in rows(q, 'SHOW COLUMNS FROM ' + table)}
        owned = {op['before']['id'] for op in plan['mutations'] if op['table'] == table}
        for col, values in [('order_id', ids), ('order_item_id', items), ('registration_case_id', cases),
                            ('supersedes_order_id', ids), ('superseded_by_order_id', ids)]:
            if col not in cols:
                continue
            found = rows(q, f'SELECT id,tenant_id FROM {table} WHERE {col} IN ({marks(values)}) FOR UPDATE', tuple(values))
            require(all(r['tenant_id'] == TENANT and r['id'] in owned for r in found), 'New/cross-tenant chain reference')
    for table, typecol, idcol, kinds in [
        ('zsjos_business_task', 'biz_type', 'biz_id', ('sales-order', 'sales_order', 'order', 'registration_case')),
        ('zsjos_business_event', 'aggregate_type', 'aggregate_id', ('sales-order', 'sales_order', 'order', 'registration_case'))]:
        found = rows(q, f'SELECT COUNT(*) n FROM {table} WHERE tenant_id=1 AND {typecol} IN ({marks(kinds)}) AND {idcol} IN ({marks(ids | cases)})', (*kinds, *(ids | cases)))
        require(found[0]['n'] == 0, 'Unexpected polymorphic order/case dependency: ' + table)


def protected(q, plan):
    """Private complete table digests detect unintended changes, including non-target rows."""
    target = {t: {op['before']['id'] for op in plan['mutations'] if op['table'] == t} for t in TABLES}
    result = {}
    tables = [{'t': table} for table in PROTECTED_TABLES]
    for entry in tables:
        table = entry['t']
        # High-volume request audit is append-only runtime telemetry, outside this data repair.
        if '_bak_' in table or 'backup' in table or table == 'zsjos_business_audit_log':
            continue
        cols = {r['Field'] for r in rows(q, 'SHOW COLUMNS FROM `' + table + '`')}
        data = rows(q, 'SELECT * FROM `' + table + '`' + (' WHERE tenant_id=1' if 'tenant_id' in cols else '') + (' ORDER BY id' if 'id' in cols else ''))
        if table in target:
            data = [r for r in data if r['id'] not in target[table]]
        # Tables without a primary id still have deterministic canonical row ordering.
        result[table] = {'rows': len(data), 'sha256': digest(sorted(data, key=encoded))}
    return result


def apply(q, plan, mapping=None, fail_after=None, limit=None):
    mapping = mapping or {t: t for t in TABLES}
    current = []
    for op in plan['mutations']:
        table = mapping[op['table']]
        found = rows(q, f'SELECT * FROM {table} WHERE tenant_id=1 AND id=%s FOR UPDATE', (op['before']['id'],))
        require(len(found) == 1 and found[0] in (op['before'], op['after']), 'Stale or missing preimage: ' + op['table'])
        current.append(found[0])
    changed = 0
    for op, actual in zip(plan['mutations'], current):
        if actual == op['after']:
            continue
        cols = [col for col in op['after'] if op['before'][col] != op['after'][col]]
        values = [op['after'][col] for col in cols]
        setters = ','.join('`' + col + '`=%s' for col in cols)
        q.execute(f'UPDATE {mapping[op["table"]]} SET {setters} WHERE tenant_id=1 AND id=%s', (*values, op['before']['id']))
        require(q.rowcount == 1, 'Unexpected update count')
        changed += 1
        if changed == fail_after:
            raise RuntimeError('injected_failure')
        if changed == limit:
            break
    return changed


def verify(q, plan, mapping=None, restored=False):
    mapping = mapping or {t: t for t in TABLES}
    side = 'before' if restored else 'after'
    for op in plan['mutations']:
        found = rows(q, f'SELECT * FROM {mapping[op["table"]]} WHERE tenant_id=1 AND id=%s', (op['before']['id'],))
        require(found == [op[side]], 'Readback differs: ' + op['table'])


def restore(q, plan, mapping=None):
    mapping = mapping or {t: t for t in TABLES}
    verify(q, plan, mapping)
    for op in reversed(plan['mutations']):
        cols = [col for col in op['after'] if op['before'][col] != op['after'][col]]
        setters = ','.join('`' + col + '`=%s' for col in cols)
        q.execute(f'UPDATE {mapping[op["table"]]} SET {setters} WHERE tenant_id=1 AND id=%s',
                  (*[op['before'][col] for col in cols], op['before']['id']))
        require(q.rowcount == 1, 'Restore row missing')


def rehearse(c, q, plan):
    mapping = {t: 'tmp_ph54_' + str(i) for i, t in enumerate(TABLES)}
    for table, temp in mapping.items():
        q.execute(f'CREATE TEMPORARY TABLE {temp} LIKE {table}')
        ids = [op['before']['id'] for op in plan['mutations'] if op['table'] == table]
        generated = {r['col'] for r in rows(q,
            "SELECT column_name AS col FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name=%s AND generation_expression<>''", (table,))}
        cols = [r['Field'] for r in rows(q, 'SHOW COLUMNS FROM ' + table) if r['Field'] not in generated]
        quoted = ','.join('`' + col + '`' for col in cols)
        q.execute(f'INSERT INTO {temp} ({quoted}) SELECT {quoted} FROM {table} WHERE tenant_id=1 AND id IN ({marks(ids)})', ids)
    c.commit()
    try:
        apply(q, plan, mapping, fail_after=55)
    except RuntimeError as exc:
        require(str(exc) == 'injected_failure', str(exc))
        c.rollback()
    else:
        raise RuntimeError('Failure injection did not run')
    verify(q, plan, mapping, restored=True)
    require(apply(q, plan, mapping) == 216, 'Initial expected 216 updates')
    verify(q, plan, mapping)
    require(apply(q, plan, mapping) == 0, 'Replay must be zero')
    restore(q, plan, mapping)
    verify(q, plan, mapping, restored=True)
    c.commit()
    require(apply(q, plan, mapping, limit=60) == 60, 'Partial fixture not applied')
    c.commit()
    require(apply(q, plan, mapping) == 156, 'Partial recovery mismatch')
    verify(q, plan, mapping)
    restore(q, plan, mapping)
    c.commit()
    first = plan['mutations'][0]
    q.execute(f'UPDATE {mapping[SERVICE]} SET version=version+3 WHERE tenant_id=1 AND id=%s', (first['before']['id'],))
    try:
        apply(q, plan, mapping)
    except RuntimeError as exc:
        require('Stale' in str(exc), str(exc))
    else:
        raise RuntimeError('Stale preimage accepted')
    c.rollback()
    verify(q, plan, mapping, restored=True)
    c.rollback()
    return ['initial-216', 'repeat-0', 'injected-rollback', 'partial-recovery', 'stale-refused', 'exact-restore']


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('mode', choices=['prepare', 'apply', 'verify', 'restore'])
    p.add_argument('--evidence', type=Path, required=True)
    p.add_argument('--expected-sha256')
    args = p.parse_args()
    environment = os.environ.get('ZSJOS_AGENT_ENV')
    if environment is None:
        path = Path('/etc/zsjos/agent-environment')
        environment = path.read_text().strip() if path.exists() else 'local'
    require(environment == 'test', 'This authorization covers only the test environment')
    evidence = args.evidence.resolve()
    require(not evidence.is_relative_to(ROOT), 'Evidence must stay outside repository')
    c = connect('zsjos-mysql-1')
    try:
        with c.cursor() as q:
            q.execute('SET NAMES utf8mb4')
            q.execute("SET time_zone='+08:00'")
            check_schema(q)
            c.rollback()
            if args.mode == 'prepare':
                evidence.mkdir(mode=0o700, parents=False, exist_ok=False)
                q.execute('START TRANSACTION WITH CONSISTENT SNAPSHOT')
                plan = prepare(q, capture(q))
                guards(c, q, plan)
                plan['protected'] = protected(q, plan)
                c.rollback()
                sha = private_write(evidence / 'plan.json', plan)
                checks = rehearse(c, q, plan)
                private_write(evidence / 'rehearsal.json', {'sha256': sha, 'checks': checks})
                print(json.dumps({'orders': 54, 'updates': 216, 'sha256': sha, 'checks': checks}))
                return
            raw = (evidence / 'plan.json').read_bytes()
            require(hashlib.sha256(raw).hexdigest() == args.expected_sha256, 'Explicit plan SHA256 required')
            plan = decoded(raw.decode('utf-8'))
            require(plan['marker'] == MARKER and plan['tenant'] == TENANT and plan['orders'] == list(NOS), 'Plan scope mismatch')
            rehearsal = decoded((evidence / 'rehearsal.json').read_text())
            require(rehearsal['sha256'] == args.expected_sha256, 'Rehearsal evidence mismatch')
            require(rows(q, "SELECT GET_LOCK('placeholder-cleanup-54-tenant1',10) ok")[0]['ok'] == 1, 'Cleanup lock unavailable')
            c.rollback()
            q.execute('START TRANSACTION')
            # Lock targets before either mutation or exact-image recovery. Never change concurrent edits.
            for op in plan['mutations']:
                rows(q, f'SELECT id FROM {op["table"]} WHERE tenant_id=1 AND id=%s FOR UPDATE', (op['before']['id'],))
            guards(c, q, plan)
            require(protected(q, plan) == plan['protected'], 'Non-target data changed since preparation; inspect and prepare fresh evidence')
            if args.mode == 'restore':
                restore(q, plan)
                verify(q, plan, restored=True)
                changed = 216
            elif args.mode == 'apply':
                changed = apply(q, plan)
                verify(q, plan)
                require(apply(q, plan) == 0, 'In-transaction replay changed rows')
            else:
                changed = 0
                verify(q, plan)
            require(protected(q, plan) == plan['protected'], 'Unexpected non-target mutation')
            if args.mode == 'verify':
                c.rollback()
            else:
                c.commit()
            verify(q, plan, restored=args.mode == 'restore')
            require(protected(q, plan) == plan['protected'], 'Post-commit non-target difference; inspect before further action')
            result = {'mode': args.mode, 'changed': changed, 'orders': 54, 'services_preserved': 54,
                      'exact_images_verified': 216, 'protected_tables': len(plan['protected']), 'checked_at': rows(q, 'SELECT NOW() now')[0]['now']}
            private_write(evidence / (args.mode + '-' + dt.datetime.now().strftime('%H%M%S%f') + '.json'), result)
            print(encoded(result))
    except Exception:
        c.rollback()
        raise
    finally:
        c.close()


if __name__ == '__main__':
    main()
