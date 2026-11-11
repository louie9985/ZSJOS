# ZSJOS 成交订单 API

## 权限与对象范围

- `zsjos:sales-order:create`：当前正式归属销售录入成交订单；公海协同销售必须先完成正式转派，不能仅凭协同身份录入。
- `zsjos:sales-order:query-own`：分页和读取当前用户作为实际提交人的历史订单。详情仍执行 `read-own` 对象校验，不随客资负责人变化。
- `zsjos:sales-order:query-management`：订单管理查询。后端按 System 数据权限解析可见提交人，并以订单提交人为范围；不按角色名称推断。
- `zsjos:sales-order:query-all`：订单管理下的“查看全部订单”按钮权限。与 `query-management` 配合使用，将管理分页、游标、高级筛选、统计和管理详情扩展至全部订单；未授予时保持既有 System 数据范围。仍保留租户、逻辑删除和用户显式筛选。不会授予改单、终止、审批或旧本人/团队入口权限。管理员通过角色管理分配，V351 仅新增菜单元数据。
- `zsjos:sales-order:query`：读取具备对象关系的订单详情。
- `zsjos:sales-order:review`：工作台成交审批菜单与直接详情查询权限。审批池、通过和驳回接口的业务授权以配置部门成员关系和本人 BPM 任务为准，不按角色名称推断。
- `zsjos:sales-order:supervisor-confirm`：统一“成交订单审批”入口中的主管待办/已办列表及确认决定权限。V076 为启用的稳定 `sales_manager` 角色补齐该权限；主管命令还要求当前用户是申请时固化的订单销售直属主管、持有本人 BPM 加签任务并通过订单对象校验。

## 引流员工客户订单读取（2026-11-04）

客资详情的 customer-orders 列表和详情保留既有功能权限与 `sales-history-read` 对象校验。在此基础上，当前用户为该客资的员工提供人（`providerOwnerType=system_user` 且 `providerOwnerId` 为本人）时，可查看同一当前 Person 的全部订单，包括复购及身份合并后的其他原始档案订单。不要求本人录单；代录人不因此取得引流提供人资格，Partner ID 也不能当作员工 ID。其他查看者仍按原始身份或独立订单权限读取，不能仅凭姓名/手机号相同跨客户读取。

该授权仅用于客资上下文只读历史，不扩展订单管理/本人/团队列表、修改、审批或导出权限，不改变订单关联、贡献快照及新媒体大盘统计。高级筛选继续按客户订单判断。Workbench 的 LeadCustomerOrders 已调用这两个接口；Vue Admin 当前无 customer-orders 消费入口，其既有订单详情接口权限保持原样。

## 接口

- `GET /zsjos/sales-order/product/catalog`：全部启用产品/SKU 目录。
- `POST /zsjos/sales-order/lead/{leadId}/submit`：创建首购订单。服务端要求客户、主客资和商机属于同一客户，且客户尚无生效订单。
- 当前首购订单入口以既有 `leadId` 为前置上下文，不重新执行客资提交查重；未来若新增“订单同时创建客资”的直接入口，应复用客资提交查重服务，强重复硬拦截、疑似重复进入复核。
- `POST /zsjos/sales-order/lead/{leadId}/repurchase`：从系统客户客资详情创建复购；客资只用于发起与权限上下文，订单交易关系仅关联客户；录单资料关联独立保存于审批轮次快照。
- 存量例外（2026-11-05）：用户明确授权 test 租户 1 的指定已通过复购订单补充同客户客资关联，采用同录单人优先、唯一候选、正常状态优先及明确裁定；不改变新复购创建规则、订单归属或历史审批快照。既有对象读取关系会按补充关联生效，历史 `leadProfile` 不补造。范围、影响及验证见[已通过复购订单关联客资](../operations/repurchase-lead-link-20261105.md)。
- 后续明确授权的快照回填（同日）：上述 24 条新关联订单的当前已通过审批轮次，按当前关联 Lead 资料和当前 System 人员名称补齐缺失 `leadProfile`；分类/渠道使用 Lead 已保存标签。此定向修复覆盖上一条“历史快照不补写”的边界，额外保存 `leadProfileBackfill` 的来源、时间及 `historicalAtSubmission=false`，不声称还原录单时资料。原有五条客资快照及其他快照字段不变，运行时仍不自动回查当前主档补历史。详情见同一操作记录的快照回填节。
- 同日身份纠错：原客户因微信占位值 `无` 误归并，已拆分并将 `OD202610150855120003` 的关联和当前轮次回填资料更正为 `KZ202610130845270001`，保留更正来源。金额、审批、归属及新复购接口不变；见[误归并修复与全量审计](../operations/false-person-merge-20261105.md)。
- `POST /zsjos/sales-order/external-repurchase`：录入系统外历史客户复购。唯一命中无主客资客户时复用客户，无命中时创建客户主档；多客户命中、身份冲突或已有主客资时拒绝。
- `GET /zsjos/sales-order/lead/{leadId}/customer-orders`：要求 `zsjos:lead-detail:order-read`，并以统一 Lead 详情对象关系按客户聚合首购与复购订单。
- `GET /zsjos/sales-order/lead/{leadId}/customer-orders/{orderId}`：同样累计标签 feature 权限、Lead 对象关系和订单与客资 `personId` 一致性校验。
- `PUT /zsjos/sales-order/{id}/resubmit`：对允许重提的 `revision_required` 订单创建全新订单并返回新订单 ID。旧订单进入 `superseded` 终态，使用 `supersedes_order_id` / `superseded_by_order_id` 双向关联；旧审批轮次、明细、凭证和驳回原因保持不变，新订单从第 1 个审批轮次独立启动。若同一客资已存在另一张活动首购订单，或同一客户已存在另一张活动复购订单，则拒绝重提；复购创建与重提通过客户主档行锁串行化。
- `GET /zsjos/sales-order/{id}`：订单、课程、凭证和当前审批轮次详情。新建或重提审批轮次会在既有 `order_snapshot` 中保存录单时订单字典显示值（性质、服务期限、学员来源、收费方式、支付方式）以及关联客资的档案字段和来源/分类/渠道显示值；详情优先使用该轮次快照，因此字典或客资后续变更、停用不会让已录订单显示为“标签未配置”。历史轮次没有这些快照字段时，返回历史缺失信息，不回退当前字典、客资或用户资料。首购订单有历史客资快照时，`leadProfile` 返回客资业务编号 `leadNo`、客户联系方式、来源、提交人、所属销售、分类、渠道、派单方式和地区，用于审批详情展示；无关联客资的复购订单不返回该字段；新录复购选择或自动匹配的客资档案通过独立 associatedLeadId 与 leadProfile 保存于轮次快照，不写交易 leadId，且内部 `leadId` 不作为客资编号回退。`registrationApproval` 与 `financeApproval` 分别返回报名履约、财务节点的 `pending/approved/rejected/cancelled` 汇总状态、实际审核人用户 ID/动作时姓名快照及节点时间。审核身份和结果只读自 BPM 当前任务和历史任务，不在订单域重复持久化；界面展示审核人姓名、结果和审核时间，不展示用户 ID。
- `GET /zsjos/sales-order/management/{id}`：订单管理详情。仅接受 `zsjos:sales-order:query-management`，并按当前 System 数据权限（或独立 `query-all`）校验订单提交人范围；不会继承旧 `query-team` 的团队读取范围。
- `GET /zsjos/sales-order/my-page`：本人提交订单分页，支持 `status` 和订单号/学员姓名/手机号 `keyword`。列表响应直接投影订单详情中可用于表格展示的业务字段，包括购买方、学员联系方式与地区、历史字典标签快照、课程摘要、付款与服务信息、备注、关联客资业务编号及来源摘要；课程明细按当前页批量读取，不要求前端逐行请求详情。内部关联 ID 仍只用于路由和命令，不作为用户可见列。
- `GET /zsjos/sales-order/management-page`、`POST /zsjos/sales-order/management-search-page`：订单管理分页及高级筛选，服务端按 System 数据权限（或独立 `query-all`）计算订单提交人范围。
- `GET /zsjos/sales-order/management-cursor`、`POST /zsjos/sales-order/management-search-cursor`：订单管理游标查询，与分页接口使用相同可见范围。
- `GET /zsjos/sales-order/management-status-counts`：订单管理状态统计，与订单列表使用相同可见范围。
- `POST /zsjos/sales-order/my-search-page`：在本人订单固定范围内组合关键词与高级条件树；高级条件与用户选择的可选状态分组同时生效（管理员全量读取仅解除权限范围，不解除用户筛选）。
- `GET /zsjos/sales-order/my-status-counts`：本人订单的全部、待审核、待重提、已通过及历史旧单（已重提）数量。
- `GET /zsjos/sales-order/my/{id}`：本人订单完整详情；已驳回订单包含最新轮次 `decisionReason` 和 `canRevise`。
- `GET /zsjos/sales-order/team-page`、`POST /zsjos/sales-order/team-search-page`：团队订单分页查询，支持状态、关键词和高级条件；团队成员提交范围始终作为固定边界。
- `GET /zsjos/sales-order/team-cursor`、`POST /zsjos/sales-order/team-search-cursor`：团队订单游标查询，筛选条件与分页接口一致。
- `GET /zsjos/sales-order/team-status-counts`：团队订单状态统计，范围与团队订单列表一致。
- Workbench `/zsjos/sales-orders` consumes the management cursor/search/count contracts.
  Workbench and Admin both use the management detail contract and server-projected command eligibility.
  Read-all is resolved server-side, so neither frontend needs a local data-scope override.
