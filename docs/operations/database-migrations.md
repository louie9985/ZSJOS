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

`bootstrap.sql` is reserved for a fresh empty environment. It is not an upgrade
mechanism and must never be run against an existing database. Releases containing
the study-planner repurchase implementation require V116 to be applied and verified
first; an environment without `zsjos_order.submission_request_fingerprint` must not
start that application version. Historical orders are not backfilled with fabricated
request fingerprints.

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

V083 adds checklist attachment flags, versioned route options, case route snapshots and attachment metadata. It resolves the exact, unique active System department names `学生服务与交付中心` and `新媒体与客资中心` once, stores their IDs, and exposes a verification failure when either name is missing or ambiguous. It snapshots routes only for active/pending cases, grants only My Students to enabled `content_director` roles, and adds the director-assignment in-app notification. It does not execute file deletion, backfill completed historical cases, or grant public-pool permissions. Apply after V082 and before V084; execution against an existing environment requires separate approval.

Rollback is forward-only: retire the menus/permissions and preserve business facts. Verify all V073 checks in `verify-bootstrap.sql`; do not drop tables containing completion or audit records.

## V074 registration task notifications

V074 follows V073 and adds the Chinese station-message template and enabled default in-app rule for a newly created registration fulfillment task. Delivery recipients are resolved from `zsjos:registration:query-pool` at event processing time, and the existing in-app channel emits its post-commit WebSocket refresh hint. The migration is repeatable, changes no role grants or business rows, and must not be executed without the normal existing-environment approval. Rollback disables untouched V074 rules while preserving notification history.

## V112 registration planner notification template

V112 repairs only the migration-owned `ZSJOS_REGISTRATION_PLANNER_ASSIGNED` template so its
title, summary, content and parameter list use the registered `lead.no` variable. It preserves
administrator-owned edits and does not rewrite delivered message snapshots. The migration is
repeatable and forward-only; verify the exact template contract with `verify-bootstrap.sql`.

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

## V080 Lead-source provider notification

V080 follows V079 and adds the global `ZSJOS_LEAD_SOURCE_LINKED` template. It changes only an
enabled, otherwise untouched V075 rule from `submitter + operator` to `operator`, then creates one
enabled `new_media_provider` rule for the same tenant. The provider message is exactly
`{{operator.name}}销售提交客资{{lead.no}}（客资编号），已关联你为客资来源。`.
Runtime delivery resolves this role only for sales self-sourced submissions with an explicitly
selected provider. The migration preserves disabled, edited, and administrator-created rules,
does not create historical messages, and is repeatable. After controlled execution, verify both
rule contracts, the exact template variables and content, both V080 version registries, and no
historical Outbox/message growth. Rollback is forward-only and retains delivered history.

## V086 Lead detail tab permissions

V086 follows V085 and adds four System button permissions under Lead management for follow-up,
appeal, complaint, and order history. It restores or inserts role-menu relations by mapping the
previous effective read permissions, so deployment does not unexpectedly hide an existing tab;
administrators may then configure each permission independently. The migration changes no Lead,
order, student, task, BPM, account, or history row. It is repeatable, records both schema-version
registries, and must pass the V086 checks in `verify-bootstrap.sql`. Existing-environment execution
still requires the normal separate approval; rollback is forward-only through role permission
configuration and preserves business history.

## V087 business notification identifier repair

V087 follows the already-applied V085 notification rewrite and the occupied V086 Lead-detail
permission migration. It is a forward repair: V085 and V086 remain immutable. The migration
includes logically deleted notification snapshots and resolves identifiers through tenant-matched
Lead, order and registration relations even when the related business row is logically deleted.
It repairs residual structured name keys, backfills missing registration `lead.no` or `order.no`
parameters, and rebuilds template parameter arrays without duplicates. Legacy message snapshots
whose parameters are not JSON objects are isolated and left unchanged; if such a malformed value
still contains a forbidden customer-name key, V087 blocks instead of guessing. For valid JSON
objects, unresolved relations needed for mutation, identifier gaps, non-string legacy values and
conflicting stored numbers block before V087 is recorded. V087 never guesses a name after V085
removed its structured source and does not replace an unverifiable historical body. Before
controlled execution, retain a database backup and the
actual applied V085 artifact/checksum; a pre-V085 backup is required to prove already-rewritten
bodies contain no value whose structured source was removed. Rollback is forward-only and must
preserve notification history. V087 compares template snapshots and business numbers as binary
UTF-8 values and gives its temporary repair table the notification-table collation, so execution
does not depend on the database connection's default collation.

