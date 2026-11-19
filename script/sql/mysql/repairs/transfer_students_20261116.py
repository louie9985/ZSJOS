#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Fixed test-tenant handover; see student-team-transfer-20261116.md."""
import argparse
import hashlib
import json
import os
from pathlib import Path

import transfer_media_team_20261101 as engine

MANIFEST_SHA256 = 'f0e57c66271e7c0e8d414864b349661de3ff49a27f642d7c7350959d10742206'
MARKER = 'student-team-transfer-20261116'
REASON = '按用户确认将指定编导及运营名下学员统一交接给目标运营和关联编导；保留教学服务及历史作者和审批快照'
SOURCE = {}
EXTRA = ('zsjos_partner_ownership', 'zsjos_partner_ownership_log')
original_verify = engine.verify


def read(q, sql, args=()):
    return list(engine.rows(q, sql, args))


def ids(values):
    return engine.id_list(values)


def check_population(q, applied=False):
    current = read(q, '''SELECT id FROM zsjos_service_relation WHERE tenant_id=1 AND deleted=0
        AND (content_director_user_id=42 OR operator_user_id=45) ORDER BY id''')
    engine.require({r['id'] for r in current} == (set() if applied else set(SOURCE)),
                   'Source population changed outside frozen manifest')


def configure(manifest):
    raw = manifest.read_bytes()
    engine.require(hashlib.sha256(raw).hexdigest() == MANIFEST_SHA256, 'Unreviewed source manifest')
    source = engine.decoded(raw.decode('utf-8'))
    engine.require(len(source) == 64 and len({r['person_id'] for r in source}) == 64, 'Manifest scope changed')
    SOURCE.update({r['id']: r for r in source})
    engine.TARGETS = {sid: (r['person_id'], 40) for sid, r in SOURCE.items()}
    engine.MARKER = MARKER
    engine.REASON = REASON
    engine.TABLES = (*engine.TABLES, *EXTRA)
    engine.capture = capture
    engine.verify = verify


def capture(q, lock=False):
    suffix = ' FOR UPDATE' if lock else ''
    people = ids(r['person_id'] for r in SOURCE.values())
    groups = ids(r['collaboration_group_id'] for r in SOURCE.values())
    if lock:
        read(q, f'SELECT id FROM zsjos_person WHERE tenant_id=1 AND id IN ({people}) ORDER BY id FOR UPDATE')

    def scoped(table, condition):
        return ids(r['id'] for r in read(q, f'SELECT id FROM {table} WHERE tenant_id=1 AND ({condition}) ORDER BY id' + suffix))

    services = scoped(engine.SERVICE, f'person_id IN ({people})')
    accounts = scoped(engine.ACCOUNT, f'student_person_id IN ({people})')
    cards = scoped(engine.CARD, f'student_person_id IN ({people})')
    stages = scoped(engine.STAGE, f'account_id IN ({accounts})')
    contents = scoped('zsjos_content', f'account_id IN ({accounts})')
    batches = scoped(engine.HISTORY[0], f'student_person_id IN ({people})')
    conditions = {
        'zsjos_person': f'id IN ({people})',
        engine.USERS: 'id IN (31,40,42,45)',
        engine.RELATION: "scene='content_director_operator' AND target_user_id=40",
        engine.SERVICE: f'person_id IN ({people})',
        engine.ACCOUNT: f'student_person_id IN ({people})',
        engine.GROUP: f'student_person_id IN ({people}) OR id IN ({groups})',
        engine.PLAN: f'student_person_id IN ({people})',
        engine.STAGE: f'account_id IN ({accounts})',
        engine.CARD: f'student_person_id IN ({people})',
        engine.INTERVIEW: f'student_person_id IN ({people})',
        engine.TASK: f"(biz_type='student_service' AND biz_id IN ({services})) OR (biz_type='positioning_card' AND biz_id IN ({cards})) OR (biz_type='media_account_diagnosis' AND biz_id IN ({accounts})) OR (biz_type='student_delivery_stage' AND biz_id IN ({stages}))",
        engine.HISTORY[0]: f'student_person_id IN ({people})',
        engine.HISTORY[1]: f'student_person_id IN ({people})',
        engine.HISTORY[2]: f'student_person_id IN ({people})',
        engine.HISTORY[3]: f'service_relation_id IN ({services})',
        'zsjos_content': f'account_id IN ({accounts})',
        'zsjos_content_version': f'content_id IN ({contents})',
        'zsjos_content_review_batch_item': f'batch_id IN ({batches})',
        'zsjos_production_ticket': f"account_id IN ({accounts}) OR JSON_OVERLAPS(account_ids_json,CAST('[{accounts}]' AS JSON))",
        engine.EVENT: f"idempotency_key LIKE '{MARKER}:%'",
        EXTRA[0]: '1=1', EXTRA[1]: '1=1',
    }
    state = {}
    for table in engine.TABLES:
        cols = 'id,tenant_id,status,deleted' if table == engine.USERS else '*'
        state[table] = read(q, f'SELECT {cols} FROM {table} WHERE tenant_id=1 AND ({conditions[table]}) ORDER BY id' + suffix)
    # Service IDs and group IDs differ after an earlier authorized service merge.
    shared = read(q, f'SELECT id FROM {engine.SERVICE} WHERE tenant_id=1 AND deleted=0 AND id NOT IN ({ids(SOURCE)}) AND collaboration_group_id IN ({groups})' + suffix)
    engine.require(not shared, 'Group shared outside reviewed scope')
    state['_target_grants'] = read(q, '''SELECT DISTINCT m.permission FROM system_user_role ur
        JOIN system_role r ON r.id=ur.role_id AND r.tenant_id=ur.tenant_id AND r.deleted=0 AND r.status=0
        JOIN system_role_menu rm ON rm.role_id=r.id AND rm.tenant_id=ur.tenant_id AND rm.deleted=0
        JOIN system_menu m ON m.id=rm.menu_id AND m.deleted=0 AND m.status=0
        WHERE ur.tenant_id=1 AND ur.deleted=0 AND ur.user_id=40
        AND m.permission='zsjos:media-student:query-my' ORDER BY m.permission''')
    processes = {r['process_instance_id'] for r in state[engine.HISTORY[0]]
                 if engine.active(r) and r.get('process_instance_id')}
    if processes:
        found = read(q, 'SELECT COUNT(*) n FROM ACT_RU_EXECUTION WHERE PROC_INST_ID_ IN (' + ','.join(['%s'] * len(processes)) + ')', tuple(processes))
        engine.require(found[0]['n'] == 0, 'Active content BPM requires API handover')
    return state


