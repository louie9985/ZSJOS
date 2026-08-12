# ZSJOS 成交订单 API

## 权限与对象范围

- `zsjos:sales-order:create`：当前归属销售录入或补正本人客资的成交订单。
- `zsjos:sales-order:query-own`：分页和读取当前用户作为实际提交人的历史订单。详情仍执行 `read-own` 对象校验，不随客资负责人变化。
- `zsjos:sales-order:query`：读取具备对象关系的订单详情。
- `zsjos:sales-order:review`：工作台成交审批菜单与直接详情查询权限。审批池、通过和驳回接口的业务授权以配置部门成员关系和本人 BPM 任务为准，不按角色名称推断。

## 接口

- `GET /zsjos/sales-order/product/catalog`：全部启用产品/SKU 目录。
- `POST /zsjos/sales-order/lead/{leadId}/submit`：创建订单、订单项、第一轮快照并启动双中心会签。
- `PUT /zsjos/sales-order/{id}/resubmit`：修改 `revision_required` 原订单并创建新审批轮次。
- `GET /zsjos/sales-order/{id}`：订单、课程、凭证和当前审批轮次详情；`registrationApproval` 与 `financeApproval` 分别返回报名履约、财务节点的 `pending/approved/rejected/cancelled` 汇总状态、实际审核人用户 ID/姓名及节点时间。审核身份和结果只读自 BPM 当前任务和历史任务，不在订单域重复持久化；界面展示审核人姓名、结果和审核时间，不展示用户 ID。
- `GET /zsjos/sales-order/my-page`：本人提交订单的轻量分页，支持 `status` 和订单号/学员姓名/手机号 `keyword`。
- `GET /zsjos/sales-order/my-status-counts`：本人订单的全部、待审核、已驳回待修改、已通过数量。
- `GET /zsjos/sales-order/my/{id}`：本人订单完整详情；已驳回订单包含最新轮次 `decisionReason` 和 `canRevise`。
- `GET /zsjos/sales-order/approval/filter-profile`：返回当前租户已发布的待处理/已处理方案，以及当前用户按审批配置部门解析出的 `centers`。单中心用户只返回本中心；同时落入两个配置部门范围的用户返回报名履约和财务两个中心。
- `GET /zsjos/sales-order/approval/inbox-page?center=registration|finance&groupKey=pending&optionKey=all&keyword=`：按当前用户审批任务、处理状态和中心分页查询轻量订单列表，支持订单号、学员姓名和手机号搜索；`handled` 仍可作为兼容参数。服务端将筛选条件与当前用户允许的 BPM 任务节点取交集，伪造无权中心返回权限错误，前端隐藏筛选项不是授权边界。
- `PUT /zsjos/sales-order/{id}/approve`、`/reject`：处理当前 BPM 任务，必须提交 `taskId` 和审批意见。

审批详情仅在当前中心节点仍为 `pending` 且当前待办仍提供 `taskId` 时显示通过、驳回操作。任一审核人处理该中心节点后，同中心其他用户不再显示操作按钮；服务端仍以 BPM 待办状态校验并拒绝过期或重复处理。
- `POST /zsjos/sales-order/voucher/upload`：上传 JPG、PNG、WebP 或 PDF，最多 10 MB；最终提交最多引用 9 个文件。工作台选择文件时只在浏览器本地暂存和预览，用户确认提交后才调用此接口；上传失败会保留表单和成功引用，重试只上传失败或尚未上传的文件。

提交命令必须携带 `idempotencyKey`。同一次录单或补正意图的快速重复点击、凭证上传失败和请求重试必须复用该键，只有服务端确认成功后才轮换；首次录单在锁定客资行后会再次检查该键，使并发相同请求返回已创建订单，而不是重复创建或误报已有活动订单。订单总金额不由客户端提交，服务端以各课程 `actualAmount` 重新汇总。手机号和微信号至少一项；非零订单必须引用至少一份由当前销售上传的缴费凭证。省市名称由服务端根据省市级联编码生成快照，五类订单字典值由系统字典 API 校验。

## 状态

- `pending_approval`：当前 BPM 轮次处理中，同一客资禁止新建活动订单。
- `revision_required`：任一中心驳回或流程取消，原订单可补正重提，同一客资仍禁止新建活动订单。
- `effective`：两个中心均通过；关联 Opportunity 同事务进入 `won`。

审批轮次保存最终非通过原因快照。补正重提创建新的空原因轮次，不覆盖上一轮审计记录。
