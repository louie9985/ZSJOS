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

### 2026-08-12 12:35:43 +08:00

- Branch: `main`
- Worktree: `D:\ZSJ-OS`
- HEAD commit: `eaae3965805dc493cc6ad34aabede623843cb540`
- User goal: Prevent future Sales Department 2/3 supervisors from seeing peer-department employees or leads while allowing department and parent-department leaders to see their managed teams.
- Key decisions: Resolve team scope from current System department leader relationships and child departments, never role/post names; keep submitted/owned inboxes strictly personal; retain `zsjos:lead:query-all` as the explicit current-tenant bypass; use one ZSJOS scope service for list, count, detail and filter-user behavior; extend the System public API instead of accessing System DAL from ZSJOS.
- Execution or analysis result: General lead pages and status counts now include only the current user's related leads plus leads owned by managed department users; detail checks allow the owner's department or parent-department leader and reject peer leaders; `GET /zsjos/lead/visible-users` replaces the global System user dropdown and returns enabled users in the same scope, while `query-all` returns all enabled current-tenant users. Existing user changes in the Vue page and permission architecture document were preserved and merged.
- Changed files: `backend/yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/api/user/AdminUserApi.java`; `backend/yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/api/user/AdminUserApiImpl.java`; `backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/lead/LeadManagementController.java`; `backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/dal/mysql/lead/LeadMapper.java`; `backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/lead/LeadManagementService.java`; `backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/lead/LeadManagementServiceImpl.java`; `backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/lead/LeadObjectPermissionService.java`; `backend/yudao-module-zsjos/src/test/java/cn/iocoder/yudao/module/zsjos/service/lead/LeadManagementServiceImplTest.java`; `backend/yudao-module-zsjos/src/test/java/cn/iocoder/yudao/module/zsjos/service/lead/LeadObjectPermissionServiceTest.java`; `frontend/admin/src/api/zsjos/leadManagement/index.ts`; `frontend/admin/src/views/zsjos/lead/index.vue`; `docs/architecture/data-and-permission-flow.md`; `handoff/20260810-main-existing.md`.
- Verification evidence: Reactor Maven method-level run passed 17 permission tests with 0 failures/errors, including peer-department rejection, parent-department access, scoped list/count and visible-user behavior; the 20-module reactor compiled successfully. Admin `pnpm build:prod` passed. `git diff --check` passed. Browser checked the running local Admin login at 1280x720 and 390x844 with no horizontal overflow; the authenticated lead page was not exercised because no browser login session was available. Full Admin `pnpm ts:check` remains blocked by five pre-existing errors in BPM designer, CRM funnel, MES workstation and System user files. Running both complete focused test classes also exposes the pre-existing `detailProjectsOwnerActionsForEachLifecycleStage` invalid-lead action expectation mismatch; all 9 object-permission tests and the task-specific method set pass.
- Dependency or integration impact: Adds one read-only System public API method and one ZSJOS HTTP endpoint; no dependency, schema, migration, seed data, role grant, real account permission, branch, commit, push or service configuration change.
- Remaining work: Rebuild/restart the backend in an authorized environment, configure distinct Sales Department 1/2 leaders, and execute authenticated HTTP cases for both peer supervisors plus a sales-center parent leader. Separately resolve the existing Admin type errors and invalid-lead action test if full-suite green status is required.

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

### 2026-08-11 16:47:59 +08:00

- Branch: `main`
- Worktree: `D:\ZSJ-OS`
- HEAD commit: `6417d6daba30bf53011ce2711557674045fb5ef5`
- User goal: Fix the Workbench login Network Error and consolidate duplicate port 5174 development servers.
- Key decisions: Use the existing same-origin `/admin-api` Vite proxy to backend port 48080; keep Admin port 80 and backend port 48080 unchanged; replace both old Workbench Vite instances with one strict port 5174 process.
- Execution or analysis result: Changed the local Workbench API base from the inactive absolute port 48081 to `/admin-api`, stopped the duplicate Vite processes, and started one hidden Workbench Vite instance on `0.0.0.0:5174`.
- Changed files: Untracked local configuration `frontend/workbench/.env.local`; `handoff/20260810-main-existing.md`.
- Verification evidence: `OPTIONS http://127.0.0.1:5174/admin-api/system/auth/login` returned HTTP 204 through the proxy; port 5174 has exactly one listener (PID 30688); backend port 48080 remains listening; port 48081 is not listening; no `48081` reference remains in Workbench environment/source files.
- Dependency / integration impact: Local development configuration and process state only. No production configuration, dependency, database, permission, backend port, commit, or push changed.
- Remaining work: None. The local `.env.local` intentionally remains untracked.

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

### 2026-08-11 15:41:22 +08:00

- Branch: `main`
- Worktree: `D:\ZSJ-OS`
- HEAD commit: `a02d1012bba4c1d3d75e016a2539e6788f57e8a0`
- User goal: Correct the repository handoff rule so every turn that adds, deletes, or modifies any file writes a handoff entry, while turns that make no file changes do not.
- Key decisions: Use repository file mutation as the sole trigger; include code, tests, scripts, SQL, configuration, documentation, and repository rules; keep read-only discussion, analysis, diagnosis, inspection, review, and explanation turns out of the handoff log.
- Execution or analysis result: Updated the handoff rule from an unconditional per-turn requirement to a file-change requirement and clarified that all file types are covered. Retained the existing workstream registration and ownership rules for all files.
- Changed files: `AGENTS.md`; `handoff/20260810-main-existing.md`.
- Verification evidence: `git diff --check` passed before the handoff append; targeted rule search confirmed the file-change trigger, explicit add/delete/modify scope, and no-change exclusion. A final diff check is required after this entry.
- Dependency / integration impact: Repository workflow policy only. No application behavior, dependency, branch, worktree, commit, push, or external state changed.
- Remaining work: None.

