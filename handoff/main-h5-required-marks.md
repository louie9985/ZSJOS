# Workstream Registration

- ID: main-h5-required-marks
- Goal: 将兼职端现有必填标记统一到标签文字右上角。
- Non-goals: 不新增必填项，不修改校验、接口、权限、数据库或依赖；保留已有未提交改动。
- Branch: main
- Worktree: D:\code\ZSJOS
- Base commit: f338087e9aaa8b8d881cd0aee80427d8773ab284
- Target branch: main
- Ownership scope: frontend/h5/src/styles/vant-overrides.css; frontend/h5/src/pages/lead/submit.vue; frontend/h5/src/pages/lead/appeal.vue; frontend/h5/src/pages/studentInfo/index.vue; frontend/h5/src/components/ProductPicker.vue; frontend/h5/docs/ui-guidelines.md; handoff/main-h5-required-marks.md
- Owner: Codex /root
- Dependencies: 现有 Vue、Vant 和主题样式，无新增依赖。
- Integration order: 共用标记样式 → 自定义标签与申诉标签适配 → 文档 → 验证。
- Verification plan: H5 类型检查及生产构建、现有测试、浏览器桌面和移动宽度检查、限定范围差异检查。
- Status: active

## Scope Update

- Added scope: frontend/h5/tsconfig.tsbuildinfo（类型构建缓存）；output/playwright/required-marks/（本轮浏览器验证截图）。
- Browser plan: 内置浏览器启动返回 trusted browser service 不可用；使用 Playwright CLI。当前本地页面需要登录，因此用隔离的实际 Vue/Vant 组件与本次构建 CSS 检查标记几何位置，不读取登录凭据或提交业务表单。

## Delivery Entry - 2026-09-10 14:12:00 +08:00

- Branch: main
- Worktree: D:\code\ZSJOS
- HEAD commit: f338087e9aaa8b8d881cd0aee80427d8773ab284
- User goal: 将现在有标记的位置统一调整到标签文字右上角。
- Key decisions: 保留 Vant required 和动态 required 条件；全局样式隐藏原左侧星号，在实际 label 右上角生成星号；自定义标签共用 h5-required-label；申诉理由补充独立标签。不增加必填字段或修改校验逻辑。
- Execution result: 已覆盖所有既有固定标记，以及课程规格、反馈、学员信息动态标记。移除本次替换后无用的局部星号样式，同步 H5 视觉规范。
- Changed files: frontend/h5/src/styles/vant-overrides.css; frontend/h5/src/pages/lead/submit.vue; frontend/h5/src/pages/lead/appeal.vue; frontend/h5/src/pages/studentInfo/index.vue; frontend/h5/src/components/ProductPicker.vue; frontend/h5/docs/ui-guidelines.md; frontend/h5/tsconfig.tsbuildinfo（构建缓存）; output/playwright/required-marks/（6 张组件验证截图）; handoff/main-h5-required-marks.md。
- Verification evidence: npm run build 通过（含 vue-tsc）；node --test tests/*.test.mjs 13/13 通过；限定源码 git diff --check 通过。Playwright 使用实际 Vue/Vant 及本次生产构建 CSS，在 390/1280 宽度与 coral/sky/lavender 三套主题下检查 21 个代表性标记：右上定位、旧左侧标记隐藏、选填不标记、无横向溢出均通过；目视复核 390-coral 与 1280-sky 截图。长标签换行按标签文字块右上角定位。
- Dependency or integration impact: 无新增依赖、接口、权限或数据库改动；未提交、推送或操作分支；保留已有工作树改动。
- Remaining work: 登录后的业务整页和后台真实动态配置未验证，当前浏览器验证为隔离组件层级；业务页局部布局仍需真实登录环境回归。
- Status: completed
