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
