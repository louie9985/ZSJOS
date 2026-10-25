#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Non-destructive V346 verification. Retains synthetic databases; --apply-local touches only two new tables/ledgers."""
import argparse
import datetime
import re
import json
import xml.etree.ElementTree as ET
from test_positioning_single_source import query, ROOT

SOURCE = 'ruoyi-vue-pro'
STAMP = datetime.datetime.now().strftime('%Y%m%d%H%M%S%f')
SQL = (ROOT/'script/sql/mysql/migrations/V346__weekly_account_diagnosis.sql').read_text(encoding='utf-8')
TABLES = ['zsjos_media_account_profile_entry','zsjos_business_task','zsjos_business_event',
          'zsjos_partner_student_link','zsjos_schema_version','zsjos_module_schema_version']

def fixture(suffix):
    db='weekly_diagnosis_'+STAMP+'_'+suffix
    query(SOURCE,f'CREATE DATABASE `{db}` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;')
    for table in TABLES:
        query(db,f'CREATE TABLE `{table}` LIKE `{SOURCE}`.`{table}`;')
    return db

def ledgers(db):
    return query(db,"SELECT COUNT(*) FROM zsjos_schema_version WHERE version='V346'; SELECT COUNT(*) FROM zsjos_module_schema_version WHERE module_code='core' AND version='V346';").strip()

def shape(db):
    return query(db,"SELECT table_name,column_name,column_type,is_nullable,COALESCE(column_default,'NULL'),HEX(column_comment) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name LIKE 'zsjos_account_diagnosis_%' ORDER BY table_name,ordinal_position; SELECT table_name,index_name,non_unique,seq_in_index,column_name FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name LIKE 'zsjos_account_diagnosis_%' ORDER BY table_name,index_name,seq_in_index;")

def verify(db):
    query(db,SQL); first=shape(db); query(db,SQL)
    assert first==shape(db) and ledgers(db)=='1\n1'
    assert query(db,"SELECT HEX(description) FROM zsjos_schema_version WHERE version='V346';").strip()=='周期诊断综合表统计'.encode().hex().upper()
    print('PASS initial/repeat/Chinese HEX',db,flush=True)
    return first

def fails(db):
    before=ledgers(db)
    try: query(db,SQL)
    except RuntimeError: pass
    else: raise AssertionError('expected controlled failure')
    assert ledgers(db)==before
    print('PASS failure leaves both ledgers unchanged',db,flush=True)

