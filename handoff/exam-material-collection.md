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
