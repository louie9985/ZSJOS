# API 约定

## 客资备注历史

详情使用 `remarkHistory` 逐条显示首次、补充和有来源依据的历史备注；`remarkHistoryIncomplete`
表示部分旧证据无法还原。仅旧接口缺少该字段时回退到 `remark`，空数组不回退。
补充备注默认空白、只追加，未填写不清空原备注；同一内容失败重试保留幂等键。
列表备注字段仍为原有单值，不表示全部补充内容。完整契约见 `docs/api/zsjos-lead-submitter-actions.md`。

## 客资销售反馈

客资详情新增服务端投影的 `submitter-feedback` 页签与 `REPLY_SUBMITTER` 动作，
`version` 是发送命令的并发版本。typed service 位于 `leadSubmitterFeedback.ts`，
使用现有 ADMIN 认证与租户请求层；读取/创建权限分别为
`zsjos:lead:submitter-feedback:read` 和 `zsjos:lead:submitter-feedback:create`。
消息 `zsjos.lead.submitter_feedback_created` 跳到反馈页签，完整约定见
[客资销售反馈](../../../docs/api/lead-submitter-feedback.md)。

## 员工头像

`GET /admin-api/system/auth/get-permission-info` 在 `user.avatar` 返回当前员工个人头像，并在
顶层可选字段 `defaultAvatar` 返回全平台默认员工头像。员工头像固定按
`个人头像 > 默认头像 > 昵称首字` 展示；图片加载失败时继续使用下一层兜底。

员工列表仍由各自接口返回 System 用户的个人 `avatar`。下属销售响应新增从 System 用户 API
透传的 `avatar`。Workbench 不把默认头像写入员工记录，也不直接请求 Infra 参数配置接口。
个人头像通过 `POST /admin-api/infra/file/avatar/upload` 上传，目录固定为 `employee/avatar`；
响应是稳定的 `/admin-api/infra/file/avatar/{fileId}` 访问地址，不是私有桶临时签名地址。
读取接口只接受 Infra 已登记的员工头像目录与 JPG、PNG、WebP 内容。

员工工作台通过 typed service 调用现有 system 和业务接口，不建立通用 `yudao-module-zsjos` 工作台聚合接口。中世健自建业务可以提供聚焦的 `/zsjos/**` 接口，例如客资派单能力，但不能复制 system 或 CRM 已有契约。

- `POST /admin-api/system/auth/login`
- `POST /admin-api/system/auth/logout`
- `POST /admin-api/system/auth/refresh-token?refreshToken=...`
- `GET /admin-api/system/auth/get-permission-info`
- `GET /admin-api/system/notify-message/get-unread-count`
- `GET /admin-api/system/notify-message/get-unread-list`
- `GET /admin-api/system/notify-message/my-page?pageNo=...&pageSize=...&readStatus=...`
- `PUT /admin-api/system/notify-message/update-read`
- `PUT /admin-api/system/notify-message/update-all-read`
- `GET /admin-api/system/area/tree`

消息页仍使用 `/messages/all` 与 `/messages/unread` 进入同一个收件箱页面；页面内按 `全部 / 客资 / 提现 / 收益 / 申诉 / 系统` 做前端业务分类聚合，当前不新增后端分类筛选参数。

菜单来自权限接口返回的 `data.menus`，这是员工工作台菜单的唯一事实源。前端只做路径规范化和布局所需的展示转换，并保留服务端递归层级；不按角色名称推断菜单，也不使用管理接口或静态数组重建另一套权限事实。后端 `component` 只作为显示和本地注册表映射信息，不能作为动态代码执行。

所有 HTTP 路径、租户头、Token 刷新和响应解包集中在 `src/services`。组件不得直接调用 Axios。

## 第三方账号与内容生产

### 内容版本与拍剪工单内容项

- `GET /zsjos/content/version/list?contentId={id}` 返回租户范围内的内容版本历史。
- `POST /zsjos/content/version/create` 创建内容版本快照，支持 `stage`、素材/成品字段和幂等键。
- `POST /zsjos/content/version/{id}/review` 记录验收通过或退回及原因。
- `GET /zsjos/production-tickets/items/list?ticketId={id}` 查询工单关联的多个内容项。
- `POST /zsjos/production-tickets/items/add` 与 `/remove` 管理内容项，并执行工单对象权限和租户归属校验。