### 2026-08-11 17:02:48 +08:00

- Branch: `main`
- Worktree: `D:\ZSJ-OS`
- HEAD commit: `6417d6daba30bf53011ce2711557674045fb5ef5`
- User goal: Audit Workbench forms so Ant Design's visible English `optional` and default `Cancel` text are Chinese, and show “手机号、微信号必填其中一个” as a persistent form hint rather than a validation error.
- Key decisions: Configure the existing global `ConfigProvider` with Ant Design `zh_CN`; keep `requiredMark="optional"` and `onCancel` as internal API identifiers; place the contact requirement in the mobile field's persistent `extra` text; use “请填写手机号或微信号” only for missing-contact validation.
- Execution or analysis result: Global Ant Design defaults now render “（可选）” and “取消”. Lead submission, lead basic-information editing, and sales-order entry display the contact requirement persistently. Basic-information and sales-order forms now validate the mobile/WeChat pair symmetrically.
- Changed files: `frontend/workbench/src/components/Theme/ThemeProvider.tsx`; `frontend/workbench/src/pages/LeadSubmissionPage.tsx`; `frontend/workbench/src/components/LeadBasicInfoModal.tsx`; `frontend/workbench/src/components/SalesOrderEntryModal.tsx`; `frontend/workbench/src/services/salesOrder.ts`; `frontend/workbench/src/services/salesOrder.test.ts`; this handoff entry.
- Verification evidence: Workbench `npm test` passed 56/56; `npm run typecheck` passed; `npm run build` passed with the existing bundle-size warning; locale inspection confirmed modal `cancelText` is “取消” and form optional text is “（可选）”; targeted source search found no old contact message or visible hard-coded `Cancel`; `git diff --check` passed before this entry. Browser checks at desktop and 390x844 were attempted, but the current account redirects `/zsjos/leads/submit` to `/zsjos/appeals` because it lacks form-page permission.
- Dependency / integration impact: No new dependency, API, schema, service, port, branch, worktree, commit, or external-state change. Existing `.env.local` and prior handoff edits were preserved.
- Remaining work: Recheck the three forms in a browser with an account authorized for客资提交、客资基础信息编辑和成交录入; implementation checks are otherwise complete.

### 2026-08-11 18:02:26 +08:00

- Branch: `main`
- Worktree: `D:\ZSJ-OS`
- HEAD commit: `6417d6daba30bf53011ce2711557674045fb5ef5`
- User goal: Make newly assigned leads open the Workbench assignment modal immediately without requiring the user to open the message center.
- Key decisions: Treat `assigned`, `reassigned`, and `transferred` WebSocket events as modal-opening signals; focus and undefer the event lead; query immediately and retry after 300 ms and 900 ms; keep the existing business-overlay exclusion and 15-second polling fallback; accept only the latest pending-list response; refresh once whenever WebSocket reconnects.
- Execution or analysis result: The global assignment host now consumes the assignment event payload directly, performs a targeted retry sequence until the assigned lead is queryable, prevents older requests from overwriting newer state, and refreshes after WebSocket connection recovery. Other assignment event types still refresh the queue without forcing a modal. Message-center navigation remains an optional fallback rather than a prerequisite.
- Changed files: `frontend/workbench/src/components/LeadAssignmentHost.tsx`; `frontend/workbench/src/services/leadAssignment.ts`; `frontend/workbench/src/services/leadAssignment.test.ts`; this handoff entry.
- Verification evidence: Workbench focused tests passed 10/10; complete `npm test` passed 58/58; `npm run typecheck` passed; `npm run build` passed with the existing bundle-size warning; `git diff --check` passed before this entry. Browser smoke testing confirmed the local login surface loads and has no horizontal overflow at 390x844, but a real assignment modal could not be exercised because the isolated browser session had no authenticated account or seeded assignment.
- Dependency / integration impact: No new dependency, backend behavior, API, schema, database data, service configuration, branch, worktree, commit, push, or external-state change. Existing unrelated Workbench and local environment changes were preserved.
- Remaining work: Perform one authenticated end-to-end automatic assignment and one specified assignment to verify live WebSocket delivery and modal opening in the deployed runtime.

### 2026-08-11 18:52:02 +08:00

