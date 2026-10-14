# Registration — local-delivery-service-subject-20261014
- Environment: local (fallback); owner Codex /root; branch main; worktree D:/ZSJ-OS; base 7ddd776265db39a8bdec7238867271baf17bbcc5. Target branch/integration: None.
- Goal: approved delivery-service subject plan; old and new delivery rows one service/product each, both frontends; preserve media aggregation and existing authorization.
- Non-goals: finance gate, order rules, data backfill, V317 execution, grants, dependencies, service lifecycle, Git operations.
- Ownership: new delivery-service VO/request/mapper/service/tests; MyStudentServiceImpl projection visibility only; MyStudentController additive endpoints; advancedfilter service-subject bindings/catalog/controller/visible users/template compatibility; RegistrationNotifyPublisher/ServiceImpl service-target notifications; Workbench RegistrationPages, service API, notification actions/tests and DeliveryClass labels; Admin my-students, registration API, message routing and delivery-class labels; dedicated browser fixtures; directly affected API/operations docs; this handoff. Preserve existing item-task changes and other work.
- Dependencies: existing service relations, system scope, service permissions; no schema migration for service reads.
- Verification: focused Java query/filter/permission tests, both frontend static checks, real component browser fixtures desktop/mobile, database read-only SQL contract if available; delivery record distinguishes deployment.

## Verification checkpoint
- Implemented additive service-page/search/detail contracts, database service pagination/scope, same-service advanced-filter bindings, existing template compatibility and singleton projection. Old aggregate endpoints and media reads preserved.
- Both frontend delivery pages use service identities; Workbench table/inbox/mobile detail, exact service selection, legacy person selection, stale-response guard and shared identity warning. Admin service detail and notification targets added. Item-task planner notifications now target exact service; historical aggregate notifications remain compatible.
- 89 focused Java tests passed: AdvancedFilter 27, MyStudent 18, RegistrationNotify 3, RegistrationService 27, StudentServicePermission 9, StudentServiceRead 5. Workbench typecheck passed.
- Browser real components with isolated data: React 1440 inbox/table and 390 mobile detail plus accept submits selected service 2; legacy person choice, delayed detail and error action isolation passed. Admin 1440/390 exact course detail passed.
- Actual local MySQL read-only joins/count/page executed: 123 active/paused/completed services across 121 people; page returned 20. No business writes, migration, service restart, deployment or grants. Authenticated end-to-end runtime acceptance remains unverified.
- API documentation synchronized. Admin full typecheck final result recorded in final delivery; previous checks blocked by unrelated withdrawal duplicate fields. No production build required by bundling/routes/assets scope; runtime endpoint deployment remains pending.

## Final source delivery
- Admin final typecheck failed only on existing withdrawal/index.ts duplicate fields; no changed-file errors. Workbench typecheck PASS, scoped diff check PASS. Browser screenshots inspected.
- No deployment, business database writes, or runtime restart. Temporary fixture servers closed after verification. Source ownership released.
