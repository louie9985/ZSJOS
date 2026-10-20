#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Synthetic, connection-local MySQL tests; no persistent business writes."""
import datetime as dt
import sys
import unittest

import backfill_lead_owner_identity_20261020 as repair

T = dt.datetime(2026, 9, 18, 11, 25, 58)


def fixture():
    lead = dict(id=1, lead_no='SYNTHETIC-1', tenant_id=1, deleted=0, owner_user_id=41,
        owner_identity=None, assignment_status='owned', status='valid', ownership_started_at=T,
        current_assignment_history_id=2, version=4, updater='fixture', update_time=T)
    hist = [dict(id=1,lead_id=1,tenant_id=1,deleted=0,action_type='accept',
        from_owner_user_id=None,to_owner_user_id=41,owner_identity_snapshot=None,occurred_at=T,
        creator='legacy-increment-20260921',reason='legacy-assignment-log-7 销售确认接单'),
        dict(id=2,lead_id=1,tenant_id=1,deleted=0,action_type='other',
        from_owner_user_id=None,to_owner_user_id=None,owner_identity_snapshot=None,
        occurred_at=T+dt.timedelta(days=2),creator='legacy-increment-20260921',reason=repair.BASELINE)]
    oldtime = T-dt.timedelta(microseconds=325882)
    source = repair.indexed({'leads':[dict(id=5,lead_no='SYNTHETIC-1',assigned_sales_id=44,
        assignment_started_at=oldtime,assignment_status='auto_assigned')],
        'logs':[dict(id=7,lead_id=5,assigned_sales_id=44,to_sales_id_snapshot=44,
            from_sales_id_snapshot=None,assignment_status='auto_assigned',
            assignment_status_after='auto_assigned',created_at=oldtime)],
        'receipts':[dict(id=9,lead_id=5,event_type='assign_accept',sales_id=44,
            source_log_id=None,occurred_at=oldtime)],'academic':[],
        'mapping':[dict(legacy_staff=44,matches=1,mapped=41)]})
    return lead,hist,source


class EvidenceTests(unittest.TestCase):
    def test_exact_log_and_receipt(self):
        lead,hist,source=fixture()
        self.assertEqual(repair.classify(lead,hist,source)['identity'],'sales')

    def test_receipt_provenance(self):
        lead,hist,source=fixture()
        hist[0].update(creator='legacy-receipt-backfill-20260928',reason='旧库接收事件回填:assign_accept')
        self.assertEqual(repair.classify(lead,hist,source)['reason'],'legacy_sales_receipt')

    def test_snapshot_education(self):
        lead,hist,source=fixture();hist[0]['owner_identity_snapshot']='education'
        self.assertEqual(repair.classify(lead,hist,source)['identity'],'education')

    def test_fail_closed(self):
        cases = [
            lambda l,h,s:l.update(owner_user_id=42),
            lambda l,h,s:l.update(tenant_id=2),
            lambda l,h,s:l.update(version=None),
            lambda l,h,s:l.update(current_assignment_history_id=999),
            lambda l,h,s:l.update(ownership_started_at=T+dt.timedelta(days=1)),
            lambda l,h,s:h[1].update(reason='unrecognized event'),
            lambda l,h,s:h[1].update(action_type='recycle'),
            lambda l,h,s:h[1].update(action_type='release_to_claim_pool'),
            lambda l,h,s:h[0].update(owner_identity_snapshot='unknown'),
            lambda l,h,s:h[0].update(reason='销售接单'),
            lambda l,h,s:s['academic'].add(5),
            lambda l,h,s:s['mapping'].clear(),
            lambda l,h,s:s['leads']['SYNTHETIC-1'].append(dict(s['leads']['SYNTHETIC-1'][0])),
            lambda l,h,s:s['receipts'][5].clear(),
            lambda l,h,s:s['receipts'][5].append(dict(s['receipts'][5][0])),
            lambda l,h,s:s['logs'][7].update(lead_id=6),
            lambda l,h,s:s['logs'].update({8:dict(s['logs'][7],id=8,created_at=T+dt.timedelta(days=1))}),
            lambda l,h,s:h.append(dict(h[0],id=3,action_type='transfer',to_owner_user_id=42)),
        ]
        for i,change in enumerate(cases):
            with self.subTest(case=i):
                lead,hist,source=fixture();change(lead,hist,source)
                self.assertEqual(repair.classify(lead,hist,source)['decision'],'unresolved')


class MysqlTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.conn=repair.connect('zsjos-mysql-1')
        with cls.conn.cursor() as q:
            q.execute('SET NAMES utf8mb4')
            q.execute('CREATE TEMPORARY TABLE verify_identity_lead ('
                'id BIGINT PRIMARY KEY,lead_no VARCHAR(80),tenant_id BIGINT,deleted INT,'
                'owner_user_id BIGINT,owner_identity VARCHAR(32),assignment_status VARCHAR(40),'
                'status VARCHAR(40),ownership_started_at DATETIME,current_assignment_history_id BIGINT,'
                'version INT,updater VARCHAR(80),update_time DATETIME) ENGINE=InnoDB')
            q.execute('CREATE TEMPORARY TABLE verify_identity_history ('
                'id BIGINT PRIMARY KEY,lead_id BIGINT,tenant_id BIGINT,deleted INT,action_type VARCHAR(40),'
                'from_owner_user_id BIGINT,to_owner_user_id BIGINT,owner_identity_snapshot VARCHAR(32),'
                'occurred_at DATETIME,creator VARCHAR(80),reason VARCHAR(200)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4')

    @classmethod
    def tearDownClass(cls):cls.conn.close()

    def setUp(self):
        self.lead,self.hist,self.source=fixture()
        with self.conn.cursor() as q:
            q.execute('DELETE FROM verify_identity_lead');q.execute('DELETE FROM verify_identity_history')
            for table,values in [('verify_identity_lead',[self.lead]),('verify_identity_history',self.hist)]:
                for r in values:
                    q.execute(f'INSERT INTO {table} ('+','.join(r)+') VALUES ('+','.join(['%s']*len(r))+')',tuple(r.values()))
        self.conn.commit()
        self.item={'before':self.lead,'after':dict(self.lead,owner_identity='sales',version=5,
            updater=repair.MARK,update_time=T+dt.timedelta(days=30)),
            'protected_sha256':repair.protected(self.lead),'history_sha256':repair.digest(self.hist),
            'result':repair.classify(self.lead,self.hist,self.source)}

    def apply(self,q,restore=False):
        return repair.apply_one(q,self.item,self.source,'verify_identity_lead','verify_identity_history',restore)

    def test_initial_repeat_restore(self):
        with self.conn.cursor() as q:
            self.assertEqual(self.apply(q),1);self.conn.commit()
            self.assertEqual(self.apply(q),0);self.conn.rollback()
            self.assertEqual(self.apply(q,True),1);self.conn.commit()
            self.assertEqual(self.apply(q,True),0);self.conn.rollback()
            self.assertEqual(self.apply(q),1);self.conn.commit()

    def test_failure_rollback_and_recovery(self):
        with self.conn.cursor() as q:
            self.apply(q)
            try:raise RuntimeError('injected failure before commit')
            except RuntimeError:self.conn.rollback()
            self.assertEqual(repair.rows(q,'SELECT owner_identity FROM verify_identity_lead')[0]['owner_identity'],None)
            self.assertEqual(self.apply(q),1);self.conn.commit()

    def test_partial_recovery(self):
        with self.conn.cursor() as q:
            # One committed item in a multi-item plan is a supported interruption boundary.
            self.apply(q);self.conn.commit()
            self.assertEqual(self.apply(q),0)
            self.conn.rollback()

    def test_stale_refusal(self):
        with self.conn.cursor() as q:
            for sql in ["UPDATE verify_identity_lead SET version=99",
                        "UPDATE verify_identity_lead SET owner_user_id=99",
                        "UPDATE verify_identity_lead SET status='won'",
                        "UPDATE verify_identity_history SET action_type='recycle' WHERE id=2"]:
                q.execute(sql)
                with self.assertRaises(RuntimeError):self.apply(q)
                self.conn.rollback()

    def test_partial_columns_refused(self):
        with self.conn.cursor() as q:
            q.execute("UPDATE verify_identity_lead SET owner_identity='sales'")
            with self.assertRaises(RuntimeError):self.apply(q)
            self.conn.rollback()

    def test_utf8(self):
        with self.conn.cursor() as q:
            value=repair.rows(q,'SELECT HEX(reason) h FROM verify_identity_history WHERE id=1')[0]['h']
            self.assertEqual(value,self.hist[0]['reason'].encode('utf-8').hex().upper())
        self.conn.rollback()


