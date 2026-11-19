# 销售与学生交付中心合并：工作流登记

- 环境：local（环境变量及环境文件均未指定）；分支：main；工作树：D:/ZSJ-OS；登记时 HEAD：21631a8a98a62c41a3c71596a6739e03a535c3b6；集成目标及顺序：None。
- 初查 HEAD 为 464c40f6d0eae8ed91cceddde915675ee8a38543，登记前已变化；工作区存在大量并行或既有未提交修改，逐文件核查并保留。
- 工作流 ID：local-sales-delivery-merge-20261113；总协调及本记录唯一写入者：Codex /root/architecture_coordinator；总控：/root。
- 目标：合并销售与学生交付功能和角色；现销售人员归销售一部、现交付人员归销售二部，两部数据独立；保留历史客资、订单、学员和班级归属；新流程覆盖提交或自拓、接收或直接归属、跟进、成交、分班及销售管理学员；销售可担任班主任，分班不自动改变服务负责人。提交端不选部门；接收资格与暂停接单区分，自动分配不按部门分池。业绩归销售，清理独立交付业绩旧代码并保留必要历史兼容。
- 非目标：删除历史业务数据；未经专项授权执行组织、真实权限、数据库、共享服务或 Git 受控操作；覆盖既有未提交工作。
- 当前阻塞：成交后保留履约确认并自动归订单成交负责人，还是审批通过立即创建服务，待用户决定；同人同周期销售及交付目标冲突由主管确认还是相加，待用户决定。班主任与服务负责人不同时，班主任对他人负责学员的访问范围待用户决定。其他未确认的业务或授权边界由总控向用户确认，不以实现假设替代。
- 暂定文件所有权：客资分配、接收、合作方提交及其测试由 /root/workflow_permissions 负责；成交到报名履约、服务关系、班级和学员领域及其测试由 /root/architecture_coordinator 负责；业绩领域及其测试由 /root/performance_merge 负责。公共资格接口、权限元数据及 SQL、共有架构文档由 /root/architecture_coordinator 独占；接收资格、销售业务资格、班主任资格分开评估，避免无必要新增。前端及其他交叉文件须由总控逐文件指定唯一写者后方可修改；具体文件清单将在实施前登记更新。HEAD 变化后所有代理须重新核对目标文件 diff，保留新出现的并发修改。
- 依赖：用户确认阻塞项；统一销售能力、班主任资格与客资接收资格的服务端契约确定；各写者先检查目录规则、目标文件现状及既有工作，重叠文件串行协调。
- 验证计划：按影响覆盖成交、复购与多订单，历史归属及幂等，分班、转班与变更班主任不改服务负责人；权限允许/拒绝、对象及列表范围、跨部隔离、混合批量原子性、租户 1；接收资格与暂停接单、手动及自动分配；业绩历史兼容和冲突决策；受影响前端契约、类型检查及可用的浏览器/API 验证。涉及 SQL 时遵守仓库受控数据库与迁移验证规则；受阻验证须单独报告。

## 决策及登记更新 — 2026-11-13
- 用户确认保留履约确认，确认时自动归订单成交负责人；同人同周期销售和交付目标自动相加。班主任就是负责人，此最新决定覆盖此前分班不改变负责人的说法：分班、转班、换班主任同步服务负责人；不批量重写历史归属。
- /root/architecture_coordinator 独占成交履约、学员、班级前后端及相应测试和文档，以及公共资格、权限元数据及 SQL；/root/performance_merge 独占业绩及共享路由；/root/workflow_permissions 独占客资和接收流程。涉及其他交叉文件先协调。未经单独授权不更改真实角色、组织、共享数据库、服务或 Git 状态。
- 验证更新：检查履约确认自动归成交负责人、待分班、分班及转班同步班主任负责人、历史服务不批量回写、业绩同周期目标合并，及原登记中适用的权限、隔离和前端检查。
- 业绩子任务由 /root/performance_merge 独占其已报总控的业绩文件，以及 frontend/workbench/src 路由 constants.ts、RouteHost.tsx、menuComponentRegistry.ts；排除 MediaLead*。跨域路由需求交该写者处理。
- /root/workflow_permissions 独占 LeadAssignmentServiceImpl、LeadDispatchServiceImpl、PartnerLeadAssignmentService、LeadAssignmentConstants、LeadSubmissionServiceImpl、LeadOwnerCommandService、LeadRestoreOwnerPolicy 及对应测试；接收资格标识 zsjos:lead:receive，由 System PermissionApi 检查，与暂停状态和销售业务资格分离。
- 用户批准服务端统一销售资格 zsjos:sales:eligible；提交端客资接收须同时具备 zsjos:lead:receive，暂停状态独立；班主任候选复用销售资格并遵循账号及部门校验。权限元数据不自动授权真实角色，管理员须配置后方可使用。/root/workflow_permissions 另独占 SalesDispatchStatusServiceImpl 及其测试。
- /root/workflow_permissions 另独占 LeadAssignmentService.java，按销售资格和接收资格区分候选接口。
- 业绩同周期目标合并采用持久化来源关联与同事务写入；验证首次、重复、并发、调小、单侧目标、修订读取授权及 SQL 失败恢复。履约 complete 的资料、清单及附件校验保留，提前建服务路径不得绕过。

