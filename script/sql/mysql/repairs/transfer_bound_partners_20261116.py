#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Authorized fixed student-bound Partner handover and dated Lead snapshot correction."""
import argparse
import datetime as dt
import hashlib
import json
import os
from pathlib import Path

from transfer_media_team_20261101 import connect, decoded, encoded, persist, require, id_list

MARK = 'bound-partner-transfer-20261116'
REASON = '按用户确认将已交接学员绑定兼职转交目标运营，并同步十一月十三日起客资归属快照；保留账号状态及其他历史'
SOURCE = Path('/opt/zsjos-runtime/backups/student-team-transfer-20261116/source.json')
SHA = 'f0e57c66271e7c0e8d414864b349661de3ff49a27f642d7c7350959d10742206'
OWN = 'zsjos_partner_ownership'
LOG = 'zsjos_partner_ownership_log'
LEAD = 'zsjos_lead'
EVENT = 'zsjos_business_event'
WRITE = (OWN, LOG, LEAD, EVENT)
START = dt.datetime(2026, 11, 13)
PEOPLE = []
PARTNERS = []


def read(q, sql, args=()):
    q.execute(sql, args or None)
    return list(q.fetchall())


def capture(q, lock=False):
    suffix = ' FOR UPDATE NOWAIT' if lock else ''
    people, partners = id_list(PEOPLE), id_list(PARTNERS)
    conditions = {
        OWN: '1=1', LOG: '1=1',
        LEAD: f"partner_id IN ({partners}) OR updater='transfer-lead-snapshots-20261116'",
        EVENT: f"idempotency_key LIKE '{MARK}:%' OR idempotency_key LIKE 'transfer-lead-snapshots-20261116:%'",
        'zsjos_partner_student_link': f'student_person_id IN ({people}) OR partner_id IN ({partners})',
        'zsjos_partner': f'id IN ({partners})',
        'zsjos_partner_account': f'partner_id IN ({partners})',
        'zsjos_service_relation': f'person_id IN ({people})',
        'system_users': 'id IN (20,40)', 'system_dept': 'id=1011',
    }
    state = {}
    for table, condition in conditions.items():
        cols = 'id,nickname,dept_id,status,deleted' if table == 'system_users' else '*'
        rows = read(q, f'SELECT {cols} FROM {table} WHERE tenant_id=1 AND ({condition}) ORDER BY id' + suffix)
        if table == 'zsjos_partner_account':
            # Verify complete login rows without storing passwords or credentials in evidence.
            rows = [dict(id=r['id'], fingerprint=hashlib.sha256(encoded(r).encode()).hexdigest()) for r in rows]
        state[table] = rows
    state['grants'] = read(q, '''SELECT DISTINCT m.permission FROM system_user_role ur
        JOIN system_role r ON r.id=ur.role_id AND r.tenant_id=ur.tenant_id AND r.deleted=0 AND r.status=0
        JOIN system_role_menu rm ON rm.role_id=r.id AND rm.tenant_id=ur.tenant_id AND rm.deleted=0
        JOIN system_menu m ON m.id=rm.menu_id AND m.deleted=0 AND m.status=0
        WHERE ur.tenant_id=1 AND ur.deleted=0 AND ur.user_id=40
        AND m.permission IN ('zsjos:partner:query','zsjos:partner:manage','zsjos:partner:manage-all')
        ORDER BY m.permission''')
    return state


def active(r):
    return r['deleted'] == b'\0'


