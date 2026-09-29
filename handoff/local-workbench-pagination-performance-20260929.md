# Workstream — workbench-pagination-performance-20260929

## Registration — 2026-09-29 Beijing time
- Environment: local (ZSJOS_AGENT_ENV and /etc/zsjos/agent-environment absent). Branch: main. Absolute worktree: D:/ZSJ-OS. Base/HEAD: 5fadfb3e9be563ac4cb3e5c142308549944aa465. Owner: this chat Codex /root. Target branch / integration order: None.
- User goal: implement the approved Workbench pagination and query performance plan, preserving business results, authorization, tenant isolation and historical snapshots.
- Scope: sales-performance detail database pagination; media-lead paginated detail API and scoped aggregation; Workbench duplicate list requests and notice summary loading; evidence-based material approval / cashback query improvements; focused verification and affected API documentation.
- Non-goals: change business/statistical definitions, permission grants, historical data, introduce dependencies/caches, truncate results, or change unrelated UI. No shared-service restart, deployment, database schema/data writes, Git branch/worktree/stage/commit/push. Index changes require evidence and a separately documented execution scope.
- Ownership: backend performance PerformanceStatisticsService, PerformanceFactMapper and new PerformanceDetail* query classes; MediaLeadAnalysisService, MediaLeadFactMapper and new MediaLead* query/aggregation classes; MediaLeadAnalysisController and MediaLeadVO; focused performance tests. Frontend MySalesOrderPage, NoticeReadStatistics, MaterialApprovalPage, MediaLeadAnalysisPage, services/mediaLeadAnalysis and focused pagination tests/fixtures; material approval service/API or cashback search classes only after recorded evidence and scope update. Docs api/sales-performance.md, api/media-lead-analysis.md and affected frontend query documentation; new scoped performance verification script; this record. Existing uncommitted changes in these files must be preserved.
- Dependencies: existing System/BPM public APIs, MyBatis/MySQL, React/Ant Design, Maven/JUnit and Vitest/browser fixture facilities. Previous personal-performance and cashback workstreams report local delivery and released ownership; their changes are prerequisites, not to be overwritten.
- Verification plan: capture existing source and runtime baseline without secrets; compare pre/post query result multisets and page order on stable scoped data; focused permission/period/conversion/automatic-source tests; actual MySQL SQL verification where available; frontend typecheck, focused tests and real-browser pagination/rapid switching at desktop/mobile widths. Keep shared build resources separate from concurrent work. Report source verification separately from authenticated running-service performance acceptance.

## Scope refinement — shared fact definitions and permission projection
- Include PerformanceFactSql.java and PerformanceAccess.java / PerformanceAccessTest.java to reuse the exact fact definitions and project existing historical access into SQL, without changing grants or visibility. Include the existing performance_task_profile.py parser solely to keep its read-only verifier compatible with extracted SQL constants. Query-specific classes remain in the existing performance DAL/service directories. Existing task-query and automatic-source changes remain intact.

## Scope refinement — material projection batches
- Evidence: MaterialApprovalService.page calls project once per row; each projection reads version, material, type and approval round (four queries per row). Include this service, MaterialApprovalServiceTest, and docs/api/material-library.md if present for bounded current-page batch projection. Preserve per-type BPM pagination, assignee/tenant enforcement and round validation. Do not change merged cross-type pagination semantics or BPM public API in this pass.
- Verification uses an independent plain temporary source snapshot and focused compiler test includes; initial broad compilation exceeded available memory, then bounded JVM settings were applied. Browser fixture server is task-owned port 5196.

## Scope clarification — affected documentation and consumers
- Directly affected docs include docs/api/material-approval.md (instead of material-library.md), docs/api/notice-read-statistics.md, docs/api/zsjos-sales-order.md, and frontend/workbench/docs/pagination-performance.md.
- Material approval /page is also consumed by Vue Admin material detail (src/api/zsjos/material/index.ts and views/zsjos/material/index.vue). Response, BPM query, permission and tenant contracts remain unchanged; batch tests preserve projections used by both clients. No admin code changes required.

