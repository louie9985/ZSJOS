#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""One-time confirmed qualification exception; see education-lead-restore-20261027.md."""
import argparse
import datetime as dt
import decimal
import hashlib
import json
import os
from pathlib import Path

from transfer_student_20261015 import connect, decoded as restore_decoded

MARKER = 'education-valid-20261027'
REASON = '按用户明确确认将54条教务自拓客资直接判为有效，豁免首跟前置条件；系统数据修复，非负责人本人操作'
RESTORE_PLAN = Path('/opt/zsjos-runtime/backups/education-lead-restore-20261027/plan.json')
RESTORE_HASH = 'f7d7183bb28b68a28dd5b187c1a60c7db989dcf4a34e993bbfa5c250b7b698a8'
L, O, T, P, E, A, I = ('zsjos_lead', 'zsjos_opportunity', 'zsjos_business_task',
                       'zsjos_performance_attribution', 'zsjos_business_event',
                       'zsjos_business_audit_log', 'zsjos_lead_intended_product')
WRITE_TABLES = (L, O, T, P, E, A)
PROTECTED = ('zsjos_person', I, 'zsjos_lead_assignment_history', 'zsjos_lead_follow_up_record',
             'zsjos_order', 'zsjos_cashback', 'zsjos_lead_appeal', 'zsjos_lead_public_sea_pool_cycle')
TABLES = WRITE_TABLES + PROTECTED
ALLOWED = {
    L: {'status', 'qualified_by_user_id', 'qualified_at', 'converted_at', 'last_activity_at',
        'valid_description', 'invalid_reason', 'invalid_reason_label_snapshot', 'invalid_description',
        'invalid_evidence_refs', 'suspended_at', 'version', 'updater', 'update_time'},
    T: {'status', 'completed_at', 'version', 'updater', 'update_time'},
    P: {'outcome', 'completed_at', 'updater', 'update_time'},
}


def require(ok, message):
    if not ok:
        raise RuntimeError(message)


def dumps(value):
    def encode(v):
        if isinstance(v, (dt.datetime, dt.date)):
            return {'$date': v.isoformat(), '$type': type(v).__name__}
        if isinstance(v, bytes):
            return {'$bytes': v.hex()}
        if isinstance(v, decimal.Decimal):
            return {'$decimal': str(v)}
        raise TypeError(type(v).__name__)
    return json.dumps(value, ensure_ascii=False, sort_keys=True, indent=2, default=encode)


def loads(text):
    def decode(v):
        if '$date' in v:
            return getattr(dt, v['$type']).fromisoformat(v['$date'])
        if '$bytes' in v:
            return bytes.fromhex(v['$bytes'])
        if '$decimal' in v:
            return decimal.Decimal(v['$decimal'])
        return v
    return json.loads(text, object_hook=decode)


def save(path, value):
    with path.open('w', encoding='utf-8') as f:
        f.write(dumps(value) + '\n')
        f.flush()
        os.fsync(f.fileno())


def rows(q, sql, args=()):
    q.execute(sql, args or None)
    return list(q.fetchall())


def active(r):
    return r['deleted'] == b'\0'


def capture(q, plan, lock=False):
    ids, persons = plan['ids'], plan['persons']
    lead_set = ','.join(map(str, ids))
    person_set = ','.join(map(str, persons))
    conditions = {L: f'id IN ({lead_set})', O: f'person_id IN ({person_set}) OR lead_id IN ({lead_set})',
                  T: f"biz_type='lead' AND biz_id IN ({lead_set})", P: f'lead_id IN ({lead_set})',
                  E: f"aggregate_type='lead' AND aggregate_id IN ({lead_set})",
                  A: "execution_key LIKE '" + MARKER + ":%'",
                  'zsjos_person': f'id IN ({person_set})'}
    for table in PROTECTED[1:]:
        conditions[table] = f'lead_id IN ({lead_set})'
    if lock:
        # Match normal identity-before-Lead locking; scoped row locks protect concurrent commands.
        rows(q, f'SELECT id FROM zsjos_person WHERE tenant_id=1 AND id IN ({person_set}) ORDER BY id FOR UPDATE')
    return {table: rows(q, f'SELECT * FROM {table} WHERE tenant_id=1 AND ({conditions[table]}) ORDER BY id'
                       + (' FOR UPDATE' if lock else '')) for table in TABLES}


