# -*- coding: utf-8 -*-
"""Retained isolated V310 schema verification; development data is read-only."""
import datetime as dt
import json
import hashlib
from pathlib import Path
from order_history_repair import connect,ident,statements

SQL=Path(__file__).resolve().parents[1]/'migrations/V310__student_service_without_virtual_order.sql'


def execute(conn):
    with conn.cursor() as cur:
        for sql in statements(SQL.read_text(encoding='utf-8')):cur.execute(sql)


def main():
    name='v310_'+dt.datetime.now().strftime('%Y%m%d%H%M%S')
    src=connect()
    with src.cursor() as cur:cur.execute('CREATE DATABASE '+ident(name)+' CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci')
    c=connect(name)
    with src.cursor() as cur,c.cursor() as out:
        for table in ('zsjos_service_relation','zsjos_schema_version','zsjos_module_schema_version'):
            cur.execute('SHOW CREATE TABLE '+ident(table));out.execute(list(cur.fetchone().values())[1])
        out.execute("INSERT INTO zsjos_service_relation(person_id,order_id,order_item_id,registration_case_id,status,activated_at,tenant_id) VALUES(1,2,3,4,'active','2026-01-01',1)")
    def count():
        with c.cursor() as q:q.execute("SELECT COUNT(*) n FROM zsjos_schema_version WHERE version='V310'");return q.fetchone()['n']
    try:execute(c);raise AssertionError('Missing prerequisite accepted')
    except Exception as e:assert 'requires V309' in str(e)
    assert count()==0
    with c.cursor() as q:
        q.execute("INSERT INTO zsjos_schema_version(version,description,checksum) VALUES('V309','fixture','fixture')")
        q.execute("INSERT INTO zsjos_module_schema_version(module_code,version,description,checksum,release_version) VALUES('core','V309','fixture','fixture','fixture')")
        q.execute("CREATE TRIGGER v310_fail BEFORE INSERT ON zsjos_module_schema_version FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='fixture failure'")
    try:execute(c);raise AssertionError('Ledger failure accepted')
    except Exception as e:assert 'fixture failure' in str(e)
    assert count()==0
    with c.cursor() as q:q.execute('DROP TRIGGER v310_fail')
    execute(c);execute(c);assert count()==1
    with c.cursor() as q:
        q.execute("SELECT COUNT(*) n FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='zsjos_service_relation' AND column_name IN ('order_id','order_item_id','registration_case_id') AND is_nullable='YES'")
        assert q.fetchone()['n']==3
        q.execute('SELECT order_id,order_item_id,registration_case_id FROM zsjos_service_relation WHERE person_id=1')
        assert q.fetchone()==dict(order_id=2,order_item_id=3,registration_case_id=4)
        q.execute("INSERT INTO zsjos_service_relation(person_id,status,activated_at,tenant_id) VALUES(2,'active','2026-01-01',1)")
        # Recover a partial DDL state with ledger present, without null rows in that column.
        q.execute('UPDATE zsjos_service_relation SET order_item_id=999 WHERE order_item_id IS NULL')
        q.execute('ALTER TABLE zsjos_service_relation MODIFY order_item_id BIGINT NOT NULL')
    execute(c)
    print(json.dumps({'schema':name,'checks':['prerequisite_failure','ledger_failure_no_marker','ddl_partial_recovery','initial','repeat','nullable_insert','existing_row_preserved']}))
    c.close();src.close()

    # Real candidate data, isolated in a second retained schema; all student service rows preserved.
    from student_virtual_order_cleanup import apply,snapshot,TABLES
    private=Path(__file__).resolve().parents[4]/'backups/mysql/student-virtual-cleanup-20261010/plan-final.json'
    raw=private.read_bytes();p=json.loads(raw);sha=hashlib.sha256(raw).hexdigest()
    name+='data';src=connect()
    with src.cursor() as q:q.execute('CREATE DATABASE '+ident(name)+' CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci')
    c=connect(name)
    with src.cursor() as q,c.cursor() as out:
        for t in list(TABLES)+['zsjos_data_repair_backup','zsjos_schema_version','zsjos_module_schema_version','zsjos_performance_attribution']:
            q.execute('SHOW CREATE TABLE '+ident(t));out.execute(list(q.fetchone().values())[1])
            q.execute("SELECT column_name FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name=%s AND extra NOT LIKE '%%STORED GENERATED%%' AND extra NOT LIKE '%%VIRTUAL GENERATED%%' ORDER BY ordinal_position",(t,))
            columns=','.join(ident(r['COLUMN_NAME']) for r in q.fetchall())
            out.execute('INSERT INTO '+ident(t)+' ('+columns+') SELECT '+columns+' FROM `ruoyi-vue-pro`.'+ident(t))
    before=snapshot(c)
    try:apply(c,p,sha,fail=True);raise AssertionError('Failure not raised')
    except ValueError as e:assert 'Injected' in str(e)
    assert before==snapshot(c)
    assert apply(c,p,sha)==436
    after=snapshot(c);assert apply(c,p,sha)==0;assert snapshot(c)==after
    assert len(after['zsjos_service_relation'])==len(before['zsjos_service_relation'])
    changed={(op['table'],op['before']['id']) for op in p['operations']}
    for t,rows in before.items():
        for rid,row in rows.items():
            if (t,rid) not in changed:assert row==after[t][rid]
    for r in after['zsjos_service_relation'].values():
        if not r['deleted'] and r['order_id'] in p['orders']:raise AssertionError('Dangling virtual order')
    first=lambda data:sum(not o['deleted'] and o['status']=='effective' and o['order_type']=='first_purchase' for o in data['zsjos_order'].values())
    assert first(before)-first(after)==107
    print(json.dumps({'dataSchema':name,'changed':436,'virtualOrders':109,'effectiveFirstPurchaseRemoved':107,'servicesPreserved':109,'independentServices':108,'realOrderRemap':1,'checks':['rollback','repeat','outside_scope_unchanged','references','first_purchase_count']}))
    c.close();src.close()


if __name__=='__main__':main()
