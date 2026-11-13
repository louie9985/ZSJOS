# V363 local migration — 2026-11-06
- Environment: local (no explicit environment marker); owner: current chat.
- Goal: synchronize V363 schema definitions and execute pending local migration.
- Non-goals: deployment, role grants, historical data changes, commit/push.
- Branch: main; worktree: D:/ZSJ-OS; base: 7a7cf5e14bef1ff193de5c4cc39af8a5255a7d5c.
- Ownership: script/sql/mysql/schema/core.sql, script/sql/mysql/00-bootstrap-schema.sql, docs/operations/database-migrations.md, this record.
- Dependencies: existing V363 migration; local yudao-mysql/ruoyi-vue-pro; configured module ledgers.
- Verification: static check, isolated V363 lifecycle tests, full fresh chain, backed-up migration and schema verification.
- Integration target/order: None.

## Delivery 2026-11-06T14:34:52.318274+08:00
- HEAD/branch/worktree unchanged from registration. Ownership released.
- Added all eight V363 CREATE TABLE definitions to desired Core and fresh baseline; operational documentation synchronized. Historical V363 unchanged.
- Static check and scoped diff check passed. Isolated V363 initial/replay/partial/prerequisite/ledger failure/recovery/index/UTF-8 tests passed. Complete fresh chain (384 pending migrations, all modules) and affected replay passed in retained v363_638052b6; fresh data-grade findings: 0.
- Full local backup: backups/mysql/pre-V363-20261106143119.sql; successful mysqldump before execution. Local ruoyi-vue-pro V363 applied under migration lock; both ledgers registered by authoritative runner; pending migrations and schema drift both empty. All eight actual table definitions match fresh database and contain zero rows; menu HEX checked.
- Invocation issues: frozen configuration corrected before writes; post-application verification SOURCE path failed in container, then recursive local SOURCE expansion completed read-only verify successfully. No repeat application needed.
- Existing data verifier reports 13 findings: V054 counters/data, V071 duplicate role-menu rows, V125 number compatibility, V157 menus, V188 pending class, V208 template, lead categories, lead filters, contact claims, registration default routes, source channels, system area ordering. These remain unresolved and are not cleared by schema verification.
- No role grants, service restart, deployment, commit or push. Remaining work: separately investigate data-grade findings; application acceptance is outside this database task.
