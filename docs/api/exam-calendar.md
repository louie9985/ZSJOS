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

## 自由名称与历史兼容

创建、编辑提交必填 `scheduleName`（手工命名，去除首尾空白，最多 100 字）。不自动生成名称，
不要求产品、SKU 或分类；旧客户端传入的产品、分类和规格字段不再建立关联。保存后的新名称用于
日历、详情、班级考期候选和新的通知快照。发布及发布预览不再解析或校验产品目录。

旧记录的 `scheduleName` 为 NULL 时，只用该记录原有产品/分类及规格标签快照展示历史名称；
不访问当前目录，不批量回填历史。编辑旧草稿时明确保存手工名称并清除旧关联，先捕获旧版本快照，
再递增通知内容版本；已保存通知、班级考期快照不追溯重写。发布/撤销仍执行原生命周期、权限和租户校验。

产品、分类选项接口及查询分类参数仅保留旧客户端兼容；现行 Workbench 不请求它们，也不以分类筛选日历。
考期页面当前仅有 Workbench 实现；Admin 班级创建/编辑复用同一考期候选接口。

开发数据库在 V188 之后显式执行 `script/sql/mysql/exam-calendar-product-scope.sql`，增加可空
`schedule_name` 并放宽旧分类列的 NOT NULL。对应 fresh core 两份定义同步；不修改历史迁移、版本校验和或已有记录。
运行 `python -B script/sql/mysql/tools/test_free_exam.py --apply-dev --fresh` 验证初次/重复/部分失败恢复、
前置条件失败、UTF-8 HEX、完整 fresh 链与开发库字段比较。数据库保留备份及验证库，不自动删除。
已部署环境的版本化升级仍需按其发布流程单独审查，不能静默改写已应用文件校验和。

同日允许多条自由命名记录。未传可选通知参数的维护请求不发送通知，也不创建定时结束任务。

## 日历通知

### 通知按钮权限配置

V282 活跃开发基线补齐 System 按钮元数据，挂在已有「考期日历」页面（73610）下：

| 中文名称 | 权限标识 | 菜单 ID | 含义 |
| --- | --- | --- | --- |
| 发送通知 | zsjos:exam-calendar:notify | 73613 | 指定员工发送、预览及通知记录读取 |
| 全员通知 | zsjos:exam-calendar:notify-all | 73614 | 全员范围附加权限，须同时有发送通知权限 |

「管理考期」仍为 `zsjos:exam-calendar:manage`，不自动包含发送权限。这些是角色授权树中的按钮选项，不新增侧栏页面。管理员在 System「角色管理 → 分配菜单」下勾选；双端角色配置使用服务端菜单源。普通租户还受既有套餐菜单范围约束，套餐未开放时应由管理员配置，本次不自动扩大套餐。

仅新增缺失定义，不写入 `system_role_menu`，不修改原角色授权或已有按钮的名称、排序、停用状态。ID/权限冲突或父页面缺失时停止，不覆盖管理员配置。授权后刷新权限会话，再进入已发布考期详情，底部显示「发送通知」；全员选项还需要上表第二项。Admin 日历旧发送入口的完整适配仍按后续 UI 工作交付，菜单定义不等于该入口已完成。

此补齐适用于当前开发基线；已部署且启用迁移校验和的环境需单独评审升级，不重写原版本账本。验证工具 `script/sql/mysql/tools/test_calendar_notification_permissions.py` 覆盖受控 MySQL 执行、重复、失败及恢复；`--fresh` 验证完整初始化链。`--apply-dev` 仅在明确授权后向本地 `ruoyi-vue-pro` 补四项日历按钮元数据，保留原账本、角色、套餐及业务表。

