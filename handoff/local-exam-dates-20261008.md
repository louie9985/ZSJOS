# 考期日期校验与过期草稿改期

## Registration — 2026-10-08
- Environment: local (ZSJOS_AGENT_ENV and /etc/zsjos/agent-environment absent).
- ID/owner: local-exam-dates-20261008 / current Codex chat.
- Branch/worktree/base: main / D:/ZSJ-OS / d2f836006ff07b5517bcd6d485d72df788bf3cb0.
- Goal: reject ended input dates, allow expired drafts to be rescheduled, align UI with Shanghai business dates.
- Non-goals: database repairs, schema/permission changes, deployment, announcements, new dependencies.
- Ownership: ExamScheduleService.java, ZsjosErrorCodeConstants.java, examcalendar date-related tests; frontend/workbench/src/pages/ExamCalendarPage.tsx and examCalendarDates.ts/.test.ts, date-focused browser fixtures under frontend/workbench/test/exam-dates*; docs/api/exam-calendar.md; this record. Preserve all existing edits. Prior attachment/color work delivered and released ownership.
- Dependencies: approved user plan overrides old documentation prohibiting expired draft edits. Existing attachment/color/note changes remain intact.
- Verification: focused Maven tests, Vitest and typecheck, isolated desktop/mobile browser fixtures including timezones and retry. Live backend if available; report unavailable verification explicitly.
- Target branch/integration order: None.

## Delivery — 2026-10-08 北京时间
- Implemented approved rule change: create/update validate submitted end against Shanghai today; expired DRAFT can be rescheduled; publish/notification preview still reject expired records. New invalid-past-input code 1900018014; existing publication code 1900018007 retained with actionable wording. No interface/schema/grant changes.
- Workbench uses explicit Shanghai dates for initial month/today/new-record defaults, picker/form/submission validation and expired publish controls; existing 250ms page clock refresh also updates midnight controls. Multi-day start may precede today. Expired edit retains original date. Publish retry preserves saved ID and reports draft saved.
- Preserved existing uncommitted color, note and attachment changes. Updated docs/api/exam-calendar.md with approved contract override and error semantics.
- Backend: 36 focused tests passed (service, reedit, color, attachment lifecycle, controller permission). Service tests made deterministic using fixed business clock; final rerun of all 19 service tests passed.
- Frontend: 18 focused tests passed; final 11 date/page tests and typecheck passed after final form adjustment. Scoped diff and new-file UTF-8/whitespace checks passed. No dependency/bundling/route changes, production build not required.
- Browser: real headless Chrome with isolated synthetic API transport passed 1440px Los Angeles and 390px Shanghai expired-draft rescue, Shanghai midnight disable, saved-ID publication retry, ongoing multi-day edit/publish, past-month default and midnight submit rejection. Screenshots retained at C:/Users/EDY/AppData/Local/Temp/exam-dates-browser-7i3ircgm; equivalent prior desktop/mobile error-layout captures visually reviewed. Initial browser failures were fixture locator mismatches (icon-inclusive button names and previous-month label), corrected without product workarounds.
- Live integration remains unverified: local 48080 is reachable but this task did not deploy/restart or establish that it runs the changed classes; no authenticated live write was made. Browser fixture results are not deployment acceptance. No database migration or historical data rewrite; no commit/push.
- Ownership released. Branch/worktree unchanged; integration None.