## V089 registration attachment idempotency result

V089 follows V088 and adds only nullable `zsjos_registration_command.result_attachment_id`. New
attachment uploads persist their exact business attachment result on the command ledger; historical
commands are deliberately not inferred from file name or size. The migration is guarded and
repeatable, deletes or rewrites no rows, and leaves Infra storage unchanged. Before deployment, run
`verify-collaboration-pool-overlap.sql` as a separate read-only preflight and investigate every
returned Lead; application code fails closed on overlaps and V089 does not clean them. After a
separately approved execution, require the V089 and collaboration-pool checks in
`verify-bootstrap.sql` to return `PASS`. Rollback is application-only and retains the nullable column.

## V090 Lead complaint result notifications

V090 follows V089 and adds two global templates plus at most two enabled complaint-result rules per
non-deleted tenant. The founded and unfounded rules both use the persisted `complainant` recipient
role, the `in_app` channel, and the `business_detail` action; runtime recipient resolution uses the
complaint record's employee or partner subject. Templates render `lead.no` and the handler opinion
and expose no internal Lead ID as a customer-facing identifier. The migration changes no complaint,
Lead, account, existing rule, Outbox, or historical message row. It is repeatable through stable
template codes and tenant/scene/channel/action/recipient guards, records both version registries,
and is wired after V089 in bootstrap order. Existing-environment execution requires separate
approval and must then pass the V090 checks in `verify-bootstrap.sql`. Recovery is forward-only:
disable untouched V090 rules while retaining templates and delivered history.

## V091 Lead flow-history permission

V091 follows V090 and adds the System button permission `zsjos:lead-detail:flow-read` under Lead
management. On the first successful installation only, it grants the permission to enabled roles
whose stable code is `sales_manager`; it does not grant `sales_specialist`, submitters, or ordinary
sales. The runtime permission remains cumulative with the existing Lead object reader, so the role
grant cannot expand the set of visible Leads. A successful V091 version marker prevents later
bootstrap reruns from restoring a role-menu relation that an administrator manually removed.

The migration changes no Lead, business event, assignment history, aging-pool event, account, or
historical row. It is additive, transaction-wrapped, repeatable, and forward-only, and it is wired
after V090 in fresh bootstrap order. Existing-environment execution still requires separate
approval. After controlled execution, run `verify-bootstrap.sql` to verify the version markers and
permission definition; role assignment is intentionally not a permanent verifier invariant because
administrator changes are authoritative. Recovery is performed through System role permission
configuration while retaining the permission definition and all business history.

## V092 subordinate sales one-click pause permission

V092 follows V091 and adds `zsjos:subordinate-sales:pause-all` under the existing subordinate-sales
menu. On first installation only, enabled roles with stable code `sales_manager` receive the
permission. The version marker prevents a later bootstrap rerun from restoring an administrator's
manual removal. The migration defines permission metadata only and does not pause users or modify
accounts, dispatch preferences, page presence, Leads, assignments, or audit history.

The migration is additive, transaction-wrapped, repeatable, and forward-only. Applying it to an
existing environment remains a separately approved operation. After controlled execution, run
`verify-bootstrap.sql`; role membership is intentionally not a permanent verification invariant.

## Optional modules

## V094 student contact chain

`V094__student_contact_chain.sql` adds acceptance/collaborator columns, contact configuration and request-fingerprinted idempotent configuration-command records, immutable request-fingerprinted contact records, extension snapshots, collaborator audit logs, empty administrator-maintained reason dictionaries, user-relation scenes, permissions, menus, and notification rules. It marks only legacy, never-accepted active service relations pending acceptance and converts historical director routes to collaborator assignments without creating contact tasks. It also versions task reminder-stage idempotency so an approved deadline extension can emit reminders for the revised schedule while retaining the earlier schedule history. Guarded alters repair the fingerprint and withdrawal-idempotency columns/index after a partial earlier run. Fixed menu IDs are guarded by an ownership preflight and role grants use an exact allowlist. The migration is repeatable and forward-only and must not be run without the separate database-execution confirmation.

Before enabling submissions that exceed the configured interval, deploy and activate the BPM process definition with key `zsjos_student_contact_extension` and task definition key `deliverySupervisorReview`. V094 does not fabricate a BPM model or personnel relationships. After controlled application, run `verify-bootstrap.sql` and verify the V094 schema/version result before releasing the UI.

