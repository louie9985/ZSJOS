# 教务团队独立界面（2026-10-18）

## 完整替换续办登记
- 用户明确要求学生交付中心端不保留原入口，完整替换为教务团队；取代此前“新增并兼容保留交付端旧入口”的决定。销售端旧入口仍属于销售端。
- 当前 local/main，基线 6bf0ce81a182b0a5ea040c8536a1ae7fad485e5e。所有权：EducationTeamScope/Controller 新增服务端导航归属查询，Workbench educationTeamNavigation 服务/测试、main.tsx 权限加载局部接入，相关后端测试、API/菜单文档与本记录。
- 实查本地和配置后端库的旧菜单 6814 与教务菜单 602371 同属 Workbench 根，非独立交付树；没有已保存布局。不得直接删除共享销售菜单或改角色授权。服务端以当前用户部门的 delivery 配置确定交付端，客户端只过滤服务端不支持的旧入口，绝不制造教务权限。旧地址从可打开路由集合移除。
- 先前业务库缺域判断已过时：本轮配置后端/本地库已存在交付菜单与 V321/V326。本轮不重写已应用 V326，不改数据库或角色授权。验证导航双方、无教务权限、旧路径以及服务端域判定。
- 新增所有权：Workbench test/education-navigation.html、education-navigation.tsx、education-navigation-browser.py，仅合成导航验证。
- 完整替换完成（本地源码）：新增认证 navigation-context API，按当前用户部门的权威 delivery 组织配置返回导航归属；不推断角色、不扩大业务权限。Root 权限加载先读取该上下文，过滤交付端菜单及布局投影中的旧 subordinate-sales 路径/组件，授权路由与标签保留集因此不含旧入口。销售端返回原菜单。新教务菜单未授权时不制造菜单、不回退旧页面。上下文读取失败/格式无效走权限加载错误与重试。
- 保留 main.tsx 已有个人导航改动，只新增导航上下文接入。没有修改已应用 V326、角色授权、共享菜单记录或数据库。
- 验证：4 项导航过滤测试 + 4 项既有教务 API 测试通过；后端 EducationTeamScopeTest/ContractTest 共 9 项通过，独立临时编译输出 navigation.log；TypeScript 检查通过；最终生产构建通过。
- Chromium education-navigation-browser.py 通过桌面/手机：交付端仅教务入口、旧路径拒绝、新路径允许、缺教务授权空态、销售旧入口允许、无效上下文失败关闭/重试。夹具使用生产过滤函数和拦截后的服务端上下文，未使用真实账号。
- 文档同步完整替换规则。尚未部署/重启，因此当前运行环境尚未由本轮源码证明完成替换；部署必须后端新增上下文 API 与前端一起更新。现有运行后端缺 API 时新版前端将显示权限加载重试，不能单独发布前端。本轮本地实现交付，所有权释放。

## 文案清理续办登记
- 用户要求扫描相关描述，将教务侧残留的销售称谓改为教务。环境 local；当前 main，D:/ZSJ-OS，基线 6bf0ce81a182b0a5ea040c8536a1ae7fad485e5e；owner 当前 Codex。
- 本次所有权：DeliveryPerformancePage.tsx 的成交率提示、pages/deliveryPerformance/UnclassifiedHistory.tsx 的提示，交付测试 DeliveryOrderDetailTest.java、DeliveryPerformanceTargetTest.java、DeliveryPerformanceTargetBatchTest.java 的人员名称，以及本记录。
- 不改变业务计算、权限、数据库及 API 标识；保持销售/教务隔离说明与既有修复。验证为范围内容差异、残留文案扫描、交付前端已有测试。无需数据库或服务操作。
- 文案清理完成：交付成交率提示使用“教务自拓客资”，未归类提示使用“教务业绩或其他业务域”，三处交付测试人员名称改为“教务甲/历史教务”。共 5 个文件、5 处描述。
- 扫描教务团队和交付业绩专属前后端源码/测试共 109 个文件，中文“销售”残留为 0；文档中的真实销售隔离边界及技术 sales 标识保持原义。21 项已有前端回归测试通过。本次为文案和测试数据称谓修改，未运行后端构建或部署；所有权释放。

## 登记
- ID local-education-team-20261018；owner 当前 Codex；环境 local（环境变量未设，环境文件不存在）；main；D:/ZSJ-OS；基线 d29fcd5f888ceca4a1980720b1eb0fbf6a1cbfc2。
- 目标：依据用户明确要求，将下属员工教务功能拆为独立“教务团队”页面、接口、权限、列表统计和主管命令。保留既有交付业绩拆分成果。
- 范围：新增 backend ZSJOS educationteam 服务/控制器/VO/DAL/测试，Workbench EducationTeamPage 与 educationTeam 服务组件测试；constants.ts、RouteHost.tsx、组件注册表局部接入；新增菜单升级 SQL 和验证脚本；教务团队 API 文档、菜单覆盖文档与本记录。
- 非目标：修改已有下属员工/销售业务行为；迁移真实账号权限；复制销售接单/新人池配置；改变客资生命周期；部署重启/提交推送。
- 人员按既有交付组织 business_domain=delivery 配置与 System 主管树累计限定；全租户读取仍限定交付组织。写入要求实际主管关系、教务客资身份、各操作权限，租户为 1。
- 复刻列表统计及主管命令编排，底层客资生命周期/System 公共 API 保持所属模块。命令幂等与审计独立前缀，销售操作及个人配置互不影响。
- 验证：专属范围/权限/统计/命令测试、前端 API/组件检查、独立编译与构建、桌面手机浏览器、MySQL 菜单迁移首次/重放/失败/部分恢复和标签 HEX。必要迁移先在保留的隔离数据库验证，再仅向本地开发库添加菜单元数据；不写 system_role_menu。
- 现有大量脏文件及先前交付工作均保留；改动共享文件前重读，只修改所需入口。目标分支与集成顺序 None。

