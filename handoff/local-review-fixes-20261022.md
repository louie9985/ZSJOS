# Workstream local-review-fixes-20261022

## Registration - 2026-10-22 Beijing time
- Environment local (variable unset, environment marker absent); owner Codex /root; branch main; worktree D:/ZSJ-OS; base HEAD 404387de566fcb9abf4b264f0e005bf0566ea209; target branch/integration order None.
- Goal: revalidate and repair R01-R23 from the 2026-10-21 verified review, preserving all existing dirty work. Prior delivered workstream ownership is released; this registration owns only the fixes below.
- Non-goals: real business data repair, account/role grants, application database migration, shared service/deployment/restart, dependencies, Git staging/commit/push/branch/worktree changes. Controlled synthetic databases and isolated browser fixtures are verification only.
- Ownership: reported source files; GiftConfigService and gift-purchase controller; contentreview/production numeric-account cleanup mappers; StudentInfoVO/public H5 submission contract; ExceptionDeliveryClassService and relevant class query/service; canonical core.sql and 00-bootstrap-schema.sql only V340-V342 structures; existing focused tests plus new review regression tests/fixtures; directly affected API/operations docs; this handoff. Numbered applied migrations remain unchanged. File scope extensions needed for exact contract callers/tests will be recorded before writes.
- Dependencies: existing System/Infra/BPM public APIs, MyBatis, existing Maven/Vitest/Playwright/MySQL facilities. One writer for each target; preserve original pre-edit snapshots in private TEMP. No delegation or isolated branches.
- Verification: focused backend state/SQL/permission/concurrency tests and compile; Workbench focused tests/typecheck/build; H5 build and collection draft/browser cases; existing isolated desktop/mobile fixtures for affected UI; controlled MySQL constant/cleanup fixtures and applicable canonical/fresh comparisons. Serial shared Maven/build outputs; independent reads/checks may run concurrently. Record any necessary check blocked by existing errors separately.

## Scope update
- Exact R11 callers added: PersonMapper.java, MyStudentPageReqVO.java and MediaStudentService.java, with internal candidate filtering and single-pass keyword sorting; no new public authorization scope. R18 includes BpmProcessPanel.tsx. R01 includes GiftConfigService.java default flat-catalog projection. R02 includes both existing cleanup mappers. Focused test/document owners remain this workstream; no overlapping active registration found.

## Scope update - fresh bootstrap compatibility
- Controlled fresh execution found V323's installed historical image-width postcondition requires VARCHAR(32). Preserve V323 and V341 identities/checksums. Own bootstrap.sql plus new bootstrap-compatibility.sql: only an empty pre-seed baseline may temporarily use width 32; V341 advances it to canonical 128. Guard both version ledgers, System users and image rows before DDL. This is a fresh-only compatibility step, never application DB synchronization. R21 controlled fresh verification is rerun from a new empty schema.

- Fresh verification scope includes test_core_baseline.py: verify V323 replay at its applicable pre-V341 state, rather than assert its superseded width at the final schema; verify V340-V342 replay against final structure. Full initial chain reached current schema before the old replay assertion failed. No historical migration text or checksum is changed.

## Delivery - 2026-10-22T01:23:35+08:00
- Context: local/main/D:/ZSJ-OS; HEAD unchanged at 404387de566fcb9abf4b264f0e005bf0566ea209; target/integration None; owner Codex /root.
- User goal: implement the previously verified R01-R23 review repairs. All 23 addressed locally; original dirty work preserved through private pre-edit copies; no stage/commit/push, branch/worktree operation, dependency change, real grants, application database migration, deployment or service restart.
- Decisions: retain configured procurement scope and immutable historical snapshots; flatten only the owning gift catalog for new selection; remove numeric account members without coercing historical strings; bind final public submission to locked draft revision; preserve exact-service visibility during keyword prefiltering and pending-file recovery; keep historical migration identities unchanged.
- Bootstrap compatibility: canonical image width is 128. An empty, pre-seed bootstrap step supplies the historical V323 width 32; V341 upgrades it normally. V323 replay is tested at its pre-V341 state, latest V340-V342 at final shape. Existing/installed/data-bearing schemas are rejected before compatibility DDL. The full current chain now succeeds without rewriting historical SQL/checksums.

