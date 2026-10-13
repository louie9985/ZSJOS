#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Isolated MySQL recovery tests. Creates and drops only its uniquely named fixture DB.

No business database connection is opened. Private ledger archives remain in --evidence.
"""
import argparse
import json
import subprocess
import uuid
from pathlib import Path

import pymysql
from pymysql.cursors import DictCursor
import notice_version_recovery as recovery
import order_history_repair as sql_parser
import test_notice_content_capacity as fixtures


def connect(container, database):
    info = json.loads(subprocess.check_output(['docker', 'inspect', container]))[0]
    env = dict(item.split('=', 1) for item in info['Config']['Env'] if '=' in item)
    password = env.get('MYSQL_ROOT_PASSWORD')
    if not password:
        password = subprocess.check_output(['docker', 'exec', container, 'cat',
                                           env['MYSQL_ROOT_PASSWORD_FILE']], text=True).strip()
    host = next(iter(info['NetworkSettings']['Networks'].values()))['IPAddress']
    return pymysql.connect(host=host, user='root', password=password, database=database,
                           charset='utf8mb4', autocommit=True, cursorclass=DictCursor)


def run(container, evidence):
    checks = fixtures.Checks(container)
    database = checks.create('recovery_' + uuid.uuid4().hex[:8])
    conn = connect(container, database)
    evidence.mkdir(mode=0o700, parents=True, exist_ok=True)
    try:
        import re
        source = (fixtures.SQL / 'schema/core.sql').read_text() + (fixtures.SQL / '02-bootstrap-zsjos-seed.sql').read_text()
        with conn.cursor() as cur:
            for table in ['system_notice', 'system_menu', 'zsjos_lead_assignment_history',
                          'zsjos_schema_version', 'zsjos_module_schema_version']:
                definition = re.search(r'CREATE TABLE IF NOT EXISTS `' + table + r'` \([\s\S]+?\) ENGINE=[^;]+;', source)
                assert definition, table
                cur.execute(definition[0])
            cur.execute('ALTER TABLE zsjos_lead_assignment_history DROP COLUMN newcomer_priority')
            cur.execute("INSERT INTO system_notice(title,content,type) VALUES('保留公告','保留中文正文',2)")
            cur.execute("INSERT INTO system_menu(name,permission,type,parent_id,path) VALUES('销售管理','zsjos:subordinate-sales:query',2,0,'sales')")
            cur.execute("INSERT INTO zsjos_schema_version(version,description) VALUES('V294','fixture')")
            cur.execute("INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version) VALUES('core','V294','fixture','fixture','fixture')")
            cur.execute("INSERT INTO zsjos_schema_version(version,description,checksum) VALUES('V295','Notice content capacity',%s)", (recovery.OLD_CHECKSUM,))
            cur.execute("INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version) VALUES('core','V295','Notice content capacity',%s,'baseline')", (recovery.OLD_CHECKSUM,))
            cur.execute('SELECT * FROM system_notice')
            notices = cur.fetchall()
        original = recovery.inspect(conn)
        # A failed second delete must roll back the first ledger deletion.
        with conn.cursor() as cur:
            cur.execute("CREATE TRIGGER reject_delete BEFORE DELETE ON zsjos_module_schema_version FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='fixture failure'")
        try:
            recovery.release_old_version(conn, evidence / 'failed-ledgers.json')
            raise AssertionError('Expected second-ledger failure')
        except pymysql.MySQLError:
            pass
        assert recovery.inspect(conn) == original
        with conn.cursor() as cur:
            cur.execute('DROP TRIGGER reject_delete')
        recovery.release_old_version(conn, evidence / 'old-ledgers.json')
        with conn.cursor() as cur:
            cur.execute("SELECT COUNT(*) n FROM zsjos_schema_version WHERE version='V313'")
            assert cur.fetchone()['n'] == 0
            newcomer = fixtures.SQL / 'migrations/V295__newcomer_priority_dispatch.sql'
            for _ in range(2):
                for statement in sql_parser.statements(newcomer.read_text()):
                    cur.execute(statement)
            cur.execute("SELECT description FROM zsjos_schema_version WHERE version='V295'")
            assert cur.fetchone()['description'] == 'Newcomer priority dispatch'
            cur.execute("SELECT COUNT(*) n FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN ('zsjos_newcomer_pool_config','zsjos_newcomer_pool_daily')")
            assert cur.fetchone()['n'] == 2
            cur.execute('SELECT * FROM system_notice')
            assert cur.fetchall() == notices
            cur.execute('SELECT HEX(content) h FROM system_notice')
            assert cur.fetchone()['h'] == '保留中文正文'.encode().hex().upper()
        try:
            recovery.release_old_version(conn, evidence / 'must-not-archive.json')
            raise AssertionError('Must reject real newcomer V295')
        except ValueError:
            pass
        assert not (evidence / 'must-not-archive.json').exists()
        print('PASS old V295 archive, atomic rollback, recovery, real newcomer apply/replay, notice preservation and wrong-identity rejection')
    finally:
        conn.close()
        # Exact database created by this invocation; no discovery/pattern-based deletion.
        checks.query(None, f'DROP DATABASE `{database}`')
        print('CLEANED', database)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--container', required=True)
    parser.add_argument('--evidence', type=Path, required=True)
    args = parser.parse_args()
    run(args.container, args.evidence)
