# Workstream Handoff: 20260810-main-existing

- Workstream ID: `20260810-main-existing`
- Status: `active`
- Goal: Preserve the current development state as one existing workstream while adopting isolated rules for future parallel development.
- Non-goals: Splitting, relocating, reclassifying, committing, or publishing changes that existed before this policy was adopted.
- Branch: `main` (user-approved transitional exception)
- Worktree: `D:\ZSJ-OS`
- Base commit: `61e6232837de2fdc77800de2311589a8bbdec1b7`
- Target branch: `main`
- Ownership scope: The existing development state in this worktree; individual pre-existing files are intentionally not reclassified by this policy change.
- Owner: Current main workstream
- Dependencies: None declared. Future workstreams must not depend on this workstream's uncommitted changes.
- Integration order: Any future workstream that needs this line's changes must start from a committed integration point after this line is ready.
- Verification plan: Apply the verification required by `AGENTS.md` for each task and record the evidence in the entries below.

## Entries

### 2026-08-10 18:57:12 +08:00

- Branch: `main`
- Worktree: `D:\ZSJ-OS`
- HEAD commit: `61e6232837de2fdc77800de2311589a8bbdec1b7`
- User goal: Implement the approved repository rules for future multi-branch, multi-worktree development and isolated AI handoffs.
- Key decisions: Keep the current development state as one transitional workstream; require each future line to use a committed base, unique `codex/<workstream-id>` branch, separate worktree, recorded ownership scope, and dedicated handoff file; retain the root handoff file as a stable guide and legacy archive without a dynamic index.
- Result: Added parallel-development rules, replaced the shared per-turn handoff policy, preserved all legacy entries, and registered the existing main workstream in its own handoff file.
- Changed files: `AGENTS.md`, `HANDOFF.md`, `handoff/20260810-main-existing.md`.
- Verification: Reviewed rule consistency and Markdown structure; `git diff --check` completed successfully; final status confirmed that unrelated existing files were not modified.
- Dependency / integration impact: Future workstreams must begin from committed state and use separate branches, worktrees, and handoff files. No branch, worktree, commit, merge, or push operation was performed.
- Remaining work: None.

### 2026-08-10 19:19:14 +08:00

- Branch: `main`
- Worktree: `D:\ZSJ-OS`
- HEAD commit: `d3459e2c064d02121315d233c0f52bfa0ec25d16`
- User goal: Diagnose the attached `zsjos_business_task.uk_tenant_idempotency` duplicate-key exception raised while creating an opportunity follow-up reminder.
- Key decisions: Treat the attachment-only request as read-only diagnosis; do not modify application code or database state; distinguish the two independently generated follow-up record ID domains when identifying the root cause.
- Result: Confirmed that lead follow-up records and opportunity follow-up records use separate auto-increment tables, while both callers build reminder task keys as `lead-follow-up-reminder:{recordId}`. The first opportunity follow-up record can therefore reuse an existing lead follow-up record ID and collide with the tenant-wide task idempotency index. Completing the previous task does not release the unique key because task history remains in the table.
- Changed files: `handoff/20260810-main-existing.md` only (required read-only task delivery entry).
- Verification: Inspected the exception call path, service implementation, mapper update predicates, task and follow-up table definitions, focused unit tests, Git state, and the supplied production backup. The backup contains tenant `1` lead follow-up record `id=1` and task key `lead-follow-up-reminder:1`, matching the reported collision. No tests were run because no implementation changed.
- Dependency / integration impact: None. A future fix should remain in `yudao-module-zsjos`; no schema or dependency change is required for the preferred key-namespace correction.
- Remaining work: Await user confirmation before changing behavior. Proposed scope is to namespace reminder keys and payload references by follow-up record scope, update both callers and focused tests, preserve existing task history, and run the module tests.

### 2026-08-10 19:27:58 +08:00

- Branch: `main`
- Worktree: `D:\ZSJ-OS`
- HEAD commit: `d3459e2c064d02121315d233c0f52bfa0ec25d16`
- User goal: Implement the confirmed fix for follow-up reminder task idempotency collisions between lead and opportunity follow-up records.
- Key decisions: Add stable `lead` and `opportunity` record-scope constants; include the scope in reminder task idempotency keys and payloads; preserve the existing tenant-wide unique index and all historical task rows; avoid schema, migration, dependency, and external API changes.
- Result: Reminder keys now use `lead-follow-up-reminder:{recordScope}:{recordId}`. Both follow-up paths pass their explicit record scope, and regression coverage verifies distinct keys for identical record IDs plus correct scope propagation from each caller.
- Changed files: `backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/enums/LeadConstants.java`; `backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/lead/LeadLifecycleTaskService.java`; `backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/lead/LeadFollowUpServiceImpl.java`; `backend/yudao-module-zsjos/src/test/java/cn/iocoder/yudao/module/zsjos/service/lead/LeadLifecycleTaskServiceTest.java`; `backend/yudao-module-zsjos/src/test/java/cn/iocoder/yudao/module/zsjos/service/lead/LeadFollowUpServiceImplTest.java`; `handoff/20260810-main-existing.md`.
- Verification: `git diff --check` passed. Focused Maven run for `LeadLifecycleTaskServiceTest` and `LeadFollowUpServiceImplTest` passed 8 tests with 0 failures and 0 errors, including compilation of the 20-module dependency reactor. The full reactor run was blocked before ZSJOS by the unrelated existing failure `CodegenEngineUniappTest.testExecute_treeSearch` in `yudao-module-infra` (194 tests, 1 failure). A direct full ZSJOS run executed 89 tests; all changed-path tests passed, but the suite remained red because the unrelated existing `LeadFollowUpRuleServiceImplTest.updatesTimeoutAndAdvancesVersion` raised `LEAD_FOLLOW_UP_RULE_INVALID` (1 error). No live HTTP request was performed because the running service has not been rebuilt or restarted.
- Dependency / integration impact: No new dependency, schema change, migration, or data cleanup. Existing reminder history remains readable; newly created reminders use the scoped key and an additive `followUpRecordScope` payload field.
- Remaining work: Deploy or restart the backend with this code and repeat the previously failing opportunity follow-up request to complete runtime verification. Existing unrelated Infra and follow-up-rule test failures remain outside this task.

### 2026-08-10 19:37:30 +08:00

- Branch: `main`
- Worktree: `D:\ZSJ-OS`
- HEAD commit: `d3459e2c064d02121315d233c0f52bfa0ec25d16`
- User goal: Diagnose the attached `zsjos_lead_intended_product.uk_tenant_lead_product` duplicate-key exception raised while updating lead basic information.
- Key decisions: Treat the attachment-only request as read-only diagnosis; preserve the pending follow-up reminder fix; do not alter code, schema, migrations, or database state without separate confirmation; prefer retaining logical-delete history over introducing physical deletion.
- Result: Confirmed that `LeadBasicInfoService.update` logically deletes all active intended-product rows and immediately reinserts the requested list. The table unique index covers only `(tenant_id, lead_id, product_ref)`, so a logically deleted row continues to reserve the key and reinserting an unchanged course fails. The reported tenant, lead, and product match the active row in the supplied production backup. The stable fix is a new forward migration and synchronized fresh-schema baseline that enforce uniqueness only for active rows, using a generated active product reference that becomes `NULL` after logical deletion.
- Changed files: `handoff/20260810-main-existing.md` only for this diagnosis entry. Previously confirmed follow-up reminder changes remain uncommitted and untouched.
- Verification: Inspected the exception path, update transaction, mapper delete method, `@TableLogic` base class, intended-product DO, current schema and unique index, submission/update validation, product API documentation, migration rules, production backup data, and current Git diff. No tests were run because no implementation changed in this turn.
- Dependency / integration impact: The recommended fix would affect `script/sql/mysql/schema/core.sql`, `script/sql/mysql/00-bootstrap-schema.sql`, bootstrap verification and migration documentation, plus a new repeatable `V021` migration. It requires no Java API or dependency change and should preserve all business rows.
- Remaining work: Await explicit confirmation to implement the schema fix. Before any database synchronization, separately confirm the exact target environment; the migration should change only index metadata and add a generated column, seed no rows, delete no rows, run after V020, record V021, and document rollback limitations.

