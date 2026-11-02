# 订单报名资料收集任务

## 2026-11-02 双向定位与自动预填收尾登记

- local/main/原工作树；本轮占用任务 Service/Controller/VO、RegistrationStudentInfo、StudentInfoTasksPage、RegistrationPages、任务 API/测试/夹具及直接文档。
- 公共池传 caseId 精确解析订单商品对应任务；任务返回同商品的报名任务 id 并反向定位，无履约任务时明确提示。生成新表在服务端自动读取当前档案，即使前端未传来源也会预填；当前档案仍为唯一最新正式提交，旧订单快照保留。
- 验证权限/关联一致性、缺失与跨页目标、无来源参数预填、表单共享操作、类型/构建/浏览器；不授权生产部署、角色授予或共享服务重启。

## 2026-11-02 单一当前档案与弹窗复用登记

- local/main/原工作树；占用 studentinfo 当前档案查询/任务读取及复用校验、任务页面/API/弹窗/测试和直接文档。
- 用户明确改为每客户一份当前档案，新正式填写替换当前可复用来源；旧订单提交快照仅为订单留痕，不作为历史档案选择。使用最新非复用的已提交表单投影唯一当前档案，无物理批量删除。草稿不覆盖、直接复用不提升为新档案；仍禁止缺项复用。
- 入口“查看已有档案”弹窗展示并允许确认复用；保留权限/敏感字段/材料边界，验证陈旧来源与跨客户拒绝。

## 2026-11-02 长列表滚动修复登记

- 沿用 local/main/原工作树，重新占用任务页面、页面样式、长列表夹具及直接文档/本记录。仅修复高度传递、左右独立滚动与分页位置，不更改数据数量/业务状态。验证 20 条以上列表、长详情、手机宽度及类型检查。

## 2026-11-02 状态/操作栏/原分享弹窗修复登记

- 沿用 local/main/原工作树；用户已明确撤回自动生成要求，保留手动生成。
- 占用任务页面、任务 API/Service/权限/Controller、StudentInfoLinkModal、相关样式/测试/文档；复用 OverflowToolbar 和原二维码分享卡片，补回任务撤销入口，不修改创建时机、数据库、角色权限或旧表单数据。

## 2026-11-02 直接资料展示修复登记

- 延续 local/main/原工作树与 HEAD；本轮占用 StudentInfoTasksPage、StudentInfoPanel、StudentArchivePanel 及共享报名资料卡片、夹具和直接文档。将任务右侧始终呈现资料卡片/资料空态，真正共用学员档案报名卡片容器与字段格式，不引入考试业务或额外编辑授权。

## 2026-11-01 界面修复登记

- 延续 local/main/原工作树；重新占用 StudentInfoTasksPage、任务 services/VO/Service、StudentInfoPanel、专项测试/夹具及直接文档。目标：共享主从/表格布局、服务端快捷筛选、草稿填写情况只读查看、现有风格操作层级。保留既有权限与敏感读取边界，不公开员工草稿读取能力给外部链接。

## 旧履约任务兼容修复登记（2026-11-01）

- 复用原 local/main/工作树及 base；本轮重新占用 V359、其 MySQL 专项验证脚本、订单收集 API 文档和本记录。
- 用户授权修复未生成表单的旧履约任务。仅补 pending/processing 且订单 effective 的缺失商品级收集任务，保留原任务、答案、材料和发送事实。
- 核对原可办理范围后，补建订单状态覆盖 pending_approval/revision_required/effective；仍只处理已有 pending/processing 履约任务，不扩展至所有历史订单。
- V359 尚未部署，仅在受控本地开发库应用；按仓库开发期规则修正原迁移并同步本地，不新建编号、不改历史台账校验值。验证首次、重跑、终态排除、身份/订单关联及失败恢复。

