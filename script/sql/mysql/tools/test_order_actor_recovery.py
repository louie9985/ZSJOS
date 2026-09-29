#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Synthetic isolated MySQL 8 migration and guarded actor-recovery verification."""
import copy
from decimal import Decimal
import json
from pathlib import Path
import subprocess
import time
import unittest
import uuid

import pymysql
import recover_order_actors as recovery

MIGRATION = Path(__file__).resolve().parents[1] / 'migrations/V285__order_imported_actor_snapshot.sql'


class RecoveryPlanTest(unittest.TestCase):
    def test_dump_parser_preserves_chinese_escapes_null_and_quotes(self):
        self.assertEqual([["历史姓名", "O'Brien", 'line\nnext', None, 'NULL', '1'], ['a\tb', '反斜\\杠']],
                         list(recovery.dump_values("('历史姓名','O\\'Brien','line\\nnext',NULL,'NULL',1),('a\\tb','反斜\\\\杠');")))

    def test_plan_requires_source_identity_amount_and_explicit_backup_name_choice(self):
        row = dict(id=1, tenant_id=1, deleted=0, order_no='OD1', total_amount=Decimal('100'),
                   current_approval_round_id=None, submitter_user_id=20, formal_sales_user_id=30)
        old = {'OD1': dict(id='91', recorder_id='120', sales_id='130', total_amount='100', sales_name_snapshot='原成交姓名')}
        employees = {120: {'display_name': '旧备份录单姓名'}}
        strict = recovery.build_plan([row], old, employees, 'a'*64, '2026-09-20', 1, False)
        self.assertNotIn('submitter', strict['changes'][0]['evidence'])
        self.assertEqual('原成交姓名', strict['changes'][0]['evidence']['formalSales']['name'])
        approved = recovery.build_plan([row], old, employees, 'a'*64, '2026-09-20', 1, True)
        self.assertEqual('legacy_backup_profile', approved['changes'][0]['evidence']['submitter']['nameSource'])
        self.assertEqual(120, approved['changes'][0]['evidence']['submitter']['sourceEmployeeId'])
        self.assertEqual(20, approved['changes'][0]['evidence']['submitter']['orderUserId'])
        variants = [dict(row, tenant_id=2), dict(row, deleted=1), dict(row, total_amount=Decimal('99')),
                    dict(row, current_approval_round_id=9), dict(row, order_no='MISSING')]
        self.assertEqual([], recovery.build_plan(variants, old, employees, 'a'*64, '2026-09-20', 1, True)['changes'])


class RecoveryMysqlTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.container = 'zsjos-order-actor-test-' + uuid.uuid4().hex[:8]
        subprocess.run(['docker', 'run', '-d', '--rm', '--name', cls.container,
                        '-e', 'MYSQL_ALLOW_EMPTY_PASSWORD=yes', '-e', 'MYSQL_ROOT_HOST=%',
                        '-p', '127.0.0.1::3306', 'mysql:8', '--character-set-server=utf8mb4'],
                       check=True, capture_output=True)
        cls.addClassCleanup(lambda: subprocess.run(['docker', 'rm', '-f', cls.container], capture_output=True))
        info = json.loads(subprocess.check_output(['docker', 'inspect', cls.container]))[0]
        port = int(info['NetworkSettings']['Ports']['3306/tcp'][0]['HostPort'])
        for _ in range(60):
            try:
                cls.conn = pymysql.connect(host='127.0.0.1', port=port, user='root', charset='utf8mb4',
                                           autocommit=False, cursorclass=pymysql.cursors.DictCursor)
                break
            except pymysql.Error:
                time.sleep(0.5)
        else:
            raise RuntimeError('Isolated MySQL did not become ready')
        cls.addClassCleanup(cls.conn.close)

    def sql(self, sql, args=()):
        with self.conn.cursor() as cur:
            cur.execute(sql, args)
            return list(cur.fetchall())

    def setUp(self):
        # Each case gets an independently named schema within this owned test container.
        self.database = 'case_' + uuid.uuid4().hex[:8]
        self.sql('CREATE DATABASE ' + self.database + ' CHARACTER SET utf8mb4')
        self.sql('USE ' + self.database)
        self.sql('CREATE TABLE zsjos_schema_version(version varchar(32) PRIMARY KEY, description varchar(255), checksum varchar(128)) ENGINE=InnoDB')
        self.sql('CREATE TABLE zsjos_module_schema_version(module_code varchar(32), version varchar(32), description varchar(255), checksum varchar(128), release_version varchar(128), PRIMARY KEY(module_code,version)) ENGINE=InnoDB')
        self.sql("CREATE TABLE zsjos_order(id bigint PRIMARY KEY, tenant_id bigint, deleted bit DEFAULT b'0', order_no varchar(64), total_amount decimal(10,2), current_approval_round_id bigint, submitter_user_id bigint, formal_sales_user_id bigint, status varchar(32), update_time datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP) ENGINE=InnoDB")
        self.sql("INSERT INTO zsjos_schema_version VALUES ('V284','prerequisite','original')")
        self.sql("INSERT INTO zsjos_module_schema_version VALUES ('core','V284','prerequisite','original','baseline')")
        self.conn.commit()

    def migrate(self, success=True):
        result = subprocess.run(['docker', 'exec', '-i', self.container, 'mysql', '-uroot',
                                 '--default-character-set=utf8mb4', self.database],
                                input=MIGRATION.read_bytes(), capture_output=True)
        self.assertEqual(success, result.returncode == 0, result.stderr.decode())
        self.conn.rollback()

    def versions(self):
        return [self.sql("SELECT * FROM zsjos_schema_version WHERE version='V285'"),
                self.sql("SELECT * FROM zsjos_module_schema_version WHERE version='V285'")]

    def test_initial_repeat_partial_recovery_and_success_marker_is_not_a_skip_guard(self):
        self.migrate()
        versions = self.versions()
        self.assertEqual([1, 1], [len(v) for v in versions])
        self.migrate()
        self.assertEqual(versions, self.versions())
        self.sql('ALTER TABLE zsjos_order DROP COLUMN imported_actor_snapshot')
        self.migrate()
        self.assertEqual(versions, self.versions())
        self.assertEqual('json', self.sql("SELECT data_type FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_order' AND column_name='imported_actor_snapshot'")[0]['DATA_TYPE'])
        self.sql("DELETE FROM zsjos_schema_version WHERE version='V285'")
        self.sql("DELETE FROM zsjos_module_schema_version WHERE version='V285'")
        self.conn.commit()
        self.migrate()
        self.assertEqual([1, 1], [len(v) for v in self.versions()])
        comment = self.sql("SELECT HEX(column_comment) value FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_order' AND column_name='imported_actor_snapshot'")[0]['value']
        self.assertEqual('导入订单人员展示证据及来源'.encode().hex().upper(), comment)

    def test_prerequisite_and_column_failures_cannot_record_success(self):
        self.sql("DELETE FROM zsjos_module_schema_version WHERE version='V284'")
        self.conn.commit()
        self.migrate(False)
        self.assertEqual([[], []], self.versions())
        self.sql("INSERT INTO zsjos_module_schema_version VALUES ('core','V284','prerequisite','original','baseline')")
        self.sql('ALTER TABLE zsjos_order ADD COLUMN imported_actor_snapshot TEXT')
        self.migrate(False)
        self.assertEqual([[], []], self.versions())
        self.sql('ALTER TABLE zsjos_order MODIFY imported_actor_snapshot JSON')
        self.migrate()
        before = self.versions()
        self.sql('ALTER TABLE zsjos_order MODIFY imported_actor_snapshot TEXT')
        self.migrate(False)
        self.assertEqual(before, self.versions())
        self.sql('ALTER TABLE zsjos_order MODIFY imported_actor_snapshot JSON')
        self.migrate()
        self.assertEqual(before, self.versions())

    def test_repair_rehearsal_repeat_restore_and_changed_row_guard(self):
        self.migrate()
        self.sql("INSERT INTO zsjos_order(id,tenant_id,order_no,total_amount,submitter_user_id,formal_sales_user_id,status) VALUES (1,1,'OD1',100,20,30,'effective'),(2,2,'OD1',100,20,30,'effective')")
        self.conn.commit()
        rows = self.sql('SELECT * FROM zsjos_order ORDER BY id')
        plan = recovery.build_plan(rows, {'OD1': dict(id='91', recorder_id='120', sales_id='130', total_amount='100', sales_name_snapshot='原成交姓名')}, {120: {'display_name': '备份录单姓名'}}, 'a'*64, '2026-09-20', 1, True)
        self.assertEqual(1, recovery.execute(self.conn, plan))
        self.assertEqual(0, recovery.execute(self.conn, plan))
        self.conn.rollback()
        self.assertEqual(rows, self.sql('SELECT * FROM zsjos_order ORDER BY id'))
        self.assertEqual(1, recovery.execute(self.conn, plan))
        self.conn.commit()
        self.assertEqual(0, recovery.execute(self.conn, plan))
        self.assertEqual(rows[1], self.sql('SELECT * FROM zsjos_order WHERE id=2')[0])
        self.assertEqual(1, recovery.execute(self.conn, plan, restore=True))
        self.assertEqual(0, recovery.execute(self.conn, plan, restore=True))
        self.conn.commit()
        self.assertEqual(rows, self.sql('SELECT * FROM zsjos_order ORDER BY id'))
        self.sql("UPDATE zsjos_order SET status='terminated' WHERE id=1")
        self.conn.commit()
        with self.assertRaisesRegex(ValueError, 'Target changed'):
            recovery.execute(self.conn, plan)
        self.conn.rollback()
        self.assertIsNone(self.sql('SELECT imported_actor_snapshot FROM zsjos_order WHERE id=1')[0]['imported_actor_snapshot'])


if __name__ == '__main__':
    unittest.main()
