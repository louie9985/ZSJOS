# Main Workstream

## Active delivery: authenticated partner full-chain verification

- Workstream ID: `main-partner-e2e-verification`
- Goal: run multiple real local end-to-end partner-to-registration/operations/finance flows, identify defects, and apply minimal fixes with regression evidence
- Non-goals: production deployment, destructive cleanup of unrelated data, credential rotation, database reset, branch/commit operations
- Branch: `main`
- Worktree: `D:\ZSJ-OS`
- Base commit: `7d4a9ae2f959ccfaee2a389bfac2a7b94cccae25`
- Target branch: `main`
- Ownership scope: test-marked local business records, directly implicated frontend/backend fixes, focused tests, and this handoff entry
- Owner: Codex root
- Dependencies: running local backend and three frontend surfaces; provided test accounts; existing `/part-api`, `/admin-api`, and `/app-api` contracts
- Integration order: authenticate each role -> create independent test cases -> advance/branch states -> record failures -> patch only confirmed defects -> rerun affected cases -> append final evidence
- Verification plan: browser/UI and network checks for each role, state-transition evidence from visible pages/API responses, focused code tests for fixes, desktop/mobile smoke checks where applicable

## Active delivery: student acceptance and continuous contact chain

- Workstream ID: `main-student-contact-chain`
- Goal: implement service-relation-level student acceptance, first-contact -> study-plan -> recurring-contact tasks, configurable minimum forms and reminders, BPM-backed deadline extensions, optional director/career-planner assignment, and permission-projected student tabs
- Non-goals: later supervision/exam/certificate/repurchase/closure SOP stages; detailed first-contact or study-plan form design beyond the confirmed minimum; real migration execution; shared-service restart; branches, commits, pushes, or publication
- Branch: `main`
- Worktree: `D:\ZSJ-OS`
- Base commit: `7d4a9ae2f959ccfaee2a389bfac2a7b94cccae25`
- Target branch: `main`
- Ownership scope: additive ZSJOS student-contact/configuration/assignment/extension persistence, Controller/Service/DAL/VO/permission/notification/task behavior and focused tests; required BPM public extension workflow boundary without duplicating BPM task history; registration planner-candidate and director-route retirement behavior; Workbench My Students/contact/exception UI and Vue administration configuration; next MySQL migration plus bootstrap/schema/verification wiring; directly affected registration, permission-flow, navigation and migration documentation; this handoff file. Existing overlapping user edits are preserved.
- Owner: Codex `/root`
- Dependencies: existing service relations and registration completion transaction, generic ZSJOS business tasks, System dictionary/user/department/notification APIs, Infra file API, user-relation scenes, BPM public APIs, React Workbench and Vue administration frontend; no new dependency planned
- Integration order: preserve current overlapping registration changes -> add schema/domain contracts -> implement transactional task chain and reminders/BPM boundary -> switch registration planner candidates and optional collaborators -> implement both frontend surfaces -> synchronize docs/tests -> focused and full verification
- Verification plan: focused student-contact/configuration/assignment/extension/reminder/permission tests; ZSJOS and relevant BPM compile/tests; Workbench tests/typecheck/build; Vue typecheck/build; database consistency checker and migration repeatability review; scoped diff check; authenticated desktop/mobile browser checks when the unchanged local runtime can expose the new code

## Active delivery: registration container validation annotation compatibility

- Workstream ID: main-registration-container-validation
- Goal: remove Hibernate Validator 9 `HV000271` warnings while preserving nested validation for registration checklist items, route options and route updates
- Non-goals: change request fields, validation constraints, checklist business rules, database data, runtime services, dependencies, branches, commits, or external state
- Branch: main
- Worktree: D:\ZSJ-OS
- Base commit: 7d4a9ae2f959ccfaee2a389bfac2a7b94cccae25
- Target branch: main
- Ownership scope: registration checklist and route request VO validation annotations, focused validation/compile checks, and this handoff record; existing overlapping changes are preserved
- Owner: Codex root
- Dependencies: Jakarta Bean Validation container-element validation already used in ZSJOS; no new dependency
- Integration order: move `@Valid` from collection fields to their generic element types; compile and run focused validation checks; append delivery evidence
- Verification plan: ZSJOS test compile/focused tests, module tests if needed, and scoped `git diff --check`

## Delivery Entry 2026-08-19 19:44 +08:00

- Branch: `main`
- Worktree: `D:\ZSJ-OS`
- HEAD commit: `7d4a9ae2f959ccfaee2a389bfac2a7b94cccae25` (no commit created)
- User goal: implement the approved student acceptance and continuous contact task chain.
- Key decisions: kept first contact, study plan, and recurring contact as separate requests; made the first-contact checklist conditional on successful contact; added relation cancellation cleanup for contact tasks and pending extension snapshots; added attachment upload, extension reason/description, normal-contact form, and server-projected tab filtering in Workbench; corrected the V094 configuration menu path and added the Vue exception placeholder; documented BPM deployment and non-executed migration requirements.
- Execution result: service-relation acceptance creates task chain; successful/failed branches, reminders, assistance, optional collaborators, config snapshots, dictionary validation, and extension boundary remain implemented. Cancellation now cancels pending student tasks and marks pending extensions cancelled when an order registration is cancelled.
- Changed files: student contact context/service, registration cancellation service, Workbench API and My Students UI, Vue exception placeholder, V094 menu seed, registration/migration/frontend documentation, and this handoff.
- Verification evidence: `mvn -f backend/pom.xml -pl yudao-module-zsjos -am -DskipTests compile` passed; focused Maven tests (`MyStudentServiceImplTest`, `BusinessTaskReminderServiceTest`) passed 6/6; Workbench `npm run typecheck` passed; Workbench `npm test -- --run` passed 54 files/316 tests; Workbench `npm run build` passed with existing large-chunk warning. Vue `pnpm ts:check` remains blocked by unrelated pre-existing errors; Vue production build and authenticated browser checks were not run.
- Dependency/integration impact: no new dependency, no branch/commit, no service restart, and no migration execution. BPM process `zsjos_student_contact_extension` still must be deployed before production extension approvals.
- Remaining work: full ZSJOS test suite, Vue build, runtime/browser verification, and controlled V094 execution require a separate environment/approval; detailed admin checklist/quick-note editing remains intentionally minimal for this phase.

## Delivery Entry 2026-08-19 15:03 +08:00

- Branch: main
- Worktree: D:\ZSJ-OS
- HEAD commit: 7d4a9ae2f959ccfaee2a389bfac2a7b94cccae25 (uncommitted worktree changes; no commit created)
- User goal: Remove Hibernate Validator `HV000271` warnings emitted when saving the registration checklist draft.
- Key decisions: Applied Jakarta Bean Validation container-element validation already used elsewhere in ZSJOS; kept `@NotEmpty`, `@Size` and every nested DTO constraint unchanged; included the registration route-update VO because it used the same deprecated annotation placement.
- Execution result: `items`, `routeOptions` and `routes` now declare `List<@Valid ...>` instead of placing `@Valid` on the `List` field, preserving nested validation without the Hibernate Validator 9 deprecation warning.
- Changed files: `backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/registration/vo/RegistrationChecklistDraftSaveReqVO.java`; `backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/registration/vo/RegistrationRoutesUpdateReqVO.java`; `handoff/main.md`.
- Verification evidence: Full `yudao-module-zsjos` test suite passed (422 tests, no failures or skips); Hibernate Validator 9 initialized during the suite without the affected `HV000271` warnings; annotation scan confirmed all three fields use container-element `@Valid`; scoped `git diff --check` passed.
- Dependency/integration impact: None; no API shape, validation rule, dependency, schema or external state changed.
- Remaining work: Restart or redeploy through the authorized operational process before the currently running service reflects the annotation change.

## Active delivery: sales-order three-party parallel approval

- Workstream ID: main-sales-order-three-party-approval
- Goal: make an optional supervisor sign request extend the registration/finance dual approval into three independent approvals, with any rejection ending the order and all required approvals needed for success
- Non-goals: migrate or rewrite in-flight process instances; change supervisor selection or permissions; add database fields or dependencies; execute external services or database changes; create branches, commits, or publish artifacts
- Branch: main
- Worktree: D:\ZSJ-OS
- Base commit: 7d4a9ae2f959ccfaee2a389bfac2a7b94cccae25
- Target branch: main
- Ownership scope: existing BPM parallel-sign completion behavior and focused tests; ZSJOS sales-order supervisor-confirmation tests and directly affected behavior documentation; this handoff record. Existing overlapping edits are preserved.
- Owner: Codex root
- Dependencies: existing Flowable task parent/child model, BPM process status events, ZSJOS order row locking/idempotency, and the integrated supervisor-confirmation feature; no new dependency
- Integration order: preserve current parallel-sign changes; remove competing parent-task entity saves; verify independent original/supervisor approval order, immediate rejection, and final process completion; synchronize documentation; run focused BPM and ZSJOS tests plus proportional builds
- Verification plan: focused BPM tests for original-first and supervisor-first approval, rejection and single parent completion; focused ZSJOS supervisor/order tests; BPM and ZSJOS Maven tests; server assembly build; scoped diff and whitespace checks

## Delivery Entry 2026-08-19 14:54 +08:00

- Branch: main
- Worktree: D:\ZSJ-OS
- HEAD commit: 7d4a9ae2f959ccfaee2a389bfac2a7b94cccae25 (uncommitted worktree changes; no commit created)
- User goal: Correct sales-order supervisor sign approval so registration, finance and one supervisor approve independently, any rejection rejects the order, and all three required approvals are needed for success without the finance approval optimistic-lock failure.
- Key decisions: Kept BPM as task and process truth; retained the parent/child parallel-sign model; replaced the stale parent task entity save after local-variable writes with task-service owner/assignee commands; limited supervisor confirmation to one request per approval round so the participant set cannot expand to four; hid the second request action from order detail projection; preserved existing BPM rejection behavior and process-result listener as the immediate order rejection and final all-required-approvals aggregation boundary.
- Execution result: The requesting center and supervisor can pass in either order without completing or changing the other center; the parent center completes only after both its ordinary and supervisor tasks pass; the parallel BPM gateway still waits for the other center; any center or supervisor rejection ends the round. The stale Flowable task update that produced `FlowableOptimisticLockingException` was removed.
- Changed files: `backend/yudao-module-bpm/src/main/java/cn/iocoder/yudao/module/bpm/service/task/BpmTaskServiceImpl.java`; `backend/yudao-module-bpm/src/test/java/cn/iocoder/yudao/module/bpm/service/task/BpmTaskServiceImplParallelSignTest.java`; `backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/order/SalesOrderSupervisorConfirmationService.java`; `backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/order/SalesOrderServiceImpl.java`; `backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/enums/ZsjosErrorCodeConstants.java`; `backend/yudao-module-zsjos/src/test/java/cn/iocoder/yudao/module/zsjos/service/order/SalesOrderSupervisorConfirmationServiceTest.java`; `docs/business/lead-order-state-machine.md`; `docs/architecture/data-and-permission-flow.md`; `docs/api/zsjos-sales-order.md`; `handoff/main.md`.
- Verification evidence: Focused BPM parallel-sign tests passed (4); focused ZSJOS order and supervisor tests passed (36); full BPM module tests passed (78, 6 skipped); full ZSJOS module tests passed (422); all 28 backend reactor modules and `yudao-server` assembly passed with `-Dspring-boot.repackage.skip=true`; scoped `git diff --check` passed. A normal executable repackage reached all module compilation successfully but could not rename the existing `yudao-server.jar` because it is held by a running process; the service was not stopped.
- Dependency/integration impact: No new dependency, schema, migration, permission or external-state change. New rounds allow at most one supervisor confirmation across both centers. Existing in-flight rounds are not rewritten.
- Remaining work: Deploy/restart through the authorized operational process and run an authenticated controlled-order check for center-first pass, supervisor-first pass, all-three pass, and rejection by each participant. The currently running service was not restarted and no real order or database data was changed.

## Active delivery: subordinate sales lazy loading and team dispatch pause

- Workstream ID: main-subordinate-sales-dispatch
- Goal: align the subordinate-sales list with the shared lazy-load pattern, add a permissioned manager command that pauses every managed sales specialist, and surface prominent home-page warnings when an eligible salesperson cannot receive automatic assignments
- Non-goals: change subordinate scope, disable accounts, force browser sessions offline, transfer existing Leads, infer permissions from roles in the Workbench, execute migrations, restart services, create branches, commit, or modify external state
- Branch: main
- Worktree: D:\ZSJ-OS
- Base commit: 7d4a9ae2f959ccfaee2a389bfac2a7b94cccae25
- Target branch: main
- Ownership scope: subordinate-sales bulk pause Controller/VO/Service/status-preference behavior and focused tests; Workbench subordinate lazy list, shared dispatch-status provider, home warning and focused tests/styles; additive V092 permission migration and bootstrap/verification wiring; directly affected API, permission-flow, frontend and migration documentation; this handoff record. Existing overlapping edits are preserved.
- Owner: Codex root
- Dependencies: existing System managed-user/post APIs, sales dispatch preference and Redis presence services, subordinate audit log, Workbench permission response and realtime provider; no new dependency
- Integration order: preserve overlapping changes; add transactional server-owned bulk scope and audit; replace list pagination with append lazy loading; centralize dispatch lifecycle state and add home warning; add V092/default sales-manager grant and documentation; run focused/full verification and append delivery
- Verification plan: backend bulk scope, disabled-user, idempotency, audit and permission contract tests; Workbench lazy loading, permission, shared-state and warning tests; ZSJOS Maven tests; Workbench full tests/typecheck/build; `zsjos-db check`; SQL static/repeatability checks; desktop/mobile browser checks; scoped diff/whitespace checks

## Active delivery: Lead flow history tab

