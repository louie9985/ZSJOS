# -*- coding: utf-8 -*-
"""V354 controlled MySQL verification; retained schemas, no shared-data writes."""
import datetime
import hashlib

from test_calendar_notifications import SQL, query, table

MIGRATION = (SQL / 'migrations/V354__my_operations_menu.sql').read_text(encoding='utf-8')
PREFIX = 'ops354_' + datetime.datetime.now().strftime('%Y%m%d%H%M%S')
PERMISSION = 'zsjos:my-operations:query'
ROOT = "INSERT INTO system_menu(id,name,permission,type,parent_id,path) VALUES (6735,'中世健','',1,0,'/zsjos');"
PAGE = "INSERT INTO system_menu(id,name,permission,type,parent_id,path,component,sort) VALUES (90001,'我的运营','zsjos:my-operations:query',2,6735,'my-operations','zsjos/myOperations/index',41);"
PREREQ = ("INSERT INTO zsjos_schema_version(version,description,checksum) VALUES ('V353','fixture','fixture');"
          "INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version) VALUES ('core','V353','fixture','fixture','fixture');")
LEGACY_MARKER = "INSERT INTO zsjos_schema_version(version,description,checksum) VALUES ('V354','preserve-description','preserve-checksum');"
MODULE_MARKER = "INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version) VALUES ('core','V354','preserve-description','preserve-checksum','fixture');"


def versions(database):
    # A missing ledger is itself a tested prerequisite failure.
    rows = []
    for name in ('zsjos_schema_version', 'zsjos_module_schema_version'):
        if query(database, f"SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='{name}';")[0] == '1':
            rows.append(query(database, f"SELECT * FROM {name} ORDER BY version;")[0])
        else:
            rows.append('<missing>')
    return rows


def preserved_signature(database):
    result = query(database, f"SELECT * FROM system_menu WHERE permission<>'{PERMISSION}' ORDER BY id;"
                   'SELECT * FROM system_role_menu ORDER BY id; SELECT * FROM system_tenant_package ORDER BY id;')[0]
    return hashlib.sha256(result.encode('utf-8')).hexdigest()


def verify(database, custom=False):
    rows = query(database, f"SELECT permission,parent_id,type,deleted+0,path,component,HEX(name),status,sort,visible+0 "
                 f"FROM system_menu WHERE permission='{PERMISSION}';")[0].splitlines()
    assert len(rows) == 1, rows
    expected = [PERMISSION, '6735', '2', '0', 'my-operations', 'zsjos/myOperations/index',
                ('自定义名称' if custom else '我的运营').encode('utf-8').hex().upper(),
                '1' if custom else '0', '17' if custom else '41', '0' if custom else '1']
    assert rows[0].split('\t') == expected, rows[0]
    assert query(database, "SELECT COUNT(*) FROM zsjos_schema_version WHERE version='V354';"
                 "SELECT COUNT(*) FROM zsjos_module_schema_version WHERE module_code='core' AND version='V354';")[0] == '1\n1'


