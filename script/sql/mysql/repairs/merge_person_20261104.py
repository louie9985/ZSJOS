#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Frozen two-person test repair; private inputs and explicit online authorization required."""
import argparse
import copy
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys

TOOLS = Path(__file__).resolve().parents[1] / 'tools'
sys.path[:0] = [str(TOOLS / 'person_identity_repair'), str(TOOLS)]
from server_target import ServerTarget, environment, read, save, digest, require
from server_apply import connect_write
from zsjos_db import db_config
from repair import LIVE, helpers
from group_repair import capture, apply_group, restore_group, confirmed_rule
import pymysql

ROOT = Path('/opt/zsjos-runtime/backups/person-merge-20261104/online')
KEY = 'person-merge-20261104'
# Reviewed owning reference added after the original bulk merger was written.
LIVE['zsjos_student_info_task'] = ['person_id']


def rows(q, sql, args=()):
    q.execute(sql, args or None)
    return list(q.fetchall())


def protect(conn, queries):
    with conn.cursor() as q:
        return {t: rows(q, sql, args) for t, (sql, args) in queries.items()}


def prepare():
    require(environment() == 'test', 'Only the confirmed test environment is permitted')
    spec = read(ROOT / 'input.json')
    c = db_config('test')
    db = pymysql.connect(host=c.host, port=c.port, user=c.user, password=c.password,
                         database=c.database, charset='utf8mb4', autocommit=False,
                         cursorclass=pymysql.cursors.DictCursor)
    try:
        with db.cursor() as q:
            q.execute('SET SESSION TRANSACTION READ ONLY')
            q.execute('START TRANSACTION WITH CONSISTENT SNAPSHOT')
            server = rows(q, 'SELECT @@server_uuid uuid,@@version version')[0]
            container = json.loads(subprocess.check_output(['docker','inspect','zsjos-mysql-1']))[0]
            binding = dict(environment='test',host=c.host,port=c.port,database=c.database,
                           tenantId=1,serverUuid=server['uuid'],mysqlVersion=server['version'],
                           container='zsjos-mysql-1',containerId=container['Id'])
            state, meta = capture(q, spec['memberIds'], KEY, lock=False)
            require(len(state['zsjos_person']) == 2 and all(p['deleted'] == b'\0' for p in state['zsjos_person']), 'Expected two live identities')
            target = next(p for p in state['zsjos_person'] if p['id'] == spec['targetPersonId'])
            require(target['mobile'] == target['wechat_id'] == spec['canonicalContact'], 'Canonical target contacts changed')
            require(not state.get('zsjos_order') and not state.get('zsjos_person_merge_event'), 'Existing orders or merge require renewed review')
            require([r['id'] for r in state['zsjos_lead']] == [spec['leadId']], 'Lead scope changed')
            require([r['id'] for r in state['zsjos_service_relation']] == [spec['serviceId']], 'Service scope changed')
            confirmation = dict(memberIds=spec['memberIds'],targetPersonId=spec['targetPersonId'],
                                canonicalName=target['name'],reviewReference='Explicit user merge and canonical-contact instruction, 2026-11-04')
            rule = confirmed_rule(state,confirmation)
            plan = dict(database=c.database,memberIds=spec['memberIds'],key=KEY,rule=rule,
                        targetPersonId=spec['targetPersonId'],sourcePersonIds=[i for i in spec['memberIds'] if i!=spec['targetPersonId']],
                        before=state,fingerprint=helpers.digest(state),confirmation=confirmation)
            cols = {}
            for r in meta: cols.setdefault(r['t'],set()).add(r['c'])
            queries = {}
            for t, fields in cols.items():
                if not {'id','tenant_id'} <= fields or t in state or '_bak_' in t: continue
                terms=[]; args=[]
                for f,values in [('lead_id',[spec['leadId']]),('service_relation_id',[spec['serviceId']]),
                                 ('person_id',spec['memberIds']),('student_person_id',spec['memberIds']),
                                 ('collaboration_group_id',[r['id'] for r in state['zsjos_collaboration_group']])]:
                    if f in fields and values:
                        terms.append(f'`{f}` IN ('+','.join(['%s']*len(values))+')');args.extend(values)
                for kind,identifier in [('biz_type','biz_id'),('aggregate_type','aggregate_id'),('business_type','business_id')]:
                    if {kind,identifier} <= fields:
                        terms.append(f"(`{kind}` IN ('lead','student_service','student-service','person') AND `{identifier}` IN (%s,%s,%s,%s))")
                        args.extend([spec['leadId'],spec['serviceId'],*spec['memberIds']])
                if terms: queries[t]=(f'SELECT * FROM `{t}` WHERE tenant_id=1 AND ('+' OR '.join(terms)+') ORDER BY id',args)
            schemas = {t: rows(q,f'SHOW CREATE TABLE `{t}`')[0]['Create Table'] for t in state}
        protected=protect(db,queries)
        manifest=dict(binding=binding,spec=spec,plan=plan,schemas=schemas,protectedQueries=queries,
                      protected=protected,writerSha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest())
        save(ROOT/'manifest.json.gz',manifest)
        print(json.dumps(dict(prepared=True,affected={t:len(rs) for t,rs in state.items() if rs},protectedRows=sum(map(len,protected.values())),manifestSha256=hashlib.sha256((ROOT/'manifest.json.gz').read_bytes()).hexdigest())))
    finally: db.rollback(); db.close()