## 中间检查点 — 2026-11-13（未完成，不得部署）
- 等待用户决定：跨部访问是否可配置明确授权，或除管理员外一律禁止；历史双域目标发生在不同部门时保留各部门分量还是统一当前部门。相关边界暂停。
- /root/architecture_coordinator：修改 RegistrationServiceImpl、DeliveryClassServiceImpl、UnscheduledDeliveryClassService、ExceptionDeliveryClassService、Workbench RegistrationPages.tsx 和 services/api.ts；新任务冻结 sales_owner 模式，complete 的资料、清单、附件校验后按订单 formalSalesUserId 建立本人待分班服务；去除分班及旧规划师选择时提前建服务的调用。班主任资格改为 sales:eligible；旧模式仍服务既有任务。尚需审查旧任务兼容和前端分班路径，增加回归测试、真实浏览器验证和直接影响文档。
- SQL 草稿 V371__sales_delivery_merge.sql 增加销售资格、接收资格元数据及目标来源列；core.sql 和 00-bootstrap-schema.sql 各新增目标列，未授权真实角色、不操作共享数据库。尚未核实目标数据库账本或 V371 部署适用性，未运行受控首次、重放、部分恢复、失败不登记、中文 HEX 和完整新建链验证；禁止应用或发布。
- /root/workflow_permissions：修改 LeadAssignmentConstants、LeadAssignmentService/Impl、LeadDispatchServiceImpl、PartnerLeadAssignmentService、SalesDispatchStatusServiceImpl 及 4 个相关测试；区分销售资格与接收资格，暂停状态独立。首次聚焦测试 56 项中 12 失败、17 错误；旧 mock 已部分调整但最新代码未重跑。部门隔离未实现，待用户决定。
- /root/performance_merge：修改 PerformanceDetailSql、PerformanceAccess、PerformanceTargetDO、PerformanceDomainSqlReviewTest，Workbench constants.ts、layouts/RouteHost.tsx、services/menuComponentRegistry.ts、test/unclassified-history.tsx；删除独立交付业绩页面、服务及对应测试。目标合并和后端旧代码清理尚未完成；工作台 typecheck 通过；Maven 曾因共享 target 中 LeadSubmitterFeedbackService.class 零长度而 testCompile 失败，未清理或归因；浏览器和完整验证未完成。
- Workbench 当前 typecheck 通过；本工作流 git diff --check 通过。后端 compile 正在运行，尚待结果；所有变更及迁移均为中间状态，禁止部署、授权或变更共享数据库。
- 用户进一步确认：员工和主管默认本部隔离，另行明确授权者可跨部；目标合并后全部归员工当前部门，旧目标与修订保留追溯且不重复计旧部门。恢复实施，跨部资格与可信部门边界待统一；接收候选跨部展示不等于数据访问授权。后端 compile 已通过（53 秒），不替代聚焦测试。
- /root/workflow_permissions 另独占 LeadObjectPermissionService、LeadMapper 及其关联测试；拟议跨部权限 zsjos:sales:cross-department，仍需 System 数据范围约束；业务部门根配置设计待总控审核，不能按部门名称或固定 ID 推断。
- 总控批准复用业绩组织配置增加显式销售业务边界根标志；销售业务根缺失/重叠 fail-closed 并给可操作错误；系统管理员既有例外保留，跨部资格 zsjos:sales:cross-department 累计 System 数据范围。默认不标记现有根、不授权真实用户；组织配置 UI/API 为 /root/performance_merge 独占，公共解析/SQL 为 /root/architecture_coordinator 独占。/root/workflow_permissions 另独占 LeadTeamTransferPolicy 及测试。
- /root/workflow_permissions 另独占 LeadManagementServiceImpl 及对应测试；公共 SalesDepartmentBoundaryService 提供 rootForDepartment、sameBoundary、canAccessUser、filterAccessibleUsers、validateRootChange，目前未验证，批量方法待优化以避免 N+1。
- 隔离 MySQL 8.4.11 专用容器 codex-sales-merge-mysql-20261113：V371 初次、重放、目标列部分应用恢复、前置缺失、第二账本失败回滚与恢复、不兼容列、中文 HEX 已通过；未连接或写入共享数据库。完整 fresh chain、目标账本核对和差异比较尚未执行；容器保留供后续验证。
- 学员 Person 与服务对象权限检查：销售视角先按公共部门边界及 System 范围过滤服务负责人，管理员例外；非销售原流程保留。列表、导出、附件和共享 Person 字段写入影响仍需完整审计；目前代码未经测试。独占 StudentObjectPermissionProvider、StudentServiceObjectPermissionProvider 及对应测试。
- /root/workflow_permissions 另独占 LeadTaskPriorityTest.java，用现有 fixture 验证分页和计数范围。
- 客资子任务新增 LeadMapper、LeadManagementServiceImpl、LeadObjectPermissionService 及相关测试；SQL 分页与统计新增 fixture 断言尚未运行，销售提交人不可借来源关系跨部，非销售来源沿原契约。
- /root/workflow_permissions 另独占 LeadOwnerCommandController、LeadOwnerCommandService、LeadQualificationServiceImpl 及对应测试、Workbench LeadManagementPage、LeadDetail；services/api.ts 已由 /root/architecture_coordinator 完成 registration assignmentMode 单字段改动并移交该代理独占，须保留该改动。

## 阶段剩余矩阵 — 2026-11-13
- 已改待测：新报名 sales_owner 履约确认后归成交人待分班；旧任务按原快照模式，移除提前建立服务；班主任资格改统一销售资格；服务对象、学员对象及服务高级查询初步接入部门边界；公共部门根解析与 V371 元数据/字段草稿；客资接收及部分 SQL 范围；业绩合并及配置 UI 在并行实现。
- 仍未完成：MyStudentServiceImpl 普通、显式范围、分页总数及关联投影均需完整边界；班级管理/分班、转班的隔离，学员和订单列表、统计、导出、附件和跨模块 Person 入口审计；已有历史未完成任务行为、成交人与班主任不一致全链路及真实用户交互测试；两前端受影响接口和授权检查。不能认为对象读取修复即完成隔离。
- 用户决定阻塞：共享 Person 基础资料写入语义待确认；销售 archive-update 当前临时 fail-closed，不作为最终方案。
- SQL：专用 MySQL 8.4.11 容器已通过 V371 局部首次/重放/部分恢复/失败不登记/HEX；fresh 全链及升级链、目标账本与只读差异比较尚未执行，migration 仍是草稿。角色/组织未授权、未修改共享数据库；新元数据和部门根未配置前相关功能不可用。
- 验证：先前 Workbench typecheck 和较早代码 compile 通过；最新边界测试因两代理中间态 testCompile 失败，源文件后续已变更，须等各代理稳定后统一重跑。浏览器 fixture 与 live HTTP 尚未执行；禁止部署。
- /root/workflow_permissions 独占 SalesOrderObjectPermissionService、SalesOrderServiceImpl、SalesOrderMapper、SalesOrderManagementScope 及相关订单测试；当前目标 diff 已由该代理检查为空，订单范围与客资隔离协同。

## 本轮暂停点 — 2026-11-13（未完成）
- 用户尚未决定共享 Person 基础资料继续统一授权修改、还是两部独立；销售 archive-update 临时拒绝，绝非最终方案。等待答复后恢复；数据库及权限、部署均未执行。
- 本人新增公共 SalesDepartmentBoundaryService、sales 根验证与权限判断单测草稿；V371 加 boundary-configure 第四权限后首次测试暴露 SQL 语法错误，已修复并在独占 MySQL 8.4.11 重跑，首次、重放、部分恢复、前置/第二账本失败及恢复、结构冲突和中文 HEX 均通过。完整 fresh/upgrade 及目标库账本差异仍未完成。
- 学员对象和服务对象加入销售根和 System 数据范围判定；StudentServiceReadService 高级 SQL 附加 owner 范围；MyStudentServiceImpl 管理列表/投影部分调整未完成，尤其普通与显式读范围、协作分支、分页统计仍需审计。班级转移、其他列表/导出/附件以及 Person 共享写入未完整隔离。上述源码和测试未经最新稳定版本编译，禁止交付。
- 业绩子任务：销售事实包含旧 delivery，目标使用双域版本令牌与锁，两行合并写 sales 并关联旧目标，目标归当前部门，旧修订保留；组织配置 UI/API 已接 boundary-configure + 原 configure + System 写范围；旧交付页面与后端代码清理、API 文档更新正在进行。真实 MySQL/并发/分页、修订、跨部及缺失根测试均未完成；先前类型检查/12 项测试之后源码已变更，需重跑；mergeRows 测试 mock 待同步。隔离 MySQL 尚未执行业绩测试。
- 注册变更仍需覆盖新成交归属、重复履约、历史快照、分班/转班及换班主任回归；旧任务选择分班时不再提前建服务，需专项确认兼容。WorkBench fixture/浏览器、两端受影响接口、真实请求未验；所有更改为中间状态，不得部署。
- 业绩文件进一步记录：SalesPerformanceController、SalesPerformanceTargetController、PerformanceVO、PerformanceOrgDO、PerformanceTargetDO、PerformanceAccess、PerformanceStatisticsService、PerformanceTargetPermissionProvider、PerformanceTargetService、PerformanceDetailSql 及 PerformanceAccessTest、PerformanceDomainSqlReviewTest、PerformanceLegacyDomainTest、PerformanceTargetBatchTest、PerformanceTargetTest；Workbench SalesPerformanceTargetPage.tsx、salesPerformance.ts，docs/api/sales-performance.md 与 delivery-performance.md。删除独立 deliveryperformance 后端目录/交付 controller/常量、PerformanceDomain 相关类型及其专属测试，删除独立交付业绩前端及专属测试；MediaLead 未触碰。具体删除仍以 git diff 为准。

