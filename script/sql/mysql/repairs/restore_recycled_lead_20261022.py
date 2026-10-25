#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Authorized single-Lead restoration; see docs/operations/lead-restore-20261022.md."""
import argparse
import datetime as dt
import json
import os
from pathlib import Path

from transfer_student_20261015 import connect, persist, require, rows

MARKER = 'lead-restore-20261022'
LEAD_NO = 'LD202609180108'
LEAD_ID, OWNER, PERSON = 6140, 27, 7642
REASON = '按用户授权通过数据库将回收客资恢复至原教务负责人，并重新启动有效性判定'
L, H, T, E, P = (
    'zsjos_lead', 'zsjos_lead_assignment_history', 'zsjos_business_task',
    'zsjos_business_event', 'zsjos_performance_attribution')
CONDITIONS = {L: 'id=6140', H: 'lead_id=6140', T: "biz_type='lead' AND biz_id=6140",
              E: "aggregate_type='lead' AND aggregate_id=6140", P: 'lead_id=6140'}
FIELDS = ('assignment_status', 'owner_user_id', 'owner_identity',
          'recycle_source_owner_user_id', 'current_assignment_history_id',
          'qualification_round_no', 'qualification_started_at', 'qualification_deadline_at',
          'qualification_rule_snapshot', 'last_activity_at', 'version', 'updater', 'update_time')


def one(cur, sql, args=()):
    found = rows(cur, sql, args)
    require(len(found) == 1, 'Expected one row')
    return found[0]


def capture(cur):
    return {table: list(rows(cur, f'SELECT * FROM {table} WHERE tenant_id=1 AND ({condition}) ORDER BY id FOR UPDATE'))
            for table, condition in CONDITIONS.items()}


def prerequisites(cur):
    user = one(cur, 'SELECT id,nickname,dept_id FROM system_users WHERE tenant_id=1 AND id=%s AND status=0 AND deleted=0 FOR SHARE', (OWNER,))
    dept = one(cur, 'SELECT id,name FROM system_dept WHERE tenant_id=1 AND id=%s AND status=0 AND deleted=0 FOR SHARE', (user['dept_id'],))
    require(not rows(cur, "SELECT id FROM zsjos_personnel_state WHERE tenant_id=1 AND system_user_id=%s AND business_state<>'enabled' AND deleted=0 FOR SHARE", (OWNER,)), 'Owner disabled')
    perms = rows(cur, '''SELECT DISTINCT m.permission FROM system_user_role ur
        JOIN system_role r ON r.id=ur.role_id AND r.tenant_id=ur.tenant_id AND r.deleted=0 AND r.status=0
        JOIN system_role_menu rm ON rm.role_id=r.id AND rm.tenant_id=r.tenant_id AND rm.deleted=0
        JOIN system_menu m ON m.id=rm.menu_id AND m.deleted=0 AND m.status=0
        WHERE ur.user_id=%s AND ur.tenant_id=1 AND ur.deleted=0
        AND m.permission IN ('zsjos:lead-follow-up:create','zsjos:lead:qualify','zsjos:sales-order:create')''', (OWNER,))
    require(len(perms) == 3, 'Required configured permissions missing')
    one(cur, 'SELECT id FROM zsjos_person WHERE tenant_id=1 AND id=%s AND deleted=0 FOR UPDATE', (PERSON,))
    for table, condition in (
        ('zsjos_order', 'person_id=7642'), ('zsjos_opportunity', '(person_id=7642 OR lead_id=6140)'),
        ('zsjos_purchase_intent', 'lead_id=6140'), ('zsjos_lead_appeal', 'lead_id=6140'),
        ('zsjos_lead_public_sea_pool_cycle', 'lead_id=6140'),
        ('zsjos_lead_public_sea_pool_compatibility_record', 'lead_id=6140'),
        ('zsjos_lead_transfer_request', 'lead_id=6140')):
        require(not rows(cur, f'SELECT id FROM {table} WHERE tenant_id=1 AND deleted=0 AND {condition} FOR UPDATE'), 'Related business state changed')
    rule = one(cur, "SELECT * FROM zsjos_lead_follow_up_rule WHERE tenant_id=1 AND code='default' AND deleted=0 FOR SHARE")
    require(rule['status'] == 0 and rule['qualification_timeout_minutes'] == 4320 and rule['version'] == 2, 'Qualification rule changed')
    org = list(rows(cur, 'SELECT center_id,business_domain FROM zsjos_performance_org WHERE tenant_id=1 AND dept_id=%s AND deleted=0 FOR SHARE', (user['dept_id'],)))
    require(len(org) <= 1, 'Ambiguous organization')
    center = one(cur, 'SELECT name FROM system_dept WHERE tenant_id=1 AND id=%s AND deleted=0 FOR SHARE', (org[0]['center_id'],)) if org else None
    return user, dept, rule, org[0] if org else None, center


