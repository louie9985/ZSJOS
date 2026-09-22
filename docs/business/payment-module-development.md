# ZSJOS 录单草稿与通联支付开发文档

## 1. 实现边界

本期在现有录单流程中加入 `collectionMode=online_link|offline_paid`。两条路径共用 `PurchaseIntent` 草稿，正式提交时才创建 `Order` 并启动现有 `zsjos_sales_order_dual_approval` BPM。线上支付不自动建单，线下支付不创建渠道流水。本期不实现退款、二维码、自动建单、独立支付管理页，也不依赖 `yudao-module-pay`。

```text
Lead / Person 跟进 -> PurchaseIntent 草稿
  -> online_link: PaymentIntent -> 通联到账 -> 补齐资料
  -> offline_paid: 付款声明 + 凭证
  -> Order -> 报名履约审核 + 财务审核
```

最终展示状态是实时投影，不落库：无订单时为订单草稿、待支付、已支付待提交或已失效；有当前订单时按 BPM 事实投影为审核中、已终止或已成交。

## 2. 数据模型

`zsjos_purchase_intent` 是一次首购或复购链路的稳定锚点，保存业务编号、购买类型、收款路径、Lead/Person/Opportunity、发起人、责任人、当前订单、表单草稿 JSON、后端解析后的 SKU/金额快照、总额、币种、版本和幂等键。草稿最低要求是可确定 Person、至少一个有效 SKU、明细金额合法且合计大于零。

`zsjos_payment_order` 作为 PaymentIntent，状态固定为 `created/waiting/paid/expired/closed`。一个 PurchaseIntent 可有历史支付单，但最多一条活动或已支付记录；旧单失效或关闭后可以在同一 PurchaseIntent 上重新生成新的 PaymentIntent，旧单不复活。`zsjos_payment_gateway_event` 保存脱敏下单、查询、回调和关单事实；`zsjos_payment_transaction` 只保存验签并核对成功的到账事实。

`zsjos_order.purchase_intent_id` 关联草稿，线上订单同时写 `source_payment_order_id`，并向 `zsjos_order_payment_allocation` 写入可信流水分配。successor Order 继承 PurchaseIntent 和 PaymentIntent，不重复收款。

## 3. 内部接口

接口复用 `zsjos:sales-order:create`，对象权限仍由现有 Lead、学员和订单服务执行。

| 方法 | 路径 | 用途 |
|---|---|---|
| POST | `/admin-api/zsjos/purchase-intent/current` | 恢复当前用户来源对象上的未提交草稿 |
| POST | `/admin-api/zsjos/purchase-intent/save-draft` | 以 `id+version` 乐观锁保存草稿 |
| POST | `/admin-api/zsjos/purchase-intent/save-and-create-payment-link` | 保存线上草稿并创建或复用支付链接 |
| POST | `/admin-api/zsjos/purchase-intent/{id}/refresh-payment` | 查单并返回最新支付事实 |

请求核心结构：

```json
{"id":101,"version":2,"collectionMode":"online_link","purchaseType":"lead_first_purchase","leadId":2001,"personId":3001,"sourceKey":"lead_first_purchase:2001","draft":{"studentName":"示例姓名"},"items":[{"spuRef":"COURSE-1","skuRef":"SKU-1","actualAmount":1280.00}],"totalAmount":1280.00,"idempotencyKey":"客户端唯一命令键"}
```

首次首购、复购提交仍核对 Person、SKU、金额和草稿状态。successor 的购买链路由原订单决定，请求不得换绑 `purchaseIntentId`。按 2026-09-22 确认的补正规则，线下 `offline_paid` 重提允许纠正课程、SKU 和金额录入错误；保留 Person 与原订单绑定校验，重新校验商品、付款资料和凭证，在原事务内按版本条件更新购买意向的最新明细/总额，并创建新订单、新快照和完整审批。原订单、原审批快照、原草稿表单 JSON 不覆盖。金额更正不代表产生补款或退款，真实性由新一轮财务审批核验。

