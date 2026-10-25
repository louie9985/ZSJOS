#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Authorized one-student Lead backfill; see student-lead-backfill-20261023.md."""
import argparse
import datetime as dt
from decimal import Decimal
import hashlib
import json
import os
from pathlib import Path
from zoneinfo import ZoneInfo

from transfer_student_20261015 import connect, require, rows

MARKER = 'student-lead-20261023'
PERSON, OWNER, REQUEST = 6028, 400, 4195687
SOURCE = 'education_self_sourced'
GENERATION = SOURCE + '_auto'
REASON = '用户授权定向补录：复用已有学员档案，按原失败提交资料创建教务自拓客资；系统数据修复'
TABLES = {k: 'zsjos_' + v for k, v in dict(
    lead='lead', product='lead_intended_product', assignment='lead_assignment_history',
    follow='lead_follow_up_record', task='business_task', event='business_event',
    performance='performance_attribution', opportunity='opportunity', counter='lead_no_daily_counter').items()}


def one(q, sql, args=()):
    found = rows(q, sql, args)
    require(len(found) == 1, 'Expected exactly one prerequisite row')
    return found[0]


def digest(value):
    return hashlib.sha256(json.dumps(value, sort_keys=True, ensure_ascii=False,
        default=lambda v: v.hex() if isinstance(v, bytes) else str(v)).encode()).hexdigest()


def persist(path, value):
    def encode(item):
        if isinstance(item, Decimal):
            return {'$decimal': str(item)}
        if isinstance(item, (dt.date, dt.datetime)):
            return {'$date': item.isoformat(), '$type': type(item).__name__}
        if isinstance(item, bytes):
            return {'$bytes': item.hex()}
        raise TypeError(type(item).__name__)
    with path.open('x', encoding='utf-8') as handle:
        handle.write(json.dumps(value, ensure_ascii=False, sort_keys=True, indent=2, default=encode))
        handle.flush()
        os.fsync(handle.fileno())


def protected(q, lock=False):
    suffix = ' FOR UPDATE' if lock else ''
    return {t: list(rows(q, f'SELECT * FROM {t} WHERE tenant_id=1 AND {condition} ORDER BY id' + suffix))
            for t, condition in {
                'zsjos_person': f'id={PERSON}', 'zsjos_person_contact_claim': f'person_id={PERSON}',
                'zsjos_order': f'person_id={PERSON}', 'zsjos_service_relation': f'person_id={PERSON}'}.items()}