def build_plan(state):
    active = engine.active
    require = engine.require
    require({r['id'] for r in state[engine.USERS] if active(r) and r['status'] == 0} == {31,40,42,45}, 'Employee unavailable')
    require(state['_target_grants'], 'Target media query permission absent')
    require([r['source_user_id'] for r in state[engine.RELATION] if active(r) and r['status'] == 0] == [31], 'Target personnel relation changed')
    services = {r['id']: r for r in state[engine.SERVICE] if active(r) and r['id'] in SOURCE}
    require(set(services) == set(SOURCE), 'Source service missing')
    mutations = []

    def add(table, row, sid, fields):
        changed = {k: v for k, v in fields.items() if row[k] != v}
        if changed:
            mutations.append(dict(table=table, id=row['id'], fields=changed, before=row, service_id=sid))

    for sid, row in services.items():
        original = SOURCE[sid]
        require(row['person_id'] == original['person_id'] and row['collaboration_group_id'] == original['collaboration_group_id']
                and row['operator_user_id'] == original['operator_user_id'] and row['content_director_user_id'] == 42
                and row['status'] == 'active' and row['acceptance_status'] == 'accepted', 'Source prerequisite changed')
        add(engine.SERVICE, row, sid, dict(content_director_user_id=31, operator_user_id=40))
    accounts = {r['id']: r for r in state[engine.ACCOUNT] if active(r) and r['create_service_relation_id'] in SOURCE}
    require(len(accounts) == 123, 'Account scope changed')
    for row in accounts.values():
        sid = row['create_service_relation_id']
        require(row['student_person_id'] == services[sid]['person_id'] and row['director_user_id'] == 42
                and row['owner_operator_user_id'] == services[sid]['operator_user_id'], 'Account/service conflict')
        add(engine.ACCOUNT, row, sid, dict(director_user_id=31, owner_operator_user_id=40))
    groups = {r['collaboration_group_id']: sid for sid, r in services.items()}
    found_groups = [r for r in state[engine.GROUP] if active(r) and r['id'] in groups]
    require(len(found_groups) == 64, 'Group scope changed')
    for row in found_groups:
        sid = groups[row['id']]
        require(row['source_service_relation_id'] == sid and row['student_person_id'] == services[sid]['person_id']
                and row['status'] == 'active' and row['director_user_id'] == 42, 'Group dependency changed')
        add(engine.GROUP, row, sid, dict(director_user_id=31, operator_user_id=40))
    plans = {r['id']: r for r in state[engine.PLAN] if active(r) and r['service_relation_id'] in SOURCE}
    require(len(plans) == 123 and {r['account_id'] for r in plans.values()} == set(accounts), 'Plan scope changed')
    for row in plans.values():
        sid = row['service_relation_id']
        require(row['status'] == 'ACTIVE' and row['director_user_id'] == 42
                and accounts[row['account_id']]['create_service_relation_id'] == sid, 'Plan prerequisite changed')
        add(engine.PLAN, row, sid, dict(director_user_id=31))
    stages = [r for r in state[engine.STAGE] if active(r) and r['account_id'] in accounts]
    require(len(stages) == 123, 'Stage scope changed')
    for row in stages:
        sid = accounts[row['account_id']]['create_service_relation_id']
        require(row['status'] == 'WAITING' and row['director_user_id'] == 42 and row['completed_at'] is None
                and not row['bpm_process_instance_id'] and plans[row['plan_id']]['service_relation_id'] == sid, 'Stage state changed')
        add(engine.STAGE, row, sid, dict(director_user_id=31))
    cards = [r for r in state[engine.CARD] if active(r) and r['service_relation_id'] in SOURCE]
    require(len(cards) == 62, 'Current-card scope changed')
    for row in cards:
        require(row['status'] == 'confirmed' and row['director_user_id'] == 42 and not row['ip_process_instance_id'], 'In-flight positioning requires review')
        add(engine.CARD, row, row['service_relation_id'], dict(director_user_id=31, operator_user_id=40))
    for row in state[engine.TASK]:
        if not active(row) or row['status'] != 'pending':
            continue
        sid = None
        if row['biz_type'] == 'media_account_diagnosis' and row['biz_id'] in accounts:
            require(row['task_type'] in {'media_account_diagnosis_7d','media_account_diagnosis_14d','media_account_diagnosis_28d'}, 'Unknown diagnosis task')
            sid = accounts[row['biz_id']]['create_service_relation_id']
        elif row['biz_type'] == 'student_delivery_stage' and row['biz_id'] in {r['id'] for r in stages}:
            require(row['task_type'] == 'student_delivery_confirmation', 'Unknown delivery task')
            stage = next(r for r in stages if r['id'] == row['biz_id'])
            sid = accounts[stage['account_id']]['create_service_relation_id']
        if sid is not None:
            require(row['assignee_type'] == 'user' and row['assignee_id'] == 42, 'Task assigned to third party')
            add(engine.TASK, row, sid, dict(assignee_id=31))
    require(all(not active(r) or r['status'] == 'completed' for r in state[engine.INTERVIEW]), 'Unfinished interview')
    batches = {}
    for row in state[engine.HISTORY[0]]:
        if active(row):
            require(row['status'] in {'COMPLETED','NEED_MODIFY','DRAFT','CANCELLED'}
                    and row['current_stage'] == ('DRAFT' if row['status'] == 'DRAFT' else 'DONE'), 'In-flight content review')
            batches[row['status']] = batches.get(row['status'], 0) + 1
    require(not state[engine.EVENT], 'Use existing plan to verify an applied handover')
    summary = {table: sum(r['table'] == table for r in mutations) for table in engine.FIELDS}
    require(summary == {engine.SERVICE:64, engine.ACCOUNT:123, engine.GROUP:64,
                        engine.PLAN:123, engine.STAGE:123, engine.CARD:62, engine.TASK:866},
            'Reviewed mutation counts changed')
    return dict(mutations=mutations, summary=summary, preservedBatches=batches)