### 2026-08-10 19:48:25 +08:00

- Branch: `main`
- Worktree: `D:\ZSJ-OS`
- HEAD commit: `d3459e2c064d02121315d233c0f52bfa0ec25d16`
- User goal: Implement the confirmed V021 fix for intended-product duplicate keys after logical deletion, without applying it to an unspecified database environment.
- Key decisions: Preserve `LeadBasicInfoService` logical-delete-and-reinsert behavior and all intended-product history; add a stored `active_product_ref` generated column that equals `product_ref` only for non-deleted rows; replace the tenant/lead/product unique index with a tenant/lead/active-product unique index; keep unknown-product `NULL` behavior unchanged; do not execute DDL against a shared or current database without separate target confirmation.
- Result: Added guarded, repeatable V021 DDL and synchronized the Core desired schema, fresh baseline, baseline version metadata, bootstrap verification, migration documentation, and the legacy manual lead-submission table definition. Extended the database CLI static check to require V021 and its controlled upgrade test to replay V020/V021, verify idempotency, and prove that the same product can be inserted after its previous row is logically deleted.
- Changed files: `script/sql/mysql/migrations/V021__lead_intended_product_active_unique_key.sql`; `script/sql/mysql/schema/core.sql`; `script/sql/mysql/00-bootstrap-schema.sql`; `script/sql/mysql/02-bootstrap-zsjos-seed.sql`; `script/sql/mysql/verify-bootstrap.sql`; `script/sql/mysql/migrations/README.md`; `script/sql/mysql/tools/zsjos_db.py`; `script/sql/zsjos_lead_submission_dispatch.sql`; `handoff/20260810-main-existing.md`. Previously confirmed follow-up reminder files remain uncommitted and were not changed in this turn.
- Verification: `git diff --check` passed. `.\zsjos-db.ps1 check` passed manifest, migration order, desired-schema/baseline identity, Java mapping, baseline version, and verification consistency. Additional read-only checks confirmed identical intended-product table blocks, no old index in baseline/manual create scripts, exactly three guarded V021 DDL operations, aligned metadata tokens, required verification predicates, and valid Python syntax. MySQL/Docker integration tests were not run because they start an external service and no separate service-start confirmation was provided.
- Dependency / integration impact: No Java API or new dependency. V021 depends on V020 and changes only table/index metadata; it inserts only schema-version metadata, seeds no business rows, and deletes no rows. Actual MySQL `ALTER TABLE` may rebuild the table depending on server version and storage settings, so production execution must use the release migrator backup/lock flow.
- Remaining work: Confirm the target environment before applying V021. After migration, run `verify`, retry the basic-information update that keeps the same course, and retain the migrator backup until runtime verification succeeds. Controlled Docker `test-fresh`, `test-upgrade`, and `test-guardrails` remain unexecuted in this turn.

### 2026-08-10 20:50:44 +08:00

- Branch: `main`
- Worktree: `D:\ZSJ-OS`
- HEAD commit: `d3459e2c064d02121315d233c0f52bfa0ec25d16`
- User goal: Diagnose the WARN mapped from `LeadAppealServiceImpl.submit` line 121.
- Key decisions: Treat the single log line as read-only diagnosis; do not change code, deploy BPMN, alter process permissions, or modify shared BPM state without explicit authorization; preserve all pending uncommitted fixes.
- Result: Line 121 is the stable `LEAD_APPEAL_PROCESS_UNAVAILABLE` mapping emitted when `BpmProcessInstanceApi.createProcessInstance` throws. The submit transaction rolls back. The most likely cause is that tenant-scoped process key `zsjos_lead_appeal_review` is not imported, published, active, or has no matching BPM definition metadata. Other mapped causes include start-user/start-department denial and BPM validation of the caller-selected reviewers for task key `appealReview`. The broad `RuntimeException` catch discards the original BPM error, so this log line alone cannot distinguish those cases.
- Changed files: `handoff/20260810-main-existing.md` only for this diagnosis entry.
- Verification: Inspected the exact service line, stable error code, BPM API implementation, active process lookup and tenant scoping, start permission logic, BPMN process/task keys, V015 migration, deployment documentation, current tests, and Git state. No runtime query or test was run because no implementation changed and the current BPM environment was not authorized for inspection or mutation.
- Dependency / integration impact: None. Correct operation depends on the existing `yudao-module-bpm` public API and a tenant-scoped, active process definition deployed from `script/bpm/zsjos_lead_appeal_review.bpmn20.xml`.
- Remaining work: In the affected tenant, inspect BPM management for an active published definition with key `zsjos_lead_appeal_review`, verify task key `appealReview`, check start-user/start-department settings for the applicant, and inspect the immediately preceding BPM exception log. Importing/publishing the BPMN or changing process permissions requires separate explicit confirmation.

### 2026-08-10 19:57:37 +08:00

- Branch: `main`
- Worktree: `D:\ZSJ-OS`
- HEAD commit: `d3459e2c064d02121315d233c0f52bfa0ec25d16`
- User goal: Diagnose why lead `8` could not submit an appeal from the supplied request log.
- Key decisions: Treat the request as read-only diagnosis; preserve all existing application, schema, and worktree changes; use system department ownership and permission configuration as the authoritative reviewer source; do not change account, department, role, or shared external state.
- Result: Confirmed the request fails during first-round reviewer resolution before the appeal insert or BPM process start. Lead owner user `231` belongs to department `1021`; the absence of the conditional second user lookup before `LEAD_APPEAL_REVIEWER_NOT_CONFIGURED` shows that department `1021` has no `leader_user_id` configured in the observed request. First-round submission requires that direct department leader to exist, be enabled, and hold `zsjos:lead:appeal:review-sales-manager`.
- Changed files: `handoff/20260810-main-existing.md` only (required read-only task delivery entry).
- Verification: Inspected the supplied chronological SQL/service log, `LeadAppealServiceImpl.resolveReviewers`, system user and department API implementations, permission evaluation, the appeal error code, V015 permission grants, architecture contract, deployment guide, frontend request handling, nested repository instructions, and current Git state. No tests or live requests were run because no implementation changed and changing shared organization configuration requires separate explicit confirmation.
- Dependency / integration impact: None. Correcting department and role configuration uses existing system-owned sources and requires no code, schema, BPM, or dependency change.
- Remaining work: In tenant `1`, configure an enabled leader for department `1021` and grant that leader `zsjos:lead:appeal:review-sales-manager`, then retry the appeal request. A read-only database check can confirm the exact current values if the target database is explicitly identified and reachable.

### 2026-08-10 19:58:26 +08:00

- Branch: `main`
- Worktree: `D:\ZSJ-OS`
- HEAD commit: `d3459e2c064d02121315d233c0f52bfa0ec25d16`
- User goal: Correct the evidentiary precision of the immediately preceding appeal-submission diagnosis before delivery.
- Key decisions: Preserve the prior chronological entry as required; record this correction separately; account for MyBatis transaction-local query caching when interpreting the absence of a repeated user SQL statement.
- Result: The log conclusively identifies first-round reviewer validation failure for owner user `231` and department `1021`, but it does not by itself uniquely prove that `leader_user_id` is null. The missing second SQL query is also possible when the leader is user `231` and the repeated lookup is served from the MyBatis local cache. The exact failing condition is one of: no department leader, missing or disabled leader user, or leader lacking `zsjos:lead:appeal:review-sales-manager`.
- Changed files: `handoff/20260810-main-existing.md` only (correction entry).
- Verification: Rechecked the conditional call path against the system API implementation and MyBatis same-transaction query behavior. `git diff --check` is the final repository check; no runtime or database query was performed.
- Dependency / integration impact: None.
- Remaining work: Read the tenant `1` department `1021` leader, that user's status, and effective permission assignment to distinguish the three configuration conditions; correct the identified system configuration and retry.

### 2026-08-10 20:12:49 +08:00