## 决策更新 — 统一客户档案
- 用户确认保留统一客户档案；有授权的修改同步两部姓名、手机号、微信。订单、服务、跟进及附件继续按业务范围隔离。取消先前销售 archive-update 临时全面拒绝，保留对象、字段权限和既有审计。
- 客资聚焦测试 183 项，4 失败、9 错误，源码与 testCompile 通过：旧 mock/断言和排序断言需修复，边界 SQL 尚未成功验证；Workbench tsc --noEmit --incremental false 通过。订单隔离尚未实施。
- /root/performance_merge 独占 script/sql/mysql/tools/test_performance_merge_behavior.py，独立 perf_merge_* schema 验证目标并发，不改 V371 fixture。验收另覆盖共享 Person 基础写权限与同步、订单/服务/跟进/附件跨部隔离、分班/转班/换班主任负责人原子同步、履约正式负责人及既有服务不重写。
- /root/workflow_permissions 另独占 SalesOrderApprovalRoundMapper 及对应测试，用于 BPM 订单待办分页及计数范围。
- 独占 MySQL 8.4.11 schema sales_merge_fresh_20261113 完整 fresh 链验证通过（全部模块迁移、最终结构/账本、选定迁移重放、UTF-8 HEX、非空 bootstrap 拒绝；data-grade 0），日志系统临时目录 sales-merge-fresh-check.log。目标环境只读对比及 upgrade 链未执行。
- 隔离既有 upgrade fixture 初次运行在 V323 content_type 定义对比失败（fixture varchar(32)，fresh 目标不同），失败原因尚未定位；V371 专项升级/重放通过不等同完整升级链通过。
- 业绩代理新增独占 tools/test_performance_merge_behavior.py 和 Workbench test/sales-performance-review.ts；隔离 MySQL 锁/合并测试通过，Workbench typecheck 与 12 项服务测试通过，浏览器虽成功渲染但输入交互卡住，未通过交互验收。
- /root/workflow_permissions 独占 docs/api/zsjos-sales-order.md，记录订单跨部与历史凭证 URL 兼容边界。
- 班级对象 query-all 前新增销售部门边界约束（DeliveryClassObjectPermissionProvider）；独占该文件及对应测试，待 focused 验证。
- /root/workflow_permissions 另独占 LeadSubmissionIdentityService、LeadSelfSourcedArchiveService、Workbench LeadSubmissionPage、LeadFollowUpCharts 及对应测试；统一销售自拓资格，保留历史兼容 API，既有新媒体岗位规则不变。
- V371 最终列顺序调整后，隔离 MySQL 的完整 fresh 链重新通过（sm_f2，data-grade 0）；合成 V370 前置运行 V371 与 fresh 两张受影响表完整列定义/顺序、四权限元数据及两账本版本一致。历史完整 upgrade fixture 仍因 V323 历史宽度比对不一致受阻，不能声称完整历史升级通过。
- 总控批准待分班非破坏性调整：保留旧班及旧服务、部门快照；按 owner+当前真实部门复用或建立新班，班号使用现有编号服务；V371 先建 (tenant_id,unscheduled_guard,dept_id) 唯一键再移除旧 owner 唯一键，保留 NULL 语义，精确检查及中途恢复。历史 dept NULL 不回填；同部门复用、调部新班、并发幂等、旧服务不变及索引失败恢复需验证。独占 UnscheduledDeliveryClassService、DeliveryClassMapper 及测试、V371/baseline/相关 SQL 测试。
- /root/workflow_permissions 另独占 LeadSubmissionController.java，旧 education 自拓端点统一检查 sales 自拓权限。
- /root/workflow_permissions 另独占 docs/api/zsjos-education-self-sourced.md 及直接影响的客资自拓 API 文档，记录统一权限和旧 URL 兼容。
- 业绩代理新增独占 Workbench test/sales-performance-merge-browser.py；最新 focused Maven 54 项全过含独立 MySQL 合成事实真实 MyBatis 分页/汇总/范围，SQL 锁与目标存储测试通过，Workbench typecheck/build、12 项服务测试及隔离浏览器目标保存/过期冲突通过。后续其他源码变动后最终稳定版须重验；真实 Java 服务事务并发与真实账号未覆盖。
- 本工作流新增媒体谈话 DTO/DO、MediaStudentService、PersonMapper、MyStudentServiceImpl 与相关测试和 V371 SQL；新谈话须显式 serviceRelationId 并校验，历史无归属不回填，处理方式待用户。考期收集 roster/附件导出存在可能跨部旁路，需审计。
- 按总控确认，考期收集与学员汇总保留全租户管理合同；销售主体访问这些全量管理入口须另有 cross-department 且 System 数据范围 all，班主任提醒独立入口不变。独占 ExamCollectionController、ExamCollectionSummaryController、ExamCollectionExportController、ExamCollectionPermissionProvider、StudentSummaryController 及其权限测试；现有无提交 diff 已核查。
- /root/workflow_permissions 另独占 Workbench test/self-sourced-auto.tsx 及对应 fixture 浏览器检查。
- 用户确认历史未知媒体谈话：普通部门不可读，仅明确被授予专属只读核对权限者可核对；已知服务/账户关联按原业务对象范围，不按创建人当前部门推测。新增 zsjos:media-student:history-review 元数据，不自动授予。由于 System hasAnyPermissions 和 getEnabledUserIdsByPermission 默认超管绕过，独占 System PermissionApi/Impl、PermissionService/Impl 及对应测试，新增仅实际角色菜单授予检查，不改变现有权限语义。
- 总控澄清沿用 System 超管既有权限例外；不新增 System 权限 API。普通用户专属历史核对资格按标准 hasAnyPermissions 判断，普通 query-all/cross 不替代；上述 System 文件所有权撤销，未修改。
- 直接文档所有权：/root/architecture_coordinator 独占 docs/api/exam-material-collection.md、docs/api/student-summary.md 和媒体学员相关 API 文档；销售全租户管理需要独立跨部资格与 System all，原班主任提醒不扩权。
- /root/architecture_coordinator 独占 docs/api/registration-fulfillment-api.md，更新销售隔离与媒体谈话服务归属契约。
- /root/workflow_permissions 另独占 Workbench pages/lead-submission-steps.guard.test.ts，验证旧 URL 与统一入口。
- /root/workflow_permissions 另独占 SubordinateSalesServiceImpl、SubordinateSalesCommandService、SubordinateSalesIdentitySql 及 DailyQueryTest/ServiceImplTest/CommandServiceTest/ScopeTest、相关 API 文档；旧教育团队独有指标在确认迁移前保留。
- /root/workflow_permissions 另独占 SubordinateSalesRespVO、SubordinateSalesDailyMapper、SubordinateSalesFilterFields、Workbench SubordinateSalesCard 及相关测试；迁入自拓提交去重、早于当日提交的人工跟进条数、当日联系学员按 person 去重，保留租户边界。
- 用户纠正：不改编导运营 inServicePeriod 模型或其原有流程；媒体历史核对限制仅销售视角，非销售读取/写入沿原合同；serviceRelationId 仅销售新写必须，非销售旧接口不强制。之前全局媒体限制撤销；保留销售读取与附件隔离。学员/班级 focused 99 项在纠正前全部通过，相关调整后需重跑。
- /root/workflow_permissions 另独占 SubordinateSalesScope.java 及 ScopeTest；统一成员资格与历史归属证明并保留部门范围。
- 总控确认业务根配置为部署硬前置：上线前管理员明确配置全部销售边界根并验证每名销售成员唯一匹配；缺失/重叠时拒绝并提示配置，不能将未知分类当非销售放行。非销售原行为验证以配置完整为前提；不自动修改真实配置或授权，未完成前禁止上线。根管理使用已配置的独立写权限及 System 写范围初始化，不依赖根读取成功。
- /root/performance_merge 另独占 PerformanceOrgConcurrencyTest、Workbench test/sales-performance-org-browser.py 及相应业绩文档；根配置串行锁与读取竞态已修，定向 55 项中 1 个旧 Batch fixture 错误已修待稳定版复测，浏览器竞态/重试 fixture 通过。
- /root/architecture_coordinator 独占 Workbench test/registration-sales-owner.tsx 与 .html，合成数据验证履约确认界面；不使用真实账号。
- /root/workflow_permissions 拟独占旧 EducationTeamController、旧独立页面/组件/客户端 helper 及专属测试、docs/api/education-team.md、docs/frontend/zsjos-menu-coverage.md 与 advanced-filter-page-coverage.md；移除前核实仅旧页面/测试引用、统一页面覆盖且保留历史表与后端必要兼容。
- /root/workflow_permissions 另独占 Workbench main.tsx、HomeMetricBusinessPage.tsx、educationTeamNavigation.ts 及对应测试，移除旧岗位菜单重写并将指标路由统一。
- /root/workflow_permissions 另独占 HomeMetricPageService、ExistingPageMetricSources 及测试、Workbench services/metricPageScope.tsx 及测试；历史 education_team 指标保留 token 约束并映射统一销售视图。
- /root/workflow_permissions 另独占 HomeMetricEducationFilterCompatibility.java 及测试，在 token/context 校验后映射旧 educationTeam.* 筛选前缀并保留条件。
- /root/workflow_permissions 另独占 SubordinateSalesDailyStatisticsTest.java，保留三项指标业务边界的迁移回归。
- 最终 SQL SHA256 FA551082C38E74B8E4298CBC440955D77AC592D7668B0F92228BFFE250A70D93；隔离 MySQL sm_f5 完整 fresh 全模块/账本/结构通过，data-grade 0；合成 V370→V371 与 fresh 的三表列、班级索引、五权限元数据及双账本版本一致，旧班及 dept=NULL 行未变；V371 局部失败恢复/重放/HEX 同样通过。真实目标只读对比未执行，完整历史 upgrade fixture 因 V323 旧阶段宽度错配受阻。
- /root/architecture_coordinator 独占 docs/api/delivery-class-management.md，更新班主任资格、统一销售及待分班 owner+部门快照契约。
- /root/workflow_permissions 另独占 Workbench test/subordinate-sales-daily.tsx 合成指标 fixture。options SQL 和目标命令隔离修复由 /root/architecture_coordinator 独占 DeliveryClassService/Impl/Controller、StudentEntryService、RegistrationServiceImpl 及相关测试。
- 统一回归首次在源码编译失败（班级选项链式返回父类与 helper 子类参数不兼容），未执行测试；已仅调整 helper 参数为 LambdaQueryWrapper 父类，重新冻结源码哈希清单 sales-merge-frozen-java-sha256-2.txt，聚合 SHA256 见协调消息。
- workflow 前端最终证据：Workbench npm run build（tsc -b + Vite，6628 modules）通过，存在既有 chunk 大小提示；隔离 subordinate-sales-daily fixture 桌面/390 移动截图目检、员工选择及三项指标展示通过；self-sourced-auto fixture 验证仅自拓权限、无 receive 的统一提交。均为合成接口，非真实账号/菜单/数据；截图位于系统临时目录。

