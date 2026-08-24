# Withdrawal and offline payout API

The independent partner frontend uses `/part-api/zsjos/**`; only an enabled ordinary partner may apply. Admin/workbench review APIs remain under `/admin-api/zsjos/**`. The server locks selected `available` cashback rows in ascending ID order, snapshots the full bank account, creates one Withdrawal and item rows, changes cashback to `withdrawing`, and starts BPM process `zsjos_partner_withdrawal` with the single task key `financeReview`. Reviewers are enabled System users who currently have `zsjos:withdrawal:review`; V063 creates the permission but intentionally assigns no finance user.

## State contract

- `pending_review`: partner may cancel; BPM approval changes it to `approved`, while BPM rejection/cancellation releases cashback and ends as `rejected`/`cancelled`.
- `approved`: displayed as pending payout. Cashback remains `withdrawing`; finance may reject with a reason and release it, or record payout.
- `paid`: immutable terminal result. Recording payout requires a tenant-unique bank transaction number and an Infra private image/PDF proof owned by the operator; server time and operator are stored and cashback changes to `withdrawn`.
- There is no balance account, partial cashback withdrawal, supervisor step, adjusted review amount, automatic transfer, fee, tax, reconciliation, payout failure state, or paid rollback.

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
| `POST /proof/upload`, `PUT /{id}/payout` | `zsjos:withdrawal:payout` | Private proof and immutable offline payout |

Feature permissions, list scope and `withdrawal` object checks are cumulative. The weekly reminder defaults to Thursday 10:30, uses the configured overdue-day threshold (default 7), resolves recipients from the review permission, and skips while maintenance mode is active. V052 does not deploy BPM or grant roles; deployment must publish the exact process and task keys before applications are enabled.

The partner start subject is external, but `zsjos_partner_withdrawal` may use the BPM `START_USER_SELECT` strategy when the request supplies the configured internal finance reviewer IDs for `financeReview`. BPM validates those IDs against enabled System users before creating the instance. Strategies that depend on the external starter's own user, department, or department leaders remain unsupported for partner-subject starts.

The Vue withdrawal list exposes direct asynchronous export only when the user has both `zsjos:export:withdrawal` and full withdrawal-list visibility. It exports all rows matching the current status filter, retains masked card numbers, creates an asynchronous task without polling the business page, and links to the existing export-task center.

Only `GET /{id}/finance-detail` returns the full card and payout finance fields, including bank transaction number, proof, payout remark, payout operator and payout time. Ordinary list/detail projections never generate proof pre-signed URLs.
