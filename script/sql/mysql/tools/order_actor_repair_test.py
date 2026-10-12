# -*- coding: utf-8 -*-
"""Controlled MySQL 8 tests. Creates isolated schemas and never deletes them."""
import datetime as dt
import hashlib
import json
from pathlib import Path
from order_actor_repair import connect, ident, run, SQL, KEY, ROOT
from order_actor_repair_sql import TABLES, CHANGES
from order_actor_repair_verify import verify
from order_history_repair import statements, scalar

CLONE_TABLES=TABLES+['system_users','system_dept','zsjos_performance_org','zsjos_lead',
                   'zsjos_data_repair_backup','zsjos_schema_version','zsjos_module_schema_version']


def clone(name, empty=False):
    c=connect()
    with c.cursor() as q:
        q.execute('CREATE DATABASE '+ident(name)+' CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci')
        for t in CLONE_TABLES:
            q.execute('SHOW CREATE TABLE '+ident(t));ddl=list(q.fetchone().values())[1]
            ddl=ddl.replace('CREATE TABLE '+ident(t),'CREATE TABLE '+ident(name)+'.'+ident(t),1)
            q.execute(ddl)
            if empty and t not in ('zsjos_schema_version','zsjos_module_schema_version'):continue
            q.execute("SELECT column_name FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name=%s AND extra NOT LIKE '%%GENERATED%%' ORDER BY ordinal_position",(t,))
            cols=','.join(ident(r['COLUMN_NAME']) for r in q.fetchall())
            q.execute(f'INSERT INTO {ident(name)}.{ident(t)} ({cols}) SELECT {cols} FROM {ident(t)}')
    c.close();return connect(name)


def snapshot(c):
    result={}
    with c.cursor() as q:
        for t in CLONE_TABLES:
            q.execute('SELECT * FROM '+ident(t)+' ORDER BY id' if t not in ('zsjos_module_schema_version','zsjos_schema_version') else 'SELECT * FROM '+ident(t)+' ORDER BY version')
            result[t]=q.fetchall()
    return result


def normalize(row):
    r=dict(row)
    if 'order_no' in r:r.setdefault('repaired_actor_snapshot',None)
    return r


def scoped(before,after):
    counts={}
    for t in CLONE_TABLES:
        if t in ('zsjos_data_repair_backup','zsjos_schema_version','zsjos_module_schema_version'):continue
        assert len(before[t])==len(after[t]),'Unexpected inserted/deleted rows'
        counts[t]=0
        for old,new in zip(before[t],after[t]):
            old=normalize(old);new=normalize(new)
            if old==new:continue
            counts[t]+=1
            assert t in TABLES,'Protected table changed'
            assert old['deleted'] in (0,b'\x00') and old['tenant_id']==1
            assert all(old[k]==new[k] for k in old if k not in CHANGES[t]),'Unexpected column changed'
            if t=='zsjos_order_approval_round':
                a=json.loads(old['order_snapshot']);b=json.loads(new['order_snapshot'])
                for k in ('submitter','formalSales'):a.pop(k,None);b.pop(k,None)
                assert a==b,'Other snapshot facts changed'
            if t=='zsjos_order' and old.get('imported_actor_snapshot'):
                a=json.loads(old['imported_actor_snapshot']);b=json.loads(new['imported_actor_snapshot'])
                for k in ('submitter','formalSales'):
                    if a.get(k):a[k].pop('orderUserId',None)
                    if b.get(k):b[k].pop('orderUserId',None)
                assert a==b,'Legacy employee evidence changed'
    return counts


def execute_text(c,text):
    with c.cursor() as q:
        for s in statements(text):q.execute(s)


def fails(c,text,expected):
    try:execute_text(c,text)
    except Exception as e:
        c.rollback();assert expected in str(e),type(e).__name__+': '+str(e)[:160]
    else:raise AssertionError('Expected failure: '+expected)


