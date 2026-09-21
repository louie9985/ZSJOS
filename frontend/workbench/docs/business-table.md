# 工作台通用业务表格

工作台所有业务表格使用 `src/components/BusinessTable`，以客资管理的 ProTable 表格模式为设计基准。
本次范围仅 React 员工工作台；Vue 管理后台与 `admin_embed` 页面后续独立迁移。
不新增依赖或跨框架共享包，不改变后台接口、菜单、权限及字典契约。

## 页面接入

```tsx
import BusinessTable from '../components/BusinessTable'

<BusinessTable<Order>
  tableKey="order-management"
  rowKey="id"
  columns={columns}
  dataSource={items}
  loading={loading}
  error={error}
  unauthorized={unauthorized}
  onReload={reload}
  filters={<AdvancedFilterToolbar {...filterProps} />}
  actions={exportButton}
  pagination={{ current, pageSize, total, showSizeChanger: true, onChange: changePage }}
/>
```

- `tableKey` 必须是稳定、唯一的表格标识，不使用用户姓名、列表下标或随机数。它隔离列宽与列设置存储；不代表访问权限。
- 完整模式统一提供密度、全屏、列设置。传入 `onReload` 才显示刷新入口，不模拟业务请求。
- 工具栏顺序为 `batchActions`（含已选数量）、`filters`、`actions`、内置设置；根据可用宽度换行。
- 业务页继续负责请求、筛选条件、排序字段白名单、行选择、跨页选择清理、批量限制、权限和详情入口。
- 服务端分页使用受控 `current/pageSize/total/onChange`。只有接口和回调确实处理每页数量时才显式设置 `showSizeChanger: true`；固定条数分页默认不显示切换器。切换每页数量时页面应回到第一页。
- `error` 展示失败与重试，不混同空数据；`unauthorized` 阻止展示可能残留的数据，不提供无效重试。页面依据既有错误类型判断权限，组件不从角色名或错误文案猜测权限。
- `pagination={false}` 用于完整静态明细；错误与空数据不能替代后端真实数据。

## 紧凑模式与兼容列

```tsx
<BusinessTable<OrderItem>
  tableKey="order-detail-items"
  mode="compact"
  rowKey="id"
  columns={itemColumns}
  dataSource={items}
  pagination={false}
/>
```

弹窗选择表、详情明细及编辑表使用紧凑模式：相同单元格设计，默认不显示完整管理工具栏。
需要筛选时仍可传 `filters`。编辑表通过列的 `render` 放置控件，状态、校验、保存由业务页维护；
含输入框或多行内容的列明确设 `ellipsis: false`。定位访谈已使用此方式。

旧 Ant Design `TableColumnsType<T>` 可传入 `columnMode="native"`，组件会把原始字段值传给
`render(value, record, index)`，保留布尔值、数值零、缺失值、嵌套字段、分组列的语义。
新页面优先采用 `ProColumns<T>`；此兼容模式仍使用同一个 ProTable 实现和样式，不是第二套表格。

## 列与主题

- 列使用稳定 `key` 或 `dataIndex`；渲染列应显式定义 `key`，避免列顺序修改影响偏好。
- 普通列默认单行省略，默认宽度 160px、拖拽最小宽度 80px；页面可配置各列初始宽度。
- 操作列显式标记 `key: 'action'` 或 `valueType: 'option'`，默认固定右侧、不允许列设置隐藏，不提供拖拽。
- 拖拽结束、取消和组件卸载均清理指针监听；存储不可用或损坏不影响操作，拖拽不触发排序。
- 排序仅透传页面声明的 `sorter/onChange`，不替接口增加排序能力、不默认对服务端分页结果作本地排序。
- 新增或修改时间列复用 `DateTimeText`；现有业务列显示内容不因组件迁移重新解释。
- 字体、密度、色彩、圆角跟随现有 ThemeProvider 与 `--crm-*`，业务页不得复制通用表格 CSS。
- 特定业务行高亮（例如消息未读）与编辑控件布局可保留在业务样式中。

## 已迁移入口与兼容

- 主从表格模式：客资、学员、订单、订单审批、主管确认、BPM 审批、客资申诉/投诉/查重、公告、消息。
- 管理表格：派单、下属销售、下属兼职、配置、返现/提现、审计、关系和通知规则、资产、素材审批、导出任务。
- 子表：素材选择、定位素材选择、兼职绑定、兼职归属历史、关系日志、订单明细及定位访谈。
- 保留原有 `columnsState` 存储键；客资和学员通过 `widthPersistenceKey` 继续读取旧列宽。
- 保留 `useInboxTableLayout` 的桌面切换约定。移动端原有主从列表不被强制改成表格；始终显示的表格使用横向滚动。
- `src/__probe__/FontProbe.tsx` 是 Ant Design 字体诊断探针，不是业务入口，继续直接检测底层组件。

## 验证

`BusinessTable.test.tsx` 覆盖原生列适配、旧列标识、损坏存储、紧凑编辑、错误/空态及无权限隐藏数据。
`entry.guard.test.ts` 扫描业务页面与组件，阻止绕开统一入口及重复/缺失 tableKey。
既有客资批量、服务端排序、表格模式、可见字段与样式 guard 已跟随抽离边界更新，业务断言保留。
前端执行 Vitest、TypeScript 与 Vite 生产构建；真实浏览器还需检查列宽拖拽、排序、列显隐、分页、
跨页选择、全屏及主题在桌面/移动宽度的表现。测试夹具数据只存在于 test 目录。
此任务不执行 Maven，后端代码未修改。

浏览器验收：启动工作台临时 Vite 服务后，可通过
`TABLE_TEST_CHROME=/path/to/chromium TABLE_TEST_BASE=http://127.0.0.1:5186 node test/business-table-browser.mjs`
执行；脚本仅访问 test 夹具，使用 Chromium CDP，不新增项目依赖。截图输出到 `/tmp/zsjos-table-*.png`。
2026-09-20 验收通过桌面 1280px、移动 390px 的布局与核心交互、列显隐及全屏，
并加载真实客资/学员页面组件的隔离接口夹具。服务器缺少中文字体，截图中文字形的视觉验收仍需在正常字体环境补做；未连接真实业务账号执行操作。
