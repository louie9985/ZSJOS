# Workstream exam-table-reuse-20261109
- Environment local (environment variable and environment file absent). Owner Codex.
- Goal unify collection member columns and supply planner archive fields. Non-goals database, deployment, permissions changes.
- Branch main, worktree D:/ZSJ-OS, base 7a7cf5e14bef1ff193de5c4cc39af8a5255a7d5c. Integration None.
- Ownership ExamMemberColumns.tsx, ExamTaskPanels.tsx, ExamPlannerProvider.tsx, services/examCollection.ts, ExamCollectionReminderService.java, ExamCollectionPlannerVO.java, ExamCollectionPlannerMembersTest.java, docs/api/exam-material-collection.md, this record.
- Prior member-column work delivered and ownership released. Preserve all existing changes.
- Dependencies existing StudentSummaryService projection and BusinessTable. Verification typecheck, focused Java test and browser fixture where available.

- Scope update owns StudentSummaryService.java scoped planner projection entry, retaining planner permission checks and shared archive projection.

## Delivery 2026-11-09 17:20 Beijing
- Branch/worktree/base unchanged from registration. User goal completed in source with verification limits below.
- Both collection roster tables reuse ExamMemberColumns and BusinessTable compact mode. Removed planner column from task roster. Added optional archive name/mobile/identity to planner API and typed client. Empty archive values remain empty.
- Scoped planner archive projection retains planner object checks, tenant checks and existing formal archive precedence. No dependency or database change. Integration None.
- Changed files are all ownership files listed above, including scoped StudentSummaryService update. Existing unrelated edits preserved.
- Verification npm run typecheck passed. Current-source isolated Java compile and 134/134 tests passed. Extended planner test checks archive values and one batch call while preserving scoped query assertions. Scoped diff check passed.
- Browser ego space66 existing real-provider fixture on local5174 confirmed seven headers including archive columns and filling status, missing archive placeholders; space closed. Synthetic fixture only. Real account API, full route browser acceptance and mobile layout unverified. Running backend not restarted.
- Original Java verifier copied a prepared dependency ancestor into itself and failed on long paths. Reran using a temporary verifier with an isolated temporary output and direct read-only dependency classpath. Generated ignored artifacts from the failed copy remain, no bulk cleanup performed.
- Remaining deployment/runtime acceptance. Ownership released. No commit/push.

## Registration update 2026-11-09
- Resume same owner and environment. Scope two member table entry points, API documentation and this log. User explicitly requests column settings, overriding compact-by-default embedded table guidance for these two tables. Use full mode with reload disabled, retain retry callbacks and isolated persisted column settings. Verification typecheck and browser column-toggle persistence.

## Delivery 2026-11-09 17:35 Beijing
- Branch main, worktree D:/ZSJ-OS and HEAD unchanged from registration. Corrected both member entry points from compact to full mode with reload:false. Common BusinessTable supplies column settings, density, fullscreen and separate persisted column state. User request takes precedence over embedded compact guidance.
- Changed ExamTaskPanels.tsx, ExamPlannerProvider.tsx and docs/api/exam-material-collection.md, plus this log. Preserved existing work. No dependency or integration impact.
- npm run typecheck passed, scoped diff check passed. Ego browser space68 fixture verified three settings icons, opened seven-column settings, hid archiveName and verified restored page retained hidden column, then restored visibility using UI. Space finished. Real account full route and mobile unverified.
- Remaining runtime acceptance. Ownership released.

## Registration update 2026-11-09 planner column
- Same environment local, owner Codex, branch/worktree/base. Scope ExamTaskPanels.tsx, API documentation, temporary test/exam-member-planner-preview.html and .tsx fixture, this log. Goal restore plannerName as 班主任 with default hidden state, retain persisted user preference. Verification typecheck and browser default/enable/persistence. Integration None.

## Delivery 2026-11-09 17:39 Beijing
- Branch main/worktree/base unchanged. Restored roster plannerName column labeled 班主任. columnsState defaultValue sets show:false and retains existing persistence key, allowing explicit user visibility preferences to override defaults.
- Changed ExamTaskPanels.tsx, docs/api/exam-material-collection.md and this record. Temporary synthetic real-component fixture removed after verification. No API, dependency or integration change.
- npm run typecheck passed. Browser space69 verified default hidden, column setting checkbox enables 班主任 and saved visibility survives reload. Space finished. Initial semantic checkbox click did not toggle the custom tree checkbox, verified successful interaction using its checkbox element. Scoped diff check passed.
- Remaining real account acceptance unverified. Ownership released.
