# Homepage metric business-page drill-down

- Environment: local (environment variable absent; no environment-file override).
- Workstream: home-metric-page-20261101; owner: current Codex chat.
- Branch/worktree: main, D:\ZSJ-OS; base: 0ecc74d4d13bcfbc0361ecdbc2bbd73327359425.
- Goal: remove metric explanation text and reuse Lead Management in the metric modal with frozen, server-enforced metric scope and normal layout preference.
- Non-goals: account grants, business data/SQL changes, deployment, service restarts, commits.
- Ownership: HomeMetricCards.tsx; new metric drill-down components/services/tests; LeadManagementPage.tsx; TodayTasksPage.tsx; homeMetric.ts; HomeMetricConfigService.java; HomeMetricController.java; new metric lead drill-down service/tests; docs/api/home-metrics.md; this record.
- Dependencies: existing metric context validation and Lead Management authorization/query/page UI. Other source expansion awaits user scope clarification.
- Verification: focused server scope tests, frontend typecheck, UI/browser desktop/mobile where available, scoped diff check.
- Integration target/order: None.
- Existing unrelated work preserved; no ownership of other dirty files.

## Scope clarification
- User requests source-driven reuse for every configurable business source. Add ownership of the main list integration points in RegistrationPages, ManagementPages, MySalesOrderPage, LeadAppealPage, LeadDuplicateReviewPage, MediaStudentsPage, SubordinateSalesPage, EducationTeamPage and BpmApprovalCenterPage. Preserve the existing edits in MySalesOrderPage. Add HomeMetricPageService and its tests. No edits to shared api.ts, constants.ts or RouteHost.tsx.
- Source adapters preserve the owning business service response and permission checks; metric filters stay server-side. Built-in task sources have no configurable filter and reuse their owning task views.
- Add AdvancedFilterService and a server-only intersection group to ownership: preserve independently valid AND/OR trees when combining immutable metric criteria, frozen dates and user-added filters, without relaxing client filter limits.
- Verification ownership includes test/home-metric.tsx and home-metric-browser.py; update the existing fixture assertions for the intentionally removed explanatory UI and reuse the real page components.

## Delivery — 2026-11-01
- Completed source-driven full business page modal for all 11 configurable business sources and the 2 built-in task sources. Metric explanation text removed; multi-operand metrics retain only a source selector. Native layout preferences, business detail/actions and permission-dependent presentation are reused.
- Added a frozen-context source-page adapter, native response projections and server-only AND intersection preserving original OR trees, dictionary checks and independent filter limits. Existing Admin configuration and simplified details API remain compatible. No database or account changes.
- Isolated business-page route/search state from the home metricContext deep link. Lead modal preserves the global inbox preference; defaults for registration/duplicate/media/team pages do not accidentally omit counted statuses.
- Withdrawal export is hidden inside a metric modal because the existing export-task contract cannot carry the metric constraint; the ordinary withdrawal page is unchanged. Student metrics count distinct people while the reused page shows their qualifying course-service rows, as documented.
- Backend: focused Maven tests passed: HomeMetricPageServiceTest 16, HomeMetricContextTest 7, AdvancedFilterServiceTest 27 (50 total). Includes range mapping, immutable scope after clearing filters, OR composition, context denial, source/operand tampering, page bounds and existing context isolation.
- Frontend: 5 metricPageScope tests passed; npm typecheck passed. Vite production build passed to isolated tmp/home-metric-page-dist; existing large-chunk warnings and shared static/dynamic import warnings remain informational.
- Browser: project Playwright/Chromium fallback (ego-browser CLI unavailable). Real component rendering with isolated HTTP: all 13 sources, lead desktop table/master-detail and mobile, route isolation, deep-link refresh and close. No authenticated live business API acceptance claimed. Screenshots: test/home-metric-business-1440-table.png, test/home-metric-business-1440-master-detail.png, test/home-metric-business-390-table.png.
- Scoped diff/content checks passed. No deployment, running backend restart, commit or push. Backend and Workbench update are both required for the new source-page endpoint to become live. Ownership released on delivery.
