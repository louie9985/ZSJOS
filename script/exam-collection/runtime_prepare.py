# -*- coding: utf-8 -*-
"""Create retained schema-only runtime fixture; never mutate the configured database."""
import datetime
import json
import re
import secrets
from pathlib import Path
import pymysql
import yaml
ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'artifacts/exam-collection/runtime'
OUT.mkdir(parents=True, exist_ok=True)
if (OUT / 'manifest.json').exists():
    raise RuntimeError('An isolated runtime is already registered; refusing to create or replace it.')
documents = yaml.safe_load_all((ROOT / 'backend/yudao-server/src/main/resources/application-local.yaml').read_text(encoding='utf-8'))
config = next((d['spring']['datasource']['dynamic']['datasource']['master'] for d in documents if d and 'datasource' in d.get('spring', {})))
address = re.search('jdbc:mysql://([^:/]+):(\\d+)/([^?]+)', config['url'])
source = address[3]
database = 'exam_collection_runtime_' + datetime.datetime.now().strftime('%Y%m%d%H%M%S')
connection = pymysql.connect(
    host=address[1],
    port=int(address[2]),
    user=config['username'],
    password=str(config['password']),
    database=source,
    charset='utf8mb4',
    autocommit=True
)
with connection.cursor() as cursor:
    cursor.execute('SHOW FULL TABLES WHERE Table_type = %s', ('BASE TABLE',))
    tables = [row[0] for row in cursor.fetchall()]
    assert all((re.fullmatch('[A-Za-z0-9_]+', t) for t in tables))
    cursor.execute(f'CREATE DATABASE `{database}` CHARACTER SET utf8mb4')
    for table in tables:
        cursor.execute(f'CREATE TABLE `{database}`.`{table}` LIKE `{source}`.`{table}`')
    for table in [
        'system_menu',
        'system_dict_type',
        'system_dict_data',
        'zsjos_schema_version',
        'zsjos_module_schema_version'
    ]:
        cursor.execute(f'INSERT INTO `{database}`.`{table}` SELECT * FROM `{source}`.`{table}`')
    for table in ['ACT_GE_PROPERTY', 'ACT_ID_PROPERTY']:
        cursor.execute(f"INSERT INTO `{database}`.`{table}` SELECT * FROM `{source}`.`{table}` WHERE NAME_ LIKE '%schema.version' OR NAME_ LIKE '%schema.history'")
connection.select_db(database)
sql = (ROOT / 'script/sql/mysql/migrations/V363__exam_material_collection.sql').read_text(encoding='utf-8')
delimiter, pending = (';', '')
with connection.cursor() as cursor:
    for line in sql.splitlines():
        if line.startswith('--'):
            continue
        if line.startswith('DELIMITER '):
            delimiter = line.split()[1]
            continue
        pending += line + '\n'
        if pending.rstrip().endswith(delimiter):
            cursor.execute(pending.rstrip()[:-len(delimiter)])
            while cursor.nextset():
                pass
            pending = ''
connection.close()
url = f'jdbc:mysql://{address[1]}:{address[2]}/{database}?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&characterEncoding=UTF-8'
profile = {
    'server': {'address': '127.0.0.1', 'port': 48089},
    'spring': {
        'datasource': {'dynamic': {
            'primary': 'master',
            'strict': True,
            'datasource': {'master': {
                'url': url,
                'username': config['username'],
                'password': str(config['password']),
                'driver-class-name': 'com.mysql.cj.jdbc.Driver',
                'type': 'com.zaxxer.hikari.HikariDataSource'
            }}
        }},
        'data': {'redis': {'host': '127.0.0.1', 'port': 6389, 'database': 0}},
        'quartz': {'auto-startup': False, 'job-store-type': 'memory'},
        'boot': {'admin': {'client': {'enabled': False}}}
    },
    'flowable': {'database-schema-update': False, 'async-executor-activate': False},
    'wx': {
        'mp': {'app-id': 'synthetic', 'secret': 'synthetic'},
        'miniapp': {'appid': 'synthetic', 'secret': 'synthetic'}
    },
    'yudao': {
        'security': {'mock-enable': False},
        'captcha': {'enable': False},
        'tenant': {'enable': True},
        'websocket': {'enable': True, 'path': '/ws', 'sender-type': 'local'},
        'sms-code': {
            'expire-times': '10m',
            'send-frequency': '1m',
            'send-maximum-quantity-per-day': 10,
            'begin-code': 100000,
            'end-code': 999999
        },
        'codegen': {
            'base-package': 'cn.iocoder.yudao',
            'db-schemas': [database],
            'front-type': 20,
            'vo-type': 10,
            'delete-batch-enable': True,
            'unit-test-enable': False,
            'import-enable': False
        },
        'swagger': {
            'title': 'Isolated exam acceptance',
            'description': 'Synthetic runtime',
            'author': 'fixture',
            'version': 'fixture',
            'url': 'http://127.0.0.1:48089',
            'email': 'fixture@example.invalid',
            'license': 'MIT',
            'license-url': 'http://127.0.0.1:48089'
        },
        'api-encrypt': {
            'enable': False,
            'algorithm': 'AES',
            'request-key': 'fixture-only-key-0123456789012345',
            'response-key': 'fixture-only-key-0123456789012345'
        }
    },
    'zsjos': {'student-info': {'public-base-url': 'http://localhost:5190'}},
    'mybatis-plus': {'encryptor': {'password': secrets.token_urlsafe(24)}},
    'logging': {'file': {'name': str(OUT / 'application.log')}, 'level': {'root': 'INFO'}}
}
(OUT / 'application-exam-isolated.yaml').write_text(yaml.safe_dump(profile, allow_unicode=True, sort_keys=False), encoding='utf-8')
(OUT / 'manifest.json').write_text(
    json.dumps(
        {
            'database': database,
            'schemaTables': len(tables),
            'httpPort': 48089,
            'redisPort': 6389,
            'redisContainer': 'zsjos-exam-collection-redis',
            'profile': 'exam-isolated'
        },
        indent=2
    ),
    encoding='utf-8'
)
print(json.dumps({'database': database, 'schemaTables': len(tables), 'profileWritten': True}))
