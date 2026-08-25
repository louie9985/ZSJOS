# HRM 模块迁移到 Workbench

## Active delivery: flatten Workbench navigation and standardize HRM ProTable UI

- Workstream ID: `main-workbench-hrm-pro-table`
- Goal: 在保留 Admin Vue 在线可用的前提下，将整个 Workbench 导航固定为服务端一级分类与二级叶子平铺，并将 HRM 表格统一迁移到 Ant Design ProTable、加宽 HRM 表单。
- Non-goals: 下线、删除或替换 `frontend/admin` HRM 页面；迁移非 HRM 业务表格；改造后端 API 或菜单数据；引入新依赖；执行数据库清理、分支/提交/发布或部署。
- Branch: `main`
- Worktree: `/Users/louie/Documents/ChatGPT/ZSJOS 2`
- Base commit: `02961f62360e96a85c734c0b87c34a43dea77309` 加现有用户未提交改动
- Target branch: `main`
- Ownership scope: `frontend/workbench/src/main.tsx`、导航布局与主题布局配置、菜单测试、HRM 页面和组件、HRM 共享样式、直接受影响的 Workbench 文档，以及本文件。
- Owner: Codex `/root`
- Dependencies: 现有 `@ant-design/pro-components`、服务端 permission response、HRM 后端公开 API 与按钮权限；不新增依赖。
- Integration order: 导航布局收敛 -> ProTable 共享配置 -> HRM 主列表 -> HRM 详情/编辑子表 -> 表单宽度 -> 文档与轻量验证。
- Verification plan: Workbench `npm run typecheck`、相关 `npm test`、`npm run build`、`git diff --check`，菜单单测和 HRM 关键页面桌面/移动端冒烟；不执行重型性能或全量浏览器回归。

## 现状

`frontend/workbench` 已完成 HRM 主要 portal 与管理页面的增量迁移，当前已注册并可构建的 HRM 页面超过 30 个；Admin Vue 保持在线，作为并存的完整管理端。

**迁移模式**：沿用 EAM 已验证的单文件页面组件 + `permissions` 入参 + 本地状态 + `listVersion` 防竞态。API 走集中式 `api.hrm.*`，类型集中在 `services/api.ts`，状态枚举/字典 key 集中在 `services/hrm.ts`，样式集中在 `styles/pages/hrm.css`（单一样式文件，非逐页）。

## 已完成页面（当前 Workbench 范围）

### 批次 1 · 考勤域
| 页面 | 文件 | 说明 |
|---|---|---|
| 员工考勤报表 | `HrmMyAttendancePage.tsx` | 打卡记录 + 请假申请（提交/撤销），走 `/hrm/portal/attendance/*` |
| 打卡记录管理 | `HrmClockPage.tsx` | 全员打卡 CRUD、批量删除与导出，`/hrm/attendance/clock/*` |
| 请假记录 | `HrmLeavePage.tsx` | 只读查询 + 详情抽屉，审批走 BPM |
| 月度考勤汇总 | `HrmAttendanceMonthPage.tsx` | 全员出勤统计、每日明细、汇总与打卡概况导出 |

### 批次 2 · 薪资域
| 页面 | 文件 | 说明 |
|---|---|---|
| 我的工资条 | `HrmMySalarySlipPage.tsx` | 卡片流 + 未读标记，`/hrm/portal/salary/slip/*` |
| 薪资档案 | `HrmSalaryEmployeeInfoPage.tsx` | 定薪/调薪、批量调薪、Excel 导入、调薪记录与明细 |
| 月度工资表 | `HrmSalaryMonthRecordPage.tsx` | 核算准备、员工明细、导入、模板、动态工资项与历史只读 |
| 发放记录 | `HrmSalarySendRecordPage.tsx` | 工资条批次发放、待发员工、批次详情与阅读状态 |

### 批次 3 · 绩效域
| 页面 | 文件 | 说明 |
|---|---|---|
| 我的绩效 | `HrmMyPerformancePage.tsx` | 指标填写/评分/确认/申诉，`/hrm/portal/performance/*` |
| 我的绩效档案 | `HrmMyPerformanceHistoryPage.tsx` | 只读历史结果 |
| 绩效计划 | `HrmPerformancePlanPage.tsx` | 完整计划配置、范围/阶段/模板/薪资同步与状态流转 |
| 绩效档案 | `HrmPerformanceAssessmentPage.tsx` | 全员考核查询 + 详情 |