### 工作台扩展

- `/zsjos/positioning/workspace` 提供采访导入确认、定位版本历史和三方执行卡签字。

新媒体业务接口由 `yudao-module-zsjos` 提供，前缀为 `/admin-api`：

- `POST /zsjos/media-account/create`：从当前学员创建第三方账号，请求必须携带当前 `serviceRelationId`、关系 `version` 和稳定 `idempotencyKey`。服务端累计校验创建功能权限、服务关系对象权限、当前责任编导和定位访谈完成状态；精确重放返回原账号 ID，同键异载荷拒绝。学员和编导不可由客户端改写，`accountNo` 由服务端生成；若学员已有唯一现任运营，则使用该运营作为账号负责人。
- `GET /zsjos/media-account/get?id=...`：查询账号详情。
- `POST /zsjos/media-account/{id}/bind-student?studentPersonId=...`：绑定学员并保留历史。
- `GET /zsjos/media-account/student-candidates`：返回当前用户有权访问的精简学员候选；仅具备 `zsjos:media-account:query-all` 的管理员可查询租户全量候选。工作台学员中心创建账号时优先使用当前学员上下文，不得用该接口扩大可见范围。
- `POST /zsjos/media-account/{id}/unbind-student`：解绑学员并保留历史。
- `PUT /zsjos/media-account/{id}/maintenance`：已退役写入，返回账号档案升级提示；改用 `/profile` 字段变更接口，自动状态不可手填。
- `GET /zsjos/media-account/{id}/maintenance-history`：分页查看维护版本，接受账号查询或维护功能权限并叠加账号对象读取权限；账号投影仅在两层权限都通过时返回 `VIEW_ACCOUNT_HISTORY`。账号页已按截图收口，不再展示或请求此旧维护历史及旧阶段历史；新档案记录使用 `/profile/history` 和 `canViewHistory`。
- Workbench 将每个账号作为“概览”的同级昵称标签：标签内按「账号定位卡 / 账号状态 / 账号复盘记录」三列展示档案，移除旧独立定位卡、内容和拍剪区块；维护使用 profile 的 `editableFields`，新历史使用 `canViewHistory`。新链接使用 `accountId`，并兼容读取 `tab=account-{id}`、`contentId` 和 `positioningCardId` 深链。
- `GET /zsjos/media-account/calendar`：查询与日期窗口相交的当前账号区间，并返回当前范围下的未排期数量；普通用户限本人所属编导/运营账号，`zsjos:media-calendar:query-all` 扩展为全量。
- `GET /zsjos/personal-calendar`：查询当前登录用户的个人手工日程；新增、修改、删除使用同路径 REST 命令和独立按钮权限，客户端不提交 owner。
- `POST /zsjos/media-account/{id}/advance-stage`、`rollback-stage`：旧阶段推进/回退路由已移除；旧客户端请求按标准 404 处理。阶段仅展示现有系统来源快照；新账号等待来源，不再人工选择。
- `PUT /zsjos/media-account/{id}`：旧全量写接口已退役；使用 `/profile` 携带账号/配置版本、幂等键和字段 changes。
- `POST /zsjos/media-account/{id}/rescue`：更新挽救状态，必须携带版本号。
- `POST /zsjos/media-account/{id}/request-rebind`：发起账号换绑 BPM，必须携带目标学员和版本号。
- `POST /zsjos/content/create`、`GET /zsjos/content/get`、`GET /zsjos/content/page`：内容查询；状态命令分别使用 `complete-topic`、`submit-production`、`submit-acceptance`、`approve-acceptance`、`reject-acceptance`、`start-revision`、`resubmit-production`。
- `GET /zsjos/production-ticket/create-context?accountId=` 返回账号字段、已确认定位卡快照和关系/权限交集候选人；`POST /zsjos/production-ticket/create` 接收账号、可选受派人、最多 500 字的可选运营备注和幂等键。定位卡按冻结模板字段、字典标签和历史分区渲染；运营备注与账号、定位卡一起冻结在派单快照中，供待接单和工单详情展示。独立页面提供待接单、我的工单和公共池视图；`GET /zsjos/production-ticket/assignment/my-pending`、`POST /zsjos/production-ticket/{id}/reject-assignment`、`GET /zsjos/production-ticket/pool/page` 与 `POST /zsjos/production-ticket/{id}/claim` 支持永久指定待接、拒接入池和并发抢单。创建、拒接和抢单按租户、操作人、幂等键及请求指纹保存结果；填写运营备注时创建指纹包含规范化后的备注，未填写时保留原无备注指纹契约；相同请求重放原成功结果且不重复发送事件，不同参数复用键返回稳定幂等冲突。状态命令继续使用 `accept`、`start-production`、`submit`、`start-check`、`approve`、`reject`、`reaccept`。
- `POST /zsjos/positioning-card/create`、`GET /zsjos/positioning-card/get`、`GET /zsjos/positioning-card/page`：定位卡查询；命令使用 `submit-review`、`operator-confirm`、`operator-reject`、`student-link`、`start-revision`。运营确认和退回分别受独立功能权限控制，`query-all` 只扩大读取范围。
- `GET /zsjos/positioning-card/import-sources` 由服务端返回同一学员当前账号和其他账号的可读已提交版本；`POST /zsjos/positioning-card/import` 按当前发布模板把选中提交映射到目标草稿。Workbench 不从详情投影自行拼来源，不导入其他账号草稿；覆盖已有草稿前确认并提交当前 `draftId + version`。

