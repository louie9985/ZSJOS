#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Second-pass repair under the user's explicit current-owner decision.

Restricted to the original 551 unresolved business numbers. Old strict plans retain
their original rules. No owner/state/history changes; see the operation document.
"""
import argparse
import collections
import os
from pathlib import Path

import backfill_lead_owner_identity_20261020 as base

MARK = 'lead-owner-review-20261020'
SCOPE_SHA = '484339355ffc88127e5f72f1948620c3ace28913231f1516827baab391aabe9d'
ORDER_COLS = 'id,lead_id,tenant_id,deleted,status,order_type,formal_sales_user_id,formal_owner_identity,version'
EVENT_COLS = 'id,aggregate_id,tenant_id,deleted,event_type,from_status,to_status,occurred_at'
OWNED = {'auto_assigned', 'claimed', 'transferred'}


def scope(path):
    plan = base.decode(Path(path).read_text(encoding='utf-8'))
    base.require(base.digest(plan) == SCOPE_SHA, 'Original reviewed scope checksum mismatch')
    selected = [x['before'] for x in plan['items'] if x['result']['decision'] == 'unresolved']
    base.require(len(selected) == 551 and len({r['lead_no'] for r in selected}) == 551, 'Scope must be the original 551 Leads')
    return selected


def dependencies(q, ids, lock=False):
    marks = ','.join(['%s']*len(ids))
    suffix = ' FOR UPDATE' if lock else ''
    return {
        'orders': base.rows(q, f'SELECT {ORDER_COLS} FROM zsjos_order WHERE tenant_id=1 '
            f'AND lead_id IN ({marks}) ORDER BY id'+suffix, ids),
        'events': base.rows(q, f'SELECT {EVENT_COLS} FROM zsjos_business_event WHERE tenant_id=1 '
            f"AND aggregate_type='lead' AND aggregate_id IN ({marks}) ORDER BY id"+suffix, ids)}


def evidence(q, ids):
    result = base.source_data(q)
    result['old_states'] = base.rows(q, 'SELECT id,lead_status,public_pool_released_at '
                                     'FROM oldcrm_scratch.leads_lead ORDER BY id')
    result['status_events'] = base.rows(q, 'SELECT id,lead_id,event_type,from_status,to_status,'
        'from_assignment_status,to_assignment_status,operator_id,created_at '
        'FROM oldcrm_scratch.leads_status_event ORDER BY created_at,id')
    result['actors'] = base.rows(q, 'SELECT id,actor_id FROM oldcrm_scratch.employees_employee_profile ORDER BY id')
    result.update(dependencies(q, ids))
    return result


def index(data):
    result = base.indexed(data)
    result['old_states'] = {r['id']: r for r in data['old_states']}
    result['academic_rows'] = collections.defaultdict(list)
    result['status_events'] = collections.defaultdict(list)
    result['orders'] = collections.defaultdict(list)
    result['events'] = collections.defaultdict(list)
    result['actors'] = {r['id']: r['actor_id'] for r in data['actors']}
    for r in data['academic']: result['academic_rows'][r['lead_id']].append(r)
    for r in data['status_events']: result['status_events'][r['lead_id']].append(r)
    for r in data['orders']: result['orders'][r['lead_id']].append(r)
    for r in data['events']: result['events'][r['aggregate_id']].append(r)
    return result


def classify(lead, history, source):
    def reject(why): return {'decision': 'unresolved', 'reason': why}
    if not base.active(lead) or lead['tenant_id'] != 1 or lead['assignment_status'] != 'owned' \
            or lead['owner_user_id'] is None or lead['owner_identity'] is not None or lead['version'] is None:
        return reject('outside_review_scope')
    h = [r for r in history if base.active(r)]
    if any(r['tenant_id'] != 1 or r['lead_id'] != lead['id'] for r in h):
        return reject('history_scope_conflict')
    # Reviewed import gaps do not permit discarding a new real ownership command.
    if any(not (r['creator'] or '').startswith('legacy-') for r in h):
        return reject('nonlegacy_history_requires_review')
    if any(r['owner_identity_snapshot'] not in (None, 'sales') for r in h):
        return reject('conflicting_recorded_identity')
    old = source['leads'].get(lead['lead_no'], [])
    if len(old) != 1: return reject('missing_unique_source')
    old = old[0]
    if source['mapping'].get(old['assigned_sales_id']) != lead['owner_user_id']:
        return reject('actual_final_owner_conflict')
    state = source['old_states'][old['id']]
    rawlogs = sorted((r for r in source['logs'].values() if r['lead_id']==old['id']),
                     key=lambda r:(r['created_at'],r['id']))
    status_events = source['status_events'][old['id']]
    proof = {'legacy_lead_id':old['id'], 'current_owner_confirmed':True}
    if old['assignment_status'] == 'recycled':
        recycles = [r for r in status_events if r['event_type']=='lead_recycled']
        releases = [r for r in status_events if r['event_type']=='lead_released_public_pool'
                    and r['to_status']=='valid']
        if state['lead_status'] != 'valid' or not state['public_pool_released_at'] or not recycles or not releases \
                or releases[-1]['created_at'] <= recycles[-1]['created_at']:
            return reject('unreleased_actual_recycle')
        if rawlogs and rawlogs[-1]['created_at'] > releases[-1]['created_at']:
            return reject('assignment_after_public_sea_release')
        proof['public_sea_release_event_id'] = releases[-1]['id']
    elif old['assignment_status'] not in OWNED:
        return reject('source_not_owned')
    elif rawlogs and (rawlogs[-1]['assignment_status_after'] not in OWNED
                     or source['mapping'].get(rawlogs[-1]['to_sales_id_snapshot']) != lead['owner_user_id']):
        return reject('latest_raw_assignment_conflict')
    current_events = sorted((r for r in source['events'][lead['id']] if base.active(r)),
                            key=lambda r:(r['occurred_at'],r['id']))
    if any(r['event_type'] in ('lead_recycled','lead_transferred','lead_released_to_claim_pool')
           for r in current_events):
        return reject('current_runtime_ownership_command_requires_review')
    orders = [r for r in source['orders'][lead['id']] if base.active(r)
              and r['status']=='effective' and r['order_type']=='first_purchase']
    if any(r['formal_sales_user_id']==lead['owner_user_id']
           and r['formal_owner_identity'] not in (None,'sales') for r in orders):
        return reject('effective_order_identity_conflict')
    if old['id'] in source['academic']:
        if any(a['owner_id']==old['assigned_sales_id']
               or source['mapping'].get(a['owner_id'])==lead['owner_user_id']
               for a in source['academic_rows'][old['id']]):
            return reject('current_owner_has_academic_identity')
        matching = [r for r in orders if r['formal_sales_user_id']==lead['owner_user_id']
                    and r['formal_owner_identity']=='sales']
        if lead['status']!='won' or len(matching)!=1 or len(orders)!=1:
            return reject('academic_overlap_without_current_sales_order')
        proof['effective_sales_order_id'] = matching[0]['id']
    receipts = [r for r in source['receipts'][old['id']]
                if r['sales_id']==old['assigned_sales_id'] and r['event_type'] in
                {'assign_accept','pool_claim','supervisor_transfer','sales_manual_entry','valid_transfer','admin_assign'}]
    logs = [r for r in rawlogs if r['to_sales_id_snapshot']==old['assigned_sales_id']
            and r['assigned_sales_id']==old['assigned_sales_id'] and r['assignment_status_after'] in OWNED]
    self_events = [r for r in status_events if r['event_type']=='sales_self_submit'
                   and r['operator_id']==source['actors'].get(old['assigned_sales_id'])]
    if receipts:
        proof['legacy_receipt_id'] = max(receipts,key=lambda r:(r['occurred_at'],r['id']))['id']
        reason = 'reviewed_current_owner_sales_receipt'
    elif logs:
        proof['legacy_log_id'] = logs[-1]['id']
        reason = 'reviewed_current_owner_raw_sales_assignment'
    elif self_events:
        if not any(r['formal_sales_user_id']==lead['owner_user_id']
                   and r['formal_owner_identity']=='sales' for r in orders):
            return reject('self_submission_without_effective_sales_order')
        proof['sales_self_event_id'] = self_events[-1]['id']
        reason = 'reviewed_current_owner_sales_self_order'
    else:
        return reject('no_explicit_sales_business_evidence')
    return dict(proof,decision='confirmed',identity='sales',reason=reason)


def prepare(q, selected):
    ids = [r['id'] for r in selected]
    data = evidence(q,ids); source = index(data)
    now = base.rows(q,'SELECT NOW() now')[0]['now']
    items = []
    for record in selected:
        lead = base.rows(q,'SELECT * FROM zsjos_lead WHERE tenant_id=1 AND id=%s',(record['id'],))[0]
        base.require(lead['lead_no']==record['lead_no'], 'Business number changed')
        history = base.histories(q,lead['id']); result = classify(lead,history,source)
        before = {f:lead[f] for f in base.FIELDS}
        item = {'before':before,'result':result,'protected_sha256':base.protected(lead),
                'history_sha256':base.digest(history),
                'dependencies_sha256':base.digest({'orders':source['orders'][lead['id']],
                                                  'events':source['events'][lead['id']]})}
        if result['decision']=='confirmed':
            item['after']=dict(before,owner_identity='sales',version=lead['version']+1,updater=MARK,update_time=now)
        items.append(item)
    return {'marker':MARK,'scope_sha256':SCOPE_SHA,'tenant':1,'environment':'test',
            'server_uuid':base.rows(q,'SELECT @@server_uuid uuid')[0]['uuid'],
            'prepared_at':now,'source_sha256':base.digest(data),'items':items}


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('mode',choices=['prepare','verify-copy','apply','verify'])
    parser.add_argument('--scope',required=True,type=Path)
    parser.add_argument('--plan',required=True,type=Path)
    parser.add_argument('--sha256')
    parser.add_argument('--report',type=Path)
    args=parser.parse_args();os.umask(0o077)
    selected=scope(args.scope);ids=[r['id'] for r in selected]
    c=base.connect('zsjos-mysql-1')
    try:
        with c.cursor() as q:
            q.execute('SET NAMES utf8mb4');q.execute("SET time_zone='+08:00'")
            q.execute('SET SESSION innodb_lock_wait_timeout=5');base.validate_target(q)
            if args.mode=='prepare':
                q.execute('START TRANSACTION READ ONLY');plan=prepare(q,selected);c.rollback()
                args.plan.parent.mkdir(parents=True,mode=0o700,exist_ok=True);base.save(args.plan,plan)
                print(base.encode(dict(base.summary(plan),sha256=base.digest(plan))));return
            plan=base.decode(args.plan.read_text(encoding='utf-8'))
            base.require(base.digest(plan)==args.sha256 and plan['scope_sha256']==SCOPE_SHA
                and plan['marker']==MARK and plan['tenant']==1 and plan['environment']=='test','Invalid reviewed plan')
            base.require({i['before']['id'] for i in plan['items']}==set(ids),'Plan expanded scope')
            base.require(base.rows(q,'SELECT @@server_uuid uuid')[0]['uuid']==plan['server_uuid'],'Server changed')
            q.execute('START TRANSACTION READ ONLY');data=evidence(q,ids)
            base.require(base.digest(data)==plan['source_sha256'],'Reviewed evidence changed')
            source=index(data);c.rollback()
            items=[i for i in plan['items'] if i['result']['decision']=='confirmed']
            if args.mode=='verify-copy':
                q.execute('CREATE TEMPORARY TABLE verify_identity_lead LIKE zsjos_lead')
                q.execute('CREATE TEMPORARY TABLE verify_identity_history LIKE zsjos_lead_assignment_history')
                cols=base.rows(q,"SELECT column_name FROM information_schema.columns WHERE table_schema=DATABASE() "
                    "AND table_name='zsjos_lead' AND generation_expression='' ORDER BY ordinal_position")
                names=','.join('`'+r['COLUMN_NAME']+'`' for r in cols);marks=','.join(['%s']*len(ids))
                q.execute(f'INSERT INTO verify_identity_lead ({names}) SELECT {names} FROM zsjos_lead '
                          f'WHERE tenant_id=1 AND id IN ({marks})',ids)
                q.execute('INSERT INTO verify_identity_history SELECT h.* FROM zsjos_lead_assignment_history h '
                          'JOIN verify_identity_lead l ON l.id=h.lead_id AND l.tenant_id=h.tenant_id');c.commit()
                for item in items:
                    base.require(base.apply_one(q,item,source,'verify_identity_lead','verify_identity_history',
                                                classifier=classify)==1,'Initial copy update failed')
                c.rollback()
                for item in items:
                    base.require(base.apply_one(q,item,source,'verify_identity_lead','verify_identity_history',
                                                classifier=classify)==1,'Rollback recovery failed')
                    c.commit()
                for item in plan['items']:
                    actual=base.rows(q,'SELECT * FROM verify_identity_lead WHERE id=%s',(item['before']['id'],))[0]
                    base.require({f:actual[f] for f in base.FIELDS}==item.get('after',item['before'])
                        and base.protected(actual)==item['protected_sha256'],'Copied row mismatch')
                    base.require(base.digest(base.histories(q,actual['id'],'verify_identity_history'))
                                 ==item['history_sha256'],'Copied history changed')
                for item in items:
                    base.require(base.apply_one(q,item,source,'verify_identity_lead','verify_identity_history',
                                                classifier=classify)==0,'Repeat changed data')
                c.rollback();print(base.encode({'initial_and_rollback_recovery':len(items),'repeat_updates':0,
                                               'protected_rows':len(plan['items'])}));return
            report=[]
            base.require(args.report is not None and not args.report.exists(),'New report path required')
            if args.mode=='apply':
                for item in items:
                    def checked(lead,history,src):
                        dep=dependencies(q,[lead['id']],lock=True)
                        base.require(base.digest(dep)==item['dependencies_sha256'],'Current order/event evidence changed')
                        return classify(lead,history,src)
                    try:
                        c.begin();changed=base.apply_one(q,item,source,classifier=checked);c.commit()
                        c.begin();base.require(base.apply_one(q,item,source,classifier=checked)==0,'Readback mismatch');c.rollback()
                        report.append({'lead_no':item['before']['lead_no'],'state':'verified','changed':changed})
                    except Exception as exc:
                        c.rollback();report.append({'lead_no':item['before']['lead_no'],'state':'refused','reason':str(exc)})
            else:
                q.execute('START TRANSACTION READ ONLY')
                for item in plan['items']:
                    a=base.rows(q,'SELECT * FROM zsjos_lead WHERE tenant_id=1 AND id=%s',(item['before']['id'],))[0]
                    ok={f:a[f] for f in base.FIELDS}==item.get('after',item['before'])
                    ok=ok and base.digest(base.histories(q,a['id']))==item['history_sha256']
                    report.append({'lead_no':a['lead_no'],'state':'verified' if ok else 'changed_or_unapplied',
                                   'protected_unchanged':base.protected(a)==item['protected_sha256']})
                c.rollback()
            base.save(args.report,report)
            print(base.encode({'states':dict(collections.Counter(r['state'] for r in report)),
                               'changed':sum(r.get('changed',0) for r in report)}))
            base.require(all(r['state']=='verified' for r in report),'Review report contains refusals/mismatches')
    finally:c.rollback();c.close()


if __name__=='__main__':main()