- Workstream ID: main-lead-flow-history
- Goal: add a permissioned Lead-detail flow-history timeline by merging persisted business events, assignment history, and aging-pool events
- Non-goals: create a duplicate event ledger, rewrite historical data, change Lead object authorization, expose internal Lead IDs as user-facing numbers, add downloads, execute migrations, branches, commits, or external state changes
- Branch: main
- Worktree: D:\ZSJ-OS
- Base commit: 7d4a9ae2f959ccfaee2a389bfac2a7b94cccae25
- Target branch: main
- Ownership scope: ZSJOS Lead flow-history API/VO/mapper projection and focused tests; Workbench flow-history tab/API/timeline and focused tests; additive permission migration and directly affected architecture/API/frontend/migration docs; this handoff record
- Owner: Codex root
- Dependencies: existing Lead object permission, BusinessEvent, LeadAssignmentHistory, LeadAgingPoolEvent, Infra file preview APIs, and server-projected `visibleTabs`; no new dependency
- Integration order: merge existing event sources with stable deduplication; add `zsjos:lead-detail:flow-read` and default `sales_manager` grant; add Workbench timeline and image/PDF preview without download; update docs; run focused/full verification and append delivery
- Verification plan: backend mapping/permission tests; Workbench tab/API/timeline tests; Maven focused tests; Workbench full tests, typecheck/build, `zsjos-db check`, SQL static checks, browser desktop/mobile smoke, scoped diff checks

## Active delivery: notification Lead-tab deep links and complaint outcomes

- Workstream ID: main-notification-lead-tabs
- Goal: route Lead business notifications to their relevant detail tab and notify the actual complaint submitter for both founded and unfounded decisions
- Non-goals: change complaint decision semantics, appeal workflow, Lead object authorization, detail-tab permissions, historical notifications, database schema, dependencies, branches, commits, deployed services, or external database state
- Branch: main
- Worktree: D:\ZSJ-OS
- Base commit: 7d4a9ae2f959ccfaee2a389bfac2a7b94cccae25
- Target branch: main
- Ownership scope: Lead complaint outcome notification constants/publishing/recipient resolution and focused tests; Workbench notify-message Lead-tab routing, Lead management deep-link parsing, Lead detail initial-tab selection, and focused tests; additive V090 notification template/rule migration plus fresh bootstrap/verification/migration documentation; directly affected notification, submitter-action, frontend deep-link, and permission-flow documentation; this handoff section and delivery entry. Existing overlapping edits are preserved.
- Owner: Codex root
- Dependencies: existing System business-notification API, persisted complaint complainant identities, Workbench React Router route, server-projected Lead `visibleTabs`, and existing Lead notification templates/rules; no new dependency
- Integration order: preserve overlapping changes; publish both complaint outcomes to the exact complainant subject; add repeatable template/rule configuration; replace follow-up-only routing with validated tab targets; preserve reviewer appeal-inbox routing; add tests/docs; run backend, SQL, Workbench, and browser verification; append delivery entry
- Verification plan: focused complaint service/provider tests for founded/unfounded ADMIN and PARTNER recipients; notification action and tab-resolution tests for appeal/complaint/follow-up/overview plus hidden-tab fallback; SQL syntax/repeatability/order review and repository verification script without applying V090 to an external database; Workbench full tests, typecheck, build, desktop/mobile browser checks where authenticated state is available; scoped diff/whitespace checks

## Active delivery: lazy-loaded Lead notification target selection

- Workstream ID: main-lead-lazy-deep-link
- Goal: keep a notification-linked Lead selected when it is outside the first lazy-loaded page, and place newly actionable Leads at the top when refresh pagination does not expose them
- Non-goals: change backend Lead ordering or permissions, alter notification protocols, permanently reorder all historical unread Leads, database/schema/data, branches, commits, or external services
- Branch: main
- Worktree: D:\ZSJ-OS
- Base commit: 7d4a9ae2f959ccfaee2a389bfac2a7b94cccae25
- Target branch: main
- Ownership scope: Workbench Lead paging/selection helpers, unified Lead management lazy-list hydration and focused tests, this handoff entry. Existing overlapping edits are preserved.
- Owner: Codex root
- Dependencies: existing `managedLead` detail API, paginated Lead list, unseen Lead event, and React Workbench route state; no new dependency
- Integration order: preserve notification deep-link behavior; retain requested selection through initial page load; hydrate missing targets into the left list; prioritize current-session unseen action Leads; clear deep-link pinning after manual selection; run focused/full Workbench verification and browser smoke checks
- Verification plan: helper and route guard tests for out-of-page selection, hydrated insertion, unseen prioritization and manual override; Workbench full tests, typecheck/build, desktop/mobile browser smoke, scoped diff/whitespace checks

## Active delivery: specified-assignment identity visibility

- Workstream ID: main-specified-assignment-identity
- Goal: make new-media specified assignments mutually identity-visible and label new-media Lead sources as automatic or specified assignment
- Non-goals: change automatic-assignment blind rules, partner or self-sourced identity rules, database schema/data, dispatch behavior, permissions, branches, commits, or shared external state
- Branch: main
- Worktree: D:\ZSJ-OS
- Base commit: 7d4a9ae2f959ccfaee2a389bfac2a7b94cccae25
- Target branch: main
- Ownership scope: the identity-mask predicate and focused tests in LeadManagementServiceImpl; the source row, focused presentation tests, and source-value styles in the Workbench Lead detail; the directly affected identity-visibility paragraph in docs/architecture/data-and-permission-flow.md; this handoff section and final delivery entry. Existing overlapping edits are preserved.
- Owner: Codex root
- Dependencies: existing `zsjos_lead.dispatch_mode` contract and Ant Design Tag; no new dependency
- Integration order: preserve overlapping changes; exempt `specified` from counterparty identity masking; render new-media dispatch-mode tags from the server field; add focused tests; run backend and Workbench verification; append delivery entry
- Verification plan: focused LeadManagementServiceImpl tests for automatic masking and specified mutual visibility; focused Workbench presentation tests; Workbench full tests, typecheck and production build; desktop/mobile browser checks when the local runtime and usable authenticated state are available; scoped diff and whitespace checks

## Active delivery: submitter appeal visibility

- Workstream ID: main-submitter-appeal-visibility
- Goal: preserve a submitter's appeal-record tab and submission entry after a Lead is judged invalid, without widening appeal reads to unrelated Lead viewers
- Non-goals: change appeal workflow rounds, reviewer assignment, BPM process behavior, partner appeal semantics, database/schema/data, branches, commits, or shared external state
- Branch: main
- Worktree: D:\ZSJ-OS
- Base commit: 7d4a9ae2f959ccfaee2a389bfac2a7b94cccae25
- Target branch: main
- Ownership scope: Lead detail visible-tab projection, LeadAppealController read permission expression, LeadAppealServiceImpl submitter read object check, Workbench LeadDetail-to-LeadAppealPanel boundary, LeadAppealPanel submission visibility and its focused tests, focused backend tests, affected permission-flow documentation, and this handoff section/delivery entry. Existing overlapping edits are preserved.
- Owner: Codex root
- Dependencies: existing `source_user_id`, Lead object reader, `zsjos:lead:appeal:create`, `zsjos:lead-detail:appeal-read`, and BPM appeal contract; no new dependency
- Integration order: preserve overlapping changes; allow create-permission callers into the read endpoint only for Service-level submitter authorization; project the appeal tab for authorized submitters; derive Workbench submission visibility from the server-projected submitter relation instead of the unified page mode; add tests and run focused/proportional verification; append delivery entry
- Verification plan: focused LeadManagementServiceImpl and LeadAppealServiceImpl/controller permission tests; focused Workbench appeal-submission visibility tests including unified `all` mode behavior; Workbench full tests, typecheck and build; scoped diff/whitespace checks; authenticated browser verification when a usable session is available

## Active delivery: workbench message-center popup

- Workstream ID: main-workbench-message-popup
- Goal: restore the Workbench message-center bell to an Admin-like fixed-height unread-message popup with a scrollable list
- Non-goals: change backend APIs, permissions, database/schema, Admin frontend, full message-page cursor behavior, branches, commits, or shared service state
- Branch: main
- Worktree: D:\ZSJ-OS
- Base commit: 7d4a9ae2f959ccfaee2a389bfac2a7b94cccae25
- Target branch: main
- Ownership scope: frontend/workbench/src/components/MessageCenter.tsx and popup-specific styles/tests
- Owner: Codex root
- Dependencies: existing Workbench notify-message API, NotifyMessageProvider, realtime provider, and notifyMessageAction service; no new dependency
- Integration order: replace bell navigation with controlled popup; render unread list and fixed scrolling body; preserve full-message navigation and existing action/read synchronization; run focused Workbench checks
- Verification plan: Workbench tests, typecheck, production build, and desktop/mobile browser checks when an authenticated session is available

## Active delivery: independent partner API prefix

- Workstream ID: main-part-api-prefix
- Goal: expose the partner H5 API under `/part-api/zsjos/**` and enforce PARTNER token routing while preserving `/app-api` for member APIs
- Non-goals: change database schema/data, partner account tables, business endpoint suffixes, ADMIN or MEMBER routes, branches, commits, or shared service state
- Branch: main
- Worktree: D:\ZSJ-OS
- Base commit: 7d4a9ae2f959ccfaee2a389bfac2a7b94cccae25
- Target branch: main
- Ownership scope: web API prefix/path matching and security user-type routing; yudao-server configuration; frontend/h5 API base and dev proxy; focused route configuration tests and directly affected API documentation
- Owner: Codex root
- Dependencies: existing WebProperties, security and tenant filters; no new dependency
- Integration order: add PARTNER API prefix support; map partner app Controllers; update H5 development/production base URLs and proxy; add focused verification; append delivery entry
- Verification plan: framework and server tests for `/part-api` mapping and PARTNER type detection; H5 typecheck/build; scoped diff/whitespace validation

- Workstream ID: main
- Goal: make an approved sales-supervisor add-sign a required completion condition for the requesting center, so the order waits for every requested supervisor as well as both centers, and normalize supervisor actions to approve/reject wording
- Non-goals: change BPM definitions, task keys, permissions, database schema or data, approval ownership, dependencies, branches, commits, or shared-service state
- Branch: main
- Worktree: D:\ZSJ-OS
- Base commit: 7d4a9ae2f959
- Target branch: main
- Ownership scope: BPM parallel add-sign completion behavior and focused tests; ZSJOS sales-order decision/supervisor-confirmation services and focused tests; Workbench supervisor inbox and focused guards; directly affected sales-order API, architecture, business, deployment and fresh-schema comments; this handoff file. Existing overlapping edits are preserved.
- Owner: Codex root
- Dependencies: existing BPM public add-sign/task APIs, ZSJOS approval round contract, and Workbench React/Ant Design 6 stack; no new dependency
- Integration order: preserve overlapping changes; make parent approval wait for every parallel add-sign child; retain immediate rejection semantics; stop cancelling pending supervisor confirmation on center approval; normalize supervisor approve/reject wording; update tests and docs; run focused and proportional verification; append delivery entry
- Verification plan: focused BPM tests for parent-first and supervisor-first approval plus rejection behavior; focused ZSJOS tests proving center approval retains the pending supervisor and center rejection cancels it; focused Workbench guards; Workbench full tests, typecheck and production build; browser checks at desktop/mobile when an authenticated runtime is available; scoped diff/whitespace validation

## Delivery 2026-08-16 18:00:49 +08:00

- Branch: main
- Worktree: D:\ZSJ-OS
- HEAD commit: 69741138b4c3fee9dc051fbdddf87f30a9ba5e49
- User goal: implement independent partner accounts and unified authorization, beginning with the current all-403 partner H5 failure
- Key decisions: preserve historical migrations; repair V063 by permission identity; logically delete only accidental work-plan grants; keep withdrawal review ungranted; do not leave a partially wired PARTNER principal implementation in the runtime
- Execution or analysis result: completed the forward permission repair and verification contract; investigated the System principal refactor and identified URL-derived app-api user types and user-role keys as required cross-framework changes; independent account runtime remains pending
- Changed files: handoff/main.md; script/sql/mysql/migrations/V068__repair_partner_permissions.sql; script/sql/mysql/bootstrap.sql; script/sql/mysql/verify-bootstrap.sql; script/sql/mysql/migrations/README.md; docs/architecture/data-and-permission-flow.md
- Verification evidence: `zsjos-db check` PASS; V068 partner permission check PASS in controlled `zsjos-db test-fresh`; System dependency graph compile PASS during the reverted compatibility experiment; fresh test retains unrelated existing failures for lead_filter_versions, module_schema_versions, and V064
- Dependency or integration impact: V068 must be applied before relying on H5 partner feature permissions; no production or local shared database was modified
- Remaining work: implement the independent System partner account table, PARTNER token routing across ZSJOS and approved System app APIs, subject-typed role relations/cache keys, profile and audit adapters, ZSJOS partner-account mapping, migration rehearsal, API tests, and H5 verification

## Delivery 2026-08-16 18:18:01 +08:00

- Branch: main
- Worktree: D:\ZSJ-OS
- HEAD commit: 69741138b4c3fee9dc051fbdddf87f30a9ba5e49
- User goal: fix the slow initial load when accessing the admin frontend through port 80
- Key decisions: bind the local Vite server to the IPv6 unspecified address so Windows provides dual-stack access; disable Baidu analytics only in local mode; leave backend, port, proxy and business behavior unchanged
- Execution or analysis result: Vite automatically reloaded its existing PID 20220 after the configuration change and now listens on `[::]:80`; localhost no longer incurs IPv6-to-IPv4 fallback; local pages no longer inject the Baidu analytics script
- Changed files: frontend/admin/vite.config.ts; frontend/admin/.env.local; handoff/main.md
- Verification evidence: actual port 80 returned HTTP 200 for localhost, 127.0.0.1 and 192.168.2.38 with 0.0004-0.0005 second connect times and about 0.010-0.012 second totals; browser loaded the Zhongshijian login page to complete state with no hm.baidu.com script; `pnpm build:local` PASS in 24.78 seconds; `git diff --check` PASS; `pnpm ts:check` remains blocked by eight existing errors outside the changed files; `pnpm lint` remains blocked by existing style errors outside the changed files
- Dependency or integration impact: no dependency, backend, API, proxy, port or production analytics configuration changes; local Vite development now accepts IPv4, IPv6 and LAN access
- Remaining work: unrelated existing admin typecheck and lint failures remain; the independent partner-account runtime refactor from the prior delivery is still pending

