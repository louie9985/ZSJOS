# UI 视觉规范

本文档是 workbench 页面视觉一致性的唯一参考来源。新建页面、组件或修改共享样式时 **MUST** 先读此文。

机器强制的规则不在此重复 — 参见 `src/styles/styles.guard.test.ts`（禁止硬编码颜色、限定字号列表、强制 page/pane padding 使用 token 等）。

---

## 1. Token 体系

定义于 `src/styles/tokens.css`。所有自有 CSS 只引用 `var(--crm-*)` 变量。

### 1.1 表面层级

```
layout → chrome → container → elevated
                   ↳ 子块与 container 同色（兼容名 sunken）
```

| 级别 | 变量 | 用途 |
|---|---|---|
| layout | `--crm-bg-layout` | 最底层背景（内容区外） |
| sunken | `--crm-bg-sunken` | 兼容旧命名的子区域底色；使用平面或外阴影，不制造凹陷 |
| chrome | `--crm-bg-chrome` | 侧边栏、header |
| container | `--crm-bg-container` | 卡片/面板主体 |
| elevated | `--crm-bg-elevated` | 浮层/弹窗 |

### 1.2 阴影 elevation

| Level | 变量 | 场景 |
|---|---|---|
| 0 | — | 平面子块，不加阴影 |
| 1 | `--crm-shadow` | 静态面板 |
| 2 | `--crm-shadow-card` | 可交互卡片（默认态） |
| 3 | `--crm-shadow-raised` | hover 抬起 |
| 4 | `--crm-shadow-float` | 浮层 / Modal |
| inset | `--crm-shadow-inset` | 兼容旧命名，运行时映射为标准外阴影；禁止向内暗阴影 |

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

### 2.2 凸起子块（raised）

用于卡片内的字段行、统计区等内嵌区域。默认使用轻微外阴影；信息密度高或层级不需要强调时可以去掉阴影，但不得使用向内暗阴影：

```css
.your-raised-block {
  padding: 4px 8px;
  border-radius: var(--crm-radius-sm);
  background: var(--crm-bg-container);
  box-shadow: var(--crm-shadow);
  border: 1px solid var(--crm-border);
}
```

`--crm-bg-sunken` 和 `--crm-shadow-inset` 仅为兼容历史类名保留。新代码应使用 `raised`、`container` 或 `box-shadow: none` 表达层级，禁止 `inset` 暗阴影、明显变暗的子块底色和“洞中洞”效果。

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
列表选中态取消凹陷阴影，使用主色浅底、边界与侧边标记；字段行、统计区和预览块统一使用凸起或无阴影的平面效果，禁止凹陷显示；使用现有主题 token，正文 base、标签 sm，主要容器细边界和轻外阴影。吸顶定位卡标题以实色兜底，磨砂开启时使用高遮盖度底色。详见 [字段迁移与验收](student-overview-grid.md)。
 经后续确认，媒体学员左栏支持按钮切换完整列表（320px）和头像栏（72px），首次展开并在同一浏览器记住选择；≤768px 收起为顶部横向头像条。收起态展开与搜索按钮同排显示为无边框轻量图标，已筛选时搜索按钮高亮；展开态收起按钮置于搜索框左侧并保持同一行。搜索图标展开并聚焦原搜索框，保留筛选；收起态提供选中标记、姓名与编号提示。媒体学员左栏采用滚动接近底部自动追加，展开列表与收起头像栏均不显示分页器；追加失败保留列表并提供重试，追加不重载当前详情。卡片参考客资管理样式，仅展示姓名、学员编号、手机号和微信号，不展示课程、服务数量或状态。布局切换保持列表、详情实例及编辑内容，不触发业务请求。加载、空结果及错误重试均须适配窄栏。
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
  height: 100%;
  display: flex;
  min-height: 0;
  flex-direction: column;
  /* 页面 padding 与不限宽行为继承 .workspace-page。 */
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
  <div class="<feature>-table-area">      ← 使用 BusinessTable 或非表格列表
