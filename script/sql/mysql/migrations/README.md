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

`V088__lead_source_provider_identity.sql` adds nullable Lead fields for the explicit new-media provider and a recording marker used by newly created sales-self leads. It does not backfill historical rows because their legacy `source_user_id` cannot distinguish a selected provider from the submitting salesperson. The marker keeps historical display and filtering behavior unchanged; new rows use the provider-aware submitter projection. The migration is additive, repeatable, and forward-only.

`V044__default_employee_avatar.sql` adds the global System-owned default employee avatar configuration after the order lifecycle V043 migration. It inserts only the missing empty system configuration, preserves any administrator-configured value on rerun, and does not update user rows or create file records. Rollback removes only the configuration after clients are prepared to use nickname initials directly.

`V046__customer_order_advanced_filter_indexes.sql` follows the menu-component V045 migration and adds only two missing secondary indexes used by customer/order advanced-filter plans. It changes no business rows, dictionaries, menus, or permissions. Each DDL statement checks `information_schema.statistics`, records both schema-version formats, and is safe to rerun. Migration execution still requires separate environment approval.

`V047__split_lead_pending_handling_stages.sql` follows V046 and upgrades only active submitter/owner schemes that still exactly match the old system defaults. It splits owned submitted leads into `first_follow_pending` and `qualification_pending`, appends an immutable published-version snapshot, preserves custom draft or published configurations, and records both schema-version formats. It is repeatable and forward-only; rollback requires publishing a replacement configuration. Do not execute it without separate environment approval.

`V048__account_personnel_partner_lifecycle.sql` widens System usernames to 32 characters, adds the ZSJOS personnel-state record and Lead submission-department snapshot, enforces one partner per bound account, and registers server-owned personnel/partner permissions without granting roles. It preserves existing accounts and business rows, is repeatable through metadata guards, and must not be executed without separate environment approval.

`V049__maintenance_mode_and_scheduler_guard.sql` seeds the database-authoritative global maintenance switch and its server-owned System administration menu without granting roles. It does not change business rows, is repeatable through stable keys and IDs, and must not be executed without separate environment approval. Rollback means setting the switch to `false` and disabling the menu rather than deleting history.

`V050__readonly_impersonation_and_audit_catalog.sql` adds empty read-only impersonation session and dedicated request-audit tables plus server-owned permissions without granting roles. It is additive and repeatable; rollback disables the feature while retaining audit history. Do not execute it without separate environment approval.

`V040__submitter_actions_and_complaints.sql` adds snapshotted submission channels for duplicate review, daily submitter urges, the independent public sales-complaint queue, server-owned menu permissions, and default in-app notifications. It grants no roles, changes no existing Lead ownership, and deletes no business rows.

`V021__lead_intended_product_active_unique_key.sql` changes only the intended-product uniqueness metadata. It adds a stored generated active product reference, removes the old tenant/lead/product unique index, and constrains only non-deleted rows; it does not delete or rewrite intended-product history.

`V022__workbench_foundation.sql` adds the generic BusinessTask display/reminder fields, the simplified work-plan task tree, completion reports, plan summaries, versioned templates/fields and sixteen explicit configuration/execution permissions. It does not infer or add ordinary role grants; administrators assign the required permissions through System role management. The lead workstream owns `V021`, which must be integrated before `V022` in the release migration chain. The local development database may rebuild only the explicitly approved zero-row V022 work-plan tables; released environments must not rewrite this applied migration.
`V033__split_work_plan_query_permission.sql` converts the Work Plan page node into a permission-free route and adds a separate `zsjos:work-plan:query` button node. It follows the already published V023-V032 sequence after resolving the former V023 collision. Existing roles and tenant packages that already include the route inherit the new query node, so upgrades preserve access while allowing future read-only grants without selecting every operation.

### V085 - business notification customer-name removal

Replaces structured customer/student name variables in ZSJOS Lead, order and registration notification templates with `leadNo` or the relevant business number, and rewrites matching rendered message snapshots through their tenant-scoped business relationships. It updates administrator templates as well as system defaults because the identifier rule is mandatory. The migration aborts before mutation when a targeted structured snapshot cannot be parsed or resolved, never guesses names in arbitrary text, is repeatable and forward-only, and requires a reviewed backup before execution.

### V087 - business notification identifier repair

V087 is the forward-only repair for already-applied V085 environments; V085 remains immutable and V086 is reserved for Lead-detail tab permissions. It covers logically deleted notification messages and related business records, repairs remaining structured customer-name keys through tenant-scoped Lead/order/registration relations, backfills the business-number key that V085 omitted from registration snapshots, and rebuilds template parameter arrays without duplicates. Historical message parameters that are not JSON objects are isolated and left unchanged unless their raw value still contains a forbidden customer-name key, which blocks the migration. Valid structured rows still fail closed on relations required for mutation, missing user-visible identifiers, non-string legacy values, and conflicting stored identifiers. Template and business-number comparisons use binary UTF-8 semantics, and the temporary repair table explicitly matches the notification-table collation instead of inheriting the connection default. It never uses an internal Lead ID as a visible identifier, guesses a removed name, or overwrites an unverifiable rendered body. Real execution requires a reviewed backup and a separate approval.

Dictionary business data does not belong here; put it under
`../dictionary-data/` and obtain explicit synchronization approval first.
V008 contains the explicitly approved system-owned defaults for follow-up method and result; the quick-note type remains empty. Generating the migration does not authorize executing it against a database.
### V029 - sales-order approval reviewer filter scheme

Adds the non-destructive `reviewer` audience to the shared filter-scheme table with published defaults for pending/completed approval and registration/finance task stages. It depends on the existing filter tables from V005 and the BPM sales-order process from V023. It is repeatable through `NOT EXISTS` guards and does not delete or rewrite existing schemes. Apply after V028 in migration order; rollback is limited to removing the newly inserted reviewer rows in a controlled environment.

