#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""V344 verification on retained synthetic schemas; application DB is read-only."""
import datetime
import re
from concurrent.futures import ThreadPoolExecutor
import time
from test_positioning_single_source import query, ROOT

SOURCE = 'ruoyi-vue-pro'
STAMP = datetime.datetime.now().strftime('%Y%m%d%H%M%S%f')
SQL = (ROOT/'script/sql/mysql/migrations/V344__delivery_class_groups.sql').read_text(encoding='utf-8')
V343 = (ROOT/'script/sql/mysql/migrations/V343__planner_unscheduled_class.sql').read_text(encoding='utf-8')

def fixture(suffix):
    db = 'class_groups_'+STAMP+'_'+suffix
    query(SOURCE, f'CREATE DATABASE `{db}` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;')
    for table in ['zsjos_delivery_class','system_menu','zsjos_schema_version','zsjos_module_schema_version']:
        query(db, f'CREATE TABLE {table} LIKE `{SOURCE}`.{table};')
    if query(db, "SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='zsjos_delivery_class' AND index_name='idx_delivery_class_group';").strip() != '0':
        query(db, 'ALTER TABLE zsjos_delivery_class DROP INDEX idx_delivery_class_group;')
    if query(db, "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_delivery_class' AND column_name='group_id';").strip() == '1':
        query(db, 'ALTER TABLE zsjos_delivery_class DROP COLUMN group_id;')
    query(db, "INSERT INTO zsjos_schema_version(version,description,checksum) VALUES('V339','fixture','fixture');"
              "INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version) VALUES('core','V339','fixture','fixture','fixture');"
              "INSERT INTO system_menu(name,permission,type,sort,parent_id,path,icon,component,status) VALUES('班级管理','zsjos:delivery-class:query',2,1,0,'class-management','','',0);")
    query(db,V343)
    query(db,"INSERT INTO zsjos_delivery_class(id,class_no,class_name,class_kind,system_class,dept_id,status,tenant_id) VALUES(1,'SYNTHETIC01','历史测试班','NORMAL',0,80,'SERVING',1);")
    return db

def ledger(db):
    return query(db,"SELECT COUNT(*) FROM zsjos_schema_version WHERE version='V344'; SELECT COUNT(*) FROM zsjos_module_schema_version WHERE version='V344' AND module_code='core';")

def shape(db):
    return query(db,"SELECT table_name,column_name,column_type,is_nullable,COALESCE(column_default,'<NULL>'),HEX(column_comment) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name IN ('zsjos_delivery_class','zsjos_delivery_class_group','zsjos_delivery_class_membership_event') ORDER BY table_name,column_name;"
                  "SELECT table_name,index_name,non_unique,seq_in_index,column_name FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name IN ('zsjos_delivery_class','zsjos_delivery_class_group','zsjos_delivery_class_membership_event') ORDER BY table_name,index_name,seq_in_index;")

def verify(db):
    query(db,SQL); first=shape(db); query(db,SQL)
    assert shape(db)==first and ledger(db).strip()=='1\n1'
    assert query(db,'SELECT class_no,HEX(class_name),group_id IS NULL FROM zsjos_delivery_class WHERE id=1;').strip()=='SYNTHETIC01\t'+ '历史测试班'.encode().hex().upper()+'\t1'
    assert query(db,"SELECT HEX(name) FROM system_menu WHERE permission='zsjos:delivery-class:group-delete';").strip()=='删除空班级组'.encode().hex().upper()
    assert query(db,"SELECT HEX(description) FROM zsjos_schema_version WHERE version='V344';").strip()=='班级父子分组'.encode().hex().upper()
    print('PASS initial/replay/preserved historical class/Chinese HEX',db,flush=True)
    return first

def reject(db):
    before=ledger(db)
    try: query(db,SQL)
    except RuntimeError: pass
    else: raise AssertionError('migration must fail')
    assert ledger(db)==before
    print('PASS no success marker after failure',db,flush=True)

