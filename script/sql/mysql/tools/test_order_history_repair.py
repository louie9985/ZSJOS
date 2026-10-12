# -*- coding: utf-8 -*-
"""Retained MySQL clone verification for V305. Never mutates the development schema."""
import argparse
import copy
import datetime as dt
import hashlib
import json
from pathlib import Path
import subprocess
from pymysql.cursors import SSDictCursor

from order_history_repair import (connect,ident,schema,normalize,execute,inspect_applied,
                                 SQL_PATH,ROOT,KEY,statements,private_write)
from order_history_repair_model import TABLES,READ_TABLES,canonical


def clone(name,folder):
    # The dump is a private full local snapshot so preservation checks also include
    # dependent records outside the write scope. No source database table is locked.
    path=folder/'before.sql'
    if not path.exists():
        command='MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysqldump --default-character-set=utf8mb4 -uroot --single-transaction --skip-lock-tables --no-tablespaces --set-gtid-purged=OFF ruoyi-vue-pro'
        with path.open('xb') as out:
            result=subprocess.run(['docker','exec','yudao-mysql','sh','-c',command],stdout=out,stderr=subprocess.PIPE)
        if result.returncode:raise ValueError('Private source dump failed')
    conn=connect()
    with conn.cursor() as cur:cur.execute('CREATE DATABASE '+ident(name)+' CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci')
    conn.close()
    with path.open('rb') as stream:
        result=subprocess.run(['docker','exec','-i','yudao-mysql','sh','-c',
          'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql --default-character-set=utf8mb4 -uroot "$@"','--',name],stdin=stream,stdout=subprocess.PIPE,stderr=subprocess.PIPE)
    if result.returncode:raise ValueError('Clone import failed')
    return connect(name)


def hashes(conn):
    with conn.cursor() as cur:
        types=schema(cur);result={}
    for t in types:
        if t in ('zsjos_data_repair_backup','zsjos_schema_version','zsjos_module_schema_version'):continue
        # Server-side checksum avoids transporting large binary workflow archives.
        # Every changed row is additionally verified by a complete canonical image.
        with conn.cursor() as cur:
            cur.execute('CHECKSUM TABLE '+ident(t)+' EXTENDED')
            result[t]=cur.fetchone()['Checksum']
            if result[t] is None:raise ValueError('Table checksum unavailable: '+t)
    return result


def ledgers(conn):
    with conn.cursor() as cur:
        cur.execute("SELECT COUNT(*) n FROM zsjos_schema_version WHERE version='V305'");a=cur.fetchone()['n']
        cur.execute("SELECT COUNT(*) n FROM zsjos_module_schema_version WHERE module_code='core' AND version='V305'");return (a,cur.fetchone()['n'])


def unaffected_rows(conn,plan):
    result={}
    affected={t:{op['before']['id'] for op in plan['operations'] if op['table']==t and 'before' in op} for t in TABLES}
    with conn.cursor() as cur:
        types=schema(cur)
        for t in TABLES:
            cur.execute('SELECT * FROM '+ident(t))
            result[t]={r['id']:normalize(r,types[t]) for r in cur.fetchall() if r['id'] not in affected[t]}
    return result


