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
- Main synchronization commit: `64f701e1de516ee1651c925c8b330ceb64df21e9`

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

### 2026-08-13 15:46 CST

- Branch: `main`
- Worktree: `D:\ZSJ-OS`
- HEAD commit: `59db5aab08c36c35ce69f0bd660e3b8f1ce19c4d`
- User goal: Merge every local feature branch and worktree into `main`, verify the integrated result, and remove the merged branches/worktrees.
- Key decisions: Merged feature commit `a572d76795a4ef890cf1ac2ec885c81ac19f9d95` after synchronizing current `main` in `64f701e1de516ee1651c925c8b330ceb64df21e9`; preserved both the dual-workbench configuration services and advanced-filter services in the shared React API module; performed no push, migration execution, service lifecycle operation, or authorization change.
- Execution or analysis result: `codex/customer-order-advanced-filter` was integrated into `main` by merge commit `59db5aab08c36c35ce69f0bd660e3b8f1ce19c4d`. Advanced filters remain subject to existing business endpoint permissions and data visibility, use a server-owned catalog, and bind user values as query parameters.
- Changed files: This handoff status and integration delivery entry. Integrated source files are recorded in the preceding entry and merge commit.
- Verification evidence: On integrated `main`, Workbench tests passed 20 files and 86 tests; `npm run typecheck` and `npm run build` passed with the existing large-chunk warning; Admin `pnpm build:local` passed with existing legacy CSS warnings; Maven `-pl yudao-module-zsjos -am -DskipTests package` passed all 20 reactor modules and compiled 385 main plus 38 test sources; `git diff --check` passed. Full Maven tests remain unverified because the earlier run stopped at the unrelated existing Infra `CodegenEngineUniappTest.testExecute_treeSearch` failure. Browser authorization/mobile verification and SQL execution were not performed.
- Dependency or integration impact: The advanced-filter and dual-workbench menu workstreams are both present on local `main`. Local `main` is ahead of `origin/main`; no remote state was changed.
- Remaining work: Commit this integration record, remove the registered feature worktree and local branch, delete verified stale physical worktree directories, and report the final inventory.
