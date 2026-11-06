# -*- coding: utf-8 -*-
import argparse, json, uuid, urllib.parse
from runtime_http import request, validate_target, ADMIN, PUBLIC, OUT
parser = argparse.ArgumentParser()
parser.add_argument('--database', required=True)
args = parser.parse_args()
validate_target(args.database)
permission = request('/admin-api/system/auth/get-permission-info')
permissions = permission.get('permissions', [])
if not any((p.startswith('zsjos:exam-collection:') for p in permissions)):
    raise AssertionError('Exam permissions absent')
request('/admin-api/zsjos/student/service/940001/archive', success=False, expected_codes=[403])
request(
    '/admin-api/zsjos/student-summary/page?pageNo=1&pageSize=20',
    success=False,
    expected_codes=[403]
)
task = request(
    ADMIN + '/create',
    {'title': '合成收齐待办生命周期', 'selectedFields': [], 'idempotencyKey': str(uuid.uuid4())}
)
tid = task['id']
headers = {'X-Exam-Token': urllib.parse.parse_qs(urllib.parse.urlparse(task['url']).fragment)['token'][0]}
request(f'{ADMIN}/{tid}/notify', {'revision': 0, 'classIds': [920001]})

def pending():
    return [t for t in request('/admin-api/zsjos/exam-collection-planner/state', actor='planner')['pending'] if t['collectionTaskId'] == tid]
if len(pending()) != 1:
    raise AssertionError('Missing initial planner todo')

def identity(prefix):
    weights = [7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2]
    return prefix + '10X98765432'[sum((int(n) * w for n, w in zip(prefix, weights))) % 11]

def submit_and_bind(service, prefix):
    session = request(PUBLIC + '/session', {}, actor=None, headers=headers)['sessionToken']
    request(
        PUBLIC + '/submit',
        {
            'idempotencyKey': str(uuid.uuid4()),
            'values': {
                'name': '合成收齐',
                'mobile': '13800000001',
                'wechat': 'synthetic',
                'id_card': identity(prefix)
            }
        },
        actor=None,
        headers={**headers, 'X-Exam-Session': session}
    )
    rows = request(f'{ADMIN}/{tid}/submissions?pageNo=1&pageSize=20')['list']
    row = max(rows, key=lambda r: r['sequenceNo'])
    if row['status'] == 'EXCEPTION':
        archive = request(f'{ADMIN}/{tid}/archive/{service}')
        request(
            f"{ADMIN}/{tid}/submissions/{row['id']}/resolve",
            {
                'revision': row['revision'],
                'serviceRelationId': service,
                'archiveRevision': archive['revision'],
                'reason': '合成生命周期核对'
            }
        )
submit_and_bind(940001, '11010519800101001')
if pending()[0]['collected'] != 1:
    raise AssertionError('Planner progress mismatch')
task = request(ADMIN + f'/detail?id={tid}')
request(f'{ADMIN}/{tid}/complete', {'revision': task['revision'], 'reason': '合成缺交结束'})
if pending():
    raise AssertionError('Completion did not remove todo')
task = request(ADMIN + f'/detail?id={tid}')
request(f'{ADMIN}/{tid}/reopen', {'revision': task['revision'], 'reason': '合成恢复缺交'})
if len(pending()) != 1 or pending()[0]['collected'] != 1:
    raise AssertionError('Reopen did not restore missing-only todo')
submit_and_bind(940002, '11010519800101002')
if pending():
    raise AssertionError('Fully collected task still has planner todo')
submit_and_bind(940002, '11010519800101002')
if pending():
    raise AssertionError('Repeated filling resurrected completed todo')

def paths(nodes):
    result = []
    for node in nodes:
        result.append(node.get('path'))
        result.extend(paths(node.get('children') or []))
    return result
(OUT / 'lifecycle-result.json').write_text(
    json.dumps(
        {
            'taskId': tid,
            'permissionCodes': permissions,
            'menuPaths': paths(permission.get('menus') or []),
            'workbenchMenuPaths': paths(permission.get('workbenchMenus') or []),
            'oldArchiveDenied': True,
            'completeRemoved': True,
            'reopenMissingRestored': True,
            'fullyCollectedRemoved': True,
            'repeatDidNotResurrect': True
        },
        ensure_ascii=False,
        indent=2
    ),
    encoding='utf-8'
)
print('PASS real menu scope, old-archive denial, completed/reopened/fully-collected planner lifecycle')
