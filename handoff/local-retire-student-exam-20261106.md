# Remove retired student exam commands

- Environment local, ZSJOS_AGENT_ENV unset and marker absent.
- Workstream local-retire-student-exam-20261106, owner current chat.
- Goal remove retired student delivery-stage and exam-date commands and exclusive dependencies.
- Non-goals active delivery submissions, class schedules, historical persisted data, role assignments and deployment.
- Branch main, worktree D:/ZSJ-OS, base 7a7cf5e14bef1ff193de5c4cc39af8a5255a7d5c.
- Ownership StudentContactController, its permission test, StudentContactService/Impl/Constants, two retired request VOs, StudentContactServiceImplTest, ServiceRelationMapper, StudentServiceObjectPermissionProvider and its affected tests, registration-fulfillment-api.md and this record.
- Dependencies no frontend callers found in either consumer. Concurrent delivery-complete work owns StudentDeliveryController and V364, untouched.
- Verification focused Maven contact/controller/permission tests, compile and both frontend reference audits.
- Integration target/order None. Database metadata cleanup requires explicit shared-state target confirmation.

## Delivery

- Removed both retired HTTP handlers, Service declarations/implementations, request DTOs, retired error and permission constants, attachment permission bypass, retired object actions and unused exam-date/reminder DAL writers.
- Updated API contract and existing controller regression to assert endpoint removal and active attachment permissions. No frontend callers in Admin or Workbench.
- Maven main/test compilation passed. Focused suite ran 87 tests, 85 passed, 2 errors in unchanged transferActions at StudentServiceObjectPermissionProvider.java:61 (null classId lookup in immutable map). No assertion failures. Contact service 39, first-contact progression 25, controller permissions 5 and participation 4 all passed. Object permission tests passed 12/14.
- Scoped git diff --check passed. Historical migrations and persisted historical fields remain unchanged.
- Database menu definitions 73428 and 602311 still require separately authorized shared database metadata cleanup. No database mutation, commit, push, restart or deployment performed.
- Ownership released for source delivery.

## Authorized database continuation

- User authorized cleanup of local yudao-mysql / ruoyi-vue-pro, only menu IDs 73428 and 602311, soft deletion with historical role links retained.
- Environment remains local. Same branch/worktree/base registration. Ownership adds migrations/V365__retire_student_exam_commands.sql, verify-bootstrap.sql, migrations/README.md, API contract and artifacts/student-exam-retirement validation scripts/results.
- Read-only inspection found MySQL 8.4.11, both V364 ledgers present, both matching active buttons under 73020, and 16 historical role links.
- Verification controlled synthetic database on same engine for initial/repeat/partial/missing prerequisites/identity conflict/ledger failure/recovery and UTF-8 HEX, then scoped backup and local application under migration lock. No unrelated migrations or role data changes.
- Shared operations guide already has concurrent edits, so deployment details go in migration README and this record.

## Database delivery

- Added V365 after V364. Both obsolete menu rows are now soft-deleted, disabled and hidden in authorized local yudao-mysql / ruoyi-vue-pro.
- Scoped backup D:\ZSJ-OS\backups\mysql\pre-V365-menu-20261106154240.sql. V365 applied under zsjos_schema_migration lock. Both ledgers exist and authoritative Core file checksum is 3c0426dc2589aea493a4d437db40b3d713d7a2746ab8dc8859652e62f65b7ba0.
- Same-engine isolated verification database verify_student_exam_dc261b22 retained. Tests passed for initial/repeat, partial state, existing markers, each missing prerequisite ledger, wrong ID/permission/parent, second-ledger insert failure and rollback, recovery, and UTF-8 HEX.
- Local scoped bootstrap acceptance returned student_delivery_stages PASS. Static zsjos_db check and scoped diff checks passed. Exact unrelated-menu and full role-table snapshots unchanged, including all 16 historical role links for these two buttons.
- Role assignment menu tree uses System simple-list -> getMenuListByTenant -> logical-deletion-filtered menu query. Both clients retain their established server menu sources. Browser/session acceptance and a full fresh initialization chain were not run for this scoped metadata update.
- Backend changes remain local, with 85/87 focused tests passing as recorded above. No application restart, deployment, commit or push. Ownership released.
