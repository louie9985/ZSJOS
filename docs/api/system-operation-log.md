# 中视简操作日志姓名展示

`/system/operate-log/page`、`/get` 和 `/export-excel` 通过 System 用户服务查询 ADMIN 操作人的当前 `nickname`，填充既有 `userName` 字段（导出列为“操作人”）。列表和导出批量查询去重后的用户 ID，不依赖 Easy-Trans 自动转换。

该姓名是当前用户资料，不是操作时姓名快照。用户不存在、已删除、缺少用户 ID 或用户类型不是 ADMIN 时不伪造姓名，也不使用其他身份空间中同 ID 的员工姓名。日志中的内部 userId 保持不变。

Vue 管理端列表和详情继续读取 userName；员工工作台源码没有独立的 System 操作日志页面实现。权限、租户过滤、接口结构及日志写入流程保持原契约。
