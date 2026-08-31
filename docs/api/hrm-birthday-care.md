# HRM 员工生日关怀

## 配置接口

管理端使用租户上下文调用：

- `GET /hrm/birthday-care-config`（兼容路径：`/hrm/birthday-care/config`）：返回 `enabled`、`advanceDays`（0-30）、`triggerTime`（`HH:mm`）、`deptIds`、`includeChildDepartments`，以及接收人预览和缺少 `zsjos:business-task:query` 的账号清单。
- `PUT /hrm/birthday-care-config`（兼容路径：`/hrm/birthday-care/config`）：保存配置。启用时至少选择一个有效部门；部门通过 System 部门 API 校验，不根据部门名称推断。

权限为 `hrm:birthday-care-config:query` 和 `hrm:birthday-care-config:update`。未保存时默认关闭、提前 1 天、北京时间 09:00、无部门。

## 调度与通知

`employeeBirthdayCareJob` 按租户每 10 分钟运行一次。配置启用且达到当天触发时间后，HRM 只返回当前在职且生日已配置的员工；接收人动态解析为所选部门（可包含下级部门）中的启用账号。ZSJOS 为每个员工/接收人组合创建一条 `EMPLOYEE_BIRTHDAY_CARE` 业务待办并发布一条站内信业务事件，事件提交后由 System 现有监听发送 WebSocket 提示。

幂等键为 `hrm-birthday-care:{生日年份}:{员工ID}:{接收人ID}`。重复调度不会重复创建；跨自然日不补发。任务摘要和通知只包含姓名、部门及生日月日，不包含出生年份或年龄。2 月 29 日遵循 HRM 现有规则，仅闰年触发。

## 待办完成

接收人可调用 `POST /zsjos/business-task/{id}/complete-birthday-care` 完成自己的生日关怀待办。接口校验租户、任务类型、接收人和待处理状态；重复完成保持幂等，不能用于其他业务任务。缺少业务待办查询权限的接收人仍会收到消息和任务，管理员配置页仅显示告警。
