# -*- coding: utf-8 -*-
"""V306 execution and isolated tests. SQL owns guards, backups and both ledgers."""
import argparse
import datetime as dt
import json
import hashlib
from pathlib import Path

from order_history_repair import connect, ident, statements, private_write

ROOT=Path(__file__).resolve().parents[4]
SQL=ROOT/'script/sql/mysql/migrations/V306__order_owner_identity_completion.sql'
TABLES=['zsjos_order','zsjos_order_approval_round','zsjos_data_repair_backup','zsjos_schema_version','zsjos_module_schema_version']
KEY='order-identity-v306'


def run(conn):
    with conn.cursor() as cur:
        for statement in statements(SQL.read_text(encoding='utf-8')):cur.execute(statement)


def checksum(conn):
    with conn.cursor() as cur:
        result={}
        for t in TABLES[:2]:
            cur.execute('CHECKSUM TABLE '+ident(t)+' EXTENDED');result[t]=cur.fetchone()['Checksum']
        return result


def row_digest(conn):
    from order_history_repair import schema,normalize
    with conn.cursor() as q:
        types=schema(q);result={}
        for t in TABLES[:2]:
            q.execute('SELECT * FROM '+ident(t)+' ORDER BY id')
            raw=json.dumps([normalize(r,types[t]) for r in q.fetchall()],ensure_ascii=False,sort_keys=True,separators=(',',':'))
            result[t]=hashlib.sha256(raw.encode()).hexdigest()
        return result


def rows_snapshot(conn):
    from order_history_repair import schema,normalize
    with conn.cursor() as q:
        types=schema(q);result={}
        for t in TABLES[:2]:
            q.execute('SELECT * FROM '+ident(t)+' ORDER BY id')
            result[t]={r['id']:normalize(r,types[t]) for r in q.fetchall()}
        return result


def scoped_diff(before,after):
    import copy
    counts={}
    for t,rows in before.items():
        assert rows.keys()==after[t].keys(),'Unexpected inserted/deleted row'
        counts[t]=0
        for rid,old in rows.items():
            new=after[t][rid]
            if old==new:continue
            counts[t]+=1
            allowed={'version','updater','update_time','formal_owner_identity' if t=='zsjos_order' else 'order_snapshot'}
            assert all(old[k]==new[k] for k in old if k not in allowed),'Unapproved column changed'
            if t=='zsjos_order':assert not old['formal_owner_identity'] and new['formal_owner_identity'] in ('sales','education')
            else:
                expected=copy.deepcopy(old['order_snapshot']);actual=new['order_snapshot']
                identity=actual['order']['formalOwnerIdentity']
                expected['order']['formalOwnerIdentity']=identity
                if isinstance(expected.get('formalSales'),dict):expected['formalSales']['identity']=identity
                assert expected==actual,'Unapproved JSON change'
    return counts


def audit(conn):
    with conn.cursor() as cur:
        cur.execute("""SELECT COUNT(*) orders, SUM(o.formal_owner_identity='education') education,
          SUM(o.formal_owner_identity='sales') sales,
          SUM(NOT(o.formal_owner_identity <=> r.order_snapshot->>'$.order.formalOwnerIdentity')) mismatches,
          SUM(JSON_TYPE(r.order_snapshot->'$.formalSales')='OBJECT' AND NOT(o.formal_owner_identity <=> r.order_snapshot->>'$.formalSales.identity')) actor_mismatches
          FROM zsjos_data_repair_backup b JOIN zsjos_order o ON o.id=b.row_id
          JOIN zsjos_order_approval_round r ON r.id=o.current_approval_round_id AND r.tenant_id=o.tenant_id
          WHERE b.repair_key='order-identity-v306' AND b.table_name='zsjos_order' AND b.column_name='__after__'""")
        result={k:int(v or 0) for k,v in cur.fetchone().items()}
        cur.execute("SELECT COUNT(*) n FROM zsjos_schema_version WHERE version='V306'");result['globalLedger']=cur.fetchone()['n']
        cur.execute("SELECT COUNT(*) n FROM zsjos_module_schema_version WHERE version='V306' AND module_code='core'");result['moduleLedger']=cur.fetchone()['n']
        return result


def clone(name):
    source=connect();target=connect()
    with target.cursor() as cur:cur.execute('CREATE DATABASE '+ident(name)+' CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci')
    target.close();target=connect(name)
    with source.cursor() as src,target.cursor() as dst:
        for t in TABLES:
            src.execute('SHOW CREATE TABLE '+ident(t));ddl=list(src.fetchone().values())[1];dst.execute(ddl)
            src.execute('SELECT column_name FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name=%s AND extra NOT LIKE %s ORDER BY ordinal_position',(t,'%GENERATED%'))
            columns=[r['COLUMN_NAME'] for r in src.fetchall()];names=','.join(ident(k) for k in columns)
            dst.execute('INSERT INTO '+ident(t)+' ('+names+') SELECT '+names+' FROM `ruoyi-vue-pro`.'+ident(t))
    source.close();return target


def expect_failure(conn,message):
    try:run(conn)
    except Exception as exc:
        assert message in str(exc), 'Unexpected failure type/code'
        conn.rollback()
        with conn.cursor() as cur:cur.execute("SELECT RELEASE_LOCK(CONCAT('v306:',DATABASE()))")
        return
    raise AssertionError('Expected SQL rejection')


