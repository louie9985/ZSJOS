# -*- coding: utf-8 -*-
"""V317 scoped MySQL verification. Retains databases and never deletes business rows.

Default: copy only affected local tables into isolated databases; verify migration,
replay, partially applied DDL, empty initialization, and failure-controlled ledgers.
--sync-local: after the same checks, preserve complete scoped before-images and apply
the non-destructive migration to the local Docker database. Backend must be stopped.
"""
import argparse
import datetime
import hashlib
import json
from pathlib import Path
from test_positioning_single_source import query, ROOT

LOCAL = 'ruoyi-vue-pro'
SQL = ROOT / 'script/sql/mysql/migrations/V317__registration_tasks_per_order_item.sql'
MUTABLE = ['zsjos_registration_case', 'zsjos_registration_case_checklist_item',
           'zsjos_registration_case_route', 'zsjos_registration_item_attachment',
           'zsjos_registration_class_assignment']
IMMUTABLE = ['zsjos_order', 'zsjos_order_item', 'zsjos_service_relation']
LEDGERS = ['zsjos_schema_version', 'zsjos_module_schema_version']
TABLES = MUTABLE + IMMUTABLE + LEDGERS


def scalar(db, sql):
    return int(query(db, sql).strip())


def rows(db, table, where='1=1'):
    order = 'module_code,version' if table == LEDGERS[1] else 'version' if table == LEDGERS[0] else 'id'
    return query(db, f'SELECT * FROM `{table}` WHERE {where} ORDER BY {order};')


def signature(db, tables):
    return hashlib.sha256(''.join(rows(db, t) for t in tables).encode()).hexdigest()


def clone(suffix, copy=True, prerequisites=True):
    db = 'registration_items_' + datetime.datetime.now().strftime('%Y%m%d%H%M%S%f') + '_' + suffix
    query(LOCAL, f'CREATE DATABASE `{db}` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;')
    for table in TABLES:
        query(db, f'CREATE TABLE `{table}` LIKE `{LOCAL}`.`{table}`;')
        if copy or (prerequisites and table in LEDGERS):
            columns = query(LOCAL, f"SELECT column_name FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='{table}' AND extra NOT LIKE '%VIRTUAL GENERATED%' AND extra NOT LIKE '%STORED GENERATED%' ORDER BY ordinal_position;").splitlines()
            names = ','.join('`'+c+'`' for c in columns)
            query(db, f'INSERT INTO `{table}` ({names}) SELECT {names} FROM `{LOCAL}`.`{table}`;')
    return db


