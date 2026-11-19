#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Fixed, explicitly authorized attribution exception; see transfer-lead-snapshots-20261116.md."""
import argparse
import copy
import datetime as dt
import hashlib
import json
import os
from pathlib import Path

import sync_partner_month_attribution_20261106 as writer
from transfer_media_team_20261101 import connect, decoded, encoded, persist, require, id_list

MARK = 'transfer-lead-snapshots-20261116'
REASON = '按用户确认同步指定兼职自十一月十三日起客资的员工及组织归属快照；保留来源主体和销售教务归属'
TABLE = 'zsjos_lead'
EVENT = 'zsjos_business_event'
START = dt.datetime(2026, 11, 13)
SOURCE = Path('/opt/zsjos-runtime/backups/partner-reassign-20261116/plan.json')
FIELDS = ('partner_owner_user_id_snapshot', 'partner_owner_name_snapshot',
          'contribution_user_id_snapshot', 'contribution_user_name_snapshot',
          'contribution_dept_id_snapshot', 'contribution_dept_name_snapshot',
          'contribution_supervisor_user_id_snapshot', 'contribution_supervisor_name_snapshot',
          'version', 'updater', 'update_time')
writer.FIELDS = FIELDS
PARTNERS = []


def read(q, sql, args=()):
    q.execute(sql, args or None)
    return list(q.fetchall())


def capture(q, lock=False):
    suffix = ' FOR UPDATE NOWAIT' if lock else ''
    partners = id_list(PARTNERS)
    queries = {
        'owners': f'SELECT * FROM zsjos_partner_ownership WHERE tenant_id=1 AND partner_id IN ({partners}) ORDER BY id',
        'logs': "SELECT * FROM zsjos_partner_ownership_log WHERE tenant_id=1 AND creator='partner-reassign-20261116' ORDER BY id",
        'partners': f'SELECT id,status,version,deleted,update_time FROM zsjos_partner WHERE tenant_id=1 AND id IN ({partners}) ORDER BY id',
        'users': 'SELECT id,nickname,dept_id,status,deleted FROM system_users WHERE tenant_id=1 AND id IN (31,20) ORDER BY id',
        'dept': 'SELECT id,name,leader_user_id,status,deleted FROM system_dept WHERE tenant_id=1 AND id=1011',
        'leads': f'SELECT * FROM {TABLE} WHERE tenant_id=1 AND partner_id IN ({partners}) ORDER BY id',
        'events': f"SELECT * FROM {EVENT} WHERE tenant_id=1 AND idempotency_key LIKE '{MARK}:%' ORDER BY id",
    }
    return {key: read(q, sql + suffix) for key, sql in queries.items()}


def prepare(q):
    state = capture(q)
    require(len(state['logs']) == len(state['owners']) == len(PARTNERS) == 38, 'Partner scope changed')
    require(all(r['deleted'] == b'\0' and r['employee_user_id'] == 31 for r in state['owners']), 'Current Partner owner changed')
    require({r['partner_id'] for r in state['logs']} == set(PARTNERS)
            and all(r['action_type'] == 'reassign' and r['employee_user_id'] == 31 for r in state['logs']), 'Transfer evidence changed')
    users = {r['id']: r for r in state['users']}
    target, supervisor, dept = users[31], users[20], state['dept'][0]
    require(all(r['deleted'] == b'\0' and r['status'] == 0 for r in (target, supervisor, dept))
            and target['dept_id'] == dept['id'] and dept['leader_user_id'] == supervisor['id'], 'Target organization unavailable')
    require(all(r['employee_name_snapshot'] == target['nickname'] for r in state['owners']), 'Target name changed')
    require(not state['events'], 'Existing receipt: use original plan')
    now = read(q, 'SELECT NOW() t')[0]['t']
    changes = []
    for lead in state['leads']:
        if lead['deleted'] != b'\0' or lead['submitted_at'] is None or not START <= lead['submitted_at'] <= now:
            continue
        require(lead['create_time'] == lead['submitted_at'] == lead['counted_at'], 'Date boundary ambiguity')
        require(lead['source_type'] == lead['provider_owner_type'] == 'partner'
                and lead['provider_owner_id'] == lead['partner_id'], 'Provider identity mismatch')
        require(lead['partner_owner_user_id_snapshot'] == lead['contribution_user_id_snapshot']
                and lead['contribution_user_id_snapshot'] in (59,522), 'Unexpected original owner')
        after = copy.deepcopy(lead)
        after.update(partner_owner_user_id_snapshot=31, partner_owner_name_snapshot=target['nickname'],
                     contribution_user_id_snapshot=31, contribution_user_name_snapshot=target['nickname'],
                     contribution_dept_id_snapshot=dept['id'], contribution_dept_name_snapshot=dept['name'],
                     contribution_supervisor_user_id_snapshot=supervisor['id'],
                     contribution_supervisor_name_snapshot=supervisor['nickname'],
                     version=lead['version']+1, updater=MARK, update_time=now)
        changes.append(dict(before=lead, after=after))
    require(len(changes) == 6 and len(state['leads']) == 561, 'Reviewed Lead scope changed')
    return dict(mark=MARK, start=START, frozenAt=now, state=state, changes=changes)


