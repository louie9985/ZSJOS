# -*- coding: utf-8 -*-
"""Retained MySQL 8 verification databases. --sync-local applies only V347 after a scoped backup."""
import argparse
import datetime
import hashlib
import json
import re
from pathlib import Path
from test_positioning_single_source import query, ROOT
from zsjos_db import desired_schema

LOCAL = 'ruoyi-vue-pro'
SQL = ROOT / 'script/sql/mysql/migrations/V347__zsjkqt.sql'
STAMP = datetime.datetime.now().strftime('%Y%m%d%H%M%S%f')
TABLES = ['zsjkqt_draft', 'zsjkqt_config']

def migration_sql():
    data = SQL.read_bytes()
    digest = hashlib.sha256(data).hexdigest()
    return f"SET @zsjkqt_file_checksum='{digest}';\n" + data.decode('utf-8')

def scalar(db, sql):
    return int(query(db, sql).strip())

def clone(label, parent=True):
    db = 'zsjkqt_' + STAMP + '_' + label
    query(LOCAL, f'CREATE DATABASE `{db}` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;')
    for table in ['system_menu', 'zsjos_schema_version', 'zsjos_module_schema_version']:
        query(db, f'CREATE TABLE `{table}` LIKE `{LOCAL}`.`{table}`;')
    if parent:
        query(db, f"INSERT INTO system_menu SELECT * FROM `{LOCAL}`.system_menu WHERE path='/hrm' AND deleted=0;")
    return db

def ledgers(db):
    return query(db, "SELECT version,checksum FROM zsjos_schema_version ORDER BY version; SELECT module_code,version,checksum FROM zsjos_module_schema_version ORDER BY module_code,version;")

def failed(db):
    before = ledgers(db)
    try:
        query(db, migration_sql())
    except RuntimeError:
        assert before == ledgers(db), 'failed migration advanced version history'
    else:
        raise AssertionError('expected failure')

def verify(db):
    query(db, migration_sql())
    columns, indexes, _ = desired_schema(ROOT / 'script/sql/mysql/schema/core.sql')
    for table in TABLES:
        actual = {}
        for line in query(db, f"SELECT column_name,column_type,is_nullable FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='{table}';").splitlines():
            name, kind, nullable = line.split('\t')
            actual[(table, name)] = (kind, nullable)
        expected = {k: v for k, v in columns.items() if k[0] == table}
        assert actual == expected, (table, actual, expected)
        for (owner, key), (unique, names) in indexes.items():
            if owner != table:
                continue
            result = query(db, f"SELECT non_unique,column_name FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='{table}' AND index_name='{key}' ORDER BY seq_in_index;").splitlines()
            assert result and int(result[0].split('\t')[0]) == unique
            assert tuple(r.split('\t')[1] for r in result) == names
    assert scalar(db, "SELECT COUNT(*) FROM zsjos_schema_version WHERE version='V347';") == 1
    assert scalar(db, "SELECT COUNT(*) FROM zsjos_module_schema_version WHERE module_code='core' AND version='V347';") == 1
    assert scalar(db, 'SELECT COUNT(*) FROM zsjkqt_draft;') == 0
    assert json.loads(query(db, "SELECT value_json FROM zsjkqt_config WHERE tenant_id=1 AND config_key='profiles';")) == {}
    schedule = json.loads(query(db, "SELECT value_json FROM zsjkqt_config WHERE tenant_id=1 AND config_key='schedules';"))
    expected = json.loads((ROOT / 'backend/yudao-module-zsjos/src/main/resources/zsjkqt/department-schedules.json').read_text(encoding='utf-8'))
    assert schedule == expected
    value = query(db, "SELECT HEX(name) FROM system_menu WHERE permission='zsjos:zsjkqt:access' AND deleted=0;").strip()
    assert bytes.fromhex(value).decode('utf-8') == '中世健考勤通'
    assert query(db, "SELECT JSON_UNQUOTE(JSON_EXTRACT(value_json,'$.departments.\"销售部\".afternoon.end')) FROM zsjkqt_config WHERE config_key='schedules' AND tenant_id=1;").strip() == '21:30'
    once = ledgers(db)
    query(db, migration_sql())
    assert once == ledgers(db)
    digest = hashlib.sha256(SQL.read_bytes()).hexdigest()
    assert query(db, "SELECT checksum FROM zsjos_schema_version WHERE version='V347'; SELECT checksum FROM zsjos_module_schema_version WHERE module_code='core' AND version='V347';").splitlines() == [digest, digest]
    assert scalar(db, "SELECT COUNT(*) FROM system_menu WHERE permission='zsjos:zsjkqt:access' AND deleted=0;") == 1
    print('PASS schema/indexes/replay/config/UTF-8/both ledgers:', db)

def concurrency(db):
    query(db, "INSERT INTO zsjkqt_draft(client_id,name,year,month,snapshot_json,tenant_id) VALUES('synthetic','合成测试',2026,6,'{}',1);")
    result = query(db, "UPDATE zsjkqt_draft SET name='合成修订',version=version+1 WHERE client_id='synthetic' AND version=0 AND tenant_id=1 AND deleted=0; SELECT ROW_COUNT(); UPDATE zsjkqt_draft SET name='错误覆盖',version=version+1 WHERE client_id='synthetic' AND version=0 AND tenant_id=1 AND deleted=0; SELECT ROW_COUNT();")
    assert result.splitlines() == ['1', '0']
    assert scalar(db, "SELECT COUNT(*) FROM zsjkqt_draft WHERE client_id='synthetic' AND tenant_id=2;") == 0
    # Replay must not reset manually changed shared config or drafts.
    query(db, "UPDATE zsjkqt_config SET version=1 WHERE config_key='schedules' AND tenant_id=1;")
    query(db, migration_sql())
    assert scalar(db, "SELECT version FROM zsjkqt_config WHERE config_key='schedules' AND tenant_id=1;") == 1
    assert scalar(db, "SELECT version FROM zsjkqt_draft WHERE client_id='synthetic';") == 1
    print('PASS optimistic update / tenant scope / replay preserves business data')


