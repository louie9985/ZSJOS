# local-collection-material-selection-20261103
- Environment: local (environment variable and environment file absent).
- Goal: registration collection generation offers server-defined material selection; eleven approved material keys default off.
- Non-goals: template editing, archive merge, reuse behavior, grants, database, deployment.
- Branch: main; worktree: D:\ZSJ-OS; base: f7abc09b91d30e140ef8bf4895b4b8b8181f0681; owner: current Codex chat.
- Ownership: StudentInfoTaskController.java, StudentInfoTaskVO.java, StudentInfoTaskService.java, new StudentInfoMaterialSelection.java; StudentInfoTaskServiceTest.java; frontend/workbench/src/services/studentInfoTasks.ts, pages/StudentInfoTasksPage.tsx, new components/StudentInfoGenerateModal.tsx; docs/api/order-student-information-tasks.md; this record.
- Dependencies: published field configuration, existing task authorization and snapshot/public runtime. Integration: None.
- Verification: focused Java tests, Workbench typecheck, browser checks if available, scoped diff.

## Delivery 2026-11-03
- Implemented server-authoritative generation configuration and eleven material selections (default empty), snapshot filtering before prefill/copy, template-version rejection and idempotency fingerprint. Basic template fields and direct reuse unchanged.
- Workbench generation now opens the selection dialog, then existing share/link dialog. Both toolbar and archive generation entries use it.
- Verification: Workbench npm run typecheck passed; git diff --check passed. 34 Java tests passed initially and after added snapshot/copy and changed-selection retry assertions. Final run compiled using tmp/order-collection/verified/material-selection-javac.args excluding concurrently added unrelated StudentSummaryController.java (invalid class-level ApiAccessLog annotation blocked the broad script). No changes to that other workstream.
- Ego browser synthetic component checks: 11 unchecked defaults, selecting portrait + identity_images sends exactly those keys, empty selection sends [], config-load error/retry. Desktop 1440 and mobile 390 screenshots inspected; no horizontal overflow. Artifacts: frontend/workbench/tmp/material-selection/. Isolated fixture server stopped; browser space finished.
- Not verified: deployed backend, real-account end-to-end H5 submission and production. No database/service deployment, commit or push. No permission grants changed. Current archive still uses latest submitted form without merging omitted material fields.
- Ownership released.
