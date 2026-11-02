#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Scoped, authorized test-tenant handover; see operator-transfer-20261101.md."""
import argparse
import datetime as dt
import hashlib
import json
import os
from pathlib import Path
import subprocess

import pymysql

TENANT = 1
MARKER = 'operator-transfer-20261101'
REASON = '按用户确认仅调整媒体服务及关联账号协作组运营；保留编导和其他业务历史'
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
# Exact reviewed service -> (person, previous operator); names never select write targets.
TARGETS = {
    12:(6004,50),14:(6006,50),15:(6007,50),17:(6009,50),19:(6011,50),
    21:(6013,50),22:(6014,50),24:(6016,50),25:(6017,50),26:(6018,50),
    27:(6019,45),29:(6021,45),35:(6027,45),36:(6028,45),40:(6032,50),
    42:(6034,50),44:(6036,50),837:(4845,49),838:(7038,45),859:(7059,49),
    862:(7062,44),873:(7073,49),876:(7076,45),877:(7077,44),878:(7078,49),
    880:(7080,49),882:(7082,44),883:(7083,45),884:(7084,44),885:(7085,44),
    886:(7086,45),897:(4256,44),1000:(8007,45),1001:(7950,44),
    1002:(8028,49),1003:(5009,45),
}
PROTECTED_SAME_NAME_IDS = {961,9998,8271,9943,10047,1460,8212,9939,8737,9954,9921,10066,7625,9907}
FIELDS = {SERVICE: 'operator_user_id', ACCOUNT: 'owner_operator_user_id', GROUP: 'operator_user_id'}
TABLES = ('zsjos_person', USERS, RELATION, SERVICE, ACCOUNT, GROUP, PLAN, STAGE,
          CARD, INTERVIEW, TASK, *HISTORY, 'zsjos_content', 'zsjos_content_version',
          'zsjos_content_review_batch_item', 'zsjos_production_ticket', EVENT)


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


def id_list(values):
    return ','.join(str(int(v)) for v in sorted(set(values))) or 'NULL'


def capture(cur, lock=False):
    result = {}
    people = id_list({p for p, _ in TARGETS.values()} | PROTECTED_SAME_NAME_IDS)
    suffix = ' FOR UPDATE' if lock else ''
    if lock:
        rows(cur, f'SELECT id FROM zsjos_person WHERE tenant_id=1 AND id IN ({people}) ORDER BY id FOR UPDATE')

    def scoped_ids(table, condition):
        # Materialize IDs to avoid MySQL reopening a temporary table in fixture subqueries.
        return id_list(x['id'] for x in rows(cur, f'SELECT id FROM {table} WHERE tenant_id=1 AND ({condition}) ORDER BY id' + suffix))

    services = scoped_ids(SERVICE, f'person_id IN ({people})')
    accounts = scoped_ids(ACCOUNT, f'student_person_id IN ({people})')
    cards = scoped_ids(CARD, f'student_person_id IN ({people})')
    stages = scoped_ids(STAGE, f'account_id IN ({accounts})')
    contents = scoped_ids('zsjos_content', f'account_id IN ({accounts})')
    batches = scoped_ids(HISTORY[0], f'student_person_id IN ({people})')
    conditions = {
        'zsjos_person': f'id IN ({people})', USERS: 'id IN (42,45)',
        RELATION: "scene='content_director_operator' AND target_user_id=45",
        SERVICE: f'person_id IN ({people})', ACCOUNT: f'student_person_id IN ({people})',
        GROUP: f'student_person_id IN ({people})', PLAN: f'student_person_id IN ({people})',
        STAGE: f'account_id IN ({accounts})', CARD: f'student_person_id IN ({people})',
        INTERVIEW: f'student_person_id IN ({people})', HISTORY[0]: f'student_person_id IN ({people})',
        HISTORY[1]: f'student_person_id IN ({people})', HISTORY[2]: f'student_person_id IN ({people})',
        HISTORY[3]: f'service_relation_id IN ({services})',
        TASK: f"(biz_type='student_service' AND biz_id IN ({services})) OR (biz_type='positioning_card' AND biz_id IN ({cards})) OR (biz_type='media_account_diagnosis' AND biz_id IN ({accounts})) OR (biz_type='student_delivery_stage' AND biz_id IN ({stages}))",
        'zsjos_content': f'account_id IN ({accounts})',
        'zsjos_content_version': f'content_id IN ({contents})',
        'zsjos_content_review_batch_item': f'batch_id IN ({batches})',
        'zsjos_production_ticket': f'account_id IN ({accounts})',
        EVENT: "idempotency_key LIKE '" + MARKER + ":%'",
    }
    for table in TABLES:
        cols = 'id,tenant_id,status,deleted' if table == USERS else '*'
        result[table] = rows(cur, f'SELECT {cols} FROM {table} WHERE tenant_id=1 AND ({conditions[table]}) ORDER BY id'
                             + (' FOR UPDATE' if lock else ''))
    target_ids = id_list(TARGETS)
    shared = rows(cur, f'SELECT id FROM {SERVICE} WHERE tenant_id=1 AND deleted=0 AND id NOT IN ({target_ids}) AND collaboration_group_id IN ({target_ids})'
                  + (' FOR UPDATE' if lock else ''))
    require(not shared, 'Collaboration group shared outside approved scope')
    return result


