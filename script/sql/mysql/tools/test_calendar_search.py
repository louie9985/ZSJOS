# -*- coding: utf-8 -*-
"""Retained isolated MySQL search fixture; no existing schema or data is changed."""
import datetime
import os
import shutil
import subprocess
import tempfile
from pathlib import Path
from test_calendar_notifications import ROOT, query

database = 'calendar_search_' + datetime.datetime.now().strftime('%Y%m%d%H%M%S')
query(None, f'CREATE DATABASE `{database}` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;')
password = subprocess.run(['docker', 'exec', 'yudao-mysql', 'sh', '-c', 'printf %s "$MYSQL_ROOT_PASSWORD"'], capture_output=True, check=True).stdout.decode()
environment = dict(os.environ, CALENDAR_SEARCH_TEST_DB=database, CALENDAR_SEARCH_TEST_PASSWORD=password)
log = Path(tempfile.gettempdir()) / (database + '.log')
command = [shutil.which('mvn.cmd') or shutil.which('mvn'), '-f', str(ROOT/'backend/pom.xml'), '-pl', 'yudao-module-zsjos', '-am', '-Dtest=CalendarSearch*Test,PersonalCalendarEventServiceTest,PersonalCalendarEventControllerPermissionTest,MediaAccountCalendarScopeServiceTest,LeadCalendarServiceTest,ExamScheduleServiceTest,CourseCalendarVersionTest', '-Dsurefire.failIfNoSpecifiedTests=false', 'test', '-q']
print('Retained fixture:', database, 'log:', log, flush=True)
with log.open('wb') as output:
    result = subprocess.run(command, cwd=ROOT, env=environment, stdout=output, stderr=subprocess.STDOUT)
print('Search verification exit:', result.returncode, flush=True)
raise SystemExit(result.returncode)
