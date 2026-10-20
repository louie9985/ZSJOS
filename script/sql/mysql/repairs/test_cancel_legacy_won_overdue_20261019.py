#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""MySQL repair temporary tables and a retained synthetic import schema."""
import argparse
import datetime as dt
from pathlib import Path
import re
import uuid

from cancel_legacy_won_overdue_20261019 import (
    TABLE, apply_rows, connect, decode, digest, require, select,
)


def insert(q, table, row):
    q.execute(f'INSERT INTO {table} (' + ','.join(f'`{key}`' for key in row)
              + ') VALUES (' + ','.join(['%s'] * len(row)) + ')', tuple(row.values()))


def repair_checks(c, q, plan):
    table = 'verify_won_overdue_tasks'
    q.execute(f'CREATE TEMPORARY TABLE {table} LIKE {TABLE}')
    for row in plan['before']:
        insert(q, table, row)
    sentinel = dict(plan['before'][0], id=max(r['id'] for r in plan['before']) + 1,
                    idempotency_key='verification-manual-follow-up', creator='verification')
    insert(q, table, sentinel)
    c.commit()
    require(apply_rows(q, plan, table) == 116, 'Initial application')
    require(apply_rows(q, plan, table) == 0, 'Repeat application')
    require(select(q, f'SELECT * FROM {table} WHERE id=%s', (sentinel['id'],))[0] == sentinel,
            'Non-target task changed')
    c.rollback()
    try:
        apply_rows(q, plan, table, fail_after=3)
        raise AssertionError('Injected failure did not fire')
    except RuntimeError as error:
        require(str(error) == 'Injected failure', 'Unexpected failure')
        c.rollback()
    require(select(q, f'SELECT * FROM {table} WHERE id<>%s ORDER BY id', (sentinel['id'],))
            == plan['before'], 'Failed transaction left partial rows')
    partial = dict(plan, before=plan['before'][:3], after=plan['after'][:3])
    require(apply_rows(q, partial, table) == 3, 'Partial fixture')
    c.commit()
    require(apply_rows(q, plan, table) == 113, 'Partial recovery')
    c.commit()
    require(apply_rows(q, plan, table, restore=True) == 116, 'Compensating restore')
    require(apply_rows(q, plan, table, restore=True) == 0, 'Repeat restore')
    c.commit()
    q.execute(f"UPDATE {table} SET status='completed' WHERE id=%s", (plan['before'][3]['id'],))
    c.commit()
    try:
        apply_rows(q, plan, table)
        raise AssertionError('Stale state accepted')
    except RuntimeError as error:
        require('Task changed' in str(error), 'Unexpected stale failure')
        c.rollback()
    require(select(q, f'SELECT status FROM {table} WHERE id=%s', (plan['before'][0]['id'],))[0]['status']
            == 'pending', 'Stale refusal failed to rollback earlier updates')
    print('PASS repair: initial, repeat, unrelated preservation, failure rollback, partial recovery, restore, stale refusal')


