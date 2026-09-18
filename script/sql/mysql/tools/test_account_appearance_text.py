#!/usr/bin/env python3
# UTF-8
"""Replay scoped configuration correction in a retained local verification database."""
import datetime
import json
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[4]
SQL = (ROOT / 'script/sql/mysql/media-account-appearance-text.sql').read_bytes()


def query(db, sql):
    result = subprocess.run(['docker', 'exec', '-i', 'yudao-mysql', 'sh', '-c',
        'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot --default-character-set=utf8mb4 -NB ' + db],
        input=sql.encode('utf-8') if isinstance(sql, str) else sql,
        stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=True)
    return result.stdout.decode('utf-8')


def configs(db):
    return [json.loads(line) for line in query(db,
        "SELECT JSON_OBJECT('id',id,'status',status,'fields',fields_json) FROM zsjos_media_account_field_config ORDER BY id").splitlines()]


if __name__ == '__main__':
    db = 'appearance_text_verify_' + datetime.datetime.now().strftime('%Y%m%d%H%M%S')
    query('', f'CREATE DATABASE {db} CHARACTER SET utf8mb4')
    query(db, 'CREATE TABLE zsjos_media_account_field_config LIKE `ruoyi-vue-pro`.zsjos_media_account_field_config;'
        'CREATE TABLE zsjos_media_account LIKE `ruoyi-vue-pro`.zsjos_media_account;')
    fields = [{'key': k, 'label': label, 'type': 'image', 'ownerType': 'OPERATOR', 'required': True}
        for k, label in [('avatar', '头像设置'), ('background', '背景设置'), ('cover', '主页截图')]]
    encoded = json.dumps(fields, ensure_ascii=False).encode('utf-8').hex()
    for version, status in enumerate(('published', 'draft'), 1):
        query(db, "INSERT INTO zsjos_media_account_field_config(version_no,status,fields_json,version,tenant_id) "
            f"VALUES({version},'{status}',CONVERT(0x{encoded} USING utf8mb4),0,1)")
    query(db, SQL)
    first = configs(db)
    assert len(first) == 3
    for row in first:
        expected = json.loads(json.dumps(fields))
        if row['status'] != 'archived':
            for field in expected[:2]:
                field['type'] = 'textarea'
        assert row['fields'] == expected
    query(db, SQL)
    assert configs(db) == first
    print('PASS published/draft conversion, archived preservation, unrelated fields and idempotence:', db)
