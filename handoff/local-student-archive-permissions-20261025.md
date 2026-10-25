# Registration - local-student-archive-permissions-20261025

- Environment: local (ZSJOS_AGENT_ENV unset; environment marker absent).
- Owner: Codex /root. Branch: main. Worktree: D:/ZSJ-OS.
- Base: 404387de566fcb9abf4b264f0e005bf0566ea209. Target branch/integration: None.
- Goal: independently configure archive sensitive-read and export under student management, without granting the registration pool.
- Non-goals: role assignments, object/data-scope changes, registration permissions, public collection, service restart/deployment, unrelated dirty work.
- Ownership: StudentInfoConstants.java, StudentArchiveService.java, StudentArchiveController.java; StudentArchiveServiceTest.java, StudentArchivePermissionTest.java in the ZSJOS module; script/sql/mysql/migrations/V350__student_archive_read_permissions.sql; script/sql/mysql/tools/test_student_archive_read_permissions.py; docs/api/student-information-collection.md; docs/architecture/data-and-permission-flow.md; this record. Preserve existing uncommitted edits in these files.
- Dependencies: System menu/button configuration; existing archive service relation authorization; existing Admin/Workbench server capability consumers. Historical upgrade compatibility documented by the collection contract; preserve applied V327.
- Verification: focused Java service/controller permission tests; controlled MySQL initial/replay/partial recovery/prerequisite and ledger failures, UTF-8 HEX and unchanged grants; scoped development metadata sync and result comparison; verify both frontend contracts without changing their permission truth.

## Scope update
- Include StudentArchiveExtraService.java, StudentArchiveExtraController.java and StudentArchiveExtraServiceTest.java: the same archive UI's exam/certificate attachments and shipping address also consumed the registration sensitive permission. Switch that direct dependency to archive sensitive-read without changing exam visibility or object scopes.

## Verification and development metadata
- MySQL 8.4 isolated V350 checks passed for initial apply/replay, partial metadata with existing version rows, absent/duplicate parent, missing prerequisite, wrong-parent/deleted/duplicate permission conflicts, ledger failure and recovery with/without an existing ledger. UTF-8 HEX verified. Fixtures retained as archive_permissions_20261025133425_*.
- Applied the same V350 source to configured development target 192.168.2.17/ruoyi-vue-pro after read-only state inspection (V327/V349 in both ledgers, no V350/new buttons). Added only menu IDs 602393/602394 and both V350 ledger rows; replay verified. Both buttons have ancestor chain student-management 73020 -> Workbench 6735, excluding registration pool 73000.
- Preserved whole-table system_role_menu and system_tenant_package signatures and all unrelated menu rows. No new grants to study_planner/delivery_manager. Scoped preimage: C:/Users/EDY/AppData/Local/Temp/zsjos-archive-permissions-before-20261025133528.json.
- Workbench existing real Chrome fixture test/student-archive-inline-browser.py passed default sensitive loading, protected lazy attachments, denied access/retry, stale service isolation, 1440/390 interaction and layout. Evidence: C:/Users/EDY/AppData/Local/Temp/zsjos-student-archive-inline-c3p9jru8.
- Vue Admin actual StudentArchivePanel mounted through its existing Vite compiler with isolated API-module fixtures: denied capabilities exposed no sensitive/attachment/export controls or requests; allowed capabilities invoked detail/sensitive/image/export and produced a download. No live writes. Evidence: C:/Users/EDY/AppData/Local/Temp/zsjos-archive-permissions-admin-lq6rx48u.
- No frontend source changes, dependency additions, service restart, deployment, real-account acceptance or Git publication. Matching backend deployment and explicit administrator assignment of archive permissions remain necessary.

## Delivery
- Archive Controller + Service and Extra Controller + Service now use independent archive sensitive/export permissions throughout capability projection, sensitive detail/edit/save, byte read/upload, shipping-address maintenance and masked export. Registration endpoints retain original permissions. No fallback to old grants.
- Final Maven reactor BUILD SUCCESS: 50 tests, zero failures/errors (StudentArchiveServiceTest 13, StudentArchiveExtraServiceTest 16, StudentArchivePermissionTest 3, StudentInfoFormServiceTest 18). Includes exact visible-service denial, attachment form/field isolation, registration-only grants denied for archive access, archive-only grants allowed, export masking, unchanged exam visibility and registration endpoint authorization. Log: C:/Users/EDY/AppData/Local/Temp/zsjos-archive-permissions-maven.log.
- During verification, the new endpoint-matrix test initially included the unrelated saveVisibility command and expected archive grants to authorize it. Corrected the test to assert the unchanged exam-calendar:manage boundary; the final complete focused suite passed.
- Scoped diff checks and UTF-8/new-file whitespace checks passed. Existing unrelated dirty source preserved. API and permission architecture documentation synchronized. Ownership released.
