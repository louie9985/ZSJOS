#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Retained local MySQL fresh/mixed fixtures; application tables remain read-only."""
import argparse
import json
from collections import Counter
from copy import deepcopy
from datetime import datetime
from pathlib import Path
import pymysql
from import_students import TABLES, apply, catalog, check, connect, digest, fetch, identity_match, packed, person_name_matches, prepare, read_source, resolve_class, save_private, summary, verify

SOURCE_DB = 'ruoyi-vue-pro'
HISTORY = ('zsjos_delivery_class', 'zsjos_service_relation', 'zsjos_collaboration_group', 'zsjos_student_contact_record', 'zsjos_business_event')


def state(c):
    return {t: digest(sorted(digest(r) for r in stream_rows(c, t))) for t in TABLES}


def stream_rows(c, table):
    # Evidence needs fixed-size row digests, not full history payloads in memory.
    with c.cursor(pymysql.cursors.SSDictCursor) as q:
        q.execute(f'SELECT * FROM `{table}`')
        yield from q


def clone_source(omit_import):
    database = 'historical_student_verify_' + datetime.now().strftime('%Y%m%d%H%M%S%f')
    c = connect(SOURCE_DB)
    try:
        with c.cursor() as q:
            q.execute(f'CREATE DATABASE `{database}` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci')
            for table in TABLES:
                q.execute(f'CREATE TABLE `{database}`.`{table}` LIKE `{SOURCE_DB}`.`{table}`')
                columns = fetch(q, 'SELECT column_name FROM information_schema.columns WHERE table_schema=%s AND table_name=%s AND extra NOT LIKE %s ORDER BY ordinal_position', (SOURCE_DB, table, '%GENERATED%'))
                names = ','.join('`' + r['COLUMN_NAME'] + '`' for r in columns)
                # Build a fresh copy without this batch; never delete or rewrite source data.
                condition = " WHERE COALESCE(creator,'')<>'historical-student-import'" if omit_import else ''
                q.execute(f'INSERT INTO `{database}`.`{table}` ({names}) SELECT {names} FROM `{SOURCE_DB}`.`{table}`' + condition)
        c.commit()
    finally:
        c.close()
    return database


def must_reject(call, message):
    try:
        call()
    except ValueError:
        return
    raise AssertionError(message)


def check_pair_rules(c, plan):
    with c.cursor() as q:
        data = catalog(q)
    sample = plan['ready'][0]
    selection = resolve_class(data, sample)
    owner = next(u for u in data['system_users'] if u['id'] == selection['ownerId'])
    another = next(u for u in data['system_users'] if u['id'] != owner['id'])
    bad = deepcopy(data)
    bad['system_users'] = [u for u in bad['system_users'] if u['id'] != owner['id']]
    must_reject(lambda: resolve_class(bad, sample), 'Missing planner accepted')
    bad = deepcopy(data)
    next(u for u in bad['system_users'] if u['id'] == owner['id'])['status'] = 1
    must_reject(lambda: resolve_class(bad, sample), 'Disabled planner accepted')
    bad = deepcopy(data)
    bad['system_user_role'] = [r for r in bad['system_user_role'] if r['user_id'] != owner['id']]
    must_reject(lambda: resolve_class(bad, sample), 'Unqualified planner accepted')
    bad = deepcopy(data)
    bad['system_users'].append(dict(owner, id=another['id']))
    must_reject(lambda: resolve_class(bad, sample), 'Duplicate planner nickname accepted')
    bad = deepcopy(data)
    exact = dict(id=-1, class_name=sample['className'], homeroom_user_id=owner['id'], status='SERVING', class_kind='NORMAL')
    bad['zsjos_delivery_class'] = [r for r in bad['zsjos_delivery_class'] if not (r['class_name'] == sample['className'] and r['homeroom_user_id'] == owner['id'])]
    bad['zsjos_delivery_class'].append(exact)
    check(resolve_class(bad, sample)['classId'] == -1, 'Exact pair not reused')
    bad['zsjos_delivery_class'].append(dict(exact, id=-2))
    must_reject(lambda: resolve_class(bad, sample), 'Duplicate class/planner pair accepted')
    bad['zsjos_delivery_class'].pop()
    bad['zsjos_delivery_class'][-1]['status'] = 'COMPLETED'
    must_reject(lambda: resolve_class(bad, sample), 'Completed pair accepted')


