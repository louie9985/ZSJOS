#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Exact authorized test repair; see student-service-batch-merge-20261029.md.

This frozen one-time mapping is not an automatic course/identity matching rule.
"""
import argparse
import copy
import hashlib
import json
import os
from pathlib import Path

from close_legacy_registration_20261028 import encoded, read, save, require, rows
from transfer_student_20261015 import connect

ROOT = Path('/opt/zsjos-runtime/backups/student-service-batch-merge-20261029/review2')
SERVICE, EVENT = 'zsjos_service_relation', 'zsjos_business_event'
MARKER = 'student-service-batch-20261029'
REASON = '按用户确认合并重复分班：保留旧服务及教学历史，课程和订单报名分班以新服务为准，物理删除新增服务'
PAIRS = {2025:1708,2027:1868,2028:1622,2029:1999,2030:1528,2032:1527,
         2034:1366,2036:1735,2038:1940,2039:1362,2041:1365,2042:1368,
         2043:1370,2044:1369,2046:1275,2047:1945,2048:1941,2049:1630,
         2050:1629,2051:1488,2053:1363}
PROTECTED = (1190,2031,1770,2018,1986,1989,1635)
TRANSFER = ('order_id','order_item_id','registration_case_id','class_id','owner_user_id','service_snapshot')
SERVICE_KEYS = ('service_relation_id','source_service_relation_id','create_service_relation_id')
JSON_KEYS = (*SERVICE_KEYS,'serviceRelationId','sourceServiceRelationId','createServiceRelationId')


def ids(values):
    return ','.join(str(int(v)) for v in sorted(set(values)) if v is not None) or '-1'


def discover(q):
    meta = {}
    for r in rows(q, "SELECT TABLE_NAME,COLUMN_NAME,DATA_TYPE FROM information_schema.columns "
                  "WHERE table_schema=DATABASE() AND table_name LIKE 'zsjos%' ORDER BY TABLE_NAME,ORDINAL_POSITION"):
        if '_bak_' not in r['TABLE_NAME']:
            meta.setdefault(r['TABLE_NAME'], {})[r['COLUMN_NAME']] = r['DATA_TYPE']
    targets = rows(q, f'SELECT * FROM {SERVICE} WHERE tenant_id=1 AND id IN ({ids([*PAIRS,*PAIRS.values(),*PROTECTED])}) ORDER BY id')
    people = {r['person_id'] for r in targets}
    relations = rows(q, f'SELECT * FROM {SERVICE} WHERE tenant_id=1 AND person_id IN ({ids(people)}) ORDER BY id')
    orders = rows(q, f'SELECT id FROM zsjos_order WHERE tenant_id=1 AND person_id IN ({ids(people)}) ORDER BY id')
    order_ids = [r['id'] for r in orders]
    items = rows(q, f'SELECT id FROM zsjos_order_item WHERE tenant_id=1 AND order_id IN ({ids(order_ids)}) ORDER BY id')
    cases = rows(q, f'SELECT id FROM zsjos_registration_case WHERE tenant_id=1 AND order_id IN ({ids(order_ids)}) ORDER BY id')
    leads = rows(q, f'SELECT id FROM zsjos_lead WHERE tenant_id=1 AND person_id IN ({ids(people)}) ORDER BY id')
    relation_ids = sorted({r['id'] for r in relations} | set(PAIRS))
    keys = dict(person_id=people,student_person_id=people,order_id=order_ids,
                order_item_id=[r['id'] for r in items], registration_case_id=[r['id'] for r in cases],
                collaboration_group_id=[r['collaboration_group_id'] for r in relations if r['collaboration_group_id']],
                lead_id=[r['id'] for r in leads])
    for k in SERVICE_KEYS: keys[k] = relation_ids
    special = {'zsjos_person':people, 'zsjos_delivery_class':[r['class_id'] for r in relations if r['class_id']],
               'zsjos_collaboration_group':keys['collaboration_group_id']}
    pattern = '"(' + '|'.join(JSON_KEYS) + ')"[[:space:]]*:[[:space:]]*"?(' + '|'.join(map(str,PAIRS)) + ')([^0-9]|$)'
    queries = {}
    for t, cols in meta.items():
        if 'tenant_id' not in cols or 'id' not in cols: continue
        terms = [f'`{k}` IN ({ids(v)})' for k,v in keys.items() if k in cols]
        if t in special: terms.append(f'id IN ({ids(special[t])})')
        for typ,key in [('biz_type','biz_id'),('business_type','business_id'),('aggregate_type','aggregate_id')]:
            if typ in cols and key in cols:
                terms.append(f"(`{typ}` IN ('student_service','student-service') AND `{key}` IN ({ids(relation_ids)}))")
        for k,typ in cols.items():
            if typ in ('json','text','mediumtext','longtext'):
                terms.append(f"REGEXP_LIKE(CAST(`{k}` AS CHAR), '{pattern}')")
        if t == EVENT: terms.append(f"idempotency_key LIKE '{MARKER}:%'")
        if terms:
            selected = ','.join(f'`{k}`' for k in cols if k not in ('token_hash','token_ciphertext'))
            queries[t] = f'SELECT {selected} FROM `{t}` WHERE tenant_id=1 AND (' + ' OR '.join(terms) + ') ORDER BY id'
    for t,cols in [('system_notify_business_outbox','id,biz_type,biz_id,status'),
                   ('system_notify_message','id,biz_type,biz_id,source_event_key')]:
        queries[t] = f"SELECT {cols} FROM {t} WHERE tenant_id=1 AND biz_type='student_service' AND biz_id IN ({ids(PAIRS)}) ORDER BY id"
    return queries


def capture(q, queries, lock=False, before=None):
    if lock:
        for t in ('zsjos_order','zsjos_registration_case','zsjos_person',SERVICE):
            rows(q, f'SELECT id FROM `{t}` WHERE tenant_id=1 AND id IN ({ids(r["id"] for r in before[t])}) ORDER BY id FOR UPDATE')
        # A person-range service lock guards concurrent creation of another duplicate.
        people = [r['id'] for r in before['zsjos_person']]
        rows(q, f'SELECT id FROM {SERVICE} WHERE tenant_id=1 AND person_id IN ({ids(people)}) ORDER BY id FOR UPDATE')
    result = {t:rows(q,sql) for t,sql in queries.items()}
    if lock:
        for t,data in result.items():
            if not data or not t.startswith('zsjos_'): continue
            current = rows(q, queries[t].split(' WHERE ',1)[0]
                           + f' WHERE tenant_id=1 AND id IN ({ids(r["id"] for r in data)}) ORDER BY id FOR UPDATE')
            require(current == data, 'Concurrent row change: '+t)
    return result


def source_json_ref(v):
    if isinstance(v,dict):
        return any((k in JSON_KEYS and str(x) in {str(s) for s in PAIRS}) or source_json_ref(x) for k,x in v.items())
    if isinstance(v,list): return any(source_json_ref(x) for x in v)
    return False


def validate(q, state):
    sr = {r['id']:r for r in state[SERVICE]}
    require(set(PAIRS)|set(PAIRS.values()) <= sr.keys(), 'Target row missing')
    classes = {r['id']:r for r in state['zsjos_delivery_class']}
    orders = {r['id']:r for r in state['zsjos_order']}
    items = {r['id']:r for r in state['zsjos_order_item']}
    cases = {r['id']:r for r in state['zsjos_registration_case']}
    for source,keep in PAIRS.items():
        new,old = sr[source],sr[keep]
        require(old['person_id']==new['person_id'] and old['student_name_snapshot']==new['student_name_snapshot'], 'Identity changed')
        require(all(r['deleted']==b'\0' and r['status']=='active' and r['teaching_delivery']==b'\1'
                    and r['teaching_closed']==b'\0' for r in (new,old)), 'Service availability changed')
        require(all(old[k] is None for k in ('order_id','order_item_id','registration_case_id'))
                and old['acceptance_status']=='accepted' and old['delivery_stage']=='supervision', 'Old service state changed')
        require(new['acceptance_status']=='pending' and new['version']==0 and new['delivery_stage'] is None
                and new['collaboration_group_id'] is None and new['first_contact_progress_json'] is None
                and str(new['create_time']).startswith('2026-10-29'), 'New service has progressed')
        require(all(new[k] is None for k in ('content_director_user_id','operator_user_id','career_planner_user_id')), 'New media assignment')
        order,item,case = orders[new['order_id']],items[new['order_item_id']],cases[new['registration_case_id']]
        require(order['status']=='effective' and order['person_id']==old['person_id']
                and order['deleted']==b'\0' and order['create_time']<old['create_time'], 'Historical order changed')
        require(item['order_id']==order['id'] and item['deleted']==b'\0' and item['product_snapshot']==new['service_snapshot'], 'Order item changed')
        require(case['order_id']==order['id'] and case['order_item_id']==item['id'] and case['deleted']==b'\0', 'Case changed')
        clazz = classes[new['class_id']]
        require(clazz['deleted']==b'\0' and clazz['status']=='SERVING' and clazz['homeroom_user_id']==new['owner_user_id'], 'Destination class changed')
        require(any(r['registration_case_id']==case['id'] and r['order_item_id']==item['id']
                    and r['class_id']==new['class_id'] and r['homeroom_user_id']==new['owner_user_id']
                    and r['deleted']==b'\0' for r in state['zsjos_registration_class_assignment']), 'Saved assignment changed')
        history=[r for r in state['zsjos_student_contact_record'] if r['service_relation_id']==keep and r['deleted']==b'\0']
        require({r['contact_type'] for r in history} >= {'student_first_contact','student_study_plan'}, 'Old history missing')
    for t,data in state.items():
        if t==SERVICE or t.startswith('system_notify_'): continue
        for r in data:
            require(not any(r.get(k) in PAIRS for k in SERVICE_KEYS), 'Source dependent row: '+t)
            for typ,key in [('biz_type','biz_id'),('business_type','business_id'),('aggregate_type','aggregate_id')]:
                require(not (r.get(typ) in ('student_service','student-service') and str(r.get(key)) in {str(x) for x in PAIRS}), 'Source typed dependency: '+t)
            for v in r.values():
                if isinstance(v,str) and v[:1] in ('{','['):
                    try: obj=json.loads(v)
                    except ValueError: continue
                    require(not source_json_ref(obj), 'Source JSON dependency: '+t)
    for r in state['zsjos_business_task']:
        if r['biz_type']=='student_service' and r['biz_id'] in PAIRS.values():
            require(r['status']=='completed', 'Unfinished historical task needs transfer review')
    require(all(r['status']=='succeeded' for r in state['system_notify_business_outbox']), 'Notification in flight')
    require(not rows(q,"SELECT TRIGGER_NAME FROM information_schema.triggers WHERE trigger_schema=DATABASE() AND event_object_table IN (%s,%s)",(SERVICE,EVENT)), 'Unexpected trigger')
    require(not rows(q,"SELECT TABLE_NAME FROM information_schema.key_column_usage WHERE referenced_table_schema=DATABASE() AND referenced_table_name=%s",(SERVICE,)), 'Unexpected foreign key')
    engines=rows(q,"SELECT ENGINE FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN (%s,%s)",(SERVICE,EVENT))
    require(len(engines)==2 and all(r['ENGINE']=='InnoDB' for r in engines), 'Transactional engine required')


def refs(before, source, keep):
    new = next(r for r in before[SERVICE] if r['id']==source)
    return dict(retainedServiceId=keep,removedServiceId=source,orderId=new['order_id'],
                orderItemId=new['order_item_id'],registrationCaseId=new['registration_case_id'],courseSource='new_service')


def mutate(q, before, prefix='', fail_after=None):
    sr={r['id']:r for r in before[SERVICE]}
    count=0
    def check():
        nonlocal count
        require(q.rowcount==1,'Expected one affected row')
        count+=1
        if fail_after==count: raise RuntimeError('Injected failure')
    for source,keep in PAIRS.items():
        old,new=sr[keep],sr[source]
        # Free the unique tenant/order-item key within the same InnoDB transaction.
        q.execute(f'DELETE FROM `{prefix}{SERVICE}` WHERE tenant_id=1 AND id=%s AND person_id=%s AND version=%s',
                  (source,new['person_id'],new['version']));check()
        q.execute(f'UPDATE `{prefix}{SERVICE}` SET '+','.join(f'`{k}`=%s' for k in TRANSFER)
                  + ',version=version+1,updater=%s,update_time=NOW() WHERE tenant_id=1 AND id=%s AND person_id=%s AND version=%s',
                  tuple(new[k] for k in TRANSFER)+(MARKER,keep,old['person_id'],old['version']));check()
        q.execute(f'INSERT INTO `{prefix}{EVENT}` (event_type,aggregate_type,aggregate_id,operator_user_id,reason,'
                  'related_object_refs,occurred_at,idempotency_key,creator,updater,tenant_id) '
                  "VALUES ('system_data_repair','student_service',%s,NULL,%s,%s,NOW(),%s,%s,%s,1)",
                  (keep,REASON,json.dumps(refs(before,source,keep)),f'{MARKER}:{source}',MARKER,MARKER));check()


def compare(before, after):
    require(before.keys()==after.keys(),'Table inventory changed')
    sr={r['id']:r for r in before[SERVICE]}
    reverse={k:s for s,k in PAIRS.items()}
    for t,original in before.items():
        actual={r['id']:r for r in after[t]}; expected_ids={r['id'] for r in original}
        if t==SERVICE: expected_ids-=PAIRS.keys()
        if t==EVENT:
            added=[r for r in after[t] if r['id'] not in expected_ids]
            require(len(added)==len(PAIRS),'Audit event count mismatch')
            require({r['idempotency_key'] for r in added}=={f'{MARKER}:{s}' for s in PAIRS},'Audit keys mismatch')
            for e in added:
                source=int(e['idempotency_key'].rsplit(':',1)[1]);keep=PAIRS[source]
                require(e['event_type']=='system_data_repair' and e['aggregate_type']=='student_service'
                        and e['aggregate_id']==keep and e['operator_user_id'] is None and e['reason']==REASON
                        and e['tenant_id']==1 and json.loads(e['related_object_refs'])==refs(before,source,keep),'Audit mismatch')
                expected_ids.add(e['id'])
        require(set(actual)==expected_ids,'Unexpected row set: '+t)
        for r in original:
            if t==SERVICE and r['id'] in PAIRS: continue
            want,got=copy.deepcopy(r),actual[r['id']]
            if t==SERVICE and r['id'] in reverse:
                new=sr[reverse[r['id']]]
                want.update({k:new[k] for k in TRANSFER})
                want.update(version=r['version']+1,updater=MARKER,update_time=got['update_time'])
            require(want==got,'Unexpected field change: '+t)


def apply_once(q,before,current,prefix=''):
    if not any(r['id'] in PAIRS for r in current[SERVICE]):
        compare(before,current)
        return False
    require(before==current,'Before-image drift or partial merge; review required')
    mutate(q,before,prefix)
    return True


def rehearse(c,before):
    tables=(SERVICE,EVENT)
    with c.cursor() as q:
        for t in tables:
            q.execute(f'CREATE TEMPORARY TABLE `batch_test_{t}` LIKE `{t}`')
            cols=[r['Field'] for r in rows(q,f'SHOW COLUMNS FROM `{t}`')
                  if not any(k in r['Extra'] for k in ('VIRTUAL GENERATED','STORED GENERATED'))]
            if before[t]:
                q.executemany(f'INSERT INTO `batch_test_{t}` ('+','.join(f'`{k}`' for k in cols)+') VALUES ('
                              +','.join(['%s']*len(cols))+')',[tuple(r[k] for k in cols) for r in before[t]])
    c.commit()
    def state(q):return dict(before,**{t:rows(q,f'SELECT * FROM `batch_test_{t}` ORDER BY id') for t in tables})
    with c.cursor() as q:require(state(q)==before,'Fixture mismatch')
    c.rollback()
    for fail in range(1,3*len(PAIRS)+1):
        with c.cursor() as q:
            try:mutate(q,before,'batch_test_',fail)
            except RuntimeError as e:require(str(e)=='Injected failure','Unexpected failure')
            else:raise RuntimeError('Failure injection skipped')
        c.rollback()
        with c.cursor() as q:require(state(q)==before,'Rollback failed')
        c.rollback()
    with c.cursor() as q:
        q.execute(f'DELETE FROM `batch_test_{SERVICE}` WHERE id=%s',(next(iter(PAIRS)),))
        try:apply_once(q,before,state(q),'batch_test_')
        except RuntimeError as e:require('Before-image drift' in str(e),'Unexpected partial failure')
        else:raise RuntimeError('Partial merge accepted')
    c.rollback()
    with c.cursor() as q:
        require(apply_once(q,before,state(q),'batch_test_'),'Initial apply skipped');compare(before,state(q))
    c.commit()
    with c.cursor() as q:
        after=state(q)
        require(not apply_once(q,before,after,'batch_test_') and state(q)==after,'Repeat changed data')
        for source,keep in reversed(list(PAIRS.items())):
            old=next(r for r in before[SERVICE] if r['id']==keep)
            new=next(r for r in before[SERVICE] if r['id']==source)
            cols=[k for k in old if k!='id']
            q.execute(f'UPDATE `batch_test_{SERVICE}` SET '+','.join(f'`{k}`=%s' for k in cols)+' WHERE tenant_id=1 AND id=%s',tuple(old[k] for k in cols)+(keep,))
            q.execute(f'INSERT INTO `batch_test_{SERVICE}` ('+','.join(f'`{k}`' for k in new)+') VALUES ('+','.join(['%s']*len(new))+')',tuple(new.values()))
        require(state(q)[SERVICE]==before[SERVICE],'Compensating restore failed')
    c.rollback()


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('mode',choices=['prepare','rehearse','apply','verify'])
    parser.add_argument('--sha256')
    a=parser.parse_args()
    env=os.environ.get('ZSJOS_AGENT_ENV')
    if env is None:env=Path('/etc/zsjos/agent-environment').read_text().strip()
    require(env=='test','Only authorized test target supported')
    os.umask(0o077);ROOT.mkdir(parents=True,exist_ok=True,mode=0o700)
    require(ROOT.stat().st_mode & 0o077==0,'Private evidence required')
    plan_path=ROOT/'plan.json';script_sha=hashlib.sha256(Path(__file__).read_bytes()).hexdigest()
    c=connect('zsjos-mysql-1')
    try:
        with c.cursor() as q:
            q.execute('SET NAMES utf8mb4');q.execute("SET time_zone='+08:00'")
            q.execute('SET SESSION innodb_lock_wait_timeout=10')
            if a.mode=='prepare':
                q.execute('SET TRANSACTION READ ONLY');q.execute('START TRANSACTION WITH CONSISTENT SNAPSHOT')
                queries=discover(q);before=capture(q,queries);validate(q,before)
                save(plan_path,dict(queries=queries,before=before))
                print(json.dumps(dict(prepared=True,sha256=hashlib.sha256(plan_path.read_bytes()).hexdigest(),
                                      pairs=len(PAIRS),tables=len(before),rows=sum(map(len,before.values())))))
                return
            require(a.sha256==hashlib.sha256(plan_path.read_bytes()).hexdigest(),'Frozen plan hash required')
            plan=read(plan_path);before=plan['before'];queries=plan['queries']
            if a.mode!='apply':q.execute('SET TRANSACTION READ ONLY')
            q.execute('START TRANSACTION WITH CONSISTENT SNAPSHOT')
            current=capture(q,queries,lock=a.mode=='apply',before=before)
            # New source references/tables are discovered independently before any write.
            fresh=discover(q)
            if any(r['id'] in PAIRS for r in current[SERVICE]):
                require(fresh==queries,'Reference inventory changed')
            else:
                require(fresh.keys()==queries.keys(),'Schema inventory changed')
            if a.mode=='rehearse':
                require(current==before,'Before-image changed');validate(q,current);c.rollback()
                rehearse(c,before)
                save(ROOT/'rehearsal.json',dict(passed=True,sha256=a.sha256,script_sha256=script_sha,
                     failure_points=3*len(PAIRS),repeat=True,partial_rejected=True,compensation=True))
            elif a.mode=='apply':
                evidence=read(ROOT/'rehearsal.json')
                require(evidence['passed'] and evidence['sha256']==a.sha256 and evidence['script_sha256']==script_sha,'Matching rehearsal required')
                exists=any(r['id'] in PAIRS for r in current[SERVICE])
                if exists:
                    validate(q,current);require(current==before,'Before-image drift')
                    save(ROOT/'locked-before.json',current)
                changed=apply_once(q,before,current)
                after=capture(q,queries);compare(before,after)
                if changed:save(ROOT/'after.json',after)
                c.commit()
                receipt=dict(applied=changed,deleted=len(PAIRS) if changed else 0,updated=len(PAIRS) if changed else 0,audits=len(PAIRS) if changed else 0)
                save(ROOT/('receipt.json' if changed else 'repeat.json'),receipt);print(json.dumps(receipt))
            else:
                compare(before,current);require(current==read(ROOT/'after.json'),'Independent after-image mismatch')
                require(not rows(q,f'SELECT id FROM {SERVICE} WHERE tenant_id=1 AND id IN ({ids(PAIRS)})'),'Deleted rows remain')
                for source,keep in PAIRS.items():
                    oldnew=next(r for r in before[SERVICE] if r['id']==source)
                    require(rows(q,f'SELECT id FROM {SERVICE} WHERE tenant_id=1 AND order_item_id=%s',(oldnew['order_item_id'],))==[{'id':keep}],'Order item not unique')
                for r in rows(q,f'SELECT student_name_snapshot,HEX(student_name_snapshot) h1,service_snapshot,HEX(service_snapshot) h2 FROM {SERVICE} WHERE tenant_id=1 AND id IN ({ids(PAIRS.values())})'):
                    require(r['student_name_snapshot'].encode('utf-8').hex().upper()==r['h1'] and r['service_snapshot'].encode('utf-8').hex().upper()==r['h2'],'UTF-8 mismatch')
                audit_hex=rows(q,f'SELECT HEX(reason) h FROM {EVENT} WHERE tenant_id=1 AND idempotency_key LIKE %s',(MARKER+':%',))
                require(len(audit_hex)==len(PAIRS) and all(r['h']==REASON.encode('utf-8').hex().upper() for r in audit_hex),'Audit UTF-8 mismatch')
                save(ROOT/'verification.json',dict(passed=True,sha256=a.sha256,protected_tables=len(before),
                     pairs=len(PAIRS),independent_connection=True,utf8_hex=True))
            if a.mode!='apply':print(json.dumps(dict(mode=a.mode,verified=True)))
    finally:c.rollback();c.close()


if __name__=='__main__':main()
