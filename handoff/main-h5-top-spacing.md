# H5 主页面顶部间距

- Workstream ID: `main-h5-top-spacing`
- Goal: 将提交客资、客资列表、收益、我的四页顶部内容间距对齐首页的固定 20px。
- Non-goals: 不改变业务逻辑、接口、路由、其他页面或现有未提交修改。
- Branch: `main`
- Worktree: `D:\code\ZSJOS`
- Base commit: `f338087e9aaa8b8d881cd0aee80427d8773ab284`
- Target branch: `main`
- Ownership scope: `frontend/h5/src/pages/lead/submit.vue`; `frontend/h5/src/pages/lead/list.vue`; `frontend/h5/src/pages/earnings/index.vue`; `frontend/h5/src/pages/profile/index.vue` 的顶部间距声明；`frontend/h5/docs/ui-guidelines.md` 的对应说明；本记录。
- Owner: Codex 当前任务
- Dependencies: 现有 Vue、Vite、PostCSS 工具链，无新依赖；保留既有工作树修改。
- Integration order: 局部 CSS 调整 → 构建和布局验证 → 交付记录。
- Verification plan: 类型检查与生产构建；移动端和桌面宽度布局检查；限定范围 diff 检查。
- Authorization: 用户已确认四页间距调整范围。

## Delivery - 2026-09-10 12:13:11 +08:00

- Beijing time: `2026-09-10 12:13:11 +08:00`
- Branch: `main`
- Worktree: `D:\code\ZSJOS`
- HEAD commit: `f338087e9aaa8b8d881cd0aee80427d8773ab284`
- User goal: 四个主页面内容稍微下移，参考首页顶部距离；用户已确认实施。
- Key decisions: 仅调整四个首个内容块的 margin-top 为固定 20px；使用大写 PX 避免 px-to-rem 转换，匹配首页 norem 的实际固定间距。
- Execution result: 提交页从 10px 设计值、收益和我的从 12px 设计值、列表从共享卡片 14px 设计值改为固定 20px；不变更其余布局或业务逻辑。
- Changed files: `frontend/h5/src/pages/lead/submit.vue`; `frontend/h5/src/pages/lead/list.vue`; `frontend/h5/src/pages/earnings/index.vue`; `frontend/h5/src/pages/profile/index.vue`; `frontend/h5/docs/ui-guidelines.md`; 本记录。构建更新已有修改的 `frontend/h5/tsconfig.tsbuildinfo`。
- Verification evidence: `npm run build` 通过，包含 vue-tsc 类型检查和 Vite 生产构建（618 模块）；构建 CSS 四个目标选择器均保留 margin-top:20px；限定范围 git diff --check 通过。浏览器访问首页重定向登录页，缺少认证态，未完成四页移动端和桌面实页视觉验证；未模拟权限或业务数据。
- Dependency or integration impact: 无新依赖、API、权限、数据库、服务或分支变更；保留已有未提交修改。内置 browser 工具缺少可信浏览器服务，使用 Playwright CLI 确认访问受登录限制。
- Remaining work: 已登录状态下复核四页实际视觉效果。
- Status: `implemented; visual verification pending authentication`

## Delivery Entry - 2026-09-10 19:01:46 +08:00

- Beijing time: `2026-09-10 19:01:46 +08:00`
- Branch: `main`
- Worktree: `D:\code\ZSJOS`
- HEAD commit: `f338087e9aaa8b8d881cd0aee80427d8773ab284`（未提交工作树）
- User goal: 将客资列表页顶部与收益页、提交客资页对齐。
- Key decisions: 仅将 `frontend/h5/src/pages/lead/list.vue` 的 `.lead-toolbar` 顶部间距从 `12px` 调整为 `20PX`；不改变业务逻辑、路由、接口或现有工具栏结构。
- Execution or analysis result: 客资列表首个内容块与提交客资、收益页统一使用 20px 顶部间距。
- Changed files: `frontend/h5/src/pages/lead/list.vue`；本记录。
- Verification evidence: `npm --prefix frontend/h5 run build` 通过（vue-tsc 与 Vite，627 modules）；目标文件 `git diff --check` 通过。全仓库 `git diff --check` 仍报告既有 `handoff/main.md`、`frontend/h5/src/pages/home/index.vue` 问题，未修改这些无关内容。
- Dependency or integration impact: 无新增依赖、接口、数据库、权限、服务或分支变更；构建更新了已有修改的 `frontend/h5/tsconfig.tsbuildinfo`。
- Remaining work: 登录态下复核三个页面的实际视觉效果。
- Status: `implemented; visual verification pending authentication`
