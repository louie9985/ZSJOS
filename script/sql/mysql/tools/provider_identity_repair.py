# -*- coding: utf-8 -*-
"""Reviewed sixteen-Lead repair. prepare is DB-read-only; apply requires target approval."""
import argparse
import copy
import hashlib
import json
from pathlib import Path

from order_history_repair import connect, statements, scalar

ROOT = Path(__file__).resolve().parents[4]
KEY = 'provider-identity-20261011'
MANIFEST = Path(__file__).with_name('provider_identity_manifest.json')
SQL = ROOT / 'script/sql/mysql/repairs/provider_identity_20261011.sql'
SOURCE_SHA = 'bb7f6234c81647ea750ad854622478830e9b90f538a4ed02e2e53a4db60c95db'
PROVIDERS = {
    'LD202607080013': (75, 42), 'LD202607100005': (75, 42),
    **{n: (66, 26) for n in ['LD202607130022', 'LD202607160021', 'LD202607170019',
       'LD202607220003', 'LD202607220004', 'LD202607270013', 'LD202607310008',
       'LD202608050002', 'LD202608060022', 'LD202608070021', 'LD202608110024', 'LD202608130016',
       'LD202609180061']},
    'LD202609200039': (64, 31),
}
TWO = {'LD202609180061', 'LD202609200039'}
EXCLUDED = ['LD202608240003', 'LD202609150034']
LEAD_FIELDS = ('id tenant_id deleted lead_no source_type submission_source_type source_user_id source_dept_id '
    'source_provider_recorded source_provider_user_id provider_owner_type provider_owner_id provider_owner_name_snapshot '
    'contribution_user_id_snapshot contribution_user_name_snapshot contribution_dept_id_snapshot '
    'contribution_dept_name_snapshot contribution_supervisor_user_id_snapshot contribution_supervisor_name_snapshot '
    'owner_user_id owner_identity partner_id creator counted_at version updater update_time').split()
ROUND_FIELDS = 'id tenant_id deleted order_id order_snapshot version updater update_time'.split()
FIELDS = {'zsjos_lead': LEAD_FIELDS, 'zsjos_order_approval_round': ROUND_FIELDS}


def canonical(value):
    return json.dumps(value, sort_keys=True, ensure_ascii=False, separators=(',', ':'), default=scalar)


def digest(value):
    return hashlib.sha256(canonical(value).encode('utf-8')).hexdigest()


def literal(value):
    # Hex literals make Chinese and JSON escaping independent of client/backslash SQL modes.
    return 'CONVERT(0x' + canonical(value).encode('utf-8').hex() + ' USING utf8mb4)'


def image(table, alias='x'):
    def value(k):
        v = f'{alias}.`{k}`'
        if k in ('update_time', 'counted_at'):
            return f"DATE_FORMAT({v},'%Y-%m-%d %H:%i:%s.%f')"
        if k in ('deleted', 'source_provider_recorded'):
            return f'CAST({v} AS UNSIGNED)'
        if k == 'order_snapshot':
            return f'CAST({v} AS JSON)'
        return v
    return 'JSON_OBJECT(' + ','.join("'"+k+"',"+value(k) for k in FIELDS[table]) + ')'


def read_original(path):
    import gzip
    import re
    from recover_order_actors import dump_values
    if hashlib.sha256(path.read_bytes()).hexdigest() != SOURCE_SHA:
        raise ValueError('Original source digest differs from reviewed backup')
    # Retain only identity evidence; no password/token/contact payload is returned or printed.
    keep = {'accounts_user': ['id', 'phone'], 'identity_actor': ['id', 'account_id'],
            'employees_employee_profile': ['id', 'actor_id', 'display_name', 'phone'],
            'leads_lead': ['lead_no', 'lead_source_type', 'submitter_id', 'submitter_name_snapshot',
                'media_attribution_employee_id', 'submitter_media_staff_id_snapshot', 'submitter_media_staff_name_snapshot']}
    cols, rows, active = {}, {t: [] for t in keep}, None
    with gzip.open(path, 'rt', encoding='utf-8', errors='strict') as stream:
        for line in stream:
            m = re.match(r'CREATE TABLE `([^`]+)`', line)
            if m:
                active = m[1] if m[1] in keep else None
                if active: cols[active] = []
            elif active:
                m = re.match(r'\s*`([^`]+)`\s+', line)
                if m: cols[active].append(m[1])
                if line.startswith(')'): active = None
            m = re.match(r'INSERT INTO `([^`]+)` VALUES ', line)
            if m and m[1] in keep:
                for values in dump_values(line[m.end():]):
                    if len(values) != len(cols[m[1]]): raise ValueError('Source columns differ')
                    r = dict(zip(cols[m[1]], values))
                    rows[m[1]].append({k: r.get(k) for k in keep[m[1]]})
    return rows