def incompatible_indexes_and_deleted_config(reference):
    for table, index, key in [('zsjkqt_draft', 'uk_zsjkqt_client', 'client_id'),
                              ('zsjkqt_config', 'uk_zsjkqt_config', 'config_key')]:
        for suffix, columns in [('columns', 'tenant_id,id'), ('order', f'{key},tenant_id')]:
            db = clone(('draft' if table.endswith('draft') else 'config') + suffix)
            for name in TABLES:
                query(db, f'CREATE TABLE `{name}` LIKE `{reference}`.`{name}`;')
            query(db, f'ALTER TABLE `{table}` DROP INDEX `{index}`, ADD UNIQUE KEY `{index}` ({columns});')
            failed(db)
            query(db, f'ALTER TABLE `{table}` DROP INDEX `{index}`, ADD UNIQUE KEY `{index}` (tenant_id,{key});')
            verify(db)
    for key in ['profiles', 'schedules']:
        db = clone('deleted' + key)
        for name in TABLES:
            query(db, f'CREATE TABLE `{name}` LIKE `{reference}`.`{name}`;')
        query(db, f"INSERT INTO zsjkqt_config SELECT * FROM `{reference}`.zsjkqt_config; UPDATE zsjkqt_config SET deleted=b'1' WHERE tenant_id=1 AND config_key='{key}';")
        failed(db)
        assert scalar(db, f"SELECT COUNT(*) FROM zsjkqt_config WHERE tenant_id=1 AND config_key='{key}' AND deleted=b'1';") == 1
        # This recovery is synthetic fixture preparation, never automatic restoration of application config.
        query(db, f"UPDATE zsjkqt_config SET deleted=b'0' WHERE tenant_id=1 AND config_key='{key}';")
        verify(db)
    print('PASS exact unique columns/order and deleted config failure/recovery without false ledger success')

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--sync-local', action='store_true')
    args = parser.parse_args()
    initial = clone('initial'); verify(initial); concurrency(initial)
    incompatible_indexes_and_deleted_config(initial)
    prerequisite = clone('prerequisite', False); failed(prerequisite)
    query(prerequisite, f"INSERT INTO system_menu SELECT * FROM `{LOCAL}`.system_menu WHERE path='/hrm' AND deleted=0;"); verify(prerequisite)
    partial = clone('partial')
    ddl = SQL.read_text(encoding='utf-8').split('CREATE TABLE IF NOT EXISTS zsjkqt_draft', 1)[1].split('CREATE TABLE IF NOT EXISTS zsjkqt_config', 1)[0]
    query(partial, 'CREATE TABLE IF NOT EXISTS zsjkqt_draft' + ddl); verify(partial)
    wrong = clone('wrongtype'); query(wrong, 'CREATE TABLE IF NOT EXISTS zsjkqt_draft' + ddl)
    query(wrong, 'ALTER TABLE zsjkqt_draft MODIFY COLUMN snapshot_json VARCHAR(32) NOT NULL;'); failed(wrong)
    query(wrong, 'ALTER TABLE zsjkqt_draft MODIFY COLUMN snapshot_json LONGTEXT NOT NULL;'); verify(wrong)
    ledger = clone('ledgerfailure')
    query(ledger, "CREATE TRIGGER zsjkqt_refuse_ledger BEFORE INSERT ON zsjos_module_schema_version FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='synthetic ledger failure';")
    failed(ledger)
    assert scalar(ledger, "SELECT COUNT(*) FROM system_menu WHERE permission='zsjos:zsjkqt:access';") == 0
    assert scalar(ledger, 'SELECT COUNT(*) FROM zsjkqt_config;') == 0
    query(ledger, 'DROP TRIGGER zsjkqt_refuse_ledger;'); verify(ledger)
    if args.sync_local:
        assert scalar(LOCAL, "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN ('zsjkqt_draft','zsjkqt_config');") == 0
        assert scalar(LOCAL, "SELECT COUNT(*) FROM zsjos_schema_version WHERE version='V347';") == 0
        backup = ROOT / 'backups/mysql' / ('zsjkqt-before-' + STAMP + '.json'); backup.parent.mkdir(parents=True, exist_ok=True)
        backup.write_text(json.dumps({'ledgers': ledgers(LOCAL), 'hrmMenu': query(LOCAL, "SELECT id,parent_id,path,workbench_render_mode FROM system_menu WHERE path='/hrm' AND deleted=0;")}, ensure_ascii=False), encoding='utf-8')
        grants = query(LOCAL, 'SELECT role_id,menu_id FROM system_role_menu WHERE deleted=0 ORDER BY role_id,menu_id;')
        verify(LOCAL)
        assert grants == query(LOCAL, 'SELECT role_id,menu_id FROM system_role_menu WHERE deleted=0 ORDER BY role_id,menu_id;')
        for table in TABLES:
            assert re.sub(r'AUTO_INCREMENT=\d+ ', '', query(initial, f'SHOW CREATE TABLE {table};')) == re.sub(r'AUTO_INCREMENT=\d+ ', '', query(LOCAL, f'SHOW CREATE TABLE {table};'))
        print('PASS scoped local application; existing grants unchanged; backup:', backup)
