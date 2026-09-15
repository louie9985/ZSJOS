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

产品配置分页从当前租户的有效产品出发，包含未配置主体的产品；主体筛选在数据库分页前生效。
返回 `productId`、`productName`、`paymentSubjectId`、`subjectCode`、`subjectName`、`configTime`。
配置时间来自关联记录 update_time；未配置时为空。不生成不存在的 productType 字段。
历史删除关联及其他租户关联不参与展示；配置下拉仅显示启用主体，查询筛选可选停用主体。
重复配置更新现有有效关联，不反复软删插入，避免 `(tenant_id,product_id,deleted)` 唯一键冲突。

两个页面分别处理查询权限不足、加载、空列表和失败重试；产品选项失败不阻止产品列表加载。
后端仍独立检查每个接口权限；精简选择项权限不授予主体详情或密钥读取权限。

验证入口：后端 `PaymentSubjectControllerTest`、`PaymentSubjectQueryTest`；Admin
`node --test tests/paymentSubjectApi.test.ts`，类型检查、定向 lint 与 `pnpm build:local`。
发布时需要加载本次后端编译结果并交付更新后的 Admin（含工作台嵌入版本），仅刷新旧后端不会注册新增接口。

## 开发库关联表修正

V241 在 V239 后执行：新表使用 `payment_subject_id bigint`；已有空旧表的 `subject_code` 转为该字段，索引随列转换。可重复执行；非空旧表由 CHECK 守卫拒绝，必须另行审核映射，不得用 `--force` 忽略错误。执行前备份结构，DDL 隐式提交；仅表仍为空时可恢复旧结构，恢复后旧结构与当前接口不兼容。已部署环境须另行审核升级流程。