def verify_prepared_plan(plan_path, sha256):
    """Replay the actual prepared scope against exact-schema, connection-local copies."""
    plan=repair.decode(repair.Path(plan_path).read_text(encoding='utf-8'))
    repair.require(repair.digest(plan)==sha256,'Plan checksum mismatch')
    c=repair.connect('zsjos-mysql-1')
    try:
        with c.cursor() as q:
            q.execute('SET NAMES utf8mb4')
            repair.validate_target(q)
            source=repair.source_data(q)
            repair.require(repair.digest(source)==plan['source_sha256'],'Source changed')
            source=repair.indexed(source)
            q.execute('CREATE TEMPORARY TABLE verify_identity_lead LIKE zsjos_lead')
            q.execute('CREATE TEMPORARY TABLE verify_identity_history LIKE zsjos_lead_assignment_history')
            cols=repair.rows(q,"SELECT column_name FROM information_schema.columns WHERE table_schema=DATABASE() "
                             "AND table_name='zsjos_lead' AND generation_expression='' ORDER BY ordinal_position")
            names=','.join('`'+r['COLUMN_NAME']+'`' for r in cols)
            q.execute(f"INSERT INTO verify_identity_lead ({names}) SELECT {names} FROM zsjos_lead WHERE tenant_id=1 "
                      "AND deleted=0 AND assignment_status='owned' AND owner_identity IS NULL")
            q.execute('INSERT INTO verify_identity_history SELECT h.* FROM zsjos_lead_assignment_history h '
                      'JOIN verify_identity_lead l ON l.id=h.lead_id AND l.tenant_id=h.tenant_id')
            c.commit()
            confirmed=[r for r in plan['items'] if r['result']['decision']=='confirmed']
            for item in confirmed:
                repair.require(repair.apply_one(q,item,source,'verify_identity_lead','verify_identity_history')==1,
                               'Initial application failed')
            c.commit()
            for item in confirmed:
                repair.require(repair.apply_one(q,item,source,'verify_identity_lead','verify_identity_history')==0,
                               'Repeat mutated')
            c.rollback()
            # Full copied row equality also checks SQL column defaults/ON UPDATE behavior.
            for item in plan['items']:
                actual=repair.rows(q,'SELECT * FROM verify_identity_lead WHERE id=%s',(item['before']['id'],))[0]
                repair.require({f:actual[f] for f in repair.FIELDS}==item.get('after',item['before'])
                    and repair.protected(actual)==item['protected_sha256'],'Unexpected copied Lead mutation')
                repair.require(repair.digest(repair.histories(q,actual['id'],'verify_identity_history'))
                               ==item['history_sha256'],'Copied history changed')
            c.rollback()
            print(repair.encode({'exact_schema_mysql':True,'scanned':len(plan['items']),
                'initial_updates':len(confirmed),'repeat_updates':0,'protected_rows_verified':len(plan['items'])}))
    finally:c.rollback();c.close()


if __name__=='__main__':
    if len(sys.argv)==4 and sys.argv[1]=='--prepared-plan':
        verify_prepared_plan(sys.argv[2],sys.argv[3])
    else:unittest.main()
