# 考期日历 API

考期日历属于 ZSJOS 业务域，使用 ADMIN 登录态、当前租户和服务端菜单权限。业务日期均为
`YYYY-MM-DD` 自然日，按 `Asia/Shanghai` 计算派生状态。

页面位于“日历 → 考期日历”，地址为 `/calendar/exam-calendar`；服务端菜单父节点为
`73600`，相对子路径为 `exam-calendar`。业务 API 仍使用 `/zsjos/exam-calendar` 前缀。

## 权限

角色授权树在“日历 → 考期日历”下提供两个同级按钮：“查看考期”（73612）和
“管理考期”（73611）。普通员工只勾选查看，考务人员勾选查看和管理；不以单独勾选
父页面代替查看授权。页面节点保留 query 标识用于路由权限契约。

- `zsjos:exam-calendar:query`：访问页面并查询考期、启用产品分类。
- `zsjos:exam-calendar:manage`：创建、修改、发布和撤销考期。Controller 与 Service 同时校验。

普通查询者只会收到 `PUBLISHED` 记录；拥有管理权限的查询者还会收到草稿和已撤销记录。
授权来自 System 菜单/按钮配置，运行时不按角色名称判断。

## 查询

- `GET /zsjos/exam-calendar/page`：精确考期分页。参数为 `pageNo`、`pageSize`、可选
  `rangeStart`、`rangeEnd`、`categoryId`、`displayStatus`。
- `GET /zsjos/exam-calendar/rough`：粗略考期分页。支持日期窗口相交和分类筛选。
- `GET /zsjos/exam-calendar/category-options`：仅返回当前租户已启用的 ZSJOS 产品分类和分类路径。
- `GET /zsjos/exam-calendar/product-options`：要求 manage（Controller 与 Service）；返回产品 ID、名称、分类路径、规格及可用 SKU，不返回价格，也不要求产品管理权限。

精确记录的 `displayStatus` 在查询时派生：草稿/撤销优先；已发布记录在考试前 N 天进入
`UPCOMING`，考试当天为 `IN_PROGRESS`，次日起为 `ENDED`。N 使用全系统 Infra 参数
`zsjos.exam-calendar.upcoming-days`，缺失、负数或非法值按 3 天处理。粗略记录不派生进行状态。

## 管理动作

- `POST /zsjos/exam-calendar/create`：创建草稿。
- `PUT /zsjos/exam-calendar/update/{id}`：仅修改未结束的草稿；已发布需要撤销后新建。
- `POST /zsjos/exam-calendar/publish/{id}`：发布草稿。
- `POST /zsjos/exam-calendar/revoke/{id}`：撤销已发布记录；不可重新发布。

已结束的精确考期不可编辑；编辑不能把精确日期改到当前业务日期之前，过去日期的精确草稿也
不能发布。历史修订需要后续单独定义审计规则。

请求的 `scheduleType` 为 `EXACT` 或 `ROUGH`。精确记录只接受 `exactDate`；粗略记录只接受
`roughStartDate` 和 `roughEndDate`，且结束日期不得早于开始日期。

## 产品与规格范围

- 仅分类：提交 `categoryId`，不提交 `productId` 或非空规格条件。
- 产品范围：提交 `productId` 与 `selectedAttrs: {属性代码: 值代码}`；不同时提交导航分类的 `categoryId`。每个字段单选可清空，空对象表示不限制规格。
- 服务端校验有效产品和分类祖先、已启用规格及可用 SKU。条件为 AND，必须匹配至少一条 SKU；不新增其他业务的部分规格录入能力。
- 响应增加 `productId/productNameSnapshot/selectedAttrs/selectedSpecs/frozenSkus/scheduleName`。`selectedSpecs` 为 `{attrKey,attrName,value,label,labelMissing}` 数组，按规格配置顺序；名称仅拼接已选字段。
- 草稿保存标签快照，未改变范围时保留旧快照；发布时重新验证条件，固定发布时标签和适用 SKU 集合。历史查询不访问现行产品目录。
- 更新、发布、撤销在租户隔离下锁定考期，防止并发命令改写已发布范围。时间类型切换显式清空互斥日期字段。
- `1900018008` 表示无有效 SKU 匹配；`1900018009` 表示范围互斥错误。产品失效和规格失效使用产品领域既有错误码。

### 审查修复后的更新与并发契约

- 更新可附带 `clearedInvalidAttrs: string[]`，记录用户明确清除的失效字段代码。原产品未改变时，后端比较原条件与锁内配置：失效字段既未被有效替换、也未被明确清除，返回 `1900018010`，不写入任何修改。旧客户端仍可提交未丢失条件的请求；不能静默省略失效条件。
- 编辑器独立保留完整条件。已删除字段、已删除值及不可用产品显示历史信息和失效提示；清除条件或切换带条件的范围需确认，失败保留输入。
- 产品范围的选择身份为产品 ID 与条件；分类范围的选择身份为分类 ID。产品换分类不使日期／备注编辑重写原显示快照；发布仍冻结发布时的有效目录信息。
- 保存／发布及相关目录写入使用 READ_COMMITTED 事务。锁顺序为考期（已有记录）、产品、分类 ID 升序、SKU；分类修改只锁受影响路径和子树，不反向锁产品或考期。分类锁后重新读取并复核结构，变化返回 `1900018011`，要求刷新重试。产品锁刷新本事务的 MyBatis 查询缓存，避免使用锁前 SKU 读取结果。
- 产品／规格／SKU 修改参与相同产品锁；分类创建、移动、停用、删除参与分类锁。先提交的操作决定校验看到的配置；发布提交后的目录变化不改变冻结范围。
- 精确、粗略及产品选项各自隔离请求序号，旧分页响应、旧错误或卸载后的响应不能覆盖最新界面。
- 不新增持久化字段，不改变查看／管理授权、日期和生命周期规则。真实 MySQL 双事务验证与原有字段迁移重放仍须在明确的受控目标完成，单元测试不替代该验收。

SQL：fresh bootstrap 调用 `script/sql/mysql/exam-calendar-product-scope.sql`；开发库修正需在 V188 后执行该幂等字段补丁。只新增可空字段，不回填历史标签、不改角色菜单。已交付环境的独立版本迁移及真实数据库同步待部署范围确认。

同日同分类允许多条记录。接口不发送通知，也不创建定时结束任务。
