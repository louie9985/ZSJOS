# Cashback search performance

## Registration — 2026-09-29 (Asia/Shanghai)
- Environment: local (environment variable and environment file absent).
- Workstream: local-cashback-search-performance-20260929; owner: current cashback chat /root.
- Goal: implement the approved cashback search optimization, preserve keyword/filter/pagination and source visibility semantics, measure query cost and verify both ADMIN clients.
- Non-goals: business lifecycle changes, role grants, new dependencies, historical data repair, shared service restart/deployment, branch/worktree operations, commits or publication.
- Branch: main; absolute worktree: D:/ZSJ-OS; base commit: 5fadfb3e9be563ac4cb3e5c142308549944aa465; target branch/integration order: None.
- Ownership: cashback search/query/projection implementation and focused tests in ZSJOS; Lead/Order/AgingPool read-only batch permission methods and focused tests; AdvancedFilterService cashback-specific compilation and focused tests; cashback API documentation; new scoped read-only performance verification tools; Admin cashback page/fixtures; Workbench cashback fixtures/browser runner; this record. ManagementPages.tsx is explicitly excluded while withdrawal-export owns it; clarification pending. No other workstream's dirty files are owned.
- Dependencies: existing System public user/department/permission APIs, existing domain permission rules, MyBatis and MySQL 8, existing frontend test/browser facilities. FinanceTraceService's withdrawal public behavior must remain compatible with concurrent withdrawal export.
- Verification: before/after read-only database/query measurements; focused query, authorization equivalence, regression and scale tests; target MySQL SQL/EXPLAIN verification; both frontend static and real-browser checks; scoped diff/UTF-8 checks. Database index changes only if evidence warrants, with separate shared-state authorization as required.
- Coordination: unique environment-specific record avoids concurrent writes to handoff/main.md; backend build output and browser fixtures must be serialized if another process uses them.

## Scope update
- Batch read equivalence additionally owns LeadReadBatchMapper and the additive PartnerOwnershipService batch-read method plus their tests. Existing single-object rules remain the reference; no grants change.
- User selected waiting for the withdrawal task's delivery before editing ManagementPages.tsx.

## Ownership and verification update — 15:15 +08:00
- Withdrawal export delivered at 15:08:54 and released ManagementPages.tsx. This workstream now owns only its CashbackPage section, preserving the delivered withdrawal diff.
- Additive search-submit props/exposure in the two AdvancedFilter toolbar components and cashback-only request plumbing/fixtures are in scope; defaults preserve all other callers.
- Concurrent backend builds changed target/test-classes during verification. Subsequent checks use a plain temporary source snapshot at C:/Users/EDY/AppData/Local/Temp/cashback-search-verify-20260929/backend, not a Git branch/worktree, with separate build output. Re-sync owned sources before final checks.
- Include the existing NotifyStartupContextTest service registry to verify the new search service in the real security/transaction proxy graph.

