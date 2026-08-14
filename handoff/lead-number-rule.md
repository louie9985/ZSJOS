# Lead Number Rule Workstream

- Workstream ID: `lead-number-rule`
- Goal: Add a tenant-daily Zhongshijian Lead business number in the form `KZyyyyMMddHHmmss` plus a fixed four-digit sequence that wraps from `9999` to `0001`, while preserving numeric Lead IDs for internal relationships and commands.
- Non-goals: Change Lead primary or foreign keys, URLs, permissions, other business-number rules, external services, or dependencies.
- Branch: `codex/lead-number-rule`
- Worktree: `D:\ZSJ-OS-worktrees\lead-number-rule`
- Base commit: `6ec2a9b53c3e8c831caa0902c88969fc69011331`
- Target branch: `main`
- Ownership scope: ZSJOS Lead number generation and projections; Lead-facing React/Vue display and search; MySQL V054, desired schema, bootstrap verification, directly affected Lead API/business documentation; this handoff.
- Owner: Codex `/root`
- Dependencies: Existing ZSJOS transaction, tenant, MyBatis, MySQL migration, React workbench, and Vue Admin facilities. No new dependency.
- Integration order: Integrate after any workstream that changes the same Lead schema or Lead submission/projection files, resolving overlaps by preserving both behaviors; then rerun affected backend, SQL, and frontend checks on the integration branch.
- Verification plan: Focused backend generation/search/projection tests; ZSJOS Maven tests/package; SQL manifest/schema/repeatability checks without applying to a real database; Workbench tests/typecheck/build; Admin scoped lint/typecheck/build; desktop/mobile browser checks when a runnable environment is available.
- Status: `implementation-complete-awaiting-commit-and-external-verification`

## Delivery 2026-08-14 12:39:49 +08:00

- Branch: `codex/lead-number-rule`
- Worktree: `D:\ZSJ-OS-worktrees\lead-number-rule`
- HEAD commit: `6ec2a9b53c3e8c831caa0902c88969fc69011331`
- User goal: Implement tenant-daily Lead business numbers in the form `KZyyyyMMddHHmmss` plus a minimum four-digit sequence, with migration, API, search, UI, tests, and documentation changes while preserving numeric Lead IDs.
- Key decisions: Keep `leadId` as the internal primary/foreign/routing/permission identifier; allocate `leadNo` in the Lead creation transaction with Beijing-local `submittedAt`; use a tenant/date MySQL counter and connection-scoped `LAST_INSERT_ID`; expand after `9999`; preserve numbers on idempotent replay and reactivation; perform deterministic historical backfill by `tenant_id`, Beijing-local submitted date, `submitted_at`, and `id`; keep uniqueness tenant-scoped.
- Execution or analysis result: Implementation complete. Added transactional number allocation, projections and search behavior, V054/backfill/counter schema, Workbench and Admin display/review controls, documentation, focused tests, and batch projection loading to avoid N+1 queries. No migration, shared-service operation, commit, push, merge, or publication was performed.
- Changed files: Backend Lead VO/DO/DAL/services under `backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/{controller/admin/lead/vo,dal/dataobject/lead,dal/mysql/lead,service/lead}` including new `LeadNoDailyCounterDO.java`, `LeadNoDailyCounterMapper.java`, and `LeadNumberService.java`; backend tests `LeadBasicInfoServiceTest.java`, `SubordinateSalesServiceImplTest.java`, and new `LeadNumberServiceTest.java`; Admin Lead API/views under `frontend/admin/src/{api/zsjos,views/zsjos}`; Workbench Lead pages/services/tests under `frontend/workbench/src/{pages,services}`; SQL `00-bootstrap-schema.sql`, `bootstrap.sql`, `schema/core.sql`, `verify-bootstrap.sql`, `migrations/README.md`, and new `migrations/V054__lead_business_number.sql`; docs `docs/api/zsjos-lead-submission-dispatch.md`, `docs/api/zsjos-subordinate-sales.md`, `docs/business/lead-order-state-machine.md`, and `docs/operations/database-migrations.md`; this handoff.
- Verification evidence: Focused backend tests passed, 10 tests total (`LeadNumberServiceTest` 7 and `SubordinateSalesServiceImplTest` 3). `mvn -f backend/pom.xml -pl yudao-server -am -DskipTests package` passed for all 25 modules. Workbench passed 21 test files/91 tests, typecheck, and production build; the build retained the existing large-chunk warning. Admin task-file ESLint and `pnpm build:local` passed; the build retained the existing legacy `*zoom` CSS warning. Core and bootstrap SHA-256 hashes both equal `52195EB20F0AB0A7DBC07F2A00445DDFD77D00AD71368BF2D1FCAA3C52D5DE48`. `git diff --check` passed.
- Dependency or integration impact: No new dependency. Integration must apply V054 before starting code that writes non-null `lead_no`; overlapping Lead schema/submission/projection changes require conflict review. Numeric IDs and existing URL/permission contracts remain unchanged.
- Remaining work: Full `-am test` is blocked before ZSJOS by the existing Infra failure `CodegenEngineUniappTest.testExecute_treeSearch`; module-only full tests are blocked by a stale installed System SNAPSHOT missing `MaintenanceModeApi` plus an existing permission assertion, which causes Mockito cascade errors. Admin full `pnpm ts:check` remains blocked by repository-wide missing auto-import declarations. `zsjos_db.py check` remains blocked by pre-existing missing Core mappings for `zsjos_lead_claim_daily_counter` and `zsjos_lead_transfer_request`. Real MySQL V054 execution, repeat execution, concurrent allocation, transaction rollback, controlled data validation, authenticated desktop/mobile browser verification, final commit, and ready-to-merge transition require separate authorization or environment repair.

