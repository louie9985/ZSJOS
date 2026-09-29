# -*- coding: utf-8 -*-
"""Retained MySQL 8 fixtures; no table/database deletion or account grants.

--apply-dev backs up and corrects only the local exam/Lead snapshot schema.
--fresh verifies the full baseline and pending chain in a retained database.
"""
import datetime
import re
import subprocess
import sys
import tempfile
from pathlib import Path
from test_calendar_notifications import query, table, ROOT, SQL

PATCH = (SQL / 'exam-calendar-product-scope.sql').read_text(encoding='utf-8')
PREFIX = 'free_exam_' + datetime.datetime.now().strftime('%Y%m%d%H%M%S')


def signature(database):
    return query(database, "SELECT column_name,column_type,is_nullable,HEX(column_comment) "
                 "FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_exam_schedule' "
                 "AND column_name IN ('schedule_name','category_id','category_name_snapshot','category_path_snapshot') ORDER BY column_name")[0]


def ledgers(database):
    return query(database, 'SELECT * FROM zsjos_schema_version ORDER BY version; '
                 'SELECT * FROM zsjos_module_schema_version ORDER BY module_code,version;')[0]


def scoped():
    expected = None
    for case in ['initial', 'repeat', 'partial', 'prerequisite', 'ddl_failure']:
        database = PREFIX + '_' + case
        query(None, f'CREATE DATABASE `{database}` CHARACTER SET utf8mb4;')
        exam = table('zsjos_exam_schedule')
        if case != 'partial':
            exam = re.sub(r'^.*`schedule_name`.*\n', '', exam, flags=re.M)
        exam = exam.replace('`category_id` bigint DEFAULT NULL', '`category_id` bigint NOT NULL')
        exam = exam.replace('`category_name_snapshot` varchar(100) DEFAULT NULL', '`category_name_snapshot` varchar(100) NOT NULL')
        exam = exam.replace('`category_path_snapshot` json DEFAULT NULL', '`category_path_snapshot` json NOT NULL')
        if case == 'ddl_failure':
            exam = re.sub(r'^.*`category_name_snapshot`.*\n', '', exam, flags=re.M)
        setup = table('zsjos_schema_version') + table('zsjos_module_schema_version') + exam + table('zsjos_lead_intended_product')
        if case != 'prerequisite':
            setup += "INSERT INTO zsjos_schema_version(version,description,checksum) VALUES ('V188','fixture','preserve');"
            setup += "INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version) VALUES ('core','V188','fixture','preserve','fixture');"
        query(database, setup)
        before = ledgers(database)
        _, error = query(database, PATCH, force=True)
        assert ledgers(database) == before
        if case in ['prerequisite', 'ddl_failure']:
            assert 'ERROR' in error
            if case == 'prerequisite':
                print('PASS prerequisite failure preserves ledgers')
                continue
            query(database, "ALTER TABLE zsjos_exam_schedule ADD category_name_snapshot varchar(100) DEFAULT NULL COMMENT '分类名称快照';")
            query(database, PATCH)
        else:
            assert not error, error
        query(database, PATCH)
        assert ledgers(database) == before
        actual = signature(database)
        if expected is None:
            expected = actual
        assert actual == expected
        query(database, "INSERT INTO zsjos_exam_schedule(tenant_id,schedule_type,schedule_name,exact_date) VALUES (1,'EXACT','自由考期','2099-10-10');")
        assert query(database, 'SELECT HEX(schedule_name) FROM zsjos_exam_schedule')[0] == '自由考期'.encode().hex().upper()
        print('PASS', case, 'free-form insert, UTF-8 HEX, replay, schema and unchanged ledgers')
    return expected


def apply_dev(expected):
    database = 'ruoyi-vue-pro'
    # Preserve every pre-existing column value; the new column is deliberately not backfilled.
    columns = query(database, "SELECT column_name FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_exam_schedule' AND column_name<>'schedule_name' ORDER BY ordinal_position")[0].splitlines()
    select = 'SELECT ' + ','.join('`' + c + '`' for c in columns) + ' FROM zsjos_exam_schedule ORDER BY id'
    before = query(database, select)[0]
    ledger_before = ledgers(database)
    backup = Path(tempfile.gettempdir()) / (PREFIX + '_exam_schema_backup.sql')
    cmd = 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysqldump --default-character-set=utf8mb4 -uroot --single-transaction --skip-lock-tables ruoyi-vue-pro zsjos_exam_schedule zsjos_lead_intended_product zsjos_schema_version zsjos_module_schema_version'
    result = subprocess.run(['docker', 'exec', 'yudao-mysql', 'sh', '-c', cmd], capture_output=True, check=True)
    backup.write_bytes(result.stdout)
    query(database, PATCH)
    assert query(database, select)[0] == before
    assert ledgers(database) == ledger_before
    assert signature(database) == expected
    print('PASS local schema matches controlled result; all original exam values and ledgers preserved; backup:', backup)


def expand(path):
    source = path.read_text(encoding='utf-8-sig')
    return re.sub(r'^SOURCE\s+([^;]+);', lambda m: expand(ROOT / m.group(1)), source, flags=re.M | re.I)


def fresh():
    database = PREFIX + '_fresh'
    query(None, f'CREATE DATABASE `{database}` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;')
    query(database, expand(SQL / 'bootstrap.sql'))
    applied = set(query(database, 'SELECT version FROM zsjos_schema_version')[0].splitlines())
    count = 0
    for migration in sorted((SQL / 'migrations').glob('V*__*.sql')):
        if migration.name.split('__')[0] in applied:
            continue
        query(database, expand(migration))
        count += 1
    output, _ = query(database, expand(SQL / 'verify/core.sql'))
    failures = [line for line in output.splitlines() if re.search(r'(?:^|\t)(FAIL|MISSING)$', line)]
    assert signature(database) == signature('ruoyi-vue-pro')
    print("PASS full-chain exam schema matches development database", flush=True)
    assert not failures, failures
    print('PASS full fresh baseline and', count, 'pending migrations, core verification and exam schema comparison;', database)


if __name__ == '__main__':
    expected = scoped()
    if '--apply-dev' in sys.argv:
        apply_dev(expected)
    if '--fresh' in sys.argv:
        fresh()
