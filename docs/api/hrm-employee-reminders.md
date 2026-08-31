# HRM 员工提醒

`/hrm/employee-reminder/config` 返回并保存生日、合同到期、入职周年三类独立规则。每类规则包含 `enabled`、`advanceDays`（0-30）、`triggerTime`、`deptIds` 和 `includeChildDepartments`。

调度任务每 10 分钟按租户执行。合同提醒只处理在职员工最新的执行中合同，且合同开启 `expireRemind`；周年提醒按 `entryTime` 自然周年计算，2 月 29 日在非闰年顺延到 3 月 1 日。每个员工/接收人组合使用提醒类型、目标日期、员工和接收人组成幂等键。

三类提醒均创建 ZSJOS 业务待办并发送站内信，接收人可通过 `POST /zsjos/business-task/{id}/complete-employee-reminder` 完成；生日旧完成接口继续兼容。
