# -*- coding: utf-8 -*-
"""Scoped synthetic student-order cleanup, with private plans and guarded recovery.

Never deletes people/services. Only the four confirmed seed batches qualify; genuine
zero-price business orders are not selected. Deploy null-safe readers before apply-local.
"""
import argparse
import copy
import datetime as dt
import hashlib
import json
from pathlib import Path

from order_history_repair import connect,ident,schema,normalize,encode,private_write,statements

ROOT=Path(__file__).resolve().parents[4]
KEY='student-virtual-order-cleanup'
BATCHES=('seed-liudandan','seed-students','seed-llyilin','seed-batch4')
TABLES=('zsjos_order','zsjos_order_item','zsjos_registration_case','zsjos_service_relation')
SQL=ROOT/'script/sql/mysql/migrations/V310__student_service_without_virtual_order.sql'


def snapshot(c):
    with c.cursor() as q:
        types=schema(q);data={}
        for t in TABLES:
            q.execute('SELECT * FROM '+ident(t)+' WHERE tenant_id=1')
            data[t]={r['id']:normalize(r,types[t]) for r in q.fetchall()}
        return data


def guard_references(c,ids,items,cases):
    with c.cursor() as q:
        types=schema(q)
        for t,cols in types.items():
            if not t.startswith('zsjos_') or '_bak_' in t or 'backup' in t:continue
            for col in cols:
                vals=None
                if col in ('order_id','source_order_id','previous_order_id','current_order_id','supersedes_order_id','superseded_by_order_id'):vals=ids
                if col in ('order_item_id','source_order_item_id'):vals=items
                if col=='registration_case_id':vals=cases
                if vals and t not in TABLES:
                    q.execute('SELECT COUNT(*) n FROM '+ident(t)+' WHERE '+ident(col)+' IN ('+','.join(['%s']*len(vals))+')',tuple(vals))
                    if q.fetchone()['n']:raise ValueError('Unreviewed relational reference: '+t+'.'+col)
        if ids:
            q.execute("SELECT COUNT(*) n FROM zsjos_performance_attribution WHERE tenant_id=1 AND fact_type='ORDER' AND fact_id IN ("+','.join(['%s']*len(ids))+')',tuple(ids))
            if q.fetchone()['n']:raise ValueError('Unexpected performance facts')
        def walk(v):
            if isinstance(v,dict):
                for k,x in v.items():
                    group=ids if k in ('orderId','order_id','sourceOrderId') else items if k in ('orderItemId','order_item_id') else cases if k in ('registrationCaseId','registration_case_id') else set()
                    if str(x) in {str(i) for i in group}:return True
                    if walk(x):return True
            if isinstance(v,list):return any(walk(x) for x in v)
            return False
        for t,cols in types.items():
            if not t.startswith('zsjos_') or '_bak_' in t or 'backup' in t:continue
            for col,kind in cols.items():
                if kind!='json':continue
                q.execute('SELECT '+ident(col)+' v FROM '+ident(t)+' WHERE '+ident(col)+' IS NOT NULL')
                for r in q.fetchall():
                    if walk(json.loads(r['v'])):raise ValueError('Unreviewed JSON reference: '+t+'.'+col)


def plan(c):
    before=snapshot(c);data=copy.deepcopy(before);now=dt.datetime.now().strftime('%Y-%m-%d %H:%M:%S')
    targets=[o for o in data['zsjos_order'].values() if not o['deleted'] and o['creator'] in BATCHES]
    ids={o['id'] for o in targets};items={i['id'] for i in data['zsjos_order_item'].values() if i['order_id'] in ids};cases={i['id'] for i in data['zsjos_registration_case'].values() if i['order_id'] in ids}
    guard_references(c,ids,items,cases)
    report=[];dependencies=[]
    for o in targets:
        if float(o['total_amount'])!=0 or float(o['payable_amount'])!=0 or o['current_approval_round_id'] is not None:raise ValueError('Seed no longer matches virtual-order evidence')
        rels=[r for r in data['zsjos_service_relation'].values() if r['order_id']==o['id'] and not r['deleted']]
        if len(rels)!=1:raise ValueError('Expected exactly one preserved service')
        r=rels[0];r.update(order_id=None,order_item_id=None,registration_case_id=None)
        mapped=None
        if o['order_no']=='OD202609231629388801':
            real=[x for x in data['zsjos_order'].values() if x['order_no']=='OR202609200025' and x['person_id']==o['person_id'] and not x['deleted'] and x['status']=='effective']
            if len(real)!=1:raise ValueError('Reviewed real order unavailable')
            real=real[0];ri=[x for x in data['zsjos_order_item'].values() if x['order_id']==real['id'] and not x['deleted']];rc=[x for x in data['zsjos_registration_case'].values() if x['order_id']==real['id'] and not x['deleted']]
            if len(ri)!=1 or len(rc)!=1:raise ValueError('Reviewed real item/case ambiguous')
            if any(x['order_item_id']==ri[0]['id'] and not x['deleted'] for x in before['zsjos_service_relation'].values()):raise ValueError('Real item already has service')
            dependencies.extend([{'table':'zsjos_order','row':copy.deepcopy(real)}, {'table':'zsjos_order_item','row':copy.deepcopy(ri[0])}, {'table':'zsjos_registration_case','row':copy.deepcopy(rc[0])}])
            r.update(order_id=real['id'],order_item_id=ri[0]['id'],registration_case_id=rc[0]['id'],service_snapshot=ri[0]['product_snapshot']);mapped=real['order_no']
        report.append({'virtualOrder':o['order_no'],'serviceId':r['id'],'realOrder':mapped})
        o['deleted']=1
        for t in ('zsjos_order_item','zsjos_registration_case'):
            for row in data[t].values():
                if row['order_id']==o['id']:row['deleted']=1
    ops=[]
    for t in reversed(TABLES):
        for rid,row in data[t].items():
            if row==before[t][rid]:continue
            if 'version' in row:row['version']=(row['version'] or 0)+1
            row.update(updater=KEY,update_time=now)
            ops.append({'table':t,'before':before[t][rid],'after':row})
    return {'key':KEY,'createdAt':now,'orders':sorted(ids),'items':sorted(items),'cases':sorted(cases),'operations':ops,'report':report,'dependencies':dependencies}


