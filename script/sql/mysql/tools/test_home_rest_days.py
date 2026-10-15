# -*- coding: utf-8 -*-
"""Controlled retained schemas only; never applies to the development business database."""
import datetime, re, subprocess, os, shutil, tempfile
from pathlib import Path
from test_calendar_notifications import query, table, ROOT
SQL = (ROOT/'script/sql/mysql/migrations/V319__home_calendar_rest_days.sql').read_text(encoding='utf-8')
DDL = re.search(r'CREATE TABLE IF NOT EXISTS zsjos_calendar_rest_day.*?;', SQL, re.S).group()
PRE = "INSERT INTO zsjos_schema_version(version,description,checksum) VALUES('V318','fixture','fixture'); INSERT INTO zsjos_module_schema_version VALUES('core','V318','fixture','fixture','fixture',NOW());"
PREFIX = 'rest_days_' + datetime.datetime.now().strftime('%m%d%H%M%S')
def versions(db):
    return query(db,"SELECT version,checksum FROM zsjos_schema_version WHERE version='V319'; SELECT version,checksum FROM zsjos_module_schema_version WHERE module_code='core' AND version='V319';")[0]
for case in ['initial','prerequisite','partial','wrong_type','missing_index','ledger_failure','menu_conflict']:
    db = PREFIX + '_' + case
    query(None, 'CREATE DATABASE ' + db + ' CHARACTER SET utf8mb4;')
    setup = ''.join(table(t) for t in ['system_menu','system_role_menu','zsjos_schema_version','zsjos_module_schema_version'])
    setup += "INSERT INTO system_menu(id,name,permission,type,parent_id) VALUES(6780,'home','zsjos:business-task:query',2,6735);"
    if case != 'prerequisite': setup += PRE
    if case in ['partial','wrong_type','missing_index']: setup += DDL
    if case == 'wrong_type': setup += 'ALTER TABLE zsjos_calendar_rest_day MODIFY date varchar(10) NOT NULL;'
    if case == 'missing_index': setup += 'ALTER TABLE zsjos_calendar_rest_day DROP INDEX uk_calendar_rest_day_date;'
    if case == 'ledger_failure': setup += "CREATE TRIGGER fail_ledger BEFORE INSERT ON zsjos_module_schema_version FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='injected';"
    if case == 'menu_conflict': setup += "INSERT INTO system_menu(id,name,permission,type,parent_id) VALUES(73650,'fixture','unrelated',3,6780);"
    query(db,setup)
    before = versions(db)
    _, error = query(db, SQL, force=True)
    if case not in ['initial','partial']:
        assert 'ERROR' in error, case
        assert versions(db) == before, case
        repair = {'prerequisite':PRE, 'wrong_type':'ALTER TABLE zsjos_calendar_rest_day MODIFY date date NOT NULL;',
          'missing_index':'ALTER TABLE zsjos_calendar_rest_day ADD UNIQUE KEY uk_calendar_rest_day_date(tenant_id,date);',
          'ledger_failure':'DROP TRIGGER fail_ledger;',
          'menu_conflict':"UPDATE system_menu SET permission='zsjos:calendar-rest-day:manage' WHERE id=73650;"}[case]
        query(db, repair); query(db,SQL)
    else: assert not error, error
    assert len(versions(db).splitlines()) == 2
    before=versions(db); query(db, SQL); assert versions(db)==before
    assert query(db,'SELECT COUNT(*) FROM system_role_menu')[0]=='0'
    assert query(db,'SELECT COUNT(*) FROM zsjos_calendar_rest_day')[0]=='0'
    if case != 'menu_conflict': assert query(db,'SELECT HEX(name) FROM system_menu WHERE id=73650')[0]=='管理休息日'.encode().hex().upper()
    print('PASS',case,db,flush=True)
if '--java' in __import__('sys').argv:
    password=subprocess.run(['docker','exec','yudao-mysql','sh','-c','printf %s "$MYSQL_ROOT_PASSWORD"'],capture_output=True,check=True).stdout.decode()
    env=dict(os.environ,REST_DAY_TEST_DB=PREFIX+'_initial',REST_DAY_TEST_PASSWORD=password)
    log=Path(tempfile.gettempdir())/(PREFIX+'_java.log')
    with log.open('wb') as output:
        result=subprocess.run([shutil.which('mvn.cmd') or shutil.which('mvn'),'-f',str(ROOT/'backend/pom.xml'),'-pl','yudao-module-zsjos','-am','-Dtest=CalendarRestDayServiceTest,CalendarRestDayMySqlTest','-Dsurefire.failIfNoSpecifiedTests=false','test','-q'],cwd=ROOT,env=env,stdout=output,stderr=subprocess.STDOUT)
    print('Java exit',result.returncode,'log',log,flush=True)
    assert result.returncode==0

if '--fresh-core' in __import__('sys').argv:
    import zsjos_db as runner
    db=PREFIX+'_fresh'
    query(None,'CREATE DATABASE '+db+' CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;')
    def expand(path):
        content=path.read_text(encoding='utf-8')
        return re.sub(r'^SOURCE ([^;]+);',lambda m:expand(ROOT/m.group(1)),content,flags=re.M)
    query(db,expand(ROOT/'script/sql/mysql/bootstrap.sql'))
    installed=set(query(db,'SELECT version FROM zsjos_schema_version')[0].splitlines())
    core=runner.load_manifests()['core']
    for migration in runner.migrations_for('core',core):
        if migration.version not in installed:
            query(db,expand(migration.path))
            print('APPLIED',migration.version,flush=True)
    result=query(db,expand(ROOT/'script/sql/mysql/verify/core.sql'))[0]
    assert not re.search(r'(?:^|\t)(FAIL|MISSING)(?:$|\n)',result),result
    assert len(versions(db).splitlines())==2
    scoped="SELECT column_name,column_type,is_nullable,COALESCE(column_default,'<NULL>'),extra FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_calendar_rest_day' ORDER BY ordinal_position"
    assert query(db,scoped)[0]==query(PREFIX+'_initial',scoped)[0]
    print('PASS fresh core and scoped upgrade schema equality',db,flush=True)
