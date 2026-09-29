# Withdrawal management status tabs

## Registration — 2026-09-29 (Asia/Shanghai)
- Environment: local; ZSJOS_AGENT_ENV and /etc/zsjos/agent-environment absent.
- Workstream: local-withdrawal-status-tabs-20260929; owner: Codex /root.
- Goal: replace Workbench withdrawal management status selector with server-driven tabs.
- Non-goals: personal withdrawal UI, Vue Admin UI, backend/state-machine changes, permissions, database, dependencies, branches, commits and publication.
- Branch: main; worktree: D:\ZSJ-OS; base: 5fadfb3e9be563ac4cb3e5c142308549944aa465.
- Ownership: withdrawal section of frontend/workbench/src/pages/ManagementPages.tsx; frontend/workbench/test/withdrawal-export.tsx; new frontend/workbench/test/withdrawal-status-tabs-browser.py; status-filter paragraph in docs/api/withdrawal-and-offline-payout.md; this record. Preserve existing dirty changes. Prior withdrawal-export workstream delivered and released ownership.
- Dependencies: existing finance filter catalog hook, Ant Design Tabs, BusinessTable, withdrawal request sequencing and existing browser fixture/runtime.
- Verification: scoped diff/content checks, Workbench typecheck, existing Python Playwright browser fixture checks for tabs/filter payloads/pagination/selection/export/error/retry/desktop/mobile; distinguish synthetic UI checks from live backend integration.
- Integration target/order: None.

## Delivery — 2026-09-29 18:20:55 +08:00
- Environment/branch/worktree/base: unchanged from registration; HEAD remains 5fadfb3e9be563ac4cb3e5c142308549944aa465.
- User goal: make withdrawal management statuses into tabs.
- Result: Workbench management view now renders All plus the server catalog statuses in catalog order; selecting a tab immediately queries page one, clears batch payout selections, retains keyword/advanced-filter state and supplies the selected status to export. Catalog loading/failure disables tabs and preserves retry. Existing request sequencing prevents slower prior-status responses from replacing the active results. Personal withdrawal selector remains unchanged.
- Files: frontend/workbench/src/pages/ManagementPages.tsx (withdrawal-only edits); frontend/workbench/test/withdrawal-export.tsx (isolated status test mode); frontend/workbench/test/withdrawal-status-tabs-browser.py; docs/api/withdrawal-and-offline-payout.md (status-tab behavior paragraph); this record. All pre-existing edits preserved.
- Verification passed: npm run typecheck; python frontend/workbench/test/withdrawal-status-tabs-browser.py; scoped git diff --check. Browser assertions cover service-driven labels/order, All/no status parameter, immediate filtering, page reset from page two, selection clearing, keyword retention, export status payload, stale-request protection, empty/list-error/retry, catalog failure/retry and personal selector preservation.
- Visual evidence: Chrome 1440x1000 and 390x844 screenshots inspected at C:/Users/EDY/AppData/Local/Temp/withdrawal-status-tabs-desktop.png and withdrawal-status-tabs-mobile.png; tabs are readable and narrow overflow uses the standard Ant Design menu.
- Verification limitations: browser uses the real page with isolated synthetic HTTP responses; no authenticated live-backend acceptance or deployment performed. Existing running frontend service reused. No production build required for this scoped UI change without route/dependency/asset/bundler changes.
- Dependencies/integration: None; no packages, API contracts, database, permissions, branches, commits or services changed.
- Remaining work: live environment acceptance after normal deployment, if required.
- Status: local implementation verified and delivered; ownership released.
