# local-student-close-reason-20261027
- Environment: local (ZSJOS_AGENT_ENV unset, /etc/zsjos/agent-environment absent).
- Goal: remove obsolete student pause/termination reason projections and filters; show current teaching closure reason from persisted close event.
- Non-goals: schema/data deletion, order cancellation behavior, lifecycle permissions, deployment.
- Branch main; worktree D:/ZSJ-OS; base 8279212fb97129ba6b006dbb0452ad843dfde4b5; owner Codex /root; integration None.
- Ownership: StudentServiceColumns.tsx/test.tsx, services/api.ts (StudentServiceSummary only), StudentServiceSummaryVO.java, StudentServiceSummaryReader.java/test, TeachingServiceEventMapper.java, StudentFilterFields.java, docs/frontend/advanced-filter-field-catalog.md, docs/api/teaching-service-close-restore.md, this record, dedicated close-reason browser fixture/check.
- Dependencies: existing teaching close event persistence and authorized student projection; preserve other dirty work.
- Verification: focused summary/lifecycle/frontend tests, backend compile, browser at desktop/mobile; scoped diff and consumer search.

## Delivery
- Removed pauseReason/terminationReason from student summary VO/type/reader, table/detail and advanced filter catalog. Preserved underlying historical columns and order-cancellation writes.
- Added closeReason from latest persisted close event for currently teaching-closed services, batch scoped to already authorized service IDs. Restored services return no current reason; missing historical event remains empty. Existing required 500-character close form and lifecycle commands unchanged.
- Workbench consumes the changed summary; repository search found no Admin consumer of these summary fields. Admin lifecycle endpoints unchanged. Synchronized API and filter docs; obsolete saved filters remain rejected by established validation.
- PASS frontend focused tests: 4. PASS Maven reactor compile/test with bounded JVM: StudentServiceSummaryReaderTest 6, TeachingServiceLifecycleServiceTest 14. Initial unrestricted JVM failed native memory during test compilation; bounded retry passed.
- PASS actual Chrome component fixture at 1440/390, persisted reason / restored / missing record states and old-label absence. Screenshots visually inspected in local temp student-close-reason-{width}.png. Scoped diff check passed.
- No live-account acceptance/deployment, application data change, migration or dependency changes. Browser fixture validates real components with synthetic data. Ownership released.
