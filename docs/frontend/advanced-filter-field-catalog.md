# 中视简高级筛选现有字段逐项目录

2026-09-23 当前工作树静态快照（字段目录拆分后已更新注册位置）；与[主矩阵](advanced-filter-inventory.md)、[页面覆盖](advanced-filter-page-coverage.md)配套。源码锚点记录采集时行号；本地阅读器不支持行号锚点时可按 fieldKey 搜索。

## 1. 场景字段数量

不包含关键词、普通查询参数、分页、排序和模板pageKey。人员选项受服务端可见范围裁剪；返现、提现已注册独立场景。

| scene | 静态字段 | 运行时附加时间作差 | 目录能力上限 |
|---|---|---|---|
| lead | 79 | 1 | 80 |
| order | 71 | 1 | 72 |
| lead_appeal | 79 | 1 | 80 |
| duplicate_review | 17 | 1 | 18 |
| registration | 78 | 1 | 79 |
| student | 76 | 1 | 77 |
| subordinate_sales | 26 | 0 | 26 |
| cashback | 15 | 1 | 16 |
| withdrawal | 17 | 1 | 18 |

去重静态 fieldKey 共 **184** 个。`duration.diff`由catalog在至少两个日期字段时动态添加；不是重复登记的业务字段。

## 2. 操作符定义

- TEXT：contains、not_contains、eq、ne、is_empty、is_not_empty。
- SELECT：in、not_in、is_empty、is_not_empty；ownerDeptId仅in/not_in。
- RANGE：eq、gt、gte、lt、lte、between、is_empty、is_not_empty。
- DATE：RANGE加relative。
- duration.diff：gt、gte、lt、lte、between，时间单位minute/hour/day；起止字段必须可兼容关联。

固定选项包含业务状态机、技术布尔与附件有无；这里记录实现来源，不将其统一判定为字典违规。dict来源须再检查实际租户字典，visible-users/visible-departments须由后端解析授权选项。

## 3. 全量字段注册矩阵

