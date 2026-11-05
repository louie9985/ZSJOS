#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Read-only full test merge inventory against original private pre-merge evidence."""
import argparse
import collections
import os
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'tools/person_identity_repair'))
from server_target import read as historical_read
from identity_rules import contacts, contact_value, contact_connected
from transfer_student_20261015 import connect, require
from link_approved_repurchase_leads_20261105 import environment
from backfill_order_self_source_20261103 import save, encode, rows

ROOT = Path('/opt/zsjos-runtime/backups/person-unification-test-20261030')
REPORTED = {'LD202607220004', 'KZ202610130845270001'}


def assess(before):
    people = before['zsjos_person']
    names = {(p['name'] or '').strip() for p in people}
    raw = [{(p.get(k) or '').strip().lower() for k in ('mobile', 'wechat_id')
            if (p.get(k) or '').strip()} for p in people]
    # Inspect every shared edge, including a suspicious bridge in a 3+ member group.
    shared = {v for v, n in collections.Counter(v for p in people for v in contacts(p)).items() if n > 1}
    raw_shared = {v for v, n in collections.Counter(v for values in raw for v in values).items() if n > 1}
    rejected = sorted(v for v in raw_shared if not contact_value(v))
    suspicious = [v for v in shared if len(v) < 6 or len(set(v)) == 1
                  or v in ('123456', '123456789', '1234567890', '12345678901', '测试', 'test', '微信', '手机号')]
    return dict(persons=len(people), same_name=len(names) == 1,
                valid_contact_connected=contact_connected(people),
                shared_placeholder_values=rejected,
                suspicious_shared_value_count=len(suspicious),
                shared_valid_contact_count=len(shared),
                leads=[l['lead_no'] for l in before.get('zsjos_lead', [])],
                orders=[o['order_no'] for o in before.get('zsjos_order', [])])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--evidence-dir', type=Path, required=True)
    args = parser.parse_args()
    os.umask(0o077)
    args.evidence_dir.mkdir(mode=0o700, parents=True, exist_ok=False)
    manifest = historical_read(ROOT/'execution-manifest.json.gz')
    independent = historical_read(Path('/opt/zsjos-runtime/backups/person-merge-20261104/online/manifest.json.gz'))
    no_event = historical_read(Path('/opt/zsjos-runtime/backups/repurchase-identity-20261029/applied/before.json'))
    c = connect('zsjos-mysql-1')
    try:
        with c.cursor() as q:
            environment(q); c.rollback()
            q.execute('START TRANSACTION WITH CONSISTENT SNAPSHOT, READ ONLY')
            events = rows(q, 'SELECT * FROM zsjos_person_merge_event ORDER BY tenant_id,id')
            require({e['tenant_id'] for e in events} == {1}, 'Additional tenant requires audit scope')
            people = rows(q, 'SELECT * FROM zsjos_person WHERE tenant_id=1 ORDER BY id')
            claims = rows(q, 'SELECT * FROM zsjos_person_contact_claim WHERE tenant_id=1 ORDER BY id')
            aliases = rows(q, 'SELECT * FROM zsjos_person_contact_alias WHERE tenant_id=1 ORDER BY id')
            leads = rows(q, 'SELECT id,person_id,identity_origin_person_id,lead_no,status,deleted '
                         'FROM zsjos_lead WHERE tenant_id=1 ORDER BY id')
            current_people = {p['id']: p for p in people}
            event_by_source = collections.defaultdict(list)
            for event in events:
                event_by_source[event['from_person_id']].append(event)
            assessments = []
            covered = set()
            for plan in manifest['plans']:
                # Use the actual committed-group receipt, not merely an earlier proposal.
                actual = historical_read(ROOT/'approved-maintenance-apply'/(plan['serverGroupKey']+'-before.json.gz'))
                assessment = assess(actual['before'])
                assessment.update(group=plan['serverGroupKey'], kind='bulk_merge',
                                  rule=actual['rule']['identityRule'], current_mapping_active=False)
                for source in actual['sourcePersonIds']:
                    match = [e for e in event_by_source[source] if e['to_person_id'] == actual['targetPersonId']]
                    require(len(match) == 1, 'Missing/ambiguous original merge event')
                    event = match[0]; covered.add(event['id'])
                    assessment['current_mapping_active'] |= event['deleted'] == b'\0'
                assessment['reported_case'] = set(assessment['leads']) == REPORTED
                assessment['current_persons_separate'] = all(current_people[p]['deleted'] == b'\0' for p in actual['memberIds'])
                assessments.append(assessment)
            ip = independent['plan']
            assessment = assess(ip['before'])
            require(ip['confirmation']['reviewReference'] ==
                    'Explicit user merge and canonical-contact instruction, 2026-11-04',
                    'Independent identity review reference changed')
            assessment.update(kind='independent_merge', group=ip['key'],
                              explicit_independent_identity_confirmation=True,
                              review_record='docs/operations/person-merge-20261104.md')
            for source in ip['sourcePersonIds']:
                match = [e for e in event_by_source[source] if e['to_person_id'] == ip['targetPersonId']]
                require(len(match) == 1, 'Independent event not covered')
                covered.add(match[0]['id'])
            assessments.append(assessment)
            require(covered == {e['id'] for e in events}, 'Unreviewed merge event exists')
            unmapped = [p for p in people if p['deleted'] == b'\1' and p['id'] not in event_by_source]
            require(len(unmapped) == 1 and unmapped[0]['updater'] == 'repurchase-identity-20261029'
                    and unmapped[0]['id'] in {p['id'] for p in no_event['zsjos_person']},
                    'Unrecorded archived identity needs additional evidence')
            extra = assess(no_event)
            extra.update(kind='documented_merge_without_event', group='repurchase-identity-20261029')
            assessments.append(extra)
            risky = [a for a in assessments if not a['valid_contact_connected'] or a['suspicious_shared_value_count']]
            unresolved = [a for a in risky if not a.get('reported_case')
                          and not a.get('explicit_independent_identity_confirmation')]
            live_placeholder = dict(
                person_fields=sum(bool((p.get(k) or '').strip()) and not contact_value(p.get(k))
                                  for p in people if p['deleted'] == b'\0' for k in ('mobile', 'wechat_id')),
                claims=sum(e['deleted'] == b'\0' and not contact_value(e['contact_value']) for e in claims),
                aliases=sum(e['deleted'] == b'\0' and not contact_value(e['contact_value']) for e in aliases))
            summary = dict(tenant=1, people_scanned=len(people), merge_events=len(events),
                           active_merge_events=sum(e['deleted'] == b'\0' for e in events),
                           original_bulk_groups=len(manifest['plans']), independent_event_groups=1,
                           additional_documented_no_event_merges=1, all_events_covered=True,
                           all_archived_people_covered=True, audited_groups=len(assessments),
                           groups_without_valid_contact_or_with_suspicious_contact=len(risky),
                           placeholder_only_groups=sum(bool(a['shared_placeholder_values'])
                                                       and not a['valid_contact_connected'] for a in assessments),
                           independently_confirmed_groups_without_shared_contact=sum(
                               bool(a.get('explicit_independent_identity_confirmation'))
                               and not a['valid_contact_connected'] for a in assessments),
                           reported_group_fixed=any(
                               a.get('reported_case') and a['current_persons_separate']
                               and not a['current_mapping_active'] for a in assessments),
                           remaining_high_risk_groups=len(unresolved),
                           other_groups_with_valid_contact_evidence=sum(a['valid_contact_connected']
                                                                        and not a['suspicious_shared_value_count'] for a in assessments),
                           different_name_groups=sum(not a['same_name'] for a in assessments),
                           live_placeholder_records=live_placeholder,
                           limitation='Valid shared contact is evidence, not independent proof of identity; no unrelated split performed.')
            save(args.evidence_dir/'private-state.json', dict(people=people, events=events, claims=claims, aliases=aliases, leads=leads))
            save(args.evidence_dir/'assessment.json', dict(summary=summary, groups=assessments))
            print(encode(dict(summary=summary, remaining_high_risk_groups=unresolved,
                              contact_evidence_exceptions=risky)))
    finally:
        c.rollback(); c.close()


if __name__ == '__main__':
    main()
