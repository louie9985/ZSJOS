---
description: "创建新的 workbench 页面骨架（含路由、组件、CSS、guard 注册）。当用户说"新建页面"/"加一个XX页"/"创建XX模块"时触发。"
---

# 新页面脚手架

## 前置

1. 读 `frontend/workbench/docs/ui-guidelines.md`，确定页面形态（主从页 / 列表表格页 / 表单详情页 / 仪表盘概览页）
2. 与用户确认页面形态和功能名称（feature slug，如 `customer-complaint`）

## 创建步骤

按顺序执行：

### 1. 路由常量

在 `frontend/workbench/src/constants.ts` 的 `APP_ROUTES` 对象里加入新路由路径，并加到 `RENDERABLE_APP_ROUTES` 数组。

### 2. 页面组件

创建 `frontend/workbench/src/pages/<Feature>Page.tsx`：
- 返回 `<section className="workspace-page <feature>-page">`
- 按 `docs/ui-guidelines.md` 选择对应形态的 DOM 骨架
- 远程数据视图必须实现 4 态：loading (`<Skeleton active/>`)、error (`<Alert type="error" action={<Button>重试</Button>}/>`)、empty (`<Empty/>`)、success

### 3. 路由注册

在 `frontend/workbench/src/layouts/RouteHost.tsx` 加路由分支：
```tsx
if (menu?.path === APP_ROUTES.YOUR_ROUTE) return <YourPage />
```

### 4. 样式文件

创建 `frontend/workbench/src/styles/pages/<feature>.css`：
- 从 `docs/ui-guidelines.md` 的对应骨架模板复制 CSS 起手式
- 只使用 `var(--crm-*)` 变量，零硬编码颜色
- 页面根 padding: `var(--crm-page-pad)`
- 主从页须覆盖 `max-width: none; height: 100%`
- detail-pane padding: `var(--crm-pane-pad)`

### 5. 样式注册

在 `frontend/workbench/src/styles/index.css` 的 pages 块（`tokens → base → layout → patterns → [pages] → components → antd-overrides`）加 `@import`。

### 6. Guard 注册（如需）

若页面有 detail-pane 或主从布局 grid：
- 在 `frontend/workbench/src/styles/styles.guard.test.ts` 的**页面 padding 锚点列表**里加 `.<feature>-page`
- 在 **detail-pane padding 锚点列表**里加 `.<feature>-detail-pane`
- 在 **grid-template-columns 校验列表**里加 `.<feature>-inbox-layout`

### 7. Antd 圆角（如需）

若新增了自定义卡片/面板类名，在 `frontend/workbench/src/styles/antd-overrides.css` 的统一圆角 `:where(...)` 选择器列表里追加。

## 验证

从 `frontend/workbench/` 执行：

```bash
npm run typecheck
npm run build
npm test
```

三项全通过后，在浏览器打开页面检查 desktop (≥1024px) 和 mobile (375px) 宽度的布局。