def verify(before, after, plan):
    old = {r['id']: r for r in before['leads']}
    expected = {r['before']['id']: r['after'] for r in plan['changes']}
    require(len(old) == len(after['leads']), 'Lead population changed')
    require(all(r == expected.get(r['id'], old[r['id']]) for r in after['leads']), 'Lead fields/history changed')
    for key in before:
        if key not in ('leads', 'events'):
            require(before[key] == after[key], 'Protected prerequisite changed: ' + key)
    require(len(after['events']) == len(plan['changes']), 'Incomplete audit')
    events = {r['idempotency_key']: r for r in after['events']}
    for change in plan['changes']:
        lead = change['after']
        event = events.get(f"{MARK}:{lead['id']}")
        require(event is not None and event['aggregate_id'] == lead['id']
                and event['aggregate_type'] == 'lead' and event['event_type'] == 'lead_attribution_snapshot_corrected'
                and event['operator_user_id'] is None and event['reason'] == REASON
                and event['occurred_at'] == lead['update_time'], 'Audit mismatch')
        require(json.loads(event['related_object_refs']) == refs(change), 'Audit references mismatch')


def refs(change):
    old, new = change['before'], change['after']
    return dict(leadNo=new['lead_no'], partnerId=new['partner_id'], cutoff=START.isoformat(),
                oldEmployeeUserId=old['contribution_user_id_snapshot'], employeeUserId=31,
                oldDeptId=old['contribution_dept_id_snapshot'], deptId=new['contribution_dept_id_snapshot'],
                oldSupervisorUserId=old['contribution_supervisor_user_id_snapshot'],
                supervisorUserId=new['contribution_supervisor_user_id_snapshot'])


def apply(q, plan, limit=None, fail_update=None, fail_event=None):
    current = {r['id']: r for r in writer.current(q, plan, TABLE)}
    pending = []
    for change in plan['changes']:
        old, new = change['before'], change['after']
        found = read(q, f'SELECT * FROM {EVENT} WHERE tenant_id=1 AND idempotency_key=%s FOR UPDATE NOWAIT', (f"{MARK}:{old['id']}",))
        require(current[old['id']] in (old, new), 'Subsequent Lead edit: refusing overwrite')
        if current[old['id']] == new:
            require(len(found) == 1 and found[0]['operator_user_id'] is None and found[0]['reason'] == REASON
                    and json.loads(found[0]['related_object_refs']) == refs(change), 'Applied row lacks valid receipt')
        else:
            require(not found, 'Receipt/preimage conflict')
            pending.append(change)
    pending = pending if limit is None else pending[:limit]
    count = writer.execute(q, plan, TABLE, limit=limit, fail_at=fail_update)
    require(count == len(pending), 'Writer count mismatch')
    for index, change in enumerate(pending, 1):
        lead = change['after']
        q.execute(f'''INSERT INTO {EVENT}(event_type,aggregate_type,aggregate_id,operator_user_id,reason,
            related_object_refs,occurred_at,idempotency_key,creator,updater,tenant_id)
            VALUES('lead_attribution_snapshot_corrected','lead',%s,NULL,%s,%s,%s,%s,%s,%s,1)''',
                  (lead['id'],REASON,json.dumps(refs(change)),lead['update_time'],f"{MARK}:{lead['id']}",MARK,MARK))
        if index == fail_event:
            raise RuntimeError('injected_failure')
    return count


