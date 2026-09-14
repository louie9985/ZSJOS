#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Replay the development baseline and verify questionnaire retirement without data loss."""
import subprocess
import unittest

import zsjos_db as db


MIGRATION = db.SQL_ROOT / 'migrations/V203__positioning_interview_template.sql'
PERMISSIONS = "'zsjos:director-interview-template:query','zsjos:director-interview-template:update','zsjos:director-interview-template:publish','zsjos:student:director-interview'"


def retirement_sql():
    source = MIGRATION.read_text(encoding='utf-8')
    return 'SET NAMES utf8mb4;\n' + source.split('-- BEGIN POSITIONING INTERVIEW MENU RETIREMENT\n')[1].split('-- END POSITIONING INTERVIEW MENU RETIREMENT')[0]


class RetirementTest(unittest.TestCase):
    def sql(self, container, query):
        result = subprocess.run(['docker', 'exec', '-i', '-w', '/workspace', container,
                                 'mysql', '--default-character-set=utf8mb4', '-uroot', '-N', '--batch', '--raw', 'zsjos_test'],
                                input=query.encode('utf-8'), capture_output=True)
        self.assertEqual(0, result.returncode, result.stderr.decode('utf-8', errors='replace'))
        return result.stdout.decode('utf-8').strip()

    def test_fresh_and_repeatable_retirement(self):
        db.with_test_mysql(self.exercise)

    def exercise(self, container):
        self.sql(container, 'SOURCE script/sql/mysql/bootstrap.sql;')
        installed = set(self.sql(container, "SELECT version FROM zsjos_module_schema_version WHERE module_code='core'").splitlines())
        for migration in db.migrations_for('core', db.load_manifests()['core']):
            if migration.version not in installed:
                self.sql(container, f'SOURCE {migration.path.relative_to(db.ROOT).as_posix()};')
        menus = self.sql(container, f'SELECT permission,HEX(name),status,visible+0 FROM system_menu WHERE deleted=0 AND permission IN ({PERMISSIONS}) ORDER BY permission')
        self.assertIn('定位访谈大纲配置'.encode().hex().upper(), menus)
        self.assertIn('保存定位访谈大纲'.encode().hex().upper(), menus)
        self.assertIn('发布定位访谈大纲'.encode().hex().upper(), menus)
        self.assertIn('zsjos:student:director-interview\t', menus)
        self.assertEqual('1\t0', self.sql(container, "SELECT status,visible+0 FROM system_menu WHERE deleted=0 AND permission='zsjos:student:director-interview'"))
        # Existing version JSON is an immutable historical source, including retired templates.
        history_query = "SELECT id,MD5(fields_json) FROM zsjos_director_form_template_version ORDER BY id"
        grants_query = 'SELECT id,role_id,menu_id,tenant_id,deleted+0 FROM system_role_menu ORDER BY id'
        history = self.sql(container, history_query)
        grants = self.sql(container, grants_query)
        self.sql(container, retirement_sql())
        self.assertEqual(history, self.sql(container, history_query))
        self.assertEqual(grants, self.sql(container, grants_query))
        self.assertEqual(menus, self.sql(container, f'SELECT permission,HEX(name),status,visible+0 FROM system_menu WHERE deleted=0 AND permission IN ({PERMISSIONS}) ORDER BY permission'))
        output = db.ROOT / 'output/positioning-interview-retirement'
        output.mkdir(parents=True, exist_ok=True)
        (output / 'expected-menus.tsv').write_text(menus + '\n', encoding='utf-8')
        schema = self.sql(container, "SELECT table_name,column_name,column_type,is_nullable,COALESCE(column_default,'NULL'),extra,COALESCE(collation_name,'NULL') FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name IN ('zsjos_director_form_template','zsjos_director_form_template_version','zsjos_student_positioning_interview','zsjos_student_positioning_interview_item','zsjos_student_positioning_interview_attachment') ORDER BY table_name,ordinal_position")
        (output / 'expected-schema.tsv').write_text(schema + '\n', encoding='utf-8')
        print('PASS: fresh baseline and all pending migrations, menu labels/UTF-8 HEX, disabled legacy action, repeatability, historical versions and grants preserved')


if __name__ == '__main__':
    unittest.main()
