#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Run deletion approval regression tests on a new local MySQL fixture database.

Copies table definitions only from local Docker MySQL. Never connects to the shared
application datasource, changes its rows, deletes schemas, or prints credentials.
"""
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import uuid
import pymysql

ROOT = Path(__file__).resolve().parents[4]

def main():
    container = json.loads(subprocess.check_output(['docker', 'inspect', 'yudao-mysql'], text=True))[0]
    settings = dict(value.split('=', 1) for value in container['Config']['Env'] if '=' in value)
    password = settings['MYSQL_ROOT_PASSWORD']
    database = 'account_delete_verify_' + uuid.uuid4().hex[:12]
    with pymysql.connect(host='127.0.0.1', port=3306, user='root', password=password, charset='utf8mb4') as connection:
        with connection.cursor() as cursor:
            cursor.execute(f'CREATE DATABASE `{database}` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci')
            for table in ['zsjos_media_account', 'zsjos_media_account_delete_request', 'zsjos_production_ticket',
                          'zsjos_content_review_batch', 'zsjos_business_task']:
                cursor.execute(f'CREATE TABLE `{database}`.`{table}` LIKE `ruoyi-vue-pro`.`{table}`')
    environment = dict(os.environ, ACCOUNT_DELETE_TEST_DB=database, ACCOUNT_DELETE_TEST_PASSWORD=password)
    environment['JAVA_TOOL_OPTIONS'] = environment.get('JAVA_TOOL_OPTIONS', '') + ' -Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8'
    log = Path(tempfile.gettempdir()) / 'zsjos-account-delete-repair-maven.log'
    tests = 'MediaAccountDeleteContentProviderTest,MediaAccountDeleteMySqlTest,MediaAccountDeleteServiceTest,MediaAccountDeleteTransactionTest,MediaAccountControllerPermissionTest,BpmApprovalContentServiceImplTest'
    command = [shutil.which('mvn.cmd') or shutil.which('mvn'), '-f', str(ROOT / 'backend/pom.xml'), '-pl',
               'yudao-module-zsjos', '-am', '-Dtest=' + tests, '-Dsurefire.failIfNoSpecifiedTests=false', 'test', '-q']
    print('Fixture database:', database, flush=True)
    print('Log:', log, flush=True)
    with log.open('w', encoding='utf-8') as output:
        result = subprocess.run(command, cwd=ROOT, env=environment, stdout=output, stderr=subprocess.STDOUT)
    print('Maven exit:', result.returncode, flush=True)
    raise SystemExit(result.returncode)

if __name__ == '__main__':
    main()
