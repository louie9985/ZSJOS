#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""V329 controlled MySQL verification. Clones only scoped tables; never alters source data."""
import argparse, hashlib, re, subprocess, uuid
from pathlib import Path
ROOT=Path(__file__).resolve().parents[4]
TABLES=['zsjos_lead','zsjos_lead_follow_up_rule','zsjos_lead_aging_pool_cycle','zsjos_lead_aging_pool_event','zsjos_lead_aging_pool_notify_stage','zsjos_lead_public_sea_record','zsjos_lead_inbox_filter_scheme','system_menu','system_notify_rule','system_notify_template','system_notify_business_outbox','system_role_menu','zsjos_schema_version','zsjos_module_schema_version']
RENAMES=dict(zip(TABLES[2:6],['zsjos_lead_public_sea_pool_cycle','zsjos_lead_public_sea_pool_event','zsjos_lead_public_sea_pool_notify_stage','zsjos_lead_public_sea_pool_compatibility_record']))
def main():
 ap=argparse.ArgumentParser();ap.add_argument('--container',default='yudao-mysql');ap.add_argument('--source',default='ruoyi-vue-pro');a=ap.parse_args()
 if not re.fullmatch(r'[A-Za-z0-9_-]+',a.source):raise ValueError('Invalid source identifier')
 def sql(statement,db=None,ok=True):
  cmd=['docker','exec','-i',a.container,'sh','-c','MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql --default-character-set=utf8mb4 -uroot --batch --skip-column-names "$@"','--']
  if db:cmd+=['--database',db]
  r=subprocess.run(cmd,input=('SET NAMES utf8mb4; '+statement).encode(),capture_output=True)
  if ok and r.returncode:raise RuntimeError(r.stderr.decode())
  return r.returncode,r.stdout.decode(),r.stderr.decode()
 db='verify_public_sea_'+uuid.uuid4().hex[:12]
 sql('CREATE DATABASE '+db+' CHARACTER SET utf8mb4;')
 print('controlled_database='+db,flush=True)
 for t in TABLES:
  ddl=sql('SHOW CREATE TABLE `'+t+'`;',a.source)[1].split(chr(9),1)[1].strip().replace(chr(92)+'n',chr(10)).replace(chr(92)+'t',chr(9))
  sql(ddl+';',db)
  # Exact pool/config data, minimal Lead row; no personal payload is printed.
  cols=sql("SELECT column_name FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='"+t+"' AND extra NOT LIKE '%GENERATED%' ORDER BY ordinal_position;",a.source)[1].splitlines()
  columns=','.join('`'+c+'`' for c in cols)
  sql('INSERT INTO `'+t+'` ('+columns+') SELECT '+columns+' FROM `'+a.source+'`.`'+t+'`'+(' LIMIT 1' if t=='zsjos_lead' else '')+';',db)
 migration=(ROOT/'script/sql/mysql/migrations/V329__public_sea_pool_naming.sql').read_text(encoding='utf-8')
 # Explicit target validation to isolate V329 from unrelated imported ledger drift.
 sql("DELETE FROM zsjos_schema_version WHERE version='V329'; DELETE FROM zsjos_module_schema_version WHERE version='V329';",db)
 def signature(table):
  cols=sql("SELECT column_name FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='"+table+"' ORDER BY ordinal_position;",db)[1].splitlines()
  values=','.join("COALESCE(HEX(`"+c+"`),'NULL')" for c in cols)
  rows=sql("SELECT MD5(CONCAT_WS(':',"+values+")) FROM `"+table+"` ORDER BY 1;",db)[1]
  return hashlib.sha256(rows.encode()).hexdigest()
 before={t:sql('SELECT COUNT(*) FROM `'+t+'`;',db)[1].strip() for t in RENAMES}
 signatures={t:signature(t) for t in RENAMES}
 grant_signature=signature('system_role_menu')
 rc,_,err=sql(migration,db,False);assert rc==0,err
 for old,new in RENAMES.items():
  assert sql('SELECT COUNT(*) FROM `'+new+'`;',db)[1].strip()==before[old]
  assert signature(new)==signatures[old]
 assert signature('system_role_menu')==grant_signature
 sql(migration,db);assert sql("SELECT COUNT(*) FROM zsjos_schema_version WHERE version='V329';",db)[1].strip()=='1'
 assert sql("SELECT COUNT(*) FROM zsjos_module_schema_version WHERE module_code='core' AND version='V329';",db)[1].strip()=='1'
 print('initial/replay/full-row-signatures/unchanged-role-grants PASS',flush=True)
 # Failed prerequisite cannot advance existing ledger checksums.
 marker=sql("SELECT checksum FROM zsjos_schema_version WHERE version='V329';",db)[1]
 sql('RENAME TABLE zsjos_lead_public_sea_pool_event TO verification_missing_event;',db)
 assert sql(migration,db,False)[0]!=0
 assert marker==sql("SELECT checksum FROM zsjos_schema_version WHERE version='V329';",db)[1]
 sql('RENAME TABLE verification_missing_event TO zsjos_lead_public_sea_pool_event;',db)
 # Recover mixed old/canonical tables and timeout name, with ledgers absent.
 sql("DELETE FROM zsjos_schema_version WHERE version='V329'; DELETE FROM zsjos_module_schema_version WHERE version='V329';",db)
 for old,new in list(RENAMES.items())[:2]:sql('RENAME TABLE `'+new+'` TO `'+old+'`;',db)
 sql('ALTER TABLE zsjos_lead_follow_up_rule RENAME COLUMN public_sea_pool_timeout_days TO aging_pool_timeout_days;',db)
 # Induced DML failure after DDL must not create success markers.
 sql("CREATE TRIGGER verify_fail_menu BEFORE UPDATE ON system_menu FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='controlled DML failure'; UPDATE system_menu SET permission=permission WHERE 1=0;",db)
 # Ensure a renamed permission is present, so the trigger executes.
 sql("DROP TRIGGER verify_fail_menu; UPDATE system_menu SET permission=REPLACE(permission,'lead-public-sea-pool','lead-aging-pool') WHERE permission LIKE 'zsjos:lead-public-sea-pool:%'; CREATE TRIGGER verify_fail_menu BEFORE UPDATE ON system_menu FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='controlled DML failure';",db)
 assert sql(migration,db,False)[0]!=0
 assert sql("SELECT COUNT(*) FROM zsjos_schema_version WHERE version='V329';",db)[1].strip()=='0'
 assert sql("SELECT COUNT(*) FROM zsjos_module_schema_version WHERE version='V329';",db)[1].strip()=='0'
 sql('DROP TRIGGER verify_fail_menu;',db);sql(migration,db);sql(migration,db)
 for old,new in RENAMES.items():assert signature(new)==signatures[old]
 assert signature('system_role_menu')==grant_signature
 print('failure-ledger/mixed-state-recovery/replay PASS',flush=True)
 print('label_hex='+sql("SELECT HEX(name) FROM system_menu WHERE permission='zsjos:lead-public-sea-pool:query';",db)[1].strip(),flush=True)
 print('source_database_unchanged=true; controlled clone retained for inspection',flush=True)
if __name__=='__main__':main()