- `GET /zsjos/sales-order/approval/filter-profile`：保留当前租户已发布的待处理/已处理方案 `groups`，另返回订单状态页签 `orderStates`（全部、当前中心待审核、待审核、待重提、已通过、已终止、历史旧单（已重提）），以及当前用户按审批配置部门解析出的 `centers`。单中心用户只返回本中心；同时落入两个配置部门范围的用户返回报名履约和财务两个中心。
- `GET /zsjos/sales-order/approval/task-target?taskId=&view=todo|done`：成交订单专用的历史兼容定位接口，校验当前用户对 BPM 普通任务或主管加签任务的关系后，返回 `workType`、`orderId`、`taskId`、`taskDefinitionKey`、`center`、`confirmationId` 和处理状态，用于成交订单审批页的精确跳转。审批中心改用统一的 `/zsjos/bpm/business-task-target`。
- `GET /zsjos/sales-order/approval/notification-target?orderId=&sceneCode=&sourceEventKey=`：仅接受主管申请和主管决定两个通知场景，分别要求当前用户是指定主管或加签申请人；服务端使用消息既有的事件幂等键精确恢复确认记录和固化任务定位，并执行订单对象权限检查。兼容缺少事件键的旧消息时才回退该订单最新确认记录；前端不得提交用户 ID 或任意目标 URL。
- `GET /zsjos/sales-order/approval/inbox-page?center=registration|finance&groupKey=pending&optionKey=all&keyword=`：按当前用户审批任务、处理状态和中心分页查询订单列表，支持订单号、学员姓名和手机号搜索，并返回与本人/团队订单表格一致的可见业务字段及当前任务上下文；`handled` 仍可作为兼容参数。服务端将筛选条件与当前用户允许的 BPM 任务节点取交集，伪造无权中心返回权限错误，前端隐藏筛选项不是授权边界。
- `POST /zsjos/sales-order/approval/search-page`：在当前用户 BPM 任务和中心权限固定范围内组合关键词与高级条件；高级条件非空时忽略可选处理分组，但不扩大允许的任务节点。
- `PUT /zsjos/sales-order/{id}/approve`、`/reject`：处理当前 BPM 任务，必须提交 `taskId`、当前 `approvalRoundId`、订单/轮次版本和幂等键。`reason` 最长 1000 字；通过允许缺省、null、空串或纯空格，统一去除首尾空白并将未填写规范化为空串，财务及报名履约普通节点的新旧实例均选填；驳回必须提供非空白原因（含通用 BPM 入口）。订单行与轮次行锁保证审批、驳回和终止只有首个命令成功。
- `PUT /zsjos/sales-order/{id}/supervisor-confirmation/request`：当前报名履约或财务普通审批人申请订单销售主管审批。请求必须提交普通 `taskId`、轮次、订单/轮次版本、必填且不超过 1000 字的 `reason` 和幂等键。每轮最多一条申请，另一中心不能再次加签；BPM 创建并行加签任务，报名履约、财务和主管三方任务均保持可见可处理。申请加签后，该中心普通审批与对应主管审批都成为本轮通过条件：中心先通过时等待主管，主管先通过时等待中心；另一中心状态不受影响，任一方驳回则整轮驳回，三方全部通过后订单才通过。
- `GET /zsjos/sales-order/supervisor-confirmation/inbox-page`、`POST /zsjos/sales-order/supervisor-confirmation/search-page`：只查询当前用户作为指定主管的待办或已办，`handled=false|true` 区分状态，支持订单关键词和订单高级条件。
- `GET /zsjos/sales-order/supervisor-confirmation/inbox-cursor`、`POST /zsjos/sales-order/supervisor-confirmation/search-cursor`：与分页接口使用完全相同的主管、页签、关键词和高级条件；游标指纹绑定这些条件，条件变化后旧游标失效。
- `GET /zsjos/sales-order/supervisor-confirmation/{confirmationId}`：读取当前指定主管可见的主管确认记录，用于消息深链恢复主管视角。
- `PUT /zsjos/sales-order/{id}/supervisor-confirmation/confirm`、`/reject`：主管提交通过或驳回决定，必须携带 `confirmationId`、主管 BPM `taskId`、轮次、订单/轮次/确认记录版本、必填且不超过 1000 字的 `reason` 和幂等键。主管通过后，已先通过的中心任务完成汇合，尚未处理的中心任务继续等待普通审批；主管驳回使用 BPM 驳回整轮并退回销售补正。
- `PUT /zsjos/sales-order/{id}/terminate`：待审核或待重提订单的原创建人或正式负责人可填写原因终止订单；仅待审核订单终止当前 BPM 流程，包括已进入财务节点的订单。订单域先校验权限、状态、版本和幂等键，再通过 BPM 业务授权终止接口记录真实操作人、授权类型与原因。

## 快照边界

2026-10-12 经确认的人员修复可保存订单级 `repaired_actor_snapshot`。服务端校验修复来源、租户、订单及当前人员引用后，优先使用其人员展示；原轮次业务事实、导入旧员工证据和 BPM 历史仍保留。补录姓名记录本次采集时间，不表示原交易时姓名。Admin、Workbench 继续消费原人员响应字段；详见 [V312 操作与兼容说明](../operations/order-actor-snapshot-repair.md)。

