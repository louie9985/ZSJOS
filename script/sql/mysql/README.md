# MySQL initialization

For all new development and production upgrades, use the repository-root
`zsjos-db` command. It owns module ordering, migration checksums, schema drift,
backup, migration locking, and verification. Direct execution remains documented
below only for controlled bootstrap troubleshooting.

```text
python script/sql/mysql/tools/zsjos_db.py check
python script/sql/mysql/tools/zsjos_db.py test-fresh
python script/sql/mysql/tools/zsjos_db.py test-upgrade
```

Production operators use the immutable migrator image through
`deploy/production/zsjos-db`; see `docs/operations/database-migrations.md`.

`bootstrap.sql` is the Core baseline only: the schema DDL plus the reviewed
System and ZSJOS seeds. It lists no versioned migration; module schemas and every
`V*.sql` file are applied by `zsjos-db migrate`, which runs the baseline first on
an empty database and then applies the pending migrations from the module
manifests, recording their checksums. Empty and non-empty environments therefore
go through the same executor, and a newly added migration never requires a second
edit here. Run the baseline with the MySQL client from the repository root so its
`SOURCE` paths resolve:

```text
mysql --default-character-set=utf8mb4 -u USER -p DATABASE < script/sql/mysql/bootstrap.sql
mysql --default-character-set=utf8mb4 -u USER -p DATABASE < script/sql/mysql/verify-bootstrap.sql
```

The baseline creates structure and reviewed baseline seeds only. It does not
drop a database, delete rows, or seed products, SKUs, leads, orders, uploads,
or business dictionary options. The admin password is stored as a BCrypt hash;
the plaintext password is intentionally not documented here.

## Production fresh-bootstrap seed policy

The production fresh baseline intentionally includes the current System tenant,
department and post tree, System roles, menus, button permissions, tenant-package
menu coverage, the initial administrator, all repository dictionary types/data,
ZSJOS runtime defaults, notification templates/default rules, and the EAM
classification tree, category fields and global asset-code rule. Existing role
menu grants are imported; employee accounts other than the initial administrator
are not.

For this production baseline set `ZSJOS_DB_MODULES=core,hrm,fms,eam,pms`. HRM and FMS
install their MySQL structure and reviewed dictionary values only (their `V001`
applies `schema/hrm.sql` and `schema/fms.sql`, then `V002` the dictionaries). EAM is
installed after Core (`migrations/eam/V001` applies `schema/eam.sql`, through the
latest available version). PMS installs its 33 project/knowledge tables in
`migrations/pms/V001__pms_schema.sql`; its menus and dictionaries are already part of
the Core seed (V189).
HRM employee/payroll/recruitment rows and FMS account-set/voucher/balance rows remain
empty; EAM asset instances, procurement, inventory,
transfers, repairs, scrap, holdings, reminders and employee tasks remain empty; PMS
project, iteration, work-item and knowledge rows remain empty.
ZSJOS Person/Lead/Partner/student-service, order/payment/refund, media/content,
feedback and generic-work-order instances also remain empty. BPM definitions are
deployed separately from the reviewed `script/bpm/manifest.json` assets; BPM
instances, tasks and history are never seeded by SQL.

Runtime logs, audit rows, notification messages and Outbox rows remain empty;
number counters are created but start on first real business use. Environment
secrets and endpoints (storage, OAuth, WeCom, SMS, payment and domains) are
provided through deployment configuration, never this repository's SQL.

For an existing environment, use `zsjos-db migrate <environment>` (or
`zsjos-db plan` first), which applies the pending files in `migrations/` in
version order after a backup and a read-only structure check. Do not re-run
historical files from `script/sql/` against an already migrated database.

`V005__lead_inbox_filter_config.sql` must be applied before deploying the backend that reads
published lead-inbox schemes. It creates configuration and history tables, seeds two published
schemes per active tenant, and adds permission metadata; it does not update or delete lead rows.

