# ZSJOS 双前端菜单覆盖矩阵

本矩阵记录父子菜单解析后的正式 URL。数据库中直接挂在 `/zsjos`“工作台”父菜单下的页面保存相对子路径，例如正式 URL `/zsjos/my-students` 对应菜单 `path=my-students`。React Workbench 的路由与直接访问使用原授权 `menus`，导航使用 System 计算的 `workbenchMenus` 投影；编排只改变分组、顺序和导航显隐，不改变本表的正式 URL、组件或权限身份。Vue Admin 继续通过服务端 `component` 字段动态解析组件。两端不得维护独立菜单树或旧路径别名。

`/zsjos/tasks/today` 是员工工作台“首页”。密码登录或已有令牌恢复并重新读取权限菜单后，拥有该授权菜单的用户固定进入首页；未获授权的用户进入首个可访问的服务端菜单。首页按权限分别加载业务待办、BPM 审批计数和公告，并从月历跳转到服务端授权的 `/calendar/all` 日历日程页。

审批中心是 BPM 任务统一入口，不复制业务审批页。能接入员工端业务页的流程由服务端统一定位接口深链到真实业务页，未接入流程回到完整 BPM 表单。待办任务和已办任务的左侧列表使用系统 20 条追加懒加载模式，不提供前端分页器。

Vue Admin 的 `/system/workbench-layout`（`system/workbenchLayout/index`）是
`workbenchRenderMode=admin_only` 的系统配置页，不属于员工业务页面，也不计入下表的
双前端覆盖数量。全局候选页面来自租户套餐，角色覆盖候选页面来自所选角色当前直接授权的有效页面；发布后由所有 Workbench 桌面布局与移动抽屉
统一消费；未发布或解析失败时回退原授权菜单树。

H5 的 `zsjos:partner:self-query` 等纯权限节点不是后台页面，不计入下表 38 个页面路由。V071 只把已失去有效父菜单的兼职权限按钮归为根级、无 path/component 的不可路由元数据，不重建已由 V069 退役的 `partner-portal` 页面。

