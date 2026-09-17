#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Regression checks for SQL authorization ownership, without a database dependency."""
import unittest
from pathlib import Path
from role_menu_policy import check, mutations


class RoleMenuPolicyTest(unittest.TestCase):
    def test_deployment_sql(self):
        self.assertEqual([], check(Path(__file__).resolve().parents[2]))
        self.assertEqual([], check(Path(__file__).resolve().parents[4] / 'sql'))

    def test_mutations_inside_procedures_and_quoted_semicolons(self):
        sql = "BEGIN INSERT IGNORE INTO `system_role_menu` VALUES ('a;b'); UPDATE system_role_menu SET deleted=0; END"
        parts = [sql[a:b] for a, b in mutations(sql)]
        self.assertEqual(2, len(parts))
        self.assertTrue(parts[0].endswith("('a;b');"))

    def test_revoke_restore_and_replace_are_mutations(self):
        for sql in ['DELETE rm FROM system_role_menu rm;',
                    'DELETE FROM system_role_menu;',
                    'REPLACE INTO system_role_menu VALUES (1);',
                    'UPDATE system_role_menu SET deleted=0;']:
            with self.subTest(sql=sql):
                self.assertEqual(1, len(list(mutations(sql))))

    def test_read_only_and_comments_are_not_mutations(self):
        self.assertEqual([], list(mutations("-- INSERT INTO system_role_menu;\n"
                         "SELECT 'UPDATE system_role_menu;' FROM system_role_menu;")))


if __name__ == '__main__':
    unittest.main()