def expect_failure(fn,reason):
    try:fn()
    except Exception:return
    raise AssertionError('Expected rejection: '+reason)


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--plan',type=Path,required=True)
    parser.add_argument('--reuse-schema')
    parser.add_argument('--resume-applied',action='store_true')
    args=parser.parse_args();raw=args.plan.read_bytes();plan=json.loads(raw);sha=hashlib.sha256(raw).hexdigest()
    folder=args.plan.parent/'tests';folder.mkdir(exist_ok=True)
    name=args.reuse_schema or 'v305_'+dt.datetime.now().strftime('%Y%m%d%H%M%S')
    conn=connect(name) if args.reuse_schema else clone(name,folder);before=hashes(conn)
    untouched_before=unaffected_rows(conn,plan)
    print('CLONE '+name,flush=True)
    # Direct migration rejects a populated environment before DDL/version writes.
    def direct():
        with conn.cursor() as cur:
            cur.execute('SET @v305_executor=NULL')
            for sql in statements(SQL_PATH.read_text(encoding='utf-8')):cur.execute(sql)
    if not args.resume_applied:
        expect_failure(direct,'private package required');assert ledgers(conn)==(0,0)
        # Removing a prerequisite only on this disposable clone must block all writes.
        with conn.cursor() as cur:cur.execute("UPDATE zsjos_module_schema_version SET version='V304-test-hidden' WHERE module_code='core' AND version='V304'")
        expect_failure(lambda:execute(conn,plan,sha),'missing prerequisite')
        with conn.cursor() as cur:cur.execute("UPDATE zsjos_module_schema_version SET version='V304' WHERE module_code='core' AND version='V304-test-hidden'")
        expect_failure(lambda:execute(conn,plan,sha,fail_after=10),'mid-transaction failure')
        assert before==hashes(conn) and ledgers(conn)==(0,0)
        # Fail the second ledger insert and prove the first ledger and business writes rollback.
        with conn.cursor() as cur:cur.execute("CREATE TRIGGER v305_test_ledger BEFORE INSERT ON zsjos_module_schema_version FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='isolated ledger failure'")
        expect_failure(lambda:execute(conn,plan,sha),'ledger failure')
        with conn.cursor() as cur:cur.execute('DROP TRIGGER v305_test_ledger')
        assert before==hashes(conn) and ledgers(conn)==(0,0)
        print('PASS fail-closed direct path and injected rollback',flush=True)
    result=execute(conn,plan,sha);after=hashes(conn)
    assert inspect_applied(conn,plan,sha)['matchesAfterImages'];assert ledgers(conn)==(1,1)
    changed={t for t in before if before[t]!=after[t]}
    assert changed<=set(TABLES),changed
    untouched_after=unaffected_rows(conn,plan)
    for t,rows in untouched_before.items():
        assert all(untouched_after[t].get(k)==v for k,v in rows.items()),'Unrelated row changed in '+t
    assert execute(conn,plan,sha)['changedOperations']==0
    assert hashes(conn)==after
    print('PASS initial, after-images, unrelated table preservation and repeat',flush=True)
    # Rehearsal restores exact prior business state inside a rolled-back transaction.
    assert execute(conn,plan,sha,rollback_mode=True)['rollbackRehearsal']
    assert hashes(conn)==after
    op=next(x for x in plan['operations'] if x['table']=='zsjos_lead')
    with conn.cursor() as cur:
        cur.execute('UPDATE zsjos_lead SET owner_user_id=%s WHERE id=%s',(op['before']['owner_user_id'],op['before']['id']))
    expect_failure(lambda:execute(conn,plan,sha),'partial column mutation is neither before nor after')
    with conn.cursor() as cur:
        cols=[k for k in op['before'] if k!='id' and op['before'][k]!=op['after'].get(k)];types=schema(cur)
        from order_history_repair import encode
        cur.execute('UPDATE zsjos_lead SET '+','.join(ident(k)+'=%s' for k in cols)+' WHERE id=%s',tuple(encode(op['before'][k],types['zsjos_lead'][k]) for k in cols)+(op['before']['id'],))
    assert execute(conn,plan,sha)['changedOperations']==1
    assert hashes(conn)==after
    print('PASS guarded recovery with version markers and later-change rejection',flush=True)
    # Cross-tenant target is rejected before any mutation.
    corrupt=copy.deepcopy(plan);corrupt['operations'][0]['after']['tenant_id']=2
    expect_failure(lambda:execute(conn,corrupt,sha),'tenant guard')
    # Real lock conflict, not a mock.
    peer=connect(name)
    with peer.cursor() as cur:cur.execute("SELECT GET_LOCK(CONCAT('v305:',DATABASE()),1)")
    expect_failure(lambda:execute(conn,plan,sha),'concurrent repair lock')
    with peer.cursor() as cur:cur.execute("SELECT RELEASE_LOCK(CONCAT('v305:',DATABASE()))")
    peer.close()
    empty=name+'_empty';admin=connect()
    with admin.cursor() as cur:cur.execute('CREATE DATABASE '+ident(empty)+' CHARACTER SET utf8mb4')
    target=connect(empty)
    with conn.cursor() as cur, target.cursor() as out:
        for t in ['zsjos_order','zsjos_data_repair_backup','zsjos_schema_version','zsjos_module_schema_version']:
            cur.execute('SHOW CREATE TABLE '+ident(t));ddl=list(cur.fetchone().values())[1];out.execute(ddl)
        out.execute("INSERT INTO zsjos_schema_version(version,description,checksum) VALUES ('V304','fixture','fixture')")
        out.execute("INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version) VALUES ('core','V304','fixture','fixture','fixture')")
        for sql in statements(SQL_PATH.read_text(encoding='utf-8')):out.execute(sql)
        for sql in statements(SQL_PATH.read_text(encoding='utf-8')):out.execute(sql)
    assert ledgers(target)==(1,1)
    target.close();admin.close();conn.close()
    report={'schema':name,'emptySchema':empty,'counts':result['counts'],'changedTables':sorted(changed),'resumed':args.resume_applied,
            'checks':(['initial','injected_failure_rollback','prerequisite','ledger_failure','private_package_required'] if not args.resume_applied else [])+['repeat','unrelated_tables','unrelated_rows','postimages','recovery','rollback_rehearsal','tenant','lock_conflict','empty_repeat']}
    private_write(folder/(name+'.json'),report);print(json.dumps(report),flush=True)


if __name__=='__main__':main()
