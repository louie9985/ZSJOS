# Follow-up refresh

## Registration — 2026-10-08 北京时间
- Environment: local (environment variable unset; environment file absent).
- Owner/workstream: Codex /root; local-follow-up-refresh-20261008.
- Branch/worktree/base: main; D:/ZSJ-OS; d2f836006ff07b5517bcd6d485d72df788bf3cb0. Integration: None.
- Goal: refresh follow-up history/count on Lead Management manual refresh and after calendar submissions, preserving unsaved forms.
- Non-goals: backend/API contracts, dependencies, permissions, database, unrelated changes, Git operations.
- Ownership: LeadManagementPage.tsx, LeadFollowUpCalendarPage.tsx, LeadDetail.tsx, LeadFollowUpPanel.tsx; new services/leadFollowUpEvents.ts; test/lead-follow-up-refresh*; docs/follow-up-form.md; this record (all frontend paths under frontend/workbench).
- Dependencies: existing page retention, typed APIs, event pattern and browser fixture facilities. Other active sorting/repurchase scopes are excluded, especially shared api.ts.
- Verification: focused existing tests, typecheck, real-browser regression using synthetic transport fixtures, scoped diff checks. No shared service lifecycle changes.

## Registration update
- Add ownership: frontend/workbench/src/pages/lead-management-unified.guard.test.ts; synchronize existing source guard with composed external/internal refresh version.

## Delivery — 2026-10-08 11:12 北京时间
- Branch/worktree/HEAD: unchanged from registration.
- Goal/result: fixed missing follow-up refresh after calendar submission and both Lead Management refresh entrypoints.
- Decisions: calendar success emits a lead-scoped in-window notification; mounted management consumers re-fetch the list and matching detail/history. Manual refresh shares a single handler. Preserve selected lead and mounted form; combine monotonic internal/external revisions for history, count and overview latest activity. No auth/API/database changes.
- Changed files: src/pages/LeadManagementPage.tsx; src/pages/LeadFollowUpCalendarPage.tsx; src/components/LeadDetail.tsx; src/services/leadFollowUpEvents.ts; src/pages/lead-management-unified.guard.test.ts; test/lead-follow-up-refresh.html, .tsx, -browser.py; docs/follow-up-form.md (all under frontend/workbench); this record. LeadFollowUpPanel final content unchanged.
- Verification: npm run typecheck passed; 23/23 focused Vitest tests passed; real Chrome isolated fixture passed manual refresh, actual calendar form submission across retained pages, record/count update, unsaved draft preservation, error/retry/empty state, table toolbar refresh and retained drawer history/count; no browser page errors. Scoped git diff --check and UTF-8 checks passed.
- Verification scope: synthetic transport responses, no live account or persistence writes. Real deployed environment remains unverified; automatic notification is within the current workbench window, other windows use manual refresh. No visual/layout/build inputs changed; production build not required for this scoped interaction fix.
- Dependencies/integration: existing facilities only; None. Preserved other active workstream changes. Ownership released.
- Remaining work: deploy through the normal authorized release flow and confirm with real accounts; no repository implementation work outstanding.
