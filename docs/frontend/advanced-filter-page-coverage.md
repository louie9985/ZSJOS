# 中视简筛选页面接入扫描矩阵

2026-09-23 当前工作树快照；[主矩阵](advanced-filter-inventory.md)记录人工核对结论，[字段目录](advanced-filter-field-catalog.md)记录可用字段。

## 1. 扫描口径

本附件按源文件穷举，避免只看已接入高级筛选的页面。源文件不等于已发布菜单；复用壳、旧页和辅助文件单独标注。所有命中属于源码证据，不代表动态菜单权限或浏览器行为已经验证。

“普通入口线索”提取placeholder中搜索/筛选提示及query/filter模型绑定，可能同时包含详情弹窗内选择器，不能据此认定全部是列表过滤条件。无命中仅表示该文件没有显式字符串，需查看复用组件和请求VO；核心页面已经在主矩阵人工追查。

复合文件中的“统一目录入口”表示至少一个视图接入，不表示文件内全部页面接入。例如 ManagementPages 只有返现/提现使用统一组件，RegistrationPages 只有履约公共池/学员列表使用统一组件。字段配置、编辑器、辅助函数名称可能随同页导出列出，不计作正式页面。

Workbench扫描src/pages全部非测试TSX；Admin扫描views/zsjos所有index.vue及该目录根部Vue页（嵌套编辑器不作为页面重复计数）；H5扫描src/pages所有Vue。共享高级筛选控件不当作页面。其余System/Infra/BPM后台原生页不在此文件级范围。

## Workbench

扫描源文件 49 个（含子目录业绩共享控件）。

