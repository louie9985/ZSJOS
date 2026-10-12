# 今日待跟进刷新修复

## Registration — 2026-10-12 北京时间
- ID/owner: local-lead-filter-refresh-20261012 / Codex.
- Environment: local (ZSJOS_AGENT_ENV unset; /etc/zsjos/agent-environment absent).
- Branch/worktree/base: main; D:/ZSJ-OS; c82c8605d86aef5c8f4cd328600bc7c3efc2bf90. Target branch/integration: None.
- Goal: 保存跟进后表格和收件箱按服务端筛选结果更新，详情不回插已排除记录。
- Non-goals: 不改业务筛选口径、后端、权限、数据库、依赖或现有未提交改动。
- Ownership: frontend/workbench/src/pages/LeadManagementPage.tsx; frontend/workbench/src/pages/lead-management-unified.guard.test.ts; frontend/workbench/test/lead-follow-up-refresh.tsx; new frontend/workbench/test/lead-filter-refresh-browser.py; frontend/workbench/docs/follow-up-form.md; this record.
- Dependencies: prior follow-up refresh and provider review delivered/released; preserve existing provider display edit in LeadManagementPage.tsx.
- Verification: focused Vitest, typecheck, existing running Vite browser fixture with both response orders, table/inbox and deep-link cases; scoped diff check.
## Delivery — local-lead-filter-refresh-20261012 — 2026-10-12 北京时间
- Result: fixed follow-up submission refresh behavior for the management table and inbox under the server-provided “今日待跟进” filter.
- Change: silent detail hydration now updates an existing list row only; it does not reinsert a lead excluded by the refreshed list query. Follow-up refresh preserves the open detail and clears stale deep-link pinning so the filtered list remains authoritative.
- Files: frontend/workbench/src/pages/LeadManagementPage.tsx; frontend/workbench/src/pages/lead-management-unified.guard.test.ts; frontend/workbench/test/lead-follow-up-refresh.tsx; frontend/workbench/test/lead-filter-refresh-browser.py; frontend/workbench/docs/follow-up-form.md; this record.
- Verification: focused guard test 7/7 passed; npm run typecheck passed; Chrome fixture passed 8 cases covering split/inbox and table layouts, deep links, and both list/detail response orders. git diff --check passed. Synthetic transport only; live authenticated persistence remains unverified.
- Dependencies/integration: no backend, API, permission, database, dependency, branch, commit or service changes. Existing unrelated worktree changes preserved.
