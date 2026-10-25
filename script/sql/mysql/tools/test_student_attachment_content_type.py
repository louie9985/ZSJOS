# -*- coding: utf-8 -*-
"""V341 controlled MySQL verification. Retains fixtures; --sync-local widens only MIME metadata."""
import argparse
import datetime
import json
from test_positioning_single_source import query, ROOT

LOCAL = 'ruoyi-vue-pro'
SQL = (ROOT / 'script/sql/mysql/migrations/V341__student_attachment_content_type.sql').read_text(encoding='utf-8')
STAMP = datetime.datetime.now().strftime('%Y%m%d%H%M%S%f')
MIME = 'application/vnd.openxmlformats-officedocument.wordprocessingml.document'


def ledger(db):
    return query(db, 'SELECT version,description,checksum FROM zsjos_schema_version ORDER BY version; '
                 'SELECT module_code,version,description,checksum FROM zsjos_module_schema_version ORDER BY module_code,version;')


def snapshot(db):
    return query(db, "SELECT COUNT(*),COALESCE(SUM(CRC32(CONCAT_WS(':',id,tenant_id,form_id,field_key,side,infra_file_id,content_type,file_size))),0) FROM zsjos_student_info_image;")


def fixture(suffix):
    db = 'attachment_mime_' + STAMP + '_' + suffix
    query(LOCAL, f'CREATE DATABASE `{db}` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;')
    for table in ['zsjos_student_info_image', 'zsjos_schema_version', 'zsjos_module_schema_version']:
        query(db, f'CREATE TABLE {table} LIKE `{LOCAL}`.{table};')
        if table != 'zsjos_student_info_image':
            query(db, f"INSERT INTO {table} SELECT * FROM `{LOCAL}`.{table} WHERE version='V323';")
    query(db, "ALTER TABLE zsjos_student_info_image MODIFY content_type VARCHAR(32) NOT NULL; "
          "INSERT INTO zsjos_student_info_image(id,tenant_id,form_id,field_key,side,infra_file_id,content_type,file_size) VALUES "
          "(1,1,1,'application_images','',1,'application/msword',100),(2,1,1,'education_report','',2,'application/pdf',100);")
    return db


def reject(db):
    before = ledger(db)
    try:
        query(db, SQL)
    except RuntimeError:
        pass
    else:
        raise AssertionError('expected migration failure')
    assert ledger(db) == before
    print('PASS failure preserves both ledgers:', db)


def verify(db):
    before = snapshot(db)
    query(db, SQL)
    assert snapshot(db) == before
    assert query(db, "SELECT character_maximum_length FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_student_info_image' AND column_name='content_type';").strip() == '128'
    first = ledger(db)
    query(db, SQL)
    assert ledger(db) == first and snapshot(db) == before
    assert query(db, "SELECT HEX(description) FROM zsjos_schema_version WHERE version='V341';").strip() == '学员附件类型长度修复'.encode().hex().upper()
    assert query(db, "SELECT COUNT(*) FROM zsjos_module_schema_version WHERE module_code='core' AND version='V341';").strip() == '1'
    # This is an isolated synthetic insert, never a real business attachment.
    query(db, f"INSERT INTO zsjos_student_info_image(id,tenant_id,form_id,field_key,side,infra_file_id,content_type,file_size) VALUES (3,1,1,'application_images','',3,'{MIME}',100);")
    assert query(db, 'SELECT content_type FROM zsjos_student_info_image WHERE id=3;').strip() == MIME
    print('PASS initial/replay/preserved DOC and PDF/DOCX write/UTF-8:', db)


if __name__ == '__main__':
    args = argparse.ArgumentParser()
    args.add_argument('--sync-local', action='store_true')
    options = args.parse_args()
    fresh = fixture('fresh')
    try:
        query(fresh, f"INSERT INTO zsjos_student_info_image(tenant_id,form_id,field_key,side,infra_file_id,content_type,file_size) VALUES (1,1,'application_images','',3,'{MIME}',100);")
    except RuntimeError as error:
        assert 'Data too long' in str(error)
        print('REPRODUCED original schema rejecting DOCX MIME')
    else:
        raise AssertionError('old schema must reproduce DOCX persistence failure')
    verify(fresh)
    partial = fixture('partial')
    query(partial, 'ALTER TABLE zsjos_student_info_image MODIFY content_type VARCHAR(128) NOT NULL;')
    verify(partial)
    marked = fixture('marked')
    for table in ['zsjos_schema_version', 'zsjos_module_schema_version']:
        query(marked, f"INSERT INTO {table} SELECT * FROM `{fresh}`.{table} WHERE version='V341';")
    verify(marked)
    missing = fixture('missing')
    query(missing, "UPDATE zsjos_module_schema_version SET version='fixture_missing' WHERE version='V323';")
    reject(missing)
    wrong = fixture('wrong')
    query(wrong, 'ALTER TABLE zsjos_student_info_image MODIFY content_type VARCHAR(64) NOT NULL;')
    reject(wrong)
    failed = fixture('failed')
    query(failed, "CREATE TRIGGER reject_v341 BEFORE INSERT ON zsjos_module_schema_version FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='fixture ledger failure';")
    reject(failed)
    query(failed, 'DROP TRIGGER reject_v341;')
    verify(failed)
    if options.sync_local:
        before = snapshot(LOCAL)
        backup = ROOT / 'backups/mysql' / ('attachment-mime-' + STAMP + '.json')
        backup.parent.mkdir(parents=True, exist_ok=True)
        backup.write_text(json.dumps(dict(schema=query(LOCAL, 'SHOW CREATE TABLE zsjos_student_info_image;'),
                                         ledgers=ledger(LOCAL), metadata_signature=before), ensure_ascii=False, indent=2), encoding='utf-8')
        query(LOCAL, SQL)
        assert snapshot(LOCAL) == before
        assert query(LOCAL, "SELECT character_maximum_length FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_student_info_image' AND column_name='content_type';").strip() == '128'
        query(LOCAL, SQL)
        assert snapshot(LOCAL) == before
        print('PASS scoped local correction, existing attachment metadata unchanged; backup:', backup)
