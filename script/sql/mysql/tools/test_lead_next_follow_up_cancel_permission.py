# -*- coding: utf-8 -*-
"""Retained MySQL 8 fixtures; no business or role data is modified."""
from test_positioning_single_source import query, ROOT
from datetime import datetime
SQL=(ROOT/'script/sql/mysql/migrations/V370__lead_next_follow_up_cancel_permission.sql').read_text(encoding='utf-8')
STAMP=datetime.now().strftime('%Y%m%d%H%M%S%f')
def fixture(suffix):
 db='task370_'+STAMP+'_'+suffix
 query('ruoyi-vue-pro',f'CREATE DATABASE {db} CHARACTER SET utf8mb4;')
 for table in ['system_menu','zsjos_schema_version','zsjos_module_schema_version']:
  query(db,f'CREATE TABLE {table} LIKE `ruoyi-vue-pro`.{table};')
 query(db,"INSERT INTO system_menu(name,permission,type,sort,parent_id,path,icon,component,status) VALUES('客资管理','zsjos:lead:query',2,1,0,'leads/manage','','',0); INSERT INTO zsjos_schema_version(version,description,checksum) VALUES('V369','fixture','fixture'); INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version) VALUES('core','V369','fixture','fixture','fixture');")
 return db
def state(db):
 return query(db,"SELECT version,checksum FROM zsjos_schema_version ORDER BY version; SELECT version,checksum FROM zsjos_module_schema_version ORDER BY version;")
def verify(db):
 query(db,SQL); before=state(db); query(db,SQL); assert state(db)==before
 assert query(db,"SELECT COUNT(*) FROM system_menu WHERE type=3;").strip()=='1'
 assert query(db,"SELECT HEX(name) FROM system_menu WHERE permission='zsjos:lead-follow-up:cancel-next-time';").strip()=='取消下次跟进'.encode().hex().upper()
 assert query(db,"SELECT COUNT(*) FROM zsjos_schema_version WHERE version='V370'; SELECT COUNT(*) FROM zsjos_module_schema_version WHERE version='V370';").strip()=='1\n1'
def fail(db):
 before=state(db)
 try: query(db,SQL)
 except RuntimeError: pass
 else: raise AssertionError('Expected failure')
 assert state(db)==before
if __name__=='__main__':
 initial=fixture('initial'); verify(initial)
 partial=fixture('partial')
 query(partial,"INSERT INTO system_menu(name,permission,type,sort,parent_id,path,icon,component,status) SELECT '取消下次跟进','zsjos:lead-follow-up:cancel-next-time',3,5,id,'','','',0 FROM system_menu WHERE type=2;")
 verify(partial)
 missing=fixture('missing'); query(missing,"UPDATE zsjos_schema_version SET version='fixture' WHERE version='V369';"); fail(missing)
 ledger=fixture('ledger'); query(ledger,"CREATE TRIGGER reject370 BEFORE INSERT ON zsjos_module_schema_version FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='fixture reject';"); fail(ledger)
 assert query(ledger,'SELECT COUNT(*) FROM system_menu WHERE type=3;').strip()=='0'
 query(ledger,'DROP TRIGGER reject370;'); verify(ledger)
 conflict=fixture('conflict'); query(conflict,"INSERT INTO system_menu(name,permission,type,sort,parent_id,path,icon,component,status) VALUES('冲突','zsjos:lead-follow-up:cancel-next-time',3,1,999,'','','',0);"); fail(conflict)
 print('PASS initial/replay/partial/prerequisite/ledger failure/recovery/conflict/Chinese HEX; retained fixtures:',STAMP)
