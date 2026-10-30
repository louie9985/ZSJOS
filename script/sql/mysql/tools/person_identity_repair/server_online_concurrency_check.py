# -*- coding: utf-8 -*-
"""Reproduce stale-request writes on real server DDL with synthetic isolated rows.

No application-write mode. Row locks cannot invalidate IDs cached by a request;
table locks also cannot invalidate an autocommit request between its reads/writes.
"""
import argparse
from pathlib import Path
import threading
import time

from server_target import ServerTarget, read, save, require
from group_repair import prepare_group, apply_group, ALIAS, CLAIM, EVENT
from repair import LIVE


class AlreadyTableLocked:
    def __init__(self, connection):
        self.connection = connection

    def begin(self):
        # START TRANSACTION would release MySQL's explicit table locks.
        pass

    def __getattr__(self, name):
        return getattr(self.connection, name)


def scenario(target, audit, table_locks):
    db = target.create_isolated()
    merger = target.connect_isolated(db)
    writer = target.connect_isolated(db)
    observer = target.connect_isolated(db)
    tables = sorted(set(LIVE) | {'zsjos_person', CLAIM, EVENT})
    outcome = {}
    started = threading.Event()
    finished = threading.Event()
    thread = None
    try:
        with merger.cursor() as q:
            for table in tables:
                q.execute(audit['schemas'][table])
            q.execute("INSERT INTO zsjos_person(id,person_no,name,wechat_id,identity_status,first_seen_at,last_seen_at,tenant_id) VALUES(1,'SYNTHETIC-A','合成并发测试','synthetic-only','lead',NOW(),NOW(),1),(2,'SYNTHETIC-B','合成并发测试','synthetic-only','lead',NOW(),NOW(),1)")
            q.execute("INSERT INTO zsjos_lead(id,lead_no,person_id,submitted_name,source_type,status,assignment_status,submitted_at,tenant_id) VALUES(1,'SYNTHETIC-L',2,'合成并发测试','sales','pending','unassigned',NOW(),1)")
        merger.commit()
        plan = prepare_group(merger, db, [1, 2], {'memberIds':[1,2], 'targetPersonId':1,
                            'canonicalName':'合成并发测试', 'reviewReference':'synthetic-concurrency-only'})
        # saveDraft reads the Lead without FOR UPDATE inside its transaction.
        # createTalkRecord performs independent autocommit permission reads.
        writer.autocommit(table_locks)
        with writer.cursor() as q:
            q.execute('SELECT person_id FROM zsjos_lead WHERE tenant_id=1 AND id=1')
            cached = q.fetchone()['person_id']
            q.execute('SELECT CONNECTION_ID() id')
            writer_id = q.fetchone()['id']

        def delayed_insert():
            try:
                started.set()
                with writer.cursor() as q:
                    if table_locks:
                        q.execute("INSERT INTO zsjos_media_student_talk_record(student_person_id,operator_user_id,content,occurred_at,tenant_id) VALUES(%s,1,'synthetic-only',NOW(),1)", (cached,))
                    else:
                        q.execute("INSERT INTO zsjos_purchase_intent(purchase_intent_no,collection_mode,purchase_type,lead_id,person_id,initiator_user_id,owner_user_id,draft_json,item_snapshot_json,total_amount,tenant_id) VALUES('SYNTHETIC-PI','offline_paid','first_purchase',1,%s,1,1,'{}','[]',1,1)", (cached,))
                writer.commit()
                outcome['insertCommitted'] = True
            except Exception as e:
                writer.rollback()
                outcome['errorType'] = type(e).__name__
            finally:
                finished.set()

        def before_commit(_):
            nonlocal thread
            thread = threading.Thread(target=delayed_insert)
            thread.start()
            require(started.wait(2), 'Synthetic writer did not start')
            deadline = time.monotonic() + 3
            while time.monotonic() < deadline:
                with observer.cursor() as q:
                    q.execute('SELECT STATE FROM information_schema.processlist WHERE ID=%s', (writer_id,))
                    state = q.fetchone()
                    q.execute('SELECT trx_state FROM information_schema.innodb_trx WHERE trx_mysql_thread_id=%s', (writer_id,))
                    trx = q.fetchone()
                observer.rollback()
                if state and 'lock' in (state['STATE'] or '').lower() or trx and trx['trx_state']=='LOCK WAIT':
                    outcome['waitObserved'] = True
                    return
                require(not finished.is_set(), 'Writer unexpectedly finished before merge commit')
                time.sleep(0.05)
            raise ValueError('Synthetic lock wait not observed')

        if table_locks:
            with merger.cursor() as q:
                q.execute('SET SESSION lock_wait_timeout=3')
                q.execute('LOCK TABLES '+','.join('`'+t+'` WRITE' for t in tables))
        try:
            result = apply_group(AlreadyTableLocked(merger) if table_locks else merger, plan,
                                 execute=True, before_commit=before_commit)
            require(result['committed'], 'Synthetic merge did not commit')
        finally:
            if table_locks:
                with merger.cursor() as q:q.execute('UNLOCK TABLES')
        require(finished.wait(5), 'Synthetic writer did not finish after unlock')
        thread.join()
        require(outcome.get('insertCommitted'), 'Expected delayed write was rejected')
        table, field = ('zsjos_media_student_talk_record','student_person_id') if table_locks else ('zsjos_purchase_intent','person_id')
        with merger.cursor() as q:
            q.execute(f'SELECT COUNT(*) n FROM `{table}` r JOIN zsjos_person p ON p.id=r.`{field}` AND p.tenant_id=r.tenant_id WHERE r.tenant_id=1 AND p.deleted=1')
            stale = q.fetchone()['n']
        require(stale==1, 'Stale-reference reproduction differed')
        return {'isolatedDatabase':db, 'lockMode':'table' if table_locks else 'row',
                **outcome, 'lateReferencesToArchivedPerson':stale, 'applicationWrites':0}
    finally:
        merger.rollback()
        if table_locks:
            with merger.cursor() as q:q.execute('UNLOCK TABLES')
        if thread is not None:thread.join(timeout=12)
        writer.close(); observer.close(); merger.close()


def run(binding, snapshot, output):
    target = ServerTarget(read(binding))
    audit = read(snapshot/'audit.json')
    require(audit['target']==target.binding, 'Snapshot target differs')
    results = [scenario(target, audit, locks) for locks in (False, True)]
    summary = {'applicationWrites':0, 'onlineMergeSafe':False, 'scenarios':results}
    save(output, summary)
    return summary


if __name__=='__main__':
    p=argparse.ArgumentParser()
    p.add_argument('--binding',type=Path,required=True)
    p.add_argument('--snapshot',type=Path,required=True)
    p.add_argument('--output',type=Path,required=True)
    args=p.parse_args()
    print(__import__('json').dumps(run(args.binding,args.snapshot,args.output)))
