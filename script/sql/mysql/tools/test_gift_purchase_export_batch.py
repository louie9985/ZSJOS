# -*- coding: utf-8 -*-
"""Retained MySQL 8 fixtures; no business or role data is modified."""
from test_positioning_single_source import query, ROOT
from datetime import datetime
SQL=(ROOT/'script/sql/mysql/migrations/V356__gift_purchase_export_batch_permissions.sql').read_text(encoding='utf-8')
STAMP=datetime.now().strftime('%Y%m%d%H%M%S%f')
def fixture(suffix):
 db='gift356_'+STAMP+'_'+suffix
 query('ruoyi-vue-pro',f'CREATE DATABASE {db} CHARACTER SET utf8mb4;')
 for table in ['system_menu','zsjos_schema_version','zsjos_module_schema_version']:
  query(db,f'CREATE TABLE {table} LIKE `ruoyi-vue-pro`.{table};')
 query(db,"INSERT INTO system_menu(name,permission,type,sort,parent_id,path,icon,component,status) VALUES('礼品采购','zsjos:gift-purchase:query',2,1,0,'gift-purchase','','',0); INSERT INTO zsjos_schema_version(version,description,checksum) VALUES('V355','fixture','fixture'); INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version) VALUES('core','V355','fixture','fixture','fixture');")
 return db
def state(db):
 return query(db,"SELECT version,checksum FROM zsjos_schema_version ORDER BY version; SELECT version,checksum FROM zsjos_module_schema_version ORDER BY version;")
def verify(db):
 query(db,SQL); before=state(db); query(db,SQL); assert state(db)==before
 assert query(db,"SELECT COUNT(*) FROM system_menu WHERE type=3;").strip()=='2'
 assert query(db,"SELECT HEX(name) FROM system_menu WHERE permission='zsjos:gift-purchase:export';").strip()=='导出采购'.encode().hex().upper()
 assert query(db,"SELECT COUNT(*) FROM zsjos_schema_version WHERE version='V356'; SELECT COUNT(*) FROM zsjos_module_schema_version WHERE version='V356';").strip()=='1\n1'
def fail(db):
 before=state(db)
 try: query(db,SQL)
 except RuntimeError: pass
 else: raise AssertionError('Expected failure')
 assert state(db)==before
if __name__=='__main__':
 initial=fixture('initial'); verify(initial)
 partial=fixture('partial')
 query(partial,"INSERT INTO system_menu(name,permission,type,sort,parent_id,path,icon,component,status) SELECT '导出采购','zsjos:gift-purchase:export',3,5,id,'','','',0 FROM system_menu WHERE type=2;")
 verify(partial)
 missing=fixture('missing'); query(missing,"UPDATE zsjos_schema_version SET version='fixture' WHERE version='V355';"); fail(missing)
 ledger=fixture('ledger'); query(ledger,"CREATE TRIGGER reject356 BEFORE INSERT ON zsjos_module_schema_version FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='fixture reject';"); fail(ledger)
 assert query(ledger,'SELECT COUNT(*) FROM system_menu WHERE type=3;').strip()=='0'
 query(ledger,'DROP TRIGGER reject356;'); verify(ledger)
 conflict=fixture('conflict'); query(conflict,"INSERT INTO system_menu(name,permission,type,sort,parent_id,path,icon,component,status) VALUES('冲突','zsjos:gift-purchase:export',3,1,999,'','','',0);"); fail(conflict)
 print('PASS initial/replay/partial/prerequisite/ledger failure/recovery/conflict/Chinese HEX; retained fixtures:',STAMP)
