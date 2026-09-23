#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Exercise migration numbering and generation without Docker or Atlas."""
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import zsjos_db as db


class MigrationNumberingTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.sql = self.root / 'sql'
        self.migrations = self.sql / 'migrations'
        self.migrations.mkdir(parents=True)
        self.manifest = {
            'schema': 'desired.sql', 'baseline': 'baseline.sql',
            'migrations': 'migrations',
        }
        for name, value in [('ROOT', self.root), ('SQL_ROOT', self.sql)]:
            mocked = patch.object(db, name, value)
            mocked.start()
            self.addCleanup(mocked.stop)

    def seed(self, numbers):
        for number in numbers:
            (self.migrations / f'V{number:03d}__existing.sql').write_text(
                'SELECT 1;\n', encoding='utf-8')

    def test_generator_accepts_both_next_version_parities(self):
        ddl = 'ALTER TABLE example ADD COLUMN value int;'
        for current in (1, 2):
            with self.subTest(current=current):
                self.seed(range(1, current + 1))
                baseline = self.sql / 'baseline.sql'
                desired = self.sql / 'desired.sql'
                baseline.write_text('old schema\n', encoding='utf-8')
                desired.write_text('new schema\n', encoding='utf-8')
                with patch.object(db, 'load_manifests', return_value={'core': self.manifest}), \
                        patch.object(db, 'atlas_diff', return_value=ddl) as atlas, \
                        patch.object(db, 'info'):
                    db.make_migration('core', 'add_value')
                generated = self.migrations / f'V{current + 1:03d}__add_value.sql'
                self.assertIn(ddl, generated.read_text(encoding='utf-8'))
                self.assertEqual(desired.read_bytes(), baseline.read_bytes())
                self.assertEqual(list(range(1, current + 2)), [
                    migration.number for migration in db.migrations_for('core', self.manifest)
                ])
                atlas.assert_called_once_with(baseline, desired)
                generated.unlink()

    def test_duplicate_version_is_rejected(self):
        self.seed([1])
        (self.migrations / 'V001__duplicate.sql').write_text('SELECT 2;\n', encoding='utf-8')
        with self.assertRaisesRegex(db.CommandError, 'not continuous'):
            db.migrations_for('core', self.manifest)

    def test_gap_is_rejected(self):
        self.seed([1, 3])
        with self.assertRaisesRegex(db.CommandError, 'not continuous'):
            db.migrations_for('core', self.manifest)

    def test_sequence_must_start_at_one(self):
        self.seed([2])
        with self.assertRaisesRegex(db.CommandError, 'start at V001'):
            db.migrations_for('core', self.manifest)


if __name__ == '__main__':
    unittest.main()
