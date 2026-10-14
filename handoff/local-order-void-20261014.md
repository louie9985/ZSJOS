# Registration — local-order-void-20261014

- Environment: local (no environment override or environment file). Owner: Codex /root.
- Branch: codex/order-void, created at user request from main. Worktree: D:/ZSJ-OS. Base: e07efc9d6baa5d40b5ec07b78f6743b0d1b2915e. Target branch / integration order: None.
- Goal: finance-authorized void of effective orders; exclude void orders from performance and restore eligible first-purchase entry. New orders follow existing approval and registration task creation.
- Non-goals: stop/cancel/migrate existing registration or service relations; automatic service succession; new BPM approval; change real role grants; commit/push/deploy/restart; alter unrelated existing changes.
- Ownership: order service/controller/VO/permission/constants/mapper and focused tests; Lead entry eligibility only if required; Workbench sales-order API/detail/approval/list status surfaces and scoped tests; Admin existing order display/action consumers; additive order-void migration and isolated verifier; affected API/operations docs; this handoff.
- Existing work: 268 dirty paths retained. Existing SalesOrderServiceImpl diff filters blank imported BPM IDs and will be preserved. Registration V317 source delivery is complete and ownership released; its independent item tasks and end-without-delivery are dependencies, not modified here.
- Dependencies: existing command idempotency and finance-center configuration, System configured operation permission, tenant 1, effective-order reporting. Financial/gift/payment handling question pending; implement independent contract/UI pieces while awaiting answer.
- Verification: focused Java lifecycle/permission/concurrency invariants; MySQL migration first/replay/partial/failure ledger verification; frontend type checks and focused real-component browser checks; scoped diff checks. Report deployment/authenticated acceptance separately.

## Scope update
- User explicitly authorized voiding despite payout/gift/payment associations and invalidating related business. Include gift purchase DO/VO/controller and Admin gift list/API; payment allocation DO; withdrawal release guard and focused tests. Preserve payment transactions, paid withdrawal records, BPM and service histories. New orders do not automatically reuse old payments. Existing withdrawal commands cannot pay cancelled cashback, and rejection/cancellation must not revive it.

- Scope update: add nullable BPM command references for historical-order voids; synchronize only order/command/allocation CREATE definitions in baseline and desired core schema. Add lifecycle filter/sort labels and registration order-status label only (no registration behavior changes). Verify complete fresh chain plus scoped V319 upgrade/recovery. Include task-specific browser fixtures in both frontends and withdrawal invalid-source error/tests.

## Delivery — 2026-10-14 17:31:56 +0800 (Beijing)