### 批次 4 · 员工档案域
| 页面 | 文件 | 说明 |
|---|---|---|
| 我的档案 | `HrmMyProfilePage.tsx` | 查看 + 编辑个人字段，`/hrm/portal/employee/*` |
| 员工档案管理 | `HrmEmployeePage.tsx` | 完整 CRUD、批量建档/删除/提醒、导入导出、材料附件、异动历史与员工异动 |
| 员工设置 | `HrmEmployeeConfigPage.tsx` | 新建员工字段、员工档案字段显示/编辑配置 |

### 批次 5 · 工作台首页 + 社保
| 页面 | 文件 | 说明 |
|---|---|---|
| 个人工作台 | `HrmPortalHomePage.tsx` | 个人概览 + 待办 + 当月日历 |
| 我的社保 | `HrmMyInsurancePage.tsx` | 社保记录 + 明细，`/hrm/portal/insurance/*` |

### 增量补齐
| 功能 | 文件 | 说明 |
|---|---|---|
| 员工档案子表 | `EmployeeSubTabs.tsx` | 合同、证件、教育、工作、培训、联系人、工资卡、离职信息、材料附件、异动记录 |
| 历史工资表 | `HrmSalaryMonthRecordPage.tsx` | 复用月度工资接口按历史状态查询 |
| 招聘完整流程 | `HrmRecruitCandidatePage.tsx` | 候选人资料/简历、面试、淘汰、转档案、确认入职与清理预览 |
| 社保月表流程 | `HrmInsuranceMonthRecordPage.tsx` | 员工明细、方案调整、增员、停保、归档与历史只读 |
| 我的绩效申诉 | `HrmMyPerformancePage.tsx` | 使用后端 `/handle-appeal` 正确命令 |

## 关键决策

1. **不需要新增菜单 SQL**。HRM 菜单与权限已由 `script/sql/mysql/migrations/V058__hrm_fms_metadata_and_data_cleanup.sql` 装入 `system_menu`（ID 段 601476+）。前端只做 `RENDERABLE_APP_ROUTES` 过滤。**切勿**新增 HRM 菜单迁移，会与 V058 冲突。

2. **员工端沿用 `hrm:portal:query` 单权限点**。代价是无法单独控制"能看工资条但不能看绩效"。若需拆分，再补一次菜单迁移。

3. **后端下发可操作标志驱动状态机，而非前端硬编码**。绩效计划用 `operationType`/`scoringReady`/`archiveReady`，员工用 `canConfirmTarget`/`canHandle`/`canScore`。员工异动按钮由 `employeeActionsOf()` 按入职状态+聘用形式计算。

4. **`portal` 与管理端 API 分开挂**在 `api.hrm.portal.*` 和 `api.hrm.*`，避免调用点看不出打的是自己的数据还是全员数据。

## 共享基建

- `services/hrm.ts` — 全量状态枚举（请假审批/绩效/员工/异动/离职）、字典 key、label/color 映射、格式化工具（金额/分钟数/年月）
- `services/api.ts` — `api.hrm.*` 命名空间 + 类型
- `styles/pages/hrm.css` — 单一共享样式（比 EAM 的逐页样式更收敛）
- 复用组件：
  - `components/HrmEmployeePicker.tsx` — 员工远程搜索选择器（`/hrm/employee/simple-page`）
  - `components/DeptTreeSelect.tsx` — 部门树选择器（`/system/dept/simple-list`）
  - `components/SalaryOptionTable.tsx` — 工资项明细表（含一层 children 摊平）
  - `components/QuotaTable.tsx` — 绩效指标可编辑/只读表 + 评分阶段表
  - `components/EmployeeModals.tsx` — 新增/编辑/异动/离职弹窗（五类异动共享一个职级变更表单）

## 需注意的契约细节

- 员工端 `portal/attendance/clock/list`、`leave/list`、`salary/slip/list`、`insurance/record/list` 返回**数组不分页**，管理端才走分页。API 方法已按此封装。
- portal 请假 `cancel` 走 **PUT body**（`{id, reason}`），非 DELETE。
- 工资条 `read` 用 **query 逗号拼接**（`ids=1,2,3`），非 body。
- 员工 `confirm-entry` 只需传 `{id}`。
- antd DatePicker 在本版本**不支持 `valueFormat`**，需手动 `dayjs().valueOf()` 转换（`EmployeeModals.tsx` 里的 `backfillDates`/`encodeDates` 封装了这一点）。
- 后台时间字段统一是**毫秒时间戳**（number），不是 ISO 字符串。

## 验证