def rehearse(c, q, plan):
    # Connection-local copies exercise the real writer without touching business rows.
    for table, rows in ((TABLE,plan['state']['leads']), (EVENT,[])):
        ddl = read(q, f'SHOW CREATE TABLE {table}')[0]['Create Table']
        q.execute(ddl.replace('CREATE TABLE', 'CREATE TEMPORARY TABLE', 1))
        columns = [r['Field'] for r in read(q, f'SHOW COLUMNS FROM {table}')
                   if 'GENERATED' not in r['Extra'].replace('DEFAULT_GENERATED','')]
        if rows:
            q.executemany(f'INSERT INTO {table} ({",".join(columns)}) VALUES ({",".join(["%s"]*len(columns))})', [[r[k] for k in columns] for r in rows])
    c.commit()
    before = plan['state']
    require(capture(q) == before, 'Temporary fixture mismatch')
    for failure in ({'fail_update':1},{'fail_update':6},{'fail_event':1},{'fail_event':6}):
        try:
            apply(q,plan,**failure)
            raise AssertionError('Missing injected failure')
        except RuntimeError as exc:
            require(str(exc) == 'injected_failure', 'Unexpected fixture error')
        c.rollback()
        require(capture(q) == before, 'Rollback did not restore complete state')
    require(apply(q,plan) == 6, 'Initial write failed')
    verify(before,capture(q),plan)
    require(apply(q,plan) == 0, 'Repeat not zero')
    c.rollback()
    require(apply(q,plan,limit=2) == 2, 'Partial application failed')
    c.commit()
    require(apply(q,plan) == 4, 'Partial recovery failed')
    verify(before,capture(q),plan)
    c.commit()
    q.execute(f'UPDATE {TABLE} SET version=version+1 WHERE tenant_id=1 AND id=%s',(plan['changes'][-1]['before']['id'],))
    conflict=capture(q)
    try:
        apply(q,plan)
        raise AssertionError('Subsequent edit accepted')
    except RuntimeError as exc:
        require('Subsequent Lead edit' in str(exc), 'Unexpected conflict')
    require(capture(q) == conflict, 'Conflict caused writes')
    c.rollback()
    q.execute(f'DROP TEMPORARY TABLE {EVENT}')
    q.execute(f'DROP TEMPORARY TABLE {TABLE}')
    c.commit()


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
    require(env == 'test', 'Only authorized test environment supported')
    require(not (args.apply and args.verify_from), 'Verification is read-only')
    original=decoded(SOURCE.read_text(encoding='utf-8'))
    require(len(original)==38, 'Original Partner transfer manifest changed')
    PARTNERS.extend(r['partner_id'] for r in original)
    os.umask(0o077)
    args.evidence_dir.mkdir(parents=True,mode=0o700,exist_ok=False)
    c=connect('zsjos-mysql-1')
    try:
        with c.cursor() as q:
            q.execute('SET NAMES utf8mb4')
            q.execute("SET time_zone='+08:00'")
            require(not read(q,"SELECT trigger_name FROM information_schema.triggers WHERE trigger_schema=DATABASE() AND event_object_table='zsjos_lead'"),'Unexpected Lead trigger')
            if args.verify_from:
                plan=decoded((args.verify_from/'plan.json').read_text(encoding='utf-8'))
                require(plan['mark']==MARK, 'Wrong plan')
            else:
                plan=prepare(q)
                c.rollback()
                persist(args.evidence_dir/'plan.json',plan)
                rehearse(c,q,plan)
                persist(args.evidence_dir/'rehearsal.json',dict(initial=6,repeat=0,rollbackCases=4,partial=2,recovery=4,conflictRefused=True,planSha256=hashlib.sha256(encoded(plan).encode()).hexdigest()))
                print('MySQL rehearsal passed: initial6/repeat0/rollback4/partial2+recovery4/conflict refusal',flush=True)
                if not args.apply:
                    return
                require(capture(q,lock=True)==plan['state'],'Live prerequisite changed')
                count=apply(q,plan)
                after=capture(q)
                verify(plan['state'],after,plan)
                persist(args.evidence_dir/'after-precommit.json',after)
                c.commit()
                persist(args.evidence_dir/'commit.json',dict(committed=True,changed=count))
    finally:
        c.rollback()
        c.close()
    independent=connect('zsjos-mysql-1')
    try:
        with independent.cursor() as q:
            q.execute('SET NAMES utf8mb4')
            q.execute("SET time_zone='+08:00'")
            after=capture(q)
            verify(plan['state'],after,plan)
            for change in plan['changes']:
                row=read(q,'SELECT '+','.join(f'HEX({field}) AS `{field}`' for field in FIELDS if field.endswith('_snapshot') and isinstance(change['after'][field],str))+' FROM zsjos_lead WHERE tenant_id=1 AND id=%s',(change['after']['id'],))[0]
                require(all(value==change['after'][field].encode().hex().upper() for field,value in row.items()),'Snapshot Chinese HEX mismatch')
            for row in read(q,f'SELECT HEX(reason) h FROM {EVENT} WHERE tenant_id=1 AND idempotency_key LIKE %s',(MARK+':%',)):
                require(row['h']==REASON.encode().hex().upper(),'Audit Chinese HEX mismatch')
            require(apply(q,plan)==0,'Committed replay changed data')
            independent.rollback()
            persist(args.evidence_dir/'independent-after.json',after)
            result=dict(mode='verified-existing' if args.verify_from else 'committed',leadUpdates=6,systemEvents=6,protectedLeads=555,replay=0,fullRowComparison=True,utf8Hex=True)
            persist(args.evidence_dir/'result.json',result)
            print(json.dumps(result))
    finally:
        independent.rollback()
        independent.close()


if __name__=='__main__':
    main()