## 本地交付记录 — 2026-11-13
- 范围：合并销售与学生交付业务；接收资格独立、统一销售资格、部门根与跨部权限由 System/管理员配置；新履约归订单正式成交负责人，分班/转班/更换班主任同步服务负责人。待分班按负责人和部门复用，旧班与历史服务不回填、不搬迁。销售/交付目标与事实合并保留历史追溯。旧教育团队及独立交付业绩的活动入口代码按已登记范围清理，保留历史数据、必要兼容和旧首页 source 映射。
- 媒体/运营：不改 Person.inServicePeriod 或非销售编导运营原创建及读取合同；仅销售视角对可证明服务关联、附件和未知历史谈话施加范围及专属核对权限。销售新谈话需有效服务关联；非销售旧请求继续可用，其额外伪造服务字段不写入。客户姓名、手机号、微信仍为统一档案，有授权修改同步两部。
- 验证：统一 Maven 728 项为 0 断言失败、4 fixture 错误、5 环境跳过；4 个 fixture 修正后相关类通过，补跑班级 options 独立 MySQL 通过，3 个修正 CTE 在独立 MySQL 通过（3/3），另真实账号只读诊断因无指定目标跳过。业绩合成 MySQL Mapper 用例执行通过；本次快照清单 SHA256 0EE4B5E5D0E1DC9E21D1BBAA41D97CE474A8649025275243E91A3D71962BDCE9，最终逐文件复核未变化。Workbench 生产 build、相关服务测试、统一团队及业绩合成浏览器通过；履约桌面合成完成动作及 390px 列表视觉通过，移动详情/完成未验证。
- SQL：V371 SHA256 FA551082C38E74B8E4298CBC440955D77AC592D7668B0F92228BFFE250A70D93；专用 MySQL 8.4.11 完整 fresh 链通过，V370→V371 合成结构/索引/五元数据/账本版本一致，初次/重放/部分恢复/失败账本/HEX 通过；既有完整历史 upgrade fixture 在 V323 历史宽度与最新 baseline 比对处失败，未修历史 fixture。目标数据库及 ledger 只读对比未做。
- 上线前置：先核实目标环境和 V370 双账本、备份与审核 V371 并执行匹配升级；管理员明确配置销售一部/二部业务边界根，校验每名销售唯一映射，明确迁移人员组织和角色；通过 System 分配 `zsjos:sales:eligible`、按需 `zsjos:lead:receive`、`zsjos:sales:cross-department`、`zsjos:sales:boundary-configure` 和 `zsjos:media-student:history-review` 及原业务功能权限，未配置/重叠时禁止开放新版本流量。以上为待审核清单，未更改真实组织、角色、权限或共享数据库。
- 待真实验收：生产或指定环境 schema/数据及 ledger 对比，真实账号允许/拒绝与两部隔离、真实订单/资料/异步导出、旧凭证外链和存储访问、实际菜单授权及部署。历史无关联谈话不按当前人员部门推断；销售未知历史仅专属权限可核对。真实 Java 目标事务并发尚未端到端覆盖，现有证据为服务测试及独立 MySQL 锁测试。未提交、推送、部署或重启共享服务；保留既有和并发工作。
- 最终补充：有效成交负责人缺少销售业务资格时返回独立错误“缺少销售业务资格，请联系管理员配置”；账号不存在/停用保留原错误。DeliveryClassSalesBoundaryTest、DeliveryClassBatchCreateTest、UnscheduledDeliveryClassServiceTest 共 18 项通过。此后最终 Java 2,463 文件清单 SHA256 7a6230a00fe66fc24a43bde8f9f0a81fdc2fb614bd081400ccf9f1573ac461e2；仅资格提示分支及测试变化，不影响已通过的 SQL 和前端构建证据。
- 本任务专属 Vite 已停止；专属 MySQL 容器 codex-sales-merge-mysql-20261113 与 codex-perf-merge-mysql-20261113 已停止，合成 schema/镜像保留，未删除或操作共享服务。

