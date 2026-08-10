# 客资、机会、订单与学生服务全生命周期模型

> 文档性质：目标领域模型、状态协议与数据流转契约  
> 适用对象：产品、研发、测试、数据人员、系统管理员和 AI 代理  
> 目标版本：新业务模型 V1  
> 状态表达：中文名称用于展示，英文 `snake_case` 代码用于接口与持久化  
> 实施说明：本文描述目标设计，不代表当前数据库、接口和页面已经实现

需求来源包括《客资和订单字段》原始字段清单以及本轮已确认业务流程；两者冲突时，以本轮已确认业务决策和本文明确替代关系为准。

## 1. 文档结论

旧模型把“客资状态、销售成交进度、付款、录单、报名和财务审核”放在客资或订单的一条状态线上，无法正确表达复购，也无法保留每次购买的独立历史。新模型必须拆成相互关联、各自负责的业务对象：

| 业务问题 | 归属对象 |
| --- | --- |
| 谁提交了线索、线索是否有效、分给谁 | 客资 `Lead` |
| 已有客户再次提交了什么、为何重新进入跟进 | 客资激活 `LeadActivation` |
| 当前正式销售转化或复购意向由谁跟进、成功或流失 | 销售机会 `Opportunity` |
| 本次买了什么、金额和提交资料是什么 | 订单 `Order`、订单项 `OrderItem` |
| 为哪条客资、哪些 SKU 发起收款，渠道是否支付成功 | 支付单 `PaymentOrder`、支付流水 `PaymentTransaction` |
| 一笔资金如何用于订单，预充值余额如何变化 | 订单资金分配 `OrderPaymentAllocation`、客户资金账户与账本 |
| 报名服务中心和财务是否通过本轮审核 | ZSJOS 审批轮次 `ApprovalRound`、BPM 流程实例与审批任务 |
| 审批后报名服务做了什么、留下什么凭证 | 报名服务单 `RegistrationCase`、服务记录 `RegistrationItem` |
| 学员正在享受哪一项服务 | 学生服务关系 `ServiceRelation` |
| 谁需要在何时处理什么 | 业务任务 `BusinessTask` |
| 何时发生了什么业务变化 | 业务事件 `BusinessEvent` |

核心原则：**客资不是订单，机会不是订单，订单生效不是报名服务完成，复购不是修改历史订单。**

## 2. 已确认的业务决策

1. 客资有两类提交方：公司新媒体客资中心员工、兼职人员独立提交端。
2. 提交人只填写一次客资表单。手机号和微信号均未命中时，同一事务创建 `Person` 与 `Lead`；任一标识命中已有客户时不新增客资，而是创建独立 `LeadActivation` 记录并通知当前业务负责人。
3. 手机号和微信号交叉查重指向不同客户时，提交校验不通过，不创建 `Person`、`Lead` 或 `LeadActivation`，由提交人先处理身份信息。
4. 客资判定前的联系记录归 Lead，不提前创建机会；判定有效后才创建 `initial_conversion` Opportunity，后续销售跟进和订单提交归机会，该类型不表示客户历史上的第一次付款。
5. 创建支付链接是客资提交之后的独立业务动作。支付链接必须绑定既有 `lead_id` 和 SKU，不提供通用支付链接，不接收线下转账进入本流程，绑定客资在支付单创建后不可修改。
6. 渠道支付成功后，系统根据支付单自动关联客资、产品和支付流水，并幂等创建订单草稿；提交人只核对和提交，不重新选择支付归属。
7. 支付渠道成功回调形成到账事实。财务审批核对支付归属、金额、产品和订单的一致性，不负责把未到账改成已到账。
8. 是否需要销售机会、BPM 审批、报名服务和持续学生服务由下单时固化的产品规则结果决定；具体产品和规则模型留给产品模块单独设计。
9. 需要双会签的订单提交后，由 BPM 同时创建报名服务中心和财务审批任务。
10. 两个中心都通过，本轮审批才通过，订单才成为有效订单；只有关联销售机会的正式销售转化订单才同时确认机会成交。
11. 任一中心驳回，本轮立即失败；BPM 关闭本流程未完成的审批任务，ZSJOS 向提交人创建补正任务。
12. 补正后重新提交必须创建新的 ZSJOS 审批轮次和 BPM 流程实例，两个中心都重新审批，不复用上一轮结果。
13. 主动撤回审批会取消当前轮次并使订单返回可编辑状态；取消订单表示放弃本次交易，是不同的业务动作。
14. 产品规则要求报名服务时，订单生效后才创建报名服务单。报名服务中心记录拉群、合同签署等事实和凭证。
15. 报名服务完成后，仅为产品规则要求持续学生服务的订单项创建并激活新的学生服务关系；一次性项目只完成报名或履约记录。
16. 预充值使用独立客户资金账户和只追加账本。余额归 `Person` 本人，可跨本人的多张订单分次使用，不允许转给其他客户或直接提现。
17. 复购由学生服务与交付中心发起，每次复购创建新的机会和订单。
18. 线下动作可以与系统流程同步或提前发生，系统不限制实际操作；系统允许事后补录实际发生时间和凭证。
19. 审批页面可查看订单全部信息。系统只提供通过和驳回，不规定两个中心具体审核哪些字段。

### 2.1 对旧方案的明确替代

旧的“订单提交 -> 报名办理 -> 财务审核 -> 完成”顺序废止。目标顺序为：

```text
选择既有客资和 SKU 创建支付单
  -> 渠道支付成功并自动创建订单草稿（零元订单不创建支付单）
  -> 固化产品和规则快照
  -> 按规则决定是否进入报名服务中心 + 财务双会签
  -> 订单生效
  -> 按规则决定是否创建报名服务单
  -> 报名或一次性履约完成
  -> 仅为需要持续服务的订单项创建并激活学生服务关系
```

## 3. 读取规则与优先级

- 本文中的唯一状态标识为“状态字段 + 状态值”，例如 `order.status.effective`。
- 禁止脱离字段解释 `pending`、`completed`、`cancelled` 等同名值。
- 状态字典是协议值和展示配置，不是可视化流程编排器。
- 管理员可以调整字典中文标签、排序、颜色和启停展示，不得修改已使用的英文值或借此改变业务规则。
- AI、前端和报表不得根据中文标签、部门名称、角色名称、菜单名称推断状态或权限。
- 示例用于说明正式规则，不能覆盖状态定义、事务规则和数据不变量。
- 解释冲突时，优先级为：已确认业务决策 > 数据不变量 > 核心事务 > 状态定义 > 场景示例 > 迁移附录。

## 4. 系统边界与非目标

系统负责：

- 记录客户、客资、机会、订单和服务关系之间的关联。
- 提交、分配、审批、驳回、补正、提醒和留痕。
- 保存订单每轮审批时的业务快照、BPM 关联标识、服务凭证和实际发生时间；审批意见和任务历史由 BPM 保存。
- 保存支付单、渠道支付事实、订单资金分配、客户账户余额与不可覆盖账本；支付渠道只负责执行资金动作。
- 保存下单时的产品、SKU 和规则结果快照；产品规则的定义和维护由后续确认的产品能力负责。
- 在跨对象动作中保证原子性、幂等性、权限和数据范围。

系统不负责：

- 规定销售如何联系、如何转化或必须填写哪些跟进话术。
- 规定报名服务中心或财务具体检查哪些订单字段。
- 限制员工在系统外拉群、签合同、沟通或提供服务。
- 管理排课、排期、考务或传统教务流程。
- 把合同签署、拉群等动作硬编码成订单主状态。
- 根据价格、产品名称、SKU 标签或部门名称推断审批、报名或交付规则。
- 提供通用支付链接、把线下转账作为本流程的正常入口，或在支付后人工改绑客资。
- 在 ZSJOS 内建设与 `yudao-module-bpm` 并行的工作流引擎或审批任务历史。

## 5. 领域所有权

本文定义的所有中世健业务数据表和后端业务逻辑均由 `yudao-module-zsjos` 独立维护。这里的“独立维护”包括表结构、实体、Mapper、Service、Controller、状态转换、事务、权限校验、审计事件和迁移脚本。不得因为现有 CRM 已有相似概念，就把中世健数据写入 CRM 表或把 CRM 服务当作本模型的领域服务。

| 能力 | 所有者 | 边界 |
| --- | --- | --- |
| 人员/学员身份主档 `Person` | `yudao-module-zsjos` | 独立表、独立生命周期、独立查重和合并记录 |
| 兼职提交主体 `Partner` | `yudao-module-zsjos` | 独立主体、账号映射、渠道归属和启停状态 |
| 客资 `Lead`、激活、分配、申诉 | `yudao-module-zsjos` | 独立表、激活记录和状态规则 |
| 销售机会 `Opportunity` | `yudao-module-zsjos` | 独立表；包含首次销售转化和复购 |
| 订单、订单项、审批轮次和业务状态 | `yudao-module-zsjos` | 独立业务表、快照、BPM 引用和订单事务 |
| 流程定义、流程实例、审批任务和审批历史 | `yudao-module-bpm` | 双会签流程执行、处理人、意见、驳回和取消；通过公开 API 与事件连接 ZSJOS |
| 报名服务单、报名记录、学生服务关系 | `yudao-module-zsjos` | 独立表；报名完成后按固化产品规则决定是否激活服务关系 |
| 业务任务、提醒、业务事件、审计 | `yudao-module-zsjos` | 独立表，事件只追加不覆盖 |
| 用户、部门、岗位、角色、字典、权限 | `yudao-module-system` | 仅复用基础身份和权限 API，不复制系统表 |
| 支付单、渠道流水引用、订单资金分配、客户资金账户、退款申请和业务审核 | `yudao-module-zsjos` | 独立业务表、账户账本、独立状态和业务事务 |
| 支付/退款渠道执行 | 外部支付或财务基础能力 | 仅执行资金动作；渠道流水号作为 ZSJOS 记录的外部引用 |
| 产品、SKU 和产品业务规则 | 后续确认的产品能力 | 通过公开契约提供稳定引用和规则结果；本文不定义其内部模型 |

