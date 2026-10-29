#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Authorized 11-Lead correction; see docs/operations/lead-original-public-sea-20261029.md."""
import argparse
import datetime as dt
import decimal
import hashlib
import json
import os
from pathlib import Path
import subprocess

import pymysql

NOS = tuple('LD202609170073 LD202609180025 LD202609180050 LD202609180087 '
            'LD202609190032 LD202609190035 LD202609190059 LD202609190121 '
            'LD202609190122 LD202609190140 LD202609190220'.split())
MARKER = 'restore-lead-public-sea-20261029'
REASON = '按旧系统判有效及释放公海记录纠正历史修复；恢复有效、公海待分配和进行中商机'
POOL_REASON = '历史公海状态恢复；原释放时间取旧系统记录，本事件时间为本次修复时间'
OLD_REASON = '经业务确认修复统一回收修复覆盖的无效状态；保留原判定时间及无效说明'
LEAD, OPP, CYCLE, POOL_EVENT, EVENT = TABLES = (
    'zsjos_lead', 'zsjos_opportunity', 'zsjos_lead_public_sea_pool_cycle',
    'zsjos_lead_public_sea_pool_event', 'zsjos_business_event')


def require(ok, message):
    if not ok:
        raise RuntimeError(message)


def encoded(value):
    def default(item):
        if isinstance(item, (dt.datetime, dt.date)):
            return item.isoformat()
        if isinstance(item, bytes):
            return {'hex': item.hex()}
        if isinstance(item, decimal.Decimal):
            return str(item)
        raise TypeError(type(item).__name__)
    return json.dumps(value, ensure_ascii=False, sort_keys=True, indent=2, default=default)


def save(path, value):
    with path.open('x', encoding='utf-8') as handle:
        handle.write(encoded(value))
        handle.flush()
        os.fsync(handle.fileno())


def rows(cur, sql, args=()):
    cur.execute(sql, args)
    return list(cur.fetchall())


def ph(values):
    return ','.join(['%s'] * len(values))


def connect():
    env = os.getenv('ZSJOS_AGENT_ENV')
    marker = Path('/etc/zsjos/agent-environment')
    if env is None:
        env = marker.read_text().strip() if marker.exists() else 'local'
    require(env == 'test', 'Only the authorized test environment is supported')
    metadata = json.loads(subprocess.check_output(['docker', 'inspect', 'zsjos-mysql-1']))[0]
    config = dict(x.split('=', 1) for x in metadata['Config']['Env'] if '=' in x)
    password = config.get('MYSQL_ROOT_PASSWORD')
    if password is None:
        password = subprocess.check_output(['docker', 'exec', 'zsjos-mysql-1', 'cat',
                                            config['MYSQL_ROOT_PASSWORD_FILE']]).decode().strip()
    port = metadata['NetworkSettings']['Ports']['3306/tcp'][0]
    require(port['HostIp'] == '127.0.0.1' and config['MYSQL_DATABASE'] == 'zsjos', 'Unexpected database target')
    conn = pymysql.connect(host=port['HostIp'], port=int(port['HostPort']), user='root', password=password,
                           database='zsjos', charset='utf8mb4', autocommit=False,
                           cursorclass=pymysql.cursors.DictCursor)
    with conn.cursor() as cur:
        cur.execute('SET NAMES utf8mb4')
        cur.execute("SET time_zone='+08:00'")
        cur.execute('SET SESSION innodb_lock_wait_timeout=10')
    return conn


def second(value):
    # Match the existing MySQL DATETIME(0) import rounding, not truncation.
    return (value + dt.timedelta(microseconds=500000)).replace(microsecond=0)


def snapshot(cur, lock=False):
    suffix = ' FOR UPDATE' if lock else ''
    result = {LEAD: rows(cur, f'SELECT * FROM {LEAD} WHERE tenant_id=1 AND deleted=0 '
                         f'AND lead_no IN ({ph(NOS)}) ORDER BY lead_no' + suffix, NOS)}
    require([r['lead_no'] for r in result[LEAD]] == list(NOS), 'Target Lead set changed')
    ids = [r['id'] for r in result[LEAD]]
    for table in TABLES[1:]:
        condition = "aggregate_type='lead' AND aggregate_id" if table == EVENT else 'lead_id'
        result[table] = rows(cur, f'SELECT * FROM {table} WHERE tenant_id=1 AND '
                            f'{condition} IN ({ph(ids)}) ORDER BY id' + suffix, ids)
    return result


