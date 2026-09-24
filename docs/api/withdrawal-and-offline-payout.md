# Withdrawal and offline payout API

The independent partner frontend uses `/part-api/zsjos/**`; only an enabled ordinary partner may apply. Admin/workbench review APIs remain under `/admin-api/zsjos/**`. The server locks selected `available` cashback rows in ascending ID order, snapshots the full bank account, creates one Withdrawal and item rows, changes cashback to `withdrawing`, and starts BPM process `zsjos_partner_withdrawal` with the single task key `financeReview`. Reviewers are enabled System users who currently have `zsjos:withdrawal:review`; V063 creates the permission but intentionally assigns no finance user.

## State contract

- `pending_review`: partner may cancel; BPM approval changes it to `approved`, while BPM rejection/cancellation releases cashback and ends as `rejected`/`cancelled`.
- `approved`: displayed as pending payout. Cashback remains `withdrawing`; finance may reject with a reason and release it, or record payout.
- `paid`: immutable terminal result. Recording payout accepts optional `paidAt` and `remark`; bank transaction number and proof are no longer payout inputs. `paidAt` is the supplied business payout time and stays null when omitted; the operator and the existing system audit/record update time still identify the registration. Cashback changes to `withdrawn`.
- There is no balance account, partial cashback withdrawal, supervisor step, adjusted review amount, automatic transfer, fee, tax, reconciliation, payout failure state, or paid rollback.

Workbench quick filters, list cells and details display the same lifecycle labels as Admin:
`pending_review` → 待审核, `approved` → 待打款, `rejected` → 已驳回,
`paid` → 已打款, `cancelled` → 已取消. Filters still submit the original state codes.
These fixed workflow states are not administrator-editable dictionary choices.
Workbench amount and timestamp cells read raw response fields from the row, never formatted
ProTable render nodes; missing or invalid withdrawal amounts display `-` rather than a fabricated zero.

`zsjos_withdrawal_item.active_cashback_id` is a generated nullable key. Its tenant unique index permits unlimited inactive history while ensuring one cashback belongs to at most one active withdrawal. Rejection/cancellation sets item `active_flag=false` before the cashback can be selected again.

## 提现管理内审批与历史流程兼容

财务在提现管理详情内处理待审核记录。`pending_review` 提供“通过”和“驳回”；通过意见可为空，驳回原因必填且最多 500 字。通过后进入 `approved`（待打款），原有“驳回已通过申请”和“登记打款”继续可用；`paid`、`rejected`、`cancelled` 为只读结果。后端按记录 `version` 做并发保护，并由 `zsjos:withdrawal:review` 独立控制审核，查询权限不能替代审核权限。

审核仍由 `zsjos_partner_withdrawal` BPM 实例执行。业务页面调用 BPM 公共业务审核边界：优先使用当前财务自己的任务；符合提现流程、租户、业务键、财务节点和“一人完成”结构时，可受限接管其他原财务任务后办理。没有唯一任务、出现加签/委派/挂起、流程关联不一致或返现占用不一致时拒绝自动办理。通用 BPM 审批入口也执行相同的提现审核授权和理由规则。

历史正在运行的实例不重建、不改写流程 ID，继续复用原任务和结果事件。待办中的提现任务保留提醒，但办理入口跳转提现详情；通知业务详情同样进入 `/zsjos/withdrawal?withdrawalId={id}`。管理权限用户可读取 `GET /zsjos/withdrawal/{id}/compatibility` 做只读核对；仅在核对确认“流程已结束、审核结果明确、返现关系完整”时，具有审核权限的运维人员才可调用 `PUT /zsjos/withdrawal/{id}/sync-process-result` 幂等补同步。实例缺失、节点异常、加签/委派和关系异常只输出异常，不自动修复。

## Endpoints and permissions

| Endpoint | Permission | Boundary |
|---|---|---|
| `POST /zsjos/withdrawal/apply` | `zsjos:withdrawal:apply` | Enabled ordinary partner, own available cashback only |
| `PUT /{id}/cancel` | `zsjos:withdrawal:apply` | Applicant and `pending_review` only |
| `GET /my-page`, `GET /my/{id}` | `zsjos:withdrawal:my-query` | Applicant rows; personal-only readers receive masked card and redacted payout fields; management readers receive complete data |
| `GET /page`, `GET /{id}` | finance/admin query | Tenant-scoped management rows; full card and payout fields; sensitive read audit |
| `GET /{id}/finance-detail` | finance/admin query, or authorized tenant read-all | Full card; every read writes business audit without card data |
| `PUT /{id}/reject-approved` | `zsjos:withdrawal:review` | Finance rejection after BPM approval and before payout |
| `PUT /{id}/payout` | `zsjos:withdrawal:payout` | Immutable single offline payout; optional `paidAt` and `remark` |
| `PUT /batch-payout` | `zsjos:withdrawal:payout` | Atomic batch of 1–100 positive IDs; shared optional `paidAt` and `remark` |
| `POST /proof/upload` | `zsjos:withdrawal:payout` | Legacy private upload endpoint; no longer used by payout forms |

