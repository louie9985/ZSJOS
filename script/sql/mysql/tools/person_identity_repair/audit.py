# -*- coding: utf-8 -*-
"""Read-only identity group/reference audit. Emits no contact values or names."""
import argparse
import collections
import datetime
import hashlib
import json
import re
import subprocess
from pathlib import Path
from identity_rules import classify_group


def query(database, sql):
    if not re.fullmatch(r'[A-Za-z0-9_-]+', database):
        raise ValueError('Invalid database identifier')
    command = 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql --default-character-set=utf8mb4 -uroot -N -B'
    result = subprocess.run(['docker', 'exec', '-i', 'yudao-mysql', 'sh', '-c', command],
                            input=('SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci; USE `' + database + '`; SET TRANSACTION READ ONLY; START TRANSACTION WITH CONSISTENT SNAPSHOT;\n' + sql + '\nCOMMIT;').encode(), capture_output=True)
    if result.returncode:
        raise RuntimeError('Read-only audit failed; inspect database/schema availability')
    return [line.split('\t') for line in result.stdout.decode().strip().splitlines() if line]


def audit(database):
    # Use the application's candidate predicate; group transitive overlaps, not contact values.
    rows = query(database, "SELECT p.id,q.id,(TRIM(p.name)=TRIM(q.name)) FROM zsjos_person p JOIN zsjos_person q ON p.id<q.id AND q.tenant_id=1 AND q.deleted=0 WHERE p.tenant_id=1 AND p.deleted=0 AND ((NULLIF(TRIM(p.mobile),'') IS NOT NULL AND (CAST(p.mobile AS BINARY)=CAST(q.mobile AS BINARY) OR LOWER(p.mobile)=LOWER(q.wechat_id))) OR (NULLIF(TRIM(p.wechat_id),'') IS NOT NULL AND (LOWER(p.wechat_id)=LOWER(q.wechat_id) OR CAST(p.wechat_id AS BINARY)=CAST(q.mobile AS BINARY))));")
    parent = {}
    def root(value):
        parent.setdefault(value,value)
        if parent[value] != value:
            parent[value] = root(parent[value])
        return parent[value]
    for a,b,_ in rows:
        parent[root(int(a))] = root(int(b))
    groups = collections.defaultdict(list)
    for person in sorted(parent):
        groups[root(person)].append(person)
    ids=','.join(map(str,sorted(parent)))
    people=[];orders=[];lead_details=[]
    if ids:
        people=[json.loads(r[0]) for r in query(database, f"SELECT JSON_OBJECT('id',id,'name',name,'mobile',mobile,'wechat_id',wechat_id) FROM zsjos_person WHERE tenant_id=1 AND deleted=0 AND id IN ({ids});")]
        orders=[json.loads(r[0]) for r in query(database, f"SELECT JSON_OBJECT('id',id,'person_id',person_id,'student_name',student_name,'creator',creator) FROM zsjos_order WHERE tenant_id=1 AND deleted=0 AND person_id IN ({ids});")]
        lead_details=[json.loads(r[0]) for r in query(database, f"SELECT JSON_OBJECT('id',id,'person_id',person_id,'status',status) FROM zsjos_lead WHERE tenant_id=1 AND deleted=0 AND person_id IN ({ids});")]
    lead_rows = query(database, "SELECT person_id,status,COUNT(*) FROM zsjos_lead WHERE tenant_id=1 AND deleted=0 GROUP BY person_id,status;")
    leads = collections.defaultdict(collections.Counter)
    for person,status,count in lead_rows:
        leads[int(person)][status] += int(count)
    references = query(database, "SELECT c.table_name,c.column_name FROM information_schema.columns c JOIN information_schema.tables t ON t.table_schema=c.table_schema AND t.table_name=c.table_name AND t.table_type='BASE TABLE' WHERE c.table_schema=DATABASE() AND c.table_name LIKE 'zsjos_%' AND (c.column_name='person_id' OR c.column_name LIKE '%_person_id') ORDER BY c.table_name,c.column_name;")
    result = []
    for members in sorted(groups.values(),key=min):
        statuses = sum((leads[p] for p in members),collections.Counter())
        result.append({'personIds':members,'sameNameEvidenceOnly':all(equal=='1' for a,b,equal in rows if int(a) in members),
                       'leadStatuses':dict(statuses),'leadCount':sum(statuses.values()),
                       'nonClosedLeadCount':sum(n for status,n in statuses.items() if status!='closed'),
                       **classify_group([p for p in people if p['id'] in members],orders,lead_details)})
    return {'database':database,'tenantId':1,'generatedAt':datetime.datetime.now(datetime.timezone.utc).isoformat(),
            'readOnly':True,'identityGroupCount':len(result),'personCount':len(parent),
            'referenceInventory':[{'table':t,'column':c} for t,c in references],
            'groups':result,'limitations':['Separate read-only snapshots: regenerate and lock/revalidate before repair.',
            'Column inventory is not a mutation allowlist; JSON/event references require explicit preservation decisions.',
            'Identity follows user-confirmed exact/complementary/cross-contact and real-order-name rules; automatic identity eligibility does not bypass execution checks.']}


if __name__ == '__main__':
    parser=argparse.ArgumentParser()
    parser.add_argument('--database',default='ruoyi-vue-pro')
    parser.add_argument('--output',required=True,type=Path)
    args=parser.parse_args()
    result=audit(args.database)
    args.output.parent.mkdir(parents=True,exist_ok=True)
    args.output.write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps({'groups':result['identityGroupCount'],'persons':result['personCount'],
                      'referenceColumns':len(result['referenceInventory']),
                      'sha256':hashlib.sha256(args.output.read_bytes()).hexdigest()}))
