# -*- coding: utf-8 -*-
"""Explicit, target-bound identity repair; default is dry-run with rollback.

Requires operator-confirmed source/target/evidence. Never guesses a target from names.
Application execution and source archival require separate explicit flags and plan hash.
"""
import argparse
import hashlib
import importlib.util
import json
from pathlib import Path
from identity_rules import classify_group,SEED_CREATORS

ROOT = Path(__file__).resolve().parents[5]
spec = importlib.util.spec_from_file_location('identity_import_helpers', ROOT / 'script/historical-student-import/import_students.py')
helpers = importlib.util.module_from_spec(spec)
spec.loader.exec_module(helpers)

# Only live owning relationships move. Snapshots, audit records and backups remain historical.
LIVE = {
    'zsjos_person_contact_alias': ['person_id'],
    'zsjos_lead': ['person_id'], 'zsjos_opportunity': ['person_id'], 'zsjos_order': ['person_id'],
    'zsjos_service_relation': ['person_id'], 'zsjos_collaboration_group': ['student_person_id'],
    'zsjos_customer_account': ['person_id'], 'zsjos_media_account': ['student_person_id', 'rebind_target_student_person_id'],
    'zsjos_media_account_student_link': ['student_person_id'], 'zsjos_partner_student_link': ['student_person_id'],
    'zsjos_partner_invitation': ['student_person_id'], 'zsjos_payment_order': ['person_id'],
    'zsjos_purchase_intent': ['person_id'], 'zsjos_student_delivery_plan': ['student_person_id'],
    'zsjos_student_info_form': ['person_id'], 'zsjos_positioning_service_card': ['student_person_id'],
    'zsjos_student_positioning_interview': ['student_person_id'],
    'zsjos_student_positioning_interview_attachment': ['student_person_id'],
    'zsjos_positioning_card': ['student_person_id'], 'zsjos_positioning_card_submission': ['student_person_id'],
    'zsjos_media_student_talk_record': ['student_person_id'], 'zsjos_interview_record': ['student_person_id'],
    'zsjos_content_review_batch': ['student_person_id'],
}
HISTORICAL = {'zsjos_lead_activation', 'zsjos_lead_duplicate_review', 'zsjos_person_merge_event',
              'zsjos_student_archive_revision', 'zsjos_student_entry_command'}


def fetch(cur, sql, args=()):
    cur.execute(sql,args or None)
    return cur.fetchall()


