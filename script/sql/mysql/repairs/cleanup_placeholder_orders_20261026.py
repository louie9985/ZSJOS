#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Exact authorized test-tenant cleanup; preserve independent student services.

No bootstrap/migration integration. Private plans contain sensitive before-images.
See docs/operations/placeholder-orders-cleanup-20261026.md for recovery boundaries.
"""
import argparse
import collections
import copy
import datetime as dt
import hashlib
import json
import os
from pathlib import Path
import re

from cleanup_placeholder_orders_20261016 import (
    encoded, decoded, require, rows, digest, private_write, active,
    apply as update_images, verify as verify_images, restore as restore_images,
    check_schema, ORDER, ITEM, CASE, SERVICE, TABLES,
)
from transfer_student_20261015 import connect

MARKER = 'placeholder-cleanup-20261026'
EVIDENCE = Path('/opt/zsjos-runtime/backups/placeholder-orders-20261026-test')
NOS = tuple(sorted(
    ['OD202609190228590001', 'OD202609201416529991',
     'OD202609201406530010', 'OD202609201539519002']
    + ['OD20260920140009' + f'{i:04}' for i in range(1, 36)]
    + ['OD20260921115948' + str(i) for i in range(8663, 8675)]
    + ['OD20260923162938' + str(i) for i in range(8800, 8804)]))
BATCHES = ('seed-liudandan', 'seed-students', 'seed-llyilin', 'seed-batch4')
SERVICE_IDS = tuple(range(898, 910))


def ids(values):
    return ','.join(str(int(v)) for v in sorted(set(values))) or '-1'


def quote(value):
    return '`' + value.replace('`', '``') + '`'


def metadata(q):
    result = {}
    for r in rows(q, 'SELECT TABLE_NAME,COLUMN_NAME,DATA_TYPE FROM information_schema.columns WHERE table_schema=DATABASE()'):
        result.setdefault(r['TABLE_NAME'], {})[r['COLUMN_NAME']] = r['DATA_TYPE']
    return result


def runtime():
    result = []
    for proc in Path('/proc').iterdir():
        if not proc.name.isdigit():
            continue
        try:
            args = (proc / 'cmdline').read_bytes().split(b'\0')
            if not args or b'java' not in args[0] or b'-jar' not in args:
                continue
            jar = Path(args[args.index(b'-jar') + 1].decode())
            if not jar.is_absolute():
                jar = (proc / 'cwd').resolve() / jar
            if 'zsjos' not in str(jar):
                continue
            ticks = (proc / 'stat').read_text().rsplit(')', 1)[1].split()[19]
            result.append({'pid': int(proc.name), 'ticks': ticks,
                           'jar': str(jar), 'sha256': hashlib.sha256(jar.read_bytes()).hexdigest()})
        except (FileNotFoundError, ProcessLookupError):
            continue
    require(len(result) == 1, 'Expected one running application')
    return result[0]


def capture(q, meta):
    orders = rows(q, 'SELECT * FROM zsjos_order WHERE tenant_id=1 AND order_no IN ('
                  + ','.join(['%s'] * len(NOS)) + ') ORDER BY id', NOS)
    require(len(orders) == 55 and {r['order_no'] for r in orders} == set(NOS), 'Order whitelist mismatch')
    people = {r['person_id'] for r in orders}
    # Protect all current services and real orders of these people, not only linked seeds.
    services = rows(q, f'SELECT * FROM {SERVICE} WHERE person_id IN ({ids(people)}) ORDER BY id')
    all_orders = rows(q, f'SELECT * FROM {ORDER} WHERE person_id IN ({ids(people)}) ORDER BY id')
    order_ids = {r['id'] for r in all_orders}
    items = rows(q, f'SELECT * FROM {ITEM} WHERE order_id IN ({ids(order_ids)}) ORDER BY id')
    cases = rows(q, f'SELECT * FROM {CASE} WHERE order_id IN ({ids(order_ids)}) ORDER BY id')
    keys = {'person_id': people, 'student_person_id': people,
            'service_relation_id': {r['id'] for r in services},
            'source_service_relation_id': {r['id'] for r in services},
            'create_service_relation_id': {r['id'] for r in services},
            'collaboration_group_id': {r['collaboration_group_id'] for r in services if r['collaboration_group_id']},
            'order_id': order_ids, 'order_item_id': {r['id'] for r in items},
            'registration_case_id': {r['id'] for r in cases}}
    accounts = rows(q, 'SELECT id FROM zsjos_media_account WHERE student_person_id IN (' + ids(people) + ')')
    keys['account_id'] = {r['id'] for r in accounts}
    for table, key in [('zsjos_content', 'content_id'), ('zsjos_production_ticket', 'ticket_id'),
                       ('zsjos_positioning_card', 'card_id'), ('zsjos_student_delivery_plan', 'plan_id'),
                       ('zsjos_student_delivery_stage', 'stage_id'), ('zsjos_content_review_batch', 'batch_id')]:
        cond = [f'{quote(k)} IN ({ids(v)})' for k, v in keys.items() if k in meta[table]]
        keys[key] = {r['id'] for r in rows(q, f'SELECT id FROM {table} WHERE ' + ' OR '.join(cond))} if cond else set()
    special = {'zsjos_person': people, 'zsjos_collaboration_group': keys['collaboration_group_id'],
               ORDER: order_ids, ITEM: keys['order_item_id'], CASE: keys['registration_case_id'],
               SERVICE: keys['service_relation_id']}
    queries = {}
    for table, cols in sorted(meta.items()):
        if not table.startswith('zsjos_') or '_bak_' in table or 'backup' in table or 'id' not in cols:
            continue
        cond = [f'{quote(k)} IN ({ids(v)})' for k, v in keys.items() if k in cols]
        if table in special:
            cond.append(f'id IN ({ids(special[table])})')
        if table == 'zsjos_business_task':
            cond.append(f"(biz_type IN ('student_service','student-service','service_relation') AND biz_id IN ({ids(keys['service_relation_id'])}))")
        if cond:
            queries[table] = 'SELECT * FROM ' + quote(table) + ' WHERE (' + ' OR '.join(cond) + ') ORDER BY id'
    return orders, queries, {t: rows(q, sql) for t, sql in queries.items()}


def reference_group(key, order_ids, item_ids, case_ids):
    key = re.sub('[^a-z]', '', key.lower())
    if key in ('orderid', 'sourceorderid', 'previousorderid', 'currentorderid',
               'supersedesorderid', 'supersededbyorderid', 'salesorderid', 'oldorderid', 'neworderid'):
        return order_ids
    if key in ('orderitemid', 'sourceorderitemid', 'oldorderitemid', 'neworderitemid'):
        return item_ids
    if key in ('registrationcaseid', 'oldregistrationcaseid', 'newregistrationcaseid'):
        return case_ids
    return set()


def guards(q, meta, plan, lock=False):
    groups = {t: {m['before']['id'] for m in plan['mutations'] if m['table'] == t} for t in TABLES}
    order_ids, item_ids, case_ids = (groups[t] for t in (ORDER, ITEM, CASE))
    suffix = ' FOR UPDATE' if lock else ''
    direct_checks = 0
    for t, cols in meta.items():
        if '_bak_' in t or 'backup' in t:
            continue
        for col in cols:
            values = reference_group(col, order_ids, item_ids, case_ids)
            if not values:
                continue
            direct_checks += 1
            found = rows(q, f'SELECT id FROM {quote(t)} WHERE {quote(col)} IN ({ids(values)})' + suffix)
            # Cross-chain references cannot become an implicit extension of the approved scope.
            allowed = groups.get(t, set()) if (t, col) in ((ITEM, 'order_id'), (CASE, 'order_id'),
                       (SERVICE, 'order_id'), (SERVICE, 'order_item_id'), (SERVICE, 'registration_case_id')) else set()
            require(all(r['id'] in allowed for r in found), 'Unexpected direct reference: ' + t + '.' + col)
    polys = [('zsjos_business_event', 'aggregate_type', 'aggregate_id'),
             ('zsjos_business_task', 'biz_type', 'biz_id'),
             ('zsjos_performance_attribution', 'fact_type', 'fact_id'),
             ('system_notify_business_outbox', 'biz_type', 'biz_id'),
             ('system_notify_message', 'biz_type', 'biz_id'),
             ('zsjos_standard_form', 'biz_type', 'biz_id'),
             ('zsjos_payment_gateway_event', 'business_type', 'business_id'),
             ('zsjos_personal_calendar_event', 'source_type', 'source_id')]
    for t, tc, ic in polys:
        for kinds, values in [(('order', 'sales-order', 'sales_order'), order_ids),
                              (('order_item', 'order-item'), item_ids),
                              (('registration_case', 'registration-case'), case_ids)]:
            found = rows(q, f'SELECT id FROM {t} WHERE LOWER({tc}) IN (%s,%s' + (',%s' if len(kinds) == 3 else '')
                         + f') AND {ic} IN ({ids(values)})' + suffix, kinds)
            require(not found, 'Unexpected business reference: ' + t)
    for t, col in [('ACT_HI_PROCINST', 'BUSINESS_KEY_'), ('ACT_RU_EXECUTION', 'BUSINESS_KEY_'),
                   ('bpm_process_instance_relation', 'target_business_key_snapshot')]:
        found = rows(q, f'SELECT {quote(col)} FROM {quote(t)} WHERE {quote(col)} IN ('
                     + ','.join(['%s'] * len(order_ids)) + ')' + suffix,
                     tuple('sales-order:' + str(i) for i in sorted(order_ids)))
        require(not found, 'Unexpected BPM process: ' + t)
    history = []
    scanned = 0
    for t, cols in meta.items():
        if '_bak_' in t or 'backup' in t or not t.startswith(('zsjos_', 'system_notify', 'bpm_')):
            continue
        if t == 'zsjos_business_audit_log':
            continue
        for col, typ in cols.items():
            if typ != 'json' and not (typ in ('text', 'mediumtext', 'longtext', 'varchar')
                                      and re.search('json|payload|snapshot|refs|params|extra', col, re.I)):
                continue
            scanned += 1
            found = rows(q, f'SELECT id,{quote(col)} v FROM {quote(t)} WHERE {quote(col)} IS NOT NULL '
                         + f"AND {quote(col)} REGEXP 'order|registration|OD202609'")
            for r in found:
                try:
                    v = json.loads(r['v'])
                except (TypeError, ValueError):
                    continue
                matches = []

                def walk(value, path='', depth=0):
                    if depth > 20:
                        raise RuntimeError('Reference nesting too deep')
                    if isinstance(value, dict):
                        for key, x in value.items():
                            p = path + '.' + key
                            group = reference_group(key, order_ids, item_ids, case_ids)
                            if isinstance(x, (str, int)) and str(x).isdigit() and int(x) in group:
                                matches.append(p)
                            if isinstance(x, str) and x in NOS:
                                matches.append(p)
                            walk(x, p, depth + 1)
                    elif isinstance(value, list):
                        for x in value:
                            walk(x, path + '[]', depth + 1)
                    elif isinstance(value, str) and value.startswith(('{', '[')):
                        try:
                            parsed = json.loads(value)
                        except ValueError:
                            return
                        walk(parsed, path, depth + 1)
                walk(v)
                if not matches:
                    continue
                # These are immutable historical mentions already reviewed, not active order ownership.
                allowed_paths = {
                    ('zsjos_business_event', 'related_object_refs'): {'.oldOrderId', '.oldOrderItemId', '.oldRegistrationCaseId'},
                    ('system_notify_business_outbox', 'payload'): {'.recipients[].context.variables.student.identifier'},
                    ('system_notify_message', 'template_params'): {'.student.identifier'},
                }.get((t, col), set())
                require(set(matches) <= allowed_paths, 'Unexpected JSON reference: ' + t + '.' + col)
                record = rows(q, f'SELECT * FROM {quote(t)} WHERE id=%s' + suffix, (r['id'],))[0]
                if t == 'system_notify_business_outbox':
                    require(record['status'] == 'succeeded', 'Order mention in unfinished notification')
                history.append({'table': t, 'column': col, 'id': r['id'], 'sha256': digest(record)})
    return {'direct_checks': direct_checks, 'json_columns': scanned, 'history': history}


def plan_build(q, meta):
    orders, queries, state = capture(q, meta)
    oid = {r['id'] for r in orders}
    chain = {ORDER: orders}
    for t in (ITEM, CASE):
        chain[t] = [r for r in state[t] if r['order_id'] in oid]
        require(len(chain[t]) == 55 and all(active(r) for r in chain[t]), 'Expected 55 live chain rows: ' + t)
        require(all(sum(r['order_id'] == o for r in chain[t]) == 1 for o in oid), 'Non-unique chain: ' + t)
    chain[SERVICE] = [r for r in state[SERVICE] if r['order_id'] in oid]
    require(tuple(sorted(r['id'] for r in chain[SERVICE])) == SERVICE_IDS, 'Expected exactly the reviewed 12 service links')
    for o in orders:
        require(active(o) and o['creator'] in BATCHES and o['status'] in ('effective', 'terminated')
                and o['total_amount'] == 0 and o['payable_amount'] == 0, 'Order evidence changed')
        require(all(o[k] is None for k in ('current_approval_round_id', 'source_payment_order_id',
                'purchase_intent_id', 'lead_id', 'opportunity_id', 'supersedes_order_id', 'superseded_by_order_id')),
                'Order has transaction/approval/successor context')
        require(all(o[k] in (None, '', '[]', 'null') for k in ('payment_voucher_refs', 'contract_refs')), 'Order has payment evidence')
        require(any(s['person_id'] == o['person_id'] and active(s) and s['status'] == 'active'
                    for s in state[SERVICE]), 'Student service missing')
    for s in chain[SERVICE]:
        o = next(o for o in orders if o['id'] == s['order_id'])
        item = next(r for r in chain[ITEM] if r['order_id'] == o['id'])
        case = next(r for r in chain[CASE] if r['order_id'] == o['id'])
        require(active(s) and s['status'] == 'active' and s['acceptance_status'] == 'accepted'
                and s['person_id'] == o['person_id'] and s['order_item_id'] == item['id']
                and s['registration_case_id'] == case['id'] and case['status'] == 'completed', 'Service chain changed')
        require(s['service_snapshot'] and json.loads(s['service_snapshot']) == json.loads(item['product_snapshot']),
                'Independent service course snapshot differs')
    now = rows(q, 'SELECT NOW() now')[0]['now']
    mutations = []
    for t in (SERVICE, CASE, ITEM, ORDER):
        for before in chain[t]:
            after = copy.deepcopy(before)
            if t == SERVICE:
                after.update(order_id=None, order_item_id=None, registration_case_id=None)
            else:
                after['deleted'] = b'\x01'
            if 'version' in after:
                after['version'] = (after['version'] or 0) + 1
            after.update(updater=MARKER, update_time=now)
            mutations.append({'table': t, 'before': before, 'after': after})
    require(len(mutations) == 177, 'Expected exactly 177 row updates')
    return {'marker': MARKER, 'tenant': 1, 'orders': list(NOS), 'created_at': now,
            'mutations': mutations, 'queries': queries, 'protected': state, 'runtime': runtime()}


def protect(q, plan, after=False, lock=False):
    expected = copy.deepcopy(plan['protected'])
    if after:
        for m in plan['mutations']:
            expected[m['table']] = [m['after'] if r['id'] == m['before']['id'] else r for r in expected[m['table']]]
    for t, sql in plan['queries'].items():
        actual = rows(q, sql + (' FOR UPDATE' if lock else ''))
        require(actual == expected[t], 'Protected data drift: ' + t)


def image_state(q, plan, mapping=None):
    mapping = mapping or {t: t for t in TABLES}
    states = []
    for m in plan['mutations']:
        found = rows(q, f'SELECT * FROM {mapping[m["table"]]} WHERE tenant_id=1 AND id=%s FOR UPDATE', (m['before']['id'],))
        require(len(found) == 1, 'Target missing')
        states.append('before' if found[0] == m['before'] else 'after' if found[0] == m['after'] else 'drift')
    require(len(set(states)) == 1 and 'drift' not in states, 'Partial or changed state; refuse automatic recovery')
    return states[0]


def fixture(c, q, plan):
    mapping = {t: 'tmp_ph55_' + str(i) for i, t in enumerate(TABLES)}
    for t, temp in mapping.items():
        q.execute(f'CREATE TEMPORARY TABLE {temp} LIKE {t}')
        cols = [r['COLUMN_NAME'] for r in rows(q, 'SELECT COLUMN_NAME FROM information_schema.columns '
                'WHERE table_schema=DATABASE() AND table_name=%s AND generation_expression=%s ORDER BY ordinal_position', (t, ''))]
        fields = ','.join(quote(col) for col in cols)
        targets = {m['before']['id'] for m in plan['mutations'] if m['table'] == t}
        q.execute(f'INSERT INTO {temp} ({fields}) SELECT {fields} FROM {t} WHERE id IN ({ids(targets)})')
    c.commit()
    for fail_at in (1, 12, 13, 67, 122, 177):
        try:
            update_images(q, plan, mapping, fail_after=fail_at)
            raise AssertionError('Failure injection not reached')
        except RuntimeError as exc:
            require(str(exc) == 'injected_failure', 'Unexpected fixture error')
            c.rollback()
        verify_images(q, plan, mapping, restored=True)
    require(image_state(q, plan, mapping) == 'before', 'Fixture initial state')
    require(update_images(q, plan, mapping) == 177, 'Fixture update count')
    verify_images(q, plan, mapping)
    require(image_state(q, plan, mapping) == 'after' and update_images(q, plan, mapping) == 0, 'Fixture replay')
    restore_images(q, plan, mapping)
    verify_images(q, plan, mapping, restored=True)
    c.commit()
    update_images(q, plan, mapping, limit=13)
    try:
        image_state(q, plan, mapping)
        raise AssertionError('Partial state accepted')
    except RuntimeError as exc:
        require('Partial' in str(exc), 'Unexpected partial-state error')
    c.rollback()
    q.execute(f'UPDATE {mapping[SERVICE]} SET version=version+1 WHERE id=898')
    try:
        image_state(q, plan, mapping)
        raise AssertionError('Drift accepted')
    except RuntimeError as exc:
        require('changed' in str(exc), 'Unexpected drift error')
    c.rollback()
    verify_images(q, plan, mapping, restored=True)
    c.rollback()
    return ['initial-177', 'replay-0', 'six-failure-rollbacks', 'exact-restore', 'partial-refused', 'drift-refused']


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('mode', choices=('prepare', 'fixture', 'preview', 'apply', 'verify'))
    p.add_argument('--sha256')
    args = p.parse_args()
    env = os.environ.get('ZSJOS_AGENT_ENV')
    if env is None:
        f = Path('/etc/zsjos/agent-environment')
        env = f.read_text().strip() if f.exists() else 'local'
    require(env == 'test', 'Authorization is test-only')
    c = connect('zsjos-mysql-1')
    try:
        with c.cursor() as q:
            q.execute('SET NAMES utf8mb4')
            q.execute("SET time_zone='+08:00'")
            q.execute('SET SESSION innodb_lock_wait_timeout=5')
            q.execute('SET SESSION MAX_EXECUTION_TIME=20000')
            check_schema(q)
            meta = metadata(q)
            c.rollback()
            if args.mode == 'prepare':
                EVIDENCE.mkdir(mode=0o700, exist_ok=False)
                q.execute('START TRANSACTION WITH CONSISTENT SNAPSHOT, READ ONLY')
                plan = plan_build(q, meta)
                plan['references'] = guards(q, meta, plan)
                c.rollback()
                sha = private_write(EVIDENCE / 'plan.json', plan)
                print(json.dumps({'orders': 55, 'updates': 177, 'sha256': sha,
                                  'protected_tables': len(plan['queries'])}))
                return
            require(EVIDENCE.stat().st_mode & 0o077 == 0, 'Private evidence directory permissions')
            raw = (EVIDENCE / 'plan.json').read_bytes()
            require(hashlib.sha256(raw).hexdigest() == args.sha256, 'Exact reviewed SHA required')
            plan = decoded(raw.decode('utf-8'))
            require(plan['marker'] == MARKER and plan['tenant'] == 1 and plan['orders'] == list(NOS), 'Plan scope mismatch')
            require(runtime() == plan['runtime'], 'Application changed since preparation')
            if args.mode == 'fixture':
                result = {'sha256': args.sha256, 'checks': fixture(c, q, plan)}
            else:
                if args.mode in ('preview', 'apply'):
                    require(decoded((EVIDENCE / 'fixture.json').read_text())['sha256'] == args.sha256, 'Fixture evidence required')
                if args.mode == 'apply':
                    require(decoded((EVIDENCE / 'preview.json').read_text())['sha256'] == args.sha256, 'Preview evidence required')
                require(rows(q, "SELECT GET_LOCK('placeholder-cleanup-55-tenant1',5) ok")[0]['ok'] == 1, 'Cleanup lock unavailable')
                c.rollback()
                q.execute('SET TRANSACTION ISOLATION LEVEL SERIALIZABLE')
                q.execute('START TRANSACTION')
                state = image_state(q, plan)
                if args.mode == 'verify':
                    require(state == 'after', 'Cleanup not applied')
                protect(q, plan, after=state == 'after', lock=True)
                require(guards(q, meta, plan, lock=True) == plan['references'], 'Reference evidence changed')
                changed = 0
                if args.mode in ('preview', 'apply'):
                    changed = update_images(q, plan)
                verify_images(q, plan)
                protect(q, plan, after=True)
                require(runtime() == plan['runtime'], 'Application changed during operation')
                if args.mode == 'apply':
                    c.commit()
                else:
                    c.rollback()
                if args.mode == 'preview':
                    verify_images(q, plan, restored=True)
                    protect(q, plan)
                else:
                    verify_images(q, plan)
                    protect(q, plan, after=True)
                c.rollback()
                result = {'sha256': args.sha256, 'mode': args.mode, 'changed': changed,
                          'orders': 55, 'services_detached': 12, 'images_verified': 177,
                          'protected_tables': len(plan['queries']), 'checked_at': dt.datetime.now()}
            filename = args.mode + (('-' + dt.datetime.now().strftime('%H%M%S%f')) if args.mode in ('apply', 'verify') else '') + '.json'
            private_write(EVIDENCE / filename, result)
            print(encoded(result))
    except Exception:
        c.rollback()
        raise
    finally:
        c.close()


if __name__ == '__main__':
    main()
