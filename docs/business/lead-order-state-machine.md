# 客资、机会、订单与学生服务全生命周期模型

> 文档性质：目标领域模型、状态协议与数据流转契约  
> 适用对象：产品、研发、测试、数据人员、系统管理员和 AI 代理  
> 目标版本：新业务模型 V1  
> 状态表达：中文名称用于展示，英文 `snake_case` 代码用于接口与持久化  
> 实施说明：本文描述目标设计，不代表当前数据库、接口和页面已经实现

需求来源包括《客资和订单字段》原始字段清单以及本轮已确认业务流程；两者冲突时，以本轮已确认业务决策和本文明确替代关系为准。

支付一期只保留一个实现来源：[ZSJOS 通联支付一期开发实施文档](./payment-module-development.md)。购买意向、通联报文、支付确认、手工补录订单、成交链路八态投影及整单全额退款均以该文档为准；本文只描述它们与客资、订单、BPM、报名和服务生命周期的关系。

提交人补充、每日催促和销售投诉是客资主状态之外的审计动作。它们不改变客资、商机或订单状态；无效、关闭和已成交客资不接受新的提交人动作。投诉成立仅产生销售及直属主管通知，不触发自动处罚、回收、停派或绩效变更。
提交人补充、每日催促和销售投诉是客资主状态之外的审计动作。它们不改变客资、商机或订单状态；无效、关闭和已成交客资不接受新的提交人动作。投诉成立仅产生销售及直属主管通知，不触发自动处罚、回收、停派或绩效变更。

## 1. 文档结论

旧模型把“客资状态、销售成交进度、付款、录单、报名和财务审核”放在客资或订单的一条状态线上，无法正确表达复购，也无法保留每次购买的独立历史。新模型必须拆成相互关联、各自负责的业务对象：

| 业务问题 | 归属对象 |
| --- | --- |
| 谁提交了线索、线索是否有效、分给谁 | 客资 `Lead` |
| 已有客户再次提交了什么、为何重新进入跟进 | 客资激活 `LeadActivation` |
| 当前正式销售转化或复购意向由谁跟进、成功或流失 | 销售机会 `Opportunity` |
| 本次首购或复购在订单创建前后属于哪条独立交易链路 | 购买意向 `PurchaseIntent` |
| 本次买了什么、金额和提交资料是什么 | 订单 `Order`、订单项 `OrderItem` |
| 为哪次购买、哪些 SKU 发起收款，渠道是否支付成功 | 支付意向 `PaymentIntent`、支付流水 `PaymentTransaction` |
| 一笔资金如何用于订单，预充值余额如何变化 | 订单资金分配 `OrderPaymentAllocation`、客户资金账户与账本 |
| 报名服务中心和财务是否通过本轮审核 | ZSJOS 审批轮次 `ApprovalRound`、BPM 流程实例与审批任务 |
| 报名节点通过后履约做了什么、留下什么凭证 | 报名服务单 `RegistrationCase`、服务记录 `RegistrationItem` |
| 学员正在享受哪一项服务 | 学生服务关系 `ServiceRelation` |
| 谁需要在何时处理什么 | 业务任务 `BusinessTask` |
| 何时发生了什么业务变化 | 业务事件 `BusinessEvent` |

核心原则：**客资不是订单，机会不是订单，订单生效不是报名服务完成，复购不是修改历史订单。**

## 2. 已确认的业务决策

1. 客资有两类提交方：公司新媒体客资中心员工、兼职人员独立提交端。
2. 提交人只填写一次客资表单。手机号和微信号均未命中时，同一事务创建 `Person` 与 `Lead`；任一标识命中已有客户时不新增客资，而是创建独立 `LeadActivation` 记录并通知当前业务负责人。
3. 手机号和微信号交叉查重指向不同客户时，提交校验不通过，不创建 `Person`、`Lead` 或 `LeadActivation`，由提交人先处理身份信息。
4. 客资判定前的联系记录归 Lead，不提前创建机会；判定有效后才创建 `initial_conversion` Opportunity，后续销售跟进和订单提交归机会。判定有效只表示客资有效，不表示已经成交。
5. 只有当前正式负责人可对已判有效客资直接录入成交订单；公海协同跟进销售必须先完成主管正式转派，转派成功后才取得成交录入资格。
6. 每次首购或复购先建立独立 PurchaseIntent。在线通联支付是现有线下凭证和经授权零元订单之外的可选路径，不是所有订单提交的前置条件。
7. 销售从全部启用 SKU 中选择成交课程，PurchaseIntent 固化产品、SKU 和金额快照；选择通联支付时生成专属 PaymentIntent，可信到账只更新支付事实并通知责任人补录订单，不自动创建 Order。
8. 补录订单时重新校验业务资格和产品状态；使用通联支付的订单明细汇总金额必须与 PaymentIntent 及成功 PaymentTransaction 完全一致，线下路径继续由财务复核销售提交的付款和凭证业务事实。
9. 本流程的付款信息是销售提交并由财务复核的业务事实，不等同于支付渠道回调。渠道流水必须以独立外部引用关联，不得改写本轮订单快照。
10. 是否需要销售机会、BPM 审批、报名服务和持续学生服务由下单时固化的产品规则结果决定；具体产品和规则模型留给产品模块单独设计。
11. 需要双会签的订单提交后，由 BPM 同时创建报名服务中心和财务审批任务。
12. 两个中心都通过，本轮审批才通过，订单才成为有效订单；只有关联销售机会的正式销售转化订单才同时确认机会成交。
13. 任一中心驳回，本轮立即失败；BPM 关闭本流程未完成的审批任务，ZSJOS 向提交人创建补正任务。
14. 补正后重新提交必须创建 successor Order、其第 1 个 ZSJOS 审批轮次和新的 BPM 流程实例，两个中心都重新审批，不复用上一轮结果。
15. 主动撤回审批会取消当前轮次并终止当前 Order；需要继续交易时通过 successor Order 补正重提。取消整个购买意向表示放弃本次交易，是不同的业务动作。
16. `registrationReview` 首次通过后即幂等创建报名服务单，并发布新报名履约任务通知；订单生效前或财务待补正时可编辑清单。只有订单 `effective`、人工项全部完成、必传附件齐全、至少选择一个流转部门且各部门负责人仍有效时才可完成。完成时固化部门与负责人快照，将 Person 转为学员并按订单明细建立服务关系，再向学习规划师发布可直达该 Person 的分配通知；被分配的学习规划师和编导均在“我的学员”按 Person 去重查看。
17. 报名服务完成后，仅为产品规则要求持续学生服务的订单项创建并激活新的学生服务关系；一次性项目只完成报名或履约记录。
18. 预充值使用独立客户资金账户和只追加账本。余额归 `Person` 本人，可跨本人的多张订单分次使用，不允许转给其他客户或直接提现。
19. 复购从系统客户客资详情或系统外历史客户入口发起，每次复购创建独立 PurchaseIntent，提交时创建新订单；不强制创建复购商机，可按既有业务规则关联。
20. 线下动作可以与系统流程同步或提前发生，系统不限制实际操作；系统允许事后补录实际发生时间和凭证。
21. 审批页面可查看订单全部信息。系统只提供通过和驳回，不规定两个中心具体审核哪些字段。

### 2.1 对旧方案的明确替代

旧的“订单提交 -> 报名办理 -> 财务审核 -> 完成”顺序废止。目标顺序为：

