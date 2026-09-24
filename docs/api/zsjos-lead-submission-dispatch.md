# 客资提交与派单 API

销售阶段的默认值、跟进快照和组合筛选见 [销售阶段与高级筛选](lead-sales-stage.md)。

教务自拓直接归属及成交身份规则见 [教务自拓与直接成交](zsjos-education-self-sourced.md)。

## 运行边界

- 接口统一位于管理端 `/admin-api/zsjos/lead`，使用当前登录用户和租户上下文。
- ZSJOS JSON 请求和响应中的日期时间统一使用 Unix epoch 毫秒数，例如 `1786258800000`。前端不得提交 ISO 日期字符串；空值使用 `null` 或省略字段。
- URL 查询参数中的日期范围不是 JSON 时间戳，继续按对应请求 VO 的 `@DateTimeFormat` 格式传递；两种契约不得混用。
- 产品由 `LeadProductCatalogPort` 读取。产品 SDK 未交付前，课程接口稳定返回“产品配置服务暂不可用”，提交页禁止提交，不使用静态课程兜底。
- WebSocket 使用现有 `/infra/ws`，消息类型为 `zsjos_lead_assignment`，内容仅包含 `leadId` 和 `eventType`。
- `eventType` 包含 `assigned`、`reassigned`、`accepted`、`rejected`、`expired`、`cancelled`；客户端只将消息作为重新查询信号。
- 待接列表项返回 `remainingSeconds`、`rejectable`、`deferrable` 和 `assignmentHistoryId`。自动派单可拒绝且不可延后，指定派单不可拒绝但可收起稍后处理。
- 待接列表和抢单池中的 `sourceChannel`、`leadCategory` 始终是持久化的稳定字典键。`leadCategoryLabel` 优先返回 Lead 选择时固化的分类标签快照；仅迁移前没有快照的历史记录兼容解析当前启用字典。`sourceChannelLabel` 仍是当前启用字典解析出的展示标签。兼容解析也缺失时客户端显示未配置状态，不得把稳定键当作展示标签。

客资创建时服务端同时固化 `leadCategory` 与 `leadCategoryLabelSnapshot`。疑似重复提交进入复核队列时也先保存服务端解析出的分类标签，稍后新建或重新激活 Lead 仍使用原提交标签。基础信息、提交人补充、跟进和有效性判定明确改选分类时生成新的值与标签快照；仅修改、停用或删除系统字典项不会回写既有 Lead。迁移前历史 Lead 不猜测提交时标签，响应在快照为空时兼容当前字典解析。
- 用户可见客资响应同时返回字符串 `leadNo` 和数值 `leadId`/`id`。`leadNo` 格式为 `KZyyyyMMddHHmmss` 加租户当日四位序号，序号从 `0001` 到 `9999` 循环，超过 `9999` 后重新从 `0001` 开始；时间固定使用北京时间。URL、命令、WebSocket 和对象权限继续使用数值 ID。

## 员工接口与权限

| 接口 | 权限 |
| --- | --- |
| `GET /zsjos/lead/product/simple-list` | `zsjos:lead:submit` |
| `POST /zsjos/lead/attachment/upload` | Lead 提交、自拓、投诉处理或 `zsjos:lead:request-submitter-assist` 任一对应操作权限 |
| `POST /zsjos/lead/create` | `zsjos:lead:submit` |
| `POST /zsjos/lead/self-sourced/create` | `zsjos:lead:self-sourced:create` |
| `GET /zsjos/lead-duplicate-review/page` | `zsjos:lead-duplicate-review:query`；租户公共复核队列 |
| `GET /zsjos/lead-duplicate-review/{id}` | `zsjos:lead-duplicate-review:query` + 独立复核对象权限 |
| `GET /zsjos/lead-duplicate-review/sales-candidates` | `zsjos:lead-duplicate-review:process`；租户中央复核可选择全部符合条件的启用销售 |
| `POST /zsjos/lead-duplicate-review/{id}/decision` | `zsjos:lead-duplicate-review:process` + 独立复核对象权限 |
| `GET /zsjos/lead/assignment/my-pending` | `zsjos:lead:accept` |
| `POST /zsjos/lead/{id}/accept` | `zsjos:lead:accept` + 当前候选对象权限 |
| `POST /zsjos/lead/{id}/reject` | `zsjos:lead:accept` + 当前候选对象权限；仅自动派单 |
| `GET /zsjos/lead/dispatch-status/my` | `zsjos:lead:accept`；返回岗位资格、页面在线和接单偏好 |
| `POST /zsjos/lead/dispatch-status/heartbeat` | `zsjos:lead:accept`；仅启用销售专员进入轮询池 |
| `PUT /zsjos/lead/dispatch-status/mode` | `zsjos:lead:accept`；请求体为 `{ "accepting": true|false }` |
| `POST /zsjos/lead/dispatch-status/offline` | `zsjos:lead:accept`；正常退出时尽力移出轮询池 |
| `GET /zsjos/lead/claim-pool/page` | `zsjos:lead:claim-pool:query`；租户抢单池只读视图 |
| `POST /zsjos/lead/claim-pool/search-page` | `zsjos:lead:claim-pool:query`；相同固定范围内组合关键词和受控高级条件树 |

