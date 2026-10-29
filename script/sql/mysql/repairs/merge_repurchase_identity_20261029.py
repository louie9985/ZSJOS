#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Exact test-only identity repair; application requires the documented approval."""
import argparse
import copy
import os
from pathlib import Path

from close_legacy_registration_20261028 import encoded, read, save, require, rows
from transfer_student_20261015 import connect

MARKER = 'repurchase-identity-20261029'
OLD, KEEP = 5311, 6005
TARGETS = {'zsjos_lead': 5307, 'zsjos_opportunity': 7331, 'zsjos_order': 635}


def inventory(q):
    return [(r['TABLE_NAME'], r['COLUMN_NAME']) for r in rows(q, """
        SELECT TABLE_NAME,COLUMN_NAME FROM information_schema.columns
        WHERE table_schema=DATABASE() AND table_name LIKE 'zsjos%'
        AND (column_name LIKE '%person_id%' OR column_name IN ('student_id','customer_id'))
        ORDER BY table_name,column_name""") if '_bak_' not in r['TABLE_NAME']]


def capture(q, lock=False):
    suffix = ' FOR UPDATE' if lock else ''
    result = {'zsjos_person': rows(q, 'SELECT * FROM zsjos_person WHERE tenant_id=1 '
                                 'AND id IN (5311,6005) ORDER BY id' + suffix)}
    predicates = {}
    for table, column in inventory(q):
        predicates.setdefault(table, []).append(f'`{column}` IN (5311,6005)')
    for table, terms in predicates.items():
        result[table] = rows(q, f'SELECT * FROM `{table}` WHERE tenant_id=1 AND ('
                             + ' OR '.join(terms) + ') ORDER BY id' + suffix)
    return result


def validate(q, snapshot):
    people = {r['id']: r for r in snapshot['zsjos_person']}
    require(set(people) == {OLD, KEEP}, 'Person prerequisite missing')
    old, keep = people[OLD], people[KEEP]
    require(old['name'] == keep['name'] and old['mobile'] == keep['mobile']
            and bool(keep['mobile']) and not old['wechat_id'] and not keep['wechat_id'],
            'Identity equality prerequisite changed')
    require(keep['deleted'] == b'\0' and keep['identity_status'] == 'student', 'Target changed')
    done = old['deleted'] == b'\1' and old['updater'] == MARKER
    require(done or old['deleted'] == b'\0', 'Unexpected source deletion')
    for table, ident in TARGETS.items():
        target = [r for r in snapshot[table] if r['id'] == ident]
        require(len(target) == 1 and target[0]['deleted'] == b'\0'
                and target[0]['person_id'] == (KEEP if done else OLD), 'Business target changed')
        require(target[0]['status'] == ('effective' if table == 'zsjos_order' else 'won'),
                'Business state changed')
        if table != 'zsjos_order':
            require(len(snapshot[table]) == 1, 'Target already has another Lead/Opportunity')
    claim = snapshot['zsjos_person_contact_claim']
    require(len(claim) == 1 and claim[0]['person_id'] == KEEP and claim[0]['deleted'] == b'\0'
            and claim[0]['contact_value'] == keep['mobile'] and not claim[0]['reservation_key'],
            'Contact claim changed')
    for table, column in inventory(q):
        old_refs = [r for r in snapshot[table] if r[column] == OLD]
        require(not old_refs or (not done and table in TARGETS
                and column in ('person_id', 'active_person_id')
                and [r['id'] for r in old_refs] == [TARGETS[table]]), 'Additional source references')
    for table, typ, key in [('zsjos_business_task','biz_type','biz_id'),
                          ('zsjos_standard_form','biz_type','biz_id'),
                          ('zsjos_work_order','business_type','business_id'),
                          ('zsjos_payment_gateway_event','business_type','business_id')]:
        found = rows(q, f'SELECT `{typ}` kind FROM `{table}` WHERE tenant_id=1 AND `{key}`=%s', (str(OLD),))
        require(all(r['kind'] == 'lead' and table == 'zsjos_business_task' for r in found),
                'Unexpected polymorphic reference requires review')
    require(not rows(q, "SELECT id FROM zsjos_order WHERE tenant_id=1 AND person_id IN (5311,6005) "
                     "AND deleted=0 AND order_type='repurchase' AND status IN ('pending_approval','revision_required')"),
            'Pending repurchase already exists')
    return done


def mutate(q, prefix='', fail_after=None):
    count = 0
    for table, ident in TARGETS.items():
        q.execute(f'UPDATE `{prefix}{table}` SET person_id=%s,version=version+1,updater=%s,update_time=NOW() '
                  'WHERE tenant_id=1 AND id=%s AND person_id=%s AND deleted=0', (KEEP, MARKER, ident, OLD))
        require(q.rowcount == 1, 'Expected exactly one business update')
        count += 1
        if fail_after == count: raise RuntimeError('Injected failure')
    q.execute(f'UPDATE `{prefix}zsjos_person` SET deleted=1,version=version+1,updater=%s,update_time=NOW() '
              'WHERE tenant_id=1 AND id=%s AND deleted=0', (MARKER, OLD))
    require(q.rowcount == 1, 'Expected exactly one logical deletion')
    if fail_after == 4: raise RuntimeError('Injected failure')


