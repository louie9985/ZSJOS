# 中视简高级筛选字段盘点矩阵

盘点日期：2026-09-23。基准：`main`，HEAD `79eab0a01a208c9aa562557b95fd7eda5436546b`，包含盘点时已有未提交修改。本文件保留盘点基线；2026-09-23 后续修正已在“已落实”段落记录。

## 1. 阅读顺序与统计口径

- 本文：页面与业务字段对照、缺口优先级、数据来源及查询边界。
- [现有字段逐项目录](advanced-filter-field-catalog.md)：逐项记录全部注册字段的 fieldKey、名称、类型、操作符、来源、场景与源码行号；附查询 VO 和导出字段清单。
- [页面接入扫描矩阵](advanced-filter-page-coverage.md)：Workbench、Admin、H5 页面源文件的筛选接入情况和可复核入口，包含未接入统一组件的页面。

“有字段”分为四层，不能互相替代：页面控件、请求参数、后端实际条件、统一高级筛选目录。普通筛选已支持的参数不计作后端完全缺失；详情/导出可见也不代表已经支持查询。目录字段数是静态注册能力上限，不是任意账号实际可见数量，也不是线上验证结果。

范围：中视简 ZSJOS 业务页面，以及 Workbench 的审批、消息、日历、资产等页面源码；Admin 重点覆盖 `views/zsjos`。上游所有 System/Infra/BPM/EAM 管理页面不属于这次 ZSJOS 业务字段逐项盘点。目录中未接入的页面仍列出，不把“无统一组件”写成“无任何筛选”。兼容旧页、配置页、录入页不计为必须新增高级筛选的需求。真实菜单发布情况仍由后端决定。

状态说明：

| 标记 | 含义 |
|---|---|
| A 已登记 | 后端统一字段目录有此字段及场景绑定；不代表浏览器/API已验收 |
| B 普通查询 | 页面或 VO 有普通参数；注明是否已追至 Mapper |
| C 未登记 | 列表/详情/导出存在业务字段，但统一目录无对应字段 |
| D 断链/错配 | 前端场景、目录权限或实际查询未接通 |
| E 语义待定 | 有关联/动态/敏感数据，必须先明确查询口径，不能直接开放 |
| N 不适用 | 创建表单、配置编辑、技术标识等，不默认要求可筛选 |

本次规模：扫描 Workbench 49 个、Admin ZSJOS 63 个、Partner H5 36 个页面/辅助源文件；核对 118 个去重静态 fieldKey、28 个核心请求/响应 VO、52 个普通列表请求 VO 和 5 类导出 Provider。源文件数量不是正式菜单数量。

## 2. 核心页面接入矩阵

表中后端路径省略 `/admin-api`。入口来源见逐页扫描附件；“同目录”仅表示相同 scene，不表示两端字段权限、页面查询和导出行为已一致。

