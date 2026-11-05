# -*- coding: utf-8 -*-
"""Prepared real upload/concurrency/correction checks. No calls occur before explicit target validation."""
import argparse, base64, concurrent.futures, json, struct, uuid, zlib, urllib.request, urllib.error, urllib.parse
from runtime_http import request, validate_target, ADMIN, PUBLIC, BASE, OUT

def png():

    def chunk(kind, data):
        return struct.pack('!I', len(data)) + kind + data + struct.pack('!I', zlib.crc32(kind + data) & 4294967295)
    return b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('!2I5B', 1, 1, 8, 6, 0, 0, 0)) + chunk(b'IDAT', zlib.compress(b'\x00\xff\x00\x00\xff')) + chunk(b'IEND', b'')

def upload(headers, key, content):
    boundary = 'fixture' + uuid.uuid4().hex
    body = f'--{boundary}\r\nContent-Disposition: form-data; name="fieldKey"\r\n\r\nportrait\r\n--{boundary}\r\nContent-Disposition: form-data; name="side"\r\n\r\n\r\n--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="synthetic.png"\r\nContent-Type: image/png\r\n\r\n'.encode() + content + f'\r\n--{boundary}--\r\n'.encode()
    req = urllib.request.Request(
        BASE + PUBLIC + '/attachments',
        data=body,
        headers={
            **headers,
            'X-Exam-Upload-Key': key,
            'Content-Type': 'multipart/form-data; boundary=' + boundary
        }
    )
    with urllib.request.urlopen(req, timeout=30) as response:
        result = json.load(response)
    if result.get('code') != 0:
        raise AssertionError('Upload failed with code ' + str(result.get('code')))
    return result['data']

def run():
    task = request(
        ADMIN + '/create',
        {
            'title': '合成附件与纠正验收',
            'selectedFields': ['portrait'],
            'idempotencyKey': str(uuid.uuid4())
        }
    )
    tid = task['id']
    token = urllib.parse.parse_qs(urllib.parse.urlparse(task['url']).fragment)['token'][0]
    head = {'X-Exam-Token': token}
    source_before = request(f'{ADMIN}/{tid}/archive/940002')
    session = request(PUBLIC + '/session', {}, actor=None, headers=head)['sessionToken']
    h = {**head, 'X-Exam-Session': session}
    storage = OUT / 'private-storage'
    before = {p for p in storage.rglob('*') if p.is_file()}
    data = png()
    key = str(uuid.uuid4())
    image = upload(h, key, data)
    assert upload(h, key, data)['id'] == image['id']
    with urllib.request.urlopen(
        urllib.request.Request(BASE + PUBLIC + f"/attachments/{image['id']}", headers=h),
        timeout=30
    ) as response:
        assert response.read() == data and response.headers.get('Cache-Control') == 'no-store'
    added = {p for p in storage.rglob('*') if p.is_file()} - before
    assert added and all((data not in p.read_bytes() for p in added)), 'Expected private encrypted physical object'
    other = request(PUBLIC + '/session', {}, actor=None, headers=head)['sessionToken']
    otherh = {**head, 'X-Exam-Session': other}
    request(
        PUBLIC + f"/attachments/{image['id']}",
        actor=None,
        headers=otherh,
        success=False,
        expected_codes=[1900170005]
    )
    payload = {
        'idempotencyKey': str(uuid.uuid4()),
        'values': {
            'name': '合成附件提交',
            'mobile': '13800000002',
            'wechat': 'submitted',
            'id_card': '11010519491231002X',
            'portrait': [image['id']]
        }
    }
    request(
        PUBLIC + '/submit',
        payload,
        actor=None,
        headers=otherh,
        success=False,
        expected_codes=[1900170005]
    )
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
        results = list(pool.map(lambda _: request(PUBLIC + '/submit', payload, actor=None, headers=h), range(2)))
    assert results == [True, True]
    rows = request(f'{ADMIN}/{tid}/submissions?pageNo=1&pageSize=20')['list']
    assert len(rows) == 1
    row = rows[0]
    request(
        f"{ADMIN}/{tid}/submissions/{row['id']}/resolve",
        {
            'revision': row['revision'],
            'serviceRelationId': 940002,
            'archiveRevision': source_before['revision'],
            'reason': '合成首次归属'
        }
    )
    repeat_image = upload(otherh, str(uuid.uuid4()), data)
    request(
        PUBLIC + '/submit',
        {
            'idempotencyKey': str(uuid.uuid4()),
            'values': {**payload['values'], 'portrait': [repeat_image['id']]}
        },
        actor=None,
        headers=otherh
    )
    assert len(request(f'{ADMIN}/{tid}/submissions?pageNo=1&pageSize=20')['list']) == 2
    task = request(ADMIN + f'/detail?id={tid}')
    request(f'{ADMIN}/{tid}/notify', {'revision': task['revision'], 'classIds': [920001]})
    current = request(f'{ADMIN}/{tid}/archive/940002')
    old_revision = current['revision']
    request(
        '/admin-api/zsjos/student/service/940002/archive',
        {
            'revision': old_revision,
            'changes': {'wechat': 'staff-preserved'},
            'reason': '合成后续人工维护',
            'idempotencyKey': str(uuid.uuid4())
        },
        actor='setup'
    )
    preview = request(f"{ADMIN}/{tid}/submissions/{row['id']}/correction-preview?serviceRelationId=940003")
    command = {k: preview[k] for k in ['revision', 'taskRevision', 'bindingRevision', 'latestSubmissionId']}
    command.update(
        serviceRelationId=940003,
        sourceArchiveRevision=preview['sourceArchive']['revision'],
        targetArchiveRevision=preview['targetArchive']['revision'],
        reason='合成纠正班外归属'
    )
    request(f"{ADMIN}/{tid}/submissions/{row['id']}/correct", command)
    moved = request(f'{ADMIN}/{tid}/submissions?pageNo=1&pageSize=20')['list']
    assert len(moved) == 2 and all((s['serviceRelationId'] == 940003 for s in moved))
    original = request(f'{ADMIN}/{tid}/archive/940002')
    assert original['values'].get('name') == source_before['values'].get('name') and original['values']['wechat'] == 'staff-preserved'
    target = request(f'{ADMIN}/{tid}/archive/940003')
    assert target['values']['name'] == '合成附件提交'
    history = request(
        f'/admin-api/zsjos/student/service/940002/archive/history/{old_revision}?sensitive=true',
        actor='setup'
    )
    assert history['information']['values']['name'] == '合成附件提交'
    members = request(f'{ADMIN}/{tid}/members?pageNo=1&pageSize=20')['list']
    assert next((m for m in members if m['serviceRelationId'] == 940002)).get('lastSubmissionId') is None
    state = request(ADMIN + f'/detail?id={tid}')
    assert state['expected'] == 2 and state['collected'] == 0 and (state['additional'] == 1)
    (OUT / 'http-extended-result.json').write_text(
        json.dumps(
            {
                'taskId': tid,
                'checks': [
                    'upload-replay',
                    'download-roundtrip',
                    'physical-ciphertext',
                    'cross-session-denied',
                    'same-key-concurrent-submit',
                    'correction',
                    'retained-staff-edit',
                    'historical-version',
                    'roster-null'
                ]
            },
            indent=2
        ),
        encoding='utf-8'
    )
    print('PASS extended real HTTP checks; no sensitive payloads recorded.')
if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--database', required=True)
    args = parser.parse_args()
    validate_target(args.database)
    run()
