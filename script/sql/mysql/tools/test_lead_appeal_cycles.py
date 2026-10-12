# -*- coding: utf-8 -*-
"""Retained MySQL 8 fixtures for the scoped V311 upgrade; never changes the development schema."""
import datetime
import re
from test_calendar_notifications import query, table, SQL

MIGRATION = (SQL / 'migrations/V311__lead_appeal_cycles.sql').read_text(encoding='utf-8')
PREFIX = 'appeal_cycles_' + datetime.datetime.now().strftime('%Y%m%d%H%M%S')
PREREQ = "INSERT INTO zsjos_schema_version(version,description,checksum) VALUES('V026','fixture','fixture'); INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version) VALUES('core','V026','fixture','fixture','fixture');"


def setup(db, prerequisite=True, partial=False):
    query(None, f'CREATE DATABASE `{db}` CHARACTER SET utf8mb4;')
    appeal = re.sub(r'^.*`invalid_event_id`.*\n', '', table('zsjos_lead_appeal'), flags=re.M)
    # The line removed above may include the new index as well.
    appeal = appeal.replace('  UNIQUE KEY `uk_tenant_appeal_submit_key`',
                           '  UNIQUE KEY `uk_tenant_lead_round` (`tenant_id`,`lead_id`,`round_no`),\n  UNIQUE KEY `uk_tenant_appeal_submit_key`')
    query(db, appeal + table('zsjos_business_event') + table('zsjos_schema_version') + table('zsjos_module_schema_version'))
    if prerequisite: query(db, PREREQ)
    if partial: query(db, 'ALTER TABLE zsjos_lead_appeal ADD invalid_event_id bigint NOT NULL DEFAULT 0;')
    query(db, """INSERT INTO zsjos_lead_appeal(id,tenant_id,lead_id,round_no,review_stage,status,reason,submitted_at,submission_idempotency_key)
        VALUES(1,1,100,1,'sales_manager','overturned','原申诉','2026-10-01','fixture-1'),
              (2,1,200,1,'sales_manager','upheld','无事件旧记录','2026-10-01','fixture-2');
        INSERT INTO zsjos_business_event(id,tenant_id,aggregate_type,aggregate_id,event_type,from_status,to_status,occurred_at,idempotency_key,related_object_refs)
        VALUES(10,1,'lead',100,'lead_qualified_invalid','valid','invalid','2026-10-01','event-10',NULL),
              (11,1,'lead',100,'lead_appeal_submitted','invalid','sales_manager_reviewing','2026-10-01','event-11','{"appealId":1}'),
              (12,1,'lead',100,'lead_qualified_invalid','valid','invalid','2026-10-01','event-12',NULL),
              (20,2,'lead',100,'lead_qualified_invalid','valid','invalid','2026-10-01','event-20',NULL);""")


def markers(db):
    return query(db, "SELECT COUNT(*) FROM zsjos_schema_version WHERE version='V311'; SELECT COUNT(*) FROM zsjos_module_schema_version WHERE version='V311';")[0]


def verify(db):
    assert query(db, 'SELECT id,invalid_event_id FROM zsjos_lead_appeal ORDER BY id;')[0] == '1\t10\n2\t0'
    assert markers(db) == '1\n1'
    assert query(db, "SELECT HEX(reason) FROM zsjos_lead_appeal WHERE id=1;")[0] == '原申诉'.encode().hex().upper()
    assert query(db, "SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='zsjos_lead_appeal' AND index_name='uk_tenant_lead_cycle_round';")[0] == 'tenant_id,lead_id,invalid_event_id,round_no'
    assert query(db, "SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='zsjos_lead_appeal' AND index_name='uk_tenant_lead_round';")[0] == '0'


def run():
    for case in ('initial', 'partial', 'missing_prerequisite', 'ledger_failure', 'wrong_index', 'existing_marker'):
        db = PREFIX + '_' + case
        setup(db, case != 'missing_prerequisite', case in ('partial', 'wrong_index'))
        if case == 'existing_marker': query(db, "INSERT INTO zsjos_schema_version(version,description,checksum) VALUES('V311','fixture','preserve'); INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version) VALUES('core','V311','fixture','preserve','fixture');")
        if case == 'ledger_failure': query(db, "CREATE TRIGGER reject_cycle_version BEFORE INSERT ON zsjos_module_schema_version FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='fixture failure';")
        if case == 'wrong_index': query(db, 'ALTER TABLE zsjos_lead_appeal ADD INDEX uk_tenant_lead_cycle_round(tenant_id);')
        if case in ('missing_prerequisite', 'ledger_failure', 'wrong_index'):
            _, error = query(db, MIGRATION, force=True)
            assert 'ERROR' in error and markers(db) == '0\n0', (case, error)
            if case == 'missing_prerequisite': query(db, PREREQ)
            if case == 'ledger_failure': query(db, 'DROP TRIGGER reject_cycle_version;')
            if case == 'wrong_index': query(db, 'ALTER TABLE zsjos_lead_appeal DROP INDEX uk_tenant_lead_cycle_round;')
        query(db, MIGRATION)
        verify(db)
        if case == 'existing_marker': assert query(db, "SELECT checksum FROM zsjos_schema_version WHERE version='V311';")[0] == 'preserve'
        query(db, MIGRATION)
        verify(db)
        query(db, "INSERT INTO zsjos_lead_appeal(tenant_id,lead_id,invalid_event_id,round_no,review_stage,status,reason,submitted_at,submission_idempotency_key) VALUES(1,100,12,1,'sales_manager','sales_manager_reviewing','新判定首轮','2026-10-02','new-cycle');")
        _, duplicate = query(db, "INSERT INTO zsjos_lead_appeal(tenant_id,lead_id,invalid_event_id,round_no,review_stage,status,reason,submitted_at,submission_idempotency_key) VALUES(1,100,12,1,'sales_manager','sales_manager_reviewing','重复','2026-10-02','duplicate-cycle');", force=True)
        assert 'Duplicate' in duplicate
        print(case + ': PASS')


if __name__ == '__main__': run()