- Branch: `main`
- Worktree: `D:\ZSJ-OS`
- HEAD commit: `6417d6daba30bf53011ce2711557674045fb5ef5`
- User goal: Implement independent PC/mobile login-device limits and an administrator-configurable password-free refresh-token period.
- Key decisions: Use explicit `PC`/`MOBILE` login platform values mapped to OAuth2 clients `zsjos-pc` and `zsjos-mobile`; count valid refresh-token sessions per user and client; serialize replacement with a Redis compare-and-delete lock; evict the oldest same-client session before issuing the new token; use Infra system configuration for limits and remember days; preserve legacy `default` client refresh compatibility when no stored client ID exists; never persist the account password.
- Execution or analysis result: PC and mobile logins now have independent configurable limits with defaults of one device each, and excess logins revoke the oldest same-platform access and refresh tokens. Refresh-token lifetime defaults to seven days and is configurable up to 365 days. Configuration failures fall back to explicit defaults with a searchable warning. The Admin and Workbench clients persist the returned OAuth2 client ID for refresh, clear it on logout, and omit it for legacy sessions. Workbench password caching and the old remember-password control were removed. Existing Infra configuration management exposes the three protected `ZSJOS登录安全` system entries through `infra:config:query`/`infra:config:update`.
- Changed files: `backend/yudao-module-infra/src/main/java/cn/iocoder/yudao/module/infra/enums/ErrorCodeConstants.java`; `backend/yudao-module-infra/src/main/java/cn/iocoder/yudao/module/infra/service/config/ConfigServiceImpl.java`; `backend/yudao-module-infra/src/test/java/cn/iocoder/yudao/module/infra/service/config/ConfigServiceImplTest.java`; System auth/OAuth2 controller, VO, constant, mapper, service, and focused test files under `backend/yudao-module-system`; `frontend/admin/src/api/login/index.ts`; `frontend/admin/src/api/login/types.ts`; `frontend/admin/src/config/axios/service.ts`; `frontend/admin/src/utils/auth.ts`; `frontend/workbench/src/constants.ts`; `frontend/workbench/src/main.tsx`; `frontend/workbench/src/services/api.ts`; `script/sql/mysql/00-bootstrap-schema.sql`; `script/sql/mysql/migrations/V030__zsjos_login_security.sql`; `docs/architecture/data-and-permission-flow.md`; this handoff entry.
- Verification evidence: Focused Maven reactor tests passed 46/46: `ConfigServiceImplTest` 15, `AdminAuthServiceImplTest` 17, and `OAuth2TokenServiceImplTest` 14. Workbench `npm test` passed 58/58, `npm run typecheck` passed, and `npm run build` passed with the existing bundle-size warning. Admin `pnpm build:local` passed with the existing legacy CSS minifier warning. The earlier full Admin `vue-tsc` check remains red only on unrelated pre-existing `PostSimpleVO`, CRM funnel, user, and workstation type errors. `git diff --check` passed. V030 uses guarded inserts and records `V030` in `zsjos_schema_version` with an idempotent duplicate-key update.
- Dependency / integration impact: No new dependency. Adds two OAuth2 client records and three Infra configuration records when V030 is applied. No migration, database write, service restart, account/permission change, branch operation, commit, push, or publication was performed. Unrelated Workbench and local `.env.local` changes were preserved.
- Remaining work: Migration continuity still lacks `V022`, so V030 must not be applied until the numbered sequence is resolved and reviewed. After controlled migration and backend/frontend deployment, perform authenticated PC-PC, mobile-mobile, PC-mobile, concurrent-login, refresh-expiry, current-device logout, and unauthorized/runtime 401 checks using real sessions.

### 2026-08-11 20:25:44 +08:00

- Branch: `main`
- Worktree: `D:\ZSJ-OS`
- HEAD commit: `6417d6daba30bf53011ce2711557674045fb5ef5`
- User goal: Make follow-up remark and next-follow-up time mandatory, cancel pending follow-up reminders when a lead becomes invalid or an order becomes effective, and implement the confirmed unified in-app/WebSocket notification matrix without a fictitious education-department approval role.
- Key decisions: Require a future next-follow-up time; block follow-ups for invalid leads; preserve follow-up history while clearing current reminder timestamps and canceling pending tasks; model timed reminders as tenant-configurable `advance`/`due`/`overdue` notification rules; send only the most urgent currently applicable stage and mark earlier applicable stages handled; use only the current department leader for `direct_leader`; resolve all eligible sales through assignment eligibility; notify actual sales-order reviewer snapshots and the order submitter; derive approval labels from configured departments with `报名履约中心审批` and `财务结算中心审批` fallbacks while retaining internal BPM keys; route workbench notification clicks to permission-checked business destinations.
- Execution or analysis result: Implemented backend validation and lifecycle cancellation, timed-task scanning and stage idempotency, System notification timing configuration/API support, lead qualification-result and sales-order scenes, recipient resolution, dynamic approval labels, workbench message navigation, Admin timing controls, additive V031 bootstrap/migration/schema artifacts, and directly affected documentation. Final review fixed lost `targetRuleId` propagation so a timed event addresses only its configured rule, guarded null approval department IDs, and verified qualification due-stage emission happens before task cancellation. The old standalone qualification-suspended notification is no longer emitted.
- Changed files: System notification DTO/API/rule/processor/provider files and focused tests under `backend/yudao-module-system`; BPM notification scene constructor compatibility; ZSJOS follow-up VO/service, lead management/qualification/lifecycle/notification/filter services, business-task mapper/model/reminder scheduler/service, sales-order notification/service files, and focused tests under `backend/yudao-module-zsjos`; `frontend/workbench/src/components/LeadFollowUpPanel.tsx`; `frontend/workbench/src/components/NotifyMessageProvider.tsx`; Admin notification-rule API/view; `script/sql/mysql/migrations/V031__timed_business_notifications.sql`; bootstrap/core schema, seed, verification, migration README, architecture/API documentation; this handoff entry. Existing unrelated authentication, assignment, form, local environment, V030, and generated metadata changes were preserved.
- Verification evidence: Backend ZSJOS reactor compile passed. Focused backend suite passed 31/31 in the final broad run (System 7 and ZSJOS 24), followed by a final reminder regression run passing 10/10 (System 1 and ZSJOS 9). Workbench `npm test -- --run` passed 58/58, `npm run typecheck` passed, and `npm run build` passed. Admin scoped notification ESLint and `pnpm build:local` passed. SQL static checks confirmed exactly one V031 migration/version record, timing columns and stage table in both schema files, and bootstrap inclusion. `git diff --check` passed before this entry. Full Maven reactor execution remains blocked by the unrelated pre-existing Infra `CodegenEngineUniappTest.testExecute_treeSearch`; global Admin `pnpm ts:check` remains blocked by five unrelated pre-existing BPM/MES/CRM/Post type errors.
- Dependency / integration impact: No new dependency. V031 adds System notification timing columns, the ZSJOS task-stage idempotency table, templates, and per-tenant default rules when applied. No migration, database write, service restart, BPM deployment, account/permission change, branch/worktree operation, commit, push, or publication was performed.
- Remaining work: Run the controlled migration only after migration continuity is approved, then restart/deploy and perform authenticated desktop/mobile notification and WebSocket checks with seeded due tasks and approval actors. The existing System business-event listener is AFTER_COMMIT asynchronous best-effort; a process crash after stage commit but before event consumption is not durably retried. Guaranteed delivery requires a separately approved persistent outbox/delivery-retry design rather than claiming reliability from the current stage table.

