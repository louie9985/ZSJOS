# -*- coding: utf-8 -*-
"""Retained MySQL 8 fixtures. --apply-dev adds only the two note tables and V290 ledgers.
--fresh runs the full current baseline/migration chain, without deleting any schema.
"""
import datetime
from collections import Counter
import os
import re
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path
from test_calendar_notifications import query, table, ROOT, SQL

MIGRATION = (SQL / 'migrations/V290__exam_calendar_note.sql').read_text(encoding='utf-8')
PREFIX = 'exam_note_' + datetime.datetime.now().strftime('%Y%m%d%H%M%S')
TABLES = ['zsjos_exam_calendar_note', 'zsjos_exam_calendar_note_image']
PREREQ = "INSERT INTO zsjos_schema_version(version,description,checksum) VALUES ('V287','fixture','fixture'); INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version) VALUES ('core','V287','fixture','fixture','fixture');"

def signature(db):
    names = ','.join("'" + name + "'" for name in TABLES)
    return query(db, f"SELECT table_name,column_name,column_type,is_nullable,column_default,HEX(column_comment) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name IN ({names}) ORDER BY table_name,ordinal_position; SELECT table_name,index_name,non_unique,seq_in_index,column_name FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name IN ({names}) ORDER BY table_name,index_name,seq_in_index;")[0]

def versions(db):
    return query(db, "SELECT version,checksum FROM zsjos_schema_version WHERE version='V290'; SELECT version,checksum FROM zsjos_module_schema_version WHERE module_code='core' AND version='V290';")[0]

def scoped():
    expected = None
    for case in ['initial','partial','prerequisite','wrong_type','wrong_index','ledger_failure','existing_marker']:
        db = PREFIX + '_' + case
        query(None, f'CREATE DATABASE `{db}` CHARACTER SET utf8mb4;')
        setup = table('zsjos_schema_version') + table('zsjos_module_schema_version')
        if case != 'prerequisite': setup += PREREQ
        if case in ['partial','wrong_type','wrong_index']: setup += table(TABLES[0])
        if case == 'wrong_type': setup += 'ALTER TABLE zsjos_exam_calendar_note MODIFY content varchar(20) NOT NULL;'
        if case == 'wrong_index': setup += 'ALTER TABLE zsjos_exam_calendar_note DROP INDEX uk_exam_note_tenant, ADD INDEX uk_exam_note_tenant(tenant_id);'
        if case == 'ledger_failure': setup += "CREATE TRIGGER reject_note_ledger BEFORE INSERT ON zsjos_module_schema_version FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='injected ledger failure';"
        if case == 'existing_marker': setup += "INSERT INTO zsjos_schema_version(version,description,checksum) VALUES ('V290','fixture','preserve'); INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version) VALUES ('core','V290','fixture','preserve','fixture');"
        query(db, setup)
        before = versions(db)
        _, error = query(db, MIGRATION, force=True)
        if case in ['prerequisite','wrong_type','wrong_index','ledger_failure']:
            assert 'ERROR' in error and versions(db) == before, (case,error)
            repair = {'prerequisite':PREREQ, 'wrong_type':"ALTER TABLE zsjos_exam_calendar_note MODIFY content mediumtext NOT NULL COMMENT '富文本说明，图片使用稳定文件引用';", 'wrong_index':'ALTER TABLE zsjos_exam_calendar_note DROP INDEX uk_exam_note_tenant, ADD UNIQUE INDEX uk_exam_note_tenant(tenant_id);', 'ledger_failure':'DROP TRIGGER reject_note_ledger;'}[case]
            query(db,repair); query(db,MIGRATION)
        else: assert not error,error
        if expected is None: expected=signature(db)
        assert signature(db)==expected
        records=versions(db); assert len(records.splitlines())==2
        query(db,MIGRATION); assert versions(db)==records
        assert query(db,'SELECT COUNT(*) FROM zsjos_exam_calendar_note; SELECT COUNT(*) FROM zsjos_exam_calendar_note_image;')[0]=='0\n0'
        query(db,"INSERT INTO zsjos_exam_calendar_note(tenant_id,content,version) VALUES (1,'免考项目说明',0),(2,'独立租户',0);")
        assert query(db,'SELECT HEX(content) FROM zsjos_exam_calendar_note WHERE tenant_id=1')[0]=='免考项目说明'.encode().hex().upper()
        _,error=query(db,"INSERT INTO zsjos_exam_calendar_note(tenant_id,content,version) VALUES (1,'重复',0)",force=True)
        assert 'Duplicate' in error
        query(db,"INSERT INTO zsjos_exam_calendar_note_image(tenant_id,file_id,uploaded_by,bound) VALUES (1,77,7,0),(2,77,8,0);")
        _,error=query(db,'INSERT INTO zsjos_exam_calendar_note_image(tenant_id,file_id,uploaded_by) VALUES (1,77,7)',force=True)
        assert 'Duplicate' in error
        print('PASS',case,'schema/repeat/recovery/ledger/UTF-8/tenant uniqueness',flush=True)
    return expected