所有详情和分页响应均为 RespVO，并返回服务端计算的 `availableActions`。定位卡统一路径为 `co_creating -> operator_feasibility -> student_link_pending -> student_confirm -> confirmed`；`professionalRisk` 仅保留为业务快照，不再改变审核路径。运营退回或学员提出修改均回到 `co_creating`，由原 `content_director` 修改后重新提交。确认后的再次修改使用 `start-revision`，修订审核期间旧 `effective` 提交继续供下游使用，新提交经学员确认后成为可应用候选，账号由编导或运营手动换版。历史 `trial_14d/student_agreed` 数据不迁移，通过运行时兼容为有效版本；历史 IP BPM 监听器仅处理发布前已在途实例。定位岗位统一使用 `content_director`。所有写操作同时受菜单/按钮权限、对象权限和乐观锁版本约束；分页额外受责任人和部门数据范围约束。

`/zsjos/accounts`、`/zsjos/content` 和 `/zsjos/positioning` 不再注册为页面入口。第三方账号档案从 `/zsjos/media-students` 的具体学员标签维护；该账号页不再发起旧定位卡、内容生产和拍剪流程，相关后端能力保留，拍剪工单独立页面不变。按钮只在服务端下发对应权限且对象 `availableActions` 允许时显示。

- `GET /zsjos/media-account-field-config/published` 返回当前租户已发布的版本化字段定义。V209 发布 57 项档案字段，包含责任、分区、完成提醒和来源配置；选择类字段的选项来自定义指定的 System 字典类型。
- 账号保存 `detailConfigVersionId`、`detailValues` 与字段名称/字典标签快照。历史记录展示保存时快照，不重新解析当前字典；旧记录没有值时显示“未记录”。
- `GET /zsjos/media-students/{personId}` 返回账号、定位、内容、交谈记录、按业务更新时间排序的操作时间线、学员级 `studentTaskLine`、逐账号 `accounts[].taskLine` 和待处理统计；服务端先验证当前用户是否在该学员的媒体业务范围内。页面按所选课程服务的真实 `leadId` 读取完整学员档案，并复用“我的学员”的客户档案、来源渠道、地区、成交课程、备注附件等概览结构；销售联系和客资流转不会混入媒体概览。
- 定位访谈配置入口为 `/zsjos/director-config/interview-template`，页面名“定位访谈大纲配置”，仅渲染服务端授权菜单。配置读写统一调用 `/zsjos/positioning-interview-template`（`director_positioning_interview` 场景），继续使用服务器配置的 `zsjos:director-interview-template:query/update/publish` 权限。发布版本只读，修改需复制草稿、保存后发布。
- 学员定位访谈使用独立 `positioningInterviewApi` 获取字段、草稿和完成快照；旧 `directorForms.interview` 只保留存量快照读取，未填写者不再返回旧模板。旧采访配置及 `/interview/draft|submit` API 已移除，不再提供采访表单编辑。历史地区和字典标签继续展示原快照。
- `GET /zsjos/media-students/target?bizType=...&bizId=...` 将受权业务对象解析为 `personId`、`targetTab` 和记录 ID，供待办与通知构造受控深链。未绑定学员的历史对象不得回退到退役页面。

