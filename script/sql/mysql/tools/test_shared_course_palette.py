# -*- coding: utf-8 -*-
"""V315 scoped verification; --apply-dev applies approved tenant-1 cleanup with backup."""
import datetime,re,sys,tempfile,subprocess,os,shutil
from pathlib import Path
from test_calendar_notifications import query,table,ROOT
SQL=(ROOT/'script/sql/mysql/migrations/V315__shared_course_palette.sql').read_text(encoding='utf-8')
V314=(ROOT/'script/sql/mysql/migrations/V314__calendar_colors.sql').read_text(encoding='utf-8')
PALETTE=re.search(r' CREATE TABLE IF NOT EXISTS zsjos_calendar_palette \([\s\S]+?;\n',V314).group()
PREFIX='cal_colors_shared_'+datetime.datetime.now().strftime('%m%d%H%M%S')
PRE="INSERT INTO zsjos_schema_version(version,description,checksum) VALUES('V314','fixture','fixture'); INSERT INTO zsjos_module_schema_version VALUES('core','V314','fixture','fixture','fixture',NOW());"
def ledger(db):
 return query(db,"SELECT version,checksum FROM zsjos_schema_version WHERE version='V315'; SELECT version,checksum FROM zsjos_module_schema_version WHERE module_code='core' AND version='V315';")[0]
for case in ['initial','prerequisite','ledger_failure','existing_shared','postcondition']:
 db=PREFIX+'_'+case
 query(None,'CREATE DATABASE '+db+' CHARACTER SET utf8mb4;')
 setup=table('zsjos_schema_version')+table('zsjos_module_schema_version')+PALETTE
 if case!='prerequisite': setup+=PRE
 setup+="INSERT INTO zsjos_calendar_palette(tenant_id,user_id,calendar_type,colors_json) VALUES(1,7,'PERSONAL','[]'),(1,7,'COURSE',JSON_ARRAY(JSON_OBJECT('id','old','name','旧课程颜色','color','#112233')));"
 if case=='existing_shared':setup+="INSERT INTO zsjos_calendar_palette(tenant_id,user_id,calendar_type,colors_json) VALUES(1,0,'COURSE_SHARED','[]');"
 if case=='ledger_failure':setup+="CREATE TRIGGER fail_ledger BEFORE INSERT ON zsjos_module_schema_version FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='injected';"
 if case=='postcondition':setup+="CREATE TRIGGER retain_old BEFORE UPDATE ON zsjos_calendar_palette FOR EACH ROW SET NEW.deleted=0;"
 query(db,setup)
 personal=query(db,"SELECT * FROM zsjos_calendar_palette WHERE calendar_type='PERSONAL'")[0]
 _,err=query(db,SQL,force=True)
 if case in ['prerequisite','ledger_failure','postcondition']:
  assert 'ERROR' in err,case
  assert not ledger(db)
  assert query(db,"SELECT deleted+0 FROM zsjos_calendar_palette WHERE calendar_type='COURSE'")[0]=='0'
  assert query(db,"SELECT COUNT(*) FROM zsjos_calendar_palette WHERE calendar_type='COURSE_SHARED'")[0]=='0'
  query(db,PRE if case=='prerequisite' else 'DROP TRIGGER '+('fail_ledger' if case=='ledger_failure' else 'retain_old')+';')
  query(db,SQL)
 else:assert not err,err
 assert query(db,"SELECT * FROM zsjos_calendar_palette WHERE calendar_type='PERSONAL'")[0]==personal
 assert query(db,"SELECT deleted+0 FROM zsjos_calendar_palette WHERE calendar_type='COURSE'")[0]=='1'
 assert len(ledger(db).splitlines())==2
 if case!='existing_shared':
  assert query(db,"SELECT HEX(JSON_UNQUOTE(JSON_EXTRACT(colors_json,'$[0].name'))) FROM zsjos_calendar_palette WHERE calendar_type='COURSE_SHARED'")[0]=='公共营养师'.encode().hex().upper()
 query(db,"UPDATE zsjos_calendar_palette SET colors_json='[]' WHERE calendar_type='COURSE_SHARED';")
 before=ledger(db);query(db,SQL);assert ledger(db)==before
 assert query(db,"SELECT JSON_LENGTH(colors_json) FROM zsjos_calendar_palette WHERE calendar_type='COURSE_SHARED'")[0]=='0'
 print('PASS',case,db,flush=True)
if '--apply-dev' in sys.argv:
 db='ruoyi-vue-pro'
 backup=Path(tempfile.gettempdir())/(PREFIX+'_before.sql')
 command='MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysqldump --default-character-set=utf8mb4 -uroot --single-transaction --skip-lock-tables '+db+' zsjos_calendar_palette zsjos_schema_version zsjos_module_schema_version'
 backup.write_bytes(subprocess.run(['docker','exec','yudao-mysql','sh','-c',command],capture_output=True,check=True).stdout)
 protected=["SELECT * FROM zsjos_calendar_palette WHERE calendar_type<>'COURSE' ORDER BY id","SELECT id,background_color FROM zsjos_course_calendar_event ORDER BY id","SELECT id,background_color FROM zsjos_personal_calendar_event ORDER BY id","SELECT * FROM system_role_menu ORDER BY id"]
 before=[query(db,s)[0] for s in protected]
 query(db,SQL);query(db,SQL)
 assert query(db,protected[0].replace("calendar_type<>'COURSE'","calendar_type NOT IN ('COURSE','COURSE_SHARED')"))[0]==before[0]
 for s,v in zip(protected[1:],before[1:]):assert query(db,s)[0]==v
 assert query(db,"SELECT COUNT(*) FROM zsjos_calendar_palette WHERE tenant_id=1 AND calendar_type='COURSE' AND deleted=0")[0]=='0'
 print('PASS local cleanup/preservation; backup',backup,flush=True)
if '--java' in sys.argv:
 password=subprocess.run(['docker','exec','yudao-mysql','sh','-c','printf %s "$MYSQL_ROOT_PASSWORD"'],capture_output=True,check=True).stdout.decode()
 env=dict(os.environ,CALENDAR_COLOR_TEST_DB=PREFIX+'_initial',CALENDAR_COLOR_TEST_PASSWORD=password)
 log=Path(tempfile.gettempdir())/(PREFIX+'_java.log')
 with log.open('wb') as out:
  result=subprocess.run([shutil.which('mvn.cmd') or shutil.which('mvn'),'-f',str(ROOT/'backend/pom.xml'),'-pl','yudao-module-zsjos','-am','-Dtest=CalendarPaletteMySqlTest,CalendarPalettePermissionTest,CalendarPaletteServiceTest','-Dsurefire.failIfNoSpecifiedTests=false','test','-q'],cwd=ROOT,env=env,stdout=out,stderr=subprocess.STDOUT)
 print('Java',result.returncode,log,flush=True)
 assert result.returncode==0