### 2026-08-11 21:09:54 +08:00

- Branch: `main`
- Worktree: `D:\ZSJ-OS`
- HEAD commit: `6417d6daba30bf53011ce2711557674045fb5ef5`
- User goal: Audit all lead inbox filter configurations for conflicting fields and apply a unified fix.
- Key decisions: Keep BPM task-definition values `registrationReview` / `financeReview` unchanged; normalize only filter option keys to `registration_review` / `finance_review`; validate condition fields explicitly by `audience`; require the top-level `all` group only for submitter and owner schemes; preserve immutable version snapshots and the applied V029 migration; add a forward-only V032 migration for current reviewer draft/published JSON.
- Execution or analysis result: Fixed the reviewer seed/key validation conflict, removed the frontend's incorrect reviewer `all`-group requirement, added duplicate/format/count validation before submission, enforced audience-specific backend fields and full structural bounds, normalized legacy reviewer keys on read/save/publish/rollback, corrected the expected initial filter-version count from two to three, and synchronized architecture and migration documentation.
- Changed files: `backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/lead/LeadInboxFilterConfigServiceImpl.java`; `backend/yudao-module-zsjos/src/test/java/cn/iocoder/yudao/module/zsjos/service/lead/LeadInboxFilterConfigServiceImplTest.java`; `frontend/admin/src/views/zsjos/leadFilter/index.vue`; `script/sql/mysql/02-bootstrap-zsjos-seed.sql`; `script/sql/mysql/bootstrap.sql`; `script/sql/mysql/verify-bootstrap.sql`; `script/sql/mysql/migrations/V032__normalize_lead_inbox_filter_keys.sql`; `script/sql/mysql/migrations/README.md`; `docs/architecture/data-and-permission-flow.md`; `handoff/20260810-main-existing.md`.
- Verification evidence: Focused Maven reactor test passed 8 tests with 0 failures/errors; target Vue ESLint passed; Admin `pnpm build:local` passed; `git diff --check` passed. Full Admin `pnpm ts:check` remains blocked by five unrelated existing type errors in BPM designer, CRM funnel, MES workstation, and System user files. Browser reached the local Admin login page but protected-page verification was blocked by the running environment error `请求的租户标识未传递`; no shared service or login configuration was changed. SQL was reviewed for scope, ordering, repeatability, JSON spacing variants, preserved BPM values, and unchanged immutable snapshots; it was not executed.
- Dependency / integration impact: No new dependency, permission, BPM key, or historical version change. Deploying the backend/frontend and applying V032 are required for existing environments; migration execution remains separately approval-gated.
- Remaining work: Apply V032 through the controlled deployment migration flow, rebuild/restart the application, fix the unrelated Admin typecheck baseline, and repeat authenticated desktop/mobile browser verification when tenant login is operational.

### 2026-08-11 21:11:18 +08:00

- Branch: `main`
- Worktree: `D:\ZSJ-OS`
- HEAD commit: `6417d6daba30bf53011ce2711557674045fb5ef5`
- User goal: Record the final broad-test result for the completed lead inbox filter consistency fix.
- Key decisions: Preserve the previous delivery entry and append this correction as required; do not modify unrelated timed-notification work to make a direct-module test command pass.
- Execution or analysis result: The direct full ZSJOS module test command did not reach test execution because JUnit discovery could not load the unrelated uninstalled System DTO `NotifyTimingRuleRespDTO` referenced by `BusinessTaskReminderServiceTest`.
- Changed files: `handoff/20260810-main-existing.md` only for this appended correction.
- Verification evidence: `mvn -f backend/yudao-module-zsjos/pom.xml test` ran 0 tests and failed during discovery with `NoClassDefFoundError: cn/iocoder/yudao/module/system/api/notify/dto/NotifyTimingRuleRespDTO`. The reactor-based focused filter suite remains valid and passed 8/8 because it built current System dependencies before ZSJOS.
- Dependency / integration impact: None for the filter fix. The broad direct-module test requires the current System module artifacts to be installed or the full reactor to be run after unrelated work is integrated.
- Remaining work: Re-run the complete ZSJOS suite through a current reactor/install once the timed-notification dependency state is integrated; retain the previously listed deployment, migration, and authenticated browser checks.

### 2026-08-11 21:45:37 +08:00

