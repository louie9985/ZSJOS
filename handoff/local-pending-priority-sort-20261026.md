# local-pending-priority-sort-20261026

- Environment: local (environment variable unset and marker absent).
- Owner: Codex /root; branch: main; worktree: D:/ZSJ-OS; base: 8279212fb97129ba6b006dbb0452ad843dfde4b5; target branch/integration: None.
- Goal: restore personal pending-task priority in Lead Management, then submission descending; Student Management keeps pending priority, then acceptance descending with nulls last.
- Non-goals: task eligibility, permissions, database changes, backend changes, deployment, Git publication; subordinate sales keeps its existing server order.
- Ownership: frontend/workbench/src/pages/LeadManagementPage.tsx, RegistrationPages.tsx, lead-management-unified.guard.test.ts; src/services/leadSubmissionDefaultSort.test.ts; test/lead-task-indicators.tsx, lead-task-indicators-browser.py, student-service-sidebar.tsx; docs/api/lead-task-indicators.md, student-task-indicators.md, the sorting paragraph in zsjos-lead-submission-dispatch.md; this record.
- Dependencies: existing backend pendingTasksFirst pagination, submittedAt ordering and acceptedAt sorting; preserve other dirty work and pending-follow-up workstream changes.
- Verification: focused Vitest and TypeScript checks; desktop/mobile real-page synthetic-transport browser flows for priority, time ordering, completion and refresh; scoped diff checks.
- Decision: current user instruction supersedes documented Lead no-priority default; Student time explicitly clarified as acceptance time.

## Delivery — 2026-10-26 10:47 Beijing time

- Context: branch/worktree/base unchanged from registration; HEAD 8279212fb97129ba6b006dbb0452ad843dfde4b5.
- Result: Lead Management requests server pending priority before pagination, retains pending groups on page merge/detail hydration/refresh, then defaults to submission descending and ID descending. Explicit sorting uses the chosen field inside each group. Subordinate-sales embedded page keeps its original behavior and default-sort label.
- Student Management retains pending priority and now defaults requests to acceptedAt descending; backend already supports nulls-last acceptance sorting and stable ID descending. Explicit column sort overrides the default; selecting default restores acceptance sorting. Existing reminder eligibility, authorization and filters unchanged.
- Changed files: LeadManagementPage.tsx, RegistrationPages.tsx, lead-management-unified.guard.test.ts, leadSubmissionDefaultSort.test.ts, lead-task-indicators.tsx, lead-task-indicators-browser.py, student-service-sidebar.tsx; docs/api/lead-task-indicators.md, student-task-indicators.md, sorting paragraph only in zsjos-lead-submission-dispatch.md; this record. The pre-existing pending-follow-up documentation edit was preserved.
- Verification: 38 focused Vitest tests passed; npm.cmd run typecheck passed. Existing real-page synthetic transport browser scripts passed Lead split/table at 1440/390 and Student inbox at 1440/390 plus table at 1440, including task priority, request sort contract, completion/recovery, selection and reminders. Inspected desktop Lead and mobile Student screenshots. git diff --check passed for all changed tracked files.
- Backend verification: focused Maven LeadTaskPriorityTest and StudentServiceReadServiceTest BUILD SUCCESS; 14 discovered, 12 passed, 2 environment-gated live MySQL tests skipped (ZSJOS_CLASS_MYSQL_VERIFY not enabled). Existing H2 queries verify priority before pagination and scope. Log: %TEMP%/zsjos-pending-priority-sort-maven.log. No backend source changed.
- Evidence: D:/ZSJ-OS-backups/lead-task-indicators and D:/ZSJ-OS-backups/student-task-indicators screenshots. Temporary Vite verification process on port 5198 stopped after checks.
- Dependency/integration impact: None; no dependencies, migration, account grants, shared service changes, deployment, commits or publication. Existing unrelated dirty work preserved.
- Remaining: authenticated live-account and deployed-version acceptance unverified; local browser uses synthetic transport. Ownership released.
