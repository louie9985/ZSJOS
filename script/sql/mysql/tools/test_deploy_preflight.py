#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Exercise deployment ordering with shell stubs; never build or touch services."""
import os
import shutil
import tempfile
import subprocess
import unittest
from pathlib import Path


class DeployPreflightTest(unittest.TestCase):
    def test_failed_plan_does_not_stop_or_build(self):
        self.assertEqual(['plan'], self.run_deploy(1)[1])

    def test_successful_plan_precedes_stop_and_migration(self):
        code, steps = self.run_deploy(0)
        self.assertEqual(0, code)
        self.assertEqual(['plan', 'stop', 'build', 'migrate', 'install', 'start', 'cleanup'], steps)

    def run_deploy(self, status):
        root = Path(__file__).resolve().parents[4]
        source = (root / 'script/shell/deploy-production.sh').read_text(encoding='utf-8')
        source = source.rsplit('main "$@"', 1)[0]
        source += f'\ndb_plan() {{ echo plan; return {status}; }}\n'
        for function, label in [('stop_server', 'stop'), ('build', 'build'),
                                ('db_migrate', 'migrate'), ('install_release', 'install'),
                                ('start_server', 'start'), ('cleanup_old_releases', 'cleanup')]:
            source += f'{function}() {{ echo {label}; }}\n'
        source += 'health() { return 0; }\ndeploy\n'
        bash = os.environ.get('ZSJOS_TEST_BASH') or shutil.which('bash')
        if not bash:
            self.skipTest('Bash is required for deployment ordering checks')
        with tempfile.TemporaryDirectory(prefix='zsjos-deploy-test-') as directory:
            script = Path(directory) / 'deploy-test.sh'
            script.write_text(source, encoding='utf-8', newline='\n')
            result = subprocess.run([bash, str(script)], text=True, encoding='utf-8', capture_output=True)
        if not result.stdout and result.returncode:
            self.fail('Bash did not reach deployment stubs: ' + result.stderr)
        return result.returncode, result.stdout.splitlines()


if __name__ == '__main__':
    unittest.main()
