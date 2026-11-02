# 订单驳回取消收集任务

- Environment: local (ZSJOS_AGENT_ENV and /etc/zsjos/agent-environment absent).
- Workstream/owner: local-collection-rejection-20261102 / Codex.
- Goal: rejected order collection tasks become cancelled; issued public links and submitted data remain usable; successor orders reuse current archives.
- Non-goals: schema/data changes, deployment, permission changes, link revocation, commits.
- Branch/worktree/base: main / D:\ZSJ-OS / 0ecc74d4d13bcfbc0361ecdbc2bbd73327359425.
- Target branch/integration order: None.
- Ownership: StudentInfoTaskService.java; StudentInfoTaskServiceTest.java; StudentInfoOrderTerminationTest.java; frontend/workbench/src/pages/StudentInfoTasksPage.tsx; docs/api/order-student-information-tasks.md; this record; isolated verification outputs under frontend/workbench/tmp/collection-rejection/.
- Dependencies: preserve all existing concurrent changes; existing order rejection status/rounds and successor order creation; public token validity remains form-owned.
- Verification: focused Java tests/compilation, Workbench typecheck, browser fixture for cancelled status and blocked staff commands; no real account or database writes.

## Delivery

- Tasks now project CANCELLED from revision_required/superseded order states, including terminated orders with retained rejected rounds. Filters exclude cancelled tasks from form-progress buckets. Old task commands reject under the order lock; forms/tokens are untouched.
- Existing successor-order creation and current-archive reuse remain in place. No schema migration, production data writes, restart, commit or push.
- Isolated Java compilation and 32 focused JUnit tests passed. Tests include public-token draft/save submission after rejection, cancellation regardless of form completion, staff-command denial, and execution of cancellation/pending filter SQL using H2 MySQL mode with tenant/deleted-round cases. H2 is not production MySQL acceptance.
- Workbench npm run typecheck passed. Ego-browser fixture at localhost:5174 verified cancelled filter, detail status, public-link preservation message and absence of staff command entry points. Screenshot capture was blocked because the browser window was minimized; visual screenshot QA remains unverified. Browser task space 13 finished.
- Admin/H5 have no student-info-task API consumer; public form service is unchanged and covered by the token regression test. No real-account deployed API acceptance performed.
- Documentation synchronized in docs/api/order-student-information-tasks.md. Ownership released.
