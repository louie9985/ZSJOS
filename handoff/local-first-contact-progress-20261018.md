# First contact checklist and plan branch — 2026-10-18

- Workstream: first-contact-progress-20261018; environment: local (fallback).
- Goal: implement the user-approved optional checklist, first-contact plan completion branch, persisted snapshots, editable overview progress and historical compatibility.
- Non-goals: deployment/restart, shared DB mutation, existing task auto-completion, role grants, unrelated edits.
- Branch: main; worktree: D:/ZSJ-OS; base: 6bf0ce81a182b0a5ea040c8536a1ae7fad485e5e.
- Owner: current Codex chat; integration target/order: None.
- Ownership: studentcontact service/constants/config and focused tests; registration contact VO/controller/DO/mappers; Workbench RegistrationPages, LeadDetailOverview and new checklist component/tests, services/api.ts; Admin studentContactConfig; V328 migration and focused SQL verifier; docs/api/registration-fulfillment-api.md; this handoff. Preserve all pre-existing modifications.
- Dependencies: existing task commands, config versioning, service relation version, business events, configured permissions, tenant 1. No new dependencies.
- Verification: isolated Java compile/tests, Workbench typecheck/focused tests, Admin affected check, synthetic browser desktop/mobile, isolated MySQL first/replay/failure/recovery if engine available; scoped diff.

- Ownership update: include Workbench test/first-contact-progress.{html,tsx}, test/first-contact-progress-browser.py and StudentFirstContactProgressTest; isolated test artifacts only.
- Ownership update: ZsjosErrorCodeConstants.java for distinct checklist validation/history/attachment/replay errors; no existing codes changed.

## Delivery — 2026-10-18

- Implemented optional first-contact checklist, explicit plan-completed-at-first-contact flag, direct supervision/contact branch, immutable record snapshot, independent service progress, versioned and idempotent update with before/after business events, configured update/upload permission, historical task-config reconstruction, and retired-key filtering in new operations/configuration.
- Workbench form/extension routing, overview progress/edit/evidence upload, phase annotation and history snapshot display implemented; Admin configuration notice and shared filtered configuration contract covered. Edit dialog keeps its original version and is keyed by service ID to avoid overwriting refreshed or switched service state.
- Preserved existing StudentInfo/registration/API/overview changes in the dirty main worktree. No Git branch/worktree/commit/push operations.

### Verification evidence
- PASS: isolated Java 25 compile and 53 tests (35 existing service, 2 configuration, 16 new progress/branch/history/permission/replay cases). Project and log: C:/Users/EDY/AppData/Local/Temp/zsjos-first-contact-20261018/test.log. Uses current affected source, existing built ZSJOS jar and current required public interfaces; not a full-server startup test.
- PASS: final Workbench npx tsc --noEmit; existing scoped Vitest 3 files / 9 tests. Admin contact configuration SFC script/template compile using installed Vue compiler. No new dependency.
- PASS: Chrome synthetic browser fixture at 1440 and 390 widths: overview, edit/save/correct, empty checklist submit, plan yes/no and deadline routing, hidden-value reset, error retry retaining idempotency key, denied and empty states; no page errors or horizontal overflow. Final run after version/key changes passed. Retained source: frontend/workbench/test/first-contact-progress-browser.py; screenshots: C:/Users/EDY/AppData/Local/Temp/zsjos-first-contact-browser-20261018. Test input uses visible checkbox labels and checks native state; no production changes were made to accommodate click mechanics.
- PASS: V328 MySQL 8.4.11 isolated fixture first/replay, prerequisite rejection/recovery, partial-column recovery, permission conflict, ledger failure rollback/recovery, Chinese HEX, unchanged grants and historical values. Retained databases prefix zsjos_fc_20261018175429; verifier script/sql/mysql/tools/test_first_contact_progress.py.
- PASS: scoped git diff --check. Existing CRLF normalization notices only.

