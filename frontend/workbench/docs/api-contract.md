# API 约定

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

- `POST /zsjos/media-account/create`：从当前学员创建第三方账号；学员不可改，普通创建人的 `directorUserId` 和 `ownerOperatorUserId` 由服务端绑定，`accountNo` 由服务端生成。
- `GET /zsjos/media-account/get?id=...`：查询账号详情。
- `POST /zsjos/media-account/{id}/bind-student?studentPersonId=...`：绑定学员并保留历史。
- `GET /zsjos/media-account/student-candidates`：返回当前用户有权访问的精简学员候选；仅具备 `zsjos:media-account:query-all` 的管理员可查询租户全量候选。工作台学员中心创建账号时优先使用当前学员上下文，不得用该接口扩大可见范围。
- `POST /zsjos/media-account/{id}/unbind-student`：解绑学员并保留历史。
- `PUT /zsjos/media-account/{id}/maintenance`：共同维护当下状态、阶段、主要问题、实行措施、修改方向和日期区间；只提交字典 value，服务端保存标签快照和不可变版本。
- `GET /zsjos/media-account/{id}/maintenance-history`、`legacy-stage-history`：分页查看新的维护版本和保留的原阶段记录，接受账号查询或维护功能权限并叠加账号对象读取权限；账号投影仅在两层权限都通过时返回 `VIEW_ACCOUNT_HISTORY`，Workbench 未收到该能力时不得请求或展示历史。
- Workbench 将状态摘要、维护入口和两类历史归属到具体账号：账号行只根据该账号的 `MAINTAIN_ACCOUNT`/`VIEW_ACCOUNT_HISTORY` 投影操作，选中账号后在“账号”页签内展示完整状态与历史，不提供学员级“状态维护”页签；旧 `tab=maintenance` 链接兼容进入 `accounts`。
- `GET /zsjos/media-account/calendar`：查询与日期窗口相交的当前账号区间，并返回当前范围下的未排期数量；普通用户限本人所属编导/运营账号，`zsjos:media-calendar:query-all` 扩展为全量。
- `POST /zsjos/media-account/{id}/advance-stage`、`rollback-stage`：兼容路由保留一个周期，但不再流转阶段，固定返回“阶段推进功能已停用，请使用状态维护”。
- `PUT /zsjos/media-account/{id}`：编辑账号资料，必须携带版本号。
- `POST /zsjos/media-account/{id}/rescue`：更新挽救状态，必须携带版本号。
- `POST /zsjos/media-account/{id}/request-rebind`：发起账号换绑 BPM，必须携带目标学员和版本号。
- `POST /zsjos/content/create`、`GET /zsjos/content/get`、`GET /zsjos/content/page`：内容查询；状态命令分别使用 `complete-topic`、`submit-production`、`submit-acceptance`、`approve-acceptance`、`reject-acceptance`、`start-revision`、`resubmit-production`。
- `GET /zsjos/production-ticket/create-context?accountId=` 返回账号字段、已确认定位卡快照和关系/权限交集候选人；`POST /zsjos/production-ticket/create` 仅接收账号、可选受派人和幂等键。独立页面提供待接单、我的工单和公共池视图；`GET /zsjos/production-ticket/assignment/my-pending`、`POST /zsjos/production-ticket/{id}/reject-assignment`、`GET /zsjos/production-ticket/pool/page` 与 `POST /zsjos/production-ticket/{id}/claim` 支持永久指定待接、拒接入池和并发抢单。创建、拒接和抢单按租户、操作人、幂等键及请求指纹保存结果；相同请求重放原成功结果且不重复发送事件，不同参数复用键返回稳定幂等冲突。状态命令继续使用 `accept`、`start-production`、`submit`、`start-check`、`approve`、`reject`、`reaccept`。
- `POST /zsjos/positioning-card/create`、`GET /zsjos/positioning-card/get`、`GET /zsjos/positioning-card/page`：定位卡查询；命令使用 `submit-review`、`operator-confirm`、`operator-reject`、`student-link`、`start-revision`。运营确认和退回分别受独立功能权限控制，`query-all` 只扩大读取范围。
- `GET /zsjos/positioning-card/import-sources` 由服务端返回同一学员当前账号和其他账号的可读已提交版本；`POST /zsjos/positioning-card/import` 按当前发布模板把选中提交映射到目标草稿。Workbench 不从详情投影自行拼来源，不导入其他账号草稿；覆盖已有草稿前确认并提交当前 `draftId + version`。

