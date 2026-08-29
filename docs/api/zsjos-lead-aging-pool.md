# ZSJOS 公海池 API

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
| POST | `/{id}/transfer-request` | `zsjos:lead-aging-pool:transfer-request` | Current public-sea collaborator B requests formal transfer to self; other visible same-team sales are rejected |

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

Visibility, manager authority, and collaborator candidates follow formal owner A's current department.
The entry-time department snapshot remains audit data only, so organization changes do not leave the
Opportunity visible to A's former team.

Existing lead detail, follow-up, and sales-order APIs remain authoritative. During an active cycle,
both formal owner A and configured collaborator B may follow up and create/revise an order. Every
mutation locks the active cycle. Final approval exits the public sea without changing formal or
performance ownership; the order submitter remains the actual operator.

If the rejected active order was submitted by A, B uses
`POST /admin-api/zsjos/sales-order/{id}/continue-submit`. The original order becomes `superseded`,
the new immutable B-owned order points to it through `supersedesOrderId`, and the original points back
through `supersededByOrderId`. If B submitted the rejected order, the normal revise endpoint updates
that same order without changing `submitterUserId`.
