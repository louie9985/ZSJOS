#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""MySQL integration regression for V194 replay; uses a disposable container only.

Run: python script/sql/mysql/tools/test_v194_replay.py
"""

import re
import subprocess
import unittest

import zsjos_db as db


class V194ReplayTest(unittest.TestCase):
    def execute(self, container, sql, expected_error=None):
        # Inline SOURCE files so the client aborts on the first SQL error in batch mode.
        def expand(text):
            return re.sub(r"(?m)^SOURCE ([^;]+);[ \t]*$",
                          lambda match: expand((db.ROOT / match[1]).read_text(encoding="utf-8-sig")), text)

        result = subprocess.run(
            ["docker", "exec", "-i", "-w", "/workspace", container,
             "mysql", "--default-character-set=utf8mb4",
             "-uroot", "--batch", "--raw", "--skip-column-names", "zsjos_test"],
            input=expand(sql).encode("utf-8"), capture_output=True, check=False,
        )
        error = result.stderr.decode("utf-8", errors="replace")
        if expected_error:
            self.assertNotEqual(result.returncode, 0, error)
            self.assertIn(expected_error, error)
        else:
            self.assertEqual(result.returncode, 0, error)
            self.assertNotRegex(error, r"(?m)^ERROR", error)
        return result.stdout.decode("utf-8")

    def test_upgrade_replay_and_conflict_protection(self):
        db.with_test_mysql(self.exercise)

    def exercise(self, container):
        v194 = (db.SQL_ROOT / "migrations/V194__material_library_content_review.sql").read_text(encoding="utf-8")
        bootstrap = (db.SQL_ROOT / "bootstrap.sql").read_text(encoding="utf-8")
        before, after = bootstrap.split(
            "SOURCE script/sql/mysql/migrations/V194__material_library_content_review.sql;", 1)
        self.execute(container, before)
        self.execute(container, v194)
        self.execute(container, v194)
        print("PASS: V194 applies and replays before V198", flush=True)
        self.execute(container, after)
        print("PASS: fresh bootstrap through latest migration", flush=True)

        snapshot = """SELECT id,parent_id,type,permission,path,component,component_name,
            workbench_render_mode,HEX(name),status,deleted+0 FROM system_menu
            WHERE id IN (80041,80042) ORDER BY id;
            SELECT COUNT(*) FROM system_role_menu;
            SELECT COUNT(*) FROM system_dict_data;
            SELECT COUNT(*) FROM zsjos_material_type;"""
        expected = self.execute(container, snapshot)
        for _ in range(2):
            self.execute(container, v194)
            self.assertEqual(self.execute(container, snapshot), expected)
        self.assertIn("爆款账号拆解".encode().hex().upper(), expected)
        self.assertIn("爆款内容拆解".encode().hex().upper(), expected)
        self.assertEqual(self.execute(container, """SELECT COUNT(*) FROM zsjos_schema_version
            WHERE version='V194'; SELECT COUNT(*) FROM zsjos_module_schema_version
            WHERE module_code='core' AND version='V194';""").strip(), "1\n1")
        print("PASS: V194 replays after V198 without changing successor menus or duplicating metadata; UTF-8 HEX verified", flush=True)

        # Real collisions must still fail before any migration data updates.
        self.execute(container, """INSERT INTO system_menu
            (id,name,permission,type,sort,parent_id,path,component,workbench_render_mode)
            VALUES (990001,'V194 regression fixture','zsjos:material:create',2,1,6735,
                    'unexpected-material-page','zsjos-workbench','native');""")
        self.execute(container, v194, "V194 permission is already owned by another menu")
        self.execute(container, "DELETE FROM system_menu WHERE id=990001;")

        for assignment, restore in [
            ("parent_id=80010", "parent_id=6735"),
            ("type=3", "type=2"),
            ("path='wrong-path'", "path='viral-account-decompose'"),
            ("permission='zsjos:material:query'", "permission='zsjos:material:create'"),
            ("component='wrong-component'", "component='zsjos-workbench'"),
            ("component_name=NULL", "component_name='ViralAccountDecomposePage'"),
            ("workbench_render_mode='admin_only'", "workbench_render_mode='native'"),
        ]:
            with self.subTest(assignment=assignment):
                self.execute(container, f"UPDATE system_menu SET {assignment} WHERE id=80041;")
                try:
                    self.execute(container, v194, "V194 permission is already owned by another menu")
                finally:
                    self.execute(container, f"UPDATE system_menu SET {restore} WHERE id=80041;")
        self.execute(container, "UPDATE system_menu SET path='occupied' WHERE id=80011;")
        self.execute(container, v194, "V194 menu ID range is occupied")
        self.execute(container, "UPDATE system_menu SET path='browse' WHERE id=80011;")
        self.execute(container, v194)
        self.assertEqual(self.execute(container, snapshot), expected)
        print("PASS: unknown duplicate permission, malformed successor menus, and occupied V194 IDs rejected", flush=True)

        # Report baseline checks separately so unrelated existing failures remain visible.
        verification = self.execute(container, "SOURCE script/sql/mysql/verify-bootstrap.sql;")
        failures = [line for line in verification.splitlines() if re.search(r"\t(?:FAIL|MISSING)(?:\t|$)", line)]
        for line in failures:
            print("BASELINE CHECK: " + line, flush=True)
        self.assertFalse([line for line in failures if line.startswith("V194 ")], failures)
        print(f"Baseline verification: {len(failures)} failing checks outside this regression", flush=True)


if __name__ == "__main__":
    unittest.main()
