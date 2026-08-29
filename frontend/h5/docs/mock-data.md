# H5 接口与 Mock 说明

## 数据来源

- 兼职业务接口前缀：`/part-api`，通过 `VITE_APP_BASE_API` 配置。
- 公共参考数据接口前缀：`/app-api`，通过 `VITE_APP_REFERENCE_API` 配置。
- 当前联调远程后端：`http://192.168.2.17:48080`（由 Vite 开发代理转发）。
- 兼职接口需要登录 Token；请求同时携带 `tenant-id`。

Mock 数据仅用于本地开发展示，不代表远程数据库真实数据，也不会写入后端。

## Mock 规则

Mock 仅在 Vite 开发环境生效，并且只覆盖 GET 读取请求。以下情况才会触发读取 Mock：

- HTTP 状态码 `404`、`405`、`501`；
- 后端消息明确包含“接口不存在”“接口未实现”“功能不存在”“暂未提供”，或英文 `endpoint not found`、`not implemented`、`feature unavailable`。

远程接口返回空列表时保持真实空数据，不触发 Mock。`401`、`403`、参数校验错误、业务规则错误、网络超时和其他 `5xx` 错误均保持真实失败状态。

写操作（包括提交客资、补充客资、催办、创建投诉、提交申诉、修改个人资料、修改密码、银行卡增删/设默认、提交或取消提现、消息已读、文件上传）不使用 Mock。后端明确不支持时提示“后端接口暂未提供”，不伪造成功。

## 当前读取 Mock

| 页面/功能 | GET 接口 | Mock 返回结构 | 后端补充或确认 |
| --- | --- | --- | --- |
| 首页数据统计 | `/part-api/zsjos/partner/home-statistics` | `{ period, leadCount, withdrawnAmount, validLeadCount, convertedLeadCount }` | 后端尚未实现；五周期固定演示数据仅在开发环境缺失接口时使用 |
| 首页统计明细 | `/part-api/zsjos/partner/home-statistics/details` | 分页 `{ period, metric, list, total, totalAmount? }` | 后端尚未实现；按首页相同周期和指标口径返回客资或已打款提现明细。开发 Mock 使用明显的 `KZ-MOCK`、`TX-MOCK` 编号并在页内展示演示标识，生产环境不启用 |
| 客资来源/分类 | `/app-api/system/dict-data/type` | 字典数组 `{ label, value, colorType }[]` | 已验证远程真实数据；Mock 仅作缺口兜底 |
| 地区选择 | `/app-api/system/area/tree` | 地区树 `{ id, name, selectionCode, leafSelectable, children }[]` | 已验证远程真实数据；Mock 仅作缺口兜底 |
| 个人资料 | `/part-api/zsjos/profile/get` | 资料对象 | 需确认字段及修改接口 |
| 兼职伙伴 | `/part-api/zsjos/partner/me` | 伙伴对象 | 需确认真实伙伴资料接口 |
| 产品目录 | `/part-api/zsjos/lead/product/catalog` | `categoryTree`、`spus`、`skus` | 客资提交依赖真实产品/规格字段 |
| 我的客资 | `/part-api/zsjos/lead/inbox/submitted/page` | 分页 `{ list, total }` | 需确认分页字段和状态枚举 |
| 客资跟进提醒 | `/part-api/zsjos/lead/inbox/submitted/summary`、`/lead/inbox/submitted/page?view=...` | 三类数量和独立分页列表 | 三类数量限定当前 Partner；独立 `/lead/follow-up` 页面内切换，不依赖“我的客资”主页面 |
| 客资详情 | `/part-api/zsjos/lead/get` | 客资详情对象 | 需确认附件和可用操作字段 |
| 客资处理进度、收益详情订单区 | `/part-api/zsjos/lead/{id}/partner-activity` | `{ currentStatus, timeline, followUps, cashbackItems, complaints, orders }` | 后端尚未实现；客资详情优先使用真实且非 Mock 的 `timeline`，缺失时仅按详情已有的提交、首跟、判定、挂起、成交提交、成交和关闭时间生成事实节点；收益详情仍按返现 `orderId` 匹配 `orders[].id`，开发 Mock 的其他聚合内容只作演示且不包含销售、主管或审核人身份 |
| 返现 | `/part-api/zsjos/cashback/my-summary`、`my-page` | 汇总对象、分页 `{ list, total }` | 需确认金额和状态字段 |
| 提现 | `/part-api/zsjos/withdrawal/my-summary`、`my-page`、`my/{id}` | 汇总、分页、详情对象 | 需确认申请状态和金额字段 |
| 银行卡查询 | `/part-api/zsjos/withdrawal/my-cards` | 银行卡数组 | 写操作接口必须由后端提供 |
| 投诉历史 | `/part-api/zsjos/lead-complaint/my-page` | 分页 `{ list, total }` | 需确认投诉字段 |
| 申诉记录 | `/part-api/zsjos/lead/appeal/lead/{id}/list` | 记录数组 | 需确认附件和状态字段 |
| 消息 | `/part-api/zsjos/messages/page`、`{id}`、`unread-count` | 分页、详情、未读数 | 已读写接口必须由后端提供 |

Mock 返回值是最小展示样例，不能用于验证权限、业务规则、金额计算、分页总量或数据库一致性。

## 后端补充清单

需要后端确认或补充的内容：

1. 所有上述 `/part-api` 读取接口的实际路径、请求参数、`CommonResult` 包装和字段命名。
2. 客资产品目录、附件上传（`infraFileId`）、客资提交/补充/催办及投诉、申诉写接口。
3. 返现、提现和银行卡的汇总、分页、详情及全部写操作接口。
4. 消息详情、未读数和标记已读接口。
5. 个人资料查询/修改、修改密码、权限信息和 Token 刷新接口。
6. 状态枚举、日期格式、分页字段以及 401/403/业务错误码约定。
7. 首页统计接口按北京时间返回今日、本周、本月、全年和累计数据；有效、成交客资以周期内提交的客资及其当前状态统计，已提现金额按 `paidAt` 汇总。
8. 首页统计明细接口接收 `period`、`metric`、`pageNo`、`pageSize`，其中 `metric` 为 `lead_count`、`withdrawn_amount`、`valid_lead_count`、`converted_lead_count`；返回值必须复用聚合统计的筛选口径，真实记录 ID 仅用于详情路由，所有用户可见客资编号使用 `leadNo`。
9. 客资处理进度接口必须返回 Partner 专用投影：`currentStatus` 负责当前状态说明，`timeline` 只包含兼职可见的已发生事件；不得下发销售、主管、审核人、部门、派单规则或内部备注。生产环境接口缺失时页面显示不可用和重试，不使用演示时间线。
10. 收益详情复用客资处理进度接口的 `orders` 投影；成交返现只展示与返现 `orderId` 精确匹配的真实订单，生产环境不得把内部 `orderId` 当作订单编号，也不得用其他订单兜底。开发 Mock 可使用固定订单演示数据，但必须显示演示标识。

移除 Mock 前，应逐项用远程后端验证接口状态和响应字段，再删除对应本地 Mock 条目。