- 订单课程/产品、SKU、规格属性、类目路径、录单价格和实收金额已经由订单明细的 `productSnapshot` 固化，产品目录后续改名、停用或调价不改变历史订单。
- 每次新建或重提审批轮次都由该轮次的 `order_snapshot` 固化订单业务字段、凭证引用、订单字典显示值，以及当时关联客资的业务编号、联系方式、来源、提交人、所属销售、分类、渠道、派单方式和地区。审批详情优先展示本轮快照，业务字典或客资后续变化不回写历史轮次。
- 历史轮次缺少新增快照字段时只兼容读取当前可用投影，不批量猜测或回填历史显示值；快照损坏也不会阻断详情读取。内部 `leadId` 始终只用于技术关联，不作为客户可见的 `leadNo` 回退。
- BPM 流程实例、当前/历史任务、实际审核人、审批结果和节点时间属于审批运行事实，继续以 `yudao-module-bpm` 为准，不复制进订单业务快照。权限、菜单、人员启停状态和当前待办也不是业务快照。

审批、驳回和终止命令使用租户内唯一的命令账本。相同幂等键只有在订单、轮次、流程实例、命令类型、审批节点、BPM 任务、操作人和规范化请求内容全部一致时才返回首次结果；任一属性不同均返回幂等冲突。BPM 调用失败会回滚订单、轮次和命令账本。终止命令不要求 `taskId`，但仍绑定当前订单、轮次、创建人和 BPM 实例。

活动公海池中的任何销售只能跟进并申请正式转派，或由主管直接转派；服务端拒绝公海期间的首购创建、成交补正和接续重提，不执行隐式转权。正式转派或主管退出成功后，客资退出公海并恢复正常成交资格。订单 `formalSalesUserId` 固化正式负责人，`submitterUserId` 记录实际操作人；后续驳回、取消或终止不回滚已完成的正式转派。历史已处于 `deal_pending` 的订单仍按原审批、驳回和终止流程完成，不代表当前允许在公海新建或补正成交。

外部复购和所有客户联系方式写入口共用 Person 身份写服务。手机号与微信号使用同一租户级占用空间，手机号命中微信号同样冲突；值仅去除首尾空格，微信号大小写敏感且最多 64 个字符。同一 Person 的手机号与微信号可填写相同值并只占用一次。并发创建相同联系方式时只允许创建一份客户主档，其他请求按已存在客户继续执行复购入口校验。

审批详情仅在当前中心节点仍为 `pending` 且当前待办仍提供 `taskId` 时显示通过、驳回操作。任一审核人处理该中心节点后，同中心其他用户不再显示操作按钮；服务端仍以 BPM 待办状态校验并拒绝过期或重复处理。
上线后创建的审批轮次返回 `supervisorConfirmationEnabled=true`。申请时服务端从订单 `formalSalesUserId` 对应销售的当前直属部门读取 `leaderUserId`；正式销售或部门负责人未配置、负责人停用、负责人就是正式销售或负责人缺少主管确认权限分别返回稳定错误。确认记录为 `pending/confirmed/rejected/cancelled`；申请中心通过不会取消主管记录，申请中心或另一中心驳回、销售终止或流程取消会把未完成记录置为 `cancelled`。历史确认记录继续使用申请时固化的 `supervisorUserId`；上线前已存在轮次保持 `false`，继续旧流程且禁止发起主管确认。
- `POST /zsjos/sales-order/voucher/upload`：上传 JPG、PNG、WebP 或 PDF，最多 100 MB；服务端按文件内容识别真实类型。所有订单最终提交必须引用 1–6 个当前销售上传的凭证，包括零元订单和系统外复购。工作台延迟上传，管理端系统外复购选择后上传；两端都保留上传中、失败、重试、删除和预览状态。

提交命令必须携带 `idempotencyKey`。同一次录单或补正意图的快速重复点击、凭证上传失败和请求重试必须复用该键，只有服务端确认成功后才轮换；首次录单在锁定客资行后会再次检查该键，使并发相同请求返回已创建订单，而不是重复创建或误报已有活动订单。订单总金额不由客户端提交，服务端以各课程 `actualAmount` 重新汇总。手机号和微信号均为必填项，空白字符串视为未填；所有订单必须引用 1–6 份缴费凭证。省市名称由服务端根据省市级联编码生成快照；对于可直接选择且以 `cityCode=OTHER` 表示的省级区域，客户端 `cityName` 可以为空，最终快照以区域 API 解析结果为准。五类订单字典值由系统字典 API 校验。

## 审批状态页签（2026-10-09）

工作台按服务端 `orderStates` 的顺序和名称显示页签，不把旧的待办/已办标签改名冒充订单状态。分页、游标及高级搜索共用可选 `orderState`：`all`、`pending_approval`、`center_pending`、`revision_required`、`effective`、`terminated`、`superseded`。除 `center_pending` 使用下述中心待办口径外，状态取订单当前值，先与关键词、高级筛选相交，再交给 BPM 分页和计数；无匹配不回退全量。状态不会扩大当前用户、审批中心和本人 BPM 待办/已办的既有可见范围，历史导入但没有 BPM 任务的订单不会因此进入审批列表。

新增“当前中心待审核”（`center_pending`），放在“待审核”之前：仅查询整体仍为 `pending_approval` 的订单当前审批轮次，按所选中心及原有本人 BPM 任务权限取普通待办，要求 BPM 任务状态为 `RUNNING`。本中心已通过、仅等待另一中心或主管加签的任务不进入此分类；主管子任务也不进入。筛选在分页和计数前执行，分页、游标、关键词和高级筛选共用；空匹配不扩大范围。无需数据迁移，工作台直接渲染后端新增分类；Vue 原 `handled=false` 请求语义保持不变。

原“待审核”包括本中心已处理但另一中心尚未完成的订单，动作仍由当前 BPM 任务和节点状态决定。已完成任务以只读方式展示。`revision_required` 显示为“待重提”，可修改重提或由创建人、正式负责人终止；`terminated` 显示为“已终止”，为禁止重提的最终状态。重提创建新订单，旧单转为 `superseded`，新单从待审核开始，二者不是同一记录。最后一项“历史旧单（已重提）”用于查历史；全部包含历史旧单。订单状态不表示“我是否已处理”。

切换状态或中心会重置分页、游标和详情选择。状态查询沿用任务创建时间及任务 ID 的稳定排序，游标绑定状态、中心、用户与筛选条件。旧 `groups`、`groupKey`、`handled` 契约保留，Vue 管理端继续提交 `handled=false`；本次不改变其待办流程，也不改主管确认分类。无需修改数据库筛选方案或发布配置；先部署支持 `orderStates/orderState` 的后端，再部署工作台。

## 状态

- `pending_approval`：当前 BPM 轮次处理中，同一客资禁止新建活动订单。
- `revision_required`：任一中心驳回或流程取消，原订单可补正重提，同一客资仍禁止新建活动订单。
- `effective`：两个中心均通过；关联 Opportunity 和 Lead 在同一事务进入 `won`。客户主档保留，订单继续以必填 `personId` 关联客户。
- `terminated`：创建人或正式负责人主动终止待审核或待重提订单，禁止重提。待审核订单同时终止当前 BPM；待重提订单仅更新订单终止原因、时间和版本，保留已结束轮次的驳回事实，不重复撤销履约、返现及商机。沿用 `zsjos:sales-order:create` 权限、对象归属校验、版本及幂等保护。
- `superseded`：旧订单已被独立 successor 订单接续，不可再次重提；详情提供新订单入口。新订单详情提供返回旧订单入口。

已成交客资是否显示复购入口只消费服务端 `ENTER_REPURCHASE` 动作。该动作同时校验创建权限、对象权限、未挂起、客户已有生效订单且不存在活动复购订单；前端不得再以 `status=won` 自行推断。

