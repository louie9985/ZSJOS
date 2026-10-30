# -*- coding: utf-8 -*-
"""One consistent server snapshot, complete contact components and private plans."""
import argparse
from collections import Counter, defaultdict
import datetime as dt
import hashlib
from pathlib import Path

from server_target import ServerTarget, require, read, save, digest
from group_repair import alive, generated_column
from repair import LIVE, HISTORICAL
from identity_rules import classify_group, SEED_CREATORS
import zsjos_db


def components(people, claims, aliases, events):
    live = {p['id']: p for p in people if alive(p)}
    mappings = defaultdict(set)
    for e in events:
        if alive(e): mappings[e['from_person_id']].add(e['to_person_id'])
    def resolve(pid):
        seen = set()
        while pid not in live and pid in mappings:
            require(pid not in seen and len(mappings[pid]) == 1, 'Ambiguous/cyclic historical mapping')
            seen.add(pid); pid = next(iter(mappings[pid]))
        return pid if pid in live else None
    parent = {pid: pid for pid in live}
    def root(pid):
        while parent[pid] != pid:
            parent[pid] = parent[parent[pid]]; pid = parent[pid]
        return pid
    owners = defaultdict(set)
    for p in people:
        pid = resolve(p['id'])
        if pid:
            for field in ('mobile', 'wechat_id'):
                value = (p.get(field) or '').strip().lower()
                if value: owners[value].add(pid)
    for row in (*claims, *aliases):
        pid = resolve(row['person_id'])
        if alive(row) and pid and (row.get('contact_value') or '').strip():
            owners[row['contact_value'].strip().lower()].add(pid)
    for ids in owners.values():
        ids = sorted(ids)
        for pid in ids[1:]: parent[root(pid)] = root(ids[0])
    groups = defaultdict(list)
    for pid in sorted(live): groups[root(pid)].append(pid)
    return sorted((g for g in groups.values() if len(g) > 1), key=min), resolve


def decide(members, people, orders, leads, confirmations):
    selected = [p for p in people if p['id'] in members]
    numbers = {p['person_no'] for p in selected}
    deferred = set(confirmations['deferPersonNos'])
    if numbers & deferred:
        return {'status': 'DEFER', 'reason': 'CONFIRMED_DEFER_MEMBER_PRESENT'}
    for entry in confirmations['names']:
        confirmed = set(entry['personNos'])
        if numbers & confirmed:
            if numbers != confirmed:
                return {'status': 'REVIEW', 'reason': 'MANUAL_MEMBER_SET_CHANGED'}
            name = entry['canonicalName']
            matching = [o for o in orders if o['person_id'] in members and alive(o)
                        and o.get('creator') not in SEED_CREATORS and (o.get('student_name') or '').strip() == name]
            target = (min(matching, key=lambda o:o['id'])['person_id'] if matching else
                      min((p['id'] for p in selected if (p.get('name') or '').strip() == name), default=min(members)))
            return {'status':'READY', 'reason':'USER_CONFIRMED_NAME', 'confirmation': {
                'memberIds':members, 'targetPersonId':target, 'canonicalName':name,
                'reviewReference': confirmations['reviewReference'] + ':' + digest(sorted(numbers))},
                'targetPersonId':target}
    rule = classify_group(selected, [{**o,'deleted':not alive(o)} for o in orders],
                          [{**l,'deleted':not alive(l)} for l in leads])
    return {'status':'READY' if rule['identityDecision']=='AUTO_MERGE' else 'REVIEW',
            'reason':rule['identityRule'], 'rule':rule, 'targetPersonId':rule['targetPersonId']}


def checksum_audit(tables):
    migrations = {(m.module,m.version):m for k,v in zsjos_db.load_manifests().items()
                  for m in zsjos_db.migrations_for(k,v)}
    result = []
    for ledger in ('zsjos_schema_version','zsjos_module_schema_version'):
        for row in tables[ledger]:
            key = (row.get('module_code','core'),row['version'])
            migration = migrations.get(key)
            if not migration: status='NO_CURRENT_SOURCE'
            elif zsjos_db.applied_checksum_matches(migration,row['checksum']): status='FILE_MATCH'
            elif row['checksum']==hashlib.sha256(migration.path.name.encode()).hexdigest(): status='FILENAME_MARKER'
            else: status='DIFFERENT'
            result.append({'ledger':ledger,'module':key[0],'version':key[1], 'status':status,
                           'recorded':row['checksum'],'current':migration.checksum if migration else None,
                           'release':row.get('release_version')})
    return result


