#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Authorized test-only, fixed-manifest self-source snapshot repair (252 rounds).

Prepare freezes private images and rehearses on connection-local MySQL tables.
Apply accepts that exact tested plan; verify is read-only. No migrations/ledgers,
Lead/Order mutations, BPM writes or inferred names. See the operation document.
"""
import argparse
import collections
import copy
import datetime as dt
import decimal
import hashlib
import json
import os
from pathlib import Path

from transfer_student_20261015 import connect, require

MARK = 'order-self-source-20261103'
SERVER = '31aace8b-a55a-11f1-8c2b-a284c2b554a0'
ROUND = 'zsjos_order_approval_round'
ORDER = 'zsjos_order'
LEAD = 'zsjos_lead'
SOURCES = {'sales_self_sourced': 155, 'education_self_sourced': 97}
SOURCE_FIELDS = ('id', 'tenant_id', 'deleted', 'lead_no', 'source_type',
                 'source_user_id', 'source_provider_recorded', 'source_provider_user_id',
                 'provider_owner_type', 'provider_owner_id', 'provider_owner_name_snapshot',
                 'contribution_user_id_snapshot', 'contribution_user_name_snapshot',
                 'submission_idempotency_key', 'submitted_at', 'create_time')
CANDIDATES = """
SELECT o.id order_id,o.lead_id,r.id round_id
FROM zsjos_order o JOIN zsjos_order_approval_round r
 ON r.id=o.current_approval_round_id AND r.tenant_id=o.tenant_id AND r.deleted=0
JOIN zsjos_lead l ON l.id=o.lead_id AND l.tenant_id=o.tenant_id AND l.deleted=0
WHERE o.tenant_id=1 AND o.deleted=0
 AND r.order_snapshot->>'$.leadProfile.sourceType' IN ('sales_self_sourced','education_self_sourced')
 AND NULLIF(NULLIF(TRIM(r.order_snapshot->>'$.leadProfile.sourceUserName'),''),'null') IS NULL
 AND COALESCE(r.order_snapshot->>'$.leadProfile.selfSourcedWithoutProvider','null')='null'
