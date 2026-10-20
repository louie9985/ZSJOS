# Test legacy won overdue reminder cleanup

## Registration — 2026-10-20T13:38:56.301669+08:00
- Workstream: test-legacy-won-overdue-20261019; owner /root; environment test (resolved environment variable then marker); branch main; worktree /opt/zsjos; base e94bc834e1d4d8d63c53ab5d3ceed7d8fa9a2671. Target branch / integration: None.
- Goal: explicit user confirmation to cancel exactly the 116 reviewed tenant-1 historical imported overdue reminders for won Leads without subsequent follow-up; preserve records. Correct matching historical import eligibility.
- Non-goals: remaining future reminders, active/new follow-ups, Lead/order data, runtime behavior, permissions, numbered migrations/ledgers, deployment/restart, commit/push.
- Ownership: this uniquely owned handoff; script/sql/mysql/repairs/cancel_legacy_won_overdue_20261019.py; script/sql/mysql/repairs/test_cancel_legacy_won_overdue_20261019.py; script/sql/merge/backfill-legacy-followup-reminders.sql; docs/operations/legacy-won-overdue-cleanup.md. Preserve all pre-existing changes and shared handoff.
- Dependencies: current MySQL schema, existing credential loader, explicit 116 Lead-number allowlist; inspect and guard original task provenance, active effective first order, won state, absent later follow-up. Historical preservation policy yields only to this explicit cleanup authorization; runtime post-deal reminders remain allowed.
- Verification: private backup and reviewed plan hash; MySQL temporary-table initial/repeat/partial/recovery/failure rollback/restore; execute changed complete import script on isolated synthetic temporary tables; transaction guard, committed readback and UTF-8 HEX; compare protected Lead/order/follow-up and non-target task data. No frontend change; authenticated browser may remain unverified.

## Verification resource update
- Repair temporary-table verification passed. Full import SQL references identical tables through multiple aliases and UNIONs, prohibited for MySQL TEMPORARY tables (1137). Use a uniquely named retained verify_legacy_won_* synthetic schema for full-script checks, with synthetic fixtures only; repair rehearsal remains session-local. No application-schema DDL or migration-ledger writes.

## Delivery — 2026-10-20T13:45:03.076181+08:00
- Registration context unchanged: /root; test/main /opt/zsjos; HEAD e94bc834e1d4d8d63c53ab5d3ceed7d8fa9a2671; integration None.
- User-confirmed exact 116 historical imported won-Lead reminders cancelled in tenant 1, records retained. Scope used the original allowlist despite execution date advancing; fresh guards passed. Source import now skips won reminders already overdue at import, preserving future/manual reminder semantics.
- Files: cancellation repair and MySQL verification scripts, historical import SQL, operation document and this handoff (ownership listed in registration). No other repository changes.
- PASS: MySQL initial/repeat/failure rollback/partial recovery/restore/stale refusal; full import SQL in synthetic schema; live guarded transaction changed 116, committed independent verification, replay 0; UTF-8 HEX; syntax and scoped diff checks. Target overdue pending 0, won future pending 56 retained; 9,081 non-target follow-up task rows and protected Lead/order/follow-up snapshots unchanged.
- Private evidence: /opt/zsjos-runtime/backups/legacy-won-overdue-20261020-134134/plan.json and verification.json; plan SHA-256 22f52e2b5e23a5e2f2003c532c59866138f67303df227f44b08b7b409ef06be1. Retained synthetic SQL schemas verify_legacy_won_52d31a487bc9 and verify_legacy_won_4b9860505c87 contain synthetic import fixtures.
- Initial full-import temporary-table test hit MySQL 1137 repeated-table-reference limitation; moved only that verification to a unique synthetic schema. No failed live writes.
- Limits: authenticated browser not exercised; no UI/runtime behavior change. No numbered migrations/ledger/checksum changes, service restart/deploy, commit or push. Existing work preserved. Task complete; ownership released.
