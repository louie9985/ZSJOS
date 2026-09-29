# Cashback API

ZSJOS cashback has two independent types and six states. A valid-lead cashback is a fixed product-rule amount keyed by `valid:<leadId>`. A deal cashback is an order-item snapshot amount multiplied by its rate with `HALF_UP` two-decimal rounding and is keyed by `deal:<orderItemId>`. Both begin in `pending_settlement`; the other states are `available`, `withdrawing`, `withdrawn`, `cancelled`, and `blocked`.

Only a Lead whose immutable source is an enabled ordinary partner can generate new cashback. For Leads submitted through the independent Partner account introduced by V072, `sourceUserId` identifies an enabled `zsjos_partner_account` belonging to the Lead's `partnerId`; the cashback is owned by `partnerId` and leaves the System-user-only `beneficiaryUserId` empty. Pre-V072 Leads that retain the former bound System-user identifier remain compatible and retain that identifier in `beneficiaryUserId`. New-media and sales-self Leads return without generating a record. Product rules override level-one category defaults; nullable product values mean inheritance, then the system default of 10.00 valid-lead cashback and 10% deal rate. Amounts must be non-negative and rates must be between zero and one inclusive. The observation days configuration defaults to seven, is limited to 0 through 365, and is copied into each record with the product and rule snapshot. Rule snapshots record whether each value came from the product, level-one category, or system default.

The independent partner frontend uses `GET /part-api/zsjos/cashback/my-page` and `GET /part-api/zsjos/cashback/my-summary`; both require `zsjos:cashback:my-query` and restrict rows by the authenticated account's `partnerId`. `GET /admin-api/zsjos/cashback/page` remains the finance-wide administrator view and requires `zsjos:cashback:finance-query`.

The hourly scheduler pauses during maintenance. Mature valid cashback does not recheck the current Lead qualification state. Mature deal cashback becomes available only after the linked order is effective. State transitions use version-and-source-state conditions.

The deal-generation API is currently a domain service boundary. Its order-create, finance-reject cancellation, and withdrawal-lock integration must be serialized after the active order workstream is integrated; they are not represented as complete by V051.

## 财务禁止／恢复提现（V281）

财务在管理端或工作台返现列表、详情抽屉内逐笔操作，原因必填（去除首尾空白，1–500 字）。禁止原因会展示给兼职；金额、返现规则及观察期快照保持不变。本功能不涉及实付金额调整、拆分支付或自动授予角色权限。

- `PUT /admin-api/zsjos/cashback/{id}/block`：请求 `{version, reason}`；要求查询权限和 `zsjos:cashback:block`，只接受 `pending_settlement`、`available`，转换为 `blocked`。
- `PUT /admin-api/zsjos/cashback/{id}/unblock`：同样请求；要求查询权限和 `zsjos:cashback:unblock`，只接受 `blocked`。原可提现记录恢复可提现；原待结算记录按原观察期和订单生效条件判定是否可结算。
- `GET /admin-api/zsjos/cashback/{id}/control-history?pageNo=1&pageSize=10`：要求财务查询及对象读取权限；返回分页 `{id, action, reason, operatorName, occurredAt}`，按时间、ID 倒序。仅返回本笔成功的禁止／恢复业务审计，不扩大通用审计访问权。

响应增加 `version`、`blockedFromStatus`、`blockReason`、`blockedByUserId`、`blockedAt`。恢复清空当前禁止信息，但所有操作原因和操作人快照保留在既有业务审计中。历史记录不编造禁止信息。

`blocked` 显示“不可提现”，不计入可提现汇总、不能入选提现申请；定时结算和重复生成事件不会解除它。禁止与提现申请采用同一租户行锁，结合源状态和版本条件更新；过期请求返回“返现状态已变化，请刷新后重试”。已申请提现的记录继续走原审批、备注和打款流程，财务不可通过此入口改动它。

来源订单按原业务规则取消成交返现时，`blocked` 也会进入 `cancelled`；已取消不可恢复。自动订单取消原因与财务禁止原因分别保存，历史不删除。

Admin/Workbench 状态选项来自服务端筛选目录。两端列表及抽屉复用各自的原因弹窗；失败保留输入，提交期间不可重复提交或关闭。H5 在收益列表、详情展示禁止状态和原因，隐藏该笔去提现按钮。详情操作记录支持分页、加载失败重试；窄屏详情采用单列信息布局。

### 数据库交付

先执行 V280，再执行 `V281__cashback_withdrawal_control.sql`；新环境使用最新 baseline 后沿同一编号顺序升级。V280 只添加四个可空字段、两个按钮元数据和版本记录，重复执行保留原字段值及管理员菜单配置，不修改 `system_role_menu`。`verify/cashback-control.sql` 提供只读结构、权限元数据、版本和中文 HEX 检查。回退代码前需先处理仍被禁止的记录；不删除字段或审计历史。新版本的 checksum 按文件字节登记，不对旧版本做自动校准。

## ADMIN keyword search

Both Admin and Workbench cashback lists label `partnerName` as **兼职姓名**. The existing `keyword` parameter on page/search-page and their ADMIN my-page equivalents trims surrounding whitespace and matches cashback number, Lead business number, partner name, beneficiary display name, customer submitted name and order student name. Names use case-insensitive substring matching. Partner-owned beneficiaries use the partner business name; legacy System beneficiaries use the System public user API, matching the detail projection. No internal ID, phone, product or order-number keyword matching is added.

Name matches are combined with number matches using OR before database pagination, and intersected with type/status, advanced filters and personal scope. Customer names require Lead page/object and unmasked-identity access; student names require order page/object access. Inaccessible source names do not contribute matches. Tenant and logical-delete isolation remain enforced. Partner H5 behavior and response/request shapes are unchanged.
