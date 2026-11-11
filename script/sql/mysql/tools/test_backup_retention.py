import io
import gzip
import sys
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch, MagicMock

sys.path.insert(0, str(Path(__file__).resolve().parent))
import zsjos_db as db


class BackupRetentionTest(unittest.TestCase):
    def backup(self, directory, payload=b'-- SQL\n-- Dump completed on 2026-11-09\n', code=0):
        client = object.__new__(db.MysqlClient)
        client.mysqldump = 'mysqldump'
        client.config = SimpleNamespace(backup_dir=Path(directory), database='zsjos',
                                      host='localhost', port=3306, user='test', password='')
        process = MagicMock()
        process.__enter__.return_value = process
        process.stdout = io.BytesIO(payload)
        process.wait.return_value = code
        with patch.object(db.subprocess, 'Popen', return_value=process):
            return client.backup()

    def test_verified_backup_replaces_only_managed_dumps(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            old = root / 'zsjos-20261108-120000.sql'
            old.write_bytes(b'old')
            compressed = root / 'zsjos-20261108-130000.sql.gz'
            compressed.write_bytes(b'old')
            other = root / 'other-20261108-120000.sql'
            other.write_bytes(b'other')
            manual = root / 'manual.sql'
            manual.write_bytes(b'manual')
            target = self.backup(directory)
            self.assertIn(b'Dump completed', gzip.decompress(target.read_bytes()))
            self.assertFalse(old.exists())
            self.assertFalse(compressed.exists())
            self.assertTrue(other.exists())
            self.assertTrue(manual.exists())
            newer = self.backup(directory)
            self.assertFalse(target.exists())
            self.assertTrue(newer.exists())

    def test_dump_failure_or_missing_marker_preserves_old_backup(self):
        for payload, code in [(b'partial', 1), (b'incomplete', 0)]:
            with self.subTest(code=code), tempfile.TemporaryDirectory() as directory:
                old = Path(directory) / 'zsjos-20261108-120000.sql'
                old.write_bytes(b'old')
                with self.assertRaises(db.CommandError):
                    self.backup(directory, payload, code)
                self.assertEqual([old], list(Path(directory).iterdir()))

    def test_compression_failure_preserves_old_backup(self):
        with tempfile.TemporaryDirectory() as directory:
            old = Path(directory) / 'zsjos-20261108-120000.sql'
            old.write_bytes(b'old')
            with patch.object(db.shutil, 'copyfileobj', side_effect=OSError('disk full')):
                with self.assertRaises(OSError):
                    self.backup(directory)
            self.assertEqual([old], list(Path(directory).iterdir()))

    def test_crc_failure_preserves_old_backup(self):
        with tempfile.TemporaryDirectory() as directory:
            old = Path(directory) / 'zsjos-20261108-120000.sql'
            old.write_bytes(b'old')
            original = db.gzip.open
            def corrupt_read(path, mode, **kwargs):
                if mode == 'rb':
                    raise gzip.BadGzipFile('invalid CRC')
                return original(path, mode, **kwargs)
            with patch.object(db.gzip, 'open', side_effect=corrupt_read):
                with self.assertRaises(gzip.BadGzipFile):
                    self.backup(directory)
            self.assertEqual([old], list(Path(directory).iterdir()))

    def test_pending_and_empty_database_backup_before_write(self):
        for count, pending in [(1, [SimpleNamespace(module='core', path=Path('V999.sql'))]),
                               (0, [])]:
            with self.subTest(count=count):
                client = MagicMock()
                client.table_count.return_value = count
                steps = []
                client.backup.side_effect = lambda: steps.append('backup')
                client.execute_file.side_effect = lambda path: steps.append('write')
                with patch.object(db, 'static_check'), patch.object(db, 'enabled_manifests'), \
                        patch.object(db, 'print_plan', return_value=(client, [], [])), \
                        patch.object(db, 'MigrationLock'), \
                        patch.object(db, 'migration_preflight', return_value=(pending, [])), \
                        patch.object(db, 'record_migration'), patch.object(db, 'verify_database'):
                    db.migrate_database('production')
                self.assertEqual(['backup', 'write'], steps)

    def test_noop_migration_skips_backup_and_writes(self):
        client = MagicMock()
        client.table_count.return_value = 1
        with patch.object(db, 'static_check'), patch.object(db, 'enabled_manifests'), \
                patch.object(db, 'print_plan', return_value=(client, [], [])), \
                patch.object(db, 'MigrationLock'), \
                patch.object(db, 'migration_preflight', return_value=([], [])), \
                patch.object(db, 'verify_database'):
            db.migrate_database('production')
        client.backup.assert_not_called()
        client.execute_file.assert_not_called()

    def test_temporary_mysql_cleanup_includes_volumes_on_failure(self):
        with patch.object(db.shutil, 'which', return_value='docker'), \
                patch.object(db, 'run'), patch.object(db, 'docker_wait'), \
                patch.object(db.subprocess, 'run') as cleanup:
            with self.assertRaises(RuntimeError):
                db.with_test_mysql(MagicMock(side_effect=RuntimeError('failed')))
        self.assertEqual(['docker', 'rm', '--force', '--volumes'], cleanup.call_args.args[0][:4])


if __name__ == '__main__':
    unittest.main()