def evidence(cur, state, lock=False):
    suffix = ' FOR UPDATE' if lock else ''
    ids = [l['id'] for l in state[LEAD]]
    owners = sorted({l['owner_user_id'] for l in state[LEAD]})
    source = rows(cur, 'SELECT id,lead_no,lead_status,assignment_status,assigned_sales_id,invalid_reason,'
                  'invalid_remark,valid_marked_at,recycle_source_status,public_pool_released_at,'
                  'public_pool_release_reason,public_pool_follow_sales_id,public_pool_follow_assigned_at '
                  f'FROM ptml.leads_lead WHERE lead_no IN ({ph(NOS)}) ORDER BY lead_no' + suffix, NOS)
    require([r['lead_no'] for r in source] == list(NOS), 'Legacy source set changed')
    old_ids = [r['id'] for r in source]
    result = {'legacy': source,
              'legacy_events': rows(cur, 'SELECT id,lead_id,event_type,from_status,to_status,reason,created_at '
                                    f'FROM ptml.leads_status_event WHERE lead_id IN ({ph(old_ids)}) ORDER BY id' + suffix, old_ids),
              'owners': rows(cur, 'SELECT id,dept_id,status,deleted FROM system_users WHERE tenant_id=1 '
                             f'AND id IN ({ph(owners)}) ORDER BY id' + suffix, owners)}
    # Protect all Lead-related domain rows, including logical deletions, against collateral changes.
    tables = rows(cur, "SELECT table_name FROM information_schema.columns WHERE table_schema=DATABASE() "
                  "AND column_name='lead_id' ORDER BY table_name")
    for entry in tables:
        table = entry['TABLE_NAME']
        if table in TABLES or '_bak_' in table:
            continue
        result[table] = rows(cur, f'SELECT * FROM `{table}` WHERE tenant_id=1 '
                             f'AND lead_id IN ({ph(ids)}) ORDER BY id' + suffix, ids)
    opp_ids = [o['id'] for o in state[OPP]]
    result['tasks'] = rows(cur, 'SELECT * FROM zsjos_business_task WHERE tenant_id=1 AND '
                          f"((biz_type='lead' AND biz_id IN ({ph(ids)})) OR "
                          f"(biz_type='opportunity' AND biz_id IN ({ph(opp_ids)}))) ORDER BY id" + suffix, ids + opp_ids)
    return result


def active(row):
    return row.get('deleted') in (0, False, b'\x00')


