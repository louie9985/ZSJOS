#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Scoped, authorized test-tenant handover; see student-responsibility-transfer-20261015.md."""
import argparse
import datetime as dt
import hashlib
import json
import os
from pathlib import Path
import subprocess

import pymysql

TENANT = 1
MARKER = 'student-transfer-882-20261015'
REASON = '按用户确认调整学员责任编导及责任运营；保留历史提交与已完成审核'
SERVICE = 'zsjos_service_relation'
ACCOUNT = 'zsjos_media_account'
CARD = 'zsjos_positioning_card'
PLAN = 'zsjos_student_delivery_plan'
STAGE = 'zsjos_student_delivery_stage'
GROUP = 'zsjos_collaboration_group'
TASK = 'zsjos_business_task'
INTERVIEW = 'zsjos_student_positioning_interview'
EVENT = 'zsjos_business_event'
RELATION = 'zsjos_user_relation'
USERS = 'system_users'
HISTORY = ('zsjos_content_review_batch', 'zsjos_positioning_card_submission',
           'zsjos_positioning_service_card', 'zsjos_student_collaborator_assignment_log')
TABLES = (USERS, RELATION, SERVICE, ACCOUNT, GROUP, PLAN, STAGE, CARD, INTERVIEW, TASK, *HISTORY, 'zsjos_content', 'zsjos_production_ticket', EVENT)


def require(ok, message):
    if not ok:
        raise RuntimeError(message)


def encoded(value):
    def default(item):
        if isinstance(item, (dt.datetime, dt.date)):
            return {'$date': item.isoformat(), '$type': type(item).__name__}
        if isinstance(item, bytes):
            return {'$bytes': item.hex()}
        raise TypeError(type(item).__name__)
    return json.dumps(value, ensure_ascii=False, sort_keys=True, indent=2, default=default)


def decoded(text):
    def hook(item):
        if '$bytes' in item:
            return bytes.fromhex(item['$bytes'])
        if '$date' in item:
            return getattr(dt, item['$type']).fromisoformat(item['$date'])
        return item
    return json.loads(text, object_hook=hook)


def persist(path, value):
    with path.open('w', encoding='utf-8') as handle:
        handle.write(encoded(value))
        handle.flush()
        os.fsync(handle.fileno())


def rows(cur, sql, args=()):
    cur.execute(sql, args or None)
    return cur.fetchall()


def active(row):
    return row['deleted'] in (b'\x00', 0, False)


def capture(cur, lock=False):
    result = {}
    conditions = {
        USERS: 'id IN (42,44)', RELATION: "scene='content_director_operator' AND target_user_id=44",
        SERVICE: 'person_id=7082', ACCOUNT: 'student_person_id=7082', GROUP: 'student_person_id=7082',
        PLAN: 'student_person_id=7082', STAGE: 'account_id IN (101,102)', CARD: 'student_person_id=7082',
        INTERVIEW: 'student_person_id=7082', HISTORY[0]: 'student_person_id=7082',
        HISTORY[1]: 'student_person_id=7082', HISTORY[2]: 'student_person_id=7082',
        HISTORY[3]: 'service_relation_id=882',
        TASK: "(biz_type='student_service' AND biz_id=882) OR (biz_type='positioning_card' AND biz_id=98) OR (biz_type='media_account_diagnosis' AND biz_id IN (101,102)) OR (biz_type='student_delivery_stage' AND biz_id IN (96,97))",
        EVENT: "idempotency_key LIKE '" + MARKER + ":%'",
        'zsjos_content': 'account_id IN (101,102)',
        'zsjos_production_ticket': "account_id IN (101,102) OR JSON_CONTAINS(account_ids_json,'101') OR JSON_CONTAINS(account_ids_json,'102')",
    }
    for table in TABLES:
        cols = 'id,tenant_id,status,deleted' if table == USERS else '*'
        result[table] = rows(cur, f'SELECT {cols} FROM {table} WHERE tenant_id=1 AND ({conditions[table]}) ORDER BY id' + (' FOR UPDATE' if lock else ''))
    return result


