# 待办取消（2026-10-30）

- Environment: local（环境变量及环境文件未设置，按规则回退）
- ID / owner: local-task-dismiss-20261030 / Codex
- Goal: 本人订单补正及提交人协助待办可取消，原因必填，仅结束待办。
- Non-goals: 不变更订单、协助业务、BPM、账号授权；不提交发布。
- Branch / worktree / base: main / D:\ZSJ-OS / ba8c788f470eb96d22e8729a2d8f711d83c0c370
- Ownership: task controller/vo、service/task 的新增取消服务/权限提供者与 BusinessTaskServiceImpl、task mapper；Workbench TodayTasksPage、services/api.ts；对应新增测试；V357 取消权限元数据；docs/api/business-task-cancellation.md；本记录。
- Dependencies: 已有任务取消字段和 System 菜单权限机制；V356。
- Integration: None
- Verification: 取消服务权限/状态/原因测试，Workbench typecheck/任务测试，浏览器交互；迁移受控验证能力检查。

- Verification scope addition: script/sql/mysql/tools/test_business_task_cancel.py；frontend/workbench/test/task-cancel-* 浏览器夹具及 tmp/task-cancel 输出，仅隔离测试数据。

## Delivery
- 实现：仅本人订单补正/提交人协助待办可取消；原因必填且最多500字。任务按ID锁定，取消保留首次原因和历史，业务记录及BPM不变。接口分页增加 cancellable/cancelReason；权限 zsjos:business-task:cancel；Workbench 刷新列表/计数/菜单角标。
- Backend: Maven reactor 编译成功，BusinessTaskCancellationServiceTest 13项、已有task测试5项，共18项通过。
- Workbench: typecheck通过；Playwright使用实际页面+隔离API夹具，必填、修剪、失败重试、两类取消、权限隐藏、桌面/手机通过，手机弹窗截图已目视核对。
- SQL: 独立保留MySQL测试库 task357_20261030105412497726_*；首次/重放/部分恢复/前置失败/账本失败回滚/冲突/中文HEX通过。未向业务库应用V357。
- Existing test failures: todayTasks.test.ts 8通过2失败，HEAD已有 length:9 与 assist-history/assistRequestId，而测试仍期待10及旧跳转；未改无关断言。
- Admin: 检查 todayTask/index.vue 共享分页消费，仅增量响应字段兼容，未声称管理端真实浏览器验收。
- Unverified: 新接口真实登录HTTP、真实业务取消后继续办理、并发数据库请求；未部署、重启、授权、commit或push。
- Ownership released.
