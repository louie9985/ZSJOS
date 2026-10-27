#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Explicit local MySQL verification; creates retained fixtures, never deletes source data."""
import argparse
import gc
import json
import subprocess
import sys
from copy import deepcopy
from datetime import datetime
from pathlib import Path
import import_students as imp
import import_disabled_skus as scoped
from historical_disabled_skus import SPECS, inspect, apply

def read(c,sql,args=()):
    with c.cursor() as q:return imp.fetch(q,sql,args)
def state(c):
    return {t:imp.digest(sorted(imp.digest(r) for r in read(c,f'SELECT * FROM `{t}`'))) for t in imp.TABLES}
def reject(fn):
    try:fn()
    except ValueError:return
    raise AssertionError('Expected guard rejection')

def clone(omit_count):
    src=imp.connect('ruoyi-vue-pro')
    with src.cursor() as q:
        q.execute('SET SESSION TRANSACTION ISOLATION LEVEL REPEATABLE READ')
        q.execute('SET TRANSACTION READ ONLY')
        q.execute('START TRANSACTION WITH CONSISTENT SNAPSHOT')
    configuration=inspect(src)
    omit=configuration['rows'][:omit_count]
    sku_ids={r['existingSkuId'] for r in omit}
    value_ids={r['existingValueId'] for r in omit}
    db='historical_student_verify_'+datetime.now().strftime('%Y%m%d%H%M%S%f')
    writer=imp.connect('ruoyi-vue-pro')
    before={}
    with writer.cursor() as q:
        q.execute(f'CREATE DATABASE `{db}` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci')
        for t in imp.TABLES:q.execute(f'CREATE TABLE `{db}`.`{t}` LIKE `ruoyi-vue-pro`.`{t}`')
        writer.select_db(db)
        for t in imp.TABLES:
            cols=[r['COLUMN_NAME'] for r in read(src,"SELECT column_name FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name=%s AND COALESCE(generation_expression,'')='' ORDER BY ordinal_position",(t,))]
            rows=read(src,f'SELECT * FROM `{t}`')
            before[t]=imp.digest(sorted(imp.digest(r) for r in rows))
            if t=='zsjos_product_sku':rows=[r for r in rows if r['id'] not in sku_ids]
            if t=='zsjos_product_attr_value':rows=[r for r in rows if r['id'] not in value_ids]
            sql=f'INSERT INTO `{t}` ('+','.join('`'+x+'`' for x in cols)+') VALUES ('+','.join(['%s']*len(cols))+')'
            for i in range(0,len(rows),200):q.executemany(sql,[tuple(r[x] for x in cols) for r in rows[i:i+200]])
            del rows;gc.collect()
    writer.commit();src.rollback();src.close()
    return writer,db,before

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--source',type=Path,required=True)
    p.add_argument('--exceptions',type=Path,required=True)
    p.add_argument('--output',type=Path,required=True)
    a=p.parse_args()
    source=scoped.read_source(a.source,a.exceptions)
    c,db,source_before=clone(5)
    initial=state(c)
    plan=inspect(c)
    assert all(r['existingSkuId'] is None for r in plan['rows'])
    try:apply(c,plan,fail_after=2)
    except RuntimeError:pass
    else:raise AssertionError('Configuration injection failed')
    assert state(c)==initial
    configured=apply(c,plan)
    after_seed=state(c)
    apply(c,plan)
    assert state(c)==after_seed
    assert all(initial[t]==after_seed[t] for t in imp.TABLES if t not in ('zsjos_product_sku','zsjos_product_attr_value'))
    with c.cursor() as q:
        catalog=imp.catalog(q)
        for r in source['candidates']:
            assert scoped.authorized_sku(catalog,r,source)['status']==1
        bad=deepcopy(source['candidates'][0]);bad['row']=48
        reject(lambda:scoped.authorized_sku(catalog,bad,source))
        bad=deepcopy(source['candidates'][0]);bad['sku']='unreviewed'
        reject(lambda:scoped.authorized_sku(catalog,bad,source))
        tampered=deepcopy(catalog)
        selected=scoped.authorized_sku(tampered,source['candidates'][0],source)
        selected['status']=0
        reject(lambda:scoped.authorized_sku(tampered,source['candidates'][0],source))
        unscheduled=next(r for r in source['candidates'] if r['sheetIndex']==4 and r['row']==45)
        assert imp.resolve_class(catalog,unscheduled,allow_unscheduled=True)['classId'] is not None
        reject(lambda:imp.resolve_class(catalog,unscheduled))
        bad_classes=deepcopy(catalog)
        owner=next(u for u in catalog['system_users'] if u['nickname']==unscheduled['sourcePlanner'])
        cls=next(x for x in bad_classes['zsjos_delivery_class'] if x['class_name']==unscheduled['className'] and x['homeroom_user_id']==owner['id'])
        cls['class_kind']='EXCEPTION'
        reject(lambda:imp.resolve_class(bad_classes,unscheduled,allow_unscheduled=True))
        cls['class_kind']='PENDING'
        reject(lambda:imp.resolve_class(bad_classes,unscheduled,allow_unscheduled=True))
        # Existing incompatible or enabled catalog data is rejected, never overwritten.
        q.execute('UPDATE zsjos_product_sku SET status=0 WHERE tenant_id=1 AND id=%s',(configured['rows'][0]['existingSkuId'],))
        reject(lambda:inspect(c));c.rollback()
    assert state(c)==after_seed
    regular=deepcopy(source);regular.pop('historicalSkuAuthorization');regular['identityConfirmationsSha256']='fixture-only'
    denied=imp.prepare(c,regular,'admin')
    assert not denied['ready'] and len(denied['exceptions'])==33
    prepared=imp.prepare(c,source,'admin')
    assert len(prepared['ready'])==33 and not prepared['exceptions'], imp.summary(prepared)
    plan_path=a.output.with_suffix('.plan.json')
    imp.save_private(plan_path,prepared)
    verify_command=[sys.executable,str(Path(__file__).with_name('import_disabled_skus.py')),'verify','--source',str(a.source),'--exceptions',str(a.exceptions),'--database',db,'--actor-username','admin','--plan',str(plan_path)]
    not_imported=subprocess.run(verify_command,capture_output=True,text=True,encoding='utf-8')
    assert not_imported.returncode!=0 and 'not been imported' in not_imported.stderr
    assert len(prepared['ready'])+len(prepared['exceptions'])+len(prepared['existing'])+len(prepared['replay'])==33
    assert all(r['row']!=48 for r in prepared['ready'])
    assert all(r['snapshot']['skuDisabledAtImport'] and r['snapshot']['catalogPriceIsPlaceholder'] for r in prepared['ready'])
    if prepared['ready']:
        try:imp.apply(c,prepared,source,fail_after=1)
        except RuntimeError:pass
        else:raise AssertionError('Import rollback injection failed')
        assert state(c)==after_seed
        # Record immutable original rows with compact digests before actual fixture import.
        originals={}
        for t in imp.TABLES:
            rows=read(c,f'SELECT * FROM `{t}`')
            originals[t]={r['id']:imp.digest(r) for r in rows}
        person_before={r['id']:r for r in read(c,'SELECT * FROM zsjos_person')}
        ids=imp.apply(c,prepared,source)
        imp.verify(c,ids)
        allowed_new={'zsjos_person','zsjos_person_contact_claim','zsjos_person_no_daily_counter','zsjos_service_relation','zsjos_collaboration_group','zsjos_student_contact_record','zsjos_business_event','zsjos_delivery_class'}
        target_ids={r['personId'] for r in prepared['ready'] if r['personId'] is not None}
        for t in imp.TABLES:
            rows=read(c,f'SELECT * FROM `{t}`');now={r['id']:r for r in rows}
            assert originals[t].keys()<=now.keys()
            assert not(now.keys()-originals[t].keys()) or t in allowed_new
            for key,digest in originals[t].items():
                if digest==imp.digest(now[key]):continue
                if t=='zsjos_person':
                    assert key in target_ids
                    assert {k for k in now[key] if now[key][k]!=person_before[key][k]}<={'identity_status','version','updater','update_time'}
                else:
                    # Counter changes are permitted only by ordinary creation of new People.
                    assert t=='zsjos_person_no_daily_counter',t
            del rows,now;gc.collect()
        committed=state(c)
        assert imp.apply(c,prepared,source)==[] and state(c)==committed
        verified=subprocess.run(verify_command,capture_output=True,text=True,encoding='utf-8')
        assert verified.returncode==0,verified.stderr
        assert json.loads(verified.stdout)['verified']==len(ids)
    else:ids=[]
    c.close()
    partial,pdb,_=clone(3)
    pp=inspect(partial)
    assert sum(r['existingSkuId'] is None for r in pp['rows'])==3
    apply(partial,pp)
    completed=state(partial)
    apply(partial,pp)
    assert state(partial)==completed
    partial.close()
    original=imp.connect('ruoyi-vue-pro')
    # Only verify owning catalog tables: live business may legitimately change concurrently.
    for t in ('zsjos_product','zsjos_product_sku','zsjos_product_attr','zsjos_product_attr_value'):
        assert source_before[t]==imp.digest(sorted(imp.digest(r) for r in read(original,f'SELECT * FROM `{t}`')))
    original.close()
    result=dict(database=db,partialRecoveryDatabase=pdb,sourceSha256=source['sourceSha256'],summary=imp.summary(prepared),fixtureImported=len(ids),
                checks=['create-missing-five','seed-rollback','seed-repeat','partial-recovery','enabled-conflict-refused','ordinary-disabled-refused','unreviewed-row-refused','import-rollback','old-row-preservation','import-repeat','UTF8','source-catalog-unchanged'])
    imp.save_private(a.output,result)
    print(imp.packed(result))

if __name__=='__main__':main()
