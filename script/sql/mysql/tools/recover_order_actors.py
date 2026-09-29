#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Source-backed imported order names. See docs/operations/order-actor-history-recovery.md.

prepare is database-read-only. apply/restore require a reviewed private plan digest.
No approval rounds, current account names, ownership or financial fields are written.
"""
from __future__ import annotations

import argparse
from collections import Counter
import datetime as dt
from decimal import Decimal
import gzip
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess

import pymysql


def dump_values(body):
    """Read mysqldump extended VALUES without executing SQL or decoding text twice."""
    i = 0
    while i < len(body):
        if body[i] in ' ,;\r\n':
            i += 1
            continue
        if body[i] != '(':
            raise ValueError('Unexpected dump tuple boundary')
        i += 1
        row = []
        while True:
            while body[i].isspace():
                i += 1
            if body[i] == "'":
                i += 1
                value = []
                while True:
                    char = body[i]
                    i += 1
                    if char == '\\':
                        char = body[i]
                        i += 1
                        value.append({'0': '\0', 'n': '\n', 'r': '\r', 't': '\t', 'b': '\b', 'Z': '\x1a'}.get(char, char))
                    elif char == "'":
                        if i < len(body) and body[i] == "'":
                            value.append("'")
                            i += 1
                        else:
                            break
                    else:
                        value.append(char)
                row.append(''.join(value))
            else:
                start = i
                while body[i] not in ',)':
                    i += 1
                token = body[start:i].strip()
                row.append(None if token == 'NULL' else token)
            while body[i].isspace():
                i += 1
            delimiter = body[i]
            i += 1
            if delimiter == ')':
                yield row
                break
            if delimiter != ',':
                raise ValueError('Unexpected dump value boundary')


def read_source(path):
    wanted = {'orders_order', 'employees_employee_profile'}
    columns, rows, active = {}, {t: [] for t in wanted}, None
    opener = gzip.open if str(path).endswith('.gz') else open
    with opener(path, 'rt', encoding='utf-8', errors='strict') as stream:
        for line in stream:
            match = re.match(r'CREATE TABLE `([^`]+)`', line)
            if match:
                active = match[1] if match[1] in wanted else None
                if active:
                    columns[active] = []
            if active:
                match = re.match(r'\s*`([^`]+)`\s+', line)
                if match:
                    columns[active].append(match[1])
                if line.startswith(')'):
                    active = None
            match = re.match(r'INSERT INTO `([^`]+)` VALUES ', line)
            if match and match[1] in wanted:
                table = match[1]
                for values in dump_values(line[match.end():]):
                    if len(values) != len(columns[table]):
                        raise ValueError('Source dump column count mismatch')
                    rows[table].append(dict(zip(columns[table], values)))
    orders, employees = {}, {}
    for row in rows['orders_order']:
        key = row['order_no']
        if not key or key in orders:
            raise ValueError('Missing or duplicate source order number')
        orders[key] = row
    for row in rows['employees_employee_profile']:
        key = int(row['id'])
        if key in employees:
            raise ValueError('Duplicate source employee')
        employees[key] = row
    if not orders or not employees:
        raise ValueError('Required legacy source tables are empty')
    return orders, employees


def canonical(value):
    if isinstance(value, bytes):
        return {'bytesHex': value.hex()}
    if isinstance(value, (dt.datetime, dt.date)):
        return {'isoDate': value.isoformat()}
    if isinstance(value, Decimal):
        return {'decimal': str(value)}
    raise TypeError(type(value).__name__)


def encode(value):
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(',', ':'), default=canonical)


def digest(value):
    return hashlib.sha256(encode(value).encode('utf-8')).hexdigest()


def row_state(row):
    result = dict(row)
    # MySQL normalizes JSON whitespace on persistence.
    if result.get('imported_actor_snapshot'):
        result['imported_actor_snapshot'] = json.loads(result['imported_actor_snapshot'])
    else:
        result['imported_actor_snapshot'] = None
    return result


def build_plan(rows, orders, employees, source_sha, captured_at, tenant, allow_backup_names):
    changes, counts = [], Counter()
    for original in rows:
        row = row_state(original)
        if row['tenant_id'] != tenant or row['deleted'] not in (0, b'\0'):
            continue
        counts['examined'] += 1
        if row['current_approval_round_id'] is not None:
            counts['has_current_round_untouched'] += 1
            continue
        old = orders.get(row['order_no'])
        if old is None:
            counts['no_source_order'] += 1
            continue
        if Decimal(str(row['total_amount'])) != Decimal(old['total_amount']):
            counts['amount_mismatch'] += 1
            continue
        if row['imported_actor_snapshot'] is not None:
            counts['existing_evidence_untouched'] += 1
            continue
        actors = {}
        for role, field, target_id in [('submitter', 'recorder_id', 'submitter_user_id'),
                                       ('formalSales', 'sales_id', 'formal_sales_user_id')]:
            source_id = old.get(field)
            if not source_id:
                counts[role + '_no_source_identity'] += 1
                continue
            source_id = int(source_id)
            name = old.get('sales_name_snapshot') if role == 'formalSales' else None
            source_kind = 'legacy_order_snapshot'
            if not name and allow_backup_names:
                name = employees.get(source_id, {}).get('display_name')
                source_kind = 'legacy_backup_profile'
            if not name or not name.strip():
                counts[role + '_no_name_evidence'] += 1
                continue
            actors[role] = {'orderUserId': row[target_id], 'sourceEmployeeId': source_id,
                            'name': name, 'nameSource': source_kind}
            counts[role + '_' + source_kind] += 1
        if not actors:
            continue
        evidence = {'version': 1, 'tenantId': tenant, 'orderNo': row['order_no'],
                    'sourceSystem': 'parttimecrm', 'sourceSha256': source_sha,
                    'sourceOrderId': int(old['id']), 'sourceCapturedAt': captured_at, **actors}
        after = dict(row, imported_actor_snapshot=evidence)
        changes.append({'id': row['id'], 'before': row, 'beforeDigest': digest(row),
                        'afterDigest': digest(after), 'evidence': evidence})
    counts['target_orders'] = len(changes)
    return {'version': 1, 'tenantId': tenant, 'sourceSha256': source_sha,
            'allowBackupNames': allow_backup_names, 'counts': dict(counts), 'changes': changes}


def connect(args):
    info = json.loads(subprocess.check_output(['docker', 'inspect', args.container]))[0]
    port = int(info['NetworkSettings']['Ports']['3306/tcp'][0]['HostPort'])
    password = Path(args.password_file).read_text().strip()
    conn = pymysql.connect(host='127.0.0.1', port=port, user='root', password=password,
                           database=args.database, charset='utf8mb4', autocommit=False,
                           cursorclass=pymysql.cursors.DictCursor)
    with conn.cursor() as cur:
        cur.execute('SET NAMES utf8mb4')
        cur.execute("SET time_zone='+08:00'")
        cur.execute('SET SESSION innodb_lock_wait_timeout=10')
    return conn


def write_private(path, data):
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(fd, 'w', encoding='utf-8') as stream:
        stream.write(encode(data) + '\n')


def execute(conn, plan, restore=False):
    """Caller owns commit/rollback. Full-row compare-and-set refuses later business changes."""
    changed = 0
    with conn.cursor() as cur:
        for item in sorted(plan['changes'], key=lambda item: item['id']):
            cur.execute('SELECT * FROM zsjos_order WHERE tenant_id=%s AND id=%s FOR UPDATE',
                        (plan['tenantId'], item['id']))
            row = cur.fetchone()
            if row is None:
                raise ValueError('Target row disappeared')
            state = digest(row_state(row))
            source_digest = item['afterDigest'] if restore else item['beforeDigest']
            desired_digest = item['beforeDigest'] if restore else item['afterDigest']
            if state == desired_digest:
                continue
            if state != source_digest:
                raise ValueError('Target changed after plan preparation; no repair committed')
            evidence = None if restore else encode(item['evidence'])
            cur.execute('UPDATE zsjos_order SET imported_actor_snapshot=%s, update_time=update_time '
                        'WHERE tenant_id=%s AND id=%s', (evidence, plan['tenantId'], item['id']))
            if cur.rowcount != 1:
                raise ValueError('Unexpected affected-row count')
            cur.execute('SELECT * FROM zsjos_order WHERE tenant_id=%s AND id=%s',
                        (plan['tenantId'], item['id']))
            if digest(row_state(cur.fetchone())) != desired_digest:
                raise ValueError('Unexpected post-write row difference')
            if not restore:
                for role, actor in item['evidence'].items():
                    if role not in ('submitter', 'formalSales'):
                        continue
                    cur.execute('SELECT HEX(JSON_UNQUOTE(JSON_EXTRACT(imported_actor_snapshot,%s))) value '
                                'FROM zsjos_order WHERE tenant_id=%s AND id=%s',
                                ('$.' + role + '.name', plan['tenantId'], item['id']))
                    if cur.fetchone()['value'] != actor['name'].encode('utf-8').hex().upper():
                        raise ValueError('Persisted name UTF-8 bytes mismatch')
            changed += 1
    return changed


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('mode', choices=['prepare', 'apply', 'restore'])
    parser.add_argument('--container', required=True)
    parser.add_argument('--database', required=True)
    parser.add_argument('--password-file', required=True)
    parser.add_argument('--tenant', type=int, required=True)
    parser.add_argument('--plan', type=Path, required=True)
    parser.add_argument('--source', type=Path)
    parser.add_argument('--source-captured-at')
    parser.add_argument('--allow-backup-names', action='store_true')
    parser.add_argument('--expected-plan-sha256')
    args = parser.parse_args()
    with connect(args) as conn:
        if args.mode == 'prepare':
            if not args.source or not args.source_captured_at:
                parser.error('prepare requires --source and --source-captured-at')
            captured_at = dt.datetime.fromisoformat(args.source_captured_at)
            if captured_at.utcoffset() is None:
                parser.error('--source-captured-at must include a time-zone offset')
            source_sha = hashlib.file_digest(args.source.open('rb'), 'sha256').hexdigest()
            orders, employees = read_source(args.source)
            with conn.cursor() as cur:
                cur.execute('START TRANSACTION READ ONLY')
                cur.execute('SELECT * FROM zsjos_order WHERE tenant_id=%s AND deleted=0 ORDER BY id', (args.tenant,))
                rows = cur.fetchall()
            conn.rollback()
            plan = build_plan(rows, orders, employees, source_sha, args.source_captured_at, args.tenant, args.allow_backup_names)
            plan.update(database=args.database, container=args.container)
            write_private(args.plan, plan)
            print(encode({'counts': plan['counts'], 'planSha256': hashlib.sha256(args.plan.read_bytes()).hexdigest()}))
        else:
            raw = args.plan.read_bytes()
            if not args.expected_plan_sha256 or hashlib.sha256(raw).hexdigest() != args.expected_plan_sha256:
                parser.error('Reviewed --expected-plan-sha256 required')
            plan = json.loads(raw)
            if (plan['database'], plan['container'], plan['tenantId']) != (args.database, args.container, args.tenant):
                parser.error('Plan target differs from requested database/container/tenant')
            # Rehearse the exact executor, verify repeat execution, then roll back.
            changed = execute(conn, plan, restore=args.mode == 'restore')
            if execute(conn, plan, restore=args.mode == 'restore') != 0:
                raise ValueError('Repeat execution is not a no-op')
            conn.rollback()
            changed_again = execute(conn, plan, restore=args.mode == 'restore')
            if changed_again != changed:
                raise ValueError('Scope changed after rehearsal')
            conn.commit()
            # Verify persisted values through a fresh transaction; no second commit is needed.
            if execute(conn, plan, restore=args.mode == 'restore') != 0:
                raise ValueError('Committed evidence verification failed')
            conn.rollback()
            print(encode({'mode': args.mode, 'changedOrders': changed, 'repeatNoOp': True}))


if __name__ == '__main__':
    main()
