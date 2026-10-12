# -*- coding: utf-8 -*-
"""Read-only business acceptance of a V305 repair against private original rows."""
import argparse
import hashlib
import json
from collections import Counter
from decimal import Decimal
from pathlib import Path

from order_history_repair import connect,schema,normalize,ident,inspect_applied,private_write
from order_history_repair_model import TABLES,ORDER_ACTOR,LEADS,EXCLUDED,KEY


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--database',default='ruoyi-vue-pro');parser.add_argument('--plan',type=Path,required=True)
    parser.add_argument('--before',type=Path,required=True);parser.add_argument('--output',type=Path)
    args=parser.parse_args();raw=args.plan.read_bytes();plan=json.loads(raw);sha=hashlib.sha256(raw).hexdigest()
    before=json.loads(args.before.read_text(encoding='utf-8'));conn=connect(args.database)
    applied=inspect_applied(conn,plan,sha)
    assert applied['matchesAfterImages'] and applied['ledgersPresent']
    with conn.cursor() as cur:
        cur.execute('START TRANSACTION WITH CONSISTENT SNAPSHOT, READ ONLY')
        types=schema(cur);after={}
        for t in TABLES:
            cur.execute('SELECT * FROM '+ident(t)+' WHERE tenant_id=1')
            after[t]={str(r['id']):normalize(r,types[t]) for r in cur.fetchall()}
    changed={(op['table'],str(op['before']['id'])) for op in plan['operations'] if 'before' in op}
    for t,rows in before.items():
        for k,r in rows.items():
            assert k in after[t], 'Original row disappeared'
            if (t,k) not in changed:assert r==after[t][k], 'Out-of-scope row changed'
    orders={r['order_no']:r for r in after['zsjos_order'].values()}
    for no,uid in ORDER_ACTOR.items():
        assert orders[no]['submitter_user_id']==uid==orders[no]['formal_sales_user_id']
    leads={r['lead_no']:r for r in after['zsjos_lead'].values()}
    for no,(owner,submitter,_) in LEADS.items():
        assert leads[no]['owner_user_id']==owner and leads[no]['source_user_id']==submitter
        original=before['zsjos_lead'][str(leads[no]['id'])]
        for field in ('provider_owner_type','provider_owner_id','provider_owner_name_snapshot','source_provider_user_id','source_provider_recorded'):
            assert leads[no][field]==original[field], 'Actor correction must preserve source attribution'
    eligible=[orders[n] for n in plan['eligibleOrders']]
    for o in eligible:
        r=after['zsjos_order_approval_round'][str(o['current_approval_round_id'])]
        assert r['order_id']==o['id'] and r['status']=='approved'
        rounds=[x for x in after['zsjos_order_approval_round'].values() if x['order_id']==o['id'] and not x['deleted']]
        assert max(rounds,key=lambda x:x['round_no'])['id']==r['id']
        items=[i for i in after['zsjos_order_item'].values() if i['order_id']==o['id'] and not i['deleted']]
        assert sum(Decimal(i['payable_amount']) for i in items)==Decimal(o['total_amount'])
        original=before['zsjos_order'][str(o['id'])]
        for k in ('status','total_amount','payable_amount','customer_paid_at','effective_at','payment_method','fee_mode','gift_items'):
            assert original[k]==o[k], 'Unapproved order fact changed'
    attributions=[r for r in after['zsjos_performance_attribution'].values() if r['creator']==KEY and r['fact_type']=='ORDER']
    assert len(attributions)==38 and all(r['org_source']=='current' for r in attributions)
    report={'eligibleOrders':len(eligible),'correctActorOrders':38,'correctLeads':8,
            'allOrderAmountsAndLifecyclePreserved':True,'allItemSumsMatch':True,
            'currentEqualsLatestApprovedRound':True,'untouchedRowsPreserved':True,
            'newAttributions':len(attributions),'roundVersions':dict(Counter(after['zsjos_order_approval_round'][str(o['current_approval_round_id'])]['order_snapshot'].get('snapshotVersion') for o in eligible)),
            'excludedSeedOrders':sum(o['creator'] in EXCLUDED and o['status']=='effective' and not o['deleted'] for o in orders.values()),
            'applied':applied}
    conn.rollback();conn.close()
    if args.output:private_write(args.output,report)
    print(json.dumps(report,ensure_ascii=True))


if __name__=='__main__':main()
