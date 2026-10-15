# Migration gap V318 - 2026-10-15

## Registration
- ID / owner: local-migration-gap-v318-20261015 / Codex root.
- Environment: local (ZSJOS_AGENT_ENV unset; environment file absent).
- Branch / worktree / base: main / D:/ZSJ-OS / 8df3f65eb379b142c0ce0cb84b21e621e59b79b9.
- Target branch / integration order: None.
- Goal: shift the remaining exam-export migration V319 to V318 after removal of the former V318 from main; restore contiguous source numbering.
- Non-goals: business SQL changes, live database ledger/checksum remapping, removed feature restoration, service changes, commit/push.
- Ownership: script/sql/mysql/migrations/V319__exam_calendar_export_permission.sql -> V318__exam_calendar_export_permission.sql; script/sql/mysql/tools/test_exam_export_permission.py; docs/api/exam-calendar.md; this record.
- Dependencies: user explicitly requested renumbering. Prior renumber delivery released ownership. Current core contains V001-V317 and V319. Development DB already contains old V318-V321 entries; do not overwrite these historical identities.
- Verification: static migration check, numbering tests, unchanged business SQL comparison, isolated MySQL initial/replay/partial/failure recovery and UTF-8 HEX checks, fresh-chain execution where available, read-only affected development-data comparison.
- Deployment boundary: source numbering applies to this main baseline; old-number deployed databases need a separately reviewed ledger/checksum mapping. Preserve unrelated dirty files and historical handoffs.

## Delivery
- Renamed exam export V319 to V318; synchronized procedure identifiers, both ledger versions, filename checksum input, fixture references and current API documentation. Core now has exactly V001-V318. SQL business body is unchanged after version-only substitution and newline normalization.
- PASS: zsjos_db.py check; four migration-numbering unit tests; scoped diff/content checks.
- PASS: retained isolated MySQL fixtures for initial execution, partial state, missing parent, identity conflict and second-ledger failure; replay/recovery, no success record on failure, administrator settings preservation and Chinese HEX verification.
- PASS: Core-only fresh bootstrap through V318, core verification and non-empty bootstrap guard. Executed existing test_fresh with process-local load_manifests filter; no repository test-runner change. The test command itself ignores ZSJOS_DB_MODULES, so environment-only filtering still ran all modules.
- All-module fresh acceptance BLOCKED by existing PMS V002 requires PMS V001. No unrelated PMS change made.
- Read-only development comparison: export menu identity and UTF-8 name agree with intended metadata; both development ledgers already contain old V318-V321 identities. No live migration, data correction or ledger/checksum reconciliation performed. Existing old-number upgrade acceptance remains unverified and requires a separately reviewed mapping; this delivery restores source continuity and validates the fresh Core baseline.
- Scope: three implementation/document files plus this record. Preserved unrelated dirty files; no commit/push. Ownership released.