## 范围更新
- 高级筛选新增独立 education_team 场景及字段，不复用 subordinate_sales 的模板和配置；涉及 AdvancedFilterFieldCatalog、AdvancedFilterMetadata、两个高级筛选控制器与模板服务的场景授权，以及 Workbench api.ts 场景类型。保留原两端场景契约。
- 新增教务客资读取与操作投影，使用原客资详情读取契约。教务命令仅包含 transfer/restore/recycle/overturn-valid；原教务规则不支持销售公海/抢单池释放。销售接单、新人池、批量下班不进入教务界面。
- 双端菜单兼容补充：Vue Admin 的 adminRouteFilter.ts 排除其不支持的新增教务团队组件；不新增 Vue 页面。高级筛选模板 VO 场景校验同步接入 education_team。

## 交付结果
- 新增独立 EducationTeamPage、人员卡片/状态筛选、详情/任务/客资面板、educationTeam API；路由 `/zsjos/education-team`，服务端组件 `zsjos/educationTeam/index`。CSS、表格标识、筛选 scene/pageKey 均独立。
- 新增后端 educationteam 包的 Controller、Service、范围、查询、VO、命令编排、操作策略和改判实现；不调用 SubordinateSalesService/CommandService，不读取销售接单/新人池。教务命令仅保留既有教务支持的转派、恢复、回收、改判有效；账号状态经 System 公共 API 更新。
- 教务范围固定为已配置交付域部门，叠加 System 主管树；全租户读取仍限定交付部门，不授予写权限。任务/客资要求教务身份，命令锁定后再核对实际范围。
- 基础客资生命周期、文件与 System 公共能力仍由原模块负责。既有命令/审计表由独立 Mapper/DO 访问，教务幂等键 `edu:` 加摘要、审计 `education_` 前缀，改判业务事件幂等 `education-overturn:`。不搬迁已有业务数据。
- 新增 V326 菜单迁移，一个页面加五个按钮权限；不改角色授权。Admin 过滤不支持的新 React 页面，保留原下属员工页面。
- 文档：docs/api/education-team.md、菜单覆盖矩阵、高级筛选覆盖补充、migration README。保留本轮之前全部销售/交付改动及其它并发改动。

## 验证
- 独立临时 Maven 工程 `C:/Users/EDY/AppData/Local/Temp/zsjos-education-team-20261018`：Java 25 编译成功，15 项测试全部通过（test.log）。范围、权限、全租户读写区别、销售身份拒绝、幂等键隔离/回放、整批对象校验、模板场景校验、两场景字段独立、租户 SQL、任务去重/自动生成排除/订单时间边界/空任务不查询均覆盖。
- 临时工程沿用前次父 POM、当前 ZSJOS 构建 jar 与当前 PermissionApi 源码；本次源码及直接影响的高级筛选源码单独编译，未改公共构建输出。该验证不代表整服务启动。
- Workbench 9 项 API/隔离/分页与菜单注册测试通过；最终 TypeScript 检查通过，Vite 生产构建通过（19.80s），输出在 `C:/Users/EDY/AppData/Local/Temp/zsjos-education-team-build-20261018`，保留既有 chunk 大小提示。
- Vue Admin 菜单过滤函数独立编译执行：新教务组件被移除，原下属员工及其它组件保留，通过。未改 Vue 页面/API，未执行无关全量构建。
- Chromium 合成夹具 `frontend/workbench/test/education-team-browser.py` 通过：1440/390 宽度人员列表及客资界面无页面横向溢出；人员选择、任务/客资/详情切换、转派、账号状态、错误重试/空态、仅销售权限时无教务写入口。所有请求被拦截，未改真实账号或业务数据。截图目录 `C:/Users/EDY/AppData/Local/Temp/zsjos-education-team-browser-20261018`。
- MySQL 8.4.11 隔离库测试通过：V326 首次/重放、缺前置失败及恢复、部分页面恢复、菜单冲突保护、账本失败回滚、中文 HEX、角色授权不变。保留测试库前缀 `zsjos_edu_20261018150618`；脚本 `script/sql/mysql/tools/test_education_team_menu.py`。
- 最终源码扫描无教务导入下属员工业务服务、销售接单/新人池；作用域 diff --check 通过。

## 尚未验证及环境阻塞
- 本地业务数据库只读核对发现 `zsjos_performance_org` 缺少 business_domain；未找到本次检查范围 V321–V326 的 core 版本记录。此状态不满足新页面及前次交付业绩所需前置。
- 未向本地业务库执行 V326，未盲目重放历史编号有差异的交付域迁移；需要先审查并升级该环境交付组织结构，再应用 V326、由管理员配置权限并进行真实账号验收。该前置故障已向用户说明。
- 未部署/重启/提交/推送，未变更真实角色授权。完成本地源码、独立测试库与浏览器夹具交付；整服务启动和实际组织/员工账号验收仍未完成。释放本工作流文件所有权。
