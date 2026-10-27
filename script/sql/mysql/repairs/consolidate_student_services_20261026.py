#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Authorized, exact test-tenant service consolidation; never a migration/bootstrap input.

See docs/operations/student-service-consolidation-20261026.md. Full private before-images,
SHA-bound plan, one transaction, and original service IDs protect existing media workflows.
"""
import argparse
import copy
import datetime as dt
import hashlib
import io
import json
import os
from pathlib import Path
import zipfile

from exit_teaching_delivery_batch2_20261022 import encoded, decoded
from transfer_student_20261015 import connect, require

ROOT = Path('/opt/zsjos-runtime/backups/student-service-consolidation-20261026-test')
MARKER = 'service-consolidation-20261026'
REASON = '按用户确认合并同一次大健康轻创履约，保留媒体服务及历史；未明确课程按当前目录补正'
SERVICE = 'zsjos_service_relation'
TASK = 'zsjos_business_task'
CONTACT = 'zsjos_student_contact_record'
EVENT = 'zsjos_business_event'
PAIRS = {1010: 1000, 1011: 1002, 1100: 1080}
PEOPLE = (7950, 8007, 8028, 8103, 8606)
SURVIVORS = (1000, 1001, 1002, 1003, 1080)
SOURCE_ORDERS = {1010: (8685, 9198, 894), 1011: (8692, 9205, 898), 1100: (8939, 9437, 1093)}
COURSE_REF = 'course_migrated_P100044'
TARGET_CLASS = 51


def read(q, sql, args=None):
    q.execute(sql, args)
    return list(q.fetchall())


def ids(values):
    return ','.join(str(int(x)) for x in sorted(set(values))) or '-1'


def private_save(name, value):
    ROOT.mkdir(mode=0o700, parents=True, exist_ok=True)
    require(ROOT.stat().st_mode & 0o077 == 0, 'Evidence directory is not private')
    data = encoded(value).encode('utf-8')
    fd = os.open(ROOT / name, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(fd, 'wb') as f:
        f.write(data)
        f.flush()
        os.fsync(f.fileno())
    return hashlib.sha256(data).hexdigest()


def runtime():
    found = []
    for proc in Path('/proc').iterdir():
        if not proc.name.isdigit():
            continue
        try:
            argv = (proc / 'cmdline').read_bytes().split(b'\0')
            if not argv or b'java' not in argv[0] or b'-jar' not in argv:
                continue
            jar = Path(argv[argv.index(b'-jar') + 1].decode())
            if not jar.is_absolute():
                jar = (proc / 'cwd').resolve() / jar
            if 'zsjos' not in str(jar):
                continue
            with zipfile.ZipFile(jar) as z:
                lib = next(n for n in z.namelist() if n.startswith('BOOT-INF/lib/yudao-module-zsjos') and n.endswith('.jar'))
                raw = z.read(lib)
                with zipfile.ZipFile(io.BytesIO(raw)) as module:
                    checks = ('positioning/PositioningCardService.class',
                              'positioninginterview/PositioningInterviewService.class',
                              'registration/StudentServiceObjectPermissionProvider.class')
                    for suffix in checks:
                        name = next(n for n in module.namelist() if n.endswith(suffix))
                        require(b'MediaServiceReadiness' in module.read(name), 'Deployed media isolation missing')
            # /proc start ticks distinguish a restarted process even if a PID is reused.
            ticks = (proc / 'stat').read_text().rsplit(')', 1)[1].split()[19]
            found.append({'pid': int(proc.name), 'start_ticks': ticks, 'jar': str(jar.resolve()),
                          'module_sha256': hashlib.sha256(raw).hexdigest()})
        except (FileNotFoundError, ProcessLookupError, PermissionError):
            continue
    require(len(found) == 1, 'Expected exactly one running backend')
    return found[0]


def metadata(q):
    result = {}
    for row in read(q, "SELECT TABLE_NAME,COLUMN_NAME,DATA_TYPE FROM information_schema.columns WHERE table_schema=DATABASE() AND (TABLE_NAME LIKE 'zsjos_%' OR TABLE_NAME IN ('system_users','system_role','system_user_role'))"):
        result.setdefault(row['TABLE_NAME'], {})[row['COLUMN_NAME']] = row['DATA_TYPE']
    return result


def discover(q):
    meta = metadata(q)
    relations = read(q, f'SELECT * FROM {SERVICE} WHERE tenant_id=1 AND person_id IN ({ids(PEOPLE)}) ORDER BY id')
    accounts = read(q, f'SELECT * FROM zsjos_media_account WHERE tenant_id=1 AND student_person_id IN ({ids(PEOPLE)}) ORDER BY id')
    orders = read(q, f'SELECT * FROM zsjos_order WHERE tenant_id=1 AND person_id IN ({ids(PEOPLE)}) ORDER BY id')
    order_ids = [r['id'] for r in orders]
    items = read(q, f'SELECT id FROM zsjos_order_item WHERE tenant_id=1 AND order_id IN ({ids(order_ids)})')
    cases = read(q, f'SELECT id FROM zsjos_registration_case WHERE tenant_id=1 AND order_id IN ({ids(order_ids)})')
    service_ids = [r['id'] for r in relations]
    account_ids = [r['id'] for r in accounts]
    groups = [r['collaboration_group_id'] for r in relations if r['collaboration_group_id'] is not None]
    keys = {'person_id': PEOPLE, 'student_person_id': PEOPLE,
            'service_relation_id': service_ids, 'create_service_relation_id': service_ids,
            'source_service_relation_id': service_ids, 'collaboration_group_id': groups,
            'account_id': account_ids, 'order_id': order_ids,
            'order_item_id': [r['id'] for r in items], 'registration_case_id': [r['id'] for r in cases]}
    # Follow typed business references, not coincidentally equal numeric IDs.
    for table, key in [('zsjos_content', 'content_id'), ('zsjos_production_ticket', 'ticket_id'),
                       ('zsjos_positioning_card', 'card_id'), ('zsjos_student_delivery_plan', 'plan_id'),
                       ('zsjos_student_delivery_stage', 'stage_id'), ('zsjos_content_review_batch', 'batch_id')]:
        cols = meta.get(table, {})
        conditions = [f'`{k}` IN ({ids(v)})' for k, v in keys.items() if k in cols]
        if 'account_ids_json' in cols:
            conditions.extend(f"JSON_CONTAINS(account_ids_json,'{a}')" for a in account_ids)
        values = read(q, f"SELECT id FROM `{table}` WHERE tenant_id=1 AND ({' OR '.join(conditions)})") if conditions else []
        keys[key] = [r['id'] for r in values]
    queries = {}
    for table, cols in sorted(meta.items()):
        if 'tenant_id' not in cols or 'id' not in cols or '_bak_' in table or table.startswith('system_'):
            continue
        conditions = [f'`{key}` IN ({ids(values)})' for key, values in keys.items() if key in cols]
        if 'account_ids_json' in cols:
            conditions.extend(f"JSON_CONTAINS(account_ids_json,'{a}')" for a in account_ids)
        special = {'zsjos_person': PEOPLE, 'zsjos_order': order_ids,
                   'zsjos_collaboration_group': groups,
                   'zsjos_delivery_class': [r['class_id'] for r in relations if r['class_id'] is not None] + [51],
                   'zsjos_product': [42], 'zsjos_product_sku': [225],
                   'zsjos_product_category': [13], 'zsjos_product_attr': [50]}
        if table in special:
            conditions.append(f'id IN ({ids(special[table])})')
        if table == 'zsjos_product_attr_value':
            conditions.append('attr_id=50')
        if table == TASK:
            conditions += [f"(biz_type='student_service' AND biz_id IN ({ids(service_ids)}))",
                           f"(biz_type='media_account_diagnosis' AND biz_id IN ({ids(account_ids)}))",
                           f"(biz_type='student_delivery_stage' AND biz_id IN ({ids(keys['stage_id'])}))",
                           f"(JSON_UNQUOTE(JSON_EXTRACT(payload,'$.serviceRelationId')) IN ({','.join(repr(str(x)) for x in service_ids)}))"]
        if table == EVENT:
            conditions += [f"(aggregate_type IN ('student_service','student-service') AND aggregate_id IN ({ids(service_ids)}))"]
        if conditions:
            queries[table] = f"SELECT * FROM `{table}` WHERE tenant_id=1 AND ({' OR '.join(conditions)}) ORDER BY id"
    # Protect current class eligibility without reading authentication or contact secrets.
    queries['system_users'] = 'SELECT id,status,deleted,dept_id FROM system_users WHERE tenant_id=1 AND id=400 ORDER BY id'
    queries['system_role'] = "SELECT id,code,status,deleted FROM system_role WHERE tenant_id=1 AND code='study_planner' ORDER BY id"
    queries['system_user_role'] = 'SELECT * FROM system_user_role WHERE tenant_id=1 AND user_id=400 ORDER BY id'
    return queries


def capture(q, queries, lock=False):
    return {table: read(q, sql + (' FOR UPDATE' if lock else '')) for table, sql in sorted(queries.items())}


def canonical_json(q, value):
    return read(q, 'SELECT CAST(%s AS JSON) value', (json.dumps(value, ensure_ascii=False),))[0]['value']


def course_snapshot(q, state):
    product = next(r for r in state['zsjos_product'] if r['id'] == 42)
    category = next(r for r in state['zsjos_product_category'] if r['id'] == 13)
    require(product['product_ref'] == COURSE_REF and product['status'] == 0 and product['deleted'] == b'\0', 'Course unavailable')
    require(product['category_id'] == 13 and category['parent_id'] == 0 and category['status'] == 0, 'Course category changed')
    # Only the course was confirmed for these two independent enrollments. Do not invent a SKU or paid price.
    return canonical_json(q, {'productRef': product['product_ref'], 'name': product['name'],
        'categoryId': category['id'], 'categoryName': category['name'],
        'categoryPath': [{'id': category['id'], 'name': category['name']}],
        'level1CategoryId': category['id'], 'level1CategoryName': category['name'],
        'level2CategoryId': None, 'level2CategoryName': None, 'skuRef': None,
        'skuName': '未明确具体班次/方案', 'selectedAttrValuesJson': None, 'price': None,
        'spuUnknown': False, 'skuUnknown': True, 'specs': []})


def build(q, state):
    sr = {r['id']: r for r in state[SERVICE]}
    expected_people = {1000:8007,1001:7950,1002:8028,1003:8103,1080:8606,1010:8007,1011:8028,1100:8606}
    for rid, person in expected_people.items():
        r = sr[rid]
        require(r['person_id'] == person and r['status'] == 'active' and r['deleted'] == b'\0'
                and r['teaching_delivery'] == b'\1' and r['teaching_closed'] == b'\0', 'Service state drift')
    for rid in SURVIVORS:
        r = sr[rid]
        require(r['acceptance_status'] == 'accepted' and r['owner_user_id'] is None and r['class_id'] == 1
                and r['director_stage'] == 'positioning_interview_completed'
                and r['content_director_user_id'] is not None and r['operator_user_id'] is not None
                and json.loads(r['service_snapshot'])['name'] == '未明确课程', 'Media survivor contract changed')
    target = next(r for r in state['zsjos_delivery_class'] if r['id'] == 51)
    require((target['status'],target['class_kind'],target['homeroom_user_id'],target['deleted']) == ('SERVING','NORMAL',400,b'\0'), 'Target class unavailable')
    require(state['system_users'][0]['status'] == 0 and state['system_users'][0]['deleted'] == b'\0', 'Class owner unavailable')
    eligible = {r['id'] for r in state['system_role'] if r['status']==0 and r['deleted']==b'\0'}
    require(any(r['role_id'] in eligible and r['deleted']==b'\0' for r in state['system_user_role']), 'Class owner no longer eligible')
    moves = []
    def add(table, rid, fields, time_fields=()):
        old = next(r for r in state[table] if r['id'] == rid)
        fields = {k: v for k, v in fields.items() if old[k] != v}
        require(bool(fields) or bool(time_fields), 'Unexpected no-op patch')
        moves.append({'table':table, 'id':rid, 'before':old, 'fields':fields, 'time_fields':list(time_fields)})
    # Release the unique order-item keys before assigning them to survivors. No order row is changed.
    for source, survivor in PAIRS.items():
        r = sr[source]
        require(tuple(r[k] for k in ('order_id','order_item_id','registration_case_id')) == SOURCE_ORDERS[source], 'Source order relation changed')
        require(r['content_director_user_id'] is None and r['operator_user_id'] is None and r['collaboration_group_id'] is None, 'Source has media work')
        order = next(x for x in state['zsjos_order'] if x['id'] == r['order_id'])
        item = next(x for x in state['zsjos_order_item'] if x['id'] == r['order_item_id'])
        require(order['status']=='effective' and order['deleted']==b'\0' and item['deleted']==b'\0'
                and item['order_id']==order['id'] and json.loads(item['product_snapshot'])['productRef']==COURSE_REF, 'Order no longer deliverable')
        require(r['acceptance_status']==('accepted' if source==1100 else 'pending'), 'Teaching status drift')
        add(SERVICE, source, {'order_id':None,'order_item_id':None,'registration_case_id':None,
            'status':'cancelled','teaching_delivery':b'\0','media_ready':b'\0',
            'termination_reason':f'同次履约经用户确认合并至服务 {survivor}；历史保留，订单未取消'}, ['terminated_at'])
    teaching_fields = ('order_id','order_item_id','registration_case_id','acceptance_status','accepted_at',
        'accepted_by_user_id','owner_user_id','class_id','delivery_stage','delivery_data_json',
        'exam_date','exam_date_version','last_notified_exam_date','exam_notice_sent_at','first_contact_progress_json',
        'teaching_closed','teaching_closed_at','teaching_previous_class_id','teaching_restored')
    for source, survivor in PAIRS.items():
        src = sr[source]
        item = next(x for x in state['zsjos_order_item'] if x['id']==src['order_item_id'])
        patch = {k:src[k] for k in teaching_fields}
        patch.update(media_ready=b'\1', service_snapshot=item['product_snapshot'])
        if source in (1010,1011):
            patch.update(class_id=51, owner_user_id=400)
        add(SERVICE, survivor, patch)
    snapshot = course_snapshot(q, state)
    for rid in (1001,1003):
        require(sr[rid]['creator']=='seed-batch4', 'Course correction is not a known seed')
        add(SERVICE, rid, {'order_id':None,'order_item_id':None,'registration_case_id':None,
                          'service_snapshot':snapshot,'media_ready':b'\1'})
    # Only observed teaching records are movable. Any newly discovered dependency blocks the plan.
    for table, rows in state.items():
        if table == SERVICE:
            continue
        for r in rows:
            for key in ('service_relation_id','create_service_relation_id','source_service_relation_id'):
                if r.get(key) in PAIRS:
                    require(table==CONTACT and r['id'] in (25,26) and r[key]==1100, 'Unexpected source service dependency')
                    add(table,r['id'],{key:1080})
    tasks = [r for r in state[TASK] if r['biz_type']=='student_service' and r['biz_id'] in PAIRS]
    require({r['id'] for r in tasks}=={15098,15099,15100}, 'Teaching task set changed')
    for task in tasks:
        require(task['biz_id']==1100 and task['status']==('pending' if task['id']==15100 else 'completed'), 'Teaching task state changed')
        payload=json.loads(task['payload']);require(payload['serviceRelationId']==1100,'Task payload mismatch')
        payload['serviceRelationId']=1080
        add(TASK,task['id'],{'biz_id':1080,'payload':canonical_json(q,payload)})
    require(len(moves)==13, 'Unexpected exact mutation count')
    return moves


def expected(change, when):
    result = dict(change['before'], **change['fields'])
    for key in change['time_fields']:
        result[key] = when
    if 'version' in result:
        result['version'] += 1
    result['updater'] = MARKER
    result['update_time'] = when
    return result


def expected_state(plan, when):
    result = copy.deepcopy(plan['before'])
    for change in plan['mutations']:
        target = result[change['table']]
        index = next(i for i,r in enumerate(target) if r['id']==change['id'])
        target[index] = expected(change,when)
    return result


def receipts(q):
    return read(q, f'SELECT * FROM {EVENT} WHERE tenant_id=1 AND idempotency_key LIKE %s ORDER BY id', (MARKER+':%',))


def comparable(state):
    result = copy.deepcopy(state)
    # Repair receipts are verified separately; prior immutable events remain protected.
    result[EVENT] = [r for r in result.get(EVENT,[]) if not r['idempotency_key'].startswith(MARKER+':')]
    return result


def change_rows(q, plan, digest, when, fail_after=None):
    for index, change in enumerate(plan['mutations'],1):
        table,rid=change['table'],change['id']
        current=read(q,f'SELECT * FROM `{table}` WHERE tenant_id=1 AND id=%s FOR UPDATE',(rid,))
        require(current==[change['before']], 'Row drift; refusing overwrite')
        after=expected(change,when)
        fields={k:v for k,v in after.items() if current[0][k]!=v}
        q.execute(f'UPDATE `{table}` SET '+','.join(f'`{k}`=%s' for k in fields)+' WHERE tenant_id=1 AND id=%s',(*fields.values(),rid))
        require(q.rowcount==1,'Unexpected mutation count')
        refs={'table':table,'rowId':rid,'changedFields':list(fields),'planSha256':digest}
        if table==SERVICE:
            refs.update(survivingServiceId=PAIRS.get(rid,rid),
                        oldOrderId=current[0]['order_id'],newOrderId=after['order_id'],
                        oldOrderItemId=current[0]['order_item_id'],newOrderItemId=after['order_item_id'],
                        oldRegistrationCaseId=current[0]['registration_case_id'],newRegistrationCaseId=after['registration_case_id'])
        else:
            refs.update(oldServiceId=1100,newServiceId=1080)
        q.execute(f'INSERT INTO {EVENT} (event_type,aggregate_type,aggregate_id,operator_user_id,reason,related_object_refs,occurred_at,idempotency_key,creator,updater,create_time,update_time,tenant_id) VALUES (%s,%s,%s,NULL,%s,%s,%s,%s,%s,%s,%s,%s,1)',
            ('student_service_consolidation',table.removeprefix('zsjos_'),rid,REASON,json.dumps(refs,ensure_ascii=False),when,
             f'{MARKER}:{table}:{rid}',MARKER,MARKER,when,when))
        if fail_after==index:
            raise RuntimeError('injected rollback')


def verify_receipts(q,plan,digest):
    rows=receipts(q)
    expected_keys={f"{MARKER}:{r['table']}:{r['id']}" for r in plan['mutations']}
    require({r['idempotency_key'] for r in rows}==expected_keys,'Partial audit; refusing replay')
    require(len({r['occurred_at'] for r in rows})==1,'Audit times inconsistent')
    require(all(r['event_type']=='student_service_consolidation' and r['operator_user_id'] is None
                and r['reason']==REASON and json.loads(r['related_object_refs'])['planSha256']==digest for r in rows),'Audit mismatch')
    return rows[0]['occurred_at']


def guard(q,plan,digest,lock=False):
    if lock:
        read(q,f'SELECT id FROM zsjos_person WHERE tenant_id=1 AND id IN ({ids(PEOPLE)}) ORDER BY id FOR UPDATE')
        read(q,f'SELECT id FROM {SERVICE} WHERE tenant_id=1 AND person_id IN ({ids(PEOPLE)}) ORDER BY id FOR UPDATE')
    current=capture(q,plan['queries'],lock)
    if receipts(q):
        when=verify_receipts(q,plan,digest)
        require(comparable(current)==comparable(expected_state(plan,when)),'Applied state changed; do not overwrite')
        return when
    require(current==plan['before'],'Preimage or dependency changed; re-review required')
    return None


def prepare(c):
    proc=runtime()
    with c.cursor() as q:
        q.execute('SET TRANSACTION READ ONLY');q.execute('START TRANSACTION WITH CONSISTENT SNAPSHOT')
        require(not receipts(q),'Repair already exists')
        queries=discover(q);before=capture(q,queries)
        mutations=build(q,before)
        plan={'database':'zsjos','tenant':1,'runtime':proc,'queries':queries,'before':before,
              'mutations':mutations,'created_at':read(q,'SELECT NOW() now')[0]['now']}
    c.rollback()
    digest=private_save('plan.json',plan)
    print('PLAN_SHA256',digest)
    print('MUTATIONS',len(mutations),'PROTECTED_TABLES',len(before),'CAPTURED_ROWS',sum(map(len,before.values())))


def load_plan(digest):
    raw=(ROOT/'plan.json').read_bytes()
    require(hashlib.sha256(raw).hexdigest()==digest,'Exact plan SHA256 required')
    plan=decoded(raw.decode('utf-8'))
    require(plan['tenant']==1 and plan['database']=='zsjos','Wrong scope')
    return plan


def fixture(c,plan,digest):
    with c.cursor() as q:
        require(guard(q,plan,digest) is None,'Fixture requires original state')
        for table in (SERVICE,CONTACT,TASK,EVENT):
            ddl=read(q,f'SHOW CREATE TABLE `{table}`')[0]['Create Table']
            q.execute(ddl.replace('CREATE TABLE ','CREATE TEMPORARY TABLE ',1))
            rows=plan['before'].get(table,[])
            if rows:
                columns=list(rows[0])
                q.executemany(f'INSERT INTO `{table}` ('+','.join('`'+x+'`' for x in columns)+') VALUES ('+','.join(['%s']*len(columns))+')',[tuple(r[k] for k in columns) for r in rows])
    c.commit()
    # Includes release of unique keys, survivor reassignment, task migration and final audit.
    for cutoff in (1,3,6,10,13):
        try:
            with c.cursor() as q:
                when=read(q,'SELECT NOW() now')[0]['now']
                change_rows(q,plan,digest,when,cutoff)
        except RuntimeError as e:
            require(str(e)=='injected rollback','Unexpected fixture failure')
            c.rollback()
        else:
            raise RuntimeError('Injected failure missing')
        with c.cursor() as q:
            require(guard(q,plan,digest) is None,'Failed rollback')
        c.rollback()
    with c.cursor() as q:
        when=read(q,'SELECT NOW() now')[0]['now']
        change_rows(q,plan,digest,when)
        require(guard(q,plan,digest)==when,'Initial application/recovery mismatch')
    c.commit()
    with c.cursor() as q:
        require(guard(q,plan,digest)==when,'Replay failed')
        # A partial commit/audit must never be mistaken for a completed repair.
        q.execute(f'DELETE FROM {EVENT} WHERE tenant_id=1 AND idempotency_key=%s',(f'{MARKER}:{SERVICE}:1010',))
        try:
            guard(q,plan,digest)
        except RuntimeError as e:
            require('Partial audit' in str(e),'Wrong partial-state failure')
        else:
            raise RuntimeError('Partial state accepted')
    c.rollback()
    with c.cursor() as q:
        # Rollback rehearsal restores references in reverse order, freeing real item keys first.
        for change in reversed(plan['mutations']):
            table=change['table'];old=change['before'];after=expected(change,when)
            fields={k:v for k,v in old.items() if after[k]!=v}
            q.execute(f'UPDATE `{table}` SET '+','.join(f'`{k}`=%s' for k in fields)+' WHERE tenant_id=1 AND id=%s',(*fields.values(),change['id']))
        q.execute(f'DELETE FROM {EVENT} WHERE tenant_id=1 AND idempotency_key LIKE %s',(MARKER+':%',))
        require(guard(q,plan,digest) is None,'Restoration rehearsal mismatch')
    c.rollback()
    private_save('fixture.json',{'plan_sha256':digest,'rollback_cutoffs':[1,3,6,10,13],
        'initial_recovery_replay_partial_refusal_restore':True,'mutations':13,'temporary_tables_only':True})
    print('PASS MySQL temporary-table initial/rollback/recovery/replay/partial-refusal/restoration')


def apply(c,plan,digest,preview=False):
    require(runtime()==plan['runtime'],'Backend process/artifact changed')
    require((ROOT/'fixture.json').exists(),'Controlled fixture required before application')
    with c.cursor() as q:
        require(read(q,'SELECT GET_LOCK(%s,0) ok',(MARKER,))[0]['ok']==1,'Another repair holds the lock')
        already=guard(q,plan,digest,True)
        if already is not None:
            c.rollback();print('REPLAY_UPDATES 0');return
        when=read(q,'SELECT NOW() now')[0]['now']
        change_rows(q,plan,digest,when)
        require(guard(q,plan,digest)==when,'Postcondition failed')
        require(runtime()==plan['runtime'],'Backend changed during transaction')
    if preview:
        c.rollback()
        private_save('preview.json',{'plan_sha256':digest,'when':when,'rolled_back':True,'mutations':13})
        print('PASS real-target transactional preview; all changes rolled back')
    else:
        require((ROOT/'preview.json').exists(),'Rolled-back target preview required')
        c.commit()
        private_save('receipt.json',{'plan_sha256':digest,'when':when,'mutations':13,'audit_events':13})
        print('COMMITTED_ROWS 13 AUDIT_EVENTS 13')


def verify(c,plan,digest):
    require(runtime()==plan['runtime'],'Backend restarted or artifact changed')
    with c.cursor() as q:
        q.execute('SET TRANSACTION READ ONLY');q.execute('START TRANSACTION WITH CONSISTENT SNAPSHOT')
        when=guard(q,plan,digest)
        require(when is not None,'Not applied')
        rows=read(q,f'SELECT * FROM {SERVICE} WHERE tenant_id=1 AND id IN ({ids(SURVIVORS)}) ORDER BY id')
        require(len(rows)==5 and all(r['status']=='active' and r['media_ready']==b'\1' for r in rows),'Media visibility lost')
        for r in rows:
            require(json.loads(r['service_snapshot'])['productRef']==COURSE_REF,'Course mismatch')
            name=read(q,f'SELECT HEX(student_name_snapshot) h, HEX(JSON_UNQUOTE(JSON_EXTRACT(service_snapshot,\'$.name\'))) course FROM {SERVICE} WHERE tenant_id=1 AND id=%s',(r['id'],))[0]
            require(name['h']==r['student_name_snapshot'].encode().hex().upper(),'Student name encoding mismatch')
            require(name['course']=='大健康轻创网红博主'.encode().hex().upper(),'Course encoding mismatch')
        old=read(q,f'SELECT id FROM {SERVICE} WHERE tenant_id=1 AND id IN ({ids(PAIRS)}) AND teaching_delivery=1 AND deleted=0')
        require(not old,'Duplicate teaching service still visible')
        for old_id,new_id in PAIRS.items():
            order,item,case=SOURCE_ORDERS[old_id]
            linked=read(q,f'SELECT id FROM {SERVICE} WHERE tenant_id=1 AND order_item_id=%s AND deleted=0',(item,))
            require(linked==[{'id':new_id}],'Unique order-item ownership failed')
        tasks=read(q,f"SELECT id,biz_id,status,JSON_EXTRACT(payload,'$.serviceRelationId') payload_id FROM {TASK} WHERE tenant_id=1 AND id IN (15098,15099,15100) ORDER BY id")
        require(all(r['biz_id']==1080 and int(r['payload_id'])==1080 for r in tasks),'Task navigation mismatch')
        summary={'when':when,'plan_sha256':digest,'survivors':list(SURVIVORS),'retired':list(PAIRS),
            'teaching_task_ids':[r['id'] for r in tasks],'media_accounts_unchanged':10,
            'orders_and_nonpatch_rows_unchanged':True,'utf8_hex':True,'backend_unchanged':True,
            'checked_tables':len(plan['before']),'mutations':13,'audit_events':13}
    c.rollback()
    private_save('verification.json',summary)
    print('PASS independent committed readback, exact field scope, media, orders, task routing, UTF-8 and unchanged backend')


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('command',choices=['prepare','fixture','preview','apply','verify'])
    parser.add_argument('--sha256')
    args=parser.parse_args()
    env=os.getenv('ZSJOS_AGENT_ENV')
    if env is None:
        marker=Path('/etc/zsjos/agent-environment')
        env=marker.read_text().strip() if marker.exists() else 'local'
    require(env=='test','Only the explicitly authorized test environment is allowed')
    c=connect('zsjos-mysql-1')
    try:
        with c.cursor() as q:
            q.execute('SET NAMES utf8mb4');q.execute("SET time_zone='+08:00'")
            q.execute('SET SESSION innodb_lock_wait_timeout=5')
            q.execute('SET SESSION TRANSACTION ISOLATION LEVEL REPEATABLE READ')
        if args.command=='prepare':
            prepare(c)
        else:
            plan=load_plan(args.sha256)
            if args.command=='fixture':fixture(c,plan,args.sha256)
            elif args.command=='verify':verify(c,plan,args.sha256)
            else:apply(c,plan,args.sha256,args.command=='preview')
    finally:
        c.rollback();c.close()


if __name__=='__main__':
    main()