def context(q):
    person = one(q, 'SELECT * FROM zsjos_person WHERE tenant_id=1 AND id=%s AND deleted=0', (PERSON,))
    require(person['identity_status'] == 'student' and person['in_service_period'] == b'\1', 'Student state changed')
    request = one(q, 'SELECT user_id,result_code,request_url,request_params FROM infra_api_access_log WHERE tenant_id=1 AND id=%s', (REQUEST,))
    require(request['user_id'] == OWNER and request['result_code'] == 1900003002
            and request['request_url'].endswith('/education-self-sourced/create'), 'Submission evidence changed')
    body = json.loads(request['request_params'])['body']
    body = json.loads(body) if isinstance(body, str) else body
    require(body['name'].strip() == person['name'] and body.get('remark', '').strip(), 'Name or remark differs')
    require(not body.get('wechatId') and not body.get('newMediaProviderUserId') and not body.get('attachments'), 'Unexpected submission scope')
    require(body['provinceCode'] == body['cityCode'] == 'OTHER', 'Region scope changed')
    products = body['intendedProducts']
    require(len(products) == 1 and products[0] == dict(spuRef='course_migrated_P100044',
            spuUnknown=False, skuUnknown=True, primary=True), 'Product selection changed')
    user = one(q, 'SELECT id,nickname,dept_id FROM system_users WHERE tenant_id=1 AND id=%s AND status=0 AND deleted=0', (OWNER,))
    dept = one(q, 'SELECT id,name FROM system_dept WHERE tenant_id=1 AND id=%s AND status=0 AND deleted=0', (user['dept_id'],))
    require(not rows(q, "SELECT id FROM zsjos_personnel_state WHERE tenant_id=1 AND system_user_id=%s AND deleted=0 AND business_state<>'enabled'", (OWNER,)), 'Submitter disabled')
    permissions = rows(q, '''SELECT DISTINCT m.permission FROM system_user_role ur
        JOIN system_role r ON r.id=ur.role_id AND r.tenant_id=ur.tenant_id AND r.deleted=0 AND r.status=0
        JOIN system_role_menu rm ON rm.role_id=r.id AND rm.tenant_id=r.tenant_id AND rm.deleted=0
        JOIN system_menu m ON m.id=rm.menu_id AND m.deleted=0 AND m.status=0
        WHERE ur.user_id=%s AND ur.tenant_id=1 AND ur.deleted=0
        AND m.permission IN ('zsjos:lead:education-self-sourced:create','zsjos:lead-follow-up:create','zsjos:lead:qualify')''', (OWNER,))
    require(len(permissions) == 3, 'Required configured permissions missing')
    dictionary = {}
    for key, kind, value in [('channel', 'source_channel', body['sourceChannel']),
                             ('category', 'category', body['leadCategory']),
                             ('stage', 'sales_stage', 'pending_contact'),
                             ('method', 'follow_up_method', 'other'),
                             ('result', 'follow_up_result', 'interested')]:
        dictionary[key] = one(q, 'SELECT value,label FROM system_dict_data WHERE dict_type=%s AND value=%s AND status=0 AND deleted=0',
                              ('zsjos_lead_' + kind, value))
        require(bool(dictionary[key]['label']), 'Dictionary snapshot missing')
    province = one(q, "SELECT id,name FROM system_area WHERE parent_id=1 AND selection_code='OTHER' AND status=0 AND deleted=0")
    city = one(q, "SELECT id,name FROM system_area WHERE parent_id=%s AND selection_code='OTHER' AND status=0 AND deleted=0", (province['id'],))
    product = one(q, 'SELECT id,product_ref,name,category_id FROM zsjos_product WHERE tenant_id=1 AND product_ref=%s AND status=0 AND deleted=0', (products[0]['spuRef'],))
    path, current = [], product['category_id']
    while current:
        require(current not in [n['id'] for n in path], 'Category cycle')
        node = one(q, 'SELECT id,parent_id,name FROM zsjos_product_category WHERE tenant_id=1 AND id=%s AND status=0 AND deleted=0', (current,))
        path.insert(0, dict(id=node['id'], name=node['name']))
        current = node['parent_id']
    require(bool(path), 'Product category absent')
    rule = one(q, "SELECT * FROM zsjos_lead_follow_up_rule WHERE tenant_id=1 AND code='default' AND status=0 AND deleted=0")
    org = list(rows(q, 'SELECT center_id,business_domain FROM zsjos_performance_org WHERE tenant_id=1 AND dept_id=%s AND deleted=0', (user['dept_id'],)))
    require(len(org) <= 1, 'Ambiguous performance organization')
    center = one(q, 'SELECT name FROM system_dept WHERE tenant_id=1 AND id=%s AND deleted=0', (org[0]['center_id'],)) if org else None
    next_at = dt.datetime.fromtimestamp(body['selfSourcedNextFollowUpAt'] / 1000, ZoneInfo('Asia/Shanghai')).replace(tzinfo=None)
    return dict(person=person, body=body, user=user, dept=dept, dictionary=dictionary,
                province=province, city=city, product=product, path=path, rule=rule,
                org=org[0] if org else None, center=center, next_at=next_at)


def write(q, table, values):
    values = dict(values, tenant_id=1, creator=MARKER, updater=MARKER)
    q.execute(f"INSERT INTO {table} ({','.join(values)}) VALUES ({','.join(['%s'] * len(values))})",
              tuple(json.dumps(v, ensure_ascii=False) if isinstance(v, (dict, list)) else v for v in values.values()))
    require(q.rowcount == 1, 'Insert failed')
    return q.lastrowid


def capture(q, tables):
    return {key: list(rows(q, f'SELECT * FROM {table} WHERE tenant_id=1 AND creator=%s ORDER BY id', (MARKER,)))
            for key, table in tables.items() if key != 'counter'}


