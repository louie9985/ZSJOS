# -*- coding: utf-8 -*-
"""Atomic, fingerprint-bound group rehearsal and recovery. CLI defaults to rollback."""
import argparse
import hashlib
import json
import re
from pathlib import Path
from repair import helpers,fetch,LIVE,HISTORICAL
from identity_rules import classify_group,text

ALIAS='zsjos_person_contact_alias'
CLAIM='zsjos_person_contact_claim'
EVENT='zsjos_person_merge_event'

def alive(row):
    v=row.get('deleted',0)
    return not (int.from_bytes(v,'big') if isinstance(v,bytes) else v)

def generated_column(extra):
    return 'VIRTUAL GENERATED' in extra or 'STORED GENERATED' in extra

def metadata(cur):
    return fetch(cur,"SELECT table_name AS t,column_name AS c,extra AS e,data_type AS d FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name LIKE 'zsjos_%'")

def capture(cur,ids,key,lock=True):
    marks=','.join(['%s']*len(ids));suffix=' FOR UPDATE' if lock else ''
    meta=metadata(cur);tables={r['t'] for r in meta}
    helpers.check(ALIAS in tables,'Verified contact alias schema is required')
    for r in meta:
        if not (r['c']=='person_id' or r['c'].endswith('_person_id')):continue
        if generated_column(r['e']) or r['c']=='identity_origin_person_id' or '_bak_' in r['t'] or r['t'] in HISTORICAL or r['t']==CLAIM:continue
        if r['t']==ALIAS and r['c']=='source_person_id':continue
        helpers.check(r['c'] in LIVE.get(r['t'],[]),'Unknown live identity reference; review schema before repair')
    state={'zsjos_person':fetch(cur,f'SELECT * FROM zsjos_person WHERE tenant_id=1 AND id IN ({marks}) ORDER BY id'+suffix,ids)}
    for table,fields in sorted(LIVE.items()):
        if table not in tables:continue
        predicate=' OR '.join(f'`{field}` IN ({marks})' for field in fields)
        state[table]=fetch(cur,f'SELECT * FROM `{table}` WHERE tenant_id=1 AND ({predicate}) ORDER BY id'+suffix,ids*len(fields))
    values={text(p.get(k)) for p in state['zsjos_person'] for k in ('mobile','wechat_id') if text(p.get(k))}
    values|={r['contact_value'] for r in state.get(ALIAS,[]) if alive(r)}
    values=sorted(values)
    value_clause=' OR contact_value IN ('+','.join(['%s']*len(values))+')' if values else ''
    state[CLAIM]=fetch(cur,f'SELECT * FROM {CLAIM} WHERE tenant_id=1 AND (person_id IN ({marks}){value_clause}) ORDER BY id'+suffix,ids+values)
    state[EVENT]=fetch(cur,f'SELECT * FROM {EVENT} WHERE tenant_id=1 AND idempotency_key LIKE %s ORDER BY id'+suffix,(key+':%',))
    return state,meta

def classify(state):
    orders=[o for o in state.get('zsjos_order',[]) if alive(o)]
    leads=[l for l in state.get('zsjos_lead',[]) if alive(l)]
    # Classifier sees booleans, never truthy b'\x00'.
    return classify_group(state['zsjos_person'],[{**o,'deleted':False} for o in orders],[{**l,'deleted':False} for l in leads])

def confirmed_rule(state,confirmation):
    ids=sorted(p['id'] for p in state['zsjos_person'])
    helpers.check(sorted(confirmation['memberIds'])==ids,'Confirmation belongs to another group')
    helpers.check(confirmation['targetPersonId'] in ids and text(confirmation.get('canonicalName')) and text(confirmation.get('reviewReference')),'Explicit name, target and review reference required')
    return {'identityDecision':'AUTO_MERGE','identityRule':'USER_CONFIRMED_NAME',
            'canonicalName':text(confirmation['canonicalName']),'targetPersonId':confirmation['targetPersonId'],
            'reviewReference':confirmation['reviewReference'],'executionBlockers':[]}

def prepare_group(conn,database,ids,confirmation=None):
    ids=sorted(set(map(int,ids)))
    helpers.check(len(ids)>1 and min(ids)>0,'A complete multi-person group is required')
    key='person-group:'+hashlib.sha256(helpers.packed([database,ids]).encode()).hexdigest()
    conn.begin()
    try:
        with conn.cursor() as cur:
            state,_=capture(cur,ids,key)
            helpers.check(len(state['zsjos_person'])==len(ids) and all(alive(p) for p in state['zsjos_person']),'All group identities must be live')
            rule=confirmed_rule(state,confirmation) if confirmation else classify(state)
            helpers.check(rule['identityDecision']=='AUTO_MERGE','Identity review groups cannot be prepared automatically')
            return {'database':database,'memberIds':ids,'key':key,'rule':rule,'automaticIdentity':rule,
                    'targetPersonId':rule['targetPersonId'],'sourcePersonIds':[i for i in ids if i!=rule['targetPersonId']],
                    'before':state,'fingerprint':helpers.digest(state),'confirmation':confirmation}
    finally:conn.rollback()