Ordinary submission identity and dispatch restrictions, submitter actions, and the independent complaint queue are defined in `docs/api/zsjos-lead-submitter-actions.md`.
| `POST /zsjos/lead/{id}/claim` | `zsjos:lead:claim` + 抢单池对象权限 |
| `GET /zsjos/lead/inbox/submitted/page` | `zsjos:lead:query` + `zsjos:lead:query-submitted` |
| `GET /zsjos/lead/inbox/submitted/filter-profile` | `zsjos:lead:query` + `zsjos:lead:query-submitted` |
| `GET /zsjos/lead/inbox/owned/page` | `zsjos:lead:query` + `zsjos:lead:query-owned` |
| `GET /zsjos/lead/inbox/owned/filter-profile` | `zsjos:lead:query` + `zsjos:lead:query-owned` |
| `GET /zsjos/lead/page?relationScope=all&audience=management&inboxGroup=...&inboxStage=...&inboxQuick=...` | 统一客资管理；按提交/负责权限返回本人及当前管理部门、子部门员工的关系并集。前端不再提供“我提交的/我负责的”切换，筛选改为服务端筛选方案（视角 `management`）下发的三行分级条件。详情中的订单和申诉记录按客资关系显示，后端仍执行对象权限校验 |
| `GET /zsjos/lead/inbox/management/filter-profile` | `zsjos:lead:query`；统一客资管理页的一级归类与二级行选项 |
| `POST /zsjos/lead/inbox/submitted/search-page` | 提交人固定范围内组合关键词与高级条件；忽略可选状态分组 |
| `POST /zsjos/lead/inbox/owned/search-page` | 负责人固定范围内组合关键词与高级条件；忽略可选状态分组 |
| `POST /zsjos/lead/{id}/judge-valid` | `zsjos:lead:qualify` + 当前负责人对象权限 |
| `POST /zsjos/lead/{id}/judge-invalid` | `zsjos:lead:qualify` + 当前负责人对象权限 |
| `GET /zsjos/lead/get?id={leadId}` | 读取指定客资详情；允许客资、公海、订单、学员或审批的真实对象关系，不扩大客资列表 |
| `POST /zsjos/lead/qualification/attachment/upload` | `zsjos:lead:qualify`；上传后仍需由判无效命令校验引用归属 |

提交人主管只读范围：持有 `zsjos:lead:query-submitted`，且为员工提交人所在部门或上级部门负责人的 ADMIN 用户，可以读取该客资详情、跟进、流转及销售历史，即使客资当前负责人属于其他部门。提交人按 `providerOwnerType=system_user` 和 `providerOwnerId` 识别，部门关系使用 System 当前配置；各接口功能权限仍须独立满足。该规则同时适用于管理端和员工工作台，不授予请求提交人协助、修改、跟进新增、转移或判定等操作权限，也不将合作方 ID 当作员工 ID。

派单、接单、拒单和超时同时维护 `lead_assignment_accept` 业务任务。接单、抢单和管理员转派在归属事务内创建 `lead_first_follow_up` 任务，截止时间由接单时启用的独立跟进规则计算。

提交接口先执行统一查重。同字段手机号或同字段微信号命中任何历史客资时为强重复，创建 `duplicate_flag=strong_duplicate`、`duplicate_result=strong_rejected` 的查重审计记录，并返回稳定业务错误 `LEAD_DUPLICATE_STRONG_CONFLICT`；本次不创建 `Person`、`Lead`、派单或指定销售任务。交叉联系方式、姓名+明确省市+明确主意向课程、姓名+手机号后四位命中时为疑似重复，保存提交快照、候选快照、命中规则和 `reviewFingerprint`，返回 `outcome=review_pending + reviewId`；相同 fingerprint 已有待处理任务时复用最早任务，不追加可见复核项。`duplicateAutoResolutionEnabled` 仅用于交叉联系方式疑似重复：开启时自动关闭本次复核并返回 `duplicate_auto_closed`，不创建 Lead、不改动历史客资。完全无命中返回 `created + leadId + leadNo`。

当前提交页联系方式查重已收敛为强重复激活：手机号、微信号至少填写一个；同字段或交叉字段命中时，逐条创建 `LeadActivation` 并发送 `zsjos.lead.activated`，同一请求对同一客资按幂等键只处理一次，不改变历史客资状态和归属。手机号与微信号分别命中不同客资时分别激活和通知；无负责人时仍记录激活，页面统一显示“客资已存在，已激活提醒”。最终提交前服务端再次执行该联系方式激活，未命中联系方式时才继续执行既有弱重复复核规则。联系方式查重接口为 `POST /zsjos/lead/contact-check`、`/self-sourced/contact-check` 和 `/education-self-sourced/contact-check`，分别受对应提交权限保护。

自拓客资未选择新媒体提供方时，`sourceUserId` 固定回退为提交销售，确保来源人与直接归属一致。提供方候选列表中的手机号只返回脱敏值，部门名称通过 System 批量接口解析，不逐行查询。V080 将默认“客资新建”站内信拆成两条规则：实际提交销售继续收到通用提交成功消息；仅当销售或教务自拓时明确选择了不同于提交人的新媒体提供方，该提供方才收到关联提醒。V257 对未编辑的 V080 默认模板使用 `lead.submitterIdentityLabel` 区分销售/教务。未选择提供方以及普通新媒体提交均不产生这条关联提醒。管理员已有的启用、停用或已编辑规则保持不变，历史客资不补发消息。

详情响应投影 `overviewVisible`、`visibleTabs`、`sourceLabel`、`sourceUserName`、`ownerUserName` 和 `identityMaskMode`。来源标签为兼职提交、新媒体提交、销售自拓录、教务自拓录；兼职提交人从 Partner 主体解析姓名，不返回内部 ID。提交人与负责人互看时沿用中文姓名脱敏，其他有权业务关系人看完整姓名。四个历史标签分别要求 `zsjos:lead-detail:follow-up-read`、`appeal-read`、`complaint-read`、`order-read`，前端不得按 mode 或角色名补齐标签。详情顶层 `nextFollowUpAt` 只来自当前 `zsjos_business_task` 中 `task_type=lead_follow_up_reminder` 且 `status=pending` 的 `dueAt`，仅在 `visibleTabs` 包含 `follow-ups` 时查询；任务已完成或取消时返回空。Workbench 在详情标题栏展示该值，不得通过 Lead 历史时间、跟进记录或 Opportunity 摘要绕过任务状态。