def tests():
    name='v306_'+dt.datetime.now().strftime('%Y%m%d%H%M%S');c=clone(name)
    before=checksum(c);before_rows=row_digest(c);original=rows_snapshot(c)
    with c.cursor() as q:q.execute("UPDATE zsjos_module_schema_version SET version='V305-hidden' WHERE module_code='core' AND version='V305'")
    expect_failure(c,'V306 requires V305');assert checksum(c)==before and audit(c)['globalLedger']==0
    with c.cursor() as q:q.execute("UPDATE zsjos_module_schema_version SET version='V305' WHERE module_code='core' AND version='V305-hidden'")
    with c.cursor() as q:q.execute("CREATE TRIGGER v306_ledger_failure BEFORE INSERT ON zsjos_module_schema_version FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='isolated ledger failure'")
    expect_failure(c,'isolated ledger failure');assert checksum(c)==before and audit(c)['globalLedger']==0
    with c.cursor() as q:q.execute('DROP TRIGGER v306_ledger_failure')
    run(c);after=checksum(c);result=audit(c)
    assert scoped_diff(original,rows_snapshot(c))=={'zsjos_order':286,'zsjos_order_approval_round':286}
    assert result=={'orders':286,'education':2,'sales':284,'mismatches':0,'actor_mismatches':0,'globalLedger':1,'moduleLedger':1},result
    run(c);assert checksum(c)==after
    with c.cursor() as q:
        q.execute("SELECT row_id,old_value FROM zsjos_data_repair_backup WHERE repair_key=%s AND table_name='zsjos_order' AND column_name='__before__' LIMIT 1",(KEY,))
        row=q.fetchone();old=json.loads(row['old_value'])
        q.execute("UPDATE zsjos_order SET formal_owner_identity=%s,version=%s,updater=%s,update_time=%s WHERE id=%s",(old['identity'],old['version'],old['updater'],old['update_time'],row['row_id']))
    run(c);assert checksum(c)==after
    with c.cursor() as q:
        q.execute('SELECT update_time FROM zsjos_order WHERE id=%s',(row['row_id'],));original_time=q.fetchone()['update_time']
        q.execute('UPDATE zsjos_order SET version=version+1,update_time=%s WHERE id=%s',(original_time,row['row_id']))
    expect_failure(c,'before/after conflict')
    with c.cursor() as q:q.execute('UPDATE zsjos_order SET version=version-1,update_time=%s WHERE id=%s',(original_time,row['row_id']))
    # Full restoration is rehearsed and discarded, never exposed as a destructive CLI.
    with c.cursor() as q:
        q.execute('START TRANSACTION')
        for table in TABLES[:2]:
            q.execute("SELECT row_id,old_value FROM zsjos_data_repair_backup WHERE repair_key=%s AND table_name=%s AND column_name='__before__'",(KEY,table))
            rows=q.fetchall()
            for r in rows:
                old=json.loads(r['old_value']);column='formal_owner_identity' if table=='zsjos_order' else 'order_snapshot'
                value=old['identity'] if table=='zsjos_order' else json.dumps(old['snapshot'],ensure_ascii=False)
                q.execute('UPDATE '+ident(table)+' SET '+ident(column)+'=%s,version=%s,updater=%s,update_time=%s WHERE id=%s',(value,old['version'],old['updater'],old['update_time'],r['row_id']))
        assert row_digest(c)==before_rows
        c.rollback()
    assert checksum(c)==after
    c.close()
    empty=name+'_empty';e=connect()
    with e.cursor() as q:
        q.execute('CREATE DATABASE '+ident(empty)+' CHARACTER SET utf8mb4')
        for t in TABLES:q.execute('CREATE TABLE '+ident(empty)+'.'+ident(t)+' LIKE '+ident(name)+'.'+ident(t))
        for t in TABLES[3:]:q.execute('INSERT INTO '+ident(empty)+'.'+ident(t)+' SELECT * FROM '+ident(name)+'.'+ident(t)+" WHERE version='V305'")
    e.close();e=connect(empty)
    try:
        run(e)
    except Exception:
        pass
    else:
        raise AssertionError('V306 must reject a missing historical manifest target')
    assert audit(e)['globalLedger']==0
    e.close()
    print(json.dumps({'schema':name,'emptySchema':empty,'result':result,'checks':['prerequisite','ledger_failure_rollback','initial','repeat','partial_recovery','later_change_rejection','rollback_rehearsal','missing_target_rejected']}))


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('mode',choices=['test','apply-local','verify']);a=p.parse_args()
    if a.mode=='test':tests();return
    c=connect()
    if a.mode=='apply-local':
        # Preserve an additional private copy of complete affected source tables.
        folder=ROOT/'backups/mysql'/('order-identity-v306-'+dt.datetime.now().strftime('%Y%m%d%H%M%S'))
        with c.cursor() as q:
            data={}
            from order_history_repair import normalize,schema
            types=schema(q)
            for t in TABLES[:2]:
                q.execute('SELECT * FROM '+ident(t)+' WHERE tenant_id=1');data[t]=[normalize(r,types[t]) for r in q.fetchall()]
        private_write(folder/'before.json',data)
        before=rows_snapshot(c)
        run(c);first=checksum(c);counts=scoped_diff(before,rows_snapshot(c));run(c);assert checksum(c)==first
        private_write(folder/'verification.json',{'changedRows':counts,'audit':audit(c),'repeatUnchanged':True})
    print(json.dumps(audit(c)));c.close()


if __name__=='__main__':main()
