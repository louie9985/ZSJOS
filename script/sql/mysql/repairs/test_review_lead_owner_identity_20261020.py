#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Review-policy regressions and guarded SQL tests on connection-local fixtures."""
import collections
import datetime as dt
import unittest

import review_lead_owner_identity_20261020 as review
from test_backfill_lead_owner_identity_20261020 import fixture, MysqlTests as OriginalMysqlTests, T


def extend(source):
    source.update(old_states={5:dict(id=5,lead_status='valid',public_pool_released_at=None)},
        academic_rows=collections.defaultdict(list),status_events=collections.defaultdict(list),
        orders=collections.defaultdict(list),events=collections.defaultdict(list),actors={44:444})
    return source


def order(owner=41,identity='sales'):
    return dict(id=31,lead_id=1,tenant_id=1,deleted=0,status='effective',order_type='first_purchase',
                formal_sales_user_id=owner,formal_owner_identity=identity,version=1)


class ReviewEvidenceTests(unittest.TestCase):
    def test_user_confirmed_import_gaps(self):
        for gap in ['time','no_history','stale_import_owner']:
            with self.subTest(gap=gap):
                lead,hist,source=fixture();extend(source)
                if gap=='time':lead['ownership_started_at']=None
                if gap=='no_history':hist=hist[1:]
                if gap=='stale_import_owner':hist[0]['to_owner_user_id']=99
                self.assertEqual(review.classify(lead,hist,source)['identity'],'sales')

    def test_raw_log_without_receipt(self):
        lead,hist,source=fixture();extend(source);source['receipts'][5].clear()
        self.assertEqual(review.classify(lead,hist,source)['reason'],'reviewed_current_owner_raw_sales_assignment')

    def test_self_order(self):
        lead,hist,source=fixture();extend(source);source['receipts'][5].clear();source['logs'].clear()
        source['status_events'][5]=[dict(id=11,event_type='sales_self_submit',operator_id=444)]
        source['orders'][1]=[order()]
        self.assertEqual(review.classify(lead,hist,source)['reason'],'reviewed_current_owner_sales_self_order')
        source['orders'][1]=[order(owner=99)]
        self.assertEqual(review.classify(lead,hist,source)['decision'],'unresolved')

    def test_public_sea_retains_owner(self):
        lead,hist,source=fixture();extend(source)
        source['leads'][lead['lead_no']][0]['assignment_status']='recycled'
        source['old_states'][5]['public_pool_released_at']=T+dt.timedelta(days=2)
        source['status_events'][5]=[
            dict(id=11,event_type='lead_recycled',created_at=T+dt.timedelta(days=1)),
            dict(id=12,event_type='lead_released_public_pool',to_status='valid',created_at=T+dt.timedelta(days=2))]
        self.assertEqual(review.classify(lead,hist,source)['public_sea_release_event_id'],12)
        source['status_events'][5].append(dict(id=13,event_type='lead_recycled',created_at=T+dt.timedelta(days=3)))
        self.assertEqual(review.classify(lead,hist,source)['reason'],'unreleased_actual_recycle')

    def test_academic_disambiguation(self):
        lead,hist,source=fixture();extend(source);lead['status']='won'
        source['academic'].add(5);source['academic_rows'][5]=[dict(id=8,owner_id=55)]
        self.assertEqual(review.classify(lead,hist,source)['decision'],'unresolved')
        source['orders'][1]=[order()]
        self.assertEqual(review.classify(lead,hist,source)['effective_sales_order_id'],31)
        source['academic_rows'][5][0]['owner_id']=44
        self.assertEqual(review.classify(lead,hist,source)['reason'],'current_owner_has_academic_identity')

    def test_real_conflicts_still_refused(self):
        changes=[lambda l,h,s:s['mapping'].update({44:99}),
                 lambda l,h,s:s['logs'][7].update(to_sales_id_snapshot=99),
                 lambda l,h,s:h[0].update(creator='normal-runtime'),
                 lambda l,h,s:h[0].update(owner_identity_snapshot='education'),
                 lambda l,h,s:l.update(tenant_id=2),
                 lambda l,h,s:l.update(assignment_status='recycle_pending'),
                 lambda l,h,s:s['orders'].update({1:[order(identity='education')]}),
                 lambda l,h,s:s['events'].update({1:[dict(id=1,deleted=0,event_type='lead_recycled',occurred_at=T)]})]
        for i,change in enumerate(changes):
            with self.subTest(case=i):
                lead,hist,source=fixture();extend(source);change(lead,hist,source)
                self.assertEqual(review.classify(lead,hist,source)['decision'],'unresolved')


class ReviewedMysqlTests(OriginalMysqlTests):
    def setUp(self):
        super().setUp();extend(self.source)
        self.item['result']=review.classify(self.lead,self.hist,self.source)

    def apply(self,q,restore=False):
        return review.base.apply_one(q,self.item,self.source,'verify_identity_lead',
                                     'verify_identity_history',restore,classifier=review.classify)


if __name__=='__main__':unittest.main()
