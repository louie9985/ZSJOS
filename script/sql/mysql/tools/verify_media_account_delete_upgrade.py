#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""V271 MySQL integration checks in the existing disposable-container test runner."""
import re
import subprocess

import zsjos_db as db


def query(container, schema, sql):
    result = subprocess.run([
        'docker', 'exec', '-i', container, 'mysql', '--default-character-set=utf8mb4',
        '-uroot', '--batch', '--raw', '--skip-column-names', schema,
    ], input=('SET NAMES utf8mb4;\n' + sql).encode('utf-8'), capture_output=True)
    if result.returncode:
        raise AssertionError(result.stderr.decode('utf-8'))
    return result.stdout.decode('utf-8').strip()


def execute(container):
    baseline = '\n'.join((db.SQL_ROOT / name).read_text(encoding='utf-8') for name in
                         ('00-bootstrap-schema.sql', '02-bootstrap-zsjos-seed.sql'))
    migration = (db.SQL_ROOT / 'migrations/V271__media_account_delete_approval.sql').read_text(encoding='utf-8')
    expected = {
        'delete_process_instance_id': 'varchar(64)', 'delete_status': 'varchar(24)',
        'delete_requested_by_user_id': 'bigint', 'delete_reviewer_user_id': 'bigint',
        'delete_reason': 'varchar(500)', 'delete_result_reason': 'varchar(500)',
    }
    for mode in ('fresh', 'partial'):
        schema = 'v271_' + mode
        query(container, 'zsjos_test', f'CREATE DATABASE {schema} CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci')
        q = lambda sql: query(container, schema, sql)
        for table in ('zsjos_media_account', 'system_menu', 'system_role_menu',
                      'zsjos_schema_version', 'zsjos_module_schema_version'):
            ddl = re.search(r'CREATE TABLE IF NOT EXISTS `' + table + r'`\s*\(.*?;', baseline, re.S)
            assert ddl, table
            q(ddl.group())
        q("INSERT INTO system_menu(id,name,permission,type,sort,parent_id) VALUES (1,'测试账号','zsjos:media-account:query',2,1,0)")
        q("INSERT INTO system_role_menu(role_id,menu_id,tenant_id) VALUES (1,1,1)")
        q("INSERT INTO zsjos_media_account(account_no,ownership_type,nickname,tenant_id) VALUES ('fixture1','test','历史账号',1),('fixture2','test','另一租户',2)")
        q("INSERT INTO zsjos_schema_version(version,description) VALUES ('V270','fixture'); INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version) VALUES ('core','V270','fixture','fixture','test')")
        before = q('SELECT id,tenant_id,HEX(nickname),update_time FROM zsjos_media_account ORDER BY id')
        grants = q('SELECT * FROM system_role_menu ORDER BY id')
        if mode == 'partial':
            # Reproduce a client continuing to menus/ledgers after failed column/index DDL.
            q(migration[:migration.index('SET @ddl')])
            q(migration[migration.index('INSERT INTO system_menu'):])
            # Also cover an interrupted repair with only some columns already installed.
            q("ALTER TABLE zsjos_media_account ADD COLUMN delete_status varchar(24) DEFAULT NULL; UPDATE zsjos_media_account SET delete_status='pending' WHERE tenant_id=1")
            before = q('SELECT id,tenant_id,HEX(nickname),update_time FROM zsjos_media_account ORDER BY id')
        for attempt in range(2):
            q(migration)
            columns = q("SELECT column_name,column_type,is_nullable FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_media_account' AND LEFT(column_name,7)='delete_' ORDER BY column_name")
            assert columns == '\n'.join(f'{name}\t{kind}\tYES' for name, kind in sorted(expected.items())), columns
            assert q("SELECT column_name FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='zsjos_media_account' AND index_name='idx_media_account_delete_process'") == 'delete_process_instance_id'
            assert q('SELECT id,tenant_id,HEX(nickname),update_time FROM zsjos_media_account ORDER BY id') == before
            assert q('SELECT * FROM system_role_menu ORDER BY id') == grants
            assert q("SELECT COUNT(*) FROM zsjos_schema_version WHERE version='V271'") == '1'
            assert q("SELECT COUNT(*) FROM zsjos_module_schema_version WHERE module_code='core' AND version='V271'") == '1'
            for permission, label in [('delete', '删除账号'), ('delete-approve', '审批账号删除')]:
                assert q(f"SELECT parent_id,HEX(name) FROM system_menu WHERE permission='zsjos:media-account:{permission}' AND deleted=0") == '1\t' + label.encode('utf-8').hex().upper()
            assert q("SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='zsjos_media_account_delete_request' AND index_name='uk_tenant_process' AND non_unique=0") == '3'
            assert q('SELECT COUNT(*) FROM zsjos_media_account_delete_request') == '0'
            if mode == 'partial':
                assert q('SELECT delete_status FROM zsjos_media_account WHERE tenant_id=1') == 'pending'
        print(f'PASS V271 {mode}: columns, index, request constraint, menus/UTF-8 HEX, ledgers, tenant rows, grants, replay')


if __name__ == '__main__':
    db.with_test_mysql(execute)
