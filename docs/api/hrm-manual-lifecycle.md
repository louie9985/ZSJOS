# HRM 人工维护与人事到期提醒

## 业务与权限

租户仅有 1（id=1）。员工档案的聘用形式、入职状态、员工状态、试用期和计划／实际日期由人工维护；不启动审批流程，不通过到期日期推断状态。正式聘用允许正式、试用；非正式聘用允许实习、兼职、劳务、顾问、返聘、外包。试用期范围仍为 0—6 个月。

员工管理继续使用 `hrm:employee:query/create/update/import/export` 等既有权限。员工个人门户只读展示新增人事日期，不允许自行编辑。人事提醒配置使用既有 `hrm:employee-reminder-config:query/update`；消息接收不授予档案查询或修改权限。

普通 `/hrm/employee/update` 请求携带 `version`、客户端生成的 `operationKey` 和修改性质 `changeMode`。默认 `CORRECTION` 修正当前档案并保留前后快照、操作者、原因和操作时间，不补写历史异动，不创建资产或招聘任务。`BUSINESS` 明确提交 `changeType`、`actualTime` 和原因，保存真实人事事实、业务异动并发送既有生命周期事件。实际日期不能在未来。`PLAN` 保存目标快照和计划日期，不改员工当前状态、岗位或实际日期。

服务端返回 `/hrm/employee/change-record/command-options` 的命令类型。类型 4—9 保持原含义；10 入职、11 离职、12 一般人员变更、13 取消离职。命令类型是技术业务契约，不是管理员可任意改写的状态机字典。员工三类字典选择继续来自 System，改变选择时校验有效项并保存标签快照；未改变的历史选择保留原快照。无历史快照的读取显示历史代码，不臆造历史标签。

快捷入职、再入职、转正、转全职、离职和岗位变更复用人工保存事务；这些入口并非修改档案的前置条件。新建与导入属于档案录入，不产生资产配资事件。新建待入职档案沿用待入职字段配置，其他状态沿用既有在职字段配置；管理员配置的隐藏字段不会通过新建表单绕过。离职登记的申请日期、薪资结算日期、原因等仍保留；其原计划日期是登记历史，当前计划以员工主档为准。

## 日期与计划接口

- `regularTime` / `leaveTime`：实际转正／离职日期；`plannedRegularTime` / `plannedLeaveTime`：计划转正／离职日期。都使用既有 epoch-millisecond HTTP 日期契约。
- 一般计划使用 `plannedTime`；业务发生使用 `actualTime`，两者不共用请求字段。转正、离职计划以主档计划字段为唯一当前日期来源。
- 改期或确认已有计划，在普通员工保存请求中提交 `planId`、`planVersion`。服务端同时锁定员工及计划并校验版本；确认时比对计划原快照与当前档案。旧档案已变更或旧计划缺少快照时，需要先核对并修订计划。
- `POST /hrm/employee/change-record/cancel` 接收计划 id、employeeId、version、employeeVersion、operationKey、reason。取消保留历史，不改变当前人事状态。
- 计划状态为 `PENDING`、`SUPERSEDED`、`CONFIRMED`、`CANCELLED`；实际业务及纠错收据为 `APPLIED`，由 changeMode 区分。每次请求保留不可变操作收据、请求摘要及必要的人事字段快照，不保存完整个人资料请求。
- 相同操作标识及相同请求重试成功返回；同标识不同请求拒绝。版本冲突、失效计划、计划快照冲突有独立错误码。返回冲突后用户刷新核对，不能自动重试覆盖。

人员列表、详情、员工门户、日历、查询和 Excel 区分计划与实际。薪资准备及 HR 实际异动统计只消费 `APPLIED + BUSINESS` 的实际发生日期；计划不计入已发生异动，计划离职不截断计薪。既有工资记录、审批快照、历史资产记录不自动重算。

## 消息提醒

