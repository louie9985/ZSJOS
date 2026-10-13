# -*- coding: utf-8 -*-
"""Controlled V309 initial/replay/recovery checks. Never applies to the shared development database."""
import json
import pymysql
from test_media_assisted_source_repair import clone, rows
from media_assisted_source_repair import run
from order_history_repair import ident
from source_review_revision import KEY, MANIFEST_PATH, CONTRIBUTION


def evidence(c):
    with c.cursor() as q:
        q.execute('SELECT * FROM zsjos_data_repair_backup WHERE repair_key=%s ORDER BY id',(KEY,))
        return q.fetchall()


def fail(c):
    try: run(c)
    except pymysql.MySQLError: return
    raise AssertionError('Expected controlled rejection')


def restore_one(c, table, row_id):
    with c.cursor() as q:
        q.execute("SELECT old_value FROM zsjos_data_repair_backup WHERE repair_key=%s AND table_name=%s AND row_id=%s AND column_name='__before__'",(KEY,table,row_id))
        fields=json.loads(q.fetchone()['old_value'])
        vals=[json.dumps(v,ensure_ascii=False) if isinstance(v,(dict,list)) else v for v in fields.values()]
        q.execute('UPDATE '+ident(table)+' SET '+','.join(ident(k)+'=%s' for k in fields)+' WHERE id=%s',tuple(vals)+(row_id,))


def main():
    name,c=clone();print('isolated',name,flush=True)
    before=rows(c)
    with c.cursor() as q:q.execute("UPDATE zsjos_schema_version SET version='V308_TEST' WHERE version='V308'")
    fail(c);assert rows(c)==before and not evidence(c)
    with c.cursor() as q:q.execute("UPDATE zsjos_schema_version SET version='V308' WHERE version='V308_TEST'")

    with c.cursor() as q:
        q.execute("CREATE TRIGGER reject_source_review BEFORE INSERT ON zsjos_module_schema_version FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='injected second ledger failure'")
    fail(c);assert rows(c)==before and not evidence(c)
    with c.cursor() as q:
        q.execute("SELECT COUNT(*) n FROM zsjos_schema_version WHERE version='V309'");assert q.fetchone()['n']==0
        q.execute('DROP TRIGGER reject_source_review')
    run(c);after=rows(c);saved=evidence(c)
    manifest=json.loads(MANIFEST_PATH.read_text(encoding='utf-8'))
    targets=[{'table':r['table_name'],'id':r['row_id']} for r in saved if r['column_name']=='__before__']
    assert len(saved)==len(targets)*3
    assert sum(r['table']=='zsjos_lead' for r in targets)==83
    leads={r['lead_no']:r for r in after['zsjos_lead'].values()}
    for decision in manifest['leads']:
        l=leads[decision['lead_no']];assert l['source_provider_recorded'] in (1,b'\x01')
        assert l['source_provider_user_id']==decision['provider']
        if decision['provider'] is None:
            assert l['source_type']==decision['entry'] and l['provider_owner_id'] is None and l['provider_owner_type'] is None
            assert l['provider_owner_name_snapshot'] is None and all(l[k] is None for k in CONTRIBUTION)
        else:
            assert l['source_type']=='internal_new_media'
            assert l['source_user_id']==l['provider_owner_id']==l['contribution_user_id_snapshot']==decision['provider']
    assert after['zsjos_order']==before['zsjos_order']
    allowed={'zsjos_lead':set(['source_dept_id','source_type','submission_source_type','source_user_id','source_provider_recorded','source_provider_user_id','provider_owner_type','provider_owner_id','provider_owner_name_snapshot','version','updater','update_time',*CONTRIBUTION]),
             'zsjos_order_approval_round':{'order_snapshot','version','updater','update_time'},
             'zsjos_performance_attribution':{'source_group','updater','update_time'}}
    keys={'sourceType','sourceLabel','sourceSubjectType','sourceSubjectId','sourceUserName'}
    counts={}
    for table,cols in allowed.items():
        changed=[k for k in before[table] if before[table][k]!=after[table][k]];counts[table]=len(changed)
        for k in changed:
            a,b=before[table][k],after[table][k]
            assert all(a.get(f)==b.get(f) for f in a.keys()|b.keys() if f not in cols),(table,k)
            if table=='zsjos_order_approval_round':
                old,new=json.loads(a['order_snapshot']),json.loads(b['order_snapshot'])
                for f in keys:old['leadProfile'].pop(f,None);new['leadProfile'].pop(f,None)
                assert old==new
    assert counts['zsjos_lead']==116,counts
    with c.cursor() as q:
        q.execute("SELECT provider_owner_name_snapshot n,HEX(provider_owner_name_snapshot) h FROM zsjos_lead WHERE lead_no IN ('LD202609150070','LD202609170048') AND tenant_id=1")
        for r in q.fetchall():assert r['h']==r['n'].encode('utf-8').hex().upper()
    run(c);assert rows(c)==after and evidence(c)==saved
    # A revision on a previously successful V309 must also roll back if ledger UPDATE fails.
    partial=next(r for r in targets if r['table']=='zsjos_lead')
    restore_one(c,'zsjos_lead',partial['id']);partial_state=rows(c)
    with c.cursor() as q:q.execute("CREATE TRIGGER reject_source_review_update BEFORE UPDATE ON zsjos_module_schema_version FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='injected ledger update failure'")
    fail(c);assert rows(c)==partial_state and evidence(c)==saved
    with c.cursor() as q:q.execute('DROP TRIGGER reject_source_review_update')
    run(c);assert rows(c)==after
    # Rehearse protected rollback in a transaction, preserving all evidence for replay.
    with c.cursor() as q:q.execute('START TRANSACTION')
    for item in targets:restore_one(c,item['table'],item['id'])
    c.rollback();assert rows(c)==after and evidence(c)==saved
    for table in allowed:
        item=next(r for r in targets if r['table']==table)
        restore_one(c,table,item['id'])
    run(c);assert rows(c)==after and evidence(c)==saved
    with c.cursor() as q:
        q.execute("UPDATE zsjos_data_repair_backup SET repair_key='source_review_hidden' WHERE repair_key=%s AND column_name='__after__' LIMIT 1",(KEY,))
    fail(c);assert rows(c)==after
    with c.cursor() as q:
        q.execute("UPDATE zsjos_data_repair_backup SET repair_key=%s WHERE repair_key='source_review_hidden'",(KEY,))
        q.execute("UPDATE zsjos_lead SET version=version+1 WHERE tenant_id=1 AND lead_no='LD202609200046'")
    later=rows(c);fail(c);assert rows(c)==later
    # Evidence-backed rollback is also verified by exact restored field values before rolling back the rehearsal.
    print('passed scoped diffs, amount/owner preservation, UTF-8, repeat, partial recovery, evidence loss and later-edit rejection',counts,flush=True)
    c.close()
    empty,ec=clone(empty=True);run(ec);run(ec)
    assert not evidence(ec)
    ec.close();print('passed empty fresh repeat',empty,flush=True)


if __name__=='__main__':main()