| # | 菜单 | 正式路径 | React Workbench | Vue Admin 组件 |
|---:|---|---|---|---|
| 1 | 提交客资 | `/zsjos/leads/submit` | `LeadSubmissionPage` | `zsjos/leadSubmission/index` |
| 2 | 客资管理 | `/zsjos/leads/manage` | `LeadManagementPage`（全部/我提交的/我负责的） | `zsjos/lead/index` |
| 3 | 销售自拓 | `/zsjos/leads/self-sourced` | `LeadSubmissionPage(selfSourced)` | `zsjos/leadSelfSourced/index` |
| 4 | 销售投诉处理 | `/zsjos/leads/complaints` | `LeadComplaintPage` | `zsjos/leadComplaint/index` |
| 5 | 我提交的（兼容路径） | `/zsjos/leads/submitted` | 重定向至客资管理并选择提交范围 | 隐藏权限节点 |
| 6 | 我负责的（兼容路径） | `/zsjos/leads/owned` | 重定向至客资管理并选择负责范围 | 隐藏权限节点 |
| 7 | 派单关系配置 | `/zsjos/leads/assignment-relations` | `LeadAssignmentPage` | `zsjos/leadAssignment/index` |
| 8 | 重复客资复核 | `/zsjos/leads/duplicate-review` | `LeadDuplicateReviewPage` | `zsjos/leadDuplicateReview/index` |
| 9 | 客资抢单池 | `/zsjos/claim-pool` | `LeadClaimPoolPage` | `zsjos/leadClaimPool/index` |
| 10 | 公海 | `/zsjos/lead-aging-pool` | `LeadAgingPoolPage` | `zsjos/leadAgingPool/index` |
| 11 | 客资派单规则 | `/zsjos/lead-rule` | `LeadRuleConfigPage` | `zsjos/leadRule/index` |
| 12 | 客资筛选方案 | `/zsjos/lead-filter` | `LeadFilterConfigPage` | `zsjos/leadFilter/index` |
| 12.1 | 高级筛选预置 | `/zsjos/advanced-filter-template` | 不注册（Admin 配置页） | `zsjos/advancedFilterTemplate/index`（`workbenchRenderMode=admin_only`） |
| 13 | 客资跟进规则 | `/zsjos/lead-follow-up-rule` | `LeadFollowUpRuleConfigPage` | `zsjos/leadFollowUpRule/index` |
| 14 | 产品配置 | `/zsjos/product` | `ProductConfigPage` | `zsjos/product/index` |
| 15 | 计划配置 | `/zsjos/work-plan-config` | `WorkPlanConfigPage` | `zsjos/workPlanConfig/index` |
| 16 | 下属销售 | `/zsjos/subordinate-sales` | `SubordinateSalesPage` | `zsjos/subordinateSales/index` |
| 17 | 首页 | `/zsjos/tasks/today` | `TodayTasksPage` | `zsjos/todayTask/index` |
| 18 | 工作计划 | `/zsjos/work-plans` | `WorkPlanPage` | `zsjos/workPlan/index` |
| 20 | 申诉处理 | `/zsjos/appeals` | `LeadAppealPage` | `zsjos/leadAppeal/index` |
| 21 | 我的订单 | `/zsjos/sales-orders/my` | `MySalesOrderPage` | `zsjos/mySalesOrder/index` |
| 21.1 | 团队订单 | `/zsjos/sales-orders/team` | `MySalesOrderPage(team)` | `zsjos/mySalesOrder/index` |
| 22 | 成交订单审批 | `/zsjos/sales-order-approvals` | `SalesOrderApprovalPage` | `zsjos/salesOrderApproval/index` |
| 23 | 历史客户复购 | `/zsjos/orders/external-repurchase` | `ExternalRepurchasePage` | `zsjos/externalRepurchase/index` |
| 24 | 导出任务 | `/zsjos/export-task` | `ExportTaskPage` | `zsjos/exportTask/index` |
| 25 | 人员管理 | `/zsjos/personnel` | `PersonnelPage` | `zsjos/personnel/index` |
| 26 | 兼职管理 | `/zsjos/partner` | `SubordinatePartnerPage` | `zsjos/partner/index` |
| 27 | 只读借视图 | `/zsjos/impersonation` | `ImpersonationPage` | `zsjos/impersonation/index` |
| 28 | 业务审计 | `/zsjos/business-audit` | `BusinessAuditPage` | `zsjos/businessAudit/index` |
| 29 | 返现管理 | `/zsjos/cashback` | `CashbackPage` | `zsjos/cashback/index` |
| 30 | 提现管理 | `/zsjos/withdrawal` | `WithdrawalPage` | `zsjos/withdrawal/index` |
| 31 | 用户关系场景 | `/system/user-relation` | `UserRelationPage` | `zsjos/userRelation/index` |
| 32 | 维护模式 | `/system/maintenance` | `MaintenancePage` | `system/maintenance/index` |
| 33 | 业务通知规则 | `/messages/notify/notify-rule` | `NotifyRulePage` | `system/notify/rule/index` |
| 34 | 全部消息 | `/messages/all` | `MessageInboxPage(view=all)` | `system/notify/my/all/index` |
| 35 | 未读消息 | `/messages/unread` | `MessageInboxPage(view=unread)` | `system/notify/my/unread/index` |
| 36 | 报名履约公共池 | `/zsjos/registration-pool` | `RegistrationPoolPage` | `zsjos/registration-pool` |
| 37 | 履约清单配置 | `/zsjos/registration-checklist-config` | `RegistrationChecklistConfigPage` | `zsjos/registrationChecklistConfig/index` |
| 38 | 我的学员 | `/zsjos/my-students` | `MyStudentsPage` | `zsjos/my-students` |
| 39 | 学员联系配置 | `/zsjos/student-contact-config` | `StudentContactConfigPage` | `zsjos/studentContactConfig/index` |
| 40 | 采访表单配置 | `/zsjos/director-config/interview-template` | `DirectorTemplateConfigPage` | `zsjos/directorTemplate/index` |
| 40.1 | 定位卡模板配置 | `/zsjos/director-config/positioning-template` | `DirectorTemplateConfigPage` | `zsjos/directorTemplate/index` |
| 40.2 | 编导时效配置 | `/zsjos/director-config/sla` | `DirectorConfigPage` | `zsjos/directorConfig/index` |
| 40 | 异常情况处理 | `/zsjos/student-contact-exceptions` | `StudentContactExceptionsPage` | `zsjos/studentContactExceptions/index` |
| 41 | 拍剪工单 | `/zsjos/production-tickets` | `MediaWorkflowPage` | `zsjos-workbench/MediaProductionTicketsPage` |
| 45 | 我的学员 | `/zsjos/media-students` | `MediaStudentsPage` | `zsjos-workbench/MediaStudentsPage` |
| 46 | 第三方账号字段配置 | `/zsjos/media-account-field-config` | 不注册（Admin 配置页） | `zsjos/mediaAccountFieldConfig/index` |
| 47 | 账号日历 | `/calendar/overview` | `MediaCalendarPage(scope=account)` | `zsjos/mediaCalendar/index` |
| 47.1 | 日历日程 | `/calendar/all` | `MediaCalendarPage(scope=all)` | `zsjos/mediaCalendarAll/index` |
| 48 | 需求与反馈 | `/zsjos/feedback` | `FeedbackPage` | `zsjos/feedback/index` |
| 49 | 我的资产 | `/zsjos/my-assets` | `EamAssetPage(view=assets)` | 不注册（员工自助；管理员从 HRM 员工档案查看） |
| 50 | 采购申请 | `/zsjos/asset-demands` | `EamAssetPage(view=demands)` | 不注册（员工自助；EAM 后台独立管理） |
| 51 | 通知公告 | `/messages/notice` | `AnnouncementCenterPage` | `system/notice/index`（公告管理，原菜单 107；员工只读权限为子按钮 `79913` / `system:notice:read`） |
| 52 | 强制表单 | `/zsjos/forced-form` | 不注册（全局强制填写 Provider） | `zsjos/forcedForm/index`（Admin 配置页，`workbenchRenderMode=admin_only`） |

