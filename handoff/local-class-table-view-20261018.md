# 班级管理表格视图

- Workstream: local-class-table-view-20261018; owner: Codex; environment: local (environment variable/file absent).
- Goal: follow the global inbox view setting; preserve cards in master-detail mode and use BusinessTable with server pagination in desktop table mode.
- Non-goals: backend, permissions, database, shared layout changes, deployment.
- Branch: main; worktree: D:\ZSJ-OS; base: d29fcd5f888ceca4a1980720b1eb0fbf6a1cbfc2; target/integration order: None.
- Ownership: frontend/workbench/src/pages/DeliveryClassPage.tsx; docs/api/delivery-class-management.md (view wording only, preserve existing edits); frontend/workbench/tmp/class-table-view/ (isolated verification fixtures); this record.
- Dependencies: existing useInboxTableLayout and BusinessTable; preserve all concurrent work.
- Verification: Workbench typecheck, existing focused class guards, browser fixture at desktop/mobile widths covering view switch, pagination, filters, actions and loading/error/empty/unauthorized states. Synthetic data is verification-only.

## Delivery

- Implemented the existing global view preference in DeliveryClassPage. Desktop table mode uses BusinessTable, eight business columns, controlled server pagination (12 rows), existing student navigation and guarded maintenance actions. Card markup and infinite scrolling are preserved; switching layout restarts at page 1 and invalidates the prior query. Mobile follows the existing desktop-only table predicate.
- Preserved pre-existing page changes for unified pending-class cards and existing API documentation edits. Added only view-specific documentation.
- PASS: npm run typecheck; focused Vitest class guards and date presets (2 files, 7 tests); scoped git diff --check.
- PASS: real Chromium through the existing local Vite server, with isolated synthetic API responses. Checked desktop 1600x1000 table/cards, pages 1/3/2 with replacement and return from last page, filter reset to page 1, empty result, student navigation, edit dialog, confirmed completion command, loading/error/retry, card infinite scrolling, switch back to table, mobile 390x844 cards with no horizontal overflow, read-only and denied permissions. No browser page errors. Test locator fixes covered searchbox semantics and Ant Design automatic Chinese button spacing; these did not require product changes.
- Evidence: frontend/workbench/tmp/class-table-view/verify.py and table-desktop.png, cards-desktop.png, cards-mobile.png. Fixtures are outside production source.
- Limits: synthetic browser verification does not establish authenticated employee-account acceptance or deployment. No backend/DB/shared service changes, dependency additions, commits or publication. No production build required for this page-only presentation change.
- Ownership released on delivery.