def verify(before, after, plan):
    original_verify(before, after, plan)
    engine.require(before['_target_grants'] == after['_target_grants'], 'Configured permissions changed')
    receipts = {r['idempotency_key']: r for r in after[engine.EVENT]}
    for change in plan['mutations']:
        key = f"{MARKER}:{change['table']}:{change['id']}"
        receipt = receipts.get(key)
        engine.require(receipt is not None and receipt['aggregate_id'] == change['id']
                       and receipt['event_type'] == 'responsibility_handover', 'Invalid receipt identity')
        refs = json.loads(receipt['related_object_refs'])
        expected = dict(table=change['table'], fields={k: dict(old=change['before'][k], new=v) for k, v in change['fields'].items()}, serviceRelationId=change['service_id'])
        engine.require(refs == expected, 'Receipt reference mismatch')
        actual = next(r for r in after[change['table']] if r['id'] == change['id'])
        engine.require(actual == engine.expected_row(change, receipt['occurred_at']), 'Receipt/postimage mismatch')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--manifest', type=Path, required=True)
    parser.add_argument('--evidence-dir', type=Path, required=True)
    parser.add_argument('--apply', action='store_true')
    parser.add_argument('--verify-from', type=Path)
    args = parser.parse_args()
    marker = Path('/etc/zsjos/agent-environment')
    environment = os.getenv('ZSJOS_AGENT_ENV')
    if environment is None:
        environment = marker.read_text().strip() if marker.exists() else 'local'
    engine.require(environment == 'test', 'Only authorized test environment is supported')
    engine.require(not (args.apply and args.verify_from), 'Verification is read-only')
    configure(args.manifest)
    os.umask(0o077)
    args.evidence_dir.mkdir(parents=True, mode=0o700, exist_ok=False)
    conn = engine.connect('zsjos-mysql-1')
    try:
        with conn.cursor() as q:
            q.execute('SET NAMES utf8mb4')
            q.execute("SET time_zone='+08:00'")
            q.execute('SET SESSION innodb_lock_wait_timeout=10')
            if args.verify_from:
                before = engine.decoded((args.verify_from / 'before.json').read_text(encoding='utf-8'))
                plan = engine.decoded((args.verify_from / 'plan.json').read_text(encoding='utf-8'))
            else:
                check_population(q)
                before = capture(q)
                plan = build_plan(before)
                engine.persist(args.evidence_dir / 'before.json', before)
                engine.persist(args.evidence_dir / 'plan.json', plan)
                conn.rollback()
                print(engine.encoded(dict(phase='preflight', changes=plan['summary'], preservedBatches=plan['preservedBatches'])), flush=True)
                engine.rehearse(conn, q, before, plan)
                print('MySQL rehearsal passed: initial/repeat/failure rollback/partial recovery/stale rejection/compensation', flush=True)
                if args.apply:
                    engine.require(capture(q, lock=True) == before, 'Live prerequisite changed')
                    check_population(q)
                    engine.require(build_plan(before) == plan, 'Plan changed')
                    changed = engine.apply(q, plan)
                    after = capture(q)
                    verify(before, after, plan)
                    engine.persist(args.evidence_dir / 'after.json', after)
                    conn.commit()
                    engine.persist(args.evidence_dir / 'commit.json', dict(committed=True, rows=changed))
                else:
                    engine.persist(args.evidence_dir / 'result.json', dict(mode='verified-only', changes=plan['summary']))
                    return
            # Live writes are committed before verification through an independent connection.
            conn.rollback()
    finally:
        conn.rollback()
        conn.close()
    independent = engine.connect('zsjos-mysql-1')
    try:
        with independent.cursor() as q:
            q.execute('SET NAMES utf8mb4')
            q.execute("SET time_zone='+08:00'")
            after = capture(q)
            verify(before, after, plan)
            check_population(q, applied=True)
            for row in read(q, 'SELECT reason,HEX(reason) h FROM zsjos_business_event WHERE tenant_id=1 AND idempotency_key LIKE %s', (MARKER + ':%',)):
                engine.require(row['h'] == REASON.encode('utf-8').hex().upper(), 'Chinese audit HEX mismatch')
            for row in read(q, 'SELECT nickname,HEX(nickname) h FROM system_users WHERE tenant_id=1 AND id IN (31,40)'):
                engine.require(row['h'] == row['nickname'].encode('utf-8').hex().upper(), 'Chinese name HEX mismatch')
            engine.require(engine.apply(q, plan) == 0, 'Committed replay changed data')
            independent.rollback()
            result = dict(mode='verified-existing' if args.verify_from else 'committed', services=64, people=64,
                          changes=plan['summary'], rows=len(plan['mutations']), replay=0,
                          independentComparison=True, protectedHistoryUnchanged=True, utf8Hex=True)
            engine.persist(args.evidence_dir / 'independent-after.json', after)
            engine.persist(args.evidence_dir / 'result.json', result)
            print(engine.encoded(result))
    finally:
        independent.rollback()
        independent.close()


if __name__ == '__main__':
    main()
