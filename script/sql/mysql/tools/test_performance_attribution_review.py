# -*- coding: utf-8 -*-
"""Retained synthetic MySQL fixtures for V322; no live writes, deletes or role grants."""
import sys, uuid
from pathlib import Path
sys.dont_write_bytecode = True
from repair_local_lead_qualification import connect
ROOT = Path(__file__).resolve().parents[4]
SQL = (ROOT / 'script/sql/mysql/migrations/V322__performance_attribution_domain_review.sql').read_text(encoding='utf-8')
BODY = SQL.split('CREATE PROCEDURE zsjos_v322_apply()', 1)[1].split('END$$', 1)[0]
TABLES = ['system_menu', 'zsjos_performance_org', 'zsjos_performance_target', 'zsjos_performance_attribution', 'zsjos_schema_version', 'zsjos_module_schema_version']
c = connect()
with c.cursor() as q:
 for scenario in ['fresh', 'partial', 'menu', 'column', 'index']:
  db = 'zsjos_verify_domain_' + scenario + '_' + uuid.uuid4().hex[:8]
  q.execute('CREATE DATABASE ' + db + ' CHARACTER SET utf8mb4')
  for table in TABLES:
   q.execute('CREATE TABLE ' + db + '.' + table + ' LIKE `ruoyi-vue-pro`.' + table)
  q.execute('USE ' + db)
  q.execute("INSERT INTO system_menu SELECT * FROM `ruoyi-vue-pro`.system_menu WHERE deleted=0 AND (path='/zsjos' OR permission LIKE 'zsjos:sales-performance%' OR permission LIKE 'zsjos:delivery-performance%')")
  q.execute("INSERT INTO zsjos_schema_version(version,description,checksum) VALUES ('V321','独立交付业绩域','fixture')")
  q.execute("INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version) VALUES ('core','V321','独立交付业绩域','fixture','baseline')")
  q.execute("INSERT INTO zsjos_performance_attribution(fact_type,fact_id,user_id,tenant_id,org_source) VALUES ('ORDER',1,1,1,'frozen')")
  q.execute("SELECT COUNT(*) n FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_performance_attribution' AND column_name='business_domain'")
  exists = q.fetchone()['n'] > 0
  if scenario in ('partial','column') and not exists:
   q.execute("ALTER TABLE zsjos_performance_attribution ADD COLUMN business_domain varchar(16) NULL DEFAULT NULL")
  if scenario == 'column': q.execute("ALTER TABLE zsjos_performance_attribution MODIFY business_domain varchar(32) NULL DEFAULT NULL")
  if scenario == 'menu': q.execute("UPDATE system_menu SET path='wrong-path' WHERE permission='zsjos:delivery-performance:query'")
  if scenario == 'index': q.execute("ALTER TABLE zsjos_performance_target ADD COLUMN fixture_extra int NULL")
  if scenario == 'index': q.execute("ALTER TABLE zsjos_performance_target DROP INDEX uk_target, ADD UNIQUE KEY uk_target(tenant_id,business_domain,scope_type,scope_id,period_type,period_start,deleted,fixture_extra)")
  c.commit()
  q.execute('CREATE PROCEDURE zsjos_v322_apply()' + BODY + 'END')
  if scenario in ('menu','column','index'):
   try:
    q.execute('CALL zsjos_v322_apply()')
   except Exception as error:
    assert 'conflict' in str(error), str(error)
   else: raise AssertionError('Expected failure: ' + scenario)
   for table in ['zsjos_schema_version','zsjos_module_schema_version']:
    q.execute("SELECT COUNT(*) n FROM " + table + " WHERE version='V322'"); assert q.fetchone()['n'] == 0
   if scenario == 'menu': q.execute("UPDATE system_menu SET path='delivery-performance' WHERE permission='zsjos:delivery-performance:query'")
   if scenario == 'column': q.execute("ALTER TABLE zsjos_performance_attribution MODIFY business_domain varchar(16) NULL DEFAULT NULL")
   if scenario == 'index': q.execute("ALTER TABLE zsjos_performance_target DROP INDEX uk_target, ADD UNIQUE KEY uk_target(tenant_id,business_domain,scope_type,scope_id,period_type,period_start,deleted)")
   c.commit()
  q.execute('CALL zsjos_v322_apply()'); q.execute('CALL zsjos_v322_apply()')
  q.execute("SELECT business_domain FROM zsjos_performance_attribution WHERE fact_id=1"); assert q.fetchone()['business_domain'] is None
  for table in ['zsjos_schema_version','zsjos_module_schema_version']:
   q.execute("SELECT COUNT(*) n FROM " + table + " WHERE version='V322'"); assert q.fetchone()['n'] == 1
  q.execute("SELECT HEX(description) h FROM zsjos_schema_version WHERE version='V322'")
  assert q.fetchone()['h'] == '业绩归属快照与结构复核'.encode().hex().upper()
  print('PASS', scenario, db)
c.close()
