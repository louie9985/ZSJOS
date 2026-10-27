#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Exact authorized test-only course correction and class assignment for 95 seeded services.

No order, account, task, schema, permission, notification or application changes.
See docs/operations/seeded-light-course-class-20261026.md.
"""
import argparse
import copy
import hashlib
import json
import os
from pathlib import Path

from transfer_student_20261015 import connect, require
from exit_teaching_delivery_batch2_20261022 import encoded, decoded
from consolidate_student_services_20261026 import read, ids, metadata, runtime, course_snapshot

ROOT = Path('/opt/zsjos-runtime/backups/seeded-light-course-class-20261026-test')
MARKER = 'seeded-light-class-20261026'
REASON = '按用户确认，直接生成且有编导运营归属的学员统一补正轻创课程并安排现有轻创班；保留原接收状态和媒体历史'
SERVICE = 'zsjos_service_relation'
EVENT = 'zsjos_business_event'
TARGETS = tuple(range(11, 47)) + (837, 838, 839) + tuple(range(841, 891)) + (892, 893, 896, 897, 1001, 1003)
SEEDS = ('seed-liudandan', 'seed-students', 'seed-llyilin', 'seed-batch4', 'seed-student')


def save(name, value):
    ROOT.mkdir(mode=0o700, parents=True, exist_ok=True)
    require(ROOT.stat().st_mode & 0o077 == 0, 'Evidence directory must be private')
    data = encoded(value).encode('utf-8')
    fd = os.open(ROOT/name, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(fd, 'wb') as f:
        f.write(data); f.flush(); os.fsync(f.fileno())
    return hashlib.sha256(data).hexdigest()


def discover(q, people):
    meta = metadata(q)
    service_rows = read(q, f'SELECT id,collaboration_group_id FROM {SERVICE} WHERE tenant_id=1 AND person_id IN ({ids(people)})')
    service_ids = [r['id'] for r in service_rows]
    accounts = read(q, f'SELECT id FROM zsjos_media_account WHERE tenant_id=1 AND student_person_id IN ({ids(people)})')
    account_ids = [r['id'] for r in accounts]
    orders = read(q, f'SELECT id FROM zsjos_order WHERE tenant_id=1 AND person_id IN ({ids(people)})')
    order_ids = [r['id'] for r in orders]
    items = read(q, f'SELECT id FROM zsjos_order_item WHERE tenant_id=1 AND order_id IN ({ids(order_ids)})')
    cases = read(q, f'SELECT id FROM zsjos_registration_case WHERE tenant_id=1 AND order_id IN ({ids(order_ids)})')
    keys = {'person_id':people, 'student_person_id':people,
        'service_relation_id':service_ids, 'create_service_relation_id':service_ids,
        'source_service_relation_id':service_ids,
        'collaboration_group_id':[r['collaboration_group_id'] for r in service_rows if r['collaboration_group_id'] is not None],
        'account_id':account_ids,'order_id':order_ids,'order_item_id':[r['id'] for r in items],
        'registration_case_id':[r['id'] for r in cases]}
    for table,key in [('zsjos_content','content_id'),('zsjos_production_ticket','ticket_id'),
                      ('zsjos_positioning_card','card_id'),('zsjos_student_delivery_plan','plan_id'),
                      ('zsjos_student_delivery_stage','stage_id'),('zsjos_content_review_batch','batch_id')]:
        cols=meta[table]
        conditions=[f'`{k}` IN ({ids(v)})' for k,v in keys.items() if k in cols]
        if 'account_ids_json' in cols:
            conditions.extend(f"JSON_CONTAINS(account_ids_json,'{a}')" for a in account_ids)
        rows=read(q,f"SELECT id FROM `{table}` WHERE tenant_id=1 AND ({' OR '.join(conditions)})") if conditions else []
        keys[key]=[r['id'] for r in rows]
    queries={}
    for table,cols in sorted(meta.items()):
        if 'tenant_id' not in cols or 'id' not in cols or '_bak_' in table or table.startswith('system_'):
            continue
        conditions=[f'`{k}` IN ({ids(v)})' for k,v in keys.items() if k in cols]
        if 'account_ids_json' in cols:
            conditions.extend(f"JSON_CONTAINS(account_ids_json,'{a}')" for a in account_ids)
        specific={'zsjos_person':people,'zsjos_order':order_ids,
            'zsjos_collaboration_group':keys['collaboration_group_id'],
            'zsjos_delivery_class':[1,51], 'zsjos_product':[42], 'zsjos_product_category':[13]}
        if table in specific:
            conditions.append(f'id IN ({ids(specific[table])})')
        if table=='zsjos_business_task':
            conditions.extend([f"(biz_type='student_service' AND biz_id IN ({ids(service_ids)}))",
                f"(biz_type='media_account_diagnosis' AND biz_id IN ({ids(account_ids)}))",
                f"(biz_type='student_delivery_stage' AND biz_id IN ({ids(keys['stage_id'])}))",
                "JSON_UNQUOTE(JSON_EXTRACT(payload,'$.serviceRelationId')) IN ("+','.join(repr(str(x)) for x in service_ids)+')'])
        if table==EVENT:
            conditions.append(f"(aggregate_type IN ('student_service','student-service','service_relation') AND aggregate_id IN ({ids(service_ids)}))")
        if conditions:
            queries[table]=f"SELECT * FROM `{table}` WHERE tenant_id=1 AND ({' OR '.join(conditions)}) ORDER BY id"
    queries['system_users']='SELECT id,status,deleted,dept_id FROM system_users WHERE tenant_id=1 AND id=400 ORDER BY id'
    queries['system_role']="SELECT id,code,status,deleted FROM system_role WHERE tenant_id=1 AND code='study_planner' ORDER BY id"
    queries['system_user_role']='SELECT * FROM system_user_role WHERE tenant_id=1 AND user_id=400 ORDER BY id'
    return queries


def capture(q,queries,lock=False):
    state={t:read(q,sql+(' FOR UPDATE' if lock else '')) for t,sql in sorted(queries.items())}
    state[EVENT]=[r for r in state.get(EVENT,[]) if not r['idempotency_key'].startswith(MARKER+':')]
    return state


def receipts(q):
    return read(q,f'SELECT * FROM {EVENT} WHERE tenant_id=1 AND idempotency_key LIKE %s ORDER BY id',(MARKER+':%',))


def build(q,before):
    relations={r['id']:r for r in before[SERVICE]}
    target_class=next(r for r in before['zsjos_delivery_class'] if r['id']==51)
    require((target_class['class_kind'],target_class['status'],target_class['homeroom_user_id'],target_class['deleted'])==('NORMAL','SERVING',400,b'\0'),'Class changed')
    require(len(before['system_users'])==1 and before['system_users'][0]['status']==0 and before['system_users'][0]['deleted']==b'\0','Class owner unavailable')
    eligible={r['id'] for r in before['system_role'] if r['status']==0 and r['deleted']==b'\0'}
    require(any(r['role_id'] in eligible and r['deleted']==b'\0' for r in before['system_user_role']),'Class owner qualification changed')
    require(len(TARGETS)==95 and len(set(TARGETS))==95,'Allowlist error')
    snapshot=course_snapshot(q,before)
    mutations=[];linked=0
    for rid in TARGETS:
        row=relations[rid]
        require(row['creator'] in SEEDS and row['status']=='active' and row['deleted']==b'\0'
            and row['teaching_delivery']==b'\1' and row['teaching_closed']==b'\0'
            and row['class_id']==1 and row['owner_user_id'] is None and row['acceptance_status']=='accepted'
            and row['content_director_user_id'] is not None and row['operator_user_id'] is not None,'Target contract changed')
        old_course=json.loads(row['service_snapshot'])['name']
        require(old_course==('大健康轻创网红博主' if rid in (1001,1003) else '注册营养师'),'Course source changed')
        if row['order_id'] is not None:
            order=next(r for r in before['zsjos_order'] if r['id']==row['order_id'])
            require(order['creator'] in ('seed-liudandan','seed-students') and order['deleted']==b'\0','Unexpected real order association')
            item=next(r for r in before['zsjos_order_item'] if r['id']==row['order_item_id'])
            require(item['order_id']==order['id'] and item['deleted']==b'\0','Order item mismatch')
            linked+=1
        else:
            require(row['order_item_id'] is None and row['registration_case_id'] is None,'Partial empty order chain')
        fields={'class_id':51,'owner_user_id':400,'media_ready':b'\1',
                'order_id':None,'order_item_id':None,'registration_case_id':None}
        if rid not in (1001,1003):
            fields['service_snapshot']=snapshot
        fields={k:v for k,v in fields.items() if row[k]!=v}
        mutations.append({'id':rid,'before':row,'fields':fields})
    require(linked==39,'Seed-order association count changed')
    require(not any(r['service_relation_id'] in TARGETS and r['deleted']==b'\0' for r in before['zsjos_student_contact_record']),'Teaching history requires review')
    require(not any(r['biz_type']=='student_service' and r['biz_id'] in TARGETS and r['deleted']==b'\0' for r in before['zsjos_business_task']),'Teaching task requires review')
    require(not any(r['service_relation_id'] in TARGETS and r['deleted']==b'\0' for r in before['zsjos_class_transfer_request']),'Existing transfer requires review')
    return mutations


def expected_row(change,when):
    return dict(change['before'],**change['fields'],version=change['before']['version']+1,updater=MARKER,update_time=when)


def expected_state(plan,when):
    result=copy.deepcopy(plan['before'])
    changed={m['id']:expected_row(m,when) for m in plan['mutations']}
    result[SERVICE]=[changed.get(r['id'],r) for r in result[SERVICE]]
    return result


def guard(q,plan,digest,lock=False):
    if lock:
        read(q,f"SELECT id FROM zsjos_person WHERE tenant_id=1 AND id IN ({ids(plan['people'])}) ORDER BY id FOR UPDATE")
        read(q,f'SELECT id FROM {SERVICE} WHERE tenant_id=1 AND id IN ({ids(TARGETS)}) ORDER BY id FOR UPDATE')
    current=capture(q,plan['queries'],lock)
    audit=receipts(q)
    if audit:
        require(len(audit)==95 and {r['idempotency_key'] for r in audit}=={f'{MARKER}:{rid}' for rid in TARGETS},'Partial audit refuses replay')
        require(len({r['occurred_at'] for r in audit})==1,'Audit timestamp mismatch')
        require(all(r['reason']==REASON and r['operator_user_id'] is None and json.loads(r['related_object_refs'])['planSha256']==digest for r in audit),'Audit provenance mismatch')
        when=audit[0]['occurred_at']
        require(current==expected_state(plan,when),'Applied state drift; refusing overwrite')
        return when
    require(current==plan['before'],'Preimage/dependency drift; re-review required')
    return None


def change(q,plan,digest,when,fail_after=None):
    for index,m in enumerate(plan['mutations'],1):
        current=read(q,f'SELECT * FROM {SERVICE} WHERE tenant_id=1 AND id=%s FOR UPDATE',(m['id'],))
        require(current==[m['before']],'Service drift')
        after=expected_row(m,when);fields={k:v for k,v in after.items() if current[0][k]!=v}
        q.execute(f'UPDATE {SERVICE} SET '+','.join(f'`{k}`=%s' for k in fields)+' WHERE tenant_id=1 AND id=%s AND version=%s',(*fields.values(),m['id'],m['before']['version']))
        require(q.rowcount==1,'Unexpected update count')
        refs={'serviceRelationId':m['id'],'oldClassId':1,'newClassId':51,'newOwnerUserId':400,
              'oldOrderId':m['before']['order_id'],'oldOrderItemId':m['before']['order_item_id'],
              'oldRegistrationCaseId':m['before']['registration_case_id'],'changedFields':list(fields),'planSha256':digest}
        q.execute(f'INSERT INTO {EVENT} (event_type,aggregate_type,aggregate_id,operator_user_id,reason,related_object_refs,occurred_at,idempotency_key,creator,updater,create_time,update_time,tenant_id) VALUES (%s,%s,%s,NULL,%s,%s,%s,%s,%s,%s,%s,%s,1)',
            ('seeded_light_course_assignment','student-service',m['id'],REASON,json.dumps(refs,ensure_ascii=False),when,f"{MARKER}:{m['id']}",MARKER,MARKER,when,when))
        if fail_after==index:raise RuntimeError('injected rollback')


def prepare(c):
    process=runtime()
    with c.cursor() as q:
        q.execute('SET TRANSACTION READ ONLY');q.execute('START TRANSACTION WITH CONSISTENT SNAPSHOT')
        require(not receipts(q),'Repair already exists')
        rows=read(q,f'SELECT id,person_id FROM {SERVICE} WHERE tenant_id=1 AND id IN ({ids(TARGETS)}) ORDER BY id')
        require(len(rows)==95,'Target missing')
        people=sorted({r['person_id'] for r in rows});require(len(people)==95,'Unexpected repeated identity')
        queries=discover(q,people);before=capture(q,queries);mutations=build(q,before)
        require(not read(q,"SELECT TRIGGER_NAME FROM information_schema.triggers WHERE trigger_schema=DATABASE() AND event_object_table='zsjos_service_relation'"),'Unexpected update triggers')
        plan={'database':'zsjos','tenant':1,'runtime':process,'people':people,'queries':queries,
              'before':before,'mutations':mutations,'created_at':read(q,'SELECT NOW() now')[0]['now']}
    c.rollback()
    digest=save('plan.json',plan)
    print('PLAN_SHA256',digest,'MUTATIONS',len(mutations),'TABLES',len(before),'ROWS',sum(map(len,before.values())))


def fixture(c,plan,digest):
    with c.cursor() as q:
        require(guard(q,plan,digest) is None,'Requires original state')
        for table in (SERVICE,EVENT):
            ddl=read(q,f'SHOW CREATE TABLE {table}')[0]['Create Table']
            q.execute(ddl.replace('CREATE TABLE ','CREATE TEMPORARY TABLE ',1))
            rows=plan['before'].get(table,[])
            if rows:
                cols=list(rows[0]);q.executemany(f'INSERT INTO {table} ('+','.join('`'+x+'`' for x in cols)+') VALUES ('+','.join(['%s']*len(cols))+')',[tuple(r[k] for k in cols) for r in rows])
    c.commit()
    for cutoff in (1,39,50,95):
        try:
            with c.cursor() as q:change(q,plan,digest,read(q,'SELECT NOW() now')[0]['now'],cutoff)
        except RuntimeError as e:
            require(str(e)=='injected rollback','Unexpected fixture error');c.rollback()
        else:raise RuntimeError('Failure was not injected')
        with c.cursor() as q:require(guard(q,plan,digest) is None,'Rollback failed')
        c.rollback()
    with c.cursor() as q:
        when=read(q,'SELECT NOW() now')[0]['now'];change(q,plan,digest,when)
        require(guard(q,plan,digest)==when,'Initial/recovery failed')
    c.commit()
    with c.cursor() as q:
        require(guard(q,plan,digest)==when,'Replay failed')
        q.execute(f'DELETE FROM {EVENT} WHERE tenant_id=1 AND idempotency_key=%s',(f'{MARKER}:11',))
        try:guard(q,plan,digest)
        except RuntimeError as e:require('Partial audit' in str(e),'Wrong partial-state refusal')
        else:raise RuntimeError('Partial state accepted')
    c.rollback()
    with c.cursor() as q:
        for m in reversed(plan['mutations']):
            old=m['before'];after=expected_row(m,when);fields={k:v for k,v in old.items() if after[k]!=v}
            q.execute(f'UPDATE {SERVICE} SET '+','.join(f'`{k}`=%s' for k in fields)+' WHERE tenant_id=1 AND id=%s',(*fields.values(),m['id']))
        q.execute(f'DELETE FROM {EVENT} WHERE tenant_id=1 AND idempotency_key LIKE %s',(MARKER+':%',))
        require(guard(q,plan,digest) is None,'Restoration failed')
    c.rollback()
    save('fixture.json',{'plan_sha256':digest,'rollback_points':[1,39,50,95],'initial_recovery_replay_partial_restore':True,'temporary_tables_only':True})
    print('PASS temporary MySQL initial/rollback/recovery/replay/partial-refusal/restore')


def apply(c,plan,digest,preview):
    require(runtime()==plan['runtime'],'Backend changed')
    require((ROOT/'fixture.json').exists(),'Fixture required')
    with c.cursor() as q:
        require(read(q,'SELECT GET_LOCK(%s,0) ok',(MARKER,))[0]['ok']==1,'Repair already running')
        if guard(q,plan,digest,True) is not None:
            c.rollback();print('REPLAY_UPDATES 0');return
        when=read(q,'SELECT NOW() now')[0]['now'];change(q,plan,digest,when)
        require(guard(q,plan,digest)==when,'Postcondition failed')
        require(runtime()==plan['runtime'],'Backend changed during transaction')
    if preview:
        c.rollback();save('preview.json',{'plan_sha256':digest,'when':when,'rolled_back':True,'rows':95})
        print('PASS real-target transaction preview; rolled back')
    else:
        require((ROOT/'preview.json').exists(),'Target preview required')
        c.commit();save('receipt.json',{'plan_sha256':digest,'when':when,'rows':95,'audit_events':95})
        print('COMMITTED_ROWS 95 AUDIT_EVENTS 95')


def verify(c,plan,digest):
    require(runtime()==plan['runtime'],'Backend restarted/changed')
    with c.cursor() as q:
        q.execute('SET TRANSACTION READ ONLY');q.execute('START TRANSACTION WITH CONSISTENT SNAPSHOT')
        when=guard(q,plan,digest);require(when is not None,'Not applied')
        rows=read(q,f'SELECT * FROM {SERVICE} WHERE tenant_id=1 AND id IN ({ids(TARGETS)}) ORDER BY id')
        require(len(rows)==95 and all(r['class_id']==51 and r['owner_user_id']==400 and r['media_ready']==b'\1' and r['acceptance_status']=='accepted' and r['status']=='active' for r in rows),'Class/media/acceptance mismatch')
        require(all(r['order_id'] is None and r['order_item_id'] is None and r['registration_case_id'] is None for r in rows),'Legacy order linkage remains')
        hexes=read(q,f"SELECT id,HEX(student_name_snapshot) name_hex,HEX(JSON_UNQUOTE(JSON_EXTRACT(service_snapshot,'$.name'))) course_hex FROM {SERVICE} WHERE tenant_id=1 AND id IN ({ids(TARGETS)}) ORDER BY id")
        by_id={r['id']:r for r in rows}
        require(all(r['name_hex']==by_id[r['id']]['student_name_snapshot'].encode().hex().upper() and r['course_hex']=='大健康轻创网红博主'.encode().hex().upper() for r in hexes),'UTF-8 mismatch')
        require(all(json.loads(r['service_snapshot'])['productRef']=='course_migrated_P100044' for r in rows),'Wrong course identifier')
        pending=read(q,"SELECT id FROM zsjos_service_relation WHERE tenant_id=1 AND deleted=0 AND status='active' AND teaching_delivery=1 AND teaching_closed=0 AND class_id=1 AND creator LIKE 'seed%' AND (content_director_user_id IS NOT NULL OR operator_user_id IS NOT NULL)")
        require(not pending,'Seeded media students still unassigned')
        count=read(q,"SELECT COUNT(*) n FROM zsjos_service_relation WHERE tenant_id=1 AND deleted=0 AND status IN ('active','paused','completed') AND teaching_delivery=1 AND teaching_closed=0 AND class_id=51")[0]['n']
        summary={'when':when,'plan_sha256':digest,'assigned':95,'course_corrections':93,'detached_seed_chains':39,
            'class51_services':count,'remaining_seeded_pending':0,'protected_tables':len(plan['before']),
            'media_accounts_unchanged':sum(r['deleted']==b'\0' and r['create_service_relation_id'] in TARGETS for r in plan['before']['zsjos_media_account']),
            'orders_tasks_other_courses_unchanged':True,'utf8_hex':True,'backend_unchanged':True}
    c.rollback();save('verification.json',summary)
    print('PASS independent whole-row verification, 95 class/media states, course HEX, unchanged orders/tasks/process; class51 total',count)


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('command',choices=['prepare','fixture','preview','apply','verify']);p.add_argument('--sha256');args=p.parse_args()
    env=os.getenv('ZSJOS_AGENT_ENV')
    if env is None:
        marker=Path('/etc/zsjos/agent-environment');env=marker.read_text().strip() if marker.exists() else 'local'
    require(env=='test','Only explicitly authorized test target allowed')
    c=connect('zsjos-mysql-1')
    try:
        with c.cursor() as q:
            q.execute('SET NAMES utf8mb4');q.execute("SET time_zone='+08:00'");q.execute('SET SESSION innodb_lock_wait_timeout=5')
            q.execute('SET SESSION TRANSACTION ISOLATION LEVEL REPEATABLE READ')
        if args.command=='prepare':prepare(c);return
        data=(ROOT/'plan.json').read_bytes();require(hashlib.sha256(data).hexdigest()==args.sha256,'Exact plan digest required')
        plan=decoded(data.decode('utf-8'));require(plan['tenant']==1 and plan['database']=='zsjos','Wrong plan scope')
        if args.command=='fixture':fixture(c,plan,args.sha256)
        elif args.command=='verify':verify(c,plan,args.sha256)
        else:apply(c,plan,args.sha256,args.command=='preview')
    finally:c.rollback();c.close()


if __name__=='__main__':main()
