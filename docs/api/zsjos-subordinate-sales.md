# 下属销售 API

Base path: `/admin-api/zsjos/subordinate-sales`. All endpoints require authentication, current tenant context, the listed feature permission, and a current System department-leader relationship covering the target sales department or one of its ancestors.

## Queries

- `GET /page`: permission `zsjos:subordinate-sales:query`; supports `keyword`, `accountStatus`, `presence`, `accepting`, `pageNo`, and `pageSize`.
- `GET /{salesUserId}/overview`: returns the same metric contract for one managed sales user.
- `GET /{salesUserId}/leads`: reuses the Lead management table response and limits rows to current ownership by that managed sales user.
- `GET /{salesUserId}/tasks`: returns current pending first/subsequent follow-up tasks; `bucket` is `overdue`, `today`, `future`, or `unscheduled` using Beijing time. An empty task set returns an empty page and never issues an empty-ID Lead query.
- `GET /transfer-candidates`: returns enabled, currently eligible sales specialists inside the manager's department tree.

The list includes disabled sales accounts that still hold the stable `sales_specialist` post. `canReceiveNewLeads` is true only when account enabled, sales eligibility valid, presence online, and intake mode accepting. Lead-category metrics follow the enabled System dictionary order and append `未配置` only when historical unmatched values exist.

### 员工卡片今日统计

`/page` 和 `/{salesUserId}/overview` 同时返回下列新增字段。今日为北京时间自然日 `[00:00, 次日 00:00)`，使用现有租户及主管可见员工范围；各员工的数据由服务端批量查询，不由前端汇总。无记录返回 0。

| 字段 | 展示与统计口径 |
| --- | --- |
| `todayFollowUpRemainingCount` | 今日到期、仍为 pending 的首跟或跟进提醒，按 Lead 去重 |
| `todayFollowUpTotalCount` | 今日到期、状态为 pending 或 completed 的首跟或跟进提醒，按 Lead 去重；取消任务不计入。同一 Lead 有已完成和待处理任务时仍计入剩余数 |
| `todayAssignedCount` | 今日 dispatch 派单历史，按候选销售及 Lead 去重 |
| `todayMissedCount` | 今日 timeout 接单超时历史，按候选销售及 Lead 去重；不等同于首跟超时，也不包含主动拒接 |
| `todayReceivedCount` | 今日 accept / claim 接收或抢单成功历史，按接收销售及 Lead 去重 |
| `todayQualifiedCount` | 今日 lead_qualified_valid 业务事件，按判定操作人及 Lead 去重 |
| `todayFollowUpRecordCount` | 今日该操作人的 Lead 跟进记录和 Opportunity 跟进记录合计，按记录数统计 |
| `todayOrderAmount` | 该销售提交、今日生效且当前状态仍为 effective 的订单金额之和，与原累计成交额的归属及有效状态口径一致 |
| `pendingQualificationCount` | 截至查询时，该销售当前名下按 `LeadStateProjection.qualification` 仍为 pending 的客资总数。**不限制是否满 3 日，不限制判定截止日期** |

派单、接收、判定及跟进按发生时的候选人/操作人归属，后续客资转移不抹去这些今日事实。跟进完成状态是查询时的任务状态，非日初冻结任务清单；今日新增/取消任务会改变分母。

Workbench 收件箱卡片右上角展示绿色“已完成”（剩余为 0，包括无今日任务），否则展示彩色“剩余 / 总数”。今日指标为两列四行，末项文案为“剩余未判定”，不展示“风险”。保留员工头像、姓名、账号/手机号、启停用、在线、接单与可接收状态，以及原累计有效客资、成交数和金额。旧后端缺少新增字段时显示“— / 状态待更新”，不伪造零值或完成状态。

原 `todayPendingCount` 继续表示今日待跟进**任务数**，用于兼容既有详情和筛选；新增两个跟进字段表示**客资数**。原累计字段、权限及请求契约不变。Vue Admin 的 `/page` 消费方继续使用原有字段，本次不复制 Workbench 卡片到管理端。加载本次后端后方可获得新增指标；不需要数据库迁移或权限分配。

Selecting an in-scope Lead reuses the Workbench owned-Lead detail. Read tabs retain their dedicated
permissions and live object checks. The detail exposes only server-projected `SUPERVISOR_*` Lead
commands; it never turns the manager view into the owner-sales write surface.