- Branch: `main`
- Worktree: `D:\ZSJ-OS`
- HEAD commit: `d3459e2c064d02121315d233c0f52bfa0ec25d16`
- User goal: Log in as the supplied sales-manager account, check appeal-related permissions, explain the apparent unmigrated page, and provide an adjustment plan.
- Key decisions: Treat the task as read-only inspection and planning; use the explicitly authorized account only against the local tenant `1` service; do not record credentials or tokens; do not change department leadership, permissions, code, database state, service state, or Git state; treat server menu metadata as authoritative and distinguish menu authorization from frontend component binding.
- Result: Confirmed user `233` is the enabled login identity for the sales-manager account, belongs to department `1021` (`销售转化一部`), has roles `normal_user` and `sales_manager`, and receives both `zsjos:lead:appeal:query` and `zsjos:lead:appeal:review-sales-manager`. The server also returns visible menu `6804` at `/zsjos/appeals` with component metadata `zsjos/leadAppeal/index`. The workbench already contains `LeadAppealPage`, but binds it to `/zsjos/leads/appeals`, so the authoritative menu path falls through to “页面尚未迁移”. A stale route retained across account switching initially produces a 404 until a primary menu is clicked. Real pending and handled appeal-inbox requests pass feature authorization but both return code `500`; inspection found that `BpmProcessTaskApiImpl` does not short-circuit empty task pages before querying Flowable with an empty process-instance ID set, unlike the established BPM controllers. The original submission still requires department `1021` to identify this enabled authorized user as its leader; the manager account cannot read the protected department detail endpoint, so the current leader value was not changed or independently confirmed.
- Changed files: `handoff/20260810-main-existing.md` only (required read-only task delivery entry).
- Verification: Logged into the running workbench, confirmed the account identity and visible menu behavior, queried the authenticated permission-info/profile APIs with tenant `1`, verified the two effective appeal permissions and the exact server menu path/component, called pending and handled appeal inbox endpoints and observed code `500` for both, inspected frontend route/component wiring, menu navigation logic and tests, inspected the ZSJOS appeal service and BPM public API empty-page behavior, and ran `git diff --check`. No code tests were run because implementation was not authorized.
- Dependency / integration impact: The recommended implementation affects the employee workbench route/component registry and account-switch fallback, the BPM task public API empty-page behavior and tests, plus system department configuration for tenant `1`. No new dependency or schema change is needed. Changing department leadership is shared external state and still requires separate explicit confirmation with the exact target user and impact.
- Remaining work: After implementation confirmation, register the existing appeal page against authoritative component metadata or align its route to `/zsjos/appeals`, redirect inaccessible stale routes to the first authorized internal route, add BPM API empty-page guards and tests, verify/deploy the appeal BPM definition, and separately confirm setting department `1021` leader to user `233`; then rebuild/restart under explicit service-state confirmation and run authorized/unauthorized browser and API checks.

### 2026-08-10 20:16:20 +08:00

- Branch: `main`
- Worktree: `D:\ZSJ-OS`
- HEAD commit: `d3459e2c064d02121315d233c0f52bfa0ec25d16`
- User goal: Execute the previously proposed lead-appeal frontend, BPM empty-state, organization configuration, and runtime verification adjustments.
- Key decisions: Do not begin behavior changes until the repository-mandated pre-change facts, assumptions, non-goals, scope, verification plan, and separate confirmations are complete; isolate the code work from the dirty transitional main worktree; propose branch `codex/lead-appeal-routing` and worktree `D:\ZSJ-OS-worktrees\lead-appeal-routing` from committed base `d3459e2c064d02121315d233c0f52bfa0ec25d16`; treat department leadership and service restart as separately confirmed external-state changes.
- Result: Verified that the proposed branch and worktree do not exist, the base commit is available, and the current main worktree still contains unrelated pending changes. No branch, worktree, code, database, permission, organization, process definition, or service-state change was performed because explicit confirmation is required after presenting the exact targets and impact.
- Changed files: `handoff/20260810-main-existing.md` only (required task delivery entry).
- Verification: Ran read-only Git worktree, branch, status, and HEAD checks. No implementation tests ran because implementation has not started.
- Dependency / integration impact: The proposed code work affects `frontend/workbench` and the BPM public task API, with no new dependencies or schema changes. Proposed external configuration would set tenant `1` department `1021` leader to user `233`. Proposed runtime verification would rebuild and restart the local backend on port `48080`; BPM process deployment remains a separate external-state action unless already deployed.
- Remaining work: Await explicit confirmation for branch/worktree creation, department leader change, local backend rebuild/restart, and any missing BPM process deployment; after confirmation create and register the isolated workstream before editing.

### 2026-08-10 20:51:50 +08:00

- Branch: `main`
- Worktree: `D:\ZSJ-OS`
- HEAD commit: `d3459e2c064d02121315d233c0f52bfa0ec25d16`
- User goal: Correct the handoff ordering after diagnosing the `LeadAppealServiceImpl.submit` line-121 warning.
- Key decisions: Do not rewrite or move concurrent workstream entries. This appended correction preserves the earlier diagnosis and establishes the chronological delivery position for this task.
- Result: The preceding 20:50:44 diagnosis remains valid: the warning is `LEAD_APPEAL_PROCESS_UNAVAILABLE`, most likely a missing/inactive tenant-scoped BPM definition or a BPM start/reviewer validation failure; no code, BPM state, or permissions were changed.
- Changed files: `handoff/20260810-main-existing.md` only.
- Verification: Confirmed the earlier 20:50:44 entry was inserted before concurrently appended entries dated 19:57:37 through 20:16:20; appended this correction at EOF without rewriting existing entries. `git diff --check` remains clean.
- Dependency / integration impact: None.
- Remaining work: Inspect the affected tenant's published BPM definition, start permissions, reviewer configuration, and preceding BPM exception log; any BPM import/publish or permission change requires separate confirmation.

### 2026-08-10 20:54:23 +08:00

- Branch: `main`
- Worktree: `D:\ZSJ-OS`
- HEAD commit: `d3459e2c064d02121315d233c0f52bfa0ec25d16`
- User goal: Diagnose the backend startup failure reporting that port `48080` is already in use.
- Key decisions: Read-only process and socket inspection; do not stop or reconfigure the existing service because it belongs to another worktree and external process-state changes require explicit confirmation.
- Result: Confirmed `48080` is listened to by PID `41208`, a responsive Java process started at 20:48:26 from `D:\ZSJ-OS-worktrees\lead-appeal-routing\backend\yudao-server\target\yudao-server.jar` with the local profile. The process has active connections and is not the current `D:\ZSJ-OS` worktree's failed startup.
- Changed files: `handoff/20260810-main-existing.md` only for this diagnostic entry.
- Verification: Read `Get-NetTCPConnection`, process metadata, full Java command line, active connections, and Git status. No process was stopped and no application test was run.
- Dependency / integration impact: None. The current worktree cannot bind `48080` while PID `41208` remains listening.
- Remaining work: Either reuse the running lead-appeal-routing backend, start the current worktree on another confirmed port, or separately confirm stopping PID `41208` before taking that action.

### 2026-08-10 21:16:30 +08:00

- Branch: `main`
- Worktree: `D:\ZSJ-OS`
- HEAD commit: `d3459e2c064d02121315d233c0f52bfa0ec25d16`
- User goal: Diagnose the continuing warning from `POST /admin-api/zsjos/lead/appeal/lead/8/submit` at `LeadAppealServiceImpl.java:121`.
- Key decisions: Read-only log and source inspection; do not alter code, BPM definitions, permissions, database, or running process state.
- Result: Application log at `C:\Users\EDY\logs\yudao-server.log` shows the request queried lead owner `231`, department `1021`, department leader `233`, permission menu `6806`, and attachment `2270`, then immediately emitted the warning. No appeal insert or BPM SQL appears before the warning. The failure therefore occurs during `resolveReviewers(1, lead)`, most likely because user `233` is disabled/missing or lacks `zsjos:lead:appeal:review-sales-manager`; BPM process creation is not reached for this request.
- Changed files: `handoff/20260810-main-existing.md` only.
- Verification: Inspected `LeadAppealServiceImpl.resolveReviewers`, BPM process creation validation, exact timestamp log context, and current listener PID (`4824`) without stopping it. No implementation or external-state verification was performed.
- Dependency / integration impact: None.
- Remaining work: In tenant `1`, verify department `1021` leader is enabled user `233` and that the user has the sales-manager appeal review permission. After that, retry submission; only if reviewer validation passes should the active BPM definition `zsjos_lead_appeal_review` and task `appealReview` be checked.