| 页面/视图 | Workbench | Admin | 业务对象/查询边界 | 结论 |
|---|---|---|---|---|
| 客资管理 | `lead / lead_management` | 同目录 | Lead；租户与已授权关系范围 | A，客资扩展业务字段仍缺，见 G01–G05 |
| 客资抢单池 | `lead / lead_claim_pool` | 同目录 | 当前可抢 Lead | A；池的时效、分配属性没有专属目录 |
| 公海 | `lead / lead_aging_pool` | 同目录 | 公海周期 + Lead | A/C；入池、到期、协同人不能用一般 Lead 时间/负责人替代 |
| 下属销售人员列表 | `subordinate_sales / subordinate_sales` | 同目录 | 当前授权下属的人员与业务指标 | A/C；新增指标未全部登记 |
| 下属销售的客资子列表 | `lead / subordinate_sales_leads` | 未发现对应 pageKey 入口 | 指定授权下属 + Lead | A；与人员指标 scene 是两个查询对象 |
| 重复客资复核 | `duplicate_review / lead_duplicate_review` | 同目录 | 复核记录与提交快照 | A/C；规则、候选集合需明确查询口径 |
| 申诉处理 | `lead_appeal / lead_appeal` | 同目录 | 申诉记录 + 关联 Lead/Order | A/C；判无效快照、裁决证据未登记 |
| 订单管理 | `order / sales_order_management` | 同目录 | 已授权订单 | A/C；审批、付款及补充表单字段仍缺 |
| 成交订单审批 | `order`，pageKey 随审批中心变化 | `WorkbenchListPage` 未传 `advanced-scene` | BPM 任务 + Order | 两端接入不一致；任务字段不能直接当订单字段 |
| 主管确认旧页 | React 旧路径重定向至审批页 | 源码保留 `order / sales_order_supervisor_confirm` | 主管确认任务 + Order | 兼容源码，不作为新增正式页面；以服务端菜单为准 |
| 报名履约公共池 | `registration / registration_pool` | 同目录 | 履约单 + 订单 + 清单/流转 | A/C；班级、负责人分派、清单时间未完整覆盖 |
| 学员管理（规划师） | `student / student_my` | 同目录 | Person + 可见服务关系 | A/B/C；班级、服务状态有普通筛选，接收/联系/协作者字段缺 |
| 我的学员（编导/运营） | `MediaStudentsPage` 仅关键词列表请求 | Workbench 专属组件 | Person + 服务 + 账号责任 | 未接统一目录；不能把规划师 student 目录直接视为已兼容 |
| 返现管理/我的返现 | `lead / cashback` | 仅普通类型、状态控件 | Cashback | D：错用 lead；cashback 目录无字段；实际列表不执行 advancedFilter |
| 提现管理/我的提现 | `withdrawal / withdrawal` | 仅普通状态控件及授权范围 | Withdrawal | D：目录权限没有该场景；目录无字段；实际列表不执行 advancedFilter |
| Partner H5 客资列表 | 独立筛选，不使用 ADMIN 目录 | N | PARTNER 自有客资 | 已有 8 个业务维度：阶段、分配、渠道、分类、主产品、申诉、订单审核、提交日期区间；另有关键词/页签 |

证据：

- [查询引擎](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/AdvancedFilterService.java)
- [目录接口授权](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/advancedfilter/AdvancedFilterController.java)
- [人员选项范围](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/AdvancedFilterVisibleUserService.java)
- [Workbench 财务页面](../../frontend/workbench/src/pages/ManagementPages.tsx)、[财务请求适配](../../frontend/workbench/src/services/managementApi.ts)
- [Admin 审批页面](../../frontend/admin/src/views/zsjos/salesOrderApproval/index.vue)、[H5 客资筛选](../../frontend/h5/src/pages/lead/list.vue)

## 3. 字段覆盖与缺口矩阵

每行是同一业务语义的字段组。现有 fieldKey 的逐项明细见附件；下列 C/E 行是候选补齐清单，不意味着每项必须开放。L=列表投影，D=详情/上下文投影，X=导出实现；投影字段不保证所有用户都会看到。