def apply_dev(expected):
    db='ruoyi-vue-pro'
    assert 'V287' in query(db,"SELECT version FROM zsjos_schema_version WHERE version='V287'; SELECT version FROM zsjos_module_schema_version WHERE module_code='core' AND version='V287';")[0]
    prior=query(db,"SELECT * FROM zsjos_schema_version WHERE version<>'V290' ORDER BY version; SELECT * FROM zsjos_module_schema_version WHERE version<>'V290' ORDER BY module_code,version;")[0]
    existing=query(db,"SELECT table_name FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN ('zsjos_exam_calendar_note','zsjos_exam_calendar_note_image')")[0].splitlines()
    backup=Path(tempfile.gettempdir())/(PREFIX+'_before.sql')
    command='MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysqldump --default-character-set=utf8mb4 -uroot --single-transaction --skip-lock-tables ruoyi-vue-pro zsjos_schema_version zsjos_module_schema_version '+ ' '.join(existing)
    result=subprocess.run(['docker','exec','yudao-mysql','sh','-c',command],capture_output=True,check=True)
    backup.write_bytes(result.stdout)
    query(db,MIGRATION)
    assert signature(db)==expected
    assert query(db,"SELECT * FROM zsjos_schema_version WHERE version<>'V290' ORDER BY version; SELECT * FROM zsjos_module_schema_version WHERE version<>'V290' ORDER BY module_code,version;")[0]==prior
    print('PASS development scoped schema comparison; prior ledgers preserved; backup:',backup,flush=True)

def fresh(expected):
    def expand(path):
        return re.sub(r'^SOURCE\s+([^;]+);',lambda m:expand(ROOT/m.group(1)),path.read_text(encoding='utf-8-sig'),flags=re.M|re.I)
    db=PREFIX+'_fresh'
    query(None,f'CREATE DATABASE `{db}` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;')
    query(db,expand(SQL/'bootstrap.sql'))
    applied=set(query(db,'SELECT version FROM zsjos_schema_version')[0].splitlines())
    for path in sorted((SQL/'migrations').glob('V*__*.sql')):
        if path.name.split('__')[0] not in applied:
            print('FRESH',path.name,flush=True)
            query(db,expand(path))
    assert signature(db)==expected
    print('PASS full fresh migration chain and scoped structure',db,flush=True)

def java_tests():
    # Credentials stay in child-process environment; never print or save them.
    password=subprocess.run(['docker','exec','yudao-mysql','sh','-c','printf %s "$MYSQL_ROOT_PASSWORD"'],capture_output=True,check=True).stdout.decode()
    env=dict(os.environ,EXAM_NOTE_TEST_PASSWORD=password,EXAM_NOTE_TEST_DB=PREFIX+'_initial')
    command=[shutil.which('mvn.cmd') or shutil.which('mvn'),'-f',str(ROOT/'backend/pom.xml'),'-pl','yudao-module-zsjos','-am','-Dtest=ExamCalendarNote*Test','-Dsurefire.failIfNoSpecifiedTests=false','test','-q']
    subprocess.run(command,cwd=ROOT,env=env,check=True)

if __name__=='__main__':
    counts=Counter(path.name.split('__')[0] for path in (SQL/'migrations').glob('V*__*.sql'))
    assert not [version for version,count in counts.items() if count>1], 'Duplicate migration versions; resolve before any database write'
    expected=scoped()
    if '--apply-dev' in sys.argv: apply_dev(expected)
    if '--fresh' in sys.argv: fresh(expected)
    if '--java' in sys.argv: java_tests()
    print('Retained databases:',PREFIX,flush=True)
