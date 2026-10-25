# local-zsjkqt-rest-import-20261023
- Environment: local (environment variable unset; marker absent). Owner: Codex /root.
- Branch main; worktree D:/ZSJ-OS; base 404387de566fcb9abf4b264f0e005bf0566ea209; integration None.
- Goal: import current-month homepage rest dates as ordinary (default) or paid, preserve existing selections, allow local adjustment.
- Non-goals: homepage writes, backend/auth/grants/schema changes, dependencies, other frontend, Git operations.
- Ownership: this record; frontend/workbench/src/features/zsjkqt/ui/App.tsx, ui/HomeRestDayImport.tsx; core/homeRestDays.ts and test; ZsjkqtPage.tsx; docs/api/zsjkqt.md; test/zsjkqt-rest-import*.
- Dependencies: existing calendarRestDays.list, business-task:query; preserve pre-existing untracked feature implementation.
- Verification: focused month/filter/merge tests, typecheck, isolated browser flow if available; scoped diff/content checks.

## Delivery
- Added import controls to initial and reimport month parameters; default ordinary / selectable paid; only adds dates not already in either category, keeps manual adjustments and snapshot flow. Existing homepage read authorization is required; no grants or backend changes.
- Verification: 116/116 feature tests passed; initial typecheck passed. Isolated Chrome import flow passed 1440/390 for default/paid, repeated import, existing categories, month range, error/retry/empty/denied. Screenshots inspected. Existing review-fixes Chrome regression passed both widths, no browser errors.
- New fixture uses isolated service responses, not authenticated live-account acceptance. No deployment, DB mutation, dependency manifest changes or Git operations. A mistaken root npx invocation downloaded a temporary npm-cache test runner; project checks reran with the existing Workbench runner. No repository dependency added.
- Ownership released; implementation and directly affected API documentation complete.

## Registration update - import styling
- Same local environment, branch, worktree, owner and base. User requests correction of import styles. Ownership additionally includes styles/zsjkqt.css and the existing import browser fixtures; no other active writer observed. Scope: Ant Design Select replacing native dropdown, dedicated wrapping/aligned toolbar, scoped status spacing. Verification: typecheck and desktop/mobile Chrome interaction and visual inspection. No business/API changes.

## Delivery - import styling
- Replaced native select with existing Ant Design Select. Dedicated wrapping toolbar aligns control and button heights; removed unrelated segmented-container styling and normalized status spacing with existing tokens. No import behavior changes.
- Typecheck passed; Chrome isolated flow passed at 1440/390 including opened dropdown, default/paid choices, repeat/preserve/error/retry/empty/denied; screenshots inspected. Browser fixture updated for actual Ant Design option DOM and shared style tokens. Earlier locator attempts addressed hidden accessibility options; final run passed. No deployment or real-account changes. Ownership released.

## Registration update - full parameter layout
- Same local execution context, owner and branch. Reopen App.tsx, styles/zsjkqt.css, this record and test/zsjkqt-rest-import*; additionally test/zsjkqt.tsx and test/zsjkqt-parameters-browser.py plus docs/api/zsjkqt.md. Goal: exclude Select internal input from native input styles; two-column monthly parameters/calendar layout, stacked at narrow width. Non-goals: other steps, business formulas, APIs, grants or dependencies. Verify complete themed editor in Chrome at desktop/mobile, including text geometry, dropdown, import, manual dates and preserved state; typecheck and focused tests.

## Delivery - full parameter layout
- Root cause: native input rule styled Ant Design 6 internal .ant-select-input with height/padding/background, displacing and clipping selected text. Excluded that internal input from native field appearance. Both month parameter steps now group controls left and calendar right; container-width fallback stacks them. Narrow parameter inputs stay within their columns; department cells retain readable nowrap content in existing scroll wrapper.
- Verification: typecheck passed; 116/116 attendance tests passed. Full themed editor Chrome at 1920/1440/390 passed import, dropdown choices, manual date click, selected-text Range bounds inside Select, two-column/stack geometry, input bounds. Final desktop/mobile screenshots inspected in TEMP/zsjkqt-parameters*.png. Existing review-fixes Chrome regression passed 1440/390 with no page errors.
- Existing port 5174 was unavailable; used an isolated temporary Vite fixture server on 5186, no shared runtime restart. Fixture adapter explicitly permits only the added rest-day read path; no live API or account changes. Previous attempts exposed navigation cold-start and fixture guard/locator issues, corrected before final passing checks.
- Documentation synchronized; no dependencies, schema/grant mutation, Git publication or deployment. Ownership released.
