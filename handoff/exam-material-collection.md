# 考试资料收集开发记录

## Registration — 2026-11-04 (Asia/Shanghai)
- Environment: local; ZSJOS_AGENT_ENV unset, environment marker absent.
- Owner: Codex /root, chat 01a1a550-9419-7f10-8abd-ed383311dd13.
- Branch: codex/exam-material-collection; worktree: D:/ZSJ-OS-exam-material-collection; base: f6712f0beb.
- Goal: implement the user-approved exam collection design and UI acceptance criteria.
- Non-goals: main runtime/database changes, deployments, real role grants, unrelated refactoring, new dependencies, automatic merge/push.
- Ownership: exam-collection backend Controller/VO/Service/DAL/tests; directly required studentinfo archive/summary integration; task/notification scene integration; Workbench exam pages/services/routes/home reminder/archive/summary integration and scoped styles/tests; public collection frontend; Admin unsupported-menu filtering; scoped migration/verification scripts; directly affected architecture/API/UI documentation; this record.
- Dependencies: existing System permissions/dictionaries/notification APIs, Infra file storage, ZSJOS generic business-task command/scene facilities, existing archive revisions and collection configuration. No new dependency approved.
- Verification: focused Java behavior/auth/concurrency tests; isolated MySQL migration first/repeat/failure/recovery checks; frontend types/build/tests; browser desktop/mobile UI-01 through UI-12; both frontend menu consumers; main resource isolation.
- Target branch/integration order: None (feature delivery first; merge not authorized).
- Authorization: user explicitly requested baseline commit before new branch, independent worktree plan accepted, and goal execution on new branch.

## Progress — 2026-11-04 (Asia/Shanghai)
- Committed completed existing main changes as f6712f0beb (16 files). Existing handoffs record 37 collection, 135 order, 24 attendance tests passed; reused unchanged evidence and checked staged diff. Temporary files/build caches excluded and preserved.
- Created independent branch/worktree from that commit. Main remains checked out in D:/ZSJ-OS; no database or service operations performed.
- Current implementation concern: archive revisions are form-bound, while exam collection may target services without an existing form. Design must provide service-scoped archive integration without rewriting original order submission snapshots or bypassing existing permissions.
- Remaining: maintained design/UI acceptance document, concrete archive integration contract, implementation and full scoped verification. Goal remains active.

## Delivery — 2026-11-04 (Asia/Shanghai), matching policy foundation
- Context: same registration; HEAD 12fa7d6f8d before this change. Prior goal turn made concrete progress (baseline/worktree/design commits).
- Implemented ExamCollectionPolicy and seven JUnit regression cases: frozen roster matching, suffix normalization, same-service deduplication versus ambiguous services, confirmed out-of-roster binding, invalid binding, ordered application, distinct service counts, selected-field-only immutable merge and malformed field rejection.
- Added script/exam-collection/verify_policy.py using existing local JUnit jars and this worktree's isolated artifacts directory; no dependency added or main build outputs touched.
- Verified current sources: 7/7 tests passed; git diff --check passed. Design/API document records invocation and exact limits.
- Source findings: Person DO has no direct identity-card field; current identity evidence is in collection/archive values. Existing StudentArchiveService requires a submitted form, so historical services without one need a service-owned archive projection/revision adapter rather than synthetic order/form records.
- Not yet wired: controllers, DAL/schema, archive integration, public form, workbench pages, reminders and browser verification. No database or service state changed. Goal remains active; ownership retained.

