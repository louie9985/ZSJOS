# 测试环境问题分阶段修复

## Registration — 2026-10-08（北京时间）
- Environment: local (environment variable and environment file absent).
- ID/owner: local-runtime-issues-20261008 / this chat /root.
- Goal: implement approved order gift/snapshot and BPM notification repairs; verify existing notice/exam repairs; diagnose remaining reported failures.
- Non-goals: deployment, shared service changes, database writes/schema changes, branch changes, dependencies, bulk historical repair.
- Branch/worktree/base: main; D:/ZSJ-OS; d2f836006ff07b5517bcd6d485d72df788bf3cb0.
- Ownership: ZSJOS order gift parser and order service/response DTOs; LeadProductSnapshot historical reader and its finance/registration consumers; gift purchase response/controller; BPM task notification converter/DTO and focused tests; web unreadable-JSON handler/test; ZSJOS collection validation annotations and focused validation tests; Workbench order entry/details/gift service helpers and API response types/tests; Admin gift purchase view/API; directly affected API and diagnostic documentation; this handoff. Existing unrelated changes in these files are preserved. Notice/exam/upload implementation remains read-only.
- Dependencies: approved plan; existing notice fix and delivered exam date work. Re-read files before edits and preserve working-tree additions. No parallel writers delegated.
- Verification: focused Maven tests/compile, Workbench tests/typecheck, Admin scoped type/contract checks, desktop/mobile browser fixtures; existing notice/exam regression checks; read-only environment diagnostics. Real MySQL/Flowable/deployed acceptance reported separately from mocks.
- Target branch/integration order: None.

## Registration scope clarification — 2026-10-08
- Includes ZsjosErrorCodeConstants.java for distinct gift errors; BPM task callback lookup fallback; exact collection-annotation files below (mechanical only); all focused tests and temporary browser fixture sources owned by this workstream.
- backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/account/vo/MediaAccountFieldConfigSaveReqVO.java
- backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/advancedfilter/vo/AdvancedFilterGroupReqVO.java
- backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/lead/vo/appeal/LeadAppealDecisionReqVO.java
- backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/lead/vo/appeal/LeadAppealSubmitReqVO.java
- backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/lead/vo/duplicate/LeadDuplicateReviewDecisionReqVO.java
- backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/lead/vo/followup/LeadFollowUpCreateReqVO.java
- backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/lead/vo/inboxfilter/LeadInboxFilterConfigVO.java
- backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/lead/vo/management/LeadBasicInfoUpdateReqVO.java
- backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/lead/vo/management/LeadSubmitterSupplementReqVO.java
- backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/lead/vo/qualification/LeadJudgeInvalidReqVO.java
- backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/lead/vo/submission/LeadCreateReqVO.java
- backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/lead/vo/subordinate/LeadOverturnValidReqVO.java
- backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/material/vo/MaterialReferencePreviewReqVO.java
- backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/material/vo/MaterialReferenceReqVO.java
- backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/material/vo/MaterialSchemaSaveReqVO.java
- backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/payment/vo/PurchaseIntentSaveDraftReqVO.java
- backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/performance/vo/PerformanceVO.java
- backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/product/vo/ZsjosProductAttrSaveReqVO.java
- backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/registration/vo/RegistrationClassAssignmentsSaveReqVO.java
- backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/studentinfo/vo/StudentInfoVO.java
- backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/workplan/vo/WorkPlanSaveReqVO.java
- backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/workplan/vo/WorkPlanSearchReqVO.java
- backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/admin/workplan/vo/WorkPlanTemplateSaveReqVO.java
- backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/framework/mediascreen/MediaScreenProperties.java

## Delivery — 2026-10-08 18:42（北京时间）
- Environment/branch/base unchanged: local / main / d2f836006ff07b5517bcd6d485d72df788bf3cb0; worktree D:/ZSJ-OS. Existing unrelated edits preserved. No integration/commit/deployment performed.
- Implemented: order gift snapshot parser and stable errors; separate request codes from snapshots; retain server-owned round history and canonical object-array writes; additive order/gift-purchase response fields; Workbench code backfill/snapshot display and corrupt-data guard; Admin snapshot display; historical product null-flag compatibility with BigDecimal precision preserved; BPM ADMIN/PARTNER initiator fallback without blocking task recipient notifications; sanitized unreadable-body parameter errors; 24 collection element validation annotation corrections.
- Focused tests/fixtures include both frontend test directories, web JSON converter contract test, BPM conversion/callback/real Flowable cancellation/task-event identity tests, gift/history/collection regression tests. Existing notice/exam/upload implementation reused.
- Evidence: 164 backend tests passed (latest two scoped Maven executions, XML count excludes duplicate class runs); 26 frontend tests passed; Workbench typecheck passed; desktop 1440/mobile 390 Chrome production-component fixtures passed. Final canonical giftItemCodes backfill/resubmit rechecked at both widths after consistency guard change. Admin actual helper contract and actual Vue fixture verified. Scoped diff --check and new documentation relative links passed.
- Backend logs: C:/Users/EDY/AppData/Local/Temp/zsjos-runtime-repair-tests.log and zsjos-runtime-final-tests.log. Browser captures: runtime-gifts-browser-5r2vh7kv and runtime-gifts-final-qzaf0k6e under the same Temp directory. Surefire XML resides in affected backend target/surefire-reports directories.
- Test repairs: corrected collection qualified-type annotation syntax, snapshot decimal-scale loss, and an engine fixture bypassing production preconditions before final green runs. Final browser rerun initially used an incorrect button caption, corrected to the real approval label and confirmation action; both widths passed. No test expectation was weakened to hide a production failure.
- Read-only diagnostics: local Docker MySQL 8.4.11 / REPEATABLE-READ, no current deadlock graph; not established as the target test database. Browser confirms 600000ms upload timeout for work order and notice actual request paths; not an end-to-end upload reproduction.
- Unverified: target deployed build/API; real-channel notification deduplication/delivery; target MySQL concurrency; original BPM activity-missing case and complete countersign/business callbacks; large/slow/concurrent uploads; permission/dictionary/version/token/WeCom/location-link original requests; 502/Broken pipe upstream attribution. Full Admin typecheck/production build not used as evidence. No SQL, index, retry or authorization relaxation introduced.
- Documentation: docs/api/zsjos-sales-order.md and docs/operations/runtime-issues-20261008.md record contract, per-issue status, verification limits, rollout/rollback prerequisites and required target evidence.
- Own isolated Admin Vite session 95762 stopped after verification. Existing Workbench/backend/shared services untouched.
- Ownership released for this delivery. Follow-up dependent on target-environment evidence or separately authorized deployment; no outstanding local implementation claimed for unproven hypotheses.
