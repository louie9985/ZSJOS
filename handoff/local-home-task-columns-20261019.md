# Workstream registration - home-task-columns-20261019

- Environment: local (ZSJOS_AGENT_ENV unset; no environment file).
- Goal: show overdue, today, future and unscheduled pending business tasks simultaneously in four responsive columns with independent pagination.
- Non-goals: backend task generation, deadlines, permissions, BPM approvals, database changes, service deployment or restart.
- Branch/worktree/base: main; D:/ZSJ-OS; 6bf0ce81a182b0a5ea040c8536a1ae7fad485e5e.
- Owner: current Codex chat; single writer for the following files.
- Ownership: frontend/workbench/src/pages/TodayTasksPage.tsx; frontend/workbench/src/styles/pages/today-tasks.css; frontend/workbench/docs/navigation.md; frontend/workbench/test/home-task-columns.html; frontend/workbench/test/home-task-columns.tsx; frontend/workbench/test/home-task-columns-browser.py; this handoff record.
- Dependencies: existing businessTaskList/businessTaskSummary APIs, BusinessReadScope and Workbench theme tokens. Preserve other dirty-worktree changes.
- Verification: existing focused home/task regression tests; Workbench typecheck and isolated production build; synthetic browser checks for four concurrent buckets, independent pagination, loading/error/retry/empty states, scope/view changes and task completion at desktop and mobile widths. Browser transport is synthetic and does not establish deployed acceptance.
- Target branch/integration order: None.

## Delivery - 2026-10-19 15:48 Beijing time

- Context: environment, branch, worktree and HEAD unchanged from registration.
- User goal: replace the homepage business-task time-bucket switch with four simultaneous columns.
- Decisions/results: overdue/today/future/unscheduled each owns loading, empty/error/retry states and six-item pagination. Preserve task action dispatch, read-only scopes and pending/done switching. Scope/view changes reset page state; late responses cannot repopulate the previous scope. Refresh/completion reload all visible buckets and summary. If a last page becomes invalid after task completion, return to the last available page.
- Layout: four columns when the panel has at least 720px content width; two below 720px and one below 420px. Narrow rows are 320px high with internal list scrolling to avoid making every lane as tall as the busiest lane. Pagination stays outside each scrolling list. Existing dashboard/calendar/announcement placement is preserved.
- Changed files: the three production/documentation files and three isolated browser fixture/check files listed in registration; this handoff record. No backend, SQL, dependency or shared permission changes.
- Verification: `npm run typecheck` passed. Production `npx vite build --outDir <private temporary directory> --emptyOutDir false` passed (existing chunk-size warning only). Existing focused tests: 17 passed, one known failing assertion excluded on the focused rerun. `git diff --check` passed for the changed tracked files.
- Existing test failure: `todayTasks.test.ts` expects the literal token sequence `state: { leadId: task.bizId }`; both original HEAD and the unchanged assistance handler include `tab: 'assist-history'` and `assistRequestId`. The unfiltered initial run was 17 passed/1 failed. No behavior or unrelated test was changed to satisfy this obsolete assertion.
- Browser: `python test/home-task-columns-browser.py` passed using bundled production fixture assets and synthetic API methods, with real headless Chrome at 1440/1000/390 widths. Verified four groups, 4/2/1 column geometry, no horizontal or column overflow, visible pagination, independent paging requests, completion/count refresh, original follow-up navigation, done view, page reset, ALL/USER read-only behavior, missing selected-user state, loading, late responses, partial error/retry and unauthorized states.
- Evidence: C:/Users/EDY/AppData/Local/Temp/home-task-columns-8mk5ct31 (fixture build and desktop/tablet/mobile PNGs); production build C:/Users/EDY/AppData/Local/Temp/home-task-production-7a901fc650c9496dbbcc14f9ad6dfca7.
- Dependency/integration impact: None. No commit, branch/worktree operation, deployment, shared service start/restart or real data mutation.
- Remaining work: authenticated target-environment acceptance after deployment has not been performed. Existing unrelated static assertion remains unresolved. Ownership released on delivery.

## Registration update - rollback

- User explicitly requested reverting this change. Reclaim the same seven-file ownership scope solely to restore the prior task switch and documentation, remove the three newly added browser files, and append rollback delivery evidence.
- Environment: local; current worktree unchanged. No concurrent edits appear in the three tracked target diffs. Verification: exact comparison with the original committed versions, removal of the three new fixture files, scoped diff check. No deployment or service operation.

## Rollback delivery - 2026-10-19 16:04 Beijing time

- Context: local; main; D:/ZSJ-OS; HEAD 6bf0ce81a182b0a5ea040c8536a1ae7fad485e5e.
- User goal/decision: revert the four-column task change and restore the original time-bucket switch.
- Result/files: restored TodayTasksPage.tsx, today-tasks.css and navigation.md to their exact pre-change Git blob contents after checking that their current hashes matched this workstream's delivered versions; removed only home-task-columns.html, home-task-columns.tsx and home-task-columns-browser.py. Retained this append-only handoff history.
- Verification: all three restored file hashes match the original base blobs; scoped Git status has no changes for those six source/documentation/test targets; scoped diff check passed. Runtime tests were not repeated for an exact restoration.
- Dependencies/integration impact: None. Other existing changes preserved; no branch, commit, deployment, service or database operation.
- Remaining work: None for rollback. Ownership released.
