# -*- coding: utf-8 -*-
"""V319 isolated MySQL first/replay/partial/failure verification. Retains test databases."""
import datetime
from test_positioning_single_source import query, ROOT

LOCAL = 'ruoyi-vue-pro'
SQL = ROOT / 'script/sql/mysql/migrations/V319__sales_order_void.sql'
TABLES = ['zsjos_order', 'zsjos_order_command', 'zsjos_gift_purchase',
          'zsjos_order_payment_allocation', 'system_menu', 'zsjos_schema_version', 'zsjos_module_schema_version']


def clone(suffix, menu=True):
    db = 'order_void_' + datetime.datetime.now().strftime('%Y%m%d%H%M%S%f') + '_' + suffix
    query(LOCAL, f'CREATE DATABASE `{db}` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;')
    for table in TABLES:
        query(db, f'CREATE TABLE `{table}` LIKE `{LOCAL}`.`{table}`;')
    if menu:
        query(db, f"INSERT INTO system_menu SELECT * FROM `{LOCAL}`.system_menu WHERE deleted=0 AND permission='zsjos:sales-order:query-management';")
    return db


def ledger(db):
    return query(db, "SELECT COUNT(*) FROM zsjos_schema_version WHERE version='V319'; SELECT COUNT(*) FROM zsjos_module_schema_version WHERE module_code='core' AND version='V319';").strip()


def verify(db):
    text = SQL.read_text(encoding='utf-8')
    query(db, text)
    assert ledger(db) == '1\n1'
    assert query(db, "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND ((table_name='zsjos_order' AND column_name IN ('void_reason','voided_at','voided_by_user_id')) OR (table_name IN ('zsjos_gift_purchase','zsjos_order_payment_allocation') AND column_name='voided_at')) AND is_nullable='YES';").strip() == '5'
    label = query(db, "SELECT HEX(name) FROM system_menu WHERE permission='zsjos:sales-order:void' AND deleted=0;").strip()
    assert label == '作废订单'.encode().hex().upper()
    before = query(db, 'SELECT * FROM system_menu ORDER BY id; SELECT * FROM zsjos_schema_version; SELECT * FROM zsjos_module_schema_version;')
    query(db, text)
    assert before == query(db, 'SELECT * FROM system_menu ORDER BY id; SELECT * FROM zsjos_schema_version; SELECT * FROM zsjos_module_schema_version;')
    # Imported orders can have no BPM round; independent void commands must persist without fake IDs.
    query(db, "INSERT INTO zsjos_order_command(idempotency_key,order_id,command_type,operator_user_id,request_fingerprint,tenant_id) VALUES ('void-test',1,'void',1,REPEAT('a',64),1);")
    assert query(db, "SELECT approval_round_id IS NULL AND process_instance_id IS NULL FROM zsjos_order_command WHERE idempotency_key='void-test';").strip() == '1'
    print('PASS', db, 'schema, nullable independent command, UTF-8 HEX, first/replay ledgers')


if __name__ == '__main__':
    text = SQL.read_text(encoding='utf-8')
    db = clone('initial')
    verify(db)
    partial = clone('partial')
    query(partial, 'ALTER TABLE zsjos_order ADD COLUMN void_reason varchar(1000) NULL;')
    verify(partial)
    failure = clone('failure')
    try:
        query(failure, text.replace(' START TRANSACTION;', " SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='injected failure after DDL';\n START TRANSACTION;", 1))
        raise AssertionError('Failure injection did not fail')
    except RuntimeError as error:
        assert 'injected failure' in str(error)
    assert ledger(failure) == '0\n0'
    verify(failure)
    missing = clone('prerequisite', False)
    try:
        query(missing, text)
        raise AssertionError('Missing prerequisite did not fail')
    except RuntimeError as error:
        assert 'requires order management' in str(error)
    assert ledger(missing) == '0\n0'
    print('PASS', missing, 'missing prerequisite cannot record success')
