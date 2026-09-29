# ZSJOS 公海池 API

Base path: `/admin-api/zsjos/lead/aging-pool`. All endpoints are tenant scoped and return the standard
`CommonResult` wrapper.

| Method | Path | Permission | Purpose |
| --- | --- | --- | --- |
| GET | `/page` | `zsjos:lead-aging-pool:query` | Paged active cycles; supports `status` and business lead number/contact `keyword` |
| GET | `/get?id=` | `zsjos:lead-aging-pool:query` | Visible cycle detail |
| GET | `/counts` | `zsjos:lead-aging-pool:query` | Active status counts in the caller's scope |
| GET | `/filter-profile` | `zsjos:lead-aging-pool:query` | Published server-owned `agingPool` status groups and scoped counts |
| GET | `/{id}/candidates` | manage or manage-all | Enabled same-department B candidates |
| POST | `/{id}/assign` | manage or manage-all | Assign or reassign B |
| POST | `/{id}/exit` | manage or manage-all | Exit with a required reason |
| POST | `/{id}/transfer-request` | `zsjos:lead-aging-pool:transfer-request` | Current public-sea collaborator B requests formal transfer to self; other visible same-team sales are rejected |

### 常驻筛选

`GET /page` 与 `POST /search-page` 的 `keyword` 去除首尾空白后，对业务客资编号 `leadNo`、提交姓名、手机号和微信号进行 OR 模糊匹配；空白关键词不增加搜索条件，不使用内部 `leadId` 作为编号搜索。关键词与租户、逻辑删除、可见范围、公海状态及高级筛选取交集。Admin 与 Workbench 均沿用原有 `keyword` 请求字段和响应结构。

Workbench 公海池复用客资管理 `lead-simple-status-shell` 样式，常驻展示全部、归属我的、我跟进的及服务端发布的状态选项；单选切换后重新从第一页加载。高级筛选不隐藏常驻栏，与选中的常驻条件取交集。

`GET /page` 和 `POST /search-page` 新增可选 `relationScope`：`owned` 为当前 ADMIN 用户是正式归属人 A，`following` 为当前指派的协同销售 B（不表示历史上写过跟进记录）。用户身份由服务端登录上下文传入，关系条件与现有租户、可见范围、关键词及高级筛选取交集，manage-all 用户也不绕过个人筛选。不传该字段保持原有范围；Admin 现有调用不需要新增参数，响应结构不变。`counts.all` 仍代表可见范围内的全部活动公海数量，列表 total 代表当前筛选结果。

Assign body:

```json
{"salesUserId": 123, "idempotencyKey": "uuid"}
```

Exit body:

```json
{"reason": "business reason", "idempotencyKey": "uuid"}
```

Transfer-request body uses the same required `reason` and `idempotencyKey` fields. The deployment must
provide BPM process definition `zsjos_lead_transfer_request` with task key `ownerManagerReview` before
the endpoint is enabled. An unavailable process returns a stable business error and rolls back the
request row.

Transfer-request creation locks the cycle and then its Lead, matching the exit workflow's lock order,
and revalidates the Lead relationship and caller visibility. An idempotent replay is returned only
after those authorization checks and must match both the locked Lead and requesting user; it remains
replayable after the original request changes the cycle status. A new request still requires an active
cycle. Guessing another tenant user's or an out-of-scope cycle ID never bypasses object authorization.

Active statuses are `waiting_assignment`, `assigned`, and `deal_pending`. Entry is timed from the
latest Opportunity follow-up, or Opportunity creation when no follow-up exists. Responses include full
contact fields only after server-side object authorization and include `availableActions`; clients
must not derive actions from A/B identities. Stable command conflicts distinguish missing cycles,
manager denial, invalid candidates, invalid owner, invalid state, and idempotency-key conflicts.

While an active public-sea cycle exists, `availableActions` never includes first-purchase deal entry
or deal revision. A qualifying same-team sales user may receive `REQUEST_TRANSFER` in
`waiting_assignment`; only the assigned collaborator B may receive it in `assigned`. Formal transfer
or manager exit ends the active cycle before deal entry becomes available. `deal_pending` remains only
for compatibility with historical in-flight approvals.

Visibility, manager authority, and collaborator candidates follow formal owner A's current department.
The entry-time department snapshot remains audit data only, so organization changes do not leave the
Opportunity visible to A's former team.

Existing lead detail, follow-up, and sales-order APIs remain authoritative. During an active cycle,
formal owner A may continue follow-up; configured collaborator B may follow-up after assignment.
首购成交与补正必须先结束活动公海，不能因为 A/B 具备跟进权限而直接录单。每次变更都会锁定
活动周期。正式转派或主管退出结束公海后才恢复成交入口；正式归属和业绩归属仍按原业务规则
处理。

待分配状态没有协作人 B。符合正式负责人当前部门、销售资格和转派申请权限的同部门销售可以
申请转给自己，服务端按当前候选资格创建主管审批；正式负责人不能为自己发起该申请。

If the rejected active order was submitted by A, B uses
`POST /admin-api/zsjos/sales-order/{id}/continue-submit`. The original order becomes `superseded`,
the new immutable B-owned order points to it through `supersedesOrderId`, and the original points back
through `supersededByOrderId`. If B submitted the rejected order, the normal revise endpoint updates
that same order without changing `submitterUserId`.
