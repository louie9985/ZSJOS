# local-db-restore-20261022

- Status: active
- Environment: local (ZSJOS_AGENT_ENV unset, environment file absent)
- Goal: Replace local Docker yudao-mysql / ruoyi-vue-pro with D:/zsjos-test-20261021T075320Z.sql.gz as explicitly requested.
- Non-goals: Remote database changes, application service changes, password reset, migration execution, source changes.
- Branch: main
- Worktree: D:/ZSJ-OS
- Base commit: 404387de566fcb9abf4b264f0e005bf0566ea209
- Owner: Codex root
- Ownership: handoff/local-db-restore-20261022.md; local target database and uniquely named restore validation schema; external D:/zsjos-local-db-restore-20261022/ artifacts.
- Dependencies: Running local MySQL 8.4.11 container and supplied gzip dump.
- Target branch / integration order: None
- Verification: gzip CRC/SHA256, fail-fast isolated restore, all table row counts and checksums, structure and generated columns, both migration ledgers, UTF-8 text HEX, tenant audit; complete rollback dump before replacement.
- Registration time: 2026-10-22T10:19:27.014738+08:00
- Replacement order: Back up old schema including routines/events/triggers; restore supplied snapshot to isolated schema; verify; recreate only ruoyi-vue-pro; import validated snapshot; compare; retain rollback backup and verification evidence.
- Repeatability / recovery: Reimporting recreates the exact snapshot; MySQL DDL is non-transactional, so recover failed replacement from retained full backup. No automatic upgrade or inferred data repair.

## Registration update 2026-10-22T10:40:38.086530+08:00

- User authorized stopping the local backend listening on port 48080 (verified java.exe PID 28176), explicitly requested no restart after restoration. Scope updated to include this exact service stop; no Docker service restart or reconfiguration is authorized. Backend was stopped.
- Observed live audit INSERT requests while importing; temporarily suspended only the task-owned verification orchestrator while awaiting the user decision, then resumed it after stopping the backend.

- Recovery update: post-import verification detected backend-generated audit/error logs and Flowable/Quartz scheduler mutations before the authorized service stop. Initial equality failure invoked rollback; task-owned rollback session 65 was stopped before promoting the already verified isolated snapshot. Ownership/verification extends to two empty-schema rename probes restore_move_src_20261022 / restore_move_dst_20261022. Source validation schema has no views, triggers, routines or events; verify cross-schema FK rename before promoting all tables.

## Delivery 2026-10-22T10:44:59.440233+08:00

- Status: completed; ownership released.
- Branch / worktree / HEAD: main / D:/ZSJ-OS / 404387de566fcb9abf4b264f0e005bf0566ea209 (unchanged).
- User goal: Replace only local Docker yudao-mysql / ruoyi-vue-pro using D:/zsjos-test-20261021T075320Z.sql.gz; user additionally instructed stopping local backend without restarting.
- Source SHA256: 397a1d31f188aa3b880d26525ec9cab9e253a59440f7e8df2bc91ffeff8f14ef; gzip integrity checked; 3,343,192,617 decompressed bytes.
- Rollback backup: D:/zsjos-local-db-restore-20261022/ruoyi-vue-pro_before_restore.sql; 2,757,459,980 bytes; SHA256 d79d844d6d33120cc7773610faa7be11c627c5ed40184dfb0e0d53c2410bd651.
- Execution: Isolated import succeeded after transient Docker restart/large-transfer failures; compressed transfer and container-side decompression succeeded. First target import was mutated by still-running backend scheduler/log writes before authorized backend shutdown, causing strict comparison failure. Stopped task-owned automatic rollback importer, then promoted verified isolated tables into a clean application schema using tested cross-schema rename. The 4 old-only tables were removed with old schema replacement; no numbered migration/checksum history was edited.
- Verification: RESTORE_VERIFIED_OK. All 563 base tables have matching exact row counts and EXTENDED checksums; all column/index definitions and both ledger hashes match isolated source; 62 foreign-key column references and 21 generated columns preserved. No views/routines/triggers/events existed in source. UTF-8 connection is utf8mb4; representative system menu HEX E7B3BBE7BB9FE7AEA1E79086 matches source. Counts: 96 system users, 9,869 role-menu rows, 5,977 Leads and 808 orders (including logical-deleted records). All Lead/order rows are tenant 1. Evidence: D:/zsjos-local-db-restore-20261022/result.json plus stage-verification.json, target-verification.json, backup.json, source.json and before-summary.json.
- Tenant deviation: Source has 91 tenant-1 user rows and 5 historical non-tenant-1 user rows. Preserved supplied snapshot; no inferred tenant cleanup or password reset.
- Changed repository files: handoff/local-db-restore-20261022.md only. User source work preserved. External artifacts are kept under D:/zsjos-local-db-restore-20261022/.
- Dependency / integration impact: Core reverted from V344 to supplied V338, other module versions match supplied backup. Current code may require later migrations; none automatically executed. No remote database changes, permission reassignments beyond exact snapshot restore, commits, pushes or application restart.
- Cleanup: Removed only task-created empty validation/probe schemas and temporary transformed SQL/transport files; retained original source archive and full old-database backup.
- Remaining work: None for requested database replacement. Local backend port 48080 intentionally remains stopped per user instruction; application/browser acceptance not run with service stopped.
