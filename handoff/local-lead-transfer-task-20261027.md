# Sales transfer qualification task cleanup

- Environment: local (fallback); workstream: local-lead-transfer-task-20261027; owner: Codex.
- Branch: main; worktree: D:\ZSJ-OS; base: 8279212fb97129ba6b006dbb0452ad843dfde4b5; integration: None.
- Goal: close the previous qualification task before a normal owner transfer creates a new round.
- Non-goals: historical data repair, deployment, permissions, schema changes, Git publication.
- Ownership: LeadDispatchServiceImpl.java and LeadDispatchServiceImplTest.java under backend/yudao-module-zsjos; the transfer lifecycle paragraph in docs/api/zsjos-lead-submission-dispatch.md; this record. Preserve existing documentation edits from delivered workstreams.
- Dependencies: existing lifecycle cancellation and business task pending-only updates; no new dependencies or contracts.
- Verification: focused dispatch/lifecycle tests, Maven compilation, scoped diff check. No API shape or frontend changes.

## Delivery

- Common transfer now cancels the old qualification round before creating the recipient's round. Existing first-follow-up replacement and submitted-only qualification creation remain intact.
- Added regression coverage for owner transfer, old-round cancellation ordering, new owner and first-follow-up replacement. Updated the transfer lifecycle contract without overwriting previous documentation changes.
- Maven reactor compilation and LeadDispatchServiceImplTest / LeadLifecycleTaskServiceTest passed: 32 tests, zero failures/errors. Evidence: %TEMP%/zsjos-transfer-task-tests.log. Diff whitespace check passed.
- Initial PowerShell startup hit host memory exhaustion; a no-profile invocation succeeded, and Maven completed with a 768 MB heap limit.
- No live API verification, deployment, database/history repair, grants or Git publication performed. Existing stale tasks remain outside this change. Ownership released.
