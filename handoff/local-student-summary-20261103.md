# local-student-summary-20261103

- Environment: local (ZSJOS_AGENT_ENV and environment file absent).
- Goal: management student summary, one teaching service per row, class filter and Excel matching visible column order.
- Non-goals: changing existing student/archive permissions, role grants, deployment, dependencies, Git integration.
- Branch: main; worktree: D:\ZSJ-OS; base: f7abc09b91d30e140ef8bf4895b4b8b8181f0681.
- Owner: current Codex chat; integration target/order: None.
- Ownership: new StudentSummary controller/VO/service/tests; new StudentSummary page/service/tests; Workbench constants.ts and RouteHost.tsx; Admin menu exclusion if needed; V361 menu migration and focused SQL verification; docs/api/student-summary.md, architecture/data-and-permission-flow.md and this record.
- Dependencies: existing service pagination, form/archive snapshots, BusinessTable, system permission configuration. Concurrent collection-material task files excluded.
- Verification: focused backend tests/compile, Workbench typecheck/build and column tests, browser desktop/mobile, controlled MySQL migration replay/failure/recovery and scoped local verification.
- Approved exception: summary query permission grants current-tenant full service and field reads, including sensitive/exam fields; export additionally requires summary export. No legacy scope or field gates in this new projection.

- Ownership update: new StudentSummaryMapper bulk form binding; browser fixture under frontend/workbench/test/student-summary.*; Admin route-filter focused test.

## Delivery — 2026-11-03 14:40:24 Beijing

- Branch/worktree/base: registration unchanged; HEAD f7abc09b91d30e140ef8bf4895b4b8b8181f0681.
- Goal/decisions: delivered independent management summary with all current-tenant fields, one teaching service per row; class filters; BusinessTable column state drives Excel field order/visibility. Existing archive gates unchanged.
- Changes: new StudentSummaryController, StudentSummaryVO, StudentSummaryService, StudentSummaryMapper, StudentSummaryServiceTest; Workbench StudentSummaryPage, studentSummary service/test and browser fixture; constants/RouteHost; Admin adminRouteFilter; V361 migration/test_student_summary.py; API and permission-flow documentation.
- Verification: 21 backend tests (8 summary + 13 archive) passed; 4 frontend tests passed; Workbench typecheck and production build passed. Browser fixture desktop/390px, class filtering, hidden column export payload, retry, denied zero reads, query-only no export passed. Existing chunk-size warning retained.
- SQL: MySQL 8.4.11 local ruoyi-vue-pro identified from configuration; isolated retained fixtures passed initial/replay/prerequisite/atomic ledger failure/recovery/conflict. Local V361 applied twice; old menu rows and role grants unchanged, Chinese HEX verified. Backup under backups/mysql/student-summary-20261103143144.json. Read-only SQL probes passed for 100 service/form bindings and latest revisions.
- Dependencies/integration: no new dependency, Git operation or integration; full read exception isolated to new interface, Vue filters unsupported Workbench component. Concurrent collection-material work preserved.
- Remaining: matching backend restart/deployment and real-account/API acceptance not performed; no service control or role authorization change. Administrators must configure query/export grants for ordinary accounts. Fixture browser evidence is not live acceptance. Ownership released.

## Scope correction — 2026-11-03
- User clarifies information means student archive, not service operations. Reacquire same owned summary files, tests, fixtures and docs; remove non-archive columns/projections, retain service row identity/class filter/full-field permissions. No SQL or grants change. Verify focused tests, types and archive fixture.

## Delivery correction — 2026-11-03 15:01:04 Beijing
- Environment/branch/worktree/HEAD: unchanged local/main/D:\ZSJ-OS/f7abc09b91d30e140ef8bf4895b4b8b8181f0681.
- User goal: archive information only, not service operations information.
- Result: removed all service/order/owner/contact-master columns and projection queries; retained archive collection and extra fields plus archive submission metadata. Service row identity and class filter remain. Page/export title now 学员档案汇总. No schema, menu metadata or role grant changes.
- Files: StudentSummaryService.java, StudentSummaryServiceTest.java; StudentSummaryPage.tsx, studentSummary.ts, test/student-summary.tsx; docs/api/student-summary.md, docs/architecture/data-and-permission-flow.md, this record.
- Verification: Maven focused summary/archive tests passed (9+13), Workbench typecheck and 4 column tests passed; browser fixture showed archive columns and emitted archive-only export keys. New regression rejects service fields in export catalog. No bundling/routes/dependency changes; prior build evidence not claimed as rebuilt for this correction.
- Remaining: backend restart/deployment and live-account/API acceptance still pending; fixture is not production evidence. Integration None; ownership released.

