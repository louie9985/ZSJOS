#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Scoped user-confirmed zero targets; see weekly-zero-targets-20261016.md."""
import argparse
import datetime as dt
import json
import os
from pathlib import Path
from transfer_student_20261015 import connect

USERS = (294, 309, 529)
START = dt.date(2026, 10, 5)
MARKER = 'zero-week-targets-20261016'
REASON = '按用户确认补录2026年10月5日至11日周目标：保底、冲刺各0元；系统数据修复'
TARGET = 'zsjos_performance_target'
REVISION = 'zsjos_performance_revision'


def require(ok, message):
    if not ok:
        raise RuntimeError(message)


def capture(cur, lock=False):
    cur.execute(f"SELECT * FROM {TARGET} WHERE tenant_id=1 AND period_type='week' AND period_start=%s ORDER BY id" + (' FOR UPDATE' if lock else ''), (START,))
    return cur.fetchall()


def scope(cur):
    cur.execute("SELECT id,dept_id FROM system_users WHERE tenant_id=1 AND id IN (294,309,529) AND deleted=0 AND status=0 FOR UPDATE")
    users = cur.fetchall()
    require({r['id'] for r in users} == set(USERS) and all(r['dept_id']==1021 for r in users), 'Personnel changed')
    cur.execute("SELECT center_id FROM zsjos_performance_org WHERE tenant_id=1 AND kind='DEPT' AND dept_id=1021 AND deleted=0 FOR UPDATE")
    require([r['center_id'] for r in cur.fetchall()]==[1020], 'Mapping changed')
    cur.execute("SELECT COUNT(DISTINCT up.user_id) AS n FROM system_user_post up JOIN system_post p ON p.id=up.post_id WHERE up.user_id IN (294,309,529) AND up.tenant_id=1 AND p.tenant_id=1 AND up.deleted=0 AND p.deleted=0 AND p.code='sales_specialist'")
    require(cur.fetchone()['n']==3, 'Sales membership changed')


def verify(cur, row):
    require(row['deleted']==bytes([0]) and row['floor_amount']==row['sprint_amount']==0
            and row['dept_id']==1021 and row['center_id']==1020 and row['manual']==bytes([1])
            and row['version']==0 and row['creator']==MARKER and row['reason']==REASON,
            'Existing target changed; refusing overwrite')
    cur.execute(f"SELECT * FROM {REVISION} WHERE target_id=%s AND tenant_id=1 AND deleted=0", (row['id'],))
    revs = cur.fetchall()
    require(len(revs)==1, 'Revision changed')
    rev = revs[0]
    saved = json.loads(rev['after_json'])
    require(rev['before_json'] is None and rev['operator_id']==0 and rev['reason']==REASON
            and saved['id']==row['id'] and saved['scopeId']==row['scope_id']
            and saved['floorAmount']==saved['sprintAmount']==0 and saved['tenantId']==1, 'Invalid snapshot')
    for table, col in ((TARGET,'id'),(REVISION,'target_id')):
        cur.execute(f"SELECT HEX(reason) AS h FROM {table} WHERE {col}=%s AND tenant_id=1 AND deleted=0", (row['id'],))
        require(cur.fetchone()['h']==REASON.encode('utf-8').hex().upper(), 'UTF-8 HEX mismatch')


def apply(cur, fail=False):
    before = capture(cur, True)
    count = 0
    for user in USERS:
        old = [r for r in before if r['scope_type']=='USER' and r['scope_id']==user]
        if old:
            require(len(old)==1, 'Unexpected historical target')
            verify(cur, old[0])
            continue
        cur.execute(f"INSERT INTO {TARGET} (scope_type,scope_id,dept_id,center_id,period_type,period_start,floor_amount,sprint_amount,manual,reason,version,creator,updater,tenant_id) VALUES ('USER',%s,1021,1020,'week',%s,0,0,b'1',%s,0,%s,%s,1)", (user,START,REASON,MARKER,MARKER))
        target_id = cur.lastrowid
        saved = dict(id=target_id,scopeType='USER',scopeId=user,deptId=1021,centerId=1020,periodType='week',periodStart=str(START),floorAmount=0,sprintAmount=0,manual=True,reason=REASON,version=0,tenantId=1,creator=MARKER,updater=MARKER)
        cur.execute(f"INSERT INTO {REVISION} (target_id,before_json,after_json,reason,operator_id,creator,updater,tenant_id) VALUES (%s,NULL,%s,%s,0,%s,%s,1)", (target_id,json.dumps(saved,ensure_ascii=False),REASON,MARKER,MARKER))
        count += 1
        if fail and count==2:
            raise RuntimeError('Injected failure')
    after = capture(cur)
    unrelated = lambda rs: [r for r in rs if not (r['scope_type']=='USER' and r['scope_id'] in USERS)]
    require(unrelated(before)==unrelated(after), 'Unrelated targets changed')
    for row in after:
        if row['scope_type']=='USER' and row['scope_id'] in USERS:
            verify(cur,row)
    return count