## Delivery — 2026-11-04 16:38 (Asia/Shanghai), persistence and field snapshots
- Same branch/worktree/owner, HEAD 24e01caa04 before changes. Prior goal turn classified progress: matching policy implementation and tests committed.
- Added seven tenant/audit-aware DOs and Mapper interfaces for task/member/submission/binding/attachment/event/service archive revisions. Secret payloads use framework EncryptTypeHandler and are excluded from toString.
- Added V362 with prerequisite checks, table/index postconditions, eight menu/button metadata entries, transactional menu/ledger publication and error propagation. DDL rollback limitations explicitly documented. No role assignments or business seeds.
- Added ExamCollectionFields: fixed four required fields; selected published/enabled material fields only; certificate address allowed; gift/textbook/unknown/disabled/duplicate options rejected; original config copied rather than mutated. Added three tests.
- Current-source isolated Java compilation passed; all 10 exam tests passed. Existing dependencies were snapshotted into this worktree artifacts; main output untouched. No dependency changes.
- MySQL 8.4.11 fixture prefix exam_collection_verify_20261104163612463391: initial/replay, missing prerequisite, failed ledger write with retained DDL, recovery, table missing despite ledger, menu conflict, missing unique index rejection/recovery, per-task binding uniqueness and Chinese HEX passed. Fixture schemas retained. Application configuration database only read; its menus, role grants and both ledgers unchanged. No main service restart.
- Changed: examcollection DAL model/mapper directories, ExamCollectionFields and test, V362 migration, two verification scripts, API design and this log. Diff checks passed.
- Remaining: controllers/transaction services, permission provider, snapshot identity extraction, public upload/token flow, service archive adapter, task/notification integration, Workbench/H5 pages and all integrated/browser verification. V362 not applied to main database; no runtime feature acceptance claimed. Ownership retained; goal active.

## Delivery — 2026-11-04 (Asia/Shanghai), initial APIs and service archive adapter
- Context unchanged; HEAD d8bfb84eb1 before this change. Previous turn made progress through schema execution and code commits.
- Added task fields/create/page/detail endpoints and typed VOs, backend page/action/object authorization, no-store and disabled payload logging. Added random public tokens and keyed normalized identity digest; invalid public URL/encryption configuration fails before insertion.
- Added internal StudentServiceArchiveStore with empty historical-service support, same-Person/order-item legacy fallback, archive-only historical file retention, service locking, idempotent append, uncollected-field preservation and attachment origin checks. Original forms/answers untouched. Not yet connected to legacy detail/edit or public submit routes.
- Added permission/token, task creation and archive adapter tests; verification script now compiles current studentinfo DAL as well as feature sources to avoid stale dependency bytecode. Initial compile exposed older prepared StudentInfoImageMapper/DO missing archiveOnly; corrected verification inputs, not production behavior.
- Updated API/permission/ownership documentation with implemented endpoints and explicit pending integration boundaries. No migration or application database changes this turn.
- Verification: current-source focused Java suite and scoped diff checks (final result recorded in following entry); no Spring HTTP startup or browser acceptance. Main resources unchanged.
- Remaining: public submit/upload, roster notification/snapshotting, matching/exception transactions, task lifecycle/reminders, shared archive view/edit adapters, task summary/export, all UI and integration verification. Goal active; ownership retained.
- Final verification for this delivery: 19/19 Java tests passed after compiling current feature and studentinfo DAL sources; git diff --check passed. No database/HTTP/browser claim implied.

## Delivery — 2026-11-04 (Asia/Shanghai), public submission and matching
- Same registration, environment, branch and worktree; previous turn classified progress (task APIs/archive adapter committed).
- Implemented public task detail, isolated seven-day sessions, idempotent submission, tenant resolution/restoration, selected-field validation, task-locked sequence assignment, fixed-roster matching, saved exceptions, existing/manual binding reuse and ordered archive updates.
- Added session-bound upload/read/remove with existing attachment content validation and AES-GCM service wrappers; new encrypted Infra objects are compensated on binding transaction failure. Added cohort-aware session creation rate limit without changing existing personal upload quotas.
- Changes: ExamCollectionPublicService/MatchingService/AttachmentService, public Controller/VO, task/submission Mapper lookup helpers; StudentInfoImageService encryption wrappers; StudentInfoUploadRateLimit cohort entry; tests and verification script; API doc and this record.
- Verification: current-source isolated compile and 34/34 JUnit tests passed, including existing StudentInfoImagesAndRulesTest encryption/tamper/attachment regression; scope includes retry, closed task rejection, cross-task/session attachment denial, tenant restoration, unmatched/ambiguous outcomes, confirmed binding reuse and delayed-sequence protection. Scoped diff checks required before commit. No main database/services touched.
- Limits: not a live HTTP or database-concurrency test; real Infra storage/browser acceptance pending. Fixed roster notification entry, manual exception operations, lifecycle commands/task synchronization, archive detail/edit integration and frontend still incomplete. Goal active; ownership retained.

