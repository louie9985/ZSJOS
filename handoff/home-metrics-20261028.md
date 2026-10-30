# 首页动态指标工作流

## 2026-10-28 18:14 北京时间 — 注册

- ID / owner: home-metrics-20261028 / Codex root（唯一写入者）。
- Environment: local；ZSJOS_AGENT_ENV 未设置，环境文件不存在，按规则回退。
- Branch: codex/home-metrics-20261028。
- Worktree: D:/ZSJ-OS-home-metrics。
- Base / HEAD: 0628f72f2c6208a6cc553f3f29b893de8f4ec697。
- Goal: 首页动态指标、系统管理配置、完整聚合和公式运算、权限范围、弹窗/深链、配置发布版本。
- Confirmed: 客资计客资、订单计订单、学员计去重学员；完整运算含聚合、四则、比率、跨来源、同比环比；除零不可计算，无权限操作数不返回整项数值。
- Non-goals: 任意 SQL/脚本、修改真实权限、主分支提交推送、合并发布、引入新依赖。
- Ownership: 本工作树内 docs/design/home-metrics.md；本记录；后续专属 homemetric 后端源码/测试、Admin home-metric 页面/API、Workbench 指标组件/API及 TodayTasksPage 接入、对应迁移和专项验证脚本、直接受影响文档。具体共享文件修改前复核当前内容。
- Dependencies: 仅 committed base 的高级筛选、业务授权服务、System 公共 API、BPM 公共能力；不依赖 main 未提交修改。
- Target branch / integration order: None（尚未授权集成）。
- Verification: 计算/周期边界及异常单测；各来源授权和去重/聚合一致性；配置并发/版本测试；MySQL 首次/重复/故障恢复；双端类型和构建；真实浏览器桌面/移动流程。
- Isolation: 用户授权新分支开发且不影响 main；独立工作树遵循根 AGENTS.md 第 7 节。不得复用 main 的输出目录、服务端口或测试数据库。

## 2026-10-28 18:18 北京时间 — 需求确认及隔离准备

- Branch/worktree/base: 同注册；HEAD 未变。
- Goal: 固定用户确认的完整运算范围并准备独立开发位置。
- Decisions: 用户进一步明确确认聚合、四则、比率、跨来源、同比环比，除零不可计算，任一操作数无权限时整项不返回数值。复合公式下钻先呈现操作数再进入明细。
- Result: 创建新分支和独立工作树；设计已记录。尚未实现业务代码，不能作为功能交付或上线验收。
- Changed files: docs/design/home-metrics.md；本记录。
- Verification: git diff --check 通过（新增文件另经内容复核）；工作树仅两个新文档；主工作树仍 main，HEAD 为注册基线，未切分支、未暂存/提交/推送。
- Dependency/integration impact: None；未复制 main 未提交改动，未写数据库或启动服务。
- Remaining: 来源逐项适配、计算/周期核心、配置持久化及 API、双端界面、SQL/权限/浏览器验收；完整功能尚未完成。

## 2026-10-28 18:30 北京时间 — 计算核心阶段记录（整体仍在开发）

