# Shared Lead table sorting

## Registration — local-lead-table-sort-20261025
- Environment local (ZSJOS_AGENT_ENV unset, /etc marker absent); owner Codex /root; branch main; worktree D:/ZSJ-OS; base 404387de566fcb9abf4b264f0e005bf0566ea209; integration None.
- Goal: add an explicit sorting menu to the exact LeadManagementPage table used by Lead Management and subordinate-sales owned Leads; synchronize it with header sorting and use existing server pagination/sort contracts.
- Non-goals: backend/default order/permissions/data changes, other pages or dependencies, deployment and Git operations. Preserve all existing dirty changes.
- Ownership: frontend/workbench/src/pages/LeadManagementPage.tsx (sort state/toolbar only), test/lead-table-sort-browser.py, docs/business-table.md, this record. No additional writers found for this scope.
- Dependencies: existing BusinessSortMenu, allowlisted LEAD_SORT_FIELD_BY_COLUMN_KEY, GET/POST APIs and backend pre-pagination ordering. New menu derives field labels from the same business columns and only offers registered server-sortable fields.
- Verification: existing focused sort/selection/shared-page guards and TypeScript check; real Chrome isolated HTTP fixtures for general/scoped tables, menu/header synchronization, asc/desc/default, page/selection reset, retry, advanced filter and desktop/mobile. No new test dependencies.

- Scope update: include src/components/BusinessSortMenu.tsx for an optional flat presentation used only by this shared Lead page at narrow widths. Browser found the old hover submenu hides before touch-width selection. Other consumers retain default nested behavior; inspect and verify flat date/name selection, scrolling and default reset.
- Presentation refinement: use the opt-in flat menu at both desktop and mobile widths for these two Lead tables; real browser also exposed off-viewport hover submenus on desktop. Other pages keep the unchanged default.

## Delivery
- Added the explicit sorting action in shared LeadManagementPage BusinessTable; both general Lead Management and subordinate owned/recycled Leads consume it. Menu labels derive from current columns only when registered in LEAD_SORT_FIELD_BY_COLUMN_KEY. Header and menu share changeLeadSort/state, clear batch selection and reset pagination via the existing query effect. Existing default order/server GET/POST/sales scope is unchanged.
- BusinessSortMenu has an opt-in flat presentation for these two tables: direct field/direction entries in a scrollable list, no hover dependency on desktop or mobile. Other consumers use the unchanged nested default.
- Verification: npm run typecheck passed; 45 focused tests passed across shared page, sort/resizing, batches, supervisor actions, lead helpers and generic sorting/API tests. Scoped git diff --check passed. Real Chrome test/lead-table-sort-browser.py passed both general and scoped table menu/header asc/desc/default synchronization, allowlisted fields, page/selection reset, pagination persistence, keyword/retry and scoped 390px advanced-filter POST/date/name sorting/default. Desktop/mobile screenshots visually inspected at C:/Users/EDY/AppData/Local/Temp/zsjos-lead-table-sort-20261025. Browser adapters isolate all HTTP; no live business data or shared-service writes.
- Earlier browser attempts exposed the old hover submenu disappearing/offscreen; flat presentation resolves it. A premature menu DOM assertion was fixed to wait for visibility. No unrelated test failure in final focused run.
- Updated docs/business-table.md. No backend/default-order/permission/dependency/schema changes; no production build needed for this existing UI composition with no route/asset/build changes. No commit/push/deployment/restart or real-account acceptance. Ownership released.

## Reversal registration
- User explicitly requests reverting the new sort button: existing header sorting is sufficient. Reopen same local/main workstream and scope solely to reverse its page/menu/documentation/test additions, preserving earlier shared-page work and unrelated dirty edits. Verification: focused tests, typecheck and real-browser existing header sorting with absence of new button.

## Reversal delivery
- Removed the newly added Lead table sort action/imports/field-choice helper and restored the exact original header sort handler. Restored BusinessSortMenu without the optional flat mode; git diff for that component is empty. Removed only this workstream's new test/lead-table-sort-browser.py and documentation paragraph; retained this historical reversal record and all earlier shared-page/unrelated changes.
- Verification: typecheck and 45 focused tests passed; real Chrome isolated general/scoped table checks passed absence of added sort button plus existing header ascending/descending/default server requests. Scoped diff check passed. User clarification honored; no deployment or Git operations. Ownership released.
