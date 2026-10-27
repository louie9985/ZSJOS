#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Authorized test-only historical correction; see order-approval-20261027.md."""
import argparse
import datetime as dt
import decimal
import hashlib
import json
import os
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'tools'))
from zsjos_db import db_config
import pymysql

MARKER = 'order-approval-20261027'
LEAD, ORDER, ROUND, OPP = 'zsjos_lead', 'zsjos_order', 'zsjos_order_approval_round', 'zsjos_opportunity'
SCOPE = {
    LEAD: 'id=6159', ORDER: '(lead_id=6159 OR person_id=7661)',
    ROUND: 'order_id IN (8633,8634)', OPP: '(lead_id=6159 OR person_id=7661)',
    'zsjos_person': 'id=7661', 'zsjos_lead_intended_product': 'lead_id=6159',
    'zsjos_order_item': 'order_id IN (8633,8634)',
    'zsjos_registration_case': 'order_id IN (8633,8634)',
    'zsjos_registration_item': 'registration_case_id IN (841,842)',
    'zsjos_service_relation': 'person_id=7661',
    'zsjos_purchase_intent': '(lead_id=6159 OR person_id=7661)',
    'zsjos_cashback': '(lead_id=6159 OR order_id IN (8633,8634))',
    'zsjos_performance_attribution': 'lead_id=6159',
    'zsjos_payment_order': '(lead_id=6159 OR person_id=7661)',
    'zsjos_order_payment_allocation': 'order_id IN (8633,8634)',
    'zsjos_order_supervisor_confirmation': 'order_id IN (8633,8634)',
    'zsjos_lead_appeal': 'lead_id=6159',
    'zsjos_lead_public_sea_pool_cycle': 'lead_id=6159',
    'zsjos_lead_public_sea_pool_compatibility_record': 'lead_id=6159',
    'zsjos_business_task': "(biz_type='lead' AND biz_id=6159) OR (biz_type IN ('order','sales_order') AND biz_id IN (8633,8634)) OR (biz_type='registration_case' AND biz_id IN (841,842))",
}


def require(ok, message):
    if not ok:
        raise RuntimeError(message)


def encoded(value):
    def default(x):
        if isinstance(x, bytes): return {'$bytes': x.hex()}
        if isinstance(x, (dt.date, dt.datetime)): return {'$type': type(x).__name__, '$date': x.isoformat()}
        if isinstance(x, decimal.Decimal): return {'$decimal': str(x)}
        raise TypeError(type(x).__name__)
    return json.dumps(value, default=default, ensure_ascii=False, sort_keys=True, indent=2)


def digest(value):
    return hashlib.sha256(encoded(value).encode('utf-8')).hexdigest()


def save(path, value):
    with path.open('x', encoding='utf-8') as f:
        f.write(encoded(value)); f.flush(); os.fsync(f.fileno())


def capture(q, prefix='', lock=False):
    state = {}
    for table, condition in SCOPE.items():
        q.execute(f'SELECT * FROM {prefix}{table} WHERE tenant_id=1 AND ({condition}) ORDER BY id' + (' FOR UPDATE' if lock else ''))
        state[table] = list(q.fetchall())
    return state


def active(row):
    return row['deleted'] in (0, False, b'\x00')


def row(state, table, id):
    found = [r for r in state[table] if r['id'] == id]
    require(len(found) == 1 and active(found[0]), 'Target record missing or deleted')
    return found[0]