def insert(cur, table, data):
    data = dict(data, tenant_id=1, creator=MARKER, updater=MARKER)
    cur.execute(f"INSERT INTO {table} ({','.join(data)}) VALUES ({','.join(['%s'] * len(data))})", tuple(data.values()))
    require(cur.rowcount == 1, 'Insertion failed')
    return cur.lastrowid


def verify(cur):
    lead = one(cur, f'SELECT * FROM {L} WHERE tenant_id=1 AND id=%s AND deleted=0', (LEAD_ID,))
    require(lead['lead_no'] == LEAD_NO and lead['person_id'] == PERSON and lead['status'] == 'submitted'
            and lead['assignment_status'] == 'owned' and lead['owner_user_id'] == OWNER
            and lead['owner_identity'] == 'education' and lead['recycle_source_owner_user_id'] is None
            and lead['suspended_at'] is None and lead['qualification_round_no'] == 3
            and lead['version'] == 2 and lead['updater'] == MARKER, 'Restored Lead differs')
    history = one(cur, f'SELECT * FROM {H} WHERE tenant_id=1 AND id=%s AND deleted=0', (lead['current_assignment_history_id'],))
    require(history['lead_id'] == LEAD_ID and history['action_type'] == 'transfer'
            and history['from_owner_user_id'] == OWNER and history['to_owner_user_id'] == OWNER
            and history['owner_identity_snapshot'] == 'education' and history['creator'] == MARKER
            and history['operator_user_id'] == 0, 'Assignment history differs')
    task = one(cur, f"SELECT * FROM {T} WHERE tenant_id=1 AND biz_type='lead' AND biz_id=%s AND status='pending' AND deleted=0", (LEAD_ID,))
    require(task['task_type'] == 'lead_qualification' and task['assignee_type'] == 'user'
            and task['assignee_id'] == OWNER and task['due_at'] == lead['qualification_deadline_at']
            and task['idempotency_key'] == 'lead-qualification:6140:3' and task['creator'] == MARKER, 'Qualification task differs')
    snapshot = json.loads(lead['qualification_rule_snapshot'])
    require(snapshot['timeoutMinutes'] == 4320 and snapshot['ruleVersion'] == 2
            and lead['qualification_deadline_at'] - lead['qualification_started_at'] == dt.timedelta(hours=72), 'Deadline differs')
    perf = one(cur, f"SELECT * FROM {P} WHERE tenant_id=1 AND fact_type='QUALIFICATION' AND fact_id=%s AND deleted=0", (task['id'],))
    require(perf['user_id'] == OWNER and perf['lead_id'] == LEAD_ID
            and perf['assignment_id'] == history['id'] and perf['outcome'] == 'pending'
            and perf['org_source'] == 'frozen' and perf['creator'] == MARKER, 'Performance attribution differs')
    events = list(rows(cur, f'SELECT * FROM {E} WHERE tenant_id=1 AND aggregate_id=%s AND creator=%s AND deleted=0 ORDER BY id', (LEAD_ID, MARKER)))
    require(len(events) == 2 and {e['event_type'] for e in events} == {'lead_transferred', 'lead_qualification_started'}, 'Business events differ')
    transfer = next(e for e in events if e['event_type'] == 'lead_transferred')
    require(transfer['from_status'] == 'recycle_pending' and transfer['to_status'] == 'submitted'
            and json.loads(transfer['related_object_refs'])['assignmentHistoryId'] == history['id'], 'Transfer event differs')
    for table, row_id, field, value in ((H, history['id'], 'reason', REASON), (E, transfer['id'], 'reason', REASON),
                                      (T, task['id'], 'title_snapshot', '有效性判定：' + LEAD_NO)):
        hex_value = one(cur, f'SELECT HEX({field}) AS h FROM {table} WHERE tenant_id=1 AND id=%s', (row_id,))['h']
        require(hex_value == value.encode('utf-8').hex().upper(), 'UTF-8 verification failed')
    return {'leadNo': LEAD_NO, 'state': 'submitted/owned', 'ownerIdentity': 'education',
            'qualificationRound': 3, 'deadlineBeijing': str(lead['qualification_deadline_at']), 'utf8Hex': 'passed'}