线上 `online_link` 重提继续保持原课程、SKU、金额约束，并核对 PaymentIntent=`paid` 和 PaymentTransaction 到账金额。当前不支持通过补正处理线上同价换课或差额交易，此限制不是永久禁止换课的业务规则。线上、线下仍都要求付款时间、支付方式和至少一份缴费凭证。旧数据没有购买意向且没有渠道支付引用时按历史线下订单处理；存在渠道支付引用但缺少购买链路时拒绝重提并要求核实数据。

## 4. 公开支付接口

公开接口使用 `/public-api`、`@PermitAll` 和 `@TenantIgnore`。链接 token 只保存 SHA-256 摘要；商户配置和 token 用于定位租户，任何 `tenant-id` 请求头都不是可信依据。

| 方法 | 路径 | 返回 |
|---|---|---|
| GET | `/public-api/zsjos/payment/{no}?token=...` | 金额、摘要、状态、有效期及商品展示快照 `items` |
| POST JSON | `/public-api/zsjos/payment/{no}/order` | 支付宝返回验签后的 `payinfo` |
| POST FORM | `/public-api/zsjos/payment/{no}/order` | 微信返回自动 POST 到通联的 HTML |
| POST | `/public-api/zsjos/payment/{no}/status` | 查单后返回是否已确认到账 |
| POST FORM | `/public-api/zsjos/payment/allinpay/notify` | 通联异步通知，成功返回 `success` |
| GET | `/public-api/zsjos/payment/allinpay/result` | 302 到 H5 `/payment-result` |

## 5. 通联协议

配置前缀为 `zsjos.payment.allinpay`，默认 `enabled=false`。生产必须配置商户号、APPID、orgid、四个接口地址、通知/回跳/H5 地址、私钥、公钥、链接 HMAC 密钥和支付宝 `payinfo` 域名白名单。密钥内容不得进入仓库或日志。

签名规则：排除 `sign`、`null`、空字符串；字段名按 ASCII 升序；以 `key=value&...` 连接；UTF-8 编码；`SHA1withRSA`、PKCS#1 v1.5；标准 Base64。

### 微信 unionorder

浏览器向 ZSJOS 提交后，后端生成并 HTML 转义自动提交 FORM：

```text
POST {unionorder-url}
cusid, appid, orgid, version=12, trxamt(分), reqsn, body,
charset=UTF-8, returl, notify_url, remark, expiretime=yyyyMMddHHmmss,
ishide=1, signtype=RSA, randomstr, sign
```

ZSJOS 只因成功生成通联表单把 PaymentIntent 置为 `waiting`；表单响应、浏览器回跳和前台文案均不是到账凭证。

### 支付宝 A01

后端直接调用：

```text
POST {unitorder-pay-url}
cusid, appid, orgid, version=11, trxamt(分), reqsn, body,
paytype=A01, notify_url, signtype=RSA, randomstr, sign
```

通联 JSON 可能返回 `retcode`、`errmsg`、`trxstatus`、`reqsn`、`trxid`、`chnltrxid`、`trxamt`、`payinfo`、`sign`。只有响应验签成功、`retcode=SUCCESS`，且 `payinfo` 是命中白名单的绝对 HTTPS URL 时才能返回前端跳转。

查单发送 `cusid,appid,reqsn,version=11,signtype=RSA,randomstr,sign`；关单发送 `cusid,appid,oldreqsn,version=11,signtype=RSA,randomstr,sign`。`waiting` 支付单切换路径或重新生成前必须先查单；未到账且通联确认关单成功后才能关闭。结果未知、验签失败或关单失败时保留原状态并阻止第二条有效链接。`expired` 或 `closed` 的旧单不复活，但同一 PurchaseIntent 可以重新生成新的 PaymentIntent。

回调或查单只有同时满足签名有效、商户和 APPID 匹配、`reqsn` 匹配、金额分完全一致、`retcode=SUCCESS`、`trxstatus=0000` 才创建 PaymentTransaction。事件编号和渠道流水唯一约束保证回调与查单并发只确认一次。

## 6. 前端流程

