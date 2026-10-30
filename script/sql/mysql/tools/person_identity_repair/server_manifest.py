# -*- coding: utf-8 -*-
"""Export reviewed server plans from a verified copy, without executing server writes."""
import argparse
import hashlib
from pathlib import Path
from server_target import read,save,require,digest
from repair import helpers
from server_rehearsal import MIGRATION


def export(snapshot,rehearsal,artifact,output):
    audit=read(snapshot/'audit.json');summary=read(rehearsal/'summary.json')
    require(not summary['blocked'] and summary['recovery'] and summary['afterEditRecoveryRejected'], 'Complete rehearsal required')
    plans=[]
    for group in audit['groups']:
        if group['status']!='READY':continue
        p=read(rehearsal/(group['groupKey']+'-before.json.gz'))
        result=read(rehearsal/(group['groupKey']+'-after.json.gz'))
        require(p['serverTarget']==audit['target'] and p['memberIds']==group['memberIds'],'Rehearsal target/members differ')
        require(not p['before']['zsjos_person_merge_event'],'An existing batch needs independent replay review')
        p['rehearsalDatabase']=p['database']
        p['database']=audit['target']['database']
        p['key']='person-group:'+hashlib.sha256(helpers.packed([p['database'],p['memberIds']]).encode()).hexdigest()
        p['expectedChanges']=result['changes']
        p['expectedAliasCount']=result['aliasCount']
        p['targetPersonNo']=next(r['person_no'] for r in p['before']['zsjos_person'] if r['id']==p['targetPersonId'])
        plans.append(p)
    tool_dir=Path(__file__).resolve().parent
    tool_files=('server_target.py','server_audit.py','server_rehearsal.py','server_apply.py','server_backup.py',
                'server_manifest.py','group_repair.py','repair.py','identity_rules.py')
    manifest={'target':audit['target'],'toolSha256':{str((tool_dir/f).resolve()):hashlib.sha256((tool_dir/f).read_bytes()).hexdigest() for f in tool_files},
              'snapshotSha256':hashlib.sha256((snapshot/'audit.json').read_bytes()).hexdigest(),
              'snapshotDirectory':str(snapshot.resolve()),'rehearsalDirectory':str(rehearsal.resolve()),
              'confirmationHash':audit['confirmationHash'],'plans':plans,
              'heldGroups':[g for g in audit['groups'] if g['status']!='READY'],
              'checksums':audit['checksums'],'migrationFile':str(MIGRATION.resolve()),
              'migrationSha256':hashlib.sha256(MIGRATION.read_bytes()).hexdigest(),
              'artifact':str(artifact.resolve()),'artifactSha256':hashlib.file_digest(artifact.open('rb'),'sha256').hexdigest(),
              'applicationCommitted':False}
    save(output,manifest)
    return {'groups':len(plans),'sources':sum(len(p['sourcePersonIds']) for p in plans),
            'heldGroups':len(manifest['heldGroups']),'manifestSha256':hashlib.sha256(output.read_bytes()).hexdigest()}


if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--snapshot',type=Path,required=True);p.add_argument('--rehearsal',type=Path,required=True)
    p.add_argument('--artifact',type=Path,required=True);p.add_argument('--output',type=Path,required=True)
    a=p.parse_args();print(__import__('json').dumps(export(a.snapshot,a.rehearsal,a.artifact,a.output)))
