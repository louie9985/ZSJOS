# -*- coding: utf-8 -*-
"""Retained isolated MySQL copies; never applies the repair to the shared source database."""
import datetime
import json
import uuid
import pymysql
from order_history_repair import connect, ident
from provider_identity_repair import KEY, MANIFEST, PROVIDERS, FIELDS, run, audit

TABLES = ['zsjos_lead', 'zsjos_order', 'zsjos_order_approval_round', 'zsjos_performance_attribution',
          'zsjos_data_repair_backup', 'zsjos_schema_version', 'zsjos_module_schema_version',
          'system_users', 'system_dept', 'zsjos_lead_assignment_history', 'zsjos_opportunity', 'zsjos_order_item']


def clone(empty=False):
    name = 'provider_test_' + datetime.datetime.now().strftime('%Y%m%d%H%M%S') + '_' + uuid.uuid4().hex[:6]
    c = connect()
    with c.cursor() as q:
        q.execute('CREATE DATABASE '+ident(name)+' CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci')
        for table in TABLES:
            dest = ident(name)+'.'+ident(table)
            q.execute('CREATE TABLE '+dest+' LIKE '+ident(table))
            if not empty or table in ('zsjos_schema_version', 'zsjos_module_schema_version'):
                q.execute('SHOW COLUMNS FROM '+ident(table))
                cols = ','.join(ident(r['Field']) for r in q.fetchall() if 'VIRTUAL GENERATED' not in r['Extra'] and 'STORED GENERATED' not in r['Extra'])
                q.execute('INSERT INTO '+dest+' ('+cols+') SELECT '+cols+' FROM '+ident(table))
    c.close()
    return name, connect(name)


def snapshot(c):
    with c.cursor() as q:
        result = {}
        for t in TABLES:
            q.execute('SELECT * FROM '+ident(t)+' ORDER BY 1')
            result[t] = q.fetchall()
        return result


def restore_record(q, item):
    q.execute("SELECT old_value FROM zsjos_data_repair_backup WHERE repair_key=%s AND table_name=%s AND row_id=%s AND column_name='__before__'", (KEY,item['table'],item['id']))
    fields = json.loads(q.fetchone()['old_value'])
    fields.pop('id')
    vals = [json.dumps(v,ensure_ascii=False) if isinstance(v,(dict,list)) else v for v in fields.values()]
    q.execute('UPDATE '+ident(item['table'])+' SET '+','.join(ident(k)+'=%s' for k in fields)+' WHERE id=%s', (*vals,item['id']))


def rejected(c, reason):
    before = snapshot(c)
    try: run(c)
    except pymysql.MySQLError as error: assert reason in str(error), str(error)
    else: raise AssertionError('Expected fail-closed rejection')
    assert snapshot(c) == before, 'Failure modified data or completion evidence'


def verify_v305(c):
    from generate_repair_replay_guards import v305_verifier
    from order_history_repair import statements
    with c.cursor() as q:
        for statement in statements(v305_verifier()): q.execute(statement)
        q.execute('CALL zsjos_v305_completed(@provider_test_completed)')
        q.execute('SELECT @provider_test_completed AS complete')
        assert q.fetchone()['complete'] == 1