Workbench `SalesOrderEntryModal` 使用分段控件选择线上链接或线下已支付，共用原表单。提供保存草稿、生成支付链接、失效后重新生成支付链接、刷新状态、复制链接和提交审批。线上链接生成后禁用主体、SKU、金额；未到账禁止提交；到账后仍需补齐正式字段并上传凭证。successor 只补正订单。

H5 提供 `/pay/:paymentIntentNo` 和 `/payment-result`。微信支付只在微信内展示并以 FORM 发起；支付宝在微信内提示外部浏览器打开。结果页使用 sessionStorage 的短期会话轮询后端；超时或查询异常只显示“结果待确认”，不显示支付失败。

公开支付页采用方案 B：公司 Logo → 本次应付金额 → 成交产品明细 → 支付按钮。
Logo 使用 `https://www.zsjedc.com/logo.png`，不可用时显示中世健文字。明细显示产品名称、
SKU 名称和规格标签、成交价格，不显示“SKU：”前缀。应付金额直接使用支付单 `amount`，
不从目录标价或前端明细合计推导。加载失败保留错误原因并支持重试，失效链接隐藏支付按钮。

公开详情新增 `items: [{ productName, skuName, actualAmount, specs }]`，`specs` 沿用
`ProductSpecVO` 的 `attrKey/attrName/value/label/labelMissing` 快照契约。
新支付单生成时通过 `ZsjosProductSkuService.validateLeadProduct` 获取服务端产品与规格标签，
连同已保存草稿的商品引用和成交金额写入现有 `product_items_snapshot` JSON；不采用客户端
提交的展示名称，不改草稿请求、锁定快照比较或付款金额。有效支付链接复用时不刷新展示快照。
公开明细只投影展示字段，不返回商品内部引用或支付主体配置。

旧支付单不补写、不查询当前目录重新贴标签；保留已有 SKU 名称与成交金额，缺失产品名称或
规格时明确显示“未记录”。`description` 保留原语义兼容旧前端，新 H5 不把它作为产品名称，
避免展示内部 SKU 引用。旧后端没有 `items` 时显示明细未记录提示；发布顺序为后端后 H5。
本次不涉及数据库迁移、Admin/Workbench 命令变更或支付宝通道修复。

支付页定向验证：后端执行
`mvn -f backend/pom.xml -pl yudao-module-zsjos -am -Dtest=PublicPaymentDetailTest -Dsurefire.failIfNoSpecifiedTests=false test`；
H5 执行 `node --test tests/payment-page.test.mjs` 和 `npm run build`。
构建后可运行 `node tests/payment-browser-server.mjs`，使用
`http://localhost:5187/pay/multi?token=fixture` 检查实际产物；路径中的 `multi` 可换为
`single/legacy/empty/error/retry/expired/loading`。此服务仅绑定本机，返回明确的测试数据，
任何支付 POST 都被拒绝，不代表真实支付联调通过。

## 7. 配置示例

```yaml
zsjos:
  payment:
    allinpay:
      enabled: false
      notify-url: ${ZSJOS_ALLINPAY_NOTIFY_URL:}
      return-url: ${ZSJOS_ALLINPAY_RETURN_URL:}
      public-base-url: ${ZSJOS_PUBLIC_H5_BASE_URL:}
      link-hmac-secret: ${ZSJOS_PAYMENT_LINK_HMAC_SECRET:}
      allowed-payinfo-hosts: ${ZSJOS_ALLINPAY_PAYINFO_HOSTS:}
```

上述配置仅管理通用支付入口、回调、超时及链接参数。购买支付与退款的商户号、应用 ID、机构号、私钥和平台公钥来自订单主体快照；全局同名字段及密钥文件路径不再作为异常回退来源。

