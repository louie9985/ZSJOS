# Workstream registration — 2026-10-12 17:43 Beijing

- ID/owner: test-lead-invalid-20261012 / Codex /root.
- Environment: test, resolved from /etc/zsjos/agent-environment.
- Branch/worktree/base: main / /opt/zsjos / cf11407a4b8a2d1485165477ad31c6278f2cec58.
- Target branch/integration order: None.
- Goal: User-confirmed restoration of tenant 1 Lead LD202609190220 to invalid and its sole open Opportunity to lost after the earlier recycle repair reset the Lead status.
- Non-goals: Other Leads, ownership, permissions, historical judgments/snapshots, application fixes, services, migrations/ledgers, commits or deployment.
- Ownership: this record; script/sql/mysql/repairs/repair_lead_invalid_20261012.py; docs/operations/lead-invalid-20261012-repair.md; scoped database Lead/Opportunity rows and one new operational audit event.
- Dependencies: Existing MySQL 8 container/configuration and PyMySQL. Explicit user approval covers shared test database writes to this Lead, associated Opportunity and repair audit. Existing independent workstreams and their files remain untouched.
- Verification plan: Revalidate exact target and no orders/pending tasks/appeals/qualification attribution; protected before-images; replay actual repair SQL on connection-local table copies for initial/repeat/rollback/conflict cases; guarded transaction with field-preservation checks; committed readback, UTF-8 HEX, repeat no-op, scoped source checks.
- Decision: User confirms actual historical outcome invalid; imported valid events remain immutable conflicting historical evidence. Repair audit uses actual execution time, not a fabricated historical judgment. Opportunity lost_at records correction time; original Lead qualification time/description and recycle ownership remain unchanged.

## Delivery — 2026-10-12 17:47 Beijing

- Context: registration above; HEAD unchanged; owner Codex /root; ownership released.
- User goal/result: Authorized test database correction committed at 17:46:01. Exactly one Lead submitted → invalid, one initial-conversion Opportunity open → lost, and one new lead_state_repaired audit event. Both versions advanced 0 → 1. Original qualification timestamp, invalid description, dictionary snapshots, recycle ownership and all other nonauthorized fields preserved; historical events untouched.
- Files: script/sql/mysql/repairs/repair_lead_invalid_20261012.py; docs/operations/lead-invalid-20261012-repair.md; this handoff.
- Verification: MySQL 8.4.11 actual-schema connection-local tables passed initial/repeat/conflict checks and injected failure after first write with full rollback/recovery. Real transaction rehearsal rolled back and matched all before-images. Authorized commit, readback, repeat zero-write and independent UTF-8 MySQL client checks passed; stored Chinese HEX decoded correctly. Python syntax, document link, git diff --check and backup permission checks passed. First temporary-table attempt failed on same-name CREATE LIKE; replaced with actual SHOW CREATE TABLE definition and reran all checks successfully before real writes.
- Evidence: protected 0700 directory /opt/zsjos-runtime/backups/lead-invalid-20261012-applied/ contains 0600 before.json, after.json and result.json. Before-image SHA-256 d28ec73bef5fd48ce69b33428d5585347d7d9c735ee9693f85c965b18e8edfaf. No sensitive values included in reports. Earlier rehearsal evidence retained separately.
- Dependencies/integration: None. No new dependency, migration/ledger/schema change, service restart, deployment, commit or push. Unrelated existing changes preserved.
- Remaining scope/limits: Database repair complete. No actual browser or authenticated API check; list qualification projection confirmed from stored status and existing code. Generic timeline pending-state bug, other affected Leads and historical batch repair source are outside the approved single-record operation. No claims of fixing them or rewriting historical event inconsistencies.