def run_case(source, output, omit_import):
    database = clone_source(omit_import)
    c = connect(database)
    try:
        plan = prepare(c, source, 'admin')
        confirmed = [r for r in plan['ready'] if r.get('identityConfirmation')]
        check(len(confirmed) == 4 and all(r['personState']['matchEvidence']['kind'] == 'user_confirmed_identity' for r in confirmed), 'Four confirmed identities not reused')
        for r in confirmed:
            bad = deepcopy(r)
            bad['identityConfirmation']['storedNameHash'] = '0' * 64
            with c.cursor() as q:
                check(identity_match(q, r['personState'], bad) is None, 'Changed target name accepted under explicit confirmation')
        check(sum(len(plan[k]) for k in ('ready','exceptions','duplicates','replay','existing')) == 525, 'Source decision partition mismatch')
        save_private(output / 'fixture-plan.json', plan)
        check(bool(plan['ready']) and bool(plan['classCreates']), 'Fixture does not exercise new class pairs')
        check_pair_rules(c, plan)
        before = state(c)
        original_ids = {t: {r['id']: digest(r) for r in stream_rows(c, t)} for t in HISTORY}
        changed = deepcopy(plan)
        changed['ready'][0]['ownerId'] = -1
        must_reject(lambda: apply(c, changed, source), 'Changed plan accepted')
        check(before == state(c), 'Rejected plan wrote rows')
        try:
            apply(c, plan, source, fail_after=3)
            raise AssertionError('Injected failure did not fire')
        except RuntimeError as e:
            check(str(e) == 'Injected rollback verification failure', 'Unexpected fixture failure')
        check(before == state(c), 'Rollback changed class/event/person/claim/counter rows')
        ids = apply(c, plan, source)
        check(len(ids) == len(plan['ready']) and verify(c, ids) == len(ids), 'Missing fixture service rows')
        committed = state(c)
        check(apply(c, plan, source) == [], 'Exact plan replay created records')
        check(state(c) == committed, 'Replay changed rows')
        after = prepare(c, source, 'admin')
        check(not after['ready'] and not after['classCreates'] and len(after['replay']) == len(ids) + len(plan['replay']) and len(after['existing']) == len(plan['existing']), 'Post-apply partition mismatch')
        for t, rows in original_ids.items():
            current = {r['id']: digest(r) for r in stream_rows(c, t) if r['id'] in rows}
            check(current == rows, 'Existing class/service history changed')
        with c.cursor() as q:
            for spec in plan['classCreates']:
                matches = fetch(q, 'SELECT *,HEX(class_name) name_hex,HEX(homeroom_user_name_snapshot) owner_hex FROM zsjos_delivery_class WHERE tenant_id=1 AND deleted=0 AND class_name=%s AND homeroom_user_id=%s', (spec['className'], spec['ownerId']))
                check(len(matches) == 1 and matches[0]['dept_id'] == spec['deptId'], 'Pair duplicated or wrong department')
                cls = matches[0]
                check(cls['group_id'] is None and cls['exam_schedule_id'] is None and cls['exam_schedule_snapshot'] is None, 'Invented grouping/exam facts')
                check(cls['name_hex'] == spec['className'].encode('utf-8').hex().upper() and cls['owner_hex'] == spec['ownerName'].encode('utf-8').hex().upper(), 'Class/owner UTF-8 HEX failed')
            unchanged = state(c)
            for table in ('zsjos_product_sku', 'zsjos_order', 'system_users', 'system_role', 'system_user_role'):
                check(before[table] == unchanged[table], 'Read-only evidence/catalog table changed')
            for row in plan['ready']:
                event = fetch(q, 'SELECT aggregate_id FROM zsjos_business_event WHERE tenant_id=1 AND idempotency_key=%s', (row['key'],))[0]
                actual = fetch(q, 'SELECT owner_user_id FROM zsjos_service_relation WHERE tenant_id=1 AND id=%s', (event['aggregate_id'],))[0]
                check(actual['owner_user_id'] == row['ownerId'], 'Source planner assignment lost')
                if row['personState']:
                    identity = fetch(q, 'SELECT name,mobile FROM zsjos_person WHERE tenant_id=1 AND id=%s', (row['personId'],))[0]
                    check(identity['name'] == row['personState']['name'] and identity['mobile'] == row['personState']['mobile'], 'Reused identity name/contact changed')
                    if row['personState']['name'].strip() != row['name']:
                        imported = fetch(q, 'SELECT person_id,student_name_snapshot,delivery_data_json FROM zsjos_service_relation WHERE tenant_id=1 AND id=%s', (event['aggregate_id'],))[0]
                        provenance = json.loads(imported['delivery_data_json'])
                        check(imported['person_id'] == row['personId'] and imported['student_name_snapshot'] == row['name'] and
                              provenance['identityNameMatch'] == row['personState']['matchEvidence']['kind'] and
                              provenance['identityMatchEvidence'] == row['personState']['matchEvidence']['evidence'] and
                              provenance['storedIdentityName'] == identity['name'], 'Identity reuse provenance lost')
                    if row['personState']['matchEvidence']['contactField'] == 'wechat_id':
                        check(identity['mobile'] == row['personState']['mobile'], 'WeChat contact moved into mobile')
            test_source = deepcopy(source)
            test_source['candidates'][0]['key'] += ':conflict'
            test_source['candidates'][0]['name'] = 'fixture-name-conflict'
            blocked = prepare(c, test_source, 'admin')
            check(any(r.get('key', '').endswith(':conflict') and '姓名' in r['reason'] for r in blocked['exceptions']), 'Identity mismatch not rejected')
        name_reuses = sum(bool(r['personState']) and r['personState']['name'].strip() != r['name'] for r in plan['ready'])
        rules = Counter(r['personState']['matchEvidence']['kind'] for r in plan['ready'] if r['personState'])
        wechat_reuses = sum(bool(r['personState']) and r['personState']['matchEvidence']['contactField'] == 'wechat_id' for r in plan['ready'])
        report = dict(database=database, mode='fresh' if omit_import else 'mixed-legacy', imported=len(ids), nameRuleReuses=name_reuses,
                      matchRules=dict(rules), wechatContactReuses=wechat_reuses, **summary(plan),
                      checks=['source partition', 'exact class/planner pair reuse', 'invalid/ambiguous planner rejection',
                              'duplicate/completed pair rejection', 'changed plan rejection', 'class/event/counter rollback',
                              'recovery', 'exact-plan replay zero changes', 'legacy class/service/history unchanged',
                              'source planner ownership', 'identity evidence and original names preserved', 'known WeChat contact reused without moving fields',
                              'existing service classified without writes', 'no SKU writes/grouping/invented exam dates', 'UTF-8 HEX'])
        save_private(output / 'fixture-result.json', report)
        print(packed(report))
    finally:
        c.rollback()
        c.close()