```text
从符合条件的 Lead、Person 或服务关系创建首购/复购 PurchaseIntent
  -> 选择全部启用 SKU 并固化金额与规则快照
  -> 可选：生成专属通联支付链接并等待可信到账
  -> 责任人补录订单；在线支付金额必须完全一致
  -> 创建订单并固化客户、产品、金额和文件快照
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
- 保存购买意向、支付意向、渠道支付事实、订单资金分配、客户账户余额与不可覆盖账本；一期通联适配负责执行渠道动作，但不能成为业务状态所有者。
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
| 购买意向、支付意向、渠道流水引用、订单资金分配、客户资金账户、退款申请和业务审核 | `yudao-module-zsjos` | 独立业务表、账户账本、独立状态和业务事务 |
| 一期通联支付/退款渠道执行 | `yudao-module-zsjos` 的独立通联适配边界 | 仅执行资金动作；不依赖 `yudao-module-pay`，渠道流水号仍是外部引用 |
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
| 首购/复购购买意向 | 建议新增 `zsjos_purchase_intent`；具体迁移另行评审 |
| 订单和订单项 | `zsjos_order`、`zsjos_order_item` |
| 审批轮次、快照和 BPM 关联 | `zsjos_order_approval_round`；不创建重复 BPM 任务状态的 ZSJOS 审批任务表 |
| 报名服务单和服务记录项 | `zsjos_registration_case`、`zsjos_registration_item` |
| 学生服务关系和服务记录 | `zsjos_service_relation`、`zsjos_service_record` |
| 支付意向、渠道事件、渠道交易和订单资金分配 | 现有 `zsjos_payment_order` 作为 PaymentIntent 基线，并评审 `zsjos_payment_gateway_event`、`zsjos_payment_transaction`、`zsjos_order_payment_allocation` |
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
Person 0:N PurchaseIntent
Partner 0:N Lead（仅兼职来源）
Lead 0:1 Opportunity（首次销售转化机会）
Lead 1:N LeadIntendedProduct（提交时课程快照，恰好一个主意向）
Lead 0:N PurchaseIntent（首购）
ServiceRelation 0:N Opportunity（复购来源）
ServiceRelation 0:N PurchaseIntent（复购来源）
Opportunity 0:1 Order（正式销售转化订单）
PurchaseIntent 0:1 PaymentIntent（一期每次购买最多一个支付意向）
PurchaseIntent 0:N Order（驳回重提形成 successor 链，其中最多一个当前订单）
PaymentIntent 0:1 PaymentTransaction（专属支付链接成功后只形成一笔成功渠道流水）
PaymentIntent 0:1 Order（责任人补录时绑定；到账不自动创建，线下或零元订单可无支付意向）
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
- 同一客资最多创建一个 `initial_conversion` 机会。首购 PurchaseIntent 可关联该机会；复购 PurchaseIntent 一期不强制创建 Opportunity。
- 在线支付不是 V023 成交录入的统一前置。每个 PurchaseIntent 同一时刻最多一个当前 Order；同一客资或 Person 的活动购买并发继续受既有订单和购买意向约束。
- 驳回或主动撤回不复活原订单：重提事务创建独立 successor 订单，原订单转为 `superseded` 并保留审批、明细、凭证和原因审计；取消整个购买意向才表示放弃本次交易。
- 复购从已有客户发起新订单，不修改原客资、原机会、原订单和原服务关系；客资只可作为发起上下文。
- 一张订单包含多个购买服务时，只为产品规则要求持续服务的订单项创建服务关系；每条关系必须能追溯到订单项。

## 7. 实体字段设计

以下是目标逻辑字段。`id`、`tenant_id`、创建人、创建时间、更新人、更新时间和逻辑删除等框架公共字段不在每张表重复列出。需要并发保护的可变业务表必须显式增加 `version`；只追加的审计、事件和账本表不依赖覆盖更新。

### 7.1 人员/学员身份 `Person`

`Person` 是 ZSJOS 自己维护的客户与学员身份主档，不引用 CRM Customer 作为主键来源。它承载查重所需的手机号、微信号、姓名等身份信息，以及合并、拆分和联系方式变更历史。至少包含 `id`、`person_no`、`name`、`mobile`、`wechat_id`、`identity_status`、`first_seen_at`、`last_seen_at`；敏感字段按系统安全规则脱敏展示。新建 Person 时，`person_no` 按 `XYyyyyMMddHHmmss` 加租户在北京时间自然日内的四位循环序号生成，每天从 `0001` 开始，`9999` 后回到 `0001`；历史 `P + UUID` 编号不回填、不重编。查重不得直接覆盖历史身份，合并必须追加 `PersonMergeEvent`。

### 7.2 兼职提交主体 `Partner`

`Partner` 是 ZSJOS 自己维护的兼职提交主体，不使用 CRM 联系人或临时文本代替。至少包含 `partner_no`、`name`、`mobile`、`status`、`bound_system_user_id`、`channel_id`、`enabled_at`、`disabled_at`。兼职端登录账号仍由系统身份能力认证，但账号与业务主体的绑定关系由 ZSJOS 保存。

### 7.3 客资 `Lead`

| 字段 | 类型建议 | 必填 | 含义 |
| --- | --- | --- | --- |
| `id` | bigint | 是 | 内部主键；继续用于外键、URL、权限和事件关联，不作为用户可见客资编号 |
| `lead_no` | varchar(32) | 是 | 用户可见客资业务编号；`KZ` + 北京时间 `yyyyMMddHHmmss` + 租户当日四位循环序号，`9999` 后回到 `0001` |
| `person_id` | bigint | 是 | 关联 ZSJOS `Person`；新客资与新 Person 在同一事务创建，命中已有 Person 时不创建新 Lead |
| `submitted_name`、`submitted_mobile`、`submitted_wechat_id` | varchar | 是/条件必填 | 本次表单的原始身份快照，用于查重和审计，不因 Person 后续变更而覆盖 |
| `source_type` | varchar | 是 | `internal_new_media` 或 `part_time_partner` |
| `source_user_id` | bigint | 条件必填 | 内部提交员工；必须取当前登录账号，不能由前端伪造 |
| `partner_id` | bigint | 条件必填 | 兼职提交主体标识 |
| `source_channel_id` | bigint/varchar | 否 | 更细的业务来源，来自业务配置或字典 |
| `province_code`、`province_name`、`city_code`、`city_name` | varchar | 新提交必填 | 提交时省市及名称快照；标准编码为数字字符串，“其他”为 `OTHER` |
| `lead_category`、`lead_category_label_snapshot`、`remark` | varchar | 新提交是/否 | 客资分类字典值、选择时标签快照与备注；后续明确改选分类时生成新快照，管理员修改字典不回写历史标签 |
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

提交人身份必须满足二选一：内部来源填写 `source_user_id`，兼职来源填写 `partner_id`。提交先执行统一查重：手机号或微信号同字段命中任何历史客资均为强重复，直接拒绝并保存强重复审计；历史状态不参与强重复过滤，已关闭、已判无效、已成交、公海、挂起、回收、待跟进和跟进中客资都会拦截。交叉联系方式、姓名+明确省市+明确主意向课程、姓名+手机号后四位命中时进入疑似重复复核；完全无命中才在同一事务创建 Person 与 Lead。

`lead_no` 在实际创建 Lead 的事务内分配。每个租户按北京时间自然日独立计数，从 `0001` 开始，递增到 `9999` 后重新从 `0001` 开始。幂等重试返回已创建 Lead 的既有编号；强重复和疑似重复复核任务本身不预占编号。所有持久化关联继续使用 `id`。同一租户同一秒内若循环到已使用的四位序号，租户唯一索引会拒绝重复编号。

新提交必须至少选择一个启用课程、同一课程不可重复且恰好一个主意向。稳定产品引用与提交时名称保存在 `zsjos_lead_intended_product`；最多九张图片的 Infra 文件编号和提交时元数据快照保存在 `zsjos_lead_attachment`。附件保持私有读，只有在对象读取权限校验通过后才通过 Infra 公共 API 生成短期访问地址。强重复与疑似重复均在 `zsjos_lead_duplicate_review` 保存查重记录；疑似重复保存完整提交资料、命中规则、候选对象和复核 fingerprint，强重复保存拦截审计。

### 7.4 重复客资复核

`LeadActivation` 仅保留阶段二上线前的历史兼容数据，新提交不再写入。新复核任务永久保存提交快照、命中规则、候选快照、`duplicate_flag`、`duplicate_result`、首要规则、fingerprint、处理人、处理时间、结构化结论、意见、附件及处理前后值。公共队列提交结论时锁定任务，第一位处理人成功。

管理员只支持 `allow_flow` 与 `close_duplicate`。放行后清空本次重复待处理含义，按首次待复核提交快照创建正式 Lead，再进入自动分配、指定销售或销售自拓归属流程；关闭后仅完成复核记录，不创建 Lead、不分配、不计入业绩。相同疑似重复 fingerprint 的后续提交复用最早待处理复核任务，不覆盖首次提交人、来源、快照或派单依据。联系方式修改只在完全无命中时成功，任何疑似命中都取消修改且不进入复核。

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
| `lead_id` | bigint | 条件必填 | 仅首次销售转化来源客资，且同一 Lead 最多一条；复购必须为空 |
| `source_service_relation_id` | bigint | 条件必填 | 复购来源服务关系；客户级复购无法指定时可为空 |
| `previous_order_id` | bigint | 否 | 直接关联的上一张订单，便于追溯 |
| `status` | varchar | 是 | 机会生命周期状态 |
| `owner_user_id` | bigint | 是 | 当前成交责任人 |
| `expected_product_summary` | json/varchar | 否 | 意向产品摘要，不替代正式订单项 |
| `next_follow_up_at` | datetime | 否 | 提醒时间，不是状态 |
| `shelved_until`、`shelved_reason` | datetime/varchar | 条件必填 | 暂缓期限和原因 |
| `won_at`、`lost_at`、`lost_reason` | datetime/varchar | 条件必填 | 结果事实 |

首次销售转化机会由销售转化中心负责。复购一期由学生服务与交付中心从 Person 或现有服务关系发起 PurchaseIntent，不强制创建复购机会。`initial_conversion` 不表示 Person 的第一次付款。责任部门不能仅按岗位或部门中文名称推断，应由权限和业务分配关系确定。

### 7.8 订单 `Order` 与订单项 `OrderItem`

| 字段 | 类型建议 | 必填 | 含义 |
| --- | --- | --- | --- |
| `purchase_intent_id` | bigint | 是 | 本次首购或复购的稳定购买链路；successor Order 沿用 |
| `lead_id` | bigint | 条件必填 | 首购订单来源客资；必须与 PurchaseIntent 和 PaymentIntent 绑定客资一致，复购可为空 |
| `source_payment_order_id` | bigint | 条件必填 | 兼容现有物理命名；在线支付补录订单时引用 PaymentIntent 且唯一，线下或零元订单为空 |
| `opportunity_id` | bigint | 条件必填 | 正式销售转化订单必须关联且一对一唯一；产品规则不要求销售机会时可为空 |
| `person_id` | bigint | 是 | 与 Lead 的 `person_id` 一致；存在机会时还必须与机会 `person_id` 一致 |
| `order_no` | varchar | 是 | 全局或租户内唯一业务编号 |
| `status` | varchar | 是 | 订单主状态 |
| `submitter_user_id` | bigint | 条件必填 | 本次订单提交人，补正任务接收人 |
| `submitter_center_type` | varchar | 是 | `sales_conversion` 或 `student_delivery` |
| `total_amount`、`discount_amount`、`payable_amount` | decimal | 是 | 本次交易金额事实 |
| `contract_refs` | json/关联表 | 否 | 合同或附件引用 |
| `current_approval_round_id` | bigint | 否 | 当前审批轮次引用；未提交审批或产品规则不要求审批时为空 |
| `submitted_at`、`effective_at`、`terminated_at` | datetime | 条件必填 | 关键业务时间 |
| `termination_reason` | varchar | 条件必填 | 生效前主动终止或流程取消原因 |

`OrderItem` 至少包含 `order_id`、`product_id`、`sku_id`、`quantity`、`unit_price`、`discount_amount`、`payable_amount`、`product_snapshot`、`product_rule_snapshot`、`product_rule_version`。产品规则快照记录当时已经解析的审批、报名和持续服务规则结果，创建后不可修改；具体规则结构留给产品模块设计。后续服务关系必须引用形成它的订单项。

新流程在正式提交时才创建 Order，不落 `order.status=draft`；提交前的可编辑交易草稿由 PurchaseIntent 承担。责任人从 PurchaseIntent 补录并提交成交订单，订单总额由订单项实际成交金额汇总。在线支付路径必须与 PaymentIntent 及成功流水金额完全一致；线下和零元路径继续遵循既有凭证与授权规则。订单应展示本次提交的完整信息。模型不增加“报名审核字段清单”或“财务审核字段清单”。财务点击通过表示对支付归属、订单金额、产品和凭证信息完成复核，不产生支付渠道到账事实。

### 7.9 审批轮次 `ApprovalRound` 与 BPM 流程

`ApprovalRound`：

| 字段 | 类型建议 | 必填 | 含义 |
| --- | --- | --- | --- |
| `order_id`、`round_no` | bigint/int | 是 | 同一 Order 内唯一；successor Order 从第 1 轮开始 |
| `status` | varchar | 是 | 本轮汇总状态 |
| `order_snapshot` | json | 是 | 本轮提交时的完整订单快照 |
| `process_instance_id` | varchar | 是 | 当前轮次关联的 BPM 流程实例，租户内唯一 |
| `process_definition_key` | varchar | 是 | 本轮使用的稳定 BPM 流程定义 Key |
| `submitted_by`、`submitted_at` | bigint/datetime | 是 | 本轮提交事实 |
| `completed_at` | datetime | 否 | 本轮通过、驳回或终止时间 |
| `rejected_bpm_task_id` | varchar | 否 | 导致本轮失败的 BPM 审批任务引用 |

`ApprovalRound` 是 ZSJOS 业务审计对象，不是工作流任务副本。每轮使用独立
`processInstanceId`，订单 ID 或轮次 ID 作为稳定 `businessKey`。报名服务中心与财务
两条并行任务、任务处理人、审批意见、附件、通过、驳回、取消和历史均由 BPM
持有。ZSJOS 通过 BPM 公共 API 发起流程，并通过按流程定义 Key 过滤的事件监听器
幂等更新轮次、订单、机会和相关业务状态。部分任务通过仍保持 `ApprovalRound.status=pending`，具体通过方和待办方从 BPM 当前流程事实读取。为支持财务节点核对结果、主管加签和驳回任务引用，BPM 公共任务结果事件必须至少提供流程实例 ID、任务 ID、任务定义 Key、动作、处理人、发生时间和幂等事件 ID。

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

客资仍为 `submitted` 且已经归属时，销售可以追加 `LeadFollowUpRecord`。新增记录不改变 Lead 主状态；当前归属周期首次记录完成 `lead_first_follow_up`，并以该首次跟进成功时间为起点创建 `lead_qualification` 任务，截止时间为起点加当时启用规则的 `qualification_timeout_minutes`。判定任务按客资和轮次幂等，固化创建时启用规则的编号、版本、时限及截止时间；后续规则修改不追溯已有轮次。首次跟进完成前只有首次跟进截止时间，不能展示有效性判定截止时间。可选的下次跟进时间创建或替换 `lead_follow_up_reminder`。记录只追加，方式、结果和分类标签均固化快照。

判定有效在同一事务内完成判定任务、保存必填有效备注、创建或恢复唯一 `initial_conversion` Opportunity，并让 Lead 保持 `valid + owned`。之后的跟进写入 Opportunity 跟进记录，并维护机会状态和提醒；判无效会同时取消待处理的首跟、判定和跟进提醒任务，并把未结束 Opportunity 改为 `lost`。`V034` 负责取消规则上线前的历史遗留记录。无效 Lead 仍允许当前负责人追加证据型跟进，但不创建首跟、判定或提醒任务。

### 7.13 业务事件 `BusinessEvent`

事件日志只追加不覆盖，至少包含 `event_type`、`aggregate_type`、`aggregate_id`、`operator_user_id`、`from_status`、`to_status`、`reason`、`evidence_refs`、`related_object_refs`、`occurred_at`、`idempotency_key`。状态字段只表达当前值，事件负责解释“为什么变成现在这样”。

### 7.14 购买意向、支付意向、渠道流水与订单资金分配

`PurchaseIntent` 是一次首购或复购在 Order 创建前后的稳定链路锚点；`PaymentIntent`、`PaymentGatewayEvent`、`PaymentTransaction` 和 `OrderPaymentAllocation` 分别表达收款意图、渠道事件、可信资金事实和订单资金归属。字段、状态、幂等及异常处理不在本文重复定义，统一执行支付一期开发实施文档。

### 7.15 客户资金账户与账本

`CustomerAccount` 每个 Person、币种最多一条，至少包含 `person_id`、`currency`、`available_balance`、`version`。余额只供本人跨订单分次使用，不允许转给其他 Person 或直接提现。

`CustomerAccountLedger` 只追加不覆盖，至少包含 `account_id`、`entry_type`、`amount`、`balance_before`、`balance_after`、`payment_transaction_id`、`order_id`、`refund_case_id`、`occurred_at`、`operator_user_id`、`idempotency_key`。购买预充值产品并支付成功时增加余额；订单使用余额时原子扣减并写入消费流水。Person 页面展示账户余额，但 Person 主表不保存可直接修改的余额字段。

### 7.16 退款申请 `RefundCase` 与退款明细 `RefundItem`

`RefundCase` 是已生效订单的 ZSJOS 退款业务审批聚合。需要审批时由 BPM 执行退款流程，ZSJOS 消费流程结果并负责申请业务状态、资金及服务关系联动；审批通过只授权渠道执行，不代表资金已经退回。

支付一期的退款范围、通联执行和成功判定统一执行支付一期开发实施文档。

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
| 支付意向状态 | `zsjos_payment_order_status` | `payment_intent.status`；字典类型名暂保留物理兼容 | 持久化状态 |
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
| `zsjos_lead_status` | 50 | 已成交 | `won` | success | 首次销售订单审批通过并生效，机会与客资均已成交 |
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
| `zsjos_order_status` | 10 | 审批中 | `pending_approval` | warning | 当前轮次关联的 BPM 双会签或主管加签流程处理中 |
| `zsjos_order_status` | 20 | 待补正 | `revision_required` | danger | 当前轮次被驳回，等待创建 successor Order |
| `zsjos_order_status` | 30 | 已生效 | `effective` | success | 已满足固化产品规则要求的生效条件 |
| `zsjos_order_status` | 40 | 已被接续 | `superseded` | default | successor 已创建，本 Order 仅保留历史 |
| `zsjos_order_status` | 50 | 已终止 | `terminated` | default | 主动终止或流程取消，历史仍保留 |
| `zsjos_approval_round_status` | 10 | 待审批 | `pending` | warning | BPM 双会签或主管加签尚未完成；部分任务通过仍保持该值 |
| `zsjos_approval_round_status` | 30 | 已通过 | `approved` | success | 本轮所需审批方均已通过 |
| `zsjos_approval_round_status` | 40 | 已驳回 | `rejected` | danger | 任一必要审批方驳回，本轮结束 |
| `zsjos_approval_round_status` | 50 | 已终止 | `terminated` | default | 主动终止或流程取消，本轮结束 |
| `zsjos_registration_case_status` | 10 | 待处理 | `pending` | warning | 报名节点通过后已创建报名服务单 |
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
| `zsjos_payment_order_status` | 10 | 已创建 | `created` | info | 专属支付链接已创建，尚未成功向具体通联产品下单 |
| `zsjos_payment_order_status` | 20 | 等待支付 | `waiting` | warning | 已向选定通联产品下单，等待客户支付或渠道确认 |
| `zsjos_payment_order_status` | 30 | 已支付 | `paid` | success | 可信渠道结果已确认到账并生成唯一成功流水 |
| `zsjos_payment_order_status` | 40 | 已过期 | `expired` | default | 超过有效期且未支付，不再接受付款 |
| `zsjos_payment_order_status` | 50 | 已关闭 | `closed` | default | 支付成功前经授权关闭并取得适用的可靠关单结果 |
| `zsjos_refund_case_status` | 10 | 草稿 | `draft` | default | 退款申请尚未提交 |
| `zsjos_refund_case_status` | 20 | 审核中 | `pending_review` | warning | 退款申请等待授权审核 |
| `zsjos_refund_case_status` | 30 | 已批准 | `approved` | primary | 退款申请获批，等待渠道执行 |
| `zsjos_refund_case_status` | 40 | 已拒绝 | `rejected` | danger | 当前退款申请未获批准 |
| `zsjos_refund_case_status` | 50 | 退款处理中 | `processing` | warning | 已向外部渠道或财务发起资金退款 |
| `zsjos_refund_case_status` | 60 | 部分退款 | `partially_refunded` | warning | 后续能力预留，非通联支付一期可达状态 |
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
| 判定有效 | `submitted + owned + 待判定` | `valid + owned` | 完成判定任务，保存有效备注并原子创建唯一 `initial_conversion` Opportunity；不创建订单，`converted_at` 仅为历史兼容字段，不作为成交时间 |
| 判定无效 | `submitted + owned + 待判定` / `converted` | `invalid` | 原因字典值与说明均必填；有效后判无效还会把 Opportunity 改为 `lost`，并保留销售归属和证据跟进入口 |
| 判定超时扫描 | `submitted + owned + 判定截止已到` | `suspended + owned` | 行锁下重新校验；扫描提交前仍允许人工判定 |
| 恢复原销售 | `suspended + owned` | `submitted + owned` | 校验原销售仍启用，跳过新首跟并创建新判定轮次 |
| 转派 | `suspended + owned` / `recycle_pending` | `submitted + owned` | 新销售直接进入待判定并重新计时；挂起转派不得选择原销售 |
| 回收 | `suspended + owned` | `submitted + recycle_pending` | 清除当前销售并保留回收来源销售 |
| 释放到抢单池 | `suspended + owned` / `recycle_pending` | `submitted + public_pool` | 被抢后重新进入待首跟 |
| 申诉改判 | `invalid` | `valid + owned` | 结束申诉，原子创建唯一 `initial_conversion` Opportunity，并以裁决理由保存有效备注；不代表订单成交 |
| 创建首次销售转化机会 | `submitted + owned + 待判定` | `valid` | 与判有效合并为同一事务，分配状态保持 `owned`；机会创建不等于订单成交 |
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

### 9.3 购买意向、支付成功与手工补录订单

在线支付是线下凭证和授权零元订单之外的可选路径。可信到账只通知 PurchaseIntent 当前责任人补录订单，不自动创建 Order，也不提前驱动 Lead 或 Opportunity 成交；完整收款、确认和补录门禁统一执行支付一期开发实施文档。

### 9.4 订单提交与产品规则分支

首次提交必须校验 PurchaseIntent 尚无当前 Order；补正提交必须校验原 Order 为 `revision_required` 或已授权终止且可重提。两者都要校验产品和规则快照完整、在线支付金额完全一致，或满足线下凭证/授权零元订单规则；存在关联机会时还必须校验机会未结束。

- 产品规则要求双会签时，进入 9.5 的 BPM 流程。
- 产品规则不要求双会签时，在一个事务中将订单改为 `effective`；存在关联 Opportunity 时同时改为 `won`；产品规则要求报名服务时创建唯一报名服务单，否则只写入生效和后续履约事件。
- 产品规则的定义、配置字段和组合方式不在本文设计；领域服务只能执行订单项中已经固化的规则结果。

### 9.5 订单提交与 BPM 双会签

提交订单必须在一个事务中完成：

1. 执行 9.4 的通用提交校验，并确认固化规则要求双会签。
2. 固化完整 `order_snapshot`。
3. 为本次新 Order 创建第 1 个 `ApprovalRound.status.pending`。
4. 使用稳定流程定义 Key 和本轮业务 Key 调用 BPM 公共 API，启动包含报名服务中心与财务两条并行任务的流程实例。
5. 将 BPM `processInstanceId` 写入本轮，并更新 `Order.status.pending_approval` 和 `current_approval_round_id`。
6. 若是补正重提，完成对应补正任务。
7. 写入提交事件；审批待办和流程提醒由 BPM 提供。

提交接口必须接受幂等键。同一订单、同一业务请求重复调用不得创建多个轮次或 BPM 流程实例。流程定义缺失、未部署或启动失败必须返回可区分错误，且不得留下仅更新一侧的半成品状态。

### 9.6 审批通过与驳回

| BPM 当前动作 | 本轮另一 BPM 任务 | ZSJOS 轮次结果 | 订单结果 |
| --- | --- | --- | --- |
| 第一个任务通过 | `pending` | 保持 `pending` | 保持 `pending_approval` |
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

主动终止或取消审批时，必须先通过 BPM 取消当前流程实例并关闭未完成任务，再幂等地结束当前轮次和当前 Order；已支付事实保持不变。当前成交链路投影为“已终止”，而不是把 Order 复活为草稿。

补正不是复活旧 BPM 流程或任务。提交人修改后重新提交，必须创建独立 successor Order 及其第 1 个新快照、新 BPM 流程实例；旧 Order 进入 `superseded`，PurchaseIntent 原子指向 successor，上一轮结果仅供审计，不参与当前聚合。

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

当前交付范围内复购不强制创建新商机，可按既有业务规则关联。每次复购先创建关联 Person 的独立 PurchaseIntent，责任人按线下/零元路径或通联可信到账事实补录新订单和审批轮次。原客资、原商机、原购买意向、原订单及首次成交时间保持不变。

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

退款不得删除或回退 Order、服务关系和 BPM 的历史事实。已生效订单先完成 RefundCase BPM，资金执行范围、通联协议和最终展示状态统一执行支付一期开发实施文档。

## 10. 数据不变量与约束

1. 一次全新客资提交必须原子创建 `Person + Lead`；命中已有 Person 时只创建 LeadActivation；交叉身份冲突不得写入三者中的任何一个。
2. 同一 Lead 最多一条 `Opportunity.type.initial_conversion`；复购 PurchaseIntent 不得重新打开历史客资，一期不强制创建复购 Opportunity。
3. 每次首购或复购必须有独立 PurchaseIntent。首购绑定既有 Lead 且 Person 必须一致；复购绑定 Person 和适用来源，创建后不得改绑业务主体。
4. 同一 PurchaseIntent 一期最多一个活动 PaymentIntent；旧单失效或关闭后不复活，但同一 PurchaseIntent 可以重新生成新的 PaymentIntent。一个 PaymentIntent 最多一笔成功 PaymentTransaction 和一笔整单全额 PaymentRefund。
5. 在线支付到账不自动建单。责任人补录的 Order 必须关联 PurchaseIntent；在线路径同时唯一关联 PaymentIntent，支付回调事件、渠道流水号和补录命令都必须幂等。
6. `Order.lead_id` 必须与首购 PurchaseIntent 和 PaymentIntent 一致；存在 Opportunity 时，`Opportunity.person_id = Order.person_id`，且一个首次转化机会最多一条当前订单链。无机会订单不得驱动任何机会为 `won`。
7. 产品、SKU 和产品规则快照在 PurchaseIntent、PaymentIntent、订单项或审批快照固化后不可修改。状态机只执行固化规则，不按名称、价格或展示标签推断规则。
8. 产品规则要求 BPM 双会签的订单，只有当前轮次流程通过后才能进入 `effective`；规则不要求审批的订单可按 9.4 的直接生效事务进入 `effective`。
9. 双会签流程必须包含报名服务中心和财务两个并行审批方；审批任务及其唯一性由 BPM 保证，ZSJOS 通过任务结果公共事件维护轮次汇总状态。
10. 每次补正重提创建 successor Order、第 1 个新轮次和新 BPM 流程实例；新旧 Order 双向关联，旧 Order 进入 `superseded` 且不可再次重提，PurchaseIntent 只能指向当前 successor。
11. 审批轮次的 `order_snapshot` 和 BPM 关联标识创建后不可修改。
12. 一张订单最多一张报名服务单；报名节点通过后创建，订单生效后才允许完成。
13. 服务关系只能由已完成的报名服务单创建，且只对应固化规则要求持续服务的有效订单项。
14. 一条 PaymentTransaction 的累计渠道资金分配不得超过交易金额；一条账户资金消费的累计分配不得超过对应可用余额，单条分配只能选择一种资金来源。
15. CustomerAccount 当前余额必须等于只追加账本的可核对汇总；充值、消费和退款导致的余额变化必须与账本、资金分配在同一事务完成，余额不得为负。
16. 一期 PaymentRefund 必须等于原 PaymentIntent 全额；部分退款及 RefundItem 拆分留待后续专项设计。
17. 状态改变、事件写入和必要的跨对象联动必须同事务提交；跨事务支付/退款回调、主动查询和补偿采用幂等消费、可靠重试和可运维告警，不允许静默部分成功。
18. 所有命令接口都要验证租户、功能权限、数据范围、对象权限、前置状态和版本；越权与状态冲突返回可区分错误。
19. 历史轮次、激活记录、账户账本、服务记录和业务事件不可物理覆盖或删除；更正通过新增记录表达。
20. 业务请求使用幂等键，消息、通知和提醒采用事务后可靠投递，避免重复业务对象。

## 11. 提醒、展示与计算状态

提醒来自业务任务和时间字段，不增加主状态：

- 待接单：`assignment_status = pending_acceptance`。
- 待首跟：`status = submitted`、`assignment_status = owned` 且当前归属尚无首次跟进。
- 待首跟：`status = submitted`、`assignment_status = owned` 且当前归属周期没有首次跟进，`qualification_deadline_at IS NULL`。
- 待判定：`status = submitted`、`assignment_status = owned` 且当前归属周期已完成首次跟进，存在当前判定截止时间。
- 已挂起：`status = suspended`；销售只读，等待主管处置。
- 回收待处理：`assignment_status = recycle_pending`；无当前销售。
- 审批待办：来自 BPM 当前运行中的审批任务。
- 订单补正：补正任务为 `pending` 或 `processing`。
- 报名待办：报名服务单为 `pending` 或 `processing`。
- 跟进提醒：机会 `next_follow_up_at` 到期且机会未结束。
- 逾期：任务 `due_at < 当前时间` 且任务状态不是 `completed`、`cancelled`。

客资页面使用后端正交投影展示有效状态、跟进状态、分配状态和挂起控制状态，并使用 `availableActions` 决定头部写操作。前端不得依据字段组合自行推断阶段或按钮。`availableActions` 仅向当前负责人投影：首次跟进前为修改基础信息、跟进；首次跟进后增加判有效、判无效；已判有效且跟进中的客资为修改基础信息、跟进、判无效和禁用的录入成交；无效后仅保留跟进；其他状态和查看者为空。该投影只读且不反向驱动业务。

### 11.1 客资收件箱筛选投影

“我的客资”收件箱使用服务端返回的一级业务归类和二级当前环节，不在前端维护静态筛选数组，也不新增可反向驱动业务的客资状态字段。筛选配置、计数和分页查询必须使用相同的数据权限范围和已发布状态映射。

租户管理员在“工作台 → 客资筛选方案”分别维护提交人视角和负责人视角。配置分为草稿与已发布版本：保存草稿不改变员工视图，发布后才切换运行时映射，每次发布保留不可变版本；回滚会把历史快照作为一个新的版本重新发布，不覆盖历史记录。

管理员可以调整分组和筛选项的名称、顺序、显隐，以及从系统白名单中选择条件和值；不能修改 Lead 真实状态、填写 SQL 或引用未注册字段。当前白名单为 `status` 和 `assignment_status`。新增跟进、成交审核等筛选能力时，必须先完成对应业务对象和查询字段，再由后端扩展白名单。

初始化时发布的默认一级归类为：

| 归类编码 | 展示名称 | 当前映射 |
| --- | --- | --- |
| `all` | 全部客资 | 当前视角下属于当前用户的全部客资 |
| `pending_qualification` | 待判定客资 | `lead.status = submitted` |
| `valid` | 有效客资 | `lead.status in (valid, won)` |
| `invalid` | 无效客资 | `lead.status = invalid` |
| `closed` | 已关闭客资 | `lead.status = closed` |

统一客资管理不再按提交人/负责人拆分页面，但保留一行互斥的简单状态标签。标签条件由后端在 `relationScope=all` 的授权关系并集内执行：待首跟与待判定分别对应当前归属周期的判定截止时间为空/非空；待跟进对应已判有效且 Opportunity 未进入成交审批或成交；其余标签直接对应成交待审核、已成交、已判无效、已关闭和已挂起状态。待分配、待接单和抢单池属于分配流程，不进入该标签行。简单状态可与关键词和高级筛选取交集，不改变对象权限。

提交人默认可以继续按 `assignment_status` 筛选待分配、待接单、抢单池、回收待处理和已归属；负责人默认显示归属自己的客资，并使用服务端正交状态区分有效性、跟进和挂起。新判有效或申诉改判均创建唯一 `initial_conversion` Opportunity；V019 负责补齐历史 `valid` 客资。成交审核和已成交仅作为预留跟进状态，由未来订单/BPM投影驱动；本期不提供暂缓成交入口。

## 12. 完整场景

### 12.1 全新客资与首次销售转化

1. 新媒体员工或兼职人员一次提交姓名、手机号、微信号和来源信息。
2. 手机号和微信号均未命中，系统在同一事务创建 Person 和 Lead；判定有效并完成分配后，销售创建唯一 `initial_conversion` 机会，客资变为 `converted`。
3. 销售从全部启用 SKU 中选择成交课程，创建关联 Lead、Person 和 `initial_conversion` Opportunity 的首购 PurchaseIntent，并固化产品、金额和规则快照。
4. 销售可以按现有线下凭证或授权零元路径补录订单，也可以生成专属 PaymentIntent；通联可信到账后只通知责任人补录，不自动创建 Order。
5. 责任人补录时重新校验业务资格、产品快照和金额；在线支付路径还必须与 PaymentIntent 和成功 PaymentTransaction 金额完全一致。
6. ZSJOS 在同一事务创建订单、订单项和不可变审批快照，并启动报名履约中心与财务结算中心双会签。
7. 两个中心都通过后订单生效；任一中心驳回则当前 Order 终止，重提时创建独立 successor Order、第 1 个审批轮次和新的 BPM 实例。
8. 报名节点通过时幂等创建报名服务单；订单生效时，存在关联机会则机会变为 `won`，并解除报名履约最终完成门禁。
9. 报名服务中心补录实际动作、时间和凭证并完成服务单。系统只为规则要求持续服务的订单项创建并激活服务关系，一次性项目不创建。学习规划师完成学习计划后直接进入常规督学，不设置入群或教研对接阶段。

### 12.2 已有客资再次激活

1. 提交表单中的手机号或微信号命中同一已有 Person。
2. 系统不新增 Person 或 Lead，创建独立 LeadActivation，保存来源、提交人、表单快照和激活时间。
3. 有活动服务关系时通知学生服务负责人；否则有未结束机会时通知机会负责人；否则通知客资负责人或进入销售分配。
4. 客资激活本身不生成支付链接。后续出现独立购买需求时，业务人员从该 Person 及适用的 Lead、服务关系或上一订单创建新的 PurchaseIntent，再选择线下/零元补录或通联在线支付路径。

### 12.3 驳回、撤回与补正

1. 财务或报名服务中心驳回第一轮。
2. BPM 关闭同流程未完成任务，ZSJOS 将订单改为 `revision_required` 并向本轮提交人派发补正任务。
3. 成交链路投影为“已终止”，PaymentIntent 和到账事实不回退。
4. 提交人补正后重提，ZSJOS 在同一 PurchaseIntent 下创建 successor Order、其第 1 个审批快照和新的 BPM 流程实例，旧 Order 转为 `superseded`。
5. 第一轮任何已通过结果不复用；successor 的两个节点均通过后订单生效。

主动撤回与驳回的原因不同，但版本规则相同：提交人撤回时，BPM 取消当前流程，轮次和当前 Order 均变为 `terminated`，成交链路显示“已终止”；继续交易必须创建 successor Order。取消整个 PurchaseIntent 才表示放弃本次交易。

补正任务仅对应明确业务驳回，包括报名履约驳回、财务驳回和主管驳回导致整轮驳回。任务类型为 `sales_order_revision`，无截止时间，只派给本轮提交人；正式负责人代为重提成功也完成该任务。新订单及其第 1 轮 BPM 启动成功后才完成旧补正任务；任一步骤失败时旧订单保持原状态。BPM 异常取消或异常终止不创建任务，功能上线前的历史 `revision_required` 订单不回填。

### 12.4 预充值与余额消费

1. 业务人员从既有 Person 及适用来源创建预充值 PurchaseIntent，选择预充值 SKU；使用在线支付时创建专属 PaymentIntent。
2. 渠道可信支付成功后创建 PaymentTransaction，并按固化产品规则给该 Person 的 CustomerAccount 入账，追加充值账本；回跳页面不作为入账凭证。
3. Person 后续购买其他产品时，可以分次使用本人余额；扣款、消费账本和订单资金分配原子完成。
4. 余额不能转给其他 Person，也不能直接提现；余额退款去向留待支付与退款设计阶段确认。

### 12.5 复购

1. 学生服务与交付中心从 ZSJOS `Person`、现有服务关系或上一订单创建新的复购 PurchaseIntent，一期不强制创建 `repurchase` Opportunity。
2. 本次复购可以按现有线下凭证或授权零元路径补录，也可以生成专属 PaymentIntent；通联可信到账只通知责任人补录 Order。
3. 责任人补录新订单并提交审批；在线支付路径的购买意向金额、支付金额和订单明细汇总金额必须完全一致。
4. 支付、审批、补正、报名和服务分支均执行本次 PurchaseIntent 与订单固化的产品规则。
5. 需要持续服务时创建新的活动服务关系，旧购买意向、旧订单和旧服务关系不变。

## 13. 旧模型迁移映射

本节只用于识别历史数据，不是目标状态定义。

| 旧概念 | 目标归属 | 迁移原则 |
| --- | --- | --- |
| 客资中的首触、跟进、暂缓、流失 | `Opportunity.status` | 按每次购买意向迁入机会，不能继续留在客资 |
| 客资中的一次首购或复购进度 | `PurchaseIntent` | 按能够可靠区分的独立交易链路迁入；无法区分的记录进入人工核验，不按更新时间猜测 |
| 客资中的待付款、已付款、已录单 | `PurchaseIntent`、`PaymentIntent`、支付流水、订单资金分配、Order 和审批事件 | 不再作为客资状态；需按购买主体、订单和可信渠道流水重建事实，最终展示状态在查询时投影 |
| “客资已转订单” | `Lead.status.won` | 只有订单生效才记为成交；仅创建机会或提交订单的历史记录分别归入机会和订单事实 |
| 订单中的报名中、财务待审 | 审批轮次或报名服务单 | 根据真实发生时间拆分，不能机械一对一改值 |
| 单一订单审核状态 | ZSJOS `ApprovalRound` + BPM 流程历史 | 缺少双中心历史时标记迁移来源，不伪造 BPM 任务、操作人和审批意见 |
| 报名 16 步或固定布尔字段 | `RegistrationItem` | 按记录类型迁移实际完成事实和凭证 |
| 旧订单完成 | `Order.status.effective` + 报名/服务事实 | 必须判断是仅审批通过，还是报名已完成并已交付 |
| 复购覆盖原订单或原服务状态 | 新 PurchaseIntent、新订单、新服务关系 | 拆分历史版本；一期不强制补造复购 Opportunity，无法可靠拆分的记录进入人工校验清单 |

被替代的旧字典类型：

- `zsjos_lead_deal_progress_status`：删除目标配置，迁移到机会状态及订单事实。
- `zsjos_order_enrollment_status`：删除目标配置，由报名服务单状态承担。
- `zsjos_order_review_status`：删除目标配置，由 ZSJOS 审批轮次状态和 BPM 流程状态共同承担。
- 旧付款数据按可核验事实迁入 `PurchaseIntent`、`PaymentIntent`、`PaymentTransaction` 和 `OrderPaymentAllocation`；当前 `zsjos_payment_order` 物理表是否演进为 PaymentIntent 需单独评审。旧退款按实际业务审批和渠道执行事实分别迁入 `RefundCase` 与 `PaymentRefund`，不得为一期补造部分退款或 RefundItem 明细。外部流水号只作为引用，不得继续把外部表作为业务主表。
- 历史重复提交若没有独立激活事实，不得根据更新时间伪造 LeadActivation；只能标记迁移来源或进入人工核验。

迁移不得重写已执行的生产迁移。正式迁移前需要单独设计可重复执行、可核对、可回滚的数据脚本，并经明确确认后执行。

## 14. 实施顺序

1. 确认 ZSJOS 各业务实体的物理表、主键、索引、金额精度、币种和公共审计字段。
2. 固化客资查重与激活协议、字典值、权限点、错误码和命令接口。
3. 按支付一期开发实施文档实现 PurchaseIntent、通联支付、手工补录订单和成交链路投影；本文不另设支付实施顺序。
4. 确认产品能力的公开规则结果契约；ZSJOS 只保存稳定引用和快照，不设计或直查产品内部表。
5. 实现订单审批轮次、BPM 双会签任务结果事件及业务事务不变量，再接报名服务和服务关系。
6. 接入首次销售转化、复购及其支付一期边界。
7. 设计历史数据盘点与迁移，不从旧混合状态直接猜测业务事实。

本文不包含 SQL、接口实现或数据库执行授权。

## 15. 验证清单

- [ ] 每个状态只属于一个明确实体和状态字段。
- [ ] 客资中不存在付款、录单、成交跟进或复购状态。
- [ ] 一次表单在未命中时原子创建 Person 与 Lead，命中时只创建 LeadActivation，交叉身份冲突不进入业务表。
- [ ] 同一 Lead 最多一条 `initial_conversion` 机会；每次首购或复购创建独立 PurchaseIntent，复购一期不强制创建 Opportunity，且不修改历史对象。
- [ ] 支付模型、渠道确认、手工补录、八态投影和退款全部通过支付一期开发实施文档的验收矩阵。
- [ ] 财务审批只核对支付与订单一致性，不创建或改变渠道到账事实。
- [ ] 无 Opportunity 订单不得驱动机会成交；有 Opportunity 时订单与机会 Person 必须一致。
- [ ] 每轮审批只创建一个新的 BPM 流程实例，并由已部署流程定义产生报名服务中心和财务两条并行任务。
- [ ] 产品规则要求双会签时两条审批都通过订单才生效；机会和报名服务只在各自条件满足时联动。
- [ ] 任一驳回会关闭本轮未完成任务并创建提交人补正任务。
- [ ] 主动撤回取消当前轮次并终止当前 Order；继续交易时创建 successor，取消整个 PurchaseIntent 才放弃本次交易。
- [ ] 补正或撤回后重提创建 successor Order、其第 1 个新轮次、快照和 BPM 流程实例，不复用旧结果。
- [ ] 报名完成只为固化产品规则要求持续服务的订单项创建服务关系。
- [ ] 预充值使用 Person 独立账户和只追加账本，余额只供本人跨订单使用且不能直接提现或转移。
- [ ] 线下动作允许按实际发生时间补录，系统不限制实际操作。
- [ ] 状态转换、权限、幂等和并发规则不依赖字典标签。
- [ ] 所有业务实体、Mapper、Service 和事务均属于 `yudao-module-zsjos`，不存在 CRM 表或 CRM Service 运行时依赖。
- [ ] 所有业务外键引用 ZSJOS 自有主键，外部系统 ID 只存在于 `external_*` 字段。
- [ ] 当前实现映射只出现在迁移附录，不改变目标模型。
- [ ] 数据迁移前能够识别无法可靠拆分的历史记录并转人工核验。

## 16. 尚待实施阶段确认

以下问题不影响当前领域拆分，但在对应数据库和接口设计前必须单独确认，本文不预设答案：

- 产品模块或产品能力的实际所有者、物理表、SKU 规则结构、版本协议和公开 API。
- 哪些 `Lead.status` 允许创建支付单，以及已关闭、无效或已转换客资被再次激活后允许发起哪些后续业务动作。
- 同一 Person 存在多条历史 Lead 时，LeadActivation 关联哪一条当前 Lead；激活本身是否以及如何影响历史终态。
- `Person.identity_status`、`Partner.status` 的稳定协议值和合法转换。
- 现有 `zsjos_lead_source_platform` 字典与本文 `source_channel_id` 的统一口径、稳定值和数据所有者。
- 预充值余额退款是退回原支付渠道、退回账户余额还是按业务类型选择，以及管理员调账的审批规则。
- 报名服务完成所需的最低记录项由哪一项业务配置提供。
- 兼职主体的结算信息及结算记录使用哪些独立 ZSJOS 子表。
- 支付与通联未决项统一见支付一期开发实施文档第 22 节，不在本文重复维护。

## 17. 客资三级申诉状态机（V015）

客资首次被销售判定无效后，提交人可在无时限条件下手动发起最多三轮独立申诉。申诉处理中 `Lead.status` 始终为 `invalid`；每轮均保存当时无效原因、说明和图片快照。

```text
invalid + 第1次申诉 -> sales_manager_reviewing -> overturned | upheld
invalid + 第2次申诉 -> quality_reviewing      -> overturned | upheld
invalid + 第3次申诉 -> chairman_reviewing      -> overturned | upheld(最终无效)
```

`upheld` 表示本轮维持无效，只有第三轮的 `upheld` 才是最终结论；第三轮之后禁止第四次申诉。改判有效统一复用“客资判有效”的后续转换入口，并清理当前无效展示字段，历史事件与申诉记录不变。每次提交和裁决都要求幂等键、必填理由和最多 9 张 JPG/PNG/WebP 图片；第二、三轮不会自动升级，必须由提交人重新提交。

## 18. 公海状态机（V033）

有效且机会处于 `open/following` 的未成交客资，从 A 正式接手时间起按租户规则计算自然日。默认 90 天；新增跟进不重置计时，修改规则立即影响尚未入池客资。活动成交审批中的客资暂不入池。

```text
超期 -> waiting_assignment -> assigned -> deal_pending -> converted
                         |          |
                         +--换派B---+--正式转派给B-> exited + owner=B