历史分页、接收人分页、摘要字段和内部事务受理契约与[课程日历通知](course-calendar.md#日历通知)一致，查询使用 `calendarType=EXAM`。历史批次不依赖安排仍处于发布状态；读取历史不等于允许从独立发送入口发送撤销前的旧内容。撤销后的正常请求重试只返回原已受理批次，不新增投递。

`calendarVersion` 新记录从 1 开始；草稿内容未变化的保存保留版本，日期、备注或通知范围快照变化时递增。发布、撤销均递增版本。维护锁定考期记录，不再锁定产品或分类；版本基于锁定后的记录计算。相同范围的历史名称和规格快照不因目录改名而重写。版本控制不替代后续预览凭证、不可变通知快照或发送时的并发复核。

维护事务现同时保存 `zsjos_calendar_notify_snapshot` 不可变快照与 `zsjos_calendar_notify_state` 当前指针；新建草稿、内容变更、发布、撤销均捕获对应版本，即使本次不通知。标题优先使用手工考期名称；未改写的历史记录保留原产品／分类及规格标题，明细保留原快照和日期信息；不重新读取当前目录生成历史正文。旧记录首次维护仅捕获当前基准。撤销保留原发布快照并新增撤销快照，不覆盖旧正文。捕获不投递通知；预览／补发接口及页面仍需接入这套状态，不能据此宣称发送闭环已完成。

已发布考期可通过 `/zsjos/calendar-notification/send` 发送指定员工或全员通知，`calendarType=EXAM`。全员名单在发送时按当前租户启用员工固定，指定名单由 System API 校验。通知接入既有站内信和企业微信场景；`resend=true` 才会生成独立重发事件。发送、预览及历史读取要求 `zsjos:exam-calendar:notify`，全员发送和全员预览同时要求 `zsjos:exam-calendar:notify-all`；管理权限不能替代通知权限，通知也不要求管理权限。

`GET /zsjos/calendar-notification/users` 接收 `calendarType=EXAM`、`keyword`、`pageNo`、`pageSize`，返回标准分页 `{list,total}`，每项含 id、nickname 和可空的 deptId（System 当前部门 ID；增量字段，旧客户端兼容）。员工候选来自 System 当前租户启用员工分页 API，需考期发送权限，无需全员发送权限。批次详情按持久化日历类型复核授权，不能使用课程权限读取考期批次。权限不足错误分别为 `1900092002`（发送）、`1900092003`（全员）。

独立发送采用与[课程日历通知](course-calendar.md#日历通知)相同的预览凭证、必填版本、请求幂等键和接收人去重协议。预览不锁定业务记录；发送锁定考期和通知状态后复核。标题及规格来自同一服务端快照投影，不能伪造正文或事件类型。草稿、撤销状态不能通过独立发送入口发送正常安排；撤销历史补发仍需后续专用流程。Workbench 已发布详情的发送入口使用共用面板，发送权限独立于管理权限，支持指定员工、全员确认、预览失效和再次提醒。Admin 面板、双端维护交互及完整历史仍未完成；隔离响应的浏览器测试不替代真实投递验收。

发布与撤销现接受可选 JSON 通知请求体，直接提交 `operationKey/expectedVersion/send/scope/originalRecipients/userIds/resend/previewToken`，不包装 `notification`；字段、结果查询、幂等及未通知语义见[维护通知参数](course-calendar.md#维护-api-的可选通知参数)。没有请求体时维持原行为。考期新建与草稿编辑不接入发送。

通知及维护预览通过独立代理的 `@ZsjosPermission` 校验考期对象。`ExamScheduleObjectPermissionProvider` 明确复核记录租户和逻辑删除；`notify` 仅要求考期发送权限，`maintain` 和原 update/publish/revoke 动作要求管理权限。已提交请求重放仍先验证原操作身份并返回原结果，不因考期撤销而新发消息。对象检查不重新设计员工查看范围。

维护预览提交 `calendarType=EXAM`、`calendarId`、`maintenanceAction=PUBLISHED/REVOKED` 及通知范围，不提交课程内容。发布预览使用已保存的考期名称与日期，提交时仍锁定考期并校验生命周期；两次快照不同则回滚并要求重新预览。撤销预览保留既有冻结范围，仅改变生命周期及版本。草稿不可预览撤销，已发布记录不可再次预览发布。维护仍要求考期管理权限，缺少通知权限只影响本次通知；实际跨模块事务和双端维护流程尚待验证。

### 工作台通知员工树

考期、课程共用通知面板的指定员工改为部门／员工树多选。部门层级来自 /system/dept/simple-list，员工来自有对应发送权限的日历候选分页接口；完整加载所有候选页后才开放勾选，避免只选到第一页。支持搜索部门或员工、跨部门多选、勾选部门及下属部门、移除和清空已选人员；未分配或部门不可用的员工保留为根层员工，不推断部门。搜索和折叠不清除已选人员，提交仅包含员工 ID，修改选人须重新预览。加载失败或候选分页发生变化时阻止预览并提供重试，发送仍由后端复核有效性及租户。全员权限与人数确认不变。Admin 兼容新增 deptId 字段，保留原有交互，本次只调整截图所示 Workbench 选择器。
