# -*- coding: utf-8 -*-
"""Read-only, all-scope six-period comparison against an isolated repaired copy."""
import argparse
import datetime as dt
import json
from decimal import Decimal
from pathlib import Path
from zoneinfo import ZoneInfo
from order_history_repair import connect


def facts(database):
    c=connect(database)
    try:
        with c.cursor() as q:
            q.execute('START TRANSACTION READ ONLY')
            q.execute("""SELECT o.id,o.lead_id,o.formal_sales_user_id user_id,o.order_type,o.submitted_at at,o.total_amount amount,
              l.source_type,l.source_provider_user_id,a.dept_id,a.center_id,
              COALESCE(a.received_at,(SELECT MAX(h.occurred_at) FROM zsjos_lead_assignment_history h WHERE h.tenant_id=1 AND h.deleted=0 AND h.lead_id=o.lead_id AND h.to_owner_user_id=o.formal_sales_user_id AND h.action_type IN ('accept','claim','transfer') AND h.occurred_at<=o.submitted_at)) received
              FROM zsjos_order o LEFT JOIN zsjos_lead l ON l.id=o.lead_id AND l.tenant_id=1 AND l.deleted=0
              LEFT JOIN zsjos_performance_attribution a ON a.fact_type='ORDER' AND a.fact_id=o.id AND a.tenant_id=1 AND a.deleted=0
              WHERE o.tenant_id=1 AND o.deleted=0 AND o.status='effective' ORDER BY o.submitted_at DESC,o.id DESC""")
            orders=q.fetchall()
            q.execute("""SELECT h.id,h.lead_id,h.to_owner_user_id user_id,h.occurred_at at,COALESCE(a.received_at,h.occurred_at) received,
              l.source_type,l.source_provider_user_id,l.status,a.dept_id,a.center_id
              FROM zsjos_lead_assignment_history h JOIN zsjos_lead l ON l.id=h.lead_id AND l.tenant_id=1 AND l.deleted=0
              LEFT JOIN zsjos_performance_attribution a ON a.fact_type='ASSIGNMENT' AND a.fact_id=h.id AND a.tenant_id=1 AND a.deleted=0
              WHERE h.tenant_id=1 AND h.deleted=0 AND h.action_type IN ('accept','claim','transfer') AND h.to_owner_user_id IS NOT NULL ORDER BY h.occurred_at DESC,h.id DESC""")
            return orders,q.fetchall()
    finally:c.rollback();c.close()


def metric(orders,receipts,start,end,rolling,legacy):
    eligible=lambda r: r['source_type']!='sales_self_sourced' or r['source_provider_user_id'] is not None if legacy else r['source_type'] not in ('sales_self_sourced','education_self_sourced')
    inside=lambda at:at is not None and start<=at<end
    result={};by_receipt={}
    for r in receipts:
        by_receipt.setdefault((r['lead_id'],r['user_id'],r['received']),r)
        if eligible(r) and inside(r['received']) and r['status'] in ('valid','converted','won'):result.setdefault(r['lead_id'],None)
    for o in orders:
        if not eligible(o) or o['order_type']!='first_purchase' or o['lead_id'] is None or o['amount'] is None or o['amount']<1280 or not inside(o['at']) or o['received'] is None:continue
        if rolling:
            if not inside(o['received']) or o['at']<o['received'] or (o['lead_id'],o['user_id'],o['received']) not in by_receipt or o['lead_id'] not in result:continue
        elif not o['received']<=o['at']<o['received']+dt.timedelta(days=60):continue
        if result.get(o['lead_id']) is None or o['at']<result[o['lead_id']]['at']:result[o['lead_id']]=o
    period=[o for o in orders if inside(o['at'])];avg=[o for o in period if o['amount'] not in (Decimal(0),Decimal('.01'))]
    return {'converted':sum(x is not None for x in result.values()),'denominator':len(result),
            'leads':sorted(result),'convertedLeads':sorted(k for k,v in result.items() if v is not None),
            'orders':len(period),'amount':str(sum((o['amount'] for o in period),Decimal(0))),
            'averageOrders':len(avg),'averageAmount':str(sum((o['amount'] for o in avg),Decimal(0)))}


def compare(before,after):
    old=facts(before);new=facts(after)
    now=dt.datetime.now(ZoneInfo('Asia/Shanghai')).replace(tzinfo=None);month=now.replace(day=1,hour=0,minute=0,second=0,microsecond=0)
    previous=(month-dt.timedelta(days=1)).replace(day=1)
    windows=[('month',month,now,False),('lastMonth',previous,month,False)]+[(f'last{n}',now.replace(hour=0,minute=0,second=0,microsecond=0)-dt.timedelta(days=n-1),now,True) for n in [7,30,60,90]]
    scopes=[('all',None)]+[(column,identity) for column in ['user_id','dept_id','center_id'] for identity in sorted({r[column] for group in (*old,*new) for r in group if r[column] is not None})]
    changed=[];checked=0
    for column,identity in scopes:
        a=[[r for r in group if column=='all' or r[column]==identity] for group in old]
        b=[[r for r in group if column=='all' or r[column]==identity] for group in new]
        for key,start,end,rolling in windows:
            x=metric(*a,start,end,rolling,True);y=metric(*b,start,end,rolling,False);checked+=1
            for field in ['orders','amount','averageOrders','averageAmount']:assert x[field]==y[field],(column,identity,key,field)
            if x!=y:changed.append({'scope':column,'id':identity,'period':key,'before':x,'after':y})
    return {'asOf':now.isoformat(),'scopePeriodsChecked':checked,'changedScopePeriods':len(changed),'financialInputsUnchanged':True,'changes':changed}


if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--before',default='ruoyi-vue-pro');p.add_argument('--after',required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args()
    result=compare(a.before,a.after);a.output.write_text(json.dumps(result,ensure_ascii=True,indent=2),encoding='utf-8')
    print(json.dumps({k:v for k,v in result.items() if k!='changes'}))
