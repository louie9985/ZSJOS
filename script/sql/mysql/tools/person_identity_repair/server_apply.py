# -*- coding: utf-8 -*-
"""Explicit approved server batch. Does not stop/start services or apply migrations.

CLI guards do not constitute user authorization. Read the reviewed runbook first.
V355 and the matching artifact must be staged during the approved maintenance window.
"""
import argparse
from collections import Counter
import hashlib
from pathlib import Path
import socket
import subprocess

import pymysql
from server_target import ServerTarget,read,save,require,digest
from server_audit import components
from server_rehearsal import MIGRATION,verify_identity_paths,table_rows
from group_repair import capture,apply_group,restore_group,alive
from repair import helpers,LIVE


def maintenance_check():
    state=subprocess.check_output(['systemctl','show','zsjos-backend.service','-p','ActiveState','--value']).decode().strip()
    require(state=='inactive','Approved backend service must be stopped')
    with socket.socket() as probe:
        probe.settimeout(1)
        require(probe.connect_ex(('127.0.0.1',48080))!=0,'Backend port still accepts connections')
    stopped=subprocess.check_output(['systemctl','show','zsjos-backend.service','-p','InactiveEnterTimestampMonotonic','--value']).decode().strip()
    require(stopped not in ('','0'),'Stopped service timestamp unavailable')
    return {'bootId':Path('/proc/sys/kernel/random/boot_id').read_text().strip(),'inactiveSinceMonotonic':stopped}


def verify_files(manifest,receipt):
    require(receipt['target']==manifest['target'] and receipt['consistent'] is True,
            'Consistent backup for this exact target required')
    require(receipt['maintenanceToken']==maintenance_check(),'Backup is from a different maintenance window')
    require(bool(manifest.get('toolSha256')),'Reviewed tool hashes required')
    for name,expected in manifest['toolSha256'].items():
        require(hashlib.sha256(Path(name).read_bytes()).hexdigest()==expected,'Execution tool changed after review')
    for path,expected in [(Path(manifest['artifact']),manifest['artifactSha256']),
                          (MIGRATION,manifest['migrationSha256']),
                          (Path(receipt['file']),receipt['sha256'])]:
        require(path.is_file() and hashlib.file_digest(path.open('rb'),'sha256').hexdigest()==expected,
                'Reviewed artifact, migration or backup differs')


def fresh_guard(conn,manifest):
    with conn.cursor() as q:
        q.execute('SELECT * FROM zsjos_person WHERE tenant_id=1 ORDER BY id');people=list(q.fetchall())
        q.execute('SELECT * FROM zsjos_person_contact_claim WHERE tenant_id=1 ORDER BY id');claims=list(q.fetchall())
        q.execute('SELECT * FROM zsjos_person_contact_alias WHERE tenant_id=1 ORDER BY id');aliases=list(q.fetchall())
        q.execute('SELECT * FROM zsjos_person_merge_event WHERE tenant_id=1 ORDER BY id');events=list(q.fetchall())
        groups,_=components(people,claims,aliases,events)
        current={tuple(g) for g in groups}
        for plan in manifest['plans']:
            state,_=capture(q,plan['memberIds'],plan['key'],lock=False)
            require(helpers.digest(state)==plan['fingerprint'],'Server before-image changed; rescan/review required')
            require(tuple(plan['memberIds']) in current,'Complete connected members changed')
            require(sorted(p['person_no'] for p in state['zsjos_person'])==plan['personNos'],'Stable Person numbers changed')
        for group in manifest['heldGroups']:
            require(tuple(group['memberIds']) in current,'Held group membership changed; review required')
        expected={tuple(p['memberIds']) for p in manifest['plans']}|{tuple(g['memberIds']) for g in manifest['heldGroups']}
        require(current==expected,'New conflict groups appeared; rescan before execution')
        q.execute("SELECT COUNT(*) n FROM zsjos_schema_version WHERE version='V355'")
        require(q.fetchone()['n']==1,'V355 Core ledger required')
        q.execute("SELECT COUNT(*) n FROM zsjos_module_schema_version WHERE module_code='core' AND version='V355'")
        require(q.fetchone()['n']==1,'V355 module ledger required')
        q.execute("SELECT COUNT(*) n FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name IN ('zsjos_lead','zsjos_opportunity') AND index_name='uk_tenant_active_person'")
        require(q.fetchone()['n']==0,'V355 actual uniqueness structure required')
    conn.rollback()


