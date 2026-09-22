# UTF-8. Additive sales-stage replay in retained, synthetic local MySQL databases.
import argparse
import datetime as dt
import hashlib
import json
import re
from pathlib import Path
from test_positioning_single_source import query

ROOT = Path(__file__).resolve().parents[4]
MIGRATION = ROOT / 'script/sql/mysql/migrations/V275__lead_sales_stage.sql'
TABLES = ['zsjos_lead', 'zsjos_lead_follow_up_record', 'zsjos_opportunity_follow_up_record', 'system_dict_type', 'system_dict_data', 'zsjos_schema_version', 'zsjos_module_schema_version']
LOCAL = 'ruoyi-vue-pro'

def expanded(path):
    return re.sub(r'^SOURCE\s+([^;]+);\s*$', lambda match: expanded(ROOT / match[1].strip()), path.read_text(encoding='utf-8'), flags=re.M)

def structure(db):
    return query(db, "SELECT table_name,column_name,column_type,is_nullable,collation_name,HEX(column_comment) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name IN ('zsjos_lead','zsjos_lead_follow_up_record','zsjos_opportunity_follow_up_record') AND column_name LIKE 'sales_stage%' ORDER BY table_name,column_name;")

def fingerprint(db):
    # No names/contact information or raw personal rows are printed or backed up.
    return query(db,"SELECT COUNT(*),SUM(CRC32(CONCAT_WS('|',id,tenant_id,status,deleted+0,update_time,last_activity_at,follow_up_count,version))) FROM zsjos_lead;") + query(db,"SELECT COUNT(*),SUM(CRC32(CONCAT_WS('|',id,tenant_id,occurred_at,update_time))) FROM zsjos_lead_follow_up_record; SELECT COUNT(*),SUM(CRC32(CONCAT_WS('|',id,tenant_id,occurred_at,update_time))) FROM zsjos_opportunity_follow_up_record;")

def seed_row(db, row_id, status='submitted', tenant=991, deleted=0):
    query(db,f"INSERT INTO zsjos_lead(id,lead_no,person_id,submitted_name,source_type,status,assignment_status,submitted_at,tenant_id,deleted,create_time,update_time,last_activity_at) VALUES ({row_id},'TEST-{row_id}',{row_id},'测试','sales_self_sourced','{status}','owned','2026-01-01',{tenant},{deleted},'2026-01-01','2026-01-01','2026-01-01');")

def replay():
    db='sales_stage_verify_'+dt.datetime.now().strftime('%Y%m%d%H%M%S')
    query(LOCAL,f'CREATE DATABASE `{db}` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;')
    schema=(ROOT/'script/sql/mysql/00-bootstrap-schema.sql').read_text(encoding='utf-8')
    seed=(ROOT/'script/sql/mysql/02-bootstrap-zsjos-seed.sql').read_text(encoding='utf-8')
    for table in TABLES:
        ddl=re.search(r'CREATE TABLE IF NOT EXISTS `'+table+r'` \(.*?;',schema+'\n'+seed,re.S)
        assert ddl,table
        query(db,re.sub(r'^  `sales_stage[^\n]+\n', '', ddl[0], flags=re.M))
    for row_id,status,tenant,deleted in [(1,'submitted',991,0),(2,'invalid',991,0),(3,'closed',992,0),(4,'suspended',992,0),(5,'won',991,0),(6,'valid',991,1),(7,'valid',991,0)]:
        seed_row(db,row_id,status,tenant,deleted)
    # A partial DDL rollout and an existing real business choice must be safe to resume.
    query(db,"ALTER TABLE zsjos_lead ADD sales_stage varchar(100) NULL COMMENT '当前销售阶段字典值', ADD sales_stage_label_snapshot varchar(100) NULL COMMENT '销售阶段名称快照'; UPDATE zsjos_lead SET sales_stage='intent_customer',sales_stage_label_snapshot='选择时名称' WHERE id=7 AND tenant_id=991;")
    query(db,"INSERT INTO zsjos_lead_follow_up_record(id,lead_id,assignment_history_id,operator_user_id,owner_user_id_snapshot,method_value,method_label_snapshot,result_value,result_label_snapshot,occurred_at,idempotency_key,tenant_id) VALUES(1,1,1,1,1,'phone','历史电话','contacted','历史已联系','2026-01-01','history-lead',991); INSERT INTO zsjos_opportunity_follow_up_record(id,opportunity_id,lead_id,operator_user_id,owner_user_id_snapshot,method_value,method_label_snapshot,result_value,result_label_snapshot,occurred_at,idempotency_key,tenant_id) VALUES(1,1,7,1,1,'phone','历史电话','contacted','历史已联系','2026-01-01','history-opportunity',991);")
    before=fingerprint(db)
    query(db,expanded(MIGRATION))
    assert fingerprint(db)==before,'Initialization changed activity/audit fields or follow-ups'
    for table in ['zsjos_lead_follow_up_record','zsjos_opportunity_follow_up_record']:
        assert query(db,f'SELECT COUNT(*) FROM {table} WHERE sales_stage_before IS NULL AND sales_stage_after IS NULL;').strip()=='1'
    assert query(db,"SELECT GROUP_CONCAT(id ORDER BY id) FROM zsjos_lead WHERE sales_stage='contacted';").strip()=='1,2,3,4'
    assert query(db,"SELECT COUNT(*) FROM zsjos_lead WHERE id IN (5,6) AND sales_stage IS NULL;").strip()=='2'
    assert query(db,"SELECT HEX(sales_stage_label_snapshot) FROM zsjos_lead WHERE id=1;").strip()=='已触达'.encode().hex().upper()
    assert query(db,"SELECT sales_stage_label_snapshot FROM zsjos_lead WHERE id=7;").strip()=='选择时名称'
    # New rows and a real post-initialization choice are never overwritten on replay.
    seed_row(db,8)
    query(db,"UPDATE zsjos_lead SET sales_stage='pending_contact',sales_stage_label_snapshot='待触达' WHERE id=1 AND tenant_id=991; UPDATE system_dict_data SET label='改名后',sort=999,status=1 WHERE dict_type='zsjos_lead_sales_stage' AND value='contacted'; UPDATE system_dict_data SET deleted=1 WHERE dict_type='zsjos_lead_sales_stage' AND value='needs_identified';")
    again=fingerprint(db)
    query(db,expanded(MIGRATION))
    assert fingerprint(db)==again
    assert query(db,"SELECT sales_stage FROM zsjos_lead WHERE id=1;").strip()=='pending_contact'
    assert query(db,"SELECT COUNT(*) FROM zsjos_lead WHERE id=8 AND sales_stage IS NULL;").strip()=='1'
    assert query(db,"SELECT label,sort,status FROM system_dict_data WHERE dict_type='zsjos_lead_sales_stage' AND value='contacted';").strip()=='改名后\t999\t1'
    assert query(db,"SELECT COUNT(*) FROM system_dict_data WHERE dict_type='zsjos_lead_sales_stage' AND value='needs_identified' AND deleted=1;").strip()=='1'
    assert query(db,"SELECT COUNT(*) FROM zsjos_schema_version WHERE version='V275';").strip()=='1'
    print('PASS scoped upgrade, both tenants, excluded won/deleted/existing choices, no fabricated history/activity, dictionary rename/disable/delete, repeatability, HEX:',db)
    return db

