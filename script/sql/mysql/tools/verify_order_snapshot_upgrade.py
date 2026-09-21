#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Verify V272 against the baseline table in an isolated disposable MySQL container.
No development/shared database is modified. Container cleanup is owned by the test runner.
"""
import subprocess
import re
from pathlib import Path
import zsjos_db as db


def query(container, sql):
    result = subprocess.run(["docker", "exec", "-i", container, "mysql",
        "--default-character-set=utf8mb4", "-uroot", "--batch", "--raw", "--skip-column-names", "zsjos_test"],
        input=("SET NAMES utf8mb4;\n" + sql).encode("utf-8"), capture_output=True, check=True)
    return result.stdout.decode("utf-8").strip()


def execute(container):
    baseline = (db.SQL_ROOT / "00-bootstrap-schema.sql").read_text(encoding="utf-8") + "\n" + (db.SQL_ROOT / "02-bootstrap-zsjos-seed.sql").read_text(encoding="utf-8")
    for table in ("zsjos_order_supervisor_confirmation", "zsjos_schema_version", "zsjos_module_schema_version"):
        ddl = re.search(r"CREATE TABLE IF NOT EXISTS `" + table + r"`\s*\(.*?;", baseline, re.S)
        assert ddl, table
        query(container, ddl.group(0))
    query(container, "INSERT INTO zsjos_order_supervisor_confirmation(order_id,approval_round_id,task_definition_key,requester_user_id,supervisor_user_id,parent_task_id,supervisor_task_id,request_reason,status,requested_at,tenant_id) VALUES (1,1,'test',1,2,'test-parent','test-child','历史申请','pending',NOW(),1),(2,2,'test',3,4,'other-parent','other-child','其他租户','pending',NOW(),2)")
    before = query(container, "SELECT id,tenant_id,HEX(request_reason),status FROM zsjos_order_supervisor_confirmation ORDER BY id")
    migration = db.SQL_ROOT / "migrations" / "V272__order_actor_name_snapshots.sql"
    db.docker_mysql_file(container, migration)
    assert query(container, "SELECT COUNT(*) FROM zsjos_order_supervisor_confirmation WHERE requester_name_snapshot IS NULL AND supervisor_name_snapshot IS NULL") == "2"
    query(container, "UPDATE zsjos_order_supervisor_confirmation SET requester_name_snapshot='申请时姓名', supervisor_name_snapshot='指派时主管' WHERE order_id=1 AND tenant_id=1")
    expected = '申请时姓名'.encode('utf-8').hex().upper() + '\t' + '指派时主管'.encode('utf-8').hex().upper()
    assert query(container, "SELECT HEX(requester_name_snapshot),HEX(supervisor_name_snapshot) FROM zsjos_order_supervisor_confirmation WHERE order_id=1 AND tenant_id=1") == expected
    db.docker_mysql_file(container, migration)
    assert query(container, "SELECT id,tenant_id,HEX(request_reason),status FROM zsjos_order_supervisor_confirmation ORDER BY id") == before
    assert query(container, "SELECT HEX(requester_name_snapshot),HEX(supervisor_name_snapshot) FROM zsjos_order_supervisor_confirmation WHERE order_id=1 AND tenant_id=1") == expected
    assert query(container, "SELECT COUNT(*) FROM zsjos_schema_version WHERE version='V272'") == "1"
    assert query(container, "SELECT COUNT(*) FROM zsjos_module_schema_version WHERE module_code='core' AND version='V272'") == "1"
    assert query(container, "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_order_supervisor_confirmation' AND column_name IN ('requester_name_snapshot','supervisor_name_snapshot') AND character_set_name='utf8mb4' AND is_nullable='YES'") == "2"
    print("PASS: V272 upgrade, repeatability, tenant row preservation, version records and Chinese UTF-8 HEX")


if __name__ == '__main__':
    db.with_test_mysql(execute)