## Evidence-driven adjustment — conversion detail
- First real-data single-page comparison (3 warmups, 20 alternated samples, both MyBatis caches cleared): full SQL conversion windows regressed from P95 17.89 ms to 52.57 ms with simulated authorization. Do not ship that query. Keep the authoritative PerformanceConversion calculation, but query only scoped in-period orders and receipts in-period or associated with candidate leads; resolve historical permission once. Other detail metrics remain SQL count/page. This preserves complete conversion cohorts and transfer history without introducing an approximate count or cross-request cache. Retest the final implementation.

## Delivery — 2026-09-29 17:34:11 +08:00

- Status: authorized local implementation and scoped verification complete; write ownership released on this delivery. Environment local; branch main; worktree D:/ZSJ-OS; HEAD 5fadfb3e9be563ac4cb3e5c142308549944aa465. No stage/commit/push/branch/worktree operation, deployment, shared-service restart, dependency addition or database/schema/grant write.
- Delivered: single order-list request lifecycle and count isolation; component-local notice summary reuse; bounded media detail API and stale-request cancellation; scoped sales detail count/page queries and one historical permission projection per request; exact conversion cohort calculation over narrowed facts; media overview grouping reuse; current-page material approval projection batches. Existing cashback, task-fact and automatic-source optimizations and unrelated user changes preserved.
- Contracts: statistical definitions, tenant/permission checks and snapshots retained. Shared material approval response remains compatible with both Workbench and Vue Admin. Cross-type material pagination semantics remain unchanged. Media overview still reads scoped history and conversion still builds the precise cohort in memory.
- Backend evidence: 113 distinct tests passed, 0 failures/errors/skips; includes real MySQL 8.4.11 read-only verification, 96 old/new comparisons and 1,138 media-detail records in identical order. Generated SQL tenant-parser check passed. All 24 owned Java source/test files byte-match the tested isolated snapshot.
- Frontend evidence: npm run typecheck passed; focused real-Chrome synthetic fixtures passed at 1440 and 390 widths with no page errors, including request counts, retries, unauthorized states and stale cancellation. Additional legacy media script passed affected analysis flows but was interrupted at an unrelated target-page screenshot; no full-script success claimed. No release-build or live authenticated new-backend acceptance claimed.
- Verification constraints: broad initial compile exceeded machine memory; successful focused verification used a private plain source snapshot and bounded JVM. Repository POM unchanged. All 41 owned files passed UTF-8 decoding/replacement-character inspection and scoped diff checks before delivery documentation; final documentation checked separately after append.
- Timing method: local MySQL, representative USER scope, same first 20 rows, three warmups and 20 alternating samples, both MyBatis caches cleared before each comparison; permissions mocked. Final results in milliseconds:

| Metric | Rows | Old P50 | New P50 | Old P95 | New P95 |
| --- | ---: | ---: | ---: | ---: | ---: |
| orders | 64 | 13.82 | 11.23 | 29.24 | 13.05 |
| leads | 277 | 13.42 | 16.15 | 15.62 | 18.31 |
| conversion | 251 | 12.70 | 16.25 | 17.71 | 30.76 |
| followUps | 6466 | 152.38 | 121.95 | 168.16 | 129.25 |
| tasks | 215 | 29.22 | 15.49 | 39.20 | 21.31 |

- Interpretation: orders/follow-ups/tasks improve in this sample; small leads/conversion regress and are not claimed as faster. Real permission-call savings remain unmeasured. Full-window conversion implementation was rejected; the table is the final tested implementation. The task-fact profiler preserved equivalent multisets in four scopes; its earlier optimization benefit is not counted as new work here.
- Evidence location: C:/Users/EDY/AppData/Local/Temp/workbench-pagination-verify-20260929/final-verified.log; tenant-parser.log; snapshot Surefire reports; owned-files.json. Browser screenshots: C:/Users/EDY/AppData/Local/Temp/workbench-pagination-browser/. These are local verification artifacts, not committed deliverables.
- Remaining release acceptance: deploy backend before Workbench and verify authenticated endpoints, real scope/tenant behavior and end-to-end latency. Shared backend port 48080 was not restarted; this delivery does not authorize a shared-service operation. No index or BPM public-contract expansion undertaken.

