#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Scoped MySQL verification. Retains isolated databases for inspection; never deletes data."""
import datetime
from pathlib import Path
from urllib.parse import urlparse
import pymysql
import yaml

ROOT = Path(__file__).resolve().parents[4]


def connection():
    docs = yaml.safe_load_all((ROOT / 'backend/yudao-server/src/main/resources/application-local.yaml').read_text(encoding='utf-8'))
    config = next(d['spring']['datasource']['dynamic']['datasource']['master'] for d in docs if d and 'datasource' in d.get('spring', {}))
    target = urlparse(config['url'][5:])
    return pymysql.connect(host=target.hostname, port=target.port or 3306, user=config['username'],
                           password=str(config['password']), database=target.path[1:], charset='utf8mb4', autocommit=True)


def execute(cursor, sql):
    delimiter = ';'
    pending = []
    for line in sql.splitlines():
        if line.startswith('DELIMITER '):
            delimiter = line.split()[1]
            continue
        pending.append(line)
        if line.rstrip().endswith(delimiter):
            statement = '\n'.join(pending).rstrip()[:-len(delimiter)]
            cursor.execute(statement)
            while cursor.nextset():
                pass
            pending = []
    if any(x.strip() for x in pending):
        raise ValueError('Unterminated SQL')


if __name__ == '__main__':
    sql = (ROOT / 'script/sql/mysql/migrations/V362__identity_area_history.sql').read_text(encoding='utf-8')
    with connection() as conn, conn.cursor() as cur:
        cur.execute('SELECT DATABASE()'); target = cur.fetchone()[0]
        name = 'verify_identity_' + datetime.datetime.now().strftime('%Y%m%d%H%M%S')
        cur.execute(f'CREATE DATABASE `{name}` CHARACTER SET utf8mb4')
        cur.execute(f'USE `{name}`')
        for table in ['zsjos_schema_version','zsjos_module_schema_version','zsjos_student_info_form_value']:
            cur.execute(f'CREATE TABLE `{table}` LIKE `{target}`.`{table}`')
        try:
            execute(cur,sql)
            raise AssertionError('Missing prerequisite did not fail')
        except pymysql.MySQLError:
            cur.execute("SELECT COUNT(*) FROM zsjos_schema_version WHERE version='V362'")
            assert cur.fetchone()[0] == 0
        for table in ['zsjos_schema_version','zsjos_module_schema_version']:
            cur.execute(f"INSERT INTO `{table}` SELECT * FROM `{target}`.`{table}` WHERE version='V361'")
        # Induce a transactional failure after DDL, then recover using the original SQL.
        broken = sql.replace(' START TRANSACTION;', " SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='controlled partial failure';\n START TRANSACTION;",1)
        try:
            execute(cur, broken)
            raise AssertionError('Injected failure did not fail')
        except pymysql.MySQLError:
            cur.execute("SELECT COUNT(*) FROM zsjos_schema_version WHERE version='V362'")
            assert cur.fetchone()[0] == 0
        execute(cur,sql)
        execute(cur,sql)
        cur.execute('SELECT COUNT(*) FROM system_identity_area'); assert cur.fetchone()[0] == 6246
        cur.execute("SELECT code,HEX(name) FROM system_identity_area WHERE code IN ('420621','362421') ORDER BY code")
        assert cur.fetchall() == (('362421','E59089E5AE89E58EBF'),('420621','E8A584E998B3E58EBF'))
        cur.execute("SELECT COUNT(*) FROM zsjos_module_schema_version WHERE module_code='core' AND version='V362'"); assert cur.fetchone()[0] == 1
        print('PASS: prerequisite failure, partial DDL recovery, first successful application, repeat, count, Chinese HEX, ledgers. Retained:',name)
