# Registration — local-inbox-sort-controls-20261029
- Environment local; owner Codex /root; main; D:/ZSJ-OS; base ba3e687e678ce92b67f16615244e47007d0892d4; integration None.
- Scope: BusinessSortMenu.tsx, LeadInboxSortControls.css, MySalesOrderPage.tsx, RegistrationPages.tsx, MediaStudentsPage.tsx, UI guidelines, order-detail-layout fixture and this record.
- Goal: no duplicate table sorting button; inbox field/direction controls plus truthful result/loaded counts. No backend or sorting protocol changes. Verify typecheck, sort tests and browser desktop/mobile.

## Delivery
- Order table removes duplicate sort menu while preserving sortable headers. Existing custom sort menus in order, registration student and media student inboxes use field/direction selectors, explanation and result/loaded counts. Collapsed student rails hide controls. Counts use filtered API totals; order cursor initial load queries matching page total. Existing lead inbox presentation preserved.
- Passed Workbench typecheck, 3 business-sort tests, diff check and Chrome fixture at 1440/390 widths (field, direction, default reset, counts, no horizontal overflow); table mode retains sortable headers and has no extra sort control. Browser fixture is synthetic; actual-account and student page end-to-end validation not performed. No deployment or database changes. Ownership released.

## Registration update — toolbar order
- Same context. Scope adds sales-order.css; modify order, student and media inbox toolbar grouping, UI docs. Reuse InboxAvatarRail for order collapse. Verify desktop/mobile placement, collapse/expand, typecheck.

## Delivery — toolbar order
- Order inbox now uses shared collapse/search/filter row followed by sorting/counts; added persistent order avatar rail collapse. Student extra filters precede sorting, and media student creation moved to page header so collapse/search/filter stay together. Documented common layout.
- Passed Workbench typecheck, scoped diff check and Chrome fixture at 1440/390: first-row alignment, sort/counts below, collapse/expand and no horizontal overflow. Student/media runtime checks remain unverified; no deployment. Ownership released.
