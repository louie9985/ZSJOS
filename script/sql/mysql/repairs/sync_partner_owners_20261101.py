#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Fixed, authorized test partner ownership sync; see partner-owner-sync-20261101.md."""
import argparse
import hashlib
import json
import os
from pathlib import Path

from transfer_media_team_20261101 import connect, encoded, persist, require, rows, active, id_list

MARKER = 'partner-owner-sync-20261101'
REASON = '按用户确认同步已绑定学员兼职账号运营归属；保留账号停用状态和历史记录'
OWN = 'zsjos_partner_ownership'
LOG = 'zsjos_partner_ownership_log'
EVENT = 'zsjos_business_event'
WRITE_TABLES = (OWN, LOG, EVENT)
# Person -> (bound Partner, explicitly requested operator, previously resolved media service)
TARGETS = {6005:(81,43,13),7070:(79,43,870),7069:(42,43,869),
           7041:(117,46,841),7039:(116,46,839),7071:(88,46,871),7042:(89,46,842),
           4907:(51,522,893),7797:(316,522,1132),7056:(69,522,856),
           7088:(114,59,888),7072:(102,59,872),8276:(310,59,1038)}
PERMISSIONS = "'zsjos:partner:query','zsjos:partner:manage','zsjos:partner:manage-all'"


def query(q, sql, args=()):
    return list(rows(q, sql, args))


def capture(q, lock=False):
    people = id_list(TARGETS)
    partners = id_list(v[0] for v in TARGETS.values())
    suffix = ' FOR UPDATE' if lock else ''
    conditions = {
        'zsjos_person': f'id IN ({people})',
        'system_users': 'id IN (43,46,522,59)',
        'zsjos_service_relation': f'person_id IN ({people})',
        'zsjos_partner': f'id IN ({partners})',
        'zsjos_partner_student_link': f'student_person_id IN ({people}) OR partner_id IN ({partners})',
        'zsjos_partner_account': f'partner_id IN ({partners})',
        OWN: f'partner_id IN ({partners})', LOG: f'partner_id IN ({partners})',
        EVENT: f"idempotency_key='{MARKER}'",
    }
    state = {}
    for table, condition in conditions.items():
        cols = 'id,nickname,status,deleted,tenant_id' if table == 'system_users' else '*'
        result = query(q, f'SELECT {cols} FROM {table} WHERE tenant_id=1 AND ({condition}) ORDER BY id' + suffix)
        if table == 'zsjos_partner_account':
            # Compare the entire login row without persisting passwords or other login data.
            result = [{'id':r['id'],'partner_id':r['partner_id'],'status':r['status'],
                       'deleted':r['deleted'],'fingerprint':hashlib.sha256(encoded(r).encode()).hexdigest()} for r in result]
        state[table] = result
    state['target_grants'] = query(q, f'''SELECT DISTINCT ur.user_id,m.permission FROM system_user_role ur
        JOIN system_role r ON r.id=ur.role_id AND r.tenant_id=ur.tenant_id AND r.deleted=0 AND r.status=0
        JOIN system_role_menu rm ON rm.role_id=r.id AND rm.tenant_id=ur.tenant_id AND rm.deleted=0
        JOIN system_menu m ON m.id=rm.menu_id AND m.deleted=0 AND m.status=0
        WHERE ur.tenant_id=1 AND ur.deleted=0 AND ur.user_id IN (43,46,522,59)
        AND m.permission IN ({PERMISSIONS}) ORDER BY ur.user_id,m.permission''')
    return state


