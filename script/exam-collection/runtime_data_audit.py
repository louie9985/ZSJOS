# -*- coding: utf-8 -*-
"""Read-only storage verification for already executed synthetic HTTP scenarios."""
import argparse, json, re, urllib.parse, yaml, pymysql
from runtime_http import request, validate_target, ADMIN, OUT
parser = argparse.ArgumentParser()
parser.add_argument('--database', required=True)
args = parser.parse_args()
validate_target(args.database)
p = yaml.safe_load((OUT / 'application-exam-isolated.yaml').read_text(encoding='utf-8'))
c = p['spring']['datasource']['dynamic']['datasource']['master']
a = re.search('jdbc:mysql://([^:/]+):(\\d+)/([^?]+)', c['url'])
db = pymysql.connect(
    host=a[1],
    port=int(a[2]),
    user=c['username'],
    password=c['password'],
    database=args.database,
    charset='utf8mb4'
)
task_ids = [json.loads((OUT / name).read_text())['taskId'] for name in ['http-result.json', 'http-extended-result.json']]
with db.cursor() as cursor:
    for tid in task_ids:
        detail = request(ADMIN + f'/detail?id={tid}')
        token = urllib.parse.parse_qs(urllib.parse.urlparse(detail['url']).fragment)['token'][0]
        cursor.execute(
            'SELECT token_ciphertext FROM zsjos_exam_collection_task WHERE id=%s AND tenant_id=1',
            (tid,)
        )
        cipher = cursor.fetchone()[0]
        if token in cipher:
            raise AssertionError('Public token stored without encryption')
        cursor.execute(
            'SELECT answers_json FROM zsjos_exam_collection_submission WHERE task_id=%s AND tenant_id=1 AND submitted_at IS NOT NULL',
            (tid,)
        )
        answers = cursor.fetchall()
        if not answers or any(('11010519491231002X' in raw or '合成' in raw for raw, in answers)):
            raise AssertionError('Submitted answers are not encrypted')
    cursor.execute('SELECT HEX(class_name) FROM zsjos_delivery_class WHERE id=920001 AND tenant_id=1')
    if cursor.fetchone()[0] != '合成验收班0'.encode().hex().upper():
        raise AssertionError('Chinese fixture bytes differ')
db.close()
(OUT / 'storage-audit-result.json').write_text(
    json.dumps(
        {'taskIds': task_ids, 'tokenCiphertext': True, 'answerCiphertext': True, 'utf8Hex': True},
        indent=2
    ),
    encoding='utf-8'
)
print('PASS persisted token/answer ciphertext and UTF-8 HEX checks')
