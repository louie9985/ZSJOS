# 首页待办拆分
- Environment: local (environment variable and identity file absent)
- ID/owner: local-home-task-split-20261029 / Codex
- Goal: 首页业务/其他待办等宽拆分；提交人协助无截止。
- Non-goals: 部署、权限变更、其他工作流。
- Branch/worktree/base: main / D:\ZSJ-OS / ba3e687e678ce92b67f16615244e47007d0892d4
- Ownership: TodayTasksPage.tsx, today-tasks.css, LeadSubmitterActionService.java and its test, test/home-task-split*, docs/api/home-task-split.md, script/sql/mysql/tools/home-task-split.sql, this record.
- Dependencies: existing business task APIs; local tenant 1 scoped pending assist correction.
- Verification: TypeScript, focused tests, desktop/mobile browser fixture, SQL scoped verification.
- Integration: None

## Delivery
- Implemented pending-only 50/50 panels and separate summary counts; independent pagination and scoped reads. Assist creation uses null dueAt; two existing test assertions updated.
- Typecheck passed. Browser fixture passed at 1440/390: split geometry, no done/unscheduled labels, independent pagination, no horizontal overflow. Screenshots in Windows Temp home-task-split-*.png.
- Local Docker yudao-mysql / ruoyi-vue-pro: 86 pending assist records, 57 scheduled corrected, repeat execution leaves zero scheduled. 26 completed records excluded. Backup: Windows Temp/home-task-split-backup.tsv. No text data changed; no schema/ledger changes.
- Java test blocked at module compilation by missing framework.security.core.util and related dependencies; assertions not executed. Real HTTP assist reply flow and Admin browser not verified. Admin today-only query excludes unscheduled by existing contract.
- Application configuration points to 192.168.2.17; no shared database update, deployment, service restart, commit or push. New creation behavior needs backend deployment before runtime acceptance.
- Ownership released.

## Follow-up: pagination visibility
- Same local/main context and owner; reacquire TodayTasksPage.tsx, today-tasks.css, test/home-task-split*, docs/api/home-task-split.md.
- Fix width-driven dashboard exceeding actual tabbed content height. Verify real shell geometry with header/tabs at wide/short desktop and normal/mobile. No database/backend changes.

### Pagination fix verification
- Browser fixture now mounts actual Ant Layout shell classes with header and optional tab strip. Passed 2550x1275 and 1920x900 tabbed, 1440x800 ordinary, and 390x1000 mobile. Both pagination bounds stay inside desktop viewport; content has no outer vertical overflow; page 2 remains clickable and independent.
- Dashboard measures actual content clientHeight with ResizeObserver, adapting to shell resize and status banners; desktop grid uses available height, mobile retains vertical flow.
- Scoped diff check passed. No deployment or database changes. Ownership released.

## Follow-up: repeated assist navigation
- Same local/main context, owner Codex; ownership: TodayTasksPage.tsx, LeadManagementPage.tsx, LeadDetail.tsx, test/lead-assist-navigation*, docs/api/home-task-split.md, this record.
- Preserve existing LeadManagementPage refresh-button removal. Dependencies: retained route location.key and existing navigation guards. No backend/database changes.
- Verify: repeated same-task navigation, drawer reopen, tab reset, different lead, cancellation guard, typecheck and browser fixture.

### Repeated assist navigation delivery
- LeadManagementPage reacts to new location.key for selection/drawer open; LeadDetail accepts optional navigationKey to reselect authorized initial tab without remounting forms. Existing projection and controlled-tab behavior retained.
- TodayTasksPage validates destination cached-page guard before assist navigation; standalone LeadManagementPage now registers its dirty guard. Existing unrelated refresh-button removal preserved.
- Workbench typecheck and scoped diff check passed. Chrome fixture at 1440 and 390 passed same-task repeat, changed internal tab, different-lead transition, drawer close/reopen; desktop real follow-up draft cancellation blocks navigation.
- Fixture uses synthetic API responses with retained route wrapper and real TodayTasksPage/LeadManagementPage/LeadDetail. No real-account/API acceptance, deployment, backend or database changes. Ownership released.
