# Scoped operator transfer

## Registration — 2026-11-01T10:13:52+08:00

- ID: test-operator-transfer-20261101; owner: /root; environment: test (/etc/zsjos/agent-environment; no environment variable override).
- Branch: main; worktree: /opt/zsjos; base: 0ecc74d4d13bcfbc0361ecdbc2bbd73327359425; target branch/integration: None.
- Goal: user explicitly confirmed the 36 previously identified media services, target operator 45, director 42 unchanged, test zsjos-mysql-1/zsjos tenant 1. Only service/operator, account/owner-operator and group/operator fields plus version/audit metadata may change.
- Non-goals: other same-name records, teaching services, student information, director, positioning cards, content, workflows/history, tasks, permissions, deployment, migrations or schema, Git operations.
- Ownership: this record; script/sql/mysql/repairs/transfer_operators_20261101.py; docs/operations/operator-transfer-20261101.md. Preserve unrelated workbench build-info and self-sourced recovery workstream.
- Dependencies: fixed service/person pairs; enabled target accounts; unique configured director/operator relation; groups not shared with out-of-scope services; prior scoped repair mechanics and installed PyMySQL.
- Verification: private scoped backup and fixed plan, complete before-image guard, MySQL same-schema temporary-table initial/repeat/failure rollback/partial recovery/subsequent-edit rejection/compensating recovery, one live transaction with per-row system audit, independent readback, unchanged-field/history comparisons and UTF-8 HEX. Expected changes: services 26, accounts 51, groups 31.

## Delivery — 2026-11-01T10:18:08+08:00

- Context: owner /root; environment test; branch main; worktree /opt/zsjos; HEAD unchanged 0ecc74d4d13bcfbc0361ecdbc2bbd73327359425; integration None.
- User goal: confirmed operator-only reassignment of the exact reviewed 36 media services; completed.
- Result: 26 service operator fields, 51 account owner-operator fields and 31 group operator fields updated in one transaction, with version/audit metadata and 108 system business events. All 36 services, 71 accounts and 36 groups now have operator 45 and unchanged director 42. Target operator has 43 distinct current service students in total.
- Preservation: captured 50 Persons, 58 services, 52 groups and related media/history records compared field by field; all non-whitelisted fields unchanged. Cards, content, review batches, historical decisions, teaching services and same-name protection objects retained. No permissions, BPM, task, schema, initialization, notification or service mutations.
- Files: script/sql/mysql/repairs/transfer_operators_20261101.py; docs/operations/operator-transfer-20261101.md; this handoff. Existing workbench build-info and separate recovery workstream preserved.
- Verification: Python syntax, UTF-8/whitespace/link checks; MySQL same-schema temporary initial/repeat/failure rollback/partial recovery/subsequent-edit rejection/compensating rollback passed. Initial fixture query hit MySQL temporary-table reopen restriction; fixed by materializing IDs before reads, then reran successfully before real writes. Live preimage guard, commit, zero-update replay and independent committed readback passed. Audit HEX and private evidence permissions passed.
- Evidence: /opt/zsjos-runtime/backups/operator-transfer-20261101-verified-v2 and operator-transfer-20261101-applied (private before/plan/after/result/independent-verification); no sensitive evidence published.
- Limits: no employee login, HTTP or browser acceptance performed; only database ownership verified. Historical positioning/content workflow permissions intentionally remain unchanged under the approved narrow scope. No application code changed; frontend builds and full migration chain checks do not apply.
- Dependency/integration impact: None; no new dependencies, deployment, restart, branch operations, commits or pushes. Remaining work: None in approved scope; ownership released.
