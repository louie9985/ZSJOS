# -*- coding: utf-8 -*-
"""Retained isolated MySQL fixtures; --apply-dev adds only V293 column/ledgers after backup."""
import datetime
import re
import subprocess
import sys
import tempfile
from pathlib import Path
from test_calendar_notifications import query, table, ROOT, SQL

MIGRATION = (SQL / 'migrations/V293__exam_schedule_attachments.sql').read_text(encoding='utf-8')
PREFIX = 'exam_att_' + datetime.datetime.now().strftime('%Y%m%d%H%M%S')
PREREQ = "INSERT INTO zsjos_schema_version(version,description,checksum) VALUES ('V287','fixture','fixture'); INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version) VALUES ('core','V287','fixture','fixture','fixture');"

def signature(db):
    return query(db,"SELECT column_type,is_nullable,column_default,HEX(column_comment) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_exam_schedule' AND column_name='attachment_ids_json';")[0]

def versions(db):
    return query(db,"SELECT version,checksum FROM zsjos_schema_version WHERE version='V293'; SELECT version,checksum FROM zsjos_module_schema_version WHERE module_code='core' AND version='V293';")[0]

def scoped():
    expected = None
    for case in ['initial','partial','prerequisite','wrong_type','ledger_failure','existing_marker']:
        db=PREFIX+'_'+case
        query(None, f'CREATE DATABASE `{db}` CHARACTER SET utf8mb4;')
        setup=table('zsjos_exam_schedule')+table('zsjos_schema_version')+table('zsjos_module_schema_version')
        if case!='prerequisite': setup+=PREREQ
        if case in ['partial','wrong_type']:
            setup+="ALTER TABLE zsjos_exam_schedule ADD COLUMN attachment_ids_json "+('varchar(20)' if case=='wrong_type' else 'json')+" DEFAULT NULL COMMENT '考期备注附件文件编号';"
        if case=='ledger_failure': setup+="CREATE TRIGGER reject_attachment_ledger BEFORE INSERT ON zsjos_module_schema_version FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='injected ledger failure';"
        if case=='existing_marker': setup+="INSERT INTO zsjos_schema_version(version,description,checksum) VALUES ('V293','fixture','preserve'); INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version) VALUES ('core','V293','fixture','preserve','fixture');"
        query(db,setup); before=versions(db)
        _,error=query(db,MIGRATION,force=True)
        if case in ['prerequisite','wrong_type','ledger_failure']:
            assert 'ERROR' in error and versions(db)==before, (case,error)
            repair={'prerequisite':PREREQ,'wrong_type':"ALTER TABLE zsjos_exam_schedule MODIFY attachment_ids_json json DEFAULT NULL COMMENT '考期备注附件文件编号';",'ledger_failure':'DROP TRIGGER reject_attachment_ledger;'}[case]
            query(db,repair); query(db,MIGRATION)
        else: assert not error,error
        if expected is None: expected=signature(db)
        assert signature(db)==expected
        records=versions(db); assert len(records.splitlines())==2
        query(db,MIGRATION); assert versions(db)==records
        # Real JSON matching for the reedit permission handoff, isolated from development rows.
        query(db,"INSERT INTO zsjos_exam_schedule(id,schedule_type,schedule_name,exact_date,record_status,remark,attachment_ids_json,tenant_id,deleted,revoked_by,reedit_claimed_at) VALUES (9101,'EXACT','官方考试通知','2027-01-01','REVOKED','图片与文档','[11,12]',1,1,7,NOW()),(9102,'EXACT','独立租户','2027-01-01','REVOKED','隔离','[11]',2,1,8,NOW());")
        predicate="SELECT COUNT(*) FROM zsjos_exam_schedule WHERE tenant_id=1 AND deleted=1 AND record_status='REVOKED' AND reedit_claimed_at IS NOT NULL AND revoked_by=7 AND JSON_CONTAINS(attachment_ids_json,CAST(11 AS JSON));"
        assert query(db,predicate)[0]=='1'
        assert query(db,predicate.replace('revoked_by=7','revoked_by=8'))[0]=='0'
        assert query(db,"SELECT HEX(remark) FROM zsjos_exam_schedule WHERE id=9101")[0]=='图片与文档'.encode().hex().upper()
        print('PASS',case,'schema/repeat/recovery/ledger/JSON/UTF-8',flush=True)
    return expected

def apply_dev(expected):
    db='ruoyi-vue-pro'
    assert len(query(db,"SELECT version FROM zsjos_schema_version WHERE version='V287'; SELECT version FROM zsjos_module_schema_version WHERE module_code='core' AND version='V287';")[0].splitlines())==2
    before=query(db,"SELECT COUNT(*) FROM zsjos_exam_schedule")[0]
    backup=Path(tempfile.gettempdir())/(PREFIX+'_before.sql')
    result=subprocess.run(['docker','exec','yudao-mysql','sh','-c','MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysqldump --default-character-set=utf8mb4 -uroot --single-transaction --skip-lock-tables ruoyi-vue-pro zsjos_exam_schedule zsjos_schema_version zsjos_module_schema_version'],capture_output=True,check=True)
    backup.write_bytes(result.stdout)
    query(db,MIGRATION)
    assert signature(db)==expected and len(versions(db).splitlines())==2
    assert query(db,"SELECT COUNT(*) FROM zsjos_exam_schedule")[0]==before
    print('PASS scoped development application/comparison; backup:',backup,flush=True)

def fresh(expected):
    def expand(path):
        return re.sub(r'^SOURCE\s+([^;]+);',lambda m:expand(ROOT/m.group(1)),path.read_text(encoding='utf-8-sig'),flags=re.M|re.I)
    db=PREFIX+'_fresh'; query(None,f'CREATE DATABASE `{db}` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;')
    query(db,expand(SQL/'bootstrap.sql'))
    applied=set(query(db,'SELECT version FROM zsjos_schema_version')[0].splitlines())
    for path in sorted((SQL/'migrations').glob('V*__*.sql')):
        if path.name.split('__')[0] not in applied: query(db,expand(path))
    assert signature(db)==expected
    print('PASS full fresh chain',db,flush=True)

if __name__=='__main__':
    expected=scoped()
    if '--apply-dev' in sys.argv: apply_dev(expected)
    if '--fresh' in sys.argv: fresh(expected)
    print('Retained databases:',PREFIX,flush=True)