def validate(before):
    lead, order, approval = row(before, LEAD, 6159), row(before, ORDER, 8634), row(before, ROUND, 45)
    require(lead['lead_no']=='LD202609190010' and lead['person_id']==7661 and lead['status']=='valid'
            and lead['assignment_status']=='owned' and lead['suspended_at'] is None and lead['owner_user_id'] is not None,
            'Lead prerequisite changed')
    require(order['order_no']=='OR202609190004' and order['status']=='terminated'
            and order['order_type']=='first_purchase' and order['lead_id']==6159 and order['person_id']==7661
            and order['current_approval_round_id']==45 and order['opportunity_id'] is None
            and order['superseded_by_order_id'] is None and order['effective_at'] is None
            and order['creator']=='legacy-increment-20260921', 'Order prerequisite changed')
    require(len(before[ORDER])==2 and row(before, ORDER, 8633)['status']=='superseded'
            and row(before, ORDER, 8633)['superseded_by_order_id']==8634, 'Order chain changed')
    require(approval['order_id']==8634 and approval['status']=='rejected' and approval['process_instance_id'] is None
            and approval['process_definition_key']=='legacy-finance-review' and approval['completed_at'] is None,
            'Approval is not the inspected historical round')
    require(set(json.loads(approval['order_snapshot']))=={'legacyStatus','legacyOrderId'}, 'Snapshot contract changed')
    require(not before[OPP], 'Conflicting opportunity exists')
    row(before, 'zsjos_person', 7661)
    for table in ('zsjos_service_relation','zsjos_purchase_intent','zsjos_order_supervisor_confirmation',
                  'zsjos_lead_appeal','zsjos_lead_public_sea_pool_cycle',
                  'zsjos_lead_public_sea_pool_compatibility_record','zsjos_business_task'):
        require(not any(active(r) for r in before[table]), 'Related active state requires review: '+table)
    require(len([r for r in before['zsjos_lead_intended_product'] if active(r)])==1, 'Product context changed')
    require(all(r['status']=='cancelled' for r in before['zsjos_registration_case'] if active(r)), 'Registration state changed')


def verify(before, after, name, now):
    lead, order, approval = row(after, LEAD, 6159), row(after, ORDER, 8634), row(after, ROUND, 45)
    require(len(after[OPP])==1, 'Expected one opportunity')
    opp = after[OPP][0]
    require(active(opp) and opp['lead_id']==6159 and opp['person_id']==7661 and opp['type']=='initial_conversion'
            and opp['status']=='won' and opp['won_at']==now and opp['owner_user_id']==lead['owner_user_id']
            and opp['creator']==MARKER and opp['updater']==MARKER and opp['version']==0, 'Opportunity result differs')
    expected_summary = [dict(spuRef=p['spu_ref'] or '',spuName=p['spu_name_snapshot'] or '',
                             skuRef=p['sku_ref'] or '',skuName=p['sku_name_snapshot'] or '',
                             primary=p['is_primary'] in (1,True,b'\x01'))
                        for p in before['zsjos_lead_intended_product'] if active(p)]
    require(json.loads(opp['expected_product_summary'])==expected_summary
            and opp['create_time']==now and opp['update_time']==now
            and all(opp[k] is None for k in ('next_follow_up_at','lost_at','lost_reason','shelved_until','shelved_reason',
                                            'source_service_relation_id','previous_order_id')), 'Opportunity context differs')
    require(order['student_name']==name and order['status']=='effective' and order['effective_at']==now
            and order['opportunity_id']==opp['id'] and order['termination_reason'] is None
            and order['terminated_at'] is None and order['cancelled_at'] is None and order['cancel_reason'] is None,
            'Order result differs')
    require(approval['status']=='approved' and approval['completed_at']==now
            and approval['decision_reason']=='按用户确认修正历史订单审批结果；通过时间为本次修正时间', 'Approval result differs')
    require(lead['status']=='won' and lead['next_follow_up_at'] is None, 'Lead result differs')
    allowed = {LEAD: (6159, {'status','next_follow_up_at','version','updater','update_time'}),
               ORDER: (8634, {'student_name','status','effective_at','opportunity_id','termination_reason','terminated_at',
                              'cancelled_at','cancel_reason','version','updater','update_time'}),
               ROUND: (45, {'status','completed_at','decision_reason','version','updater','update_time'})}
    for table, originals in before.items():
        if table==OPP: continue
        require(len(originals)==len(after[table]), 'Unexpected row count change: '+table)
        for original, current in zip(originals, after[table]):
            target, fields = allowed.get(table, (None, set()))
            if original['id']==target:
                require(current['version']==original['version']+1 and current['updater']==MARKER
                        and current['update_time']==now, 'Audit/version differs')
                require(all(current[k]==v for k,v in original.items() if k not in fields), 'Protected field changed: '+table)
            else: require(current==original, 'Protected row changed: '+table)