```

**CSS 要点：**

```css
.<feature>-page {
  padding: var(--crm-page-pad);
  /* 通常不覆盖 max-width，使用 .workspace-page 的 1440px */
}
```

如需限宽：`max-width: var(--crm-page-max-table)` (1360px) 或 `var(--crm-page-max-narrow)` (1200px)。

工作台所有业务表格统一使用 `src/components/BusinessTable`，以客资管理表格为设计基准。
管理列表和主从页的表格模式使用完整模式，弹窗选择器、详情明细及编辑表格使用
`mode="compact"`。列宽拖拽、列设置、密度、全屏、工具栏布局及分页样式由组件负责，
页面通过 `filters`、`actions`、`batchActions` 插槽接入业务控件。使用唯一、稳定的
`tableKey` 隔离展示偏好；数据、权限、排序字段映射及详情交互仍由页面负责。
禁止业务页面直接导入 Table/ProTable 或复制表格外观 CSS。后台 Vue 和内嵌后台页面
本次未迁移。接口、迁移兼容与验证说明见 [通用业务表格](business-table.md)。

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
- `.workspace-page` 已提供统一页面间距与不限宽行为，主从页仅按容器需要设置 `height: 100%`，不要重复声明相同 padding/max-width
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

定位卡公共填写组件 `PositioningCardFields` 保持「定位卡项目 / 填写提示 / 计划交付内容确定 / 参考账号与爆款」四列，每个字段均占完整一行，按服务端 `sort` 排序；`group` 不改变字段顺序或增减单元格，`referenceFor` 素材仍属于对应字段。首次填写、草稿重开、驳回修改、修订和使用该组件的快照详情共用此规则。桌面表头与各行列边界一致，窄屏按字段纵向排列并展示提示/填写/参考标签；布局验收必须检查第二行及后续行、不同分组和保存重开，不能只检查表头或第一行。

账号页采用外层 12 栏：左侧 9 栏再按 4:8 比例放置账号定位卡与两列账号状态，右侧 3 栏为 sticky 账号摘要、主页截图、操作和反馈。左侧下方复盘记录占 9 栏，诊断、承担事项、S0—S6 交付确认和内部交付目标均保留为独立摘要卡；内容发布历史位于复盘记录之后并占满 12 栏。资料完整时使用紧凑成功标签，不渲染大块成功 Alert；Alert 仅用于待补、错误、权限或加载异常。

账号阅读态的历史定位与采访记录独立放在发布历史之后，全宽展示历史卡片；编辑弹窗原历史入口保留。账号定位卡与账号状态标题居中，字段名使用次要色，取消阅读行连续责任色条。主页 URL 复用 ResourceLink；当前发布节奏、账号定位、专业定位、当前期段、账号状态、当前瓶颈和内容主要形式使用浅蓝标签，使用原 displayValue 快照；多选字段按接口的顿号连接规则逐项显示标签，单选保持完整，不重新查字典。按钮统一圆角、字号和触控高度，侧栏操作通栏排列。

账号阅读页右侧 3 栏增加独立的定位卡状态与操作模块，集中展示当前应用提交版本、新版可用提示和更换/选择应用定位卡按钮。定位卡正文不重复状态或操作；沿用同一组件的数据与应用确认逻辑，通过 portal 放置侧栏内容，不重复请求。编辑弹窗维持原位入口。

账号阅读页顶部不再重复展示「自动生成 / 编导填写 / 运营填写」责任图例；字段内及维护弹窗的责任说明沿用原规则。

账号阅读页移除顶部重复的账号昵称、平台及状态摘要，卡片网格顶部使用 --crm-sp-5 留白；账号维护和诊断入口保留在右侧操作区。

### 链接填写

内容制作、批量审核与发布登记的链接字段统一使用 `ResourceLinkInput`；账号资料依据服务端 `url` 类型接入，素材动态表单（含重复组）依据 `https-link` 类型接入。组件保留输入原文、表单事件、字段 ID、长度限制及禁用状态，允许清空；有效地址复用 `ResourceLink` 提供打开与复制预览，不请求外站元数据。必填、HTTPS 等业务校验仍由原表单和服务端负责，预览不可用不等同于表单校验失败。上传、自动生成只读链接和普通文本不转为链接输入。

### 爆款拆解页滚动

爆款账号与爆款内容独立拆解页在桌面宽度保留定高四列、列头固定和列体独立滚动。草稿工具栏不收缩，加载组件的包装层必须传递剩余高度。1180px 及以下改为纵向内容流，页面根容器同步取消定高和溢出裁剪，由外层页面滚动显示全部内容。


### 内容审核收件箱与内部账号导航（2026-09-22）

内容审核顶部保留标题操作行和状态分类；搜索框、筛选按钮位于收件箱顶部，搜索区与列表内容统一使用 crm-pane-pad。展开筛选后显示生效数量和重置；选项读取权威 API/字典。卡片展示学员/账号名称、账号对应责任人、提交时间、高亮作品数量，不显示批次号或内部 ID。

详情身份、状态、提交信息和批次操作分行；批次编号作为次要信息。作品单列，宽屏右侧流程栏 288px；详情宽度不足 900px 时流程前置，节点可折叠、审批动作保持可见。手机列表与详情切换并提供返回收件箱。正文保留原 180px 内部滚动阅读结构。

内部标签仍按菜单路径唯一标识，另存完整 href（含 query/hash），激活和关闭相邻页回退均恢复完整地址。内容审核与媒体学员两页按已打开标签受控保留，隐藏页固定自身路由上下文，其他页面不统一缓存。关闭标签、退出会话、租户/用户/权限变更时清理相关页面实例。账号定位链接复用一个媒体学员标签，未保存账号编辑或保存进行中不得被另一账号定位覆盖；切换前使用页面注册的离开确认。媒体学员名单选择与账号页签切换复用这一检查。

独立验收入口 `test/content-review-integration.html` 使用正式页面组件与隔离只读传输夹具，不挂入生产菜单、不发真实业务写请求。旧 layout 入口保留为设计原型，以 integration 入口检验最终组件。

### 拍剪工单收件箱与抢单池（2026-09-22）

拍剪页不展示重复标题/技术副标题，一级导航为待接单、我的工单、抢单池并右置刷新。我的工单默认待处理，二级状态标签为待处理、制作中、待核对、已完成、全部；收件箱上方搜索工单编号并提供具体状态、截止日期范围与清空。列表使用统一宽度 token、平面选中浅底及左侧标记。

抢单池使用销售抢单池风格的全宽响应式卡片网格，显示需求摘要、关联对象、提交人及截止信息；点击进入完整详情，操作位于右侧，返回保留筛选/分页/滚动位置。详情采用 12 列 9:3 Grid，左侧制作需求、全部关联账号、成品交付；短字段两列，长文/链接/附件通栏。右侧依次为交付截止、当前状态与操作、业务阶段及真实流转记录。截止使用普通卡片、大字号倒计时，24 小时内 warning、逾期 error；完成/取消停止倒计时，无截止明确标注；不使用 Alert 提醒框。配色/间距/圆角沿用 crm token。

详情容器低于 700px 时操作侧栏优先单列，768px 以下列表与详情分别展示并提供返回入口。原因与成品提交使用独立弹窗；历史缺失明确标注，不展示推算事件或内部配置说明。
