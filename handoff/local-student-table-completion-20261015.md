# Student delivery table completion

## Registration
- Environment: local (ZSJOS_AGENT_ENV unset; environment file absent). ID: local-student-table-completion-20261015; owner: Codex /root.
- Branch: main; worktree: D:/ZSJ-OS; base: 315efd729f2e049fc2639194567d86077ae91184. Target branch/integration: None.
- Goal: approved full delivery table and Workbench detail, additive summaries, scoped filters/sorting and compatibility verification.
- Non-goals: finance/sensitive content, workflow changes, grants, database changes, dependencies, deployment/restarts, Git publication, Admin UI expansion.
- Ownership: StudentServiceReadService and service-subject VO/query/mapper; new student summary/task projection helpers and tests; student-service advanced-filter metadata/bindings; StudentContactServiceImpl task selection only; Workbench MyStudentsPage and service types, focused student-table components/tests, student-only LeadDetailOverview label; service/list sorting documentation and this record. Existing registration/class edits preserved.
- Dependencies: delivered registration early-delivery, guided-detail and class-date edits retained; previous owners released files. Separate delivery-manager feature is not restored.
- Verification: focused backend query/projection/authorization tests and compile; Workbench tests/typecheck; Admin additive-contract compatibility; browser fixtures at 1440/1280/390; live API if available, separately reported. Shared build outputs serialized.

## Scope clarification
- Own new student-table-completion browser fixtures and StudentServiceColumns/Filters tests; existing advanced-filter catalog count regression updated for additive fields. No ownership of unrelated existing advanced-filter completeness/template-guard failures.
- Existing per-row MyStudentServiceImpl projection remains unchanged; the new summary reader batches added associations.

- Scope update: template controller scene validation and save-request validation are included to restore approved student_service saved-template compatibility; existing permission IDs reused.

## Delivery
- Implemented 16 default columns and optional service/course/collaborator/class/contact/lifecycle/order-handoff/info-submission columns. Current-service detail reuses column display policy; student-only lead owner label is explicit. Mobile includes planner and acceptance. Existing early-delivery order-review projection preserved.
- Added batched summaries with service task priority, stable latest-contact selection, metadata-only forms, snapshot-based order labels/actors, feature and object checks. Added server quick filters/date sorts and same-service advanced fields. Existing base projection still performs its prior per-row reads; new summaries do not add per-row requests.
- Saved template route now accepts student_service with student query permission and preserves old student template fallback. No grants changed.
- PASS: backend compile and 69 focused tests (64 service/projection/contact/catalog tests plus 5 template permission tests; final runs against their stable inputs). Workbench typecheck and 6 focused frontend tests.
- PASS: real-component browser fixtures at 1440/1280/390, exact selected service, detail, old hidden/mobile and visible/activation preferences, newly default-visible planner, owner filter and remote sort. Script: frontend/workbench/test/student-table-completion-browser.py; screenshots in Workbench tmp. Fixtures do not call live business APIs.
- PASS: read-only MySQL task-sort query returns 20 rows; latest-contact scalar selection executes and returns 2 latest records. No database writes. Scoped diff checks pass.
- Admin response shape remains additive; existing service property and route consumers inspected. Full Admin typecheck blocked only by existing duplicate fields in src/api/zsjos/withdrawal/index.ts. Admin authenticated runtime not verified.
- Earlier broad backend run found pre-existing AdvancedFilterCompletenessTest scene/authority manifest mismatches; not changed. Additive catalog count and directly relevant template scene guard were corrected and targeted tests pass.
- Limits: no deployment/restart, no authenticated real API acceptance, no production build (no routes/dependencies/bundling changes). Browser fixtures and read-only SQL are not deployment evidence. Screenshot artifacts produced; manual visual review remains pending.
- Documentation synchronized. No commits/push, new dependencies, schema migration, or role changes. Ownership released.
