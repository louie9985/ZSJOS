#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Scoped, authorized test-tenant handover; see content-director-emergency-repair.md."""
import argparse
import datetime as dt
import hashlib
import json
import os
from pathlib import Path
import subprocess

import pymysql

TENANT = 1
MARKER = 'director-transfer-20261015'
REASON = '经业务确认，按运营当前唯一有效的关联编导交接当前责任归属；保留历史提交与审批快照'
SERVICE = 'zsjos_service_relation'
ACCOUNT = 'zsjos_media_account'
CARD = 'zsjos_positioning_card'
PLAN = 'zsjos_student_delivery_plan'
STAGE = 'zsjos_student_delivery_stage'
GROUP = 'zsjos_collaboration_group'
TASK = 'zsjos_business_task'
INTERVIEW = 'zsjos_student_positioning_interview'
EVENT = 'zsjos_business_event'
RELATION = 'zsjos_user_relation'
USERS = 'system_users'
HISTORY = ('zsjos_content_review_batch', 'zsjos_positioning_card_submission',
           'zsjos_positioning_service_card', 'zsjos_student_collaborator_assignment_log')
TABLES = (USERS, RELATION, SERVICE, ACCOUNT, GROUP, PLAN, STAGE, CARD, INTERVIEW, TASK, *HISTORY, EVENT)


def require(ok, message):
    if not ok:
        raise RuntimeError(message)


def encoded(value):
    def default(item):
        if isinstance(item, (dt.datetime, dt.date)):
            return {'$date': item.isoformat(), '$type': type(item).__name__}
        if isinstance(item, bytes):
            return {'$bytes': item.hex()}
        raise TypeError(type(item).__name__)
    return json.dumps(value, ensure_ascii=False, sort_keys=True, indent=2, default=default)


def decoded(text):
    def hook(item):
        if '$bytes' in item:
            return bytes.fromhex(item['$bytes'])
        if '$date' in item:
            return getattr(dt, item['$type']).fromisoformat(item['$date'])
        return item
    return json.loads(text, object_hook=hook)


def persist(path, value):
    with path.open('w', encoding='utf-8') as handle:
        handle.write(encoded(value))
        handle.flush()
        os.fsync(handle.fileno())


def rows(cur, sql, args=()):
    cur.execute(sql, args or None)
    return cur.fetchall()


def active(row):
    return row['deleted'] in (b'\x00', 0, False)


def capture(cur, lock=False):
    result = {}
    for table in TABLES:
        condition = 'tenant_id=1'
        if table == RELATION:
            condition += " AND scene='content_director_operator'"
        if table == TASK:
            condition += " AND biz_type IN ('media_account_diagnosis','student_delivery_stage','student_service','positioning_card')"
        if table == EVENT:
            condition += f" AND idempotency_key LIKE '{MARKER}:%'"
        cols = 'id,tenant_id,status,deleted' if table == USERS else '*'
        result[table] = rows(cur, f'SELECT {cols} FROM {table} WHERE {condition} ORDER BY id' + (' FOR UPDATE' if lock else ''))
    return result