认证失败既可能使用 HTTP 401，也可能使用 HTTP 200 包裹业务码 `401`。工作台对两种响应执行同一套单次刷新与请求回放；刷新失败通过全局事件立即卸载工作台并进入登录页。HTTP 403 保留当前会话并显示无权限，网络错误和服务端错误保留独立的重试状态。

`/messages/all` 调用 `my-page` 获取当前用户全部消息；`/messages/unread` 固定传递 `readStatus=false`。完整消息页的左侧列表采用 cursor 懒加载，不提供前端分页控件。两个页面均由权限接口中的服务端菜单决定是否可见，前端不自行制造入口权限。

消息中心和实时消息弹窗的业务跳转会携带新的导航上下文。目标页面即使已经处于当前路由，也必须重新读取消息指向的列表/详情数据；当前筛选条件保留，目标记录不在筛选结果时临时置顶。消息导航会清理未提交的临时表单状态，以服务端最新详情为准，不使用浏览器整站刷新。

WebSocket 使用 `/infra/ws?token=...`，不带 `/admin-api` 前缀。当前消费 `notify-message-new` 和 `zsjos_lead_assignment`；事件只触发对应 HTTP 数据刷新，不替代站内信或客资业务记录。

`GET /admin-api/zsjos/lead-follow-up-rule/runtime-setting` 返回当前租户右下角消息浮窗时长，工作台按分钟换算为秒，非法值或请求失败使用 5 分钟默认值。该配置不影响待接单功能弹窗。

`PUT /admin-api/zsjos/lead-follow-up-rule/update` 必须回传最近一次读取到的 `version`。服务端只在版本仍一致时更新并递增版本；并发管理员已先保存时返回稳定冲突错误 `1_900_003_079`，客户端应提示刷新后重试，不得用旧表单覆盖新配置。

具备 `zsjos:lead:accept` 的工作台调用 `/zsjos/lead/dispatch-status/my`、`heartbeat`、`mode` 和 `offline` 维护销售页面在线与接单偏好。前端权限只决定是否发起请求和展示控件，后端仍通过销售专员岗位资格决定 `eligible`。WebSocket 断开时工作台停止发送在线心跳并尽力调用 offline；Redis TTL 负责异常关页兜底。

客资提交页从 `/system/area/tree` 读取启用的中国地区树，以两级 `Cascader` 展示省、市，不使用地区字典、静态省市数组，也不在前端补造“其他”。节点提交值来自后端 `selectionCode`；支持数据库配置的 `OTHER + OTHER` 和具体省份 `+ OTHER`。香港、澳门等标记为 `leafSelectable` 的省级节点可直接选择，前端仍按既有契约提交对应 `provinceCode` 与 `cityCode=OTHER`。同级普通地区顺序使用后端 `sort`，System 服务始终把“其他”固定在末尾，管理员可调整普通地区顺序。地区请求拥有独立的加载、空态、错误和重试状态，课程或字典请求失败不会清空已加载地区。

### 内容验收退回

`POST /admin-api/zsjos/content/{id}/reject-acceptance` 使用明确业务命令参数 `version` 与必填 `reason`。原因去除首尾空白后必须为 1-500 个字符；成功后内容进入 `rejected`、乐观锁版本递增、`rejectCount` 原子递增，并将原因写入业务状态事件。Workbench 必须先采集原因再发送命令，不得使用固定说明代替操作人输入。

### 拍剪工单返工

`POST /admin-api/zsjos/production-ticket/{id}/reject` 使用 `version` 与必填 `reason`。原因去除首尾空白后必须为 1-500 个字符；服务端原子递增 `revisionCount`，不再参与历史最大返工次数限制。工单列表保留旧截止时间字段读取兼容，但新建工单不写入截止时间或返工上限。

### Student delivery stages

`GET /zsjos/student/service/{serviceRelationId}/contact-context` returns the server-owned normal delivery stage projection in `deliveryStage`, `deliveryStageLabel`, and `deliveryStages`. The planner advances the current stage with `POST /zsjos/student/service/{serviceRelationId}/delivery-stage` using the current `stage`, a required remark, structured `data`, and an idempotency key. The backend validates service ownership, stage order, required facts, attachment ownership, and idempotency; the client must not mutate stage fields directly.

