#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Authorized test-only service merge; see student-service-merge-20261029.md."""
import argparse
import copy
import hashlib
import json
import os
from pathlib import Path

from close_legacy_registration_20261028 import encoded, read, save, require, rows
from transfer_student_20261015 import connect

ROOT = Path('/opt/zsjos-runtime/backups/student-service-merge-20261029')
SERVICE = 'zsjos_service_relation'
EVENT = 'zsjos_business_event'
MARKER = 'student-service-merge-20261029'
REASON = '按用户确认保留旧服务及历史教学资料，承接最新订单报名和分班关联，物理删除重复新服务'
KEEP, SOURCE = 1635, 2019
LINKS = dict(order_id=8631, order_item_id=9144, registration_case_id=1127,
             class_id=79, owner_user_id=27)
REFS = json.dumps(dict(retainedServiceId=KEEP, removedServiceId=SOURCE,
                       orderId=8631, orderItemId=9144, registrationCaseId=1127))


def discover(q):
    meta = {}
    for r in rows(q, "SELECT TABLE_NAME,COLUMN_NAME,DATA_TYPE FROM information_schema.columns "
                  "WHERE table_schema=DATABASE() AND table_name LIKE 'zsjos%'"):
        if '_bak_' not in r['TABLE_NAME']:
            meta.setdefault(r['TABLE_NAME'], {})[r['COLUMN_NAME']] = r['DATA_TYPE']
    keys = dict(person_id='7780', student_person_id='7780', service_relation_id='1527,1635,2019',
                source_service_relation_id='1527,1635,2019', create_service_relation_id='1527,1635,2019',
                order_id='8631', order_item_id='9143,9144', registration_case_id='839,1127',
                collaboration_group_id='1364,1472', lead_id='6278')
    special = {'zsjos_person': '7780', 'zsjos_delivery_class': '78,79',
               'zsjos_collaboration_group': '1364,1472'}
    queries = {}
    for t, cols in sorted(meta.items()):
        if 'tenant_id' not in cols or 'id' not in cols:
            continue
        terms = [f'`{k}` IN ({v})' for k, v in keys.items() if k in cols]
        if t in special:
            terms.append(f'id IN ({special[t]})')
        for typ, key in [('biz_type', 'biz_id'), ('business_type', 'business_id'),
                         ('aggregate_type', 'aggregate_id')]:
            if typ in cols and key in cols:
                terms.append(f"(`{typ}` IN ('student_service','student-service') AND `{key}` IN (1527,1635,2019))")
        # JSON references must not bypass the direct-column reference inventory.
        pattern = '\"(serviceRelationId|service_relation_id|sourceServiceRelationId|createServiceRelationId)\"[[:space:]]*:[[:space:]]*\"?2019([^0-9]|$)'
        for k, typ in cols.items():
            if typ in ('json', 'text', 'mediumtext', 'longtext'):
                terms.append(f"REGEXP_LIKE(CAST(`{k}` AS CHAR), '{pattern}')")
        if t == EVENT:
            terms.append(f"idempotency_key='{MARKER}'")
        if terms:
            # Token material is unrelated to this repair and is not backed up.
            selected = ','.join(f'`{k}`' for k in cols if k not in ('token_hash', 'token_ciphertext'))
            queries[t] = f'SELECT {selected} FROM `{t}` WHERE tenant_id=1 AND (' + ' OR '.join(terms) + ') ORDER BY id'
    queries['system_notify_business_outbox'] = "SELECT id,biz_type,biz_id,status FROM system_notify_business_outbox WHERE tenant_id=1 AND biz_type='student_service' AND biz_id=2019 ORDER BY id"
    queries['system_notify_message'] = "SELECT id,biz_type,biz_id,source_event_key FROM system_notify_message WHERE tenant_id=1 AND biz_type='student_service' AND biz_id=2019 ORDER BY id"
    return queries


def capture(q, queries, lock=False):
    if lock:
        # Match application order -> registration -> person/service locking.
        for t, cond in [('zsjos_order', 'id=8631'), ('zsjos_registration_case', 'id IN (839,1127)'),
                        ('zsjos_person', 'id=7780'), (SERVICE, 'person_id=7780')]:
            rows(q, f'SELECT id FROM `{t}` WHERE tenant_id=1 AND {cond} ORDER BY id FOR UPDATE')
    result = {t: rows(q, sql) for t, sql in queries.items()}
    if lock:
        # JSON inventory scans must not lock unrelated business rows. Lock the discovered
        # primary keys and compare current reads to the transaction's frozen snapshot.
        for t, data in result.items():
            if not data or not t.startswith('zsjos_'): continue
            select = queries[t].split(' WHERE ', 1)[0]
            locked = rows(q, select + ' WHERE tenant_id=1 AND id IN ('
                          + ','.join(str(r['id']) for r in data) + ') ORDER BY id FOR UPDATE')
            require(locked == data, 'Concurrent row update: ' + t)
    return result


