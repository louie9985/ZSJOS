# -*- coding: utf-8 -*-
"""Controlled V323 checks. Retains isolated databases; --sync-local backs up before additive application."""
import argparse
import datetime
import json
from pathlib import Path
from test_positioning_single_source import query, ROOT
from zsjos_db import desired_schema, extract_create_tables

LOCAL='ruoyi-vue-pro'
SQL=ROOT/'script/sql/mysql/migrations/V323__registration_student_information.sql'
TABLES=['zsjos_student_info_form','zsjos_student_info_form_value','zsjos_student_info_form_config',
        'zsjos_schema_version','zsjos_module_schema_version','system_menu']
STAMP=datetime.datetime.now().strftime('%Y%m%d%H%M%S%f')
def scalar(db,sql): return int(query(db,sql).strip())
def clone(suffix,prerequisites=True):
    db='student_collection_'+STAMP+'_'+suffix
    query(LOCAL,f'CREATE DATABASE {db} CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;')
    baseline=dict(extract_create_tables((ROOT/'script/sql/mysql/00-bootstrap-schema.sql').read_text(encoding='utf-8')))
    for table in TABLES:
        if table in TABLES[:3]: query(db,f'CREATE TABLE {table} ('+baseline[table]+') ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;')
        else: query(db,f'CREATE TABLE {table} LIKE `{LOCAL}`.{table};')
        if table=='system_menu' or prerequisites and table in TABLES[3:5]:
            predicate=" WHERE version<>'V323'" if table in TABLES[3:5] else ''
            query(db,f'INSERT INTO {table} SELECT * FROM `{LOCAL}`.{table}'+predicate+';')
    # Exercise the actual V322 predecessor independently of the current fresh
    # baseline, which already contains the final V323 collection definitions.
    query(db,'ALTER TABLE zsjos_student_info_form DROP INDEX uk_student_info_case, '
          'DROP COLUMN registration_case_id, DROP COLUMN order_item_id, '
          'DROP COLUMN person_id, DROP COLUMN product_id, DROP COLUMN sent_by_user_id, '
          'DROP COLUMN sent_at, DROP COLUMN fields_snapshot, DROP COLUMN category_path_snapshot, '
          'DROP COLUMN product_name, MODIFY COLUMN lead_id BIGINT NOT NULL; '
          'ALTER TABLE zsjos_student_info_form_config DROP COLUMN education_category_ids_json;')
    return db
def ledgers(db): return query(db,"SELECT version,checksum FROM zsjos_schema_version ORDER BY version; SELECT module_code,version,checksum FROM zsjos_module_schema_version ORDER BY module_code,version;")
def verify(db):
    query(db,SQL.read_text(encoding='utf-8'))
    first=ledgers(db);query(db,SQL.read_text(encoding='utf-8'));assert first==ledgers(db)
    assert scalar(db,"SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_student_info_form' AND column_name IN ('registration_case_id','person_id','sent_at','fields_snapshot');")==4
    assert scalar(db,"SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='zsjos_student_info_image';")==1
    assert query(db,"SELECT HEX(description) FROM zsjos_schema_version WHERE version='V323';").strip()=='报名履约学员信息收集表'.encode().hex().upper()
    assert query(db,"SELECT HEX(name) FROM system_menu WHERE permission='zsjos:student-info-form:sent' AND deleted=0;").strip()=='确认已发送收集表'.encode().hex().upper()
    expected_columns,expected_indexes,_=desired_schema(ROOT/'script/sql/mysql/schema/core.sql')
    for table in ['zsjos_student_info_form','zsjos_student_info_form_config','zsjos_student_info_image']:
        actual={tuple(row.split(chr(9))[:2]):tuple(row.split(chr(9))[2:]) for row in query(db,f"SELECT table_name,column_name,column_type,is_nullable FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='{table}' ORDER BY ordinal_position;").splitlines()}
        # archive_only belongs to V327 and is absent at this V323 checkpoint.
        expected={key:value for key,value in expected_columns.items()
                  if key[0]==table and key!=('zsjos_student_info_image','archive_only')}
        assert actual==expected,(table,actual,expected)
        for (owner,index),(unique,columns) in expected_indexes.items():
            if owner!=table:continue
            rows=query(db,f"SELECT non_unique,column_name FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='{table}' AND index_name='{index}' ORDER BY seq_in_index;").splitlines()
            assert rows and int(rows[0].split(chr(9))[0])==unique and tuple(r.split(chr(9))[1] for r in rows)==columns,(table,index)
    print('PASS initial/replay/schema/ledgers/UTF-8',db)
def fail(db):
    before=ledgers(db)
    try: query(db,SQL.read_text(encoding='utf-8'))
    except RuntimeError: assert before==ledgers(db)
    else: raise AssertionError('failure expected')
if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--sync-local',action='store_true');args=parser.parse_args()
    verify(clone('initial'))
    partial=clone('partial');query(partial,'ALTER TABLE zsjos_student_info_form ADD COLUMN registration_case_id BIGINT NULL;');verify(partial)
    missing=clone('prerequisite',False);fail(missing)
    for table in TABLES[3:5]: query(missing,f"INSERT INTO {table} SELECT * FROM `{LOCAL}`.{table} WHERE version<>'V323';")
    verify(missing)
    legacy=clone('legacy');query(legacy,"INSERT INTO zsjos_student_info_form(tenant_id,lead_id,sales_user_id,config_version_id,token_hash,token_ciphertext,status,expires_at) VALUES(1,1,1,1,REPEAT('a',64),'synthetic','SUBMITTED',NOW());");fail(legacy)
    assert scalar(legacy,'SELECT COUNT(*) FROM zsjos_student_info_form;')==1
    print('PASS prerequisites/partial recovery/legacy preservation/failure never advances ledgers')
    incompatible=clone('wrong_type');query(incompatible,'ALTER TABLE zsjos_student_info_form ADD COLUMN registration_case_id VARCHAR(32) NULL;');fail(incompatible)
    failed_ddl=clone('ddl_failure')
    # Force the final structure guard to fail after earlier DDL has already succeeded.
    query(failed_ddl,'ALTER TABLE zsjos_student_info_form ADD COLUMN fields_snapshot VARCHAR(10) NULL;');fail(failed_ddl)
    query(failed_ddl,'ALTER TABLE zsjos_student_info_form MODIFY COLUMN fields_snapshot LONGTEXT NULL;');verify(failed_ddl)
    ledger_failure=clone('ledger_failure');query(ledger_failure,"CREATE TRIGGER refuse_collection_ledger BEFORE INSERT ON zsjos_module_schema_version FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='synthetic ledger failure';")
    fail(ledger_failure);query(ledger_failure,'DROP TRIGGER refuse_collection_ledger;');verify(ledger_failure)
    print('PASS incompatible schema rejection/partial recovery/ledger transaction failure')
    if args.sync_local:
        assert scalar(LOCAL,'SELECT COUNT(*) FROM zsjos_student_info_form;')==0
        backup=ROOT/'backups/mysql'/('registration-student-info-'+STAMP+'.json');backup.parent.mkdir(parents=True,exist_ok=True)
        snapshots={t:{'ddl':query(LOCAL,f'SHOW CREATE TABLE {t};'),'rows':query(LOCAL,f'SELECT * FROM {t};')} for t in TABLES}
        backup.write_text(json.dumps(snapshots,ensure_ascii=False),encoding='utf-8')
        verify(LOCAL);print('PASS local additive synchronization; backup',backup)
