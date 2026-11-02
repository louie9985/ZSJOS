# -*- coding: utf-8 -*-
"""Verify V360 on retained, empty MySQL fixtures; --sync-local requires explicit authorization."""
import argparse, datetime, json, re
from pathlib import Path
import pymysql, yaml
ROOT=Path(__file__).resolve().parents[4]
SQL=(ROOT/'script/sql/mysql/migrations/V360__student_attachment_original_name.sql').read_text(encoding='utf-8')
docs=list(yaml.safe_load_all((ROOT/'backend/yudao-server/src/main/resources/application-local.yaml').read_text(encoding='utf-8')))
d=next(x['spring']['datasource']['dynamic']['datasource']['master'] for x in docs if x and 'datasource' in x.get('spring',{}))
m=re.search(r'jdbc:mysql://([^:/]+):(\d+)/([^?]+)',d['url']); LOCAL=m[3]
c=pymysql.connect(host=m[1],port=int(m[2]),user=d['username'],password=str(d['password']),database=LOCAL,charset='utf8mb4',autocommit=True)
def q(db,sql,args=None):
 c.select_db(db)
 with c.cursor() as cur:
  cur.execute(sql,args); result=cur.fetchall()
  while cur.nextset(): pass
  return result
def apply(db):
 delimiter=';'; pending=''
 for line in SQL.splitlines():
  if line.startswith('--'):continue
  if line.startswith('DELIMITER '):delimiter=line.split()[1];continue
  pending+=line+'\n'
  if pending.rstrip().endswith(delimiter):
   q(db,pending.rstrip()[:-len(delimiter)]);pending=''
def ledger(db):
 return [q(db,'SELECT * FROM '+t+' ORDER BY version') for t in ['zsjos_schema_version','zsjos_module_schema_version']]
STAMP=datetime.datetime.now().strftime('%Y%m%d%H%M%S')
def fixture(kind,partial=False):
 db='attachment_name_'+STAMP+'_'+kind
 q(LOCAL,f'CREATE DATABASE `{db}` CHARACTER SET utf8mb4')
 for t in ['zsjos_schema_version','zsjos_module_schema_version']:
  q(db,f'CREATE TABLE {t} LIKE `{LOCAL}`.{t}')
  q(db,f"INSERT INTO {t} SELECT * FROM `{LOCAL}`.{t} WHERE version='V323'")
 q(db,'CREATE TABLE zsjos_student_info_image(id BIGINT PRIMARY KEY, tenant_id BIGINT NOT NULL, content_type VARCHAR(128))')
 q(db,"INSERT INTO zsjos_student_info_image VALUES(1,1,'application/pdf')")
 if partial:q(db,'ALTER TABLE zsjos_student_info_image ADD COLUMN original_name TEXT CHARACTER SET utf8mb4 NULL')
 return db
def verify(db):
 apply(db);before=ledger(db);apply(db);assert ledger(db)==before
 assert q(db,'SELECT original_name FROM zsjos_student_info_image WHERE id=1')==((None,),)
 name='学历报告.pdf'
 q(db,'UPDATE zsjos_student_info_image SET original_name=%s WHERE id=1',(name,));apply(db)
 assert q(db,'SELECT original_name,HEX(original_name) FROM zsjos_student_info_image WHERE id=1')==((name,name.encode().hex().upper()),)
 print('PASS initial/replay/UTF-8/preserved data:',db)
def reject(db):
 before=ledger(db)
 try:apply(db)
 except pymysql.MySQLError:pass
 else:raise AssertionError('expected failure')
 assert ledger(db)==before
 print('PASS failure leaves ledgers unchanged:',db)
if __name__=='__main__':
 args=argparse.ArgumentParser();args.add_argument('--sync-local',action='store_true');args=args.parse_args()
 print('MySQL',q(LOCAL,'SELECT VERSION()')[0][0])
 assert q(LOCAL,"SELECT COUNT(*) FROM zsjos_schema_version WHERE version='V323'")[0][0]==1
 verify(fixture('fresh'));verify(fixture('partial',True))
 db=fixture('missing');q(db,"UPDATE zsjos_module_schema_version SET version='missing' WHERE version='V323'");reject(db)
 db=fixture('wrong');q(db,'ALTER TABLE zsjos_student_info_image ADD COLUMN original_name INT');reject(db)
 db=fixture('ledger_failure');q(db,"CREATE TRIGGER reject_v360 BEFORE INSERT ON zsjos_module_schema_version FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='synthetic failure'");reject(db)
 q(db,'DROP TRIGGER reject_v360');verify(db)
 if args.sync_local:
  schema=q(LOCAL,'SHOW CREATE TABLE zsjos_student_info_image');before=ledger(LOCAL)
  cols=[r[0] for r in q(LOCAL,'SHOW COLUMNS FROM zsjos_student_info_image') if r[0]!='original_name']
  projection=','.join('`'+col+'`' for col in cols)
  rows=q(LOCAL,'SELECT '+projection+' FROM zsjos_student_info_image ORDER BY id')
  backup=ROOT/'backups/mysql'/('attachment-name-'+STAMP+'.json');backup.parent.mkdir(parents=True,exist_ok=True)
  backup.write_text(json.dumps(dict(schema=schema,ledgers=before),default=str,ensure_ascii=False),encoding='utf-8')
  apply(LOCAL);once=ledger(LOCAL);apply(LOCAL);assert ledger(LOCAL)==once
  assert q(LOCAL,'SELECT '+projection+' FROM zsjos_student_info_image ORDER BY id')==rows
  assert q(LOCAL,"SELECT HEX(description) FROM zsjos_schema_version WHERE version='V360'")[0][0]=='学员附件原始名称'.encode().hex().upper()
  assert q(LOCAL,"SELECT HEX(column_comment) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_student_info_image' AND column_name='original_name'")[0][0]=='原始附件名称'.encode().hex().upper()
  print('PASS authorized local migration; existing rows preserved:',len(rows))
 c.close()