管理、抢单池和判定异常列表的 `keyword` 规则一致：以 `KZ` 开头时按大写标准化后精确匹配 `leadNo`，纯数字精确匹配内部 Lead ID，其他值继续模糊匹配姓名、手机号和微信号。

统一客资管理页不再使用 `simpleStatus`，改由 `audience=management` 的已发布筛选方案解析 `inboxGroup`（一级业务归类）、`inboxStage`（二级当前环节）与 `inboxQuick`（三级快捷条件）三个互斥选中项，各行条件与一级条件取交集，且不改变 `relationScope=all` 已计算的对象可见范围。一级归类为 `all`、`pending_qualification`、`valid`、`invalid`、`closed`、`manual`；`manual` 复用自拓语义（`source_type in (sales_self_sourced, education_self_sourced)`）。二级“当前环节”的待首跟与待判定按当前归属周期首次跟进事实区分，判定截止时间不作为已完成首跟的证据；“正常推进”表示已判有效且 Opportunity 尚未进入成交审批或成交。三级“快捷条件”的 `today`/`overdue` 以 `zsjos_business_task.due_at` 为准（待办 `pending` 且 `task_type in (lead_first_follow_up, lead_follow_up_reminder)`），`transferred_pending` 表示当前归属周期由主管转派产生且本周期尚未首跟。`simpleStatus` 参数保留给兼职端 H5 等既有调用方，语义不变。

统一客资分页接口支持成对提交 `sortField` 与 `sortOrder=ascend|descend`，排序作用于完整筛选结果后再分页。`sortField` 只接受 Lead 根表的持久化字段白名单：`leadNo`、`submittedName`、`submittedMobile`、`submittedWechatId`、`sourceType`、`leadCategory`、`sourceChannelId`、`assignmentStatus`、`dispatchMode`、`assignmentAttemptCount`、`publicPoolAt`、`countedAt`、`currentAssignmentFirstFollowUpAt`、`currentAssignmentFirstFollowUpDeadlineAt`、`qualificationStartedAt`、`qualificationDeadlineAt`、`suspendedAt`、`validDescription`、`invalidDescription`、`appealDeadlineAt`、`closedAt`、`closeReason`、`nextFollowUpAt`、`submittedAt`、`lastActivityAt`、`qualifiedAt`、`convertedAt`、`remark`、`updateTime`。每种显式排序均追加 `id DESC` 作为并列值的稳定次序；未提交完整排序参数时仍使用 `lastActivityAt DESC, id DESC`。提交人、所属销售、产品等关联投影名称不在排序白名单中，前端不得将当前页投影值伪装为全量排序。游标接口的令牌固定编码 `lastActivityAt + id`，即使请求携带排序参数也继续使用默认游标顺序；自定义排序只适用于普通分页接口。

`lastActivityAt` 只表示已成功提交的客资业务变化，并在同一事务中按事件实际发生时间单调推进。基本资料、归属与分配、跟进、资格判定、申诉/投诉、公海协作、正式销售反馈及直接改变非复购 Lead 的订单结果属于业务变化；查看、选中、已读、临时附件上传和提醒发送不属于。较早事件、失败请求、权限拒绝、幂等重放和版本冲突不得覆盖较新的活动时间。

复核队列不绑定管理员角色，迁移也不自动授权角色。具备独立查询权限的租户用户共享待处理列表；这是租户级中央复核。决定事务对任务加行锁，第一位提交者成功。结论固定为 `allow_flow` 和 `close_duplicate`，意见必填、附件可选。`allow_flow` 按首次待复核提交的快照创建正式 Lead，后续进入普通自动分配、指定销售或销售/教务自拓归属流程；`close_duplicate` 只关闭本次复核，永久不创建 Lead、不分配、不计入业绩。联系方式修改调用同一查重规则，任何强或弱命中都拒绝且不创建复核任务。

## 管理接口与权限

高级筛选字段目录由 `GET /zsjos/advanced-filter/catalog?scene=` 返回，场景白名单为 `lead`、`order`、`lead_appeal`、`duplicate_review`、`registration`、`student` 和 `subordinate_sales`，目录本身按场景权限校验。目录中的提交人、负责人、审核人、学习规划师等人员选项只能来自当前场景已经建立的对象范围：Lead 使用客资层级范围，下属销售使用主管管理范围，学员使用当前用户的有效服务关系；场景范围存在但当前没有可见人员时返回空选项。尚未提供权威人员范围的订单、申诉、重复复核和报名场景不下发人员型筛选字段，前端不得回退到 Lead 范围或 System 全量用户列表。前端只展示“身份与联系、状态与进度、归属与人员、产品与服务、金额与付款、时间、补充信息、业务指标”等中性分组及业务字段名称；场景名和 `person.*`、`lead.*`、`order.*` 仅用于协议识别，不作为用户分类。