| ID | 页面/字段组 | 字段证据或实际属性 | 当前覆盖 | 缺口与数据来源 | 优先级 |
|---|---|---|---|---|---|
| G01 | 客资基本信息 | L/D：`provinceCode/provinceName/cityCode/cityName`；`primaryProduct/intendedProducts`；X：意向产品 | 姓名/手机号/微信号、编号、来源、分类 A；客资地区、意向产品 C | 地区来自区域契约，产品/SKU来自业务 API；`order.region`是订单地区，`opportunity.expectedProduct`是预计产品，均不能替代客资原始意向 | P1 |
| G02 | 客资业务阶段 | L/D：`handlingStage/followUpStatus/operationalStatus/dispatchMode` | status、assignmentStatus、qualificationStatus、dealStatus、salesStage A；其他 C | 简单状态标签属于 B，不代表任意 AND/OR 条件已支持；必须复用现有服务端状态投影 | P1 |
| G03 | 客资归属/贡献 | L/D：`providerOwnerType/providerOwnerId`、贡献人/主管/部门快照、`pendingAssigneeUserId/recycleSourceOwnerUserId/qualifiedByUserId` | 提交人/负责人/负责人所属组织 A；provider 普通参数 B；其余 C/E | 当前组织与历史业绩归属分开；脱敏身份不能通过筛选结果反推出 | P1 |
| G04 | 客资时效 | L/D：`lastActivityAt/currentAssignmentFirstFollowUpAt/currentAssignmentFirstFollowUpDeadlineAt/qualificationStartedAt/qualificationDeadlineAt/suspendedAt/appealDeadlineAt/closedAt/publicPoolAt` | 提交、最近/下次跟进、持有起点、判定、成交 A；所列 C | 当前归属周期与历史跟进分开；超时条件需按业务截止口径，不只是日期为空/非空 | P1 |
| G05 | 客资原因/证据 | D：`invalidReason/invalidDescription/validDescription/closeReason/invalidEvidence/attachments/remarkHistory` | 备注 A；其余 C/E | 可配置原因用字典；历史显示标签用快照；附件优先“有/无”，备注历史须明确任一/最新 | P2 |
| G06 | 抢单池、公海 | 公海 L/D：`cycleNo/originalOwnerUserId/collaboratorUserId/frozenDeptId/enteredAt/dueAt/assignedAt`；待接投影：`expiresAt/dispatchMode` | 公海 relationScope、status B；统一目录仅 Lead A | 公海周期状态/正式归属人/协同人/冻结部门/入池时效 C；待接期限不等于所有抢单记录均有期限，需按实际入口确认 | P1 |
| G07 | 订单常规 | L/D/X：编号、类型、状态、学员、地区、金额、付款方式、提交/生效时间；D：商品规格 | 多数 A | `orderItem.product`是商品快照文本查询，尚非产品/SKU实体选择；规格、分类 C/E | P1 |
| G08 | 订单补充信息 | L/D：`agreedExamTime/giftItems/giftShippingAddress/repurchaseReason/terminationReason` | 班种、服务周期、学生来源、备注、特殊要求、教材联系 A；所列 C | 考期与预约考试文本/实体口径需明确；赠品集合须定义任一/全部；历史表单标签保持快照 | P2 |
| G09 | 收款状态 | D：`collectionMode/paymentStatus/transactionLocked` | 缴费方式、支付方式、客户付款时间 A；所列 C | 支付业务状态契约；交易锁是技术/操作状态，是否面向业务开放 E | P1 |
| G10 | 订单审批/财务台账 | L/D/X：`approvalRoundNo/taskDefinitionKey/taskStatus/taskCreateTime/taskEndTime/supervisorConfirmationStatus`；X：报名/财务审核人、审核时间、结论 | 普通 center/handled/groupKey/optionKey B；通用目录所列 C | BPM 权威任务事实；同一订单多轮、多任务须明确当前/历史/当前用户任务，禁止跨轮拼接条件 | P1 |
| G11 | 申诉 | L/D：轮次、阶段、状态、申请/审核人、原因、裁决、提交/处理时间 | 已登记 A；`invalidReasonSnapshot/invalidDescriptionSnapshot/decisionEvidence` C | 原判快照与当前客资判定不同；裁决证据与申请证据不同 | P2 |
| G12 | 重复复核 | L/D：提交姓名/联系方式、状态、结果、匹配规则、复核人、意见、时间、附件 | 大部分 A；`primaryRuleCode/candidateSnapshot` C/E | 主命中规则可作为候选；候选集合、before/afterSnapshot不能直接开放整段 JSON 模糊搜索 | P2 |
| G13 | 履约公共池 | L/D：`assignmentMode/classAssignments`、清单 `checkedByUserId/checkedAt`、流转 `assigneeType` | 状态/规划师/审批与完成取消时间/清单标题状态附件/流转部门负责人 A；所列 C | 班级来自班级 API；同一清单项的条件必须命中同一行；未完成原因是投影，查询需同口径 | P1 |
| G14 | 规划师学员 | L/D：`personNo`、`services.classId/className/acceptanceStatus/acceptedAt`、编导/职业规划师/运营、directorStage/interviewAt | 姓名/手机/微信与服务状态负责人激活暂停结业终止 A；classId/serviceStatus B；所列 C | 以当前可见服务关系为约束；一个人多门课不能将不同课程条件拼成命中 | P1 |
| G15 | 学员联系与交付 | D：联系 `successful/contactType/nextContactAt`；上下文 `currentTask.dueAt/overdue/deliveryStage/examDate` | C/E | 单独学员联系契约，不用 Lead 最近跟进代替；联系人、联系时间按服务关系 | P1 |
| G16 | 编导/运营学员与账号 | L/D：账号平台、当前状态、责任人、账号/定位/内容/拍剪状态及最近活动 | 关键词 B；通用高级目录未接入 | 需按 Person→服务→账号分组明确归属；动态字段见 G23，不能借用 lead 目录 | P1 |
| G17 | 下属销售新增指标 | L：`todayAssignedCount/todayMissedCount/todayReceivedCount/todayQualifiedCount/todayFollowUpRecordCount/todayOrderAmount/pendingQualificationCount/todayFollowUpTotalCount/todayFollowUpRemainingCount/canReceiveNewLeads` | 16 个人员/业务指标字段 A；所列 C | 复用服务端指标计算；日期统计窗口需明确为北京自然日；不另造第二套指标 | P1 |
| G18 | 返现基础 | L/X：返现号、类型、状态、客资号、受益人、产品、金额 | 普通 type/status/keyword/beneficiaryUserId/partnerId/productName/amountMin/Max 在 Mapper 有条件 B；高级 D | Workbench 错场景，Admin无高级控件；需先接通 Cashback 自己的查询链路 | P0 |
| G19 | 返现扩展 | L/D/X：`baseAmount/rateSnapshot/observationDaysSnapshot/generatedAt/availableAt/settledAt/cancelledAt/cancelReason` | 生成/可提现/结算区间 B；基数/比例/观察期/取消原因及时间 C | 金额/比例须固定单位；orderNo 参数存在但关联表名与 SalesOrderDO 不一致，见 F04 | P1 |
| G20 | 提现基础 | L/D/X：提现号、状态、申请人、金额、提交/审核/打款时间、银行流水 | 普通参数及 Mapper 条件 B；高级目录/执行 D | 前端“银行流水号”关键词提示与 keyword 实际查询不一致，见 F05 | P0 |
| G21 | 提现扩展 | D/X：`verificationStatus/approvedAmount/reviewedByUserId/paidByUserId/rejectionReason/payoutRemark/proofFileId` | C | 核验、审批、打款状态按业务契约；有无凭证可独立筛选；空 paidAt 是允许事实，不能补成登记时间 | P1 |
| G22 | 提现银行信息 | D/X：账户名、银行名、支行、脱敏卡号；完整卡号为独立授权详情 | C/E | 不把完整银行卡、凭证 URL、流程实例 ID 作为通用字段；脱敏字段也需防止试探查询 | P2 |
| G23 | 模板动态字段 | 账号画像、定位访谈、定位卡、素材、学员信息收集、强制表单 | 独立模板/快照；不在静态 FIELDS | 先定义模板版本、字段稳定键、类型、可筛选标记与查询索引；当前字典标签不能重写历史意义 | E |

