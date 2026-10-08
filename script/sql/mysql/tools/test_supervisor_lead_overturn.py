# -*- coding: utf-8 -*-
"""Retained MySQL fixtures; --apply-dev synchronizes only V289 metadata, never role grants or ledgers."""
import datetime
import hashlib
import json
import sys
import tempfile
from pathlib import Path
from test_calendar_notifications import query, table, SQL

MIGRATION = (SQL / 'migrations/V289__supervisor_lead_overturn.sql').read_text(encoding='utf-8')
BLOCK = MIGRATION.split('-- BEGIN SUPERVISOR OVERTURN METADATA')[1].split('-- END SUPERVISOR OVERTURN METADATA')[0]
CORRECTION = '''SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS zsjos_supervisor_overturn_metadata;
DELIMITER $$
CREATE PROCEDURE zsjos_supervisor_overturn_metadata()
BEGIN
 DECLARE parent_menu BIGINT;
 DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
 START TRANSACTION;
''' + BLOCK + '''
 COMMIT;
END$$
DELIMITER ;
CALL zsjos_supervisor_overturn_metadata();
DROP PROCEDURE zsjos_supervisor_overturn_metadata;
'''
PERMISSION = 'zsjos:subordinate-sales:lead-overturn-valid'
SCENE = 'zsjos.lead.supervisor_overturned'

def digest(database, sql):
    return hashlib.sha256(query(database, sql)[0].encode('utf-8')).hexdigest()

def guards(database):
    return [digest(database, f'SELECT * FROM {name} ORDER BY {order};') for name, order in (
        ('system_role_menu','id'),('zsjos_schema_version','version'),('zsjos_module_schema_version','module_code,version'))]

def metadata(database):
    return query(database, f"SELECT HEX(name),permission,type,parent_id,status FROM system_menu WHERE permission='{PERMISSION}';"
                 f"SELECT HEX(title),scene_code,params FROM system_notify_template WHERE scene_code='{SCENE}';"
                 f"SELECT tenant_id,recipient_roles,action_type,status FROM system_notify_rule WHERE scene_code='{SCENE}' ORDER BY tenant_id;")[0]

def verify(database):
    rows = metadata(database)
    assert '主管直接改判有效'.encode().hex().upper() in rows
    assert '主管已将客资改判有效'.encode().hex().upper() in rows
    assert '["submitter","owner"]' in rows.replace(' ', '')
    assert 'lead.no' in rows and 'lead.id' not in rows

def setup(database, prerequisite=True):
    query(None, f'CREATE DATABASE `{database}` CHARACTER SET utf8mb4;')
    names = ['system_menu','system_role_menu','system_tenant','system_notify_template','system_notify_rule','zsjos_schema_version','zsjos_module_schema_version']
    sql = ''.join(table(n) for n in names)
    sql += "INSERT INTO system_menu(id,name,permission,type,parent_id) VALUES(6814,'下属销售','zsjos:subordinate-sales:query',2,6735);"
    sql += "INSERT INTO system_tenant(id,name,contact_name,contact_mobile,expire_time,account_count,package_id) VALUES(1,'测试租户','测试','', '2099-01-01',100,0);"
    sql += "INSERT INTO system_role_menu(role_id,menu_id,tenant_id) VALUES(1,6814,1);"
    if prerequisite:
        sql += "INSERT INTO zsjos_schema_version(version,description,checksum) VALUES('V288','fixture','fixture');"
        sql += "INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version) VALUES('core','V288','fixture','fixture','fixture');"
    query(database, sql)