| 源文件中的页面/组件 | 证据 | 接入状态 | scene | pageKey | 普通入口线索 |
|---|---|---|---|---|---|
| AnnouncementCenterPage | [AnnouncementCenterPage.tsx](../../frontend/workbench/src/pages/AnnouncementCenterPage.tsx) | 未发现统一目录入口（不等于无普通筛选） | — | — | 搜索公告标题或正文 |
| BpmApprovalCenterPage | [BpmApprovalCenterPage.tsx](../../frontend/workbench/src/pages/BpmApprovalCenterPage.tsx) | 未发现统一目录入口（不等于无普通筛选） | — | — | 搜索任务名称 |
| LeadRuleConfigPage, LeadFollowUpRuleConfigPage, LeadFilterConfigPage, ProductConfigPage, WorkPlanConfigPage | [ConfigurationPages.tsx](../../frontend/workbench/src/pages/ConfigurationPages.tsx) | 未发现统一目录入口（不等于无普通筛选） | — | — | 搜索产品；全部计划类型 |
| ContentProductionPage | [ContentProductionPage.tsx](../../frontend/workbench/src/pages/ContentProductionPage.tsx) | 未发现统一目录入口（不等于无普通筛选） | — | — | 搜索内容编号或标题 |
| AccountProfiles, DraftEditDialog, ContentReviewBatchPage | [ContentReviewBatchPage.tsx](../../frontend/workbench/src/pages/ContentReviewBatchPage.tsx) | 未发现统一目录入口（不等于无普通筛选） | — | — | 搜索内容编号或标题 |
| CourseCalendarPage | [CourseCalendarPage.tsx](../../frontend/workbench/src/pages/CourseCalendarPage.tsx) | 未发现统一目录入口（不等于无普通筛选） | — | — | 无显式线索；查看源码/复用组件 |
| DeliveryClassPage | [DeliveryClassPage.tsx](../../frontend/workbench/src/pages/DeliveryClassPage.tsx) | 未发现统一目录入口（不等于无普通筛选） | — | — | 无显式线索；查看源码/复用组件 |
| DirectorTemplateConfigPage, DirectorSlaConfigPage | [DirectorConfigPages.tsx](../../frontend/workbench/src/pages/DirectorConfigPages.tsx) | 未发现统一目录入口（不等于无普通筛选） | — | — | 无显式线索；查看源码/复用组件 |
| EamAssetPage | [EamAssetPage.tsx](../../frontend/workbench/src/pages/EamAssetPage.tsx) | 未发现统一目录入口（不等于无普通筛选） | — | — | 无显式线索；查看源码/复用组件 |
| ExamCalendarPage | [ExamCalendarPage.tsx](../../frontend/workbench/src/pages/ExamCalendarPage.tsx) | 未发现统一目录入口（不等于无普通筛选） | — | — | 状态 |
| ExportTaskPage | [ExportTaskPage.tsx](../../frontend/workbench/src/pages/ExportTaskPage.tsx) | 未发现统一目录入口（不等于无普通筛选） | — | — | 无显式线索；查看源码/复用组件 |
| ExternalRepurchasePage | [ExternalRepurchasePage.tsx](../../frontend/workbench/src/pages/ExternalRepurchasePage.tsx) | 未发现统一目录入口（不等于无普通筛选） | — | — | 无显式线索；查看源码/复用组件 |
| FeedbackPage | [FeedbackPage.tsx](../../frontend/workbench/src/pages/FeedbackPage.tsx) | 未发现统一目录入口（不等于无普通筛选） | — | — | 全部类型；全部状态 |
| money | [financeTableColumns.tsx](../../frontend/workbench/src/pages/financeTableColumns.tsx) | 辅助列定义，不是页面 | — | — | 无显式线索；查看源码/复用组件 |
| LeadAgingPoolPage | [LeadAgingPoolPage.tsx](../../frontend/workbench/src/pages/LeadAgingPoolPage.tsx) | 统一目录入口 | lead | lead_aging_pool | 公海池常驻筛选；搜索客资编号 / 姓名 / 手机号 / 微信号；选择同部门启用销售 |
| LeadAppealPage | [LeadAppealPage.tsx](../../frontend/workbench/src/pages/LeadAppealPage.tsx) | 统一目录入口 | lead_appeal | lead_appeal | 搜索客资编号 / 姓名 / 手机号 / 微信号 |
| LeadAssignmentPage | [LeadAssignmentPage.tsx](../../frontend/workbench/src/pages/LeadAssignmentPage.tsx) | 未发现统一目录入口（不等于无普通筛选） | — | — | 搜索姓名或手机号；全部配置状态；搜索姓名、手机号或部门 |
| LeadClaimPoolPage | [LeadClaimPoolPage.tsx](../../frontend/workbench/src/pages/LeadClaimPoolPage.tsx) | 统一目录入口 | lead | lead_claim_pool | 搜索客资编号 / 姓名 / 手机号 / 微信号 |
| LeadComplaintPage | [LeadComplaintPage.tsx](../../frontend/workbench/src/pages/LeadComplaintPage.tsx) | 未发现统一目录入口（不等于无普通筛选） | — | — | 无显式线索；查看源码/复用组件 |
| LeadDuplicateReviewPage | [LeadDuplicateReviewPage.tsx](../../frontend/workbench/src/pages/LeadDuplicateReviewPage.tsx) | 统一目录入口 | duplicate_review | lead_duplicate_review | 搜索姓名 / 手机号 / 微信号 |
| LeadFollowUpCalendarPage | [LeadFollowUpCalendarPage.tsx](../../frontend/workbench/src/pages/LeadFollowUpCalendarPage.tsx) | 未发现统一目录入口（不等于无普通筛选） | — | — | 无显式线索；查看源码/复用组件 |
| LeadManagementPage | [LeadManagementPage.tsx](../../frontend/workbench/src/pages/LeadManagementPage.tsx) | 统一目录入口 | lead | lead_management | 客资状态筛选；搜索客资编号 / 姓名 / 手机号 / 微信号 |
| LeadSubmissionPage | [LeadSubmissionPage.tsx](../../frontend/workbench/src/pages/LeadSubmissionPage.tsx) | 未发现统一目录入口（不等于无普通筛选） | — | — | 无显式线索；查看源码/复用组件 |
| PersonnelPage, PartnerPage, ImpersonationPage, BusinessAuditPage, CashbackPage, WithdrawalPage, UserRelationPage, MaintenancePage, NotifyRulePage | [ManagementPages.tsx](../../frontend/workbench/src/pages/ManagementPages.tsx) | 统一目录入口 | lead, withdrawal | cashback, withdrawal | 搜索姓名 / 账号 / 部门；返现状态；提现状态；高级筛选 |
| MaterialApprovalPage | [MaterialApprovalPage.tsx](../../frontend/workbench/src/pages/MaterialApprovalPage.tsx) | 未发现统一目录入口（不等于无普通筛选） | — | — | 无显式线索；查看源码/复用组件 |
| MaterialFields, MaterialLibraryPage | [MaterialLibraryPage.tsx](../../frontend/workbench/src/pages/MaterialLibraryPage.tsx) | 未发现统一目录入口（不等于无普通筛选） | — | — | 搜索并选择生产内容草稿；搜索标题、摘要或内容；搜索账号画像 |
| MediaCalendarPage | [MediaCalendarPage.tsx](../../frontend/workbench/src/pages/MediaCalendarPage.tsx) | 未发现统一目录入口（不等于无普通筛选） | — | — | 搜索账号编号或昵称；当下状态 |
| MediaFeaturePage, AccountsPage, ContentPage, ProductionTicketDetail, ProductionTicketAssignmentHost, PositioningPage | [MediaFeaturePage.tsx](../../frontend/workbench/src/pages/MediaFeaturePage.tsx) | 复用业务页面壳；须随调用方确认 | — | — | 搜索编号、名称或关键词 |
| MediaStudentsPage | [MediaStudentsPage.tsx](../../frontend/workbench/src/pages/MediaStudentsPage.tsx) | 未发现统一目录入口（不等于无普通筛选） | — | — | 搜索姓名或手机号；搜索学员 |
| MessageInboxPage | [MessageInboxPage.tsx](../../frontend/workbench/src/pages/MessageInboxPage.tsx) | 未发现统一目录入口（不等于无普通筛选） | — | — | 搜索消息标题、摘要或正文 |
| MySalesOrderPage | [MySalesOrderPage.tsx](../../frontend/workbench/src/pages/MySalesOrderPage.tsx) | 统一目录入口 | order | sales_order_management | 搜索订单号 / 学员姓名 / 手机号 |
| NoticeManagementPage | [NoticeManagementPage.tsx](../../frontend/workbench/src/pages/NoticeManagementPage.tsx) | 未发现统一目录入口（不等于无普通筛选） | — | — | 搜索公告标题；全部发布状态 |
| PersonalCalendarPage | [PersonalCalendarPage.tsx](../../frontend/workbench/src/pages/PersonalCalendarPage.tsx) | 未发现统一目录入口（不等于无普通筛选） | — | — | 无显式线索；查看源码/复用组件 |
| ProductionTicketsPage | [ProductionTicketsPage.tsx](../../frontend/workbench/src/pages/ProductionTicketsPage.tsx) | 未发现统一目录入口（不等于无普通筛选） | — | — | 搜索工单编号；工单状态；具体状态；截止日期范围 |
| RegistrationPoolPage, MyStudentsPage, StudentPlannerOperations, StudentContactConfigPage, StudentContactExceptionsPage, RegistrationChecklistConfigPage | [RegistrationPages.tsx](../../frontend/workbench/src/pages/RegistrationPages.tsx) | 统一目录入口 | registration, student | registration_pool, student_my | 搜索订单号、客资编号、姓名或手机号；全部班级；全部服务状态；搜索姓名、手机号或客资编号 |
| SalesOrderApprovalPage | [SalesOrderApprovalPage.tsx](../../frontend/workbench/src/pages/SalesOrderApprovalPage.tsx) | 统一目录入口 | order | sales_order_approval:${center}（动态） | 搜索订单号 / 学员姓名 / 手机号 |
| SalesPerformancePage | [SalesPerformancePage.tsx](../../frontend/workbench/src/pages/SalesPerformancePage.tsx) | 未发现统一目录入口（不等于无普通筛选） | — | — | 无显式线索；查看源码/复用组件 |
| SalesPerformanceTargetPage | [SalesPerformanceTargetPage.tsx](../../frontend/workbench/src/pages/SalesPerformanceTargetPage.tsx) | 未发现统一目录入口（不等于无普通筛选） | — | — | 无显式线索；查看源码/复用组件 |
| StudentDeliveryConfigPage | [StudentDeliveryConfigPage.tsx](../../frontend/workbench/src/pages/StudentDeliveryConfigPage.tsx) | 未发现统一目录入口（不等于无普通筛选） | — | — | 无显式线索；查看源码/复用组件 |
| SubordinatePartnerPage | [SubordinatePartnerPage.tsx](../../frontend/workbench/src/pages/SubordinatePartnerPage.tsx) | 未发现统一目录入口（不等于无普通筛选） | — | — | 搜索姓名、编号或手机号 |
| SubordinateSalesPage | [SubordinateSalesPage.tsx](../../frontend/workbench/src/pages/SubordinateSalesPage.tsx) | 统一目录入口 | lead, subordinate_sales | subordinate_sales_leads, subordinate_sales | 搜索客资编号 / 姓名 / 手机号 / 微信号；搜索姓名、账号或手机号；账号状态；页面状态；接单状态 |
| TodayTasksPage | [TodayTasksPage.tsx](../../frontend/workbench/src/pages/TodayTasksPage.tsx) | 未发现统一目录入口（不等于无普通筛选） | — | — | 无显式线索；查看源码/复用组件 |
| UserProfilePage | [UserProfilePage.tsx](../../frontend/workbench/src/pages/UserProfilePage.tsx) | 未发现统一目录入口（不等于无普通筛选） | — | — | 无显式线索；查看源码/复用组件 |
| ViralAccountDecomposePage | [ViralAccountDecomposePage.tsx](../../frontend/workbench/src/pages/ViralAccountDecomposePage.tsx) | 未发现统一目录入口（不等于无普通筛选） | — | — | 无显式线索；查看源码/复用组件 |
| ViralContentDecomposePage | [ViralContentDecomposePage.tsx](../../frontend/workbench/src/pages/ViralContentDecomposePage.tsx) | 未发现统一目录入口（不等于无普通筛选） | — | — | 无显式线索；查看源码/复用组件 |
| WecomClickPage | [WecomClickPage.tsx](../../frontend/workbench/src/pages/WecomClickPage.tsx) | 未发现统一目录入口（不等于无普通筛选） | — | — | 无显式线索；查看源码/复用组件 |
| WorkOrderCenterPage | [WorkOrderCenterPage.tsx](../../frontend/workbench/src/pages/WorkOrderCenterPage.tsx) | 未发现统一目录入口（不等于无普通筛选） | — | — | 无显式线索；查看源码/复用组件 |
| WorkPlanPage | [WorkPlanPage.tsx](../../frontend/workbench/src/pages/WorkPlanPage.tsx) | 未发现统一目录入口（不等于无普通筛选） | — | — | 无显式线索；查看源码/复用组件 |
| PerformanceShared | [PerformanceShared.tsx](../../frontend/workbench/src/pages/performance/PerformanceShared.tsx) | 业绩共享控件，不是独立页面；未接统一目录 | — | — | 期间与人员范围控件，按调用页请求契约核对 |

