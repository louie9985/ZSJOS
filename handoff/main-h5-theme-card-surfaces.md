# H5 卡片主题联动修正

- Workstream ID: main-h5-theme-card-surfaces
- Goal: 将已梳理的 12 个页面固定底色卡片改为提交客资顶部同源主题渐变，覆盖骨架态、选中态和降级。
- Non-goals: 不改业务、数据、权限、布局、导航、主题定义及其他页面材质；不新增列表模糊。
- Branch: main
- Worktree: D:\code\ZSJOS
- Base commit: f338087e9aaa8b8d881cd0aee80427d8773ab284
- Target branch: main
- Ownership scope: frontend/h5/src/styles/base.css; frontend/h5/src/pages/earnings/index.vue; frontend/h5/src/pages/lead/list.vue; frontend/h5/src/pages/lead/follow-up.vue; frontend/h5/src/pages/lead/submit.vue; frontend/h5/src/pages/lead/complaints.vue; frontend/h5/src/pages/profile/edit.vue; frontend/h5/src/pages/profile/password.vue; frontend/h5/src/pages/profile/bank-cards.vue; frontend/h5/src/pages/profile/theme.vue; frontend/h5/src/pages/messages/index.vue; frontend/h5/src/pages/withdrawal/index.vue; frontend/h5/src/pages/feedback/index.vue; frontend/h5/docs/ui-guidelines.md; frontend/h5/tsconfig.tsbuildinfo（构建产物）; handoff/main-h5-theme-card-surfaces.md; output/playwright/theme-card-surfaces/
- Owner: Codex /root
- Dependencies: 当前 main 上既有 hero-surface 和前述已交付卡片修改；保留已有未提交改动，串行执行。
- Integration order: 抽取背景变量 -> 替换局部固定背景和降级覆盖 -> 同步规范 -> 验证与交付。
- Verification plan: 全量固定色残留检查、修改前快照比较、现有测试、类型检查与生产构建；浏览器检查三主题、桌面/移动端、骨架/选中/减少透明度和不支持模糊状态；真实页面登录受限时明确记录。
- Status: active

## Delivery Entry - 2026-09-10 15:11:00 +08:00

- Branch: main
- Worktree: D:\code\ZSJOS
- HEAD commit: f338087e9aaa8b8d881cd0aee80427d8773ab284
- User goal: 修正所有已梳理的固定底色卡片，使其随主题变化，修改后检查是否有遗漏。
- Key decisions: 从 hero-surface 原有配方抽取 --h5-content-surface；12 个页面的 16 处固定背景统一引用该变量；根级变量在不支持模糊和减少透明度时分别使用同源降级背景。保留现有布局和模糊配置；主题选中边框和预览继续使用现有规则。同步取消文档中的固定底色例外。
- Execution result: 12 个页面修改完成，含客资提交表单、列表/跟进骨架及收益/客资列表降级覆盖；src 与视觉规范中无 #F7F9FD 或旧固定底色规则残留。修改前快照比较确认所有 Vue 文件只有背景值替换，公共样式及文档差异已审阅。
- Changed files: frontend/h5/src/styles/base.css; frontend/h5/src/pages/earnings/index.vue; frontend/h5/src/pages/lead/list.vue; frontend/h5/src/pages/lead/follow-up.vue; frontend/h5/src/pages/lead/submit.vue; frontend/h5/src/pages/lead/complaints.vue; frontend/h5/src/pages/profile/edit.vue; frontend/h5/src/pages/profile/password.vue; frontend/h5/src/pages/profile/bank-cards.vue; frontend/h5/src/pages/profile/theme.vue; frontend/h5/src/pages/messages/index.vue; frontend/h5/src/pages/withdrawal/index.vue; frontend/h5/src/pages/feedback/index.vue; frontend/h5/docs/ui-guidelines.md; frontend/h5/tsconfig.tsbuildinfo（构建更新）; output/playwright/theme-card-surfaces/; handoff/main-h5-theme-card-surfaces.md。
- Verification evidence: 现有 feedback-complaints 测试 3/3；npm run build（vue-tsc -b、Vite 624 modules）通过；git diff --check 通过。浏览器隔离检查编译实际 Vue scoped CSS，15 个卡片状态 × 3 主题 × 2 宽度（390/1280）× 3 模式共 270 项背景比较通过，各主题背景互异且与顶部参照一致；减少透明度使用媒体模拟，不支持模糊通过强制启用对应 CSS 分支模拟。普通模式生成 6 张截图并查看珊瑚粉手机图确认背景色调。结果位于 output/playwright/theme-card-surfaces/result.txt。
- Dependency or integration impact: None；无新增依赖、提交或分支；保留既有未提交改动。
- Remaining work: 实际主题页面重定向到登录页，真实业务内容和完整移动端/桌面交互验收仍需有效登录态；隔离样式检查不替代真实页面验收。
- Status: implemented; isolated style checks passed; live page verification pending authentication
