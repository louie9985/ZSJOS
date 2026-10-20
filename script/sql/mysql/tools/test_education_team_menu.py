# -*- coding: utf-8 -*-
"""Retained MySQL fixtures for V326; never changes real menus or role assignments."""
import datetime
from pathlib import Path
from test_role_menu_replay import Client
ROOT=Path(__file__).resolve().parents[4]
sql=(ROOT/'script/sql/mysql/migrations/V326__education_team_menu.sql').read_text(encoding='utf-8')
prefix='zsjos_edu_'+datetime.datetime.now().strftime('%Y%m%d%H%M%S')
admin=Client('yudao-mysql','mysql')
def fixture(suffix,domain=True):
 name=prefix+'_'+suffix;admin.query('CREATE DATABASE '+name+' CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci');c=Client('yudao-mysql',name)
 for table in ['system_menu','system_role_menu','zsjos_schema_version','zsjos_module_schema_version']:c.query('CREATE TABLE '+table+' LIKE `ruoyi-vue-pro`.'+table)
 c.query("CREATE TABLE zsjos_performance_org(id BIGINT,tenant_id BIGINT,deleted BIT"+(",business_domain VARCHAR(16)" if domain else '')+")")
 c.query("INSERT INTO system_menu(id,name,permission,type,parent_id,path,component) VALUES (1,'工作台','',1,0,'/zsjos',''),(2,'下属员工','zsjos:subordinate-sales:query',2,1,'subordinate-sales','zsjos/subordinateSales/index'); INSERT INTO system_role_menu(id,role_id,menu_id,tenant_id,deleted) VALUES(1,1,2,1,0)")
 return c
def verify(c):
 assert c.query("SELECT COUNT(*) FROM system_menu WHERE permission LIKE 'zsjos:education-team:%' AND deleted=0")=='6'
 assert c.query("SELECT HEX(name) FROM system_menu WHERE permission='zsjos:education-team:query'")=='教务团队'.encode().hex().upper()
 assert c.query("SELECT COUNT(*) FROM zsjos_schema_version WHERE version='V326'")=='1'
 assert c.query("SELECT COUNT(*) FROM zsjos_module_schema_version WHERE module_code='core' AND version='V326'")=='1'
 assert c.query('SELECT COUNT(*),SUM(id),SUM(menu_id) FROM system_role_menu')=='1	1	2'
def fails(c):
 try:c.query(sql)
 except RuntimeError:pass
 else:raise AssertionError('migration should fail')
 assert c.query("SELECT COUNT(*) FROM zsjos_schema_version WHERE version='V326'")=='0'
 assert c.query("SELECT COUNT(*) FROM zsjos_module_schema_version WHERE version='V326'")=='0'
fresh=fixture('fresh');fresh.query(sql);fresh.query(sql);verify(fresh)
fresh.query("UPDATE system_menu SET sort=123,status=1 WHERE permission='zsjos:education-team:query'");fresh.query(sql);assert fresh.query("SELECT sort,status FROM system_menu WHERE permission='zsjos:education-team:query'")=='123	1'
missing=fixture('missing',False);fails(missing);missing.query('ALTER TABLE zsjos_performance_org ADD business_domain VARCHAR(16)');missing.query(sql);verify(missing)
partial=fixture('partial');partial.query("INSERT INTO system_menu(name,permission,type,parent_id,path,component) VALUES('教务团队','zsjos:education-team:query',2,1,'education-team','zsjos/educationTeam/index')");partial.query(sql);verify(partial)
conflict=fixture('conflict');conflict.query("INSERT INTO system_menu(name,permission,type,parent_id,path,component) VALUES('已有菜单','zsjos:education-team:query',2,1,'wrong','wrong')");fails(conflict)
conflict.query("UPDATE system_menu SET name='教务团队',path='education-team',component='zsjos/educationTeam/index' WHERE permission='zsjos:education-team:query'");conflict.query(sql);verify(conflict)
ledger=fixture('ledger');ledger.query("CREATE TRIGGER reject_v326 BEFORE INSERT ON zsjos_module_schema_version FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='test ledger write failure'");fails(ledger);assert ledger.query("SELECT COUNT(*) FROM system_menu WHERE permission LIKE 'zsjos:education-team:%'")=='0';ledger.query('DROP TRIGGER reject_v326');ledger.query(sql);verify(ledger)
print('PASS: MySQL initial/replay, partial recovery, prerequisite guard, conflict guard, ledger rollback, UTF-8 HEX, grants unchanged; retained',prefix)
