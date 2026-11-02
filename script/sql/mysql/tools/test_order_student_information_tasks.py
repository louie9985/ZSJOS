# -*- coding: utf-8 -*-
"""Retained isolated MySQL fixtures; never applies SQL to the application database."""
import datetime
from test_positioning_single_source import query, ROOT

SQL = (ROOT / 'script/sql/mysql/migrations/V359__order_student_information_tasks.sql').read_text(encoding='utf-8')
STAMP = datetime.datetime.now().strftime('%Y%m%d%H%M%S%f')
LOCAL = 'ruoyi-vue-pro'

def fixture(suffix, prerequisite=True):
    db = 'order_collection_' + STAMP + '_' + suffix
    query(LOCAL, f'CREATE DATABASE `{db}` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;')
    for table in ['zsjos_schema_version', 'zsjos_module_schema_version', 'system_menu', 'zsjos_student_info_form', 'zsjos_order_item']:
        query(db, f'CREATE TABLE {table} LIKE `{LOCAL}`.{table};')
    for table in ['zsjos_schema_version', 'zsjos_module_schema_version']:
        versions = "'V342','V358'" if prerequisite else "'V342'"
        query(db, f"INSERT INTO {table} SELECT * FROM `{LOCAL}`.{table} WHERE version IN ({versions});")
    query(db, f"INSERT INTO system_menu SELECT * FROM `{LOCAL}`.system_menu WHERE path='/zsjos' AND type=1 AND deleted=0;")
    query(db, "INSERT INTO zsjos_order_item(id,order_id,unit_price,payable_amount,product_snapshot,tenant_id) VALUES(2,1,0,0,JSON_OBJECT('name','合成项目'),1); INSERT INTO zsjos_student_info_form(id,tenant_id,sales_user_id,config_version_id,order_item_id,person_id,token_hash,token_ciphertext,status,expires_at) VALUES(3,1,1,1,2,4,REPEAT('a',64),'synthetic-ciphertext','SUBMITTED',DATE_ADD(NOW(),INTERVAL 1 DAY));")
    # Only columns read by this migration; use the real MySQL engine and real item/form structures.
    query(db,"CREATE TABLE zsjos_order(id BIGINT PRIMARY KEY,person_id BIGINT NULL,status VARCHAR(32),tenant_id BIGINT,deleted BIT DEFAULT b'0'); CREATE TABLE zsjos_registration_case(id BIGINT PRIMARY KEY,order_id BIGINT,order_item_id BIGINT,status VARCHAR(32),tenant_id BIGINT,deleted BIT DEFAULT b'0');")
    scenarios=[('pending','effective',1,0,False),('processing','effective',1,0,False),
               ('completed','effective',1,0,False),('cancelled','effective',1,0,False),
               ('ended','effective',1,0,False),('pending','terminated',1,0,False),
               ('pending','pending_approval',1,0,False),('pending','effective',2,0,False),
               ('pending','effective',1,1,False),('pending','effective',1,0,True),('pending','revision_required',1,0,False),('processing','superseded',1,0,False)]
    for number,(case_status,order_status,tenant,deleted,mismatch) in enumerate(scenarios,10):
        query(db,f"INSERT INTO zsjos_order VALUES({number},4,'{order_status}',{tenant},0); INSERT INTO zsjos_order_item(id,order_id,unit_price,payable_amount,product_snapshot,tenant_id) VALUES({number},{999 if mismatch else number},0,0,JSON_OBJECT(),{tenant}); INSERT INTO zsjos_registration_case VALUES({number},{number},{number},'{case_status}',{tenant},{deleted});")
    return db

def markers(db):
    return query(db, "SELECT version,checksum FROM zsjos_schema_version ORDER BY version; SELECT module_code,version,checksum FROM zsjos_module_schema_version ORDER BY module_code,version;")

