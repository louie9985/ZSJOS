# 报名收集提交通知

- Environment local（ZSJOS_AGENT_ENV 未设置，环境文件不存在）
- Workstream local-registration-collection-notify-20261111，owner Codex
- Goal 学员正式提交报名收集表后通知报名服务
- Scope StudentInfoFormService、RegistrationConstants、RegistrationNotifyPublisher、RegistrationNotifySceneProvider 及对应现有测试，docs/api/student-information-collection.md，script/sql/mysql/registration-collection-notification.sql，本记录
- Non-goals 历史补发、权限分配、前端交互调整
- Branch main，worktree D:\ZSJ-OS，base 7a7cf5e14bef1ff193de5c4cc39af8a5255a7d5c
- Dependencies System 通知事件 API，现有报名部门/权限接收人解析
- Integration None
- Verification 聚焦提交幂等、事件载荷、接收范围测试与编译，隔离 MySQL 配置首次/重放/恢复/失败检查

- Scope update 纳入 StudentInfoOrderTerminationTest，覆盖订单产品任务绑定提交路径新增通知依赖。

## Delivery

- 已接入公开正式提交事务的消息事件，固定表单事件键，重复提交不重复发布，姓名采用本次答案。接收人复用报名部门与 query-pool 权限交集。
- 用户明确授权后应用本地 ruoyi-vue-pro 租户 1，应用前确认 MySQL 8.4.11、租户存在、目标模板/规则均为 0，应用后中文 HEX 与默认 in_app/pool_handlers/message_detail/启用状态验证通过。
- 保留隔离库 registration_collection_notify_20261111105455 及 _partial、_failed，首次/重放/部分恢复/缺租户失败/规则失败事务回滚/恢复均通过，无版本台账变更。
- Maven 聚焦测试 StudentInfoFormServiceTest、StudentInfoOrderTerminationTest、RegistrationNotifySceneProviderTest、RegistrationNotifyPublisherTest 均通过，范围 diff whitespace 检查通过。
- 后端尚未部署或重启，真实学员提交后员工消息收取及双前端展示未验收。本地配置已就绪，运行时需加载本次后端代码。
- 所有本工作流文件写入所有权随交付释放。