现有 CRM 的 Customer、Clue、Business、Contract 等表和服务**不是本模型的数据来源、持久化目标或运行时业务依赖**。ZSJOS 不读取 CRM 表完成查重、机会、订单、合同或服务流程，也不调用 CRM 领域 Service 代替本模块逻辑。未来如果确需同步 CRM，必须作为单独的外部集成需求确认，通过公开 API 或消息同步，并在 ZSJOS 表中保存自己的业务副本和外部引用；禁止跨模块 DAL、Mapper、表或领域 Service 直接调用。

### 5.1 物理数据口径

目标物理表统一使用 `zsjos_` 前缀，由 `yudao-module-zsjos` 的 DAL 维护。以下为当前建议表边界，最终建表仍需单独确认字段、索引和迁移方案：

| 逻辑对象 | 建议独立表 |
| --- | --- |
| 人员/学员身份及合并历史 | `zsjos_person`、`zsjos_person_merge_event` |
| 兼职提交主体 | `zsjos_partner` |
| 客资、提交时意向课程、附件、激活、分配历史、申诉 | `zsjos_lead`、`zsjos_lead_intended_product`、`zsjos_lead_attachment`、`zsjos_lead_activation`、`zsjos_lead_assignment_history`、`zsjos_lead_appeal` |
| 销售机会 | `zsjos_opportunity` |
| 订单和订单项 | `zsjos_order`、`zsjos_order_item` |
| 审批轮次、快照和 BPM 关联 | `zsjos_order_approval_round`；不创建重复 BPM 任务状态的 ZSJOS 审批任务表 |
| 报名服务单和服务记录项 | `zsjos_registration_case`、`zsjos_registration_item` |
| 学生服务关系和服务记录 | `zsjos_service_relation`、`zsjos_service_record` |
| 支付单、渠道交易和订单资金分配 | `zsjos_payment_order`、`zsjos_payment_transaction`、`zsjos_order_payment_allocation` |
| 客户资金账户和只追加账本 | `zsjos_customer_account`、`zsjos_customer_account_ledger` |
| 退款业务记录和订单项明细 | `zsjos_refund_case`、`zsjos_refund_item` |
| 普通业务任务和业务事件 | `zsjos_business_task`、`zsjos_business_event` |

物理关联统一使用 ZSJOS 自己的主键，例如 `person_id`、`opportunity_id`、`order_id`。CRM ID、支付渠道流水号或其他系统 ID 只能放在明确的 `external_*` 引用字段中，不得充当 ZSJOS 领域对象的主键，也不得替代本模块外键。

### 5.2 后端逻辑口径

- ZSJOS Controller 负责本业务接口、参数校验入口、权限注解和响应协议。
- ZSJOS Service 负责查重、派单、机会、订单、BPM 流程编排与结果消费、报名、服务、复购、退款联动及业务事务。
- ZSJOS DAL 只访问 `zsjos_` 自有业务表；禁止访问 CRM Mapper 或 CRM 表。
- 系统用户、部门、岗位、权限等基础信息通过 `yudao-module-system` 的公开 API 获取。
- 外部支付结果通过公开 API、回调或消息进入 ZSJOS，由 ZSJOS Service 更新自己的付款/退款业务记录。
- 跨对象状态变化必须在 ZSJOS 事务边界内完成，不能把一致性责任交给 CRM 模块。

## 6. 逻辑实体与关系

```text
Person 0:N Lead
Person 0:N LeadActivation
Person 0:N Opportunity
Partner 0:N Lead（仅兼职来源）
Lead 0:1 Opportunity（首次销售转化机会）
Lead 1:N LeadIntendedProduct（提交时课程快照，恰好一个主意向）
Lead 0:N PaymentOrder
ServiceRelation 0:N Opportunity（复购来源）
Opportunity 0:1 Order（正式销售转化订单）
PaymentOrder 0:1 PaymentTransaction（当前专属支付链接成功后只形成一笔渠道流水）
PaymentOrder 0:1 Order（支付成功后幂等自动创建；零元订单无支付单）
PaymentTransaction N:M Order（通过 OrderPaymentAllocation）
Person 0:N CustomerAccount（每币种最多一条）
CustomerAccount 1:N CustomerAccountLedger
Order 1:N OrderItem
Order 0:N ApprovalRound
ApprovalRound 1:1 BPM ProcessInstance（每轮独立流程实例）
BPM ProcessInstance 1:2 BPM UserTask（报名服务中心与财务并行）
Order 0:N RefundCase
RefundCase 1:N RefundItem
Order 0:1 RegistrationCase
RegistrationCase 0:N RegistrationItem
Order 0:N ServiceRelation（报名完成后按购买服务项创建）
ServiceRelation 1:N ServiceRecord
任一业务对象 0:N BusinessTask
任一业务对象 1:N BusinessEvent
```

补充约束：

- 一个 `Person` 可以有多条历史客资、多个机会、多个订单和多个服务关系。`Person` 是 ZSJOS 自己维护的业务身份，不等同于系统用户，也不等同于 CRM 客户。
- 客资转换后保留，不被订单覆盖；客资只负责首次需求入口。
- `LeadIntendedProduct` 是客资提交时的原始意向快照。Opportunity 可以继承该快照作为初始意向，但不得覆盖或替代它；后续正式交易仍以订单项及其产品规则快照为准。
- 同一客资最多创建一个 `initial_conversion` 机会。每个销售购买意向对应一个机会和最多一张订单；产品规则不要求销售机会的订单可以不关联 Opportunity。
- 支付单创建时必须绑定既有客资和 SKU，支付成功后自动创建订单草稿；正常流程不存在支付后人工匹配或改绑客资。
- 驳回补正仍修改同一订单并新增审批轮次；主动撤回结束当前轮次并返回可编辑状态，不等同于取消订单。
- 复购必须从已有服务关系或客户发起新的复购机会，不修改原机会、原订单和原服务关系。
- 一张订单包含多个购买服务时，只为产品规则要求持续服务的订单项创建服务关系；每条关系必须能追溯到订单项。

## 7. 实体字段设计

以下是目标逻辑字段。`id`、`tenant_id`、创建人、创建时间、更新人、更新时间和逻辑删除等框架公共字段不在每张表重复列出。需要并发保护的可变业务表必须显式增加 `version`；只追加的审计、事件和账本表不依赖覆盖更新。

### 7.1 人员/学员身份 `Person`

`Person` 是 ZSJOS 自己维护的客户与学员身份主档，不引用 CRM Customer 作为主键来源。它承载查重所需的手机号、微信号、姓名等身份信息，以及合并、拆分和联系方式变更历史。至少包含 `id`、`person_no`、`name`、`mobile`、`wechat_id`、`identity_status`、`first_seen_at`、`last_seen_at`；敏感字段按系统安全规则脱敏展示。查重不得直接覆盖历史身份，合并必须追加 `PersonMergeEvent`。

### 7.2 兼职提交主体 `Partner`

`Partner` 是 ZSJOS 自己维护的兼职提交主体，不使用 CRM 联系人或临时文本代替。至少包含 `partner_no`、`name`、`mobile`、`status`、`bound_system_user_id`、`channel_id`、`enabled_at`、`disabled_at`。兼职端登录账号仍由系统身份能力认证，但账号与业务主体的绑定关系由 ZSJOS 保存。

### 7.3 客资 `Lead`

