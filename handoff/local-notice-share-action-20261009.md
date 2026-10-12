# Workstream local-notice-share-action-20261009

## Registration
- Owner: Codex /root; environment local (fallback); branch main; worktree D:/ZSJ-OS; base f89f6b4b35374bb2bb37b644a6668448356672ed. Target branch/integration None.
- Goal: move existing external sharing from announcement detail to each management row's action area in the two existing clients.
- Ownership: frontend/workbench/src/pages/NoticeManagementPage.tsx; frontend/workbench/src/components/NoticeManagementDetail.tsx; frontend/admin/src/views/system/notice/index.vue; frontend/admin/src/views/system/notice/NoticeDetail.vue; frontend/workbench/test/notice-share.tsx; frontend/admin/test/notice-share.ts; frontend/workbench/test/notice-share-browser.py; docs/frontend/notice-public-share.md; this record.
- Non-goals: backend/API, permission contracts/grants, database, dependencies, shared services, Git operations and unrelated changes.
- Dependencies: existing share dialogs and query/share permission checks; fetch current notice before opening sharing. Preserve other active workstream changes and use this independently owned record.
- Verification: Workbench typecheck, scoped Vue lint/compiler checks, existing real Chrome share flow at desktop/mobile with management fixtures, permission/status/error cases, scoped diff checks.

## Delivery — 2026-10-09
- Context: local/main/D:/ZSJ-OS; HEAD/base f89f6b4b35374bb2bb37b644a6668448356672ed unchanged. Target branch/integration None.
- Result: both existing management clients now show external sharing beside View in each published notice's action column. Removed detail entry; preserved query + share permissions and existing share dialogs. Fetch fresh detail before opening; failed fetch can be retried from the action, stale publication state refreshes the list.
- Changed files: all nine files in the registered ownership scope; no other task changes included. Feature guide updated to the direct entry.
- Verification: npm run typecheck PASS; pnpm exec eslint src/views/system/notice/index.vue src/views/system/notice/NoticeDetail.vue PASS; python -X utf8 -B frontend/workbench/test/notice-share-browser.py PASS with real Chrome at 1440/390 for React/Vue management actions, fresh-body preview, attachment selection, QR download, revoke/reopen, missing permissions, draft/offline, detail failure, stale status and share error/retry. Existing public H5 checks also PASS. Actual Vue templates compiled/rendered through Vite during browser checks.
- Visual evidence: management screenshots reviewed at both widths for both clients in %TEMP%/notice-public-share-browser. Existing fixed action columns remain in use; no shared layout changed.
- Verification limits: isolated synthetic transport; live authenticated backend and actual public sharing were not exercised. Production build not applicable to this entry relocation (no bundle/config/route/dependency changes).
- No backend, database, grant, dependency, shared service, branch, commit or push changes. Task-owned fixture servers stopped by the test runner. Unrelated edits preserved. Ownership released.
