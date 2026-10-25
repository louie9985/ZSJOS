#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Evidence-only stage timing audit. Real --apply/--restore need exact-target approval.

No schema/ledger changes. Tenant 1 only. Default mode opens a read-only transaction.
Use MYSQL_HOST/PORT/USER/PASSWORD/DATABASE; credentials are never printed.
"""
import argparse
import copy
import datetime as dt
import hashlib
import json
import os
from pathlib import Path
import re

import pymysql

ZONE = dt.timezone(dt.timedelta(hours=8))
RECOVERY = '_stageTimingRecovery'
FIELDS = ('sStageEnteredAt', 'sStageDurationAsOf', 'sStageDurationDays', 'sStageTimingSource')
TABLES = {'accounts': 'zsjos_media_account', 'entries': 'zsjos_media_account_profile_entry',
          'batches': 'zsjos_content_review_batch'}


def require(ok, message):
    if not ok:
        raise RuntimeError(message)


def json_value(value):
    return json.loads(value) if isinstance(value, str) else value


def serialized(value):
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(',', ':'),
                      default=lambda x: {'$datetime': x.isoformat()} if isinstance(x, dt.datetime) else
                      {'$bytes': x.hex()} if isinstance(x, bytes) else str(x))


def digest(value):
    return hashlib.sha256(serialized(value).encode('utf-8')).hexdigest()


def millis(value):
    if isinstance(value, dt.datetime):
        return int(value.replace(tzinfo=ZONE).timestamp() * 1000)
    raise ValueError('invalid time')


def missing(value):
    return value is None or value == ''


def chronology(entries):
    """Reject incomplete/ambiguous chains rather than inventing an earlier start."""
    if not entries:
        return [], 'NO_DIAGNOSIS'
    if any(not isinstance(r.get('create_time'), dt.datetime) for r in entries):
        return [], 'INVALID_TIME'
    rows = sorted(entries, key=lambda r: (r['create_time'], r['id']))
    segments, previous_time, previous_id, previous_version = [], None, None, None
    try:
        for i, row in enumerate(rows):
            require(row.get('deleted', 0) in (0, False, b'\x00'), 'DELETED_DIAGNOSIS_IN_CHAIN')
            when = row['create_time']
            content = json_value(row['content'])
            stage = content.get('currentStage')
            require(isinstance(stage, str) and bool(stage.strip()), 'INVALID_STAGE')
            require(isinstance(when, dt.datetime), 'INVALID_TIME')
            require((i == 0) == (row['field_key'] == 'diagnosis_initial'), 'INCOMPLETE_INITIAL_CHAIN')
            require(row['field_key'] in ('diagnosis_initial', 'diagnosis_7d', 'diagnosis_14d', 'diagnosis_28d', 'adjustment_28d'), 'INVALID_DIAGNOSIS_TYPE')
            require(previous_time != when, 'AMBIGUOUS_SAME_TIME')
            require(previous_id is None or row['id'] > previous_id, 'CONFLICTING_ORDER')
            version = row.get('result_version')
            require(isinstance(version, int) and (previous_version is None or version > previous_version), 'CONFLICTING_VERSION')
            snapshots = json_value(row.get('snapshot_json'))
            require(isinstance(snapshots, list), 'MISSING_STAGE_EVIDENCE')
            saved = [r for r in snapshots if r.get('key') == 'stage']
            require(len(saved) == 1 and saved[0].get('value') == stage, 'CONFLICTING_STAGE_EVIDENCE')
            if not segments or segments[-1]['stage'] != stage:
                segments.append({'stage': stage, 'start': when, 'evidence': []})
            segments[-1]['evidence'].append(row['id'])
            previous_time, previous_id, previous_version = when, row['id'], version
    except (ValueError, TypeError, AttributeError, KeyError, RuntimeError) as error:
        return [], str(error) if isinstance(error, RuntimeError) else 'INVALID_DIAGNOSIS'
    return segments, None


def interval_at(segments, cutoff):
    if not isinstance(cutoff, dt.datetime):
        return None
    eligible = [s for s in segments if s['start'] <= cutoff]
    return eligible[-1] if eligible else None


def timing(segment, cutoff):
    return {'sStageEnteredAt': millis(segment['start']), 'sStageDurationAsOf': millis(cutoff),
            'sStageDurationDays': (cutoff.date() - segment['start'].date()).days,
            'sStageTimingSource': 'HISTORY_RECONSTRUCTED'}


def capture(cursor, lock=False):
    # Repeatable-read next-key locks also prevent diagnoses appearing mid-repair.
    state = {}
    for key, table in TABLES.items():
        suffix = " AND kind='DIAGNOSIS'" if key == 'entries' else ''
        deleted = '' if key == 'entries' else ' AND deleted=0'
        cursor.execute(f'SELECT * FROM {table} WHERE tenant_id=1{deleted}{suffix} ORDER BY id' + (' FOR UPDATE' if lock else ''))
        state[key] = list(cursor.fetchall())
    return state


def build(state, cutoff):
    changes, skipped, chains = [], [], {}
    accounts = {row['id']: row for row in state['accounts']}
    grouped = {}
    for row in state['entries']:
        grouped.setdefault(row['account_id'], []).append(row)
    for aid, account in accounts.items():
        chain, reason = chronology(grouped.get(aid, []))
        if chain and any(row['create_time'] > cutoff for row in grouped.get(aid, [])):
            chain, reason = [], 'FUTURE_DIAGNOSIS'
        chains[aid] = (chain, reason)
        if not missing(account.get('s_stage_entered_at')):
            continue
        if not chain or chain[-1]['stage'] != account.get('s_stage'):
            skipped.append({'kind': 'account', 'id': aid, 'reason': reason or 'CURRENT_STAGE_MISMATCH'})
            continue
        segment = chain[-1]
        try:
            values = json_value(account.get('detail_values_json')) or {}
            require(isinstance(values, dict), 'INVALID_ACCOUNT_VALUES')
            require(RECOVERY not in values, 'EXISTING_RECOVERY_MARKER')
        except (ValueError, RuntimeError) as error:
            skipped.append({'kind': 'account', 'id': aid, 'reason': str(error)})
            continue
        values = copy.deepcopy(values)
        values[RECOVERY] = {'stage': segment['stage'], 'enteredAt': millis(segment['start']),
                            'evidenceIds': segment['evidence']}
        changes.append({'kind': 'accounts', 'id': aid, 'before': account,
                        'set': {'s_stage_entered_at': segment['start'], 'detail_values_json': serialized(values)},
                        'evidenceIds': segment['evidence']})
    for batch in state['batches']:
        if batch.get('status') == 'DRAFT':
            continue
        if not isinstance(batch.get('submitted_at'), dt.datetime):
            skipped.append({'kind': 'batch', 'id': batch['id'], 'reason': 'MISSING_SUBMISSION_TIME'})
            continue
        try:
            context = json_value(batch.get('context_snapshot_json'))
            require(isinstance(context, dict), 'INVALID_CONTEXT')
            allowed = set(json_value(batch.get('account_ids_json')) or []) | {batch.get('account_id')}
            snapshots = context.get('accountSnapshots')
            if not isinstance(snapshots, list) or not snapshots:
                snapshots = [context.get('account')]
            require(all(isinstance(s, dict) for s in snapshots), 'INVALID_ACCOUNT_SNAPSHOTS')
            ids = [s.get('id') for s in snapshots]
            require(len(ids) == len(set(ids)), 'DUPLICATE_ACCOUNT_SNAPSHOTS')
            after, evidence = copy.deepcopy(context), set()
            targets = after.get('accountSnapshots')
            targets = targets if isinstance(targets, list) and targets else [after.get('account')]
            for snapshot in targets:
                aid = snapshot.get('id')
                if any(not missing(snapshot.get(f)) for f in FIELDS):
                    skipped.append({'kind': 'batch', 'id': batch['id'], 'accountId': aid, 'reason': 'EXISTING_TIMING_FIELDS'})
                    continue
                account = accounts.get(aid)
                chain, reason = chains.get(aid, ([], 'MISSING_ACCOUNT'))
                segment = interval_at(chain, batch['submitted_at'])
                reason = reason or ('ACCOUNT_SCOPE_MISMATCH' if aid not in allowed or account is None
                          or batch.get('student_person_id') is not None and account.get('student_person_id') != batch['student_person_id'] else None)
                if batch['submitted_at'] > cutoff:
                    reason = 'FUTURE_SUBMISSION'
                stage = snapshot.get('sStage') or snapshot.get('stageValue')
                if reason or segment is None or segment['stage'] != stage:
                    skipped.append({'kind': 'batch', 'id': batch['id'], 'accountId': aid, 'reason': reason or 'SNAPSHOT_STAGE_MISMATCH'})
                    continue
                # A diagnosis at the same DB-second as submission has no reliable cross-table ordering.
                if any(e['create_time'] == batch['submitted_at'] for e in grouped.get(aid, [])):
                    skipped.append({'kind': 'batch', 'id': batch['id'], 'accountId': aid, 'reason': 'AMBIGUOUS_SUBMISSION_TIME'})
                    continue
                fields = timing(segment, batch['submitted_at'])
                snapshot.update(fields)
                evidence.update(e['id'] for e in grouped.get(aid, []) if e['id'] in segment['evidence'] and e['create_time'] <= batch['submitted_at'])
                primary = after.get('account')
                if isinstance(primary, dict) and primary is not snapshot and primary.get('id') == aid:
                    require((primary.get('sStage') or primary.get('stageValue')) == stage, 'PRIMARY_STAGE_MISMATCH')
                    require(all(missing(primary.get(f)) for f in FIELDS), 'PRIMARY_TIMING_EXISTS')
                    primary.update(fields)
            if after != context:
                changes.append({'kind': 'batches', 'id': batch['id'], 'before': batch,
                                'set': {'context_snapshot_json': serialized(after)}, 'evidenceIds': sorted(evidence)})
        except (ValueError, TypeError, AttributeError, RuntimeError) as error:
            skipped.append({'kind': 'batch', 'id': batch['id'], 'reason': str(error) if isinstance(error, RuntimeError) else 'INVALID_CONTEXT'})
    return {'changes': changes, 'skipped': skipped}


def public_plan(plan):
    result = []
    for change in plan['changes']:
        item = {key: change[key] for key in ('kind', 'id', 'evidenceIds')}
        if change['kind'] == 'accounts':
            item['sStageEnteredAt'] = millis(change['set']['s_stage_entered_at'])
        else:
            before = json_value(change['before']['context_snapshot_json'])
            after = json_value(change['set']['context_snapshot_json'])
            item['timing'] = []
            for key in ('accountSnapshots', 'account'):
                old = before.get(key); new = after.get(key)
                old_rows = old if isinstance(old, list) else [old] if isinstance(old, dict) else []
                new_rows = new if isinstance(new, list) else [new] if isinstance(new, dict) else []
                for old_row, row in zip(old_rows, new_rows):
                    if old_row != row:
                        item['timing'].append({'path': key, 'accountId': row['id'], **{f: row.get(f) for f in FIELDS}})
        result.append(item)
    return {'changes': result, 'skipped': plan['skipped']}


def persist(path, value):
    # CLI creates a protected parent directory before storing sensitive preimages.
    fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(fd, 'w', encoding='utf-8') as handle:
        handle.write(serialized(value))
        handle.flush()
        os.fsync(handle.fileno())


def update(cursor, change, restore=False):
    table = TABLES[change['kind']]
    values = {key: change['before'][key] if restore else value for key, value in change['set'].items()}
    assignments = ','.join(f'`{key}`=%s' for key in values)
    cursor.execute(f'UPDATE {table} SET {assignments},version=version+1 WHERE id=%s AND tenant_id=1 AND deleted=0 AND version=%s',
                   (*values.values(), change['id'], change['expectedVersion']))
    require(cursor.rowcount == 1, 'TARGET_CHANGED')


def target_identity(connection):
    database = connection.db.decode() if isinstance(connection.db, bytes) else connection.db
    return {'host': connection.host, 'port': connection.port, 'database': database}


def verify_update(before, after, values, expected_version):
    require(after is not None and after['version'] == expected_version + 1, 'VERSION_POSTCONDITION_FAILED')
    for key, value in values.items():
        actual = after[key]
        require(json_value(actual) == json_value(value) if key.endswith('_json') else actual == value, 'POSTCONDITION_FAILED')
    for key, value in before.items():
        if key not in values and key not in ('version', 'update_time'):
            require(after[key] == value, 'UNRELATED_FIELD_CHANGED')


def apply_plan(connection, expected_state, plan, backup_path):
    with connection.cursor() as cursor:
        locked = capture(cursor, lock=True)
        require(digest(locked) == digest(expected_state), 'SOURCE_OR_TARGET_CHANGED')
        for change in plan['changes']:
            change['expectedVersion'] = change['before']['version']
        persist(backup_path, {'target': target_identity(connection),
                              'changes': plan['changes']})
        for change in plan['changes']:
            update(cursor, change)
        after = capture(cursor)
        lookup = {(k, row['id']): row for k in ('accounts', 'batches') for row in after[k]}
        for change in plan['changes']:
            row = lookup[(change['kind'], change['id'])]
            verify_update(change['before'], row, change['set'], change['expectedVersion'])
            change['afterDigest'] = digest(row)
        persist(backup_path.with_name(backup_path.stem + '-verified.json'), {'target': target_identity(connection), 'changes': plan['changes']})
    connection.commit()


def restore(connection, backup):
    target = target_identity(connection)
    require(backup.get('target') == target, 'WRONG_RESTORE_TARGET')
    with connection.cursor() as cursor:
        for change in backup['changes']:
            require(change['kind'] in ('accounts', 'batches'), 'INVALID_BACKUP_KIND')
            permitted = {'s_stage_entered_at', 'detail_values_json'} if change['kind'] == 'accounts' else {'context_snapshot_json'}
            require(set(change['set']) <= permitted, 'INVALID_BACKUP_FIELDS')
            cursor.execute(f"SELECT * FROM {TABLES[change['kind']]} WHERE id=%s AND tenant_id=1 AND deleted=0 FOR UPDATE", (change['id'],))
            row = cursor.fetchone()
            require(row is not None and digest(row) == change.get('afterDigest'), 'RESTORE_TARGET_CHANGED')
            change['expectedVersion'] = row['version']
            update(cursor, change, restore=True)
            cursor.execute(f"SELECT * FROM {TABLES[change['kind']]} WHERE id=%s AND tenant_id=1 AND deleted=0", (change['id'],))
            restored = cursor.fetchone()
            verify_update(row, restored, {key: change['before'][key] for key in change['set']}, row['version'])
    connection.commit()


def read_backup(path):
    def hook(obj):
        if set(obj) == {'$datetime'}:
            return dt.datetime.fromisoformat(obj['$datetime'])
        if set(obj) == {'$bytes'}:
            return bytes.fromhex(obj['$bytes'])
        return obj
    return json.loads(path.read_text(encoding='utf-8'), object_hook=hook)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True, help='new protected evidence directory')
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument('--apply', action='store_true')
    mode.add_argument('--restore', type=Path, help='verified backup from an approved apply')
    parser.add_argument('--approved-plan-sha256')
    parser.add_argument('--expected-database', required=True)
    args = parser.parse_args()
    database = os.environ['MYSQL_DATABASE']
    require(database == args.expected_database and bool(re.fullmatch(r'[A-Za-z0-9_-]+', database)), 'WRONG_DATABASE')
    os.umask(0o077)
    args.output.mkdir(mode=0o700, parents=True, exist_ok=False)
    if os.name == 'nt':
        import subprocess
        import getpass
        subprocess.run(['icacls', str(args.output), '/inheritance:r', '/grant:r', getpass.getuser() + ':(OI)(CI)F'], check=True, capture_output=True)
    connection = pymysql.connect(host=os.environ['MYSQL_HOST'], port=int(os.getenv('MYSQL_PORT', '3306')),
                                 user=os.environ['MYSQL_USER'], password=os.environ['MYSQL_PASSWORD'], database=database,
                                 charset='utf8mb4', autocommit=False, cursorclass=pymysql.cursors.DictCursor)
    try:
        with connection.cursor() as cursor:
            cursor.execute('SET NAMES utf8mb4')
            cursor.execute("SET time_zone='+08:00'")
            cursor.execute('SET SESSION innodb_lock_wait_timeout=10')
            cursor.execute('SET SESSION TRANSACTION ISOLATION LEVEL REPEATABLE READ')
            cursor.execute('START TRANSACTION READ ONLY')
            state = capture(cursor)
        cutoff = dt.datetime.now(ZONE).replace(tzinfo=None)
        plan = build(state, cutoff)
        approval = digest({'target': target_identity(connection), 'state': state, 'plan': plan})
        public = public_plan(plan)
        public.update({'target': target_identity(connection), 'sha256': approval, 'accountCandidates': sum(c['kind'] == 'accounts' for c in plan['changes']),
                       'batchCandidates': sum(c['kind'] == 'batches' for c in plan['changes'])})
        persist(args.output / 'audit.json', public)
        connection.rollback()
        if args.apply:
            require(args.approved_plan_sha256 == approval, 'APPROVED_PLAN_CHANGED')
            apply_plan(connection, state, plan, args.output / 'before.json')
        elif args.restore:
            backup = read_backup(args.restore)
            require(args.approved_plan_sha256 == digest(backup), 'APPROVED_RESTORE_CHANGED')
            persist(args.output / 'restore-before.json', backup)
            restore(connection, backup)
        print(serialized({'mode': 'apply' if args.apply else 'restore' if args.restore else 'read-only',
                          'accountCandidates': public['accountCandidates'], 'batchCandidates': public['batchCandidates'],
                          'skipped': len(plan['skipped']), 'sha256': approval}))
    finally:
        connection.rollback()
        connection.close()


if __name__ == '__main__':
    main()
