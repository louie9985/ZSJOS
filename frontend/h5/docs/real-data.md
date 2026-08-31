# H5 真实接口说明

## 数据来源

- 兼职业务接口前缀：`/part-api/zsjos/**`，通过 `VITE_APP_BASE_API` 配置。
- 公共参考数据接口前缀：`/app-api/system/**`，通过 `VITE_APP_REFERENCE_API` 配置。
- 兼职业务接口需要 `PARTNER` Token，并携带 `tenant-id`。
- 客资来源、客资分类和地区树使用公开 System 参考数据客户端，只携带 `tenant-id`，不携带 `Authorization`。

## 运行规则

H5 本地、开发和生产运行面均使用后端真实数据。前端不再为缺失接口、空列表、权限失败或网络失败提供本地替代数据。

- 后端返回空分页或空数组时展示真实空状态。
- `401` 进入既有刷新或重新登录流程。
- `403`、业务错误、参数错误、网络失败和服务端错误展示明确失败状态和重试入口。
- 后端明确返回接口缺失或未实现时展示“后端接口暂未提供”，不伪造成功。
- 文件、反馈、银行卡、提现、客资、投诉、申诉和消息已读等写操作全部以服务端结果为准。

## 已切换真实接口

| 页面/功能 | 接口 |
| --- | --- |
| 首页统计 | `GET /part-api/zsjos/partner/home-statistics` |
| 首页统计明细 | `GET /part-api/zsjos/partner/home-statistics/details` |
| 排行榜配置 | `GET /part-api/zsjos/partner/leaderboard/config` |
| 排行榜数据 | `GET /part-api/zsjos/partner/leaderboard` |
| 客资处理进度 | `GET /part-api/zsjos/lead/{leadId}/partner-activity` |
| 客资筛选项 | `GET /part-api/zsjos/lead/partner-filter-options` |
| 消息分组 | `GET /part-api/zsjos/messages/groups` |
| 消息分页 | `GET /part-api/zsjos/messages/page` |
| 反馈入口 | `GET /part-api/zsjos/feedback/portal` |
| 反馈动态表单 | `GET /part-api/zsjos/feedback/form` |
| 创建反馈 | `POST /part-api/zsjos/feedback/{type}/create` |
| 反馈分页 | `GET /part-api/zsjos/feedback/my-page` |
| 反馈详情 | `GET /part-api/zsjos/feedback/{id}` |
| 反馈已读 | `PUT /part-api/zsjos/feedback/{id}/read` |
| 反馈回复 | `POST /part-api/zsjos/feedback/{id}/reply` |
| 反馈附件上传 | `POST /part-api/zsjos/feedback/file/upload` |
| 银行卡编辑 | `PUT /part-api/zsjos/withdrawal/my-cards/{id}` |

## 关键契约

- 用户可见客资编号只展示 `leadNo`；缺失时显示“客资编号暂未生成”，不回退展示内部 `id` 或 `leadId`。
- 首页统计和明细按北京时间周期计算，明细记录只能跳转真实的客资或提现详情。
- 客资处理进度只展示兼职可见事件，不下发销售、主管、审核人、部门、派单规则或内部备注。
- 成交返现订单区只展示与返现 `orderId` 精确匹配的真实订单。
- 反馈由后端动态表单定义字段、配置版本和可用入口；需求反馈如启用审批，兼职端提交会被服务端拒绝为暂不支持。
- 反馈附件上传使用后端 Partner 目录和 Infra 文件归属校验。
