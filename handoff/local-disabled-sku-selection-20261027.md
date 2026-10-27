# Disabled SKU selection repair

## Registration — 2026-10-27 (Asia/Shanghai)

- Environment local; owner Codex root; branch main; worktree D:/ZSJ-OS; base 8279212fb97129ba6b006dbb0452ad843dfde4b5.
- Goal: exclude disabled-only attribute choices from sales catalog and prevent stale SKU submission on invalid order-picker combinations; retain historical snapshot display.
- Scope: ZsjosProductSkuServiceImpl.java and its existing test; SalesOrderCoursePicker.tsx/test.ts (write pending other workstream completion); new focused picker browser fixtures; docs/api/zsjos-product-configuration.md; this record. No database/runtime restart/deployment/dependency change.
- Existing picker popup/virtual-list changes belong to local-sales-course-full-labels-20261027; preserve them. User asked whether ownership is released; backend work proceeds independently.
- Verify focused backend/frontend tests, Admin/Workbench/H5 shared catalog consumer compatibility, picker real-browser selection/invalid/empty/history behavior. No production build unless build inputs change. Target integration/order None.

- Ownership update: user confirmed the long-label task finished; its delivery record now releases ownership. Picker writes proceed preserving its popup classes/virtual-list settings. Use existing local Vite only; no shared service restart.

- Scope addition: extend existing frontend/workbench/src/services/productSpecs.test.ts to exercise the filtered catalog response with actual Admin, Workbench and H5 specification utilities; no consumer implementation duplication or changes.

## Delivery — 2026-10-27 16:48 (Asia/Shanghai)

- Result: sales catalog attribute values are intersected with values actually used by enabled SKUs for that course; management attribute lists remain complete. Workbench picker independently filters orphan/disabled-only choices against returned SKUs, clears submitted SKU on incomplete/invalid/ambiguous combinations, preserves partial attribute input, and clears unavailable new selections after catalog refresh. Historical order snapshot mode remains unchanged. Long-label popup/virtual-list changes preserved.
- Changed files: backend product SKU service and its focused test; Workbench SalesOrderCoursePicker.tsx/test.ts and services/productSpecs.test.ts; new test/disabled-sku-selection.html, .tsx and -browser.py; docs/api/zsjos-product-configuration.md; this handoff.
- Verification: backend reactor command with -am passed ZsjosProductSkuServiceImplTest 13/13 (including catalog orphan-value filtering, empty catalog and complete management attributes). Initial module-only invocation failed due to stale installed BPM dependencies, resolved by compiling upstream reactor modules; no unrelated source edits. Frontend picker/modal tests 12/12 and shared product-spec tests 12/12 passed, exercising actual Admin/Workbench/H5 utilities. npm run typecheck passed with bounded heap.
- Chromium production-component fixtures passed at 1440/390 widths: disabled-only choices absent, valid-to-invalid transition emits empty SKU, form submit blocked, completing valid combination works, catalog removal clears selected SKU, empty enabled catalog, historical snapshot and reselection. Existing long-label browser fixture also passed both widths; screenshot inspected. Scoped diff whitespace checks passed.
- Shared-consumer coverage: backend response shape unchanged, Admin direct SKU selector and H5 catalog consumers inspected; actual three-runtime spec utilities tested with filtered/empty payloads. No real-account Admin/H5 UI or live updated backend endpoint acceptance; existing server not restarted. Browser validation uses synthetic catalog, not live-account data. No bundling/runtime config change, production build not required for this local fix.
- Branch/worktree/HEAD unchanged from registration. No DB data/permissions/dependencies/commits/deployment changes. Backend filter requires normal deployment/restart to affect the running server; local Vite can consume picker changes without restart. Remaining: deployment and real-account acceptance. Ownership released.
