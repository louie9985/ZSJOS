#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Authorized test-only historical stage bypass; not a bootstrap or migration.

See docs/operations/historical-student-supervision-20261028.md. Frozen preimages,
temporary-table execution evidence and exact plan hash are required before apply.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path

from transfer_student_20261015 import connect
from qualify_education_leads_20261027 import dumps, loads, rows, require, save

ROOT = Path('/opt/zsjos-runtime/backups/historical-student-supervision-20261028')
MARKER = 'historical-supervision-20261028'
REMARK = '【历史学员调整10.28】'
CUTOFF = '2026-10-28 00:00:00'
ACTOR = 1
S, R, E, T = ('zsjos_service_relation', 'zsjos_student_contact_record',
               'zsjos_business_event', 'zsjos_business_task')
P, G, C, X, A, O = ('zsjos_person', 'zsjos_collaboration_group', 'zsjos_delivery_class',
                     'zsjos_student_contact_extension', 'zsjos_student_service_annotation', 'zsjos_order')
TABLES = (S, R, E, T, P, G, C, X, A, O)
PREDICATE = ("tenant_id=1 AND deleted=0 AND status='active' AND teaching_delivery=1 "
             "AND teaching_closed=0 AND acceptance_status='pending' AND create_time<%s")


def digest(value):
    return hashlib.sha256(dumps(value).encode('utf-8')).hexdigest()


def code_digest():
    return hashlib.sha256(Path(__file__).read_bytes()).hexdigest()


def ids(values):
    require(values and all(type(v) is int and v > 0 for v in values), 'Invalid frozen IDs')
    return ','.join(map(str, sorted(set(values))))


def capture(q, plan):
    service_ids, people = ids(plan['ids']), ids(plan['people'])
    conditions = {
        # All services protect today's entries, other tenants and same-person other courses.
        S: '1=1', R: f'tenant_id=1 AND service_relation_id IN ({service_ids})',
        E: f"tenant_id=1 AND aggregate_type='student-service' AND aggregate_id IN ({service_ids})",
        T: f"tenant_id=1 AND biz_type='student_service' AND biz_id IN ({service_ids})",
        P: f'tenant_id=1 AND id IN ({people})',
        G: f'tenant_id=1 AND student_person_id IN ({people})',
        C: f'tenant_id=1 AND id IN ({ids(plan["classes"])})',
        X: f'tenant_id=1 AND service_relation_id IN ({service_ids})',
        A: f'tenant_id=1 AND service_relation_id IN ({service_ids})',
        O: f'tenant_id=1 AND id IN ({ids(plan["orders"])})',
    }
    result = {t: rows(q, f'SELECT * FROM {t} WHERE {conditions[t]} ORDER BY id') for t in TABLES}
    result['users'] = rows(q, 'SELECT id,status,deleted FROM system_users WHERE tenant_id=1 AND id IN ('
                           + ids(plan['owners'] + [ACTOR]) + ') ORDER BY id')
    return result


def initial(state, plan):
    selected = [s for s in state[S] if s['id'] in plan['ids']]
    require(len(selected) == 80 and len(plan['people']) == 78, 'Frozen population differs')
    require(not state[R] and not state[T] and not state[X], 'Existing teaching work needs review')
    require(not any(e['idempotency_key'].startswith(MARKER) for e in state[E]), 'Partial repair')
    require(len(state['users']) == len(set(plan['owners'] + [ACTOR]))
            and all(u['status'] == 0 and u['deleted'] == b'\0' for u in state['users']), 'Executor/owner unavailable')
    for s in selected:
        require(s['tenant_id'] == 1 and s['deleted'] == b'\0' and s['status'] == 'active'
                and s['acceptance_status'] == 'pending' and s['teaching_delivery'] == b'\1'
                and s['teaching_closed'] == b'\0' and s['create_time'].isoformat(' ') < CUTOFF
                and s['accepted_at'] is None and s['accepted_by_user_id'] is None
                and s['delivery_stage'] is None and s['first_contact_progress_json'] is None,
                'Target lifecycle differs')


