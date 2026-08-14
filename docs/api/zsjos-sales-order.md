# ZSJOS 成交订单 API

## 权限与对象范围

- `zsjos:sales-order:create`：当前归属销售录入或补正本人客资的成交订单。
- `zsjos:sales-order:query-own`：分页和读取当前用户作为实际提交人的历史订单。详情仍执行 `read-own` 对象校验，不随客资负责人变化。
- `zsjos:sales-order:query`：读取具备对象关系的订单详情。
- `zsjos:sales-order:review`：工作台成交审批菜单与直接详情查询权限。审批池、通过和驳回接口的业务授权以配置部门成员关系和本人 BPM 任务为准，不按角色名称推断。
- `zsjos:sales-order:supervisor-confirm`：独立“主管确认”入口、待办/已办列表及确认决定权限。迁移不按角色名自动授权；管理员必须显式分配。主管命令还要求当前用户是申请时固化的直属部门负责人、持有本人 BPM 加签任务并通过订单对象校验。

## 接口

- `GET /zsjos/sales-order/product/catalog`：全部启用产品/SKU 目录。
- `POST /zsjos/sales-order/lead/{leadId}/submit`：创建首购订单。服务端要求客户、主客资和商机属于同一客户，且客户尚无生效订单。
- `POST /zsjos/sales-order/lead/{leadId}/repurchase`：从系统客户客资详情创建复购；客资只用于发起与权限上下文，订单持久化仅关联客户。
- `POST /zsjos/sales-order/external-repurchase`：录入系统外历史客户复购。唯一命中无主客资客户时复用客户，无命中时创建客户主档；多客户命中、身份冲突或已有主客资时拒绝。
- `GET /zsjos/sales-order/lead/{leadId}/customer-orders`：按客户聚合首购与复购订单。
- `PUT /zsjos/sales-order/{id}/resubmit`：修改 `revision_required` 原订单并创建新审批轮次。
- `GET /zsjos/sales-order/{id}`：订单、课程、凭证和当前审批轮次详情；`registrationApproval` 与 `financeApproval` 分别返回报名履约、财务节点的 `pending/approved/rejected/cancelled` 汇总状态、实际审核人用户 ID/姓名及节点时间。审核身份和结果只读自 BPM 当前任务和历史任务，不在订单域重复持久化；界面展示审核人姓名、结果和审核时间，不展示用户 ID。
- `GET /zsjos/sales-order/my-page`：本人提交订单的轻量分页，支持 `status` 和订单号/学员姓名/手机号 `keyword`。
- `POST /zsjos/sales-order/my-search-page`：在本人订单固定范围内组合关键词与高级条件树；高级条件非空时忽略可选状态分组。
- `GET /zsjos/sales-order/my-status-counts`：本人订单的全部、待审核、已驳回待修改、已通过数量。
- `GET /zsjos/sales-order/my/{id}`：本人订单完整详情；已驳回订单包含最新轮次 `decisionReason` 和 `canRevise`。
- `GET /zsjos/sales-order/approval/filter-profile`：返回当前租户已发布的待处理/已处理方案，以及当前用户按审批配置部门解析出的 `centers`。单中心用户只返回本中心；同时落入两个配置部门范围的用户返回报名履约和财务两个中心。
- `GET /zsjos/sales-order/approval/inbox-page?center=registration|finance&groupKey=pending&optionKey=all&keyword=`：按当前用户审批任务、处理状态和中心分页查询轻量订单列表，支持订单号、学员姓名和手机号搜索；`handled` 仍可作为兼容参数。服务端将筛选条件与当前用户允许的 BPM 任务节点取交集，伪造无权中心返回权限错误，前端隐藏筛选项不是授权边界。
- `POST /zsjos/sales-order/approval/search-page`：在当前用户 BPM 任务和中心权限固定范围内组合关键词与高级条件；高级条件非空时忽略可选处理分组，但不扩大允许的任务节点。
- `PUT /zsjos/sales-order/{id}/approve`、`/reject`：处理当前 BPM 任务，必须提交 `taskId`、当前 `approvalRoundId`、订单/轮次版本、审批意见和幂等键。订单行与轮次行锁保证审批、驳回和终止只有首个命令成功。
- `PUT /zsjos/sales-order/{id}/supervisor-confirmation/request`：当前报名履约或财务普通审批人申请直属主管确认。请求必须提交普通 `taskId`、轮次、订单/轮次版本、必填且不超过 1000 字的 `reason` 和幂等键。每轮每中心最多一条申请；待确认时锁定该中心全部普通任务，另一中心继续处理。
- `GET /zsjos/sales-order/supervisor-confirmation/inbox-page`、`POST /zsjos/sales-order/supervisor-confirmation/search-page`：只查询当前用户作为指定主管的待办或已办，`handled=false|true` 区分状态，支持订单关键词。
- `PUT /zsjos/sales-order/{id}/supervisor-confirmation/confirm`、`/reject`：主管提交确认决定，必须携带 `confirmationId`、主管 BPM `taskId`、轮次、订单/轮次/确认记录版本、必填且不超过 1000 字的 `reason` 和幂等键。确认后恢复该中心普通任务；不确认使用 BPM 驳回整轮并退回销售补正。
- `PUT /zsjos/sales-order/{id}/terminate`：未生效订单的原创建人可填写原因终止当前 BPM 流程，包括已进入财务节点的订单。