def verify(q, tables, ctx):
    state = capture(q, tables)
    require({k: len(v) for k, v in state.items()} == dict(lead=1, product=1, assignment=1, follow=1, task=3, event=5, performance=5, opportunity=1),
            'Incomplete or conflicting backfill; refusing repair by overwrite')
    lead, product, assignment, follow, opportunity = (state[k][0] for k in ['lead', 'product', 'assignment', 'follow', 'opportunity'])
    require(lead['person_id'] == PERSON and lead['status'] == 'valid' and lead['assignment_status'] == 'owned'
            and lead['owner_user_id'] == OWNER and lead['source_user_id'] == OWNER and lead['owner_identity'] == 'education'
            and lead['source_type'] == lead['submission_source_type'] == SOURCE and lead['dispatch_mode'] == 'self'
            and lead['submitted_mobile'] == ctx['person']['mobile'] and lead['submitted_name'] == ctx['person']['name']
            and lead['submitted_wechat_id'] is None and lead['follow_up_count'] == 1
            and lead['qualification_round_no'] == 1 and lead['qualified_by_user_id'] == 0
            and lead['next_follow_up_at'] == ctx['next_at'] and lead['remark'] == ctx['body']['remark'].strip()
            and lead['source_provider_user_id'] is None and lead['source_provider_recorded'] == b'\1'
            and lead['submission_idempotency_key'] == ctx['body']['idempotencyKey'], 'Lead differs from approved backfill')
    require(lead['current_assignment_history_id'] == assignment['id'] and lead['last_follow_up_record_id'] == follow['id']
            and assignment['to_owner_user_id'] == OWNER and assignment['operator_user_id'] == 0
            and follow['operator_user_id'] == 0 and follow['first_in_assignment'] == b'\1'
            and follow['assignment_history_id'] == assignment['id'], 'Ownership/follow-up linkage invalid')
    require(opportunity['person_id'] == PERSON and opportunity['owner_user_id'] == OWNER
            and opportunity['type'] == 'initial_conversion' and opportunity['status'] == 'open', 'Opportunity differs')
    require(product['spu_ref'] == ctx['product']['product_ref'] and product['sku_unknown'] == b'\1'
            and product['sku_ref'] is None and product['is_primary'] == b'\1', 'Product differs')
    for key in ['product', 'assignment', 'follow', 'performance', 'opportunity']:
        require(all(r['lead_id'] == lead['id'] and r['deleted'] == b'\0' for r in state[key]), 'Broken Lead linkage')
    require(lead['deleted'] == b'\0', 'Lead removed')
    for task in state['task']:
        require(task['biz_type'] == 'lead' and task['biz_id'] == lead['id'] and task['assignee_id'] == OWNER and task['deleted'] == b'\0', 'Task linkage invalid')
        payload = json.loads(task['payload'])
        if task['task_type'] == 'lead_follow_up_reminder':
            require(task['status'] == 'pending' and task['due_at'] == ctx['next_at'] and payload['followUpRecordId'] == follow['id'], 'Reminder invalid')
        else:
            require(task['status'] == 'completed' and task['completed_at'] == lead['submitted_at']
                    and payload['generationSource'] == GENERATION, 'Automatic task invalid')
    require({r['fact_type'] for r in state['performance']} == {'TASK', 'FOLLOW_UP', 'ASSIGNMENT', 'QUALIFICATION'}, 'Performance coverage invalid')
    for event in state['event']:
        require(event['aggregate_id'] == lead['id'] and event['deleted'] == b'\0', 'Event linkage invalid')
        refs = json.loads(event['related_object_refs'])
        require(refs['repairMarker'] == MARKER and refs['sourceRequestLogId'] == REQUEST, 'Repair provenance absent')
        if event['event_type'] in ('lead_follow_up_recorded', 'lead_qualified_valid'):
            require(refs['generationSource'] == GENERATION and refs['followUpRecordId'] == follow['id'], 'Automatic provenance absent')
    # Validate stored UTF-8 bytes without disclosing the underlying personal text.
    for key, row, columns in [('lead', lead, ['submitted_name', 'remark', 'lead_category_label_snapshot', 'source_channel_label_snapshot']),
                              ('product', product, ['spu_name_snapshot', 'sku_name_snapshot']),
                              ('follow', follow, ['method_label_snapshot', 'result_label_snapshot'])]:
        for column in columns:
            actual = one(q, f'SELECT HEX({column}) h FROM {tables[key]} WHERE tenant_id=1 AND id=%s', (row['id'],))['h']
            require(actual == row[column].encode('utf-8').hex().upper(), 'UTF-8 byte mismatch')
    return state