### 2026-08-11 09:40:00 +08:00

- Branch: `main`
- Worktree: `D:\ZSJ-OS`
- HEAD commit: `e826b4b277`
- User goal: Merge all active workstream branches into `main`.
- Key decisions: Committed the existing `main`, lead-appeal, and sales-order worktree changes separately; merged `codex/lead-appeal-routing` first and `codex/sales-order-dual-approval` second; resolved overlapping tests, schema/bootstrap V021 plus V023 checks, database tooling, and generated frontend metadata. The untracked lead-worktree `.env.local` was inspected by variable name only and deliberately excluded.
- Result: Both feature branches are merged into `main`; merge commits and the post-build metadata refresh are present. `main` is clean and ahead of `origin/main` by six commits.
- Changed files: Git history and conflict resolutions across the two feature workstreams; no additional product behavior was introduced during merge resolution.
- Verification: Merged-worktree ZSJOS focused tests passed 24/24; frontend tests passed 52/52; frontend production build passed; `git diff --check` passed; route registration includes both lead appeal and sales-order approval pages. Database check reports the expected missing `V022` gap in the current migration baseline.
- Dependency / integration impact: `V023` remains ordered after `V021` and requires `V022` integration before database continuity checks pass. No push, migration execution, BPM deployment, service restart, or branch deletion was performed.
- Remaining work: Integrate or provide `V022`, rerun database verification, and separately decide whether to retain or remove the merged feature branches. The lead-appeal worktree still contains its excluded local `.env.local` file.

### 2026-08-11 09:46:00 +08:00

- Branch: `main`
- Worktree: `D:\ZSJ-OS`
- HEAD commit: `1efda2d113`
- User goal: Diagnose Maven clean failure while deleting `backend/yudao-server/target/yudao-server.jar`.
- Key decisions: Read-only process and file inspection; do not stop the running server without separate confirmation.
- Result: PID `46276` is running `D:\ZSJ-OS\backend\yudao-server\target\yudao-server.jar` with `--spring.profiles.active=local`, listening on port `48080`; this is the direct cause of the clean-plugin deletion failure.
- Changed files: `handoff/20260810-main-existing.md` only.
- Verification: Confirmed process command line, JAR path/metadata, and PID network bindings. No process was stopped and no build rerun was performed.
- Dependency / integration impact: Stop the service cleanly, or terminate PID `46276` only after confirming it is safe, before rerunning Maven clean/package.
- Remaining work: Service shutdown and build retry require user/runtime confirmation.

### 2026-08-11 09:52:00 +08:00

- Branch: `main`
- Worktree: `D:\ZSJ-OS`
- HEAD commit: `1efda2d113`
- User goal: Resolve application startup failure caused by `configMapper` Bean injection into the sales-order controller dependency graph.
- Key decisions: Rename both `SalesOrderApprovalConfigMapper` resource fields to the unique bean name `salesOrderApprovalConfigMapper`; do not change proxy mode or global Spring configuration.
- Result: Removed the `@Resource` by-name collision with Infra `ConfigMapper` in `SalesOrderServiceImpl` and `SalesOrderObjectPermissionService`.
- Changed files: The two sales-order service classes and this handoff file.
- Verification: ZSJOS compile passed; focused lead-management and sales-order tests passed 24/24; source scan confirms no remaining sales-order `configMapper` injection. Runtime startup was not attempted.
- Dependency / integration impact: None beyond rebuilding/restarting `yudao-server` with the corrected classes.
- Remaining work: Rebuild and start the server; report the next deepest exception if startup still fails.

### 2026-08-11 10:05:39 +08:00

- Branch: `main`
- Worktree: `D:\ZSJ-OS`
- HEAD commit: `1efda2d113b27950dfbe6aa98d9c17e9fdd4617a`
- User goal: Explain how the lead appeal workflow and sales-order dual-center approval workflow should now be configured.
- Key decisions: Perform read-only repository and local-database inspection; do not change BPM definitions, roles, users, departments, menus, migrations, database records, or running services. Treat the business services and checked-in BPMN files as the workflow contract, and distinguish process-task assignment from menu discoverability.
- Execution or analysis result: Both BPMN definitions are integrated in `main` but have zero deployed process definitions locally. Lead appeals use one start-user-selected OR-sign task: round 1 resolves the lead owner's department leader, round 2 resolves enabled `quality_manager` and `quality_specialist` users, and round 3 requires exactly one enabled `boss` user. The local tenant has the sales-manager reviewer configured, but no enabled user assigned to the quality or boss roles. Sales-order approval uses parallel registration and finance task groups, with OR-sign inside each department pool and both centers required overall. The local approval configuration points to departments `1030` and `1040`, each with one enabled user in its subtree, but the approval menu has no role assignment. The schema-version table contains V023 without its declared V022 prerequisite, so further sales-order deployment must pause until that migration-order inconsistency is resolved.
- Changed files: `handoff/20260810-main-existing.md` only, as required for a read-only task delivery entry.
- Verification evidence: Inspected both BPMN files, appeal and sales-order service assignment logic, migrations V015 and V023, API and deployment documentation, Git/worktree state, and current local MySQL rows for schema versions, process definitions, approval configuration, departments, enabled user counts, role assignments, and menu grants. No tests were run because no implementation changed.
- Dependency or integration impact: Appeal deployment requires BPM model create/deploy authority plus complete tenant role and department-leader configuration. Sales-order deployment additionally depends on resolving the missing V022 baseline before treating V023 as valid; menu grants do not replace department-pool and BPM-task authorization.
- Remaining work: With separate explicit confirmation, assign at least one enabled quality reviewer, assign exactly one enabled boss, publish and verify the appeal BPMN, resolve the V022/V023 migration-order issue, confirm departments `1030/1040`, grant the sales-order approval menu to intended center users, publish the dual-approval BPMN, and run controlled end-to-end authorized and unauthorized workflow checks.

### 2026-08-11 10:15:05 +08:00

- Branch: `main`
- Worktree: `D:\ZSJ-OS`
- HEAD commit: `1efda2d113b27950dfbe6aa98d9c17e9fdd4617a`
- User goal: Review the Admin `工作流程` menu and determine how the ZSJOS appeal and sales-order approval processes should be imported into it.
- Key decisions: Treat this as read-only analysis; inspect the Admin menu seed, Vue model-management UI, BPM model APIs, Flowable model/deployment services, checked-in BPMN artifacts, current menu grants, and local runtime without changing configuration or starting services. Use the existing Yudao model-management boundary rather than proposing direct Flowable table writes or global auto-deployment.
- Execution or analysis result: The intended Admin route is `工作流程 -> 流程管理 -> 流程模型`. The page supports model creation, update, publish, history, export, state changes, cleanup, and a JSON-only `导入模型` action. The JSON import endpoint expects a complete `BpmModelSaveReqVO`, not a raw `.bpmn20.xml`. The frontend button requires `bpm:model:import`, but the current menu data has no such permission node and no user has that permission, while two users have the existing query/create/update/deploy set. Both ZSJOS BPMN files contain executable process semantics but no BPMNDiagram/BPMNShape/BPMNEdge data, so Flowable can deploy them but the Admin graphical designer cannot reliably open them as files. Deployment also requires model form metadata: either a valid platform form or valid custom create/view paths. These workflows are programmatically started by ZSJOS and should be hidden from Admin's generic manual-start list while retaining an all-users BPM start scope so the business services can start them after their own authorization checks.
- Changed files: `handoff/20260810-main-existing.md` only, as required for a read-only task delivery entry.
- Verification evidence: Inspected `frontend/admin` model list/import/editor/basic/form components and APIs, `BpmModelController`, `BpmModelServiceImpl`, process-definition visibility/start checks, system menu seed rows, current role-menu/user permissions, current Flowable model rows, BPMN XML structure, and local listener ownership. The Admin frontend itself was not running: port `3000` belongs to a different `D:\AI-CRM` Vite application, so no live Admin menu interaction was performed. No tests were run because no implementation changed.
- Dependency or integration impact: The checked-in deployment documentation currently says to import the BPMN files through Admin, but the actual Admin import contract is JSON-only and the XML files are not designer-ready; this documentation/implementation mismatch must be resolved before claiming controlled UI deployment. Direct database inserts or direct Flowable deployments would bypass model metadata and are not recommended.
- Remaining work: After explicit behavior-change confirmation, add and grant a scoped `bpm:model:import` menu permission, decide the model form strategy for system-started ZSJOS workflows, add BPMN diagram interchange metadata or produce complete Admin model JSON packages, update deployment documentation, then verify import, preview, publish, active-definition lookup, hidden manual-start behavior, and programmatic start in a controlled environment.

