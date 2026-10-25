# -*- coding: utf-8 -*-
"""V342 controlled MySQL verification; retain fixtures and optionally sync additive local DDL."""
import argparse
import datetime
import json
from test_positioning_single_source import query, ROOT

LOCAL='ruoyi-vue-pro'
SQL=(ROOT/'script/sql/mysql/migrations/V342__student_information_draft.sql').read_text(encoding='utf-8')
STAMP=datetime.datetime.now().strftime('%Y%m%d%H%M%S%f')

def ledger(db):
    return query(db,'SELECT version,checksum FROM zsjos_schema_version ORDER BY version; SELECT module_code,version,checksum FROM zsjos_module_schema_version ORDER BY module_code,version;')

def fixture(suffix):
    db='collection_draft_'+STAMP+'_'+suffix
    query(LOCAL,f'CREATE DATABASE `{db}` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;')
    for table in ['zsjos_student_info_form','zsjos_schema_version','zsjos_module_schema_version']:
        query(db,f'CREATE TABLE {table} LIKE `{LOCAL}`.{table};')
        if table!='zsjos_student_info_form':query(db,f"INSERT INTO {table} SELECT * FROM `{LOCAL}`.{table} WHERE version='V323';")
    columns=query(db,"SELECT column_name FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_student_info_form' AND column_name IN ('draft_snapshot','draft_revision','draft_saved_at');").splitlines()
    # Empty fixture only: reconstruct the documented predecessor without touching real records.
    for column in columns: query(db,f'ALTER TABLE zsjos_student_info_form DROP COLUMN {column};')
    query(db,"INSERT INTO zsjos_student_info_form(id,tenant_id,sales_user_id,config_version_id,token_hash,token_ciphertext,status,expires_at) VALUES (1,1,1,1,REPEAT('a',64),'synthetic-encrypted-token','DRAFT',DATE_ADD(NOW(),INTERVAL 1 DAY));")
    return db

def verify(db):
    before=query(db,'SELECT id,status,token_ciphertext FROM zsjos_student_info_form;')
    query(db,SQL);first=ledger(db);query(db,SQL);assert ledger(db)==first
    assert query(db,'SELECT id,status,token_ciphertext FROM zsjos_student_info_form;')==before
    assert query(db,'SELECT draft_revision,draft_snapshot IS NULL,draft_saved_at IS NULL FROM zsjos_student_info_form WHERE id=1;').strip()=='0\t1\t1'
    assert query(db,"SELECT HEX(description) FROM zsjos_schema_version WHERE version='V342';").strip()=='学员信息草稿自动保存'.encode().hex().upper()
    query(db,"UPDATE zsjos_student_info_form SET draft_snapshot='synthetic-encrypted-draft',draft_revision=1,draft_saved_at=NOW() WHERE id=1;")
    query(db,SQL)
    assert query(db,'SELECT draft_revision,draft_snapshot FROM zsjos_student_info_form WHERE id=1;').strip()=='1\tsynthetic-encrypted-draft'
    print('PASS initial/replay/preserved form and draft/UTF-8',db)

def reject(db):
    before=ledger(db)
    try:query(db,SQL)
    except RuntimeError:pass
    else:raise AssertionError('expected failure')
    assert ledger(db)==before
    print('PASS failure leaves both ledgers unchanged',db)

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--sync-local',action='store_true');args=parser.parse_args()
    verify(fixture('fresh'))
    partial=fixture('partial');query(partial,'ALTER TABLE zsjos_student_info_form ADD COLUMN draft_snapshot MEDIUMTEXT NULL;');verify(partial)
    missing=fixture('missing');query(missing,"UPDATE zsjos_module_schema_version SET version='missing' WHERE version='V323';");reject(missing)
    wrong=fixture('wrong');query(wrong,'ALTER TABLE zsjos_student_info_form ADD COLUMN draft_revision VARCHAR(20) NULL;');reject(wrong)
    failed=fixture('failed');query(failed,"CREATE TRIGGER reject_v342 BEFORE INSERT ON zsjos_module_schema_version FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='synthetic ledger failure';");reject(failed)
    query(failed,'DROP TRIGGER reject_v342;');verify(failed)
    if args.sync_local:
        before=query(LOCAL,"SELECT COUNT(*),COALESCE(SUM(CRC32(CONCAT_WS(':',id,tenant_id,status,token_hash))),0) FROM zsjos_student_info_form;")
        backup=ROOT/'backups/mysql'/('collection-draft-'+STAMP+'.json');backup.parent.mkdir(parents=True,exist_ok=True)
        backup.write_text(json.dumps(dict(schema=query(LOCAL,'SHOW CREATE TABLE zsjos_student_info_form;'),ledgers=ledger(LOCAL),signature=before),ensure_ascii=False,indent=2),encoding='utf-8')
        query(LOCAL,SQL);first=ledger(LOCAL);query(LOCAL,SQL);assert ledger(LOCAL)==first
        assert query(LOCAL,"SELECT COUNT(*),COALESCE(SUM(CRC32(CONCAT_WS(':',id,tenant_id,status,token_hash))),0) FROM zsjos_student_info_form;")==before
        print('PASS scoped local migration/preserved records; backup:',backup)