def validate_sources(state, proof):
    require(len(state[OPP]) == len(NOS) and all(active(o) for o in state[OPP]), 'Unexpected Opportunity set')
    for table in ('zsjos_order', 'zsjos_lead_appeal', 'zsjos_lead_transfer_request',
                  'zsjos_lead_public_sea_pool_compatibility_record', 'zsjos_payment_order'):
        require(not any(active(r) for r in proof.get(table, [])), 'Unexpected dependency: ' + table)
    require(not any(active(t) and t['status'] == 'pending' for t in proof['tasks']), 'Pending task requires separate review')
    require(not any(active(r) and r['fact_type'] != 'ASSIGNMENT'
                    for r in proof.get('zsjos_performance_attribution', [])), 'Unexpected performance fact')
    require(not proof.get('zsjos_lead_public_sea_pool_notify_stage'), 'Existing notification stage requires review')
    for l, s in zip(state[LEAD], proof['legacy']):
        require(s['lead_status'] == 'valid' and s['assignment_status'] == 'recycled'
                and s['recycle_source_status'] == 'valid' and s['assigned_sales_id']
                and not s['invalid_reason'] and s['invalid_remark'] and s['public_pool_released_at']
                and s['public_pool_follow_sales_id'] is None and s['public_pool_follow_assigned_at'] is None,
                'Legacy prerequisite changed: ' + l['lead_no'])
        history = [r for r in proof['legacy_events'] if r['lead_id'] == s['id']]
        require(not any(r['to_status'] == 'invalid' or r['event_type'] == 'mark_invalid' for r in history),
                'Conflicting original invalid judgment')
        valid = [r for r in history if r['event_type'] == 'mark_valid']
        release = [r for r in history if r['event_type'] == 'lead_released_public_pool']
        require(len(valid) == len(release) == 1 and valid[0]['reason'] == s['invalid_remark']
                and release[0]['to_status'] == 'valid' and max(history, key=lambda r: (r['created_at'], r['id'])) == release[0],
                'Legacy judgment/release chain changed')
        require(l['qualified_at'] == second(s['valid_marked_at'])
                and l['public_pool_at'] == second(s['public_pool_released_at'])
                and l['owner_user_id'] and l['owner_identity'] == 'sales'
                and l['assignment_status'] == 'owned' and l['recycle_source_owner_user_id'] is None
                and l['ownership_started_at'] and l['ownership_started_at'] <= l['public_pool_at']
                and all(l[k] is None for k in ('closed_at', 'converted_at', 'next_follow_up_at',
                                             'qualification_deadline_at', 'appeal_deadline_at', 'invalid_reason',
                                             'invalid_reason_label_snapshot', 'invalid_evidence_refs', 'suspended_at')),
                'Lead historical fields changed: ' + l['lead_no'])
        ownership = [r for r in proof['zsjos_lead_assignment_history'] if r['lead_id'] == l['id'] and active(r)]
        recycle = [r for r in ownership if r['action_type'] == 'recycle']
        require(len(recycle) == 1 and recycle[0]['from_owner_user_id'] == l['owner_user_id']
                and not any(r['action_type'] != 'other' and r['occurred_at'] > recycle[0]['occurred_at'] for r in ownership),
                'Original sales ownership changed')
        owner = [u for u in proof['owners'] if u['id'] == l['owner_user_id']]
        require(len(owner) == 1 and active(owner[0]) and owner[0]['status'] == 0 and owner[0]['dept_id'],
                'Original owner unavailable')
        opps = [o for o in state[OPP] if o['lead_id'] == l['id']]
        require(len(opps) == 1 and opps[0]['owner_user_id'] == l['owner_user_id']
                and opps[0]['type'] == 'initial_conversion' and opps[0]['next_follow_up_at'] is None
                and opps[0]['won_at'] is None and opps[0]['shelved_until'] is None, 'Opportunity prerequisites changed')


def completed(state, lead, source, owner):
    lid, key = lead['id'], MARKER + ':' + lead['lead_no']
    events = [e for e in state[EVENT] if e['aggregate_id'] == lid and e['idempotency_key'] == key]
    cycles = [c for c in state[CYCLE] if c['lead_id'] == lid]
    pools = [e for e in state[POOL_EVENT] if e['lead_id'] == lid]
    opp = next(o for o in state[OPP] if o['lead_id'] == lid)
    if not events:
        require(not cycles and not pools, 'Partial/colliding public sea records without repair audit')
        return False
    require(len(events) == len(cycles) == len(pools) == 1, 'Partial repair: expected one audit/cycle/pool event')
    event, cycle, pool = events[0], cycles[0], pools[0]
    require(all(active(r) for r in (event, cycle, pool)), 'Repair record deleted')
    require(lead['status'] == 'valid' and lead['valid_description'] == source['invalid_remark']
            and lead['invalid_description'] is None and lead['version'] == 3 and lead['updater'] == MARKER
            and opp['status'] == 'open' and opp['lost_at'] is None and opp['lost_reason'] is None
            and opp['version'] == 2 and opp['updater'] == MARKER, 'Completed state diverged; no automatic overwrite')
    require(cycle['status'] == 'waiting_assignment' and cycle['collaborator_user_id'] is None
            and cycle['original_owner_user_id'] == lead['owner_user_id'] and cycle['frozen_dept_id'] == owner['dept_id']
            and cycle['entered_at'] == cycle['due_at'] == second(source['public_pool_released_at'])
            and cycle['ownership_started_at'] == lead['ownership_started_at'] and cycle['idempotency_key'] == key
            and cycle['cycle_no'] == 1 and cycle['version'] == 0
            and all(cycle[k] is None for k in ('assigned_at', 'exited_at', 'converted_at', 'exit_reason')),
            'Completed public sea cycle diverged')
    require(event['event_type'] == 'lead_state_repaired' and event['from_status'] == 'invalid'
            and event['to_status'] == 'valid' and event['reason'] == REASON and event['operator_user_id'] is None
            and pool['cycle_id'] == cycle['id'] and pool['event_type'] == 'entered'
            and pool['reason'] == POOL_REASON and pool['operator_user_id'] is None
            and pool['idempotency_key'] == key + ':entered'
            and pool['occurred_at'] == event['occurred_at'], 'Repair audit diverged')
    refs = json.loads(event['related_object_refs'])
    require(refs['publicSeaCycleId'] == cycle['id'] and refs['opportunityId'] == opp['id']
            and refs['legacyReleasedAt'] == source['public_pool_released_at'].isoformat()
            and refs['repairSource'] == MARKER, 'Repair provenance mismatch')
    return True


