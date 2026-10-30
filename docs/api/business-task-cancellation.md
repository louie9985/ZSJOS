# 中世健待办取消

POST `/admin-api/zsjos/business-task/{id}/cancel`，请求 `{ "reason": "取消原因" }`。
需要 System 配置的 `zsjos:business-task:cancel` 按钮权限，V357 仅定义元数据，不分配角色权限。
仅当前登录用户本人、当前租户下的 `sales_order_revision` 和 `lead_submitter_assist` 待处理任务可取消；管理员不能借助全员只读视图取消他人任务。
原因非空白且最多500字，服务端去除首尾空白。任务行保存取消时间及原因；重复取消保留首次原因。完成后的任务不能取消。
只更改业务待办行，不终止订单、协助申请或BPM，不删除历史。后续业务继续使用原入口及权限。取消行不再计入 pending 列表/计数/提醒；分页响应新增 cancellable 和 cancelReason 字段。
Workbench 首页业务待办、其他待办提供取消入口；Vue Admin todayTask 页使用同一分页API只读展示，已核对其只读取既有字段，新增字段向后兼容；此次取消操作入口位于员工 Workbench。

发布顺序：V356 → V357 → 匹配后端/Workbench；管理员在今日待办菜单下按需配置取消待办权限。迁移仅写菜单元数据及版本账本，可重复执行；冲突失败不登记版本，事务回滚DML。回退可禁用按钮，不删除任务历史。
