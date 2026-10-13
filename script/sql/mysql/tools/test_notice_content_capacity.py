#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Verify V313 on retained, uniquely named MySQL schemas; never write the business DB."""
import argparse
import datetime
import re
import subprocess
from pathlib import Path
from unittest.mock import patch

import zsjos_db as db_tools

ROOT = Path(__file__).resolve().parents[4]
SQL = ROOT / 'script/sql/mysql'
MIGRATION = (SQL / 'migrations/V313__notice_content_capacity.sql').read_text(encoding='utf-8')
PREFIX = 'nc313_' + datetime.datetime.now().strftime('%m%d%H%M%S%f')


def expand(path):
    return re.sub(r'^\s*SOURCE\s+([^;\r\n]+);',
                  lambda m: expand(ROOT / m[1].strip()),
                  path.read_text(encoding='utf-8'), flags=re.M | re.I)


def compare_notice_schema(client):
    drift = db_tools.schema_drift(client, db_tools.load_manifests())
    notice_drift = [item for item in drift if re.search(r'\bsystem_notice(?:[ .]|$)', item)]
    for item in drift:
        if item not in notice_drift:
            print('WARN unrelated schema drift:', item, flush=True)
    assert not notice_drift, notice_drift


class Checks:
    def __init__(self, container):
        self.container = container

    def run(self, database, sql, force=False):
        assert database is None or database.startswith(PREFIX + '_')
        command = ('if [ -n "$MYSQL_ROOT_PASSWORD_FILE" ]; then '
                   'MYSQL_PWD="$(cat "$MYSQL_ROOT_PASSWORD_FILE")"; '
                   'else MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; fi; export MYSQL_PWD; '
                   'exec mysql --default-character-set=utf8mb4 -uroot -N -B')
        result = subprocess.run(['docker', 'exec', '-i', self.container, 'sh', '-c',
                                 command + (' --force' if force else '')],
                                input=('SET NAMES utf8mb4;\n' +
                                       (f'USE `{database}`;\n' if database else '') + sql).encode(),
                                capture_output=True)
        if not force and (result.returncode or b'ERROR' in result.stderr):
            raise RuntimeError(result.stderr.decode())
        return result

    def query(self, database, sql):
        return self.run(database, sql).stdout.decode().strip()

    def create(self, suffix):
        database = PREFIX + '_' + suffix
        self.query(None, f'CREATE DATABASE `{database}` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;')
        return database

    def markers(self, database):
        return self.query(database, "SELECT * FROM zsjos_schema_version WHERE version='V313';"
                          "SELECT * FROM zsjos_module_schema_version WHERE module_code='core' AND version='V313';")

    def columns(self, database):
        return self.query(database, "SELECT column_name,column_type,is_nullable,column_default,"
                          "character_set_name,collation_name,HEX(column_comment) FROM information_schema.columns "
                          "WHERE table_schema=DATABASE() AND table_name='system_notice' ORDER BY ordinal_position;")

    def focused(self):
        source = ((SQL / 'schema/core.sql').read_text(encoding='utf-8') +
                  (SQL / '02-bootstrap-zsjos-seed.sql').read_text(encoding='utf-8'))

        def table(name):
            match = re.search(r'CREATE TABLE IF NOT EXISTS `' + name + r'` \([\s\S]+?\) ENGINE=[^;]+;', source)
            assert match, name
            return match[0]

        prereq = ("INSERT INTO zsjos_schema_version(version,description) VALUES('V312','fixture');"
                  "INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version) "
                  "VALUES('core','V312','fixture','fixture','fixture');")
        for case in ('initial', 'partial', 'prerequisite', 'contract', 'ledger_failure', 'existing_marker'):
            database = self.create(case)
            notice = table('system_notice')
            if case != 'partial':
                notice = notice.replace('`content` mediumtext', '`content` text')
            self.query(database, notice + table('zsjos_schema_version') + table('zsjos_module_schema_version'))
            if case != 'prerequisite':
                self.query(database, prereq)
            self.query(database, "INSERT INTO system_notice(title,content,type) VALUES('测试公告','保留原文',2);")
            rows_before = self.query(database, 'SELECT * FROM system_notice;')
            expected_columns = self.columns(database).replace('content\ttext\t', 'content\tmediumtext\t')
            if case == 'initial':
                failure = self.run(database, "UPDATE system_notice SET content=REPEAT('中',30000);", force=True)
                assert b'Data too long' in failure.stderr
            if case == 'contract':
                self.query(database, "ALTER TABLE system_notice MODIFY content TEXT NULL COMMENT '公告内容';")
            if case == 'ledger_failure':
                self.query(database, "CREATE TRIGGER reject_v313 BEFORE INSERT ON zsjos_module_schema_version "
                           "FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='injected ledger failure';")
            if case == 'existing_marker':
                self.query(database, "INSERT INTO zsjos_schema_version(version,description,checksum) VALUES('V313','fixture','preserve');"
                           "INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version) "
                           "VALUES('core','V313','fixture','preserve','fixture');")
            before = self.markers(database)
            result = self.run(database, MIGRATION, force=True)
            if case in ('prerequisite', 'contract', 'ledger_failure'):
                assert b'ERROR' in result.stderr and self.markers(database) == before, case
                if case == 'prerequisite':
                    self.query(database, prereq)
                elif case == 'contract':
                    self.query(database, "ALTER TABLE system_notice MODIFY content TEXT CHARACTER SET utf8mb4 "
                               "COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '公告内容';")
                else:
                    assert 'content\tmediumtext\t' in self.columns(database)
                    self.query(database, 'DROP TRIGGER reject_v313;')
                self.query(database, MIGRATION)
            else:
                assert not result.stderr, result.stderr.decode()
            after = self.markers(database)
            assert len(after.splitlines()) == 2
            if case == 'existing_marker':
                assert after == before
            self.query(database, MIGRATION)
            assert self.markers(database) == after
            assert self.columns(database) == expected_columns
            assert self.query(database, 'SELECT * FROM system_notice;') == rows_before
            assert self.query(database, 'SELECT HEX(content) FROM system_notice;') == '保留原文'.encode().hex().upper()
            self.query(database, "UPDATE system_notice SET content=REPEAT('中',30000);")
            assert self.query(database, "SELECT OCTET_LENGTH(content),CHAR_LENGTH(content),HEX(LEFT(content,1)) FROM system_notice;") == '90000\t30000\tE4B8AD'
            print('PASS', case, 'replay/recovery, atomic ledgers, preserved data/metadata, 90000-byte UTF-8 content', flush=True)

    def fresh(self):
        database = self.create('fresh')

        def query(_container, sql):
            return self.query(database, sql.replace('ALTER DATABASE zsjos_test ', f'ALTER DATABASE `{database}` '))

        def file(_container, path, expect_success=True):
            result = self.run(database, expand(path), force=not expect_success)
            # --force can return zero for errors; the existing verifier expects nonzero.
            if b'ERROR' in result.stderr and not result.returncode:
                result.returncode = 1
            return result

        with patch.object(db_tools, 'docker_mysql_query', query), \
                patch.object(db_tools, 'docker_mysql_file', file), \
                patch.object(db_tools, 'with_test_mysql', lambda callback: callback(self.container)):
            db_tools.test_fresh()
            compare_notice_schema(db_tools.DockerTestClient(self.container))
        assert 'content\tmediumtext\t' in self.columns(database)
        assert len(self.markers(database).splitlines()) == 2
        print('PASS full fresh chain and notice desired-schema comparison:', database, flush=True)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--container', required=True)
    parser.add_argument('--fresh', action='store_true')
    args = parser.parse_args()
    checks = Checks(args.container)
    checks.focused()
    if args.fresh:
        checks.fresh()
    print('Retained isolated schemas:', PREFIX, flush=True)