### V095 student contact extension BPM form

`V095__student_contact_extension_bpm_form.sql` adds one repeatable, read-only snapshot form per enabled tenant, marked `zsjos-system-form:student-contact-extension`; its `fields` value is a JSON array of field objects, and verification requires exactly one active marked form for every enabled tenant. It depends on V094 and the BPM form tables, changes no business rows, and does not bind a runtime-created model automatically. After applying it, an administrator must create/update the `zsjos_student_contact_extension` model as a normal “流程表单” model and select the tenant form named `学员联系延期审批表单`; leaving `formId` empty is invalid for this process. BPM task approval opinion remains the framework's required task `reason` field and is stored by ZSJOS as `decisionReason`.

An approved optional module adds its own manifest under
`script/sql/mysql/modules/`, desired schema, migration directory starting at
`V001`, and verification SQL. Its manifest declares Core and other module
dependencies. Enable it explicitly in `ZSJOS_DB_MODULES` and build a new application and migrator
image; it never means rebuilding or clearing the existing database. Removing a
module does not delete its tables or rows.

## V101 student basic-information permission

V101 follows V100 and adds only the System button permission `zsjos:student:update-basic-info` under
My Students. On first installation it grants the button to enabled `system_administrator` and
`study_planner` roles; later reruns do not restore an administrator-removed relation. Runtime access
also requires the current accepted service owner through `student-service:update-basic-info` object
authorization. The migration changes no Person, Lead, order, contact, task, event, or historical
snapshot row. It is additive, repeatable, forward-only, and generated but not executed by this change.
After separately approved execution, run `verify-bootstrap.sql`; recovery is performed through System
role permission configuration while retaining the permission definition.
# V103 new-media operator menu repair

V103 removes menu `7022` (`/zsjos/media-students`) from `new_media_operator` roles. The menu remains
exclusive to `content_director`; the planner-owned `/zsjos/my-students` menu is unchanged. The migration
is repeatable and changes no business or audit rows. Applying V103 locally is recorded separately from
formal/shared-environment rollout.

## V115 generic work-order core

V115 follows V114 and adds the additive tenant-scoped work-order scene, work-order, and
status-history tables. Scene field definitions and submitted values are JSON snapshots;
no business options, users, attachments, or sample work orders are seeded. The migration
is repeatable and does not delete or rewrite existing user-relation, Lead, or task rows.
Apply only through the numbered migration chain, then run the read-only bootstrap/schema
verification. Existing-environment execution still requires separate approval.

V116 follows V115 and grants `study_planner` only the dedicated student-repurchase button
and the existing personal-order page. It explicitly does not grant generic order creation or
external historical-customer repurchase. V113-V116 are edited in place only while delivery
records confirm they have not run in any shared environment; otherwise use a new forward migration.

V116 v5 contains its guarded order-column addition, canonical menu recovery, role grants and version
writes in one temporary stored-procedure call. It treats records owned by V073/V114/V116 for menu
`73020`, V025/quick-init/V116 for menu `6813`, and V116 for menu `73440` as recoverable migration
state. Missing, soft-deleted or drifted records from those owners are restored to canonical metadata;
zero enabled `study_planner` roles is valid. A foreign fixed-ID owner, active duplicate permission or
invalid `/zsjos` root still blocks before any mutation. A GUI runner that continues after the failed
call can execute only the final temporary-procedure cleanup.

The local 2026-08-23 diagnostic environment already records V116 v4 and currently has canonical
menus/grants after its GUI runner continued past the assertion. That environment must retain its v4
record; this v5 file is the production/fresh-chain definition for environments where V116 has not run.

## V117 Lead category label snapshot

V117 follows V116 and adds only nullable category-label snapshot columns to `zsjos_lead` and
`zsjos_lead_duplicate_review`. It deletes no rows and deliberately performs no historical backfill:
an old Lead without a snapshot continues through the documented current-dictionary compatibility
path, while every new or explicitly changed category selection stores its contemporaneous label.
Run V117 through the normal reviewed migration sequence and then require the
`lead_category_label_snapshot` check in `verify-bootstrap.sql` to return `PASS`. Do not execute the
migration against a shared environment without separate confirmation.

## V114 failed-execution recovery

