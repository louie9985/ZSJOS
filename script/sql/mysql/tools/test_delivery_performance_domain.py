# -*- coding: utf-8 -*-
"""Verify V320 on a retained isolated MySQL database; never modify live business rows."""
import sys,json
from pathlib import Path
sys.dont_write_bytecode=True
from repair_local_lead_qualification import connect
ROOT=Path(__file__).resolve().parents[4]
sql=(ROOT/'script/sql/mysql/migrations/V320__delivery_performance_domain.sql').read_text(encoding='utf-8')
body=sql.split('CREATE PROCEDURE zsjos_v320_apply()',1)[1].split('END$$',1)[0]
c=connect()
with c.cursor() as q:
 q.execute('CREATE DATABASE IF NOT EXISTS zsjos_verify_delivery_partial_20261015 CHARACTER SET utf8mb4')
 for t in ['system_menu','zsjos_performance_org','zsjos_performance_target','zsjos_schema_version','zsjos_module_schema_version']:
  q.execute('CREATE TABLE IF NOT EXISTS zsjos_verify_delivery_partial_20261015.'+t+' LIKE `ruoyi-vue-pro`.'+t)
  q.execute('SELECT COUNT(*) n FROM zsjos_verify_delivery_partial_20261015.'+t)
  if q.fetchone()['n']==0:q.execute('INSERT INTO zsjos_verify_delivery_partial_20261015.'+t+' SELECT * FROM `ruoyi-vue-pro`.'+t)
 c.commit();q.execute('USE zsjos_verify_delivery_partial_20261015')
 q.execute("SELECT COUNT(*) n FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_performance_org' AND column_name='business_domain'")
 if q.fetchone()['n']==0:q.execute("ALTER TABLE zsjos_performance_org ADD COLUMN business_domain varchar(16) NOT NULL DEFAULT 'sales'")
 q.execute("SELECT id,floor_amount,sprint_amount,period_start,period_type,reason FROM zsjos_performance_target ORDER BY id");before=q.fetchall()
 q.execute('DROP PROCEDURE IF EXISTS zsjos_v320_apply');q.execute('CREATE PROCEDURE zsjos_v320_apply()'+body+'END')
 q.execute('CALL zsjos_v320_apply()');q.execute('CALL zsjos_v320_apply()')
 q.execute("SELECT id,floor_amount,sprint_amount,period_start,period_type,reason FROM zsjos_performance_target ORDER BY id");assert before==q.fetchall()
 q.execute("SELECT COUNT(*) n FROM system_menu WHERE permission LIKE 'zsjos:delivery-performance%' AND deleted=0");assert q.fetchone()['n']==8
 q.execute("SELECT COUNT(*) n FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='zsjos_performance_target' AND index_name='uk_target' AND column_name='business_domain'");assert q.fetchone()['n']==1
 for t in ['zsjos_schema_version','zsjos_module_schema_version']:
  q.execute("SELECT COUNT(*) n FROM "+t+" WHERE version='V320'");assert q.fetchone()['n']==1
  q.execute("DELETE FROM "+t+" WHERE version='V320'")
 q.execute("UPDATE system_menu SET deleted=1 WHERE permission='zsjos:sales-performance:query'");c.commit()
 try:q.execute('CALL zsjos_v320_apply()');raise AssertionError('Expected prerequisite failure')
 except Exception as error:assert 'requires V276' in str(error),str(error)
 for t in ['zsjos_schema_version','zsjos_module_schema_version']:
  q.execute("SELECT COUNT(*) n FROM "+t+" WHERE version='V320'");assert q.fetchone()['n']==0
 q.execute("UPDATE system_menu SET deleted=0 WHERE permission='zsjos:sales-performance:query'");c.commit();q.execute('CALL zsjos_v320_apply()')
 q.execute("SELECT name,HEX(name) label_hex FROM system_menu WHERE permission='zsjos:delivery-performance:query'");assert q.fetchone()['label_hex']=='E4BAA4E4BB98E4B89AE7BBA9E7BB9FE8AEA1'
 q.execute('DROP PROCEDURE zsjos_v320_apply')
c.close();print('PASS partial schema recovery, replay, preserved target values, failure guards, recovery, UTF-8 HEX; isolated database retained')