def validate(q, state):
    services = {r['id']: r for r in state[SERVICE]}
    require(set(services) == {1527, KEEP, SOURCE}, 'Service set changed')
    old, new = services[KEEP], services[SOURCE]
    require(all(r['tenant_id'] == 1 and r['person_id'] == 7780 and r['deleted'] == b'\0'
                and r['status'] == 'active' for r in services.values()), 'Identity/state changed')
    require(old['student_name_snapshot'] == new['student_name_snapshot'], 'Identity snapshot changed')
    require(all(old[k] is None for k in ('order_id', 'order_item_id', 'registration_case_id')), 'Old service already linked')
    require(all(new[k] == v for k, v in LINKS.items()), 'New assignment changed')
    require(old['class_id'] == 79 and old['owner_user_id'] == 27
            and old['acceptance_status'] == 'accepted' and old['delivery_stage'] == 'supervision'
            and old['version'] == 1 and old['collaboration_group_id'] == 1472, 'Historical service changed')
    require(new['acceptance_status'] == 'pending' and new['version'] == 0
            and new['delivery_stage'] is None and new['collaboration_group_id'] is None
            and new['first_contact_progress_json'] is None, 'New service has progressed')
    require(json.loads(old['service_snapshot'])['productRef'] == 'edu2026_23'
            and json.loads(old['service_snapshot'])['name'] == json.loads(new['service_snapshot'])['name'], 'Course changed')
    for t, data in state.items():
        if t in (SERVICE, 'system_notify_business_outbox', 'system_notify_message'):
            continue
        for r in data:
            require(not any(v == SOURCE for k, v in r.items() if 'service' in k and k.endswith('_id')),
                    'Source has dependent reference: ' + t)
            for typ, key in [('biz_type','biz_id'), ('business_type','business_id'), ('aggregate_type','aggregate_id')]:
                require(not (r.get(typ) in ('student-service','student_service') and str(r.get(key)) == str(SOURCE)),
                        'Source has typed reference: ' + t)
            def source_ref(value):
                if isinstance(value, dict):
                    return any((k in ('serviceRelationId','service_relation_id','sourceServiceRelationId','createServiceRelationId')
                                and str(v) == str(SOURCE)) or source_ref(v) for k, v in value.items())
                if isinstance(value, list): return any(source_ref(v) for v in value)
                return False
            for v in r.values():
                if isinstance(v, str) and v[:1] in ('{','['):
                    try: obj = json.loads(v)
                    except ValueError: continue
                    require(not source_ref(obj), 'Source has JSON reference: ' + t)
    require(all(r['status'] == 'succeeded' for r in state['system_notify_business_outbox']), 'Notification still in flight')
    order = next(r for r in state['zsjos_order'] if r['id'] == 8631)
    require(order['status'] == 'effective' and order['person_id'] == 7780 and order['deleted'] == b'\0', 'Order changed')
    item = next(r for r in state['zsjos_order_item'] if r['id'] == 9144)
    require(item['order_id'] == 8631 and item['deleted'] == b'\0' and item['product_snapshot'] == new['service_snapshot'], 'Order item changed')
    case = next(r for r in state['zsjos_registration_case'] if r['id'] == 1127)
    require(case['order_id'] == 8631 and case['order_item_id'] == 9144 and case['status'] == 'processing', 'Case changed')
    assignments = state['zsjos_registration_class_assignment']
    require(any(r['id'] == 202 and r['class_id'] == 79 and r['homeroom_user_id'] == 27 for r in assignments), 'Assignment changed')
    require(not rows(q, "SELECT TRIGGER_NAME FROM information_schema.triggers WHERE trigger_schema=DATABASE() AND event_object_table IN (%s,%s)", (SERVICE, EVENT)), 'Unexpected trigger')
    require(not rows(q, "SELECT TABLE_NAME FROM information_schema.key_column_usage WHERE referenced_table_schema=DATABASE() AND referenced_table_name=%s", (SERVICE,)), 'Unexpected foreign key')
    engines = rows(q, "SELECT ENGINE FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN (%s,%s)", (SERVICE, EVENT))
    require(len(engines) == 2 and all(r['ENGINE'] == 'InnoDB' for r in engines), 'Transactional engine required')


