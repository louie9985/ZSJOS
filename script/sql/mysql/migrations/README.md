# Versioned migrations

`V045__dual_frontend_workbench_menu_components.sql` assigns Vue-loadable component metadata to the eight Workbench routes that previously used the React-only `zsjos-workbench` marker. React continues to resolve the same server-owned paths locally. The migration changes no menu identity, permission, role grant, ordering, visibility, or business data and is repeatable through stable targeted updates.

V043 is the forward repair for already-applied V041/V042 environments. It independently restores missing concurrency objects, blocks on ambiguous Person contact data, installs cross-field contact ownership and the order command ledger, and path-normalizes only legacy Lead status conditions. Run `../audit-v043-person-contacts.sql` first; V043 never merges, truncates, deletes, or auto-corrects conflicting Person rows. Its backfill scope is active, non-deleted Person phone/WeChat values only. Reruns are supported; rollback is limited to the application because dropping the additive audit tables would lose command and contact-ownership evidence.

Use one immutable, forward-only SQL file per schema change. Each file must be
repeatable, explain its dependency and rollback limitation, and avoid bulk
deletion. Record successful versions in `zsjos_schema_version`.

`V020__unified_schema_migration_and_crm_tables.sql` introduces
`zsjos_module_schema_version`, the version table used by `zsjos-db`. The legacy
`zsjos_schema_version` remains for compatibility and its existing rows are mapped
to the Core module. New applied migrations are identified by module, version, and
SHA-256; editing an applied file blocks the next deployment.

`V023__sales_order_dual_approval.sql` adds direct sales-order entry, immutable approval-round snapshots, department-pool configuration, dictionaries and permissions. It depends on V021 and V022. The migration is additive and repeatable, seeds no lead/order/payment business rows, and preserves all order and BPM audit history on rollback.

`V024__zsjos_bpm_readonly_forms.sql` adds two enabled, read-only technical BPM forms for every active tenant. The forms expose only stable appeal/order process reference variables for Admin process inspection and model publication; they do not accept business input or create ZSJOS records. Stable system-form markers make the migration repeatable without overwriting administrator changes. Disable the forms for rollback and retain all model, definition, instance, task and audit history.

`V025__sales_order_workbench_views.sql` adds the sales-side “我的订单” menu and permission, copies that menu only to roles already holding “录入成交”, snapshots the final non-approval reason on each order approval round, and adds the submitter/status list index. It deletes no business rows and does not change the dual-center approval scope.

`V026__lead_appeal_reviewer_snapshot.sql` stores the submit-time lead owner, owner department, resolved reviewer department and reviewer ID list for each new appeal round. It does not backfill historical appeals; legacy pending tasks continue to rely on their BPM assignee. The migration is additive and repeatable, and rollback disables the new snapshot behavior while retaining appeal and BPM history.

Apply migrations in filename order. `V006__lead_acceptance_follow_up.sql` adds only follow-up configuration and permission metadata; it does not backfill historical leads or create tasks for existing ownership rows. `V007__split_lead_inbox_audiences.sql` hides the mixed workbench route and adds fixed submitter/owner routes without changing lead rows.
`V008__lead_follow_up_and_today_tasks.sql` adds append-only pre-qualification follow-up records, Lead summary fields and the employee task entry. It backfills only a determinable current assignment-history ID and ends with a read-only verification list for unresolved owned leads; it does not fabricate follow-ups or tasks.
`V009__online_round_robin_dispatch.sql` adds only the persistent per-sales-user automatic-intake preference used by online Redis round-robin dispatch. It defaults to paused by absence of a row and does not seed sales users or alter lead ownership.
`V010__personal_message_center.sql` adds personal all-message and unread-message menus under the existing message center and inherits only its current role grants. It does not change station-message rows, accounts, or role definitions.
`V011__configurable_business_notifications.sql` adds global template scene metadata, tenant notification rules, rendered message snapshots and controlled actions. It backfills only title/summary snapshots, preserves every original body, seeds one global pending-assignment template, and does not enable a rule for any tenant.
`V012__system_area_management.sql` creates the global `system_area` tree, inserts the 3,879 bundled `area.csv` rows with `INSERT IGNORE`, and adds area query/create/update permissions inherited from the existing area menu. Reruns add only missing seed rows and preserve administrator edits; they do not delete, overwrite, or synchronize area data.
`V013__configurable_area_other_nodes.sql` adds stable business submission codes, 34 database-managed `OTHER` nodes, and province-level direct selection for Hong Kong and Macao. Its first execution initializes ordinary sibling ordering by Chinese pinyin with `OTHER` last; the V013 version guard preserves later administrator sort and direct-selection edits on rerun, while the System service keeps `OTHER` as the final runtime option. It does not delete area rows or change existing administrative IDs.
`V016__complete_lead_notify_templates.sql` idempotently supplies one global default station-message template for each of the 20 notification scenes registered by `LeadNotifySceneProvider`. It inserts only missing active template codes, preserves administrator-created or modified templates, and does not create or enable tenant notification rules.
`V031__timed_business_notifications.sql` adds tenant-configurable reminder stages to notification rules, task-stage idempotency storage, enabled default reminder and sales-order rules, and missing templates. It preserves business history and administrator aliases; only exact historical system-default `教务审批` labels are changed to `报名履约中心审批`. The migration is additive and must not be executed without separate environment approval.

