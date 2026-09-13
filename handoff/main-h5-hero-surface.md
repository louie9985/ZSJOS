# main workstream

- Workstream ID: main-h5-hero-surface
- Goal: 统一 H5 客资、收益、首页排行榜卡片视觉，复用 submit-hero 的卡片样式体系
- Non-goals: 不改变业务逻辑、路由、权限、数据结构及专属内容布局
- Branch: main
- Worktree: D:\code\ZSJOS
- Base commit: f338087e9aaa8b8d881cd0aee80427d8773ab284
- Target branch: main
- Ownership scope: frontend/h5/src/styles/base.css; frontend/h5/src/pages/lead/submit.vue; frontend/h5/docs/ui-guidelines.md; handoff/main-h5-hero-surface.md; frontend/h5/src/pages/lead/list.vue; frontend/h5/src/pages/earnings/index.vue; frontend/h5/src/pages/home/index.vue
- Owner: /root
- Dependencies: None
- Integration order: None
- Verification plan: frontend/h5 构建或类型检查

## Delivery Entry - 2026-09-10 12:32:19 +08:00

- Branch: main
- Worktree: D:\code\ZSJOS
- HEAD commit: f338087e9aaa8b8d881cd0aee80427d8773ab284
- User goal: 确认后将提交卡片材质复用至客资工具栏、收益概览/分段底板、首页排行榜。
- Key decisions: 提取公共 hero-surface，保留页面布局、分段胶囊与指示器、吸顶；统一透明度降级。
- Execution result: 四处接入共享材质，提交页作为原始样式来源也使用公共类；删除收益分段底板未命中的后代选择器。
- Changed files: frontend/h5/src/styles/base.css; frontend/h5/src/pages/lead/submit.vue; frontend/h5/src/pages/lead/list.vue; frontend/h5/src/pages/earnings/index.vue; frontend/h5/src/pages/home/index.vue; frontend/h5/docs/ui-guidelines.md; handoff/main-h5-hero-surface.md。
- Verification evidence: npm run build 通过（vue-tsc -b 与 Vite）；diff check 发现首页已有文件尾空行，未修改非本次内容。浏览器插件初始化失败；现有 Playwright H5 会话落在登录页，真实页面桌面/移动端视觉验收未完成。
- Dependency or integration impact: None；既有页面未提交改动保留。
- Incident: 初次登记误覆盖 handoff/main.md；已用 Git 索引内容恢复该文件，与索引一致。覆盖前未读取原文件，无法确认或保证覆盖前的未提交历史记录已恢复；本次记录改用独立文件。
- Remaining work: 登录态下验收桌面/移动端卡片视觉；核实 handoff/main.md 是否存在覆盖前的未提交记录。