def prepare(q):
    state = capture(q)
    links = [r for r in state['zsjos_partner_student_link'] if active(r) and r['status'] == 'active']
    require(len(links) == 58 and {r['partner_id'] for r in links} == set(PARTNERS)
            and all(r['student_person_id'] in PEOPLE for r in links), 'Binding scope changed')
    owners = [r for r in state[OWN] if active(r) and r['partner_id'] in PARTNERS]
    require(len(owners) == 58 and {uid:sum(r['employee_user_id']==uid for r in owners) for uid in (45,44,49,50)}
            == {45:39,44:9,49:7,50:3}, 'Ownership scope changed')
    require(len([r for r in state['zsjos_partner'] if active(r)]) == 58, 'Partner missing')
    users = {r['id']:r for r in state['system_users']}
    user, supervisor, dept = users[40], users[20], state['system_dept'][0]
    require(state['grants'] and all(active(r) and r['status']==0 for r in (user,supervisor,dept))
            and user['dept_id']==dept['id'] and dept['leader_user_id']==20, 'Target permissions/organization changed')
    require(not any(r['idempotency_key'].startswith(MARK+':') for r in state[EVENT]), 'Use original plan for replay')
    now = read(q,'SELECT NOW() t')[0]['t']
    changes = []
    for row in owners:
        fields = dict(employee_user_id=40,employee_name_snapshot=user['nickname'],assigned_at=now)
        changes.append(change(OWN,row,fields,now))
    scoped = [r for r in state[LEAD] if r['partner_id'] in PARTNERS]
    require(len(scoped)==483, 'Associated Lead population changed')
    for row in scoped:
        if not active(row) or row['submitted_at'] is None or not START <= row['submitted_at'] <= now:
            continue
        require(row['create_time']==row['submitted_at']==row['counted_at'], 'Date boundary ambiguity')
        require(row['source_type']==row['provider_owner_type']=='partner' and row['provider_owner_id']==row['partner_id']
                and row['partner_owner_user_id_snapshot']==row['contribution_user_id_snapshot']==45, 'Provider snapshot conflict')
        fields = dict(partner_owner_user_id_snapshot=40,partner_owner_name_snapshot=user['nickname'],
                      contribution_user_id_snapshot=40,contribution_user_name_snapshot=user['nickname'],
                      contribution_dept_id_snapshot=dept['id'],contribution_dept_name_snapshot=dept['name'],
                      contribution_supervisor_user_id_snapshot=20,contribution_supervisor_name_snapshot=supervisor['nickname'])
        changes.append(change(LEAD,row,fields,now))
    require(len(changes)==64, 'Recent Lead count changed')
    return dict(mark=MARK,state=state,changes=changes,frozenAt=now)


def change(table, row, fields, now):
    after = dict(row)
    after.update(fields,version=row['version']+1,updater=MARK,update_time=now)
    return dict(table=table,before=row,after=after,fields=list(fields)+['version','updater','update_time'])


def key(c):
    return f"{MARK}:{c['table']}:{c['before']['id']}"


def refs(c, log_id=None):
    old,new = c['before'],c['after']
    if c['table']==OWN:
        return dict(ownershipId=old['id'],ownershipLogId=log_id,partnerId=old['partner_id'],
                    previousEmployeeUserId=old['employee_user_id'],employeeUserId=40)
    return dict(leadNo=old['lead_no'],partnerId=old['partner_id'],cutoff=START.isoformat(),
                previousEmployeeUserId=old['contribution_user_id_snapshot'],employeeUserId=40,
                deptId=new['contribution_dept_id_snapshot'],supervisorUserId=20)


def valid_receipt(q,c,event):
    require(event['reason']==REASON and event['operator_user_id'] is None
            and event['aggregate_id']==c['before']['id'] and event['occurred_at']==c['after']['update_time'], 'Receipt mismatch')
    reference=json.loads(event['related_object_refs'])
    lid=reference.get('ownershipLogId')
    require(reference==refs(c,lid),'Receipt reference mismatch')
    if c['table']==OWN:
        logs=read(q,f'SELECT * FROM {LOG} WHERE tenant_id=1 AND id=%s',(lid,))
        require(len(logs)==1,'Missing domain log')
        log=logs[0];old,new=c['before'],c['after']
        require(log['partner_id']==old['partner_id'] and log['previous_employee_user_id']==old['employee_user_id']
                and log['previous_employee_name_snapshot']==old['employee_name_snapshot']
                and log['employee_user_id']==40 and log['employee_name_snapshot']==new['employee_name_snapshot']
                and log['action_type']=='reassign' and log['operator_user_id']==0 and log['reason']==REASON
                and log['occurred_at']==new['update_time'] and log['creator']==MARK, 'Invalid domain log')