- Branch/worktree/base: 同注册；本分支 HEAD 未变。
- Goal: 执行用户“开始开发”，首先实现并验证可复用计算核心。
- Decisions: 不限时间不注入日期条件；跨来源比率支持无量纲百分比；非法公式不执行；除零、空集、超时、来源失败、溢出分别处理；执行前后均校验操作数权限。
- Changed: service/homemetric/{MetricFormula,MetricFormulaParser,MetricPeriod,MetricUnit,MetricAggregate,MetricEvaluation}.java；对应 5 个测试类；docs/design/home-metrics.md；本记录。
- Result: 实现受限 AST/文本解析、四则/负数/括号、BigDecimal 求值、单位推导、聚合空集规则、北京时区周期/同比环比、权限预检和结果撤权丢弃。真实来源适配尚未实现，核心没有 Controller/生产调用方。
- Verification: Maven reactor 编译及 30 项专项测试通过，0 failures/0 errors/0 skipped；命令 `mvn -f backend/pom.xml -pl yudao-module-zsjos -am '-Dtest=MetricFormulaTest,MetricPeriodTest,MetricUnitTest,MetricEvaluationTest,MetricFormulaParserTest' '-Dsurefire.failIfNoSpecifiedTests=false' test -q`。报告在本工作树 backend/yudao-module-zsjos/target/surefire-reports。首次 Maven 参数被 PowerShell 拆分已改为完整引号；中间验证跨编辑阶段产生旧主类/新测试混合结果，稳定输入重跑已通过。最终 git diff --check 通过；新增源码通过编译和测试。
- Isolation: 未提交、推送、改主分支文件或数据库，未启动服务。检查时 main 已由其他工作推进到 ba3e687e678ce92b67f16615244e47007d0892d4；本任务未跟进合并或复制该变化。
- Limitations: 时间预算只是查询边界检查，真实 SQL 必须设置语句超时。没有数据库、API、双端或浏览器验证；这些层尚未实现，不属于环境阻断。
- Remaining: 各高级筛选来源的授权聚合与明细（学员按身份去重）、持久化/版本/权限 API 和 SQL、Vue 管理界面、React 首页/深链、集成验证。整体功能不完整，不可标记 ready-to-merge。

## 2026-10-29 15:28 北京时间 — 范围更新

- 延续原工作流、分支、工作树和 owner；环境仍为 local（环境变量及环境文件未设置）。
- 新增独占修改范围：StudentServiceReadService.java、StudentServicePageMapper.java、StudentServicePageSql.java，以及 registration 包内本功能专属测试。
- 目的：复用服务级授权查询统计去重学员，不复制授权逻辑。验证真实生成 SQL 的去重/隔离行为及 Service 权限拒绝路径。其他数据源和管理界面继续待实现。

## 2026-10-29 15:32 北京时间 — 学员授权聚合入口

- 分支/工作树/HEAD：沿用注册，HEAD 未变；owner 为 Codex root。
- 用户目标：继续首页动态指标开发。
- 决策：按现有 person_id 去重；复用原有服务级查询和高级筛选；既有页面仍按课程服务分页；新增 countPeople 查询设置 5 秒语句超时。
- 修改文件：StudentServiceReadService.java、StudentServicePageMapper.java、StudentServicePageSql.java、StudentMetricCountTest.java、设计文档、本记录。
- 验证：Maven reactor 编译及 StudentMetricCountTest（3）、StudentServiceReadServiceTest（17）、Metric*Test（30）通过；git diff --check 通过。生成 SQL 的执行验证使用现有 H2 测试设施，不代表 MySQL 验收。
- 集成影响：新增内部 Service/DAL 入口，尚无配置 API/首页调用；未修改数据库、主分支文件，未提交/推送。
- 剩余：指标来源注册及其他业务聚合、配置持久化和发布/权限 API、双端界面、深链、MySQL 和端到端验收。整体仍未完成。


## 2026-10-29 — 用户授权多智能体完整推进