## Header wording update
- Reacquire StudentSummaryService.java, existing summary test, browser fixture and API doc; remove label prefixes only, preserve field keys/groups. Environment local; registration metadata unchanged. Verification: scoped text/contract assertions.

## Delivery — 2026-11-03 15:08:01 Beijing
- Branch/worktree/HEAD: unchanged from registration.
- Result: table/Excel column labels now use plain field names; stable keys and groups unchanged. Updated existing workbook assertion and fixture labels.
- Files: StudentSummaryService.java, StudentSummaryServiceTest.java, test/student-summary.tsx, docs/api/student-summary.md, this record.
- Verification: scoped assertions confirm prefixes removed and Excel still consumes the same labels; no runtime/build rerun for mechanical wording change.
- Dependencies/integration: None. Remaining: prior deployment/restart/live acceptance pending. Ownership released.

## Follow-up 2026-11-03 — submitted coverage
Environment local; owner current chat; branch/worktree/base unchanged. Scope: StudentSummary VO/controller/service/test, Workbench page/service, API document and this record. Goal: submitted-only rows/export, multiple classes and class-wide coverage counts; no SQL, permissions or deployment changes. Dependencies existing form binding and service query. Verification: focused backend tests, frontend typecheck/tests and browser where available. Integration None.

Follow-up delivery: submitted-only backend pagination/export and projection; classIds multi-select with explicit repeated-query serialization; coverage ignores keyword/page and uses the same latest-form resolver. API documentation updated. Backend focused tests and frontend 4 column tests passed; typecheck passed before serialization adjustment, final check recorded separately. Real browser/real-account API acceptance remains unverified: running backend has not been restarted to expose coverage; no restart/deployment authorized or performed. No schema or permission changes. Ownership released.
Final verification: backend 10 tests passed (including multi-class coverage/search independence), Workbench final typecheck passed, frontend 4 tests passed, diff whitespace check passed. Browser visual/interaction and deployed API checks remain pending.

## SQL parser repair 2026-11-03
Same local environment/worktree/branch/base and owner. Scope service, service test, this record. Fix missing SQL conjunction whitespace; add actual MyBatis-bound SQL parser regression for default and multi-class queries, counts and pages. No database writes or restart.
`nSQL repair delivery: reproduced the reported EXISTS parser failure with new regression before fix; explicit separator added at SQL composition. All 11 StudentSummaryServiceTest tests now pass, including actual MyBatis bound SQL parsed by JSqlParser for default/filter queries and coverage/count/page. Diff check passed. No database mutation, restart, deployment or real HTTP revalidation performed. Ownership released.

## Comparison columns follow-up 2026-11-03
Environment local, same branch/worktree/base/owner. Scope StudentSummaryService/test, API documentation and this record. Add student.name (service student-name snapshot) and student.mobile (linked Person current mobile) beside submitted info.name/info.mobile; preserve submission and class coverage semantics. No database writes, permissions, restart or deployment. Verify focused backend projection/catalog/XLSX tests. Existing dynamic Workbench consumes columns without frontend change.
`nComparison columns delivery: added student.name/student.mobile and ordered both student and submitted contact columns first. Person reads batched per page. API documentation synchronized. 12 focused backend tests pass, including distinct source values and real XLSX contents; diff check passed. Existing dynamic Workbench column renderer inspected. Browser and real HTTP remain unverified pending running backend update; no restart/deployment performed. Ownership released.

## Filling status follow-up 2026-11-03
Same local context and owner. Scope summary VO/service/test, Workbench page/service/browser fixture, API docs, this record. Supersedes submitted-only rows: all students with submitted-derived filled/unfilled status, server-side column filter and matching export; coverage independent. Verification focused backend parser/projection tests, frontend typecheck/tests, browser fixture. No migration/deployment/restart.
`nFilling status delivery: all service rows restored; drafts omitted from answer projection while student contacts remain. Server-derived status metadata feeds green/yellow tags and single-select header filter. filled query filters before pagination/export; coverage remains independent. Backend 12 focused tests passed including parser checks for all/filled/unfilled, Workbench typecheck and 4 tests passed. Ego desktop fixture verified both tag colors, unfilled row contacts/empty answers, column filter request filled=false and matching export payload. Browser uses synthetic data; real-account API/deployment acceptance remains pending. No restart/deployment/database changes. Ownership released.