审批、驳回和终止命令使用租户内唯一的命令账本。相同幂等键只有在订单、轮次、流程实例、命令类型、审批节点、BPM 任务、操作人和规范化请求内容全部一致时才返回首次结果；任一属性不同均返回幂等冲突。BPM 调用失败会回滚订单、轮次和命令账本。终止命令不要求 `taskId`，但仍绑定当前订单、轮次、创建人和 BPM 实例。

外部复购和所有客户联系方式写入口共用 Person 身份写服务。手机号与微信号使用同一租户级占用空间，手机号命中微信号同样冲突；值仅去除首尾空格，微信号大小写敏感且最多 64 个字符。同一 Person 的手机号与微信号可填写相同值并只占用一次。并发创建相同联系方式时只允许创建一份客户主档，其他请求按已存在客户继续执行复购入口校验。

审批详情仅在当前中心节点仍为 `pending` 且当前待办仍提供 `taskId` 时显示通过、驳回操作。任一审核人处理该中心节点后，同中心其他用户不再显示操作按钮；服务端仍以 BPM 待办状态校验并拒绝过期或重复处理。
上线后创建的审批轮次返回 `supervisorConfirmationEnabled=true`。申请时服务端从申请人直属部门读取 `leaderUserId`；未配置、负责人停用或负责人就是申请人分别返回稳定错误。确认记录为 `pending/confirmed/rejected/cancelled`，并行中心驳回、销售终止或流程取消会把未完成记录置为 `cancelled`。上线前已存在轮次保持 `false`，继续旧流程且禁止发起主管确认。
- `POST /zsjos/sales-order/voucher/upload`：上传 JPG、PNG、WebP 或 PDF，最多 10 MB；最终提交最多引用 9 个文件。工作台选择文件时只在浏览器本地暂存和预览，用户确认提交后才调用此接口；上传失败会保留表单和成功引用，重试只上传失败或尚未上传的文件。

提交命令必须携带 `idempotencyKey`。同一次录单或补正意图的快速重复点击、凭证上传失败和请求重试必须复用该键，只有服务端确认成功后才轮换；首次录单在锁定客资行后会再次检查该键，使并发相同请求返回已创建订单，而不是重复创建或误报已有活动订单。订单总金额不由客户端提交，服务端以各课程 `actualAmount` 重新汇总。手机号和微信号至少一项；非零订单必须引用至少一份由当前销售上传的缴费凭证。省市名称由服务端根据省市级联编码生成快照，五类订单字典值由系统字典 API 校验。

## 状态

- `pending_approval`：当前 BPM 轮次处理中，同一客资禁止新建活动订单。
- `revision_required`：任一中心驳回或流程取消，原订单可补正重提，同一客资仍禁止新建活动订单。
- `effective`：两个中心均通过；关联 Opportunity 和 Lead 在同一事务进入 `won`。客户主档保留，订单继续以必填 `personId` 关联客户。
- `terminated`：创建人主动终止未生效订单；当前 BPM 轮次同时终止。首购恢复商机跟进状态，复购不修改客资或商机。

已成交客资是否显示复购入口只消费服务端 `ENTER_REPURCHASE` 动作。该动作同时校验创建权限、对象权限、未挂起、客户已有生效订单且不存在活动复购订单；前端不得再以 `status=won` 自行推断。

`orderType` 由服务端判定并在创建后不可修改：首购为 `first_purchase`，复购为 `repurchase`。复购生效只新增客户的一张有效订单，不覆盖客资、商机或首次成交时间；同一客户最多一张活动复购订单。

审批轮次保存最终非通过原因快照。补正重提创建新的空原因轮次，不覆盖上一轮审计记录。