def apply(cur, fail_after=0):
    before = capture(cur)
    require(len(before[L]) == 1, 'Lead missing')
    lead = before[L][0]
    marked = [e for e in before[E] if e['idempotency_key'] == MARKER + ':transfer']
    if marked:
        verify(cur)
        return 0
    require(not any(r['creator'] == MARKER for records in before.values() for r in records), 'Partial repair detected; inspect backup')
    require(lead['lead_no'] == LEAD_NO and lead['person_id'] == PERSON and lead['deleted'] == b'\0'
            and lead['status'] == 'submitted' and lead['assignment_status'] == 'recycle_pending'
            and lead['owner_user_id'] is None and lead['owner_identity'] is None
            and lead['recycle_source_owner_user_id'] == OWNER and lead['qualification_round_no'] == 2
            and lead['version'] == 1 and lead['source_type'] == 'education_self_sourced', 'Lead preconditions changed')
    for key in ('suspended_at', 'current_assignment_history_id', 'current_assignment_first_follow_up_at',
                'current_assignment_first_follow_up_deadline_at', 'qualification_deadline_at',
                'qualification_rule_snapshot', 'pending_assignee_user_id', 'pending_owner_identity',
                'pending_expires_at', 'public_pool_at', 'next_follow_up_at', 'converted_at', 'closed_at'):
        require(lead[key] is None, 'Unexpected lifecycle field')
    require(not any(t['status'] == 'pending' and t['deleted'] == b'\0' for t in before[T]), 'Pending task exists')
    history = sorted((h for h in before[H] if h['deleted'] == b'\0'), key=lambda h: h['id'], reverse=True)
    require(len(history) >= 2 and history[0]['action_type'] == 'recycle'
            and history[0]['from_owner_user_id'] == OWNER and history[0]['to_owner_user_id'] is None
            and history[1]['action_type'] == 'accept' and history[1]['to_owner_user_id'] == OWNER
            and history[1]['owner_identity_snapshot'] == 'education', 'Education ownership evidence changed')
    user, dept, rule, org, center = prerequisites(cur)
    now = one(cur, 'SELECT NOW() AS n')['n']
    due = now + dt.timedelta(minutes=rule['qualification_timeout_minutes'])

    def checkpoint(step):
        if fail_after == step:
            raise RuntimeError('Injected failure')

    history_id = insert(cur, H, dict(lead_id=LEAD_ID, action_type='transfer', from_owner_user_id=OWNER,
        to_owner_user_id=OWNER, operator_user_id=0, reason=REASON, occurred_at=now, owner_identity_snapshot='education'))
    checkpoint(1)
    snapshot = dict(ruleId=rule['id'], ruleVersion=rule['version'], timeoutMinutes=4320, startedAt=now.isoformat())
    changes = dict(assignment_status='owned', owner_user_id=OWNER, owner_identity='education',
        recycle_source_owner_user_id=None, current_assignment_history_id=history_id, qualification_round_no=3,
        qualification_started_at=now, qualification_deadline_at=due, qualification_rule_snapshot=json.dumps(snapshot),
        last_activity_at=max(lead['last_activity_at'], now), version=2, updater=MARKER, update_time=now)
    cur.execute(f"UPDATE {L} SET " + ','.join(k + '=%s' for k in changes) +
                " WHERE tenant_id=1 AND id=%s AND lead_no=%s AND deleted=0 AND version=1 AND assignment_status='recycle_pending' AND owner_user_id IS NULL",
                (*changes.values(), LEAD_ID, LEAD_NO))
    require(cur.rowcount == 1, 'Concurrent Lead change')
    checkpoint(2)
    task_id = insert(cur, T, dict(task_type='lead_qualification', biz_type='lead', biz_id=LEAD_ID,
        status='pending', assignee_type='user', assignee_id=OWNER, title_snapshot='有效性判定：' + LEAD_NO,
        action_code='OPEN_LEAD_FOLLOW_UP', due_at=due, payload=json.dumps(dict(roundNo=3, ruleId=rule['id'],
        ruleVersion=rule['version'], timeoutMinutes=4320)), idempotency_key='lead-qualification:6140:3', version=0))
    checkpoint(3)
    # A new fact freezes current organization; previous historical attributions stay untouched.
    insert(cur, P, dict(id=one(cur, 'SELECT UUID_SHORT() AS id')['id'], fact_type='QUALIFICATION', fact_id=task_id,
        user_id=OWNER, user_name=user['nickname'], dept_id=user['dept_id'], dept_name=dept['name'],
        center_id=org['center_id'] if org else None, center_name=center['name'] if center else None,
        business_domain=org['business_domain'] if org and org['business_domain'] in ('sales', 'delivery') else None,
        lead_id=LEAD_ID, assignment_id=history_id, received_at=lead['ownership_started_at'], source_group='self',
        channel_code=lead['source_channel_id'], channel_label=lead['source_channel_label_snapshot'],
        outcome='pending', org_source='frozen'))
    checkpoint(4)
    common = dict(aggregate_type='lead', aggregate_id=LEAD_ID, operator_user_id=None, occurred_at=now)
    insert(cur, E, dict(common, event_type='lead_qualification_started', from_status='submitted', to_status='submitted',
        reason=REASON, related_object_refs=json.dumps(dict(roundNo=3, dueAt=due.isoformat(), repair=MARKER)),
        idempotency_key='lead-qualification-started:6140:3'))
    checkpoint(5)
    insert(cur, E, dict(common, event_type='lead_transferred', from_status='recycle_pending', to_status='submitted',
        reason=REASON, related_object_refs=json.dumps(dict(fromOwnerUserId=OWNER, toOwnerUserId=OWNER,
        assignmentHistoryId=history_id, repair=MARKER)), idempotency_key=MARKER + ':transfer'))
    checkpoint(6)
    verify(cur)
    after = capture(cur)
    for key, value in lead.items():
        if key not in FIELDS:
            require(after[L][0][key] == value, 'Unrelated Lead field changed')
    for table in (H, T, E, P):
        by_id = {r['id']: r for r in after[table]}
        require(all(by_id[r['id']] == r for r in before[table]), 'Historical row changed')
        require(len(after[table]) - len(before[table]) == (2 if table == E else 1), 'Unexpected write count')
    return 6