## 登记更新 — 2026-11-14（Claude 接手审查后修复）
- 环境 local；分支 main；工作树 D:/ZSJ-OS；HEAD 21631a8a98（未变）；集成目标 None。本工作流前序写者均已交付，文件所有权释放；本轮由 Claude 主会话作为唯一协调者及本记录写入者，子代理按下列文件范围单写者执行。
- 用户新决定：(1) 客资、订单、学员、班级按负责人/班主任当前部门“跟人走”；(2) 审查中列出的可见范围收窄均不合理，应恢复（全量/跨部可见未分配客资、公海列表与详情一致、仅本人范围可本人转派与使用自己班级、提交人可见自己提交订单、协作者可见）；(3) 订单审批通过后自动分班：每个产品按 产品+商定考试月份+成交负责人(formalSalesUserId，录单人语义待用户复核) 找班，无则自动建班，班主任即服务负责人；履约确认仅补齐/校验资料，不再决定入班（方案一）；订单新增必填考试月份 YYYY-MM；旧文本转换规则待用户确认样例后再执行；(4) 业绩按快照计算，提供回填脚本把既有业绩归属快照及个人目标改为新部门/人员安排，执行须单独授权且在组织调整后。
- 非目标：执行回填或旧文本转换于任何非隔离数据库；修改真实组织、角色、权限；旧交付业绩菜单退役；提交/推送/部署。
- 文件所有权：主会话——SalesDepartmentBoundaryService 及测试、deliveryclass 包(Scope/ObjectPermission/ServiceImpl/ClassTransfer/Unscheduled/Exception)及 DeliveryClassMapper 与相关测试、V371 待分班索引回退及 core.sql/00-bootstrap-schema.sql 对应行；客资订单子代理——service/lead、dal/mysql/lead、service/order、dal/mysql/order、homemetric 及对应测试与 Workbench LeadManagementPage；业绩子代理——service/performance、dal/mysql/performance、controller/admin/performance 及测试、新 script/sql/mysql/repair/sales_delivery_org_backfill.sql；自动分班子代理——在主会话完成 SQL 回退后接手 V371/core.sql/00-bootstrap-schema.sql、订单 VO/DO/提交校验、RegistrationServiceImpl、订单审批生效监听、Workbench SalesOrderEntryModal 及相关展示、相关文档与测试。
- 验证计划：模块 testCompile 及受影响聚焦测试；V371 在专用隔离 MySQL 首次/重放/部分恢复/失败不登记；Workbench typecheck/build；回填脚本仅在隔离 schema 验证。

## 本地交付记录 — 2026-11-14 16:40（北京时间，Claude 主会话）
- 分支 main；工作树 D:/ZSJ-OS；HEAD 21631a8a98（未变，未提交）。
- 用户目标：修复审查发现的高危问题；落实“跟人走”、恢复被收窄的可见范围、订单审批通过后自动分班（方案一）、业绩按快照并提供回填脚本。
- 关键决定：部门边界服务对非销售用户回到原行为，仅持销售资格者配置错误时 fail-closed；新增 canTransferTo/filterTransferTargets/transferableSalesUserIds（选负责人只看部门边界，不看 System 数据范围）；边界结果按请求缓存。班级对销售按班主任是否可访问判断，不用 dept 快照；待分班恢复一人一个、随人更新部门，撤回 V371 按部门唯一索引。申请转班及审批校验目标班主任边界，审批时不合格标记 invalidated。学员协作者（编导/职业规划/媒体运营）可见性恢复。订单新增必填 agreedExamMonth（YYYY-MM），审批通过同事务按 产品+月份+formalSalesUserId 自动建/入班（新模式 auto_class，履约只校验资料）；成交负责人缺失/停用/无销售资格时阻断审批并提示。业绩：读取按快照、冲突行不再拖垮整页、中心负责人不报边界错；新增 repair/sales_delivery_org_backfill.sql（备份后回填归属快照及个人目标到当前部门，org_source 保持原值）。生成列表达式改为 `exam_month > ''`，避免 schema 工具把 “IS NOT NULL” 识别为列约束。
- 子代理：performance 完成并回报；lead-order、auto-class 交付后无响应 35+ 分钟，由主会话停止并接手收尾（补 s.deleted、前端、管理后台重提、文档、测试修正、SQL 验证）。
- 变更文件（本轮）：service/sales/SalesDepartmentBoundaryService 及测试；enums/ZsjosErrorCodeConstants；deliveryclass（Scope/ObjectPermission/ServiceImpl/ClassTransfer/Unscheduled，新增 AutoDeliveryClassService、AutoDeliveryClassMapper）及测试；registration（RegistrationServiceImpl、MyStudentServiceImpl、Student*PermissionProvider，新增 AutoClassEnrollmentService）及测试；studententry/StudentEntryService；lead/order/homemetric 相关服务、Mapper 与测试；performance 包、VO/DO 与测试；order VO/DO/SalesOrderServiceImpl/Snapshot；V371、core.sql、00-bootstrap-schema.sql、migrations/README.md、tools/test_sales_delivery_merge.py、新增 repair/sales_delivery_org_backfill.sql；Workbench SalesOrderEntryModal/DetailCards/TableColumns、api.ts、SalesPerformanceTargetPage、salesPerformance.ts 等；Admin views/zsjos/mySalesOrder/index.vue；docs/api 下 delivery-class-management、registration-fulfillment-api、zsjos-sales-order、sales-performance、delivery-performance 等。
- 验证：模块全量 7576 项，对照 HEAD 导出副本基线（7575 项，66 失败/错误），现余失败类均在基线中且失败原因一致，无新增回归；自动分班/订单/边界专项 151 项通过；V371 专用隔离 MySQL 8.4.11 首次/重放/部分恢复/前置失败/账本失败恢复/冲突/中文 HEX/自动分班唯一键全部 PASS；zsjos_db test-fresh 临时容器 392 迁移、结构差异 0、PASS；回填脚本由业绩子代理在独立隔离 schema 验证首次/重放 0 变更/失败回滚/恢复/HEX。Workbench typecheck 通过；Admin ts:check 仅 mes 模块既有未使用变量错误。未做浏览器验证、真实账号与真实数据库验证。
- 依赖/集成影响：V371 未部署，仍禁止应用到共享库；上线前须管理员配置销售边界根及权限，并给原仅有 zsjos:lead:education-self-sourced:create 的人员补 zsjos:lead:self-sourced:create、给原 education-team 主管补 subordinate-sales 权限。回填脚本须组织调整后单独授权执行。专用容器 codex-sales-merge-mysql-20261113 已停止。
- 待办：用户确认 班主任=成交负责人、回填执行授权、旧考试时间文本转月份规则（含 12/26 类歧义）；文本转换脚本待确认后编写；浏览器端到端验证；旧交付业绩菜单退役未处理。
- 更正（2026-11-15）：撤回 SubordinateSalesDailyMapper.selectContactedStudents 的 s.deleted=0。docs/api/education-team.md 规定服务关系逻辑删除后已提交联系仍计工作量，该过滤与既有契约冲突，客资子代理此前已据此暂停；如需改变口径须用户确认。其余记录不变。

