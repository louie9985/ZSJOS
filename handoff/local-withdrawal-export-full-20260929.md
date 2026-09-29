# Withdrawal complete-information export

## Registration — 2026-09-29 (Asia/Shanghai)
- Environment: local (environment variable and environment file absent).
- Workstream: local-withdrawal-export-full-20260929; owner: Codex /root.
- Goal: export complete withdrawal management information including full card snapshots; expose the existing asynchronous export in Workbench and verify both consumers.
- Non-goals: personal/Partner disclosure changes, permission grants, database writes, payment execution, new dependencies, branch changes, commits or publication.
- Branch: main; worktree: D:\ZSJ-OS; base commit: 5fadfb3e9be563ac4cb3e5c142308549944aa465.
- Ownership: WithdrawalExportTypeProvider.java; focused export provider/task tests; frontend/workbench/src/pages/ManagementPages.tsx; frontend/admin/src/views/zsjos/withdrawal/index.vue; withdrawal export test fixtures/runners under both frontend test directories; docs/api/withdrawal-and-offline-payout.md; docs/api/async-export-and-business-audit.md; directly affected withdrawal export passages in docs/superpowers/specs/2026-09-11-cashback-withdrawal-admin-design.md and docs/frontend/advanced-filter-inventory.md; this record.
- Dependencies: existing WithdrawalService management projection, FinanceTraceService, System PermissionApi, asynchronous task framework and existing frontend tools. No changes to another workstream's dirty files.
- Contract decision: user's explicit request for complete card information supersedes the prior masked withdrawal export contract. Personal and Partner projections remain masked.
- Verification: focused workbook-content and permission tests, existing task revocation/ownership tests, affected frontend static checks and browser flows using isolated fixtures; inspect real API availability and report unavailable verification separately.
- Integration target/order: None.

## Scope update — 2026-09-29 15:05 +08:00
- Add ownership of the withdrawal row in docs/frontend/advanced-filter-field-catalog.md; its published export-field list must reflect the approved full-information contract. No other rows are changed.


## Delivery — 2026-09-29 15:08:54 +08:00
- Environment/branch/worktree/base: unchanged from registration; HEAD remains 5fadfb3e9be563ac4cb3e5c142308549944aa465.
- User goal: complete withdrawal export information including full bank-card numbers.
- Decision/result: replace the masked export projection with the existing management snapshot projection; enrich applicant/partner/payer and BPM reviewer/opinion using existing services. Preserve text-format card numbers, numeric amounts, source snapshots and empty historical fields. Export 25 business columns plus 3 task metadata columns. No expiring proof URLs or attachment bytes are included; legacy proof file ID remains a reference.
- Authorization: dedicated export permission plus finance-query/admin-query checked at creation/execution/download; provider rechecks management scope per page. Both UI entry points additionally require export-task query permission. Real grants, personal/Partner masking, database and shared services are unchanged.
- UI: Workbench export entry added; both clients explain full-card export and link to existing task center; Admin now handles task-creation errors locally. Status/keyword/advancedFilter payload and all-filtered-rows semantics preserved.
- Changed files:
  - backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/export/provider/WithdrawalExportTypeProvider.java
  - backend/yudao-module-zsjos/src/test/java/cn/iocoder/yudao/module/zsjos/service/export/provider/ExportTypeProviderTest.java
  - backend/yudao-module-zsjos/src/test/java/cn/iocoder/yudao/module/zsjos/service/export/ExportTaskServiceImplTest.java
  - backend/yudao-module-zsjos/src/test/java/cn/iocoder/yudao/module/zsjos/service/export/ExportTaskWiringTest.java
  - frontend/workbench/src/pages/ManagementPages.tsx
  - frontend/admin/src/views/zsjos/withdrawal/index.vue
  - frontend/workbench/test/withdrawal-export.tsx
  - frontend/workbench/test/withdrawal-export.html
  - frontend/workbench/test/withdrawal-export-browser.py
  - frontend/admin/test/withdrawal-export.ts
  - frontend/admin/test/withdrawal-export.html
  - docs/api/withdrawal-and-offline-payout.md
  - docs/api/async-export-and-business-audit.md
  - docs/frontend/advanced-filter-inventory.md
  - docs/frontend/advanced-filter-field-catalog.md
  - docs/superpowers/specs/2026-09-11-cashback-withdrawal-admin-design.md
  - handoff/local-withdrawal-export-full-20260929.md
- Verification passed:
  - Current export production sources and four focused test classes compiled with javac into an isolated temporary directory; JUnit 6.0.3 ran 36/36 tests successfully. Untouched dependencies use the previously built local ZSJOS jar and existing reactor/dependency classpath. Covers full card text/leading zeros/precision, numeric amounts, names/branch/review/payout data, empty historical card, cross-page filters, create/execution/download permission revocation, creator-only download and existing personal-view behavior. This does not certify the currently failing full source reactor.
  - Workbench npm run typecheck passed. Admin scoped pnpm exec eslint src/views/zsjos/withdrawal/index.vue passed.
  - python frontend/workbench/test/withdrawal-export-browser.py passed for Admin and Workbench with synthetic transport: explicit contents confirmation, cancel, filter request, failure/retry, task-center navigation, download entry, missing export/query permissions, own view, and admin management scope. No real business writes.
  - Desktop 1440px/mobile 390px confirmation screenshots inspected after transitions settled; both readable and within viewport. Images and test logs remain under the local temporary directory.
  - Scoped git diff --check passed.
- Verification limitations:
  - Normal Maven reactor rerun is blocked by concurrent/unrelated SalesOrderObjectPermissionService references to missing PERMISSION_QUERY_MANAGEMENT/PERMISSION_QUERY_OWN constants. A direct testCompile attempt is also blocked by incomplete module output after that compilation failure. Those files were not modified by this workstream.
  - Admin full pnpm ts:check is blocked by pre-existing duplicate applicantName/partnerName/paidByName/cashbackCount/availableBalanceSnapshot/approvedAmount declarations in src/api/zsjos/withdrawal/index.ts. No errors reported in this workstream's page; duplicates left unchanged.
  - Existing backend process was not rebuilt/restarted; live authenticated API-to-private-file export and deployment remain unverified. Browser fixtures verify UI behavior, not deployed service integration. No production build required: no bundling/dependency/route/asset configuration changed.
- Dependencies/integration: no new dependencies, SQL, role assignments, branch operations, commits or publication. Isolated Admin fixture server on port 5189 stopped; existing runtime processes preserved.
- Remaining work: resolve the separately owned compilation/type failures, then perform integrated backend build/deployment and authenticated live export acceptance. Existing ready export files remain unchanged; new tasks generate the full-information format after rollout.
- Status: local implementation delivered with explicit integration verification limitations; ownership released.
