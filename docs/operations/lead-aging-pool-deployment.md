# 超期协同公海部署说明

Migration: `script/sql/mysql/migrations/V034__lead_aging_collaboration_pool.sql`.

## Before execution

1. Back up the target database and confirm V032 is present.
2. Run the repository schema-difference and `verify-bootstrap.sql` checks read-only.
3. Review the V034 final read-only query. Leads returned by it have no recoverable formal ownership
   start and will not age into the pool until an authorized ownership operation supplies one.
4. Confirm menu IDs `6794-6796` and notification template codes are unused by unrelated data.

V034 adds the Lead ownership-start and timeout columns, order continuation links, the Lead scan and
order relationship indexes, three append/audit tables, the `agingPool` filter scheme, menus, templates
and tenant notification rules. It removes the historical one-order-per-opportunity unique index while
retaining the generated active-Lead unique key and a one-successor-per-order unique key. It recovers
`ownership_started_at` only from explicit assignment-history actions
`accept`, `claim`, or `transfer`; it never substitutes `update_time`.

## Controlled rollout

1. Apply V034 once through the approved migration process. Do not edit an already applied V034.
2. Re-run V034 to verify guarded DDL and seed idempotency, then run `verify-bootstrap.sql`.
3. Grant `manage` and `manage-all` only through the normal System role/menu workflow. V034 copies
   query access from existing owned-lead access but does not grant management permissions.
4. Enable application instances and observe one-minute tenant scans, cycle/event growth, and System
   notification delivery. Advance stages remain `pending` or `failed` until System confirms durable
   message persistence, then become `sent`; verify retries progress and no `overdue` aging-pool rule exists.
5. Validate desktop/mobile Workbench and desktop Admin views with authorized, unauthorized, empty,
   error and retry cases.

## Rollback limitation

Rollback is operational, not destructive: hide the menu, disable aging-pool notification rules, and
disable the scheduler in a forward change. Do not drop the new columns or tables and do not delete
cycles, events, notification stages, messages, or recovered ownership timestamps. Any correction is a
new forward migration.
