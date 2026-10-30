#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""One-time, explicitly authorized department-based repair; see operation record.

Only five latest recycle snapshots in test tenant 1. No lifecycle transitions.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path

from transfer_student_20261015 import connect, encoded, decoded, require

MARK = 'recycled-identity-20261030'
# Frozen technical keys prevent a repeated command from targeting a later cycle.
TARGETS = {
    'LD202606180010': (243, 8164, 294),
    'LD202606250017': (378, 8163, 294),
    'LD202606260025': (430, 8160, 294),
    'LD202606260047': (452, 8159, 311),
    'LD202606280011': (511, 8158, 309),
}
TABLES = dict(lead='zsjos_lead', history='zsjos_lead_assignment_history',
              user='system_users', dept='system_dept')
IDS = ','.join(str(t[0]) for t in TARGETS.values())
OWNERS = ','.join(str(t) for t in sorted({v[2] for v in TARGETS.values()}))
SCOPES = dict(lead=f'tenant_id=1 AND id IN ({IDS})',
              history=f'tenant_id=1 AND lead_id IN ({IDS})',
              user=f'tenant_id=1 AND id IN ({OWNERS})',
              dept='tenant_id=1 AND id IN (1021,1020,1001)')


def rows(q, sql, args=()):
    q.execute(sql, args or None)
    return list(q.fetchall())


def digest(value):
    return hashlib.sha256(json.dumps(value, ensure_ascii=False, sort_keys=True,
                                    default=str).encode('utf-8')).hexdigest()


def active(row):
    return row['deleted'] in (0, False, b'\0')


def capture(q, tables, lock=False):
    suffix = ' FOR UPDATE' if lock else ''
    # Lead/customer fields are only hashed in memory, never emitted or backed up.
    leads = rows(q, f"SELECT * FROM {tables['lead']} WHERE {SCOPES['lead']} ORDER BY id" + suffix)
    history = rows(q, f"SELECT * FROM {tables['history']} WHERE {SCOPES['history']} ORDER BY id" + suffix)
    users = rows(q, f"SELECT id,dept_id,deleted FROM {tables['user']} WHERE {SCOPES['user']} ORDER BY id" + suffix)
    depts = rows(q, f"SELECT id,parent_id,name,HEX(name) name_hex,deleted FROM {tables['dept']} "
                 f"WHERE {SCOPES['dept']} ORDER BY id" + suffix)
    return leads, dict(lead_hashes={r['lead_no']: digest(r) for r in leads},
                       history=history, users=users, departments=depts)


def validate(leads, state):
    require(len(leads) == 5 and {r['lead_no'] for r in leads} == set(TARGETS), 'Target Leads differ')
    users = {u['id']: u for u in state['users']}
    depts = {d['id']: d for d in state['departments']}
    # This is the frozen, user-reviewed sales branch, not a general label heuristic.
    require(1021 in depts and 1020 in depts, 'Reviewed departments missing')
    require(depts[1021]['name'] == '销售转化一部' and depts[1021]['parent_id'] == 1020
            and depts[1020]['name'] == '销售转化中心' and depts[1020]['parent_id'] == 1001,
            'Reviewed department hierarchy changed')
    for d in depts.values():
        require(active(d) and d['name_hex'] == d['name'].encode('utf-8').hex().upper(),
                'Department deletion or UTF-8 mismatch')
    latest = []
    for lead in leads:
        lead_id, history_id, owner = TARGETS[lead['lead_no']]
        require(active(lead) and lead['id'] == lead_id and lead['status'] == 'submitted'
                and lead['assignment_status'] == 'recycle_pending' and lead['owner_user_id'] is None
                and lead['owner_identity'] is None and lead['recycle_source_owner_user_id'] == owner
                and lead['version'] == 0, 'Lead changed since reviewed scan')
        require(owner in users and active(users[owner]) and users[owner]['dept_id'] == 1021,
                'Original owner account department changed')
        history = sorted((h for h in state['history'] if h['lead_id'] == lead_id and active(h)),
                         key=lambda h: (h['occurred_at'], h['id']), reverse=True)
        require(bool(history), 'Missing history')
        h = history[0]
        require(h['id'] == history_id and h['action_type'] == 'recycle'
                and h['from_owner_user_id'] == owner and h['to_owner_user_id'] is None,
                'Latest recycle cycle changed')
        require(h['owner_identity_snapshot'] is None or
                (h['owner_identity_snapshot'] == 'sales' and h['updater'] == MARK),
                'Existing identity is outside this repair')
        latest.append(h)
    return latest