def build_plan(state):
    users = {x['id']: x for x in state[USERS] if active(x) and x['status'] == 0}
    directors = {}
    for row in state[RELATION]:
        if active(row) and row['status'] == 0:
            directors.setdefault(row['target_user_id'], []).append(row['source_user_id'])
    services = {x['id']: x for x in state[SERVICE] if active(x)}
    require(all(uid in users for uid in (31,59,522)), 'Required users unavailable')
    for op in (59,522):
        require(directors.get(op) == [42], 'Operator relation changed')
    targets = {}
    for row in services.values():
        if row['operator_user_id'] not in (59,522):
            continue
        if row['status'] != 'active' or row['acceptance_status'] != 'accepted':
            continue
        op = row['operator_user_id']
        require(op in users and len(directors.get(op, [])) == 1, 'Ambiguous or disabled operator relationship')
        new = 31
        require(new in users, 'Disabled target director')
        if new != row['content_director_user_id']:
            targets[row['id']] = new
    require(len(targets) == 25, 'Expected approved 25 service relations; review changed scope')
    mutations = []

    def add(table, row, field, new, service_id):
        if row[field] != new:
            mutations.append({'table': table, 'id': row['id'], 'field': field,
                              'old': row[field], 'new': new, 'service_id': service_id,
                              'before': row})

    for sid, new in targets.items():
        add(SERVICE, services[sid], 'content_director_user_id', new, sid)
    accounts = {}
    for row in state[ACCOUNT]:
        if not active(row):
            continue
        service = services.get(row['create_service_relation_id'])
        require(service is not None and row['student_person_id'] == service['person_id']
                and row['owner_operator_user_id'] == service['operator_user_id']
                and row['director_user_id'] == service['content_director_user_id'], 'Account/service preimage conflict')
        sid = service['id']
        if sid in targets:
            accounts[row['id']] = sid
            add(ACCOUNT, row, 'director_user_id', targets[sid], sid)
    require(len(accounts) == 48, 'Expected approved 48 accounts; review changed scope')
    for table in (GROUP, PLAN, STAGE, CARD, INTERVIEW):
        for row in state[table]:
            if not active(row):
                continue
            sid = (row.get('source_service_relation_id') if table == GROUP else row.get('service_relation_id'))
            account_sid = accounts.get(row.get('account_id'))
            if sid is not None and account_sid is not None:
                require(sid == account_sid, 'Linked object has contradictory service/account')
            sid = sid if sid in targets else account_sid
            if sid not in targets:
                continue
            if table in (GROUP, PLAN) and row['status'].lower() != 'active':
                continue
            if table == STAGE:
                require(row['status'] == 'WAITING' and row['completed_at'] is None
                        and row['bpm_process_instance_id'] is None, 'Unexpected active/completed delivery workflow')
            if table == CARD:
                require(row['status'] in {'confirmed', 'student_evidence_pending', 'co_creating',
                                         'student_confirm', 'operator_feasibility'}
                        and row['ip_process_instance_id'] is None, 'Unexpected positioning BPM workflow')
            if table == INTERVIEW and row['status'] != 'draft':
                continue
            require(row['director_user_id'] in (services[sid]['content_director_user_id'], targets[sid]),
                    'Linked object has different director')
            add(table, row, 'director_user_id', targets[sid], sid)
    stage_services = {x['id']: accounts[x['account_id']] for x in state[STAGE] if x['account_id'] in accounts}
    for row in state[TASK]:
        if not active(row) or row['status'] != 'pending':
            continue
        sid = None
        if row['biz_type'] == 'media_account_diagnosis' and row['biz_id'] in accounts:
            require(row['task_type'] in {'media_account_diagnosis_7d', 'media_account_diagnosis_14d',
                                        'media_account_diagnosis_28d'}, 'Unknown diagnosis task')
            sid = accounts[row['biz_id']]
        if row['biz_type'] == 'student_delivery_stage' and row['biz_id'] in stage_services:
            require(row['task_type'] == 'student_delivery_confirmation', 'Unknown delivery task')
            sid = stage_services[row['biz_id']]
        if sid is not None:
            require(row['assignee_type'] == 'user' and row['assignee_id'] in
                    (services[sid]['content_director_user_id'], targets[sid]), 'Task assigned to third party')
            add(TASK, row, 'assignee_id', targets[sid], sid)
    batch_counts = {}
    for row in state[HISTORY[0]]:
        selected = json.loads(row['account_ids_json'] or '[]')
        if active(row) and (row['account_id'] in accounts or set(selected) & accounts.keys()):
            require(row['status'] in {'COMPLETED', 'DRAFT', 'NEED_MODIFY', 'CANCELLED'}, 'In-flight content workflow needs BPM handover')
            batch_counts[row['status']] = batch_counts.get(row['status'], 0) + 1
    require(not state[EVENT], 'Existing handover receipt; use existing evidence for verification')
    for row in state[RELATION]:
        if active(row) and row['status'] == 0 and row['target_user_id'] in (59,522):
            add(RELATION, row, 'source_user_id', 31, None)
    require(len([x for x in mutations if x['table'] == RELATION]) == 2, 'Relation count changed')
    summary = {}
    for change in mutations:
        summary[change['table']] = summary.get(change['table'], 0) + 1
    return {'mutations': mutations, 'services': targets, 'accounts': accounts,
            'summary': summary, 'preservedBatches': batch_counts}


