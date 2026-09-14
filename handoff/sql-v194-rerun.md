# Workstream registration — 2026-09-11

- ID: `sql-v194-rerun`
- Goal: Fix V194 replay failing after V198 creates the two material creation pages.
- Non-goals: Change permission identifiers, grants, business rows, other migrations, dependencies, branches, commits, or services used by the application.
- Branch / target branch: `fix/media-account-create-columns-v204`
- Absolute worktree: `D:\ZSJ-OS`
- Base commit: `f338087e9aaa8b8d881cd0aee80427d8773ab284`
- Ownership scope: `script/sql/mysql/migrations/V194__material_library_content_review.sql`; `script/sql/mysql/migrations/README.md`; `script/sql/mysql/tools/test_v194_replay.py`; this handoff file.
- Owner: Codex `/root`, serial file writer for this task.
- Dependencies: Existing MySQL 8 Docker image, Python standard library, repository SQL; preserve all pre-existing changes.
- Integration order: Reproduce using controlled MySQL; narrow the conflict predicate; verify upgrade/replay and real conflict rejection; document evidence.
- Verification plan: Execute baseline through latest version in a disposable test container, run V194 before/after V198, inspect schema and scoped metadata against local development database, verify Chinese labels by HEX, and run scoped diff checks. Local development database inspection is read-only until the exact effects of any replay are reviewed.

## Delivery entry — 2026-09-11 09:45:08 +08:00

- Beijing time: 2026-09-11 09:45:08 +08:00
- Branch: `fix/media-account-create-columns-v204`
- Worktree: `D:\ZSJ-OS`
- HEAD commit: `f338087e9aaa8b8d881cd0aee80427d8773ab284`
- User goal: 修复 SQL 迁移脚本执行失败。
- Key decisions: Correct the existing development V194 guard, without a new migration or changes to permission identifiers/grants. Accept only V198's exact page identities for menu IDs 80041/80042. Preserve the existing dictionary changes in V194 and all unrelated worktree changes. Use the current branch and serial file ownership.
- Execution / analysis result: The supplied log shows V198 executed before V194. The original V194 conflict SELECT reproduces two false positives on the local database: the legitimate V198 creation pages sharing `zsjos:material:create`. The corrected SELECT returns no conflicts. Local V194 schema, successor menu metadata, and relevant registry records already match the controlled migrated database; no development data repair or migration replay was necessary. No development or shared database was written. The earlier hypothesis about retired menu 6974 was ruled out by read-only evidence.
- Changed files: `script/sql/mysql/migrations/V194__material_library_content_review.sql`; `script/sql/mysql/migrations/README.md`; `script/sql/mysql/tools/test_v194_replay.py`; `handoff/sql-v194-rerun.md`.
- Verification evidence: Real MySQL 8 integration passed V194 initial execution and replay before V198, complete bootstrap through V206, two V194 replays after V198, stable successor menu metadata and seed/grant counts, Chinese name HEX assertions, and both V194 version registries. An unknown permission owner, seven malformed V198 page variants, and an occupied V194 menu ID were rejected. After restoring test fixtures, V194 passed again. The corrected guard was also queried read-only against local development. Python syntax and scoped `git diff --check` passed. Test containers were removed; generated tracked Python bytecode was restored and the newly generated cache file removed.
- Schema / scoped data comparison: Using UTF-8 clients with identical raw output, the controlled fresh database and local development have no column or index differences across V194-owned tables and its two extended base tables. Menus 80041/80042, including HEX labels, match exactly. V185/V194/V198/V206 checksums match in both registries. Whole-database comparison reports column differences across 124 tables and index differences across 127 tables, including objects outside this task; this is not evidence of full baseline equivalence.
- Unresolved verification: The generic `zsjos_db.py check` fails because the manifest references missing `yudao-module-crm`. Bootstrap SQL execution succeeds, but `verify-bootstrap.sql` reports 19 failed assertions outside the focused V194 assertions: `new_media_workflow_schema`; `partner_student_active_unique_keys`; `new_media_role_menu_permissions`; `new_media_business_notifications`; `employee_birthday_care_menu`; `employee_birthday_care_super_admin_menu`; `business_notification_templates_no_customer_name_variables`; `lead_filter_versions`; `sales_order_v025_menu`; `module_schema_versions`; `V071 exact partner permissions`; `V071 exact finance permissions`; `V071 zero-ZSJOS roles`; `V071 no duplicate role permissions`; `V071 active menu parent integrity`; `study_planner_repurchase_permissions`; `V128 media director student flow`; `V149 feedback menus and permissions`; `V191 dictionaries, default material types, and content-review config`. The focused test reports these separately and does not certify the whole baseline.
- Dependency / integration impact: None. No new dependency, branch/worktree operation, commit, push, application-service restart, or real account permission change. No frontend contract change; the existing Vue create permission and React successor page contracts are retained.
- Remaining work: The reported V194 migration error is fixed and verified. Full-release baseline approval still requires diagnosis of the manifest issue, 19 failed assertions, and broader schema differences. Deployed immutable migration histories must not be overwritten using this development-baseline correction. Existing-database replays require reviewing their effects, especially V194's existing menu upserts; future migrations must execute in numeric order.
