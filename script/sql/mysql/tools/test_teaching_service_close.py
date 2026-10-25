#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""V339 controlled MySQL verification. Retains synthetic databases, never changes the application DB."""
import re
import subprocess
import uuid
from pathlib import Path
from test_core_baseline import Client

ROOT = Path(__file__).resolve().parents[4]
MIGRATION = ROOT / 'script/sql/mysql/migrations/V339__teaching_service_close_restore.sql'
PREFIX = 'tc_' + uuid.uuid4().hex[:8]


def create(name):
    command = ['docker', 'exec', '-i', 'yudao-mysql', 'sh', '-c',
               'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql --default-character-set=utf8mb4 -uroot -N -B --raw']
    result = subprocess.run(command, input=f'CREATE DATABASE `{name}` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;'.encode(), capture_output=True)
    assert result.returncode == 0, result.stderr.decode()
    return Client(name)


def definition(source, table):
    match = re.search(r'CREATE TABLE IF NOT EXISTS `?' + table + r'`?\s*\(.*?;\s*', source, re.S)
    assert match, table
    return match.group(0)


def fixture(suffix):
    client = create(PREFIX + '_' + suffix)
    # Actual committed V338 prerequisite schema, independent of the edited canonical schema.
    source = subprocess.check_output(['git', 'show', 'HEAD:script/sql/mysql/00-bootstrap-schema.sql'], cwd=ROOT).decode('utf-8')
    source += (ROOT / 'script/sql/mysql/02-bootstrap-zsjos-seed.sql').read_text(encoding='utf-8')
    for table in ['zsjos_service_relation', 'zsjos_delivery_class', 'system_menu', 'zsjos_schema_version', 'zsjos_module_schema_version']:
        client.query(definition(source, table))
    client.query("INSERT INTO zsjos_schema_version(version,description,checksum) VALUES('V338','fixture','fixture'); "
                 "INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version) VALUES('core','V338','fixture','fixture','fixture'); "
                 "INSERT INTO system_menu(name,permission,type,sort,parent_id,path,icon,component,status) VALUES('学员','zsjos:student:query-my',2,1,0,'my-students','','',0); "
                 "INSERT INTO zsjos_delivery_class(id,class_no,class_name,system_class,status,tenant_id) VALUES(101,'PENDING','待分班',1,'SERVING',1); "
                 "INSERT INTO zsjos_service_relation(id,person_id,status,owner_user_id,class_id,tenant_id,activated_at) VALUES(201,301,'active',401,101,1,NOW());")
    return client


def apply(client, success=True):
    return client.execute(MIGRATION.read_text(encoding='utf-8'), expect_success=success)


def ledger(client):
    return client.query("SELECT COUNT(*) FROM zsjos_schema_version WHERE version='V339'; SELECT COUNT(*) FROM zsjos_module_schema_version WHERE module_code='core' AND version='V339';")


