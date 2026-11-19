"""UTF-8. Retained isolated V380 fixtures; no application writes."""
from pathlib import Path
import uuid
from test_positioning_single_source import query
source='ruoyi-vue-pro'
sql=(Path(__file__).resolve().parents[1]/'migrations/V380__enrollment_category_agreement.sql').read_text(encoding='utf-8')
prefix='v380_'+uuid.uuid4().hex[:8]
def fixture(suffix, prerequisite=True):
    db=prefix+suffix
    query(source,'CREATE DATABASE '+db+' CHARACTER SET utf8mb4')
    for t in ['zsjos_enrollment_agreement','zsjos_schema_version','zsjos_module_schema_version']:
        query(db,f'CREATE TABLE {t} LIKE `{source}`.{t}')
    query(db,"CREATE TABLE zsjos_student_info_form(id BIGINT PRIMARY KEY, payload VARCHAR(40)); INSERT INTO zsjos_student_info_form VALUES(1,'preserved')")
    if prerequisite:
        for t in ['zsjos_schema_version','zsjos_module_schema_version']:
            query(db,f"INSERT INTO {t} SELECT * FROM `{source}`.{t} WHERE version='V378'")
    query(db,"INSERT INTO zsjos_enrollment_agreement(id,tenant_id,revision,published_version,draft_body,published_body) VALUES(1,1,1,1,'合成协议','合成协议')")
    return db
def markers(db):
    return query(db,'CHECKSUM TABLE zsjos_schema_version,zsjos_module_schema_version,zsjos_enrollment_agreement')
def reject(db):
    before=markers(db)
    try:query(db,sql)
    except RuntimeError:pass
    else:raise AssertionError('expected refusal')
    assert markers(db)==before
missing=fixture('_missing',False);reject(missing)
fresh=fixture('_fresh');before=query(fresh,'CHECKSUM TABLE zsjos_enrollment_agreement')
query(fresh,sql);first=markers(fresh);query(fresh,sql);assert first==markers(fresh)
assert before==query(fresh,'CHECKSUM TABLE zsjos_enrollment_agreement')
assert query(fresh,'SELECT payload,agreement_category_name IS NULL FROM zsjos_student_info_form').strip()=='preserved\t1'
assert query(fresh,'SELECT COUNT(*) FROM zsjos_enrollment_category_agreement').strip()=='0'
assert query(fresh,"SELECT HEX(description) FROM zsjos_schema_version WHERE version='V380'").strip()=='末级分类报名协议'.encode().hex().upper()
partial=fixture('_partial');query(partial,'CREATE TABLE zsjos_enrollment_category_agreement LIKE zsjos_enrollment_agreement');query(partial,sql)
invalid=fixture('_invalid');query(invalid,'ALTER TABLE zsjos_student_info_form ADD COLUMN agreement_category_name INT');reject(invalid)
ledger=fixture('_ledger');query(ledger,"CREATE TRIGGER fail_ledger BEFORE INSERT ON zsjos_module_schema_version FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='fixture failure'");reject(ledger)
query(ledger,'DROP TRIGGER fail_ledger');query(ledger,sql)
print('PASS initial/replay/missing prerequisite/partial DDL/invalid column/ledger rollback and recovery/preserved rows/UTF-8; retained',prefix)