def prepare():
    require(not (ROOT / 'plan.json').exists(), 'Do not replace frozen evidence')
    c = connect('zsjos-mysql-1')
    try:
        with c.cursor() as q:
            q.execute('SET NAMES utf8mb4')
            q.execute('START TRANSACTION WITH CONSISTENT SNAPSHOT, READ ONLY')
            target = rows(q, f'SELECT * FROM {S} WHERE {PREDICATE} ORDER BY id', (CUTOFF,))
            plan = dict(ids=[s['id'] for s in target], people=sorted({s['person_id'] for s in target}),
                        classes=sorted({s['class_id'] for s in target}),
                        orders=sorted({s['order_id'] for s in target if s['order_id']}),
                        owners=sorted({s['owner_user_id'] for s in target}), cutoff=CUTOFF)
            plan['before'] = capture(q, plan)
            initial(plan['before'], plan)
            save(ROOT / 'plan.json', plan)
            print(json.dumps(dict(services=len(target), people=len(plan['people']), sha256=digest(plan))))
    finally:
        c.rollback(); c.close()


def insert(q, table, values, now):
    fields = dict(values, tenant_id=1, creator=MARKER, updater=MARKER, create_time=now, update_time=now)
    q.execute(f'INSERT INTO {table} (' + ','.join(fields) + ') VALUES ('
              + ','.join(['%s'] * len(fields)) + ')', tuple(fields.values()))
    require(q.rowcount == 1, 'Insert count differs')
    return q.lastrowid


def provenance(plan):
    return dict(generationSource='historical_adjustment', executor=MARKER, executorUserId=ACTOR,
                skippedStages=['first_contact', 'study_plan'], targetStage='supervision',
                actualContactPerformed=False, planSha256=digest(plan), cutoff=CUTOFF)


def verify(q, plan):
    state = capture(q, plan)
    own_events = [e for e in state[E] if e['idempotency_key'].startswith(MARKER + ':')]
    require(len(own_events) == len(state[R]) == 80, 'Partial repair state')
    times = {e['occurred_at'] for e in own_events}
    require(len(times) == 1, 'Mixed repair times')
    now = next(iter(times))
    for table in (T, P, G, C, X, A, O, 'users'):
        require(dumps(state[table]) == dumps(plan['before'][table]), 'Protected state changed: ' + table)
    require(dumps([e for e in state[E] if not e['idempotency_key'].startswith(MARKER + ':')])
            == dumps(plan['before'][E]), 'Earlier audit changed')
    expected = []
    for old in plan['before'][S]:
        expected.append(dict(old, acceptance_status='accepted', accepted_at=now, accepted_by_user_id=ACTOR,
                             delivery_stage='supervision', version=old['version'] + 1,
                             updater=MARKER, update_time=now) if old['id'] in plan['ids'] else old)
    require(dumps(state[S]) == dumps(expected), 'Service fields or unrelated services changed')
    records = {r['service_relation_id']: r for r in state[R]}
    events = {e['aggregate_id']: e for e in own_events}
    require(set(records) == set(events) == set(plan['ids']), 'Record association differs')
    refs = provenance(plan)
    for rid in plan['ids']:
        r, e = records[rid], events[rid]
        require(r['contact_type'] == 'student_delivery_stage' and r['delivery_stage'] == 'study_plan'
                and r['successful'] == b'\1' and r['remark'] == REMARK and r['task_id'] is None
                and r['operator_user_id'] == ACTOR and r['submitted_at'] == now
                and r['next_contact_at'] is None and r['creator'] == MARKER and r['deleted'] == b'\0'
                and r['checklist_snapshot_json'] is None and r['study_plan_completed_at_first_contact'] is None
                and json.loads(r['attachment_file_ids_json']) == [] and json.loads(r['checklist_result_json']) == []
                and json.loads(r['delivery_data_json']) == refs
                and r['idempotency_key'] == f'{MARKER}:record:{rid}'
                and r['request_fingerprint'] == digest([MARKER, rid, REMARK, refs]), 'Stage record differs')
        require(e['operator_user_id'] is None and e['event_type'] == 'system_data_repair'
                and e['from_status'] == 'pending' and e['to_status'] == 'supervision'
                and e['reason'] == REMARK and e['creator'] == MARKER and e['deleted'] == b'\0'
                and json.loads(e['related_object_refs']) == dict(refs, stageRecordId=r['id'])
                and e['idempotency_key'] == f'{MARKER}:event:{rid}', 'Audit provenance differs')
    for table, column in ((R, 'remark'), (E, 'reason')):
        values = rows(q, f'SELECT HEX({column}) h FROM {table} WHERE tenant_id=1 AND idempotency_key LIKE %s', (MARKER + ':%',))
        require(len(values) == 80 and all(v['h'].lower() == REMARK.encode('utf-8').hex() for v in values), 'UTF-8 HEX differs')
    return state


