# ZSJOS 成交订单 API

## 权限与对象范围

- `zsjos:sales-order:create`：当前归属销售录入或补正本人客资的成交订单。
- `zsjos:sales-order:query`：读取具备对象关系的订单详情。
- `zsjos:sales-order:review`：工作台成交审批菜单与直接详情查询权限。审批池、通过和驳回接口的业务授权以配置部门成员关系和本人 BPM 任务为准，不按角色名称推断。

## 接口

- `GET /zsjos/sales-order/product/catalog`：全部启用产品/SKU 目录。
- `POST /zsjos/sales-order/lead/{leadId}/submit`：创建订单、订单项、第一轮快照并启动双中心会签。
- `PUT /zsjos/sales-order/{id}/resubmit`：修改 `revision_required` 原订单并创建新审批轮次。
- `GET /zsjos/sales-order/{id}`：订单、课程、凭证和当前审批轮次详情。
- `GET /zsjos/sales-order/approval/inbox-page?handled=false`：当前用户 BPM 待办或已办。
- `PUT /zsjos/sales-order/{id}/approve`、`/reject`：处理当前 BPM 任务，必须提交 `taskId` 和审批意见。
- `POST /zsjos/sales-order/voucher/upload`：上传 JPG、PNG、WebP 或 PDF，最多 10 MB；最终提交最多引用 9 个文件。

提交命令必须携带 `idempotencyKey`。订单总金额不由客户端提交，服务端以各课程 `actualAmount` 重新汇总。手机号和微信号至少一项；非零订单必须引用至少一份由当前销售上传的缴费凭证。省市名称由服务端根据省市级联编码生成快照，五类订单字典值由系统字典 API 校验。

## 状态

- `pending_approval`：当前 BPM 轮次处理中，同一客资禁止新建活动订单。
- `revision_required`：任一中心驳回或流程取消，原订单可补正重提，同一客资仍禁止新建活动订单。
- `effective`：两个中心均通过；关联 Opportunity 同事务进入 `won`。
