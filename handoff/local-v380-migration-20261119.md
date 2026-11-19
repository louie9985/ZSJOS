# V380 schema and migration
- Environment local (no explicit marker); Beijing date 2026-11-19; owner current chat.
- Branch main; worktree D:/ZSJ-OS; base 1269093554b5d4c6621ff7a3bd028754cb975a9d.
- Goal: synchronize V380 desired/fresh schema, verify and migrate local ruoyi-vue-pro.
- Non-goals: restart/deploy, grants, commit/push; preserve existing changes.
- Ownership: script/sql/mysql/schema/core.sql, 00-bootstrap-schema.sql, tools/test_enrollment_category_agreement.py; docs/operations/database-migrations.md; this record.
- Dependencies: V378 starter and V380 script. Integration target/order None.
- Verification: static, isolated lifecycle, full fresh chain, backed-up migration, schema/ledger/UTF-8 comparison.

- Scope update: include V380 migration. Replace CREATE TABLE LIKE with explicit equivalent columns/PK so pending-drift parser recognizes all created fields; target has no V380 marker. Preserve applied artifact checksums elsewhere, no reconciliation.

## Delivery 2026-11-19T17:54:57.263435+08:00
- Branch/worktree/base unchanged. Existing edits preserved; ownership released.
- Added category agreement table and nullable agreement_category_name to desired/fresh schemas. Expanded V380 CREATE TABLE LIKE to equivalent explicit definition because preflight could not associate cloned column names with pending migration; no integrity bypass or checksum reconciliation.
- Verification: static and diff checks passed; isolated initial/replay/missing prerequisite/partial DDL/invalid column/ledger failure rollback/recovery/data preservation/UTF-8 passed (v380_50780821). Repeated complete fresh chain after final migration edit: 401 migrations, every module, final schema/ledgers/replay/bootstrap guards passed; zero fresh data findings (v380_873e0dc0).
- Backed up local database to backups/mysql/pre-V380-20261119175145.sql; dump completed successfully. Authoritative runner under migration lock applied V380, both ledgers registered; 207 file-integrity checks passed, no pending migration or unexpected drift. Target affected columns/indexes/defaults/collations match fresh schema; Chinese ledger HEX checked.
- Existing backend remained running: additive empty table/nullable field only, no data rewrite; metadata lock wait bounded to 30 seconds. No service operation, grants, deploy, commit or push.
- Existing 14 data-grade warnings remain for separate investigation; database structural success is not business-data acceptance.