| 字段 | 类型建议 | 必填 | 含义 |
| --- | --- | --- | --- |
| `person_id` | bigint | 是 | 关联 ZSJOS `Person`；新客资与新 Person 在同一事务创建，命中已有 Person 时不创建新 Lead |
| `submitted_name`、`submitted_mobile`、`submitted_wechat_id` | varchar | 是/条件必填 | 本次表单的原始身份快照，用于查重和审计，不因 Person 后续变更而覆盖 |
| `source_type` | varchar | 是 | `internal_new_media` 或 `part_time_partner` |
| `source_user_id` | bigint | 条件必填 | 内部提交员工；必须取当前登录账号，不能由前端伪造 |
| `partner_id` | bigint | 条件必填 | 兼职提交主体标识 |
| `source_channel_id` | bigint/varchar | 否 | 更细的业务来源，来自业务配置或字典 |
| `province_code`、`province_name`、`city_code`、`city_name` | varchar | 新提交必填 | 提交时省市及名称快照；标准编码为数字字符串，“其他”为 `OTHER` |
| `lead_category`、`remark` | varchar | 新提交是/否 | 客资分类字典值与备注 |
| `dispatch_mode` | varchar | 新提交必填 | `auto` 或 `specified` |
| `pending_assignee_user_id`、`pending_expires_at` | bigint/datetime | 条件必填 | 当前待接销售及自动派单截止时间；指定派单不超时 |
| `assignment_attempt_count`、`assignment_rule_snapshot` | int/json | 自动派单必填 | 已尝试次数和提交时规则快照 |
| `public_pool_at`、`submission_idempotency_key` | datetime/varchar | 条件必填/新提交必填 | 进入抢单池时间和提交幂等键 |
| `status` | varchar | 是 | 客资主状态 |
| `assignment_status` | varchar | 是 | 当前销售分配状态 |
| `owner_user_id` | bigint | 条件必填 | 当前主责销售；`owned` 时必须存在 |
| `current_assignment_first_follow_up_deadline_at` | datetime | 否 | 当前归属周期首跟截止时间；逾期不自动挂起 |
| `qualification_round_no`、`qualification_started_at`、`qualification_deadline_at` | int/datetime | 条件必填 | 有效性判定轮次与当前轮次计时 |
| `qualification_rule_snapshot` | json | 条件必填 | 判定任务创建时固化的规则编号、版本和时限 |
| `suspended_at` | datetime | 否 | 判定超时扫描实际挂起时间 |
| `qualified_by_user_id`、`qualified_at` | bigint/datetime | 否 | 最终有效性判定人和时间 |
| `invalid_reason`、`invalid_reason_label_snapshot`、`invalid_description` | varchar | 无效时必填 | 无效原因稳定字典值、标签快照与说明 |
| `recycle_source_owner_user_id` | bigint | 回收待处理时必填 | 回收前销售，用于主管对象范围校验 |
| `submitted_at` | datetime | 是 | 客资提交时间 |
| `converted_at` | datetime | 否 | 首次销售转化机会创建成功时间，不是订单创建或首次付款时间 |
| `closed_at`、`close_reason` | datetime/varchar | 条件必填 | 关闭时间和原因 |

提交人身份必须满足二选一：内部来源填写 `source_user_id`，兼职来源填写 `partner_id`。手机号和微信号均未命中时，同一事务创建 Person 与 Lead；任一标识命中同一已有 Person 时不插入 Lead，而是创建客资激活记录；两个标识交叉指向不同 Person 时整个提交失败。

新提交必须至少选择一个启用课程、同一课程不可重复且恰好一个主意向。稳定产品引用与提交时名称保存在 `zsjos_lead_intended_product`；最多九张图片的 Infra 文件编号和提交时元数据快照保存在 `zsjos_lead_attachment`。附件保持私有读，只有在客资读取权限校验通过后，ZSJOS 才通过 Infra 公共 API 按文件原存储配置生成短期访问地址；临时签名地址不作为持久化标识。重复客资的本次地区、课程、分类、备注和附件只写入 `LeadActivation.submission_snapshot`，不覆盖原 Lead。

### 7.4 客资激活 `LeadActivation`

重复客资不覆盖原 Lead，也不伪造一条新客资。每次激活独立保存，至少包含 `person_id`、`lead_id`、`source_type`、`source_user_id`、`partner_id`、`source_channel_id`、`submission_snapshot`、`activated_at`、`idempotency_key`。通知对象及投递结果通过关联业务任务或事件留痕。

激活通知按当前业务关系路由：有活动服务关系时通知学生服务负责人；否则有未结束机会时通知机会负责人；否则通知客资当前负责人，尚无负责人时进入销售分配流程。路由依据稳定业务关系和状态，不根据部门、岗位或角色名称推断。

### 7.5 客资分配历史 `LeadAssignmentHistory`

记录每次派单、接单、拒单、超时、转派、回收、进入抢单池和认领。除负责人、操作人、原因和发生时间外，自动派单还保存规则、尝试序号、候选销售、派出截止时间和响应时间。通常分配动作不扩张客资主状态；有效性判定超时后的 `suspended` 是明确例外，用于服务端统一阻断销售写操作。

`SalesDispatchPreference` 保存租户内销售用户是否主动开启自动接单，首次默认为暂停。它不表达页面在线、客资归属或销售负载；页面在线是带 TTL 的 Redis 临时事实，只有“页面在线 + 开启接单 + 启用销售专员资格”同时满足时才可进入自动派单候选。每名销售同一时刻最多保留一个自动待接预留，Redis 预留不能替代 `Lead.assignment_status` 和分配历史。

### 7.6 客资申诉 `LeadAppeal`

每次申诉独立保存，至少包含 `lead_id`、`round_no`、`status`、`applicant_user_id`、`reason`、`evidence_refs`、`reviewer_user_id`、`decision_reason`、`submitted_at`、`decided_at`。申诉状态不得塞入 `Lead.status`。

### 7.7 销售机会 `Opportunity`

| 字段 | 类型建议 | 必填 | 含义 |
| --- | --- | --- | --- |
| `person_id` | bigint | 是 | 关联 ZSJOS `Person` |
| `type` | varchar | 是 | 首次销售转化 `initial_conversion` 或复购 `repurchase` |
| `lead_id` | bigint | 条件必填 | 首次销售转化来源客资，且同一 Lead 最多一条；复购可为空 |
| `source_service_relation_id` | bigint | 条件必填 | 复购来源服务关系；客户级复购无法指定时可为空 |
| `previous_order_id` | bigint | 否 | 直接关联的上一张订单，便于追溯 |
| `status` | varchar | 是 | 机会生命周期状态 |
| `owner_user_id` | bigint | 是 | 当前成交责任人 |
| `expected_product_summary` | json/varchar | 否 | 意向产品摘要，不替代正式订单项 |
| `next_follow_up_at` | datetime | 否 | 提醒时间，不是状态 |
| `shelved_until`、`shelved_reason` | datetime/varchar | 条件必填 | 暂缓期限和原因 |
| `won_at`、`lost_at`、`lost_reason` | datetime/varchar | 条件必填 | 结果事实 |

首次销售转化机会由销售转化中心负责；复购机会由学生服务与交付中心发起。`initial_conversion` 不表示 Person 的第一次付款。责任部门不能仅按岗位或部门中文名称推断，应由权限和业务分配关系确定。

### 7.8 订单 `Order` 与订单项 `OrderItem`

| 字段 | 类型建议 | 必填 | 含义 |
| --- | --- | --- | --- |
| `lead_id` | bigint | 是 | 订单来源客资；必须与支付单绑定客资一致 |
| `source_payment_order_id` | bigint | 条件必填 | 支付成功自动创建的订单必须填写且唯一；授权零元订单为空 |
| `opportunity_id` | bigint | 条件必填 | 正式销售转化订单必须关联且一对一唯一；产品规则不要求销售机会时可为空 |
| `person_id` | bigint | 是 | 与 Lead 的 `person_id` 一致；存在机会时还必须与机会 `person_id` 一致 |
| `order_no` | varchar | 是 | 全局或租户内唯一业务编号 |
| `status` | varchar | 是 | 订单主状态 |
| `submitter_user_id` | bigint | 条件必填 | 本次订单提交人，补正任务接收人 |
| `submitter_center_type` | varchar | 是 | `sales_conversion` 或 `student_delivery` |
| `total_amount`、`discount_amount`、`payable_amount` | decimal | 是 | 本次交易金额事实 |
| `contract_refs` | json/关联表 | 否 | 合同或附件引用 |
| `current_approval_round_id` | bigint | 否 | 当前审批轮次引用；未提交审批或产品规则不要求审批时为空 |
| `submitted_at`、`effective_at`、`cancelled_at` | datetime | 条件必填 | 关键业务时间 |
| `cancel_reason` | varchar | 条件必填 | 生效前取消原因 |

`OrderItem` 至少包含 `order_id`、`product_id`、`sku_id`、`quantity`、`unit_price`、`discount_amount`、`payable_amount`、`product_snapshot`、`product_rule_snapshot`、`product_rule_version`。产品规则快照记录当时已经解析的审批、报名和持续服务规则结果，创建后不可修改；具体规则结构留给产品模块设计。后续服务关系必须引用形成它的订单项。

支付成功后，系统根据支付单幂等创建订单草稿；零元订单不创建支付单，必须记录零元原因和授权事实。订单应展示本次提交的完整信息。模型不增加“报名审核字段清单”或“财务审核字段清单”。财务点击通过表示支付归属、金额、产品和订单信息核对通过，不产生或改变渠道到账事实。

### 7.9 审批轮次 `ApprovalRound` 与 BPM 流程

`ApprovalRound`：

| 字段 | 类型建议 | 必填 | 含义 |
| --- | --- | --- | --- |
| `order_id`、`round_no` | bigint/int | 是 | 同一订单内轮次唯一且连续递增 |
| `status` | varchar | 是 | 本轮汇总状态 |
| `order_snapshot` | json | 是 | 本轮提交时的完整订单快照 |
| `process_instance_id` | varchar | 是 | 当前轮次关联的 BPM 流程实例，租户内唯一 |
| `process_definition_key` | varchar | 是 | 本轮使用的稳定 BPM 流程定义 Key |
| `submitted_by`、`submitted_at` | bigint/datetime | 是 | 本轮提交事实 |
| `completed_at` | datetime | 否 | 本轮通过或驳回时间 |
| `rejected_bpm_task_id` | varchar | 否 | 导致本轮失败的 BPM 审批任务引用 |

`ApprovalRound` 是 ZSJOS 业务审计对象，不是工作流任务副本。每轮使用独立
`processInstanceId`，订单 ID 或轮次 ID 作为稳定 `businessKey`。报名服务中心与财务
两条并行任务、任务处理人、审批意见、附件、通过、驳回、取消和历史均由 BPM
持有。ZSJOS 通过 BPM 公共 API 发起流程，并通过按流程定义 Key 过滤的事件监听器
幂等更新轮次、订单、机会和相关业务状态。为支持 `partially_approved`、财务节点核对结果和驳回任务引用，BPM 公共任务结果事件必须至少提供流程实例 ID、任务 ID、任务定义 Key、动作、处理人、发生时间和幂等事件 ID。

