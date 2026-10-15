# 员工反馈消息详情跳转

## Registration — 2026-10-15
- Environment: local
- Workstream: local-feedback-message-link-20261015
- Owner: Codex current chat
- Goal: 普通反馈通知及消息详情的查看业务入口打开关联的具体反馈。
- Non-goals: 后端、数据库、权限配置、管理端、审批催办语义及共享服务变更。
- Branch: main
- Worktree: D:\ZSJ-OS
- Base commit: 8df3f65eb379b142c0ce0cb84b21e621e59b79b9
- Ownership: frontend/workbench/src/services/notifyMessageAction.ts; frontend/workbench/src/services/notifyMessageAction.test.ts; frontend/workbench/test/feedback-notification.html; frontend/workbench/test/feedback-notification.tsx; frontend/workbench/test/feedback-notification-browser.py; docs/api/feedback-management.md; this handoff.
- Dependencies: 现有 FeedbackPage feedbackId 深链和业务权限校验；无新增依赖。目标文件无已有本地修改，保留其他并行改动。
- Verification: focused Vitest; TypeScript noEmit; Chrome 真实组件、隔离模拟接口验证两个消息入口、指定详情、非法 ID、权限失败和重试。
- Target branch / integration order: None

## Delivery — 2026-10-15
- 普通 feedback 业务消息新增统一候选识别和基于 bizId 的具体详情跳转；消息点击和消息详情“查看业务”共享该逻辑。只接受安全正整数 ID，遵循配置的 actionType，不使用模板链接覆盖目标。
- 审批催办分支优先保留，仍校验任务后进入对应审批任务；反馈详情沿用页面和后端现有授权。
- Updated docs: docs/api/feedback-management.md 通知入口说明。
- Verification: 3 个 Vitest 文件共 50 项通过（消息动作 44 项、消息列表守卫 4 项、消息中心守卫 2 项）；npm run typecheck -- --incremental false 通过。
- Browser: python frontend/workbench/test/feedback-notification-browser.py 通过。复用现有 localhost:5174；Chrome 1440/390 宽度验证消息动作、真实消息详情按钮、真实反馈页面指定详情、无权限/不存在及重试、缺少读取权限、非法 ID。接口为隔离模拟传输，未发送真实通知。
- Screenshot artifacts: C:/Users/EDY/AppData/Local/Temp/feedback-notification-browser (feedback-1440.png, feedback-390.png).
- Boundaries: 未修改数据库、权限、后端、共享服务；未部署或验证线上员工账号实际消息。无新增路由定义、构建配置或依赖，不适用生产构建验收。
- Ownership released. No commit or push.
