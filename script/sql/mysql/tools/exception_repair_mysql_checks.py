#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Retained synthetic MySQL checks for the October 14 exception repair.

Only newly created er1014_<timestamp>_* schemas are writable. No DROP DATABASE,
business-data copy, account changes or global server settings. Credentials stay
inside the process. Results contain counts/schema identifiers, not business data.
This is a SQL-level concurrency fixture, not a replay of the live application.
"""
import argparse
import concurrent.futures
import datetime as dt
from decimal import Decimal
import hashlib
import json
import re
import subprocess
import threading
from pathlib import Path

import pymysql

ROOT = Path(__file__).resolve().parents[4]
SQL = ROOT / 'script/sql/mysql'


class Checks:
    def __init__(self, container, port):
        self.container, self.port = container, port
        self.prefix = 'er1014_' + dt.datetime.now().strftime('%Y%m%d%H%M%S%f')
        self.created = set()
        self.results = []
        proc = subprocess.run(['docker', 'exec', container, 'sh', '-c',
                               'if [ -n "$MYSQL_ROOT_PASSWORD_FILE" ]; then cat "$MYSQL_ROOT_PASSWORD_FILE"; '
                               'else printf %s "$MYSQL_ROOT_PASSWORD"; fi'], capture_output=True, check=True)
        self.password = proc.stdout.decode().strip()

    def connection(self, schema):
        assert schema in self.created
        return pymysql.connect(host='127.0.0.1', port=self.port, user='root', password=self.password,
                               database=schema, charset='utf8mb4', autocommit=True)

    def cli(self, schema, source, force=False):
        assert schema is None or schema in self.created
        command = ('if [ -n "$MYSQL_ROOT_PASSWORD_FILE" ]; then MYSQL_PWD="$(cat "$MYSQL_ROOT_PASSWORD_FILE")"; '
                   'else MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; fi; export MYSQL_PWD; '
                   'exec mysql --default-character-set=utf8mb4 -uroot -N -B')
        result = subprocess.run(['docker', 'exec', '-i', self.container, 'sh', '-c',
                                 command + (' --force' if force else '')],
                                input=('SET NAMES utf8mb4;\n' + (f'USE `{schema}`;\n' if schema else '') + source).encode(),
                                capture_output=True)
        if not force and (result.returncode or b'ERROR' in result.stderr):
            raise RuntimeError(result.stderr.decode())
        return result

    def create(self, suffix):
        assert re.fullmatch('[a-z0-9_]+', suffix)
        name = self.prefix + '_' + suffix
        self.cli(None, f'CREATE DATABASE `{name}` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;')
        self.created.add(name)
        return name

    def query(self, schema, text, args=None):
        with self.connection(schema) as conn, conn.cursor() as cur:
            cur.execute(text, args)
            return cur.fetchall()

    def record(self, case, **details):
        self.results.append({'case': case, **details})
        print(json.dumps(self.results[-1], ensure_ascii=False), flush=True)

    @staticmethod
    def table(name):
        sources = (SQL / 'schema/core.sql').read_text(encoding='utf-8') + (SQL / '02-bootstrap-zsjos-seed.sql').read_text(encoding='utf-8')
        match = re.search(r'CREATE TABLE IF NOT EXISTS `?' + re.escape(name) + r'`?\s*\([\s\S]+?\) ENGINE\s*=[^;]+;', sources)
        assert match, name
        return match[0]

    def v184(self):
        migration = (SQL / 'migrations/V184__repair_work_order_scene_legacy_columns.sql').read_text(encoding='utf-8')
        for case in ('initial', 'partial', 'missing_prerequisite', 'ledger_failure'):
            schema = self.create('v184_' + case)
            scene = self.table('zsjos_work_order_scene')
            scene = re.sub(r'(`(?:source_post_code|target_post_code|assignment_mode)` varchar\(\d+\))[^,\n]*', r'\1 NOT NULL', scene)
            self.cli(schema, scene + self.table('zsjos_schema_version') + self.table('zsjos_module_schema_version'))
            if case != 'missing_prerequisite':
                for name in ('zsjos_student_info_form', 'zsjos_student_info_form_value', 'zsjos_student_info_form_config'):
                    self.cli(schema, self.table(name))
            if case == 'partial':
                self.cli(schema, 'ALTER TABLE zsjos_work_order_scene MODIFY source_post_code varchar(64) NULL;')
            if case == 'ledger_failure':
                self.cli(schema, "DELIMITER $$\nCREATE TRIGGER reject_v184 BEFORE INSERT ON zsjos_module_schema_version FOR EACH ROW "
                         "BEGIN IF NEW.version='V184' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='fixture ledger failure'; END IF; END$$\nDELIMITER ;\n")
            result = self.cli(schema, migration, force=True)
            markers = self.query(schema, "SELECT version FROM zsjos_schema_version WHERE version='V184'")
            module = self.query(schema, "SELECT version FROM zsjos_module_schema_version WHERE version='V184'")
            if case == 'missing_prerequisite':
                assert b'ERROR' in result.stderr and not markers and not module
                self.record('v184_missing_prerequisite', status='PASS', schema=schema)
                continue
            if case == 'ledger_failure':
                assert b'ERROR' in result.stderr
                self.record('v184_second_ledger_failure', status='KNOWN_GAP' if markers or module else 'PASS',
                            core_success_marker=bool(markers), module_success_marker=bool(module), schema=schema)
                self.cli(schema, 'DROP TRIGGER reject_v184;')
            else:
                assert not result.stderr, result.stderr.decode()
            self.cli(schema, migration)
            cols = self.query(schema, "SELECT column_name,is_nullable FROM information_schema.columns WHERE table_schema=DATABASE() "
                              "AND table_name='zsjos_work_order_scene' AND column_name IN ('source_post_code','target_post_code','assignment_mode')")
            assert len(cols) == 3 and all(row[1] == 'YES' for row in cols)
            assert self.query(schema, "SELECT COUNT(*) FROM zsjos_schema_version WHERE version='V184'")[0][0] == 1
            assert self.query(schema, "SELECT COUNT(*) FROM zsjos_module_schema_version WHERE version='V184'")[0][0] == 1
            self.record('v184_' + case + '_replay_recovery', status='PASS', schema=schema)

    def hrm(self):
        schema = self.create('hrm')
        source = (SQL / 'schema/hrm.sql').read_text(encoding='utf-8')
        names = re.findall(r'CREATE TABLE IF NOT EXISTS\s+`?(\w+)`?', source, re.I)
        self.cli(schema, source)
        first = self.query(schema, 'SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()')[0][0]
        self.cli(schema, source)
        second = self.query(schema, 'SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()')[0][0]
        assert first == second == len(names)
        assert self.query(schema, 'SELECT COUNT(*) FROM hrm_employee')[0][0] == 0
        self.record('hrm_structure_initial_replay', status='PASS', tables=first, schema=schema,
                    limitation='structure only; module runner ledgers and full bootstrap not claimed')

    def numbers(self):
        schema = self.create('person_counter')
        self.cli(schema, self.table('zsjos_person_no_daily_counter'))
        source = (ROOT / 'backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/dal/mysql/lead/PersonNoDailyCounterMapper.java').read_text(encoding='utf-8')
        statements = re.findall(r'@(?:Insert|Select)\("""\s*([\s\S]*?)\s*"""\)', source)
        assert len(statements) == 2
        def bind(statement, values):
            keys = re.findall(r'#\{(\w+)\}', statement)
            return re.sub(r'#\{\w+\}', '%s', statement), tuple(values[key] for key in keys)
        def reserve(day, rollback=False):
            values = {'tenantId': 1, 'sequenceDate': day, 'maxSequence': 9999}
            with self.connection(schema) as conn, conn.cursor() as cur:
                conn.begin()
                cur.execute(*bind(statements[0], values))
                cur.execute(*bind(statements[1], values))
                value = cur.fetchone()[0]
                conn.rollback() if rollback else conn.commit()
                return value
        self.cli(schema, 'ALTER TABLE zsjos_person_no_daily_counter AUTO_INCREMENT=50000;')
        assert reserve('2026-10-14') == 1
        assert reserve('2026-10-15', True) == reserve('2026-10-15') == 1
        with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
            values = list(pool.map(lambda _: reserve('2026-10-14'), range(40)))
        assert sorted(values) == list(range(2, 42))
        self.record('person_counter_new_date_high_auto_increment_concurrent_rollback', status='PASS',
                    concurrent_values=len(values), schema=schema)

    def task_concurrency(self):
        schema = self.create('tasks')
        self.cli(schema, self.table('zsjos_business_task'))
        self.query(schema, "INSERT INTO zsjos_business_task(task_type,biz_type,biz_id,status,assignee_type,assignee_id,"
                   "title_snapshot,due_at,version,tenant_id) VALUES "
                   "('lead_follow_up_reminder','lead',1,'pending','user',1,'fixture',NOW(),0,1),"
                   "('lead_follow_up_reminder','lead',2,'pending','user',1,'fixture',NOW(),0,1)")
        ids = [row[0] for row in self.query(schema, 'SELECT id FROM zsjos_business_task ORDER BY id')]
        barrier = threading.Barrier(2)
        errors = []
        def worker(reminder):
            for _ in range(30):
                with self.connection(schema) as conn, conn.cursor() as cur:
                    cur.execute('SET SESSION innodb_lock_wait_timeout=3')
                    conn.begin()
                    barrier.wait(timeout=15)
                    try:
                        if reminder:
                            # Same task-row locking shape as emitForTask; no fabricated reverse lock order.
                            for task_id in ids:
                                cur.execute('SELECT * FROM zsjos_business_task WHERE id=%s AND tenant_id=1 AND deleted=0 FOR UPDATE', (task_id,))
                                cur.fetchone()
                        else:
                            cur.execute("UPDATE zsjos_business_task SET status='completed',completed_at=NOW() "
                                        "WHERE deleted=0 AND task_type='lead_follow_up_reminder' AND biz_id=1 AND assignee_id=1 AND status='pending' AND tenant_id=1")
                            cur.execute("UPDATE zsjos_business_task SET status='cancelled',cancelled_at=NOW(),cancel_reason='fixture' "
                                        "WHERE deleted=0 AND task_type='lead_follow_up_reminder' AND biz_id=2 AND status='pending' AND tenant_id=1")
                        conn.commit()
                    except pymysql.MySQLError as error:
                        errors.append(error.args[0]); conn.rollback()
                barrier.wait(timeout=15)
                if not reminder:
                    self.query(schema, "UPDATE zsjos_business_task SET status='pending',completed_at=NULL,cancelled_at=NULL,cancel_reason=NULL WHERE tenant_id=1")
                barrier.wait(timeout=15)
        with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
            list(pool.map(worker, (True, False)))
        self.record('task_row_lock_concurrency', status='REPRODUCED' if 1213 in errors else 'NOT_REPRODUCED',
                    rounds=30, mysql_error_codes=errors, schema=schema,
                    limitation='SQL-level synthetic task updates only; no actual Lead/claim transaction, outbox, or scheduler replay')

    def query_fixture(self, query_dir=None):
        schema = self.create('queries')
        for name in ('zsjos_lead', 'zsjos_order', 'zsjos_order_item', 'zsjos_lead_assignment_history',
                     'zsjos_performance_attribution', 'zsjos_performance_org',
                     'zsjos_order_supervisor_confirmation', 'zsjos_order_approval_round'):
            self.cli(schema, self.table(name))
        self.query(schema, "INSERT INTO zsjos_lead(id,lead_no,person_id,submitted_name,source_type,status,assignment_status,submitted_at,tenant_id,lead_category_label_snapshot) "
                   "VALUES(1,'LD-FIXTURE',1,'测试','internal_new_media','won','owned','2026-10-01',1,'测试分类')")
        cases = [('repurchase','inbound',100), ('first_purchase','inbound',200),
                 ('first_purchase','self',300), ('REPURCHASE','INBOUND',400),
                 ('first_purchase',None,0), ('first_purchase','self',0.01)]
        for i, (kind, group, amount) in enumerate(cases, 1):
            self.query(schema, "INSERT INTO zsjos_order(id,order_no,person_id,lead_id,formal_sales_user_id,order_type,status,total_amount,payable_amount,submitted_at,tenant_id) "
                       "VALUES(%s,%s,1,1,1,%s,'effective',%s,%s,'2026-10-02',1)", (i, 'FIXTURE-'+str(i), kind, amount, amount))
            self.query(schema, "INSERT INTO zsjos_performance_attribution(fact_type,fact_id,user_id,user_name,dept_id,center_id,lead_id,source_group,business_domain,org_source,tenant_id) "
                       "VALUES('ORDER',%s,1,'测试',10,10,1,%s,'sales','frozen',1)", (i, group))
            self.query(schema, "INSERT INTO zsjos_order_item(order_id,unit_price,payable_amount,product_ref,product_snapshot,tenant_id) "
                       "VALUES(%s,%s,%s,'P1','{\"name\":\"测试课程\"}',1)", (i, amount, amount))
        self.record('query_fixture', status='READY', schema=schema, orders=len(cases))
        assert self.query(schema, 'SELECT HEX(submitted_name) FROM zsjos_lead WHERE id=1')[0][0] == '测试'.encode().hex().upper()
        if query_dir is None:
            return
        root = Path(query_dir).resolve(strict=True)
        for i in range(1, 22):
            order_id = 100 + i
            self.query(schema, "INSERT INTO zsjos_order(id,order_no,person_id,lead_id,order_type,status,current_approval_round_id,total_amount,payable_amount,tenant_id) "
                       "VALUES(%s,%s,%s,NULL,'repurchase','pending_approval',%s,0,0,1)",
                       (order_id, 'PAGING-FIXTURE-' + str(i), order_id, order_id))
            # MySQL's PAD SPACE unique index treats '' and spaces as equal; exercise them in sequence.
            process_id = None if i <= 3 else '  ' if i == 4 else 'p' + str(i)
            self.query(schema, "INSERT INTO zsjos_order_approval_round(id,order_id,round_no,status,order_snapshot,process_instance_id,"
                       "process_definition_key,submitted_by_user_id,submitted_at,tenant_id) "
                       "VALUES(%s,%s,1,'pending','{}',%s,'fixture',1,NOW(),1)", (order_id, order_id, process_id))
            self.query(schema, "INSERT INTO zsjos_order_supervisor_confirmation(order_id,approval_round_id,task_definition_key,"
                       "requester_user_id,supervisor_user_id,parent_task_id,supervisor_task_id,request_reason,status,requested_at,tenant_id) "
                       "VALUES(%s,%s,'fixture',1,1,%s,%s,'fixture','pending',NOW(),1)",
                       (order_id, order_id, 'parent' + str(i), 'supervisor' + str(i)))
        files = sorted(root.glob('amounts-*.json')) + sorted(root.glob('supervisor-offset*.json'))
        assert len(files) == 17, 'Expected all 15 amount scope/groupings and both paging exports'
        pages = {}
        for path in files:
            exported = json.loads(path.read_text(encoding='utf-8'))
            with self.connection(schema) as conn, conn.cursor(pymysql.cursors.DictCursor) as cur:
                cur.execute(exported['sql'].replace('%', '%%').replace('?', '%s'), exported['parameters'])
                rows = cur.fetchall()
            if path.name.startswith('amounts-'):
                expected = Decimal('0') if 'scope2' in path.name else Decimal('1000.01')
                assert sum((row['amount'] for row in rows), Decimal(0)) == expected, path.name
                if path.name == 'amounts-source-scope0.json':
                    amounts = {row['group_key']: row['amount'] for row in rows}
                    assert amounts == {'self': Decimal('400.01'), 'inbound': Decimal('200'), 'unknown': Decimal('400')}
            else:
                pages[path.stem] = [row['id'] for row in rows]
            self.record(path.stem, status='PASS', schema=schema, rows=len(rows))
        assert len(pages['supervisor-offset0']) == 20 and len(pages['supervisor-offset20']) == 1
        assert not set(pages['supervisor-offset0']) & set(pages['supervisor-offset20'])
        mapper = (ROOT / 'backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/dal/mysql/order/SalesOrderApprovalRoundMapper.java').read_text(encoding='utf-8')
        annotation = re.search(r'@Select\(("SELECT DISTINCT r\.process_instance_id[\s\S]+?)\)\s*List<String> selectProcessInstanceIdsByKeyword', mapper)
        assert annotation
        sql = ''.join(json.loads(part) for part in re.findall(r'"(?:[^"\\]|\\.)*"', annotation[1]))
        sql = sql.replace('%', '%%').replace('#{tenantId}', '%s').replace('#{keyword}', '%s')
        ids = self.query(schema, sql, (1, 'PAGING-FIXTURE', 'PAGING-FIXTURE', 'PAGING-FIXTURE'))
        assert {row[0] for row in ids} == {'p' + str(i) for i in range(5, 22)}
        self.query(schema, "UPDATE zsjos_order_approval_round SET process_instance_id='' WHERE id=104 AND tenant_id=1")
        assert set(self.query(schema, sql, (1, 'PAGING-FIXTURE', 'PAGING-FIXTURE', 'PAGING-FIXTURE'))) == set(ids)
        assert not self.query(schema, sql, (1, 'no-match', 'no-match', 'no-match'))
        self.record('approval_keyword_null_blank_and_no_match', status='PASS', schema=schema, valid_ids=len(ids))

    def diagnostics(self):
        schema = self.create('diagnostics')
        for name in ('infra_file', 'system_social_user', 'bpm_form'):
            self.cli(schema, self.table(name))
        self.query(schema, "INSERT INTO infra_file(name,path,url,size) VALUES('fixture','fixture',%s,1)", ('x' * 1024,))
        self.query(schema, "INSERT INTO system_social_user(type,openid,raw_token_info,nickname,raw_user_info,code,tenant_id) "
                   "VALUES(30,%s,'{}','fixture','{}','fixture',1)", ('x' * 32,))
        for length, statement in (
                (1025, "INSERT INTO infra_file(name,path,url,size) VALUES('fixture','fixture',%s,1)"),
                (33, "INSERT INTO system_social_user(type,openid,raw_token_info,nickname,raw_user_info,code,tenant_id) "
                     "VALUES(30,%s,'{}','fixture','{}','fixture',1)")):
            try:
                self.query(schema, statement, ('x' * length,))
            except pymysql.err.DataError as error:
                assert error.args[0] == 1406
            else:
                raise AssertionError('Baseline must reject overlength input without silent truncation')
        for value in ('["field"]', '[{"field":"fixture"}]', '{broken', '{}', '[]'):
            self.query(schema, "INSERT INTO bpm_form(name,status,conf,fields,tenant_id) VALUES('fixture',0,'{}',%s,1)", (value,))
        source = (SQL / 'tools/exception_repair_diagnostics.sql').read_text(encoding='utf-8')
        output = self.cli(schema, source).stdout.decode('utf-8')
        assert 'INVALID\t1' in output and 'STRING\t1' in output and 'OBJECT\t1' in output
        assert self.query(schema, 'SELECT COUNT(*) FROM infra_file')[0][0] == 1
        assert self.query(schema, 'SELECT COUNT(*) FROM system_social_user')[0][0] == 1
        self.record('readonly_diagnostics_and_url_openid_boundaries', status='PASS', schema=schema,
                    url_characters=1024, openid_characters=32, json_shapes=5,
                    limitation='Synthetic baseline only; no production identity or URL input was supplied')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--container', required=True)
    parser.add_argument('--port', type=int, default=3306)
    parser.add_argument('--cases', default='numbers,tasks,hrm,v184')
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--query-dir', type=Path, help='SQL exported by PerformanceReportParserTest')
    args = parser.parse_args()
    checks = Checks(args.container, args.port)
    try:
        for case in args.cases.split(','):
            {'numbers': checks.numbers, 'tasks': checks.task_concurrency, 'hrm': checks.hrm, 'v184': checks.v184,
             'queries': lambda: checks.query_fixture(args.query_dir), 'diagnostics': checks.diagnostics}[case]()
    finally:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps({'schemas_retained': sorted(checks.created), 'results': checks.results},
                                         ensure_ascii=False, indent=2), encoding='utf-8')


if __name__ == '__main__':
    main()