def apply(cur, plan, fail_after=None, limit=None):
    count = 0
    for change in plan['mutations']:
        table, rid, field = change['table'], change['id'], change['field']
        key = f'{MARKER}:{table}:{rid}'
        current = rows(cur, f'SELECT * FROM {table} WHERE tenant_id=%s AND id=%s FOR UPDATE', (TENANT, rid))[0]
        receipt = rows(cur, f'SELECT * FROM {EVENT} WHERE tenant_id=%s AND idempotency_key=%s', (TENANT, key))
        if receipt:
            expected = dict(change['before'])
            expected.update({field: change['new'], 'version': expected.get('version',0) + 1,
                             'updater': MARKER, 'update_time': receipt[0]['occurred_at']})
            if 'version' not in current: expected.pop('version', None)
            require(current == expected, 'Previously repaired row has subsequent edits; refusing replay')
            continue
        require(current == change['before'], 'Stale preimage; no overwrite allowed')
        now = rows(cur, 'SELECT NOW() AS now')[0]['now']
        version_set = 'version=version+1,' if 'version' in current else ''
        cur.execute(f'UPDATE {table} SET {field}=%s,{version_set}updater=%s,update_time=%s '
                    f'WHERE tenant_id=%s AND id=%s AND {field}<=>%s AND deleted=0',
                    (change['new'], MARKER, now, TENANT, rid, change['old']))
        require(cur.rowcount == 1, 'Optimistic update conflict')
        refs = json.dumps({'table': table, 'field': field, 'oldUserId': change['old'],
                           'newUserId': change['new'], 'serviceRelationId': change['service_id']})
        cur.execute(f'INSERT INTO {EVENT} (event_type,aggregate_type,aggregate_id,operator_user_id,reason,'
                    'related_object_refs,occurred_at,idempotency_key,creator,updater,tenant_id) '
                    'VALUES (%s,%s,%s,NULL,%s,%s,%s,%s,%s,%s,%s)',
                    ('director_handover', table.removeprefix('zsjos_'), rid, REASON, refs, now, key, MARKER, MARKER, TENANT))
        count += 1
        if fail_after == count:
            raise RuntimeError('injected_failure')
        if limit == count:
            break
    return count


def verify(before, after, plan):
    changes = {(x['table'], x['id']): x for x in plan['mutations']}
    for table in TABLES:
        if table == EVENT:
            continue
        require(len(before[table]) == len(after[table]), 'Unexpected row count change')
        for old, new in zip(before[table], after[table]):
            change = changes.get((table, old['id']))
            if not change:
                require(old == new, 'Unrelated or historical record modified')
                continue
            expected = dict(old)
            expected.update({change['field']: change['new'], 'version': old.get('version',0) + 1,
                             'updater': MARKER, 'update_time': new['update_time']})
            if 'version' not in old: expected.pop('version', None)
            require(expected == new, 'Unexpected field mutation')
    require(len(after[EVENT]) == len(plan['mutations']), 'Incomplete transaction audit')
    for receipt in after[EVENT]:
        require(receipt['reason'] == REASON and receipt['operator_user_id'] is None, 'Invalid system audit')