def compare(before, after):
    require(before.keys() == after.keys(), 'Table inventory changed')
    for table, original in before.items():
        actual = {r['id']: r for r in after[table]}
        require(set(actual) == {r['id'] for r in original}, 'Related row set changed')
        for row in original:
            want, got = copy.deepcopy(row), actual[row['id']]
            changed = (table in TARGETS and row['id'] == TARGETS[table]) or (table == 'zsjos_person' and row['id'] == OLD)
            if changed:
                if table == 'zsjos_person': want['deleted'] = b'\1'
                else:
                    want['person_id'] = KEEP
                    if 'active_person_id' in want and want['active_person_id'] == OLD:
                        want['active_person_id'] = KEEP
                want.update(version=row['version'] + 1, updater=MARKER, update_time=got['update_time'])
            require(want == got, 'Unexpected field change: ' + table)


def rehearse(c, snapshot):
    tables = ['zsjos_person', *TARGETS]
    with c.cursor() as q:
        for table in tables:
            q.execute(f'CREATE TEMPORARY TABLE `rr_{table}` LIKE `{table}`')
            cols = [r['Field'] for r in rows(q, f'SHOW FULL COLUMNS FROM `{table}`')
                    if not any(kind in r['Extra'] for kind in ('VIRTUAL GENERATED', 'STORED GENERATED'))]
            col_sql = ','.join('`' + k + '`' for k in cols)
            q.executemany(f'INSERT INTO `rr_{table}` ({col_sql}) VALUES ({",".join(["%s"]*len(cols))})',
                          [tuple(r[k] for k in cols) for r in snapshot[table]])
    c.commit()  # Only session-local temporary tables have been written.
    before = {t: snapshot[t] for t in tables}
    def state(q): return {t: rows(q, f'SELECT * FROM `rr_{t}` ORDER BY id') for t in tables}
    with c.cursor() as q:
        require(state(q) == before, 'Temporary fixture differs from source')
    c.rollback()
    for fail in [1, 2, 3, 4, None]:
        with c.cursor() as q:
            try:
                mutate(q, 'rr_', fail)
                compare(before, state(q))
            except RuntimeError as e:
                if str(e) != 'Injected failure': raise
            finally: c.rollback()
            require(state(q) == before, 'Rollback failed')
        c.rollback()
    with c.cursor() as q:
        mutate(q, 'rr_'); compare(before, state(q))
    c.commit()
    with c.cursor() as q:
        require(rows(q, 'SELECT id FROM rr_zsjos_person WHERE deleted=0') == [{'id': KEEP}], 'Ambiguous identity remains')
        for table in TARGETS:
            require(not rows(q, f'SELECT id FROM `rr_{table}` WHERE person_id=%s', (OLD,)), 'Reference remains')
        # Exercise the same completion guard used by a repeated real invocation.
        completed = state(q)
        require(validate(q, dict(snapshot, **completed)), 'Repeat guard failed')
        require(state(q) == completed, 'Repeat guard changed rows')
    c.rollback()


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('mode', choices=['preview', 'rehearse', 'apply'])
    p.add_argument('--evidence-dir', type=Path, required=True)
    p.add_argument('--approved-preview', type=Path)
    a = p.parse_args()
    env = os.environ.get('ZSJOS_AGENT_ENV')
    if env is None:
        env = Path('/etc/zsjos/agent-environment').read_text().strip()
    require(env == 'test', 'Only test environment supported')
    require(a.mode != 'apply' or a.approved_preview is not None, 'Approved preview required')
    os.umask(0o077)
    a.evidence_dir.mkdir(parents=True, exist_ok=False, mode=0o700)
    c = connect('zsjos-mysql-1')
    try:
        with c.cursor() as q:
            q.execute('SET NAMES utf8mb4');q.execute("SET time_zone='+08:00'")
            q.execute('SET SESSION innodb_lock_wait_timeout=10')
            if a.mode != 'apply': q.execute('SET TRANSACTION READ ONLY')
            q.execute('START TRANSACTION WITH CONSISTENT SNAPSHOT')
            before = capture(q, lock=a.mode == 'apply')
            done = validate(q, before)
            save(a.evidence_dir / 'before.json', before)
            if a.mode == 'apply' and not done:
                require(before == read(a.approved_preview), 'Preview changed; review a new preview')
                mutate(q)
                after = capture(q)
                compare(before, after);require(validate(q, after), 'Postconditions failed')
                save(a.evidence_dir / 'after.json', after)
                c.commit()
                verified = capture(q)
                require(verified == after, 'Independent transaction verification differs')
                c.rollback()
            else: c.rollback()
        if a.mode == 'rehearse' and not done: rehearse(c, before)
        save(a.evidence_dir / 'result.json', dict(mode=a.mode,already_applied=done,
             application_rows_changed=4 if a.mode == 'apply' and not done else 0))
        print(encoded(dict(mode=a.mode,already_applied=done,verified=True)))
    finally:
        c.rollback();c.close()


if __name__ == '__main__': main()
