# 多周期成交率明细改造

- Environment: local; workstream: conversion-details-20261010; owner: Codex current thread.
- Branch: main; worktree: D:/ZSJ-OS; base: c82c8605d86aef5c8f4cd328600bc7c3efc2bf90.
- Goal: approved conversion-only filtering API, same-drawer readonly detail navigation and route cleanup.
- Non-goals: conversion calculation changes, database changes, dependencies, Vue UI, shared service operations, Git integration.
- Ownership: new conversion VO/DAL/assembler/tests; PerformanceStatisticsService and SalesPerformanceController; Workbench salesPerformance service, SalesPerformancePage, new conversion components/tests; readonly option in LeadAppealPanel if needed; PerformanceShared overlay lifecycle; docs/api/sales-performance.md appended conversion contract; this record.
- Dependencies: existing PerformanceConversion and PerformanceAccess contracts; existing managedLead and managementSalesOrder APIs. Preserve all pre-existing edits, including sales-performance documentation and SalesOrderDetailCards.
- Verification: focused Java calculation/filter/auth/SQL tests; Workbench typecheck, focused tests, build; isolated synthetic API browser fixture at desktop/mobile widths for filters, detail return and route cleanup. Live backend verification subject to available environment.
- Target branch / integration order: None.


## Delivery — 2026-10-10

- Implemented dedicated `GET /zsjos/sales-performance/conversion-details`, retaining the existing conversion population algorithm. Snapshot enrichment is tenant/deletion scoped and batched; all filters precede pagination; facets and summary use the unfiltered population.
- Workbench conversion drawer uses server snapshot options, a fixed original-card summary with a live-data mismatch notice, same-drawer readonly lead/order views, preserved page/filter/scroll state, and retained-route cleanup. Long rule text is collapsible on desktop/mobile. Generic metric details remain supported.
- Reused `LeadDetailOverview`, read-only history panels and `SalesOrderDetailCards` team mode; only `LeadAppealPanel` gained an optional readOnly flag. Existing SalesOrderDetailCards/api.ts/layout changes from other workstreams were preserved.
- Updated `docs/api/sales-performance.md` by appending the new endpoint, filtering/count semantics, permissions, timestamp/cache limits and interaction contract, preserving its pre-existing edits.
- Added files: ConversionDetailVO.java, ConversionSnapshotMapper.java, ConversionDetails.java; ConversionDetailsTest.java, ConversionDetailAccessTest.java, ConversionSnapshotMapperTest.java; ConversionDetailDrawer.tsx, ConversionRecordDetail.tsx, services/conversionDetails.test.ts; test/conversion-details.html, .tsx and -browser.py.
- Java compile passed. Focused reactor tests passed: 42 tests, zero failures/errors/skips, including six-period filtering, threshold and original population regression, scope denial, metadata tenant/deletion isolation and multi-product deduplication. Mapper execution used isolated H2 MySQL mode; JSON helper aliases model the existing MySQL JSON calls.
- Workbench typecheck passed. Frontend tests passed: 24 across salesPerformance, conversionDetails, performanceCache and performanceSession. Production build passed; Vite reports the existing large-chunk advisory.
- Real Chromium fixture passed at 1600x1000 and 390x844: combined filters, pre-pagination query parameters, constant ratio, pagination/scroll restoration, readonly full order and lead views, hidden unauthorized entries, query and detail error/retry states, empty results, retained-route cleanup for conversion and generic drawers, no runtime errors. Synthetic HTTP adapter rejects non-GET calls. Screenshots and logs are outside the repo at D:/ZSJ-OS-backups/conversion-details-browser and conversion-*.log.
- Verification boundary: no authenticated request against the newly deployed endpoint or real MySQL query execution was performed. The existing shared backend was not restarted or deployed. No schema/migration/dependency, database/account/permission, branch/worktree, commit or push operation was performed. Deployment and authenticated live acceptance remain external follow-up.
- Ownership released on delivery; no integration operation planned.
