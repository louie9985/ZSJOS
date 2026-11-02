# Registration — local-student-info-backfill-20261102

## Scope correction registration

- User explicitly restricts repair to pending_approval only. Reclaim prior file ownership; same environment/branch/base. Add owned one-off recovery script script/sql/mysql/tools/revert_student_info_overfill_20261102.py and private local backup under artifacts/student-info-backfill/.
- Remove only unused rows from this run (tenant 1, audit marker and original creation window), whose orders are not pending_approval. Verify no form references or operation/version changes; backup exact rows before deletion, lock and transactionally recheck, preserve used rows. No other task deletion or production execution. Verify narrowed selection using MySQL fixtures and local readback.

- Environment: local (variable and environment file absent). Owner: Codex /root.
- Branch: main; worktree: D:/ZSJ-OS; base: 0ecc74d4d13bcfbc0361ecdbc2bbd73327359425; integration: None.
- Goal: repeatable preview/apply tool to backfill missing tenant 1 order-item collection tasks, then apply to authorized local development database.
- Non-goals: production execution, schema/migration rewrites, permissions, form contents, application/UI changes.
- Ownership: script/sql/mysql/tools/backfill_student_info_tasks.py, script/sql/mysql/tools/test_backfill_student_info_tasks.py, docs/operations/student-info-task-backfill.md, appended section in docs/api/order-student-information-tasks.md, this record.
- Dependencies: V359 actual schema and both ledgers; existing Python/PyMySQL tooling. Preserve concurrent changes.
- Verification: real MySQL temporary-table fixtures (replay, rollback, historical form association and invalid binding), preview, transactional local application and independent readback. No dependency additions.

## Delivery

- Added preview-first Python tool with target-bound plan hash, tenant/status scope, order-first locks, unique-key protection, historical form association checks, atomic insert/postcondition/commit, and safe error output. No schema/ledger/permission writes.
- Local preview: 425 missing order-item tasks, 0 historical forms to associate, 0 conflicts. Applied the reviewed plan in one transaction: 425 committed; fresh preview: 0 missing, 0 conflicts. Independent join verified the reported order has one item and one correctly bound task while remaining pending approval; audit marker count is 425, all with null form_id.
- Real MySQL connection-local temporary fixtures passed initial apply, replay, rollback, partial-repair recovery, terminal and tenant exclusion, existing form preservation, deleted-task and legacy-form conflict checks. Invalid expected plan rejected without writes. Initial fixture setup used unsupported same-name CREATE TEMPORARY TABLE LIKE; changed to SHOW CREATE TABLE and temporary DDL, then all checks passed.
- API note and operational runbook updated. Scoped diff check passed. No production execution, browser/account acceptance, deployment, dependency installation, commit or push. Ownership released.

## Corrected delivery — pending approval only

- Restricted tool to pending_approval, updated docs and real MySQL fixture to explicitly exclude effective and revision_required in addition to terminal states. Tests passed.
- Recovery preview found 423 unused out-of-scope tasks, 0 used conflicts. Backed up full rows to artifacts/student-info-backfill/overfill-backup-20261102145219301173.json before deletion; exact tenant/marker/original timestamp scope, order/task locks, form reference/operation checks and one transaction. Deleted 423, replay found 0 eligible rows. No business form/order changes.
- Independent readback: only 2 original-run tasks remain, both pending_approval. Narrowed preview reports 0 missing and 0 conflicts. Scoped diff check passed. Backup preserves original IDs/columns; restoration, if explicitly needed, must first check current references and unique keys, decode hex byte fields, and insert only the reviewed rows in a transaction. No automatic restoration or production action. Ownership released.