## Admin ZSJOS

扫描源文件 63 个。

| 源文件中的页面/组件 | 证据 | 接入状态 | scene | pageKey | 普通入口线索 |
|---|---|---|---|---|---|
| ZsjosAdvancedFilterTemplate | [index.vue](../../frontend/admin/src/views/zsjos/advancedFilterTemplate/index.vue) | 筛选模板管理页，不是业务列表条件入口 | — | — | 无显式线索；查看源码/复用组件 |
| ZsjosBusinessAudit | [index.vue](../../frontend/admin/src/views/zsjos/businessAudit/index.vue) | 未发现统一目录入口（不等于无普通筛选） | — | — | 无显式线索；查看源码/复用组件 |
| ZsjosCashback | [index.vue](../../frontend/admin/src/views/zsjos/cashback/index.vue) | 未发现统一目录入口（不等于无普通筛选） | — | — | query.type；query.status；query.pageNo；query.pageSize |
| ZsjosClassManagement | [class-management.vue](../../frontend/admin/src/views/zsjos/class-management.vue) | 未发现统一目录入口（不等于无普通筛选） | — | — | query.status；query.keyword；query.pageNo；query.pageSize |
| courseCalendar/index | [index.vue](../../frontend/admin/src/views/zsjos/courseCalendar/index.vue) | 未发现统一目录入口（不等于无普通筛选） | — | — | 无显式线索；查看源码/复用组件 |
| directorConfig/index | [index.vue](../../frontend/admin/src/views/zsjos/directorConfig/index.vue) | 未发现统一目录入口（不等于无普通筛选） | — | — | 无显式线索；查看源码/复用组件 |
| directorTemplate/index | [index.vue](../../frontend/admin/src/views/zsjos/directorTemplate/index.vue) | 未发现统一目录入口（不等于无普通筛选） | — | — | 采集日期 |
| ZsjosExportTask | [index.vue](../../frontend/admin/src/views/zsjos/exportTask/index.vue) | 未发现统一目录入口（不等于无普通筛选） | — | — | 全部；query.exportType；query.pageNo；query.pageSize |
| externalRepurchase/index | [index.vue](../../frontend/admin/src/views/zsjos/externalRepurchase/index.vue) | 未发现统一目录入口（不等于无普通筛选） | — | — | 无显式线索；查看源码/复用组件 |
| ZsjosFeedbackBug | [index.vue](../../frontend/admin/src/views/zsjos/feedback/bug/index.vue) | 未发现统一目录入口（不等于无普通筛选） | — | — | 无显式线索；查看源码/复用组件 |
| ZsjosFeedbackRequirement | [index.vue](../../frontend/admin/src/views/zsjos/feedback/requirement/index.vue) | 未发现统一目录入口（不等于无普通筛选） | — | — | 无显式线索；查看源码/复用组件 |
| ZsjosFeedbackSettings | [index.vue](../../frontend/admin/src/views/zsjos/feedback/settings/index.vue) | 未发现统一目录入口（不等于无普通筛选） | — | — | 无显式线索；查看源码/复用组件 |
| ZsjosFeedbackSupport | [index.vue](../../frontend/admin/src/views/zsjos/feedback/support/index.vue) | 未发现统一目录入口（不等于无普通筛选） | — | — | 无显式线索；查看源码/复用组件 |
| ZsjosForcedForm | [index.vue](../../frontend/admin/src/views/zsjos/forcedForm/index.vue) | 未发现统一目录入口（不等于无普通筛选） | — | — | 全部状态；全部表单；全部用户；全部平台；请选择部门，后端会展开下级部门；queryParams.name；queryParams.status；queryParams.pageNo；queryParams.pageSize |
| gift/index | [index.vue](../../frontend/admin/src/views/zsjos/gift/index.vue) | 未发现统一目录入口（不等于无普通筛选） | — | — | 无显式线索；查看源码/复用组件 |
| gift-purchase/index | [index.vue](../../frontend/admin/src/views/zsjos/gift-purchase/index.vue) | 未发现统一目录入口（不等于无普通筛选） | — | — | query.studentName；query.orderNo；query.giftKeyword；query.pageNo；query.pageSize |
| ZsjosImpersonation | [index.vue](../../frontend/admin/src/views/zsjos/impersonation/index.vue) | 未发现统一目录入口（不等于无普通筛选） | — | — | 无显式线索；查看源码/复用组件 |
| ZsjosLeadManagement | [index.vue](../../frontend/admin/src/views/zsjos/lead/index.vue) | 统一目录入口 | lead | lead_management | queryParams.status；queryParams.assignmentStatus；queryParams.sourceChannel；queryParams.leadCategory；queryParams.sourceUserId；queryParams.ownerUserId；queryParams.submittedAt；queryParams.pageNo；queryParams.pageSize |
| ZsjosLeadAgingPool | [index.vue](../../frontend/admin/src/views/zsjos/leadAgingPool/index.vue) | 统一目录入口 | lead | lead_aging_pool | 全部状态；选择同部门启用销售；queryParams.status；queryParams.pageNo；queryParams.pageSize |
| leadAppeal/index | [index.vue](../../frontend/admin/src/views/zsjos/leadAppeal/index.vue) | 统一目录入口 | lead_appeal | lead_appeal | 无显式线索；查看源码/复用组件 |
| ZsjosLeadAssignment | [index.vue](../../frontend/admin/src/views/zsjos/leadAssignment/index.vue) | 未发现统一目录入口（不等于无普通筛选） | — | — | 全部部门；全部；搜索姓名、手机号或部门；queryParams.keyword；queryParams.deptId；queryParams.configured；queryParams.pageNo；queryParams.pageSize |
| ZsjosLeadClaimPool | [index.vue](../../frontend/admin/src/views/zsjos/leadClaimPool/index.vue) | 统一目录入口 | lead | lead_claim_pool | queryParams.pageNo；queryParams.pageSize |
| leadComplaint/index | [index.vue](../../frontend/admin/src/views/zsjos/leadComplaint/index.vue) | 未发现统一目录入口（不等于无普通筛选） | — | — | 无显式线索；查看源码/复用组件 |
| leadDuplicateReview/index | [index.vue](../../frontend/admin/src/views/zsjos/leadDuplicateReview/index.vue) | 统一目录入口 | duplicate_review | lead_duplicate_review | 无显式线索；查看源码/复用组件 |
| leadEducationSelfSourced/index | [index.vue](../../frontend/admin/src/views/zsjos/leadEducationSelfSourced/index.vue) | 未发现统一目录入口（不等于无普通筛选） | — | — | 无显式线索；查看源码/复用组件 |
| leaderboardConfig/index | [index.vue](../../frontend/admin/src/views/zsjos/leaderboardConfig/index.vue) | 未发现统一目录入口（不等于无普通筛选） | — | — | 无显式线索；查看源码/复用组件 |
| ZsjosLeadFilter | [index.vue](../../frontend/admin/src/views/zsjos/leadFilter/index.vue) | 未发现统一目录入口（不等于无普通筛选） | — | — | 无显式线索；查看源码/复用组件 |
| ZsjosLeadFollowUpRule | [index.vue](../../frontend/admin/src/views/zsjos/leadFollowUpRule/index.vue) | 未发现统一目录入口（不等于无普通筛选） | — | — | 无显式线索；查看源码/复用组件 |
| ZsjosLeadQualification | [index.vue](../../frontend/admin/src/views/zsjos/leadQualification/index.vue) | 未发现统一目录入口（不等于无普通筛选） | — | — | 无显式线索；查看源码/复用组件 |
| ZsjosLeadRule | [index.vue](../../frontend/admin/src/views/zsjos/leadRule/index.vue) | 未发现统一目录入口（不等于无普通筛选） | — | — | 无显式线索；查看源码/复用组件 |
| leadSelfSourced/index | [index.vue](../../frontend/admin/src/views/zsjos/leadSelfSourced/index.vue) | 未发现统一目录入口（不等于无普通筛选） | — | — | 无显式线索；查看源码/复用组件 |
| leadSubmission/index | [index.vue](../../frontend/admin/src/views/zsjos/leadSubmission/index.vue) | 未发现统一目录入口（不等于无普通筛选） | — | — | 无显式线索；查看源码/复用组件 |
| ZsjosMaterial | [index.vue](../../frontend/admin/src/views/zsjos/material/index.vue) | 未发现统一目录入口（不等于无普通筛选） | — | — | query.keyword；query.materialTypeId；query.status；query.source；query.pageNo；query.pageSize |
| ZsjosMaterialImport | [index.vue](../../frontend/admin/src/views/zsjos/materialImport/index.vue) | 未发现统一目录入口（不等于无普通筛选） | — | — | query.materialTypeId；query.status；query.pageNo；query.pageSize |
| ZsjosMaterialType | [index.vue](../../frontend/admin/src/views/zsjos/materialType/index.vue) | 未发现统一目录入口（不等于无普通筛选） | — | — | 无显式线索；查看源码/复用组件 |
| ZsjosMediaAccountFieldConfig | [index.vue](../../frontend/admin/src/views/zsjos/mediaAccountFieldConfig/index.vue) | 未发现统一目录入口（不等于无普通筛选） | — | — | 无显式线索；查看源码/复用组件 |
| ZsjosMediaCalendar | [index.vue](../../frontend/admin/src/views/zsjos/mediaCalendar/index.vue) | 未发现统一目录入口（不等于无普通筛选） | — | — | query.keyword；query.currentStatusValue；query.stageValue；query.directorUserId；query.operatorUserId；query.pageNo；query.pageSize |
| ZsjosMyClasses | [my-classes.vue](../../frontend/admin/src/views/zsjos/my-classes.vue) | 未发现统一目录入口（不等于无普通筛选） | — | — | 无显式线索；查看源码/复用组件 |
| ZsjosMyStudents | [my-students.vue](../../frontend/admin/src/views/zsjos/my-students.vue) | 统一目录入口 | student | student_my | query.keyword；query.pageNo；query.pageSize |
| mySalesOrder/index | [index.vue](../../frontend/admin/src/views/zsjos/mySalesOrder/index.vue) | 统一目录入口 | order | sales_order_management | 全部状态；queryParams.keyword；queryParams.status |
| ZsjosPartner | [index.vue](../../frontend/admin/src/views/zsjos/partner/index.vue) | 未发现统一目录入口（不等于无普通筛选） | — | — | 全部状态；queryParams.keyword；queryParams.status；queryParams.pageNo；queryParams.pageSize |
| productSubject/index | [index.vue](../../frontend/admin/src/views/zsjos/payment/productSubject/index.vue) | 未发现统一目录入口（不等于无普通筛选） | — | — | queryParams.productName；queryParams.paymentSubjectId；queryParams.pageNo；queryParams.pageSize |
| subject/index | [index.vue](../../frontend/admin/src/views/zsjos/payment/subject/index.vue) | 未发现统一目录入口（不等于无普通筛选） | — | — | 请选择状态；queryParams.subjectName；queryParams.status；queryParams.pageNo；queryParams.pageSize |
| ZsjosPersonalCalendar | [index.vue](../../frontend/admin/src/views/zsjos/personalCalendar/index.vue) | 未发现统一目录入口（不等于无普通筛选） | — | — | 无显式线索；查看源码/复用组件 |
| ZsjosPersonnel | [index.vue](../../frontend/admin/src/views/zsjos/personnel/index.vue) | 未发现统一目录入口（不等于无普通筛选） | — | — | 无显式线索；查看源码/复用组件 |
| ZsjosProduct | [index.vue](../../frontend/admin/src/views/zsjos/product/index.vue) | 未发现统一目录入口（不等于无普通筛选） | — | — | 无显式线索；查看源码/复用组件 |
| ZsjosRegistrationPool | [registration-pool.vue](../../frontend/admin/src/views/zsjos/registration-pool.vue) | 统一目录入口 | registration | registration_pool | 全部状态；query.status；query.pageNo；query.pageSize |
| ZsjosRegistrationChecklistConfig | [index.vue](../../frontend/admin/src/views/zsjos/registrationChecklistConfig/index.vue) | 未发现统一目录入口（不等于无普通筛选） | — | — | 无显式线索；查看源码/复用组件 |
| salesOrderApproval/index | [index.vue](../../frontend/admin/src/views/zsjos/salesOrderApproval/index.vue) | 未发现统一目录入口（不等于无普通筛选） | — | — | 无显式线索；查看源码/复用组件 |
| ZsjosSalesOrderSupervisorConfirmation | [index.vue](../../frontend/admin/src/views/zsjos/salesOrderSupervisorConfirmation/index.vue) | 统一目录入口 | order | sales_order_supervisor_confirm | 无显式线索；查看源码/复用组件 |
| ZsjosBusinessFormConfig | [index.vue](../../frontend/admin/src/views/zsjos/studentContactConfig/index.vue) | 未发现统一目录入口（不等于无普通筛选） | — | — | 无显式线索；查看源码/复用组件 |
| ZsjosStudentContactExceptions | [index.vue](../../frontend/admin/src/views/zsjos/studentContactExceptions/index.vue) | 未发现统一目录入口（不等于无普通筛选） | — | — | 无显式线索；查看源码/复用组件 |
| studentDelivery/index | [index.vue](../../frontend/admin/src/views/zsjos/studentDelivery/index.vue) | 未发现统一目录入口（不等于无普通筛选） | — | — | 无显式线索；查看源码/复用组件 |
| ZsjosStudentInfoFormConfig | [index.vue](../../frontend/admin/src/views/zsjos/studentInfoFormConfig/index.vue) | 未发现统一目录入口（不等于无普通筛选） | — | — | 无显式线索；查看源码/复用组件 |
| ZsjosSubordinatePartner | [index.vue](../../frontend/admin/src/views/zsjos/subordinatePartner/index.vue) | 未发现统一目录入口（不等于无普通筛选） | — | — | 搜索兼职姓名或编号 |
| subordinateSales/index | [index.vue](../../frontend/admin/src/views/zsjos/subordinateSales/index.vue) | 统一目录入口 | subordinate_sales | subordinate_sales | 无显式线索；查看源码/复用组件 |
| todayTask/index | [index.vue](../../frontend/admin/src/views/zsjos/todayTask/index.vue) | 未发现统一目录入口（不等于无普通筛选） | — | — | 无显式线索；查看源码/复用组件 |
| ZsjosUserRelationScene | [index.vue](../../frontend/admin/src/views/zsjos/userRelation/index.vue) | 未发现统一目录入口（不等于无普通筛选） | — | — | 全部；queryParams.name；queryParams.code；queryParams.status；queryParams.pageNo；queryParams.pageSize |
| ZsjosWithdrawal | [index.vue](../../frontend/admin/src/views/zsjos/withdrawal/index.vue) | 未发现统一目录入口（不等于无普通筛选） | — | — | query.status；query.pageNo；query.pageSize |
| ZsjosWorkOrderAudit | [index.vue](../../frontend/admin/src/views/zsjos/workOrderAudit/index.vue) | 未发现统一目录入口（不等于无普通筛选） | — | — | query.status；query.pageNo；query.pageSize |
| ZsjosWorkOrderTemplate | [index.vue](../../frontend/admin/src/views/zsjos/workOrderTemplate/index.vue) | 未发现统一目录入口（不等于无普通筛选） | — | — | query.pageNo；query.pageSize |
| ZsjosWorkPlan | [index.vue](../../frontend/admin/src/views/zsjos/workPlan/index.vue) | 未发现统一目录入口（不等于无普通筛选） | — | — | query.templateId；query.periodType；query.status；query.ownerUserId；query.ownerDeptId；query.pageNo；query.pageSize |
| ZsjosWorkPlanConfig | [index.vue](../../frontend/admin/src/views/zsjos/workPlanConfig/index.vue) | 未发现统一目录入口（不等于无普通筛选） | — | — | 全部计划类型 |

