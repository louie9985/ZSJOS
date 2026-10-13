#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Authorized single-Lead repair. See docs/operations/lead-invalid-20261012-repair.md."""
import argparse
import datetime as dt
import hashlib
import json
import os
from pathlib import Path
import subprocess

import pymysql

LEAD_NO = 'LD202609190220'
TENANT = 1
MARKER = 'repair-lead-invalid-20261012'
KEY = MARKER + ':' + LEAD_NO
REASON = '经业务确认修复统一回收修复覆盖的无效状态；保留原判定时间及无效说明'
TABLES = ('zsjos_lead', 'zsjos_opportunity', 'zsjos_business_event')


def require(ok, message):
    if not ok:
        raise RuntimeError(message)


def encoded(value):
    def default(item):
        if isinstance(item, (dt.datetime, dt.date)):
            return item.isoformat()
        if isinstance(item, bytes):
            return {'hex': item.hex()}
        raise TypeError(type(item).__name__)
    return json.dumps(value, default=default, ensure_ascii=False, sort_keys=True, indent=2)


def rows(cur, sql, args=()):
    cur.execute(sql, args)
    return cur.fetchall()


def snapshot(cur):
    lead = rows(cur, 'SELECT * FROM zsjos_lead WHERE tenant_id=%s AND lead_no=%s AND deleted=0 FOR UPDATE',
                (TENANT, LEAD_NO))
    require(len(lead) == 1, 'Expected exactly one Lead')
    lid = lead[0]['id']
    opportunity = rows(cur, 'SELECT * FROM zsjos_opportunity WHERE tenant_id=%s AND lead_id=%s AND deleted=0 FOR UPDATE',
                       (TENANT, lid))
    require(len(opportunity) == 1, 'Expected exactly one Opportunity')
    events = rows(cur, 'SELECT * FROM zsjos_business_event WHERE tenant_id=%s AND aggregate_type=%s AND aggregate_id=%s ORDER BY id',
                  (TENANT, 'lead', lid))
    return [lead, opportunity, events]


def check_dependencies(cur, state):
    lead, opp = state[0][0], state[1][0]
    for table in ('zsjos_order', 'zsjos_lead_appeal'):
        require(not rows(cur, f'SELECT id FROM {table} WHERE tenant_id=%s AND lead_id=%s AND deleted=0',
                         (TENANT, lead['id'])), 'Unexpected order or appeal')
    require(not rows(cur, 'SELECT id FROM zsjos_business_task WHERE tenant_id=%s AND deleted=0 '
                     'AND status=%s AND ((biz_type=%s AND biz_id=%s) OR (biz_type=%s AND biz_id=%s))',
                     (TENANT, 'pending', 'lead', lead['id'], 'opportunity', opp['id'])), 'Unexpected pending task')
    require(not rows(cur, 'SELECT id FROM zsjos_performance_attribution WHERE tenant_id=%s AND lead_id=%s '
                     'AND deleted=0 AND fact_type<>%s', (TENANT, lead['id'], 'ASSIGNMENT')),
            'Unexpected performance attribution')


def verify_preservation(before, after):
    mutable = ({'status', 'version', 'updater', 'update_time'},
               {'status', 'lost_at', 'lost_reason', 'next_follow_up_at', 'version', 'updater', 'update_time'})
    for i in (0, 1):
        require({k: v for k, v in before[i][0].items() if k not in mutable[i]} ==
                {k: v for k, v in after[i][0].items() if k not in mutable[i]}, 'Unapproved field change')
        require(after[i][0]['version'] == before[i][0]['version'] + 1, 'Version not advanced once')
    require(after[2][:-1] == before[2], 'Historical events changed')
    require(len(after[2]) == len(before[2]) + 1, 'Expected one audit event')