请求只提交目录白名单中的 `fieldKey`、运算符和值，不接受数据库列名、内部 ID、原始 JSON 或 SQL。“客资编号”只映射 `lead.leadNo`，绝不回退到 `id/leadId/personId`。文本支持包含、不包含、等于、不等于和空值；枚举支持属于、不属于和空值；数字支持比较、区间和空值；日期支持比较、区间、空值及今天、昨天、近 7 天、近 30 天、本周、本月、本季度、本年。相对日期由服务端按北京时间自然日计算，近 7 天和近 30 天包含当天。场景下至少存在两个日期字段时，目录追加虚拟字段 `duration.diff`（时间作差），其 `options` 只列出当前场景可选日期字段；条件必须提交 `startFieldKey`、`endFieldKey`、`unit=minute/hour/day` 以及比较值，语义固定为“结束时间 - 开始时间”。服务端将分钟、小时、天统一折算为分钟并编译为受控 `TIMESTAMPDIFF(MINUTE, start, end)`，同时追加两个时间字段均不为空的条件；两个日期字段必须来自当前场景字段目录，根表字段可与一个关联字段组合，同一关联关系内两个字段可组合，两个不同关联关系字段组合返回 `ADVANCED_FILTER_INVALID`。

高级筛选模板只保存上述结构化条件树，字段为 `filter`，持久化为 `filter_json`；任何用户、管理员和前端都不能保存或提交 SQL 片段。SQL 只在列表查询时由后端 `AdvancedFilterService` 根据场景字段目录即时校验和编译，并且始终叠加页面固定范围、租户、当前用户对象范围、数据范围和业务池约束。模板按 `scene + pageKey` 绑定到具体页面：个人模板 `scope=personal` 仅当前用户可见，系统预置 `scope=system` 由管理员维护并对当前租户该页面可见；同一页面、同一范围最多一个 `defaultTemplate=true`。

已接入预置标签的业务页（`lead_management`、`lead_claim_pool`、`lead_aging_pool`、`sales_order_management`、`sales_order_supervisor_confirm`、`lead_appeal`、`lead_duplicate_review`、`registration_pool`、`student_my`、`subordinate_sales`）在加载时读取 `visible-list`，把返回的模板渲染为筛选栏下方的快捷标签，点击即把模板 `filter` 树作为该页当前的完整高级条件提交。页面首次加载且用户尚未添加任何条件时自动套用默认模板；用户已输入条件时不覆盖，改为手工套用后高亮失效，标签不再代表当前条件。预置只是把条件树交给既有查询链路，不改变页面固定范围、数据权限或对象范围，也不绕过用户当前查询条件之外的服务端约束。

个人模板由员工在业务页自助维护：当前高级条件非空时可「存为快捷筛选」保存为 `scope=personal`，并在「管理我的快捷筛选」中重命名、设为默认或删除，接口为 `POST/PUT/DELETE /zsjos/advanced-filter-template/personal`。重命名只改名称，不把当前条件写回该模板；`defaultTemplate` 的互斥范围是同一页面、同一 scope、同一 owner，因此个人默认与系统默认互不覆盖，可以并存。页面自动套用时的优先级为**个人默认高于系统默认**，该规则由服务端唯一实现在 `visible-list` 响应中：返回项带 `effectiveDefault=true` 的那条即页面应当套用的模板，至多一条；两者都未设默认时全部为 `false`，页面不自动套用。`system-list` 供管理页使用，不参与页面自动套用，其 `effectiveDefault` 恒为 `false`。前端不得自行按 `scope` 或 `defaultTemplate` 推断优先级。

根组和一级子组支持 `AND/OR`，最多 5 个子组和 20 个条件；根组可为空并表示不追加筛选，一级子组不得为空。`in/not_in` 必须提供 1–100 个非空且类型有效的值，标量运算值不得为空，`between` 两端必须类型有效且起点不晚于终点；SQL 与内存指标筛选使用同一校验并统一返回 `ADVANCED_FILTER_INVALID`。绝对日期使用 Unix epoch 毫秒。同一关联范围内的正向 `AND` 条件合并到同一个 `EXISTS`，保证商品与金额等条件由同一张订单、服务条件由同一条服务关系满足；关联字段的“不属于、不等于、不包含”编译为单层 `NOT EXISTS`，内部保持正向谓词。租户、当前用户对象范围、部门范围、待处理/已处理页签和业务池范围始终作为条件树之外的固定约束，不能被 `OR` 绕过。

| 接口 | 权限 |
| --- | --- |
| `GET /zsjos/lead/assignment-rule/get` | `zsjos:lead-rule:query` |
| `PUT /zsjos/lead/assignment-rule/update` | `zsjos:lead-rule:update` |
| `POST /zsjos/lead/{id}/admin-transfer` | `zsjos:lead:transfer` + 客资对象检查 |
| `GET /zsjos/lead-follow-up-rule/get` | `zsjos:lead-follow-up-rule:query` |
| `PUT /zsjos/lead-follow-up-rule/update` | `zsjos:lead-follow-up-rule:update` |
| `GET /zsjos/lead-follow-up-rule/runtime-setting` | 已登录用户；只返回当前租户消息浮窗时长 |
| `GET /zsjos/lead/qualification-exception/page` | 后端兼容查询接口；Workbench 不再提供独立异常客资路由 |
| `POST /zsjos/lead/qualification-exception/search-page` | 同异常客资固定范围，组合关键词与高级条件 |
| `POST /zsjos/lead/search-page` | 通用客资管理范围内组合关键词与高级条件 |
| `POST /zsjos/lead/aging-pool/search-page` | 公海固定范围内组合关键词与高级条件 |
| `POST /zsjos/lead/appeal/inbox/search-page`、`search-cursor` | 本人有权处理的申诉任务范围内组合关键词与申诉/关联客资条件 |
| `POST /zsjos/lead-duplicate-review/search-page` | 租户复核队列内按结构化提交快照和复核字段筛选；不提供原始 JSON 检索 |
| `POST /zsjos/subordinate-sales/search-page` | 当前用户可见下属范围内先聚合业务指标、再筛选和分页 |
| `POST /zsjos/subordinate-sales/{salesUserId}/leads/search-page` | 指定可见下属的名下客资固定范围内组合关键词与高级条件 |
| `GET /zsjos/advanced-filter-template/visible-list?scene=&pageKey=` | 按 `scene` 判定，与同场景 `GET /zsjos/advanced-filter/catalog` 的权限分支完全一致；返回当前页面启用的系统预置和本人个人模板，按默认模板、排序、编号排列 |
| `POST/PUT/DELETE /zsjos/advanced-filter-template/personal` | 已登录用户需具备对应业务页面查询类权限；创建、修改或删除本人个人模板 |
| `GET /zsjos/advanced-filter-template/system-list?scene=&pageKey=` | `zsjos:advanced-filter-template:query` |
| `POST/PUT/DELETE /zsjos/advanced-filter-template/system` | `zsjos:advanced-filter-template:update` |
| `GET /zsjos/lead/{id}/transfer-candidates` | `zsjos:lead:qualification:manage` + 异常客资对象权限 |
| `POST /zsjos/lead/{id}/restore` | `zsjos:lead:qualification:manage` + 异常客资对象权限 |
| `POST /zsjos/lead/{id}/transfer` | `zsjos:lead:qualification:manage` + 异常客资对象权限 |
| `POST /zsjos/lead/{id}/recycle` | `zsjos:lead:qualification:manage` + 异常客资对象权限 |
| `POST /zsjos/lead/{id}/release-to-claim-pool` | `zsjos:lead:qualification:manage` + 异常客资对象权限 |

