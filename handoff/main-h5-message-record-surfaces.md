# H5 消息与记录卡片样式工作流

- Workstream ID: main-h5-message-record-surfaces
- Goal: 消息中心概览复用 hero-surface；消息、提现、主题、系统反馈及投诉记录卡片统一为 #F7F9FD。
- Non-goals: 不改业务、权限、接口、导航、筛选、内容布局或其他卡片。
- Branch: main
- Worktree: D:\code\ZSJOS
- Base commit: f338087e9aaa8b8d881cd0aee80427d8773ab284
- Target branch: main
- Ownership scope: frontend/h5/src/pages/messages/index.vue; frontend/h5/src/pages/withdrawal/index.vue; frontend/h5/src/pages/profile/theme.vue; frontend/h5/src/pages/feedback/index.vue; frontend/h5/src/pages/lead/complaints.vue; frontend/h5/docs/ui-guidelines.md; frontend/h5/tsconfig.tsbuildinfo（构建产物）; handoff/main-h5-message-record-surfaces.md; output/playwright/message-record-surfaces/（如可完成浏览器检查）
- Owner: Codex /root
- Dependencies: 当前工作区已有 H5 样式及 hero-surface；无新增依赖。已有工作流交付后在当前 main 串行修改，保留全部既有改动。
- Integration order: 局部样式和模板 -> 同步视觉规范 -> 测试、类型检查、构建与浏览器检查 -> 交付记录。
- Verification plan: 现有 feedback-complaints 测试；npm run build（含 vue-tsc）；差异检查；可用登录态下检查三主题、手机/桌面、选中态及透明度降级。
- Status: active

## Delivery Entry - 2026-09-10 14:57:50 +08:00

- Branch: main
- Worktree: D:\code\ZSJOS
- HEAD commit: f338087e9aaa8b8d881cd0aee80427d8773ab284
- User goal: 实现上一轮梳理的消息中心、消息、提现、主题、反馈、投诉卡片样式方案。
- Key decisions: 消息中心概览复用现有 hero-surface；五类内容卡片通过 scoped CSS 使用 #F7F9FD；主题选中项仅保留主题边框与勾选，去除其三处主题背景覆盖。局部选择器优先级高于公共卡片降级背景；保留公共透明度降级及原布局、业务交互。按用户确认同步视觉规范的固定底色例外。
- Execution result: 指定页面样式及文档已修改；已对比修改前临时快照，变更限于模板类名、局部背景及直接相关文档，未覆盖原有未提交修改。
- Changed files: frontend/h5/src/pages/messages/index.vue; frontend/h5/src/pages/withdrawal/index.vue; frontend/h5/src/pages/profile/theme.vue; frontend/h5/src/pages/feedback/index.vue; frontend/h5/src/pages/lead/complaints.vue; frontend/h5/docs/ui-guidelines.md; frontend/h5/tsconfig.tsbuildinfo（构建更新）; handoff/main-h5-message-record-surfaces.md。
- Verification evidence: node --test tests/feedback-complaints.test.mjs 通过 3/3；npm run build 通过（vue-tsc -b 与 Vite，624 modules）；五个 Vue 文件 git diff --check 通过；与修改前快照的差异审阅通过。Playwright 实际访问 messages 后重定向至登录页，现有 default 会话也处于登录页；移动端/桌面端、三主题、选中态及透明度降级的真实页面视觉检查未完成。
- Dependency or integration impact: None；当前 main 串行修改，无新增依赖、提交、分支或外部服务调整。
- Remaining work: 在有效登录态下验收五个页面的视觉及主题/透明度状态。
- Status: implemented; visual verification pending authentication
