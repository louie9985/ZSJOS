# ZSJOS BPM 业务任务定位接口

## 接口

- `GET /zsjos/bpm/business-task-target?taskId=&view=todo|done`
- 权限：`bpm:task:query`

## 返回

```json
{
  "supported": true,
  "route": "/zsjos/appeals",
  "query": {
    "appealId": 123,
    "leadId": 456,
    "handled": false
  },
  "bizType": "lead_appeal"
}
```

字段含义：

- `supported`：是否已接入员工端业务页。
- `route`：员工端目标路由。
- `query`：跳转时附带的查询参数。
- `bizType`：`sales_order`、`lead_appeal` 或 `unsupported`。
- `message`：`supported=false` 时返回的用户提示。

## 支持范围

- `zsjos_sales_order_dual_approval`
  - 任务关系和对象权限仍由后端校验。
  - 返回成交订单审批页 `/zsjos/sales-order-approvals`。
  - `view=done` 时返回只读目标，不要求待办仍存在。
- `zsjos_lead_appeal_review`
  - 按 `businessKey=lead-appeal:{appealId}` 校验流程实例、阶段权限和对象关系。
  - 返回申诉页 `/zsjos/appeals`，并附带 `appealId`、`leadId`、`handled`。
- 其他已存在流程
  - 统一返回 `supported=false`。
  - 文案：`该流程暂未接入员工端业务审批页，请在完整 BPM 表单中处理。`

## 错误语义

- 401 或登录失效：前端提示当前账号无权或需重新登录。
- BPM 任务不属于当前用户、业务键不匹配、流程实例不匹配或对象权限不足：返回稳定权限错误。
- 网络、超时或接口异常：前端提示定位失败，请重试。