### V032 - normalize reviewer filter option keys

V051 additively introduces product/category cashback rules, an empty cashback table, observation configuration, and ungranted permissions. Populated financial history is retained on rollback.

V052 adds empty partner-card, withdrawal and withdrawal-item tables, BPM references, active-cashback uniqueness, offline payout evidence, default rules and ungranted permissions. Deploy BPM key `zsjos_partner_withdrawal` separately; rollback disables the process and menus while retaining financial history.

V053 adds the ungranted `zsjos:withdrawal:finance-query` child permission required for finance full-card queries. It does not grant the permission to any role and is repeatable through `INSERT IGNORE` and schema-version upserts.

V054 adds tenant-daily Lead business numbers and a transactional daily counter. Existing Leads are backfilled in stable `submitted_at, id` order per tenant and Beijing-local date; no business rows are deleted. The suffix is fixed at four digits and wraps from 9999 to 0001. If one tenant already has more than 9999 Leads in the same second, tenant-scoped uniqueness intentionally blocks the migration for manual review. Rollback must retain assigned numbers.

V055 adds optional direct-supervisor confirmation to new sales-order approval rounds, its audit table, and an ungranted Workbench menu permission. It depends on V054 and a separately published sign-enabled BPM definition. It is repeatable and does not modify existing orders, tasks, roles, or users. Rollback must preserve confirmation audit history.

V056 adds the Beijing-local sales-order number counter, transfer-reviewer snapshots, tenant-local binary username and non-empty mobile uniqueness, and the in-app notification Outbox. Before DDL it reports only counts for duplicate usernames, duplicate mobiles, and cross username/mobile conflicts, then blocks when any count is non-zero. It deletes no business rows. `unique_username` is a stored `utf8mb4_bin` generated column; the Outbox claim token prevents an expired worker from updating a newer claim. Reruns are supported. Rollback must preserve allocated order numbers, notification audit rows, and account uniqueness until a separately reviewed compatibility release exists.

V057 adds two tenant-scoped columns to the existing lead follow-up rule: notification popup duration (default 5 minutes) and automatic duplicate resolution (default disabled). It changes no lead, review, account, or message rows. Metadata guards and version upserts make reruns safe; rollback should leave the columns in place while older application versions ignore them.

V058 is the explicitly approved HRM/FMS import repair. After an external backup, it physically removes incompatible HRM tenant/demo rows for tenants 0, 1, and 121 (preserving the tenant-0 default salary-slip template) and FMS business rows for tenants 1 and 121. It preserves all System identities and organization data, ZSJOS business rows, global FMS subject/report templates, and HRM salary-option templates. It installs the reviewed upstream HRM/FMS menus in the reserved local 600000 namespace, synchronizes their dictionaries, and grants the new menus only to tenant 1 `super_admin`. Destructive work runs only before V058 is recorded; recovery requires restoring the pre-migration backup. Fresh bootstrap does not source V058 because HRM/FMS schema installation remains a separate module prerequisite and bootstrap must not perform repair cleanup.

V059 is the forward repair for V047 on MySQL, where assigning a JSON-array user variable through `JSON_SET` serialized the pending-stage `options` as a JSON string. It preserves the malformed version-2 snapshots for audit, appends corrected version-3 snapshots, and updates only active submitter/owner schemes still at the exact V047 state. Corrected current draft and published options are real JSON arrays. Reruns do not create later versions; rollback publishes a later configuration rather than rewriting immutable snapshots.

V060 reconciles migration metadata after an existing environment has independently verified and repaired effective V001-V059 behavior. It copies missing stable `Vnnn` legacy records into the Core module registry, records V060 in both registries, and changes no schema, menu, permission, configuration, or business rows. Apply it only after missing older migrations have been executed or their effects have been explicitly verified; otherwise it would incorrectly mark behavior as installed. Registry reconciliation is forward-only because deleting records can cause old migrations to run again.

V061 repairs the V048/V055 menu-ID collision without rewriting either applied migration. Personnel retains IDs 6850-6855, while the previously skipped `zsjos:sales-order:supervisor-confirm` menu is installed at reserved ID 6856. The migration creates no role grants and changes no order, personnel, account, configuration, or business rows; administrators must assign the supervisor permission explicitly. Stable ID and permission guards make reruns safe. Rollback should retain the menu when any administrator-created grant references it.

V062 registers the ungranted `zsjos:export:finance-order` child permission under the existing asynchronous export menu. It changes no orders, export tasks, roles, or historical files. The migration takes an advisory lock, validates the parent menu and exact permission identity, inserts only when absent, verifies the row before recording both schema-version registries, and releases the lock on success or failure. Stable ID and permission guards make reruns safe; rollback should retain the permission whenever an administrator-created grant references it.

V063 creates the tenant-scoped `part_time_partner` role and app-api permissions, backfills enabled/disabled partners, and keeps finance review ungranted for manual assignment. It fills missing first-level category defaults with 10.00 and 0.1000; lead source/category options remain administrator-maintained dictionaries.

### V122 - repair partner Lead source accounts and cashback defaults

Repairs active partner Leads whose `source_user_id` is null only when the same tenant and partner have exactly one enabled non-deleted partner account; ambiguous or invalid rows are left for manual review. It fills missing first-level product-category cashback defaults with 10.00 and 0.1000 without overwriting configured values. The migration is non-destructive, tenant-scoped, repeatable, records both schema registries, and must be applied through the normal controlled migration process. Rollback is forward-only; retain repaired provenance and financial snapshots.

### V123 - retire student group/handoff delivery stage

Moves only service relations whose current projection is `group_handoff` to `supervision`, clears the retired current-stage fact payload, and increments their optimistic-lock version. Immutable student-contact records, including historical stage snapshots, remain unchanged. The migration deletes no rows, is repeatable because migrated rows no longer match, and records both schema registries. Apply V123 before deploying the matching application release; recovery requires a reviewed forward migration and may use immutable contact records as audit evidence rather than inventing current-stage facts.