- Branch/worktree/base: codex/order-void; D:/ZSJ-OS; HEAD remains e07efc9d6baa5d40b5ec07b78f6743b0d1b2915e. No commit, push, merge, service restart, business DB write, or real role grant. All pre-existing dirty changes retained.
- Result: implemented independent finance void API with configured permission + enabled finance-center object authorization, trimmed mandatory reason, row locks/version/idempotent command and retained original approval history. Current order state becomes voided; existing effective-only performance queries exclude it. Reopens eligible won Lead and won opportunity with owner preserved; missing opportunity creates a current following opportunity, no historical reconstruction. Customer-only final-order void resolves existing Lead by Person. Current activity advances to avoid immediate stale-activity recycling; no historical follow-up tasks are restored.
- Confirmed association handling: all order-linked cashback statuses (including withdrawing/withdrawn) become cancelled, no payout/receipt history rewrite. Cancel/reject withdrawal skips cancelled items; payout rejects them with a distinct error. Gifts and order-payment allocations record voided_at. Gift list hides invalid entries by default and supports historical inspection. Original payments/purchase intents stay historical; no automatic reuse, refund or recovery. Existing registration/service/class/contact-extension records stay unchanged. New order uses existing approval/item-registration flow.
- UI: Workbench management and approval details expose server-authorized void action and refresh using existing request guards. Both management/approval filters and exports accept voided; shared status displays updated. Admin existing management detail has the same operation; related gift list exposes historical invalidation. Both clients show confirmation and reason validation. Docs record manual delivery handling and funds boundary.
- Verification: final Maven reactor compile PASS. Order/withdrawal suite: 170 passing tests with the pre-existing keywordMixedProcessIdsAreNormalizedBeforeTaskLoading excluded after first run exposed its expected-once vs actual-twice mismatch; its original source diff was retained. Subsequently expanded permission suite 6/6 and final void service suite 16/16 PASS (172 unique related cases in total). Covers operation permission + configured finance membership + disabled account denial, invalid lifecycle/version/replay, historical no-BPM orders, existing effective-order guard, customer-linked reopen, cancellation of all rebate states, payout refusal and safe withdrawal release. Final constant substitution compiled successfully.
- Frontend verification: final Workbench typecheck PASS; 20 focused existing tests PASS. Actual React/Vue management components at 1440 and 390: permission-hidden action, required reason, confirmed void, refreshed status, disabled repeat operation PASS; React simulated failure/retry retained idempotency key. Screenshots visually inspected under %TEMP%/order-void-browser. Synthetic HTTP adapters only; no authenticated business writes. Admin full ts:check remains blocked solely by existing duplicate declarations in api/zsjos/withdrawal/index.ts (12 errors); new component error corrected and absent on rerun. Temporary Admin port 5189 test server stopped; pre-existing Workbench/backend kept intact.
- SQL verification: MySQL 8.4.11 scoped V319 first/replay/partial-DDL/failure-after-DDL recovery/missing prerequisite checks PASS, UTF-8 label HEX and nullable independent command persisted. Retained test DBs: order_void_20261014170821137717_initial, order_void_20261014170825940140_partial, order_void_20261014170830764325_failure, order_void_20261014170835802704_prerequisite. Complete Core-only fresh bootstrap + 318 pending migrations through V319 + Core verification + nonempty bootstrap guard PASS. Standard all-module fresh test blocked by existing PMS V002 requires PMS V001; not changed or hidden. Static database manifest/schema/mapping check PASS; baseline and desired schema identical.
- Evidence: %TEMP%/order-void-tests2.log; order-void-permissions.log; order-void-final-tests2.log; order-void-final-compile.log; order-void-admin-types2.log; order-void-fresh.log; order-void-core-fresh.log. Scoped UTF-8 and git diff --check PASS. Verification fixture debugging corrected lambda-cache initialization, nested-dialog selectors and synthetic JSON object identity; production refresh remains server-owned.
- Deployment status: read-only local preflight confirms all five new columns, V319 marker and void permission definition are absent. Migration has NOT been applied to the development business DB. No real order was voided. Apply V319 before deploying the matching code, configure operation permission via role management for finance, then run authenticated end-to-end acceptance. Full all-module release acceptance remains blocked as above; no claim of deployment or live payment/withdrawal acceptance.
- Dependencies: no new npm/Maven dependency. Registration item-task source V317 remains the existing dependency and was preserved. No integration branch/commit planned. Ownership released after delivery.

### Owned files

