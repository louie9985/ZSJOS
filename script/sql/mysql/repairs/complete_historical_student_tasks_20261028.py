#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Complete the original 80 historical services under the user's corrected instruction.

Test tenant 1 only; not a migration. Preserve prior administrative history and
freeze current preimages. See historical-student-supervision-20261028.md.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path

import advance_pending_students_20261028 as previous
from advance_imported_students_20261027 import checklist, TYPES
from qualify_education_leads_20261027 import dumps, loads, rows, require, save
from transfer_student_20261015 import connect

ROOT = Path('/opt/zsjos-runtime/backups/historical-student-completion-20261028')
MARKER = 'historical-completion-20261028'
SOURCE_HASH = 'a1c590387ccd84289435017aebe7d9dda0c4b0f26468843dec4a9fd4dffc2125'
REMARK, ACTOR = previous.REMARK, 1
S, T, R, E = previous.S, previous.T, previous.R, previous.E
F = 'zsjos_student_contact_config_version'
TABLES = previous.TABLES + (F,)
COUNT, WRITES = 80, 480


def sha(value):
    return hashlib.sha256(dumps(value).encode('utf-8')).hexdigest()


def code_hash():
    # Evidence is invalidated if any imported implementation changes.
    return sha({name: hashlib.sha256(Path(__file__).with_name(name).read_bytes()).hexdigest()
                for name in (Path(__file__).name, 'advance_pending_students_20261028.py',
                             'advance_imported_students_20261027.py', 'qualify_education_leads_20261027.py',
                             'transfer_student_20261015.py')})


def capture(q, plan):
    state = previous.capture(q, plan)
    state[F] = rows(q, f'SELECT * FROM {F} WHERE tenant_id=1 AND deleted=0 '
                      "AND (status='published' OR id=%s) ORDER BY id", (plan['config_id'],))
    return state


def initial(state, plan):
    targets = [s for s in state[S] if s['id'] in plan['ids']]
    require(len(targets) == COUNT and len(plan['people']) == 78, 'Frozen scope differs')
    require(not state[T] and not state[previous.X], 'Teaching tasks/extension appeared')
    require(len(state[R]) == COUNT and all(r['creator'] == previous.MARKER
            and r['contact_type'] == 'student_delivery_stage' and r['remark'] == REMARK
            and r['deleted'] == b'\0' for r in state[R]), 'Existing contact history changed')
    require({r['service_relation_id'] for r in state[R]} == set(plan['ids']), 'Previous record association differs')
    require(len(state[F]) == 1 and state[F][0]['id'] == plan['config_id']
            and checklist(state[F][0]) == plan['checklist'], 'Published checklist changed')
    require(not any(e['idempotency_key'].startswith(MARKER + ':') for e in state[E]), 'Partial completion exists')
    require(len(state['users']) == len(set(plan['owners'] + [ACTOR])) and all(
        u['status'] == 0 and u['deleted'] == b'\0' for u in state['users']), 'Owner/executor unavailable')
    for s in targets:
        require(s['tenant_id'] == 1 and s['deleted'] == b'\0' and s['status'] == 'active'
                and s['teaching_delivery'] == b'\1' and s['teaching_closed'] == b'\0'
                and s['acceptance_status'] == 'accepted' and s['delivery_stage'] == 'supervision'
                and s['first_contact_progress_json'] is None and s['accepted_by_user_id'] == ACTOR,
                'Target teaching lifecycle progressed since original adjustment')


def prepare():
    require(not (ROOT / 'plan.json').exists(), 'Preserve frozen evidence')
    source = loads((previous.ROOT / 'plan.json').read_text(encoding='utf-8'))
    require(sha(source) == SOURCE_HASH, 'Original target provenance differs')
    c = connect('zsjos-mysql-1')
    try:
        with c.cursor() as q:
            q.execute('SET NAMES utf8mb4')
            q.execute('START TRANSACTION WITH CONSISTENT SNAPSHOT, READ ONLY')
            config = rows(q, f"SELECT * FROM {F} WHERE tenant_id=1 AND deleted=0 AND status='published'")
            require(len(config) == 1, 'Published policy ambiguous')
            plan = {k: source[k] for k in ('ids', 'people', 'classes', 'orders', 'owners', 'cutoff')}
            current = rows(q, f'SELECT * FROM {S} WHERE tenant_id=1 AND id IN ({previous.ids(plan["ids"])}) ORDER BY id')
            require(len(current) == COUNT and {s['person_id'] for s in current} == set(plan['people']),
                    'Original population changed')
            # Subsequent class/owner assignments are authoritative; never restore the old import assignment.
            plan.update(classes=sorted({s['class_id'] for s in current}),
                        owners=sorted({s['owner_user_id'] for s in current}),
                        orders=sorted({s['order_id'] for s in current if s['order_id']}))
            plan.update(source_sha256=SOURCE_HASH, config_id=config[0]['id'], checklist=checklist(config[0]))
            plan['before'] = capture(q, plan)
            initial(plan['before'], plan)
            save(ROOT / 'plan.json', plan)
        print(json.dumps(dict(services=COUNT, completed_tasks=COUNT*2, completed_records=COUNT*2,
                             writes=WRITES, sha256=sha(plan))))
    finally:
        c.rollback(); c.close()


