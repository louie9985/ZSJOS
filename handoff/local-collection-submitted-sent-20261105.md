# 学员提交视为已发送
- 环境：local（环境变量和环境文件均未设置）
- 工作流：collection-submitted-sent-20261105；负责人：Codex
- 分支：main；工作树：D:/ZSJ-OS；基线：216b75eaa84b7b757889be24e6a5483ed5a93593
- 目标：已提交资料可完成报名履约，历史记录即时兼容。
- 非目标：数据库回填、部署、其他履约条件调整。
- 所有权：RegistrationServiceImpl.java、RegistrationEarlyDeliveryTest.java、docs/api/student-information-collection.md、本记录。
- 依赖：现有表单状态；目标分支/集成顺序：None。
- 验证：报名履约定向测试；核查 Admin 和 Workbench 的 informationSent 消费。

## 交付
- 已完成履约门槛与 informationSent 投影调整；兼容既有 SUBMITTED 且 sentAt 为空的记录，不修改历史审计字段。
- 定向 Maven reactor 测试 RegistrationEarlyDeliveryTest、RegistrationServiceImplTest 通过，git diff --check 通过。覆盖未发送草稿阻断与已提交未确认发送可完成。
- Admin 和 Workbench 均消费 informationSent 显示发送标签，已核查源代码；真实账号接口与浏览器未验证，本次未部署或重启。
- 文档已同步；所有权释放。