if __name__=='__main__':
    initial=fixture('initial'); expected=verify(initial)
    partial=fixture('partial');query(partial,"ALTER TABLE zsjos_delivery_class ADD COLUMN group_id bigint NULL COMMENT '普通班级所属父级';");assert verify(partial)==expected
    missing=fixture('missing');query(missing,"UPDATE zsjos_module_schema_version SET version='fixture_missing' WHERE version='V343';");reject(missing)
    wrong=fixture('wrong');query(wrong,'ALTER TABLE zsjos_delivery_class ADD COLUMN group_id varchar(10) NULL;');reject(wrong)
    failed=fixture('ledger_failure');query(failed,"CREATE TRIGGER reject_v344 BEFORE INSERT ON zsjos_module_schema_version FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='synthetic ledger failure';");reject(failed)
    assert query(failed,"SELECT COUNT(*) FROM system_menu WHERE permission='zsjos:delivery-class:group-delete';").strip()=='0'
    query(failed,'DROP TRIGGER reject_v344;');assert verify(failed)==expected
    conflict=fixture('menu_conflict');query(conflict,"INSERT INTO system_menu(name,permission,type,sort,parent_id,path,icon,component,status) VALUES('fixture','zsjos:delivery-class:group-delete',3,1,0,'','','',0);");reject(conflict)
    tombstone=fixture('deleted_permission')
    query(tombstone,"INSERT INTO system_menu(name,permission,type,sort,parent_id,path,icon,component,status,deleted) SELECT '删除空班级组','zsjos:delivery-class:group-delete',3,1,id,'','','',0,1 FROM system_menu WHERE permission='zsjos:delivery-class:query';")
    reject(tombstone)
    assert query(tombstone,"SELECT deleted+0 FROM system_menu WHERE permission='zsjos:delivery-class:group-delete';").strip()=='1'
    query(tombstone,"UPDATE system_menu SET deleted=0 WHERE permission='zsjos:delivery-class:group-delete';")
    verify(tombstone)
    # Compare canonical table declarations with additive upgrade, without rewriting the baseline.
    canonical='class_groups_'+STAMP+'_canonical';query(SOURCE,f'CREATE DATABASE `{canonical}` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;')
    core=(ROOT/'script/sql/mysql/schema/core.sql').read_text(encoding='utf-8')
    for table in ['zsjos_delivery_class','zsjos_delivery_class_group','zsjos_delivery_class_membership_event']:
        declaration=re.search(r'CREATE TABLE IF NOT EXISTS `?'+table+r'`?\s*\(.*?;(?=\n)',core,re.S)
        assert declaration,table
        query(canonical,declaration.group())
    assert shape(canonical)==expected
    print('PASS canonical schema matches V343 -> V344 upgrade',flush=True)
    query(initial,"INSERT INTO zsjos_delivery_class_group(id,name,dept_id,tenant_id) VALUES(1,'并发测试组',80,1);")
    # Both membership writers and empty-group deletion lock the same group before classes.
    def attach():
        return query(initial,"START TRANSACTION;SELECT id FROM zsjos_delivery_class_group WHERE tenant_id=1 AND id=1 AND deleted=0 FOR UPDATE;DO SLEEP(0.5);UPDATE zsjos_delivery_class SET group_id=1,version=version+1 WHERE tenant_id=1 AND id=1;COMMIT;")
    def inspect_empty():
        return query(initial,"START TRANSACTION;SELECT id FROM zsjos_delivery_class_group WHERE tenant_id=1 AND id=1 AND deleted=0 FOR UPDATE;SELECT COUNT(*) FROM zsjos_delivery_class WHERE tenant_id=1 AND group_id=1 AND deleted=0;COMMIT;")
    with ThreadPoolExecutor(max_workers=2) as pool:
        writer=pool.submit(attach);time.sleep(0.15);observer=pool.submit(inspect_empty)
        writer.result();assert observer.result().strip()=='1\n1'
    print('PASS concurrent group lock makes empty-group check observe committed membership',flush=True)
