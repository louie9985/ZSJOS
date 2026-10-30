# -*- coding: utf-8 -*-
"""Retained server-copy rehearsal. There is deliberately no application-write mode."""
import argparse
from collections import Counter, defaultdict
import hashlib
from pathlib import Path
import re

from server_target import ServerTarget, read, save, require, digest
from group_repair import prepare_group, apply_group, restore_group, capture, generated_column, alive
from repair import LIVE, helpers

MIGRATION = Path(__file__).resolve().parents[2] / 'migrations/V355__person_historical_leads.sql'


def execute_script(conn, sql):
    delimiter=';';pending=''
    with conn.cursor() as q:
        for line in sql.splitlines():
            if line.strip().upper().startswith('DELIMITER '):
                require(not pending.strip(), 'Unexpected delimiter with pending SQL')
                delimiter=line.strip().split()[1];continue
            if not pending and (not line.strip() or line.lstrip().startswith('--')):continue
            pending+=line+'\n'
            if pending.rstrip().endswith(delimiter):
                q.execute(pending.rstrip()[:-len(delimiter)])
                while q.nextset():pass
                pending=''
        require(not pending.strip(), 'Incomplete SQL script')


def table_rows(conn, table, meta):
    cols={c['c'] for c in meta if c['t']==table}
    order=' ORDER BY id' if 'id' in cols else (' ORDER BY ID_' if 'ID_' in cols else '')
    with conn.cursor() as q:
        q.execute(f'SELECT * FROM `{table}`'+order);rows=list(q.fetchall())
    if not order:rows.sort(key=digest)
    return rows


def restore_copy(target, snapshot, audit):
    db=target.create_isolated();conn=target.connect_isolated(db)
    try:
        with conn.cursor() as q:
            q.execute('SET FOREIGN_KEY_CHECKS=0')
            for table,ddl in audit['schemas'].items():q.execute(ddl)
            for table in audit['tables']:
                rows=read(snapshot/(table+'.json.gz'))
                require(digest(rows)==audit['tables'][table]['fingerprint'], 'Snapshot file changed')
                fields=[c['c'] for c in audit['metadata'] if c['t']==table and not generated_column(c['e'])]
                sql=f'INSERT INTO `{table}` ('+','.join('`'+f+'`' for f in fields)+') VALUES ('+','.join(['%s']*len(fields))+')'
                for offset in range(0,len(rows),200):
                    q.executemany(sql,[[r[f] for f in fields] for r in rows[offset:offset+200]])
            q.execute('SET FOREIGN_KEY_CHECKS=1')
        conn.commit()
        for table,record in audit['tables'].items():
            require(digest(table_rows(conn,table,audit['metadata']))==record['fingerprint'], 'Copied rows differ: '+table)
        conn.rollback()
        return db,conn
    except Exception:conn.close();raise


def state_hash(conn,plan):
    with conn.cursor() as q:state,_=capture(q,plan['memberIds'],plan['key'])
    conn.rollback();return helpers.digest(state)