def mutate(c, plan, fail_after=None):
    with c.cursor() as q:
        rows(q, f'SELECT id FROM {S} WHERE tenant_id=1 AND id IN ({ids(plan["ids"])}) ORDER BY id FOR UPDATE')
        state = capture(q, plan)
        if any(e['idempotency_key'].startswith(MARKER + ':') for e in state[E]):
            verify(q, plan)
            return 0
        require(dumps(state) == dumps(plan['before']), 'Frozen preimage drift; refuse overwrite')
        initial(state, plan)
        now = rows(q, 'SELECT NOW() now')[0]['now']
        refs = provenance(plan)
        count = 0
        def checkpoint():
            nonlocal count
            count += 1
            if count == fail_after:
                raise RuntimeError('Injected repair failure')
        for old in state[S]:
            rid = old['id']
            if rid not in plan['ids']:
                continue
            q.execute(f'UPDATE {S} SET acceptance_status=%s,accepted_at=%s,accepted_by_user_id=%s,'
                      'delivery_stage=%s,version=version+1,updater=%s,update_time=%s '
                      "WHERE tenant_id=1 AND id=%s AND version=%s AND deleted=0 AND acceptance_status='pending'",
                      ('accepted', now, ACTOR, 'supervision', MARKER, now, rid, old['version']))
            require(q.rowcount == 1, 'Service update conflict')
            checkpoint()
            record_id = insert(q, R, dict(service_relation_id=rid, task_id=None,
                contact_type='student_delivery_stage', delivery_stage='study_plan', successful=1, remark=REMARK,
                attachment_file_ids_json='[]', checklist_result_json='[]', delivery_data_json=json.dumps(refs),
                operator_user_id=ACTOR, submitted_at=now, next_contact_at=None,
                idempotency_key=f'{MARKER}:record:{rid}', request_fingerprint=digest([MARKER, rid, REMARK, refs])), now)
            checkpoint()
            insert(q, E, dict(event_type='system_data_repair', aggregate_type='student-service', aggregate_id=rid,
                operator_user_id=None, from_status='pending', to_status='supervision', reason=REMARK,
                related_object_refs=json.dumps(dict(refs, stageRecordId=record_id)), occurred_at=now,
                idempotency_key=f'{MARKER}:event:{rid}'), now)
            checkpoint()
        require(count == 240, 'Write count differs')
        verify(q, plan)
        return count


