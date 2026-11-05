# -*- coding: utf-8 -*-
"""User-confirmed identity rules; identity decisions are distinct from write readiness."""
from itertools import combinations
import re

SEED_CREATORS = {'seed-liudandan','seed-students','seed-llyilin','seed-batch4'}

# Missing-value labels are not identity evidence, including across mobile/WeChat.
CONTACT_PLACEHOLDERS = frozenset({'无', '暂无', '未知', '未填写', '未提供', '没有',
    '无微信', '无手机号', '不详', '待补充', 'null', 'none', 'n/a', 'na', '0', '1'})


def text(value):
    return (value or '').strip()


def contact_value(value):
    value = text(value).lower()
    return '' if not value or value in CONTACT_PLACEHOLDERS or re.fullmatch(r'[\W_]+', value) else value


def contacts(person):
    return {contact_value(person.get(k)) for k in ('mobile','wechat_id')} - {''}


def contact_connected(people):
    if not people:
        return False
    reached = {people[0]['id']}
    while True:
        expanded = reached | {p['id'] for p in people if any(
            contacts(p) & contacts(q) for q in people if q['id'] in reached)}
        if expanded == reached:
            return len(reached) == len(people)
        reached = expanded


def classify_group(people, orders, leads=()):
    """Input is a complete contact-connected group, not a selected convenient pair."""
    ids={p['id'] for p in people}
    real=[o for o in orders if o.get('person_id') in ids and not o.get('deleted') and o.get('creator') not in SEED_CREATORS]
    names={text(o.get('student_name')) for o in real if text(o.get('student_name'))}
    result={'identityDecision':'REVIEW','identityRule':'UNRESOLVED','canonicalName':None,'targetPersonId':None,'executionBlockers':[]}
    if len(people)<2 or len(ids)!=len(people):
        result['identityRule']='INVALID_GROUP';return result
    if not contact_connected(people):
        result['identityRule']='NO_VALID_CONTACT_CONNECTION';return result
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
        values={(contact_value(p.get('mobile')),contact_value(p.get('wechat_id'))) for p in people}
        compatible=lambda a,b: all(not contact_value(a.get(k)) or not contact_value(b.get(k)) or contact_value(a.get(k))==contact_value(b.get(k)) for k in ('mobile','wechat_id'))
        cross=lambda a,b: bool(contact_value(a.get('mobile')) and contact_value(a.get('mobile'))==contact_value(b.get('wechat_id')) or contact_value(a.get('wechat_id')) and contact_value(a.get('wechat_id'))==contact_value(b.get('mobile')))
        if not all(compatible(a,b) or cross(a,b) for a,b in combinations(people,2)):
            result['identityRule']='CONFLICTING_NONEMPTY_CONTACTS';return result
        if len(values)==1 and all(next(iter(values))): rule='EXACT_NAME_MOBILE_WECHAT'
        elif all(any(contact_value(a.get(k)) and contact_value(a.get(k))==contact_value(b.get(k)) for k in ('mobile','wechat_id')) for a,b in combinations(people,2)):
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
