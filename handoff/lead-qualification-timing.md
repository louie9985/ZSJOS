# Lead Qualification Timing

- Workstream ID: `lead-qualification-timing`
- Goal: 修正客资待首跟与有效性判定阶段的任务提示、截止时间展示和收件箱筛选口径。
- Non-goals: 不改变接单、首次跟进、判定任务创建、超时挂起、时限配置数值或历史任务截止时间；不执行数据库迁移。
- Branch: `codex/lead-qualification-timing`
- Worktree: `D:\ZSJ-OS-worktrees\lead-qualification-timing`
- Base commit: `1c4605d276aea9a8ae001e0fef1f01ef2589de17`
- Target branch: `main`
- Owner: Codex
- Dependencies: 复用现有 ZSJOS Lead handlingStage、收件箱配置和业务任务能力；不新增 npm 或 Maven 依赖。
- Integration order: 独立完成并验证后，由用户另行确认提交和合入 `main`。
- Ownership scope: `yudao-module-zsjos` 客资处理阶段筛选契约、查询和测试；WorkBench 客资详情阶段提示；MySQL V047、ZSJOS 初始化种子；直接相关业务、API、权限流文档；本 handoff。
- Verification plan: ZSJOS 聚焦测试与模块构建；WorkBench test/typecheck/build；桌面和移动端浏览器检查；SQL 语法、数据范围、版本快照和可重复性审查；不执行真实数据库迁移。
- Status: ready-to-merge
- Final commit: `bd19734cdc42ec1ab1acb6291c0480f173aa8ced`

## Delivery Entries

### 2026-08-13 16:54 CST

- Branch: `codex/lead-qualification-timing`
- Worktree: `D:\ZSJ-OS-worktrees\lead-qualification-timing`
- HEAD commit: `1c4605d276aea9a8ae001e0fef1f01ef2589de17` (uncommitted implementation)
- User goal: 修复刚接单客资误显示“待完成有效性判定”且判定截止为空的问题，并明确待首跟与判定计时口径。
- Key decisions: 有效性判定仍从当前归属周期首次跟进成功时间起算；`qualificationStatus=pending` 保持兼容，任务提醒按服务端 `handlingStage` 和对应截止时间展示；新增 `handling_stage` 收件箱条件；V047 只升级仍完全匹配旧系统默认的 submitter/owner 配置，自定义配置保留；不执行真实数据库迁移、提交、推送或合并。
- Execution or analysis result: 完成后端阶段条件编译、分页过滤和统计 CASE 投影；工作台详情在待首跟阶段显示首跟截止，在判定阶段且截止非空时显示判定提醒；新环境默认筛选和 V047 已拆分两阶段；业务、API、架构和迁移说明已同步。
- Changed files: ZSJOS LeadMapper/LeadConstants/LeadInboxFilterQuery/LeadInboxFilterConfigServiceImpl/LeadManagementServiceImpl 及两组测试；WorkBench LeadManagementPage、leadManagement helper/test；V047、bootstrap seed、migration README；三份直接相关文档；本 handoff。
- Verification evidence: `git diff --check` passed；PowerShell 只读解析 bootstrap 两个 JSON 成功，选项分别为 `all,unassigned,pending_acceptance,public_pool,first_follow_pending,qualification_pending` 和 `all,first_follow_pending,qualification_pending`；Maven 聚焦测试 36/36 passed（LeadInboxFilterConfigServiceImplTest、LeadManagementServiceImplTest）；WorkBench 聚焦测试 15/15 passed、`npm run typecheck` passed、`npm run build` passed（保留既有大 chunk warning）。未执行真实数据库迁移；未完成认证浏览器桌面/移动交互检查。
- Dependency or integration impact: 无新增 npm/Maven 依赖；V047 依赖 V005/V014/V046，迁移记录两种 schema version，需在受控环境按序执行；工作区使用过主工作区依赖的临时 junction，已删除且主工作区依赖仍存在。
- Remaining work: 需要用户另行确认后提交、合入 `main`、执行真实迁移或进行带认证的浏览器验收；当前分支保持 `in-progress`，未标记 `ready-to-merge`。

### 2026-08-13 16:57 CST

- Branch: `codex/lead-qualification-timing`
- Worktree: `D:\ZSJ-OS-worktrees\lead-qualification-timing`
- HEAD commit: `bd19734cdc42ec1ab1acb6291c0480f173aa8ced`
- User goal: 将已验证的客资判定阶段修复合并至主分支。
- Key decisions: 以独立功能提交保留工作流历史；合并前将状态记录为 `ready-to-merge`；主工作区未提交的 PersonMapper/DAL 测试/其他 handoff 与本工作流无文件重叠，合并时必须原样保留；不推送或执行 V047。
- Execution or analysis result: 功能代码、测试、V047、默认种子和直接相关文档已提交为 `bd19734cdc`，分支具备合入 `main` 的条件。
- Changed files: 本 handoff 状态和本条交付记录。
- Verification evidence: 功能提交前后 `git diff --check` passed；此前 Maven 聚焦测试 36/36、WorkBench 聚焦测试 15/15、typecheck 和生产构建均通过；种子 JSON 解析通过。
- Dependency or integration impact: V047 仍未执行；无新增依赖；合入后需在 `main` 重跑受影响检查并记录集成提交。
- Remaining work: 合并到 `main`、执行集成态验证、记录 merge commit 和 merged 状态；真实迁移、推送和认证浏览器检查仍需另行处理。
