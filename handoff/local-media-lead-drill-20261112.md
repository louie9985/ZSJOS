# 新媒体客资分析下钻

- Environment local, owner Codex, workstream local-media-lead-drill-20261112.
- Goal 指标点击后按原统计范围展示客资及关联首购订单。
- Scope MediaLeadAnalysisPage.tsx, services/mediaLeadAnalysis.ts, backend performance MediaLeadVO/Controller/Service/QueryMapper/DetailRow, focused performance tests, docs/api/media-lead-analysis.md, this record.
- Branch main, worktree D:/ZSJ-OS, base 464c40f6d0eae8ed91cceddde915675ee8a38543. Integration None.
- Dependencies existing query/detail permissions and frozen contribution snapshots. Preserve unrelated work.
- Verification focused mapper/service tests, Workbench typecheck/build, desktop/mobile browser flow where available.

- Scope update owns existing test/media-lead.tsx and media-lead-browser.py fixtures for drill endpoint/drawer compatibility.

## Delivery 2026-11-12

- Implemented scoped drill-page with submitted/valid/invalid/converted/contribution/memberConverted filters, cumulative history and frozen-label distributions. Reuses existing query/detail permissions and original scope authorization.
- Workbench team metrics, member metric tags, monthly actual, funnel, channel/category counts open a paginated responsive drawer. Shows Lead business number, submitted name, provider snapshot, contribution snapshot and deterministic first-purchase order number/amount/effective time. Full-page links retain existing Lead/order permissions and object checks.
- Verification Maven MediaLeadPageQueryTest, MediaLeadAnalysisServiceTest, MediaLeadAccessTest passed 23 tests, including frozen/tenant/deletion/empty scope, member date semantics, amount-threshold difference, cumulative/distribution filtering and missing detail permission. SQL exercised by MyBatis/H2 MySQL mode.
- Chrome isolated fixture checks passed at 1440 and 390 widths, including order/name/provider projection, team/member requests, pagination, retry, distribution/funnel parameters, no-detail entry and cumulative legacy entry. Ego browser executable unavailable, used isolated Playwright Chrome. Screenshots in frontend/workbench/tmp/media-lead-drill-20261112.
- Frontend typecheck and production builds passed. Existing chunk-size/mixed-import warnings remain.
- API documentation updated. No shared backend restart or deployment. Real-account HTTP/permission/data acceptance and MySQL query-plan performance remain unverified. Full-detail links continue to require original business visibility.
- Ownership released on delivery. Preserved concurrent exam-collection/schema/handoff changes. Non-goals include changing attribution, assigning roles, migrating SQL or expanding existing Lead/order object access.
