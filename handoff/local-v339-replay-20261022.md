# V339 replay compatibility

## Registration - 2026-10-22
- Environment: local (ZSJOS_AGENT_ENV unset; marker absent). Owner: Codex /root. Branch: main. Worktree: D:/ZSJ-OS. Base: 404387de566fcb9abf4b264f0e005bf0566ea209. Target branch / integration: None.
- Goal: user-authorized V339 replay repair after V343; retain the later pending-class uniqueness rule and all existing business/ledger data.
- Non-goals: application database writes, deployed checksum reconciliation, permission grants, service changes, publication, unrelated work.
- Ownership: script/sql/mysql/migrations/V339__teaching_service_close_restore.sql; script/sql/mysql/tools/test_planner_unscheduled_class.py; docs/operations/database-migrations.md (scoped compatibility note); this record. Existing uncommitted implementations are preserved.
- Dependencies: delivered V339/V343 migration contracts, MySQL 8 container and existing synthetic fixture helpers. No active writer found for these files.
- Verification: reproduce old-script failure on isolated V343 schema with multiple UNSCHEDULED classes; V339 initial/replay/partial/failure suite; V343 initial/replay/partial/failure plus V339 cross-version replay preserving generated expressions, rows and both ledgers; read-only application comparison; scoped content checks.
- Deployment boundary: user explicitly authorized the diagnosed V339 source repair. Applied target ledgers/checksums are preserved; use of changed source on a deployed environment still requires a reviewed rollout, never automatic checksum reconciliation. This is a compatibility correction, not a new schema baseline or migration order.

## Registration update
- Include script/sql/mysql/tools/test_teaching_service_close.py: existing canonical comparison includes V344's later group index while its fixture stops at V343. Scope both legacy canonical comparisons to their owned structure; no V344 implementation change. Reproduction failed with original ERROR 1062; patched cross-version checks already pass.

## Delivery - 2026-10-22
- V339 now retains V343's PENDING-only generated guard when either ledger records V343 or actual unscheduled_guard DDL exists. The original V338-to-V339 path remains unchanged. Existing version rows/checksums are not reconciled.
- Added cross-version replay regression with multiple active UNSCHEDULED rows, repeated V339 execution and partial V343 without success markers. Complete synthetic class/service/menu rows and ledger records remain identical; generated expressions/index definitions remain stable and PENDING duplication is still rejected.
- Verification: original script reproduced ERROR 1062 in the new regression. Final python -B script/sql/mysql/tools/test_teaching_service_close.py and python -B script/sql/mysql/tools/test_planner_unscheduled_class.py --skip-fresh both exited 0. Initial/replay, prerequisite failures, partial DDL, second-ledger rollback/recovery, class uniqueness, UTF-8 HEX and owned canonical structure comparisons passed. Retained V339 suite schemas: tc_ebc989cf_*. Syntax/UTF-8/whitespace checks passed.
- Read-only application comparison: class_kind and all three guard definitions plus unique indexes match controlled migrated fixture. No application database write, service change, grants, checksum rewrite, commit or push. Full fresh chain not rerun: baseline/order/dependencies are unchanged and scoped correction paths were executed on MySQL 8.
- Updated migration operations documentation with cross-version behavior, partial recovery, deployment/checksum review boundary and rollback limits. Unrelated work preserved. Ownership released.