字段来源证据：核心 VO 全量属性及路径列于字段附件的“请求与响应投影”。表格列核对 [订单列](../../frontend/workbench/src/components/SalesOrderTableColumns.tsx)、[返现列](../../frontend/workbench/src/pages/financeTableColumns.tsx)、[学员页面](../../frontend/workbench/src/pages/RegistrationPages.tsx)、[媒体学员](../../frontend/workbench/src/pages/MediaStudentsPage.tsx)。显示字段与目录名称相似时按数据源/对象关系比对，不按中文名称直接合并。

## 4. 已确认的接入问题与待验证风险

| ID | 静态检查结果 | 证据 | 处理依赖 |
|---|---|---|---|
| F01 | 返现 Workbench 使用 `scene="lead"`，pageKey 是 cashback；pageKey仅用于模板，不改变目录 scene | ManagementPages + AdvancedFilterToolbar | 需要返现目录/授权/查询同时接通，不能只替换字符串 |
| F02 | SCENES 声明 cashback/withdrawal，但 FIELDS 没有这两类绑定；目录 Controller 无对应授权分支 | AdvancedFilterService + AdvancedFilterController | 实际应按各场景功能权限开放；不新增静态角色判断 |
| F03 | 两类财务 Controller 有 search-page，ReqVO有advancedFilter，但 Service getPage调用 Mapper 两参数重载，matchedIds始终从该调用传null；未消费advancedFilter | [CashbackServiceImpl](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/cashback/CashbackServiceImpl.java)、[WithdrawalServiceImpl](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/withdrawal/WithdrawalServiceImpl.java) | 筛选条件可能提交成功却不参与列表；导出复用同 Service 也不能视为支持高级条件 |
| F04 | 返现 orderNo 普通条件 SQL 使用 `zsjos_sales_order`，SalesOrderDO 映射为 `zsjos_order` | [CashbackMapper](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/dal/mysql/cashback/CashbackMapper.java)、[SalesOrderDO](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/dal/dataobject/order/SalesOrderDO.java) | 源码命名不一致已确认；未检查数据库是否有兼容对象，不能宣称线上已复现报错 |
| F05 | 提现搜索提示为“提现单号 / 银行流水号”，keyword仅like提现单号；银行流水另有bankTransactionNo参数，当前Workbench未传 | [WithdrawalMapper](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/dal/mysql/withdrawal/WithdrawalMapper.java)、ManagementPages | 区分关键词含义和单字段条件 |
| F06 | Admin订单审批未配置advanced-scene；Admin返现/提现没有统一高级筛选组件 | 对应三个Admin页面 | 两端需要分别盘点，不以Workbench入口证明Admin已支持 |
| F07 | 多场景人员字段依赖Lead可见人员规则；student普通用户选项为自身且要求存在活动owner关系 | AdvancedFilterVisibleUserService | 是已读到的实现；对审批人/协作者的业务适配性仍需账号样本验证，不据此直接判定越权 |
| F08 | 两端目录请求仅传scene；pageKey参与模板API，未参与字段目录裁剪 | 两端高级筛选组件与API | 页面字段多寡不能通过改pageKey解决 |
| F09 | 下属销售使用服务端`matches`匹配聚合人员行；不能将其Binding中的表达式`1`当真实数据库字段 | SubordinateSalesServiceImpl + AdvancedFilterService | 与其他场景SQL查询机制不同，扩字段需补聚合值提供器 |

