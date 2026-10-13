# Calendar day details

## Registration — 2026-10-13 Beijing time
- Environment: local; branch main; worktree D:/ZSJ-OS; base 45f1edbe24b316b5f5924416c4fc18977658ff5f.
- Owner: current Codex chat; workstream local-calendar-day-details-20261013.
- Goal: full day-card titles/times in Personal and Course calendars; click for creator/details; common attachment/link rendering.
- Non-goals: permissions, SQL, notification semantics, Admin UI changes, unrelated concurrent work.
- Ownership: Workbench PersonalCalendarPage/CourseCalendarPage, new CalendarDayTimeline component/styles/tests/browser fixture; course attachment browser selector adaptation; api.ts additive response fields; backend calendar creator projection service, CourseCalendarEventService/RespVO, PersonalCalendarEventService/RespVO and focused projection tests; docs/api/calendar-day-details.md; this record.
- Dependencies: released course-attachments work, existing AttachmentCard/LinkedText, System AdminUserApi, existing calendar read authorization. Preserve all current color/attachment edits; avoid shared ui-guidelines edits owned by another workstream.
- Verification: frontend types and focused logic/browser tests at desktop/mobile, Java creator projection and calendar regression tests; additive field compatibility for Admin; scoped diff.
- Target branch/integration order: None.

## Coordination update — 2026-10-13 Beijing time
- User explicitly authorized messaging the concurrent calendar chat; sent scoped coordination to 01a13400-0242-7b83-865c-9cd23de1a087. Its handoff now confirms shared page toolbar/query/palette writes complete and stable, preserving this detail work. Do not modify its independent grid/palette files.
- User clarified all existing-record operations (edit/delete/notify) belong only in the expanded detail panel; removed month-card actions as well.
- Scope clarification: creator tests in service/calendar/CalendarCreatorNamesTest.java, frontend fixture and selectors in test/calendar-day-details* and test/course-attachments-browser.py. No production role or source choices altered.

- Documentation ownership update: concurrent color/view workstream completed and released ownership at 15:24 Beijing time; its ui-guidelines detail paragraph already synchronized. This workstream corrects the adjacent obsolete month-action sentence and adds the course API documentation link, preserving all view/palette content.

## Delivery — 2026-10-13 15:29:09 Beijing time
- Branch/worktree/HEAD: main, D:/ZSJ-OS, 45f1edbe24b316b5f5924416c4fc18977658ff5f unchanged.
- User goal: full names/times for all related records on a selected day; click for creator/details; common attachment/link components; edit/delete/notify only within details.
- Result: shared adaptive desktop timeline and mobile list with full cross-day times; controlled desktop Popover/mobile Modal; no month-card or single-day-card edit/delete/notify controls. Existing role/object/read-scope authorization preserved. Course files load only inside selected-course details through existing CourseDayAttachments/AttachmentCard; descriptions use LinkedText. Server batches current creator names from valid audit identities; missing creators never use owner/current-user fallback.
- Changed files: frontend/workbench/src/components/CalendarDayTimeline.tsx and .test.ts; styles/components/calendar-day-timeline.css; pages/CourseCalendarPage.tsx and PersonalCalendarPage.tsx; services/api.ts additive creatorName; test/calendar-day-details.tsx/html/browser.py and course-attachments-browser.py selector adaptation. Backend service/calendar/CalendarCreatorNames.java and test CalendarCreatorNamesTest.java; CourseCalendarEventService and CourseCalendarRespVO; PersonalCalendarEventService and PersonalCalendarEventRespVO. docs/api/calendar-day-details.md, docs/api/course-calendar.md, frontend/workbench/docs/ui-guidelines.md adjacent stale action sentence; this record.
- Verification: initial focused Maven suite 25 tests passed (3 creator, 7 personal, 7 course version, 6 attachment service, 2 attachment permission); final creator suite 4 tests passed after adding owner/creator distinction. Logs: C:/Users/EDY/AppData/Local/Temp/calendar-day-backend.log and calendar-day-creator-final2.log. Frontend focused Vitest 4 passed; final npm run typecheck passed. Intermediate checks were disrupted by unrelated concurrent ContentReviewStudentDetail type edits / shared HRM build output; no unrelated repair, checks succeeded after inputs stabilized.
- Browser: Chrome 390/1440 x course/personal x allowed/read-only passed full long names, 5-minute overlap layout, full time, creator, link component, no inline actions, detail edit, course notification-panel entry and course delete with refreshed list. Screenshots C:/Users/EDY/AppData/Local/Temp/calendar-day-details-363dns13. Course attachment regression passed 390/1440 upload-on-save, failed-upload retry, preserved refs, image preview, download, edit removal and query-only read; screenshots C:/Users/EDY/AppData/Local/Temp/course-attachments-4stow6lv. Desktop/mobile visual inspection complete. Popover placement changed to bottom to stay within viewport on full-width cards.
- Consumer compatibility: Admin calendar API response additions are optional and do not rename/remove existing fields; source inspection confirms its consumers still read original fields. No Admin interaction change or authenticated Admin browser acceptance claimed.
- Documentation links and scoped diff checks passed. No schema/database writes, dependencies, role grants, runtime restart, real notification sending, branch operation or commit.
- Coordination: preserved completed calendar color/view work and course attachment implementation; user authorized coordination message; shared pages verified after their stable checkpoint. Dedicated localhost5199 browser fixture service is stopped after checks.
- Remaining work: deploy frontend and backend/restart backend for live creatorName responses; real authenticated environment acceptance not performed. Fixtures and mocks do not prove deployed storage/notification delivery.
- Status: completed; ownership released.