### 7.10 报名服务单 `RegistrationCase` 与记录 `RegistrationItem`

`RegistrationCase` 一张有效订单最多一条，至少包含 `order_id`、`status`、`owner_user_id`、`started_at`、`completed_at`、`cancelled_at`、`cancel_reason`。

`RegistrationItem` 用于扩展服务事实，不把每一种动作做成固定布尔列：

| 字段 | 含义 |
| --- | --- |
| `registration_case_id` | 所属报名服务单 |
| `item_type` | `group_created`、`contract_signed`、`course_activated`、`material_processed`、`service_explained`、`other` |
| `status` | 记录项是否完成；若无独立过程可只保存完成记录 |
| `occurred_at` | 实际发生时间，允许早于系统记录时间 |
| `recorded_at`、`recorded_by` | 系统补录时间和补录人 |
| `evidence_refs`、`remark` | 凭证和说明 |

报名服务单能否完成由当前业务配置决定，不能在状态机中硬编码必须具有哪些 `item_type`。这保证系统只做记录和流转，不替代线下业务判断。

### 7.11 学生服务关系 `ServiceRelation`

| 字段 | 含义 |
| --- | --- |
| `person_id` | 被服务的 ZSJOS `Person` |
| `order_id`、`order_item_id` | 服务权益来源，必须可追溯到有效订单 |
| `registration_case_id` | 激活该关系的报名服务单 |
| `status` | 服务关系状态 |
| `owner_user_id` | 学生服务与交付负责人 |
| `activated_at` | 产品规则要求持续服务时，在报名完成事务中的直接激活时间 |
| `paused_at`、`pause_reason` | 暂停事实 |
| `completed_at` | 正常服务结束时间 |
| `terminated_at`、`termination_reason` | 非正常终止事实，例如全额退款后的终止 |

每次复购产生新的服务关系；即使服务内容相同，也不得覆盖或“续写”原关系。服务中心的具体服务动作只作为 `ServiceRecord` 记录和提醒，不扩张为主状态。

### 7.12 统一业务任务 `BusinessTask`

至少包含 `task_type`、`biz_type`、`biz_id`、`status`、`assignee_type`、`assignee_id`、`due_at`、`completed_at`、`cancelled_at`、`cancel_reason`、`payload`。典型任务：接单、补正订单、处理报名服务、业务提醒。

BPM 审批任务不写入 `BusinessTask`，也不在 ZSJOS 建立任务副本；待办、处理人、意见、逾期和任务历史从 BPM 查询。`BusinessTask` 只保存补正订单、接单、报名处理和业务提醒等非工作流任务。

派单时为当前候选销售创建 `lead_assignment_accept` 任务；接受时完成，拒绝、超时、转派或进入抢单池时取消。销售通过接单、抢单或管理员转派取得归属时，在同一事务内创建 `lead_first_follow_up` 任务。首次跟进任务按对应分配历史编号幂等，payload 固化跟进规则版本和归属开始时间；本阶段逾期只形成任务逾期事实，不自动回收客资。

客资仍为 `submitted` 且已经归属时，销售可以追加 `LeadFollowUpRecord`。新增记录不改变 Lead 主状态；首次记录完成当前归属周期的 `lead_first_follow_up`，并创建 `lead_qualification` 任务。判定任务按客资和轮次幂等，固化创建时启用规则的编号、版本、时限及截止时间；后续规则修改不追溯已有轮次。可选的下次跟进时间创建或替换 `lead_follow_up_reminder`。记录只追加，方式、结果和分类标签均固化快照。判定有效只开放后续转化入口，不自动创建 Opportunity 或订单。

### 7.13 业务事件 `BusinessEvent`

事件日志只追加不覆盖，至少包含 `event_type`、`aggregate_type`、`aggregate_id`、`operator_user_id`、`from_status`、`to_status`、`reason`、`evidence_refs`、`related_object_refs`、`occurred_at`、`idempotency_key`。状态字段只表达当前值，事件负责解释“为什么变成现在这样”。

### 7.14 支付单、渠道流水与订单资金分配

`PaymentOrder` 是生成支付链接前创建的 ZSJOS 收款请求，至少包含 `payment_order_no`、`lead_id`、`person_id`、`opportunity_id`、`status`、`expected_amount`、`currency`、`product_items_snapshot`、`product_rule_snapshot`、`initiator_user_id`、`link_token_hash`、`expires_at`、`paid_at`。`lead_id` 创建后不可修改；正式销售转化收款可以同时绑定未结束 Opportunity，其他产品规则不要求销售机会时为空。支付链接只供该支付单使用，不是通用链接。

`PaymentTransaction` 是支付渠道成功回调形成的不可伪造资金事实，至少包含 `payment_order_id`、`amount`、`currency`、`payment_method`、`paid_at`、`external_channel`、`external_transaction_no`、`payer_reference`、`callback_event_id`。渠道流水号和回调事件 ID 必须幂等唯一；实际付款人可以不同于订单 Person，不改变支付归属。

`OrderPaymentAllocation` 表达支付流水或客户余额用于哪张订单，至少包含 `order_id`、`payment_transaction_id`、`customer_account_ledger_id`、`allocated_amount`、`allocated_at`、`idempotency_key`。渠道资金和账户余额二选一作为本条分配来源，同一资金来源的累计分配不得超过其可用金额。

支付回调无法按内部支付单号处理等特殊技术或数据异常进入独立异常审批，不创建“待匹配客资”正常状态。异常审批对象、状态和处置权限在支付异常流程评审时单独确认。

### 7.15 客户资金账户与账本

`CustomerAccount` 每个 Person、币种最多一条，至少包含 `person_id`、`currency`、`available_balance`、`version`。余额只供本人跨订单分次使用，不允许转给其他 Person 或直接提现。

`CustomerAccountLedger` 只追加不覆盖，至少包含 `account_id`、`entry_type`、`amount`、`balance_before`、`balance_after`、`payment_transaction_id`、`order_id`、`refund_case_id`、`occurred_at`、`operator_user_id`、`idempotency_key`。购买预充值产品并支付成功时增加余额；订单使用余额时原子扣减并写入消费流水。Person 页面展示账户余额，但 Person 主表不保存可直接修改的余额字段。

### 7.16 退款申请 `RefundCase` 与退款明细 `RefundItem`

`RefundCase` 是 ZSJOS 自己维护的退款业务聚合，至少包含 `order_id`、`status`、`requested_amount`、`approved_amount`、`refunded_amount`、`reason`、`evidence_refs`、`requested_by_user_id`、`approved_by_user_id`、`process_instance_id`、`external_refund_no`、`completed_at`。需要审批时由 BPM 执行退款流程，ZSJOS 消费流程结果并负责申请业务状态、资金及服务关系联动；实际渠道退款可以委托外部能力执行。

`RefundItem` 至少包含 `refund_case_id`、`order_item_id`、`service_relation_id`、`requested_amount`、`approved_amount`、`refunded_amount`。退款按订单项精确处理，只调整明细关联的资金和服务关系，不按整张订单笼统终止所有服务。

## 8. 扁平状态字典

### 8.1 配置规则

- 每个字典只有一层，字典项不得包含子状态。
- 唯一身份为 `dict_type + value`；相同 `value` 可以出现在不同字典类型中。
- 所有 `value` 均为稳定协议值，不得在生产使用后修改或删除。
- `label`、`sort`、`color_type` 是展示配置；`status = 0` 表示字典项可展示。
- 字典启停不等于允许或禁止业务转换，后端领域服务仍是状态写入唯一入口。

### 8.2 字典类型清单

| 字典名称 | `dict_type` | 对应字段 | 性质 |
| --- | --- | --- | --- |
| 客资来源类型 | `zsjos_lead_source_type` | `lead.source_type` | 分类 |
| 客资主状态 | `zsjos_lead_status` | `lead.status` | 持久化状态 |
| 客资分配状态 | `zsjos_lead_assignment_status` | `lead.assignment_status` | 持久化状态 |
| 客资无效原因 | `zsjos_lead_invalid_reason` | `lead.invalid_reason` | 管理员维护的分类；初始化为空 |
| 客资无效快捷备注 | `zsjos_lead_invalid_remark_template` | 判无效备注输入模板 | 管理员维护的快捷填充文案；初始化为空，最终仅保存销售编辑后的备注文本 |
| 客资申诉状态 | `zsjos_lead_appeal_status` | `lead_appeal.status` | 过程状态 |
| 机会类型 | `zsjos_opportunity_type` | `opportunity.type` | 分类 |
| 机会状态 | `zsjos_opportunity_status` | `opportunity.status` | 持久化状态 |
| 订单状态 | `zsjos_order_status` | `order.status` | 持久化状态 |
| 审批轮次状态 | `zsjos_approval_round_status` | `approval_round.status` | 持久化状态 |
| 报名服务状态 | `zsjos_registration_case_status` | `registration_case.status` | 持久化状态 |
| 报名记录类型 | `zsjos_registration_item_type` | `registration_item.item_type` | 可扩展分类 |
| 学生服务关系状态 | `zsjos_service_relation_status` | `service_relation.status` | 持久化状态 |
| 业务任务状态 | `zsjos_business_task_status` | `business_task.status` | 持久化状态 |
| 支付单状态 | `zsjos_payment_order_status` | `payment_order.status` | 持久化状态 |
| 退款申请状态 | `zsjos_refund_case_status` | `refund_case.status` | 过程状态 |

