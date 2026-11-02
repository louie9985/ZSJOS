#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""One-off LOCAL recovery of unused rows from the 2026-11-02 overbroad run."""
import argparse
import datetime
import json
from pathlib import Path
import backfill_student_info_tasks as tool


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apply', action='store_true')
    args = parser.parse_args()
    conn, target = tool.connect(True)
    try:
        with conn.cursor() as q:
            tool.preflight(q)
            conn.rollback()
            q.execute('SET SESSION innodb_lock_wait_timeout=15')
            q.execute('START TRANSACTION')
            q.execute("SELECT id,status FROM zsjos_order WHERE tenant_id=1 AND id IN (SELECT order_id FROM zsjos_student_info_task WHERE tenant_id=1 AND creator=%s AND create_time BETWEEN '2026-11-02 06:47:59' AND '2026-11-02 06:48:00') ORDER BY id FOR UPDATE", (tool.MARKER,))
            states = {r['id']: r['status'] for r in q.fetchall()}
            q.execute("SELECT * FROM zsjos_student_info_task WHERE tenant_id=1 AND creator=%s AND create_time BETWEEN '2026-11-02 06:47:59' AND '2026-11-02 06:48:00' ORDER BY id FOR UPDATE", (tool.MARKER,))
            remove, preserve = [], []
            for row in q.fetchall():
                if states.get(row['order_id']) == 'pending_approval':
                    continue
                unused = (row['order_id'] in states and row['version'] == 0
                    and row['deleted'] in (0, b'\x00') and row['updater'] == tool.MARKER
                    and row['update_time'] == row['create_time']
                    and all(row[k] is None for k in ('form_id', 'source_form_id', 'source_revision',
                        'operator_user_id', 'last_command_key', 'last_command_fingerprint')))
                q.execute('SELECT id FROM zsjos_student_info_form WHERE tenant_id=1 AND (order_item_id=%s OR registration_case_id IN (SELECT id FROM zsjos_registration_case WHERE tenant_id=1 AND order_id=%s AND order_item_id=%s)) FOR UPDATE',
                    (row['order_item_id'], row['order_id'], row['order_item_id']))
                if q.fetchall():
                    unused = False
                (remove if unused else preserve).append(row)
            print(json.dumps(dict(remove=len(remove), preserve_used=len(preserve))))
            if args.apply and remove:
                backup = tool.ROOT / 'artifacts/student-info-backfill' / ('overfill-backup-' + datetime.datetime.now().strftime('%Y%m%d%H%M%S%f') + '.json')
                backup.parent.mkdir(parents=True, exist_ok=True)
                def encode(value):
                    if isinstance(value, bytes):
                        return {'hex': value.hex()}
                    return str(value)
                with backup.open('x', encoding='utf-8') as f:
                    json.dump(dict(target=target, rows=remove), f, default=encode, ensure_ascii=False, indent=2)
                    f.flush()
                    import os
                    os.fsync(f.fileno())
                for row in remove:
                    q.execute('DELETE FROM zsjos_student_info_task WHERE tenant_id=1 AND id=%s AND creator=%s', (row['id'], tool.MARKER))
                    if q.rowcount != 1:
                        raise ValueError('Delete postcondition failed')
                q.execute("SELECT COUNT(*) n FROM zsjos_student_info_task WHERE tenant_id=1 AND creator=%s AND create_time BETWEEN '2026-11-02 06:47:59' AND '2026-11-02 06:48:00' AND order_id IN (SELECT id FROM zsjos_order WHERE tenant_id=1 AND status<>'pending_approval')", (tool.MARKER,))
                if q.fetchone()['n'] != len(preserve):
                    raise ValueError('Recovery postcondition failed')
                conn.commit()
                print(json.dumps(dict(deleted=len(remove), backup=str(backup))))
            else:
                conn.rollback()
    finally:
        conn.rollback()
        conn.close()


if __name__ == '__main__':
    main()
