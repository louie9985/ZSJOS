# 首页公告已读同步

- Workstream: local-notice-read-sync-20261023; environment: local (environment variable and environment file absent).
- Goal: synchronize acknowledged notice reads into retained homepage without resetting pagination or scroll.
- Non-goals: backend, SQL, permissions, menu cache settings, deployment.
- Branch: main; worktree: D:\ZSJ-OS; base: 404387de566fcb9abf4b264f0e005bf0566ea209.
- Owner: Codex; dependencies: existing notice read API and session identity; integration: None.
- Ownership: AnnouncementProvider.tsx, HomeAnnouncementPanel.tsx, AnnouncementCenterPage.tsx; narrow AnnouncementProvider key in main.tsx; notice paragraph in docs/ui-guidelines.md; test/home-announcement-integration.tsx; test/home-announcement-read-sync-browser.py; this record. Existing unrelated edits are preserved.
- Verification: focused existing Vitest tests, typecheck, synthetic Chromium integration at desktop/mobile widths covering retained and unretained pages, pagination/scroll, failed reads, summary retry, stale responses and session reset.

## Delivery

- Added session-only confirmed read IDs; acknowledgement updates homepage presentation without replacing loaded pages. Unread counts remain server-owned. Provider is keyed by authentication platform, tenant and user ID; disabling clears confirmations.
- Updated homepage behavior documentation and extended existing integration fixture to retain the real homepage using RetainedReviewRoute.
- PASS: npm run typecheck (frontend/workbench).
- PASS: announcement.guard.test.ts, noticeManagement.test.ts, noticeAttachment.test.ts: 26 tests.
- PASS: navigationUnread.test.ts: 5 tests; todayTasks.test.ts: 9 tests. One unrelated failure in existing submitter-assistance navigation source guard, expecting `state: { leadId: task.bizId }`; neither that implementation nor test was changed.
- PASS: python test/home-announcement-read-sync-browser.py, real Chrome at 1440 and 390 widths: cached page pagination/scroll without refetch, repeated reads, write failure/retry, summary failure/retry, stale page response, identity and permission reset, uncached navigation, hidden body does not acknowledge. Synthetic transport only; no real account acceptance or deployment.
- PASS: scoped git diff --check. No backend/SQL/dependency changes. Ownership released on delivery.
