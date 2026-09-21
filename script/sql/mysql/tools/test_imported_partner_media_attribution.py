#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""UTF-8: isolated replay for V266 imported Partner media-screen attribution."""
import subprocess
import unittest

import zsjos_db as db


class ImportedPartnerMediaAttributionTest(unittest.TestCase):
    def sql(self, container, statement):
        result = subprocess.run(
            ["docker", "exec", "-i", "-w", "/workspace", container, "mysql",
             "--default-character-set=utf8mb4", "-uroot", "-N", "--batch", "--raw", "zsjos_test"],
            input=statement.encode("utf-8"), capture_output=True,
        )
        self.assertEqual(0, result.returncode, result.stderr.decode("utf-8", errors="replace"))
        return result.stdout.decode("utf-8").strip()

    def test_replay_preserves_business_fields_and_scopes_attribution(self):
        db.with_test_mysql(self.exercise)

    def exercise(self, container):
        self.sql(container, """
            CREATE TABLE zsjos_lead (
              id bigint PRIMARY KEY, tenant_id bigint NOT NULL, deleted bit NOT NULL DEFAULT b'0',
              provider_owner_type varchar(32), provider_owner_id bigint, submission_idempotency_key varchar(128),
              counted_at datetime NULL, status varchar(32), owner_user_id bigint, updater varchar(64), update_time datetime,
              contribution_user_id_snapshot bigint NULL, contribution_user_name_snapshot varchar(100) NULL,
              contribution_dept_id_snapshot bigint NULL, contribution_dept_name_snapshot varchar(100) NULL,
              contribution_supervisor_user_id_snapshot bigint NULL, contribution_supervisor_name_snapshot varchar(100) NULL
            ) CHARACTER SET utf8mb4;
            CREATE TABLE zsjos_partner_ownership (
              partner_id bigint, employee_user_id bigint, employee_name_snapshot varchar(100),
              tenant_id bigint, deleted bit NOT NULL DEFAULT b'0'
            ) CHARACTER SET utf8mb4;
            CREATE TABLE system_users (
              id bigint, tenant_id bigint, nickname varchar(100), dept_id bigint, status tinyint,
              deleted bit NOT NULL DEFAULT b'0', PRIMARY KEY(id, tenant_id)
            ) CHARACTER SET utf8mb4;
            CREATE TABLE system_dept (
              id bigint, tenant_id bigint, name varchar(100), leader_user_id bigint,
              deleted bit NOT NULL DEFAULT b'0', PRIMARY KEY(id, tenant_id)
            ) CHARACTER SET utf8mb4;
            CREATE TABLE zsjos_schema_version (
              version varchar(32) PRIMARY KEY, description varchar(255), checksum varchar(128), installed_at datetime
            ) CHARACTER SET utf8mb4;
            CREATE TABLE zsjos_module_schema_version (
              module_code varchar(32), version varchar(32), description varchar(255), checksum varchar(128),
              release_version varchar(128), installed_at datetime, PRIMARY KEY(module_code, version)
            ) CHARACTER SET utf8mb4;
            INSERT INTO system_users VALUES
              (101,1,'运营现名',11,0,b'0'), (102,1,'主管甲',99,0,b'0'),
              (201,2,'租户二运营',21,0,b'0'), (301,1,'停用运营',31,1,b'0');
            INSERT INTO system_dept VALUES (11,1,'新媒体一部',102,b'0'),(21,2,'租户二新媒体',NULL,b'0'),(31,1,'停用部门',NULL,b'0');
            INSERT INTO zsjos_partner_ownership VALUES
              (10,101,'运营归属快照',1,b'0'), (20,201,'租户二归属',2,b'0'), (30,301,'停用归属',1,b'0');
            INSERT INTO zsjos_lead(id,tenant_id,provider_owner_type,provider_owner_id,submission_idempotency_key,counted_at,status,owner_user_id,contribution_user_id_snapshot,contribution_dept_id_snapshot)
            VALUES
              (1,1,'partner',10,'legacy-parttimecrm-1','2026-09-01 08:00:00','valid',700,NULL,NULL),
              (2,1,'partner',10,'normal-2','2026-09-01 08:00:00','valid',701,NULL,NULL),
              (3,1,'partner',99,'legacy-parttimecrm-3','2026-09-01 08:00:00','valid',702,NULL,NULL),
              (4,2,'partner',20,'legacy-parttimecrm-4','2026-09-01 08:00:00','won',703,NULL,NULL),
              (5,1,'partner',30,'legacy-parttimecrm-5','2026-09-01 08:00:00','valid',704,NULL,NULL),
              (6,1,'partner',10,'legacy-parttimecrm-6','2026-09-01 08:00:00','valid',705,888,11);
        """)
        migration = "SOURCE script/sql/mysql/migrations/V266__backfill_imported_partner_media_attribution.sql;"
        self.sql(container, migration)
        self.assertEqual(
            "101\t运营归属快照\t11\t新媒体一部\t102\t主管甲\tvalid\t700",
            self.sql(container, "SELECT contribution_user_id_snapshot,contribution_user_name_snapshot,contribution_dept_id_snapshot,contribution_dept_name_snapshot,contribution_supervisor_user_id_snapshot,contribution_supervisor_name_snapshot,status,owner_user_id FROM zsjos_lead WHERE id=1"),
        )
        self.assertEqual("201\t租户二归属\t21\t租户二新媒体\twon\t703", self.sql(container,
            "SELECT contribution_user_id_snapshot,contribution_user_name_snapshot,contribution_dept_id_snapshot,contribution_dept_name_snapshot,status,owner_user_id FROM zsjos_lead WHERE id=4"))
        self.assertEqual("3", self.sql(container, "SELECT COUNT(*) FROM zsjos_lead WHERE contribution_user_id_snapshot IS NULL"))
        self.assertEqual("888\t11", self.sql(container, "SELECT contribution_user_id_snapshot,contribution_dept_id_snapshot FROM zsjos_lead WHERE id=6"))
        self.sql(container, migration)
        self.assertEqual("1", self.sql(container, "SELECT COUNT(*) FROM zsjos_schema_version WHERE version='V266'"))
        self.assertEqual("1", self.sql(container, "SELECT COUNT(*) FROM zsjos_module_schema_version WHERE module_code='core' AND version='V266'"))
        self.assertEqual('回填导入兼职客资大屏归属快照'.encode().hex().upper(), self.sql(container,
            "SELECT HEX(description) FROM zsjos_schema_version WHERE version='V266'"))


if __name__ == "__main__":
    unittest.main()