def repair(q, tables, fail_after=0, before_write=None):
    leads, before = capture(q, tables, lock=True)
    latest = validate(leads, before)
    pending = [h for h in latest if h['owner_identity_snapshot'] is None]
    if before_write:
        before_write(before)
    now = rows(q, 'SELECT NOW() value')[0]['value']
    for index, h in enumerate(pending, 1):
        q.execute(f"UPDATE {tables['history']} SET owner_identity_snapshot='sales',updater=%s,update_time=%s "
                  "WHERE tenant_id=1 AND id=%s AND lead_id=%s AND deleted=0 "
                  "AND action_type='recycle' AND from_owner_user_id=%s AND to_owner_user_id IS NULL "
                  "AND owner_identity_snapshot IS NULL", (MARK, now, h['id'], h['lead_id'], h['from_owner_user_id']))
        require(q.rowcount == 1, 'Concurrent history change')
        if index == fail_after:
            raise RuntimeError('Injected failure')
    after_leads, after = capture(q, tables)
    expected = decoded(encoded(before))
    changed_ids = {h['id'] for h in pending}
    for h in expected['history']:
        if h['id'] in changed_ids:
            h.update(owner_identity_snapshot='sales', updater=MARK, update_time=now)
    require(after == expected, 'Protected fields or rows changed')
    require(all(h['owner_identity_snapshot'] == 'sales' for h in validate(after_leads, after)),
            'Identity postcondition failed')
    # Original Chinese reasons must remain byte-for-byte intact.
    for h in latest:
        value = rows(q, f"SELECT HEX(reason) value FROM {tables['history']} WHERE id=%s", (h['id'],))[0]['value']
        require(value == h['reason'].encode('utf-8').hex().upper(), 'Reason UTF-8 mismatch')
    return len(pending), before, after


def save(path, data):
    fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(fd, 'w', encoding='utf-8') as stream:
        stream.write(encoded(data)); stream.flush(); os.fsync(stream.fileno())
    fd = os.open(path.parent, os.O_RDONLY)
    try:
        os.fsync(fd)
    finally:
        os.close(fd)


