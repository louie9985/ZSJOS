# -*- coding: utf-8 -*-
"""Retained synthetic MySQL fixtures only; never opens the business database."""
import datetime
from pathlib import Path
from test_calendar_notifications import query, table, ROOT
SQL=(ROOT/'script/sql/mysql/tools/repair_relation_1016.sql').read_text(encoding='utf-8')
PREFIX='review1016_'+datetime.datetime.now().strftime('%m%d%H%M%S')
# Use real production table definitions for every mutated business table.
TABLES=['zsjos_service_relation','zsjos_delivery_class','zsjos_student_collaborator_assignment_log','zsjos_business_task','zsjos_collaboration_group']
# Reference fixtures contain only the fields this fixed-target repair reads.
REF="""
CREATE TABLE system_users(id bigint primary key,status int,deleted bit,dept_id bigint,tenant_id bigint,nickname varchar(64)) ENGINE=InnoDB;
INSERT INTO system_users VALUES(400,0,0,1051,1,'fixture'),(47,0,0,1051,1,'fixture'),(525,0,0,1051,1,'fixture');
CREATE TABLE system_role(id bigint primary key,code varchar(64)) ENGINE=InnoDB;
INSERT INTO system_role VALUES(1,'study_planner');
CREATE TABLE system_user_role(user_id bigint,role_id bigint) ENGINE=InnoDB;
INSERT INTO system_user_role VALUES(400,1);
CREATE TABLE system_dept(id bigint primary key,name varchar(64),deleted bit) ENGINE=InnoDB;
INSERT INTO system_dept VALUES(1051,'fixture',0);
CREATE TABLE zsjos_user_relation(scene varchar(64),source_user_id bigint,target_user_id bigint,status int,deleted bit,tenant_id bigint) ENGINE=InnoDB;
INSERT INTO zsjos_user_relation VALUES('study_planner_content_director',400,47,0,0,1),('content_director_operator',47,525,0,0,1);
CREATE TABLE zsjos_exam_schedule(id bigint primary key,record_status varchar(32),deleted bit,tenant_id bigint,schedule_type varchar(16),exact_date date,start_date date,end_date date,schedule_name varchar(64)) ENGINE=InnoDB;
INSERT INTO zsjos_exam_schedule VALUES(30,'PUBLISHED',0,1,'EXACT','2099-01-01',NULL,NULL,'fixture');
CREATE TABLE zsjos_student_contact_config_version(id bigint primary key,status varchar(32),deleted bit,tenant_id bigint,first_contact_timeout_minutes int) ENGINE=InnoDB;
INSERT INTO zsjos_student_contact_config_version VALUES(2,'published',0,1,75);
CREATE TABLE zsjos_order(id bigint primary key,order_no varchar(64)) ENGINE=InnoDB;
INSERT INTO zsjos_order VALUES(8822,'fixture-order');
CREATE TABLE zsjos_data_repair_backup(id bigint primary key auto_increment,repair_key varchar(100),table_name varchar(64),row_id bigint,column_name varchar(64),old_value longtext,backed_up_at datetime default current_timestamp) ENGINE=InnoDB;
"""
def setup(db):
 query(None,'CREATE DATABASE '+db+' CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;')
 query(db,''.join(table(t) for t in TABLES)+REF)
 # Fill mandatory columns from real schema using information_schema, with explicit business values.
 required=query(db,"SELECT column_name,data_type FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_service_relation' AND is_nullable='NO' AND column_default IS NULL AND extra NOT LIKE '%auto_increment%'")[0]
 values={}
 for line in required.splitlines():
  name,typ=line.split('\t');values[name]="'fixture'" if typ in ['varchar','text'] else "'2026-10-14 00:00:00'" if typ in ['datetime','timestamp','date'] else '0'
 values.update(id='1016',person_id='8150',order_id='8822',order_item_id='9323',registration_case_id='1019',tenant_id='1',status="'active'",acceptance_status="'pending'",version='0')
 query(db,'INSERT INTO zsjos_service_relation('+','.join(values)+') VALUES('+','.join(values.values())+');')
def approval(db):
 rows=query(db,"SELECT column_name,data_type FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_service_relation' ORDER BY ordinal_position")[0]
 parts=[]
 for line in rows.splitlines():
  name,typ=line.split('\t');parts.extend(["'"+name+"'",'CAST(`'+name+'` AS UNSIGNED)' if typ=='bit' else '`'+name+'`'])
 return "SET @repair_1016_expected_version=0; SET @repair_1016_expected_sha=(SELECT SHA2(CAST(JSON_OBJECT("+','.join(parts)+") AS CHAR),256) FROM zsjos_service_relation WHERE id=1016);\n"
def counts(db):
 return query(db,';'.join('SELECT COUNT(*) FROM '+t for t in TABLES+['zsjos_data_repair_backup']))[0]
if __name__=='__main__':
 for case in ['success','missing_approval','wrong_version','missing_order','write_failure','later_edit']:
  db=PREFIX+'_'+case;setup(db);before=counts(db);prefix=approval(db)
  if case=='missing_approval':prefix=''
  if case=='wrong_version':prefix+='SET @repair_1016_expected_version=99;'
  if case=='missing_order':query(db,'UPDATE zsjos_order SET id=999 WHERE id=8822;')
  if case=='write_failure':query(db,"CREATE TRIGGER fail_group BEFORE INSERT ON zsjos_collaboration_group FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='injected';")
  _,error=query(db,prefix+SQL,force=True)
  if case in ['success','later_edit']:
   assert 'ERROR' not in error,error
   assert query(db,"SELECT COUNT(*) FROM zsjos_data_repair_backup WHERE repair_key='relation-1016-v2'")[0]=='7'
   after=counts(db);query(db,SQL);assert counts(db)==after
   encoded=query(db,"SELECT HEX(class_name) FROM zsjos_delivery_class WHERE creator='repair-1016'")[0]
   assert bytes.fromhex(encoded).decode('utf-8') == '技能提升·大健康轻创 2026秋（班主任：梁颖）'
   assert query(db,"SELECT TIMESTAMPDIFF(MINUTE,create_time,due_at) FROM zsjos_business_task")[0]=='75'
   if case=='later_edit':
    query(db,"UPDATE zsjos_service_relation SET version=version+1 WHERE id=1016;")
    _,error=query(db,SQL,force=True);assert 'ERROR' in error;assert counts(db)==after
  else:
   assert 'ERROR' in error,case
   assert counts(db)==before,(case,counts(db),before)
   if case=='write_failure':
    query(db,'DROP TRIGGER fail_group;');query(db,approval(db)+SQL)
  print('PASS',case,db,flush=True)
