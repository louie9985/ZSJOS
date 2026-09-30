# -*- coding: utf-8 -*-
"""V287 tests on retained isolated MySQL schemas; no deletes or real business writes.

--apply-dev applies the verified additive migration to the inspected local configured database.
It backs up affected DDL/ledger rows, preserves all business rows, and never rewrites checksums.
"""
import datetime
import json
import re
import sys
import tempfile
from pathlib import Path
from test_calendar_notifications import query, table, ROOT

SQL = ROOT / 'script/sql/mysql'
MIGRATION = (SQL / 'migrations/V287__exam_revoke_reedit.sql').read_text(encoding='utf-8')
PREFIX = 'exam_reedit_' + datetime.datetime.now().strftime('%Y%m%d%H%M%S')
COLUMNS = "'revoked_at','revoked_by','reedit_claimed_at','reedit_operation_key'"
PREREQ = """INSERT INTO zsjos_schema_version(version,description,checksum) VALUES ('V286','fixture','fixture');
INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version)
VALUES ('core','V286','fixture','fixture','fixture');"""

def version(database):
    return query(database, "SELECT version,checksum FROM zsjos_schema_version WHERE version='V287';"
                 "SELECT version,checksum FROM zsjos_module_schema_version WHERE module_code='core' AND version='V287';")[0]

def signature(database):
    return query(database, "SELECT column_name,column_type,is_nullable,IFNULL(column_default,'NULL'),HEX(column_comment) "
                 "FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_exam_schedule' "
                 f"AND column_name IN ({COLUMNS}) ORDER BY column_name;")[0]

def business_signature(database):
    # Compare hashes, never expose the business content in test output or delivery notes.
    return query(database, "SELECT COUNT(*),COALESCE(BIT_XOR(CRC32(CONCAT_WS('|',id,tenant_id,record_status,"
                 "COALESCE(schedule_name,''),COALESCE(remark,''),COALESCE(exact_date,''),"
                 "COALESCE(start_date,''),COALESCE(end_date,''),deleted+0,update_time))),0) FROM zsjos_exam_schedule;")[0]

def scenarios():
    for case in ['initial', 'partial_marker', 'prerequisite', 'invalid_column', 'ledger_failure']:
        database = PREFIX + '_' + case
        query(None, f'CREATE DATABASE {database} CHARACTER SET utf8mb4;')
        setup = table('zsjos_schema_version') + table('zsjos_module_schema_version') + table('zsjos_exam_schedule')
        setup += "INSERT INTO zsjos_exam_schedule(id,tenant_id,schedule_type,exact_date,schedule_name,record_status) VALUES (1,1,'EXACT','2026-10-01','中文历史考试','REVOKED');"
        if case != 'prerequisite': setup += PREREQ
        if case == 'partial_marker':
            setup += "ALTER TABLE zsjos_exam_schedule ADD COLUMN revoked_at datetime(3) NULL COMMENT '撤销时间';"
            setup += "INSERT INTO zsjos_schema_version(version,description,checksum) VALUES ('V287','partial','preserve');"
        if case == 'invalid_column': setup += 'ALTER TABLE zsjos_exam_schedule ADD COLUMN revoked_by varchar(10) NULL;'
        if case == 'ledger_failure':
            setup += "CREATE TRIGGER reject_v287 BEFORE INSERT ON zsjos_module_schema_version FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='injected ledger failure';"
        query(database, setup)
        before, versions = business_signature(database), version(database)
        _, error = query(database, MIGRATION, force=True)
        if case in ('prerequisite', 'invalid_column', 'ledger_failure'):
            assert 'ERROR' in error and version(database) == versions, (case, error)
            if case == 'prerequisite': query(database, PREREQ)
            elif case == 'invalid_column': query(database, "ALTER TABLE zsjos_exam_schedule MODIFY revoked_by bigint NULL COMMENT '撤销操作人';")
            else: query(database, 'DROP TRIGGER reject_v287;')
            query(database, MIGRATION)
        else: assert not error, error
        assert business_signature(database) == before
        sig, versions = signature(database), version(database)
        assert len(sig.splitlines()) == 4 and len(versions.splitlines()) == 2
        assert '撤销时间'.encode().hex().upper() in sig
        assert query(database, 'SELECT HEX(schedule_name) FROM zsjos_exam_schedule;')[0] == '中文历史考试'.encode().hex().upper()
        assert query(database, 'SELECT revoked_at IS NULL AND revoked_by IS NULL FROM zsjos_exam_schedule;')[0] == '1'
        query(database, MIGRATION)
        assert signature(database) == sig and version(database) == versions and business_signature(database) == before
        print('PASS initial/repeat/recovery/data/UTF8:', case, flush=True)
    return PREFIX + '_initial'

def local_database():
    # Only the explicit loopback dev profile; application-local may point to a shared LAN master.
    config = (ROOT / 'backend/yudao-server/src/main/resources/application-dev.yaml').read_text(encoding='utf-8')
    found = re.search(r'jdbc:mysql://(?:localhost|127\.0\.0\.1):\d+/([^?\s]+)', config)
    if not found: raise RuntimeError('No explicit loopback development JDBC database found')
    return found.group(1)

def apply_dev(verified):
    database = local_database()
    inspection = query(database, "SELECT VERSION(); SELECT version FROM zsjos_schema_version WHERE version IN ('V286','V287');"
        "SELECT version FROM zsjos_module_schema_version WHERE module_code='core' AND version IN ('V286','V287');"
        "SELECT column_name,column_type FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_exam_schedule' ORDER BY ordinal_position;")[0]
    before = business_signature(database)
    backup = Path(tempfile.mkdtemp(prefix='exam-reedit-dev-schema-')) / 'before.json'
    ddl = query(database, 'SHOW CREATE TABLE zsjos_exam_schedule;')[0]
    backup.write_text(json.dumps({'database': database, 'inspection': inspection, 'ddl': ddl,
        'versions': version(database), 'business_signature': before}, ensure_ascii=False, indent=2), encoding='utf-8')
    query(database, MIGRATION)
    assert signature(database) == signature(verified), 'Development/controlled column drift'
    assert before == business_signature(database), 'Existing business data changed'
    assert len(version(database).splitlines()) == 2
    print('PASS local additive application, unchanged rows and controlled-schema comparison:', database)
    print('Scoped schema/ledger backup:', backup)

def fresh(verified):
    from test_free_exam import expand
    # Legacy migrations include DATABASE() in lock names (MySQL limit: 64 chars).
    database = 'er_' + datetime.datetime.now().strftime('%m%d%H%M%S')
    query(None, 'CREATE DATABASE ' + database + ' CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;')
    query(database, expand(SQL / 'bootstrap.sql'))
    applied = set(query(database, 'SELECT version FROM zsjos_schema_version;')[0].splitlines())
    for migration in sorted((SQL / 'migrations').glob('V*__*.sql')):
        if migration.name.split('__')[0] not in applied:
            query(database, expand(migration))
    output, _ = query(database, expand(SQL / 'verify/core.sql'))
    failures = [line for line in output.splitlines() if re.search(r'(?:^|\t)(FAIL|MISSING)$', line)]
    assert signature(database) == signature(verified), 'Fresh/scoped schema drift'
    assert not failures, failures
    print('PASS complete fresh chain through V287:', database, flush=True)

if __name__ == '__main__':
    verified = scenarios()
    print('Retained verification schema:', verified)
    if '--fresh' in sys.argv: fresh(verified)
    if '--apply-dev' in sys.argv: apply_dev(verified)