| fieldKey | 分组 | 名称 | 类型 | 操作符 | 选项来源 | 绑定scene | 证据 |
|---|---|---|---|---|---|---|---|
| person.name | 身份与联系 | 姓名 | text | TEXT | 无下拉选项 | lead, order, lead_appeal, registration, student | [注册](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/CommonFilterFields.java#L12) |
| person.mobile | 身份与联系 | 手机号 | text | TEXT | 无下拉选项 | lead, order, lead_appeal, registration, student | [注册](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/CommonFilterFields.java#L13) |
| person.wechatId | 身份与联系 | 微信号 | text | TEXT | 无下拉选项 | lead, order, lead_appeal, registration, student | [注册](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/CommonFilterFields.java#L14) |
| person.identityStatus | 状态与进度 | 身份状态 | select | SELECT | 业务/技术固定选项（见源码） | lead, order, student | [注册](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/CommonFilterFields.java#L15) |
| lead.leadNo | 身份与联系 | 客资编号 | text | TEXT | 无下拉选项 | lead, order, lead_appeal, duplicate_review, registration, student | [注册](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/LeadFilterFields.java#L12) |
| lead.name | 身份与联系 | 提交姓名 | text | TEXT | 无下拉选项 | lead, order, lead_appeal, registration, student | [注册](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/LeadFilterFields.java#L13) |
| lead.mobile | 身份与联系 | 提交手机号 | text | TEXT | 无下拉选项 | lead, order, lead_appeal, registration, student | [注册](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/LeadFilterFields.java#L14) |
| lead.wechatId | 身份与联系 | 提交微信号 | text | TEXT | 无下拉选项 | lead, order, lead_appeal, registration, student | [注册](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/LeadFilterFields.java#L15) |
| lead.status | 状态与进度 | 客资状态 | select | SELECT | 字典：`zsjos_lead_status` | lead, order, lead_appeal, registration, student | [注册](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/LeadFilterFields.java#L16) |
| lead.assignmentStatus | 状态与进度 | 分配状态 | select | SELECT | 字典：`zsjos_lead_assignment_status` | lead, order, lead_appeal, registration, student | [注册](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/LeadFilterFields.java#L17) |
| lead.sourceType | 补充信息 | 客资来源 | select | SELECT | 业务/技术固定选项（见源码） | lead, order, lead_appeal, registration, student | [注册](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/LeadFilterFields.java#L18) |
| lead.ownerIdentity | 补充信息 | 负责人身份 | select | SELECT | 业务/技术固定选项（见源码） | lead, order, lead_appeal, registration, student | [注册](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/LeadFilterFields.java#L19) |
| order.formalOwnerIdentity | 补充信息 | 成交归属身份 | select | SELECT | 业务/技术固定选项（见源码） | lead, order, lead_appeal, registration, student | [注册](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/OrderFilterFields.java#L12) |
| lead.sourceChannel | 补充信息 | 来源渠道 | select | SELECT | dict:zsjos_lead_source_channel | lead, order, lead_appeal, registration, student | [注册](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/LeadFilterFields.java#L20) |
| lead.category | 补充信息 | 客资分类 | select | SELECT | dict:zsjos_lead_category | lead, order, lead_appeal, registration, student | [注册](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/LeadFilterFields.java#L21) |
| lead.salesStage | 状态与进度 | 当前销售阶段 | select | SELECT | dict:zsjos_lead_sales_stage | lead | [注册](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/LeadFilterFields.java#L22) |
| lead.ownerDeptId | 归属与人员 | 负责人所属组织（含下级） | select | in, not_in | visible-departments | lead | [注册](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/LeadFilterFields.java#L23) |
| lead.qualificationStatus | 状态与进度 | 有效性 | select | SELECT | 业务/技术固定选项（见源码） | lead | [注册](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/LeadFilterFields.java#L24) |
| lead.dealStatus | 状态与进度 | 成交状态 | select | SELECT | 业务/技术固定选项（见源码） | lead | [注册](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/LeadFilterFields.java#L25) |
| lead.qualifiedAt | 时间 | 有效性判定时间 | date | DATE | 无下拉选项 | lead | [注册](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/LeadFilterFields.java#L26) |
| lead.convertedAt | 时间 | 成交时间 | date | DATE | 无下拉选项 | lead | [注册](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/LeadFilterFields.java#L27) |
| lead.sourceUserId | 归属与人员 | 提交人 | select | SELECT | visible-users | lead, order, lead_appeal, registration, student | [注册](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/LeadFilterFields.java#L28) |
| lead.ownerUserId | 归属与人员 | 负责人 | select | SELECT | visible-users | lead, order, lead_appeal, registration, student | [注册](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/LeadFilterFields.java#L30) |
| lead.submittedAt | 时间 | 客资提交时间 | date | DATE | 无下拉选项 | lead, order, lead_appeal, registration, student | [注册](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/LeadFilterFields.java#L31) |
| lead.lastFollowUpAt | 时间 | 最近跟进时间 | date | DATE | 无下拉选项 | lead, order, lead_appeal, registration, student | [注册](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/LeadFilterFields.java#L32) |
| lead.nextFollowUpAt | 时间 | 下次跟进时间 | date | DATE | 无下拉选项 | lead, order, lead_appeal, registration, student | [注册](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/LeadFilterFields.java#L33) |
| lead.ownershipStartedAt | 时间 | 持有起点 | date | DATE | 无下拉选项 | lead, order, lead_appeal, registration, student | [注册](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/LeadFilterFields.java#L34) |
| lead.remark | 补充信息 | 客资备注 | text | TEXT | 无下拉选项 | lead, order, lead_appeal, registration, student | [注册](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/LeadFilterFields.java#L35) |
| opportunity.expectedProduct | 产品与服务 | 预计产品 | text | TEXT | 无下拉选项 | lead, order | [注册](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/OpportunityFilterFields.java#L12) |
| opportunity.status | 状态与进度 | 推进状态 | select | SELECT | 业务/技术固定选项（见源码） | lead, order | [注册](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/OpportunityFilterFields.java#L13) |
| opportunity.lostReason | 补充信息 | 流失原因 | text | TEXT | 无下拉选项 | lead, order | [注册](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/OpportunityFilterFields.java#L14) |
| order.orderNo | 身份与联系 | 订单号 | text | TEXT | 无下拉选项 | lead, order, lead_appeal, registration, student | [注册](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/OrderFilterFields.java#L13) |
| order.status | 状态与进度 | 订单状态 | select | SELECT | 业务/技术固定选项（见源码） | lead, order, lead_appeal, registration, student | [注册](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/OrderFilterFields.java#L14) |
| order.type | 状态与进度 | 订单类型 | select | SELECT | 业务/技术固定选项（见源码） | lead, order, lead_appeal, registration, student | [注册](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/OrderFilterFields.java#L15) |
| order.buyerName | 身份与联系 | 购买方 | text | TEXT | 无下拉选项 | lead, order, lead_appeal, registration, student | [注册](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/OrderFilterFields.java#L16) |
| order.studentName | 身份与联系 | 学员姓名 | text | TEXT | 无下拉选项 | lead, order, lead_appeal, registration, student | [注册](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/OrderFilterFields.java#L17) |
| order.studentMobile | 身份与联系 | 学员手机号 | text | TEXT | 无下拉选项 | lead, order, lead_appeal, registration, student | [注册](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/OrderFilterFields.java#L18) |
| order.studentWechatId | 身份与联系 | 学员微信号 | text | TEXT | 无下拉选项 | lead, order, lead_appeal, registration, student | [注册](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/OrderFilterFields.java#L19) |
| order.submitterUserId | 归属与人员 | 订单提交人 | select | SELECT | visible-users | lead, order, lead_appeal, registration, student | [注册](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/OrderFilterFields.java#L20) |
| order.formalSalesUserId | 归属与人员 | 成交负责人 | select | SELECT | visible-users | lead, order, lead_appeal, registration, student | [注册](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/OrderFilterFields.java#L21) |
| order.totalAmount | 金额与付款 | 订单总金额 | number | RANGE | 无下拉选项 | lead, order, lead_appeal, registration, student | [注册](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/OrderFilterFields.java#L22) |
| order.studentNature | 身份与联系 | 学员性质 | select | SELECT | dict:zsjos_order_student_nature | lead, order, lead_appeal, registration, student | [注册](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/OrderFilterFields.java#L23) |
| order.region | 身份与联系 | 所在地区 | text | TEXT | 无下拉选项 | lead, order, lead_appeal, registration, student | [注册](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/OrderFilterFields.java#L24) |
| order.feeMode | 金额与付款 | 缴费方式 | select | SELECT | dict:zsjos_order_fee_mode | lead, order, lead_appeal, registration, student | [注册](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/OrderFilterFields.java#L25) |
| order.paymentMethod | 金额与付款 | 支付方式 | select | SELECT | dict:zsjos_order_payment_method | lead, order, lead_appeal, registration, student | [注册](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/OrderFilterFields.java#L26) |
| order.customerPaidAt | 金额与付款 | 客户付款时间 | date | DATE | 无下拉选项 | lead, order, lead_appeal, registration, student | [注册](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/OrderFilterFields.java#L27) |
| order.classType | 产品与服务 | 开通班种 | text | TEXT | 无下拉选项 | lead, order, lead_appeal, registration, student | [注册](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/OrderFilterFields.java#L28) |
| order.servicePeriod | 产品与服务 | 服务周期 | select | SELECT | dict:zsjos_order_service_period | lead, order, lead_appeal, registration, student | [注册](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/OrderFilterFields.java#L29) |
| order.studentSource | 补充信息 | 学生来源 | select | SELECT | dict:zsjos_order_student_source | lead, order, lead_appeal, registration, student | [注册](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/OrderFilterFields.java#L30) |
| order.submittedAt | 时间 | 订单提交时间 | date | DATE | 无下拉选项 | lead, order, lead_appeal, registration, student | [注册](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/OrderFilterFields.java#L31) |
| order.effectiveAt | 时间 | 订单生效时间 | date | DATE | 无下拉选项 | lead, order, lead_appeal, registration, student | [注册](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/OrderFilterFields.java#L32) |
| order.remark | 补充信息 | 订单备注 | text | TEXT | 无下拉选项 | lead, order, lead_appeal, registration, student | [注册](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/OrderFilterFields.java#L33) |
| order.specialRequirements | 补充信息 | 学员特殊要求 | text | TEXT | 无下拉选项 | lead, order, lead_appeal, registration, student | [注册](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/OrderFilterFields.java#L34) |
| order.materialDelivery | 补充信息 | 教材邮递联系 | text | TEXT | 无下拉选项 | lead, order, lead_appeal, registration, student | [注册](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/OrderFilterFields.java#L35) |
| order.hasVoucher | 补充信息 | 缴费凭证 | select | SELECT | 业务/技术固定选项（见源码） | lead, order, lead_appeal, registration, student | [注册](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/OrderFilterFields.java#L36) |
| orderItem.product | 产品与服务 | 成交商品或课程 | text | TEXT | 无下拉选项 | lead, order, lead_appeal, registration, student | [注册](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/OrderFilterFields.java#L37) |
| orderItem.payableAmount | 金额与付款 | 商品应付金额 | number | RANGE | 无下拉选项 | lead, order, lead_appeal, registration, student | [注册](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/OrderFilterFields.java#L38) |
| appeal.roundNo | 状态与进度 | 申诉轮次 | number | RANGE | 无下拉选项 | lead_appeal | [注册](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/AppealFilterFields.java#L12) |
| appeal.reviewStage | 状态与进度 | 审核阶段 | select | SELECT | 业务/技术固定选项（见源码） | lead_appeal | [注册](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/AppealFilterFields.java#L13) |
| appeal.status | 状态与进度 | 申诉状态 | select | SELECT | 字典：`zsjos_lead_appeal_status` | lead_appeal | [注册](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/AppealFilterFields.java#L14) |
| appeal.applicantUserId | 归属与人员 | 申请人 | select | SELECT | visible-users | lead_appeal | [注册](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/AppealFilterFields.java#L15) |
| appeal.reviewerUserId | 归属与人员 | 审核人 | select | SELECT | visible-users | lead_appeal | [注册](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/AppealFilterFields.java#L16) |
| appeal.reason | 补充信息 | 申诉原因 | text | TEXT | 无下拉选项 | lead_appeal | [注册](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/AppealFilterFields.java#L17) |
| appeal.decisionReason | 补充信息 | 裁决意见 | text | TEXT | 无下拉选项 | lead_appeal | [注册](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/AppealFilterFields.java#L18) |
| appeal.submittedAt | 时间 | 申诉提交时间 | date | DATE | 无下拉选项 | lead_appeal | [注册](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/AppealFilterFields.java#L19) |
| appeal.decidedAt | 时间 | 申诉处理时间 | date | DATE | 无下拉选项 | lead_appeal | [注册](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/AppealFilterFields.java#L20) |
| appeal.hasEvidence | 补充信息 | 申诉附件 | select | SELECT | 业务/技术固定选项（见源码） | lead_appeal | [注册](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/AppealFilterFields.java#L21) |
| review.submittedName | 身份与联系 | 提交姓名 | text | TEXT | 无下拉选项 | duplicate_review | [注册](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/DuplicateReviewFilterFields.java#L12) |
| review.submittedMobile | 身份与联系 | 提交手机号 | text | TEXT | 无下拉选项 | duplicate_review | [注册](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/DuplicateReviewFilterFields.java#L13) |
| review.submittedWechatId | 身份与联系 | 提交微信号 | text | TEXT | 无下拉选项 | duplicate_review | [注册](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/DuplicateReviewFilterFields.java#L14) |
| review.status | 状态与进度 | 复核状态 | select | SELECT | 业务/技术固定选项（见源码） | duplicate_review | [注册](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/DuplicateReviewFilterFields.java#L15) |
| review.resultType | 状态与进度 | 复核结果 | select | SELECT | 业务/技术固定选项（见源码） | duplicate_review | [注册](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/DuplicateReviewFilterFields.java#L16) |
| review.duplicateFlag | 状态与进度 | 重复类型 | select | SELECT | 业务/技术固定选项（见源码） | duplicate_review | [注册](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/DuplicateReviewFilterFields.java#L17) |
| review.duplicateResult | 状态与进度 | 查重结果 | select | SELECT | 业务/技术固定选项（见源码） | duplicate_review | [注册](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/DuplicateReviewFilterFields.java#L18) |
| review.submitterUserId | 归属与人员 | 复核提交人 | select | SELECT | visible-users | duplicate_review | [注册](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/DuplicateReviewFilterFields.java#L19) |
| review.reviewerUserId | 归属与人员 | 复核人 | select | SELECT | visible-users | duplicate_review | [注册](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/DuplicateReviewFilterFields.java#L20) |
| review.selectedSalesUserId | 归属与人员 | 选定销售 | select | SELECT | visible-users | duplicate_review | [注册](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/DuplicateReviewFilterFields.java#L21) |
| review.submissionSource | 补充信息 | 提交来源 | text | TEXT | 无下拉选项 | duplicate_review | [注册](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/DuplicateReviewFilterFields.java#L22) |
| review.matchRules | 补充信息 | 匹配规则 | text | TEXT | 无下拉选项 | duplicate_review | [注册](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/DuplicateReviewFilterFields.java#L23) |
| review.opinion | 补充信息 | 复核意见 | text | TEXT | 无下拉选项 | duplicate_review | [注册](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/DuplicateReviewFilterFields.java#L24) |
| review.submittedAt | 时间 | 复核提交时间 | date | DATE | 无下拉选项 | duplicate_review | [注册](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/DuplicateReviewFilterFields.java#L25) |
| review.reviewedAt | 时间 | 复核完成时间 | date | DATE | 无下拉选项 | duplicate_review | [注册](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/DuplicateReviewFilterFields.java#L26) |
| review.hasAttachments | 补充信息 | 复核附件 | select | SELECT | 业务/技术固定选项（见源码） | duplicate_review | [注册](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/DuplicateReviewFilterFields.java#L27) |
| registration.status | 状态与进度 | 履约状态 | select | SELECT | 业务/技术固定选项（见源码） | registration | [注册](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/RegistrationFilterFields.java#L12) |
| registration.plannerUserId | 归属与人员 | 学习规划师 | select | SELECT | visible-users | registration | [注册](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/RegistrationFilterFields.java#L13) |
| registration.approvedAt | 时间 | 报名审核时间 | date | DATE | 无下拉选项 | registration | [注册](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/RegistrationFilterFields.java#L14) |
| registration.completedAt | 时间 | 履约完成时间 | date | DATE | 无下拉选项 | registration | [注册](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/RegistrationFilterFields.java#L15) |
| registration.cancelledAt | 时间 | 履约取消时间 | date | DATE | 无下拉选项 | registration | [注册](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/RegistrationFilterFields.java#L16) |
| registration.cancelReason | 补充信息 | 履约取消原因 | text | TEXT | 无下拉选项 | registration | [注册](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/RegistrationFilterFields.java#L17) |
| registration.checklistTitle | 产品与服务 | 清单项目 | text | TEXT | 无下拉选项 | registration | [注册](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/RegistrationFilterFields.java#L19) |
| registration.checklistStatus | 状态与进度 | 清单完成状态 | select | SELECT | 业务/技术固定选项（见源码） | registration | [注册](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/RegistrationFilterFields.java#L20) |
| registration.hasChecklistAttachment | 补充信息 | 清单附件 | select | SELECT | 业务/技术固定选项（见源码） | registration | [注册](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/RegistrationFilterFields.java#L21) |
| registration.routeDepartment | 产品与服务 | 流转部门 | text | TEXT | 无下拉选项 | registration | [注册](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/RegistrationFilterFields.java#L23) |
| registration.routeAssignee | 归属与人员 | 流转负责人 | select | SELECT | visible-users | registration | [注册](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/RegistrationFilterFields.java#L24) |
| service.status | 状态与进度 | 服务状态 | select | SELECT | 业务/技术固定选项（见源码） | student | [注册](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/StudentFilterFields.java#L12) |
| service.ownerUserId | 归属与人员 | 服务负责人 | select | SELECT | visible-users | student | [注册](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/StudentFilterFields.java#L13) |
| service.activatedAt | 时间 | 服务激活时间 | date | DATE | 无下拉选项 | student | [注册](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/StudentFilterFields.java#L14) |
| service.pausedAt | 时间 | 服务暂停时间 | date | DATE | 无下拉选项 | student | [注册](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/StudentFilterFields.java#L15) |
| service.completedAt | 时间 | 服务完成时间 | date | DATE | 无下拉选项 | student | [注册](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/StudentFilterFields.java#L16) |
| service.terminatedAt | 时间 | 服务终止时间 | date | DATE | 无下拉选项 | student | [注册](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/StudentFilterFields.java#L17) |
| service.pauseReason | 补充信息 | 暂停原因 | text | TEXT | 无下拉选项 | student | [注册](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/StudentFilterFields.java#L18) |
| service.terminationReason | 补充信息 | 终止原因 | text | TEXT | 无下拉选项 | student | [注册](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/StudentFilterFields.java#L19) |
| subordinate.name | 身份与联系 | 姓名 | text | TEXT | 无下拉选项 | subordinate_sales | [注册](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/SubordinateSalesFilterFields.java#L13) |
| subordinate.username | 身份与联系 | 账号 | text | TEXT | 无下拉选项 | subordinate_sales | [注册](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/SubordinateSalesFilterFields.java#L14) |
| subordinate.mobile | 身份与联系 | 手机号 | text | TEXT | 无下拉选项 | subordinate_sales | [注册](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/SubordinateSalesFilterFields.java#L15) |
| subordinate.accountStatus | 状态与进度 | 账号状态 | select | SELECT | 业务/技术固定选项（见源码） | subordinate_sales | [注册](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/SubordinateSalesFilterFields.java#L16) |
| subordinate.presence | 状态与进度 | 在岗状态 | select | SELECT | 业务/技术固定选项（见源码） | subordinate_sales | [注册](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/SubordinateSalesFilterFields.java#L17) |
| subordinate.accepting | 状态与进度 | 接单状态 | select | SELECT | 业务/技术固定选项（见源码） | subordinate_sales | [注册](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/SubordinateSalesFilterFields.java#L18) |
| subordinate.eligible | 状态与进度 | 接单资格 | select | SELECT | 业务/技术固定选项（见源码） | subordinate_sales | [注册](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/SubordinateSalesFilterFields.java#L19) |
| subordinate.newcomerPoolStatus | 状态与进度 | 新人池状态 | select | SELECT | 业务/技术固定选项（见源码） | subordinate_sales | [注册](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/SubordinateSalesFilterFields.java#L20) |
| subordinate.todayPendingCount | 业务指标 | 今日待办数 | number | RANGE | 无下拉选项 | subordinate_sales | [注册](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/SubordinateSalesFilterFields.java#L21) |
| subordinate.todayFollowUpStatus | 业务指标 | 今日跟进状态 | select | SELECT | 业务/技术固定选项（见源码） | subordinate_sales | [注册](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/SubordinateSalesFilterFields.java#L22) |
| subordinate.firstFollowTimeoutCount | 业务指标 | 首次跟进超时数 | number | RANGE | 无下拉选项 | subordinate_sales | [注册](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/SubordinateSalesFilterFields.java#L23) |
| subordinate.suspendedLeadCount | 业务指标 | 挂起客资数 | number | RANGE | 无下拉选项 | subordinate_sales | [注册](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/SubordinateSalesFilterFields.java#L24) |
| subordinate.validLeadCount | 业务指标 | 有效客资数 | number | RANGE | 无下拉选项 | subordinate_sales | [注册](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/SubordinateSalesFilterFields.java#L25) |
| subordinate.convertedLeadCount | 业务指标 | 成交客资数 | number | RANGE | 无下拉选项 | subordinate_sales | [注册](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/SubordinateSalesFilterFields.java#L26) |
| subordinate.effectiveOrderCount | 业务指标 | 生效订单数 | number | RANGE | 无下拉选项 | subordinate_sales | [注册](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/SubordinateSalesFilterFields.java#L27) |
| subordinate.effectiveOrderAmount | 业务指标 | 生效订单金额 | number | RANGE | 无下拉选项 | subordinate_sales | [注册](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/SubordinateSalesFilterFields.java#L28) |

## 4. 请求与响应投影

以下逐项提取Java声明，包含嵌套类型字段（用所属类型区分）、技术属性和授权投影，不等于都应该开放筛选。继承的分页字段不重复列出；字段登记覆盖和建议见主矩阵。

### LeadManagementPageReqVO

[LeadManagementPageReqVO.java](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/lead/vo/management/LeadManagementPageReqVO.java)

| 声明类型（内嵌类型按源码出现顺序） | 属性 | Java类型 |
|---|---|---|
| LeadManagementPageReqVO | keyword | String |
| LeadManagementPageReqVO | status | String |
| LeadManagementPageReqVO | assignmentStatus | String |
| LeadManagementPageReqVO | audience | String |
| LeadManagementPageReqVO | relationScope | String |
| LeadManagementPageReqVO | simpleStatus | String |
| LeadManagementPageReqVO | sortField | String |
| LeadManagementPageReqVO | sortOrder | String |
| LeadManagementPageReqVO | view | String |
| LeadManagementPageReqVO | inboxGroup | String |
| LeadManagementPageReqVO | inboxStage | String |
| LeadManagementPageReqVO | inboxQuick | String |
| LeadManagementPageReqVO | sourceChannel | String |
| LeadManagementPageReqVO | leadCategory | String |
| LeadManagementPageReqVO | sourceUserId | Long |
| LeadManagementPageReqVO | providerOwnerType | String |
| LeadManagementPageReqVO | providerOwnerId | Long |
| LeadManagementPageReqVO | ownerUserId | Long |
| LeadManagementPageReqVO | advancedFilter | AdvancedFilterGroupReqVO |
| LeadManagementPageReqVO | cursor | String |
| LeadManagementPageReqVO | limit | Integer |
| LeadManagementPageReqVO | cursorActivityAt | LocalDateTime |
| LeadManagementPageReqVO | cursorId | Long |

### LeadManagementRespVO

[LeadManagementRespVO.java](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/lead/vo/management/LeadManagementRespVO.java)

| 声明类型（内嵌类型按源码出现顺序） | 属性 | Java类型 |
|---|---|---|
| LeadManagementRespVO | id | Long |
| LeadManagementRespVO | version | Integer |
| LeadManagementRespVO | leadNo | String |
| LeadManagementRespVO | personId | Long |
| LeadManagementRespVO | submittedName | String |
| LeadManagementRespVO | submittedMobile | String |
| LeadManagementRespVO | submittedWechatId | String |
| LeadManagementRespVO | sourceType | String |
| LeadManagementRespVO | sourceLabel | String |
| LeadManagementRespVO | ownerIdentity | String |
| LeadManagementRespVO | ownerIdentityLabel | String |
| LeadManagementRespVO | sourceUserId | Long |
| LeadManagementRespVO | sourceUserName | String |
| LeadManagementRespVO | partnerOwnerNameSnapshot | String |
| LeadManagementRespVO | providerOwnerType | String |
| LeadManagementRespVO | providerOwnerId | Long |
| LeadManagementRespVO | providerOwnerNameSnapshot | String |
| LeadManagementRespVO | contributionUserIdSnapshot | Long |
| LeadManagementRespVO | contributionUserNameSnapshot | String |
| LeadManagementRespVO | contributionSupervisorUserIdSnapshot | Long |
| LeadManagementRespVO | contributionSupervisorNameSnapshot | String |
| LeadManagementRespVO | contributionDeptIdSnapshot | Long |
| LeadManagementRespVO | contributionDeptNameSnapshot | String |
| LeadManagementRespVO | countedAt | LocalDateTime |
| LeadManagementRespVO | sourceChannel | String |
| LeadManagementRespVO | sourceChannelLabelSnapshot | String |
| LeadManagementRespVO | provinceCode | String |
| LeadManagementRespVO | provinceName | String |
| LeadManagementRespVO | cityCode | String |
| LeadManagementRespVO | cityName | String |
| LeadManagementRespVO | salesStage | String |
| LeadManagementRespVO | salesStageLabelSnapshot | String |
| LeadManagementRespVO | leadCategory | String |
| LeadManagementRespVO | leadCategoryLabelSnapshot | String |
| LeadManagementRespVO | remark | String |
| LeadManagementRespVO | remarkHistory | List<LeadRemarkRespVO> |
| LeadManagementRespVO | remarkHistoryIncomplete | Boolean |
| LeadManagementRespVO | status | String |
| LeadManagementRespVO | assignmentStatus | String |
| LeadManagementRespVO | handlingStage | String |
| LeadManagementRespVO | qualificationStatus | String |
| LeadManagementRespVO | followUpStatus | String |
| LeadManagementRespVO | operationalStatus | String |
| LeadManagementRespVO | dispatchMode | String |
| LeadManagementRespVO | ownerUserId | Long |
| LeadManagementRespVO | ownerUserName | String |
| LeadManagementRespVO | pendingAssigneeUserId | Long |
| LeadManagementRespVO | pendingAssigneeUserName | String |
| LeadManagementRespVO | pendingExpiresAt | LocalDateTime |
| LeadManagementRespVO | assignmentAttemptCount | Integer |
| LeadManagementRespVO | publicPoolAt | LocalDateTime |
| LeadManagementRespVO | submittedAt | LocalDateTime |
| LeadManagementRespVO | lastActivityAt | LocalDateTime |
| LeadManagementRespVO | nextFollowUpAt | LocalDateTime |
| LeadManagementRespVO | currentAssignmentFirstFollowUpAt | LocalDateTime |
| LeadManagementRespVO | currentAssignmentFirstFollowUpDeadlineAt | LocalDateTime |
| LeadManagementRespVO | qualificationStartedAt | LocalDateTime |
| LeadManagementRespVO | qualificationDeadlineAt | LocalDateTime |
| LeadManagementRespVO | suspendedAt | LocalDateTime |
| LeadManagementRespVO | qualifiedByUserId | Long |
| LeadManagementRespVO | qualifiedByUserName | String |
| LeadManagementRespVO | qualifiedAt | LocalDateTime |
| LeadManagementRespVO | validDescription | String |
| LeadManagementRespVO | convertedAt | LocalDateTime |
| LeadManagementRespVO | salesOrderSubmittedAt | LocalDateTime |
| LeadManagementRespVO | invalidReason | String |
| LeadManagementRespVO | invalidReasonLabelSnapshot | String |
| LeadManagementRespVO | invalidDescription | String |
| LeadManagementRespVO | invalidEvidence | List<LeadEvidenceVO> |
| LeadManagementRespVO | recycleSourceOwnerUserId | Long |
| LeadManagementRespVO | recycleSourceOwnerUserName | String |
| LeadManagementRespVO | appealDeadlineAt | LocalDateTime |
| LeadManagementRespVO | closedAt | LocalDateTime |
| LeadManagementRespVO | closeReason | String |
| LeadManagementRespVO | createTime | LocalDateTime |
| LeadManagementRespVO | updateTime | LocalDateTime |
| LeadManagementRespVO | relationTypes | List<String> |
| LeadManagementRespVO | overviewVisible | Boolean |
| LeadManagementRespVO | visibleTabs | List<String> |
| LeadManagementRespVO | identityMaskMode | String |
| LeadManagementRespVO | primaryProduct | LeadProductVO |
| LeadManagementRespVO | intendedProducts | List<LeadProductVO> |
| LeadManagementRespVO | attachments | List<LeadAttachmentVO> |
| LeadManagementRespVO | opportunity | OpportunityVO |
| LeadManagementRespVO | activeSalesOrderId | Long |
| LeadManagementRespVO | activeSalesOrderStatus | String |
| LeadManagementRespVO | availableActions | List<ActionVO> |
| OpportunityVO | id | Long |
| OpportunityVO | status | String |
| OpportunityVO | nextFollowUpAt | LocalDateTime |
| OpportunityVO | wonAt | LocalDateTime |
| ActionVO | code | String |
| ActionVO | enabled | Boolean |
| LeadProductVO | id | Long |
| LeadProductVO | spuRef | String |
| LeadProductVO | spuName | String |
| LeadProductVO | skuRef | String |
| LeadProductVO | skuName | String |
| LeadProductVO | selectedAttrValues | String |
| LeadProductVO | specs | java.util.List<cn.iocoder.yudao.module.zsjos.controller.admin.product.vo.ProductSpecVO> |
| LeadProductVO | price | BigDecimal |
| LeadProductVO | categoryName | String |
| LeadProductVO | primary | Boolean |
| LeadAttachmentVO | id | Long |
| LeadAttachmentVO | fileUrl | String |
| LeadAttachmentVO | originalName | String |
| LeadAttachmentVO | contentType | String |
| LeadAttachmentVO | fileSize | Long |
| LeadEvidenceVO | infraFileId | Long |
| LeadEvidenceVO | fileUrl | String |
| LeadEvidenceVO | originalName | String |
| LeadEvidenceVO | contentType | String |
| LeadEvidenceVO | fileSize | Long |
| LeadEvidenceVO | sort | Integer |

### LeadAgingPoolPageReqVO

[LeadAgingPoolPageReqVO.java](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/lead/vo/agingpool/LeadAgingPoolPageReqVO.java)

| 声明类型（内嵌类型按源码出现顺序） | 属性 | Java类型 |
|---|---|---|
| LeadAgingPoolPageReqVO | keyword | String |
| LeadAgingPoolPageReqVO | status | String |
| LeadAgingPoolPageReqVO | inboxGroup | String |
| LeadAgingPoolPageReqVO | inboxStage | String |
| LeadAgingPoolPageReqVO | relationScope | String |
| LeadAgingPoolPageReqVO | advancedFilter | AdvancedFilterGroupReqVO |

### LeadAgingPoolRespVO

[LeadAgingPoolRespVO.java](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/lead/vo/agingpool/LeadAgingPoolRespVO.java)

| 声明类型（内嵌类型按源码出现顺序） | 属性 | Java类型 |
|---|---|---|
| LeadAgingPoolRespVO | cycleId | Long |
| LeadAgingPoolRespVO | leadId | Long |
| LeadAgingPoolRespVO | leadNo | String |
| LeadAgingPoolRespVO | cycleNo | Integer |
| LeadAgingPoolRespVO | status | String |
| LeadAgingPoolRespVO | originalOwnerUserId | Long |
| LeadAgingPoolRespVO | originalOwnerUserName | String |
| LeadAgingPoolRespVO | collaboratorUserId | Long |
| LeadAgingPoolRespVO | collaboratorUserName | String |
| LeadAgingPoolRespVO | frozenDeptId | Long |
| LeadAgingPoolRespVO | frozenDeptName | String |
| LeadAgingPoolRespVO | submittedName | String |
| LeadAgingPoolRespVO | submittedMobile | String |
| LeadAgingPoolRespVO | submittedWechatId | String |
| LeadAgingPoolRespVO | leadCategory | String |
| LeadAgingPoolRespVO | sourceChannel | String |
| LeadAgingPoolRespVO | ownershipStartedAt | LocalDateTime |
| LeadAgingPoolRespVO | dueAt | LocalDateTime |
| LeadAgingPoolRespVO | enteredAt | LocalDateTime |
| LeadAgingPoolRespVO | assignedAt | LocalDateTime |
| LeadAgingPoolRespVO | lastFollowUpAt | LocalDateTime |
| LeadAgingPoolRespVO | nextFollowUpAt | LocalDateTime |
| LeadAgingPoolRespVO | activeSalesOrderId | Long |
| LeadAgingPoolRespVO | activeSalesOrderStatus | String |
| LeadAgingPoolRespVO | availableActions | List<String> |

### LeadClaimPoolPageReqVO

[LeadClaimPoolPageReqVO.java](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/lead/vo/dispatch/LeadClaimPoolPageReqVO.java)

| 声明类型（内嵌类型按源码出现顺序） | 属性 | Java类型 |
|---|---|---|
| LeadClaimPoolPageReqVO | keyword | String |
| LeadClaimPoolPageReqVO | advancedFilter | AdvancedFilterGroupReqVO |

### LeadPendingRespVO

[LeadPendingRespVO.java](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/lead/vo/dispatch/LeadPendingRespVO.java)

| 声明类型（内嵌类型按源码出现顺序） | 属性 | Java类型 |
|---|---|---|
| LeadPendingRespVO | id | Long |
| LeadPendingRespVO | leadNo | String |
| LeadPendingRespVO | dispatchMode | String |
| LeadPendingRespVO | maskedName | String |
| LeadPendingRespVO | maskedMobile | String |
| LeadPendingRespVO | maskedWechatId | String |
| LeadPendingRespVO | provinceName | String |
| LeadPendingRespVO | cityName | String |
| LeadPendingRespVO | intendedProducts | List<String> |
| LeadPendingRespVO | primaryIntendedProduct | String |
| LeadPendingRespVO | sourceChannel | String |
| LeadPendingRespVO | sourceChannelLabel | String |
| LeadPendingRespVO | leadCategory | String |
| LeadPendingRespVO | leadCategoryLabel | String |
| LeadPendingRespVO | remark | String |
| LeadPendingRespVO | attachmentUrls | List<String> |
| LeadPendingRespVO | submittedAt | LocalDateTime |
| LeadPendingRespVO | expiresAt | LocalDateTime |
| LeadPendingRespVO | remainingSeconds | Long |
| LeadPendingRespVO | rejectable | Boolean |
| LeadPendingRespVO | deferrable | Boolean |
| LeadPendingRespVO | assignmentHistoryId | Long |

### LeadDuplicateReviewPageReqVO

[LeadDuplicateReviewPageReqVO.java](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/lead/vo/duplicate/LeadDuplicateReviewPageReqVO.java)

| 声明类型（内嵌类型按源码出现顺序） | 属性 | Java类型 |
|---|---|---|
| LeadDuplicateReviewPageReqVO | status | String |
| LeadDuplicateReviewPageReqVO | keyword | String |
| LeadDuplicateReviewPageReqVO | advancedFilter | AdvancedFilterGroupReqVO |

### LeadDuplicateReviewRespVO

[LeadDuplicateReviewRespVO.java](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/lead/vo/duplicate/LeadDuplicateReviewRespVO.java)

| 声明类型（内嵌类型按源码出现顺序） | 属性 | Java类型 |
|---|---|---|
| LeadDuplicateReviewRespVO | id | Long |
| LeadDuplicateReviewRespVO | status | String |
| LeadDuplicateReviewRespVO | submitterUserId | Long |
| LeadDuplicateReviewRespVO | submissionSnapshot | String |
| LeadDuplicateReviewRespVO | duplicateFlag | String |
| LeadDuplicateReviewRespVO | duplicateResult | String |
| LeadDuplicateReviewRespVO | primaryRuleCode | String |
| LeadDuplicateReviewRespVO | reviewFingerprint | String |
| LeadDuplicateReviewRespVO | matchRules | String |
| LeadDuplicateReviewRespVO | candidateSnapshot | String |
| LeadDuplicateReviewRespVO | resultType | String |
| LeadDuplicateReviewRespVO | reviewOpinion | String |
| LeadDuplicateReviewRespVO | reviewAttachments | String |
| LeadDuplicateReviewRespVO | selectedSalesUserId | Long |
| LeadDuplicateReviewRespVO | reviewerUserId | Long |
| LeadDuplicateReviewRespVO | reviewedAt | LocalDateTime |
| LeadDuplicateReviewRespVO | beforeSnapshot | String |
| LeadDuplicateReviewRespVO | afterSnapshot | String |
| LeadDuplicateReviewRespVO | createTime | LocalDateTime |

### LeadAppealPageReqVO

[LeadAppealPageReqVO.java](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/lead/vo/appeal/LeadAppealPageReqVO.java)

| 声明类型（内嵌类型按源码出现顺序） | 属性 | Java类型 |
|---|---|---|
| LeadAppealPageReqVO | handled | Boolean |
| LeadAppealPageReqVO | cursor | String |
| LeadAppealPageReqVO | limit | Integer |
| LeadAppealPageReqVO | keyword | String |
| LeadAppealPageReqVO | advancedFilter | AdvancedFilterGroupReqVO |

### LeadAppealRespVO

[LeadAppealRespVO.java](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/lead/vo/appeal/LeadAppealRespVO.java)

| 声明类型（内嵌类型按源码出现顺序） | 属性 | Java类型 |
|---|---|---|
| LeadAppealRespVO | id | Long |
| LeadAppealRespVO | leadId | Long |
| LeadAppealRespVO | leadNo | String |
| LeadAppealRespVO | leadName | String |
| LeadAppealRespVO | roundNo | Integer |
| LeadAppealRespVO | reviewStage | String |
| LeadAppealRespVO | status | String |
| LeadAppealRespVO | applicantUserId | Long |
| LeadAppealRespVO | applicantUserName | String |
| LeadAppealRespVO | reason | String |
| LeadAppealRespVO | evidence | List<EvidenceVO> |
| LeadAppealRespVO | invalidReasonSnapshot | String |
| LeadAppealRespVO | invalidDescriptionSnapshot | String |
| LeadAppealRespVO | invalidEvidenceSnapshot | List<EvidenceVO> |
| LeadAppealRespVO | processInstanceId | String |
| LeadAppealRespVO | taskId | String |
| LeadAppealRespVO | reviewerUserId | Long |
| LeadAppealRespVO | reviewerUserName | String |
| LeadAppealRespVO | decisionReason | String |
| LeadAppealRespVO | decisionEvidence | List<EvidenceVO> |
| LeadAppealRespVO | submittedAt | LocalDateTime |
| LeadAppealRespVO | decidedAt | LocalDateTime |
| LeadAppealRespVO | canSubmitNextRound | Boolean |
| EvidenceVO | infraFileId | Long |
| EvidenceVO | fileUrl | String |
| EvidenceVO | originalName | String |
| EvidenceVO | contentType | String |
| EvidenceVO | fileSize | Long |
| EvidenceVO | sort | Integer |

### SubordinateSalesPageReqVO

[SubordinateSalesPageReqVO.java](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/lead/vo/subordinate/SubordinateSalesPageReqVO.java)

| 声明类型（内嵌类型按源码出现顺序） | 属性 | Java类型 |
|---|---|---|
| SubordinateSalesPageReqVO | keyword | String |
| SubordinateSalesPageReqVO | accountStatus | Integer |
| SubordinateSalesPageReqVO | presence | String |
| SubordinateSalesPageReqVO | accepting | Boolean |
| SubordinateSalesPageReqVO | advancedFilter | AdvancedFilterGroupReqVO |

### SubordinateSalesRespVO

[SubordinateSalesRespVO.java](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/lead/vo/subordinate/SubordinateSalesRespVO.java)

| 声明类型（内嵌类型按源码出现顺序） | 属性 | Java类型 |
|---|---|---|
| SubordinateSalesRespVO | userId | Long |
| SubordinateSalesRespVO | name | String |
| SubordinateSalesRespVO | avatar | String |
| SubordinateSalesRespVO | username | String |
| SubordinateSalesRespVO | mobile | String |
| SubordinateSalesRespVO | accountStatus | Integer |
| SubordinateSalesRespVO | presence | String |
| SubordinateSalesRespVO | accepting | Boolean |
| SubordinateSalesRespVO | eligible | Boolean |
| SubordinateSalesRespVO | canReceiveNewLeads | Boolean |
| SubordinateSalesRespVO | newcomerPoolStatus | String |
| SubordinateSalesRespVO | todayPendingCount | Long |
| SubordinateSalesRespVO | todayFollowUpTotalCount | Long |
| SubordinateSalesRespVO | todayFollowUpRemainingCount | Long |
| SubordinateSalesRespVO | todayAssignedCount | Long |
| SubordinateSalesRespVO | todayMissedCount | Long |
| SubordinateSalesRespVO | todayReceivedCount | Long |
| SubordinateSalesRespVO | todayQualifiedCount | Long |
| SubordinateSalesRespVO | todayFollowUpRecordCount | Long |
| SubordinateSalesRespVO | todayOrderAmount | BigDecimal |
| SubordinateSalesRespVO | pendingQualificationCount | Long |
| SubordinateSalesRespVO | todayFollowUpStatus | String |
| SubordinateSalesRespVO | firstFollowTimeoutCount | Long |
| SubordinateSalesRespVO | suspendedLeadCount | Long |
| SubordinateSalesRespVO | categoryCounts | List<CategoryCountVO> |
| SubordinateSalesRespVO | validLeadCount | Long |
| SubordinateSalesRespVO | convertedLeadCount | Long |
| SubordinateSalesRespVO | effectiveOrderCount | Long |
| SubordinateSalesRespVO | effectiveOrderAmount | BigDecimal |
| CategoryCountVO | value | String |
| CategoryCountVO | label | String |
| CategoryCountVO | count | Long |
| CategoryCountVO | configured | Boolean |

### SalesOrderMyPageReqVO

[SalesOrderMyPageReqVO.java](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/order/vo/SalesOrderMyPageReqVO.java)

| 声明类型（内嵌类型按源码出现顺序） | 属性 | Java类型 |
|---|---|---|
| SalesOrderMyPageReqVO | status | String |
| SalesOrderMyPageReqVO | keyword | String |
| SalesOrderMyPageReqVO | advancedFilter | AdvancedFilterGroupReqVO |

### SalesOrderPageReqVO

[SalesOrderPageReqVO.java](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/order/vo/SalesOrderPageReqVO.java)

| 声明类型（内嵌类型按源码出现顺序） | 属性 | Java类型 |
|---|---|---|
| SalesOrderPageReqVO | center | String |
| SalesOrderPageReqVO | handled | Boolean |
| SalesOrderPageReqVO | groupKey | String |
| SalesOrderPageReqVO | optionKey | String |
| SalesOrderPageReqVO | keyword | String |
| SalesOrderPageReqVO | advancedFilter | AdvancedFilterGroupReqVO |
| SalesOrderPageReqVO | cursor | String |
| SalesOrderPageReqVO | limit | Integer |
| SalesOrderPageReqVO | cursorTaskTime | LocalDateTime |
| SalesOrderPageReqVO | cursorTaskId | String |

### SalesOrderListItemRespVO

[SalesOrderListItemRespVO.java](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/order/vo/SalesOrderListItemRespVO.java)

| 声明类型（内嵌类型按源码出现顺序） | 属性 | Java类型 |
|---|---|---|
| SalesOrderListItemRespVO | historyMissingFields | java.util.Map<String, String> |
| SalesOrderListItemRespVO | id | Long |
| SalesOrderListItemRespVO | orderNo | String |
| SalesOrderListItemRespVO | leadId | Long |
| SalesOrderListItemRespVO | personId | Long |
| SalesOrderListItemRespVO | submitterUserId | Long |
| SalesOrderListItemRespVO | submitterUserName | String |
| SalesOrderListItemRespVO | orderType | String |
| SalesOrderListItemRespVO | status | String |
| SalesOrderListItemRespVO | buyerName | String |
| SalesOrderListItemRespVO | studentName | String |
| SalesOrderListItemRespVO | studentNatureLabelSnapshot | String |
| SalesOrderListItemRespVO | studentMobile | String |
| SalesOrderListItemRespVO | studentWechatId | String |
| SalesOrderListItemRespVO | provinceName | String |
| SalesOrderListItemRespVO | cityName | String |
| SalesOrderListItemRespVO | agreedExamTime | String |
| SalesOrderListItemRespVO | classType | String |
| SalesOrderListItemRespVO | servicePeriodLabelSnapshot | String |
| SalesOrderListItemRespVO | studentSourceLabelSnapshot | String |
| SalesOrderListItemRespVO | totalAmount | BigDecimal |
| SalesOrderListItemRespVO | customerPaidAt | LocalDateTime |
| SalesOrderListItemRespVO | feeModeLabelSnapshot | String |
| SalesOrderListItemRespVO | paymentMethodLabelSnapshot | String |
| SalesOrderListItemRespVO | remark | String |
| SalesOrderListItemRespVO | studentSpecialRequirements | String |
| SalesOrderListItemRespVO | materialDeliveryContact | String |
| SalesOrderListItemRespVO | giftItems | String |
| SalesOrderListItemRespVO | giftShippingAddress | String |
| SalesOrderListItemRespVO | repurchaseReason | String |
| SalesOrderListItemRespVO | terminationReason | String |
| SalesOrderListItemRespVO | productSummary | String |
| SalesOrderListItemRespVO | leadNo | String |
| SalesOrderListItemRespVO | leadSourceLabel | String |
| SalesOrderListItemRespVO | leadSourceUserName | String |
| SalesOrderListItemRespVO | leadOwnerUserName | String |
| SalesOrderListItemRespVO | formalOwnerIdentity | String |
| SalesOrderListItemRespVO | formalOwnerIdentityLabel | String |
| SalesOrderListItemRespVO | leadCategoryLabelSnapshot | String |
| SalesOrderListItemRespVO | leadSourceChannelLabelSnapshot | String |
| SalesOrderListItemRespVO | leadProvinceName | String |
| SalesOrderListItemRespVO | leadCityName | String |
| SalesOrderListItemRespVO | approvalRoundNo | Integer |
| SalesOrderListItemRespVO | submittedAt | LocalDateTime |
| SalesOrderListItemRespVO | effectiveAt | LocalDateTime |
| SalesOrderListItemRespVO | taskId | String |
| SalesOrderListItemRespVO | approvalReasonRequired | Boolean |
| SalesOrderListItemRespVO | taskDefinitionKey | String |
| SalesOrderListItemRespVO | taskStatus | Integer |
| SalesOrderListItemRespVO | taskReason | String |
| SalesOrderListItemRespVO | taskCreateTime | LocalDateTime |
| SalesOrderListItemRespVO | taskEndTime | LocalDateTime |
| SalesOrderListItemRespVO | supervisorConfirmationId | Long |
| SalesOrderListItemRespVO | supervisorConfirmationStatus | String |
| SalesOrderListItemRespVO | supervisorRequesterName | String |
| SalesOrderListItemRespVO | canRevise | Boolean |
| SalesOrderListItemRespVO | canTerminate | Boolean |

### SalesOrderRespVO

[SalesOrderRespVO.java](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/order/vo/SalesOrderRespVO.java)

| 声明类型（内嵌类型按源码出现顺序） | 属性 | Java类型 |
|---|---|---|
| SalesOrderRespVO | id | Long |
| SalesOrderRespVO | orderNo | String |
| SalesOrderRespVO | collectionMode | String |
| SalesOrderRespVO | paymentStatus | String |
| SalesOrderRespVO | transactionLocked | Boolean |
| SalesOrderRespVO | leadId | Long |
| SalesOrderRespVO | opportunityId | Long |
| SalesOrderRespVO | status | String |
| SalesOrderRespVO | orderType | String |
| SalesOrderRespVO | personId | Long |
| SalesOrderRespVO | formalSalesUserId | Long |
| SalesOrderRespVO | formalOwnerIdentity | String |
| SalesOrderRespVO | formalOwnerIdentityLabel | String |
| SalesOrderRespVO | submitterUserId | Long |
| SalesOrderRespVO | submitterUserName | String |
| SalesOrderRespVO | formalSalesUserName | String |
| SalesOrderRespVO | historyMissingFields | Map<String, String> |
| SalesOrderRespVO | supersedesOrderId | Long |
| SalesOrderRespVO | supersededByOrderId | Long |
| SalesOrderRespVO | buyerName | String |
| SalesOrderRespVO | studentName | String |
| SalesOrderRespVO | studentNature | String |
| SalesOrderRespVO | studentNatureLabelSnapshot | String |
| SalesOrderRespVO | studentMobile | String |
| SalesOrderRespVO | studentWechatId | String |
| SalesOrderRespVO | provinceCode | String |
| SalesOrderRespVO | provinceName | String |
| SalesOrderRespVO | cityCode | String |
| SalesOrderRespVO | cityName | String |
| SalesOrderRespVO | agreedExamTime | String |
| SalesOrderRespVO | classType | String |
| SalesOrderRespVO | servicePeriod | String |
| SalesOrderRespVO | servicePeriodLabelSnapshot | String |
| SalesOrderRespVO | studentSource | String |
| SalesOrderRespVO | studentSourceLabelSnapshot | String |
| SalesOrderRespVO | totalAmount | BigDecimal |
| SalesOrderRespVO | customerPaidAt | LocalDateTime |
| SalesOrderRespVO | feeMode | String |
| SalesOrderRespVO | feeModeLabelSnapshot | String |
| SalesOrderRespVO | paymentMethod | String |
| SalesOrderRespVO | paymentMethodLabelSnapshot | String |
| SalesOrderRespVO | remark | String |
| SalesOrderRespVO | studentSpecialRequirements | String |
| SalesOrderRespVO | materialDeliveryContact | String |
| SalesOrderRespVO | giftItems | String |
| SalesOrderRespVO | giftShippingAddress | String |
| SalesOrderRespVO | items | List<ItemVO> |
| SalesOrderRespVO | paymentVouchers | List<AttachmentVO> |
| SalesOrderRespVO | approvalRoundNo | Integer |
| SalesOrderRespVO | approvalRoundStatus | String |
| SalesOrderRespVO | processInstanceId | String |
| SalesOrderRespVO | taskId | String |
| SalesOrderRespVO | approvalReasonRequired | Boolean |
| SalesOrderRespVO | taskDefinitionKey | String |
| SalesOrderRespVO | taskStatus | Integer |
| SalesOrderRespVO | taskReason | String |
| SalesOrderRespVO | taskCreateTime | LocalDateTime |
| SalesOrderRespVO | taskEndTime | LocalDateTime |
| SalesOrderRespVO | decisionReason | String |
| SalesOrderRespVO | canRevise | Boolean |
| SalesOrderRespVO | canTerminate | Boolean |
| SalesOrderRespVO | repurchaseReason | String |
| SalesOrderRespVO | terminationReason | String |
| SalesOrderRespVO | version | Integer |
| SalesOrderRespVO | currentApprovalRoundId | Long |
| SalesOrderRespVO | approvalRoundVersion | Integer |
| SalesOrderRespVO | canRequestSupervisorConfirmation | Boolean |
| SalesOrderRespVO | submittedAt | LocalDateTime |
| SalesOrderRespVO | effectiveAt | LocalDateTime |
| SalesOrderRespVO | leadProfile | LeadProfileVO |
| SalesOrderRespVO | registrationApproval | ApprovalStatusVO |
| SalesOrderRespVO | financeApproval | ApprovalStatusVO |
| SalesOrderRespVO | registrationSupervisorConfirmation | SupervisorConfirmationVO |
| SalesOrderRespVO | financeSupervisorConfirmation | SupervisorConfirmationVO |
| SalesOrderRespVO | supervisorApproval | SupervisorApprovalVO |
| LeadProfileVO | leadNo | String |
| LeadProfileVO | submittedName | String |
| LeadProfileVO | submittedMobile | String |
| LeadProfileVO | submittedWechatId | String |
| LeadProfileVO | sourceSubjectType | String |
| LeadProfileVO | sourceSubjectId | Long |
| LeadProfileVO | ownerUserId | Long |
| LeadProfileVO | sourceType | String |
| LeadProfileVO | sourceLabel | String |
| LeadProfileVO | sourceUserName | String |
| LeadProfileVO | sourceChannel | String |
| LeadProfileVO | sourceChannelLabelSnapshot | String |
| LeadProfileVO | provinceName | String |
| LeadProfileVO | cityName | String |
| LeadProfileVO | leadCategory | String |
| LeadProfileVO | leadCategoryLabelSnapshot | String |
| LeadProfileVO | dispatchMode | String |
| LeadProfileVO | ownerUserName | String |
| LeadProfileVO | ownerIdentity | String |
| LeadProfileVO | ownerIdentityLabel | String |
| SupervisorConfirmationVO | id | Long |
| SupervisorConfirmationVO | status | String |
| SupervisorConfirmationVO | requesterUserId | Long |
| SupervisorConfirmationVO | requesterUserName | String |
| SupervisorConfirmationVO | requestReason | String |
| SupervisorConfirmationVO | decisionReason | String |
| SupervisorConfirmationVO | requestedAt | LocalDateTime |
| SupervisorConfirmationVO | decidedAt | LocalDateTime |
| SupervisorApprovalVO | id | Long |
| SupervisorApprovalVO | status | String |
| SupervisorApprovalVO | taskDefinitionKey | String |
| SupervisorApprovalVO | center | String |
| SupervisorApprovalVO | requesterUserId | Long |
| SupervisorApprovalVO | requesterUserName | String |
| SupervisorApprovalVO | supervisorUserId | Long |
| SupervisorApprovalVO | supervisorUserName | String |
| SupervisorApprovalVO | requestReason | String |
| SupervisorApprovalVO | decisionReason | String |
| SupervisorApprovalVO | requestedAt | LocalDateTime |
| SupervisorApprovalVO | decidedAt | LocalDateTime |
| ApprovalStatusVO | status | String |
| ApprovalStatusVO | reviewerUserId | Long |
| ApprovalStatusVO | reviewerUserName | String |
| ApprovalStatusVO | createTime | LocalDateTime |
| ApprovalStatusVO | endTime | LocalDateTime |
| ItemVO | id | Long |
| ItemVO | productRef | String |
| ItemVO | skuRef | String |
| ItemVO | productName | String |
| ItemVO | skuName | String |
| ItemVO | categoryPath | List<String> |
| ItemVO | attrValues | Map<String, String> |
| ItemVO | specs | List<cn.iocoder.yudao.module.zsjos.controller.admin.product.vo.ProductSpecVO> |
| ItemVO | actualAmount | BigDecimal |
| AttachmentVO | infraFileId | Long |
| AttachmentVO | fileUrl | String |
| AttachmentVO | originalName | String |
| AttachmentVO | contentType | String |
| AttachmentVO | fileSize | Long |

### FinanceOrderExportRowRespVO

[FinanceOrderExportRowRespVO.java](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/order/vo/FinanceOrderExportRowRespVO.java)

| 声明类型（内嵌类型按源码出现顺序） | 属性 | Java类型 |
|---|---|---|
| FinanceOrderExportRowRespVO | orderNo | String |
| FinanceOrderExportRowRespVO | orderType | String |
| FinanceOrderExportRowRespVO | status | String |
| FinanceOrderExportRowRespVO | buyerName | String |
| FinanceOrderExportRowRespVO | studentName | String |
| FinanceOrderExportRowRespVO | studentMobile | String |
| FinanceOrderExportRowRespVO | studentWechatId | String |
| FinanceOrderExportRowRespVO | region | String |
| FinanceOrderExportRowRespVO | courseSummary | String |
| FinanceOrderExportRowRespVO | totalAmount | BigDecimal |
| FinanceOrderExportRowRespVO | customerPaidAt | LocalDateTime |
| FinanceOrderExportRowRespVO | paymentMethod | String |
| FinanceOrderExportRowRespVO | formalSalesName | String |
| FinanceOrderExportRowRespVO | formalOwnerIdentityLabel | String |
| FinanceOrderExportRowRespVO | submitterName | String |
| FinanceOrderExportRowRespVO | submittedAt | LocalDateTime |
| FinanceOrderExportRowRespVO | effectiveAt | LocalDateTime |
| FinanceOrderExportRowRespVO | approvalRoundNo | Integer |
| FinanceOrderExportRowRespVO | registrationStatus | String |
| FinanceOrderExportRowRespVO | registrationReviewer | String |
| FinanceOrderExportRowRespVO | registrationReviewedAt | LocalDateTime |
| FinanceOrderExportRowRespVO | financeStatus | String |
| FinanceOrderExportRowRespVO | financeReviewer | String |
| FinanceOrderExportRowRespVO | financeReviewedAt | LocalDateTime |
| FinanceOrderExportRowRespVO | finalReason | String |

### RegistrationPoolPageReqVO

[RegistrationPoolPageReqVO.java](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/registration/vo/RegistrationPoolPageReqVO.java)

| 声明类型（内嵌类型按源码出现顺序） | 属性 | Java类型 |
|---|---|---|
| RegistrationPoolPageReqVO | status | String |
| RegistrationPoolPageReqVO | keyword | String |
| RegistrationPoolPageReqVO | advancedFilter | AdvancedFilterGroupReqVO |

### RegistrationCaseRespVO

[RegistrationCaseRespVO.java](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/registration/vo/RegistrationCaseRespVO.java)

| 声明类型（内嵌类型按源码出现顺序） | 属性 | Java类型 |
|---|---|---|
| RegistrationCaseRespVO | id | Long |
| RegistrationCaseRespVO | orderId | Long |
| RegistrationCaseRespVO | orderNo | String |
| RegistrationCaseRespVO | orderStatus | String |
| RegistrationCaseRespVO | orderStatusLabel | String |
| RegistrationCaseRespVO | studentName | String |
| RegistrationCaseRespVO | studentMobile | String |
| RegistrationCaseRespVO | leadNo | String |
| RegistrationCaseRespVO | status | String |
| RegistrationCaseRespVO | statusLabel | String |
| RegistrationCaseRespVO | assignmentMode | String |
| RegistrationCaseRespVO | studyPlannerUserId | Long |
| RegistrationCaseRespVO | studyPlannerUserName | String |
| RegistrationCaseRespVO | registrationApprovedAt | LocalDateTime |
| RegistrationCaseRespVO | completedAt | LocalDateTime |
| RegistrationCaseRespVO | cancelledAt | LocalDateTime |
| RegistrationCaseRespVO | cancelReason | String |
| RegistrationCaseRespVO | version | Integer |
| RegistrationCaseRespVO | completable | Boolean |
| RegistrationCaseRespVO | completionBlockCode | String |
| RegistrationCaseRespVO | completionBlockReason | String |
| RegistrationCaseRespVO | items | List<ItemVO> |
| RegistrationCaseRespVO | routes | List<RouteVO> |
| RegistrationCaseRespVO | classAssignments | List<ClassAssignmentVO> |
| ItemVO | id | Long |
| ItemVO | itemKey | String |
| ItemVO | itemType | String |
| ItemVO | title | String |
| ItemVO | sort | Integer |
| ItemVO | checked | Boolean |
| ItemVO | checkedByUserId | Long |
| ItemVO | checkedByUserName | String |
| ItemVO | checkedAt | LocalDateTime |
| ItemVO | attachmentRequired | Boolean |
| ItemVO | attachments | List<AttachmentVO> |
| AttachmentVO | id | Long |
| AttachmentVO | infraFileId | Long |
| AttachmentVO | fileUrl | String |
| AttachmentVO | originalName | String |
| AttachmentVO | contentType | String |
| AttachmentVO | fileSize | Long |
| AttachmentVO | uploadedByUserId | Long |
| AttachmentVO | uploadedByUserName | String |
| AttachmentVO | uploadedAt | LocalDateTime |
| RouteVO | id | Long |
| RouteVO | optionKey | String |
| RouteVO | departmentId | Long |
| RouteVO | departmentName | String |
| RouteVO | assigneeType | String |
| RouteVO | assigneeTypeLabel | String |
| RouteVO | selected | Boolean |
| RouteVO | assigneeUserId | Long |
| RouteVO | assigneeUserName | String |
| RouteVO | sort | Integer |
| ClassAssignmentVO | orderItemId | Long |
| ClassAssignmentVO | productId | Long |
| ClassAssignmentVO | productName | String |
| ClassAssignmentVO | specs | List<cn.iocoder.yudao.module.zsjos.controller.admin.product.vo.ProductSpecVO> |
| ClassAssignmentVO | categoryId | Long |
| ClassAssignmentVO | categoryName | String |
| ClassAssignmentVO | categoryPath | String |
| ClassAssignmentVO | classId | Long |
| ClassAssignmentVO | classNo | String |
| ClassAssignmentVO | className | String |
| ClassAssignmentVO | systemClass | Boolean |
| ClassAssignmentVO | homeroomUserId | Long |
| ClassAssignmentVO | homeroomUserName | String |
| ClassAssignmentVO | errorCode | String |
| ClassAssignmentVO | errorReason | String |

### MyStudentPageReqVO

[MyStudentPageReqVO.java](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/registration/vo/MyStudentPageReqVO.java)

| 声明类型（内嵌类型按源码出现顺序） | 属性 | Java类型 |
|---|---|---|
| MyStudentPageReqVO | readScope | String |
| MyStudentPageReqVO | targetUserId | Long |
| MyStudentPageReqVO | keyword | String |
| MyStudentPageReqVO | serviceStatus | String |
| MyStudentPageReqVO | classId | Long |
| MyStudentPageReqVO | advancedFilter | AdvancedFilterGroupReqVO |

### MyStudentRespVO

[MyStudentRespVO.java](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/registration/vo/MyStudentRespVO.java)

| 声明类型（内嵌类型按源码出现顺序） | 属性 | Java类型 |
|---|---|---|
| MyStudentRespVO | personId | Long |
| MyStudentRespVO | personNo | String |
| MyStudentRespVO | leadId | Long |
| MyStudentRespVO | leadNo | String |
| MyStudentRespVO | name | String |
| MyStudentRespVO | mobile | String |
| MyStudentRespVO | wechatId | String |
| MyStudentRespVO | activatedAt | LocalDateTime |
| MyStudentRespVO | services | List<ServiceVO> |
| ServiceVO | serviceRelationId | Long |
| ServiceVO | leadId | Long |
| ServiceVO | leadNo | String |
| ServiceVO | orderId | Long |
| ServiceVO | orderNo | String |
| ServiceVO | orderItemId | Long |
| of | classId | Long |
| of | className | String |
| of | courseName | String |
| of | skuName | String |
| of | categoryPath | List<String> |
| of | attributeValues | List<String> |
| of | specs | List<cn.iocoder.yudao.module.zsjos.controller.admin.product.vo.ProductSpecVO> |
| of | productSnapshot | String |
| of | status | String |
| of | activatedAt | LocalDateTime |
| of | acceptanceStatus | String |
| of | acceptedAt | LocalDateTime |
| of | version | Integer |
| of | owner | Boolean |
| of | ownerUserId | Long |
| of | ownerUserName | String |
| of | contentDirectorUserId | Long |
| of | contentDirectorUserName | String |
| of | careerPlannerUserId | Long |
| of | careerPlannerUserName | String |
| of | operatorUserId | Long |
| of | operatorUserName | String |
| of | directorStage | String |
| of | directorInterviewAt | LocalDateTime |

### StudentContactContextRespVO

[StudentContactContextRespVO.java](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/registration/vo/StudentContactContextRespVO.java)

| 声明类型（内嵌类型按源码出现顺序） | 属性 | Java类型 |
|---|---|---|
| StudentContactContextRespVO | serviceRelationId | Long |
| StudentContactContextRespVO | acceptanceStatus | String |
| StudentContactContextRespVO | acceptedAt | LocalDateTime |
| StudentContactContextRespVO | version | Integer |
| StudentContactContextRespVO | currentTask | CurrentTaskVO |
| StudentContactContextRespVO | firstContactChecklist | List<ChecklistItemVO> |
| StudentContactContextRespVO | quickNotes | List<String> |
| StudentContactContextRespVO | firstContactTimeoutMinutes | Integer |
| StudentContactContextRespVO | studyPlanTimeoutMinutes | Integer |
| StudentContactContextRespVO | visibleTabs | List<String> |
| StudentContactContextRespVO | availableActions | List<String> |
| StudentContactContextRespVO | ownerUserId | Long |
| StudentContactContextRespVO | ownerUserName | String |
| StudentContactContextRespVO | contentDirectorUserId | Long |
| StudentContactContextRespVO | contentDirectorUserName | String |
| StudentContactContextRespVO | careerPlannerUserId | Long |
| StudentContactContextRespVO | careerPlannerUserName | String |
| StudentContactContextRespVO | operatorUserId | Long |
| StudentContactContextRespVO | operatorUserName | String |
| StudentContactContextRespVO | directorStage | String |
| StudentContactContextRespVO | directorInterviewAt | LocalDateTime |
| StudentContactContextRespVO | defaultDirectorInterviewAt | LocalDateTime |
| StudentContactContextRespVO | directorInterviewAppointmentHours | Integer |
| StudentContactContextRespVO | directorTrialDays | Integer |
| StudentContactContextRespVO | deliveryStage | String |
| StudentContactContextRespVO | deliveryStageLabel | String |
| StudentContactContextRespVO | deliveryStages | List<DeliveryStageVO> |
| StudentContactContextRespVO | examDate | LocalDate |
| StudentContactContextRespVO | formFields | List<FormFieldVO> |
| StudentContactContextRespVO | directorForms | DirectorFormsVO |
| StudentContactContextRespVO | operatorAssignmentConflict | Boolean |
| CurrentTaskVO | id | Long |
| CurrentTaskVO | type | String |
| CurrentTaskVO | status | String |
| CurrentTaskVO | dueAt | LocalDateTime |
| CurrentTaskVO | overdue | Boolean |
| ChecklistItemVO | key | String |
| ChecklistItemVO | title | String |
| ChecklistItemVO | type | String |
| ChecklistItemVO | attachmentRequired | Boolean |
| DeliveryStageVO | code | String |
| DeliveryStageVO | label | String |
| DeliveryStageVO | status | String |
| DeliveryStageVO | current | Boolean |
| DeliveryStageVO | available | Boolean |
| FormFieldVO | key | String |
| FormFieldVO | title | String |
| FormFieldVO | type | String |
| FormFieldVO | required | Boolean |
| FormFieldVO | sort | Integer |
| FormFieldVO | description | String |
| FormFieldVO | dictType | String |
| FormFieldVO | multiple | Boolean |
| FormFieldVO | enabled | Boolean |
| FormFieldVO | systemField | Boolean |
| FormFieldVO | minSelections | Integer |
| FormFieldVO | maxSelections | Integer |
| FormFieldVO | minValue | Integer |
| FormFieldVO | maxValue | Integer |
| FormFieldVO | maxLength | Integer |
| FormFieldVO | group | String |
| DirectorFormsVO | precheck | DirectorFormVO |
| DirectorFormsVO | interview | DirectorFormVO |
| DirectorFormVO | version | Integer |
| DirectorFormVO | state | String |
| DirectorFormVO | configId | Long |
| DirectorFormVO | configVersion | Integer |
| DirectorFormVO | templateId | Long |
| DirectorFormVO | templateVersionId | Long |
| DirectorFormVO | templateVersionNo | Integer |
| DirectorFormVO | fields | List<FormFieldVO> |
| DirectorFormVO | values | Map<String, Object> |
| DirectorFormVO | dictSnapshots | Map<String, Object> |
| DirectorFormVO | savedAt | LocalDateTime |
| DirectorFormVO | savedByUserId | Long |
| DirectorFormVO | submittedAt | LocalDateTime |
| DirectorFormVO | interviewAt | LocalDateTime |

### StudentContactRecordRespVO

[StudentContactRecordRespVO.java](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/registration/vo/StudentContactRecordRespVO.java)

| 声明类型（内嵌类型按源码出现顺序） | 属性 | Java类型 |
|---|---|---|
| StudentContactRecordRespVO | id | Long |
| StudentContactRecordRespVO | contactType | String |
| StudentContactRecordRespVO | successful | Boolean |
| StudentContactRecordRespVO | unsuccessfulReasonValue | String |
| StudentContactRecordRespVO | unsuccessfulReasonLabel | String |
| StudentContactRecordRespVO | remark | String |
| StudentContactRecordRespVO | attachmentFileIds | List<Long> |
| StudentContactRecordRespVO | completedChecklistKeys | List<String> |
| StudentContactRecordRespVO | nextContactAt | LocalDateTime |
| StudentContactRecordRespVO | operatorUserId | Long |
| StudentContactRecordRespVO | operatorUserName | String |
| StudentContactRecordRespVO | submittedAt | LocalDateTime |
| StudentContactRecordRespVO | deliveryStage | String |
| StudentContactRecordRespVO | deliveryData | String |

### MediaStudentDetailRespVO

[MediaStudentDetailRespVO.java](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/registration/vo/MediaStudentDetailRespVO.java)

| 声明类型（内嵌类型按源码出现顺序） | 属性 | Java类型 |
|---|---|---|
| MediaStudentDetailRespVO | student | MyStudentRespVO |
| MediaStudentDetailRespVO | accounts | List<AccountVO> |
| MediaStudentDetailRespVO | positioningCards | List<PositioningVO> |
| MediaStudentDetailRespVO | positioningDrafts | List<PositioningVO> |
| MediaStudentDetailRespVO | contents | List<ContentVO> |
| MediaStudentDetailRespVO | productionTickets | List<TicketVO> |
| MediaStudentDetailRespVO | operationTimeline | List<OperationVO> |
| MediaStudentDetailRespVO | studentTaskLine | List<TaskStageVO> |
| MediaStudentDetailRespVO | taskLine | List<TaskStageVO> |
| MediaStudentDetailRespVO | pendingStats | PendingStatsVO |
| AccountVO | id | Long |
| AccountVO | accountNo | String |
| AccountVO | nickname | String |
| AccountVO | platformLabel | String |
| AccountVO | stage | String |
| AccountVO | stageLabelSnapshot | String |
| AccountVO | currentStatusValue | String |
| AccountVO | currentStatusLabelSnapshot | String |
| AccountVO | primaryProblems | List<MediaAccountMaintenanceProblemVO> |
| AccountVO | executionMeasureValue | String |
| AccountVO | executionMeasureLabelSnapshot | String |
| AccountVO | adjustmentDirection | String |
| AccountVO | maintenanceStartDate | LocalDate |
| AccountVO | maintenanceEndDate | LocalDate |
| AccountVO | runStatus | String |
| AccountVO | version | Integer |
| AccountVO | lastActivityAt | LocalDateTime |
| AccountVO | availableActions | List<String> |
| AccountVO | detailSnapshots | List<MediaAccountDetailSnapshotVO> |
| AccountVO | taskLine | List<TaskStageVO> |
| PositioningVO | id | Long |
| PositioningVO | accountId | Long |
| PositioningVO | cardNo | String |
| PositioningVO | submissionId | Long |
| PositioningVO | status | String |
| PositioningVO | versionNo | Integer |
| PositioningVO | submissionNo | Integer |
| PositioningVO | submittedAt | LocalDateTime |
| PositioningVO | studentDecidedAt | LocalDateTime |
| PositioningVO | studentDecision | String |
| PositioningVO | studentDecisionComment | String |
| PositioningVO | latestRound | Boolean |
| PositioningVO | effective | Boolean |
| PositioningVO | current | Boolean |
| PositioningVO | professionalRisk | Boolean |
| PositioningVO | version | Integer |
| PositioningVO | lastActivityAt | LocalDateTime |
| PositioningVO | availableActions | List<String> |
| ContentVO | id | Long |
| ContentVO | accountId | Long |
| ContentVO | contentNo | String |
| ContentVO | title | String |
| ContentVO | status | String |
| ContentVO | currentVersionNo | Integer |
| ContentVO | publishedAt | LocalDateTime |
| ContentVO | version | Integer |
| ContentVO | lastActivityAt | LocalDateTime |
| ContentVO | availableActions | List<String> |
| TicketVO | id | Long |
| TicketVO | accountId | Long |
| TicketVO | ticketNo | String |
| TicketVO | status | String |
| TicketVO | deadlineAt | LocalDateTime |
| TicketVO | revisionCount | Integer |
| TicketVO | lastActivityAt | LocalDateTime |
| OperationVO | key | String |
| OperationVO | type | String |
| OperationVO | title | String |
| OperationVO | detail | String |
| OperationVO | operatorName | String |
| OperationVO | occurredAt | LocalDateTime |
| TaskStageVO | key | String |
| TaskStageVO | label | String |
| TaskStageVO | status | String |
| TaskStageVO | detail | String |
| PendingStatsVO | accountCount | Integer |
| PendingStatsVO | positioningCount | Integer |
| PendingStatsVO | contentCount | Integer |
| PendingStatsVO | productionCount | Integer |

### CashbackPageReqVO

[CashbackPageReqVO.java](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/cashback/vo/CashbackPageReqVO.java)

| 声明类型（内嵌类型按源码出现顺序） | 属性 | Java类型 |
|---|---|---|
| CashbackPageReqVO | type | String |
| CashbackPageReqVO | status | String |
| CashbackPageReqVO | keyword | String |
| CashbackPageReqVO | beneficiaryUserId | Long |
| CashbackPageReqVO | partnerId | Long |
| CashbackPageReqVO | amountMin | java.math.BigDecimal |
| CashbackPageReqVO | amountMax | java.math.BigDecimal |
| CashbackPageReqVO | advancedFilter | cn.iocoder.yudao.module.zsjos.controller.admin.advancedfilter.vo.AdvancedFilterGroupReqVO |
| CashbackPageReqVO | orderNo | String |
| CashbackPageReqVO | productName | String |
| CashbackPageReqVO | generatedAtFrom | java.time.LocalDateTime |
| CashbackPageReqVO | generatedAtTo | java.time.LocalDateTime |
| CashbackPageReqVO | availableAtFrom | java.time.LocalDateTime |
| CashbackPageReqVO | availableAtTo | java.time.LocalDateTime |
| CashbackPageReqVO | settledAtFrom | java.time.LocalDateTime |
| CashbackPageReqVO | settledAtTo | java.time.LocalDateTime |

### CashbackRespVO

[CashbackRespVO.java](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/cashback/vo/CashbackRespVO.java)

| 声明类型（内嵌类型按源码出现顺序） | 属性 | Java类型 |
|---|---|---|
| CashbackRespVO | id | Long |
| CashbackRespVO | cashbackNo | String |
| CashbackRespVO | type | String |
| CashbackRespVO | status | String |
| CashbackRespVO | beneficiaryUserId | Long |
| CashbackRespVO | leadId | Long |
| CashbackRespVO | leadNo | String |
| CashbackRespVO | orderId | Long |
| CashbackRespVO | orderItemId | Long |
| CashbackRespVO | productRefSnapshot | String |
| CashbackRespVO | productNameSnapshot | String |
| CashbackRespVO | baseAmount | BigDecimal |
| CashbackRespVO | rateSnapshot | BigDecimal |
| CashbackRespVO | amount | BigDecimal |
| CashbackRespVO | observationDaysSnapshot | Integer |
| CashbackRespVO | generatedAt | LocalDateTime |
| CashbackRespVO | availableAt | LocalDateTime |
| CashbackRespVO | settledAt | LocalDateTime |
| CashbackRespVO | cancelledAt | LocalDateTime |
| CashbackRespVO | cancelReason | String |

### WithdrawalPageReqVO

[WithdrawalPageReqVO.java](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/withdrawal/vo/WithdrawalPageReqVO.java)

| 声明类型（内嵌类型按源码出现顺序） | 属性 | Java类型 |
|---|---|---|
| WithdrawalPageReqVO | readScope | String |
| WithdrawalPageReqVO | targetUserId | Long |
| WithdrawalPageReqVO | status | String |
| WithdrawalPageReqVO | keyword | String |
| WithdrawalPageReqVO | applicantUserId | Long |
| WithdrawalPageReqVO | partnerId | Long |
| WithdrawalPageReqVO | amountMin | java.math.BigDecimal |
| WithdrawalPageReqVO | amountMax | java.math.BigDecimal |
| WithdrawalPageReqVO | advancedFilter | cn.iocoder.yudao.module.zsjos.controller.admin.advancedfilter.vo.AdvancedFilterGroupReqVO |
| WithdrawalPageReqVO | withdrawalNo | String |
| WithdrawalPageReqVO | bankTransactionNo | String |
| WithdrawalPageReqVO | submittedAtFrom | java.time.LocalDateTime |
| WithdrawalPageReqVO | submittedAtTo | java.time.LocalDateTime |
| WithdrawalPageReqVO | reviewedAtFrom | java.time.LocalDateTime |
| WithdrawalPageReqVO | reviewedAtTo | java.time.LocalDateTime |
| WithdrawalPageReqVO | paidAtFrom | java.time.LocalDateTime |
| WithdrawalPageReqVO | paidAtTo | java.time.LocalDateTime |

### WithdrawalRespVO

[WithdrawalRespVO.java](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/withdrawal/vo/WithdrawalRespVO.java)

| 声明类型（内嵌类型按源码出现顺序） | 属性 | Java类型 |
|---|---|---|
| WithdrawalRespVO | id | Long |
| WithdrawalRespVO | withdrawalNo | String |
| WithdrawalRespVO | applicantUserId | Long |
| WithdrawalRespVO | status | String |
| WithdrawalRespVO | verificationStatus | String |
| WithdrawalRespVO | applicationAmount | BigDecimal |
| WithdrawalRespVO | availableBalanceSnapshot | BigDecimal |
| WithdrawalRespVO | accountNameSnapshot | String |
| WithdrawalRespVO | maskedCardNumber | String |
| WithdrawalRespVO | cardNumber | String |
| WithdrawalRespVO | bankNameSnapshot | String |
| WithdrawalRespVO | branchNameSnapshot | String |
| WithdrawalRespVO | processInstanceId | String |
| WithdrawalRespVO | submittedAt | LocalDateTime |
| WithdrawalRespVO | approvedAmount | BigDecimal |
| WithdrawalRespVO | reviewedByUserId | Long |
| WithdrawalRespVO | reviewedAt | LocalDateTime |
| WithdrawalRespVO | rejectionReason | String |
| WithdrawalRespVO | bankTransactionNo | String |
| WithdrawalRespVO | proofFileId | Long |
| WithdrawalRespVO | proofUrl | String |
| WithdrawalRespVO | payoutRemark | String |
| WithdrawalRespVO | paidByUserId | Long |
| WithdrawalRespVO | paidAt | LocalDateTime |
| WithdrawalRespVO | items | List<Item> |
| Item | cashbackId | Long |
| Item | amount | BigDecimal |

## 5. 导出实际取值矩阵

按Provider.toRow调用读取实际属性，不从Excel列标题推断业务含义；导出有该列不表示可作为过滤条件。技术ID不作为客户可见业务编号。

| 导出类型 | 查询VO | 实际取值属性 | 证据 |
|---|---|---|---|
| cashback | CashbackPageReqVO | id, cashbackNo, type, status, beneficiaryUserId, leadNo, orderId, productNameSnapshot, baseAmount, rateSnapshot, amount, availableAt, settledAt | [CashbackExportTypeProvider.java](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/export/provider/CashbackExportTypeProvider.java) |
| finance_order | FinanceOrderExportReqVO | orderNo, orderType, status, buyerName, studentName, studentMobile, studentWechatId, region, courseSummary, totalAmount, customerPaidAt, paymentMethod, formalSalesName, formalOwnerIdentityLabel, submitterName, submittedAt, effectiveAt, approvalRoundNo, registrationStatus, registrationReviewer, registrationReviewedAt, financeStatus, financeReviewer, financeReviewedAt, finalReason | [FinanceOrderExportTypeProvider.java](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/export/provider/FinanceOrderExportTypeProvider.java) |
| lead | LeadManagementPageReqVO | leadNo, submittedName, submittedMobile, submittedWechatId, sourceChannel, leadCategory, status, assignmentStatus, ownerUserName, submittedAt, intendedProducts | [LeadExportTypeProvider.java](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/export/provider/LeadExportTypeProvider.java) |
| order | SalesOrderMyPageReqVO | id, orderNo, orderType, status, studentName, studentMobile, totalAmount, submittedAt, effectiveAt | [SalesOrderExportTypeProvider.java](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/export/provider/SalesOrderExportTypeProvider.java) |
| withdrawal | WithdrawalPageReqVO | id, withdrawalNo, applicantUserId, status, verificationStatus, applicationAmount, approvedAmount, accountNameSnapshot, maskedCardNumber, bankNameSnapshot, submittedAt, reviewedAt, paidAt, bankTransactionNo | [WithdrawalExportTypeProvider.java](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/export/provider/WithdrawalExportTypeProvider.java) |

统一导出基类额外写入的导出说明/导出时间属于技术元数据；业务字段以本表为准。

## 6. 元数据逐字段分类（2026-09-23）

全部字段均返回supportedScenes、supportedPages、permission、dataScope、sensitive、sensitivity、sortable、deprecated、optionSourceType和declaredOptionSource。具体定义见[元数据契约](advanced-filter-metadata.md)。场景仍取第3节实际绑定，页面按场景已知接入关系展开。

| fieldKey | sensitivity | sensitive | sortable | deprecated |
|---|---|---|---|---|
| appeal.roundNo | standard | false | false | false |
| appeal.reviewStage | standard | false | false | false |
| appeal.status | standard | false | false | false |
| appeal.applicantUserId | personal | true | false | false |
| appeal.reviewerUserId | personal | true | false | false |
| appeal.reason | free_text | true | false | false |
| appeal.decisionReason | free_text | true | false | false |
| appeal.submittedAt | standard | false | false | false |
| appeal.decidedAt | standard | false | false | false |
| appeal.hasEvidence | standard | false | false | false |
| person.name | personal | true | false | false |
| person.mobile | personal | true | false | false |
| person.wechatId | personal | true | false | false |
| person.identityStatus | standard | false | false | false |
| review.submittedName | personal | true | false | false |
| review.submittedMobile | personal | true | false | false |
| review.submittedWechatId | personal | true | false | false |
| review.status | standard | false | false | false |
| review.resultType | standard | false | false | false |
| review.duplicateFlag | standard | false | false | false |
| review.duplicateResult | standard | false | false | false |
| review.submitterUserId | personal | true | false | false |
| review.reviewerUserId | personal | true | false | false |
| review.selectedSalesUserId | personal | true | false | false |
| review.submissionSource | standard | false | false | false |
| review.matchRules | standard | false | false | false |
| review.opinion | free_text | true | false | false |
| review.submittedAt | standard | false | false | false |
| review.reviewedAt | standard | false | false | false |
| review.hasAttachments | standard | false | false | false |
| lead.leadNo | standard | false | false | false |
| lead.name | personal | true | false | false |
| lead.mobile | personal | true | false | false |
| lead.wechatId | personal | true | false | false |
| lead.status | standard | false | false | false |
| lead.assignmentStatus | standard | false | false | false |
| lead.sourceType | standard | false | false | false |
| lead.ownerIdentity | standard | false | false | false |
| lead.sourceChannel | standard | false | false | false |
| lead.category | standard | false | false | false |
| lead.salesStage | standard | false | false | false |
| lead.ownerDeptId | personal | true | false | false |
| lead.qualificationStatus | standard | false | false | false |
| lead.dealStatus | standard | false | false | false |
| lead.qualifiedAt | standard | false | false | false |
| lead.convertedAt | standard | false | false | false |
| lead.sourceUserId | personal | true | false | false |
| lead.ownerUserId | personal | true | false | false |
| lead.submittedAt | standard | false | false | false |
| lead.lastFollowUpAt | standard | false | false | false |
| lead.nextFollowUpAt | standard | false | false | false |
| lead.ownershipStartedAt | standard | false | false | false |
| lead.remark | free_text | true | false | false |
| opportunity.expectedProduct | standard | false | false | false |
| opportunity.status | standard | false | false | false |
| opportunity.lostReason | free_text | true | false | false |
| order.formalOwnerIdentity | standard | false | false | false |
| order.orderNo | standard | false | false | false |
| order.status | standard | false | false | false |
| order.type | standard | false | false | false |
| order.buyerName | personal | true | false | false |
| order.studentName | personal | true | false | false |
| order.studentMobile | personal | true | false | false |
| order.studentWechatId | personal | true | false | false |
| order.submitterUserId | personal | true | false | false |
| order.formalSalesUserId | personal | true | false | false |
| order.totalAmount | financial | true | false | false |
| order.studentNature | personal | true | false | false |
| order.region | personal | true | false | false |
| order.feeMode | financial | true | false | false |
| order.paymentMethod | financial | true | false | false |
| order.customerPaidAt | financial | true | false | false |
| order.classType | standard | false | false | false |
| order.servicePeriod | standard | false | false | false |
| order.studentSource | standard | false | false | false |
| order.submittedAt | standard | false | false | false |
| order.effectiveAt | standard | false | false | false |
| order.remark | free_text | true | false | false |
| order.specialRequirements | free_text | true | false | false |
| order.materialDelivery | free_text | true | false | false |
| order.hasVoucher | financial | true | false | false |
| orderItem.product | standard | false | false | false |
| orderItem.payableAmount | financial | true | false | false |
| registration.status | standard | false | false | false |
| registration.plannerUserId | personal | true | false | false |
| registration.approvedAt | standard | false | false | false |
| registration.completedAt | standard | false | false | false |
| registration.cancelledAt | standard | false | false | false |
| registration.cancelReason | free_text | true | false | false |
| registration.checklistTitle | standard | false | false | false |
| registration.checklistStatus | standard | false | false | false |
| registration.hasChecklistAttachment | standard | false | false | false |
| registration.routeDepartment | standard | false | false | false |
| registration.routeAssignee | personal | true | false | false |
| service.status | standard | false | false | false |
| service.ownerUserId | personal | true | false | false |
| service.activatedAt | standard | false | false | false |
| service.pausedAt | standard | false | false | false |
| service.completedAt | standard | false | false | false |
| service.terminatedAt | standard | false | false | false |
| service.pauseReason | free_text | true | false | false |
| service.terminationReason | free_text | true | false | false |
| subordinate.name | personal | true | false | false |
| subordinate.username | personal | true | false | false |
| subordinate.mobile | personal | true | false | false |
| subordinate.accountStatus | standard | false | false | false |
| subordinate.presence | standard | false | false | false |
| subordinate.accepting | standard | false | false | false |
| subordinate.eligible | standard | false | false | false |
| subordinate.newcomerPoolStatus | standard | false | false | false |
| subordinate.todayPendingCount | standard | false | false | false |
| subordinate.todayFollowUpStatus | standard | false | false | false |
| subordinate.firstFollowTimeoutCount | standard | false | false | false |
| subordinate.suspendedLeadCount | standard | false | false | false |
| subordinate.validLeadCount | standard | false | false | false |
| subordinate.convertedLeadCount | standard | false | false | false |
| subordinate.effectiveOrderCount | standard | false | false | false |
| subordinate.effectiveOrderAmount | financial | true | false | false |
| duration.diff | standard | false | false | false |

## 财务场景新增目录（2026-09-23 查询契约修正）

原 118 项目录为盘点基线；现新增返现 15 项、提现 17 项，共 150 项静态字段。两场景各追加一个动态 `duration.diff`，返回目录数分别为 16、18。原场景字段保持不变。

| fieldKey | 名称 | 类型 | 分组 | 敏感性 | 定义来源 |
|---|---|---|---|---|---|
| `cashback.cashbackNo` | 返现编号 | text | 身份与联系 | standard | [CashbackFilterFields](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/CashbackFilterFields.java) |
| `cashback.productName` | 产品名称快照 | text | 产品与服务 | standard | [CashbackFilterFields](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/CashbackFilterFields.java) |
| `cashback.baseAmount` | 返现基数（元） | number | 金额与付款 | financial | [CashbackFilterFields](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/CashbackFilterFields.java) |
| `cashback.rateSnapshot` | 返现比例（小数） | number | 金额与付款 | financial | [CashbackFilterFields](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/CashbackFilterFields.java) |
| `cashback.amount` | 返现金额（元） | number | 金额与付款 | financial | [CashbackFilterFields](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/CashbackFilterFields.java) |
| `cashback.observationDaysSnapshot` | 观察期（天） | number | 时间 | standard | [CashbackFilterFields](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/CashbackFilterFields.java) |
| `cashback.generatedAt` | 返现生成时间 | date | 时间 | standard | [CashbackFilterFields](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/CashbackFilterFields.java) |
| `cashback.availableAt` | 可提现时间 | date | 时间 | standard | [CashbackFilterFields](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/CashbackFilterFields.java) |
| `cashback.settledAt` | 结算时间 | date | 时间 | standard | [CashbackFilterFields](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/CashbackFilterFields.java) |
| `cashback.cancelledAt` | 取消时间 | date | 时间 | standard | [CashbackFilterFields](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/CashbackFilterFields.java) |
| `cashback.cancelReason` | 取消原因 | text | 补充信息 | free_text | [CashbackFilterFields](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/CashbackFilterFields.java) |
| `cashback.type` | 返现类型 | select | 状态与进度 | standard | [CashbackFilterFields](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/CashbackFilterFields.java) |
| `cashback.status` | 返现状态 | select | 状态与进度 | standard | [CashbackFilterFields](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/CashbackFilterFields.java) |
| `cashback.leadNo` | 客资编号 | text | 身份与联系 | standard | [CashbackFilterFields](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/CashbackFilterFields.java) |
| `cashback.orderNo` | 订单号 | text | 身份与联系 | standard | [CashbackFilterFields](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/CashbackFilterFields.java) |
| `withdrawal.withdrawalNo` | 提现单号 | text | 身份与联系 | standard | [WithdrawalFilterFields](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/WithdrawalFilterFields.java) |
| `withdrawal.applicationAmount` | 申请金额（元） | number | 金额与付款 | financial | [WithdrawalFilterFields](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/WithdrawalFilterFields.java) |
| `withdrawal.approvedAmount` | 批准金额（元） | number | 金额与付款 | financial | [WithdrawalFilterFields](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/WithdrawalFilterFields.java) |
| `withdrawal.availableBalanceSnapshot` | 申请时可用余额（元） | number | 金额与付款 | financial | [WithdrawalFilterFields](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/WithdrawalFilterFields.java) |
| `withdrawal.accountNameSnapshot` | 收款账户名 | text | 身份与联系 | personal | [WithdrawalFilterFields](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/WithdrawalFilterFields.java) |
| `withdrawal.bankNameSnapshot` | 开户银行 | text | 补充信息 | personal | [WithdrawalFilterFields](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/WithdrawalFilterFields.java) |
| `withdrawal.branchNameSnapshot` | 开户支行 | text | 补充信息 | personal | [WithdrawalFilterFields](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/WithdrawalFilterFields.java) |
| `withdrawal.bankTransactionNo` | 银行流水号 | text | 金额与付款 | financial | [WithdrawalFilterFields](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/WithdrawalFilterFields.java) |
| `withdrawal.submittedAt` | 提交时间 | date | 时间 | standard | [WithdrawalFilterFields](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/WithdrawalFilterFields.java) |
| `withdrawal.reviewedAt` | 审核时间 | date | 时间 | standard | [WithdrawalFilterFields](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/WithdrawalFilterFields.java) |
| `withdrawal.paidAt` | 打款时间 | date | 时间 | standard | [WithdrawalFilterFields](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/WithdrawalFilterFields.java) |
| `withdrawal.cancelledAt` | 撤销时间 | date | 时间 | standard | [WithdrawalFilterFields](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/WithdrawalFilterFields.java) |
| `withdrawal.rejectionReason` | 驳回原因 | text | 补充信息 | free_text | [WithdrawalFilterFields](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/WithdrawalFilterFields.java) |
| `withdrawal.payoutRemark` | 打款备注 | text | 补充信息 | free_text | [WithdrawalFilterFields](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/WithdrawalFilterFields.java) |
| `withdrawal.status` | 提现状态 | select | 状态与进度 | standard | [WithdrawalFilterFields](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/WithdrawalFilterFields.java) |
| `withdrawal.verificationStatus` | 核验状态 | select | 状态与进度 | standard | [WithdrawalFilterFields](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/WithdrawalFilterFields.java) |
| `withdrawal.hasProof` | 打款凭证 | select | 补充信息 | financial | [WithdrawalFilterFields](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/WithdrawalFilterFields.java) |

新增字段完整继承既有元数据契约：scene/page 分别为 cashback/cashback、withdrawal/withdrawal，授权和数据范围继承实际查询。状态/类型/核验使用服务端业务状态常量；有无凭证是文件引用存在性；金额以元、比例以小数、观察期以天计。产品与银行字段筛选持久化快照，不回查现时标签。银行卡号不加入通用目录，避免在脱敏查询中通过条件探测完整卡号。受益人/申请人等人员高级选项仍待明确可见范围并接入权威人员 API，不以全员列表替代。

## 选项权威来源收口（2026-09-23）

- 客资主状态、分配状态、申诉状态改为既有 `zsjos_lead_status`、`zsjos_lead_assignment_status`、`zsjos_lead_appeal_status` 字典。依据：现有初始化/迁移及字典快照中的定义。两端加载 System `dict-data/simple-list`，采用返回的值、标签和顺序，失败不提供静态替代。没有新增或同步字典数据。
- 返现类型/状态和提现状态/核验来自 CashbackConstants、WithdrawalConstants；普通筛选和高级筛选都读取后台目录，列表状态与返现类型显示也使用目录标签。无选项时显示空列表，加载失败禁用选择并提供重试。
- 订单状态/类型引用 SalesOrderConstants；客资来源、归属身份、有效性、机会状态、复核结果和申诉阶段引用 LeadConstants；报名状态引用 RegistrationConstants；账号启停引用 CommonStatusEnum。文案由后端目录提供，客户端不复制这些选项。
- 服务关系四态来自业务契约 `docs/business/lead-order-state-machine.md` §8.3/§9.11，保持 active/paused/completed/terminated；不能将文档提出但初始化尚未落地的字典类型冒充已有数据源。
- Person 正式学员选项由错误的 active 修正为 student，与 RegistrationServiceImpl.complete 的写入一致；潜在学员 lead 与 LeadSubmissionServiceImpl 一致。旧模板中 active 不自动改写，需重新选择正式学员；不回填历史数据。
- 附件存在性、清单完成性、时间单位和下属销售聚合布尔/计算状态属于后端查询/聚合契约，不是管理员业务字典。它们的判断依据仍为对应 SQL 表达式或 SubordinateSalesServiceImpl 投影。
- 人员/部门来源保留服务端场景范围。Admin 筛选器及模板管理删除 visible-users → 系统简易用户列表的回退；未知来源直接进入选项加载错误，空的已解析列表保持为空。

`optionSourceType=business_contract` 是分类信息，不是来源验收证明；以上依赖和回归测试才是本次来源核对依据。字典运行时是否存在、启用与具体选项内容由服务端决定，本次未查询或修改真实数据库。


## 本轮缺项补齐（30 项）

保留现有界面；以下字段通过原 catalog 自动进入两端条件编辑器。Lead/Order 字段复用已有五场景关联与同一行分组，申诉仅用于 lead_appeal，下属指标仅用于 subordinate_sales。

| fieldKey | 名称 | 类型工厂 |
|---|---|---|
| `lead.lastActivityAt` | 最近活动时间 | date |
| `lead.currentAssignmentFirstFollowUpAt` | 本次分配首次跟进时间 | date |
| `lead.currentAssignmentFirstFollowUpDeadlineAt` | 本次分配首跟截止时间 | date |
| `lead.qualificationStartedAt` | 当前有效性判定开始时间 | date |
| `lead.qualificationDeadlineAt` | 当前有效性判定截止时间 | date |
| `lead.suspendedAt` | 挂起时间 | date |
| `lead.appealDeadlineAt` | 申诉截止时间 | date |
| `lead.closedAt` | 关闭时间 | date |
| `lead.publicPoolAt` | 进入抢单池时间 | date |
| `lead.validDescription` | 有效判定说明 | text |
| `lead.invalidDescription` | 无效判定说明 | text |
| `lead.closeReason` | 关闭原因 | text |
| `lead.invalidReason` | 无效原因 | selectSource |
| `order.agreedExamTime` | 商定考试时间（文本） | text |
| `order.giftShippingAddress` | 礼品邮寄地址 | text |
| `order.repurchaseReason` | 复购原因 | text |
| `order.terminationReason` | 订单终止原因 | text |
| `order.terminatedAt` | 订单终止时间 | date |
| `appeal.invalidReasonSnapshot` | 原判无效原因（历史标签） | text |
| `appeal.invalidDescriptionSnapshot` | 原判无效说明 | text |
| `subordinate.todayAssignedCount` | 今日分配数 | number |
| `subordinate.todayMissedCount` | 今日漏接数 | number |
| `subordinate.todayReceivedCount` | 今日接单数 | number |
| `subordinate.todayQualifiedCount` | 今日判定有效数 | number |
| `subordinate.todayFollowUpRecordCount` | 今日跟进记录数 | number |
| `subordinate.todayOrderAmount` | 今日订单金额 | number |
| `subordinate.pendingQualificationCount` | 待判定数 | number |
| `subordinate.todayFollowUpTotalCount` | 今日应跟进数 | number |
| `subordinate.todayFollowUpRemainingCount` | 今日剩余跟进数 | number |
| `subordinate.canReceiveNewLeads` | 当前可接新客资 | select |

无效原因选项来自 `zsjos_lead_invalid_reason`（V014 已定义类型）；申诉原判原因是存储的历史标签，使用文本查询，不重新解析当前字典。商定考试时间按现有字符串契约查询。下属指标取现有授权行计算结果，在分页前过滤；未重算统计窗口。


## 产品/SKU 实体筛选（新增 4 项）

| fieldKey | 业务含义 | 场景 | 查询列 |
|---|---|---|---|
| lead.intendedProductRef | 原始意向产品 | lead | lip.product_ref |
| lead.intendedSkuRef | 原始意向 SKU | lead | lip.sku_ref |
| orderItem.productRef | 成交产品 | lead/order/lead_appeal/registration/student | oi.product_ref |
| orderItem.skuRef | 成交 SKU | 同上 | oi.sku_ref |

四项均为 select，支持 in/not_in/is_empty/is_not_empty。选项由已有 ZsjosProductSkuService.getLeadCatalog 提供启用产品/SKU，目录接口仅返回引用和名称，不返回价格；SKU 标签带产品名避免同名混淆。optionSourceType=business_api，declaredOptionSource=product-catalog:spu/sku，服务端解析后 optionSource=null，空列表不触发前端回退。业务筛选权限沿用 catalog 对应 scene 的服务端权限，不授予商品管理权限。

AND 的正向产品/SKU条件必须命中同一条意向记录或订单明细；负向条件沿用 NOT EXISTS（排除有此明细的对象）。关联保留 tenant/deleted 与学员可见服务范围。历史停用/删除产品不在当前启用选项中，旧条件保存的稳定引用仍可查询，不重新解释为新产品；当前选项不等于完整历史商品档案。

审批轮次和动态字段的待确认实现口径：审批同时区分当前轮次（order.current_approval_round_id）与任意历史轮次，同组条件不得跨轮匹配；动态字段由模板显式开启，键包含模板版本及字段键，仅已提交的业务快照参与查询，草稿不参与。此段为待确认方案，尚非已实现功能；BPM任务审核人/结论不得从业务轮次字段臆造。