`orderType` 由服务端判定并在创建后不可修改：首购为 `first_purchase`，复购为 `repurchase`。首购持久化 `personId + leadId + opportunityId`；系统客户和系统外复购都只持久化 `personId`，固定 `leadId=null`、`opportunityId=null`。复购的资料关联客资独立保存在审批轮次 `order_snapshot.associatedLeadId`，并使用 `leadProfile` 固化档案，不参与成交/返现/客资状态流转。复购不通知原客资提交人、不继承原兼职返现，生效时只新增客户的一张有效订单，不覆盖客资、商机或首次成交时间；同一客户最多一张活动复购订单。

审批轮次保存最终非通过原因快照。补正重提创建新的空原因轮次，不覆盖上一轮审计记录。

明确业务驳回（报名履约、财务或主管驳回）会为本轮提交人创建无截止的 `sales_order_revision` 业务任务，幂等键为 `sales-order-revision:{approvalRoundId}`，动作码为 `OPEN_SALES_ORDER_REVISION`。正式负责人可代为补正；新订单及第 1 轮 BPM 启动成功后完成旧订单补正任务；主动终止待重提订单时取消该补正任务。BPM 异常取消或异常终止不创建补正任务，历史订单不回填任务，既有驳回通知不因任务创建重复发送。重提事务任一步骤失败时旧订单保持原状态。
Sales-order notifications use the order business number (`orderNo`) and do not render the student or buyer name. Internal employee names used for approval roles remain available where required by the notification scene.
Applied V085 environments use forward migration V087 to repair safely resolvable residual `order.studentName` snapshots, including logically deleted history, without changing V085 or the occupied V086 permission migration. Missing tenant-scoped order relations block the repair; an internal order or Lead ID is never substituted as the visible identifier.
- `POST /zsjos/sales-order/student/{personId}/repurchase`：需要独立权限 `zsjos:sales-order:student-repurchase`。学习规划师只能为本人已接收且状态为服务中、已暂停或已结业的学员录入复购；该权限不开放通用订单创建或外部历史客户复购。订单提交人和正式销售归属均为当前学习规划师；订单仅关联 `personId`，不继承原客资或兼职返现。幂等重放还必须匹配客户、提交人、提交中心、复购原因和完整订单请求指纹，跨中心或不同请求复用 key 返回冲突。

### 双中心通过意见选填（所有审批轮次）

财务与报名履约普通节点不区分流程版本，通过意见统一选填，驳回原因必填。审批列表（分页/游标）、任务详情（`GET /{id}?taskId=...`，省略 taskId 保留原业务详情语义）及普通任务定位响应新增 `approvalReasonRequired`，由 BPM 的有效业务意见策略提供：成交订单双中心普通任务为 false，主管子任务为 true，其他业务仍使用原定义配置；缺失或读取失败时前端阻止提交并提供重试，不能按最新定义猜测。主管确认及其申请原因要求不变。

部署本次后端后，已有待审订单立即适用选填规则；双端继续消费服务端策略。无需发布新模型或迁移已有实例，2.1.0 资产仍作为后续部署的推荐定义。空意见不补写“同意”，详情与历史保持空值展示；幂等指纹与 BPM 提交使用同一规范化意见。


## 订单历史展示契约（2026-09-22）

新增审批轮次写入 `snapshotVersion=2`：`order` 为明确类型的交易事实，不再序列化 DO 的状态、版本、幂等信息；`orderLabels` 和 `selections` 保存字典类型、编码、选择时名称；`submitter`、`formalSales` 保存 ADMIN 用户 ID、当时姓名与业务身份；`leadProfile` 保存客资档案和员工/兼职主体类型、ID。课程仍以订单明细 `productSnapshot` 为唯一历史产品来源，凭证在交易事实中保存文件 ID 及元数据。生命周期、权限和当前数据范围不从快照恢复。

详情、列表、BPM 订单内容卡和财务导出使用同一历史投影。财务支付方式输出快照名称。已删除/改名的字典、用户、客资、产品不改变已有历史；历史产品资料缺失时展示“历史未记录”，不展示内部引用代替名称。有文件 ID 的凭证访问链接实时生成；文件服务无法提供链接时保留元数据并显示“凭证暂不可用”，不回退原始存储地址。

历史凭证兼容（2026-09-29）：读取支持 URL 字符串数组、凭证对象数组及混合数组，并保留原有顺序。没有 `infraFileId` 的历史凭证沿用保存的地址；按已确认的旧 CRM 访问规则，以 `/media` 开头的路径拼接 `https://crm.zhongshijian.top`，已有绝对 URL 保持不变。无文件 ID 的凭证不参与文件签名，不补造 ID、文件名、类型或大小。兼容仅作用于读取投影，不回写历史订单或审批快照；新提交仍使用校验后的文件 ID 和元数据。

`SalesOrderRespVO` 新增 `submitterUserName`、`formalSalesUserName`、`historyMissingFields`；列表亦返回缺失信息。缺失原因 `history_not_recorded` 与 `invalid_snapshot` 分开。第一版已有标签/客资快照继续读取，旧订单主表的独立交易字段保留；不读取当前资料伪造历史，不批量回填旧记录。无关联客资的复购不标记客资缺失。

历史导入订单的定向恢复例外（2026-09-29）：订单主表的可空 `imported_actor_snapshot` 保存经过核对的旧系统人员展示证据，独立于 BPM 审批轮次。已有轮次姓名优先；缺失姓名时才读取匹配租户、订单号和订单人员引用的导入证据，详情、列表及财务导出使用相同规则。不生成审批轮次，不修改人员归属、权限、状态、金额或旧轮次快照；无可靠来源的字段继续显示“历史未记录”。证据中的旧员工 ID 使用 `LEGACY_EMPLOYEE` 类型，仅用于展示溯源，不作为 ADMIN 身份或权限依据。

恢复工具默认仅接受旧订单已有的姓名快照（`legacy_order_snapshot`）。使用旧备份员工档案姓名必须另行明确确认，并以 `legacy_backup_profile` 记录姓名来源及备份时点；这类姓名不是提交时姓名快照，不可在后续处理中改称提交时姓名。所有导入证据保留源文件 SHA-256、旧订单/员工引用和备份时点，不动态关联当前用户名称。操作范围、校验及恢复步骤见[历史订单人员恢复](../operations/order-actor-history-recovery.md)。

补正未改变的字典选择和产品保留旧快照。历史字典名称缺失时，两端清空该选择并要求重新选择，提交 `refreshedDictionaryFields`（仅允许 studentNature/servicePeriod/studentSource/feeMode/paymentMethod），服务端再次校验当前字典并生成本次选择快照。未显式重选的缺失历史返回 `SALES_ORDER_DICTIONARY_RESELECTION_REQUIRED`；当前字典名称不可用返回 `SALES_ORDER_DICTIONARY_LABEL_UNAVAILABLE`。前端提交名称不作为权威来源。后继订单记录本次提交人，旧订单不变。

BPM 在通过/驳回动作内写入任务局部变量 `bpm_action_actor_events` 与终态动作的 `bpm_action_actor_snapshot`，记录主体、ID、动作时姓名、动作与时间。委派回应仅追加动作事件，不覆盖最终审核人；自动执行标识 SYSTEM，不借用受理人身份。公共任务/节点 DTO 追加 `actionUserId`、`actionUserNameSnapshot`；旧 reviewer 字段保留兼容，订单使用新增历史字段。主管确认申请/指派姓名分别写入 V272 的可空列，实际审核姓名从 BPM 历史读取，取消不伪造审核人。