def current_tasks(state):
    result = {}
    for lead in state[L]:
        key = f"lead-qualification:{lead['id']}:{lead['qualification_round_no']}"
        tasks = [t for t in state[T] if active(t) and t['idempotency_key'] == key]
        require(len(tasks) == 1, 'Missing or ambiguous current qualification task')
        result[lead['id']] = tasks[0]
    return result


def validate_initial(state):
    require(len(state[L]) == 54 and len(state['zsjos_person']) == 54, 'Target count changed')
    require(not state[O] and not state[A], 'Existing opportunity or partial repair')
    for table in ('zsjos_order', 'zsjos_cashback', 'zsjos_lead_appeal'):
        require(not state[table], 'Existing related business requires new review: ' + table)
    require(not any(active(r) and r['status'] == 'active' for r in state['zsjos_lead_public_sea_pool_cycle']), 'Active public sea')
    require(not any(e['idempotency_key'].startswith(MARKER + ':') for e in state[E]), 'Partial repair events')
    tasks = current_tasks(state)
    for lead in state[L]:
        require(active(lead) and lead['status'] == 'submitted' and lead['assignment_status'] == 'owned'
                and lead['source_type'] == 'education_self_sourced' and lead['owner_identity'] == 'education'
                and lead['suspended_at'] is None and lead['partner_id'] is None
                and lead['provider_owner_type'] != 'partner', 'Lead eligibility changed')
        t = tasks[lead['id']]
        require(t['status'] == 'pending' and t['assignee_id'] == lead['owner_user_id']
                and t['task_type'] == 'lead_qualification', 'Task changed')
        facts = [p for p in state[P] if active(p) and p['fact_type'] == 'QUALIFICATION' and p['fact_id'] == t['id']]
        require(len(facts) == 1 and facts[0]['outcome'] == 'pending'
                and facts[0]['user_id'] == lead['owner_user_id'], 'Qualification attribution changed')


def insert(q, table, values, now):
    values = dict(values, tenant_id=1, creator=MARKER, updater=MARKER, create_time=now, update_time=now)
    q.execute(f"INSERT INTO {table} ({','.join(values)}) VALUES ({','.join(['%s'] * len(values))})", tuple(values.values()))
    require(q.rowcount == 1, 'Insertion failed')
    return q.lastrowid


def update(q, table, old, values):
    q.execute(f"UPDATE {table} SET " + ','.join(f'{k}=%s' for k in values)
              + ' WHERE tenant_id=1 AND id=%s AND deleted=0', (*values.values(), old['id']))
    require(q.rowcount == 1, 'Update count differs')


def summary(products, lid):
    return [{'spuRef': p['spu_ref'] or '', 'spuName': p['spu_name_snapshot'] or '',
             'skuRef': p['sku_ref'] or '', 'skuName': p['sku_name_snapshot'] or '',
             'primary': p['is_primary'] == b'\1'} for p in products if active(p) and p['lead_id'] == lid]


