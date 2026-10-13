# 公告管理操作列自适应宽度

## Registration
- ID: local-notice-action-width-20261013; owner: Codex /root; environment: local (fallback; no explicit environment override).
- Branch: main; worktree: D:/ZSJ-OS; base commit: 45f1edbe24b316b5f5924416c4fc18977658ff5f; target branch/integration order: None.
- Goal: implement user-approved comparison variant 2: size the Workbench notice management action column from visible operations, sharing one width across the current page and retaining right fixation/wrapping.
- Non-goals: no permission, API, database, Admin frontend, other business table, dependency or deployment changes.
- Ownership: frontend/workbench/src/pages/NoticeManagementPage.tsx; frontend/workbench/test/notice-action-width.html and notice-action-width.tsx and notice-action-width-browser.py; frontend/workbench/docs/business-table.md; this record.
- Dependencies: existing noticeActions/noticePermission and BusinessTable; existing concurrent source changes remain preserved. Notice recipient workstream has delivered and released ownership.
- Verification plan: Workbench typecheck; actual page in Chromium with isolated transport for query/share/manage permissions, mixed statuses, filter changes, persisted non-action widths, and desktop/mobile layouts; scoped diff checks. No shared build outputs or real account writes.

## Delivery - 2026-10-13
- Context: environment, branch, worktree, base and owner unchanged. Scope complete; ownership released.
- Result: notice action descriptors now feed both button rendering and width calculation using existing server-returned permissions and notice lifecycle rules. The current page uses its maximum required width, clamped to 80–280px before normal table space distribution; buttons wrap at the cap. Other tables and Admin are unchanged. Updated the business-table documentation.
- Verification: Workbench `npm run typecheck` PASS; `python frontend/workbench/test/notice-action-width-browser.py` PASS; scoped `git diff --check` PASS. Chromium used the actual page and isolated HTTP adapter with query/share/manage/denied states, three notice statuses, filter-driven shrinkage, detail opening, persisted ordinary column widths, and 1912/1440/390px windows. No uncaught browser errors or buttons escaping action cells. Desktop and mobile screenshots visually inspected at C:/Users/EDY/AppData/Local/Temp/notice-action-width-20261013/.
- Screenshot-matched 1558px table viewport: view action width 84.30px and share width 154.45px, with full highlight header/time visible; management action width 280px, with normal wrapping and horizontal scrolling still needed. All rows share one action width. Filtering management to offline notices shrinks to 154.45px.
- Existing notice service regression: `npm test -- src/services/noticeManagement.test.ts` PASS, 19 tests, including existing lifecycle/permission behavior.
- Limits: browser checks use synthetic records and permissions, not target-environment account acceptance. No deployment, build/release operation, database write, commit or push. Mobile retains the existing horizontally scrolling table with fixed right actions; this scope does not redesign mobile table navigation. No production build required for this page-only layout change.

## Registration update - single-line adjustment
- User explicitly confirmed removing the 280px cap and retaining all operations on one line. This supersedes the earlier cap/wrapping decision and its documentation; only this notice page is affected.
- Reactivate the existing workstream with the same environment, branch, base, owner and file scope. Preserve all earlier and unrelated local edits.
- Implementation: measure the actual rendered action group plus cell padding, sharing the current page maximum with an 80px minimum; observe size changes to follow themes/font sizes. Keep right fixation and horizontal scrolling.
- Verification update: require all action buttons to share a baseline without escaping cells at desktop/mobile widths, including larger font/density settings; retain existing permission/filter/ordinary-width coverage and run typecheck.

## Delivery - single-line adjustment
- Result: removed the 280px cap and disabled action-group wrapping. Width now comes from the rendered intrinsic group plus computed cell padding, rather than the old character-count estimate (which omitted button borders). Layout measurement and ResizeObserver follow permission/status/content and theme/font size changes. The current page still uses a shared maximum with an 80px minimum. Updated the directly affected table documentation and browser fixture/checks.
- Verification: `npm run typecheck` PASS; final `python frontend/workbench/test/notice-action-width-browser.py` PASS; scoped `git diff --check` PASS. Same real-page/synthetic-transport checks plus explicit single-line and containment assertions, live large-font/loose-density switching and restoration, and horizontal scroll restoring the full highlight column on desktop. Screenshot-matched table widths: query-only 84.30px, query/share 158px, management 300px, management large font 312px. No wrapping or escaped buttons at 1912/1440/390px. Screenshots visually inspected at C:/Users/EDY/AppData/Local/Temp/notice-action-single-line-20261013/.
- Remaining boundary: management rows still require horizontal scrolling when total columns exceed available width; this approved change removes wrapping, not the fixed-column scrolling behavior. Isolated browser verification does not establish target-account/deployment acceptance. No deployment, production build, permission change, database operation, commit or push.
- Scope complete; ownership released. Environment/branch/worktree/owner remain unchanged.
