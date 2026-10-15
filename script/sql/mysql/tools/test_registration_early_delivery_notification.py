# -*- coding: utf-8 -*-
"""Retained isolated MySQL verification; never changes the development configuration."""
import datetime
from test_positioning_single_source import query, ROOT

sql = (ROOT / 'script/sql/mysql/registration-early-delivery-notification.sql').read_text(encoding='utf-8')
db = 'registration_notify_' + datetime.datetime.now().strftime('%Y%m%d%H%M%S%f')
query('ruoyi-vue-pro', f'CREATE DATABASE {db} CHARACTER SET utf8mb4;')
for table in ['system_notify_template', 'system_notify_rule', 'system_tenant']:
    query(db, f'CREATE TABLE {table} LIKE ruoyi_vue_placeholder.{table};'.replace('ruoyi_vue_placeholder', '\x60ruoyi-vue-pro\x60'))
try:
    query(db, sql)
    raise AssertionError('Missing tenant must fail')
except RuntimeError:
    assert query(db, 'SELECT COUNT(*) FROM system_notify_template;') == '0\n'
query(db, "INSERT INTO system_tenant (id,name,contact_name,contact_mobile,status,package_id,expire_time,account_count,creator,updater,deleted) VALUES (1,'Fixture','Fixture','',0,0,'2099-01-01',10,'fixture','fixture',b'0');")
query(db, sql)
assert query(db, 'SELECT COUNT(*) FROM system_notify_template; SELECT COUNT(*) FROM system_notify_rule;').splitlines() == ['1','1']
value = query(db, "SELECT title,HEX(title) FROM system_notify_template;").strip().split('\t')
assert value == ['提前交付已停止', '提前交付已停止'.encode().hex().upper()]
query(db, sql)
assert query(db, 'SELECT COUNT(*) FROM system_notify_template; SELECT COUNT(*) FROM system_notify_rule;').splitlines() == ['1','1']
# Partial template-only setup: a second empty rule table with the existing template is recoverable.
partial = db + '_partial'
query(db, f'CREATE DATABASE {partial} CHARACTER SET utf8mb4;')
for table in ['system_notify_template','system_notify_rule','system_tenant']:
    query(partial, f'CREATE TABLE {table} LIKE {db}.{table};')
    if table != 'system_notify_rule': query(partial, f'INSERT INTO {table} SELECT * FROM {db}.{table};')
query(partial, sql)
assert query(partial, 'SELECT COUNT(*) FROM system_notify_rule;').strip() == '1'
# Administrator disabling is never undone by replay.
query(partial, 'UPDATE system_notify_rule SET status=1 WHERE tenant_id=1;')
query(partial, sql)
assert query(partial, 'SELECT status FROM system_notify_rule;').strip() == '1'
print('PASS initial, replay, missing prerequisite, partial recovery, admin configuration and UTF-8 HEX;', db, partial)