### 8.3 字典数据清单

| `dict_type` | `sort` | `label` | `value` | `color_type` | 业务含义 |
| --- | ---: | --- | --- | --- | --- |
| `zsjos_lead_source_type` | 10 | 新媒体客资中心 | `internal_new_media` | primary | 公司内部新媒体员工提交 |
| `zsjos_lead_source_type` | 20 | 兼职人员 | `part_time_partner` | info | 兼职人员从独立端口提交 |
| `zsjos_lead_status` | 10 | 已提交 | `submitted` | info | Person 与客资已创建，等待有效性处理 |
| `zsjos_lead_status` | 20 | 已挂起 | `suspended` | warning | 判定任务超时扫描已执行，销售只读 |
| `zsjos_lead_status` | 30 | 有效 | `valid` | success | 可以创建并进入首次销售转化机会 |
| `zsjos_lead_status` | 40 | 无效 | `invalid` | danger | 当前有效性结论为无效，可按规则申诉 |
| `zsjos_lead_status` | 50 | 已转换 | `converted` | primary | 已创建首次销售转化机会，客资职责结束 |
| `zsjos_lead_status` | 60 | 已关闭 | `closed` | default | 不再处理且未转换为机会 |
| `zsjos_lead_assignment_status` | 10 | 未分配 | `unassigned` | default | 当前没有销售归属 |
| `zsjos_lead_assignment_status` | 20 | 待接单 | `pending_acceptance` | warning | 已指定销售，等待接受 |
| `zsjos_lead_assignment_status` | 30 | 已归属 | `owned` | success | 已有当前主责销售 |
| `zsjos_lead_assignment_status` | 40 | 公海 | `public_pool` | info | 可由符合条件的销售认领 |
| `zsjos_lead_assignment_status` | 45 | 回收待处理 | `recycle_pending` | warning | 已清除当前销售，等待主管再次处置 |
| `zsjos_lead_assignment_status` | 50 | 已结束 | `closed` | default | 客资转换或关闭后不再参与分配 |
| `zsjos_lead_appeal_status` | 10 | 已提交 | `submitted` | info | 已发起无效申诉 |
| `zsjos_lead_appeal_status` | 20 | 销售主管复核中 | `sales_manager_reviewing` | warning | 第一层复核处理中 |
| `zsjos_lead_appeal_status` | 30 | 质控仲裁中 | `quality_reviewing` | warning | 已升级质控仲裁 |
| `zsjos_lead_appeal_status` | 40 | 已改判 | `overturned` | success | 无效结论改判为有效 |
| `zsjos_lead_appeal_status` | 50 | 已维持 | `upheld` | danger | 最终维持无效结论 |
| `zsjos_lead_appeal_status` | 60 | 已撤回 | `withdrawn` | default | 申请人撤回申诉 |
| `zsjos_opportunity_type` | 10 | 首次销售转化 | `initial_conversion` | primary | 由有效客资创建的首次正式销售转化机会 |
| `zsjos_opportunity_type` | 20 | 复购 | `repurchase` | success | 由学生服务关系或已有客户创建的新机会 |
| `zsjos_opportunity_status` | 10 | 待跟进 | `open` | info | 机会已创建，等待开始跟进 |
| `zsjos_opportunity_status` | 20 | 跟进中 | `following` | primary | 责任人正在推进本次购买 |
| `zsjos_opportunity_status` | 30 | 已暂缓 | `shelved` | warning | 暂时停止推进，但允许恢复 |
| `zsjos_opportunity_status` | 40 | 已成交 | `won` | success | 关联正式销售订单按固化产品规则生效 |
| `zsjos_opportunity_status` | 50 | 已流失 | `lost` | danger | 本次购买意向失败 |
| `zsjos_opportunity_status` | 60 | 已取消 | `cancelled` | default | 机会在成交前被合法取消 |
| `zsjos_order_status` | 10 | 草稿 | `draft` | default | 订单可编辑，尚未提交审批 |
| `zsjos_order_status` | 20 | 审批中 | `pending_approval` | warning | 当前轮次关联的 BPM 双会签流程处理中 |
| `zsjos_order_status` | 30 | 待补正 | `revision_required` | danger | 当前轮次被驳回，等待提交人修改 |
| `zsjos_order_status` | 40 | 已生效 | `effective` | success | 已满足固化产品规则要求的生效条件 |
| `zsjos_order_status` | 50 | 已取消 | `cancelled` | default | 生效前取消；历史仍保留 |
| `zsjos_approval_round_status` | 10 | 待审批 | `pending` | warning | BPM 双会签流程尚未产生通过结果 |
| `zsjos_approval_round_status` | 20 | 部分通过 | `partially_approved` | primary | 一个中心已通过，另一个仍待处理 |
| `zsjos_approval_round_status` | 30 | 已通过 | `approved` | success | 两个中心均已通过 |
| `zsjos_approval_round_status` | 40 | 已驳回 | `rejected` | danger | 任一中心驳回，本轮结束 |
| `zsjos_approval_round_status` | 50 | 已取消 | `cancelled` | default | 主动撤回审批或取消审批中订单，本轮结束 |
| `zsjos_registration_case_status` | 10 | 待处理 | `pending` | warning | 订单生效后已创建报名服务单 |
| `zsjos_registration_case_status` | 20 | 处理中 | `processing` | primary | 报名服务正在记录和办理 |
| `zsjos_registration_case_status` | 30 | 已完成 | `completed` | success | 报名服务完成；按产品规则决定是否激活服务关系 |
| `zsjos_registration_case_status` | 40 | 已取消 | `cancelled` | default | 经授权终止且未激活服务关系 |
| `zsjos_registration_item_type` | 10 | 已完成拉群 | `group_created` | success | 记录拉群事实和凭证 |
| `zsjos_registration_item_type` | 20 | 已完成合同签署 | `contract_signed` | success | 记录合同签署事实和凭证 |
| `zsjos_registration_item_type` | 30 | 已完成课程开通 | `course_activated` | success | 记录课程或权益开通事实 |
| `zsjos_registration_item_type` | 40 | 已完成资料处理 | `material_processed` | success | 记录报名资料处理事实 |
| `zsjos_registration_item_type` | 50 | 已完成服务说明 | `service_explained` | success | 记录已向学员说明服务内容 |
| `zsjos_registration_item_type` | 90 | 其他 | `other` | info | 其他可配置报名服务记录 |
| `zsjos_service_relation_status` | 10 | 服务中 | `active` | success | 报名完成后直接激活 |
| `zsjos_service_relation_status` | 20 | 已暂停 | `paused` | warning | 服务暂时停止但允许恢复 |
| `zsjos_service_relation_status` | 30 | 已完成 | `completed` | primary | 服务正常结束 |
| `zsjos_service_relation_status` | 40 | 已终止 | `terminated` | danger | 服务非正常结束且不允许恢复 |
| `zsjos_business_task_status` | 10 | 待处理 | `pending` | warning | 等待责任人处理 |
| `zsjos_business_task_status` | 20 | 处理中 | `processing` | primary | 责任人已开始处理 |
| `zsjos_business_task_status` | 30 | 已完成 | `completed` | success | 任务目标已完成 |
| `zsjos_business_task_status` | 40 | 已关闭 | `cancelled` | default | 因业务分支结束而关闭 |
| `zsjos_payment_order_status` | 10 | 待支付 | `pending_payment` | warning | 已绑定客资和 SKU 并生成专属支付链接 |
| `zsjos_payment_order_status` | 20 | 已支付 | `paid` | success | 支付渠道成功回调并已生成唯一支付流水 |
| `zsjos_payment_order_status` | 30 | 已过期 | `expired` | default | 超过有效期且未支付，不再接受付款 |
| `zsjos_payment_order_status` | 40 | 已关闭 | `closed` | default | 支付成功前由授权业务动作关闭 |
| `zsjos_refund_case_status` | 10 | 草稿 | `draft` | default | 退款申请尚未提交 |
| `zsjos_refund_case_status` | 20 | 审核中 | `pending_review` | warning | 退款申请等待授权审核 |
| `zsjos_refund_case_status` | 30 | 已批准 | `approved` | primary | 退款申请获批，等待渠道执行 |
| `zsjos_refund_case_status` | 40 | 已拒绝 | `rejected` | danger | 当前退款申请未获批准 |
| `zsjos_refund_case_status` | 50 | 退款处理中 | `processing` | warning | 已向外部渠道或财务发起资金退款 |
| `zsjos_refund_case_status` | 60 | 部分退款 | `partially_refunded` | warning | 已退金额大于零且小于批准金额 |
| `zsjos_refund_case_status` | 70 | 已完成退款 | `refunded` | success | 已完成本申请批准金额的退款 |
| `zsjos_refund_case_status` | 80 | 已取消 | `cancelled` | default | 资金执行前撤销退款申请 |

所有字典数据建议配置 `status = 0`、`css_class` 为空。颜色仅为默认建议，不具有业务含义。

## 9. 核心状态规则

状态转换不是字典配置，必须由领域服务基于当前状态、权限、版本号和关联对象执行。

### 9.1 客资

