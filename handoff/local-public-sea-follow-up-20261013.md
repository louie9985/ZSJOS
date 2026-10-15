# 公海跟进责任调整

## Registration
- Environment: local (ZSJOS_AGENT_ENV and /etc/zsjos/agent-environment absent).
- Workstream: local-public-sea-follow-up-20261013; owner: current Codex chat /root.
- Goal: preserve formal owner A, assign public-sea follow-up exclusively to current collaborator B, pause when B is absent, restore owner responsibility after exit.
- Confirmed decision: user explicitly supersedes the former A-and-B follow-up rule; unassigned public sea has no personal follow-up responsibility.
- Non-goals: historical follow-up edits, ownership/commission changes, DB writes/migrations, menu grants, dependencies, service/deployment operations, Git operations.
- Branch: main; worktree: D:/ZSJ-OS; base: e07efc9d6baa5d40b5ec07b78f6743b0d1b2915e; target/integration: None.
- Ownership: LeadAgingPoolServiceImpl, LeadCollaborationService, LeadObjectPermissionService; LeadMapper, LeadCalendarMapper/SearchSql; new LeadFollowUpResponsibilitySql; BusinessTaskMapper, BusinessTaskServiceImpl, BusinessTaskReminderService; related focused lead/task/query tests; backend/yudao-module-zsjos/AGENTS.md; docs/api/zsjos-lead-aging-pool.md, lead-follow-up-calendar.md, zsjos-lead-submission-dispatch.md; docs/architecture/data-and-permission-flow.md; this handoff. No ownership of concurrent order/performance/appeal/complaint work.
- Dependencies: existing tenant/menu permissions, public-sea cycles and legacy compatibility rows, existing task framework; no new packages. Source ownership targets initially clean.
- Verification: focused permission/lifecycle tests, executed query fixtures for A/B/waiting/reassignment/exit/tenant/deleted/history, shared frontend API consumption review, isolated Maven outputs if concurrent builds exist; scoped diff check. No runtime deployment claims.


## Scope/verification update
- Related task fixtures include MenuTaskSummaryServiceImplTest; add frontend/workbench/test/public-sea-follow-up-browser.py for isolated real-component A/B calendar checks.
- First 159 focused tests passed. Broader query tests exposed H2's lack of MySQL bit-literal syntax and the existing task-only fixture missing the newly joined Lead/pool tables; only test fixtures are adapted, production SQL remains MySQL compatible.
- Shared reassignPending also serves media/delivery tasks: version/scoping changes are restricted to the two Lead sales-follow-up task types, preserving other callers.
- Scope extension: LeadFollowUpServiceImpl and its existing test (both clean before edit). B follow-up must first reconcile pending task assignees before completing reminders; end obsolete owner-first-follow-up pending tasks without inventing A first-follow-up facts. Final audit found the old assignee-specific completion would otherwise leave pre-existing A reminders pending.


## Delivery
- Result: active public sea uses only configured B for follow-up, waiting pauses personal responsibility, reassignment switches B, exit restores formal owner. Formal ownership, completed/cancelled history and existing menu grants are preserved.
- Query coverage: employee following/today/overdue scope; calendar days/cards/search; pending task page/count/menu badges and subordinate task grouping; reminder recipients. Existing A-assigned pending tasks are projected using current pool state without bulk DB repair.
- Lifecycle: assign/manual entry with B/exit reassign pending follow-up tasks and increment their version. B follow-up reconciles residual assignees before consuming reminders, cancels obsolete pending first-follow-up tasks without fabricating A first-follow-up facts.
- Verification: 191 focused backend tests passed, including executed H2 MySQL-mode query fixtures and existing tenant-interceptor task tests. Final follow-up-save change reverified with 60 affected tests, all passed. Logs: %TEMP%/zsjos-public-sea-tests.log and %TEMP%/zsjos-public-sea-follow-save-tests.log.
- Browser: real Chrome at 1600 and 390 widths, 8/8 synthetic API cases passed for A assigned/waiting and B waiting/assigned. Existing Workbench calendar component and availableActions consumption verified. Admin pool page only exposes existing assign/exit actions and Lead history remains read-only; API shape unchanged. No frontend production changes or build-input changes.
- Content: 23 scoped files checked for UTF-8 validity/replacement characters, trailing whitespace and git diff --check; passed. Module AGENTS and affected API/architecture documents synchronize the user-confirmed durable rule.
- Limits: no live authenticated target data, target MySQL execution, deployment or restart performed. H2/query-parser/browser fixture checks do not represent target environment acceptance or production query-plan measurements.
- Ownership released on delivery. Unrelated concurrent source changes preserved; no branch, commit or push.

## Reopened — 2026-10-14: cancel obsolete public-sea follow-up tasks
- User explicitly requests cancelling existing erroneous tasks, superseding prior projection-only and reassignment approach for obsolete pending tasks.
- Same local/main/base context. Ownership extends to new LeadPublicSeaTaskCleanupService and LeadPublicSeaTaskCleanupMapper/Sql, LeadAgingPoolScheduler and focused cleanup tests. Existing workstream owns affected aging/follow-up services and docs.
- Scope: cancel pending first-follow-up tasks and obsolete reminders for active public sea; preserve current B reminders created since current assignment, completed/cancelled history, ownership and unrelated task types. Existing scheduler performs tenant-scoped repeatable cleanup; immediate entry/assignment transitions invoke same predicate.
- No live target exists in this task per user; source/runtime cleanup implementation only, no service restart or direct shared DB write. Verify executed fixtures, boundary tests and scheduler wiring.


## Cancellation delivery — 2026-10-14
- Supersedes earlier transfer/projection-only behavior for erroneous tasks: entry/assignment/collaborator removal and B-save now cancel obsolete pending follow-up tasks; exit first cleans obsolete tasks while the pool is active.
- Existing LeadAgingPoolScheduler calls tenant-scoped cleanup every scheduled cycle (fixed delay 60 seconds; maintenance mode skips). Each batch scans at most 500 IDs, then UPDATE rechecks pending, tenant, deletion, active pool, assignee and assignment-time criteria.
- Preserve current B reminders created since assigned_at (fallback entered_at), normal legacy B reminders since released_at, completed/cancelled rows, non-pool leads and other task types. Cancelled rows retain cancellation reason/time and increment version. No bulk task deletion, no invented follow-up facts.
- Verification: 47 focused tests passed, including dynamic MyBatis XML binding, executed H2 MySQL-mode cancel statements, repeated zero-change execution, changed-state rechecks, old reassigned A task cancellation, current B task preservation, tenant/deletion/history boundaries and scheduler tenant/maintenance/failure isolation. Final exit-order change checked separately.
- No real target data cancellation performed: user explicitly scoped investigation to source and reported no local examples. Target MySQL/runtime scheduler execution and deployment remain unverified; deployment is required before stored remote tasks change. No migration or manual SQL required by implementation.
- No frontend change in this continuation; prior browser checks remain applicable to unchanged UI. No shared service or Git operations. Ownership released after final checks.

### Final verification completed
- Final exit-before-cleanup ordering regression passed on recheck: LeadAgingPoolServiceImplTest, LeadPublicSeaTaskCleanupServiceTest, LeadPublicSeaTaskCleanupTest; BUILD SUCCESS. See %TEMP%/zsjos-public-sea-cancel-recheck.log.
- An earlier final invocation failed during unrelated media/content test compilation with missing class references. Source/class existence was verified and no active Maven process was present before recheck; recheck succeeded without changing those unrelated files. Shared-output interference is suspected, not proven.
- Source cancellation implementation complete; no deployed/live cancellation claimed.