def mutate(q, prefix='', fail_after=None):
    def checkpoint(n):
        require(q.rowcount == 1, 'Expected exactly one affected row')
        if fail_after == n: raise RuntimeError('Injected failure')
    # Delete first to release the unique tenant/order-item key; all writes share one transaction.
    q.execute(f'DELETE FROM `{prefix}{SERVICE}` WHERE tenant_id=1 AND id=2019 AND person_id=7780 AND version=0')
    checkpoint(1)
    q.execute(f'UPDATE `{prefix}{SERVICE}` SET order_id=8631,order_item_id=9144,registration_case_id=1127,'
              'class_id=79,owner_user_id=27,version=version+1,updater=%s,update_time=NOW() '
              'WHERE tenant_id=1 AND id=1635 AND person_id=7780 AND version=1', (MARKER,))
    checkpoint(2)
    q.execute(f'INSERT INTO `{prefix}{EVENT}` (event_type,aggregate_type,aggregate_id,operator_user_id,reason,'
              'related_object_refs,occurred_at,idempotency_key,creator,updater,tenant_id) '
              "VALUES ('system_data_repair','student_service',1635,NULL,%s,%s,NOW(),%s,%s,%s,1)",
              (REASON, REFS, MARKER, MARKER, MARKER))
    checkpoint(3)


def compare(before, after):
    require(before.keys() == after.keys(), 'Captured table set changed')
    for t, original in before.items():
        actual = {r['id']: r for r in after[t]}
        expected_ids = {r['id'] for r in original}
        if t == SERVICE: expected_ids.remove(SOURCE)
        if t == EVENT:
            added = [r for r in after[t] if r['id'] not in expected_ids]
            require(len(added) == 1, 'Expected one repair event')
            e = added[0]
            require(e['idempotency_key'] == MARKER and e['event_type'] == 'system_data_repair'
                    and e['aggregate_type'] == 'student_service' and e['aggregate_id'] == KEEP
                    and e['reason'] == REASON and json.loads(e['related_object_refs']) == json.loads(REFS)
                    and e['operator_user_id'] is None and e['tenant_id'] == 1, 'Repair event mismatch')
            expected_ids.add(e['id'])
        require(set(actual) == expected_ids, 'Unexpected row addition/deletion: ' + t)
        for r in original:
            if t == SERVICE and r['id'] == SOURCE: continue
            want, got = copy.deepcopy(r), actual[r['id']]
            if t == SERVICE and r['id'] == KEEP:
                want.update(LINKS, version=r['version'] + 1, updater=MARKER, update_time=got['update_time'])
            require(want == got, 'Unexpected field changes: ' + t)


def apply_once(q, before, current, prefix=''):
    if not any(r['id'] == SOURCE for r in current[SERVICE]):
        compare(before, current)
        return False
    require(current == before, 'Approved before-image changed; review required')
    mutate(q, prefix)
    return True