统一客资详情的 `availableActions` 在满足同一权限与对象范围时返回 `QUALIFICATION_RESTORE`、
`QUALIFICATION_TRANSFER`、`QUALIFICATION_RECYCLE` 和 `QUALIFICATION_RELEASE`；Workbench 将这些动作
渲染在客资详情的 `lead-action-toolbar` 操作集合中。前端不得依据角色或状态自行补齐动作。

规则参数范围为接单超时 10–3600 秒、最大尝试 1–20 次。修改只影响之后提交的客资，进行中客资继续使用提交时规则快照。

自动派单候选来自租户隔离的 Redis 轮询池。工作台每 30 秒刷新页面心跳，心跳键 90 秒过期；接单偏好持久化在 `zsjos_sales_dispatch_preference`，首次默认暂停。每次派单最多旋转初始池长度的三倍，跳过离线、暂停、已有待接客资以及当前客资已经尝试过的销售。Redis 原子预留 `lead:lock` 与 `sale:pending` 后，数据库条件更新才确认 `pending_acceptance`；Redis 不是客资状态或归属的事实源。

派单状态接口的资格判断复用人员关系场景配置的候选资格规则（权限或岗位及账号启用状态），不加载候选列表的部门名称；部门资料可见范围不参与该资格判断。候选列表仍按原有流程补充展示资料。

顶部接单控件与工作台全局状态提示复用同一状态和 30 秒心跳。接口确认具备销售资格但状态加载失败、页面/实时连接离线或接单偏好暂停时，所有工作台路由都会在全局内容区域显示醒目提示，并按该优先级处理；顶部“接单暂停”和“页面离线”状态使用红色标签。不具备销售资格的用户不显示可恢复式接单提示。

拒单立即释放预留并重新派发，不对销售实施冷却。自动派单超时继续由数据库 `pending_expires_at` 扫描处理，不能通过扫描已经过期消失的 Redis 键恢复客资。Redis 暂不可用时客资保持 `unassigned`，租户定时任务恢复后重试；三圈确实无人可接或达到最大实际尝试次数时进入抢单池。指定派单不进入在线轮询。
首次跟进时限独立配置，范围为 5–10080 分钟，默认 1440 分钟；有效性判定时限范围为 5–43200 分钟，默认 4320 分钟。相同租户规则还维护 `notificationPopupDurationMinutes`（1–30 分钟，默认 5）和 `duplicateAutoResolutionEnabled`（默认 `false`）。运行时接口只暴露浮窗时长，管理读取和更新接口返回全部字段；更新请求必须携带读取时的 `version`，版本冲突返回 `1_900_003_079`。首次跟进和有效性判定截止时间均从当前归属成立时间计算，并在对应任务创建时固化规则版本和截止时间；修改不追溯已有任务。首次跟进完成前，客资处理阶段仍为待首跟，但已返回有效性判定截止时间；首次跟进逾期只提醒，判定截止后进入挂起。

提交人、负责人和公海筛选配置接口只返回筛选结构与标签，不返回分组或选项数量，也不执行状态统计 SQL。列表分页总数及独立统计接口保持原契约。

## 判定前跟进与今日待办

客资仍为 `submitted` 且分配状态为 `owned` 时，跟进记录归属于 Lead，不提前创建 Opportunity，也不改变 Lead 主状态。当前负责人通过 `POST /zsjos/lead/{id}/follow-ups` 追加记录；只读查询与客资详情使用同一对象范围：持有对应关系查询权限的提交人、负责人及其当前部门层级主管，或 `zsjos:lead:query-all` 账号可读取。记录时间由服务端生成，记录不提供修改或删除接口。

客资提交、跟进、有效性判定和异常处置中的 `idempotencyKey` 表示一次用户操作意图。前端在打开或重置为新操作时生成一次键，同一操作的快速重复点击、上传失败、网络失败和超时重试必须复用该键，只有服务端确认成功后才能轮换。前端提交状态必须使用同步互斥保护，按钮 loading 仅作为交互反馈，不能作为唯一防重手段。

成交前跟进提交完成当前分配历史对应的 `lead_first_follow_up`，并按必填的下次跟进时间替换 `lead_follow_up_reminder`；已成交跟进不补写首跟事实，下次时间可选。填写的 `nextFollowUpAt` 必须使用 epoch 毫秒数且换算后的服务端时间晚于提交时刻。`GET /zsjos/business-task/my-summary` 与 `GET /zsjos/business-task/my-page` 只返回当前用户的 ZSJOS 任务，使用 `unscheduled`、`overdue`、`today`、`future` 分组；任务没有通用完成接口，只能由接单或填写跟进等业务动作完成。

