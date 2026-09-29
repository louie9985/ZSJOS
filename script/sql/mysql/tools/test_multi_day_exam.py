# -*- coding: utf-8 -*-
"""Retained MySQL 8 verification databases; scoped local conversion only with --apply-dev."""
import datetime
import hashlib
import json
import re
import subprocess
import sys
import tempfile
from pathlib import Path
from test_calendar_notifications import query, table, ROOT, SQL
from test_free_exam import expand

PATCH = (SQL / 'migrations/V286__multi_day_exam_schedule.sql').read_text(encoding='utf-8')
PREFIX = 'md_' + datetime.datetime.now().strftime('%Y%m%d%H%M%S')


def dump_json(value):
    return json.dumps(value, ensure_ascii=False, separators=(',', ':'))


def sql_text(value):
    return "CONVERT(0x" + value.encode('utf-8').hex() + " USING utf8mb4)"


def snapshots(database):
    raw = query(database, """SELECT HEX(JSON_OBJECT('id',id,'type',calendar_type,'calendarId',calendar_id,
        'version',calendar_version,'status',record_status,'title',title_snapshot,'time',time_snapshot,
        'remark',remark_snapshot,'details',details_json,'hash',content_hash)) FROM zsjos_calendar_notify_snapshot
        WHERE calendar_type='EXAM' ORDER BY id""")[0]
    return [json.loads(bytes.fromhex(line)) for line in raw.splitlines() if line]


def verify_hashes(database):
    for row in snapshots(database):
        content = [row[k] for k in ['type', 'calendarId', 'version', 'status', 'title', 'time', 'remark', 'details']]
        assert hashlib.sha256(dump_json(content).encode()).hexdigest() == row['hash'], 'snapshot hash mismatch'
        details = json.loads(row['details'])
        assert 'roughStartDate' not in details and 'roughEndDate' not in details
        assert details['scheduleType'] in ['EXACT', 'MULTI_DAY']


def signature(database):
    return query(database, """SELECT column_name,column_type,is_nullable,IF(column_name IN ('schedule_type','start_date','end_date'),HEX(column_comment),'unaffected')
        FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_exam_schedule'
        ORDER BY column_name;
        SELECT index_name,seq_in_index,column_name FROM information_schema.statistics
        WHERE table_schema=DATABASE() AND table_name='zsjos_exam_schedule' ORDER BY index_name,seq_in_index;
        SELECT constraint_name,check_clause FROM information_schema.check_constraints
        WHERE constraint_schema=DATABASE() AND constraint_name LIKE 'chk_exam_schedule%' ORDER BY constraint_name;""")[0]


def setup(database, prerequisite=True):
    query(None, f'CREATE DATABASE `{database}` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;')
    old = table('zsjos_exam_schedule').replace('MULTI_DAY', 'ROUGH').replace('start_date', 'rough_start_date').replace('end_date', 'rough_end_date').replace('idx_exam_schedule_multi_day', 'idx_exam_schedule_rough')
    query(database, table('zsjos_schema_version') + table('zsjos_module_schema_version') + old + table('zsjos_calendar_notify_snapshot'))
    if prerequisite:
        add_prerequisite(database)
    query(database, """INSERT INTO zsjos_exam_schedule(id,tenant_id,schedule_type,schedule_name,rough_start_date,rough_end_date,record_status)
        VALUES (1,1,'ROUGH','多日考试','2026-10-01','2026-10-08','PUBLISHED'),(2,2,'ROUGH','另一租户','2026-09-30','2026-10-01','DRAFT');
        INSERT INTO zsjos_exam_schedule(id,tenant_id,schedule_type,schedule_name,exact_date) VALUES(3,1,'EXACT','单日考试','2026-10-10');""")
    for id_, type_ in [(1, 'ROUGH'), (3, 'EXACT')]:
        details = dump_json(dict(sorted({'scheduleType': type_, 'roughStartDate': '2026-10-01' if id_ == 1 else None,
            'roughEndDate': '2026-10-08' if id_ == 1 else None, 'exactDate': '2026-10-10' if id_ == 3 else None,
            'categoryPathSnapshot': None, 'selectedSpecsJson': None, 'frozenSkusJson': None}.items())))
        query(database, f"""INSERT INTO zsjos_calendar_notify_snapshot(tenant_id,calendar_type,calendar_id,calendar_version,event_type,
            record_status,title_snapshot,time_snapshot,details_json,content_hash)
            VALUES(1,'EXAM',{id_},1,'PUBLISHED','PUBLISHED','多日考试','2026-10-01 - 2026-10-08',{sql_text(details)},'fixture');""")


def add_prerequisite(database):
    query(database, """INSERT INTO zsjos_schema_version(version,description,checksum) VALUES('V285','fixture','preserve');
        INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version) VALUES('core','V285','fixture','preserve','fixture');""")