上轮建议中的“财务后端条件缺失”在本次细查后细化为：普通金额/时间参数已有一部分实现，高级条件链路未完成。现有静态 options 还包含状态机、技术布尔值和时间单位，不能一律归为必须字典化的问题。

## 5. 尚未接入统一目录的页面

全量文件级扫描见页面附件。以下是业务归类，不是已批准扩展范围：

| 业务页面 | 已观察到的入口/对象 | 后续字段盘点重点 |
|---|---|---|
| 素材浏览/管理/类型/导入 | 独立关键词、类型/状态/来源等查询；后台有模板配置 | 标题、类型、平台、发布状态、创建/提交人、版本时间；动态内容按G23 |
| 内容生产/内容审核 | 内容关键词、状态/分类；审核收件箱有责任运营/编导/平台条件 | 内容与审核批次的粒度不同；当前审核与历史审核分开 |
| 拍剪工单/通用工单 | 工单号、状态、池/本人处理范围 | 制作人、需求方、截止时间、返修次数、关联学员/账号，遵循各自工单API |
| 销售投诉/需求反馈 | 独立列表与处理状态 | 提交、处理人、类型、结果、提交/处理时间；隐私范围独立 |
| 班级管理/日历 | 班级或日期范围的专用界面 | 班级、课程/规格、考期、班主任、责任人、事件类型；日历范围不是普通日期字段的任意组合 |
| 兼职/人员/派单关系 | 姓名/编号/手机号、配置状态等独立查询 | 启停、组织、关系有效性、来源主体；不根据名称推断权限 |
| 销售业绩/目标 | 期间、组织/人员范围与聚合指标 | 统计粒度及贡献快照，已停用人员规则；不直接挂Lead/Order筛选器 |
| 工作计划/首页/审批中心 | 计划、待办和BPM任务 | 类型、状态、负责人、到期时间，不能混用不同任务系统 |
| 消息/公告/导出任务/业务审计 | 系统消息、公告、导出任务、审计记录 | 业务分类、阅读/发布/任务状态、时间；读写权限与数据范围独立 |
| 我的资产/采购申请 | EAM员工自助 | 所属模块公开API，不能复制EAM DAL到ZSJOS |
| 产品/规则/模板/通知/维护配置 | 管理配置或编辑表单 | 先区分列表查找和配置编辑，N不意味着缺陷 |
| 提交客资/自拓/历史复购/拆解创建 | 录入或创建流程 | 不适用通用列表高级筛选；实体选择器另按权威API检查 |

## 6. 来源、权限与历史口径

| 字段类别 | 权威来源 | 盘点结论/约束 |
|---|---|---|
| 来源渠道、客资分类、销售阶段、学员性质、服务周期、学生来源、缴费/支付方式 | 当前目录声明的System字典类型 | 目录登记类型不等于当前租户一定有选项；历史停用值与快照标签需单独考虑 |
| 人员、组织、班级、产品、SKU、账号 | System或业务实体API | 用稳定ID匹配、授权范围内名称显示；不能让用户手填内部ID作为业务查询体验 |
| 客资/订单/审批/履约/服务状态 | 各自业务状态机/BPM契约 | 固定状态不是管理员可任意编辑的业务枚举，不整体迁入字典 |
| 历史金额、比例、字典标签、负责人贡献 | 业务记录快照 | 当前值/历史值分开命名；未记录历史值不得编造 |
| 内部主外键、version、cursor、permission、availableActions、fileUrl | 技术关系或授权投影 | 不以“字段全面”为由开放为业务筛选；Lead展示仍用leadNo |