def reject(db):
    before = markers(db)
    try:
        query(db, SQL)
    except RuntimeError:
        pass
    else:
        raise AssertionError('expected failure')
    assert markers(db) == before

def verify(db):
    protected = 'CHECKSUM TABLE zsjos_order_item,zsjos_student_info_form,zsjos_order,zsjos_registration_case;'
    original = query(db, protected)
    query(db, SQL)
    before = markers(db)
    tasks_before = query(db,'CHECKSUM TABLE zsjos_student_info_task;')
    query(db, SQL)
    assert markers(db) == before
    assert query(db, protected) == original
    assert query(db,'CHECKSUM TABLE zsjos_student_info_task;') == tasks_before
    assert query(db, 'SELECT order_id,order_item_id,person_id,form_id,tenant_id FROM zsjos_student_info_task ORDER BY order_item_id;').strip().splitlines() == ['1\t2\t4\t3\t1','10\t10\t4\tNULL\t1','11\t11\t4\tNULL\t1','16\t16\t4\tNULL\t1','20\t20\t4\tNULL\t1']
    assert query(db,'SELECT COUNT(*) FROM zsjos_student_info_task WHERE order_item_id IN (10,11,16,20) AND form_id IS NULL AND source_form_id IS NULL AND operator_user_id IS NULL AND version=0;').strip() == '4'
    assert query(db, "SELECT HEX(name) FROM system_menu WHERE permission='zsjos:student-info-task:query';").strip() == '报名资料收集'.encode().hex().upper()
    assert query(db, "SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='zsjos_student_info_task' AND index_name='uk_tenant_item';").strip() == '2'
    print('PASS initial/replay/UTF-8/legacy pending cases/terminal and scope exclusions/unchanged history', db)
    schema = "SELECT column_name,column_type,is_nullable,COALESCE(column_default,'NULL'),COALESCE(collation_name,''),extra FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_student_info_task' ORDER BY ordinal_position; SELECT index_name,non_unique,seq_in_index,column_name FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='zsjos_student_info_task' ORDER BY index_name,seq_in_index;"
    if query(LOCAL,"SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='zsjos_student_info_task';").strip() == '1':
        assert query(db,schema) == query(LOCAL,schema)
        print('PASS scoped structure comparison with local development database')

if __name__ == '__main__':
    verify(fixture('initial'))
    applied=fixture('already_applied')
    query(applied,f"CREATE TABLE zsjos_student_info_task LIKE `{LOCAL}`.zsjos_student_info_task; INSERT INTO zsjos_student_info_task(order_id,order_item_id,person_id,form_id,tenant_id) VALUES(1,2,4,3,1);")
    for table in ['zsjos_schema_version','zsjos_module_schema_version']:
        query(applied,f"INSERT INTO {table} SELECT * FROM `{LOCAL}`.{table} WHERE version='V359';")
    old_markers=markers(applied)
    verify(applied)
    assert markers(applied)==old_markers
    reject(fixture('missing', False))
    conflict=fixture('conflict')
    query(conflict,f"CREATE TABLE zsjos_student_info_task LIKE `{LOCAL}`.zsjos_student_info_task; INSERT INTO zsjos_student_info_task(order_id,order_item_id,person_id,tenant_id) VALUES(10,10,99,1);")
    reject(conflict)
    assert query(conflict,'SELECT COUNT(*) FROM zsjos_student_info_task;').strip()=='1'
    query(conflict,'UPDATE zsjos_student_info_task SET person_id=4 WHERE order_item_id=10 AND tenant_id=1;')
    verify(conflict)
    failed = fixture('failed')
    query(failed, "CREATE TRIGGER fail_ledger BEFORE INSERT ON zsjos_module_schema_version FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='fixture ledger failure';")
    reject(failed)
    query(failed, 'DROP TRIGGER fail_ledger;')
    verify(failed)
    print('PASS prerequisite/ledger failure and recovery after table DDL; retained fixtures only')
