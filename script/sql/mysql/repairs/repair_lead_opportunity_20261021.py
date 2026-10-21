#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Confirmed single-Lead repair; see lead-opportunity-repair-20261021.md."""
import argparse
import hashlib
import json
import os
from pathlib import Path

from transfer_student_20261015 import connect

LEAD_NO = 'LD202609190146'
MARKER = 'lead-opportunity-repair-20261021'
TABLE = 'zsjos_opportunity'


def require(condition, message):
    if not condition:
        raise RuntimeError(message)


def encoded(value):
    return json.dumps(value, ensure_ascii=False, sort_keys=True,
                      default=lambda x: x.hex() if isinstance(x, bytes) else str(x))


def digest(value):
    return hashlib.sha256(encoded(value).encode('utf-8')).hexdigest()


def rows(cur, sql, args=()):
    cur.execute(sql, args)
    return list(cur.fetchall())


def capture(cur, lock=False):
    suffix = ' FOR UPDATE' if lock else ''
    leads = rows(cur, 'SELECT * FROM zsjos_lead WHERE tenant_id=1 AND lead_no=%s AND deleted=0' + suffix, (LEAD_NO,))
    require(len(leads) == 1, 'Expected exactly one target Lead')
    lead = leads[0]
    person = rows(cur, 'SELECT id FROM zsjos_person WHERE tenant_id=1 AND id=%s AND deleted=0' + suffix,
                  (lead['person_id'],))
    orders = rows(cur, 'SELECT * FROM zsjos_order WHERE tenant_id=1 AND person_id=%s ORDER BY id' + suffix,
                  (lead['person_id'],))
    drafts = rows(cur, 'SELECT * FROM zsjos_purchase_intent WHERE tenant_id=1 AND lead_id=%s ORDER BY id' + suffix,
                  (lead['id'],))
    products = rows(cur, 'SELECT * FROM zsjos_lead_intended_product WHERE tenant_id=1 AND lead_id=%s AND deleted=0 ORDER BY id' + suffix,
                    (lead['id'],))
    blockers = {}
    for table in ('zsjos_lead_appeal', 'zsjos_lead_public_sea_pool_cycle', 'zsjos_lead_public_sea_pool_compatibility_record'):
        blockers[table] = rows(cur, f'SELECT id,status FROM {table} WHERE tenant_id=1 AND lead_id=%s AND deleted=0' + suffix,
                               (lead['id'],)) if table != 'zsjos_lead_public_sea_pool_compatibility_record' else rows(
            cur, f'SELECT id FROM {table} WHERE tenant_id=1 AND lead_id=%s AND deleted=0' + suffix, (lead['id'],))
    return dict(lead=lead, person=person, orders=orders, drafts=drafts, products=products, blockers=blockers)


def validate(state):
    lead = state['lead']
    require(lead['tenant_id'] == 1 and lead['lead_no'] == LEAD_NO and lead['id'] == 6295,
            'Target identity changed')
    require(lead['status'] == 'valid' and lead['assignment_status'] == 'owned'
            and lead['suspended_at'] is None and lead['owner_user_id'] is not None,
            'Lead no longer eligible')
    require(len(state['person']) == 1, 'Active person missing')
    require(all(r['status'] == 'terminated' for r in state['orders'] if r['deleted'] in (0, b'\0')),
            'Conflicting order exists')
    require(not any(state['blockers'].values()), 'Appeal or public-sea history requires new review')
    require(len(state['products']) == 1, 'Intended-product scope changed')


def expected(state):
    validate(state)
    lead = state['lead']
    summary = [dict(spuRef=p['spu_ref'] or '', spuName=p['spu_name_snapshot'] or '',
                    skuRef=p['sku_ref'] or '', skuName=p['sku_name_snapshot'] or '',
                    primary=p['is_primary'] in (True, 1, b'\1')) for p in state['products']]
    return dict(tenant_id=1, person_id=lead['person_id'], lead_id=lead['id'],
                owner_user_id=lead['owner_user_id'], type='initial_conversion', status='open',
                version=0, creator=MARKER, updater=MARKER, expected_product_summary=summary)


def related(cur, want, table=TABLE):
    return rows(cur, f'SELECT * FROM {table} WHERE tenant_id=1 AND (lead_id=%s OR person_id=%s) ORDER BY id FOR UPDATE',
                (want['lead_id'], want['person_id']))


def verify(cur, want, table=TABLE):
    found = related(cur, want, table)
    require(len(found) == 1, 'Expected exactly one opportunity')
    row = found[0]
    for key, value in want.items():
        actual = json.loads(row[key]) if key == 'expected_product_summary' else row[key]
        require(actual == value, 'Opportunity differs from repair image; refusing overwrite')
    require(row['deleted'] in (0, b'\0') and all(row[k] is None for k in (
        'source_service_relation_id', 'previous_order_id', 'next_follow_up_at', 'shelved_until',
        'shelved_reason', 'won_at', 'lost_at', 'lost_reason')), 'Unexpected opportunity state')
    for i, product in enumerate(want['expected_product_summary']):
        for field in ('spuName', 'skuName'):
            result = rows(cur, f'SELECT HEX(JSON_UNQUOTE(JSON_EXTRACT(expected_product_summary,%s))) AS h FROM {table} WHERE id=%s AND tenant_id=1',
                          (f'$[{i}].{field}', row['id']))
            require(result[0]['h'] == product[field].encode('utf-8').hex().upper(), 'UTF-8 snapshot mismatch')
    return row


