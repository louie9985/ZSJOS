# -*- coding: utf-8 -*-
"""V314 controlled MySQL checks. Retains fixtures; --apply-dev backs up and applies locally."""
import datetime,sys,tempfile,subprocess,re,os,shutil
from pathlib import Path
from test_calendar_notifications import query,table,ROOT
SQL=(ROOT/'script/sql/mysql/migrations/V314__calendar_colors.sql').read_text(encoding='utf-8')
PRE="INSERT INTO zsjos_schema_version(version,description,checksum) VALUES('V313','fixture','fixture'); INSERT INTO zsjos_module_schema_version VALUES('core','V313','fixture','fixture','fixture',NOW());"
PREFIX='cal_colors_'+datetime.datetime.now().strftime('%m%d%H%M%S')
def ledgers(db):
 return query(db,"SELECT version,checksum FROM zsjos_schema_version WHERE version='V314'; SELECT version,checksum FROM zsjos_module_schema_version WHERE module_code='core' AND version='V314';")[0]
def verify(db):
 assert len(ledgers(db).splitlines())==2
 rows=query(db,"SELECT HEX(name) FROM system_menu WHERE id IN (73635,73636) ORDER BY id")[0].splitlines()
 assert rows==[s.encode().hex().upper() for s in ['管理个人日历快捷色','管理课程日历快捷色']]
 assert query(db,"SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name IN ('zsjos_personal_calendar_event','zsjos_course_calendar_event') AND column_name='background_color' AND character_maximum_length=7")[0]=='2'
for case in ['initial','prerequisite','ledger_failure','partial','wrong_type','missing_index']:
 db=PREFIX+'_'+case
 query(None,'CREATE DATABASE '+db+' CHARACTER SET utf8mb4;')
 setup=''.join(table(t) for t in ['zsjos_personal_calendar_event','zsjos_course_calendar_event','system_menu','system_role_menu','zsjos_schema_version','zsjos_module_schema_version'])
 setup=re.sub(r'^.*background_color.*\n','',setup,flags=re.M)
 setup+="INSERT INTO system_menu(id,name,permission,type,parent_id) VALUES(73605,'personal','zsjos:personal-calendar:query',2,73600),(73630,'course','zsjos:course-calendar:query',2,73600);"
 if case!='prerequisite': setup+=PRE
 if case=='ledger_failure': setup+="CREATE TRIGGER fail_ledger BEFORE INSERT ON zsjos_module_schema_version FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='injected';"
 if case in ['partial','wrong_type']: setup+="ALTER TABLE zsjos_personal_calendar_event ADD background_color varchar("+('20' if case=='wrong_type' else '7')+") CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL;"
 query(db,setup)
 if case=='missing_index':
  query(db,SQL); query(db,'ALTER TABLE zsjos_calendar_palette DROP INDEX uk_calendar_palette_owner;')
 before=ledgers(db)
 _,err=query(db,SQL,force=True)
 if case in ['prerequisite','ledger_failure','wrong_type','missing_index']:
  assert 'ERROR' in err,case
  assert ledgers(db)==before,case
  repair={'prerequisite':PRE,'ledger_failure':'DROP TRIGGER fail_ledger;','wrong_type':'ALTER TABLE zsjos_personal_calendar_event MODIFY background_color varchar(7) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL;','missing_index':'ALTER TABLE zsjos_calendar_palette ADD UNIQUE KEY uk_calendar_palette_owner(tenant_id,user_id,calendar_type);'}[case]
  query(db,repair);query(db,SQL)
 else: assert not err,err
 verify(db);before=ledgers(db);query(db,SQL);assert ledgers(db)==before
 assert query(db,'SELECT COUNT(*) FROM zsjos_calendar_palette')[0]=='0'
 assert query(db,'SELECT COUNT(*) FROM system_role_menu')[0]=='0'
 print('PASS',case,db,flush=True)
if '--apply-dev' in sys.argv:
 db='ruoyi-vue-pro'
 tables=['zsjos_personal_calendar_event','zsjos_course_calendar_event','system_menu','zsjos_schema_version','zsjos_module_schema_version']
 backup=Path(tempfile.gettempdir())/(PREFIX+'_before.sql')
 command='MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysqldump --default-character-set=utf8mb4 -uroot --single-transaction --skip-lock-tables '+db+' '+' '.join(tables)
 result=subprocess.run(['docker','exec','yudao-mysql','sh','-c',command],capture_output=True,check=True);backup.write_bytes(result.stdout)
 original={}
 for t in tables[:2]:
  columns=query(db,"SELECT column_name FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='"+t+"' AND column_name<>'background_color' ORDER BY ordinal_position")[0].splitlines()
  select='SELECT '+','.join(columns)+' FROM '+t+' ORDER BY id'
  original[t]=(select,query(db,select)[0])
 grants=query(db,'SELECT * FROM system_role_menu ORDER BY id')[0]
 query(db,SQL);verify(db);query(db,SQL)
 for select,before in original.values(): assert query(db,select)[0]==before
 assert query(db,'SELECT * FROM system_role_menu ORDER BY id')[0]==grants
 print('PASS local migration, original event data/grants preserved; backup',backup)

if '--java' in sys.argv:
 password=subprocess.run(['docker','exec','yudao-mysql','sh','-c','printf %s "$MYSQL_ROOT_PASSWORD"'],capture_output=True,check=True).stdout.decode()
 env=dict(os.environ,CALENDAR_COLOR_TEST_DB=PREFIX+'_initial',CALENDAR_COLOR_TEST_PASSWORD=password)
 log=Path(tempfile.gettempdir())/(PREFIX+'_java.log')
 with log.open('wb') as output:
  result=subprocess.run([shutil.which('mvn.cmd') or shutil.which('mvn'),'-f',str(ROOT/'backend/pom.xml'),'-pl','yudao-module-zsjos','-am','-Dtest=CalendarPaletteMySqlTest','-Dsurefire.failIfNoSpecifiedTests=false','test','-q'],cwd=ROOT,env=env,stdout=output,stderr=subprocess.STDOUT)
 print('Java result',result.returncode,'log',log)
 assert result.returncode==0

if '--fresh-core' in sys.argv:
 import zsjos_db as runner
 core=runner.load_manifests()['core']
 runner.load_manifests=lambda:{'core':core}
 original_test=runner.with_test_mysql
 signature="SELECT table_name,column_name,column_type,is_nullable,COALESCE(column_default,'<NULL>'),COALESCE(character_set_name,''),COALESCE(collation_name,'') FROM information_schema.columns WHERE table_schema=DATABASE() AND (table_name='zsjos_calendar_palette' OR (table_name IN ('zsjos_personal_calendar_event','zsjos_course_calendar_event') AND column_name='background_color')) ORDER BY table_name,column_name"
 def scoped(callback):
  def check(container):
   callback(container)
   assert runner.docker_mysql_query(container,signature).strip()==query('ruoyi-vue-pro',signature)[0]
   assert runner.docker_mysql_query(container,"SELECT COUNT(*) FROM zsjos_module_schema_version WHERE module_code='core' AND version='V314'").strip()=='1'
   print('PASS Core V314 fresh/local scoped schema comparison',flush=True)
  original_test(check)
 runner.with_test_mysql=scoped
 runner.test_fresh()