“我的学员”按 Person 聚合并按服务关系切换。规划师页与媒体学员页共享 Person/课程服务详情壳，但业务投影不同：规划师可在真实 Lead 存在时追加获准的客资历史；媒体学员页始终以 Person、课程服务和账号为主体，不加载或展示 Lead、客资编号、联系历史或沟通记录。学习规划师确认接收后，可按服务端动作投影分配编导或职业规划师。媒体页只消费 `contact-context` 中的负责人、编导阶段、预约时间和 `availableActions`，账号、定位、内容和拍剪操作继续由各自接口及对象权限控制。

Vue Admin 的 `zsjos/registration-pool` 与 `zsjos/my-students` 组件分别落地为 `src/views/zsjos/registration-pool.vue` 和 `src/views/zsjos/my-students.vue`，与服务端菜单的 `component` 值直接对应。

## 路径约束

- 申诉正式路径仅为 `/zsjos/appeals`，不提供 `/zsjos/leads/appeals` 兼容跳转。
- 公海正式路径仅为 `/zsjos/lead-aging-pool`，不提供 `/zsjos/opportunity-public-sea` 兼容跳转。
- 团队订单正式路径为 `/zsjos/sales-orders/team`，只消费服务端 `zsjos:sales-order:query-team`
  页面授权；详情使用只读 `team` 模式，不投影修改、终止或审批操作。