## Partner H5

扫描源文件 36 个。

| 源文件中的页面/组件 | 证据 | 接入状态 | scene | pageKey | 普通入口线索 |
|---|---|---|---|---|---|
| eam/asset | [asset.vue](../../frontend/h5/src/pages/eam/asset.vue) | 未发现统一目录入口（不等于无普通筛选） | — | — | 无显式线索；查看源码/复用组件 |
| Earnings | [index.vue](../../frontend/h5/src/pages/earnings/index.vue) | 未发现统一目录入口（不等于无普通筛选） | — | — | 无显式线索；查看源码/复用组件 |
| FeedbackCreate | [create.vue](../../frontend/h5/src/pages/feedback/create.vue) | 未发现统一目录入口（不等于无普通筛选） | — | — | 无显式线索；查看源码/复用组件 |
| FeedbackDetail | [detail.vue](../../frontend/h5/src/pages/feedback/detail.vue) | 未发现统一目录入口（不等于无普通筛选） | — | — | 无显式线索；查看源码/复用组件 |
| FeedbackList | [index.vue](../../frontend/h5/src/pages/feedback/index.vue) | 未发现统一目录入口（不等于无普通筛选） | — | — | 搜索反馈编号或标题 |
| Home | [index.vue](../../frontend/h5/src/pages/home/index.vue) | 未发现统一目录入口（不等于无普通筛选） | — | — | 无显式线索；查看源码/复用组件 |
| LeadAppeal | [appeal.vue](../../frontend/h5/src/pages/lead/appeal.vue) | 未发现统一目录入口（不等于无普通筛选） | — | — | 无显式线索；查看源码/复用组件 |
| LeadComplaint | [complaint.vue](../../frontend/h5/src/pages/lead/complaint.vue) | 未发现统一目录入口（不等于无普通筛选） | — | — | 无显式线索；查看源码/复用组件 |
| ComplaintHistory | [complaints.vue](../../frontend/h5/src/pages/lead/complaints.vue) | 未发现统一目录入口（不等于无普通筛选） | — | — | 无显式线索；查看源码/复用组件 |
| LeadDetail | [detail.vue](../../frontend/h5/src/pages/lead/detail.vue) | 未发现统一目录入口（不等于无普通筛选） | — | — | 无显式线索；查看源码/复用组件 |
| LeadFollowUp | [follow-up.vue](../../frontend/h5/src/pages/lead/follow-up.vue) | 未发现统一目录入口（不等于无普通筛选） | — | — | 无显式线索；查看源码/复用组件 |
| LeadList | [list.vue](../../frontend/h5/src/pages/lead/list.vue) | 独立PARTNER筛选（非ADMIN目录） | — | — | 搜索姓名、手机号或客资编号；关闭筛选；simpleStatus；assignmentStatus；sourceChannel；leadCategory；mainProductRef；appealStatus；orderReviewStatus；submittedAt |
| LeadSubmit | [submit.vue](../../frontend/h5/src/pages/lead/submit.vue) | 未发现统一目录入口（不等于无普通筛选） | — | — | 无显式线索；查看源码/复用组件 |
| lead/supplement | [supplement.vue](../../frontend/h5/src/pages/lead/supplement.vue) | 未发现统一目录入口（不等于无普通筛选） | — | — | 无显式线索；查看源码/复用组件 |
| Leaderboard | [index.vue](../../frontend/h5/src/pages/leaderboard/index.vue) | 未发现统一目录入口（不等于无普通筛选） | — | — | 无显式线索；查看源码/复用组件 |
| login/index | [index.vue](../../frontend/h5/src/pages/login/index.vue) | 未发现统一目录入口（不等于无普通筛选） | — | — | 无显式线索；查看源码/复用组件 |
| MessageDetail | [detail.vue](../../frontend/h5/src/pages/messages/detail.vue) | 未发现统一目录入口（不等于无普通筛选） | — | — | 无显式线索；查看源码/复用组件 |
| Messages | [index.vue](../../frontend/h5/src/pages/messages/index.vue) | 未发现统一目录入口（不等于无普通筛选） | — | — | 全部已读 |
| payment/index | [index.vue](../../frontend/h5/src/pages/payment/index.vue) | 未发现统一目录入口（不等于无普通筛选） | — | — | 无显式线索；查看源码/复用组件 |
| payment/result | [result.vue](../../frontend/h5/src/pages/payment/result.vue) | 未发现统一目录入口（不等于无普通筛选） | — | — | 无显式线索；查看源码/复用组件 |
| positioning/confirmation | [confirmation.vue](../../frontend/h5/src/pages/positioning/confirmation.vue) | 未发现统一目录入口（不等于无普通筛选） | — | — | 无显式线索；查看源码/复用组件 |
| positioning/ConfirmationFile | [ConfirmationFile.vue](../../frontend/h5/src/pages/positioning/ConfirmationFile.vue) | 未发现统一目录入口（不等于无普通筛选） | — | — | 无显式线索；查看源码/复用组件 |
| positioning/ConfirmationMaterial | [ConfirmationMaterial.vue](../../frontend/h5/src/pages/positioning/ConfirmationMaterial.vue) | 未发现统一目录入口（不等于无普通筛选） | — | — | 无显式线索；查看源码/复用组件 |
| BankCardEdit | [bank-card-edit.vue](../../frontend/h5/src/pages/profile/bank-card-edit.vue) | 未发现统一目录入口（不等于无普通筛选） | — | — | 无显式线索；查看源码/复用组件 |
| BankCards | [bank-cards.vue](../../frontend/h5/src/pages/profile/bank-cards.vue) | 未发现统一目录入口（不等于无普通筛选） | — | — | 无显式线索；查看源码/复用组件 |
| ProfileEdit | [edit.vue](../../frontend/h5/src/pages/profile/edit.vue) | 未发现统一目录入口（不等于无普通筛选） | — | — | 无显式线索；查看源码/复用组件 |
| Profile | [index.vue](../../frontend/h5/src/pages/profile/index.vue) | 未发现统一目录入口（不等于无普通筛选） | — | — | 无显式线索；查看源码/复用组件 |
| ProfilePassword | [password.vue](../../frontend/h5/src/pages/profile/password.vue) | 未发现统一目录入口（不等于无普通筛选） | — | — | 无显式线索；查看源码/复用组件 |
| ThemeSwitch | [theme.vue](../../frontend/h5/src/pages/profile/theme.vue) | 未发现统一目录入口（不等于无普通筛选） | — | — | 无显式线索；查看源码/复用组件 |
| VersionUpdate | [version-update.vue](../../frontend/h5/src/pages/profile/version-update.vue) | 未发现统一目录入口（不等于无普通筛选） | — | — | 无显式线索；查看源码/复用组件 |
| studentInfo/index | [index.vue](../../frontend/h5/src/pages/studentInfo/index.vue) | 未发现统一目录入口（不等于无普通筛选） | — | — | 无显式线索；查看源码/复用组件 |
| Unauthorized | [index.vue](../../frontend/h5/src/pages/unauthorized/index.vue) | 未发现统一目录入口（不等于无普通筛选） | — | — | 无显式线索；查看源码/复用组件 |
| WecomClick | [click.vue](../../frontend/h5/src/pages/wecom/click.vue) | 未发现统一目录入口（不等于无普通筛选） | — | — | 无显式线索；查看源码/复用组件 |
| WithdrawalApply | [apply.vue](../../frontend/h5/src/pages/withdrawal/apply.vue) | 未发现统一目录入口（不等于无普通筛选） | — | — | 无显式线索；查看源码/复用组件 |
| WithdrawalDetail | [detail.vue](../../frontend/h5/src/pages/withdrawal/detail.vue) | 未发现统一目录入口（不等于无普通筛选） | — | — | 无显式线索；查看源码/复用组件 |
| Withdrawal | [index.vue](../../frontend/h5/src/pages/withdrawal/index.vue) | 未发现统一目录入口（不等于无普通筛选） | — | — | 无显式线索；查看源码/复用组件 |

