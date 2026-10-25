# -*- coding: utf-8 -*-
"""V340 controlled MySQL checks. Retain fixture DBs; optional scoped local synchronization."""
import argparse
import datetime
import json
from test_positioning_single_source import query, ROOT

SQL = (ROOT / 'script/sql/mysql/migrations/V340__gift_purchase_management.sql').read_text(encoding='utf-8')
LOCAL = 'ruoyi-vue-pro'
STAMP = datetime.datetime.now().strftime('%Y%m%d%H%M%S%f')


def fixture(suffix):
    db = 'gift340_' + STAMP + '_' + suffix
    query(LOCAL, f'CREATE DATABASE {db} CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;')
    for table in ['system_menu', 'zsjos_schema_version', 'zsjos_module_schema_version']:
        query(db, f'CREATE TABLE {table} LIKE `{LOCAL}`.{table};')
    # Use the actual pre-upgrade purchase DDL, rather than a clone after local synchronization.
    source = (ROOT / 'script/sql/mysql/migrations/V212__gift_purchase.sql').read_text(encoding='utf-8')
    query(db, source[source.index('CREATE TABLE'):source.index(';', source.index('CREATE TABLE')) + 1])
    query(db, "INSERT INTO zsjos_schema_version(version,description,checksum) VALUES('V339','fixture','fixture');"
              "INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version) VALUES('core','V339','fixture','fixture','fixture');"
              "INSERT INTO system_menu(name,permission,type,sort,parent_id,path,icon,component,status) VALUES('礼品采购','zsjos:gift-purchase:query',2,1,0,'gift-purchase','','',0);"
              "INSERT INTO zsjos_gift_purchase(tenant_id,order_id,order_no,student_name,gift_items_json,generated_at) VALUES(1,12,'FIXTURE-12','测试学员','[]','2026-01-01 12:00:00');")
    return db


def ledgers(db):
    return query(db, "SELECT version,checksum FROM zsjos_schema_version ORDER BY version;"
                     "SELECT module_code,version,checksum FROM zsjos_module_schema_version ORDER BY module_code,version;")


def verify(db):
    query(db, SQL)
    before = ledgers(db)
    query(db, SQL)
    assert before == ledgers(db)
    assert query(db, "SELECT purchased+0,order_no,generated_at FROM zsjos_gift_purchase WHERE order_id=12;").strip() == '0\tFIXTURE-12\t2026-01-01 12:00:00'
    assert query(db, "SELECT COUNT(*) FROM system_menu WHERE permission IN ('zsjos:gift-purchase:create','zsjos:gift-purchase:update','zsjos:gift-purchase:delete') AND deleted=0;").strip() == '3'
    assert query(db, "SELECT HEX(name) FROM system_menu WHERE permission='zsjos:gift-purchase:create';").strip() == '新增采购'.encode().hex().upper()
    assert query(db, "SELECT COUNT(*) FROM zsjos_schema_version WHERE version='V340'; SELECT COUNT(*) FROM zsjos_module_schema_version WHERE module_code='core' AND version='V340';").strip() == '1\n1'
    print('PASS initial/replay/defaults/preserve timestamp/permissions/UTF-8/ledgers', db)


def fail(db):
    before = ledgers(db)
    try:
        query(db, SQL)
    except RuntimeError:
        pass
    else:
        raise AssertionError('Expected migration failure')
    assert before == ledgers(db)


def local_signature():
    return query(LOCAL, "SELECT COUNT(*),SUM(CRC32(CONCAT_WS('|',id,tenant_id,order_id,order_no,student_name,student_mobile,student_wechat_id,CAST(gift_items_json AS CHAR),shipping_address,generated_at,deleted+0))) FROM zsjos_gift_purchase; SELECT COUNT(*) FROM system_role_menu;")


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--sync-local', action='store_true')
    args = parser.parse_args()
    initial = fixture('initial'); verify(initial)
    # Independent records coexist, while the existing unique tenant/order constraint remains.
    query(initial, "INSERT INTO zsjos_gift_purchase(tenant_id,student_name,gift_items_json,generated_at) VALUES(1,'手工甲','[]',NOW()),(1,'手工乙','[]',NOW());"
                   "INSERT INTO zsjos_gift_purchase_operation(tenant_id,purchase_id,action,operator_user_id,operator_name,occurred_at) VALUES(1,2,'CREATE',44,'操作快照',NOW());"
                   "UPDATE zsjos_gift_purchase SET deleted=1 WHERE tenant_id=1 AND id=2;")
    assert query(initial, "SELECT COUNT(*) FROM zsjos_gift_purchase_operation WHERE tenant_id=1 AND purchase_id=2;").strip() == '1'
    assert query(initial, "SELECT HEX(operator_name) FROM zsjos_gift_purchase_operation;").strip() == '操作快照'.encode().hex().upper()
    partial = fixture('partial')
    query(partial, "ALTER TABLE zsjos_gift_purchase ADD COLUMN purchased bit(1) NOT NULL DEFAULT b'0' COMMENT '是否已采购';")
    verify(partial)
    missing = fixture('prereq')
    query(missing, "UPDATE zsjos_schema_version SET version='FIXTURE339' WHERE version='V339';")
    fail(missing)
    assert query(missing, "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_gift_purchase' AND column_name='purchased';").strip() == '0'
    failed = fixture('ledger_failure')
    query(failed, "CREATE TRIGGER reject_v340 BEFORE INSERT ON zsjos_module_schema_version FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='fixture reject';")
    fail(failed)
    assert query(failed, "SELECT COUNT(*) FROM system_menu WHERE permission='zsjos:gift-purchase:create';").strip() == '0'
    query(failed, 'DROP TRIGGER reject_v340;'); verify(failed)
    invalid = fixture('invalid_structure')
    query(invalid, 'ALTER TABLE zsjos_gift_purchase ADD COLUMN purchased varchar(10);')
    fail(invalid)
    query(invalid, "UPDATE zsjos_gift_purchase SET purchased='0'; ALTER TABLE zsjos_gift_purchase MODIFY COLUMN purchased bit(1) NOT NULL DEFAULT b'0';")
    verify(invalid)
    print('PASS manual linkage/audit preservation/prerequisite and ledger failures/partial recovery')
    if args.sync_local:
        before = local_signature()
        tables = ['zsjos_gift_purchase', 'system_menu', 'zsjos_schema_version', 'zsjos_module_schema_version']
        backup = ROOT / 'backups/mysql' / ('gift-purchase-V340-' + STAMP + '.json')
        backup.parent.mkdir(parents=True, exist_ok=True)
        backup.write_text(json.dumps({
            'ddl': {table: query(LOCAL, 'SHOW CREATE TABLE ' + table + ';') for table in tables},
            'menus': query(LOCAL, "SELECT * FROM system_menu WHERE permission LIKE 'zsjos:gift-purchase:%';"),
            'ledgers': ledgers(LOCAL), 'existing_business_signature': before
        }, ensure_ascii=False), encoding='utf-8')
        query(LOCAL, SQL); query(LOCAL, SQL)
        assert before == local_signature()
        for table in ['zsjos_gift_purchase', 'zsjos_gift_purchase_operation']:
            assert query(LOCAL, 'SHOW COLUMNS FROM ' + table + ';') == query(partial, 'SHOW COLUMNS FROM ' + table + ';')
        assert query(LOCAL, "SELECT HEX(name) FROM system_menu WHERE permission='zsjos:gift-purchase:create';").strip() == '新增采购'.encode().hex().upper()
        print('PASS scoped local synchronization/schema comparison/business signatures/grant counts/UTF-8; metadata backup', backup)