## Delivery 2026-08-16 19:53:38 +08:00

- Branch: main
- Worktree: D:\ZSJ-OS
- HEAD commit: 69741138b4c3fee9dc051fbdddf87f30a9ba5e49
- User goal: fix the post-login Vue Router exception caused by the invalid `partner-portal` route after V068
- Key decisions: add forward-only V069 instead of rewriting V068; logically retire the app-api-only `ZsjosPartnerPortal` admin menu; migrate the guard from deprecated `next()` callbacks to returned navigation results and always release loading state
- Execution or analysis result: implemented the V069 migration, bootstrap and verification wiring, migration documentation, and Vue Router 5 guard update; migration was not executed because no local MySQL client/database process was available
- Changed files: script/sql/mysql/migrations/V069__remove_invalid_partner_admin_route.sql; script/sql/mysql/bootstrap.sql; script/sql/mysql/migrations/README.md; script/sql/mysql/verify-bootstrap.sql; frontend/admin/src/permission.ts; handoff/main.md
- Verification evidence: `pnpm build:local` PASS in 24.19 seconds; focused ESLint and Prettier checks for `src/permission.ts` PASS; browser regression loaded `http://127.0.0.1/login?redirect=/index`; `git diff --check` PASS; full `pnpm ts:check` remains blocked by eight existing errors outside the changed files; SQL execution remains unverified because MySQL is unavailable locally
- Dependency or integration impact: V069 must be applied to each existing database before the invalid menu is removed; fresh bootstrap now sources V069; no business rows or H5 permissions are changed
- Remaining work: execute V069 in a controlled database, verify `get-permission-info` and partner/employee login after migration, and complete the independent partner-account runtime refactor

## Delivery 2026-08-16 21:12:35 +08:00

- Branch: main
- Worktree: D:\ZSJ-OS
- HEAD commit: 69741138b4c3fee9dc051fbdddf87f30a9ba5e49
- User goal: implement the complete H5 API contract and all-role ZSJOS menu-permission repair, retaining the WeCom login path as an unavailable entry without connecting OAuth or a backend login endpoint
- Key decisions: use anonymous tenant-only clients for System dictionary/area data; keep partner identities as ADMIN users for `/app-api/zsjos/**`; unify HTTP/business 401 recovery into one raw-Axios refresh flight; expose personal messages through role plus ownership checks rather than copied menu permissions; define V071 grants by stable role/permission codes; treat the explicit zero-menu list as eighteen roles; keep V071 generated-only and preserve all applied migrations and real account state
- Execution or analysis result: aligned all active H5 calls and DTOs, corrected Lead actions/supplement/product/identifier behavior and withdrawal fields, retained the inert WeCom entry, added retryable remote states and desktop H5 sizing, added the System partner-message Controller and tests, added the repeatable V071/bootstrap/verification chain, and documented the complete 34-role target matrix. The latest WeCom decision changes the surface from 38 callable endpoints to 37 callable contracts plus one unavailable retained entry.
- Changed files: frontend/h5/.env.development; frontend/h5/src/api/{auth,cashback,lead,message,reference,request,withdrawal}.ts; frontend/h5/src/composables/{useAuth,useDict,usePageList}.ts; frontend/h5/src/main.ts; frontend/h5/src/pages/{earnings,home,lead,login,messages,withdrawal} affected Vue files; frontend/h5/src/router/index.ts; frontend/h5/src/stores/user.ts; frontend/h5/src/styles/base.css; frontend/h5/src/utils/{format,storage}.ts; frontend/h5/兼职端API接口.md; backend System AppAreaNodeRespVO and partner message Controller/test; script/sql/mysql/bootstrap.sql, V071, migration README, migration test runner and verify-bootstrap.sql; directly affected API/architecture/menu/migration documentation; handoff/main.md
- Verification evidence: H5 `npm run build` PASS; mobile 390x844 and desktop 1440x900 browser checks PASS with no horizontal overflow, centered 540px desktop canvas, retained WeCom toast and unchanged login URL; System partner-message focused tests PASS 6/6; Maven package for System, BPM and ZSJOS dependency chain PASS; static audit mapped all 37 active H5 HTTP contracts to Controller routes and confirmed no source calls to `/zsjos/lead/area-tree` or `/zsjos/auth/wecom-login`; local 48080 public dictionary and area requests returned HTTP 200 and unauthenticated message returned business code 401; `zsjos-db check` PASS; guardrails PASS; V071 executed twice in isolated fresh MySQL and every V071 permission, duplicate, zero-role and menu-parent check passed; scoped `git diff --check` PASS
- Dependency or integration impact: no dependency, branch, commit, applied-migration checksum, real account permission, business row, BPM instance or existing database was changed. Deploying the compiled backend requires the normal application restart; applying V071 to an existing database still requires separate approval and a reviewed role-menu snapshot.
- Remaining work: the currently running 48080 backend is an older process and still returns area nodes without `selectionCode`/`leafSelectable`, so runtime contract verification must be repeated after an approved deployment/restart; authenticated partner end-to-end flows and concurrent refresh require a non-sensitive test account/environment; fresh and upgrade SQL suites retain pre-existing `lead_filter_versions` and `module_schema_versions` failures outside V071; no `spring-security-test` dependency was added, so non-partner method-security behavior is evidenced by the class annotation/framework and unauthenticated real request rather than a dedicated Spring security integration test

## Delivery 2026-08-16 21:19:08 +08:00

- Branch: main
- Worktree: D:\ZSJ-OS
- HEAD commit: 69741138b4c3fee9dc051fbdddf87f30a9ba5e49
- User goal: fix V071 failure `[HY000][1267] Illegal mix of collations` when role and permission codes use different `utf8mb4` collations
- Key decisions: compare stable role and permission codes with binary exact equality on both operands; reproduce the reported mismatch by changing only an isolated test database default to `utf8mb4_0900_ai_ci` after the unicode-collated bootstrap; preserve the V071 authorization matrix and do not execute it against an existing database
- Execution or analysis result: updated all V071 comparisons between temporary grant codes and System role/menu codes; extended the fresh integration test so V071 is rerun with mixed table/default collations and remains repeatable
- Changed files: script/sql/mysql/migrations/V071__repair_h5_and_role_permissions.sql; script/sql/mysql/tools/zsjos_db.py; handoff/main.md
- Verification evidence: `zsjos-db.ps1 check` PASS; `test-guardrails` PASS; mixed-collation `test-fresh` passed V071 execution and all V071 permission checks, then retained only the pre-existing `lead_filter_versions` and `module_schema_versions` failures; `python -m py_compile script/sql/mysql/tools/zsjos_db.py` PASS; scoped diff whitespace checks reported no errors
- Dependency or integration impact: no dependency, role matrix, business row, account grant, existing database, branch, or external service was changed; the corrected idempotent V071 may be rerun after a separately approved existing-database execution
- Remaining work: resolve the unrelated fresh-suite `lead_filter_versions` and `module_schema_versions` baseline failures separately; applying V071 to any existing database still requires explicit confirmation and a reviewed role-menu snapshot

## Delivery 2026-08-16 21:22:50 +08:00

- Branch: main
- Worktree: D:\ZSJ-OS
- HEAD commit: 69741138b4c3fee9dc051fbdddf87f30a9ba5e49
- User goal: remove the unsafe-query warning for the V071 role-menu restore UPDATE
- Key decisions: retain the existing inner-join target restriction and add an explicit WHERE on the same role-menu ID plus the expected logical-deleted state; do not disable client safety checks or change the authorization matrix
- Execution or analysis result: the V071 restore statement now visibly limits updates to deleted relations selected in `tmp_v071_restore`, preserving idempotent behavior while satisfying WHERE-based SQL safety inspection
- Changed files: script/sql/mysql/migrations/V071__repair_h5_and_role_permissions.sql; handoff/main.md
- Verification evidence: `zsjos-db.ps1 check` PASS; `test-guardrails` PASS; mixed-collation `test-fresh` passed V071 execution, repeat execution, and V071 permission checks, retaining only the pre-existing `lead_filter_versions` and `module_schema_versions` failures; scoped whitespace checks reported no errors
- Dependency or integration impact: no dependency, permission target, business row, real account, existing database, branch, or external service was changed
- Remaining work: applying the corrected V071 to any existing database still requires explicit confirmation and a reviewed role-menu snapshot; unrelated fresh-suite baseline failures remain

## Delivery 2026-08-16 21:37:44 +08:00

- Branch: main
- Worktree: D:\ZSJ-OS
- HEAD commit: 69741138b4c3fee9dc051fbdddf87f30a9ba5e49
- User goal: fix the H5 frontend development server to port 10086
- Key decisions: configure the port in the existing Vite server block and enable `strictPort` so an occupied port causes startup failure instead of silently selecting another port; preserve the existing proxy target, preview command, dependencies, and H5 behavior
- Execution or analysis result: changed the H5 development port from 5175 to 10086; the already-running H5 Vite process reloaded the configuration and now serves the application on `localhost:10086`; the temporary verification process was stopped after testing
- Changed files: frontend/h5/vite.config.ts; handoff/main.md
- Verification evidence: H5 `npm run build` PASS; the retained Vite process listens on `[::1]:10086`; `http://localhost:10086/` returned HTTP 200 with the application root; a concurrent startup failed with `Port 10086 is already in use`, confirming strict-port behavior; scoped `git diff --check` PASS with a line-ending warning only
- Dependency or integration impact: no dependency, API proxy, backend, database, account, permission, business data, branch, or external shared service changed
- Remaining work: None

## Delivery 2026-08-17 17:51:16 +08:00

- Branch: main
- Worktree: D:\ZSJ-OS
- HEAD commit: 84474ae6083a64343f5b39b397e5143b233523ae
- User goal: fix partner login/update optimistic-lock failures and make expired-token logout type-safe, idempotent, and locally authoritative in the partner H5
- Key decisions: register the existing MyBatis-Plus optimistic-lock interceptor globally; require every versioned partner-account update to affect exactly one row before token side effects; add an OAuth2 revocation overload guarded by expected user type; treat logout audit as best effort; let normal H5 requests refresh once while logout requests never refresh and always clear local state after confirmation
- Execution or analysis result: implemented stable concurrent-modification handling for partner login, enable/disable, mobile, and password updates; PARTNER logout now removes persisted expired access/refresh tokens and caches without touching ADMIN/MEMBER tokens, and missing tokens are idempotent success; H5 logout separates user cancellation from server failure, suppresses expired-token refresh/toast on logout, clears all authentication state in `finally`, and uses centralized login redirection with return targets only for passive session expiry; synchronized API and authentication-flow documentation
- Changed files: backend/yudao-framework/yudao-common OAuth2TokenCommonApi; backend/yudao-framework/yudao-spring-boot-starter-mybatis YudaoMybatisAutoConfiguration and focused test; backend/yudao-module-system OAuth2TokenApiImpl, OAuth2TokenService, OAuth2TokenServiceImpl and focused test; backend/yudao-module-zsjos ZsjosErrorCodeConstants, PartnerAccountService/Impl, PartnerAuthServiceImpl and focused tests; frontend/h5/src/api/request.ts; frontend/h5/src/api/auth.ts; frontend/h5/src/pages/profile/index.vue; docs/api/partner-app-api.md; frontend/h5/兼职端API接口.md; docs/architecture/data-and-permission-flow.md; handoff/main.md
- Verification evidence: MyBatis configuration test PASS 1/1; typed OAuth2 revocation tests PASS 3/3; partner account/auth tests PASS 11/11; H5 `npm run build` PASS; browser check on `localhost:10086` confirmed the login page renders without console errors and unauthenticated `/profile` preserves `/profile` as the return target; server dependency graph compiled all 28 modules and package PASS with `spring-boot.repackage.skip=true`; scoped `git diff --check` PASS. The complete `yudao-module-zsjos -am test` reactor remains blocked before System/BPM/ZSJOS by the unrelated existing `CodegenEngineUniappTest.testExecute_treeSearch` failure in Infra. Standard executable-JAR repackage compiled all modules but could not rename the currently running locked `yudao-server.jar`.
- Dependency or integration impact: no dependency, database schema/data, migration, account permission, branch, or shared-service state changed; deployment requires replacing/restarting the backend before the new interceptor and revocation implementation are active
- Remaining work: after an approved backend deployment/restart and with a non-sensitive partner test account, run real HTTP/mobile regressions for login version increment, valid/expired/repeated logout, wrong subject type, refresh success/failure, network failure, and active-logout no-return-target behavior; resolve the unrelated Infra codegen test baseline separately

## Delivery 2026-08-18 13:49:37 +08:00

- Branch: main
- Worktree: D:\ZSJ-OS
- HEAD commit: 7d4a9ae2f959ccfaee2a389bfac2a7b94cccae25
- User goal: continue real-browser verification and fix the partner Lead English status plus the reproducible Workbench console warnings
- Key decisions: centralize H5 Lead status labels in the existing format utility; never expose an unknown protocol value directly; expose `won` through the existing partner status query instead of adding an API; replace only the two Ant Design props reproduced on the Lead workbench
- Execution or analysis result: partner home, Lead list, and Lead detail now render `won` as `已成交`; the partner Lead list includes an `已成交` tab; the AdvancedFilter Drawer and global Lead assignment Modal use the Ant Design 6 APIs without the prior warnings
- Changed files: frontend/h5/src/utils/format.ts; frontend/h5/src/pages/home/index.vue; frontend/h5/src/pages/lead/list.vue; frontend/h5/src/pages/lead/detail.vue; frontend/workbench/src/components/AdvancedFilter.tsx; frontend/workbench/src/components/LeadAssignmentHost.tsx; handoff/main.md
- Verification evidence: H5 `npm run build` PASS; Workbench `npm test` PASS (43 files, 230 tests), `npm run typecheck` PASS, and `npm run build` PASS; authenticated browser verification confirmed the partner `已成交` filter returns the expected record and home/list/detail all show Chinese status; 390x844 partner and Workbench views had no horizontal overflow or overlapping controls; desktop H5 retained its 540px application canvas; related browser console error/warning logs were empty; scoped `git diff --check` reported no whitespace errors
- Dependency or integration impact: no dependency, backend contract, database, permission, account, business data, branch, or shared-service state changed; existing unrelated worktree changes were preserved
- Remaining work: the Workbench production bundle still reports the pre-existing large-chunk advisory; the temporary H5 verification server was stopped after testing

