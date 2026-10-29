# -*- coding: utf-8 -*-
"""Retained MySQL fixtures for V355. No application database writes."""
import datetime
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from test_calendar_notifications import query, table, SQL

migration = (SQL / 'migrations/V355__person_historical_leads.sql').read_text(encoding='utf-8')
prefix = 'identity355_' + datetime.datetime.now().strftime('%Y%m%d%H%M%S')
prerequisite = "INSERT INTO zsjos_schema_version(version,description,checksum) VALUES('V354','fixture','fixture'); INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version) VALUES('core','V354','fixture','fixture','fixture');"

for case in ('initial', 'partial_ddl', 'missing_prerequisite', 'ledger_failure'):
    db = prefix + '_' + case
    query(None, f'CREATE DATABASE `{db}` CHARACTER SET utf8mb4;')
    query(db, table('zsjos_schema_version') + table('zsjos_module_schema_version') + "CREATE TABLE zsjos_lead(id BIGINT PRIMARY KEY,tenant_id BIGINT,person_id BIGINT,deleted BIT DEFAULT b'0',active_person_id BIGINT GENERATED ALWAYS AS (CASE WHEN deleted=0 THEN person_id ELSE NULL END) STORED, UNIQUE KEY uk_tenant_active_person(tenant_id,active_person_id)); CREATE TABLE zsjos_order(id BIGINT PRIMARY KEY,person_id BIGINT); CREATE TABLE zsjos_opportunity(id BIGINT PRIMARY KEY,tenant_id BIGINT,active_person_id BIGINT,UNIQUE KEY uk_tenant_active_person(tenant_id,active_person_id)); INSERT INTO zsjos_lead(id,tenant_id,person_id) VALUES(1,1,10);")
    if case != 'missing_prerequisite':
        query(db, prerequisite)
    if case == 'partial_ddl':
        query(db, 'ALTER TABLE zsjos_lead DROP INDEX uk_tenant_active_person;')
    if case == 'ledger_failure':
        query(db, "CREATE TRIGGER fail_ledger BEFORE INSERT ON zsjos_module_schema_version FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='injected failure';")
    if case in ('missing_prerequisite', 'ledger_failure'):
        _, error = query(db, migration, force=True)
        assert 'ERROR' in error
        assert query(db, "SELECT COUNT(*) FROM zsjos_schema_version WHERE version='V355';")[0] == '0'
        assert query(db, "SELECT COUNT(*) FROM zsjos_module_schema_version WHERE version='V355';")[0] == '0'
        query(db, prerequisite if case == 'missing_prerequisite' else 'DROP TRIGGER fail_ledger;')
    query(db, migration)
    query(db, migration)
    assert query(db, "SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='zsjos_lead' AND index_name='uk_tenant_active_person';")[0] == '0'
    query(db, 'INSERT INTO zsjos_lead(id,tenant_id,person_id) VALUES(2,1,10); INSERT INTO zsjos_opportunity VALUES(1,1,10),(2,1,10);')
    assert query(db, 'SELECT COUNT(*) FROM zsjos_lead WHERE tenant_id=1 AND person_id=10;')[0] == '2'
    assert query(db, "SELECT HEX(description) FROM zsjos_schema_version WHERE version='V355';")[0] == '客户历史多客资'.encode().hex().upper()
    assert query(db, "SELECT COUNT(*) FROM zsjos_module_schema_version WHERE module_code='core' AND version='V355';")[0] == '1'
    print(case, 'PASS', db)
