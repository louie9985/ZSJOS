# -*- coding: utf-8 -*-
"""Controlled V288 verification: creates new isolated schemas on the existing local MySQL container.
Never writes existing development tables, stops services, deletes schemas or changes account grants.
"""
import datetime
import os
import re
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[4]
SQL = ROOT / 'script/sql/mysql'
MIGRATION = (SQL / 'migrations/V288__notice_public_share.sql').read_text(encoding='utf-8')
CONTAINER = os.environ.get('NOTICE_SHARE_VERIFY_CONTAINER', 'yudao-mysql')
PREFIX = 'notice_share_v288_' + datetime.datetime.now().strftime('%Y%m%d%H%M%S')

def query(database, sql, force=False):
    if database:
        assert database.startswith(PREFIX), 'Only newly owned verification schemas may be written'
    command = 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql --default-character-set=utf8mb4 -uroot -N -B'
    if force:
        command += ' --force'
    result = subprocess.run(['docker', 'exec', '-i', CONTAINER, 'sh', '-c', command],
        input=('SET NAMES utf8mb4;\n' + (f'USE `{database}`;\n' if database else '') + sql).encode('utf-8'),
        capture_output=True)
    error = result.stderr.decode('utf-8')
    if not force and (result.returncode or 'ERROR' in error):
        raise RuntimeError(error)
    return result.stdout.decode('utf-8').strip(), error

def versions(db):
    return query(db, "SELECT version,checksum FROM zsjos_schema_version WHERE version='V288'; SELECT version,checksum FROM zsjos_module_schema_version WHERE module_code='core' AND version='V288';")[0]

def expand_sources(text, seen=None):
    seen = set() if seen is None else seen
    def include(match):
        path = (ROOT / match.group(1).strip()).resolve()
        assert path.is_relative_to(ROOT) and path not in seen, 'Invalid recursive SQL SOURCE'
        return expand_sources(path.read_text(encoding='utf-8'), seen | {path})
    return re.sub(r'^[ \t]*SOURCE[ \t]+(script/sql/[^;\r\n]+\.sql)[ \t]*;', include, text, flags=re.M | re.I)

def run():
    source = (SQL / 'schema/core.sql').read_text(encoding='utf-8') + (SQL / '02-bootstrap-zsjos-seed.sql').read_text(encoding='utf-8')
    def table(name):
        match = re.search(r'CREATE TABLE IF NOT EXISTS `' + name + r'` \([\s\S]+?\) ENGINE=[^;]+;', source)
        assert match, name
        return match.group()
    share_table = re.search(r'CREATE TABLE IF NOT EXISTS system_notice_share \([\s\S]+?\) ENGINE=[^;]+;', MIGRATION).group()
    prereq = "INSERT INTO zsjos_schema_version(version,description,checksum) VALUES ('V287','fixture','fixture'); INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version) VALUES ('core','V287','fixture','fixture','fixture');"
    for case in ['initial', 'partial', 'prerequisite', 'wrong_type', 'wrong_index', 'ledger_failure', 'existing_marker']:
        db = PREFIX + '_' + case
        query(None, f'CREATE DATABASE `{db}` CHARACTER SET utf8mb4;')
        setup = ''.join(table(name) for name in ['system_notice','system_notice_attachment','system_menu','zsjos_schema_version','zsjos_module_schema_version'])
        setup += "INSERT INTO system_menu(id,name,permission,type,sort,parent_id,path,icon,component,status,visible,keep_alive,always_show,deleted) VALUES (107,'通知公告','',2,1,1,'notice','','',0,1,1,1,0),(1036,'公告查询','system:notice:query',3,1,107,'#','','',0,1,1,1,0);"
        if case != 'prerequisite':
            setup += prereq
        if case in ['partial','wrong_type','wrong_index','existing_marker']:
            setup += share_table
        if case == 'wrong_type':
            setup += 'ALTER TABLE system_notice_share MODIFY token varchar(8) CHARACTER SET ascii COLLATE ascii_bin NOT NULL;'
        if case == 'wrong_index':
            setup += 'ALTER TABLE system_notice_share DROP INDEX uk_notice_share_token, ADD INDEX uk_notice_share_token(token);'
        if case == 'ledger_failure':
            setup += "CREATE TRIGGER reject_v288 BEFORE INSERT ON zsjos_module_schema_version FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='injected failure';"
        if case == 'existing_marker':
            setup += "INSERT INTO zsjos_schema_version(version,description,checksum) VALUES ('V288','fixture','preserve'); INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version) VALUES ('core','V288','fixture','preserve','fixture');"
        query(db, setup)
        before = versions(db)
        _, error = query(db, MIGRATION, True)
        if case in ['prerequisite','wrong_type','wrong_index','ledger_failure']:
            assert 'ERROR' in error and versions(db) == before, (case, error)
            assert query(db, "SELECT COUNT(*) FROM system_menu WHERE permission='system:notice:share'")[0] == '0'
            if case == 'prerequisite':
                query(db, prereq)
            elif case == 'wrong_type':
                query(db, 'ALTER TABLE system_notice_share MODIFY token varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL;')
            elif case == 'wrong_index':
                query(db, 'ALTER TABLE system_notice_share DROP INDEX uk_notice_share_token, ADD UNIQUE INDEX uk_notice_share_token(token);')
            else:
                query(db, 'DROP TRIGGER reject_v288;')
            query(db, MIGRATION)
        else:
            assert not error, error
        records = versions(db)
        assert len(records.splitlines()) == 2
        query(db, MIGRATION)
        assert versions(db) == records
        assert query(db, "SELECT HEX(name) FROM system_menu WHERE permission='system:notice:share'")[0] == '公告对外分享'.encode().hex().upper()
        assert query(db, 'SELECT COUNT(*) FROM system_notice_share')[0] == '0'
        # Token uniqueness is global and case-sensitive; notice uniqueness is tenant-scoped.
        query(db, "INSERT INTO system_notice_share(notice_id,token,attachment_ids,active,version,opened_by,opened_at,tenant_id) VALUES (1,'Abc','[]',1,1,7,NOW(),1),(1,'abc','[]',1,1,7,NOW(),2);")
        _, duplicate = query(db, "INSERT INTO system_notice_share(notice_id,token,attachment_ids,active,version,opened_by,opened_at,tenant_id) VALUES (2,'Abc','[]',1,1,7,NOW(),3);", True)
        assert 'Duplicate' in duplicate
        print('PASS', case, 'initial/replay/recovery, ledgers, permission HEX, scoped uniqueness')
    print('Retained isolated schemas:', PREFIX)
    assert 'system_role_menu' not in MIGRATION.lower()