| 业务动作 | 起始状态 | 结果 | 同事务影响 |
| --- | --- | --- | --- |
| 提交全新客资 | 无 | `lead.status.submitted` | 手机号和微信号均未命中；原子创建 Person、Lead、来源归因和事件 |
| 提交已有客资 | 无 | 不创建新 Lead | 任一标识命中同一 Person；创建 LeadActivation 并按当前关系发送激活通知 |
| 提交身份冲突 | 无 | 校验失败 | 手机号和微信号指向不同 Person；不创建任何 Person、Lead 或 LeadActivation |
| 首次跟进 | `submitted + owned` | 主状态不变 | 完成首跟任务并创建判定任务；迟到首跟仍允许提交 |
| 判定有效 | `submitted + owned + 待判定` | `valid` | 完成判定任务并开放转化入口，不自动创建 Opportunity 或订单 |
| 判定无效 | `submitted + owned + 待判定` | `invalid` | 原因字典值与说明均必填；保留销售归属但销售只读 |
| 判定超时扫描 | `submitted + owned + 判定截止已到` | `suspended + owned` | 行锁下重新校验；扫描提交前仍允许人工判定 |
| 恢复原销售 | `suspended + owned` | `submitted + owned` | 校验原销售仍启用，跳过新首跟并创建新判定轮次 |
| 转派 | `suspended + owned` / `recycle_pending` | `submitted + owned` | 新销售直接进入待判定并重新计时；挂起转派不得选择原销售 |
| 回收 | `suspended + owned` | `submitted + recycle_pending` | 清除当前销售并保留回收来源销售 |
| 释放到抢单池 | `suspended + owned` / `recycle_pending` | `submitted + public_pool` | 被抢后重新进入待首跟 |
| 申诉改判 | `invalid` | `valid` | 结束申诉并记录改判事件 |
| 创建首次销售转化机会 | `valid` | `converted` | 原子创建唯一 `initial_conversion` Opportunity，分配状态改为 `closed` |
| 关闭 | `invalid` / `valid` | `closed` | 记录关闭原因，分配状态改为 `closed` |

一般派单、接单、拒单、公海释放和认领只改变 `assignment_status`、负责人和分配历史。判定超时挂起是新增例外：扫描把 `Lead.status` 从 `submitted` 改为 `suspended`；恢复、转派、回收或释放再将主状态恢复为 `submitted`。无效申诉由 `LeadAppeal.status` 表达，不增加 `Lead.status.appealing`。

### 9.2 机会

| 业务动作 | 起始状态 | 结果 | 约束 |
| --- | --- | --- | --- |
| 开始跟进 | `open` | `following` | 记录责任人和首次跟进时间 |
| 暂缓 | `open` / `following` | `shelved` | 必须有原因，可设置恢复提醒 |
| 恢复 | `shelved` | `following` | 清除当前暂缓期限，保留历史事件 |
| 确认流失 | `open` / `following` / `shelved` | `lost` | 必须有流失原因 |
| 取消 | `open` / `following` / `shelved` | `cancelled` | 不得已有有效订单 |
| 关联订单按规则生效 | `open` / `following` / `shelved` | `won` | 只能由关联正式销售订单生效事务驱动 |

机会创建订单不代表成交；`Opportunity.status.won` 只能与其关联的 `Order.status.effective` 同时出现。订单处于 `pending_approval` 或 `revision_required` 时，不允许机会独立进入 `shelved`、`lost` 或 `cancelled`。

### 9.3 支付单、支付成功与自动建单

1. 业务人员从既有客资选择 SKU 创建 `PaymentOrder.status.pending_payment`，固化 `lead_id`、Person、产品、SKU、金额和规则快照并生成专属支付链接。
2. 待支付的支付单可以在支付成功前过期或由授权业务动作关闭；`paid`、`expired`、`closed` 均为终态，禁止重新打开或改绑客资。
3. 渠道成功回调必须携带内部支付单号。ZSJOS 以渠道流水号和回调事件 ID 幂等创建 `PaymentTransaction`，并把支付单改为 `paid`。
4. 同一事务或可靠事务后处理根据已支付的支付单幂等创建唯一订单草稿和订单项，自动继承客资、Person、产品、金额与规则快照。
5. 自动建单失败不得回退真实支付成功事实；系统必须可靠重试并产生可运维告警。重复回调或重试不得创建重复支付流水、订单或订单项。

正常流程不提供付款后人工匹配或改绑客资。无法按内部支付单号处理的特殊异常进入独立审批流程；该异常流程不改变正常支付单状态协议。

### 9.4 订单提交与产品规则分支

提交订单必须先校验订单为 `draft` 或 `revision_required`、产品和规则快照完整、资金分配满足当前订单要求或订单为经授权的零元订单。存在关联机会时，还必须校验机会未结束。

- 产品规则要求双会签时，进入 9.5 的 BPM 流程。
- 产品规则不要求双会签时，在一个事务中将订单改为 `effective`；存在关联 Opportunity 时同时改为 `won`；产品规则要求报名服务时创建唯一报名服务单，否则只写入生效和后续履约事件。
- 产品规则的定义、配置字段和组合方式不在本文设计；领域服务只能执行订单项中已经固化的规则结果。

### 9.5 订单提交与 BPM 双会签

提交订单必须在一个事务中完成：

1. 执行 9.4 的通用提交校验，并确认固化规则要求双会签。
2. 固化完整 `order_snapshot`。
3. 创建递增的新 `ApprovalRound.status.pending`。
4. 使用稳定流程定义 Key 和本轮业务 Key 调用 BPM 公共 API，启动包含报名服务中心与财务两条并行任务的流程实例。
5. 将 BPM `processInstanceId` 写入本轮，并更新 `Order.status.pending_approval` 和 `current_approval_round_id`。
6. 若是补正重提，完成对应补正任务。
7. 写入提交事件；审批待办和流程提醒由 BPM 提供。

提交接口必须接受幂等键。同一订单、同一业务请求重复调用不得创建多个轮次或 BPM 流程实例。流程定义缺失、未部署或启动失败必须返回可区分错误，且不得留下仅更新一侧的半成品状态。

### 9.6 审批通过与驳回

| BPM 当前动作 | 本轮另一 BPM 任务 | ZSJOS 轮次结果 | 订单结果 |
| --- | --- | --- | --- |
| 第一个任务通过 | `pending` | `partially_approved` | 保持 `pending_approval` |
| 第二个任务通过 | `approved` | `approved` | 原子变为 `effective` |
| 任一任务驳回 | `pending` 或 `approved` | `rejected` | 原子变为 `revision_required` |

财务审批任务通过表示财务已核对支付归属、分配金额、产品快照和订单一致性，不创建、不确认也不回退渠道支付流水。支付成功事实始终来自支付渠道回调，订单仍需等待 BPM 双会签流程通过。

第二个任务通过的事务必须同时：

- BPM 将当前任务和流程标记为通过；ZSJOS 将当前业务轮次标记为通过。
- 将订单标记为 `effective`，记录 `effective_at`。
- 存在关联机会时，将机会标记为 `won`，记录 `won_at`。
- 产品规则要求报名服务时，创建唯一的 `RegistrationCase.status.pending` 和报名服务待办。
- 写入订单生效及后续处理业务事件。

任一任务驳回的事务必须同时：

- BPM 将当前任务标记为驳回并按流程定义关闭同轮未完成任务，保留全部任务历史和意见。
- 将轮次标记为 `rejected`，订单标记为 `revision_required`。
- 给本轮订单提交人创建补正任务。

BPM 负责审批任务并发和终态，ZSJOS 监听器仍须使用版本号、幂等事件键或等价锁机制保护业务更新。已经结束的轮次不得再次改变业务结果；重复或乱序事件不得静默覆盖终态。

### 9.7 撤回、补正与重提

主动撤回审批时，必须先通过 BPM 取消当前流程实例并关闭未完成任务，再幂等地把当前轮次标记为 `cancelled`、订单返回 `draft`。撤回表示继续本次交易，不得把订单或机会标记为 `cancelled`。

补正不是复活旧 BPM 流程或任务。提交人修改同一订单后重新提交，必须创建 `round_no + 1` 的新轮次、新快照和新的 BPM 流程实例。上一轮结果仅供审计，不参与新轮聚合。

### 9.8 报名完成与服务激活

完成报名服务必须在一个事务中：

1. 校验报名服务单属于有效订单且状态为 `pending` 或 `processing`。
2. 保存本次补充的报名记录和凭证。
3. 将报名服务单标记为 `completed`。
4. 仅为固化产品规则要求持续学生服务的订单项创建新的 `ServiceRelation.status.active`。
5. 对新服务关系设置 `activated_at`，分配学生服务责任人并创建提醒或初始化记录；一次性项目不创建 ServiceRelation。
6. 写入报名完成、一次性履约完成以及适用的服务激活事件。

不创建“待交付人接收”状态。重复完成请求不得创建重复服务关系。

### 9.9 预充值账户与余额使用

- 购买预充值产品并收到渠道成功回调时，必须在同一事务中增加 CustomerAccount 可用余额并追加充值账本，使用回调事件 ID 保证只入账一次。
- 使用余额支付订单时，必须通过账户版本或等价锁原子校验和扣减余额、追加消费账本并创建 OrderPaymentAllocation，不允许出现负余额或超额分配。
- 余额只允许 Person 本人跨其多张订单分次使用，不允许跨 Person 转账或直接提现。
- 账户当前余额是账本汇总的查询缓存；任何余额变化都必须有对应账本，不提供直接修改 Person 或账户余额的普通接口。

### 9.10 复购

创建复购必须生成新的 `Opportunity.type.repurchase`。后续生成新订单、新审批轮次、新报名服务单和新服务关系。原客资保持 `converted`，原机会保持 `won`，原订单保持 `effective`，原服务关系保持其真实状态。