def apply(q, tables, ctx, fail_after=0):
    existing = capture(q, tables)
    if any(existing.values()):
        verify(q, tables, ctx)
        return 0
    require(not rows(q, f"SELECT id FROM {tables['lead']} WHERE tenant_id=1 AND (person_id=%s OR submission_idempotency_key=%s) FOR UPDATE",
                     (PERSON, ctx['body']['idempotencyKey'])), 'Existing Lead conflict')
    require(not rows(q, f"SELECT id FROM {tables['opportunity']} WHERE tenant_id=1 AND person_id=%s AND deleted=0 FOR UPDATE", (PERSON,)), 'Existing opportunity conflict')
    now = one(q, 'SELECT NOW() n')['n']
    require(ctx['next_at'] > now, 'Follow-up date expired; user input required')
    counter = tables['counter']
    q.execute(f"INSERT IGNORE INTO {counter}(sequence_date,current_value,tenant_id) VALUES(%s,0,1)", (now.date(),))
    q.execute(f'UPDATE {counter} SET current_value=IF(current_value>=9999,1,current_value+1),update_time=NOW() WHERE tenant_id=1 AND sequence_date=%s', (now.date(),))
    seq = one(q, f'SELECT current_value FROM {counter} WHERE tenant_id=1 AND sequence_date=%s FOR UPDATE', (now.date(),))['current_value']
    lead_no = 'KZ' + now.strftime('%Y%m%d%H%M%S') + f'{seq:04d}'
    d, b, p, rule = ctx['dictionary'], ctx['body'], ctx['person'], ctx['rule']
    first_due = now + dt.timedelta(minutes=rule['first_follow_up_timeout_minutes'])
    qual_due = now + dt.timedelta(minutes=rule['qualification_timeout_minutes'])
    count = 0
    def ins(key, **values):
        nonlocal count
        result = write(q, tables[key], dict(values, create_time=now, update_time=now))
        count += 1
        if fail_after == count:
            raise RuntimeError('Injected failure')
        return result
    lead = ins('lead', lead_no=lead_no, person_id=PERSON, submitted_name=p['name'], submitted_mobile=p['mobile'],
        source_type=SOURCE, submission_source_type=SOURCE, source_user_id=OWNER, source_provider_recorded=1,
        source_dept_id=ctx['user']['dept_id'], source_channel_id=d['channel']['value'], source_channel_label_snapshot=d['channel']['label'],
        status='valid', assignment_status='owned', owner_user_id=OWNER, owner_identity='education',
        ownership_started_at=now, current_assignment_first_follow_up_at=now, current_assignment_first_follow_up_deadline_at=first_due,
        last_follow_up_at=now, next_follow_up_at=ctx['next_at'], follow_up_count=1, qualification_round_no=1,
        qualification_started_at=now, qualification_deadline_at=qual_due,
        qualification_rule_snapshot=dict(ruleId=rule['id'], ruleVersion=rule['version'], timeoutMinutes=rule['qualification_timeout_minutes'], startedAt=now.isoformat()),
        qualified_by_user_id=0, qualified_at=now, valid_description=b['remark'].strip(), submitted_at=now, counted_at=now,
        last_activity_at=now, converted_at=now, version=0, province_code=b['provinceCode'], province_name=ctx['province']['name'],
        city_code=b['cityCode'], city_name=ctx['city']['name'], lead_category=d['category']['value'],
        lead_category_label_snapshot=d['category']['label'], remark=b['remark'].strip(), dispatch_mode='self', assignment_attempt_count=0,
        submission_idempotency_key=b['idempotencyKey'], sales_stage=d['stage']['value'], sales_stage_label_snapshot=d['stage']['label'])
    path, prod = ctx['path'], ctx['product']
    ins('product', lead_id=lead, product_ref=prod['product_ref'], product_name_snapshot=prod['name'], is_primary=1, sort=0,
        spu_ref=prod['product_ref'], spu_name_snapshot=prod['name'], sku_name_snapshot='未明确具体班次/方案', spu_unknown=0, sku_unknown=1,
        category_id=path[-1]['id'], category_name_snapshot=path[-1]['name'], category_path_snapshot=path,
        level1_category_id=path[0]['id'], level1_category_name_snapshot=path[0]['name'],
        level2_category_id=path[1]['id'] if len(path)>1 else None, level2_category_name_snapshot=path[1]['name'] if len(path)>1 else None)
    assignment = ins('assignment', lead_id=lead, action_type='accept', to_owner_user_id=OWNER, operator_user_id=0,
        reason=REASON, occurred_at=now, attempt_no=1, candidate_user_id=OWNER, response_at=now, owner_identity_snapshot='education')
    follow = ins('follow', lead_id=lead, assignment_history_id=assignment, operator_user_id=0,
        owner_user_id_snapshot=OWNER, owner_identity_snapshot='education', owner_dept_id_snapshot=ctx['user']['dept_id'],
        method_value=d['method']['value'], method_label_snapshot=d['method']['label'], result_value=d['result']['value'], result_label_snapshot=d['result']['label'],
        category_before=d['category']['value'], category_before_label_snapshot=d['category']['label'],
        category_after=d['category']['value'], category_after_label_snapshot=d['category']['label'],
        sales_stage_before=d['stage']['value'], sales_stage_before_label_snapshot=d['stage']['label'],
        sales_stage_after=d['stage']['value'], sales_stage_after_label_snapshot=d['stage']['label'],
        remark=b['remark'].strip(), next_follow_up_at=ctx['next_at'], occurred_at=now, first_in_assignment=1,
        idempotency_key=f'{GENERATION}:follow:{lead}')
    q.execute(f"UPDATE {tables['lead']} SET current_assignment_history_id=%s,last_follow_up_record_id=%s WHERE id=%s AND tenant_id=1", (assignment, follow, lead))
    summary = [dict(spuRef=prod['product_ref'], spuName=prod['name'], skuRef='', skuName='未明确具体班次/方案', primary=True)]
    opportunity = ins('opportunity', person_id=PERSON, type='initial_conversion', lead_id=lead, status='open',
                      owner_user_id=OWNER, expected_product_summary=summary, version=0)
    def task(kind, title, due, key, payload, completed):
        if completed:
            payload.update(generationSource=GENERATION)
        payload.update(repairMarker=MARKER, sourceRequestLogId=REQUEST)
        return ins('task', task_type=kind, biz_type='lead', biz_id=lead, status='completed' if completed else 'pending',
                   assignee_type='user', assignee_id=OWNER, title_snapshot=title + lead_no, action_code='OPEN_LEAD_FOLLOW_UP',
                   due_at=due, completed_at=now if completed else None, payload=payload, idempotency_key=key, version=0)
    first = task('lead_first_follow_up', '首次跟进：', first_due, f'lead-first-follow-up:{assignment}',
        dict(assignmentHistoryId=assignment, ruleId=rule['id'], ruleVersion=rule['version'], timeoutMinutes=rule['first_follow_up_timeout_minutes'], ownershipStartedAt=now.isoformat()), True)
    qualification = task('lead_qualification', '有效性判定：', qual_due, f'lead-qualification:{lead}:1',
        dict(roundNo=1, ruleId=rule['id'], ruleVersion=rule['version'], timeoutMinutes=rule['qualification_timeout_minutes']), True)
    reminder = task('lead_follow_up_reminder', '跟进提醒：', ctx['next_at'], f'lead-follow-up-reminder:lead:{follow}',
                    dict(followUpRecordScope='lead', followUpRecordId=follow), False)
    for kind, fact in [('ASSIGNMENT', assignment), ('TASK', first), ('QUALIFICATION', qualification), ('FOLLOW_UP', follow), ('TASK', reminder)]:
        # System repair remains the actor. Business attribution is the authorized original submitting owner.
        org = ctx['org']
        ins('performance', fact_type=kind, fact_id=fact, user_id=OWNER, user_name=ctx['user']['nickname'],
            dept_id=ctx['user']['dept_id'], dept_name=ctx['dept']['name'], center_id=org['center_id'] if org else None,
            center_name=ctx['center']['name'] if org else None, business_domain=org['business_domain'] if org else None,
            lead_id=lead, assignment_id=assignment, received_at=now, source_group='self', org_source='frozen',
            channel_code=d['channel']['value'], channel_label=d['channel']['label'],
            outcome='valid' if kind=='QUALIFICATION' else None, completed_at=now if kind=='QUALIFICATION' else None)
    event_ids = []
    def event(kind, key, refs, before=None, after=None):
        refs.update(repairMarker=MARKER, sourceRequestLogId=REQUEST, originalSubmitterUserId=OWNER)
        eid = ins('event', event_type=kind, aggregate_type='lead', aggregate_id=lead, operator_user_id=0,
            from_status=before, to_status=after, reason=REASON, related_object_refs=refs, occurred_at=now, idempotency_key=key)
        event_ids.append(eid)
    event('lead_assignment_accepted', f'lead-ownership:{assignment}', dict(assignmentHistoryId=assignment), 'unassigned', 'owned')
    event('lead_qualification_started', f'lead-qualification-started:{lead}:1', dict(roundNo=1, dueAt=qual_due.isoformat()), 'submitted', 'submitted')
    provenance = dict(roundNo=1, followUpRecordId=follow, assignmentHistoryId=assignment, generationSource=GENERATION)
    event('lead_follow_up_recorded', f'lead_follow_up_recorded:{follow}', dict(provenance))
    event('lead_qualified_valid', f'lead-qualification:{GENERATION}:qualify:{lead}', dict(provenance, opportunityId=opportunity), 'submitted', 'valid')
    def value(code, label=None, identity=None):
        return dict(state='VALUE', code=str(code), text=label or str(code), identityType='ADMIN' if identity else None, identityId=identity)
    changes = []
    for field, label, val in [('status', '客资状态', value('valid', '有效')), ('assignment', '分配状态', value('owned', '已归属')),
                             ('owner', '负责人', value(OWNER, ctx['user']['nickname'], OWNER)), ('ownerIdentity', '负责人身份', value('education', '教务')),
                             ('category', '客资类别', value(d['category']['value'], d['category']['label'])),
                             ('sourceType', '来源类型', value(SOURCE, '教务自拓')), ('sourceChannel', '来源渠道', value(d['channel']['value'], d['channel']['label'])),
                             ('nextFollowUpAt', '下次跟进时间', value(ctx['next_at'].isoformat()))]:
        changes.append(dict(field=field, label=label, before=dict(state='UNRECORDED',code=None,text=None,identityType=None,identityId=None), after=val))
    key = f'lead-flow:{MARKER}'
    event('lead_flow_operation', key, dict(flowRemark=None, flowSnapshot=dict(version=1, operationKey=key,
        title='授权补录教务自拓客资', actorType='SYSTEM', actorId=0, actorName='系统', result='已补录并自动判有效', reason=REASON,
        relatedType='lead', relatedId=lead, relatedNo=lead_no, changes=changes,
        coveredRecords=[f'event:{i}' for i in event_ids] + [f'assignment:{assignment}', f'lead:{lead}'])))
    verify(q, tables, ctx)
    return count


