"""UTF-8: scoped V263 replay, isolation and optional local additive synchronization."""
import argparse
import datetime
from pathlib import Path
from test_positioning_single_source import query
ROOT = Path(__file__).resolve().parents[4]
LOCAL = 'ruoyi-vue-pro'
TABLES = ['zsjos_user_relation_scene','zsjos_user_relation','zsjos_lead','zsjos_schema_version','zsjos_module_schema_version']
SQL = ROOT / 'script/sql/mysql/migrations/V263__partner_specified_assignment.sql'
def shape(db):
    return query(db, "SELECT table_name,column_name,column_type,is_nullable,HEX(column_comment) FROM information_schema.columns WHERE table_schema=DATABASE() AND ((table_name='zsjos_user_relation_scene' AND column_name IN ('source_type','source_post_code')) OR (table_name='zsjos_user_relation' AND column_name='owner_identity') OR (table_name='zsjos_lead' AND column_name='pending_owner_identity')) ORDER BY table_name,column_name;")
def preserved(db):
    return query(db, "SELECT COUNT(*),COALESCE(SUM(CRC32(CONCAT(id,':',tenant_id,':',source_user_id,':',target_user_id,':',status))),0) FROM zsjos_user_relation; SELECT COUNT(*),COALESCE(SUM(CRC32(CONCAT(id,':',tenant_id,':',COALESCE(owner_user_id,0),':',assignment_status))),0) FROM zsjos_lead;")
if __name__ == '__main__':
    parser=argparse.ArgumentParser(); parser.add_argument('--sync-local',action='store_true'); args=parser.parse_args()
    print('Local preflight counts/checksums:', preserved(LOCAL).strip())
    db='partner_assignment_verify_'+datetime.datetime.now().strftime('%Y%m%d%H%M%S')
    query(LOCAL,f'CREATE DATABASE `{db}` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;')
    for table in TABLES: query(db,f'CREATE TABLE {table} LIKE `{LOCAL}`.{table};')
    query(db,"""INSERT INTO zsjos_user_relation_scene(name,code,source_label,target_label,source_post_code,target_post_code,tenant_id) VALUES
('测试原场景','lead_specified_assignment','来源','目标','source','sales',991),
('测试原场景','lead_specified_assignment','来源','目标','source','sales',992);
INSERT INTO zsjos_user_relation(scene,source_user_id,target_user_id,status,tenant_id) VALUES('lead_specified_assignment',7,8,0,991),('lead_specified_assignment',7,9,0,992);""")
    before=preserved(db); sql=SQL.read_text(encoding='utf-8')
    query(db,sql); query(db,sql)
    assert before==preserved(db)
    assert query(db,"SELECT COUNT(*) FROM zsjos_user_relation_scene WHERE code='partner_lead_specified_assignment' AND source_type='partner' AND source_post_code IS NULL;").strip()=='2'
    assert query(db,"SELECT COUNT(*) FROM zsjos_user_relation WHERE scene='partner_lead_specified_assignment';").strip()=='0'
    assert query(db,"SELECT HEX(name) FROM zsjos_user_relation_scene WHERE code='partner_lead_specified_assignment' AND tenant_id=991;").strip()=='兼职客资指定分配'.encode().hex().upper()
    assert query(db,"SELECT COUNT(*) FROM zsjos_schema_version WHERE version='V263';").strip()=='1'
    assert query(db,"SELECT COUNT(*) FROM zsjos_module_schema_version WHERE version='V263' AND module_code='core';").strip()=='1'
    query(db,"UPDATE zsjos_user_relation_scene SET status=1,remark='管理员配置' WHERE code='partner_lead_specified_assignment' AND tenant_id=991;")
    query(db,sql)
    assert query(db,"SELECT status,HEX(remark) FROM zsjos_user_relation_scene WHERE code='partner_lead_specified_assignment' AND tenant_id=991;").strip()=='1\t'+'管理员配置'.encode().hex().upper()
    print('PASS controlled replay/repeatability, tenant separation, zero seeded relations, administrator configuration retained, version and UTF-8 HEX:',db)
    if args.sync_local:
        before=preserved(LOCAL)
        grants=query(LOCAL,"SELECT COUNT(*),COALESCE(SUM(CRC32(CONCAT(role_id,':',menu_id))),0) FROM system_role_menu;")
        query(LOCAL,sql);query(LOCAL,sql)
        assert preserved(LOCAL)==before
        assert grants==query(LOCAL,"SELECT COUNT(*),COALESCE(SUM(CRC32(CONCAT(role_id,':',menu_id))),0) FROM system_role_menu;")
        assert shape(LOCAL)==shape(db)
        assert query(LOCAL,"SELECT HEX(name) FROM zsjos_user_relation_scene WHERE code='partner_lead_specified_assignment' AND tenant_id=1;").strip()=='兼职客资指定分配'.encode().hex().upper()
        print('PASS local additive sync, repeatability, scoped schema equality, relations/leads/grants preserved, UTF-8 HEX')
