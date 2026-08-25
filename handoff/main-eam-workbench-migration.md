# Workstream: main-eam-workbench-migration

- Workstream ID: `main-eam-workbench-migration`
- Status: `in_progress`
- Goal: 恢复 Workbench 服务端菜单的完整递归层级，并将 EAM 页面现有表格迁移到 Ant Design ProTable、加宽 EAM 表单弹窗且保持移动端可用。
- Non-goals: 修改服务端菜单、权限、路由或数据库；迁移 HRM/FMS 及其他业务表格；删除或下线 Vue Admin EAM；执行数据库破坏性操作；创建分支、提交、推送或部署。
- Branch: `main`
- Worktree: `/Users/louie/Documents/ChatGPT/ZSJOS 2`
- Base commit: `02961f62360e96a85c734c0b87c34a43dea77309` plus existing user changes
- Target branch: `main`
- Ownership scope: `frontend/workbench/src/services/menu.ts`, `frontend/workbench/src/services/menu.test.ts`, `frontend/workbench/src/layouts/{navItems,navItems.test,MobileNavDrawer}.tsx`, the navigation-specific portions of `frontend/workbench/src/main.tsx`, `frontend/workbench/src/pages/Eam*.tsx`, `frontend/workbench/src/styles/pages/eam.css`, focused EAM/menu tests, and this handoff file.
- Owner: Codex
- Dependencies: existing EAM admin APIs, System dictionary/user/department APIs, Infra file upload, existing Workbench dependencies; no new dependency planned.
- Integration order: recursive menu model and tests -> EAM ProTable migration -> EAM modal/form responsiveness -> focused/full verification -> browser smoke -> handoff.
- Verification plan: focused menu tests, full `npm test`, `npm run typecheck`, production build, `git diff --check`, and desktop/mobile browser smoke checks when the local runtime is available. Backend and database behavior are out of scope for this turn.

## Delivery Entry

- Beijing time: `2026-08-25 00:00:15`
- Branch: `main`
- Worktree: `/Users/louie/Documents/ChatGPT/ZSJOS 2`
- HEAD commit: `02961f62360e96a85c734c0b87c34a43dea77309`
- User goal: 保留 Vue Admin EAM 页面并行存在，完成 Workbench EAM 八个功能域迁移对齐；采用轻量验证。
- Key decisions: Workbench 资产表单严格按后端 `EamAssetSaveReqVO` 对齐，移除无法持久化的上级/入司日期/承诺字段；补齐原值、净值、来源、保修到期日、预计寿命；保留 Vue Admin EAM，不做下线、删除、菜单切换或 API 移除。
- Execution result: 修正 Workbench EAM 类型与资产列表自定义字段筛选；详情使用字典快照；编辑附件使用受控列表并保留已有附件；动态字段支持 FILE 上传并在定义加载失败/未完成时阻止保存；导入展示错误行并在 `errorCount > 0` 时阻止确认；统计页移除后端不存在的原值合计。
- Changed files: `frontend/workbench/src/services/api.ts`, `frontend/workbench/src/pages/EamAssetPage.tsx`, `frontend/workbench/src/components/DynamicFields.tsx`, `frontend/workbench/src/components/AssetImportModal.tsx`, `frontend/workbench/src/pages/EamStatisticsPage.tsx`, `frontend/workbench/src/services/useDict.ts`, this handoff file.
- Verification evidence: `npm run typecheck` passed; `npm run build` passed; focused `src/services/eam.test.ts` and `src/services/menu.test.ts` passed (25 tests); `git diff --check` passed. Full Workbench suite: 57 files/352 tests passed, 1 existing `src/services/menu.test.ts` baseline assertion failed because route set is 115 while assertion expects 113; failure is unrelated to EAM changes. No local backend/browser session was available, so real authenticated API/browser smoke remains unverified.
- Dependency/integration impact: no new npm/Maven dependency; relies on existing EAM, system dictionary/user/department, and infra upload APIs. Vue Admin EAM files already modified in the user worktree were preserved and remain active.
- Remaining work: optional follow-up is to reconcile the pre-existing route-count test baseline and run authenticated browser smoke when services are available; no EAM migration blocker remains for this turn.

## Delivery Entry

- Beijing time: `2026-08-25 09:40:59 +08:00`
- Branch: `main`
- Worktree: `/Users/louie/Documents/ChatGPT/ZSJOS 2`
- HEAD commit: `02961f62360e96a85c734c0b87c34a43dea77309`
- User goal: 恢复 Workbench 服务端菜单完整下拉层级；仅将 EAM 表格迁移为 Ant Design ProTable，并加宽 EAM 表单。
- Key decisions: EAM 列表保留现有服务端 API、筛选和权限操作语义；ProTable 启用刷新、密度、列设置、列状态持久化、全屏、横向滚动和可变分页；资产表单宽 900px，普通 EAM 表单宽 760px，双列布局在移动端降为单列；统计页无表格，不强行改造成表格。
- Execution or analysis result: 完成资产、分类字段、流转、盘点及盘点明细、维修、报废、编号规则的 ProTable 迁移和 EAM 弹窗/表单宽度调整。菜单递归模型与聚焦测试曾通过，但同一 worktree 的活动工作流 `main-workbench-hrm-pro-table` 随后并行覆盖同一批导航文件，并明确要求相反的“二级叶子平铺”行为；为避免继续互相覆盖，菜单整合暂停等待用户停止或协调该工作流。
- Changed files: `frontend/workbench/src/pages/EamAssetPage.tsx`, `frontend/workbench/src/pages/EamCategoryPage.tsx`, `frontend/workbench/src/pages/EamTransferPage.tsx`, `frontend/workbench/src/pages/EamInventoryPage.tsx`, `frontend/workbench/src/pages/EamRepairPage.tsx`, `frontend/workbench/src/pages/EamScrapPage.tsx`, `frontend/workbench/src/pages/EamCodeRulePage.tsx`, `frontend/workbench/src/styles/pages/eam.css`, navigation files currently shared with the conflicting HRM workstream, and this handoff file.
- Verification evidence: EAM service tests 12/12 passed; style guard tests 19/19 passed; EAM page TypeScript diagnostics are clear; EAM scoped and repository `git diff --check` passed. Full typecheck/build/browser verification cannot be completed while the concurrent HRM workstream leaves `HrmProTable.tsx` and shared navigation files in a transient failing state.
- Dependency/integration impact: no dependency, backend, database, Admin Vue, branch, commit, push, deployment, or shared-service change. Navigation files have an active ownership conflict with `handoff/workbench-hrm-migration.md`.
- Remaining work: stop or complete `main-workbench-hrm-pro-table`, then reconcile the shared navigation implementation to the user-confirmed recursive hierarchy; rerun focused/full tests, typecheck, build, and desktop/mobile browser checks.
