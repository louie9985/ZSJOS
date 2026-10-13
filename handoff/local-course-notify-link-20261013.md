# Course notification deep link
## Registration — 2026-10-13 Beijing time
- Environment local; branch main; worktree D:/ZSJ-OS; base 45f1edbe24b316b5f5924416c4fc18977658ff5f; owner current Codex chat.
- Goal: course in-app and WeCom notification opens current course date/details with missing/denied/retry handling.
- Non-goals: exam notifications, historical message rewrites, real sends, role grants, remote deployment or service restart.
- Ownership: CalendarNotificationSceneProvider, WecomClickTicketService and focused tests; Workbench notifyMessageAction and tests, CourseCalendarPage, CalendarDayTimeline, new course deep-link hook/tests/fixture; standalone course-calendar-notification-config.sql; related course/day/config docs and this record; local tenant-1 course rules 657/658.
- Dependencies: existing authenticated course get/page and menu route; existing WeCom ticket login redirect. Preserve all prior/concurrent edits.
- Verification: focused Java/TS/browser checks, controlled SQL initial/replay/upgrade/conflict and local preflight/backup/postconditions; no live employee messages.
- Target branch/integration order None.

## Delivery — 2026-10-13 16:00:01 Beijing time
- Branch/worktree/HEAD unchanged from registration.
- Result: course scene now supports business_detail; in-app notification action routes by courseId, WeCom one-use ticket resolves to same authenticated route. Course page loads authoritative record, locates current month/day and opens detail; invalid, missing, forbidden and transient failures have retry. Exam actions and historical notification snapshots unchanged.
- Changed files: CalendarNotificationSceneProvider.java; WecomClickTicketService.java/Test; Workbench notifyMessageAction.ts/Test; useCourseNotificationLink.ts; CourseCalendarPage.tsx; CalendarDayTimeline.tsx; test/calendar-day-details.tsx and course-notify-link-browser.py; course-calendar-notification-config.sql; docs/api/calendar-day-details.md; docs/operations/course-calendar-notification-config.md; this record.
- Local DB: scoped preflight confirmed only tenant1 rules657/658 were untouched defaults; before/after exported C:/Users/EDY/AppData/Local/Temp/course-link-config-pgypw49c. Both actions upgraded none -> business_detail; repeat unchanged; template wording/recipients/channel preferences/roles/history untouched. Fresh/repeat/upgrade/conflict-rollback tested on controlled MySQL course_link_* fixtures. No schema/version ledger changes. Recovery requires scoped action reset after review, no sent-message recall.
- Verification: Workbench typecheck; Vitest37 tests passed. Maven WecomClickTicketServiceTest passed, log C:/Users/EDY/AppData/Local/Temp/course-notify-link-java-final.log. Test verifies one-use course route and exam no-route with no anonymous business query. Chrome390/1440 passed future-month auto detail, missing/forbidden/invalid link and retry. Admin rule UI already consumes server allowedActions and business_detail; no Admin page behavior changed. Scoped diff passed.
- Runtime boundary: no backend restart or frontend publication, no real notifications sent, no historical rewrites. New rules require deployment of new backend/frontend. WeCom external workbench-base-url/actual login and external delivery not live-verified; existing ticket infrastructure retained. Dedicated5199 fixture server stopped on completion.
- Remaining work: deploy/restart and real authenticated acceptance. Ownership released; complete source/config delivery.