def controlled(conn, q, ctx):
    tables = {k: 'verify_20261023_' + k for k in TABLES}
    for key, name in tables.items():
        q.execute(f'CREATE TEMPORARY TABLE {name} LIKE {TABLES[key]}')
    conn.commit()
    for failure in [1, 6, 12, 17]:
        try:
            apply(q, tables, ctx, fail_after=failure)
        except RuntimeError as error:
            require(str(error) == 'Injected failure', 'Unexpected controlled failure')
            conn.rollback()
        else:
            raise RuntimeError('Failure injection did not fire')
        require(not any(capture(q, tables).values()), 'Failure left rows')
        require(not rows(q, f"SELECT id FROM {tables['counter']}"), 'Failure advanced counter')
        conn.rollback()
    require(apply(q, tables, ctx) == 18, 'Initial insert count differs')
    first = verify(q, tables, ctx)
    conn.commit()
    require(apply(q, tables, ctx) == 0 and capture(q, tables) == first, 'Repeat is not idempotent')
    conn.rollback()
    # Deliberately break only a session-owned fixture, then verify refusal and recovery.
    q.execute(f"DELETE FROM {tables['opportunity']}")
    try:
        apply(q, tables, ctx)
    except RuntimeError as error:
        require('Incomplete' in str(error), 'Unexpected partial-state result')
        conn.rollback()
    else:
        raise RuntimeError('Partial state was silently accepted')
    require(capture(q, tables) == first, 'Fixture recovery failed')
    conn.rollback()
    return dict(initial=True, repeat=True, failuresRolledBack=4, counterRollback=True, partialRefused=True, recovery=True, utf8Hex=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apply', action='store_true')
    parser.add_argument('--evidence-dir', type=Path, required=True)
    args = parser.parse_args()
    environment = os.getenv('ZSJOS_AGENT_ENV')
    if environment is None:
        environment = Path('/etc/zsjos/agent-environment').read_text().strip()
    require(environment == 'test', 'Only the authorized test environment is supported')
    os.umask(0o077)
    args.evidence_dir.mkdir(parents=True, mode=0o700, exist_ok=False)
    conn = connect('zsjos-mysql-1')
    try:
        with conn.cursor() as q:
            q.execute('SET NAMES utf8mb4')
            q.execute("SET SESSION time_zone='+08:00'")
            q.execute('SET SESSION innodb_lock_wait_timeout=5')
            require(one(q, 'SELECT VERSION() v')['v'] == '8.4.11', 'Target engine changed')
            ctx = context(q)
            before = protected(q)
            existing = capture(q, TABLES)
            require(len(before['zsjos_person_contact_claim']) == 1
                    and before['zsjos_person_contact_claim'][0]['contact_value'] == ctx['person']['mobile']
                    and before['zsjos_person_contact_claim'][0]['deleted'] == b'\0', 'Contact claim changed')
            require(len(before['zsjos_order']) == len(before['zsjos_service_relation']) == 1
                    and before['zsjos_order'][0]['status'] == 'effective'
                    and before['zsjos_service_relation'][0]['status'] == 'active', 'Protected business state changed')
            conn.rollback()
            persist(args.evidence_dir / 'protected-before.json', before)
            persist(args.evidence_dir / 'context-private.json', ctx)
            persist(args.evidence_dir / 'backfill-before.json', existing)
            tests = controlled(conn, q, ctx)
            persist(args.evidence_dir / 'controlled.json', tests)
            require(one(q, 'SELECT GET_LOCK(%s,5) acquired', (MARKER,))['acquired'] == 1, 'Repair already running')
            try:
                require(protected(q, lock=True) == before, 'Protected state changed during preflight')
                require(digest(context(q)) == digest(ctx), 'Configuration changed during preflight')
                require(capture(q, TABLES) == existing, 'Another repair changed target')
                if not args.apply:
                    # Exercise the actual execution path in a rolled-back transaction before live commit.
                    inserted = apply(q, TABLES, ctx)
                    verify(q, TABLES, ctx)
                    require(protected(q) == before, 'Protected rows modified')
                    conn.rollback()
                    require(capture(q, TABLES) == existing, 'Preview changed committed data')
                    conn.rollback()
                    persist(args.evidence_dir / 'result.json', dict(applied=False, controlled=tests, rolledBackInsertions=inserted))
                    print('PASS controlled tests and rolled-back live preview; no business rows committed')
                    return
                inserted = apply(q, TABLES, ctx)
                pending = verify(q, TABLES, ctx)
                require(protected(q) == before, 'Protected rows modified')
                persist(args.evidence_dir / 'pending-private.json', pending)
                conn.commit()
                persist(args.evidence_dir / 'committed.json', dict(inserted=inserted, leadNo=pending['lead'][0]['lead_no']))
                require(verify(q, TABLES, ctx) == pending and apply(q, TABLES, ctx) == 0, 'Committed readback/repeat failed')
                require(protected(q) == before, 'Protected rows changed after commit')
                conn.rollback()
                result = dict(applied=True, inserted=inserted, leadNo=pending['lead'][0]['lead_no'],
                              status='valid', assignment='owned', ownerIdentity='education',
                              nextFollowUpAt=str(ctx['next_at']), protectedRowsUnchanged=True, repeatWrites=0, utf8Hex=True, controlled=tests)
                persist(args.evidence_dir / 'result.json', result)
                print(json.dumps(result, ensure_ascii=False))
            finally:
                conn.rollback()
                q.execute('SELECT RELEASE_LOCK(%s)', (MARKER,))
    finally:
        conn.rollback()
        conn.close()


if __name__ == '__main__':
    main()