`V007__split_lead_inbox_audiences.sql` must be applied before deploying the workbench with fixed
inbox routes. It hides the legacy mixed route and derives submitted/owned route grants from existing
submit, claim, accept, and query-all permissions; it does not change accounts or lead data.

`V010__personal_message_center.sql` must be applied before exposing the administration frontend's
personal message-center routes. It adds “全部消息” and “未读消息” beneath the existing message-center
menu and grants them only to roles that already hold that parent menu; it does not change message rows.

`V012__system_area_management.sql` must be applied before deploying database-backed area APIs. It
creates the global `system_area` tree, inserts all 3,879 bundled snapshot rows only when missing, and
adds area operation permissions. It does not overwrite administrator changes or delete area rows.

`V013__configurable_area_other_nodes.sql` adds stable submission codes, database-managed `OTHER`
nodes, and province-level direct selection for Hong Kong and Macao. On its first execution it also
initializes every sibling group in Chinese pinyin order with `OTHER` fixed last. Its version guard
prevents a rerun from overwriting later administrator changes to area ordering or direct-selection
flags; the System service also enforces `OTHER` as the final runtime option. It does not delete area rows.

`V016__complete_lead_notify_templates.sql` must be applied before administrators configure rules for
all registered lead notification scenes. It inserts only missing global template codes and does not
overwrite templates or create, enable, or modify tenant notification rules.

To compare two environments without changing either database, use
`compare-schema.ps1`. It requires the standard MySQL client tools and reads the
password from the client configuration or `MYSQL_PWD`; do not put credentials
in the command line or repository files.

The schema baseline was exported from the local database structure and excludes
the `yudao_demo*` tables and all table data. Future structure changes must be
added as numbered migrations and reflected in the baseline generator/review.

`V147__workbench_navigation_layout.sql` is reserved for the tenant-owned Workbench
navigation projection and has a hard dependency on `V146` in both schema-version
registries. It creates two tenant tables, the Admin-only configuration page, and three
operation permissions. It appends those menu IDs only to packages that already contain
System Management and creates no role grant or published layout. Because `V139-V146` are
owned by concurrent workstreams and are not yet present here, final bootstrap ordering and
the V147 verification row must be integrated only after those files land.

## Restoring a full database dump locally

A full logical backup produced by the ZSJOS dump tooling (`zsjos_full_<timestamp>.tar.gz`)
is restored with `script/sql/mysql/tools/restore_full_dump.py`. It targets the local
Docker MySQL container only; there is no remote-host option by design.

```text
python script/sql/mysql/tools/restore_full_dump.py --archive <path>/zsjos_full_<stamp>.tar.gz
python script/sql/mysql/tools/restore_full_dump.py --archive <path>/zsjos_full_<stamp>.tar.gz --apply
python script/sql/mysql/tools/restore_full_dump.py --archive <path>/zsjos_full_<stamp>.tar.gz --apply --reset-password
```

The archive's own database name is `zsjos`, while the local application and
`script/docker/docker-compose.yml` connect to `ruoyi-vue-pro`. The script therefore rewrites
only the two standalone `CREATE DATABASE` / `USE` lines to the `--database` value; table names
and data are untouched. A run without `--apply` verifies checksums, extracts, rewrites, and
reports the target database state without writing anything.

`--apply` first dumps the current target database to `backups/mysql/` (git-ignored), then
imports. The import contains `DROP TABLE` / `CREATE TABLE`, so it replaces every same-named
table. `V*.sql` migration history and `zsjos_module_schema_version` come from the backup, which
may be older than the checked-out code; re-run `zsjos-db migrate` afterwards when the backup
trails the current code baseline.

`--reset-password` sets `system_users.password` and `zsjos_partner_account.password` for all
non-deleted rows to one BCrypt hash of `--password` (default `Test@123456`). The hash uses the
`$2a$` prefix and cost 10, matching Spring Security's `BCryptPasswordEncoder`. Tokens in
`system_oauth2_*` are not cleared, so an already-issued token stays valid until it expires.
