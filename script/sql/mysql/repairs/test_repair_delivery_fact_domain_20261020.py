#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Controlled MySQL fixture for reviewed frozen delivery-domain correction; retains its own schema."""
import importlib.util
import json
from pathlib import Path
import unittest
import uuid

SPEC = importlib.util.spec_from_file_location("delivery_repair", Path(__file__).with_name("repair_delivery_fact_domain_20261020.py"))
repair = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(repair)


class DeliveryDomainRepairTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.database = "delivery_domain_review_" + uuid.uuid4().hex[:10]
        with repair.connect("yudao-mysql", "ruoyi-vue-pro") as connection:
            with connection.cursor() as cursor:
                cursor.execute("CREATE DATABASE `" + cls.database + "` CHARACTER SET utf8mb4")
        cls.connection = repair.connect("yudao-mysql", cls.database)
        with cls.connection.cursor() as cursor:
            cursor.execute("CREATE TABLE zsjos_performance_org(tenant_id BIGINT,deleted BIT,dept_id BIGINT,center_id BIGINT,kind VARCHAR(16),business_domain VARCHAR(16)) ENGINE=InnoDB")
            cursor.execute("CREATE TABLE zsjos_performance_attribution(id BIGINT PRIMARY KEY,tenant_id BIGINT,deleted BIT,dept_id BIGINT,org_source VARCHAR(16),business_domain VARCHAR(16),user_name VARCHAR(64),update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP) ENGINE=InnoDB")
        cls.connection.commit()

    @classmethod
    def tearDownClass(cls):
        cls.connection.close()
        print(json.dumps({"retained_test_schema": cls.database}))

    def setUp(self):
        with self.connection.cursor() as cursor:
            cursor.execute("DROP TRIGGER IF EXISTS fail_second_repair")
            cursor.execute("DELETE FROM zsjos_performance_attribution")
            cursor.execute("DELETE FROM zsjos_performance_org")
            cursor.execute("INSERT INTO zsjos_performance_org VALUES(1,0,1051,1050,'DEPT','delivery')")
            cursor.execute("INSERT INTO zsjos_performance_attribution(id,tenant_id,deleted,dept_id,org_source,business_domain,user_name) VALUES (1,1,0,1051,'frozen',NULL,'交付历史'),(2,1,0,1051,'frozen',NULL,'交付历史'),(3,1,0,1051,'current',NULL,'旧补写'),(4,1,0,1051,NULL,NULL,'未知'),(5,1,0,1051,'frozen','sales','明确销售'),(6,2,0,1051,'frozen',NULL,'其他租户'),(7,1,1,1051,'frozen',NULL,'已删除'),(8,1,0,1011,'frozen',NULL,'其他旧部门')")
        self.connection.commit()
        self.manifest = repair.prepare(self.connection, 1051, self.database)

    def test_initial_replay_rollback_and_utf8(self):
        self.assertEqual([1, 2], self.manifest["ids"])
        self.assertEqual(2, repair.apply(self.connection, self.manifest, self.database))
        self.assertEqual(0, repair.apply(self.connection, self.manifest, self.database))
        with self.connection.cursor() as cursor:
            cursor.execute("SELECT HEX(user_name) value FROM zsjos_performance_attribution WHERE id=1")
            self.assertEqual("交付历史".encode("utf-8").hex().upper(), cursor.fetchone()["value"])
        self.assertEqual(2, repair.apply(self.connection, self.manifest, self.database, rollback=True))
        self.assertEqual(0, repair.apply(self.connection, self.manifest, self.database, rollback=True))
        self.assertEqual(self.manifest["preimage_digest"], repair.prepare(self.connection, 1051, self.database)["preimage_digest"])

    def test_changed_preimage_is_rejected(self):
        with self.connection.cursor() as cursor:
            cursor.execute("UPDATE zsjos_performance_attribution SET dept_id=1011 WHERE id=2")
        self.connection.commit()
        with self.assertRaises(ValueError):
            repair.apply(self.connection, self.manifest, self.database)

    def test_new_candidates_and_mapping_changes_are_rejected(self):
        with self.connection.cursor() as cursor:
            cursor.execute("INSERT INTO zsjos_performance_attribution(id,tenant_id,deleted,dept_id,org_source) VALUES(9,1,0,1051,'frozen')")
        self.connection.commit()
        with self.assertRaises(ValueError):
            repair.apply(self.connection, self.manifest, self.database)
        with self.connection.cursor() as cursor:
            cursor.execute("UPDATE zsjos_performance_org SET business_domain='sales'")
        self.connection.commit()
        with self.assertRaises(ValueError):
            repair.apply(self.connection, self.manifest, self.database)

    def test_conflict_and_post_repair_changes_prevent_overwrite_or_rollback(self):
        with self.connection.cursor() as cursor:
            cursor.execute("UPDATE zsjos_performance_attribution SET business_domain='sales' WHERE id=2")
        self.connection.commit()
        with self.assertRaises(ValueError):
            repair.apply(self.connection, self.manifest, self.database)
        with self.connection.cursor() as cursor:
            cursor.execute("UPDATE zsjos_performance_attribution SET business_domain=NULL WHERE id=2")
        self.connection.commit()
        self.manifest = repair.prepare(self.connection, 1051, self.database)
        repair.apply(self.connection, self.manifest, self.database)
        with self.connection.cursor() as cursor:
            cursor.execute("UPDATE zsjos_performance_attribution SET user_name='后续修改' WHERE id=1")
        self.connection.commit()
        with self.assertRaises(ValueError):
            repair.apply(self.connection, self.manifest, self.database, rollback=True)

    def test_second_row_failure_rolls_back_first_row(self):
        with self.connection.cursor() as cursor:
            cursor.execute("CREATE TRIGGER fail_second_repair BEFORE UPDATE ON zsjos_performance_attribution FOR EACH ROW BEGIN IF NEW.id=2 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='fixture failure'; END IF; END")
        with self.assertRaises(Exception):
            repair.apply(self.connection, self.manifest, self.database)
        with self.connection.cursor() as cursor:
            cursor.execute("SELECT COUNT(*) value FROM zsjos_performance_attribution WHERE id IN (1,2) AND business_domain IS NULL")
            self.assertEqual(2, cursor.fetchone()["value"])
            cursor.execute("DROP TRIGGER fail_second_repair")
        self.assertEqual(2, repair.apply(self.connection, self.manifest, self.database))


if __name__ == "__main__":
    unittest.main()