## Delivery — 2026-11-04 17:09 (Asia/Shanghai), frozen roster and planner task lifecycle
- Same registration/owner/environment/worktree; HEAD 0403fafa80 before changes. Prior turn made progress by integrating public submission and matching.
- Added serving-class options, initial roster notification, complete/reopen commands with optimistic task revision and object permission checks. Planner ownership freezes class homeroom user; identity digest comes from the current service archive. Empty selected classes persist in task classIdsJson.
- Added BusinessTaskCommandService scene integration for per-planner progress, all-collected completion, closure cancellation and missing-only reopen generations. Matching invokes synchronization within the same business transaction. No business-task DAL writes from exam services.
- Added own-planner pending/reminder/ack APIs; acknowledgement persists exam audit state without completing collection. Candidate tasks must belong to current user/tenant and have missing roster members; all requested acknowledgement IDs checked before writes.
- Extended development-only V362 original script with repeatable conditional class_ids_json addition; no deployed migration rewritten. Retained isolated MySQL fixtures under exam_collection_verify_20261104170640053333: initial/repeat/missing-prerequisite/ledger-failure/recovery/missing-table/missing-column/menu-conflict/index-conflict/uniqueness/Chinese HEX all passed. Application menus, role grants and ledgers unchanged.
- Verification: current-source isolated Java compile and 39/39 tests passed, including new frozen planner/identity, reason-required completion, version rejection, per-generation task transitions, owned/idempotent popup acknowledgement and existing material encryption regression. git diff --check passed. No main service or database mutation.
- Files: exam Controller/VO, task DO, task/roster/todo/reminder/scene services and matching hook, four scoped tests, V362 and its verifier, API/permission docs, this record.
- Remaining: explicit roster adjustment, manual exception resolution and query/history APIs, service archive display/edit integration, summary/export, Workbench/H5 pages and browser/HTTP/real-storage/concurrency acceptance. Goal active; ownership retained.

## Delivery — 2026-11-04 (Asia/Shanghai), exception resolution and history APIs
- Same owner/environment/worktree; HEAD ff5656ea90 before changes. Previous turn classified progress (roster/task lifecycle and tests committed).
- Added typed formal-submission/history/detail/attachment, candidate search, current archive, audit-event and frozen-member query APIs. All require exam query/object authorization, preserve snapshot labels, and reject cross-task/tenant or unselected attachment reads; drafts excluded. Batched candidate/member reference reads preserve unassigned/null cases.
- Added manual exception resolution with target archive version check, outside-roster service selection and newest-first identity-group processing; marks manual binding and audits reason/operator. Invalidating only unmatched exceptions retains all material. Changed matching audit to preserve manual operator/reason and increment submission revision.
- Added explicit roster delta command: soft removal, restore frozen membership, add valid serving-class service, preserve prior collected state, audit and regenerate planner tasks. No automatic roster mutation on transfer.
- Initial focused compile found fluent wrapper return type after isNotNull did not expose eqIfPresent; reordered wrapper operations. Added tests for manual grouping, stale revision, invalidation/no-delete, tenant/task rejection, historic labels, attachment selection and frozen roster restore; current-source compile/tests rerun after fixes.
- Outstanding behavior: safely correcting a previously archived wrong binding remains an explicit unresolved implementation item; current resolve refuses silent reassignment. Shared archive view/edit, task summary/export and all frontend/integration acceptance also remain. No database/service changes this turn. Goal active; ownership retained.
- Final verification: 47/47 focused Java tests passed with current feature sources; scoped diff check passed. Runtime HTTP, mapper SQL execution for new query shapes and browser validation remain unverified.
