#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Authorized, fixed-scope test repair; see registration-mode-20261029.md."""
import argparse
import os
from pathlib import Path
import sys

# Reuse the established UTF-8 connection and private, lossless evidence serialization.
from close_legacy_registration_20261028 import connect, rows, require, save, read

MARKER = 'registration-mode-20261029'
IDS = (798,800,801,803,804,805,811,813,817,818,824,826,828,829,830,831,
       832,833,834,835,836,837,838,839,847,855,858,859,861,862,863,864,
       868,869,870,1127)
ID_SQL = ','.join(map(str, IDS))
CASE = 'zsjos_registration_case'
ITEM = 'zsjos_registration_case_checklist_item'
ROUTE = 'zsjos_registration_case_route'
MUTABLE = (CASE, ITEM, ROUTE)
DEPENDENTS = ('zsjos_service_relation','zsjos_student_info_form','zsjos_registration_command',
              'zsjos_registration_class_assignment','zsjos_registration_item_attachment',
              'zsjos_registration_item')
ZERO, ONE = b'\x00', b'\x01'


def mutable(q, prefix=''):
    return {t: rows(q, f'SELECT * FROM {prefix}{t} ORDER BY id') for t in MUTABLE}


def capture(q, lock=False):
    suffix = ' FOR UPDATE' if lock else ''
    parents = rows(q, f'SELECT * FROM zsjos_order WHERE tenant_id=1 AND id IN '
                   f'(SELECT order_id FROM {CASE} WHERE tenant_id=1 AND id IN ({ID_SQL})) ORDER BY id' + suffix)
    rows(q, f'SELECT * FROM {CASE} WHERE tenant_id=1 AND id IN ({ID_SQL}) ORDER BY id' + suffix)
    state = {'mutable': mutable(q), 'orders': parents}
    order_ids = ','.join(str(r['id']) for r in parents)
    require(bool(order_ids), 'Missing parent orders')
    for t in ('zsjos_order_item','zsjos_order_approval_round'):
        state[t] = rows(q, f'SELECT * FROM {t} WHERE tenant_id=1 AND order_id IN ({order_ids}) ORDER BY id')
    for t in DEPENDENTS:
        state[t] = rows(q, f'SELECT * FROM {t} WHERE tenant_id=1 AND registration_case_id IN ({ID_SQL}) ORDER BY id' + suffix)
    state['tasks'] = rows(q, "SELECT * FROM zsjos_business_task WHERE tenant_id=1 "
                         f"AND biz_type IN ('registration_case','registration-case') AND biz_id IN ({ID_SQL}) ORDER BY id" + suffix)
    for t in ('zsjos_registration_checklist_template','zsjos_registration_checklist_version',
              'zsjos_registration_checklist_template_item','zsjos_registration_route_option'):
        state[t] = rows(q, f'SELECT * FROM {t} WHERE tenant_id=1 AND deleted=0 ORDER BY id' + suffix)
    return state


def source(state):
    templates = state['zsjos_registration_checklist_template']
    require(len(templates)==1 and templates[0]['published_version_id']==4, 'Published template changed')
    version = next((r for r in state['zsjos_registration_checklist_version'] if r['id']==4), None)
    require(version is not None and version['status']=='published' and version['template_id']==templates[0]['id'],
            'Published version invalid')
    items = [r for r in state['zsjos_registration_checklist_template_item'] if r['version_id']==4 and r['enabled']==ONE]
    routes = [r for r in state['zsjos_registration_route_option'] if r['version_id']==4 and r['enabled']==ONE]
    require(len(items)==6 and len(routes)==1, 'Published definition count changed')
    require(sum(r['item_type']=='study_planner' for r in items)==1, 'Planner definition missing')
    return items, routes


def validate(state):
    source(state)
    selected = [r for r in state['mutable'][CASE] if r['id'] in IDS and r['tenant_id']==1]
    require(len(selected)==36, 'Target membership changed')
    parents = {r['id']:r for r in state['orders']}
    products = {r['id']:r for r in state['zsjos_order_item']}
    for r in selected:
        require(r['deleted']==ZERO and r['status']=='pending' and r['assignment_mode']=='auto'
                and r['creator'] in ('legacy-migration','legacy-increment-20260921') and r['updater']=='V317'
                and r['checklist_version_id'] is None and r['study_planner_user_id'] is None
                and r['registration_approved_at'] is None and r['cancelled_at'] is None and r['cancel_reason'] is None,
                'Target prerequisites changed')
        o, i = parents.get(r['order_id']), products.get(r['order_item_id'])
        require(o is not None and o['status']=='effective' and o['deleted']==ZERO and o['person_id'] is not None
                and i is not None and i['deleted']==ZERO and i['order_id']==o['id'], 'Order/item prerequisite changed')
    for t in (ITEM, ROUTE):
        require(not any(r['tenant_id']==1 and r['registration_case_id'] in IDS for r in state['mutable'][t]),
                'Existing target snapshots require review')
    for t in (*DEPENDENTS,'tasks'):
        require(not any(r['deleted']==ZERO for r in state[t]), 'Active dependent requires review: '+t)


