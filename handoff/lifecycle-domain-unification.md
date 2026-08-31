# Workstream: lifecycle-domain-unification

- ID: `lifecycle-domain-unification`
- Goal: Unify the customer, lead, opportunity, and first-purchase order lifecycle model for phase one of the approved lifecycle plan.
- Non-goals: Duplicate-review workflows, opportunity public pool, complaints, repurchase entry points, cashback, withdrawal, export, maintenance mode, and external notification channels.
- Branch: `codex/lifecycle-domain-unification`
- Worktree: `D:\ZSJ-OS-worktrees\lifecycle-domain-unification`
- Base commit: `405f9ed30146fb7b0528c5c22cab5d4c78264b7b`
- Target branch: `main`
- Ownership scope: ZSJOS lead/opportunity/order domain model and services, focused tests, additive MySQL migration and bootstrap/schema verification, directly affected workbench projections, and directly affected lifecycle/API documentation.
- Owner: Codex `/root`
- Dependencies: Existing lead qualification, opportunity follow-up, sales-order dual BPM approval, object permissions, and current schema baseline through `V032`.
- Integration order: Phase one must merge before duplicate review, opportunity public pool, submitter actions/complaints, and repurchase workstreams.
- Verification plan: Focused service tests; full `yudao-module-zsjos` Maven tests; migration syntax/repeatability and schema verification review; workbench unit tests, typecheck, production build, and responsive browser checks for changed list/detail projections.
- Status: `ready-to-merge`

## Delivery Entries

### 2026-08-12 19:51:29 +08:00

- Branch: `codex/lifecycle-domain-unification`
- Worktree: `D:\ZSJ-OS-worktrees\lifecycle-domain-unification`
- HEAD commit: `405f9ed30146fb7b0528c5c22cab5d4c78264b7b`
- User goal: Implement phase one of the approved customer-to-deal lifecycle plan: unify Customer, Lead, Opportunity, and first-purchase Order states and relationships without starting phases two through five.
- Key decisions: Lead remains `valid + owned` after qualification; qualification and appeal overturn create or restore the unique Opportunity; official Lead and Opportunity owners are synchronized by formal transfer; effective first-purchase approval sets Order effective and Lead/Opportunity won; `person_id` remains the internal customer key; active Customer-to-Lead and Customer-to-Opportunity uniqueness uses logical-delete-aware generated keys; repurchase remains phase five.
- Execution or analysis result: Phase-one implementation is complete in the isolated worktree. Added V033 with blocking preflight assertions, historical `converted -> valid` and effective first-purchase `-> won` normalization, Lead/Opportunity relationship repair, dictionary/filter normalization, unique constraints, and module/global version recording. Updated current API and lifecycle documentation. No database, external service, commit, merge, push, or publication action was performed.
- Changed files: `backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/enums/LeadConstants.java`; Lead services `LeadAppealServiceImpl.java`, `LeadBasicInfoService.java`, `LeadDispatchServiceImpl.java`, `LeadFollowUpServiceImpl.java`, `LeadInboxFilterConfigServiceImpl.java`, `LeadManagementServiceImpl.java`, `LeadObjectPermissionService.java`, `LeadQualificationServiceImpl.java`, `LeadStateProjection.java`; `SalesOrderServiceImpl.java`; focused Lead/Order service tests; `frontend/workbench/src/constants.ts`; `frontend/workbench/src/services/leadManagement.test.ts`; `script/sql/mysql/00-bootstrap-schema.sql`; `01-bootstrap-system-seed.sql`; `02-bootstrap-zsjos-seed.sql`; `bootstrap.sql`; `schema/core.sql`; `verify-bootstrap.sql`; `migrations/V033__lifecycle_domain_unification.sql`; `verify-lifecycle-domain-v033.sql`; `docs/api/zsjos-lead-submission-dispatch.md`; `docs/api/zsjos-sales-order.md`; `docs/business/lead-order-state-machine.md`; this handoff file.
- Verification evidence: Reactor compile/install with tests skipped passed for all dependencies and ZSJOS. Focused lifecycle tests passed 66/66, including appeal restore, qualification restore, owner transfer, follow-up projection, management actions, customer consistency, and approval success. Workbench tests passed 65/65; TypeScript typecheck passed; production build passed with the existing large-chunk warning. Browser checks at 1440x900 and 390x844 showed no horizontal overflow or control clipping on the unauthenticated page; authenticated lead-list rendering was unavailable. `git diff --check` passed. SQL was reviewed for MySQL client `DELIMITER`, guarded DDL, repeatability, migration order, global/module version recording, and non-destructive duplicate blocking; real MySQL execution was not authorized and therefore not run.
- Dependency or integration impact: Phase two must start only after this workstream is committed and integrated. Full repository/module test commands expose unrelated baseline failures: `yudao-module-infra` `CodegenEngineUniappTest.testExecute_treeSearch`; ZSJOS `LeadFollowUpRuleServiceImplTest.updatesTimeoutAndAdvancesVersion`; ZSJOS `LeadNotifySceneProviderTest.registersAllScenesWithSceneSpecificVariables`. These files were not modified. `npm ci --ignore-scripts` installed lockfile-defined dependencies only in this worktree; no lockfile changed.
- Remaining work: Obtain explicit confirmation to commit this workstream. Before production migration, run `script/sql/mysql/verify-lifecycle-domain-v033.sql` against the target database, resolve any returned relationship conflicts, execute V033 in a controlled environment, and run `verify-bootstrap.sql`. Real authenticated HTTP and lead-list browser checks remain unverified because no backend/login environment was started. After commit and integration confirmation, create a separate branch/worktree for phase two.

