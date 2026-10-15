# HRM 试岗与小数司龄

## Registration
- Environment: local（ZSJOS_AGENT_ENV 与 /etc/zsjos/agent-environment 均未设置）。
- Workstream: local-hrm-trial-tenure-20261014; owner: current Codex chat /root.
- Goal: 非正式聘用新增字典状态试岗、试岗期天数、计划及实际结束日期；司龄按实际天数 / 365 四舍五入两位，离职截至实际离职日；核实数值消费者。
- Confirmed: 入职日期加试岗天数（10 月 14 日加 3 天为 10 月 17 日）；司龄按上述口径。
- Non-goals: 自动状态切换、新增到期提醒、权限变更、历史试岗数据补造、共享服务操作、提交或发布。
- Branch: main; worktree: D:/ZSJ-OS; base: e07efc9d6baa5d40b5ec07b78f6743b0d1b2915e; target/integration: None.
- Ownership: backend/yudao-module-hrm 员工实体/VO/生命周期/枚举/司龄工具/相关 controller 与 home 统计及聚焦测试；frontend/admin HRM 员工 API/常量/表单/详情/portal 与 hrm 测试；script/sql/mysql/schema/hrm.sql、migrations/hrm/V004__employee_trial_and_decimal_tenure.sql、verify/hrm-trial-tenure.py；docs/api/hrm-manual-lifecycle.md；本记录。
- Dependencies: 既有 System 字典与 HRM V003；无新增依赖。Git 现有修改均在本次 HRM 源码范围外。
- Verification: 聚焦 Java 服务/DTO/日期/生命周期测试，Vue 静态检查与真实组件浏览器桌面和移动检查，MySQL 8 隔离 schema 的首次/重放/部分恢复/失败不登记/UTF-8 验证，共享开发库只读预检。共享库实际升级在具体脚本与影响可审阅后确认。

## Scope update
- Include Admin salary/employee-info status tabs and performance/plan/form/PerformancePlanScopeForm.vue: new trial status must retain dictionary labels in dependent selectors. Existing performance static label map conflicts with the repository dictionary contract; use the authoritative dictionary for these affected options.
- Live read-only preflight: local yudao-mysql / ruoyi-vue-pro is MySQL 8, HRM V001-V003 applied, 46 tenant-1 employees; company_age INT, no trial columns, status dictionary 1-8. V004 is an upgrade; historical migrations remain unchanged.


## Source delivery and verification
- Implemented: informal trial dictionary code 9 and permitted state/convert-to-full-time/tab/filter consumers; three nullable trial fields in archive/portal/import/API, explicit null persistence and lifecycle snapshots. Date button adds trial days to entry; future actual dates and end-before-entry are rejected. No new state automation or reminder scene.
- Tenure: BigDecimal / DECIMAL(10,2), actual days / 365 HALF_UP, zero floor, actual leave cutoff for both archive and portal responses. Age remains whole calendar years. Team chart keeps date-first grouping and decimal fallback conversion.
- Numeric consumer audit: archive write/read, personal portal read, Admin archive/portal display, team chart fallback. No salary/attendance/performance or cross-module companyAge business calculation found. Workbench employee notification reads only id.
- Java: Maven reactor focused tests passed, 69 total (HrmEmployeeServiceImplTest 51, HrmEmployeeManualLifecycleTest 7, HrmHomeServiceImplTest 9, HrmCompanyAgeTest 2). Random fixtures were updated to explicitly leave new fields null; fixed decimal scale in existing equality assertions.
- MySQL 8: verify/hrm-trial-tenure.py passed fresh HRM schema/V002/V003/V004, V003 upgrade, partial-DDL recovery, prerequisite failure, execution failure, ledger failure; replay preserves dates/status/decimal values/version timestamps, Chinese HEX passed, fresh/upgrade columns equal. Fixtures retained under hrm_trial_verify_502893603000_*; real ruoyi-vue-pro only read.
- Browser: actual Vue components with synthetic API adapter passed at 1440 and 390 widths: dictionary trial, calculate dates, independently enter actual date, save/reopen/clear, decimal display, no overflow. Existing hrm-manual-browser.py also passed correction/plan/reminder regression. Screenshots visually inspected; trial actual-date label widened locally to prevent desktop wrapping. Browser fixtures are not authenticated live acceptance.
- Workbench: hrmNotification.test.ts 2 tests passed; no Workbench source changes.
- Static: scoped git diff --check passed. Admin full vue-tsc using project 8GB heap reached diagnostics and reports only 12 pre-existing duplicate-field errors in src/api/zsjos/withdrawal/index.ts; no HRM diagnostics. Initial default-heap attempt exhausted memory. No new dependency/build configuration/route changes; no production bundle or service operation performed.
- Final real-DB read-only preflight: local yudao-mysql / ruoyi-vue-pro, tenant 1, 46 employees; stored company_age range 0..12, INT; no trial columns, no V004 ledger. V004 is ready for explicit shared-database execution approval. No commit/push, real data update, permission change, restart, or deployment performed.
- Remaining: obtain authorization for V004 against the above local DB, back up affected table/metadata and execute with UTF-8 and postcondition verification; then coordinated backend/Admin runtime rollout and authenticated acceptance separately. Source ownership released; this record may be reused for that continuation.