def verify_identity_paths(conn, plans):
    aliases=links=scopes=0
    with conn.cursor() as q:
        for plan in plans:
            target=plan['targetPersonId']
            q.execute('SELECT HEX(name) h FROM zsjos_person WHERE tenant_id=1 AND id=%s',(target,))
            canonical=plan['rule']['canonicalName'] or next(p['name'] for p in plan['before']['zsjos_person'] if p['id']==target)
            require(q.fetchone()['h']==canonical.encode('utf-8').hex().upper(),'Canonical name persisted with different UTF-8 bytes')
            for person in plan['before']['zsjos_person']:
                for field,kind in [('mobile','mobile'),('wechat_id','wechat')]:
                    value=(person.get(field) or '').strip()
                    if not value:continue
                    q.execute("SELECT person_id FROM zsjos_person_contact_alias WHERE tenant_id=1 AND source_person_id=%s AND contact_type=%s AND CAST(contact_value AS BINARY)=CAST(%s AS BINARY) AND deleted=0",
                              (person['id'],kind,value))
                    require([r['person_id'] for r in q.fetchall()]==[target],'Original contact provenance/recognition lost')
                    q.execute("SELECT p.id FROM zsjos_person p WHERE p.tenant_id=1 AND p.deleted=0 AND (CAST(p.mobile AS BINARY)=CAST(%s AS BINARY) OR LOWER(p.wechat_id)=LOWER(%s) OR EXISTS (SELECT 1 FROM zsjos_person_contact_alias ca WHERE ca.tenant_id=p.tenant_id AND ca.person_id=p.id AND ca.deleted=0 AND LOWER(ca.contact_value)=LOWER(%s)))",(value,value,value))
                    require([r['id'] for r in q.fetchall()]==[target],'Contact candidate remains ambiguous')
                    aliases+=1
                if person['id']!=target:
                    q.execute("SELECT e.to_person_id FROM zsjos_person_merge_event e JOIN zsjos_person p ON p.id=e.from_person_id AND p.tenant_id=e.tenant_id AND p.deleted=1 WHERE e.tenant_id=1 AND e.from_person_id=%s AND e.event_type='identity_unification' AND e.deleted=0 ORDER BY e.id DESC LIMIT 1",(person['id'],))
                    require(q.fetchone()['to_person_id']==target,'Historical link mapping lost')
                    links+=1
            for table in ('zsjos_lead','zsjos_order'):
                for row in plan['before'].get(table,[]):
                    q.execute(f'SELECT person_id,identity_origin_person_id FROM {table} WHERE tenant_id=1 AND id=%s',(row['id'],))
                    current=q.fetchone()
                    require(current['person_id']==target and current['identity_origin_person_id']==(row['identity_origin_person_id'] if row['identity_origin_person_id'] is not None else row['person_id']), 'Original authorization scope lost')
                    scopes+=1
        q.execute('SELECT COUNT(*) n FROM zsjos_person_contact_alias WHERE tenant_id<>1')
        require(q.fetchone()['n']==0,'Repair wrote another tenant')
    conn.rollback()
    return {'originalContactChecks':aliases,'oldLinkMappings':links,'originalAuthorizationScopes':scopes}