所有详情和分页响应均为 RespVO，并返回服务端计算的 `availableActions`。定位卡统一路径为 `co_creating -> operator_feasibility -> student_link_pending -> student_confirm -> confirmed`；`professionalRisk` 仅保留为业务快照，不再改变审核路径。运营退回或学员提出修改均回到 `co_creating`，由原 `content_director` 修改后重新提交。确认后的再次修改使用 `start-revision`，修订审核期间旧 `effective` 提交继续供下游使用，新提交经学员确认后原子替换旧版。历史 `trial_14d/student_agreed` 数据不迁移，通过运行时兼容为有效版本；历史 IP BPM 监听器仅处理发布前已在途实例。定位岗位统一使用 `content_director`。所有写操作同时受菜单/按钮权限、对象权限和乐观锁版本约束；分页额外受责任人和部门数据范围约束。

`/zsjos/accounts`、`/zsjos/content` 和 `/zsjos/positioning` 不再注册为页面入口。第三方账号、内容生产和账号定位从 `/zsjos/media-students` 的具体学员标签发起；拍剪工单仍保留独立页面。按钮只在服务端下发对应权限且对象 `availableActions` 允许时显示。

- `GET /zsjos/media-account-field-config/published` 返回当前租户已发布的版本化字段定义。默认字段为 `uid`、`nickname`；选择类字段的选项来自定义指定的 System 字典类型。
- 账号保存 `detailConfigVersionId`、`detailValues` 与字段名称/字典标签快照。历史记录展示保存时快照，不重新解析当前字典；旧记录没有值时显示“未记录”。
- `GET /zsjos/media-students/{personId}` 返回账号、定位、内容、交谈记录、按业务更新时间排序的操作时间线、学员级 `studentTaskLine`、逐账号 `accounts[].taskLine` 和待处理统计；服务端先验证当前用户是否在该学员的媒体业务范围内。页面按所选课程服务的真实 `leadId` 读取完整学员档案，并复用“我的学员”的客户档案、来源渠道、地区、成交课程、备注附件等概览结构；销售联系和客资流转不会混入媒体概览。
- 编导采访字段由 `directorForms.interview.fields` 渲染。字典字段加载 `/system/dict-data/simple-list`，地区字段加载 `/system/area/tree` 并使用 `Cascader`；请求仅提交地区 ID，服务端返回并冻结 `{code,labelSnapshot}`。旧字符串地区在草稿中提示为历史值，正式提交前必须重新选择。
- `GET /zsjos/media-students/target?bizType=...&bizId=...` 将受权业务对象解析为 `personId`、`targetTab` 和记录 ID，供待办与通知构造受控深链。未绑定学员的历史对象不得回退到退役页面。

认证失败既可能使用 HTTP 401，也可能使用 HTTP 200 包裹业务码 `401`。工作台对两种响应执行同一套单次刷新与请求回放；刷新失败通过全局事件立即卸载工作台并进入登录页。HTTP 403 保留当前会话并显示无权限，网络错误和服务端错误保留独立的重试状态。

`/messages/all` 调用 `my-page` 获取当前用户全部消息；`/messages/unread` 固定传递 `readStatus=false`。两个页面均由权限接口中的服务端菜单决定是否可见，前端不自行制造入口权限。

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