客资归属成立时立即创建 `lead_first_follow_up` 和 `lead_qualification` 任务。客资响应由服务端返回正交的 `qualificationStatus`、`followUpStatus`、`assignmentStatus`、`operationalStatus` 和 `availableActions`，并附带首跟截止、判定截止、挂起时间、判定结果、Opportunity 摘要与无效判定附件；附件 URL 在详情读取时重新签名。前端不组合 `status` 和 `assignmentStatus` 自行推断状态或写操作。系统不再因无进展预警或宽限期自动释放客资到抢单池；抢单池进入仅由明确的分配或主管处置动作触发。历史有效客资通过 V019 补齐唯一 `initial_conversion` Opportunity。

客资详情的生命周期时间字段按业务事实投影：`qualifiedAt` 表示判定有效/无效时间，`salesOrderSubmittedAt` 表示最近一次首购订单录入并提交审批的时间，`convertedAt` 仅在关联 Opportunity 进入 `won` 时返回其 `wonAt`，不再把创建 Opportunity 的历史兼容时间展示为成交转化。

`POST /zsjos/lead/{id}/judge-valid` 请求为 `{ leadCategory?: string | null, remark, idempotencyKey }`。非空分类必须是启用的 `zsjos_lead_category` 字典值，备注必填且最长 2000 字。接口在一个事务内完成判定任务、保存备注并创建或恢复该客户唯一的 `initial_conversion` Opportunity；Lead 保持 `valid + owned`，Opportunity 承担后续 `open/following/deal_pending_approval/won/lost` 阶段。恢复旧 Opportunity 时保留历史记录，清除旧流失字段，并把正式负责人同步为 Lead 负责人。`zsjos_lead_valid_remark_template` 只提供管理员维护的快捷备注，初始化为空。判无效请求同时提交启用的 `zsjos_lead_invalid_reason` 字典值、必填备注和最多 9 个已上传附件引用；入口同时适用于待判定 Lead 与推进中的 Opportunity，后者会在同一事务改为 `lost`。`zsjos_lead_invalid_remark_template` 仅提供快捷文案，接口只保存销售最终编辑文本。

统一跟进接口按状态路由：`submitted` 写 Lead 跟进并维护首跟、判定和提醒任务；`valid` 且存在活动 Opportunity 时写 Opportunity 跟进并维护机会状态和提醒；`invalid` 写 Lead 证据记录，不创建任何任务。分类在三类请求中都可保留、修改或清空。分页查询合并 Lead 与 Opportunity 跟进，按发生时间倒序返回，并通过 `recordScope` 标识 `lead` 或 `opportunity`。

`PUT /zsjos/lead/{id}/basic-info` 仅供当前负责人使用，接受姓名、手机号、微信号、地区、可空客资分类、至少一项且唯一主意向的课程快照以及必填修改原因。手机号与微信号至少保留一个；身份冲突、无效地区、无效字典或产品目录引用均拒绝且事务回滚。联系人同步到 Person 和当前 Lead，事件只记录变更字段名和修改原因，不记录完整联系方式。

每分钟按租户扫描已到判定截止时间的 `submitted + owned` 客资，并在行锁下再次校验后改为 `suspended`。截止时间已到但扫描尚未提交时，当前销售仍可判定；扫描先提交后，跟进、判定、资料修改、转派和建单均由服务端拒绝。恢复与转派创建新判定轮次；回收进入 `recycle_pending` 并清除销售；释放进入抢单池，被抢后重新创建首跟任务。

销售主管通过独立的 `zsjos:subordinate-sales:lead-*` 按钮权限处置管理范围内的未成交客资。

销售本人可通过 `POST /zsjos/lead/owner/{leadId}/transfer`（权限
`zsjos:lead:owner-transfer`）将本人负责的客资转派给启用销售；目标列表由
`GET /zsjos/lead/owner/transfer-candidates` 返回，转派成功后沿用既有流转通知通知目标销售。`POST
/zsjos/lead/owner/{leadId}/release-public-sea`（权限
`zsjos:lead:owner-release-public-sea`）将本人符合公海前置条件的客资进入公海待分配，
保留正式负责人快照并沿用公海通知与后续认领逻辑。
销售本人转派以成交订单活动状态为业务前置条件：对应客资存在 `pending_approval` 或
`revision_required` 首购订单时，详情不返回 `OWNER_TRANSFER`，转派命令返回
`LEAD_OWNER_TRANSFER_DEAL_ACTIVE`；订单主动终止后恢复。该约束不改变主管、资格异常或管理员转派权限。
判有效前释放进入抢单池并清除当前归属；判有效后释放创建公海周期，保留正式归属，
可同步指定符合范围的实际跟进销售。挂起状态额外允许恢复，成交和关闭状态不允许这些操作。

普通主管必须是原销售部门或其上级部门负责人，只能转派给本人管理部门及子部门的启用销售专员。`zsjos:lead:qualification:manage-all` 允许当前租户内跨部门处置，但不绕过租户隔离。超时挂起和主管处置通过既有业务通知机制通知相关销售、操作主管及原销售部门负责人链。

旧的 `POST /zsjos/lead/{id}/admin-transfer` 不接受 `suspended` 或 `recycle_pending` 客资，异常客资必须通过上述专用处置接口，避免绕过理由、轮次重启和分配历史规则。

## 部署顺序