def selftest(c):
    test = {key: 'verify_recycled_identity_' + key for key in TABLES}
    with c.cursor() as q:
        for key, table in test.items():
            q.execute(f'CREATE TEMPORARY TABLE {table} LIKE {TABLES[key]}')
            columns = rows(q, 'SELECT column_name FROM information_schema.columns '
                           'WHERE table_schema=DATABASE() AND table_name=%s '
                           "AND generation_expression='' ORDER BY ordinal_position", (TABLES[key],))
            names = ','.join('`' + r['COLUMN_NAME'] + '`' for r in columns)
            q.execute(f'INSERT INTO {table} ({names}) SELECT {names} FROM {TABLES[key]} WHERE {SCOPES[key]}')
        c.commit()
        baseline = capture(q, test)[1]
        require(all(h['owner_identity_snapshot'] is None for h in validate(*capture(q, test))),
                'Selftest requires the documented pre-repair state')
        c.rollback()
        count, _, _ = repair(q, test)
        require(count == 5 and repair(q, test)[0] == 0, 'First/repeat result differs')
        c.rollback()
        require(capture(q, test)[1] == baseline, 'Successful preview rollback differs')
        for step in (1, 3, 5):
            try:
                repair(q, test, fail_after=step)
            except RuntimeError as ex:
                require(str(ex) == 'Injected failure', 'Unexpected injected-test failure')
            else:
                raise RuntimeError('Failure injection was not reached')
            c.rollback()
            require(capture(q, test)[1] == baseline, 'Injected rollback changed data')
        # A previously completed subset is accepted only with this repair's provenance.
        q.execute(f"UPDATE {test['history']} SET owner_identity_snapshot='sales',updater=%s WHERE id=8164", (MARK,))
        require(repair(q, test)[0] == 4 and repair(q, test)[0] == 0, 'Partial recovery failed')
        c.rollback()
        invalid = [
            f"UPDATE {test['history']} SET owner_identity_snapshot='education' WHERE id=8164",
            f"UPDATE {test['history']} SET action_type='transfer' WHERE id=8164",
            f"UPDATE {test['user']} SET dept_id=1001 WHERE id=294",
            f"UPDATE {test['lead']} SET version=version+1 WHERE id=243",
        ]
        for sql in invalid:
            q.execute(sql)
            try:
                repair(q, test)
            except RuntimeError:
                pass
            else:
                raise RuntimeError('Invalid evidence was accepted')
            c.rollback()
            require(capture(q, test)[1] == baseline, 'Rejection rollback differs')
        require(repair(q, test)[0] == 5, 'Recovery after failure failed')
        c.rollback()
    print(json.dumps(dict(result='PASS',engine='MySQL',first=5,repeat=0,
                         rollbackSteps=[1,3,5],partialRecovery=4,rejections=4)))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('mode', choices=['test', 'apply', 'verify'])
    parser.add_argument('--evidence-dir', type=Path)
    args = parser.parse_args()
    marker = Path('/etc/zsjos/agent-environment')
    environment = os.environ.get('ZSJOS_AGENT_ENV')
    if environment is None:
        environment = marker.read_text().strip() if marker.exists() else 'local'
    require(environment == 'test', 'Only explicitly reviewed test environment is supported')
    if args.mode != 'test':
        require(args.evidence_dir is not None, 'Private evidence directory required')
        directory = args.evidence_dir.resolve()
        require(directory.is_relative_to(Path('/opt/zsjos-runtime/backups')), 'Unexpected evidence location')
        if args.mode == 'apply':
            directory.mkdir(mode=0o700, parents=True, exist_ok=False)
        require(directory.stat().st_mode & 0o077 == 0, 'Evidence directory must be private')
    c = connect('zsjos-mysql-1')
    try:
        with c.cursor() as q:
            q.execute('SET NAMES utf8mb4')
            q.execute("SET time_zone='+08:00'")
            q.execute('SET SESSION innodb_lock_wait_timeout=10')
            if args.mode == 'test':
                selftest(c)
                return
            if args.mode == 'verify':
                q.execute('SET SESSION TRANSACTION READ ONLY')
                q.execute('START TRANSACTION WITH CONSISTENT SNAPSHOT')
                leads, actual = capture(q, TABLES)
                expected = decoded((directory / 'after.json').read_text(encoding='utf-8'))
                require(actual == expected, 'Committed state differs from recorded post-image')
                require(all(h['owner_identity_snapshot'] == 'sales' for h in validate(leads, actual)),
                        'Identity verification failed')
                print(json.dumps(dict(result='PASS',identities={n:'sales' for n in TARGETS},
                                      protectedLeadHashes='unchanged'), ensure_ascii=False))
                return
            count, before, after = repair(q, TABLES, before_write=lambda state: save(directory / 'before.json', state))
            save(directory / 'after.json', after)
            save(directory / 'manifest.json', dict(environment=environment,database='zsjos',tenant=1,
                 marker=MARK,leadNos=list(TARGETS),historyRowsChanged=count,
                 authorization='User explicitly selected original-owner current department as one-time evidence',
                 beforeSha256=digest(before),afterSha256=digest(after)))
            c.commit()
            print(json.dumps(dict(result='COMMITTED',historyRowsChanged=count,leadRowsChanged=0)))
    finally:
        c.rollback(); c.close()


if __name__ == '__main__':
    main()
