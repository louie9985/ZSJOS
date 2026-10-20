# -*- coding: utf-8 -*-
"""V333 controlled MySQL 8 verification. Retains isolated schemas; no real role changes."""
import datetime
from pathlib import Path
from test_role_menu_replay import Client

ROOT = Path(__file__).resolve().parents[4]
SQL = (ROOT / 'script/sql/mysql/migrations/V333__material_draft_delete_permission.sql').read_text(encoding='utf-8')
prefix = 'zsjos_draft_' + datetime.datetime.now().strftime('%Y%m%d%H%M%S')
admin = Client('yudao-mysql', 'mysql')

def fixture(suffix, parent=True):
    name = prefix + '_' + suffix
    admin.query('CREATE DATABASE ' + name + ' CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci')
    client = Client('yudao-mysql', name)
    for table in ['system_menu', 'system_role_menu', 'zsjos_schema_version', 'zsjos_module_schema_version']:
        client.query('CREATE TABLE ' + table + ' LIKE `ruoyi-vue-pro`.' + table)
    if parent:
        client.query("INSERT INTO system_menu(id,name,permission,type,parent_id) VALUES(80011,'素材库','zsjos:material:query',2,0)")
    client.query('INSERT INTO system_role_menu(id,role_id,menu_id,tenant_id,deleted) VALUES(1,1,80011,1,0)')
    return client

def verify(c):
    assert c.query("SELECT COUNT(*) FROM system_menu WHERE permission='zsjos:material:delete-draft' AND parent_id=80011 AND type=3 AND deleted=0") == '1'
    assert c.query("SELECT HEX(name) FROM system_menu WHERE permission='zsjos:material:delete-draft'") == '删除素材草稿'.encode().hex().upper()
    assert c.query("SELECT COUNT(*) FROM zsjos_schema_version WHERE version='V333'") == '1'
    assert c.query("SELECT COUNT(*) FROM zsjos_module_schema_version WHERE module_code='core' AND version='V333'") == '1'
    assert c.query('SELECT COUNT(*),SUM(id),SUM(menu_id) FROM system_role_menu') == '1\t1\t80011'

def fails(c):
    try:
        c.query(SQL)
    except RuntimeError:
        pass
    else:
        raise AssertionError('expected failure')
    assert c.query("SELECT COUNT(*) FROM zsjos_schema_version WHERE version='V333'") == '0'
    assert c.query("SELECT COUNT(*) FROM zsjos_module_schema_version WHERE version='V333'") == '0'

fresh = fixture('fresh'); fresh.query(SQL); fresh.query(SQL); verify(fresh)
fresh.query("UPDATE system_menu SET sort=123,status=1 WHERE permission='zsjos:material:delete-draft'")
fresh.query(SQL)
assert fresh.query("SELECT sort,status FROM system_menu WHERE permission='zsjos:material:delete-draft'") == '123\t1'
partial = fixture('partial')
partial.query("INSERT INTO system_menu(name,permission,type,parent_id) VALUES('删除素材草稿','zsjos:material:delete-draft',3,80011)")
partial.query(SQL); verify(partial)
missing = fixture('missing', False); fails(missing)
missing.query("INSERT INTO system_menu(id,name,permission,type,parent_id) VALUES(80011,'素材库','zsjos:material:query',2,0)")
missing.query(SQL); verify(missing)
conflict = fixture('conflict')
conflict.query("INSERT INTO system_menu(name,permission,type,parent_id) VALUES('保留冲突','zsjos:material:delete-draft',3,0)")
fails(conflict)
conflict.query("UPDATE system_menu SET name='删除素材草稿',parent_id=80011 WHERE permission='zsjos:material:delete-draft'")
conflict.query(SQL); verify(conflict)
ledger = fixture('ledger')
ledger.query("CREATE TRIGGER reject_v333 BEFORE INSERT ON zsjos_module_schema_version FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='test failure'")
fails(ledger)
assert ledger.query("SELECT COUNT(*) FROM system_menu WHERE permission='zsjos:material:delete-draft'") == '0'
ledger.query('DROP TRIGGER reject_v333'); ledger.query(SQL); verify(ledger)
print('PASS: MySQL initial/replay/partial/prerequisite/conflict/ledger rollback/UTF-8 HEX/grants preserved; retained', prefix)
