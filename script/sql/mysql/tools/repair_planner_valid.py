#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Authorized tenant-1 operational repair. See ../repairs/planner-valid-repair.md.

Uses existing PyMySQL; prepare freezes private before-images. Apply rehearses the
same SQL with rollback before committing. No notifications or schema changes.
"""
import argparse
import collections
import datetime as dt
import hashlib
import json
import os
from pathlib import Path
import pickle
import subprocess

import pymysql

MARK = 'planner-valid-20260923'
REASON = '经确认批量修复：学习规划师负责且已完成首跟的客资统一判定有效'
PREFIX = 'zsjos_'
TABLES = ['lead', 'opportunity', 'business_task', 'performance_attribution',
          'business_event', 'business_audit_log', 'order']
DEPS = ['lead_follow_up_record', 'lead_intended_product', 'lead_appeal']


def connect():
    info = json.loads(subprocess.check_output(['docker', 'inspect', 'zsjos-mysql-1']))[0]
    env = dict(x.split('=', 1) for x in info['Config']['Env'] if '=' in x)
    assert env['MYSQL_DATABASE'] == 'zsjos'
    password = subprocess.check_output(['docker', 'exec', 'zsjos-mysql-1', 'cat',
                                       '/run/secrets/mysql-root-password']).decode().strip()
    c = pymysql.connect(host='127.0.0.1', port=int(info['NetworkSettings']['Ports']['3306/tcp'][0]['HostPort']),
                        user='root', password=password, database=env['MYSQL_DATABASE'], charset='utf8mb4',
                        autocommit=False, cursorclass=pymysql.cursors.DictCursor)
    run(c, "SET NAMES utf8mb4")
    run(c, "SET time_zone='+08:00'")
    run(c, 'SET SESSION innodb_lock_wait_timeout=10')
    return c


def query(c, sql, args=()):
    with c.cursor() as cur:
        cur.execute(sql, args)
        return cur.fetchall()


def run(c, sql, args=()):
    with c.cursor() as cur:
        cur.execute(sql, args)
        return cur.rowcount


def insert(c, table, data):
    data = dict(data, tenant_id=1, creator=MARK, updater=MARK)
    with c.cursor() as cur:
        cur.execute('INSERT INTO '+PREFIX+table+' ('+','.join('`'+k+'`' for k in data)+
                    ') VALUES ('+','.join(['%s']*len(data))+')', tuple(data.values()))
        return cur.lastrowid


def update(c, table, old, values):
    values = dict(values, updater=MARK)
    assert run(c, 'UPDATE '+PREFIX+table+' SET '+','.join('`'+k+'`=%s' for k in values)+
               ' WHERE tenant_id=1 AND id=%s', (*values.values(), old['id'])) == 1


def allrows(c, table):
    if table == 'business_audit_log':
        return query(c, 'SELECT * FROM zsjos_business_audit_log WHERE tenant_id=1 AND execution_key LIKE %s ORDER BY id', (MARK+':%',))
    return query(c, 'SELECT * FROM '+PREFIX+table+' WHERE tenant_id=1 ORDER BY id')


def snapshot(c):
    return {t: allrows(c, t) for t in TABLES+DEPS}


def scoped_images(state, b):
    ids={l['id'] for l in b['leads']}
    people={l['person_id'] for l in b['leads']}
    result={}
    for table, data in state.items():
        if table=='lead':
            result[table]=[r for r in data if r['id'] in ids]
        elif table=='opportunity':
            result[table]=[r for r in data if r['lead_id'] in ids or r['person_id'] in people]
        elif table=='business_audit_log':
            result[table]=data
        else:
            key={'business_task':'biz_id','business_event':'aggregate_id'}.get(table,'lead_id')
            result[table]=[r for r in data if r[key] in ids]
    return result


def discover(c):
    return query(c, """SELECT l.* FROM zsjos_lead l JOIN system_users u
      ON u.id=l.owner_user_id AND u.tenant_id=l.tenant_id
      WHERE l.tenant_id=1 AND l.deleted=0 AND u.deleted=0 AND u.status=0
      AND JSON_CONTAINS(COALESCE(u.post_ids,'[]'),'2012')
      AND l.assignment_status='owned' AND l.current_assignment_first_follow_up_at IS NOT NULL
      AND l.status IN ('submitted','invalid') ORDER BY l.id""")


def live(row):
    return row['deleted'] == b'\0'


def one(items):
    assert len(items) == 1, 'Missing or ambiguous related row'
    return items[0]


def preflight(c, b):
    ids = [l['id'] for l in b['leads']]
    locked = query(c, 'SELECT * FROM zsjos_lead WHERE tenant_id=1 AND id IN ('+
                   ','.join(['%s']*len(ids))+') ORDER BY id FOR UPDATE', ids)
    assert locked == b['leads'], 'Lead changed since frozen backup'
    assert discover(c) == b['leads'], 'Candidate scope changed'
    for t in TABLES[1:]:
        key = {'opportunity':'lead_id','business_task':'biz_id','performance_attribution':'lead_id',
               'business_event':'aggregate_id','business_audit_log':None,'order':'lead_id'}[t]
        if key:
            query(c, 'SELECT id FROM '+PREFIX+t+' WHERE tenant_id=1 AND '+key+' IN ('+
                  ','.join(['%s']*len(ids))+') ORDER BY id FOR UPDATE', ids)
    current = snapshot(c)
    # Exact before-images also detect scheduler/business changes before this execution.
    assert scoped_images(current,b) == scoped_images(b['before'],b), 'Target/associations changed since backup; prepare a new reviewed backup'
    assert collections.Counter(l['status'] for l in locked) == {'submitted':63, 'invalid':1}
    assert one([l for l in locked if l['status']=='invalid'])['lead_no']=='LD202609200016'
    assert len({l['owner_user_id'] for l in locked}) <= 5
    for l in locked:
        assert l['provider_owner_type'] in (None, 'system_user'), 'Cashback eligibility needs review'
        assert l['qualification_round_no'] > 0 and l['qualification_deadline_at']
        assert any(live(f) and f['lead_id']==l['id'] and f['assignment_history_id']==l['current_assignment_history_id']
                   and f['operator_user_id']==l['owner_user_id'] for f in current['lead_follow_up_record'])
        assert not any(live(x) and x['lead_id']==l['id'] for x in current['lead_appeal']), 'Appeal conflict'
        orders = [x for x in current['order'] if live(x) and x['lead_id']==l['id']]
        if orders:
            o = one(orders)
            assert l['lead_no']=='LD202609150049' and o['status']=='effective' and o['order_type']=='first_purchase'
            assert o['opportunity_id'] is None and o['effective_at']
            assert o['person_id']==l['person_id'] and o['formal_sales_user_id']==l['owner_user_id']
        opportunities = [o for o in current['opportunity'] if live(o) and (o['person_id']==l['person_id'] or o['lead_id']==l['id'])]
        if l['status']=='submitted':
            assert not opportunities
        else:
            o = one(opportunities)
            assert o['lead_id']==l['id'] and o['type']=='initial_conversion' and o['status']=='lost'
        t = one([t for t in current['business_task'] if live(t) and t['idempotency_key']==f"lead-qualification:{l['id']}:{l['qualification_round_no']}"])
        assert t['biz_id']==l['id'] and t['assignee_id']==l['owner_user_id']
        assert t['status']==('pending' if l['status']=='submitted' else 'completed')
        attrs = [a for a in current['performance_attribution'] if live(a) and a['fact_type']=='QUALIFICATION' and a['fact_id']==t['id']]
        if attrs:
            assert one(attrs)['outcome']==('pending' if l['status']=='submitted' else 'invalid')
        else:
            assert l['lead_no']=='KZ202609221710270021', 'Unreviewed missing attribution'
    return current


def repair(c, b, before):
    events = [e for e in allrows(c,'business_event') if e['idempotency_key'].startswith(MARK+':')]
    if events:
        assert len(events)==64
        verify(c,b)
        return 0
    now = query(c, 'SELECT NOW() t')[0]['t']
    expected = {t:{r['id']:dict(r) for r in before[t]} for t in TABLES}
    newids = {t:set() for t in TABLES}

    def change(table, row, data):
        update(c,table,row,data)
        expected[table][row['id']].update(data,updater=MARK)

    for l in b['leads']:
        effective_orders=[o for o in before['order'] if live(o) and o['lead_id']==l['id']]
        won=bool(effective_orders)
        products = sorted([p for p in before['lead_intended_product'] if live(p) and p['lead_id']==l['id']], key=lambda p:(p['sort'],p['id']))
        summary = json.dumps([dict(spuRef=p['spu_ref'] or '',spuName=p['spu_name_snapshot'] or '',
                                   skuRef=p['sku_ref'] or '',skuName=p['sku_name_snapshot'] or '',
                                   primary=p['is_primary']==b'\1') for p in products],ensure_ascii=False)
        oscope = [o for o in before['opportunity'] if live(o) and o['lead_id']==l['id']]
        if oscope:
            o=one(oscope); oid=o['id']
            change('opportunity',o,dict(status='open',lost_at=None,lost_reason=None,
                    expected_product_summary=summary,version=o['version']+1))
        else:
            oid=insert(c,'opportunity',dict(person_id=l['person_id'],type='initial_conversion',lead_id=l['id'],
                       status='won' if won else 'open',won_at=effective_orders[0]['effective_at'] if won else None,
                       owner_user_id=l['owner_user_id'],expected_product_summary=summary,version=0))
            newids['opportunity'].add(oid)
        if won:
            order=one(effective_orders)
            change('order',order,dict(opportunity_id=oid,version=order['version']+1))
        lead_changes=dict(status='won' if won else 'valid',suspended_at=None,qualified_by_user_id=None,qualified_at=now,
               converted_at=now,last_activity_at=max(now,l['last_activity_at']),valid_description=REASON,
               invalid_reason=None,invalid_reason_label_snapshot=None,invalid_description=None,
               invalid_evidence_refs=None,version=l['version']+1)
        if won:
            lead_changes['next_follow_up_at']=None
        change('lead',l,lead_changes)
        t=one([t for t in before['business_task'] if live(t) and t['idempotency_key']==f"lead-qualification:{l['id']}:{l['qualification_round_no']}"])
        if t['status']=='pending':
            change('business_task',t,dict(status='completed',completed_at=now,version=t['version']+1))
        attrs=[a for a in before['performance_attribution'] if live(a) and a['fact_type']=='QUALIFICATION' and a['fact_id']==t['id']]
        if attrs:
            change('performance_attribution',one(attrs),dict(outcome='valid',completed_at=now))
        refs=dict(repairSource=MARK,roundNo=l['qualification_round_no'],opportunityId=oid,
                  executionType='user_authorized_database_repair',previousStatus=l['status'],
                  categoryPreserved=True,notificationsSent=False,preservedEffectiveOrder=won)
        eid=insert(c,'business_event',dict(event_type='lead_qualified_valid',aggregate_type='lead',aggregate_id=l['id'],
                   operator_user_id=None,from_status=l['status'],to_status='won' if won else 'valid',reason=REASON,
                   related_object_refs=json.dumps(refs),occurred_at=now,idempotency_key=f"{MARK}:{l['id']}"))
        newids['business_event'].add(eid)
        aid=insert(c,'business_audit_log',dict(operator_name_snapshot='系统运维修复',operator_role_snapshot='SYSTEM',
                   executor_type='SYSTEM',executor_identity=MARK,execution_key=f"{MARK}:{l['id']}",
                   category_code='lead',action_code='lead.qualification.operational-repair',target_type='lead',
                   target_id=l['lead_no'],detail_json=json.dumps(refs),source_type='SYSTEM',result_status='SUCCESS',
                   result_code=0,occurred_at=now,finished_at=now))
        newids['business_audit_log'].add(aid)
    after=snapshot(c)
    for t in TABLES:
        actual={r['id']:r for r in after[t]}
        assert set(actual)==set(expected[t])|newids[t], 'Unexpected row addition/deletion'
        for rid, row in expected[t].items():
            for key, val in row.items():
                if key=='update_time' and row.get('updater')==MARK:
                    continue
                got=actual[rid][key]
                if key=='expected_product_summary' and val is not None:
                    assert json.loads(got)==json.loads(val)
                else:
                    assert got==val, f'Unexpected {t}.{key} modification'
    for t in DEPS:
        assert before[t]==after[t], 'Dependency unexpectedly modified'
    verify(c,b)
    assert {t:len(x) for t,x in newids.items()}==dict(lead=0,opportunity=63,business_task=0,
             performance_attribution=0,business_event=64,business_audit_log=64,order=0)
    return 64


def verify(c,b):
    s=snapshot(c)
    for old in b['leads']:
        won=old['lead_no']=='LD202609150049'
        l=one([l for l in s['lead'] if l['id']==old['id']])
        assert l['status']==('won' if won else 'valid') and l['assignment_status']=='owned' and l['valid_description']==REASON
        for key in ['owner_user_id','lead_category','lead_category_label_snapshot','current_assignment_first_follow_up_at',
                    'current_assignment_history_id','qualification_deadline_at','qualification_round_no']:
            assert l[key]==old[key]
        o=one([o for o in s['opportunity'] if live(o) and o['lead_id']==l['id']])
        assert o['status']==('won' if won else 'open') and o['owner_user_id']==l['owner_user_id'] and o['type']=='initial_conversion'
        if won:
            order=one([r for r in s['order'] if live(r) and r['lead_id']==l['id']])
            assert order['opportunity_id']==o['id'] and order['status']=='effective' and order['effective_at']==o['won_at']
        t=one([t for t in s['business_task'] if live(t) and t['idempotency_key']==f"lead-qualification:{l['id']}:{l['qualification_round_no']}"])
        assert t['status']=='completed'
        attrs=[a for a in s['performance_attribution'] if live(a) and a['fact_type']=='QUALIFICATION' and a['fact_id']==t['id']]
        if attrs:
            a=one(attrs)
            assert a['outcome']=='valid' and a['completed_at']==l['qualified_at']
        else:
            assert l['lead_no']=='KZ202609221710270021'
        e=one([e for e in s['business_event'] if e['idempotency_key']==f"{MARK}:{l['id']}"])
        assert e['from_status']==old['status'] and e['to_status']==l['status'] and e['operator_user_id'] is None
        audit=one([a for a in s['business_audit_log'] if a['execution_key']==f"{MARK}:{l['id']}"])
        assert audit['target_id']==l['lead_no'] and audit['result_status']=='SUCCESS'
    hexrows=query(c, 'SELECT DISTINCT HEX(valid_description) h FROM zsjos_lead WHERE tenant_id=1 AND updater=%s',(MARK,))
    assert hexrows==[{'h':REASON.encode('utf-8').hex().upper()}]


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('mode',choices=['prepare','apply','verify'])
    p.add_argument('--backup-dir',type=Path,required=True)
    p.add_argument('--reconcile-effective-order',action='store_true',help='Requires explicit approval of the documented order exception')
    args=p.parse_args()
    assert os.environ.get('ZSJOS_AGENT_ENV',Path('/etc/zsjos/agent-environment').read_text().strip())=='test'
    os.umask(0o077)
    dest=args.backup_dir.resolve()
    assert str(dest).startswith('/opt/zsjos-runtime/backups/')
    dest.mkdir(parents=True,exist_ok=True,mode=0o700)
    f=dest/'before.pickle'
    c=connect()
    try:
        if args.mode=='prepare':
            assert not f.exists(), 'Do not replace frozen backup'
            b=dict(leads=discover(c),before=snapshot(c))
            assert len(b['leads'])==64
            preflight(c,b)
            f.write_bytes(pickle.dumps(b)); c.rollback()
            print(json.dumps(dict(prepared=64,backup_sha256=hashlib.sha256(f.read_bytes()).hexdigest())))
            return
        b=pickle.loads(f.read_bytes())  # Trusted private operational backup only.
        if args.mode=='verify':
            verify(c,b)
            print('Verified: qualification states, opportunities, completed tasks, existing attribution results, events/audits and UTF-8 HEX correct.')
            return
        assert args.reconcile_effective_order, 'Effective-order exception needs separately confirmed handling'
        for commit in [False,True]:
            before=preflight(c,b)
            assert repair(c,b,before)==64
            after=snapshot(c)
            assert repair(c,b,before)==0 and snapshot(c)==after
            if commit:
                (dest/'after.pickle').write_bytes(pickle.dumps(after))
                c.commit(); print('Committed 64 records after exact-diff/relationship/UTF-8/idempotence checks.')
            else:
                c.rollback(); print('Rehearsal passed and rolled back.')
        c.close(); c=connect()
        verify(c,b)
        assert scoped_images(snapshot(c),b)==scoped_images(pickle.loads((dest/'after.pickle').read_bytes()),b)
        print('Fresh-connection postcommit exact readback passed.')
    finally:
        c.rollback(); c.close()


if __name__=='__main__':
    main()