关联筛选还必须明确：同一个订单/服务/清单项命中全部条件，还是不同记录分别命中；当前记录还是任一历史记录；无关联记录与关联字段为空是否相同。现有引擎对同一关系的AND可归并为EXISTS，并对否定关系使用NOT EXISTS，扩字段时必须保持这些语义。

## 7. 盘点交付结论与验证

优先顺序：P0修复财务场景和查询断链；P1补齐主业务字段/当前页面已经展示的字段；P2补充低频原因、附件存在性等；E类先定义口径。此顺序仅作为后续任务拆分依据，本次不实施。

本次通过源代码、请求/响应VO、字段注册、实际Service/Mapper调用、导出Provider及页面组件完成静态对照。附件保留定位路径和行号；字段数量按注册调用及scene绑定逐项计算并交叉核对；文档链接和格式检查纳入交付。

未做登录账号下的目录API、真实筛选命中、数据库schema或浏览器验收，因此运行态权限、字典选项完整性、查询性能和线上实际行为仍未验证。文档类交付不运行前后端构建，不修改任何业务实现、权限、字典或SQL。

## 8. 已落实：通用字段与业务场景字段拆分（2026-09-23）

字段拆分使用现有scene作为页面业务边界，不引入pageKey目录裁剪。fieldKey、类型、操作符、选项来源、SQL绑定、接口响应和模板结构保持兼容。字段依所属对象集中排列，成交归属身份字段移到订单字段组内；缺口和财务断链仍按本矩阵单独跟踪。

| 归属 | 实现 | 责任 |
|---|---|---|
| 组合目录 | `AdvancedFilterFieldCatalog` | 组合通用/业务字段并保存不可变映射；scene绑定是最终白名单 |
| 通用定义 | `AdvancedFilterFields` | 描述符、类型操作符、分组、人员/字典选择和时间/金额等工厂；重复fieldKey立即失败 |
| 通用身份 | `CommonFilterFields` | Person姓名、手机、微信、身份；仅在原有显式绑定场景复用 |
| 共享关联 | `AdvancedFilterRelations` | 统一关联SQL、租户和逻辑删除条件、服务关系范围 |
| 客资/商机/订单 | `LeadFilterFields`、`OpportunityFilterFields`、`OrderFilterFields` | 各对象字段定义及已有跨页面绑定，复用不等于开放全部页面 |
| 页面业务专属 | `AppealFilterFields`、`DuplicateReviewFilterFields`、`RegistrationFilterFields`、`StudentFilterFields`、`SubordinateSalesFilterFields` | 申诉、复核、履约、服务关系和人员聚合指标 |
| 查询执行 | `AdvancedFilterService` | 读取组合目录、动态时间作差、条件校验和SQL/聚合值匹配；不再内嵌118个字段注册 |

上述类均位于 [advancedfilter](../../backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter) 包，注册定位见[字段附件](advanced-filter-field-catalog.md)。通用时间/人员/金额是构造规则，不意味着凭空给每个页面添加同名数据库列。财务场景没有新增空壳Provider。

新增筛选字段时，在对应业务Provider登记字段与允许scene；只有真正共享的身份字段放入Common。关联口径复用Relations，差异不可仅凭名称合并；补充类型/选项时遵循权威来源。新增页面业务对象才增加Provider并在组合目录显式注册，同时补目录、条件执行和权限测试。已有模板继续保存稳定fieldKey。

Workbench和Admin继续请求`GET /zsjos/advanced-filter/catalog?scene=...`并读取`fields/relativeDateOptions`；本次不改变它们各自的页面集和菜单权限，也不要求共享React/Vue组件。

## 9. 已落实：完整字段元数据（2026-09-23）

118个字段与动态时间作差字段已补充支持场景/已知页面、继承授权与数据范围策略、敏感性、排序/弃用能力、选项来源类型及原始来源。见[元数据契约](advanced-filter-metadata.md)及[逐字段分类](advanced-filter-field-catalog.md)。两端协议类型同步扩展；原来的fieldKey、操作符、查询SQL与模板保持兼容。元数据只描述现有能力，不将错接的返现页、未接入的媒体学员页登记为已支持，也不代替后端鉴权。

