# ZSJOS 超期协同公海 API

Base path: `/admin-api/zsjos/lead/aging-pool`. All endpoints are tenant scoped and return the standard
`CommonResult` wrapper.

| Method | Path | Permission | Purpose |
| --- | --- | --- | --- |
| GET | `/page` | `zsjos:lead-aging-pool:query` | Paged active cycles; supports `status` and contact `keyword` |
| GET | `/get?id=` | `zsjos:lead-aging-pool:query` | Visible cycle detail |
| GET | `/counts` | `zsjos:lead-aging-pool:query` | Active status counts in the caller's scope |
| GET | `/filter-profile` | `zsjos:lead-aging-pool:query` | Published server-owned `agingPool` status groups and scoped counts |
| GET | `/{id}/candidates` | manage or manage-all | Enabled same-department B candidates |
| POST | `/{id}/assign` | manage or manage-all | Assign or reassign B |
| POST | `/{id}/exit` | manage or manage-all | Exit with a required reason |

Assign body:

```json
{"salesUserId": 123, "idempotencyKey": "uuid"}
```

Exit body:

```json
{"reason": "business reason", "idempotencyKey": "uuid"}
```

Active statuses are `waiting_assignment`, `assigned`, and `deal_pending`. Responses include full
contact fields only after server-side object authorization and include `availableActions`; clients
must not derive actions from A/B identities. Stable command conflicts distinguish missing cycles,
manager denial, invalid candidates, invalid owner, invalid state, and idempotency-key conflicts.

Existing lead detail, follow-up, and sales-order APIs remain authoritative for B's work. During an
active cycle, the backend effective-sales check permits only B for follow-up/deal actions and rejects
A's basic editing and qualification commands. Final order approval atomically transfers Lead and
Opportunity to the immutable order submitter B.

If the rejected active order was submitted by A, B uses
`POST /admin-api/zsjos/sales-order/{id}/continue-submit`. The original order becomes `superseded`,
the new immutable B-owned order points to it through `supersedesOrderId`, and the original points back
through `supersededByOrderId`. If B submitted the rejected order, the normal revise endpoint updates
that same order without changing `submitterUserId`.