def restore_row(c,t,row):
    values=[];sets=[]
    for k in CHANGES[t]:
        v=row.get(k)
        if isinstance(v,(dict,list)):v=json.dumps(v,ensure_ascii=False)
        sets.append(ident(k)+'=%s');values.append(v)
    values.append(row['id'])
    with c.cursor() as q:q.execute('UPDATE '+ident(t)+' SET '+','.join(sets)+' WHERE id=%s',values)


def tests():
    stamp=dt.datetime.now().strftime('%Y%m%d%H%M%S');name='order_actors_v312_'+stamp
    c=clone(name);before=snapshot(c);text=SQL.read_text(encoding='utf-8')
    # Missing prerequisite must not even add the column.
    with c.cursor() as q:q.execute("UPDATE zsjos_module_schema_version SET version='V311-hidden' WHERE module_code='core' AND version='V311'")
    fails(c,text,'V312 requires V311')
    with c.cursor() as q:q.execute("UPDATE zsjos_module_schema_version SET version='V311' WHERE module_code='core' AND version='V311-hidden'")
    # Changed business origin rejects before any evidence/ledger write.
    with c.cursor() as q:q.execute("UPDATE zsjos_order SET submitter_user_id=999,update_time=update_time WHERE order_no='OR202609070005' AND tenant_id=1")
    fails(c,text,'V312 order preimage changed')
    with c.cursor() as q:q.execute("UPDATE zsjos_order SET submitter_user_id=41,update_time=update_time WHERE order_no='OR202609070005' AND tenant_id=1")
    for marker in ['INSERT INTO zsjos_schema_version(version,description,checksum)', 'INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version)']:
        baseline=snapshot(c)
        fails(c,text.replace(marker,"SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='V312 injected failure';\n"+marker),'V312 injected failure')
        assert snapshot(c)==baseline,'Failure left data/evidence/ledger writes'
    run(c);after=snapshot(c);counts=scoped(before,after);checks=verify(c)
    run(c);assert snapshot(c)==after,'Replay changed rows'
    # Complete row before-image is safe partial recovery, even with version markers present.
    old=next(r for r in before['zsjos_order'] if r['order_no']=='OR202609070005')
    restore_row(c,'zsjos_order',old);run(c);assert snapshot(c)==after
    with c.cursor() as q:q.execute("UPDATE zsjos_order SET remark=CONCAT(COALESCE(remark,''),'concurrent'),update_time=update_time WHERE id=%s",(old['id'],))
    fails(c,text,'V312 before/after conflict')
    with c.cursor() as q:q.execute('UPDATE zsjos_order SET remark=%s,update_time=update_time WHERE id=%s',(old['remark'],old['id']))
    assert snapshot(c)==after
    # Physical rollback rehearsal stays in a transaction and leaves applied state intact.
    with c.cursor() as q:q.execute('START TRANSACTION')
    for t in TABLES:
        for row in before[t]:
            if normalize(row)!=normalize(next(n for n in after[t] if n['id']==row['id'])):restore_row(c,t,row)
    rolled=snapshot(c)
    for t in TABLES:assert [normalize(r) for r in rolled[t]]==[normalize(r) for r in before[t]]
    c.rollback();assert snapshot(c)==after
    # Export only private evidence for reader tests, never print record payloads.
    out=ROOT/'backups/mysql'/('order-actors-v312-'+stamp);out.mkdir(parents=True,exist_ok=True)
    (out/'before.json').write_text(json.dumps(before,default=scalar,ensure_ascii=False),encoding='utf-8')
    (out/'after.json').write_text(json.dumps(after,default=scalar,ensure_ascii=False),encoding='utf-8')
    c.close();empty=clone(name+'_empty',True);run(empty);assert verify(empty)['orders']==0
    e=snapshot(empty);run(empty);assert snapshot(empty)==e;empty.close()
    result={'schema':name,'evidence':str(out),'changes':counts,'checks':checks,'tests':'prerequisite,preimage,transaction/ledger failures,first,repeat,partial,concurrent,rollback,empty'}
    (out/'verification.json').write_text(json.dumps(result,indent=2),encoding='utf-8');print(json.dumps(result))


if __name__=='__main__':tests()