def repair(conn, decision, execute=False, inject_failure=False):
    if 'memberIds' in decision:
        from group_repair import apply_group
        return apply_group(conn,decision,execute,fail_after='zsjos_lead' if inject_failure else None)
    source,target = int(decision['sourcePersonId']),int(decision['targetPersonId'])
    helpers.check(source != target and source > 0 and target > 0, 'Distinct positive identities required')
    helpers.check(bool(str(decision.get('evidence','')).strip()), 'Reviewed identity evidence is required')
    key='person-unify:' + hashlib.sha256(helpers.packed(decision).encode()).hexdigest()
    conn.begin()
    try:
        with conn.cursor() as cur:
            replay=fetch(cur,'SELECT id FROM zsjos_person_merge_event WHERE tenant_id=1 AND idempotency_key=%s',(key,))
            if replay:
                conn.rollback()
                return {'replay':True,'changes':{}}
            people=fetch(cur,'SELECT * FROM zsjos_person WHERE tenant_id=1 AND id IN (%s,%s) ORDER BY id FOR UPDATE',(source,target))
            helpers.check(len(people)==2 and all(not int.from_bytes(p['deleted'],'big') if isinstance(p['deleted'],bytes) else not p['deleted'] for p in people), 'Both identities must be live')
            helpers.check(helpers.digest(people)==decision['personFingerprint'], 'Identity state changed; prepare again')
            by_id={p['id']:p for p in people}
            if decision.get('automaticIdentity'):
                current_orders=fetch(cur,'SELECT id,person_id,student_name,creator FROM zsjos_order WHERE tenant_id=1 AND deleted=0 AND person_id IN (%s,%s) ORDER BY id FOR UPDATE',(source,target))
                current_leads=fetch(cur,'SELECT id,person_id,status FROM zsjos_lead WHERE tenant_id=1 AND deleted=0 AND person_id IN (%s,%s) ORDER BY id FOR UPDATE',(source,target))
                current_rule=classify_group(people,current_orders,current_leads)
                helpers.check(current_rule==decision['automaticIdentity'],'Automatic identity evidence changed; prepare again')
            # This executor only handles compatible contacts; different values need an explicit contact-resolution plan.
            for field in ('mobile','wechat_id'):
                old=(by_id[source].get(field) or '').strip(); new=(by_id[target].get(field) or '').strip()
                helpers.check(not old or not new or old==new, 'Different nonempty contacts require separate review')
            metadata=fetch(cur,"SELECT table_name AS table_name,column_name AS column_name,extra AS extra FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name LIKE 'zsjos_%' AND (column_name='person_id' OR column_name LIKE '%%_person_id')")
            changes={}; before={}
            for column in metadata:
                table,field=column['table_name'],column['column_name']
                if 'GENERATED' in column['extra'] or field=='identity_origin_person_id' or table=='zsjos_person_contact_alias' and field=='source_person_id' or '_bak_' in table or table in HISTORICAL or table=='zsjos_person_contact_claim':
                    continue
                helpers.check(field in LIVE.get(table,[]), 'Unreviewed identity reference column; executor must be reviewed')
                rows=fetch(cur,f'SELECT * FROM `{table}` WHERE tenant_id=1 AND `{field}`=%s ORDER BY id FOR UPDATE',(source,))
                if not rows: continue
                before[table+'.'+field]=rows
            helpers.check(helpers.digest(before)==decision['referenceFingerprint'], 'Related data changed; prepare again')
            if decision.get('canonicalName'):
                real_orders=fetch(cur,"SELECT student_name,creator FROM zsjos_order WHERE tenant_id=1 AND deleted=0 AND person_id IN (%s,%s) ORDER BY id FOR UPDATE",(source,target))
                if decision.get('automaticIdentity'):
                    real_orders=[o for o in real_orders if o['creator'] not in SEED_CREATORS and (o['student_name'] or '').strip()]
                helpers.check(real_orders and all(o['creator'] not in ('seed-liudandan','seed-students','seed-llyilin','seed-batch4') for o in real_orders), 'Real order evidence unavailable')
                helpers.check({(o['student_name'] or '').strip() for o in real_orders}=={decision['canonicalName']}, 'Order names are missing or conflicting')
                cur.execute('UPDATE zsjos_person SET name=%s WHERE tenant_id=1 AND id=%s',(decision['canonicalName'],target))
            for table in ('zsjos_lead','zsjos_order'):
                cur.execute(f'UPDATE `{table}` SET identity_origin_person_id=person_id WHERE tenant_id=1 AND person_id IN (%s,%s) AND identity_origin_person_id IS NULL',(source,target))
            for table_field,rows in before.items():
                table,field=table_field.split('.')
                cur.execute(f'UPDATE `{table}` SET `{field}`=%s WHERE tenant_id=1 AND `{field}`=%s',(target,source))
                helpers.check(cur.rowcount==len(rows), 'Reference update count changed')
                changes[table_field]=len(rows)
            for table_field,rows in before.items():
                table,field=table_field.split('.')
                generated={r['column_name'] for r in fetch(cur,"SELECT column_name AS column_name FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name=%s AND extra LIKE '%%GENERATED%%'",(table,))}
                for original in rows:
                    current=fetch(cur,f'SELECT * FROM `{table}` WHERE tenant_id=1 AND id=%s',(original['id'],))[0]
                    for column,value in original.items():
                        if column in generated or column in ('update_time','identity_origin_person_id'): continue
                        expected=target if column in LIVE[table] and value==source else value
                        helpers.check(current[column]==expected,'Unrelated business field changed during identity repair')
            # Claims keep their original values; refuse third-party ownership rather than stealing it.
            for field in ('mobile','wechat_id'):
                value=(by_id[source].get(field) or by_id[target].get(field) or '').strip()
                if not value: continue
                outsiders=fetch(cur,'SELECT id FROM zsjos_person WHERE tenant_id=1 AND deleted=0 AND id NOT IN (%s,%s) AND (mobile=%s OR LOWER(wechat_id)=LOWER(%s)) FOR UPDATE',(source,target,value,value))
                helpers.check(not outsiders, 'Contact also matches another live identity')
                owners=fetch(cur,'SELECT * FROM zsjos_person_contact_claim WHERE tenant_id=1 AND contact_value=%s FOR UPDATE',(value,))
                helpers.check(not owners or owners[0]['person_id'] in (source,target), 'Third-party or reserved contact claim')
                cur.execute("INSERT INTO zsjos_person_contact_claim(contact_value,person_id,tenant_id,creator,updater) VALUES(%s,%s,1,'person-unify','person-unify') ON DUPLICATE KEY UPDATE person_id=VALUES(person_id)",(value,target))
                cur.execute(f"UPDATE zsjos_person SET `{field}`=%s WHERE tenant_id=1 AND id=%s AND (`{field}` IS NULL OR `{field}`='')",(value,target))
            cur.execute('UPDATE zsjos_person_contact_claim SET person_id=%s WHERE tenant_id=1 AND person_id=%s',(target,source))
            cur.execute("UPDATE zsjos_person SET deleted=b'1',updater='person-unify' WHERE tenant_id=1 AND id=%s",(source,))
            cur.execute("INSERT INTO zsjos_person_merge_event(from_person_id,to_person_id,event_type,reason,source_snapshot,occurred_at,idempotency_key,tenant_id,creator,updater) VALUES(%s,%s,'identity_unification',%s,%s,NOW(),%s,1,'person-unify','person-unify')",(source,target,'Reviewed identity decision',helpers.packed({'decisionHash':key,'references':{field:[row['id'] for row in rows] for field,rows in before.items()}}),key))
            if inject_failure: raise ValueError('Injected rollback verification')
        if execute: conn.commit()
        else: conn.rollback()
        return {'replay':False,'committed':execute,'changes':changes}
    except Exception:
        conn.rollback()
        raise


if __name__=='__main__':
    parser=argparse.ArgumentParser()
    parser.add_argument('--database',required=True)
    parser.add_argument('--decision',required=True,type=Path)
    parser.add_argument('--sha256',required=True)
    parser.add_argument('--apply',action='store_true')
    parser.add_argument('--allow-source-archive',action='store_true')
    args=parser.parse_args()
    raw=args.decision.read_bytes()
    helpers.check(hashlib.sha256(raw).hexdigest()==args.sha256,'Decision hash mismatch')
    decision=json.loads(raw)
    helpers.check(decision['database']==args.database,'Decision targets another database')
    helpers.check(not args.apply or args.allow_source_archive,'Explicit source archival authorization required')
    conn=helpers.connect(args.database)
    try:
        result=repair(conn,decision,args.apply)
        print(json.dumps({k:v for k,v in result.items() if k!='after'}))
    finally:
        conn.close()
