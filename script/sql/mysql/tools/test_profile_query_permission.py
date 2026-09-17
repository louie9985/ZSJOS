#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Scoped permission replay with current baseline schemas and synthetic authorization data."""
import re
import subprocess
import unittest
import zsjos_db as db


class ProfileQueryPermissionTest(unittest.TestCase):
    def sql(self, container, sql):
        result = subprocess.run(['docker','exec','-i','-w','/workspace',container,'mysql',
            '--default-character-set=utf8mb4','-uroot','-N','--batch','--raw','zsjos_test'],
            input=sql.encode('utf-8'),capture_output=True)
        self.assertEqual(0,result.returncode,result.stderr.decode('utf-8'))
        self.assertNotIn('ERROR ',result.stderr.decode('utf-8'))
        return result.stdout.decode('utf-8').strip()

    def test_scoped_replay(self):
        db.with_test_mysql(self.exercise)

    def exercise(self, container):
        baseline=(db.SQL_ROOT/'00-bootstrap-schema.sql').read_text(encoding='utf-8')
        for table in ['system_menu','system_role','system_role_menu','system_tenant_package']:
            statement=re.search(r'CREATE TABLE(?: IF NOT EXISTS)? `'+table+r'`\s*\(.*?;\s*',baseline,re.S)
            self.assertIsNotNone(statement,table)
            self.sql(container,statement.group())
        self.sql(container,"""SET NAMES utf8mb4;
          INSERT INTO system_menu(id,name,permission,type,parent_id,path,status,deleted) VALUES
          (6970,'Retired','zsjos:media-account:query',2,1,'accounts',1,1),
          (7022,'Students','zsjos:media-student:query-my',2,1,'media-students',0,0),
          (7003,'Edit','zsjos:media-account:edit',3,7022,'',0,0),
          (73603,'Maintain','zsjos:media-account:maintenance',3,7022,'',0,0),
          (6998,'Query all','zsjos:media-account:query-all',3,7022,'',0,0);
          INSERT INTO system_role(id,name,code,sort,status,type,tenant_id) VALUES
          (1,'Writer','custom_writer',1,0,2,1),(2,'Other','custom_other',1,0,2,1),
          (3,'Maintainer','custom_maintainer',1,0,2,1),(4,'Reader','custom_reader',1,0,2,1);
          INSERT INTO system_role_menu(role_id,menu_id,tenant_id) VALUES (1,7003,1),(3,73603,1),(4,6998,1);
          INSERT INTO system_tenant_package(id,name,status,menu_ids) VALUES
          (1,'Included',0,'[7022]'),(2,'Unrelated',0,'[]');
        """)
        script='SOURCE script/sql/mysql/permissions/media-account-profile-query.sql;'
        self.sql(container,script)
        menu=self.sql(container,"SELECT id FROM system_menu WHERE permission='zsjos:media-account:query' AND deleted=0 AND status=0 AND parent_id=7022 AND type=3")
        self.assertTrue(menu.isdigit())
        self.assertEqual('0',self.sql(container,f"SELECT COUNT(*) FROM system_role_menu WHERE menu_id={menu} AND deleted=0"))
        self.assertEqual('1',self.sql(container,f"SELECT JSON_CONTAINS(menu_ids,'{menu}','$') FROM system_tenant_package WHERE id=1"))
        self.assertEqual('[]',self.sql(container,"SELECT menu_ids FROM system_tenant_package WHERE id=2"))
        self.assertEqual('1',self.sql(container,"SELECT deleted+0 FROM system_menu WHERE id=6970"))
        self.assertEqual('查看账号档案'.encode().hex().upper(),self.sql(container,f"SELECT HEX(name) FROM system_menu WHERE id={menu}"))
        self.sql(container,script)
        self.assertEqual('0',self.sql(container,f"SELECT COUNT(*) FROM system_role_menu WHERE menu_id={menu} AND deleted=0"))
        self.assertEqual('3',self.sql(container,"SELECT COUNT(*) FROM system_role_menu"))
        self.assertEqual('2',self.sql(container,"SELECT JSON_LENGTH(menu_ids) FROM system_tenant_package WHERE id=1"))
        self.assertEqual('1',self.sql(container,"SELECT COUNT(*) FROM system_menu WHERE permission='zsjos:media-account:query' AND deleted=0"))
        print('PASS: no automatic grants, unrelated role/package isolation, retired menu preserved, replay, UTF-8 HEX')


if __name__=='__main__': unittest.main()
