#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Evidence-based current Lead identity repair; no owner/history/ledger changes.

Test tenant 1 only. prepare -> test -> apply priority -> apply remaining -> verify.
Private plans contain technical before-images and hashes, never customer/contact data.
See docs/operations/lead-owner-identity-backfill-20261020.md for evidence and rollback.
"""
import argparse
import collections
import datetime as dt
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess

from transfer_student_20261015 import connect, require

MARK = 'lead-owner-identity-20261020'
PRIORITY = 'LD202609180039'
SOURCE = 'oldcrm_scratch'
LEAD = 'zsjos_lead'
HISTORY = 'zsjos_lead_assignment_history'
BASELINE = 'legacy assignment history baseline'
RECEIPTS = {'assign_accept': 'accept', 'sales_manual_entry': 'accept',
            'pool_claim': 'claim', 'supervisor_transfer': 'transfer'}
FIELDS = ('id', 'lead_no', 'tenant_id', 'deleted', 'owner_user_id', 'owner_identity',
          'assignment_status', 'status', 'ownership_started_at',
          'current_assignment_history_id', 'version', 'updater', 'update_time')
WRITE_FIELDS = ('owner_identity', 'version', 'updater', 'update_time')


def encode(value):
    def custom(x):
        if isinstance(x, (dt.datetime, dt.date)):
            return {'$type': type(x).__name__, '$value': x.isoformat()}
        if isinstance(x, bytes):
            return {'$type': 'bytes', '$value': x.hex()}
        # Protected full-row hashes may contain decimal columns; no such fields are written.
        import decimal
        if isinstance(x, decimal.Decimal):
            return {'$type': 'Decimal', '$value': str(x)}
        raise TypeError(type(x).__name__)
    return json.dumps(value, default=custom, ensure_ascii=False, sort_keys=True, indent=2)


def decode(raw):
    def custom(x):
        if set(x) == {'$type', '$value'}:
            if x['$type'] in ('datetime', 'date'):
                return getattr(dt, x['$type']).fromisoformat(x['$value'])
            if x['$type'] == 'bytes':
                return bytes.fromhex(x['$value'])
            if x['$type'] == 'Decimal':
                import decimal
                return decimal.Decimal(x['$value'])
        return x
    return json.loads(raw, object_hook=custom)


def digest(value):
    return hashlib.sha256(encode(value).encode('utf-8')).hexdigest()


def save(path, value):
    fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(fd, 'w', encoding='utf-8') as f:
        f.write(encode(value)); f.flush(); os.fsync(f.fileno())


def rows(q, sql, args=()):
    q.execute(sql, args or None)
    return list(q.fetchall())


def active(r):
    return r['deleted'] in (0, False, b'\x00')


def close_time(a, b):
    # Legacy microseconds became DATETIME(0); both truncation and rounding occurred.
    return a is not None and b is not None and abs((a-b).total_seconds()) < 1


def protected(row):
    return digest({k: v for k, v in row.items() if k not in WRITE_FIELDS})


def source_data(q):
    data = {}
    data['leads'] = rows(q, f'SELECT id,lead_no,assigned_sales_id,assignment_started_at,assignment_status '
                         f'FROM {SOURCE}.leads_lead ORDER BY id')
    data['logs'] = rows(q, f'SELECT id,lead_id,assigned_sales_id,to_sales_id_snapshot,'
                        f'from_sales_id_snapshot,assignment_status,assignment_status_after,created_at '
                        f'FROM {SOURCE}.assignment_log ORDER BY id')
    data['receipts'] = rows(q, f'SELECT id,lead_id,event_type,sales_id,source_log_id,occurred_at '
                            f'FROM {SOURCE}.dashboard_lead_sales_receipt_event ORDER BY id')
    data['academic'] = rows(q, f'SELECT id,lead_id,owner_id FROM {SOURCE}.academic_customer ORDER BY id')
    # Exact account identity mapping only; a non-unique mapping is never resolved using MAX.
    # Phone values remain inside MySQL and are never included in the plan/output.
    data['mapping'] = rows(q, f"SELECT p.id legacy_staff,COUNT(DISTINCT nu.id) matches,MIN(nu.id) mapped "
        f"FROM {SOURCE}.employees_employee_profile p JOIN {SOURCE}.accounts_user u ON u.id=p.actor_id "
        "LEFT JOIN zsjos.system_users nu ON nu.tenant_id=1 AND nu.deleted=0 AND COALESCE(u.phone,'')<>'' "
        "AND (nu.mobile COLLATE utf8mb4_unicode_ci=u.phone COLLATE utf8mb4_unicode_ci "
        "OR nu.username COLLATE utf8mb4_unicode_ci=u.phone COLLATE utf8mb4_unicode_ci) GROUP BY p.id ORDER BY p.id")
    return data


def indexed(data):
    result = {'leads': collections.defaultdict(list), 'logs': {},
              'receipts': collections.defaultdict(list), 'academic': set(), 'mapping': {}}
    for r in data['leads']: result['leads'][r['lead_no']].append(r)
    for r in data['logs']: result['logs'][r['id']] = r
    for r in data['receipts']: result['receipts'][r['lead_id']].append(r)
    for r in data['academic']: result['academic'].add(r['lead_id'])
    for r in data['mapping']:
        if r['matches'] == 1: result['mapping'][r['legacy_staff']] = r['mapped']
    return result


def classify(lead, history, source):
    def reject(reason): return {'decision': 'unresolved', 'reason': reason}
    if not active(lead) or lead['tenant_id'] != 1 or lead['assignment_status'] != 'owned' \
            or lead['owner_identity'] is not None or lead['owner_user_id'] is None:
        return reject('outside_missing_owned_scope')
    if lead['version'] is None: return reject('missing_version')
    history = sorted((h for h in history if active(h)), key=lambda h: (h['occurred_at'], h['id']))
    if any(h['tenant_id'] != 1 or h['lead_id'] != lead['id'] for h in history):
        return reject('history_identity_conflict')
    # Ignore only the exact known import baseline; unknown "other" events remain barriers.
    transitions = [h for h in history if not (h['action_type'] == 'other'
        and h['reason'] == BASELINE and (h['creator'] or '').startswith('legacy-')
        and h['from_owner_user_id'] is None and h['to_owner_user_id'] in (None, lead['owner_user_id']))]
    if not transitions: return reject('missing_actual_ownership_event')
    h = transitions[-1]
    if h['action_type'] not in ('accept', 'claim', 'transfer'):
        return reject('latest_event_not_ownership')
    if h['to_owner_user_id'] != lead['owner_user_id']: return reject('latest_owner_mismatch')
    if not close_time(h['occurred_at'], lead['ownership_started_at']):
        return reject('ownership_cycle_time_mismatch')
    same_time = [r for r in transitions if r['occurred_at'] == h['occurred_at']]
    if any(r['action_type'] not in ('accept', 'claim', 'transfer')
           or r['to_owner_user_id'] != lead['owner_user_id'] for r in same_time):
        return reject('same_time_conflicting_transitions')
    linked = next((r for r in history if r['id'] == lead['current_assignment_history_id']), None)
    if linked is None: return reject('missing_current_assignment_link')
    if linked['id'] != h['id'] and linked in transitions:
        return reject('current_assignment_link_conflict')
    if h['owner_identity_snapshot'] not in (None, 'sales', 'education'):
        return reject('invalid_history_identity')
    if h['owner_identity_snapshot'] is not None:
        return {'decision': 'confirmed', 'identity': h['owner_identity_snapshot'],
                'reason': 'current_cycle_identity_snapshot', 'history_id': h['id']}
    old = source['leads'].get(lead['lead_no'], [])
    if len(old) != 1: return reject('missing_or_duplicate_legacy_lead')
    old = old[0]
    if old['id'] in source['academic']: return reject('legacy_academic_overlap')
    if source['mapping'].get(old['assigned_sales_id']) != lead['owner_user_id']:
        return reject('legacy_owner_mapping_mismatch')
    if old['assignment_status'] not in ('auto_assigned', 'manual_assigned', 'claimed', 'transferred'):
        return reject('legacy_not_owned')
    if not close_time(old['assignment_started_at'], lead['ownership_started_at']):
        return reject('legacy_ownership_cycle_mismatch')
    reason = h['reason'] or ''
    receipt_type = reason.removeprefix('旧库接收事件回填:') if reason.startswith('旧库接收事件回填:') else None
    log_match = re.fullmatch(r'legacy-assignment-log-(\d+) (.+)', reason)
    evidence = {'history_id': h['id'], 'legacy_lead_id': old['id'], 'legacy_staff_id': old['assigned_sales_id']}
    if receipt_type in RECEIPTS and RECEIPTS[receipt_type] == h['action_type'] \
            and h['creator'] == 'legacy-receipt-backfill-20260928':
        receipts = [r for r in source['receipts'][old['id']]
                    if r['event_type'] == receipt_type and r['sales_id'] == old['assigned_sales_id']
                    and close_time(r['occurred_at'], h['occurred_at'])]
        if len(receipts) != 1: return reject('legacy_receipt_not_unique_or_missing')
        evidence['legacy_receipt_id'] = receipts[0]['id']
        kind = 'legacy_sales_receipt'
    elif log_match and (h['creator'] or '').startswith('legacy-'):
        log = source['logs'].get(int(log_match[1]))
        if not log or log['lead_id'] != old['id'] or log['assigned_sales_id'] != old['assigned_sales_id'] \
                or log['to_sales_id_snapshot'] != old['assigned_sales_id'] \
                or not close_time(log['created_at'], h['occurred_at']) \
                or log['assignment_status_after'] not in ('auto_assigned', 'manual_assigned', 'claimed', 'transferred'):
            return reject('legacy_log_mismatch')
        receipts = [r for r in source['receipts'][old['id']]
                    if RECEIPTS.get(r['event_type']) == h['action_type'] and r['sales_id'] == old['assigned_sales_id']
                    and close_time(r['occurred_at'], h['occurred_at'])]
        if len(receipts) != 1: return reject('legacy_log_receipt_not_unique_or_missing')
        evidence.update(legacy_log_id=log['id'], legacy_receipt_id=receipts[0]['id'])
        kind = 'legacy_sales_log_and_receipt'
    else:
        return reject('unproven_event_provenance')
    # A later old-system release/recycle/transfer must never be bypassed.
    later = [r for r in source['logs'].values() if r['lead_id'] == old['id']
             and r['created_at'] >= h['occurred_at'] + dt.timedelta(seconds=1)]
    if later: return reject('later_legacy_transition')
    return dict(evidence, decision='confirmed', identity='sales', reason=kind)


def histories(q, lead_id, table=HISTORY, lock=False):
    require(table in (HISTORY, 'verify_identity_history'), 'Unexpected history table')
    return rows(q, f'SELECT * FROM {table} WHERE tenant_id=1 AND lead_id=%s ORDER BY id'
                + (' FOR UPDATE' if lock else ''), (lead_id,))


def prepare(q):
    source = source_data(q)
    index = indexed(source)
    leads = rows(q, "SELECT * FROM zsjos_lead WHERE tenant_id=1 AND deleted=0 "
                 "AND assignment_status='owned' AND owner_identity IS NULL ORDER BY lead_no")
    all_history = rows(q, "SELECT h.* FROM zsjos_lead_assignment_history h JOIN zsjos_lead l "
        "ON l.id=h.lead_id AND l.tenant_id=h.tenant_id WHERE l.tenant_id=1 AND l.deleted=0 "
        "AND l.assignment_status='owned' AND l.owner_identity IS NULL ORDER BY h.id")
    grouped = collections.defaultdict(list)
    for h in all_history: grouped[h['lead_id']].append(h)
    now = rows(q, 'SELECT NOW() now')[0]['now']
    items = []
    for lead in leads:
        history = grouped[lead['id']]
        result = classify(lead, history, index)
        before = {f: lead[f] for f in FIELDS}
        item = {'before': before, 'protected_sha256': protected(lead),
                'history_sha256': digest(history), 'result': result}
        if result['decision'] == 'confirmed':
            item['after'] = dict(before, owner_identity=result['identity'], version=lead['version']+1,
                                 updater=MARK, update_time=now)
        items.append(item)
    return {'marker': MARK, 'environment': 'test', 'tenant': 1, 'source': SOURCE,
            'source_sha256': digest(source), 'prepared_at': now,
            'server_uuid': rows(q, 'SELECT @@server_uuid uuid')[0]['uuid'], 'items': items}


def apply_one(q, item, source_index, table=LEAD, history_table=HISTORY, restore=False, classifier=None):
    require(table in (LEAD, 'verify_identity_lead'), 'Unexpected Lead table')
    require(item['result']['decision'] == 'confirmed', 'Unresolved identity cannot be written')
    found = rows(q, f'SELECT * FROM {table} WHERE tenant_id=1 AND id=%s FOR UPDATE', (item['before']['id'],))
    require(len(found) == 1, 'Lead disappeared')
    actual = found[0]
    before, after = (item['after'], item['before']) if restore else (item['before'], item['after'])
    require(protected(actual) == item['protected_sha256'], 'Protected Lead fields changed')
    history = histories(q, actual['id'], history_table, lock=True)
    require(digest(history) == item['history_sha256'], 'Ownership history changed')
    actual_fields = {f: actual[f] for f in FIELDS}
    if actual_fields == after: return 0
    require(actual_fields == before, 'Lead changed since preparation; refuse overwrite')
    if not restore:
        require((classifier or classify)(actual, history, source_index) == item['result'], 'Identity evidence changed')
    q.execute(f'UPDATE {table} SET owner_identity=%s,version=%s,updater=%s,update_time=%s '
              'WHERE id=%s AND tenant_id=1 AND deleted=0 AND owner_identity <=> %s '
              'AND owner_user_id=%s AND assignment_status=%s AND version=%s',
              tuple(after[f] for f in WRITE_FIELDS) + (before['id'], before['owner_identity'],
              before['owner_user_id'], before['assignment_status'], before['version']))
    require(q.rowcount == 1, 'Concurrent identity update')
    check = rows(q, f'SELECT * FROM {table} WHERE tenant_id=1 AND id=%s', (before['id'],))[0]
    require(protected(check) == item['protected_sha256'] and {f: check[f] for f in FIELDS} == after,
            'Postcondition mismatch')
    return 1


def summary(plan):
    counts = collections.Counter((r['result']['decision'], r['result']['reason']) for r in plan['items'])
    return {'scanned': len(plan['items']), 'confirmed': sum(r['result']['decision']=='confirmed' for r in plan['items']),
            'categories': {':'.join(k): v for k, v in sorted(counts.items())},
            'priority': [r['result'] for r in plan['items'] if r['before']['lead_no']==PRIORITY]}


def validate_target(q):
    marker = Path('/etc/zsjos/agent-environment')
    environment = os.environ.get('ZSJOS_AGENT_ENV')
    if environment is None: environment = marker.read_text().strip() if marker.exists() else 'local'
    require(environment == 'test', 'Only the authorized test environment is supported')
    pid = subprocess.check_output(['systemctl', 'show', 'zsjos-backend.service', '-p', 'MainPID', '--value'], text=True).strip()
    env = dict(x.split('=', 1) for x in (Path('/proc')/pid/'environ').read_bytes().decode().split('\0') if '=' in x)
    require(env.get('ZSJOS_DB_HOST') == '127.0.0.1' and env.get('ZSJOS_DB_PORT') == '3306'
            and env.get('ZSJOS_DB_NAME') == 'zsjos', 'Runtime datasource changed')
    require(rows(q, 'SELECT DATABASE() db')[0]['db'] == 'zsjos', 'Unexpected database')


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('mode', choices=['prepare', 'apply', 'verify'])
    p.add_argument('--plan', type=Path, required=True)
    p.add_argument('--sha256')
    p.add_argument('--group', choices=['priority', 'remaining'], default='priority')
    p.add_argument('--report', type=Path)
    args = p.parse_args()
    os.umask(0o077)
    c = connect('zsjos-mysql-1')
    try:
        with c.cursor() as q:
            q.execute('SET NAMES utf8mb4'); q.execute("SET time_zone='+08:00'")
            q.execute('SET SESSION innodb_lock_wait_timeout=5')
            validate_target(q)
            if args.mode == 'prepare':
                q.execute('START TRANSACTION READ ONLY')
                plan = prepare(q); c.rollback()
                args.plan.parent.mkdir(parents=True, mode=0o700, exist_ok=True)
                save(args.plan, plan)
                print(encode(dict(summary(plan), sha256=digest(plan))))
                return
            plan = decode(args.plan.read_text(encoding='utf-8'))
            require(plan['marker'] == MARK and plan['environment'] == 'test' and plan['tenant'] == 1
                    and plan['source'] == SOURCE, 'Plan scope mismatch')
            require(rows(q, 'SELECT @@server_uuid uuid')[0]['uuid'] == plan['server_uuid'], 'Server changed')
            require(args.sha256 == digest(plan), 'Plan checksum required/mismatch')
            q.execute('START TRANSACTION READ ONLY')
            source = source_data(q)
            require(digest(source) == plan['source_sha256'], 'Source snapshot/account mapping changed')
            index = indexed(source); c.rollback()
            report = []
            items = [r for r in plan['items'] if r['result']['decision']=='confirmed']
            if args.mode == 'apply':
                items = [r for r in items if (r['before']['lead_no']==PRIORITY) == (args.group=='priority')]
                require(args.report is not None and not args.report.exists(), 'New report path required')
                for item in items:
                    try:
                        c.begin()
                        changed = apply_one(q, item, index)
                        c.commit()
                        # A fresh transaction reads the committed state, not only affected-row counts.
                        c.begin()
                        require(apply_one(q, item, index) == 0, 'Committed readback failed')
                        c.rollback()
                        report.append({'lead_no': item['before']['lead_no'], 'changed': changed, 'state': 'verified'})
                    except Exception as exc:
                        c.rollback()
                        report.append({'lead_no': item['before']['lead_no'], 'state': 'refused', 'reason': type(exc).__name__ + ': ' + str(exc)})
                save(args.report, report)
                print(encode({'selected':len(items), 'changed':sum(x.get('changed',0) for x in report),
                              'states':dict(collections.Counter(x['state'] for x in report))}))
            else:
                q.execute('START TRANSACTION READ ONLY')
                for item in plan['items']:
                    actual = rows(q, 'SELECT * FROM zsjos_lead WHERE tenant_id=1 AND id=%s', (item['before']['id'],))
                    expected = item.get('after', item['before'])
                    ok = len(actual)==1 and {f:actual[0][f] for f in FIELDS}==expected \
                         and protected(actual[0])==item['protected_sha256'] \
                         and digest(histories(q, expected['id']))==item['history_sha256']
                    report.append({'lead_no':expected['lead_no'], 'state':'verified' if ok else 'changed_or_unapplied'})
                c.rollback()
                if args.report: save(args.report, report)
                print(encode({'states':dict(collections.Counter(x['state'] for x in report)), 'plan':summary(plan)}))
    finally:
        c.rollback(); c.close()


if __name__ == '__main__': main()
