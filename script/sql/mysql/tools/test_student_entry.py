#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Verify V345 on retained synthetic MySQL 8 databases; application tables are read-only."""
import datetime
from concurrent.futures import ThreadPoolExecutor
from test_positioning_single_source import query, ROOT

SQL = (ROOT / 'script/sql/mysql/migrations/V345__independent_student_entry.sql').read_text(encoding='utf-8')
STAMP = datetime.datetime.now().strftime('%Y%m%d%H%M%S%f')
SOURCE = 'ruoyi-vue-pro'

def fixture(suffix, prerequisite=True):
    db = 'student_entry_' + STAMP + '_' + suffix
    query(SOURCE, f'CREATE DATABASE `{db}` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;')
    for table in ['zsjos_service_relation', 'system_menu', 'zsjos_schema_version', 'zsjos_module_schema_version']:
        query(db, f'CREATE TABLE `{table}` LIKE `{SOURCE}`.`{table}`;')
    # Only this new, empty fixture is normalized; the source can advance independently during testing.
    if query(db, "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_service_relation' AND column_name='media_ready';").strip() == '1':
        query(db, 'ALTER TABLE zsjos_service_relation DROP COLUMN media_ready;')
    query(db, "INSERT INTO system_menu(name,permission,type,sort,parent_id,path,icon,component,status) VALUES('班级管理','zsjos:delivery-class:query',2,1,9000,'class-management','','',0);")
    if prerequisite:
        query(db, "INSERT INTO zsjos_schema_version(version,description,checksum) VALUES('V344','fixture','fixture');"
                  "INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version) VALUES('core','V344','fixture','fixture','fixture');")
    return db

def ledger(db):
    return query(db, "SELECT COUNT(*) FROM zsjos_schema_version WHERE version='V345'; SELECT COUNT(*) FROM zsjos_module_schema_version WHERE module_code='core' AND version='V345';").strip()

def reject(db):
    try:
        query(db, SQL)
    except RuntimeError:
        assert ledger(db) == '0\n0'
    else:
        raise AssertionError('migration should fail')

if __name__ == '__main__':
    db = fixture('initial')
    query(db, SQL)
    assert ledger(db) == '1\n1'
    query(db, SQL)
    assert ledger(db) == '1\n1'
    assert query(db, "SELECT HEX(name),path,parent_id FROM system_menu WHERE permission='zsjos:student:create';").strip() == '学员录入'.encode().hex().upper() + '\tstudent-entry\t9000'
    assert query(db, "SELECT HEX(description) FROM zsjos_schema_version WHERE version='V345';").strip() == '学员直接新增'.encode().hex().upper()
    assert query(db, "SELECT is_nullable FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_service_relation' AND column_name='media_ready';").strip() == 'YES'
    missing = fixture('missing_prerequisite', False)
    reject(missing)
    partial = fixture('partial')
    query(partial, "ALTER TABLE zsjos_service_relation ADD COLUMN media_ready bit(1) DEFAULT NULL;")
    query(partial, SQL)
    assert ledger(partial) == '1\n1'
    invalid = fixture('invalid_structure')
    query(invalid, "ALTER TABLE zsjos_service_relation ADD COLUMN media_ready varchar(10) DEFAULT NULL;")
    reject(invalid)
    conflict = fixture('menu_conflict')
    query(conflict, "INSERT INTO system_menu(name,permission,type,sort,parent_id,path,icon,component,status) VALUES('conflict','zsjos:student:create',3,1,0,'','','',0);")
    reject(conflict)
    query(conflict, "UPDATE system_menu SET permission='fixture:other' WHERE permission='zsjos:student:create';")
    query(conflict, SQL)
    assert ledger(conflict) == '1\n1'
    wrong_engine = fixture('wrong_engine')
    # Start from an otherwise correct command structure, but never trust a prior success marker alone.
    query(wrong_engine, SQL)
    query(wrong_engine, "ALTER TABLE zsjos_student_entry_command ENGINE=MyISAM;")
    try:
        query(wrong_engine, SQL)
    except RuntimeError:
        assert ledger(wrong_engine) == '1\n1'
    else:
        raise AssertionError('non-transactional command storage must fail revalidation')
    for suffix, alteration in [
        ('extra_unique_column', 'DROP INDEX uk_student_entry_command, ADD UNIQUE KEY uk_student_entry_command(tenant_id,operator_user_id,command_key,id)'),
        ('missing_primary', 'ADD KEY fixture_id(id), DROP PRIMARY KEY'),
        ('missing_auto_increment', 'MODIFY id bigint NOT NULL')]:
        broken = fixture(suffix)
        query(broken, f'CREATE TABLE zsjos_student_entry_command LIKE `{SOURCE}`.zsjos_student_entry_command;')
        query(broken, 'ALTER TABLE zsjos_student_entry_command '+alteration+';')
        reject(broken)
    recovered = fixture('unique_recovery')
    query(recovered, f'CREATE TABLE zsjos_student_entry_command LIKE `{SOURCE}`.zsjos_student_entry_command;')
    query(recovered, 'ALTER TABLE zsjos_student_entry_command DROP INDEX uk_student_entry_command, ADD UNIQUE KEY uk_student_entry_command(tenant_id,operator_user_id,command_key,id);')
    reject(recovered)
    query(recovered, 'ALTER TABLE zsjos_student_entry_command DROP INDEX uk_student_entry_command, ADD UNIQUE KEY uk_student_entry_command(tenant_id,operator_user_id,command_key);')
    query(recovered, SQL)
    assert ledger(recovered) == '1\n1'
    def reserve(_):
        query(db, "START TRANSACTION;INSERT IGNORE INTO zsjos_student_entry_command(tenant_id,operator_user_id,command_key,request_hash) VALUES(1,99,'parallel','hash');SELECT id FROM zsjos_student_entry_command WHERE tenant_id=1 AND operator_user_id=99 AND command_key='parallel' FOR UPDATE;COMMIT;")
    with ThreadPoolExecutor(max_workers=4) as pool:
        list(pool.map(reserve, range(4)))
    assert query(db, 'SELECT COUNT(*) FROM zsjos_student_entry_command;').strip() == '1'
    print('PASS MySQL initial/repeat/partial recovery/prerequisite+DDL+menu failures/no false success/UTF8 HEX/concurrent reservation', db)
