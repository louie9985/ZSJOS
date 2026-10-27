#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Scoped Excel import through existing APIs; database connections are READ ONLY.

See docs/operations/education-student-import-20261027.md. Evidence includes personal
data and belongs only in the private runtime directory, never in console output.
"""
import argparse
from collections import Counter
from decimal import Decimal
import hashlib
import json
import os
from pathlib import Path
import re

import openpyxl
import requests

from transfer_student_20261015 import connect, decoded, encoded as encode_base, require

ROOT = Path('/opt/zsjos')
SOURCE = ROOT / 'output/教务学员_无冲突待导入.xlsx'
EVIDENCE = Path('/opt/zsjos-runtime/backups/education-student-import-20261027')
BASE = 'http://127.0.0.1:48080/admin-api'
CONFIRMED_SAME_NAMES = {(0, 85), (0, 97), (1, 69), (5, 42)}
_reader = None


def encoded(value):
    def normalize(item):
        if isinstance(item, Decimal):
            return {'$decimal': str(item)}
        if isinstance(item, dict):
            return {k: normalize(v) for k, v in item.items()}
        if isinstance(item, (tuple, list)):
            return [normalize(v) for v in item]
        return item
    return encode_base(normalize(value))


def save(path, data):
    temp = path.with_suffix(path.suffix + '.tmp')
    with temp.open('w', encoding='utf-8') as f:
        f.write(encoded(data)); f.flush(); os.fsync(f.fileno())
    temp.replace(path)


def read(path):
    return decoded(path.read_text(encoding='utf-8'))


def digest(data):
    return hashlib.sha256(encoded(data).encode('utf-8')).hexdigest()


def connection():
    c = connect('zsjos-mysql-1')
    with c.cursor() as q:
        q.execute('SET NAMES utf8mb4')
        q.execute('SET SESSION TRANSACTION READ ONLY')
    return c


def query(sql, args=()):
    global _reader
    if _reader is None:
        _reader = connection()
    _reader.rollback()
    with _reader.cursor() as q:
        q.execute(sql, args or None)
        return list(q.fetchall())


def api(s, method, path, payload=None):
    response = s.request(method, BASE + path, json=payload, timeout=45)
    data = response.json()
    # Backend messages may echo identity data; print stable codes only.
    require(response.status_code == 200 and data.get('code') == 0,
            f'API rejected {path}: HTTP {response.status_code}, code {data.get("code")}')
    return data['data']


def auth():
    tokens = query('SELECT access_token FROM system_oauth2_access_token '
                   'WHERE tenant_id=1 AND user_id=1 AND user_type=2 AND deleted=0 '
                   'AND expires_time>NOW() ORDER BY expires_time DESC LIMIT 1')
    require(tokens, 'No active existing ADMIN session; do not mint or impersonate a session')
    s = requests.Session()
    s.headers.update({'tenant-id': '1', 'Authorization': 'Bearer ' + tokens[0]['access_token']})
    info = api(s, 'GET', '/system/auth/get-permission-info')
    require(info['user']['id'] == 1 and
            bool({'*:*:*', 'zsjos:student:create'} & set(info['permissions'])), 'Create permission unavailable')
    return s


def snapshot():
    tables = {'classes': 'zsjos_delivery_class', 'products': 'zsjos_product',
              'skus': 'zsjos_product_sku', 'people': 'zsjos_person',
              'claims': 'zsjos_person_contact_claim', 'services': 'zsjos_service_relation'}
    with connection() as c:
        with c.cursor() as q:
            q.execute('START TRANSACTION WITH CONSISTENT SNAPSHOT, READ ONLY')
            data = {}
            for name, table in tables.items():
                q.execute(f'SELECT * FROM {table} WHERE tenant_id=1 AND deleted=0 ORDER BY id')
                data[name] = list(q.fetchall())
            q.execute('SELECT id,nickname,status,dept_id FROM system_users WHERE tenant_id=1 AND deleted=0')
            data['users'] = list(q.fetchall())
            return data


def prepare():
    require(not (EVIDENCE / 'plan.json').exists(), 'Frozen plan already exists')
    workbook = openpyxl.load_workbook(SOURCE, data_only=True)
    source_hash = hashlib.sha256(SOURCE.read_bytes()).hexdigest()
    before = snapshot()
    s = auth()
    options = api(s, 'GET', '/zsjos/student-entry/options')
    eligible_classes = {x['id']: x for x in options['classes']}
    eligible_skus = {x['skuRef'] for x in options['courses']}
    records = []
    checks = {}
    for sheet_index, sheet in enumerate(workbook.worksheets[:8]):
        for row_number, raw in enumerate(sheet.iter_rows(min_row=2, values_only=True), 2):
            if not any(x is not None for x in raw):
                continue
            require(all(x is None for x in raw[7:]), 'Unexpected optional values require mapping')
            name, mobile, wechat, product, sku, class_name, planner = [
                str(x).strip() if x is not None else None for x in raw[:7]]
            record = dict(sheet=sheet.title, sheet_index=sheet_index, row=row_number,
                          name=name, mobile=mobile, wechatId=wechat, product=product,
                          sku=sku, class_name=class_name, planner=planner, issues=[])
            require(name and mobile and re.fullmatch(r'1[3-9]\d{9}', mobile) and not wechat,
                    f'Unsupported identity at sheet {sheet_index + 1} row {row_number}')
            products = [p for p in before['products'] if p['name'] == product and p['status'] == 0]
            skus = [p for p in before['skus'] if p['sku_name'] == sku and p['status'] == 0
                    and p['spu_id'] in {p['id'] for p in products} and p['sku_ref'] in eligible_skus]
            users = [u for u in before['users'] if u['nickname'] == planner and u['status'] == 0]
            require(len(skus) == len(users) == 1, f'Catalog/personnel ambiguity: sheet {sheet_index+1}, row {row_number}')
            record.update(skuRef=skus[0]['sku_ref'], owner_id=users[0]['id'])
            # The user authorized distinct same-name classes for the Excel planners.
            classes = [cl for cl in before['classes'] if cl['class_name'] == class_name
                       and cl['status'] == 'SERVING' and cl['homeroom_user_id'] == users[0]['id']
                       and cl['id'] in eligible_classes]
            if len(classes) != 1:
                record['issues'].append('待创建指定班主任的班级')
            else:
                record['class_id'] = classes[0]['id']
            matches = [p for p in before['people'] if mobile in (p['mobile'], p['wechat_id'])]
            claims = {p['person_id'] for p in before['claims'] if p['contact_value'] == mobile}
            require(len(matches) <= 1 and not claims - {p['id'] for p in matches}, 'Contact conflict')
            require(all(p['name'].strip() == name for p in matches), 'Contact/name conflict')
            if (sheet_index, row_number) not in CONFIRMED_SAME_NAMES:
                require(not any(p['name'] == name and mobile not in (p['mobile'], p['wechat_id'])
                                for p in before['people']), 'Unreviewed same-name warning')
            refs = {p['product_ref'] for p in products}
            for rel in before['services']:
                if rel['person_id'] not in {p['id'] for p in matches}:
                    continue
                snap = json.loads(rel['service_snapshot']) if rel['service_snapshot'] else {}
                require(snap, 'Existing service missing product snapshot')
                require(snap.get('productRef') not in refs and snap.get('productName') != product
                        and snap.get('name') != product, 'Existing same-product service')
            if not record['issues']:
                if mobile not in checks:
                    checks[mobile] = api(s, 'POST', '/zsjos/student-entry/check', {'mobile': mobile, 'wechatId': None})
                check = checks[mobile]
                require(check['status'] in ('new', 'reuse'), f'Identity API rejected sheet {sheet_index+1}, row {row_number}')
                require(check['status'] != 'reuse' or check['name'].strip() == name, 'Identity API name mismatch')
                require((check.get('personId') or None) == (matches[0]['id'] if matches else None), 'Snapshot/API identity changed')
                record['preflight_check'] = check
            record['idempotency_key'] = 'edu-xlsx-' + source_hash[:16] + f'-{sheet_index+1}-{row_number}'
            records.append(record)
    require(len(records) == 458, 'Source row count changed')
    plan = dict(source_sha256=source_hash, environment='test', tenant_id=1, records=records)
    save(EVIDENCE / 'before.json', before)
    save(EVIDENCE / 'plan.json', plan)
    print(json.dumps({'records': len(records), 'ready': sum(not r['issues'] for r in records),
                      'held': sum(bool(r['issues']) for r in records), 'plan_sha256': digest(plan)}))


def verify_row(record, result):
    rows = query('SELECT * FROM zsjos_service_relation WHERE tenant_id=1 AND deleted=0 AND id=%s',
                 (result['serviceRelationId'],))
    require(len(rows) == 1, 'Missing imported service')
    row = rows[0]
    expected = {'person_id': result['personId'], 'student_name_snapshot': record['name'],
                'student_name_source': 'independent_entry', 'class_id': record['class_id'],
                'owner_user_id': record['owner_id'], 'status': 'active', 'acceptance_status': 'pending',
                'accepted_at': None, 'accepted_by_user_id': None, 'delivery_stage': None,
                'order_id': None, 'order_item_id': None, 'registration_case_id': None}
    require(all(row[k] == v for k, v in expected.items()), 'Imported service fields differ')
    require(row['teaching_delivery'] in (b'\x01', 1, True), 'Missing teaching participation')
    course = json.loads(row['service_snapshot'])
    require(course['skuRef'] == record['skuRef'] and course['skuName'] == record['sku'], 'Course snapshot differs')
    person = query('SELECT id,name,mobile,wechat_id,HEX(name) AS hex_name FROM zsjos_person WHERE tenant_id=1 AND deleted=0 AND id=%s', (row['person_id'],))[0]
    require(person['name'].strip() == record['name'] and record['mobile'] in (person['mobile'], person['wechat_id']), 'Person differs')
    hex_name = query('SELECT HEX(student_name_snapshot) AS hex_name FROM zsjos_service_relation WHERE tenant_id=1 AND id=%s', (row['id'],))[0]['hex_name']
    require(hex_name.lower() == record['name'].encode('utf-8').hex(), 'Snapshot UTF-8 mismatch')
    require(person['hex_name'].lower() == person['name'].encode('utf-8').hex(), 'Person UTF-8 mismatch')
    group = query('SELECT id FROM zsjos_collaboration_group WHERE tenant_id=1 AND deleted=0 AND id=%s AND source_service_relation_id=%s AND student_person_id=%s',
                  (row['collaboration_group_id'], row['id'], row['person_id']))
    require(len(group) == 1, 'Collaboration link missing')
    command = query('SELECT id,person_id,service_relation_id FROM zsjos_student_entry_command WHERE tenant_id=1 AND deleted=0 AND operator_user_id=1 AND command_key=%s', (record['idempotency_key'],))
    require(len(command) == 1 and command[0]['service_relation_id'] == row['id']
            and command[0]['person_id'] == row['person_id'], 'Command differs')
    events = query("SELECT id FROM zsjos_business_event WHERE tenant_id=1 AND deleted=0 AND event_type='student_independent_entry' AND aggregate_id=%s AND idempotency_key=%s",
                   (row['id'], 'student-entry:' + str(command[0]['id'])))
    require(len(events) == 1, 'Business event differs')
    return row


def apply(plan):
    s = auth()
    for record in plan['records']:
        if record['issues']:
            continue
        stem = record['idempotency_key']
        request_path = EVIDENCE / (stem + '.request.json')
        result_path = EVIDENCE / (stem + '.result.json')
        if result_path.exists():
            verify_row(record, read(result_path))
            continue
        # Save the exact request BEFORE the mutating call. After an uncertain response,
        # retries must reuse it rather than reclassifying the newly created Person.
        if request_path.exists():
            request = read(request_path)
        else:
            options = api(s, 'GET', '/zsjos/student-entry/options')
            require(any(x['id'] == record['class_id'] and x['homeroomUserId'] == record['owner_id']
                        for x in options['classes']), 'Class changed after preflight')
            check = api(s, 'POST', '/zsjos/student-entry/check', {'mobile': record['mobile'], 'wechatId': None})
            require(check['status'] in ('new', 'reuse'), 'Contact changed after preflight')
            require(check['status'] != 'reuse' or check['name'].strip() == record['name'], 'Name changed')
            prior = record['preflight_check']
            if prior['status'] == 'reuse':
                require(check.get('personId') == prior['personId'], 'Existing identity changed')
            elif check['status'] == 'reuse':
                own_results = [read(p)['personId'] for p in EVIDENCE.glob('*.result.json')]
                require(check['personId'] in own_results, 'New external identity appeared; re-review required')
            current = query('SELECT service_snapshot FROM zsjos_service_relation WHERE tenant_id=1 AND deleted=0 AND person_id=%s', (check.get('personId') or -1,))
            require(all(x['service_snapshot'] and json.loads(x['service_snapshot']).get('productName', json.loads(x['service_snapshot']).get('name')) != record['product'] for x in current), 'Same-course service appeared')
            request = dict(entryType='teaching', name=record['name'], mobile=record['mobile'], wechatId=None,
                           skuRef=record['skuRef'], directorUserId=None, teachingDelivery=True,
                           classId=record['class_id'], reusePersonId=check.get('personId'),
                           confirmReuse=check['status'] == 'reuse', idempotencyKey=stem)
            save(request_path, request)
        result = api(s, 'POST', '/zsjos/student-entry/create', request)
        save(result_path, result)
        verify_row(record, result)
    verify(plan, replay=True)


def verify(plan, replay=False):
    records = [r for r in plan['records'] if not r['issues']]
    after = []
    for record in records:
        path = EVIDENCE / (record['idempotency_key'] + '.result.json')
        require(path.exists(), 'Incomplete batch; resume with the SAME frozen plan')
        after.append(verify_row(record, read(path)))
    require(len({r['id'] for r in after}) == len(records), 'Repeated service mapping')
    if replay and records:
        s = auth()
        for record in (records[0], records[-1]):
            stem = record['idempotency_key']
            result = api(s, 'POST', '/zsjos/student-entry/create', read(EVIDENCE / (stem + '.request.json')))
            require(result == read(EVIDENCE / (stem + '.result.json')), 'Idempotency replay differs')
            verify_row(record, result)
    save(EVIDENCE / 'after.json', after)
    summary = dict(imported=len(records), people=len({r['person_id'] for r in after}),
                   pending=len(after), deferred=len(plan['records'])-len(records),
                   utf8_hex=True, identical_command_replay=replay, plan_sha256=digest(plan))
    save(EVIDENCE / 'verification.json', summary)
    print(json.dumps(summary))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('mode', choices=['prepare', 'apply', 'verify'])
    parser.add_argument('--sha256')
    args = parser.parse_args()
    environment = os.getenv('ZSJOS_AGENT_ENV')
    if environment is None:
        marker = Path('/etc/zsjos/agent-environment')
        environment = marker.read_text().strip() if marker.exists() else 'local'
    require(environment == 'test', 'This execution is authorized only for test')
    os.umask(0o077)
    EVIDENCE.mkdir(mode=0o700, parents=True, exist_ok=True)
    if args.mode == 'prepare':
        prepare()
    else:
        plan = read(EVIDENCE / 'plan.json')
        require(args.sha256 == digest(plan), 'Frozen plan digest required')
        require(hashlib.sha256(SOURCE.read_bytes()).hexdigest() == plan['source_sha256'], 'Source changed')
        apply(plan) if args.mode == 'apply' else verify(plan)


if __name__ == '__main__':
    main()
