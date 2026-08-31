# 兼职管理与归属 API

## Permission model

`zsjos:partner:query` exposes the consolidated Partner page. An ordinary reader sees only Partners
whose current tenant-scoped ownership row points to that employee. Removing the permission, disabling
the employee or reassigning the Partner immediately removes access without deleting ownership history.

`zsjos:partner:manage` grants tenant-wide Partner visibility and all supported management commands:
create, enable/disable, login-mobile update, password reset, ownership assignment and ownership history.
Neither permission is inferred from a role name. Historical `converted` rows remain visible but cannot
be converted again; the conversion endpoint and permission are retired.

## Unified endpoints

- `GET /admin-api/zsjos/partner/page`
- `GET /admin-api/zsjos/partner/{partnerId}/leads/page`
- `GET /admin-api/zsjos/partner/leads/{leadId}`
- `POST /admin-api/zsjos/partner/create` (`zsjos:partner:manage`)
- `PUT /admin-api/zsjos/partner/{partnerId}/enable|disable` (`zsjos:partner:manage`)
- `PUT /admin-api/zsjos/partner/{partnerId}/mobile` (`zsjos:partner:manage`)
- `PUT /admin-api/zsjos/partner/{partnerId}/reset-password` (`zsjos:partner:manage`)
- `GET /admin-api/zsjos/partner/assignment-candidates` (`zsjos:partner:manage`)
- `PUT /admin-api/zsjos/partner/{partnerId}/assignment` (`zsjos:partner:manage`)
- `GET /admin-api/zsjos/partner/{partnerId}/assignment-log/page` (`zsjos:partner:manage`)

The list returns account identity and state, current ownership and lifecycle timestamps. It never
returns passwords, tokens or the internal bound System user identifier to the Workbench contract.
Assignment updates require a reason and use the current relation version to reject stale changes.

The former `/admin-api/zsjos/subordinate-partners/**` GET endpoints remain temporary aliases for a
rolling frontend/backend release. They execute the same query/manage permission and object checks and
must not become a second authorization contract.

## Partner Lead visibility

Once a Partner is visible, the reader may inspect every historical and future Lead whose persisted
`partnerId` matches it. Detail, follow-up, appeal, complaint, customer-order and flow-history reads use
the same live Partner scope and remain read-only. Reassignment moves this complete visibility to the new
owner and does not rewrite Lead snapshots.

Partner Leads created after V143 snapshot `partnerOwnerUserIdSnapshot` and
`partnerOwnerNameSnapshot` at submission. Older null snapshots display `未记录`; current ownership is
never substituted as historical fact.
