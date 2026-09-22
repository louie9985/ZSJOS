# 销售业绩设置与统计

## 页面与授权

仅 React 员工工作台提供 `/zsjos/sales-performance` 和 `/zsjos/sales-performance-target`。
Vue Admin 过滤两个无法在 Vue 渲染的组件，仍维护服务端菜单、角色及数据权限。
左侧树来自 System 部门及稳定销售岗位，通过业绩组织配置明确关联销售中心、部门。
组织层级与名称不能推导权限或中心类型。祖先导航节点不授予查询能力。

统计页面需要 `zsjos:sales-performance:query`，本人、部门、中心分别累计
`:self`、`:department`、`:center` 权限，统计基础明细另需 `:detail`。
目标页面需要 `zsjos:sales-performance-target:query`，保存和恢复汇总需要 `:update`，
组织关联需要 `:configure`。写入不继承管理员 tenant-read-all 只读例外。
后端独立执行租户、功能和数据范围检查，历史个人记录进一步检查冻结部门，
避免当前部门授权泄露其调岗前其他部门的记录。
个人查询中，拥有 System 全量数据范围或租户全量只读授权的读者可查看组织归属缺失的历史记录；
功能权限和租户过滤仍生效，此例外不用于将缺失记录分配到部门或中心。完整订单/客资入口保留原授权。
迁移不配置任何 `system_role_menu`，也不修改套餐或真实账号权限。

## 页面时间

总览固定展示五周期目标（上周、本周、本月、本季度、本年）、九周期业绩
（今日、本周、本月、本季度、本年、近7/30/60/90日）、六周期成交率
（本月、上月、近7/30/60/90日）。分析日期不改变总览。
历史柱状图独立选年，月度漏斗及日历独立选月；工作量默认今日。
所有业务统计使用 Asia/Shanghai，自然周从周一开始。近N日包含今日。
接口日期含首尾日，内部使用左闭右开时间窗，当前周期截止查询时刻。
`periodKey` 固定周期优先于日期；自定义日期清除它。`cumulative=true` 从范围内
可读订单/接收事实的最早时间开始，不使用虚构业务起点。

## 计算

- 订单仅 `effective`，按 `submittedAt` 归属，金额为录单明细实际成交金额合计
  `totalAmount`。驳回、待审、终止及替代旧单排除。退款不回扣该成交金额指标。
- 客单价分子、分母同时排除整单0和0.01，其他金额/订单指标保留它们。
- 产品按明细 `payableAmount` 汇总；单产品内订单去重；产品下钻金额仅为所选产品金额。
- 成交率分母为期间新接有效客资与往期接收、60日内期间首购成交客资的并集；
  分子为期间新接期间成交与上述往期成交的并集。各范围按客资去重。
  60日从对应销售接收时刻开始、截止不含，转派后重新起算，复购不增加成交人数。
- 复购仍计业绩、订单数和客单价；来源总览独立为复购组。
  提交客资来源 `internal_new_media/partner` 为引流，`sales_self_sourced` 为非引流；
  其他来源单列未知，不能从名字推断。渠道/产品/分类名称使用业务快照。
- 周目标和月目标独立；季度/年累加3/12个月目标。部门累加个人，中心累加部门。
  人工值与实时自动汇总分开展示；恢复自动汇总不删除修订历史。
  目标不完整或为零时不计算完成率。任意日期区间不擅自分摊目标。
- 跟进按计划到期时间统计，跟单记录按发生时间，漏接来自明确 `timeout` 派单事件，
  不把拒绝或一般任务取消算作漏接。
- 漏斗按所选月接收批次观察截至当前状态；不能混入往期接收本月成交客资。

## 历史与快照

新增 `zsjos_performance_org`、`zsjos_performance_target`、`zsjos_performance_revision`、
`zsjos_performance_attribution` 四张租户表。订单、接收、派单、跟进和任务在业务事务内
固化人员、部门、中心等归属；快照写入失败会使所在业务事务失败，不降级到当前组织。
目标记录保留周期所属组织，修改检查版本并记录原因、前后值和操作者。

既有订单不补造组织快照。个人历史可用订单正式销售和当时真实接收历史计算；
部门/中心历史只使用可证明的冻结组织。页面显示归属快照可用起点，个人可读的缺失
组织订单同时显示数量及金额。无快照不代表该团队过去业绩为零。

判定任务继续使用已保存的规则期限，本功能不修改业务期限。
保存各判定轮次的有效、无效、逾期和结束结果；恢复后使用该接收轮次最早期限
判断是否迟判。原任务超时取消仍为红色，转派终止责任单列，迟到补判保留迟判数量。
缺失历史过程独立显示浅灰，不能显示为判定成功。圆环分项之和必须等于接收数量。

