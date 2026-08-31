# Main Media Calendar Visibility Workstream

## Workstream Registration - 2026-08-28 00:00:00 +08:00

- Workstream ID: `main-media-calendar-visibility`
- Goal: split the media calendar into an account-scoped calendar and a new all-employee calendar, repair account visibility boundaries for director/operator/supervisor/admin access, and replace the calendar user pickers with role-aware candidate lists.
- Non-goals: change unrelated media-account maintenance behavior, mutate live database data, create or switch branches/worktrees, add dependencies, or alter unrelated dirty-worktree changes.
- Branch: `main`
- Worktree: `D:\ZSJ-OS`
- Base commit: `d4b1de1f7b5b35abd6ece0a578844424c300c8bb` plus existing uncommitted user changes.
- Target branch: current local `main`.
- Ownership scope: ZSJOS media-account calendar backend service/controller/mapper/VOs, System role-aware candidate lookup bridge if needed, Workbench calendar routes/pages/services/tests, directly affected calendar documentation and menu coverage notes, and this handoff record.
- Owner: Codex `/root`
- Dependencies: existing System permission, role, department, and user APIs; existing media-account object permissions and data-scope helpers; React Workbench and Ant Design; no new third-party dependency unless a blocked API gap requires explicit confirmation.
- Integration order: add backend calendar split and visibility rules -> add role-aware candidate endpoints -> add Workbench dual calendar routes and picker wiring -> update menu/docs/tests -> run focused backend/frontend verification -> append delivery entry.
- Verification plan: focused ZSJOS tests for calendar visibility and candidate filtering, Workbench tests/typecheck/build, browser spot checks for both calendar routes, and scoped diff verification.


## Delivery Entry - 2026-08-28 17:20:19 +08:00

- Branch: `main`
- Worktree: `D:\ZSJ-OS`
- HEAD commit: `d4b1de1f7b5b35abd6ece0a578844424c300c8bb`
- User goal: implement the approved calendar repair plan: split account calendar and all-staff calendar, fix account calendar visibility, filter director/operator pickers by System identity, and synchronize menus/docs/tests.
- Key decisions: account calendar now resolves visibility through `MediaDataScopeService` plus account director/operator ownership; `zsjos:media-calendar:query-all` remains only the account-calendar override; all-staff calendar uses `zsjos:media-calendar:all-query` and no account object scope; director/operator picker candidates come from enabled System roles `content_director` and `new_media_operator`; V161 inherits all-staff page grants only from existing calendar directory/account-calendar grants.
- Execution or analysis result: added backend all-staff calendar and candidate endpoints, widened mapper calendar scope from a single user ID to a resolved visible-user collection, refactored Workbench calendar page to switch data source by scope, registered `/calendar/all`, added V161 menu migration and bootstrap/verify coverage, and updated directly affected API/frontend/architecture docs.
- Changed files: `backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/account/MediaAccountController.java`; `backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/account/vo/MediaAccountCalendarCandidatesRespVO.java`; `backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/account/MediaAccountMaintenanceService.java`; `backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/dal/mysql/account/MediaAccountMapper.java`; `backend/yudao-module-zsjos/src/test/java/cn/iocoder/yudao/module/zsjos/service/account/MediaAccountMaintenanceServiceTest.java`; `frontend/workbench/src/pages/MediaCalendarPage.tsx`; `frontend/workbench/src/constants.ts`; `frontend/workbench/src/layouts/RouteHost.tsx`; `frontend/workbench/src/services/api.ts`; `frontend/workbench/src/services/menuComponentRegistry.ts`; `frontend/workbench/src/pages/media-calendar.test.ts`; `frontend/workbench/src/services/menu.test.ts`; `script/sql/mysql/migrations/V161__media_calendar_all_view.sql`; `script/sql/mysql/bootstrap.sql`; `script/sql/mysql/verify-bootstrap.sql`; `script/sql/mysql/migrations/README.md`; `docs/api/media-account-maintenance-calendar.md`; `docs/frontend/zsjos-menu-coverage.md`; `docs/architecture/data-and-permission-flow.md`; `handoff/main-media-calendar-visibility.md`.
- Verification evidence: `mvn -f backend/pom.xml -pl yudao-module-zsjos -am "-Dtest=MediaAccountMaintenanceServiceTest" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed with 11 tests; `npm test -- media-calendar.test.ts menu.test.ts` passed with 21 tests; `npm run typecheck` passed; `npm run build` passed with the existing Vite chunk-size warning; `git diff --check` over touched files passed.
- Dependency or integration impact: no new Maven or npm dependency; V161 must be applied through the normal migration process before `/calendar/all` appears in server-owned menus; no live database or external permission state was changed.
- Remaining work: full backend `mvn -f backend/pom.xml -pl yudao-module-zsjos -am test` is blocked before ZSJOS by pre-existing `yudao-module-infra` failure `CodegenEngineUniappTest.testExecute_treeSearch`; full Workbench `npm test` is blocked by pre-existing positioning-card guard expectations in `src/pages/media-students.guard.test.ts`; real DB migration execution, `verify-bootstrap.sql` execution, and browser checks were not run in this turn.


## Delivery Entry - 2026-08-28 17:52:33 +08:00

- Branch: `main`
- Worktree: `D:\ZSJ-OS`
- HEAD commit: `d4b1de1f7b5b35abd6ece0a578844424c300c8bb`
- User goal: continue the calendar repair by renaming the shared calendar from `全员日历` to `日历日程` and making it visually follow the attached WeCom-style calendar instead of repeating the account-calendar timeline.
- Key decisions: kept the existing public route `/calendar/all` and backend permission `zsjos:media-calendar:all-query`; changed only the user-facing shared page/menu name to `日历日程`; split the all-scope Workbench rendering into a separate `media-schedule-*` month-calendar layout while preserving the account calendar's existing account timeline; retained read-only behavior and backend-driven director/operator candidate filters.
- Execution or analysis result: added an independent all-scope schedule component with left search/mini-month/filter sidebar, right month grid, day detail list, reload/today/month navigation, tooltips, empty/error/loading handling, and pagination for results beyond the first page; synchronized the V161 menu label and verification checks plus affected API/frontend/architecture docs and tests.
- Changed files: `frontend/workbench/src/pages/MediaCalendarPage.tsx`; `frontend/workbench/src/styles/pages/media-calendar.css`; `frontend/workbench/src/pages/media-calendar.test.ts`; `docs/api/media-account-maintenance-calendar.md`; `docs/frontend/zsjos-menu-coverage.md`; `docs/architecture/data-and-permission-flow.md`; `script/sql/mysql/migrations/V161__media_calendar_all_view.sql`; `script/sql/mysql/verify-bootstrap.sql`; `script/sql/mysql/migrations/README.md`; `handoff/main-media-calendar-visibility.md`.
- Verification evidence: `npm test -- media-calendar.test.ts menu.test.ts` passed with 21 tests; `npm test -- styles.guard.test.ts media-calendar.test.ts menu.test.ts` passed with 47 tests; `npm run typecheck` passed; `npm run build` passed with the existing Vite chunk-size warning; `git diff --check` over touched files passed. Dev server started at `http://127.0.0.1:5173/` for manual inspection.
- Dependency or integration impact: no new npm or Maven dependency; no live database or external permission state was changed; V161 now seeds/verifies the visible menu name `日历日程` for environments that apply the migration.
- Remaining work: real browser screenshot inspection was not completed because no Node browser-control tool was exposed in this session; real backend data/permission verification and SQL execution remain environment-dependent.
