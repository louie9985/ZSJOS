#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""One-time tenant-1 course color backfill; see docs/operations/course-color-backfill.md."""
import argparse
from collections import Counter
import json
import os
from pathlib import Path
import re
import subprocess

import pymysql

TABLE = 'zsjos_course_calendar_event'
MARKER = 'course-colors-20261014'


def require(ok, message):
    if not ok:
        raise RuntimeError(message)


def persist(path, value):
    with path.open('x', encoding='utf-8') as out:
        json.dump(value, out, ensure_ascii=False, indent=2,
                  default=lambda x: x.hex() if isinstance(x, bytes) else x.isoformat())
        out.flush()
        os.fsync(out.fileno())


def connect():
    meta = json.loads(subprocess.check_output(['docker', 'inspect', 'zsjos-mysql-1']))[0]
    config = dict(x.split('=', 1) for x in meta['Config']['Env'] if '=' in x)
    port = meta['NetworkSettings']['Ports']['3306/tcp'][0]
    require(port['HostIp'] == '127.0.0.1' and config['MYSQL_DATABASE'] == 'zsjos', 'Unexpected target')
    password = subprocess.check_output(['docker', 'exec', 'zsjos-mysql-1', 'cat',
                                        config['MYSQL_ROOT_PASSWORD_FILE']]).decode().strip()
    return pymysql.connect(host=port['HostIp'], port=int(port['HostPort']), user='root',
                           password=password, database=config['MYSQL_DATABASE'], charset='utf8mb4',
                           autocommit=False, cursorclass=pymysql.cursors.DictCursor)


def read(cur, table, lock=False):
    cur.execute(f'SELECT * FROM {table} ORDER BY id' + (' FOR UPDATE' if lock else ''))
    return cur.fetchall()


def plan(rows, palette):
    result, unmatched = [], []
    for row in rows:
        if row['tenant_id'] != 1 or row['deleted'] not in (0, b'\x00'):
            continue
        name = row['course_name']
        matches = {key for key in palette if key in name}
        # This one-time request groups Chinese-medicine pharmacist courses under pharmacist.
        if '执业中药师' in name:
            matches.add('执业药师')
        if len(matches) != 1:
            unmatched.append({'id': row['id'], 'course_name': name, 'matches': sorted(matches)})
            continue
        category = matches.pop()
        result.append({'id': row['id'], 'category': category, 'color': palette[category]})
    return result, unmatched


def update(cur, table, rows, targets, fail=False):
    before = {r['id']: r for r in rows}
    count = 0
    for target in targets:
        row = before[target['id']]
        if row['background_color'] == target['color']:
            continue
        cur.execute(f'UPDATE {table} SET background_color=%s,updater=%s,update_time=CURRENT_TIMESTAMP '
                    'WHERE id=%s AND tenant_id=1 AND deleted=0 AND background_color <=> %s',
                    (target['color'], MARKER, row['id'], row['background_color']))
        require(cur.rowcount == 1, 'Stale preimage')
        count += 1
        if fail:
            raise RuntimeError('injected failure')
    return count


def verify(before, after, targets):
    expected = {x['id']: x['color'] for x in targets}
    require(len(before) == len(after), 'Row count changed')
    for old, new in zip(before, after):
        changed = old['id'] in expected and old['background_color'] != expected[old['id']]
        allowed = {'background_color', 'updater', 'update_time'} if changed else set()
        require(all(new[k] == v for k, v in old.items() if k not in allowed), 'Unrelated field changed')
        if old['id'] in expected:
            require(new['background_color'] == expected[old['id']], 'Wrong color')
        if changed:
            require(new['updater'] == MARKER, 'Missing repair audit marker')