- 原分支/worktree/base/环境不变；root 负责集成与唯一 handoff 写入。
- sources 子任务独占：homemetric/source/**、相应业务 Service/DAL 的指标适配方法、源码外 source 测试；不得改配置 DTO/Controller/计算核心。
- config 子任务独占：homemetric 配置 DTO/Controller/DO/Mapper/Service（不含既有计算核心及 source/**）、对象权限、SQL 迁移与专项脚本、对应配置测试。先公布公共契约再实现。
- frontend 子任务独占：Admin 指标管理页面/API、Workbench 指标组件/API/TodayTasksPage/受控路由及菜单映射、对应前端测试。与 config 对齐接口，不创造生产选项。
- root 独占：既有 Metric* 计算核心、设计/交付文档、集成测试与最终验收。所有 Maven 构建由 root 串行执行，子任务提交测试代码而不争用 target；Admin/Workbench 检查由 frontend 独占。
- 禁止提交/推送/合并、修改主工作树或共享数据库；隔离数据库验证由 root 协调。目标是完整交付，不能将核心或占位 UI 当成最终功能。

- 2026-10-29 root 范围补充：System PermissionApi/Impl 与专属单测增加只读 permission 元数据存在性能力，供指标配置验证；不访问 System 私有 DAL、不更改现有授权语义。

- root 扩展来源所有权：新增 ExistingPageMetricSources.java 及其测试，调用已存在授权分页 total（只取1条而非扫描分页），覆盖申诉/查重/报名/媒体学员/下属销售/教务团队。sources 保持其主注册实现所有权，按公开方法委托 root 类。

- sources 范围补充：BpmProcessTaskApi/Impl 及专属测试，新增明确全待办只读分页API，内部复用BPM已有任务查询；不复制BPM DAL或更改工作流。
- root 范围补充：script/sql/mysql/tools/test_home_metrics_mysql.py（新建隔离验证schema，仅合成fixtures，保留验证库不删除）。

## 2026-10-29 16:44 北京时间 — 完整实现与本地隔离验证交付

- Branch/worktree/base: 同注册；HEAD 仍为 0628f72f2c6208a6cc553f3f29b893de8f4ec697；未提交、推送、合并。环境 local。主工作树仍 main，本任务没有向其写文件。
- 用户目标：完整推进动态首页指标，用户明确授权子智能体逐步实现。root 协调 config/sources/frontend 三个子任务，按登记文件归属集成。
- 实现：Admin 系统管理配置、权限目录与人员/部门交集、高级筛选、数值聚合/四则/比率/跨来源/同比环比、草稿预览/发布/停用/复制/回退/CAS；Workbench 指标层全部配置驱动，四并发、逐卡重试、操作数说明、安全明细、弹窗与可刷新受控深链。原下方任务面板保留。目录覆盖 5 个 SQL 聚合来源、6 个业务分页来源及 2 类内置待办；可聚合字段/时间能力由服务端明确返回，不支持的字段不伪造能力。
- 关键边界：所有来源复用原授权；学员按 person_id 去重；数字字符串保精度；字典历史标签快照及执行时失效校验；上下文绑定用户/租户/版本/15分钟有效期，返回前后复检；普通响应裁剪敏感筛选/范围。Range JSON 为北京时间转换的毫秒时间戳，专用序列化不依赖JVM时区；Instant为ISO字符串。核心时间预算改用System.nanoTime，冻结业务Clock不影响超时。
- Changed files: homemetric Controller/Service/DO/DAL/测试；原 AdvancedFilter 条件及编译器、Lead/Student/Cashback 指标适配；System PermissionApi 元数据与测试；BPM 全待办公开 API/DTO及测试；Admin homeMetric 页面/API/metricTime及fixture；Workbench HomeMetricCards/API/decimal/TodayTasksPage及测试；feature SQL/隔离验证脚本；docs/api/home-metrics.md、设计、权限流/边界/菜单覆盖文档、本记录。真实角色授权和业务库无改动。
- 后端验证：最终 Maven reactor 指定 PermissionDefinitionApiTest、BpmAllTodoMetricApiTest、HomeMetric*Test、Metric*Test、ExistingPageMetricSourcesTest、StudentMetricCountTest、StudentServiceReadServiceTest、AdvancedFilterServiceTest、AdvancedFilterAccessContractTest：123项，121通过、0失败、0错误、2项原 StudentServiceReadServiceTest 跳过。最后明细安全标签小修后单独 ExistingPageMetricSourcesTest 4项再次通过。生成SQL执行覆盖重复金额SUM、授权范围、无范围0、多课程学员去重、财务个人/财务视图；使用H2，非真实MySQL业务数据验收。JSON测试覆盖UTC JVM往返、权限上下文脱敏和大数字。
- 数据库验证：最终脚本 SHA256 C35DF8F1FD6C4C9E52C148216061E140A30345567DB497F19E909DC26C7CF605；MySQL 8.4.11 下 7组通过：首次/重复、前提失败恢复、部分DDL恢复、账本失败原子回滚恢复、已有标记补结构、错误列类型拒绝及修复、菜单身份冲突不登记。中文 HEX 和 admin_only 验证通过。最终验证库前缀 metric_verify_20261029163014_，只复制基础表结构及合成fixture，source_rows_copied=0，未删除验证库。
- 前端验证：Admin/Workbench生产构建通过；Workbench typecheck通过；4项本功能专项测试通过。两端浏览器fixture覆盖1440/390、预览/保存/发布/冲突、深链刷新/返回、空态/无权限/除零/超时、并发响应与北京时间展示；浏览器America/Los_Angeles下日期断言通过。root复核移动截图并修复Admin日期摘要截断。脚本/截图见两端test/home-metric*。专用Vite5188/5189已停止，未动48080运行服务。
- 无关基线失败：Admin全量typecheck仍有withdrawal接口12条重复字段错误；Workbench完整todayTasks+decimal 13项中一条旧协助客资导航静态断言与HEAD已有实现不一致（12通过）；扩大AdvancedFilterCompletenessTest有2条基线目录/覆盖manifest遗漏（partnerSubmitterId选项authority、education_team/student_service未列入manifest），相关目录/测试文件与本分支HEAD未变。未自动修复或扩大任务。
- 集成/未验收范围：feature SQL 当前使用 HOME_METRICS 标记，未加入主分支正式编号链。合并时必须按目标分支最新迁移分配编号并验证fresh/upgrade，不能占用或改写已存在/已部署V354。未对业务库迁移、未授真实账号权限、未重启后端或部署；浏览器fixture不代表真实账号/真实后端端到端或性能验收。新服务完整Spring启动尚未验证，模块编译和接口契约测试已过。
- 状态：本地实现及上述隔离验证完成；未标记 ready-to-merge（未获提交/集成授权，正式迁移编号及目标环境验收仍属集成阶段）。所有子任务写入已释放，后续集成前重新检查目标HEAD与迁移序列。

## 2026-10-30 10:29 北京时间 — 审查修复注册更新

- 用户授权实现完整修复建议，明确布局只支持六列两行；启用上限调整12，明细不占卡片网格。不做额外布局拓展。
- 延续原独立分支/工作树/base与local环境。root负责核心、迁移集成方案、文档和统一后端验证；config负责配置/上下文/快照；frontend负责两端状态和同版本展示；sources负责来源权限及申诉/BPM授权统计，ExistingPageMetricSources交由sources唯一写入。
- 不提交/合并/推送，不改主工作树或真实权限/业务库。Maven由root统一，前端验证由frontend独占。注册复用，不另建分支。

## 2026-10-30 11:03 北京时间 — 审查修复验证与集成前置条件

- Branch/worktree/base/owner: 同注册；HEAD仍0628f72f2c6208a6cc553f3f29b893de8f4ec697，root汇总三子任务，未提交或合并。
- 用户确认：其他修复按讨论方案，唯指标区仅支持既有桌面六列两行，不拓展动态行数。
- 修复：客资基础权限与数据范围门禁统一；Context按当前权限重算所有操作数、保留冻结Range，不回旧汇总；结果返回同发布version/spec；运行名单不因其他用户停用整体失败，保存发布批量校验；可见版本/查看者批量读取；字典快照贯穿编辑与freeze；稳定错误码；12启用上限及授权排序后12槽兼容；申诉由Business匹配实例+BPM公开授权查询计数分页，取消全待办分页扫描；剩余deadline传到只读限时事务，BPM专用查询校验事务/dataSource实际一致；非法日期Token拒绝隐式1970转换，日期限制为MySQL范围。
- 前端：稳定编辑ID及异步状态归属；删除/清空/切周期/切来源状态同步；初始化禁编辑并可重试；历史选择快照与新标签；真实CAS版本冲突；卡片绑定求值响应spec/version；6×2固定网格、最多12槽、长文本tooltip、深链Modal覆盖层、错误位于业务面板内部。
- Changed scope: 原HomeMetric核心/配置/来源/测试；Lead、LeadAppeal Service/Mapper与测试；BPM指标分页/事务检查及测试；Admin页面/API/fixture；Workbench卡片/API/首页/样式及fixture；API文档和本记录。各子任务文件写入已释放。
- 后端验证：Maven reactor `-Dtest=*Metric*Test,PermissionDefinitionApiTest,StudentServiceReadServiceTest,AdvancedFilterServiceTest,AdvancedFilterAccessContractTest`，最终166项、164通过、0失败、0错误、2项原StudentServiceReadServiceTest跳过。首次testCompile两个测试引用问题及第二轮mock旧两参数aggregate不匹配、权限严格stub遗漏均修复，未放宽生产校验以通过测试。
- 前端验证：Admin/Workbench构建通过；Workbench typecheck通过；相关Vitest4通过/9跳过；两端浏览器通过稳定操作数/迟到目录/删首行/日期清空与切换/毫秒异时区/字典快照/CAS重新打开；真实TodayTasksPage12卡6×2、长文本数值/按钮bounds、同版本显示、深链覆盖层、少量配置空槽和错误不占grid通过。Admin全量typecheck仍仅12条既有withdrawal重复字段错误，未扩大修复。
- SQL证据复用：feature SQL未改变，SHA256仍C35DF8F1FD6C4C9E52C148216061E140A30345567DB497F19E909DC26C7CF605；原MySQL8.4.11七组验证对该文件仍有效。本轮未写业务库、未授权限、未部署或重启现有服务。
- 集成前置：10:59只读核对主分支HEAD ba8c788f470eb96d22e8729a2d8f711d83c0c370，已提交core迁移至V355，另有未提交V356__gift_purchase_export_batch_permissions.sql和V357__business_task_cancel_permission.sql。本功能基线仍V353；迁移runner要求连续编号。不得占用并行编号、复制未完成工作，或擅自提交/合并。正式编号、fresh/upgrade全链验证及真实后端账号验收须在分支集成阶段继续；尚未完成这部分，不能标记ready-to-merge/生产验收。
- 可执行的后续集成方案：获得明确Git授权后，先将本工作树已验证功能变更提交到codex/home-metrics-20261028；等待并行迁移归并后复核main实际SHA/编号，仅向本功能分支同步已提交main，处理已知LeadManagementServiceImpl与TodayTasksPage重叠改动，分配当时的下一连续迁移号并跑完整适用链路。该方案不向main提交/推送，不自动部署。

## 2026-10-30 11:15 北京时间 — 整合交付更新
- 用户明确授权所有本地开发分支与origin业务分支完整合入main；不含遗留gitee/master，不推送部署。
- Target: main，依赖其1068599已包含V354–V357及五项已交付功能。状态ready-to-merge；最终源码commit由紧接本条的Git提交确定，并记录到main整合handoff。
- V358正式化完成：旧feature源替换为migrations/V358__home_metrics.sql；Core desired与fresh baseline仅追加两空表。MySQL8.4.11 9组通过，三份DDL/实际22列4索引一致；fixture前缀v358_verify_20261030111204_；SHA256 b03b8c285a6ab01a0f42808ec92910c9912d5ccb6dca4df3beb92cc0d3cc7010。
- Java/前端功能源码维持前条164通过等证据；本次改变SQL/文档及baseline，整合后main重跑适用验证和全链。已知冲突TodayTasks须保留main取消/其他待办/导航验证及feature指标，Lead须保留公海写策略与metric只读scope。剩余集成风险明确交给root，不把分支证据当合并后验收。
