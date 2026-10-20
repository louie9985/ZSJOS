# Test placeholder order cleanup

## Registration — 2026-10-16
- ID: test-placeholder-order-cleanup-20261016; owner /root; environment test from /etc/zsjos/agent-environment.
- Goal: explicitly authorized tenant 1 whitelist of 54 seed-students orders; preserve services, detach virtual origin, logically delete order/item/case chains.
- Non-goals: other seed batches/orders, real order reassignment, service/person deletion or cancellation, permissions, schema/migration changes, deployment/restart, commits.
- Branch main; worktree /opt/zsjos; base 29f8f5b5ee7b0cfd3fe4539f640289e9783f51c8; integration/target branch None.
- Ownership: script/sql/mysql/repairs/cleanup_placeholder_orders_20261016.py; docs/operations/placeholder-orders-cleanup-20261016.md; this record. Existing dirty files and default test handoff belong to other work and are preserved.
- Dependencies: MySQL 8 test Docker zsjos-mysql-1/database zsjos, V310 nullable origin and deployed null-safe readers. Runtime release inspected: 2026.10.16-005754-29f8f5b5.
- Verification: exact whitelist and seed/amount/state/origin/snapshot guards; private fsynced before/after plan; same-engine temporary-table initial/repeat/injected rollback/partial recovery/stale rejection/restore; relational and JSON guards; transactional live apply, committed readback, downstream/non-target fingerprints, replay zero; representative persisted Chinese HEX unchanged.
- Recovery: no physical deletes; restore only against exact after-images, reverse deletion order then service origin, retain later edits by refusing conflicts. Backups under /opt/zsjos-runtime/backups/placeholder-orders-20261016-* with private modes.

## Delivery — 2026-10-16 15:34:12 +08:00
- Owner /root; test/main /opt/zsjos; HEAD 29f8f5b5ee7b0cfd3fe4539f640289e9783f51c8 unchanged; integration None.
- User goal achieved: explicit 54-order tenant1 whitelist cleaned with services preserved, virtual origins detached and only order/item/case logical deletions. 216 committed updates; repeat apply 0.
- All54 services remain active/accepted with original IDs/domain fields/snapshots, 102 accounts/54 groups/102 plans preserved. 19 target/direct-dependency tables retain non-target row digests. UTF8 snapshot HEX verified.
- Verification: MySQL8 temporary initial216/repeat0/injected rollback/partial recovery/stale rejection/exact restore passed; deployed reader bytecode null guards and snapshot fallback inspected; committed216 exact images and independent SQL readback passed; Python AST and scoped diff checks.
- Preparation failures: oversized audit read cancelled; generated columns/metadata capitalization fixed in isolated rehearsal. First real apply stopped before writes on concurrent non-target data; narrowed stable protection to19 direct tables, refreshed plan and repeated rehearsal. No claim of globally unchanged live database.
- Private backup/evidence: /opt/zsjos-runtime/backups/placeholder-orders-20261016-54-v5; plan SHA256 0d18b334eb6ea21bdea328368d4be9d7c9c2ee600544a2bcaf628490cd1a21b2. Full before/after and restore guard documented.
- Files: cleanup_placeholder_orders_20261016.py; docs/operations/placeholder-orders-cleanup-20261016.md; this record. Existing dirty files and other handoff preserved.
- Impact: normal order entries removed; order counts/first-purchase classification may differ; service identities and delivery relationships preserved. No schema/permission/service restart/deployment/commit/push/production operations.
- Remaining: authenticated browser and real allowed/denied delivery action flows unverified; data cleanup and scoped verification complete. Ownership released.