## Delivery 2026-08-18 14:07:28 +08:00

- Branch: main
- Worktree: D:\ZSJ-OS
- HEAD commit: 7d4a9ae2f959ccfaee2a389bfac2a7b94cccae25
- User goal: prevent the Lead management page from switching to another Lead after a follow-up record is submitted
- Key decisions: refresh the affected list and detail without replacing them with initial-loading skeletons; explicitly prefer the changed Lead ID when reconciling the refreshed activity-sorted page; retain a manual selection over stale route state; fall back to the first refreshed Lead only when the changed Lead is no longer in the filtered result; avoid a stale-detail race by coordinating detail refresh after list reconciliation
- Execution or analysis result: the just-followed Lead remains selected while the refreshed list moves it according to `lastActivityAt`; list and detail refresh silently; stale route state no longer overrides a later manual selection
- Changed files: frontend/workbench/src/pages/LeadManagementPage.tsx; frontend/workbench/src/pages/lead-management-unified.guard.test.ts; frontend/workbench/src/services/leadManagement.ts; frontend/workbench/src/services/leadManagement.test.ts; handoff/main.md
- Verification evidence: focused Lead management tests PASS 18/18; Workbench full tests PASS 43 files and 234/234 tests; `npm run typecheck` PASS; `npm run build` PASS with the existing large-chunk advisory; scoped `git diff --check` PASS. The local Workbench opened successfully in the in-app browser, but the available browser session was unauthenticated, so an actual follow-up submission at desktop and mobile widths remains unverified.
- Dependency or integration impact: no new dependency, backend contract, database, permission, filter, branch, commit, or shared-service change; existing overlapping uncommitted Lead management work was preserved
- Remaining work: repeat the authenticated follow-up submission flow at desktop and mobile widths when a non-sensitive signed-in browser session is available; the existing production bundle large-chunk advisory remains

## Delivery 2026-08-18 14:13:17 +08:00

- Branch: main
- Worktree: D:\ZSJ-OS
- HEAD commit: 7d4a9ae2f959ccfaee2a389bfac2a7b94cccae25
- User goal: make the visible follow-up record timeline refresh automatically after submitting through the standalone follow-up modal
- Key decisions: keep the inline follow-up form's existing direct record reload; add a dedicated refresh version for standalone-modal success; reload the mounted panel's first page without remounting it or depending on a Lead ID/category change; preserve the prior silent list refresh and selected-Lead reconciliation
- Execution or analysis result: a successful standalone follow-up now refreshes the visible timeline and its total before/alongside the parent Lead list and detail refresh; inline submission and aging-pool use remain on their existing paths
- Changed files: frontend/workbench/src/components/LeadDetail.tsx; frontend/workbench/src/components/LeadFollowUpPanel.tsx; frontend/workbench/src/pages/lead-management-unified.guard.test.ts; handoff/main.md
- Verification evidence: focused Lead management tests PASS 23/23; Workbench full tests PASS 43 files and 235/235 tests; `npm run typecheck` PASS; `npm run build` PASS with the existing large-chunk advisory; scoped `git diff --check` PASS. Authenticated browser submission remains unverified because the available in-app browser session has no login state.
- Dependency or integration impact: no new dependency, backend contract, database, permission, sorting, filter, branch, commit, or shared-service change; existing overlapping uncommitted LeadDetail and Lead management changes were preserved
- Remaining work: repeat standalone-modal and inline follow-up submissions at desktop and mobile widths when a non-sensitive signed-in browser session is available; the existing production bundle large-chunk advisory remains

## Delivery 2026-08-18 14:23:53 +08:00

- Branch: main
- Worktree: D:\ZSJ-OS
- HEAD commit: 7d4a9ae2f959ccfaee2a389bfac2a7b94cccae25
- User goal: left-align order-record field labels and right-align their displayed values
- Key decisions: scope both alignment rules under `.sales-order-detail` so the shared `DetailFieldGrid` behavior remains unchanged on Lead, registration, aging-pool, and other surfaces; preserve existing wrapping and responsive grid behavior
- Execution or analysis result: order-detail `dt` labels now align left and `dd` values align right across the order record cards
- Changed files: frontend/workbench/src/styles/pages/sales-order.css; frontend/workbench/src/styles/styles.guard.test.ts; handoff/main.md
- Verification evidence: focused style guard PASS 18/18; Workbench full tests PASS 43 files and 236/236 tests; `npm run typecheck` PASS; `npm run build` PASS with the existing large-chunk advisory; scoped `git diff --check` PASS. Authenticated desktop/mobile order-detail browser verification remains unavailable because the current browser session has no login state.
- Dependency or integration impact: no component, API, backend, database, permission, dependency, branch, commit, or shared-service change; existing overlapping uncommitted sales-order styles and style guards were preserved
- Remaining work: visually confirm an authenticated order detail at desktop and mobile widths when a non-sensitive signed-in browser session is available; the existing production bundle large-chunk advisory remains

## Active Workstream

- Workstream ID: study-planner-student-history
- Goal: let an assigned study planner see the student's complete historical Lead overview, follow-up records, and order records in the same detail interface as Lead management, while hiding appeals and complaints
- Non-goals: no mutation authority for planners; no changes to historical Lead, follow-up, order, appeal, or complaint data; no database schema/data, dependency, role-name inference, branch, commit, or shared-service changes
- Branch: main
- Worktree: D:\ZSJ-OS
- Base commit: 7d4a9ae2f959ccfaee2a389bfac2a7b94cccae25
- Target branch: main
- Ownership scope: ZSJOS student-to-Lead/follow-up/order read authorization through active service relations; My Student response contract; Workbench My Students detail reuse and focused tests; directly affected registration and permission documentation; this handoff record. Existing overlapping edits are preserved.
- Owner: Codex / root
- Dependencies: existing `zsjos_service_relation`, student object-permission boundary, Lead management/follow-up APIs, customer-order APIs, and Workbench React/Ant Design detail components; no new dependency
- Integration order: expose the server-authoritative Lead relationship for an assigned student; extend read-only object authorization without mutation grants; render the existing Lead detail in student mode with overview/follow-ups/orders only; update tests/docs; run focused and proportional verification; append delivery entry
- Verification plan: focused backend student/Lead/follow-up/order authorization tests covering assigned and unrelated users; focused Workbench tab and My Students guards; Workbench full tests, typecheck and production build; browser checks at desktop/mobile widths; scoped diff and whitespace validation

## Delivery 2026-08-18 14:32:08 +08:00

- Branch: main
- Worktree: D:\ZSJ-OS
- HEAD commit: 7d4a9ae2f959ccfaee2a389bfac2a7b94cccae25
- User goal: fix sales-order submission failure when a leaf-selectable province uses `cityCode=OTHER` and sends an empty `cityName`, and remove the Hibernate Validator container-cascade warnings
- Key decisions: keep province/city codes as the request authority and let the existing order service resolve name snapshots; allow an empty client `cityName`; move nested validation to list type arguments without weakening item or voucher validation; document the leaf-province contract
- Execution or analysis result: requests with an empty `cityName` now pass Controller bean validation and reach server-side region resolution; item and payment-voucher nested validation remains active; the two `HV000271` warnings for these lists are removed
- Changed files: backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/order/vo/SalesOrderSubmitReqVO.java; backend/yudao-module-zsjos/src/test/java/cn/iocoder/yudao/module/zsjos/controller/admin/order/vo/SalesOrderSubmitReqVOValidationTest.java; docs/api/zsjos-sales-order.md; handoff/main.md
- Verification evidence: focused ZSJOS validation and order service tests PASS 27/27 with the full dependency graph compiled; standalone validation test PASS 2/2; scoped tracked-file `git diff --check` PASS with line-ending notices only; new test trailing-whitespace check PASS. The full `mvn -f backend/pom.xml -pl yudao-module-zsjos -am test` reactor remains blocked before ZSJOS by the existing unrelated `CodegenEngineUniappTest.testExecute_treeSearch` failure in Infra.
- Dependency or integration impact: no new dependency, frontend, API route, database, migration, permission, branch, commit, or shared-service state change; deploying the backend is required before the runtime endpoint uses the corrected validation contract
- Remaining work: after an approved backend deployment/restart, repeat the reported order submission as a real authenticated HTTP request; resolve the unrelated Infra codegen test baseline separately

## Delivery 2026-08-18 16:10:59 +08:00

- Branch: main
- Worktree: D:\ZSJ-OS
- HEAD commit: 7d4a9ae2f959ccfaee2a389bfac2a7b94cccae25
- User goal: correct supervisor add-sign completion semantics so a requested supervisor becomes a required approver alongside the requesting center, and change supervisor actions from confirmation wording to approve/reject wording
- Key decisions: when a parallel-sign parent is approved first, persist `APPROVING`, move its assignee to owner, and keep the BPM parent task running without leaving it in the center todo list; when the supervisor child is approved first, clear the add-sign scope but leave the parent center task actionable; complete the parent only when both parent and all parallel children have approved; retain immediate BPM rejection behavior; only cancel a pending ZSJOS supervisor confirmation when its center rejects, not when its center approves
- Execution or analysis result: a center that applied for supervisor approval now waits for that supervisor even if the center approved first; a supervisor approval waits for the center when it arrives first; all required centers and requested supervisors must approve before the process can complete; supervisor inbox buttons, modal actions, statuses, messages, Controller summaries and affected documentation now use “通过/驳回” language
- Changed files: `backend/yudao-module-bpm/src/main/java/cn/iocoder/yudao/module/bpm/service/task/BpmTaskServiceImpl.java`; new and updated BPM parallel-sign tests; `backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/order/SalesOrderServiceImpl.java` and sales-order Controller; focused ZSJOS order test; `frontend/workbench/src/components/SalesOrderSupervisorInbox.tsx` and unified approval guard; sales-order API, permission-flow, state-machine and deployment documentation; this handoff file. Existing overlapping worktree changes were preserved.
- Verification evidence: BPM focused tests PASS 12/12; ZSJOS focused tests PASS 34/34 with the full dependency chain compiled; Workbench focused guard PASS 4/4 and full tests PASS 272/272; Workbench `npm run typecheck` PASS; production build PASS with the existing large-chunk advisory; in-app browser checks at 1440x900 and 390x844 PASS with no horizontal overflow or console errors on the available login page; stale-rule scan found no affected-surface remnants; scoped tracked-file and new-test whitespace checks PASS with line-ending notices only. A concurrent Maven attempt was discarded after it raced on generated output; the subsequent serialized builds passed. The full Reactor test remains known to be blocked by the unrelated existing Infra `CodegenEngineUniappTest.testExecute_treeSearch` failure when run across the repository.
- Dependency or integration impact: no new dependency, BPMN definition, task key, permission, database schema/data, migration, branch, commit, or shared-service state changed; backend and Workbench deployment/restart are required before runtime behavior is active
- Remaining work: after deployment and with a non-sensitive authenticated account, run the four live order permutations (center-first approve, supervisor-first approve, center reject, supervisor reject) at desktop and mobile widths; the current browser session was unauthenticated, so those live task interactions remain unverified

## Delivery 2026-08-18 15:52:19 +08:00

- Branch: main
- Worktree: D:\ZSJ-OS
- HEAD commit: 7d4a9ae2f959ccfaee2a389bfac2a7b94cccae25
- User goal: keep registration and finance approval visible and actionable while sales-supervisor confirmation is pending, implement the supervisor confirmation as an add-sign operation, and replace user-facing dual-center/countersign submission terminology with generic approval copy
- Key decisions: add a BPM `PARALLEL` sign type because the existing `BEFORE` sign type puts the parent task into WAIT; retain the original center task assignee and actionability while creating a supervisor child task; let supervisor rejection reject the round, and cancel an unfinished supervisor child/confirmation when the ordinary center decision wins; preserve BPM definitions, task keys, permissions, database structure, and internal dual-center terminology where operationally necessary
- Execution or analysis result: registration/finance approvers can continue to see and approve or reject the order during supervisor add-sign review; supervisor approval no longer removes the center approval, supervisor rejection still rejects the round, and an ordinary center decision closes the pending supervisor confirmation consistently; order entry, approval pages, status cards, Controller summaries, tests, and directly affected documentation now use generic user-facing approval wording
- Changed files: BPM task sign enum, public process-task API/implementation, task service, and focused tests; ZSJOS sales-order Controller, error constants, order/supervisor-confirmation services, and focused tests; Workbench SalesOrderDetailCards, SalesOrderEntryModal, SalesOrderApprovalPage, and approval guard; sales-order API, permission-flow architecture, state-machine and deployment documentation; fresh-schema comments in `00-bootstrap-schema.sql` and `schema/core.sql`; handoff/main.md
- Verification evidence: BPM focused tests PASS 9/9; ZSJOS focused tests PASS 34/34; Workbench focused guard PASS 3/3 and full tests PASS 265/265; Workbench typecheck PASS; Workbench production build PASS with the existing large-chunk advisory; full ZSJOS dependency-chain compile PASS through BPM and ZSJOS; desktop 1440x900 and mobile 390x844 browser smoke checks showed no horizontal overflow or console errors on the accessible login page; stale user-facing phrase scan found only the expected negative test assertion; scoped tracked-file and new-test whitespace checks PASS with line-ending notices only. The full `yudao-module-zsjos -am test` reactor remains blocked before BPM/ZSJOS by the unrelated existing `CodegenEngineUniappTest.testExecute_treeSearch` failure in Infra.
- Dependency or integration impact: no new dependency, BPMN definition, task key, permission, database schema/data, migration, branch, commit, or shared-service state changed; backend and Workbench deployment are required before the new runtime behavior and copy are active; existing unrelated worktree changes were preserved
- Remaining work: repeat the complete authenticated order flow for registration and finance at desktop/mobile widths after deployment, covering supervisor-first approve/reject and center-first approve/reject ordering; the available browser preview had no authenticated session, so these live workflow permutations remain unverified; resolve the unrelated Infra codegen test baseline separately