### Generic work orders

- Scene create/update requests use a structured `fields` array. Each field declares `key`, `label`, `type`, `required`, and, only for dictionary fields, `dictionaryType`; administrator-maintained business choices must not be embedded as static options.
- `POST /zsjos/work-order/create` accepts `values` as a JSON object plus a top-level `attachmentIds` list. The backend validates required/unknown fields and type-specific user, department, dictionary, date and number values, then stores both definitions and display-label snapshots.
- Create and action commands require an idempotency key. Reuse is accepted only when order, actor, operation, version, reason, values, and normalized attachments are identical; any mismatch returns an idempotency conflict.
- `GET /zsjos/work-order/pool?sceneCode=...&pageNo=...&pageSize=...` returns `PageResult<WorkOrderRespVO>` and applies the framework page-size limit.
- Workbench routes are `/zsjos/work-orders/create`, `/zsjos/work-orders/available`, and `/zsjos/work-orders/mine`; menus and action permissions remain server-owned. Mine views are `PENDING_ACCEPT`, `PROCESSING`, `PENDING_REVIEW`, `CREATED`, and `CLOSED`.
- Detail and list responses return server-computed `availableActions`; the client does not infer operations from status or role names. The backend independently checks permission, participant, live pool eligibility, status, version, and idempotency fingerprint.
- `PRODUCTION_TICKET` templates require `relatedAccountId`. Both the account page and work-order center render published dynamic fields and attachments and call the same production boundary, which loads the confirmed positioning snapshot and creates the production record plus envelope transactionally.
- See `docs/api/generic-work-order-center.md` for the complete lifecycle and permission contract.

## EAM 我的资产与采购申请

`/zsjos/my-assets` 和 `/zsjos/asset-demands` 只由 System 权限响应中的服务端菜单开放，
数据库直接子路径分别保存为 `my-assets` 和 `asset-demands`。前端本地组件注册为
`EamMyAssets` 和 `EamAssetDemands`，不按角色名推断访问权。

- `GET /admin-api/eam/workbench/my-assets` 返回当前 System 用户对应员工的单件资产、批量持有、
  待签收、待退还验收和入离职/异动任务；权限为 `eam:workbench:asset:query`。
- `GET /admin-api/eam/workbench/my-demands` 返回当前用户提交的采购申请；权限为
  `eam:workbench:demand:query`。
- `GET /admin-api/eam/workbench/categories` 与 `category-fields?categoryId=` 返回服务端分类策略
  和采集阶段自定义字段。未确认交付/持有策略的历史分类不可提交；System 字典字段加载失败时
  必须显示错误和重试，不得回退到硬编码选项。
- `POST /admin-api/eam/workbench/stock-candidates` 根据当前分类、单位和已填写的自定义字段返回
  提交前只读库存候选，不创建需求或预留。页面在明细完整后自动刷新；审批通过后仍由管理员
  通过管理端候选查询和原子预留命令再次确认当时库存。
- `POST /admin-api/eam/workbench/demand` 提交当前员工的资产需求；员工编号由服务端根据登录
  `userId` 解析，客户端不能代填其他员工。
- `PUT /admin-api/eam/workbench/holding/{id}/sign`、`holding/{id}/return` 分别执行签收和退还；
  后端再次校验持有人。对应权限为 `eam:workbench:asset:sign` 和
  `eam:workbench:asset:return`。
- `POST /admin-api/eam/workbench/repair` 复用 EAM 现有报修能力；只允许当前员工持有的单件资产，
  权限为 `eam:workbench:asset:repair`。

资产卡状态展示使用 System 字典 `eam_asset_status`；持有状态、需求状态和员工资产任务状态
来自 EAM 技术状态契约。页面分别处理加载、成功、空数据、失败与重试；HTTP 403 保留当前
会话并由全局无权限状态处理。

## 收件箱双布局查询

支持表格布局的收件箱保持同一查询条件：收件箱左侧使用 cursor 或页码追加实现滚动懒加载，
表格使用对应的 `PageResult` 接口进行服务端分页。表格页支持每页 20、50、100 条和快速跳页；
搜索、筛选、状态切换后重新从首批或第一页请求，ProTable 的刷新、列设置、密度和全屏按钮保持可用。
消息与公告的关键词、阅读状态、分类/类型、高亮和时间条件由 System 查询接口在分页前处理；
BPM 任务名称等条件由 BPM `todo-page`/`done-page` 接口处理。