### V124 - repair planner notification student contract

Updates only the migration-owned planner-assignment template from the old Lead wording to `student.name` and `student.no`, preserving administrator-edited templates and delivered message snapshots. It is repeatable, forward-only, and records both schema registries.

### V125 - tenant-daily student business numbers

Adds the `zsjos_person_no_daily_counter` table used by newly created Person records to allocate
`XYyyyyMMddHHmmss` plus a four-digit tenant-daily sequence in Beijing time. Existing `person_no`
values, including legacy `P + UUID` values, are preserved and are not backfilled. The migration
changes no Person or relationship rows, is repeatable, and records both schema registries. Apply
it through the reviewed migration process before deploying the matching application release;
real database execution remains separately confirmed.

V068 repairs the V063 partner-permission menu-ID collision without rewriting the applied migration. It logically removes only accidental `zsjos:work-plan*` grants from `part_time_partner`, resolves the eleven H5 permissions by permission code, creates the missing self-profile permission when necessary, and grants no finance-review capability. Reruns preserve administrator-managed menus and business data; rollback should restore grants only from a reviewed pre-migration role snapshot.

V069 logically removes the invalid `ZsjosPartnerPortal` admin menu created by V063/V068. The partner portal is served by the app-api/H5 surface and has no matching admin Vue component; retaining the malformed `partner-portal` route causes Vue Router 5 navigation to fail after login. The migration changes only the menu and any role-menu rows referencing that route, preserves all partner permissions and business data, and is repeatable. Rollback is forward-only and must retain the retired menu if a replacement administrator-owned route references it.

V064 attempted to add the missing `bpm:model:import` button under the standard BPM model menu without granting it to any role. Its fixed menu ID collides with the existing work-plan template publish permission, so applied environments require the V070 forward repair; do not rewrite applied V064.

V070 is the forward repair for applied V064 environments. V064's fixed menu ID `6913` was already owned by the work-plan template publish permission, so `INSERT IGNORE` could record V064 without creating `bpm:model:import`. V070 resolves the standard BPM model parent, checks the stable permission code, lets MySQL allocate the menu ID, and grants no role. Reruns preserve an existing active permission; rollback should retain it while any administrator-created role grant references it.

V071 is the forward-only H5 and all-role ZSJOS permission repair. It resolves grants by tenant-scoped stable role code and stable permission code, restores one matching logically deleted grant before inserting a missing grant, retires duplicate active role/permission relations, removes `zsjos:lead:query` and every other unapproved ZSJOS permission from `part_time_partner`, and makes both finance roles share the reviewed eleven-permission finance allowlist. Administrator withdrawal remains read-only; eighteen roles with no implemented ZSJOS domain intentionally receive zero ZSJOS permissions. Orphaned H5 permission buttons become non-routable root metadata without recreating an admin page. It changes no users, account-role assignments, BPM instances, or business rows. Reruns are idempotent; recovery requires a reviewed pre-migration role-menu snapshot and a later forward migration. Generating or testing V071 does not authorize applying it to an existing database.

V072 introduces the tenant-scoped `zsjos_partner_account` identity and `PARTNER(3)` message ownership. It blocks before mutation when an enabled/disabled Partner lacks a unique mobile, a unique valid System binding, or a BCrypt password hash. During an approved maintenance window it copies those hashes, backfills explicit Partner ownership on complaints, appeals, and urges, migrates Partner messages, revokes the legacy `part_time_partner` role relation, disables the legacy System accounts, and removes their ADMIN access/refresh tokens. Converted Partners and historical Flowable snapshots remain unchanged. Recovery requires the pre-migration backup; generating or testing V072 does not authorize applying it.

V065 adds the tenant-scoped lead `last_activity_at` projection and compound cursor index used by employee inboxes. It backfills the maximum persisted lead, assignment, follow-up, appeal, and order activity without deleting rows. The migration is repeatable; rollback should retain the additive column and index while cursor clients are deployed.

V066 adds nine stage-specific Chinese templates for first follow-up, next follow-up, and qualification reminders. It repoints only untouched V031 default rules whose rule and current template still retain the original migration creator/updater markers; administrator-created or edited rules and templates are preserved. It changes no timing-stage protocol values, reminder timing, recipients, history, or permissions. Reruns add no duplicate template codes and do not revisit rules already migrated to V066. Existing rendered messages are not rewritten; rollback should preserve administrator mappings and may restore only untouched defaults.

V067 establishes `leadNo` as the user-visible Lead identifier. It updates only untouched system-owned Lead notification templates from `lead.id` to `lead.no`, updates untouched ZSJOS appeal and order read-only BPM forms to show `leadNo`, and clarifies the Lead primary-key comment as 内部客资ID. Administrator-edited templates and forms, historical messages, started workflow instances, and all internal `leadId` relationships are preserved. Reruns are idempotent; rollback must use a forward migration and retain assigned business numbers.

Normalizes only the current reviewer scheme option keys from `registrationReview` / `financeReview` to `registration_review` / `finance_review`. BPM task-definition condition values and immutable version snapshots are not changed. It depends on V029, is repeatable through exact-fragment replacement, and should not be reversed because the legacy keys violate the shared stable-key contract.
### V073 registration fulfillment and students

Adds the versioned registration checklist template, public-pool case and checklist snapshot,
completion facts, per-order-item student service relations, and idempotent command audit.
It seeds only the five confirmed checklist items for active tenants and the three feature menu
surfaces; it does not backfill historical orders or execute permission changes outside the
administrator configuration and `study_planner` read-only student menu grants. Apply after V072.
The migration is additive and repeatable. Rollback is forward-only: retire menu permissions and
preserve business facts; do not drop these tables in a live environment.

### V074 registration task notifications

