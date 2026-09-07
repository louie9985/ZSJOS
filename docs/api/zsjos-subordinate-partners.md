# 兼职管理与归属 API

## Permission model

The Partner page is a permission-free route container. Its three server-owned permissions are additive:

- `zsjos:partner:query` keeps the existing read scope: the current employee, enabled employees covered
  by System department data permission, and employees configured through Partner visibility relations.
- `zsjos:partner:manage` is read-only and strictly limited to Partners currently assigned to the logged-in
  employee. It does not consult System department data permission or Partner visibility relations.
- `zsjos:partner:manage-all` grants tenant-wide visibility and all supported management commands.

To configure strict self-only visibility, grant `zsjos:partner:manage` without `zsjos:partner:query`.
When permissions are combined, their read scopes are unioned and `manage-all` wins. Unassigned Partners are
visible only to `manage-all`. Disabling an employee or reassigning a Partner immediately removes self-only
access without deleting ownership history. No permission is inferred from a role name.

## Unified endpoints

- `GET /admin-api/zsjos/partner/page`
- `GET /admin-api/zsjos/partner/{partnerId}/leads/page`
- `GET /admin-api/zsjos/partner/leads/{leadId}`
- `POST /admin-api/zsjos/partner/create` (`zsjos:partner:manage-all`)
- `PUT /admin-api/zsjos/partner/{partnerId}/enable|disable` (`zsjos:partner:manage-all`)
- `PUT /admin-api/zsjos/partner/{partnerId}/mobile` (`zsjos:partner:manage-all`)
- `PUT /admin-api/zsjos/partner/{partnerId}/reset-password` (`zsjos:partner:manage-all`)
- `POST /admin-api/zsjos/partner/{partnerId}/convert` (`zsjos:partner:manage-all`)
- `GET /admin-api/zsjos/partner/assignment-candidates` (`zsjos:partner:manage-all`)
- `PUT /admin-api/zsjos/partner/{partnerId}/assignment` (`zsjos:partner:manage-all`)
- `GET /admin-api/zsjos/partner/{partnerId}/assignment-log/page` (`zsjos:partner:manage-all`)
- `POST /admin-api/zsjos/partner-student-link/bind|unbind` (`zsjos:partner:manage-all`)

The list returns account identity and state, current ownership and lifecycle timestamps. It never
returns passwords, tokens or the internal bound System user identifier to the Workbench contract.
Assignment updates require a reason and use the current relation version to reject stale changes.

The former `/admin-api/zsjos/subordinate-partners/**` GET endpoints remain temporary aliases for a
rolling frontend/backend release. They execute the same three-permission read and object checks and
must not become a second authorization contract.

## Partner Lead visibility

Once a Partner is visible, the reader may inspect every historical and future Lead whose persisted
`partnerId` matches it. Detail, follow-up, appeal, complaint, customer-order and flow-history reads use
the same live Partner scope and remain read-only. Reassignment moves this complete visibility to the new
owner and does not rewrite Lead snapshots.

Partner Leads created after V143 snapshot `partnerOwnerUserIdSnapshot` and
`partnerOwnerNameSnapshot` at submission. Older null snapshots display `未记录`; current ownership is
never substituted as historical fact.
