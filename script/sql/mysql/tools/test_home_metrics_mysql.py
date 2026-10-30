#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Non-destructive isolated MySQL verification. Keeps synthetic schemas for inspection.

Reads only table structures from the configured local source database. Never copies
business rows or changes the source database. Uses the container's existing root secret.
"""
import argparse
import datetime
import hashlib
import json
import re
import subprocess
from pathlib import Path
import zsjos_db as schema_tools

ROOT = Path(__file__).resolve().parents[4]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--container', default='yudao-mysql')
    parser.add_argument('--source', default='ruoyi-vue-pro')
    args = parser.parse_args()
    if not re.fullmatch(r'[A-Za-z0-9_-]+', args.source):
        raise ValueError('Invalid source schema name')
    migration_path = ROOT / 'script/sql/mysql/migrations/V358__home_metrics.sql'
    migration = migration_path.read_text(encoding='utf-8-sig')
    affected = {'zsjos_home_metric', 'zsjos_home_metric_version'}
    expected = schema_tools.desired_schema(migration_path)
    assert len(expected[0]) == 22 and len(expected[1]) == 4
    for relative in ('script/sql/mysql/schema/core.sql', 'script/sql/mysql/00-bootstrap-schema.sql'):
        actual = tuple({key: value for key, value in section.items() if key[0] in affected}
                       for section in schema_tools.desired_schema(ROOT / relative))
        assert actual == expected, f'V358 declarations differ in {relative}'
    prefix = 'v358_verify_' + datetime.datetime.now().strftime('%Y%m%d%H%M%S')
    results = []

    def sql(text, schema=None, success=True):
        command = ['docker', 'exec', '-i', args.container, 'sh', '-c',
                   'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql --default-character-set=utf8mb4 -uroot -N -B']
        data = ('SET NAMES utf8mb4;\n' + (f'USE `{schema}`;\n' if schema else '') + text).encode('utf-8')
        result = subprocess.run(command, input=data, capture_output=True)
        if (result.returncode == 0) != success:
            raise AssertionError(f'Unexpected SQL outcome for {schema}: ' + result.stderr.decode('utf-8', errors='replace')[:1500])
        return result.stdout.decode('utf-8').strip()

    def fixture(case, prerequisites=True):
        schema = prefix + '_' + case
        sql(f'CREATE DATABASE `{schema}` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;')
        sql('\n'.join(f'CREATE TABLE `{table}` LIKE `{args.source}`.`{table}`;' for table in
                     ('system_menu', 'zsjos_schema_version', 'zsjos_module_schema_version')), schema)
        sql("INSERT INTO system_menu(name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,updater,deleted) "
            "VALUES('系统管理','',1,1,0,'/system','','',NULL,0,b'1',b'1',b'0','metric_fixture','metric_fixture',b'0');", schema)
        if prerequisites:
            prerequisite(schema)
        return schema

    def prerequisite(schema):
        sql("INSERT INTO zsjos_schema_version(version,description) VALUES('V357','synthetic prerequisite');"
            "INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version) "
            "VALUES('core','V357','synthetic prerequisite','','fixture');", schema)

    def markers(schema):
        return sql("SELECT (SELECT COUNT(*) FROM zsjos_schema_version WHERE version='V358'),"
                   "(SELECT COUNT(*) FROM zsjos_module_schema_version WHERE module_code='core' AND version='V358');", schema)

    def verify(schema, record_count=0):
        assert markers(schema) == '1\t1'
        assert sql("SELECT COUNT(*) FROM system_menu WHERE permission LIKE 'zsjos:home-metric:%' AND deleted=0;", schema) == '3'
        assert sql('SELECT COUNT(*) FROM zsjos_home_metric;', schema) == str(record_count)
        assert sql('SELECT COUNT(*) FROM zsjos_home_metric_version;', schema) == str(record_count)
        assert sql("SELECT HEX(name) FROM system_menu WHERE permission='zsjos:home-metric:query';", schema) == '首页指标配置'.encode().hex().upper()
        assert sql("SELECT workbench_render_mode FROM system_menu WHERE permission='zsjos:home-metric:query';", schema) == 'admin_only'
        actual_columns = {}
        for line in sql("SELECT table_name,column_name,column_type,is_nullable FROM information_schema.columns "
                        "WHERE table_schema=DATABASE() AND table_name IN ('zsjos_home_metric','zsjos_home_metric_version');", schema).splitlines():
            table, column, datatype, nullable = line.split('\t')
            actual_columns[(table, column)] = (datatype, nullable)
        assert actual_columns == expected[0]
        actual_indexes = {}
        for line in sql("SELECT table_name,LOWER(index_name),non_unique,GROUP_CONCAT(column_name ORDER BY seq_in_index) "
                        "FROM information_schema.statistics WHERE table_schema=DATABASE() "
                        "AND table_name IN ('zsjos_home_metric','zsjos_home_metric_version') "
                        "GROUP BY table_name,index_name,non_unique;", schema).splitlines():
            table, index, non_unique, columns = line.split('\t')
            actual_indexes[(table, index)] = (int(non_unique), tuple(columns.split(',')))
        assert actual_indexes == expected[1]

    schema = fixture('initial')
    sql(migration, schema)
    verify(schema)
    sql(migration, schema)
    verify(schema)
    checksum = hashlib.sha256(b'V358__home_metrics.sql').hexdigest()
    assert sql("SELECT checksum FROM zsjos_schema_version WHERE version='V358';", schema) == checksum
    assert sql("SELECT CONCAT(checksum,':',release_version) FROM zsjos_module_schema_version WHERE module_code='core' AND version='V358';", schema) == checksum + ':baseline'
    results.append({'case': 'initial_and_repeat', 'schema': schema, 'passed': True})

    schema = fixture('baseline_precreated')
    baseline = (ROOT / 'script/sql/mysql/00-bootstrap-schema.sql').read_text(encoding='utf-8')
    for table in sorted(affected):
        ddl = re.search(r'CREATE TABLE IF NOT EXISTS ' + table + r' \(.*?;', baseline, re.S).group(0)
        sql(ddl, schema)
    sql(migration, schema)
    verify(schema)
    results.append({'case': 'fresh_baseline_tables_then_migration', 'schema': schema, 'passed': True})

    schema = fixture('preserve_configuration')
    sql(migration, schema)
    sql("INSERT INTO zsjos_home_metric(id,code,draft_json,version,published_version,enabled,tenant_id) VALUES(1,'fixture','{}',1,1,1,1);"
        "INSERT INTO zsjos_home_metric_version(metric_id,version,spec_json,tenant_id) VALUES(1,1,'{}',1);", schema)
    sql(migration, schema)
    verify(schema, 1)
    assert sql("SELECT CONCAT(code,':',version,':',published_version,':',enabled+0) FROM zsjos_home_metric WHERE id=1;", schema) == 'fixture:1:1:1'
    results.append({'case': 'repeat_preserves_existing_configuration_and_history', 'schema': schema, 'passed': True})

    schema = fixture('prerequisite', False)
    sql(migration, schema, False)
    assert markers(schema) == '0\t0'
    prerequisite(schema)
    sql(migration, schema)
    verify(schema)
    results.append({'case': 'prerequisite_failure_and_recovery', 'schema': schema, 'passed': True})

    schema = fixture('partial')
    first_table = re.search(r'CREATE TABLE IF NOT EXISTS zsjos_home_metric \(.*?;', migration, re.S).group(0)
    sql(first_table, schema)
    sql(migration, schema)
    verify(schema)
    results.append({'case': 'partial_ddl_recovery', 'schema': schema, 'passed': True})

    schema = fixture('ledger_failure')
    sql("CREATE TRIGGER metric_ledger_failure BEFORE INSERT ON zsjos_module_schema_version FOR EACH ROW "
        "SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='synthetic ledger failure';", schema)
    sql(migration, schema, False)
    assert markers(schema) == '0\t0'
    assert sql("SELECT COUNT(*) FROM system_menu WHERE permission LIKE 'zsjos:home-metric:%';", schema) == '0'
    # This trigger belongs only to the synthetic fixture, not a business database.
    sql('DROP TRIGGER metric_ledger_failure;', schema)
    sql(migration, schema)
    verify(schema)
    results.append({'case': 'ledger_atomicity_and_recovery', 'schema': schema, 'passed': True})

    schema = fixture('markers_only')
    sql("INSERT INTO zsjos_schema_version(version,description) VALUES('V358','synthetic partial');"
        "INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version) "
        "VALUES('core','V358','synthetic partial','','fixture');", schema)
    sql(migration, schema)
    verify(schema)
    results.append({'case': 'markers_do_not_skip_missing_structure', 'schema': schema, 'passed': True})

    schema = fixture('wrong_column_type')
    sql(first_table.replace('code VARCHAR(64)', 'code INT'), schema)
    sql(migration, schema, False)
    assert markers(schema) == '0\t0'
    sql('ALTER TABLE zsjos_home_metric MODIFY code VARCHAR(64) NOT NULL;', schema)
    sql(migration, schema)
    verify(schema)
    results.append({'case': 'column_type_conflict_and_recovery', 'schema': schema, 'passed': True})

    schema = fixture('menu_conflict')
    sql("INSERT INTO system_menu(name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,updater,deleted) "
        "VALUES('冲突测试','zsjos:home-metric:query',2,1,1,'wrong-path','','wrong-component',NULL,0,b'1',b'1',b'0','metric_fixture','metric_fixture',b'0');", schema)
    sql(migration, schema, False)
    assert markers(schema) == '0\t0'
    results.append({'case': 'menu_identity_conflict_no_success_marker', 'schema': schema, 'passed': True})

    print(json.dumps({'mysql': sql('SELECT VERSION();'), 'migration': 'V358__home_metrics.sql', 'migration_sha256': hashlib.sha256(migration_path.read_bytes()).hexdigest(), 'executed_sql_sha256': hashlib.sha256(migration.encode('utf-8')).hexdigest(), 'source_rows_copied': 0, 'results': results}, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()
