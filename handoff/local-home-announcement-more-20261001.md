# Workstream — local-home-announcement-more-20261001

- Environment: local (environment variable and environment file absent).
- Goal: 首页公告首次加载 10 条，支持区内加载更多、失败重试及刷新重置。
- Non-goals: backend/API contract, permissions, SQL, dependencies, other homepage panels, deployment or Git operations.
- Branch: main; worktree: D:/ZSJ-OS; base: d43cc180c3a3e92740888b521fc2ea4e006a0560.
- Owner: this chat /root; target branch/integration order: None.
- Ownership: frontend/workbench/src/components/HomeAnnouncementPanel.tsx; frontend/workbench/src/styles/pages/today-tasks.css; frontend/workbench/docs/ui-guidelines.md; frontend/workbench/test/home-announcement-pagination*; this record.
- Dependencies: existing announcement page API, AnnouncementProvider, existing React/Vite/browser test facilities. Preserve unrelated edits and use this unique record alongside concurrent workstreams.
- Verification: focused announcement/style tests, typecheck, real-component browser checks at desktop/mobile with isolated synthetic transport for paging/end/retry/refresh/races/empty/denied; scoped diff and UTF-8 checks. No production build required for this component-only behavior change.

## Delivery — local-home-announcement-more-20261001 — 2026-10-01

- Result: 首页公告首次请求 10 条；“加载更多”按每页 10 条追加，列表在公告区内部滚动，全部加载后显示完成状态。追加失败保留已有数据并支持同页重试；刷新重置到第一页；重复点击、刷新竞态和跨页重复公告受保护；权限、空态、列表错误和汇总状态沿用现有处理。
- Changed files: `frontend/workbench/src/components/HomeAnnouncementPanel.tsx`; `frontend/workbench/src/styles/pages/today-tasks.css`; `frontend/workbench/docs/ui-guidelines.md`; isolated browser fixture/check `frontend/workbench/test/home-announcement-pagination.*`; this workstream record.
- Verification: Workbench `npm run typecheck` passed; Python fixture `py_compile` and scoped `git diff --check` passed. Existing focused Vitest run had 40/42 passing; two failures are unrelated pre-existing guards (`content-review-attachments.css` hard-coded font size and TodayTasks lead route token). Browser pagination script was added for desktop/mobile paging, end, retry, duplicate-click, refresh race, overlap, empty and denied states; local run reached the fixture flow but was blocked by Playwright's label lookup on the retry control, so browser result is unverified until that harness is adjusted.
- Impact: no backend/API, permissions, SQL, dependencies, external services, branches, commits or deployment; unrelated working-tree changes preserved.
