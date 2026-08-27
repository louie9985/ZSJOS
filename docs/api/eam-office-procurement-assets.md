# EAM 办公采购、库存与员工资产契约

## 边界

本功能是办公用品、手机、电脑和数字账号等轻量采购，不接 ERP、FMS、供应商主数据、
仓库、库位或商品/SKU 主数据。采购、库存、预留、入退库批次、员工持有和员工资产任务
均归 EAM；HRM 只提供员工公共 API 和同步生命周期事件；审批定义、任务、审批人、加签、
驳回、撤回、抄送和历史均归 BPM。

EAM 直接依赖 `yudao-module-hrm` 的 `api.employee` 包，不读取 HRM 的 DO、Mapper、Service
或表。HRM 本期没有表结构变化，也没有 HRM SQL 模块。

资产归属、领用、归还、流转和盘点统一使用 HRM `employee_id`；EAM 不为这些关系保存
System `userId`，也不复制完整员工档案。申请人、操作人、核验人、当前登录人和 BPM 路由
仍使用 System 用户编号，因为这些字段表达系统行为主体而不是资产归属。

HRM 员工详情的“个人资产”页签由前端直接调用 EAM
`GET /eam/employee-asset/get-by-employee?employeeId=`。HRM 后端不代理该查询，也不保存资产
副本，因此当前后端依赖仍保持单向 `EAM -> HRM API`，不会形成 Maven 循环依赖。

## 分类、快照与库存匹配

- 分类增加可继承的 `deliveryMode`（1 实物入库、2 数字交付）和 `custodyMode`
  （1 消耗型、2 需归还型）。根分类必须配置，子分类可以继承或覆盖。
- 历史分类不会被自动推断；有效策略无法解析时，分类可以查询，但不能用于新需求或采购。
- 需求明细、采购明细和库存余额保存自定义字段值、显示标签及字典类型快照；入退库明细
  另存实际值、实际标签和实际字典类型快照。历史展示不得重新解析当前字典标签。
- 单件候选只来自同分类、同自定义字段签名的闲置资产卡，并排除已预留和仍有持有记录的
  资产。批量候选只来自管理模式、交付模式、持有模式、分类、单位和自定义字段签名均一致
  的库存余额。
- 可用数量固定为 `onHandQuantity - reservedQuantity - frozenQuantity`。候选查询只推荐，
  管理员通过预留命令确认后才占用库存；批量预留使用条件更新，单件预留使用行锁和活动预留
  校验防止重复占用。

## 管理端 API

以下 Controller 均为 Admin 身份，实际 HTTP 前缀为 `/admin-api`。

| 场景 | 方法与路径 | 权限 |
| --- | --- | --- |
| 创建需求 | `POST /eam/demand/create` | `eam:demand:create` |
| 查询需求 | `GET /eam/demand/get?id=`、`GET /eam/demand/list` | `eam:demand:query` |
| 检查库存 | `GET /eam/demand/stock-candidates?demandItemId=` | `eam:stock:query` |
| 预留 | `PUT /eam/demand/reserve` | `eam:stock:allocate` |
| 预留并分配 | `PUT /eam/demand/reserve-and-allocate` | `eam:stock:allocate` |
| 分配已有预留 | `PUT /eam/demand/allocate` | `eam:stock:allocate` |
| 创建采购单 | `POST /eam/purchase/create` | `eam:purchase:create` |
| 查询采购单 | `GET /eam/purchase/get?id=`、`GET /eam/purchase/list` | `eam:purchase:query` |
| 分批入库/交付 | `POST /eam/purchase/{id}/receive` | `eam:purchase:receive` |
| 供应商退货 | `POST /eam/purchase/{id}/supplier-return` | `eam:purchase:return` |
| 少到关闭 | `PUT /eam/purchase/{id}/short-close` | `eam:purchase:close` |
| 提交费用审批 | `POST /eam/purchase/{id}/expense` | `eam:purchase:expense` |
| 库存余额 | `GET /eam/stock/list` | `eam:stock:query` |
| 最低库存 | `PUT /eam/stock/minimum` | `eam:stock:update` |
| 员工个人资产 | `GET /eam/employee-asset/get-by-employee?employeeId=` | `eam:employee-asset:query` |
| 员工资产任务 | `GET /eam/employee-asset/task/get?id=` | `eam:employee-asset:task` |
| 提交配资需求 | `POST /eam/employee-asset/task/{id}/provisioning` | `eam:employee-asset:task` |
| 提交异动/离职复核 | `POST /eam/employee-asset/task/{id}/review` | `eam:employee-asset:task` |
| 退还验收 | `PUT /eam/employee-asset/holding/{id}/inspect-return` | `eam:employee-asset:inspect` |

采购创建可以混合来源需求和行政补库明细。来源需求会冻结本次采购承诺数量；采购流程被拒绝
或取消时释放承诺。审批通过后才允许入库，原采购明细自动带入，入库只补实际数量、价格、
序列号或数字账号信息、实际自定义字段、票据和异常说明。单件入库逐件生成资产卡；序列号可
全部留空，填写时必须与本次数量一致且不能重复。批量入库增加库存余额和流水，定向且需归还
的数量同时生成待签收持有记录。

采购状态为 0 草稿、1 审批中、2 待入库、3 已驳回、4 已取消、5 部分履约、6 已完成。
费用状态独立使用 0 未提交、1 审批中、2 已通过、3 已驳回、4 已取消。入库与退货只追加
批次、明细和库存流水，不覆盖历史数量。