- 环境：local（ZSJOS_AGENT_ENV 与 /etc/zsjos/agent-environment 均未设置）。
- 分支 main；工作树 D:\ZSJ-OS；base 0ecc74d4d13bcfbc0361ecdbc2bbd73327359425；Owner Codex。
- 目标：订单进入审核按商品明细创建收集任务；统一列表、历史资料检查/复用/预填；订单终止限制内部操作但不撤销公开链接；履约读取同一收集状态。
- 非目标：修改审批通过条件、角色授权、部署、提交推送、无关考勤改动。
- 所有权：studentinfo 后端领域/接口/测试；订单审核和 registration 的关联入口；Workbench 收集列表、路由、service 和收集组件；Admin 原履约收集组件兼容；H5 如需的预填兼容；新增收集任务 SQL、对应验证脚本及 schema；相关 API/架构/导航文档；本记录。
- 依赖：已有订单商品、Person、System 权限与字典、Infra 加密文件、现有收集字段和档案版本。
- 验证：任务幂等、完整/缺项复用、预填和附件隔离、终止内部拒绝/外部保存提交、履约投影；Java 编译和相关测试；前端类型/构建及桌面移动浏览器；MySQL 首次/重复/部分恢复/失败台账验证。
- 集成目标分支/顺序：None；保持当前工作树，不修改现有无关改动。

## 继续实现范围确认

- 用户已解除 SalesOrderServiceImpl.java 暂缓限制，授权保留现有改动并接入订单审核事务；另一工作流记录已释放写权限。本工作流恢复该文件的收集任务接入所有权。
- 本轮补全统一列表资料/材料查看、客户历史资料导航、过期链接轮换、对应权限和回归验证；StudentInfoPanel 仅增加读取适配器，保留既有履约消费者。
- 客户档案入口范围增加 LeadDetailModal、MySalesOrderPage、SalesOrderApprovalPage：仅传入既有权限并提供按 Person 筛选的收集资料链接；不改订单档案确认表单。

## 范围更新

- 用户明确要求“先不改该文件”：暂停 SalesOrderServiceImpl.java，不向另一个聊天发送协调消息。订单审核事务 hook 尚未接入。
- 新增脚本与夹具归本工作流：script/student-info-tasks/verify_java.py、script/sql/mysql/tools/test_order_student_information_tasks.py、Workbench test/order-collection*。

## 2026-11-01 本地阶段交付（待集成，非完成）

- 北京时间：2026-11-01；分支/工作树/base 与登记一致，HEAD 未变。
- 已写入：独立 task DO/Mapper/VO/Controller/Service/权限 provider；历史字段和材料兼容性检查、预填、独立附件复制和直接复用快照；终止订单内部禁取链接、公开草稿保留；V359 SQL 草稿；Workbench 统一列表及两端履约只读状态入口。
- 履约 byCase 查询增加任务关联投影；复用满足原已发送完成条件但不伪造发送事实。旧履约生成/发送/链接 API 返回转往统一任务办理的明确提示。
- 修改文件范围：studentinfo 新增任务及复用领域文件、StudentInfoFormService/StudentInfoImageService/StudentInfoFormMapper、旧 StudentInfoFormController、RegistrationServiceImpl；Workbench StudentInfoTasksPage/studentInfoTasks、constants/RouteHost、RegistrationStudentInfo/studentInfo；Admin RegistrationStudentInfo/adminRouteFilter；V359 和两个验证脚本/浏览器夹具；相关 API/归属文档和本记录。
- 验证：Maven reactor compile 在修复接口注解后通过；最新隔离 Java 编译覆盖全部 studentinfo、旧 Controller 与 RegistrationServiceImpl，10 个专项测试通过。Workbench 类型检查通过，路由版本 production build 通过；最后小屏增加表格横向滚动经实际浏览器复验。1440/390 的缺项禁止复用、预填入口、终止订单不提供链接通过；截图为系统临时目录 order-collection-1440.png、order-collection-390.png。
- MySQL 8.4.11 隔离验证：首次/重复、UTF-8 HEX、唯一索引、缺前置拒绝、台账失败不登记、部分 DDL 后恢复通过；保留 order_collection_20261101103632199521_*，未执行应用库迁移。原应用库只读确认 V342/V358 和 122 份表单。
- Admin ts:check 未通过，唯一报告是未改动的 src/views/mes/pro/task/edit/index.vue:86 handleRefresh 未使用；本次不修改该文件。浏览器最初 Vite 模块加载超时，改用独立 esbuild 静态夹具后验证通过。
- 未完成：订单审核 hook（用户要求暂缓对应文件）；客户档案导航与统一列表完整资料查看；过期链接恢复；完整功能授权/并发/附件复制集成测试；schema/core.sql 同步和完整适用 fresh/upgrade 校验；应用库升级、角色配置及真实账号验收。
- 未部署、未提交、未推送、未修改角色授权。当前是待集成代码，不可单独发布前端或切换旧入口。固定所有权保留用于继续本工作流；订单服务文件不占用写权限。