### 2026-08-11 10:40:13 +08:00

- Branch: `main`
- Worktree: `D:\ZSJ-OS`
- HEAD commit: `1efda2d113b27950dfbe6aa98d9c17e9fdd4617a`
- User goal: Add complete graphical coordinates to the two ZSJOS BPMN definitions and create their corresponding read-only BPM forms.
- Key decisions: Follow the repository's mandatory pre-change confirmation gate. Inspect form persistence, Admin form editing, BPM deployment validation, SQL/bootstrap conventions, migration continuity, current Git state, and the BPMN files before editing. Recommend a new additive V024 migration rather than assigning an unknown purpose to the missing V022, and keep local database execution outside the implementation confirmation because it changes external state.
- Execution or analysis result: The two BPMN files need BPMN DI namespaces plus one shape for every flow node and one edge with waypoints for every sequence flow. BPM deployment requires either a valid `bpm_form` row or custom Admin routes. The aligned approach is two enabled, tenant-owned, read-only platform forms containing only stable process reference variables: appeal (`appealId`, `leadId`, `roundNo`, `reviewStage`) and sales order (`orderId`, `leadId`, `roundNo`). No current `bpm_form` rows exist locally. Repository artifacts must cover a repeatable V024 migration, fresh bootstrap seeds, verification SQL, migration documentation, and the two deployment guides. The existing V022 gap means full migration-chain verification remains blocked until that separate inconsistency is resolved.
- Changed files: `handoff/20260810-main-existing.md` only, as required while awaiting pre-change confirmation.
- Verification evidence: Inspected both BPMN files; `bpm_form` schema, DO, Mapper, Service, Controller and Admin designer; model form validation; migration inventory and documentation; desired schema/bootstrap/verification artifacts; architecture ownership boundaries; Git/worktree state; and read-only local form data. No tests were run because implementation has not started.
- Dependency or integration impact: Planned changes stay within BPM-owned process/form artifacts and SQL initialization. They add no dependency and do not deploy a model, grant permissions, start services, or mutate the local database. V024 must not be executed in the current local database until the V022/V023 ordering issue has an approved resolution.
- Remaining work: Await confirmation of the stated scope, assumptions, non-goals, affected files, and verification plan; then implement and verify without executing database migrations or publishing BPM definitions.

### 2026-08-11 10:55:47 +08:00

- Branch: `main`
- Worktree: `D:\ZSJ-OS`
- HEAD commit: `1efda2d113b27950dfbe6aa98d9c17e9fdd4617a`
- User goal: Add complete graphical coordinates to the lead-appeal and sales-order dual-approval BPMN definitions, and create corresponding read-only BPM forms.
- Key decisions: Add BPMN DI only, without changing workflow semantics; create two enabled tenant-owned platform forms in additive V024 and the fresh bootstrap; store form fields in the repository's `List<String>` JSON-array contract; mark every input both disabled and readonly; use stable remark markers to preserve administrator edits and make reruns repeatable; keep model creation, form association, BPM publication, database execution, permissions, and service lifecycle outside this change. V024 remains after V023 rather than assigning a new meaning to the missing V022.
- Execution or analysis result: Both BPMNs now contain a shape for every flow node and an edge with waypoints for every sequence flow. V024 and fresh bootstrap create `客资申诉流程关联信息` (`appealId`, `leadId`, `roundNo`, `reviewStage`) and `成交会签流程关联信息` (`orderId`, `leadId`, `roundNo`) for each enabled tenant. Verification SQL checks version presence, one enabled form per marker and tenant, valid field counts, and readonly/disabled properties. Deployment guides now use Admin model creation followed by designer `打开文件`, associate the corresponding form, and explicitly reject the JSON-only model-list import path.
- Changed files: `script/bpm/zsjos_lead_appeal_review.bpmn20.xml`; `script/bpm/zsjos_sales_order_dual_approval.bpmn20.xml`; `script/sql/mysql/migrations/V024__zsjos_bpm_readonly_forms.sql`; `script/sql/mysql/02-bootstrap-zsjos-seed.sql`; `script/sql/mysql/verify-bootstrap.sql`; `script/sql/mysql/migrations/README.md`; `docs/operations/lead-appeal-deployment.md`; `docs/operations/sales-order-dual-approval-deployment.md`; `handoff/20260810-main-existing.md`. The pre-existing sales-order Java changes were inspected in Git status but not modified in this task.
- Verification evidence: Repository `bpmn-moddle` parsed both files without warnings. Flowable 8.0 `BpmnXMLConverter` parsed each as one process and reported appeal 3 graphic locations/2 flow routes and sales order 6 graphic locations/6 flow routes. Independent XML coverage checks found appeal 3 nodes/shapes and 2 flows/edges, and sales order 6 nodes/shapes and 6 flows/edges, with no missing DI elements and at least two waypoints per edge. Both SQL copies yielded five valid field JSON definitions, all with `disabled=true` and `readonly=true`; `git diff --check` passed. `zsjos_db.py check` reached the expected pre-existing continuity failure because V022 is absent from `[V001..V021,V023,V024]`. No database migration, bootstrap, BPM model creation/publication, service restart, permission change, or live UI action was performed.
- Dependency or integration impact: V024 requires the existing BPM form/system tenant tables and schema-version infrastructure, and must not be applied until the V022/V023 migration-order gap is resolved. Models remain system-started by ZSJOS and should be hidden from Admin's generic manual-start list while retaining an all-users start scope.
- Remaining work: Integrate the intended V022 migration, rerun the database continuity and controlled bootstrap/upgrade checks, then obtain separate confirmation before applying V024 or creating, associating, and publishing the two models in Admin. Perform controlled end-to-end workflow checks after publication.

### 2026-08-11 11:15:24 +08:00

- Branch: `main`
- Worktree: `D:\ZSJ-OS`
- HEAD commit: `1efda2d113b27950dfbe6aa98d9c17e9fdd4617a`
- User goal: Explain how to import the two completed BPMN workflows into Admin.
- Key decisions: Provide the existing Admin model-creation and designer file-open procedure; distinguish BPMN file loading from the model-list JSON import action; do not execute migrations, create or publish models, change permissions, or restart services.
- Execution or analysis result: The operator must first make the V024 forms available, then create one BPMN model per workflow under `工作流程 -> 流程管理 -> 流程模型`, associate its corresponding read-only platform form, load the `.bpmn20.xml` through the process designer's `打开文件` action, save, publish, and verify process/task keys. The top-level `导入模型` action is not applicable because it accepts a complete JSON model package rather than raw BPMN XML.
- Changed files: `handoff/20260810-main-existing.md` only.
- Verification evidence: Based on the previously inspected Admin model UI/API contract, deployment documentation, completed BPMN files, and V024 form definitions. No new runtime or database checks were required for this explanatory turn.
- Dependency or integration impact: V024 must not be applied while the V022/V023 migration-order gap remains unresolved. Workflow publication remains an external-state change requiring explicit confirmation.
- Remaining work: Resolve V022, apply V024 in a controlled environment, then follow the documented Admin steps for both models and run end-to-end workflow verification.

### 2026-08-11 11:29:12 +08:00