def fixture(plan):
    c = connect('zsjos-mysql-1')
    try:
        with c.cursor() as q:
            q.execute('SET NAMES utf8mb4')
            for table in TABLES:
                ddl = rows(q, f'SHOW CREATE TABLE {table}')[0]['Create Table']
                q.execute(ddl.replace('CREATE TABLE', 'CREATE TEMPORARY TABLE', 1))
                columns = [x['COLUMN_NAME'] for x in rows(q, "SELECT COLUMN_NAME FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name=%s AND EXTRA NOT LIKE '%%VIRTUAL GENERATED%%' AND EXTRA NOT LIKE '%%STORED GENERATED%%' ORDER BY ORDINAL_POSITION", (table,))]
                if plan['before'][table]:
                    q.executemany(f'INSERT INTO {table} (' + ','.join(columns) + ') VALUES ('
                                  + ','.join(['%s'] * len(columns)) + ')',
                                  [tuple(r[k] for k in columns) for r in plan['before'][table]])
        c.commit()
        for point in (1, 2, 3, 120, 240):
            try:
                mutate(c, plan, point)
                raise AssertionError('Fault did not fire')
            except RuntimeError as error:
                require(str(error) == 'Injected repair failure', 'Unexpected fixture error')
                c.rollback()
            with c.cursor() as q:
                require(dumps(capture(q, plan)) == dumps(plan['before']), 'Rollback left changes')
            c.rollback()
        # A changed preimage must fail before writing any administrative history.
        with c.cursor() as q:
            q.execute(f'UPDATE {S} SET version=version+1 WHERE id=%s AND tenant_id=1', (plan['ids'][0],))
        try:
            mutate(c, plan)
            raise AssertionError('Drift accepted')
        except RuntimeError as error:
            require(str(error) == 'Frozen preimage drift; refuse overwrite', 'Unexpected drift error')
        c.rollback()
        require(mutate(c, plan) == 240, 'Initial fixture count differs')
        c.commit()
        require(mutate(c, plan) == 0, 'Replay wrote data')
        c.rollback()
        with c.cursor() as q:
            q.execute(f'UPDATE {E} SET idempotency_key=CONCAT(idempotency_key,%s) WHERE tenant_id=1 AND idempotency_key=%s',
                      (':partial', f'{MARKER}:event:{plan["ids"][0]}'))
        try:
            mutate(c, plan)
            raise AssertionError('Partial audit accepted')
        except RuntimeError as error:
            require(str(error) == 'Audit provenance differs', 'Unexpected partial-state error')
        c.rollback()
        with c.cursor() as q:
            verify(q, plan)
        save(ROOT / 'fixture.json', dict(plan_sha256=digest(plan), script_sha256=code_digest(),
             first_writes=240, replay_writes=0, fault_points=5, rollback=True, drift_rejected=True,
             partial_rejected=True, recovered=True))
        print('PASS MySQL initial/repeat/5 fault rollbacks/recovery/preimage drift/partial-state rejection')
    finally:
        c.rollback(); c.close()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('mode', choices=['prepare', 'fixture', 'apply', 'verify'])
    parser.add_argument('--sha256')
    args = parser.parse_args()
    environment = os.getenv('ZSJOS_AGENT_ENV')
    marker = Path('/etc/zsjos/agent-environment')
    if environment is None:
        environment = marker.read_text().strip() if marker.exists() else 'local'
    require(environment == 'test', 'Authorized only for test')
    os.umask(0o077); ROOT.mkdir(mode=0o700, parents=True, exist_ok=True)
    if args.mode == 'prepare':
        prepare(); return
    plan = loads((ROOT / 'plan.json').read_text(encoding='utf-8'))
    require(args.sha256 == digest(plan), 'Frozen plan hash required')
    if args.mode == 'fixture':
        fixture(plan); return
    c = connect('zsjos-mysql-1')
    try:
        with c.cursor() as q:
            q.execute('SET NAMES utf8mb4'); q.execute("SET SESSION time_zone='+08:00'")
            q.execute('SET SESSION innodb_lock_wait_timeout=5')
        if args.mode == 'apply':
            evidence = loads((ROOT / 'fixture.json').read_text(encoding='utf-8'))
            require(evidence['plan_sha256'] == digest(plan) and evidence['script_sha256'] == code_digest(),
                    'Matching execution evidence required')
            with c.cursor() as q:
                require(rows(q, 'SELECT GET_LOCK(%s,0) ok', (MARKER,))[0]['ok'] == 1, 'Repair already running')
            count = mutate(c, plan)
            c.commit()
            print(json.dumps(dict(writes=count, services=80, people=78, stage_remarks=80, audit_events=80)))
        with c.cursor() as q:
            q.execute('START TRANSACTION WITH CONSISTENT SNAPSHOT, READ ONLY')
            after = verify(q, plan)
        save(ROOT / ('after.json' if args.mode == 'apply' else 'independent-after.json'), after)
        print('PASS independent state/remark HEX/protected rows verification')
    finally:
        c.rollback(); c.close()


if __name__ == '__main__':
    main()