def repair(cur, inject_failure=False):
    before = snapshot(cur)
    lead, opp = before[0][0], before[1][0]
    existing = [e for e in before[2] if e['idempotency_key'] == KEY]
    if existing:
        require(len(existing) == 1 and lead['status'] == 'invalid' and opp['status'] == 'lost'
                and lead['updater'] == opp['updater'] == MARKER
                and opp['lost_reason'] == REASON and opp['lost_at'] == existing[0]['occurred_at']
                and opp['next_follow_up_at'] is None, 'Repair marker conflicts with current state')
        return 0
    require(lead['status'] == 'submitted' and lead['assignment_status'] == 'recycle_pending'
            and lead['owner_user_id'] is None and lead['recycle_source_owner_user_id'] is not None
            and lead['updater'] == 'lead-recycle-suspension-repair' and lead['version'] == 0
            and lead['qualified_at'] is not None and lead['invalid_description']
            and lead['next_follow_up_at'] is None and lead['valid_description'] is None,
            'Lead no longer matches approved prerequisite')
    require(opp['status'] == 'open' and opp['type'] == 'initial_conversion' and opp['version'] == 0
            and opp['lost_at'] is None and opp['lost_reason'] is None, 'Opportunity prerequisite changed')
    now = rows(cur, 'SELECT NOW() AS now')[0]['now']
    cur.execute('UPDATE zsjos_lead SET status=%s,version=version+1,updater=%s,update_time=%s '
                'WHERE tenant_id=%s AND id=%s AND version=%s AND status=%s',
                ('invalid', MARKER, now, TENANT, lead['id'], lead['version'], 'submitted'))
    require(cur.rowcount == 1, 'Lead update conflict')
    if inject_failure:
        raise RuntimeError('Injected interruption after Lead update')
    cur.execute('UPDATE zsjos_opportunity SET status=%s,lost_at=%s,lost_reason=%s,next_follow_up_at=NULL,'
                'version=version+1,updater=%s,update_time=%s WHERE tenant_id=%s AND id=%s AND version=%s AND status=%s',
                ('lost', now, REASON, MARKER, now, TENANT, opp['id'], opp['version'], 'open'))
    require(cur.rowcount == 1, 'Opportunity update conflict')
    cur.execute('INSERT INTO zsjos_business_event (event_type,aggregate_type,aggregate_id,operator_user_id,'
                'from_status,to_status,reason,related_object_refs,occurred_at,idempotency_key,creator,updater,tenant_id) '
                'VALUES (%s,%s,%s,NULL,%s,%s,%s,%s,%s,%s,%s,%s,%s)',
                ('lead_state_repaired', 'lead', lead['id'], 'submitted', 'invalid', REASON,
                 json.dumps({'repairSource': MARKER, 'opportunityId': opp['id'],
                             'opportunityFromStatus': 'open', 'opportunityToStatus': 'lost',
                             'historicalQualificationPreserved': True}), now, KEY, MARKER, MARKER, TENANT))
    verify_preservation(before, snapshot(cur))
    return 1


