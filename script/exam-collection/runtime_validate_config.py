# -*- coding: utf-8 -*-
"""Fail closed before launching the approved isolated fixture."""
import json, os, re
from pathlib import Path
import yaml
folder = Path(__file__).resolve().parents[2] / 'artifacts/exam-collection/runtime'
manifest = json.loads((folder / 'manifest.json').read_text())
profile = yaml.safe_load((folder / 'application-exam-isolated.yaml').read_text(encoding='utf-8'))
database = re.search(
    'jdbc:mysql://[^/]+/([^?]+)',
    profile['spring']['datasource']['dynamic']['datasource']['master']['url']
)[1]
if database != 'exam_collection_runtime_20261104234105' or database != manifest['database']:
    raise RuntimeError('Unexpected isolated database')
if profile['server'] != {'address': '127.0.0.1', 'port': 48089}:
    raise RuntimeError('Unexpected server binding')
redis = profile['spring']['data']['redis']
if redis.get('host') != '127.0.0.1' or redis.get('port') != 6389:
    raise RuntimeError('Unexpected cache target')
if profile['yudao']['tenant']['enable'] is not True or profile['yudao']['security']['mock-enable'] is not False:
    raise RuntimeError('Real security/tenant guards required')
for name in os.environ:
    if re.match(
        '^(SPRING_(DATASOURCE|DATA_REDIS|REDIS|CONFIG)|MYBATIS_PLUS_ENCRYPTOR|JAVA_TOOL_OPTIONS|JDK_JAVA_OPTIONS)',
        name.upper()
    ):
        raise RuntimeError('Environment overrides require review: ' + name)
print('Isolated database, loopback HTTP/cache and real tenant/authentication configuration verified')