def verify(db):
    before = signature(db, IMMUTABLE)
    historical_columns = ','.join('`'+c+'`' for c in query(db, "SELECT column_name FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_registration_case' AND column_name<>'order_item_id' ORDER BY ordinal_position;").splitlines())
    historical_sql = f"SELECT {historical_columns} FROM zsjos_registration_case WHERE status IN ('completed','cancelled') ORDER BY id;"
    historical = query(db, historical_sql)
    expected = scalar(db, "SELECT COUNT(*) FROM zsjos_registration_case c JOIN zsjos_order_item i ON i.order_id=c.order_id AND i.tenant_id=c.tenant_id AND i.deleted=0 AND (c.order_item_id IS NULL OR c.order_item_id=i.id) WHERE c.tenant_id=1 AND c.deleted=0 AND c.status IN ('pending','processing');") if scalar(db, "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_registration_case' AND column_name='order_item_id';") else scalar(db, "SELECT COUNT(*) FROM zsjos_registration_case c JOIN zsjos_order_item i ON i.order_id=c.order_id AND i.tenant_id=c.tenant_id AND i.deleted=0 WHERE c.tenant_id=1 AND c.deleted=0 AND c.status IN ('pending','processing');")
    original_attachments = query(db, 'SELECT id,HEX(original_name),infra_file_id,checklist_item_id FROM zsjos_registration_item_attachment ORDER BY id;')
    old_attachment_count = scalar(db, 'SELECT COUNT(*) FROM zsjos_registration_item_attachment;')
    query(db, SQL.read_text(encoding='utf-8'))
    assert before == signature(db, IMMUTABLE), 'Order or existing delivery changed'
    assert historical == query(db, historical_sql), 'Terminal cases changed'
    assert scalar(db, "SELECT COUNT(*) FROM zsjos_registration_case WHERE tenant_id=1 AND deleted=0 AND status IN ('pending','processing') AND order_item_id IS NOT NULL;") == expected
    assert scalar(db, "SELECT COUNT(*) FROM zsjos_registration_class_assignment a JOIN zsjos_registration_case c ON c.id=a.registration_case_id WHERE a.deleted=0 AND c.deleted=0 AND c.order_item_id IS NOT NULL AND a.order_item_id<>c.order_item_id;") == 0
    assert scalar(db, 'SELECT COUNT(*) FROM zsjos_registration_item_attachment;') >= old_attachment_count
    after_attachments = set(query(db, 'SELECT id,HEX(original_name),infra_file_id,checklist_item_id FROM zsjos_registration_item_attachment ORDER BY id;').splitlines())
    assert set(original_attachments.splitlines()) <= after_attachments
    once = signature(db, TABLES)
    query(db, SQL.read_text(encoding='utf-8'))
    assert once == signature(db, TABLES), 'Replay changed data or ledger'
    assert query(db, "SELECT HEX(description) FROM zsjos_schema_version WHERE version='V317';").strip() == '报名履约按订单商品拆分'.encode().hex().upper()
    print('PASS first/replay, terminal/order/service preservation, item/class scope and UTF-8:', db)


def expect_failure(db):
    before = signature(db, LEDGERS)
    try:
        query(db, SQL.read_text(encoding='utf-8'))
    except RuntimeError:
        assert before == signature(db, LEDGERS), 'Failed migration advanced ledgers'
    else:
        raise AssertionError('Expected migration failure')


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--sync-local', action='store_true')
    args = parser.parse_args()
    verify(clone('upgrade'))
    partial = clone('partial')
    if not scalar(partial, "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_registration_case' AND column_name='order_item_id';"):
        query(partial, 'ALTER TABLE zsjos_registration_case ADD order_item_id BIGINT NULL;')
    verify(partial)
    verify(clone('empty', copy=False))
    failure = clone('prerequisite', copy=False, prerequisites=False)
    expect_failure(failure)
    for table in LEDGERS:
        query(failure, f'INSERT INTO `{table}` SELECT * FROM `{LOCAL}`.`{table}`;')
    verify(failure)
    data_failure = clone('data_failure', copy=False)
    query(data_failure, "INSERT INTO zsjos_registration_case(order_id,status,checklist_version_id,assignment_mode,registration_approved_at,tenant_id) VALUES(999999999,'pending',1,'class_per_item',NOW(),1);")
    expect_failure(data_failure)
    # A stale success marker must not suppress prerequisite/data checks or be advanced on failure.
    for table in LEDGERS:
        query(data_failure, f"INSERT INTO `{table}` SELECT * FROM `{partial}`.`{table}` WHERE version='V317';")
    expect_failure(data_failure)
    print('PASS missing prerequisites/data never register or advance success; prerequisite recovery verified')
    if args.sync_local:
        backup = ROOT / 'backups/mysql' / ('registration-items-' + datetime.datetime.now().strftime('%Y%m%d%H%M%S') + '.json')
        backup.parent.mkdir(parents=True, exist_ok=True)
        backup.write_text(json.dumps({t: rows(LOCAL, t) for t in MUTABLE + LEDGERS}, ensure_ascii=False), encoding='utf-8')
        verify(LOCAL)
        print('PASS local non-destructive synchronization. Scoped before-images:', backup)
