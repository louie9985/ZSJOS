# 客资提交与派单 API

## 运行边界

- 接口统一位于管理端 `/admin-api/zsjos/lead`，使用当前登录用户和租户上下文。
- ZSJOS JSON 请求和响应中的日期时间统一使用 Unix epoch 毫秒数，例如 `1786258800000`。前端不得提交 ISO 日期字符串；空值使用 `null` 或省略字段。
- URL 查询参数中的日期范围不是 JSON 时间戳，继续按对应请求 VO 的 `@DateTimeFormat` 格式传递；两种契约不得混用。
- 产品由 `LeadProductCatalogPort` 读取。产品 SDK 未交付前，课程接口稳定返回“产品配置服务暂不可用”，提交页禁止提交，不使用静态课程兜底。
- WebSocket 使用现有 `/infra/ws`，消息类型为 `zsjos_lead_assignment`，内容仅包含 `leadId` 和 `eventType`。
- `eventType` 包含 `assigned`、`reassigned`、`accepted`、`rejected`、`expired`、`cancelled`；客户端只将消息作为重新查询信号。
- 待接列表项返回 `remainingSeconds`、`rejectable`、`deferrable` 和 `assignmentHistoryId`。自动派单可拒绝且不可延后，指定派单不可拒绝但可收起稍后处理。
- 待接列表和抢单池中的 `sourceChannel`、`leadCategory` 始终是持久化的稳定字典键；`sourceChannelLabel`、`leadCategoryLabel` 是当前启用字典解析出的展示标签。字典缺项时标签字段为空，客户端必须显示未配置状态，不得把稳定键当作展示标签。

## 员工接口与权限

| 接口 | 权限 |
| --- | --- |
| `GET /zsjos/lead/product/simple-list` | `zsjos:lead:submit` |
| `POST /zsjos/lead/attachment/upload` | `zsjos:lead:submit` |
| `POST /zsjos/lead/create` | `zsjos:lead:submit` |
| `GET /zsjos/lead/assignment/my-pending` | `zsjos:lead:accept` |
| `POST /zsjos/lead/{id}/accept` | `zsjos:lead:accept` + 当前候选对象权限 |
| `POST /zsjos/lead/{id}/reject` | `zsjos:lead:accept` + 当前候选对象权限；仅自动派单 |
| `GET /zsjos/lead/dispatch-status/my` | `zsjos:lead:accept`；返回岗位资格、页面在线和接单偏好 |
| `POST /zsjos/lead/dispatch-status/heartbeat` | `zsjos:lead:accept`；仅启用销售专员进入轮询池 |
| `PUT /zsjos/lead/dispatch-status/mode` | `zsjos:lead:accept`；请求体为 `{ "accepting": true|false }` |
| `POST /zsjos/lead/dispatch-status/offline` | `zsjos:lead:accept`；正常退出时尽力移出轮询池 |
| `GET /zsjos/lead/claim-pool/page` | `zsjos:lead:claim` + 启用销售专员校验 |
| `POST /zsjos/lead/{id}/claim` | `zsjos:lead:claim` + 抢单池对象权限 |
| `GET /zsjos/lead/inbox/submitted/page` | `zsjos:lead:query` + `zsjos:lead:query-submitted` |
| `GET /zsjos/lead/inbox/submitted/filter-profile` | `zsjos:lead:query` + `zsjos:lead:query-submitted` |
| `GET /zsjos/lead/inbox/owned/page` | `zsjos:lead:query` + `zsjos:lead:query-owned` |
| `GET /zsjos/lead/inbox/owned/filter-profile` | `zsjos:lead:query` + `zsjos:lead:query-owned` |
| `POST /zsjos/lead/{id}/judge-valid` | `zsjos:lead:qualify` + 当前负责人对象权限 |
| `POST /zsjos/lead/{id}/judge-invalid` | `zsjos:lead:qualify` + 当前负责人对象权限 |
| `POST /zsjos/lead/qualification/attachment/upload` | `zsjos:lead:qualify`；上传后仍需由判无效命令校验引用归属 |

派单、接单、拒单和超时同时维护 `lead_assignment_accept` 业务任务。接单、抢单和管理员转派在归属事务内创建 `lead_first_follow_up` 任务，截止时间由接单时启用的独立跟进规则计算。

## 管理接口与权限

