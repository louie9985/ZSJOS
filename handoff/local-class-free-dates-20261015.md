# Class free exam dates — 2026-10-15

- Environment: local (environment variable and file absent); owner: Codex; workstream: local-class-free-dates-20261015.
- Goal: independently editable class dates with optional existing-exam calendar selection, including multi-day ranges.
- User decision supersedes the mandatory published exam association in docs/api/delivery-class-management.md.
- Branch: main; worktree: D:/ZSJ-OS; base: 315efd729f2e049fc2639194567d86077ae91184; target branch/integration order: None.
- Ownership: deliveryclass save/response/exam-option VOs, DeliveryClassServiceImpl, date snapshot helper and focused tests; Workbench DeliveryClassPage, dedicated date/calendar components and tests, services/api.ts; existing Admin class-management page, its API and dedicated calendar component; docs/api/delivery-class-management.md; this record.
- Dependencies: existing date snapshot column, nullable exam association, class exam-options permissions, installed React/Ant Design and Vue/Element Plus.
- Non-goals: SQL/database changes, permission changes, calendar management behavior, service restarts, Git publication, unrelated concurrent work.
- Verification: focused backend date/create/update/compatibility tests, Workbench typecheck and focused tests, Admin scoped checks, desktop/mobile browser interaction fixtures; distinguish fixtures from deployed authenticated acceptance.

## Single-date correction registration

- User confirmed one class exam date only. Reacquire the existing frontend/date-calendar/test/documentation scope; remove end-date controls and submit only startDate. Calendar selection copies the selected calendar day, including a day within a multi-day reference. Backend historical range and old-client compatibility remain supported. Environment/branch/worktree unchanged. Verification: focused tests/types/lint and both desktop/mobile browser fixtures. No external changes.

- Correction delivered: both forms have one exam date picker; calendar selections return the selected day; no endDate submitted. Workbench scoped TypeScript and 6 focused tests passed; Admin scoped ESLint and Vue fixture compilation passed. Desktop/mobile browser fixtures passed for both consumers at 1440/390 px, including modified date save/reopen and optional calendar error/empty states. Backend unchanged; prior backend evidence still applies. API documentation synchronized; no deployment/restart/database changes. Ownership released.

## Delivery

- Implemented both existing frontends: editable start date and optional multi-day end date; optional calendar modal with month navigation, date markers and selectable daily exam entries. Selection copies dates and remains editable; candidate failure does not block manual dates.
- Backend accepts independent dates, persists canonical date/range in existing snapshot column and explicitly clears old calendar association on update. Read projections use frozen snapshots. Legacy ID-only writes remain compatible and retain existing published/unended checks. No schema migration or database write.
- Exam option response includes structured dates. Existing permissions, tenant isolation, department/homeroom checks and optimistic-version behavior remain in place.
- 13 focused Java tests passed after compiling changed sources with Lombok into private output against existing dependency classpath. Covers manual creation, reference override, clearing association, historical frozen ranges, input validation and existing owner-change/options behavior.
- Workbench: 6 focused Vitest tests passed; scoped TypeScript check passed. Full typecheck observed concurrent RegistrationPages.tsx canUpdate errors at lines 503/513; no unrelated edits made.
- Admin: scoped ESLint passed; Vue SFC/API browser bundle compilation passed. Full vue-tsc exhausted its default 4 GB heap, so full Admin type safety remains unverified.
- Browser fixtures passed at 1440 and 390 px for both frontends: calendar reference, full cross-month range, edit after selection, submitted independent payload, reopening saved dates, error and empty/retry. Screenshots inspected. Fixtures replace transport and Admin application wrappers; they are not authenticated target-backend acceptance.
- Temporary evidence: frontend/workbench/tmp/class-free-dates/{java.log,browser.log,admin-browser.log,admin-eslint.log,admin-types.log,calendar-390.png,admin-calendar-390.png}. No dependency added or shared service started/restarted.
- Concurrent registration additions in services/api.ts were preserved; its workstream explicitly recorded release of shared-file write ownership. This work owns only delivery-class sections.
- docs/api/delivery-class-management.md synchronized. Deploy backend before frontends; deployed HTTP/persistence and authenticated runtime remain unverified. Scoped diff checks passed. No commit/push/deployment. Ownership released.
