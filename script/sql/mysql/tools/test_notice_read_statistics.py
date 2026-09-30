# -*- coding: utf-8 -*-
"""V284 controlled MySQL 8 checks. Uses only a dedicated notice-v284-verify container.

Start the disposable, network-isolated container separately. Keeps schemas for inspection;
never connects to the development database or removes existing schemas/data.
"""
import datetime
import os
import re
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[4]
SQL = ROOT / 'script/sql/mysql'
MIGRATION = (SQL / 'migrations/V284__notice_read_statistics.sql').read_text(encoding='utf-8')
CONTAINER = os.environ.get('NOTICE_VERIFY_CONTAINER', 'notice-v284-verify')
assert CONTAINER.startswith('notice-v284-verify'), 'Only the isolated verification container is allowed'

def query(database, sql, force=False):
    args = ['docker', 'exec', '-i', CONTAINER, 'mysql', '--default-character-set=utf8mb4', '-uroot', '-N', '-B']
    if force:
        args.append('--force')
    result = subprocess.run(args, input=('SET NAMES utf8mb4;\n' + (f'USE `{database}`;\n' if database else '') + sql).encode('utf-8'), capture_output=True)
    error = result.stderr.decode('utf-8')
    if not force and (result.returncode or 'ERROR' in error):
        raise RuntimeError(error)
    return result.stdout.decode('utf-8').strip(), error

def versions(database):
    return query(database, "SELECT version,checksum FROM zsjos_schema_version WHERE version='V284'; SELECT version,checksum FROM zsjos_module_schema_version WHERE module_code='core' AND version='V284';")[0]

def run():
    source = (SQL / 'schema/core.sql').read_text(encoding='utf-8') + (SQL / '02-bootstrap-zsjos-seed.sql').read_text(encoding='utf-8')
    def table(name):
        match = re.search(r'CREATE TABLE IF NOT EXISTS `' + name + r'` \([\s\S]+?\) ENGINE=[^;]+;', source)
        assert match, name
        return match.group()
    prefix = 'notice_v284_' + datetime.datetime.now().strftime('%Y%m%d%H%M%S')
    for case in ['initial', 'partial', 'prerequisite', 'wrong_type', 'ledger_failure', 'existing_marker_failure']:
        db = prefix + '_' + case
        query(None, f'CREATE DATABASE `{db}` CHARACTER SET utf8mb4;')
        setup = ''.join(table(name) for name in ['zsjos_schema_version', 'zsjos_module_schema_version', 'system_notice', 'system_notice_recipient', 'system_notice_read'])
        # The fresh schema already includes V284; remove its fields only in this
        # isolated fixture so initial/partial/failure cases still start at V283.
        setup += "ALTER TABLE system_notice DROP COLUMN recipient_snapshot_complete;"
        setup += ("ALTER TABLE system_notice_recipient DROP COLUMN user_name_snapshot, "
                  "DROP COLUMN dept_id_snapshot, DROP COLUMN dept_name_snapshot, "
                  "DROP COLUMN profile_snapshot_complete;")
        if case != 'prerequisite':
            setup += "INSERT INTO zsjos_schema_version(version,description,checksum) VALUES ('V283','fixture','fixture'); INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version) VALUES ('core','V283','fixture','fixture','fixture');"
        if case == 'partial':
            setup += "ALTER TABLE system_notice ADD COLUMN recipient_snapshot_complete bit(1) NOT NULL DEFAULT b'0';"
            setup += "INSERT INTO zsjos_schema_version(version,description,checksum) VALUES ('V284','fixture','preserve'); INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version) VALUES ('core','V284','fixture','preserve','fixture');"
        if case in ['wrong_type', 'existing_marker_failure']:
            setup += 'ALTER TABLE system_notice_recipient ADD COLUMN user_name_snapshot int DEFAULT NULL;'
        if case == 'existing_marker_failure':
            setup += "INSERT INTO zsjos_schema_version(version,description,checksum) VALUES ('V284','fixture','preserve'); INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version) VALUES ('core','V284','fixture','preserve','fixture');"
        if case == 'ledger_failure':
            setup += "CREATE TRIGGER reject_v284 BEFORE INSERT ON zsjos_module_schema_version FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='injected ledger failure';"
        query(db, setup)
        before = versions(db)
        _, error = query(db, MIGRATION, True)
        if case in ['prerequisite', 'wrong_type', 'ledger_failure', 'existing_marker_failure']:
            assert 'ERROR' in error and versions(db) == before, (case, error)
            if case == 'prerequisite':
                query(db, "INSERT INTO zsjos_schema_version(version,description,checksum) VALUES ('V283','fixture','fixture'); INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version) VALUES ('core','V283','fixture','fixture','fixture');")
            elif case in ['wrong_type', 'existing_marker_failure']:
                query(db, 'ALTER TABLE system_notice_recipient MODIFY COLUMN user_name_snapshot varchar(64) DEFAULT NULL;')
            else:
                query(db, 'DROP TRIGGER reject_v284;')
            query(db, MIGRATION)
        else:
            assert not error, error
        records = versions(db)
        assert len(records.splitlines()) == 2
        query(db, MIGRATION)
        assert versions(db) == records
        assert query(db, "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND ((table_name='system_notice' AND column_name='recipient_snapshot_complete') OR (table_name='system_notice_recipient' AND column_name IN ('user_name_snapshot','dept_id_snapshot','dept_name_snapshot','profile_snapshot_complete')));")[0] == '5'
        query(db, "INSERT INTO system_notice_recipient(notice_id,user_id,user_name_snapshot,dept_name_snapshot,profile_snapshot_complete,tenant_id) VALUES (1,1,'测试姓名','测试部门',1,1);")
        expected = '测试姓名'.encode('utf-8').hex().upper() + '\t' + '测试部门'.encode('utf-8').hex().upper()
        assert query(db, 'SELECT HEX(user_name_snapshot),HEX(dept_name_snapshot) FROM system_notice_recipient WHERE notice_id=1 AND user_id=1;')[0] == expected
        print('PASS', case, 'initial/replay or failure/recovery, ledgers, UTF-8 snapshots')
    print('Retained isolated schemas:', prefix)

if __name__ == '__main__':
    run()
