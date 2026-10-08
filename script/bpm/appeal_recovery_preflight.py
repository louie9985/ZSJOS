#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Read-only evidence for one Lead appeal; never starts an engine or changes rows."""
import argparse
import hashlib
import json
import re
import shlex
import subprocess
import sys
import xml.etree.ElementTree as ET


def assess(row):
    blockers = []
    def check(condition, message):
        if not condition:
            blockers.append(message)
    raw_reviewers = row['reviewers']
    reviewers = json.loads(raw_reviewers) if isinstance(raw_reviewers, str) else raw_reviewers
    check(isinstance(reviewers, list) and bool(reviewers)
          and all(type(x) is int and x > 0 for x in reviewers)
          and len(set(reviewers)) == len(reviewers), 'invalid frozen reviewer snapshot')
    check(row['status'] == 'sales_manager_reviewing' and row['roundNo'] == 1
          and row['reviewStage'] == 'sales_manager', 'not an initial sales-manager review')
    check(row['taskCount'] == 1 and row['taskKey'] == 'StartUserNode'
          and row['taskStatus'] == 2 and row['suspensionState'] == 1, 'starter task/state changed')
    check(row['businessKey'] == 'lead-appeal:' + str(row['appealId'])
          and str(row['processTenant']) == str(row['tenantId']) and row['endTime'] is None,
          'process identity/state mismatch')
    check(row['jobs'] == 0 and row['reviewHistory'] == 0, 'jobs or review history require separate recovery')
    check(row['compatibilityVariables'] == 0, 'compatibility variable already exists')
    check(isinstance(reviewers, list) and row['eligibleReviewers'] == len(reviewers), 'reviewer status/permission changed')
    raw = bytes.fromhex(row['xmlHex'])
    root = ET.fromstring(raw)
    ns = {'b': 'http://www.omg.org/spec/BPMN/20100524/MODEL', 'f': 'http://flowable.org/bpmn'}
    task = root.find(".//b:userTask[@id='appealReview']", ns)
    check(task is not None, 'review node missing')
    if task is not None:
        check(task.findtext('b:extensionElements/f:candidateStrategy', namespaces=ns) == '60'
              and task.findtext('b:extensionElements/f:candidateParam', namespaces=ns) == '${coll_userList}',
              'not the known deployed expression contract')
    return {
        'mode': 'READ_ONLY', 'state': 'CANDIDATE' if not blockers else 'BLOCKED',
        'leadNo': row['leadNo'], 'tenantId': str(row['tenantId']), 'appealId': row['appealId'],
        'instanceId': row['instanceId'], 'definitionId': row['definitionId'],
        'businessKey': row['businessKey'], 'startTaskId': row['taskId'],
        'reviewTaskKey': 'appealReview', 'variableName': 'coll_userList',
        'definitionXmlSha256': hashlib.sha256(raw).hexdigest(), 'reviewers': reviewers,
        'reviewPermission': 'zsjos:lead:appeal:review-sales-manager', 'blockers': blockers,
        'remainingChecks': ['BPM engine must compare persisted selected assignees and SYSTEM actor snapshot',
                            'Authorized operator must recheck business state and frozen snapshot immediately before recovery',
                            'No write authorization is implied by this report'],
    }


