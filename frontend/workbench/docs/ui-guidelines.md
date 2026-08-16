# UI 视觉规范

本文档是 workbench 页面视觉一致性的唯一参考来源。新建页面、组件或修改共享样式时 **MUST** 先读此文。

机器强制的规则不在此重复 — 参见 `src/styles/styles.guard.test.ts`（禁止硬编码颜色、限定字号列表、强制 page/pane padding 使用 token 等）。

---

## 1. Token 体系

定义于 `src/styles/tokens.css`。所有自有 CSS 只引用 `var(--crm-*)` 变量。

### 1.1 表面色阶（5 级深→浅）

```
layout → sunken → chrome → container → elevated
```

| 级别 | 变量 | 用途 |
|---|---|---|
| layout | `--crm-bg-layout` | 最底层背景（内容区外） |
| sunken | `--crm-bg-sunken` | 凹陷子区域：字段行、代码块、统计区 |
| chrome | `--crm-bg-chrome` | 侧边栏、header |
| container | `--crm-bg-container` | 卡片/面板主体 |
| elevated | `--crm-bg-elevated` | 浮层/弹窗 |

### 1.2 阴影 elevation

| Level | 变量 | 场景 |
|---|---|---|
| 0 | — | sunken 区域无外阴影 |
| 1 | `--crm-shadow` | 静态面板 |
| 2 | `--crm-shadow-card` | 可交互卡片（默认态） |
| 3 | `--crm-shadow-raised` | hover 抬起 |
| 4 | `--crm-shadow-float` | 浮层 / Modal |
| inset | `--crm-shadow-inset` | 凹陷区域内阴影 |

### 1.3 间距

| 变量 | 默认值 | 用途 |
|---|---|---|
| `--crm-page-pad` | 12px | 页面根元素 padding |
| `--crm-card-pad` | 14px | 卡片 padding |
| `--crm-pane-pad` | 16px | 主从页 detail-pane padding |
| `--crm-gap` | 10px | 主从列间距 / 卡片网格 gap |
| `--crm-sp-1` ~ `--crm-sp-6` | 4/6/8/12/16/24 | 通用间距阶梯 |

上述值随 `data-crm-density` 属性切换（loose / default / compact）。

### 1.4 圆角

`--crm-radius-sm` / `-md` / `-lg` (6/8/10)。随 `data-crm-radius` 切换。

### 1.5 字号

`--crm-font-sm` / `-base` / `-lg` / `-display` (12/14/15/22)。  
Guard 允许的 px 字面量白名单：`10, 11, 12, 13, 16, 18, 30`。

---

## 2. 配方

### 2.1 标准卡片 `.lead-card`

```css
.your-card {
  min-width: 0;
  padding: var(--crm-card-pad);
  border: 1px solid var(--crm-border);
  border-radius: var(--crm-radius-md);
  background: var(--crm-bg-container);
  box-shadow: var(--crm-shadow-card);
  transition: box-shadow 0.25s ease, transform 0.25s ease;
}
.your-card:hover {
  box-shadow: var(--crm-shadow-raised);
  transform: translateY(-1px);
}
```

### 2.2 凹陷子块（sunken）

用于卡片内的字段行、统计区等内嵌区域：

```css
.your-sunken-block {
  padding: 4px 8px;
  border-radius: var(--crm-radius-sm);
  background: var(--crm-bg-sunken);
  box-shadow: var(--crm-shadow-inset);
  border: none;
}
```

### 2.3 玻璃效果（glass hero/toolbar）

仅在 `data-crm-glass="frosted"` 时生效。必须使用 `--crm-glass-*` 变量：

```css
.your-glass-panel {
  background: color-mix(in srgb, var(--crm-bg-container) 65%, transparent);
  backdrop-filter: blur(var(--crm-glass-blur-strong));
  box-shadow: var(--crm-glass-edge), var(--crm-shadow-card);
}
```

注意 `--crm-glass-edge` 的 none 态是 `inset 0 0 0 0 transparent`（不是 `none`），可安全拼入 `box-shadow` 列表。