def prepare(c, source):
    old = read_original(source)
    employees = {r['id']: r for r in old['employees_employee_profile']}
    actors = {r['id']: r for r in old['identity_actor']}
    accounts = {r['id']: r for r in old['accounts_user']}
    originals = {r['lead_no']: r for r in old['leads_lead']}
    result = {'key': KEY, 'sourceSha': SOURCE_SHA, 'excludedConfirmedSelf': EXCLUDED, 'unresolved': [], 'rows': [], 'providers': {}}
    try:
        with c.cursor() as q:
            q.execute('START TRANSACTION READ ONLY')
            q.execute('SELECT id,mobile FROM system_users WHERE tenant_id=1 AND deleted=0')
            users = q.fetchall()
            q.execute('SELECT lead_no FROM zsjos_lead WHERE tenant_id=1 AND deleted=0')
            existing = {r['lead_no'] for r in q.fetchall()}
            mapped = {}
            for r in old['leads_lead']:
                if not r['media_attribution_employee_id'] or r['lead_no'] not in existing: continue
                employee = employees.get(r['media_attribution_employee_id'], {})
                account = accounts.get(actors.get(employee.get('actor_id'), {}).get('account_id'), {})
                phones = {v for v in [employee.get('phone'), account.get('phone')] if v}
                matches = [u['id'] for u in users if u['mobile'] and u['mobile'] in phones]
                if len(matches) != 1: result['unresolved'].append(r['lead_no'])
                else: mapped[r['lead_no']] = matches[0]
            for number, (old_id, user_id) in PROVIDERS.items():
                r = originals[number]
                if mapped.get(number) != user_id or r['media_attribution_employee_id'] != str(old_id) or r['submitter_media_staff_id_snapshot'] != str(old_id):
                    raise ValueError('Reviewed employee mapping changed: ' + number)
                name = r['submitter_media_staff_name_snapshot']
                if not name: raise ValueError('Original provider name missing: ' + number)
                q.execute("""SELECT u.id,d.id dept_id,d.name dept_name,d.leader_user_id,s.nickname supervisor_name,
                    SHA2(CONCAT(u.id,':',COALESCE(u.mobile,'')),256) identity_sha
                    FROM system_users u LEFT JOIN system_dept d ON d.id=u.dept_id AND d.tenant_id=1 AND d.deleted=0
                    LEFT JOIN system_users s ON s.id=d.leader_user_id AND s.tenant_id=1 AND s.deleted=0
                    WHERE u.id=%s AND u.tenant_id=1 AND u.deleted=0""", (user_id,))
                org = q.fetchone()
                if not org: raise ValueError('Mapped provider missing')
                result['providers'][str(user_id)] = org
                q.execute(f'SELECT {image("zsjos_lead").replace(chr(37), chr(37)*2)} doc,SHA2(CAST({image("zsjos_lead").replace(chr(37), chr(37)*2)} AS CHAR CHARACTER SET utf8mb4),256) sha FROM zsjos_lead x WHERE tenant_id=1 AND deleted=0 AND lead_no=%s', (number,))
                row = q.fetchone()
                if not row: raise ValueError('Target missing: ' + number)
                before = json.loads(row['doc'])
                if number not in TWO:
                    q.execute("SELECT old_value FROM zsjos_data_repair_backup WHERE repair_key='confirmed-provider-v308' AND table_name='zsjos_lead' AND row_id=%s AND column_name='__after__'", (before['id'],))
                    evidence = q.fetchone()
                    if not evidence or json.loads(evidence['old_value'])['provider_owner_id'] != user_id:
                        raise ValueError('Confirmed V308 provider evidence missing: ' + number)
                patch = dict(source_type='internal_new_media', source_user_id=user_id, source_dept_id=org['dept_id'],
                    source_provider_recorded=1, source_provider_user_id=user_id, provider_owner_type='system_user',
                    provider_owner_id=user_id, provider_owner_name_snapshot=name,
                    contribution_user_id_snapshot=user_id, contribution_user_name_snapshot=name,
                    contribution_dept_id_snapshot=org['dept_id'], contribution_dept_name_snapshot=org['dept_name'],
                    contribution_supervisor_user_id_snapshot=org['leader_user_id'], contribution_supervisor_name_snapshot=org['supervisor_name'])
                if number in TWO: patch['submission_source_type'] = 'sales_self_sourced'
                provenance = {'leadNo': number, 'sourceSha': SOURCE_SHA, 'oldEmployeeId': old_id, 'providerId': user_id,
                    'mapping': 'unique employee/account phone match; values not retained', 'nameSource': 'original lead media staff snapshot',
                    'organizationSource': 'current provider organization at reviewed preparation', 'organization': org,
                    'originalEntry': r['lead_source_type'], 'originalSubmitterId': r['submitter_id'], 'originalSubmitterName': r['submitter_name_snapshot']}
                result['rows'].append({'table': 'zsjos_lead', 'id': before['id'], 'sha': row['sha'], 'patch': patch, 'provenance': provenance})
                q.execute(f"SELECT {image('zsjos_order_approval_round','x').replace(chr(37), chr(37)*2)} doc,SHA2(CAST({image('zsjos_order_approval_round','x').replace(chr(37), chr(37)*2)} AS CHAR CHARACTER SET utf8mb4),256) sha FROM zsjos_order_approval_round x JOIN zsjos_order o ON o.id=x.order_id AND o.tenant_id=x.tenant_id WHERE x.tenant_id=1 AND x.deleted=0 AND o.deleted=0 AND o.lead_id=%s AND JSON_TYPE(JSON_EXTRACT(x.order_snapshot,'$.leadProfile'))='OBJECT'", (before['id'],))
                for rr in q.fetchall():
                    rd = json.loads(rr['doc'])
                    result['rows'].append({'table': 'zsjos_order_approval_round', 'id': rd['id'], 'sha': rr['sha'],
                        'patch': {'order_snapshot.leadProfile.sourceType': 'internal_new_media',
                                  'order_snapshot.leadProfile.sourceLabel': '新媒体提交',
                                  'order_snapshot.leadProfile.sourceSubjectType': 'ADMIN',
                                  'order_snapshot.leadProfile.sourceSubjectId': user_id,
                                  'order_snapshot.leadProfile.sourceUserName': name}, 'provenance': provenance})
            # Do not merge previous V309/r2 decisions into this independent sixteen-row execution.
            from generate_media_assisted_source_sql import MANIFEST as prior
            from source_review_revision import MANIFEST_PATH
            prior_numbers = {n for n, _ in prior} | {r['lead_no'] for r in json.loads(MANIFEST_PATH.read_text(encoding='utf-8'))['leads']}
            if set(PROVIDERS) & prior_numbers: raise ValueError('Overlaps existing V309 decisions')
        return result
    finally: c.rollback()