def preflight(state):
    users = {r['id']:r for r in state['system_users'] if active(r) and r['status'] == 0}
    require(set(users) == {43,46,522,59}, 'Target employee unavailable')
    require({r['user_id'] for r in state['target_grants']} == set(users), 'Target configured permissions changed')
    services = {r['id']:r for r in state['zsjos_service_relation'] if active(r)}
    partners = {r['id']:r for r in state['zsjos_partner'] if active(r)}
    links = [r for r in state['zsjos_partner_student_link'] if active(r) and r['status'] == 'active']
    owners = {r['partner_id']:r for r in state[OWN]}
    require(len(links) == 13 and len(partners) == 13 and len(owners) == 12, 'Binding/ownership scope changed')
    for person, (partner, operator, service) in TARGETS.items():
        require(len([r for r in links if r['student_person_id'] == person and r['partner_id'] == partner]) == 1,
                'Explicit student binding changed')
        require(services[service]['person_id'] == person and services[service]['operator_user_id'] == operator
                and services[service]['content_director_user_id'] == 31, 'Media-service assignment changed')
        if partner != 88:
            require(active(owners[partner]) and owners[partner]['employee_user_id'] == operator,
                    'Previously correct owner changed')
    require(88 not in owners and not any(r['partner_id'] == 88 for r in state[LOG]), 'New target ownership/history found')
    require(partners[88]['status'] == 'disabled', 'Partner status changed')
    account = [r for r in state['zsjos_partner_account'] if r['partner_id'] == 88 and active(r)]
    require(len(account) == 1 and account[0]['status'] == 1, 'Disabled login prerequisite changed')
    require(not state[EVENT], 'Existing receipt; use original evidence')
    return users[46]['nickname']


def verify(before, after):
    for table in before:
        if table not in WRITE_TABLES:
            require(before[table] == after[table], 'Protected dependency changed: ' + table)
    for table in (OWN,LOG):
        old = {r['id']:r for r in before[table]}
        current = {r['id']:r for r in after[table]}
        require(all(current.get(k) == v for k,v in old.items()), 'Existing ownership/history changed')
        require(len(current) == len(old)+1, 'Unexpected insert count')
    own = next(r for r in after[OWN] if r['partner_id'] == 88)
    log = next(r for r in after[LOG] if r['partner_id'] == 88)
    require(len(after[EVENT]) == 1, 'Missing system receipt')
    event = after[EVENT][0]
    name = next(r['nickname'] for r in before['system_users'] if r['id'] == 46)
    when = event['occurred_at']
    for row in (own,log):
        require(row['employee_user_id'] == 46 and row['employee_name_snapshot'] == name
                and row['creator'] == MARKER and row['updater'] == MARKER and active(row)
                and row['tenant_id'] == 1 and row['create_time'] == when and row['update_time'] == when,
                'Inserted record differs from expected result')
    require(own['version'] == 0 and own['assigned_at'] == when, 'Ownership metadata mismatch')
    require(log['action_type'] == 'assign' and log['operator_user_id'] == 0 and log['reason'] == REASON
            and log['previous_employee_user_id'] is None and log['previous_employee_name_snapshot'] is None
            and log['occurred_at'] == when, 'Ownership audit mismatch')
    require(event['operator_user_id'] is None and event['reason'] == REASON and event['aggregate_id'] == 88
            and event['event_type'] == 'partner_ownership_assigned' and event['idempotency_key'] == MARKER,
            'Invalid system event')
    require(json.loads(event['related_object_refs']) == {'studentPersonId':7071,'employeeUserId':46,
            'ownershipId':own['id'],'ownershipLogId':log['id']}, 'Receipt references mismatch')


def apply(q, before, fail_after=None):
    current = capture(q, lock=True)
    if current[EVENT]:
        verify(before, current)
        return 0
    require(current == before, 'Stale or partially applied prerequisite; refusing overwrite')
    name = preflight(current)
    now = query(q,'SELECT NOW() value')[0]['value']
    q.execute(f'''INSERT INTO {OWN} (partner_id,employee_user_id,employee_name_snapshot,assigned_at,version,
        creator,updater,create_time,update_time,tenant_id) VALUES (88,46,%s,%s,0,%s,%s,%s,%s,1)''',
        (name,now,MARKER,MARKER,now,now));oid=q.lastrowid
    if fail_after == 1: raise RuntimeError('injected_failure')
    # The domain log requires an integer executor. Zero is the documented maintenance actor, never an employee.
    q.execute(f'''INSERT INTO {LOG} (partner_id,employee_user_id,employee_name_snapshot,action_type,reason,
        operator_user_id,occurred_at,creator,updater,create_time,update_time,tenant_id)
        VALUES (88,46,%s,'assign',%s,0,%s,%s,%s,%s,%s,1)''',(name,REASON,now,MARKER,MARKER,now,now));lid=q.lastrowid
    if fail_after == 2: raise RuntimeError('injected_failure')
    refs=json.dumps({'studentPersonId':7071,'employeeUserId':46,'ownershipId':oid,'ownershipLogId':lid})
    q.execute(f'''INSERT INTO {EVENT} (event_type,aggregate_type,aggregate_id,operator_user_id,reason,
        related_object_refs,occurred_at,idempotency_key,creator,updater,tenant_id)
        VALUES ('partner_ownership_assigned','partner',88,NULL,%s,%s,%s,%s,%s,%s,1)''',
        (REASON,refs,now,MARKER,MARKER,MARKER))
    if fail_after == 3: raise RuntimeError('injected_failure')
    verify(before,capture(q))
    return 1