def run():
    cases = ['initial', 'partial_page', 'partial_markers', 'parent_missing', 'parent_duplicate',
             'identity_conflict', 'retired_permission', 'duplicate_permission', 'path_conflict',
             'component_conflict', 'prerequisite_missing', 'legacy_ledger_missing', 'module_ledger_missing',
             'ledger_failure', 'existing_ledger_failure']
    for case in cases:
        database = PREFIX + '_' + case
        query(None, f'CREATE DATABASE `{database}` CHARACTER SET utf8mb4;')
        tables = ['system_menu', 'system_role_menu', 'system_tenant_package', 'zsjos_schema_version', 'zsjos_module_schema_version']
        missing = {'legacy_ledger_missing': 'zsjos_schema_version', 'module_ledger_missing': 'zsjos_module_schema_version'}.get(case)
        setup = ''.join(table(name) for name in tables if name != missing)
        setup += "INSERT INTO system_menu(id,name,permission,type,parent_id,path) VALUES (73020,'其他页面','fixture:query',2,6735,'fixture');"
        if case != 'parent_missing':
            setup += ROOT
        setup += "INSERT INTO system_role_menu(role_id,menu_id,tenant_id) VALUES (101,6735,1),(102,73020,1);"
        setup += "INSERT INTO system_tenant_package(id,name,status,menu_ids) VALUES (1,'测试套餐',0,'[6735,73020]');"
        if case != 'prerequisite_missing':
            for statement in PREREQ.split(';'):
                if statement and (missing is None or 'INTO ' + missing + '(' not in statement):
                    setup += statement + ';'
        if case in ('partial_page', 'identity_conflict', 'retired_permission', 'duplicate_permission'):
            setup += PAGE
        if case == 'partial_markers':
            setup += LEGACY_MARKER + MODULE_MARKER
        if case == 'existing_ledger_failure':
            setup += LEGACY_MARKER
        if case == 'parent_duplicate':
            setup += ROOT.replace('6735', '99001')
        elif case == 'identity_conflict':
            setup += 'UPDATE system_menu SET parent_id=73020 WHERE id=90001;'
        elif case == 'retired_permission':
            setup += 'UPDATE system_menu SET deleted=1 WHERE id=90001;'
        elif case == 'duplicate_permission':
            setup += PAGE.replace('90001', '99001')
        elif case == 'path_conflict':
            setup += PAGE.replace('90001', '99001').replace(PERMISSION, 'fixture:conflict').replace('zsjos/myOperations/index', 'fixture/index')
        elif case == 'component_conflict':
            setup += PAGE.replace('90001', '99001').replace(PERMISSION, 'fixture:conflict').replace("'my-operations'", "'fixture'")
        elif case in ('ledger_failure', 'existing_ledger_failure'):
            setup += "CREATE TRIGGER reject_ops_ledger BEFORE INSERT ON zsjos_module_schema_version FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='injected failure';"
        query(database, setup)
        before_versions = versions(database)
        before_menus = query(database, 'SELECT * FROM system_menu ORDER BY id;')[0]
        preserved = preserved_signature(database)
        _, error = query(database, MIGRATION, force=True)
        if case in ('initial', 'partial_page', 'partial_markers'):
            assert not error, error
        else:
            assert 'ERROR' in error, (case, error)
            assert versions(database) == before_versions, case
            assert query(database, 'SELECT * FROM system_menu ORDER BY id;')[0] == before_menus, case
            assert preserved_signature(database) == preserved, case
            if case == 'parent_missing':
                query(database, ROOT)
            elif case == 'parent_duplicate':
                query(database, "UPDATE system_menu SET path='/fixture' WHERE id=99001;")
            elif case == 'identity_conflict':
                query(database, 'UPDATE system_menu SET parent_id=6735 WHERE id=90001;')
            elif case == 'retired_permission':
                query(database, "UPDATE system_menu SET permission='fixture:retired' WHERE id=90001;")
            elif case in ('duplicate_permission', 'path_conflict', 'component_conflict'):
                query(database, "UPDATE system_menu SET permission='fixture:other',path='other',component='fixture/other' WHERE id=99001;")
            elif case == 'prerequisite_missing':
                query(database, PREREQ)
            elif missing:
                query(database, table(missing) + next(statement for statement in PREREQ.split(';') if 'INTO ' + missing + '(' in statement) + ';')
            else:
                query(database, 'DROP TRIGGER reject_ops_ledger;')
            preserved = preserved_signature(database)
            query(database, MIGRATION)
        verify(database)
        if case in ('partial_markers', 'existing_ledger_failure'):
            assert 'preserve-description\tpreserve-checksum' in '\n'.join(versions(database)), case
        if case == 'partial_markers':
            assert versions(database) == before_versions, case
        before_versions = versions(database)
        query(database, MIGRATION)
        verify(database)
        assert versions(database) == before_versions, case
        assert preserved_signature(database) == preserved, case
        query(database, f"UPDATE system_menu SET name='自定义名称',status=1,sort=17,visible=0 WHERE permission='{PERMISSION}';")
        query(database, MIGRATION)
        verify(database, custom=True)
        assert versions(database) == before_versions, case
        assert preserved_signature(database) == preserved, case
        print('PASS', case, 'initial/replay/recovery/UTF8/admin metadata/grants/packages preserved')
    print('Retained isolated database prefix:', PREFIX)


if __name__ == '__main__':
    run()