### Review-item delivery
- R01: 采购新增把礼品树当作平表 — fixed locally.
- R02: 新增直接删除入口复用错误的账号集合清理 — fixed locally.
- R03: 正式提交没有绑定草稿版本 — fixed locally.
- R04: 顶部筛选可静默丢弃未提交跟进 — fixed locally.
- R05: 嵌入审批卸载时遗留宿主 busy 状态 — fixed locally.
- R06: 批量建班保存缺少同步防重 — fixed locally.
- R07: 下属客资的服务端排序被重新覆盖 — fixed locally.
- R08: 默认班未清除空部门，保留旧可见范围 — fixed locally.
- R09: 我的素材搜索仍匹配旧生效版本 — fixed locally.
- R10: 切换素材标签重复消费详情深链 — fixed locally.
- R11: 关键词筛选与排序叠加反复全量读取 — fixed locally.
- R12: 最后一页结课后列表停在越界页 — fixed locally.
- R13: 重构未同步现有回归测试 — fixed locally.
- R14: 异常班并发插入冲突后仍使用快照读 — fixed locally.
- R15: 单个规划师失败会中断全部异常班对账 — fixed locally.
- R16: 转班候选包含失去资格的默认班负责人 — fixed locally.
- R17: 新增附件超限错误码与已提交错误冲突 — fixed locally.
- R18: 流程面板未考虑评论接口的独立权限 — fixed locally.
- R19: 考期选择没有同步自动父级参考日期 — fixed locally.
- R20: 超长班级组 ID 落入无界数字解析 — fixed locally.
- R21: 新增迁移未同步 canonical 目标结构 — fixed locally.
- R22: 不可见的未绑定访谈上传永久占用额度 — fixed locally.
- R23: 旧令牌草稿请求可吞掉新表单自动保存 — fixed locally.

### Verification
- Backend: reactor BUILD SUCCESS; 180 focused cases across the selected service, permission, SQL, state/rollback and regression suites, zero failures/errors/skips. Includes real gift-tree shape, stale-tab submission rejection, NULL-department SQL, current-read race recovery, isolated planner failures, target eligibility, oversized-key errors, snapshot query policy, one-pass keyword sorting and literal candidate semantics.
- Workbench: 11 focused Vitest cases, final typecheck and production build passed (existing chunk advisory). Durable current-source offline Chrome suite passed 1440/390 grouping, rapid confirm, create/move retry, last-page completion, read-only and revoked permission, deep-link tab switch, process-only comment permission, plus preserved unsaved filter input and successful-approval/failed-done-lookup next-task flow. Command: python -X utf8 -B frontend/workbench/test/review-fixes-browser.py.
- H5: production build and revised collection-draft-browser.py passed 1440/390, including a stale tab submitting after a newer saved revision and protection of the newer saved values. No personal values/token are cached locally.
- Admin: isolated current gift page production bundle and existing Chrome CRUD/history/permissions/corrupt-history fixture passed 1440/390; backend tree test verifies the actual tree-to-flat response contract consumed by this page.
- SQL: actual cleanup mapper SQL executed on retained synthetic tables; numeric/mixed-string/sibling/empty/missing-target/other-tenant/replay cases passed. V341/V342 controlled initial, repeat, partial and failure/recovery suites passed. New complete 365-migration fresh chain passed schema/ledger comparison, applicable replay, UTF-8 HEX and nonempty bootstrap rejection. Static manifests/order/desired-schema/Java-mapping check passed. Affected image/draft/purchase-operation metadata exactly matches the development database in a read-only comparison, including Chinese comment HEX; no application writes were necessary.
- Existing limitations: 9 previously present fresh data-grade verification findings remain outside R01-R23 (same checksums/legacy gates/menu/template warnings as prior evidence). No production/global data-grade acceptance is claimed. Authenticated real-account acceptance, migration/deployment and full-size upload/load testing are separate and not performed.
- Evidence: C:\Users\EDY\AppData\Local\Temp\zsjos-review-fixes-20261022/{fix-results.json,backend-results.json,maven-complete.log,maven-search-final.log,workbench-final-types.log,workbench-final-build.log,h5-final.log,h5-final-browser.log,admin-build.log,admin-browser.log,browser-delivery.log,fresh-verified.log,upgrade-image.log,upgrade-draft.log,static-check.log}; retained verification schemas and private before snapshots were not deleted.

