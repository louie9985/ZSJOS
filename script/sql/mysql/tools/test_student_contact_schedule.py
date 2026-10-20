# -*- coding: utf-8 -*-
"""V330 isolated MySQL fixtures, never applies to the shared application database."""
from pathlib import Path
import datetime
from test_role_menu_replay import Client
root=Path(__file__).resolve().parents[4]
sql=(root/'script/sql/mysql/migrations/V330__student_contact_optional_schedule.sql').read_text(encoding='utf-8')
prefix='zsjos_schedule_'+datetime.datetime.now().strftime('%Y%m%d%H%M%S')
admin=Client('yudao-mysql','mysql')
def fixture(suffix):
 name=prefix+'_'+suffix;admin.query('CREATE DATABASE '+name+' CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci');c=Client('yudao-mysql',name)
 for table in ['zsjos_schema_version','zsjos_module_schema_version','zsjos_student_contact_config_version']:
  c.query('CREATE TABLE '+table+' LIKE '+chr(96)+'ruoyi-vue-pro'+chr(96)+'.'+table)
 c.query('CREATE TABLE zsjos_student_contact_record(id BIGINT PRIMARY KEY,task_id BIGINT NOT NULL,next_contact_at DATETIME NOT NULL,checklist_snapshot_json JSON,tenant_id BIGINT,UNIQUE KEY task(tenant_id,task_id))')
 c.query("INSERT INTO zsjos_student_contact_record VALUES(1,20,'2026-10-20 12:00:00',NULL,1)")
 c.query("INSERT INTO zsjos_student_contact_config_version(id,version_no,status,first_contact_timeout_minutes,study_plan_timeout_minutes,checklist_json,quick_notes_json,collaborator_tabs_json,forms_json,version,tenant_id) VALUES(1,1,'published',120,1440,'[]','[]','{}','{}',0,1),(2,2,'draft',120,1440,'[]','[]','{}','{}',0,1)")
 return c
def verify(c):
 assert c.query("SELECT first_contact_timeout_minutes,study_plan_timeout_minutes FROM zsjos_student_contact_config_version WHERE tenant_id=1 AND status='published'")=='120\t4320'
 assert c.query("SELECT status,study_plan_timeout_minutes FROM zsjos_student_contact_config_version WHERE id=1")=='archived\t1440'
 assert c.query("SELECT study_plan_timeout_minutes FROM zsjos_student_contact_config_version WHERE id=2")=='4320'
 assert c.query("SELECT task_id,next_contact_at FROM zsjos_student_contact_record WHERE id=1")=='20\t2026-10-20 12:00:00'
 assert c.query("SELECT COUNT(*) FROM zsjos_schema_version WHERE version='V330'")=='1'
 assert c.query("SELECT COUNT(*) FROM zsjos_module_schema_version WHERE module_code='core' AND version='V330'")=='1'
 assert c.query("SELECT HEX(description) FROM zsjos_schema_version WHERE version='V330'")=='学员联系预约选填与学习计划72小时'.encode().hex().upper()
def fail(c):
 try:c.query(sql)
 except RuntimeError:pass
 else:raise AssertionError('expected migration failure')
 assert c.query("SELECT COUNT(*) FROM zsjos_schema_version WHERE version='V330'")=='0'
 assert c.query("SELECT COUNT(*) FROM zsjos_module_schema_version WHERE version='V330'")=='0'
a=fixture('fresh');a.query(sql);a.query(sql);verify(a)
a.query('INSERT INTO zsjos_student_contact_record VALUES(2,NULL,NULL,NULL,1),(3,NULL,NULL,NULL,1)');assert a.query('SELECT COUNT(*) FROM zsjos_student_contact_record WHERE task_id IS NULL')=='2'
b=fixture('partial');b.query('ALTER TABLE zsjos_student_contact_record MODIFY task_id BIGINT NULL');b.query(sql);verify(b)
c=fixture('ledger');c.query("CREATE TRIGGER reject_schedule BEFORE INSERT ON zsjos_module_schema_version FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='fixture failure'");fail(c)
assert c.query("SELECT status,study_plan_timeout_minutes FROM zsjos_student_contact_config_version WHERE id=1")=='published\t1440'
c.query('DROP TRIGGER reject_schedule');c.query(sql);verify(c)
d=fixture('missing');d.query('ALTER TABLE zsjos_student_contact_record DROP COLUMN checklist_snapshot_json');fail(d);d.query('ALTER TABLE zsjos_student_contact_record ADD checklist_snapshot_json JSON');d.query(sql);verify(d)
print('PASS V330 first/replay, partial and prerequisite recovery, ledger rollback, unchanged old records/config, nullable unique tasks and UTF-8 HEX; retained',prefix)
