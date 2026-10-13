# -*- coding: utf-8 -*-
"""Read-only V312 semantic checks and guarded recovery validation."""
import json
from order_actor_repair import MANIFEST, KEY, MISSING, KEEP


def preflight(c):
    """No DDL, locks, backups or version writes; fails closed on changed inputs."""
    manifest=json.loads(MANIFEST.read_text(encoding='utf-8'))
    with c.cursor() as q:
        q.execute('START TRANSACTION READ ONLY')
        q.execute("SELECT COUNT(*) n FROM zsjos_schema_version WHERE version='V311'");assert q.fetchone()['n']==1
        q.execute("SELECT COUNT(*) n FROM zsjos_module_schema_version WHERE module_code='core' AND version='V311'");assert q.fetchone()['n']==1
        q.execute("SELECT COUNT(*) n FROM zsjos_data_repair_backup WHERE repair_key=%s",(KEY,))
        assert q.fetchone()['n']==0,'Existing repair evidence: use verify or guarded SQL recovery'
        present=0
        for m in manifest['orders']:
            q.execute("""SELECT o.submitter_user_id,o.formal_sales_user_id,o.status,CAST(o.deleted AS UNSIGNED) deleted,
               SHA2(CAST(o.imported_actor_snapshot AS CHAR),256) imported_sha,r.round_no,
               r.submitted_by_user_id,
               r.process_definition_key,r.process_instance_id FROM zsjos_order o
               LEFT JOIN zsjos_order_approval_round r ON r.id=o.current_approval_round_id AND r.order_id=o.id
               AND r.tenant_id=1 AND r.deleted=0 WHERE o.tenant_id=1 AND o.order_no=%s""",(m['order_no'],))
            rows=q.fetchall()
            if not rows:continue
            assert len(rows)==1 and rows[0]['deleted']==0,'Target ambiguous/deleted'
            row=rows[0];present+=1
            assert all(row[k]==m[k] for k in row if k!='deleted'),'Order/round preimage differs from manifest'
            q.execute("SELECT SHA2(nickname,256) name_sha FROM system_users WHERE tenant_id=1 AND id=%s AND deleted=0 AND status=0",(m['target'],))
            user=q.fetchone();assert user and user['name_sha']==m['name_sha'],'Target account changed'
    c.rollback()
    return {'presentOrders':present,'manifestOrders':len(manifest['orders']),'readOnly':True}


def verify(c):
    manifest=json.loads(MANIFEST.read_text(encoding='utf-8'))
    with c.cursor() as q:
        q.execute('START TRANSACTION READ ONLY')
        q.execute("SELECT id,nickname FROM system_users WHERE tenant_id=1 AND deleted=0")
        users={r['id']:r['nickname'] for r in q.fetchall()}
        counts={'orders':0,'roundReferences':0,'missingNames':0,'retainedAliases':0}
        for m in manifest['orders']:
            q.execute("""SELECT o.*,r.submitted_by_user_id,r.order_snapshot,r.process_instance_id
               FROM zsjos_order o LEFT JOIN zsjos_order_approval_round r ON r.id=o.current_approval_round_id
               AND r.order_id=o.id AND r.tenant_id=1 AND r.deleted=0
               WHERE o.tenant_id=1 AND o.deleted=0 AND o.order_no=%s""",(m['order_no'],))
            o=q.fetchone()
            if o is None:continue
            counts['orders']+=1
            assert o['submitter_user_id']==m['target'] and o['formal_sales_user_id']==m['formal_target'], 'Order identity mismatch'
            if m['repair_round']:
                assert not o['process_instance_id'] and o['submitted_by_user_id']==m['target']
                counts['roundReferences']+=1
            if m['group'] in ('correct','missing'):
                repair=json.loads(o['repaired_actor_snapshot']); actor=repair['submitter']
                assert actor['id']==m['target'] and actor['subjectType']=='ADMIN' and actor['name']==users[m['target']]
                assert repair['tenantId']==1 and repair['orderNo']==m['order_no'] and repair['repairKey']==KEY
                if o['order_snapshot']:
                    snap=json.loads(o['order_snapshot']); assert snap['submitter']==actor
                q.execute("SELECT HEX(repaired_actor_snapshot->>'$.submitter.name') h FROM zsjos_order WHERE id=%s AND tenant_id=1",(o['id'],))
                assert q.fetchone()['h']==actor['name'].encode('utf-8').hex().upper()
            if m['group']=='missing':counts['missingNames']+=1
            if m['group']=='keep':counts['retainedAliases']+=1
        q.execute("SELECT COUNT(*) n FROM zsjos_schema_version WHERE version='V312'");assert q.fetchone()['n']==1
        q.execute("SELECT COUNT(*) n FROM zsjos_module_schema_version WHERE module_code='core' AND version='V312'");assert q.fetchone()['n']==1
        q.execute("SELECT a.user_id FROM zsjos_performance_attribution a JOIN zsjos_order o ON o.id=a.fact_id AND o.tenant_id=a.tenant_id WHERE o.order_no='OR202609160008' AND o.tenant_id=1 AND a.fact_type='ORDER' AND a.deleted=0")
        attrs=q.fetchall();assert not attrs or attrs==[{'user_id':30}]
        q.execute("SELECT owner_user_id,source_user_id FROM zsjos_lead WHERE tenant_id=1 AND lead_no='LD202609160052' AND deleted=0")
        lead=q.fetchone();assert lead is None or lead=={'owner_user_id':37,'source_user_id':37}
        q.execute("SELECT r.submitted_by_user_id FROM zsjos_order o JOIN zsjos_order_approval_round r ON r.id=o.current_approval_round_id AND r.tenant_id=o.tenant_id WHERE o.tenant_id=1 AND o.order_no='OD202609212049560024' AND o.deleted=0")
        bpm=q.fetchone();assert bpm is None or bpm['submitted_by_user_id']==27
        q.execute("SELECT submitter_user_id FROM zsjos_order WHERE tenant_id=1 AND order_no='OR202609070005' AND deleted=0")
        ownership=q.fetchone();assert ownership is None or ownership['submitter_user_id']==17
    c.rollback()
    return counts