def run_fresh():
    """Snapshot and verify the complete applicable fresh chain through this feature's version."""
    import hashlib
    global PREFIX
    # Historical migrations append long advisory-lock suffixes (MySQL limit: 64 bytes).
    PREFIX = 'ns288_' + datetime.datetime.now().strftime('%m%d%H%M%S')
    db = PREFIX + '_fresh'
    paths = sorted((SQL / 'migrations').glob('V*__*.sql'), key=lambda p: int(p.name.split('__')[0][1:]))
    paths = [p for p in paths if int(p.name.split('__')[0][1:]) <= 288]
    assert [int(p.name.split('__')[0][1:]) for p in paths] == list(range(1, 289))
    snapshots = {p: expand_sources(p.read_text(encoding='utf-8')) for p in paths}
    bootstrap = (SQL / 'bootstrap.sql').read_text(encoding='utf-8')
    sources = re.findall(r'^SOURCE ([^;]+);', bootstrap, re.M)
    baseline = [(name, (ROOT / name).read_text(encoding='utf-8')) for name in sources]
    query(None, f'CREATE DATABASE `{db}` CHARACTER SET utf8mb4;')
    for name, text in baseline:
        query(db, expand_sources(text))
        print('PASS bootstrap source', name, flush=True)
    installed = set(query(db, "SELECT version FROM zsjos_module_schema_version WHERE module_code='core'")[0].splitlines())
    for path, text in snapshots.items():
        version = path.name.split('__')[0]
        if version in installed:
            continue
        query(db, text)
        print('PASS fresh', version, flush=True)
    assert len(versions(db).splitlines()) == 2
    assert query(db, "SELECT HEX(name) FROM system_menu WHERE permission='system:notice:share'")[0] == '公告对外分享'.encode().hex().upper()
    assert query(db, 'SELECT COUNT(*) FROM system_notice_share')[0] == '0'
    query(db, MIGRATION)
    verify = query(db, expand_sources((SQL / 'verify/core.sql').read_text(encoding='utf-8')))[0]
    failures = [line for line in verify.splitlines() if re.search(r'(?:^|\t)(FAIL|MISSING)$', line)]
    assert not failures, failures
    fingerprints = '\n'.join(name + ' ' + hashlib.sha256(text.encode()).hexdigest() for name, text in baseline)
    print('PASS full fresh chain through V288; retained database:', db, flush=True)
    print('Baseline fingerprints:', fingerprints, flush=True)

if __name__ == '__main__':
    import sys
    run_fresh() if '--fresh-through-v288' in sys.argv else run()