## 9. 已落实：页面场景与后端查询契约（2026-09-23）

返现使用 `cashback` 场景，提现使用 `withdrawal` 场景；两类目录均含本实体字段，目录与模板接口按现有查询权限开放。高级条件由 `search-page` 请求进入服务，再与租户、个人/管理范围和普通条件取交集后分页；空匹配集返回空页。返现订单号关联 `zsjos_order`，提现关键词同时覆盖提现单号和银行流水号。Workbench 与 Admin 的返现、提现入口都提交统一 `advancedFilter`。

F01–F06 已在源码修正：除财务链路外，Admin 成交审批新增 order 场景入口，使用 `/zsjos/sales-order/approval/search-page`。返现/提现普通条件与高级条件采用 AND，组内逻辑遵循 AND/OR；切换条件重回第一页，清除高级条件恢复普通 GET 分页。Admin 提现导出保留当前 keyword/status/advancedFilter，后端导出继续使用脱敏投影。模板 visible-list、personal、scene 参数校验及管理页可选场景同步支持财务场景；原先错误 lead/cashback 模板不自动转换，需在正确 cashback 场景重新建立，避免把客资语义误迁为财务语义。

F07–F09 是待核验或既有设计说明，本次不扩展人员可见性、不引入 pageKey 字段裁剪、不改变下属销售聚合算法。Partner H5 查询不在本次 ADMIN 两端接入范围。G18–G21 的人员选项缺口仍保留，其余财务新增字段见目录增量。

## 10. 已落实：选项权威来源

两端财务普通筛选复用服务端目录；Admin 人员简易列表回退已移除。三个既有状态字典直接引用，固定状态复用领域常量；正式学员身份选项修正为 student。来源分类、兼容性和验证边界见[字段目录来源记录](advanced-filter-field-catalog.md)。


## 本轮补齐状态（保留原界面）

本轮新增 30 个静态字段，总数 180；前文 G 表保留盘点基线，以下为更新状态。

| 缺口 | 当前落实情况 | 仍待处理 |
|---|---|---|
| G04 | 所列 9 个业务时间字段全部进入目录并绑定真实列 | 派生超时状态不等于简单日期比较，尚未新增 |
| G05 | 无效原因使用权威字典；有效说明、无效说明、关闭原因支持文本条件 | 证据有无、附件、备注历史 |
| G08 | 商定考试时间（文本）、礼品邮寄地址、复购/终止原因、终止时间已接入 | giftItems 是集合，尚未实现实体选择与任一/全部语义 |
| G11 | 原判无效原因历史标签、原判无效说明已接入 | 裁决证据有无 |
| G17 | 所列 10 个新增指标均接入后端 matches，在授权行上分页前过滤 | 本项字段接入完成，现场数据验收待部署 |

本轮不改变样式、页面路由、数据库或权限。G01–G03、G06–G07、G09–G10、G12–G16、G23 等未因此标记完成；订单审批查询当前/历史轮次口径仍待确认。新增字段明细见字段目录末节。


## 产品/SKU 续补状态

新增原始意向产品/SKU与成交产品/SKU四项，静态目录合计184项。G01 原始意向实体筛选与 G07 成交实体筛选已接通；地区、分类和规格扩展不因此标记完成。选项仅来自商品模块启用目录，同一行关联、防跨租户条件已增加测试。

G10 审批轮次等待“当前/历史双组或单组”的明确选择；G23 动态字段等待“显式允许筛选/全部发布字段默认开放”的选择。已确认动态模板存在稳定键、已发布/归档版本及快照，但不同模板体系（编导、定位、素材、强制表单）的记录关联与对象授权不同，不能统一对任意 JSON 模糊查询。实现前须按模板体系绑定具体快照和对象范围；当前未新增动态查询或改写模板配置。


## 自动化完整性回归

已增加[七项契约检查与运行说明](advanced-filter-contract-checks.md)。运行 `script/verify-advanced-filter-contract.ps1` 可检查已登记页面、字段/操作符、关键业务覆盖、来源、权限拦截及两端契约；新增统一筛选入口未登记会失败。完整盘点验收应增加 `-RequireFullCoverage`，未关闭缺口会明确阻断，而非仅凭已实现范围的回归绿灯放行。