## Delivery — 2026-09-29 15:34:32 +08:00
- Environment: local; owner: current cashback chat /root; registration metadata above remains applicable.
- Branch: main; worktree: D:/ZSJ-OS; current HEAD: 5fadfb3e9be563ac4cb3e5c142308549944aa465. No branch, worktree, stage, commit or push operation performed.
- User goal: execute cashback-search performance optimization while preserving search/filter/pagination and source visibility semantics.
- Key decisions/results: replace all-reference materialization and repeated per-object authorization with scoped 256-reference candidate batches and equivalent batch access checks; compile cashback advanced filters into the final count/page predicates; reuse finance page rows in enrichment; preserve MySQL LIKE number behavior and Java literal name matching, including Unicode fallback. Both clients submit the current draft from Query, allow explicit refresh, suppress identical pending requests and reject stale responses. No dependencies, schema/indexes, grants or business state changed. Withdrawal-export delivered before CashbackPage was edited; its changes remain preserved.
- Verification: isolated source/output Maven suite BUILD SUCCESS, 102 tests with zero failures/errors/skips (cashback-isolated-final.log, 15:28:32); newly added actual MySQL Lead relationship test separately BUILD SUCCESS, 1 test with zero failures/errors/skips (cashback-mysql-permission-final.log, 15:31:39). Total 103 distinct tests. All 20 owned Java source/test files byte-match the isolated tested snapshot. UTF-8 decode and scoped git diff --check passed for 29 delivery files.
- Performance evidence: MySQL 8.4.11, 600 synthetic cashback records in connection-local temporary tables, three warmups plus 20 measured iterations, MyBatis cache cleared between old/new. Old P50/P95 64.07/76.39 ms; new 21.56/28.45 ms. Permission APIs are mocked; this is not authenticated endpoint or production latency. Earlier cached timings are superseded. Real-data read-only profiling covered 4,292 scoped rows; its richer first-batch candidate SQL is slower than the old reference-only SELECT in isolation and does not measure removed Java/permission overhead. No new index inferred from those partial timings.
- Frontend evidence: npm run typecheck passed for Workbench; real Chrome fixture checks passed for both frontends (desktop search/pagination/clear/draft/duplicate/stale/error/retry plus mobile layout/search-field inspection). Screenshots: C:/Users/EDY/AppData/Local/Temp/cashback-search-browser/. Admin pnpm ts:check failed only on 12 existing duplicate-field diagnostics in src/api/zsjos/withdrawal/index.ts (lines 15-16 and 40-45); full Admin typecheck remains unverified. No build inputs/routes/dependencies changed; release build not claimed.
- Dependencies/integration: existing System public APIs, MyBatis and MySQL 8 only. Existing detail/withdrawal enrichment entry point retained. Integration order: None. Existing shared dev services were not restarted. Unrelated dirty files and delivery records were preserved; Workbench typecheck may update its existing tsconfig.tsbuildinfo artifact, which was not manually replaced.
- Remaining acceptance: deploying/restarting the running local backend on port 48080 and testing authenticated live results, permissions and endpoint timings. These were not performed; source optimization is delivered locally and must not be represented as already active in the running backend. Shared-service restart requires explicit target/impact authorization under AGENTS.md section 4. Broad candidate searches retain matching IDs and some advanced-filter cases still scan scoped references; measure live workloads before further indexing or architectural changes.
- Status: local implementation delivered; runtime acceptance pending; file ownership released.
- Changed files (task scope):
  - `backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/cashback/CashbackController.java`
  - `backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/dal/mysql/cashback/CashbackSearchMapper.java`
  - `backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/dal/mysql/lead/LeadReadBatchMapper.java`
  - `backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/AdvancedFilterService.java`
  - `backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/cashback/CashbackSearchQuery.java`
  - `backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/cashback/CashbackSearchService.java`
  - `backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/cashback/CashbackService.java`
  - `backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/cashback/CashbackServiceImpl.java`
  - `backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/cashback/FinanceTraceService.java`
  - `backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/lead/LeadAgingPoolService.java`
  - `backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/lead/LeadAgingPoolServiceImpl.java`
  - `backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/lead/LeadObjectPermissionService.java`
  - `backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/order/SalesOrderObjectPermissionService.java`
  - `backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/personnel/PartnerOwnershipService.java`
  - `backend/yudao-module-zsjos/src/test/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/AdvancedFilterFinanceQueryTest.java`
  - `backend/yudao-module-zsjos/src/test/java/cn/iocoder/yudao/module/zsjos/service/cashback/CashbackBatchPermissionTest.java`
  - `backend/yudao-module-zsjos/src/test/java/cn/iocoder/yudao/module/zsjos/service/cashback/CashbackNameSearchTest.java`
  - `backend/yudao-module-zsjos/src/test/java/cn/iocoder/yudao/module/zsjos/service/cashback/CashbackSearchMySqlTest.java`
  - `backend/yudao-module-zsjos/src/test/java/cn/iocoder/yudao/module/zsjos/service/cashback/CashbackServiceImplTest.java`
  - `backend/yudao-module-zsjos/src/test/java/cn/iocoder/yudao/module/zsjos/service/order/NotifyStartupContextTest.java`
  - `frontend/admin/src/views/zsjos/cashback/index.vue`
  - `frontend/admin/src/views/zsjos/components/ZsjosAdvancedFilter.vue`
  - `frontend/admin/test/cashback-search.ts`
  - `frontend/workbench/src/pages/ManagementPages.tsx (CashbackPage only)`
  - `frontend/workbench/test/cashback-search.tsx`
  - `frontend/workbench/test/cashback-search-browser.py`
  - `script/sql/mysql/tools/cashback_search_profile.py`
  - `docs/api/cashback.md`
  - `handoff/local-cashback-search-performance-20260929.md`
