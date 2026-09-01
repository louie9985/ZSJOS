# Workstream Registration

- Workstream ID: `main-hrm-fms-schema`
- Goal: 为生产启用的 HRM/FMS 模块补齐 MySQL 表结构、必要字典与数据库模块化初始化契约，不导入历史业务数据。
- Non-goals: 不执行生产数据库变更；不执行 V058 数据清理；不导入员工、工资、凭证、账套等业务实例；不切换分支、提交或推送。
- Branch: `main`
- Worktree: `D:\ZSJ-OS`
- Base commit: `01a66c0f33ed546124eab29fce57d50db298340a`
- Target branch: `main`
- Ownership scope: `script/sql/mysql/modules/hrm.json`; `script/sql/mysql/modules/fms.json`; `script/sql/mysql/schema/hrm.sql`; `script/sql/mysql/schema/fms.sql`; module migrations and verification SQL; bootstrap/module documentation; this handoff file.
- Owner: Codex `/root`
- Dependencies: Core schema and System dictionary/menu tables; historical FMS MySQL dump supplied by user; repository HRM/FMS test DDL where historical HRM file is unavailable in the current temp path.
- Integration order: classify source SQL -> convert/validate structure -> add manifests and module migrations -> add verification/docs -> run static and controlled checks -> append delivery entry.
- Verification plan: `python script/sql/mysql/tools/zsjos_db.py check`; SQL structural scans; fresh/upgrade execution when MySQL tooling/database is available; no production execution.

## Delivery Entry - 2026-09-01 00:00:00 +08:00

- Workstream ID: `main-hrm-fms-schema`
- Branch: `main`
- Worktree: `D:\ZSJ-OS`
- HEAD commit: `01a66c0f33ed546124eab29fce57d50db298340a` (uncommitted worktree)
- User goal: 生产环境需要 HRM 和 FMS，仅初始化表结构及相关字典值，不导入历史业务数据。
- Key decisions: HRM/FMS 作为依赖 Core 的独立数据库模块；从仓库 H2 集成测试 DDL 转换 MySQL 结构；从现有 V058 提取 HRM/FMS 字典类型和值并去除 V058 条件；不执行 V058 的物理数据清理，不导入用户提供 dump 中的业务 INSERT 数据。
- Execution or analysis result: 新增 HRM 50 表、FMS 30 表的结构 schema、V001 结构迁移、V002 字典迁移、verify SQL、模块 manifest；生产模块配置更新为 `core,hrm,fms,eam`。用户提供的 HRM 临时文件在当前环境不可读取，HRM 结构以仓库测试 DDL 为来源；用户提供的 FMS dump 已确认包含 30 张表及业务数据，未直接导入。
- Changed files: `deploy/production/.env.example`; `deploy/production/.env.production`; `deploy/production/compose.database.yml`; `docs/operations/database-migrations.md`; `script/sql/mysql/README.md`; `script/sql/mysql/modules/README.md`; `script/sql/mysql/modules/hrm.json`; `script/sql/mysql/modules/fms.json`; `script/sql/mysql/schema/hrm.sql`; `script/sql/mysql/schema/fms.sql`; `script/sql/mysql/migrations/hrm/V001__hrm_schema.sql`; `script/sql/mysql/migrations/hrm/V002__hrm_dictionary.sql`; `script/sql/mysql/migrations/fms/V001__fms_schema.sql`; `script/sql/mysql/migrations/fms/V002__fms_dictionary.sql`; `script/sql/mysql/verify/hrm.sql`; `script/sql/mysql/verify/fms.sql`; `handoff/main-hrm-fms-schema.md`。
- Verification evidence: HRM schema contains 50 `CREATE TABLE`; FMS schema contains 30 `CREATE TABLE`; new HRM/FMS SQL contains no DELETE/DROP/TRUNCATE/UPDATE; Python helper scripts compile before removal; `git diff --check` showed only existing line-ending warnings. `zsjos_db.py check` remains blocked by pre-existing duplicate Core migration marker V162, unrelated to this change. Controlled MySQL fresh/upgrade execution was not run because Docker/MySQL availability was not established.
- Dependency or integration impact: No new Maven/npm dependency, no production database execution, no branch/commit/push, and no external account or permission changes. Production migrator must use `ZSJOS_DB_MODULES=core,hrm,fms,eam` and apply HRM/FMS V001 then V002 after Core.
- Remaining work: Before release, run controlled MySQL fresh/upgrade verification and compare against production schema; confirm the missing HRM user-provided SQL if exact historical MySQL compatibility is required; separately review and authorize V058 only if its tenant data cleanup is intended.

## Verification Update - 2026-09-01

- Controlled MySQL 8 container verification passed after converting oversized JSON/text columns to `TEXT` and moving the FMS generated column into the table definition for MySQL compatibility.
- Result: HRM 50 tables created; FMS 30 tables created; no business rows were loaded.
- Repository `zsjos_db.py check` and `test-fresh` remain blocked before execution by the pre-existing duplicate Core migration marker V162; this is unrelated to HRM/FMS schema execution.
