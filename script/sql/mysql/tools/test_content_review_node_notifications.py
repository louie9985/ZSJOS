#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Retained MySQL fixtures and tenant-1 audit; --apply-local updates backed-up local defaults only."""
import json
import argparse
import tempfile
import uuid
from pathlib import Path
from test_core_baseline import Client, create_database
import zsjos_db as db
from order_history_repair import connect

ROOT = Path(__file__).resolve().parents[4]
SQL = ROOT / 'script/sql/mysql/migrations/V338__content_review_node_notifications.sql'
SCENES = {
    'DIRECTOR_COMPLETED': ('director_completed', '编导审核通过'),
    'DIRECTOR_REJECTED': ('director_rejected', '编导审核驳回'),
    'FINAL_COMPLETED': ('final_completed', '终审通过'),
    'FINAL_REJECTED': ('final_rejected', '终审驳回'),
}


def markers(c):
    return c.query("SELECT HEX(version),HEX(checksum) FROM zsjos_schema_version WHERE version='V338' "
                   "UNION ALL SELECT HEX(version),HEX(checksum) FROM zsjos_module_schema_version "
                   "WHERE module_code='core' AND version='V338'")


def templates(c):
    return c.query('SELECT id,code,HEX(name),HEX(nickname),HEX(title),HEX(summary),HEX(content),'
                   'params,status,updater FROM system_notify_template ORDER BY id')


def apply(c, success=True):
    result = c.execute(SQL.read_text(encoding='utf-8'), expect_success=success)
    if not success:
        assert result.returncode != 0 or b'ERROR' in result.stderr


def fixture():
    target = Client('ruoyi-vue-pro')
    c = create_database('review_notify_' + uuid.uuid4().hex[:10])
    for table in ('system_notify_template', 'zsjos_schema_version', 'zsjos_module_schema_version'):
        c.query(f'CREATE TABLE `{table}` LIKE `{target.database}`.`{table}`')
    for suffix, (scene, title) in SCENES.items():
        for channel in ('in_app', 'wecom'):
            code = 'ZSJ_N269_ZSJOS_CONTENT_REVIEW_' + suffix + ('_WECOM' if channel == 'wecom' else '')
            fields = [title + ('（企微）' if channel == 'wecom' else ''), code, '中仕健消息中心', 'zsjos.content_review.' + scene,
                      channel, title, title, '内容审核批次{{batchNo}}：' + title + '。']
            c.query('INSERT INTO system_notify_template(name,code,nickname,scene_code,channel_code,title,summary,content,'
                    'type,params,status,remark,creator,updater) VALUES('
                    + ','.join(db.sql_literal(value) for value in fields)
                    + ",2,'[\"batchNo\"]',0,'V269 notification defaults','fixture','fixture')")
    # One administrator edit must remain byte-for-byte unchanged even on a system template code.
    c.query("UPDATE system_notify_template SET title='管理员自定义标题' "
            "WHERE code='ZSJ_N269_ZSJOS_CONTENT_REVIEW_FINAL_REJECTED_WECOM'")
    before = templates(c)
    apply(c, False)
    assert before == templates(c) and not markers(c)
    c.query("INSERT INTO zsjos_schema_version(version,description,checksum) VALUES('V269','fixture',REPEAT('a',64))")
    c.query("INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version) "
            "VALUES('core','V269','fixture',REPEAT('a',64),'fixture')")
    c.query("CREATE TRIGGER fail_v338_ledger BEFORE INSERT ON zsjos_module_schema_version "
            "FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='fixture ledger failure'")
    apply(c, False)
    assert before == templates(c) and not markers(c)
    c.query('DROP TRIGGER fail_v338_ledger')
    c.query("CREATE TRIGGER fail_v338_postcondition BEFORE UPDATE ON system_notify_template "
            "FOR EACH ROW SET NEW.summary='fixture corrupt summary'")
    apply(c, False)
    assert before == templates(c) and not markers(c)
    c.query('DROP TRIGGER fail_v338_postcondition')
    apply(c)
    assert len(markers(c).splitlines()) == 2
    assert c.query("SELECT COUNT(*) FROM system_notify_template WHERE updater='migration-V338'") == '7'
    assert c.query("SELECT HEX(title) FROM system_notify_template "
                   "WHERE code='ZSJ_N269_ZSJOS_CONTENT_REVIEW_FINAL_REJECTED_WECOM'") == '管理员自定义标题'.encode().hex().upper()
    assert c.query("SELECT HEX(title) FROM system_notify_template "
                   "WHERE code='ZSJ_N269_ZSJOS_CONTENT_REVIEW_FINAL_COMPLETED'") == '内容审批 · 终审通过'.encode().hex().upper()
    after = (templates(c), markers(c))
    apply(c)
    assert after == (templates(c), markers(c))
    # Recover a partially applied presentation without treating existing version records as proof.
    title = SCENES['DIRECTOR_COMPLETED'][1]
    c.query("UPDATE system_notify_template SET name={0},nickname='中仕健消息中心',title={0},summary={0},"
            "content={1},params=JSON_ARRAY('batchNo') WHERE code='ZSJ_N269_ZSJOS_CONTENT_REVIEW_DIRECTOR_COMPLETED'"
            .format(db.sql_literal(title), db.sql_literal('内容审核批次{{batchNo}}：' + title + '。')))
    old_markers = markers(c)
    apply(c)
    assert markers(c) == old_markers and templates(c) == after[0]
    print('PASS MySQL initial/replay/prerequisite/postcondition/ledger rollback/partial recovery/custom defaults/Chinese HEX')
    print('Retained fixture database:', c.database)