## 本地交付记录 — 2026-11-15（北京时间，Claude 主会话）
- 分支 main；工作树 D:/ZSJ-OS；HEAD 21631a8a98（未变）。用户确认班主任=成交负责人；旧考试时间文本由 AI 判断规则、歧义项交用户。
- 新增 script/sql/mysql/tools/exam_month_conversion.py：只读分类导出 TSV，生成报告与逐行 UPDATE SQL（仅 agreed_exam_month 为空且原文本未变时写入，先备份到 zsjos_bak_order_exam_month，建表在事务外）。规则：明确年月、明年X月、仅月份/月.日按下单月份推算最近不早于下单月的月份；无/待定/待确认/最近一期留空；年份、统考、跨月区间、课程名等列为待人工判断。
- 验证：本地开发库只读导出 579 条，可转 468、无信息留空约 40、待人工 71 条（20 种文本）；生成 SQL 在专用隔离 MySQL 新 schema em_conv_20261115 执行两次，写入 468 条、重放无变化、备份 468 条、中文 HEX 正确；容器已停止。未在任何开发/共享库执行。
- 待办：用户判断 20 种歧义文本；之后按目标环境重新导出生成 SQL，单独授权执行。
- 更正（2026-11-15）：用户决定旧数据仅作历史存储、不转换，新规则只适用新数据；驳回后重提视同新提交（必填考试月份、通过后自动分班）。已删除未跟踪的 script/sql/mysql/tools/exam_month_conversion.py，上一条中的转换工具及待判断歧义项作废；历史订单 agreed_exam_month 保持为空，审批通过走原 sales_owner 流程。

## 开发库执行 V371 — 2026-11-15（北京时间，Claude 主会话，用户授权）
- 目标：本机 Docker yudao-mysql / ruoyi-vue-pro（tenant 1 开发库）。执行前两账本均为 V370、五项菜单与新列不存在，前置父菜单 zsjos:lead:query、zsjos:media-student:query-my 各 1。
- 备份：mysqldump 8 张表（system_menu、zsjos_order、zsjos_delivery_class、zsjos_performance_target、zsjos_performance_org、zsjos_media_student_talk_record、两张版本账本）到 D:/ruoyi-vue-pro-pre-v371-20261115.sql。
- 结果：V371 执行成功并重放成功；两账本新增 V371；五项菜单各 1、6 列、uk_delivery_auto_class 已建；中文名 HEX 正确；无 system_role_menu 授权；历史订单 agreed_exam_month、班级 auto_class_guard 均为空。未重启后端（进程 10832 仍运行改动前代码），未配置边界根或任何账号权限。
- 回滚限制：DDL 已提交，需按 V371 头部说明手工删除新增列/索引/菜单及账本行，或用上述备份恢复相关表。

## 开发环境配置与浏览器验证 — 2026-11-16（北京时间，Claude 主会话，用户授权）
- 授权范围：本机开发库 ruoyi-vue-pro 配置边界根与测试权限；重启本地后端。
- 备份：system_role_menu、zsjos_performance_org 导出到 D:/ruoyi-vue-pro-pre-grants-20261116.sql。
- 配置（tenant 1，两次执行结果一致）：zsjos_performance_org id 1(dept 1021 销售转化一部)、id 4(dept 1050 学生服务与交付中心) 设 sales_boundary_root=1；system_role_menu 新增 creator=claude-dev-20261116：销售业务资格→角色 3006/3007/3012/3014，接收客资→3007，统一自拓→3012，交付主管 3014 复制销售主管全部 zsjos:subordinate-sales:* 共 13 项。四个角色仅两部门 17 名在职员工持有。撤回：DELETE FROM system_role_menu WHERE creator=claude-dev-20261116，并将上述两行 sales_boundary_root 置 0。
- 后端：停止原进程 10832，离线打包 yudao-server 后以相同命令 java -jar backend/yudao-server/target/yudao-server.jar --spring.profiles.active=local 启动，36 秒就绪、健康检查 200。Workbench vite 启动于 5174。
- 浏览器（ego-browser，浏览器内既有登录账号为行政部门非销售用户）：学员管理、班级管理、订单管理、学员信息汇总、考试资料收集、客资管理、销售业绩 7 个路由共 157 个 zsjos 接口调用，未出现销售边界缺失错误；唯一非 0 为学员详情内 lead/get 无权（该账号本无客资读取权限，属原权限行为）。未执行任何写操作。销售账号流程（订单月份必填、审批自动分班、跨部隔离）因无测试账号凭据未在浏览器验证。

## 多账号端到端验证 — 2026-11-16（北京时间，Claude 主会话，用户授权使用开发库测试账号）
- 账号由开发库查得：销售专员 GuoGuo(17)、销售主管 ChengZhenJian(21)、学习规划师 XiaoJiang(27)、交付主管 LiuJing(15)，另 LiFeng、XiaoShu 仅登录。密码不入记录；临时令牌文件已删除。
- 发现并修复代码缺陷：Yudao 数据权限规则对 system_dept/system_users 按登录人数据范围过滤，导致部门边界服务查不到上级根部门或同事（交付一部账号全部报“边界缺失”，仅本人范围销售无法转派同事）。SalesDepartmentBoundaryService 类级 @DataPermission(enable=false)，范围仍由 getDeptDataPermission 显式判断；新增 organizationLookupsIgnoreCallerRowDataPermission 测试，边界服务测试 10 项通过。后端已重新打包重启。
- 追加开发库测试授权（creator=claude-dev-20261116，可整体撤回）：销售专员 3007 复制学习规划师的 delivery-class/class-transfer/student/student-service 权限及上级菜单；销售主管 3006 复制交付主管同类权限；学习规划师 3012 复制销售专员、交付主管 3014 复制销售主管的 zsjos:sales-performance* 权限。最终 3006=17、3007=10、3012=5、3014=22 条。仅删除受影响菜单的 menu_role_ids:1:* Redis 缓存键（直接写库不会触发缓存失效）。
- 接口验证（只读）：班级——销售专员仅见本人班、选项含全部销售同部门班主任；销售主管见全部销售部门班；规划师仅本人；交付主管见全交付部门；跨部门读取班级 53/117/118 均按预期拒绝。客资、订单跨部门读取拒绝、本部允许；销售主管无 query-all 读未分配客资被拒（符合设计）。四账号业绩 tree/overview 正常。
- 浏览器（ego-browser，127.0.0.1:5176 独立登录态，不影响 localhost 既有会话）：GuoGuo 客资“录入成交”表单显示必填“商定考试月份”月份选择器及自动分班提示，旧文本输入框已移除；未选课程时前端先拦截金额校验，未发请求；后端缺月份/格式错误返回 400 且未产生订单。关闭了 GuoGuo 的两条版本更新通知（写入已读记录）。未提交订单、未走审批，故审批后自动分班未做端到端验证（单元测试覆盖）。
- 环境现状：后端 48080 运行新代码；Workbench vite 5174 仍运行，5176 已停止。
- 上线提示：合并后销售角色需补班级/学员权限、交付角色需补统一销售业绩权限；直接写库授权需清理对应 Redis 缓存或经管理后台分配。

