# 首页共享休息日

## Registration
- ID: local-home-rest-days-20261015; owner: Codex /root.
- Environment: local (ZSJOS_AGENT_ENV unset; /etc/zsjos/agent-environment absent).
- Branch: main; worktree: D:/ZSJ-OS; base: 315efd729f2e049fc2639194567d86077ae91184; target branch/integration: None.
- Goal: implement approved homepage-only shared manual rest dates, configurable management permission and atomic versioned batch changes.
- Non-goals: HRM/attendance, other calendars, automatic holidays, notifications, grants, deployment, commits.
- Ownership: new calendarrestday backend classes/tests, new HomeCalendarPanel/RestDayManager and services/tests/CSS/browser fixtures, TodayTasksPage.tsx integration, new V319 migration and its schema/verification metadata, docs/api/home-calendar-rest-days.md, homepage UI documentation, this handoff. Existing unrelated edits remain untouched.
- Dependencies: System permissions and existing execution audit; existing HTTP client, Ant Design; no new dependencies. Shared build outputs used serially.
- Verification: focused backend permissions/version/batch tests, MySQL first/replay/partial recovery/failure checks, frontend tests/typecheck, desktop/mobile browser fixtures, available live endpoint/two-user checks. Record environment-blocked checks separately.

## Registration update
- Correction to follow-up 2: retain outer card/grid/mobile height; user explicitly wants the internal calendar expanded vertically. Carry remaining height through Spin and distribute six date rows evenly. The earlier intrinsic-card-height approach is removed.
- Follow-up 2: homepage calendar is independent per explicit user correction; remove navigation/permission helpers and update affected tests/docs. Calendar card uses intrinsic height instead of stretch/mobile minimum. Same local/main context, scoped ownership of HomeCalendarPanel, TodayTasksPage helper, calendar CSS, existing tests/fixture, docs and this record; verify typecheck and desktop/mobile browser.
- Follow-up: reuse this workstream for the requested title legend placement and blue SVG management action; ownership limited to HomeCalendarPanel, its CSS, directly affected UI documentation and this record. Verify typecheck and desktop/mobile browser layout.
- Includes bootstrap structure, Core desired structure, existing todayTasks source guard adaptation and dedicated SQL/browser fixtures. No existing migration rewritten. Audit uses the existing HTTP interceptor.

## Delivery
- Follow-up 2 delivered: removed homepage calendar navigation, right-arrow entry, router dependency and personal-calendar permission/helper. Outer card height retained per user correction; Spin wrapper and six calendar rows fill remaining card height. Typecheck PASS; five affected existing frontend checks PASS; Chrome at 390px/1440px PASS including unchanged URL after date selection, no jump entry, bottom gap under 35px, no overflow and management regression. Screenshots: C:/Users/EDY/AppData/Local/Temp/home-rest-days-browser-h8l4567h. API/UI docs synchronized. No deployment or backend/SQL changes in this follow-up.
- UI follow-up delivered: legend moved beside the calendar title; management action uses blue link text and existing EditOutlined SVG. Typecheck PASS; 390px browser flow PASS; 1440px geometry/save/reload checks passed and screenshot captured, then remaining viewer check blocked by local Vite port 5174 connection refused. Both saved layout screenshots inspected under home-rest-days-browser-gg6s2jea. No service restart performed.
- Implemented homepage red circular rest dates, month navigation, configurable management button, cross-month drafts, atomic versioned batch writes, conflict refresh, cancel confirmation, read errors and existing personal-calendar navigation. No automatic holidays or HRM changes.
- Backend compile PASS; CalendarRestDayServiceTest 8/8 and CalendarRestDayMySqlTest 4/4 PASS, including real MySQL rollback, concurrent initial writes, disjoint writes and tenant-separated reads.
- Migration 7 cases PASS: initial/replay, missing prerequisite, partial DDL, wrong type, missing unique index, ledger failure, menu identity conflict; UTF-8 HEX and no role grants checked. Controlled schemas retained under rest_days_1015172838_* and rest_days_1015173436_*.
- Full Core baseline and pending migrations executed through V319 in rest_days_1015173436_fresh; scoped new-table structure equals upgrade fixture, both V319 ledgers and menu HEX correct. Overall verification remains FAILED on existing V071 active menu parent integrity, V139 supervisor permissions/public-sea route and V159 public sea terminology. These were not changed.
- Workbench typecheck PASS. Frontend targeted run: 12 passed / 1 unrelated pre-existing submitter-assistance navigation guard failed; new draft tests 3/3 PASS. Source diff confirms that navigation code was untouched.
- Chrome synthetic transport PASS at 390px and 1440px: multi-month drafts/save, reloaded shared viewer, permission controls, red circle/white text, today outline, layout, conflict recovery and read errors. Screenshots inspected at C:/Users/EDY/AppData/Local/Temp/home-rest-days-browser-37wmsu0d. Synthetic viewer evidence is not real-account acceptance.
- zsjos_db.py check PASS; scoped diff whitespace check PASS. Vue role-assignment source inspected: dynamic server menu tree supports new button; authenticated role configuration not exercised.
- Not applied to business database, no real role grants, no restart/deploy/commit/push. Live two-account API/permission acceptance remains unverified until migration and updated backend are deployed. Ownership released.
