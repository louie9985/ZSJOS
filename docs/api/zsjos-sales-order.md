# ZSJOS 成交订单 API

## 权限与对象范围

- `zsjos:sales-order:create`：当前归属销售或两类公海的授权协同销售录入成交订单。
- `zsjos:sales-order:query-own`：分页和读取当前用户作为实际提交人的历史订单。详情仍执行 `read-own` 对象校验，不随客资负责人变化。
- `zsjos:sales-order:query`：读取具备对象关系的订单详情。
- `zsjos:sales-order:review`：工作台成交审批菜单与直接详情查询权限。审批池、通过和驳回接口的业务授权以配置部门成员关系和本人 BPM 任务为准，不按角色名称推断。
- `zsjos:sales-order:supervisor-confirm`：统一“成交订单审批”入口中的主管待办/已办列表及确认决定权限。V076 为启用的稳定 `sales_manager` 角色补齐该权限；主管命令还要求当前用户是申请时固化的订单销售直属主管、持有本人 BPM 加签任务并通过订单对象校验。

## 接口

- `GET /zsjos/sales-order/product/catalog`：全部启用产品/SKU 目录。
- `POST /zsjos/sales-order/lead/{leadId}/submit`：创建首购订单。服务端要求客户、主客资和商机属于同一客户，且客户尚无生效订单。
- `POST /zsjos/sales-order/lead/{leadId}/repurchase`：从系统客户客资详情创建复购；客资只用于发起与权限上下文，订单持久化仅关联客户。
- `POST /zsjos/sales-order/external-repurchase`：录入系统外历史客户复购。唯一命中无主客资客户时复用客户，无命中时创建客户主档；多客户命中、身份冲突或已有主客资时拒绝。
- `GET /zsjos/sales-order/lead/{leadId}/customer-orders`：要求 `zsjos:lead-detail:order-read`，并以统一 Lead 详情对象关系按客户聚合首购与复购订单。
- `GET /zsjos/sales-order/lead/{leadId}/customer-orders/{orderId}`：同样累计标签 feature 权限、Lead 对象关系和订单与客资 `personId` 一致性校验。
- `PUT /zsjos/sales-order/{id}/resubmit`：对允许重提的 `revision_required` 或 `terminated` 订单创建全新订单并返回新订单 ID。旧订单进入 `superseded` 终态，使用 `supersedes_order_id` / `superseded_by_order_id` 双向关联；旧审批轮次、明细、凭证和驳回原因保持不变，新订单从第 1 个审批轮次独立启动。若同一客资已存在另一张活动首购订单，或同一客户已存在另一张活动复购订单，则拒绝重提；复购创建与重提通过客户主档行锁串行化。
- `GET /zsjos/sales-order/{id}`：订单、课程、凭证和当前审批轮次详情。新建或重提审批轮次会在既有 `order_snapshot` 中保存录单时订单字典显示值（性质、服务期限、学员来源、收费方式、支付方式）以及关联客资的档案字段和来源/分类/渠道显示值；详情优先使用该轮次快照，因此字典或客资后续变更、停用不会让已录订单显示为“标签未配置”。历史轮次没有这些快照字段时，兼容回退到当前可读投影。首购订单关联的当前客资仍存在时，`leadProfile` 返回客资业务编号 `leadNo`、客户联系方式、来源、提交人、所属销售、分类、渠道、派单方式和地区，用于审批详情展示；无关联客资的复购订单不返回该字段，且内部 `leadId` 不作为客资编号回退。`registrationApproval` 与 `financeApproval` 分别返回报名履约、财务节点的 `pending/approved/rejected/cancelled` 汇总状态、实际审核人用户 ID/姓名及节点时间。审核身份和结果只读自 BPM 当前任务和历史任务，不在订单域重复持久化；界面展示审核人姓名、结果和审核时间，不展示用户 ID。
- `GET /zsjos/sales-order/my-page`：本人提交订单的轻量分页，支持 `status` 和订单号/学员姓名/手机号 `keyword`。
- `POST /zsjos/sales-order/my-search-page`：在本人订单固定范围内组合关键词与高级条件树；高级条件非空时忽略可选状态分组。
- `GET /zsjos/sales-order/my-status-counts`：本人订单的全部、待审核、已驳回待修改、已通过数量。
- `GET /zsjos/sales-order/my/{id}`：本人订单完整详情；已驳回订单包含最新轮次 `decisionReason` 和 `canRevise`。
- `GET /zsjos/sales-order/approval/filter-profile`：返回当前租户已发布的待处理/已处理方案，以及当前用户按审批配置部门解析出的 `centers`。单中心用户只返回本中心；同时落入两个配置部门范围的用户返回报名履约和财务两个中心。
- `GET /zsjos/sales-order/approval/task-target?taskId=`：校验当前用户对 BPM 普通任务或主管加签任务的关系后，返回 `workType`、`orderId`、`taskId`、`taskDefinitionKey`、`center`、`confirmationId` 和处理状态，用于今日任务和消息的精确跳转。
- `GET /zsjos/sales-order/approval/notification-target?orderId=&sceneCode=&sourceEventKey=`：仅接受主管申请和主管决定两个通知场景，分别要求当前用户是指定主管或加签申请人；服务端使用消息既有的事件幂等键精确恢复确认记录和固化任务定位，并执行订单对象权限检查。兼容缺少事件键的旧消息时才回退该订单最新确认记录；前端不得提交用户 ID 或任意目标 URL。
- `GET /zsjos/sales-order/approval/inbox-page?center=registration|finance&groupKey=pending&optionKey=all&keyword=`：按当前用户审批任务、处理状态和中心分页查询轻量订单列表，支持订单号、学员姓名和手机号搜索；`handled` 仍可作为兼容参数。服务端将筛选条件与当前用户允许的 BPM 任务节点取交集，伪造无权中心返回权限错误，前端隐藏筛选项不是授权边界。
- `POST /zsjos/sales-order/approval/search-page`：在当前用户 BPM 任务和中心权限固定范围内组合关键词与高级条件；高级条件非空时忽略可选处理分组，但不扩大允许的任务节点。
- `PUT /zsjos/sales-order/{id}/approve`、`/reject`：处理当前 BPM 任务，必须提交 `taskId`、当前 `approvalRoundId`、订单/轮次版本、审批意见和幂等键。订单行与轮次行锁保证审批、驳回和终止只有首个命令成功。
- `PUT /zsjos/sales-order/{id}/supervisor-confirmation/request`：当前报名履约或财务普通审批人申请订单销售主管审批。请求必须提交普通 `taskId`、轮次、订单/轮次版本、必填且不超过 1000 字的 `reason` 和幂等键。每轮最多一条申请，另一中心不能再次加签；BPM 创建并行加签任务，报名履约、财务和主管三方任务均保持可见可处理。申请加签后，该中心普通审批与对应主管审批都成为本轮通过条件：中心先通过时等待主管，主管先通过时等待中心；另一中心状态不受影响，任一方驳回则整轮驳回，三方全部通过后订单才通过。
- `GET /zsjos/sales-order/supervisor-confirmation/inbox-page`、`POST /zsjos/sales-order/supervisor-confirmation/search-page`：只查询当前用户作为指定主管的待办或已办，`handled=false|true` 区分状态，支持订单关键词和订单高级条件。
- `GET /zsjos/sales-order/supervisor-confirmation/inbox-cursor`、`POST /zsjos/sales-order/supervisor-confirmation/search-cursor`：与分页接口使用完全相同的主管、页签、关键词和高级条件；游标指纹绑定这些条件，条件变化后旧游标失效。
- `GET /zsjos/sales-order/supervisor-confirmation/{confirmationId}`：读取当前指定主管可见的主管确认记录，用于消息深链恢复主管视角。
- `PUT /zsjos/sales-order/{id}/supervisor-confirmation/confirm`、`/reject`：主管提交通过或驳回决定，必须携带 `confirmationId`、主管 BPM `taskId`、轮次、订单/轮次/确认记录版本、必填且不超过 1000 字的 `reason` 和幂等键。主管通过后，已先通过的中心任务完成汇合，尚未处理的中心任务继续等待普通审批；主管驳回使用 BPM 驳回整轮并退回销售补正。
- `PUT /zsjos/sales-order/{id}/terminate`：未生效订单的原创建人或正式负责人可填写原因终止当前 BPM 流程，包括已进入财务节点的订单。订单域先校验权限、状态、版本和幂等键，再通过 BPM 业务授权终止接口记录真实操作人、授权类型与原因。