## 审批后自动分班端到端 — 2026-11-16（北京时间，Claude 主会话，用户授权使用任意开发账号并产生测试数据）
- GuoGuo 经 API 上传凭证并提交测试订单 9229（客资 8377，商品 中医食疗调理师 product 47 + 中医健康管理师 product 43，考试月份 2027-03）；报名履约中心 baomingfuwu001 与财务 WangNa 分别通过 BPM 任务。订单生效同事务内：建 2 个 auto_class 报名任务（1280/1281），自动新建班级 130“中医食疗调理师 2027年3月”、131“中医健康管理师 2027年3月”（NORMAL、SERVING、班主任 17、部门快照 1021 销售转化一部、考期 2027-03-01~2027-03-31、auto_class_guard=产品ID，中文 HEX 正确），服务 2107/2108 负责人 17。
- 复用：GuoGuo 对客资 8266 提交仅含 product 47 的订单 9232，审批后服务 2109 进入既有班级 130，该键班级数仍为 1。
- 可见性：班级 130 对 GuoGuo、销售主管可读，对交付主管、学习规划师拒绝；班内学员 2 人。报名任务 1280 显示 auto_class、不可分班、提示“已建立交付服务，变更班级请通过班级管理办理转班”。
- 开发库新增测试数据（未清理，可按需删除）：订单 9229、9232 及订单项、审批轮次与 BPM 流程，报名任务 1280-1282 左右，班级 130/131，服务 2107-2109，凭证文件 15747-15750（15748、15749 为因客资联系方式不符而未使用的上传）。临时令牌与联系方式文件已删除。

## 开发库组织调整与业绩回填 — 2026-11-16（北京时间，Claude 主会话；用户决定不提交代码，仅在开发库完成调整与验证）
- 用户确认销售二部=既有部门 1122“销售转换二部”。备份：system_users、system_dept、zsjos_performance_org、zsjos_performance_target、zsjos_delivery_class 导出到 D:/ruoyi-vue-pro-pre-org-restructure-20261116.sql。
- 组织配置经 admin 调用 PUT /zsjos/sales-performance-target/organization（验证 boundary-configure 路径）：新增 1020 CENTER、1122 DEPT(中心 1020) 并设为销售边界根；1050 取消边界根。现边界根：1021 销售转化一部、1122 销售转换二部。
- 人员：交付一部 7 名在职员工（15、27、30、37、51、400、528）dept_id 由 1051 改为 1122，1051 已无人；1122 负责人由已停用的 57 改为 15（刘景），updater=claude-dev-20261116。
- 业绩回填：sales_delivery_org_backfill.sql 以 @tenant_id=1 在开发库执行，首次更新归属快照 2469 行（预估部门变化 2453 行，余 16 行为中心/名称差异）并备份 2469 行，个人目标 0 行；第二次执行全部 0；不一致行 0；被调人员快照全部为 1122 销售转换二部 / 1020 销售转化中心，org_source 保持原值（frozen 1942、current 498、NULL 13）；中文 HEX 正确。
- 复验（重新登录取令牌）：班级、学员、订单、客资、业绩各账号结果与调整前一致；跨部门读取客资/订单/班级均拒绝；业绩树中交付主管看到 1020→1122→7 人，部门业绩 1122 仅交付主管可看、1021 仅销售主管可看。未改代码、未提交。
- 撤回：用上述备份恢复 system_users/system_dept/zsjos_performance_org 相关行；业绩快照用 zsjos_bak_sdm_perf_attribution 按脚本头部恢复 SQL 还原。

## 开发库清理学生服务与交付中心及角色合并 — 2026-11-16（北京时间，Claude 主会话，用户指示）
- 备份：system_dept、system_user_role、system_role、system_role_menu、zsjos_performance_org、zsjos_delivery_class 导出到 D:/ruoyi-vue-pro-pre-delivery-cleanup-20261116.sql。脚本执行两次结果一致。
- 角色：销售专员 3007、销售主管 3006 先补齐原学习规划师 3012、交付主管 3014 的业务权限（学员联系、协作者分配、学员档案、批注、学员复购等；排除已下线的 education-team:* 及 education-self-sourced:create）；原持有人 3014→3006（刘景）、3012→3007（6 名学习规划师）；3012/3014 角色及其授权逻辑删除。
- 部门：1051 学生服务与交付一部、1050 学生服务与交付中心 逻辑删除（无人员、无 BPM 模型引用），对应业绩组织映射逻辑删除；dept_manager(3002) 自定义数据范围去掉 1050/1051、补入 1122；33 个原 dept 1051 的班级按现班主任部门刷新快照为 1122。
- 代码：AdvancedFilterVisibleUserService.studyPlannerOptions 由按角色编码 study_planner 查询改为按 zsjos:sales:eligible 资格（移除 RoleApi 依赖），相关 11 项测试通过；未提交。
- 清理 Redis 中 dept/role/user_role/menu_role/permission_menu 缓存 91 个键；后端重新打包以独立进程启动，健康检查 200。
- 复验：刘景=销售主管、学习规划师=销售专员，均在 1122；班级/学员/订单/客资/业绩接口及跨部门拒绝结果与清理前一致。所有变更 updater/creator=claude-dev-20261116，可按备份恢复。

## 数据范围验证（一部主管/二部主管/中心负责人）— 2026-11-16（北京时间，Claude 主会话）
- 现中心 1020 负责人为超管账号 526（超管例外，不代表一般配置），故新建开发测试账号 e2e_center(530，部门 1020) 与测试角色 e2e_sales_center_manager(4211，数据范围=本部门及以下，权限=销售主管全部业务权限去掉销售业务资格，另加 zsjos:sales:cross-department、sales-performance:center 及 lead/sales-order/student/delivery-class 的 query-all)，creator=claude-dev-20261116；之后清理 role/user_role/menu_role/permission_menu Redis 缓存。
- 结果（各列表抽样前 500 条，按订单正式负责人/服务负责人/班主任当前部门归类）：一部主管 ChengZhenJian 客资/订单/学员/班级仅一部（客资另有 5 条未分配，为其可见的待接收/回收类），二部业绩被拒；二部主管 LiuJing 全部仅二部，一部业绩被拒；中心负责人客资/订单/学员/班级同时含一部和二部，业绩树含两个部门。
- 业绩金额核对（2026-11 月度）：中心负责人看一部 56011.9/26 单、二部 12291.0/19 单、中心 68302.9/45 单=两部之和；一部主管看中心仅得一部数，二部主管看中心仅得二部数。
- 配置结论：中心负责人须不持有销售业务资格（否则位于边界根外会按配置错误拒绝），用数据范围“本部门及以下”覆盖两部；若需跨租户全量还需 query-all。

