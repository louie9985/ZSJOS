# -*- coding: utf-8 -*-
"""V282 scoped MySQL checks; creates retained isolated schemas, never deletes data.

Usage: python script/sql/mysql/tools/test_calendar_notifications.py
Uses the existing local yudao-mysql container and its configured root credential.
"""
import datetime
import re
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[4]
SQL = ROOT / 'script/sql/mysql'
MIGRATION = (SQL / 'migrations/V282__calendar_notifications.sql').read_text(encoding='utf-8')
SCHEMA = (SQL / 'schema/core.sql').read_text(encoding='utf-8')
SEED = (SQL / '02-bootstrap-zsjos-seed.sql').read_text(encoding='utf-8')
TABLES = ['zsjos_course_calendar_event', 'zsjos_exam_schedule',
          'zsjos_calendar_notify_batch', 'zsjos_calendar_notify_recipient',
          'zsjos_calendar_notify_snapshot', 'zsjos_calendar_notify_state', 'zsjos_calendar_notify_preview',
          'zsjos_calendar_notify_intent']
MENU_IDS = '73613,73614,73633,73634'


def menu_setup():
    return (table('system_menu') + table('system_role_menu')
            + "INSERT INTO system_menu(id,name,permission,type,parent_id) VALUES "
              "(73610,'考期日历','zsjos:exam-calendar:query',2,73600),"
              "(73630,'课程日历','zsjos:course-calendar:query',2,73600);"
              "INSERT INTO system_role_menu(role_id,menu_id,tenant_id) VALUES (1,73610,1),(2,73630,2);")


def menu_signature(database):
    return query(database, f'SELECT id,HEX(name),permission,type,sort,parent_id,status,deleted+0 '
                          f'FROM system_menu WHERE id IN ({MENU_IDS}) ORDER BY id;')[0]


def verify_menus(database):
    rows = menu_signature(database).splitlines()
    assert len(rows) == 4, rows
    for row, menu_id, parent, prefix, all_staff in zip(rows, [73613, 73614, 73633, 73634],
            [73610, 73610, 73630, 73630], ['exam', 'exam', 'course', 'course'], [False, True, False, True]):
        label = '全员通知' if all_staff else '发送通知'
        permission = f'zsjos:{prefix}-calendar:notify' + ('-all' if all_staff else '')
        expected = [str(menu_id), label.encode('utf-8').hex().upper(), permission, '3',
                    '4' if all_staff else '3', str(parent), '0', '0']
        assert row.split('\t') == expected, row


def query(database, text, force=False):
    command = 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql --default-character-set=utf8mb4 -uroot -N -B'
    if force:
        command += ' --force'
    result = subprocess.run(['docker', 'exec', '-i', 'yudao-mysql', 'sh', '-c', command],
                            input=('SET NAMES utf8mb4;\n' + (f'USE `{database}`;\n' if database else '') + text).encode('utf-8'),
                            capture_output=True)
    error = result.stderr.decode('utf-8')
    if not force and (result.returncode or 'ERROR' in error):
        raise RuntimeError(error)
    return result.stdout.decode('utf-8').strip(), error


def table(name):
    match = re.search(r'CREATE TABLE IF NOT EXISTS `' + name + r'` \([\s\S]+?\) ENGINE=[^;]+;', SCHEMA + SEED)
    assert match, name
    return match.group()


def versions(database):
    return query(database, "SELECT version,checksum FROM zsjos_schema_version WHERE version='V282';"
                 "SELECT version,checksum FROM zsjos_module_schema_version WHERE module_code='core' AND version='V282';")[0]


def signature(database):
    names = ','.join("'" + name + "'" for name in TABLES)
    return query(database, f"SELECT table_name,column_name,column_type,is_nullable,IFNULL(column_default,'<NULL>'),extra,HEX(column_comment) "
                 f"FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name IN ({names}) ORDER BY table_name,column_name;"
                 f"SELECT table_name,index_name,non_unique,seq_in_index,column_name FROM information_schema.statistics "
                 f"WHERE table_schema=DATABASE() AND table_name IN ({names}) ORDER BY table_name,index_name,seq_in_index;")[0]


