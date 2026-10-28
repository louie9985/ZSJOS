# 我的运营

中世健员工工作台 `/zsjos/my-operations` 面向编导查看当前关联运营及其客资。菜单组件为 `zsjos/myOperations/index`，直接挂在 `/zsjos` 的菜单 path 保存 `my-operations`。Vue Admin 排除该员工页面，继续提供角色与关系配置。

## 范围与权限

页面和接口要求服务端配置 `zsjos:my-operations:query`。成员来自当前登录用户作为 source 的启用 `content_director_operator` 关联；不按角色名称或部门名称推导团队，也不因已有销售团队或兼职权限获得此页面。

“名下客资”采用业务提供方归属：`provider_owner_type=system_user`、`provider_owner_id=所选运营`。包含运营直接提交，以及销售、教务代录但归属于运营的客资；不按实际录入人 `creator` 查询，不包含运营名下合作方的客资。当前销售负责人变化不改变此查询口径。所有查询保持租户和逻辑删除约束，当前系统仅使用租户 1。

分页、关键字、快捷筛选、高级筛选和排序均在成员范围内执行；筛选不能扩大团队范围。成员及客资详情每次请求重新校验当前关联和页面权限，前端传入成员 ID 不构成授权。该页面只读，不提供销售接单、转派、回收、资格改判等命令；其他页面既有独立权限不变。详情子资源继续遵循各自功能权限和对象检查，不能通过此入口绕过。如需查看跟进、流转等附属标签，应另行配置已有对应读取权限；仅配置本页面查询权限不隐含这些授权。即使用户在其他入口拥有写权限，本页面仍以严格只读方式展示详情。

## 接口

高级筛选复用 `lead` scene，页面模板键为 `my_operations_leads`，管理端既有系统模板配置可选择此页面。仅持有新页面权限时，人员候选限定在当前关联运营内。

以下路径由 ADMIN 前缀 `/admin-api` 挂载，使用已有认证与租户上下文。

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| GET | `/zsjos/my-operations/members` | 当前关联成员及名下客资数量 |
| POST | `/zsjos/my-operations/{memberId}/leads/search-page` | 指定关联运营名下客资，复用客资分页契约 |
| GET | `/zsjos/my-operations/leads/filter-profile` | 已发布的管理客资筛选配置 |
| GET | `/zsjos/my-operations/{memberId}/leads/{id}` | 检查成员、客资归属后读取详情 |

成员统计与列表使用相同业务归属；客资对外标识只展示 `leadNo`。工作台复用 `LeadManagementPage` 和既有客资详情组件，加载、空态、失败重试与无权限状态均保留。

## 启用与交付边界

V354 在 V353 后定义页面菜单元数据及版本记录，不授予或修改 `system_role_menu`。管理员通过 System 角色管理将页面权限配置给需要使用的编导，并维护编导—运营关联。部署需同时包含后端与 Workbench；菜单存在不代表旧运行实例已经具备接口。无需修改业务客资、合作方归属或创建新的运营团队表。
