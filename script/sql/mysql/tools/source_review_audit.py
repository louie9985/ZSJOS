# -*- coding: utf-8 -*-
"""Read-only source consistency and September conversion acceptance; no credentials in output."""
import argparse
import datetime as dt
import json
from decimal import Decimal
from order_history_repair import connect, scalar


def population(orders, receipts, start, end, legacy=False):
    def eligible(r):
        if legacy:return r['source_type']!='sales_self_sourced' or r['source_provider_user_id'] is not None
        return r['source_type'] not in ('sales_self_sourced','education_self_sourced')
    result={}
    for r in receipts:
        if eligible(r) and start<=r['received_at']<end and r['status'] in ('valid','converted','won'):
            result.setdefault(r['lead_id'],False)
    for o in orders:
        at,received=o['submitted_at'],o['received_at']
        if eligible(o) and o['order_type']=='first_purchase' and o['total_amount']>=Decimal('1280') and start<=at<end and received and received<=at<received+dt.timedelta(days=60):
            result[o['lead_id']]=True
    return {'converted':sum(result.values()),'denominator':len(result),'rate':str(Decimal(sum(result.values()))/len(result)) if result else None}


def audit(c):
    with c.cursor() as q:
        q.execute('START TRANSACTION READ ONLY')
        q.execute("""SELECT COUNT(*) n FROM zsjos_lead WHERE tenant_id=1 AND deleted=0
          AND ((source_type IN ('sales_self_sourced','education_self_sourced') AND source_provider_recorded=0)
           OR (source_provider_recorded=1 AND source_provider_user_id IS NOT NULL AND (NOT(provider_owner_type <=> 'system_user') OR NOT(provider_owner_id <=> source_provider_user_id)))
           OR (source_type IN ('sales_self_sourced','education_self_sourced') AND source_provider_recorded=1 AND source_provider_user_id IS NULL AND (provider_owner_type IS NOT NULL OR provider_owner_id IS NOT NULL)))""")
        result={'unresolved':q.fetchone()['n']}
        q.execute("""SELECT o.order_no,o.lead_id,o.order_type,o.submitted_at,o.total_amount,l.source_type,l.source_provider_user_id,
          COALESCE(a.received_at,(SELECT MAX(h.occurred_at) FROM zsjos_lead_assignment_history h WHERE h.tenant_id=1 AND h.deleted=0 AND h.lead_id=o.lead_id AND h.to_owner_user_id=o.formal_sales_user_id AND h.action_type IN ('accept','claim','transfer') AND h.occurred_at<=o.submitted_at)) received_at
          FROM zsjos_order o JOIN zsjos_lead l ON l.id=o.lead_id AND l.tenant_id=1 AND l.deleted=0
          LEFT JOIN zsjos_performance_attribution a ON a.fact_type='ORDER' AND a.fact_id=o.id AND a.tenant_id=1 AND a.deleted=0
          WHERE o.tenant_id=1 AND o.deleted=0 AND o.status='effective' AND o.formal_sales_user_id=17 ORDER BY o.submitted_at DESC""")
        orders=q.fetchall()
        q.execute("""SELECT h.lead_id,COALESCE(a.received_at,h.occurred_at) received_at,l.status,l.source_type,l.source_provider_user_id
          FROM zsjos_lead_assignment_history h JOIN zsjos_lead l ON l.id=h.lead_id AND l.tenant_id=1 AND l.deleted=0
          LEFT JOIN zsjos_performance_attribution a ON a.fact_type='ASSIGNMENT' AND a.fact_id=h.id AND a.tenant_id=1 AND a.deleted=0
          WHERE h.tenant_id=1 AND h.deleted=0 AND h.action_type IN ('accept','claim','transfer') AND h.to_owner_user_id=17 ORDER BY h.occurred_at DESC""")
        receipts=q.fetchall();start=dt.datetime(2026,9,1);end=dt.datetime(2026,10,1)
        result['user17SeptemberLegacyRule']=population(orders,receipts,start,end,True)
        if not result['unresolved']:result['user17SeptemberConfirmedRule']=population(orders,receipts,start,end)
        month=[o for o in orders if start<=o['submitted_at']<end]
        result['user17SeptemberOrders']={'count':len(month),'amount':str(sum((o['total_amount'] for o in month),Decimal(0)))}
        q.execute("SELECT lead_no,source_type,CAST(source_provider_recorded AS UNSIGNED) recorded,source_provider_user_id,provider_owner_id,HEX(provider_owner_name_snapshot) provider_name_hex FROM zsjos_lead WHERE tenant_id=1 AND lead_no IN ('LD202609200029','LD202609200046','LD202609200048','LD202609150070','LD202609170048') ORDER BY lead_no")
        result['keyRecords']=q.fetchall()
    c.rollback();return result


if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--database',default='ruoyi-vue-pro');a=p.parse_args()
    c=connect(a.database)
    try:print(json.dumps(audit(c),ensure_ascii=True,default=scalar))
    finally:c.close()
