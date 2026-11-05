#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Explicit current-source Lead profile backfill for 24 reviewed test orders.

Does not pretend to restore submission-time data: a separate JSON provenance
entry records the source and capture time. No existing profile is overwritten.
Uses the previously rehearsed exact-image approval-round writer.
"""
import argparse
import copy
import json
import os
from pathlib import Path

from transfer_student_20261015 import connect, require
from backfill_order_self_source_20261103 import (
    encode, decode, digest, save, rows, execute, normalized, capture_rounds, ROUND,
)
from link_approved_repurchase_leads_20261105 import PAIRS, environment, in_list, capture

MARK = 'repurchase-profile-20261105'
TEMP = 'verify_' + ROUND
# These labels mirror SalesOrderServiceImpl.buildLeadProfile and LeadConstants;
# business dictionary labels come only from persisted Lead selections below.
SOURCE_LABELS = {'internal_new_media': '新媒体提交',
                 'education_self_sourced': '教务自拓录', 'sales_self_sourced': '销售自拓录'}
IDENTITY_LABELS = {'sales': '销售', 'education': '教务'}
FIELDS = {'leadNo': 'lead_no', 'submittedName': 'submitted_name',
          'submittedMobile': 'submitted_mobile', 'submittedWechatId': 'submitted_wechat_id',
          'sourceType': 'source_type', 'sourceSubjectId': 'source_user_id',
          'ownerUserId': 'owner_user_id', 'ownerIdentity': 'owner_identity',
          'sourceChannel': 'source_channel_id', 'sourceChannelLabelSnapshot': 'source_channel_label_snapshot',
          'provinceName': 'province_name', 'cityName': 'city_name', 'leadCategory': 'lead_category',
          'leadCategoryLabelSnapshot': 'lead_category_label_snapshot', 'dispatchMode': 'dispatch_mode'}


def state(q, lock=False):
    value = capture(q, lock=lock)
    target_orders = [o for o in value['orders'] if o['order_no'] in PAIRS]
    require(len(target_orders) == 24, 'Fixed order scope changed')
    selected_leads = {o['lead_id'] for o in target_orders}
    user_ids = sorted({l[k] for l in value['protected']['zsjos_lead'] if l['id'] in selected_leads
                       for k in ('source_user_id', 'owner_user_id') if l[k] is not None})
    value['protected']['system_users'] = rows(q,
        'SELECT id,tenant_id,deleted,nickname FROM system_users WHERE tenant_id=1 AND id IN ('
        + in_list(user_ids) + ') ORDER BY id' + (' FOR SHARE NOWAIT' if lock else ''), user_ids)
    require(not rows(q, 'SELECT trigger_name FROM information_schema.triggers '
                     "WHERE trigger_schema=DATABASE() AND event_object_table='zsjos_order_approval_round'"),
            'Unexpected approval-round trigger')
    return value


def profile(lead, users):
    require(lead['partner_id'] is None and lead['source_type'] in SOURCE_LABELS
            and lead['owner_identity'] in IDENTITY_LABELS, 'Unreviewed source/identity')
    result = {target: lead[source] for target, source in FIELDS.items()}
    explicit = lead['source_provider_recorded'] == b'\1'
    self_without_provider = (lead['source_type'] in ('sales_self_sourced', 'education_self_sourced')
                             and explicit and lead['source_provider_user_id'] is None)
    def name(uid):
        user = users.get(uid)
        require(user is not None and user['deleted'] == b'\0', 'Current user evidence unavailable')
        return user['nickname']
    result.update(sourceSubjectType='ADMIN', sourceLabel=SOURCE_LABELS[lead['source_type']],
                  ownerIdentityLabel=IDENTITY_LABELS[lead['owner_identity']],
                  ownerUserName=name(lead['owner_user_id']),
                  sourceUserName=None if self_without_provider else name(lead['source_user_id']),
                  selfSourcedWithoutProvider=self_without_provider if explicit else None)
    return result


def prepare(q):
    before = state(q)
    leads = {l['id']: l for l in before['protected']['zsjos_lead']}
    users = {u['id']: u for u in before['protected']['system_users']}
    rounds = {r['id']: r for r in before['protected'][ROUND]}
    now = rows(q, 'SELECT NOW() t')[0]['t']
    changes = []
    for order in before['orders']:
        if order['order_no'] not in PAIRS:
            continue
        lead = leads[order['lead_id']]
        require(lead['lead_no'] == PAIRS[order['order_no']] and lead['person_id'] == order['person_id'],
                'Approved association changed')
        original = rounds[order['current_approval_round_id']]
        require(original['status'] == 'approved' and original['deleted'] == b'\0', 'Approval changed')
        snapshot = json.loads(original['order_snapshot'])
        require(snapshot.get('snapshotVersion') == 2 and snapshot.get('leadProfile') is None
                and 'leadProfileBackfill' not in snapshot, 'Existing profile/provenance cannot be overwritten')
        snapshot['leadProfile'] = profile(lead, users)
        snapshot['leadProfileBackfill'] = {
            'repairKey': MARK, 'capturedAt': now.isoformat(),
            'basis': 'explicitly_authorized_current_lead_and_system_user_data',
            'historicalAtSubmission': False, 'leadNo': lead['lead_no'],
            'leadRowSha256': digest(lead),
            'dictionaryLabels': 'persisted_lead_selection_snapshots',
        }
        after = copy.deepcopy(original)
        after.update(order_snapshot=json.dumps(snapshot, ensure_ascii=False),
                     version=original['version'] + 1, updater=MARK, update_time=now)
        changes.append(dict(before=original, after=after, order_no=order['order_no']))
    require(len(changes) == 24, 'Reviewed count changed')
    return dict(repair_key=MARK, pairs=PAIRS, created_at=now, before=before, changes=changes)


def protected(value, plan):
    result = copy.deepcopy(value)
    ids = {x['before']['id'] for x in plan['changes']}
    result['protected'][ROUND] = [r for r in result['protected'][ROUND] if r['id'] not in ids]
    return result


def verify(q, plan, table=ROUND):
    actual = capture_rounds(q, plan, table)
    for change in plan['changes']:
        require(normalized(actual[change['after']['id']]) == normalized(change['after']), 'Full after-image mismatch')
    ids = sorted(actual)
    data = rows(q, f"SELECT id,HEX(order_snapshot->>'$.leadProfile.sourceLabel') source_hex,"
                "HEX(order_snapshot->>'$.leadProfile.submittedName') name_hex,"
                "JSON_TYPE(order_snapshot->'$.leadProfile') profile_type "
                f'FROM {table} WHERE tenant_id=1 AND id IN (' + in_list(ids) + ')', ids)
    for row in data:
        p = json.loads(actual[row['id']]['order_snapshot'])['leadProfile']
        require(row['profile_type'] == 'OBJECT', 'Profile missing')
        for field, col in [('sourceLabel', 'source_hex'), ('submittedName', 'name_hex')]:
            require(row[col] == p[field].encode('utf-8').hex().upper(), 'UTF-8 HEX mismatch')
    return dict(rounds=24, full_after_images=True, profile_object=True, utf8_hex=True)


def rehearse(c, q, plan):
    q.execute(f'CREATE TEMPORARY TABLE {TEMP} LIKE {ROUND}')
    columns = list(plan['changes'][0]['before'])
    q.executemany(f"INSERT INTO {TEMP} ({','.join('`'+k+'`' for k in columns)}) VALUES ("
                  + in_list(columns) + ')', [[x['before'][k] for k in columns] for x in plan['changes']])
    c.commit()
    require(execute(q, plan, TEMP) == 24, 'Initial count')
    verify(q, plan, TEMP)
    require(execute(q, plan, TEMP) == 0, 'Repeat not empty')
    require(execute(q, plan, TEMP, restore=True) == 24, 'Restore count')
    c.commit()
    for index in (1, 12, 24):
        try:
            execute(q, plan, TEMP, fail_at=index)
            raise AssertionError('Failure not injected')
        except RuntimeError as error:
            require(str(error) == 'injected_failure', 'Unexpected failure')
        c.rollback()
        actual = capture_rounds(q, plan, TEMP)
        require(all(normalized(actual[x['before']['id']]) == normalized(x['before']) for x in plan['changes']),
                'Rollback failed')
        c.rollback()
    require(execute(q, plan, TEMP, limit=7) == 7, 'Partial count')
    c.commit()
    require(execute(q, plan, TEMP) == 17, 'Recovery count')
    require(execute(q, plan, TEMP, restore=True) == 24, 'Recovery restore')
    c.commit()
    q.execute(f'UPDATE {TEMP} SET version=version+1 WHERE id=%s', (plan['changes'][-1]['before']['id'],))
    conflict = capture_rounds(q, plan, TEMP)
    try:
        execute(q, plan, TEMP)
        raise AssertionError('Conflict accepted')
    except RuntimeError as error:
        require(str(error) == 'Round changed after plan; stop', 'Unexpected conflict')
    require(capture_rounds(q, plan, TEMP) == conflict, 'Conflict wrote partial data')
    c.rollback()
    require(execute(q, plan, TEMP) == 24, 'Reapply count')
    verify(q, plan, TEMP)
    c.commit()
    return dict(initial=24, repeat=0, failures_rolled_back=[1, 12, 24], partial=7,
                recovery=17, conflict_refused=True, restore_reapply=True)


def load_plan(path):
    plan = decode(path.read_text(encoding='utf-8'))
    proof = decode((path.parent / 'rehearsal.json').read_text(encoding='utf-8'))
    require(plan['repair_key'] == MARK and plan['pairs'] == PAIRS and len(plan['changes']) == 24
            and proof['plan_sha256'] == digest(plan), 'Invalid/unrehearsed plan')
    for change in plan['changes']:
        before, after = change['before'], change['after']
        require(before['tenant_id'] == 1 and before['status'] == 'approved', 'Wrong target')
        changed = {k for k in before if before[k] != after[k]}
        require(changed <= {'order_snapshot', 'version', 'updater', 'update_time'}, 'Unauthorized fields')
        old, new = json.loads(before['order_snapshot']), json.loads(after['order_snapshot'])
        require(old.get('leadProfile') is None and new['leadProfile']['leadNo'] == PAIRS[change['order_no']]
                and new['leadProfileBackfill']['historicalAtSubmission'] is False, 'Wrong profile')
        new.pop('leadProfileBackfill')
        if 'leadProfile' in old:
            new['leadProfile'] = old['leadProfile']
        else:
            new.pop('leadProfile')
        require(new == old, 'Original snapshot fields changed')
    return plan


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('mode', choices=('prepare', 'apply', 'verify'))
    parser.add_argument('--plan', type=Path)
    parser.add_argument('--evidence-dir', type=Path, required=True)
    args = parser.parse_args()
    os.umask(0o077)
    args.evidence_dir.mkdir(parents=True, exist_ok=False, mode=0o700)
    c = connect('zsjos-mysql-1')
    try:
        with c.cursor() as q:
            environment(q)
            q.execute('SET NAMES utf8mb4')
            q.execute("SET time_zone='+08:00'")
            q.execute('SET SESSION innodb_lock_wait_timeout=3')
            c.rollback()
            if args.mode == 'prepare':
                q.execute('START TRANSACTION WITH CONSISTENT SNAPSHOT, READ ONLY')
                plan = prepare(q)
                c.rollback()
                save(args.evidence_dir / 'plan.json', plan)
                result = rehearse(c, q, plan)
            else:
                require(args.plan is not None, 'Plan required')
                plan = load_plan(args.plan)
                if args.mode == 'apply':
                    actual = state(q, lock=True)
                    require(protected(actual, plan) == protected(plan['before'], plan), 'Protected source changed')
                    save(args.evidence_dir / 'before.json', actual)
                    count = execute(q, plan)
                    result = verify(q, plan)
                    after = state(q)
                    require(protected(after, plan) == protected(actual, plan), 'Protected data changed')
                    save(args.evidence_dir / 'after-precommit.json', after)
                    c.commit()
                    result.update(changed=count, protected_sha256=digest(protected(actual, plan)))
                    other = connect('zsjos-mysql-1')
                    try:
                        with other.cursor() as independent:
                            independent.execute('START TRANSACTION WITH CONSISTENT SNAPSHOT, READ ONLY')
                            result['independent'] = verify(independent, plan)
                            require(protected(state(independent), plan) == protected(actual, plan),
                                    'Independent protected comparison changed')
                    finally:
                        other.rollback()
                        other.close()
                else:
                    q.execute('START TRANSACTION WITH CONSISTENT SNAPSHOT, READ ONLY')
                    result = verify(q, plan)
                    require(protected(state(q), plan) == protected(plan['before'], plan), 'Protected data changed')
                    result['protected_match'] = True
            result['plan_sha256'] = digest(plan)
            save(args.evidence_dir / ('rehearsal.json' if args.mode == 'prepare' else 'result.json'), result)
            print(encode(result))
    finally:
        c.rollback()
        c.close()


if __name__ == '__main__':
    main()
