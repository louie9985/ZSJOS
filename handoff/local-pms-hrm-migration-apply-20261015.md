# Local PMS and HRM migration application

## Registration
- Workstream: local-pms-hrm-migration-apply-20261015; owner: Codex /root.
- Environment: local (environment override absent; fallback); branch: main; worktree: D:/ZSJ-OS; base: 315efd729f2e049fc2639194567d86077ae91184. Target branch/integration: None.
- Authorization: user requested application of the three identified pending migrations to local yudao-mysql / ruoyi-vue-pro: PMS V003 then V004, and HRM V004 including trial dictionary value 9.
- Goal: apply existing scripts with backup and verified postconditions. Non-goals: Core ledger reconciliation, unrelated changes, permissions, service restart/deployment, commits/pushes.
- Ownership: this record; private local backup artifacts; database objects affected by the three migrations (three PMS tables, HRM trial fields/company_age, trial dictionary entry and scoped version records). Existing SQL sources are read-only.
- Dependencies: PMS V002, HRM V003 and prerequisite structures; one sequential writer to the target database.
- Verification: existing isolated MySQL verification scripts; target prerequisite audit; restricted backup; failure-stopping application; table/column/index and ledger checks; UTF-8 HEX; replay preserves records; confirm no pending module scripts remain.
- Recovery: retain pre-change backup; MySQL DDL is not atomically reversible. On failure inspect actual objects and ledgers before resuming; do not drop business tables or downgrade decimal tenure automatically.

## Delivery - 2026-10-15
- Applied original PMS V003, PMS V004, HRM V004 SQL sequentially to local yudao-mysql / ruoyi-vue-pro using a UTF-8 client and stop-on-error execution. Each succeeded, then each was replayed successfully. SQL source unchanged.
- Before application: rechecked PMS V002 / HRM V003 prerequisites, absent PMS tables and pending records, absent trial columns/value 9, and INT company_age.
- Backup: backups/mysql/local-pms-hrm-apply-20261015/before.sql (Git-ignored, restricted directory ACL). Contains affected employee/dictionary/version tables; dump completion, table definitions and SHA-256 verified. Private preflight/application/verification JSON files record data hashes and migration file hashes without exporting employee rows into reports.
- Controlled validation: pms-attachments.py passed initial/replay, UTF-8 HEX, prerequisite failure, partial recovery and incompatible-schema recovery, plus full PMS V001-V004/schema comparison. hrm-trial-tenure.py passed fresh, upgrade, partial, prerequisite failure, execution failure and ledger failure scenarios.
- V003 legacy verifier reached its final all-PMS schema comparison and failed because the current desired schema also includes V004. Read-only comparison confirmed zero chain-only columns and exactly 14 attachment-table-only columns in the snapshot; V003 affected schema comparison passed. Full V001-V004 validation above passed. Verifier source unchanged.
- Retained synthetic evidence: pms_import_20261015130946_*, pms_attachment_20261015130946_*, hrm_trial_verify_987084260900_*. No real business rows copied to fixtures.
- Target postconditions: three PMS table column shapes/indexes match controlled chain; three HRM trial fields and DECIMAL(10,2) match verified upgrade; trial label HEX E8AF95E5B297 and attachment table comment HEX verified; PMS V003/V004, HRM V004 and legacy HRM_V004 records present.
- Preservation: normalized hashes confirm all original employee values, existing dictionary rows and existing ledger rows unchanged. Replay preserved employee values, complete dictionary rows and ledger timestamps. Current repository manifest scan has zero pending numbered migrations.
- Boundaries: Core records V319-V321 have no corresponding current source files and remain unreconciled; no claim of complete historical schema/release acceptance. No shared-service restart/deployment, authenticated browser acceptance, permissions changes, commits or pushes. Existing unrelated worktree changes preserved.
- Outcome: requested local database application complete; source ownership released.