## 登记更新 — 旧业务残留清理（Claude 主会话协调）
- 用户决定：客资相关旧“教务”快照全部回填为销售且不保留兼容分支；两端报名履约页支持 auto_class（Admin 同时支持 sales_owner）；履约清单取消“配置学习规划师”固定项；清理旧菜单、权限与种子；按 一→二→三 顺序清理，历史兼容类保留。不提交代码，数据库仅开发库。
- 子任务单写者：edu-removal——service/lead、controller/admin/lead、dal/mysql/lead(除 PersonMapper)、LeadConstants/LeadAssignmentConstants、advancedfilter 场景、homemetric、相关前端(含 workbench services/api.ts、constants/RouteHost/menuComponentRegistry)、新 repair/sales_education_identity_backfill.sql；registration——service/registration、controller/admin/registration、studentcontact、各通知/文案文件、ZsjosErrorCodeConstants、两端履约页与清单配置页；sql-cleanup——script/sql/mysql/**、service/performance、dal/mysql/performance、docs/frontend/zsjos-menu-coverage.md。主会话负责统一编译测试、开发库执行与本记录。
- 验证计划：模块编译与全量测试对比基线、两端类型检查、V371/修复脚本隔离库验证、test-fresh、开发库执行后多账号接口复验。

## 本地交付记录 — 旧业务残留清理 2026-11-16（北京时间，Claude 主会话）
- 三个子代理（edu-removal / registration / sql-cleanup）完成后均已停止。主会话补修：RepurchaseCustomerService、SalesOrderServiceImpl、PartnerPortalServiceImpl 三处引用已删常量；LeadInboxFilterConfigServiceImpl 多余括号；LeadManagementServiceImpl 残留 salesPoolAllowed 调用；删除无引用错误码 HOME_METRIC_LEGACY_FILTER_UNMAPPABLE；BusinessEventMapper 去掉 education_self_sourced_auto；SalesOrderSourceSnapshotTest 改为教务来源已退役。
- 代码结果：客资不再有教务身份与教务专属规则，教务自拓/教务团队/education_team 筛选场景/首页教务指标来源及前后端入口删除；兼职分配仅销售承接；两端履约页支持 auto_class（Admin 另支持 sales_owner）；履约清单取消学习规划师固定项与流转，删除规划师接口；文案统一为班主任/销售主管；新业绩快照恒为 sales，delivery 单独查询分支为空；绩效 SQL 去除 education 自动来源。
- SQL：V371 增加 15 项旧菜单退役（不改 system_role_menu）；01-bootstrap-system-seed 删除 1050/1051 部门、2012/2014 岗位、3012/3014 角色；verify-bootstrap 同步；新增 repair/sales_education_identity_backfill.sql。
- 验证：模块全量 7391 项，失败类全部属于 HEAD 基线、无新增；Workbench typecheck 通过，Admin 仅 mes 既有错误；sql-cleanup 报告 V371 隔离生命周期 PASS、test-fresh EXIT 0（392 迁移、drift 0）、test-guardrails EXIT 0；回填脚本隔离验证（首次/重放 0/租户隔离/失败回滚/HEX）。
- 开发库（备份 D:/ruoyi-vue-pro-pre-legacy-cleanup-20261116.sql）：重跑 V371 两次，15 项菜单 deleted=1/status=1/visible=0，账本各 1；教务回填 tenant 1 首次更新 lead 387、activation 28、分配历史 388、跟进 389、商机跟进 452、订单 162、用户关系 1、事件 330、任务 330、收件箱方案及版本各 1，备份 2469 行，重放全 0；残留核对项为历史审计（流程变更记录中的 before/after“教务”快照 114 条、修复标记 54 条、人工填写原因 3 条等），按历史记录不改。履约模板：学习规划师清单项 5 条、流转 5 条逻辑删除（为避开含 deleted 的唯一键，先物理删除 4 条无引用的已删除孪生行 14/24/5/8），历史任务快照 321 条保留。清理 Redis 菜单/角色缓存，后端重新打包启动。
- 复验：教务自拓与教务团队接口 404；账号权限中无退役权限；原学习规划师客资身份全为 sales、来源全为 sales_self_sourced；auto_class 任务正常；一部/二部/中心负责人数据范围与跨部门拒绝结果与清理前一致。
- 遗留（未处理，待决定）：V094 种子的 4 个以 study_planner 岗位为目标的 user_relation_scene 及 verify 第 820 行；registration-early-delivery-notification.sql 接收人规则仍为 ["study_planner"]；tools/test_performance_attribution_review.py 依赖开发库未删除的 delivery-performance 菜单；test_sales_delivery_upgrade.py 未重跑。

## 本地交付记录 — 剩余旧角色引用清理 2026-11-16（北京时间，Claude 主会话）
- 用户决定：V094 关系场景同步修改；提前交付通知接收人同步调整；旧业绩测试工具清理。
- 代码：通知接收人角色键改名 study_planner→head_teacher、delivery_supervisor→sales_supervisor（RegistrationConstants.NOTIFY_ROLE_HEAD_TEACHER、StudentContactConstants.NOTIFY_ROLE_HEAD_TEACHER/NOTIFY_ROLE_SUPERVISOR、TeachingServiceCloseNotifySceneProvider.SUPERVISOR 及测试）。
- SQL：V371 事务内新增（保留稳定 code）V094 四个关系场景岗位 study_planner→sales_specialist、标签与名称“学习规划师”→“班主任”，zsjos.* 通知规则 recipient_roles 键改名，均有后置检查；头注释补充范围。registration-early-delivery-notification.sql 接收人改为 head_teacher。删除 tools/test_performance_attribution_review.py 并更新 README 中的引用（误执行 git rm --cached 后已立即 git reset 恢复索引，仅保留工作区删除）。test_sales_delivery_merge.py 增加场景与通知规则夹具和断言。
- 验证：V371 隔离生命周期三段 PASS；提前交付通知脚本测试 PASS（在 yudao-mysql 新建临时库，未改 ruoyi-vue-pro）；test-fresh EXIT 0（392 迁移、drift 0）；通知/履约/学员联系/关系场景相关 185 项测试通过。
- 开发库（备份 D:/ruoyi-vue-pro-pre-scene-notify-20261116.sql）：重跑 V371 两次成功；4 个场景改为班主任/销售专员；通知规则 head_teacher 16、head_teacher+sales_supervisor 4、sales_supervisor 1，无旧键；7 名原交付人员岗位改为销售专员 6、销售主管 1，岗位 2012/2014 逻辑删除；清理 Redis 权限缓存，后端重新打包启动，协作者上下文与数据范围冒烟正常。
- 观察到其他会话（updater codex-retire-unscheduled-20261116 等）同期修改开发库班级与服务数据（18 个待分班班级逻辑删除、新服务 2110），非本工作流所为，数据范围结果仍符合预期。
