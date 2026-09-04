# Workstream: feedback-image-preview

- Goal: Add direct image thumbnails with click-to-enlarge preview to demand/feedback forms and drawers.
- Non-goals: No API, permission, upload contract, or database changes.
- Branch: main
- Worktree: D:\ZSJ-OS
- Base commit: 554d50a690b93b276aae5adac41804646f7ba747
- Target branch: main
- Ownership scope: frontend/workbench/src/components/FeedbackDynamicForm.tsx; frontend/workbench/src/pages/FeedbackPage.tsx; frontend/workbench/src/styles/pages/feedback.css; frontend/admin/src/views/zsjos/feedback/components/FeedbackListPage.vue
- Owner: /root
- Dependencies: Existing Ant Design Image and Element Plus el-image preview primitives.
- Integration order: None; serialized on local main.
- Verification plan: Workbench focused tests, typecheck, build; admin typecheck/lint/build where available.

## Delivery 2026-09-04 02:02:08 +08:00

- Branch: main
- Worktree: D:\ZSJ-OS
- HEAD commit: 554d50a690b93b276aae5adac41804646f7ba747
- User goal: Show feedback/demand image attachments as direct thumbnails with click-to-enlarge preview instead of download links.
- Key decisions: Reused Ant Design Image preview in Workbench and Element Plus el-image preview in Admin; image detection uses MIME type or common image filename extension; non-image attachments remain links.
- Result: Implemented thumbnail rendering in dynamic forms, feedback detail drawer sections, admin drawer attachments, and admin reply/completion upload lists.
- Changed files: frontend/admin/src/views/zsjos/feedback/components/FeedbackListPage.vue; frontend/workbench/src/components/FeedbackDynamicForm.tsx; frontend/workbench/src/components/FeedbackDynamicForm.test.ts; frontend/workbench/src/pages/FeedbackPage.tsx; frontend/workbench/src/styles/pages/feedback.css; handoff/feedback-image-preview.md.
- Verification: Focused Workbench FeedbackDynamicForm tests passed (3/3); Workbench typecheck process failed with Node heap out-of-memory; Admin vue-tsc process produced no output before session ended and is unverified; git diff --check passed with line-ending warnings only.
- Dependency/integration impact: None; no dependency, API, permission, or database changes.
- Remaining work: Browser verification and full builds remain unverified.

## Delivery 2026-09-04 02:05:00 +08:00

- Branch: main
- Worktree: D:\ZSJ-OS
- HEAD commit: 554d50a690b93b276aae5adac41804646f7ba747
- User goal: Ensure the Admin frontend also shows feedback image attachments as thumbnails with click-to-enlarge preview.
- Key decisions: Admin detail field attachments now reuse AttachmentLinks, and Element Plus el-image uses previewSrcList for direct image preview; ordinary files remain links.
- Result: Admin drawer submission fields, result/reply attachments, and reply/completion upload lists all support image thumbnails and enlarged preview.
- Changed files: frontend/admin/src/views/zsjos/feedback/components/FeedbackListPage.vue; handoff/feedback-image-preview.md.
- Verification: Admin vue-tsc attempted but exited with Node heap out-of-memory; git diff --check passed with line-ending warnings only.
- Dependency/integration impact: None.
- Remaining work: Browser verification and full Admin build remain unverified.

## Delivery 2026-09-04 02:08:00 +08:00

- Branch: main
- Worktree: D:\ZSJ-OS
- HEAD commit: 554d50a690b93b276aae5adac41804646f7ba747
- User goal: Fix Admin bottom-right WebSocket notification click navigation.
- Key decision: Route notification fallback to the existing Admin route `/user/notify-message?messageId=...`; `/messages/all` was not registered in the Admin router.
- Result: Clicking a WebSocket notification with an actionable message now opens the corresponding message detail page; Lead business deep links remain unchanged.
- Changed files: frontend/admin/src/layout/components/Message/src/Message.vue; handoff/feedback-image-preview.md.
- Verification: Targeted Admin ESLint passed; git diff --check passed with line-ending warnings only; browser verification not run.
- Dependency/integration impact: None.
- Remaining work: Real WebSocket click-through remains to be verified in a running Admin environment.

## Delivery 2026-09-04 02:12:00 +08:00

- Branch: main
- Worktree: D:\ZSJ-OS
- HEAD commit: 554d50a690b93b276aae5adac41804646f7ba747
- User goal: Clicking a feedback WebSocket toast must open the Admin demand/feedback page, refresh it, and show the matching feedback drawer.
- Key decisions: Use the existing `bizType=feedback` and feedback detail API to resolve `feedbackType`; route to `/feedback-management/requirements`, `/feedback-management/bugs`, or `/feedback-management/support` with `feedbackId`. The shared Admin list component consumes the query, reloads the list, and opens the drawer.
- Result: Feedback notifications now deep-link to the correct Admin feedback subpage and drawer; generic notifications retain existing behavior.
- Changed files: frontend/admin/src/layout/components/Message/src/Message.vue; frontend/admin/src/views/zsjos/feedback/components/FeedbackListPage.vue; handoff/feedback-image-preview.md.
- Verification: Targeted Admin ESLint passed; browser/WebSocket runtime verification not run.
- Dependency/integration impact: None; uses existing feedback APIs and routes.
- Remaining work: Verify with a live feedback notification in Admin.