def controlled(seed):
    conn = connect('zsjos-mysql-1')
    try:
        with conn.cursor() as cur:
            cur.execute('SET NAMES utf8mb4')
            cur.execute("SET time_zone='+08:00'")
            for table in CONDITIONS:
                ddl = one(cur, f'SHOW CREATE TABLE {table}')['Create Table']
                cur.execute(ddl.replace('CREATE TABLE', 'CREATE TEMPORARY TABLE', 1))
                # Only computed columns are omitted; timestamp defaults retain the captured values.
                columns = [r['Field'] for r in rows(cur, f'SHOW COLUMNS FROM {table}') if not any(x in r['Extra'] for x in ('STORED GENERATED', 'VIRTUAL GENERATED'))]
                for row in seed[table]:
                    cur.execute(f"INSERT INTO {table} ({','.join(columns)}) VALUES ({','.join(['%s'] * len(columns))})", tuple(row[k] for k in columns))
            conn.commit()
            for step in range(1, 7):
                try:
                    apply(cur, fail_after=step)
                except RuntimeError as error:
                    require(str(error) == 'Injected failure', 'Unexpected test failure')
                    conn.rollback()
                else:
                    raise RuntimeError('Failure injection did not run')
                require(capture(cur) == seed, 'Failed operation left partial data')
                conn.rollback()
            cur.execute(f'UPDATE {L} SET version=99 WHERE id=%s', (LEAD_ID,))
            try:
                apply(cur)
            except RuntimeError as error:
                require(str(error) == 'Lead preconditions changed', 'Unexpected guard failure')
            else:
                raise RuntimeError('Changed state was not rejected')
            conn.rollback()
            require(apply(cur) == 6, 'Initial application failed')
            conn.commit()
            successful = capture(cur)
            require(apply(cur) == 0 and capture(cur) == successful, 'Repeat execution changed data')
            conn.rollback()
            return 'MySQL temporary clones: six rollback points, changed-state refusal, application, repeat zero-write, protected fields/history and UTF-8 passed'
    finally:
        conn.close()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apply', action='store_true')
    parser.add_argument('--evidence-dir', required=True, type=Path)
    args = parser.parse_args()
    environment = os.getenv('ZSJOS_AGENT_ENV')
    marker = Path('/etc/zsjos/agent-environment')
    if environment is None:
        environment = marker.read_text().strip() if marker.exists() else 'local'
    require(environment == 'test', 'Only the explicitly authorized test environment is supported')
    os.umask(0o077)
    args.evidence_dir.mkdir(parents=True, mode=0o700, exist_ok=False)
    conn = connect('zsjos-mysql-1')
    try:
        with conn.cursor() as cur:
            cur.execute('SET NAMES utf8mb4')
            cur.execute("SET time_zone='+08:00'")
            cur.execute('SET SESSION innodb_lock_wait_timeout=5')
            require(one(cur, 'SELECT VERSION() AS v')['v'].startswith('8.'), 'Reverify changed MySQL engine')
            seed = capture(cur)
            conn.rollback()
            if any(e['idempotency_key'] == MARKER + ':transfer' for e in seed[E]):
                require(apply(cur) == 0, 'Repeat should not write')
                result = dict(verify(cur), changedRows=0)
                conn.rollback()
            else:
                report = controlled(seed)
                print(report)
                persist(args.evidence_dir / 'verification.json', {'result': report})
                before = capture(cur)
                require(before == seed, 'Data changed during verification; inspect before retry')
                persist(args.evidence_dir / 'before.json', before)
                changed = apply(cur)
                persist(args.evidence_dir / 'after.json', capture(cur))
                result = dict(verify(cur), changedRows=changed, committed=args.apply)
                if args.apply:
                    conn.commit()
                    verify(cur)
                    conn.rollback()
                else:
                    conn.rollback()
                    require(capture(cur) == before, 'Dry-run rollback differs')
                    conn.rollback()
            persist(args.evidence_dir / 'result.json', result)
            print(json.dumps(result, ensure_ascii=False))
    except Exception:
        conn.rollback()
        raise
    finally:
        conn.close()


if __name__ == '__main__':
    main()