- Branch: `main`
- Worktree: `D:\ZSJ-OS`
- HEAD commit: `6417d6daba30bf53011ce2711557674045fb5ef5`
- User goal: Fix all lead-detail surfaces that display stable dictionary or protocol keys instead of user-facing labels.
- Key decisions: Preserve dictionary keys as the write and transport source of truth; split pending/claim-pool response keys from optional server-resolved labels; never invent business dictionary options; show explicit label-loading, label-missing, or unknown-status text instead of presenting a key as a label; prefer the persisted invalid-reason label snapshot.
- Execution or analysis result: Workbench submitted/owned lead details now distinguish dictionary load failures from missing entries, unknown protocol states no longer expose raw keys, pending-assignment and claim-pool views consume separate label fields, and both Workbench and Admin invalid-reason details use the stored label snapshot. The pending and claim-pool API now keeps `sourceChannel` and `leadCategory` as stable keys and returns `sourceChannelLabel` and `leadCategoryLabel` separately.
- Changed files: `backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/lead/vo/dispatch/LeadPendingRespVO.java`; `backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/lead/LeadDispatchServiceImpl.java`; `backend/yudao-module-zsjos/src/test/java/cn/iocoder/yudao/module/zsjos/service/lead/LeadDispatchServiceImplTest.java`; `frontend/workbench/src/services/api.ts`; `frontend/workbench/src/services/leadManagement.ts`; `frontend/workbench/src/services/leadManagement.test.ts`; `frontend/workbench/src/components/LeadAssignmentHost.tsx`; `frontend/workbench/src/pages/LeadClaimPoolPage.tsx`; `frontend/workbench/src/pages/LeadManagementPage.tsx`; `frontend/admin/src/views/zsjos/lead/index.vue`; `docs/api/zsjos-lead-submission-dispatch.md`; this handoff entry.
- Verification evidence: Focused backend reactor tests passed 8/8; complete Workbench tests passed 61/61; final focused label tests passed 9/9; Workbench `npm run typecheck` and `npm run build` passed with the existing bundle-size warning; Admin `pnpm build:local` passed with the existing legacy CSS minifier warning; scoped Admin Prettier check passed. Full Admin `pnpm ts:check` remains blocked by five unrelated existing BPM/MES/CRM/Post type errors. Browser smoke checks loaded the Workbench login surface at desktop and 390x844, with mobile `scrollWidth` equal to viewport width; authenticated lead details could not be exercised because the isolated browser had no login session. `git diff --check` passed before this entry apart from one corrected trailing space.
- Dependency / integration impact: No new dependency, dictionary data, schema, migration, database write, permission, service configuration, branch/worktree operation, commit, push, or publication. API consumers of pending and claim-pool responses should use the new optional label fields for display; stable key fields retain their original semantic meaning.
- Remaining work: Deploy backend and frontends together, then run authenticated desktop/mobile checks for submitted leads, owned leads, pending assignment, claim pool, and Admin details with one configured dictionary value and one historical missing value. The existing unrelated Admin typecheck baseline still needs separate repair.

### 2026-08-11 22:11:24 +08:00

- Branch: `main`
- Worktree: `D:\ZSJ-OS`
- HEAD commit: `eaae3965805dc493cc6ad34aabede623843cb540`
- User goal: Isolate sales-order approval inboxes by registration-fulfillment versus finance center while showing both centers' approval status in every authorized order detail.
- Key decisions: Keep one server-owned 成交审批 menu; derive allowed centers from configured root departments and descendants; allow center switching only when a user belongs to both configured ranges; intersect every inbox request with server-resolved BPM task keys; read node status from BPM current/history data without duplicating task state in ZSJOS; preserve an actual approve/reject decision over later any-sign sibling cancellation.
- Execution or analysis result: Added center-scoped approval profile and inbox behavior, rejected forged center requests, added a BPM public node-status summary, projected registration and finance statuses into order details, replaced mixed center filters with an optional dual-center switch, and added a shared dual-status detail card for My Orders and approval details.
- Changed files: BPM task API/implementation/new node-status DTO and focused test; ZSJOS sales-order request/response VOs, constants, object-permission/service implementation and focused test; `frontend/workbench/src/services/api.ts`; `frontend/workbench/src/pages/SalesOrderApprovalPage.tsx`; `frontend/workbench/src/components/SalesOrderDetailCards.tsx`; `docs/api/zsjos-sales-order.md`; `docs/architecture/data-and-permission-flow.md`; this handoff entry.
- Verification evidence: BPM and ZSJOS focused reactor tests passed 16/16; backend reactor compile passed; Workbench tests passed 61/61; Workbench typecheck and production build passed with the existing bundle-size warning; `git diff --check` passed. The full `mvn -f backend/pom.xml -pl yudao-module-zsjos -am test` run remained blocked before BPM/ZSJOS by the unrelated existing Infra `CodegenEngineUniappTest.testExecute_treeSearch` failure. Browser smoke testing loaded the current authenticated Workbench and confirmed no horizontal overflow at 390x844, but the available account did not have the 成交审批 menu.
- Dependency / integration impact: No new dependency, schema, migration, seed data, database write, service restart, permission assignment, branch/worktree operation, commit, push, or publication. Backend and Workbench must be deployed together because the approval profile and order-detail response contracts gained fields.
- Remaining work: Perform authenticated desktop/mobile checks with one registration-only reviewer, one finance-only reviewer, and one dual-center reviewer after deployment; verify pending, approved, rejected, and cancelled node projections against real Flowable history. Resolve the unrelated Infra codegen test separately if a green full reactor baseline is required.

### 2026-08-12 09:50:29 +08:00