def run(source_path, output, confirmations):
    check(all(person_name_matches(stored, '示例') for stored in ('示例（老学员）',' 示例 ','示例(老学员）','示例（复购）','示例（代理）','示例（某渠道推荐）')), 'Allowed name match failed')
    for stored in ('其他（老学员）', '示例（其他姓名）', '示例（已退款）', '示例（老学员）（老学员）', 'B示例', '示例飞（老学员）'):
        check(not person_name_matches(stored, '示例'), 'Unapproved name normalization accepted')
    contact_person = dict(name='示例', mobile=None, wechat_id='13800000000')
    check(identity_match(None, contact_person, dict(name='示例',mobile='13800000000'))['contactField'] == 'wechat_id', 'Known WeChat contact rejected')
    check(identity_match(None, contact_person, dict(name='示例',mobile='13900000000')) is None, 'Unknown contact accepted')
    source = read_source(source_path, confirmations)
    check((len(source['candidates']), len(source['exceptions']), len(source['duplicates'])) == (498, 22, 5), 'Source partition mismatch')
    check(Counter(r['sheetIndex'] for r in source['candidates']) == {0: 183, 1: 203, 2: 112}, 'Source sheet counts mismatch')
    application = connect(SOURCE_DB)
    try:
        before = state(application)
        run_case(source, output / 'fresh', True)
        run_case(source, output / 'mixed', False)
        check(before == state(application), 'Application database changed during isolated verification')
        save_private(output / 'application-readonly.json', dict(database=SOURCE_DB, unchanged=True, tableHashes=before))
        print('PASS application database read-only throughout both isolated imports')
    finally:
        application.rollback()
        application.close()


if __name__ == '__main__':
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--source', type=Path, required=True)
    p.add_argument('--identity-confirmations', type=Path, required=True)
    p.add_argument('--evidence-dir', type=Path, required=True)
    a = p.parse_args()
    run(a.source, a.evidence_dir, a.identity_confirmations)