def rehearse(conn, cur, palette):
    table = 'course_colors_fixture'
    cur.execute(f'CREATE TEMPORARY TABLE {table} LIKE {TABLE}')
    names = ['公共营养师课程', '营养指导员课程', '注册营养师课程', '执业药师课程',
             '执业中药师课程', '其他课程', '公共营养师和注册营养师', '营养指导员外租户', '营养指导员已删除']
    for index, name in enumerate(names, 1):
        cur.execute(f'INSERT INTO {table} (id,tenant_id,course_name,course_form_value,'
                    'course_form_label_snapshot,start_time,end_time,deleted) '
                    'VALUES (%s,%s,%s,%s,%s,NOW(),NOW(),%s)',
                    (index, 2 if index == 8 else 1, name, 'FIXTURE', '验证', index == 9))
    conn.commit()
    before = read(cur, table)
    targets, unmatched = plan(before, palette)
    require(len(targets) == 5 and len(unmatched) == 2, 'Matching fixture failed')
    try:
        update(cur, table, before, targets, fail=True)
    except RuntimeError as error:
        require(str(error) == 'injected failure', 'Unexpected rehearsal failure')
        conn.rollback()
    require(read(cur, table) == before, 'Rollback failed')
    require(update(cur, table, before, targets[:1]) == 1, 'Partial fixture failed')
    conn.commit()
    partial = read(cur, table)
    require(update(cur, table, partial, targets) == 4, 'Partial recovery failed')
    conn.commit()
    after = read(cur, table)
    verify(before, after, targets)
    require(update(cur, table, after, targets) == 0, 'Repeat changed rows')
    conn.rollback()
    return {'initial_and_partial_recovery': True, 'repeat': True, 'rollback': True,
            'unmatched_ambiguous_tenant_deleted_protection': True, 'field_preservation': True}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--evidence-dir', type=Path, required=True)
    parser.add_argument('--apply', action='store_true')
    args = parser.parse_args()
    os.umask(0o077)
    args.evidence_dir.mkdir(parents=True, exist_ok=False)
    conn = connect()
    try:
        with conn.cursor() as cur:
            cur.execute('SET NAMES utf8mb4')
            cur.execute('SET SESSION innodb_lock_wait_timeout=10')
            cur.execute("SELECT colors_json FROM zsjos_calendar_palette WHERE tenant_id=1 "
                        "AND user_id=0 AND calendar_type='COURSE_SHARED' AND deleted=0")
            raw_palette = cur.fetchone()['colors_json']
            palette = {x['name']: x['color'] for x in json.loads(raw_palette)}
            require(set(palette) == {'公共营养师', '营养指导员', '注册营养师', '执业药师'}
                    and all(re.fullmatch(r'#[0-9A-F]{6}', v) for v in palette.values()), 'Unexpected palette')
            checks = rehearse(conn, cur, palette)
            conn.begin()
            cur.execute("SELECT colors_json FROM zsjos_calendar_palette WHERE tenant_id=1 "
                        "AND user_id=0 AND calendar_type='COURSE_SHARED' AND deleted=0 FOR UPDATE")
            require(cur.fetchone()['colors_json'] == raw_palette, 'Palette changed')
            before = read(cur, TABLE, lock=True)
            targets, unmatched = plan(before, palette)
            summary = {'matched': len(targets), 'categories': dict(Counter(x['category'] for x in targets)),
                       'unmatched': unmatched, 'checks': checks}
            persist(args.evidence_dir / 'before.json', before)
            persist(args.evidence_dir / 'plan.json', {'palette': palette, 'targets': targets, **summary})
            if not args.apply:
                conn.rollback()
                print(json.dumps(summary, ensure_ascii=False))
                return
            require(len(targets) == 51 and not unmatched, 'Reviewed 51-course scope changed')
            summary['updated'] = update(cur, TABLE, before, targets)
            after = read(cur, TABLE)
            verify(before, after, targets)
            require(update(cur, TABLE, after, targets) == 0, 'Repeat changed rows')
            cur.execute(f'SELECT id,course_name,HEX(course_name) AS name_hex FROM {TABLE} WHERE tenant_id=1')
            require(all(x['name_hex'] == x['course_name'].encode('utf-8').hex().upper()
                        for x in cur.fetchall()), 'UTF-8 mismatch')
            persist(args.evidence_dir / 'expected-after.json', after)
            conn.commit()
            committed = read(cur, TABLE)
            require(committed == after, 'Committed readback differs; inspect evidence')
            persist(args.evidence_dir / 'after.json', committed)
            summary['committed_readback'] = True
            persist(args.evidence_dir / 'result.json', summary)
            print(json.dumps(summary, ensure_ascii=False))
    finally:
        conn.rollback()
        conn.close()


if __name__ == '__main__':
    main()