def insert(cur, table, values):
    cur.execute(f'INSERT INTO {table} ({",".join(values)}) VALUES ({ph(values)})', list(values.values()))
    return cur.lastrowid


def repair(cur, proof, fail_at=None, limit=None):
    before = snapshot(cur, True)
    validate_sources(before, proof)
    changed = steps = 0
    now = rows(cur, 'SELECT NOW() AS now')[0]['now']
    def checkpoint():
        nonlocal steps
        steps += 1
        if steps == fail_at:
            raise RuntimeError('Injected transaction failure')
    for l, s in zip(before[LEAD], proof['legacy']):
        owner = next(u for u in proof['owners'] if u['id'] == l['owner_user_id'])
        if completed(before, l, s, owner):
            continue
        if limit is not None and changed >= limit:
            break
        old_marker = 'repair-lead-invalid-20261012' if l['lead_no'] == 'LD202609190220' else 'repair-lead-invalid-batch-20261014'
        old_events = [e for e in before[EVENT] if e['aggregate_id'] == l['id'] and e['creator'] == old_marker]
        require(len(old_events) == 1 and old_events[0]['to_status'] == 'invalid', 'Missing prior correction event')
        require(not any(e['aggregate_id'] == l['id'] and e['occurred_at'] > dt.datetime(2026, 10, 14, 16, 34, 9)
                        for e in before[EVENT]), 'Newer business activity requires review')
        o = next(o for o in before[OPP] if o['lead_id'] == l['id'])
        require(l['status'] == 'invalid' and l['version'] == 2 and l['updater'] == 'invalid-lead-owner-return-20261014'
                and l['invalid_description'] == s['invalid_remark'] and l['valid_description'] is None,
                'Lead no longer matches approved state: ' + l['lead_no'])
        require(o['status'] == 'lost' and o['version'] == 1 and o['updater'] == old_marker
                and o['lost_at'] == old_events[0]['occurred_at'] and o['lost_reason'] == OLD_REASON,
                'Opportunity no longer matches prior repair')
        key = MARKER + ':' + l['lead_no']
        cur.execute('UPDATE zsjos_lead SET status=%s,valid_description=%s,invalid_description=NULL,'
                    'version=version+1,updater=%s,update_time=%s WHERE tenant_id=1 AND id=%s AND version=2 AND status=%s',
                    ('valid', s['invalid_remark'], MARKER, now, l['id'], 'invalid'))
        require(cur.rowcount == 1, 'Lead update conflict'); checkpoint()
        cur.execute('UPDATE zsjos_opportunity SET status=%s,lost_at=NULL,lost_reason=NULL,'
                    'version=version+1,updater=%s,update_time=%s WHERE tenant_id=1 AND id=%s AND version=1 AND status=%s',
                    ('open', MARKER, now, o['id'], 'lost'))
        require(cur.rowcount == 1, 'Opportunity update conflict'); checkpoint()
        audit = dict(tenant_id=1, creator=MARKER, updater=MARKER, create_time=now, update_time=now)
        cycle_id = insert(cur, CYCLE, dict(audit, lead_id=l['id'], cycle_no=1, original_owner_user_id=l['owner_user_id'],
                          frozen_dept_id=owner['dept_id'], status='waiting_assignment',
                          ownership_started_at=l['ownership_started_at'], due_at=second(s['public_pool_released_at']),
                          entered_at=second(s['public_pool_released_at']), idempotency_key=key, version=0))
        checkpoint()
        insert(cur, POOL_EVENT, dict(audit, cycle_id=cycle_id, lead_id=l['id'], event_type='entered',
                                     reason=POOL_REASON, idempotency_key=key + ':entered', occurred_at=now))
        checkpoint()
        refs = dict(repairSource=MARKER, supersededRepairEventId=old_events[0]['id'],
                    opportunityId=o['id'], opportunityFromStatus='lost', opportunityToStatus='open',
                    publicSeaCycleId=cycle_id, legacySourceTable='ptml.leads_lead', legacySourceId=s['id'],
                    legacyReleasedAt=s['public_pool_released_at'].isoformat(), legacyReleaseReason=s['public_pool_release_reason'],
                    legacyQualificationAt=s['valid_marked_at'].isoformat(),
                    departmentSnapshotBasis='current owner department at correction; not historical department',
                    historicalQualificationPreserved=True)
        insert(cur, EVENT, dict(audit, event_type='lead_state_repaired', aggregate_type='lead', aggregate_id=l['id'],
                               from_status='invalid', to_status='valid', reason=REASON,
                               related_object_refs=json.dumps(refs, ensure_ascii=False), occurred_at=now, idempotency_key=key))
        checkpoint()
        changed += 1
    after = snapshot(cur)
    verify_preservation(before, after, changed)
    if limit is None:
        for l, s in zip(after[LEAD], proof['legacy']):
            require(completed(after, l, s, next(u for u in proof['owners'] if u['id'] == l['owner_user_id'])),
                    'Missing completed repair')
    return changed


