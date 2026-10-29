# -*- coding: utf-8 -*-
"""User-confirmed identity rules; identity decisions are distinct from write readiness."""
from itertools import combinations

SEED_CREATORS = {'seed-liudandan','seed-students','seed-llyilin','seed-batch4'}


def text(value):
    return (value or '').strip()


def contacts(person):
    return {text(person.get(k)).lower() for k in ('mobile','wechat_id') if text(person.get(k))}


def classify_group(people, orders, leads=()):
    """Input is a complete contact-connected group, not a selected convenient pair."""
    ids={p['id'] for p in people}
    real=[o for o in orders if o.get('person_id') in ids and not o.get('deleted') and o.get('creator') not in SEED_CREATORS]
    names={text(o.get('student_name')) for o in real if text(o.get('student_name'))}
    result={'identityDecision':'REVIEW','identityRule':'UNRESOLVED','canonicalName':None,'targetPersonId':None,'executionBlockers':[]}
    if len(people)<2 or len(ids)!=len(people):
        result['identityRule']='INVALID_GROUP';return result
    if len(names)>1:
        result['identityRule']='CONFLICTING_ORDER_NAMES';return result
    live_orders=[o for o in orders if o.get('person_id') in ids and not o.get('deleted')]
    live_leads=[l for l in leads if l.get('person_id') in ids and not l.get('deleted')]
    open_leads=[l for l in live_leads if l.get('status') and l['status']!='closed']
    reached={people[0]['id']}
    while True:
        expanded=reached|{p['id'] for p in people if any(contacts(p)&contacts(q) for q in people if q['id'] in reached)}
        if expanded==reached:break
        reached=expanded
    primary=open_leads[0] if not live_orders and len(live_leads)>1 and len(open_leads)==1 and reached==ids else None
    same_name=len({text(p.get('name')) for p in people})==1 and bool(text(people[0].get('name')))
    # Do not turn a transitive phone chain into a single person without shared identity evidence.
    pairwise=all(contacts(a)&contacts(b) for a,b in combinations(people,2))
    if primary:
        rule='SINGLE_OPEN_LEAD_NO_ORDER'
    elif same_name and pairwise:
        values={(text(p.get('mobile')),text(p.get('wechat_id'))) for p in people}
        compatible=lambda a,b: all(not text(a.get(k)) or not text(b.get(k)) or text(a.get(k))==text(b.get(k)) for k in ('mobile','wechat_id'))
        cross=lambda a,b: bool(text(a.get('mobile')) and text(a.get('mobile')).lower()==text(b.get('wechat_id')).lower() or text(a.get('wechat_id')) and text(a.get('wechat_id')).lower()==text(b.get('mobile')).lower())
        if not all(compatible(a,b) or cross(a,b) for a,b in combinations(people,2)):
            result['identityRule']='CONFLICTING_NONEMPTY_CONTACTS';return result
        if len(values)==1 and all(next(iter(values))): rule='EXACT_NAME_MOBILE_WECHAT'
        elif all(any(text(a.get(k)) and text(a.get(k))==text(b.get(k)) for k in ('mobile','wechat_id')) for a,b in combinations(people,2)):
            rule='SAME_NAME_COMPATIBLE_CONTACTS'
        else:rule='SAME_NAME_CROSS_CONTACTS'
    elif len(names)==1 and real and all(text(o.get('student_name')) for o in real):
        rule='CONSISTENT_ORDER_NAME'
    else:
        result['identityRule']='CONTACT_CHAIN_REQUIRES_REVIEW' if same_name and not pairwise else 'NO_CONCLUSIVE_IDENTITY'
        return result
    target=primary['person_id'] if primary else min(real,key=lambda o:o['id'])['person_id'] if real and names else min(ids)
    canonical_name=text(next(p for p in people if p['id']==target).get('name')) if primary else next(iter(names)) if names else text(people[0].get('name'))
    result.update(identityDecision='AUTO_MERGE',identityRule=rule,
                  canonicalName=canonical_name,targetPersonId=target)
    if primary:result['primaryLeadId']=primary['id']
    if len(ids)>2:result['executionBlockers'].append('ATOMIC_GROUP_EXECUTION_REQUIRED')
    if any(len({text(p.get(k)) for p in people if text(p.get(k))})>1 for k in ('mobile','wechat_id')):
        result['executionBlockers'].append('CONTACT_NORMALIZATION_REQUIRED')
    return result