def apply_group(conn,plan,execute=False,fail_after=None,before_commit=None):
    ids=plan['memberIds'];target=plan['rule']['targetPersonId'];sources=[i for i in ids if i!=target]
    marks=','.join(['%s']*len(sources));all_marks=','.join(['%s']*len(ids));key=plan['key']
    conn.begin()
    try:
        with conn.cursor() as cur:
            current,meta=capture(cur,ids,key)
            if current[EVENT]:
                helpers.check({r['from_person_id'] for r in current[EVENT]}==set(sources) and all(r['to_person_id']==target for r in current[EVENT]),'Incomplete merge-event group')
                helpers.check(all(not alive(p) if p['id'] in sources else alive(p) for p in current['zsjos_person']),'Replay identity state inconsistent')
                conn.rollback();return {'replay':True,'committed':False}
            helpers.check(helpers.digest(current)==plan['fingerprint'],'Group data changed; prepare again')
            current_rule=confirmed_rule(current,plan['confirmation']) if plan.get('confirmation') else classify(current)
            helpers.check(current_rule==plan['rule'],'Group identity decision changed')
            person={p['id']:p for p in current['zsjos_person']}
            aliases=set()
            for pid,p in person.items():
                for field,kind in [('mobile','mobile'),('wechat_id','wechat')]:
                    value=text(p.get(field))
                    if not value:continue
                    aliases.add((pid,kind,value))
                    # Phone-looking values in a historical WeChat field remain available in both roles.
                    if re.fullmatch(r'1[3-9]\d{9}',value):aliases.add((pid,'mobile',value))
                    elif field=='mobile':aliases.add((pid,'wechat',value))
            values=sorted({a[2] for a in aliases}|{r['contact_value'] for r in current[CLAIM]})
            for value in values:
                outsiders=fetch(cur,f'SELECT id FROM zsjos_person WHERE tenant_id=1 AND deleted=0 AND id NOT IN ({all_marks}) AND (mobile=%s OR LOWER(wechat_id)=LOWER(%s)) FOR UPDATE',ids+[value,value])
                helpers.check(not outsiders,'Contact matches an identity outside the complete group')
                outside_alias=fetch(cur,f'SELECT id FROM {ALIAS} WHERE tenant_id=1 AND deleted=0 AND person_id NOT IN ({all_marks}) AND LOWER(contact_value)=LOWER(%s) FOR UPDATE',ids+[value])
                helpers.check(not outside_alias,'Verified alias belongs to another identity')
                claims=fetch(cur,f'SELECT * FROM {CLAIM} WHERE tenant_id=1 AND LOWER(contact_value)=LOWER(%s) FOR UPDATE',(value,))
                helpers.check(all(alive(r) and r['person_id'] in ids and not r['reservation_key'] for r in claims),'Contact claim is reserved, retired or foreign')
            for table in ('zsjos_lead','zsjos_order'):
                if table in current:
                    cur.execute(f'UPDATE `{table}` SET identity_origin_person_id=person_id WHERE tenant_id=1 AND person_id IN ({all_marks}) AND identity_origin_person_id IS NULL',ids)
            changes={}
            for table,fields in sorted(LIVE.items()):
                if table not in current:continue
                for field in fields:
                    cur.execute(f'UPDATE `{table}` SET `{field}`=%s WHERE tenant_id=1 AND `{field}` IN ({marks})',[target]+sources)
                    if cur.rowcount:changes[table+'.'+field]=cur.rowcount
                if fail_after==table:raise ValueError('Injected group failure')
            for source,kind,value in sorted(aliases):
                cur.execute(f"INSERT INTO {ALIAS}(person_id,source_person_id,contact_type,contact_value,merge_key,tenant_id,creator,updater) VALUES(%s,%s,%s,%s,%s,1,'person-unify','person-unify') ON DUPLICATE KEY UPDATE person_id=VALUES(person_id)",(target,source,kind,value,key))
            for value in values:
                cur.execute(f"INSERT INTO {CLAIM}(contact_value,person_id,tenant_id,creator,updater) VALUES(%s,%s,1,'person-unify','person-unify') ON DUPLICATE KEY UPDATE person_id=VALUES(person_id)",(value,target))
            canonical={'name':plan['rule']['canonicalName'] or person[target]['name']}
            for field in ('mobile','wechat_id'):
                values_for_field={text(p.get(field)) for p in person.values() if text(p.get(field))}
                canonical[field]=person[target].get(field)
                if not text(canonical[field]) and len(values_for_field)==1:canonical[field]=next(iter(values_for_field))
            cur.execute('UPDATE zsjos_person SET name=%s,mobile=%s,wechat_id=%s WHERE tenant_id=1 AND id=%s',(canonical['name'],canonical['mobile'],canonical['wechat_id'],target))
            cur.execute(f"UPDATE zsjos_person SET deleted=b'1',updater='person-unify' WHERE tenant_id=1 AND id IN ({marks})",sources)
            for source in sources:
                cur.execute(f"INSERT INTO {EVENT}(from_person_id,to_person_id,event_type,reason,source_snapshot,occurred_at,idempotency_key,tenant_id,creator,updater) VALUES(%s,%s,'identity_unification','Confirmed group rule',%s,NOW(),%s,1,'person-unify','person-unify')",(source,target,helpers.packed({'group':ids,'rule':plan['rule']['identityRule'],'fingerprint':plan['fingerprint']}),key+':'+str(source)))
            after,_=capture(cur,ids,key)
            generated={(r['t'],r['c']) for r in meta if generated_column(r['e'])}
            for table,rows in current.items():
                indexed={r['id']:r for r in after[table]}
                for before in rows:
                    helpers.check(before['id'] in indexed,'An existing row was removed')
                    now=indexed[before['id']]
                    for field,old in before.items():
                        if (table,field) in generated or field=='update_time':continue
                        expected=old
                        if field in LIVE.get(table,[]) and old in sources:expected=target
                        if table==CLAIM and field=='person_id':expected=target
                        if table in ('zsjos_lead','zsjos_order') and field=='identity_origin_person_id' and old is None:expected=before['person_id']
                        if table=='zsjos_person':
                            if before['id']==target and field in canonical:expected=canonical[field]
                            if before['id'] in sources and field=='deleted':expected=b'\x01' if isinstance(old,bytes) else 1
                            if before['id'] in sources and field=='updater':expected='person-unify'
                        helpers.check(now[field]==expected,'Unrelated business field changed')
            result={'replay':False,'committed':execute,'changes':changes,'aliasCount':len(after[ALIAS]),'after':after,'afterFingerprint':helpers.digest(after)}
        if execute:
            if before_commit is not None:before_commit(result)
            conn.commit()
        else:conn.rollback()
        return result
    except Exception:conn.rollback();raise

