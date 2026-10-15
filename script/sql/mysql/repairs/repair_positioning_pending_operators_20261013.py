#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Authorized four-card pending operator repair; no live approval decisions."""
import argparse
import hashlib
import os
from pathlib import Path

from repair_content_directors_20261013 import connect, rows, require, encoded, persist, active

MARKER = 'positioning-operator-repair-20261013'
REASON = '经确认交接尚未完成的定位卡运营复核绑定，保留提交编导、历史版本及审核结果'
CARD = 'zsjos_positioning_card'
SUB = 'zsjos_positioning_card_submission'
SERVICE = 'zsjos_service_relation'
EVENT = 'zsjos_business_event'
TARGETS = {37: (57, 525, 45), 39: (44, 50, 522), 44: (49, 50, 522), 46: (51, 50, 522)}
TABLES = (SERVICE, CARD, SUB, EVENT)


def capture(cur, lock=False):
    conditions = {
        SERVICE: 'id IN (1003,18,20,16)',
        CARD: 'id IN (37,39,44,46)',
        SUB: '1=1',
        EVENT: f"idempotency_key LIKE '{MARKER}:%'",
    }
    return {table: rows(cur, f'SELECT * FROM {table} WHERE tenant_id=1 AND {conditions[table]} ORDER BY id'
                        + (' FOR UPDATE' if lock else '')) for table in TABLES}


def validate(state, repaired=False):
    services = {r['id']: r for r in state[SERVICE]}
    require(len(state[CARD]) == 4, 'Missing authorized card')
    for card in state[CARD]:
        sub_id, old, new = TARGETS[card['id']]
        service = services[card['service_relation_id']]
        versions = [r for r in state[SUB] if r['card_id'] == card['id'] and active(r)]
        latest = max(versions, key=lambda r: r['id'])
        require(active(card) and active(service) and service['status'] == 'active'
                and service['acceptance_status'] == 'accepted', 'Inactive card/service')
        require(card['operator_user_id'] == service['operator_user_id'] == new
                and card['student_person_id'] == service['person_id'], 'Current ownership changed')
        require(latest['id'] == sub_id and latest['service_relation_id'] == service['id']
                and latest['student_person_id'] == service['person_id'], 'Latest submission changed')
        require(card['status'] == latest['status'] == 'operator_feasibility'
                and latest['operator_reviewed_at'] is None and latest['operator_reviewed_by_user_id'] is None,
                'Approval already advanced')
        require(latest['operator_user_id'] == (new if repaired else old), 'Unexpected pending operator')
        if repaired:
            require(card['operator_user_id'] == latest['operator_user_id'] == new, 'New operator guard fails')
            require(old != card['operator_user_id'], 'Old operator still passes card guard')


def apply(cur, before, fail_after=None, limit=None):
    state = capture(cur, True)
    require(state[CARD] == before[CARD] and state[SERVICE] == before[SERVICE], 'Concurrent owner/card edit')
    current = {r['id']: r for r in state[SUB]}
    original = {r['id']: r for r in before[SUB]}
    receipts = {r['idempotency_key']: r for r in state[EVENT]}
    count = 0
    for cid, (sid, old, new) in TARGETS.items():
        key = f'{MARKER}:{sid}'
        require(max(r['id'] for r in state[SUB] if r['card_id'] == cid and active(r)) == sid,
                'New submission exists')
        expected = dict(original[sid])
        if key in receipts:
            expected.update(operator_user_id=new, version=expected['version']+1,
                            updater=MARKER, update_time=receipts[key]['occurred_at'])
            require(current[sid] == expected, 'Later edit prevents replay')
            continue
        require(current[sid] == expected, 'Stale submission preimage')
        require(expected['status'] == 'operator_feasibility' and expected['operator_user_id'] == old
                and expected['operator_reviewed_at'] is None and expected['operator_reviewed_by_user_id'] is None,
                'Pending state changed')
        now = rows(cur, 'SELECT NOW() now')[0]['now']
        cur.execute(f'UPDATE {SUB} SET operator_user_id=%s,version=version+1,updater=%s,update_time=%s '
                    "WHERE tenant_id=1 AND id=%s AND version=%s AND operator_user_id=%s AND status='operator_feasibility' "
                    'AND operator_reviewed_at IS NULL AND operator_reviewed_by_user_id IS NULL AND deleted=0',
                    (new, MARKER, now, sid, expected['version'], old))
        require(cur.rowcount == 1, 'Concurrent submission update')
        cur.execute(f'INSERT INTO {EVENT} (event_type,aggregate_type,aggregate_id,operator_user_id,reason,'
                    'related_object_refs,occurred_at,idempotency_key,creator,updater,tenant_id) '
                    'VALUES (%s,%s,%s,NULL,%s,%s,%s,%s,%s,%s,1)',
                    ('positioning_operator_handover', 'positioning_card_submission', sid, REASON,
                     encoded({'cardId': cid, 'oldOperatorId': old, 'newOperatorId': new}), now, key, MARKER, MARKER))
        count += 1
        if fail_after == count:
            raise RuntimeError('injected_failure')
        if limit == count:
            break
    return count


def verify(before, after):
    require(before[CARD] == after[CARD] and before[SERVICE] == after[SERVICE], 'Owner/card fields changed')
    require(len(before[SUB]) == len(after[SUB]), 'Submission count changed')
    target_map = {v[0]: v[2] for v in TARGETS.values()}
    for old, new in zip(before[SUB], after[SUB]):
        expected = dict(old)
        if old['id'] in target_map:
            expected.update(operator_user_id=target_map[old['id']], version=old['version']+1,
                            updater=MARKER, update_time=new['update_time'])
        require(expected == new, 'Historical/unauthorized field change')
    require(len(after[EVENT]) == 4, 'Missing audit')
    validate(after, True)


