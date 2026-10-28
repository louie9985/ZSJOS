# 学员考试时间操作入口清理

- ID: local-student-exam-entry-20261028; owner: Codex; environment: local（环境变量和环境文件未指定）
- Goal: 删除交付端学员详情的修改考试时间入口及其未使用的前端调用。
- Non-goals: 不删除数据库字段、历史值、后端接口、通知任务、权限配置，不影响班级考期和订单商定考试时间。
- Branch: main; worktree: D:/ZSJ-OS; base: 0628f72f2c6208a6cc553f3f29b893de8f4ec697
- Ownership: frontend/workbench/src/pages/RegistrationPages.tsx; frontend/workbench/src/services/api.ts; frontend/workbench/docs/student-exam-date-entry.md; 本记录。
- Dependencies: None; target branch/integration order: None.
- Verification: scoped diff、前端 typecheck、检查可用浏览器验证设施。

## Delivery

- 已删除菜单、弹窗、表单状态、提交处理和唯一前端 API 包装；同步入口说明。
- npm run typecheck 通过；限定文件 git diff --check 通过；源代码无该入口及调用残留。
- 现有 5174 页面夹具 + Chrome：1440/390 宽度学员详情正常打开，未出现修改考试时间文案；隔离夹具不是实际账号验收。
- 后端仍有考试前七天提醒消费，未声称该字段未被使用；未修改后端/数据库/角色权限，未部署、提交。
- Ownership released.

## Scope update — user confirmed retirement
- Retire legacy exam transitions and reminders; preserve first contact, study plan, supervision, history and class calendar.
- Ownership adds service/studentcontact/{StudentContactServiceImpl,StudentContactConstants,StudentExamNoticeScheduler,StudentContactNotifySceneProvider}.java and StudentContactServiceImplTest.java under zsjos; isolated verification outputs in TEMP. No database writes.
- Verify focused Java tests, frontend types and browser fixtures.

- Documentation ownership extension: docs/architecture/data-and-permission-flow.md (only three student-stage bullets, preserve other dirty edits), docs/api/registration-fulfillment-api.md (retirement addendum). User confirmation supersedes old stage contract.

## Expanded delivery
- Removed old exam UI/actions/submission wrapper, scheduler and notify scene registration. Both legacy commands reject with 1900010090; first-contact/study-plan/contact flows preserved. Navigation stops at supervision. Historical late stages retain ordinary contact access, including exam_confirmation.
- Preserved historical fields/data, stored stages and backend endpoint authorization; no role assignments changed. Class/calendar untouched. Updated API and architecture contracts, preserving unrelated edits.
- Verification: focused current-source compilation and 40 StudentContactServiceImplTest tests passed in TEMP/zsjos-exam-retirement-java using existing build dependencies. Original student-media-boundary runner failed from stale/missing cached dependencies; replacement isolated run passed. Workbench typecheck and scoped diff check passed. Chrome fixture 1440/390: details open and retired entry labels absent.
- Limits: no real-account endpoint/startup validation or deployment; no service restart authorized. Current running service may still execute old code until a clean rebuild/restart. Historical queued external notifications not revoked. Deleted scheduler must not remain as stale class in build artifacts.
- Ownership released.