def rehearse(c,q,before):
    for table in WRITE_TABLES:
        ddl=query(q,f'SHOW CREATE TABLE {table}')[0]['Create Table']
        q.execute(ddl.replace('CREATE TABLE','CREATE TEMPORARY TABLE',1))
        cols=[x['Field'] for x in query(q,f'SHOW COLUMNS FROM {table}')
              if 'GENERATED' not in x['Extra'].replace('DEFAULT_GENERATED','')]
        if before[table]:
            q.executemany(f'INSERT INTO {table} ({",".join(cols)}) VALUES ({",".join(["%s"]*len(cols))})',
                          [[r[k] for k in cols] for r in before[table]])
    c.commit()
    require(capture(q)==before,'Fixture prerequisite mismatch')
    for failure in (1,2,3):
        try:
            apply(q,before,fail_after=failure)
            raise AssertionError('Expected failure')
        except RuntimeError as exc:
            require(str(exc)=='injected_failure','Unexpected fixture error')
        c.rollback();require(capture(q)==before,'Failure did not fully roll back')
    require(apply(q,before)==1,'First apply failed');after=capture(q)
    require(apply(q,before)==0 and capture(q)==after,'Repeat changed data')
    c.commit()
    q.execute(f'UPDATE {OWN} SET version=version+1 WHERE tenant_id=1 AND partner_id=88')
    try:
        apply(q,before);raise AssertionError('Later edit accepted')
    except RuntimeError:pass
    c.rollback()
    q.execute(f'DELETE FROM {EVENT} WHERE idempotency_key=%s',(MARKER,))
    try:
        apply(q,before);raise AssertionError('Partial state accepted')
    except RuntimeError as exc:
        require('partially applied' in str(exc),'Unexpected partial-state error')
    c.rollback()
    for table in reversed(WRITE_TABLES):q.execute(f'DROP TEMPORARY TABLE {table}')
    c.commit()


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--evidence-dir',type=Path,required=True)
    p.add_argument('--apply',action='store_true');a=p.parse_args()
    env=os.getenv('ZSJOS_AGENT_ENV')
    if env is None:env=Path('/etc/zsjos/agent-environment').read_text().strip() if Path('/etc/zsjos/agent-environment').exists() else 'local'
    require(env=='test','Only confirmed test target is supported')
    os.umask(0o077);a.evidence_dir.mkdir(mode=0o700,parents=True,exist_ok=False)
    c=connect('zsjos-mysql-1')
    try:
        with c.cursor() as q:
            q.execute('SET NAMES utf8mb4');q.execute("SET time_zone='+08:00'");q.execute('SET SESSION innodb_lock_wait_timeout=10')
            before=capture(q);preflight(before);c.rollback()
            persist(a.evidence_dir/'before.json',before)
            persist(a.evidence_dir/'plan.json',{'partnerId':88,'studentPersonId':7071,'targetOperator':46,
                    'newOwnerships':1,'unchangedExistingOwnerships':12,'preserveDisabledStatus':True})
            rehearse(c,q,before);print('MySQL fixture passed: initial/repeat/rollback after all three inserts/stale and partial-state rejection',flush=True)
            if not a.apply:
                persist(a.evidence_dir/'result.json',{'mode':'verified-only'});return
            changed=apply(q,before);after=capture(q);verify(before,after)
            persist(a.evidence_dir/'after.json',after);c.commit()
            require(apply(q,before)==0,'Committed replay changed data');c.rollback()
            report={'mode':'committed','newOwnerships':changed,'newOwnershipLogs':1,'newBusinessEvents':1,
                    'unchangedExistingOwnerships':12,'disabledStatusPreserved':True,'replayZeroUpdates':True}
            persist(a.evidence_dir/'result.json',report);print(json.dumps(report))
    finally:c.rollback();c.close()


if __name__=='__main__':main()
