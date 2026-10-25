# -*- coding: utf-8 -*-
"""V350 isolated MySQL verification; retains fixtures and never changes shared grants."""
import datetime
import hashlib

from test_calendar_notifications import SQL, query, table

MIGRATION = (SQL / 'migrations/V350__student_archive_read_permissions.sql').read_text(encoding='utf-8')
PREFIX = 'archive_permissions_' + datetime.datetime.now().strftime('%Y%m%d%H%M%S')
PERMISSIONS = "'zsjos:student-archive:sensitive-read','zsjos:student-archive:export'"


def snapshot(db):
    return query(db, f"SELECT * FROM system_menu WHERE permission NOT IN ({PERMISSIONS}) ORDER BY id;"
                 "SELECT * FROM system_role_menu ORDER BY id; SELECT * FROM system_tenant_package ORDER BY id;")[0]


def ledgers(db):
    return query(db, "SELECT * FROM zsjos_schema_version ORDER BY version;"
                 "SELECT * FROM zsjos_module_schema_version ORDER BY module_code,version;")[0]


def verify(db):
    rows = query(db, f"SELECT permission,parent_id,type,deleted+0,HEX(name) FROM system_menu "
                 f"WHERE permission IN ({PERMISSIONS}) ORDER BY permission;")[0].splitlines()
    assert rows == [
        '\t'.join(['zsjos:student-archive:export', '73020', '3', '0', '导出学员档案'.encode().hex().upper()]),
        '\t'.join(['zsjos:student-archive:sensitive-read', '73020', '3', '0', '查看学员档案敏感资料及附件'.encode().hex().upper()]),
    ], rows
    assert query(db, "SELECT COUNT(*) FROM zsjos_schema_version WHERE version='V350';"
                 "SELECT COUNT(*) FROM zsjos_module_schema_version WHERE module_code='core' AND version='V350';")[0] == '1\n1'


def run():
    for case in ['initial', 'partial', 'missing_parent', 'duplicate_parent', 'missing_prerequisite',
                 'wrong_parent', 'retired', 'duplicate', 'ledger_failure', 'existing_ledger_failure']:
        db = PREFIX + '_' + case
        query(None, f'CREATE DATABASE `{db}` CHARACTER SET utf8mb4;')
        setup = ''.join(table(name) for name in ['system_menu', 'system_role_menu', 'system_tenant_package',
                                                'zsjos_schema_version', 'zsjos_module_schema_version'])
        setup += "INSERT INTO system_menu(id,name,permission,type,parent_id,path) VALUES " \
                 "(6735,'Workbench','',1,0,'/zsjos'),(73000,'Pool','zsjos:registration:query-pool',2,6735,'registration-pool')," \
                 "(73020,'Students','zsjos:student:query-my',2,6735,'my-students')," \
                 "(90001,'Read','zsjos:student-archive:read',3,73020,''),(90002,'Update','zsjos:student-archive:update',3,73020,'')," \
                 "(90003,'Registration sensitive','zsjos:student-info-form:sensitive-read',3,73000,'');"
        setup += "INSERT INTO system_role_menu(role_id,menu_id,tenant_id) VALUES (3012,73020,1),(3014,90001,1);"
        setup += "INSERT INTO system_tenant_package(id,name,status,menu_ids) VALUES (1,'fixture',0,'[6735,73020]');"
        setup += "INSERT INTO zsjos_schema_version(version,description,checksum) VALUES ('V327','fixture','fixture');"
        setup += "INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version) VALUES ('core','V327','fixture','fixture','fixture');"
        if case == 'missing_parent':
            setup += "UPDATE system_menu SET deleted=1 WHERE id=73020;"
        if case == 'duplicate_parent':
            setup += "INSERT INTO system_menu(name,permission,type,parent_id) VALUES ('duplicate','zsjos:student:query-my',2,6735);"
        if case == 'missing_prerequisite':
            setup += "UPDATE zsjos_module_schema_version SET version='fixture' WHERE version='V327';"
        if case in ['partial', 'wrong_parent', 'retired', 'duplicate']:
            setup += "INSERT INTO system_menu(id,name,permission,type,parent_id) VALUES (90004,'查看学员档案敏感资料及附件','zsjos:student-archive:sensitive-read',3,73020);"
        if case == 'wrong_parent':
            setup += "UPDATE system_menu SET parent_id=73000 WHERE id=90004;"
        if case == 'retired':
            setup += "UPDATE system_menu SET deleted=1 WHERE id=90004;"
        if case == 'duplicate':
            setup += "INSERT INTO system_menu(name,permission,type,parent_id) VALUES ('duplicate','zsjos:student-archive:sensitive-read',3,73020);"
        if case in ['partial', 'existing_ledger_failure']:
            setup += "INSERT INTO zsjos_schema_version(version,description,checksum) VALUES ('V350','preserve','preserve');"
        if case == 'partial':
            setup += "INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version) VALUES ('core','V350','preserve','preserve','fixture');"
        if case in ['ledger_failure', 'existing_ledger_failure']:
            setup += "CREATE TRIGGER reject_v350 BEFORE INSERT ON zsjos_module_schema_version FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='fixture failure';"
        query(db, setup)
        before, versions = snapshot(db), ledgers(db)
        if case not in ['initial', 'partial']:
            _, error = query(db, MIGRATION, force=True)
            assert 'ERROR' in error, case
            assert versions == ledgers(db), case
            assert before == snapshot(db), case
            if case not in ['ledger_failure', 'existing_ledger_failure']:
                print('PASS fail-closed', case, db)
                continue
            assert query(db, f'SELECT COUNT(*) FROM system_menu WHERE permission IN ({PERMISSIONS});')[0] == '0'
            query(db, 'DROP TRIGGER reject_v350;')
        query(db, MIGRATION)
        verify(db)
        assert before == snapshot(db)
        completed, metadata = ledgers(db), query(db, f'SELECT * FROM system_menu WHERE permission IN ({PERMISSIONS}) ORDER BY id;')[0]
        query(db, MIGRATION)
        verify(db)
        assert completed == ledgers(db)
        assert metadata == query(db, f'SELECT * FROM system_menu WHERE permission IN ({PERMISSIONS}) ORDER BY id;')[0]
        assert before == snapshot(db)
        # Administrator customization survives replay; version markers do not bypass checks.
        query(db, "UPDATE system_menu SET name='custom',status=1,sort=17 WHERE permission='zsjos:student-archive:export';")
        query(db, MIGRATION)
        assert query(db, "SELECT name,status,sort FROM system_menu WHERE permission='zsjos:student-archive:export';")[0] == 'custom\t1\t17'
        print('PASS apply/replay/recovery/UTF8/grants', case, db, hashlib.sha256(before.encode()).hexdigest()[:12])


if __name__ == '__main__':
    run()