## 后端普通列表请求参数总表

穷举ZSJOS admin Controller树中的*PageReqVO.java，逐个记录该文件直接声明的属性；分页继承、类继承字段、方法参数和非Page命名请求不在自动提取范围。此表只证明VO声明，不证明每个参数已传到Mapper，核心财务链路已在主矩阵区分。可作为未接统一目录页面的后续逐项查询清单。

| 请求VO | 父类 | 直接声明参数 | 证据 |
|---|---|---|---|
| MediaAccountCalendarPageReqVO | PageParam | rangeStart, rangeEnd, keyword, currentStatusValue, stageValue, directorUserId, operatorUserId | [MediaAccountCalendarPageReqVO.java](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/account/vo/MediaAccountCalendarPageReqVO.java) |
| MediaAccountPageReqVO | PageParam | keyword, sStage | [MediaAccountPageReqVO.java](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/account/vo/MediaAccountPageReqVO.java) |
| BusinessAuditPageReqVO | PageParam | categoryCode, actionCode, targetType, sourceType, resultStatus, operatorUserId, initiatorUserId, executorType, executorIdentity, parentAuditId, executionKey | [BusinessAuditPageReqVO.java](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/audit/vo/BusinessAuditPageReqVO.java) |
| CashbackPageReqVO | PageParam | type, status, keyword, beneficiaryUserId, partnerId, amountMin, amountMax, advancedFilter, orderNo, productName, generatedAtFrom, generatedAtTo, availableAtFrom, availableAtTo, settledAtFrom, settledAtTo | [CashbackPageReqVO.java](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/cashback/vo/CashbackPageReqVO.java) |
| ContentPageReqVO | PageParam | status, keyword | [ContentPageReqVO.java](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/content/vo/ContentPageReqVO.java) |
| ContentReviewBatchPageReqVO | PageParam | keyword, status, accountId, mine, statuses, operatorUserId, directorUserId, platformValue, submittedFrom, submittedTo | [ContentReviewBatchPageReqVO.java](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/contentreview/vo/ContentReviewBatchPageReqVO.java) |
| ContentReviewCandidatePageReqVO | PageParam | keyword | [ContentReviewCandidatePageReqVO.java](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/contentreview/vo/ContentReviewCandidatePageReqVO.java) |
| CourseCalendarPageReqVO | 无直接继承 | rangeStart, rangeEnd | [CourseCalendarPageReqVO.java](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/coursecalendar/vo/CourseCalendarPageReqVO.java) |
| ClassTransferPageReqVO | PageParam | status | [ClassTransferPageReqVO.java](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/deliveryclass/vo/ClassTransferPageReqVO.java) |
| DeliveryClassPageReqVO | PageParam | readScope, targetUserId, status, keyword, categoryId, examScheduleId, homeroomUserId | [DeliveryClassPageReqVO.java](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/deliveryclass/vo/DeliveryClassPageReqVO.java) |
| ExamSchedulePageReqVO | PageParam | rangeStart, rangeEnd, categoryId, displayStatus | [ExamSchedulePageReqVO.java](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/examcalendar/vo/ExamSchedulePageReqVO.java) |
| FeedbackPageReqVO | PageParam | readScope, targetUserId, feedbackType, status, assigneeUserId, keyword | [FeedbackPageReqVO.java](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/feedback/vo/FeedbackPageReqVO.java) |
| ForcedFormPageReqVO | PageParam | name, status | [ForcedFormPageReqVO.java](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/forcedform/vo/ForcedFormPageReqVO.java) |
| ForcedFormSubmissionPageReqVO | PageParam | formId, userId, platform | [ForcedFormSubmissionPageReqVO.java](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/forcedform/vo/ForcedFormSubmissionPageReqVO.java) |
| GiftPurchasePageReqVO | PageParam | studentName, orderNo, giftKeyword | [GiftPurchasePageReqVO.java](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/giftpurchase/vo/GiftPurchasePageReqVO.java) |
| LeadAgingPoolPageReqVO | PageParam | keyword, status, inboxGroup, inboxStage, relationScope, advancedFilter | [LeadAgingPoolPageReqVO.java](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/lead/vo/agingpool/LeadAgingPoolPageReqVO.java) |
| LeadAppealPageReqVO | PageParam | handled, cursor, limit, keyword, advancedFilter | [LeadAppealPageReqVO.java](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/lead/vo/appeal/LeadAppealPageReqVO.java) |
| LeadAssignmentLogPageReqVO | PageParam | scene, actionType | [LeadAssignmentLogPageReqVO.java](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/lead/vo/assignment/LeadAssignmentLogPageReqVO.java) |
| LeadAssignmentRelationPageReqVO | PageParam | keyword, deptId, configured | [LeadAssignmentRelationPageReqVO.java](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/lead/vo/assignment/LeadAssignmentRelationPageReqVO.java) |
| LeadComplaintPageReqVO | PageParam | status | [LeadComplaintPageReqVO.java](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/lead/vo/complaint/LeadComplaintPageReqVO.java) |
| LeadClaimPoolPageReqVO | PageParam | keyword, advancedFilter | [LeadClaimPoolPageReqVO.java](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/lead/vo/dispatch/LeadClaimPoolPageReqVO.java) |
| LeadDuplicateReviewPageReqVO | PageParam | status, keyword, advancedFilter | [LeadDuplicateReviewPageReqVO.java](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/lead/vo/duplicate/LeadDuplicateReviewPageReqVO.java) |
| LeadManagementPageReqVO | PageParam | keyword, status, assignmentStatus, audience, relationScope, simpleStatus, sortField, sortOrder, view, inboxGroup, inboxStage, sourceChannel, leadCategory, sourceUserId, providerOwnerType, providerOwnerId, ownerUserId, advancedFilter, cursor, limit, cursorActivityAt, cursorId | [LeadManagementPageReqVO.java](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/lead/vo/management/LeadManagementPageReqVO.java) |
| LeadQualificationExceptionPageReqVO | PageParam | type, keyword, advancedFilter | [LeadQualificationExceptionPageReqVO.java](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/lead/vo/qualification/LeadQualificationExceptionPageReqVO.java) |
| SubordinateSalesPageReqVO | PageParam | keyword, accountStatus, presence, accepting, advancedFilter | [SubordinateSalesPageReqVO.java](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/lead/vo/subordinate/SubordinateSalesPageReqVO.java) |
| SubordinateTaskPageReqVO | PageParam | bucket | [SubordinateTaskPageReqVO.java](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/lead/vo/subordinate/SubordinateTaskPageReqVO.java) |
| MaterialApprovalPageReqVO | PageParam | typeCode, done | [MaterialApprovalPageReqVO.java](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/material/vo/MaterialApprovalPageReqVO.java) |
| MaterialPageReqVO | PageParam | keyword, materialTypeId, accountId, status, source, mine, favorite, recommendation, accountType, profession, accountStage, platform | [MaterialPageReqVO.java](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/material/vo/MaterialPageReqVO.java) |
| MaterialReferenceTargetPageReqVO | PageParam | keyword | [MaterialReferenceTargetPageReqVO.java](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/material/vo/MaterialReferenceTargetPageReqVO.java) |
| MaterialImportPageReqVO | PageParam | materialTypeId, status | [MaterialImportPageReqVO.java](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/materialimport/vo/MaterialImportPageReqVO.java) |
| SalesOrderMyPageReqVO | PageParam | status, keyword, advancedFilter | [SalesOrderMyPageReqVO.java](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/order/vo/SalesOrderMyPageReqVO.java) |
| SalesOrderPageReqVO | PageParam | center, handled, groupKey, optionKey, keyword, advancedFilter, cursor, limit, cursorTaskTime, cursorTaskId | [SalesOrderPageReqVO.java](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/order/vo/SalesOrderPageReqVO.java) |
| SalesOrderSupervisorPageReqVO | PageParam | handled, keyword, advancedFilter | [SalesOrderSupervisorPageReqVO.java](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/order/vo/SalesOrderSupervisorPageReqVO.java) |
| SalesOrderTeamPageReqVO | SalesOrderMyPageReqVO | 无直接声明；查看父类 | [SalesOrderTeamPageReqVO.java](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/order/vo/SalesOrderTeamPageReqVO.java) |
| PaymentSubjectPageReqVO | PageParam | subjectName, status | [PaymentSubjectPageReqVO.java](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/payment/vo/PaymentSubjectPageReqVO.java) |
| ProductPaymentSubjectPageReqVO | PageParam | productName, paymentSubjectId | [ProductPaymentSubjectPageReqVO.java](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/payment/vo/ProductPaymentSubjectPageReqVO.java) |
| PartnerInvitationPageReqVO | PageParam | keyword, status, assignedOperatorUserId | [PartnerInvitationPageReqVO.java](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/personnel/vo/PartnerInvitationPageReqVO.java) |
| SubordinatePartnerPageReqVO | PageParam | keyword, status | [SubordinatePartnerPageReqVO.java](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/personnel/vo/SubordinatePartnerPageReqVO.java) |
| PositioningCardPageReqVO | PageParam | status | [PositioningCardPageReqVO.java](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/positioning/vo/PositioningCardPageReqVO.java) |
| ZsjosProductPageReqVO | PageParam | name, productRef, status, categoryId | [ZsjosProductPageReqVO.java](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/product/vo/ZsjosProductPageReqVO.java) |
| ProductionTicketPageReqVO | PageParam | status, keyword, pendingAssignment, statusGroup, deadlineFrom, deadlineTo | [ProductionTicketPageReqVO.java](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/production/vo/ProductionTicketPageReqVO.java) |
| MyStudentPageReqVO | PageParam | readScope, targetUserId, keyword, serviceStatus, classId, advancedFilter | [MyStudentPageReqVO.java](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/registration/vo/MyStudentPageReqVO.java) |
| RegistrationPoolPageReqVO | PageParam | status, keyword, advancedFilter | [RegistrationPoolPageReqVO.java](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/registration/vo/RegistrationPoolPageReqVO.java) |
| BusinessTaskPageReqVO | PageParam | readScope, targetUserId, status, bucket | [BusinessTaskPageReqVO.java](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/task/vo/BusinessTaskPageReqVO.java) |
| UserRelationPageReqVO | PageParam | sceneCode, keyword, deptId, configured | [UserRelationPageReqVO.java](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/userrelation/vo/relation/UserRelationPageReqVO.java) |
| UserRelationScenePageReqVO | PageParam | name, code, status | [UserRelationScenePageReqVO.java](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/userrelation/vo/scene/UserRelationScenePageReqVO.java) |
| WithdrawalPageReqVO | PageParam | readScope, targetUserId, status, keyword, applicantUserId, partnerId, amountMin, amountMax, advancedFilter, withdrawalNo, bankTransactionNo, submittedAtFrom, submittedAtTo, reviewedAtFrom, reviewedAtTo, paidAtFrom, paidAtTo | [WithdrawalPageReqVO.java](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/withdrawal/vo/WithdrawalPageReqVO.java) |
| WorkOrderCandidatePageReqVO | PageParam | sceneCode, keyword | [WorkOrderCandidatePageReqVO.java](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/workorder/vo/WorkOrderCandidatePageReqVO.java) |
| WorkOrderMyPageReqVO | PageParam | readScope, targetUserId, status, view | [WorkOrderMyPageReqVO.java](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/workorder/vo/WorkOrderMyPageReqVO.java) |
| WorkOrderPoolPageReqVO | PageParam | sceneCode | [WorkOrderPoolPageReqVO.java](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/workorder/vo/WorkOrderPoolPageReqVO.java) |
| WorkOrderScenePageReqVO | PageParam | 无直接声明；查看父类 | [WorkOrderScenePageReqVO.java](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/workorder/vo/WorkOrderScenePageReqVO.java) |
| WorkPlanPageReqVO | PageParam | periodType, status, templateId, ownerUserId, ownerDeptId, startDate, endDate | [WorkPlanPageReqVO.java](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/workplan/vo/WorkPlanPageReqVO.java) |

## 核对摘要

| 运行面 | 扫描文件 | 统一目录入口源文件 |
|---|---|---|
| Workbench | 49 | 10 |
| Admin ZSJOS | 63 | 10 |
| Partner H5 | 36 | 0 |

后端列表请求VO共扫描 52 个。一个源文件可承载多页/多视图，桌面和移动端重复控件不重复算页面；这不是正式菜单数量。

## 2026-09-23 接入增量

Admin 返现、提现和成交审批页面已接入统一高级筛选组件；Workbench 返现场景修正为 cashback。财务 POST search-page 现实际编译高级条件并参与分页，详见[查询修正记录](advanced-filter-inventory.md#9-已落实页面场景与后端查询契约2026-09-23)。以上为源码状态，不代表已完成已登录浏览器验收。