def sync_local(verified):
    count=query(LOCAL,"SELECT tenant_id,COUNT(*) FROM zsjos_lead WHERE deleted=0 AND status<>'won' GROUP BY tenant_id;").strip()
    print('Local candidate counts by tenant:',count)
    backup=Path('D:/ZSJ-OS-backups/lead-sales-stage-20260922')
    backup.mkdir(parents=True,exist_ok=True)
    stage_exists=bool(structure(LOCAL))
    cols='id,tenant_id'+(',sales_stage,sales_stage_label_snapshot' if stage_exists else '')
    (backup/'local-preinitialization.tsv').write_text(query(LOCAL,'SELECT '+cols+' FROM zsjos_lead ORDER BY id;'),encoding='utf-8')
    before=fingerprint(LOCAL)
    grants=query(LOCAL,'SELECT COUNT(*),SUM(CRC32(CONCAT_WS(CHAR(58),id,role_id,menu_id,tenant_id))) FROM system_role_menu;')
    query(LOCAL,expanded(MIGRATION))
    once=query(LOCAL,"SELECT id,tenant_id,sales_stage,HEX(sales_stage_label_snapshot) FROM zsjos_lead ORDER BY id;")
    query(LOCAL,expanded(MIGRATION))
    assert once==query(LOCAL,"SELECT id,tenant_id,sales_stage,HEX(sales_stage_label_snapshot) FROM zsjos_lead ORDER BY id;")
    assert fingerprint(LOCAL)==before
    assert grants==query(LOCAL,'SELECT COUNT(*),SUM(CRC32(CONCAT_WS(CHAR(58),id,role_id,menu_id,tenant_id))) FROM system_role_menu;')
    assert structure(LOCAL)==structure(verified)
    assert query(LOCAL,"SELECT COUNT(*) FROM zsjos_lead WHERE sales_stage='contacted' AND HEX(sales_stage_label_snapshot)<>HEX('已触达');").strip()=='0'
    checksum=hashlib.sha256(MIGRATION.read_bytes()).hexdigest()
    # Register this newly executed version only; never reconcile historical checksums.
    query(LOCAL,"INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version) SELECT 'core','V275','Lead sales stage snapshots','"+checksum+"','development' WHERE NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V275');")
    assert query(LOCAL,"SELECT checksum FROM zsjos_module_schema_version WHERE module_code='core' AND version='V275';").strip()==checksum
    print('PASS local additive sync, frozen scope, unchanged activity/history/grants, scoped schema comparison and UTF-8 HEX')


def fresh(verified):
    """Exercise the affected fresh baseline and compare it with the old-schema upgrade."""
    db='sales_stage_fresh_'+dt.datetime.now().strftime('%Y%m%d%H%M%S')
    query(LOCAL,f'CREATE DATABASE `{db}` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;')
    schema=(ROOT/'script/sql/mysql/00-bootstrap-schema.sql').read_text(encoding='utf-8')
    seed=(ROOT/'script/sql/mysql/02-bootstrap-zsjos-seed.sql').read_text(encoding='utf-8')
    for table in TABLES:
        ddl=re.search(r'CREATE TABLE IF NOT EXISTS `'+table+r'` \(.*?;',schema+'\n'+seed,re.S)
        assert ddl,table
        query(db,ddl[0])
    query(db,expanded(MIGRATION))
    query(db,expanded(MIGRATION))
    assert structure(db)==structure(verified)
    assert len(structure(db).strip().splitlines())==10
    assert query(db,'SELECT COUNT(*) FROM zsjos_lead;').strip()=='0'
    assert query(db,"SELECT COUNT(*) FROM system_dict_data WHERE dict_type='zsjos_lead_sales_stage';").strip()=='5'
    print('PASS scoped fresh baseline, repeated V275, empty business data, schema matches upgrade:',db)

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--sync-local',action='store_true');args=parser.parse_args()
    verified=replay()
    fresh(verified)
    if args.sync_local:sync_local(verified)
