# 公海池表格视图
- ID: local-public-sea-table-20261030; environment: local (环境变量与标识文件均未设置)
- Goal: 公海池通用表格、分页、详情与现有全部操作/服务端筛选接入。
- Non-goals: 数据库、权限配置、部署、提交推送、无接口支持的批量操作。
- Branch: main; worktree: D:\ZSJ-OS; base: ba8c788f470eb96d22e8729a2d8f711d83c0c370
- Owner: 当前 Codex chat; ownership: LeadPublicSeaPoolPage.tsx、public-sea-table 专项测试/夹具、docs/public-sea-pool.md、本记录。
- Dependencies: 已有 BusinessTable、LeadDetail、服务端筛选目录与 availableActions。
- Verification: typecheck、相关 Vitest、浏览器夹具桌面/移动检查。
- Target branch / integration order: None。保留其他工作流的全部修改。
## Scope update
- 增加后端 AdvancedFilterFieldCatalog.java、PublicSeaFilterFields.java 与 PublicSeaFilterFieldsTest.java：补全公海专属人员、轮次、时间筛选；不改变授权、表结构或接口参数。
- 两端高级筛选均消费 lead 目录：检查 Admin/Workbench 对新增 date/select/number 字段的兼容性；执行后端定向测试。
- Scope update: public-sea-pool.css，修复该页移动端继承零高度主从容器导致的点击遮挡。
## Delivery / verification
- 完成 BusinessTable 表格、全局视图切换、服务端分页/页容量、列设置、刷新、重置、详情抽屉与现有授权操作入口。移动端列表和详情抽屉已检查；修复权限失败时残留展示、转派申请错误反馈。
- 新增 8 个服务端公海专属高级筛选字段，复用可见用户/部门选项。Admin/Workbench 的 select/number/date 消费代码均已核对；没有数据库或授权配置变更。
- Workbench typecheck 通过；公海页面 guard 2/2、高级筛选公海项 2/2 通过。浏览器合成夹具验证桌面分页、按行权限显示、指派提交、错误重试、移动端抽屉通过，截图在 tmp/public-sea-table。
- 后端 Maven 编译完成，PublicSeaFilterFieldsTest 2/2 通过；合并运行 AdvancedFilterFieldCatalogTest 共 11 项，10 通过，目录硬编码总数断言失败（期望 231、实际 237），未修改原有总数断言。新增字段计 8 项；不将该差异全部归为本次增量。
- 扩大运行 inbox-advanced-filter.guard.test.ts 时 3 个其他页面的 ReloadOutlined 文本断言失败；公海池相关项通过，未改动其他页面。
- 限制：没有真实数据库查询执行、真实账号授权/业务命令验收或 Admin 浏览器验收；详情浏览器夹具验证的是加载失败/重试展示，实际详情业务流程复用既有 LeadDetail。本次不涉及构建入口或依赖变更，未运行生产构建。
- 未提交、推送、部署或重启服务，未修改真实数据。交付后释放上述文件所有权。