- Branch: `main`
- Worktree: `D:\ZSJ-OS`
- HEAD commit: `1efda2d113b27950dfbe6aa98d9c17e9fdd4617a`
- User goal: Diagnose why submitting the appeal for lead `8` fails at `LeadAppealServiceImpl.java:121`.
- Key decisions: Perform read-only source, runtime, and local-database inspection; do not edit product code, redeploy BPM, restart the running service, submit another appeal, or change database state. Avoid exposing datasource credentials or request payloads.
- Execution or analysis result: The failure occurs after reviewer resolution and appeal insertion, inside BPM process-instance creation; the enclosing transaction rolls the appeal insert back. The active tenant-`1` Flowable definition `zsjos_lead_appeal_review` version 1 exists and is active, has matching `bpm_process_definition_info`, is invisible but startable by all users, and references enabled form `41`. The deployed BPMN bytes exactly match the checked-in file by size and SHA-256 and contain task `appealReview`, candidate strategy `35`, and multi-instance configuration. Therefore the observed failure is not explained by an absent/suspended definition, tenant mismatch, missing form, restricted start scope, or a stale deployed BPMN. `LeadAppealServiceImpl` catches every runtime exception from BPM creation and replaces it with `LEAD_APPEAL_PROCESS_UNAVAILABLE` without logging or preserving the cause, so the supplied log cannot reveal whether the remaining failure is approval-detail validation, assignee validation, expression evaluation, or Flowable task creation.
- Changed files: `handoff/20260810-main-existing.md` only.
- Verification evidence: Inspected `LeadAppealServiceImpl` submit/reviewer logic, BPM process-instance creation and start authorization, candidate strategy and multi-instance behaviors, the running server process on port `48080`, and read-only local MySQL rows in `ACT_RE_PROCDEF`, `ACT_RE_MODEL`, `ACT_GE_BYTEARRAY`, `bpm_process_definition_info`, `bpm_form`, and schema versions. Definition ID is `zsjos_lead_appeal_review:1:6cfb86b5-9533-11f1-b61d-8c32236cc8cf`; deployed BPMN SHA-256 is `47934b196416db1183878282280a5cc0b129bccdbc3984c70797a1f40508d8e2`, identical to the repository file. No matching persisted API error-log row exists because the original exception is converted to a service exception.
- Dependency or integration impact: Exact root-cause capture requires a narrowly scoped observability change in `LeadAppealServiceImpl`, followed by rebuild/restart and one controlled retry. Restarting the current service and resubmitting are external-state actions requiring explicit confirmation.
- Remaining work: After confirmation, log the caught BPM exception with lead, appeal, round, process key and task key but no sensitive request data; compile and run focused tests; rebuild/restart the local server if separately authorized; retry once and then fix the revealed BPM runtime cause.

### 2026-08-11 11:38:32 +08:00

- Branch: `main`
- Worktree: `D:\ZSJ-OS`
- HEAD commit: `1efda2d113b27950dfbe6aa98d9c17e9fdd4617a`
- User goal: Review whether first-round appeal approval should resolve the lead owner and then the owner's department supervisor, and whether `leadId` belongs in the form.
- Key decisions: Treat `leadId` in the read-only form as display/process context only; the server-side lead record remains authoritative for reviewer resolution. The existing first-round chain is conceptually correct: lock/read the lead, load `ownerUserId`, load the owner's department, then require that department's enabled `leaderUserId` with the sales-manager appeal permission. Do not edit code during this analysis turn.
- Execution or analysis result: `submit(leadId, ...)` calls `requireLeadForUpdate(leadId)` and `resolveReviewers(1, lead)`, then passes the resolved reviewer IDs to BPM via the start-user-select map. The form's `leadId` variable is not trusted for authorization. However, `canReview()` later recomputes the current lead owner's department leader rather than validating the reviewer selected at submission; ownership changes during a pending appeal can therefore cause an authorization mismatch. A robust design should snapshot the owner/department/supervisor used for each appeal round and authorize the BPM task against that snapshot.
- Changed files: `handoff/20260810-main-existing.md` only.
- Verification evidence: Inspected `LeadAppealServiceImpl` submit, reviewer resolution, and decision authorization paths; `LeadDO.ownerUserId`; BPM start-user-select variable handling; and the read-only form variables. No tests or database writes were performed.
- Dependency or integration impact: Fixing the snapshot mismatch would affect the appeal business record/schema, submit transaction, decision authorization, and focused tests; it is a behavior change requiring confirmation. The current form can retain `leadId` for display and audit context without making it an input source.
- Remaining work: Confirm whether supervisor identity must be frozen at appeal submission. If confirmed, design the smallest snapshot fields and migration, then separately address the swallowed BPM exception that currently prevents exact diagnosis of the failed submission.

### 2026-08-11 11:34:53 +08:00

- Branch: `main`
- Worktree: `D:\ZSJ-OS`
- HEAD commit: `1efda2d113b27950dfbe6aa98d9c17e9fdd4617a`
- User goal: Determine how a salesperson can view orders they submitted and their approval status in Workbench.
- Key decisions: Inspect the current Workbench routes, sales-order APIs, object authorization, lead status presentation, and approval data model read-only; do not implement the missing sales-facing order view without the repository's pre-change confirmation.
- Execution or analysis result: Current Workbench has an approver-only `成交审批` page backed by the BPM todo/done inbox, but no sales-facing `我的成交` route or API. Sales can only find the originating lead under `我负责的客资` and infer overall state from `成交待审核` or `已成交`; when rejected, `补正成交` appears and opens the active order. Submission returns an order ID, and the existing detail endpoint permits the submitter or current lead owner to read it, but the UI discards that ID after closing and exposes no list/detail action while approval is pending. The current response/data model exposes overall order and round status, not separate registration-center and finance-center decisions for a salesperson.
- Changed files: `handoff/20260810-main-existing.md` only.
- Verification evidence: Inspected Workbench routing and menu mapping, `LeadManagementPage`, `SalesOrderEntryModal`, `SalesOrderApprovalPage`, API types/client, `SalesOrderController`, `SalesOrderServiceImpl`, `SalesOrderMapper`, `SalesOrderObjectPermissionService`, response/page VOs, approval-round data object, and V023 menu seeds.
- Dependency or integration impact: A complete solution needs a submitter-scoped paginated business API and a server-owned menu-backed Workbench page. Per-center progress should be derived from BPM task/history through its public API rather than direct Flowable-table access or invented frontend state.
- Remaining work: After explicit implementation confirmation, define the sales-facing list/detail contract and status semantics, add `我的成交` navigation/permission through a numbered migration, implement the backend query and Workbench states, and verify submitter isolation, loading/empty/error/unauthorized cases, responsive UI, typecheck, tests, build, and browser behavior.

### 2026-08-11 12:14:06 +08:00

