# 课程日历 API

课程安排归属 ZSJOS，按租户隔离。租户内持有 `zsjos:course-calendar:query` 的用户可查询，持有
`zsjos:course-calendar:manage` 的用户可新增、修改和逻辑删除。每条记录是一次课程时间段，时间精确到分钟。

固定字段为课程名称、课程形式、课程时间、备注和附件。课程形式使用 System 字典 `zsjos_course_form`，初始值
`LIVE=直播`；业务记录保存 value 和选择时的 label 快照。附件通过 Infra FileApi 管理，业务记录保存文件 ID。

接口：`GET /admin-api/zsjos/course-calendar/page`、`GET /admin-api/zsjos/course-calendar/{id}`、
`POST /admin-api/zsjos/course-calendar`、`PUT /admin-api/zsjos/course-calendar/{id}`、
`DELETE /admin-api/zsjos/course-calendar/{id}`。

`calendarVersion` 新记录从 1 开始。修改与删除先锁定课程记录；名称、课程形式、时间或备注发生实际变化时才递增通知版本，内容相同的保存保留版本。附件不属于当前通知正文，单独更换附件不递增通知版本。课程形式值未改变时保留历史标签，不因管理员改名或停用字典项而重写；重新选择其他形式才获取新标签。清空备注显式写入空值。逻辑删除前递增版本并保存 `DELETED` 快照。

维护事务同步保存不可变通知快照和当前通知状态，均归属 ZSJOS。新建、变更、删除即使未选择发送也保留快照；旧记录首次维护时只捕获当前内容基准，不编造更早的历史。相同版本重复捕获复用原快照；同版本内容不一致或状态指针回退版本时拒绝写入。课程形式历史标签包含在标题及明细快照中。删除后的通知状态与快照继续保留，补发接口和页面仍需后续接入；保存快照本身不发送消息。

## 日历通知

### 通知按钮权限配置

V282 活跃开发基线补齐 System 按钮元数据，挂在已有「课程日历」页面（73630）下：

| 中文名称 | 权限标识 | 菜单 ID |
| --- | --- | --- |
| 发送通知 | zsjos:course-calendar:notify | 73633 |
| 全员通知 | zsjos:course-calendar:notify-all | 73634 |

这是角色管理的按钮授权，不新增侧栏菜单。「全员通知」同时要求两项权限；管理课程权限不隐含发送权限。管理员通过 System 角色管理配置；迁移不自动分配角色、不扩大租户套餐。已有定义的名称、排序和停用状态在重跑时保留。Workbench 月历课程旁及当天时间轴使用上述发送权限；Admin 的旧 `zsjos:calendar:notify` 按钮尚未完成新版发送契约适配，不能通过继续发放旧权限替代修复。