def audit():
    c = Client('ruoyi-vue-pro')
    scenes = ','.join(db.sql_literal('zsjos.content_review.' + value[0]) for value in SCENES.values())
    rows = c.query('SELECT id,scene_code,channel_code,status,recipient_roles,JSON_LENGTH(specified_user_ids) '
                   f'FROM system_notify_rule WHERE tenant_id=1 AND deleted=0 AND scene_code IN ({scenes}) ORDER BY id')
    counts = c.query('SELECT scene_code,COUNT(*) FROM system_notify_rule WHERE tenant_id=1 AND deleted=0 '
                    f"AND status=0 AND channel_code='in_app' AND scene_code IN ({scenes}) GROUP BY scene_code")
    assert len(counts.splitlines()) == 4 and all(row.endswith('\t1') for row in counts.splitlines()), counts
    print('PASS read-only tenant=1: exactly one enabled in_app rule per node result scene')
    print(json.dumps({'rules': rows.splitlines()}, ensure_ascii=True))


def apply_local():
    """Authorized local non-destructive default update; remote/shared targets are unsupported."""
    codes = ['ZSJ_N269_ZSJOS_CONTENT_REVIEW_' + suffix + channel
             for suffix in SCENES for channel in ('', '_WECOM')]
    names = ','.join(db.sql_literal(code) for code in codes)

    def snapshot():
        connection = connect(database='ruoyi-vue-pro')
        try:
            with connection.cursor() as q:
                q.execute('SET NAMES utf8mb4')
                q.execute(f'SELECT * FROM system_notify_template WHERE code IN ({names}) AND deleted=0 ORDER BY id')
                templates_found = q.fetchall()
                q.execute('SELECT * FROM system_notify_rule WHERE tenant_id=1 ORDER BY id')
                rules = q.fetchall()
                q.execute('SELECT COUNT(*) AS count_value FROM system_notify_message WHERE tenant_id=1')
                messages = q.fetchone()['count_value']
            return templates_found, rules, messages
        finally:
            connection.close()

    before, rules, messages = snapshot()
    assert len(before) == 8, 'Inspect missing/duplicate templates before applying local migration'
    backup = Path(tempfile.mkdtemp(prefix='content-review-v338-')) / 'default-templates-before.json'
    backup.write_text(json.dumps(before, ensure_ascii=False, indent=2,
                                default=lambda value: value.hex() if isinstance(value, bytes) else str(value)), encoding='utf-8')
    print('Local default template backup:', backup, flush=True)
    target = Client('ruoyi-vue-pro')
    apply(target)
    after, after_rules, after_messages = snapshot()
    assert rules == after_rules and messages == after_messages
    mutable = {'name', 'nickname', 'title', 'summary', 'content', 'params', 'updater', 'update_time'}
    for old, new in zip(before, after):
        assert {k: v for k, v in old.items() if k not in mutable} == {k: v for k, v in new.items() if k not in mutable}
    assert len(markers(target).splitlines()) == 2
    assert target.query("SELECT HEX(title) FROM system_notify_template WHERE code='ZSJ_N269_ZSJOS_CONTENT_REVIEW_FINAL_COMPLETED' AND deleted=0") == '内容审批 · 终审通过'.encode().hex().upper()
    stable = (templates(target), markers(target))
    apply(target)
    assert stable == (templates(target), markers(target))
    print('PASS local V338 default update/replay/Chinese HEX; tenant-1 rules and historical message count unchanged')
    print('Local default template backup:', backup)


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--apply-local', action='store_true', help='Apply only to inspected local development defaults')
    args = parser.parse_args()
    fixture()
    audit()
    if args.apply_local:
        apply_local()