def verify(q, plan):
    before = plan['before']
    after = capture(q, plan)
    tasks = current_tasks(before)
    new_events = [e for e in after[E] if e['idempotency_key'].startswith(MARKER + ':')]
    require(len(after[O]) == len(after[A]) == len(new_events) == 54, 'Incomplete repair effects')
    changed_task_ids = {t['id'] for t in tasks.values()}
    changed_perf_ids = {p['id'] for p in before[P] if active(p) and p['fact_type'] == 'QUALIFICATION' and p['fact_id'] in changed_task_ids}
    for table in PROTECTED:
        require(after[table] == before[table], 'Protected rows changed: ' + table)
    for table in (L, T, P, E):
        index = {r['id']: r for r in after[table]}
        for old in before[table]:
            require(old['id'] in index, 'Historical row missing')
            current = index[old['id']]
            changed = table == L or table == T and old['id'] in changed_task_ids or table == P and old['id'] in changed_perf_ids
            allowed = ALLOWED.get(table, set()) if changed else set()
            require(all(current[k] == v for k, v in old.items() if k not in allowed), 'Protected field changed: ' + table)
        require(len(after[table]) == len(before[table]) + (54 if table == E else 0), 'Unexpected row growth')
    for lead in after[L]:
        lid = lead['id']
        old = next(l for l in before[L] if l['id'] == lid)
        require(lead['status'] == 'valid' and lead['qualified_by_user_id'] is None
                and lead['valid_description'] == REASON and lead['version'] == old['version'] + 1
                and lead['qualified_at'] == lead['converted_at'] and lead['updater'] == MARKER,
                'Qualification result differs')
        require(all(lead[k] is None for k in ('invalid_reason', 'invalid_reason_label_snapshot', 'invalid_description', 'invalid_evidence_refs', 'suspended_at')), 'Stale invalid state')
        now = lead['qualified_at']
        opp = [o for o in after[O] if o['lead_id'] == lid and active(o)]
        require(len(opp) == 1 and opp[0]['status'] == 'open' and opp[0]['type'] == 'initial_conversion'
                and opp[0]['person_id'] == old['person_id'] and opp[0]['owner_user_id'] == old['owner_user_id']
                and json.loads(opp[0]['expected_product_summary']) == summary(before[I], lid), 'Opportunity differs')
        task = next(t for t in after[T] if t['id'] == tasks[lid]['id'])
        require(task['status'] == 'completed' and task['completed_at'] == now
                and task['version'] == tasks[lid]['version'] + 1, 'Qualification task differs')
        fact = next(p for p in after[P] if p['id'] in changed_perf_ids and p['fact_id'] == task['id'])
        require(fact['outcome'] == 'valid' and fact['completed_at'] == now, 'Performance result differs')
        event = next(e for e in new_events if e['aggregate_id'] == lid)
        require(event['event_type'] == 'lead_qualified_valid' and event['operator_user_id'] is None
                and event['reason'] == REASON and event['from_status'] == 'submitted' and event['to_status'] == 'valid', 'Repair event differs')
        refs = json.loads(event['related_object_refs'])
        require(refs['opportunityId'] == opp[0]['id'] and refs['repair'] == MARKER and refs['firstFollowUpWaived'], 'Event links differ')
        audit = [a for a in after[A] if a['execution_key'] == MARKER + ':' + str(lid)]
        require(len(audit) == 1 and audit[0]['operator_user_id'] is None and audit[0]['executor_type'] == 'SYSTEM'
                and audit[0]['executor_identity'] == MARKER and audit[0]['result_status'] == 'SUCCESS', 'Audit differs')
    sample = after[L][0]
    h = rows(q, f'SELECT HEX(valid_description) h FROM {L} WHERE tenant_id=1 AND id=%s', (sample['id'],))[0]['h']
    require(h == REASON.encode('utf-8').hex().upper(), 'UTF-8 HEX mismatch')
    return after