Adds one system-owned Chinese station-message template and one enabled default in-app rule per
active tenant for newly created registration fulfillment tasks. Recipients are resolved at delivery
time from the stable public-pool query permission; no role name or department is inferred. The
in-app delivery keeps the existing post-commit WebSocket hint. The migration is additive and
repeatable and changes no task, order, permission, role, or historical message rows. Rollback is
forward-only: disable untouched V074 rules and preserve delivered message history.

### V075 Lead-created default notification

Adds one enabled default in-app rule per non-deleted tenant only when the tenant has no existing
`zsjos.lead.created` rule. The rule resolves both the Lead source user and the actual operator, so a
sales self-sourced Lead with a selected new-media provider notifies both employees; identical users
are deduplicated by the notification recipient set. Existing enabled or disabled administrator
rules are preserved, and no historical Lead message is generated. The migration is repeatable and
forward-only; rollback disables untouched V075 rules while retaining delivered message history.

### V076 unified sales-order approval entry

Moves ordinary review and sales-supervisor confirmation permissions under one server-owned
`成交订单审批` page. Existing ordinary-review and supervisor grants are preserved, and enabled
`sales_manager` roles receive the page plus supervisor-confirm action. No order, BPM task, approval
round, or confirmation history is rewritten. The migration is repeatable and forward-only; execute
it only after reviewing the role impact for the target environment.

### V077 WeCom userid normalization

Normalizes `system_users.wecom_user_id` by trimming configured values and converting blank
values to `NULL`, then replaces the V028 raw unique index with a tenant-scoped unique index
over a generated nonblank value. A read-only preflight blocks before mutation when trimming
would make two configured userids collide in one tenant; it reports only the aggregate conflict
count. The migration deletes no users and changes no accounts, roles, permissions, tokens, or
notification history. It is repeatable and forward-only; retain normalized values and the index
on application rollback. Generating and testing V077 does not authorize applying it to an
existing database.

### V078 unified Lead management scope

Makes `客资管理` the single employee Lead route and converts the former submitted/owned pages
into hidden relation-scope permissions. Their existing role grants are preserved and receive the
shared page. `sales_manager` and `sales_specialist` lose only active `query-all` grants, while
enabled sales managers receive follow-up query permission. List and object reads use current
managed departments and child departments; writes remain action-scoped. No Lead, task, user,
role, or history row is deleted. Reruns are idempotent. Recovery is forward-only and requires a
reviewed role-menu snapshot before restoring any tenant-wide grant.

### V121 retire standalone Lead qualification exception menu

Hides and clears the route/component metadata for menu `6800` (`异常客资`) so the
Workbench has only the unified `客资管理` page. Qualification query/manage permissions
and the backend disposition APIs remain active; no Lead or role data is changed. The
migration is repeatable and forward-only.

### V080 Lead-source provider notification

Splits only untouched enabled V075 Lead-created defaults into an operator-only submission-success
rule and a dedicated `new_media_provider` rule. The provider rule uses the global
`ZSJOS_LEAD_SOURCE_LINKED` template and renders
`{{operator.name}}销售提交客资{{lead.no}}（客资编号），已关联你为客资来源。`.
Runtime context resolves that role only for a sales self-sourced Lead with an explicitly selected
provider. Disabled, edited, or administrator-created rules are preserved, historical messages are
not generated, and reruns do not duplicate the template or rule. Apply after V079; rollback is
forward-only and retains delivered history.

### V081 Employee birthday care

Adds the HRM birthday-care configuration menu, in-app notification template/rule, and a repeatable ten-minute `employeeBirthdayCareJob` definition. The job is disabled by business configuration until an administrator selects recipient departments and enables the feature. It does not seed departments, employees, accounts, or permissions.

### V082 Registration planner notifications

Adds the system-owned in-app template and default rule for assigning a registration case to a study planner. The rule sends only to the assigned planner; the message uses the user-visible `leadNo`. Runtime resolution for the existing registration-task scene now intersects the public-pool permission with the configured registration approval department subtree, so administrators outside that center are excluded. No permissions, departments, accounts, business rows, or historical messages are changed. The migration is repeatable and forward-only; preserve delivered history when disabling a rule.

### V083 Registration routes and checklist attachments

Adds versioned department route options, per-case department/assignee snapshots, attachment checklist metadata and a director-assignment notification. Default routes resolve the exact unique active department names `学生服务与交付中心` and `新媒体与客资中心` once and persist their IDs; verification fails on missing or ambiguous mappings. Pending/processing cases receive route snapshots, completed historical cases are unchanged, and enabled `content_director` roles receive only the existing My Students menu. Apply after V082 and before V084. The migration is repeatable and forward-only.

### V084 Employee birthday-care menu repair

Repairs V081's menu IDs, which overlap the V058 FMS currency and finance-parameter menus. It creates the birthday-care page and two permissions with IDs `602100-602102`, preserves all FMS rows, and grants the repaired menu only to enabled `super_admin` roles. Existing ordinary-role permissions are not changed. The migration is repeatable and forward-only.

### V086 Lead detail tab permissions

Adds independent read permissions for follow-up, appeal, complaint, and order tabs under the existing Lead management menu. Existing effective permissions are mapped to the corresponding new read permission so deployment does not hide records that a role can currently read; administrators can then adjust each tab through standard System role management. The migration changes menu metadata and role-menu relations only, is repeatable, and does not execute business-data or permission cleanup.

### V089 Registration attachment idempotency result

Adds nullable `zsjos_registration_command.result_attachment_id` so an attachment-upload replay can
return only the exact attachment created by the original command. Historical command rows are not
guessed or backfilled; a replay without a valid exact result fails with a stable application error.
The migration is additive, repeatable, and forward-only. It changes no attachment, Infra file,
permission, account, or historical command row and must not be executed against an existing database
without the normal separate approval.

### V090 Lead complaint result notifications

