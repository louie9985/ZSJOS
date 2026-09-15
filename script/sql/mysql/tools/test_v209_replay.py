#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Full bootstrap plus scoped V209 upgrade/replay checks in disposable MySQL."""
import json
import subprocess
import unittest
import zsjos_db as db

class V209ReplayTest(unittest.TestCase):
    def sql(self, container, sql):
        r=subprocess.run(["docker","exec","-i","-w","/workspace",container,"mysql","--default-character-set=utf8mb4","-uroot","-N","--batch","--raw","zsjos_test"],input=sql.encode("utf-8"),capture_output=True)
        self.assertEqual(0,r.returncode,r.stderr.decode("utf-8",errors="replace"))
        self.assertNotIn("ERROR ",r.stderr.decode("utf-8",errors="replace"))
        return r.stdout.decode("utf-8").strip()
    def test_full_bootstrap_upgrade_and_replay(self):
        db.with_test_mysql(self.exercise)
    def exercise(self,container):
        bootstrap=(db.SQL_ROOT/"bootstrap.sql").read_text(encoding="utf-8")
        self.sql(container,bootstrap.replace("SOURCE script/sql/mysql/migrations/V209__media_account_profile.sql;", ""))
        # Bootstrap now supplies only the baseline; mirror the manifest executor's pending order.
        installed=set(self.sql(container,"SELECT version FROM zsjos_module_schema_version WHERE module_code='core'").splitlines())
        for migration in db.migrations_for('core',db.load_manifests()['core']):
            if migration.number < 209 and migration.version not in installed:
                self.sql(container,f"SOURCE {migration.path.relative_to(db.ROOT).as_posix()};")
        self.assertEqual('0',self.sql(container,"SELECT COUNT(*) FROM system_menu WHERE permission='zsjos:media-account:query' AND deleted=0"))
        # A custom role proves grant migration follows configured capabilities, never role names.
        self.sql(container,"""INSERT INTO system_role(id,name,code,sort,status,type,tenant_id) VALUES
          (900209,'Synthetic writer','synthetic_profile_writer',1,0,2,1),
          (900210,'Synthetic unrelated','synthetic_profile_unrelated',1,0,2,1);
          INSERT INTO system_role_menu(role_id,menu_id,tenant_id)
          SELECT 900209,id,1 FROM system_menu WHERE permission='zsjos:media-account:maintenance' AND deleted=0;
        """)
        # Synthetic non-personal historical values prove migration preserves account data and drafts.
        self.sql(container,"""SET NAMES utf8mb4;
          INSERT INTO zsjos_media_account_field_config(version_no,status,fields_json,version,tenant_id)
          SELECT 2,'draft',JSON_ARRAY(JSON_OBJECT('key','nickname','label','草稿名称','type','text','required',false,'enabled',true,'searchable',false,'sort',10)),0,1
          WHERE NOT EXISTS(SELECT 1 FROM zsjos_media_account_field_config WHERE tenant_id=1 AND status='draft');
        """)
        self.sql(container,"""ALTER TABLE zsjos_media_account MODIFY owner_operator_user_id bigint NOT NULL, MODIFY platform_value varchar(100) NOT NULL, MODIFY platform_label_snapshot varchar(100) NOT NULL, MODIFY nickname varchar(255) NOT NULL;
          INSERT INTO zsjos_media_account(id,account_no,ownership_type,owner_operator_user_id,platform_value,platform_label_snapshot,nickname,detail_values_json,detail_snapshot_json,tenant_id)
          VALUES(900209,'TEST-PROFILE-209','student',900209,'test','historical platform','historical name',JSON_OBJECT('uid','historic-id'),JSON_ARRAY(JSON_OBJECT('key','uid','value','historic-id','displayValue','historic-id')),1);
        """)
        historical=self.sql(container,"SELECT CONCAT(platform_value,'|',platform_label_snapshot,'|',nickname,'|',detail_values_json,'|',detail_snapshot_json) FROM zsjos_media_account WHERE id=900209")
        draft=self.sql(container,"SELECT fields_json FROM zsjos_media_account_field_config WHERE tenant_id=1 AND status='draft'")
        self.sql(container,"SOURCE script/sql/mysql/migrations/V209__media_account_profile.sql;")
        query_menu=self.sql(container,"SELECT id FROM system_menu WHERE permission='zsjos:media-account:query' AND deleted=0 AND status=0 AND type=3 AND parent_id=7022")
        self.assertTrue(query_menu.isdigit())
        self.assertEqual('1',self.sql(container,f"SELECT COUNT(*) FROM system_role_menu WHERE role_id=900209 AND menu_id={query_menu} AND deleted=0"))
        self.assertEqual('0',self.sql(container,f"SELECT COUNT(*) FROM system_role_menu WHERE role_id=900210 AND menu_id={query_menu} AND deleted=0"))
        self.assertEqual('1',self.sql(container,"SELECT deleted+0 FROM system_menu WHERE id=6970"))
        self.assertEqual('查看账号档案'.encode().hex().upper(),self.sql(container,f"SELECT HEX(name) FROM system_menu WHERE id={query_menu}"))
        grants_before=self.sql(container,f"SELECT COUNT(*) FROM system_role_menu WHERE menu_id={query_menu} AND deleted=0")
        fields=json.loads(self.sql(container,"SELECT fields_json FROM zsjos_media_account_field_config WHERE tenant_id=1 AND status='published'"))
        by_key={f['key']:f for f in fields}
        self.assertEqual(57,len(fields))
        self.assertEqual('OPERATOR',by_key['nickname']['ownerType'])
        self.assertEqual('DIRECTOR',by_key['joining_goal']['ownerType'])
        self.assertEqual('AUTO',by_key['stage']['ownerType'])
        self.assertEqual('OPERATOR',by_key['cover']['ownerType'])
        self.assertTrue(all(not f['requiredForCreate'] for f in fields))
        self.assertEqual(draft,self.sql(container,"SELECT fields_json FROM zsjos_media_account_field_config WHERE tenant_id=1 AND status='draft'"))
        self.assertEqual(historical,self.sql(container,"SELECT CONCAT(platform_value,'|',platform_label_snapshot,'|',nickname,'|',detail_values_json,'|',detail_snapshot_json) FROM zsjos_media_account WHERE id=900209"))
        before=self.sql(container,"SELECT COUNT(*) FROM zsjos_media_account_field_config")
        self.sql(container,"SOURCE script/sql/mysql/migrations/V209__media_account_profile.sql;")
        self.assertEqual(before,self.sql(container,"SELECT COUNT(*) FROM zsjos_media_account_field_config"))
        self.assertEqual(grants_before,self.sql(container,f"SELECT COUNT(*) FROM system_role_menu WHERE menu_id={query_menu} AND deleted=0"))
        self.assertEqual('1',self.sql(container,"SELECT COUNT(*) FROM system_menu WHERE permission='zsjos:media-account:query' AND deleted=0"))
        self.assertEqual('4',self.sql(container,"SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_media_account' AND column_name IN ('owner_operator_user_id','platform_value','platform_label_snapshot','nickname') AND is_nullable='YES'"))
        self.assertEqual('1',self.sql(container,"SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='zsjos_media_account_profile_entry'"))
        self.assertEqual('0',self.sql(container,"SELECT COUNT(*) FROM system_dict_data WHERE dict_type IN ('zsjos_account_publish_rhythm','zsjos_account_content_format')"))
        self.assertEqual('账号发布节奏'.encode().hex().upper(), self.sql(container,"SELECT HEX(name) FROM system_dict_type WHERE type='zsjos_account_publish_rhythm' AND deleted=b'0'"))
        # Full fresh bootstrap source has been executed in its documented order, including V209.
        # Capture normalized schema for a read-only comparison with the development database.
        schema=self.sql(container,"SELECT table_name,column_name,column_type,is_nullable,COALESCE(column_default,'NULL'),extra,COALESCE(collation_name,'NULL') FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name IN ('zsjos_media_account','zsjos_media_account_profile_entry') ORDER BY table_name,ordinal_position")
        target=db.ROOT/'output/account-profile-acceptance'
        target.mkdir(exist_ok=True)
        (target/'expected-schema.tsv').write_text(schema+'\n',encoding='utf-8')
        print('PASS: full prerequisite bootstrap, 57 fields, responsibility, draft preservation, nullable columns, UTF-8 HEX, replay')

if __name__ == '__main__': unittest.main()
