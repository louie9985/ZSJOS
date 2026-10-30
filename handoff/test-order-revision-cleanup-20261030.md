# 成交补正待办清理

## Registration — test-order-revision-cleanup-20261030
- 北京时间：2026-10-30；owner /root；环境 test（/etc/zsjos/agent-environment）；branch main；worktree /opt/zsjos；base ba8c788f470eb96d22e8729a2d8f711d83c0c370；target branch / integration None。
- 用户目标及授权：先清理已有两条补正任务；采用保留历史的取消任务语义。
- 精确范围：test 数据库 zsjos、tenant_id=1、zsjos_business_task.id IN (11279,11280)，task_type=sales_order_revision，关联订单 OD202609302255200019 / OD202609302305180020，订单及轮次均已驳回。
- 非目标：不删除任务，不修改订单、审批、其他待办、权限、配置或任务生成逻辑，不部署或重启服务。
- Ownership：本 handoff 文件；上述两条数据库任务；独占私有备份目录 /opt/zsjos-runtime/backups/order-revision-cleanup-20261030/。现有其他工作树修改保持原样。
- Dependencies：现有 db_config(test)、PyMySQL、MySQL 8.4.11；后台任务查询按 pending 状态筛选，无需应用变更。
- Verification：连接环境/数据库 UUID 核对；订单先锁、任务后锁；核对固定主键/关联/状态/版本；写前受限备份；单事务取消并核对字段白名单及订单/轮次不变；独立连接复查 UTF-8/HEX、待办数量。异常回滚；已提交恢复需先核对后像及后续业务变化。

## Delivery — 2026-10-30 10:47:51 北京时间
- Context：同上述登记；owner /root；test/main，/opt/zsjos；HEAD 未变 ba8c788f470eb96d22e8729a2d8f711d83c0c370；integration None。
- 结果：精确更新任务 11279、11280，由 pending 改为 cancelled；取消原因为“按用户要求清理已有补正待办”；写入实际取消时间，version 各加一，updater=revision-cleanup-20261030。不冒充员工身份或伪造业务 API 审计。
- 验证：写前核对订单、审批轮次、任务幂等键及版本；单事务恰好更新两行；完整任务后像仅指定取消/版本/审计字段变化。独立新连接确认两条任务取消、tenant 1 待处理补正任务数量为 0、关联订单与审批轮次完整前后像不变、中文值及 HEX 一致。未操作其他业务记录。
- 私有证据：/opt/zsjos-runtime/backups/order-revision-cleanup-20261030/{before.json,expected-after.json,verified.json}，目录 0700、文件 0600，写前 fsync；含敏感业务前像，不纳入仓库。恢复仅限重新核对后像后按备份恢复相关字段，需单独授权，不覆盖后续业务操作。
- Changed files：仅本 handoff；操作通过一次性受控 PyMySQL 事务完成，无迁移、schema、版本台账、代码或生成规则变更。
- 限制：未执行已登录 API / 浏览器验证；数据库待办筛选结果已验证。刷新页面应退出待办，历史仍保留；以后新驳回仍按现有逻辑生成任务。本次关闭任务不等于关闭交易，不释放订单业务限制。
- 剩余工作：None（本次两条任务清理已完成）；后续产品关闭入口不在本次范围。Ownership released。
