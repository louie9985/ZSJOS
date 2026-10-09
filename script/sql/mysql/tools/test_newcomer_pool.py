# -*- coding: utf-8 -*-
"""V295 checks in retained isolated MySQL schemas. Never changes development data.

Run: python script/sql/mysql/tools/test_newcomer_pool.py [--fresh]
Uses the existing yudao-mysql container; creates only uniquely named test schemas.
"""
import datetime
import re
import sys
from concurrent.futures import ThreadPoolExecutor
from test_calendar_notifications import query, table, ROOT, SQL

MIGRATION = (SQL / 'migrations/V295__newcomer_priority_dispatch.sql').read_text(encoding='utf-8')
PREFIX = 'newcomer_' + datetime.datetime.now().strftime('%Y%m%d%H%M%S')
PREREQ = "INSERT INTO zsjos_schema_version(version,description,checksum) VALUES ('V294','fixture','fixture'); INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version) VALUES ('core','V294','fixture','fixture','fixture');"
TABLE_NAMES = ['zsjos_newcomer_pool_config', 'zsjos_newcomer_pool_daily']


def versions(db):
    return query(db, "SELECT version,checksum FROM zsjos_schema_version WHERE version='V295'; SELECT version,checksum FROM zsjos_module_schema_version WHERE module_code='core' AND version='V295';")[0]


def signature(db):
    return query(db, "SELECT table_name,column_name,column_type,is_nullable,COALESCE(column_default,'NULL'),extra,HEX(column_comment) FROM information_schema.columns WHERE table_schema=DATABASE() AND (table_name IN ('zsjos_newcomer_pool_config','zsjos_newcomer_pool_daily') OR (table_name='zsjos_lead_assignment_history' AND column_name='newcomer_priority')) ORDER BY table_name,ordinal_position; SELECT table_name,index_name,non_unique,seq_in_index,column_name FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name IN ('zsjos_newcomer_pool_config','zsjos_newcomer_pool_daily') ORDER BY table_name,index_name,seq_in_index;")[0]


def mapper_sql(name, method):
    path = ROOT / 'backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/dal/mysql/lead' / name
    source = path.read_text(encoding='utf-8')
    return re.search(r'@Insert\("""(.*?)"""\)\s+int ' + method, source, re.S).group(1)


def render(sql, **params):
    for key, value in params.items():
        sql = sql.replace('#{' + key + '}', str(value))
    assert '#{' not in sql
    return sql + ';'