- `GET /zsjos/lead/get?id={leadId}`: complete Lead overview.
- `GET /zsjos/lead/{leadId}/follow-ups/page`: paged follow-up history.
- `GET /zsjos/lead/appeal/lead/{leadId}/list`: appeal history.
- `GET /zsjos/lead-complaint/lead/{leadId}/list`: complaint history with display names and signed evidence URLs.
- `GET /zsjos/sales-order/lead/{leadId}/customer-orders` and `/{orderId}`: customer order list and detail; only the Lead owner, an in-scope department manager, or `zsjos:lead:query-all` may pass the order-specific object action. A submitter-only relationship is insufficient.

## Commands

- `PUT /{salesUserId}/account-status`: permission `zsjos:subordinate-sales:account-status`; body `{status, reason}`. System owns the status mutation and revokes tokens on disable.
- `PUT /{salesUserId}/dispatch-mode`: permission `zsjos:subordinate-sales:dispatch-mode`; body `{accepting, reason}`.
- `PUT /dispatch-mode/pause-all`: permission `zsjos:subordinate-sales:pause-all`; no request body. The server resolves every currently managed user holding the stable `sales_specialist` post, including disabled accounts, and persists `accepting=false`. It returns `{totalCount, changedCount, alreadyPausedCount}` and writes one `dispatch_mode_bulk_pause` audit row with reason `主管一键下班` only for each actual `accepting -> paused` change. It does not disable accounts, force page presence offline, transfer Leads, or affect existing assignments.
- `POST /leads/{leadId}/transfer` and `/leads/batch-transfer`: permission `zsjos:subordinate-sales:lead-transfer`; body uses `targetUserId` and `reason`.
- `POST /leads/{leadId}/restore` and `/leads/batch-restore`: permission `zsjos:subordinate-sales:lead-restore`.
- `POST /leads/{leadId}/recycle` and `/leads/batch-recycle`: permission `zsjos:subordinate-sales:lead-recycle`.
- `POST /leads/{leadId}/release-claim-pool` and `/leads/batch-release-claim-pool`: permission `zsjos:subordinate-sales:lead-release-claim-pool`.
- `POST /leads/{leadId}/release-public-sea` and `/leads/batch-release-public-sea`: permission `zsjos:subordinate-sales:lead-release-public-sea`; `collaboratorUserId` is optional.

The legacy `/leads/batch-public-sea` endpoint remains temporarily available under its existing
permission for compatible clients, but it uses the same valid-pre-deal public-sea state validation.

Supervisor state rules are authoritative on the backend: submitted Leads may be transferred,
recycled, or released to the claim pool; suspended Leads additionally support restore; recycle-pending
Leads support transfer or claim-pool release; valid pre-deal Leads support transfer or release to the
public sea. Won and closed Leads reject every supervisor command. Public-sea release preserves formal
ownership and may assign an eligible actual follow-up salesperson in the created public-sea cycle;
that collaborator may follow up but cannot submit a first-purchase order until a formal transfer is completed.

`assignmentStatus=recycle_pending` is displayed as `回收待处理`. It means the supervisor has removed
the current formal owner, retained that employee in `recycleSourceOwnerUserId`, and must next either
transfer the Lead or release it to the claim pool. It is an assignment state, not a Lead main status.

Error code `1900003058` remains the stable supervisor state-conflict code. Its message includes the
attempted command, current Lead/assignment labels, the applicable state requirement, and whether a
close timestamp exists. Unknown historical values are returned as `未知客资状态（原值）` or
`未知分配状态（原值）`; clients must not replace these details with a generic failure.

The subordinate Lead list returns both `status` and `assignmentStatus`. Workbench displays both,
retains selected-row state across pages, and enables a batch command only when every selected Lead
meets its basic state condition. Mixed selections are never silently filtered; the UI reports the
number of inapplicable Leads, while the backend repeats the authoritative validation inside each
Lead transaction to cover concurrent changes.

Every reason is trimmed, required, and limited to 500 characters. Batch commands accept 1 to 200 IDs and return `{successCount, failureCount, items[]}`. Each item contains internal `leadId`, user-visible `leadNo`, `success`, stable `code`, and `message`; each Lead runs in an independent transaction.

Manual public-sea release preserves Lead owner, main status, and assignment status. It does not use the claim-pool value `assignment_status=public_pool` and does not make the Lead claimable. A collaborator must use the transfer-request BPM flow or supervisor transfer command before entering a first-purchase order.

This state-guidance behavior uses existing Lead columns and the supervisor permissions delivered by
V139/V140/V142. It requires no new SQL migration, menu grant, dictionary entry, or data repair.
