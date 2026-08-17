# ZSJOS 双前端菜单覆盖矩阵

本矩阵以 `system_menu` 返回的正式路径为准。React Workbench 只渲染授权菜单；隐藏菜单仍可在授权后直达，但不出现在导航。Vue Admin 继续通过服务端 `component` 字段动态解析组件。两端不得维护独立菜单树或旧路径别名。

H5 的 `zsjos:partner:self-query` 等纯权限节点不是后台页面，不计入下表 36 个页面路由。V071 只把已失去有效父菜单的兼职权限按钮归为根级、无 path/component 的不可路由元数据，不重建已由 V069 退役的 `partner-portal` 页面。

| # | 菜单 | 正式路径 | React Workbench | Vue Admin 组件 |
|---:|---|---|---|---|
| 1 | 提交客资 | `/zsjos/leads/submit` | `LeadSubmissionPage` | `zsjos/leadSubmission/index` |
| 2 | 客资管理（隐藏） | `/zsjos/leads/manage` | `LeadManagementPage(audience=all)` | `zsjos/lead/index` |
| 3 | 销售自拓 | `/zsjos/leads/self-sourced` | `LeadSubmissionPage(selfSourced)` | `zsjos/leadSelfSourced/index` |
| 4 | 销售投诉处理 | `/zsjos/leads/complaints` | `LeadComplaintPage` | `zsjos/leadComplaint/index` |
| 5 | 我提交的 | `/zsjos/leads/submitted` | `LeadManagementPage(audience=submitter)` | `zsjos/leadInbox/submitted` |
| 6 | 我负责的 | `/zsjos/leads/owned` | `LeadManagementPage(audience=owner)` | `zsjos/leadInbox/owned` |
| 7 | 派单关系配置 | `/zsjos/leads/assignment-relations` | `LeadAssignmentPage` | `zsjos/leadAssignment/index` |
| 8 | 重复客资复核 | `/zsjos/leads/duplicate-review` | `LeadDuplicateReviewPage` | `zsjos/leadDuplicateReview/index` |
| 9 | 客资抢单池 | `/zsjos/claim-pool` | `LeadClaimPoolPage` | `zsjos/leadClaimPool/index` |
| 10 | 商机公海 | `/zsjos/lead-aging-pool` | `LeadAgingPoolPage` | `zsjos/leadAgingPool/index` |
| 11 | 客资派单规则 | `/zsjos/lead-rule` | `LeadRuleConfigPage` | `zsjos/leadRule/index` |
| 12 | 客资筛选方案 | `/zsjos/lead-filter` | `LeadFilterConfigPage` | `zsjos/leadFilter/index` |
| 13 | 客资跟进规则 | `/zsjos/lead-follow-up-rule` | `LeadFollowUpRuleConfigPage` | `zsjos/leadFollowUpRule/index` |
| 14 | 产品配置 | `/zsjos/product` | `ProductConfigPage` | `zsjos/product/index` |
| 15 | 计划配置 | `/zsjos/work-plan-config` | `WorkPlanConfigPage` | `zsjos/workPlanConfig/index` |
| 16 | 下属销售 | `/zsjos/subordinate-sales` | `SubordinateSalesPage` | `zsjos/subordinateSales/index` |
| 17 | 今日待办 | `/zsjos/tasks/today` | `TodayTasksPage` | `zsjos/todayTask/index` |
| 18 | 工作计划 | `/zsjos/work-plans` | `WorkPlanPage` | `zsjos/workPlan/index` |
| 19 | 异常客资 | `/zsjos/leads/qualification-exceptions` | `LeadQualificationExceptionPage` | `zsjos/leadQualification/index` |
| 20 | 申诉处理 | `/zsjos/appeals` | `LeadAppealPage` | `zsjos/leadAppeal/index` |
| 21 | 我的订单 | `/zsjos/sales-orders/my` | `MySalesOrderPage` | `zsjos/mySalesOrder/index` |
| 22 | 成交审批 | `/zsjos/sales-order-approvals` | `SalesOrderApprovalPage` | `zsjos/salesOrderApproval/index` |
| 23 | 主管确认 | `/zsjos/sales-order-supervisor-confirmations` | `SalesOrderSupervisorConfirmationPage` | `zsjos/salesOrderSupervisorConfirmation/index` |
| 24 | 历史客户复购 | `/zsjos/orders/external-repurchase` | `ExternalRepurchasePage` | `zsjos/externalRepurchase/index` |
| 25 | 导出任务 | `/zsjos/export-task` | `ExportTaskPage` | `zsjos/exportTask/index` |
| 26 | 人员管理 | `/zsjos/personnel` | `PersonnelPage` | `zsjos/personnel/index` |
| 27 | 兼职主体 | `/zsjos/partner` | `PartnerPage` | `zsjos/partner/index` |
| 28 | 只读借视图 | `/zsjos/impersonation` | `ImpersonationPage` | `zsjos/impersonation/index` |
| 29 | 业务审计 | `/zsjos/business-audit` | `BusinessAuditPage` | `zsjos/businessAudit/index` |
| 30 | 返现管理 | `/zsjos/cashback` | `CashbackPage` | `zsjos/cashback/index` |
| 31 | 提现管理 | `/zsjos/withdrawal` | `WithdrawalPage` | `zsjos/withdrawal/index` |
| 32 | 用户关系场景 | `/system/user-relation` | `UserRelationPage` | `zsjos/userRelation/index` |
| 33 | 维护模式 | `/system/maintenance` | `MaintenancePage` | `system/maintenance/index` |
| 34 | 业务通知规则 | `/messages/notify/notify-rule` | `NotifyRulePage` | `system/notify/rule/index` |
| 35 | 全部消息 | `/messages/all` | `MessageInboxPage(view=all)` | `system/notify/my/all/index` |
| 36 | 未读消息 | `/messages/unread` | `MessageInboxPage(view=unread)` | `system/notify/my/unread/index` |

## 路径约束

- 申诉正式路径仅为 `/zsjos/appeals`，不提供 `/zsjos/leads/appeals` 兼容跳转。
- 商机公海正式路径仅为 `/zsjos/lead-aging-pool`，不提供 `/zsjos/opportunity-public-sea` 兼容跳转。
- `/zsjos/leads/manage` 是服务端隐藏菜单：具备菜单授权时可以直接访问，但不显示在 React 导航中。
- 新增或修改页面菜单时，必须同步服务端菜单种子、React `APP_ROUTES`/`RENDERABLE_APP_ROUTES`/`RouteHost`、Vue `component` 文件和本矩阵。