def rehearse(conn, cur, original):
    # Temporary names shadow real tables only within this connection. Generated columns
    # are omitted during copying; schema defaults and constraints remain engine-owned.
    for table, data in zip(TABLES, original):
        ddl = rows(cur, f'SHOW CREATE TABLE {table}')[0]['Create Table']
        cur.execute(ddl.replace('CREATE TABLE', 'CREATE TEMPORARY TABLE', 1))
        columns = [r['Field'] for r in rows(cur, f'SHOW COLUMNS FROM {table}')
                   if 'GENERATED' not in r['Extra'].replace('DEFAULT_GENERATED', '')]
        if data:
            cur.executemany(f'INSERT INTO {table} ({",".join(columns)}) VALUES ({",".join(["%s"] * len(columns))})',
                            [[row[c] for c in columns] for row in data])
    conn.commit()
    try:
        repair(cur, inject_failure=True)
        raise AssertionError('Failure injection did not fail')
    except RuntimeError as exc:
        require(str(exc) == 'Injected interruption after Lead update', 'Unexpected failure')
        conn.rollback()
    require(snapshot(cur) == original, 'Partial execution did not roll back')
    require(repair(cur) == 1, 'Initial execution failed')
    first = snapshot(cur)
    require(repair(cur) == 0 and snapshot(cur) == first, 'Repeat execution changed data')
    conn.rollback()
    require(snapshot(cur) == original, 'Successful rehearsal rollback failed')
    cur.execute('UPDATE zsjos_lead SET status=%s WHERE tenant_id=%s AND lead_no=%s', ('valid', TENANT, LEAD_NO))
    try:
        repair(cur)
        raise AssertionError('Conflicting state was accepted')
    except RuntimeError as exc:
        require(str(exc) == 'Lead no longer matches approved prerequisite', 'Unexpected conflict failure')
    conn.rollback()
    require(snapshot(cur) == original, 'Conflict rollback failed')
    for table in reversed(TABLES):
        cur.execute(f'DROP TEMPORARY TABLE {table}')
    conn.commit()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--container', required=True)
    parser.add_argument('--evidence-dir', required=True, type=Path)
    parser.add_argument('--apply', action='store_true')
    args = parser.parse_args()
    marker = Path('/etc/zsjos/agent-environment')
    environment = os.getenv('ZSJOS_AGENT_ENV')
    if environment is None:
        environment = marker.read_text().strip() if marker.exists() else 'local'
    require(environment == 'test', 'Only the explicitly authorized test environment is supported')
    os.umask(0o077)
    args.evidence_dir.mkdir(mode=0o700, parents=True, exist_ok=False)
    metadata = json.loads(subprocess.check_output(['docker', 'inspect', args.container]))[0]
    config = dict(item.split('=', 1) for item in metadata['Config']['Env'] if '=' in item)
    password = config.get('MYSQL_ROOT_PASSWORD')
    if password is None:
        password = subprocess.check_output(['docker', 'exec', args.container, 'cat',
                                            config['MYSQL_ROOT_PASSWORD_FILE']]).decode().strip()
    port = metadata['NetworkSettings']['Ports']['3306/tcp'][0]
    require(port['HostIp'] == '127.0.0.1', 'Expected local database binding')
    conn = pymysql.connect(host=port['HostIp'], port=int(port['HostPort']), user='root', password=password,
                           database=config['MYSQL_DATABASE'], charset='utf8mb4', autocommit=False,
                           cursorclass=pymysql.cursors.DictCursor)
    try:
        with conn.cursor() as cur:
            cur.execute('SET NAMES utf8mb4')
            cur.execute("SET time_zone='+08:00'")
            original = snapshot(cur)
            check_dependencies(cur, original)
            backup = args.evidence_dir / 'before.json'
            backup.write_text(encoded(dict(zip(TABLES, original))), encoding='utf-8')
            with backup.open('rb') as handle:
                os.fsync(handle.fileno())
            conn.rollback()
            completed = any(e['idempotency_key'] == KEY for e in original[2])
            if not completed:
                rehearse(conn, cur, original)
            require(snapshot(cur) == original, 'Live data changed since backup; no write performed')
            check_dependencies(cur, original)
            changed = repair(cur)
            after = snapshot(cur)
            if args.apply:
                conn.commit()
                require(snapshot(cur) == after, 'Committed readback mismatch')
                require(repair(cur) == 0 and snapshot(cur) == after, 'Committed repeat was not a no-op')
                conn.rollback()
            else:
                conn.rollback()
                require(snapshot(cur) == original, 'Live dry-run rollback mismatch')
                conn.rollback()
            # Read actual stored bytes without publishing private historical descriptions.
            for table, field, condition, params in (
                ('zsjos_lead', 'invalid_description', 'tenant_id=%s AND lead_no=%s', (TENANT, LEAD_NO)),
                ('zsjos_opportunity', 'lost_reason', 'tenant_id=%s AND id=%s', (TENANT, original[1][0]['id'])),
                ('zsjos_business_event', 'reason', 'tenant_id=%s AND idempotency_key=%s', (TENANT, KEY))):
                for value in rows(cur, f'SELECT {field} AS text,HEX({field}) AS hex_value FROM {table} WHERE {condition}', params):
                    if value['text'] is not None:
                        require(bytes.fromhex(value['hex_value']).decode('utf-8') == value['text'], 'UTF-8 byte mismatch')
            (args.evidence_dir / 'after.json').write_text(encoded(dict(zip(TABLES, after))), encoding='utf-8')
            report = {'mode': 'committed' if args.apply else 'rolled_back', 'leadNo': LEAD_NO,
                      'changedLeadOpportunityEvent': [changed] * 3, 'repeatNoOp': True,
                      'initialRepeatFailureRollbackConflictVerified': not completed,
                      'preservationAndUtf8Verified': True,
                      'beforeSha256': hashlib.sha256(backup.read_bytes()).hexdigest()}
            (args.evidence_dir / 'result.json').write_text(encoded(report), encoding='utf-8')
            print(encoded(report))
    finally:
        conn.rollback()
        conn.close()


if __name__ == '__main__':
    main()