def run(target,snapshot,output):
    output.mkdir(mode=0o700,parents=True,exist_ok=False)
    audit=read(snapshot/'audit.json')
    require(audit['target']==target.binding,'Snapshot belongs to another server')
    db,conn=restore_copy(target,snapshot,audit)
    save(output/'copy.json',{'database':db,'sourceTarget':target.binding,'snapshotSha256':hashlib.sha256((snapshot/'audit.json').read_bytes()).hexdigest()})
    results=[];plans=[];changes=Counter();errors=[]
    try:
        execute_script(conn,MIGRATION.read_text(encoding='utf-8'))
        execute_script(conn,MIGRATION.read_text(encoding='utf-8'))
        with conn.cursor() as q:
            q.execute("SELECT HEX(description) h FROM zsjos_schema_version WHERE version='V355'")
            require(q.fetchone()['h']=='客户历史多客资'.encode().hex().upper(),'Migration Chinese bytes differ')
        before={t:digest(table_rows(conn,t,audit['metadata'])) for t in audit['tables']}
        conn.rollback()
        # Freeze every candidate first. Unresolved groups do not block other preparations.
        for group in audit['groups']:
            if group['status']!='READY':continue
            try:
                plan=prepare_group(conn,db,group['memberIds'],group.get('confirmation'))
                plan['serverTarget']=target.binding
                plan['serverGroupKey']=group['groupKey']
                plan['personNos']=group['personNos']
                apply_group(conn,plan,execute=False)
                require(state_hash(conn,plan)==plan['fingerprint'],'Dry run changed state')
                try:apply_group(conn,plan,execute=True,fail_after='zsjos_lead')
                except ValueError as e:require(str(e)=='Injected group failure','Unexpected failure injection result')
                else:raise ValueError('Failure injection did not fail')
                require(state_hash(conn,plan)==plan['fingerprint'],'Failed transaction left partial data')
                save(output/(group['groupKey']+'-before.json.gz'),plan)
                plans.append(plan)
            except Exception as e:
                # Never log SQL errors that may contain a duplicate contact or personal value.
                errors.append({'groupKey':group['groupKey'],'errorType':type(e).__name__})
                save(output/(group['groupKey']+'-error.json'),{'error':str(e)})
        save(output/'preparation.json',{'prepared':len(plans),'blocked':errors})
        for plan in plans:
            # Exercise recovery from persisted evidence, including bytes/decimal/datetime types.
            plan=read(output/(plan['serverGroupKey']+'-before.json.gz'))
            result=apply_group(conn,plan,execute=True,before_commit=lambda r,p=plan:save(output/(p['serverGroupKey']+'-after.json.gz'),r))
            require(apply_group(conn,plan,execute=True)['replay'],'Replay failed')
            changes.update(result['changes']);results.append((plan,result))
        identity_checks=verify_identity_paths(conn,plans)
        mutable=set(LIVE)|{'zsjos_person','zsjos_person_contact_claim','zsjos_person_merge_event'}
        protected=[t for t in before if t not in mutable]
        for table in protected:
            require(digest(table_rows(conn,table,audit['metadata']))==before[table],'Protected history/task/permission changed: '+table)
        # Deferred/review Persons and their owning references must remain identical.
        for group in audit['groups']:
            if group['status']=='READY':continue
            for table,fields in {'zsjos_person':['id'],**LIVE,'zsjos_person_contact_claim':['person_id']}.items():
                if table not in audit['tables']:continue
                original=read(snapshot/(table+'.json.gz'))
                selected=lambda rows:[r for r in rows if any(r.get(f) in group['memberIds'] for f in fields)]
                actual=table_rows(conn,table,audit['metadata'])
                # V355 adds nullable provenance columns without changing old values.
                old=selected(original);new=selected(actual)
                new=[{k:v for k,v in r.items() if k in (old[0] if old else r)} for r in new]
                require(digest(old)==digest(new),'Deferred/review group changed')
        # After-image protection must reject any intervening update, not overwrite it.
        if results:
            plan,result=results[-1];pid=plan['targetPersonId']
            with conn.cursor() as q:
                q.execute('SELECT name,update_time FROM zsjos_person WHERE tenant_id=1 AND id=%s',(pid,));saved=q.fetchone()
                q.execute("UPDATE zsjos_person SET name='isolated-recovery-guard' WHERE tenant_id=1 AND id=%s",(pid,))
            conn.commit()
            try:restore_group(conn,plan,result,execute=True)
            except ValueError as e:require(str(e)=='After-image changed; recovery refused','Unexpected recovery guard error')
            else:raise ValueError('Recovery overwrote a later edit')
            with conn.cursor() as q:q.execute('UPDATE zsjos_person SET name=%s,update_time=%s WHERE tenant_id=1 AND id=%s',(saved['name'],saved['update_time'],pid))
            conn.commit()
        for plan,result in reversed(results):
            result=read(output/(plan['serverGroupKey']+'-after.json.gz'))
            restore_group(conn,plan,result,execute=True)
        for table in before:
            require(digest(table_rows(conn,table,audit['metadata']))==before[table],'Batch recovery differs: '+table)
        with conn.cursor() as q:
            q.execute('SELECT COUNT(*) n FROM zsjos_person_contact_alias');require(q.fetchone()['n']==0 if 'zsjos_person_contact_alias' not in audit['tables'] else True,'Aliases not recovered')
        summary={'database':db,'applicationCommitted':False,'prepared':len(plans),'blocked':errors,
                 'appliedIsolated':len(results),'archivedSourcesIsolated':sum(len(p['sourcePersonIds']) for p in plans),
                 'deferred':sum(g['status']=='DEFER' for g in audit['groups']),
                 'review':sum(g['status']=='REVIEW' for g in audit['groups']),
                 'changes':dict(changes),'protectedTables':len(protected),'protectedRows':sum(audit['tables'][t]['rows'] for t in protected),
                 'failureRollback':True,'replay':True,'recovery':True,'afterEditRecoveryRejected':True,
                 'migrationFirstRepeat':True,'snapshotTables':len(audit['tables'])}
        summary.update(identity_checks)
        save(output/'summary.json',summary)
        return summary
    finally:conn.rollback();conn.close()


if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--binding',type=Path,required=True)
    p.add_argument('--snapshot',type=Path,required=True);p.add_argument('--output',type=Path,required=True)
    a=p.parse_args();print(__import__('json').dumps(run(ServerTarget(read(a.binding)),a.snapshot,a.output)))
