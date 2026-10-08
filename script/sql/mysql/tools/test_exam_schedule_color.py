# -*- coding: utf-8 -*-
"""V292 retained MySQL fixtures; --inspect is read-only, --apply-dev is additive with backup.
--fresh checks the complete baseline/pending chain. No databases or business rows are deleted.
"""
import datetime
import re
import os
import shutil
import subprocess
import sys
import tempfile
from collections import Counter
from pathlib import Path
from test_calendar_notifications import query, table, ROOT, SQL

MIGRATION = (SQL / 'migrations/V292__exam_schedule_color.sql').read_text(encoding='utf-8')
PREFIX = 'exam_color_' + datetime.datetime.now().strftime('%Y%m%d%H%M%S')
PREREQ = "INSERT INTO zsjos_schema_version(version,description,checksum) VALUES ('V287','fixture','fixture'); INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version) VALUES ('core','V287','fixture','fixture','fixture');"


def signature(db):
    return query(db, "SELECT column_name,column_type,is_nullable,column_default,character_set_name,collation_name,HEX(column_comment) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_exam_schedule' AND column_name='background_color';")[0]


def versions(db):
    return query(db, "SELECT version,checksum FROM zsjos_schema_version WHERE version='V292'; SELECT version,checksum FROM zsjos_module_schema_version WHERE module_code='core' AND version='V292';")[0]


def inspect():
    db = 'ruoyi-vue-pro'
    print(query(db, "SELECT VERSION(); SELECT COUNT(*) AS exam_count FROM zsjos_exam_schedule; SELECT version FROM zsjos_schema_version WHERE version IN ('V287','V292'); SELECT version FROM zsjos_module_schema_version WHERE module_code='core' AND version IN ('V287','V292');")[0])
    print('Existing color column:', signature(db) or 'absent')


def scoped():
    expected = None
    for case in ['initial', 'partial', 'prerequisite', 'wrong_type', 'ledger_failure', 'existing_marker', 'missing_table']:
        db = PREFIX + '_' + case
        query(None, f'CREATE DATABASE `{db}` CHARACTER SET utf8mb4;')
        setup = table('zsjos_schema_version') + table('zsjos_module_schema_version')
        exam = table('zsjos_exam_schedule')
        if case != 'partial': exam = re.sub(r'^.*`background_color`.*\n', '', exam, flags=re.M)
        if case != 'missing_table': setup += exam
        if case != 'prerequisite': setup += PREREQ
        if case == 'wrong_type': setup += 'ALTER TABLE zsjos_exam_schedule ADD background_color varchar(20) DEFAULT NULL;'
        if case == 'ledger_failure': setup += "CREATE TRIGGER reject_color_ledger BEFORE INSERT ON zsjos_module_schema_version FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='injected ledger failure';"
        if case == 'existing_marker': setup += "INSERT INTO zsjos_schema_version(version,description,checksum) VALUES ('V292','fixture','preserve'); INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version) VALUES ('core','V292','fixture','preserve','fixture');"
        query(db, setup)
        if case != 'missing_table': query(db, "INSERT INTO zsjos_exam_schedule(tenant_id,schedule_type,schedule_name,exact_date) VALUES (1,'EXACT','颜色升级前的考试','2099-10-10');")
        before = versions(db)
        _, error = query(db, MIGRATION, force=True)
        if case in ['prerequisite','wrong_type','ledger_failure','missing_table']:
            assert 'ERROR' in error and versions(db) == before, (case, error)
            repair = {'prerequisite': PREREQ, 'wrong_type': "ALTER TABLE zsjos_exam_schedule MODIFY background_color varchar(7) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '考期自选底色，空为默认配色';", 'ledger_failure': 'DROP TRIGGER reject_color_ledger;', 'missing_table':exam}[case]
            query(db, repair); query(db, MIGRATION)
        else: assert not error, error
        if expected is None: expected = signature(db)
        assert signature(db) == expected
        records = versions(db); assert len(records.splitlines()) == 2
        query(db, MIGRATION); assert versions(db) == records
        assert query(db, 'SELECT COUNT(*) FROM zsjos_exam_schedule WHERE background_color IS NOT NULL')[0] == '0'
        if case != 'missing_table':
            assert query(db, 'SELECT HEX(schedule_name) FROM zsjos_exam_schedule')[0] == '颜色升级前的考试'.encode().hex().upper()
        print('PASS', case, 'schema/repeat/recovery/ledgers/original data/UTF-8', flush=True)
    return expected


