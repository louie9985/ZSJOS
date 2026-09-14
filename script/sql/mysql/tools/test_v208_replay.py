#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Disposable MySQL regression for V208 viral-account template publication."""

import hashlib
import re
import subprocess
import time
import unittest

import zsjos_db as db


class V208ReplayTest(unittest.TestCase):
    def execute(self, container, sql):
        for attempt in range(20):
            result = subprocess.run(
                ["docker", "exec", "-i", "-w", "/workspace", container,
                 "mysql", "--default-character-set=utf8mb4",
                 "-uroot", "--batch", "--raw", "--skip-column-names", "zsjos_test"],
                input=sql.encode("utf-8"), capture_output=True, check=False,
            )
            if result.returncode == 0 or b"ERROR 2002" not in result.stderr or attempt == 19:
                break
            time.sleep(1)
        self.assertEqual(result.returncode, 0, result.stderr.decode("utf-8", errors="replace"))
        return result.stdout.decode("utf-8")

    def test_upgrade_scenarios_and_replay(self):
        db.with_test_mysql(self.exercise)

    def exercise(self, container):
        migration = (db.SQL_ROOT / "migrations/V208__viral_account_template_v3.sql").read_text(encoding="utf-8")
        canonical_match = re.search(r"  SET v_fields='(\[.*\])';", migration)
        self.assertIsNotNone(canonical_match)
        canonical_json = canonical_match.group(1)
        canonical_hash = hashlib.sha256(canonical_json.encode("utf-8")).hexdigest()

        setup = f"""
        SET NAMES utf8mb4;
        CREATE TABLE zsjos_schema_version (
          version varchar(64) PRIMARY KEY, description varchar(255) NOT NULL,
          checksum varchar(128), installed_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
        CREATE TABLE zsjos_module_schema_version (
          module_code varchar(32) NOT NULL, version varchar(64) NOT NULL,
          description varchar(255) NOT NULL, checksum varchar(128),
          release_version varchar(64) NOT NULL, installed_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
          PRIMARY KEY (module_code,version)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
        CREATE TABLE zsjos_material_type (
          id bigint NOT NULL, code varchar(64) NOT NULL, current_schema_version_id bigint,
          version int NOT NULL DEFAULT 0, creator varchar(64) DEFAULT '',
          create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP, updater varchar(64) DEFAULT '',
          update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
          deleted bit(1) NOT NULL DEFAULT b'0', tenant_id bigint NOT NULL,
          PRIMARY KEY (id)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
        CREATE TABLE zsjos_material_schema_version (
          id bigint NOT NULL AUTO_INCREMENT, material_type_id bigint NOT NULL, version_no int NOT NULL,
          status varchar(24) NOT NULL, fields_json json NOT NULL, schema_hash varchar(64) NOT NULL,
          published_by_user_id bigint, published_at datetime, version int NOT NULL DEFAULT 0,
          creator varchar(64) DEFAULT '', create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
          updater varchar(64) DEFAULT '', update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
          deleted bit(1) NOT NULL DEFAULT b'0', tenant_id bigint NOT NULL,
          PRIMARY KEY (id), UNIQUE KEY uk_schema (tenant_id,material_type_id,version_no,deleted)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
        INSERT INTO zsjos_schema_version(version,description) VALUES ('V194','fixture');
        INSERT INTO zsjos_module_schema_version(module_code,version,description,release_version)
          VALUES ('core','V194','fixture','test');
        INSERT INTO zsjos_material_type(id,code,current_schema_version_id,tenant_id) VALUES
          (101,'viral_account',NULL,1),(102,'viral_account',201,2),
          (103,'viral_account',202,3),(104,'viral_account',203,4),
          (105,'viral_account',204,5),(106,'other_type',NULL,6);
        INSERT INTO zsjos_material_schema_version
          (id,material_type_id,version_no,status,fields_json,schema_hash,published_at,tenant_id)
        VALUES
          (201,102,1,'PUBLISHED',JSON_ARRAY(),'25df599cf13a2f333b0faa545de2d5e99d4a0ecf789cbfa2a8cf45e60217e31e',NOW(),2),
          (202,103,2,'PUBLISHED',JSON_ARRAY(),'b1369fe4d75afb37b65e1cd8837505b14c0feedc9ffe6df4c0f46fd6d1044e59',NOW(),3),
          (203,104,3,'PUBLISHED',CAST({db.sql_literal(canonical_json)} AS JSON),'{canonical_hash}',NOW(),4),
          (204,105,7,'PUBLISHED',JSON_ARRAY(JSON_OBJECT('key','custom')),'{'f' * 64}',NOW(),5);
        """
        self.execute(container, setup)
        self.execute(container, migration)

        summary = self.execute(container, """
          SELECT t.tenant_id,s.version_no,s.status,JSON_LENGTH(s.fields_json),s.schema_hash
          FROM zsjos_material_type t
          LEFT JOIN zsjos_material_schema_version s ON s.id=t.current_schema_version_id
          WHERE t.code='viral_account' ORDER BY t.tenant_id;
        """).strip().splitlines()
        self.assertEqual(5, len(summary))
        for tenant in range(1, 5):
            columns = summary[tenant - 1].split("\t")
            self.assertEqual(str(tenant), columns[0])
            self.assertEqual("PUBLISHED", columns[2])
            self.assertEqual("36", columns[3])
            self.assertEqual(canonical_hash, columns[4])
        self.assertEqual(["5", "7", "PUBLISHED", "1", "f" * 64], summary[4].split("\t"))

        self.assertEqual("ARCHIVED\nARCHIVED", self.execute(container, """
          SELECT status FROM zsjos_material_schema_version WHERE id IN (201,202) ORDER BY id;
        """).strip())
        self.assertEqual("12\t12\t12\t6", self.execute(container, """
          SELECT
            SUM(j.section_name='ACCOUNT_DETAIL'),
            SUM(j.section_name='DIRECTOR_ANALYSIS'),
            SUM(j.section_name='BUILD_SUGGESTION'),
            SUM(j.field_key IN ('s1_stage_plan','s2_stage_plan','s3_stage_plan',
                                's4_stage_plan','s5_stage_plan','s6_stage_plan'))
          FROM zsjos_material_type t
          JOIN zsjos_material_schema_version s ON s.id=t.current_schema_version_id
          JOIN JSON_TABLE(s.fields_json,'$[*]' COLUMNS(
            field_key varchar(64) PATH '$.key', section_name varchar(64) PATH '$.section'
          )) j
          WHERE t.tenant_id=3 AND t.code='viral_account';
        """).strip())
        expected_label_hex = "账号名称".encode().hex().upper()
        self.assertEqual(expected_label_hex, self.execute(container, """
          SELECT HEX(JSON_UNQUOTE(JSON_EXTRACT(s.fields_json,'$[0].label')))
          FROM zsjos_material_type t JOIN zsjos_material_schema_version s ON s.id=t.current_schema_version_id
          WHERE t.tenant_id=3 AND t.code='viral_account';
        """).strip())
        self.assertEqual("1\n1", self.execute(container, """
          SELECT COUNT(*) FROM zsjos_schema_version WHERE version='V208';
          SELECT COUNT(*) FROM zsjos_module_schema_version WHERE module_code='core' AND version='V208';
        """).strip())

        before = self.execute(container, """
          SELECT COUNT(*),GROUP_CONCAT(CONCAT(tenant_id,':',material_type_id,':',version_no) ORDER BY tenant_id)
          FROM zsjos_material_schema_version;
        """)
        self.execute(container, migration)
        after = self.execute(container, """
          SELECT COUNT(*),GROUP_CONCAT(CONCAT(tenant_id,':',material_type_id,':',version_no) ORDER BY tenant_id)
          FROM zsjos_material_schema_version;
        """)
        self.assertEqual(before, after)
        print("PASS: V208 upgrades known templates, preserves custom schemas, verifies UTF-8, and replays without duplicates")


if __name__ == "__main__":
    unittest.main()