def run():
    prefix = 'v282_verify_' + datetime.datetime.now().strftime('%Y%m%d%H%M%S')
    cases = ['initial', 'partial', 'snapshot_partial', 'snapshot_key_failure', 'intent_partial', 'intent_key_failure',
             'prerequisite', 'ddl_failure', 'ledger_failure', 'baseline']
    for case in cases:
        database = prefix + '_' + case
        query(None, f'CREATE DATABASE `{database}` CHARACTER SET utf8mb4;')
        setup = table('zsjos_schema_version') + table('zsjos_module_schema_version') + menu_setup()
        for name in TABLES[:2]:
            definition = table(name)
            if case != 'baseline':
                definition = re.sub(r'^.*`calendar_version`.*\n', '', definition, flags=re.M)
            setup += definition
        if case != 'prerequisite':
            setup += "INSERT INTO zsjos_schema_version(version,description,checksum) VALUES ('V281','fixture','fixture');"
            setup += "INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version) VALUES ('core','V281','fixture','fixture','fixture');"
        if case in ('partial', 'baseline'):
            setup += ''.join(table(name) for name in TABLES[2:])
        if case == 'snapshot_partial':
            setup += table('zsjos_calendar_notify_snapshot')
        if case == 'snapshot_key_failure':
            setup += table('zsjos_calendar_notify_snapshot').replace('UNIQUE KEY `uk_calendar_snapshot_version`', 'KEY `uk_calendar_snapshot_version`')
        if case in ('intent_partial', 'intent_key_failure'):
            definition = table('zsjos_calendar_notify_intent')
            if case == 'intent_key_failure':
                definition = definition.replace('UNIQUE KEY `uk_calendar_intent_operation`', 'KEY `uk_calendar_intent_operation`')
            setup += definition
        if case == 'partial':
            setup += "INSERT INTO zsjos_schema_version(version,description,checksum) VALUES ('V282','fixture','preserve');"
            setup += "INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version) VALUES ('core','V282','fixture','preserve','fixture');"
            setup += "INSERT INTO zsjos_calendar_notify_batch(tenant_id,calendar_type,calendar_id,calendar_version,event_type,scope,title_snapshot,source_event_key,status) VALUES (1,'COURSE',1,7,'PUBLISH','ALL','历史通知','fixture','PENDING');"
        if case == 'ddl_failure':
            setup += 'CREATE VIEW zsjos_calendar_notify_batch AS SELECT 1 AS id;'
        if case == 'ledger_failure':
            setup += "CREATE TRIGGER reject_v282 BEFORE INSERT ON zsjos_module_schema_version FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='injected ledger failure';"
        query(database, setup)
        before = versions(database)
        _, error = query(database, MIGRATION, force=True)
        if case == 'intent_key_failure':
            assert 'ERROR' in error and versions(database) == before == '', error
            query(database, 'ALTER TABLE zsjos_calendar_notify_intent RENAME INDEX uk_calendar_intent_operation TO fixture_nonunique;'
                  'ALTER TABLE zsjos_calendar_notify_intent ADD UNIQUE KEY uk_calendar_intent_operation(tenant_id,operation_key);')
            query(database, MIGRATION)
            assert len(versions(database).splitlines()) == 2
            print('PASS intent key failure blocks ledgers; additive recovery succeeds')
            continue
        if case == 'snapshot_key_failure':
            assert 'ERROR' in error and versions(database) == before == '', error
            query(database, 'ALTER TABLE zsjos_calendar_notify_snapshot RENAME INDEX uk_calendar_snapshot_version TO fixture_nonunique;'
                  'ALTER TABLE zsjos_calendar_notify_snapshot ADD UNIQUE KEY uk_calendar_snapshot_version(tenant_id,calendar_type,calendar_id,calendar_version);')
            query(database, MIGRATION)
            assert len(versions(database).splitlines()) == 2
            print('PASS snapshot key failure blocks ledgers; additive recovery succeeds')
            continue
        if case in ('prerequisite', 'ddl_failure', 'ledger_failure'):
            assert 'ERROR' in error, case
            assert versions(database) == before == '', case
            assert menu_signature(database) == '', case
            print('PASS failure blocks both version writes:', case)
            continue
        assert 'ERROR' not in error, error
        verify_menus(database)
        assert query(database, 'SELECT COUNT(*) FROM system_role_menu;')[0] == '2'
        actual = signature(database)
        assert actual.count('calendar_version\tint\tNO\t1') == 2
        expected_hex = '通知内容版本'.encode('utf-8').hex().upper()
        assert actual.count(expected_hex) == 2
        for name in TABLES[4:]:
            assert name in actual
        if case == 'partial':
            assert versions(database) == before
            assert query(database, 'SELECT calendar_version,HEX(title_snapshot) FROM zsjos_calendar_notify_batch;')[0] == '7\t' + '历史通知'.encode('utf-8').hex().upper()
        if case == 'initial':
            batch = "INSERT INTO zsjos_calendar_notify_batch(tenant_id,calendar_type,calendar_id,calendar_version,event_type,scope,title_snapshot,source_event_key,status,idempotency_key) VALUES "
            query(database, batch + "(1,'COURSE',7,1,'MANUAL','SPECIFIED','fixture','event-a','SUBMITTED','request-a');")
            _, duplicate = query(database, batch + "(1,'COURSE',7,1,'MANUAL','SPECIFIED','fixture','event-b','SUBMITTED','request-a');", force=True)
            assert 'Duplicate entry' in duplicate
            query(database, batch + "(2,'COURSE',7,1,'MANUAL','SPECIFIED','fixture','event-b','SUBMITTED','request-a');")
            recipient = "INSERT INTO zsjos_calendar_notify_recipient(tenant_id,batch_id,user_id,user_type,status,accepted,dedup_key) VALUES "
            query(database, recipient + "(1,1,7,2,'PENDING',1,SHA2('same-content-user',256));")
            _, duplicate = query(database, recipient + "(1,99,7,2,'PENDING',1,SHA2('same-content-user',256));", force=True)
            assert 'Duplicate entry' in duplicate
            query(database, recipient + "(1,99,7,2,'PENDING',1,NULL),(1,100,7,2,'PENDING',1,NULL);")
            query(database, 'START TRANSACTION;' + batch + "(1,'COURSE',7,1,'MANUAL','SPECIFIED','fixture','event-rollback','SUBMITTED','request-rollback');ROLLBACK;")
            assert query(database, "SELECT COUNT(*) FROM zsjos_calendar_notify_batch WHERE idempotency_key='request-rollback';")[0] == '0'
            print('PASS tenant-scoped request and recipient uniqueness, explicit reminders and transaction rollback')
            intent = "INSERT INTO zsjos_calendar_notify_intent(tenant_id,operation_key,request_hash,acceptance_key,operator_user_id,calendar_type,calendar_id,snapshot_id,event_type,scope,recipients_json,next_attempt_at) VALUES "
            query(database, intent + "(1,'operation-a',SHA2('request',256),'acceptance-a',1,'COURSE',7,1,'UPDATED','ALL','固定名单',NOW());")
            _, duplicate = query(database, intent + "(1,'operation-a',SHA2('request',256),'acceptance-b',1,'COURSE',7,1,'UPDATED','ALL','[]',NOW());", force=True)
            assert 'Duplicate entry' in duplicate
            query(database, intent + "(2,'operation-a',SHA2('request',256),'acceptance-a',1,'COURSE',7,1,'UPDATED','ALL','[]',NOW());")
            query(database, 'START TRANSACTION;' + intent + "(1,'operation-rollback',SHA2('request',256),'acceptance-rollback',1,'COURSE',7,1,'UPDATED','ALL','[]',NOW());ROLLBACK;")
            assert query(database, "SELECT COUNT(*) FROM zsjos_calendar_notify_intent WHERE operation_key='operation-rollback';")[0] == '0'
            assert query(database, "SELECT HEX(recipients_json) FROM zsjos_calendar_notify_intent WHERE tenant_id=1;")[0] == '固定名单'.encode('utf-8').hex().upper()
            print('PASS persisted intent tenant uniqueness, rollback and UTF-8 snapshot')
        query(database, MIGRATION)
        assert signature(database) == actual
        assert len(versions(database).splitlines()) == 2
        print('PASS upgrade/replay/UTF-8:', case)
    assert signature(prefix + '_initial') == signature(prefix + '_partial') == signature(prefix + '_baseline') == signature(prefix + '_snapshot_partial') == signature(prefix + '_intent_partial')
    print('PASS baseline/upgrade/partial schemas match; retained schemas:', prefix)


if __name__ == '__main__':
    run()
