# Workstream: feedback-file-url-refresh

- Goal: Refresh feedback attachment access URLs when reading details.
- Non-goals: Database, upload, COS configuration, permissions, frontend layout.
- Branch: main
- Worktree: D:\ZSJ-OS
- Base commit: 037496d1f2895e3f83319cb07185f9513b664f05
- Target branch: main
- Ownership scope: FeedbackDynamicFormService.java, FeedbackServiceImpl.java and their tests under backend/yudao-module-zsjos; docs/api/feedback-management.md; this handoff file.
- Owner: Codex feedback-file-url-refresh
- Dependencies: Existing Infra FileApi; no new dependencies.
- Integration order: Serialized changes in current worktree; no branch operations.
- Verification plan: Focused feedback tests and module compilation; inspect Vue and React consumers; attempt available runtime verification.
- Status: active
- Scope addition: FeedbackServiceContextTest.java, to register the existing PartnerMapper dependency missing from its test context.
- Batch optimization scope: FeedbackFileUrls.java and FeedbackFileUrlsTest.java in the same feedback service/test packages; existing service files, tests and API documentation. Owner and branch unchanged. Verification adds both frontend builds and focused batch/fallback tests.

## Delivery: 2026-09-06 15:54:00 Beijing time

- Branch: main
- Worktree: D:\ZSJ-OS
- HEAD commit: 037496d1f2895e3f83319cb07185f9513b664f05
- User goal: Fix feedback images failing after private COS upload signatures expire.
- Key decisions: Generate URLs through the existing Infra FileApi by file ID at detail read time; preserve stored snapshots and historical metadata. Resolve only image/upload fields. Re-sign survey, reply and result attachments as well. Keep individual unavailable files as metadata with no URL. No schema, upload, permission, dependency or frontend changes.
- Execution result: Implemented response-only URL refresh and regression coverage for repeated reads, historical metadata, unchanged snapshots, empty values, missing files and signing failures. Fixed the existing context test's missing PartnerMapper mock after the first run identified it.
- Changed files: backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/feedback/FeedbackDynamicFormService.java; FeedbackServiceImpl.java in the same directory; backend/yudao-module-zsjos/src/test/java/cn/iocoder/yudao/module/zsjos/service/feedback/FeedbackDynamicFormServiceTest.java; FeedbackServiceImplTest.java and FeedbackServiceContextTest.java in the same test directory; docs/api/feedback-management.md; handoff/feedback-file-url-refresh.md.
- Verification evidence: Maven reactor compilation and `mvn -f backend/pom.xml -pl yudao-module-zsjos -am test -Dtest=Feedback*Test -Dsurefire.failIfNoSpecifiedTests=false -DfailIfNoTests=false -q` passed on rerun. Workbench `npm test -- src/services/feedbackApi.test.ts src/pages/feedback-route.guard.test.ts`: 4 tests passed. Vue FeedbackListPage and React FeedbackPage consumers inspected for URL and missing-URL compatibility. Scoped git diff --check passed. Browser visit to local workbench feedback reached login page.
- Dependency or integration impact: None; current branch preserved; no service restart, commits, database writes or unrelated file edits.
- Remaining work: Authenticated real-detail/COS image verification remains unverified because the inspected browser session is unauthenticated. Running backend code reload was not confirmed. Frontend production builds and desktop/mobile image rendering were not run; frontend files are unchanged.
- Status: implementation and automated verification complete; live authenticated verification outstanding.

## Delivery: 2026-09-06 23:54:00 Beijing time

- Branch: main
- Worktree: D:\ZSJ-OS
- HEAD commit: 037496d1f2895e3f83319cb07185f9513b664f05
- User goal: Execute the approved batch-signing optimization with per-file fallback.
- Key decisions: Use a feedback-private resolver for the shared batch/fallback policy. Deduplicate all image/upload IDs within each form. Merge result and reply IDs for one batch, reuse metadata, and preserve attachment ordering. No cross-request cache or protocol change.
- Execution result: Normal signing uses the existing Infra batch API; a failed batch retries each distinct ID once and isolates individual failures. Metadata lookup remains per distinct result/reply file because no batch metadata public API exists.
- Changed files: FeedbackDynamicFormService.java, FeedbackServiceImpl.java, new FeedbackFileUrls.java in the feedback service package; FeedbackDynamicFormServiceTest.java, FeedbackServiceImplTest.java, new FeedbackFileUrlsTest.java in the corresponding test package; docs/api/feedback-management.md; this handoff file.
- Verification evidence: Maven reactor test command with -Dtest=Feedback*Test passed; feedback service package reports 37 tests, zero failures/errors. Workbench feedback tests: 4 passed. Workbench npx tsc --noEmit and npx vite build passed. Admin pnpm build:prod passed with CSS minification, environment and bundle-size warnings. Scoped git diff --check under repository Git settings passed.
- Dependency or integration impact: No new external dependencies, database changes, branch operations, service restarts or frontend source edits. The new helper is package-private and adds no Spring wiring.
- Remaining work: Authenticated live COS image verification and running-backend reload confirmation remain outstanding. Desktop/mobile real-image checks were not performed. No deployment or commit performed.
- Status: batch optimization implemented and automated checks passed.
