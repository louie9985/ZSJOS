# 下属销售 API

Base path: `/admin-api/zsjos/subordinate-sales`. All endpoints require authentication, current tenant context, the listed feature permission, and a current System department-leader relationship covering the target sales department or one of its ancestors.

## Queries

- `GET /page`: permission `zsjos:subordinate-sales:query`; supports `keyword`, `accountStatus`, `presence`, `accepting`, `pageNo`, and `pageSize`.
- `GET /{salesUserId}/overview`: returns the same metric contract for one managed sales user.
- `GET /{salesUserId}/leads`: reuses the Lead management table response and limits rows to current ownership by that managed sales user.
- `GET /{salesUserId}/tasks`: returns current pending first/subsequent follow-up tasks; `bucket` is `overdue`, `today`, `future`, or `unscheduled` using Beijing time. An empty task set returns an empty page and never issues an empty-ID Lead query.
- `GET /transfer-candidates`: returns enabled, currently eligible sales specialists inside the manager's department tree.

The list includes disabled sales accounts that still hold the stable `sales_specialist` post. `canReceiveNewLeads` is true only when account enabled, sales eligibility valid, presence online, and intake mode accepting. Lead-category metrics follow the enabled System dictionary order and append `未配置` only when historical unmatched values exist.

Selecting an in-scope Lead reuses the Workbench owned-Lead detail in `manager-readonly` mode. The manager can read the full overview, follow-up history, appeal history, complaint history, and customer-order history, but no Lead, appeal, complaint, follow-up, or order write action is exposed. The detail calls the existing domain read APIs with `zsjos:subordinate-sales:query`; every call still requires the live System department-leader relationship through the Lead object-permission boundary.

- `GET /zsjos/lead/get?id={leadId}`: complete Lead overview.
- `GET /zsjos/lead/{leadId}/follow-ups/page`: paged follow-up history.
- `GET /zsjos/lead/appeal/lead/{leadId}/list`: appeal history.
- `GET /zsjos/lead-complaint/lead/{leadId}/list`: complaint history with display names and signed evidence URLs.
- `GET /zsjos/sales-order/lead/{leadId}/customer-orders` and `/{orderId}`: customer order list and detail; only the Lead owner, an in-scope department manager, or `zsjos:lead:query-all` may pass the order-specific object action. A submitter-only relationship is insufficient.

## Commands

- `PUT /{salesUserId}/account-status`: permission `zsjos:subordinate-sales:account-status`; body `{status, reason}`. System owns the status mutation and revokes tokens on disable.
- `PUT /{salesUserId}/dispatch-mode`: permission `zsjos:subordinate-sales:dispatch-mode`; body `{accepting, reason}`.
- `POST /leads/batch-transfer`: permission `zsjos:subordinate-sales:batch-transfer`; body `{leadIds, targetUserId, reason}`.
- `POST /leads/batch-public-sea`: permission `zsjos:subordinate-sales:batch-public-sea`; body `{leadIds, collaboratorUserId?, reason}`.

Every reason is trimmed, required, and limited to 500 characters. Batch commands accept 1 to 200 IDs and return `{successCount, failureCount, items[]}`. Each item contains internal `leadId`, user-visible `leadNo`, `success`, stable `code`, and `message`; each Lead runs in an independent transaction.

Manual public-sea release preserves Lead owner, main status, and assignment status. It does not use the claim-pool value `assignment_status=public_pool` and does not make the Lead claimable.
