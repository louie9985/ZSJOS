# 交付业绩统计与设置

独立 Workbench 页面 `/zsjos/delivery-performance`、`/zsjos/delivery-performance-target`；使用 `/admin-api/zsjos/delivery-performance/**` 与 `/admin-api/zsjos/delivery-performance-target/**`。

交付业绩统计与设置完整复刻既有能力并独立维护，不再复用销售业绩的页面、API 工厂、业务服务或查询实现。此次拆分保留既有功能与口径：周期、金额、订单、成交率、跟进、明细、目标和修订交互不变；来源、1280 元门槛、接收窗口及复购规则保持一致，无新媒体引流的教务自拓仍不进入成交率。

## 独立实现边界（2026-10-18）

- Workbench 直接注册 `DeliveryPerformancePage`、`DeliveryPerformanceTargetPage`；组件与样式归属 `pages/deliveryPerformance/`，类型、接口、格式化规则、缓存和会话处理归属 `services/deliveryPerformance*.ts`。CSS、表格配置标识使用 `delivery-performance-` 前缀，避免与销售端相互影响。
- 后端两个交付控制器直接调用 `service/deliveryperformance/` 的专属服务；请求响应模型在 `controller/admin/deliveryperformance/vo/`，查询、SQL 和配置对象分别在 `dal/mysql/deliveryperformance/`、`dal/dataobject/deliveryperformance/`。Spring Bean 与 Mapper 类名使用 `Delivery` 前缀。
- 交付服务固定业务域 `delivery`，直接校验交付权限，无 `PerformanceDomainBoundary`、线程上下文切换或销售服务委托。目标对象权限提供器独立注册 `delivery-performance-target`。目标候选默认使用 `study_planner`；跟进统计仍涵盖当前范围内启用员工，不以目标岗位过滤。
- 现有 `zsjos_performance_org/target/revision` 表和已保存配置继续使用，交付查询及保存固定绑定 `business_domain=delivery`；禁止通过交付设置接管销售组织或读取、修改销售目标修订。修订通过已校验业务域的目标关联读取。此次不变更表结构、不搬迁配置、不重写历史事实。
- 订单、客资、冻结归属事实及其原有详情接口仍由所属业务维护；System 组织用户权限、ADMIN 认证、租户、HTTP、基础表格组件继续使用既有公共设施。共用事实不是共用业绩计算或配置实现。未知业务域事实保持个人未归类历史语义，不进入交付汇总。
- Vue Admin 未注册独立交付业绩页面，既有下属员工接口未改动。独立常量保留现有响应错误码，确保客户端错误处理兼容。后续交付业务规则只在上述交付目录调整，不重新引入销售页面包装或业务服务共享。

独立权限为 `zsjos:delivery-performance:query/self/department/center/detail` 和 `zsjos:delivery-performance-target:query/update/configure`。功能权限、业务域、System 数据范围及对象校验累计执行。控制器固定业务域，客户端不能切换域。

组织和目标增加 `business_domain`，目标唯一键包含该字段。销售专员与交付学习规划师分别默认参与目标汇总；其他岗位可查看但不自动计入目标缺失，已有历史显式目标保留。历史事实按已有组织快照过滤，不回填历史事实。事实写入按明确组织配置冻结部门和中心。

V321 只迁移明确已配置的交付组织 1050/1051 和按保存部门归属的目标，保留目标金额、周期和修订。新增菜单元数据，不写角色关系。管理员通过 System 角色管理替换交付主管销售业绩权限。

2026-10-16 合并编号：V318 考期、V319 首页休息日、V320 下属员工菜单、V321 交付业绩域、V322 归属复审。历史环境可能存在不同版本身份，必须先核对两本账本及校验和；源码合并不修改数据库版本记录。

## 复审修复与历史兼容（2026-10-15）
- 新增 `GET /admin-api/zsjos/{sales|delivery}-performance/unclassified-history`，参数 userId（省略为本人）、pageNo、pageSize；返回分页列表，元素包含 fact（既有明细字段）、attributionReason、historicalDepartmentId。两个域读取同一未归类事实，不计入任何域指标。
- 接口累计要求对应 query/self/detail 权限；本人可读，他人仅 System 全租户读取权限可读。部门团队权限不授予未知组织事实。
- attribution.business_domain 在首次事实快照创建时取权威组织配置，可空且不回填。明确域优先；旧域为空仅当 org_source=frozen 且部门域配置创建/最后修改时间均不晚于事实时可分类。配置缺失或变更后的旧证据不推断。已知域、缺部门的本人事实保留，部门汇总仍要求冻结部门。
- 个人历史不受当前部门业务域/停用影响；团队历史逐条校验冻结部门。报表、明细、订单下钻共用域过滤。历史贡献行保留停用员工，默认目标候选仍按当前岗位。
- 目标列表一次读取请求周期快照，并保留明确记录的历史目标；修订操作人批量读取。写入继续校验当前域、启用状态、部门数据范围。
- Workbench 提供“我的未归类历史”抽屉，分页及空态/失败重试；Vue Admin 不注册独立业绩页面，继续消费兼容下属员工接口。Admin 批量处置使用当前 lead-transfer/lead-release-public-sea 权限、幂等键和逐条结果。
- V322 精确校验菜单路径/组件/类型/父节点/权限集合、组织目标字段定义与唯一索引，再新增 nullable 快照字段。保留管理员名称、排序、启停、显示配置；不修改角色授权。
- 本机存在旧 V320 考期、V321 交付与新 V319 考期并存账本。V322 不重写旧账本或校验和；完整编号链与部署账本映射仍需单独处理，禁止按新编号盲目重放旧迁移。
