#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Execute the reviewed local batch and retain scoped before/after audit evidence."""
import argparse
import hashlib
import json
from datetime import datetime
from pathlib import Path

from import_students import CATALOG_TABLES, ROOT, TABLES, apply, check, connect, digest, fetch, packed, prepare, read_source, save_private, summary, verify

PROTECTED = ('system_notify_message', 'system_role_menu', 'zsjos_business_task', 'zsjos_lead',
             'zsjos_media_account', 'zsjos_order', 'zsjos_order_item', 'zsjos_partner',
             'zsjos_partner_account', 'zsjos_payment_order', 'zsjos_registration_case', 'zsjos_student_entry_command')
APPEND_ONLY = ('zsjos_delivery_class', 'zsjos_service_relation', 'zsjos_collaboration_group', 'zsjos_student_contact_record', 'zsjos_business_event')


def read_all(conn, table):
    with conn.cursor() as q:
        return fetch(q, f'SELECT * FROM `{table}`')


def signature(rows):
    return digest(sorted((packed(r) for r in rows)))


def execute(source_path, directory, actor, plan_path, sha256, confirmations):
    source = read_source(source_path, confirmations)
    check(hashlib.sha256(plan_path.read_bytes()).hexdigest() == sha256, 'Exact reviewed plan SHA256 required')
    plan = json.loads(plan_path.read_text(encoding='utf-8'))
    check(not directory.resolve().is_relative_to(ROOT), 'Private evidence must be outside repository')
    directory.mkdir(parents=True, exist_ok=False)
    conn = connect('ruoyi-vue-pro')
    try:
        check(plan['actorUsername'] == actor and plan['sourceSha256'] == source['sourceSha256'] and
              plan['target']['db'] == 'ruoyi-vue-pro', 'Reviewed source, actor or target changed')
        check(digest(prepare(conn, source, actor)) == digest(plan), 'Preflight changed; generate a new reviewed plan')
        save_private(directory / 'plan.json', plan)
        protected = {t: signature(read_all(conn, t)) for t in (*CATALOG_TABLES, *PROTECTED) if t != 'zsjos_delivery_class'}
        old = {t: {r['id']: digest(r) for r in read_all(conn, t)} for t in APPEND_ONLY}
        people = {r['id']: r for r in read_all(conn, 'zsjos_person')}
        claims = {r['id']: r for r in read_all(conn, 'zsjos_person_contact_claim')}
        counters = read_all(conn, 'zsjos_person_no_daily_counter')
        # Existing people contain private data; keep only the plan's necessary original states
        # plus row digests here. The source and database remain available for reviewed recovery.
        save_private(directory / 'before.json', dict(protected=protected, appendOnlyRows=old,
                     people={k: digest(v) for k, v in people.items()}, claims={k: digest(v) for k, v in claims.items()}, counters=counters))
        ids = apply(conn, plan, source)
        check(len(ids) == len(plan['ready']) and verify(conn, ids) == len(ids), 'Committed service verification failed')
        check(all(signature(read_all(conn, t)) == value for t, value in protected.items()), 'Unrelated table changed during import; inspect evidence')
        for t, rows in old.items():
            current = {r['id']: digest(r) for r in read_all(conn, t) if r['id'] in rows}
            check(current == rows, 'Existing business history changed')
        after_people = read_all(conn, 'zsjos_person')
        changed_people = 0
        reused = {r['personId'] for r in plan['ready'] if r['personId'] is not None}
        for r in after_people:
            if r['id'] not in people:
                continue
            original = people[r['id']]
            if digest(original) != digest(r):
                check(r['id'] in reused and original['identity_status'] != 'student' and r['identity_status'] == 'student'
                      and r['version'] == original['version'] + 1, 'Unexpected existing identity change')
                check(all(r[k] == v for k, v in original.items() if k not in {'identity_status', 'version', 'updater', 'update_time'}), 'Historical person fields changed')
                changed_people += 1
        for r in read_all(conn, 'zsjos_person_contact_claim'):
            if r['id'] in claims:
                old_claim = claims[r['id']]
                check(all(r[k] == v for k, v in old_claim.items() if k not in {'person_id', 'reservation_key', 'update_time'}), 'Unexpected contact claim change')
        final = prepare(conn, source, actor)
        check(len(final['ready']) == 0 and len(final['replay']) == len(plan['replay']) + len(ids), 'Final source partition failed')
        stable = {t: signature(read_all(conn, t)) for t in TABLES}
        check(apply(conn, plan, source) == [], 'Local replay created new services')
        check(all(signature(read_all(conn, t)) == value for t, value in stable.items()), 'Local replay changed rows')
        result = dict(database='ruoyi-vue-pro', imported=len(ids), newPeople=len(after_people) - len(people),
                      reusedPeople=len(reused), promotedPeople=changed_people, serviceIds=ids,
                      completedAt=datetime.now().isoformat(), sourceSha256=source['sourceSha256'], **summary(plan),
                      verified=['old class/service/group/contact/event rows unchanged', 'original identity fields preserved',
                                'catalog/roles/orders/leads/payments/registration/accounts/tasks/notifications unchanged',
                                f'{len(ids)} accepted supervision teaching-only services', f'{2 * len(ids)} taskless history records',
                                f'{len(plan["classCreates"])} new class/planner pairs', 'UTF-8 HEX', 'exact-plan replay zero changes'])
        save_private(directory / 'result.json', result)
        print(packed({k: v for k, v in result.items() if k != 'serviceIds'}))
    finally:
        conn.rollback()
        conn.close()


if __name__ == '__main__':
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--source', type=Path, required=True)
    p.add_argument('--identity-confirmations', type=Path, required=True)
    p.add_argument('--evidence-dir', type=Path, required=True)
    p.add_argument('--actor-username', required=True)
    p.add_argument('--reviewed-plan', type=Path, required=True)
    p.add_argument('--sha256', required=True)
    a = p.parse_args()
    execute(a.source, a.evidence_dir, a.actor_username, a.reviewed_plan, a.sha256, a.identity_confirmations)