def apply(q, plan, fail_at=None):
    state = capture(q, plan, lock=True)
    marked = [e for e in state[E] if e['idempotency_key'].startswith(MARKER + ':')]
    if marked:
        verify(q, plan)
        return 0
    require(state == plan['before'], 'Frozen preimage changed or partial repair')
    validate_initial(state)
    tasks = current_tasks(state)
    now = rows(q, 'SELECT NOW() n')[0]['n']
    writes = 0

    def checkpoint():
        nonlocal writes
        writes += 1
        if writes == fail_at:
            raise RuntimeError('Injected failure')

    for lead in state[L]:
        lid = lead['id']
        oid = insert(q, O, {'person_id': lead['person_id'], 'type': 'initial_conversion', 'lead_id': lid,
                           'status': 'open', 'owner_user_id': lead['owner_user_id'], 'version': 0,
                           'expected_product_summary': json.dumps(summary(state[I], lid), ensure_ascii=False)}, now)
        checkpoint()
        update(q, L, lead, dict(status='valid', qualified_by_user_id=None, qualified_at=now, converted_at=now,
                               last_activity_at=max(lead['last_activity_at'] or now, now), valid_description=REASON,
                               invalid_reason=None, invalid_reason_label_snapshot=None, invalid_description=None,
                               invalid_evidence_refs=None, suspended_at=None, version=lead['version'] + 1,
                               updater=MARKER, update_time=now))
        checkpoint()
        task = tasks[lid]
        update(q, T, task, dict(status='completed', completed_at=now, version=task['version'] + 1, updater=MARKER, update_time=now))
        checkpoint()
        fact = next(p for p in state[P] if active(p) and p['fact_type'] == 'QUALIFICATION' and p['fact_id'] == task['id'])
        update(q, P, fact, dict(outcome='valid', completed_at=now, updater=MARKER, update_time=now))
        checkpoint()
        refs = dict(repair=MARKER, executorType='SYSTEM', firstFollowUpWaived=True,
                    roundNo=lead['qualification_round_no'], opportunityId=oid, leadNo=lead['lead_no'])
        insert(q, E, dict(event_type='lead_qualified_valid', aggregate_type='lead', aggregate_id=lid,
                          operator_user_id=None, from_status='submitted', to_status='valid', reason=REASON,
                          occurred_at=now, related_object_refs=json.dumps(refs, ensure_ascii=False),
                          idempotency_key=MARKER + ':' + str(lid)), now)
        checkpoint()
        insert(q, A, dict(operator_user_id=None, operator_name_snapshot='系统数据修复', operator_role_snapshot='SYSTEM',
                          executor_type='SYSTEM', executor_identity=MARKER, execution_key=MARKER + ':' + str(lid),
                          category_code='lead', action_code='lead_qualification_repair', target_type='lead', target_id=str(lid),
                          detail_json=json.dumps(dict(refs, reason=REASON), ensure_ascii=False), source_type='EXPLICIT',
                          result_status='SUCCESS', occurred_at=now, finished_at=now), now)
        checkpoint()
    verify(q, plan)
    return writes


