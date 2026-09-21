# -*- coding: utf-8 -*-
"""Run with python3 -m unittest discover -s script/shell/tests -v.

All deletions and deploy calls are confined to disposable fixture directories.
"""
import os
from pathlib import Path
import shlex
import subprocess
import tempfile
import unittest


class ReleaseRetentionTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="zsjos-retention-")
        self.addCleanup(self.temp.cleanup)
        self.base = Path(self.temp.name)
        self.root = self.base / "releases"
        self.root.mkdir()
        source = Path(__file__).resolve().parents[1] / "deploy-production.sh"
        self.functions = self.base / "functions.sh"
        self.functions.write_text(source.read_text().removesuffix('main "$@"\n'))
        for version in ("old", "previous", "new"):
            self.release(version)
        (self.root / "current").symlink_to(self.root / "new")
        (self.root / "previous-release").write_text(str(self.root / "previous") + "\n")
        self.prefix = f'''
source {shlex.quote(str(self.functions))}
RELEASES_DIR={shlex.quote(str(self.root))}
REPO_DIR={shlex.quote(str(self.base / 'repo'))}
LOG_DIR={shlex.quote(str(self.base / 'logs'))}
BACKUP_DIR={shlex.quote(str(self.base / 'backups'))}
APP_VERSION=new
'''

    def release(self, name):
        path = self.root / name
        path.mkdir()
        (path / "yudao-server.jar").touch()
        for surface in ("admin", "workbench", "h5"):
            (path / surface).mkdir()
        return path

    def run_shell(self, body):
        return subprocess.run(["bash", "-c", self.prefix + body], text=True,
                              capture_output=True, timeout=10)

    def test_retains_references_not_newest_mtime_and_is_repeatable(self):
        extra = self.release("newer-but-not-active")
        os.utime(extra, (2000000000, 2000000000))
        unrelated = self.root / "backups"
        unrelated.mkdir()
        (self.root / "old.sha256").touch()
        external = self.base / "external"
        external.mkdir()
        (external / "keep").touch()
        (self.root / "alias").symlink_to(external)
        (self.root / "old" / "external-link").symlink_to(external)
        result = self.run_shell("cleanup_old_releases\ncleanup_old_releases")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertFalse((self.root / "old").exists())
        self.assertFalse(extra.exists())
        for path in ("new", "previous", "current", "previous-release", "backups", "alias", "old.sha256"):
            self.assertTrue((self.root / path).exists(), path)
        self.assertTrue((external / "keep").exists())

    def test_invalid_references_preserve_everything(self):
        for target in ("", str(self.base), str(self.root / "missing"),
                       str(self.root / "new"), str(self.root / "previous" / "admin")):
            with self.subTest(target=target):
                (self.root / "previous-release").write_text(target)
                self.assertNotEqual(self.run_shell("cleanup_old_releases").returncode, 0)
                self.assertTrue((self.root / "old").exists())

    def test_missing_reference_and_wrong_current_preserve_everything(self):
        (self.root / "previous-release").unlink()
        self.assertNotEqual(self.run_shell("cleanup_old_releases").returncode, 0)
        (self.root / "previous-release").write_text(str(self.root / "previous"))
        self.assertNotEqual(self.run_shell("APP_VERSION=other; cleanup_old_releases").returncode, 0)
        (self.root / "current").unlink()
        (self.root / "current").symlink_to(self.base)
        self.assertNotEqual(self.run_shell("cleanup_old_releases").returncode, 0)
        self.assertTrue((self.root / "old").exists())

    def test_protected_directory_and_mount_abort_before_deleting(self):
        for prelude in ('BACKUP_DIR="$RELEASES_DIR/old/backups"',
                        'LOG_DIR="$RELEASES_DIR/old"',
                        'mountpoint() { return 0; }'):
            with self.subTest(prelude=prelude):
                result = self.run_shell(prelude + "\ncleanup_old_releases")
                self.assertNotEqual(result.returncode, 0)
                self.assertTrue((self.root / "old").exists())

    def deploy_stubs(self):
        return '''
stop_server() { :; }
build() { :; }
db_migrate() { :; }
install_release() { :; }
start_server() { :; }
sleep() { :; }
'''

    def test_deploy_waits_for_health_then_cleans(self):
        result = self.run_shell(self.deploy_stubs() + '''
load_env() { :; }
SERVER_PORT=48080
curl() {
  if [[ ! -f "$RELEASES_DIR/probed" ]]; then
    touch "$RELEASES_DIR/probed"
    printf 503
  else
    printf 200
  fi
}
deploy
''')
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertFalse((self.root / "old").exists())

    def test_failed_deploy_does_not_clean(self):
        for override in ('health() { return 1; }', 'build() { return 1; }',
                         'db_migrate() { return 1; }', 'start_server() { return 1; }'):
            with self.subTest(override=override):
                result = self.run_shell(self.deploy_stubs() + '\nhealth() { :; }\n' + override + '\ndeploy')
                self.assertNotEqual(result.returncode, 0)
                self.assertTrue((self.root / "old").exists())

    def test_start_and_restart_do_not_clean(self):
        for command in ("start", "restart"):
            result = self.run_shell(self.deploy_stubs() + f"\nmain {command}")
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertTrue((self.root / "old").exists())

    def test_cleanup_failure_reports_failure_after_successful_start(self):
        (self.root / "previous-release").unlink()
        result = self.run_shell(self.deploy_stubs() + '''
health() { :; }
start_server() { touch "$RELEASES_DIR/started"; }
deploy
''')
        self.assertNotEqual(result.returncode, 0)
        self.assertTrue((self.root / "started").exists())
        self.assertTrue((self.root / "old").exists())

    def test_symlink_reference_and_incomplete_previous_are_rejected(self):
        reference = self.root / "previous-release"
        reference.unlink()
        external = self.base / "reference"
        external.write_text(str(self.root / "previous"))
        reference.symlink_to(external)
        self.assertNotEqual(self.run_shell("cleanup_old_releases").returncode, 0)
        reference.unlink()
        reference.write_text(str(self.root / "previous"))
        (self.root / "previous" / "yudao-server.jar").unlink()
        self.assertNotEqual(self.run_shell("cleanup_old_releases").returncode, 0)
        self.assertTrue((self.root / "old").exists())


if __name__ == "__main__":
    unittest.main()
