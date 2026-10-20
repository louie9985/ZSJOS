# -*- coding: utf-8 -*-
"""Execute production mapper SQL and V334 only in newly created, retained MySQL fixtures."""
import datetime
import html
import json
import re
from pathlib import Path
from test_role_menu_replay import Client

ROOT = Path(__file__).resolve().parents[4]
JAVA = ROOT / 'backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos'
name = 'zsjos_education_daily_' + datetime.datetime.now().strftime('%Y%m%d%H%M%S')
Client('yudao-mysql', 'mysql').query('CREATE DATABASE ' + name + ' CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci')
c = Client('yudao-mysql', name)


def annotation_sql(path, method):
    source = path.read_text(encoding='utf-8')
    end = source.index(' ' + method + '(')
    begin = source.rindex('@Select(', 0, end)
    expression = source[begin:end]
    expression = expression.replace('USERS', '"(20,30)"')
    sql = ''.join(json.loads(value) for value in re.findall(r'"(?:[^"\\]|\\.)*"', expression))
    sql = re.sub(r'<foreach[^>]*>.*?</foreach>', '(20,30)', sql)
    sql = html.unescape(sql.replace('<script>', '').replace('</script>', ''))
    for key, value in {'tenant': '1', 'start': "'2026-10-19 00:00:00'", 'end': "'2026-10-20 00:00:00'"}.items():
        sql = sql.replace('#{' + key + '}', value)
    return sql


