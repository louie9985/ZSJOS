# 素材管理详情与审批实施计划

## 1. Admin Vue 页面

- 扩展 rontend/admin/src/views/zsjos/material/index.vue 的详情抽屉，复刻 Workbench 查看布局的四栏结构。
- 复用 MaterialDynamicForm 和服务端版本数据，增加封面、账号详情、编导拆解、搭建建议分组展示。
- 增加左下角固定操作区，按 vailableActions 与按钮权限显示编辑、提交、审批、驳回、停用、恢复。
- 接入素材审批 API、审批意见弹窗、处理中锁定、成功刷新详情和列表、失败保留上下文。
- 补齐 loading、empty、error、retry 和无权限状态。

## 2. Admin API 与类型

- 检查并扩展 rontend/admin/src/api/zsjos/material/index.ts，补齐审批查询、通过、驳回请求及响应类型。
- 保持审批意见、素材版本号、任务标识与后端契约一致，不新增客户端授权判断。

## 3. Workbench 菜单嵌入

- 将 rontend/workbench/src/layouts/RouteHost.tsx 的素材管理分支改为 Admin embed 机制。
- 保留素材浏览的 React 原生页面。
- 更新路由/菜单测试，确保管理路径不再渲染 MaterialLibraryPage。

## 4. 文档与权限

- 同步更新 docs/frontend/zsjos-menu-coverage.md，明确管理页为 Admin embed、详情布局和审批职责。
- 核对审批菜单按钮权限与 Admin 菜单覆盖，不新增静态权限或角色推断。

## 5. 验证

- Admin：pnpm ts:check、pnpm lint、pnpm build:local。
- Workbench：
pm run typecheck、
pm run build。
- 运行相关单元测试；浏览器验证列表筛选、详情四栏布局、左下角操作、审批通过/驳回、权限隐藏、失败重试和窄屏滚动。

