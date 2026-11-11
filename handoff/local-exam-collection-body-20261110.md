# Exam collection rich body

## Registration · 2026-11-10 Beijing
- ID local-exam-collection-body-20261110. Owner Codex.
- Environment local (ZSJOS_AGENT_ENV unset, no /etc/zsjos/agent-environment).
- Branch main. Worktree D:\ZSJ-OS. Base 7a7cf5e14bef1ff193de5c4cc39af8a5255a7d5c.
- Goal independent rich body above collection fields, desktop/mobile preview and public rendering.
- Scope exam collection create/detail/runtime VO, DO and services, new content formatter, Workbench dialog/detail/service, H5 collection page/API, V367 migration, directly affected API/operations docs and focused checks.
- Preserve all existing changes. Other notice implementation remains owned by its existing workstream.
- Dependencies existing editor and Infra upload, Jsoup. No new dependencies. Integration None.
- Verification focused content tests, typecheck/build, controlled MySQL migration execution if available, browser desktop/mobile.

- Scope update. Shared static document CSS and table projection live in frontend/shared and are consumed by both Vite builds without a new package, dependency or framework adapter. Desired schema core.sql is updated. Historical bootstrap and applied V363 remain intact. Local controlled database target yudao-mysql / ruoyi-vue-pro, tenant 1. Only nullable task content column and V367 version records are added.

## Delivery · 2026-11-10 10:07 Beijing
- Branch/worktree/base refer to registration. HEAD unchanged at 7a7cf5e14bef1ff193de5c4cc39af8a5255a7d5c. Owner Codex releases ownership on delivery. Integration None.
- Implemented optional independent rich body before collection fields, using existing configurable editor and notice table normalization/resize support. Original description remains plain text. Added desktop/mobile isolated iframe preview and public H5 body rendering, preserving authored table dimensions with local horizontal scrolling. Content is fixed by task creation, matching the existing creation-only flow.
- Backend uses Jsoup whitelist for stored/public content and includes nonempty body in request fingerprints while preserving legacy empty-body retry hashes. New nullable mediumtext column via V367 follows V366, without editing applied V363/bootstrap. Desired core schema includes the column.
- Changed files in this task: ExamCollectionVO.java, ExamCollectionPublicVO.java, ExamCollectionTaskDO.java, ExamCollectionTaskService.java, ExamCollectionPublicService.java, new ExamCollectionContent.java and its focused test, ExamCollectionTaskServiceTest.java, Workbench examCollection service and ExamCollectionDialogs/ExamCollectionBodyEditor/ExamCollectionBodyPreview/ExamCollectionPage, H5 examCollection API/page, shared examCollectionBody.ts and CSS, V367, schema/core.sql, test_exam_collection_body.py, migration README and API/operations docs. Pre-existing changes in these files remain preserved.
- Verification: 9 focused Java tests passed (2 content, 1 task, 6 public), Workbench typecheck/build and H5 build passed. Scoped diff check passed. Existing build chunk-size warnings remain. Vitest examCollection filter has no matching unit tests.
- MySQL 8.4.11 scoped fixtures passed initial/repeat/missing-prerequisite/partial/ledger-failure/recovery and persisted Chinese content HEX. Fixtures retained under verify_exam_body_afbc8b7b. Local ruoyi-vue-pro inspected at V366, migration applied to V367, nullable utf8mb4 mediumtext structure and both ledgers verified, existing task count stayed 2. No business row updates.
- Browser: actual logged-in Workbench create flow loaded editor, content input reached iframe source, switching preview measured 1352px and 375px. Screenshot attempt blocked because Ego window was minimized. Browser task 71 finished. Ego reported an available update.
- Remaining verification: visual screenshots at desktop/mobile widths and real create/share HTTP round trip after backend runtime update. Existing process was not restarted. No production deployment or full fresh/upgrade-chain acceptance was performed. Scoped migration checks cover this change.