V114 v6 executes all schema, backfill, menu, role-grant and version statements within one temporary
stored-procedure call. It creates or canonically restores the V073-owned My Students page `73020`,
creates or restores delivery-stage button `73428`, and grants both to enabled `study_planner` roles.
An environment with no enabled planner role still completes successfully and leaves the permissions
available for later System role configuration. Expected missing or soft-deleted records are therefore
repeatable recovery cases, not migration failures.

Only an invalid or ambiguous active `/zsjos` root, a fixed menu ID owned by another permission, or an
active duplicate permission raises a direct procedural `SIGNAL`. These are unsafe ownership conflicts
that require administrator investigation. Do not encode `SIGNAL` in a prepared SQL string: MySQL
rejects that command in the prepared-statement protocol. Keeping every migration mutation inside the
same `CALL` also prevents GUI runners that continue after an error from executing later business SQL;
after a failed call, the only remaining file statement drops the temporary procedure.

The repository static migration check enforces this rule for V113 and later migrations. Older applied
migrations retain their historical bytes and checksums; specifically, V094 still contains the legacy
dynamic validation pattern and must not be rewritten in place.

After any failed manual V114 v4/v5 attempt, stop before rerunning it and inspect both schema-version tables,
the delivery columns on `zsjos_service_relation` and `zsjos_student_contact_record`, menus `73020` and
`73428`, and tenant-scoped `study_planner` grants. If neither version table records V114, correct the
V073-owned parent-menu baseline and run the reviewed repeatable V114 v6 file. If either table records
the earlier V114, preserve the executed file/checksum and create a new forward migration from the observed state;
do not overwrite or manually delete the version marker. Database inspection and recovery execution
remain separately approved operations.

## V113 media student center consolidation

V113 follows V112 and retires the standalone third-party-account, content-production, and
account-positioning page menus without deleting their business tables, records, APIs, or stable
button permission strings. Their operation permissions are reparented beneath
`/zsjos/media-students`, and enabled `new_media_operator` roles receive that page menu; runtime
student and object scope remains enforced independently by service relations, account responsibility,
and current task assignment.

The migration adds versioned third-party-account field definitions, account field-value and label
snapshot columns, media-student talk records, and the administrator-only field-configuration page.
It seeds only the system defaults `uid` and `nickname` for enabled tenants, guards fixed menu IDs
`73500`-`73502` against unrelated ownership, and changes no historical business value or snapshot.
It is forward-only and repeatable. Applying it to an existing environment requires separate approval;
after controlled execution, run `verify-bootstrap.sql` and require every V113 check to return `PASS`.

## V119 Workbench relative child menu paths

V119 follows V117 in the rewritten baseline and normalizes only active page-menu metadata directly beneath the unique active
`/zsjos` Workbench root. A stored child path such as `/zsjos/my-students` becomes `my-students`; the
resolved browser URL remains `/zsjos/my-students`. Buttons, external links, nested pages outside that
direct parent, role grants, users, and business rows are not changed.

The migration is repeatable. Before updating, it blocks when the Workbench root is missing or
ambiguous, or when normalization would collide with an existing active sibling path. Recovery is a
forward correction after resolving the conflicting menu metadata; there is no automatic rollback
because restoring duplicated parent prefixes would reintroduce invalid routing. Apply V119 only
through the reviewed migration sequence and require `workbench_relative_child_paths` in
`verify-bootstrap.sql` to return `PASS`.

## V132 Workbench menu rendering mode

V132 adds the server-owned `workbench_render_mode` column to `system_menu`. It is additive and repeatable,
defaults existing menu rows to `native`, and does not change permissions or business data. After controlled
execution, run `verify-bootstrap.sql` and require the V132 rendering-mode check to pass. Removing the column
is not an automatic rollback because administrator-selected rendering metadata would be lost.

## V133 director interview form presentation

V133 follows V132 in the baseline and normalizes active director interview templates, retires the
six-dimension field, supplies the reviewed dictionary choices, and guards the two director draft-version
columns. It uses a database named lock and preserves published history and service-relation snapshots.
Execute it only through the controlled migration sequence, then require the V133 verification check to pass.
Rollback must use a reviewed forward template version rather than rewriting historical snapshots.

## V134 positioning confirmation handoff

V134 follows V133 and adds tenant-scoped positioning submission snapshots and public confirmation-link
records. Link rows contain only SHA-256 token digests; no plaintext token, fixed expiry, test account, or
business dictionary option is seeded. It also defines `zsjos:positioning-card:student-link-generate`, adds it
to tenant packages that already contain the media-student page, and grants it to existing
`new_media_operator` roles. Runtime object and status checks remain mandatory.

