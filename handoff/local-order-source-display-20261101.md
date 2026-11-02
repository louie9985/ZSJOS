# Order source display — 2026-11-01

- Environment: local (environment variable and environment file absent).
- Workstream: order-source-display-20261101; owner: current Codex chat.
- Goal: persist explicit self-sourced/no-provider evidence in order snapshots and display it without guessing missing history.
- Non-goals: historical data repairs, permissions, database schema, deployment.
- Branch/worktree/base: main; D:\ZSJ-OS; 0ecc74d4d13bcfbc0361ecdbc2bbd73327359425. Integration: None.
- Ownership: SalesOrderRespVO, SalesOrderListItemRespVO, SalesOrderServiceImpl (source snapshot/projection only); new SalesOrderSourceSnapshotTest; workbench services/api.ts (order source fields only), services/salesOrder.ts, components/SalesOrderDetailCards.tsx and SalesOrderTableColumns.tsx, source display tests; admin OrderHistoryFacts.vue; docs/api/zsjos-sales-order.md (source display addition only); this record.
- Dependencies: existing concurrent edits preserved; previous order-profile work released ownership. No new dependencies.
- Verification artifact ownership: frontend/workbench/tmp/order-source-display and frontend/admin/tmp/order-source-display; isolated local fixture servers only, no shared services.
- Scope update: own the source-person selectors in frontend/workbench/src/styles/pages/sales-order.css to prevent the new explanation being truncated on mobile.
- Verification: focused backend snapshot tests and compile; Workbench render/helper tests and typecheck; Admin typecheck; browser where available. Old snapshots must remain unknown and must never consult current Lead state.

## Delivery — 2026-11-01
- Implemented optional snapshot evidence selfSourcedWithoutProvider and list projection leadSelfSourcedWithoutProvider. No names are replaced with status text; historical absent evidence remains unknown. Both self-sourced types use their matching explanation only with explicit true evidence.
- Workbench list/detail and Admin OrderHistoryFacts consume the state. Source person occupies a wrapping detail row so mobile users can read the explanation.
- Backend: Maven reactor targeted test run BUILD SUCCESS; 2 snapshot tests passed (explicit/missing/provider/non-self evidence plus JSON compatibility). Main and test sources compiled. Log: frontend/workbench/tmp/order-source-display/backend-test.log.
- Workbench: typecheck passed; 12 focused service/render tests passed, including re-run after responsive fix. Broader style guards: 26 passed / 4 failed in unrelated existing class-statistics-preview, content-review-attachments, calendar-colors and bpm-approval-center styles; no failure points to changed selectors.
- Admin full typecheck completed with only existing unused handleRefresh (mes/pro/task/edit/index.vue:86) and Refresh (zsjos/leadQualification/index.vue:129). No pass claimed; log under frontend/admin/tmp/order-source-display/typecheck.log. First attempt exhausted default Node heap; retry used project-standard 8GB.
- Browser: ego unavailable; existing Playwright/Chrome fallback verified actual React/Vue components with synthetic data at 1440 and 390 widths, explicit self/no-provider, recorded name and missing historical state. Screenshots inspected; corrected mobile truncation. Final capture skipped waiting for remote fonts to avoid a hanging fixture screenshot. Fixture servers stopped.
- Scope checks: git diff --check passed. API documentation updated. Existing edits preserved; no dependency, schema/data, permission, shared service, commit, push or deployment changes.
- Limitation: this fixes newly generated snapshots once deployed. The user's already-created order lacks the new historical evidence and is NOT repaired by this code; historical repair needs independently verified submission-time evidence. No live-account/HTTP acceptance performed.
- Ownership released.
