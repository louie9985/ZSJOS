# -*- coding: utf-8 -*-
import argparse, json, re, yaml, pymysql
from runtime_http import request, validate_target, ADMIN, OUT
parser = argparse.ArgumentParser()
parser.add_argument('--database', required=True)
args = parser.parse_args()
validate_target(args.database)
p = yaml.safe_load((OUT / 'application-exam-isolated.yaml').read_text(encoding='utf-8'))
if p['yudao']['tenant']['enable'] is not True:
    raise RuntimeError('Tenant protection must remain enabled')
c = p['spring']['datasource']['dynamic']['datasource']['master']
a = re.search('jdbc:mysql://([^:/]+):(\\d+)/([^?]+)', c['url'])
db = pymysql.connect(
    host=a[1],
    port=int(a[2]),
    user=c['username'],
    password=c['password'],
    database=args.database,
    charset='utf8mb4',
    autocommit=True
)
with db.cursor() as cursor:
    cursor.execute("INSERT IGNORE INTO zsjos_exam_collection_task(id,title,status,config_version_id,fields_snapshot,token_hash,token_ciphertext,tenant_id) VALUES (999901,'synthetic-tenant-boundary','OPEN',1,'[]','synthetic-other-tenant','synthetic-unreadable-ciphertext',2)")
page = request(ADMIN + '/page?pageNo=1&pageSize=20&keyword=synthetic-tenant-boundary')
assert page['total'] == 0
request(ADMIN + '/fields', headers={'tenant-id': '2'}, success=False, expected_codes=[403])
db.close()
(OUT / 'isolation-result.json').write_text(
    json.dumps(
        {
            'tenantEnabled': True,
            'foreignTenantListExcluded': True,
            'mismatchedHeaderDeniedCode': 403
        },
        indent=2
    ),
    encoding='utf-8'
)
print('PASS real tenant header denial and mapper list isolation')
