#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Replay the cover correction with reordered fields, drafts and multiple tenants."""
import json
import re
import subprocess
import unittest

import zsjos_db as db


class CoverOperatorTest(unittest.TestCase):
    def sql(self, container, sql):
        result = subprocess.run(
            ['docker', 'exec', '-i', '-w', '/workspace', container, 'mysql',
             '--default-character-set=utf8mb4', '-uroot', '-N', '--batch', '--raw', 'zsjos_test'],
            input=sql.encode('utf-8'), capture_output=True)
        self.assertEqual(0, result.returncode, result.stderr.decode('utf-8', errors='replace'))
        self.assertNotIn('ERROR ', result.stderr.decode('utf-8', errors='replace'))
        return result.stdout.decode('utf-8').strip()

    def test_scoped_replay(self):
        db.with_test_mysql(self.exercise)

    def exercise(self, container):
        schema = (db.SQL_ROOT / '00-bootstrap-schema.sql').read_text(encoding='utf-8')
        ddl = re.search(r'CREATE TABLE IF NOT EXISTS `zsjos_media_account_field_config` \(.*?;\n', schema, re.S).group()
        self.sql(container, ddl)
        fields = [dict(key='custom', label='保留字段', type='text', ownerType='DIRECTOR'),
                  dict(key='cover', label='主页截图', type='image', ownerType='UNASSIGNED',
                       requiredForComplete=False, enabled=True)]
        encoded = json.dumps(fields, ensure_ascii=False).replace("'", "''")
        self.sql(container, f"""SET NAMES utf8mb4;
          INSERT INTO zsjos_media_account_field_config(id,version_no,status,fields_json,version,tenant_id)
          VALUES(1,1,'archived','{encoded}',0,1),(2,2,'published','{encoded}',3,1),
          (3,3,'draft','{encoded}',5,1),(4,1,'published','{encoded}',0,2),
          (5,1,'published','{encoded}',0,3),(6,1,'published','[]',0,4);
          UPDATE zsjos_media_account_field_config SET fields_json=JSON_SET(fields_json,'$[1].ownerType','DIRECTOR') WHERE id=5;
        """)
        original = self.sql(container, 'SELECT id,fields_json,version FROM zsjos_media_account_field_config ORDER BY id')
        correction = 'SOURCE script/sql/mysql/media-account-cover-operator.sql;'
        self.sql(container, correction)
        self.assertEqual(original, self.sql(container, 'SELECT id,fields_json,version FROM zsjos_media_account_field_config WHERE id<=6 ORDER BY id'))
        self.assertEqual('archived\narchived\ndraft\narchived\npublished\npublished',
                         self.sql(container, 'SELECT status FROM zsjos_media_account_field_config WHERE id<=6 ORDER BY id'))
        updated = json.loads(self.sql(container, "SELECT fields_json FROM zsjos_media_account_field_config WHERE tenant_id=1 AND status='published'"))
        fields[1]['ownerType'] = 'OPERATOR'
        self.assertEqual(fields, updated)
        self.assertEqual('4', self.sql(container, "SELECT version_no FROM zsjos_media_account_field_config WHERE tenant_id=1 AND status='published'"))
        self.assertEqual('主页截图'.encode().hex().upper(), self.sql(container,
            "SELECT HEX(JSON_UNQUOTE(JSON_EXTRACT(fields_json,'$[1].label'))) FROM zsjos_media_account_field_config WHERE tenant_id=1 AND status='published'"))
        before = self.sql(container, 'SELECT * FROM zsjos_media_account_field_config ORDER BY id')
        self.sql(container, correction)
        self.assertEqual(before, self.sql(container, 'SELECT * FROM zsjos_media_account_field_config ORDER BY id'))
        print('PASS: publication, tenant isolation, draft/history/custom-field preservation, UTF-8 HEX and repeatability')


if __name__ == '__main__':
    unittest.main()
