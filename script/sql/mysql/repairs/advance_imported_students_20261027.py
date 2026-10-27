#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Explicitly authorized historical completion of the frozen 458-service import.

No live deletes, DDL, grants, notifications or worker impersonation. See the
2026-10-27 follow-up in docs/operations/education-student-import-20261027.md.
"""
import argparse
import datetime as dt
import hashlib
import json
import os
from pathlib import Path

from transfer_student_20261015 import connect
from qualify_education_leads_20261027 import dumps, loads, save, rows, require
from import_education_students_20261027 import read as import_read, digest as import_digest

ROOT = Path('/opt/zsjos-runtime/backups/education-student-supervision-20261027')
IMPORT = Path('/opt/zsjos-runtime/backups/education-student-import-20261027')
IMPORT_HASH = 'ebbcb22cd9b6cbdef65cf2f2f7a6dff13cd1574a3f221231488f2a5ceb38c1d0'
MARKER = 'edu-supervision-20261027'
REMARK = '【历史学员批量导入10.27】'
ACTOR = 1
S, T, R, E = ('zsjos_service_relation', 'zsjos_business_task',
              'zsjos_student_contact_record', 'zsjos_business_event')
P, G, C, I, F, X = ('zsjos_person', 'zsjos_collaboration_group', 'zsjos_delivery_class',
                    'zsjos_student_entry_command', 'zsjos_student_contact_config_version',
                    'zsjos_student_contact_extension')
TABLES = (S, T, R, E, P, G, C, I, F, X)
TYPES = (('student_first_contact', '首次联系', 'OPEN_STUDENT_FIRST_CONTACT'),
         ('student_study_plan', '制定学习计划', 'OPEN_STUDENT_STUDY_PLAN'))


def sha(value):
    return hashlib.sha256(dumps(value).encode('utf-8')).hexdigest()


def sql_ids(values):
    require(values and all(isinstance(x, int) and x > 0 for x in values), 'Invalid frozen IDs')
    return ','.join(map(str, sorted(set(values))))


def capture(q, plan):
    services, persons = sql_ids(plan['ids']), sql_ids(plan['persons'])
    conditions = {
        S: f'person_id IN ({persons})',
        T: f"(biz_type='student_service' AND biz_id IN ({services})) OR idempotency_key LIKE '{MARKER}:%'",
        R: f"service_relation_id IN ({services}) OR idempotency_key LIKE '{MARKER}:%'",
        E: f"(aggregate_type='student-service' AND aggregate_id IN ({services})) OR idempotency_key LIKE '{MARKER}:%'",
        P: f'id IN ({persons})', G: f'student_person_id IN ({persons})',
        C: 'id IN (' + sql_ids(plan['classes']) + ')',
        I: f'service_relation_id IN ({services})', F: "status='published' OR id=" + str(plan['config_id']),
        X: f'service_relation_id IN ({services})',
    }
    state = {table: rows(q, f'SELECT * FROM {table} WHERE tenant_id=1 AND ({conditions[table]}) ORDER BY id')
             for table in TABLES}
    state['users'] = rows(q, 'SELECT id,status,deleted,dept_id FROM system_users WHERE tenant_id=1 AND id IN ('
                          + sql_ids(plan['owners'] + [ACTOR]) + ') ORDER BY id')
    return state


def checklist(config):
    items = [x for x in json.loads(config['checklist_json'])
             if x.get('enabled') and x['key'] != 'voice_learning_plan']
    require(items and not any(x.get('attachmentRequired') for x in items),
            'Attachments would require additional evidence, not fabricated completion')
    return {'configVersionId': config['id'], 'source': 'snapshot', 'historyIncomplete': False,
            'items': [dict(key=x['key'], title=x['title'], sort=x['sort'],
                           attachmentRequired=False, completed=True, attachmentFileIds=[])
                      for x in sorted(items, key=lambda x: x['sort'])]}


def initial(state, plan):
    targets = [r for r in state[S] if r['id'] in plan['ids']]
    require(len(targets) == 458 and len(state[P]) == 452, 'Frozen target count changed')
    require(not state[T] and not state[R] and not state[X], 'New teaching work requires review')
    require(not any(e['idempotency_key'].startswith(MARKER + ':') for e in state[E]), 'Partial repair')
    require(len(state[F]) == 1 and state[F][0]['id'] == plan['config_id'], 'Published config changed')
    require(all(u['deleted'] == b'\0' and u['status'] == 0 for u in state['users'])
            and len(state['users']) == len(set(plan['owners'] + [ACTOR])), 'Executor/planner unavailable')
    for row in targets:
        require(row['deleted'] == b'\0' and row['status'] == 'active' and row['acceptance_status'] == 'pending'
                and row['teaching_delivery'] == b'\1' and row['teaching_closed'] == b'\0'
                and row['accepted_at'] is None and row['accepted_by_user_id'] is None
                and row['delivery_stage'] is None and row['first_contact_progress_json'] is None,
                'Service no longer matches untouched import')
    require(len(state[I]) == 458 and {c['service_relation_id'] for c in state[I]} == set(plan['ids']),
            'Import command linkage differs')


def prepare():
    require(not (ROOT / 'plan.json').exists(), 'Do not replace frozen repair evidence')
    source = import_read(IMPORT / 'plan.json')
    require(import_digest(source) == IMPORT_HASH, 'Original import plan hash differs')
    results = [import_read(IMPORT / (r['idempotency_key'] + '.result.json')) for r in source['records']]
    ids = sorted({r['serviceRelationId'] for r in results})
    persons = sorted({r['personId'] for r in results})
    require(len(ids) == 458 and len(persons) == 452, 'Original import result count differs')
    c = connect('zsjos-mysql-1')
    try:
        with c.cursor() as q:
            q.execute('SET NAMES utf8mb4')
            q.execute('START TRANSACTION WITH CONSISTENT SNAPSHOT, READ ONLY')
            configs = rows(q, f"SELECT * FROM {F} WHERE tenant_id=1 AND deleted=0 AND status='published'")
            require(len(configs) == 1, 'Published policy missing or ambiguous')
            plan = dict(ids=ids, persons=persons, classes=sorted({r['class_id'] for r in source['records']}),
                        owners=sorted({r['owner_id'] for r in source['records']}), config_id=configs[0]['id'],
                        import_plan_sha256=IMPORT_HASH, checklist=checklist(configs[0]))
            plan['before'] = capture(q, plan)
            initial(plan['before'], plan)
            save(ROOT / 'plan.json', plan)
            print(json.dumps({'services': len(ids), 'completed_tasks': 916, 'contact_records': 916,
                              'repair_events': 458, 'total_writes': 2748, 'sha256': sha(plan)}))
    finally:
        c.rollback(); c.close()


def insert(q, table, values, now):
    fields = dict(values, tenant_id=1, creator=MARKER, updater=MARKER, create_time=now, update_time=now)
    q.execute(f'INSERT INTO {table} (' + ','.join(fields) + ') VALUES (' + ','.join(['%s']*len(fields)) + ')', tuple(fields.values()))
    require(q.rowcount == 1, 'Insert count differs')
    return q.lastrowid


def task_key(relation_id, kind):
    return f'{MARKER}:task:{relation_id}:{kind}'


def record_key(relation_id, kind):
    return f'{MARKER}:record:{relation_id}:{kind}'


def verify(q, plan):
    after = capture(q, plan)
    before = plan['before']
    own_events = [r for r in after[E] if r['idempotency_key'].startswith(MARKER + ':')]
    require(len(own_events) == 458 and len(after[T]) == len(after[R]) == 916, 'Incomplete completion records')
    times = {e['occurred_at'] for e in own_events}
    require(len(times) == 1, 'Mixed execution times')
    now = next(iter(times))
    for table in (P, G, C, I, F, X, 'users'):
        require(dumps(after[table]) == dumps(before[table]), 'Protected data changed: ' + table)
    require(dumps([e for e in after[E] if not e['idempotency_key'].startswith(MARKER + ':')]) == dumps(before[E]),
            'Previous business events changed')
    current = {r['id']: r for r in after[S]}
    require(current.keys() == {r['id'] for r in before[S]}, 'Service set changed')
    tasks = {r['idempotency_key']: r for r in after[T]}
    records = {r['idempotency_key']: r for r in after[R]}
    events = {r['aggregate_id']: r for r in own_events}
    for old in before[S]:
        row = current[old['id']]
        if old['id'] not in plan['ids']:
            require(dumps(row) == dumps(old), 'Other same-person service changed')
            continue
        expected = dict(old, acceptance_status='accepted', accepted_by_user_id=ACTOR, accepted_at=now,
                        delivery_stage='supervision', version=old['version']+1, updater=MARKER, update_time=now)
        for key in old:
            if key != 'first_contact_progress_json':
                require(row[key] == expected[key], 'Unexpected service field: ' + key)
        require(json.loads(row['first_contact_progress_json']) == plan['checklist'], 'Checklist snapshot differs')
        event = events[old['id']]
        require(event['reason'] == REMARK and event['operator_user_id'] is None
                and event['from_status'] == 'pending' and event['to_status'] == 'supervision'
                and event['creator'] == MARKER, 'Repair audit differs')
        refs = json.loads(event['related_object_refs'])
        for kind, title, action in TYPES:
            task = tasks[task_key(old['id'], kind)]
            record = records[record_key(old['id'], kind)]
            require(task['biz_type'] == 'student_service' and task['biz_id'] == old['id']
                    and task['task_type'] == kind and task['status'] == 'completed'
                    and task['assignee_type'] == 'user' and task['assignee_id'] == old['owner_user_id']
                    and task['summary_snapshot'] == REMARK and task['title_snapshot'] == title
                    and task['action_code'] == action and task['completed_at'] == now
                    and task['due_at'] is None and task['remind_at'] is None
                    and task['deleted'] == b'\0' and task['creator'] == MARKER, 'Completed task differs')
            require(json.loads(task['payload']) == dict(serviceRelationId=old['id'], configVersionId=plan['config_id'],
                                                       generationSource='historical_import', executor=MARKER), 'Task provenance differs')
            require(record['task_id'] == task['id'] and record['service_relation_id'] == old['id']
                    and record['contact_type'] == kind and record['successful'] == b'\1'
                    and record['remark'] == REMARK and record['operator_user_id'] == ACTOR
                    and record['submitted_at'] == now and record['next_contact_at'] is None
                    and record['deleted'] == b'\0' and record['creator'] == MARKER
                    and json.loads(record['attachment_file_ids_json']) == [], 'Completion record differs')
            require(refs[kind] == {'taskId': task['id'], 'recordId': record['id']}, 'Audit association differs')
            if kind == TYPES[0][0]:
                require(json.loads(record['checklist_snapshot_json']) == plan['checklist']
                        and record['study_plan_completed_at_first_contact'] == b'\0'
                        and json.loads(record['checklist_result_json']) == [x['key'] for x in plan['checklist']['items']],
                        'First-contact progress differs')
        require(refs['executor'] == MARKER and refs['executorUserId'] == ACTOR
                and refs['importPlanSha256'] == IMPORT_HASH, 'Audit provenance differs')
    hex_rows = rows(q, f'SELECT HEX(remark) AS h FROM {R} WHERE tenant_id=1 AND idempotency_key LIKE %s', (MARKER + ':%',))
    require(len(hex_rows) == 916 and all(x['h'].lower() == REMARK.encode('utf-8').hex() for x in hex_rows), 'Remark UTF-8 mismatch')
    return after


def mutate(c, plan, fail_after=None):
    with c.cursor() as q:
        rows(q, f'SELECT id FROM {S} WHERE tenant_id=1 AND id IN (' + sql_ids(plan['ids']) + ') ORDER BY id FOR UPDATE')
        state = capture(q, plan)
        if any(e['idempotency_key'].startswith(MARKER + ':') for e in state[E]):
            verify(q, plan)
            return 0
        require(dumps(state) == dumps(plan['before']), 'Frozen preimage changed; refuse overwrite')
        initial(state, plan)
        now = rows(q, 'SELECT NOW() AS now')[0]['now']
        count = 0
        def checkpoint():
            nonlocal count
            count += 1
            if count == fail_after:
                raise RuntimeError('Injected completion failure')
        for relation in state[S]:
            rid = relation['id']
            if rid not in plan['ids']:
                continue
            refs = dict(executor=MARKER, executorUserId=ACTOR, importPlanSha256=IMPORT_HASH,
                        historicalImport=True, originalAcceptance='pending')
            for kind, title, action in TYPES:
                task_id = insert(q, T, dict(task_type=kind, biz_type='student_service', biz_id=rid,
                    status='completed', assignee_type='user', assignee_id=relation['owner_user_id'],
                    title_snapshot=title, summary_snapshot=REMARK, action_code=action,
                    due_at=None, remind_at=None, completed_at=now, version=1,
                    payload=json.dumps(dict(serviceRelationId=rid, configVersionId=plan['config_id'],
                                            generationSource='historical_import', executor=MARKER)),
                    idempotency_key=task_key(rid, kind)), now)
                checkpoint()
                first = kind == TYPES[0][0]
                record_id = insert(q, R, dict(service_relation_id=rid, task_id=task_id, contact_type=kind,
                    successful=1, remark=REMARK, attachment_file_ids_json='[]',
                    checklist_result_json=json.dumps([x['key'] for x in plan['checklist']['items']] if first else []),
                    checklist_snapshot_json=json.dumps(plan['checklist'], ensure_ascii=False) if first else None,
                    study_plan_completed_at_first_contact=0 if first else None, delivery_data_json='{}',
                    operator_user_id=ACTOR, submitted_at=now, next_contact_at=None,
                    idempotency_key=record_key(rid, kind), request_fingerprint=sha([MARKER, rid, kind, REMARK, plan['config_id']])), now)
                refs[kind] = dict(taskId=task_id, recordId=record_id)
                checkpoint()
            q.execute(f'UPDATE {S} SET acceptance_status=%s,accepted_by_user_id=%s,accepted_at=%s,'
                      'delivery_stage=%s,first_contact_progress_json=%s,version=version+1,updater=%s,update_time=%s '
                      "WHERE tenant_id=1 AND id=%s AND deleted=0 AND version=%s AND acceptance_status='pending'",
                      ('accepted', ACTOR, now, 'supervision', json.dumps(plan['checklist'], ensure_ascii=False),
                       MARKER, now, rid, relation['version']))
            require(q.rowcount == 1, 'Service update conflict')
            checkpoint()
            insert(q, E, dict(event_type='system_data_repair', aggregate_type='student-service', aggregate_id=rid,
                operator_user_id=None, from_status='pending', to_status='supervision', reason=REMARK,
                related_object_refs=json.dumps(refs), occurred_at=now, idempotency_key=f'{MARKER}:event:{rid}'), now)
            checkpoint()
        require(count == 2748, 'Write count differs')
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
                cols = [x['COLUMN_NAME'] for x in rows(q, "SELECT COLUMN_NAME FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name=%s AND EXTRA NOT LIKE '%%VIRTUAL GENERATED%%' AND EXTRA NOT LIKE '%%STORED GENERATED%%' ORDER BY ORDINAL_POSITION", (table,))]
                if plan['before'][table]:
                    q.executemany(f'INSERT INTO {table} (' + ','.join(cols) + ') VALUES (' + ','.join(['%s']*len(cols)) + ')',
                                  [tuple(r[k] for k in cols) for r in plan['before'][table]])
        c.commit()
        for point in (1, 2, 5, 6, 1374, 2748):
            try:
                mutate(c, plan, point)
                raise AssertionError('Failure injection did not fire')
            except RuntimeError as e:
                require(str(e) == 'Injected completion failure', 'Unexpected fixture exception: ' + str(e))
                c.rollback()
            with c.cursor() as q:
                require(dumps(capture(q, plan)) == dumps(plan['before']), 'Failed transaction left effects')
            c.rollback()
        require(mutate(c, plan) == 2748, 'Fixture initial write count differs')
        c.commit()
        require(mutate(c, plan) == 0, 'Fixture repeat wrote data')
        c.rollback()
        with c.cursor() as q:
            q.execute(f'DELETE FROM {E} WHERE tenant_id=1 AND idempotency_key=%s', (f'{MARKER}:event:{plan["ids"][0]}',))
            try:
                mutate(c, plan)
                raise AssertionError('Partial state was accepted')
            except RuntimeError as e:
                require(str(e) == 'Incomplete completion records', 'Unexpected partial-state error')
        c.rollback()
        with c.cursor() as q:
            verify(q, plan)
        save(ROOT / 'fixture.json', dict(plan_sha256=sha(plan), script_sha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
                                       first_writes=2748, repeat_writes=0, fault_points=6, partial_rejected=True, recovery=True))
        print('MySQL temporary-table initial/6 failures/rollback/recovery/replay/partial-state checks PASS')
    finally:
        c.rollback(); c.close()


def apply(plan):
    evidence = loads((ROOT / 'fixture.json').read_text())
    require(evidence['plan_sha256'] == sha(plan) and evidence['script_sha256'] == hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
            'Matching fixture evidence required')
    c = connect('zsjos-mysql-1')
    try:
        with c.cursor() as q:
            q.execute('SET NAMES utf8mb4'); q.execute('SET SESSION innodb_lock_wait_timeout=5')
            require(rows(q, 'SELECT GET_LOCK(%s,0) AS ok', (MARKER,))[0]['ok'] == 1, 'Repair already running')
        count = mutate(c, plan)
        c.commit()
        with c.cursor() as q:
            after = verify(q, plan)
        save(ROOT / 'after.json', after)
        print(json.dumps({'writes': count, 'supervision_services': 458, 'completed_tasks': 916,
                          'remark_records': 916, 'audit_events': 458, 'sha256': sha(plan)}))
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
    require(environment == 'test', 'This repair is authorized only for test')
    os.umask(0o077); ROOT.mkdir(mode=0o700, parents=True, exist_ok=True)
    if args.mode == 'prepare':
        prepare(); return
    plan = loads((ROOT / 'plan.json').read_text(encoding='utf-8'))
    require(args.sha256 == sha(plan), 'Exact frozen plan hash required')
    if args.mode == 'fixture':
        fixture(plan)
    elif args.mode == 'apply':
        apply(plan)
    else:
        c = connect('zsjos-mysql-1')
        try:
            with c.cursor() as q:
                q.execute('SET NAMES utf8mb4'); q.execute('START TRANSACTION WITH CONSISTENT SNAPSHOT, READ ONLY')
                after = verify(q, plan)
            save(ROOT / 'independent-after.json', after)
            print('Independent 458 supervision / 916 completed tasks / 916 exact remarks / protected-data verification PASS')
        finally:
            c.rollback(); c.close()


if __name__ == '__main__':
    main()
