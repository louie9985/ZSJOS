# 学员筛选调整
- Environment: local (环境变量及配置文件未设置)
- Workstream: student-filters-20261028; owner: Codex
- Branch: main; worktree: D:\ZSJ-OS; base: 42fa03db3302282101beb2294b2e90c23991f4e6
- Goal: 核查规划师来源，移除表格排序菜单与旧服务状态筛选，批注督学改名及快捷筛选。
- Non-goals: 数据迁移、部署、权限赋予、提交。
- Ownership: StudentServiceFilters.tsx, StudentServiceAnnotationCard.tsx, RegistrationPages.tsx（仅学员筛选段，保留原有修改）, services/api.ts（仅学员请求类型）, StudentServicePageReqVO.java, StudentServiceReadService.java; 本记录及 docs/student-filter-adjustments.md。
- Dependencies: 批注读权限、infra_boolean_string 字典、既有服务可见性。
- Verification: typecheck、批注测试、浏览器筛选检查；后端编译及查询验证。
- Target branch / integration: None

- Scope update: 用户确认学习规划师角色与可见人员取交集；增加 AdvancedFilterVisibleUserService.java、AdvancedFilterController.java；浏览器验证使用临时脚本。
- Verification scope: StudentServiceReadServiceTest.java 新增批注权限与查询边界测试。

## Delivery
- 已按确认的 study_planner 角色成员与可见用户交集修正规划师选项，仅影响 student_service 的 ownerUserId 选项。
- 完成表格独立排序菜单移除、旧服务状态快捷筛选移除、批注督学文案及两个批注快捷筛选。
- npm run typecheck 通过；批注单元测试 3/3；StudentServiceReadServiceTest 15 项、0 失败、2 项既有跳过；Maven 编译成功。
- Playwright 真实 React 页面、隔离传输 fixture：1440/390 两宽度通过，新筛选展示、false 参数提交、旧状态入口消失及页面不横向溢出。首次脚本未匹配 Vite 查询参数，修正路由 glob 后通过。
- git diff --check 通过。未执行数据库写入、提交、部署或真实账号验收；角色成员真实配置与实际数据库查询结果需运行环境验收。
- Ownership released.

## Follow-up registration
- 同一环境 local、分支及 worktree；owner Codex。范围：StudentServiceAnnotationCard.tsx、services/studentServiceAnnotation.ts 及其测试、docs/student-filter-adjustments.md、本记录。
- 用户确认代理学员与无需服务改 checkbox，默认均否；保留 serviceNeeded 正向 API，通过展示取反兼容旧数据。验证类型、单测及真实组件浏览器。不修改数据库或发布。

## Follow-up delivery
- 完成代理学员/无需服务 checkbox 与默认值映射，保留字典可用性、权限、自动保存及代理人必填规则。
- typecheck 通过，批注单测 3/3；浏览器真实学员组件 fixture 在 1440/390 通过默认未勾选、首次加载零写入、无需服务双向保存、代理人必填后保存及取消清空。旧 sales fixture 的插槽已不展示批注，改用实际学员页面验证。
- 未部署、未改写数据库。Ownership released.

## Default-filter correction registration
- local / main / D:\ZSJ-OS，沿用原注册；owner Codex。用户授权默认值同步到查询。范围：StudentServiceReadService.java、StudentServiceReadServiceTest.java、student-filter-adjustments.md、本记录。
- 缺少批注或 NULL 按 proxyStudent=false、serviceNeeded=true 查询，不回填数据库；验证查询 SQL 执行及权限回归。

## Default-filter correction delivery
- 查询使用同服务、同租户、未删除批注的标量子查询及 COALESCE，缺行与 NULL 分别默认非代理/需要服务。原权限边界不变，不写历史数据。
- Maven 定向 StudentServiceReadServiceTest 通过；新增 H2 MySQL 模式执行四种组合，覆盖缺行、NULL、明确默认、明确反值、已删除与其他租户批注。非真实 MySQL/账号验收。
- 首次 JVM 因本机内存不足启动失败，限制 Maven 512m/test 256m 后编译测试通过。diff 检查通过，无部署。Ownership released.
