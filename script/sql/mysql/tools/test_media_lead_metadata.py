# UTF-8. Synthetic retained MySQL fixture only; no development grants or business changes.
from datetime import datetime
from pathlib import Path
from test_positioning_single_source import query

ROOT = Path(__file__).resolve().parents[4]
SQL = (ROOT / 'script/sql/mysql/migrations/V283__media_lead_analysis.sql').read_text(encoding='utf-8')

def main():
    db = 'media_lead_metadata_' + datetime.now().strftime('%Y%m%d%H%M%S')
    query('ruoyi-vue-pro', f'CREATE DATABASE `{db}` CHARACTER SET utf8mb4;')
    for table in ['system_menu', 'system_role_menu', 'zsjos_schema_version', 'zsjos_module_schema_version']:
        query(db, f'CREATE TABLE `{table}` LIKE `ruoyi-vue-pro`.`{table}`;')
    def versions():
        return query(db, "SELECT COUNT(*) FROM zsjos_schema_version WHERE version='V283'; SELECT COUNT(*) FROM zsjos_module_schema_version WHERE version='V283';").split()
    def fails(sql, expected):
        try:
            query(db, sql)
        except RuntimeError as e:
            assert expected in str(e), str(e)
            return
        raise AssertionError('Expected failure: ' + expected)
    fails(SQL, 'requires Core V282')
    assert versions() == ['0','0']
    assert query(db, "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='zsjos_media_lead_target';").strip() == '0'
    query(db, """INSERT INTO zsjos_schema_version(version,description,checksum) VALUES('V282','fixture','fixture');
    INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version) VALUES('core','V282','fixture','fixture','fixture');""")
    fails(SQL, 'exactly one active Workbench root')
    assert versions() == ['0','0']
    query(db, """INSERT INTO system_menu(id,name,permission,type,sort,parent_id,path,icon,component,status,visible,keep_alive,always_show,deleted)
    VALUES(900001,'测试工作台','',1,1,0,'/zsjos','','',0,1,1,0,0);""")
    partial = SQL.replace('CREATE TABLE IF NOT EXISTS zsjos_media_lead_org',
                          "SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='injected ddl interruption';\nCREATE TABLE IF NOT EXISTS zsjos_media_lead_org")
    fails(partial, 'injected ddl interruption')
    assert versions() == ['0','0']
    assert query(db, "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='zsjos_media_lead_target';").strip() == '1'
    assert query(db, "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='zsjos_media_lead_org';").strip() == '0'
    broken = SQL.replace('INSERT INTO zsjos_schema_version(version,description,checksum)', "SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='injected failure';\nINSERT INTO zsjos_schema_version(version,description,checksum)")
    fails(broken, 'injected failure')
    assert versions() == ['0','0']
    assert query(db,"SELECT COUNT(*) FROM system_menu WHERE permission LIKE 'zsjos:media-lead-%';").strip() == '0'
    query(db, SQL)
    assert versions() == ['1','1']
    def signature():
        return query(db,"SELECT id,HEX(name),permission,parent_id,path,component,status,sort FROM system_menu ORDER BY id;")
    first=signature()
    query(db,SQL)
    assert signature()==first
    assert query(db,'SELECT COUNT(*) FROM system_role_menu;').strip()=='0'
    for permission,name in [('zsjos:media-lead-analysis:query','新媒体客资分析大盘'),('zsjos:media-lead-target:query','客资引流人数指标设置')]:
        assert query(db,f"SELECT HEX(name) FROM system_menu WHERE permission='{permission}';").strip()==name.encode().hex().upper()
    # Simulate a former partial application with existing version markers and a missing child.
    query(db,"DELETE FROM system_menu WHERE permission='zsjos:media-lead-analysis:detail';")
    query(db,SQL)
    assert query(db,"SELECT COUNT(*) FROM system_menu WHERE permission LIKE 'zsjos:media-lead-%';").strip()=='8'
    query(db,"UPDATE system_menu SET name='管理员自定义名称',sort=777 WHERE permission='zsjos:media-lead-analysis:query';")
    edited=signature()
    fails(broken,'injected failure')
    assert versions()==['1','1'] and signature()==edited
    query(db,SQL)
    assert signature()==edited
    assert 'zsjos/salesPerformance' not in signature()
    print('PASS',db,'prerequisite failure, partial DDL recovery, menu rollback, replay, existing marker, UTF-8 HEX, no grants and admin-edit retention')

if __name__ == '__main__':
    main()
