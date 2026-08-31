# Customer And Order Advanced Filter

- Workstream ID: `customer-order-advanced-filter`
- Goal: 为 React 员工工作台与 Vue 后台的客资、商机和订单业务对象池提供受控的跨对象高级筛选。
- Non-goals: 不覆盖申诉、投诉、重复复核、派单关系和规则配置；不改变菜单、角色授权或业务状态机；不执行数据库迁移。
- Branch: `codex/customer-order-advanced-filter`
- Worktree: `D:\ZSJ-OS-worktrees\customer-order-advanced-filter`
- Base commit: `80dd44a5e3706599fa3d1d45581b4fb8149e1574`
- Target branch: `main`
- Owner: Codex
- Dependencies: 复用现有 System 字典、地区、用户和 ZSJOS 产品接口；不新增 npm 或 Maven 依赖。
- Integration order: 独立完成并验证后合入 `main`。
- Ownership scope: `yudao-module-zsjos` 高级筛选契约、查询与测试；WorkBench 客资/订单业务池；Admin ZSJOS 客资/商机业务池；新增迁移、直接相关 API/架构文档与本 handoff。
- Verification plan: ZSJOS Maven 聚焦测试与模块测试；WorkBench test/typecheck/build；Admin typecheck/scoped lint/build；SQL 语法、索引、顺序和可重复性审查；可用环境下桌面/移动端浏览器检查。
- Status: merged
- Final feature commit: `a572d76795a4ef890cf1ac2ec885c81ac19f9d95`
- Final enhancement commit: `5ceba54de15d6b3d70dc0b5f534281a58a38efb1`
- Main synchronization commit: `64f701e1de516ee1651c925c8b330ceb64df21e9`
- Main reapply commit: `938bc5fbfe9f2acb37b37bef9bc2b174b1297777`
- Main merge commit: `3a927fa49f273c59e206c98ace74b8eb461ba1ee`

## Delivery Entries

### 2026-08-13 15:44 CST

- Branch: `codex/customer-order-advanced-filter`
- Worktree: `D:\ZSJ-OS-worktrees\customer-order-advanced-filter`
- HEAD commit: `80dd44a5e3706599fa3d1d45581b4fb8149e1574`
- User goal: Preserve and integrate all branch and worktree changes into `main`, including the discovered customer/order advanced-filter workstream.
- Key decisions: Kept the server-owned field/operator catalog and parameter-bound values; preserved existing endpoint permission and visibility checks; made the React timer ref compatible with the installed React TypeScript definitions; did not add dependencies, execute migrations, push, or change authorization.
- Execution or analysis result: Advanced filtering is wired into the scoped lead, opportunity, and order pools for both frontends. SQL expressions are constructed only from service-owned field/operator definitions, while user values remain MyBatis parameters. The workstream is ready for a feature commit and integration conflict resolution against current `main`.
- Changed files: ZSJOS advanced-filter controller/VO/service/query mapper and lead/order query integrations; Admin advanced-filter API/component and lead pool integrations; Workbench advanced-filter component, API types/services, lead/order page integrations and styles; this handoff.
- Verification evidence: `git diff --check` passed; Workbench `npm run typecheck` passed; Workbench `npm run build` passed with the existing large-chunk warning; Maven `-pl yudao-module-zsjos -am -DskipTests package` passed all 20 reactor modules. Workbench tests passed 18 files and 76 tests, while `loginFormCache.test.ts` could not load the existing junction-backed `jsencrypt` module from the primary worktree. The earlier full Maven test run was blocked before ZSJOS by the unrelated existing `CodegenEngineUniappTest.testExecute_treeSearch` failure in Infra. Admin verification remains for post-integration because this worktree has no independent Admin dependency installation.
- Dependency or integration impact: Must merge current `main` into this branch and preserve both branches' additions to `frontend/workbench/src/services/api.ts`; then rerun integrated frontend and backend checks.
- Remaining work: Commit, merge current `main` into the feature branch, resolve overlap, verify, merge into `main`, record merged status, and remove the worktree and local branch.

### 2026-08-13 15:48 CST

- Branch: `codex/customer-order-advanced-filter`
- Worktree: `D:\ZSJ-OS-worktrees\customer-order-advanced-filter`
- HEAD commit: `7042d30b79edb904723824b85805bb651ca5d26a`
- User goal: Keep the advanced-filter workstream separate and do not merge it into `main`.
- Key decisions: Restored the branch at its existing ready commit and recreated its registered worktree after reverting the accidental integration from `main`; no feature source was changed.
- Execution or analysis result: The advanced-filter commits remain preserved exclusively on `codex/customer-order-advanced-filter`; integration is deferred until a future explicit request.
- Changed files: This handoff status and delivery entry only.
- Verification evidence: The feature worktree is clean at `7042d30b79`; `main` has no file-tree difference from pre-integration commit `dc00d1b6a4`.
- Dependency or integration impact: None on `main`; the branch/worktree intentionally remain available and must not be removed as part of current cleanup.
- Remaining work: None for the current request. Future integration requires explicit confirmation.

### 2026-08-13 16:26 CST

