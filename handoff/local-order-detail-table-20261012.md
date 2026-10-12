# Order management detail information table

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
