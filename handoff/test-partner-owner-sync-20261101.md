# Linked partner ownership sync

## Registration — 2026-11-01T14:04:15+08:00

- ID test-partner-owner-sync-20261101; owner /root; environment test from /etc/zsjos/agent-environment, no variable override; main /opt/zsjos; base 0ecc74d4d13bcfbc0361ecdbc2bbd73327359425; target branch/integration None.
- Goal: user explicitly requests current ownership synchronization for the 13 linked partner accounts from the preceding media-team transfer. Fixed Person/Partner/target-operator mapping; current bindings authoritative.
- Findings: 12 current ownership rows already match and must remain byte-for-byte unchanged; partner 88 linked to Person 7071 lacks ownership. Insert owner 46, preserving its disabled Partner and login-account states. No activation, new account, rebinding or past business changes.
- Ownership: this file; script/sql/mysql/repairs/sync_partner_owners_20261101.py; docs/operations/partner-owner-sync-20261101.md. Prior transfer and unrelated files untouched.
- Dependencies: existing ownership command semantics (enabled employee with configured query/manage permission; Partner existence, no Partner-status restriction); active unique student binding; fixed current media-service mapping. Target operator grants verified read-only, no permission changes.
- Plan: private scoped backup; insert one ownership with current name snapshot/assigned timestamp, one ownership log and one system business event in one transaction. Ownership log requires non-null executor, so system-maintenance actor 0 is explicit (never an impersonated employee); business event actor NULL.
- Verification: same-engine temporary tables initial/repeat and injected failures after each insert, stale/refused partial state, complete before-image guard, independent committed readback and all 13 mappings, unchanged existing owners/account states/history, snapshot/audit UTF-8 HEX. No DDL/migration, service operation, notification, dependency or Git operation.

## Delivery — 2026-11-01T14:08:11+08:00

- Context: /root; test/main /opt/zsjos; HEAD unchanged 0ecc74d4d13bcfbc0361ecdbc2bbd73327359425; integration None.
- User goal completed: all 13 linked Partner accounts match the preceding four operator groups (43/46/522/59 counts 3/4/3/3).
- Actual mutation: insert Partner 88 ownership to employee 46, one assign ownership log and one NULL-actor system event, one transaction. Domain log actor 0 explicitly denotes maintenance due to non-null schema; no human impersonation. Existing 12 correct ownership rows untouched.
- Preserved: Partner and login disabled state, login-row fingerprints, profiles, student bindings, student services, pre-existing ownership logs and business history. No account activation or password change; no permissions or other student changes.
- Files: script/sql/mysql/repairs/sync_partner_owners_20261101.py; docs/operations/partner-owner-sync-20261101.md; this record. Prior transfer and unrelated dirty files preserved.
- Verification: scoped syntax/UTF-8/whitespace/links; real MySQL temporary initial/repeat, failure after each of three inserts with rollback, later-edit rejection and partial-state rejection passed. Full live preimage guard and atomic commit passed; post-commit replay zero writes. Independent connection verified all 13 mappings and unchanged dependencies; new snapshot/audit HEX and private file permissions passed.
- Evidence: /opt/zsjos-runtime/backups/partner-owner-sync-20261101-{verified,applied}, including independent-verification. No passwords in private backup, only whole login-row SHA-256 fingerprints. No sensitive payload published.
- Limits: browser/employee login/HTTP command not performed; disabled account remains unable to log in. Application code and schema unchanged, frontend builds/full migration chain do not apply. No dependency/service/Git/integration operations.
- Status delivered; ownership released. Remaining work None within approved scope.
