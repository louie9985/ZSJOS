# local-exam-color-shortcuts-20261009

## Registration
- Environment: local (ZSJOS_AGENT_ENV unset; /etc/zsjos/agent-environment absent). Owner: Codex /root.
- Branch: main; worktree: D:/ZSJ-OS; base: c82c8605d86aef5c8f4cd328600bc7c3efc2bf90. Target branch/integration: None.
- Goal: user-requested start/exam/deadline color shortcuts and matching calendar legend.
- Non-goals: historical recoloring, API/database/permissions, dependencies, Git or shared-service operations.
- Ownership: frontend/workbench/src/pages/ExamCalendarPage.tsx; docs/api/exam-calendar.md; this record. Prior exam color/attachment/date work delivered; preserve existing changes.
- Dependencies: existing per-exam backgroundColor and Ant Design controls. User explicitly supplies presentation colors, not dictionary options.
- Verification: typecheck; existing focused exam tests; isolated real-browser desktop/mobile shortcut, preview, save/reopen and legend checks; scoped diff review.

## Delivery
- Added three labeled one-click color buttons with selected-state accessibility, using the same presentation palette in the calendar legend. Existing custom picker, clear behavior and backgroundColor save contract retained. API documentation updated; pre-existing changes preserved.
- Verification: npm run typecheck PASS; 36 existing focused color/page/month/request tests PASS; real Chrome isolated exam-color fixture PASS at 1440px and 390px for all three shortcut values, preview/legend RGB, viewport fit, save, reload and edit restoration. Screenshots visually inspected at C:/Users/EDY/AppData/Local/Temp/exam-shortcuts-final-wd1kvcgt. Scoped git diff --check PASS.
- Initial browser probes used exact icon-inclusive button name and a Drawer selector for the day-detail Modal; corrected verification locators, no product workaround.
- Limits: isolated browser transport, no authenticated live writes or deployment verification; no historical recoloring. No dependencies, database, shared-service or Git operations. Ownership released.

## Follow-up — 2026-10-10
- Reuse registration context and ownership above; environment remains local (variable unset, environment file absent), branch/main and base unchanged. Scope: rename the shared shortcut/legend label to 报名开始时间 and synchronize API documentation. Preserve subsequent unrelated page changes. Verification: scoped source/content and whitespace checks for this text-only correction.
- Delivered: shortcut button and legend consume the renamed shared label; #00D0FF unchanged. Documentation synchronized. Source/content and git diff --check PASS; no layout/logic changes or runtime checks. Ownership released.
