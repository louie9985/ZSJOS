# 撤回修改与终止订单
- Environment: local (ZSJOS_AGENT_ENV and /etc/zsjos/agent-environment absent)
- ID/owner: local-order-withdraw-20261102 / Codex
- Branch: main; worktree: D:/ZSJ-OS; base: 0ecc74d4d13bcfbc0361ecdbc2bbd73327359425
- Goal: user-confirmed withdrawal to revision_required; termination stays final.
- Scope: SalesOrderTerminateReqVO, SalesOrderServiceImpl and its test; Workbench MySalesOrderPage, SalesOrderDetailCards and tests, services/api.ts; Admin mySalesOrder; docs/api/zsjos-sales-order.md, docs/business/lead-order-state-machine.md; this record.
- Non-goals: SQL, deployment, restart, grants, commits, unrelated changes.
- Dependencies: existing create permission and terminate object authorization, BPM business cancellation API; preserve existing edits.
- Integration: None.
- Verification: focused backend and component tests; both frontend typechecks; browser where available.

## Delivery
- Implemented optional withdrawForRevision on existing terminate command. Pending approval can withdraw to revision_required; final termination still blocks resubmission. Existing permission and BPM business authorization reused; command intent recorded separately and late process events ignored after round closure.
- Workbench and Admin expose separate confirmation actions. Workbench gates order actions on configured create permission as well as server object capabilities. Updated directly affected API and state-machine documents according to this chat's explicit decision.
- Backend SalesOrderServiceImplTest: 89 passed, 0 failures/errors. Workbench typecheck passed; SalesOrderDetailCards 11 tests passed; scoped diff checks passed.
- Admin typecheck remains blocked by existing MES src/views/mes/pro/task/edit/index.vue:86 unused handleRefresh; not changed.
- Ego browser read-only inspection reached both local frontends and Workbench pending order detail. Current account has no mutation capability on the available pending order, so new buttons/confirmation visual and real mutation end-to-end acceptance remain unverified. Updated backend not restarted/deployed. No real orders changed.
- No database changes, grants, service restarts, commits or push. Ownership released.
