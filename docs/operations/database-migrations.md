# Database migration operations

## Contract

ZSJ-OS uses one MySQL schema for the enabled runtime modules. The desired Core
structure is `script/sql/mysql/schema/core.sql`; reviewed, forward-only changes are
stored under `script/sql/mysql/migrations/`. MyBatis data objects are mapping
evidence, not a complete DDL source.

Do not run generated differences directly against production. `make` generates a
candidate migration for review. Production runs only migrations packaged in the
release migrator image.

## Developer workflow

1. Change `script/sql/mysql/schema/core.sql` to the intended final structure.
2. Generate the next migration:

   ```powershell
   .\zsjos-db.ps1 make core add_lead_score
   ```

   On Linux or macOS, use `bash ./zsjos-db make core add_lead_score`.

3. Review the generated SQL. Add non-structural work such as controlled data
   backfills, System menu metadata, or dictionary types explicitly. Document
   dependencies, data scope, repeatability, and rollback limitations.
4. Update the applicable verification SQL and baseline version registration.
5. Run:

   ```powershell
   .\zsjos-db.ps1 check
   python script/sql/mysql/tools/zsjos_db.py test-fresh
   python script/sql/mysql/tools/zsjos_db.py test-upgrade
   python script/sql/mysql/tools/zsjos_db.py test-guardrails
   ```

`make` prefers a locally installed Atlas CLI. If Atlas is absent and Docker is
available, it uses the pinned `arigaio/atlas:0.36.2` image. Atlas only generates
candidate structural DDL; it never infers renames, business data, permissions, or
destructive production actions.

## Production preparation

Copy `deploy/production/.env.example` to `deploy/production/.env`. Create the three
referenced files under `deploy/production/secrets/` with different URL-safe values
of at least 24 characters. These files and the local `.env` are ignored by Git.

Build the release-specific migrator image from the reviewed commit:

```bash
docker compose --env-file deploy/production/.env \
  -f deploy/production/compose.database.yml \
  --profile tools build db-migrator
```

For a real release, set `ZSJOS_DB_MIGRATOR_IMAGE` and
`ZSJOS_DB_RELEASE_VERSION` to the immutable image tag and release identifier.
Set `ZSJOS_DB_MODULES=core` to the exact comma-separated module set packaged in the
application release. Adding an optional module's files does not enable it.
The application must use `ZSJOS_DB_APP_USER`; only the migrator receives the DDL
credential.

## Fresh installation

```bash
docker compose --env-file deploy/production/.env \
  -f deploy/production/compose.database.yml up -d mysql redis
bash deploy/production/zsjos-db plan production
bash deploy/production/zsjos-db migrate production
bash deploy/production/zsjos-db verify production
```

An empty schema uses `bootstrap.sql`. A non-empty schema is never bootstrapped;
only pending versioned migrations are applied.

## Every production database update

Run these commands from the reviewed release directory:

```bash
bash deploy/production/zsjos-db plan production
bash deploy/production/zsjos-db migrate production
bash deploy/production/zsjos-db verify production
```

`plan` is read-only. `migrate` obtains the migration lock, creates a timestamped
logical backup, validates applied checksums, executes pending migrations in module
order, records each successful version, and runs verification. Any unexplained
schema drift, checksum mismatch, SQL failure, or verification failure blocks the
application release.

Database DDL is not automatically rolled back. If application startup fails after
a compatible migration, roll back the application image and keep the schema.
Destructive cleanup requires a separate approved release and recovery plan.

## V043 order lifecycle repair

V043 is a forward repair for environments where V041 or V042 may have been only partially applied. Do not edit or re-checksum V041/V042. Before migration, run `script/sql/mysql/audit-v043-person-contacts.sql`; any blank contact, WeChat value longer than 64 characters, or cross-field contact mapped to multiple Person rows blocks the release and requires an independently reviewed data correction.

The migration independently checks every required V041 column and index, creates the Person contact-claim and order-command tables, backfills only unambiguous active Person contacts, narrows the Person WeChat column after the audit, and updates only structured Lead status filter paths. It does not merge Person rows, truncate conflicting values, delete business data, or rewrite immutable filter-version snapshots. Reruns are supported after a blocked audit is corrected.

After applying V043, run `verify-bootstrap.sql` and confirm the V041 objects, command-ledger unique index, contact-claim completeness/no-orphan checks, and structured legacy-status check all return `PASS`. Rollback is application-only: retain the additive tables and columns because removing them would discard idempotency and ownership audit data.

## Optional modules

An approved optional module adds its own manifest under
`script/sql/mysql/modules/`, desired schema, migration directory starting at
`V001`, and verification SQL. Its manifest declares Core and other module
dependencies. Enable it explicitly in `ZSJOS_DB_MODULES` and build a new application and migrator
image; it never means rebuilding or clearing the existing database. Removing a
module does not delete its tables or rows.