def snapshot(target, confirmations, output):
    output.mkdir(mode=0o700, parents=True, exist_ok=False)
    conn = target.connect_readonly()
    tables = {}; manifest = {}; schemas = {}; meta=[]
    try:
        with conn.cursor() as q:
            q.execute("SELECT table_name t,column_name c,extra e,data_type d,ordinal_position pos FROM information_schema.columns WHERE table_schema=DATABASE() ORDER BY table_name,ordinal_position")
            meta = q.fetchall(); by_table=defaultdict(list)
            for col in meta: by_table[col['t']].append(col)
            references = [r for r in meta if r['t'].startswith('zsjos_') and (r['c']=='person_id' or r['c'].endswith('_person_id'))]
            # Copy all owning identity references, dependent task/history/approval tables and permission sources.
            selected = set(LIVE) | HISTORICAL | {r['t'] for r in references} | {
                'zsjos_person','zsjos_person_contact_claim','zsjos_schema_version','zsjos_module_schema_version',
                'zsjos_business_event','zsjos_business_task','system_users','system_role','system_user_role',
                'system_role_menu','system_menu','system_dept','system_user_post'}
            selected |= {t for t,cols in by_table.items() if t.startswith('zsjos_') and any(
                c['c'] in ('lead_id','order_id','service_relation_id','opportunity_id','registration_case_id','task_id') for c in cols)}
            selected |= {t for t in by_table if t.startswith('zsjos_') and any(s in t for s in ('permission','follow_up','contact_record','assignment_history'))}
            selected |= {t for t in by_table if t.startswith(('ACT_HI_','ACT_RU_'))}
            for table in sorted(selected & by_table.keys()):
                cols=by_table[table]
                where=' WHERE tenant_id=1' if any(c['c']=='tenant_id' for c in cols) else ''
                order=' ORDER BY `id`' if any(c['c']=='id' for c in cols) else (' ORDER BY `ID_`' if any(c['c']=='ID_' for c in cols) else '')
                q.execute(f'SELECT * FROM `{table}`'+where+order);rows=list(q.fetchall())
                # Stable full-row comparison also covers tables without a numeric primary key.
                if not order: rows.sort(key=digest)
                q.execute(f'SHOW CREATE TABLE `{table}`');ddl=list(q.fetchone().values())[1]
                schemas[table]=ddl
                save(output/(table+'.json.gz'), rows)
                manifest[table]={'rows':len(rows),'fingerprint':digest(rows)}
                if table in {'zsjos_person','zsjos_person_contact_claim','zsjos_person_contact_alias',
                             'zsjos_person_merge_event','zsjos_lead','zsjos_order','zsjos_schema_version','zsjos_module_schema_version'}:
                    tables[table]=rows
            q.execute('SELECT tenant_id,COUNT(*) n FROM zsjos_person GROUP BY tenant_id')
            require(all(r['tenant_id']==1 for r in q.fetchall()), 'Unexpected Person tenant')
    finally: conn.rollback();conn.close()
    groups,resolve = components(tables['zsjos_person'],tables['zsjos_person_contact_claim'],
                               tables.get('zsjos_person_contact_alias',[]),tables['zsjos_person_merge_event'])
    people = tables['zsjos_person']; by_id={p['id']:p for p in people}; result=[]
    deferred_targets={resolve(p['id']) for p in people if p['person_no'] in confirmations['deferPersonNos']}
    confirmed_numbers={n for entry in confirmations['names'] for n in entry['personNos']}
    archived_confirmed_targets={resolve(p['id']) for p in people
                                if p['person_no'] in confirmed_numbers and not alive(p)}
    for members in groups:
        decision=decide(members,people,tables['zsjos_order'],tables['zsjos_lead'],confirmations)
        if set(members) & deferred_targets:
            decision={'status':'DEFER','reason':'CONFIRMED_DEFER_MEMBER_OR_MAPPING_PRESENT'}
        elif set(members) & archived_confirmed_targets:
            decision={'status':'REVIEW','reason':'MANUAL_MEMBER_ALREADY_ARCHIVED_OR_MERGED'}
        number_set=sorted(by_id[i]['person_no'] for i in members)
        result.append({'memberIds':members,'personNos':number_set,
                       'groupKey':digest([target.binding,number_set]), **decision})
    lookup=[]
    for number in sorted(set(confirmations['deferPersonNos']) | {n for entry in confirmations['names'] for n in entry['personNos']}):
        matches=[p for p in people if p['person_no']==number]
        require(len(matches)<=1,'Duplicate stable Person number')
        lookup.append({'personNo':number,'status':'MISSING' if not matches else 'LIVE' if alive(matches[0]) else 'ARCHIVED',
                       'resolvedPersonNo':by_id[resolve(matches[0]['id'])]['person_no'] if matches and resolve(matches[0]['id']) else None})
    audit={'target':target.binding,'generatedAt':dt.datetime.now(dt.timezone.utc).isoformat(),
           'groups':result,'confirmationLookup':lookup,'confirmationHash':digest(confirmations),
           'tables':manifest,'schemas':schemas,'metadata':meta,'references':references,
           'checksums':checksum_audit(tables),'applicationCommitted':False}
    save(output/'audit.json',audit)
    summary={'groups':len(result),'people':sum(len(g['memberIds']) for g in result),
             'decisions':dict(Counter(g['status'] for g in result)), 'snapshotTables':len(manifest),
             'snapshotRows':sum(v['rows'] for v in manifest.values()), 'checksumStatuses':dict(Counter(r['status'] for r in audit['checksums'])),
             'confirmationLookupStatuses':dict(Counter(r['status'] for r in lookup))}
    save(output/'summary.json',summary)
    return summary


if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--binding',type=Path,required=True)
    p.add_argument('--confirmations',type=Path,required=True);p.add_argument('--output',type=Path,required=True)
    a=p.parse_args();print(__import__('json').dumps(snapshot(ServerTarget(read(a.binding)),read(a.confirmations),a.output)))
