# -*- coding: utf-8 -*-
"""Run real MySQL reedit tests against a newly created, retained isolated schema."""
import datetime
import json
import os
import subprocess
import tempfile
from pathlib import Path
from test_calendar_notifications import query, table, ROOT
from test_exam_reedit import PREREQ, MIGRATION

database = 'exam_reedit_java_' + datetime.datetime.now().strftime('%Y%m%d%H%M%S')
query(None, 'CREATE DATABASE ' + database + ' CHARACTER SET utf8mb4;')
query(database, table('zsjos_exam_schedule') + table('zsjos_schema_version')
      + table('zsjos_module_schema_version') + PREREQ + MIGRATION)
config = json.loads(subprocess.check_output(['docker', 'inspect', 'yudao-mysql']))[0]['Config']['Env']
password = next(value.partition('=')[2] for value in config if value.startswith('MYSQL_ROOT_PASSWORD='))
environment = os.environ.copy()
environment.update(EXAM_REEDIT_TEST_DB=database, EXAM_REEDIT_TEST_PASSWORD=password)
log = Path(tempfile.gettempdir()) / 'zsjos-exam-reedit-integration.log'
command = ['mvn.cmd' if os.name == 'nt' else 'mvn', '-f', 'backend/pom.xml', '-pl', 'yudao-module-zsjos', '-am', 'test',
           '-Dtest=ExamSchedule*Test,Calendar*Notification*Test,DeliveryClassServiceImplTest',
           '-Dsurefire.failIfNoSpecifiedTests=false']
print('Retained isolated integration schema:', database, flush=True)
with log.open('wb') as output:
    result = subprocess.run(command, cwd=ROOT, env=environment, stdout=output, stderr=subprocess.STDOUT)
print('Integration exit:', result.returncode, 'log:', log, flush=True)
raise SystemExit(result.returncode)