def build_plan(state):
    require({x['id'] for x in state[USERS] if active(x) and x['status']==0} == {42,44}, 'Targets unavailable')
    require([x['source_user_id'] for x in state[RELATION] if active(x) and x['status']==0] == [42], 'Operator/director mapping changed')
    service = [x for x in state[SERVICE] if active(x)]
    require(len(service)==1 and service[0]['id']==882 and service[0]['status']=='active' and service[0]['acceptance_status']=='accepted', 'Service scope changed')
    require(service[0]['content_director_user_id']==31 and service[0]['operator_user_id']==59, 'Service preimage changed')
    accounts = [x for x in state[ACCOUNT] if active(x)]
    require({x['id'] for x in accounts}=={101,102}, 'Account scope changed')
    mutations=[]
    def add(table, row, fields):
        changed={key:value for key,value in fields.items() if row[key]!=value}
        if changed:
            mutations.append({'table':table,'id':row['id'],'fields':changed,'before':row,'service_id':882})
    add(SERVICE,service[0],{'content_director_user_id':42,'operator_user_id':44})
    for row in accounts:
        require(row['create_service_relation_id']==882 and row['director_user_id']==31 and row['owner_operator_user_id']==59, 'Account preimage changed')
        add(ACCOUNT,row,{'director_user_id':42,'owner_operator_user_id':44})
    for table in (GROUP,PLAN,STAGE,CARD,INTERVIEW):
        for row in state[table]:
            if not active(row): continue
            if table==GROUP and row['status']!='active': continue
            if table==PLAN and row['status']!='ACTIVE': continue
            if table==STAGE and (row['status'] not in {'WAITING','PENDING','OVERDUE','MONITORING'} or row['completed_at'] is not None): continue
            if table==INTERVIEW and row['status']!='draft': continue
            require(row['director_user_id']==31, 'Current object director changed')
            if table in (STAGE,CARD): require(not row.get('bpm_process_instance_id') and not row.get('ip_process_instance_id'),'Active BPM requires API handover')
            fields={'director_user_id':42}
            if table in (GROUP,CARD):
                require(row['operator_user_id'] in (59,44),'Unexpected object operator')
                fields['operator_user_id']=44
            add(table,row,fields)
    cards=[x for x in state[CARD] if active(x)]
    require(len(cards)==1 and cards[0]['id']==98 and cards[0]['status']=='student_confirm','Positioning scope changed')
    submissions=[x for x in state[HISTORY[1]] if active(x) and x['card_id']==98]
    latest=max(submissions,key=lambda x:x['submission_no'])
    require(latest['id']==135 and latest['status']=='student_confirm' and latest['operator_user_id']==59,'Pending submission changed')
    add(HISTORY[1],latest,{'operator_user_id':44})
    for row in state[TASK]:
        if not active(row) or row['status']!='pending':continue
        require(row['assignee_type']=='user' and row['assignee_id'] in (31,59),'Unknown pending task owner')
        require(row['biz_type'] in ('media_account_diagnosis','student_delivery_stage'),'Unknown pending task semantics')
        add(TASK,row,{'assignee_id':42})
    batches={}
    for row in state[HISTORY[0]]:
        if active(row):
            require(row['status']=='COMPLETED','Non-completed content review requires separate review')
            batches[row['status']]=batches.get(row['status'],0)+1
    require(not any(active(x) for x in state['zsjos_production_ticket']),'Production tickets require ownership review')
    require(all(not active(x) or x['owner_operator_user_id']==44 for x in state['zsjos_content']),'Content owner conflict')
    require(not state[EVENT],'Existing receipt: use original plan')
    summary={}
    for change in mutations: summary[change['table']]=summary.get(change['table'],0)+1
    return {'mutations':mutations,'services':{882:42},'accounts':{101:882,102:882},'summary':summary,'preservedBatches':batches}


def expected_row(change,when):
    expected=dict(change['before'])
    expected.update(change['fields'])
    expected.update(version=expected['version']+1,updater=MARKER,update_time=when)
    return expected


def apply(cur,plan,fail_after=None,limit=None):
    count=0
    for change in plan['mutations']:
        table,rid=change['table'],change['id']; key=f'{MARKER}:{table}:{rid}'
        current=rows(cur,f'SELECT * FROM {table} WHERE tenant_id=1 AND id=%s FOR UPDATE',(rid,))[0]
        receipt=rows(cur,f'SELECT * FROM {EVENT} WHERE tenant_id=1 AND idempotency_key=%s',(key,))
        if receipt:
            require(current==expected_row(change,receipt[0]['occurred_at']),'Previously repaired row has subsequent edits; refusing replay')
            continue
        require(current==change['before'],'Stale preimage; no overwrite allowed')
        now=rows(cur,'SELECT NOW() now')[0]['now']; fields=change['fields']
        setters=','.join(f'{field}=%s' for field in fields)
        cur.execute(f'UPDATE {table} SET {setters},version=version+1,updater=%s,update_time=%s WHERE tenant_id=1 AND id=%s AND version=%s AND deleted=0',(*fields.values(),MARKER,now,rid,current['version']))
        require(cur.rowcount==1,'Optimistic conflict')
        refs=json.dumps({'table':table,'fields':{k:{'old':current[k],'new':v} for k,v in fields.items()},'serviceRelationId':882})
        cur.execute(f'INSERT INTO {EVENT} (event_type,aggregate_type,aggregate_id,operator_user_id,reason,related_object_refs,occurred_at,idempotency_key,creator,updater,tenant_id) VALUES (%s,%s,%s,NULL,%s,%s,%s,%s,%s,%s,1)',('responsibility_handover',table.removeprefix('zsjos_'),rid,REASON,refs,now,key,MARKER,MARKER))
        count+=1
        if fail_after==count:raise RuntimeError('injected_failure')
        if limit==count:break
    return count


