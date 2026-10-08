# -*- coding: utf-8 -*-
"""Retained isolated MySQL V294 first/repeat/partial/failure/recovery fixtures. No shared DB writes."""
import datetime
from test_calendar_notifications import query, table, SQL

MIGRATION = (SQL / 'migrations/V294__feedback_approval_progress_urge.sql').read_text(encoding='utf-8')
PREFIX = 'feedback_urge_' + datetime.datetime.now().strftime('%Y%m%d%H%M%S')
TABLES = ['zsjos_feedback_round', 'system_menu', 'system_notify_template', 'system_notify_rule',
          'system_tenant', 'zsjos_schema_version', 'zsjos_module_schema_version']
PREREQ = "INSERT INTO zsjos_schema_version(version,description,checksum) VALUES('V155','fixture','fixture'); INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version) VALUES('core','V155','fixture','fixture','fixture');"

def versions(db):
    return query(db, "SELECT checksum FROM zsjos_schema_version WHERE version='V294'; SELECT checksum FROM zsjos_module_schema_version WHERE module_code='core' AND version='V294';")[0]

def signature(db):
    return query(db, "SELECT column_type,is_nullable,HEX(column_comment) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_feedback_round' AND column_name='last_urged_at'; SELECT permission,HEX(name),type,parent_id FROM system_menu WHERE permission='zsjos:feedback:requirement:urge'; SELECT code,HEX(name),scene_code FROM system_notify_template WHERE code='ZSJOS_FEEDBACK_APPROVAL_URGED'; SELECT tenant_id,scene_code,channel_code,status FROM system_notify_rule ORDER BY tenant_id;")[0]

for case in ['initial', 'partial', 'prerequisite', 'wrong_type', 'ledger_failure', 'existing_marker']:
    db = PREFIX + '_' + case
    query(None, f'CREATE DATABASE `{db}` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;')
    setup = ''.join(table(name) for name in TABLES)
    setup += "INSERT INTO system_menu(id,name,permission,type,parent_id) VALUES(79940,'需求与反馈','zsjos:feedback:query',2,6735);"
    setup += "INSERT INTO system_tenant(id,name,contact_name,status,package_id,expire_time,account_count) VALUES(1,'测试租户','测试',0,0,'2099-01-01',100),(2,'隔离租户','测试',0,0,'2099-01-01',100);"
    if case != 'prerequisite': setup += PREREQ
    if case in ['partial', 'wrong_type']:
        setup += 'ALTER TABLE zsjos_feedback_round ADD COLUMN last_urged_at ' + ('varchar(20)' if case == 'wrong_type' else 'datetime') + " NULL DEFAULT NULL COMMENT '本轮最近催办时间';"
    if case == 'ledger_failure':
        setup += "CREATE TRIGGER reject_urge_ledger BEFORE INSERT ON zsjos_module_schema_version FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='injected ledger failure';"
    if case == 'existing_marker':
        setup += "INSERT INTO zsjos_schema_version(version,description,checksum) VALUES('V294','fixture','preserve'); INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version) VALUES('core','V294','fixture','preserve','fixture');"
    query(db, setup)
    if case in ['prerequisite', 'wrong_type', 'ledger_failure']:
        _, error = query(db, MIGRATION, force=True)
        assert 'ERROR' in error and not versions(db), (case, error, versions(db))
        if case == 'prerequisite': query(db, PREREQ)
        if case == 'wrong_type': query(db, "ALTER TABLE zsjos_feedback_round MODIFY COLUMN last_urged_at datetime NULL DEFAULT NULL COMMENT '本轮最近催办时间';")
        if case == 'ledger_failure': query(db, 'DROP TRIGGER reject_urge_ledger;')
    query(db, MIGRATION)
    before = signature(db)
    query(db, MIGRATION)
    assert before == signature(db)
    assert len(versions(db).splitlines()) == 2
    if case == 'existing_marker': assert versions(db) == 'preserve\npreserve'
    assert '本轮最近催办时间'.encode().hex().upper() in before
    assert '催办需求审批'.encode().hex().upper() in before
    # Administrators may disable or customize delivery; repeat must preserve their choices.
    query(db, "UPDATE system_notify_rule SET status=1 WHERE tenant_id=2;")
    query(db, MIGRATION)
    assert query(db, 'SELECT status FROM system_notify_rule WHERE tenant_id=2;')[0] == '1'
    print('PASS', case, 'repeat/recovery/ledgers/UTF-8/admin-rule-preservation', flush=True)
print('Retained isolated databases:', PREFIX, flush=True)