Adds separate system-owned in-app result templates and complainant-targeted rules for founded and
unfounded Lead complaint decisions. The runtime resolves the immutable internal complainant user or
Partner subject stored on the complaint record; it does not infer the recipient from a current role,
department, or Lead relationship. Existing founded notifications to the sales owner and direct leader
remain unchanged. The migration inserts at most two rules per active tenant, preserves administrator
rules and historical messages, is repeatable, and is forward-only. Applying V090 to an existing
database remains a separate deployment action.

### V091 Lead flow history permission

Adds the independent `zsjos:lead-detail:flow-read` button permission under Lead management and
grants it only to enabled `sales_manager` roles in each tenant on the first successful V091 install.
Once the V091 version marker exists, reruns do not restore a role-menu relation removed by an
administrator. Runtime access remains cumulative
with the existing Lead object permission; the grant does not widen Lead visibility. The migration
changes no Lead, business-event, assignment-history, aging-pool, account, or historical row, is
repeatable, and is forward-only. Existing-environment execution remains a separate approved action.

### V092 subordinate sales one-click pause

Adds `zsjos:subordinate-sales:pause-all` under the existing subordinate-sales menu and grants it
only to enabled `sales_manager` roles on the first successful V092 install. Reruns preserve any
later administrator removal. The runtime command derives its entire target set from the current
manager hierarchy and stable `sales_specialist` post, including disabled accounts; the migration
itself changes no account, dispatch preference, Lead, presence, or audit data. It is additive,
repeatable, and forward-only. Existing-environment execution remains a separate approved action.

### V101 student basic-information permission

Adds the `zsjos:student:update-basic-info` button permission under My Students and grants it to
enabled `system_administrator` and `study_planner` roles only on the first successful V101 install.
Reruns preserve later administrator removal of a role grant. The runtime command remains cumulative
with owner-only student-service object authorization and accepted-service state. The migration changes
no Person, Lead, order, contact, task, event, or historical snapshot row. It is additive, repeatable,
forward-only, and must not be applied to an existing environment without separate approval.

### V102 new-media business notifications

Adds system-owned in-app templates and, only when a scene has no existing rule, one enabled default
rule per active tenant for the confirmed new-media pending-action, return, and final-result nodes.
Recipients come from persisted object responsibility or Partner binding snapshots supplied by the
business event; the migration does not infer recipients from role names. Message copy displays the
business number snapshot rather than an internal ID. Reruns do not duplicate templates or rules,
administrator-created scene rules are preserved, and historical messages are not backfilled. Recovery
is forward-only: disable V102-created rules and retain delivered messages and outbox history.

### V103 new-media operator director-menu repair

Removes the content-director-only `/zsjos/media-students` menu (`7022`) from every
`new_media_operator` role after V100 granted it as part of the operator menu set. The migration leaves
the dedicated `content_director` grant and the planner-owned `/zsjos/my-students` menu unchanged. It is
repeatable, changes no business or audit rows, and records both schema-version markers. Recovery is an
explicit re-grant through System role management if the business permission contract later changes.

### V104 new-media business dictionaries

Adds the confirmed system dictionary types `zsjos_account_platform` (抖音、小红书、视频号) and
`zsjos_content_class` (首批、重点、异常、日常). Inserts are guarded by type/value and preserve
existing administrator entries. The migration changes dictionary metadata and options only; it does
not alter media accounts, content, snapshots, notifications, or permissions. It is repeatable and
forward-only; historical records retain their stored label snapshots.

### V105 production-ticket delivery time compatibility

Makes `zsjos_production_ticket.expected_delivered_at` nullable to match the current
Workbench form, which collects a single delivery deadline. The migration is forward-only,
repeatable, and changes no existing business rows; tightening the column again requires
an explicit backfill and separate schema decision.

### V107 new-media role operation permissions

Adds missing operation permissions to the existing tenant-1 study planner, content director,
new-media operator, and filming editor roles. The migration is additive and repeatable: it
does not remove existing role grants, create accounts, or change business rows. Service-level
object authorization and responsibility checks remain authoritative.

### V109 local media BPM publisher permission

Adds the existing BPM model create/import/update/deploy buttons to the tenant-1
`dept_manager` role so the confirmed local media BPM assets can be published through the
standard BPM public API. It is additive and repeatable and does not create accounts,
remove permissions, or deploy a model by itself.

### V112 registration planner notification template

Repairs only the migration-owned `ZSJOS_REGISTRATION_PLANNER_ASSIGNED` template so its
title, summary, content and parameter list use the registered `lead.no` variable. It
preserves administrator-owned edits and delivered message snapshots, and is repeatable
and forward-only.

### V113 media student center consolidation

Moves third-party account, content and positioning operation permissions beneath the media-student
page, retires their standalone page menus, grants the scoped student page to the media operator,
and adds versioned account-field configuration plus media-student talk records. The migration is
repeatable and preserves all business rows and stable permission strings.
### V114 Student delivery stages

Adds the normal learning-planner delivery-stage projection and immutable structured stage facts to student service relations and contact records. The repeatable backfill uses only authoritative history: completed services become `completed`, successful study-plan records advance to `group_handoff`, successful first-contact records advance to `study_plan`, and accepted services without later evidence start at `first_contact`. It does not infer stages from names, remarks, or UI labels. Apply after V113; do not execute against an existing environment without the reviewed migration procedure.

V114 runs every schema, backfill, menu, grant and version statement inside one stored-procedure call.
It creates or canonically restores the V073-owned My Students page `73020`, creates or restores button
`73428`, and grants both menus to every enabled `study_planner`; having no such role is valid and
leaves the menus ready for later administrator assignment. Repeated execution preserves business
facts and does not duplicate grants. Only an invalid `/zsjos` root, a fixed menu ID owned by another
permission, or an active duplicate permission blocks execution. MySQL cannot execute `SIGNAL` through
`PREPARE`, so those true-conflict failures use direct procedural `SIGNAL` statements.

If an earlier manual V114 v4/v5 attempt continued after a statement error, first inspect both version
tables, the five affected columns, menus `73020`/`73428`, and role grants. A database that already
records that earlier V114 must use a separately reviewed forward migration rather than replacing its
executed file/checksum. Production environments where V114 has never run use the reviewed v6 file.

