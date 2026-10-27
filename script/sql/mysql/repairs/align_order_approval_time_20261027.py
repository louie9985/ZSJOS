#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Explicit single-order timestamp correction; not a historical approval reconstruction."""
import argparse
import datetime as dt
import os
from pathlib import Path

import approve_legacy_order_20261027 as base

MARKER = 'order-approval-time-20261027'
REASON = '按用户明确要求，将审核通过时间调整为本轮审批发起时间；非原始审批时间还原'


def verify(before, after, target):
    allowed = {
        base.ORDER: (8634, 'effective_at'),
        base.ROUND: (45, 'completed_at'),
        base.OPP: (base.row(before, base.ORDER, 8634)['opportunity_id'], 'won_at'),
    }
    for table, originals in before.items():
        base.require(len(originals)==len(after[table]), 'Unexpected row count change')
        for old,new in zip(originals,after[table]):
            id, field = allowed.get(table, (None,None))
            if old['id'] != id:
                base.require(old==new, 'Protected row changed: '+table)
                continue
            fields={field,'version','updater','update_time'}
            if table==base.ROUND:
                fields.add('decision_reason')
                base.require(new['decision_reason']==REASON,'Correction explanation differs')
            base.require(new[field]==target and new['version']==old['version']+1
                         and new['updater']==MARKER, 'Corrected field or audit differs')
            base.require(all(new[k]==v for k,v in old.items() if k not in fields), 'Protected field changed: '+table)


def apply(q,before,target,prefix='',fail=False):
    current=base.capture(q,prefix,lock=True)
    if current!=before:
        verify(before,current,target)
        return False
    opportunity_id=base.row(before,base.ORDER,8634)['opportunity_id']
    for table,id,field in ((base.ORDER,8634,'effective_at'),(base.ROUND,45,'completed_at'),(base.OPP,opportunity_id,'won_at')):
        extra=',decision_reason=%s' if table==base.ROUND else ''
        args=[target,MARKER]+([REASON] if table==base.ROUND else [])+[id]
        q.execute(f'UPDATE {prefix}{table} SET {field}=%s,version=version+1,updater=%s,update_time=CURRENT_TIMESTAMP{extra} WHERE tenant_id=1 AND id=%s AND deleted=0',args)
        base.require(q.rowcount==1,'Unexpected write count')
        if fail: raise RuntimeError('Injected failure')
    verify(before,base.capture(q,prefix),target)
    q.execute(f'SELECT HEX(decision_reason) AS h FROM {prefix}{base.ROUND} WHERE tenant_id=1 AND id=45')
    base.require(q.fetchone()['h']==REASON.encode('utf-8').hex().upper(),'UTF-8 mismatch')
    return True


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--evidence-dir',type=Path,required=True)
    parser.add_argument('--apply',action='store_true')
    args=parser.parse_args()
    environment=os.getenv('ZSJOS_AGENT_ENV')
    if environment is None:
        marker=Path('/etc/zsjos/agent-environment')
        environment=marker.read_text().strip() if marker.exists() else 'local'
    base.require(environment=='test','Only authorized test environment allowed')
    os.umask(0o077)
    args.evidence_dir.mkdir(parents=True,mode=0o700,exist_ok=False)
    cfg=base.db_config('test')
    base.require(cfg.host in ('127.0.0.1','localhost') and cfg.database=='zsjos','Unexpected target')
    con=base.pymysql.connect(host=cfg.host,port=cfg.port,user=cfg.user,password=cfg.password,
        database=cfg.database,charset='utf8mb4',autocommit=False,cursorclass=base.pymysql.cursors.DictCursor)
    try:
        with con.cursor() as q:
            q.execute('SET NAMES utf8mb4'); q.execute("SET time_zone='+08:00'")
            q.execute('START TRANSACTION READ ONLY')
            before=base.capture(q)
            order=base.row(before,base.ORDER,8634); approval=base.row(before,base.ROUND,45)
            opp=base.row(before,base.OPP,order['opportunity_id'])
            target=approval['submitted_at']
            base.require(order['order_no']=='OR202609190004' and order['status']=='effective'
                and order['current_approval_round_id']==45 and approval['order_id']==8634
                and approval['status']=='approved' and approval['process_instance_id'] is None
                and approval['process_definition_key']=='legacy-finance-review' and opp['status']=='won'
                and opp['lead_id']==6159 and opp['person_id']==7661
                and target==order['submitted_at']==dt.datetime(2026,9,19,20,6,3)
                and order['effective_at']==approval['completed_at']==opp['won_at']
                and all(x['updater']==base.MARKER for x in (order,approval,opp)), 'Inspected prerequisite changed')
            con.rollback()
            base.save(args.evidence_dir/'before.json',before)
            prefix='verify_time_'
            for table in base.SCOPE:
                q.execute(f'CREATE TEMPORARY TABLE {prefix}{table} LIKE {table}')
                q.execute("SELECT column_name FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name=%s AND generation_expression='' ORDER BY ordinal_position",(table,))
                cols=[r['COLUMN_NAME'] for r in q.fetchall()]
                if before[table]:
                    q.executemany(f"INSERT INTO {prefix}{table} ({','.join('`'+c+'`' for c in cols)}) VALUES ({','.join(['%s']*len(cols))})",[[r[c] for c in cols] for r in before[table]])
            con.commit()
            try: apply(q,before,target,prefix,fail=True)
            except RuntimeError as e:
                base.require(str(e)=='Injected failure','Unexpected failure'); con.rollback()
            else: raise RuntimeError('Failure injection not reached')
            base.require(base.capture(q,prefix)==before,'Partial state after rollback')
            base.require(apply(q,before,target,prefix),'Initial/recovery failed'); con.commit()
            tested=base.capture(q,prefix)
            base.require(not apply(q,before,target,prefix) and base.capture(q,prefix)==tested,'Repeat wrote data'); con.rollback()
            q.execute(f'UPDATE {prefix}{base.ORDER} SET version=version+1 WHERE tenant_id=1 AND id=8634')
            try: apply(q,before,target,prefix)
            except RuntimeError: con.rollback()
            else: raise RuntimeError('Subsequent edit accepted')
            base.save(args.evidence_dir/'controlled.json',dict(initial=True,repeat=True,failure_rollback=True,recovery=True,stale_refusal=True))
            if not args.apply:
                print('Controlled MySQL checks passed'); return
            q.execute('SET TRANSACTION ISOLATION LEVEL SERIALIZABLE'); con.begin()
            base.require(base.capture(q,lock=True)==before,'Live state changed')
            base.require(apply(q,before,target),'Expected correction')
            after=base.capture(q)
            base.save(args.evidence_dir/'pending-after.json',after)
            con.commit()
            q.execute('START TRANSACTION READ ONLY')
            base.require(base.capture(q)==after,'Committed image differs');con.rollback()
            base.require(not apply(q,before,target),'Repeat must not write');con.rollback()
            base.save(args.evidence_dir/'verified.json',dict(order_no=order['order_no'],target_time=target,rows_updated=3,
                before_sha256=base.digest(before),after_sha256=base.digest(after),protected_rows_unchanged=True,utf8_hex=True,repeat_zero_writes=True))
            print('Verified three timestamps aligned to approval initiation: '+str(target)+' +08:00; protected fields unchanged')
    finally:
        con.rollback();con.close()


if __name__=='__main__':
    try: main()
    except Exception as error:
        print('Correction stopped: '+(str(error) if type(error) is RuntimeError else type(error).__name__))
        raise SystemExit(1)