部署：保留已应用迁移，按现有版本顺序执行 `V272__order_actor_name_snapshots.sql` 后部署服务；不重启或发布现有服务作为本地代码交付的一部分。V272 不更新旧业务行或权限，重复执行不覆盖新姓名；回退应用不得删除新历史字段。专项验证：`python script/sql/mysql/tools/verify_order_snapshot_upgrade.py`，隔离 MySQL 中验证原表升级、版本记录、重复执行、跨租户数据保留及中文 HEX。全链初始化与真实登录验收需另外记录运行结果。


本次本地验证边界：V272 专项升级/重复性/UTF-8 测试通过；完整 fresh 检查被已有 `zsjos_media_account_delete_request` 目标 schema 缺失阻断，直接运行既有 bootstrap 亦在 student-delivery-defer 建表附近报语法错误。本次不修改这些无关基线问题。尚未对部署数据库执行 V272、重启后端或完成真实登录端到端验收。发布前必须修复基线问题、通过完整初始化链并按顺序升级目标数据库，不能将专项通过视为发布验收通过。只读历史缺失清单见 `script/sql/mysql/verify-order-history.sql`。

## 驳回补正的收款契约（2026-09-22）

订单详情新增 `collectionMode: offline_paid|online_link`、`paymentStatus` 和 `transactionLocked: boolean`。无法核实收款链路时不返回推断值；前端必须禁止重提并提示刷新或核实。线上重提须确认 `paymentStatus=paid`，锁定课程、SKU、金额和明细增删；线下允许记录纠错。交易字段限制不是菜单权限，后端独立执行校验。

重提的 `purchaseIntentId` 可省略，由服务端继承原订单；显式传入不同值返回 `1900017026`。线下更正金额仍须付款凭证支持并重新通过教务、财务完整审批，不能替代补退款；线上修改交易内容返回 `1900017027`，到账金额不符返回 `1900017028`。首次提交明细不符返回 `1900017029`。原订单和审批快照保持历史事实，新订单承载纠正结果。

Workbench 按服务端状态决定商品编辑限制；Vue 管理端沿用现有资料/金额补正表单，线上金额禁用。两个客户端都禁止未知收款状态提交。先发布后端再发布前端；不需要数据库结构迁移或重置权限。发布前只读审计线上接续链路与支付分配，异常数据须逐笔追溯，不能直接覆盖原支付事实。


## 订单终止通知补全（2026-09-22）