主体路由以服务端真实 SKU 关联为准：各产品有配置时使用其主体，没有配置时使用当前租户默认主体；所有明细最终主体相同则使用共同主体，不同则使用默认主体。不会因多个 SKU 或多条明细直接指定学校编码。所有参与解析的主体及最终默认主体必须有效，否则生成链接失败。完整错误与快照契约见[支付主体 API](../api/payment-subject.md#支付主体路由与快照2026-09-18)。

## 8. 发布与验收

### 8.1 退款与主动对账

一期只允许通联线上支付的整单全额退款，退款金额由后端读取已验签的 `PaymentTransaction.amount`，线下已支付不调用通联。未生效、驳回或撤回订单由财务直退；已成交订单须通过业务主管与财务 BPM 后再提交通联。

退款单状态：`approval_pending`、`submitting`、`accepted`、`unknown`、`succeeded`、`failed`、`manual_review`。`retcode=SUCCESS` 仅代表受理，只有验签成功且 `trxstatus=0000` 才能标记 `succeeded`。超时或结果未知时复用原 `refund_reqsn` 查询，禁止再次提交新请求；回调按退款请求号幂等。

支付下单、查单、关单以及退款提交、查询、回调均写入 `zsjos_payment_gateway_event`，记录业务类型、操作、请求号、返回码、交易状态、验签结果和脱敏摘要。`PaymentReconciliationJob` 按租户扫描 `accepted/unknown` 退款主动查询，超过阈值转 `manual_review`。原支付流水、订单和 BPM 历史永久保留，退款成功只在展示层覆盖为“已退款取消”。

管理端接口：`POST /zsjos/payment-refund/apply`、`POST /zsjos/payment-refund/direct`、`GET /zsjos/payment-refund/{id}`、`POST /zsjos/payment-refund/{id}/refresh`；通联回调：`POST /public-api/zsjos/payment/allinpay/refund-notify`。新增配置 `refund-url`、`refund-query-url`、`refund-notify-url`、`refund-version`，数据库结构由 `V163__zsjos_payment_refund_reconciliation.sql` 提供。无数据库环境使用 Mockito/Fake 网关测试，不执行真实迁移或连接真实通联。

数据库使用 `V162__zsjos_purchase_intent_payment_draft.sql` 升级，基线同步在 `schema/core.sql` 与 `00-bootstrap-schema.sql`。发布前须由通联确认正式接口域名、APPID 产品授权、通知白名单、证书格式、回调应答和关单状态语义，再启用配置。

验收至少覆盖四种来源、草稿恢复、乐观锁、重复生成、失效后重新生成、两种路径切换、微信/支付宝报文、金额转分、签名/验签、回调与查单并发、金额/SKU 不一致、未支付提交、凭证校验、successor 继承，以及 H5 轮询超时。测试环境可在测试代码中使用假网关，但生产运行时不提供 Mock。

### 2026-09-22 驳回接续与支付一致性

线上接续先保存已验证的支付引用，通过显式置 NULL 的数据库更新释放旧订单唯一键，再给 successor 绑定原支付单和可信到账流水分配；购买意向指向新订单，以上步骤与 BPM 启动属于同一事务。历史分配记录保留，新分配按新订单幂等，不表示再次收款；汇总不得把历史被接续订单作为当前有效分配重复计算。

错误码 `1900017026` 表示链路不匹配，`1900017027` 表示线上交易字段不可直接更改，`1900017028` 表示到账金额不符，`1900017029` 表示首次提交与草稿不一致。`1900017005` 不再用于订单重提的课程校验。


## 在线到账业务通知落地（2026-09-22）

`PurchaseIntentService.confirmPaid` 的可信确认分支，在记账同一事务发布 `zsjos.payment.paid`，回调与主动查单共用此入口。已到账或重复网关事件不再次发布，System outbox 按支付单事件键和规则去重。收件人冻结为当前购买意向负责人；缺少关系或负责人时不猜测替代账号，由 System 投递诊断暴露无收件人。内容只包含购买意向业务编号、支付单号和金额，提示在成交录单中核对补录；不包含客户姓名、手机号、链接令牌、网关签名或完整回调。

站内信与企微使用独立规则，默认打开消息详情，不自动创建订单、不新增购买意向跳转。配置由开发 V274 引用 `script/sql/mysql/sales-notification-alignment.sql`，已有本地环境按该脚本的备份、验证及重复执行要求同步；共享/生产升级另行审查。新租户通过 System 默认规则 API 初始化。到账通知失败不会被标成真实企微已送达，历史支付记录不补发。详见[业务通知契约](../api/system-business-notifications.md)。
