#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Verify V257 twice in an isolated retained database in the existing local MySQL container.
No services are started/stopped, no business data is copied, and no databases are deleted.
"""
import datetime
import hashlib
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[4]
MIGRATION = ROOT / 'script/sql/mysql/migrations/V257__education_self_sourced.sql'
TABLES = {'zsjos_lead': 'owner_identity', 'zsjos_lead_assignment_history': 'owner_identity_snapshot',
          'zsjos_opportunity_follow_up_record': 'owner_identity_snapshot', 'zsjos_order': 'formal_owner_identity', 'zsjos_lead_complaint': 'owner_identity_snapshot',
          'zsjos_lead_follow_up_record': 'owner_identity_snapshot'}

def query(database, sql):
    result = subprocess.run(['docker', 'exec', '-i', 'yudao-mysql', 'sh', '-c',
        'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql --default-character-set=utf8mb4 '
        '-uroot --batch --raw --skip-column-names ' + database],
        input=('SET NAMES utf8mb4;\n' + sql).encode('utf-8'), capture_output=True)
    if result.returncode:
        raise RuntimeError(result.stderr.decode('utf-8', errors='replace'))
    return result.stdout.decode('utf-8').strip()

def main():
    database = 'education_verify_' + datetime.datetime.now().strftime('%Y%m%d%H%M%S')
    query('', f'CREATE DATABASE `{database}` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci')
    # Minimal predecessor fixtures exercise ALTER and metadata, without copying live records.
    for table in TABLES:
        query(database, f'CREATE TABLE `{table}` (id bigint PRIMARY KEY); INSERT INTO `{table}` VALUES (1)')
    for table in ['system_menu', 'system_role_menu', 'system_notify_template', 'zsjos_schema_version']:
        query(database, f'CREATE TABLE `{table}` LIKE `ruoyi-vue-pro`.`{table}`')
    query(database, "INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,icon,component,status,visible,keep_alive,always_show,creator,updater,deleted) VALUES (6735,'工作台','',1,1,0,'/zsjos','','',0,1,0,1,'test','test',0)")
    query(database, "INSERT INTO system_role_menu (role_id,menu_id,tenant_id) VALUES (1,6735,1)")
    query(database, "INSERT INTO system_notify_template (name,code,nickname,scene_code,title,summary,content,type,params,status,remark,creator,updater,deleted) VALUES ('fixture','ZSJOS_LEAD_SOURCE_LINKED','fixture','zsjos.lead.created','fixture','{{operator.name}}销售提交客资{{lead.no}}（客资编号），已关联你为客资来源。','{{operator.name}}销售提交客资{{lead.no}}（客资编号），已关联你为客资来源。',2,'[]',0,'fixture','migration-V080','administrator',0)")
    before = hashlib.sha256(query(database, 'SELECT * FROM system_role_menu ORDER BY id').encode()).hexdigest()
    for run in range(2):
        query(database, MIGRATION.read_text(encoding='utf-8'))
        for table, column in TABLES.items():
            assert query(database, f'SELECT COUNT(*) FROM `{table}` WHERE `{column}` IS NULL') == '1'
        assert query(database, "SELECT COUNT(*) FROM system_menu WHERE permission='zsjos:lead:education-self-sourced:create'") == '1'
        assert query(database, "SELECT HEX(name) FROM system_menu WHERE permission='zsjos:lead:education-self-sourced:create'") == '教务自拓'.encode().hex().upper()
        assert query(database, "SELECT path FROM system_menu WHERE permission='zsjos:lead:education-self-sourced:create'") == 'leads/education-self-sourced'
        assert query(database, "SELECT COUNT(*) FROM zsjos_schema_version WHERE version='V257'") == '1'
        assert hashlib.sha256(query(database, 'SELECT * FROM system_role_menu ORDER BY id').encode()).hexdigest() == before
        assert 'lead.submitterIdentityLabel' in query(database, "SELECT content FROM system_notify_template WHERE code='ZSJOS_LEAD_SOURCE_LINKED'")
    # Administrator-authored text must survive replay even if it resembles the old default.
    query(database, "UPDATE system_notify_template SET content='管理员维护',updater='administrator' WHERE code='ZSJOS_LEAD_SOURCE_LINKED'")
    query(database, MIGRATION.read_text(encoding='utf-8'))
    assert query(database, "SELECT HEX(content) FROM system_notify_template WHERE code='ZSJOS_LEAD_SOURCE_LINKED'") == '管理员维护'.encode().hex().upper()
    print('PASS: repeatable schema/menu/version, unchanged historical identity and grants, UTF-8 HEX, custom template protection')
    print('Retained controlled database:', database)

if __name__ == '__main__':
    main()
