#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Missing-only active review repair; actual --apply requires exact target approval."""
import argparse
import copy
import datetime as dt
import hashlib
import json
import os
from pathlib import Path
import subprocess

import pymysql

BATCH = 'zsjos_content_review_batch'
ACCOUNT = 'zsjos_media_account'
MARKER = 'content-profile-repair-20261014'
ACTIVE = ('DIRECTOR_REVIEW', 'FINAL_REVIEW')
DIRECT = {'accountNo': 'account_no', 'nickname': 'nickname',
          'platformAccountId': 'platform_account_id'}
PAIRS = [('platformValue', 'platformLabel', 'platform_value', 'platform_label_snapshot'),
         ('sStage', 'sStageLabel', 's_stage', 's_stage_label_snapshot'),
         ('currentStatusValue', 'currentStatusLabel', 'current_status_value', 'current_status_label_snapshot')]


def require(ok, message):
    if not ok:
        raise RuntimeError(message)


def encoded(value):
    def default(v):
        if isinstance(v, bytes):
            return {'$bytes': v.hex()}
        if isinstance(v, (dt.datetime, dt.date)):
            return {'$date': v.isoformat(), '$type': type(v).__name__}
        raise TypeError(type(v).__name__)
    return json.dumps(value, default=default, ensure_ascii=False, sort_keys=True, indent=2)


def digest(value):
    return hashlib.sha256(encoded(value).encode('utf-8')).hexdigest()


def persist(path, value):
    with path.open('w', encoding='utf-8') as handle:
        handle.write(encoded(value))
        handle.flush()
        os.fsync(handle.fileno())


def empty(v):
    return v is None or isinstance(v, str) and not v.strip()


def rows(q, sql, args=None):
    q.execute(sql, args)
    return q.fetchall()


def capture(q, lock=False):
    suffix = ' FOR UPDATE' if lock else ''
    batches = rows(q, f'SELECT * FROM {BATCH} WHERE tenant_id=1 AND deleted=0 AND status IN (%s,%s) ORDER BY id' + suffix, ACTIVE)
    ids = set()
    for b in batches:
        ids.add(b['account_id'])
        ids.update(json.loads(b['account_ids_json'] or '[]'))
    accounts = rows(q, f'SELECT * FROM {ACCOUNT} WHERE tenant_id=1 AND id IN (' + ','.join(['%s'] * len(ids)) + ') ORDER BY id' + suffix, tuple(sorted(ids))) if ids else []
    return {'batches': batches, 'accounts': accounts}


def build(state, timestamp):
    accounts = {a['id']: a for a in state['accounts'] if a['deleted'] in (0, False, b'\x00')}
    plan = []
    for b in state['batches']:
        context = json.loads(b['context_snapshot_json'] or '{}')
        after = copy.deepcopy(context)
        snapshots = after.get('accountSnapshots')
        if not isinstance(snapshots, list) or not snapshots:
            continue  # Do not invent a new historical account collection.
        allowed = set(json.loads(b['account_ids_json'] or '[]')) | {b['account_id']}
        changes = []
        for index, snapshot in enumerate(snapshots):
            aid = snapshot.get('id')
            a = accounts.get(aid)
            if aid not in allowed or not a or b['student_person_id'] is not None and a['student_person_id'] != b['student_person_id']:
                continue
            source = {key: a[col] for key, col in DIRECT.items()}
            for value, label, value_col, label_col in PAIRS:
                # A current label is safe only with the same stored code.
                if (not empty(snapshot.get(value)) and snapshot[value] != a[value_col]
                        or not empty(snapshot.get(label)) and snapshot[label] != a[label_col]):
                    continue
                if not empty(a[value_col]) and not empty(a[label_col]):
                    source.update({value: a[value_col], label: a[label_col]})
            values = json.loads(a['detail_values_json'] or '{}')
            details = json.loads(a['detail_snapshot_json'] or '[]')
            for field, key, plain in [('productGoal', 'product_goal', True),
                                     ('productFormLabel', 'work_format', True),
                                     ('publishFrequency', 'publish_rhythm', False)]:
                text = next((r.get('displayValue') for r in details if r.get('key') == key and isinstance(r.get('displayValue'), str) and r['displayValue'].strip()), None)
                source[field] = text if not empty(text) else values.get(key) if plain else None
            source['bottleneckLabel'] = a['primary_problem_code_label_snapshot']
            for field, value in source.items():
                if not empty(snapshot.get(field)) or empty(value):
                    continue
                if field == 'bottleneckLabel' and snapshot.get('primaryProblems'):
                    continue
                provenance = snapshot.setdefault('profileBackfill', {})
                require(isinstance(provenance, dict), 'Unexpected provenance format')
                require(field not in provenance, 'Prior repair metadata requires separate review')
                snapshot[field] = value
                provenance[field] = {'source': 'CURRENT_ACCOUNT', 'repair': MARKER,
                                     'accountId': aid, 'capturedAt': timestamp,
                                     'sourceColumn': DIRECT.get(field, next((vc if field == v else lc for v, l, vc, lc in PAIRS if field in (v, l)), 'stored_account_profile'))}
                changes.append({'accountId': aid, 'index': index, 'field': field})
        if changes:
            plan.append({'id': b['id'], 'batchNo': b['batch_no'], 'beforeHash': digest(b),
                         'beforeContext': context, 'afterContext': after, 'changes': changes})
    return plan


