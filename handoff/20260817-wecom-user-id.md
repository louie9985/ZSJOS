# WeCom User ID Normalization Workstream

- Workstream ID: 20260817-wecom-user-id
- Goal: allow multiple users without a WeCom userid while preserving tenant-scoped uniqueness for configured WeCom userids
- Non-goals: execute migrations, modify a real database, change accounts or permissions, alter notification delivery behavior, add dependencies, or change unrelated domains
- Branch: main
- Worktree: D:\ZSJ-OS
- Base commit: 84474ae6083a64343f5b39b397e5143b233523ae
- Target branch: main
- Ownership scope: System admin-user create/update normalization and focused tests; Core system_users desired schema and fresh bootstrap baseline; V077 forward migration; bootstrap, migration, and operational documentation; this handoff file. Existing overlapping edits are preserved.
- Owner: Codex root
- Dependencies: existing Hutool string utilities, MyBatis-Plus persistence, MySQL generated-column and unique-index behavior; no new dependency
- Integration order: normalize application writes; align desired and bootstrap schemas; add repeatable V077 repair; update migration wiring and documentation; run focused Java and SQL verification
- Verification plan: focused AdminUserServiceImplTest; System module compile/test as feasible; migration static/repeatability checks and controlled fresh/upgrade database tests when available; scoped diff and whitespace validation
- Status: completed

## Delivery 2026-08-17 18:08:21 +08:00

- Branch: main
- Worktree: D:\ZSJ-OS
- HEAD commit: 84474ae6083a64343f5b39b397e5143b233523ae
- User goal: fix user creation failing with duplicate tenant/blank WeCom userid values
- Key decisions: normalize optional WeCom userids with `trimToNull` at the System service boundary; preserve tenant-scoped uniqueness through a generated nonblank database value; block V077 before mutation when trimming would collide configured values; preserve all unrelated working-tree changes and do not execute the migration against a real database
- Execution or analysis result: create and update now persist blank WeCom userids as SQL NULL and trim configured values; fresh and desired schemas share the same generated unique constraint; V077 normalizes existing values, replaces the V028 raw index, records both migration registries, and has verification and operational documentation
- Changed files: backend/yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/service/user/AdminUserServiceImpl.java; backend/yudao-module-system/src/test/java/cn/iocoder/yudao/module/system/service/user/AdminUserServiceImplTest.java; script/sql/mysql/00-bootstrap-schema.sql; script/sql/mysql/schema/core.sql; script/sql/mysql/migrations/V077__normalize_wecom_user_id_uniqueness.sql; script/sql/mysql/bootstrap.sql; script/sql/mysql/verify-bootstrap.sql; script/sql/mysql/migrations/README.md; docs/operations/database-migrations.md; handoff/20260817-wecom-user-id.md
- Verification evidence: `mvn -pl yudao-module-system -am -Dtest=AdminUserServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false test` PASS, 39 tests with zero failures; `zsjos-db check` PASS; isolated MySQL V077 first run, second run, blank/configured normalization, unique index, and conflict-before-mutation checks PASS; full `test-fresh` executed bootstrap including V077 without a V077 failure but retains the unrelated existing `lead_filter_versions` and `module_schema_versions` verification failures; `git diff --check` PASS
- Dependency or integration impact: no new dependency; V077 must be reviewed and applied to existing environments before relying on the normalized schema contract; application deployment is required for service-side normalization; no shared database, account, permission, branch, commit, or service state was changed
- Remaining work: obtain separate approval before applying V077 to any existing environment; resolve the unrelated fresh-database verification failures separately
