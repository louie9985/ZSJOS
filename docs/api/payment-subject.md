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


## 支付主体路由与快照（2026-09-21）

生成支付链接时，以已保存购买明细为准，通过真实 `skuRef` 查询 SKU 的 `spuId`，复用商品服务校验产品、SKU 的启用状态和归属。不解析 SKU 编号字符串，不接受前端指定收款主体。同产品多个 SKU 去重，不按金额、数量、排列顺序分配主体，不拆分付款。

先逐产品求有效主体，再按主体 ID 去重：无产品关联时读取当前租户 `subjectCode=school`；所有有效主体相同则使用共同主体；任意主体冲突则整笔读取当前租户 `subjectCode=company`，包括其他主体之间的冲突。

| 产品配置组合 | 最终主体 |
| --- | --- |
| 单产品未配置、全部未配置、学校＋未配置 | 学校 |
| 全部学校／全部公司／全部同一个其他主体 | 共同主体 |
| 公司＋未配置、学校＋公司 | 公司 |
| 其他主体＋学校／公司／未配置、两个不同其他主体 | 公司 |

`school/company` 是本业务明确约定的路由编码，由后端 `PaymentSubjectCodes` 集中定义；主体名称、主键、商户和签名凭据仍来自当前租户配置。`isDefault` 保留兼容，但不参与本支付路由。只有需要回退或冲突时才读取相应编码，单一已配置主体不依赖学校、公司存在。

所有参与主体及最终主体都必须启用、商户号/应用 ID 完整且 RSA 私钥、公钥可解析。有关联但主体不存在、删除、停用或凭据无效时直接报错，不能按未配置回退，也不能通过冲突走公司掩盖异常。检查不证明通联账户授权或密钥已在平台生效。

新增稳定错误：`PAYMENT_SCHOOL_SUBJECT_MISSING`（1900017024）表示缺少学校主体；`PAYMENT_COMPANY_SUBJECT_MISSING`（1900017025）表示冲突时缺少公司主体。沿用主体不存在、停用、`PAYMENT_SUBJECT_CONFIG_INVALID`（1900017022）、`PAYMENT_SUBJECT_SNAPSHOT_INVALID`（1900017023）及商品校验错误。原默认主体缺失错误保留定义兼容，本选路不再使用。

最终主体保存到 `zsjos_payment_order.subject_snapshot_json`。支付下单、查单、关单、回调验签、退款提交/查询/回调统一使用该快照；回调的商户号、应用 ID 与快照比较，金额与对应支付/退款单比较。修改配置、编码或停用主体不改写历史快照；快照损坏不打印原始 JSON，也不切换到全局商户配置。

已有 `created/waiting/paid` 链接继续复用冻结快照；需要切换时通过现有取消操作，确认关单成功后重新生成。状态不确定、关单失败时不能重建切换。API 路径、请求/响应字段、权限和快照结构不变；Workbench/H5 沿用后端错误提示。

配置页面显示“已配置支付主体”；无关联显示“未配置（按规则使用学校）”，有关联但无法解析主体显示“关联主体异常”。筛选仍按实际配置关联，不将未配置产品算作已配置学校；配置时间保持为空，不补写关联记录。

发布与旧交易处理见[发布说明](../deployment/payment-subject-deployment.md)。
