#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Authorized test-only restoration of 95 teaching services to pending acceptance.

Uses the prior repair's transaction/fixture engine with operation-specific discovery,
preconditions, mutations and verification. Never part of bootstrap or migration chain.
See docs/operations/seeded-teaching-pending-20261027.md.
"""
import json
from pathlib import Path

import assign_seeded_light_course_20261026 as engine
from consolidate_student_services_20261026 import read, ids, runtime
from transfer_student_20261015 import require

ROOT = Path('/opt/zsjos-runtime/backups/seeded-teaching-pending-20261027-test')
MARKER = 'seeded-teaching-pending-20261027'
REASON = '按用户确认恢复尚无教学记录的轻创服务为待接收，保留班级及媒体交付进度，由实际学习规划师接收后启动首联'
TARGETS = engine.TARGETS
SERVICE, EVENT = engine.SERVICE, engine.EVENT
original_discover = engine.discover


def discover(q, people):
    queries = original_discover(q, people)
    # Explicitly protect the three other services that already have real teaching work.
    queries[SERVICE] = queries[SERVICE].replace(' ORDER BY id', ' OR (tenant_id=1 AND id IN (1000,1002,1080)) ORDER BY id')
    for table, condition in (
        ('zsjos_student_contact_record', 'service_relation_id IN (1000,1002,1080)'),
        ('zsjos_business_task', "biz_type='student_service' AND biz_id IN (1000,1002,1080)"),
    ):
        queries[table] = queries[table].replace(' ORDER BY id', f' OR (tenant_id=1 AND ({condition})) ORDER BY id')
    queries['zsjos_student_contact_config_version'] = "SELECT * FROM zsjos_student_contact_config_version WHERE tenant_id=1 AND deleted=0 AND status='published' ORDER BY id"
    queries['configured_accept_permissions'] = """SELECT DISTINCT m.id,m.permission FROM system_user_role ur
        JOIN system_role r ON r.id=ur.role_id AND r.tenant_id=ur.tenant_id AND r.deleted=0 AND r.status=0
        JOIN system_role_menu rm ON rm.role_id=r.id AND rm.tenant_id=r.tenant_id AND rm.deleted=0
        JOIN system_menu m ON m.id=rm.menu_id AND m.deleted=0 AND m.status=0
        WHERE ur.tenant_id=1 AND ur.user_id=400 AND ur.deleted=0
        AND m.permission='zsjos:student:accept' ORDER BY m.id"""
    return queries


def build(q, before):
    relations = {r['id']: r for r in before[SERVICE]}
    target_class = next(r for r in before['zsjos_delivery_class'] if r['id'] == 51)
    require((target_class['class_kind'], target_class['status'], target_class['homeroom_user_id'], target_class['deleted'])
            == ('NORMAL', 'SERVING', 400, b'\0'), 'Class changed')
    require(len(before['system_users']) == 1 and before['system_users'][0]['status'] == 0
            and before['system_users'][0]['deleted'] == b'\0', 'Owner unavailable')
    configs = before['zsjos_student_contact_config_version']
    require(configs and max(configs, key=lambda r: r['version_no'])['first_contact_timeout_minutes'] > 0,
            'Published first-contact configuration unavailable')
    require(before['configured_accept_permissions'], 'Configured accept permission missing')
    require(len(TARGETS) == len(set(TARGETS)) == 95, 'Allowlist error')
    for table in ('zsjos_student_contact_record', 'zsjos_class_transfer_request'):
        require(not any(r['service_relation_id'] in TARGETS and r['deleted'] == b'\0' for r in before[table]),
                'Teaching history or transfer requires review')
    # Even a deleted task with an old idempotency key must not obstruct real acceptance.
    require(not any(r['biz_type'] == 'student_service' and r['biz_id'] in TARGETS
                    for r in before['zsjos_business_task']), 'Existing teaching task requires review')
    keys = tuple(f'student-contact:accept:{rid}' for rid in TARGETS)
    require(not read(q, 'SELECT id FROM zsjos_business_task WHERE tenant_id=1 AND idempotency_key IN ('
                     + ','.join(['%s'] * len(keys)) + ')', keys), 'Acceptance task key already used')
    mutations = []
    for rid in TARGETS:
        row = relations[rid]
        require(row['creator'] in engine.SEEDS and row['status'] == 'active' and row['deleted'] == b'\0'
                and row['teaching_delivery'] == b'\1' and row['teaching_closed'] == b'\0'
                and row['teaching_restored'] == b'\0' and row['delivery_stage'] is None
                and row['class_id'] == 51 and row['owner_user_id'] == 400
                and row['acceptance_status'] == 'accepted' and row['media_ready'] == b'\1'
                and (row['content_director_user_id'] is not None or row['operator_user_id'] is not None),
                'Target lifecycle changed')
        require(json.loads(row['service_snapshot'])['productRef'] == 'course_migrated_P100044', 'Course changed')
        require(row['first_contact_progress_json'] in (None, '{}') and row['delivery_data_json'] in (None, '{}'),
                'Unreviewed teaching progress exists')
        fields = {'acceptance_status': 'pending', 'accepted_at': None, 'accepted_by_user_id': None}
        mutations.append({'id': rid, 'before': row, 'fields': fields})
    return mutations


def change(q, plan, digest, when, fail_after=None):
    for index, mutation in enumerate(plan['mutations'], 1):
        current = read(q, f'SELECT * FROM {SERVICE} WHERE tenant_id=1 AND id=%s FOR UPDATE', (mutation['id'],))
        require(current == [mutation['before']], 'Service drift')
        after = engine.expected_row(mutation, when)
        fields = {k: v for k, v in after.items() if current[0][k] != v}
        q.execute(f'UPDATE {SERVICE} SET ' + ','.join(f'`{k}`=%s' for k in fields)
                  + ' WHERE tenant_id=1 AND id=%s AND version=%s',
                  (*fields.values(), mutation['id'], mutation['before']['version']))
        require(q.rowcount == 1, 'Unexpected update count')
        refs = {'serviceRelationId': mutation['id'], 'oldAcceptanceStatus': 'accepted',
                'newAcceptanceStatus': 'pending', 'classId': 51, 'ownerUserId': 400,
                'mediaReadyPreserved': True, 'changedFields': list(fields), 'planSha256': digest}
        q.execute(f'INSERT INTO {EVENT} (event_type,aggregate_type,aggregate_id,operator_user_id,reason,related_object_refs,occurred_at,idempotency_key,creator,updater,create_time,update_time,tenant_id) VALUES (%s,%s,%s,NULL,%s,%s,%s,%s,%s,%s,%s,%s,1)',
                  ('seeded_teaching_acceptance_reset', 'student-service', mutation['id'], REASON,
                   json.dumps(refs, ensure_ascii=False), when, f"{MARKER}:{mutation['id']}", MARKER, MARKER, when, when))
        if fail_after == index:
            raise RuntimeError('injected rollback')


def verify(c, plan, digest):
    require(runtime() == plan['runtime'], 'Backend restarted/changed')
    with c.cursor() as q:
        q.execute('SET TRANSACTION READ ONLY')
        q.execute('START TRANSACTION WITH CONSISTENT SNAPSHOT')
        when = engine.guard(q, plan, digest)
        require(when is not None, 'Not applied')
        rows = read(q, f'SELECT * FROM {SERVICE} WHERE tenant_id=1 AND id IN ({ids(TARGETS)}) ORDER BY id')
        require(len(rows) == 95 and all(r['acceptance_status'] == 'pending' and r['accepted_at'] is None
                and r['accepted_by_user_id'] is None and r['class_id'] == 51 and r['owner_user_id'] == 400
                and r['media_ready'] == b'\1' and r['delivery_stage'] is None for r in rows), 'Pending state mismatch')
        hexes = read(q, f'SELECT HEX(reason) h FROM {EVENT} WHERE tenant_id=1 AND idempotency_key LIKE %s', (MARKER + ':%',))
        require(len(hexes) == 95 and all(r['h'] == REASON.encode('utf-8').hex().upper() for r in hexes), 'Audit UTF-8 mismatch')
        summary = {'when': when, 'plan_sha256': digest, 'pending': 95, 'audit_events': 95,
                   'protected_tables': len(plan['before']), 'protected_rows': sum(map(len, plan['before'].values())),
                   'media_orders_tasks_other_services_unchanged': True, 'backend_unchanged': True,
                   'utf8_hex': True, 'authenticated_workflow_tested': False}
    c.rollback()
    engine.save('verification.json', summary)
    print('PASS independent full-state verification: 95 pending; media/class/owner/orders/tasks/other services unchanged; audit HEX; backend unchanged')


if __name__ == '__main__':
    # Reuse reviewed snapshot/locking/fixture/replay machinery; never run its course mutation.
    engine.ROOT, engine.MARKER, engine.REASON = ROOT, MARKER, REASON
    engine.discover, engine.build, engine.change, engine.verify = discover, build, change, verify
    engine.main()