def apply(c,p,sha,dry=False,fail=False):
    with c.cursor() as q:
        q.execute("SELECT GET_LOCK(CONCAT('virtual-student:',DATABASE()),10) ok")
        if q.fetchone()['ok']!=1:raise ValueError('Cleanup lock unavailable')
    try:
        with c.cursor() as q:
            for sql in statements(SQL.read_text(encoding='utf-8')):q.execute(sql)
            q.execute('SET TRANSACTION ISOLATION LEVEL SERIALIZABLE');q.execute('START TRANSACTION')
            types=schema(q);guard_references(c,set(p['orders']),set(p['items']),set(p['cases']))
            for dep in p['dependencies']:
                q.execute('SELECT * FROM '+ident(dep['table'])+' WHERE id=%s FOR UPDATE',(dep['row']['id'],))
                actual=q.fetchone()
                if not actual or normalize(actual,types[dep['table']])!=dep['row']:raise ValueError('Real order reference changed')
            # Lock all service references to the removed chain, rejecting newly created relations.
            q.execute('SELECT id FROM zsjos_service_relation WHERE order_id IN ('+','.join(['%s']*len(p['orders']))+') FOR UPDATE',tuple(p['orders']))
            expected={op['before']['id'] for op in p['operations'] if op['table']=='zsjos_service_relation'}
            if any(r['id'] not in expected for r in q.fetchall()):raise ValueError('New service references virtual chain')
            q.execute('SELECT old_value FROM zsjos_data_repair_backup WHERE repair_key=%s AND table_name=%s AND row_id=0 AND column_name=%s FOR UPDATE',(KEY,'__plan__','sha256'))
            saved=q.fetchall()
            if saved and (len(saved)!=1 or saved[0]['old_value']!=sha):raise ValueError('Different cleanup plan already recorded')
            for op in p['operations']:
                q.execute('SELECT * FROM '+ident(op['table'])+' WHERE id=%s FOR UPDATE',(op['before']['id'],));row=q.fetchone()
                current=normalize(row,types[op['table']]) if row else None
                if current not in (op['before'],op['after']):raise ValueError('Cleanup before/after conflict')
            changed=0
            for op in p['operations']:
                t=op['table'];old=op['before'];new=op['after'];q.execute('SELECT * FROM '+ident(t)+' WHERE id=%s',(old['id'],))
                if normalize(q.fetchone(),types[t])==new:continue
                if not saved:
                    for kind,value in [('__before__',old),('__after__',new)]:
                        q.execute('INSERT INTO zsjos_data_repair_backup(repair_key,table_name,row_id,column_name,old_value) VALUES(%s,%s,%s,%s,%s)',(KEY,t,old['id'],kind,json.dumps(value,ensure_ascii=False)))
                cols=[k for k in new if old.get(k)!=new[k]]
                q.execute('UPDATE '+ident(t)+' SET '+','.join(ident(k)+'=%s' for k in cols)+' WHERE id=%s',tuple(encode(new[k],types[t][k]) for k in cols)+(old['id'],));changed+=1
                if fail:raise ValueError('Injected test failure')
            for op in p['operations']:
                q.execute('SELECT * FROM '+ident(op['table'])+' WHERE id=%s',(op['after']['id'],))
                if normalize(q.fetchone(),types[op['table']])!=op['after']:raise ValueError('Cleanup postcondition failed')
            if not saved:q.execute('INSERT INTO zsjos_data_repair_backup(repair_key,table_name,row_id,column_name,old_value) VALUES(%s,%s,0,%s,%s)',(KEY,'__plan__','sha256',sha))
        c.rollback() if dry else c.commit()
        return changed
    except Exception:c.rollback();raise
    finally:
        with c.cursor() as q:q.execute("SELECT RELEASE_LOCK(CONCAT('virtual-student:',DATABASE()))")


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('mode',choices=['prepare','apply-local','verify']);p.add_argument('--plan',type=Path,required=True);p.add_argument('--expected-sha256');p.add_argument('--readers-deployed',action='store_true');a=p.parse_args();c=connect()
    if a.mode=='prepare':
        value=plan(c);sha=private_write(a.plan,value);print(json.dumps({'orders':len(value['orders']),'operations':len(value['operations']),'sha256':sha}))
    else:
        raw=a.plan.read_bytes();sha=hashlib.sha256(raw).hexdigest()
        if sha!=a.expected_sha256:raise ValueError('Reviewed plan hash required')
        value=json.loads(raw)
        if a.mode=='apply-local':
            if not a.readers_deployed:raise ValueError('Deploy null-safe backend before data cleanup')
            n=apply(c,value,sha);print(json.dumps({'changed':n,'repeatChanged':apply(c,value,sha)}))
        else:
            actual=snapshot(c);bad=[x['table'] for x in value['operations'] if actual[x['table']].get(x['after']['id'])!=x['after']]
            print(json.dumps({'afterImageMismatches':bad}))
    c.close()


if __name__=='__main__':main()
