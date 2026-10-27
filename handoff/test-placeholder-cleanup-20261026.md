# Registration — test-placeholder-cleanup-20261026

- Owner: Codex /root; environment test from /etc/zsjos/agent-environment; branch main; worktree /opt/zsjos; base 8279212fb97129ba6b006dbb0452ad843dfde4b5; target branch/integration None.
- Authorization: user said “清理” after reviewing the exact remaining 55 placeholder orders, their 55 items/55 registration cases, and 12 service links. Target only zsjos-mysql-1 / zsjos / tenant 1.
- Goal: detach those 12 service origins and soft-delete the 165 placeholder-chain rows, retaining all service business fields, related materials, existing notification/audit history and backups.
- Ownership: script/sql/mysql/repairs/cleanup_placeholder_orders_20261026.py; docs/operations/placeholder-orders-cleanup-20261026.md; this record; private evidence /opt/zsjos-runtime/backups/placeholder-orders-20261026-test/.
- Non-goals: real orders, service cancellation/reassignment/course changes, physical deletion, permissions, schema/migrations/version ledgers, application deployment/restart, branches/commits/push, production.
- Dependencies: prior 2026-10-26 service consolidation and seeded-course work delivered; preserve their changes. Existing nullable origin schema and deployed independent-service readers. Existing database connection/serialization helpers reused without editing them.
- Verification: exact whitelist and fresh relational/JSON/BPM/payment guards; complete private before/after images and protected dependency snapshots; target MySQL temporary-table initial/replay/failure rollback/stale/partial-state/restore rehearsal; actual transaction preview and rollback; guarded atomic execution, independent full readback, replay zero, UTF-8 HEX and unchanged runtime. Browser authenticated flow remains unverified unless an existing session is available.
- Recovery: transaction rollback before commit; afterward restore only after matching complete saved after-images and separate authorization; never overwrite later business changes or remove historical audit.

## Delivery — 2026-10-26

- Completed authorized test-only cleanup at 16:53 Beijing: 12 service origins detached, 55 registration cases/55 items/55 orders soft-deleted; 177 updates total. Original domain states and all non-target fields retained.
- Evidence: /opt/zsjos-runtime/backups/placeholder-orders-20261026-test/; plan SHA256 5ac697bbe770a9e2bfdfb1757788a7e16f9578aba01c7088fd51c603c9730274. Private complete images, fixture/preview/apply/verify/replay and independent-summary records retained; 0700 directory/0600 files.
- PASS: exact whitelist/reference guards; same-engine temporary tables initial177/replay0/six rollback points/exact restore/partial and drift refusal; actual target preview rolled back; postcommit independent 177-image verification and 81-table/4883-row protection; second apply0; independent UTF-8/HEX for 12 unchanged service snapshots; backend PID/start/JAR unchanged.
- Protected: 59 service rows, 96 media accounts and associated business materials, other real orders; 22 old notification Outbox records,67 messages,43 historical repair references unchanged. Existing backup tables untouched. No permission/BPM/notification/schema/ledger/deployment/restart/commit/push operations.
- Final read-only result 16:56: all55 chain rows per table deleted; 12 services active/accepted with null origins; remaining unremoved orders across the four confirmed seed batches0. Prior54 remain deleted, total109.
- Source/documentation owned above completed. Authenticated browser/API flow not exercised; database and existing deployed reader checks are not UI acceptance. No production operation. Ownership released.