def scoped():
    expected = None
    for case in ['initial', 'partial', 'prerequisite', 'ledger_failure']:
        database = PREFIX + '_' + case
        setup(database, case != 'prerequisite')
        if case == 'partial':
            query(database, """ALTER TABLE zsjos_exam_schedule DROP CHECK chk_exam_schedule_dates;
                ALTER TABLE zsjos_exam_schedule DROP CHECK chk_exam_schedule_type;
                ALTER TABLE zsjos_exam_schedule CHANGE rough_start_date start_date date DEFAULT NULL COMMENT '考试开始日期';""")
        if case == 'ledger_failure':
            query(database, """CREATE TRIGGER reject_v286 BEFORE INSERT ON zsjos_module_schema_version
                FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='fixture ledger failure';""")
        _, error = query(database, PATCH, force=True)
        if case in ['prerequisite', 'ledger_failure']:
            assert 'ERROR' in error
            assert query(database, "SELECT COUNT(*) FROM zsjos_schema_version WHERE version='V286'; SELECT COUNT(*) FROM zsjos_module_schema_version WHERE version='V286';")[0] == '0\n0'
            if case == 'prerequisite':
                add_prerequisite(database)
            else:
                query(database, 'DROP TRIGGER reject_v286;')
            query(database, PATCH)
        else:
            assert not error, error
        before = query(database, 'SELECT id,tenant_id,schedule_type,HEX(schedule_name),start_date,end_date,exact_date FROM zsjos_exam_schedule ORDER BY id')[0]
        query(database, PATCH)
        assert before == query(database, 'SELECT id,tenant_id,schedule_type,HEX(schedule_name),start_date,end_date,exact_date FROM zsjos_exam_schedule ORDER BY id')[0]
        assert query(database, "SELECT COUNT(*) FROM zsjos_exam_schedule WHERE schedule_type='MULTI_DAY';")[0] == '2'
        assert query(database, "SELECT HEX(schedule_name) FROM zsjos_exam_schedule WHERE id=1")[0] == '多日考试'.encode().hex().upper()
        verify_hashes(database)
        actual = signature(database)
        expected = expected or actual
        assert actual == expected
        print('PASS', case, 'repeat, hash, IDs/dates/tenants, UTF-8 HEX and ledgers', flush=True)
    return expected


def apply_dev(expected):
    database = 'ruoyi-vue-pro'
    columns = query(database, "SELECT column_name FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_exam_schedule' ORDER BY ordinal_position")[0].splitlines()
    before_select = 'SELECT ' + ','.join('`' + c + '`' for c in columns) + ' FROM zsjos_exam_schedule ORDER BY id'
    before = query(database, before_select)[0]
    old_snapshots = snapshots(database)
    backup = Path(tempfile.gettempdir()) / (PREFIX + '_backup.sql')
    command = 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysqldump --default-character-set=utf8mb4 -uroot --single-transaction --skip-lock-tables ruoyi-vue-pro zsjos_exam_schedule zsjos_calendar_notify_snapshot zsjos_schema_version zsjos_module_schema_version'
    result = subprocess.run(['docker', 'exec', 'yudao-mysql', 'sh', '-c', command], capture_output=True, check=True)
    backup.write_bytes(result.stdout)
    query(database, PATCH)
    after = query(database, before_select.replace('rough_start_date','start_date').replace('rough_end_date','end_date'))[0]
    assert before.replace('\tROUGH\t','\tMULTI_DAY\t') == after, 'unexpected exam data change'
    new_snapshots = snapshots(database)
    assert len(old_snapshots) == len(new_snapshots)
    for old, new in zip(old_snapshots, new_snapshots):
        assert all(old[k] == new[k] for k in old if k not in ['details','hash'])
        details = json.loads(old['details'])
        details['startDate'] = details.pop('roughStartDate', details.get('startDate'))
        details['endDate'] = details.pop('roughEndDate', details.get('endDate'))
        if details['scheduleType'] == 'ROUGH': details['scheduleType'] = 'MULTI_DAY'
        assert details == json.loads(new['details'])
    verify_hashes(database)
    assert signature(database) == expected
    print('PASS development conversion, scoped preservation and schema comparison; backup:', backup, flush=True)


def fresh():
    database = PREFIX + '_fresh'
    query(None, f'CREATE DATABASE `{database}` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;')
    query(database, expand(SQL / 'bootstrap.sql'))
    applied = set(query(database, 'SELECT version FROM zsjos_schema_version')[0].splitlines())
    for migration in sorted((SQL / 'migrations').glob('V*__*.sql')):
        if migration.name.split('__')[0] not in applied: query(database, expand(migration))
    output, _ = query(database, expand(SQL / 'verify/core.sql'))
    failures = [line for line in output.splitlines() if re.search(r'(?:^|\t)(FAIL|MISSING)$',line)]
    assert signature(database) == signature('ruoyi-vue-pro')
    print('PASS complete fresh execution through V286 and affected development comparison', flush=True)
    assert not failures, failures


if __name__ == '__main__':
    expected = scoped()
    if '--apply-dev' in sys.argv: apply_dev(expected)
    if '--fresh' in sys.argv: fresh()