def main():
    manifest = json.loads(MANIFEST.read_text(encoding='utf-8'))
    name,c = clone(); print('isolated',name,flush=True)
    before = snapshot(c)
    verify_v305(c)
    with c.cursor() as q:
        q.execute("UPDATE zsjos_schema_version SET version='V308_TEST' WHERE version='V308'")
    rejected(c, 'requires both V308')
    with c.cursor() as q:
        q.execute("UPDATE zsjos_schema_version SET version='V308' WHERE version='V308_TEST'")
        q.execute("CREATE TRIGGER reject_provider_receipt BEFORE INSERT ON zsjos_data_repair_backup FOR EACH ROW BEGIN IF NEW.repair_key='provider-identity-20261011' AND NEW.table_name='__repair__' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='injected completion failure'; END IF; END")
    rejected(c, 'injected completion failure')
    with c.cursor() as q: q.execute('DROP TRIGGER reject_provider_receipt')
    run(c); after = snapshot(c)
    assert audit(c) == {'applied':True,'verifiedRows':32,'evidenceRows':97}
    verify_v305(c)
    allowed = {}
    for r in manifest['rows']: allowed[(r['table'],r['id'])] = {k.split('.')[0] for k in r['patch']} | {'version','updater','update_time'}
    counts = {}
    for table in TABLES:
        if table == 'zsjos_data_repair_backup': continue
        if table in ('zsjos_schema_version', 'zsjos_module_schema_version'):
            assert before[table] == after[table]; continue
        old = {r['id']:r for r in before[table]}; new = {r['id']:r for r in after[table]}
        assert old.keys() == new.keys()
        for rid in old:
            if old[rid] == new[rid]: continue
            assert (table,rid) in allowed,(table,rid)
            changes = {k for k in old[rid] if old[rid][k] != new[rid][k]}
            assert changes <= allowed[(table,rid)],(table,rid,changes)
            counts[table] = counts.get(table,0)+1
            if table == 'zsjos_order_approval_round':
                a,b = json.loads(old[rid]['order_snapshot']),json.loads(new[rid]['order_snapshot'])
                for k in ['sourceType','sourceLabel','sourceSubjectType','sourceSubjectId','sourceUserName']:
                    a['leadProfile'].pop(k,None);b['leadProfile'].pop(k,None)
                assert a == b
    assert counts['zsjos_lead'] == 16
    with c.cursor() as q:
        q.execute("SELECT lead_no,source_user_id,source_provider_user_id,provider_owner_id,contribution_user_id_snapshot,provider_owner_name_snapshot n,HEX(provider_owner_name_snapshot) h FROM zsjos_lead WHERE tenant_id=1 AND lead_no IN ("+','.join(['%s']*16)+')',tuple(PROVIDERS))
        for r in q.fetchall():
            assert r['source_user_id'] == r['source_provider_user_id'] == r['provider_owner_id'] == r['contribution_user_id_snapshot'] == PROVIDERS[r['lead_no']][1]
            assert r['h'] == r['n'].encode('utf-8').hex().upper()
    run(c); assert snapshot(c) == after
    # Account changes after repair must not rewrite the frozen repair-time organization.
    with c.cursor() as q: q.execute('UPDATE system_users SET dept_id=NULL WHERE tenant_id=1 AND id=26')
    changed_org = snapshot(c); run(c); assert snapshot(c) == changed_org
    with c.cursor() as q:
        original = next(r for r in before['system_users'] if r['id']==26)
        q.execute('UPDATE system_users SET dept_id=%s,update_time=%s WHERE id=26', (original['dept_id'],original['update_time']))
        restore_record(q, manifest['rows'][0])
    run(c); assert snapshot(c) == after
    # Exact rollback rehearsal, retaining evidence; no destructive cleanup.
    with c.cursor() as q:
        q.execute('START TRANSACTION')
        for r in manifest['rows']: restore_record(q,r)
    restored = snapshot(c)
    for t in FIELDS: assert restored[t] == before[t]
    c.rollback(); assert snapshot(c) == after
    with c.cursor() as q:
        q.execute("UPDATE zsjos_data_repair_backup SET repair_key='hidden_provider_evidence' WHERE repair_key=%s AND column_name='__after__' LIMIT 1",(KEY,))
    rejected(c, 'incomplete or different evidence')
    with c.cursor() as q:
        q.execute("UPDATE zsjos_data_repair_backup SET repair_key=%s WHERE repair_key='hidden_provider_evidence'",(KEY,))
        q.execute("SELECT id,old_value FROM zsjos_data_repair_backup WHERE repair_key=%s AND table_name='zsjos_lead' AND column_name='__after__' LIMIT 1",(KEY,))
        original=q.fetchone()
        q.execute("UPDATE zsjos_data_repair_backup SET old_value=JSON_SET(CAST(old_value AS JSON),'$.source_user_id',999999) WHERE id=%s",(original['id'],))
    rejected(c, 'after evidence differs from approved patch')
    with c.cursor() as q:
        q.execute('UPDATE zsjos_data_repair_backup SET old_value=%s WHERE id=%s',(original['old_value'],original['id']))
        q.execute("UPDATE zsjos_lead SET version=version+1 WHERE lead_no='LD202609180061' AND tenant_id=1")
    rejected(c, 'later edit conflicts')
    print('PASS initial/repeat/failure/completion rollback/partial recovery/evidence/later edits/UTF8/scoped preservation',counts,flush=True)
    c.close()
    empty,ec = clone(True); run(ec); run(ec)
    assert not snapshot(ec)['zsjos_data_repair_backup']; ec.close()
    print('PASS empty repeat',empty,flush=True)


if __name__ == '__main__': main()
