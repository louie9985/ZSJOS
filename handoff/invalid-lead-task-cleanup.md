# Invalid Lead Task Cleanup Workstream

- Workstream ID: `invalid-lead-task-cleanup`
- Goal: Cancel historical pending lead follow-up tasks after a lead is invalid and prevent stale invalid-lead tasks from appearing in Today's Tasks.
- Non-goals: Delete task or lead history; change permissions; alter valid, submitted, converted, or closed lead behavior; modify unrelated notification behavior.
- Branch: `codex/invalid-lead-task-cleanup`
- Worktree: `D:\ZSJ-OS-worktrees\invalid-lead-task-cleanup`
- Base commit: `eaae3965805dc493cc6ad34aabede623843cb540`
- Target branch: `main`
- Ownership scope: `backend/yudao-module-zsjos` business-task query and focused tests; `script/sql/mysql/migrations/V033__cancel_invalid_lead_pending_tasks.sql`; directly affected migration/bootstrap verification documentation; this handoff file.
- Owner: Codex `/root`
- Dependencies: Existing V008 task schema, V014 qualification state, and the cancellation behavior in commit `eaae396580`.
- Integration order: Standalone; integrate after resolving any concurrent ownership of the recorded files.
- Verification plan: Focused ZSJOS tests; module compile/test; SQL syntax and relationship review; controlled V033 execution twice against local MySQL; database read-back; authenticated `xiaoshou1` summary and overdue-page requests.
- Status: `implemented-uncommitted`

## Delivery Entries

### 2026-08-12 11:05:11 +08:00

- Branch: `codex/invalid-lead-task-cleanup`
- Worktree: `D:\ZSJ-OS-worktrees\invalid-lead-task-cleanup`
- HEAD commit: `eaae3965805dc493cc6ad34aabede623843cb540`
- User goal: Remove the invalid lead from `xiaoshou1`'s overdue tasks, repair historical pending tasks, and prevent recurrence.
- Key decisions: Preserve task history by cancelling rather than deleting; limit cleanup and read-side filtering to `lead_first_follow_up`, `lead_follow_up_reminder`, and `lead_qualification`; match lead and task by tenant; make V033 repeatable; leave unrelated task types and non-invalid leads unchanged.
- Execution or analysis result: Added read-side filtering for stale invalid-lead lifecycle tasks, regression coverage for filtering and invalid-judgment cancellation, repeatable V033 cleanup and bootstrap verification, and synchronized business/migration documentation. Applied V033 twice to local MySQL. Task `14` changed from `pending` to `cancelled`, version `0` to `1`, with Beijing cancellation time `2026-08-12 11:04:10`; no invalid-lead pending lifecycle tasks remain. Authenticated `xiaoshou1` verification now returns one overdue task, ID `2`; future task IDs `19` and `18` remain.
- Changed files: `backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/task/BusinessTaskServiceImpl.java`; `backend/yudao-module-zsjos/src/test/java/cn/iocoder/yudao/module/zsjos/service/task/BusinessTaskServiceImplTest.java`; `backend/yudao-module-zsjos/src/test/java/cn/iocoder/yudao/module/zsjos/service/lead/LeadQualificationServiceImplTest.java`; `script/sql/mysql/migrations/V033__cancel_invalid_lead_pending_tasks.sql`; `script/sql/mysql/bootstrap.sql`; `script/sql/mysql/verify-bootstrap.sql`; `script/sql/mysql/migrations/README.md`; `docs/business/lead-order-state-machine.md`; `handoff/invalid-lead-task-cleanup.md`.
- Verification evidence: Focused Reactor tests passed (`8/8`); all selected ZSJOS tests executed (`116`) with the new and directly affected tests passing, while three unrelated existing tests failed; `yudao-server` reactor package passed with tests skipped; V033 SQL matched exactly task `14`, executed successfully, reran without changing its version, recorded schema version V033, and its read-only invariant check returned `PASS`; authenticated summary returned overdue `1`, today `1`, future `2`, and overdue page contained only task `2`; `git diff --check` passed.
- Dependency or integration impact: Requires V008 and V014 schema behavior and should be integrated after resolving any concurrent ownership of the touched task, SQL bootstrap/verification, and documentation files. Local MySQL external state now contains applied V033 and the cancelled task `14`.
- Remaining work: Review and commit this workstream, then integrate into `main` and rerun affected checks. Existing unrelated failures remain in `CodegenEngineUniappTest.testExecute_treeSearch`, `LeadFollowUpRuleServiceImplTest.updatesTimeoutAndAdvancesVersion`, `LeadManagementServiceImplTest.detailProjectsOwnerActionsForEachLifecycleStage`, and `LeadNotifySceneProviderTest.registersAllScenesWithSceneSpecificVariables`.
