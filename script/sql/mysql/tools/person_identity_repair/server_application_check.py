# -*- coding: utf-8 -*-
"""Verify the exact server batch writer against a newly restored isolated copy."""
import argparse
import hashlib
from pathlib import Path
from server_target import ServerTarget,read,save,require,digest
from server_rehearsal import restore_copy,execute_script,MIGRATION,table_rows
from server_apply import fresh_guard,apply_batch
from group_repair import restore_group
from repair import helpers


def check(manifest_path,output):
    output.mkdir(mode=0o700,parents=True,exist_ok=False)
    manifest=read(manifest_path);snapshot=Path(manifest['snapshotDirectory']);audit=read(snapshot/'audit.json')
    target=ServerTarget(manifest['target']);db,c=restore_copy(target,snapshot,audit)
    try:
        execute_script(c,MIGRATION.read_text(encoding='utf-8'))
        for p in manifest['plans']:
            p['database']=db
            p['key']='person-group:'+hashlib.sha256(helpers.packed([db,p['memberIds']]).encode()).hexdigest()
        fresh_guard(c,manifest)
        # A late data edit must invalidate the approved before-image.
        pid=manifest['plans'][0]['targetPersonId']
        with c.cursor() as q:
            q.execute('SELECT name,update_time FROM zsjos_person WHERE id=%s',(pid,));saved=q.fetchone()
            q.execute("UPDATE zsjos_person SET name='isolated-stale-plan-check' WHERE id=%s",(pid,))
        c.commit()
        try:fresh_guard(c,manifest)
        except ValueError as e:require('before-image changed' in str(e),'Unexpected stale-plan rejection')
        else:raise ValueError('Stale plan accepted')
        with c.cursor() as q:q.execute('UPDATE zsjos_person SET name=%s,update_time=%s WHERE id=%s',(saved['name'],saved['update_time'],pid))
        c.commit();fresh_guard(c,manifest)
        baseline={t:digest(table_rows(c,t,audit['metadata'])) for t in audit['tables']};c.rollback()
        result=apply_batch(c,manifest,output)
        for plan in reversed(manifest['plans']):
            receipt=read(output/(plan['serverGroupKey']+'-after.json.gz'))
            restore_group(c,plan,receipt,execute=True)
        for t,h in baseline.items():require(digest(table_rows(c,t,audit['metadata']))==h,'Exact writer recovery differs: '+t)
        summary={'applicationCommitted':False,'isolatedDatabase':db,'exactWriterGroups':result['groups'],
                 'staleBeforeImageRejected':True,'protectedTables':result['protectedTables'],
                 'heldGroupsUnchanged':result['heldGroupsUnchanged'],'restored':True,
                 'originalContactChecks':result['originalContactChecks'],'oldLinkMappings':result['oldLinkMappings'],
                 'originalAuthorizationScopes':result['originalAuthorizationScopes']}
        save(output/'verification.json',summary);return summary
    finally:c.rollback();c.close()


if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--manifest',type=Path,required=True);p.add_argument('--output',type=Path,required=True)
    a=p.parse_args();print(__import__('json').dumps(check(a.manifest,a.output)))