def verify_preservation(before, after, changed):
    mutable = {LEAD: {'status', 'valid_description', 'invalid_description', 'version', 'updater', 'update_time'},
               OPP: {'status', 'lost_at', 'lost_reason', 'version', 'updater', 'update_time'}}
    for table, fields in mutable.items():
        require(len(before[table]) == len(after[table]), 'Unexpected row count')
        count = 0
        for old, new in zip(before[table], after[table]):
            require({k: v for k, v in old.items() if k not in fields} ==
                    {k: v for k, v in new.items() if k not in fields}, 'Unauthorized field changed: ' + table)
            if old != new:
                count += 1
                require(new['version'] == old['version'] + 1, 'Version must advance once')
        require(count == changed, 'Unexpected changed count')
    for table in (CYCLE, POOL_EVENT, EVENT):
        old_ids = {r['id'] for r in before[table]}
        require([r for r in after[table] if r['id'] in old_ids] == before[table], 'Historical record changed')
        require(len(after[table]) == len(before[table]) + changed, 'Unexpected inserted count')


def verify_utf8(cur):
    for table, field in ((LEAD, 'valid_description'), (EVENT, 'reason'), (POOL_EVENT, 'reason')):
        for r in rows(cur, f'SELECT {field} AS value,HEX({field}) AS bytes FROM {table} WHERE tenant_id=1 AND updater=%s', (MARKER,)):
            require(r['value'] and bytes.fromhex(r['bytes']).decode('utf-8') == r['value'], 'UTF-8 byte mismatch')


