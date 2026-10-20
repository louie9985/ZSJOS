# -*- coding: utf-8 -*-
"""Isolated MySQL verification; retains synthetic databases, never changes shared business rows."""
import datetime
from pathlib import Path
from test_role_menu_replay import Client
ROOT = Path(__file__).resolve().parents[4]
SQL = (ROOT/'script/sql/mysql/migrations/V328__student_first_contact_progress.sql').read_text(encoding='utf-8')
prefix = 'zsjos_fc_' + datetime.datetime.now().strftime('%Y%m%d%H%M%S')
admin = Client('yudao-mysql', 'mysql')
def fixture(suffix, prerequisite=True):
    name = prefix + '_' + suffix
    admin.query('CREATE DATABASE ' + name + ' CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci')
    c = Client('yudao-mysql', name)
    for table in ['system_menu','system_role_menu','zsjos_schema_version','zsjos_module_schema_version']:
        c.query('CREATE TABLE ' + table + ' LIKE ' + chr(96) + 'ruoyi-vue-pro' + chr(96) + '.' + table)
    c.query('CREATE TABLE zsjos_student_contact_record(id BIGINT PRIMARY KEY, tenant_id BIGINT, checklist_result_json JSON)')
    c.query('CREATE TABLE zsjos_service_relation(id BIGINT PRIMARY KEY,tenant_id BIGINT,version INT' + (',delivery_stage VARCHAR(40)' if prerequisite else '') + ')')
    c.query("INSERT INTO system_menu(id,name,permission,type,parent_id,path,component) VALUES(1,'我的学员','zsjos:student:query-my',2,0,'my-students','zsjos/myStudents/index'); INSERT INTO system_role_menu(id,role_id,menu_id,tenant_id,deleted) VALUES(1,1,1,1,0)")
    c.query("INSERT INTO zsjos_student_contact_record VALUES(1,1,JSON_ARRAY('voice_learning_plan')); INSERT INTO zsjos_service_relation(id,tenant_id,version) VALUES(1,1,7)")
    return c

def verify(c):
    assert c.query("SELECT COUNT(*) FROM zsjos_schema_version WHERE version='V328'") == '1'
    assert c.query("SELECT COUNT(*) FROM zsjos_module_schema_version WHERE module_code='core' AND version='V328'") == '1'
    assert c.query("SELECT HEX(name) FROM system_menu WHERE permission='zsjos:student-contact:checklist-update'") == '更新首联清单'.encode().hex().upper()
    assert c.query('SELECT COUNT(*),SUM(id),SUM(menu_id) FROM system_role_menu') == '1\t1\t1'
    assert c.query('SELECT study_plan_completed_at_first_contact IS NULL, checklist_snapshot_json IS NULL FROM zsjos_student_contact_record') == '1\t1'
    assert c.query('SELECT first_contact_progress_json IS NULL,version FROM zsjos_service_relation') == '1\t7'
    assert 'voice_learning_plan' in c.query('SELECT checklist_result_json FROM zsjos_student_contact_record')

def fails(c):
    try: c.query(SQL)
    except RuntimeError: pass
    else: raise AssertionError('Expected failure')
    assert c.query("SELECT COUNT(*) FROM zsjos_schema_version WHERE version='V328'") == '0'
    assert c.query("SELECT COUNT(*) FROM zsjos_module_schema_version WHERE version='V328'") == '0'

fresh=fixture('fresh'); fresh.query(SQL); fresh.query(SQL); verify(fresh)
missing=fixture('missing',False); fails(missing); missing.query('ALTER TABLE zsjos_service_relation ADD delivery_stage VARCHAR(40)'); missing.query(SQL); verify(missing)
partial=fixture('partial'); partial.query('ALTER TABLE zsjos_student_contact_record ADD study_plan_completed_at_first_contact BIT(1) NULL'); partial.query(SQL); verify(partial)
ledger=fixture('ledger')
ledger.query("CREATE TRIGGER reject_v328 BEFORE INSERT ON zsjos_module_schema_version FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='fixture ledger failure'"); fails(ledger)
assert ledger.query("SELECT COUNT(*) FROM system_menu WHERE permission='zsjos:student-contact:checklist-update'") == '0'
ledger.query('DROP TRIGGER reject_v328'); ledger.query(SQL); verify(ledger)
conflict=fixture('conflict'); conflict.query("INSERT INTO system_menu(name,permission,type,parent_id,path) VALUES('冲突','zsjos:student-contact:checklist-update',2,0,'conflict')"); fails(conflict)
print('PASS first/replay, prerequisite and partial recovery, ledger rollback, conflict, UTF-8 HEX, grants and historical rows unchanged; retained',prefix)
