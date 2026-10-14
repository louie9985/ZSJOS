# -*- coding: utf-8 -*-
"""Retained MySQL fixtures for V320; optional scoped, additive local apply. No grants."""
import datetime
import hashlib
import sys
from test_calendar_notifications import query, table, SQL

MIGRATION = (SQL / 'migrations/V320__exam_calendar_export_permission.sql').read_text(encoding='utf-8')
PREFIX = 'exam_export_' + datetime.datetime.now().strftime('%m%d%H%M%S')
PARENT = "INSERT INTO system_menu(id,name,permission,type,parent_id) VALUES (73610,'考期日历','zsjos:exam-calendar:query',2,73600);"


def versions(db):
    return query(db, "SELECT version,checksum FROM zsjos_schema_version WHERE version='V320'; SELECT version,checksum FROM zsjos_module_schema_version WHERE module_code='core' AND version='V320';")[0]


def verify(db):
    actual = query(db, "SELECT parent_id,permission,type,HEX(name) FROM system_menu WHERE id=73615 AND deleted=0;")[0]
    assert actual == '73610\tzsjos:exam-calendar:export\t3\t' + '导出考期'.encode().hex().upper(), actual
    assert len(versions(db).splitlines()) == 2


def scoped():
    for case in ['initial', 'partial', 'parent_missing', 'identity_conflict', 'ledger_failure']:
        db = PREFIX + '_' + case
        query(None, f'CREATE DATABASE `{db}` CHARACTER SET utf8mb4;')
        setup = ''.join(table(name) for name in ['system_menu','zsjos_schema_version','zsjos_module_schema_version'])
        if case != 'parent_missing': setup += PARENT
        if case in ['partial', 'identity_conflict']:
            permission = 'zsjos:exam-calendar:export' if case == 'partial' else 'fixture:conflict'
            setup += f"INSERT INTO system_menu(id,name,permission,type,parent_id) VALUES(73615,'导出考期','{permission}',3,73610);"
        if case == 'ledger_failure':
            setup += "CREATE TRIGGER reject_export_ledger BEFORE INSERT ON zsjos_module_schema_version FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='injected failure';"
        query(db, setup)
        before = versions(db)
        _, error = query(db, MIGRATION, force=True)
        if case in ['parent_missing','identity_conflict','ledger_failure']:
            assert 'ERROR' in error and versions(db) == before, (case, error)
            if case == 'parent_missing': query(db, PARENT)
            elif case == 'identity_conflict': query(db, 'UPDATE system_menu SET id=99001 WHERE id=73615;')
            else: query(db, 'DROP TRIGGER reject_export_ledger;')
            query(db, MIGRATION)
        else: assert not error, error
        verify(db)
        before = versions(db)
        query(db, MIGRATION)
        verify(db)
        assert before == versions(db)
        query(db, "UPDATE system_menu SET name='自定义名称',status=1,sort=99 WHERE id=73615;")
        query(db, MIGRATION)
        assert query(db, 'SELECT status,sort,HEX(name) FROM system_menu WHERE id=73615;')[0] == '1\t99\t' + '自定义名称'.encode().hex().upper()
        print('PASS', case, 'initial/replay/recovery/UTF8/admin configuration')


def apply_dev():
    db = 'ruoyi-vue-pro'
    print('Preflight:', query(db, 'SELECT id,type,permission,deleted+0 FROM system_menu WHERE id IN (73610,73615);')[0])
    existing = query(db, 'SELECT COUNT(*) FROM system_menu WHERE id=73615;')[0]
    assert existing == '0', 'Unexpected existing target: review before applying'
    guards = 'SELECT * FROM system_role_menu ORDER BY id; SELECT * FROM system_menu WHERE id<>73615 ORDER BY id;'
    before = hashlib.sha256(query(db, guards)[0].encode()).hexdigest()
    query(db, MIGRATION)
    verify(db)
    query(db, MIGRATION)
    assert before == hashlib.sha256(query(db, guards)[0].encode()).hexdigest()
    print('PASS local additive metadata apply/replay; other menus and all grants preserved')


if __name__ == '__main__':
    scoped()
    if '--apply-dev' in sys.argv: apply_dev()