def exact_apply(conn,m,execute=True,receipt=None,fail=False):
    plan=m['plan']; spec=m['spec']
    def finish(result):
        with conn.cursor() as q:
            q.execute('UPDATE zsjos_lead SET submitted_mobile=%s,submitted_wechat_id=%s,version=version+1,updater=%s WHERE tenant_id=1 AND id=%s AND person_id=%s',
                      (spec['canonicalContact'],spec['canonicalContact'],KEY,spec['leadId'],spec['targetPersonId']))
            require(q.rowcount == 1,'Exact Lead correction required')
            after,_=capture(q,plan['memberIds'],KEY,lock=False)
            expected=copy.deepcopy(result['after'])
            lead=expected['zsjos_lead'][0]; actual=after['zsjos_lead'][0]
            lead.update(submitted_mobile=spec['canonicalContact'],submitted_wechat_id=spec['canonicalContact'],
                        version=lead['version']+1,updater=KEY,update_time=actual['update_time'])
            require(after==expected,'Unexpected contact correction side effect')
            result.update(after=after,afterFingerprint=helpers.digest(after))
            require(all(p['mobile']==p['wechat_id']==spec['canonicalContact'] for p in after['zsjos_person'] if p['id']==spec['targetPersonId']), 'Canonical contacts differ')
            require(digest(after['zsjos_service_relation'])==digest(plan['before']['zsjos_service_relation']),'Historical service changed')
            name=rows(q,'SELECT HEX(name) h FROM zsjos_person WHERE tenant_id=1 AND id=%s',(spec['targetPersonId'],))[0]['h']
            require(name.lower()==plan['confirmation']['canonicalName'].encode('utf-8').hex(),'UTF-8 name differs')
        if receipt:
            require(digest(protect(conn,m['protectedQueries']))==digest(m['protected']),'Protected history changed before commit')
        if fail: raise ValueError('Injected failure after canonical contact update')
        if receipt: save(receipt,result)
    result=apply_group(conn,plan,execute=execute,before_commit=finish)
    if result.get('replay'):
        with conn.cursor() as q:
            lead=rows(q,'SELECT submitted_mobile,submitted_wechat_id,person_id FROM zsjos_lead WHERE tenant_id=1 AND id=%s',(spec['leadId'],))[0]
            require(lead==dict(submitted_mobile=spec['canonicalContact'],submitted_wechat_id=spec['canonicalContact'],person_id=spec['targetPersonId']),'Incomplete replay')
        conn.rollback()
    return result