def main():
    client = fixture('initial')
    before = client.query('SELECT id,person_id,status,owner_user_id,class_id FROM zsjos_service_relation;')
    apply(client)
    assert ledger(client) == '1\n1'
    assert client.query('SELECT id,person_id,status,owner_user_id,class_id FROM zsjos_service_relation;') == before
    assert client.query('SELECT teaching_closed+0,teaching_restored+0 FROM zsjos_service_relation;') == '0\t0'
    assert client.query('SELECT class_kind FROM zsjos_delivery_class WHERE id=101;') == 'PENDING'
    assert client.query("SELECT HEX(name) FROM system_menu WHERE permission='zsjos:student-service:close';") == '关闭服务'.encode().hex().upper()
    client.query("INSERT INTO zsjos_delivery_class(class_no,class_name,system_class,class_kind,homeroom_user_id,status,tenant_id) VALUES('EX401','异常结业',1,'EXCEPTION',401,'SERVING',1),('EX402','异常结业',1,'EXCEPTION',402,'SERVING',1);")
    assert client.query("SELECT HEX(class_name) FROM zsjos_delivery_class WHERE class_no='EX401';") == '异常结业'.encode().hex().upper()
    fail = client.execute("INSERT INTO zsjos_delivery_class(class_no,class_name,system_class,class_kind,homeroom_user_id,status,tenant_id) VALUES('EX401DUP','异常结业',1,'EXCEPTION',401,'SERVING',1);", expect_success=False)
    assert fail.returncode != 0
    fail = client.execute("INSERT INTO zsjos_delivery_class(class_no,class_name,system_class,class_kind,status,tenant_id) VALUES('PENDING2','待分班',1,'PENDING','SERVING',1);", expect_success=False)
    assert fail.returncode != 0
    apply(client)
    assert ledger(client) == '1\n1'
    assert client.query('SELECT COUNT(*) FROM zsjos_delivery_class;') == '3'
    print('PASS initial/replay, no historical closure/move, per-owner and pending uniqueness, Chinese HEX', flush=True)

    missing = fixture('missing')
    missing.query("DELETE FROM zsjos_module_schema_version WHERE version='V338';")
    assert apply(missing, False).returncode != 0
    assert ledger(missing) == '0\n0'
    missing.query("INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version) VALUES('core','V338','fixture','fixture','fixture');")
    apply(missing)
    assert ledger(missing) == '1\n1'
    print('PASS missing prerequisite leaves no success marker, recovery works', flush=True)

    partial = fixture('partial')
    partial.query("ALTER TABLE zsjos_service_relation ADD COLUMN teaching_closed bit(1) NOT NULL DEFAULT b'0' COMMENT '教学服务是否已关闭'; ALTER TABLE zsjos_delivery_class ADD COLUMN class_kind varchar(24) NOT NULL DEFAULT 'NORMAL' COMMENT 'NORMAL/PENDING/EXCEPTION';")
    apply(partial)
    apply(partial)
    assert ledger(partial) == '1\n1'
    print('PASS partial DDL recovery and replay', flush=True)

    failure = fixture('ledger_failure')
    failure.query("CREATE TRIGGER reject_v339 BEFORE INSERT ON zsjos_module_schema_version FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='controlled ledger failure';")
    assert apply(failure, False).returncode != 0
    assert ledger(failure) == '0\n0'
    assert failure.query("SELECT COUNT(*) FROM system_menu WHERE permission LIKE 'zsjos:student-service:%';") == '0'
    failure.query('DROP TRIGGER reject_v339;')
    apply(failure)
    assert ledger(failure) == '1\n1'
    print('PASS second-ledger failure rolls back permission metadata and both markers; recovery works', flush=True)
    # Compare the latest canonical declaration after its subsequent owned-default-class upgrade.
    client.query((ROOT / "script/sql/mysql/migrations/V343__planner_unscheduled_class.sql").read_text(encoding="utf-8"))
    # Canonical declarations must match the same columns/indexes produced by the migrations.
    canonical = create(PREFIX + '_canonical')
    source = (ROOT / 'script/sql/mysql/schema/core.sql').read_text(encoding='utf-8')
    for table in ['zsjos_service_relation', 'zsjos_delivery_class', 'zsjos_teaching_service_event']:
        canonical.query(definition(source, table))
    # V344 owns the later group index; this fixture intentionally stops at V343.
    signature = "SELECT table_name,column_name,column_type,is_nullable,COALESCE(column_default,'<NULL>'),extra,HEX(column_comment) FROM information_schema.columns WHERE table_schema=DATABASE() AND (table_name='zsjos_teaching_service_event' OR (table_name='zsjos_service_relation' AND column_name LIKE 'teaching_%') OR (table_name='zsjos_delivery_class' AND column_name IN ('class_kind','pending_guard','exception_guard','unscheduled_guard'))) ORDER BY table_name,column_name; SELECT table_name,index_name,non_unique,seq_in_index,column_name FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name IN ('zsjos_service_relation','zsjos_delivery_class','zsjos_teaching_service_event') AND index_name<>'idx_delivery_class_group' ORDER BY table_name,index_name,seq_in_index;"
    assert client.query(signature) == canonical.query(signature)
    print('PASS canonical / upgrade structure matches; retained databases: ' + PREFIX + '_*', flush=True)


if __name__ == '__main__':
    main()
