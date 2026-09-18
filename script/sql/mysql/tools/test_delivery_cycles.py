# -*- coding: utf-8 -*-
"""Scoped V264 execution; retains synthetic verification database. Optional local sync is non-deleting."""
import argparse,datetime,json
from pathlib import Path
from test_positioning_single_source import query,ROOT
SQL=ROOT/'script/sql/mysql/migrations/V264__account_review_delivery_cycles.sql'
TABLES=['zsjos_student_delivery_plan','zsjos_student_delivery_stage','zsjos_student_delivery_defer','zsjos_student_delivery_submission','zsjos_student_delivery_config','system_menu','zsjos_schema_version','zsjos_module_schema_version']
def state(db):
 return query(db,"SELECT id,stage_code,status,trigger_at,due_at,completed_at,version FROM zsjos_student_delivery_stage ORDER BY id; SELECT id,field_values_json,attachment_snapshot_json FROM zsjos_student_delivery_submission ORDER BY id;")
if __name__=='__main__':
 parser=argparse.ArgumentParser();parser.add_argument('--sync-local',action='store_true');args=parser.parse_args();local='ruoyi-vue-pro'
 db='delivery_cycles_verify_'+datetime.datetime.now().strftime('%Y%m%d%H%M%S');query(local,f'CREATE DATABASE `{db}` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;')
 for t in TABLES:query(db,f'CREATE TABLE `{t}` LIKE `{local}`.`{t}`;')
 query(db,"""INSERT INTO zsjos_student_delivery_plan(id,tenant_id,student_person_id,account_id,account_opened_at) VALUES(1,991,1,1,'2026-09-01');
 INSERT INTO zsjos_student_delivery_stage(id,tenant_id,plan_id,account_id,stage_code,trigger_at,status,completed_at) VALUES
 (1,991,1,1,'S0','2026-09-04','COMPLETED','2026-09-05'),(2,991,1,1,'S1','2026-09-06','WAITING',NULL),(3,991,1,1,'S4','2026-09-20','WAITING',NULL);
 INSERT INTO zsjos_student_delivery_submission(tenant_id,stage_id,field_values_json,submitted_by,attachment_snapshot_json) VALUES(991,1,'历史中文正文',1,'历史附件');
 INSERT INTO system_menu(name,permission,type,sort,parent_id,path,component,status) VALUES('查看交付','zsjos:student-delivery:query',3,0,6920,'','',0);""")
 frozen=query(db,'SELECT HEX(field_values_json),HEX(attachment_snapshot_json) FROM zsjos_student_delivery_submission;')
 sql=SQL.read_text(encoding='utf-8');query(db,sql);once=state(db);query(db,sql);assert once==state(db)
 assert '2026-09-12' in once and 'NEEDS_REVIEW' in once
 assert query(db,'SELECT HEX(field_values_json),HEX(attachment_snapshot_json) FROM zsjos_student_delivery_submission;')==frozen
 query(db,"UPDATE zsjos_student_delivery_plan SET status='CLOSED' WHERE id=1; INSERT INTO zsjos_student_delivery_plan(tenant_id,student_person_id,account_id,account_opened_at,status) VALUES(991,1,1,'2026-09-10','CLOSED'),(991,1,1,'2026-09-18','ACTIVE');")
 query(db,"INSERT INTO zsjos_student_delivery_defer(tenant_id,stage_id,requested_by,requested_days,original_due_at,new_due_at,reason,status,idempotency_key) VALUES(991,2,1,2,'2026-09-12','2026-09-14','第一次延期','EFFECTIVE','one'),(991,2,1,2,'2026-09-14','2026-09-16','第二次延期','EFFECTIVE','two');")
 assert query(db,"SELECT HEX(description) FROM zsjos_schema_version WHERE version='V264';").strip()=='账号复盘与交付轮次'.encode().hex().upper()
 print('PASS controlled first/repeat execution, history/Chinese preservation, multiple closed rounds and defers:',db)
 if args.sync_local:
  backup=ROOT/'backups/mysql'/('delivery-cycles-'+datetime.datetime.now().strftime('%Y%m%d%H%M%S')+'.json');backup.parent.mkdir(parents=True,exist_ok=True)
  backup.write_text(json.dumps({t:query(local,f'SELECT * FROM `{t}`;') for t in TABLES if t.startswith('zsjos_student_delivery_')},ensure_ascii=False),encoding='utf-8')
  grants=query(local,'SELECT COUNT(*) FROM system_role_menu;');before=query(local,'SELECT COUNT(*) FROM zsjos_student_delivery_submission; SELECT COUNT(*) FROM zsjos_student_delivery_defer;')
  query(local,sql);once=state(local);query(local,sql);assert once==state(local)
  assert before==query(local,'SELECT COUNT(*) FROM zsjos_student_delivery_submission; SELECT COUNT(*) FROM zsjos_student_delivery_defer;')
  assert grants==query(local,'SELECT COUNT(*) FROM system_role_menu;')
  print('PASS local scoped replay; rows and role grants retained. Backup:',backup)