def run():
    prefix = 'ov_' + datetime.datetime.now().strftime('%m%d%H%M%S')
    for case in ('initial','partial','missing_prerequisite','missing_parent','identity_conflict','ledger_failure'):
        db=prefix+'_'+case
        setup(db, case!='missing_prerequisite')
        if case=='partial':
            query(db, f"INSERT INTO system_menu(name,permission,type,parent_id,status,sort) VALUES('管理员名称','{PERMISSION}',3,6814,1,99);"
                  "INSERT INTO zsjos_schema_version(version,description,checksum) VALUES('V289','keep','keep');")
        if case=='missing_parent': query(db,"UPDATE system_menu SET deleted=1 WHERE id=6814;")
        if case=='identity_conflict': query(db,f"INSERT INTO system_menu(name,permission,type,parent_id) VALUES('其他','{PERMISSION}',3,999);")
        if case=='ledger_failure': query(db,"CREATE TRIGGER reject_v289 BEFORE INSERT ON zsjos_module_schema_version FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='injected ledger failure';")
        before=guards(db); original=metadata(db)
        _,error=query(db,MIGRATION,force=True)
        if case not in ('initial','partial'):
            assert 'ERROR' in error and guards(db)==before and metadata(db)==original,case
            if case=='missing_prerequisite': query(db,"INSERT INTO zsjos_schema_version(version,description,checksum) VALUES('V288','fixture','fixture'); INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version) VALUES('core','V288','fixture','fixture','fixture');")
            elif case=='missing_parent': query(db,'UPDATE system_menu SET deleted=0 WHERE id=6814;')
            elif case=='identity_conflict': query(db,f"UPDATE system_menu SET permission='fixture:other' WHERE permission='{PERMISSION}';")
            else: query(db,'DROP TRIGGER reject_v289;')
            query(db,MIGRATION)
        if case!='partial': verify(db)
        else:
            assert '管理员名称'.encode().hex().upper() in metadata(db)
            assert query(db,"SELECT checksum FROM zsjos_schema_version WHERE version='V289';")[0]=='keep'
        stable=metadata(db); stable_guard=guards(db)
        query(db,MIGRATION); query(db,CORRECTION)
        assert metadata(db)==stable and guards(db)==stable_guard
        assert guards(db)[0]==before[0]
        assert query(db,"SELECT COUNT(*) FROM zsjos_module_schema_version WHERE module_code='core' AND version='V289';")[0]=='1'
        print('PASS MySQL 8 initial/replay/recovery/HEX/grants:',case,flush=True)
    return prefix+'_initial'

def apply_dev(reference):
    db='ruoyi-vue-pro'
    guard=guards(db)
    other=[digest(db,sql) for sql in (
        f"SELECT * FROM system_menu WHERE permission<>'{PERMISSION}' ORDER BY id;",
        f"SELECT * FROM system_notify_template WHERE code<>'ZSJOS_LEAD_SUPERVISOR_OVERTURNED' ORDER BY id;",
        f"SELECT * FROM system_notify_rule WHERE scene_code<>'{SCENE}' ORDER BY id;")]
    directory=Path(tempfile.mkdtemp(prefix='zsjos-overturn-metadata-'))
    (directory/'correction.sql').write_text(CORRECTION,encoding='utf-8')
    (directory/'before.json').write_text(json.dumps({'guards':guard,'unrelated':other,'metadata':metadata(db)},ensure_ascii=False),encoding='utf-8')
    query(db,CORRECTION);verify(db)
    assert guards(db)==guard
    after=[digest(db,sql) for sql in (
        f"SELECT * FROM system_menu WHERE permission<>'{PERMISSION}' ORDER BY id;",
        f"SELECT * FROM system_notify_template WHERE code<>'ZSJOS_LEAD_SUPERVISOR_OVERTURNED' ORDER BY id;",
        f"SELECT * FROM system_notify_rule WHERE scene_code<>'{SCENE}' ORDER BY id;")]
    assert after==other
    assert metadata(db)==metadata(reference),'scoped metadata differs from migration result'
    query(db,CORRECTION);assert guards(db)==guard
    print('PASS local metadata synchronization and migration comparison; backup:',directory)

if __name__=='__main__':
    fixture=run()
    if '--apply-dev' in sys.argv: apply_dev(fixture)