def restore_group(conn,plan,result,execute=False):
    """Refuse rollback after any captured row has changed; restore only this group's rows."""
    conn.begin()
    try:
        with conn.cursor() as cur:
            now,meta=capture(cur,plan['memberIds'],plan['key'])
            helpers.check(helpers.digest(now)==result['afterFingerprint'],'After-image changed; recovery refused')
            generated={(r['t'],r['c']) for r in meta if generated_column(r['e'])}
            types={(r['t'],r['c']):r['d'] for r in meta}
            for table in reversed(list(plan['before'])):
                before=plan['before'][table];oldids={r['id'] for r in before}
                for row in now[table]:
                    if row['id'] not in oldids:
                        helpers.check(table in (ALIAS,CLAIM,EVENT),'Unexpected new business row')
                        cur.execute(f'DELETE FROM `{table}` WHERE tenant_id=1 AND id=%s',(row['id'],))
                for row in before:
                    fields=[f for f in row if f not in ('id','tenant_id') and (table,f) not in generated]
                    vals=[bytes.fromhex(row[f]) if types.get((table,f))=='bit' and isinstance(row[f],str) else row[f] for f in fields]
                    cur.execute(f'UPDATE `{table}` SET '+','.join(f'`{f}`=%s' for f in fields)+' WHERE tenant_id=1 AND id=%s',vals+[row['id']])
            restored,_=capture(cur,plan['memberIds'],plan['key'])
            if helpers.digest(restored)!=plan['fingerprint']:
                differences=[]
                for table,rows in plan['before'].items():
                    actual={r['id']:r for r in restored[table]}
                    if len(actual)!=len(rows):differences.append(table+':row-count')
                    for row in rows:
                        for field,value in row.items():
                            if actual.get(row['id'],{}).get(field)!=value:differences.append(table+'.'+field)
                raise ValueError('Recovery before-image mismatch: '+','.join(sorted(set(differences))))
        if execute:conn.commit()
        else:conn.rollback()
        return {'restored':True,'committed':execute}
    except Exception:conn.rollback();raise

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--database',required=True);parser.add_argument('--members',nargs='+',type=int,required=True);parser.add_argument('--output',type=Path,required=True)
    args=parser.parse_args();conn=helpers.connect(args.database)
    try:
        plan=prepare_group(conn,args.database,args.members);result=apply_group(conn,plan)
        helpers.save_private(args.output,{'plan':plan,'rehearsal':result})
        print(json.dumps({'committed':False,'sources':len(plan['memberIds'])-1,'changes':result['changes'],'aliases':result['aliasCount']}))
    finally:conn.close()
