# Calendar mobile viewing

## Registration — 2026-10-08 北京时间
- Environment: local (ZSJOS_AGENT_ENV unset, environment file absent).
- Owner/workstream: Codex /root; local-calendar-mobile-20261008.
- Branch/worktree/base: main; D:/ZSJ-OS; d2f836006ff07b5517bcd6d485d72df788bf3cb0. Target branch/integration order: None.
- Goal: readable mobile viewing for the five workbench calendars, navigation and day details.
- Non-goals: API/auth/permissions, database, dependencies, publication, unrelated dirty files.
- Ownership: frontend/workbench/src/components/CalendarSideNavigation.tsx; src/styles/components/calendar-side-navigation.css; src/styles/pages/media-calendar.css; src/styles/pages/exam-calendar.css; src/styles/pages/course-calendar.css; docs/ui-guidelines.md (calendar navigation section only, preserve other edits); test/calendar-navigation.tsx; new test/calendar-mobile-browser.py; this record. All abbreviated paths under frontend/workbench.
- Dependencies: existing Ant Design calendars, existing isolated synthetic transport fixture; prior calendar and follow-up work delivered/released. Other active workstream scopes excluded.
- Decision: current mobile-fix request supersedes the prior narrow-screen side-rail specification; desktop retains side rails. Mobile uses top navigation and readable horizontally scrollable calendar, day details stack vertically.
- Verification: existing focused calendar/style tests, typecheck, real Chromium at desktop and 320/390/768 touch widths, navigation/date selection/details/remote states and screenshots; scoped diff checks. Isolated local Vite 5196 and browser context, no shared service lifecycle changes.

## Delivery — 2026-10-08 11:41 北京时间
- Branch/worktree/HEAD: unchanged from registration.
- Result: mobile arrows now occupy a 44px top row; calendar uses full page width with horizontal scrolling for readable seven-column dates. Month selectors remain visible. Account information shrinks to 112px with aligned taller rows. Personal/course day details stack readable cards with full remarks and existing permission-controlled actions. Desktop retains side navigation and timeline layout.
- Changed files: all registered files; new test/calendar-mobile-browser.py. Only the calendar section of docs/ui-guidelines.md changed; preserved existing announcement documentation and all other workstreams' edits. No API, database, permissions, dependency or Git changes.
- Verification: 23/23 focused navigation/request/exam render tests passed; global styles 29/30 passed (unrelated pre-existing content-review-attachments.css:6 uses font-size:28px). Typecheck blocked by unrelated LeadDetail.tsx:276 qualificationToken missing from availableActions type (two TS2339 diagnostics). These failures were not modified. Scoped git diff --check and strict UTF-8 reads passed.
- Browser: real Chrome, isolated synthetic API fixture, all five calendars at 320/390/768/1440 passed 20 cases; verifies no page horizontal overflow, 44px mobile controls, scrollable date content, previous/next navigation and day dialogs; mobile error/retry/empty/loading and denied lead view checked. No page errors. Evidence: C:/Users/EDY/AppData/Local/Temp/calendar-mobile-8yno5kpl/results.json and screenshots. Mobile and desktop screenshots visually inspected.
- Additional details verification: actual personal/course components with long linked remarks and manage actions passed at 320/390/1440; mobile cards have no vertical clipping, links and actions visible. Evidence: C:/Users/EDY/AppData/Local/Temp/calendar-mobile-details-7wsqi_aq/. Initial optional probe clicked a course action rather than its date; rerun targeted the date badge and passed.
- Dependencies/integration impact: None. Build not required for scoped CSS/rendered hint changes; no bundling/routes/assets/dependency changes. Existing unrelated type errors would prevent a full production build. Local isolated Vite verification process stopped after checks.
- Remaining: live authenticated acceptance, physical-device Safari and deployment not performed; synthetic browser verification does not assert live account data. Ownership released.

### Final concurrent-change verification — 2026-10-08 11:45 北京时间
- Another workstream added ExamCalendarNotePanel and a responsive wrapper to ExamCalendarPage during final checks. Preserved those changes; current mobile wrapper stacks the note below the calendar. Updated this workstream's browser assertion to scope calendar errors separately from note authorization alerts.
- Reran all 20 calendar/viewport cases and mobile remote-state/denied cases against the combined current tree: passed. Current evidence: C:/Users/EDY/AppData/Local/Temp/calendar-mobile-1z4veuw0/results.json and screenshots; current exam mobile screenshot visually inspected. This supersedes earlier browser evidence for the combined exam layout. Note content/edit behavior remains owned and verified by its separate workstream.
- Scoped diff check passed; owned production-file diffs unchanged. Isolated Vite process stopped after the rerun. No additional requested work remains.
- Visual clarification: final screenshot places the concurrently added note above the calendar on mobile (rather than below as initially described); both occupy full-width rows and the calendar remains scrollable. No change to that separate workstream's ordering.