## Delivery 2026-08-18 16:34:48 +08:00

- Branch: main
- Worktree: D:\ZSJ-OS
- HEAD commit: 7d4a9ae2f959ccfaee2a389bfac2a7b94cccae25
- User goal: keep sales-visible Lead overview, follow-up history, and order history when a student is assigned to a study planner, while hiding appeals and complaints and reusing the complete Lead detail experience
- Key decisions: derive planner history access from an active `zsjos_service_relation.owner_user_id` relationship; expose internal `leadId` only as a technical link for directly owned students while keeping `leadNo` as the only visible Lead identifier; extend only the four required read controllers with `zsjos:student:query-my`; add a `sales-history-read` object action; render `LeadDetail` in `student-readonly` mode with overview/follow-ups/orders only and no actions; preserve routed collaborators' existing course-rights view
- Execution or analysis result: assigned study planners can load the existing Lead detail, follow-up pages, and customer order history through the student page; unrelated users and inactive service relationships fail object authorization; planner mode cannot create follow-ups, edit Lead data, qualify, create orders, or access appeal/complaint panels
- Changed files: backend Lead/follow-up/order read Controller permissions; `ServiceRelationMapper`, `LeadObjectPermissionService`, `LeadManagementServiceImpl`, `SalesOrderServiceImpl`, `MyStudentServiceImpl`, `MyStudentRespVO`; focused backend service and Controller permission tests; Workbench `RegistrationPages`, `LeadDetail`, API/types, lead-detail mode tests and student history guard; registration API and permission-flow documentation; this handoff file. Existing overlapping worktree edits were preserved.
- Verification evidence: Workbench `npm test` PASS (46 files, 276 tests); `npm run typecheck` PASS; `npm run build` PASS with the existing large-chunk advisory; ZSJOS dependency-chain focused Maven tests PASS (47 tests: student, Lead permission/management, Controller contract); scoped `git diff --check` PASS with only repository line-ending notices; browser smoke check at 1440x900 and 390x844 PASS with no horizontal overflow or console warnings on the unauthenticated local Workbench login page
- Dependency or integration impact: no new dependency, database schema/data, branch, commit, or shared-service state change; backend restart/deployment is required before the new student history authorization is active; frontend page is available at `/zsjos/my-students` and existing `/zsjos/leads/manage` remains unchanged for sales
- Remaining work: authenticated study-planner browser verification of a real assigned student and the three visible tabs remains unverified because the available browser session has no login state; full repository Maven reactor remains outside this focused check and retains unrelated baseline failures documented elsewhere

## Delivery 2026-08-18 16:36:27 +08:00

- Branch: main
- Worktree: D:\ZSJ-OS
- HEAD commit: 7d4a9ae2f959ccfaee2a389bfac2a7b94cccae25
- User goal: correction to the study-planner student history delivery
- Key decisions: limit the `student_service_owner` relation calculation to Lead detail conversions so ordinary Lead list responses do not issue one service-relation lookup per row; retain existence-based active service-owner authorization for object reads
- Execution or analysis result: final focused backend verification after the performance correction remains green
- Changed files: `backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/lead/LeadManagementServiceImpl.java`; this handoff file
- Verification evidence: ZSJOS dependency-chain focused Maven tests PASS (47/47)
- Dependency or integration impact: None
- Remaining work: None beyond the authenticated browser verification already recorded above

## Delivery 2026-08-18 19:58:07 +08:00

- Branch: main
- Worktree: D:\ZSJ-OS
- HEAD commit: 7d4a9ae2f959ccfaee2a389bfac2a7b94cccae25
- User goal: move the independent partner H5 API from the shared `/app-api` prefix to `/part-api` while keeping MEMBER and employee routes separate
- Key decisions: add a dedicated `partner-api` mapping for `controller.app.partner` before the generic app mapping; enforce PARTNER user type from the top-level prefix; retain `/zsjos/**` as the ZSJOS business namespace; keep public dictionary and area reads on unauthenticated `/app-api/system/**`; preserve ADMIN, MEMBER, database, account, and business endpoint suffix behavior
- Execution or analysis result: partner Controllers now map to `/part-api/zsjos/**`, API filters and Swagger include `/part-api`, H5 business and refresh requests use `/part-api`, and its public reference client continues to use `/app-api`; the old `/app-api/zsjos/**` Controller mapping is no longer exposed by path-prefix selection
- Changed files: WebProperties, YudaoWebAutoConfiguration, WebFrameworkUtils, ApiRequestFilter, TokenAuthenticationFilter comment, Swagger grouping, focused web framework tests, yudao-server application configuration, H5 environment/proxy/reference client, partner API documentation, cashback/withdrawal/submitter API docs, permission-flow and system-overview architecture docs, frontend/h5/兼职端API接口.md, and this handoff file
- Verification evidence: focused Web framework tests PASS 4/4; H5 `npm run build` PASS including vue-tsc and Vite production build; complete yudao-server dependency reactor compiled all 28 modules through server test compilation, but final Spring Boot repackage could not rename the existing locked `yudao-server.jar`; scoped `git diff --check` PASS
- Dependency or integration impact: no new dependency, database/schema/data, branch, commit, or shared-service state change; backend and H5 must be deployed together; production reverse proxy must forward `/part-api/**` as well as the retained `/app-api/**` public reference routes
- Remaining work: restart or redeploy the backend during an approved service window, configure the production reverse proxy for `/part-api/**`, and perform authenticated PARTNER login plus ADMIN/MEMBER token-rejection smoke checks against the deployed route

## Delivery 2026-08-18 21:28:00 +08:00

- Branch: main
- Worktree: D:\ZSJ-OS
- HEAD commit: 7d4a9ae2f959ccfaee2a389bfac2a7b94cccae25
- User goal: show the correct submitter for partner/new-media leads, show the selected new-media provider for sales-self leads, and leave submitter empty when a new sales-self lead has no provider while keeping filtering distinguishable
- Key decisions: add additive `source_provider_user_id` and `source_provider_recorded` Lead fields; keep legacy `source_user_id` unchanged for permissions and submitter actions; apply provider-aware display/filter semantics only to newly recorded sales-self leads; do not backfill historical rows
- Execution or analysis result: new sales-self submissions persist provider presence, Lead management responses suppress the fallback salesperson name when no provider was selected, advanced-filter submitter matching uses the provider-aware expression, and the Workbench renders empty submitter values as `-`
- Changed files: Lead DO, Lead submission and management services, advanced-filter catalog binding, focused Lead management test, Workbench Lead detail, core schema, V088 migration, bootstrap/verification SQL, migration README, and this handoff file
- Verification evidence: `mvn -f backend/pom.xml -pl yudao-module-zsjos -am -DskipTests compile` PASS; `mvn -f backend/pom.xml -pl yudao-module-zsjos -Dtest=LeadManagementServiceImplTest test` PASS (31/31); Workbench `npm run typecheck` PASS; Workbench `npm run build` PASS; scoped `git diff --check` reported no whitespace errors (only existing LF/CRLF conversion warnings)
- Dependency or integration impact: no new dependency, branch, commit, existing database execution, historical data rewrite, or external service change; V088 must be applied before relying on provider-aware behavior in an existing environment
- Remaining work: deploy/restart backend and apply V088 during an approved migration window; perform authenticated list/detail and advanced-filter smoke checks against a newly created sales-self lead with and without a provider

## Delivery 2026-08-18 21:58:51 +08:00

- Branch: main
- Worktree: D:\ZSJ-OS
- HEAD commit: 7d4a9ae2f959ccfaee2a389bfac2a7b94cccae25
- User goal: restore the Workbench message-center bell to an Admin-like popup that shows all unread messages through scrolling without allowing message volume to enlarge the window
- Key decisions: use the existing System notify-message cursor endpoint with `readStatus=false` instead of the Admin-compatible ten-item endpoint; load the next cursor when the fixed list nears its scroll bottom; reuse the existing message action/read synchronization service; retain a direct full-inbox entry; add loading, empty, retryable initial-error, and retryable load-more states; keep the popup at a responsive 400px width and 440px height ceiling
- Execution or analysis result: clicking the Workbench bell now opens a fixed-height unread-message popup; additional unread records load through continuous scrolling; selecting a message closes the popup and follows the existing authorized business/detail action while synchronizing read state; message quantity no longer changes popup height
- Changed files: `frontend/workbench/src/components/MessageCenter.tsx`; `frontend/workbench/src/components/MessageCenter.guard.test.ts`; popup-specific additions in `frontend/workbench/src/styles/pages/message-inbox.css`; `frontend/workbench/docs/architecture.md`; this handoff file. Existing overlapping Workbench style and handoff edits were preserved.
- Verification evidence: focused popup guard PASS 2/2; full Workbench tests PASS 49 files and 290 tests; `npm run typecheck` PASS; `npm run build` PASS with the existing large-chunk advisory; in-app browser checks on the latest local preview at 1440x900 and 390x844 showed no horizontal overflow and no console warnings/errors on the available login page; scoped `git diff --check` PASS with repository line-ending notices only; the untracked guard test has no trailing whitespace
- Dependency or integration impact: no new dependency, backend/API contract, database/schema/data, permission, menu, branch, commit, Admin frontend, or shared-service state change; Workbench deployment is required for the new bell behavior
- Remaining work: authenticated desktop/mobile verification of the real popup, unread cursor loading, message action, and read-count refresh remains unverified because the available browser session has no login state

## Delivery 2026-08-19 10:18:54 +08:00

- Branch: main
- Worktree: D:\ZSJ-OS
- HEAD commit: 7d4a9ae2f959ccfaee2a389bfac2a7b94cccae25
- User goal: make new-media specified assignments mutually identity-visible and show an automatic/specified assignment status tag beside the Lead source name
- Key decisions: use the persisted `dispatch_mode` as the authoritative distinction; exempt only `specified` from the existing counterparty identity mask; retain automatic-assignment, partner, sales-self, manager, and historical-null behavior; render tags only for `internal_new_media`; use blue for automatic assignment and orange for specified assignment; do not infer missing historical modes or add schema/data changes
- Execution or analysis result: specified-assignment submitter and owner detail views now receive each other's complete employee name and user ID; automatic assignment remains mutually masked; the Workbench source row displays `自动分配` or `指定派单` beside `新媒体提交` with a non-shrinking Ant Design tag and an ellipsizing source label; the architecture contract is synchronized
- Changed files: the identity-mask predicate in `backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/lead/LeadManagementServiceImpl.java`; focused cases in `backend/yudao-module-zsjos/src/test/java/cn/iocoder/yudao/module/zsjos/service/lead/LeadManagementServiceImplTest.java`; source-tag presentation in `frontend/workbench/src/components/LeadDetailOverview.tsx`; new `frontend/workbench/src/components/LeadDetailOverview.source.test.ts`; source-value styles in `frontend/workbench/src/styles/components/lead-detail-v2.css`; the identity-visibility paragraph in `docs/architecture/data-and-permission-flow.md`; this handoff file. Existing overlapping edits in modified files were preserved.
- Verification evidence: focused ZSJOS Maven test PASS 32/32; focused source-tag test PASS 2/2; full Workbench tests PASS 50 files and 292 tests; Workbench `npm run typecheck` PASS; Workbench `npm run build` PASS with the existing large-chunk advisory; scoped `git diff --check` PASS with repository line-ending notices only; the local Workbench server responded at `http://localhost:5174/`, but the available in-app browser session exposed only the login page
- Dependency or integration impact: no new dependency, database/schema/data, permission, dispatch workflow, branch, commit, or external/shared-service state change; backend and Workbench deployment are both required for the complete behavior
- Remaining work: authenticated desktop/mobile verification of the real Lead detail source row and both counterpart views remains unverified because the available browser session has no login state

## Delivery 2026-08-19 10:35:30 +08:00

- Branch: main
- Worktree: D:\ZSJ-OS
- HEAD commit: 7d4a9ae2f959ccfaee2a389bfac2a7b94cccae25
- User goal: restore the appeal entry for a new-media submitter after a salesperson marks the Lead invalid
- Key decisions: allow the original submitter to read the appeal history and see the appeals tab when the Lead object is readable, while retaining the existing appeal-read and review-permission checks for other readers; keep appeal submission subject to the existing create/review authorization and appeal-round limits
- Execution or analysis result: the appeal list Controller now admits submitter-capable requests into service-level object authorization; Lead appeal reads recognize the source user; Lead detail tab projection exposes appeals to the submitter; regression tests cover both the submitter path and an unrelated Lead reader without appeal capability
- Changed files: `backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/lead/LeadAppealController.java`; `backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/lead/LeadAppealServiceImpl.java`; `backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/lead/LeadManagementServiceImpl.java`; `backend/yudao-module-zsjos/src/test/java/cn/iocoder/yudao/module/zsjos/service/lead/LeadAppealServiceImplTest.java`; `backend/yudao-module-zsjos/src/test/java/cn/iocoder/yudao/module/zsjos/service/lead/LeadManagementServiceImplTest.java`; `docs/architecture/data-and-permission-flow.md`; this handoff file
- Verification evidence: focused Maven tests PASS (49/49 across `LeadAppealServiceImplTest` and `LeadManagementServiceImplTest`); prior Workbench full test suite PASS (50 files, 292 tests); prior Workbench `npm run typecheck` PASS; prior Workbench `npm run build` PASS with the existing large-chunk advisory; `git diff --check` PASS with repository LF/CRLF conversion notices only
- Dependency or integration impact: no new dependency, schema/data/migration, branch, commit, BPMN definition, or external/shared-service state change; backend and Workbench deployment are required; authenticated browser verification remains unavailable without a signed-in session
- Remaining work: deploy/restart the backend and Workbench, then verify as a submitter that an invalid Lead exposes the appeals tab, loads history, and permits a new appeal within the configured limit; verify unrelated readers remain denied