def query(tenant, lead_no):
    if tenant <= 0 or not re.fullmatch(r'[A-Za-z0-9_-]{1,64}', lead_no):
        raise ValueError('Invalid tenant or lead number')
    return f"""SET NAMES utf8mb4;
SET TRANSACTION ISOLATION LEVEL REPEATABLE READ;
START TRANSACTION READ ONLY;
SELECT JSON_OBJECT(
 'leadNo',l.lead_no,'tenantId',a.tenant_id,'appealId',a.id,'status',a.status,
 'roundNo',a.round_no,'reviewStage',a.review_stage,'reviewers',a.reviewer_user_ids_snapshot,
 'instanceId',a.process_instance_id,'definitionId',h.PROC_DEF_ID_,'businessKey',h.BUSINESS_KEY_,
 'processTenant',h.TENANT_ID_,'endTime',h.END_TIME_,'taskId',t.ID_,'taskKey',t.TASK_DEF_KEY_,
 'suspensionState',t.SUSPENSION_STATE_,
 'taskStatus',(SELECT v.LONG_ FROM ACT_RU_VARIABLE v WHERE v.TASK_ID_=t.ID_ AND v.NAME_='TASK_STATUS'),
 'taskCount',(SELECT COUNT(*) FROM ACT_RU_TASK x WHERE x.PROC_INST_ID_=a.process_instance_id),
 'reviewHistory',(SELECT COUNT(*) FROM ACT_HI_TASKINST x WHERE x.PROC_INST_ID_=a.process_instance_id AND x.TASK_DEF_KEY_='appealReview'),
 'compatibilityVariables',(SELECT COUNT(*) FROM ACT_RU_VARIABLE x WHERE x.PROC_INST_ID_=a.process_instance_id AND x.NAME_='coll_userList'),
 'jobs',((SELECT COUNT(*) FROM ACT_RU_JOB x WHERE x.PROCESS_INSTANCE_ID_=a.process_instance_id)
       +(SELECT COUNT(*) FROM ACT_RU_TIMER_JOB x WHERE x.PROCESS_INSTANCE_ID_=a.process_instance_id)
       +(SELECT COUNT(*) FROM ACT_RU_SUSPENDED_JOB x WHERE x.PROCESS_INSTANCE_ID_=a.process_instance_id)
       +(SELECT COUNT(*) FROM ACT_RU_DEADLETTER_JOB x WHERE x.PROCESS_INSTANCE_ID_=a.process_instance_id)),
 'eligibleReviewers',(SELECT COUNT(DISTINCT u.id) FROM system_users u
   JOIN system_user_role ur ON ur.user_id=u.id AND ur.deleted=0 AND ur.tenant_id=a.tenant_id
   JOIN system_role r ON r.id=ur.role_id AND r.status=0 AND r.deleted=0 AND r.tenant_id=a.tenant_id
   JOIN system_role_menu rm ON rm.role_id=r.id AND rm.deleted=0 AND rm.tenant_id=a.tenant_id
   JOIN system_menu m ON m.id=rm.menu_id AND m.deleted=0 AND m.status=0
   WHERE u.tenant_id=a.tenant_id AND u.status=0 AND u.deleted=0
   AND JSON_CONTAINS(a.reviewer_user_ids_snapshot,CAST(u.id AS JSON))
   AND m.permission='zsjos:lead:appeal:review-sales-manager'),
 'xmlHex',HEX(b.BYTES_))
FROM zsjos_lead_appeal a
JOIN zsjos_lead l ON l.id=a.lead_id AND l.tenant_id=a.tenant_id AND l.deleted=0
JOIN ACT_HI_PROCINST h ON h.PROC_INST_ID_=a.process_instance_id
JOIN ACT_RE_PROCDEF d ON d.ID_=h.PROC_DEF_ID_
JOIN ACT_GE_BYTEARRAY b ON b.DEPLOYMENT_ID_=d.DEPLOYMENT_ID_ AND b.NAME_=d.RESOURCE_NAME_
LEFT JOIN ACT_RU_TASK t ON t.PROC_INST_ID_=a.process_instance_id
WHERE a.deleted=0 AND a.tenant_id={tenant} AND l.lead_no='{lead_no}' AND a.status LIKE '%reviewing';
COMMIT;
"""


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--tenant-id', type=int, required=True)
    parser.add_argument('--lead-no', required=True)
    parser.add_argument('--database', default='zsjos')
    parser.add_argument('--mysql-command', default='mysql', help='Credential-free argv prefix; use client option file or protected environment')
    args = parser.parse_args()
    sql = query(args.tenant_id, args.lead_no)
    command = shlex.split(args.mysql_command) + ['--default-character-set=utf8mb4', '--batch', '--raw',
                                                '--skip-column-names', '--database=' + args.database]
    result = subprocess.run(command, input=sql, text=True, encoding='utf-8', capture_output=True)
    if result.returncode:
        raise ValueError('Read-only MySQL query failed; check connection/schema (raw stderr withheld)')
    rows = [json.loads(line) for line in result.stdout.splitlines() if line.strip()]
    if len(rows) != 1:
        raise ValueError('Expected exactly one reviewing appeal and one task; no recovery plan emitted')
    report = assess(rows[0])
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0 if report['state'] == 'CANDIDATE' else 2


if __name__ == '__main__':
    try:
        sys.exit(main())
    except (ValueError, OSError, ET.ParseError) as error:
        print(str(error), file=sys.stderr)
        sys.exit(2)