def rehearse(c, before):
    tables = (SERVICE, EVENT)
    with c.cursor() as q:
        for t in tables:
            q.execute(f'CREATE TEMPORARY TABLE `merge_test_{t}` LIKE `{t}`')
            cols = [r['Field'] for r in rows(q, f'SHOW COLUMNS FROM `{t}`')
                    if not any(x in r['Extra'] for x in ('VIRTUAL GENERATED', 'STORED GENERATED'))]
            if before[t]:
                q.executemany(f'INSERT INTO `merge_test_{t}` (' + ','.join(f'`{k}`' for k in cols)
                              + ') VALUES (' + ','.join(['%s'] * len(cols)) + ')',
                              [tuple(r[k] for k in cols) for r in before[t]])
    c.commit()  # Only connection-local fixtures have been written.
    def state(q):
        return dict(before, **{t: rows(q, f'SELECT * FROM `merge_test_{t}` ORDER BY id') for t in tables})
    with c.cursor() as q: require(state(q) == before, 'Fixture mismatch')
    c.rollback()
    for fail in (1, 2, 3):
        with c.cursor() as q:
            try: mutate(q, 'merge_test_', fail)
            except RuntimeError as e:
                require(str(e) == 'Injected failure', 'Unexpected fixture failure')
            else: raise RuntimeError('Failure injection did not execute')
        c.rollback()
        with c.cursor() as q: require(state(q) == before, 'Failure rollback lost data')
        c.rollback()
    with c.cursor() as q:
        require(apply_once(q, before, state(q), 'merge_test_'), 'First apply skipped')
        compare(before, state(q))
    c.commit()
    with c.cursor() as q:
        after = state(q)
        require(not apply_once(q, before, after, 'merge_test_') and state(q) == after, 'Repeat changed data')
        # Verify compensating restoration in private fixtures only, including original IDs/indexes.
        old = next(r for r in before[SERVICE] if r['id'] == KEEP)
        cols = [k for k in old if k != 'id']
        q.execute(f'UPDATE `merge_test_{SERVICE}` SET ' + ','.join(f'`{k}`=%s' for k in cols)
                  + ' WHERE id=1635 AND tenant_id=1', tuple(old[k] for k in cols))
        source = next(r for r in before[SERVICE] if r['id'] == SOURCE)
        q.execute(f'INSERT INTO `merge_test_{SERVICE}` (' + ','.join(f'`{k}`' for k in source)
                  + ') VALUES (' + ','.join(['%s'] * len(source)) + ')', tuple(source.values()))
        require(state(q)[SERVICE] == before[SERVICE], 'Compensation failed')
    c.rollback()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('mode', choices=['prepare', 'rehearse', 'apply', 'verify'])
    parser.add_argument('--sha256')
    a = parser.parse_args()
    environment = os.environ.get('ZSJOS_AGENT_ENV')
    if environment is None: environment = Path('/etc/zsjos/agent-environment').read_text().strip()
    require(environment == 'test', 'Only authorized test environment supported')
    os.umask(0o077)
    ROOT.mkdir(parents=True, exist_ok=True, mode=0o700)
    require(ROOT.stat().st_mode & 0o077 == 0, 'Evidence directory must be private')
    plan_path = ROOT / 'plan.json'
    c = connect('zsjos-mysql-1')
    try:
        with c.cursor() as q:
            q.execute('SET NAMES utf8mb4'); q.execute("SET time_zone='+08:00'")
            q.execute('SET SESSION innodb_lock_wait_timeout=10')
            queries = discover(q)
            c.rollback()
            if a.mode != 'apply': q.execute('SET TRANSACTION READ ONLY')
            q.execute('START TRANSACTION WITH CONSISTENT SNAPSHOT')
            current = capture(q, queries, a.mode == 'apply')
            if a.mode == 'prepare':
                validate(q, current)
                save(plan_path, dict(queries=queries, before=current))
                print(json.dumps(dict(prepared=True, sha256=hashlib.sha256(plan_path.read_bytes()).hexdigest(),
                                      tables=len(current), protected_rows=sum(map(len, current.values())))))
                return
            require(a.sha256 == hashlib.sha256(plan_path.read_bytes()).hexdigest(), 'Plan hash required')
            plan = read(plan_path); before = plan['before']
            require(queries == plan['queries'], 'Schema/reference inventory changed')
            if a.mode == 'rehearse':
                require(current == before, 'Before-image changed'); validate(q, current); c.rollback()
                rehearse(c, before)
                save(ROOT / 'rehearsal.json', dict(passed=True, sha256=a.sha256, failure_points=3,
                     recovery=True, repeat=True, compensation=True))
            elif a.mode == 'apply':
                evidence = read(ROOT / 'rehearsal.json')
                require(evidence['passed'] and evidence['sha256'] == a.sha256, 'Rehearsal required')
                exists = any(r['id'] == SOURCE for r in current[SERVICE])
                if exists:
                    validate(q, current)
                    save(ROOT / 'locked-before.json', current)
                changed = apply_once(q, before, current)
                after = capture(q, queries)
                compare(before, after)
                if changed: save(ROOT / 'after.json', after)
                c.commit()
                print(json.dumps(dict(applied=changed, deleted_rows=int(changed), updated_rows=int(changed),
                                      audit_rows=int(changed))))
            else:
                compare(before, current)
                require(current == read(ROOT / 'after.json'), 'Independent after-image mismatch')
                checks = rows(q, 'SELECT student_name_snapshot,HEX(student_name_snapshot) name_hex,'
                              'service_snapshot,HEX(service_snapshot) snapshot_hex FROM zsjos_service_relation WHERE tenant_id=1 AND id=1635')
                require(len(checks) == 1, 'Retained service missing')
                r = checks[0]
                require(r['student_name_snapshot'].encode('utf-8').hex().upper() == r['name_hex']
                        and r['service_snapshot'].encode('utf-8').hex().upper() == r['snapshot_hex'], 'UTF-8 mismatch')
                label = rows(q, 'SELECT reason,HEX(reason) hex_value FROM zsjos_business_event WHERE tenant_id=1 AND idempotency_key=%s', (MARKER,))
                require(len(label) == 1 and label[0]['hex_value'] == REASON.encode('utf-8').hex().upper(), 'Repair label encoding mismatch')
                require(rows(q, 'SELECT id FROM zsjos_service_relation WHERE tenant_id=1 AND order_item_id=9144') == [{'id': KEEP}], 'Order-item uniqueness failed')
                save(ROOT / 'verification.json', dict(passed=True, sha256=a.sha256, utf8_hex=True,
                     deleted_source_absent=True, preserved_histories=True, protected_tables=len(current)))
        if a.mode != 'apply': print(json.dumps(dict(mode=a.mode, verified=True)))
    finally:
        c.rollback(); c.close()


if __name__ == '__main__': main()