## 2026-11-01 13:57 北京时间：继续实现交付

- 环境/分支/工作树与登记一致；HEAD 仍为 0ecc74d4d13bcfbc0361ecdbc2bbd73327359425。用户已明确解除订单服务写入限制。
- 完成审核事务接入：首购、复购及接续重提共用 startRound 创建商品级任务；保留另一工作流的 OrderProfileService 与幂等指纹修改。
- 新增统一任务资料预览、当前档案修订附件读取及绑定校验；增加敏感权限和防敏感错误日志覆盖。StudentInfoPanel 使用读取适配器复用既有展示，不影响原履约读取。
- 新增链接过期状态及轮换：保留已填内容，清除发送确认，拒绝终止订单和已提交表单；命令保留版本/幂等检查。
- 客户档案（订单与订单审批页面）增加按 Person 查看历史收集资料入口，统一列表提供同样筛选；目标页面再次校验配置权限。
- 数据库：同步 schema/core.sql 与新建库 00-bootstrap-schema.sql；V359 在隔离 MySQL 首次/重复/缺前置/台账失败/部分 DDL 恢复/旧表单关联测试通过，且新表列与索引和开发库只读比较一致。完整 fresh 基线后执行 380 个迁移通过，结构差异 0。
- 本地开发库已应用 V359，并重复验证；新增 122 个既有表单任务关联。原表单、答案、附件、档案修订、system_role_menu 的 CHECKSUM TABLE 前后不变，中文 HEX 核验通过。执行前记录 C:\Users\EDY\AppData\Local\Temp\order-collection-v359-before-20261101135147.json；不含凭据或资料明文。
- 验证：后端 reactor compile 通过；最新独立编译及 19 项收集专项测试全部通过（权限允许/拒绝、缺项、敏感/跨表单读取、终止、文件复制与回滚补偿）。扩大订单回归 104 项中 103 项通过；唯一失败 terminateLocksCurrentRoundAndCancelsBpm 期待既有 cancelEarlyDeliveryByOrderId，而 HEAD/current 均调用 cancelByOrderId，该路径不是本次修改。新首购/复购/幂等任务创建断言通过。
- Workbench 生产构建通过；1440/390 浏览器缺项禁止复用、终止禁链接但可查看已提交资料、客户历史筛选通过，已查看手机截图。Admin 本轮没有进一步修改源文件；上轮全量类型检查的无关 MES 未使用变量问题仍不宣称通过。
- 修改范围在原登记上新增：订单服务的 task 注入/startRound 接入和其回归 mock/断言；LeadDetailModal、MySalesOrderPage、SalesOrderApprovalPage、StudentInfoPanel；两个专项测试类；schema/baseline 与 SQL 验证；菜单覆盖、权限流、API 文档。其余本工作流文件均为已有增量。
- 发布边界：未重启/部署，未提交/推送，未分配角色权限。管理员仍需配置新页面/复用按钮；真实账号 HTTP、运行中服务及多请求数据库并发未验证，不能将夹具或 mock 测试当作上线验收。角色及服务操作需要单独明确授权。所有权释放。

## 2026-11-01 14:20 北京时间：旧履约无表单兼容修复