def analytics(db):
    for table in ['zsjos_content_review_batch','zsjos_content_review_batch_item','zsjos_lead','zsjos_order']:
        query(db,f'CREATE TABLE `{table}` LIKE `{SOURCE}`.`{table}`;')
    query(db,"""INSERT INTO zsjos_content_review_batch(id,batch_no,account_id,account_ids_json,operator_user_id,status,current_stage,submitted_at,tenant_id) VALUES
      (1,'SYNTHETIC1',10,'[10,11]',1,'completed','final','2026-10-02',1),
      (2,'SYNTHETIC2',10,'[10,11]',1,'rejected','final','2026-10-04',1),
      (3,'SYNTHETIC3',10,'[10]',1,'draft','draft',NULL,1),
      (4,'SYNTHETIC4',10,'[10]',1,'completed','final','2026-10-09',1);
      INSERT INTO zsjos_content_review_batch_item(id,batch_id,content_id,content_version_id,sort_no,content_snapshot_json,tenant_id) VALUES
      (1,1,100,1001,1,'{"plannedPublishAt":"2026-10-03T10:00:00"}',1),
      (2,2,100,1002,1,'{"plannedPublishAt":"2026-10-05T10:00:00"}',1),
      (3,1,101,1011,2,'{"plannedPublishAt":"2026-10-08T00:00:00"}',1),
      (4,1,102,1021,3,'{}',1),
      (5,3,103,1031,1,'{"plannedPublishAt":"2026-10-05T10:00:00"}',1),
      (6,4,100,1003,1,'{"plannedPublishAt":"2026-10-10T10:00:00"}',1);
    """)
    mapper=(ROOT/'backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/dal/mysql/account/DiagnosisMetricMapper.java').read_text(encoding='utf-8')
    sql=re.search(r'WITH versions AS \(.*?</script>',mapper,re.S).group(0).replace('</script>','')
    values={'tenant':'1','account':'10','from':"'2026-10-01'",'to':"'2026-10-08'",'cutoff':"'2026-10-08'"}
    for key,value in values.items(): sql=sql.replace('#{'+key+'}',value)
    sql=sql.replace('&lt;','<').replace('&gt;','>')
    assert query(db,sql).strip()=='1\t1'
    assert query(db,sql.replace('CAST(10 AS JSON)','CAST(11 AS JSON)').replace('b.account_id=10','b.account_id=11')).strip()=='1\t1'
    print('PASS production publication SQL: revisions/multi-account/missing/draft/boundary/later change',flush=True)
    query(db,"""INSERT INTO zsjos_account_diagnosis_metric(id,account_id,node_key,round_key,interval_days,interval_cycle,period_from,period_to,state,metric_json,submission_snapshot_json) VALUES(1,10,'synthetic','r',14,1,'2026-10-01','2026-10-15','SETTLED','{}','{}');
      INSERT INTO zsjos_account_diagnosis_member(tenant_id,metric_id,lead_id) VALUES(1,1,1001),(1,1,1002);
      INSERT INTO zsjos_order(id,order_no,person_id,lead_id,order_type,status,total_amount,payable_amount,effective_at,tenant_id) VALUES
      (1,'SYNTHETIC1',999,1001,'initial','effective',1,1,'2026-10-18',1),
      (2,'SYNTHETIC2',999,1001,'initial','effective',1,1,'2026-10-19',1),
      (3,'SYNTHETIC3',999,1002,'repurchase','effective',1,1,'2026-10-19',1),
      (4,'SYNTHETIC4',999,1003,'initial','effective',1,1,'2026-10-19',1);
    """)
    deals=re.search(r'@Select\("(SELECT COUNT\(DISTINCT m.lead_id\).*?)"\)',mapper).group(1)
    for key,value in {'tenant':'1','metric':'1','now':"'2026-10-22'"}.items(): deals=deals.replace('#{'+key+'}',value)
    assert query(db,deals).strip()=='1'
    query(db,"INSERT INTO zsjos_order(id,order_no,person_id,lead_id,order_type,status,total_amount,payable_amount,effective_at,tenant_id) VALUES(5,'SYNTHETIC5',999,1002,'initial','effective',1,1,'2026-10-21',1);")
    assert query(db,deals).strip()=='2'
    assert query(db,'SELECT COUNT(*) FROM zsjos_account_diagnosis_member WHERE metric_id=1;').strip()=='2'
    print('PASS fixed cohort/live deals/duplicate orders/repurchase exclusion/no amount threshold',flush=True)


def bulk_analytics(db):
    mapper=(ROOT/'backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/dal/mysql/account/DiagnosisMetricMapper.java').read_text(encoding='utf-8')
    statements={}
    for match in re.finditer(r'@(Select|Insert)\("""\s*(.*?)\s*"""\)\s*([^\n]+)',mapper,re.S):
        method=re.search(r'(\w+)\(',match.group(3)).group(1)
        statements[method]=match.group(2)
    def render(method, ids, **values):
        script=ET.fromstring(statements[method])
        sql=script.text or ''
        for child in script:
            assert child.tag=='foreach' and child.attrib['collection']=='ids',method
            sql+=child.attrib.get('open','')+child.attrib.get('separator','').join(
                (child.text or '').replace('#{id}',str(i)) for i in ids)+child.attrib.get('close','')+(child.tail or '')
        for key,value in values.items():sql=sql.replace('#{'+key+'}',str(value))
        assert '#{' not in sql,method
        return sql
    query(db,"""INSERT INTO zsjos_business_event(id,event_type,aggregate_type,aggregate_id,to_status,occurred_at,idempotency_key,related_object_refs,deleted,tenant_id) VALUES
      (7001,'lead_flow_operation','lead',1001,'valid','2026-10-01','synthetic-bulk-1','{}',0,1),
      (7002,'lead_flow_operation','lead',1001,'invalid','2026-10-16','synthetic-bulk-2','{}',0,1),
      (7003,'lead_flow_operation','lead',1001,'won','2026-10-14','synthetic-bulk-3','{}',0,2),
      (7004,'lead_flow_operation','lead',1002,NULL,'2026-10-02','synthetic-bulk-4','{"sample":"legacy"}',0,1),
      (7005,'lead_flow_operation','lead',1001,'lost','2026-10-14','synthetic-bulk-5','{}',1,1);
      INSERT INTO zsjos_order(id,order_no,person_id,lead_id,order_type,status,total_amount,payable_amount,effective_at,tenant_id) VALUES
      (6,'SYNTHETIC6',999,1004,'repurchase','effective',1,1,'2026-10-19',1),
      (7,'SYNTHETIC7',999,1005,'initial','effective',1,1,'2026-10-25',1),
      (8,'SYNTHETIC8',999,1004,'initial','effective',1,1,'2026-10-19',2);""")
    assert query(db,render('statusesBefore',[1001,1002],tenant=1,cutoff="'2026-10-15'")).strip()=='1001\tvalid'
    legacy=query(db,render('flowsBefore',[1002],tenant=1,cutoff="'2026-10-15'")).strip()
    assert legacy.startswith('1002\t') and 'legacy' in legacy
    assert set(query(db,render('dealtLeadIds',[1001,1002,1004,1005],tenant=1,now="'2026-10-22'")).split())=={'1001','1002'}
    members=render('members',[1001,1002,1003],tenant=1,metric=1)
    query(db,members);query(db,members)
    assert query(db,'SELECT COUNT(*) FROM zsjos_account_diagnosis_member WHERE tenant_id=1 AND metric_id=1;').strip()=='3'
    print('PASS actual bulk mapper SQL: cutoff/legacy/tenant/duplicates/repurchase/live-time/member replay',flush=True)

