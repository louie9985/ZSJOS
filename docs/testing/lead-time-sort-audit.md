# 客资时间排序与页面扫描（2026-10-29）

## 客资变更
客资主记录的全部 20 个时间字段（含创建/更新审计时间）支持排序。新增后端白名单及映射：createTime、ownershipStartedAt、lastFollowUpAt、noProgressWarnedAt、pendingExpiresAt。其余补充已有协议选项。保持默认排序及同值 id 降序规则，显式排序仍先全量排序再分页。
Vue Admin 原请求协议不变；新增字段为可选扩展，未修改管理端页面。

## 其他页面只读扫描
订单和学员列表由列配置生成排序选项；自媒体学员有显式排序字段列表。未将这些页面视为已完整覆盖所有业务时间。
以下为工作台及管理端逐行扫描到的时间列线索（不是缺陷判定；包装组件可能补充排序，需结合接口逐项核实）。

- `frontend/workbench/src/pages/AnnouncementCenterPage.tsx:229`：高亮截止时间；本行未声明排序（待追踪包装/接口）
- `frontend/workbench/src/pages/AnnouncementCenterPage.tsx:233`：发布时间；本行未声明排序（待追踪包装/接口）
- `frontend/workbench/src/pages/AnnouncementCenterPage.tsx:235`：阅读时间；本行未声明排序（待追踪包装/接口）
- `frontend/workbench/src/pages/BpmApprovalCenterPage.tsx:503`：任务到达时间；本行未声明排序（待追踪包装/接口）
- `frontend/workbench/src/pages/BpmApprovalCenterPage.tsx:504`：任务完成时间；本行未声明排序（待追踪包装/接口）
- `frontend/workbench/src/pages/ConfigurationPages.tsx:73`：发布时间；本行未声明排序（待追踪包装/接口）
- `frontend/workbench/src/pages/ConfigurationPages.tsx:96`：更新时间；本行未声明排序（待追踪包装/接口）
- `frontend/workbench/src/pages/DeliveryPerformancePage.tsx:73`：截止时间；本行未声明排序（待追踪包装/接口）
- `frontend/workbench/src/pages/DeliveryPerformancePage.tsx:79`：接收时间；本行未声明排序（待追踪包装/接口）
- `frontend/workbench/src/pages/DeliveryPerformancePage.tsx:81`：归属时间；本行未声明排序（待追踪包装/接口）
- `frontend/workbench/src/pages/financeTableColumns.tsx:38`：生成时间；本行未声明排序（待追踪包装/接口）
- `frontend/workbench/src/pages/financeTableColumns.tsx:39`：可用时间；本行未声明排序（待追踪包装/接口）
- `frontend/workbench/src/pages/LeadAppealPage.tsx:248`：提交时间；本行未声明排序（待追踪包装/接口）
- `frontend/workbench/src/pages/LeadAppealPage.tsx:249`：处理时间；本行未声明排序（待追踪包装/接口）
- `frontend/workbench/src/pages/LeadAssignmentPage.tsx:208`：操作时间；本行未声明排序（待追踪包装/接口）
- `frontend/workbench/src/pages/LeadComplaintPage.tsx:110`：提交时间；本行未声明排序（待追踪包装/接口）
- `frontend/workbench/src/pages/LeadDuplicateReviewPage.tsx:217`：创建时间；本行未声明排序（待追踪包装/接口）
- `frontend/workbench/src/pages/LeadDuplicateReviewPage.tsx:218`：复核时间；本行未声明排序（待追踪包装/接口）
- `frontend/workbench/src/pages/LeadManagementPage.tsx:607`：进入公海时间；本行未声明排序（待追踪包装/接口）
- `frontend/workbench/src/pages/LeadManagementPage.tsx:608`：计入业绩时间；本行未声明排序（待追踪包装/接口）
- `frontend/workbench/src/pages/LeadManagementPage.tsx:609`：首次跟进时间；本行未声明排序（待追踪包装/接口）
- `frontend/workbench/src/pages/LeadManagementPage.tsx:610`：首跟截止时间；本行未声明排序（待追踪包装/接口）
- `frontend/workbench/src/pages/LeadManagementPage.tsx:611`：判定开始时间；本行未声明排序（待追踪包装/接口）
- `frontend/workbench/src/pages/LeadManagementPage.tsx:612`：判定截止时间；本行未声明排序（待追踪包装/接口）
- `frontend/workbench/src/pages/LeadManagementPage.tsx:613`：挂起时间；本行未声明排序（待追踪包装/接口）
- `frontend/workbench/src/pages/LeadManagementPage.tsx:616`：订单提交时间；本行未声明排序（待追踪包装/接口）
- `frontend/workbench/src/pages/LeadManagementPage.tsx:619`：申诉截止时间；本行未声明排序（待追踪包装/接口）
- `frontend/workbench/src/pages/LeadManagementPage.tsx:620`：关闭时间；本行未声明排序（待追踪包装/接口）
- `frontend/workbench/src/pages/LeadManagementPage.tsx:623`：下次跟进时间；本行未声明排序（待追踪包装/接口）
- `frontend/workbench/src/pages/LeadManagementPage.tsx:624`：提交时间；本行未声明排序（待追踪包装/接口）
- `frontend/workbench/src/pages/LeadManagementPage.tsx:625`：最近活动时间；本行未声明排序（待追踪包装/接口）
- `frontend/workbench/src/pages/LeadManagementPage.tsx:626`：判定时间；本行未声明排序（待追踪包装/接口）
- `frontend/workbench/src/pages/LeadManagementPage.tsx:627`：成交转化时间；本行未声明排序（待追踪包装/接口）
- `frontend/workbench/src/pages/LeadManagementPage.tsx:629`：更新时间；本行未声明排序（待追踪包装/接口）
- `frontend/workbench/src/pages/ManagementPages.tsx:98`：时间；本行未声明排序（待追踪包装/接口）
- `frontend/workbench/src/pages/ManagementPages.tsx:99`：时间；本行未声明排序（待追踪包装/接口）
- `frontend/workbench/src/pages/ManagementPages.tsx:242`：提交时间、审核时间；本行未声明排序（待追踪包装/接口）
- `frontend/workbench/src/pages/ManagementPages.tsx:297`：时间；本行未声明排序（待追踪包装/接口）
- `frontend/workbench/src/pages/MediaLeadAnalysisPage.tsx:112`：提交时间、首购生效时间；本行未声明排序（待追踪包装/接口）
- `frontend/workbench/src/pages/MessageInboxPage.tsx:268`：时间；本行未声明排序（待追踪包装/接口）
- `frontend/workbench/src/pages/MessageInboxPage.tsx:276`：阅读时间；本行未声明排序（待追踪包装/接口）
- `frontend/workbench/src/pages/NoticeManagementPage.tsx:124`：发布时间；本行未声明排序（待追踪包装/接口）
- `frontend/workbench/src/pages/NoticeManagementPage.tsx:125`：高亮截止时间；本行未声明排序（待追踪包装/接口）
- `frontend/workbench/src/pages/SalesPerformancePage.tsx:73`：截止时间；本行未声明排序（待追踪包装/接口）
- `frontend/workbench/src/pages/SalesPerformancePage.tsx:79`：接收时间；本行未声明排序（待追踪包装/接口）
- `frontend/workbench/src/pages/SalesPerformancePage.tsx:81`：归属时间；本行未声明排序（待追踪包装/接口）
- `frontend/workbench/src/pages/SubordinatePartnerPage.tsx:156`：归属时间；本行未声明排序（待追踪包装/接口）
- `frontend/workbench/src/pages/SubordinatePartnerPage.tsx:232`：时间；本行未声明排序（待追踪包装/接口）
- `frontend/workbench/src/pages/educationTeam/TeamMemberDetail.tsx:25`：任务时间范围、到期时间；本行未声明排序（待追踪包装/接口）
- `frontend/workbench/src/pages/performance/MediaLeadTargetHistory.tsx:47`：修改时间；本行未声明排序（待追踪包装/接口）
- `frontend/admin/src/views/zsjos/class-management.vue:168`：考期日期；本行未声明排序（待追踪包装/接口）
- `frontend/admin/src/views/zsjos/my-students.vue:72`：服务激活时间；本行未声明排序（待追踪包装/接口）
- `frontend/admin/src/views/zsjos/my-students.vue:155`：生效时间；本行未声明排序（待追踪包装/接口）
- `frontend/admin/src/views/zsjos/businessAudit/index.vue:66`：时间；本行未声明排序（待追踪包装/接口）
- `frontend/admin/src/views/zsjos/businessAudit/index.vue:94`：时间；本行未声明排序（待追踪包装/接口）
- `frontend/admin/src/views/zsjos/cashback/index.vue:51`：生成时间；本行未声明排序（待追踪包装/接口）
- `frontend/admin/src/views/zsjos/cashback/index.vue:52`：可提现时间；本行未声明排序（待追踪包装/接口）
- `frontend/admin/src/views/zsjos/components/CashbackControlHistory.vue:4`：时间；本行未声明排序（待追踪包装/接口）
- `frontend/admin/src/views/zsjos/components/ExternalRepurchaseDialog.vue:78`：缴费时间；本行未声明排序（待追踪包装/接口）
- `frontend/admin/src/views/zsjos/directorTemplate/index.vue:217`：发布时间；本行未声明排序（待追踪包装/接口）
- `frontend/admin/src/views/zsjos/exportTask/index.vue:37`：创建时间；本行未声明排序（待追踪包装/接口）
- `frontend/admin/src/views/zsjos/forcedForm/index.vue:97`：提交时间；本行未声明排序（待追踪包装/接口）
- `frontend/admin/src/views/zsjos/lead/index.vue:59`：提交时间；本行未声明排序（待追踪包装/接口）
- `frontend/admin/src/views/zsjos/material/index.vue:79`：更新时间；本行未声明排序（待追踪包装/接口）
- `frontend/admin/src/views/zsjos/materialImport/index.vue:76`：预检时间；本行未声明排序（待追踪包装/接口）
- `frontend/admin/src/views/zsjos/materialImport/index.vue:77`：确认时间；本行未声明排序（待追踪包装/接口）
- `frontend/admin/src/views/zsjos/materialType/index.vue:133`：发布时间；本行未声明排序（待追踪包装/接口）
- `frontend/admin/src/views/zsjos/partner/index.vue:332`：时间；本行未声明排序（待追踪包装/接口）
- `frontend/admin/src/views/zsjos/subordinateSales/index.vue:45`：更新时间；本行未声明排序（待追踪包装/接口）
- `frontend/admin/src/views/zsjos/withdrawal/index.vue:68`：申请时间；本行未声明排序（待追踪包装/接口）

## 验证
全部时间字段 UI/白名单/Mapper 映射静态校验通过。真实 Chrome fixture 在 1440/375 宽度验证新增创建时间可选、方向说明和无横向溢出通过。未验证真实账号接口和数据库排序结果；后端编译/运行测试尚未执行。

确认缺项：导出任务创建时间列没有排序入口，exportTaskPage 请求只传页码及页大小；引流业绩客资明细的提交时间、首购生效时间列没有排序回调。其他候选需进一步接口核查。
Workbench typecheck 最终通过。
