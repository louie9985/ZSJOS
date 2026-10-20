#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Retained MySQL 8 fixtures for V325; optionally apply the verified additive migration locally."""
import argparse
import datetime
import json
from pathlib import Path
from test_role_menu_replay import Client

ROOT = Path(__file__).resolve().parents[4]
SQL = ROOT / 'script/sql/mysql/migrations/V325__personal_navigation_preference.sql'

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apply-local', action='store_true')
    args = parser.parse_args()
    admin = Client('yudao-mysql', '')
    live = Client('yudao-mysql', 'ruoyi-vue-pro')
    prefix = 'nav_' + datetime.datetime.now().strftime('%m%d%H%M%S')
    sql = SQL.read_text(encoding='utf-8')
    def fixture(suffix, prerequisite=True):
        name = prefix + '_' + suffix
        admin.query('CREATE DATABASE ' + name + ' CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci')
        c = Client('yudao-mysql', name)
        for table in ['zsjos_schema_version', 'zsjos_module_schema_version', 'system_menu', 'system_role_menu']:
            c.query('CREATE TABLE ' + table + ' LIKE \x60ruoyi-vue-pro\x60.' + table)
        c.query("INSERT INTO system_menu(id,name,permission,type,parent_id,path) VALUES (1,'System','',1,0,'system'),(2,'Menus','system:menu:query',3,1,'')")
        if prerequisite:
            c.query("INSERT INTO zsjos_schema_version(version,description,checksum) VALUES ('V324','fixture','fixture'); INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version) VALUES ('core','V324','fixture','fixture','fixture')")
        return c
    def failure(c):
        try: c.query(sql)
        except RuntimeError: pass
        else: raise AssertionError('expected migration failure')
        assert c.query("SELECT COUNT(*) FROM zsjos_schema_version WHERE version='V325'") == '0'
        assert c.query("SELECT COUNT(*) FROM zsjos_module_schema_version WHERE version='V325'") == '0'
    def verify(c):
        assert c.query("SELECT COUNT(*) FROM zsjos_schema_version WHERE version='V325'") == '1'
        assert c.query("SELECT COUNT(*) FROM zsjos_module_schema_version WHERE module_code='core' AND version='V325'") == '1'
        assert c.query("SELECT COUNT(*) FROM system_menu WHERE permission LIKE 'zsjos:navigation-preference:%'") == '2'
        assert c.query('SELECT COUNT(*) FROM system_role_menu') == '0'
        expected = '个人导航设置'.encode().hex().upper()
        assert c.query("SELECT HEX(name) FROM system_menu WHERE permission='zsjos:navigation-preference:query'") == expected
    fresh = fixture('fresh'); fresh.query(sql); verify(fresh)
    fresh.query("INSERT INTO zsjos_navigation_preference(tenant_id,user_id,client,preference_json) VALUES (1,7,'admin','{}'),(1,7,'workbench','{}'),(1,8,'admin','{}'),(2,7,'admin','{}')")
    fresh.query(sql); verify(fresh)
    assert fresh.query('SELECT COUNT(*) FROM zsjos_navigation_preference') == '4'
    # A duplicate save updates only the authenticated account/client key.
    fresh.query("INSERT INTO zsjos_navigation_preference(tenant_id,user_id,client,preference_json) VALUES (1,7,'admin','{\"groups\":[]}') ON DUPLICATE KEY UPDATE preference_json=VALUES(preference_json)")
    assert fresh.query("SELECT COUNT(*) FROM zsjos_navigation_preference WHERE preference_json='{}'") == '3'
    missing = fixture('missing', False); failure(missing)
    partial = fixture('partial')
    partial.query('CREATE TABLE zsjos_navigation_preference LIKE ' + fresh.database + '.zsjos_navigation_preference')
    partial.query(sql); verify(partial)
    broken = fixture('broken')
    broken.query('CREATE TABLE zsjos_navigation_preference LIKE ' + fresh.database + '.zsjos_navigation_preference')
    broken.query('ALTER TABLE zsjos_navigation_preference DROP INDEX uk_user_client')
    failure(broken)
    broken.query('ALTER TABLE zsjos_navigation_preference ADD UNIQUE KEY uk_user_client(tenant_id,user_id,client,deleted)')
    broken.query(sql); verify(broken)
    ledger = fixture('ledger')
    ledger.query("CREATE TRIGGER reject_version BEFORE INSERT ON zsjos_module_schema_version FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='fixture ledger failure'")
    failure(ledger)
    ledger.query('DROP TRIGGER reject_version')
    ledger.query(sql); verify(ledger)
    if args.apply_local:
        # Read-only backup before additive local schema/metadata application. No grants are changed.
        before = {table: live.query('SHOW CREATE TABLE ' + table) for table in ['system_menu','system_role_menu','zsjos_schema_version','zsjos_module_schema_version']}
        before['grants'] = live.query('SELECT COUNT(*),COALESCE(SUM(id),0) FROM system_role_menu')
        before['permissions'] = live.query("SELECT id,permission,HEX(name) FROM system_menu WHERE permission LIKE 'zsjos:navigation-preference:%'")
        backup = ROOT / 'backend/target/personal-navigation-verification'
        backup.mkdir(parents=True, exist_ok=True)
        (backup / (prefix + '-before.json')).write_text(json.dumps(before, ensure_ascii=False, indent=2),encoding='utf-8')
        live.query(sql); live.query(sql)
        assert live.query('SELECT COUNT(*),COALESCE(SUM(id),0) FROM system_role_menu') == before['grants']
        assert live.query("SELECT HEX(name) FROM system_menu WHERE permission='zsjos:navigation-preference:query'") == '个人导航设置'.encode().hex().upper()
        columns = "SELECT column_name,column_type,is_nullable FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_navigation_preference' ORDER BY ordinal_position"
        assert live.query(columns) == fresh.query(columns)
        assert live.query("SELECT COUNT(*) FROM zsjos_schema_version WHERE version='V325'") == '1'
        assert live.query("SELECT COUNT(*) FROM zsjos_module_schema_version WHERE module_code='core' AND version='V325'") == '1'
        print('Local additive application/replay/schema comparison/Chinese HEX/grants unchanged: PASS')
    print('PASS: initial/repeat/account-client-tenant isolation/prerequisite/partial/invalid index/ledger failure/recovery; retained prefix',prefix)

if __name__ == '__main__':
    main()
