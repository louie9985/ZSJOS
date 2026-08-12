# Versioned migrations

Use one immutable, forward-only SQL file per schema change. Each file must be
repeatable, explain its dependency and rollback limitation, and avoid bulk
deletion. Record successful versions in `zsjos_schema_version`.

`V020__unified_schema_migration_and_crm_tables.sql` introduces
`zsjos_module_schema_version`, the version table used by `zsjos-db`. The legacy
`zsjos_schema_version` remains for compatibility and its existing rows are mapped
to the Core module. New applied migrations are identified by module, version, and
SHA-256; editing an applied file blocks the next deployment.

Apply migrations in filename order. `V006__lead_acceptance_follow_up.sql` adds only follow-up configuration and permission metadata; it does not backfill historical leads or create tasks for existing ownership rows. `V007__split_lead_inbox_audiences.sql` hides the mixed workbench route and adds fixed submitter/owner routes without changing lead rows.
`V008__lead_follow_up_and_today_tasks.sql` adds append-only pre-qualification follow-up records, Lead summary fields and the employee task entry. It backfills only a determinable current assignment-history ID and ends with a read-only verification list for unresolved owned leads; it does not fabricate follow-ups or tasks.
`V009__online_round_robin_dispatch.sql` adds only the persistent per-sales-user automatic-intake preference used by online Redis round-robin dispatch. It defaults to paused by absence of a row and does not seed sales users or alter lead ownership.
`V010__personal_message_center.sql` adds personal all-message and unread-message menus under the existing message center and inherits only its current role grants. It does not change station-message rows, accounts, or role definitions.
`V011__configurable_business_notifications.sql` adds global template scene metadata, tenant notification rules, rendered message snapshots and controlled actions. It backfills only title/summary snapshots, preserves every original body, seeds one global pending-assignment template, and does not enable a rule for any tenant.
`V012__system_area_management.sql` creates the global `system_area` tree, inserts the 3,879 bundled `area.csv` rows with `INSERT IGNORE`, and adds area query/create/update permissions inherited from the existing area menu. Reruns add only missing seed rows and preserve administrator edits; they do not delete, overwrite, or synchronize area data.
`V013__configurable_area_other_nodes.sql` adds stable business submission codes, 34 database-managed `OTHER` nodes, and province-level direct selection for Hong Kong and Macao. Its first execution initializes ordinary sibling ordering by Chinese pinyin with `OTHER` last; the V013 version guard preserves later administrator sort and direct-selection edits on rerun, while the System service keeps `OTHER` as the final runtime option. It does not delete area rows or change existing administrative IDs.
`V016__complete_lead_notify_templates.sql` idempotently supplies one global default station-message template for each of the 20 notification scenes registered by `LeadNotifySceneProvider`. It inserts only missing active template codes, preserves administrator-created or modified templates, and does not create or enable tenant notification rules.

`V022__workbench_foundation.sql` adds the generic BusinessTask display/reminder fields, the simplified work-plan task tree, completion reports, plan summaries, versioned templates/fields and sixteen explicit configuration/execution permissions. It does not infer or add ordinary role grants; administrators assign the required permissions through System role management. The lead workstream owns `V021`, which must be integrated before `V022` in the release migration chain. The local development database may rebuild only the explicitly approved zero-row V022 work-plan tables; released environments must not rewrite this applied migration.
`V023__split_work_plan_query_permission.sql` converts the Work Plan page node into a permission-free route and adds a separate `zsjos:work-plan:query` button node. Existing roles and tenant packages that already include the route inherit the new query node, so upgrades preserve access while allowing future read-only grants without selecting every operation.

Dictionary business data does not belong here; put it under
`../dictionary-data/` and obtain explicit synchronization approval first.
V008 contains the explicitly approved system-owned defaults for follow-up method and result; the quick-note type remains empty. Generating the migration does not authorize executing it against a database.
