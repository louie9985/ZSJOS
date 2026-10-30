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

    def verify(self, migration, recorded, release='release'):
        client = Mock(spec=db.MysqlClient)
        installed = {(migration.module, migration.version): (recorded, release)}
        with patch.object(db, 'installed_versions', return_value=installed), \
                patch.object(db, 'migrations_for', return_value=[migration]), patch.object(db, 'info'):
            result = db.verify_applied_migration_integrity(client, self.manifests)
        self.assertEqual([unittest.mock.call.table_exists('zsjos_module_schema_version')], client.mock_calls)
        return result

    def reviewed(self, version):
        artifact = db.REVIEWED_SEED_ARTIFACTS[('core', version)]
        source = db.ROOT / 'script/sql/mysql/migrations' / artifact.filename
        path = self.root / 'migrations' / artifact.filename
        path.write_bytes(source.read_bytes())
        return db.Migration('core', version, int(version[1:]), 'reviewed fixture', path, db.sha256(path))

    def test_read_only_integrity_accepts_exact_or_strict_eol_for_every_release(self):
        for release in ('test-fresh', 'release', 'baseline', 'legacy'):
            migration = self.migration(self.lf.replace(b'\n', b'\r\n'))
            for recorded in (migration.checksum, hashlib.sha256(self.lf).hexdigest()):
                with self.subTest(release=release, recorded=recorded):
                    self.assertEqual([], self.verify(migration, recorded, release))

    def test_read_only_integrity_rejects_content_edits_and_bypass_flag(self):
        expected = hashlib.sha256(self.lf).hexdigest()
        for content in (self.lf.replace(b'SELECT 1', b'SELECT 2'),
                        self.lf.replace(b'SELECT 1', b'SELECT  1'), b'\xef\xbb\xbf' + self.lf):
            migration = self.migration(content)
            with self.subTest(content=content), patch.dict(os.environ, {'ZSJOS_DB_SKIP_CHECKSUM': 'true'}):
                with self.assertRaisesRegex(db.CommandError, 'checksum changed'):
                    self.verify(migration, expected)

    def test_all_seven_reviewed_seed_lists_require_current_pinned_artifact(self):
        for key, artifact in db.REVIEWED_SEED_ARTIFACTS.items():
            migration = self.reviewed(key[1])
            for release in ('legacy', 'baseline'):
                for recorded in artifact.prior_checksums:
                    with self.subTest(version=key[1], release=release, recorded=recorded):
                        self.assertEqual([], self.verify(migration, recorded, release))

    def test_reviewed_seed_bad_hash_and_body_whitespace_bom_or_edits_fail(self):
        for version in ('V114', 'V116', 'V136', 'V192', 'V193', 'V196', 'V208'):
            migration = self.reviewed(version)
            content = migration.path.read_bytes()
            artifact = db.REVIEWED_SEED_ARTIFACTS[('core', version)]
            with self.subTest(version=version, kind='unknown hash'):
                with self.assertRaisesRegex(db.CommandError, 'checksum changed'):
                    self.verify(migration, 'a' * 64, 'baseline')
            for changed in (content + b' ', b'\xef\xbb\xbf' + content, content + b'\nSELECT 987;\n'):
                migration.path.write_bytes(changed)
                # Deliberately retain the old Migration object to check that verify re-reads bytes.
                with self.subTest(version=version, kind='tampered artifact'):
                    with self.assertRaisesRegex(db.CommandError, 'checksum changed'):
                        self.verify(migration, artifact.prior_checksums[-1], 'legacy')

    def test_historical_seed_compatibility_never_weakens_runner_records(self):
        migration = self.reviewed('V114')
        artifact = db.REVIEWED_SEED_ARTIFACTS[('core', 'V114')]
        for recorded in artifact.prior_checksums:
            if recorded not in (migration.checksum, artifact.lf_checksum):
                with self.assertRaisesRegex(db.CommandError, 'checksum changed'):
                    self.verify(migration, recorded, 'test-fresh')

    def test_missing_reviewed_ledger_fails_and_other_seed_scope_is_explicit(self):
        migration = self.reviewed('V114')
        with patch.object(db, 'installed_versions', return_value={}), \
                patch.object(db, 'migrations_for', return_value=[migration]):
            with self.assertRaisesRegex(db.CommandError, 'Missing migration integrity ledger'):
                db.verify_applied_migration_integrity(Mock(spec=db.MysqlClient), self.manifests)
        unrelated = self.migration(self.lf)
        self.assertEqual(['core/V001'], self.verify(unrelated, 'historical-tag', 'baseline'))

    def test_verify_database_wires_integrity_before_sql_without_ledger_writes(self):
        migration = self.migration(self.lf)
        client = Mock(spec=db.MysqlClient)
        client.table_exists.return_value = True
        client.query.return_value = f'core\tV001\t{migration.checksum}\trelease\n'
        client.execute_file.return_value = 'fixture\tPASS\n'
        manifest = {'core': {**self.manifests['core'], 'verify': 'verify.sql'}}
        with patch.object(db, 'enabled_manifests', return_value=manifest), \
                patch.object(db, 'schema_drift', return_value=[]), patch.object(db, 'info'):
            db.verify_database('local', client)
            client.execute_file.assert_called_once()
            client.execute_file.reset_mock()
            client.query.return_value = 'core\tV001\t' + '0' * 64 + '\trelease\n'
            with self.assertRaisesRegex(db.CommandError, 'checksum changed'):
                db.verify_database('local', client)
            client.execute_file.assert_not_called()
        self.assertTrue(all(call.args[0].startswith('SELECT ') for call in client.query.call_args_list))
        client.backup.assert_not_called()

    def test_complete_verification_rejects_missing_non_target_and_v358(self):
        for number in (2, 114, 358):
            migration = self.migration(self.lf, number)
            client = Mock(spec=db.MysqlClient)
            client.table_exists.return_value = True
            with self.subTest(version=migration.version), \
                    patch.object(db, 'installed_versions', return_value={}), \
                    patch.object(db, 'migrations_for', return_value=[migration]):
                with self.assertRaisesRegex(db.CommandError, f'Missing migration integrity ledger: core/{migration.version}'):
                    db.verify_applied_migration_integrity(client, self.manifests)
                self.assertEqual([], db.verify_applied_migration_integrity(client, self.manifests, require_complete=False))
                self.assertEqual([migration], db.pending_migrations(self.manifests, {}))
            client.query.assert_not_called()
            client.execute_file.assert_not_called()

    def test_complete_verification_requires_module_ledger_not_legacy_fallback(self):
        client = Mock(spec=db.MysqlClient)
        client.table_exists.return_value = False
        with self.assertRaisesRegex(db.CommandError, 'Missing module migration integrity ledger table'):
            db.verify_applied_migration_integrity(client, self.manifests)
        client.query.assert_not_called()

    def test_reviewed_seed_tamper_blocks_preflight_before_any_database_write(self):
        migration = self.reviewed('V114')
        artifact = db.REVIEWED_SEED_ARTIFACTS[('core', 'V114')]
        installed = {('core', 'V114'): (artifact.prior_checksums[-1], 'legacy')}
        client = Mock(spec=db.MysqlClient)
        with patch.object(db, 'installed_versions', return_value=installed), \
                patch.object(db, 'migrations_for', return_value=[migration]):
            self.assertEqual(([], []), db.migration_preflight(client, self.manifests))
            migration.path.write_bytes(migration.path.read_bytes() + b' ')
            with patch.dict(os.environ, {'ZSJOS_DB_SKIP_CHECKSUM': 'true'}):
                pending, blockers = db.migration_preflight(client, self.manifests)
            self.assertEqual([], pending)
            self.assertIn('reviewed seed artifact mismatch', blockers[0])
            self.assertEqual([], client.mock_calls)
            with patch.object(db, 'static_check'), \
                    patch.object(db, 'enabled_manifests', return_value=self.manifests), \
                    patch.object(db, 'db_config'), patch.object(db, 'MysqlClient', return_value=client), \
                    patch.object(db, 'schema_drift', return_value=[]), patch.object(db, 'info'), \
                    patch.object(db, 'MigrationLock') as lock:
                with self.assertRaisesRegex(db.CommandError, 'blocked'):
                    db.migrate_database('local')
                lock.assert_not_called()
            client.backup.assert_not_called()
            client.execute_file.assert_not_called()
            client.query.assert_not_called()


if __name__ == '__main__':
    unittest.main()
