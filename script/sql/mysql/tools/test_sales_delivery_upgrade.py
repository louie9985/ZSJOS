# -*- coding: utf-8 -*-
"""Compare a synthetic V370 predecessor upgraded by V371 with a fresh baseline."""
import subprocess, sys, uuid
from pathlib import Path
container=sys.argv[1]; fresh=sys.argv[2]; upgrade='sm_up_' + uuid.uuid4().hex[:8]
def run(sql):
 p=subprocess.run(['docker','exec','-i',container,'mysql','--default-character-set=utf8mb4','-uroot','--batch','--skip-column-names'],input=sql.encode(),stdout=subprocess.PIPE,stderr=subprocess.PIPE)
 if p.returncode: raise RuntimeError(p.stderr.decode(errors='replace'))
 return p.stdout.decode().strip()
run(f'CREATE DATABASE `{upgrade}` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;')
for table in ['system_menu','zsjos_schema_version','zsjos_module_schema_version','zsjos_performance_target','zsjos_performance_org','zsjos_delivery_class','zsjos_media_student_talk_record','zsjos_order']:
 run(f'CREATE TABLE `{upgrade}`.`{table}` LIKE `{fresh}`.`{table}`;')
 if table not in ('zsjos_delivery_class','zsjos_order'): run(f'INSERT INTO `{upgrade}`.`{table}` SELECT * FROM `{fresh}`.`{table}`;')
run(f"USE `{upgrade}`; DELETE FROM system_menu WHERE permission IN ('zsjos:sales:eligible','zsjos:lead:receive','zsjos:sales:cross-department','zsjos:sales:boundary-configure','zsjos:media-student:history-review'); DELETE FROM zsjos_schema_version WHERE version='V371'; DELETE FROM zsjos_module_schema_version WHERE version='V371'; ALTER TABLE zsjos_performance_target DROP COLUMN merged_delivery_target_id; ALTER TABLE zsjos_performance_org DROP COLUMN sales_boundary_root; ALTER TABLE zsjos_media_student_talk_record DROP COLUMN service_relation_id; ALTER TABLE zsjos_delivery_class DROP INDEX uk_delivery_auto_class; ALTER TABLE zsjos_delivery_class DROP COLUMN auto_class_guard; ALTER TABLE zsjos_delivery_class DROP COLUMN exam_month; ALTER TABLE zsjos_order DROP COLUMN agreed_exam_month;")
run(f"USE `{upgrade}`; INSERT INTO zsjos_delivery_class(id,class_no,class_name,class_kind,homeroom_user_id,dept_id,tenant_id) VALUES(100,'OLD100','旧班','UNSCHEDULED',20,10,1),(101,'OLD101','旧班无部门','UNSCHEDULED',21,NULL,1),(102,'OLD102','旧普通班','NORMAL',22,10,1),(103,'OLD103','旧普通班同人','NORMAL',22,10,1);")
run(f"USE `{upgrade}`; INSERT INTO zsjos_order(id,order_no,status,agreed_exam_time,tenant_id) VALUES(900,'OLD900','effective','明年三月',1);")
order_before = run(f"SELECT id,agreed_exam_time FROM `{upgrade}`.zsjos_order ORDER BY id")
before = run(f"SELECT id,class_no,homeroom_user_id,COALESCE(dept_id,-1) FROM `{upgrade}`.zsjos_delivery_class ORDER BY id")
source=(Path(__file__).resolve().parents[1]/'migrations/V371__sales_delivery_merge.sql').read_text(encoding='utf-8-sig')
run(f'USE `{upgrade}`;\n'+source)
def sig(db):
 return run(f'''SELECT table_name,column_name,ordinal_position,column_type,is_nullable,COALESCE(column_default,'<NULL>'),extra,HEX(column_comment) FROM information_schema.columns WHERE table_schema='{db}' AND table_name IN ('zsjos_performance_target','zsjos_performance_org','zsjos_media_student_talk_record','zsjos_delivery_class','zsjos_order') ORDER BY table_name,ordinal_position;
SELECT index_name,non_unique,seq_in_index,column_name FROM information_schema.statistics WHERE table_schema='{db}' AND table_name='zsjos_delivery_class' ORDER BY index_name,seq_in_index;
SELECT permission,name,type,parent_id,status,deleted FROM `{db}`.system_menu WHERE permission IN ('zsjos:sales:eligible','zsjos:lead:receive','zsjos:sales:cross-department','zsjos:sales:boundary-configure','zsjos:media-student:history-review') ORDER BY permission;
SELECT version FROM `{db}`.zsjos_schema_version WHERE version='V371'; SELECT module_code,version FROM `{db}`.zsjos_module_schema_version WHERE version='V371';''')
assert sig(upgrade)==sig(fresh), 'V371 upgrade differs from fresh in affected schema/metadata/ledger versions'
assert before == run(f"SELECT id,class_no,homeroom_user_id,COALESCE(dept_id,-1) FROM `{upgrade}`.zsjos_delivery_class ORDER BY id")
assert order_before == run(f"SELECT id,agreed_exam_time FROM `{upgrade}`.zsjos_order ORDER BY id")
assert run(f"SELECT COUNT(*) FROM `{upgrade}`.zsjos_order WHERE agreed_exam_month IS NOT NULL") == '0'
assert run(f"SELECT COUNT(*) FROM `{upgrade}`.zsjos_delivery_class WHERE exam_month IS NOT NULL OR auto_class_guard IS NOT NULL") == '0'
print('PASS synthetic V370 -> V371 affected schema (incl. order/class auto-class columns and keys), permission metadata and both ledger versions match fresh; legacy rows unchanged')
