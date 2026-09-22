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

## Endpoints and permissions

| Endpoint | Permission | Boundary |
|---|---|---|
| `POST /zsjos/withdrawal/apply` | `zsjos:withdrawal:apply` | Enabled ordinary partner, own available cashback only |
| `PUT /{id}/cancel` | `zsjos:withdrawal:apply` | Applicant and `pending_review` only |
| `GET /my-page`, `GET /my/{id}` | `zsjos:withdrawal:my-query` | Applicant rows; masked card; no transaction number, proof, payout remark, payout operator or payout time |
| `GET /page`, `GET /{id}` | finance/admin query | All rows; masked card; payout finance fields redacted |
| `GET /{id}/finance-detail` | `zsjos:withdrawal:finance-query` | Full card; every read writes business audit without card data |
| `PUT /{id}/reject-approved` | `zsjos:withdrawal:review` | Finance rejection after BPM approval and before payout |
| `PUT /{id}/payout` | `zsjos:withdrawal:payout` | Immutable single offline payout; optional `paidAt` and `remark` |
| `PUT /batch-payout` | `zsjos:withdrawal:payout` | Atomic batch of 1–100 positive IDs; shared optional `paidAt` and `remark` |
| `POST /proof/upload` | `zsjos:withdrawal:payout` | Legacy private upload endpoint; no longer used by payout forms |

Feature permissions, list scope and `withdrawal` object checks are cumulative. The weekly reminder defaults to Thursday 10:30, uses the configured overdue-day threshold (default 7), resolves recipients from the review permission, and skips while maintenance mode is active. V052 does not deploy BPM or grant roles; deployment must publish the exact process and task keys before applications are enabled.

The partner start subject is external, but `zsjos_partner_withdrawal` may use the BPM `START_USER_SELECT` strategy when the request supplies the configured internal finance reviewer IDs for `financeReview`. BPM validates those IDs against enabled System users before creating the instance. Strategies that depend on the external starter's own user, department, or department leaders remain unsupported for partner-subject starts.

The Vue withdrawal list exposes direct asynchronous export only when the user has both `zsjos:export:withdrawal` and full withdrawal-list visibility. It exports all rows matching the current status filter, retains masked card numbers, creates an asynchronous task without polling the business page, and links to the existing export-task center.

Only `GET /{id}/finance-detail` returns the full card and payout finance fields, including bank transaction number, proof, payout remark, payout operator and payout time. Ordinary list/detail projections never generate proof pre-signed URLs. The Workbench withdrawal table omits payout time because ordinary list responses redact it; authorized finance detail remains the entry for viewing the stored payout time. Table renderers read raw row fields for amounts, lifecycle states and timestamps, rather than treating ProTable formatted nodes as API values.

## Single and batch payout registration

Both Vue Admin and React Workbench support selecting current-page `approved` rows and invoking **批量登记打款**. The same existing server-owned `zsjos:withdrawal:payout` permission controls single and batch entry points; no role assignments or menu permissions are seeded. Refreshing, filtering or paging the list clears its selection. The dialog freezes the selected IDs, displays their count and applies the same time/remark to every selected record. Failed commands preserve the form for correction/retry; an in-flight submission disables further submission and dialog dismissal.

Single requests accept `{ "paidAt": "2026-09-21T15:30:00", "remark": "登记说明" }`; both fields may be omitted. Batch requests add `ids: [1, 2]`. `paidAt` follows the existing Beijing-time date/time JSON contract. Remark is trimmed and limited to 500 characters. A blank optional time is not replaced with the registration time. Existing date-filtered payout reports therefore exclude undated payouts from bounded date ranges, while unbounded paid totals still include them. Finance detail displays the stored time and remark; ordinary/partner projections retain their existing finance-field redaction.

The batch service authorizes all distinct objects before mutation, processes IDs in ascending order, then calls the proxied single-record service (`@ZsjosPermission`, tenant-bound row lock, `approved` state validation and cashback transition) inside one outer transaction. Missing/cross-tenant objects, denied objects, changed withdrawal/cashback states or any persistence/event failure abort the whole batch. Withdrawal, cashback, transactional audit and notification outbox writes roll back together. Already-paid rows cannot be registered twice.

No schema or migration changes are necessary: the existing `paid_at`, `payout_remark`, bank transaction and proof columns are nullable. Existing paid records and any historical proof/transaction projections remain untouched. There is no data cleanup, synthetic historical timestamp or permission reassignment.

## 当前租户管理员读取

Personal page/search-page accept optional readScope=SELF|ALL|USER and targetUserId; defaults remain SELF. Tenant read-all users may use the explicit finance-detail sensitive read through an existing authorized query entry; the service still requires finance-query or tenantReadAll and records the read audit. Both frontends offer a read-only scope and a dedicated full-account-details action. Apply/cancel/review/payout are unchanged.
