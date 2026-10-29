# Registration — local-order-status-label-20261029
- Environment: local (no environment override); owner: Codex /root; branch: main; worktree: D:/ZSJ-OS; base: ba3e687e678ce92b67f16615244e47007d0892d4; integration: None.
- Goal: rename revision_required display to 已终止 per user request. Non-goals: state transitions, database, deployment.
- Ownership: backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/order/SalesOrderServiceImpl.java, backend/yudao-module-zsjos/src/test/java/cn/iocoder/yudao/module/zsjos/service/order/SalesOrderServiceImplTest.java, docs/api/zsjos-sales-order.md, frontend/workbench/src/components/SalesOrderDetailCards.tsx, frontend/workbench/src/pages/MySalesOrderPage.tsx, frontend/workbench/test/order-approval-status-browser.py, frontend/workbench/test/order-approval-status.tsx, frontend/admin/src/views/zsjos/mySalesOrder/index.vue and this record.
- Verification: scoped diff and label consistency; existing browser fixture if available.

## Delivery
- Updated Workbench detail/my orders, backend approval filter label and Admin selector; synchronized existing test expectations and API documentation. Internal states and resubmission behavior unchanged; revision_required and terminated both display 已终止 and retain separate filter keys.
- Verification: scoped diff and git diff --check passed. Browser fixture port 5249 unavailable; browser rendering and runtime tests not verified. No deployment, database changes or commits. Ownership released.

## Registration update — termination presentation
- Same environment, branch, owner and worktree. Scope: SalesOrderDetailCards.tsx, styles/pages/sales-order.css, docs/ui-guidelines.md, test/order-detail-layout.tsx and this record. Goal: identify manual sales cancellation and make resubmit button full width. Verify existing isolated fixture in browser and typecheck; no backend behavior changes.

## Delivery — termination presentation
- Confirmed terminated is set by authorized sales cancellation; changed title and reason prefix while preserving entered reason. Resubmit action spans entire grid and stays single-line. Updated UI documentation and existing fixture.
- Passed Workbench typecheck, scoped diff check, real Chrome fixture checks at 1440 and 390 widths for wording, full-width/no-wrap button and click callback. Fixture is synthetic; no live order mutation or deployment. Ownership released.