def rehearse(conn, cur, before):
    for table in TABLES:
        ddl = rows(cur, f'SHOW CREATE TABLE {table}')[0]['Create Table']
        cur.execute(ddl.replace('CREATE TABLE', 'CREATE TEMPORARY TABLE', 1))
        columns = [r['Field'] for r in rows(cur, f'SHOW COLUMNS FROM {table}')
                   if 'GENERATED' not in r['Extra'].replace('DEFAULT_GENERATED', '')]
        if before[table]:
            cur.executemany(f'INSERT INTO {table} ({",".join(columns)}) VALUES ({",".join(["%s"]*len(columns))})',
                            [[r[c] for c in columns] for r in before[table]])
    conn.commit()
    require(capture(cur) == before, 'Fixture differs')
    try:
        apply(cur, before, fail_after=2)
        raise AssertionError('Injection failed')
    except RuntimeError as exc:
        require(str(exc) == 'injected_failure', 'Unexpected failure')
    conn.rollback()
    require(capture(cur) == before, 'Rollback failed')
    require(apply(cur, before) == 4, 'Initial repair failed')
    verify(before, capture(cur))
    require(apply(cur, before) == 0, 'Repeat changed rows')
    conn.rollback()
    apply(cur, before, limit=2)
    conn.commit()
    require(apply(cur, before) == 2, 'Partial recovery failed')
    verify(before, capture(cur))
    conn.commit()
    cur.execute(f'UPDATE {SUB} SET version=version+1 WHERE id=57')
    try:
        apply(cur, before)
        raise AssertionError('Stale row accepted')
    except RuntimeError as exc:
        require('Later edit' in str(exc), 'Wrong stale failure')
    conn.rollback()
    # Rehearse the persistence predicate of markOperatorDecision, only on temporary tables.
    for cid, (sid, old, new) in TARGETS.items():
        card = next(r for r in before[CARD] if r['id'] == cid)
        sub = rows(cur, f'SELECT * FROM {SUB} WHERE id=%s', (sid,))[0]
        require(card['operator_user_id'] == sub['operator_user_id'] == new, 'New operator denied')
        require(not(card['operator_user_id'] == sub['operator_user_id'] == old), 'Old operator allowed')
        cur.execute(f"UPDATE {SUB} SET status='student_link_pending',operator_reviewed_by_user_id=%s,"
                    "operator_reviewed_at=NOW(),version=version+1 WHERE id=%s AND version=%s AND status='operator_feasibility'",
                    (new, sid, sub['version']))
        require(cur.rowcount == 1, 'Approval transition predicate failed')
    conn.rollback()
    verify(before, capture(cur))
    for table in reversed(TABLES):
        cur.execute(f'DROP TEMPORARY TABLE {table}')
    conn.commit()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--evidence-dir', type=Path, required=True)
    parser.add_argument('--apply', action='store_true')
    args = parser.parse_args()
    path = Path('/etc/zsjos/agent-environment')
    env = os.getenv('ZSJOS_AGENT_ENV')
    if env is None:
        env = path.read_text().strip() if path.exists() else 'local'
    require(env == 'test', 'Only authorized test environment supported')
    os.umask(0o077)
    args.evidence_dir.mkdir(parents=True, mode=0o700, exist_ok=False)
    conn = connect('zsjos-mysql-1')
    try:
        with conn.cursor() as cur:
            cur.execute('SET NAMES utf8mb4')
            cur.execute("SET time_zone='+08:00'")
            cur.execute('SET SESSION innodb_lock_wait_timeout=10')
            before = capture(cur)
            validate(before)
            require(not before[EVENT], 'Already executed; use saved plan/readback rather than new repair')
            enabled = rows(cur,'SELECT id FROM system_users WHERE tenant_id=1 AND id IN (45,522) AND status=0 AND deleted=0')
            require(len(enabled)==2, 'New operator unavailable')
            persist(args.evidence_dir/'before.json', before)
            conn.rollback()
            rehearse(conn,cur,before)
            print('MySQL rehearsal passed: initial/repeat/rollback/recovery/stale refusal/operator guards/decision predicate',flush=True)
            if not args.apply:
                persist(args.evidence_dir/'result.json',{'mode':'verified-only','targets':4})
                return
            require(len(rows(cur,'SELECT id FROM system_users WHERE tenant_id=1 AND id IN (45,522) AND status=0 AND deleted=0 FOR UPDATE'))==2,'Operator changed')
            require(capture(cur,True)==before,'Live input changed; regenerate plan')
            validate(before)
            require(apply(cur,before)==4,'Unexpected mutation count')
            after=capture(cur)
            verify(before,after)
            for row in rows(cur,f'SELECT reason,HEX(reason) hex_value FROM {EVENT} WHERE tenant_id=1 AND idempotency_key LIKE %s',(MARKER+':%',)):
                require(bytes.fromhex(row['hex_value']).decode('utf-8')==row['reason'],'UTF-8 mismatch')
            persist(args.evidence_dir/'after.json',after)
            conn.commit()
            require(apply(cur,before)==0,'Committed repeat failed')
            verify(before,capture(cur))
            conn.rollback()
            report={'mode':'committed','updatedPendingSubmissions':4,'auditEvents':4,
                    'historyAndCardServicePreserved':True,'mysqlRehearsalPassed':True,
                    'newOperatorGuardsPassed':4,'oldOperatorGuardsDenied':4,
                    'liveApprovalExecuted':False,'utf8Verified':True,
                    'backupSha256':hashlib.sha256((args.evidence_dir/'before.json').read_bytes()).hexdigest()}
            persist(args.evidence_dir/'result.json',report)
            print(encoded(report))
    finally:
        conn.rollback();conn.close()

if __name__=='__main__':
    main()
