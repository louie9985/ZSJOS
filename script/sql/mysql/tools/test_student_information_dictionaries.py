# -*- coding: utf-8 -*-
"""V324 controlled additive dictionary verification; retained databases; optional local sync."""
import argparse,datetime,json
from test_positioning_single_source import query,ROOT
SQL=ROOT/'script/sql/mysql/migrations/V324__student_information_dictionaries.sql'
TABLES=['system_dict_type','system_dict_data','zsjos_schema_version','zsjos_module_schema_version']
PREFIX='zsjos_student_info_'
STAMP=datetime.datetime.now().strftime('%Y%m%d%H%M%S%f')
def clone(suffix,prerequisites=True):
 db='collection_dict_'+STAMP+'_'+suffix;query('ruoyi-vue-pro',f'CREATE DATABASE {db} CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;')
 for table in TABLES:
  query(db,f'CREATE TABLE {table} LIKE '+chr(96)+'ruoyi-vue-pro'+chr(96)+f'.{table};')
  if prerequisites and table in TABLES[2:]:query(db,f"INSERT INTO {table} SELECT * FROM "+chr(96)+'ruoyi-vue-pro'+chr(96)+f".{table} WHERE version<>'V324';")
 return db
def signature(db):return query(db,'SELECT * FROM system_dict_type ORDER BY id; SELECT * FROM system_dict_data ORDER BY id; SELECT * FROM zsjos_schema_version ORDER BY version; SELECT * FROM zsjos_module_schema_version ORDER BY module_code,version;')
def apply(db):query(db,SQL.read_text(encoding='utf8'))
def verify(db):
 apply(db);before=signature(db);apply(db);assert before==signature(db)
 for suffix,count in [('ethnicity',56),('retraining',2),('education_level',8),('registration_level',4)]:
  assert int(query(db,f"SELECT COUNT(*) FROM system_dict_data WHERE dict_type='{PREFIX+suffix}' AND deleted=0;").strip())==count
 assert query(db,"SELECT HEX(label) FROM system_dict_data WHERE dict_type='zsjos_student_info_registration_level' AND value='equivalent_master';").strip()=='同等学历申硕'.encode().hex().upper()
 print('PASS seed/replay/count/UTF-8',db)
def failure(db):
 before=signature(db)
 try:apply(db)
 except RuntimeError:assert before==signature(db)
 else:raise AssertionError('Expected failure')
if __name__=='__main__':
 p=argparse.ArgumentParser();p.add_argument('--sync-local',action='store_true');args=p.parse_args()
 initial=clone('initial');verify(initial)
 partial=clone('partial');query(partial,"INSERT INTO system_dict_type(name,type,status) VALUES('管理员名称','zsjos_student_info_retraining',0); INSERT INTO system_dict_data(label,value,dict_type,status) VALUES('管理员标签','yes','zsjos_student_info_retraining',1);")
 apply(partial);assert query(partial,"SELECT HEX(label),status FROM system_dict_data WHERE dict_type='zsjos_student_info_retraining' AND value='yes';").strip()=='管理员标签'.encode().hex().upper()+chr(9)+'1'
 verify(partial)
 missing=clone('prerequisite',False);failure(missing)
 bad=clone('ledger_failure');query(bad,"CREATE TRIGGER refuse_ledger BEFORE INSERT ON zsjos_module_schema_version FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='fixture';");failure(bad);query(bad,'DROP TRIGGER refuse_ledger;');verify(bad)
 print('PASS preserve administrator edits/prerequisite failure/transaction rollback/recovery')
 if args.sync_local:
  backup=ROOT/'backups/mysql'/('student-info-dictionaries-'+STAMP+'.json');backup.parent.mkdir(parents=True,exist_ok=True)
  backup.write_text(json.dumps({t:query('ruoyi-vue-pro',f'SELECT * FROM {t};')for t in TABLES},ensure_ascii=False),encoding='utf8')
  verify('ruoyi-vue-pro');print('PASS local sync; scoped backup',backup)