def fixture(plan):
    c = connect('zsjos-mysql-1')
    try:
        q = c.cursor()
        q.execute('SET NAMES utf8mb4')
        for table in TABLES:
            ddl = rows(q, f'SHOW CREATE TABLE {table}')[0]['Create Table']
            q.execute(ddl.replace('CREATE TABLE', 'CREATE TEMPORARY TABLE', 1))
            columns = [r['Field'] for r in rows(q, f'SHOW COLUMNS FROM {table}')
                       if not any(x in r['Extra'] for x in ('STORED GENERATED', 'VIRTUAL GENERATED'))]
            for r in plan['before'][table]:
                q.execute(f"INSERT INTO {table} ({','.join('`'+k+'`' for k in columns)}) VALUES ({','.join(['%s']*len(columns))})", tuple(r[k] for k in columns))
        c.commit()
        for failure in (1, 2, 3, 4, 5, 6, 163, 324):
            try:
                apply(q, plan, failure)
            except RuntimeError as e:
                require(str(e) == 'Injected failure', 'Unexpected fixture failure: ' + str(e))
                c.rollback()
            else:
                raise RuntimeError('Injection missed')
            require(capture(q, plan) == plan['before'], 'Rollback did not preserve all rows')
            c.rollback()
        q.execute(f"UPDATE {L} SET status='valid' WHERE id=%s AND tenant_id=1", (plan['ids'][0],))
        try:
            apply(q, plan)
        except RuntimeError as e:
            require(str(e) == 'Frozen preimage changed or partial repair', 'Unexpected partial-state error')
        else:
            raise RuntimeError('Partial state accepted')
        c.rollback()
        require(apply(q, plan) == 324, 'Unexpected initial write count')
        c.commit()
        after = capture(q, plan)
        require(apply(q, plan) == 0 and capture(q, plan) == after, 'Replay mutated data')
        c.rollback()
        # A missing audit after a committed-looking marker must reject replay as partial.
        q.execute(f'DELETE FROM {A} WHERE id=%s', (after[A][0]['id'],))
        try:
            apply(q, plan)
        except RuntimeError as e:
            require(str(e) == 'Incomplete repair effects', 'Unexpected missing-audit failure')
        else:
            raise RuntimeError('Incomplete audit accepted')
        c.rollback()
        verify(q, plan)
        return {'engine': rows(q, 'SELECT VERSION() v')[0]['v'], 'failure_rollback_points': 8,
                'initial_writes': 324, 'repeat_writes': 0, 'partial_preimage_and_audit_rejected': True,
                'recovery_and_protected_rows': 'PASS', 'utf8_hex': 'PASS'}
    finally:
        c.close()


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('mode', choices=['prepare', 'fixture', 'apply', 'verify'])
    p.add_argument('--evidence-dir', type=Path, required=True)
    p.add_argument('--sha256')
    args = p.parse_args()
    environment = os.getenv('ZSJOS_AGENT_ENV')
    if environment is None:
        marker = Path('/etc/zsjos/agent-environment')
        environment = marker.read_text().strip() if marker.exists() else 'local'
    require(environment == 'test', 'Only the authorized test environment is supported')
    require(hashlib.sha256(RESTORE_PLAN.read_bytes()).hexdigest() == RESTORE_HASH, 'Original 54-record scope changed')
    os.umask(0o077)
    root = args.evidence_dir
    if args.mode == 'prepare':
        root.mkdir(mode=0o700, parents=True, exist_ok=False)
        source = restore_decoded(RESTORE_PLAN.read_text())
        plan = {'ids': source['ids'], 'persons': sorted(l['person_id'] for l in source['before'][L])}
        require(len(set(plan['ids'])) == len(set(plan['persons'])) == 54, 'Original scope not unique')
    else:
        raw = (root / 'plan.json').read_bytes()
        require(hashlib.sha256(raw).hexdigest() == args.sha256, 'Exact plan SHA256 required')
        plan = loads(raw.decode())
    if args.mode == 'fixture':
        result = fixture(plan)
        result['script_sha256'] = hashlib.sha256(Path(__file__).read_bytes()).hexdigest()
        save(root / 'fixture.json', result)
        print(json.dumps(result))
        return
    c = connect('zsjos-mysql-1')
    try:
        q = c.cursor()
        q.execute('SET NAMES utf8mb4')
        q.execute("SET time_zone='+08:00'")
        q.execute('SET SESSION innodb_lock_wait_timeout=5')
        if args.mode in ('prepare', 'verify'):
            q.execute('SET SESSION TRANSACTION READ ONLY')
        if args.mode == 'prepare':
            plan['before'] = capture(q, plan)
            validate_initial(plan['before'])
            save(root / 'plan.json', plan)
            digest = hashlib.sha256((root / 'plan.json').read_bytes()).hexdigest()
            (root / 'plan.sha256').write_text(digest + '\n')
            print(json.dumps({'prepared': 54, 'writes_planned': 324, 'plan_sha256': digest}))
        elif args.mode == 'apply':
            checks = loads((root / 'fixture.json').read_text())
            require(checks['script_sha256'] == hashlib.sha256(Path(__file__).read_bytes()).hexdigest(), 'Verification invalidated by script change')
            changed = apply(q, plan)
            if changed:
                require(not (root / 'applied-after.json').exists(), 'Existing after-image requires review')
                save(root / 'applied-after.json', capture(q, plan))
                c.commit()
            else:
                c.rollback()
            verify(q, plan)
            save(root / ('apply-result.json' if changed else 'replay-result.json'), {'changed_rows': changed, 'committed': True})
            print(json.dumps({'changed_rows': changed, 'committed': True}))
        else:
            after = verify(q, plan)
            save(root / 'verified-after.json', after)
            result = {'valid_leads': 54, 'open_opportunities': 54, 'completed_qualification_tasks': 54,
                      'valid_performance_outcomes': 54, 'repair_events': 54, 'repair_audits': 54,
                      'protected_rows_and_utf8_hex': 'PASS', 'owner_actions_or_first_followups_fabricated': False,
                      'verified_at_beijing': str(rows(q, 'SELECT NOW() n')[0]['n'])}
            save(root / 'verification.json', result)
            print(json.dumps(result))
        c.rollback()
    except Exception:
        c.rollback()
        raise
    finally:
        c.close()


if __name__ == '__main__':
    main()
