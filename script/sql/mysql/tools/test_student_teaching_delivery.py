#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Retained MySQL fixtures and optional explicitly authorized live additive migration.

Target data/assignments stay unchanged. Schema add only via --apply-local. No databases are deleted.
"""
import argparse
import json
import uuid
from datetime import datetime
import seed_student
from pathlib import Path
from order_history_repair import connect, statements
from test_core_baseline import Client, create_database, verify_fresh
import zsjos_db as db

ROOT=Path(__file__).resolve().parents[4]
SQL=ROOT/'script/sql/mysql/migrations/V336__student_teaching_delivery_participation.sql'

def column(c):
    return c.query("SELECT column_type,is_nullable,column_default,HEX(column_comment) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_service_relation' AND column_name='teaching_delivery'")
def ledger(c):
    return c.query("SELECT HEX(version),HEX(checksum) FROM zsjos_schema_version WHERE version='V336' UNION ALL SELECT HEX(version),HEX(checksum) FROM zsjos_module_schema_version WHERE version='V336'")
def rows(c):
    return c.query('SELECT id,person_id,teaching_delivery+0,class_id,owner_user_id,version,HEX(service_snapshot) FROM zsjos_service_relation ORDER BY id')
def apply(c,success=True):
    result=c.execute_file(SQL) if success else c.execute(SQL.read_text(encoding='utf-8'),expect_success=False)
    if not success:assert result.returncode!=0 or b'ERROR' in result.stderr

def fixtures(prefix,target):
    c=create_database(prefix+'_upgrade')
    for t in ('zsjos_service_relation','zsjos_schema_version','zsjos_module_schema_version'):
        c.query(f'CREATE TABLE `{t}` LIKE `{target}`.`{t}`')
    if column(c):c.query('ALTER TABLE zsjos_service_relation DROP COLUMN teaching_delivery')
    c.query("INSERT INTO zsjos_service_relation(id,person_id,status,activated_at,version,tenant_id,service_snapshot) VALUES(1,9,'active',NOW(),4,1,JSON_OBJECT('name','学员服务'))")
    apply(c,False);assert not column(c) and not ledger(c)
    for t,fields,values in [('zsjos_schema_version','version,description,checksum',"'V335','fixture',REPEAT('a',64)"),('zsjos_module_schema_version','module_code,version,description,checksum,release_version',"'core','V335','fixture',REPEAT('a',64),'fixture'")]:
        c.query(f'INSERT INTO {t}({fields}) VALUES({values})')
    c.query("CREATE TRIGGER refuse_v336 BEFORE INSERT ON zsjos_module_schema_version FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='fixture ledger failure'")
    apply(c,False);assert column(c) and not ledger(c)
    assert c.query('SELECT teaching_delivery+0,version FROM zsjos_service_relation WHERE id=1')=='1\t4'
    c.query('DROP TRIGGER refuse_v336')
    apply(c);assert len(ledger(c).splitlines())==2
    c.query("INSERT INTO zsjos_service_relation(id,person_id,status,activated_at,teaching_delivery,class_id,content_director_user_id,operator_user_id,acceptance_status,version,tenant_id) VALUES(2,9,'active',NOW(),b'0',1,7,8,'accepted',0,1)")
    once=(column(c),ledger(c),rows(c));apply(c);assert once==(column(c),ledger(c),rows(c))
    assert c.query('SELECT id FROM zsjos_service_relation WHERE tenant_id=1 AND teaching_delivery=1 ORDER BY id')=='1'
    assert c.query("SELECT id FROM zsjos_service_relation WHERE tenant_id=1 AND content_director_user_id=7 AND acceptance_status='accepted'")=='2'
    # Invalid partially-added columns cannot register success, including after an existing ledger marker.
    bad=create_database(prefix+'_bad')
    for t in ('zsjos_service_relation','zsjos_schema_version','zsjos_module_schema_version'):bad.query(f'CREATE TABLE `{t}` LIKE `{c.database}`.`{t}`')
    bad.query(f'INSERT INTO zsjos_schema_version SELECT * FROM `{c.database}`.zsjos_schema_version')
    bad.query(f'INSERT INTO zsjos_module_schema_version SELECT * FROM `{c.database}`.zsjos_module_schema_version')
    before=ledger(bad);bad.query('ALTER TABLE zsjos_service_relation MODIFY COLUMN teaching_delivery BIT(1) NULL DEFAULT NULL')
    apply(bad,False);assert before==ledger(bad)
    bad.query("ALTER TABLE zsjos_service_relation MODIFY COLUMN teaching_delivery BIT(1) NOT NULL DEFAULT b'1' COMMENT '是否纳入教学交付'")
    apply(bad);assert before==ledger(bad)
    print('PASS MySQL prerequisite/initial/default/replay/partial-DDL/ledger-failure/recovery/UTF-8 checks',flush=True)
    return c

def seed_fixture(c,target):
    for table in ('zsjos_delivery_class','zsjos_collaboration_group'):
        c.query(f'CREATE TABLE `{table}` LIKE `{target}`.`{table}`')
    c.query("INSERT INTO zsjos_delivery_class(class_no,class_name,system_class,tenant_id) VALUES('FIXTURE','测试待分班',b'1',1)")
    connection=connect(database=c.database)
    try:
        with connection.cursor() as q:
            now=datetime.now()
            media=seed_student.ensure_chain(q,99,'fixture','fixture',7,8,1,now,'fixture',False)
            teaching=seed_student.ensure_chain(q,99,'fixture','fixture',7,8,1,now,'fixture',True)
            assert media!=teaching
            assert seed_student.ensure_chain(q,99,'fixture','fixture',7,8,1,now,'fixture',False)==media
            assert seed_student.ensure_chain(q,99,'fixture','fixture',7,8,1,now,'fixture',True)==teaching
            q.execute('SELECT id,teaching_delivery+0 AS teaching,class_id,order_id,order_item_id,registration_case_id,content_director_user_id,operator_user_id FROM zsjos_service_relation WHERE id IN (%s,%s) ORDER BY id',(media,teaching))
            found={row['id']:row for row in q.fetchall()}
            assert found[media]['teaching']==0 and found[media]['class_id'] is None
            assert found[teaching]['teaching']==1 and found[teaching]['class_id'] is not None
            assert all(row[key] is None for row in found.values() for key in ('order_id','order_item_id','registration_case_id'))
            assert all(row['content_director_user_id']==7 and row['operator_user_id']==8 for row in found.values())
        connection.commit()
    finally:connection.close()
    print('PASS actual seed SQL yes/no/replay/separate same-Person relationships/no teaching class for media',flush=True)

def main():
    p=argparse.ArgumentParser();p.add_argument('--apply-local',action='store_true');p.add_argument('--fresh',action='store_true');args=p.parse_args()
    prefix='md_'+uuid.uuid4().hex[:8];target=Client('ruoyi-vue-pro');c=fixtures(prefix,target.database)
    seed_fixture(c,target.database)
    if args.fresh:
        fresh=create_database(prefix+'_fresh');verify_fresh(fresh)
        assert column(fresh)==column(c)
        print('PASS baseline through latest migration and participation definition',flush=True)
    if args.apply_local:
        connection=connect()
        try:
            with connection.cursor() as q:
                # Full before/after values remain in memory only; do not emit personal fields.
                q.execute('SELECT * FROM zsjos_service_relation ORDER BY id');before=q.fetchall()
                q.execute('SET NAMES utf8mb4')
                for statement in statements(SQL.read_text(encoding='utf-8')):q.execute(statement)
                q.execute('SELECT * FROM zsjos_service_relation ORDER BY id');after=q.fetchall()
                assert len(before)==len(after)
                for a,b in zip(before,after):
                    assert {k:v for k,v in b.items() if k!='teaching_delivery'}=={k:v for k,v in a.items() if k!='teaching_delivery'}
                    assert b['teaching_delivery']==b'\x01' or b['teaching_delivery']==1
                print('PASS authorized local schema add; all',len(before),'service rows unchanged and enabled',flush=True)
        finally:connection.close()
        assert column(target)==column(c) and len(ledger(target).splitlines())==2
if __name__=='__main__':main()