1. 评审并备份目标库，确认历史 `zsjos_lead` 兼容空值策略。
2. 单独确认后按版本顺序执行至 `script/sql/mysql/migrations/V038__duplicate_lead_review.sql`；代码实现不会自动执行迁移，V038 不授予任何角色菜单。
3. 配置 `zsjos_lead_category`、`zsjos_lead_invalid_reason`、`zsjos_lead_invalid_remark_template` 与 `zsjos_lead_valid_remark_template` 字典数据及岗位对应菜单权限；快捷备注字典类型初始化为空。
4. 接入并验证真实产品 SDK 适配器后才开放提交入口。
5. 使用真实 MySQL、Redis、文件存储和 WebSocket 验证提交、超时转派、并发接单、业务任务及抢单。
6. JVM、MySQL 连接会话和产品展示统一使用 `Asia/Shanghai`。MySQL Connector/J 8 连接必须配置 `connectionTimeZone=Asia/Shanghai&forceConnectionTimeZoneToSession=true`，并通过 `script/sql/mysql/verify-zsjos-time-contract.sql` 只读核验 `@@session.time_zone` 和历史异常。
主动抢单使用租户派单规则 `dailyClaimLimit`，默认每个销售每个北京时间自然日 5 条。服务端按
租户、销售和日期原子保留额度；自动接单开关只影响自动轮询，不影响主动抢单。
## 请求提交人协助

- 员工端详情动作码：`REQUEST_SUBMITTER_ASSIST`。当用户可读该 Lead 且持有
  `zsjos:lead:request-submitter-assist` 时投影，不受 Lead 状态或分配状态限制。
- 命令：`POST /admin-api/zsjos/lead/{id}/submitter-assist-request`。
- 请求字段：`problem`（必填，最多 1000 字）、`expectedAssistance`（必填，最多 1000 字）、
  `remark`（选填，最多 2000 字）、`attachments`（选填，最多 9 个 Infra 文件引用）和
  `idempotencyKey`（必填）。附件沿用 Lead 管理端上传契约，仅支持 JPG、PNG、WebP，单文件不超过 10 MB。
- 命令使用 Lead 统一对象读取权限，并写入独立的 `zsjos_lead_submitter_assist_request` 快照和
  `lead_submitter_assist_requested` 业务事件。用户可见标识始终为快照 `leadNo`。
- 内部员工提交时，消息和 `lead_submitter_assist` 待办均落给提交人。兼职提交时，主消息发送给
  Partner 账号；待办与补充提醒发送给当前 `PartnerOwnership` 员工，当前关系缺失时回落 Lead
  的历史所属员工快照。补充提醒固定包含“请提醒兼职人员尽快协助处理”。

协助申请现在支持双向闭环：同一客资同时只允许一条未完成申请。内部员工提交时，提交人收到通知并生成本人待办；兼职提交时通知兼职，有当前所属运营才提醒运营并生成运营待办，没有所属运营则只通知兼职、不生成待办。待办打开客资后进入“协助历史”回复表单，填写多行备注和图片附件（JPG、PNG、WebP，沿用客资附件接口），提交即完成申请并通知原发起销售。历史接口为 `GET /admin-api/zsjos/lead/{leadId}/submitter-assist/history/page`，回复接口为 `POST /admin-api/zsjos/lead/{leadId}/submitter-assist/{requestId}/reply`；标签页需要独立权限 `zsjos:lead:submitter-assist:read`。

## 客资表格批量处置

Workbench 客资表格使用 ProTable 原生 `rowSelection`，通过 `preserveSelectedRowKeys` 支持跨分页选择，
最多保留 100 条。普通刷新保留仍存在的选择，筛选或状态切换清空选择，批量完成后清空选择并刷新列表。

批量接口为 `POST /admin-api/zsjos/lead/batch/{action}`，`action` 仅支持
`transfer`、`restore`、`recycle`、`release-claim-pool` 和 `release-public-sea`。请求包含去重后的
`leadIds`、必填 `reason`、可选目标销售/公海协作销售和 `idempotencyKey`。目标人员由既有销售资格接口
提供，服务端再次校验资格、对象权限、当前状态和业务前置条件。

接口逐条调用现有 Lead 命令并返回 `successCount`、`failureCount` 及逐条 `leadNo`、稳定错误码和用户可读消息；
单条失败不回滚其他成功项，系统异常统一返回“操作失败，请刷新后重试”。


### 客资概览与列表时间刷新（2026-09-22）

工作台弹窗和跟进记录页提交成功后，刷新最近跟进卡片、详情和列表；表格保留当前页。
最近跟进读取失败显示错误并允许重试，切换客资后忽略旧请求的迟到响应。
ADMIN 客资分页（含下属销售列表）与详情采用一致的时间来源：`nextFollowUpAt` 来自该客资最新的待处理跟进提醒任务，仍遵循既有跟进读取权限；已完成或取消的提醒不回退主表旧值。
`salesOrderSubmittedAt` 为最近首购订单提交时间，按提交时间和订单 ID 降序取第一条。分页批量读取关联时间，避免逐行查询。Admin 与 Workbench 共用字段类型和毫秒时间契约，历史缺失值保持为空。
本次不修复历史首跟/判定任务数据，不变更判定状态、权限或计时规则。

协助历史以每次申请为一组显示左右对话，申请在左、回复在右，保留服务端分页和待回复入口。申请完整展示问题、希望协助方式、备注和附件；回复展示协助备注、附件、实际回复人快照及回复时间，多行文本保留换行。历史响应补全 `remark`、`requesterName`、`requestAttachments`、`responseAttachments`，附件含 `infraFileId/name/type/size/url`；仅在客资读取授权通过后，按已存附件引用生成 600 秒访问地址，预览/下载通过历史接口重新获取。发起人旧记录仅存用户 ID，`requesterName` 为 System 当前姓名（非历史快照），账号缺失时显示未记录，不借用提交人身份。未保存过的历史内容不推测补造。无需数据库迁移；后端需加载新增投影后，前端才能展示已持久化的完整内容。现有 Admin/H5 无该历史接口消费者。


