# 交付业绩代码独立（2026-10-18）

## 登记
- ID：local-delivery-performance-isolation-20261018；owner：当前 Codex 工作流。
- 环境：local（ZSJOS_AGENT_ENV 未设置，环境文件不存在）；分支 main；工作树 D:/ZSJ-OS；基线 d29fcd5f888ceca4a1980720b1eb0fbf6a1cbfc2。
- 目标：按用户明确要求完整复刻交付统计、设置、查询、权限与前端业务代码，独立维护，不再委托销售实现。
- 非目标：改变指标口径、迁移事实或配置数据、修改账号授权、部署重启、提交推送。现有 business_domain 数据隔离和历史快照契约继续有效。
- 所有权：新增后端 deliveryperformance 服务、DAL、VO 与测试；两个 DeliveryPerformance 控制器；新增交付常量；新增 Workbench DeliveryPerformance 页面、deliveryPerformance 组件/服务/测试；原销售服务 API 工厂及 PerformanceModule 的交付桥接清理；RouteHost 的交付路由两处；unclassified-history 测试入口；docs/api/delivery-performance.md；本记录。
- 保留当前所有既有改动；销售 PerformanceDetailSql 的已暂存修复仅作为复制时的现状，不编辑该文件。RouteHost 若出现并发变更，只合并目标路由。
- 依赖：既有 System 公共 API、ZSJOS 业务事实、框架认证租户与 UI 基础组件。无新增依赖。目标分支与集成顺序：None。
- 验证：独立依赖扫描；销售/交付 API 和计算、目标、权限回归；TypeScript 与生产构建；可用环境下专属浏览器夹具；后端使用独立临时编译输出避免污染并发构建。运行环境验收另行区分。

## 登记更新
- 新增所有权：Workbench `test/delivery-performance.html`、`test/delivery-performance.tsx`、`test/delivery-performance-browser.py` 浏览器夹具，以及服务目录交付隔离守卫测试。专属夹具使用现有本地 Vite 的静态测试入口和合成响应，不写入真实业务数据。

## 交付
- 完成交付专属前后端链：33 个独立服务/DAL/VO 类，独立错误常量，两个既有交付控制器改为直接注入专属服务；独立对象权限类型 delivery-performance-target。
- Workbench 新增两个独立页面及完整组件、SQL 对应接口模型、缓存、会话处理和样式；移除销售页上下文包装及共享 API 工厂的 delivery 分支。表格标识与 CSS 前缀独立，销售路由保留。
- 配置继续按既有 business_domain 隔离；不新增 SQL、不修改数据库或权限。已保存组织、目标和修订继续可用。事实、订单客资详情、System API 与框架设施仍属原业务公共边界，统计和设置业务实现独立。
- 同步 docs/api/delivery-performance.md 的维护边界。本次明确用户决策取代原文“复用销售业绩契约”的实现方向。

### 验证证据
- 后端临时工程 C:/Users/EDY/AppData/Local/Temp/zsjos-delivery-isolation-20261018：Java 25 编译 36 个本次业务类通过；完整测试 89 项通过（test.log），随后新增销售事实排除用例并定向运行 8 项订单查询测试通过（order-final.log），合计 90 个不同用例。
- 临时工程复制当前父 POM 保留 Java 25/Lombok 配置，使用现有 ZSJOS 构建 jar 和当前 PermissionApi 源码补齐本机旧依赖；未新增仓库依赖或改动公共构建输出。该证据是局部源码/契约验证，不是整服务启动验证。
- 覆盖：交付权限不能由销售权限授予、销售目标读写拒绝、销售组织接管拒绝、配置查询固定 delivery、销售事实不进入交付订单金额、历史缺失域排除、目标汇总修订、成交率和接收轮次、租户 SQL 解析及隔离夹具。订单旧测试夹具补充已有 business_domain/org_source 和来源字段，以匹配当前历史归属契约；未改变产品统计口径。
- Workbench 49 项 API/规则/缓存/会话测试通过，2 项独立依赖/路由守卫通过；最终 npx tsc --noEmit 通过。
- 最终 npx vite build --outDir C:/Users/EDY/AppData/Local/Temp/zsjos-delivery-build-final-20261018 通过（26.57s），保留现有大 chunk 提示；独立输出避免覆盖共享 dist/tsbuildinfo。
- Chromium 专属夹具通过：1440/390 宽度的统计与设置、无页面横向溢出、目标保存提交到交付接口、修订抽屉、组织配置入口、错误重试/空态、仅销售权限不显示交付修改入口。截图在 C:/Users/EDY/AppData/Local/Temp/zsjos-delivery-isolation-browser-20261018。
- 原未归类历史浏览器夹具改接实际独立组件后，销售/交付双端桌面及手机、成功/空态/错误重试/无权限入口全部通过。所有浏览器响应及写入均为合成夹具，未使用真实员工账号。
- 最后依赖扫描未发现交付统计/设置导入销售业绩服务、DAL、VO、页面或业务 API；既有文件 scoped diff --check 通过。既有销售 PerformanceDetailSql 及其它并发改动保留。

### 状态与边界
- 完成本地源码、测试、前端构建、浏览器夹具与文档交付，释放本工作流所有权。未提交、推送、部署、重启或变更真实数据库。
- 尚未完成整服务启动及真实账号、实际配置数据的运行验收；当前运行服务不因源码拆分自动更新。
