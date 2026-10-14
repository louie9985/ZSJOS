# 交付业绩统计与设置

独立 Workbench 页面 `/zsjos/delivery-performance`、`/zsjos/delivery-performance-target`；使用 `/admin-api/zsjos/delivery-performance/**` 与 `/admin-api/zsjos/delivery-performance-target/**`。

功能、周期、金额、订单、成交率、跟进、明细和目标交互复用销售业绩契约。来源、1280 元门槛、接收窗口及复购规则保持一致，无新媒体引流的教务自拓仍不进入成交率。

独立权限为 `zsjos:delivery-performance:query/self/department/center/detail` 和 `zsjos:delivery-performance-target:query/update/configure`。功能权限、业务域、System 数据范围及对象校验累计执行。控制器固定业务域，客户端不能切换域。

组织和目标增加 `business_domain`，目标唯一键包含该字段。销售专员与交付学习规划师分别默认参与目标汇总；其他岗位可查看但不自动计入目标缺失，已有历史显式目标保留。历史事实按已有组织快照过滤，不回填历史事实。事实写入按明确组织配置冻结部门和中心。

V321 只迁移明确已配置的交付组织 1050/1051 和按保存部门归属的目标，保留目标金额、周期和修订。新增菜单元数据，不写角色关系。管理员通过 System 角色管理替换交付主管销售业绩权限。

本机独立升级已验证；当前仓库缺少 V319，完整 fresh/upgrade 链检查仍受阻，不能声明完整发布验收。
