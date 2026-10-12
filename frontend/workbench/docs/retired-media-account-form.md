# 已退役的独立账号表单

V113 已退役 `/zsjos/accounts` 独立页面菜单。当前账号维护入口为员工工作台
`/zsjos/media-students`：选择学员及服务，再进入具体账号标签页，由
`MediaStudentsPage` 挂载 `AccountMaintenancePanel`（实际实现为 `AccountProfilePanel`）。

2026-10-09 清理了无调用的 `AccountsPage` 导出，以及 `MediaFeaturePage` 内仅供旧账号页
使用的新增、编辑、绑定、解绑、挽救、换绑表单、账号类型及专业方向选择控件和对应状态、请求分支。
这些控件不是当前账号画像的字段配置来源。

保留 `MediaFeaturePage` 中现有内容、定位代码及仍在使用的拍剪工单详情、指定派单组件。
共享账号候选查询及类型仍供内容、定位和工单使用。此次清理不删除 System 字典、业务记录、
后端接口、权限元数据或历史迁移，也不改变当前账号画像的服务端字段配置和标签快照。