Feature permissions, list scope and `withdrawal` object checks are cumulative. The weekly reminder defaults to Thursday 10:30, uses the configured overdue-day threshold (default 7), resolves recipients from the review permission, and skips while maintenance mode is active. V052 does not deploy BPM or grant roles; deployment must publish the exact process and task keys before applications are enabled.

The partner start subject is external, but `zsjos_partner_withdrawal` may use the BPM `START_USER_SELECT` strategy when the request supplies the configured internal finance reviewer IDs for `financeReview`. BPM validates those IDs against enabled System users before creating the instance. Strategies that depend on the external starter's own user, department, or department leaders remain unsupported for partner-subject starts.

The Vue withdrawal list exposes direct asynchronous export only when the user has both `zsjos:export:withdrawal` and full withdrawal-list visibility. It exports all rows matching the current status filter, retains masked card numbers, creates an asynchronous task without polling the business page, and links to the existing export-task center.

Management `GET /page`, `POST /search-page`, and detail reads return the complete `cardNumber` snapshot and payout fields (transaction number, proof, remark, operator and payout time) to users with `zsjos:withdrawal:finance-query` or `zsjos:withdrawal:admin-query`. Both Vue Admin and React Workbench display `cardNumber` directly in lists and details, without another full-account action. List reads carry sensitive-read audit; full detail reads retain business audit without card contents. Tenant and object checks remain mandatory. The existing `maskedCardNumber` compatibility field remains masked; personal-only and Partner reads, saved-card selectors and exports retain their existing masking contracts. Missing historical values are not fabricated.

## Single and batch payout registration

Both Vue Admin and React Workbench support selecting current-page `approved` rows and invoking **批量登记打款**. The same existing server-owned `zsjos:withdrawal:payout` permission controls single and batch entry points; no role assignments or menu permissions are seeded. Refreshing, filtering or paging the list clears its selection. The dialog freezes the selected IDs, displays their count and applies the same time/remark to every selected record. Failed commands preserve the form for correction/retry; an in-flight submission disables further submission and dialog dismissal.

Single requests accept `{ "paidAt": "2026-09-21T15:30:00", "remark": "登记说明" }`; both fields may be omitted. Batch requests add `ids: [1, 2]`. `paidAt` follows the existing Beijing-time date/time JSON contract. Remark is trimmed and limited to 500 characters. A blank optional time is not replaced with the registration time. Existing date-filtered payout reports therefore exclude undated payouts from bounded date ranges, while unbounded paid totals still include them. Finance detail displays the stored time and remark; personal-only/partner projections retain their existing finance-field redaction.

The batch service authorizes all distinct objects before mutation, processes IDs in ascending order, then calls the proxied single-record service (`@ZsjosPermission`, tenant-bound row lock, `approved` state validation and cashback transition) inside one outer transaction. Missing/cross-tenant objects, denied objects, changed withdrawal/cashback states or any persistence/event failure abort the whole batch. Withdrawal, cashback, transactional audit and notification outbox writes roll back together. Already-paid rows cannot be registered twice.

No schema or migration changes are necessary: the existing `paid_at`, `payout_remark`, bank transaction and proof columns are nullable. Existing paid records and any historical proof/transaction projections remain untouched. There is no data cleanup, synthetic historical timestamp or permission reassignment.

## 当前租户管理员读取

Personal page/search-page accept optional readScope=SELF|ALL|USER and targetUserId; defaults remain SELF. Tenant read-all users may use the explicit finance-detail sensitive read through an existing authorized query entry; the service requires finance/admin query or tenantReadAll and records the read audit. Both frontends offer a read-only scope; the dedicated full-account action remains only for personal tenant read-all views without management permission. Apply/cancel/review/payout are unchanged.

## 状态标签与运行版本核对

### 财务来源追溯

财务返现列表补充受益人、合作方、客户／学员、来源单据、产品、返现基数和比例；返现详情通过 `GET /zsjos/cashback/{id}` 展示计算快照、来源权限状态及提现历史。提现管理补充申请人、合作方和来源返现数量；提现详情通过 `GET /zsjos/withdrawal/{id}/sources` 按保存的明细金额展示返现、客资／订单摘要和产品项。

来源摘要只有在当前操作者同时拥有对应客资／订单页面权限和对象读取权限时返回；财务权限不会扩大客资或订单可见范围。有效客资返现明确标记无关联订单，历史缺失与无权限分别显示，不使用当前数据反推历史身份。来源列表和详情均按需分页，不改变个人／合作方投影和导出契约。

Vue 管理端、React 工作台的状态标签和筛选选项来自 `/admin-api/zsjos/advanced-filter/catalog?scene=withdrawal`。首次加载时显示“状态加载中”；失败或未识别的值显示“状态暂不可用”，保留错误提示和重试，不直接展示英文编码。重试保留本页面已成功读取的标签，切换场景或失去访问时清空 React 缓存；失败期间筛选项不可选。

如列表可查而目录返回 403，先检查运行包是否包含提现场景及 `my-query`、`finance-query`、`admin-query` 查询授权，再核对账户配置。不能用新增角色授权或前端静态选项掩盖旧运行包问题。部署当前源码后须验收五个中文状态及筛选；源码与类型检查通过不代表运行端已修复。