def import_checks(c, q, plan):
    # MySQL cannot reference one TEMPORARY table through multiple aliases/UNIONs.
    # The unmodified import queries need a uniquely owned synthetic schema.
    schema = 'verify_legacy_won_' + uuid.uuid4().hex[:12]
    q.execute(f'CREATE DATABASE {schema} CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci')
    q.execute(f'USE {schema}')
    print('Retained synthetic import schema:', schema)
    root = Path(__file__).resolve().parents[4]
    path = root / 'script/sql/merge/backfill-legacy-followup-reminders.sql'
    sql = path.read_text(encoding='utf-8')
    sql = re.sub(r'--[^\n]*', '', sql)
    sql = sql.replace('oldcrm_scratch.', 'verify_imp_old_').replace('zsjos.', 'verify_imp_')
    sql = re.sub(r'\bsystem_users\b', 'verify_imp_system_users', sql)
    statements = [s.strip() for s in sql.split(';') if s.strip()]
    # Only table/schema identifiers differ from the delivered SQL.
    definitions = {
        'old_followup_reminder': 'id BIGINT,followup_id BIGINT,lead_id BIGINT,sales_id BIGINT,remind_at DATETIME,overdue_at DATETIME,created_at DATETIME,reminder_type VARCHAR(40),status VARCHAR(40),handled_at DATETIME,closed_at DATETIME',
        'old_leads_lead': 'id BIGINT,lead_no VARCHAR(80)',
        'old_employees_employee_profile': 'id BIGINT,phone VARCHAR(80)',
        'system_users': 'id BIGINT,mobile VARCHAR(80),username VARCHAR(80)',
        'zsjos_lead': 'id BIGINT,lead_no VARCHAR(80),status VARCHAR(40),assignment_status VARCHAR(40),current_assignment_history_id BIGINT,owner_user_id BIGINT,follow_up_count INT,last_follow_up_at DATETIME,last_follow_up_record_id BIGINT,current_assignment_first_follow_up_at DATETIME',
        'zsjos_lead_follow_up_record': 'id BIGINT,lead_id BIGINT,idempotency_key VARCHAR(100),occurred_at DATETIME',
    }
    for name, columns in definitions.items():
        q.execute(f'CREATE TABLE verify_imp_{name} ({columns}) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4')
    q.execute(f'CREATE TABLE verify_imp_{TABLE} LIKE zsjos.{TABLE}')
    insert(q, 'verify_imp_old_employees_employee_profile', {'id': 1, 'phone': 'synthetic-link'})
    insert(q, 'verify_imp_system_users', {'id': 1, 'mobile': 'synthetic-link', 'username': 'synthetic-user'})
    now = select(q, 'SELECT NOW() now')[0]['now']
    for i, status in enumerate(('won', 'won', 'valid', 'invalid', 'submitted', 'valid', 'won'), 1):
        lead_no = f'LD-VERIFY-{i}'
        insert(q, 'verify_imp_zsjos_lead', dict(id=i, lead_no=lead_no, status=status,
            assignment_status='owned', current_assignment_history_id=1, owner_user_id=1, follow_up_count=0))
        insert(q, 'verify_imp_old_leads_lead', dict(id=i, lead_no=lead_no))
        insert(q, 'verify_imp_zsjos_lead_follow_up_record', dict(id=i, lead_id=i,
            idempotency_key=f'legacy-followup-{i}', occurred_at=now - dt.timedelta(days=5)))
        due = now + dt.timedelta(days=5) if i == 2 else now - dt.timedelta(days=1)
        insert(q, 'verify_imp_old_followup_reminder', dict(id=i, followup_id=i, lead_id=i, sales_id=1,
            remind_at=due, overdue_at=due, created_at=now - dt.timedelta(days=5),
            reminder_type='next_follow', status='pending'))
    insert(q, 'verify_imp_zsjos_lead_follow_up_record', dict(id=100, lead_id=6,
        idempotency_key='manual-follow-up', occurred_at=now))
    manual = dict(plan['before'][0], id=90000000, biz_id=7, creator='verification',
                  idempotency_key='manual-won-follow-up', title_snapshot='验证跟进提醒')
    insert(q, 'verify_imp_zsjos_business_task', manual)
    c.commit()

    def run(until_insert=False):
        for statement in statements:
            q.execute(statement)
            if q.description:
                q.fetchall()
            if until_insert and statement.startswith('INSERT IGNORE'):
                break

    def check():
        tasks = select(q, 'SELECT * FROM verify_imp_zsjos_business_task ORDER BY biz_id')
        require([t['biz_id'] for t in tasks] == [2, 3, 5, 7], 'Import eligibility incorrect')
        require(tasks[-1] == manual, 'Existing manual won reminder changed')
        require(all(t['title_snapshot'].startswith('跟进提醒：') for t in tasks[:-1]), 'UTF-8 label changed')
        return digest(tasks)

    # Stop after task insertion to emulate a partial old run, then recover using full SQL.
    run(until_insert=True); c.commit()
    first = check()
    run(); c.commit(); require(check() == first, 'Import recovery changed task images')
    run(); c.commit(); require(check() == first, 'Import repeat changed task images')
    require(select(q, 'SELECT HEX(title_snapshot) h FROM verify_imp_zsjos_business_task WHERE biz_id=2')[0]['h']
            == '跟进提醒：LD-VERIFY-2'.encode('utf-8').hex().upper(), 'Import UTF-8 HEX mismatch')
    print('PASS complete import SQL: excludes won-overdue, retains won-future/valid/submitted/manual, partial recovery, repeat, UTF-8 HEX')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--plan', type=Path, required=True)
    args = parser.parse_args()
    plan = decode(args.plan.read_text(encoding='utf-8'))
    c = connect('zsjos-mysql-1')
    try:
        with c.cursor() as q:
            q.execute('SET NAMES utf8mb4')
            repair_checks(c, q, plan)
            import_checks(c, q, plan)
    finally:
        c.rollback(); c.close()


if __name__ == '__main__':
    main()