def task_projection(db):
    mapper=(ROOT/'backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/dal/mysql/task/BusinessTaskMapper.java').read_text(encoding='utf-8')
    block=mapper.split('private static LambdaQueryWrapperX<BusinessTaskDO> weeklyNodes',1)[1].split('return query;',1)[0]
    condition=''.join(json.loads('"'+piece+'"') for piece in re.findall(r'"((?:\\.|[^"\\])*)"',block)).replace('{0}',"'2026-10-29'")
    query(db,"""INSERT INTO zsjos_business_task(id,task_type,biz_type,biz_id,status,assignee_type,assignee_id,due_at,payload,tenant_id) VALUES
      (1,'media_account_diagnosis_7d','media_account_diagnosis',10,'pending','ADMIN',20,'2026-10-29','{"roundKey":"r"}',1),
      (2,'media_account_diagnosis_14d','media_account_diagnosis',10,'pending','ADMIN',20,'2026-10-29','{"roundKey":"r"}',1),
      (3,'media_account_diagnosis_28d','media_account_diagnosis',10,'pending','ADMIN',20,'2026-10-29','{"roundKey":"r"}',1),
      (4,'media_account_diagnosis_14d','media_account_diagnosis',10,'pending','ADMIN',20,'2026-11-12','{"roundKey":"r"}',1),
      (5,'other','other',10,'pending','ADMIN',20,'2026-10-29','{}',1);
    """)
    select="SELECT id FROM zsjos_business_task WHERE status='pending' AND "+condition+' ORDER BY id;'
    assert query(db,select).strip()=='1\n5'
    query(db,"UPDATE zsjos_business_task SET status='completed' WHERE id=1;")
    assert query(db,select).strip()=='2\n5'
    assert query(db,"SELECT COUNT(*) FROM zsjos_business_task WHERE biz_type='media_account_diagnosis' AND status='pending';").strip()=='3'
    print('PASS production pending projection: one weekly row/count, remaining legacy parts, future hidden, raw task identities preserved',flush=True)

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--apply-local',action='store_true');args=parser.parse_args()
    initial=fixture('initial');expected=verify(initial);analytics(initial);bulk_analytics(initial);task_projection(initial)
    missing=fixture('missing');query(missing,'RENAME TABLE zsjos_business_event TO fixture_missing_event;');fails(missing)
    partial=fixture('partial')
    query(partial,f'CREATE TABLE zsjos_account_diagnosis_metric LIKE `{initial}`.zsjos_account_diagnosis_metric;')
    assert verify(partial)==expected
    wrong=fixture('wrong');query(wrong,'CREATE TABLE zsjos_account_diagnosis_metric(id bigint PRIMARY KEY);');fails(wrong)
    failed=fixture('ledger_failure');query(failed,"CREATE TRIGGER reject_v346 BEFORE INSERT ON zsjos_module_schema_version FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='synthetic ledger failure';");fails(failed)
    query(failed,'DROP TRIGGER reject_v346;');assert verify(failed)==expected
    # Preserve and compare pre-existing local evidence, including permission grants.
    signature="SELECT COUNT(*),COALESCE(SUM(id),0) FROM system_role_menu; SELECT COUNT(*),COALESCE(MAX(id),0) FROM zsjos_media_account_profile_entry;"
    before=query(SOURCE,signature)
    print('Local target precheck:',query(SOURCE,"SELECT table_name FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name LIKE 'zsjos_account_diagnosis_%';").strip() or 'new empty tables only',flush=True)
    if args.apply_local:
        assert verify(SOURCE)==expected
        assert query(SOURCE,signature)==before
        print('PASS local non-destructive sync and scoped schema comparison; existing entries/grants preserved',flush=True)
