# -*- coding: utf-8 -*-
"""Prepare private target-bound decisions using confirmed automatic rules or reviewed evidence."""
import argparse
import hashlib
import json
from pathlib import Path
from repair import helpers, LIVE, fetch, repair
from identity_rules import classify_group


def prepare_automatic(conn,database,member_ids):
    """Select the compatible-pair or atomic-group executor without manual identity confirmation."""
    helpers.check(len(set(member_ids))==len(member_ids) and len(member_ids)>=2,'Distinct group members required')
    if len(member_ids)>2:
        from group_repair import prepare_group
        return prepare_group(conn,database,member_ids)
    with conn.cursor() as cur:
        people=fetch(cur,'SELECT * FROM zsjos_person WHERE tenant_id=1 AND deleted=0 AND id IN (%s,%s) ORDER BY id',tuple(member_ids))
        orders=fetch(cur,'SELECT id,person_id,student_name,creator FROM zsjos_order WHERE tenant_id=1 AND deleted=0 AND person_id IN (%s,%s) ORDER BY id',tuple(member_ids))
        leads=fetch(cur,'SELECT id,person_id,status FROM zsjos_lead WHERE tenant_id=1 AND deleted=0 AND person_id IN (%s,%s) ORDER BY id',tuple(member_ids))
    conn.rollback()
    rule=classify_group(people,orders,leads)
    helpers.check(rule['identityDecision']=='AUTO_MERGE','Group contains unresolved identity contradictions')
    if rule['executionBlockers']:
        from group_repair import prepare_group
        return prepare_group(conn,database,member_ids)
    target=rule['targetPersonId'];source=next(i for i in member_ids if i!=target)
    decision=prepare(conn,database,source,target,'User-confirmed automatic identity rule: '+rule['identityRule'])
    decision['automaticIdentity']=rule
    target_row=next(p for p in people if p['id']==target)
    if (target_row['name'] or '').strip()!=rule['canonicalName']:
        decision['canonicalName']=rule['canonicalName']
    return decision


def prepare(conn,database,source,target,evidence):
    conn.begin()
    try:
        with conn.cursor() as cur:
            people=fetch(cur,'SELECT * FROM zsjos_person WHERE tenant_id=1 AND id IN (%s,%s) ORDER BY id',(source,target))
            helpers.check(len(people)==2,'Two identities required')
            existing={r['table_name'] for r in fetch(cur,'SELECT table_name AS table_name FROM information_schema.tables WHERE table_schema=DATABASE()')}
            refs={}; backup={}
            for table,fields in LIVE.items():
                if table not in existing: continue
                for field in fields:
                    rows=fetch(cur,f'SELECT * FROM `{table}` WHERE tenant_id=1 AND `{field}`=%s ORDER BY id',(source,))
                    if rows: refs[table+'.'+field]=rows
                    both=fetch(cur,f'SELECT * FROM `{table}` WHERE tenant_id=1 AND `{field}` IN (%s,%s) ORDER BY id',(source,target))
                    if both: backup[table+'.'+field]=both
            claims=fetch(cur,'SELECT * FROM zsjos_person_contact_claim WHERE tenant_id=1 AND person_id IN (%s,%s) ORDER BY id',(source,target))
            return {'database':database,'sourcePersonId':source,'targetPersonId':target,'evidence':evidence,
                    'personFingerprint':helpers.digest(people),'referenceFingerprint':helpers.digest(refs),
                    'privateBeforeImages':{'people':people,'references':backup,'claims':claims}}
    finally:
        conn.rollback()


if __name__=='__main__':
    p=argparse.ArgumentParser()
    p.add_argument('--database',required=True);p.add_argument('--source',type=int)
    p.add_argument('--target',type=int);p.add_argument('--evidence-file',type=Path)
    p.add_argument('--members',type=int,nargs='+',help='Automatic identity-rule path; no manual evidence file required')
    p.add_argument('--output',required=True,type=Path)
    args=p.parse_args();conn=helpers.connect(args.database)
    try:
        if args.members:
            helpers.check(not args.source and not args.target and not args.evidence_file,'Do not mix automatic and manual decision inputs')
            decision=prepare_automatic(conn,args.database,args.members)
        else:
            helpers.check(args.source and args.target and args.evidence_file,'Provide members or a reviewed source/target/evidence file')
            decision=prepare(conn,args.database,args.source,args.target,args.evidence_file.read_text(encoding='utf-8'))
        result=repair(conn,decision)
        helpers.save_private(args.output,decision)
        print(json.dumps({'dryRun':{k:v for k,v in result.items() if k!='after'},'sha256':hashlib.sha256(args.output.read_bytes()).hexdigest()}))
    finally: conn.close()
