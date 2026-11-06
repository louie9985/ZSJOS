# -*- coding: utf-8 -*-
"""Real HTTP fixture acceptance. Requires an explicitly started isolated backend on port 48089."""
from pathlib import Path
import json, uuid, urllib.request, urllib.error, urllib.parse, argparse, re, subprocess, yaml
ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'artifacts/exam-collection/runtime'
credentials = json.loads((OUT / 'synthetic-credentials.json').read_text())
BASE = 'http://127.0.0.1:48089'
ADMIN = '/admin-api/zsjos/exam-collection'
PUBLIC = '/public-api/zsjos/exam-collection'

def request(path, body=None, actor='exam', headers=None, success=True, expected_codes=None):
    h = {'tenant-id': '1', 'Content-Type': 'application/json'}
    if actor:
        h['Authorization'] = 'Bearer ' + credentials[actor]
    h.update(headers or {})
    req = urllib.request.Request(BASE + path, data=None if body is None else json.dumps(body).encode(), headers=h)
    try:
        with urllib.request.urlopen(req, timeout=30) as response:
            result = json.load(response)
    except urllib.error.HTTPError as error:
        result = json.loads(error.read())
    if success and result.get('code') != 0:
        raise AssertionError(f"{path.split('?')[0]} failed with code {result.get('code')}")
    if not success and result.get('code') not in (expected_codes or [403, 1900170002]):
        raise AssertionError(f"Expected specific rejection, got code {result.get('code')}")
    return result.get('data') if success else result.get('code')

def run():
    request(ADMIN + '/fields', actor='outsider', success=False)
    request(ADMIN + '/fields', actor='planner', success=False)
    fields = request(ADMIN + '/fields')
    assert all((f['key'] != 'gift_shipping_address' for f in fields))
    command = {
        'title': '合成HTTP考试资料验收',
        'description': '隔离数据库真实接口验证',
        'selectedFields': [],
        'idempotencyKey': str(uuid.uuid4())
    }
    task = request(ADMIN + '/create', command)
    tid = task['id']
    token = urllib.parse.parse_qs(urllib.parse.urlparse(task['url']).fragment)['token'][0]
    again = request(ADMIN + '/create', command)
    assert again['id'] == tid and again['url'] == task['url']
    request(
        ADMIN + '/create',
        {**command, 'title': '不同内容'},
        success=False,
        expected_codes=[1900170004]
    )
    headers = {'X-Exam-Token': token}
    runtime = request(PUBLIC + '/detail', actor=None, headers=headers)
    assert {f['key'] for f in runtime['fields']} == {'name', 'mobile', 'wechat', 'id_card'}

    def submit(name, mobile):
        session = request(PUBLIC + '/session', {}, actor=None, headers=headers)['sessionToken']
        h = {**headers, 'X-Exam-Session': session}
        payload = {
            'idempotencyKey': str(uuid.uuid4()),
            'values': {'name': name, 'mobile': mobile, 'wechat': 'synthetic', 'id_card': '11010519491231002X'}
        }
        request(PUBLIC + '/submit', payload, actor=None, headers=h)
        request(PUBLIC + '/submit', payload, actor=None, headers=h)
    submit('合成首次填写', '13800000000')
    rows = request(f'{ADMIN}/{tid}/submissions?pageNo=1&pageSize=20')['list']
    assert len(rows) == 1 and rows[0]['status'] == 'EXCEPTION'
    row = rows[0]
    archive = request(f'{ADMIN}/{tid}/archive/940001')
    request(
        f"{ADMIN}/{tid}/submissions/{row['id']}/resolve",
        {
            'revision': row['revision'],
            'serviceRelationId': 940001,
            'archiveRevision': archive['revision'],
            'reason': '合成核对归属'
        }
    )
    task = request(ADMIN + f'/detail?id={tid}')
    request(f'{ADMIN}/{tid}/notify', {'revision': task['revision'], 'classIds': [920001]})
    task = request(ADMIN + f'/detail?id={tid}')
    assert task['expected'] == 2 and task['collected'] == 1
    submit('合成重复更新', '13900000000')
    rows = request(f'{ADMIN}/{tid}/submissions?pageNo=1&pageSize=20')['list']
    assert len(rows) == 2 and all((r['status'] == 'ARCHIVED' for r in rows))
    archive = request(f'{ADMIN}/{tid}/archive/940001')
    assert archive['values']['name'] == '合成重复更新' and archive['values']['mobile'] == '13900000000'
    task = request(ADMIN + f'/detail?id={tid}')
    request(f'{ADMIN}/{tid}/complete', {'revision': task['revision'], 'reason': '合成结束验收'})
    request(
        PUBLIC + '/session',
        {},
        actor=None,
        headers=headers,
        success=False,
        expected_codes=[1900170003]
    )
    task = request(ADMIN + f'/detail?id={tid}')
    request(f'{ADMIN}/{tid}/reopen', {'revision': task['revision'], 'reason': '合成重开验收'})
    pending = request('/admin-api/zsjos/exam-collection-planner/state', actor='planner')
    assert any((r['collectionTaskId'] == tid for r in pending['pending']))
    other = request('/admin-api/zsjos/exam-collection-planner/state', actor='planner2')
    assert all((r['collectionTaskId'] != tid for r in other['pending']))
    (OUT / 'http-result.json').write_text(
        json.dumps(
            {
                'taskId': tid,
                'checks': [
                    'denied',
                    'create-replay',
                    'selected-fields',
                    'exception',
                    'manual-before-notify',
                    'repeat-submit',
                    'archive-update',
                    'complete',
                    'reopen',
                    'planner-isolation'
                ]
            },
            indent=2
        ),
        encoding='utf-8'
    )
    print('PASS real HTTP core chain; evidence contains IDs/check names only.')

def validate_target(database):
    manifest = json.loads((OUT / 'manifest.json').read_text())
    profile = yaml.safe_load((OUT / 'application-exam-isolated.yaml').read_text(encoding='utf-8'))
    configured = re.search(
        'jdbc:mysql://[^/]+/([^?]+)',
        profile['spring']['datasource']['dynamic']['datasource']['master']['url']
    )[1]
    if not (database.startswith('exam_collection_runtime_') and database == manifest['database'] == configured):
        raise RuntimeError('Runtime database target mismatch')
    if not (manifest['httpPort'] == 48089 and profile['server']['port'] == 48089 and (profile['server']['address'] == '127.0.0.1')):
        raise RuntimeError('Runtime HTTP target mismatch')
    cmd = '$p=(Get-NetTCPConnection -LocalPort 48089 -State Listen -ErrorAction Stop).OwningProcess | Select-Object -Unique; if(@($p).Count -ne 1){exit 2}; (Get-CimInstance Win32_Process -Filter "ProcessId=$p").CommandLine'
    process = subprocess.run(
        ['powershell', '-NoProfile', '-Command', cmd],
        capture_output=True,
        text=True,
        check=True
    ).stdout
    if not ('exam-isolated' in process and '48089' in process and ('ZSJ-OS-exam-material-collection' in process)):
        raise RuntimeError('Listener is not the owned isolated application')
if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--database', required=True)
    args = parser.parse_args()
    validate_target(args.database)
    run()
