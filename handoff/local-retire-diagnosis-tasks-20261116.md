# 账号诊断任务停用

## 注册（2026-11-16，北京时间）
- 环境：local（环境变量与环境文件均未设置）。工作流：local-retire-diagnosis-tasks-20261116；owner：当前 Codex。
- 分支：main；工作树：D:\ZSJ-OS；基线：21631a8a98a62c41a3c71596a6739e03a535c3b6；目标分支/集成顺序：None。
- 目标：按用户确认停用整个账号诊断任务生成及提醒，既有 pending 退出待办、计数和填写入口。
- 非目标：删除历史、修改数据库/权限、取消启动诊断档案行为、发布或重启。
- 所有权：MediaAccountDiagnosisScheduler/ReminderService、MediaAccountProfileService、BusinessTaskMapper、BusinessTaskReminderService 及其专项测试；Workbench main.tsx、AccountProfilePanel.tsx；诊断 API/部署文档及本记录。
- 保留 main.tsx 中已有导航清理和 BusinessTaskReminderService 中已有班主任文案改动。其他并发改动不归本任务。
- 依赖：现有任务查询、通知、档案权限契约；无新依赖、SQL。验证：专项 Java 测试、Workbench typecheck、可用浏览器检查及差异检查。共享编译输出不得与其他 Maven 并发。

## 交付（2026-11-16 16:15，北京时间）
- HEAD 与注册相同，工作范围及 owner 不变，诊断文档包含 docs/api/media-account-profile.md、docs/api/weekly-account-diagnosis.md、docs/deployment/account-diagnosis-followup.md。
- 删除诊断生成器的 Spring 注册和调度方法，保留历史 context/key 工具；诊断任务/提醒接口返回空集合，旧确认请求返回任务不可用且不写事件。
- BusinessTaskMapper 统一 pending 查询排除诊断业务，覆盖列表、菜单、汇总；不删除或改写原任务行。通用提醒候选移除诊断类型，单任务触发亦阻止通知。
- 档案服务关闭周期提交资格，Workbench 移除全局诊断弹窗挂载，旧链接显示停用且不自动打开启动诊断。启动诊断、历史查看和修订保留。
- 验证：Maven 七组专项测试共 78 项通过，包含 H2 真实 Mapper 查询验证旧任务排除、其他任务保留、历史行未删除与跨用户/租户范围；日志 artifacts/retire-diagnosis-tests.log。
- Workbench npm run typecheck 通过；Vite 生产构建通过，隔离输出 tmp/retire-diagnosis-build-20261116，未改共享 dist。构建日志 tmp/retire-diagnosis-build-20261116.log。
- Ego 浏览器在已有 localhost:5174 合成传输页面验证：停用说明可见，周期填写按钮为 0，启动诊断与历史入口仍在，写请求为 0。这是组件级浏览器证据，不是真实账号/API 验收。Vue Admin 源码检索无员工诊断消费者，通用任务契约由后端统一生效。
- 未修改数据库/真实权限，未部署、重启、提交或推送。已发送或进入 System 通知队列的历史通知不撤回。运行环境需更新后端和 Workbench 后才生效；真实账号端到端验收待部署后执行。
- Ego Lite 提示有更新，本任务未升级。所有权随本次交付释放。