### Exact owned file scope

The following files contain this workstream's changes or verification/documentation updates; existing changes by other workstreams in shared files were preserved.
- backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/performance/MediaLeadAnalysisController.java
- backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/performance/vo/MediaLeadVO.java
- backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/dal/mysql/performance/PerformanceFactMapper.java
- backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/dal/mysql/performance/PerformanceFactSql.java
- backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/dal/mysql/performance/PerformanceDetailMapper.java
- backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/dal/mysql/performance/PerformanceDetailQuery.java
- backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/dal/mysql/performance/PerformanceDetailSql.java
- backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/dal/mysql/performance/MediaLeadDetailRow.java
- backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/dal/mysql/performance/MediaLeadQueryMapper.java
- backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/performance/PerformanceAccess.java
- backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/performance/PerformanceStatisticsService.java
- backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/performance/MediaLeadAnalysisService.java
- backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/material/MaterialApprovalService.java
- backend/yudao-module-zsjos/src/test/java/cn/iocoder/yudao/module/zsjos/service/performance/PerformanceAccessTest.java
- backend/yudao-module-zsjos/src/test/java/cn/iocoder/yudao/module/zsjos/service/performance/PerformanceMapperTest.java
- backend/yudao-module-zsjos/src/test/java/cn/iocoder/yudao/module/zsjos/service/performance/PerformanceReportTest.java
- backend/yudao-module-zsjos/src/test/java/cn/iocoder/yudao/module/zsjos/service/performance/PerformanceTaskQueryTest.java
- backend/yudao-module-zsjos/src/test/java/cn/iocoder/yudao/module/zsjos/service/performance/PerformanceDetailReference.java
- backend/yudao-module-zsjos/src/test/java/cn/iocoder/yudao/module/zsjos/service/performance/PerformanceDetailMySqlTest.java
- backend/yudao-module-zsjos/src/test/java/cn/iocoder/yudao/module/zsjos/service/performance/PerformanceDetailServiceTest.java
- backend/yudao-module-zsjos/src/test/java/cn/iocoder/yudao/module/zsjos/service/performance/PerformanceConversionSqlTest.java
- backend/yudao-module-zsjos/src/test/java/cn/iocoder/yudao/module/zsjos/service/performance/MediaLeadAnalysisServiceTest.java
- backend/yudao-module-zsjos/src/test/java/cn/iocoder/yudao/module/zsjos/service/performance/MediaLeadPageQueryTest.java
- backend/yudao-module-zsjos/src/test/java/cn/iocoder/yudao/module/zsjos/service/material/MaterialApprovalServiceTest.java
- frontend/workbench/src/components/NoticeReadStatistics.tsx
- frontend/workbench/src/pages/MySalesOrderPage.tsx
- frontend/workbench/src/pages/MediaLeadAnalysisPage.tsx
- frontend/workbench/src/services/mediaLeadAnalysis.ts
- frontend/workbench/test/media-lead.tsx
- frontend/workbench/test/notice-reading.tsx
- frontend/workbench/test/order-pagination.tsx
- frontend/workbench/test/order-pagination.html
- frontend/workbench/test/pagination-performance-browser.py
- script/sql/mysql/tools/performance_task_profile.py
- docs/api/sales-performance.md
- docs/api/media-lead-analysis.md
- docs/api/material-approval.md
- docs/api/notice-read-statistics.md
- docs/api/zsjos-sales-order.md
- frontend/workbench/docs/pagination-performance.md
- handoff/local-workbench-pagination-performance-20260929.md