c.query("""
CREATE TABLE zsjos_lead(id BIGINT PRIMARY KEY,tenant_id BIGINT,deleted BIT DEFAULT 0,submitted_at DATETIME,
 submission_source_type VARCHAR(64),source_user_id BIGINT,owner_identity VARCHAR(32),owner_user_id BIGINT,
 assignment_status VARCHAR(32),recycle_source_owner_user_id BIGINT);
CREATE TABLE zsjos_business_event(id BIGINT PRIMARY KEY,tenant_id BIGINT,deleted BIT DEFAULT 0,aggregate_type VARCHAR(32),
 aggregate_id BIGINT,event_type VARCHAR(64),operator_user_id BIGINT,idempotency_key VARCHAR(128),related_object_refs JSON,occurred_at DATETIME);
CREATE TABLE zsjos_lead_follow_up_record(id BIGINT PRIMARY KEY,tenant_id BIGINT,deleted BIT DEFAULT 0,lead_id BIGINT,operator_user_id BIGINT,owner_identity_snapshot VARCHAR(32),occurred_at DATETIME);
CREATE TABLE zsjos_opportunity_follow_up_record LIKE zsjos_lead_follow_up_record;
CREATE TABLE zsjos_service_relation(id BIGINT,tenant_id BIGINT,person_id BIGINT,deleted BIT DEFAULT 0);
CREATE TABLE zsjos_student_contact_record(id BIGINT,tenant_id BIGINT,deleted BIT DEFAULT 0,service_relation_id BIGINT,contact_type VARCHAR(64),operator_user_id BIGINT,submitted_at DATETIME,successful BIT);
CREATE TABLE zsjos_lead_assignment_history(id BIGINT PRIMARY KEY,tenant_id BIGINT,deleted BIT DEFAULT 0,lead_id BIGINT,action_type VARCHAR(64),from_owner_user_id BIGINT,to_owner_user_id BIGINT,owner_identity_snapshot VARCHAR(32),occurred_at DATETIME);
INSERT INTO zsjos_lead(id,tenant_id,submitted_at,submission_source_type,source_user_id,owner_identity,owner_user_id,assignment_status) VALUES
 (1,1,'2026-10-19','education_self_sourced',99,'education',30,'owned'),
 (2,1,'2026-10-18','education_self_sourced',20,'sales',99,'owned'),
 (3,1,'2026-10-19','education_self_sourced',20,'education',20,'owned'),
 (4,1,'2026-10-19','sales_self_sourced',20,'sales',20,'owned'),
 (5,1,'2026-10-19','education_self_sourced',99,'education',30,'owned'),
 (6,2,'2026-10-19','education_self_sourced',20,'education',20,'owned'),
 (7,1,'2026-10-20','education_self_sourced',20,'education',20,'owned');
INSERT INTO zsjos_business_event(id,tenant_id,aggregate_type,aggregate_id,event_type,operator_user_id,idempotency_key,occurred_at) VALUES
 (1,1,'lead',1,'lead_flow_operation',20,'lead-flow:submission:1','2026-10-19'),
 (2,1,'lead',1,'lead_flow_operation',20,'lead-flow:submission:1','2026-10-19'),
 (3,1,'lead',2,'lead_flow_operation',20,'lead-flow:submission:2','2026-10-19'),
 (4,1,'lead',4,'lead_flow_operation',20,'lead-flow:submission:4','2026-10-19'),
 (5,1,'lead',5,'lead_flow_operation',30,'lead-flow:submission:5','2026-10-19'),
 (6,2,'lead',6,'lead_flow_operation',20,'lead-flow:submission:6','2026-10-19'),
 (7,1,'lead',7,'lead_flow_operation',20,'lead-flow:submission:7','2026-10-20');
INSERT INTO zsjos_lead_follow_up_record(id,tenant_id,lead_id,operator_user_id,owner_identity_snapshot,occurred_at) VALUES
 (1,1,2,20,'education','2026-10-19'),(2,1,2,20,'education','2026-10-19 23:59:59'),
 (3,1,1,20,'education','2026-10-19'),(4,1,2,20,'sales','2026-10-19'),
 (5,1,2,20,NULL,'2026-10-19'),(6,1,2,20,'education','2026-10-20'),
 (7,2,2,20,'education','2026-10-19'),(8,1,2,20,'education','2026-10-19');
INSERT INTO zsjos_opportunity_follow_up_record(id,tenant_id,lead_id,operator_user_id,owner_identity_snapshot,occurred_at) VALUES
 (1,1,2,20,'education','2026-10-19'),(2,1,2,20,'sales','2026-10-19');
INSERT INTO zsjos_business_event(id,tenant_id,aggregate_type,aggregate_id,event_type,related_object_refs) VALUES
 (8,1,'lead',2,'lead_follow_up_recorded','{"followUpRecordId":8,"generationSource":"education_self_sourced_auto"}');
INSERT INTO zsjos_service_relation VALUES (1,1,500,0),(2,1,500,1),(3,1,501,0),(4,2,502,0);
INSERT INTO zsjos_student_contact_record VALUES
 (1,1,0,1,'student_first_contact',20,'2026-10-19',1),
 (2,1,0,1,'student_study_plan',20,'2026-10-19',1),
 (3,1,0,2,'student_contact',20,'2026-10-19',1),
 (4,1,0,3,'student_contact',20,'2026-10-19',0),
 (5,1,0,1,'student_contact',30,'2026-10-19',1),
 (6,1,0,3,'student_contact',20,'2026-10-20',1),
 (7,1,0,3,'delivery_stage',20,'2026-10-19',1),
 (8,1,0,4,'student_contact',20,'2026-10-19',1),
 (9,1,1,3,'student_contact',20,'2026-10-19',1),
 (10,1,0,3,'student_contact',20,NULL,1);
""")
mapper = JAVA / 'dal/mysql/educationteam/EducationTeamDailyMapper.java'
assert sorted(c.query(annotation_sql(mapper, 'selectNewIntentions')).splitlines()) == ['20\t1', '30\t5']
assert c.query(annotation_sql(mapper, 'selectOldIntentionFollowUps')).splitlines() == ['20'] * 3
contacts = c.query(annotation_sql(mapper, 'selectContactedStudents')).splitlines()
assert len([row for row in contacts if row.startswith('20\t')]) == 2, contacts
assert len([row for row in contacts if row.startswith('30\t')]) == 1, contacts
print('PASS new intentions/provider/review/dedup; old lead and opportunity records/automatic/date/identity; contacts/student/failed/multiple services/tenant')

identity = (JAVA / 'dal/mysql/lead/SubordinateSalesIdentitySql.java').read_text(encoding='utf-8').split('CONDITION =', 1)[1].split(';', 1)[0]
identity = ''.join(json.loads(s) for s in re.findall(r'"(?:[^"\\]|\\.)*"', identity))
c.query("""
INSERT INTO zsjos_lead(id,tenant_id,assignment_status,recycle_source_owner_user_id,owner_identity) VALUES
 (10,1,'recycle_pending',20,NULL),(11,1,'recycle_pending',20,NULL),(12,1,'recycle_pending',20,NULL),(13,1,'recycle_pending',20,'education');
INSERT INTO zsjos_lead_assignment_history VALUES
 (1,1,0,10,'recycle',20,NULL,'sales','2026-10-19'),
 (2,1,0,11,'recycle',20,NULL,'education','2026-10-19'),
 (3,1,0,12,'accept',NULL,20,'sales','2026-10-18'),
 (4,1,0,12,'recycle',20,NULL,NULL,'2026-10-19'),
 (5,1,0,13,'recycle',20,NULL,'sales','2026-10-19');
""")
assert c.query('SELECT id FROM zsjos_lead WHERE tenant_id=1 AND ' + identity + ' ORDER BY id').splitlines() == ['2', '4', '10', '12']
c.query("UPDATE zsjos_lead_assignment_history SET action_type='transfer' WHERE id=4")
assert '12' not in c.query('SELECT id FROM zsjos_lead WHERE ' + identity).splitlines()
print('PASS sales current/recycled/adjacent-proof/conflicting-identity/latest-transition SQL')

