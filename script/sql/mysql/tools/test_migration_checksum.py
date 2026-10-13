#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Applied-migration checksum compatibility without ledger or SQL rewrites."""
import hashlib
import os
import tempfile
import unittest
from pathlib import Path
from unittest.mock import Mock, patch

import zsjos_db as db


class MigrationChecksumTest(unittest.TestCase):
    def setUp(self):
        temp = tempfile.TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        self.root = Path(temp.name)
        (self.root / 'migrations').mkdir()
        self.manifests = {'core': {'dependsOn': [], 'migrations': 'migrations'}}
        for p in (patch.object(db, 'SQL_ROOT', self.root),
                  patch.dict(os.environ, {'ZSJOS_DB_SKIP_CHECKSUM': 'false'})):
            p.start()
            self.addCleanup(p.stop)
        self.lf = "-- UTF-8 中世健\nSELECT 1;\n".encode('utf-8')

    def migration(self, content, number=1):
        path = self.root / 'migrations' / f'V{number:03d}__fixture.sql'
        path.write_bytes(content)
        return db.Migration('core', f'V{number:03d}', number, 'fixture', path, db.sha256(path))

    def installed(self, content, release='release'):
        return {('core', 'V001'): (hashlib.sha256(content).hexdigest(), release)}

    def test_exact_lf_and_crlf_hashes_remain_accepted(self):
        for content in (self.lf, self.lf.replace(b'\n', b'\r\n')):
            with self.subTest(content=content):
                self.migration(content)
                self.assertEqual([], db.pending_migrations(self.manifests, self.installed(content)))

    def test_lf_ledger_accepts_crlf_checkout_without_replay_or_writes(self):
        content = self.lf.replace(b'\n', b'\r\n')
        migration = self.migration(content)
        self.migration(b'SELECT 2;\r\n', 2)
        installed = self.installed(self.lf)
        original = installed.copy()
        client = Mock(spec=db.MysqlClient)
        with patch.object(db, 'installed_versions', return_value=installed):
            pending, blockers = db.migration_preflight(client, self.manifests)
        self.assertEqual(['V002'], [m.version for m in pending])
        self.assertEqual([], blockers)
        self.assertEqual(original, installed)
        self.assertEqual(content, migration.path.read_bytes())
        self.assertEqual([], client.mock_calls)

    def test_content_whitespace_bom_and_bare_cr_still_block(self):
        variants = [self.lf.replace(b'SELECT 1', b'SELECT 2'),
                    self.lf.replace(b'SELECT 1', b'SELECT  1'),
                    b'\xef\xbb\xbf' + self.lf,
                    self.lf.rstrip(b'\n'),
                    self.lf.replace(b'\n', b'\r'),
                    self.lf.replace('中世健'.encode(), '其他'.encode())]
        for content in variants:
            with self.subTest(content=content):
                self.migration(content.replace(b'\n', b'\r\n'))
                with self.assertRaisesRegex(db.CommandError, 'checksum changed'):
                    db.pending_migrations(self.manifests, self.installed(self.lf))

    def test_fallback_does_not_invent_a_recorded_crlf_artifact(self):
        self.migration(self.lf)
        with self.assertRaisesRegex(db.CommandError, 'checksum changed'):
            db.pending_migrations(self.manifests, self.installed(self.lf.replace(b'\n', b'\r\n')))

    def test_baseline_and_legacy_exemptions_are_preserved(self):
        self.migration(self.lf)
        for release in ('baseline', 'legacy'):
            self.assertEqual([], db.pending_migrations(self.manifests, self.installed(b'tag', release)))

    def test_reconcile_remains_a_read_only_raw_byte_audit_by_default(self):
        migration = self.migration(self.lf.replace(b'\n', b'\r\n'))
        installed = self.installed(self.lf)
        client = Mock(spec=db.MysqlClient)
        with patch.object(db, 'installed_versions', return_value=installed):
            differences = db.reconcile_checksums(client, self.manifests, apply=False)
        self.assertEqual([('core', 'V001', installed[('core', 'V001')][0], migration.checksum)], differences)
        self.assertEqual([], client.mock_calls)


if __name__ == '__main__':
    unittest.main()
