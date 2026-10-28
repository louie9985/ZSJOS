# local-lead-sort-pagination-20261028

## Registration — 2026-10-28 Beijing
- Environment: local (environment variable and marker absent). Owner: current Codex chat.
- Branch: main; worktree: D:/ZSJ-OS; base: 0628f72f2c6208a6cc553f3f29b893de8f4ec697. Integration: None.
- Goal: explicit sorting overrides task priority; table deep-link details do not expand server pages.
- Non-goals: database, permissions, deployment, service operations, unrelated changes.
- Ownership: LeadManagementPage.tsx; LeadMapper.java; LeadMapperSqlTest.java; frontend/workbench/docs/lead-inbox-sorting.md; new test/lead-sort-pagination-browser.py; this record. Prior owners released; preserve their uncommitted changes.
- Dependencies: existing APIs, BusinessTable, installed test tooling.
- Verification: focused SQL ordering tests, Workbench tests/typecheck, real browser synthetic-transport page navigation and detail checks; inspect Admin consumer coverage.

## Delivery — 2026-10-28 15:12 Beijing
- Context and HEAD unchanged from registration. Goal: repair explicit time sorting and deep-link pagination.
- Changes: LeadMapper omits pending-task CASE ordering when both explicit sort parameters are supplied; Workbench disables request priority and local task reordering for explicit sorting. Table details by ID/number update existing rows only; pinned records and delayed acceptance refresh cannot add rows to server pages. Default ordering and non-table behavior retained. Updated sorting documentation and added focused backend/browser regressions.
- Changed files: registered LeadManagementPage.tsx, LeadMapper.java, LeadMapperSqlTest.java, docs/lead-inbox-sorting.md, test/lead-sort-pagination-browser.py and this record. Preserved all preexisting work.
- Verification: Workbench typecheck passed after final edit; 28 focused leadManagement/subordinateSalesSorting tests passed. Isolated javac compiled current Mapper and SQL test against installed project dependencies; all 17 LeadMapperSqlTest tests passed, including explicit first-follow-up ascending/descending and incomplete-sort default priority. Temporary evidence: Windows TEMP/zsjos-lead-sort-pagination-20261028. Initial runner argument length issue resolved with Java argument file; no backend output folders modified.
- Real Chromium with existing localhost:5174 and synthetic API responses: actual LeadManagementPage opens deep-link details separately; pages 1, 2 and 3 each show 20 rows; explicit first-follow-up sorting requests pendingTasksFirst=false and preserves API row order despite a pending task on the final row. Test closes the automatically opened drawer before pagination. Scoped diff check passed.
- Consumer review: Admin leadManagement API uses the shared page/search-page endpoints, with no explicit sortField/sortOrder/pendingTasksFirst declaration in its request contract; its default ordering is unchanged and covered by Mapper default-order checks. No Admin implementation change.
- Integration/dependencies: None. No DB, permission, service, deploy, commit or push operation. Backend and Workbench changes must both be deployed for end-to-end effect.
- Remaining: real-account/API acceptance and production data cross-page verification are unverified; browser uses synthetic transport, Java tests inspect generated SQL rather than executing live MySQL queries. No claim of deployment or resolution of individual sales visibility filters. Ownership released.
