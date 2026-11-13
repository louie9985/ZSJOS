# -*- coding: utf-8 -*-
"""Single-task trigger fault injection in the exact isolated fixture; retains failure evidence."""
import argparse, hashlib, json, re, uuid, urllib.parse, yaml, pymysql
from runtime_http import request, validate_target, ADMIN, PUBLIC, OUT
from runtime_http_extended import png, upload
parser = argparse.ArgumentParser()
parser.add_argument('--database', required=True)
args = parser.parse_args()
validate_target(args.database)
if args.database != 'exam_collection_runtime_20261104234105':
    raise RuntimeError('Unapproved fault-injection target')
p = yaml.safe_load((OUT / 'application-exam-isolated.yaml').read_text(encoding='utf-8'))
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
task = request(
    ADMIN + '/create',
    {
        'title': '合成上传回滚故障注入',
        'selectedFields': ['portrait'],
        'idempotencyKey': str(uuid.uuid4())
    }
)
tid = task['id']
head = {'X-Exam-Token': urllib.parse.parse_qs(urllib.parse.urlparse(task['url']).fragment)['token'][0]}
session = request(PUBLIC + '/session', {}, actor=None, headers=head)['sessionToken']
head['X-Exam-Session'] = session
trigger = 'fixture_exam_upload_fail_' + str(tid)
with db.cursor() as cursor:
    cursor.execute(
        'SELECT id FROM zsjos_exam_collection_submission WHERE tenant_id=1 AND task_id=%s AND session_token_hash=%s',
        (tid, hashlib.sha256(session.encode()).hexdigest())
    )
    sid = cursor.fetchone()[0]
    cursor.execute(
        'SELECT COUNT(*) FROM information_schema.triggers WHERE trigger_schema=DATABASE() AND trigger_name=%s',
        (trigger,)
    )
    if cursor.fetchone()[0]:
        raise RuntimeError('Fault injection trigger already exists')
    cursor.execute(f"CREATE TRIGGER `{trigger}` BEFORE INSERT ON zsjos_exam_collection_attachment FOR EACH ROW BEGIN IF NEW.task_id={int(tid)} AND NEW.submission_id={int(sid)} AND NEW.tenant_id=1 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='synthetic binding failure'; END IF; END")
try:
    try:
        upload(head, str(uuid.uuid4()), png())
    except AssertionError as error:
        if str(error) != 'Upload failed with code 1900090008':
            raise
    else:
        raise AssertionError('Injected binding failure unexpectedly succeeded')
finally:
    with db.cursor() as cursor:
        cursor.execute(
            'SELECT action_statement FROM information_schema.triggers WHERE trigger_schema=DATABASE() AND trigger_name=%s',
            (trigger,)
        )
        definition = cursor.fetchone()
        if definition and f'NEW.task_id={tid}' in definition[0]:
            cursor.execute(f'DROP TRIGGER `{trigger}`')
        else:
            raise RuntimeError('Trigger identity changed; refusing cleanup')
folder = OUT / 'private-storage' / 'exam-collection-private' / str(tid) / str(sid)
physical = [f for f in folder.rglob('*') if f.is_file()] if folder.exists() else []
with db.cursor() as cursor:
    cursor.execute(
        'SELECT COUNT(*) FROM zsjos_exam_collection_attachment WHERE task_id=%s AND submission_id=%s AND tenant_id=1',
        (tid, sid)
    )
    bindings = cursor.fetchone()[0]
    cursor.execute(
        'SELECT COUNT(*) FROM infra_file WHERE path LIKE %s',
        ('%exam-collection-private/' + str(tid) + '/%',)
    )
    metadata = cursor.fetchone()[0]
db.close()
result = {
    'taskId': tid,
    'sessionId': sid,
    'bindingRows': bindings,
    'infraMetadataRows': metadata,
    'residualPhysicalFiles': len(physical),
    'residualSizes': [f.stat().st_size for f in physical]
}
(OUT / 'rollback-result.json').write_text(json.dumps(result, indent=2), encoding='utf-8')
if bindings or metadata or physical:
    raise AssertionError('Rollback compensation failed; retained evidence in rollback-result.json')
upload(head, str(uuid.uuid4()), png())
print('PASS physical rollback compensation and successful retry after trigger cleanup')
