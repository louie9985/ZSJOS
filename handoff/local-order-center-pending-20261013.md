# Workstream local-order-center-pending-20261013

- Status: active
- Environment: local (ZSJOS_AGENT_ENV unset; /etc/zsjos/agent-environment absent).
- Owner: Codex current chat.
- Goal: add 当前中心待审核 while preserving existing whole-order 待审核.
- Non-goals: no order lifecycle, permission, database, deployment, or existing layout changes.
- Branch: main; worktree: D:/ZSJ-OS; base: 45f1edbe24b316b5f5924416c4fc18977658ff5f.
- Target branch / integration order: None.
- Ownership: SalesOrderConstants.java; SalesOrderPageReqVO.java; SalesOrderServiceImpl.java; SalesOrderApprovalRoundMapper.java; SalesOrderServiceImplTest.java; SalesOrderRevisionMapperTest.java; docs/api/zsjos-sales-order.md; frontend/workbench/test/order-approval-status.tsx; frontend/workbench/test/order-approval-status-browser.py; this record.
- Dependencies: existing BPM public task/node status API and server-driven Workbench tabs; existing unrelated changes preserved, including SalesOrderApprovalPage.tsx and services/api.ts.
- Verification: focused service/mapper tests including both centers, waiting supervisor, pagination/count/cursor, advanced filters and legacy behavior; Workbench typecheck and isolated desktop/mobile browser fixture; Admin legacy contract regression; scoped diff.

- Scope update: BPM getTodoTaskPage must populate its existing status field from task-local variables to distinguish RUNNING from APPROVING (waiting supervisor). Also own BpmProcessTaskApiImpl.java, BpmTaskServiceImpl.java and BpmProcessTaskApiImplTest.java; no BPM state transitions change. Node summary alone labels unfinished parent tasks pending, so use task status instead.

## Delivery — 2026-10-13 15:05:46 +08:00

- Status: completed; ownership released.
- Branch/worktree/base: registration unchanged; HEAD 45f1edbe24b316b5f5924416c4fc18977658ff5f.
- User goal: confirmed adding 当前中心待审核 while retaining original 待审核.
- Key decisions: publish center_pending after pending_approval in server profile; retain lifecycle filters and Admin handled=false behavior. Select current approval round of pending orders; combine keyword/advanced filters and original user/center permissions. Only RUNNING ordinary BPM tasks enter the new scope, excluding approved parents waiting for parallel supervisor sign-off and child sign tasks, before pagination/counting. No persisted lifecycle state added.
- Result: implemented backend query/profile/validation, existing BPM DTO status projection with task-local loading, focused tests, fixture and API documentation. Workbench renders server tabs without production frontend edits; preserved concurrent layout/calendar/API changes. No database, service lifecycle, dependencies, commits, push or deployment.
- Changed files: the 12 source/test/document files and this record listed in registration and scope update. SalesOrderConstants.java, SalesOrderPageReqVO.java, SalesOrderServiceImpl.java, SalesOrderApprovalRoundMapper.java, SalesOrderServiceImplTest.java, SalesOrderRevisionMapperTest.java; BpmProcessTaskApiImpl.java, BpmTaskServiceImpl.java, BpmProcessTaskApiImplTest.java; docs/api/zsjos-sales-order.md; frontend/workbench/test/order-approval-status.tsx and order-approval-status-browser.py.
- Verification: Maven reactor BUILD SUCCESS, 101 tests (15 BPM API, 5 parallel-sign, 79 order service, 2 mapper). Covers both centers, advanced empty/intersection, current round, historical/other-tenant exclusion, pagination after filtered BPM pages, counts, cursor boundaries, denied centers, and preserved lifecycle/Admin handled=false paths. Initial new test fixture used wrong state variable key; corrected to framework TASK_VARIABLE_STATUS and reran successfully. Workbench npm run typecheck PASS; approval guard 6/6 PASS. Real Chromium isolated fixture via existing Vite 5197 PASS at 1440/390 widths for server tabs, new scope/center request and results, table pagination reset, inbox, errors/retry/empty/denied. Screenshots inspected under Windows TEMP/order-approval-status-browser. Scoped diff/UTF-8 checks PASS.
- Dependency/integration impact: existing BPM public DTO status now populated for todo pages; state-machine transitions unchanged. No schema migration. Backend deployment supplies the new tab to existing Workbench; Admin continues old contract.
- Remaining work: authenticated live acceptance after backend rebuild/restart/deployment; not performed or authorized here. Browser data is isolated synthetic fixture, not live business acceptance.

## Registration update — reorder follow-up

- Status: active; same owner, local environment, branch/worktree/base and scope as registration. User requests 当前中心待审核 before 待审核. Verification: scoped sequence assertions and existing desktop/mobile browser fixture; no query logic change.

## Delivery — 2026-10-13 17:57:10 +08:00

- Status: completed; ownership released.
- Branch/worktree: main, D:/ZSJ-OS; HEAD unchanged 45f1edbe24b316b5f5924416c4fc18977658ff5f.
- User goal: move 当前中心待审核 before 待审核.
- Key decisions/result: server profile order is now 全部、当前中心待审核、待审核、待重提、已通过、已终止、历史旧单（已重提）. Query semantics unchanged.
- Changed files: SalesOrderServiceImpl.java, SalesOrderServiceImplTest.java, docs/api/zsjos-sales-order.md, frontend/workbench/test/order-approval-status.tsx, frontend/workbench/test/order-approval-status-browser.py, this record.
- Verification: scoped server sequence assertion and git diff --check PASS; existing real Chromium isolated fixture PASS at 1440/390 widths, including tab order, center switching, table/inbox and pagination. No compile rerun for the mechanical ordering change; previous logic test evidence remains applicable.
- Dependency/integration impact: None; preserved unrelated local work.
- Remaining work: backend deployment/restart and authenticated live acceptance remain outstanding; no service restarted.
