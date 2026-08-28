# Workbench Mobile Adaptation Workstream

## Workstream Registration - 2026-08-28 17:10:00 +08:00

- Workstream ID: `workbench-mobile-adapt`
- Goal: 让 ZSJOS 员工工作台公共框架适配手机端，采用「移动 Header + 授权菜单抽屉」，同时保留桌面端现有导航与布局能力，不改变业务、认证、租户、菜单或权限契约。
- Non-goals: 不实现底部 Tab；不改业务页面内部组件、表格、查询表单、详情或业务交互；不改后端、SQL、菜单/权限初始化数据、`frontend/admin`；不加 npm 依赖；不新增第二套静态生产菜单或硬编码角色名/用户 ID/权限列表；不改公开 URL 或相对子路径契约。
- Branch: `main`
- Worktree: `/Users/louie/Documents/ChatGPT/ZSJOS 2`
- Base commit: `d4b1de1f7b5b35abd6ece0a578844424c300c8bb`，并保留当前工作树全部既有未提交改动（均为 Lead 业务页/样式及 `handoff/main.md`，与公共框架无关）。
- Target branch: 当前本地 `main`。
- Ownership scope: `frontend/workbench/src/main.tsx` 的 Shell/布局分支、`frontend/workbench/src/layouts/MobileNavDrawer.tsx`、`frontend/workbench/src/layouts/navItems.tsx`（只读复用，未修改）、`frontend/workbench/src/layouts/navItems.test.tsx`（新增抽屉展开链测试）、`frontend/workbench/src/services/menu.ts`（只读复用既有 `getNavigationOpenKeys`，未修改）、`frontend/workbench/src/styles/layout.css`、`frontend/workbench/src/styles/tokens.css`（如新增语义变量，本次未改动）、相关测试与本 handoff 条目。
- Owner: Claude (workbench-mobile-adapt)
- Dependencies: 既有 antd `Drawer`/`Menu`、服务端授权菜单投影 `navigation`；无新增依赖。
- Integration order: 隐藏移动端常驻侧栏 → 精简移动 Header（产品标识 + 汉堡 + 必要用户入口）→ 修复抽屉展开链 / 选中 / 滚动锁定 → `100dvh` 视口与单滚动区 → 校验并回归 → 追加交付证据。
- Verification plan: `npm test`（重点 `styles.guard.test.ts` 不因新移动规则破防）、`npm run typecheck`、`npm run build`；桌面宽（1200px+）与移动宽（390px）真实浏览器检查抽屉开合、路由/返回/刷新/直达深层路由后的选中与展开、遮罩/Esc/关闭、背景滚动锁定、无整页横向滚动、单主滚动区、接单与用户入口可操作性；确认权限、登录/退出、未登录跳转行为不变。

## 文件所有权声明

本工作流独占修改的公共框架文件（未在其他活跃 handoff 中登记）：
- `frontend/workbench/src/main.tsx`
- `frontend/workbench/src/layouts/MobileNavDrawer.tsx`
- `frontend/workbench/src/layouts/navItems.tsx`
- `frontend/workbench/src/services/menu.ts`
- `frontend/workbench/src/styles/layout.css`
- `frontend/workbench/src/styles/tokens.css`
- 本工作流新增的测试文件与文档

不触碰：`handoff/main.md`、`frontend/workbench/src/styles/styles.guard.test.ts`（外部 Lead 工作流已改动，本工作流如需登记新规则需串行协调）、全部业务页面文件。

## Delivery Entry - 2026-08-28 17:45:00 +08:00

