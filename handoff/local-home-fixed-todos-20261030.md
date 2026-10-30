# 固定待办指标恢复

## 2026-10-30 注册
- Environment: local（环境变量和环境文件未设置）。Owner: root。
- Branch/worktree: main / D:/ZSJ-OS；base: 208abf277065706cbee45164113903eded9e6303。
- Goal: 按用户最新明确例外恢复固定业务待办/其他待办/审批待办及原分组，保留动态指标。
- Non-goals: 分支操作、提交推送、其他业务变更、角色授权。
- Scope: TodayTasksPage及新增HomeTodoSummary组件、专属测试/fixture、直接相关首页样式及home-metrics/菜单覆盖说明；两条由本会话新增的home_business_todo/home_bpm_todo配置仅停用，保留历史。
- Verification: focused tests/typecheck/桌面移动浏览器；SQL scoped before/after与版本检查。
- Contract resolution: 旧设计指标全部配置驱动与用户本轮要求冲突，以当前明确请求恢复三张固定卡；权限/统计不硬编码，沿用API。
- Integration: None。本轮在已经整合功能的当前主工作树局部维护，保留其他未提交内容。
- Scope更新：restore_todo_ui子任务独占HomeMetricCards.tsx，增加可选槽位容量以容纳固定三卡；默认行为保持，不能无提示丢弃额外已发布指标。测试输出/tsbuildinfo保留运行前用户状态。

## 2026-10-30 15:34 北京时间 — 交付
- Branch/worktree/base同注册；HEAD未变，未提交/推送/切分支。root协调restore_todo_ui子任务，仅修改登记范围。
- 从10685998c8的SummaryRegion找回三卡原展示：业务待办=overdue+today+future，分项逾期/今日/未来；其他待办=unscheduled；审批待办使用原bpmTaskPage(todo,page1,size1)计数并跳转BPM_TODO。新增HomeTodoSummary复用原样式，统计失败不伪装0，权限由服务端permission决定。
- TodayTasksPage接回固定卡，保持当前任务取消/其他面板等已合并功能；HomeMetricCards slots=9仅减少占位，超过9仍能滚动查看现有已支持指标。文档同步用户明确的固定三卡例外。
- Changed: frontend/workbench/src/components/HomeTodoSummary.tsx、HomeMetricCards.tsx、pages/TodayTasksPage.tsx、styles/pages/today-tasks.css、相关todayTasks测试及test/home-fixed-todos*fixture/截图；docs/api/home-metrics.md、docs/design/home-metrics.md、docs/frontend/zsjos-menu-coverage.md；本记录。
- 数据库：本地Docker yudao-mysql/ruoyi-vue-pro、tenant_id=1，仅本会话创建的IDs2/3(home_business_todo/home_bpm_todo)在creator/version/状态条件下停用，影响2行：enabled0/version2/published_version1；历史版本保留。原教务指标ID1 enabled1/version3/published3未变；不改角色授权/业务任务。
- 操作SQL及前后验证：C:/Users/EDY/.codex/visualizations/2026/10/30/01a18b98-051a-7943-8457-6270185a8b7d/home-todo-restore/disable-duplicate-todos.sql 和 fixed-todos-db-verification.tsv。恢复这些动态配置可在指标管理重新发布，固定卡无需配置。
- 验证：Workbench typecheck、生产构建、5项相关Vitest通过（5项未选中）；1440/390真实浏览器隔离fixture通过分组计数/审批跳转/无权限不请求/失败非零状态。root复核截图与git diff --check通过。截图数值为测试fixture，不是实时账号数量验收。5190专用测试服务已停止。
- 保留所有无关dirty changes；子任务按运行前字节恢复原有dirty tsconfig.tsbuildinfo；未动API用户修改、主后端服务或提交记录。所有写入所有权释放。
