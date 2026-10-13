# Order management detail information table

## Voucher scope update — 2026-10-13
- User authorizes replacing payment voucher presentation with AttachmentCard on order management and approval pages. Resume owner /root on local main, D:/ZSJ-OS, base 45f1edbe24b316b5f5924416c4fc18977658ff5f; integration None.
- Ownership: SalesOrderDetailCards.tsx, MySalesOrderPage.tsx, SalesOrderApprovalPage.tsx; new SalesOrderVoucherAttachments.tsx and stylesheet; existing order-detail-layout.tsx/browser fixture; new voucher browser check; docs/order-detail-layout.md; this record.
- Keep other shared component consumers and all authorization/API contracts unchanged. The page supplies a typed voucher reload callback using its existing detail API. Initial cards use authorized loaded data; user actions renew via that callback. Preserve prior table-layout work.
- Verify typecheck, existing attachment/detail unit tests, isolated real-page browser image/PDF/download/retry/missing/empty/order-switch checks on both pages, desktop/mobile. No live business writes, service changes or deployment.

## Voucher delivery — 2026-10-13
- Both requested pages now supply reloadVouchers through their existing detail API, enabling SalesOrderVoucherAttachments/AttachmentCard. Initial display reuses authorized data; preview/download/retry renew and match the same infraFileId. Missing rows/URLs produce actionable errors. Keys include order ID, file ID and initial URL to prevent stale card reuse across orders. Other consumers retain previous rendering.
- PASS: typecheck; AttachmentCard and SalesOrderDetailCards tests 21/21; existing order-detail-layout browser regression; new order-voucher-attachments browser check for both real pages with isolated API data. Covers image/PDF preview, freshly reloaded download target, query failure/retry, missing URL/recovery, removed attachment, empty list, order switching, per-page query boundary, desktop 1440 and mobile 390. Screenshots inspected under C:/Users/EDY/AppData/Local/Temp/order-voucher-attachments.
- Original fixture port 5174 was unavailable; a task-owned Vite preview on 5257 was used instead. No shared service restarted. PDF verification covers iframe preview wiring with a synthetic response, not a real uploaded PDF. Download verification captures the renewed target rather than saving a real file. Authenticated live attachment retrieval and deployed acceptance remain unverified.
- Scoped diff checks PASS; related docs and existing fixture selectors/URLs updated. No dependencies, backend/database/permission changes or publication. Ownership released.

## Scope update — 2026-10-13
- User explicitly extends the approved table/tag presentation to 成交订单审批. This supersedes the earlier exclusion of SalesOrderApprovalPage; all other consumer exclusions remain.
- Environment local, main, D:/ZSJ-OS, current base 45f1edbe24b316b5f5924416c4fc18977658ff5f; owner this chat /root; target branch/integration None.
- Resumed ownership: SalesOrderApprovalPage.tsx, docs/order-detail-layout.md, new test/order-approval-layout-browser.py, this record. Existing table component/style reused unchanged.
- Verification: typecheck, existing detail tests, existing isolated approval fixture at 1440/390 widths for pending/done presentation and action gating. No approval decisions sent; no service changes or deployment.

## Approval extension delivery — 2026-10-13
- SalesOrderApprovalPage now opts into the same table/tag detail layout for both pending and completed tasks. Existing task context, action handlers, workflow rail and all field projections unchanged. Documentation updated to the explicitly extended scope.
- PASS: npm run typecheck; SalesOrderDetailCards 10/10 tests; test/order-approval-layout-browser.py using isolated existing fixture on port 5174. Desktop 1440px and mobile 390px checked for pending/done table/tag rendering, completed-task result/time/reason fields, approval/rejection button gating, rail retention, cell overflow and no JS errors. Screenshots inspected in C:/Users/EDY/AppData/Local/Temp/order-approval-layout-browser.
- Scoped git diff --check PASS (only normal CRLF notices). No database/permission/service changes or deployments; authenticated live acceptance not performed. Ownership released.

## Registration — 2026-10-12
- Environment: local (environment variable unset, environment file absent); owner: this chat /root.
- Branch: main; worktree: D:/ZSJ-OS; base: c82c8605d86aef5c8f4cd328600bc7c3efc2bf90; target branch/integration: None.
- Goal: implement the approved second preview, joined information tables with colored value tags, only inside the order-management detail main region.
- Non-goals: list/header/approval rail changes, other detail consumers, backend/dictionary/permission changes, dependency changes, service lifecycle, Git operations.
- Ownership: SalesOrderDetailCards.tsx, new SalesOrderInformationTable.tsx and its scoped stylesheet, MySalesOrderPage.tsx; focused SalesOrderDetailCards tests; new order-detail-layout browser fixtures; docs/order-detail-layout.md; this record (all frontend paths under frontend/workbench).
- Dependencies: existing field projections, snapshot labels, BusinessTable, Ant Design Tag, active Vite on port 5174. Prior status/source/identity edits in shared components are preserved; inspected earlier workstream deliveries release ownership.
- Verification: focused component/style tests, typecheck, isolated real-page browser fixture at desktop/mobile and dark appearance, default shared-consumer regression, scoped diff/UTF-8 checks. No authenticated live business acceptance or deployment implied.

## Delivery — 2026-10-12
- Added an opt-in informationLayout to SalesOrderDetailCards; only MySalesOrderPage passes table. Shared field data remains unchanged; the new SalesOrderInformationTable renders joined label/value rows and existing snapshot text with Ant Design tags. The order information groups stack vertically; two fields per row above a 620px main-region width, otherwise one. Course category labels become tags only in this variant; BusinessTable, specifications, amounts and vouchers are retained.
- Left list/header/approval rail and existing customer profile remain intact. Existing uncommitted lifecycle labels and provider identity edits preserved. Shared default rendering tested, including identical approval rail HTML.
- Checks: npm run typecheck PASS; SalesOrderDetailCards tests 10/10 PASS; style guards 29/30 PASS. The unrelated existing content-review-attachments.css line 6 uses font-size:28px and fails the font token guard; left unchanged.
- Browser: test/order-detail-layout-browser.py PASS on active local Vite 5174, isolated synthetic transport. 1600/1280 desktop, 390/320 mobile, real default-dark theme, tag and snapshot text, profile/copy entry retention, voucher preview, error/retry, shared default consumer. Screenshots inspected at C:/Users/EDY/AppData/Local/Temp/zsjos-order-detail-layout. Mobile capture waits for drawer animation completion.
- Scoped diff checks and new source UTF-8 checks PASS. Source/tests and directly affected layout documentation delivered; no dependency, backend, database, service restart, commit, push or deployment. Authenticated live business acceptance remains unverified. Ownership released.