```bash
cd frontend/workbench
npm run typecheck  # 通过
npm test -- --run  # 59 个文件、356/356 通过
npm run build      # 通过；仅保留 Vite chunk 体积提示
```

样式守卫 `src/styles/styles.guard.test.ts` 用 `node:fs` 递归扫目录，`hrm.css` 已自动纳入；注意 `hrm.css` 里已用 `--crm-font` 等 token，不要写裸 px 字号（会被守卫报错）。

## 剩余验证 / 后续

- V058 HRM 页面菜单、`RENDERABLE_APP_ROUTES`、`RouteHost` 和本次按钮权限已完成静态对账，当前审计范围内没有已知迁移漏项。
- Admin Vue HRM 保持在线并与 Workbench 长期并存；本次没有修改、重定向或删除 Admin Vue HRM 文件。
- Playwright mock 冒烟已覆盖员工档案/材料附件、打卡记录和月度考勤桌面视图，以及 390x844 员工档案移动视图；没有发现白屏或控件重叠。由于本机没有可安全复用的认证会话，真实 HRM API 浏览器冒烟仍未验证；上线前应使用具备对应菜单权限的测试账号做一次只读检查。

## Delivery Entry - 2026-08-25 00:10:00 +08:00

- Workstream ID: `main-workbench-hrm-complete`
- Branch: `main`
- Worktree: `/Users/louie/Documents/ChatGPT/ZSJOS 2`
- HEAD commit: `02961f62360e96a85c734c0b87c34a43dea77309`（未创建提交）
- User goal: Admin Vue 保持在线，增量完成 HRM Workbench 迁移，并采用轻量验证。
- Key decisions: 不删除或下线 Admin Vue；复用现有 HRM 后端 API；将 V058 的员工设置与历史工资表注册为 Workbench 页面；优先修复构建阻断和确定性业务错误。
- Execution or analysis result: 修复 HRM 子表组件类型/依赖并接入员工详情；新增员工设置页和历史工资表路由；修正绩效处理申诉 API；补齐候选人增改删、社保月表创建下月/删除；修复并行 EAM/FMS 最小编译错误以恢复整体构建。
- Changed files: `frontend/workbench/src/components/EmployeeSubTable.tsx`; `frontend/workbench/src/components/EmployeeSubTabs.tsx`; `frontend/workbench/src/pages/HrmEmployeePage.tsx`; `frontend/workbench/src/pages/HrmEmployeeConfigPage.tsx`; `frontend/workbench/src/pages/HrmSalaryMonthRecordPage.tsx`; `frontend/workbench/src/pages/HrmRecruitCandidatePage.tsx`; `frontend/workbench/src/pages/HrmInsuranceMonthRecordPage.tsx`; `frontend/workbench/src/pages/HrmMyPerformancePage.tsx`; `frontend/workbench/src/layouts/RouteHost.tsx`; `frontend/workbench/src/constants.ts`; `frontend/workbench/src/services/api.ts`; `frontend/workbench/src/services/hrm.ts`; `frontend/workbench/src/services/menu.test.ts`; directly affected EAM/FMS compile files; this handoff file.
- Verification evidence: `npm run typecheck` passed; `npm test -- --run` passed 58 files/353 tests; `npm run build` passed with only existing chunk-size warning.
- Dependency or integration impact: no new dependency, no Admin Vue removal, no database execution, no branch/commit/deployment. Runtime API and permission behavior still need authenticated browser smoke checks.
- Remaining work: complete the complex salary, insurance employee-detail, payroll batch, performance advanced configuration, HRM import/export and file/history workflows listed above; then perform focused browser verification.

## Delivery Entry - 2026-08-25 00:16:00 +08:00

- Workstream ID: `main-workbench-hrm-complete`
- Branch: `main`
- Worktree: `/Users/louie/Documents/ChatGPT/ZSJOS 2`
- HEAD commit: `02961f62360e96a85c734c0b87c34a43dea77309`（未创建提交）
- User goal: 继续补齐 HRM Workbench 能力，同时保持 Admin Vue 在线。
- Key decisions: 先开放后端已有的单员工薪资 `update` 命令，批量调薪/Excel 导入继续保持独立后续范围；不复制或修改 Vue 实现。
- Execution or analysis result: Workbench 薪资档案详情新增定薪/调薪入口，支持记录类型、调整原因、生效时间、工资项金额和备注提交。
- Changed files: `frontend/workbench/src/services/api.ts`; `frontend/workbench/src/layouts/RouteHost.tsx`; `frontend/workbench/src/pages/HrmSalaryEmployeeInfoPage.tsx`; this handoff file.
- Verification evidence: `npm run typecheck` passed; `npm test -- --run` passed 58 files/353 tests; `npm run build` passed with only existing chunk-size warning。
- Dependency or integration impact: no new dependency, database execution, Admin Vue change, branch/commit/deployment. Authenticated API/browser verification remains pending。
- Remaining work: 批量调薪、Excel 导入、发放批次、社保员工明细、工资表明细与导入、绩效高级配置及 HRM 文件/历史投影仍未完成。