sales_mapper = JAVA / 'dal/mysql/lead/SubordinateSalesDailyMapper.java'
c.query("""
INSERT INTO zsjos_lead_assignment_history VALUES (20,1,0,2,'accept',NULL,20,'sales','2026-10-19 01:00:00'),
 (21,1,0,2,'transfer',20,30,'education','2026-10-19 03:00:00');
INSERT INTO zsjos_business_event(id,tenant_id,aggregate_type,aggregate_id,event_type,operator_user_id,occurred_at,related_object_refs) VALUES
 (20,1,'lead',2,'lead_qualified_valid',20,'2026-10-19 02:00:00','{}'),
 (21,1,'lead',2,'lead_qualified_valid',30,'2026-10-19 04:00:00','{}'),
 (22,1,'lead',2,'lead_qualified_valid',20,'2026-10-19 02:00:00','{"generationSource":"sales_self_sourced_auto"}');
""")
assert c.query(annotation_sql(sales_mapper, 'selectQualifications')) == '20\t2'
print('PASS sales qualification attribution survives later education transfer and excludes automatic qualification')

for table in ['system_menu', 'system_role_menu', 'zsjos_schema_version', 'zsjos_module_schema_version']:
    c.query('CREATE TABLE ' + table + ' LIKE `ruoyi-vue-pro`.' + table)
menu = "INSERT INTO system_menu(id,name,permission,type,parent_id,path,component) VALUES(6814,'下属员工','zsjos:subordinate-sales:query',2,1,'subordinate-sales','zsjos/subordinateSales/index')"
c.query(menu)
c.query("INSERT INTO system_role_menu(id,role_id,menu_id,tenant_id,deleted) VALUES(1,1,6814,1,0)")
grants = c.query('SELECT * FROM system_role_menu')
sql = (ROOT / 'script/sql/mysql/migrations/V334__restore_subordinate_sales_menu.sql').read_text(encoding='utf-8')


def verify():
    assert c.query('SELECT HEX(name) FROM system_menu WHERE id=6814') == '下属销售'.encode().hex().upper()
    assert c.query("SELECT COUNT(*) FROM zsjos_schema_version WHERE version='V334'") == '1'
    assert c.query("SELECT COUNT(*) FROM zsjos_module_schema_version WHERE module_code='core' AND version='V334'") == '1'
    assert c.query('SELECT * FROM system_role_menu') == grants


def fails():
    try:
        c.query(sql)
    except RuntimeError:
        pass
    else:
        raise AssertionError('Expected guarded failure')
    assert c.query("SELECT COUNT(*) FROM zsjos_schema_version WHERE version='V334'") == '0'
    assert c.query("SELECT COUNT(*) FROM zsjos_module_schema_version WHERE version='V334'") == '0'


c.query("UPDATE system_menu SET component='conflict' WHERE id=6814")
fails()
c.query("UPDATE system_menu SET component='zsjos/subordinateSales/index' WHERE id=6814")
c.query("CREATE TRIGGER reject_v334 BEFORE INSERT ON zsjos_module_schema_version FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='fixture ledger failure'")
fails()
assert c.query('SELECT HEX(name) FROM system_menu WHERE id=6814') == '下属员工'.encode().hex().upper()
c.query('DROP TRIGGER reject_v334')
c.query(sql)
verify()
c.query(sql)
verify()
# Partial application recovery with the renamed menu already persisted, without any real data changes.
c.query("DELETE FROM zsjos_schema_version WHERE version='V334'; DELETE FROM zsjos_module_schema_version WHERE version='V334'")
c.query(sql)
verify()
print('PASS V334 initial/replay/prerequisite/conflict/ledger rollback/partial recovery/UTF-8/grants; retained', name)