- Workstream ID: `workbench-mobile-adapt`
- Branch: `main`
- Worktree: `/Users/louie/Documents/ChatGPT/ZSJOS 2`
- HEAD commit: `d4b1de1f7b5b35abd6ece0a578844424c300c8bb`（未创建提交）。
- User goal: 让 ZSJOS 员工工作台公共框架适配手机端，采用「移动 Header + 授权菜单抽屉」，保留桌面端现有导航与布局，不改变业务/认证/租户/菜单/权限契约。
- Key decisions: 按已确认推荐方案 —— 移动端隐藏全部常驻侧栏（含 56px 图标栏），抽屉为唯一导航入口；移动 Header 精简为「汉堡 + 品牌/当前页名 + 必要用户入口」，接单三段控件窄屏隐藏（状态由全局接单 Alert 提供）；不做底部 Tab；不加 npm 依赖。
- Execution or analysis result: 在既有 `MobileNavDrawer` 基础上补齐移动框架心智 —— 抽屉展开键由 `defaultOpenKeys`（仅挂载读取一次，无法覆盖深层路由/刷新/返回）改为受控 `openKeys`，用既有 `getNavigationOpenKeys` 求当前页完整祖先链，并在每次打开抽屉时同步；打开抽屉锁定背景滚动、关闭恢复；`main.tsx` 移除已失效的 matchMedia 收起一级栏逻辑（侧栏由 CSS 整体隐藏）；新增移动品牌/当前页单元格 `mobile-header-brand`；用 `.header-dispatch-control` 包裹接单控件以便窄屏隐藏；`layout.css` 在 ≤768px 隐藏 `.primary-sider/.secondary-sider/.single-sider/.mini-sider`，并加 `@supports (height: 100dvh)` 使外壳/水印/内容区跟随移动视口高度，`vh` 兜底在前。未改任何业务页、后端、SQL、菜单/权限数据、公开 URL 或相对子路径契约。
- Changed files: `frontend/workbench/src/main.tsx`、`frontend/workbench/src/layouts/MobileNavDrawer.tsx`、`frontend/workbench/src/layouts/navItems.test.tsx`、`frontend/workbench/src/styles/layout.css`、`handoff/workbench-mobile-adapt.md`。（`navItems.tsx` 与 `services/menu.ts` 只读复用，未修改。）
- Verification evidence: `npm run typecheck` 通过；`npm test` 447 通过；新增 2 个抽屉 open-keys 用例通过（`navItems.test.tsx` 12 项全绿）；`styles.guard.test.ts` 27 项全绿（新移动规则无硬编码色/字号/间距，均走 token）；`npm run build` 通过（仅既有大 chunk 警告）。全套件仍余 3 个文件 6 个用例失败，均为与本任务无关的既有业务守卫（`announcement.guard.test.ts` 期望 `/zsjos/announcements` 而 `constants.ts` 仍为 `/announcements`；`media-students.guard.test.ts`、`subordinate-lead-actions.guard.test.ts` 属 media-subordinate/公告进行中工作流），已确认不引用本任务任何改动符号。真实浏览器登录态未可用，桌面/移动交互与本任务的视觉细节未做实测。
- Dependency or integration impact: 无新增 npm/Maven 依赖，无数据库/SQL、菜单、权限、服务、租户、分支/工作树、提交或推送改动；保留其他未提交改动。
- Remaining work: 在有可用登录会话的环境对桌面宽（≥1200px）与移动宽（约 390px）做真实浏览器验收 — 抽屉开合、路由/返回/刷新/直达深层路由后的选中与展开、遮罩/Esc/关闭、背景滚动锁定、无整页横向滚动、单主滚动区、接单与用户入口可操作性，以及 5 个布局模式在移动端的呈现。

## Delivery Entry - 2026-08-28 17:50:00 +08:00

- Workstream ID: `workbench-mobile-adapt`
- Branch: `main`
- Worktree: `/Users/louie/Documents/ChatGPT/ZSJOS 2`
- HEAD commit: `d4b1de1f7b5b35abd6ece0a578844424c300c8bb`（未创建提交）。
- User goal: 修复移动端"整页等比缩小、响应式完全不生效"的根因 —— 用户提供的只读诊断指出 `frontend/workbench/index.html` 缺少 viewport 声明。
- Key decisions: 采纳用户诊断。补全入口 HTML 的标准文档骨架（DOCTYPE/html lang/head/charset/viewport/title），与 `frontend/admin/index.html`、`frontend/h5/index.html` 的既有约定一致；workbench 取 `width=device-width, initial-scale=1.0`（不加 h5 的 maximum-scale/user-scalable 限制，保留用户缩放能力）。新增入口守卫测试防止再次静默丢失。
- Execution or analysis result: 原 `index.html` 是无 head 的单行文件（仅 `<div id="root">` + script）。缺少 viewport 时移动浏览器按约 980px 桌面宽度布局再整体缩放，导致全部 `@media (max-width: 768px)` 与 `window.matchMedia('(max-width: 768px)')` 移动分支不命中 —— 这解释了此前移动适配"看起来没实现"，实际 CSS 是对的但从未被匹配。修复后前一交付条目的移动框架改动方可真正生效。
- Changed files: `frontend/workbench/index.html`、`frontend/workbench/src/entry-html.guard.test.ts`（新增）、`handoff/workbench-mobile-adapt.md`。
- Verification evidence: 新增 `entry-html.guard.test.ts` 2/2 通过；`npm run build` 通过，构建产物 `dist/index.html` 由 0.17 kB 增至 0.59 kB，实际内容已确认包含 `<meta name="viewport" content="width=device-width, initial-scale=1.0" />`；全套件 451 通过，失败仍为既有的同 3 文件 6 用例（announcement/media-students/subordinate-lead 业务守卫，与本任务无关）。真实移动浏览器渲染仍未实测。
- Dependency or integration impact: 无新增依赖；不改后端、SQL、菜单、权限、路由或公开 URL。修复会改变移动端实际渲染宽度，此前依赖"桌面宽度再缩放"观感的截图基线将失效（这是预期修复结果）。
- Remaining work: 在真实手机或设备模拟中复验 —— 侧栏隐藏、抽屉为唯一导航、Header 精简、无整页横向滚动、单主滚动区；并复验 5 种布局模式在移动端的呈现。
