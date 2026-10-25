#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Authorized test-only adapter for the existing independent student name repair.

See docs/operations/student-name-backfill-20261021.md. Private exact plans are
required; no schema, ledger, Person, order or service lifecycle changes.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import sys

from transfer_student_20261015 import connect

ROOT = Path(__file__).resolve().parents[4]
sys.path.insert(0, str(ROOT / 'script/sql/mysql/tools'))
from student_name_backfill import candidates, repair, SOURCE


def private_plan(path, value):
    path = path.resolve()
    if path.is_relative_to(ROOT):
        raise ValueError('Evidence must be outside repository')
    path.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    payload = json.dumps(value, ensure_ascii=False, indent=2).encode('utf-8')
    fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(fd, 'wb') as stream:
        stream.write(payload)
        stream.flush()
        os.fsync(stream.fileno())
    return hashlib.sha256(payload).hexdigest()


def fixtures(c):
    # Temporary tables shadow live names only in this connection and disappear on close.
    with c.cursor() as q:
        q.execute('CREATE TEMPORARY TABLE zsjos_person (id BIGINT PRIMARY KEY,tenant_id BIGINT,deleted BIT(1),name VARCHAR(100)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4')
        q.execute('CREATE TEMPORARY TABLE zsjos_service_relation (id BIGINT PRIMARY KEY,person_id BIGINT,tenant_id BIGINT,deleted BIT(1),order_id BIGINT,student_name_snapshot VARCHAR(100),student_name_source VARCHAR(32),version INT DEFAULT 7,update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4')
        q.execute("INSERT INTO zsjos_person VALUES (1,1,0,' 核实姓名 '),(2,1,0,'另一姓名'),(3,1,0,'   '),(4,2,0,'其他租户')")
        q.execute("INSERT INTO zsjos_service_relation (id,person_id,tenant_id,deleted,order_id,student_name_snapshot,student_name_source,update_time) VALUES (1,1,1,0,NULL,NULL,NULL,'2020-01-01'),(2,2,1,0,NULL,NULL,NULL,'2020-01-01'),(3,3,1,0,NULL,NULL,NULL,'2020-01-01'),(4,1,1,0,NULL,'已有快照',NULL,'2020-01-01'),(5,1,1,0,99,NULL,NULL,'2020-01-01'),(6,4,2,0,NULL,NULL,NULL,'2020-01-01')")
    c.commit()
    with c.cursor() as q:
        plan = candidates(q)
    assert len(plan) == 2

    class FailingCursor:
        def __init__(self, cursor): self.cursor = cursor
        def __enter__(self): return self
        def __exit__(self, *args): self.cursor.close()
        def __getattr__(self, key): return getattr(self.cursor, key)
        def execute(self, sql, args=None):
            if sql.startswith('UPDATE zsjos_service_relation') and args[-1] == 2:
                return self.cursor.execute("SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='fixture failure'")
            return self.cursor.execute(sql, args)

    class FailingConnection:
        def __getattr__(self, key): return getattr(c, key)
        def cursor(self): return FailingCursor(c.cursor())

    try:
        repair(FailingConnection(), plan)
        raise AssertionError('Expected injected failure')
    except Exception as error:
        assert 'fixture failure' in str(error)
    with c.cursor() as q:
        q.execute('SELECT COUNT(*) n FROM zsjos_service_relation WHERE student_name_source IS NOT NULL')
        assert q.fetchone()['n'] == 0
    assert repair(c, plan) == 2
    assert repair(c, plan) == 0
    with c.cursor() as q:
        q.execute("SELECT COUNT(*) n FROM zsjos_service_relation WHERE update_time='2020-01-01' AND version=7")
        assert q.fetchone()['n'] == 6
        q.execute('SELECT HEX(student_name_snapshot) h FROM zsjos_service_relation WHERE id=4')
        assert q.fetchone()['h'] == '已有快照'.encode('utf-8').hex().upper()
    print('PASS MySQL rollback, recovery, replay, tenant/order/existing-name exclusions, HEX and audit preservation')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('command', choices=['fixture', 'prepare', 'apply'])
    parser.add_argument('--plan', type=Path)
    parser.add_argument('--sha256')
    args = parser.parse_args()
    marker = Path('/etc/zsjos/agent-environment')
    environment = os.environ.get('ZSJOS_AGENT_ENV', marker.read_text().strip() if marker.exists() else 'local')
    if environment != 'test':
        raise ValueError('Only test environment authorized')
    c = connect('zsjos-mysql-1')
    try:
        with c.cursor() as q:
            q.execute('SET NAMES utf8mb4')
            q.execute('SET SESSION innodb_lock_wait_timeout=5')
        if args.command == 'fixture':
            fixtures(c)
            return
        with c.cursor() as q:
            q.execute("SELECT (SELECT COUNT(*) FROM zsjos_schema_version WHERE version='V337') a,(SELECT COUNT(*) FROM zsjos_module_schema_version WHERE module_code='core' AND version='V337') b")
            assert q.fetchone() == {'a': 1, 'b': 1}, 'V337 required in both ledgers'
        if args.command == 'prepare':
            if not args.plan: raise ValueError('Private plan path required')
            with c.cursor() as q: rows = candidates(q)
            plan = {'database': 'zsjos', 'environment': 'test', 'tenant': 1, 'source': SOURCE, 'rows': rows}
            digest = private_plan(args.plan, plan)
            print(json.dumps({'count': len(rows), 'sha256': digest, 'plan': str(args.plan)}))
        else:
            if not args.plan or hashlib.sha256(args.plan.read_bytes()).hexdigest() != args.sha256:
                raise ValueError('Exact private plan SHA256 required')
            plan = json.loads(args.plan.read_text(encoding='utf-8'))
            assert (plan['database'], plan['environment'], plan['tenant'], plan['source']) == ('zsjos', 'test', 1, SOURCE)
            print(json.dumps({'changed': repair(c, plan['rows'])}))
    finally:
        c.close()


if __name__ == '__main__':
    main()