### 2.4 详情区 12 列网格（overview grid）

所有页面的详情区/概览区内容布局统一使用 12 列网格体系。至少遵守最外层的 **9:3** 主侧分栏比例；二级嵌套可按内容量选择是否细分。

**最外层（必须）：**

```css
.<feature>-overview-grid {
  container-type: inline-size;
  display: grid;
  grid-template-columns: repeat(12, minmax(0, 1fr));
  gap: var(--crm-gap);
}

.<feature>-overview-main { grid-column: span 9; min-width: 0; }
.<feature>-overview-aside { grid-column: span 3; min-width: 0; }

@container (max-width: 699px) {
  .<feature>-overview-main,
  .<feature>-overview-aside { grid-column: 1 / -1; }
}
```

**二级嵌套（可选，按信息密度选择）：**

| 场景 | 列定义 | 比例 |
|------|--------|------|
| 主区内左右分栏 | `8fr 4fr` | 66.7% / 33.3% |
| 卡片等分行 | `repeat(2, minmax(0, 1fr))` | 50% / 50% |
| 字段 2 列 | `repeat(2, minmax(0, 1fr))` | 50% / 50% |
| 字段 3 列 | `repeat(3, minmax(0, 1fr))` | 三等分 |

**参考实现：** `src/components/LeadDetailOverview.tsx` + `src/styles/components/lead-detail-v2.css`

---

## 3. 页面骨架

所有页面根元素：`<section className="workspace-page <feature>-page">`

### 3.1 主从页（inbox 式：左列表右详情）

代表页面：leads/owned、message-inbox、sales-order-inbox、work-plan。

```
<section class="workspace-page <feature>-page">
  <header class="<feature>-filter-shell">            ← 可选：tabs + 筛选行
  <div class="<feature>-inbox-layout">               ← CSS grid
    <aside class="<feature>-list-pane">
      <div class="<feature>-toolbar">
      <div class="<feature>-scroll">                 ← overflow-y:auto; flex:1
        <button class="<feature>-item [active] [unseen]">
        <div class="<feature>-list-sentinel"/>       ← IntersectionObserver
    <main class="<feature>-detail-pane">
```

**CSS 要点：**

```css
.<feature>-page {
  max-width: none;
  height: 100%;
  display: flex;
  min-height: 0;
  flex-direction: column;
  padding: var(--crm-page-pad);           /* guard enforced */
}

.<feature>-inbox-layout {
  display: grid;
  flex: 1;
  min-height: 0;
  grid-template-columns: var(--crm-list-pane-w) minmax(0, 1fr);
}

.<feature>-detail-pane {
  padding: var(--crm-pane-pad);           /* guard enforced */
  overflow-y: auto;
}
```

**别忘：** 新主从页须在 `styles.guard.test.ts` 的锚点列表里注册 `.<feature>-page` 和 `.<feature>-detail-pane`。

### 3.2 列表/表格页

代表页面：lead-assignment、claim-pool、aging-pool、subordinate-sales。

```
<section class="workspace-page <feature>-page">
  <div class="page-heading">              ← patterns.css 提供，注意不是 workspace-page-heading
    <Typography.Title level={4}/>
    <Space>…actions…</Space>
  </div>
  <div class="<feature>-table-area">      ← 包裹 ProTable 或自定义列表
```

**CSS 要点：**

```css
.<feature>-page {
  padding: var(--crm-page-pad);
  /* 通常不覆盖 max-width，使用 .workspace-page 的 1440px */
}
```

如需限宽：`max-width: var(--crm-page-max-table)` (1360px) 或 `var(--crm-page-max-narrow)` (1200px)。

### 3.3 表单/详情页

代表页面：lead-detail overview（12 列网格）、user-profile。

```
<section class="workspace-page <feature>-page">
  <div class="<feature>-detail-grid">           ← container-type: inline-size
    <div class="<feature>-main">                ← grid-column: span 9
      <section class="lead-card">…</section>
      <section class="lead-card">…</section>
    <aside class="<feature>-aside">             ← grid-column: span 3
```

**CSS 要点：**