- Branch: `main`
- Worktree: `D:\ZSJ-OS`
- HEAD commit: `eaae3965805dc493cc6ad34aabede623843cb540`
- User goal: Prevent duplicate form and task submissions with frontend synchronous guards, stable idempotency-key lifecycles, backend concurrent replay, and management-page operation locking.
- Key decisions: Continue in the user-confirmed transitional main workstream and preserve the existing uncommitted sales-order approval changes; use one idempotency key per user intent and retain it across upload/network failures; rotate only after confirmed success; use synchronous in-memory guards instead of global Axios POST deduplication; keep BPM and business state machines unchanged; serialize first sales-order submission through the existing lead row lock and recheck the key after lock acquisition; add per-object/action processing locks for Admin product operations without adding dependencies or schema changes.
- Execution or analysis result: Added a reusable Workbench submission gate and integrated it into follow-up, sales-order submit/resubmit, appeal submit/decision, lead qualification, qualification disposition, and sales-order approval actions. The gate rejects concurrent re-entry, keeps the same key after failure, and rotates after success. Admin qualification disposition now uses a stable per-dialog key and synchronous guard. Admin category, product, and SKU status/delete/generate actions now expose scoped loading states and suppress repeated calls. Concurrent identical first-order submissions now replay the winning order after the lead lock instead of reporting an active-order conflict. Updated directly affected API documentation.
- Changed files: `frontend/workbench/src/services/submissionGuard.ts`; `frontend/workbench/src/services/submissionGuard.test.ts`; `frontend/workbench/src/components/LeadFollowUpPanel.tsx`; `frontend/workbench/src/components/LeadAppealPanel.tsx`; `frontend/workbench/src/components/SalesOrderEntryModal.tsx`; `frontend/workbench/src/pages/LeadManagementPage.tsx`; `frontend/workbench/src/pages/LeadQualificationExceptionPage.tsx`; `frontend/workbench/src/pages/LeadAppealPage.tsx`; `frontend/workbench/src/pages/SalesOrderApprovalPage.tsx`; `frontend/workbench/tsconfig.tsbuildinfo` (build metadata refreshed for the new source/test files); `frontend/admin/src/views/zsjos/leadQualification/index.vue`; `frontend/admin/src/views/zsjos/product/index.vue`; `backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/order/SalesOrderServiceImpl.java`; `backend/yudao-module-zsjos/src/test/java/cn/iocoder/yudao/module/zsjos/service/order/SalesOrderServiceImplTest.java`; `docs/api/zsjos-lead-submission-dispatch.md`; `docs/api/zsjos-lead-appeal.md`; `docs/api/zsjos-sales-order.md`; `docs/api/zsjos-product-configuration.md`; this handoff entry. The sales-order service/test files already contained the active approval-inbox work and were extended without reverting it.
- Verification evidence: Workbench full test suite passed 63/63; Workbench `npm run typecheck` passed; Workbench production build passed with the existing bundle-size warning; submission-gate focused tests passed 2/2; Admin target ESLint and Prettier checks passed; Admin `pnpm build:local` passed with the existing legacy `*zoom` CSS minifier warning; focused backend reactor test passed `SalesOrderServiceImplTest` 14/14 and compiled the 20-module dependency graph; `git diff --check` passed. Browser verification was not run because ports 5174, 80, and 48080 were all closed, and starting services requires separate explicit confirmation under repository rules.
- Dependency / integration impact: No new npm/Maven dependency, schema, migration, dictionary data, permission, BPM definition, database write, service start/restart, branch/worktree operation, commit, push, or publication. Backend and Workbench should be deployed together for consistent sales-order retry behavior; Admin changes are client-side request suppression only.
- Remaining work: With separate approval to start or use a deployed environment, run authenticated desktop/mobile checks for follow-up, qualification, appeal, sales-order submission/approval, and Admin product actions, including rapid double-click and failed-upload retry scenarios. Other lower-risk configuration forms still rely on local loading/state-update behavior and can be migrated to the same guard pattern in a later scoped task.
### 2026-08-12 10:27:40 +08:00

- Branch: `main`
- Worktree: `D:\ZSJ-OS`
- HEAD commit: `eaae3965805dc493cc6ad34aabede623843cb540`
- User goal: Record the actual finance or registration reviewer after a sales-order decision, show reviewer name/result/time in order details without displaying the user ID, and remove approve/reject actions for other users after that center has been handled.
- Key decisions: Keep BPM as the source of truth for task assignee, result, and completion time; return reviewer user ID as a stable API identifier while resolving and displaying only the System user nickname; do not add an approval-history table or schema; require the selected center's projected node status to remain `pending` in addition to a current `taskId` before rendering actions; retain server-side BPM task validation as the concurrency boundary.
- Execution or analysis result: Extended BPM node summaries with actual reviewer identity, projected reviewer identity/result/time into registration and finance order-detail nodes, rendered a responsive center audit table with reviewer name/result/time, and hid approve/reject actions once the selected center was no longer pending. Updated the sales-order API contract and added BPM, order projection, and frontend action-visibility tests. Existing uncommitted center-isolation and submission-guard changes were preserved and extended.
- Changed files: `backend/yudao-module-bpm/src/main/java/cn/iocoder/yudao/module/bpm/api/task/dto/BpmProcessNodeStatusRespDTO.java`; `backend/yudao-module-bpm/src/main/java/cn/iocoder/yudao/module/bpm/api/task/BpmProcessTaskApiImpl.java`; `backend/yudao-module-bpm/src/test/java/cn/iocoder/yudao/module/bpm/api/task/BpmProcessTaskApiImplTest.java`; `backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/order/vo/SalesOrderRespVO.java`; `backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/order/SalesOrderServiceImpl.java`; `backend/yudao-module-zsjos/src/test/java/cn/iocoder/yudao/module/zsjos/service/order/SalesOrderServiceImplTest.java`; `frontend/workbench/src/services/api.ts`; `frontend/workbench/src/services/salesOrder.ts`; `frontend/workbench/src/services/salesOrder.test.ts`; `frontend/workbench/src/components/SalesOrderDetailCards.tsx`; `docs/api/zsjos-sales-order.md`; this handoff entry.
- Verification evidence: Focused BPM and ZSJOS reactor tests passed 19/19; Workbench full tests passed 64/64; Workbench typecheck passed; Workbench production build passed with the existing bundle-size warning; `git diff --check` passed. The existing Workbench and backend were reachable on ports 80 and 48080, but browser verification reached only the old deployed login page and no login form was submitted; the new detail behavior is therefore not authenticated-browser verified.
- Dependency / integration impact: No new dependency, schema, migration, dictionary data, BPM definition, permission, database write, service restart, branch/worktree operation, commit, push, or publication. BPM, ZSJOS backend, and Workbench must be deployed together because the node-summary and order-detail response contracts gained reviewer fields.
- Remaining work: After deployment, authenticate one registration reviewer and one finance reviewer, have one user decide each center, then verify at desktop and mobile widths that all authorized users see reviewer name/result/time without the user ID, handled-center actions disappear, the other pending center remains actionable, and stale concurrent decisions receive the existing already-handled response.

### 2026-08-12 11:33:00 +08:00