## 快照边界

- 订单课程/产品、SKU、规格属性、类目路径、录单价格和实收金额已经由订单明细的 `productSnapshot` 固化，产品目录后续改名、停用或调价不改变历史订单。
- 每次新建或重提审批轮次都由该轮次的 `order_snapshot` 固化订单业务字段、凭证引用、订单字典显示值，以及当时关联客资的业务编号、联系方式、来源、提交人、所属销售、分类、渠道、派单方式和地区。审批详情优先展示本轮快照，业务字典或客资后续变化不回写历史轮次。
- 历史轮次缺少新增快照字段时只兼容读取当前可用投影，不批量猜测或回填历史显示值；快照损坏也不会阻断详情读取。内部 `leadId` 始终只用于技术关联，不作为客户可见的 `leadNo` 回退。
- BPM 流程实例、当前/历史任务、实际审核人、审批结果和节点时间属于审批运行事实，继续以 `yudao-module-bpm` 为准，不复制进订单业务快照。权限、菜单、人员启停状态和当前待办也不是业务快照。

审批、驳回和终止命令使用租户内唯一的命令账本。相同幂等键只有在订单、轮次、流程实例、命令类型、审批节点、BPM 任务、操作人和规范化请求内容全部一致时才返回首次结果；任一属性不同均返回幂等冲突。BPM 调用失败会回滚订单、轮次和命令账本。终止命令不要求 `taskId`，但仍绑定当前订单、轮次、创建人和 BPM 实例。