## Delivery 2026-08-14 15:03:07 +08:00

- Branch: `codex/lead-number-rule`
- Worktree: `D:\ZSJ-OS-worktrees\lead-number-rule`
- HEAD commit: `6ec2a9b53c3e8c831caa0902c88969fc69011331`
- User goal: Correct the Lead sequence rule so the fixed four-digit suffix wraps from `9999` back to `0001`.
- Key decisions: The tenant/date counter now atomically applies `IF(current_value >= 9999, 1, current_value + 1)`. V054 backfill applies the same modulo-9999 sequence in stable historical order and initializes each daily counter from the daily row count. Tenant-scoped uniqueness remains; more than 9999 Leads for one tenant in the same second is intentionally rejected rather than silently duplicating a business number.
- Execution or analysis result: Updated runtime allocation, focused tests, V054 backfill and checksum marker, Core/Bootstrap counter comments, verification SQL, migration documentation, API documentation, business documentation, and this workstream goal. No migration, service operation, commit, push, merge, or publication was performed.
- Changed files: `LeadNoDailyCounterMapper.java`, `LeadNumberServiceTest.java`, `V054__lead_business_number.sql`, `00-bootstrap-schema.sql`, `schema/core.sql`, `verify-bootstrap.sql`, `migrations/README.md`, `docs/api/zsjos-lead-submission-dispatch.md`, `docs/business/lead-order-state-machine.md`, and this handoff.
- Verification evidence: Focused backend tests passed, 10 tests total (`LeadNumberServiceTest` 7 and `SubordinateSalesServiceImplTest` 3). `mvn -f backend/pom.xml -pl yudao-server -am -DskipTests package` passed for all 25 modules. Core and Bootstrap SHA-256 hashes both equal `25181873BE900CC668249087F1A7D354156B72BBDDF536E90563879F3182C7FA`. `git diff --check` passed. Frontend verification was not repeated because the API shape and UI behavior did not change.
- Dependency or integration impact: No new dependency. V054 must use the updated `lead-business-number-v2` marker and must be reviewed before execution because the fixed-width wrap can expose same-second historical collisions.
- Remaining work: `zsjos_db.py check` remains blocked by the pre-existing missing Core mappings for `zsjos_lead_claim_daily_counter` and `zsjos_lead_transfer_request`. Real MySQL V054 execution, repeat execution, collision precheck, concurrent allocation, transaction rollback, commit, and ready-to-merge transition still require separate authorization.

## Integration preparation 2026-08-14 16:00:00 +08:00
- Beijing time: `2026-08-14 16:00:00 +08:00`
- Branch: `codex/lead-number-rule`
- Worktree: `D:\ZSJ-OS-worktrees\lead-number-rule`
- HEAD commit: `6ec2a9b53c3e8c831caa0902c88969fc69011331`
- User goal: Commit and integrate all completed workstreams into local main.
- Key decisions: Preserve V054 as additive and unexecuted; resolve shared schema and Lead UI files against current main and later marker changes.
- Execution or analysis result: Integration authorization received; workstream marked ready to merge.
- Changed files: Existing Lead numbering implementation and this handoff.
- Verification evidence: Existing focused tests, server package, frontend checks, schema hashes, and diff-check evidence remains applicable; integrated checks will be rerun on main.
- Dependency or integration impact: Requires V054 before runtime writes; no new dependency or external-state change.
- Remaining work: Create the feature commit, record it, merge into main, and run integrated verification without applying V054.
- Status: `ready-to-merge`

## Final commit record 2026-08-14 16:05:00 +08:00
- Beijing time: `2026-08-14 16:05:00 +08:00`
- Branch: `codex/lead-number-rule`
- Worktree: `D:\ZSJ-OS-worktrees\lead-number-rule`
- HEAD commit: `b930c34758c0b113698ef5a0e8a0a75f45e0f980`
- User goal: Record the authorized feature commit before integration.
- Key decisions: Treat `b930c34758c0b113698ef5a0e8a0a75f45e0f980` as the final functional commit; this entry changes handoff metadata only.
- Execution or analysis result: Feature commit created successfully and the worktree is clean.
- Changed files: `handoff/lead-number-rule.md` only.
- Verification evidence: `git diff --cached --check` passed before the feature commit; prior focused verification remains recorded above.
- Dependency or integration impact: None beyond the recorded Lead numbering implementation.
- Remaining work: Merge into local main and run integrated verification.
- Final commit: `b930c34758c0b113698ef5a0e8a0a75f45e0f980`

## Merged 2026-08-14 16:12:37 +08:00
- Beijing time: `2026-08-14 16:12:37 +08:00`
- Branch: `main`
- Worktree: `D:\ZSJ-OS`
- HEAD commit: `d7e849cecef476c54c466a1ae0f2d0b22ebadcb1`
- User goal: Integrate all completed branches and worktrees into local main.
- Key decisions: Combined the Lead number with the current detail toolbar and retained advanced filtering; V054 remains unexecuted.
- Execution or analysis result: Merged by `9ccc859242`; conflict resolved without dropping either behavior.
- Changed files: Lead numbering workstream plus integrated `LeadManagementPage.tsx` resolution and this handoff.
- Verification evidence: Integrated focused backend tests passed; Workbench 165 tests, typecheck, and build passed; server package passed; migration ordering is unique through V055.
- Dependency or integration impact: V054 must be applied through the controlled migration flow before runtime use.
- Remaining work: Real MySQL and authenticated browser verification remain unexecuted; local branch/worktree will be removed under the confirmed cleanup scope.
- Status: `merged`
- Merge commit: `9ccc859242`