- Branch: `main`
- Worktree: `D:\ZSJ-OS`
- HEAD commit: `eaae3965805dc493cc6ad34aabede623843cb540`
- User goal: Fix lead-detail cards that display protocol or dictionary keys instead of user-facing values, and reorganize detail-card field placement.
- Key decisions: Preserve stable keys as backend protocol values; persist only resolved labels in historical snapshots; treat English protocol-looking historical snapshots as missing labels in both frontends; keep customer identity, lead state, intended product, submission/assignment, qualification result, and general attachments in their respective business sections; replace Admin-facing `SPU` / `SKU` headings with business wording.
- Execution or analysis result: New follow-up category snapshots no longer fall back to dictionary keys, and appeal snapshots no longer fall back to the invalid-reason key. Workbench and Admin follow-up, appeal, invalid-reason, and unknown-stage displays now suppress protocol keys. The Workbench moved owner information out of customer identity and moved invalid qualification details into submission/assignment. The Admin detail moved invalid reason and qualification remarks into submission/assignment and renamed product columns to `课程` and `具体方案`. Admin follow-up API typing now includes the server-returned method/result keys required for snapshot validation.
- Changed files: `backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/lead/LeadAppealServiceImpl.java`; `backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/lead/LeadFollowUpServiceImpl.java`; `backend/yudao-module-zsjos/src/test/java/cn/iocoder/yudao/module/zsjos/service/lead/LeadAppealServiceImplTest.java`; `backend/yudao-module-zsjos/src/test/java/cn/iocoder/yudao/module/zsjos/service/lead/LeadFollowUpServiceImplTest.java`; `frontend/workbench/src/services/leadManagement.ts`; `frontend/workbench/src/services/leadManagement.test.ts`; `frontend/workbench/src/components/LeadFollowUpPanel.tsx`; `frontend/workbench/src/components/LeadAppealPanel.tsx`; `frontend/workbench/src/pages/LeadAppealPage.tsx`; `frontend/workbench/src/pages/LeadManagementPage.tsx`; `frontend/workbench/src/pages/LeadQualificationExceptionPage.tsx`; `frontend/admin/src/api/zsjos/leadFollowUp/index.ts`; `frontend/admin/src/views/zsjos/lead/index.vue`; `frontend/admin/src/views/zsjos/leadQualification/index.vue`; this handoff entry.
- Verification evidence: Focused backend reactor tests passed 14/14. Workbench full tests passed 65/65, focused lead-management tests passed 10/10 after the final alert fix, `npm run typecheck` passed, and the production build passed with the existing bundle-size warning. Admin `pnpm build:local` passed with the existing legacy `*zoom` CSS minifier warning; scoped Prettier passed. Admin full `pnpm ts:check` has no task-related errors and remains blocked by five unrelated existing BPM designer, CRM funnel, MES workstation, and System user type errors. Browser smoke loaded the Workbench login surface at desktop width 1280 and mobile 390x844 with no horizontal overflow. Authenticated lead-detail data was not visually verified because the browser session had no business login. `git diff --check` passed before this entry.
- Dependency / integration impact: No new dependency, API endpoint behavior, schema, migration, dictionary seed, database write, permission, BPM definition, account change, branch/worktree operation, commit, push, or publication. Backend, Workbench, and Admin should be deployed together so new snapshots and historical-display protection remain consistent.
- Remaining work: After deployment, run authenticated desktop/mobile checks against one lead with configured labels and one historical lead whose snapshots contain or omit keys; separately repair the five unrelated Admin typecheck baseline errors.
### 2026-08-12 18:58:00 +08:00

- Branch: `main`
- Worktree: `/Users/louie/Documents/ChatGPT/ZSJOS 2`
- HEAD commit: `8891e24f8f2e92cb315104a4ba0f1c6692775ce8` (pre-merge HEAD)
- User goal: Push the local main branch to the remote main branch, directly integrating remote changes when the initial non-fast-forward push was rejected.
- Key decisions: Merge `origin/main` into local `main`; preserve both the local work-plan foundation and the remote sales-order, approval, notification, login-security, and lead improvements; keep the generic object-permission provider architecture and add a sales-order provider; preserve Work Plan error codes in `1_900_005_xxx` and move Sales Order codes to `1_900_006_xxx`; preserve the published Sales Order `V023` migration and renumber the Work Plan query-permission migration to forward-only `V033`; synchronize fresh and desired Core schemas without executing any database migration.
- Execution or analysis result: Resolved ten content conflicts across task queries, error codes, object permissions, lead lifecycle tasks, Workbench routing, and SQL bootstrap/schema/verification tooling. Integrated scoped follow-up reminder idempotency through `BusinessTaskCommandService`, adapted Work Plan notification scenes to the current timed-scene DTO contract, repaired the merged lifecycle test, restored SQL baseline/schema equality, and removed obsolete migration-gap warnings. The merge is ready for commit and push.
- Changed files: The merge result from local commits `03a1142` through `8891e24` and remote commits through `eaae396`; conflict-resolution edits in `BusinessTaskMapper.java`, `ZsjosErrorCodeConstants.java`, `ZsjosPermissionAspect.java`, `LeadLifecycleTaskService.java`, `SalesOrderObjectPermissionProvider.java`, `WorkPlanNotifySceneProvider.java`, `LeadLifecycleTaskServiceTest.java`, `frontend/workbench/src/main.tsx`, Core bootstrap/schema/seed/verification tooling, migration README, renamed `V033__split_work_plan_query_permission.sql`; this handoff entry.
- Verification evidence: `mvn -f backend/pom.xml -pl yudao-module-zsjos -am -DskipTests compile` passed; focused reactor tests passed 32/32 for lead lifecycle/follow-up, business-task reminders, sales orders, work plans, templates, and object permissions; Workbench tests passed 66/66; Workbench typecheck and production build passed with only the existing bundle-size warning; `python3 script/sql/mysql/tools/zsjos_db.py check` passed; Core desired schema and fresh baseline are byte-identical; conflict-marker search, unmerged-index check, and `git diff --cached --check` passed.
- Dependency / integration impact: No new external dependency and no database migration, shared-service reconfiguration, account/permission mutation, or force push. The merge adds the forward-only `V033` migration and changes Sales Order API error codes from the colliding `1_900_005_xxx` range to `1_900_006_xxx`; consumers should use symbolic/business error handling rather than hard-coded old numbers. The final merge commit and remote push are performed immediately after this entry under the user's explicit authorization.
- Remaining work: After deployment, apply pending migrations only through the controlled migration flow and perform the authenticated desktop/mobile and runtime checks already listed in the merged workstream handoffs. None blocks the Git integration.