## Delivery Entry - 2026-08-25 01:17:36 +08:00

- Workstream ID: `main-workbench-hrm-complete`
- Branch: `main`
- Worktree: `/Users/louie/Documents/ChatGPT/ZSJOS 2`
- HEAD commit: `02961f62360e96a85c734c0b87c34a43dea77309`（未创建提交）
- User goal: Admin Vue 继续在线，不做复杂重型测试，完整补齐已纳入范围的 HRM Workbench 增量迁移。
- Key decisions: 保留双前端长期并存；只复用 HRM/System/Infra/BPM 现有公开 API；页面和操作入口全部按服务器菜单/button permission 控制；字典字段读取后端字典，员工材料类型对齐后端固定枚举；不新增依赖、菜单 SQL 或后端聚合接口。
- Execution or analysis result: 补齐绩效计划全量配置与考核模板必填契约；完成候选人简历、面试、淘汰、转档案和入职流程；完成社保员工明细、工资核算/导入/发放和薪资档案流程；完成员工批量建档、提醒、删除、导入导出、材料附件和异动历史；完成打卡批量删除、打卡导出及月度考勤两类导出；V058 HRM 页面路由和本次按钮权限静态对账未发现剩余漏项。
- Changed files: `frontend/workbench/src/services/api.ts`; `frontend/workbench/src/services/hrm.ts`; `frontend/workbench/src/layouts/RouteHost.tsx`; `frontend/workbench/src/components/PerformanceAssessmentConfigFields.tsx`; `frontend/workbench/src/components/HrmPerformancePlanForm.tsx`; `frontend/workbench/src/components/HrmRecruitCandidateModals.tsx`; `frontend/workbench/src/components/HrmEmployeeBulkModals.tsx`; `frontend/workbench/src/components/HrmEmployeeMaterialFiles.tsx`; `frontend/workbench/src/components/HrmEmployeeChangeRecordList.tsx`; `frontend/workbench/src/components/EmployeeSubTabs.tsx`; `frontend/workbench/src/pages/HrmPerformancePlanPage.tsx`; `frontend/workbench/src/pages/HrmAssessmentTemplatePage.tsx`; `frontend/workbench/src/pages/HrmRecruitCandidatePage.tsx`; `frontend/workbench/src/pages/HrmSalaryEmployeeInfoPage.tsx`; `frontend/workbench/src/pages/HrmSalaryMonthRecordPage.tsx`; `frontend/workbench/src/pages/HrmSalarySendRecordPage.tsx`; `frontend/workbench/src/pages/HrmInsuranceMonthRecordPage.tsx`; `frontend/workbench/src/pages/HrmEmployeePage.tsx`; `frontend/workbench/src/pages/HrmClockPage.tsx`; `frontend/workbench/src/pages/HrmAttendanceMonthPage.tsx`; this handoff file.
- Verification evidence: `npm run typecheck` passed; `npm test -- --run` passed 59 files/356 tests; `npm run build` passed with only the existing chunk-size warning; `git diff --check` passed; V058 menu/RouteHost/button-permission static reconciliation passed; Playwright rendered the Workbench login page, with only an unrelated favicon 404. Authenticated HRM page smoke could not run because no safe login session was available.
- Dependency or integration impact: no Admin Vue HRM changes, backend/schema/menu migration, dependency addition, database execution, branch/worktree operation, commit, push, deployment, or shared-service reconfiguration. Concurrent EAM/FMS changes were preserved.
- Remaining work: authenticated read-only browser smoke for representative HRM pages when a suitable test session is available; no known implementation gap remains in the audited HRM migration scope.

## Delivery Entry - 2026-08-25 01:26:00 +08:00

