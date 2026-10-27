# Workstream local-order-query-all-20261026

- Environment: local (ZSJOS_AGENT_ENV unset; /etc/zsjos/agent-environment absent).
- Owner: Codex /root. Goal: configurable order-management read-all permission.
- Branch: main; worktree: D:/ZSJ-OS; base: 8279212fb97129ba6b006dbb0452ad843dfde4b5.
- Non-goals: role grants, command permissions, unrelated dirty work, deployment/restarts.
- Ownership: SalesOrderConstants.java, SalesOrderObjectPermissionService.java and its test; V351__sales_order_query_all_permission.sql; test_sales_order_query_all_permission.py; docs/api/zsjos-sales-order.md; script/sql/mysql/migrations/README.md; this record.
- Dependencies: existing System PermissionApi and order management scope; existing MySQL fixture tools. No new dependencies.
- Verification: focused backend permission/service/controller tests; both frontend call-chain inspection; controlled MySQL initial/replay/partial recovery/failure/HEX/preservation checks; scoped diff. Inspect and apply additive metadata to local development DB only after controlled verification.
- Target branch/integration order: None.

## Delivery — 2026-10-26 14:16 Beijing

- HEAD unchanged; branch/worktree as registered. Added `zsjos:sales-order:query-all` under order management. Existing page permission remains required. The shared management resolver expands list/cursor/search/count/detail reads (and existing finance source read projection), without changing commands, old own/team scopes, tenant/logical-delete rules or explicit filters.
- Changed files: registered enum, object permission service/test, V351 migration, its MySQL verification script, order API documentation, migration README and this record. Both Admin mySalesOrder and Workbench MySalesOrderPage already consume management endpoints and server command flags; no frontend changes required. Corrected stale API documentation describing the old team detail path.
- MySQL 8.4.11: nine retained isolated cases under `order_all_20261026141132` passed initial/replay/partial state, missing/duplicate parents, conflicting/retired/duplicate buttons, ledger failures and recovery; UTF-8 HEX and administrator configuration/role grants/packages preserved. Test constant names subsequently clarified without behavior changes.
- Local `ruoyi-vue-pro`: inspected unique parent 73511 and V350 in both ledgers; applied V351 and replayed. Verified exact button parent/type/HEX and both V351 records. Existing menu rows, role grants and tenant packages unchanged. Private before-image: C:/Users/EDY/.codex/tmp/order-query-all-20261026/metadata-before.tsv. No business rows or real role assignments changed.
- Backend compilation succeeded. Focused Maven run: 91 tests, 90 passed, 1 unrelated existing failure. Object permission tests 7/7 and controller permission tests 3/3 passed; management advanced-filter and cursor cases passed. Existing `SalesOrderServiceImplTest.terminateLocksCurrentRoundAndCancelsBpm` expects cancelEarlyDeliveryByOrderId while unchanged implementation invokes cancelByOrderId; neither file is modified. Do not claim whole suite passed.
- Verification environment: first JVM native allocation failed; limited-memory retry compiled but fork startup failed. Successful execution used MAVEN_OPTS `-Xmx512m -XX:MaxMetaspaceSize=256m -XX:ReservedCodeCacheSize=64m -XX:+UseSerialGC -XX:ActiveProcessorCount=2`, Maven test with `-DforkCount=0`, the four focused SalesOrder test classes and no-specified-tests flags. Scoped diff checks passed.
- Remaining: backend deployment/start and real-account/browser acceptance unverified (48080 not listening). Administrator must assign the new button alongside page access. No restart, dependency addition, role grant, Git commit/push or production changes performed. Ownership released.
