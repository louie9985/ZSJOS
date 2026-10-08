# -*- coding: utf-8 -*-
"""V291 checks on retained isolated schemas in the existing local MySQL container.
--sync-development applies only the additive migration after read-only target checks.
No deletion, service control, role grants, or personal-data output.
"""
import datetime
import hashlib
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[4]
SQL = ROOT / 'script/sql/mysql'
MIGRATION = (SQL / 'migrations/V291__notice_origin.sql').read_text(encoding='utf-8')
PREFIX = 'no291_' + datetime.datetime.now().strftime('%m%d%H%M%S')
FIELDS = ['source_dept_id', 'source_dept_name', 'publisher_id', 'publisher_name', 'audience_summary']


def query(db, sql, force=False):
    assert db is None or db.startswith(PREFIX) or (db == 'ruoyi-vue-pro' and '--sync-development' in sys.argv)
    command = 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql --default-character-set=utf8mb4 -uroot -N -B'
    result = subprocess.run(['docker', 'exec', '-i', 'yudao-mysql', 'sh', '-c', command + (' --force' if force else '')],
        input=('SET NAMES utf8mb4;\n' + (f'USE `{db}`;\n' if db else '') + sql).encode('utf-8'), capture_output=True)
    error = result.stderr.decode('utf-8')
    if not force and (result.returncode or 'ERROR' in error):
        raise RuntimeError(error)
    return result.stdout.decode('utf-8').strip(), error


def markers(db):
    return query(db, "SELECT version,checksum FROM zsjos_schema_version WHERE version='V291'; SELECT version,checksum FROM zsjos_module_schema_version WHERE module_code='core' AND version='V291';")[0]


def columns(db):
    return query(db, "SELECT column_name,column_type,is_nullable,character_set_name,collation_name,HEX(column_comment) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='system_notice' AND column_name IN ('source_dept_id','source_dept_name','publisher_id','publisher_name','audience_summary') ORDER BY column_name;")[0]


def expand(text):
    return re.sub(r'^\s*SOURCE\s+(script/sql/[^;\r\n]+\.sql)\s*;', lambda m: expand((ROOT / m[1]).read_text(encoding='utf-8')), text, flags=re.M | re.I)


def controlled():
    source = (SQL / 'schema/core.sql').read_text(encoding='utf-8') + (SQL / '02-bootstrap-zsjos-seed.sql').read_text(encoding='utf-8')
    def table(name):
        match = re.search(r'CREATE TABLE IF NOT EXISTS `' + name + r'` \([\s\S]+?\) ENGINE=[^;]+;', source)
        assert match, name
        return match[0]
    prereq = "INSERT INTO zsjos_schema_version(version,description,checksum) VALUES ('V287','fixture','fixture'); INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version) VALUES ('core','V287','fixture','fixture','fixture');"
    expected = None
    for case in ['initial', 'partial', 'prerequisite', 'wrong_type', 'ledger_failure', 'existing_marker']:
        db = PREFIX + '_' + case
        query(None, f'CREATE DATABASE `{db}` CHARACTER SET utf8mb4;')
        notice = table('system_notice')
        for field in FIELDS:
            if case == 'partial' and field == 'source_dept_id':
                continue
            notice = re.sub(r'^.*`' + field + r'`[^\n]+\n', '', notice, flags=re.M)
        setup = notice + table('zsjos_schema_version') + table('zsjos_module_schema_version')
        if case != 'prerequisite': setup += prereq
        setup += "INSERT INTO system_notice(id,title,content,type) VALUES(1,'历史测试公告','保留原文',2);"
        if case == 'wrong_type': setup += 'ALTER TABLE system_notice ADD COLUMN source_dept_name varchar(8);'
        if case == 'ledger_failure': setup += "CREATE TRIGGER reject_v291 BEFORE INSERT ON zsjos_module_schema_version FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='injected failure';"
        if case == 'existing_marker':
            setup += "INSERT INTO zsjos_schema_version(version,description,checksum) VALUES('V291','fixture','preserve'); INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version) VALUES('core','V291','fixture','preserve','fixture');"
        query(db, setup)
        before = markers(db)
        _, error = query(db, MIGRATION, True)
        if case in ['prerequisite', 'wrong_type', 'ledger_failure']:
            assert 'ERROR' in error and markers(db) == before, (case, error)
            if case == 'prerequisite': query(db, prereq)
            elif case == 'wrong_type': query(db, "ALTER TABLE system_notice MODIFY source_dept_name varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '来源部门名称快照';")
            else: query(db, 'DROP TRIGGER reject_v291;')
            query(db, MIGRATION)
        else: assert not error, error
        after = markers(db)
        assert len(after.splitlines()) == 2
        query(db, MIGRATION)
        assert markers(db) == after
        assert query(db, 'SELECT source_dept_name IS NULL AND publisher_name IS NULL AND audience_summary IS NULL FROM system_notice WHERE id=1')[0] == '1'
        assert query(db, 'SELECT HEX(content) FROM system_notice WHERE id=1')[0] == '保留原文'.encode().hex().upper()
        query(db, "INSERT INTO system_notice(title,content,type,source_dept_name,publisher_name,audience_summary) VALUES('新公告','正文',2,'考务部','测试发布人','全体员工');")
        assert query(db, 'SELECT HEX(source_dept_name),HEX(publisher_name),HEX(audience_summary) FROM system_notice WHERE id<>1')[0] == '\t'.join(s.encode().hex().upper() for s in ['考务部', '测试发布人', '全体员工'])
        actual = columns(db)
        if expected is None: expected = actual
        assert actual == expected
        print('PASS', case, 'initial/replay/partial recovery, atomic ledgers, history retention and HEX', flush=True)
    print('Retained schemas:', PREFIX, flush=True)
    return expected


