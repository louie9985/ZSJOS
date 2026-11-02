# 工作台收件箱滚动加载

- 环境 local；分支 main；工作树 D:\ZSJ-OS；base 0ecc74d4d13bcfbc0361ecdbc2bbd73327359425；owner Codex。
- 用户要求所有收件箱采用滚动追加加载；表格模式保留分页，普通表格/日历/选择器不改。
- 所有权：共享收件箱加载组件/工具及测试；StudentInfoTasksPage、RegistrationPages、LeadComplaintPage、ContentProductionPage、ContentReviewBatchPage/ContentReviewInbox、ProductionTicketsPage、SubordinatePartnerPage、DeliveryClassGroups 及实际核查出的收件箱分页入口；相关文档及本记录。保留已有其他修改。
- 依赖：已有分页/游标 API，不新增后端业务能力或静态权限。目标分支/集成 None。
- 验证：去重、筛选重置、追加不改详情、错误重试、表格分页保持；类型检查、相关测试、桌面/手机浏览器长列表验证；逐项审计覆盖。

## 范围补充

- 实际审计增加公海、教务团队的“加载更多”入口、WorkPlan 左侧计划、MediaFeature 通用收件箱；ManagementPages 的 Personnel 与 MyOperations 成员目录使用渐进呈现。完整目录接口不冒充后端分页。
- 更新 Workbench AGENTS.md 的持久规则，以及 docs/testing/inbox-lazy-loading-audit.md 覆盖表；不修改既有游标队列、普通表格、日历、首页卡片和详情子记录分页。

## 2026-11-02 11:30 北京时间交付

- local（环境变量与 /etc/zsjos/agent-environment 均未设置），main，D:\ZSJ-OS，HEAD/base 0ecc74d4d13bcfbc0361ecdbc2bbd73327359425 未变。原工作区已有改动均保留，没有将它们算作本轮工作。
- 共享 InboxLoadMore 使用各列表实际滚动容器，支持接近底部触发、未撑满时继续补充、窗口滚动型列表、错误停止/重试、请求令牌防重复。InboxPageBuffer 按页管理与按业务 ID 去重，刷新/筛选重置，支持保留详情命令对已显示记录的更新/移除。
- 已改：StudentInfoTasksPage、RegistrationPages（公共池/学员列表）、LeadComplaintPage、SubordinatePartnerPage、ContentProductionPage、ContentReviewBatchPage/ContentReviewInbox、ProductionTicketsPage 收件箱、MediaFeaturePage、公海池、教务团队、计划左栏。人员/运营目录分批渲染，既有 API 未变。表格分页与未保存档案保护保留。
- 追加响应不会重新选中首条详情；公共池点击任务不再使追加请求遗留 loading；内容审核切换 URL 目标不重置已加载队列；失败重试记录真正失败页，避免跳过数据。
- 测试：最终相关 5 文件 16 项测试通过（合并/去重/重试/筛选重置/保留本地更新、服务记录身份、报名关闭守卫、工作计划加载、教务范围、公海守卫）。Workbench typecheck 和 production build 通过。最后一处 MediaFeature 仅补 append 不清除详情错误的条件保护，类型结构不变。
- 扩大守卫检查 21 项中 18 通过、3 失败：global-inbox-layout 的客资高级筛选旧字面量，以及 subordinate-partner 的旧内联 readonly/detailRequestRef 字面量；本轮没有改这些消费者/模式，HEAD 中已无相关 Partner 字面量。未为了让旧守卫通过而修改无关业务。
- ego-browser 实际合成夹具：资料收集失败第2批后仍20条/选中TEST-003，重试请求页为 [1,1,2,2] 并追加40条，筛选重置为1条，表格仍20条，手机追加40条；报名公共池连续请求 [1,2,3,4] 到65条，详情请求仅 [1,2]，持续选中任务2，结束状态正确。未做真实账号验收；截图因浏览器最小化未完成，交互与 DOM 断言通过。TaskSpace 6 已 finish，夹具服务停止。
- 直接变更文件包含上述页面、InboxLoadMore.tsx、inboxAppend.ts/test、两个测试夹具、Workbench AGENTS.md、收集 API 文档、审计文档及本记录。无数据库、依赖、后台权限、服务重启、部署、提交或推送。所有权释放。