The only business-row conversion targets active, non-deleted positioning cards currently in legacy
`student_confirm`. A compatibility submission is inserted only when its service relation, student and
operator identifiers are present; only cards with a resulting submission move to `student_link_pending`.
Because the old schema has no authoritative submit time, the compatibility `submitted_at` remains `NULL`.
No card, history, or link is deleted. The migration is repeatable and forward-only. Generated or consumed
links and student decisions cannot be rolled back into the retired Partner-H5 flow.

Do not execute V134 against any database until the target environment and count of matching
`student_confirm` rows have been reviewed and separately approved. After controlled execution, run
`verify-bootstrap.sql` and check the V134 version marker, both tables, token-hash uniqueness, and button
permission. This repository change wires V134 into fresh bootstrap but does not execute it.

## V135 applied director and positioning schema repair

V135 follows V134 and repairs environments that applied earlier revisions of V133 or V134. It adds the
missing precheck/interview draft-version columns to `zsjos_service_relation` and the six legacy positioning
section snapshot columns to `zsjos_positioning_card_submission`. The compatibility update targets only
active submission rows created by V134, joins them to the authoritative positioning card by tenant and card
ID, and fills only null snapshot sections. It does not invent data, overwrite existing snapshot content,
change card status, rewrite V133/V134 markers, or modify permissions.

The migration is guarded and repeatable. It performs additive DDL and a bounded source-backed update; it
deletes no rows. Rollback by dropping the columns is unsafe after runtime writes because it would discard
draft versions and immutable positioning snapshot content. Apply it only after V134. After execution, run
`verify-bootstrap.sql` and require the V128, V134, and V135 checks to return `PASS`; also confirm no
V134-owned submission retains a null legacy section.

## V137 Workbench menu rendering-mode collision repair

The Windows development line had already applied different local migrations under V132/V133/V134 before
the remote Workbench rendering migration was received as V132. Those applied markers remain historical
facts and must not be rewritten. V137 therefore repeatably ensures `system_menu.workbench_render_mode`
exists and records a new unique compatibility marker without changing menu grants or business rows. Run it
after V132-V136 for both upgraded and fresh environments, then require the V137 compatibility check in
`verify-bootstrap.sql` to return `PASS`.

## V140 supervisor idempotency, positioning expiry, and menu repair

V140 follows V139. It creates the tenant/operator/idempotency-key command ledger used by supervisor Lead
commands, adds `expires_at` plus its lookup index to positioning confirmation links, backfills only active
historical links to `create_time + 7 days`, and widens the student decision comment to 2000 characters.
It restores menu `73460` to the student business-form page, moves the interview-template page and its
buttons/grants to stable menu `73483`, and forward-repairs V139 supervisor permission definitions by
preferring the oldest compatible identity, preserving its grants, and leaving exactly one active definition
per permission. It also preserves the canonical relative public-sea path `lead-aging-pool`.

The migration is forward-only and repeatable. This repository update does not execute it against any
database. V139 and V140 now execute their repair and version writes inside one stored-procedure call; their
temporary permission tables use the System menu collation, and V140 uses a legal role-menu self-join. After
controlled application, run `verify-bootstrap.sql` and require the V139 and V140 checks to pass.
`script/sql/mysql/audit/V131_permission_grant_audit.sql` and
`script/sql/mysql/audit/V135_snapshot_cleanup_audit.sql` are read-only scope/export scripts. Do not revoke
permissions or clear inferred snapshot fields until the exact tenant and row list has been separately
confirmed and a recovery export has been retained.

## V142 partial V139/V140 execution repair

V142 is required when a statement-batch client continued after the former V139 collation error or V140
same-target-table error and then wrote either migration's success markers. Do not delete or edit those applied
version rows and do not rerun a recorded V139/V140 file. First run a read-only audit of both version tables,
menus `73460`/`73483`, the five supervisor permissions, affected role-menu grants, positioning expiry objects,
and the command table. After the exact environment is separately approved, apply V141 if pending and then V142.

V142 repairs only additive V140 schema, migration-owned menu identities, and affected grants. It preserves
effective grants while consolidating duplicate supervisor menu definitions, completes the original initial
`sales_manager` grants, and blocks rather than overwriting a conflicting menu owner. It deletes no Lead,
positioning, command, account, or other business row. Its single stored-procedure call records V142 only after
all repair statements succeed. Run `verify-bootstrap.sql` afterward and require the V139, V140, V141, and V142
checks to pass. Recovery is forward-only; do not drop idempotency or positioning schema after runtime use.