def insert(cur, want, table=TABLE, fail=False):
    if related(cur, want, table):
        verify(cur, want, table)
        return 0
    columns = list(want)
    values = [encoded(want[k]) if k == 'expected_product_summary' else want[k] for k in columns]
    cur.execute(f"INSERT INTO {table} ({','.join(columns)}) VALUES ({','.join(['%s'] * len(columns))})", values)
    require(cur.rowcount == 1, 'Expected one insertion')
    if fail:
        raise RuntimeError('Injected failure')
    verify(cur, want, table)
    return 1


def controlled(conn, cur):
    table = 'verify_lead_opportunity_20261021'
    cur.execute(f'CREATE TEMPORARY TABLE {table} LIKE {TABLE}')
    conn.commit()
    want = dict(tenant_id=1, person_id=1, lead_id=1, owner_user_id=1,
                type='initial_conversion', status='open', version=0, creator=MARKER, updater=MARKER,
                expected_product_summary=[dict(spuRef='fixture', spuName='验证课程', skuRef='sku', skuName='验证规格', primary=True)])
    try:
        insert(cur, want, table, fail=True)
    except RuntimeError as error:
        require(str(error) == 'Injected failure', 'Unexpected controlled failure')
        conn.rollback()
    else:
        raise RuntimeError('Failure injection did not fire')
    require(not related(cur, want, table), 'Failed insertion was not rolled back')
    require(insert(cur, want, table) == 1, 'Recovery/initial application failed')
    conn.commit()
    before = verify(cur, want, table)
    require(insert(cur, want, table) == 0 and verify(cur, want, table) == before, 'Repeat changed data')
    conn.rollback()
    cur.execute(f"UPDATE {table} SET status='following' WHERE id=%s", (before['id'],))
    try:
        insert(cur, want, table)
    except RuntimeError as error:
        require('refusing overwrite' in str(error), 'Wrong stale-state error')
        conn.rollback()
    else:
        raise RuntimeError('Changed opportunity was overwritten')
    conflicting = dict(want, lead_id=2)
    try:
        insert(cur, conflicting, table)
    except RuntimeError as error:
        require('refusing overwrite' in str(error), 'Wrong conflict error')
        conn.rollback()
    else:
        raise RuntimeError('Conflicting customer opportunity accepted')
    require(verify(cur, want, table) == before, 'Controlled recovery changed committed row')
    conn.rollback()
    # Temporary table is session-owned and disappears on disconnect; no shared DDL.


def evidence(state):
    lead = state['lead']
    return dict(leadNo=LEAD_NO, tenantId=1, status=lead['status'],
                assignmentStatus=lead['assignment_status'], version=lead['version'],
                fingerprints={key: digest(value) for key, value in state.items()},
                relatedOrderCount=len(state['orders']), draftCount=len(state['drafts']),
                productCount=len(state['products']))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apply', action='store_true')
    parser.add_argument('--evidence-dir', required=True, type=Path)
    args = parser.parse_args()
    env = os.getenv('ZSJOS_AGENT_ENV')
    marker = Path('/etc/zsjos/agent-environment')
    if env is None:
        env = marker.read_text().strip() if marker.exists() else 'local'
    require(env == 'test', 'Only confirmed test environment supported')
    os.umask(0o077)
    args.evidence_dir.mkdir(mode=0o700, parents=True, exist_ok=False)

    def persist(name, value):
        with (args.evidence_dir / name).open('x', encoding='utf-8') as f:
            f.write(encoded(value) + '\n')
            f.flush()
            os.fsync(f.fileno())

    conn = connect('zsjos-mysql-1')
    try:
        with conn.cursor() as cur:
            cur.execute('SET NAMES utf8mb4')
            cur.execute('SET SESSION innodb_lock_wait_timeout=5')
            require(rows(cur, 'SELECT VERSION() AS v')[0]['v'] == '8.4.11', 'Reverify changed MySQL version')
            conn.rollback()
            state = capture(cur)
            want = expected(state)
            existing = related(cur, want)
            if existing:
                verify(cur, want)
            conn.rollback()
            persist('before.json', evidence(state))
            persist('opportunity-before.json', existing)
            persist('expected.json', want)
            controlled(conn, cur)
            persist('controlled.json', dict(initial=True, repeat=True, failureRollback=True,
                                            recovery=True, staleRefusal=True, customerConflict=True, utf8Hex=True))
            current = capture(cur, lock=args.apply)
            require(evidence(current) == evidence(state), 'Prerequisite changed since capture')
            require(related(cur, want) == existing, 'Opportunity changed since capture')
            if not args.apply:
                conn.rollback()
                persist('result.json', dict(applied=False, inserted=0, controlled='passed'))
                print('PASS controlled MySQL checks; live preflight read-only')
                return
            n = insert(cur, expected(current))
            row = verify(cur, want)
            require(evidence(capture(cur)) == evidence(state), 'Related records changed')
            persist('pending.json', dict(inserted=n, opportunity=row))
            conn.commit()
            persist('committed.json', dict(inserted=n, opportunityId=row['id']))
            require(evidence(capture(cur, lock=True)) == evidence(state), 'Related records changed after commit')
            require(verify(cur, want) == row and insert(cur, want) == 0, 'Committed readback/repeat failed')
            conn.rollback()
            persist('result.json', dict(applied=True, inserted=n, relatedRecordsUnchanged=True,
                                       committedReadback=True, repeatWrites=0, utf8Hex=True))
            print(f'PASS committed {n} opportunity; related records unchanged; repeat zero writes; UTF-8 HEX verified')
    finally:
        conn.rollback()
        conn.close()


if __name__ == '__main__':
    main()
