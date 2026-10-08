# Feedback approval progress and manual urging

## Registration — 2026-10-08 (Asia/Shanghai)
- Workstream: local-feedback-approval-20261008; owner: current Codex chat.
- Environment: local (ZSJOS_AGENT_ENV unset; /etc/zsjos/agent-environment absent).
- Branch: main; worktree: D:\ZSJ-OS; base HEAD: d2f836006ff07b5517bcd6d485d72df788bf3cb0.
- Goal: implement the approved feedback approval summary, per-round timeline/snapshots, and submitter-only 30-minute manual urge with durable notifications in Workbench and Admin.
- Non-goals: automatic reminders, changing approval decisions, new dependencies, role assignments, shared-service changes, branches/commits/push.
- Ownership: BPM public approval progress API/DTO/implementation and focused tests; ZSJOS feedback controller/VO/service/DAL/tests; additive feedback error constants; Workbench feedback API/page/new approval component/styles/tests, approval-center task deep link and notification resolver/tests; Admin feedback API/list/new approval component; new forward SQL migration and verification script, migration entry chain if required; feedback API/release/permission documentation; this handoff only.
- Dependencies: existing BPM workflow engine, System notification durable publication, Infra attachment signing; prior unrelated uncommitted changes preserved. Shared files will be reread before patching; no ownership of unrelated edits.
- Verification: focused Java and frontend tests, module compile and both frontend type checks/builds as applicable, desktop/mobile browser flows, isolated MySQL first/repeat/failure/recovery and UTF-8 HEX checks; report environment blocks explicitly.
- Integration target/order: None. Shared database synchronization and real permission assignments require separately identified target authorization.

## Registration update — 2026-10-08
- Additional necessary scope: BPM task get-todo controller and actual task-participant read API; FeedbackContentProvider round authorization; isolated React/Vue browser fixtures and Vite test configuration; actual service transaction tests using isolated JDBC fixtures. Existing frontend build cache may be updated by checks; no changes to dependencies or lockfiles.
- Actual evidence: local Docker MySQL 8 is available. Shared development database is read-only until explicitly approved; isolated test schemas remain retained and are not deleted. Verification servers use dedicated loopback ports 5294/5295 and synthetic APIs.

## Registration update — Admin notification compatibility
- Additional scope: Admin BPM task API, feedback urge notification target resolver, Message popup and MyNotifyMessageDetail task entry; existing browser fixture checks. Reuse the established BPM process detail route with an exact task identifier and a live ownership check.
- User decision: code and migration delivery only; do not synchronize the shared development database or assign permissions.

## Delivery — 2026-10-08
- Implemented both frontend approval summaries, per-round timeline and submission snapshots, distinct current approvers/handlers, missing/error/retry states, and Workbench submitter-only manual urge with permission, version, round and 30-minute checks.
- BPM public APIs provide tenant-scoped batch current tasks and read-only progress. Feedback object/round authorization precedes BPM reads. Transfer/delegation/signing use live actionable assignees; waiting/suspended tasks are excluded.
- Urge transaction serializes on feedback, deduplicates recipients and idempotent requests, and atomically writes durable notification events, round cooldown and work-order audit. Notification clicks in both frontends resolve the exact live task; the task endpoint rejects a shared reader's fallback after a completion race.
- Verification: focused feedback/BPM service, authorization, notification and transaction tests passed; additional exact-task controller regression tests passed (2/2). Workbench 38 focused tests, typecheck and production build passed. Admin final production build passed; full typecheck remains blocked only by existing duplicate properties in src/api/zsjos/withdrawal/index.ts (lines 15–16, 40–45).
- Real Chrome with synthetic APIs passed at 1440px and 390px: actual Workbench feedback page, Admin approval panel and Admin message detail; summary, history snapshot, readonly, missing/no-approval, retry, urge retry-key/cooldown, stale notification and exact-task navigation. Screenshots inspected under C:\Users\EDY\AppData\Local\Temp\zsjos-feedback-approval. Owned verification servers on ports 5294/5295 stopped after checks.
- V294 isolated MySQL 8.4.11 verification passed initial/repeat/partial recovery, prerequisite/type/ledger failures, pre-existing markers, Chinese HEX and administrator rule preservation. Test schema prefix feedback_urge_20261008132158 retained. Historical migrations and role grants unchanged.
- Final Admin build: C:\Users\EDY\AppData\Local\Temp\zsjos-feedback-admin-final-20261008; Workbench build: C:\Users\EDY\AppData\Local\Temp\zsjos-feedback-approval-build-20261008. Scoped diff checks passed. API, permission and release documentation updated.
- Per explicit user decision, shared ruoyi-vue-pro database remains untouched; migration and real permissions are pending deployment. No application backend was available on port 48080, so authenticated end-to-end API calls and actual notification delivery remain unverified. Browser fixtures do not establish deployed runtime acceptance.
- HEAD remains d2f836006ff07b5517bcd6d485d72df788bf3cb0 on main. Existing unrelated changes preserved; no commit, branch, worktree, role assignment or shared-service operation performed. Workstream ownership released.
