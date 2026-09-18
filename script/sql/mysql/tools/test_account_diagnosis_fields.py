#!/usr/bin/env python3
# UTF-8
"""Verify scoped internal-goal field policy and repeatable publication, optionally sync local."""
import datetime, json, sys
from pathlib import Path
from test_account_appearance_text import query, configs, ROOT
SQL=(ROOT/'script/sql/mysql/media-account-diagnosis-fields.sql').read_bytes()

def verify(before,after):
    active={r['status']:r for r in before if r['status'] in ('published','draft')}
    for status,row in active.items():
        expected=json.loads(json.dumps(row['fields']))
        for f in expected:
            if f['key']=='delivery_goals':
                f.update(ownerType='AUTO',sourceType='ACCOUNT',requiredForComplete=False)
        actual=next(r for r in after if r['status']==status)
        assert actual['fields']==expected
    for old in before:
        if old['status']=='archived': assert old in after

if __name__=='__main__':
    stamp=datetime.datetime.now().strftime('%Y%m%d%H%M%S')
    db='diagnosis_fields_verify_'+stamp
    query('',f'CREATE DATABASE {db} CHARACTER SET utf8mb4')
    query(db,'CREATE TABLE zsjos_media_account_field_config LIKE `ruoyi-vue-pro`.zsjos_media_account_field_config;')
    fields=[{'key':'delivery_goals','label':'内部交付目标约定','ownerType':'DIRECTOR','type':'textarea','sourceType':'MANUAL','requiredForComplete':True},
            {'key':'student_commitments','label':'学员承担事项','ownerType':'DIRECTOR','type':'textarea','sourceType':'MANUAL','requiredForComplete':True}]
    for i,status in enumerate(('published','draft'),1):
        hx=json.dumps(fields,ensure_ascii=False).encode().hex()
        query(db,f"INSERT INTO zsjos_media_account_field_config(version_no,status,fields_json,version,tenant_id) VALUES({i},'{status}',CONVERT(0x{hx} USING utf8mb4),0,1)")
    before=configs(db);query(db,SQL);after=configs(db);verify(before,after)
    query(db,SQL);assert configs(db)==after
    value=query(db,"SELECT HEX(JSON_UNQUOTE(JSON_EXTRACT(fields_json,'$[0].label'))) FROM zsjos_media_account_field_config WHERE status='published'").strip()
    assert value.lower()=='内部交付目标约定'.encode().hex()
    print('PASS published/draft, other fields unchanged, archived history, repeatability and UTF-8 HEX:',db)
    if '--sync-local' in sys.argv:
        local='ruoyi-vue-pro';before=configs(local)
        backup=ROOT/'backups/mysql'/('diagnosis-fields-'+stamp+'.json');backup.parent.mkdir(parents=True,exist_ok=True)
        backup.write_text(json.dumps(before,ensure_ascii=False,indent=2),encoding='utf-8')
        business=query(local,'CHECKSUM TABLE zsjos_media_account,system_role_menu')
        query(local,SQL);after=configs(local);verify(before,after);query(local,SQL);assert configs(local)==after
        assert business==query(local,'CHECKSUM TABLE zsjos_media_account,system_role_menu')
        print('PASS local scope, replay and business/grants unchanged; backup:',backup)