def scoped():
    expected = None
    for case in ['initial', 'partial', 'prerequisite', 'wrong_index', 'wrong_type', 'ledger_failure', 'existing_marker']:
        db = PREFIX + '_' + case
        query(None, f'CREATE DATABASE `{db}` CHARACTER SET utf8mb4;')
        history = re.sub(r"\s*`newcomer_priority`[^\n]+\n", '\n', table('zsjos_lead_assignment_history'))
        setup = history + table('system_menu') + table('system_role_menu') + table('zsjos_schema_version') + table('zsjos_module_schema_version')
        setup += "INSERT INTO system_menu(id,name,permission,type,parent_id) VALUES (900001,'下属销售','zsjos:subordinate-sales:query',2,900000);"
        if case != 'prerequisite': setup += PREREQ
        if case in ['partial', 'wrong_index', 'wrong_type']:
            setup += table(TABLE_NAMES[0])
        if case == 'wrong_index': setup += 'ALTER TABLE zsjos_newcomer_pool_config DROP INDEX uk_tenant_user;'
        if case == 'wrong_type': setup += 'ALTER TABLE zsjos_newcomer_pool_config MODIFY daily_quota varchar(20) NOT NULL;'
        if case == 'ledger_failure': setup += "CREATE TRIGGER reject_newcomer_ledger BEFORE INSERT ON zsjos_module_schema_version FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='injected failure';"
        if case == 'existing_marker': setup += "INSERT INTO zsjos_schema_version(version,description,checksum) VALUES ('V295','fixture','preserve'); INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version) VALUES ('core','V295','fixture','preserve','fixture');"
        query(db, setup)
        before = versions(db)
        _, error = query(db, MIGRATION, force=True)
        repairs = {'prerequisite': PREREQ, 'wrong_index': 'ALTER TABLE zsjos_newcomer_pool_config ADD UNIQUE KEY uk_tenant_user(tenant_id,user_id);', 'wrong_type': 'ALTER TABLE zsjos_newcomer_pool_config MODIFY daily_quota int NOT NULL;', 'ledger_failure': 'DROP TRIGGER reject_newcomer_ledger;'}
        if case in repairs:
            assert 'ERROR' in error and versions(db) == before, (case, error)
            query(db, repairs[case]); query(db, MIGRATION)
        else:
            assert not error, error
        if expected is None: expected = signature(db)
        assert signature(db) == expected
        ledger = versions(db)
        assert len(ledger.splitlines()) == 2
        query(db, MIGRATION)
        assert versions(db) == ledger and signature(db) == expected
        assert query(db, "SELECT HEX(name) FROM system_menu WHERE permission='zsjos:subordinate-sales:newcomer-pool';")[0] == '配置新手池'.encode().hex().upper()
        assert query(db, 'SELECT COUNT(*) FROM system_role_menu;')[0] == '0'
        print('PASS', case, 'schema/repeat/recovery/ledger/UTF-8/no-role-grants', flush=True)
    increment = mapper_sql('NewcomerPoolDailyMapper.java', 'increment')
    statement = render(increment, userId=10, date="'2026-10-09'", tenantId=1)
    with ThreadPoolExecutor(max_workers=8) as pool:
        list(pool.map(lambda _: query(db, statement), range(32)))
    query(db, render(increment, userId=10, date="'2026-10-10'", tenantId=1))
    query(db, render(increment, userId=10, date="'2026-10-09'", tenantId=2))
    assert query(db, 'SELECT tenant_id,business_date,accepted_count FROM zsjos_newcomer_pool_daily ORDER BY tenant_id,business_date;')[0].splitlines() == ['1\t2026-10-09\t32', '1\t2026-10-10\t1', '2\t2026-10-09\t1']
    save = mapper_sql('NewcomerPoolConfigMapper.java', 'save')
    for enabled, quota in [(1, 10), (0, 10), (1, 2)]:
        query(db, render(save, tenantId=1, userId=10, enabled=enabled, dailyQuota=quota, operator="'fixture'"))
    assert query(db, 'SELECT enabled+0,daily_quota FROM zsjos_newcomer_pool_config;')[0] == '1\t2'
    assert query(db, "SELECT accepted_count FROM zsjos_newcomer_pool_daily WHERE tenant_id=1 AND user_id=10 AND business_date='2026-10-09';")[0] == '32'
    print('PASS real mapper SQL concurrent increments / tenant-date isolation / config retains usage', flush=True)
    return expected


def fresh(expected):
    def expand(path):
        return re.sub(r'^SOURCE\s+([^;]+);', lambda m: expand(ROOT / m.group(1)), path.read_text(encoding='utf-8-sig'), flags=re.M | re.I)
    db = PREFIX + '_fresh'
    query(None, f'CREATE DATABASE `{db}` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;')
    query(db, expand(SQL / 'bootstrap.sql'))
    applied = set(query(db, 'SELECT version FROM zsjos_schema_version;')[0].splitlines())
    for path in sorted((SQL / 'migrations').glob('V*__*.sql')):
        if path.name.split('__')[0] not in applied:
            query(db, expand(path))
    assert signature(db) == expected
    print('PASS full fresh bootstrap/upgrade chain', db, flush=True)


if __name__ == '__main__':
    expected = scoped()
    if '--fresh' in sys.argv: fresh(expected)
    print('Retained isolated schemas:', PREFIX, flush=True)
