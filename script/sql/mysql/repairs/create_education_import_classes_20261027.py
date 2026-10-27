#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Two explicitly authorized test-only class inserts, with transaction and replay guards.

Ordinary same-department class authorization remains unchanged. The user authorized
this system import exception. No historical classes or account permissions change.
"""
import argparse
import datetime as dt
import hashlib
import json
import os
from pathlib import Path
import secrets
from zoneinfo import ZoneInfo

import openpyxl
from import_education_students_20261027 import (
    EVIDENCE, SOURCE, query, read, save, digest, encoded, require, connect,
)

MARKER = 'edu-import-classes-20261027'
CLASS = 'zsjos_delivery_class'
EVENT = 'zsjos_business_event'


def rows(q, sql, args=()):
    q.execute(sql, args or None)
    return list(q.fetchall())


def prepare():
    require(not (EVIDENCE / 'class-plan.json').exists(), 'Class plan already exists')
    w = openpyxl.load_workbook(SOURCE, data_only=True)
    before = query('SELECT * FROM zsjos_delivery_class WHERE tenant_id=1 ORDER BY id')
    plan = {'before': before, 'classes': [], 'source_sha256': hashlib.sha256(SOURCE.read_bytes()).hexdigest()}
    original = next(x for x in before if x['id'] == 57)
    for index in (4, 7):
        sheet = w.worksheets[index]
        data = [tuple(c.value for c in row) for row in sheet.iter_rows(min_row=2)
                if row[0].value is not None]
        subset = data if index == 4 else data[:7]
        names = {str(r[5]).strip() for r in subset}
        planners = {str(r[6]).strip() for r in subset}
        require(len(names) == len(planners) == 1, 'Class/planner ambiguity')
        users = query('SELECT u.id,u.nickname,u.dept_id,d.name AS dept_name FROM system_users u '
                      'JOIN system_dept d ON d.id=u.dept_id AND d.tenant_id=u.tenant_id AND d.deleted=0 '
                      'WHERE u.tenant_id=1 AND u.deleted=0 AND u.status=0 AND u.nickname=%s', (next(iter(planners)),))
        require(len(users) == 1, 'Planner missing or ambiguous')
        user = users[0]
        roles = query("SELECT r.id FROM system_user_role ur JOIN system_role r ON r.id=ur.role_id AND r.tenant_id=ur.tenant_id "
                      "WHERE ur.tenant_id=1 AND ur.deleted=0 AND r.deleted=0 AND r.status=0 AND r.code='study_planner' AND ur.user_id=%s", (user['id'],))
        require(roles, 'Planner does not satisfy existing class eligibility')
        name = next(iter(names))
        require(not any(x['class_name'] == name and x['homeroom_user_id'] == user['id']
                        and x['deleted'] in (b'\x00', 0) for x in before), 'Target class already exists')
        now = dt.datetime.now(ZoneInfo('Asia/Shanghai'))
        fields = dict(class_no='BJ' + now.strftime('%Y%m%d%H%M%S') + f'{secrets.randbelow(10000):04d}',
                      class_name=name, system_class=0, class_kind='NORMAL', homeroom_user_id=user['id'],
                      homeroom_user_name_snapshot=user['nickname'], dept_id=user['dept_id'],
                      dept_name_snapshot=user['dept_name'], status='SERVING', version=0,
                      creator=MARKER, updater=MARKER, tenant_id=1,
                      exam_schedule_id=original['exam_schedule_id'] if index == 4 else None,
                      exam_schedule_snapshot=original['exam_schedule_snapshot'] if index == 4 else None)
        if index == 4:
            require(name == original['class_name'], 'Psychology class source changed')
        plan['classes'].append(fields)
    save(EVIDENCE / 'class-plan.json', plan)
    print(json.dumps({'classes': 2, 'events': 2, 'sha256': digest(plan)}))


def check(q, plan):
    actual = rows(q, f'SELECT * FROM {CLASS} WHERE tenant_id=1 ORDER BY id')
    prior_ids = {x['id'] for x in plan['before']}
    protected = [x for x in actual if x['id'] in prior_ids]
    require(encoded(protected) == encoded(plan['before']), 'Existing class changed')
    own = [x for x in actual if x['creator'] == MARKER]
    events = rows(q, f'SELECT * FROM {EVENT} WHERE tenant_id=1 AND idempotency_key LIKE %s ORDER BY id', (MARKER + ':%',))
    if not own and not events:
        return False
    require(len(own) == len(events) == 2, 'Partial class import detected')
    for i, expected in enumerate(plan['classes']):
        found = [x for x in own if x['class_no'] == expected['class_no']]
        require(len(found) == 1, 'Imported class missing')
        row = found[0]
        for key, value in expected.items():
            require(row[key] == (b'\x00' if key == 'system_class' else value), 'Imported class field changed: ' + key)
        require(any(e['aggregate_id'] == row['id'] and e['operator_user_id'] is None
                    and e['idempotency_key'] == MARKER + ':' + str(i) for e in events), 'Class repair event missing')
        hexa = rows(q, f'SELECT HEX(class_name) AS hex_name FROM {CLASS} WHERE tenant_id=1 AND id=%s', (row['id'],))[0]
        require(hexa['hex_name'].lower() == expected['class_name'].encode('utf-8').hex(), 'Class UTF-8 mismatch')
    return True


def mutate(c, plan, fail_after=None):
    with c.cursor() as q:
        rows(q, f'SELECT id FROM {CLASS} WHERE tenant_id=1 ORDER BY id FOR UPDATE')
        if check(q, plan):
            return 0
        count = 0
        for i, fields in enumerate(plan['classes']):
            require(not rows(q, f'SELECT id FROM {CLASS} WHERE tenant_id=1 AND deleted=0 AND class_name=%s AND homeroom_user_id=%s',
                             (fields['class_name'], fields['homeroom_user_id'])), 'Matching class appeared')
            cols = list(fields)
            q.execute(f'INSERT INTO {CLASS} (' + ','.join(cols) + ') VALUES (' + ','.join(['%s']*len(cols)) + ')', tuple(fields.values()))
            class_id = q.lastrowid
            count += 1
            if count == fail_after:
                raise RuntimeError('Injected class import failure')
            reason = '按用户确认导入教务学员：系统初始化班级及表内班主任，不修改日常权限；考期按确认配置'
            q.execute(f'INSERT INTO {EVENT} (event_type,aggregate_type,aggregate_id,operator_user_id,reason,related_object_refs,occurred_at,idempotency_key,creator,updater,tenant_id) '
                      "VALUES ('system_data_repair','delivery-class',%s,NULL,%s,%s,NOW(),%s,%s,%s,1)",
                      (class_id, reason, json.dumps({'executor': MARKER, 'sourceSha256': plan['source_sha256']}), MARKER+':'+str(i), MARKER, MARKER))
            count += 1
            if count == fail_after:
                raise RuntimeError('Injected class import failure')
        require(check(q, plan), 'Class postcondition failed')
        return count


def fixture(plan):
    c = connect('zsjos-mysql-1')
    try:
        with c.cursor() as q:
            q.execute('SET NAMES utf8mb4')
            for table in (CLASS, EVENT):
                ddl = rows(q, f'SHOW CREATE TABLE {table}')[0]['Create Table']
                q.execute(ddl.replace('CREATE TABLE', 'CREATE TEMPORARY TABLE', 1))
            cols = [x['COLUMN_NAME'] for x in rows(q, "SELECT COLUMN_NAME FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name=%s AND EXTRA NOT LIKE '%%VIRTUAL GENERATED%%' AND EXTRA NOT LIKE '%%STORED GENERATED%%' ORDER BY ORDINAL_POSITION", (CLASS,))]
            for row in plan['before']:
                q.execute(f'INSERT INTO {CLASS} (' + ','.join(cols) + ') VALUES (' + ','.join(['%s']*len(cols)) + ')', tuple(row[k] for k in cols))
        c.commit()
        for point in (1, 2, 3, 4):
            try:
                mutate(c, plan, point)
                raise AssertionError('Failure injection did not fire')
            except RuntimeError as e:
                require(str(e) == 'Injected class import failure', 'Unexpected fixture failure')
                c.rollback()
            with c.cursor() as q:
                require(not check(q, plan), 'Failure left class effects')
            c.rollback()
        require(mutate(c, plan) == 4, 'Initial fixture count differs')
        c.commit()
        require(mutate(c, plan) == 0, 'Replay fixture wrote data')
        c.rollback()
        # An incomplete event trail must be rejected rather than silently repaired.
        with c.cursor() as q:
            q.execute(f'DELETE FROM {EVENT} WHERE tenant_id=1 AND idempotency_key=%s', (MARKER+':0',))
            try:
                check(q, plan)
                raise AssertionError('Partial state accepted')
            except RuntimeError as e:
                require(str(e) == 'Partial class import detected', 'Unexpected partial-state result')
        c.rollback()
        with c.cursor() as q:
            require(check(q, plan), 'Recovery verification failed')
        save(EVIDENCE / 'class-fixture.json', {'plan_sha256': digest(plan), 'script_sha256': hashlib.sha256(Path(__file__).read_bytes()).hexdigest(), 'failure_points': 4, 'first': 4, 'repeat': 0, 'partial_rejected': True})
        print('Class temporary-table first/four-failures/recovery/replay/partial-state checks PASS')
    finally:
        c.rollback(); c.close()


def apply(plan):
    evidence = read(EVIDENCE / 'class-fixture.json')
    require(evidence['plan_sha256'] == digest(plan) and evidence['script_sha256'] == hashlib.sha256(Path(__file__).read_bytes()).hexdigest(), 'Matching class fixture required')
    c = connect('zsjos-mysql-1')
    try:
        with c.cursor() as q:
            q.execute('SET NAMES utf8mb4')
            q.execute('SET SESSION innodb_lock_wait_timeout=5')
            require(rows(q, 'SELECT GET_LOCK(%s,0) AS acquired', (MARKER,))[0]['acquired'] == 1, 'Class import busy')
        count = mutate(c, plan)
        c.commit()
        with c.cursor() as q:
            require(check(q, plan), 'Committed classes invalid')
            result = rows(q, f'SELECT * FROM {CLASS} WHERE tenant_id=1 AND creator=%s ORDER BY id', (MARKER,))
        save(EVIDENCE / 'class-result.json', result)
        print(json.dumps({'writes': count, 'class_ids': [x['id'] for x in result]}))
    finally:
        c.rollback(); c.close()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('mode', choices=['prepare', 'fixture', 'apply'])
    parser.add_argument('--sha256')
    args = parser.parse_args()
    marker = Path('/etc/zsjos/agent-environment')
    environment = os.getenv('ZSJOS_AGENT_ENV')
    if environment is None:
        environment = marker.read_text().strip() if marker.exists() else 'local'
    require(environment == 'test', 'Test-only authorized class import')
    os.umask(0o077); EVIDENCE.mkdir(mode=0o700, parents=True, exist_ok=True)
    if args.mode == 'prepare':
        prepare(); return
    plan = read(EVIDENCE / 'class-plan.json')
    require(args.sha256 == digest(plan), 'Exact class plan digest required')
    require(plan['source_sha256'] == hashlib.sha256(SOURCE.read_bytes()).hexdigest(), 'Source changed')
    fixture(plan) if args.mode == 'fixture' else apply(plan)


if __name__ == '__main__':
    main()
