# Workstream local-exam-month-only-20261012

## Registration — 2026-10-12
- Owner: Codex /root. Environment: local (environment variable unset; environment file absent).
- Branch: main; worktree: D:/ZSJ-OS; base HEAD: c82c8605d86aef5c8f4cd328600bc7c3efc2bf90. Target branch/integration order: None.
- Goal: exam month view shows only the selected month's dates and events; retain weekday alignment with empty boundary cells, clip multi-day bars to the month, request only the month and render only necessary weeks.
- Non-goals: other calendars, backend/API contract, database, permissions, dependencies, service lifecycle, Git operations.
- Ownership: frontend/workbench/src/pages/ExamCalendarMonth.tsx, examCalendarLayout.ts and their existing tests; frontend/workbench/docs/ui-guidelines.md; new frontend/workbench/test/exam-month-only-browser.py; this record.
- Dependencies: existing exam component, calendar-navigation browser fixture and local Vite. Prior calendar-auto-size work delivered; preserve all existing edits.
- Verification: focused exam layout/component/request tests, TypeScript check, real Chrome at desktop/mobile widths with month boundaries, navigation, empty boundary positions and day interaction; scoped diff check. Browser uses isolated synthetic data, not authenticated acceptance.

## Scope update
- Include docs/api/exam-calendar.md in ownership: its 42-day presentation description conflicts with the user's current instruction. The current explicit instruction takes priority; synchronize this directly affected documentation.

## Delivery — 2026-10-12
- Branch/worktree/base unchanged. Ownership released after delivery.
- Implemented month-only query window and rendering. Empty first/last-week grid positions preserve the locale's weekday alignment without dates, entries or click targets; render only weeks intersecting the month. Multi-day bars and their accessible per-day buttons clip to the month while preserving continuation indicators and original records.
- Files: two implementation files, two existing unit-test files, two directly affected documents, new browser check, this record. Preserved existing unrelated source/documentation changes; no CSS, backend, dependency, service, database or Git operation.
- Verification: npm run typecheck PASS. Focused Vitest ExamCalendarMonth, examCalendarLayout, ExamCalendarRequests and CalendarNavigationRequests: 4 files / 34 tests PASS. Scoped git diff --check PASS.
- Real Chrome: 10 combinations at 390/1440 px across February, leap February, six-week August, December and January PASS; checks month-only dates/entries, correct weekday columns, necessary weeks, navigation, day and cross-month bar clicks, no page horizontal overflow. Evidence: C:/Users/EDY/AppData/Local/Temp/exam-month-only-d1auc83n/results.json and screenshots. Visually inspected desktop December and mobile August. Reused local Vite 5174; blocked live API and external network in browser context.
- Verification harness corrections: Vite optimized CommonJS ReactDOM exposes createRoot via default export; browser Chinese locale starts weeks on Monday while unit-test default locale starts Sunday, so browser expected columns now follow the actual configured week start. No production fix was needed for these harness failures.
- Limits: browser evidence uses actual components with isolated synthetic data, not authenticated deployed acceptance. Production build not applicable: no routes, assets, dependencies or bundling changes.
