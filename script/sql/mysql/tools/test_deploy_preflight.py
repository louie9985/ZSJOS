#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Exercise deployment ordering with shell stubs; never build or touch services."""
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
        source = (root / 'script/shell/deploy-production.sh').read_text()
        source = source.rsplit('main "$@"', 1)[0]
        source += f'\ndb_plan() {{ echo plan; return {status}; }}\n'
        for function, label in [('stop_server', 'stop'), ('build', 'build'),
                                ('db_migrate', 'migrate'), ('install_release', 'install'),
                                ('start_server', 'start'), ('cleanup_old_releases', 'cleanup')]:
            source += f'{function}() {{ echo {label}; }}\n'
        source += 'health() { return 0; }\ndeploy\n'
        result = subprocess.run(['bash'], input=source, text=True, capture_output=True)
        return result.returncode, result.stdout.splitlines()


if __name__ == '__main__':
    unittest.main()
