# Registration — local-order-gift-address-required-20261021

- Environment: local (environment variable unset; environment file absent); owner: Codex /root, order gift address chat.
- Branch: main; worktree: D:/ZSJ-OS; base: b1ecfffc9f2e19fa4d58d455cb4e4c3e7c033f11; target branch/integration order: None.
- Goal: show the conditional required asterisk and field validation for gift shipping address in the shared Workbench order entry form.
- Non-goals: backend/API, permissions, database, deployment, dependencies, Git operations, unrelated concurrent work.
- Ownership: this record; frontend/workbench/src/components/SalesOrderEntryModal.tsx; docs/api/zsjos-sales-order.md; isolated verification artifacts under frontend/workbench/tmp/order-gift-address/.
- Dependencies: existing Ant Design Form and server gift catalog; no shared contract changes.
- Verification: focused existing order component/service tests, Workbench typecheck, actual component in isolated Chromium with fixture APIs (desktop/mobile; no gift, selected gift, blank/whitespace address, valid address, clearing gifts). No production build required for field validation metadata alone.

## Delivery — 2026-10-21 11:51 Beijing time
- Branch/worktree/base: unchanged from registration; HEAD b1ecfffc9f2e19fa4d58d455cb4e4c3e7c033f11.
- User goal: mark the shipping address required after selecting order gifts.
- Decisions/result: use the shared Workbench modal's watched gift selection and Ant Design conditional required/whitespace rules with gift dependencies. Shows the native asterisk and field error; removes both required state and obsolete validation error when all gifts are cleared. Existing submit/backend rules retained.
- Changed files: frontend/workbench/src/components/SalesOrderEntryModal.tsx; docs/api/zsjos-sales-order.md; this record. Isolated verification fixture/script/screenshots/cache under frontend/workbench/tmp/order-gift-address/.
- Verification: 36/36 focused tests passed (SalesOrderEntryModal, salesOrder, orderGifts); npm run typecheck passed; scoped git diff --check passed. Real Chrome with isolated fixture APIs passed at 1440px and 390px: unselected/selected asterisk (including CSS pseudo-element), empty and whitespace rejection, valid address, clearing gifts clears errors and allows an empty address, reselection restores requirement. Screenshots visually inspected. Submit fixture initially hit existing course prevalidation; corrected the fixture to supply a valid draft course before the passing checks. No live business writes.
- Dependency/integration impact: None; no dependency, API, DB, permission, branch or publishing changes. Unrelated concurrent edits preserved.
- Remaining work: frontend deployment and real-account acceptance not performed. Ownership released; no local implementation work remains.
