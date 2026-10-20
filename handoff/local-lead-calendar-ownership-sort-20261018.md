# Workstream local-lead-calendar-ownership-sort-20261018

- Environment: local (fallback); branch: main; worktree: D:/ZSJ-OS; base: d29fcd5f888ceca4a1980720b1eb0fbf6a1cbfc2.
- Owner: Codex current chat; status: active; target branch / integration order: None.
- Goal: add 归属开始时间 (ownershipStartedAt) sorting before pagination, asc/desc with missing dates last.
- Non-goals: schema/data changes, public-sea responsibility changes, shared service restarts, deployment, commits.
- Ownership: LeadCalendarQueryReqVO.java, LeadCalendarMapper.java, LeadCalendarSearchSql.java, LeadCalendarService.java, LeadCalendarServiceTest.java; frontend/workbench/src/services/leadCalendar.ts, src/pages/LeadFollowUpCalendarPage.tsx, test/lead-calendar-ownership-sort-browser.py; docs/api/lead-follow-up-calendar.md; this record.
- Dependencies: existing ownership_started_at and calendar API; no new dependencies. Target files have no existing Git diffs; unrelated dirty files preserved.
- Verification: focused backend sorting/paging/validation tests, existing calendar tests, Workbench typecheck, Chrome synthetic API flow at desktop/mobile widths, scoped diff. Isolate build outputs; authenticated runtime acceptance excluded.
- Scope update: own the clean existing LeadFollowUpResponsibilityTest.java fixture to add the now-required ownership timestamp column and verify actual query projection. Verification adds its H2 MySQL-mode query tests.

## Delivery — 2026-10-18
- Status: delivered locally; ownership released.
- Added 归属开始时间 / ownershipStartedAt to Workbench and backend calendar contract. Sorts all matching date-range rows before pagination; missing historical timestamps last in both directions; ties use deadline then internal ID. Existing category/deadline defaults retained. New mode direction labels: 最早优先 / 最新优先.
- SQL query projects the existing formal-owner timestamp. No schema, permissions, tenant rules or public-sea responsibility changes. Vue Admin has no native consumer for this employee page; existing APIs remain compatible.
- Verification: full source snapshot reactor compiled successfully; LeadCalendarServiceTest 13/13 and LeadFollowUpResponsibilityTest 13/13 passed (26 total). H2 MySQL-mode executes production query and verifies timestamp/null projection and existing responsibility boundaries. Workbench npm run typecheck passed. Real Chrome synthetic API flow passed at 1600x1100 and 390x844, including option visibility, asc/desc parameters, default page, original-sort switch and no document overflow. Scoped git diff --check passed.
- Evidence: D:/ZSJ-OS-backups/lead-calendar-ownership-20261018/final-tests.log and ownership-sort-{1600,390}.png. Initial single-module build hit stale installed BPM APIs; complete reactor snapshot resolved it without shared output changes.
- Limits: no restarted/deployed backend or authenticated live API verification; SQL execution above is H2 compatibility-mode evidence, not target MySQL acceptance. Screenshots captured; automated browser visibility/layout assertions passed. No schema/migration changes, DB writes, service restarts, commits or pushes.
