# -*- coding: utf-8 -*-
"""Rehearse selected technical groups; commit/recovery runs only in a retained isolated copy."""
import argparse
import collections
import datetime
import json
from pathlib import Path
from repair import helpers,LIVE,HISTORICAL,fetch
from group_repair import prepare_group,apply_group,restore_group,capture,metadata,generated_column

def verify(audit_path,previous_path,output):
    audit=json.loads(audit_path.read_text(encoding='utf-8'))
    previous=json.loads(previous_path.read_text(encoding='utf-8'))
    selected={r['group'] for r in previous if r['status']=='technical_pending'}
    helpers.check(all(audit['groups'][i-1]['identityDecision']=='AUTO_MERGE' for i in selected),'Review groups must not enter technical execution')
    output.mkdir(parents=True,exist_ok=False)
    conn=helpers.connect('ruoyi-vue-pro');results=[]
    def protected():
        hashes={}
        for i,g in enumerate(audit['groups'],1):
            if g['identityDecision']=='REVIEW':
                with conn.cursor() as cur:state,_=capture(cur,g['personIds'],'protected-review')
                conn.rollback();hashes[i]=helpers.digest(state)
        return hashes
    try:
        protected_before=protected()
        for i in sorted(selected):
            plan=prepare_group(conn,'ruoyi-vue-pro',audit['groups'][i-1]['personIds'])
            result=apply_group(conn,plan)
            with conn.cursor() as cur:after,_=capture(cur,plan['memberIds'],plan['key'])
            conn.rollback();helpers.check(helpers.digest(after)==plan['fingerprint'],'Rehearsal changed live business rows')
            helpers.save_private(output/f'group-{i}.json',{'plan':plan,'rehearsal':result})
            results.append({'group':i,'status':'dry_run_pass','aliases':result['aliasCount'],'changes':result['changes']})
        helpers.check(protected_before==protected(),'Identity-review group changed')
        # Copy only approved identity-domain tables into a dedicated fixture; application data stays untouched.
        fixture='historical_student_verify_'+datetime.datetime.now().strftime('%Y%m%d%H%M%S%f')
        with conn.cursor() as cur:
            meta=metadata(cur);present={r['t'] for r in meta}
            tables=(set(LIVE)|HISTORICAL|{'zsjos_person','zsjos_person_contact_claim'})&present
            cur.execute(f'CREATE DATABASE `{fixture}` CHARACTER SET utf8mb4')
            for table in sorted(tables):
                columns=[r['c'] for r in meta if r['t']==table and not generated_column(r['e'])]
                fields=','.join('`'+c+'`' for c in columns)
                cur.execute(f'CREATE TABLE `{fixture}`.`{table}` LIKE `ruoyi-vue-pro`.`{table}`')
                cur.execute(f'INSERT INTO `{fixture}`.`{table}` ({fields}) SELECT {fields} FROM `ruoyi-vue-pro`.`{table}` WHERE tenant_id=1')
        conn.commit()
        isolated=helpers.connect(fixture)
        try:
            for i in sorted(selected):
                plan=prepare_group(isolated,fixture,audit['groups'][i-1]['personIds'])
                result=apply_group(isolated,plan,execute=True)
                helpers.check(apply_group(isolated,plan,execute=True)['replay'],'Committed merge was not idempotent')
                helpers.check(restore_group(isolated,plan,result,execute=True)['restored'],'Recovery failed')
        finally:isolated.close()
        summary={'results':results,'protectedReviewGroups':len(protected_before),'protectedUnchanged':True,'isolatedRecoveryGroups':len(selected),'isolatedDatabase':fixture}
        helpers.save_private(output/'summary.json',summary)
        print(json.dumps({'rehearsalGroups':len(results),'committedAndRecoveredInIsolatedCopy':len(selected),'protectedReviewGroups':len(protected_before),'applicationMerges':0,'isolatedDatabase':fixture}))
    finally:conn.close()

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--audit',type=Path,required=True);p.add_argument('--previous-summary',type=Path,required=True);p.add_argument('--output',type=Path,required=True)
    a=p.parse_args();verify(a.audit,a.previous_summary,a.output)
