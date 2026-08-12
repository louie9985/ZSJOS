# Workstream: order-repurchase-concurrency

- ID: `order-repurchase-concurrency`
- Goal: Implement lifecycle phase five: server-owned first-purchase/repurchase classification, system and external-customer repurchase entries, customer-level active-order constraints, dual-center approval concurrency and revision rounds, termination, and customer-aggregated order display.
- Non-goals: Refunds, cashback, withdrawals, exports, customer merge, independent customer management, complete opportunity management, WeCom/email delivery, or real database execution.
- Branch: `codex/order-repurchase-concurrency`
- Worktree: `D:\ZSJ-OS-worktrees\order-repurchase-concurrency`
- Base commit: `0b7f2f7e904ff703bbc84b5b92f22b0cc800d423`
- Target branch: `main`
- Ownership scope: ZSJOS customer/Lead/Opportunity/order contracts and persistence; order creation/revision/termination and BPM approval status-event boundaries; dual-center lock/idempotency/version enforcement; focused backend tests; additive V041/bootstrap artifacts; Workbench customer-order aggregation and repurchase entry UI; directly affected API, permission, state-machine, navigation, and deployment documentation.
- Owner: Codex `/root`
- Dependencies: Integrated lifecycle phases one through four, Person/Lead/Opportunity domain constraints, current dual-center sales-order BPM integration, System user/department APIs, Infra files, and server-owned menus/permissions.
- Integration order: Phase five follows phase four and is the final lifecycle implementation phase.
- Verification plan: Focused classification, ownership, customer-active-order, external identity conflict, revision-round, dual-center lock/version/BPM-task/idempotency, termination, permission, and aggregation tests; ZSJOS compile/tests; V041 static consistency; Workbench tests/typecheck/build; browser and real request checks when an environment is available.
- Status: `in-progress`

## Delivery Entries

### 2026-08-13 04:09:21 +08:00

- Branch: `codex/order-repurchase-concurrency`
- Worktree: `D:\ZSJ-OS-worktrees\order-repurchase-concurrency`
- HEAD commit: `0b7f2f7e904ff703bbc84b5b92f22b0cc800d423` (pre-delivery commit)
- User goal: Complete phase five with server-owned first-purchase/repurchase classification, customer-level repurchase, dual-center approval concurrency, original-order revision, creator termination, and customer-order presentation; then commit and merge automatically.
- Key decisions: Repurchase orders persist only `personId`; system customer Lead is request/permission context; external historical customer creation is allowed when no Person matches; duplicate/multiple/conflicting identities and Persons with a primary Lead are rejected; old continuation-order submission was removed; creator or formal sales owner may revise the original rejected order; creator may terminate at either approval center; BPM remains workflow owner through a minimal public cancellation API; approval commands lock order then current round and validate BPM task, round, versions, and node idempotency.
- Execution or analysis result: Implemented phase-five backend, Workbench, V041 migration/baseline, menu wiring, and directly affected documentation. Corrected the prior delivery document conflict that required a repurchase Opportunity, following the user-confirmed rule that repurchase does not link an Opportunity.
- Changed files: BPM process instance public API; ZSJOS order controllers/VOs/DOs/mappers/services/constants/errors and focused tests; Lead management/aging-pool revision actions; Workbench order services, forms, details, approval page, customer repurchase entry and external historical repurchase page; `V041__order_repurchase_and_concurrency.sql`, bootstrap/core schema/seed; sales-order API, permission-flow and lifecycle state-machine docs; this handoff.
- Verification evidence: `mvn -pl yudao-module-zsjos -am -Dtest=SalesOrderServiceImplTest,SalesOrderObjectPermissionServiceTest,LeadManagementServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false test` passed 38 tests; Workbench `npm run typecheck` passed; 17 Vitest files / 78 tests passed; production build passed; `git diff --check` passed; V041/bootstrap/schema/seed references and required columns/keys were statically checked.
- Dependency or integration impact: Adds no npm or Maven dependency. Adds a BPM public cancellation method delegating to the existing BPM service. V041 adds nullable Lead association for repurchase, formal sales attribution, repurchase/termination snapshots, current-round versions/idempotency keys, one-active-repurchase unique key, and a server-owned historical-repurchase menu copied to roles already holding order-create permission.
- Remaining work: Commit and merge to `main`, rerun integration checks, then mark merged. Real MySQL execution, real authenticated API requests, browser desktop/mobile verification, remote push, and shared-service changes were not performed.
- Status: `ready-to-merge`

### 2026-08-13 04:13:42 +08:00

- Branch: `main`
- Worktree: `D:\ZSJ-OS`
- HEAD commit: `944c0fb1afaa07c4c231da14addc60c29a893fd3`
- User goal: Integrate the completed fifth lifecycle phase and verify the merged result.
- Key decisions: Preserved all ready-to-merge phase-five decisions; no conflict resolution changed behavior during the non-fast-forward merge.
- Execution or analysis result: Feature commit `469ed3c0a7` was merged into `main` by merge commit `944c0fb1afaa07c4c231da14addc60c29a893fd3`.
- Changed files: Integration of the 41 phase-five files listed in the preceding delivery entry; this appended merged-status record.
- Verification evidence: On `main`, backend focused tests passed 38/38 (`SalesOrderServiceImplTest` 18 and `LeadManagementServiceImplTest` 20); Workbench typecheck passed; 17 Vitest files / 78 tests passed; production build passed; worktree was clean after removing generated `tsconfig.tsbuildinfo` change.
- Dependency or integration impact: Phase five now follows integrated phases one through four on `main`. No remote push, real migration, shared service reconfiguration, or publication occurred.
- Remaining work: Real MySQL V041 execution, authenticated API contract requests, and desktop/mobile browser verification remain environment-dependent release checks. The generated production bundle retains the existing large-chunk warning.
- Status: `merged`