def apply(q, plan):
    changed = 0
    for item in plan:
        found = rows(q, f'SELECT * FROM {BATCH} WHERE id=%s AND tenant_id=1 FOR UPDATE', (item['id'],))
        require(len(found) == 1, 'Missing target')
        b = found[0]
        actual = json.loads(b['context_snapshot_json'] or '{}')
        require(b['status'] in ACTIVE and b['deleted'] in (0, False, b'\x00'), 'Target left active review')
        if actual == item['afterContext']:
            continue
        require(digest(b) == item['beforeHash'], 'Target changed; regenerate and review plan')
        q.execute(f'UPDATE {BATCH} SET context_snapshot_json=%s,version=version+1,updater=%s WHERE id=%s AND tenant_id=1 AND version=%s',
                  (json.dumps(item['afterContext'], ensure_ascii=False), MARKER, b['id'], b['version']))
        require(q.rowcount == 1, 'Version conflict')
        changed += 1
    return changed


def verify(q, before, plan):
    wanted = {p['id']: p for p in plan}
    actual = capture(q)
    require(actual['accounts'] == before['accounts'], 'Source accounts changed')
    require(len(actual['batches']) == len(before['batches']), 'Active batch set changed')
    current = {b['id']: b for b in actual['batches']}
    for b in before['batches']:
        row = current[b['id']]
        if b['id'] not in wanted:
            require(row == b, 'Unrelated batch changed')
            continue
        expected = dict(b)
        expected.update(context_snapshot_json=row['context_snapshot_json'], version=b['version'] + 1,
                        updater=MARKER, update_time=row['update_time'])
        require(row == expected, 'Unrelated target columns changed')
        require(json.loads(row['context_snapshot_json']) == wanted[b['id']]['afterContext'], 'JSON postimage mismatch')
        for change in wanted[b['id']]['changes']:
            path = '$.accountSnapshots[%d].%s' % (change['index'], change['field'])
            result = rows(q, f'SELECT JSON_UNQUOTE(JSON_EXTRACT(context_snapshot_json,%s)) value,HEX(JSON_UNQUOTE(JSON_EXTRACT(context_snapshot_json,%s))) hex_value FROM {BATCH} WHERE id=%s', (path, path, b['id']))[0]
            require(bytes.fromhex(result['hex_value']).decode('utf-8') == result['value'], 'UTF-8 mismatch')
    return actual


