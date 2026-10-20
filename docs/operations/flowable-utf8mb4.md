# Flowable utf8mb4 upgrade (V331)

V331 upgrades the 41 explicitly listed Flowable engine tables from `utf8mb3_bin` to
`utf8mb4_bin`. It preserves case sensitivity, PAD SPACE behavior, column types and
lengths, defaults, comments, indexes, foreign keys and their actions, BLOBs and all
business rows. It changes no roles, permissions, tenant IDs or ASCII technical fields.
Local, test and production servers use the same numbered migration. Source delivery
and local verification do not establish that any server has applied the migration.

## Prerequisites and release order

- MySQL 8 or later, InnoDB DYNAMIC tables, `innodb_page_size >= 16384`, sufficient free
  space for table rebuilds and a maintenance window. MariaDB is explicitly rejected.
- Both existing schema ledgers and all 41 Flowable tables must exist. Source table and
  character-column collations must be `utf8mb3_bin` or `utf8mb4_bin`. Unexpected
  collations, table layouts, foreign keys crossing the reviewed scope, and estimated
  indexes above 3072 bytes fail explicitly.
- Stop **all** backend instances, Flowable executors and other writers to this
  database. The migration's named lock protects against another migration, not an
  application writing data. The standard deployment script stops its configured
  local backend; operators must coordinate any additional instances separately.
- The migration account needs existing migration facilities plus `SELECT`, `ALTER`,
  `CREATE TEMPORARY TABLES`, routine privileges and ledger `INSERT` access. The account
  must be able to inspect all foreign keys touching the target tables.

Existing server accounts must be audited before release. The repository's current
fresh-account GRANT template does not include `CREATE TEMPORARY TABLES`, `CREATE ROUTINE`
or `ALTER ROUTINE`; do not assume rebuilding the migrator changes an existing account.
Have the database administrator review the migration account's privileges for the exact
target schema. This migration neither grants privileges nor changes any real account.

Normal numeric order is after V330. No historical numbered migration is modified.
The fresh schema and desired schema include the same target charset; fresh installs
still run the numbered migration to validate and register it. Existing databases must
**not** replay bootstrap to perform this upgrade.

Deploy the source and SQL using the normal release process, with the intended server's
environment file. For the documented production layout:

```bash
cd /opt/zsjos
ENV_FILE=/opt/zsjos-runtime/.env.production bash script/shell/deploy-production.sh db-plan
ENV_FILE=/opt/zsjos-runtime/.env.production bash script/shell/deploy-production.sh deploy
```

`db-plan` rebuilds the migration tool and checks the pending chain without changing the
database. `deploy` performs preflight, stops the configured backend, builds the release,
backs up the database, applies pending migrations and verifies the result before
installing/starting the release. Check that the plan includes V331. Do not bypass
unrelated preflight failures or reconcile historical checksums to force execution.
`build`, `start` and `restart` alone do not run migrations. There is no additional
restart after a successful complete deployment; its start recreates the connection pool.

At source delivery, the shared worktree already has a separate baseline/desired-schema
mismatch in the student-information collection definitions (V323 scope). The standard
`check`, `test-fresh` and `test-upgrade` gates stop on that mismatch before executing SQL.
It is not a Flowable charset difference. Resolve it in its owning change before claiming
complete fresh-chain acceptance or expecting the standard deployment preflight to pass.
No unrelated definitions or historical checksums are changed by V331 to bypass that gate.

A reviewed, scoped maintenance execution may run V331 independently of unrelated
pending business migrations once the engine schema and both ledgers exist. Stop all
writers, take a restorable database backup and pass the exact target through an
utf8mb4 MySQL client without `--force`. The file checks structure rather than requiring
an unrelated business version. Direct SQL registers filename-based checksum evidence;
the normal runner records its authoritative file-byte checksum. Do not rewrite existing
checksums during direct replay, and do not confuse a higher maximum version with all
lower versions being installed.

## Repeatability and interrupted execution

The procedure selects actual metadata on every run, including when V331 already exists
in either ledger. Already-converted tables are skipped; remaining utf8mb3 tables are
converted. Foreign key definitions remain installed throughout. Only the migration
session temporarily disables foreign-key checks while the related columns change.
It restores that setting on success and handled errors; connection loss destroys that
session. Existing application connections are never reconfigured.

Before registering success, the procedure checks the target charsets, original column
definitions, indexes, foreign keys/actions, row counts and all affected foreign-key
relationships. The two ledger inserts share a transaction. A prerequisite, DDL,
postcondition or ledger failure cannot create a partial success registration or change
an existing marker/checksum. The version marker is not used as a skip condition.

MySQL DDL implicitly commits: an error can leave a mix of utf8mb3 and utf8mb4 tables.
Keep every writer stopped, inspect metadata and both ledgers, fix the reported cause,
then replay the **same file**. Do not drop constraints, delete data/version rows, or
blindly restart the application. A migration lock/statement timeout propagates as a
failure. Once V331 is installed by the runner, later releases normally skip it; direct
replay remains available for reviewed recovery of actual partial state.

Application rollback retains utf8mb4. Downgrading to utf8mb3 is not an automatic rollback:
supplementary characters may already exist. A pre-upgrade database restore is an
offline recovery operation with its own data-loss scope and authorization.

## Acceptance and local verification

After application of V331, run the read-only
`script/sql/mysql/verify-flowable-utf8mb4.sql` against each target. Expect 41 PASS rows,
the two-ledger PASS result, and no remaining utf8mb3 columns in the reviewed tables.
The final query reports utf8mb3 columns elsewhere without modifying them. Confirm
representative Chinese/supplementary text with `HEX()` through an utf8mb4 client, and
exercise workflow submission, task completion and history reads in the actual target
environment. Source/SQL verification does not replace authenticated workflow acceptance.

```powershell
python -B script/sql/mysql/tools/test_flowable_utf8mb4.py
```

This uses newly named, retained databases on local `yudao-mysql`, never remote servers.
It tests old-schema upgrades, the desired fresh schema, replay, metadata and complete
row-content preservation, Chinese/Emoji/supplementary HEX, enforced FKs, missing tables,
unsupported collations, partial DDL, ledger-write failure, existing markers, orphans,
concurrent migration locking and actual connection loss followed by recovery.

For an explicitly authorized local correction only, stop the local backend first and
use `--apply-local`. It backs up the 41 target tables and both ledgers under
`backups/mysql/`, compares all original row hashes/definitions, applies only V331 and
replays it once. It never applies other pending versions or discovers remote targets.
Preserve the private backup and generated verification JSON; they are not release seeds.
