# Registration — local-approval-list-title-20261022

- Environment: local (ZSJOS_AGENT_ENV unset; /etc/zsjos/agent-environment absent).
- Owner: Codex /root. Read-only contributor: approval_title_source (backend title source audit).
- Branch / worktree / base: main / D:/ZSJ-OS / 404387de566fcb9abf4b264f0e005bf0566ea209.
- Goal: use the server-provided approval name as the inbox heading; retain business numbers in the summary.
- Non-goals: backend/API contracts, process names or definitions, business data, permissions, dependencies, deployment and Git operations; preserve all pre-existing changes.
- Ownership: frontend/workbench/src/pages/BpmApprovalCenterPage.tsx (title selection only); docs/frontend/approval-center.md (title contract only); this handoff (root only). Prior related workstreams delivered and released ownership.
- Dependencies: existing BPM processInstance.name and task.name; business-summary-batch remains unchanged. Existing local Vite 5174 and isolated HTTP fixtures for browser verification.
- Verification: existing focused approval-label tests; Workbench typecheck; real Chrome desktop/mobile todo/done title and number-summary checks, table consistency and missing-name fallback using isolated fixtures; scoped diff review.
- Target branch / integration order: None.

## Delivery — 2026-10-22 17:51 Beijing

- Branch / worktree / HEAD: unchanged from registration, main / D:/ZSJ-OS / 404387de566fcb9abf4b264f0e005bf0566ea209. Environment local; owner Codex /root.
- User goal / result: approval inbox headings now display the server-provided process name rather than a business summary title that may contain only a batch/order number. The content-review process is named 生产内容审核批次 in the existing process asset. Business numbers remain in the summary; todo/done cards and the existing table use the same taskSubject fallback (process name, node name, then 审批任务).
- Changed files: frontend/workbench/src/pages/BpmApprovalCenterPage.tsx (removed briefTitle override and documented why); docs/frontend/approval-center.md (heading contract); this record. Existing uncommitted embedded-approval/navigation changes preserved.
- Verification: npm run typecheck passed; existing bpm-approval-labels.guard.test.ts 4/4 passed; scoped git diff --check passed. Read-only contributor confirmed backend source and unchanged consumers. Chrome isolated adapter checks passed at 1440 and 390 pixels for todo/done headings, retained batch numbers, other content approval headings, table consistency, blank/missing-name fallback, missing briefs and long titles. No page errors or business mutations; desktop/mobile/long-title screenshots visually inspected.
- Verification artifacts: C:/Users/EDY/AppData/Local/Temp/zsjos-approval-title-check.py and C:/Users/EDY/AppData/Local/Temp/zsjos-approval-title-check/. Script reuses existing unified-business-approval fixture and its exact Vite module version; briefs refresh on task/view changes. Temporary verifier initially needed correction for Ant button spacing, Vite module identity and brief refresh behavior; final execution passed.
- Unrelated observation: the existing 390px fixture header search overlaps the todo tab. No layout CSS changed; browser checks switch views at desktop width then inspect each mobile state. Mobile pointer interaction with those tabs is not accepted by this check and remains outside the title fix.
- Dependencies / integration: None. No backend/API, SQL, permissions, process deployment, dependencies, build inputs, routes or assets changed. Production build not applicable to this display-only change. Existing Vite service left unchanged; no branch/commit/push operations.
- Remaining: deployment and real-account acceptance unverified. Ownership released.