- local/main/D:\ZSJ-OS，HEAD/base 未变（0ecc74d4d13bcfbc0361ecdbc2bbd73327359425）。目标：修复旧生成入口关闭后，尚无表单的旧履约任务缺少收集办理入口的问题。
- 修改原尚未部署的 V359，先保留既有表单关联，再为租户 1 中 pending/processing 且订单仍可办理的缺失任务补建空记录；核对订单、商品、客户关系。终态/删除记录/无效关联不补建，已有绑定冲突报错回滚，不覆盖旧记录、不改版本台账。
- 修改文件：V359__order_student_information_tasks.sql、test_order_student_information_tasks.py、docs/api/order-student-information-tasks.md、本记录。无 Java/UI/DDL/迁移顺序变化。
- MySQL 8.4 隔离验证通过：首次、重跑、已有 V359 台账后的补齐、pending/processing 与审核中/待补正、completed/cancelled/ended/terminated/superseded 排除、租户/删除/错误订单关联排除、绑定冲突拒绝及修复恢复、台账写入失败及 DDL 后恢复。旧订单/履约/表单校验值不变，空任务没有 form/source/operator 且 version=0；新表结构与本地一致。
- 本地修复已执行：补建 2 个空收集任务，总数 124；符合范围但缺任务的数量归零。再次执行无变化。原收集任务逐行 SHA256、订单/履约/表单/答案/材料/档案/角色授权表校验及两本 V359 台账全部保持原值。中文菜单 HEX 核验通过。执行前记录 C:\Users\EDY\AppData\Local\Temp\collection-legacy-before-20261101141925.json（校验值，无资料明文）。
- 静态迁移/结构检查及 diff 检查通过；本次是局部数据补齐修正，未重复不相关完整 fresh 链。未部署、未重启、未提交/推送、未改权限。线上实际范围未查询或写入；真实账号页面验收仍需匹配运行版本。所有权释放。

## 2026-11-01 23:38 北京时间：收集界面四项修复

- local/main/原工作树，HEAD 未变。按用户截图要求接入共享主从/表格视图控制、快捷筛选、填写情况和操作层级；保留其他任务改动。
- StudentInfoTasksPage 使用 useInboxTableLayout/useTheme，沿用订单主从列表与详情样式；表格继续 BusinessTable；手机列表打开详情。顶部收集状态 Tabs 和订单状态/关键词传至服务端分页，切换条件重置页码。已过期记录不算作待发送/待填写。
- 主操作突出生成/获取链接，发送确认配图标，历史资料改为轻量链接，重新生成归入更多确认菜单。详情直接内嵌填写内容，不再层层打开资料弹窗。
- StudentInfoPanel 的 showDraft 默认为 false，任务入口显式启用；后端返回最近保存的加密草稿投影及当前材料，标注未提交、只读，保留敏感权限和附件归属检查，不回显客户端未保存值或改变提交状态。
- 修改文件：任务 Query/Service、StudentInfoPanel、StudentInfoTasksPage/studentInfoTasks、任务专项测试、浏览器夹具及原检查脚本、API 文档、本记录。
- 验证：Workbench typecheck/production build 通过；最新独立 Java 编译及 20 项收集测试通过（新增草稿敏感字段脱敏、允许读取及不写入断言）；diff 检查通过。ego-browser 使用独立合成夹具验证桌面主从/表格切换、待填写筛选、草稿详情及手机宽度；已检查截图 collection-ui-split.png、collection-ui-table.png、collection-ui-mobile.png（系统临时目录）。TaskSpace 3 已 finish，夹具 HTTP 服务已停止。
- 旧 Playwright 检查脚本随布局修改了定位与断言，但本轮实际浏览器验证使用 ego-browser，并未另跑旧脚本。服务端过滤已实现且编译通过，尚无真实账号 HTTP 验收；夹具验证不等于生产验证。
- 未改数据库、未部署/重启、未提交/推送，所有权释放。

## 2026-11-02 10:20 北京时间：直接展示报名资料

- local/main/原工作树，沿用登记 HEAD。用户要求任务页面直接展示资料并复用学员档案格式。
- 新增 RegistrationArchiveCard，StudentArchivePanel 与 StudentInfoTasksPage 实际共用报名卡片容器、标题与样式；StudentInfoPanel 字段标签/行复用 archive-registration-field/description-label。任务详情始终渲染资料区域：有数据直接显示，无表单显示空态，无读权限显示权限说明。不存在可复用来源时不再展示禁用的复用按钮。
- 修改：RegistrationArchiveCard.tsx、StudentArchivePanel.tsx、StudentInfoPanel.tsx、StudentInfoTasksPage.tsx、合成夹具、API 文档及本记录。无后台/数据库/权限改变；学员档案原编辑逻辑保留。
- 验证：Workbench typecheck、scoped 内容检查和 diff check 通过。ego-browser 合成页面核验未生成表单空态与草稿直接展示；已查看 desktop/mobile 截图（系统临时目录 collection-archive-desktop.png / collection-archive-mobile.png）。TaskSpace 3 已结束，临时 HTTP 服务已停止。未执行真实账号后端读取；测试数据不代表生产数据。
- 未部署、重启、提交或推送。所有权释放。