ORDER BY o.id
"""


def encode(value):
    def special(x):
        if isinstance(x, (dt.datetime, dt.date)):
            return {'$type': type(x).__name__, '$value': x.isoformat()}
        if isinstance(x, bytes):
            return {'$type': 'bytes', '$value': x.hex()}
        if isinstance(x, decimal.Decimal):
            return {'$type': 'decimal', '$value': str(x)}
        raise TypeError(type(x).__name__)
    return json.dumps(value, ensure_ascii=False, sort_keys=True, default=special)


def decode(raw):
    def special(x):
        kind = x.get('$type')
        if kind in ('datetime', 'date'):
            return getattr(dt, kind).fromisoformat(x['$value'])
        if kind == 'bytes':
            return bytes.fromhex(x['$value'])
        if kind == 'decimal':
            return decimal.Decimal(x['$value'])
        return x
    return json.loads(raw, object_hook=special)


def digest(value):
    return hashlib.sha256(encode(value).encode('utf-8')).hexdigest()


def save(path, value):
    # Exclusive creation prevents a later invocation replacing the audited plan/receipt.
    with path.open('x', encoding='utf-8') as f:
        f.write(encode(value) + '\n')
        f.flush()
        os.fsync(f.fileno())
    path.chmod(0o600)
    fd = os.open(path.parent, os.O_RDONLY | os.O_DIRECTORY)
    try:
        os.fsync(fd)
    finally:
        os.close(fd)


def rows(q, sql, args=()):
    q.execute(sql, args or None)
    return list(q.fetchall())


def select_ids(q, table, ids, lock=False, columns='*'):
    require(table in (ROUND, ORDER, LEAD, 'verify_' + ROUND), 'Invalid table')
    return rows(q, f'SELECT {columns} FROM {table} WHERE tenant_id=1 AND id IN ('
                + ','.join(['%s'] * len(ids)) + ') ORDER BY id'
                + (' FOR UPDATE NOWAIT' if lock else ''), sorted(ids))


def frozen(lead):
    return {k: lead[k] for k in SOURCE_FIELDS}


def valid_source(lead, profile):
    require(lead['tenant_id'] == 1 and lead['deleted'] == b'\x00', 'Lead unavailable')
    require(lead['source_type'] == profile['sourceType'] and lead['lead_no'] == profile['leadNo'],
            'Historical source/Lead reference differs')
    require(lead['source_provider_recorded'] == b'\x01', 'No explicit frozen selection')
    require(all(lead[k] is None for k in SOURCE_FIELDS if k.startswith(('provider_', 'contribution_'))
                or k == 'source_provider_user_id'), 'Conflicting provider/contribution evidence')
    require(profile.get('sourceSubjectId') in (None, lead['source_user_id']), 'Source actor differs')
    require(profile.get('sourceSubjectType') in (None, 'ADMIN'), 'Source subject differs')


def evidence(q, lead, round_row):
    """Original selection is frozen at creation, never inferred from today's owner.

    Imported Leads additionally require an explicit reviewed historical correction.
    Flow snapshots are corroboration; absence does not erase the creation-time flag.
    """
    receipts = rows(q, "SELECT * FROM zsjos_data_repair_backup WHERE table_name='zsjos_lead' "
                    "AND row_id=%s AND repair_key IN ('confirmed-provider-v308',"
                    "'lead-source-confirmed-v309-r2') ORDER BY id", (lead['id'],))
    proven = []
    for key in ('confirmed-provider-v308', 'lead-source-confirmed-v309-r2'):
        group = {r['column_name']: r for r in receipts if r['repair_key'] == key}
        if not group:
            continue
        require(set(group) == {'__before__', '__after__', '__provenance__'}, 'Incomplete source receipt')
        after = json.loads(group['__after__']['old_value'])
        require(after['source_type'] == lead['source_type'] and after['source_provider_recorded'] == 1
                and after['source_provider_user_id'] is None and after['provider_owner_id'] is None,
                'Historical source receipt conflicts')
        proven.append(key)
    key = lead['submission_idempotency_key'] or ''
    if 'legacy' in key:
        require(bool(proven), 'Imported source needs reviewed historical evidence')
        basis = 'reviewed_historical_correction'
    else:
        require(bool(key) and lead['submitted_at'] <= round_row['submitted_at'],
                'Original frozen submission unavailable')
        basis = 'original_frozen_submission_choice'
    flow = rows(q, "SELECT id,occurred_at,related_object_refs FROM zsjos_business_event "
                "WHERE tenant_id=1 AND deleted=0 AND aggregate_type='lead' AND aggregate_id=%s "
                "AND event_type='lead_flow_operation' AND occurred_at<=%s ORDER BY id",
                (lead['id'], round_row['submitted_at']))
    corroboration = []
    for event in flow:
        snapshot = json.loads(event['related_object_refs']).get('flowSnapshot', {})
        changes = {x['field']: x for x in snapshot.get('changes', [])}
        provider = changes.get('provider', {}).get('after', {})
        source = changes.get('sourceType', {}).get('after', {})
        if provider.get('state') == 'EMPTY' and source.get('code') == lead['source_type']:
            corroboration.append({'id': event['id'], 'sha256': digest(event)})
    return {'basis': basis, 'frozen_source': frozen(lead), 'historical_receipts': receipts,
            'corroborating_flow': corroboration}


def expected_round(before, when):
    after = copy.deepcopy(before)
    snapshot = json.loads(after['order_snapshot'])
    profile = snapshot['leadProfile']
    require(not profile.get('sourceUserName') and profile.get('selfSourcedWithoutProvider') is None,
            'Snapshot outside approved missing-evidence scope')
    profile['selfSourcedWithoutProvider'] = True
    after.update(order_snapshot=json.dumps(snapshot, ensure_ascii=False),
                 version=before['version'] + 1, updater=MARK, update_time=when)
    return after


def normalized(row):
    result = dict(row)
    result['order_snapshot'] = json.loads(result['order_snapshot'])
    return result


def capture_rounds(q, plan, table=ROUND, lock=False):
    result = select_ids(q, table, {c['before']['id'] for c in plan['changes']}, lock)
    require(len(result) == len(plan['changes']), 'Target round missing')
    return {r['id']: r for r in result}


def execute(q, plan, table=ROUND, limit=None, fail_at=None, restore=False):
    require(table in (ROUND, 'verify_' + ROUND), 'Invalid write table')
    # All preimages checked before the first UPDATE; live locks are NOWAIT.
    actual = capture_rounds(q, plan, table, lock=True)
    pending = []
    for change in plan['changes']:
        before, after = change['before'], change['after']
        start, end = (after, before) if restore else (before, after)
        current = actual[before['id']]
        if normalized(current) == normalized(end):
            continue
        require(normalized(current) == normalized(start), 'Round changed after plan; stop')
        pending.append((current, end))
    for index, (current, end) in enumerate(pending[:limit] if limit else pending, 1):
        q.execute(f'UPDATE {table} SET order_snapshot=%s,version=%s,updater=%s,update_time=%s '
                  'WHERE tenant_id=1 AND id=%s AND deleted=0 AND version=%s',
                  (end['order_snapshot'], end['version'], end['updater'], end['update_time'],
                   end['id'], current['version']))
        require(q.rowcount == 1, 'Unexpected affected-row count')
        if index == fail_at:
            raise RuntimeError('injected_failure')
    checked = capture_rounds(q, plan, table)
    for current, end in (pending[:limit] if limit else pending):
        require(normalized(checked[end['id']]) == normalized(end), 'Postimage mismatch')
    return min(limit, len(pending)) if limit else len(pending)


def check_hex(q, plan, table=ROUND):
    checked = rows(q, f"SELECT id,JSON_TYPE(order_snapshot->'$.leadProfile.selfSourcedWithoutProvider') typ,"
                   "order_snapshot->>'$.leadProfile.selfSourcedWithoutProvider' flag,"
                   f"HEX(order_snapshot->>'$.leadProfile.sourceLabel') label_hex FROM {table} "
                   'WHERE tenant_id=1 AND id IN (' + ','.join(['%s'] * len(plan['changes'])) + ')',
                   [x['before']['id'] for x in plan['changes']])
    expected = {x['before']['id']: json.loads(x['before']['order_snapshot'])['leadProfile']['sourceLabel']
                for x in plan['changes']}
    require(len(checked) == 252, 'HEX verification scope differs')
    for row in checked:
        require(row['typ'] == 'BOOLEAN' and row['flag'] == 'true', 'Wrong JSON boolean')
        require(row['label_hex'] == expected[row['id']].encode('utf-8').hex().upper(), 'UTF-8 mismatch')


def rehearsal(c, q, plan):
    table = 'verify_' + ROUND
    q.execute(f'CREATE TEMPORARY TABLE {table} LIKE {ROUND}')
    columns = list(plan['changes'][0]['before'])
    q.executemany(f"INSERT INTO {table} ({','.join('`'+k+'`' for k in columns)}) VALUES ("
                  + ','.join(['%s'] * len(columns)) + ')',
                  [[x['before'][k] for k in columns] for x in plan['changes']])
    c.commit()
    try:
        execute(q, plan, table, fail_at=7)
    except RuntimeError as exc:
        require(str(exc) == 'injected_failure', 'Unexpected rehearsal failure')
        c.rollback()
    else:
        raise RuntimeError('Failure injection not reached')
    require(all(normalized(capture_rounds(q, plan, table)[x['before']['id']]) == normalized(x['before'])
                for x in plan['changes']), 'Rollback left partial data')
    require(execute(q, plan, table, limit=7) == 7, 'Partial fixture failed')
    c.commit()
    require(execute(q, plan, table) == 245, 'Partial recovery failed')
    c.commit()
    require(execute(q, plan, table) == 0, 'Replay wrote data')
    check_hex(q, plan, table)
    c.rollback()
    q.execute(f'UPDATE {table} SET version=version+1 WHERE id=%s', (plan['changes'][0]['before']['id'],))
    try:
        execute(q, plan, table)
    except RuntimeError:
        c.rollback()
    else:
        raise RuntimeError('Concurrent-edit guard failed')
    require(execute(q, plan, table, restore=True) == 252, 'Restore failed')
    c.commit()
    require(execute(q, plan, table) == 252, 'Initial apply after restore failed')
    check_hex(q, plan, table)
    c.commit()
    return dict(initial=True, repeat=True, rollback=True, partial_recovery=True,
                conflicting_edit_rejected=True, restore=True, json_boolean=True, utf8_hex=True)


def prepare(c, q, path):
    q.execute('START TRANSACTION WITH CONSISTENT SNAPSHOT, READ ONLY')
    candidates = rows(q, CANDIDATES)
    require(len(candidates) == 252, 'Reviewed candidate count changed; do not expand scope')
    orders = {r['id']: r for r in select_ids(q, ORDER, {x['order_id'] for x in candidates})}
    leads = {r['id']: r for r in select_ids(q, LEAD, {x['lead_id'] for x in candidates})}
    rounds = {r['id']: r for r in select_ids(q, ROUND, {x['round_id'] for x in candidates})}
    require(len(leads) == 192 and len(rounds) == 252, 'Reviewed identity counts changed')
    now = rows(q, 'SELECT NOW() t')[0]['t']
    changes = []
    for x in candidates:
        before, lead, order = rounds[x['round_id']], leads[x['lead_id']], orders[x['order_id']]
        require(before['order_id'] == order['id'], 'Approval/order mismatch')
        profile = json.loads(before['order_snapshot'])['leadProfile']
        valid_source(lead, profile)
        changes.append(dict(before=before, after=expected_round(before, now), order=order,
                            evidence=evidence(q, lead, before)))
    require(dict(collections.Counter(x['evidence']['frozen_source']['source_type'] for x in changes)) == SOURCES,
            'Reviewed source counts changed')
    c.rollback()
    plan = dict(repair_key=MARK, server_uuid=SERVER, database='zsjos', tenant_id=1,
                created_at=now, changes=changes)
    save(path / 'plan.json', plan)
    checks = rehearsal(c, q, plan)
    save(path / 'rehearsal.json', dict(plan_sha256=digest(plan), checks=checks,
                                     script_sha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest()))
    print(json.dumps({'prepared': len(changes), 'source_counts': SOURCES,
                      'evidence': dict(collections.Counter(x['evidence']['basis'] for x in changes)),
                      'controlled_mysql': checks}))


def verify(q, plan):
    actual = capture_rounds(q, plan)
    require(all(normalized(actual[x['after']['id']]) == normalized(x['after']) for x in plan['changes']),
            'Committed postimage differs')
    check_hex(q, plan)


def apply(c, q, path, plan):
    require(not (path / 'committed.json').exists(), 'Already committed; use verify')
    q.execute('SET TRANSACTION ISOLATION LEVEL READ COMMITTED')
    c.begin()
    # Lock only reviewed objects, promptly refuse contention instead of stopping a service.
    orders = select_ids(q, ORDER, {x['order']['id'] for x in plan['changes']}, lock=True)
    expected_orders = sorted((x['order'] for x in plan['changes']), key=lambda x: x['id'])
    require(orders == expected_orders, 'Order state changed; re-review required')
    capture_rounds(q, plan, lock=True)
    leads = {r['id']: r for r in select_ids(q, LEAD,
             {x['order']['lead_id'] for x in plan['changes']}, lock=True)}
    for x in plan['changes']:
        lead = leads[x['order']['lead_id']]
        require(frozen(lead) == x['evidence']['frozen_source'], 'Frozen source evidence changed')
        valid_source(lead, json.loads(x['before']['order_snapshot'])['leadProfile'])
        # Historical receipt changes invalidate the approved provenance.
        require(evidence(q, lead, x['before']) == x['evidence'], 'Historical evidence changed')
    protected = {t: rows(q, f'SELECT * FROM {t} ORDER BY id') for t in (ORDER, ROUND)}
    save(path / 'precommit-protection.json', {t: digest(v) for t, v in protected.items()})
    changed = execute(q, plan)
    require(changed in (0, 252), 'Unexpected partial live application')
    verify(q, plan)
    after_rounds = rows(q, f'SELECT * FROM {ROUND} ORDER BY id')
    targets = {x['before']['id'] for x in plan['changes']}
    require([r for r in protected[ROUND] if r['id'] not in targets] ==
            [r for r in after_rounds if r['id'] not in targets], 'Excluded round changed')
    require(rows(q, f'SELECT * FROM {ORDER} ORDER BY id') == protected[ORDER], 'Order changed')
    require(select_ids(q, LEAD, set(leads)) == sorted(leads.values(), key=lambda x: x['id']), 'Lead changed')
    require(execute(q, plan) == 0, 'In-transaction replay wrote data')
    save(path / 'pending-commit.json', dict(plan_sha256=digest(plan), changed=changed,
         protected_orders=len(protected[ORDER]), excluded_rounds=len(after_rounds)-252,
         leads_unchanged=len(leads), full_after_images_verified=True, utf8_hex=True))
    c.commit()
    # A new connection independently checks committed data.
    readback = connect('zsjos-mysql-1')
    try:
        with readback.cursor() as check:
            check.execute('START TRANSACTION READ ONLY')
            verify(check, plan)
        readback.rollback()
    finally:
        readback.close()
    save(path / 'committed.json', dict(plan_sha256=digest(plan), verified_rounds=252,
                                     changed=changed, independent_readback=True))
    print('PASS committed and independently verified 252 snapshots; excluded rounds, Orders and Leads preserved')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('mode', choices=('prepare', 'apply', 'verify'))
    parser.add_argument('--evidence-dir', type=Path, required=True)
    args = parser.parse_args()
    env = os.getenv('ZSJOS_AGENT_ENV')
    if env is None:
        marker = Path('/etc/zsjos/agent-environment')
        env = marker.read_text().strip() if marker.exists() else 'local'
    require(env == 'test', 'Only authorized test environment supported')
    os.umask(0o077)
    path = args.evidence_dir.resolve()
    require(str(path).startswith('/opt/zsjos-runtime/backups/order-self-source-20261103/'), 'Private evidence path required')
    if args.mode == 'prepare':
        path.mkdir(mode=0o700, parents=True, exist_ok=False)
    c = connect('zsjos-mysql-1')
    try:
        with c.cursor() as q:
            q.execute('SET NAMES utf8mb4')
            q.execute("SET time_zone='+08:00'")
            q.execute('SET SESSION innodb_lock_wait_timeout=2')
            require(rows(q, 'SELECT @@server_uuid uuid,DATABASE() db')[0] ==
                    {'uuid': SERVER, 'db': 'zsjos'}, 'Database target differs')
            c.rollback()
            if args.mode == 'prepare':
                prepare(c, q, path)
                return
            plan = decode((path / 'plan.json').read_text(encoding='utf-8'))
            receipt = decode((path / 'rehearsal.json').read_text(encoding='utf-8'))
            require(plan['server_uuid'] == SERVER and plan['repair_key'] == MARK and
                    digest(plan) == receipt['plan_sha256'] and all(receipt['checks'].values()), 'Untested plan')
            require(receipt['script_sha256'] == hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
                    'Writer changed after rehearsal')
            if args.mode == 'apply':
                apply(c, q, path, plan)
            else:
                q.execute('START TRANSACTION READ ONLY')
                verify(q, plan)
                print('PASS read-only verification of all 252 full postimages, JSON boolean and UTF-8 HEX')
    finally:
        c.rollback()
        c.close()


if __name__ == '__main__':
    try:
        main()
    except Exception as error:
        # Database exception payloads and JSON documents can contain personal data.
        print('Stopped: ' + (str(error) if type(error) is RuntimeError else type(error).__name__))
        raise SystemExit(1)