def rehearse(conn, q, state, plan):
    # Session temporary tables shadow live names; no persistent fixture schemas.
    for table, key in [(BATCH, 'batches'), (ACCOUNT, 'accounts')]:
        definition = rows(q, f'SHOW CREATE TABLE {table}')[0]['Create Table']
        q.execute(definition.replace('CREATE TABLE', 'CREATE TEMPORARY TABLE', 1))
        for row in state[key]:
            columns = list(row)
            q.execute(f'INSERT INTO {table} (' + ','.join('`' + col + '`' for col in columns) + ') VALUES (' + ','.join(['%s'] * len(columns)) + ')', tuple(row.values()))
    conn.commit()
    try:
        require(apply(q, plan) == len(plan), 'Initial count mismatch')
        verify(q, state, plan)
        conn.rollback()
        require(capture(q) == state, 'Injected failure rollback mismatch')
        if plan:
            apply(q, plan[:1])
            conn.commit()
            require(apply(q, plan) == len(plan) - 1, 'Partial recovery mismatch')
            verify(q, state, plan)
            conn.commit()
            require(apply(q, plan) == 0, 'Repeat changed data')
            require(build(capture(q), 'repeat') == [], 'New repair not idempotent')
            q.execute(f'UPDATE {BATCH} SET context_snapshot_json=%s,version=version+2 WHERE id=%s', (json.dumps(plan[0]['beforeContext'], ensure_ascii=False), plan[0]['id']))
            try:
                apply(q, plan)
            except RuntimeError as e:
                require('Target changed' in str(e), 'Unexpected stale-plan error')
            else:
                raise RuntimeError('Stale plan accepted')
            conn.rollback()
    finally:
        conn.rollback()
        for table in (BATCH, ACCOUNT):
            q.execute(f'DROP TEMPORARY TABLE {table}')
        conn.commit()


def connect(container):
    meta = json.loads(subprocess.check_output(['docker', 'inspect', container]))[0]
    cfg = dict(x.split('=', 1) for x in meta['Config']['Env'] if '=' in x)
    password = subprocess.check_output(['docker', 'exec', container, 'cat', cfg['MYSQL_ROOT_PASSWORD_FILE']]).decode().strip()
    port = meta['NetworkSettings']['Ports']['3306/tcp'][0]
    require(container == 'zsjos-mysql-1' and port['HostIp'] == '127.0.0.1' and cfg['MYSQL_DATABASE'] == 'zsjos', 'Unexpected database target')
    return pymysql.connect(host=port['HostIp'], port=int(port['HostPort']), user='root', password=password, database='zsjos', charset='utf8mb4', autocommit=False, cursorclass=pymysql.cursors.DictCursor)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--evidence-dir', type=Path, required=True)
    parser.add_argument('--apply', action='store_true')
    parser.add_argument('--approved-plan-sha256')
    args = parser.parse_args()
    marker = Path('/etc/zsjos/agent-environment')
    environment = os.getenv('ZSJOS_AGENT_ENV')
    if environment is None:
        environment = marker.read_text().strip() if marker.exists() else 'local'
    require(environment == 'test', 'Only test environment supported')
    os.umask(0o077)
    args.evidence_dir.mkdir(mode=0o700, parents=True, exist_ok=False)
    conn = connect('zsjos-mysql-1')
    try:
        with conn.cursor() as q:
            q.execute('SET NAMES utf8mb4')
            q.execute("SET time_zone='+08:00'")
            q.execute('SET SESSION innodb_lock_wait_timeout=10')
            timestamp = dt.datetime.now(dt.timezone(dt.timedelta(hours=8))).isoformat()
            before = capture(q)
            plan = build(before, timestamp)
            # Approval identity excludes capture time but covers full source/target preimages and fill values.
            approval_hash = digest({'before': before, 'targets': [{k: v for k, v in p.items() if k != 'afterContext'} for p in plan]})
            conn.rollback()
            persist(args.evidence_dir / 'before.json', before)
            persist(args.evidence_dir / 'plan.json', plan)
            rehearse(conn, q, before, plan)
            summary = {'mode': 'verified-only', 'activeBatches': len(before['batches']), 'targetBatches': len(plan), 'fields': sum(len(p['changes']) for p in plan), 'approvedPlanSha256': approval_hash, 'mysqlRehearsalPassed': True}
            if args.apply:
                require(args.approved_plan_sha256 == approval_hash, 'Reviewed plan/source changed; fresh approval required')
                locked = capture(q, lock=True)
                require(locked == before, 'Live state changed during rehearsal')
                require(apply(q, plan) == len(plan), 'Actual update count mismatch')
                after = verify(q, before, plan)
                persist(args.evidence_dir / 'after.json', after)
                conn.commit()
                verify(q, before, plan)
                require(apply(q, plan) == 0, 'Committed repeat changed data')
                conn.rollback()
                summary['mode'] = 'committed'
            persist(args.evidence_dir / 'result.json', summary)
            print(encoded(summary))
    finally:
        conn.rollback()
        conn.close()


if __name__ == '__main__':
    main()
