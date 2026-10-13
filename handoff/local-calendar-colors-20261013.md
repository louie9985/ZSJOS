# Workstream local-calendar-colors-20261013

- Status: active
- Owner: Codex /root
- Environment: local (environment variable unset; environment file absent)
- Branch: main
- Worktree: D:/ZSJ-OS
- Base commit: 40ec4b41be4f51dda93ad612ebfc2dc5bde1e17a
- Target branch/integration: None
- Goal: Implement approved personal/course event colors and account-synced, independently named palettes.
- Non-goals: Exam changes, Admin editor, historical backfill, role grants, dependencies, Git or service lifecycle operations.
- Ownership: personal/course backend DO/VO/services/mappers; new calendar color API/service/DAL; Workbench personal/course pages, focused calendar service/component/style/tests; V314 migration and focused verification; calendar docs; this record. Shared api.ts limited to calendar type declarations, preserving delivered order changes.
- Dependencies: Existing calendar contracts, tenant/security facilities and exam contrast helper.
- Verification: Focused backend tests/compile, Workbench typecheck and browser fixture, controlled MySQL first/repeat/failure recovery and scoped local application, Admin compatibility, scoped diff checks.

- Scope update: synchronize V314 final definitions in script/sql/mysql/schema/core.sql and 00-bootstrap-schema.sql per database-migrations.md static-check contract; no historical numbered migration edits. Add full fresh-chain verification. Preserve concurrent runner changes.

### Delivery - 2026-10-13 14:17 +08:00

- Status: completed; ownership released.
- Branch/worktree/HEAD: main; D:/ZSJ-OS; 40ec4b41be4f51dda93ad612ebfc2dc5bde1e17a (unchanged).
- User goal: Implement the approved personal/course event color and independently account-synced named palette plan.
- Key decisions: Store actual event color, not a palette reference; null omission preserves old-client color, empty explicitly clears. Course fixed presets come from server, personal presets are independent per calendar/account. Palette JSON owner row and InnoDB lock serialize first insert and limit/name checks. New menu permissions are metadata only; no role grants. Exam and Admin UI remain unchanged.
- Result: Workbench picker, preview, clear, saved palette CRUD, course legend, month/day/mobile colors and contrast; backend VO/DO/persistence and palette APIs with current-user AOP plus calendar-specific permission checks. Course color leaves notification version/hash unchanged. V314 and both fresh/desired schema files added/synchronized. Existing unrelated changes preserved, including api.ts order fields.
- Changed files: backend personal/course DO/VO/services and personal mapper; new CalendarColors, CalendarPaletteService/PermissionProvider/Mapper/DO/Controller and palette VOs; four focused Java test files plus MySQL test; Workbench two calendar pages, calendar type fields in services/api.ts, CalendarColorEditor, calendarColors service, calendar-colors CSS and three browser fixture files; V314, test_calendar_colors.py, 00-bootstrap-schema.sql and schema/core.sql; docs/api/calendar-colors.md, course-calendar.md, Workbench ui-guidelines.md; this record.
- Verification: backend compile PASS; CalendarPaletteServiceTest 4, CalendarPalettePermissionTest 2, PersonalCalendarEventServiceTest 7, CourseCalendarVersionTest 6, real CalendarPaletteMySqlTest 2 (21 total, no failures). Real MySQL confirms persistence, owner/calendar separation, rename/delete and concurrent final-slot exclusion. Workbench typecheck PASS; 18 focused Vitest cases PASS. Chrome fixture 390/1440 x light/dark passes course and personal creation/reload, edit/clear, palette persistence/deletion, read-only palette actions, loading retry and save failure retaining input followed by successful retry. Screenshots C:/Users/EDY/AppData/Local/Temp/calendar-colors-skk67r_9; stable mobile dark screenshot visually reviewed from prior equivalent fixture run calendar-colors-tscyo2ia.
- Database verification: MySQL 8.4.11 controlled first/repeat/prerequisite/ledger failure/partial/wrong-type/missing-index recovery PASS, success markers preserved on failure, UTF-8 labels verified by HEX. Local V314 applied and replayed; original event values and system_role_menu rows exactly preserved; backup C:/Users/EDY/AppData/Local/Temp/cal_colors_1013140449_before.sql. Core full fresh bootstrap + 313 pending migrations + verification/nonempty guard PASS; V314 scoped schema matches local. Database static check PASS. No full-data dump or credentials in this record.
- Independent failure: unfiltered all-module test-fresh failed at concurrently developed PMS V002 with 'PMS V002 requires PMS V001'. Core-only full fresh verification passes. PMS files/runner untouched by this workstream.
- Runtime: original 5174 Vite stopped externally during validation; a dedicated 5198 test server completed remaining fixture checks and was stopped. Backend not restarted, shared/production not deployed, no Git operation.
- Remaining acceptance: deploy/restart backend and frontend, configure the two color-manage button grants through role management as required, then real authenticated HTTP and cross-device acceptance. Admin old request compatibility verified through source request shapes and backend omitted-color tests; no live Admin session run. All-module PMS fresh-chain issue remains outside this task.
