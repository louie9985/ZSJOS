"""UTF-8: scoped V262 replay and optional additive local development synchronization."""
import argparse
import datetime
from pathlib import Path
from test_positioning_single_source import query

ROOT = Path(__file__).resolve().parents[4]
LOCAL = 'ruoyi-vue-pro'
SQL = ROOT / 'script/sql/mysql/migrations/V262__partner_nickname.sql'

def column(db):
    return query(db, "SELECT column_type,is_nullable,collation_name,HEX(column_comment) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_partner' AND column_name='nickname';")

def fingerprint(db):
    return query(db, "SELECT COUNT(*),COALESCE(SUM(CRC32(CONCAT(id,':',tenant_id,':',COALESCE(name,'')))),0) FROM zsjos_partner;")

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--sync-local', action='store_true')
    args = parser.parse_args()
    print('Local preflight:', fingerprint(LOCAL).strip(), 'nickname column:', bool(column(LOCAL)))
    db = 'partner_nickname_verify_' + datetime.datetime.now().strftime('%Y%m%d%H%M%S')
    query(LOCAL, f'CREATE DATABASE `{db}` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;')
    # Minimal documented prerequisite; synthetic rows only, no copied personal data.
    query(db, """CREATE TABLE zsjos_partner(id bigint PRIMARY KEY,tenant_id bigint NOT NULL,name varchar(100)) CHARACTER SET utf8mb4;
CREATE TABLE zsjos_schema_version LIKE `ruoyi-vue-pro`.zsjos_schema_version;
CREATE TABLE zsjos_module_schema_version LIKE `ruoyi-vue-pro`.zsjos_module_schema_version;
INSERT INTO zsjos_partner VALUES(1,991,'测试姓名甲'),(2,992,'测试姓名乙');""")
    before = fingerprint(db)
    sql = SQL.read_text(encoding='utf-8')
    query(db, sql)
    assert query(db, 'SELECT COUNT(*) FROM zsjos_partner WHERE nickname IS NULL;').strip() == '2'
    query(db, "UPDATE zsjos_partner SET nickname='星光' WHERE id=1 AND tenant_id=991;")
    query(db, sql)
    assert fingerprint(db) == before
    assert query(db, 'SELECT HEX(nickname) FROM zsjos_partner WHERE id=1;').strip() == '星光'.encode().hex().upper()
    assert query(db, "SELECT COUNT(*) FROM zsjos_schema_version WHERE version='V262';").strip() == '1'
    assert query(db, "SELECT COUNT(*) FROM zsjos_module_schema_version WHERE module_code='core' AND version='V262';").strip() == '1'
    assert '排行榜昵称'.encode().hex().upper() in column(db)
    print('PASS controlled replay, repeatability, tenant rows/names preserved, existing nickname retained, UTF-8 HEX:', db)
    if args.sync_local:
        before = fingerprint(LOCAL)
        grants = query(LOCAL, 'SELECT COUNT(*) FROM system_role_menu;')
        query(LOCAL, sql)
        query(LOCAL, sql)
        assert fingerprint(LOCAL) == before
        assert query(LOCAL, 'SELECT COUNT(*) FROM system_role_menu;') == grants
        assert column(LOCAL) == column(db)
        assert query(LOCAL, "SELECT HEX(description) FROM zsjos_schema_version WHERE version='V262';").strip() == '兼职姓名与排行榜昵称分离'.encode().hex().upper()
        print('PASS local additive sync, repeated execution, scoped schema comparison, names/grants preserved, version HEX verified')