def build_plan(state):
    require({x['id'] for x in state[USERS] if active(x) and x['status'] == 0} == {42,45}, 'Targets unavailable')
    require([x['source_user_id'] for x in state[RELATION] if active(x) and x['status'] == 0] == [42], 'Personnel mapping changed')
    require({x['id'] for x in state['zsjos_person'] if active(x)} ==
            {p for p, _ in TARGETS.values()} | PROTECTED_SAME_NAME_IDS, 'Person scope changed')
    services = {x['id']:x for x in state[SERVICE] if x['id'] in TARGETS and active(x)}
    require(set(services) == set(TARGETS), 'Service scope changed')
    mutations = []

    def add(table, row, service_id):
        field = FIELDS[table]
        if row[field] != 45:
            mutations.append({'table':table,'id':row['id'],'fields':{field:45},
                              'before':row,'service_id':service_id})

    for sid, (pid, old_operator) in TARGETS.items():
        row = services[sid]
        require(row['person_id'] == pid and row['operator_user_id'] == old_operator
                and row['collaboration_group_id'] == sid and row['content_director_user_id'] == 42
                and row['status'] == 'active' and row['acceptance_status'] == 'accepted', 'Service prerequisite changed')
        add(SERVICE, row, sid)
    accounts = [x for x in state[ACCOUNT] if active(x) and x['create_service_relation_id'] in TARGETS]
    require(len(accounts) == 71, 'Account count changed')
    for sid in TARGETS:
        require(sum(x['create_service_relation_id'] == sid for x in accounts) == (1 if sid == 877 else 2), 'Account scope changed')
    for row in accounts:
        sid = row['create_service_relation_id']
        require(row['student_person_id'] == TARGETS[sid][0] and row['director_user_id'] == 42
                and row['owner_operator_user_id'] == TARGETS[sid][1], 'Account prerequisite changed')
        add(ACCOUNT, row, sid)
    groups = [x for x in state[GROUP] if active(x) and x['id'] in TARGETS]
    require(len(groups) == 36, 'Group scope changed')
    for row in groups:
        sid = row['id']
        require(row['student_person_id'] == TARGETS[sid][0] and row['director_user_id'] == 42
                and row['status'] == 'active', 'Group prerequisite changed')
        add(GROUP, row, sid)
    require(not state[EVENT], 'Existing receipts: use original plan for replay')
    summary = {table:sum(x['table'] == table for x in mutations) for table in FIELDS}
    require(summary == {SERVICE:26, ACCOUNT:51, GROUP:31}, 'Reviewed update count changed')
    batches = {}
    for row in state[HISTORY[0]]:
        if active(row):
            batches[row['status']] = batches.get(row['status'], 0) + 1
    return {'mutations':mutations,'summary':summary,'preservedBatches':batches}


def expected_row(change,when):
    expected=dict(change['before'])
    expected.update(change['fields'])
    expected.update(version=expected['version']+1,updater=MARKER,update_time=when)
    return expected


def apply(cur,plan,fail_after=None,limit=None):
    count=0
    for change in plan['mutations']:
        table,rid=change['table'],change['id']
        require(table in FIELDS and change['fields'] == {FIELDS[table]:45}
                and change['service_id'] in TARGETS, 'Mutation outside operator-only whitelist')
        key=f'{MARKER}:{table}:{rid}'
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
        refs=json.dumps({'table':table,'fields':{k:{'old':current[k],'new':v} for k,v in fields.items()},'serviceRelationId':change['service_id']})
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
