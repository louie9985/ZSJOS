# 订单待重提与终止状态分离
- Environment: local (environment variable and environment file absent)
- Workstream/owner: local-order-status-20261102 / Codex
- Branch: main; worktree: D:/ZSJ-OS; base: 0ecc74d4d13bcfbc0361ecdbc2bbd73327359425
- Goal: restore revision_required display; allow termination from revision; terminated cannot resubmit.
- Scope: SalesOrderServiceImpl.java and its test; Workbench sales order detail/page/API types; Admin mySalesOrder; order status filter/sort labels; docs/api/zsjos-sales-order.md; this record.
- Non-goals: database changes, permissions grants, deployment, unrelated edits.
- Dependencies: existing termination/create permission, BPM and command idempotency APIs.
- Integration: None.
- Verification: focused Java tests, frontend type checks, scoped diff; browser if available.

## Delivery
- Restored revision_required label and independent terminated tab. Terminated orders cannot resubmit (service and projections).
- Revision termination preserves finished approval round, uses existing create permission and ownership/version/idempotency checks, and cancels pending revision task.
- No schema changes, external data writes, service restart, commit or deployment.
- Verification: SalesOrderServiceImplTest 87 tests passed after final change; Workbench typecheck passed; scoped diff check passed.
- Admin typecheck blocked by existing src/views/mes/pro/task/edit/index.vue:86 unused handleRefresh. Browser reached local order page, but changed-flow end-to-end acceptance not completed against updated runtime. No real order mutated.
- Ownership released.
