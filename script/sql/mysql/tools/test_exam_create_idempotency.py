# -*- coding: utf-8 -*-
"""V297 scoped MySQL verification; retains isolated schemas and never deletes business data.

Default: migration initial/repeat/failure/recovery checks. --apply-dev backs up then adds
the verified schema to local development. --java runs real service concurrency/rollback.
--fresh verifies bootstrap through V297. Credentials come from the local Docker container.
"""
import datetime
import os
import re
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path
from test_calendar_notifications import query, table, ROOT, SQL

MIGRATION = (SQL / 'migrations/V297__exam_create_idempotency.sql').read_text(encoding='utf-8')
PREFIX = 'exam_create_' + datetime.datetime.now().strftime('%Y%m%d%H%M%S')
PREREQ = "INSERT INTO zsjos_schema_version(version,description,checksum) VALUES ('V293','fixture','fixture'); INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version) VALUES ('core','V293','fixture','fixture','fixture');"


def signature(db):
    return query(db, "SELECT column_name,column_type,is_nullable,column_default,character_set_name,collation_name,HEX(column_comment) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_exam_schedule' AND column_name IN ('create_request_key','create_request_hash') ORDER BY column_name; SELECT index_name,non_unique,seq_in_index,column_name,sub_part FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='zsjos_exam_schedule' AND index_name='uk_exam_create_request' ORDER BY seq_in_index;")[0]


def versions(db):
    return query(db, "SELECT version,checksum FROM zsjos_schema_version WHERE version='V297'; SELECT version,checksum FROM zsjos_module_schema_version WHERE module_code='core' AND version='V297';")[0]


def create_db(db):
    query(None, f'CREATE DATABASE `{db}` CHARACTER SET utf8mb4;')


def scoped():
    expected = None
    for case in ['initial', 'partial', 'prerequisite', 'wrong_column', 'wrong_index', 'ledger_failure', 'existing_marker', 'missing_table']:
        db = PREFIX + '_' + case
        create_db(db)
        exam = re.sub(r'^.*(?:`create_request_key`|`create_request_hash`|`uk_exam_create_request`).*\n', '', table('zsjos_exam_schedule'), flags=re.M)
        setup = table('zsjos_schema_version') + table('zsjos_module_schema_version')
        if case != 'missing_table': setup += exam
        if case != 'prerequisite': setup += PREREQ
        if case == 'partial': setup += "ALTER TABLE zsjos_exam_schedule ADD create_request_key char(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '创建操作人及请求标识摘要';"
        if case == 'wrong_column': setup += 'ALTER TABLE zsjos_exam_schedule ADD create_request_key varchar(20) DEFAULT NULL;'
        if case == 'wrong_index': setup += 'ALTER TABLE zsjos_exam_schedule ADD INDEX uk_exam_create_request(tenant_id);'
        if case == 'ledger_failure': setup += "CREATE TRIGGER reject_create_ledger BEFORE INSERT ON zsjos_module_schema_version FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='injected ledger failure';"
        if case == 'existing_marker': setup += "INSERT INTO zsjos_schema_version(version,description,checksum) VALUES ('V297','fixture','preserve'); INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version) VALUES ('core','V297','fixture','preserve','fixture');"
        query(db, setup)
        if case != 'missing_table': query(db, "INSERT INTO zsjos_exam_schedule(tenant_id,schedule_type,schedule_name,exact_date) VALUES (1,'EXACT','升级前考期','2099-10-10'),(1,'EXACT','升级前考期','2099-10-10');")
        before = versions(db)
        _, error = query(db, MIGRATION, force=True)
        repairs = {
            'prerequisite': PREREQ,
            'wrong_column': "ALTER TABLE zsjos_exam_schedule MODIFY create_request_key char(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL COMMENT '创建操作人及请求标识摘要';",
            'wrong_index': 'ALTER TABLE zsjos_exam_schedule DROP INDEX uk_exam_create_request;',
            'ledger_failure': 'DROP TRIGGER reject_create_ledger;',
            'missing_table': exam,
        }
        if case in repairs:
            assert 'ERROR' in error and versions(db) == before, (case, error)
            query(db, repairs[case]); query(db, MIGRATION)
        else: assert not error, error
        if expected is None: expected = signature(db)
        assert signature(db) == expected, case
        records = versions(db); assert len(records.splitlines()) == 2
        query(db, MIGRATION); assert versions(db) == records
        assert query(db, 'SELECT COUNT(*) FROM zsjos_exam_schedule WHERE create_request_key IS NOT NULL OR create_request_hash IS NOT NULL')[0] == '0'
        if case != 'missing_table':
            assert query(db, 'SELECT HEX(schedule_name) FROM zsjos_exam_schedule')[0].splitlines() == ['升级前考期'.encode().hex().upper()] * 2
        print('PASS', case, 'structure/repeat/recovery/ledgers/preserved duplicates/UTF-8', flush=True)
    return expected


