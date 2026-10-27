# Workstream local-approval-completed-refresh-20261027
- Environment: local (ZSJOS_AGENT_ENV unset; /etc/zsjos/agent-environment absent).
- Owner: Codex /root; branch: main; worktree: D:\ZSJ-OS; base: 8279212fb97129ba6b006dbb0452ad843dfde4b5.
- Goal: completed approval detail uses done queries without refreshing stale todo.
- Non-goals: backend/permissions/database changes; deployment; other working-tree changes.
- Ownership: frontend/workbench/src/components/bpm/BpmApprovalDetail.tsx; frontend/workbench/test/approval-center-layout.tsx; frontend/workbench/test/approval-center-layout-browser.py; docs/frontend/approval-center.md; this record.
- Dependencies: existing BPM done API and parent completion flag. Integration target/order: None.
- Verification: existing focused Vitest guards, typecheck, isolated browser fixture with rejection of completed todo lookups, subsequent refresh and next-task checks.

## Delivery — 2026-10-27 13:52:16 (Beijing)
- Branch/worktree/base: unchanged from registration; HEAD 8279212fb97129ba6b006dbb0452ad843dfde4b5.
- Result: completed state changes resource identity to done before querying business detail; success no longer reloads stale todo. Non-completing actions retain refresh. Manual refresh and next-task behavior covered.
- Changed files: all five registered implementation/test/documentation/record paths. Fixture now rejects completed todo queries and records business-detail views; browser checks done refresh and next todo.
- Verification: 23 focused Vitest tests passed; scoped git diff --check passed. Browser passed five viewport layouts, approve/retained result/manual refresh/next todo, feedback/table/read-only/stale/record-failure scenarios. Separate reject failure/retry/completion browser case passed.
- Limitations: full browser script stopped at existing commentFail visible-message assertion (comment error element not found); no unrelated UI change. Full npm run typecheck exited with Node Zone Allocation out-of-memory. Live authenticated approval and deployment not performed; browser evidence uses isolated fixture adapter only.
- Dependencies/integration impact: None; no API/schema/dependency changes. Remaining work: full typecheck with adequate memory and live environment acceptance. Ownership released; no commit or deployment.