## Delivery 2026-08-19 10:54:30 +08:00

- Branch: main
- Worktree: D:\ZSJ-OS
- HEAD commit: 7d4a9ae2f959ccfaee2a389bfac2a7b94cccae25
- User goal: fix the still-missing appeal button for submitters of both historical and newly invalidated Leads in the unified Lead management page
- Key decisions: treat the server-projected `relationTypes` submitter relationship as the UI identity authority; remove the unified page `mode="all"` from appeal eligibility; retain invalid Lead status, upheld-decision sequencing, and the three-round ceiling; keep backend submitter and round validation as the final authorization boundary
- Execution or analysis result: a submitter viewing an invalid Lead in the unified Lead management page now receives the first-round appeal entry even though the page mode is `all`; later-round entries appear only after the prior appeal is upheld and before round three; owners and other non-submitters remain unable to render the submission button
- Changed files: `frontend/workbench/src/components/LeadAppealPanel.tsx`; `frontend/workbench/src/components/LeadDetail.tsx`; new `frontend/workbench/src/components/LeadAppealPanel.test.ts`; this handoff file. Existing overlapping edits in LeadDetail and the handoff log were preserved.
- Verification evidence: focused appeal eligibility tests PASS 3/3; Workbench full tests PASS (51 files, 295 tests); `npm run typecheck` PASS; `npm run build` PASS with the existing large-chunk advisory; scoped `git diff --check` PASS with repository LF/CRLF conversion notices only; browser smoke checks at 1440x900 and 390x844 showed no horizontal overflow or console warnings on the accessible login page
- Dependency or integration impact: no backend/API contract, dependency, permission, database/schema/data, migration, branch, commit, or shared-service state change; Workbench deployment or dev-server refresh is required for the corrected button visibility
- Remaining work: authenticated verification of first-, second-, and third-round button visibility on real Lead details remains unverified because the available browser session has no login state; historical Leads still require a valid `source_user_id` relationship to identify their submitter

## Delivery 2026-08-19 11:25:28 +08:00

- Branch: main
- Worktree: D:\ZSJ-OS
- HEAD commit: 7d4a9ae2f959ccfaee2a389bfac2a7b94cccae25
- User goal: route Lead business-notification cards to their corresponding detail tabs and notify the actual complaint submitter for both founded and unfounded decisions
- Key decisions: resolve the complainant only from the persisted complaint employee or partner identity; retain owner and direct-leader recipients only for founded decisions; add a distinct unfounded scene and two complainant-specific default rules; map appeal, complaint, follow-up, and other Lead scenes to stable detail tabs; keep the appeal-review inbox as the preferred action for submitted appeals; validate every requested tab against server-projected `visibleTabs`; use `lead.no` in user-visible notification content
- Execution or analysis result: founded and unfounded complaint decisions now publish result events to the actual complaint submitter, with founded decisions continuing to notify the sales owner and direct leader; realtime cards, the bell popup, and the full message center use one action resolver and navigate to the corresponding Lead detail tab; hidden tabs fall back to overview without widening authorization; V090 is prepared and wired but was not applied to any database
- Changed files: `LeadNotifySceneConstants`, `LeadComplaintService`, `LeadNotifySceneProvider`, and focused backend tests; Workbench `notifyMessageAction` service/tests, Lead detail-tab parsing/tests, `LeadManagementPage`, and `LeadDetail`; V090 migration, bootstrap, verification SQL, and migration README; directly affected notification, submitter-action, frontend-route, permission-flow, and migration documentation; this handoff file. Existing overlapping worktree edits were preserved.
- Verification evidence: focused ZSJOS Maven tests PASS 12/12; Workbench full tests PASS 51 files and 298 tests; `npm run typecheck` PASS; `npm run build` PASS with the existing large-chunk advisory; `zsjos-db check` PASS for manifests, migration order, desired schema, Java mappings, baseline versions, and verification; scoped `git diff --check` PASS with repository LF/CRLF conversion notices only; in-app browser checks at 1440x900 and 390x844 found no horizontal overflow or console warnings/errors on the available login page
- Dependency or integration impact: no new dependency, branch, commit, external database execution, historical-message rewrite, permission grant, or shared-service state change; backend and Workbench deployment plus separately approved V090 execution are required for complete runtime behavior
- Remaining work: authenticated end-to-end verification of realtime, bell-popup, and message-center clicks against real appeal and founded/unfounded complaint messages remains unverified because the available browser session has no login state; execute V090 only in a separately approved migration window and then run `verify-bootstrap.sql`

## Delivery 2026-08-19 11:38:00 +08:00

- Branch: main
- Worktree: D:\ZSJ-OS
- HEAD commit: 7d4a9ae2f959ccfaee2a389bfac2a7b94cccae25
- User goal: prevent notification deep links from falling back to the first Lead when the target is outside the first lazy-loaded page, while keeping newly actionable Leads at the top
- Key decisions: preserve the route-requested Lead ID through the initial page response even when it is absent from page one; hydrate the authorized detail and pin it into the left list; retry list refreshes for unseen action Leads, then hydrate by `managedLead` if pagination still misses them; prioritize current-session unseen IDs while retaining backend order for other rows; clear route pinning after manual selection
- Execution or analysis result: notification-linked details no longer switch to the first loaded Lead; missing deep-link targets become visible and active in the left list; newly actionable targets are inserted at the top after refresh fallback; manual selection is stable across later list refreshes
- Changed files: `frontend/workbench/src/services/leadManagement.ts`; `frontend/workbench/src/services/leadManagement.test.ts`; `frontend/workbench/src/pages/LeadManagementPage.tsx`; `frontend/workbench/src/pages/lead-management-unified.guard.test.ts`; this handoff file. Existing overlapping edits were preserved.
- Verification evidence: focused Workbench tests PASS 27/27; full Workbench tests PASS 51 files and 302 tests; `npm run typecheck` PASS; `npm run build` PASS with the existing large-chunk advisory; desktop 1440x900 and mobile 390x844 browser smoke checks PASS with no horizontal overflow or console errors on the unauthenticated login page; scoped `git diff --check` PASS with repository LF/CRLF conversion notices only
- Dependency or integration impact: no new dependency, backend/API contract, database/schema/data, permission, branch, commit, or external/shared-service change; Workbench deployment is required
- Remaining work: authenticated verification with a real notification-linked Lead outside page one and a real unseen action event remains unverified because the available browser session has no login state

## Delivery 2026-08-19 12:07:30 +08:00

- Branch: main
- Worktree: D:\ZSJ-OS
- HEAD commit: 7d4a9ae2f959ccfaee2a389bfac2a7b94cccae25
- User goal: add a permissioned Lead-detail flow-history timeline that merges all currently persisted submission, business-event, assignment-history, and aging-pool flow facts without inventing historical data
- Key decisions: expose `GET /zsjos/lead/{id}/flow-history` behind cumulative `zsjos:lead-detail:flow-read` feature permission and Service-boundary Lead object authorization; merge three existing history sources plus the persisted Lead submission time; deduplicate assignment rows explicitly referenced by business events; map real event/action/status codes to Chinese display values; distinguish automatic and specified dispatch through the persisted assignment-rule reference; resolve current employee/Partner names without claiming unavailable snapshots; preview only image/PDF evidence through short-lived Infra URLs; project `flow-history` only through server `visibleTabs`; grant enabled `sales_manager` roles only on the first V091 installation so later administrator removals remain authoritative
- Execution or analysis result: the backend returns a descending, stable unified flow record with separate Lead and assignment transitions, owners, source, operator, reason, and attachment availability; the Workbench renders the new Ant Design timeline tab with loading, empty, error/retry, responsive fields, unsupported/unavailable attachment states, image preview, and PDF preview without a download action; deep-link parsing accepts `tab=flow-history` and still falls back to overview when the server hides it; V091, bootstrap wiring, verification SQL, API/architecture/frontend/operations documentation, and migration guidance are prepared but not applied
- Changed files: Lead flow-history Controller/VO/Service, `LeadConstants`, three source mappers, Lead detail projection and object-permission action, focused backend tests; Workbench Lead detail, typed API, tab protocol/tests, flow-history panel/tests and responsive styles; `V091__lead_flow_history_permission.sql`, bootstrap/verification SQL and migration README; `docs/api/zsjos-lead-flow-history.md`, `docs/architecture/data-and-permission-flow.md`, `docs/frontend/zsjos-menu-coverage.md`, `docs/operations/database-migrations.md`; this handoff file. Existing overlapping worktree edits were preserved.
- Verification evidence: ZSJOS module full test suite PASS 412/412; focused flow-history/permission/projection tests PASS 57/57; Workbench full tests PASS 52 files and 304 tests; Workbench `npm run typecheck` PASS; Workbench `npm run build` PASS with the existing large-chunk advisory; `zsjos-db check` PASS; scoped `git diff --check` PASS; browser connection and page reload succeeded but exposed only the unauthenticated login page. The wider `-am` reactor was attempted and stopped before System/BPM/ZSJOS at the unrelated, repeatable existing Infra failure `CodegenEngineUniappTest.testExecute_treeSearch:153`; the target ZSJOS module was then run directly and passed completely.
- Dependency or integration impact: no new dependency, duplicate flow ledger, historical-row mutation, migration execution, external database change, branch, commit, or shared-service reconfiguration; backend and Workbench deployment plus separately approved V091 execution are required for runtime availability; current source tables do not contain uniform historical name snapshots, so deleted subjects may display as unknown
- Remaining work: run V091 only in a separately approved migration window, then execute the read-only `verify-bootstrap.sql`; verify the real flow tab at desktop/mobile widths with authenticated `sales_manager` and unauthorized users, including image/PDF/unsupported/unavailable attachments. These runtime checks remain unverified because the available browser session has no login state and V091 was intentionally not applied.

## Delivery 2026-08-19 12:57:16 +08:00

- Branch: main
- Worktree: D:\ZSJ-OS
- HEAD commit: 7d4a9ae2f959ccfaee2a389bfac2a7b94cccae25
- User goal: restyle the Lead flow-history tab according to the Workbench UI guidelines and the compact follow-up timeline pattern, with a high-density responsive layout
- Key decisions: replace the generic Ant Design Timeline presentation with a compact custom vertical track; keep time, node, business object, source, and operator in the scan-first header area; combine before/after values into three dense owner, Lead-status, and assignment-status transition fields; render reason and attachments only when present; use a 3/2/1-column container-query layout; isolate all new selectors under `lead-flow-history-*` so the existing overview flow timeline remains unaffected
- Execution or analysis result: flow-history records now use the same dot/connector/content rhythm as follow-up records, with compact sunken transition fields and conditional supporting details; desktop, medium, and narrow containers progressively reduce from three columns to two and one; loading, empty, retry, attachment preview, API, permission, and sorting behavior remain unchanged; a final review found and removed a CSS selector collision with the pre-existing overview timeline
- Changed files: `frontend/workbench/src/components/LeadFlowHistoryPanel.tsx`; `frontend/workbench/src/components/LeadFlowHistoryPanel.test.ts`; the flow-history section in `frontend/workbench/src/styles/components/lead-detail-v2.css`; this handoff file. Existing overlapping edits were preserved.
- Verification evidence: Workbench full test suite PASS 52 files and 305 tests, including style guards and focused compact-timeline coverage; `npm run typecheck` PASS; `npm run build` PASS with the existing large-chunk advisory; scoped `git diff --check` PASS with repository line-ending notices only; local Workbench endpoint `http://localhost:5174/` returned HTTP 200
- Dependency or integration impact: no new dependency, backend/API contract, permission, database/schema/data, migration execution, branch, commit, or external/shared-service change; Workbench deployment or dev-server refresh is required for the new presentation
- Remaining work: authenticated desktop and mobile visual verification of the actual Lead flow-history tab remains unavailable because the current browser session exposes only the login page; verify long operator/source/reason text and image/PDF/unsupported attachment examples once a signed-in `sales_manager` session is available

## Delivery 2026-08-19 13:04:38 +08:00

- Branch: main
- Worktree: D:\ZSJ-OS
- HEAD commit: 7d4a9ae2f959ccfaee2a389bfac2a7b94cccae25
- User goal: make each flow-history record strictly match the requested fields and show `-` for missing or unchanged values instead of “不变”
- Key decisions: split the owner transition into separate `原归属销售` and `新归属销售` fields; keep Lead and assignment state fields as explicit `客资状态变化` and `分配状态变化`; render missing sides of a transition as `-`; render empty owner values as `-`; retain the compact four-column desktop and two/one-column responsive layout
- Execution or analysis result: the flow-history card now exposes the requested owner and status fields without an implicit combined owner field, and no user-visible “不变” value remains
- Changed files: `frontend/workbench/src/components/LeadFlowHistoryPanel.tsx`; `frontend/workbench/src/components/LeadFlowHistoryPanel.test.ts`; the flow-history grid value selector in `frontend/workbench/src/styles/components/lead-detail-v2.css`; this handoff file
- Verification evidence: Workbench full test suite PASS 52 files and 305 tests; `npm run typecheck` PASS; targeted search confirmed no runtime “不变” label or legacy selector remains (the test intentionally asserts absence); no whitespace errors in the changed source files
- Dependency or integration impact: no new dependency, backend/API contract, permission, database/schema/data, migration execution, branch, commit, or external/shared-service change
- Remaining work: authenticated desktop/mobile visual verification remains unavailable because the current browser session exposes only the login page

## Delivery 2026-08-19 13:19:01 +08:00