超期协同公海和主管人工公海的协同销售提交首购订单时，服务端按 Lead、协同记录、Opportunity 的顺序锁定，在订单写入前把 Lead 与 Opportunity 正式负责人转给提交销售、记录原负责人转派历史并换绑未完成跟进任务。订单 `formalSalesUserId` 固化新负责人，`submitterUserId` 保留实际操作人；后续驳回、取消或终止不回滚归属。正式负责人先提交时不转派，超期周期沿用 `deal_pending` 冻结。

外部复购和所有客户联系方式写入口共用 Person 身份写服务。手机号与微信号使用同一租户级占用空间，手机号命中微信号同样冲突；值仅去除首尾空格，微信号大小写敏感且最多 64 个字符。同一 Person 的手机号与微信号可填写相同值并只占用一次。并发创建相同联系方式时只允许创建一份客户主档，其他请求按已存在客户继续执行复购入口校验。

审批详情仅在当前中心节点仍为 `pending` 且当前待办仍提供 `taskId` 时显示通过、驳回操作。任一审核人处理该中心节点后，同中心其他用户不再显示操作按钮；服务端仍以 BPM 待办状态校验并拒绝过期或重复处理。
上线后创建的审批轮次返回 `supervisorConfirmationEnabled=true`。申请时服务端从订单 `formalSalesUserId` 对应销售的当前直属部门读取 `leaderUserId`；正式销售或部门负责人未配置、负责人停用、负责人就是正式销售或负责人缺少主管确认权限分别返回稳定错误。确认记录为 `pending/confirmed/rejected/cancelled`；申请中心通过不会取消主管记录，申请中心或另一中心驳回、销售终止或流程取消会把未完成记录置为 `cancelled`。历史确认记录继续使用申请时固化的 `supervisorUserId`；上线前已存在轮次保持 `false`，继续旧流程且禁止发起主管确认。
- `POST /zsjos/sales-order/voucher/upload`：上传 JPG、PNG、WebP 或 PDF，最多 10 MB；服务端按文件内容识别真实类型。所有订单最终提交必须引用 1–6 个当前销售上传的凭证，包括零元订单和系统外复购。工作台延迟上传，管理端系统外复购选择后上传；两端都保留上传中、失败、重试、删除和预览状态。

