# 周期诊断综合表工作流

## Registration — 2026-10-22（北京时间）
- ID: local-weekly-diagnosis-20261022; environment: local（环境变量与环境文件未指定，按规则回退）。
- Goal: 执行已确认的每周综合诊断、固定周期统计与实时成交方案。
- Non-goals: 启动诊断变更、角色授权、历史推测回填、部署/重启、提交/推送。
- Branch: main; worktree: D:\ZSJ-OS; base: 404387de566fcb9abf4b264f0e005bf0566ea209.
- Owner: Codex root; target branch/integration order: None.
- Ownership: account 诊断 Controller/VO/Service/Mapper/DO 及相关测试；LeadFlowRecorder 状态证据；Workbench AccountDiagnosisForm/AccountProfilePanel/DiagnosisReminder/TodayTasksPage、mediaAccountProfile service 及新增综合诊断组件/测试；V346 综合诊断迁移及专项验证工具；诊断 API/部署/UI 文档；本记录。
- Dependencies: 保留当前大量用户未提交改动，尤其账号阶段计时、删除账号及内容审核变更；既有业务任务/System 字典与权限/Lead 事件设施。不新增依赖。
- Verification: 后端专项测试与编译、Workbench 测试/typecheck/build、受控 MySQL 首次/重复/失败/恢复验证与本地定向非破坏同步、桌面/手机隔离浏览器验证。共享环境操作另行授权。

## Ownership update
- Include frontend/workbench/src/components/AccountReviewRecord.tsx for the weekly history projection, and module error-code constants for distinct metric-refresh errors; preserve unrelated changes.

## Ownership update — task projections
- Include BusinessTaskMapper and MediaAccountDiagnosisTaskSceneProvider for SQL-level weekly representative rows/counts in Today Tasks; internal selectMyPending remains ungrouped for atomic completion. Include focused task projection tests.

## Ownership update — navigation counts
- Include MenuTaskSummaryServiceImpl and its focused tests to count same-deadline diagnosis tasks once in navigation badges; retain existing unrelated lead responsibility changes.

