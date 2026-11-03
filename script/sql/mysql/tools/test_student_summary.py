# -*- coding: utf-8 -*-
"""Verify V361 on isolated retained MySQL fixtures and optionally synchronize local metadata."""
import argparse, datetime, json, re
from pathlib import Path
import pymysql, yaml
ROOT=Path(__file__).resolve().parents[4]
SQL=(ROOT/'script/sql/mysql/migrations/V361__student_summary.sql').read_text(encoding='utf-8')
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
def fixture(kind):
 db='student_summary_'+STAMP+'_'+kind
 q(LOCAL,f'CREATE DATABASE `{db}` CHARACTER SET utf8mb4')
 for table in ['system_menu','zsjos_schema_version','zsjos_module_schema_version']:
  q(db,f'CREATE TABLE {table} LIKE `{LOCAL}`.{table}')
 q(db,f"INSERT INTO system_menu SELECT * FROM `{LOCAL}`.system_menu WHERE path='/zsjos' AND type=1 AND deleted=0")
 for table in ['zsjos_schema_version','zsjos_module_schema_version']:
  q(db,f"INSERT INTO {table} SELECT * FROM `{LOCAL}`.{table} WHERE version='V360'")
 return db

def verify(db):
 apply(db);before=ledger(db);menus=q(db,'SELECT * FROM system_menu ORDER BY id');apply(db)
 assert before==ledger(db) and menus==q(db,'SELECT * FROM system_menu ORDER BY id')
 assert q(db,"SELECT name,HEX(name) FROM system_menu WHERE permission='zsjos:student-summary:query'")==(('学员信息汇总','学员信息汇总'.encode().hex().upper()),)
 assert q(db,"SELECT COUNT(*) FROM system_menu WHERE permission='zsjos:student-summary:export' AND type=3")[0][0]==1
 print('PASS initial/replay/UTF-8:',db)

def reject(db):
 before=ledger(db);menus=q(db,'SELECT * FROM system_menu ORDER BY id')
 try:apply(db)
 except pymysql.MySQLError:pass
 else:raise AssertionError('expected failure')
 assert ledger(db)==before and menus==q(db,'SELECT * FROM system_menu ORDER BY id')
 print('PASS atomic failure:',db)

if __name__=='__main__':
 parser=argparse.ArgumentParser();parser.add_argument('--sync-local',action='store_true');args=parser.parse_args()
 print('Target',LOCAL,'MySQL',q(LOCAL,'SELECT VERSION()')[0][0])
 assert q(LOCAL,"SELECT COUNT(*) FROM zsjos_schema_version WHERE version='V360'")[0][0]==1
 db=fixture('fresh');verify(db)
 db=fixture('missing');q(db,"UPDATE zsjos_module_schema_version SET version='missing' WHERE version='V360'");reject(db)
 db=fixture('ledger_failure');q(db,"CREATE TRIGGER reject_v361 BEFORE INSERT ON zsjos_module_schema_version FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='synthetic failure'");reject(db)
 q(db,'DROP TRIGGER reject_v361');verify(db)
 db=fixture('partial');apply(db)
 # A missing button with existing version markers must be repaired. Keep the old row under a fixture-only identity.
 q(db,"UPDATE system_menu SET permission='fixture:old',parent_id=0 WHERE permission='zsjos:student-summary:export'");verify(db)
 db=fixture('conflict');apply(db);q(db,"UPDATE system_menu SET path='conflict' WHERE permission='zsjos:student-summary:query'");reject(db)
 if args.sync_local:
  before=q(LOCAL,'SELECT * FROM system_menu ORDER BY id');grants=q(LOCAL,'SELECT * FROM system_role_menu ORDER BY id')
  backup=ROOT/'backups/mysql'/('student-summary-'+STAMP+'.json');backup.parent.mkdir(parents=True,exist_ok=True)
  backup.write_text(json.dumps(dict(menus=before,ledgers=ledger(LOCAL)),default=str,ensure_ascii=False),encoding='utf-8')
  verify(LOCAL)
  old={row[0]:row for row in before};after={row[0]:row for row in q(LOCAL,'SELECT * FROM system_menu ORDER BY id')}
  assert all(after[key]==row for key,row in old.items())
  assert grants==q(LOCAL,'SELECT * FROM system_role_menu ORDER BY id')
  print('PASS local metadata synchronized; existing menus and role grants unchanged')
 c.close()