提交命令必须携带 `idempotencyKey`。同一次录单或补正意图的快速重复点击、凭证上传失败和请求重试必须复用该键，只有服务端确认成功后才轮换；首次录单在锁定客资行后会再次检查该键，使并发相同请求返回已创建订单，而不是重复创建或误报已有活动订单。订单总金额不由客户端提交，服务端以各课程 `actualAmount` 重新汇总。手机号和微信号至少一项；所有订单必须引用 1–6 份缴费凭证。省市名称由服务端根据省市级联编码生成快照；对于可直接选择且以 `cityCode=OTHER` 表示的省级区域，客户端 `cityName` 可以为空，最终快照以区域 API 解析结果为准。五类订单字典值由系统字典 API 校验。

## 状态

- `pending_approval`：当前 BPM 轮次处理中，同一客资禁止新建活动订单。
- `revision_required`：任一中心驳回或流程取消，原订单可补正重提，同一客资仍禁止新建活动订单。
- `effective`：两个中心均通过；关联 Opportunity 和 Lead 在同一事务进入 `won`。客户主档保留，订单继续以必填 `personId` 关联客户。
- `terminated`：创建人或正式负责人主动终止未生效订单；当前 BPM 轮次同时终止，可由有权人员发起独立新订单重提。旧订单保持终止审计事实，重提成功后旧订单转为 `superseded`，新订单沿用 Lead/Person/Opportunity 和订单类型但拥有独立明细、快照、返现、服务单和 BPM。首购恢复商机跟进状态，复购不修改客资或商机。
- `superseded`：旧订单已被独立 successor 订单接续，不可再次重提；详情提供新订单入口。新订单详情提供返回旧订单入口。

已成交客资是否显示复购入口只消费服务端 `ENTER_REPURCHASE` 动作。该动作同时校验创建权限、对象权限、未挂起、客户已有生效订单且不存在活动复购订单；前端不得再以 `status=won` 自行推断。

`orderType` 由服务端判定并在创建后不可修改：首购为 `first_purchase`，复购为 `repurchase`。首购持久化 `personId + leadId + opportunityId`；系统客户和系统外复购都只持久化 `personId`，固定 `leadId=null`、`opportunityId=null`。复购不通知原客资提交人、不继承原兼职返现，生效时只新增客户的一张有效订单，不覆盖客资、商机或首次成交时间；同一客户最多一张活动复购订单。

审批轮次保存最终非通过原因快照。补正重提创建新的空原因轮次，不覆盖上一轮审计记录。

明确业务驳回（报名履约、财务或主管驳回）会为本轮提交人创建无截止的 `sales_order_revision` 业务任务，幂等键为 `sales-order-revision:{approvalRoundId}`，动作码为 `OPEN_SALES_ORDER_REVISION`。正式负责人可代为补正；新订单及第 1 轮 BPM 启动成功后完成旧订单补正任务。BPM 异常取消或异常终止不创建补正任务，历史订单不回填任务，既有驳回通知不因任务创建重复发送。重提事务任一步骤失败时旧订单保持原状态。
Sales-order notifications use the order business number (`orderNo`) and do not render the student or buyer name. Internal employee names used for approval roles remain available where required by the notification scene.
Applied V085 environments use forward migration V087 to repair safely resolvable residual `order.studentName` snapshots, including logically deleted history, without changing V085 or the occupied V086 permission migration. Missing tenant-scoped order relations block the repair; an internal order or Lead ID is never substituted as the visible identifier.
- `POST /zsjos/sales-order/student/{personId}/repurchase`：需要独立权限 `zsjos:sales-order:student-repurchase`。学习规划师只能为本人已接收且状态为服务中、已暂停或已结业的学员录入复购；该权限不开放通用订单创建或外部历史客户复购。订单提交人和正式销售归属均为当前学习规划师；订单仅关联 `personId`，不继承原客资或兼职返现。幂等重放还必须匹配客户、提交人、提交中心、复购原因和完整订单请求指纹，跨中心或不同请求复用 key 返回冲突。
