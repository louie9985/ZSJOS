# -*- coding: utf-8 -*-
"""V296 retained-schema checks. --inspect-local is read-only; --sync-local explicitly applies V296 only."""
import datetime
import re
from test_calendar_notifications import query, table, SQL, ROOT

MIGRATION = (SQL / 'migrations/V296__points_redemption.sql').read_text(encoding='utf-8')
PREFIX = 'points_' + datetime.datetime.now().strftime('%Y%m%d%H%M%S')
PREREQ = "INSERT INTO zsjos_schema_version(version,description,checksum) VALUES('V295','fixture','fixture'); INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version) VALUES('core','V295','fixture','fixture','fixture');"

def versions(db):
    return query(db, "SELECT version,checksum FROM zsjos_schema_version WHERE version='V296'; SELECT version,checksum FROM zsjos_module_schema_version WHERE version='V296';")[0]

def signature(db):
    return query(db, "SELECT table_name,column_name,column_type,is_nullable,COALESCE(column_default,'NULL') FROM information_schema.columns WHERE table_schema=DATABASE() AND (table_name LIKE 'zsjos_points_%' OR (table_name='infra_config' AND column_name='sensitive')) ORDER BY table_name,ordinal_position; SELECT permission,HEX(name),type,path FROM system_menu WHERE permission LIKE 'zsjos:points-redemption:%' OR permission='infra:config:secret-update' ORDER BY permission; SELECT config_key,LENGTH(value),`sensitive`+0,visible+0 FROM infra_config WHERE config_key LIKE 'zsjos.points-redemption.%' ORDER BY config_key;")[0]

def run():
    expected = None
    for case in ['initial','partial','prerequisite','ledger_failure','existing_marker','wrong_type']:
        db = PREFIX + '_' + case
        query(None, f'CREATE DATABASE `{db}` CHARACTER SET utf8mb4;')
        setup = ''.join(table(t) for t in ['infra_config','system_menu','system_role_menu','system_notify_template','system_notify_rule','zsjos_schema_version','zsjos_module_schema_version'])
        setup += "INSERT INTO system_menu(id,name,permission,type,parent_id,path) VALUES(900001,'工作台','',1,0,'/zsjos'),(900002,'配置','',2,0,'config'),(900003,'配置查询','infra:config:query',3,900002,'');"
        if case != 'prerequisite': setup += PREREQ
        if case in ['partial','wrong_type']: setup += table('zsjos_points_package')
        if case == 'wrong_type': setup += 'ALTER TABLE zsjos_points_package MODIFY credits_amount varchar(20) NOT NULL;'
        if case == 'ledger_failure': setup += "CREATE TRIGGER fail_points_ledger BEFORE INSERT ON zsjos_module_schema_version FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='injected failure';"
        if case == 'existing_marker': setup += "INSERT INTO zsjos_schema_version(version,description,checksum) VALUES('V296','fixture','preserve'); INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version) VALUES('core','V296','fixture','preserve','fixture');"
        query(db, setup)
        before = versions(db)
        _, error = query(db, MIGRATION, force=True)
        if case in ['prerequisite','ledger_failure','wrong_type']:
            assert 'ERROR' in error and versions(db) == before, (case,error)
            query(db, PREREQ if case == 'prerequisite' else 'ALTER TABLE zsjos_points_package MODIFY credits_amount int NOT NULL;' if case == 'wrong_type' else 'DROP TRIGGER fail_points_ledger;')
            query(db, MIGRATION)
        else: assert 'ERROR' not in error, (case,error)
        first = signature(db)
        query(db, MIGRATION)
        assert first == signature(db), case
        assert query(db,'SELECT COUNT(*) FROM system_role_menu; SELECT COUNT(*) FROM zsjos_points_package; SELECT COUNT(*) FROM zsjos_points_request;')[0] == '0\n0\n0'
        assert len(versions(db).splitlines()) == 2
        if expected is None: expected = first
        assert expected == first, case
        assert '积分兑换码'.encode().hex().upper() in first
        print('PASS',case,db)
    print('PASS MySQL V296 initial/repeat/partial/failure-ledger/recovery/UTF-8/no grants')