- `/zsjos/leads/manage` 是服务端隐藏菜单：具备菜单授权时可以直接访问，但不显示在 React 导航中。
- `/zsjos/feedback` 由服务端相对子路径 `feedback` 解析，要求页面权限 `zsjos:feedback:query`；创建、查看、回复和满意度入口分别消费独立按钮权限。通知深链使用 `feedbackId`，详情仍由服务端校验本人数据范围。
- `/zsjos/my-assets` 与 `/zsjos/asset-demands` 分别由服务端相对子路径 `my-assets`、`asset-demands` 解析。页面查询权限为 `eam:workbench:asset:query`、`eam:workbench:demand:query`；签收、退还、报修和提交申请继续使用独立按钮权限，Workbench 不按持有状态之外的前端推断扩大操作权。
- 订单、学员、审批和业务通知可深链到 `/zsjos/leads/manage?leadId={内部客资ID}&tab={overview|follow-ups|orders|appeals|complaints|flow-history}`；省略或传入非法 `tab` 时进入概览。该入口只加载指定详情，不扩大客资列表。通知按场景选择跟进、申诉、投诉或概览页签，当前不自动改投流转记录；前端只会激活服务端 `visibleTabs` 中的目标页签，不可见时回退概览。五个业务标签权限在 System 角色权限管理中独立配置，`flow-history` 对应 `zsjos:lead-detail:flow-read`，不得由角色名或前端 mode 推断。
- 历史 `/zsjos/sales-order-supervisor-confirmations` 仅由 React 重定向到 `/zsjos/sales-order-approvals`，服务端不再发布独立主管确认页面菜单。
- 新增或修改页面菜单时，必须同步服务端菜单种子、React `APP_ROUTES`/`RENDERABLE_APP_ROUTES`/`RouteHost`、Vue `component` 文件和本矩阵。
- 强制表单管理页是 Vue Admin 配置面，使用服务端权限 `zsjos:forced-form:*`，不作为 Workbench 可导航页面注册。员工端通过全局 `ForcedFormProvider` 在登录、刷新、路由切换、401 恢复和 WebSocket 重连后查询 `/zsjos/forced-form/pending`，有待办时显示不可关闭填写面并阻断普通业务接口。
- `/zsjos/my-students` 仅属于 `study_planner`；`content_director` 和 `new_media_operator` 共用 `/zsjos/media-students` 与 `zsjos:media-student:query-my`，后端分别按服务关系、账号责任关系和本人任务限制学员范围。
- `/zsjos/accounts`、`/zsjos/content`、`/zsjos/positioning` 的页面菜单由 V113 退役。稳定的查询和操作权限字符串保留，并调整到学员菜单下；账号、内容和定位只能从具体学员的相应标签进入。
The subordinate-sales left pane uses the shared 20-row append lazy-loading pattern with a scroll-root sentinel, stable server ordering, deduplication, stale-request rejection, and retryable load-more failure. The `一键下班` command is rendered only from `zsjos:subordinate-sales:pause-all`; its scope is entirely server-owned. The home page and header consume one dispatch-status provider so mode, heartbeat, page-offline state, retry, and eligibility remain synchronized without duplicate polling.

The subordinate Lead detail and batch toolbar render restore, transfer, recycle, claim-pool release,
and public-sea release only from their server-returned button permissions. The canonical public-sea
page remains `/zsjos/lead-aging-pool`; the retired `/zsjos/opportunity-public-sea` path is not aliased.
The public-sea page reuses the Lead-management status bar, list item, selected state, loading/empty/error
states, complete `LeadDetail`, desktop master-detail layout, and mobile detail behavior. Its paging,
statistics, A/B ownership fields, entry/expiry data, and `availableActions` remain public-sea-owned; it
does not keep a separate Card/List/Pagination presentation or infer actions from roles.

### Media student center

- `/zsjos/media-students` is rendered by `MediaStudentsPage` and requires `zsjos:media-student:query-my`.
- The page follows the same responsive master-detail layout as `/zsjos/my-students`. Its three tabs are overview, third-party platform accounts (including positioning cards and account-scoped status maintenance), and content production history. The former student-information/talk-record tab is not part of the media workspace; old `tab=student` links fall back to overview, while retired `tab=maintenance` and `tab=positioning` links resolve to the account tab.
- Directors see their service-relation or account responsibility scope. Operators see only students related to accounts, content, positioning, or tasks they currently own. Each detail and command is re-authorized independently by the backend.
- 账号状态维护使用服务端按钮权限 `zsjos:media-account:maintenance`，按钮节点挂在当前媒体学员页面菜单 `7022` 下；Workbench 在每个账号行展示该账号的状态摘要和服务端投影的维护/查看入口，完整快照与历史位于选中账号区域，不建立学员级“状态维护”页签。已退役的第三方账号页面 `6970` 不再承载该按钮或任何前端权限来源。
- 定位卡运营确认和退回继续使用 `zsjos:positioning-card:operator-confirm`、`zsjos:positioning-card:operator-reject`；生成或重新生成学员外链使用独立按钮权限 `zsjos:positioning-card:student-link-generate`。V134 将该按钮挂在媒体学员页下并授予现有 `new_media_operator` 角色，服务端仍独立校验当前运营归属、最新版和状态。
- 定位卡不再展示确认试跑或归档动作。学员确认即完成本轮；原编导在同时具备 `zsjos:positioning-card:edit` 和对象权限时收到服务端 `START_POSITIONING_REVISION` 动作。Workbench 按账号分别展示“当前生效定位”“审核中版本”“历史版本”，并只用 `effective` 版本开放拍剪工单入口。
- Account, content, and positioning notifications deep-link to the student center with `personId`, `tab`, and the relevant record ID. Historical records without a student binding show an explicit unavailable target instead of opening a retired route.
