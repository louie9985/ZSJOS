# -*- coding: utf-8 -*-
"""Run the real Java service/mapper transaction tests on retained isolated local MySQL schemas."""
import json
import os
import subprocess
import tempfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[4]
container=json.loads(subprocess.check_output(['docker','inspect','yudao-mysql']))[0]
values=dict(item.split('=',1) for item in container['Config']['Env'] if '=' in item)
port=container['NetworkSettings']['Ports']['3306/tcp'][0]['HostPort']
env=os.environ.copy()
env.update(ZSJOS_OVERTURN_MYSQL_URL=f'jdbc:mysql://127.0.0.1:{port}/mysql?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=UTF-8&connectionCollation=utf8mb4_unicode_ci',
           ZSJOS_OVERTURN_MYSQL_USER='root',ZSJOS_OVERTURN_MYSQL_PASSWORD=values['MYSQL_ROOT_PASSWORD'])
log=Path(tempfile.gettempdir())/'supervisor-overturn-mysql-transactions.log'
with log.open('w',encoding='utf-8') as output:
    result=subprocess.run(['mvn.cmd','-f','backend/pom.xml','-pl','yudao-module-zsjos','-am',
        '-Dtest=SupervisorLeadOverturnTransactionTest','-Dsurefire.failIfNoSpecifiedTests=false','test'],
        cwd=ROOT,env=env,stdout=output,stderr=subprocess.STDOUT)
print('PASS' if result.returncode==0 else 'FAIL','isolated MySQL service/mapper transactions; log:',log)
raise SystemExit(result.returncode)
