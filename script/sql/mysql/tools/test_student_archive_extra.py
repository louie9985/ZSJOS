# -*- coding: utf-8 -*-
"""Controlled V332 tests. Creates retained fixture databases; never changes business rows."""
from pathlib import Path
import datetime
from test_positioning_single_source import query, ROOT
SQL=(ROOT/'script/sql/mysql/migrations/V332__student_archive_extra.sql').read_text(encoding='utf-8')
STAMP=datetime.datetime.now().strftime('%Y%m%d%H%M%S%f')
def fixture(suffix):
    db='student_archive_extra_'+STAMP+'_'+suffix
    query('ruoyi-vue-pro',f'CREATE DATABASE {db} CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;')
    for name in ['zsjos_schema_version','zsjos_module_schema_version']:
        query(db,f'CREATE TABLE {name} LIKE `ruoyi-vue-pro`.{name};')
    query(db,"INSERT INTO zsjos_schema_version(version,description,checksum) VALUES('V327','fixture','fixture'); INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version) VALUES('core','V327','fixture','fixture','fixture');")
    return db
def ledger(db):
    return query(db,'SELECT version,description,checksum FROM zsjos_schema_version ORDER BY version; SELECT module_code,version,description,checksum FROM zsjos_module_schema_version ORDER BY module_code,version;')
def verify(db):
    query(db,SQL);before=ledger(db);query(db,SQL);assert before==ledger(db)
    assert query(db,"SELECT COUNT(*) FROM zsjos_schema_version WHERE version='V332';").strip()=='1'
    assert query(db,"SELECT COUNT(*) FROM zsjos_module_schema_version WHERE module_code='core' AND version='V332';").strip()=='1'
    assert query(db,"SELECT HEX(description) FROM zsjos_schema_version WHERE version='V332';").strip()=='学员考试与证书档案'.encode().hex().upper()
    assert query(db,"SELECT COUNT(*) FROM zsjos_student_archive_extra_revision; SELECT COUNT(*) FROM zsjos_student_archive_attachment;").strip()=='0\n0'
    print('PASS initial/replay/UTF-8/empty business scope',db,flush=True)
def reject(db):
    before=ledger(db)
    try: query(db,SQL)
    except RuntimeError: pass
    else: raise AssertionError('expected SQL failure')
    assert ledger(db)==before
    print('PASS failure leaves ledgers unchanged',db,flush=True)
if __name__=='__main__':
    fresh=fixture('fresh');verify(fresh)
    partial=fixture('partial')
    query(partial,'CREATE TABLE zsjos_student_archive_extra_revision LIKE '+fresh+'.zsjos_student_archive_extra_revision;')
    verify(partial)
    missing=fixture('prerequisite');query(missing,"UPDATE zsjos_schema_version SET version='fixture_missing' WHERE version='V327';");reject(missing)
    failure=fixture('ledger_failure');query(failure,"CREATE TRIGGER reject_v332 BEFORE INSERT ON zsjos_module_schema_version FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='fixture reject';");reject(failure)
    query(failure,'DROP TRIGGER reject_v332;');verify(failure)
    wrong=fixture('wrong_type')
    query(wrong,'CREATE TABLE zsjos_student_archive_extra_revision LIKE '+fresh+'.zsjos_student_archive_extra_revision; ALTER TABLE zsjos_student_archive_extra_revision MODIFY service_relation_id VARCHAR(64) NOT NULL;')
    reject(wrong)
    query(wrong,'ALTER TABLE zsjos_student_archive_extra_revision MODIFY service_relation_id BIGINT NOT NULL;');verify(wrong)
    query(fresh,"INSERT INTO zsjos_student_archive_extra_revision(service_relation_id,revision,operator_user_id,reason,idempotency_key,request_hash,values_json) VALUES(5,1,1,'fixture','fixture-one',REPEAT('a',64),'fixture encrypted snapshot');")
    preserved=query(fresh,'SELECT id,service_relation_id,revision,values_json FROM zsjos_student_archive_extra_revision;')
    query(fresh,SQL)
    assert query(fresh,'SELECT id,service_relation_id,revision,values_json FROM zsjos_student_archive_extra_revision;')==preserved
    try: query(fresh,"INSERT INTO zsjos_student_archive_extra_revision(service_relation_id,revision,operator_user_id,reason,idempotency_key,request_hash,values_json) VALUES(5,1,1,'fixture','fixture-two',REPEAT('a',64),'fixture');")
    except RuntimeError: pass
    else: raise AssertionError('duplicate service revision must fail')
    print('PASS existing synthetic revisions preserved and duplicate version rejected',fresh,flush=True)