- Branch: `codex/customer-order-advanced-filter`
- Worktree: `D:\ZSJ-OS-worktrees\customer-order-advanced-filter`
- HEAD commit: `9bb3c112ea1cc0e81d6ced7fbd41ba7f5eacf304`
- User goal: Continue the restored worktree and finish the approved customer/order advanced-filter implementation across the covered React Workbench and Vue Admin business pools.
- Key decisions: Kept the whitelist field protocol and parameter-bound values; aligned every new POST search endpoint and the catalog with the existing page permission codes; retained System dictionary/user APIs as authoritative option sources; emitted epoch-millisecond dates while accepting ISO offsets for compatibility; treated incomplete UI rows as drafts rather than active conditions; preserved the last successful list on filter errors and discarded stale responses; added only repeatable V046 indexes and did not execute them.
- Execution or analysis result: Completed controlled catalog options, date compatibility, two-level Element Plus filter editing, effective-condition tags/counting, per-option retry, request sequencing, failure retention, and endpoint permission alignment. Search remains inside the input and the Filter button occupies the former external-search position. Advanced conditions remain session-only and suppress optional status groups without changing fixed pool, tenant, data-scope, or BPM boundaries.
- Changed files: Advanced-filter controller/service and focused tests; permission annotations on claim-pool, aging-pool, and approval POST search endpoints; affected lead/order service tests; React advanced-filter component/test and covered pool request behavior; Vue advanced-filter API/components and three existing Admin pools; lead/order API documentation; bootstrap/core schema, migration README, and `V046__customer_order_advanced_filter_indexes.sql`; this handoff. `frontend/workbench/tsconfig.tsbuildinfo` has only generated end-of-file newline churn and is not a functional change.
- Verification evidence: 66 focused Java tests passed after the final permission/date changes. Workbench `npm run typecheck`, production build, and the 2 focused advanced-filter tests passed; the full suite passed 20 files and 79 tests, with only `loginFormCache.test.ts` blocked by the pre-existing junction target missing `D:/ZSJ-OS/frontend/workbench/node_modules/jsencrypt/lib/index.js`. Admin scoped ESLint and direct Vite `env.local` production build passed; build retained the existing Lightning CSS `*zoom: 1` warning. Full Maven remained blocked before ZSJOS by the unrelated Infra `CodegenEngineUniappTest.testExecute_treeSearch` failure recorded earlier. `git diff --check` passed. Bootstrap and core schema SHA-256 values matched exactly; V046 contains named-index existence checks, repeatable version writes, and no destructive SQL. The local Workbench loaded without console errors or horizontal overflow at 1280x720 and 390x844, but authenticated advanced-filter business pages could not be exercised because the browser had no signed-in session or running authenticated backend.
- Dependency or integration impact: No new npm or Maven dependency. V046 is additive and unexecuted. The branch contains uncommitted implementation/verification enhancements and must be committed before it can be marked ready-to-merge; commit, merge, migration execution, and push all still require separate explicit confirmation.
- Remaining work: Run authenticated desktop/mobile interaction checks for Drawer editing, status-group hide/restore, tag deletion, stale-response handling, and loading/empty/error/retry states; validate query plans with controlled real database data; optionally repair the primary Workbench dependency installation and rerun the one blocked test; resolve the generated `tsconfig.tsbuildinfo` newline-only diff during an authorized commit-preparation step; then commit and integrate only after explicit approval.

### 2026-08-13 16:30 CST

- Branch: `main`
- Worktree: `D:\ZSJ-OS`
- HEAD commit: `3a927fa49f273c59e206c98ace74b8eb461ba1ee`
- User goal: Merge the completed customer/order advanced-filter workstream into `main`.
- Key decisions: Because `main` had previously merged and then reverted this feature, first reverted the prior revert with commit `938bc5fbfe`, then merged `codex/customer-order-advanced-filter` with merge commit `3a927fa49f`. Preserved the unrelated uncommitted duplicate-contact parser work in `PersonMapper.java`, its DAL tests, and its handoff. Did not push or execute V046.
- Execution or analysis result: The complete advanced-filter feature and its final permission, request sequencing, Vue editor, testing, documentation, and migration enhancements are now integrated into `main` without conflicts.
- Changed files: This handoff entry only for the final delivery turn; feature files are recorded by merge commit `3a927fa49f` and reapply commit `938bc5fbfe`.
- Verification evidence: On integrated `main`, 66 focused Java tests passed; Workbench typecheck, 2 focused advanced-filter tests, and production build passed; Admin scoped ESLint and `env.local` production build passed with the existing Lightning CSS `*zoom: 1` warning; bootstrap and core schema SHA-256 values matched; `git diff --check` passed. The earlier authenticated browser and real-database query-plan limitations remain unchanged.
- Dependency or integration impact: `main` now contains V046 but the migration remains unexecuted. No dependency was added. No push was performed. Existing unrelated uncommitted duplicate-contact parser changes remain in the main worktree and were not staged or committed by this workstream.
- Remaining work: Execute V046 only with separate environment approval; run authenticated desktop/mobile business-page checks and controlled database query-plan validation; push `main` only when explicitly requested.