def apply(q,plan,limit=None,fail_step=None):
    pending=[]
    for c in plan['changes']:
        rows=read(q,f"SELECT * FROM {c['table']} WHERE tenant_id=1 AND id=%s FOR UPDATE NOWAIT",(c['before']['id'],))
        events=read(q,f'SELECT * FROM {EVENT} WHERE tenant_id=1 AND idempotency_key=%s FOR UPDATE NOWAIT',(key(c),))
        require(len(rows)==1 and rows[0] in (c['before'],c['after']), 'Subsequent edit: refusing overwrite')
        if rows[0]==c['after']:
            require(len(events)==1,'Applied row lacks receipt')
            valid_receipt(q,c,events[0])
        else:
            require(not events,'Receipt/preimage conflict')
            pending.append(c)
    pending=pending if limit is None else pending[:limit]
    step=0

    def checkpoint():
        nonlocal step
        step+=1
        if step==fail_step:
            raise RuntimeError('injected_failure')

    for c in pending:
        old,new=c['before'],c['after']
        q.execute(f"UPDATE {c['table']} SET "+','.join(f'`{f}`=%s' for f in c['fields'])
                  +' WHERE tenant_id=1 AND deleted=0 AND id=%s AND version=%s',
                  [new[f] for f in c['fields']]+[old['id'],old['version']])
        require(q.rowcount==1,'Optimistic conflict')
        checkpoint()
        lid=None
        if c['table']==OWN:
            q.execute(f'''INSERT INTO {LOG}(partner_id,previous_employee_user_id,previous_employee_name_snapshot,
                employee_user_id,employee_name_snapshot,action_type,reason,operator_user_id,occurred_at,
                creator,updater,create_time,update_time,tenant_id)
                VALUES(%s,%s,%s,40,%s,'reassign',%s,0,%s,%s,%s,%s,%s,1)''',
                      (old['partner_id'],old['employee_user_id'],old['employee_name_snapshot'],new['employee_name_snapshot'],
                       REASON,new['update_time'],MARK,MARK,new['update_time'],new['update_time']))
            lid=q.lastrowid
            checkpoint()
        q.execute(f'''INSERT INTO {EVENT}(event_type,aggregate_type,aggregate_id,operator_user_id,reason,
            related_object_refs,occurred_at,idempotency_key,creator,updater,tenant_id)
            VALUES(%s,%s,%s,NULL,%s,%s,%s,%s,%s,%s,1)''',
                  ('partner_ownership_reassigned' if c['table']==OWN else 'lead_attribution_snapshot_corrected',
                   'partner_ownership' if c['table']==OWN else 'lead',old['id'],REASON,
                   json.dumps(refs(c,lid)),new['update_time'],key(c),MARK,MARK))
        checkpoint()
    return len(pending)


def verify(q,plan,state):
    before=plan['state']
    for table in before:
        if table not in WRITE:
            require(before[table]==state[table], 'Protected dependency changed: '+table)
    for table in (OWN,LEAD):
        target={c['before']['id']:c['after'] for c in plan['changes'] if c['table']==table}
        expected=[target.get(r['id'],r) for r in before[table]]
        require(state[table]==expected,'Unexpected row/field changes: '+table)
    for table,n in ((LOG,58),(EVENT,64)):
        old={r['id']:r for r in before[table]};new={r['id']:r for r in state[table]}
        require(all(new.get(k)==v for k,v in old.items()) and len(new)==len(old)+n,'Audit history/count mismatch')
    events={r['idempotency_key']:r for r in state[EVENT]}
    for c in plan['changes']:
        require(key(c) in events,'Missing event')
        valid_receipt(q,c,events[key(c)])


