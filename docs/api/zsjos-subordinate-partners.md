# 下属兼职与归属 API

## Permission model

`zsjos:subordinate-partner:query` is the single employee read capability. System resolves enabled
employees holding this permission as assignment candidates. The permission alone does not expose any
Partner: every read also requires the current tenant-scoped `Partner -> employee` ownership row.
Removing the permission or disabling the employee immediately makes the retained relationship
ineffective. Partner disablement or conversion does not remove historical read access.

`zsjos:partner:assign-owner` protects assignment candidate, update, and audit APIs. It grants no
subordinate Partner read scope by itself.

## Administrator endpoints

- `GET /admin-api/zsjos/partner/assignment-candidates`
- `PUT /admin-api/zsjos/partner/{partnerId}/assignment` with
  `{assignedUserId?, reason, expectedVersion?}`. A missing user unassigns the Partner. The reason is
  required and the current relation version prevents stale changes.
- `GET /admin-api/zsjos/partner/{partnerId}/assignment-log/page`

The existing Partner list projects current employee name, assignment time/version, and whether the
relationship is presently effective. It never returns Partner password or token data.

## Employee endpoints

- `GET /admin-api/zsjos/subordinate-partners/page`
- `GET /admin-api/zsjos/subordinate-partners/{partnerId}/leads/page`
- `GET /admin-api/zsjos/subordinate-partners/leads/{leadId}`

The Lead list covers every historical and future Lead whose persisted `partnerId` matches the current
Partner relationship. Detail, follow-up, appeal, complaint, customer-order, and flow-history reads use
the same live object check and remain read-only. The relationship does not authorize supplement,
urge, complaint/appeal submission, follow-up, transfer, recycle, release, account, cashback, or
withdrawal operations.

Partner Leads created after V143 snapshot `partnerOwnerUserIdSnapshot` and
`partnerOwnerNameSnapshot` from the configured relationship even when that employee is temporarily
disabled or lacks the read permission. Reassignment never rewrites these fields. Older Leads remain
null and display `未记录`; current ownership must not be presented as their historical value.
