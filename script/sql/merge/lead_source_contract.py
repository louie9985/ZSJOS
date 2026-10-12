# -*- coding: utf-8 -*-
"""Fail-closed source checks for offline imports; no inferred identities or database writes."""
import hashlib
import json
from pathlib import Path

VERSION = 'lead-source-20261011-v2'
SOURCE_REFERENCES = {'source_user_id', 'source_provider_user_id', 'provider_owner_id'}


def null(value):
    return value is None or value in ('NULL', '')


def validate(row):
    source, entry = row.get('source_type'), row.get('submission_source_type')
    selected, provider = row.get('source_provider_user_id'), row.get('provider_owner_id')
    recorded = row.get('source_provider_recorded') in (True, 1, '1', '\x01', b'\x01')
    self_types = ('sales_self_sourced', 'education_self_sourced')
    if source not in self_types and entry not in self_types and not (recorded and not null(selected)):
        return
    reason = None
    if source not in (*self_types, 'internal_new_media'):
        reason = 'source mismatch'
    elif not recorded:
        reason = 'association not confirmed'
    elif null(selected):
        if not null(provider) or not null(row.get('provider_owner_type')) or source not in self_types:
            reason = 'unlinked source has conflicting attribution'
    elif source != 'internal_new_media':
        reason = 'confirmed media acquisition must use the media business source'
    elif row.get('provider_owner_type') != 'system_user' or str(selected) != str(provider):
        reason = 'selected provider differs from formal provider'
    elif any(null(row.get(k)) or str(row[k]) != str(selected)
             for k in ('source_user_id', 'contribution_user_id_snapshot')):
        reason = 'business source or contribution differs from selected provider'
    if reason:
        raise ValueError(f"Lead {row.get('lead_no', '(business number missing)')}: {reason}; explicit source review required")


def validate_import(row, decisions=None):
    """Known human corrections override the original dump; unknown linked imports require review."""
    validate(row)
    if decisions is None:
        directory = Path(__file__).resolve().parents[1] / 'mysql/tools'
        repair = json.loads((directory / 'provider_identity_manifest.json').read_text(encoding='utf-8'))
        decisions = {r['provenance']['leadNo']: r['patch']['provider_owner_id']
                     for r in repair['rows'] if r['table'] == 'zsjos_lead'}
        decisions.update({n: None for n in repair['excludedConfirmedSelf']})
        confirmed = json.loads((directory / 'source_review_manifest.json').read_text(encoding='utf-8'))
        decisions.update({r['lead_no']: r['provider'] for r in confirmed['leads']})
    number = row.get('lead_no')
    selected = row.get('source_provider_user_id')
    if number in decisions:
        expected = decisions[number]
        if expected is None:
            if not null(selected) or not null(row.get('provider_owner_id')) or row.get('source_type') not in ('sales_self_sourced', 'education_self_sourced'):
                raise ValueError(f'Lead {number}: confirmed unassisted decision overrides legacy attribution')
        elif str(row.get('provider_owner_id')) != str(expected):
            raise ValueError(f'Lead {number}: original/confirmed provider differs from imported provider')
    elif not null(selected):
        raise ValueError(f'Lead {number}: linked historical import lacks reviewed original-provider evidence')


def digest(value):
    return hashlib.sha256(json.dumps(value, sort_keys=True, ensure_ascii=True).encode()).hexdigest()


def producer_digest(producer):
    directory = Path(__file__).resolve().parents[1] / 'mysql/tools'
    return digest([Path(producer).read_bytes().hex(), Path(__file__).read_bytes().hex(),
                   (directory / 'provider_identity_manifest.json').read_bytes().hex(),
                   (directory / 'source_review_manifest.json').read_bytes().hex()])


def metadata(sql, producer, source, target, source_snapshot, target_snapshot):
    return {'contract': VERSION, 'producer': producer_digest(producer), 'sql': hashlib.sha256(sql.encode('utf-8')).hexdigest(),
            'source': source, 'target': target, 'source_snapshot': source_snapshot, 'target_snapshot': target_snapshot}


def verify_metadata(saved, expected):
    if saved != expected:
        raise ValueError('Import payload is stale, unreviewed, or belongs to a different source/target; regenerate and review')


# A temporary CHECK constraint aborts the transactional import before COMMIT on any invalid target Lead.
POSTCHECK_SQL = """
CREATE TEMPORARY TABLE source_contract_postcheck(n BIGINT CHECK(n=0));
INSERT INTO source_contract_postcheck
SELECT COUNT(*) FROM zsjos_lead WHERE tenant_id=1 AND deleted=0
AND (source_type IN ('sales_self_sourced','education_self_sourced')
 OR submission_source_type IN ('sales_self_sourced','education_self_sourced')
 OR (source_provider_recorded=1 AND source_provider_user_id IS NOT NULL))
AND (source_type NOT IN ('sales_self_sourced','education_self_sourced','internal_new_media')
 OR COALESCE(source_provider_recorded,0)<>1
 OR (source_provider_user_id IS NULL AND (provider_owner_type IS NOT NULL OR provider_owner_id IS NOT NULL OR source_type='internal_new_media'))
 OR (source_provider_user_id IS NOT NULL AND (source_type<>'internal_new_media' OR NOT(provider_owner_type <=> 'system_user') OR NOT(source_provider_user_id <=> provider_owner_id)
 OR NOT(source_provider_user_id <=> source_user_id) OR NOT(source_provider_user_id <=> contribution_user_id_snapshot))));
DROP TEMPORARY TABLE source_contract_postcheck;
"""
