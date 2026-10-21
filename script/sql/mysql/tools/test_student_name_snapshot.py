#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Retained MySQL upgrade fixtures; --apply-local adds schema/order snapshots only."""
import argparse
import uuid
from pathlib import Path
from test_core_baseline import Client, create_database

SQL = Path(__file__).resolve().parents[1] / 'migrations/V337__student_service_name_snapshot.sql'

def apply(c, success=True):
    r = c.execute(SQL.read_text(encoding='utf-8'), expect_success=success)
    if not success:
        assert r.returncode or b'ERROR' in r.stderr

def ledger(c):
    return c.query("SELECT HEX(version),HEX(checksum) FROM zsjos_schema_version WHERE version='V337' UNION ALL SELECT HEX(version),HEX(checksum) FROM zsjos_module_schema_version WHERE module_code='core' AND version='V337'")

def verify():
    c = create_database('sn_' + uuid.uuid4().hex[:8])
    for t in ('zsjos_service_relation', 'zsjos_schema_version', 'zsjos_module_schema_version'):
        c.query(f'CREATE TABLE `{t}` LIKE `ruoyi-vue-pro`.`{t}`')
    for col in ('student_name_snapshot', 'student_name_source'):
        if c.query(f"SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_service_relation' AND column_name='{col}'") == '1':
            c.query(f'ALTER TABLE zsjos_service_relation DROP COLUMN {col}')
    c.query('CREATE TABLE zsjos_order(id BIGINT PRIMARY KEY,tenant_id BIGINT,deleted BIT(1),student_name VARCHAR(100)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4')
    c.query("INSERT INTO zsjos_order VALUES(10,1,0,'测试姓名'),(11,1,0,'  '),(12,1,1,'已删除姓名')")
    c.query("INSERT INTO zsjos_service_relation(id,person_id,order_id,status,activated_at,version,tenant_id) VALUES(1,9,10,'active',NOW(),4,1),(2,9,NULL,'active',NOW(),4,1),(3,9,11,'active',NOW(),4,1),(4,9,12,'active',NOW(),4,1)")
    c.query("UPDATE zsjos_service_relation SET update_time='2020-01-01 00:00:00'")
    apply(c, False)
    assert not ledger(c)
    c.query("INSERT INTO zsjos_schema_version(version,description,checksum) VALUES('V336','fixture',REPEAT('a',64))")
    c.query("INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version) VALUES('core','V336','fixture',REPEAT('a',64),'fixture')")
    c.query("CREATE TRIGGER reject_ledger BEFORE INSERT ON zsjos_module_schema_version FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='fixture failure'")
    apply(c, False)
    assert not ledger(c)
    assert c.query('SELECT COUNT(*) FROM zsjos_service_relation WHERE student_name_snapshot IS NOT NULL') == '0'
    c.query('DROP TRIGGER reject_ledger')
    apply(c)
    assert len(ledger(c).splitlines()) == 2
    assert c.query("SELECT COUNT(*) FROM zsjos_service_relation WHERE update_time='2020-01-01 00:00:00' AND version=4") == '4'
    assert c.query('SELECT HEX(student_name_snapshot) FROM zsjos_service_relation WHERE id=1') == '测试姓名'.encode().hex().upper()
    assert c.query('SELECT COUNT(*) FROM zsjos_service_relation WHERE student_name_snapshot IS NOT NULL') == '1'
    c.query("UPDATE zsjos_order SET student_name='后续更名' WHERE id=10")
    before = c.query('SELECT id,HEX(student_name_snapshot),student_name_source,version FROM zsjos_service_relation ORDER BY id')
    apply(c)
    assert before == c.query('SELECT id,HEX(student_name_snapshot),student_name_source,version FROM zsjos_service_relation ORDER BY id')
    c.query("UPDATE zsjos_service_relation SET student_name_snapshot='人工回填',student_name_source='verified_repair' WHERE id=2")
    apply(c)
    assert c.query('SELECT HEX(student_name_snapshot) FROM zsjos_service_relation WHERE id=2') == '人工回填'.encode().hex().upper()
    c.query('ALTER TABLE zsjos_service_relation MODIFY student_name_source VARCHAR(16) NULL')
    before = ledger(c)
    apply(c, False)
    assert before == ledger(c)
    c.query("ALTER TABLE zsjos_service_relation MODIFY student_name_source VARCHAR(32) NULL COMMENT '学员姓名快照来源'")
    apply(c)
    from test_student_teaching_delivery import seed_fixture
    seed_fixture(c, 'ruoyi-vue-pro')
    assert c.query("SELECT COUNT(*) FROM zsjos_service_relation WHERE person_id=99 AND student_name_snapshot='fixture' AND student_name_source='independent_entry'") == '2'
    print('PASS prerequisite, DDL recovery, ledger rollback, order-only fill, replay, repair persistence, UTF-8 HEX:', c.database)

def local():
    c = Client('ruoyi-vue-pro')
    # Aggregate evidence only: never print names or personal identifiers.
    from order_history_repair import connect
    connection = connect()
    try:
        with connection.cursor() as q:
            q.execute('SELECT * FROM zsjos_service_relation ORDER BY id')
            before = q.fetchall()
        connection.commit()
        apply(c)
        with connection.cursor() as q:
            q.execute('SELECT * FROM zsjos_service_relation ORDER BY id')
            after = q.fetchall()
        assert len(before) == len(after)
        for original, migrated in zip(before, after):
            assert {k: v for k, v in migrated.items() if k not in ('student_name_snapshot', 'student_name_source')} == {k: v for k, v in original.items() if k not in ('student_name_snapshot', 'student_name_source')}
    finally:
        connection.close()
    independent = c.query("SELECT id,person_id,order_id,version,update_time,HEX(student_name_snapshot),student_name_source FROM zsjos_service_relation WHERE tenant_id=1 AND deleted=0 AND order_id IS NULL ORDER BY id")
    once = c.query('SELECT id,HEX(student_name_snapshot),student_name_source FROM zsjos_service_relation ORDER BY id')
    apply(c)
    assert once == c.query('SELECT id,HEX(student_name_snapshot),student_name_source FROM zsjos_service_relation ORDER BY id')
    assert independent == c.query("SELECT id,person_id,order_id,version,update_time,HEX(student_name_snapshot),student_name_source FROM zsjos_service_relation WHERE tenant_id=1 AND deleted=0 AND order_id IS NULL ORDER BY id")
    assert c.query("SELECT COUNT(*) FROM zsjos_service_relation sr JOIN zsjos_order o ON o.id=sr.order_id AND o.tenant_id=sr.tenant_id AND o.deleted=0 WHERE sr.tenant_id=1 AND sr.deleted=0 AND sr.student_name_source='order_migration' AND HEX(sr.student_name_snapshot)<>HEX(TRIM(o.student_name))") == '0'
    print('PASS local migration; order snapshots / deferred independent services:', c.query("SELECT SUM(student_name_source='order_migration'),SUM(order_id IS NULL AND student_name_snapshot IS NULL) FROM zsjos_service_relation WHERE tenant_id=1 AND deleted=0"))

if __name__ == '__main__':
    p = argparse.ArgumentParser(); p.add_argument('--apply-local', action='store_true'); p.add_argument('--fresh', action='store_true'); args = p.parse_args()
    verify()
    if args.fresh:
        from test_core_baseline import verify_fresh
        verify_fresh(create_database('sn_fresh_' + uuid.uuid4().hex[:8]))
    if args.apply_local: local()