主动终止成功后与 BPM 非通过/非驳回结束回调均发布 `zsjos.sales_order.cancelled`，按 `sales-order-cancelled:{roundId}` 幂等。发布与订单状态写入共用事务，重复回调不重复发布。`result_actors` 为成交负责人和本轮录单人去重集合，历史 `submitter` 规则仍可解析订单提交人。V056 已停用的站内信和企微规则不自动启用；未修改审批状态机、BPM 取消语义或补正任务条件。通知协议及投递配置见[业务通知契约](system-business-notifications.md#销售事件与企微对齐2026-09-22)。


### 零金额线下录入（2026-09-28）

线下 `offline_paid` 允许订单合计为 `0.00`，包括单课程或多课程全部为零；每条明细必须非负、金额非空且最多两位小数。保存购买草稿、恢复草稿与正式提交使用同一边界。线上 `online_link` 合计必须大于零，但允许部分课程为零；零金额线上草稿和支付链接返回稳定错误 `1900017030`，提示使用线下已支付。明细与总额不一致、缺失、负数或非法精度仍拒绝，不通过四舍五入修正。

零金额订单仍需付款时间、支付方式、1–6 份缴费凭证及原有必填资料，仍进入教务、财务双审批；不自动生效、不创建渠道支付流水或线上支付分配。Workbench 首购、客资复购、学员复购、外部客户复购共用 `SalesOrderEntryModal`，新录单先保存草稿，再带 `purchaseIntentId` 提交，保留版本及幂等控制。线下补正可改为零金额并重新审批，原订单和审批快照保留；线上未到账禁止提交，已到账补正继续受原交易快照限制。

Vue Admin 的 `ExternalRepurchaseDialog.vue` 直接调用外部复购订单接口，`mySalesOrder/index.vue` 调用订单补正接口；当前 Admin 未调用购买草稿接口，沿用原有非负金额和线上交易锁定约束，无须新增草稿页面。建议先发布后端再发布 Workbench；本次不需要数据库迁移或历史数据修复。

## Workbench 我的订单请求（2026-09-29）

列表由单一加载 effect 驱动：桌面翻页只请求当前订单页，不重复加载状态计数；移动端保留 cursor 加载。状态计数在进入页面及显式刷新/操作完成后更新。刷新仍同时更新列表与计数，旧列表响应按请求代次丢弃，离开页面也使旧请求失效。接口、数据范围、订单详情和审批契约不变。


## 销售与教务交叉复购（2026-09-30）

本节按已确认业务决定补充既有复购契约。历史客户复购页面现在是统一身份入口：可先输入姓名及一项联系方式核验；填写完整订单前须补齐手机号和微信号并重新校验。销售和教务可以为彼此录入或负责的客户录入一笔真实的新购买，不以原录入人、原负责人或本人服务关系作为此入口的限制。

- 只读预检：POST /zsjos/sales-order/repurchase/check-customer。请求 customerName、customerMobile、customerWechatId；返回 NO_MATCH、EXISTING_CUSTOMER、MULTIPLE_MATCH、IDENTITY_CONFLICT 或 REPURCHASE_BLOCKED，以及 canRepurchase、reason。唯一准确命中且允许录单时返回内部关联 personId、客户姓名与脱敏手机号；不返回其他负责人的客户详情、联系方式、订单金额或客户列表。
- 统一提交：POST /zsjos/sales-order/repurchase，沿用复购订单请求，增加可选 expectedPersonId 作为预检目标一致性检查。该 ID 不是授权凭据；服务端重新按准确联系方式查找，锁定客户并核对姓名及全部已填写的既有非空联系方式；至少一项必须匹配已有值，另一空项允许提出补充值并校验占用。多命中或不一致拒绝，不能由前端选择冲突客户。
- 两个接口沿用 zsjos:sales-order:create，并校验当前有效内部员工；不新增菜单权限、不自动授权。不具备该权限的教务仍不能使用统一录单。既有仅 student-repurchase 权限的本人学员入口保持原边界，由管理员配置是否开放通用复购入口。本次不扩大客户列表、客资详情或学员详情读取权限。
- 按 2026-10-08 确认的新规则，历史客户复购统一入口允许已有主客资但没有任何订单或尚无生效订单的客户录入，预检、购买草稿和最终提交不再要求先完成首购；已有待处理复购仍拒绝。此例外不改变客资详情及本人学员复购接口的原有前置条件。完全无记录时通过既有联系方式占用机制建立客户主档（React 保存购买草稿时，或未使用草稿的最终提交时）；无主客资客户继续复用。预检不写客户、客资、订单或联系方式占用。
- 正式提交通过独立 Bean 的事务及 repurchase-customer 对象权限边界调用原订单创建能力。新订单仅关联 personId，leadId/opportunityId 均为空；提交人和正式成交归属均为当前录单人。身份使用已有人员资格判定：符合销售资格为 sales，其他获准有效内部员工沿用教务身份及 student_delivery 提交中心。原客资归属、原商机、首次成交、旧订单和学员服务关系均不改写。
- 原 external-repurchase API 仍严格用于系统外客户，原 lead/student 路径保持其对象上下文；统一页面改用新接口，不能直接删掉旧接口的拒绝条件。跨归属录单通过准确身份入口完成，不凭任意客户 ID 扩大旧入口权限。
- React 支付草稿采用技术类型 customer_repurchase，保存已核验身份快照；草稿读写再次校验，拒绝绑定其他客户或 Lead/商机。订单提交必须引用当前录单人的同类型草稿。失败重试复用同一购买草稿、凭证和订单幂等键，防止首次响应丢失后新建草稿导致指纹冲突。
- 客户行锁下先检查幂等重放，再检查活动复购和重复缴费。相同客户的待处理/生效订单复用同一凭证文件，或付款时间、完整课程规格与金额全部一致，拒绝重复录入。仅课程相同不拦截不同时间的真实再次购买。现有线上支付绑定与分配校验继续生效；不新增合同号或支付凭证字段，不用图片相似性推测同一笔款。
- 已识别客户的订单学员姓名、联系方式绑定本次核验结果；需要更正时返回身份步骤重新校验。React/Vue 修改身份立即失效旧结果，异步旧响应不能恢复提交资格；网络错误可重试。填写订单前显示原客资与历史订单不会变更及本次订单归属当前录单人的提示。
- 客户、新订单、明细、审批和付款关联沿用同一事务，失败回滚；通知复用提交后发布。不产生新客资、首跟、资格判定或首购商机，不继承原兼职返现。不改首购转化、原订单业绩或首次成交时间；复购计入本次录单人的订单业绩。

### 录单联系方式完整性与空项补齐（2026-10-10）

- 首次录单、各复购接口与驳回补正统一要求 `studentMobile`、`studentWechatId` 非空，去除首尾空白，沿用手机号格式和 32/64 字符长度限制。购买草稿仍允许缺项，不批量修改历史订单。
- Workbench 和 Admin 正式表单分别校验两项。初始化按字段优先采用非空草稿、当前订单、已授权客资/学员资料；补正可使用订单已返回的客资快照补齐初始表单，不额外读取其他负责人的客资。草稿空值不清空已有回填，初始化完成后不覆盖用户编辑。
- 历史客户预检仍可使用单项查询，但进入完整表单前须提供两项并重新核验。姓名及至少一项既有联系方式必须匹配；已存非空项不允许修改，空项可以提出未被其他客户占用的补充值。预检及已有客户的草稿保存不补写主档，返回值继续脱敏。
- 订单提交在现有事务中补齐 Person 空联系方式，并仅补齐本次明确授权关联的 Lead 空项。首购/补正使用订单 Lead；客资入口复购使用该命令已授权的 Lead 上下文，但复购订单依然不关联 Lead/商机，不改变返现语义；其他复购不得按 Person 反查和修改其他 Lead。
- `OrderContactCompletionService` 要求加入调用者事务，锁定并重新读取目标资料，复用 `PersonIdentityWriteService` 的联系方式占用机制。补齐会造成主档与客资冲突或占用冲突时拒绝，订单/审批失败全部回滚；已有非空值不覆盖，幂等重放不再执行补齐。
- 补齐是本次已授权录单命令的受限副作用，不开放独立资料编辑或新增角色授权。审计事件 `sales_order_contacts_completed` 只记录客户、订单、操作人和变化字段，不记录完整手机号/微信号。客资归属、商机、历史订单快照及服务关系保持原语义。
- 已锁定支付的旧草稿允许补齐原本为空的联系方式；已存身份、联系方式、课程及金额保持锁定，正式提交仍要求两项完整。部署需协调前后端版本；旧前端的缺项提交将被后端明确拒绝。

### 验证入口

后端定向测试包括 RepurchaseCustomerServiceTest、RepurchaseSubmissionServiceTest、RepurchaseSubmissionTransactionTest、RepurchaseDuplicateGuardTest、SalesOrderServiceImplTest、CustomerRepurchaseDraftTest、PurchaseIntentDraftTest。浏览器测试在 Workbench 5195、Admin 5196 的任务专属 Vite 服务运行后执行 python frontend/workbench/test/repurchase-customer-browser.py；夹具仅替换传输层，不写真实业务数据库。生产验收仍需已部署的后端、字典/商品/审批配置及对应用户实际权限。

## 订单管理全局排序

管理分页和卡片游标支持可选 `sortField` / `sortOrder`，普通及高级筛选一致。历史金额、人员、标签按原轮次快照投影排序；详细字段和兼容行为见 [业务列表排序](business-list-sorting.md)。其他订单查询和导出保持原契约。

## 礼品与历史商品快照兼容（2026-10-08）

提交和补正的 `giftItems` 仍为编码数组，例如 `["gift-code"]`。省略、`null`、`[]` 均表示无礼品；清空选择显式发送 `[]`。JSON 字符串不能替代数组，类型或格式错误返回参数错误，不回显原始请求值。

Workbench 共用订单录入表单（首购、复购和补正）选择礼品后，“礼品邮寄地址”显示必填星号；空地址或纯空白地址在字段处提示“选择礼品后请填写邮寄地址”。清空全部礼品后取消必填，既有提交校验及服务端地址校验保持生效。

订单列表、详情新增 `giftItemCodes: string[]`、`giftItemSnapshots: {code,name?,path?,snapshotAt?}[]` 和 `giftItemsInvalid: boolean`；原 `giftItems` 字符串兼容保留。礼品采购响应保留 `giftItemsJson` 并新增 `giftItemSnapshots`、`giftItemsInvalid`。损坏历史返回无效标志，编码/快照不可作为可提交的空选择；Workbench 和 Admin 均阻止补正并提示核对，Admin 礼品采购展示“历史礼品信息无法读取”。

统一读取对象数组 JSON 与旧版“JSON 快照字符串数组”；新写入为对象数组 JSON，数据库列不变。历史缺失名称、路径、时间不从当前目录补造，显示名称未记录。Workbench 以编码回填、快照展示，并核对两者一致；旧服务响应可从兼容字段提取编码。Admin 礼品采购使用相同历史语义，不展示原始 JSON。

服务端补正依据所属订单原轮次快照：保留编码复用原快照，即使礼品已改名、停用、删除；新增编码必须是当前启用的叶子礼品，并生成新快照。删除选择仅影响新轮次；客户端不能提供可信快照。历史损坏即使提交空选择也拒绝，须先进行有来源的数据核对。错误码 `1900006083` 为历史礼品快照无效，`1900006084` 为新增礼品选择无效，`1900006085` 为礼品收货地址缺失或无效。

商品历史读取统一通过 `LeadProductSnapshot.readHistorical`：`spuUnknown/skuUnknown` 缺失或显式 `null` 归一为 `false`，布尔值保持原义，其他类型及损坏 JSON 拒绝。兼容不补造商品名称、规格、价格，也不查询当前目录替换历史。金额保持 BigDecimal 精度和小数位；展示入口沿用降级策略，补正入口沿用无效快照拒绝语义。

先发布兼容性新增字段的后端，再发布两个前端消费者。旧字段本次保留，不迁移或回写历史数据。回滚前用对象数组和旧字符串数组样本验证目标包的读取能力；能够读取新格式不等于修复后的补正行为仍成立。验收证据与待验项见 [运行问题修复记录](../operations/runtime-issues-20261008.md)。


## 异常日志补充修复（2026-10-14，本地交付）

Admin 订单补正显示可编辑的礼品及邮寄地址。未修改的选择以编码数组回传，名称沿用历史快照；删除、停用或改名的原礼品仍可保留。新增礼品只来自既有 `/zsjos/gift-config/list?status=0` 返回的叶子项，不扩大目录权限。目录失败或无权访问时显示提示与重试，仅允许保留或移除可读取的原礼品；损坏历史阻止整个补正。选中礼品必须填写不超过 1000 字符的地址，显式清空发送 `[]`。这些修改不回写原订单或审批快照。

订单审批所有查询分支在进入 BPM 前排除 null、空白流程实例 ID 并去重；关键词 SQL 同时排除无流程的历史轮次。公共 BPM 待办/已办 API 区分 null（未指定该筛选）与空集合（无匹配结果），全无效集合直接返回空，不触发无筛选查询。流程实例 ID 是不透明值，有效 ID 不进行 trim 改写。此边界不改变任务所有权、中心权限和历史无流程的存储语义。

回归入口：`BpmTaskProcessFilterTest`、`SalesOrderServiceImplTest`，Admin `node --test test/order-revision-gifts.test.mjs` 与 `test/order-revision-gifts-browser.py`。浏览器使用实际补正页面及隔离请求夹具；本地验证不是部署或真实账号验收。

## 兼职归属新媒体员工快照（2026-10-13）

新订单提交（首购、复购及修订产生的后继订单）在审批轮次 `order_snapshot.leadProfile.partnerOwnerSnapshot` 保存提交时的兼职归属。仅 `sourceType=partner` 采集，以 `partnerId` 查询 `PartnerOwnershipService` 当前关系；保存 ADMIN 员工 `employeeUserId` 和 System 当前姓名 `employeeName`。账号已不存在时保留归属记录中的姓名快照，不用编号或其他身份推断员工。现有 Lead 归属、来源和业绩口径不变。

对象缺失或 null 表示历史未记录；存在但 employeeUserId 为空表示提交时未分配；有员工 ID 但无姓名时展示姓名未记录。历史订单不补数据，读取不查询当前关系。兼职后续转归属、员工改名不改变原订单；新后继订单重新采集，原审批轮次不重写。

详情通过 `leadProfile.partnerOwnerSnapshot` 返回，列表通过 `partnerOwnerSnapshot` 返回同一快照，并补充 `leadSourceType` 供客户端按来源判断。Workbench 列表卡片、订单表格、详情及 Admin 既有订单表格和详情显示“兼职归属新媒体员工”；非兼职来源列表显示“—”，详情隐藏。仅复用既有 JSON 存储，无数据库列、迁移、回填或权限变更。


## 客户身份归一与复购联系方式（2026-10-29，本地开发）

本地实际应用补充：已确认125组客户归一后，历史客户统一复购与客资详情复购均通过真实HTTP创建订单、启动审批和幂等重放测试，零金额测试订单随后通过正式终止接口结束。旧学员复购链接先解析已归档Person的归一映射，再调用原服务对象权限边界；映射本身不授予权限。详见[本地实际应用记录](../operations/person-lead-unification.md)。

后续本地补充：经归一工具核实保留的历史联系方式存入 `zsjos_person_contact_alias`，记录原客户及 mobile/wechat 技术字段。预检与提交均可按对应字段识别这些别名；不能只因占用表存在记录就绕过身份匹配。既有主档联系方式、姓名校验、租户和录单权限继续生效，不向用户返回别名全集。别名表及配套后端必须先完成升级再提交归一数据。

未携带档案确认选择的兼容请求：复购沿用已授权 Person 关联和订单联系方式快照，不再通过 OrderContactCompletionService 强制补写 Person/Lead；首购和其他既有非复购路径保持原校验。此规则覆盖前述复购空项补齐的自动副作用，不改变两项联系方式必填、唯一客户核验、重复缴费和待处理复购检查。2026-11-01 起，双端现有录单入口采用下述显式确认协议。

## 录单档案差异确认（2026-11-01）

- Workbench 首购、客资复购、学员复购、历史客户复购与补正，以及 Admin 既有补正/历史客户复购入口，在表单校验后读取当前客户主档，并打开独立“确认提交订单”弹窗。替代原提交确认，不连续追加确认。返回修改保留表单，读取失败不开放确认提交，可再次点击提交重试。
- 已有值不同的姓名、手机号、微信号默认勾选更新，可逐项取消或保留全部已有值。空项在成功提交时自动补齐，界面单独说明；新客户使用本次资料建档。订单始终保存本次填写的学员信息。保存草稿不执行本次档案覆盖选择（原有新客户建档流程保留）。
- 当前主档读取：`GET /zsjos/sales-order/profile/lead/{id}`、`/order/{id}`、`/student/{id}`，分别复用 `lead/enter-deal`、`sales-order/revise`、`student/repurchase` 对象授权；功能权限沿用对应录单命令。学员入口保留归一映射。`POST /zsjos/sales-order/profile/customer` 使用既有准确身份预检，不接受任意 personId 作为授权。该入口未由用户准确提供的非空联系方式只返回“已登记（已隐藏）”，不扩大历史客户资料可见范围。接口关闭请求/响应访问日志。
- 响应为 `{ personId?, name?, mobile?, wechatId?, fingerprint }`。fingerprint 绑定租户、目标档案及三项当前值，是并发校验依据，不是授权凭据；新客户返回 `fingerprint="new"`。
- 订单提交 VO 新增可选 `profileUpdate: { fingerprint, fields: ("name"|"mobile"|"wechatId")[] }`。`fields=[]` 表示保留非空值，仍自动补空。服务端在既有订单事务内加锁重读 Person，验证 fingerprint，使用 `PersonIdentityWriteService` 更新所选字段并复用联系方式占用校验。档案变更返回 `1900006086`，要求重新核对；联系方式冲突沿用原查重错误，不可通过勾选绕过。任何后续审批启动失败会回滚档案、订单和本次审计。
- 新确认路径仅更新客户主档 Person，不改写 Lead 原始提交信息、历史订单、已启动流程快照或课程服务姓名快照。历史客户统一入口的身份匹配和支付锁定规则保持原约束，不能用本功能绕过身份步骤更换客户。
- 审计事件 `sales_order_profile_updated` 记录操作人、来源订单、时间、变化字段；前后身份值通过框架 `EncryptTypeHandler` 加密后存入 `relatedObjectRefs.encryptedIdentityChange`，不写入明文日志。失败时不保留成功事件。沿用现有加密配置，无新表、SQL、角色授权或依赖。
- 未携带 `profileUpdate` 的旧调用保持原有补空/复购不回写行为，绝不隐式覆盖。订单幂等重放返回原订单，不重复执行档案更新；复购请求指纹不包含会随档案变化的确认对象，其他订单字段仍参与原指纹校验。

客户归一不扩大客资订单历史权限。V355 增加 Lead/Order 的 identity_origin_person_id；修复前必须固化原身份范围。空值表示当前 Person。原范围内保留既有 Lead 对象授权，跨原范围还需既有订单读取权限；列表和详情使用同一判断。复购继续只关联 Person，不新增 Lead/商机关联。尚未执行存量归一，生产不可直接运行当前开发脚本。


## 订单资料收集（2026-11-01）

首购、复购和接续重提进入审核时，在原订单事务内按每条商品明细创建独立收集任务，不等待审核通过。审核失败回滚沿用原事务边界；幂等重试不重复建任务。履约只消费收集结果，详见[订单报名资料收集任务](order-student-information-tasks.md)。订单终止关闭内部收集办理及获取链接，已经发出的公开链接仍按原有效期保存/提交，不恢复订单状态。

### 自拓来源人员展示（2026-11-01）

新订单的 `leadProfile.selfSourcedWithoutProvider` 在生成订单快照时记录：仅客资明确记录提供人选择、属于销售/教务自拓且未关联提供人时为 true；提供人选择未记录时为 null。列表投影为 `leadSelfSourcedWithoutProvider`，来自同一历史快照。

Workbench 详情和列表、Admin 历史信息在该标记为 true 且来源类型匹配时显示“销售本人自拓（未关联新媒体）”或“教务本人自拓（未关联新媒体）”；其他情况显示快照姓名，缺失时显示“历史未记录”。姓名字段不写入业务状态文案。

兼容边界：旧订单快照缺少此标记时不根据空姓名推断，也不查询当前客资回填历史状态。本次不修改已持久化的旧订单、审批快照或数据库；历史订单修复需要另行核验当时的来源证据。

2026-11-03 经单独授权，对测试环境租户 1 的 252 笔旧订单完成有来源证据的标记回填；
运行时仍不推断其他旧单。范围、排除项、私有备份及验证见
[订单自拓来源展示回填](../operations/order-self-source-backfill-20261103.md)。

### 撤回修改与终止订单（2026-11-02 用户确认）
- `PUT /zsjos/sales-order/{id}/terminate` 增加可选布尔字段 `withdrawForRevision`；省略或 false 保持最终终止，true 表示撤回修改。两种操作沿用现有 `zsjos:sales-order:create` 配置权限及原创建人/正式负责人对象校验。
- 撤回仅允许待审核订单，结束当前 BPM 和审批轮次、取消关联履约及返现，订单进入 `revision_required`；原因保存在结束轮次及订单中。用户主动撤回不伪造审批驳回，也不创建审批驳回补正任务。
- 待重提可修改并提交 successor Order，也可彻底终止；`terminated` 禁止重提。新订单重新启动审批，旧订单与轮次保留。
- 两端分别提供“撤回修改”和“终止订单”，确认框说明不同结果。幂等命令区分 withdraw/terminate；同一幂等键不可切换意图，版本/归属检查与事务沿用原逻辑。BPM 授权类型仍为 `zsjos.sales-order.terminate`（结束审批的既有授权），业务命令保存不同意图。

## 2026-11-03 邮寄地址调整

报名收集表新增可选敏感字段 `textbook_shipping_address`（教材邮寄地址），与礼品、证书地址并列；沿用配置升级与发布流程，已发出的表单快照不追溯修改。销售、教务录单及补正不再填写礼品地址，选择礼品后提示“邮寄地址自动读取报名收集表信息，不再需要填写”。订单审批流程最终通过、订单生效时，仅为有礼品且尚无采购记录的订单生成采购单。生成时按订单 personId 读取最新已提交档案（提交时间、ID 倒序）的最新修订礼品地址，写入采购地址快照；当时未填写则为空，不回退订单或旧档案。列表、详情、导出直接读取采购单已保存的地址，历史快照保留，后续档案变更不自动改写采购地址。收集任务在提交订单审批时创建；若审批通过后才填写地址，不自动回填已有采购空值，需通过采购编辑维护。独立手工采购保留原有地址录入。未批量修改历史数据。

### 客资提交时间兼容（2026-11-05）

订单详情 `leadProfile.submittedAt` 优先使用审批轮次快照中的客资提交时间（提交订单时保存的 Lead.createTime）。旧快照有 leadProfile 但缺少该时间时，仅按该订单关联 leadId 读取原客资 createTime 补充响应；这是用户确认的历史投影兼容例外，不重建姓名、来源、字典等可变资料，不更新数据库或历史快照。关联客资已删除或时间缺失时保留空值，工作台显示“历史未记录”；无客资档案快照时不伪造档案。订单自身 submittedAt 含义不变。

2026-11-06 用户另行要求持久化回填：测试库租户 1 的既有客户档案快照，按原关联客资 `create_time` 补齐缺失的 `leadProfile.submittedAt`。仅限已核对的关联与缺失字段，保留已有时间及其他历史资料；该一次性修复不改变上述只读接口行为。范围、备份和验证见[订单客资提交时间回填](../operations/order-lead-time-backfill-20261106.md)。


## 客户录单资料关联客资（2026-11-05）

首购保持原逻辑。客户录单和复购按已确认 personId + 当前租户匹配未删除客资（包含已关闭的历史客资，不按录单人的旧客资可见范围截断）。唯一候选自动带入；多条候选必须选择；无候选保持原流程。明确从客资详情发起的复购固定关联该客资，不展示重新选择；请求指定另一条客资时返回关联变化错误，并校验客户关系。无“取消自动关联”或“均不适用”选项。

候选只读接口：`POST /zsjos/sales-order/repurchase/lead-candidates` 接收与客户核验相同的姓名/手机号/微信字段，累计录单 feature 权限、内部人员资格及精确身份核验；`GET /zsjos/sales-order/lead/{leadId}/repurchase/lead-candidates` 累计录单权限及原客资 enter-deal 对象权限；`GET /zsjos/sales-order/student/{personId}/repurchase/lead-candidates` 累计学员复购权限及本人负责学员关系。返回技术 id、状态及 statusLabel、完整 profile。前端展示业务编号 leadNo，不展示技术 id。

用户明确批准的读取例外：以上核验后，录单弹窗允许读取该客户全部候选档案，即使原本不具备该客资普通页面访问权。该例外不授予任意客户检索、客资详情路由访问或修改权限。业务客户选择与身份错误仍阻止读取，不能降级成空候选。

提交请求 order 增加可选 associatedLeadId。后端在创建事务内重新查询相同客户候选；多候选未选返回 1900006110，选择已失效/其他客户客资返回 1900006111。唯一候选可由后端自动确定；提交幂等指纹包含显式选择，重放成功结果不会重新匹配。复购订单成交归属为录单人，关联不继承旧销售/提交人或兼职返现，不改变客资状态。

资料关联采用提交事务中候选查询时的关系与档案快照，不为展示关联额外获取 Lead 行锁，避免与客资入口既有 Lead → Person 锁顺序形成循环等待。查询之后发生的客资修改或删除不重写该历史快照。

关联 id 和录单时完整客户档案只保存在既有审批轮次 JSON 快照，不需要 SQL 迁移。详情返回 associatedLeadId 与 leadProfile；补正重提复制原关联及档案快照，不重新猜测历史关联。原订单交易 leadId/opportunityId 仍为空。历史订单不批量回填。

React 工作台和 Vue 管理端现有客户录单均已适配候选弹窗。完整档案包括姓名、业务编号、联系方式、提交时间、成交归属身份、业务来源/派单方式、来源人员、兼职归属、负责人、分类、渠道、地区，另显示查询时的客资当前状态。成交归属身份按当前录单人确定，不从候选负责人推导；状态不回写订单历史快照。

### 2026-11-05 审查修复

管理端保存本次已确认的完整提交请求。传输失败且输入未变时，重试直接重放原请求和幂等键，避免成功订单被候选前置检查拦截；修改输入后重新核验并生成新的提交意图，明确业务错误允许重新核验。候选档案的用户、兼职和归属使用批量读取，保留完整历史候选及员工姓名快照回退。历史 converted 客资按既有有效状态语义展示“有效”。

历史客户复购要求填写姓名、手机号、微信号，校验客户成功后立即展示候选卡牌，唯一候选自动带入、多候选选定后才能填写订单，无候选直接继续。身份信息变化清除校验和选择。提交确认弹窗核对档案差异和更新选项，沿用校验阶段的关联选择，后端提交时再次校验。两端均使用该交互。

候选客资按卡牌展示，整卡可选。选中卡牌放大上浮并显示主题蓝色光边和“已选中”文本，两端均支持单选及键盘操作，窄屏纵向排列。卡牌保留全部客户档案字段。

历史客户复购页面采用横排身份输入，核验成功后显示客户摘要，可修改后重新核验。卡牌默认直接展示全部客户档案字段及当前状态，不增加展开/收起操作。候选区使用可用宽度，继续录单操作位于底部粘性操作栏。