客资收件箱的服务端结果固定按 `lastActivityAt DESC, id DESC` 返回，cursor 编码同一组字段。
Workbench 只把未查看客资和通知深链目标等特殊集合移到顶部，其余客资保留服务端相对顺序；
加载更多按 cursor 追加，业务操作成功和实时分配事件到达后重新读取首批并保留当前详情选择。
置顶、查看详情、选中和标记已读都不得修改 `lastActivityAt`。消息继续按收到时间、公告继续按发布时间排序。

### 新媒体账号运营档案 V209

详见 [完整字段、接口与操作说明](../../../docs/api/media-account-profile.md)。空账号点击即创建；业务资料可空，未分配运营时不回退为编导。Workbench 使用 profile 接口服务端下发的 editableFields；Admin 维护字段责任与完成提醒。红色只读、蓝色编导、黄色运营。保存为字段补丁，记录为追加；旧全量更新与手工状态维护写入口停止接受数据。

账号档案读取需要服务端 `zsjos:media-account:query`；无此权限时显示无权限提示且不请求 profile/历史。编辑/维护权限不替代读取权限。V209 开发基线补齐新媒体学员下的独立查询按钮，详见账号档案契约。


### 定位卡来源统一（2026-09-17）

学员详情 `positioningDrafts` 包含当前编导自己的未绑定账号草稿（`accountId: null`）；已绑定草稿仍受账号可见范围限制，不因学员可见而放宽。Workbench 按账号（将 null/undefined 统一为空）及课程服务关系恢复草稿，并读取其完整值与素材版本 ID；不同课程服务的草稿不能互相覆盖。Admin 未消费该详情投影。

填写定位卡使用四列表格直接显示完整填写提示，窄屏改为逐项堆叠；填写和素材预览弹窗最大宽度为 1480px。素材预览除配置字段外单独渲染版本 `coverPreviewUrl`（兼容 `files` 中 `__cover__` 的 `previewUrl`），提供加载失败提示与刷新预览。

`GET /zsjos/positioning-card/account-overview?accountId=` 返回 `{current:null,effective,history:[]}`，effective 仅为账号当前应用的提交快照。课程服务的 `service-overview` 管理草稿、主卡、历史与审核操作；账号通过 `application-options` 获取同课程服务已确认候选，用 `apply` 显式应用。新版确认不替换已应用版本，新工单冻结应用版本，已有工单保持不变。接口和迁移详情见 [独立定位卡契约](../../../docs/api/positioning-service-application.md)。

填写入口使用模板最新发布字段；保存同步升级同一模板的草稿版本，保留旧字段业务值供追溯；已提交快照不变。新发布模板有 37 个主项目和 10 个参考素材字段。

### 定位卡手动保存（2026-09-17）

定位卡不再随输入、素材选择、附件选择、下载或关闭自动保存；资料预审维持原自动保存行为。底部提供“取消 / 保存草稿 / 保存并关闭 / 保存并提交审核”，JSON 导入仅填入表单，历史版本导入明确标为“导入并保存”。保存与历史导入互斥，保存期间禁用编辑和关闭。主表单和导入弹窗点击遮罩及 Escape 均不关闭；未保存的取消、关闭或重新加载须确认放弃修改。

附件先作为本地 File 暂存，不进入草稿请求。显式保存新卡时先创建草稿取得 ID，再逐个上传，最后保存附件 ID；已有卡直接上传并保存关联。上传成功即将本地待上传项替换为 ID，失败重试不重复上传已成功项，每次草稿写入使用上次响应版本。该多请求过程不是原子事务：部分失败可能已创建草稿或上传文件，但未完成关联，必须保留现场并重试；不自动删除存储文件。

真实版本冲突保留输入，不自动覆盖或重新加载；复制内容会以文件名标识待上传附件，不包含文件字节。重新加载经放弃确认后读取最新草稿。附件下载仅调用 GET，不写草稿。后端附件上传的非责任编导错误使用 POSITIONING_CARD_PERMISSION_DENIED（1900014004），不再误报版本冲突；手动保存阶段沿用既有接口和租户权限；后续独立定位卡功能的数据库变更见 V258 契约。
