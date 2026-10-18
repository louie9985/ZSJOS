# -*- coding: utf-8 -*-
"""V317 schema regression on an isolated MySQL 8 container; no live data."""
import re
import zsjos_db as db

TABLE = 'zsjos_registration_case'


def verify(container):
    q = lambda sql: db.docker_mysql_query(container, 'SET NAMES utf8mb4;' + sql)
    db.docker_mysql_file(container, db.SQL_ROOT / '00-bootstrap-schema.sql')
    seed = (db.SQL_ROOT / '02-bootstrap-zsjos-seed.sql').read_text(encoding='utf-8')
    q(re.search(r'CREATE TABLE IF NOT EXISTS `zsjos_schema_version`.*?;', seed, re.S)[0])
    manifest = {'core': db.load_manifests()['core']}
    def check():
        drift = db.schema_drift(db.DockerTestClient(container), manifest)
        assert not drift, drift
        label = q("SELECT HEX(column_comment) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_registration_case' AND column_name='order_item_id'").strip()
        assert label == '履约订单商品；历史整单任务为空'.encode().hex().upper()
    def migrate():
        db.docker_mysql_file(container, db.SQL_ROOT / 'migrations/V317__registration_tasks_per_order_item.sql')
    def prerequisite():
        q("INSERT INTO zsjos_schema_version(version,description) VALUES('V316','fixture'); INSERT INTO zsjos_module_schema_version VALUES('core','V316','fixture',REPEAT('0',64),'fixture',NOW())")
    def old_shape():
        q('ALTER TABLE zsjos_registration_case DROP INDEX uk_registration_case_item, DROP INDEX idx_registration_case_order, DROP COLUMN order_item_id, ADD UNIQUE KEY uk_tenant_order(tenant_id,order_id)')
    def fail_without_success():
        try:
            migrate()
        except db.CommandError:
            assert q("SELECT COUNT(*) FROM zsjos_schema_version WHERE version='V317'; SELECT COUNT(*) FROM zsjos_module_schema_version WHERE version='V317'").split() == ['0', '0']
        else:
            raise AssertionError('Expected migration failure')
    check()
    old_shape()
    fail_without_success()
    prerequisite()
    migrate()
    check()
    before = q("SELECT * FROM zsjos_schema_version; SELECT * FROM zsjos_module_schema_version")
    migrate()
    assert before == q("SELECT * FROM zsjos_schema_version; SELECT * FROM zsjos_module_schema_version")
    # Only synthetic fixture ledgers are reset; never run against a business DB.
    q("DELETE FROM zsjos_schema_version WHERE version='V317'; DELETE FROM zsjos_module_schema_version WHERE version='V317'")
    old_shape()
    q("ALTER TABLE zsjos_registration_case ADD COLUMN order_item_id BIGINT NULL COMMENT '履约订单商品；历史整单任务为空' AFTER order_id")
    q("CREATE TRIGGER reject_v317 BEFORE INSERT ON zsjos_module_schema_version FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='fixture ledger failure'")
    fail_without_success()
    q('DROP TRIGGER reject_v317')
    migrate()
    check()
    assert q("SELECT HEX(description) FROM zsjos_schema_version WHERE version='V317'").strip() == '报名履约按订单商品拆分'.encode().hex().upper()
    print('PASS baseline, V316 upgrade/replay, prerequisite/second-ledger failure, partial DDL recovery, final schema and UTF-8 HEX')


if __name__ == '__main__':
    db.with_test_mysql(verify)