def insert(q, table, values, now):
    fields = dict(values, tenant_id=1, creator=MARKER, updater=MARKER, create_time=now, update_time=now)
    q.execute(f'INSERT INTO {table} (' + ','.join(fields) + ') VALUES ('
              + ','.join(['%s'] * len(fields)) + ')', tuple(fields.values()))
    require(q.rowcount == 1, 'Insert count differs')
    return q.lastrowid


def refs(plan):
    return dict(generationSource='historical_adjustment', executor=MARKER, executorUserId=ACTOR,
                sourceAdjustment=previous.MARKER, sourcePlanSha256=SOURCE_HASH,
                planSha256=sha(plan), completionAuthorized=True, actualContactPerformed=False)


def key(kind, rid, contact_type=''):
    return f'{MARKER}:{kind}:{rid}' + (':' + contact_type if contact_type else '')


def verify(q, plan):
    state = capture(q, plan)
    own = {t: [r for r in state[t] if r['idempotency_key'].startswith(MARKER + ':')] for t in (T, R, E)}
    require(len(own[T]) == len(own[R]) == COUNT*2 and len(own[E]) == COUNT, 'Partial completion state')
    times = {e['occurred_at'] for e in own[E]}
    require(len(times) == 1, 'Mixed completion times')
    now = next(iter(times))
    for table in TABLES:
        if table == S:
            continue
        protected = [r for r in state[table] if not r['idempotency_key'].startswith(MARKER + ':')] if table in own else state[table]
        require(dumps(protected) == dumps(plan['before'][table]), 'Protected data changed: ' + table)
    require(dumps(state['users']) == dumps(plan['before']['users']), 'User state changed')
    current = {r['id']: r for r in state[S]}
    require(current.keys() == {r['id'] for r in plan['before'][S]}, 'Service population changed')
    for old in plan['before'][S]:
        actual = dict(current[old['id']])
        expected = dict(old)
        if old['id'] in plan['ids']:
            require(json.loads(actual.pop('first_contact_progress_json')) == plan['checklist'], 'Progress snapshot differs')
            expected.pop('first_contact_progress_json')
            expected.update(version=old['version']+1, updater=MARKER, update_time=now)
        require(dumps(actual) == dumps(expected), 'Unexpected service mutation')
    tasks = {t['idempotency_key']: t for t in own[T]}
    records = {r['idempotency_key']: r for r in own[R]}
    events = {e['aggregate_id']: e for e in own[E]}
    require(set(events) == set(plan['ids']), 'Audit population differs')
    provenance = refs(plan)
    for rid in plan['ids']:
        event = events[rid]
        links = {}
        for kind, title, action in TYPES:
            t, r = tasks[key('task', rid, kind)], records[key('record', rid, kind)]
            require(t['task_type'] == kind and t['biz_type'] == 'student_service' and t['biz_id'] == rid
                    and t['status'] == 'completed' and t['assignee_type'] == 'user'
                    and t['assignee_id'] == current[rid]['owner_user_id'] and t['title_snapshot'] == title
                    and t['summary_snapshot'] == REMARK and t['action_code'] == action
                    and t['completed_at'] == now and t['due_at'] is None and t['remind_at'] is None
                    and t['deleted'] == b'\0' and t['creator'] == MARKER and t['version'] == 1
                    and json.loads(t['payload']) == dict(provenance, serviceRelationId=rid, configVersionId=plan['config_id']),
                    'Completed task differs')
            first = kind == 'student_first_contact'
            require(r['service_relation_id'] == rid and r['task_id'] == t['id'] and r['contact_type'] == kind
                    and r['successful'] == b'\1' and r['remark'] == REMARK and r['operator_user_id'] == ACTOR
                    and r['submitted_at'] == now and r['next_contact_at'] is None
                    and r['deleted'] == b'\0' and r['creator'] == MARKER
                    and r['request_fingerprint'] == sha([MARKER, rid, kind, REMARK, provenance])
                    and json.loads(r['attachment_file_ids_json']) == []
                    and json.loads(r['delivery_data_json']) == provenance
                    and json.loads(r['checklist_result_json']) == ([i['key'] for i in plan['checklist']['items']] if first else []),
                    'Completion record differs')
            require((json.loads(r['checklist_snapshot_json']) == plan['checklist']
                     and r['study_plan_completed_at_first_contact'] == b'\0') if first else
                    (r['checklist_snapshot_json'] is None and r['study_plan_completed_at_first_contact'] is None),
                    'First contact/plan linkage differs')
            links[kind] = dict(taskId=t['id'], recordId=r['id'])
        require(event['event_type'] == 'system_data_repair' and event['operator_user_id'] is None
                and event['from_status'] == event['to_status'] == 'supervision' and event['reason'] == REMARK
                and event['creator'] == MARKER and event['deleted'] == b'\0'
                and json.loads(event['related_object_refs']) == dict(provenance, **links), 'Audit differs')
    for table, column, count in ((T, 'summary_snapshot', COUNT*2), (R, 'remark', COUNT*2), (E, 'reason', COUNT)):
        hexes = rows(q, f'SELECT HEX({column}) h FROM {table} WHERE tenant_id=1 AND idempotency_key LIKE %s', (MARKER + ':%',))
        require(len(hexes) == count and all(v['h'].lower() == REMARK.encode('utf-8').hex() for v in hexes), 'UTF-8 mismatch')
    return state