def restore(cur, plan):
    """Compensating handover, never a blind import over later application changes."""
    require(apply(cur, plan) == 0, 'Restore requires a fully applied unchanged handover')
    for change in reversed(plan['mutations']):
        table, rid, field = change['table'], change['id'], change['field']
        now = rows(cur, 'SELECT NOW() AS now')[0]['now']
        version_set = 'version=version+1,' if 'version' in change['before'] else ''
        cur.execute(f'UPDATE {table} SET {field}=%s,{version_set}updater=%s,update_time=%s '
                    'WHERE tenant_id=%s AND id=%s',
                    (change['old'], MARKER + '-rollback', now, TENANT, rid))
        require(cur.rowcount == 1, 'Rollback row missing')
        cur.execute(f'INSERT INTO {EVENT} (event_type,aggregate_type,aggregate_id,operator_user_id,reason,'
                    'related_object_refs,occurred_at,idempotency_key,creator,updater,tenant_id) '
                    'VALUES (%s,%s,%s,NULL,%s,%s,%s,%s,%s,%s,%s)',
                    ('director_handover_rollback', table.removeprefix('zsjos_'), rid,
                     '受控回退责任编导交接，保留原交接审计',
                     json.dumps({'table': table, 'field': field, 'restoredUserId': change['old']}), now,
                     f'{MARKER}:rollback:{table}:{rid}', MARKER, MARKER, TENANT))


def rehearse(conn, cur, before, plan):
    for table in TABLES:
        ddl = rows(cur, f'SHOW CREATE TABLE {table}')[0]['Create Table']
        # System users are only dependency inputs; temporary copy needs no sensitive columns.
        if table == USERS:
            cur.execute('CREATE TEMPORARY TABLE system_users (id BIGINT PRIMARY KEY,tenant_id BIGINT,status INT,deleted BIT) ENGINE=InnoDB')
        else:
            cur.execute(ddl.replace('CREATE TABLE', 'CREATE TEMPORARY TABLE', 1))
        columns = [r['Field'] for r in rows(cur, f'SHOW COLUMNS FROM {table}')
                   if 'GENERATED' not in r['Extra'].replace('DEFAULT_GENERATED', '')]
        if before[table]:
            cur.executemany(f'INSERT INTO {table} ({",".join(columns)}) VALUES ({",".join(["%s"] * len(columns))})',
                            [[row[c] for c in columns] for row in before[table]])
    conn.commit()
    require(capture(cur) == before, 'Temporary fixture differs from live prerequisite')
    try:
        apply(cur, plan, fail_after=3)
        raise AssertionError('Expected injected error')
    except RuntimeError as exc:
        require(str(exc) == 'injected_failure', 'Unexpected test failure')
    conn.rollback()
    require(capture(cur) == before, 'Failure failed to roll back')
    require(apply(cur, plan) == len(plan['mutations']), 'Initial application failed')
    after = capture(cur)
    verify(before, after, plan)
    require(apply(cur, plan) == 0 and capture(cur) == after, 'Repeat mutated fixture')
    conn.rollback()
    require(capture(cur) == before, 'Success rollback mismatch')
    # A committed partial application is recoverable only if each receipt matches its row.
    apply(cur, plan, limit=3)
    conn.commit()
    require(apply(cur, plan) == len(plan['mutations']) - 3, 'Partial recovery failed')
    verify(before, capture(cur), plan)
    conn.commit()
    first = plan['mutations'][0]
    cur.execute(f"UPDATE {first['table']} SET version=version+1 WHERE id=%s", (first['id'],))
    try:
        apply(cur, plan)
        raise AssertionError('Later edit accepted')
    except RuntimeError as exc:
        require('subsequent edits' in str(exc), 'Unexpected stale failure')
    conn.rollback()
    restore(cur, plan)
    restored = capture(cur)
    for change in plan['mutations']:
        actual = next(x for x in restored[change['table']] if x['id'] == change['id'])
        expected = dict(change['before'])
        expected.update({'version': expected.get('version',0) + 2, 'updater': MARKER + '-rollback',
                         'update_time': actual['update_time']})
        if 'version' not in change['before']: expected.pop('version', None)
        require(actual == expected, 'Compensating rollback failed')
    conn.rollback()
    for table in reversed(TABLES):
        cur.execute(f'DROP TEMPORARY TABLE {table}')
    conn.commit()