### 2026-08-12 21:19:44 +08:00

- Branch: `codex/lifecycle-domain-unification`
- Worktree: `D:\ZSJ-OS-worktrees\lifecycle-domain-unification`
- HEAD commit: `0275ff5e10542a145547d8d8c3fa9ac2d32dc018`
- User goal: Commit and integrate all approved branches and worktrees into `main`.
- Key decisions: Preserved the phase-one implementation as its own auditable commit; marked it ready to merge after confirming the worktree was clean and the previously recorded verification remained applicable.
- Execution or analysis result: Phase-one implementation committed successfully and is ready for integration. Integration must renumber its migration because another ready workstream independently owns V033 through V035.
- Changed files: `handoff/lifecycle-domain-unification.md`.
- Verification evidence: Pre-commit `git diff --check` passed; implementation verification remains the 66/66 backend lifecycle tests, 65/65 workbench tests, typecheck, production build, and responsive smoke checks recorded above.
- Dependency or integration impact: During integration, rename the lifecycle migration and verification references from V033 to the next free migration number after the collaboration-pool sequence, then rerun affected checks.
- Remaining work: Merge into `main`, resolve the migration-number collision, rerun integration verification, and record the resulting integration commit.

### 2026-08-12 21:44:35 +08:00

- Branch: `main`
- Worktree: `D:\ZSJ-OS`
- HEAD commit: `cddab6f8ad16805d86fab8267db385fc0ba2865d`
- User goal: Merge all approved branches and worktrees into `main`.
- Key decisions: Integrated phase one after the collaboration-pool chain; retained `valid` as the post-qualification Lead state and `won` as the first-purchase completion state; preserved collaboration-pool effective-sales behavior; enforced Lead/Opportunity customer and owner consistency before order creation; renumbered unpublished integration migrations to a continuous V034-V037 chain without editing existing mainline V022/V033 migrations.
- Execution or analysis result: Lifecycle implementation and integration conflicts are resolved and staged for the merge commit. The lifecycle migration is now `V037__lifecycle_domain_unification.sql` with matching verification script.
- Changed files: Lifecycle implementation, tests, API/state-machine documentation, bootstrap/schema artifacts, `script/sql/mysql/migrations/V037__lifecycle_domain_unification.sql`, `script/sql/mysql/verify-lifecycle-domain-v037.sql`, migration-chain integration files, and this handoff file.
- Verification evidence: Backend Reactor compile passed; 68 focused lifecycle, aging-pool, and subordinate-sales tests passed; Workbench 76/76 tests, typecheck, and production build passed; `zsjos_db.py check` passed migration continuity, schema, Java mapping, baseline, and verification checks; `git diff --check` passed.
- Dependency or integration impact: Collaboration pool is V034, invalid-task cleanup V035, subordinate sales V036, and lifecycle unification V037. No database migration was executed.
- Remaining work: Complete the merge commit, merge the Popconfirm workstream, rerun final integration checks, and clean merged worktrees/branches.