## 2026-11-02 11:56 北京时间：唯一当前档案与弹窗复用

- local/main/原工作树，HEAD 仍为 0ecc74d4d13bcfbc0361ecdbc2bbd73327359425。用户明确覆盖原“多份历史来源选择”方向：每客户一份当前档案，入口为弹窗，不跳转历史列表。
- 当前档案使用同一 Person 最新非复用正式提交的唯一投影（submittedAt/id 降序），新正式提交整体替换当前来源；草稿和复用副本不会提升为最新档案。旧订单答案/材料留痕不删除，不作为历史档案选择。兼容 candidates 只能返回零或一份。
- 新 archive/附件读取 API 校验任务对象权限、当前来源及修订、客户和附件归属。复用也拒绝已被新提交替代的旧来源。查看已有档案先刷新当前来源，读成功后才允许复用；缺项禁用直接复用，可带入新表。
- 已生成但未提交的任务可经页面明确确认复用：原草稿保留为订单记录、旧链接撤销，新复用快照保留原任务冻结字段配置。已提交/已复用任务不可覆盖，终止订单不可复用。
- 修改 TaskService/Controller、FormMapper、任务页面/services、LeadDetailModal 导航文案、单元/Mapper 测试、验证脚本、夹具/原浏览器检查脚本及相关 API/权限文档。无表结构或应用库数据变更。
- 验证：最新独立 Java 编译及 25 项专项测试通过，含真实 MyBatis Mapper + H2 验证只选择最新正式提交、排除草稿/复用/其他客户、保留旧订单事实；旧来源拒绝、已提交拒绝、未提交替换且保留草稿/冻结字段、复制快照断言通过。Workbench typecheck 与最终 production build 通过，diff check 通过。
- ego-browser 合成验证：缺项复用禁用、无历史来源选择器、当前资料弹窗读取、完整来源从弹窗确认复用，请求绑定 formId=7/revision=0；390 宽度弹窗内容可读、操作栏不横向溢出。窗口最小化导致截图无法获取，未声称完成截图视觉验收。TaskSpace 7 已 finish，夹具 HTTP 服务停止。
- 原 Playwright 检查脚本同步新入口/断言但本轮未另跑。无真实账号 HTTP/MySQL 并发或运行服务验收，未部署、重启、提交、推送或批量删除。所有权释放。

## 2026-11-02 13:58 北京时间：双向精确定位与后端自动预填

- local/main/原工作树，HEAD 沿用登记。目标补齐用户确认的独立资料收集流程，不改变唯一当前档案/旧订单快照边界。
- 公共池链接携带 registrationCaseId；新增 by-registration 读取，通过原 case 对象权限、任务列表范围及订单商品/订单一致性定位；任务 Row 返回同商品 registrationCaseId。收集页支持 caseId/taskId 深链，跨页目标单独读取并显示定位项；反向链接使用公共池 URL 参数，兼容原 location.state。错误参数/无目标不打开其他记录。无履约任务显示明确提示及禁用动作。
- 新表生成在服务端自动解析当前档案，客户端不传来源也预填字段并复制材料；客户端传来源仍检查当前性/修订。自动解析不更改请求指纹，丢响应重试不重复创建。无档案时保持首次空白填写行为。
- 修改 TaskService/Controller/VO、任务 API、StudentInfoTasksPage、RegistrationStudentInfo、RegistrationPages、专项测试、双页导航合成夹具/静态服务器、API 文档和本记录。无 SQL/数据库结构或业务数据写入。
- 验证：最新独立 Java 编译及 28 项专项测试通过，新增同商品正反定位、范围拒绝/订单错配拒绝、无客户端来源自动预填/材料复制调用和幂等重放测试。Workbench typecheck/最终 production build 通过，diff check 通过。
- ego-browser 实际页面合成 HTTP 验证：公共池 case555 → 收集 task999 → case555，两个目标均不在第一页，读取请求精确为目标且未误选首条；目标缺失/坏参数提示明确，坏参数未请求其他case详情；直接taskId999正常定位；待审核且无case时提示存在且真实可见返回按钮禁用（测量行隐藏按钮不计入交互断言），390宽度打开详情。TaskSpace8结束，5198夹具服务器停止。
- 本地只读检查：关联收集表但 case.order_item_id 为空的数量0；已生效收集任务无同商品履约case数量0。没有使用此检查替代线上关联核验。
- 发布边界：功能代码及隔离验证完成；未重启/部署、未配置真实账号权限、未提交/推送。真实账号下从下单、公开填写、正式提交到再次复用的整条 HTTP/数据库链路尚未验收，不能称为生产验收完成。所有权释放。