### V115 Generic work-order core

Adds tenant-scoped configurable work-order scenes, JSON field definitions and immutable
work-order status history. It supports direct assignment and public claim-pool records;
it seeds no scenes, users, files or business rows. Apply after V114; database execution
against an existing environment requires the normal reviewed migration procedure.
### V116__study_planner_repurchase_permissions.sql

Adds only the dedicated `zsjos:sales-order:student-repurchase` button and the existing personal-order page grant to `study_planner`, and adds the nullable order request-fingerprint column used for exact future repurchase replay. It does not grant generic order creation or external historical-customer repurchase, does not backfill invented historical fingerprints, and changes no business rows. Apply after V115 through the reviewed migration process; do not execute automatically in shared environments.

V116 v5 runs its guarded column addition, canonical menu recovery, grants and version writes inside
one stored-procedure call. It restores migration-owned `73020`, V025/quick-init-owned `6813`, and
V116-owned `73440` records when missing, soft-deleted or metadata-drifted. Zero enabled
`study_planner` roles is valid; existing enabled roles receive exactly the personal-order page and
student-repurchase button without duplicate grants. A foreign fixed-ID owner, an active duplicate
permission, or an invalid `/zsjos` root remains a blocking ownership conflict. Environments that
already recorded V116 v4 retain that executed migration/checksum and use a forward migration for any
future repair; production environments where V116 has never run use the reviewed v5 file.

### V117 Lead category label snapshot

Adds nullable `lead_category_label_snapshot` columns to Lead and duplicate-review submission
persistence. New selections retain the administrator-owned display label that existed when the
business choice was made. The migration does not invent or backfill labels for historical Leads or
pending reviews. It is repeatable through `information_schema` guards and records both schema-version
markers. Apply after V116 through the reviewed migration process; database execution remains a
separately confirmed operation.

### V119 Workbench relative child menu paths

Normalizes page-menu paths directly beneath the active `/zsjos` Workbench root from
duplicated absolute values such as `/zsjos/my-students` to relative child values such
as `my-students`. The resolved public browser URL remains `/zsjos/my-students` because
the client joins the parent and child paths. The migration changes no menu IDs,
permissions, role grants, users, or business rows. It blocks on an ambiguous Workbench
root or a conflicting active sibling path and is otherwise repeatable. Apply after V117.

### V120 Restore operator media-student menu grant

Re-grants the shared `/zsjos/media-students` page (`7022`,
`zsjos:media-student:query-my`) to enabled `new_media_operator` roles. V103
removed that grant when the page was temporarily director-only; V113 established
the shared director/operator student-center contract. The migration changes only
role-menu metadata, is repeatable, and does not broaden the backend object scope.
Apply after V119; no business rows are changed.

### V126 Student service forms, exam dates and menu

V126 adds tenant-scoped dynamic form JSON to the published student-contact configuration,
immutable student form snapshot storage, and additive exam-date/reminder state on service
relations, plus the server-owned `/zsjos/business-form-config` menu and permission metadata.
It does not invent historical exam dates or execute reminders during migration. The migration
is repeatable and must be applied through the normal controlled migration process; no database
execution is performed by the application change.

### V127 Repair student business form configuration menu grant

V127 repairs the V126 Admin menu metadata and grants the business-form configuration page to
enabled `system_administrator` and `super_admin` roles in each tenant. It is repeatable,
forward-only, and changes no business data. Apply after V126 through the controlled migration
process.

### V128 Media director student flow foundation

V128 adds the student-level director stage, interview appointment, immutable dynamic-form
snapshot columns, and the student-level operator owner to `zsjos_service_relation`. Each of
the six columns is guarded independently so a partially upgraded database can be rerun safely.
It registers director and operator positioning-card permissions and adjusts only the confirmed
default role grants: directors receive precheck/interview/assignment and director card actions;
operators receive confirmation/rejection and lose director card commands. The migration is
repeatable and forward-only; it does not backfill invented historical workflow states, assign
operators, or execute notifications. Apply after V127 through the controlled migration process.
Rollback is limited to a separately reviewed forward migration; no destructive rollback is
provided.

### V129 Director form dictionaries

V129 seeds the System dictionary types and initial values used by the Demo-based director
interview and account-positioning forms. It inserts only missing dictionary types/items and
preserves administrator edits to existing labels, ordering and enabled status. It records the
`zsjos_schema_version` marker and changes no student form snapshots or business rows. Apply
after V128 through the controlled migration process. Rollback is limited to a separately
reviewed forward migration because existing tenants may already use these dictionary values.

### V130 Configurable director forms and menu wiring

V130 depends on the V129 System dictionaries. It adds tenant-owned interview/positioning
template and version tables, director SLA configuration, immutable positioning snapshot
columns, the default Demo templates, and the server-owned `编导业务配置` menu tree. It grants
the configuration directory, pages, and buttons only to `system_administrator` and
`super_admin`. Re-execution only fills missing schema or seed rows and does not overwrite
administrator-edited dictionaries, existing templates, drafts, or business snapshots.
Rollback requires a reviewed forward migration; published versions and snapshot columns must
not be dropped while referenced.

### V131 Director and operator action permission repair

V131 is a forward-only repair for databases where the V128 schema exists but its
media-student action menus or role relations were not installed. It adds only missing action
menus and grants: `content_director` receives precheck, interview, operator assignment and its
approved positioning actions; `new_media_operator` receives positioning query, confirm and
reject. Confirmed legacy positioning write grants are soft-deleted only for
`new_media_operator`. All five buttons are repaired under the shared media-student page `7022`;
the shared positioning-card query button is added there as well, and all six action IDs are appended only to tenant packages already containing that page. The script is repeatable, does not change business rows, and rollback
also restores the `content_director_operator` relationship-scene definition for each tenant
without inventing any source/target user relation. Candidate operators remain entirely controlled
through the relationship-maintenance page. Rollback requires restoring the affected
`system_role_menu` relations from an environment-specific audit; scene removal is allowed only
after confirming that no configured relationship still references it.