def rehearse(conn,q,plan):
    for table in WRITE:
        ddl=read(q,f'SHOW CREATE TABLE {table}')[0]['Create Table']
        q.execute(ddl.replace('CREATE TABLE','CREATE TEMPORARY TABLE',1))
        cols=[r['Field'] for r in read(q,f'SHOW COLUMNS FROM {table}')
              if 'GENERATED' not in r['Extra'].replace('DEFAULT_GENERATED','')]
        rows=plan['state'][table]
        if rows:
            q.executemany(f'INSERT INTO {table} ({",".join(cols)}) VALUES ({",".join(["%s"]*len(cols))})',[[r[f] for f in cols] for r in rows])
    conn.commit()
    require(capture(q)==plan['state'],'Fixture mismatch')
    for step in (1,2,3,174,175,186):
        try:
            apply(q,plan,fail_step=step)
            raise AssertionError('Failure not injected')
        except RuntimeError as exc:
            require(str(exc)=='injected_failure','Unexpected failure')
        conn.rollback()
        require(capture(q)==plan['state'],'Rollback incomplete')
    require(apply(q,plan)==64,'Initial application failed')
    verify(q,plan,capture(q))
    require(apply(q,plan)==0,'Repeat not zero')
    conn.rollback()
    require(apply(q,plan,limit=59)==59,'Partial application failed')
    conn.commit()
    require(apply(q,plan)==5,'Partial recovery failed')
    verify(q,plan,capture(q))
    conn.commit()
    last=plan['changes'][-1]
    q.execute(f"UPDATE {last['table']} SET version=version+1 WHERE tenant_id=1 AND id=%s",(last['before']['id'],))
    conflict=capture(q)
    try:
        apply(q,plan)
        raise AssertionError('Subsequent edit accepted')
    except RuntimeError as exc:
        require('Subsequent edit' in str(exc),'Unexpected conflict')
    require(capture(q)==conflict,'Conflict caused writes')
    conn.rollback()
    for table in reversed(WRITE):
        q.execute(f'DROP TEMPORARY TABLE {table}')
    conn.commit()


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--evidence-dir',type=Path,required=True)
    p.add_argument('--apply',action='store_true')
    p.add_argument('--verify-from',type=Path)
    args=p.parse_args()
    env=os.getenv('ZSJOS_AGENT_ENV')
    if env is None:
        marker=Path('/etc/zsjos/agent-environment')
        env=marker.read_text().strip() if marker.exists() else 'local'
    require(env=='test','Unexpected environment')
    require(not (args.apply and args.verify_from),'Verification is read-only')
    raw=SOURCE.read_bytes()
    require(hashlib.sha256(raw).hexdigest()==SHA,'Source manifest changed')
    source=decoded(raw.decode('utf-8'))
    PEOPLE.extend(r['person_id'] for r in source)
    os.umask(0o077)
    args.evidence_dir.mkdir(parents=True,mode=0o700,exist_ok=False)
    conn=connect('zsjos-mysql-1')
    try:
        with conn.cursor() as q:
            q.execute('SET NAMES utf8mb4')
            q.execute("SET time_zone='+08:00'")
            q.execute('SET SESSION innodb_lock_wait_timeout=10')
            if args.verify_from:
                plan=decoded((args.verify_from/'plan.json').read_text(encoding='utf-8'))
                require(plan['mark']==MARK,'Wrong plan')
                PARTNERS.extend(plan['partners'])
            else:
                links=read(q,f"SELECT partner_id FROM zsjos_partner_student_link WHERE tenant_id=1 AND deleted=0 AND status='active' AND student_person_id IN ({id_list(PEOPLE)}) ORDER BY partner_id")
                PARTNERS.extend(r['partner_id'] for r in links)
                require(len(PARTNERS)==len(set(PARTNERS))==58,'Reviewed binding count changed')
                require(not read(q,"SELECT trigger_name FROM information_schema.triggers WHERE trigger_schema=DATABASE() AND event_object_table IN ('zsjos_lead','zsjos_partner_ownership')"),'Unexpected trigger')
                plan=prepare(q)
                plan['partners']=PARTNERS[:]
                conn.rollback()
                persist(args.evidence_dir/'plan.json',plan)
                rehearse(conn,q,plan)
                persist(args.evidence_dir/'rehearsal.json',dict(initial=64,repeat=0,failureSteps=[1,2,3,174,175,186],partial=59,recovery=5,staleRefused=True))
                print('MySQL rehearsal passed: ownership58+Lead6/repeat0/six failure points/partial59+recovery5/stale refusal',flush=True)
                if not args.apply:
                    return
                require(capture(q,True)==plan['state'],'Live prerequisite changed')
                require(apply(q,plan)==64,'Live count mismatch')
                state=capture(q)
                verify(q,plan,state)
                persist(args.evidence_dir/'after-precommit.json',state)
                conn.commit()
                persist(args.evidence_dir/'commit.json',dict(committed=True,ownerships=58,leads=6,events=64,logs=58))
    finally:
        conn.rollback()
        conn.close()
    independent=connect('zsjos-mysql-1')
    try:
        with independent.cursor() as q:
            q.execute('SET NAMES utf8mb4')
            q.execute("SET time_zone='+08:00'")
            state=capture(q)
            verify(q,plan,state)
            for c in plan['changes']:
                fields=[f for f in c['fields'] if isinstance(c['after'][f],str) and f!='updater']
                if fields:
                    row=read(q,'SELECT '+','.join(f'HEX(`{f}`) AS `{f}`' for f in fields)+f" FROM {c['table']} WHERE tenant_id=1 AND id=%s",(c['before']['id'],))[0]
                    require(all(value==c['after'][f].encode().hex().upper() for f,value in row.items()),'Chinese snapshot HEX mismatch')
            for row in read(q,f'SELECT HEX(reason) h FROM {EVENT} WHERE tenant_id=1 AND idempotency_key LIKE %s',(MARK+':%',)):
                require(row['h']==REASON.encode().hex().upper(),'Audit HEX mismatch')
            require(apply(q,plan)==0,'Committed replay changed data')
            independent.rollback()
            result=dict(mode='verified-existing' if args.verify_from else 'committed',ownerships=58,leads=6,logs=58,events=64,protectedScopedLeads=477,replay=0,independentFullComparison=True,utf8Hex=True)
            persist(args.evidence_dir/'independent-after.json',state)
            persist(args.evidence_dir/'result.json',result)
            print(json.dumps(result))
    finally:
        independent.rollback()
        independent.close()


if __name__=='__main__':
    main()
