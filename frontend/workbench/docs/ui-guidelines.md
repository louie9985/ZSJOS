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

媒体学员概览（2026-09-18 用户确认）使用 12 栏 9:3 主侧布局。顶部身份、服务与标签通栏、学员服务级工具栏保持结构；右侧统一承载服务状态、兼职账号状态和定位卡状态及当前版本操作，下方为学员档案与现有学员信息表资料。右侧自然滚动，不吸顶。定位访谈阶段、预约、记录数和材料独立标明，不与定位卡审核状态混同。
左侧定位卡阅读采用两列短字段网格，实际长内容（超过 120 字符）、附件、参考资料通栏；不根据 textarea 类型单独判定通栏。字段名和正文采用 14px 对应的现有 lg token，字段名加粗；模板组标题加大。正文全部展开，填写提示保留，referenceFor 参考关系、附件、凭证及历史标签快照完整保留。历史版本原位展开且只读，右侧操作只作用于当前版本。编辑弹窗仍为原四列对照。详情内容不足以并排时在移动断点转为右侧信息优先的单列。
列表选中态取消凹陷阴影，使用主色浅底、边界与侧边标记；使用现有主题 token，正文 base、标签 sm，主要容器细边界和轻外阴影。吸顶定位卡标题以实色兜底，磨砂开启时使用高遮盖度底色。详见 [字段迁移与验收](student-overview-grid.md)。
 经后续确认，媒体学员左栏支持按钮切换完整列表（320px）和头像栏（72px），首次展开并在同一浏览器记住选择；≤768px 收起为顶部横向头像条。收起态展开与搜索按钮同排显示为无边框轻量图标，已筛选时搜索按钮高亮；展开态收起按钮置于搜索框左侧并保持同一行。搜索图标展开并聚焦原搜索框，保留筛选；收起态提供选中标记、姓名与编号提示及紧凑分页。布局切换保持列表、详情实例及编辑内容，不触发业务请求。加载、空结果及错误重试均须适配窄栏。
学员名下的每个真实媒体账号以账号昵称作为与“概览”同级的标签页；空昵称显示“未命名账号 + 账号编号”；重名使用平台与账号编号消歧。账号标签内承载原有账号维护、定位卡、内容历史和拍剪操作，新增账号入口位于概览的统一操作栏。
定位访谈弹窗桌面端每行横向展示“字段／访谈注意／访谈确认”三等列。移动端恢复纵向排列，依次展示字段名称、访谈注意、确认选项和备注，不横向滚动。

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

`/zsjos/sales-orders` 沿用标准主从页面骨架：使用 `.workspace-page` 的
`--crm-page-pad`，列表与详情区使用统一的 `--crm-list-pane-w`、`--crm-gap` 和
`--crm-pane-pad`。订单状态标签与刷新操作共用顶部一行；订单字段和业务操作保持订单域
自己的数据与权限契约，不因视觉对齐而复制客资页面的数据模型。

订单详情使用 12 列的 9:3 主侧结构：主区是高密度、无外层卡片嵌套的信息画布，右侧栏
集中显示当前状态、可执行审批动作和纵向会签轨迹。报名履约中心和财务结算中心是轨迹
主节点，销售主管确认作为对应节点内的嵌套会签信息；窄屏时审批侧栏排在业务信息之前。
客户档案、订单概览、学员资料、成交付款和服务信息按业务主题合并；重复性课程明细继续
使用表格，缴费凭证使用附件预览区。当前订单接口只提供节点级审批快照；若要展示完整
多人会签人员和 BPM 历史，必须扩展后端公开契约，前端不得自行猜测审批人列表。

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

HRM 表格统一使用 `src/components/HrmProTable.tsx`。管理主列表传入 `advanced`、稳定的
`persistenceKey` 和 `onReload`，获得当前页搜索、刷新、列设置、密度和全屏能力；详情、
编辑器和静态子表不传 `advanced`，避免在弹窗或抽屉中重复堆叠工具栏。服务端分页、
导出、行选择和业务操作仍沿用页面既有 API 与权限边界。

新增或修改时间列统一使用 `src/components/DateTimeText.tsx`：列表默认显示
`YYYY-MM-DD HH:mm`，悬停展示精确到秒的完整值；日期字段显式使用 `date` 精度，审计或
流程详情需要直接展示秒时显式使用 `second` 精度。时间列通常设为 `160px` 并保持单行，
列较多时通过表格横向滚动兜底，不缩小字体、不拆行挤压相邻列。底层格式化统一复用
`src/services/time.ts`，不要在页面中再使用 `toLocaleString` 拼接用户可见时间。

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

### 账号运营档案布局例外（已确认）

账号标签以用户截图为准：独立主页图在左侧，右侧并排三个一级栏目「账号定位卡」「账号状态」「账号复盘记录」。账号定位卡包含学员定位及历史定位/采访记录；账号状态内部左右两组容纳账号资料、系统状态和经营指标；追加记录时间线位于账号复盘记录列内。桌面主从容器不得将三列降为两列。736px/360px 顺序折为单列，不产生横向溢出。

