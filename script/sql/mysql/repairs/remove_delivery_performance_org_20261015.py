# -*- coding: utf-8 -*-
"""Remove the two explicitly authorized delivery performance mappings only.
Requires application-local master and existing configuration rows 4/5.
Run with --apply; defaults to inspection. Not a bootstrap or numbered migration.
Backup preimages outside repo, validate on temporary MySQL table, then soft-delete
in one guarded transaction. Repeat runs are no-ops; no inserts or FK changes.
Recovery requires reviewing backup/current rows and separately authorizing restore
of deleted/version/update_time for these two ids; never restore over later edits.
"""
import argparse
import json
from datetime import datetime
from pathlib import Path
from urllib.parse import urlparse
from zoneinfo import ZoneInfo
import pymysql
import yaml


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apply', action='store_true')
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[4]
    config = None
    for doc in yaml.safe_load_all((root / 'backend/yudao-server/src/main/resources/application-local.yaml').read_text(encoding='utf-8')):
        master = (doc or {}).get('spring', {}).get('datasource', {}).get('dynamic', {}).get('datasource', {}).get('master')
        if master:
            config = master
            break
    if not config:
        raise RuntimeError('Configured master not found')
    url = urlparse(config['url'][5:])
    connection = pymysql.connect(host=url.hostname, port=url.port or 3306,
        user=config['username'], password=str(config['password']), database=url.path[1:],
        charset='utf8mb4', connect_timeout=8, read_timeout=20,
        cursorclass=pymysql.cursors.DictCursor, autocommit=False)
    predicate = "tenant_id=1 AND deleted=b'0' AND ((id=4 AND dept_id=1050 AND center_id=1050 AND kind='CENTER') OR (id=5 AND dept_id=1051 AND center_id=1050 AND kind='DEPT'))"
    def change(cursor, table):
        return cursor.execute(f"UPDATE {table} SET deleted=b'1',version=version+1,update_time=NOW() WHERE {predicate}")
    try:
        with connection.cursor() as cursor:
            cursor.execute('SET NAMES utf8mb4')
            cursor.execute('SELECT id,dept_id,center_id,kind,version,deleted+0 AS deleted FROM zsjos_performance_org WHERE tenant_id=1 ORDER BY id')
            print('Before:', json.dumps(cursor.fetchall()))
            if not args.apply:
                return
            cursor.execute("SELECT ENGINE FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='zsjos_performance_org'")
            assert cursor.fetchone()['ENGINE'] == 'InnoDB'
            cursor.execute('CREATE TEMPORARY TABLE delivery_org_check LIKE zsjos_performance_org')
            cursor.execute('INSERT INTO delivery_org_check SELECT * FROM zsjos_performance_org WHERE tenant_id=1')
            connection.commit()
            cursor.execute(f'SELECT COUNT(*) AS n FROM delivery_org_check WHERE {predicate}')
            expected = cursor.fetchone()['n']
            assert change(cursor, 'delivery_org_check') == expected
            assert change(cursor, 'delivery_org_check') == 0
            connection.rollback()
            cursor.execute(f'SELECT COUNT(*) AS n FROM delivery_org_check WHERE {predicate}')
            assert cursor.fetchone()['n'] == expected
            print('Temporary MySQL execution, repeat and rollback passed')
            connection.rollback()
            columns = 'id,dept_id,center_id,kind,version,deleted+0 AS deleted,create_time,update_time'
            cursor.execute(f'SELECT {columns} FROM zsjos_performance_org WHERE tenant_id=1 ORDER BY id FOR UPDATE')
            before = cursor.fetchall()
            scoped = [r for r in before if r['dept_id'] in (1050, 1051)]
            assert len(scoped) == 2
            assert {(r['id'],r['dept_id'],r['center_id'],r['kind']) for r in scoped} == {(4,1050,1050,'CENTER'),(5,1051,1050,'DEPT')}
            assert not any(r['deleted'] == 0 and r['center_id'] == 1050 and r['dept_id'] not in (1050,1051) for r in before)
            expected = sum(r['deleted'] == 0 for r in scoped)
            if expected:
                stamp = datetime.now(ZoneInfo('Asia/Shanghai')).strftime('%Y%m%d-%H%M%S-%f')
                backup = root.parent / 'ZSJ-OS-backups' / 'performance-org' / f'{stamp}-delivery-org.json'
                backup.parent.mkdir(parents=True, exist_ok=True)
                backup.write_text(json.dumps(scoped, ensure_ascii=False, default=str, indent=2), encoding='utf-8')
                print('Backup:', backup)
            affected = change(cursor, 'zsjos_performance_org')
            assert affected == expected
            cursor.execute(f'SELECT {columns} FROM zsjos_performance_org WHERE tenant_id=1 ORDER BY id')
            after = cursor.fetchall()
            assert [r for r in before if r['dept_id'] not in (1050,1051)] == [r for r in after if r['dept_id'] not in (1050,1051)]
            assert all(r['deleted'] == 1 for r in after if r['dept_id'] in (1050,1051))
            assert change(cursor, 'zsjos_performance_org') == 0
            connection.commit()
            print('Committed configuration changes:', affected)
            print('Active mappings:', json.dumps([r for r in after if r['deleted'] == 0], default=str))
    finally:
        connection.rollback()
        connection.close()


if __name__ == '__main__':
    main()