- Branch: main
- Worktree: D:\ZSJ-OS
- HEAD commit: 7d4a9ae2f959ccfaee2a389bfac2a7b94cccae25
- User goal: show the missing Lead-status transition for the appeal overturn on `KZ202608121049550001`, separate reason and remark, and keep compact field labels left-aligned with values right-aligned on one row
- Key decisions: treat stable `lead_appeal_overturned` events as the actual Lead transition `invalid -> valid` even when historical event rows contain appeal-review statuses; suppress Lead transitions for appeal submission and upheld decisions; map legacy `converted` to the visible valid status; add a distinct response `remark` field; map invalid-reason labels to reason, qualification descriptions and persisted follow-up text to remark, without duplicating or guessing content; keep all existing history rows unchanged
- Execution or analysis result: the affected historical overturn now projects `无效 → 有效`; reason and remark render as separate always-present fields with `-` when empty; submission, owner, Lead-status, and assignment-status fields stay on one compact row with left labels and right values; attachments continue to show previews or `无附件`
- Changed files: `LeadFlowHistoryRespVO`; `LeadFlowHistoryService`; `LeadFlowHistoryServiceTest`; Workbench flow-history API type, panel, component/style tests, and compact styles; `docs/api/zsjos-lead-flow-history.md`; `docs/architecture/data-and-permission-flow.md`; this handoff file. Existing overlapping worktree edits were preserved.
- Verification evidence: read-only local MySQL inspection confirmed Lead 11 is currently `valid/owned` and event 124 stores `lead_appeal_overturned` as `sales_manager_reviewing -> overturned` at 2026-08-19 11:28:34; focused backend flow-history tests PASS 6/6; ZSJOS module full suite PASS 414/414; Workbench full suite PASS 52 files and 306 tests; `npm run typecheck` PASS; `npm run build` PASS with the existing large-chunk advisory; focused diff/whitespace check PASS with line-ending notices only
- Dependency or integration impact: no new external dependency, schema change, migration, historical-data mutation, database write, permission change, branch, commit, or shared-service restart; backend and Workbench deployment/restart are required before the corrected projection appears in the running UI
- Remaining work: authenticated desktop/mobile verification of the real flow-history tab remains unavailable because the current browser session exposes only the login page; the currently running backend was not restarted, so its live endpoint still uses the previous implementation

## Delivery 2026-08-19 13:55:26 +08:00

- Branch: main
- Worktree: D:\ZSJ-OS
- HEAD commit: 7d4a9ae2f959ccfaee2a389bfac2a7b94cccae25
- User goal: replace subordinate-sales pagination with the shared append lazy-loading behavior, give sales managers a permissioned one-click command that pauses all managed sales, and make inability to receive automatic assignments prominent on the home page
- Key decisions: retain the existing page API and stable name/user-ID ordering; load 20-row pages through a scroll-root sentinel with stale-response rejection and user-ID deduplication; derive bulk scope only from the live manager hierarchy and stable `sales_specialist` post, including disabled accounts; persist only the accepting preference with fixed reason `主管一键下班`; audit only actual `accepting -> paused` changes; restore Redis mode on transaction rollback; share one dispatch status/heartbeat provider between the header and Today Tasks; project home warnings by load-error, page-offline, then paused priority; define independent permission `zsjos:subordinate-sales:pause-all` and first-install-only `sales_manager` grant in V092
- Execution or analysis result: the subordinate left pane now lazy-loads and retains loaded rows on load-more failure; filter changes reset paging, selection, stale requests, and scroll; the one-click command returns total/changed/already-paused counts and does not accept frontend targets; existing Leads, accounts, and page presence are untouched; eligible sales now see a prominent synchronized home warning and recovery action; V092 and fresh-bootstrap/read-only verification wiring are prepared but not applied
- Changed files: subordinate-sales Controller/response VO/Service and sales-dispatch status service plus focused tests; Workbench subordinate page, typed API/helpers/tests, shared dispatch provider/control/alert, Today Tasks, shell wiring, responsive styles and guard tests; `V092__subordinate_sales_pause_all_permission.sql`, bootstrap seed/order/verification SQL and migration README; subordinate-sales/dispatch API, permission-flow, role-matrix, frontend coverage and migration documentation; this handoff file. Existing overlapping changes in these files were preserved.
- Verification evidence: focused backend tests PASS 13/13; ZSJOS module full suite PASS 420/420; focused Workbench tests PASS 10/10 after the final provider guard; Workbench full suite PASS 53 files and 310 tests before that final guard addition; Workbench typecheck PASS; production build PASS with the existing large-chunk advisory; `zsjos-db check` PASS; scoped `git diff --check` PASS with repository line-ending notices only
- Dependency or integration impact: no new dependency, cursor API, schema/table change, historical-data mutation, migration execution, real permission/account/preference update, branch, commit, or service restart; backend and Workbench deployment plus separately approved V092 execution are required for runtime availability
- Remaining work: authenticated desktop/mobile browser verification of the real subordinate list, confirmation dialog, home warning and header synchronization remains unverified because `http://localhost:5174/` currently shows `无法访问此站点` and the browser safety policy blocked reloading the local URL; the local service was intentionally not started or restarted without separate approval

## Delivery 2026-08-19 14:09:55 +08:00

- Branch: main
- Worktree: D:\ZSJ-OS
- HEAD commit: 7d4a9ae2f959ccfaee2a389bfac2a7b94cccae25
- User goal: make dispatch pause, page-offline, and status-error warnings global across Workbench routes, and show paused/offline header states in red
- Key decisions: mount the existing shared `SalesDispatchStatusAlert` in the route shell immediately above tabs/content; remove the page-local Today Tasks mount; keep one Provider/heartbeat source; use Ant Design error tags for both non-receiving header states while preserving success/processing colors for healthy states
- Execution or analysis result: switching between any Workbench page now retains the warning, and the Today Tasks page no longer owns a duplicate alert; global alert styles moved to dispatch component styles with error-token red emphasis
- Changed files: `frontend/workbench/src/main.tsx`; `frontend/workbench/src/pages/TodayTasksPage.tsx`; `frontend/workbench/src/components/SalesDispatchStatusControl.tsx`; `frontend/workbench/src/components/SalesDispatchStatusAlert.tsx`; dispatch and Today Tasks styles; Workbench guard test; dispatch API and permission-flow documentation; this handoff file. Existing overlapping changes were preserved.
- Verification evidence: focused Workbench tests PASS 6/6; Workbench full suite PASS 53 files and 311 tests; `npm run typecheck` PASS; production build PASS with the existing large-chunk advisory; scoped `git diff --check` PASS after removing the new trailing whitespace
- Dependency or integration impact: no backend/API, database, migration, permission, dependency, branch, commit, or shared-service change; Workbench deployment/refresh is required
- Remaining work: authenticated desktop/mobile browser verification remains unavailable because the local `http://localhost:5174/` tab currently reports `无法访问此站点` and browser policy blocked reloading it

## Workstream Scope Update 2026-08-19 14:22:48 +08:00

- Workstream ID: `main`
- Goal: Embed the authoritative Lead customer profile in the sales-order approval detail while preserving the existing order approval workflow and detail content.
- Non-goals: Changing approval decisions, BPM behavior, menu or feature permissions, Lead ownership, database schema/data, external services, dependencies, branches, commits, or unrelated Workbench layouts.
- Branch: `main`
- Worktree: `D:\ZSJ-OS`
- Base commit: `7d4a9ae2f959ccfaee2a389bfac2a7b94cccae25`
- Target branch: `main`
- Ownership scope: Sales-order response VO and conversion/tests in `backend/yudao-module-zsjos`; Workbench sales-order API type, detail component/page tests, and scoped sales-order styles; directly affected sales-order API documentation; this handoff file. Existing overlapping user changes remain preserved.
- Owner: Codex `/root`
- Dependencies: Existing ZSJOS Lead/order DAL, System user public API, Workbench Ant Design, dictionary API, and established Lead profile styles only. No new dependency.
- Integration order: Register scope -> extend the authorized order-detail response with current linked-Lead profile data -> render the profile card in order details -> retain navigation and repurchase fallbacks -> update focused contract/UI tests and API documentation -> run backend and Workbench verification -> perform desktop/mobile browser checks when authenticated state is available -> append delivery evidence.
- Verification plan: Focused sales-order backend tests; Workbench focused tests, full tests, typecheck, and production build; scoped `git diff --check`; authenticated desktop/mobile browser checks for success, no-Lead, loading/error, copy controls, and responsive layout when a signed-in session is available.

## Delivery 2026-08-19 14:31:57 +08:00

- Workstream ID: `main`
- Branch: `main`
- Worktree: `D:\ZSJ-OS`
- HEAD commit: `7d4a9ae2f959ccfaee2a389bfac2a7b94cccae25`
- User goal: Show the corresponding Lead customer profile when an approver selects a sales order, using the Lead-management customer-card layout.
- Key decisions: Project a nested `leadProfile` only through the already-authorized order-detail response; resolve current Lead, employee, Partner, category, channel, dispatch, ownership, and region facts from their authoritative backend or dictionary sources; never substitute internal `leadId` for `leadNo`; omit the card for unlinked repurchases; retain the existing full Lead navigation and all order approval/detail content; collapse the profile grids to one column on mobile.
- Execution or analysis result: Linked first-purchase order details now render the Lead customer card above the existing order cards with name, business Lead number, copyable mobile/WeChat values, source and dispatch tag, submitter, owner, category, channel, and region. The API returns no invented profile for an unlinked repurchase, and existing order loading, error/retry, unauthorized, approval-action, and dictionary-error behavior remains in place.
- Changed files: `backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/order/vo/SalesOrderRespVO.java`; `backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/order/SalesOrderServiceImpl.java`; `backend/yudao-module-zsjos/src/test/java/cn/iocoder/yudao/module/zsjos/service/order/SalesOrderServiceImplTest.java`; `frontend/workbench/src/services/api.ts`; `frontend/workbench/src/components/SalesOrderDetailCards.tsx`; new `frontend/workbench/src/components/SalesOrderDetailCards.test.tsx`; `frontend/workbench/src/pages/sales-order-approval-unified.guard.test.ts`; `frontend/workbench/src/styles/pages/sales-order.css`; `docs/api/zsjos-sales-order.md`; this handoff file. Existing overlapping edits were preserved.
- Verification evidence: Focused backend `SalesOrderServiceImplTest` PASS 29/29; full ZSJOS module PASS 422/422; focused Workbench profile/approval tests PASS 7/7; final Workbench full suite PASS 54 files and 314/314 tests; `npm run typecheck` PASS; `npm run build` PASS with the existing large-chunk advisory; scoped whitespace/diff check PASS with repository line-ending notices only. The local URL loaded at 1440x900 and 390x844 with no console warnings/errors and no mobile horizontal overflow, but the available browser session showed only the login page.
- Dependency or integration impact: No new dependency, schema/data/migration, BPM or approval-state behavior, permission/menu grant, branch, commit, external-state mutation, or service start/restart. Backend and Workbench deployment/restart are required for the running environment to expose the new response and card.
- Remaining work: Authenticated desktop/mobile visual verification of a linked first-purchase order and an unlinked repurchase remains unverified because no signed-in browser session was available. A real authenticated HTTP contract check also remains pending until the running backend is intentionally rebuilt/restarted with these source changes.

## Delivery 2026-08-19 16:10:34 +08:00

- Workstream ID: `main`
- Branch: `main`
- Worktree: `D:\ZSJ-OS`
- HEAD commit: `7d4a9ae2f959ccfaee2a389bfac2a7b94cccae25`
- User goal: Prevent sales-order approval details from losing dictionary-derived labels such as Lead category, source, and course information after administrators update or remove dictionary options, and clarify the complete business snapshot boundary.
- Key decisions: reuse the existing per-approval-round `order_snapshot` without a schema migration; snapshot five order dictionary labels plus the linked Lead profile and its category/channel labels whenever a round is created or resubmitted; keep the established order-item product/course snapshot; prefer the current round snapshot in details; conservatively fall back to the current projection for historical or malformed snapshots without inventing historical values; retain BPM as the authority for workflow tasks, approvers, decisions, and node times; never expose internal `leadId` as the customer-facing Lead number.
- Execution or analysis result: new approval rounds retain the recorded display labels for student nature, service period, student source, fee mode, payment method, Lead category, and Lead channel together with the linked Lead profile; dictionary edits, disablement, or later Lead changes no longer turn those saved approval details into `标签未配置`; the Workbench renders snapshot labels first and retains dictionary lookup only as the historical compatibility path; the API documentation now records existing, newly added, fallback, and explicitly non-snapshot business states.
- Changed files: `backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/order/vo/SalesOrderRespVO.java`; `backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/order/SalesOrderServiceImpl.java`; `backend/yudao-module-zsjos/src/test/java/cn/iocoder/yudao/module/zsjos/service/order/SalesOrderServiceImplTest.java`; `frontend/workbench/src/services/api.ts`; `frontend/workbench/src/components/SalesOrderDetailCards.tsx`; `docs/api/zsjos-sales-order.md`; this handoff file. Existing overlapping user changes were preserved.
- Verification evidence: focused `SalesOrderServiceImplTest` PASS 30/30 including snapshot precedence; full ZSJOS module PASS 424/424; Workbench full suite PASS 54 files and 315/315 tests; `npm run typecheck` PASS; `npm run build` PASS with the existing large-chunk advisory; scoped `git diff --check` PASS with repository line-ending notices only. Earlier desktop/mobile browser checks loaded the local route without console errors or horizontal overflow, but the available session displayed only the login page.
- Dependency or integration impact: no new dependency, schema/data migration, historical-row rewrite, BPM state-machine change, permission/menu change, branch, commit, external-state mutation, or service start/restart. Backend and Workbench deployment/restart are required before the running environment creates and displays the new snapshot fields.
- Remaining work: authenticated desktop/mobile verification and a real authenticated HTTP contract check remain unverified because the browser session has no login state and the running backend was not rebuilt/restarted. Existing historical rounds cannot recover labels already absent from both their snapshot and the current dictionaries without an explicitly reviewed data-repair source.

## Workstream Scope Update 2026-08-19 16:30:00 +08:00