waiting_assignment/assigned --主管填写原因退出--> exited
```

- 入池记录 A 的直属部门快照用于审计，运行时可见范围按 A 当前直属部门计算；A 仍是 Lead 名义负责人，`waiting_assignment` 时 A 只读。
- 主管只能指派 A 当前直属部门内、启用且不同于 A 的销售 B。B 失效或调离后自动清空并返回待指派，历史记录不覆盖。
- `assigned` 时 A 与 B 均可新增跟进、设置下次跟进；只有 A 可录入或补正成交，B 必须先申请或由主管直接完成正式转派。
- A 先提交订单时周期进入 `deal_pending`，禁止换派和退出；驳回或取消回到 `assigned`，最终生效进入 `converted`。
- B 不得通过成交接口隐式转权。B 可线上申请正式转派，或由主管线下沟通后直接转派；正式转派完成后，Lead/Opportunity 负责人改为 B，随后 B 才能录入成交。订单驳回、取消或终止不回滚已完成的正式转派。
- 主管退出必须填写原因，且 A 必须仍启用；退出后以退出时间作为 A 新一轮持有起点。
- 通知只包含一个或多个提前规则和实际入池到期通知，不发送逾期通知。
### 公海接续成交

- B 补正自己提交的驳回订单时，在同一 PurchaseIntent 下新建 successor Order，并保持本次实际提交人审计。
- B 接续 A 提交的驳回订单时，同样在原 PurchaseIntent 下新建 `continuation_sale` successor Order；A 的原订单转为 `superseded`。
- 新旧订单通过 `supersedes_order_id` / `superseded_by_order_id` 双向关联，历史不可覆盖。
- 同一 PurchaseIntent 同一时刻只能指向一张当前 Order；`superseded` Order 退出活动唯一约束但继续保留审计与审批历史。

## 19. 主管公海标记（V035）

人工释放公海不是 Lead 状态机迁移。主管只能对当前仍归属受管下属且未关闭的客资创建一次活动协作标记，可选一名管理范围内的启用销售作为公海跟进销售。

```text
owner=A, status=S, assignment_status=X
  --填写原因释放公海，可选 collaborator=B-->