复购链路通过 `person_id`、`source_service_relation_id` 和可选 `previous_order_id` 追溯，不通过修改历史对象来表示。

### 9.11 报名服务、服务关系与任务退出路径

| 对象 | 合法变化 | 规则 |
| --- | --- | --- |
| 报名服务单 | `pending -> processing` | 报名服务责任人开始处理；允许直接从 `pending` 完成 |
| 报名服务单 | `pending/processing -> completed` | 必须执行报名完成事务，并按固化产品规则决定是否创建服务关系 |
| 报名服务单 | `pending/processing -> cancelled` | 仅限经授权终止且尚未创建服务关系，必须记录原因 |
| 服务关系 | `active -> paused -> active` | 暂停和恢复必须保留原因、时间和操作事件 |
| 服务关系 | `active/paused -> completed` | 服务正常结束，不允许重新激活；后续需求走复购 |
| 服务关系 | `active/paused -> terminated` | 非正常终止，不允许重新激活；必须记录原因和关联依据 |
| 普通任务 | `pending -> processing -> completed` | 允许由 `pending` 直接完成 |
| 普通任务 | `pending/processing -> cancelled` | 仅由关联业务分支结束、重建或取消驱动 |

BPM 审批任务状态遵循 BPM 合同。ZSJOS 不复制这些任务状态；新一轮审批必须创建新的 BPM 流程实例，不能重开历史流程任务。

### 9.12 取消、退款与服务终止

- `draft`、`pending_approval`、`revision_required` 订单可按权限正式取消；取消审批中的订单必须通过 BPM 取消当前流程实例并关闭其未完成任务，将轮次标记为 `cancelled`，再幂等更新订单和适用的机会终态。
- `effective` 订单不得改为 `cancelled`，退款必须通过 ZSJOS 自有 `RefundCase` 处理；外部支付能力只执行资金动作。
- 部分或全额退款不改变订单曾经生效的事实。退款通过 RefundItem 按订单项精确处理，只调整明细关联的资金和服务关系并留痕。
- 退款业务状态在 ZSJOS `RefundCase` 中表达，不复用订单审批轮次；需要审批时启动独立 BPM 流程实例。

## 10. 数据不变量与约束

1. 一次全新客资提交必须原子创建 `Person + Lead`；命中已有 Person 时只创建 LeadActivation；交叉身份冲突不得写入三者中的任何一个。
2. 同一 Lead 最多一条 `Opportunity.type.initial_conversion`；复购机会不得重新打开历史客资，建议关联来源服务关系或上一订单。
3. 支付单必须绑定既有 `lead_id`，其 Person 必须与 Lead 一致；支付单创建后不得修改 Lead，正常流程不存在无归属支付。
4. 同一支付单最多自动创建一张订单，`Order.source_payment_order_id` 必须唯一；支付回调事件 ID、渠道流水号和自动建单业务键必须唯一且幂等。
5. `Order.lead_id` 必须与来源支付单一致；存在 Opportunity 时，`Opportunity.person_id = Order.person_id`，且一个机会最多一张订单。无机会订单不得驱动任何机会为 `won`。
6. 产品、SKU 和产品规则快照在支付单、订单项或审批快照固化后不可修改。状态机只执行固化规则，不按名称、价格或展示标签推断规则。
7. 产品规则要求 BPM 双会签的订单，只有当前轮次流程通过后才能进入 `effective`；规则不要求审批的订单可按 9.4 的直接生效事务进入 `effective`。
8. 双会签流程必须包含报名服务中心和财务两个并行审批方；审批任务及其唯一性由 BPM 保证，ZSJOS 通过任务结果公共事件维护轮次汇总状态。
9. 每次补正重提创建新轮次和新 BPM 流程实例；同一订单唯一键为 `(order_id, round_no)`，`process_instance_id` 必须唯一。主动撤回或正式取消审批中订单时，当前轮次必须进入 `cancelled`。
10. 审批轮次的 `order_snapshot` 和 BPM 关联标识创建后不可修改。
11. 一张订单最多一张报名服务单；只有订单生效且固化产品规则要求报名服务时才能创建。
12. 服务关系只能由已完成的报名服务单创建，且只对应固化规则要求持续服务的有效订单项。
13. 一条 PaymentTransaction 的累计渠道资金分配不得超过交易金额；一条账户资金消费的累计分配不得超过对应可用余额，单条分配只能选择一种资金来源。
14. CustomerAccount 当前余额必须等于只追加账本的可核对汇总；充值、消费和退款导致的余额变化必须与账本、资金分配在同一事务完成，余额不得为负。
15. 退款必须通过 RefundItem 精确关联订单项；服务终止只作用于退款明细关联的 ServiceRelation。
16. 状态改变、事件写入和必要的跨对象联动必须同事务提交；跨事务回调和自动建单采用幂等消费、可靠重试和可运维告警，不允许静默部分成功。
17. 所有命令接口都要验证租户、权限、数据范围、前置状态和版本；越权与状态冲突返回可区分错误。
18. 历史轮次、激活记录、账户账本、服务记录和业务事件不可物理覆盖或删除；更正通过新增记录表达。
19. 业务请求使用幂等键，消息、通知和提醒采用事务后可靠投递，避免重复业务对象。

## 11. 提醒、展示与计算状态

提醒来自业务任务和时间字段，不增加主状态：

- 待接单：`assignment_status = pending_acceptance`。
- 待首跟：`status = submitted`、`assignment_status = owned` 且当前归属尚无首次跟进。
- 待判定：`status = submitted`、`assignment_status = owned` 且存在当前判定截止时间。
- 已挂起：`status = suspended`；销售只读，等待主管处置。
- 回收待处理：`assignment_status = recycle_pending`；无当前销售。
- 审批待办：来自 BPM 当前运行中的审批任务。
- 订单补正：补正任务为 `pending` 或 `processing`。
- 报名待办：报名服务单为 `pending` 或 `processing`。
- 跟进提醒：机会 `next_follow_up_at` 到期且机会未结束。
- 逾期：任务 `due_at < 当前时间` 且任务状态不是 `completed`、`cancelled`。

客资页面使用后端统一投影的 `handlingStage` 展示待接单、待首跟、待判定、挂起与回收待处理，前端不得依据字段组合自行推断。该字段只读且不反向驱动业务。其他页面可以计算醒目标签，但必须同时允许查看各对象真实状态。

### 11.1 客资收件箱筛选投影

“我的客资”收件箱使用服务端返回的一级业务归类和二级当前环节，不在前端维护静态筛选数组，也不新增可反向驱动业务的客资状态字段。筛选配置、计数和分页查询必须使用相同的数据权限范围和已发布状态映射。

租户管理员在“工作台 → 客资筛选方案”分别维护提交人视角和负责人视角。配置分为草稿与已发布版本：保存草稿不改变员工视图，发布后才切换运行时映射，每次发布保留不可变版本；回滚会把历史快照作为一个新的版本重新发布，不覆盖历史记录。

管理员可以调整分组和筛选项的名称、顺序、显隐，以及从系统白名单中选择条件和值；不能修改 Lead 真实状态、填写 SQL 或引用未注册字段。当前白名单为 `status` 和 `assignment_status`。新增跟进、成交审核等筛选能力时，必须先完成对应业务对象和查询字段，再由后端扩展白名单。

初始化时发布的默认一级归类为：

| 归类编码 | 展示名称 | 当前映射 |
| --- | --- | --- |
| `all` | 全部客资 | 当前视角下属于当前用户的全部客资 |
| `pending_qualification` | 待判定客资 | `lead.status = submitted` |
| `valid` | 有效客资 | `lead.status in (valid, converted)` |
| `invalid` | 无效客资 | `lead.status = invalid` |
| `closed` | 已关闭客资 | `lead.status = closed` |

提交人默认可以继续按 `assignment_status` 筛选待分配、待接单、抢单池、回收待处理和已归属；负责人默认显示归属自己的客资，并使用服务端 `handlingStage` 区分待首跟、待判定与挂起。有效客资可按 `valid` 与 `converted` 区分已判有效和已进入销售转化。申诉中、暂缓成交、成交审核和已成交等尚未实现的选项不得以零计数静态选项或前端推断提前暴露。

## 12. 完整场景

### 12.1 全新客资与首次销售转化

1. 新媒体员工或兼职人员一次提交姓名、手机号、微信号和来源信息。
2. 手机号和微信号均未命中，系统在同一事务创建 Person 和 Lead；判定有效并完成分配后，销售创建唯一 `initial_conversion` 机会，客资变为 `converted`。
3. 销售从既有 Lead 选择 SKU 创建专属支付单和支付链接。
4. 客户本人或代付人完成支付，渠道回调产生唯一支付流水，系统自动创建订单草稿并固化产品与规则快照。
5. 提交人核对并提交订单。若固化规则要求双会签，ZSJOS 建立第一轮快照并启动 BPM；否则订单按规则直接生效。
6. 订单生效时，存在关联机会则机会变为 `won`；产品规则要求报名服务时创建报名服务单。
7. 报名服务中心补录实际动作、时间和凭证并完成服务单。系统只为规则要求持续服务的订单项创建并激活服务关系，一次性项目不创建。

### 12.2 已有客资再次激活