## 2026-11-02 10:46 北京时间：状态标签、统一操作与原分享弹窗

- local/main/原工作树与 HEAD。用户撤回自动生成要求，明确保持手动生成；订单创建时机没有改变。
- 卡片使用右上角色状态 Tag，待发送 gold、待填写 blue、已提交 green、已复用 purple、过期 orange、撤销 red；详情实际使用 OverflowToolbar，所有业务操作集中到该栏。
- StudentInfoLinkModal 增加保持默认行为的 client 适配器；任务页面复用原弹窗、StudentInfoQrCard、ResourceLink，生成支持原候选预填，查看支持链接、二维码、复制分享图片、保存图片、轮换和撤销。未新增角色授权。
- 任务端补回 revoke API/对象动作及权限；复用原锁、版本与幂等流程，拒绝终止订单及已提交表单，清除发送确认不删除草稿。taskLink 返回真实可轮换/撤销能力。
- 修改：StudentInfoTasksPage、studentInfoTasks service、新页面样式、StudentInfoLinkModal、任务 Controller/Service/PermissionProvider、StudentInfoFormService、专项测试、夹具/文档及本记录。未修改自动创建、数据库或旧表单数据。
- 验证：Workbench typecheck/build 通过；独立 Java 编译及 20 项收集测试通过，新增撤销时终止/已提交拒绝断言。ego-browser 合成夹具确认统一操作栏、右上角色状态、分享弹窗及手机布局；实际下载 800×1080 PNG 并查看图片。已点击复制图片且无错误提示，但未读取操作系统剪贴板作额外证明。TaskSpace 4 已结束，测试 HTTP 服务停止。截图与下载在系统临时目录 collection-share-{toolbar,mobile,export}.png。
- 原 Playwright 检查脚本同步更新按钮名称但本轮没有另跑；真实账号 HTTP/剪贴板权限差异未验收。未部署/重启、未提交/推送；所有权释放。

## 2026-11-02 10:54 北京时间：长列表高度修复

- local/main/原工作树，HEAD 沿用登记。页面外层补齐 workspace-page/sales-order-inbox-page，高度经 Spin wrapper/container 的 flex/min-height 约束传给实际列表；左侧分页不收缩，右侧使用既有独立滚动；表格独立滚动区域防止底部裁切。
- 修改 StudentInfoTasksPage.tsx、student-info-tasks.css、order-collection.tsx 长列表夹具、API 文档及本记录。每页 20 条请求参数不变，无后台/权限/数据库改动。
- 验证：最新 typecheck 和 diff check 通过；ego-browser 合成 24 卡片、32 字段验证左右独立滚动。桌面列表内容 2776px/容器 825px，分页位置稳定且可见，页面高度等于视口994px；手机390×844分页底部832px，页面高度844px；表格1440×900容器771px/内容1302px可滚动，页面仍900px。已查看 collection-scroll-desktop.png/mobile.png（系统临时目录）。
- 浏览器空间5已结束，夹具 HTTP 服务已停止。未验证真实账号页面，未部署/重启或提交/推送。所有权释放。