def rehearse(m):
    target=ServerTarget(m['binding']); database=target.create_isolated();db=target.connect_isolated(database)
    try:
        with db.cursor() as q:
            for table,ddl in m['schemas'].items():
                q.execute(ddl)
                columns=[r['Field'] for r in rows(q,f'SHOW COLUMNS FROM `{table}`') if 'VIRTUAL GENERATED' not in r['Extra'] and 'STORED GENERATED' not in r['Extra']]
                data=m['plan']['before'][table]
                if data:q.executemany(f'INSERT INTO `{table}` ('+','.join(f'`{c}`' for c in columns)+') VALUES ('+','.join(['%s']*len(columns))+')',[tuple(r[c] for c in columns) for r in data])
        db.commit()
        try: exact_apply(db,m,fail=True)
        except ValueError as e: require(str(e).startswith('Injected failure'),'Unexpected rehearsal failure')
        else: raise ValueError('Failure injection did not fire')
        with db.cursor() as q: state,_=capture(q,m['plan']['memberIds'],KEY,lock=False)
        require(digest(state)==digest(m['plan']['before']),'Failure rollback mismatch');db.rollback()
        result=exact_apply(db,m)
        require(exact_apply(db,m)['replay'],'Replay writes unexpectedly')
        restore_group(db,m['plan'],result,execute=True)
        result=exact_apply(db,m)
        restore_group(db,m['plan'],result,execute=True)
        report=dict(isolatedDatabase=database,initial=True,replayNoWrite=True,failureRollback=True,restoreReapply=True,
                    writerSha256=m['writerSha256'],manifestSha256=hashlib.sha256((ROOT/'manifest.json.gz').read_bytes()).hexdigest())
        save(ROOT/'rehearsal.json',report); print(json.dumps(report))
    finally:db.close()


def apply(m,sha):
    require(sha==hashlib.sha256((ROOT/'manifest.json.gz').read_bytes()).hexdigest(),'Manifest approval hash differs')
    report=read(ROOT/'rehearsal.json');require(report['manifestSha256']==sha,'Rehearsal differs')
    # User explicitly approved this one online merge after the stale-request risk
    # was explained. Keep the generic maintenance-only executor unchanged.
    require(read(ROOT/'authorization.json')['mode']=='explicit-online-single-group', 'Online authorization record required')
    target=ServerTarget(m['binding'])
    require(not (ROOT/'precommit.json.gz').exists(), 'Existing commit receipt requires inspection')
    db=connect_write(target)
    try:
        with db.cursor() as q:
            q.execute('SET SESSION innodb_lock_wait_timeout=2')
        require(protect(db,m['protectedQueries'])==m['protected'],'Protected history changed; prepare a new reviewed plan')
        db.rollback()
        result=exact_apply(db,m,receipt=ROOT/'precommit.json.gz')
        require(not result.get('replay'),'Inspect previous committed receipt before resuming')
        require(protect(db,m['protectedQueries'])==m['protected'],'Protected history changed')
        db.rollback()
        require(exact_apply(db,m)['replay'],'Committed replay failed')
    finally:db.close()
    verify(m)


def verify(m):
    target=ServerTarget(m['binding']);db=target.connect_readonly()
    try:
        expected=read(ROOT/'precommit.json.gz')['after']
        with db.cursor() as q:
            state,_=capture(q,m['plan']['memberIds'],KEY,lock=False)
            require(digest(state)==digest(expected),'Committed after-image differs')
            candidates=rows(q,'SELECT id FROM zsjos_person WHERE tenant_id=1 AND deleted=0 AND (mobile=%s OR wechat_id=%s)',(m['spec']['canonicalContact'],)*2)
            require(candidates==[{'id':m['spec']['targetPersonId']}],'Canonical identity is ambiguous')
        require(protect(db,m['protectedQueries'])==m['protected'],'Historical records changed')
        save(ROOT/'verified.json',dict(committed=True,independentReadback=True,canonicalSingleIdentity=True,protectedHistory=True))
        print(json.dumps(dict(committed=True,verified=True)))
    finally:db.close()


if __name__=='__main__':
    os.umask(0o077)
    p=argparse.ArgumentParser();p.add_argument('mode',choices=['prepare','rehearse','apply','verify']);p.add_argument('--sha256');a=p.parse_args()
    require(environment()=='test','Wrong environment')
    if a.mode=='prepare': prepare()
    else:
        m=read(ROOT/'manifest.json.gz')
        require(m['writerSha256']==hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),'Writer changed after preparation')
        if a.mode=='rehearse':rehearse(m)
        elif a.mode=='apply':apply(m,a.sha256)
        else:verify(m)