### 首跟事实与迁移恢复（2026-09-23）

新增跟进不再依赖首跟任务存在或 UPDATE 成功才写首跟时间。当前负责人、当前归属周期的最早跟进记录决定缺失的首跟事实，已有首跟时间不被覆盖；任务完成保持同步且不重置判定轮次。合作人的跟进不能代替负责人首跟。`followUpStatus` 不再因缺少判定截止时间把已首跟客资显示为待首跟；主管直接进入判定的既有例外保持。
Admin/Workbench 继续读取同一 `followUpStatus`、`handlingStage`、`currentAssignmentFirstFollowUpAt` 和判定时间字段，接口类型、菜单和对象权限不变。历史数据恢复必须显式执行，不能在 GET 请求中补任务或重计时。


## 企微待接单通知与轮次校验（2026-09-24）

首次派单 `zsjos.lead.assigned`、重新派单 `zsjos.lead.reassigned` 恢复通过 System
通知总线按已启用规则进入 outbox。分配历史编号构成 `lead-dispatch:<historyId>` 幂等键；
事件与派单变更使用同一事务。事件保存候选销售、派单历史编号、派出时间、截止时间，
指定派单保留空截止时间。站内信及企微各自遵循原有规则，不补发历史派单，不恢复抢单成功通知。

企微每次发送或重试前由 Lead 场景校验租户、待接状态、候选及最新派单历史。
已超时返回 `LEAD_ASSIGNMENT_EXPIRED`，失效或换轮次返回 `LEAD_ASSIGNMENT_OBSOLETE`，
缺少轮次快照返回 `LEAD_ASSIGNMENT_SNAPSHOT_MISSING`。逐人记录为 skipped，不调用企微接口；
查询异常保持失败/重试，不能记为成功跳过。总任务完成不能证明每人收件。

派单卡片通过现有一次性企微票据跳至 Workbench
`/zsjos/leads/manage?assignmentLeadId=<internal-id>&assignmentHistoryId=<history-id>`，
复用全局接单弹窗，并通过我的待接列表重新确认轮次。URL 标识仅用于定位；页面仍使用 `leadNo`。
旧票据和其他业务卡片沿用原跳转。卡片已失效时不自动打开另一条客资。

`POST /zsjos/lead/{id}/accept` 增加可选查询参数 `expectedAssignmentHistoryId`。
携带参数时，在租户 Lead 行锁下读取最新派单历史并校验候选、轮次和截止时间，
再执行原条件更新；旧调用不传参数保持兼容。控制器权限及 Service 对象授权保持生效。
Workbench 所有现有接单动作提交当前轮次；Admin 当前没有此接口或待接列表消费者，
仅维护派单关系，不新增 Admin 接单页面。

Workbench 保留 WebSocket、15 秒轮询、页面恢复可见及重连刷新。
企微发送前检查无法撤回已发消息；点击校验与接单的后端轮次校验共同处理状态变化。
生产时限不自动调整，发布前须测量实际企微收件与接单耗时。
诊断与验收限制见 [派单通知诊断记录](wecom-lead-dispatch-diagnosis.md)。


### 来源关联通知的适用性与异常

来源关联的接收范围仍遵循本文第 65 行所述自拓可选提供方契约，不扩大到普通新媒体或兼职。
仅来源关联角色且未指定用户的规则：普通新媒体/兼职返回
`LEAD_SOURCE_LINK_NOT_APPLICABLE`；明确记录未选提供方返回
`LEAD_SOURCE_PROVIDER_NOT_SELECTED`；选择自己返回 `LEAD_SOURCE_PROVIDER_IS_OPERATOR`。
以上在站内信和企微均记为 skipped，不发送、不重试。

`sourceProviderRecorded` 不是 true 的历史自拓记录不能当作明确未选，返回
`LEAD_SOURCE_SNAPSHOT_MISSING`；选中提供方与正式归属不一致返回
`LEAD_SOURCE_ATTRIBUTION_MISMATCH`；客资不存在或提交人缺失分别返回
`LEAD_SOURCE_RECORD_MISSING` / `LEAD_SOURCE_OPERATOR_MISSING`。这些异常保留失败记录，
不靠重试掩盖，不推测或补写历史来源。自定义混合角色、指定用户规则保持原有并集语义。


### 已成交客资继续跟进

已成交客资仍通过 `POST /zsjos/lead/{id}/follow-ups` 新增跟进，沿用 `zsjos:lead-follow-up:create` 和对象操作关系、租户隔离；服务端投影 `ADD_FOLLOW_UP`。仅 `status=won` 的 `nextFollowUpAt` 可省略或为 null，其他可跟进状态仍必填；提供的时间必须为未来 epoch 毫秒。成交审批中、无效、挂起和关闭状态不因此放开。

首购订单生效仍取消旧首跟任务和跟进提醒。成交后新跟进完成当前操作人的现有提醒；不填时间不新建提醒，填未来时间则生成新的提醒，不恢复已取消任务、不补写首跟事实。有商机沿用商机跟进记录，历史无商机的成交客资沿用客资跟进记录，不创建虚构商机。更新跟进次数、最近活动与分类/销售阶段快照，保留成交状态及订单。销售阶段可继续按字典修改，不回填历史快照。下次跟进展示继续只读取待处理提醒任务。

Workbench 弹窗和详情表单同步支持可选时间；Vue Admin 保持跟进历史只读并兼容空时间。学员服务联系不在此接口范围内。
