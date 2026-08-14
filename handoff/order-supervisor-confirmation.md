# Order Supervisor Confirmation Workstream

- Workstream ID: `order-supervisor-confirmation`
- Goal: Add optional direct-supervisor confirmation to registration and finance sales-order approval tasks while preserving parallel-center approval.
- Non-goals: Migrate or restart in-flight process instances; infer or grant menu permissions from role names; modify historical migrations; change unrelated lead or customer behavior.
- Branch: `codex/order-supervisor-confirmation`
- Worktree: `D:\ZSJ-OS-worktrees\order-supervisor-confirmation`
- Base commit: `d64cd28c397e1a161a1a2372cd08642d254aced6`
- Target branch: `main`
- Ownership scope: BPM public task API and focused sign-task tests; ZSJOS sales-order supervisor-confirmation domain, APIs, tests, errors and notifications; workbench sales-order approval and supervisor-confirmation UI; next MySQL migration plus synchronized bootstrap/verification artifacts; directly affected sales-order architecture, API and operations documentation.
- Owner: Codex `/root`
- Dependencies: Existing Yudao Flowable sign-task behavior, System department leader/user APIs, ZSJOS sales-order dual-approval workflow and notification infrastructure.
- Integration order: BPM public API, ZSJOS persistence/service/API, SQL/bootstrap, workbench, documentation, focused and aggregate verification.
- Verification plan: BPM and ZSJOS focused unit tests; Maven module tests and server assembly build; workbench tests, typecheck and production build; desktop/mobile browser checks when runtime dependencies are available; SQL syntax/repeatability and bootstrap verification review.
- Status: `in-progress`

## Delivery Entry 2026-08-13 17:40 +08:00

- Branch: `codex/order-supervisor-confirmation`
- Worktree: `D:\ZSJ-OS-worktrees\order-supervisor-confirmation`
- HEAD commit: `d64cd28c397e1a161a1a2372cd08642d254aced6` (uncommitted worktree changes; no commit created)
- User goal: Implement成交订单双中心会签中的“申请主管确认”，包括 BPM 向前加签、主管独立待办、中心锁、直属负责人校验、审计、权限、SQL、前端和部署文档。
- Key decisions: Reused BPM native sign-task/comment/history/reject behavior; added `supervisor_confirmation_enabled` so existing rounds remain on legacy behavior; excluded all root/child department leaders from new ordinary reviewer pools; ordinary task list/count now traverses BPM pages and filters sign children without a fixed 1000-row bound; added `flowable:signEnable=true` to both new process nodes; supervisor menu/permission is seeded without role grants.
- Execution result: Implemented BPM public DTO/API and sign-child metadata, ZSJOS confirmation DO/mapper/service/controller/notifications/permissions/errors, center locking/versioned command handling/cancellation, workbench request and supervisor inbox pages, V047 migration plus bootstrap/schema/verification synchronization, and API/architecture/state/deployment documentation.
- Changed files: BPM task API/service/DTO/tests; ZSJOS order controller, service, permission, mapper, DO/VO/constants/errors/notifications/tests; workbench API, approval page, supervisor page, detail cards, route and responsive styles; BPMN definition; `V047__sales_order_supervisor_confirmation.sql`, bootstrap/schema/seed/verification SQL; four directly affected docs.
- Verification evidence: BPM focused tests passed (6 tests); ZSJOS focused tests passed (25 tests); ZSJOS compile passed; server assembly `mvn -f backend/pom.xml -pl yudao-server -am -DskipTests package` passed; workbench `npm test` passed (21 files, 91 tests), `npm run typecheck` passed, `npm run build` passed; `git diff --check` passed; bootstrap and core schema hashes match; browser smoke test loaded the route at desktop and 390x844 mobile widths in unauthenticated state without a blank page.
- Dependency/integration impact: Requires a newly published BPM definition version with sign enabled before new rounds are created, V047 migration execution, explicit administrator assignment of `zsjos:sales-order:review` and `zsjos:sales-order:supervisor-confirm`, and valid enabled non-leader reviewers/direct leaders in each tenant's approval configuration.
- Remaining work: Real MySQL migration/verification, live BPM publication, authenticated end-to-end HTTP chain, and authenticated desktop/mobile approval interaction remain environment-dependent and unverified. Full `mvn -f backend/pom.xml -pl yudao-module-zsjos -am test` was not green because the unrelated existing `CodegenEngineUniappTest.testExecute_treeSearch` in `yudao-module-infra` failed; the ZSJOS-focused suite passed. No commit, push, or branch publication performed.

## Integration Preparation 2026-08-14 16:00 +08:00
- Branch: `codex/order-supervisor-confirmation`
- Worktree: `D:\ZSJ-OS-worktrees\order-supervisor-confirmation`
- HEAD commit: `d64cd28c397e1a161a1a2372cd08642d254aced6`
- User goal: Commit and integrate all completed workstreams into local main.
- Key decisions: Preserve BPM as workflow source of truth; keep V047 and BPM publication unexecuted; merge after Lead numbering and before marker-only UI adjustments.
- Execution result: Integration authorization received; workstream marked ready to merge.
- Changed files: Existing supervisor-confirmation implementation and this handoff.
- Verification evidence: Existing BPM/ZSJOS tests, server package, Workbench checks, schema hash, browser smoke, and diff-check evidence remains applicable; integrated checks will be rerun on main.
- Dependency/integration impact: Requires controlled V047 application, BPM definition publication, permissions, and tenant reviewer configuration before runtime use.
- Remaining work: Create the feature commit, record it, merge into main, and run integrated verification without changing external state.
- Status: `ready-to-merge`