### Runtime limits and remaining rollout
- Read-only local DB inspection: required contact checklist column and service delivery_stage/version exist; exactly one student page exists; new V328 fields and both V328 ledger entries are absent.
- V328 was NOT applied to the shared development database. No service restart/deployment, permission assignments or real task/data mutations were performed. Administrators must configure the new checklist-update permission after migration.
- No authenticated employee runtime acceptance or full-server startup performed. Browser writes were intercepted synthetic fixtures; Java tests use mocked service boundaries, so concurrent database transaction rollback remains a target-environment acceptance item.
- No full frontend production build: no routes/dependencies/assets/build configuration changed; scoped type, component and browser verification applied.
- Apply the reviewed V328 to the intended environment, then deploy backend/Workbench and validate a real assigned service using both plan branches and checklist updates. Preserve old study-plan tasks and nullable historical plan flags.
- Ownership released on delivery.

## Registration update — 2026-10-19
- Goal: approved 2h/72h stage-dependent next-contact policy, five scoped shortcuts, optional ongoing scheduling and taskless follow-up; existing deadlines/approvals unchanged.
- Context: local fallback, main, D:/ZSJ-OS, base unchanged 6bf0ce81a182b0a5ea040c8536a1ae7fad485e5e. Owner: this chat; integration None.
- Reacquire prior contact service/VO/Workbench/API/docs/tests ownership. Additional files: services/studentContactSchedule.ts and test, V330 migration, tools/test_student_contact_schedule.py. Preserve other work, including V329.
- Verification: isolated Java tests, stage schedule unit tests/typecheck, existing browser fixture adapted for optional time and taskless follow-up, controlled MySQL migration/replay/recovery. No shared DB application or deployment.

## Delivery update — 2026-10-19
- Implemented stage-specific shortcuts and conditional next-contact validation. First failure: +30min/+1h and 120min deadline. Pending study plan: additionally +1/+2/+3 days, new tasks use current published policy. Completed plan/ordinary contact: optional time, no next task when omitted.
- Added taskless ordinary follow-up under existing owner/status/stage/permission checks; service lock finds/completes an existing ordinary task when present. No placeholder task, no stage bypass. Omitted task identity has a separate replay fingerprint; original task-linked retries remain compatible.
- V330 makes record task_id/next_contact_at nullable; clones tenant-1 published config with 4320min timeout, archives old config without rewriting its timeout, upgrades only 1440min drafts with version bump. Leaves existing task deadlines/approvals untouched. Replay preserves later administrator settings. No role grants.
- Workbench uses one schedule policy for shortcut choices, required validation and extension prompt. Global sales shortcuts unchanged. Overview empty deadline text now says 未安排下次联系. API/doc contract updated.
- PASS: isolated Java 25 compilation and 63 tests, schedule-test.log in C:/Users/EDY/AppData/Local/Temp/zsjos-first-contact-20261018. Includes old-task/new-policy, nullable first/plan completion, taskless recording/replay, existing task completion, stage rejection and allowed/denied action projection. Mockito fixture adjustments made for published-config and taskless queries.
- PASS: Workbench typecheck; 16 Vitest cases including schedule policy and unchanged sales shortcut behavior.
- PASS: final Chrome desktop/mobile fixture: optional blank first completion, 3-day within limit, 5-day manual date still prompts approval, short-only failed-first options, taskless ordinary blank submission, overview edits/retry/denial. All business calls intercepted. Existing screenshot directory refreshed: C:/Users/EDY/AppData/Local/Temp/zsjos-first-contact-browser-20261018.
- PASS: MySQL 8.4 controlled V330 first/replay, partial/prerequisite recovery, ledger-failure rollback/recovery, nullable unique task IDs, preserved old record and old timeout, Chinese HEX. Retained test DB prefix zsjos_schedule_20261019134809. Script: script/sql/mysql/tools/test_student_contact_schedule.py.
- Local code/tests/docs complete. Shared business DB has NOT received V330 in this workstream; no deployment/restart, real account permission change, task rewrite, Git commit/push. Running application remains unverified until migration/publication and authenticated acceptance. No full-chain SQL or whole-server build: local migration and focused behavior scope only. Mock service tests do not prove real DB concurrency/transaction integration.
- Ownership released after delivery; prior uncommitted work preserved.
