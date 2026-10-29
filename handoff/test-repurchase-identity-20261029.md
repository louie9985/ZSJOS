# Registration — test-repurchase-identity-20261029

- Owner: /root. Environment: test from /etc/zsjos/agent-environment. Branch: main. Worktree: /opt/zsjos. Base: ba3e687e678ce92b67f16615244e47007d0892d4. Target branch/integration: None.
- Goal: prepare a narrowly scoped emergency repair for duplicate customer masters 5311/6005 blocking repurchase. Shared database application requires explicit confirmation of the exact merge below.
- Ownership: this record; script/sql/mysql/repairs/merge_repurchase_identity_20261029.py; docs/operations/repurchase-identity-20261029.md. Private evidence in a task-specific restricted directory under /opt/zsjos-runtime/backups/.
- Scope: tenant 1, retain Person 6005, repoint Lead internal ID 5307, Opportunity 7331 and Order 635 from Person 5311 to 6005, logically delete Person 5311. Preserve owners, amounts, states, business numbers, immutable snapshots and existing student relationships. No hard deletion.
- Non-goals: general deduplication, frontend/backend deployment, permission changes, notification delivery, historical backup rewrites, migration or schema changes, service lifecycle, branches, commits or pushes. Preserve all pre-existing work.
- Dependencies: existing MySQL 8 test database; exact identity equality; contact claim owned by 6005; no additional live references to 5311 or conflicting target Lead/Opportunity; explicit merge authorization pending.
- Verification: read-only reference inventory and preview; MySQL temporary-table rehearsal using exact current source schemas, initial/repeat/failure rollback and recovery checks; full-row comparison allowing only person_id/deleted/version/audit fields; generated keys and unique contact candidate checks. Application must freeze and revalidate full before-images under locks, persist restricted backup before updates, and verify independent post-images after commit. Authenticated API/browser acceptance is separate.

## Authorization update

- User explicitly instructed “执行修复” after reviewing the exact four-row scope. Application to the identified test database is authorized. Scope and ownership unchanged; complete rehearsal before applying.

## Delivery

- Applied exactly four authorized updates in test/tenant 1: Lead internal ID 5307, Opportunity 7331, Order 635 now reference Person 6005; Person 5311 logically deleted. Existing 6005 master/contact claim and all student services preserved. No schema, migration, grant, owner, amount, status, notification, service lifecycle, branch or commit operations.
- Verification PASS: actual MySQL temporary tables with source schemas; failures after each of four writes roll back completely; successful recovery and completed-state guard; applied transaction full before-image guard, row locks and whitelist comparison; independent connection exact post-image comparison across all scoped person references; UTF-8 HEX; phone-only and phone-plus-same-WeChat identity queries uniquely match 6005 with no pending-repurchase conflict. Two active student services remain. Actual repeated application performs zero updates. Python syntax, scoped whitespace and git diff --check pass.
- First rehearsal found a fixture-copy issue: DEFAULT_GENERATED timestamp columns were omitted along with computed columns. Fixed exclusion to VIRTUAL/STORED GENERATED, checked fixture equality, reran full rehearsal successfully. No application rows changed during the failed rehearsal.
- Restricted evidence: /opt/zsjos-runtime/backups/repurchase-identity-20261029/{rehearsal-v2,applied,repeat}/ and independent-verification.json. Before/after backups retained with 0700 directories and 0600 files. Source and operations document delivered; unrelated current edits preserved.
- Limits: no authenticated API/browser full order submission; no real order created. Restore requires later-business and concurrency review plus explicit scope authorization; use protected before-images, never overwrite subsequent work. Delivery complete; ownership released.