def expected_cases(before, now):
    return [dict(r, assignment_mode='class_per_item', checklist_version_id=4,
                 completed_at=None, completed_by_user_id=None, version=r['version']+1,
                 updater=MARKER, update_time=now) if r['tenant_id']==1 and r['id'] in IDS else dict(r)
            for r in before]


def snapshots(state, now):
    items, routes = source(state)
    common = dict(version=0, creator=MARKER, updater=MARKER, create_time=now, update_time=now,
                  deleted=ZERO, tenant_id=1)
    result = {ITEM:[], ROUTE:[]}
    for cid in IDS:
        for d in items:
            result[ITEM].append(dict(common, registration_case_id=cid, template_item_id=d['id'],
                item_key=d['item_key'],item_type=d['item_type'],title_snapshot=d['title'],sort=d['sort'],
                attachment_required=d['attachment_required'],checked=ZERO,checked_by_user_id=None,checked_at=None))
        for d in routes:
            result[ROUTE].append(dict(common,registration_case_id=cid,route_option_id=d['id'],
                option_key=d['option_key'],department_id=d['department_id'],
                department_name_snapshot=d['department_name_snapshot'],assignee_type=d['assignee_type'],
                selected=ZERO,assignee_user_id=None,assignee_name_snapshot=None,sort=d['sort']))
    return result


def verify_mutation(state, current, now):
    require(current[CASE]==expected_cases(state['mutable'][CASE],now), 'Case field whitelist differs')
    expected = snapshots(state, now)
    for t in (ITEM,ROUTE):
        original_ids = {r['id'] for r in state['mutable'][t]}
        require([r for r in current[t] if r['id'] in original_ids]==state['mutable'][t], 'Unrelated snapshots changed')
        added = [{k:v for k,v in r.items() if k!='id'} for r in current[t] if r['id'] not in original_ids]
        require(added==expected[t], 'New snapshot fields differ from published configuration')


def apply(q, state, now, prefix='', fail=None):
    current = mutable(q, prefix)
    if current!=state['mutable']:
        verify_mutation(state,current,now)
        return 0
    for index,r in enumerate(state['mutable'][CASE]):
        if r['tenant_id']!=1 or r['id'] not in IDS: continue
        q.execute(f"UPDATE {prefix}{CASE} SET assignment_mode='class_per_item',checklist_version_id=4,"
                  'completed_at=NULL,completed_by_user_id=NULL,version=version+1,updater=%s,update_time=%s '
                  "WHERE id=%s AND tenant_id=1 AND deleted=0 AND status='pending' AND assignment_mode='auto' AND version=%s",
                  (MARKER,now,r['id'],r['version']))
        require(q.rowcount==1, 'Concurrent case update')
        if fail=='first-case': raise RuntimeError('Injected failure')
    if fail=='cases': raise RuntimeError('Injected failure')
    for t, additions in snapshots(state,now).items():
        for r in additions:
            columns=','.join(r); marks=','.join(['%s']*len(r))
            q.execute(f'INSERT INTO {prefix}{t} ({columns}) VALUES ({marks})',tuple(r.values()))
            if fail=='first-'+t: raise RuntimeError('Injected failure')
    verify_mutation(state,mutable(q,prefix),now)
    if fail=='final': raise RuntimeError('Injected failure')
    return 36