def fresh():
    def expand(path):
        return re.sub(r'^SOURCE\s+([^;]+);', lambda m: expand(ROOT / m.group(1)), path.read_text(encoding='utf-8-sig'), flags=re.M|re.I)
    db = PREFIX + '_fresh'
    query(None, f'CREATE DATABASE `{db}` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;')
    query(db, expand(SQL / 'bootstrap.sql'))
    applied = set(query(db,'SELECT version FROM zsjos_schema_version;')[0].splitlines())
    for path in sorted((SQL/'migrations').glob('V*__*.sql'), key=lambda p:int(p.name[1:].split('__')[0])):
        if path.name.split('__')[0] not in applied: query(db,expand(path))
    assert len(versions(db).splitlines()) == 2
    assert query(db,'SELECT COUNT(*) FROM zsjos_points_request; SELECT COUNT(*) FROM zsjos_points_package;')[0] == '0\n0'
    result=query(db,expand(SQL/'verify/core.sql'))[0]
    failures=[line for line in result.splitlines() if re.search(r'(?:^|\t)(FAIL|MISSING)$',line)]
    assert not failures, failures
    print('PASS full fresh baseline/upgrade chain and core verification',db,flush=True)

def inspect_local():
    print(query('ruoyi-vue-pro', "SELECT version FROM zsjos_schema_version WHERE version IN ('V295','V296'); SELECT table_name FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name LIKE 'zsjos_points_%'; SELECT COUNT(*) FROM system_role_menu; SELECT COUNT(*) FROM infra_config WHERE config_key LIKE 'zsjos.points-redemption.%';")[0])

def compare_local():
    names = query(None, "SELECT schema_name FROM information_schema.schemata WHERE schema_name REGEXP '^points_[0-9]+_fresh$' ORDER BY schema_name DESC LIMIT 1;")[0]
    assert names and re.fullmatch(r'points_[0-9]+_fresh', names)
    assert signature(names) == signature('ruoyi-vue-pro'), 'Local and fresh points schema/metadata differ'
    checks = "SELECT table_name,index_name,non_unique,seq_in_index,column_name FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name LIKE 'zsjos_points_%' ORDER BY table_name,index_name,seq_in_index; SELECT permission,component FROM system_menu WHERE permission LIKE 'zsjos:points-redemption:%' ORDER BY permission;"
    assert query(names, checks)[0] == query('ruoyi-vue-pro', checks)[0]
    print('PASS read-only local vs fresh points columns/indexes/menu/config comparison:', names)

def sync_local():
    # Fixed local development target; never reconcile checksums of other applied versions.
    import hashlib
    db = 'ruoyi-vue-pro'
    grants_sql = 'SELECT id,role_id,menu_id,tenant_id,deleted+0 FROM system_role_menu ORDER BY id;'
    before = query(db, grants_sql)[0]
    query(db, MIGRATION)
    assert before == query(db, grants_sql)[0], 'Unrelated role grants changed during synchronization'
    assert query(db, 'SELECT COUNT(*) FROM zsjos_points_package; SELECT COUNT(*) FROM zsjos_points_request;')[0] == '0\n0'
    assert query(db, "SELECT LENGTH(value),`sensitive`+0,visible+0 FROM infra_config WHERE config_key='zsjos.points-redemption.api-key' AND deleted=0;")[0] == '0\t1\t0'
    checksum = hashlib.sha256((SQL/'migrations/V296__points_redemption.sql').read_bytes()).hexdigest()
    query(db, f"START TRANSACTION; UPDATE zsjos_schema_version SET checksum='{checksum}' WHERE version='V296'; UPDATE zsjos_module_schema_version SET checksum='{checksum}' WHERE module_code='core' AND version='V296'; COMMIT;")
    first = signature(db)
    query(db, MIGRATION)
    assert first == signature(db)
    assert '积分兑换码'.encode().hex().upper() in first
    print('PASS local V296 sync/repeat/UTF-8; empty key, no packages/applications, exact role grants preserved')
    print(first)

if __name__ == '__main__':
    import sys
    if '--fresh' in sys.argv: fresh()
    elif '--inspect-local' in sys.argv: inspect_local()
    elif '--compare-local' in sys.argv: compare_local()
    elif '--sync-local' in sys.argv: sync_local()
    else: run()