- Branch: `main`
- Worktree: `D:\ZSJ-OS`
- HEAD commit: `1efda2d113b27950dfbe6aa98d9c17e9fdd4617a`
- User goal: Implement the approved Workbench “我的订单” page and refactor “成交审批” into the shared inbox/card-detail experience, including submitter-scoped APIs, rejection-reason snapshots, V025 menu/permission migration, responsive UI, tests, and documentation.
- Key decisions: Keep the three existing business statuses; scope personal lists and counts strictly by `submitter_user_id`; add a separate `query-own` detail endpoint with `read-own` object authorization; retain current-owner-only correction authorization and expose `canRevise` so transferred orders remain visible without showing an unusable correction action; return lightweight list rows and fetch full details only after selection; key approval rows by BPM task ID so the two center tasks for one order remain distinct; preserve the real-time configured-department approval pool; generate but do not execute V025 while V022 is missing.
- Execution or analysis result: Added personal order page/count/detail APIs, lightweight approval inbox responses, BPM task context fields, final non-approval reason snapshots, and focused service coverage. Added V025 with the personal-order menu, copied grants from existing “录入成交” role-menu relationships, final menu ordering, submitter query index, fresh-schema/bootstrap/verification synchronization, and deployment/API documentation. Added the Workbench “我的订单” inbox with counts, status tabs, keyword search, incremental paging, stale-response protection, categorized details, mobile drawer, and correction flow. Rebuilt “成交审批” on the same list/detail cards with todo/done views, center labels, approval actions, and handled result/reason/time.
- Changed files: Sales-order controller/VO/service/mapper/permission/data-object/test files under `backend/yudao-module-zsjos`; `frontend/workbench/src/components/SalesOrderDetailCards.tsx`, `SalesOrderEntryModal.tsx`, `pages/MySalesOrderPage.tsx`, `pages/SalesOrderApprovalPage.tsx`, routing/constants/API/helpers/tests/styles and generated `tsconfig.tsbuildinfo`; `script/sql/mysql/migrations/V025__sales_order_workbench_views.sql`, Core schema/baseline/bootstrap/verification/tooling/migration documentation; `docs/api/zsjos-sales-order.md`, `docs/architecture/data-and-permission-flow.md`, `docs/operations/sales-order-dual-approval-deployment.md`; this handoff entry. Pre-existing BPMN/V024/appeal-deployment and bean-name changes were preserved.
- Verification evidence: ZSJOS compile passed. Focused `SalesOrderServiceImplTest` passed 10 tests with 0 failures/errors. Workbench passed 54 tests across 12 files, `npm run typecheck`, and production build; build emitted only the existing large-chunk warning. `git diff --check` passed. Browser checks used a temporary localhost-only mock API because V025 is intentionally unapplied: at 1440x900 the list/detail panes had no horizontal overflow; at 390x844 the detail drawer showed all six card groups, long rejection text wrapped, voucher rendered, and approval buttons occupied separate non-overlapping columns; the handled view retained both registration and finance tasks for one order. The temporary mock file, logs, browser tabs, and ports 48081/5175/5176 were cleaned up. `zsjos-db check` reached the expected continuity failure for `[V001..V021,V023,V024,V025]` because V022 is absent.
- Dependency or integration impact: No new npm/Maven dependency and no change to dual-center BPM ownership or department authorization. V025 is additive, deletes no rows, and must follow the eventual V022 resolution plus V023/V024. No migration, bootstrap, BPM publication, permission mutation, backend restart, commit, push, or deployment was performed.
- Remaining work: Resolve and integrate V022, rerun database continuity plus controlled fresh/upgrade verification, then separately approve and apply V023/V024/V025 in order. Rebuild/restart the backend and verify real authenticated API responses for tenant/submitter isolation, transferred-order visibility, unauthorized detail access, correction eligibility, both center task lists, rejection reason persistence, and end-to-end resubmission/approval. The production bundle remains above Vite's 500 kB chunk warning threshold.

### 2026-08-11 13:34:14 +08:00

- Branch: `main`
- Worktree: `D:\ZSJ-OS`
- HEAD commit: `1efda2d113b27950dfbe6aa98d9c17e9fdd4617a`
- User goal: Implement the confirmed lead-appeal reviewer optimization: freeze reviewer resolution at submission, resolve the first-round reviewer through the lead owner's nearest valid supervisor, authorize decisions against BPM assignment plus the immutable reviewer snapshot and enabled account, retain legacy BPM-assignee compatibility without backfill, and log the original BPM startup exception.
- Key decisions: Resolve the owner and owner department from the server-owned lead at submission; walk parent departments with cycle protection until finding a leader who is not the owner, has an enabled account, and holds the sales-manager appeal-review permission; freeze owner, department, reviewer department, and reviewer IDs per appeal round; do not recalculate organization or roles during approval; treat only a `NULL` reviewer snapshot as a legacy row and fail closed for blank, empty, or invalid snapshots; keep the external BPM failure stable while logging the original exception without request evidence, reasons, or reviewer IDs.
- Execution or analysis result: Added four reviewer-context snapshot fields and repeatable V026 migration/baseline synchronization. Submission now freezes reviewer context and supplies the same reviewer IDs to BPM. Inbox and decision checks require an enabled user and snapshot membership, while legacy null snapshots rely on the already user-scoped BPM task API. First-round resolution climbs past the owner, disabled leaders, leaders without permission, and department cycles. Bootstrap verification now accepts historical null snapshots but rejects non-null invalid or empty snapshots. Deployment guidance documents the resolution and compatibility rules.
- Changed files: `backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/dal/dataobject/lead/LeadAppealDO.java`; `backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/lead/LeadAppealServiceImpl.java`; `backend/yudao-module-zsjos/src/test/java/cn/iocoder/yudao/module/zsjos/service/lead/LeadAppealServiceImplTest.java`; `script/sql/mysql/migrations/V026__lead_appeal_reviewer_snapshot.sql`; reviewer-snapshot sections of `script/sql/mysql/00-bootstrap-schema.sql`, `script/sql/mysql/schema/core.sql`, and `script/sql/mysql/verify-bootstrap.sql`; `script/sql/mysql/migrations/README.md`; `docs/operations/lead-appeal-deployment.md`; this handoff entry. Unrelated sales-order, frontend, V024, and V025 worktree changes were preserved.
- Verification evidence: Focused `LeadAppealServiceImplTest` passed 8/8 with module compilation and reactor success. All other lead tests except the independently broken `LeadFollowUpRuleServiceImplTest` passed 81/81. The full `Lead*Test` run exposed that existing test's stale fixture: it omits the now-required `qualificationTimeoutMinutes` and fails before any appeal behavior. `git diff --check` passed. `python script/sql/mysql/tools/zsjos_db.py check` reached the expected pre-existing continuity failure for `[V001..V021,V023,V024,V025,V026]` because V022 is absent.
- Dependency / integration impact: No new dependency. V026 is additive and does not backfill historical appeals. It must not be applied until the V022 through V025 sequence is resolved. No database migration, bootstrap, BPM model deployment, service restart, permission change, or real appeal retry was performed.
- Remaining work: Resolve and integrate V022, repair the unrelated follow-up-rule test fixture in its own scope, then obtain separate confirmation to apply V026, rebuild/restart the backend, and perform controlled authorized, unauthorized, ownership-transfer, legacy-task, and BPM-start-failure HTTP checks before retrying the real appeal.

### 2026-08-11 13:45:00 +08:00

- Branch: `main`
- Worktree: `D:\ZSJ-OS`
- HEAD commit: `1efda2d113b27950dfbe6aa98d9c17e9fdd4617a`
- User goal: Diagnose the latest `POST /admin-api/zsjos/lead/appeal/lead/8/submit` response `1900003043`.
- Key decisions: Read the supplied server stack trace and related BPM source only. Do not edit product code, restart the backend, apply migrations, publish BPM, or retry the real appeal during this diagnostic turn.
- Execution or analysis result: The BPM definition lookup and reviewer resolution succeeded. `LeadAppealServiceImpl` passes `Map.of("appealId", ..., "leadId", ..., "roundNo", ..., "reviewStage", ...)` to the BPM API. `BpmProcessInstanceServiceImpl.createProcessInstance0` calls `FlowableUtils.filterProcessInstanceFormVariable`, which removes a system variable from that map. Because `Map.of` is immutable, `remove()` throws `UnsupportedOperationException`; the service catches it and correctly maps it to the generic `LEAD_APPEAL_PROCESS_UNAVAILABLE` response. The failure is therefore a mutable-map contract mismatch, not a missing process definition, form, or reviewer.
- Changed files: `handoff/20260810-main-existing.md` only.
- Verification evidence: Supplied log shows `FlowableUtils.java:177` as the cause and `BpmProcessInstanceServiceImpl.java:811` as the caller; source inspection confirms the immutable `Map.of` construction at `LeadAppealServiceImpl.java:123`. No runtime state was changed.
- Dependency / integration impact: The minimal fix is to construct a mutable `LinkedHashMap` for process variables before calling the BPM public API. It adds no dependency and preserves the process variables and reviewer assignment contract.
- Remaining work: Await explicit confirmation to implement the mutable-map fix, then run the focused appeal tests and compile. A real retry and service restart remain separate runtime actions.

### 2026-08-11 13:47:00 +08:00