## Delivery — 2026-10-22 16:09:20 +08:00（北京时间）
- Branch/worktree/base: unchanged from registration; HEAD 404387de566fcb9abf4b264f0e005bf0566ea209.
- Goal: 已实施周期诊断综合表；启动诊断不变。未提交代码、未推送、未部署、未重启共享服务。
- Result: 同截止周节点合并7/14/28天；通用字段一次填写；固定窗口与提前提交后原周期继续统计；发布量按提交版本、作品去重及多账号归属；周期结束有效客资证据还原、固定成员和当前成交；缺失标明且可提交；三种入口、导航数量、旧链接和旧历史兼容；新表与整表修订防绕过、版本/幂等/对象权限校验。
- Source changes: new WeeklyDiagnosisVO/WeeklyDiagnosisPeriods/WeeklyDiagnosisService/DiagnosisMetricService/DiagnosisMetricDO/DiagnosisMetricMapper; account diagnosis/profile controllers plus legacy-submit wrapper and distinct error code; existing LeadFlowRecorder adds status evidence to its existing operation event (no duplicate event stream); BusinessTaskMapper public pending projection and diagnosis display provider; navigation count aggregation. Existing unrelated stage timing, deletion and lead responsibility edits preserved.
- Frontend: AccountDiagnosisForm/AccountProfilePanel/AccountReviewRecord/DiagnosisReminder/mediaAccountProfile; new WeeklyDiagnosisMetrics; new actual-panel synthetic HTTP fixture/browser test. Startup content and submit behavior retained. No Vue employee page duplication or new dependencies.
- SQL/docs: V346__weekly_account_diagnosis.sql; test_weekly_account_diagnosis.py; weekly-account-diagnosis API and related existing API/deployment/UI documentation. Uses existing enabled infra_boolean_string dictionary (local read-only value/status/Chinese HEX verified).
- Verification: final Maven targeted 90 tests, 0 failures/errors/skips (weekly periods/service/security/SQL binding-parser/metrics; existing diagnosis/profile/task/navigation/Lead-flow transaction tests), compiled application module. Workbench typecheck and final production build passed (existing large-chunk advisory only). Actual Chrome 1440/390 form flow, required fields, zero likes, unbound submit, node-load retry, history/current metric loading, fixed footer/no horizontal overflow and unchanged startup submission passed; final screenshots visually inspected. Browser transport is isolated synthetic HTTP, not a real account.
- Database: retained controlled schemas weekly_diagnosis_20261022160327501137_* verify initial/replay/prerequisite failure/partial recovery/wrong structure/ledger failure rollback/Chinese HEX; actual publication/member/live-conversion/task-projection SQL verified. Local ruoyi-vue-pro only non-destructive V346 tables/ledgers applied and repeated, controlled/local shape compared, prior entries and role_menu signatures unchanged; both new tables remain empty. No historical data repair/grant change. SQL logs in TEMP/zsjos-weekly-sql-local.log and zsjos-weekly-sql-final.log.
- Evidence: TEMP/zsjos-weekly-acceptance-test.log; zsjos-weekly-build-delivery.log; zsjos-weekly-browser-delivery.log; TEMP/zsjos-weekly-diagnosis/*.png. First H2 query failure required adding MySQL JSON test aliases and portable CASE/equality; real MySQL and final H2 regression now pass. Original 5174 dev server stopped externally during verification; own isolated 5186 fixture server was used, without restarting original/shared services.
- Limitations/remaining: target deployment, runtime scheduler over real periods, real authenticated API/real-account browser acceptance remain unverified and require target environment operations/authorization. Old periods without reliable historical status evidence explicitly report unavailable; no guessed backfill. Deploy V346, backend, then Workbench.
- Dependencies/integration: None beyond existing facilities; no branch/worktree operations. Ownership released after this delivery; unrelated dirty work retained.

## Registration update — 2026-10-25（北京时间）
- Goal: 合并诊断填写入口为账号复盘记录标题旁单一按钮，移除卡片填写及修订按钮；启动诊断、历史和已确认周期规则不变。
- Environment: local（环境变量/环境文件未指定，按规则回退）；branch/worktree/HEAD 同原注册。
- Owner: Codex root. Ownership: AccountProfilePanel.tsx、AccountReviewRecord.tsx、weekly-account-diagnosis.py、diagnosis-loading.py（已有浏览器定位器同步）、ui-guidelines.md 和本记录。
- Preserve concurrent loading/history/deletion changes. Dependencies/integration: None. Verify typecheck and actual-panel desktop/mobile browser flows; no backend/SQL/deployment changes.

## Delivery — 2026-10-25 14:11:39 +08:00（北京时间）
- Branch/worktree/HEAD: main, D:\ZSJ-OS, 404387de566fcb9abf4b264f0e005bf0566ea209（同原注册）。
- Goal/result: 周期诊断唯一填写入口移动到账号复盘记录标题旁，按钮为填写诊断表；移除侧栏重复入口、各周期卡片填写及基于最新记录修订按钮。卡片历史查看、启动诊断、权限/待办开放条件和最早可填周节点逻辑保留；其他记录的填写入口不变。
- Changed files: frontend/workbench/src/components/AccountProfilePanel.tsx; AccountReviewRecord.tsx; test/weekly-account-diagnosis.py; test/diagnosis-loading.py; docs/ui-guidelines.md; 本记录。保留既有未提交加载、历史弹窗、删除等修改，无后端/SQL/依赖变更。
- Verification: npm run typecheck passed; git diff --check passed. Actual Chrome 1440/390 synthetic API fixture passed: unique title-adjacent entry, no per-card fill/revision entry, history buttons retained, 7/14/28 composite form submission/required/zero values/unbound/retry/startup regression. Loading browser regression passed slow/duplicate/retry/future/empty/denied/close/account-switch/late-response/deep-link cases. History-button assertion uses accessible-name suffix because Ant Design icon contributes its name.
- Evidence: TEMP/zsjos-diagnosis-entry-typecheck.log; zsjos-diagnosis-entry-browser.log; zsjos-diagnosis-entry-loading.log; TEMP/zsjos-weekly-diagnosis/weekly-entry-1440.png and weekly-entry-390.png visually inspected.
- Scope/limitations: local UI verification only, no deployment or real authenticated acceptance; no production build required for this entry/layout-only adjustment. No branch/worktree, commit/push, database, permission or shared service operations. Dependencies/integration: None. Ownership released.

## Registration update — 2026-10-25 history alignment
- Goal: 将复盘三张诊断卡的历史按钮对齐到底部；保持移动端自然高度、填写和历史行为。Owner: Codex root；环境/分支/工作树同原注册。
- Ownership: AccountReviewRecord.tsx、styles/pages/media-students.css、weekly-account-diagnosis.py、ui-guidelines.md、本记录。保留已有未提交改动。Verify: typecheck、不同长度内容的1440/390真实浏览器几何/溢出检查。Dependencies/integration: None.

## Delivery — 2026-10-25 14:14:48 +08:00（北京时间）
- Branch/worktree/HEAD: same registration, main / D:\ZSJ-OS / 404387de566fcb9abf4b264f0e005bf0566ea209.
- Goal/result: 复盘三张诊断卡的历史按钮底部对齐；内容区域使用剩余网格高度，记录操作区 margin-block-start:auto。无固定高度，移动单列仍自然撑开。不改变权限、填写、历史或后台逻辑。
- Files: AccountReviewRecord.tsx adds root class; styles/pages/media-students.css adds diagnosis-card-scoped flex layout; weekly-account-diagnosis.py verifies unequal content alignment/no overflow; docs/ui-guidelines.md; this record. Unrelated edits preserved.
- Verification: npm run typecheck and scoped git diff --check passed. Actual Chrome 1440/390 existing composite form/startup/retry suite passed; added unequal diagnostic requirement content test confirms desktop history button Y spread <=1px and mobile no horizontal overflow. Additional inspected desktop screenshot measured all three Y=907.546875.
- Evidence: TEMP/zsjos-history-align-typecheck.log; zsjos-history-align-browser.log; TEMP/zsjos-weekly-diagnosis/history-aligned-desktop.png visually inspected. Local synthetic browser evidence, not deployment or authenticated real-account acceptance.
- Dependency/integration: None. No SQL/dependency/branch/commit/push/shared service changes. No production build applicable to this local layout-only change. Ownership released.