## V106, V108 and V118 retired migration placeholders

V106, V108 and V118 are retained as metadata-only placeholders because their Student Operations business
capabilities are retired while migration numbering must remain continuous. Each file inserts a missing row
into both version registries and preserves an existing row unchanged. Applying them does not create tables,
menus, permissions, notification configuration or business data.

## V144 new-media Student Operations retirement

V144 permanently removes the retired new-media exception-ticket, cooperation-assessment and graduation
domains. It deletes graduation notification messages/rules/templates and business events first, then
Student Operations grants and menu/button definitions, then drops the three business tables. It also
preserves the V106/V108/V118 retired migration markers before recording V144. Student-contact extension
approval (`zsjos_student_contact_extension`) is a separate business capability and is not touched.

Before execution, retain a verified full database backup and confirm there are no active or historical
`zsjos_media_graduation` process instances. Retire that Flowable model/deployment through the BPM service
and repository boundary; do not delete `ACT_*` rows directly. V144 is repeatable but irreversible without
restoring the backup. After the first and second executions, verify all retired menus, grants, notification
records, events and tables are absent, while the student-contact extension table, permission and process
definition remain present.

## V149 feedback management

V149 follows V147 and is the only migration for the requirement, BUG and technical-support feedback
workspace. V148 is intentionally skipped because that number is owned elsewhere. The migration adds
`business_type` to the generic work-order table (`GENERIC` for existing records, `FEEDBACK` for this
feature), six tenant-scoped feedback tables, menu/button metadata, four default BPM dynamic forms,
feedback settings, the approved support-type dictionary, and four notification templates/rules.

All schema and seed writes are additive and repeatable. Fixed menu IDs, routes, permissions, BPM form
markers and dictionary identities are checked before writes; conflicts stop execution instead of
overwriting existing administrator data. Existing feedback and work-order rows are never deleted or
rebuilt. Both `zsjos_schema_version` and `zsjos_module_schema_version` record V149 with the file SHA-256.

Before an existing-environment execution, review the migration plan, retain a database backup, and run
the read-only checks in `script/sql/mysql/verify-bootstrap.sql`. Afterward verify the six tables,
business-type index, binary idempotency keys, four forms/settings per enabled tenant, five dictionary
values, menu/package coverage, notification defaults, and both version markers. Rollback is
forward-only; retain feedback history, snapshots, surveys and notification messages.
### V150 claim-pool and Partner permissions

`V150__claim_pool_read_and_partner_permissions.sql` depends on V143, does not require V149, and changes only
menu metadata and role-menu grants. It preserves existing claim-pool readers, grants the read-only page to enabled sales
manager roles, consolidates Partner query/manage permissions, and retires the old Partner action and
subordinate-page permissions after compatibility grants are copied. It does not modify Partner, Lead,
account, ownership or historical converted records. Review the generated grant audit before controlled
execution; rollback requires a forward permission migration.

V148 and V150 use a single stored-procedure call as the failure boundary. This is required because GUI
statement-batch clients may report a failed `CALL` and still execute later top-level statements. V148 uses
`information_schema`-guarded dynamic DDL for its three `system_notice` lifecycle columns; do not replace it
with `ADD COLUMN IF NOT EXISTS`. If the former V148 was partially executed, retain its additive tables,
menus, and both version markers, take a backup, and explicitly rerun the corrected V148 file. Verify
`publish_status`, `publish_time`, `offline_time`, both notice child tables, menu IDs `79910-79912`, and both
V148 registry rows. A version-aware runner may otherwise skip the repair.

V150 is sourced by `bootstrap.sql` after V148 and independently verifies V143 in both version registries;
the separately owned V149 file and version rows are not prerequisites. If a continue-on-error client already
wrote V150 state during a former partial run, retain both registry rows and existing permission history, then
audit menu IDs `6749`, `6852`, `79920`, affected role-menu grants, tenant packages, and both V150 registry rows
before deciding whether an idempotent rerun is sufficient. If an old permission was retired before its grants
were copied, a rerun cannot infer those former grants; restore them only through a separately reviewed forward
repair backed by the pre-migration export. Any destructive cleanup or permission rollback remains a separately
reviewed operation.
