# local-student-course-card-20261102
- Environment: local (environment variable and marker absent); owner: Codex.
- Goal: collection-task cards show historical course specifications like registration pool.
- Non-goals: workflow, permissions, database or deployment changes.
- Branch: main; worktree: D:\ZSJ-OS; base: 0ecc74d4d13bcfbc0361ecdbc2bbd73327359425.
- Ownership: StudentInfoTaskVO.java, StudentInfoTaskService.java, StudentInfoTaskServiceTest.java; frontend/workbench/src/pages/StudentInfoTasksPage.tsx, services/studentInfoTasks.ts, styles/pages/student-info-tasks.css; docs/api/student-information-collection.md; this record.
- Dependencies: existing order-item snapshot and ProductSpecs component. Integration: None.
- Verification: targeted backend test, workbench typecheck, browser inspection if available.
`n## Delivery`n- Added historical specs to task response and reused ProductSpecs in task cards; wrapped long course/spec text and added order status. API documentation synchronized.`n- Verification: Workbench typecheck passed; StudentInfoTaskServiceTest 16 tests passed; diff whitespace check passed. Initial fixture lacked required labelMissing flag; corrected fixture and rerun passed.`n- Browser: localhost:5174 reached login page; authenticated desktop/mobile visual verification and live updated backend response remain unverified. No deployment/restart/commit. Ownership released.

## Follow-up registration 2026-11-02
- Scope: shared public-pool detail layout; task information left / collection workflow right; shared panel spacing.
- Add ownership: components/RegistrationDetailLayout.tsx, pages/RegistrationPages.tsx, styles/components/student-info-panel.css (under frontend/workbench/src). Existing unrelated edits preserved. No business workflow or permission changes.
- Checks: typecheck and desktop/mobile browser inspection; both layout consumers.
- Verification fixture ownership added: frontend/workbench/test/collection-layout.html and collection-layout.tsx (synthetic transport only).

## Follow-up delivery
- Shared RegistrationDetailLayout now used by public pool and collection page; preserved pool hero, information, process and actions. Collection reuses pool list styles, avatars, detail columns, steps and responsive behavior.
- Shared StudentInfoPanel notices have bottom spacing; task details keep their existing operations and permissions.
- Checks: Workbench typecheck passed; desktop and 390px mobile synthetic fixture screenshots inspected (table detail/modal). Live account remained at login; real business-data acceptance, pool live regression and split-mode visual inspection remain unverified. No service restart/deployment/commit.
- Ownership released.

## Sent-button follow-up registration
- Goal: show confirmation only for server-owned UNSENT tasks; confirmation response and refresh remove it.
- Owner: Codex; local/main/worktree/base unchanged. Scope: StudentInfoTasksPage.tsx, docs/api/student-information-collection.md, this record.
- Checks: typecheck and scoped content check. No backend or permission changes.

## Sent-button delivery
- Button now requires UNSENT; successful command updates selected from backend response, so it disappears and remains absent after reload. Failed commands keep existing state.
- Workbench typecheck and scoped diff check passed; server sent/status mapping inspected. Actual browser click against live data not verified (prior session remained unauthenticated). No deployment/commit. Ownership released.

## Toolbar follow-up registration
- Scope: StudentInfoTasksPage.tsx, student-info-tasks.css, docs/api/student-information-collection.md, this record; owner/context unchanged.
- Remove page title/local layout selector/refresh, use global inbox layout, filters then search. Preserve error retry. Checks: typecheck and fixture browser desktop/mobile.

## Toolbar delivery
- Removed local view switch, duplicate page/table heading and refresh entries. Global useInboxTableLayout retained; order-status filter precedes search at the top; status tabs retained.
- Typecheck passed. Browser fixture inspected at desktop and 390px mobile; filter/search alignment and absence of local selector verified. Synthetic data only; no deployment/commit. Ownership released.