## Workbench API

Workbench 使用以下 `/admin-api/eam/workbench` 接口，不直接调用 Axios，也不根据角色名
制造菜单或操作权限。

| 场景 | 方法与路径 | 权限 |
| --- | --- | --- |
| 我的资产 | `GET /eam/workbench/my-assets` | `eam:workbench:asset:query` |
| 我的采购申请 | `GET /eam/workbench/my-demands` | `eam:workbench:demand:query` |
| 可申请分类 | `GET /eam/workbench/categories` | `eam:workbench:demand:create` |
| 分类采集字段 | `GET /eam/workbench/category-fields?categoryId=` | `eam:workbench:demand:create` |
| 提交前库存预览 | `POST /eam/workbench/stock-candidates` | `eam:workbench:demand:create` |
| 提交采购申请 | `POST /eam/workbench/demand` | `eam:workbench:demand:create` |
| 员工签收 | `PUT /eam/workbench/holding/{id}/sign` | `eam:workbench:asset:sign` |
| 申请退还 | `PUT /eam/workbench/holding/{id}/return` | `eam:workbench:asset:return` |
| 资产报修 | `POST /eam/workbench/repair` | `eam:workbench:asset:repair` |

提交前库存预览按当前分类、单位和已填写的自定义字段返回只读候选，不创建需求或预留。
员工完善申请明细后页面自动刷新预览；审批通过后管理员仍须使用管理端候选查询和原子预留
命令再次确认当时可用库存。

需归还持有记录状态为 0 待签收、1 持有中、2 待退还验收、3 已退还、4 遗失。退还验收
结果为 1 完好、2 损坏、3 缺件/遗失、4 不符驳回；完好回到可用库存，损坏进入冻结库存，
缺件/遗失结清持有但不增加库存，不符驳回恢复持有中。只有单件资产可以发起现有报修流程。

## BPM 契约

四个流程必须由管理员在统一审批中心建模、发布并启用。EAM SQL 不创建流程定义，也不固化
角色、岗位、审批人或会签规则；采购和费用审批人由 BPM 配置。若流程未部署，创建流程实例
失败并与当前 EAM 业务事务一起回滚。

| Process key | Business key | 启动变量 |
| --- | --- | --- |
| `eam_asset_demand` | 需求 ID | `summary`、`employeeId`、`employeeUserId`、可选 `employeeDeptId`、可选 `leaderUserId` |
| `eam_office_purchase` | 采购单 ID | `summary` |
| `eam_purchase_expense` | 采购单 ID | `summary`、`paymentMode`、`actualAmount` |
| `eam_employee_asset_review` | 员工资产任务 ID | 配资时包含 `summary`、员工及直属负责人变量；异动/离职复核当前只保证 `summary` |

BPM `APPROVE` 映射业务已通过或进入履行，`REJECT` 映射已驳回，`CANCEL` 映射已取消。
监听器按 process key 过滤，并只在业务仍处于审批中时更新，因此重复状态事件不会重复入库、
分配或结清。驳回、撤回后的再次提交必须创建新的业务单据或新的员工资产任务，不覆盖 BPM
历史。

## HRM 生命周期

`HrmEmployeeApi` 支持按 `employeeId`、`userId` 和一组员工 ID 查询员工、System 账号、
部门、直属负责人、入职状态和入离职时间。`HrmEmployeeLifecycleEvent` 是 Spring 同步事件，
与 HRM 命令处于同一本地事务；EAM 监听失败会抛回 HRM，避免静默遗漏。

事件类型包括 `ACCOUNT_BOUND`、`ENTRY_CONFIRMED`、`REHIRED`、`CHANGE_EFFECTIVE`、
`QUIT_PLANNED`、`QUIT_CANCELLED` 和 `LEFT`。事件携带 tenant、唯一 `eventKey`、来源记录、
发生时间及员工前后快照。EAM 按事件键幂等创建或更新：

- 账号绑定、确认入职和再入职生成配资任务；未绑定 System `userId` 时不生成配资任务；
  再入职始终生成新任务。
- 调岗、晋升、降级、转正和转全职实际生效后生成资产复核，可逐项选择随人、退回、转交或
  不调整。
- 设置离职和正式离职生成或更新离职结清任务；HRM 正式离职不以资产结清为前置条件。
- 取消离职终止仍运行的资产复核流程、取消未完成结清任务并保留取消审计记录。

退回动作在 BPM 通过后保持履行中，直到退还验收完成。员工资产任务状态为 0 草稿、1 审批中、
2 已通过、3 已驳回、4 已取消、5 履行中、6 已完成。

## 数据库与任务

EAM 数据库模块执行顺序为 V001 至 V008。V007 及 `schema/eam.sql` 创建空业务表和权限菜单，
不创建采购、库存、员工资产或测试数据；`eam_purchase_payment_mode` 只创建空字典类型，选项
由管理员另行配置。V008 删除旧资产归属 System 用户编号，且不会把其数值推断为 HRM 员工
编号；需要保留的开发记录应通过员工选择器重新指定归属。Workbench 根菜单使用相对子路径
`my-assets` 和 `asset-demands`。

`EamStockAlertJob` 使用现有 Job starter 并按租户运行。空参数默认扫描未来 30 天；参数为天数。
任务为低库存和数字资产到期创建按“场景 + 业务对象 + 提醒日期”幂等的查询投影，不直接发送
通知。数字到期日继续读取分类自定义字段键 `package_expiry`。
