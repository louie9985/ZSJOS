# 教务团队

Workbench 独立页面 `/zsjos/education-team`，组件 `zsjos/educationTeam/index`；接口前缀 `/admin-api/zsjos/education-team`。学生交付中心端完整替换原“下属员工”入口，旧入口和旧地址不再可打开；销售端继续使用自己的下属销售页面与接口。教务团队不包装 SubordinateSalesPage，也不调用 SubordinateSalesService。

登录后读取认证接口 `GET /navigation-context`，服务端按当前用户所属部门是否在交付域组织配置中返回 delivery；不按角色或部门名称判断。Workbench 在路由集合、菜单和已发布导航投影中移除交付用户的 subordinate-sales 入口，不制造教务菜单或权限。未授予教务权限时保持无权限/空态，不回退旧入口。读取该认证上下文失败时保留权限加载失败与重试状态，避免误展示旧入口。此限制只控制交付端可支持的导航，接口授权仍由各业务后端独立校验。

## 范围与能力

人员来自 System 用户、部门、岗位 API。部门必须在 `zsjos_performance_org` 中明确配置 `business_domain=delivery`，普通读取叠加 System 当前部门主管树；全租户读取也仅显示已配置交付组织人员。启用与停用账号都可查询。配置为空时返回空集，不按部门名称、角色名或固定部门 ID 推断。

独立员工列表、卡片、累计和今日指标、客资分页、跟进任务、客资详情、账号启停、团队内转派、恢复、回收及符合条件的改判有效。教务人员不展示销售接单、新人池、一键下班配置；教务客资原有规则不支持释放到销售抢单池、公海池，新接口也不注册这些操作。

客资列表和任务仅展示教务身份的当前名下记录；回收待处理列表按原教务负责人读取。对外客资编号使用 leadNo。主管命令在锁定对象后验证当前主管范围和教务身份，转派目标还必须满足教务接收资格、所需业务权限及当前交付组织范围。全租户读取不授予写权限。改判继续使用既有状态、申诉、商机、证据文件与版本令牌规则。

累计订单金额仍按该员工提交的当前有效订单计算；今日金额按生效时间的北京时间自然日计算，区别于交付业绩报表的周期口径。今日待跟进按客资去重，跟进记录旧字段仍按记录数统计，自动首跟/自动判定不计人工工作量；事件发生时归属不因后续人员转移而重写。人员卡片的统计逻辑为独立实现。

## 接口与权限

| 接口 | 权限 |
| --- | --- |
| GET /page、POST /search-page、GET /{userId}/overview | zsjos:education-team:query |
| GET /{userId}/leads、POST /{userId}/leads/search-page | zsjos:education-team:query |
| GET /{userId}/tasks、GET /leads/{leadId} | zsjos:education-team:query |
| GET /transfer-candidates、GET /batch-transfer-candidates | zsjos:education-team:query，另校验实际管理范围 |
| PUT /{userId}/account-status | zsjos:education-team:account-status |
| POST /leads/{leadId}/transfer、restore、recycle | 对应 zsjos:education-team:lead-transfer、lead-restore、lead-recycle |
| POST /leads/batch-transfer、batch-restore、batch-recycle | 对应操作权限；先校验整批对象范围，再返回逐条结果 |
| POST /leads/{leadId}/overturn-valid、POST /overturn-attachment/upload | zsjos:education-team:lead-overturn-valid |

高级筛选场景及模板 pageKey 为 `education_team`，字段前缀 `educationTeam.`；不读取 subordinate_sales 模板。表格配置使用 education-team 标识，样式与组件独立。

主管编排、状态策略、改判与恢复代码位于 `service/educationteam`，专属 VO/DAL 位于对应 educationteam 包。System 权限/组织、客资生命周期、文件和事务框架仍使用所属模块公共能力。既有主管命令和审计表继续使用；教务幂等键通过 `edu:` 加摘要隔离并保持长度，审计动作使用 `education_` 前缀。改判事件与通知保持所属业务事件语义，幂等前缀为 education-overturn。

## 菜单迁移和验收

`V326__education_team_menu.sql` 仅新增一个页面和五个按钮元数据，禁止写 system_role_menu。管理员通过角色管理配置权限，旧下属员工授权不会自动授予教务团队。Vue Admin 不注册此员工页面，其菜单过滤器排除新组件，保留下属销售页面。

执行前要求：两本版本账本、规范下属员工菜单与 Workbench 根节点、已完成交付组织 business_domain 结构升级。不得仅为满足前置而重放历史编号不一致的迁移。本文件可重放并保留管理员名称、排序、启停；冲突或写账失败回滚新增菜单，不登记成功。

验证入口：`python script/sql/mysql/tools/test_education_team_menu.py`，使用当前本地 MySQL 容器创建并保留独立测试库，覆盖首次/重复/部分应用、前置/菜单冲突/账本失败与恢复、中文 HEX、角色授权不变。

2026-10-18：本地开发数据库实查缺少交付组织 business_domain 字段，交付域相关版本记录也未发现，因此未对该业务库执行 V326。已完成隔离 MySQL 验证、局部 Java 测试、TypeScript/构建与合成浏览器验证；尚未完成目标服务启动、真实账号权限与实际组织数据验收。

## 今日状态（2026-10-19）

人员列表和概况使用同一后端批量统计；北京时间自然日 `[00:00, 次日00:00)`，不按浏览器时区。卡片保持当前布局，展示两列三行，不新增表格视图；全局主从和表格设置均沿用现有布局。

| 字段 | 展示及口径 |
| --- | --- |
| todayNewIntentionCount | 新意向：教务自拓实际生成的新客资，按原录入教务和客资去重；包含指定新媒体提供人。使用 submission_source_type 与稳定提交事件操作人，不把 sourceUserId 当录入教务。复核通过创建新客资计入创建当天；待复核、拒绝、激活旧客资、幂等重复不计 |
| todayOldIntentionFollowUpRecordCount | 老意向跟进次数：今日前已录入客资的今日人工客资和商机跟进记录，按操作人和当时 education 身份快照计记录数；自动首跟不计 |
| todayReceivedCount | 接收客资数：今日 education 身份快照的 accept/claim 成功历史按候选接收人及客资去重，与销售计数方式一致 |
| todayOrderAmount | 今日成交额：该员工提交、今日生效且当前有效的订单金额 |
| todayFollowUpRemainingCount | 今日待跟进未完成：今日到期的人工首跟、跟进提醒中仍 pending 的当前教务客资数，按客资去重 |
| todayStudentContactCount | 今日学员联系数：正式提交的 student_first_contact、student_study_plan、student_contact，按操作人及服务关系的 personId 去重；成功和未成功都计，同学员多次联系、多服务关系、首联同时完成计划，当天均最多1；草稿、交付阶段记录不计 |

本次新增的字段为 todayNewIntentionCount、todayOldIntentionFollowUpRecordCount、todayStudentContactCount；旧 todayFollowUpRecordCount 等字段保持原含义与模板兼容。高级筛选新增对应三项数值字段。卡片不再展示今日判定有效、剩余未判定；右上状态仅表示今日客资跟进任务。接口旧版本缺字段显示“—”，真实无记录返回0。

事件和联系按原操作人归属，不因后续转派或服务关系停用而改写；历史缺失不按当前负责人推测。联系关联同租户服务关系获取稳定 personId，即使该关系后来逻辑删除，仍保留其已提交联系的工作量归属。

受控 MySQL 查询及 V334 回归入口：`python script/sql/mysql/tools/test_education_team_statistics.py`。该脚本只创建并保留独立测试库，不改业务库、真实菜单或授权。