`V042__normalize_legacy_lead_filter_status.sql` repairs active submitter/owner inbox configurations left with the retired `converted` Lead state after V037. It uses JSON paths instead of serialized-text matching, preserves immutable filter-version snapshots, and is repeatable. The application normalizes a legacy snapshot to `won` only when it is read or republished as a new version.

`V044__default_employee_avatar.sql` adds the global System-owned default employee avatar configuration after the order lifecycle V043 migration. It inserts only the missing empty system configuration, preserves any administrator-configured value on rerun, and does not update user rows or create file records. Rollback removes only the configuration after clients are prepared to use nickname initials directly.

`V046__customer_order_advanced_filter_indexes.sql` follows the menu-component V045 migration and adds only two missing secondary indexes used by customer/order advanced-filter plans. It changes no business rows, dictionaries, menus, or permissions. Each DDL statement checks `information_schema.statistics`, records both schema-version formats, and is safe to rerun. Migration execution still requires separate environment approval.

`V047__split_lead_pending_handling_stages.sql` follows V046 and upgrades only active submitter/owner schemes that still exactly match the old system defaults. It splits owned submitted leads into `first_follow_pending` and `qualification_pending`, appends an immutable published-version snapshot, preserves custom draft or published configurations, and records both schema-version formats. It is repeatable and forward-only; rollback requires publishing a replacement configuration. Do not execute it without separate environment approval.

`V048__account_personnel_partner_lifecycle.sql` widens System usernames to 32 characters, adds the ZSJOS personnel-state record and Lead submission-department snapshot, enforces one partner per bound account, and registers server-owned personnel/partner permissions without granting roles. It preserves existing accounts and business rows, is repeatable through metadata guards, and must not be executed without separate environment approval.

`V049__maintenance_mode_and_scheduler_guard.sql` seeds the database-authoritative global maintenance switch and its server-owned System administration menu without granting roles. It does not change business rows, is repeatable through stable keys and IDs, and must not be executed without separate environment approval. Rollback means setting the switch to `false` and disabling the menu rather than deleting history.

`V040__submitter_actions_and_complaints.sql` adds snapshotted submission channels for duplicate review, daily submitter urges, the independent public sales-complaint queue, server-owned menu permissions, and default in-app notifications. It grants no roles, changes no existing Lead ownership, and deletes no business rows.

`V021__lead_intended_product_active_unique_key.sql` changes only the intended-product uniqueness metadata. It adds a stored generated active product reference, removes the old tenant/lead/product unique index, and constrains only non-deleted rows; it does not delete or rewrite intended-product history.

`V022__workbench_foundation.sql` adds the generic BusinessTask display/reminder fields, the simplified work-plan task tree, completion reports, plan summaries, versioned templates/fields and sixteen explicit configuration/execution permissions. It does not infer or add ordinary role grants; administrators assign the required permissions through System role management. The lead workstream owns `V021`, which must be integrated before `V022` in the release migration chain. The local development database may rebuild only the explicitly approved zero-row V022 work-plan tables; released environments must not rewrite this applied migration.
`V033__split_work_plan_query_permission.sql` converts the Work Plan page node into a permission-free route and adds a separate `zsjos:work-plan:query` button node. It follows the already published V023-V032 sequence after resolving the former V023 collision. Existing roles and tenant packages that already include the route inherit the new query node, so upgrades preserve access while allowing future read-only grants without selecting every operation.

Dictionary business data does not belong here; put it under
`../dictionary-data/` and obtain explicit synchronization approval first.
V008 contains the explicitly approved system-owned defaults for follow-up method and result; the quick-note type remains empty. Generating the migration does not authorize executing it against a database.
### V029 - sales-order approval reviewer filter scheme

Adds the non-destructive `reviewer` audience to the shared filter-scheme table with published defaults for pending/completed approval and registration/finance task stages. It depends on the existing filter tables from V005 and the BPM sales-order process from V023. It is repeatable through `NOT EXISTS` guards and does not delete or rewrite existing schemes. Apply after V028 in migration order; rollback is limited to removing the newly inserted reviewer rows in a controlled environment.

### V032 - normalize reviewer filter option keys

Normalizes only the current reviewer scheme option keys from `registrationReview` / `financeReview` to `registration_review` / `finance_review`. BPM task-definition condition values and immutable version snapshots are not changed. It depends on V029, is repeatable through exact-fragment replacement, and should not be reversed because the legacy keys violate the shared stable-key contract.
