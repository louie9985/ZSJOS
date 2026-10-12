# -*- coding: utf-8 -*-
"""Execute the reviewed migration locally with private evidence and all-table checksums."""
import datetime as dt
import hashlib
import json
from order_actor_repair import connect, run, ROOT, SQL, ident
from order_actor_repair_verify import preflight, verify
from order_actor_repair_test import snapshot, scoped, TABLES
from order_history_repair import private_write, scalar


def save(path, value):
    return private_write(path, json.loads(json.dumps(value, default=scalar, ensure_ascii=False)))


def checksums(c):
    with c.cursor() as q:
        q.execute("SELECT table_name FROM information_schema.tables WHERE table_schema=DATABASE() AND table_type='BASE TABLE' ORDER BY table_name")
        tables=[r['TABLE_NAME'] for r in q.fetchall()]
        result={}
        for table in tables:
            q.execute('CHECKSUM TABLE '+ident(table)+' EXTENDED')
            result[table]=q.fetchone()['Checksum']
    return result


def apply_local():
    c=connect();result=preflight(c)
    assert result['presentOrders']==95,'Reviewed local population changed'
    stamp=dt.datetime.now().strftime('%Y%m%d%H%M%S')
    out=ROOT/'backups/mysql'/('order-actors-v312-local-'+stamp)
    before=snapshot(c);protected_before=checksums(c)
    save(out/'before.json',before)
    save(out/'checksums-before.json',protected_before)
    run(c)
    after=snapshot(c);protected_after=checksums(c)
    save(out/'after.json',after)
    save(out/'checksums-after.json',protected_after)
    counts=scoped(before,after);semantic=verify(c)
    allowed=set(TABLES)|{'zsjos_data_repair_backup','zsjos_schema_version','zsjos_module_schema_version'}
    changed=[t for t in protected_before if protected_before[t]!=protected_after.get(t)]
    assert not set(changed)-allowed,'Protected table changed; inspect private checksum evidence'
    assert protected_before.keys()==protected_after.keys(),'Unexpected table set changed'
    run(c);assert snapshot(c)==after,'Replay changed rows'
    final=verify(c)
    changes={}
    for column in ('submitter_user_id','formal_sales_user_id'):
        changes[column]=sum(a[column]!=b[column] for a,b in zip(before['zsjos_order'],after['zsjos_order']))
    report={'evidence':str(out),'sqlSha256':hashlib.sha256(SQL.read_bytes()).hexdigest(),
            'rowChanges':counts,'identityChanges':changes,'semantic':semantic,
            'protectedTablesChecked':len(protected_before),'changedTables':changed,'replay':'zero-write','final':final}
    private_write(out/'verification.json',report)
    c.close();print(json.dumps(report,ensure_ascii=True))


def finalize_saved_evidence(path):
    """Resume verification after concurrent service activity; never reapply a new plan."""
    from pathlib import Path
    out=Path(path);c=connect()
    before=json.loads((out/'before.json').read_text(encoding='utf-8'))
    after=json.loads((out/'after.json').read_text(encoding='utf-8'))
    # This only works with persisted scalar forms, so compare all records consistently.
    counts=scoped(before,after)
    b=json.loads((out/'checksums-before.json').read_text());a=json.loads((out/'checksums-after.json').read_text())
    allowed=set(TABLES)|{'zsjos_data_repair_backup','zsjos_schema_version','zsjos_module_schema_version'}
    drift=[t for t in b if b[t]!=a.get(t) and t not in allowed]
    assert set(drift)<= {'QRTZ_SCHEDULER_STATE','zsjos_business_audit_log'},'Unexplained protected-table drift'
    semantic=verify(c);stable=snapshot(c);run(c);assert snapshot(c)==stable,'Replay changed business rows'
    changes={column:sum(x[column]!=y[column] for x,y in zip(before['zsjos_order'],after['zsjos_order']))
             for column in ('submitter_user_id','formal_sales_user_id')}
    report={'sqlSha256':hashlib.sha256(SQL.read_bytes()).hexdigest(),'rowChanges':counts,
            'identityChanges':changes,'semantic':semantic,'protectedTablesChecked':len(b),
            'concurrentServiceTables':drift,'replay':'zero-write','final':verify(c)}
    private_write(out/'verification.json',report);c.close();print(json.dumps(report,ensure_ascii=True))


if __name__=='__main__':apply_local()
