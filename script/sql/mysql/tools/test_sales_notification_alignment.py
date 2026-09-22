#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Verify sales notification SQL in a retained isolated database on local Docker MySQL.

Default is verification only. --apply-local also backs up and synchronizes the same
reviewed configuration on the explicitly named local database. Never sends messages.
"""
import argparse
import datetime
import json
import re
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[4]
TABLES = ('system_tenant', 'system_notify_template', 'system_notify_rule',
          'system_menu', 'zsjos_schema_version', 'zsjos_lead_submitter_assist_request')
CONFIG = ('system_notify_template', 'system_notify_rule')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--container', default='yudao-mysql')
    parser.add_argument('--database', default='ruoyi-vue-pro')
    parser.add_argument('--backup-dir', required=True, type=Path)
    parser.add_argument('--apply-local', action='store_true')
    args = parser.parse_args()
    inspect = json.loads(subprocess.check_output(['docker', 'inspect', args.container]))[0]
    env = dict(value.split('=', 1) for value in inspect['Config']['Env'] if '=' in value)
    prefix = ['docker', 'exec', '-i', '-e', 'MYSQL_PWD=' + env.get('MYSQL_ROOT_PASSWORD', ''), args.container]

    def call(command, content=b''):
        result = subprocess.run(prefix + command, input=content, capture_output=True)
        if result.returncode:
            # Do not include the Docker environment/credential-bearing command in errors.
            raise RuntimeError(result.stderr.decode('utf-8'))
        return result.stdout

    def sql(database, query):
        return call(['mysql', '--default-character-set=utf8mb4', '-uroot', '-N', '--batch', '--raw', database],
                    ('SET NAMES utf8mb4;\n' + query).encode('utf-8')).decode('utf-8').strip()

    def expand(path):
        content = path.read_text(encoding='utf-8')
        return re.sub(r'^SOURCE ([^;]+);', lambda match: expand(ROOT / match[1]), content, flags=re.M)

    def snapshot(database, table, predicate='1=1'):
        return sql(database, f'SELECT * FROM {table} WHERE {predicate} ORDER BY id;')

    def preserved(database, table, predicate='1=1'):
        if table == 'system_notify_template':
            predicate += " AND code<>'ZSJOS_LEAD_SOURCE_LINKED_WECOM'"
        return snapshot(database, table, predicate)

    args.backup_dir.mkdir(parents=True, exist_ok=False)
    # The isolated clone has no business rows; configuration backups remain outside the repository.
    for table in TABLES:
        options = ['--no-data'] if table == 'zsjos_lead_submitter_assist_request' else []
        data = call(['mysqldump', '--default-character-set=utf8mb4', '-uroot', '--single-transaction',
                     '--skip-add-drop-table', '--no-tablespaces', '--set-gtid-purged=OFF',
                     *options, args.database, table])
        (args.backup_dir / (table + '.sql')).write_bytes(data)
    clone = 'sales_notify_verify_' + datetime.datetime.now().strftime('%Y%m%d%H%M%S')
    sql(args.database, f'CREATE DATABASE `{clone}` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;')
    for table in TABLES:
        call(['mysql', '--default-character-set=utf8mb4', '-uroot', clone],
             (args.backup_dir / (table + '.sql')).read_bytes())
    originals = {table: snapshot(clone, table) for table in CONFIG}
    unchanged = {table: preserved(clone, table) for table in CONFIG}
    target_fields = "SELECT id,name,code,nickname,scene_code,channel_code,title,status,remark,creator,create_time,deleted " \
                    "FROM system_notify_template WHERE code='ZSJOS_LEAD_SOURCE_LINKED_WECOM';"
    original_target_fields = sql(clone, target_fields)
    # IDs can exceed GROUP_CONCAT's default size; use a high watermark for preservation assertions.
    maxima = {table: sql(clone, f'SELECT COALESCE(MAX(id),0) FROM {table};') for table in CONFIG}
    migration = expand(ROOT / 'script/sql/mysql/migrations/V274__lead_submitter_assist_bidirectional.sql')
    alignment = expand(ROOT / 'script/sql/mysql/sales-notification-alignment.sql')
    sql(clone, migration)
    for table in CONFIG:
        assert unchanged[table] == preserved(clone, table, 'id <= ' + maxima[table]), table + ' changed existing rows'
    assert original_target_fields == sql(clone, target_fields)
    once = {table: snapshot(clone, table) for table in CONFIG}
    sql(clone, migration)
    for table in CONFIG:
        assert once[table] == snapshot(clone, table), table + ' not repeatable'

    def verify(database):
        assert sql(database, "SELECT COUNT(*) FROM system_notify_template w JOIN system_notify_template a "
                   "ON a.code='ZSJOS_LEAD_SOURCE_LINKED' AND a.deleted=0 "
                   "WHERE w.code='ZSJOS_LEAD_SOURCE_LINKED_WECOM' AND w.deleted=0 "
                   "AND w.creator='migration-V177' AND w.updater IN ('migration-V177','sales-notify-alignment') "
                   "AND (HEX(w.summary)<>HEX(a.summary) OR HEX(w.content)<>HEX(a.content) "
                   "OR CAST(w.params AS JSON)<>CAST(a.params AS JSON))") == '0', 'Default WeCom wording was not aligned'
        assert sql(database, "SELECT COUNT(*) FROM system_notify_rule WHERE deleted=0 AND tenant_id=1 "
                   "AND scene_code='zsjos.lead.submitter_assist_replied' AND channel_code='wecom'") == '1'
        assert sql(database, "SELECT COUNT(*) FROM system_notify_rule WHERE deleted=0 AND tenant_id=1 "
                   "AND scene_code='zsjos.payment.paid'") == '2'
        title_hex = sql(database, "SELECT HEX(title) FROM system_notify_template "
                       "WHERE code='ZSJOS_PAYMENT_PAID_WECOM' AND deleted=0")
        assert title_hex == '在线收款到账待补录'.encode('utf-8').hex().upper()
        assert sql(database, "SELECT COUNT(*) FROM system_notify_rule r LEFT JOIN system_notify_template t "
                   "ON r.template_id=t.id WHERE r.creator='sales-notify-alignment' "
                   "AND (t.id IS NULL OR r.scene_code<>t.scene_code OR r.channel_code<>t.channel_code)") == '0'

    verify(clone)
    # Exact parity, excluding generated IDs/timestamps, for newly added configuration.
    projection = "SELECT scene_code,channel_code,status,recipient_roles,specified_user_ids,action_type," \
                 "timing_stage,timing_offset_minutes,tenant_id FROM system_notify_rule " \
                 "WHERE creator='sales-notify-alignment' ORDER BY scene_code,channel_code,tenant_id,name;"
    expected = sql(clone, projection)
    target_content = "SELECT summary,content,params FROM system_notify_template WHERE code='ZSJOS_LEAD_SOURCE_LINKED_WECOM'"
    expected_content = sql(clone, target_content)

    # Separate synthetic fixtures exercise preserved disabled/custom scenes and multiple reminder rules.
    sql(clone, """INSERT INTO system_notify_template(name,code,nickname,scene_code,channel_code,title,summary,content,type,params,status)
        VALUES('test','SALES_VERIFY_FIXTURE','test','zsjos.lead.test_fixture','in_app','test','test','test',2,'[]',0);
        INSERT INTO system_notify_rule(name,scene_code,channel_code,template_id,recipient_roles,specified_user_ids,
          action_type,timing_stage,timing_offset_minutes,status,tenant_id,creator)
        SELECT 'test',scene_code,'in_app',id,'["owner"]','[]','none','advance',30,0,1,'synthetic'
          FROM system_notify_template WHERE code='SALES_VERIFY_FIXTURE';
        INSERT INTO system_notify_rule(name,scene_code,channel_code,template_id,recipient_roles,specified_user_ids,
          action_type,timing_stage,timing_offset_minutes,status,tenant_id,creator)
        SELECT 'test2',scene_code,'in_app',id,'["direct_leader"]','[7]','message_detail','overdue',60,1,1,'synthetic'
          FROM system_notify_template WHERE code='SALES_VERIFY_FIXTURE';""")
    sql(clone, alignment)
    assert sql(clone, "SELECT COUNT(*) FROM system_notify_rule WHERE scene_code='zsjos.lead.test_fixture' "
               "AND channel_code='wecom' AND tenant_id=1") == '2'
    sql(clone, "UPDATE system_notify_rule SET status=1,updater='administrator',recipient_roles='[]' "
               "WHERE scene_code='zsjos.lead.test_fixture' AND channel_code='wecom';")
    preserved_fixture = snapshot(clone, 'system_notify_rule', "scene_code='zsjos.lead.test_fixture'")
    sql(clone, alignment)
    assert preserved_fixture == snapshot(clone, 'system_notify_rule', "scene_code='zsjos.lead.test_fixture'")
    sql(clone, "UPDATE system_notify_template SET content='administrator content',updater='administrator' "
               "WHERE code='ZSJOS_LEAD_SOURCE_LINKED_WECOM';")
    custom_template = snapshot(clone, 'system_notify_template', "code='ZSJOS_LEAD_SOURCE_LINKED_WECOM'")
    sql(clone, alignment)
    assert custom_template == snapshot(clone, 'system_notify_template', "code='ZSJOS_LEAD_SOURCE_LINKED_WECOM'")

    if args.apply_local:
        # Recheck unchanged prerequisites immediately before the scoped live write.
        for table in CONFIG:
            assert originals[table] == snapshot(args.database, table), 'Configuration changed; repeat inspection/backup'
        sql(args.database, alignment)
        verify(args.database)
        assert expected == sql(args.database, projection), 'Controlled/local rule projection mismatch'
        for table in CONFIG:
            assert unchanged[table] == preserved(args.database, table, 'id <= ' + maxima[table])
        assert original_target_fields == sql(args.database, target_fields)
        assert expected_content == sql(args.database, target_content)
        live_once = {table: snapshot(args.database, table) for table in CONFIG}
        sql(args.database, alignment)
        assert live_once == {table: snapshot(args.database, table) for table in CONFIG}
        print('PASS local scoped alignment, existing configuration preserved, repeatability and controlled parity')
    print('PASS controlled V274 replay, UTF-8 HEX, template/rule links, multi-rule copying and disabled/custom preservation')
    print('Retained verification database:', clone)
    print('Backup directory:', args.backup_dir)


if __name__ == '__main__':
    main()