| 接口 | 权限 |
| --- | --- |
| `GET /zsjos/lead/assignment-rule/get` | `zsjos:lead-rule:query` |
| `PUT /zsjos/lead/assignment-rule/update` | `zsjos:lead-rule:update` |
| `POST /zsjos/lead/{id}/admin-transfer` | `zsjos:lead:transfer` + 客资对象检查 |
| `GET /zsjos/lead-follow-up-rule/get` | `zsjos:lead-follow-up-rule:query` |
| `PUT /zsjos/lead-follow-up-rule/update` | `zsjos:lead-follow-up-rule:update` |
| `GET /zsjos/lead/qualification-exception/page` | `zsjos:lead:qualification:query` + 部门范围或全租户处置权限 |
| `GET /zsjos/lead/{id}/transfer-candidates` | `zsjos:lead:qualification:manage` + 异常客资对象权限 |
| `POST /zsjos/lead/{id}/restore` | `zsjos:lead:qualification:manage` + 异常客资对象权限 |
| `POST /zsjos/lead/{id}/transfer` | `zsjos:lead:qualification:manage` + 异常客资对象权限 |
| `POST /zsjos/lead/{id}/recycle` | `zsjos:lead:qualification:manage` + 异常客资对象权限 |
| `POST /zsjos/lead/{id}/release-to-claim-pool` | `zsjos:lead:qualification:manage` + 异常客资对象权限 |

规则参数范围为接单超时 10–3600 秒、最大尝试 1–20 次。修改只影响之后提交的客资，进行中客资继续使用提交时规则快照。

自动派单候选来自租户隔离的 Redis 轮询池。工作台每 30 秒刷新页面心跳，心跳键 90 秒过期；接单偏好持久化在 `zsjos_sales_dispatch_preference`，首次默认暂停。每次派单最多旋转初始池长度的三倍，跳过离线、暂停、已有待接客资以及当前客资已经尝试过的销售。Redis 原子预留 `lead:lock` 与 `sale:pending` 后，数据库条件更新才确认 `pending_acceptance`；Redis 不是客资状态或归属的事实源。

拒单立即释放预留并重新派发，不对销售实施冷却。自动派单超时继续由数据库 `pending_expires_at` 扫描处理，不能通过扫描已经过期消失的 Redis 键恢复客资。Redis 暂不可用时客资保持 `unassigned`，租户定时任务恢复后重试；三圈确实无人可接或达到最大实际尝试次数时进入抢单池。指定派单不进入在线轮询。
首次跟进时限独立配置，范围为 5–10080 分钟，默认 1440 分钟；有效性判定时限范围为 5–43200 分钟，默认 4320 分钟。两者均在任务创建时固化规则版本和截止时间，修改不追溯已有任务。

## 判定前跟进与今日待办

客资仍为 `submitted` 且分配状态为 `owned` 时，跟进记录归属于 Lead，不提前创建 Opportunity，也不改变 Lead 主状态。当前负责人通过 `POST /zsjos/lead/{id}/follow-ups` 追加记录；当前负责人、提交人、负责人部门及子部门主管和 `zsjos:lead:query-all` 账号可通过 `GET /zsjos/lead/{id}/follow-ups/page` 只读查询。记录时间由服务端生成，记录不提供修改或删除接口。

客资提交、跟进、有效性判定和异常处置中的 `idempotencyKey` 表示一次用户操作意图。前端在打开或重置为新操作时生成一次键，同一操作的快速重复点击、上传失败、网络失败和超时重试必须复用该键，只有服务端确认成功后才能轮换。前端提交状态必须使用同步互斥保护，按钮 loading 仅作为交互反馈，不能作为唯一防重手段。

跟进提交完成当前分配历史对应的 `lead_first_follow_up`，并按可选的下次跟进时间替换 `lead_follow_up_reminder`。`nextFollowUpAt` 必须使用 epoch 毫秒数且换算后的服务端时间晚于提交时刻。`GET /zsjos/business-task/my-summary` 与 `GET /zsjos/business-task/my-page` 只返回当前用户的 ZSJOS 任务，使用 `unscheduled`、`overdue`、`today`、`future` 分组；任务没有通用完成接口，只能由接单或填写跟进等业务动作完成。

