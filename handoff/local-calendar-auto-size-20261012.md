# Workstream local-calendar-auto-size-20261012

## Registration — 2026-10-12 10:46 北京时间
- Owner: Codex /root. Environment: local (ZSJOS_AGENT_ENV unset; /etc/zsjos/agent-environment absent).
- Branch: main; worktree: D:/ZSJ-OS; base HEAD: c82c8605d86aef5c8f4cd328600bc7c3efc2bf90. Target branch/integration order: None.
- Goal: personal/course month cells show every schedule, fit date-column width, wrap full titles and grow with content; regress existing exam layout.
- Non-goals: API, permissions, database, interval account calendar, day-detail time-axis redesign, dependency or service lifecycle changes.
- Ownership: frontend/workbench/src/pages/{PersonalCalendarPage,CourseCalendarPage}.tsx; src/styles/pages/{course-calendar,media-calendar}.css; src/styles/components/calendar-side-navigation.css; docs/ui-guidelines.md calendar section; test/calendar-navigation.tsx; new test/calendar-auto-size-browser.py; this record. Relative paths above are under frontend/workbench.
- Dependencies: existing CalendarSideNavigation, Ant Design Calendar and isolated calendar-navigation fixture. Preserve all prior and concurrent unrelated changes.
- Verification: typecheck; focused calendar tests; real Chrome fixture at desktop/mobile widths with dense long-title days, all rows visible, height growth, no title clipping or horizontal page overflow, detail opening; existing mobile regression. No authenticated runtime acceptance claimed.

## Scope update — 2026-10-12 10:49 北京时间
- User additionally requests exam-like compact cards: remove status dots and redundant space. Same ownership and verification; remove duplicated course date within cell content.

## Delivery — 2026-10-12 10:54 北京时间
- Context: branch/worktree/HEAD unchanged from registration. Owner: Codex /root.
- Result: personal/course month cells render every event; no three-item cap or “另有” collapse. Titles wrap at date-column width, including long unbroken Latin text. Removed Badge dots, duplicate course date and empty action-space gap. Compact title size/padding follow exam cards. Date cells align to the top; content height and page grow with agenda length.
- Changed files: all nine registered source/style/documentation/fixture/check/record files. No change to exam source, follow-up count calendar or account interval timeline.
- Verification: npm run typecheck PASS; focused Vitest 4 files / 26 tests PASS (course-calendar, CalendarNavigationRequests, ExamCalendarMonth, CalendarSideNavigation); git diff --check PASS. Chrome dense-layout 16 scenarios PASS at 320/390/768/1440, personal/course, with/without actions, one vs eight same-day long titles; assertions cover all entries, full title bounds, date-column width, increasing height, aligned date headings, no page horizontal overflow, no badges, detail opening. Evidence: C:/Users/EDY/AppData/Local/Temp/calendar-auto-size-5t4816zp. Inspected desktop personal and mobile course screenshots. Existing five-calendar mobile regression 20 combinations plus remote-state/denied checks PASS on final CSS; evidence: C:/Users/EDY/AppData/Local/Temp/calendar-mobile-_dwhq6nx.
- Browser finding/fix: Ant Design full-calendar selector initially retained an 80px content height; corrected scoped specificity. Screenshot inspection then found neighboring date headings vertically centered; corrected date-cell vertical alignment and page sizing, then reran both browser suites successfully.
- Dependencies/integration impact: None. Reused existing local Vite at 5174 and isolated synthetic API fixtures. No service lifecycle, API, database, permission, dependency, branch, commit or publishing changes.
- Remaining work: authenticated deployment acceptance not performed; browser evidence is real Chrome against fixture data. Production build not applicable to CSS/component rendering changes without route/dependency/bundle changes.
