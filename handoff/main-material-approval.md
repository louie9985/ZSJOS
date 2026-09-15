# Main material approval workstream
- ID: main-material-approval
- Owner: Codex current thread
- Goal: 素材库内审批爆款账号/内容，复用 BPM。
- Non-goals: 改动审批人策略、共享导航、业务状态机或扩大管理权限。
- Branch / target: main
- Worktree: D:\ZSJ-OS
- Base: 436ba84cd8ab5471356e3faa62db9ab44e3c76e9
- Ownership: 新增 MaterialApproval* 后端/测试；素材 BPM 跳转服务；Workbench MaterialApprovalPage、materialApprovalApi、RouteHost、constants；V233 菜单迁移和文档；本 handoff。
- Dependencies: existing BPM APIs and MaterialService.toVersionResp. Preserve all prior changes.
- Integration: serialized in current worktree; no commit or branch operation.
- Verification: focused backend authorization/decision tests, frontend tests/typecheck/build, local API and browser desktop/mobile; controlled SQL replay.

## Approved design
素材库新增独立素材审批菜单。使用类型筛选（服务端提供支持的类型）和待办/已办切换，任务详情展示提交版本快照及任务处理记录。服务端用 BPM 用户任务授权、业务键和流程实例匹配保护详情与决定；审批命令调用 BPM API。任务跳转返回新业务页。只授权部门主管新页面和通过/驳回按钮。已处理任务所指版本若已重提而无法还原旧快照，明确拒绝展示当前内容作为历史快照。

- Ownership expanded: menu.test.ts route count, own prior main.tsx projection regression correction, focused API documentation.

- Ownership expanded: existing ZsjosBpmBusinessTaskTargetServiceImplTest for material route regression cases.

- Ownership expanded: api.ts BPM target union and BpmApprovalCenterPage.tsx material permission error handling; targeted changes only.

- Design correction: removed standalone Workbench approval route/menu; approval is embedded in material management detail. Added V234 forward-only soft-disable migration for any locally applied V233 menu rows.

- Delivery: Beijing time 2026-09-14; branch main; changed MaterialLibraryPage to host approval actions via existing detail, changed BPM target route to /zsjos/material-library/manage, removed standalone route constant/host, added V234 soft-disable migration and docs. Verification: backend compile passed; focused backend MaterialApprovalService/Result/ObjectPermission/Submit and BPM target tests passed (24 total); Workbench tests 26 passed; typecheck and build passed; V233 SQL replay twice passed with zero scoped metadata drift; V234/live endpoint/browser verification pending user restart, as requested.

## Delivery 2026-09-14 21:00:09 Asia/Shanghai
- Branch: main; worktree: D:\ZSJ-OS; HEAD: 436ba84cd8ab5471356e3faa62db9ab44e3c76e9
- User goal: 修复素材管理详情布局及审批按钮缺失。
- Decisions: 管理菜单改为 workbench native；新增 manage 路由映射；详情字段分为账号主截图、账号详情、编导拆解、搭建建议四列；审批操作置于详情底部左侧。
- Result: 完成前端路由、布局和样式调整，V235 菜单同步脚本设置 native 渲染。
- Changed files: frontend/workbench/src/constants.ts; frontend/workbench/src/layouts/RouteHost.tsx; frontend/workbench/src/pages/MaterialLibraryPage.tsx; frontend/workbench/src/styles/pages/material-library.css; script/sql/mysql/migrations/V235__restore_material_management_approval_actions.sql
- Verification: frontend/workbench npm run typecheck passed; npm run build passed (仅有既有 chunk size warning)。未重启 48080，浏览器运行态待用户自行重启后验证。
- Dependency/integration impact: 无新增依赖；服务端菜单迁移需按 V235 执行。
- Remaining work: 用户重启本地服务并刷新权限缓存后确认菜单与审批按钮显示。

## Delivery 2026-09-14 21:05:00 Asia/Shanghai
- User goal: 修复素材管理入口显示空白的问题。
- Result: 管理路由进入页面时默认使用“全部”视图，素材浏览入口继续保持推荐视图。
- Changed files: frontend/workbench/src/pages/MaterialLibraryPage.tsx; frontend/workbench/src/layouts/RouteHost.tsx
- Verification: frontend workbench npm run typecheck passed。
- Remaining work: 重启服务后刷新浏览器验证。

## Delivery 2026-09-14 21:15:00 Asia/Shanghai
- User goal: 点击素材后不再显示普通详情页。
- Result: 移除普通详情渲染，仅保留素材列表和布局查看抽屉；点击素材直接设置 view 模式并打开布局。
- Changed files: frontend/workbench/src/pages/MaterialLibraryPage.tsx
- Verification: frontend workbench npm run typecheck passed。
- Remaining work: 重启服务或刷新开发前端后验证实际浏览器缓存。