执行前提、受控验证及仅限本地的元数据修正工具见[考期通知按钮配置](exam-calendar.md#通知按钮权限配置)。本次补齐权限定义不代表维护通知、历史补发或双端生产闭环已全部完成。

Workbench 的日历单元格和当天详情现通过共用 `CalendarNotificationPanel` 独立发送，按课程 `notify` 权限显示入口，`notify-all` 控制全员选项。面板支持员工分页搜索、多选、预览、全员人数确认及明确再次提醒；输入变化立即作废旧预览，提交失败保留选择和请求键，版本／凭证／名单冲突要求重新预览。提交结果只表示受理，不表示企微送达。Admin、维护时通知、完整历史及渠道结果仍在实施，不能按生产闭环验收。

具备通知权限的员工可通过 `/zsjos/calendar-notification/send` 发送指定员工或全员通知。`scope` 为 `SPECIFIED` 时提交 `userIds`，为 `ALL` 时由服务端固定发送时刻当前租户的启用员工名单；员工状态和租户边界由 System API 校验。通知复用 System 站内信和企业微信投递。发送、预览及历史读取要求 `zsjos:course-calendar:notify`；全员发送和全员预览同时要求 `zsjos:course-calendar:notify-all`。管理权限不隐含通知权限，发送不要求管理权限。

`GET /zsjos/calendar-notification/users` 接收 `calendarType=COURSE`、`keyword`、`pageNo`、`pageSize`，返回标准分页 `{list,total}`，每项含 id、nickname 和可空的 deptId（System 当前部门 ID；增量字段，旧客户端兼容），由 System 查询当前租户启用员工。候选查询只要求课程发送权限。批次详情按实际类型复核权限及租户，不接受客户端指定另一类型绕过授权。权限不足错误分别为 `1900092002`（发送）、`1900092003`（全员）。

独立发送先调用 `preview`，返回 `calendarVersion`、`previewToken`、`contentHash`、候选人数、所选人员中已受理人数和新增人数。凭证有效期 10 分钟，绑定租户、操作者、范围、所选人员、重发选择、内容和实际名单摘要。`send` 必须提交该版本、凭证和非空 `idempotencyKey`；服务端锁定日历并复核，不能仅上传标题或时间直接发送。输入、内容或全员名单变化后重新预览。

相同请求键及内容返回原批次；同键不同内容返回 `1900092013`。不同请求键仍受同安排、同版本、同 ADMIN 接收人的业务去重约束；`resend=true` 使用独立轮次。结果包含 `batchId`、`acceptedCount`、`skippedCount`、`status`。全部已受理时创建 `SKIPPED` 记录但不发布空名单事件。无站内信／企微规则时返回 `1900092015`，批次、接收人和 System outbox 由调用事务共同回滚。站内／企微后续投递失败仍由渠道重试处理。

版本冲突、预览失效、名单变化、无效人员分别为 `1900092008`、`1900092009`、`1900092010`、`1900092011`；指定名单中缺失／停用／外租户人员统一为不可选，不泄露其他租户身份。维护时通知意图、Admin 面板、历史补发及真实并发／事务验收仍在推进。

`CalendarNotificationAcceptanceService` 是独立发送与维护恢复共用的内部受理边界，要求已有 `READ_COMMITTED` 事务，不自行提交。调用方先验证权限、预览及员工，按日历记录、通知状态顺序加锁；受理服务复核当前快照指针、租户和生命周期，写入批次及接收人，并调用 System `publishDurable`。内部历史名单可保留不可用人员的跳过原因，不能因此扩大有效接收人。维护入口现已接入持久化意图，双端交互及真实事务验证仍待完成。

已提交请求重试先按租户、操作人及请求摘要匹配原批次；之后安排删除、撤销或预览过期不影响返回原受理结果，但仍校验当前发送权限。首次请求仍要求有效安排及预览。等待日历／状态锁后再次检查幂等键，避免等待期间已受理的同一请求重复创建。

通知历史接口：

- `GET /zsjos/calendar-notification/batch-page`：必传 `calendarType`，可按 `calendarId`、`status` 过滤，返回 `{list,total}`；按当前租户、已授权类型查询，批次编号倒序，保留撤销／删除安排的记录。
- `GET /zsjos/calendar-notification/batch/{id}`：返回批次快照、范围、版本、事件、受理状态、`requestedCount/acceptedCount/skippedCount` 和创建时间；不再内嵌全量接收人。
- `GET /zsjos/calendar-notification/batch/{id}/recipients`：按批次实际类型复核权限，再分页返回人员历史姓名、身份类型、受理状态、跳过原因、消息引用及完成时间。当前结果尚未包含两个渠道的最终投递事实。

两类分页均使用 `pageNo/pageSize`，每页 1 至 200 条，禁止 `-1` 无界读取。历史人员名称来自保存时快照，不查询当前员工姓名替换历史含义。发送和预览响应现在使用明确 VO，字段与既有前端协议一致。

批次详情与接收人分页在 Service 方法上声明 `@ZsjosPermission`，由 `CalendarNotifyBatchObjectPermissionProvider` 检查当前租户的保留批次、逻辑删除及实际日历类型对应发送权限。业务课程删除不会使其历史批次不可读；批次自身逻辑删除则不可读。不增加管理权限或全员权限作为读取历史的附加条件。

独立发送和预览、带通知参数的维护及其预览，经过独立 Spring 代理 `CalendarNotificationObjectAccess` 上的 `@ZsjosPermission` 方法。两类日历分别验证真实记录的租户、逻辑删除和对应动作权限；通知动作不要求管理权限，维护动作不要求通知权限。范围为全员的附加权限仍由调用入口复核。此代理只检查业务对象，不把新建请求映射到虚构 ID；新建使用已有管理权限边界。已提交请求先验证操作者、租户和摘要并返回原结果，无须再次读取已删除业务记录。并发删除导致授权读不到对象时，维护包装仍重新查询匹配的已提交操作；查询不到则保留原拒绝。原维护领域接口及恢复后台调用仍遵循各自入口契约。

### 维护通知意图与恢复基础

`CalendarNotificationIntentService.record` 只能加入已有业务事务，在业务快照已保存、预览及操作身份已验证后，保存 `zsjos_calendar_notify_intent`。记录包含操作键、请求摘要、原操作人、快照引用、固定人员姓名快照和独立受理键。同租户相同操作键只对应一条意图；不同请求摘要或操作人返回幂等冲突。维护调用方仍须在执行维护前查询操作结果，并处理并发请求事务回滚后的结果重取，不能把该记录方法误当成完整维护幂等包装。

发送／全员权限不足时记录 `SKIPPED` 及稳定原因，不因缺少通知权限抛出维护失败。原名单为空时记录 `NO_RECIPIENTS`。有资格受理的记录为 `PENDING`；事务提交后尝试独立受理，事务回滚不执行提交后监听器。持久化意图是恢复依据，内存事件仅用于及时触发。

`CalendarNotificationIntentDispatcher` 复用现有 Spring 调度、租户及维护模式设施，默认每 30 秒按有效租户读取最多 50 条到期意图；这只恢复已确认通知，不是考前或开课提醒。维护模式下暂不受理。工作事务使用 `REQUIRES_NEW/READ_COMMITTED`，按业务记录、通知状态、意图记录顺序加锁，再复用受理服务；批次、固定接收人、System outbox 及意图受理结果在同一事务提交。

恢复不重新展开全员名单。重新检查原操作人状态和当前权限；人员只减不增，停用、删除或不可访问的员工保留原姓名并记录 `EMPLOYEE_UNAVAILABLE`。快照已被新版本替代则将意图置为 `SUPERSEDED/CONTENT_CHANGED`，不发送过时安排；后续发送应从当前快照重新确认。原操作人失效或权限撤销则记为 `SKIPPED`，不撤销已完成维护。

配置不可用或临时数据库故障保持 `PENDING`，按 30 秒起、最长 15 分钟的退避恢复；非暂时异常记为 `FAILED`，避免无限重试。只保存错误码，不保存异常原文或敏感请求。失败记录使用独立事务且仅更新仍为 `PENDING` 的记录，不能覆盖另一执行者已完成的受理。`SUBMITTED` 仍只代表提交 System 投递。

### 维护 API 的可选通知参数

新增与编辑课程的请求可附带 `notification` 对象；`DELETE /zsjos/course-calendar/{id}` 的可选 JSON 请求体直接为通知参数对象，不额外包装 `notification`。未传时继续原维护流程，不创建发送意图；新前端传 `send=false` 时显式记录取消通知，不能把省略参数理解为通知默认开启。

| 字段 | 契约 |
|---|---|
| `operationKey` | 必填，1–128 字符；客户端为一次维护操作生成并在重试时保留 |
| `expectedVersion` | 必填，新增为 0，已有记录为预览返回的基础版本 |
| `send` | 对象存在时默认 true；false 表示取消本次通知 |
| `scope` | 默认 SPECIFIED；ALL 同时要求全员通知权限 |
| `originalRecipients` | 默认 false；true 使用历史批次人员并集，仅允许 SPECIFIED 且不另传非空 userIds |
| `userIds` | 指定员工名单；明确选择的失效员工阻止提交 |
| `resend` | 默认 false；true 明确允许再次提醒 |
| `previewToken` | 请求发送且有发送权限时必填，必须对应此次维护内容和名单 |

待保存内容预览使用 `POST /zsjos/calendar-notification/preview`：提交 `calendarType=COURSE`、`maintenanceAction=CREATED/UPDATED/DELETED`、已有记录的 `calendarId`、范围及人员参数。新增／编辑附带 `courseContent`，其字段与课程保存请求相同；删除不传内容，后端读取原记录生成删除快照。新增不传日历 ID。响应的 `expectedVersion` 是提交前基础版本，`calendarVersion` 是预计保存后的版本；另返回 `eventType`、`invalidRecipientCount` 以及既有预览人数、正文和凭证字段。预览需管理和对应通知权限。

原接收人按当前租户、安排类型和 ID 汇总历史批次，人员去重并保留最近一次姓名快照。历史人员已停用、删除或不可访问时保留跳过原因；不会像新指定名单一样阻止其他人员。原名单为空记录 `NO_RECIPIENTS`。全员名单变化或预览过期要求重新预览；保存后投影与预览不同会回滚本次维护。

`CalendarMaintenanceNotificationService` 在独立 READ_COMMITTED 业务事务内执行原领域维护并保存意图，事务提交后再尝试通知受理。管理权限不足返回 `1900092016`；发送或全员权限不足不阻止维护，而记录未通知原因。`send=false` 记录 `SKIPPED/CANCELLED`，不检查通知权限或要求预览。重复操作键先查询原结果；并发插入失败后在失败事务结束后重读已提交结果，避免重试重复创建课程。

维护响应保持原来的新增 ID／编辑删除布尔值。调用 `GET /zsjos/calendar-notification/operation?operationKey=...` 查询 `calendarId/calendarType/eventType/notificationStatus/reasonCode/batchId`。仅当前租户原操作人且仍具备对应管理权限可查，无须通知权限；不可访问的键返回 `1900092017`。`PENDING` 表示待受理，`SUBMITTED` 表示已提交 System，不能当作最终送达。查询失败时不得自动重复创建业务记录，应保留操作键重试查询或原维护请求。

当前验证覆盖维护编排、取消、冲突、重放及领域只读预览的单元测试，历史接收人查询以真实 MyBatis/H2 执行。对象授权使用真实 AOP 代理进行允许／拒绝测试，DAL 和 System 权限接口仍为测试替身。模拟事务管理器测试不证明真实 MySQL 回滚和并发。真实租户拦截器与 HTTP 方法安全、跨模块真实事务、进程中断恢复、DELETE 请求体端到端传输及双端维护交互仍待验收；本节不代表生产闭环完成。

### 工作台通知员工树

考期、课程共用通知面板的指定员工改为部门／员工树多选。部门层级来自 /system/dept/simple-list，员工来自有对应发送权限的日历候选分页接口；完整加载所有候选页后才开放勾选，避免只选到第一页。支持搜索部门或员工、跨部门多选、勾选部门及下属部门、移除和清空已选人员；未分配或部门不可用的员工保留为根层员工，不推断部门。搜索和折叠不清除已选人员，提交仅包含员工 ID，修改选人须重新预览。加载失败或候选分页发生变化时阻止预览并提供重试，发送仍由后端复核有效性及租户。全员权限与人数确认不变。Admin 兼容新增 deptId 字段，保留原有交互，本次只调整截图所示 Workbench 选择器。
