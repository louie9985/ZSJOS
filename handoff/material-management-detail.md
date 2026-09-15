# Workstream: material-management-detail

- Goal: 将素材管理改为 Admin Vue 嵌入页，并实现 Workbench 查看布局风格的详情抽屉与当前素材审批操作。
- Non-goals: 独立审批待办列表、审批历史页面、iframe 复用 React 页面、后端审批模型重构。
- Branch: main
- Worktree: D:\ZSJ-OS
- Base commit: 436ba84cd8ab5471356e3faa62db9ab44e3c76e9
- Target branch: main
- Ownership scope: frontend/admin 素材管理页面、frontend/workbench 路由适配、直接受影响文档与验证记录。
- Owner: root
- Dependencies: 现有素材详情/版本接口、素材审批接口、服务端菜单与权限配置。
- Integration order: 先完成 Vue 详情与审批接入，再切换 Workbench 菜单嵌入路由，最后更新文档并验证两端。
- Verification plan: Admin typecheck/lint/build；Workbench typecheck/build；素材详情与审批接口契约检查；浏览器验收抽屉布局、权限和异常状态。

## Delivery 2026-09-14 现阶段
- Beijing time: 2026-09-14
- Branch: main
- Worktree: D:\ZSJ-OS
- HEAD commit: 436ba84cd8ab5471356e3faa62db9ab44e3c76e9
- User goal: 素材管理使用 Admin Vue，详情采用查看布局并支持当前素材审批。
- Key decisions: 按用户确认修正 V235 菜单冲突，使素材管理为 admin_embed，并恢复审批权限作为管理页隐藏按钮权限。
- Execution result: 已更新 V235 菜单渲染模式、组件路径及审批权限父级/显示状态；业务页面改造尚未完成。
- Changed files: script/sql/mysql/migrations/V235__restore_material_management_approval_actions.sql；docs/superpowers/specs/2026-09-14-material-management-detail-design.md；docs/superpowers/plans/2026-09-14-material-management-detail-plan.md。
- Verification evidence: 已检查 V194/V233/V234/V235 菜单链路；未运行前端构建。
- Dependency or integration impact: 依赖 Admin embed 现有机制及素材审批 Controller；开发库同步待执行。
- Remaining work: 完成 Vue 四栏详情抽屉、左下角操作区、审批 API/UI 接入并运行两端验证。

## Delivery 2026-09-14 实施阶段
- Beijing time: 2026-09-14
- Branch: main
- Worktree: D:\ZSJ-OS
- HEAD commit: 436ba84cd8ab5471356e3faa62db9ab44e3c76e9
- User goal: 素材管理详情采用查看布局，左下角放操作区。
- Key decisions: Admin Vue 详情抽屉改为四栏响应式布局；操作区放在 footer 左下角；审批按钮暂不伪造 BPM taskId。
- Execution result: 已扩展详情布局、封面卡片、账号详情/编导拆解/搭建建议分栏及编辑/提交/停用/恢复按钮；新增审批 API 方法供后续接入。
- Changed files: frontend/admin/src/views/zsjos/material/index.vue；frontend/admin/src/api/zsjos/material/index.ts。
- Verification evidence: pnpm ts:check 执行失败，错误位于既有 rontend/admin/src/views/zsjos/materialType/index.vue:438 语法错误；未发现本次文件错误证据。
- Dependency or integration impact: 审批通过/驳回仍需从审批任务接口取得 taskId/versionId 后接入；当前页面不绕过 BPM。
- Remaining work: 接入当前素材审批任务查询及通过/驳回意见弹窗；修复或隔离既有 typecheck 错误后重跑构建。

## Delivery 2026-09-14 审批接通
- Beijing time: 2026-09-14
- Branch: main
- Worktree: D:\ZSJ-OS
- HEAD commit: 436ba84cd8ab5471356e3faa62db9ab44e3c76e9
- User goal: 直接查询代码和数据库接通素材审批。
- Key decisions: 详情按爆款类型查询当前用户未完成 BPM 待办，以 materialNo 匹配当前素材；提交使用真实 versionId/taskId；不伪造任务标识。
- Execution result: Admin API 新增审批待办分页与决策请求；详情加载接入待办匹配；左下角增加审批通过/驳回并要求审批意见；成功后刷新详情和列表。
- Changed files: frontend/admin/src/api/zsjos/material/index.ts；frontend/admin/src/views/zsjos/material/index.vue。
- Verification evidence: 定向 vue-tsc 未报告上述两个文件错误；完整 ts-check 仍受既有 materialType/index.vue:438 错误影响。
- Dependency or integration impact: 依赖后端 /zsjos/material-approval/page 与 /approve|reject、当前用户审批权限及 BPM 待办；未执行数据库写入。
- Remaining work: 浏览器真实账号验收审批按钮与任务匹配；处理既有 materialType 类型错误后运行完整构建。

## Delivery 2026-09-15 权限根因修复
- Beijing time: 2026-09-15
- Branch: main
- Worktree: D:\ZSJ-OS
- HEAD commit: 436ba84cd8ab5471356e3faa62db9ab44e3c76e9
- User goal: 修复用户 260 素材审批无操作权限。
- Key decisions: 通过 Redis 缓存与数据库对照定位问题；仅恢复角色 3002 到查询菜单 602138 的软删除关系。
- Execution result: system_role_menu(role_id=3002, menu_id=602138) 从 deleted=1 恢复为 deleted=0；三个审批菜单绑定均已有效；同步清理用户/角色/菜单权限缓存。
- Changed files: None（开发数据库与 Redis 定向状态修复）。
- Verification evidence: MySQL 查询确认 602138/602139/602140 均 deleted=0；Redis 定向删除返回 4 个键。
- Dependency or integration impact: 用户 260 重新请求时应获得素材审批查询权限；未改其他角色或业务数据。
- Remaining work: 用户重新登录并验证详情审批按钮；必要时确认审批通过/驳回 BPM 任务链路。
