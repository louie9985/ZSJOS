#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""V349 metadata verification on retained isolated MySQL schemas; no application DB writes."""
import json
import uuid
from test_teaching_service_close import create, definition, ROOT

PATH = ROOT / 'script/sql/mysql/migrations/V349__teaching_service_closed_notification.sql'
PREFIX = 'cn_' + uuid.uuid4().hex[:8]
SCENE = 'zsjos.student.service_closed'
CODE = 'ZSJOS_TEACHING_SERVICE_CLOSED'


def fixture(suffix):
    c = create(PREFIX + '_' + suffix)
    source = (ROOT / 'script/sql/mysql/schema/core.sql').read_text(encoding='utf-8')
    source += (ROOT / 'script/sql/mysql/02-bootstrap-zsjos-seed.sql').read_text(encoding='utf-8')
    for table in ['system_tenant', 'system_notify_template', 'system_notify_rule', 'zsjos_schema_version', 'zsjos_module_schema_version']:
        c.query(definition(source, table))
    c.query("INSERT INTO system_tenant(id,name,contact_name,package_id,expire_time,account_count) VALUES(1,'测试租户','测试联系人',0,'2099-01-01',1);"
            "INSERT INTO zsjos_schema_version(version,description,checksum) VALUES('V348','fixture','fixture');"
            "INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version) VALUES('core','V348','fixture','fixture','fixture');")
    return c


def versions(c):
    return c.query("SELECT COUNT(*) FROM zsjos_schema_version WHERE version='V349';"
                   "SELECT COUNT(*) FROM zsjos_module_schema_version WHERE module_code='core' AND version='V349';")


def apply(c, success=True):
    return c.execute(PATH.read_text(encoding='utf-8'), expect_success=success)


def main():
    c = fixture('initial')
    apply(c)
    assert versions(c) == '1\n1'
    assert c.query(f"SELECT COUNT(*) FROM system_notify_template WHERE code='{CODE}' AND deleted=0;") == '1'
    assert c.query(f"SELECT channel_code,status,action_type,recipient_roles FROM system_notify_rule WHERE tenant_id=1 AND scene_code='{SCENE}';") == 'in_app\t0\tbusiness_detail\t["delivery_supervisor"]'
    assert c.query(f"SELECT HEX(name) FROM system_notify_template WHERE code='{CODE}';") == '学员教学服务关闭'.encode().hex().upper()
    params = json.loads(c.query(f"SELECT params FROM system_notify_template WHERE code='{CODE}';"))
    assert params == ['student.name', 'course.name', 'planner.name', 'close.reason', 'close.time']
    before = c.query('SELECT * FROM system_notify_template; SELECT * FROM system_notify_rule;')
    apply(c)
    assert before == c.query('SELECT * FROM system_notify_template; SELECT * FROM system_notify_rule;')
    c.query(f"UPDATE system_notify_rule SET status=1,name='管理员自定义',recipient_roles='[]',specified_user_ids='[9001]' WHERE scene_code='{SCENE}';"
            f"UPDATE system_notify_template SET title='管理员自定义标题',status=1 WHERE code='{CODE}';")
    before = c.query('SELECT * FROM system_notify_template; SELECT * FROM system_notify_rule;')
    apply(c)
    assert before == c.query('SELECT * FROM system_notify_template; SELECT * FROM system_notify_rule;')
    print('PASS initial/replay, UTF-8 HEX, tenant-1 default, disabled/custom rules and templates preserved', flush=True)

    missing = fixture('missing')
    missing.query("DELETE FROM zsjos_module_schema_version WHERE version='V348';")
    assert apply(missing, False).returncode != 0
    assert versions(missing) == '0\n0'
    assert missing.query('SELECT COUNT(*) FROM system_notify_template;') == '0'
    print('PASS prerequisite failure leaves no metadata or success marker', flush=True)

    failed = fixture('failure')
    failed.query("CREATE TRIGGER reject_close_notify BEFORE INSERT ON zsjos_module_schema_version FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='controlled second ledger failure';")
    assert apply(failed, False).returncode != 0
    assert versions(failed) == '0\n0'
    assert failed.query('SELECT COUNT(*) FROM system_notify_template; SELECT COUNT(*) FROM system_notify_rule;') == '0\n0'
    failed.query('DROP TRIGGER reject_close_notify;')
    apply(failed)
    assert versions(failed) == '1\n1'
    print('PASS transactional metadata / dual-ledger rollback and recovery', flush=True)

    partial = fixture('partial')
    apply(partial)
    markers = partial.query('SELECT * FROM zsjos_schema_version; SELECT * FROM zsjos_module_schema_version;')
    partial.query('DELETE FROM system_notify_rule;')
    apply(partial)
    assert partial.query('SELECT COUNT(*) FROM system_notify_rule;') == '1'
    assert markers == partial.query('SELECT * FROM zsjos_schema_version; SELECT * FROM zsjos_module_schema_version;')
    print('PASS partial metadata recovery preserves installed ledger identities; retained ' + PREFIX + '_*', flush=True)


if __name__ == '__main__':
    main()
