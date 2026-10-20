# -*- coding: utf-8 -*-
"""Controlled V327 execution only; retain test databases, never modify target business data."""
import datetime
from test_positioning_single_source import query, ROOT
SQL = (ROOT/'script/sql/mysql/migrations/V327__student_archive.sql').read_text(encoding='utf-8')
LOCAL = 'ruoyi-vue-pro'
STAMP = datetime.datetime.now().strftime('%Y%m%d%H%M%S%f')
def scalar(db,sql): return int(query(db,sql).strip())
def clone(suffix):
    db='student_archive_'+STAMP+'_'+suffix
    query(LOCAL,f'CREATE DATABASE {db} CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;')
    for table in ['zsjos_student_info_form','zsjos_student_info_image','system_menu','zsjos_schema_version','zsjos_module_schema_version']:
        query(db,f'CREATE TABLE {table} LIKE `{LOCAL}`.{table};')
        if table in ['system_menu','zsjos_schema_version','zsjos_module_schema_version']:
            predicate=" WHERE version<>'V327'" if table!='system_menu' else " WHERE permission NOT IN ('zsjos:student-archive:read','zsjos:student-archive:update')"
            query(db,f'INSERT INTO {table} SELECT * FROM `{LOCAL}`.{table}'+predicate+';')
    # The Docker verification source can lack deployed ledgers; establish only required fixture prerequisites.
    query(db,"INSERT INTO zsjos_schema_version(version,description,checksum) SELECT 'V324','fixture prerequisite','fixture' WHERE NOT EXISTS(SELECT 1 FROM zsjos_schema_version WHERE version='V324'); INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version) SELECT 'core','V324','fixture prerequisite','fixture','fixture' WHERE NOT EXISTS(SELECT 1 FROM zsjos_module_schema_version WHERE module_code='core' AND version='V324');")
    return db
def ledger(db): return query(db,"SELECT version,checksum FROM zsjos_schema_version; SELECT module_code,version,checksum FROM zsjos_module_schema_version;")
def verify(db):
    query(db,SQL);before=ledger(db);query(db,SQL);assert before==ledger(db)
    assert scalar(db,"SELECT COUNT(*) FROM system_menu WHERE permission IN ('zsjos:student-archive:read','zsjos:student-archive:update') AND deleted=0")==2
    assert scalar(db,"SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_student_info_image' AND column_name='archive_only'")==1
    assert query(db,"SELECT HEX(name) FROM system_menu WHERE permission='zsjos:student-archive:read';").strip()=='查看学员档案'.encode().hex().upper()
    assert scalar(db,"SELECT COUNT(*) FROM zsjos_schema_version WHERE version='V327'")==1
    assert scalar(db,"SELECT COUNT(*) FROM zsjos_module_schema_version WHERE module_code='core' AND version='V327'")==1
    print('PASS initial/replay/UTF-8/permissions/ledgers',db)
def fail(db):
    before=ledger(db)
    try: query(db,SQL)
    except RuntimeError: pass
    else: raise AssertionError('expected failure')
    assert ledger(db)==before
    print('PASS failure does not advance ledgers',db)
if __name__=='__main__':
    fresh=clone('fresh');verify(fresh)
    # Only controlled fixture rows are removed; source/target grants never modified.
    partial=clone('partial');query(partial,SQL);query(partial,"DELETE FROM system_menu WHERE permission='zsjos:student-archive:update'; DELETE FROM zsjos_schema_version WHERE version='V327'; DELETE FROM zsjos_module_schema_version WHERE version='V327';");verify(partial)
    prerequisite=clone('prerequisite');query(prerequisite,"DELETE FROM zsjos_schema_version WHERE version='V324';");fail(prerequisite)
    failed=clone('ledger_failure');query(failed,"CREATE TRIGGER reject_archive_version BEFORE INSERT ON zsjos_module_schema_version FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='fixture reject';");fail(failed)
    assert scalar(failed,"SELECT COUNT(*) FROM system_menu WHERE permission LIKE 'zsjos:student-archive:%'")==0
    query(failed,'DROP TRIGGER reject_archive_version;');verify(failed)