def mutate(c, plan, fail_after=None):
    with c.cursor() as q:
        rows(q, f'SELECT id FROM {S} WHERE tenant_id=1 AND id IN ({previous.ids(plan["ids"])}) ORDER BY id FOR UPDATE')
        state = capture(q, plan)
        if any(e['idempotency_key'].startswith(MARKER + ':') for e in state[E]):
            verify(q, plan); return 0
        require(dumps(state) == dumps(plan['before']), 'Frozen preimage drift')
        initial(state, plan)
        now = rows(q, 'SELECT NOW() now')[0]['now']
        provenance, count = refs(plan), 0
        def checkpoint():
            nonlocal count
            count += 1
            if count == fail_after:
                raise RuntimeError('Injected completion failure')
        for s in state[S]:
            rid = s['id']
            if rid not in plan['ids']:
                continue
            links = {}
            for kind, title, action in TYPES:
                task_id = insert(q, T, dict(task_type=kind, biz_type='student_service', biz_id=rid,
                    status='completed', assignee_type='user', assignee_id=s['owner_user_id'],
                    title_snapshot=title, summary_snapshot=REMARK, action_code=action,
                    due_at=None, remind_at=None, completed_at=now, version=1,
                    payload=json.dumps(dict(provenance, serviceRelationId=rid, configVersionId=plan['config_id'])),
                    idempotency_key=key('task', rid, kind)), now)
                checkpoint()
                first = kind == 'student_first_contact'
                record_id = insert(q, R, dict(service_relation_id=rid, task_id=task_id, contact_type=kind,
                    successful=1, remark=REMARK, attachment_file_ids_json='[]',
                    checklist_result_json=json.dumps([i['key'] for i in plan['checklist']['items']] if first else []),
                    checklist_snapshot_json=json.dumps(plan['checklist'], ensure_ascii=False) if first else None,
                    study_plan_completed_at_first_contact=0 if first else None,
                    delivery_data_json=json.dumps(provenance), operator_user_id=ACTOR, submitted_at=now,
                    next_contact_at=None, idempotency_key=key('record', rid, kind),
                    request_fingerprint=sha([MARKER, rid, kind, REMARK, provenance])), now)
                links[kind] = dict(taskId=task_id, recordId=record_id)
                checkpoint()
            q.execute(f'UPDATE {S} SET first_contact_progress_json=%s,version=version+1,updater=%s,update_time=%s '
                      "WHERE tenant_id=1 AND id=%s AND version=%s AND deleted=0 AND acceptance_status='accepted' AND delivery_stage='supervision'",
                      (json.dumps(plan['checklist'], ensure_ascii=False), MARKER, now, rid, s['version']))
            require(q.rowcount == 1, 'Service version conflict')
            checkpoint()
            insert(q, E, dict(event_type='system_data_repair', aggregate_type='student-service', aggregate_id=rid,
                operator_user_id=None, from_status='supervision', to_status='supervision', reason=REMARK,
                related_object_refs=json.dumps(dict(provenance, **links)), occurred_at=now,
                idempotency_key=key('event', rid)), now)
            checkpoint()
        require(count == WRITES, 'Write count differs')
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
                columns = [r['COLUMN_NAME'] for r in rows(q, "SELECT COLUMN_NAME FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name=%s AND EXTRA NOT LIKE '%%VIRTUAL GENERATED%%' AND EXTRA NOT LIKE '%%STORED GENERATED%%' ORDER BY ORDINAL_POSITION", (table,))]
                if plan['before'][table]:
                    q.executemany(f'INSERT INTO {table} (' + ','.join(columns) + ') VALUES ('
                                  + ','.join(['%s'] * len(columns)) + ')',
                                  [tuple(r[col] for col in columns) for r in plan['before'][table]])
        c.commit()
        for point in (1, 2, 5, 6, 240, WRITES):
            try:
                mutate(c, plan, point)
                raise AssertionError('Fault did not fire')
            except RuntimeError as error:
                require(str(error) == 'Injected completion failure', 'Unexpected fault error: ' + str(error))
            c.rollback()
            with c.cursor() as q:
                require(dumps(capture(q, plan)) == dumps(plan['before']), 'Rollback left effects')
            c.rollback()
        with c.cursor() as q:
            q.execute(f'UPDATE {S} SET version=version+1 WHERE tenant_id=1 AND id=%s', (plan['ids'][0],))
        try:
            mutate(c, plan)
            raise AssertionError('Preimage drift accepted')
        except RuntimeError as error:
            require(str(error) == 'Frozen preimage drift', 'Unexpected drift error')
        c.rollback()
        require(mutate(c, plan) == WRITES, 'First execution count differs')
        c.commit()
        require(mutate(c, plan) == 0, 'Replay wrote data')
        c.rollback()
        # Exercise a structurally complete but wrongly pending task: replay must not bless it.
        with c.cursor() as q:
            q.execute(f"UPDATE {T} SET status='pending' WHERE tenant_id=1 AND idempotency_key=%s",
                      (key('task', plan['ids'][0], 'student_first_contact'),))
        try:
            mutate(c, plan)
            raise AssertionError('Pending first-contact corruption accepted')
        except RuntimeError as error:
            require(str(error) == 'Completed task differs', 'Unexpected partial error')
        c.rollback()
        with c.cursor() as q:
            verify(q, plan)
        save(ROOT / 'fixture.json', dict(plan_sha256=sha(plan), code_sha256=code_hash(), first_writes=WRITES,
             repeat_writes=0, failures=6, rollback=True, recovery=True, drift_rejected=True, partial_rejected=True))
        print('PASS MySQL first/repeat/6 fault rollbacks/recovery/drift/pending-task corruption rejection')
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
    require(args.sha256 == sha(plan), 'Exact frozen plan hash required')
    if args.mode == 'fixture':
        fixture(plan); return
    c = connect('zsjos-mysql-1')
    try:
        with c.cursor() as q:
            q.execute('SET NAMES utf8mb4'); q.execute("SET SESSION time_zone='+08:00'")
            q.execute('SET SESSION innodb_lock_wait_timeout=5')
        if args.mode == 'apply':
            evidence = loads((ROOT / 'fixture.json').read_text(encoding='utf-8'))
            require(evidence['plan_sha256'] == sha(plan) and evidence['code_sha256'] == code_hash(),
                    'Matching fixture evidence required')
            with c.cursor() as q:
                require(rows(q, 'SELECT GET_LOCK(%s,0) ok', (MARKER,))[0]['ok'] == 1, 'Repair already running')
            count = mutate(c, plan)
            c.commit()
            print(json.dumps(dict(writes=count, services=COUNT, completed_tasks=COUNT*2, completed_records=COUNT*2)))
        with c.cursor() as q:
            q.execute('START TRANSACTION WITH CONSISTENT SNAPSHOT, READ ONLY')
            after = verify(q, plan)
        save(ROOT / ('after.json' if args.mode == 'apply' else 'independent-after.json'), after)
        print('PASS independent completion/progress/UTF-8/protected-state verification')
    finally:
        c.rollback(); c.close()


if __name__ == '__main__':
    main()