维护表为近全屏 Modal，左侧主页区和三项同名导航，右侧两列字段；缺失导航按相同展示归属统计并定位。服务端配置中的 PROFILE/METRICS 在此投影到账号状态，positioning_history 展示在定位卡，cover 留在主页区。此投影不改配置值、字段责任或历史快照。

账号页只保留截图内容，移除改版前维护历史及独立定位卡、内容生产、拍剪区块和操作入口。新档案追加记录保留；后端历史数据及其他业务页面不因展示收口而删除。责任使用红/蓝/黄标签、文字和左色条，自动及非本人字段只读，全部颜色引用 crm token；主页图默认由运营维护，责任标签和维护入口读取服务端配置与 editableFields；历史未配置时仍显示待确认。

2026-09-14 用户确认的补充：账号资料区下方恢复“内容发布历史”只读作品网格，限定当前账号已发布作品，按可用宽度自动排列。原内容生产编辑与拍剪区块不恢复。账号操作按钮集中在右侧并向左扩展，换行保持右对齐；交付确认、申请延期分别通过按钮打开独立 Modal，表单不铺在账号首页。

### 账号诊断表单

启动诊断与周期诊断使用 1080px 宽屏弹窗，最大宽度和高度为视口减 32px，内容自适应高度，正文内部滚动，提交区保持可见。周期表单顶部以“本次填写”文字展示对应类型，截止时间显示日期、时分及北京时间；不展示禁用的模板选择器和轮次输入框，模板与周期由任务自动关联，修订保留原值。基础信息三列；配合等级与证据两列；主要/次要瓶颈各自与证据组成一列；结论整行，改进措施与重点观测数据两列。768px 及以下统一单列，保持选择项紧邻对应证据，间距沿用设计变量。次要瓶颈启动时选填，选后证据必填。工作台提醒从截止前 24 小时开始，展示对应表单及截止时间，已完成不提醒。

定位卡各版本的学员确认与运营复核意见置于正文上方，全文可见并标注版本与时间。资源链接复用 ResourceLink，HTTP(S) 外链带新标签标识和复制，站内链接走路由；不抓取外部标题。图片附件有可点击缩略图、等比例大图弹窗、原文件下载及失败重试。


学员概览定位卡阅读态不展示填写提示，普通字段采用“名称：内容”同行，字段名与正文使用 lg 字号。平台主页搭建桌面四列；阶段网格 S0 全宽，S1–S6 两行三列。窄容器降列但 S0 始终全宽。素材库引用使用独立封面卡及“预览参考内容”按钮，保持所选爆款账号/爆款内容版本与 referenceFor 归属，不转换为普通文件卡。确认凭证在对应版本反馈区内有独立标题。编辑态保持原四列与模板提示。


定位卡视觉密度：正文、普通字段名、平台/阶段/反馈标题统一使用 13px base token；普通字段常规字重，区块标题最多 500 字重。参考素材说明用次要文字色。分组和平台以低饱和主色浅底、阶段以成功色浅底区分，颜色仅为分区装饰、不表示流程状态。图片附件采用 72×64 等比例缩略图与文件名同行，下方保留统一预览/下载操作，不再叠加大图和重复图片文件图标。素材库封面卡不受此普通附件调整影响。

账号页采用外层 12 栏：左侧 9 栏再按 4:8 比例放置账号定位卡与两列账号状态，右侧 3 栏为 sticky 账号摘要、主页截图、操作和反馈。左侧下方复盘记录占 9 栏，诊断、承担事项、S0—S6 交付确认和内部交付目标均保留为独立摘要卡；内容发布历史位于复盘记录之后并占满 12 栏。资料完整时使用紧凑成功标签，不渲染大块成功 Alert；Alert 仅用于待补、错误、权限或加载异常。

账号阅读态的历史定位与采访记录独立放在发布历史之后，全宽展示历史卡片；编辑弹窗原历史入口保留。账号定位卡与账号状态标题居中，字段名使用次要色，取消阅读行连续责任色条。主页 URL 复用 ResourceLink；当前发布节奏、账号定位、专业定位、当前期段、账号状态、当前瓶颈和内容主要形式使用浅蓝标签，使用原 displayValue 快照；多选字段按接口的顿号连接规则逐项显示标签，单选保持完整，不重新查字典。按钮统一圆角、字号和触控高度，侧栏操作通栏排列。

账号阅读页右侧 3 栏增加独立的定位卡状态与操作模块，集中展示当前应用提交版本、新版可用提示和更换/选择应用定位卡按钮。定位卡正文不重复状态或操作；沿用同一组件的数据与应用确认逻辑，通过 portal 放置侧栏内容，不重复请求。编辑弹窗维持原位入口。

账号阅读页顶部不再重复展示「自动生成 / 编导填写 / 运营填写」责任图例；字段内及维护弹窗的责任说明沿用原规则。

账号阅读页移除顶部重复的账号昵称、平台及状态摘要，卡片网格顶部使用 --crm-sp-5 留白；账号维护和诊断入口保留在右侧操作区。