### 2026-08-12 19:14:10 +08:00

- Branch: `main`
- Worktree: `D:\ZSJ-OS`
- HEAD commit: `405f9ed30146fb7b0528c5c22cab5d4c78264b7b` (pre-merge HEAD)
- User goal: Fetch the latest remote code, merge it into the local `main` branch, and create the local merge commit without pushing.
- Key decisions: Preserve the existing local commit and integrate `origin/main` with a non-rebase merge; bypass the unavailable user-level localhost Git proxy only for the fetch command; preserve both chronological handoff histories; regenerate the tracked TypeScript build metadata from the merged source.
- Execution or analysis result: Fetched five new remote commits, merged them with the one local commit, resolved conflicts in `frontend/workbench/tsconfig.tsbuildinfo` and this handoff file, retained both sides' handoff entries, and prepared the conflict-free merge for commit.
- Changed files: The merge result between local `405f9ed301` and remote `22dfbf830c`; conflict-resolution changes in `frontend/workbench/tsconfig.tsbuildinfo`; this handoff entry in `handoff/20260810-main-existing.md`.
- Verification evidence: Unmerged-index and repository conflict-marker checks passed; Workbench `npm run build` passed, including TypeScript project build and Vite production build, with only the existing large-chunk warning; final staged diff checks are run immediately before the merge commit.
- Dependency / integration impact: No dependency, database execution, external service reconfiguration, branch/worktree switch, rebase, push, or publication. The local `main` will contain a merge commit and remain ahead of `origin/main` until separately authorized for push.
- Remaining work: Push the resulting local merge commit only after separate explicit authorization. Full backend and database verification was already recorded on the integrated remote commit and was not rerun for this Git-only merge.

### 2026-08-14 14:22:04 +08:00

- Branch: `main`
- Worktree: `D:\ZSJ-OS`
- HEAD commit: `6ec2a9b53c3e8c831caa0902c88969fc69011331` (pre-merge HEAD)
- User goal: Fetch the latest remote repository state and merge it into the local `main`, using the remote visual framework while preserving all local Workbench business functionality.
- Key decisions: Merge `origin/main` without rebasing; adopt the remote modular shell, layout modes, tabs, watermark, mobile navigation, theme settings, detail overview, and split stylesheet architecture; restore every local business route, server-owned default employee avatar, advanced lead filters, submitter supplement/urge/complaint actions, repurchase entry, irreversible confirmations, incremental loading, and realtime unseen-lead behavior; move retained local styles into focused modules instead of keeping the legacy monolithic stylesheet; preserve unrelated uncommitted backend mapper/test/handoff work.
- Execution or analysis result: Fetched three remote-only Workbench commits, resolved five merge conflicts, extended the new `RouteHost` with all local routes and required permissions, combined the new lead-detail presentation with local business actions and safeguards, retained appeal decision confirmation, migrated aging-pool, subordinate-sales, advanced-filter, and lead-list loading styles into the remote CSS structure, regenerated TypeScript build metadata, and prepared the local merge commit without pushing.
- Changed files: The merge result between local `6ec2a9b53c` and remote `2b283f08d4`, covering `.gitignore`, `frontend/admin/.env.local`, and the remote Workbench theme/layout/component/style changes; conflict and preservation edits in `frontend/workbench/src/main.tsx`, `frontend/workbench/src/layouts/RouteHost.tsx`, `frontend/workbench/src/pages/LeadAppealPage.tsx`, `frontend/workbench/src/pages/LeadManagementPage.tsx`, `frontend/workbench/src/styles/index.css`, `frontend/workbench/src/styles/layout.css`, `frontend/workbench/src/styles/components/lead-detail.css`, `frontend/workbench/src/styles/components/tab-bar.css`, `frontend/workbench/src/styles/components/advanced-filter.css`, `frontend/workbench/src/styles/pages/lead-management.css`, `frontend/workbench/src/styles/pages/aging-pool.css`, `frontend/workbench/src/styles/pages/subordinate-sales.css`, deletion of `frontend/workbench/src/styles.css`, regenerated `frontend/workbench/tsconfig.tsbuildinfo`, and this handoff entry.
- Verification evidence: Workbench `npm run typecheck` passed; all 30 Vitest files and 162 tests passed, including CSS hygiene, route/navigation, theme, lead management, subordinate sales, submission guard, and realtime unseen tests; `npm run build` passed with 5,085 modules transformed and only the existing large-chunk warning; `git diff --check` passed. Authenticated browser checks passed at `1280x720` and `390x844` with zero document/shell horizontal overflow; Today Tasks and Subordinate Sales loaded real API data, and Subordinate Sales changed from a desktop grid to the intended mobile single-column/list-only state. Browser logs contained only existing Ant Design deprecation warnings for Drawer `width` and `List`, with no runtime exception.
- Dependency or integration impact: Integrates the remote declared `@types/node` development dependency and lockfile state; no new dependency beyond the remote commits, backend/schema/migration/database/permission/BPM change, external-service reconfiguration, branch/worktree switch, rebase, push, or publication. The local development server already listening on port 5174 was reused and remains available.
- Remaining work: Push the resulting local merge commit only after separate explicit authorization. Ant Design Drawer `width` and List deprecation warnings can be handled in a separately scoped cleanup before a future major-version upgrade.