def controlled(conn,cur):
    global TARGET, REVISION
    original = (TARGET, REVISION)
    TARGET, REVISION = ('verify_zero_week_target', 'verify_zero_week_revision')
    for table, source in zip((TARGET,REVISION), original):
        cur.execute(f'CREATE TEMPORARY TABLE {table} LIKE zsjos.{source}')
    conn.commit()
    require(apply(cur)==3, 'Initial application failed')
    conn.commit()
    require(apply(cur)==0, 'Repeat failed')
    conn.rollback()
    # These names resolve only to this connection's temporary tables.
    cur.execute(f'DELETE FROM {REVISION}')
    cur.execute(f'DELETE FROM {TARGET}')
    conn.commit()
    require(not capture(cur), 'Fixture cleanup failed')
    conn.commit()
    try:
        apply(cur,True)
    except RuntimeError as error:
        require(str(error)=='Injected failure', 'Unexpected failure')
        conn.rollback()
    else:
        raise RuntimeError('Failure injection did not fire')
    require(not capture(cur), 'Rollback failed')
    cur.execute(f'SELECT COUNT(*) AS n FROM {REVISION}')
    require(cur.fetchone()['n']==0, 'Revision rollback failed')
    require(apply(cur)==3, 'Failure recovery failed')
    conn.commit()
    cur.execute(f'DELETE r FROM {REVISION} r JOIN {TARGET} t ON t.id=r.target_id WHERE t.scope_id=529')
    cur.execute(f'DELETE FROM {TARGET} WHERE scope_id=529')
    conn.commit()
    require(apply(cur)==1, 'Partial application recovery failed')
    conn.commit()
    require(apply(cur)==0, 'Recovered repeat failed')
    conn.rollback()
    for table in (REVISION,TARGET):
        cur.execute(f'DROP TEMPORARY TABLE {table}')
    conn.commit()
    TARGET, REVISION = original


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--apply',action='store_true')
    p.add_argument('--evidence-dir',type=Path,required=True)
    args=p.parse_args()
    env=os.environ.get('ZSJOS_AGENT_ENV')
    if env is None:
        env=Path('/etc/zsjos/agent-environment').read_text().strip() if Path('/etc/zsjos/agent-environment').exists() else 'local'
    require(env=='test','Test environment required')
    os.umask(0o077)
    args.evidence_dir.mkdir(mode=0o700,parents=True,exist_ok=False)
    conn=connect('zsjos-mysql-1')
    try:
        with conn.cursor() as cur:
            scope(cur)
            before=capture(cur)
            conn.rollback()
            def persist(name,value):
                (args.evidence_dir/name).write_text(json.dumps(value,ensure_ascii=False,default=lambda x:x.hex() if isinstance(x,bytes) else str(x),indent=2),encoding='utf-8')
            persist('before.json',before)
            controlled(conn,cur)
            scope(cur)
            require(capture(cur,True)==before,'Live prerequisite changed')
            prior=[r for r in before if r['deleted']==bytes([0]) and r['scope_type']=='USER' and r['scope_id'] not in USERS]
            require(len(prior)==7 and sum(r['floor_amount'] for r in prior)==65000 and sum(r['sprint_amount'] for r in prior)==127500,'Previous totals changed')
            n=apply(cur)
            if args.apply:
                conn.commit()
            else:
                conn.rollback()
            after=capture(cur)
            if args.apply:
                require(apply(cur)==0,'Committed repeat failed')
                require(len([r for r in after if r['deleted']==bytes([0]) and r['scope_type']=='USER'])==10,'Incomplete population')
            else:
                require(after==before,'Preview changed live data')
            conn.rollback()
            persist('after.json',after)
            persist('result.json',dict(applied=args.apply,inserted=n if args.apply else 0,controlled='passed',utf8Hex='passed'))
            print('PASS controlled initial/repeat/failure rollback/recovery/partial; '+(f'committed {n} zero targets and revisions, repeat zero mutations, UTF-8 HEX' if args.apply else 'preview left live data unchanged'))
    finally:
        conn.close()


if __name__=='__main__':
    main()