`GET/PUT /hrm/employee-reminder/lifecycle-config` 提供 regular、leave、change 三类规则；`POST .../preview` 返回每类有效接收人和待处理事项数。每类分别配置员工部门范围、包含子部门、接收账号／接收部门、提前天数、北京时间触发时刻。默认关闭、提前 1 天、09:00、范围与接收人为空。启用时间由后端维护。

保留原 Quartz handler `hrmEmployeeChangeJob` 以兼容既有调度；其行为已改为仅发送提醒。旧的自动应用转正、离职、异动方法不再改变员工事实。调薪和绩效超期任务不受影响。

三类场景为 `hrm.employee.lifecycle_regular/leave/change`，通过 HRM 所属场景 provider 与 System `NotifyBusinessEventApi` 对接。默认 in_app，企业微信另行在统一消息规则配置；不生成 ZSJOS 业务待办。消息动作使用 `hrm_employee` 业务对象。Admin 和 Workbench 点击前请求员工详情校验访问权；Workbench 复用既有 `/hrm/employee/list?employeeId=...` 嵌入入口。

提前一次、到期一次；advanceDays=0 只有到期一次。同员工／计划／日期／阶段／接收人使用稳定事件键去重。每个事件使用 System FIXED typed ADMIN 接收人，统一消息规则只控制模板和渠道，不能追加人事配置之外的接收人。配置启用前的已过期日期不补发；未处理事项留在页面标记逾期。发送及重试重新检查当前计划、状态、接收人；取消、改期、已完成或不再符合提醒条件时不投递。消息读取不改变人事状态。

## 升级和交付边界

Spring 初始化时，`HrmEmployeeManualLifecycle` 对招聘候选人服务采用延迟注入，避免形成“员工服务 → 人工生命周期服务 → 招聘候选人／职位／渠道服务 → 员工服务”的循环。招聘服务必须持有完成代理包装后的员工服务，保留方法校验及事务能力；不通过允许注入原始对象来绕过启动检查。`HrmEmployeeLifecycleWiringTest` 使用真实的这五个服务 Bean 和 Spring 方法校验代理覆盖该依赖链，外部 API 与 Mapper 使用测试替身。

执行顺序：既有 core 通知与版本结构 → HRM V001/V002 → `script/sql/mysql/migrations/hrm/V003__manual_employee_lifecycle.sql` → 同时发布后端和 Admin；Workbench 发布消息跳转改动。新环境结构同步到 `schema/hrm.sql`，历史 V001/V002 不改。

V003 增加日期、版本、标签快照和计划／收据字段；范围为租户 1。正式员工旧转正日期承接实际日期，试用员工承接计划日期；离职员工旧离职日期承接实际日期，待离职员工承接计划日期。原值保存在 legacy 字段，来源注明“旧系统记录（按状态承接）”；冲突或无法分类的值保留并标记待核实。历史标签不补造。已有预约保留为人工计划；旧计划确认前必须核对。

迁移不修改员工状态、不发送消息、不赋予角色权限。站内信模板和规则只在缺失时定义。缺少 Quartz handler 记录时新增暂停记录；存在的运行状态和周期不覆盖。提醒配置默认关闭，发布后由有权限的管理员确认范围、接收人、模板及调度后启用。

DDL 无法事务回滚；列／索引逐项检查以支持部分执行恢复。数据转换、通知元数据与两个版本登记在带 ROLLBACK/RESIGNAL 的事务中执行，失败不登记成功。回退需事前备份，不能直接降级代码解释拆分后的字段。受控验证脚本：`python script/sql/mysql/verify/hrm-manual-lifecycle.py`，只允许固定的独立测试容器，不连接共享业务库。

本地源码及受控测试不代表共享数据库已升级或运行服务已发布。共享开发库当前仅 HRM V001/V002，须先执行 V003 再运行新代码；数据库写入、服务重启和真实消息启用仍按明确目标另行执行。