### V132 Workbench menu rendering mode

V132 adds the server-owned `workbench_render_mode` metadata column to `system_menu`. The change is
additive and repeatable, defaults existing rows to `native`, and does not alter menu permissions or
business rows. Rollback requires a reviewed forward repair because removing the column would discard
administrator-selected rendering metadata.

### V133 director interview form presentation

V133 follows V132 in the baseline and normalizes active director interview templates, removes the retired
six-dimension field, adds the required dictionary choices, and guards the director draft-version columns.
It preserves published history and service-relation snapshots, serializes execution with a named lock, and
is repeatable. Apply it only through the controlled migration sequence; rollback requires a reviewed forward
template version because rewriting published historical snapshots is prohibited.

### V134 positioning confirmation handoff

Adds immutable positioning submission field snapshots, digest-only long-lived single-decision links, and
the operator button permission for generating or regenerating a student confirmation URL. It backfills only
non-deleted legacy `student_confirm` cards that contain the required relationship and ownership identifiers;
their unknown historical submit time remains null. Successful compatibility rows advance to
`student_link_pending`. The migration deletes no business data and cannot restore the retired Partner-H5
confirmation entry after a new link or decision. Review the target and in-flight count before separately
approved execution, then run `verify-bootstrap.sql`.

### V135 applied director and positioning schema repair

Repairs databases that applied earlier revisions of V133 or V134 before the director draft-version columns
and complete positioning submission section snapshots were added. It uses guarded DDL to add the two
service-relation version columns and six nullable submission snapshot columns. Existing compatibility
submissions are repaired only when they remain owned by V134 and have a matching active positioning card;
only missing values are copied from that authoritative source. The migration does not rewrite V133/V134
markers, statuses, permissions, links, or non-null snapshot values. It is repeatable and forward-only.
Dropping the columns after runtime use would lose version and snapshot facts. Apply V135 after V134, then
run `verify-bootstrap.sql` and require the V128, V134, and V135 checks to pass.

### V136 sales order team management

V136 adds the server-owned `团队订单` page and `zsjos:sales-order:query-team` permission, granting it only
to existing active `sales_manager` role rows. The page stores the Workbench child path
`sales-orders/team`; both Workbench and Admin consume the same team-order read APIs. The backend resolves
the current user's department subtree at query time and filters by submitter, so no member snapshot or
role-name authorization rule is stored. The migration is repeatable, non-destructive, and changes no order
or approval rows. Apply after V135 through the controlled migration process. Rollback requires a reviewed
forward repair and must preserve administrator-created menu grants.

### V137 Workbench menu rendering-mode collision repair

V137 is required for environments that already recorded the former local V132/V133/V134 migrations before
the remote Workbench rendering migration claimed V132. It preserves every applied marker and repeatably
adds `system_menu.workbench_render_mode` only when absent, then records its own V137 legacy and module
markers. Fresh environments also run V137 as a guarded no-op after V132-V136. It changes no menu grants or
business rows; rollback requires a reviewed forward repair because removing the column loses configured
rendering metadata.

### V138 HRM/FMS/EAM Workbench admin embed mode

V138 updates the active HRM (`601476`), FMS (`601894`), and EAM (`7100`) Workbench
menu trees. Their roots and every descendant page or directory (`type` 1/2) use
`admin_embed`, so Vue Admin content is rendered in the Workbench. Button permission
rows (`type` 3), menu grants, and business data are intentionally unchanged. The
migration is repeatable and forward-only; fresh bootstrap applies the same update
after the System menu seed.

### V139 Lead supervisor actions and canonical public-sea route

Restores the server-owned public-sea page path to the Workbench canonical relative child path
`lead-aging-pool` and adds five operation permissions for supervisor restore, transfer, recycle,
claim-pool release, and public-sea release. The first installation grants them only to enabled
`sales_manager` roles; reruns preserve administrator-managed grants. It changes no Lead, assignment,
opportunity, public-sea, account, or audit rows and does not rewrite V039.
The migration runs its repair and both version writes inside one stored-procedure call. Its temporary
permission table explicitly uses `utf8mb4_unicode_ci`, matching `system_menu.permission`; a statement
failure rolls back the V139 DML and prevents either V139 version marker from being written.

### V140 Command idempotency, positioning expiry, and menu identity repair

Creates the tenant/operator scoped supervisor-command ledger, adds and backfills the positioning
confirmation-link expiry boundary, expands the student decision comment to 2000 characters, and
restores menu `73460` to student business-form configuration while moving the interview-template
page and its buttons/grants to stable menu `73483`. Apply after V139. The migration is repeatable;
it also restores the oldest compatible soft-deleted V139 supervisor permission identity, preserves grants,
and leaves one active definition for each permission. It does not execute the separate V131
permission-revocation or V135 inferred-snapshot cleanup.
The migration runs its schema/menu repair and version writes inside one stored-procedure call. The
temporary permission table explicitly matches the System menu collation, and the role-menu move uses a
legal MySQL self-join. A failed call may retain earlier additive DDL because MySQL commits DDL, but it does
not record V140; rerun the corrected migration only when V140 was never recorded.

Use `../audit/V131_permission_grant_audit.sql` and `../audit/V135_snapshot_cleanup_audit.sql` for
read-only scope review and recovery export. Any resulting permission revocation or snapshot update
requires an independently confirmed tenant/row list and is intentionally not part of bootstrap.

### V141 Media-screen daily snapshot

Creates the empty tenant-scoped daily member snapshot table used by the public new-media contribution
screen history API. The unique tenant/date/member key makes the daily freeze idempotent; department and
member names are frozen with that day's counts. Apply after V140. The migration does not backfill or
invent historical data, change Lead rows, or seed business options. Once snapshots have been written,
rollback requires a reviewed export-and-forward-repair plan because deleting the table loses frozen history.

