# ZSJOS 公海池 API

Base path: `/admin-api/zsjos/lead/public-sea-pool`. All endpoints are tenant scoped and return the standard
`CommonResult` wrapper.

| Method | Path | Permission | Purpose |
| --- | --- | --- | --- |
| GET | `/page` | `zsjos:lead-public-sea-pool:query` | Paged active cycles; supports `status` and business lead number/contact `keyword` |
| GET | `/get?id=` | `zsjos:lead-public-sea-pool:query` | Visible cycle detail |
| GET | `/counts` | `zsjos:lead-public-sea-pool:query` | Active status counts in the caller's scope |
| GET | `/filter-profile` | `zsjos:lead-public-sea-pool:query` | Published server-owned `publicSeaPool` status groups and scoped counts |
| GET | `/{id}/candidates` | manage or manage-all | Enabled same-department B candidates |
| POST | `/{id}/assign` | manage or manage-all | Assign or reassign B |
| POST | `/{id}/exit` | manage or manage-all | Exit with a required reason |
| POST | `/{id}/transfer-request` | `zsjos:lead-public-sea-pool:transfer-request` | Current public-sea collaborator B requests formal transfer to self; other visible same-team sales are rejected |

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
only the current configured collaborator B may create follow-up. Formal owner A retains ownership
and read access but cannot create follow-up while the cycle is active. A waiting cycle has no personal
follow-up responsibility. Reassignment switches responsibility to the new B; manager exit restores A.
首购成交与补正必须先结束活动公海，不能因为 B 具备跟进权限而直接录单。每次变更都会锁定
活动周期。正式转派或主管退出结束公海后才恢复成交入口；正式归属和业绩归属仍按原业务规则
处理。

待分配状态没有协作人 B。符合正式负责人当前部门、销售资格和转派申请权限的同部门销售可以
申请转给自己，服务端按当前候选资格创建主管审批；正式负责人不能为自己发起该申请。

If the rejected active order was submitted by A, B uses
`POST /admin-api/zsjos/sales-order/{id}/continue-submit`. The original order becomes `superseded`,
the new immutable B-owned order points to it through `supersedesOrderId`, and the original points back
through `supersededByOrderId`. If B submitted the rejected order, the normal revise endpoint updates
that same order without changing `submitterUserId`.


## 公海跟进责任与待办

活动公海的跟进执行人取当前协助人 B，独立于 Lead/Opportunity 正式负责人 A。
`waiting_assignment` 不回退给 A；历史人工公海兼容记录也按其当前协助人执行。
跟进中、今日待跟进、逾期待跟进的员工负责人范围按实际跟进责任过滤；全部客资、归属查询、
提交人查询仍保留其原有关系范围。管理全量查询仍受既有管理权限控制。

跟进日历（含跨月搜索）、个人任务列表和数量、主管任务分桶、待办提醒使用同一责任口径。
即使旧 pending 跟进待办仍保存 A，活动公海期间也按当前 B 投影；待指派时不出现在个人待办，
也不发个人跟进提醒。已完成、已取消任务和历史跟进记录不重归属、不回写。
进入公海、指定/更换 B、清除失效协助人时，在原事务内取消错误的未完成跟进任务，不再转派旧任务给 B。
取消范围为活动公海中的首次跟进任务，以及执行人不是当前 B、创建于本次指派之前或待指派期间的跟进提醒。
即使旧任务已被上一版程序改到 B 名下，只要创建时间早于本次指派，仍会取消。
当前 B 在本次指派后产生的正常下次提醒保留；已完成/已取消记录、非公海客资、判定及其他业务任务不改写。

现有每 60 秒公海扫描增加存量清理步骤，每轮每租户最多处理 500 条，重复执行只命中仍为 pending 且仍满足公海条件的任务。
取消写入 status=cancelled、cancelled_at、cancel_reason，并递增 version；不物理删除，不重写跟进记录和历史负责人。
维护模式暂停扫描；某一步失败不会阻止下一轮重试。新后端部署运行后逐批处理存量，无需等待 B 提交跟进。
B 保存跟进前也执行同一清理，然后仅完成自己的正常旧提醒并创建新提醒。无待办时不凭空生成预约。
主管退出公海前先清理遗留错误任务，再恢复 A 的跟进责任；已取消的错误任务不会复活。

本次调整不修改正式归属、业绩、角色授权、成交前置条件或数据库结构。Admin 继续消费原管理查询
和只读跟进历史，Workbench 消费服务端列表与 availableActions；接口形状不变。
