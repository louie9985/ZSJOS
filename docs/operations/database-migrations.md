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

V054 adds `zsjos_lead.lead_no` and the tenant-daily `zsjos_lead_no_daily_counter`. It backfills every existing Lead by tenant and Beijing-local submission date, ordered by `submitted_at, id`, then seeds each daily counter to that date's maximum sequence. It deletes no rows and is repeatable, but assigned business numbers are durable and the migration must not be rolled back by dropping the column or counter. Apply it only while Lead writes are paused or as part of application startup before the new version accepts traffic; do not roll the application back to a version that inserts Leads without `lead_no`.

V056 must be preceded by its built-in read-only account conflict audit. The migration stops and reports only aggregate counts when it finds a tenant-local exact username duplicate, non-empty mobile duplicate, or one account's username equal to another account's mobile. Resolve those conflicts through a separately reviewed account correction before rerunning. V056 adds generated uniqueness columns, the order-number counter and in-app notification Outbox; it deletes no rows and can be rerun. Afterward, verify the generated-column index definitions, `claim_token`, V056 version row, and zero conflict checks in `verify-bootstrap.sql`.

V057 additively extends the tenant-scoped lead follow-up rule with `notification_popup_duration_minutes` (default 5) and `duplicate_auto_resolution_enabled` (default disabled). It depends on V039's `no_progress_grace_days` column and V056's migration position, deletes or rewrites no business rows, and uses metadata guards plus version upserts for repeatability. Afterward, confirm the V057 version row and both column defaults through `verify-bootstrap.sql`; application rollback leaves the additive columns intact.

V067 establishes the user-visible Lead-number contract after V066. It replaces `lead.id` with `lead.no` only in untouched system-owned Lead notification templates and changes only untouched ZSJOS appeal and order read-only BPM forms to display `leadNo`; internal `leadId` variables and relationships remain unchanged. Administrator customizations, historical messages, and started workflow instances are preserved. Afterward, use `verify-bootstrap.sql` to confirm the V067 version row, default template variables, and BPM form fields. Rollback is forward-only and must retain durable Lead business numbers.

## V071 H5 and role-permission repair

V071 depends on V063, V068, V069, and V070. Its exact target is System menu metadata plus `system_role_menu` rows for active roles in every tenant. It neither changes `system_user_role` nor any user, account, business, finance, BPM instance, or history row. Before any existing-environment execution, export a tenant/role/permission snapshot and obtain separate approval; this repository delivery only generates and validates the migration.

The migration grants required permissions first, retires non-allowlisted ZSJOS grants for the selected roles, clears the eighteen intentional zero-ZSJOS roles, then keeps one active relation for each tenant/role/permission identity. Repetition produces the same effective permission set. Rollback is not a down migration: restore only a reviewed snapshot through a later forward migration. After controlled execution, run `verify-bootstrap.sql` and require the V071 partner, finance, administrator separation, zero-role, duplicate, and permission-parent checks to return `PASS`.

The migration independently checks every required V041 column and index, creates the Person contact-claim and order-command tables, backfills only unambiguous active Person contacts, narrows the Person WeChat column after the audit, and updates only structured Lead status filter paths. It does not merge Person rows, truncate conflicting values, delete business data, or rewrite immutable filter-version snapshots. Reruns are supported after a blocked audit is corrected.

After applying V043, run `verify-bootstrap.sql` and confirm the V041 objects, command-ledger unique index, contact-claim completeness/no-orphan checks, and structured legacy-status check all return `PASS`. Rollback is application-only: retain the additive tables and columns because removing them would discard idempotency and ownership audit data.

## V073 registration fulfillment

V073 is additive and follows V072. It creates the versioned checklist, public-pool case, immutable checklist snapshot, completion facts, per-order-item service relationship and command-idempotency tables. It seeds exactly five confirmed items for active tenants, adds menu metadata, grants checklist configuration to `system_administrator`, and grants only My Students to `study_planner`. It does not backfill historical orders and does not grant public-pool handling to ordinary roles. Existing-environment execution still requires a separate approval and a preflight review of orders whose registration node already passed.

Rollback is forward-only: retire the menus/permissions and preserve business facts. Verify all V073 checks in `verify-bootstrap.sql`; do not drop tables containing completion or audit records.

## V074 registration task notifications

V074 follows V073 and adds the Chinese station-message template and enabled default in-app rule for a newly created registration fulfillment task. Delivery recipients are resolved from `zsjos:registration:query-pool` at event processing time, and the existing in-app channel emits its post-commit WebSocket refresh hint. The migration is repeatable, changes no role grants or business rows, and must not be executed without the normal existing-environment approval. Rollback disables untouched V074 rules while preserving notification history.

## V075 Lead-created default notification

V075 follows V074 and inserts an enabled `zsjos.lead.created` in-app rule only for non-deleted tenants that have no existing rule for that scene. Its recipients are the Lead source user and the actual event operator, covering the selected new-media provider and submitting salesperson for a sales self-sourced Lead. It does not overwrite enabled or disabled administrator rules, does not create historical messages, and changes no Lead, account, role, permission, or template row. Reruns are idempotent. Rollback is forward-only: disable untouched V075 rules and preserve delivered message history.

## V077 WeCom userid normalization

V077 follows V076 and repairs the optional `system_users.wecom_user_id` contract. Its preflight
groups configured values by tenant after trimming and blocks before mutation if any normalized
value would collide. After a clean preflight, it trims configured values, converts blank values
to SQL `NULL`, installs the generated `unique_wecom_user_id` column and tenant-scoped unique
index, and removes the older raw V028 index. It deletes no users and does not change account
status, roles, permissions, tokens, or notification history. Apply it before deploying application
writes that normalize the field. Rollback is forward-only and retains the normalized values and
unique index.

## V078 unified Lead management scope

V078 follows V077 and changes only Lead menu metadata and `system_role_menu`. Before applying it
to an existing environment, export active grants for `zsjos:lead:query-all`,
`zsjos:lead:query-submitted`, `zsjos:lead:query-owned`, `zsjos:lead:query`, and
`zsjos:lead-follow-up:query`, grouped by tenant and stable role code. The migration preserves the
two relation permissions, grants their holders the single `客资管理` page, logically retires
`query-all` only for `sales_manager` and `sales_specialist`, and grants follow-up query to enabled
sales managers. It changes no Lead, user, role, task, BPM, or history row. After controlled
execution, require `unified_lead_management_scope_v078` in `verify-bootstrap.sql` to return `PASS`.
Rollback is a reviewed forward migration based on the captured grant snapshot; do not broadly
restore tenant-wide Lead access.

## Optional modules

An approved optional module adds its own manifest under
`script/sql/mysql/modules/`, desired schema, migration directory starting at
`V001`, and verification SQL. Its manifest declares Core and other module
dependencies. Enable it explicitly in `ZSJOS_DB_MODULES` and build a new application and migrator
image; it never means rebuilding or clearing the existing database. Removing a
module does not delete its tables or rows.
