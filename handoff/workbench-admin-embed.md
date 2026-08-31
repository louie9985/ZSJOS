# Workstream: workbench-admin-embed

- ID: `workbench-admin-embed`
- Goal: 从 `main` 删除 HRM/FMS/EAM React Workbench 实现，并让服务端菜单可配置使用 Workbench 原生页面或同源 Vue Admin 内容嵌入。
- Non-goals: 不删除 Vue Admin、后端业务 API、数据库业务表或菜单权限；不重写已有 Git 历史；不提交当前无关未提交修改。
- Branch: `main`
- Worktree: `/Users/louie/Documents/ChatGPT/ZSJOS 2`
- Base commit: `6cc39f1d28ee3273bbfc3f8832a814b91905b1a9`
- Target branch: `origin/main`
- Ownership scope: System 菜单渲染元数据、Admin 菜单表单/嵌入布局与认证、Workbench 模块清理/嵌入/认证/本地代理、对应 SQL/测试/架构和开发文档、本文件。
- Owner: Codex `/root`
- Dependencies: 三个本地模块分支完成并提交后再清理 `main`。
- Integration order: HRM/FMS/EAM 本地分支 -> `main` 清理与嵌入 -> 验证 -> 仅推送 `origin/main`。
- Verification plan: Workbench 测试/typecheck/build；Admin ts:check/lint/build；System 聚焦测试与 Maven 编译；SQL 语法/顺序/可重复性审查；桌面和移动浏览器验证；远端 HEAD 复核。
- Status: completed

## Delivery log

### 2026-08-25 18:29:31 CST

- Branch: `main`
- Worktree: `/Users/louie/Documents/ChatGPT/ZSJOS 2`
- HEAD commit: `6cc39f1d28ee3273bbfc3f8832a814b91905b1a9` (delivery commit follows this entry)
- User goal: 将 Workbench 的 HRM、FMS、EAM 实现分别保存在三个本地分支，从 `main` 删除这些实现；让后端菜单可选择 Workbench 原生、嵌入 Vue Admin 内容页或仅在 Vue Admin 显示，并且只推送清理后的 `main`。
- Key decisions: 三个模块分支仅保存在本地，不推送；菜单权限和后端授权保持不变；`workbench_render_mode` 只负责呈现方式；嵌入页使用同源 iframe 和共享 `localStorage` token，不通过 URL 或 `postMessage` 传 token；本地由 Workbench 5174 统一对外，并代理 Admin 5175、`/admin-api` 和 `/infra/ws`。
- Execution result: 本地分支 `codex/workbench-hrm`=`e3245b28`、`codex/workbench-fms`=`f70d5cbc`、`codex/workbench-eam`=`11681a57` 已提交；`main` 已移除三个模块的 Workbench 页面、组件、服务、路由、样式及 FMS 迁移说明，并加入服务端菜单配置、Admin 内容模式、共享认证、Workbench iframe 与开发代理。
- Changed files: System 菜单 DO/VO；`script/sql/mysql/00-bootstrap-schema.sql`、`schema/core.sql`、`bootstrap.sql`、`migrations/V132__workbench_menu_render_mode.sql`；Admin 菜单表单、认证、布局、嵌入环境；Workbench 常量、菜单过滤、路由宿主、认证服务、代理、嵌入页、样式及已删除的 `src/pages/{Hrm*,Eam*,fms/**}`、对应 components/services/styles；相关架构文档和 `frontend/workbench/README.md`；本 handoff。
- Verification evidence: Workbench `npm test` 通过（59 files / 366 tests）、`npm run typecheck` 通过、`npm run build` 通过；Admin `pnpm build:local` 通过，本次文件 ESLint/Stylelint 定向检查通过；System `mvn -pl yudao-module-system -am -DskipTests compile` 通过；`git diff --cached --check` 通过；V132 初始化、迁移和 bootstrap 引用静态核对一致。Admin 全仓 lint 仍被非本次文件的 125 个既有 Stylelint 错误阻断，`pnpm ts:check` 仍被非本次文件的 19 个既有类型错误阻断。
- Dependency or integration impact: 部署端需将 `/` 路由到 Workbench、`/admin-embed/` 路由到 Admin、`/admin-api/` 路由到后端，并为 `/infra/ws` 启用 WebSocket upgrade；应用 V132 后方可在菜单管理中配置呈现方式。三个模块后续开发从各自本地分支继续，不能假设远端存在这些分支。
- Remaining work: 本地后端未启动，尚未用真实账号验证动态菜单进入嵌入内容页、401 并发刷新和权限拒绝场景；上线前需在部署代理和真实权限数据上完成一次联调。