def apply_dev(expected):
    db = 'ruoyi-vue-pro'
    columns = query(db, "SELECT column_name FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_exam_schedule' AND column_name<>'background_color' ORDER BY ordinal_position")[0].splitlines()
    select = 'SELECT ' + ','.join('`' + c + '`' for c in columns) + ' FROM zsjos_exam_schedule ORDER BY id'
    before = query(db, select)[0]
    other_ledgers = "SELECT * FROM zsjos_schema_version WHERE version<>'V292' ORDER BY version; SELECT * FROM zsjos_module_schema_version WHERE version<>'V292' ORDER BY module_code,version;"
    ledgers = query(db, other_ledgers)[0]
    backup = Path(tempfile.gettempdir()) / (PREFIX + '_before.sql')
    command = 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysqldump --default-character-set=utf8mb4 -uroot --single-transaction --skip-lock-tables ruoyi-vue-pro zsjos_exam_schedule zsjos_schema_version zsjos_module_schema_version'
    result = subprocess.run(['docker','exec','yudao-mysql','sh','-c',command], capture_output=True, check=True)
    backup.write_bytes(result.stdout)
    query(db, MIGRATION)
    assert signature(db) == expected
    assert query(db, select)[0] == before
    assert query(db, other_ledgers)[0] == ledgers
    print('PASS development comparison; all original values and other ledgers preserved. Backup:', backup, flush=True)


def fresh(expected):
    def expand(path):
        return re.sub(r'^SOURCE\s+([^;]+);', lambda m: expand(ROOT / m.group(1)), path.read_text(encoding='utf-8-sig'), flags=re.M | re.I)
    # Historical migrations append long advisory-lock suffixes; MySQL caps names at 64 bytes.
    db = 'ec_' + datetime.datetime.now().strftime('%m%d%H%M%S')
    query(None, f'CREATE DATABASE `{db}` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;')
    query(db, expand(SQL / 'bootstrap.sql'))
    applied = set(query(db, 'SELECT version FROM zsjos_schema_version')[0].splitlines())
    for path in sorted((SQL / 'migrations').glob('V*__*.sql')):
        if int(path.name.split('__')[0][1:]) <= 292 and path.name.split('__')[0] not in applied:
            print('FRESH', path.name, flush=True)
            query(db, expand(path))
    assert signature(db) == expected == signature('ruoyi-vue-pro')
    assert len(versions(db).splitlines()) == 2
    assert query(db, 'SELECT COUNT(*) FROM zsjos_exam_schedule')[0] == '0'
    print('PASS full fresh chain and scoped comparison:', db, flush=True)


def java():
    db = PREFIX + '_java'
    query(None, f'CREATE DATABASE `{db}` CHARACTER SET utf8mb4;')
    query(db, table('zsjos_exam_schedule'))
    # Parallel attachment work already added a mapped nullable field; isolate that prerequisite
    # in this fixture without applying its unfinished migration to development.
    source = (ROOT / 'backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/dal/dataobject/examcalendar/ExamScheduleDO.java').read_text(encoding='utf-8')
    if 'attachmentIdsJson' in source and not query(db, "SELECT column_name FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_exam_schedule' AND column_name='attachment_ids_json'")[0]:
        query(db, 'ALTER TABLE zsjos_exam_schedule ADD attachment_ids_json json DEFAULT NULL;')
    password = subprocess.run(['docker','exec','yudao-mysql','sh','-c','printf %s "$MYSQL_ROOT_PASSWORD"'],capture_output=True,check=True).stdout.decode()
    env = dict(os.environ, EXAM_COLOR_TEST_DB=db, EXAM_COLOR_TEST_PASSWORD=password)
    command = [shutil.which('mvn.cmd') or shutil.which('mvn'),'-f',str(ROOT/'backend/pom.xml'),'-pl','yudao-module-zsjos','-am','-Dtest=ExamScheduleColor*Test','-Dsurefire.failIfNoSpecifiedTests=false','test','-q']
    log = Path(tempfile.gettempdir()) / (PREFIX + '_java.log')
    with log.open('wb') as output:
        result = subprocess.run(command, cwd=ROOT, env=env, stdout=output, stderr=subprocess.STDOUT)
    print('Java result:',result.returncode,'fixture:',db,'log:',log,flush=True)
    assert result.returncode == 0


if __name__ == '__main__':
    if '--inspect' in sys.argv:
        inspect(); sys.exit(0)
    if '--fresh-only' in sys.argv:
        fresh(signature('ruoyi-vue-pro')); sys.exit(0)
    if '--java-only' in sys.argv:
        java(); sys.exit(0)
    counts = Counter(path.name.split('__')[0] for path in (SQL / 'migrations').glob('V*__*.sql'))
    assert not [version for version, count in counts.items() if count > 1]
    expected = scoped()
    if '--apply-dev' in sys.argv: apply_dev(expected)
    if '--fresh' in sys.argv: fresh(expected)
    print('Retained fixtures:', PREFIX, flush=True)