- Workstream ID: `main-workbench-hrm-complete`
- Branch: `main`
- Worktree: `/Users/louie/Documents/ChatGPT/ZSJOS 2`
- HEAD commit: `02961f62360e96a85c734c0b87c34a43dea77309`（未创建提交）
- User goal: 在保持轻量验证的前提下，为完整 HRM Workbench 迁移补足关键页面冒烟证据。
- Key decisions: 使用本地 mock 响应验证渲染与响应式布局，不写入真实 HRM 数据、不伪造真实 API 通过结论；认证后的真实接口检查保留为上线前验证项。
- Execution or analysis result: 桌面端验证员工档案工具栏、详情和材料附件页签、打卡列表、月度考勤及双导出入口；移动端 390x844 验证员工档案工具栏、页签和横向表格，没有发现白屏、按钮或文字重叠。新增弹窗改用 Ant Design 6 的 `destroyOnHidden`。
- Changed files: `frontend/workbench/src/components/HrmEmployeeBulkModals.tsx`; `frontend/workbench/src/components/HrmEmployeeMaterialFiles.tsx`; this handoff file.
- Verification evidence: Playwright mock smoke rendered `/hrm/employee/list`, `/hrm/attendance/clock`, `/hrm/attendance/month`; desktop and 390x844 snapshots passed visual inspection. Mock 会话仅有预期 WebSocket 连接错误、既有 Drawer/Modal 弃用提示和 favicon 404；未发现页面运行时异常。
- Dependency or integration impact: None; no real account, permission, API data, Admin Vue, dependency, database, branch, commit, deployment, or shared-service change.
- Remaining work: authenticated read-only API smoke when a suitable test session is available; no known implementation gap remains.

## Delivery Entry - 2026-08-25 10:00:45 +08:00

- Workstream ID: `main-workbench-hrm-pro-table`
- Branch: `main`
- Worktree: `/Users/louie/Documents/ChatGPT/ZSJOS 2`
- HEAD commit: `02961f62360e96a85c734c0b87c34a43dea77309`（未创建提交）
- User goal: 整个 Workbench 菜单统一为一级分类与二级叶子平铺、取消下拉；HRM 表格统一使用 Ant Design ProTable 高级能力；HRM 表单加宽；Admin Vue 继续在线；只做轻量验证。
- Key decisions: 服务端 permission response 继续保存完整菜单树和权限事实，展示层递归收集可见叶子并平铺；仅保留 `side` 与 `top` 两种始终显示二级侧栏的布局；HRM 管理主表启用当前页搜索、刷新、列设置、密度、全屏和列状态持久化，详情/编辑子表使用紧凑 ProTable；表单使用 720-1040px 档位并限制为 96vw；不迁移非 HRM 表格。
- Execution or analysis result: 桌面与移动导航均不再产生嵌套下拉；52 处 HRM 普通 Table 全部迁移到共享 `HrmProTable`，其中 30 个主列表启用高级工具栏；共享封装兼容既有 Ant Table 原始值 render 契约；HRM Modal/Drawer 表单宽度完成响应式加宽；直接约束和架构文档已同步；Admin Vue 未修改。
- Changed files: `frontend/workbench/src/components/HrmProTable.tsx`; `frontend/workbench/src/main.tsx`; `frontend/workbench/src/constants.ts`; `frontend/workbench/src/components/SettingsDrawer.tsx`; `frontend/workbench/src/layouts/MobileNavDrawer.tsx`; `frontend/workbench/src/styles/layout.css`; `frontend/workbench/src/styles/pages/hrm.css`; directly affected `frontend/workbench/src/pages/Hrm*.tsx` and HRM/Employee/Performance shared components; `frontend/workbench/AGENTS.md`; `frontend/workbench/docs/ui-guidelines.md`; `docs/architecture/data-and-permission-flow.md`; this handoff file.
- Verification evidence: `npm run typecheck` passed; focused Vitest 3 files/42 tests passed; `npm run build` passed with only existing chunk-size warning; `git diff --check` passed; static audit found 0 ordinary HRM Table tags, 52 HrmProTable tags, 30 advanced main tables and 0 runtime dropdown-layout references. Playwright mock smoke at desktop and 390x844 confirmed flat navigation, ProTable search/reload/density/column-setting/fullscreen controls, row selection/pagination, and the widened employee form; no post-fix runtime exception was observed.
- Dependency or integration impact: No dependency addition, Admin Vue change, backend/API/schema/menu migration, database execution, branch/worktree operation, commit, push, deployment, or shared-service reconfiguration. Existing FMS/EAM and other user changes were retained. The existing local Workbench server remains available at `http://127.0.0.1:5174`.
- Remaining work: 针对真实权限和 HRM API 响应的认证只读冒烟仍是上线前检查项；mock 浏览器环境只保留预期 WebSocket failure、favicon 404 和既有 Ant Design deprecation warnings。