def run(c):
    with c.cursor() as q:
        for statement in statements(SQL.read_text(encoding='utf-8')): q.execute(statement)


def audit(c):
    try:
        with c.cursor() as q:
            q.execute('START TRANSACTION READ ONLY')
            q.execute("SELECT table_name,row_id,column_name,old_value FROM zsjos_data_repair_backup WHERE repair_key=%s", (KEY,))
            evidence = q.fetchall()
            if not evidence: return {'applied': False, 'evidenceRows': 0}
            saved = json.loads(MANIFEST.read_text(encoding='utf-8'))
            indexed = {(r['table_name'],r['row_id'],r['column_name']):r['old_value'] for r in evidence}
            if len(evidence) != len(saved['rows'])*3+1 or len(indexed) != len(evidence) or indexed.get(('__repair__',0,'__manifest__')) != digest(saved):
                raise ValueError('Repair completion evidence incomplete or changed')
            for r in saved['rows']:
                before = json.loads(indexed[(r['table'],r['id'],'__before__')])
                expected = json.loads(indexed[(r['table'],r['id'],'__after__')])
                provenance = json.loads(indexed[(r['table'],r['id'],'__provenance__')])
                if provenance != r['provenance']: raise ValueError('Repair provenance differs from reviewed manifest')
                q.execute('SELECT SHA2(CAST(CAST(%s AS JSON) AS CHAR CHARACTER SET utf8mb4),256) sha', (canonical(before),))
                if q.fetchone()['sha'] != r['sha']: raise ValueError('Repair before evidence differs from reviewed manifest')
                derived = copy.deepcopy(before)
                derived.update(version=before['version']+1, updater=KEY, update_time=expected['update_time'])
                for path,value in r['patch'].items():
                    target=derived; keys=path.split('.')
                    for key in keys[:-1]: target=target[key]
                    target[keys[-1]]=value
                if derived != expected: raise ValueError('Repair after evidence differs from approved patch')
                q.execute(f"SELECT {image(r['table'])} doc FROM {r['table']} x WHERE tenant_id=1 AND id={r['id']}")
                current = q.fetchone()
                if not current or json.loads(current['doc']) != expected:
                    raise ValueError('Repair postcondition changed: '+r['provenance']['leadNo'])
            return {'applied': True, 'verifiedRows': len(saved['rows']), 'evidenceRows': len(evidence)}
    finally: c.rollback()


if __name__ == '__main__':
    p = argparse.ArgumentParser(); p.add_argument('mode', choices=['prepare', 'preflight', 'audit', 'apply'])
    p.add_argument('--database', default='ruoyi-vue-pro')
    p.add_argument('--source', type=Path, default=Path.home()/'Desktop/parttimecrm-cst+0800-20260920-222103.sql.gz')
    a = p.parse_args(); c = connect(a.database)
    try:
        if a.mode == 'prepare':
            if MANIFEST.exists(): raise ValueError('Reviewed manifest already exists; do not regenerate over later edits')
            data = prepare(c, a.source); MANIFEST.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding='utf-8')
            print({'leads': len(PROVIDERS), 'protectedRows': len(data['rows']), 'unresolved': data['unresolved'], 'manifestSha': digest(data)})
        elif a.mode == 'preflight':
            saved = json.loads(MANIFEST.read_text(encoding='utf-8'))
            current = prepare(c, a.source)
            if current != saved: raise ValueError('Live preparation differs from reviewed manifest; no writes performed')
            print({'ready': True, 'leads': 16, 'approvalSnapshots': len(saved['rows'])-16,
                   'evidenceRows': len(saved['rows'])*3+1, 'manifestSha': digest(saved)})
        elif a.mode == 'apply': run(c); print(audit(c))
        else: print(audit(c))
    finally: c.close()
