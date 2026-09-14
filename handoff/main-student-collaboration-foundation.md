# main-student-collaboration-foundation

- Scope: 协作组 DO/Mapper、服务关系字段、V218 可复现迁移、schema/core.sql、00-bootstrap-schema.sql、verify/student-collaboration-foundation.sql、tools/test_student_collaboration_foundation.py、本记录。
- Non-goals: 权限查询、任务通知、素材库、前端。
- Owner: `/root/collab_data_impl`; branch/worktree: `main` / `D:\ZSJ-OS`。
- Base commit: `f338087e9aaa8b8d881cd0aee80427d8773ab284`; target branch: `main`。
- Goal: 修正尚未部署的协作组基础层及其初始化接入。
- Dependencies: TenantBaseDO、Core migrator；不添加依赖。
- Integration order: 基础模型 -> baseline/migration/verification -> 隔离容器验证 -> 根代理接手业务集成。
- Verification: SQL 静态检查、独立临时 MySQL 容器的 baseline/upgrade/repeatability；共享开发库保持只读。

## Delivery Entry - 2026-09-13 15:45 +08:00
- Corrected V215 collision by replacing draft with V218; source relation ID is immutable unique boundary.
- Synced `schema/core.sql` and `00-bootstrap-schema.sql`; added verification SQL for unbound, duplicate, mismatched tenant/source bindings and UTF-8 HEX.
- Removed unverified `zsjos_schema_version` write from migration after confirming migrator records `zsjos_module_schema_version`; retained baseline schema-version compatibility row only.
- Controlled Docker MySQL: fresh bootstrap completed in isolated database; V218 twice yielded idempotent checks (no duplicate source). Full app bootstrap is environment-dependent and not claimed.
- `python script/sql/mysql/tools/zsjos_db.py check` PASS; no shared database writes; no permissions changed.
- Remaining: root agent to integrate object-permission query changes and add tests; run full fresh/upgrade migrator in release environment.

## Delivery Correction - 2026-09-13 16:25 +08:00

- Branch: `main`; Worktree: `D:\ZSJ-OS`; HEAD: `f338087e9aaa8b8d881cd0aee80427d8773ab284`.
- User goal: 可复现且按源服务关系隔离的协作组基础层。
- Correction: Previous entry's schema-version sentence was inaccurate. V218 contains INSERT IGNORE into zsjos_schema_version after verifying its real DDL in 02-bootstrap-zsjos-seed.sql. No new baseline version row was added; the core migrator discovers V218 and owns the module SHA-256 registration.
- Decisions: Never-executed collab V215 replaced by V218, leaving gift V215 untouched; a non-null source relation is required for every group. Group active maps only active/paused relations; other historical states are closed. Pending acceptance remains an independent service-relation check for future consumers. Same members never imply same group. Existing mismatched bindings are retained and reported as FAIL for explicit repair.
- Changed files: V218 migration; schema/core.sql; 00-bootstrap-schema.sql; collaboration DO/Mapper; ServiceRelationDO; verify/student-collaboration-foundation.sql; tools/test_student_collaboration_foundation.py; this handoff.
- Verification evidence: python script/sql/mysql/tools/test_student_collaboration_foundation.py exited 0; real MySQL 8 container ran actual fresh bootstrap, V218, simulated pre-V218 upgrade, repeated migration, same-member two-relation isolation, distinct tenants, null members, deleted relation exclusion, table schema equality, version uniqueness, UTF-8 HEX, and invalid-binding preservation/detection. Container removed by the test helper. python script/sql/mysql/tools/zsjos_db.py check passed. Root previously compiled the initial model; later mapper/source/version additions require root integration compile.
- Dependency/integration impact: No dependency, shared database write, real permission change, Git operation, task/notification/frontend behavior change. Fresh initialization wiring uses existing core manifest automatic discovery; schema baseline equals desired schema.
- Remaining: Not a complete business isolation fix. New group lifecycle, exact permissions and object bindings remain for later phases. Full baseline-through-latest migration replay and comparison to shared development database remain unverified; this test validates actual baseline plus focused V218, not all unrelated migrations.