def connect(container):
    meta = json.loads(subprocess.check_output(['docker', 'inspect', container]))[0]
    cfg = dict(x.split('=', 1) for x in meta['Config']['Env'] if '=' in x)
    password = subprocess.check_output(['docker', 'exec', container, 'cat', cfg['MYSQL_ROOT_PASSWORD_FILE']]).decode().strip()
    port = meta['NetworkSettings']['Ports']['3306/tcp'][0]
    require(port['HostIp'] == '127.0.0.1' and cfg['MYSQL_DATABASE'] == 'zsjos', 'Unexpected database target')
    return pymysql.connect(host=port['HostIp'], port=int(port['HostPort']), user='root', password=password,
                           database=cfg['MYSQL_DATABASE'], charset='utf8mb4', autocommit=False,
                           cursorclass=pymysql.cursors.DictCursor)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--container', default='zsjos-mysql-1')
    parser.add_argument('--evidence-dir', type=Path, required=True)
    parser.add_argument('--apply', action='store_true')
    parser.add_argument('--restore-from', type=Path, help='Separately authorized compensating rollback evidence')
    args = parser.parse_args()
    marker = Path('/etc/zsjos/agent-environment')
    environment = os.getenv('ZSJOS_AGENT_ENV')
    if environment is None:
        environment = marker.read_text().strip() if marker.exists() else 'local'
    require(environment == 'test', 'Only confirmed test environment is supported')
    os.umask(0o077)
    args.evidence_dir.mkdir(parents=True, mode=0o700, exist_ok=False)
    conn = connect(args.container)
    try:
        with conn.cursor() as cur:
            cur.execute('SET NAMES utf8mb4')
            cur.execute("SET time_zone='+08:00'")
            cur.execute('SET SESSION innodb_lock_wait_timeout=10')
            if args.restore_from:
                require(args.apply, 'Rollback is explicit; --apply required')
                plan = decoded((args.restore_from / 'plan.json').read_text(encoding='utf-8'))
                persist(args.evidence_dir / 'before.json', capture(cur, lock=True))
                restore(cur, plan)
                persist(args.evidence_dir / 'after.json', capture(cur))
                conn.commit()
                persist(args.evidence_dir / 'result.json', {'mode': 'rollback-committed', 'rows': len(plan['mutations'])})
                print('Compensating rollback committed')
                return
            before = capture(cur)
            plan = build_plan(before)
            conn.rollback()
            persist(args.evidence_dir / 'before.json', before)
            persist(args.evidence_dir / 'plan.json', plan)
            print(encoded({'phase': 'preflight', 'changes': plan['summary'], 'preservedBatches': plan['preservedBatches']}), flush=True)
            rehearse(conn, cur, before, plan)
            print('MySQL rehearsal passed: initial, repeat, failure rollback, partial recovery, stale refusal', flush=True)
            if not args.apply:
                persist(args.evidence_dir / 'result.json', {'mode': 'verified-only', 'changes': plan['summary']})
                return
            require(capture(cur, lock=True) == before, 'Live state changed during rehearsal; regenerate plan')
            require(build_plan(before) == plan, 'Plan changed')
            changed = apply(cur, plan)
            after = capture(cur)
            verify(before, after, plan)
            for value in rows(cur, f'SELECT reason,HEX(reason) hex_value FROM {EVENT} WHERE tenant_id=1 AND idempotency_key LIKE %s', (MARKER + ':%',)):
                require(bytes.fromhex(value['hex_value']).decode('utf-8') == value['reason'], 'Stored UTF-8 byte mismatch')
            persist(args.evidence_dir / 'after.json', after)
            conn.commit()
            # Read committed rows by exact mutation key; other business activity may resume.
            require(apply(cur, plan) == 0, 'Committed repeat not a no-op')
            conn.rollback()
            report = {'mode': 'committed', 'changedRecords': changed, 'auditEvents': changed,
                      'changes': plan['summary'], 'preservedBatches': plan['preservedBatches'],
                      'mysqlRehearsalPassed': True, 'fieldAndHistoryPreserved': True, 'utf8Verified': True,
                      'committedReadbackAndRepeatPassed': True,
                      'backupSha256': hashlib.sha256((args.evidence_dir / 'before.json').read_bytes()).hexdigest()}
            persist(args.evidence_dir / 'result.json', report)
            print(encoded(report))
    finally:
        conn.rollback()
        conn.close()


if __name__ == '__main__':
    main()