- Workstream ID: `main`
- Goal: Implement supervisor-sign notifications and task deep links, independent three-party sales-order approval status, precise Lead-profile return navigation, active refresh controls on the four confirmed business page groups, and immutable successor-order resubmission.
- Non-goals: Changing BPM countersign ownership, menu/permission grants, administrator-customized notification rules, historical business rows, unrelated Workbench pages, dependencies, branches, commits, or shared service runtime state.
- Branch: `main`
- Worktree: `D:\ZSJ-OS`
- Base commit: `7d4a9ae2f959ccfaee2a389bfac2a7b94cccae25`
- Target branch: `main`
- Ownership scope: ZSJOS sales-order Controller/Service/VO/DAL notification initialization and focused tests; Workbench sales-order/message/task/Lead/registration page routing, API types, refresh controls and focused tests/styles; V093 notification defaults, bootstrap/verification wiring, read-only duplicate-order audit SQL; directly affected sales-order, notification, permission-flow and state-machine documentation; this handoff file. Existing overlapping user edits remain preserved.
- Owner: Codex `/root`
- Dependencies: Existing Yudao BPM public APIs, System notification scene/rule APIs, ZSJOS order/registration/cashback services, React Router, Ant Design and current database successor-order columns/indexes only. No new dependency.
- Integration order: backend response and target contracts -> atomic successor-order resubmission -> notification defaults/payloads -> Workbench deep links/status/refresh -> SQL audit and documentation -> focused then full verification -> authenticated browser checks when available.
- Verification plan: focused order/supervisor notification and lifecycle tests; ZSJOS full module tests; Workbench focused and full tests, typecheck and production build; migration/bootstrap/read-only audit static checks; scoped `git diff --check`; authenticated desktop/mobile workflow checks if a signed-in session is available.

## Workstream Scope Update 2026-08-19 17:35:04 +08:00

- Workstream ID: `main`
- Goal: Add durable repository-wide rules for configurable menu/button permissions and administrator-managed dictionary dropdown snapshots.
- Non-goals: Changing application behavior, permission assignments, dictionary data, schemas, APIs, dependencies, branches, commits, services, or external state.
- Branch: `main`
- Worktree: `D:\ZSJ-OS`
- Base commit: `7d4a9ae2f959ccfaee2a389bfac2a7b94cccae25`
- Target branch: `main`
- Ownership scope: Root `AGENTS.md` and this appended delivery record only; all existing overlapping worktree changes remain preserved.
- Owner: Codex `/root`
- Dependencies: Existing Yudao menu/button permission, backend authorization, dictionary, and business API facilities only. No new dependency.
- Integration order: Register scope -> add permission configuration boundary -> add dictionary/dropdown snapshot boundary and authoritative-source exceptions -> review the scoped diff -> append delivery evidence.
- Verification plan: Review consistency with existing source-of-truth, Yudao alignment, permission, dictionary, and handoff rules; run scoped `git diff --check`; inspect the final scoped diff.

## Delivery 2026-08-19 17:36:09 +08:00

- Workstream ID: `main`
- Branch: `main`
- Worktree: `D:\ZSJ-OS`
- HEAD commit: `7d4a9ae2f959ccfaee2a389bfac2a7b94cccae25` (uncommitted worktree changes; no commit created)
- User goal: Define durable repository rules requiring configurable page/action permissions and administrator-managed dictionary dropdowns with immutable selection snapshots, with explicit confirmation before any hardcoded exception.
- Key decisions: Treat backend permission identifiers as stable links to menu/button configuration while prohibiting hardcoded roles, users, permission lists, and authorization outcomes; preserve backend enforcement and domain invariants; apply dictionaries to stable user-editable business enumerations; keep entity selectors and technical workflow/state actions on their authoritative APIs or contracts; snapshot dictionary code and selection-time label only when the selection becomes persisted business data; never invent historical labels.
- Execution or analysis result: Added repository-wide permission and dictionary snapshot contracts to the root instructions, including frontend/backend authorization responsibilities, hardcoding escalation requirements, entity/API boundaries, historical-display guarantees, transient-filter behavior, and legacy-data handling.
- Changed files: `AGENTS.md`; `handoff/main.md`.
- Verification evidence: Reviewed the new rules against the existing server-source-of-truth, Yudao alignment, architecture-routing, documentation, and handoff requirements; inspected the scoped diff; `git diff --check -- AGENTS.md handoff/main.md` completed with line-ending notices only and no whitespace errors.
- Dependency/integration impact: No application behavior, dependency, schema/data, API, menu/permission assignment, dictionary entry, branch, commit, service, or external state changed. The rules apply repository-wide to future design and implementation work.
- Remaining work: None.

## Delivery 2026-08-19 17:52:10 +08:00

- Workstream ID: `main`
- Branch: `main`
- Worktree: `D:\ZSJ-OS`
- HEAD commit: `7d4a9ae2f959ccfaee2a389bfac2a7b94cccae25` (uncommitted worktree changes; no commit created)
- User goal: Implement the confirmed sales-order approval, supervisor notification, task deep-link, independent status, return navigation, active refresh, and successor-order resubmission plan.
- Key decisions: Supervisor request notifications target only the designated supervisor and decisions target only the requester; task-target resolution remains permission and BPM-task scoped; supervisor approval is an independent third projection; resubmission creates a new order and marks the old order `superseded`; V093 is repeatable and does not overwrite tenant configuration; historical duplicate cleanup remains audit-only.
- Execution or analysis result: Implemented backend APIs, notification payloads/default initialization, supervisor projection, successor-order transaction, frontend deep links/returnTo whitelist/refresh actions/status display, read-only successor audit SQL, and synchronized API, architecture, notification, and state-machine documentation. Updated brittle frontend guard tests to assert the confirmed refresh and unified approval behavior.
- Changed files: Relevant files under `backend/yudao-module-zsjos`, `frontend/workbench`, `script/sql/mysql/migrations/V093__sales_order_supervisor_notifications.sql`, `script/sql/mysql/verify-sales-order-successors.sql`, `script/sql/mysql/bootstrap.sql`, `docs/api/zsjos-sales-order.md`, `docs/api/system-business-notifications.md`, `docs/architecture/data-and-permission-flow.md`, `docs/business/lead-order-state-machine.md`, and this handoff file. Existing unrelated worktree changes were preserved.
- Verification evidence: `mvn -f backend/pom.xml -pl yudao-module-zsjos test` passed (424 tests); Workbench `npm test` passed (54 files, 315 tests); `npm run typecheck` passed; `npm run build` passed; scoped `git diff --check` passed after removing a trailing-space finding. SQL was reviewed statically only.
- Dependency/integration impact: No new dependencies, branch, commit, service restart, migration execution, or real-data mutation. V093 must be applied through the normal migration process; successor audit SQL is read-only.
- Remaining work: Authenticated desktop/mobile browser E2E and execution of the SQL migration/audit require an available approved runtime/database and were not run in this turn.

## Delivery 2026-08-19 18:50:53 +08:00

- Workstream ID: `main`
- Branch: `main`
- Worktree: `D:\ZSJ-OS`
- HEAD commit: `7d4a9ae2f959ccfaee2a389bfac2a7b94cccae25` (uncommitted worktree changes; no commit created)
- User goal: Fix the concrete defects found by the scoped review of the sales-order approval implementation, without reviewing or changing unrelated prior work.
- Key decisions: Resolve supervisor notification actions from the persisted source event key and confirmation record rather than storing arbitrary URLs; retain a latest-confirmation fallback only for legacy messages without an event key; enforce recipient and order-object checks on task targets; recheck resubmission idempotency after locking the old order; separate request and decision reasons; protect approval, supervisor, registration, and student list/detail refreshes with generation and in-flight guards; preserve existing content during refresh.
- Execution or analysis result: Added the controlled notification-target API and exact message deep links, exposed the existing System notification source event key, added order object checks, removed blanket BPM error remapping, closed the resubmission idempotency race, corrected notification variables, prevented stale frontend responses from replacing newer filters/selections, merged duplicate refresh/page requests, pinned directly addressed supervisor confirmations, refreshed student dictionaries, validated URL work types, and changed V093 to avoid adding a default when an administrator rule already exists for the scene.
- Changed files: `backend/yudao-module-system/src/main/java/cn/iocoder/yudao/module/system/controller/admin/notify/vo/message/NotifyMessageRespVO.java`; relevant sales-order controller/mapper/service/test files under `backend/yudao-module-zsjos`; `frontend/workbench/src/services/api.ts`; `frontend/workbench/src/services/notifyMessageAction.ts` and test; `frontend/workbench/src/components/SalesOrderSupervisorInbox.tsx`; `frontend/workbench/src/pages/SalesOrderApprovalPage.tsx`; `frontend/workbench/src/pages/RegistrationPages.tsx`; `script/sql/mysql/migrations/V093__sales_order_supervisor_notifications.sql`; `docs/api/zsjos-sales-order.md`; `docs/api/system-business-notifications.md`; this handoff file.
- Verification evidence: Focused backend tests passed (39); ZSJOS full module tests passed (426); Workbench full tests passed (54 files, 316 tests); `npm run typecheck` passed; `npm run build` passed; System module compile passed; scoped `git diff --check` passed with line-ending notices only; the successor audit script contains no write or DDL statements.
- Dependency/integration impact: No new dependency, branch, commit, service restart, migration execution, audit execution, or real-data mutation. The System response adds the already-persisted `sourceEventKey` field for controlled action resolution. V093 still requires normal migration execution approval.
- Remaining work: Authenticated desktop/mobile browser E2E and execution of V093/read-only audit against an approved database remain unverified because no service or database mutation was authorized in this turn.

## Delivery 2026-08-20 00:28:18 +08:00

- Workstream ID: `main-partner-e2e-verification`
- Branch: `main`
- Worktree: `D:\ZSJ-OS`
- HEAD commit: `7d4a9ae2f959ccfaee2a389bfac2a7b94cccae25` (uncommitted worktree changes; no commit created)
- User goal: Exercise the real local Partner H5 business chain with multiple records and employee roles, fix confirmed defects while testing, and cover divergent lifecycle states.
- Key decisions: Keep the Partner H5 on `/part-api`; use React Workbench as the employee surface; treat independent `zsjos_partner_account.id` as the Partner login identity; own new Partner cashback by `partnerId` with a null System-user beneficiary; retain the pre-V072 bound-System-user cashback path only for historical Leads; do not bypass cashback, mutate permissions, or directly force business states.
- Execution or analysis result: Authenticated the Partner and sales roles; created six isolated Lead cases for valid/deal, invalid/appeal, supplement/urge/complaint, order rejection/resubmission, registration correction/rejection, and new-media collaboration branches; sales claimed five cases and advanced the main case through its first follow-up. Fixed the H5 unknown-course payload, added the Vue Admin compatibility claim action, and fixed the independent-Partner cashback identity mismatch that blocked valid qualification. Runtime re-verification is paused at the required explicit service-restart boundary.
- Changed files: `frontend/h5/src/api/lead.ts`; `frontend/h5/src/pages/lead/submit.vue`; `frontend/h5/src/pages/lead/supplement.vue`; `frontend/admin/src/api/zsjos/leadClaimPool/index.ts`; `frontend/admin/src/views/zsjos/leadClaimPool/index.vue`; `backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/cashback/CashbackServiceImpl.java`; `backend/yudao-module-zsjos/src/test/java/cn/iocoder/yudao/module/zsjos/service/cashback/CashbackServiceImplTest.java`; `docs/api/cashback.md`; this handoff file. Existing unrelated worktree changes were preserved.
- Verification evidence: Real Partner submission succeeded for six records after the unknown-course fix; real sales claim succeeded for five records; first follow-up succeeded and moved the main case to follow-up; the server-permission-controlled compatibility claim action was verified in the browser. Cashback and qualification focused tests passed 16/16. The ZSJOS suite ran 430 tests with 429 passing and one unrelated existing registration-test injection error. The dependency-chain suite stopped at one unrelated Infra code-generator assertion after 204 upstream tests passed. All 27 backend dependency modules compiled; final executable repackaging was blocked only because running PID 42740 holds the target JAR. Scoped `git diff --check` passed with line-ending notices only.
- Dependency/integration impact: No new dependency, schema migration, destructive data action, permission/role/account mutation, branch, commit, or push. Six local test Leads and their authorized follow-up/claim state are retained for continued E2E coverage. PID 42740 still serves port 48080; the on-disk target JAR is currently the thin pre-repackage artifact and must be rebuilt after the approved stop before restart.
- Remaining work: Obtain separate confirmation to stop PID 42740, rebuild the executable JAR, restart port 48080, retry valid qualification, and continue the invalid/appeal, supplement/urge/complaint, order approval/rejection/resubmission, finance, registration/planner, and new-media branches across all supplied roles. Run frontend typecheck/build and desktop/mobile browser checks, then append the final delivery evidence.

## Delivery 2026-08-20 09:41:06 +08:00

- Workstream ID: `main`
- Branch: `main`
- Worktree: `D:\ZSJ-OS`
- HEAD commit: `7d4a9ae2f959ccfaee2a389bfac2a7b94cccae25` (uncommitted worktree changes; no commit created)
- User goal: add the versioned BPMN asset for student contact deadline extension approval.
- Key decisions: added a `1.0.0` BPMN asset with process key `zsjos_student_contact_extension`, task key `deliverySupervisorReview`, start-user-selected candidate strategy `35`, required approval reason, one-person completion, and the existing `coll_userList`/`assignee` variable pattern. Registered `extensionId`, `serviceRelationId`, and `requestedDueAt` as runtime business variables and used business key format `student-contact-extension:{extensionId}`.
- Execution or analysis result: Created the BPMN XML, registered its SHA-256 in `script/bpm/manifest.json`, and updated the versioned BPM asset documentation from four to five assets.
- Changed files: `script/bpm/zsjos_student_contact_extension/1.0.0/process.bpmn20.xml`, `script/bpm/manifest.json`, `docs/operations/zsjos-bpm-versioned-assets.md`, and this handoff file.
- Verification evidence: `python script/bpm/validate_manifest.py` passed with `Validated 5 versioned BPM assets`; Python XML parsing passed; scoped `git diff --check` passed with only line-ending conversion notices.
- Dependency/integration impact: No dependency, database migration, BPM publication, service restart, branch, commit, or external state change. The asset still requires controlled Admin model creation/import and publication in each target environment.
- Remaining work: Publish the model through Admin after confirming V094 and reviewer permissions; record Flowable definition ID/version, deployment time, and operator in the release record; execute a controlled extension request to verify the supervisor task.