- `backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/order/SalesOrderController.java`
- `backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/order/vo/SalesOrderVoidReqVO.java`
- `backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/order/vo/SalesOrderRespVO.java`
- `backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/order/vo/SalesOrderPageReqVO.java`
- `backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/order/vo/SalesOrderMyPageReqVO.java`
- `backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/order/vo/SalesOrderMyCursorReqVO.java`
- `backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/order/vo/FinanceOrderExportReqVO.java`
- `backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/giftpurchase/GiftPurchaseController.java`
- `backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/giftpurchase/vo/GiftPurchasePageReqVO.java`
- `backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/giftpurchase/vo/GiftPurchaseRespVO.java`
- `backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/dal/dataobject/order/SalesOrderDO.java`
- `backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/dal/dataobject/giftpurchase/GiftPurchaseDO.java`
- `backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/dal/dataobject/payment/OrderPaymentAllocationDO.java`
- `backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/enums/SalesOrderConstants.java`
- `backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/enums/ZsjosErrorCodeConstants.java`
- `backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/order/SalesOrderVoidService.java`
- `backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/order/SalesOrderServiceImpl.java`
- `backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/order/SalesOrderObjectPermissionService.java`
- `backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/withdrawal/WithdrawalServiceImpl.java`
- `backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/cashback/FinanceTraceService.java`
- `backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/OrderFilterFields.java`
- `backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/sorting/OrderListSort.java`
- `backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/registration/RegistrationServiceImpl.java`
- `backend/yudao-module-zsjos/src/test/java/cn/iocoder/yudao/module/zsjos/service/order/SalesOrderVoidServiceTest.java`
- `backend/yudao-module-zsjos/src/test/java/cn/iocoder/yudao/module/zsjos/service/order/SalesOrderServiceImplTest.java`
- `backend/yudao-module-zsjos/src/test/java/cn/iocoder/yudao/module/zsjos/service/order/SalesOrderObjectPermissionServiceTest.java`
- `backend/yudao-module-zsjos/src/test/java/cn/iocoder/yudao/module/zsjos/service/withdrawal/WithdrawalServiceImplTest.java`
- `frontend/workbench/src/services/api.ts`
- `frontend/workbench/src/components/SalesOrderDetailCards.tsx`
- `frontend/workbench/src/components/SalesOrderVoidButton.tsx`
- `frontend/workbench/src/pages/MySalesOrderPage.tsx`
- `frontend/workbench/src/pages/SalesOrderApprovalPage.tsx`
- `frontend/workbench/test/order-void.tsx`
- `frontend/workbench/test/order-void.html`
- `frontend/workbench/test/order-void-browser.py`
- `frontend/admin/src/api/zsjos/workbenchMenus.ts`
- `frontend/admin/src/api/zsjos/gift/index.ts`
- `frontend/admin/src/views/zsjos/components/SalesOrderVoidButton.vue`
- `frontend/admin/src/views/zsjos/components/OrderHistoryFacts.vue`
- `frontend/admin/src/views/zsjos/components/WorkbenchListPage.vue`
- `frontend/admin/src/views/zsjos/mySalesOrder/index.vue`
- `frontend/admin/src/views/zsjos/gift-purchase/index.vue`
- `frontend/admin/test/order-void.ts`
- `frontend/admin/test/order-void.html`
- `script/sql/mysql/migrations/V319__sales_order_void.sql`
- `script/sql/mysql/tools/test_sales_order_void.py`
- `script/sql/mysql/00-bootstrap-schema.sql`
- `script/sql/mysql/schema/core.sql`
- `docs/api/zsjos-sales-order.md`
- `docs/api/sales-performance.md`
- `docs/api/registration-fulfillment-api.md`
- `docs/operations/sales-order-void.md`
- `handoff/local-order-void-20261014.md`

## Integration registration update
- User requested merge into main. Owner remains Codex /root; environment local; worktree D:/ZSJ-OS; base unchanged. Target main; order: stage only this feature, commit on codex/order-void, fast-forward main, retain unrelated worktree changes. No push/deploy or real database/permission changes. Verification: inspect staged patch, compare working file hashes, verify ancestry and branch HEAD.

## Integration delivery — 2026-10-14 17:48:33 +0800 (Beijing)

- User goal: merge the order-void feature into main. Environment local; owner Codex /root; worktree D:/ZSJ-OS.
- Result: feature-only commit `0d53e1a513f43227c24fbcabce3adfa7a04be994` created on codex/order-void, main fast-forwarded from e07efc9d6baa5d40b5ec07b78f6743b0d1b2915e, and checkout switched to main. Main was advanced with an ancestry check and compare-and-swap ref update while the feature branch was checked out, avoiding a stash or checkout rewrite of overlapping dirty files.
- Scope: 53 feature paths, 860 additions/30 deletions. Mixed files were staged from HEAD plus only order-void changes; existing approval keyword fixes, gift revision UI, registration item tasks, subordinate changes and unrelated documentation remain uncommitted. No unrelated changes were committed.
- Verification: cached diff/content checks passed; main and feature HEAD matched immediately after integration; SHA-256 comparisons verified all 353 pre-integration changed/untracked files retained identical bytes. Initial direct index staging retained temporary Windows CRLF; corrected index blobs to Git LF normalization before commit without modifying working files. No executable change in this turn, so previous workspace runtime test evidence is reused; it does not claim an isolated build without the existing local work.
- This delivery-record append is committed separately on main. Feature branch retained. No push, database migration, role assignment or deployment. Existing V317/V318 and other work remains uncommitted; previously documented runtime/deployment prerequisites still apply. Ownership released.