## API

所有接口在 `/admin-api` 下，返回 CommonResult，列表分页使用 PageResult。

| 路径 | 能力 |
| --- | --- |
| GET `/zsjos/sales-performance/tree` | 可选范围与导航树 |
| GET `/zsjos/sales-performance/overview` | 固定周期目标、金额、成交率及当前异常 |
| GET `/zsjos/sales-performance/analysis` | 来源、产品、贡献、客单价及趋势 |
| GET `/zsjos/sales-performance/history` | 指定年份月业绩及同比 |
| GET `/zsjos/sales-performance/leads` | 工作量、分类、阶段、漏斗、跟进；calendar=true返回月历 |
| GET `/zsjos/sales-performance/details` | 对应指标基础明细、分子分母构成及来源/产品过滤 |
| GET `/zsjos/sales-performance-target/tree`、`/list` | 可配置对象与周/月目标 |
| PUT `/zsjos/sales-performance-target/batch` | 原子批量保存、恢复自动汇总 |
| GET `/zsjos/sales-performance-target/history` | 目标修订历史 |
| GET `/zsjos/sales-performance-target/organizations`、`/organization-candidates` | 组织关联及系统候选 |
| PUT `/zsjos/sales-performance-target/organization` | 关联中心或部门 |

查询统一 `scopeType=SELF|USER|DEPT|CENTER`、scopeId；SELF始终绑定登录用户。
时间参数为 start/end、periodKey、cumulative、grain、year。
明细使用 metric、dimension/groupKey、pageNo/pageSize，不返回联系方式。
目标批量最多200条，金额非负、冲刺不低于保底、周一/月初为周期起点、原因必填。

## 部署及验证

V276依赖V275及唯一工作台根菜单，创建空表并缺失式添加8条菜单/按钮元数据。
源文件、期望结构和初始化空表定义同步；不回填业务，不修改管理员已有菜单。
回滚保留目标、修订和快照，仅停用入口及回退代码；不删除新产生记录。
开发库已执行并与受控库比对，完整fresh/upgrade由原有基线差异阻断，见交付日志。
共享服务未重启；已运行旧后端不代表新API已部署，认证运行时联调仍需更新运行版本后验证。

### 员工端时间响应适配

后端 `LocalDateTime` 字段沿用全局毫秒时间戳契约。员工端业绩服务层将 `start/end`（指标周期）、`asOf`、`attributionAvailableSince`、`occurredAt` 和修订记录 `at` 的数值时间按 `Asia/Shanghai` 转换为页面使用的日期时间字符串；`LocalDate` 查询日期和目标周期日期仍为 `YYYY-MM-DD`，空值保持为空。金额、数量与 ID 不参与转换。时间适配在返回页面之前完成，避免目标卡片字符串截取崩溃及明细显示原始时间戳。

## 2026-09-23 本地旧订单归属兼容回填

用户确认仅为本地开发库租户1的263笔已生效、销售/部门/中心关联齐全的订单，
按回填时当前组织补齐 ORDER 归属快照，包含停用账号的11笔；这些记录不是提交时历史组织的证明。
批次通过 creator/updater=`current-org-backfill-20260923` 标识。未变更订单金额、状态、人员归属或账号状态。
其余144笔归属不完整订单不处理；既有快照不覆盖；客资接收/判定/跟进历史不在本次回填范围。
来源除订单明确记录的复购外保持 unknown，未用当前字典标签补造历史来源。

工具：`script/sql/mysql/tools/backfill_performance_orders.py`。仅用于已授权的上述本地范围，
不进入bootstrap或全环境迁移。首次固定263笔私有清单，保存在
`D:/ZSJ-OS-backups/sales-performance-order-backfill-20260923/manifest.json`；
同目录保留执行SQL、执行前备份和verification.txt。清单包含业务数据，不提交仓库。
脚本先在保留的受控数据库执行同一份冻结SQL并验证重复执行，再以--apply执行本地缺失式插入。
本地执行要求候选与冻结清单完全一致；完成后再次运行--apply会拒绝候选变化，不能借重建清单扩大范围。
冻结SQL已验证重复执行不覆盖已有记录。回滚须另行确认，只能针对已记录的新增快照，不能删除后续业务数据。

新订单由提交路径自动写入归属快照，审批通过后按提交时间计入业绩。
销售当前部门及中心关联必须完整，且运行中的后端须包含 PerformanceSnapshotService 提交钩子。
本次未重启服务、未创建真实测试订单，不构成新订单端到端运行验收。