def verify(before,after,plan):
    changes={(x['table'],x['id']):x for x in plan['mutations']}
    for table in TABLES:
        if table==EVENT:continue
        require(len(before[table])==len(after[table]),'Row count changed')
        for old,new in zip(before[table],after[table]):
            change=changes.get((table,old['id']))
            require(new==(expected_row(change,new['update_time']) if change else old),'Unexpected field/history mutation')
    require(len(after[EVENT])==len(plan['mutations']),'Incomplete audit')
    require(all(x['reason']==REASON and x['operator_user_id'] is None for x in after[EVENT]),'Invalid system audit')


def restore(cur,plan):
    require(apply(cur,plan)==0,'Restore requires unchanged applied state')
    for change in reversed(plan['mutations']):
        table,rid=change['table'],change['id']; fields=change['fields']; now=rows(cur,'SELECT NOW() now')[0]['now']
        setters=','.join(f'{k}=%s' for k in fields)
        cur.execute(f'UPDATE {table} SET {setters},version=version+1,updater=%s,update_time=%s WHERE tenant_id=1 AND id=%s',(*(change['before'][k] for k in fields),MARKER+'-rollback',now,rid))
        require(cur.rowcount==1,'Missing restore row')
        cur.execute(f'INSERT INTO {EVENT} (event_type,aggregate_type,aggregate_id,operator_user_id,reason,related_object_refs,occurred_at,idempotency_key,creator,updater,tenant_id) VALUES (%s,%s,%s,NULL,%s,%s,%s,%s,%s,%s,1)',('responsibility_handover_rollback',table.removeprefix('zsjos_'),rid,'受控回退学员责任交接，保留原交接审计',json.dumps({'restoredFields':list(fields)}),now,f'{MARKER}:rollback:{table}:{rid}',MARKER,MARKER))

def rehearse(conn, cur, before, plan):
    for table in TABLES:
        ddl = rows(cur, f'SHOW CREATE TABLE {table}')[0]['Create Table']
        # System users are only dependency inputs; temporary copy needs no sensitive columns.
        if table == USERS:
            cur.execute('CREATE TEMPORARY TABLE system_users (id BIGINT PRIMARY KEY,tenant_id BIGINT,status INT,deleted BIT) ENGINE=InnoDB')
        else:
            cur.execute(ddl.replace('CREATE TABLE', 'CREATE TEMPORARY TABLE', 1))
        columns = [r['Field'] for r in rows(cur, f'SHOW COLUMNS FROM {table}')
                   if 'GENERATED' not in r['Extra'].replace('DEFAULT_GENERATED', '')]
        if before[table]:
            cur.executemany(f'INSERT INTO {table} ({",".join(columns)}) VALUES ({",".join(["%s"] * len(columns))})',
                            [[row[c] for c in columns] for row in before[table]])
    conn.commit()
    require(capture(cur) == before, 'Temporary fixture differs from live prerequisite')
    try:
        apply(cur, plan, fail_after=3)
        raise AssertionError('Expected injected error')
    except RuntimeError as exc:
        require(str(exc) == 'injected_failure', 'Unexpected test failure')
    conn.rollback()
    require(capture(cur) == before, 'Failure failed to roll back')
    require(apply(cur, plan) == len(plan['mutations']), 'Initial application failed')
    after = capture(cur)
    verify(before, after, plan)
    require(apply(cur, plan) == 0 and capture(cur) == after, 'Repeat mutated fixture')
    conn.rollback()
    require(capture(cur) == before, 'Success rollback mismatch')
    # A committed partial application is recoverable only if each receipt matches its row.
    apply(cur, plan, limit=3)
    conn.commit()
    require(apply(cur, plan) == len(plan['mutations']) - 3, 'Partial recovery failed')
    verify(before, capture(cur), plan)
    conn.commit()
    first = plan['mutations'][0]
    cur.execute(f"UPDATE {first['table']} SET version=version+1 WHERE id=%s", (first['id'],))
    try:
        apply(cur, plan)
        raise AssertionError('Later edit accepted')
    except RuntimeError as exc:
        require('subsequent edits' in str(exc), 'Unexpected stale failure')
    conn.rollback()
    restore(cur, plan)
    restored = capture(cur)
    for change in plan['mutations']:
        actual = next(x for x in restored[change['table']] if x['id'] == change['id'])
        expected = dict(change['before'])
        expected.update({'version': expected.get('version',0) + 2, 'updater': MARKER + '-rollback',
                         'update_time': actual['update_time']})
        if 'version' not in change['before']: expected.pop('version', None)
        require(actual == expected, 'Compensating rollback failed')
    conn.rollback()
    for table in reversed(TABLES):
        cur.execute(f'DROP TEMPORARY TABLE {table}')
    conn.commit()


