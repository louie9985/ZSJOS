# 公海池布局入口统一

- Environment: local (environment variable and environment file absent; fallback).
- Workstream: local-public-sea-layout-20261030; owner: Codex.
- Branch: main; worktree: D:\ZSJ-OS; base: 208abf277065706cbee45164113903eded9e6303.
- Goal: remove the redundant public-sea layout selector; retain global SettingsDrawer preference through useInboxTableLayout.
- Non-goals: API, permissions, data, dependencies, deployment, commit/push.
- Ownership: frontend/workbench/src/pages/LeadPublicSeaPoolPage.tsx; frontend/workbench/docs/business-table.md; this record.
- Dependencies: existing ThemeContext and useInboxTableLayout; no other workstream dependency.
- Target branch / integration order: None.
- Verification: existing global layout/settings guard tests, Workbench typecheck, scoped diff; browser check if available.

## Delivery
- Removed the redundant Segmented control and unused imports/context access. Shared useInboxTableLayout remains the page layout source; documented the single settings entry.
- Existing guards: 4 passed, 1 unrelated failure in global-inbox-layout.guard.test.ts:45 (unchanged LeadManagementPage AdvancedFilterToolbar source-string expectation).
- Scoped diff check passed. Browser verification unavailable in this session (no browser tool); actual settings interaction and rendered toolbar remain unverified.
- Workbench npm run typecheck passed (exit 0).
- No deployment, database operations, commit or push. Ownership released.