def mutate(q, before, name, now, prefix='', fail=False):
    current = capture(q, prefix, lock=True)
    if current != before:
        verify(before, current, name, now)
        return False
    validate(current)
    lead = row(before, LEAD, 6159)
    summary = [dict(spuRef=p['spu_ref'] or '',spuName=p['spu_name_snapshot'] or '',
                    skuRef=p['sku_ref'] or '',skuName=p['sku_name_snapshot'] or '',
                    primary=p['is_primary'] in (1,True,b'\x01'))
               for p in before['zsjos_lead_intended_product'] if active(p)]
    q.execute(f'''INSERT INTO {prefix}{OPP}
        (tenant_id,person_id,lead_id,owner_user_id,type,status,won_at,expected_product_summary,
         version,creator,updater,create_time,update_time)
        VALUES (1,7661,6159,%s,'initial_conversion','won',%s,%s,0,%s,%s,%s,%s)''',
        (lead['owner_user_id'],now,json.dumps(summary,ensure_ascii=False),MARKER,MARKER,now,now))
    opportunity_id = q.lastrowid
    if fail: raise RuntimeError('Injected rollback check')
    q.execute(f'''UPDATE {prefix}{ORDER} SET student_name=%s,status='effective',effective_at=%s,opportunity_id=%s,
        termination_reason=NULL,terminated_at=NULL,cancelled_at=NULL,cancel_reason=NULL,
        version=version+1,updater=%s,update_time=%s WHERE tenant_id=1 AND id=8634 AND deleted=0 AND status='terminated' ''',
        (name,now,opportunity_id,MARKER,now))
    require(q.rowcount==1, 'Order write count differs')
    q.execute(f'''UPDATE {prefix}{ROUND} SET status='approved',completed_at=%s,decision_reason=%s,
        version=version+1,updater=%s,update_time=%s WHERE tenant_id=1 AND id=45 AND deleted=0 AND status='rejected' ''',
        (now,'按用户确认修正历史订单审批结果；通过时间为本次修正时间',MARKER,now))
    require(q.rowcount==1, 'Approval write count differs')
    q.execute(f'''UPDATE {prefix}{LEAD} SET status='won',next_follow_up_at=NULL,version=version+1,
        updater=%s,update_time=%s WHERE tenant_id=1 AND id=6159 AND deleted=0 AND status='valid' ''',(MARKER,now))
    require(q.rowcount==1, 'Lead write count differs')
    verify(before,capture(q,prefix),name,now)
    q.execute(f'SELECT HEX(student_name) AS name_hex FROM {prefix}{ORDER} WHERE tenant_id=1 AND id=8634')
    require(q.fetchone()['name_hex']==name.encode('utf-8').hex().upper(), 'Student name UTF-8 mismatch')
    q.execute(f'SELECT HEX(decision_reason) AS reason_hex FROM {prefix}{ROUND} WHERE tenant_id=1 AND id=45')
    require(q.fetchone()['reason_hex']=='按用户确认修正历史订单审批结果；通过时间为本次修正时间'.encode('utf-8').hex().upper(), 'Reason UTF-8 mismatch')
    return True