- Branch: `main`
- Worktree: `D:\ZSJ-OS`
- HEAD commit: `1efda2d113b27950dfbe6aa98d9c17e9fdd4617a`
- User goal: Apply the confirmed fix for the appeal submission `1900003043` BPM startup error.
- Key decisions: Keep the BPM public API and process variables unchanged; change only the variable-map mutability contract and add a focused regression assertion. Do not restart the running service or retry the real appeal in this turn.
- Execution or analysis result: Replaced the immutable `Map.of` process-variable construction in `LeadAppealServiceImpl` with a mutable `LinkedHashMap`, allowing BPM's existing system-variable filter to remove entries safely. Added a test assertion that the submitted variables accept `remove("appealId")`.
- Changed files: `backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/lead/LeadAppealServiceImpl.java`; `backend/yudao-module-zsjos/src/test/java/cn/iocoder/yudao/module/zsjos/service/lead/LeadAppealServiceImplTest.java`; this handoff entry.
- Verification evidence: `mvn -f backend/pom.xml -pl yudao-module-zsjos -am -Dtest=LeadAppealServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false test` passed 8/8 with reactor/module build success. No database, HTTP, BPM deployment, or runtime retry was performed.
- Dependency / integration impact: No new dependency and no schema change. The running service must be rebuilt/restarted before this fix is present at `localhost:48080`.
- Remaining work: Rebuild/restart the backend under explicit runtime approval, then perform one controlled retry of lead 8 and inspect the resulting BPM task/appeal state.

### 2026-08-11 13:55:00 +08:00

- Branch: `main`
- Worktree: `D:\ZSJ-OS`
- HEAD commit: `1efda2d113b27950dfbe6aa98d9c17e9fdd4617a`
- User goal: Diagnose why the sales supervisor's appeal decision “维持无效” appeared to have no response.
- Key decisions: Read the supplied log and BPM/ZSJOS decision path only. Do not change user data, BPM shared behavior, restart the service, or retry the real decision during this diagnostic turn.
- Execution or analysis result: The request was `PUT /admin-api/zsjos/lead/appeal/13/uphold` with reason `无效`. BPM task `906f4032-9548-11f1-9603-8c32236cc8cf` reached result `3` and the process-completion path ran. During `BpmMessageServiceImpl.sendMessageWhenProcessInstanceReject`, SMS delivery to the process starter user `230` failed because that admin account has no mobile number (`手机号不存在`, code `1002013000`). The exception propagated through Flowable command completion before `LeadAppealServiceImpl.decide` could update the ZSJOS appeal row, so the client observed no successful response. The deprecation warning about `@Valid` on the attachment list is unrelated.
- Changed files: `handoff/20260810-main-existing.md` only.
- Verification evidence: Log stack trace reaches `BpmProcessInstanceServiceImpl.processProcessInstanceCompleted` and `BpmMessageServiceImpl.sendMessageWhenProcessInstanceReject`; source inspection confirms SMS is called inline and is not isolated from the BPM transaction, while ZSJOS persists the appeal decision only after `processTaskApi.rejectTask` returns. No runtime state was queried or changed.
- Dependency / integration impact: Operationally adding a mobile number to user 230 is the narrowest configuration fix. Making BPM notification failure non-blocking is a shared BPM behavior change affecting all approval workflows and requires separate confirmation plus cross-module tests.
- Remaining work: Obtain the user's choice between correcting user 230's mobile data and implementing non-blocking BPM notification errors, then run a controlled retry and verify both BPM task completion and ZSJOS appeal status.

### 2026-08-11 14:16:00 +08:00

- Branch: `main`
- Worktree: `D:\ZSJ-OS`
- HEAD commit: `1efda2d113b27950dfbe6aa98d9c17e9fdd4617a`
- User goal: Implement the confirmed configurable multi-channel notification plan.
- Key decisions: Preserve legacy null channels as `in_app`; support fixed channel codes `in_app`, `websocket`, `wecom`, and `sms`; process notification events asynchronously after commit; isolate BPM SMS failures from workflow transactions; keep external channels inert until tenant adapters/configuration are explicitly enabled.
- Execution or analysis result: Added channel fields and validation to notification rule/template APIs and persistence objects, asynchronous post-commit event handling, channel filtering in the processor, non-blocking BPM SMS wrapper, admin rule-page channel selection/display/template filtering, and additive V027 schema for channel columns, tenant channel configuration, and idempotent delivery records. Fresh bootstrap/core schema now include channel columns.
- Changed files: notification system API/DO/VO/service/listener/processor files; `backend/yudao-module-bpm/src/main/java/cn/iocoder/yudao/module/bpm/service/message/BpmMessageServiceImpl.java`; `frontend/admin/src/api/system/notify/rule/index.ts`; `frontend/admin/src/views/system/notify/rule/index.vue`; `script/sql/mysql/migrations/V027__configurable_notification_channels.sql`; notification table sections of `script/sql/mysql/00-bootstrap-schema.sql` and `script/sql/mysql/schema/core.sql`; this handoff entry.
- Verification evidence: `mvn -pl yudao-module-system,yudao-module-bpm -am -DskipTests compile` passed. `git diff --check` passed. `vue-tsc --noEmit` with increased Node heap reports only pre-existing unrelated errors in BPM designer, CRM funnel, MES workstation, and user form files; no notification-page error was reported.
- Dependency / integration impact: No new dependency. V027 is additive and must not be executed until migration continuity (including the pre-existing missing V022) is resolved. No migration, service restart, BPM deployment, HTTP retry, commit, or push was performed.
- Remaining work: Implement concrete delivery adapter/service and admin tenant channel-configuration and delivery-status pages before enabling WeCom/SMS; add delivery retry tests and runtime verification after controlled deployment.

### 2026-08-11 14:23:00 +08:00

- Branch: `main`
- Worktree: `D:\ZSJ-OS`
- HEAD commit: `1efda2d113b27950dfbe6aa98d9c17e9fdd4617a`
- User goal: Confirm offline WebSocket behavior and retain the existing WebSocket plus in-app binding.
- Key decisions: Keep durable in-app persistence as the source of message history; WebSocket remains an AFTER_COMMIT best-effort push tied to message creation. An offline or failed WebSocket delivery must not remove or roll back the stored message.
- Execution or analysis result: Removed the temporary direct-WebSocket branch from the business event processor so `websocket` rules use the existing durable-message lifecycle again. Confirmed the WebSocket listener catches send failures after message commit.
- Changed files: `backend/yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/service/notify/NotifyBusinessEventProcessor.java`; this handoff entry.
- Verification evidence: `mvn -pl yudao-module-system -am -DskipTests compile` passed.
- Dependency / integration impact: No new dependency or runtime state change. Service restart is required before the code is active; no restart or real notification retry was performed.
- Remaining work: None for the offline-history behavior. The broader WeCom/SMS adapter, tenant credential UI, and delivery retry worker remain intentionally unimplemented and disabled.

### 2026-08-11 14:45:00 +08:00

- Branch: `main`
- Worktree: `D:\ZSJ-OS`
- HEAD commit: `1efda2d113b27950dfbe6aa98d9c17e9fdd4617a`
- User goal: Implement the approved message-center integration plan for tenant-scoped SMS and WeCom channels.
- Key decisions: Keep WebSocket bound to durable in-app message creation; introduce a channel adapter contract; route BPM notifications through `NotifyBusinessEvent`; reuse existing `SmsSendApi`; keep WeCom inert until tenant credentials are implemented; add explicit administrator WeCom userid and channel-specific template metadata.
- Execution or analysis result: Added adapter/context/result contracts, SMS and guarded WeCom adapters, external-channel dispatch in the business event processor, BPM scene publication/provider, administrator WeCom userid API/model/UI fields, SMS-template and WeCom-message template metadata, and repeatable V028 schema metadata. Removed direct BPM SMS calls. Existing in-app rules continue to persist messages and trigger WebSocket after commit; legacy standalone websocket rules are ignored to prevent duplicate messages.
- Changed files: system notification API/service/template files; BPM message service and scene provider; administrator user DO/VO/form/API; V028 and bootstrap/core schema notification/user sections; this handoff entry.
- Verification evidence: `mvn -pl yudao-module-system,yudao-module-bpm -am -DskipTests compile` passed. `git diff --check` passed.
- Dependency / integration impact: No new dependency. No migration, service restart, BPM deployment, credential change, or real external send was performed. V028 remains blocked by the pre-existing missing V022 migration continuity issue.
- Remaining work: The plan is not yet feature-complete: implement encrypted tenant channel-config CRUD, concrete WeCom HTTP/token client, delivery DO/service/controller/retry scheduler and admin delivery/config pages, then add focused retry/isolation/BPM tests and browser verification.
