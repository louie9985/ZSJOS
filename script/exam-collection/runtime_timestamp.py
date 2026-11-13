# -*- coding: utf-8 -*-
import argparse, datetime, json, re, yaml, pymysql
from runtime_http import request, validate_target, ADMIN, OUT
parser = argparse.ArgumentParser()
parser.add_argument('--database', required=True)
parser.add_argument('--task-id', required=True, type=int)
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
with db.cursor() as cursor:
    cursor.execute(
        'SELECT create_time,update_time,NOW(),@@session.time_zone,@@global.time_zone FROM zsjos_exam_collection_task WHERE id=%s AND tenant_id=1',
        (args.task_id,)
    )
    row = cursor.fetchone()
detail = request(ADMIN + f'/detail?id={args.task_id}')
raw = detail.get('createdAt')
display = datetime.datetime.fromtimestamp(raw / 1000, datetime.timezone(datetime.timedelta(hours=8))).isoformat() if isinstance(raw, (int, float)) else str(raw)
result = {
    'taskId': args.task_id,
    'dbCreated': str(row[0]),
    'dbUpdated': str(row[1]),
    'dbNow': str(row[2]),
    'dbSessionZone': row[3],
    'dbGlobalZone': row[4],
    'apiCreatedAt': raw,
    'beijingFromApi': display
}
print(json.dumps(result, ensure_ascii=False))
db.close()
(OUT / 'timestamp-result.json').write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')