### V142 Partial V139/V140 execution repair

Repairs databases where a statement-batch client continued after the former V139 collation failure or
V140 target-table failure and nevertheless recorded their version markers. It repeatably ensures the V140
command/positioning schema, restores menu `73460` and interview menu `73483`, moves legacy interview grants,
canonicalizes the five supervisor permissions without losing effective duplicate grants, and completes the
initial `sales_manager` grants. It blocks on conflicting menu ownership and writes V142 only after the single
stored-procedure call completes. Do not delete V139/V140 version rows or rerun those recorded files; apply
V142 after V141 through the controlled migration process. No Lead, positioning submission snapshot, command
record, account, or other business row is deleted. Rollback is forward-only and later permission changes must
use reviewed System configuration or another forward migration.

### V143 Subordinate Partner ownership

V143 adds the tenant-scoped one-current-owner relation between a Partner and a System employee, immutable
assignment audit rows, and nullable submission-time employee ID/name snapshots on Partner Leads. It creates the
server-owned `subordinate-partners` page permission and the independent Partner assignment button permission.
Only the assignment button is initially granted to enabled `system_administrator` roles; ordinary subordinate
read access remains administrator-configured. It creates no ownership rows, does not backfill historical Leads,
and is repeatable. Recovery disables menus or grants while retaining relationship, audit, and snapshot facts.

### V144 Remove new-media Student Operations

Retires the new-media Student Operations domains: exception tickets, cooperation assessments, and
graduation applications. In execution order it deletes graduation notification messages, rules and
templates; deletes `media-graduation` business events; deletes Student Operations role-menu grants and
menu/button definitions; drops the three business tables; removes obsolete V106/V108/V118 markers; and
records V144. The separately owned student-contact extension table, permissions, process key and history
are explicitly outside this migration.

V144 is repeatable through stable scene, aggregate and permission identifiers plus guarded table drops.
It is intentionally destructive and forward-only. Take and verify a full database backup before applying
it; recovery requires restoring that pre-execution backup. Flowable model, definition, deployment and
runtime/history cleanup must be performed through the BPM service/repository boundary before V144, not by
adding scattered `ACT_*` deletes to this SQL file.

### V145 Production-ticket dispatch and public pool

Adds account-scoped production-ticket dispatch snapshots, rejection and the filming-editor claim pool.
It adds a tenant/operator command ledger for replay-safe create, assignment-rejection and claim commands;
the create key remains unique across logical deletion. Fixed permission IDs are guarded by bidirectional
ID/permission ownership checks before any upsert. It inherits grants from existing production-ticket
permissions and seeds the corresponding notification scenes. Apply after V144; no historical assigned
ticket is rewritten. A partial rerun refuses to rebuild the create-key index when duplicate tenant keys
exist, so conflicts must be audited and corrected through a separately reviewed data repair.

### V146 Media-account maintenance and calendar

Adds nullable dictionary-backed current account-maintenance snapshots, the immutable per-account revision
table, and the top-level `/calendar` directory with relative child path `overview`. Existing `s_stage`
values receive only a current label snapshot; no maintenance revision is invented and the original stage
log remains intact. The `zsjos:media-account:maintenance` operation is a server-owned button under the active
media-student page `7022` (`/zsjos/media-students`), never under the retired standalone account page `6970`.
Calendar and maintenance grants are inherited from the effective account query, edit, and query-all grants
instead of role names. The former stage-advance/rollback menu permissions are disabled
and their grants retired; the compatibility endpoints return the explicit retired-operation error to users
with the new maintenance permission. V146 is repeatable and forward-only. Recovery disables the new menus
and notification rule while retaining account snapshots and revision history.
### V147 Workbench navigation layout

V147 depends on V146 and adds `system_workbench_layout` for the current draft and
published pointer plus `system_workbench_layout_version` for immutable publish history.
Both tables are tenant-scoped. A tenant has one global scope and at most one scope per
role; enabled published role priorities are unique within the tenant. The migration does
not write a layout row, publish a default layout, or alter any existing application-menu
parent, page URL, component, permission, role assignment, or business row.

Menu IDs `79900-79903` create `系统管理 -> Workbench 菜单编排` and the query, update,
and publish permissions. The page uses `workbench_render_mode=admin_only`. Existing active
tenant packages receive these IDs only when they already contain the System Management
root menu `1`; no role receives an automatic grant. Fixed-ID, permission, and route
ownership conflicts fail before schema or menu writes. Table creation is additive, menu
and package writes are repeatable, and both schema-version markers are recorded. Rollback
requires a reviewed forward migration because published snapshots are permanent; do not
drop either table while any tenant layout or history exists.

`V139-V147` are now present in the integrated migration chain. V149 follows V147; V148 is
intentionally absent because it is owned elsewhere. V149 must remain the only migration
number used by the feedback-management workspace and must be reviewed through the normal
controlled migration process before execution.

### V149 Feedback management

Adds the tenant-scoped requirement, BUG and technical-support feedback workspace. It extends
generic work orders with `business_type`, creates feedback/reply/approval-round/survey/config
tables and the daily number counter, and seeds default forms, the approved support dictionary,
menu/button permissions, and notification templates/rules. Existing work-order rows remain
`GENERIC`; feedback rows are marked `FEEDBACK` and excluded by generic work-order APIs.

V149 is additive, repeatable and guarded against fixed-ID, route, permission, form-marker and
dictionary conflicts. It records both schema-version registries and does not delete business
data. Form definitions, field values, dictionary labels, people, attachments, processing results
and approval rounds are snapshotted for history. Execute only after V147, run
`verify-bootstrap.sql`, and retain the additive schema on application rollback. BPM import,
publish, enablement and dispatcher configuration are manual release steps; no startup auto-deploy
is introduced.
