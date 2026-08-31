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

`bootstrap.sql` is the fresh-environment entry point. Run it with the MySQL
client from the repository root so its `SOURCE` paths resolve:

```text
mysql --default-character-set=utf8mb4 -u USER -p DATABASE < script/sql/mysql/bootstrap.sql
mysql --default-character-set=utf8mb4 -u USER -p DATABASE < script/sql/mysql/verify-bootstrap.sql
```

The bootstrap creates structure and reviewed baseline seeds only. It does not
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

For this production baseline set `ZSJOS_DB_MODULES=core,eam`. EAM is installed after Core (`schema/eam.sql`, then `migrations/eam/V001` through
the latest available version). EAM asset instances, procurement, inventory,
transfers, repairs, scrap, holdings, reminders and employee tasks remain empty.
ZSJOS Person/Lead/Partner/student-service, order/payment/refund, media/content,
feedback and generic-work-order instances also remain empty. BPM definitions are
deployed separately from the reviewed `script/bpm/manifest.json` assets; BPM
instances, tasks and history are never seeded by SQL.

Runtime logs, audit rows, notification messages and Outbox rows remain empty;
number counters are created but start on first real business use. Environment
secrets and endpoints (storage, OAuth, WeCom, SMS, payment and domains) are
provided through deployment configuration, never this repository's SQL.

For an existing environment, apply files in `migrations/` in version order,
after a backup and a read-only structure check. Do not re-run historical files
from `script/sql/` against an already migrated database.

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
