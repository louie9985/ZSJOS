#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Real MySQL regression using connection-local temporary tables only."""
import backfill_student_info_tasks as tool


def main():
    conn, _ = tool.connect(True)
    try:
        with conn.cursor() as q:
            tool.preflight(q)
            conn.rollback()
            # Shadow application tables on this connection; never mutate their rows.
            schemas = {
                'zsjos_order': 'id BIGINT PRIMARY KEY, order_no VARCHAR(64),person_id BIGINT,status VARCHAR(32),tenant_id BIGINT DEFAULT 1,deleted BIT DEFAULT 0',
                'zsjos_order_item': 'id BIGINT PRIMARY KEY,order_id BIGINT,tenant_id BIGINT DEFAULT 1,deleted BIT DEFAULT 0',
                'zsjos_person': 'id BIGINT PRIMARY KEY,tenant_id BIGINT DEFAULT 1,deleted BIT DEFAULT 0',
                'zsjos_registration_case': 'id BIGINT PRIMARY KEY,order_id BIGINT,order_item_id BIGINT,tenant_id BIGINT DEFAULT 1,deleted BIT DEFAULT 0',
                'zsjos_student_info_form': 'id BIGINT PRIMARY KEY,order_item_id BIGINT,registration_case_id BIGINT,person_id BIGINT,tenant_id BIGINT DEFAULT 1,deleted BIT DEFAULT 0',
            }
            q.execute('SHOW CREATE TABLE zsjos_student_info_task')
            ddl = q.fetchone()['Create Table']
            q.execute(ddl.replace('CREATE TABLE', 'CREATE TEMPORARY TABLE', 1))
            for table, columns in schemas.items():
                q.execute(f'CREATE TEMPORARY TABLE {table} ({columns}) ENGINE=InnoDB')
            q.execute('INSERT INTO zsjos_person(id) VALUES(1)')
            for idx, state in enumerate(('pending_approval', 'pending_approval', 'pending_approval', 'terminated', 'superseded', 'effective', 'revision_required'), 1):
                q.execute('INSERT INTO zsjos_order(id,order_no,person_id,status) VALUES(%s,%s,1,%s)', (idx, 'FIXTURE'+str(idx), state))
                q.execute('INSERT INTO zsjos_order_item(id,order_id) VALUES(%s,%s)', (idx, idx))
            q.execute("INSERT INTO zsjos_order VALUES(8,'TENANT2',1,'pending_approval',2,0)")
            q.execute('INSERT INTO zsjos_order_item VALUES(8,8,2,0)')
            q.execute('INSERT INTO zsjos_student_info_form(id,order_item_id,person_id) VALUES(3,3,1)')
            conn.commit()
            rows, blocked = tool.plan(q, lock=True)
            assert len(rows) == 3 and not blocked
            assert rows[2]['form_id'] == 3
            tool.insert(q, rows)
            assert tool.plan(q, lock=True) == ([], [])
            conn.rollback()
            assert len(tool.plan(q)[0]) == 3, 'rollback must remove all writes'
            # Recover after a previous partially completed repair and preserve rows.
            tool.insert(q, rows[:1])
            conn.commit()
            remaining, blocked = tool.plan(q, lock=True)
            assert len(remaining) == 2 and not blocked
            tool.insert(q, remaining)
            conn.commit()
            assert tool.plan(q) == ([], [])
            q.execute('SELECT COUNT(*) n FROM zsjos_student_info_form')
            assert q.fetchone()['n'] == 1
            q.execute('SELECT form_id FROM zsjos_student_info_task WHERE order_item_id=3')
            assert q.fetchone()['form_id'] == 3
            # Logical deletion must block; never silently restore a task.
            q.execute('UPDATE zsjos_student_info_task SET deleted=1 WHERE order_item_id=1')
            assert tool.plan(q)[1][0]['reason'] == 'existing-task-binding-conflict'
            conn.rollback()
            # A legacy unbound form cannot be replaced by a newly empty task.
            q.execute("INSERT INTO zsjos_order(id,order_no,person_id,status) VALUES(9,'LEGACY',1,'pending_approval')")
            q.execute('INSERT INTO zsjos_order_item(id,order_id) VALUES(9,9)')
            q.execute('INSERT INTO zsjos_registration_case(id,order_id,order_item_id) VALUES(9,9,9)')
            q.execute('INSERT INTO zsjos_student_info_form(id,registration_case_id,person_id) VALUES(9,9,1)')
            assert tool.plan(q)[1][0]['reason'] == 'historical-form-binding-conflict'
            conn.rollback()
            assert tool.digest({}, rows, []) != tool.digest({}, rows[:1], [])
            print('PASS: MySQL initial/replay/rollback/partial recovery, tenant and terminal exclusion, existing form preservation, deleted task and legacy binding guards')
    finally:
        conn.rollback()
        conn.close()


if __name__ == '__main__':
    main()