def fresh(expected):
    db = PREFIX + '_fresh'
    query(None, f'CREATE DATABASE `{db}` CHARACTER SET utf8mb4;')
    baseline = expand((SQL / 'bootstrap.sql').read_text(encoding='utf-8'))
    paths = sorted((SQL / 'migrations').glob('V*__*.sql'), key=lambda p: int(p.name.split('__')[0][1:]))
    snapshots = {p: expand(p.read_text(encoding='utf-8')) for p in paths if int(p.name.split('__')[0][1:]) <= 291}
    verify_sql = expand((SQL / 'verify/core.sql').read_text(encoding='utf-8'))
    query(db, baseline)
    installed = set(query(db, "SELECT version FROM zsjos_module_schema_version WHERE module_code='core'")[0].splitlines())
    for p, text in snapshots.items():
        version = p.name.split('__')[0]
        if version not in installed:
            query(db, text)
            print('PASS fresh', version, flush=True)
    verify = query(db, verify_sql)[0]
    assert not [line for line in verify.splitlines() if re.search(r'(?:^|\t)(FAIL|MISSING)$', line)], verify
    assert columns(db) == expected and len(markers(db).splitlines()) == 2
    assert baseline == expand((SQL / 'bootstrap.sql').read_text(encoding='utf-8')), 'Concurrent baseline change; rerun against stable inputs'
    assert all(text == expand(p.read_text(encoding='utf-8')) for p, text in snapshots.items()), 'Concurrent migration change'
    assert verify_sql == expand((SQL / 'verify/core.sql').read_text(encoding='utf-8')), 'Concurrent verification change'
    print('PASS full fresh through V291:', db, 'baseline SHA256:', hashlib.sha256(baseline.encode()).hexdigest(), flush=True)


def sync(expected):
    db = 'ruoyi-vue-pro'
    # Do not print business rows. Migration only adds nullable columns and its ledger entries.
    before = query(db, 'SELECT COUNT(*) FROM system_notice')[0]
    prereq = query(db, "SELECT COUNT(*) FROM zsjos_schema_version WHERE version='V287'; SELECT COUNT(*) FROM zsjos_module_schema_version WHERE module_code='core' AND version='V287';")[0]
    assert prereq == '1\n1'
    query(db, MIGRATION)
    assert query(db, 'SELECT COUNT(*) FROM system_notice')[0] == before
    assert columns(db) == expected and len(markers(db).splitlines()) == 2
    print('PASS development additive sync: system_notice row count preserved; column metadata/HEX and both ledgers match controlled result', flush=True)


if __name__ == '__main__':
    expected = controlled()
    if '--fresh' in sys.argv: fresh(expected)
    if '--sync-development' in sys.argv: sync(expected)
