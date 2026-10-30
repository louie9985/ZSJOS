# -*- coding: utf-8 -*-
"""Read-only late validation against V354 or V355; no source ALTER or row locks."""
import argparse
from pathlib import Path
from server_target import ServerTarget,read,require
from server_audit import components
from repair import LIVE,helpers,fetch
from group_repair import metadata,ALIAS,CLAIM,EVENT


def validate(manifest):
    target=ServerTarget(manifest['target']);c=target.connect_readonly()
    try:
        with c.cursor() as q:
            tables={r['t'] for r in metadata(q)}
            people=fetch(q,'SELECT * FROM zsjos_person WHERE tenant_id=1 ORDER BY id')
            claims=fetch(q,f'SELECT * FROM {CLAIM} WHERE tenant_id=1 ORDER BY id')
            aliases=fetch(q,f'SELECT * FROM {ALIAS} WHERE tenant_id=1 ORDER BY id') if ALIAS in tables else []
            events=fetch(q,f'SELECT * FROM {EVENT} WHERE tenant_id=1 ORDER BY id')
            groups,_=components(people,claims,aliases,events)
            expected={tuple(p['memberIds']) for p in manifest['plans']}|{tuple(g['memberIds']) for g in manifest['heldGroups']}
            require({tuple(g) for g in groups}==expected,'Server conflict components changed')
            for plan in manifest['plans']:
                ids=plan['memberIds'];marks=','.join(['%s']*len(ids))
                state={'zsjos_person':[p for p in people if p['id'] in ids]}
                for table,fields in sorted(LIVE.items()):
                    if table==ALIAS and table not in tables:state[table]=[];continue
                    if table not in tables:continue
                    predicate=' OR '.join(f'`{field}` IN ({marks})' for field in fields)
                    rows=fetch(q,f'SELECT * FROM `{table}` WHERE tenant_id=1 AND ({predicate}) ORDER BY id',ids*len(fields))
                    if table in ('zsjos_lead','zsjos_order'):
                        rows=[{**r,'identity_origin_person_id':r.get('identity_origin_person_id')} for r in rows]
                    state[table]=rows
                values=sorted({(p.get(f) or '').strip() for p in state['zsjos_person'] for f in ('mobile','wechat_id') if (p.get(f) or '').strip()}|
                              {r['contact_value'] for r in state[ALIAS] if not r['deleted'] or r['deleted']==b'\0'})
                predicate=' OR contact_value IN ('+','.join(['%s']*len(values))+')' if values else ''
                state[CLAIM]=fetch(q,f'SELECT * FROM {CLAIM} WHERE tenant_id=1 AND (person_id IN ({marks}){predicate}) ORDER BY id',ids+values)
                state[EVENT]=fetch(q,f'SELECT * FROM {EVENT} WHERE tenant_id=1 AND idempotency_key LIKE %s ORDER BY id',(plan['key']+':%',))
                require(helpers.digest(state)==plan['fingerprint'],'Server group before-image changed; regenerate plan')
        return {'groups':len(manifest['plans']),'heldGroups':len(manifest['heldGroups']),'allBeforeImagesMatch':True,'applicationCommitted':False}
    finally:c.rollback();c.close()


if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--manifest',type=Path,required=True)
    a=p.parse_args();print(__import__('json').dumps(validate(read(a.manifest))))