首次跟进成功后创建 `lead_qualification` 任务。客资响应由服务端返回正交的 `qualificationStatus`、`followUpStatus`、`assignmentStatus`、`operationalStatus` 和 `availableActions`，并附带首跟截止、判定截止、挂起时间、判定结果、Opportunity 摘要与无效判定附件；附件 URL 在详情读取时重新签名。前端不组合 `status` 和 `assignmentStatus` 自行推断状态或写操作。历史有效客资通过 V019 补齐唯一 `initial_conversion` Opportunity。

`POST /zsjos/lead/{id}/judge-valid` 请求为 `{ leadCategory?: string | null, remark, idempotencyKey }`。非空分类必须是启用的 `zsjos_lead_category` 字典值，备注必填且最长 2000 字。接口在一个事务内完成判定任务、保存备注并创建或恢复该客户唯一的 `initial_conversion` Opportunity；Lead 保持 `valid + owned`，Opportunity 承担后续 `open/following/deal_pending_approval/won/lost` 阶段。恢复旧 Opportunity 时保留历史记录，清除旧流失字段，并把正式负责人同步为 Lead 负责人。`zsjos_lead_valid_remark_template` 只提供管理员维护的快捷备注，初始化为空。判无效请求同时提交启用的 `zsjos_lead_invalid_reason` 字典值、必填备注和最多 9 个已上传附件引用；入口同时适用于待判定 Lead 与推进中的 Opportunity，后者会在同一事务改为 `lost`。`zsjos_lead_invalid_remark_template` 仅提供快捷文案，接口只保存销售最终编辑文本。

统一跟进接口按状态路由：`submitted` 写 Lead 跟进并维护首跟、判定和提醒任务；`valid` 且存在活动 Opportunity 时写 Opportunity 跟进并维护机会状态和提醒；`invalid` 写 Lead 证据记录，不创建任何任务。分类在三类请求中都可保留、修改或清空。分页查询合并 Lead 与 Opportunity 跟进，按发生时间倒序返回，并通过 `recordScope` 标识 `lead` 或 `opportunity`。

`PUT /zsjos/lead/{id}/basic-info` 仅供当前负责人使用，接受姓名、手机号、微信号、地区、可空客资分类、至少一项且唯一主意向的课程快照以及必填修改原因。手机号与微信号至少保留一个；身份冲突、无效地区、无效字典或产品目录引用均拒绝且事务回滚。联系人同步到 Person 和当前 Lead，事件只记录变更字段名和修改原因，不记录完整联系方式。

每分钟按租户扫描已到判定截止时间的 `submitted + owned` 客资，并在行锁下再次校验后改为 `suspended`。截止时间已到但扫描尚未提交时，当前销售仍可判定；扫描先提交后，跟进、判定、资料修改、转派和建单均由服务端拒绝。恢复与转派创建新判定轮次；回收进入 `recycle_pending` 并清除销售；释放进入抢单池，被抢后重新创建首跟任务。

普通主管必须是原销售部门或其上级部门负责人，只能转派给本人管理部门及子部门的启用销售专员。`zsjos:lead:qualification:manage-all` 允许当前租户内跨部门处置，但不绕过租户隔离。超时挂起和主管处置通过既有业务通知机制通知相关销售、操作主管及原销售部门负责人链。

旧的 `POST /zsjos/lead/{id}/admin-transfer` 不接受 `suspended` 或 `recycle_pending` 客资，异常客资必须通过上述专用处置接口，避免绕过理由、轮次重启和分配历史规则。

## 部署顺序

1. 评审并备份目标库，确认历史 `zsjos_lead` 兼容空值策略。
2. 单独确认后按版本顺序执行至 `script/sql/mysql/migrations/V018__lead_actions_and_opportunity_followups.sql`；代码实现不会自动执行迁移。
3. 配置 `zsjos_lead_category`、`zsjos_lead_invalid_reason`、`zsjos_lead_invalid_remark_template` 与 `zsjos_lead_valid_remark_template` 字典数据及岗位对应菜单权限；快捷备注字典类型初始化为空。
4. 接入并验证真实产品 SDK 适配器后才开放提交入口。
5. 使用真实 MySQL、Redis、文件存储和 WebSocket 验证提交、超时转派、并发接单、业务任务及抢单。
6. JVM、MySQL 连接会话和产品展示统一使用 `Asia/Shanghai`。MySQL Connector/J 8 连接必须配置 `connectionTimeZone=Asia/Shanghai&forceConnectionTimeZoneToSession=true`，并通过 `script/sql/mysql/verify-zsjos-time-contract.sql` 只读核验 `@@session.time_zone` 和历史异常。