def connect_write(target):
    # Only called after the explicit apply flag, approval reference, maintenance,
    # backup and fresh read-only guards; no root application connection is used.
    c=target.config
    conn=pymysql.connect(host=c.host,port=c.port,user=c.user,password=c.password,database=c.database,
                         charset='utf8mb4',autocommit=False,cursorclass=pymysql.cursors.DictCursor)
    target.validate(conn,c.database)
    with conn.cursor() as q:
        q.execute('SET NAMES utf8mb4');q.execute("SET time_zone='+08:00'")
        q.execute('SET SESSION innodb_lock_wait_timeout=10')
    return conn


def apply_batch(conn,manifest,output):
    completed=[];changes=Counter()
    audit=read(Path(manifest['snapshotDirectory'])/'audit.json')
    require(hashlib.sha256((Path(manifest['snapshotDirectory'])/'audit.json').read_bytes()).hexdigest()==manifest['snapshotSha256'],
            'Snapshot manifest changed')
    mutable=set(LIVE)|{'zsjos_person','zsjos_person_contact_claim','zsjos_person_merge_event'}
    protected={t:digest(table_rows(conn,t,audit['metadata'])) for t in audit['tables'] if t not in mutable}
    held={}
    with conn.cursor() as q:
        for group in manifest['heldGroups']:
            state,_=capture(q,group['memberIds'],'held-protection',lock=False)
            held[group['groupKey']]=helpers.digest(state)
    conn.rollback()
    save(output/'protected-before.json',{'tables':protected,'heldGroups':held})
    try:
        for plan in manifest['plans']:
            save(output/(plan['serverGroupKey']+'-before.json.gz'),plan)
            def before_commit(result,p=plan):
                require(result['changes']==p['expectedChanges'] and result['aliasCount']==p['expectedAliasCount'],
                        'Impact differs from approved rehearsal')
                save(output/(p['serverGroupKey']+'-after.json.gz'),result)
            result=apply_group(conn,plan,execute=True,before_commit=before_commit)
            require(not result['replay'],'Unexpected previous application; inspect receipts before resuming')
            completed.append((plan,result));changes.update(result['changes'])
            require(apply_group(conn,plan,execute=True)['replay'],'Idempotent replay failed')
        checks=verify_identity_paths(conn,manifest['plans'])
        for table,expected in protected.items():
            require(digest(table_rows(conn,table,audit['metadata']))==expected,'Protected task/history/permission rows changed')
        with conn.cursor() as q:
            for group in manifest['heldGroups']:
                state,_=capture(q,group['memberIds'],'held-protection',lock=False)
                require(helpers.digest(state)==held[group['groupKey']],'Held group changed')
        conn.rollback()
        summary={'committed':True,'database':manifest['plans'][0]['database'] if manifest['plans'] else manifest['target']['database'],
                 'groups':len(completed),'archivedSources':sum(len(p['sourcePersonIds']) for p,_ in completed),
                 'changes':dict(changes),'protectedTables':len(protected),'heldGroupsUnchanged':len(held),**checks}
        save(output/'summary.json',summary)
        return summary
    except Exception:
        # Completed groups are retained, with durable receipts. Never automatically
        # delete their new aliases/events under a merge-only authorization.
        save(output/'interrupted.json',{'completedGroups':[p['serverGroupKey'] for p,_ in completed],
                                       'keepMaintenance':True,'automaticRecovery':False})
        raise


def run(args):
    require(hashlib.sha256(args.manifest.read_bytes()).hexdigest()==args.sha256,'Manifest SHA mismatch')
    manifest=read(args.manifest);target=ServerTarget(manifest['target'])
    require(all(p['database']==target.binding['database'] and p['serverTarget']==target.binding for p in manifest['plans']),
            'A plan targets a different database/server')
    maintenance_check();verify_files(manifest,read(args.backup_receipt))
    conn=target.connect_readonly()
    try:fresh_guard(conn,manifest)
    finally:conn.close()
    if not args.apply:return {'validated':True,'applicationCommitted':False}
    require(bool(args.approval_reference.strip()),'User approval reference required; CLI flags alone are not authorization')
    args.output.mkdir(mode=0o700,parents=True,exist_ok=False)
    save(args.output/'approval-reference.json',{'reference':args.approval_reference,'manifestSha256':args.sha256,'target':target.binding})
    maintenance_check()
    conn=connect_write(target)
    try:return {**apply_batch(conn,manifest,args.output),'applicationCommitted':True}
    finally:conn.close()


if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--manifest',type=Path,required=True);p.add_argument('--sha256',required=True)
    p.add_argument('--backup-receipt',type=Path,required=True);p.add_argument('--output',type=Path,required=True)
    p.add_argument('--apply',action='store_true');p.add_argument('--approval-reference',default='')
    a=p.parse_args();print(__import__('json').dumps(run(a)))
