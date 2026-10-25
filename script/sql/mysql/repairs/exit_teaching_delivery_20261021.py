#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Exact authorized test-only teaching exit; see media-teaching-exit-20261021.md."""
import argparse
import hashlib
import os
from pathlib import Path

from transfer_student_20261015 import connect, encoded, decoded, require

TARGETS = {1111: (8825, 'XY202610181456440016'), 1112: (8826, 'XY202610181456530017')}
EVIDENCE = Path('/opt/zsjos-runtime/backups/media-teaching-exit-20261021-test')


def save(name, value):
    EVIDENCE.mkdir(mode=0o700, parents=True, exist_ok=True)
    require(EVIDENCE.stat().st_mode & 0o077 == 0, 'Evidence directory must be private')
    data = encoded(value).encode('utf-8')
    fd = os.open(EVIDENCE / name, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(fd, 'wb') as f:
        f.write(data)
        f.flush()
        os.fsync(f.fileno())
    return hashlib.sha256(data).hexdigest()


def read(q, sql):
    q.execute(sql)
    return list(q.fetchall())


def services(q, lock=False):
    return read(q, 'SELECT * FROM zsjos_service_relation WHERE tenant_id=1 AND id IN (1111,1112) ORDER BY id'
                + (' FOR UPDATE' if lock else ''))


def linked(q):
    # Discover actual columns: some immutable snapshot tables have no logical-delete column.
    metadata = read(q, "SELECT table_name,column_name FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name LIKE 'zsjos_%'")
    tables = {}
    for r in metadata:
        tables.setdefault(r['TABLE_NAME'], set()).add(r['COLUMN_NAME'])
    result = {}
    for table, cols in sorted(tables.items()):
        if '_bak_' in table or 'tenant_id' not in cols or 'id' not in cols:
            continue
        clauses = []
        for col in ('service_relation_id', 'create_service_relation_id'):
            if col in cols:
                clauses.append(f'{col} IN (1111,1112)')
        if 'student_person_id' in cols:
            clauses.append('student_person_id IN (8825,8826)')
        if 'account_id' in cols:
            clauses.append('account_id IN (210,211,212)')
        if 'account_ids_json' in cols:
            clauses.extend(f"JSON_CONTAINS(account_ids_json,'{x}')" for x in (210,211,212))
        if table == 'zsjos_person':
            clauses.append('id IN (8825,8826)')
        if table == 'zsjos_business_task':
            clauses.append("(biz_type='student_service' AND biz_id IN (1111,1112)) OR (biz_type='media_account_diagnosis' AND biz_id IN (210,211,212)) OR (biz_type='student_delivery_stage' AND biz_id IN (SELECT id FROM zsjos_student_delivery_stage WHERE tenant_id=1 AND account_id IN (210,211,212)))")
        if clauses:
            result[table] = read(q, f"SELECT * FROM `{table}` WHERE tenant_id=1 AND ({' OR '.join(clauses)}) ORDER BY id")
    return result


def after(rows):
    return [dict(r, teaching_delivery=b'\x00') for r in rows]


def change(q, before, inject_failure=False):
    current = services(q, True)
    if current == after(before):
        return 0
    require(current == before, 'Service before-image drift; no changes authorized')
    for index, r in enumerate(current):
        q.execute('UPDATE zsjos_service_relation SET teaching_delivery=0,update_time=update_time '
                  'WHERE tenant_id=1 AND id=%s AND teaching_delivery=1', (r['id'],))
        require(q.rowcount == 1, 'Unexpected update count')
        if inject_failure and index == 0:
            q.execute("SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='injected rollback verification'")
    require(services(q) == after(before), 'Unexpected service field change')
    return len(current)


def fixture(c):
    with c.cursor() as q:
        original = services(q)
        q.execute('SHOW CREATE TABLE zsjos_service_relation')
        ddl = q.fetchone()['Create Table']
        q.execute(ddl.replace('CREATE TABLE ', 'CREATE TEMPORARY TABLE ', 1))
        # Temporary table shadows only this session; these are the two authorized rows.
        require(len(original) == 2, 'Fixture targets missing')
        columns = list(original[0])
        q.executemany('INSERT INTO zsjos_service_relation (' + ','.join('`'+k+'`' for k in columns)
                      + ') VALUES (' + ','.join(['%s'] * len(columns)) + ')',
                      [tuple(r[k] for k in columns) for r in original])
        before = services(q)
    c.commit()
    require(len(before) == 2 and all(r['teaching_delivery'] == b'\x01' for r in before), 'Fixture requires initial state')
    try:
        with c.cursor() as q:
            change(q, before, True)
    except Exception as e:
        c.rollback()
        require('injected rollback verification' in str(e), 'Unexpected fixture failure')
    else:
        raise RuntimeError('Injected failure did not occur')
    with c.cursor() as q:
        require(services(q) == before, 'Rollback failed')
        require(change(q, before) == 2, 'Recovery failed')
    c.commit()
    with c.cursor() as q:
        require(change(q, before) == 0, 'Replay failed')
    c.rollback()
    print('PASS temporary MySQL rollback, initial/recovery, whole-row preservation and replay')


def prepare(c):
    with c.cursor() as q:
        rows = services(q)
        require(len(rows) == 2, 'Target count changed')
        for r in rows:
            require((r['person_id'], r['status'], r['acceptance_status'], r['deleted'], r['teaching_delivery'],
                     r['content_director_user_id'], r['operator_user_id'], r['class_id'])
                    == (TARGETS[r['id']][0], 'active', 'accepted', b'\x00', b'\x01', 47, 525, 1), 'Target contract changed')
            require(all(r[k] is None for k in ('order_id','order_item_id','registration_case_id','owner_user_id')), 'Teaching association changed')
        state = linked(q)
        people = state['zsjos_person']
        require({(r['id'],r['person_no'],r['deleted']) for r in people}
                == {(p,n,b'\x00') for p,n in TARGETS.values()}, 'Identity mismatch')
        require(not state['zsjos_class_transfer_request'], 'Transfer requires review')
        require(not state['zsjos_student_contact_record'], 'Teaching history requires review')
        require(not state['zsjos_business_task'], 'Task scope requires review')
        accounts = state['zsjos_media_account']
        require({r['id'] for r in accounts} == {210,211,212}, 'Account set changed')
        require(all(r['create_service_relation_id']==1111 and r['director_user_id']==47 and r['owner_operator_user_id']==525 for r in accounts), 'Media assignment changed')
        plan = {'database':'zsjos','tenant':1,'services':rows,'linked':state}
    c.rollback()
    print('PLAN_SHA256', save('plan.json', plan))


def apply(c, digest):
    data = (EVIDENCE / 'plan.json').read_bytes()
    require(hashlib.sha256(data).hexdigest() == digest, 'Exact plan digest required')
    plan = decoded(data.decode('utf-8'))
    with c.cursor() as q:
        # Match application lock order: Person before its service relationships.
        read(q, 'SELECT id FROM zsjos_person WHERE tenant_id=1 AND id IN (8825,8826) ORDER BY id FOR UPDATE')
        services(q, True)
        require(linked(q) == plan['linked'], 'Related data changed; re-review required')
        count = change(q, plan['services'])
        require(linked(q) == plan['linked'], 'Related data changed in transaction')
    c.commit()
    print('COMMITTED_ROWS', count)


def verify(c):
    plan = decoded((EVIDENCE / 'plan.json').read_text(encoding='utf-8'))
    with c.cursor() as q:
        require(services(q) == after(plan['services']), 'Committed service mismatch')
        state = linked(q)
        require(state == plan['linked'], 'Committed linked data mismatch')
        teaching = read(q, 'SELECT id FROM zsjos_service_relation WHERE tenant_id=1 AND deleted=0 AND teaching_delivery=1 AND person_id IN (8825,8826)')
        require(not teaching, 'Still visible in teaching scope')
        media = read(q, "SELECT id FROM zsjos_service_relation WHERE tenant_id=1 AND deleted=0 AND status='active' AND acceptance_status='accepted' AND content_director_user_id=47 AND operator_user_id=525 AND person_id IN (8825,8826) ORDER BY id")
        require(media == [{'id':1111},{'id':1112}], 'Media responsibility changed')
        for r in plan['services']:
            q.execute('SELECT HEX(student_name_snapshot) h FROM zsjos_service_relation WHERE tenant_id=1 AND id=%s',(r['id'],))
            require(q.fetchone()['h'] == r['student_name_snapshot'].encode('utf-8').hex().upper(), 'Name bytes changed')
    c.rollback()
    evidence = {'services':[1111,1112], 'teaching_visible':0, 'media_responsible':2,
                'only_changed_column':'teaching_delivery', 'linked_records_unchanged':True,
                'linked_counts':{t:len(rows) for t,rows in state.items()}, 'name_hex_unchanged':True}
    save('verification.json',evidence)
    print('PASS committed readback, teaching exclusion, media responsibility, all related rows and name HEX')


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('command',choices=['fixture','prepare','apply','verify'])
    p.add_argument('--sha256')
    args=p.parse_args()
    marker=Path('/etc/zsjos/agent-environment')
    require(os.environ.get('ZSJOS_AGENT_ENV',marker.read_text().strip() if marker.exists() else 'local')=='test','Test environment only')
    c=connect('zsjos-mysql-1')
    try:
        with c.cursor() as q:
            q.execute('SET NAMES utf8mb4')
            q.execute('SET SESSION innodb_lock_wait_timeout=5')
        if args.command=='apply': apply(c,args.sha256)
        else: globals()[args.command](c)
    finally:
        c.rollback()
        c.close()


if __name__=='__main__': main()