def fixtures(db,state,now):
    prefix='tmp_mode_'
    with db.cursor() as q:
        for t in MUTABLE:
            q.execute(f'CREATE TEMPORARY TABLE {prefix}{t} LIKE {t}')
            q.execute(f'INSERT INTO {prefix}{t} SELECT * FROM {t}')
    db.commit()
    with db.cursor() as q:
        require(mutable(q,prefix)==state['mutable'],'Fixture inputs changed')
        for phase in ('first-case','cases','first-'+ITEM,'first-'+ROUTE,'final'):
            try:
                apply(q,state,now,prefix,phase)
                raise RuntimeError('Injection did not run')
            except RuntimeError as error:
                require(str(error)=='Injected failure','Unexpected fixture failure')
                db.rollback()
            require(mutable(q,prefix)==state['mutable'],'Fixture rollback failed')
        require(apply(q,state,now,prefix)==36,'First execution failed')
        require(apply(q,state,now,prefix)==0,'Repeat execution failed')
        db.commit()
        q.execute(f'UPDATE {prefix}{ITEM} SET checked=1 WHERE registration_case_id=835 AND tenant_id=1')
        try:
            apply(q,state,now,prefix)
            raise RuntimeError('Post-edit rejection did not run')
        except RuntimeError as error:
            require(str(error)=='New snapshot fields differ from published configuration','Unexpected post-edit failure')
        db.rollback()
        verify_mutation(state,mutable(q,prefix),now)
    return dict(first=36,repeat=0,failure_rollback_phases=5,recovery=True,post_edit_rejected=True)


def verify_state(before,after,now):
    require({k:v for k,v in before.items() if k!='mutable'}=={k:v for k,v in after.items() if k!='mutable'},
            'Protected parent/configuration/dependent data changed')
    verify_mutation(before,after['mutable'],now)


def verify_hex(q):
    for table,column in ((ITEM,'title_snapshot'),(ROUTE,'department_name_snapshot')):
        result=rows(q,f'SELECT {column} value,HEX({column}) hex_value FROM {table} '
                      f'WHERE tenant_id=1 AND registration_case_id IN ({ID_SQL}) AND deleted=0')
        require(len(result)==(216 if table==ITEM else 36),'Snapshot count differs')
        require(all(r['value'].encode('utf-8').hex().upper()==r['hex_value'] for r in result),'UTF-8 HEX differs')


def main():
    p=argparse.ArgumentParser();p.add_argument('--evidence-dir',type=Path,required=True);p.add_argument('--apply',action='store_true')
    args=p.parse_args()
    ep=Path('/etc/zsjos/agent-environment')
    require(os.environ.get('ZSJOS_AGENT_ENV',ep.read_text().strip() if ep.exists() else 'local')=='test',
            'Only authorized test environment allowed')
    os.umask(0o077);args.evidence_dir.mkdir(parents=True,exist_ok=True,mode=0o700)
    db=connect()
    try:
        with db.cursor() as q:
            state=capture(q)
            if (args.evidence_dir/'pending-after.json').exists():
                require(state==read(args.evidence_dir/'pending-after.json'),'Evidence differs; manual recovery review required')
                verify_hex(q)
                print('Exact committed post-image verified; zero writes');return
            require(not (args.evidence_dir/'before.json').exists(),'Existing before-image requires recovery review')
            validate(state);now=rows(q,'SELECT NOW() now')[0]['now']
        db.rollback()
        result=fixtures(db,state,now)
        save(args.evidence_dir/'fixtures.json',result)
        db.rollback()
        if not args.apply:
            print('PASS: 36 targets; 216 checklist snapshots; 36 unselected routes; MySQL rollback/repeat/recovery guards; no business writes');return
        with db.cursor() as q:
            before=capture(q,True);validate(before)
            require(before==state,'Prerequisites changed during fixtures')
            now=rows(q,'SELECT NOW() now')[0]['now']
            save(args.evidence_dir/'before.json',before)
            save(args.evidence_dir/'intent.json',dict(ids=IDS,time=now,template_version=4,marker=MARKER))
            require(apply(q,before,now)==36,'Live update count differs')
            after=capture(q);verify_state(before,after,now);verify_hex(q)
            save(args.evidence_dir/'pending-after.json',after)
        db.commit()
        check=connect()
        try:
            with check.cursor() as q:
                persisted=capture(q)
                require(persisted==after,'Independent post-image differs; inspect committed state')
                verify_state(before,persisted,now);verify_hex(q)
            save(args.evidence_dir/'verified.json',dict(time=now,cases=36,checklist_items=216,routes=36,
                 independent_readback=True,utf8_hex=True,protected_records_unchanged=True,fixtures=result))
        finally:
            check.rollback();check.close()
        print('COMMITTED: 36 class_per_item tasks; 216 unchecked items; 36 unselected routes; independent readback and UTF-8 verified')
    finally:
        db.rollback();db.close()


if __name__=='__main__':
    try: main()
    except Exception as error:
        print('Migration stopped: '+(str(error) if type(error) is RuntimeError else type(error).__name__),file=sys.stderr)
        raise SystemExit(1)