def rehearse(conn, cur, initial, proof):
    for table in TABLES:
        ddl = rows(cur, f'SHOW CREATE TABLE {table}')[0]['Create Table']
        cur.execute(ddl.replace('CREATE TABLE', 'CREATE TEMPORARY TABLE', 1))
        columns = [r['Field'] for r in rows(cur, f'SHOW COLUMNS FROM {table}')
                   if 'GENERATED' not in r['Extra'].replace('DEFAULT_GENERATED', '')]
        if initial[table]:
            cur.executemany(f'INSERT INTO {table} ({",".join(columns)}) VALUES ({ph(columns)})',
                            [[row[k] for k in columns] for row in initial[table]])
    conn.commit()
    for step in (1, 2, 3, 4, 5, 55):
        try:
            repair(cur, proof, fail_at=step)
            raise AssertionError('Expected injected failure')
        except RuntimeError as exc:
            require(str(exc) == 'Injected transaction failure', 'Unexpected rehearsal failure')
        conn.rollback()
        require(snapshot(cur) == initial, 'Injected failure did not fully roll back')
    require(repair(cur, proof) == 11, 'Initial rehearsal failed')
    first = snapshot(cur)
    verify_utf8(cur)
    require(repair(cur, proof) == 0 and snapshot(cur) == first, 'Replay changed completed rows')
    conn.rollback()
    require(snapshot(cur) == initial, 'Rehearsal rollback failed')
    # Model already-completed targets; the identical per-target guard safely skips them.
    require(repair(cur, proof, limit=1) == 1, 'Partial-target setup failed')
    require(repair(cur, proof) == 10, 'Partial-target recovery failed')
    require(repair(cur, proof) == 0, 'Recovered replay failed')
    conn.rollback()
    cur.execute('UPDATE zsjos_lead SET status=%s WHERE lead_no=%s AND tenant_id=1', ('won', NOS[0]))
    try:
        repair(cur, proof)
        raise AssertionError('Conflicting prerequisite accepted')
    except RuntimeError as exc:
        require(str(exc).startswith('Lead no longer matches approved state'), 'Unexpected conflict')
    conn.rollback()
    # Incomplete individual targets are rejected, never mistaken for completed based on a marker alone.
    require(repair(cur, proof, limit=1) == 1, 'Incomplete setup failed')
    cur.execute('UPDATE zsjos_lead_public_sea_pool_cycle SET status=%s WHERE idempotency_key=%s', ('exited', MARKER + ':' + NOS[0]))
    try:
        repair(cur, proof)
        raise AssertionError('Incomplete cycle accepted')
    except RuntimeError as exc:
        require(str(exc) == 'Completed public sea cycle diverged', 'Unexpected incomplete-target error')
    conn.rollback()
    require(snapshot(cur) == initial, 'Fixture cleanup mismatch')
    for table in reversed(TABLES):
        cur.execute(f'DROP TEMPORARY TABLE {table}')
    conn.commit()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--evidence-dir', type=Path, required=True)
    parser.add_argument('--apply', action='store_true')
    args = parser.parse_args()
    os.umask(0o077)
    args.evidence_dir.mkdir(parents=True, mode=0o700, exist_ok=False)
    conn = connect()
    try:
        with conn.cursor() as cur:
            before = snapshot(cur)
            proof = evidence(cur, before)
            validate_sources(before, proof)
            save(args.evidence_dir / 'before.json', before)
            save(args.evidence_dir / 'source-and-protected.json', proof)
            conn.rollback()
            is_replay = all(any(e['idempotency_key'] == MARKER + ':' + no for e in before[EVENT]) for no in NOS)
            if not is_replay:
                rehearse(conn, cur, before, proof)
            save(args.evidence_dir / 'rehearsal.json', {'mysql': rows(cur, 'SELECT VERSION() AS version')[0]['version'],
                 'executed': not is_replay, 'cases': [] if is_replay else ['initial', 'repeat', 'six_failure_points_rollback',
                    'partial_target_recovery', 'conflict_rejection', 'incomplete_cycle_rejection', 'utf8_hex', 'field_preservation']})
            conn.rollback()
            require(snapshot(cur, True) == before, 'Target changed since backup; no write performed')
            require(evidence(cur, before, True) == proof, 'Source/dependency changed since backup; no write performed')
            if args.apply:
                changed = repair(cur, proof)
                after = snapshot(cur)
                require(evidence(cur, after) == proof, 'Protected domain rows changed')
                verify_utf8(cur)
                save(args.evidence_dir / 'intended-after.json', after)
                conn.commit()
                save(args.evidence_dir / 'commit-receipt.json', {'changed': changed, 'committed': True})
                # New connection guarantees verification does not reuse the pre-commit snapshot.
                with connect() as independent:
                    with independent.cursor() as verify:
                        require(snapshot(verify) == after, 'Independent committed readback mismatch')
                        require(evidence(verify, after) == proof, 'Independent protected-domain mismatch')
                        verify_utf8(verify)
                        require(repair(verify, proof) == 0 and snapshot(verify) == after, 'Independent replay not a no-op')
                    independent.rollback()
                save(args.evidence_dir / 'after.json', after)
            else:
                changed = 0
                conn.rollback()
                require(snapshot(cur) == before, 'Rehearsal changed persistent data')
            report = {'mode': 'committed' if args.apply else 'rehearsed_only', 'leadNos': list(NOS),
                      'changedPerTable': {table: changed for table in TABLES}, 'replay': is_replay,
                      'beforeSha256': hashlib.sha256((args.evidence_dir / 'before.json').read_bytes()).hexdigest(),
                      'scriptSha256': hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
                      'protectedFieldsAndRowsVerified': True, 'utf8HexVerified': True}
            save(args.evidence_dir / 'result.json', report)
            print(encoded(report))
    finally:
        conn.rollback()
        conn.close()


if __name__ == '__main__':
    main()
