# Media provider customer order reads

## Registration — 2026-11-04 (Asia/Shanghai)
- Environment: local (variable and marker absent); owner: Codex /root; branch: main; worktree: D:/ZSJ-OS; base: c9964acf71fdfec268c9e08c148308e9c81f1827; integration: None.
- Goal: employee Lead provider can read every order of the same current Person through customer-order history, including repurchases and merged origins.
- Non-goals: general order lists, write permissions, statistics, role grants, identity changes, deployment/database writes.
- Ownership: SalesOrderServiceImpl.java and its test; docs/api/zsjos-sales-order.md, zsjos-subordinate-sales.md, zsjos-workbench-foundation.md; docs/architecture/data-and-permission-flow.md; this record; isolated temporary verification output.
- Dependencies: existing provider ownership, feature and Lead object authorization, tenant-aware mappers. Verification: focused service/permission regression, current-source compilation, both frontend consumer inspection, scoped diff. Unrelated existing edits preserved.

## Delivery — 2026-11-04 14:12 (Asia/Shanghai)
- HEAD unchanged; environment/branch/worktree refer to registration. Implemented same-current-Person full customer-order history for the Lead employee provider. Retained feature/object checks and non-provider origin boundaries; no general order authorization or statistical SQL changed.
- Files: SalesOrderServiceImpl.java, SalesOrderServiceImplTest.java, and the four API/architecture documents named above. Updated obsolete owner-only/submitter-tab documentation to match current unified detail contracts.
- Verification: current-source isolated javac compilation and 135/135 focused order service, order object permission, Lead object permission and controller permission tests passed. New regression covers merged-origin repurchase list/detail, assisted creator denial, Partner identity denial, cross-Person denial and no mutation. Existing tests cover original-origin and independent permission reads. Scoped git diff --check passed.
- Workbench LeadDetail consumes server visibleTabs; LeadCustomerOrders calls both unchanged endpoints and renders read-only details. Vue Admin source has no customer-orders consumer; its generic/management order APIs are unchanged. Frontend rendering was not changed; no browser/build check required for a backend-only scope adjustment.
- Initial verification fixture used an unavailable Lead setter and then omitted required order status; corrected the fixture and reran successfully. Verification output: frontend/workbench/tmp/media-order-read-20261104/java (temporary).
- Real-account HTTP/browser acceptance, database-backed tenant interceptor execution and deployed behavior remain unverified: only isolated local compilation/tests were run; no service was deployed/restarted or real-account permissions changed. Existing tenant-aware mapper path is unchanged. No migrations, dependencies, commits or pushes. Unrelated concurrent studentinfo/attendance edits preserved. Ownership released.
