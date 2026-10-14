# 完整账号审批快照刷新

## Registration — 2026-10-14
- Environment: local (ZSJOS_AGENT_ENV unset; environment file absent).
- Workstream: local-content-review-full-profile-20261014; owner: /root.
- Branch: main; worktree: D:/ZSJ-OS; base: 7ddd776265db39a8bdec7238867271baf17bbcc5; integration: None.
- Goal: current full account preview and submit-time immutable full snapshots; name read-only.
- Non-goals: historical backfill, migrations, permissions grants, deployment, service restart.
- Ownership: contentreview ContentReviewAccountProfile, ContentReviewBatchService, ContentReviewController, new preview VO and focused tests; Workbench ContentApprovalDraft, new preview component/tests, ContentReviewBatchPage, MediaStudentsPage, materialApi and focused tests; docs/api/content-review-batch.md and docs/operations/content-review-account-profile.md; this record.
- Dependencies: existing account authorization and System user API; preserve concurrent changes. Prior profile work delivered.
- Verification: focused Maven and Vitest, typecheck, desktop/mobile browser fixture if available; authenticated acceptance reported separately.
- Decision: user confirmed all fields including name synchronize, no historical repair; supersedes old editable-name/inherited-profile contract.

## Delivery — 2026-10-14
- Result: full current account snapshot at submission; current name/platform account/platform/stage/status/responsibility and profile fields replace stale draft values. Historical rounds unchanged; no backfill or migration.
- API/UI: submit-authorized account-profile-preview endpoint; read-only current preview on new/edit/resubmit; loading/error blocks validation, retry supported; account name no longer editable.
- Verification: focused Maven reports 44 service + 3 profile tests passed; Workbench focused Vitest 8 passed; typecheck passed. Isolated Chromium fixture verified preview failure blocks submit, retry succeeds, desktop 1440/mobile 390 layout and inspected screenshots under %TEMP%/profile-preview-*.png.
- Limits: isolated component browser evidence, not authenticated full approval flow; no deployment/restart, database modification, commit or push. Backend must deploy before frontend.
- Documentation: API and operations contracts updated. Concurrent work preserved. Ownership released.