def controlled(con, q, before, name, now):
    prefix='verify_20261027_'
    for table in SCOPE:
        q.execute(f'CREATE TEMPORARY TABLE {prefix}{table} LIKE {table}')
        q.execute("SELECT column_name FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name=%s AND generation_expression='' ORDER BY ordinal_position", (table,))
        columns=[r['COLUMN_NAME'] for r in q.fetchall()]
        if before[table]:
            q.executemany(f"INSERT INTO {prefix}{table} ({','.join('`'+c+'`' for c in columns)}) VALUES ({','.join(['%s']*len(columns))})", [[r[c] for c in columns] for r in before[table]])
    con.commit()
    try: mutate(q,before,name,now,prefix,fail=True)
    except RuntimeError as e: require(str(e)=='Injected rollback check','Unexpected controlled failure'); con.rollback()
    else: raise RuntimeError('Failure injection did not fire')
    require(capture(q,prefix)==before, 'Failure left partial state')
    require(mutate(q,before,name,now,prefix), 'Initial recovery failed')
    con.commit()
    after=capture(q,prefix)
    require(not mutate(q,before,name,now,prefix) and capture(q,prefix)==after, 'Repeat was not zero-write')
    con.rollback()
    q.execute(f"UPDATE {prefix}{ORDER} SET version=version+1 WHERE tenant_id=1 AND id=8634")
    try: mutate(q,before,name,now,prefix)
    except RuntimeError: con.rollback()
    else: raise RuntimeError('Post-repair business edit was accepted')
    require(capture(q,prefix)==after, 'Controlled rollback differs')
    con.rollback()


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--evidence-dir',type=Path,required=True)
    parser.add_argument('--name-file',type=Path,required=True)
    parser.add_argument('--apply',action='store_true')
    args=parser.parse_args()
    env=os.getenv('ZSJOS_AGENT_ENV')
    if env is None:
        marker=Path('/etc/zsjos/agent-environment')
        env=marker.read_text().strip() if marker.exists() else 'local'
    require(env=='test','Only the authorized test environment is supported')
    os.umask(0o077)
    args.evidence_dir.mkdir(mode=0o700,parents=True,exist_ok=False)
    name=args.name_file.read_text(encoding='utf-8').strip()
    require(0<len(name)<=100, 'Invalid name input')
    cfg=db_config('test')
    require(cfg.host in ('127.0.0.1','localhost') and cfg.database=='zsjos', 'Unexpected database target')
    con=pymysql.connect(host=cfg.host,port=cfg.port,user=cfg.user,password=cfg.password,database=cfg.database,
                        charset='utf8mb4',autocommit=False,cursorclass=pymysql.cursors.DictCursor)
    try:
        with con.cursor() as q:
            q.execute("SET NAMES utf8mb4")
            q.execute("SET time_zone='+08:00'")
            q.execute('START TRANSACTION READ ONLY')
            before=capture(q); validate(before)
            q.execute('SELECT CURRENT_TIMESTAMP AS now'); now=q.fetchone()['now']
            con.rollback()
            save(args.evidence_dir/'before.json',before)
            controlled(con,q,before,name,now)
            save(args.evidence_dir/'controlled.json',dict(initial=True,repeat=True,failure_rollback=True,recovery=True,stale_refusal=True))
            print('Controlled MySQL checks passed; private backup saved')
            if not args.apply: return
            q.execute('SET TRANSACTION ISOLATION LEVEL SERIALIZABLE')
            con.begin()
            require(capture(q,lock=True)==before,'Live preimage changed; refusing correction')
            require(mutate(q,before,name,now),'Expected one correction')
            after=capture(q)
            save(args.evidence_dir/'pending-after.json',after)
            save(args.evidence_dir/'context.json',dict(marker=MARKER,corrected_at=now,before_sha256=digest(before),after_sha256=digest(after)))
            con.commit()
            q.execute('START TRANSACTION READ ONLY')
            committed=capture(q)
            require(committed==after,'Committed state differs')
            verify(before,committed,name,now)
            con.rollback()
            con.begin()
            require(not mutate(q,before,name,now),'Live repeat must not write')
            con.rollback()
            save(args.evidence_dir/'verified.json',dict(order_no='OR202609190004',order_status='effective',round_status='approved',lead_status='won',opportunity_status='won',name_matches=True,utf8_hex=True,protected_rows_unchanged=True,repeat_zero_writes=True,after_sha256=digest(after)))
            print('Verified: order effective; round approved; lead/opportunity won; name matches; UTF-8 valid; repeat zero writes')
    finally:
        con.rollback(); con.close()


if __name__=='__main__':
    try: main()
    except Exception as error:
        # Driver errors may include sensitive values; retain only the class publicly.
        print('Repair stopped: '+(str(error) if type(error) is RuntimeError else type(error).__name__),file=sys.stderr)
        raise SystemExit(1)