def apply_dev(expected):
    db = 'ruoyi-vue-pro'
    columns = query(db, "SELECT column_name FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_exam_schedule' AND column_name NOT IN ('create_request_key','create_request_hash') ORDER BY ordinal_position")[0].splitlines()
    select = 'SELECT ' + ','.join('`' + c + '`' for c in columns) + ' FROM zsjos_exam_schedule ORDER BY id'
    before = query(db, select)[0]
    other_ledgers = "SELECT * FROM zsjos_schema_version WHERE version<>'V297' ORDER BY version; SELECT * FROM zsjos_module_schema_version WHERE version<>'V297' ORDER BY module_code,version;"
    ledgers = query(db, other_ledgers)[0]
    backup = Path(tempfile.gettempdir()) / (PREFIX + '_before.sql')
    command = 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysqldump --default-character-set=utf8mb4 -uroot --single-transaction --skip-lock-tables ruoyi-vue-pro zsjos_exam_schedule zsjos_schema_version zsjos_module_schema_version'
    backup.write_bytes(subprocess.run(['docker', 'exec', 'yudao-mysql', 'sh', '-c', command], capture_output=True, check=True).stdout)
    query(db, MIGRATION)
    assert signature(db) == expected and len(versions(db).splitlines()) == 2
    assert query(db, select)[0] == before and query(db, other_ledgers)[0] == ledgers
    print('PASS development schema matches controlled execution; all original rows/other ledgers preserved. Backup:', backup, flush=True)


def java():
    db = PREFIX + '_java'; create_db(db)
    setup = table('zsjos_exam_schedule')
    if 'attachment_ids_json' not in setup: setup += 'ALTER TABLE zsjos_exam_schedule ADD attachment_ids_json json DEFAULT NULL;'
    setup += table('zsjos_calendar_notify_snapshot') + table('zsjos_calendar_notify_state')
    query(db, setup)
    password = subprocess.run(['docker', 'exec', 'yudao-mysql', 'sh', '-c', 'printf %s "$MYSQL_ROOT_PASSWORD"'], capture_output=True, check=True).stdout.decode()
    env = dict(os.environ, EXAM_CREATE_TEST_DB=db, EXAM_CREATE_TEST_PASSWORD=password,
               EXAM_COLOR_TEST_DB=db.replace('exam_create_', 'exam_color_'), EXAM_COLOR_TEST_PASSWORD=password)
    # Color integration uses the same schema shape but a separately named retained database.
    create_db(env['EXAM_COLOR_TEST_DB']); query(env['EXAM_COLOR_TEST_DB'], setup)
    command = [shutil.which('mvn.cmd') or shutil.which('mvn'), '-f', str(ROOT/'backend/pom.xml'), '-pl', 'yudao-module-zsjos', '-am',
               '-Dtest=ExamSchedule*Test,ExamAttachmentLifecycleTest', '-Dsurefire.failIfNoSpecifiedTests=false', 'test', '-q']
    log = Path(tempfile.gettempdir()) / (PREFIX + '_java.log')
    print('Java fixture:', db, 'log:', log, flush=True)
    with log.open('wb') as output:
        result = subprocess.run(command, cwd=ROOT, env=env, stdout=output, stderr=subprocess.STDOUT)
    print('Java exit:', result.returncode, 'log:', log, flush=True)
    assert result.returncode == 0


def fresh(expected):
    def expand(path):
        return re.sub(r'^SOURCE\s+([^;]+);', lambda m: expand(ROOT/m.group(1)), path.read_text(encoding='utf-8-sig'), flags=re.M|re.I)
    db = 'eci_' + datetime.datetime.now().strftime('%m%d%H%M%S'); create_db(db)
    print('Fresh fixture:', db, flush=True)
    query(db, expand(SQL/'bootstrap.sql'))
    applied = set(query(db, 'SELECT version FROM zsjos_schema_version')[0].splitlines())
    for path in sorted((SQL/'migrations').glob('V*__*.sql')):
        version = path.name.split('__')[0]
        if int(version[1:]) <= 297 and version not in applied:
            print('FRESH', version, flush=True); query(db, expand(path))
    assert signature(db) == expected
    assert len(versions(db).splitlines()) == 2
    assert query(db, 'SELECT COUNT(*) FROM zsjos_exam_schedule')[0] == '0'
    print('PASS fresh through V297 and scoped schema comparison', db, flush=True)


if __name__ == '__main__':
    if '--java-only' in sys.argv: java(); sys.exit(0)
    if '--fresh-only' in sys.argv: fresh(signature('ruoyi-vue-pro')); sys.exit(0)
    expected = scoped()
    if '--apply-dev' in sys.argv: apply_dev(expected)
    if '--fresh' in sys.argv: fresh(expected)
    if '--java' in sys.argv: java()
    print('Retained fixtures:', PREFIX, flush=True)