### Changed-file ownership inventory
- backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/giftpurchase/GiftPurchaseController.java
- backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/registration/vo/MyStudentPageReqVO.java
- backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/studentinfo/vo/StudentInfoVO.java
- backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/dal/mysql/contentreview/ContentReviewBatchMapper.java
- backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/dal/mysql/lead/PersonMapper.java
- backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/dal/mysql/material/MaterialMapper.java
- backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/dal/mysql/production/ProductionTicketMapper.java
- backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/enums/ZsjosErrorCodeConstants.java
- backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/deliveryclass/DeliveryClassGroupService.java
- backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/deliveryclass/DeliveryClassServiceImpl.java
- backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/deliveryclass/ExceptionDeliveryClassService.java
- backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/deliveryclass/UnscheduledDeliveryClassService.java
- backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/gift/GiftConfigService.java
- backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/giftpurchase/GiftPurchaseService.java
- backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/positioninginterview/PositioningInterviewService.java
- backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/registration/MediaStudentService.java
- backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/registration/MyStudentServiceImpl.java
- backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/studentinfo/StudentInfoFormService.java
- backend/yudao-module-zsjos/src/test/java/cn/iocoder/yudao/module/zsjos/dal/mysql/lead/StudentKeywordCandidateSqlTest.java
- backend/yudao-module-zsjos/src/test/java/cn/iocoder/yudao/module/zsjos/service/advancedfilter/AdvancedFilterFieldCatalogTest.java
- backend/yudao-module-zsjos/src/test/java/cn/iocoder/yudao/module/zsjos/service/deliveryclass/DefaultClassReviewFixTest.java
- backend/yudao-module-zsjos/src/test/java/cn/iocoder/yudao/module/zsjos/service/deliveryclass/UnscheduledDeliveryClassServiceTest.java
- backend/yudao-module-zsjos/src/test/java/cn/iocoder/yudao/module/zsjos/service/giftpurchase/GiftCatalogTreeTest.java
- backend/yudao-module-zsjos/src/test/java/cn/iocoder/yudao/module/zsjos/service/giftpurchase/GiftPurchaseServiceTest.java
- backend/yudao-module-zsjos/src/test/java/cn/iocoder/yudao/module/zsjos/service/material/MaterialPersonalFilterTest.java
- backend/yudao-module-zsjos/src/test/java/cn/iocoder/yudao/module/zsjos/service/positioning/PositioningAttachmentLimitsTest.java
- backend/yudao-module-zsjos/src/test/java/cn/iocoder/yudao/module/zsjos/service/positioning/PositioningCardServiceTest.java
- backend/yudao-module-zsjos/src/test/java/cn/iocoder/yudao/module/zsjos/service/positioninginterview/PositioningInterviewServiceTest.java
- backend/yudao-module-zsjos/src/test/java/cn/iocoder/yudao/module/zsjos/service/registration/MediaStudentServiceTest.java
- backend/yudao-module-zsjos/src/test/java/cn/iocoder/yudao/module/zsjos/service/registration/StudentKeywordReviewTest.java
- backend/yudao-module-zsjos/src/test/java/cn/iocoder/yudao/module/zsjos/service/studentinfo/StudentInfoFormServiceTest.java
- docs/api/delivery-class-management.md
- docs/api/gift-purchase-management.md
- docs/api/material-library.md
- docs/api/media-account-delete-approval.md
- docs/api/positioning-interview.md
- docs/api/registration-fulfillment-api.md
- docs/api/student-information-collection.md
- docs/api/zsjos-subordinate-sales.md
- docs/frontend/approval-center.md
- docs/operations/database-migrations.md
- frontend/h5/src/api/studentInfo.ts
- frontend/h5/src/pages/studentInfo/index.vue
- frontend/h5/tests/collection-draft-browser.py
- frontend/workbench/src/components/DeliveryClassGroupDialogs.tsx
- frontend/workbench/src/components/bpm/BpmProcessPanel.tsx
- frontend/workbench/src/pages/ContentReviewBatchPage.tsx
- frontend/workbench/src/pages/DeliveryClassGroups.tsx
- frontend/workbench/src/pages/DeliveryClassPage.tsx
- frontend/workbench/src/pages/LeadManagementPage.tsx
- frontend/workbench/src/pages/MaterialLibraryPage.tsx
- frontend/workbench/src/pages/SubordinateSalesPage.tsx
- frontend/workbench/src/pages/delivery-class-review-fixes.guard.test.ts
- frontend/workbench/src/services/leadManagement.ts
- frontend/workbench/src/services/reviewFixes.test.ts
- frontend/workbench/test/delivery-class-groups-browser.py
- frontend/workbench/test/review-fixes-browser.py
- handoff/local-review-fixes-20261022.md
- script/sql/mysql/00-bootstrap-schema.sql
- script/sql/mysql/bootstrap-compatibility.sql
- script/sql/mysql/bootstrap.sql
- script/sql/mysql/schema/core.sql
- script/sql/mysql/tools/test_core_baseline.py
- script/sql/mysql/tools/test_review_account_cleanup.py

- Dependencies/integration: None added; ADMIN/tenant/menu authentication truth preserved. Backend and H5 must be deployed together for required submit revision. Ownership released. No implementation work remains within the authorized local repair scope.
