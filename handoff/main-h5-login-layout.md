# Workstream: main-h5-login-layout

- ID / Owner: main-h5-login-layout / Codex 当前任务
- Goal: 调整登录按钮布局，增加文字激活入口及协议勾选项。
- Non-goals: 不修改认证接口、租户、权限、企业微信自动登录或其他工作区改动；不编造协议正文。
- Branch / Target branch: main / main
- Worktree: D:\code\ZSJOS
- Base commit: f338087e9aaa8b8d881cd0aee80427d8773ab284
- Ownership scope: frontend/h5/src/pages/login/index.vue; frontend/h5/docs/login.md; handoff/main-h5-login-layout.md; frontend/h5/tsconfig.tsbuildinfo（构建产物）。
- Dependencies: 现有 Vue、Vant、useAuth；协议正式链接待用户提供，无新增依赖。
- Integration order: 登录布局与激活入口 → 页面说明 → 构建和浏览器验证 → 交付记录。
- Verification plan: vue-tsc、Vite 生产构建；检查登录按钮并排、激活切换与返回、协议勾选、桌面和移动端布局；核对原激活接口调用链。
- Status: active

## Delivery - 2026-09-10 15:19:00 +08:00
- Beijing time: 2026-09-10 15:19:00 +08:00
- Branch / Worktree: main / D:\code\ZSJOS
- HEAD commit: f338087e9aaa8b8d881cd0aee80427d8773ab284
- User goal: 实现截图中的登录页布局与原激活流程入口调整。
- Key decisions: 移除顶部切换按钮；登录与企业微信登录等宽同行；新增文字激活及返回入口、协议勾选项；保留原激活校验与接口链。协议默认未勾选，不新增认证拦截；无正式协议来源时点击提示暂未配置。
- Execution result: 已完成布局和激活入口调整，保留登录页原有透明背景及其他工作区修改。
- Changed files: frontend/h5/src/pages/login/index.vue; frontend/h5/docs/login.md; handoff/main-h5-login-layout.md; frontend/h5/tsconfig.tsbuildinfo（构建产物）。
- Verification evidence: npm run build（vue-tsc、Vite）通过；登录页 git diff --check 通过；Playwright 390×844 与 1280×900 两宽度按钮等宽同高且无横向溢出，截图复核通过；勾选、打开四字段激活表单及返回登录通过；静态核对 useAuth.activateWithInvite 到原 activate API 调用链。浏览器仅有既有 favicon 404 和密码字段非 form 提示。
- Dependency or integration impact: None；无新增依赖或后端改动。
- Remaining work: 用户提供正式协议链接或正文后接入；真实账号登录、企业微信授权、账号激活提交未执行，服务端端到端结果未验证。
- Status: layout-delivered; agreement-content-pending