def connect(container):
    meta = json.loads(subprocess.check_output(['docker', 'inspect', container]))[0]
    cfg = dict(x.split('=', 1) for x in meta['Config']['Env'] if '=' in x)
    password = subprocess.check_output(['docker', 'exec', container, 'cat', cfg['MYSQL_ROOT_PASSWORD_FILE']]).decode().strip()
    port = meta['NetworkSettings']['Ports']['3306/tcp'][0]
    require(port['HostIp'] == '127.0.0.1' and cfg['MYSQL_DATABASE'] == 'zsjos', 'Unexpected database target')
    return pymysql.connect(host=port['HostIp'], port=int(port['HostPort']), user='root', password=password,
                           database=cfg['MYSQL_DATABASE'], charset='utf8mb4', autocommit=False,
                           cursorclass=pymysql.cursors.DictCursor)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--container', default='zsjos-mysql-1')
    parser.add_argument('--evidence-dir', type=Path, required=True)
    parser.add_argument('--apply', action='store_true')
    parser.add_argument('--restore-from', type=Path, help='Separately authorized compensating rollback evidence')
    args = parser.parse_args()
    marker = Path('/etc/zsjos/agent-environment')
    environment = os.getenv('ZSJOS_AGENT_ENV')
    if environment is None:
        environment = marker.read_text().strip() if marker.exists() else 'local'
    require(environment == 'test', 'Only confirmed test environment is supported')
    os.umask(0o077)
    args.evidence_dir.mkdir(parents=True, mode=0o700, exist_ok=False)
    conn = connect(args.container)
    try:
        with conn.cursor() as cur:
            cur.execute('SET NAMES utf8mb4')
            cur.execute("SET time_zone='+08:00'")
            cur.execute('SET SESSION innodb_lock_wait_timeout=10')
            if args.restore_from:
                require(args.apply, 'Rollback is explicit; --apply required')
                plan = decoded((args.restore_from / 'plan.json').read_text(encoding='utf-8'))
                persist(args.evidence_dir / 'before.json', capture(cur, lock=True))
                restore(cur, plan)
                persist(args.evidence_dir / 'after.json', capture(cur))
                conn.commit()
                persist(args.evidence_dir / 'result.json', {'mode': 'rollback-committed', 'rows': len(plan['mutations'])})
                print('Compensating rollback committed')
                return
            before = capture(cur)
            plan = build_plan(before)
            conn.rollback()
            persist(args.evidence_dir / 'before.json', before)
            persist(args.evidence_dir / 'plan.json', plan)
            print(encoded({'phase': 'preflight', 'changes': plan['summary'], 'preservedBatches': plan['preservedBatches']}), flush=True)
            rehearse(conn, cur, before, plan)
            print('MySQL rehearsal passed: initial, repeat, failure rollback, partial recovery, stale refusal', flush=True)
            if not args.apply:
                persist(args.evidence_dir / 'result.json', {'mode': 'verified-only', 'changes': plan['summary']})
                return
            require(capture(cur, lock=True) == before, 'Live state changed during rehearsal; regenerate plan')
            require(build_plan(before) == plan, 'Plan changed')
            changed = apply(cur, plan)
            after = capture(cur)
            verify(before, after, plan)
            for value in rows(cur, f'SELECT reason,HEX(reason) hex_value FROM {EVENT} WHERE tenant_id=1 AND idempotency_key LIKE %s', (MARKER + ':%',)):
                require(bytes.fromhex(value['hex_value']).decode('utf-8') == value['reason'], 'Stored UTF-8 byte mismatch')
            persist(args.evidence_dir / 'after.json', after)
            conn.commit()
            # Read committed rows by exact mutation key; other business activity may resume.
            require(apply(cur, plan) == 0, 'Committed repeat not a no-op')
            conn.rollback()
            report = {'mode': 'committed', 'changedRecords': changed, 'auditEvents': changed,
                      'changes': plan['summary'], 'preservedBatches': plan['preservedBatches'],
                      'mysqlRehearsalPassed': True, 'fieldAndHistoryPreserved': True, 'utf8Verified': True,
                      'committedReadbackAndRepeatPassed': True,
                      'backupSha256': hashlib.sha256((args.evidence_dir / 'before.json').read_bytes()).hexdigest()}
            persist(args.evidence_dir / 'result.json', report)
            print(encoded(report))
    finally:
        conn.rollback()
        conn.close()


if __name__ == '__main__':
    main()
