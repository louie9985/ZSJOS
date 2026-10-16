# 交付业绩统计与设置

独立 Workbench 页面 `/zsjos/delivery-performance`、`/zsjos/delivery-performance-target`；使用 `/admin-api/zsjos/delivery-performance/**` 与 `/admin-api/zsjos/delivery-performance-target/**`。

功能、周期、金额、订单、成交率、跟进、明细和目标交互复用销售业绩契约。来源、1280 元门槛、接收窗口及复购规则保持一致，无新媒体引流的教务自拓仍不进入成交率。

独立权限为 `zsjos:delivery-performance:query/self/department/center/detail` 和 `zsjos:delivery-performance-target:query/update/configure`。功能权限、业务域、System 数据范围及对象校验累计执行。控制器固定业务域，客户端不能切换域。

组织和目标增加 `business_domain`，目标唯一键包含该字段。销售专员与交付学习规划师分别默认参与目标汇总；其他岗位可查看但不自动计入目标缺失，已有历史显式目标保留。历史事实按已有组织快照过滤，不回填历史事实。事实写入按明确组织配置冻结部门和中心。

V320 只迁移明确已配置的交付组织 1050/1051 和按保存部门归属的目标，保留目标金额、周期和修订。新增菜单元数据，不写角色关系。管理员通过 System 角色管理替换交付主管销售业绩权限。

历史本机独立升级按原 V321 验证；2026-10-15 源码编号调整为 V320（考期导出由 V320 调整为 V319），编号缺口已修复。旧编号数据库须先核对两本版本账本与校验和，不能直接按新编号重放；尚未完成调整后的完整 fresh/upgrade 链验收。

## 复审修复与历史兼容（2026-10-15）
- 新增 `GET /admin-api/zsjos/{sales|delivery}-performance/unclassified-history`，参数 userId（省略为本人）、pageNo、pageSize；返回分页列表，元素包含 fact（既有明细字段）、attributionReason、historicalDepartmentId。两个域读取同一未归类事实，不计入任何域指标。
- 接口累计要求对应 query/self/detail 权限；本人可读，他人仅 System 全租户读取权限可读。部门团队权限不授予未知组织事实。
- attribution.business_domain 在首次事实快照创建时取权威组织配置，可空且不回填。明确域优先；旧域为空仅当 org_source=frozen 且部门域配置创建/最后修改时间均不晚于事实时可分类。配置缺失或变更后的旧证据不推断。已知域、缺部门的本人事实保留，部门汇总仍要求冻结部门。
- 个人历史不受当前部门业务域/停用影响；团队历史逐条校验冻结部门。报表、明细、订单下钻共用域过滤。历史贡献行保留停用员工，默认目标候选仍按当前岗位。
- 目标列表一次读取请求周期快照，并保留明确记录的历史目标；修订操作人批量读取。写入继续校验当前域、启用状态、部门数据范围。
- Workbench 提供“我的未归类历史”抽屉，分页及空态/失败重试；Vue Admin 不注册独立业绩页面，继续消费兼容下属员工接口。Admin 批量处置使用当前 lead-transfer/lead-release-public-sea 权限、幂等键和逐条结果。
- V322 精确校验菜单路径/组件/类型/父节点/权限集合、组织目标字段定义与唯一索引，再新增 nullable 快照字段。保留管理员名称、排序、启停、显示配置；不修改角色授权。
- 本机存在旧 V320 考期、V321 交付与新 V319 考期并存账本。V322 不重写旧账本或校验和；完整编号链与部署账本映射仍需单独处理，禁止按新编号盲目重放旧迁移。