```css
.<feature>-detail-grid {
  container-type: inline-size;
  display: grid;
  grid-template-columns: repeat(12, minmax(0, 1fr));
  gap: var(--crm-gap);
}

.<feature>-main { grid-column: span 9; min-width: 0; }
.<feature>-aside { grid-column: span 3; min-width: 0; }

/* 窄屏折叠 — 容器查询优先，媒体查询兜底 */
@container (max-width: 699px) {
  .<feature>-main,
  .<feature>-aside { grid-column: 1 / -1; }
}
```

每个网格子项加 `min-width: 0`（文本截断所需）。

### 3.4 仪表盘/概览页

代表页面：today-tasks (首页)。

```
<section class="workspace-page <feature>-page">
  <div class="<feature>-stats-grid">           ← 指标卡行
    <section class="lead-card <feature>-stat-card">…
  <div class="<feature>-charts-grid">          ← 图表区
    <section class="lead-card">…<Chart/>…
  <div class="<feature>-shortcuts">            ← 快捷入口
```

**CSS 要点：**

```css
.<feature>-page {
  padding: var(--crm-page-pad);
  max-width: var(--crm-page-max-narrow);
}

.<feature>-stats-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: var(--crm-gap);
}
```

图表配色：使用 `--crm-color-primary`、`--crm-color-success`、`--crm-color-warning`、`--crm-color-error` 及其 `-bg` / `-border` 衍生作为分类色。序列色用 `color-mix` 从 primary 派生透明度梯度。

---

## 4. 命名约定

| 模式 | 格式 | 示例 |
|---|---|---|
| 页面根 | `.workspace-page .<feature>-page` | `.claim-pool-page` |
| 主从布局容器 | `.<feature>-inbox-layout` | `.message-inbox-layout` |
| 列表面板 | `.<feature>-list-pane` | `.sales-order-list-pane` |
| 详情面板 | `.<feature>-detail-pane` | `.lead-inbox-detail-pane` |
| 列表项 | `.<feature>-item` | `.lead-inbox-item` |
| 状态修饰类 | 裸类名 | `.active` `.unseen` `.done` `.current` `.future` `.overdue` |
| 卡片 | `.<feature>-card` 或复用 `.lead-card` | `.claim-pool-card` |
| 色调修饰 | `.tone-<name>` / `.color-<name>` | `.tone-warning` `.color-green` |

kebab-case。不加 `__` / `--` (非 BEM 双下划线)。

---

## 5. 新页面接线清单

1. **路由常量** — `src/constants.ts` → `APP_ROUTES` + `RENDERABLE_APP_ROUTES`
2. **页面组件** — `src/pages/<Feature>Page.tsx`，返回 `<section className="workspace-page <feature>-page">`
3. **路由注册** — `src/layouts/RouteHost.tsx` 加 `if (menu?.path === APP_ROUTES.X) return <YourPage/>`
4. **样式文件** — `src/styles/pages/<feature>.css`
5. **样式注册** — `src/styles/index.css` 的 pages 块加 `@import './pages/<feature>.css'`
6. **antd 圆角** — 如有自定义卡片/面板类，在 `antd-overrides.css` 第 33 行的 `:where(...)` 列表里加
7. **guard 注册** — 如有 detail-pane 或主从布局，在 `styles.guard.test.ts` 的锚点列表里加选择器

---

## 6. 已知坑

- `.workspace-page-heading` 不存在 → 用 `.page-heading`（定义在 `patterns.css`）
- `.workspace-page` 默认 `max-width: 1440px`，主从页须覆盖为 `max-width: none; height: 100%`
- `[data-crm-preset="illustration"]` 预设强制 3px 边框 + 硬阴影 → 新视觉块需要尾部兼容覆盖
- `--crm-shadow-float` 运行时与 `--crm-shadow-card` 值相同 → 浮层独立性靠 `z-index` 而非阴影区分
- 在 glass 路径中，`--crm-glass-edge` 的零值是 `inset 0 0 0 0 transparent`，不能写 `none`
- 移动端(≤768px) `compact` 密度强制回落到 default 触控尺寸