owner=A, status=S, assignment_status=X, public_sea(owner=A, collaborator=B?)
```

- 释放前后 `owner_user_id`、Lead 主状态和 `assignment_status` 必须相同。
- `assignment_status=public_pool` 仍专指无归属、可抢单的抢单池；公海不得进入抢单列表或领取接口。
- 协同本身不改变正式归属；公海跟进销售不得直接提交首购订单，必须先完成正式转派。正式转派后退出公海并由新负责人录入成交。
- 配置跟进销售后仍留在公海；只有正式转派、关闭或成交退出。活动订单审批期间冻结转派、退出和协作人替换。
- 重复释放返回稳定的“已在公海”失败；批量中该失败不回滚其他成功项。

## 20. 成交生命周期并发与身份约束（V043）

- Person 是客户唯一身份主档。手机号和微信号进入同一个租户级、二进制比较的联系方式占用空间；两字段交叉命中也属于身份冲突。规范化只去除首尾空格，微信号大小写敏感且最长 64 字符。
- 所有 Person 联系方式新增和修改必须在事务内先锁定或预占联系方式，再写 Person，最后释放已移除的旧联系方式。历史冲突由迁移前审计阻断，不自动合并、截断或删除。
- 客资判有效后主状态保持 `valid`；商机状态独立表达跟进和赢单。首购生效才把商机置为 `won`、客资置为 `won`；复购生效只新增有效订单，不修改客资、商机或首次成交时间。
- 订单审批、驳回以及创建人或正式负责人终止均按“订单→当前审批轮次”顺序加锁，并登记绑定操作人、节点、BPM 任务和请求指纹的命令账本。相同命令可重放，不同命令复用幂等键必须冲突，BPM 失败整体回滚。
- 补正权限只属于原创建人或正式负责人。公海跟进销售可按协作权限新建成交，但不能仅凭协作身份补正他人订单。
- 创建人可在报名履约或财务审批阶段终止未生效订单。订单详情永久显示终止原因；历史缺失原因明确显示为未记录。

## 21. 成交审批主管确认（V055）

报名履约与财务继续并行会签。上线后新轮次中，任一中心普通审批人可在本轮唯一一次申请订单销售主管审批；申请后整轮参与者严格为报名履约、财务和主管三方。申请、通过和驳回意见均必填且不超过 1000 字。

```text
中心普通审批中 --申请主管审批--> 普通审批与主管并行加签
并行加签中 --中心先通过--> 等待主管通过
并行加签中 --主管先通过--> 等待中心通过
中心与主管均通过 --> 该中心节点完成
并行加签中 --中心或主管驳回--> BPM 驳回整轮 -> revision_required
并行加签中 --另一中心驳回/销售终止/流程取消--> pending confirmation.cancelled
```

- 主管固定取订单正式销售当前直属部门 `leaderUserId`，必须启用、不同于正式销售本人且持有主管确认权限；每轮唯一记录阻止另一中心再次申请，避免扩展为四方审批。
- 加签期间报名履约、财务和主管任务均保持可见可处理；普通中心通过后从该审批人的待办中移除，但 BPM 父任务等待主管通过后才完成。主管先通过时普通中心任务继续等待；订单与轮次行锁及版本校验保证并发决定一致收敛。
- 申请与决定进入订单命令账本并递增订单、轮次版本，主管记录另有版本；订单和轮次行锁保证并发命令只有首个有效。
- 新轮次普通审批池包含报名/财务根部门及其子部门的所有启用用户，包括部门负责人；任一中心没有启用用户时才拒绝提交。
- 上线前已存在轮次不迁移、不重启，保持旧流程；只有绑定新 BPM 定义版本且 `supervisor_confirmation_enabled=1` 的新轮次允许申请。
