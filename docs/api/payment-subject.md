# 支付主体管理与产品支付主体配置

2026-09-15 接口联调修正。页面由 Vue Admin 实现，PC 工作台通过既有 `admin_embed` 访问。
服务端菜单位于工作台 `/zsjos`，页面路径为 `/zsjos/payment-subject`、`/zsjos/product-payment-subject`。
本次不调整菜单、角色授权或支付交易流程。开发库关联表按 V241 修正。

所有接口使用 ADMIN 身份、当前租户和 CommonResult 响应。下表路径均带 `/admin-api` 前缀。

| 方法及路径 | 参数 / 返回 | 权限 |
| --- | --- | --- |
| GET `/zsjos/payment-subject/page` | pageNo、pageSize、subjectName、status；返回 `{list,total}` | `zsjos:payment-subject:query` |
| GET `/zsjos/payment-subject/simple-list` | 返回主体列表项数组 | 主体 query、产品配置 query 或 configure 任一权限 |
| GET `/zsjos/payment-subject/get` | id；返回详情 | 主体 query |
| POST `/zsjos/payment-subject/create` | PaymentSubjectSaveReqVO | 主体 create |
| PUT `/zsjos/payment-subject/update` | PaymentSubjectSaveReqVO，含 id | 主体 update |
| PUT `/zsjos/payment-subject/update-status` | 查询参数 id、status（不是 JSON body） | 主体 update |
| DELETE `/zsjos/payment-subject/delete` | 查询参数 id | 主体 delete |
| GET `/zsjos/product-payment-subject/page` | pageNo、pageSize、productName、paymentSubjectId；返回 `{list,total}` | `zsjos:product-payment-subject:query` |
| POST `/zsjos/product-payment-subject/configure` | `{productId,paymentSubjectId}` | 产品配置 configure |
| POST `/zsjos/product-payment-subject/batch-configure` | `{productIds,paymentSubjectId}`；页面单选也复用此接口 | 产品配置 configure |

主体列表与选择项不返回商户私钥或平台公钥。主体管理表单使用后端字段
`subjectCode`、`subjectName`、`cusid`、`appid`、`orgid`、`merchantPrivateKey`、
`platformPublicKey`、`status`、`remark`；不再提交无对应后端字段的 channel、merchantId、appId、notifyUrl、returnUrl。
状态沿用 CommonStatus：0 启用、1 停用；状态选项读取 `common_status` 系统字典。

产品配置分页只查询当前租户未删除且已启用（`status = 0`）的课程，包含未配置主体的课程；课程状态及主体筛选均在数据库分页前生效，total 不包含停用课程。
返回 `productId`、`productName`、`paymentSubjectId`、`subjectCode`、`subjectName`、`configTime`。
配置时间来自关联记录 update_time；未配置时为空。不生成不存在的 productType 字段。
历史删除关联及其他租户关联不参与展示；配置下拉仅显示启用主体，查询筛选可选停用主体。
重复配置更新现有有效关联，不反复软删插入，避免 `(tenant_id,product_id,deleted)` 唯一键冲突。
单个及批量配置均在事务内锁定当前租户课程并校验启用状态；不存在、已删除或其他租户课程返回 `PRODUCT_NOT_EXISTS`，停用课程返回 `PRODUCT_NOT_ENABLE`。批量课程全部通过校验后才写入，任一失败则整批不保存。停用课程仅从配置列表隐藏，不删除已有关联，历史关联读取保持不变；重新启用后可重新显示原配置。

两个页面分别处理查询权限不足、加载、空列表和失败重试；产品选项失败不阻止产品列表加载。
后端仍独立检查每个接口权限；精简选择项权限不授予主体详情或密钥读取权限。

验证入口：后端 `PaymentSubjectControllerTest`、`PaymentSubjectQueryTest`；Admin
`node --test tests/paymentSubjectApi.test.ts`，类型检查、定向 lint 与 `pnpm build:local`。
发布时需要加载本次后端编译结果并交付更新后的 Admin（含工作台嵌入版本），仅刷新旧后端不会注册新增接口。

## 开发库关联表修正

V241 在 V239 后执行：新表使用 `payment_subject_id bigint`；已有空旧表的 `subject_code` 转为该字段，索引随列转换。可重复执行；非空旧表由 CHECK 守卫拒绝，必须另行审核映射，不得用 `--force` 忽略错误。执行前备份结构，DDL 隐式提交；仅表仍为空时可恢复旧结构，恢复后旧结构与当前接口不兼容。已部署环境须另行审核升级流程。


## 支付主体路由与快照（2026-09-18）

生成支付链接时，以已保存购买明细为准，通过真实 `skuRef` 查询 SKU 的 `spuId`，并复用商品服务校验产品、SKU 的启用状态和归属。不解析 SKU 编号字符串，不接受前端指定收款主体。

| 场景 | 选择规则 |
| --- | --- |
| 单产品或同产品多个 SKU | 产品配置的主体；确实未配置时使用当前租户默认主体 |
| 多产品最终主体相同 | 使用共同主体 |
| 多产品最终主体不同 | 使用当前租户管理端配置的默认主体 |
| 关联指向不存在或停用的主体、商户或签名配置无效 | 明确报错，不按“未配置”回退 |

学校由管理员设为默认主体；代码不固定 `school`。默认主体需要启用并配置完整。生成链接前检查所涉及主体的商户号、应用 ID 和可解析的 RSA 私钥、公钥；此检查不证明通联账户授权或密钥已在平台生效。

最终主体保存到 `zsjos_payment_order.subject_snapshot_json`。支付下单、查单、关单、回调验签、退款提交/查询/回调统一使用该快照；支付和退款回调的商户号、应用 ID 与快照比较，金额与对应支付/退款单比较。配置修改或主体停用不改写历史快照。快照损坏不打印原始 JSON，也不切换到全局商户配置。

新增稳定错误：`PAYMENT_DEFAULT_SUBJECT_MISSING`（1900017021）、`PAYMENT_SUBJECT_CONFIG_INVALID`（1900017022）、`PAYMENT_SUBJECT_SNAPSHOT_INVALID`（1900017023）；保留主体不存在、停用与商品无效错误。API 路径、请求/响应字段和权限不变。Workbench 现有错误提示和 H5 支付错误提示沿用后端消息；Admin 主体配置仍通过既有 API 维护配置。

已有 active 支付链接继续复用冻结快照，修改产品配置后不能直接复用旧链接来验证新规则。旧链接与已下单交易的处理边界见[发布说明](../deployment/payment-subject-deployment.md#支付主体路由修复发布说明2026-09-18)。
