# -*- coding: utf-8 -*-
"""V351 checks in retained isolated MySQL databases; never applies to a shared database."""
import datetime
import hashlib

from test_calendar_notifications import SQL, query, table


MIGRATION = (SQL / 'migrations/V351__sales_order_query_all_permission.sql').read_text(encoding='utf-8')
PREFIX = 'order_all_' + datetime.datetime.now().strftime('%Y%m%d%H%M%S')
PERMISSIONS = "'zsjos:sales-order:query-all'"
ORDER_PARENT = "INSERT INTO system_menu(id,name,permission,type,parent_id,path) VALUES (73620,'订单管理','zsjos:sales-order:query-management',2,6735,'sales-orders');"
OTHER_PARENT = "INSERT INTO system_menu(id,name,permission,type,parent_id,path) VALUES (73020,'学员管理','zsjos:student:query-my',2,6735,'my-students');"
EXISTING_ORDER_PERMISSION = "INSERT INTO system_menu(id,name,permission,type,parent_id) VALUES (90001,'查看全部订单','zsjos:sales-order:query-all',3,73620);"


def versions(database):
    return query(database, "SELECT version,description,checksum FROM zsjos_schema_version WHERE version='V351';"
                 "SELECT module_code,version,description,checksum FROM zsjos_module_schema_version WHERE module_code='core' AND version='V351';")[0]


def preserved_signature(database):
    text = query(database, f'SELECT * FROM system_menu WHERE permission NOT IN ({PERMISSIONS}) ORDER BY id;'
                 'SELECT * FROM system_role_menu ORDER BY id; SELECT * FROM system_tenant_package ORDER BY id;')[0]
    return hashlib.sha256(text.encode('utf-8')).hexdigest()


def verify(database, custom=False):
    rows = query(database, f'SELECT permission,parent_id,type,deleted+0,HEX(name),status,sort FROM system_menu '
                 f'WHERE permission IN ({PERMISSIONS}) ORDER BY permission;')[0].splitlines()
    expected = [
        ('zsjos:sales-order:query-all', '73620', '查看全部订单'),
    ]
    assert len(rows) == 1, rows
    for row, (permission, parent_id, label) in zip(rows, expected):
        columns = row.split('\t')
        assert columns[:4] == [permission, parent_id, '3', '0'], row
        assert columns[4] == ('自定义名称' if custom else label).encode('utf-8').hex().upper(), row
        if custom:
            assert columns[5:] == ['1', '17'], row
    assert len(versions(database).splitlines()) == 2


def run():
    cases = ['initial', 'partial', 'parent_missing', 'parent_duplicate', 'identity_conflict',
             'retired_permission', 'duplicate_permission', 'ledger_failure', 'existing_ledger_failure']
    for case in cases:
        database = PREFIX + '_' + case
        query(None, f'CREATE DATABASE `{database}` CHARACTER SET utf8mb4;')
        setup = ''.join(table(name) for name in ['system_menu', 'system_role_menu', 'system_tenant_package',
                                               'zsjos_schema_version', 'zsjos_module_schema_version'])
        setup += OTHER_PARENT
        if case != 'parent_missing':
            setup += ORDER_PARENT
        setup += "INSERT INTO system_role_menu(role_id,menu_id,tenant_id) VALUES (101,73620,1),(102,73020,1);"
        setup += "INSERT INTO system_tenant_package(id,name,status,menu_ids) VALUES (1,'测试套餐',0,'[73620,73020]');"
        if case in ('partial', 'identity_conflict', 'retired_permission', 'duplicate_permission'):
            setup += EXISTING_ORDER_PERMISSION
        if case == 'partial':
            setup += "INSERT INTO zsjos_schema_version(version,description,checksum) VALUES ('V351','preserve-description','preserve-checksum');"
            setup += "INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version) VALUES ('core','V351','preserve-description','preserve-checksum','fixture');"
        if case == 'existing_ledger_failure':
            setup += "INSERT INTO zsjos_schema_version(version,description,checksum) VALUES ('V351','preserve-description','preserve-checksum');"
        if case == 'parent_duplicate':
            setup += "INSERT INTO system_menu(id,name,permission,type,parent_id) VALUES (99001,'冲突父菜单','zsjos:sales-order:query-management',2,6735);"
        elif case == 'identity_conflict':
            setup += 'UPDATE system_menu SET parent_id=73020 WHERE id=90001;'
        elif case == 'retired_permission':
            setup += 'UPDATE system_menu SET deleted=1 WHERE id=90001;'
        elif case == 'duplicate_permission':
            setup += EXISTING_ORDER_PERMISSION.replace('90001', '99001')
        elif case in ('ledger_failure', 'existing_ledger_failure'):
            setup += "CREATE TRIGGER reject_query_all_ledger BEFORE INSERT ON zsjos_module_schema_version FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='injected failure';"
        query(database, setup)
        before_versions = versions(database)
        before_menus = query(database, 'SELECT * FROM system_menu ORDER BY id;')[0]
        preserved = preserved_signature(database)
        _, error = query(database, MIGRATION, force=True)
        if case in ('initial', 'partial'):
            assert not error, error
        else:
            assert 'ERROR' in error, (case, error)
            assert versions(database) == before_versions, case
            assert query(database, 'SELECT * FROM system_menu ORDER BY id;')[0] == before_menus, case
            assert preserved_signature(database) == preserved, case
            if case == 'parent_missing':
                query(database, ORDER_PARENT)
            elif case == 'parent_duplicate':
                query(database, "UPDATE system_menu SET permission='fixture:other-page' WHERE id=99001;")
            elif case == 'identity_conflict':
                query(database, 'UPDATE system_menu SET parent_id=73620 WHERE id=90001;')
            elif case == 'retired_permission':
                query(database, "UPDATE system_menu SET permission='fixture:retired-query-all' WHERE id=90001;")
            elif case == 'duplicate_permission':
                query(database, "UPDATE system_menu SET permission='fixture:other-button' WHERE id=99001;")
            else:
                query(database, 'DROP TRIGGER reject_query_all_ledger;')
            preserved = preserved_signature(database)
            query(database, MIGRATION)
        verify(database)
        if case in ('partial', 'existing_ledger_failure'):
            assert 'preserve-description\tpreserve-checksum' in versions(database), case
        if case == 'partial':
            assert versions(database) == before_versions, case
        before_versions = versions(database)
        query(database, MIGRATION)
        verify(database)
        assert versions(database) == before_versions, case
        assert preserved_signature(database) == preserved, case
        query(database, f"UPDATE system_menu SET name='自定义名称',status=1,sort=17 WHERE permission IN ({PERMISSIONS});")
        query(database, MIGRATION)
        verify(database, custom=True)
        assert versions(database) == before_versions, case
        assert preserved_signature(database) == preserved, case
        print('PASS', case, 'initial/replay/recovery/UTF8/admin configuration/grants/packages preserved')
    print('Retained isolated database prefix:', PREFIX)


if __name__ == '__main__':
    run()
