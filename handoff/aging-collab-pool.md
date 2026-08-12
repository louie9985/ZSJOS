# Aging Collaboration Pool Workstream

- Workstream ID: `aging-collab-pool`
- Goal: Implement the overdue collaboration pool while preserving the existing claim pool.
- Non-goals: Replace CRM customer-pool behavior, change the existing claim-pool contract, execute migrations against a shared database, or change real permissions.
- Branch: `codex/aging-collab-pool`
- Worktree: `D:\ZSJ-OS-worktrees\aging-collab-pool`
- Base commit: `eaae3965805dc493cc6ad34aabede623843cb540`
- Target branch: `main`
- Ownership scope: ZSJOS lead/order/task services and contracts, System notification scene integration, ZSJOS Workbench/Admin UI, V033 SQL/bootstrap verification, and directly affected documentation.
- Owner: Codex `/root`
- Dependencies: Existing System user/department/post/notification APIs and existing ZSJOS lead, opportunity, order, task, permission, and inbox contracts.
- Integration order: Merge after reconciling current main-worktree sales-order and Workbench changes; rerun affected backend and frontend checks on the integration branch.
- Verification plan: Focused backend tests, ZSJOS Maven test and server package, Workbench test/typecheck/build plus desktop/mobile browser checks, Admin typecheck/lint/build plus browser check, and SQL syntax/repeatability/schema verification review.
- Final commit: `None` (changes intentionally remain uncommitted pending separate confirmation).
- Verification evidence: ZSJOS focused tests 37/37 passed; latest ZSJOS compile passed; latest `mvn -pl yudao-server -am -DskipTests package` passed after row-lock changes; Workbench 61 tests, typecheck, and production build passed; Admin targeted ESLint passed; `git diff --check` passed; V033 notification columns were checked against the current System schema and no overdue rule exists.
- Unresolved risks: Full Reactor tests are blocked by the unrelated existing `CodegenEngineUniappTest.testExecute_treeSearch` failure. Full Admin typecheck/lint/build is blocked by pnpm ignored dependency build scripts requiring interactive approval. Authenticated desktop/mobile browser flows, real HTTP success/empty/error/unauthorized states, migration execution, and live schema-difference verification remain environment-dependent and unverified.
- Dependency state: No Maven or npm dependencies added. Existing System organization/user/notification APIs and BPM/order facilities are reused.
- Status: `ready-to-merge`

## Delivery Entries

### 2026-08-12 12:08:37 +08:00

- Branch: `codex/aging-collab-pool`
- Worktree: `D:\ZSJ-OS-worktrees\aging-collab-pool`
- HEAD commit: `eaae3965805dc493cc6ad34aabede623843cb540`
- User goal: Implement the confirmed overdue collaboration pool plan end to end while preserving the existing claim pool.
- Key decisions: Maintain an independent aging-pool lifecycle; retain A as nominal owner until B's order becomes effective; freeze the entry department; authorize all UI actions from backend projections; use immutable order submitter identity for B; maintain append-only events and notification-rule-level idempotency; recover ownership start only from explicit assignment history.
- Execution or analysis result: Implemented backend lifecycle, scheduler, permissions, notifications, order conversion transaction, SQL/bootstrap/schema verification, Workbench and Admin interfaces, tests, and directly affected documentation. Removed temporary preview output and generated TypeScript build metadata from the delivery diff.
- Changed files: ZSJOS lead/order controllers, VOs, DOs, mappers, services, constants and tests; `frontend/workbench` aging-pool route/page/API/styles; `frontend/admin` aging-pool API/page and follow-up rule setting; `script/sql/mysql/migrations/V033__lead_aging_collaboration_pool.sql` plus bootstrap/schema verification artifacts; aging-pool API/operations and architecture/state-machine documentation; this handoff file.
- Verification evidence: Focused backend tests 37/37 passed; latest module compile passed; latest server assembly package passed; Workbench 61 tests, typecheck, and production build passed; Admin targeted ESLint passed; `git diff --check` passed; SQL notification template/rule columns match the checked System schema and no overdue rule was registered.
- Dependency or integration impact: No new dependencies. Merge requires reconciliation with current main-worktree sales-order and Workbench changes, followed by affected checks on the integration branch. Migration and permission synchronization require separate operational confirmation.
- Remaining work: Resolve the unrelated Infra Reactor-test failure; approve pnpm dependency build scripts before running full Admin checks; run authenticated desktop/mobile browser checks and real API authorization/error-state checks; execute V033 and schema-difference verification in a controlled environment; stage/commit/merge only after explicit confirmation.

### 2026-08-12 14:03:08 +08:00

- Branch: `codex/aging-collab-pool`
- Worktree: `D:\ZSJ-OS-worktrees\aging-collab-pool`
- HEAD commit: `eaae3965805dc493cc6ad34aabede623843cb540`
- User goal: Continue the confirmed implementation and complete the business/code remediation review for the overdue collaboration pool.
- Key decisions: Added focused coverage for the late new-tenant initialization refactor; kept notification default-rule seeding generic and idempotent; verified tenant creation publishes a synchronous domain event; changed Workbench filter consumption to locate the server-owned `all` group and aggregate all of its sections instead of depending on the first group/section position.
- Execution or analysis result: Confirmed the full Reactor failure remains isolated to the unrelated Infra `CodegenEngineUniappTest.testExecute_treeSearch`; added and passed new tenant-event, default-rule, and aging-pool initializer tests; removed the Workbench filter-ordering assumption; reviewed V033 guarded DDL, ownership recovery, bootstrap ordering, four-audience seed, version registration, and read-only verification queries. No additional backend or SQL defects were found in the final pass.
- Changed files: `backend/yudao-module-system/src/test/java/cn/iocoder/yudao/module/system/service/notify/NotifyRuleServiceImplTest.java`; `backend/yudao-module-system/src/test/java/cn/iocoder/yudao/module/system/service/tenant/TenantServiceImplTest.java`; `backend/yudao-module-zsjos/src/test/java/cn/iocoder/yudao/module/zsjos/service/lead/LeadAgingPoolTenantInitializerTest.java`; `frontend/workbench/src/pages/LeadAgingPoolPage.tsx`; generated `frontend/workbench/tsconfig.tsbuildinfo`; this handoff file.
- Verification evidence: Focused Maven Reactor test set passed with 46 tests (System 31, ZSJOS 15), including new-tenant initialization, aging-pool lifecycle, and sales-order continuation; Workbench 61 tests passed; Workbench typecheck and production build passed; V033/bootstrap/schema verification artifacts were statically reviewed; `git diff --check` passed with only line-ending warnings. Earlier module compile and server assembly package remain passed.
- Dependency or integration impact: No new dependencies. The generated TypeScript build metadata includes the new page and the available TypeScript compiler version and should not be treated as a business contract. Existing merge-order and integration-check requirements remain unchanged.
- Remaining work: Full Reactor tests remain blocked by the unrelated existing Infra codegen assertion. Full Admin checks remain blocked by pnpm ignored dependency build scripts requiring separate approval. Authenticated browser checks, real HTTP success/empty/error/unauthorized verification, controlled V033 execution, live schema-difference verification, staging, commit, push, and merge remain unperformed pending the relevant environment or explicit confirmation.