1. 提交表单中的手机号或微信号命中同一已有 Person。
2. 系统不新增 Person 或 Lead，创建独立 LeadActivation，保存来源、提交人、表单快照和激活时间。
3. 有活动服务关系时通知学生服务负责人；否则有未结束机会时通知机会负责人；否则通知客资负责人或进入销售分配。
4. 后续业务人员需要收款时，再从既有 Lead 独立发起支付链接；客资提交本身不生成支付链接。

### 12.3 驳回、撤回与补正

1. 财务或报名服务中心驳回第一轮。
2. BPM 关闭同流程未完成任务，ZSJOS 将订单改为 `revision_required` 并向本轮提交人派发补正任务。
3. 提交人修改订单后重提，ZSJOS 创建第二轮快照并启动新的 BPM 流程实例。
4. 第一轮任何已通过结果不复用；第二轮两者均通过后订单生效。

主动撤回与驳回不同：提交人撤回时，BPM 取消当前流程，轮次变为 `cancelled`，订单返回 `draft`；修改后仍创建新轮次。正式取消订单才表示放弃交易。

### 12.4 预充值与余额消费

1. 业务人员从既有 Lead 选择预充值 SKU 创建专属支付单。
2. 渠道支付成功后创建支付流水，并按固化产品规则给该 Person 的 CustomerAccount 入账，追加充值账本。
3. Person 后续购买其他产品时，可以分次使用本人余额；扣款、消费账本和订单资金分配原子完成。
4. 余额不能转给其他 Person，也不能直接提现；余额退款去向留待支付与退款设计阶段确认。

### 12.5 复购

1. 学生服务与交付中心从 ZSJOS `Person` 或现有服务关系创建 `repurchase` 机会。
2. 新机会创建新订单，由该次提交人提交双会签。
3. 支付、审批、补正、报名和服务分支均执行本次订单固化的产品规则。
4. 需要持续服务时创建新的活动服务关系，旧订单和旧服务关系不变。

## 13. 旧模型迁移映射

本节只用于识别历史数据，不是目标状态定义。

| 旧概念 | 目标归属 | 迁移原则 |
| --- | --- | --- |
| 客资中的首触、跟进、暂缓、流失 | `Opportunity.status` | 按每次购买意向迁入机会，不能继续留在客资 |
| 客资中的待付款、已付款、已录单 | 支付单、支付流水、订单资金分配、订单和审批事件 | 不再作为客资状态；需按客资、订单和渠道流水重建事实 |
| “客资已转订单” | `Lead.status.converted` | 语义改为已创建首次销售转化机会；迁移时必须保留原订单关联 |
| 订单中的报名中、财务待审 | 审批轮次或报名服务单 | 根据真实发生时间拆分，不能机械一对一改值 |
| 单一订单审核状态 | ZSJOS `ApprovalRound` + BPM 流程历史 | 缺少双中心历史时标记迁移来源，不伪造 BPM 任务、操作人和审批意见 |
| 报名 16 步或固定布尔字段 | `RegistrationItem` | 按记录类型迁移实际完成事实和凭证 |
| 旧订单完成 | `Order.status.effective` + 报名/服务事实 | 必须判断是仅审批通过，还是报名已完成并已交付 |
| 复购覆盖原订单或原服务状态 | 新机会、新订单、新服务关系 | 拆分历史版本；无法可靠拆分的记录进入人工校验清单 |

被替代的旧字典类型：

- `zsjos_lead_deal_progress_status`：删除目标配置，迁移到机会状态及订单事实。
- `zsjos_order_enrollment_status`：删除目标配置，由报名服务单状态承担。
- `zsjos_order_review_status`：删除目标配置，由 ZSJOS 审批轮次状态和 BPM 流程状态共同承担。
- 旧付款数据按可核验事实迁入 `PaymentOrder`、`PaymentTransaction` 和 `OrderPaymentAllocation`；旧退款迁入 `RefundCase` 与 `RefundItem`。外部流水号只作为引用，不得继续把外部表作为业务主表。
- 历史重复提交若没有独立激活事实，不得根据更新时间伪造 LeadActivation；只能标记迁移来源或进入人工核验。

迁移不得重写已执行的生产迁移。正式迁移前需要单独设计可重复执行、可核对、可回滚的数据脚本，并经明确确认后执行。

## 14. 实施顺序

1. 确认 ZSJOS 各业务实体的物理表、主键、索引、金额精度、币种和公共审计字段。
2. 固化客资查重与激活协议、字典值、权限点、错误码和命令接口。
3. 设计支付单、渠道回调、自动建单、订单资金分配、客户账户账本及特殊支付异常审批边界。
4. 确认产品能力的公开规则结果契约；ZSJOS 只保存稳定引用和快照，不设计或直查产品内部表。
5. 实现订单审批轮次、BPM 双会签任务结果事件及业务事务不变量，再接报名服务和服务关系。
6. 接入首次销售转化、复购和按订单项精确退款。
7. 设计历史数据盘点与迁移，不从旧混合状态直接猜测业务事实。

本文不包含 SQL、接口实现或数据库执行授权。

## 15. 验证清单

- [ ] 每个状态只属于一个明确实体和状态字段。
- [ ] 客资中不存在付款、录单、成交跟进或复购状态。
- [ ] 一次表单在未命中时原子创建 Person 与 Lead，命中时只创建 LeadActivation，交叉身份冲突不进入业务表。
- [ ] 同一 Lead 最多一条 `initial_conversion` 机会；复购创建新 Opportunity 且不修改历史对象。
- [ ] 支付单必须绑定既有 Lead 和 SKU，绑定后不可修改；正常流程不存在无归属支付、通用链接或线下转账。
- [ ] 渠道成功回调幂等创建支付流水并自动创建唯一订单草稿，提交人不重新选择支付归属。
- [ ] 财务审批只核对支付与订单一致性，不创建或改变渠道到账事实。
- [ ] 无 Opportunity 订单不得驱动机会成交；有 Opportunity 时订单与机会 Person 必须一致。
- [ ] 每轮审批只创建一个新的 BPM 流程实例，并由已部署流程定义产生报名服务中心和财务两条并行任务。
- [ ] 产品规则要求双会签时两条审批都通过订单才生效；机会和报名服务只在各自条件满足时联动。
- [ ] 任一驳回会关闭本轮未完成任务并创建提交人补正任务。
- [ ] 主动撤回将当前轮次取消并使订单返回草稿；正式取消订单才终止交易。
- [ ] 补正或撤回后重提创建全新轮次、快照和 BPM 流程实例，不复用旧结果。
- [ ] 报名完成只为固化产品规则要求持续服务的订单项创建服务关系。
- [ ] 预充值使用 Person 独立账户和只追加账本，余额只供本人跨订单使用且不能直接提现或转移。
- [ ] 退款通过 RefundItem 精确关联订单项和适用的服务关系。
- [ ] 线下动作允许按实际发生时间补录，系统不限制实际操作。
- [ ] 状态转换、权限、幂等和并发规则不依赖字典标签。
- [ ] 所有业务实体、Mapper、Service 和事务均属于 `yudao-module-zsjos`，不存在 CRM 表或 CRM Service 运行时依赖。
- [ ] 所有业务外键引用 ZSJOS 自有主键，外部系统 ID 只存在于 `external_*` 字段。
- [ ] 当前实现映射只出现在迁移附录，不改变目标模型。
- [ ] 数据迁移前能够识别无法可靠拆分的历史记录并转人工核验。

## 16. 尚待实施阶段确认

以下问题不影响当前领域拆分，但在对应数据库和接口设计前必须单独确认，本文不预设答案：

- 产品模块或产品能力的实际所有者、物理表、SKU 规则结构、版本协议和公开 API。
- 特殊支付异常的业务对象、审批状态、权限、补偿动作和与 BPM 的具体集成方式。
- 哪些 `Lead.status` 允许创建支付单，以及已关闭、无效或已转换客资被再次激活后允许发起哪些后续业务动作。
- 同一 Person 存在多条历史 Lead 时，LeadActivation 关联哪一条当前 Lead；激活本身是否以及如何影响历史终态。
- `Person.identity_status`、`Partner.status` 的稳定协议值和合法转换。
- 现有 `zsjos_lead_source_platform` 字典与本文 `source_channel_id` 的统一口径、稳定值和数据所有者。
- 预充值余额退款是退回原支付渠道、退回账户余额还是按业务类型选择，以及管理员调账的审批规则。
- 报名服务完成所需的最低记录项由哪一项业务配置提供。
- 兼职主体的结算信息及结算记录使用哪些独立 ZSJOS 子表。
- 金额精度、币种范围、支付链接有效期、支付渠道能力和退款渠道执行契约。

## 17. 客资三级申诉状态机（V015）

客资首次被销售判定无效后，提交人可在无时限条件下手动发起最多三轮独立申诉。申诉处理中 `Lead.status` 始终为 `invalid`；每轮均保存当时无效原因、说明和图片快照。

```text
invalid + 第1次申诉 -> sales_manager_reviewing -> overturned | upheld
invalid + 第2次申诉 -> quality_reviewing      -> overturned | upheld
invalid + 第3次申诉 -> chairman_reviewing      -> overturned | upheld(最终无效)
```

`upheld` 表示本轮维持无效，只有第三轮的 `upheld` 才是最终结论；第三轮之后禁止第四次申诉。改判有效统一复用“客资判有效”的后续转换入口，并清理当前无效展示字段，历史事件与申诉记录不变。每次提交和裁决都要求幂等键、必填理由和最多 9 张 JPG/PNG/WebP 图片；第二、三轮不会自动升级，必须由提交人重新提交。
