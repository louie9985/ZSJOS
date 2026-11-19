# V381 schema and local migration
- Environment local (no explicit marker); owner current chat; Beijing date 2026-11-19.
- Branch main; worktree D:/ZSJ-OS; base 1269093554b5d4c6621ff7a3bd028754cb975a9d.
- Goal: complete desired/fresh V381 schema and apply pending local migration, continuing authorized migration repair scope.
- Non-goals: runtime changes, grants, commit/push. Preserve concurrent edits.
- Ownership: script/sql/mysql/schema/core.sql, 00-bootstrap-schema.sql, docs/operations/database-migrations.md, this record.
- Dependencies: V380; V381 script and existing verifier. Integration target/order None.
- Verification: static, isolated lifecycle, full fresh chain, backup and target migration, schema/ledger/UTF-8 comparison.

## Delivery 2026-11-19T18:26:21.873720+08:00
- Same branch/worktree/base. Ownership released. Added product agreement table and nullable product-name snapshot to desired/fresh schemas; operation documentation synchronized. V381 SQL unchanged; prior edits preserved.
- Static/diff checks passed. Existing V381 isolated lifecycle verifier passed in v381_5f74393f fixtures. Complete fresh chain of 402 migrations across all modules passed in v381_72b40737, including final structure/ledgers/replay/UTF-8/bootstrap guards; zero fresh data-grade findings.
- Full backup completed: backups/mysql/pre-V381-20261119182336.sql. Authoritative runner applied V381 under migration lock, both ledgers registered, 208 file-integrity checks passed, pending list and drift empty. Target affected columns/defaults/collations/indexes equal fresh database; Chinese ledger HEX verified.
- Existing 14 data-grade warnings retained for separate review. No role grant, service restart, deployment, commit or push.
