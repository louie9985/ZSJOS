# 素材库 API

素材库以“素材主记录 + 不可变内容版本”保存内容包。模板版本发布后不可修改，素材提交审批时冻结字段定义、字典标签快照、文件引用和 BPM 定义版本。

## 核心接口

- `GET /admin-api/zsjos/material-types`：查询当前租户可用的素材类型和已发布模板版本。
- `POST /admin-api/zsjos/material-types`、`PUT /admin-api/zsjos/material-types/{id}`：配置素材类型；模板变更必须发布新版本。
- `GET /admin-api/zsjos/materials`、`GET /admin-api/zsjos/materials/{id}`：分页搜索和查看素材及有效版本。
- `POST /admin-api/zsjos/materials`、`PUT /admin-api/zsjos/materials/{id}`：按当前模板创建或编辑草稿。
- `POST /admin-api/zsjos/materials/{id}/submit`：提交当前草稿审批；旧有效版本继续可用。
- `POST /admin-api/zsjos/materials/{id}/disable`、`POST /admin-api/zsjos/materials/{id}/restore`：停用或恢复素材。
- `GET /admin-api/zsjos/material/page?recommendation=true&accountId={id}`：按账号画像返回确定性匹配结果。推荐维度由模板字段字典自动归属（`zsjos_persona_type`→账号类型、`zsjos_material_profession`→专业方向、`zsjos_media_account_stage`→账号时期），类型级推荐配置只决定启用哪些维度与返回数量，实际生效维度为类型配置与模板字段的交集。
- `POST /admin-api/zsjos/materials/{id}/like`、`DELETE /admin-api/zsjos/materials/{id}/like`：用户级可切换点赞。
- `POST /admin-api/zsjos/materials/{id}/favorite`、`DELETE /admin-api/zsjos/materials/{id}/favorite`：用户级可切换收藏。
- `POST /admin-api/zsjos/materials/{id}/references`：按 `materialVersionId` 复制字段到指定生产内容版本；必须携带幂等键。

引用成功写入生产内容草稿后才增加被调用量。同一素材版本对同一生产内容版本只计一次；重试时必须复用原幂等键，参数不一致会被拒绝。

字典字段保存 value、类型和选择时的 label 快照，历史详情不重新解析当前字典。文件通过 Infra 预签名直传，再由服务端确认对象元数据。

Infra S3 的两个 PUT 预签名入口均使用存储配置的 `endpoint`，由 SDK 按桶名和
path-style 配置生成上传地址；`endpoint` 必须能被上传浏览器访问，并允许站点来源、PUT
及上传请求头的 CORS 预检。自定义 `domain` 继续用于文件访问，公开读取 URL 和私有 GET
签名保持原有逻辑。修复上传不需要改写 `domain` 或历史文件链接；部署后应验证初始化、
浏览器 PUT、上传确认与保存后预览的完整流程。

Workbench provides direct creation pages for `viral_account` and `viral_content` under the server-owned
`/zsjos` menu. Both use `zsjos:material:create`; the browse page also exposes the same forms. The stable
`viral_content` type is displayed as “爆款内容” and its `zsjos_viral_content_type` selections retain their
dictionary value/type/label snapshots. Material browsing remains server-paged but appends pages through
infinite scrolling; no alternate material API or authorization source is introduced.

## 爆款账号拆解

素材类型编码为 `viral_account`。页面左侧账号主页截图是固定封面列，模板字段按 `sort`
依次进入账号详情、编导拆解、搭建建议三个分区；Workbench 只合并相邻且同名的分组。
缺少分区、分区值为字符串 `"null"` 或分区未知的字段不会静默丢弃，而是显示在“模板待完善”区域。

账号名称自动作为素材标题；草稿保存只校验已填写值的格式、HTTPS 链接、字典值和文件引用，提交审批时才校验全部必填字段、两个重复组至少一行以及主页截图。内容矩阵和 S6 稳定增长期均为可增删排序的重复字段组。

V208 以确认的 V2 字段内容发布不可变 V3：36 个一级字段按账号详情、编导拆解、搭建建议
各 12 个排列；`build_notify` 位于编导拆解末尾，S1-S6 使用稳定键
`s1_stage_plan` 至 `s6_stage_plan` 并直接按顺序显示在搭建建议中。迁移只升级空模板及两个
已知应用模板哈希，遇到未知租户自定义哈希保持原模板和当前版本指针不变。

该类型暂不绑定 BPM 流程定义。后台发布流程后，通过素材类型配置绑定流程 Key；编导审核、总监审核、节点顺序、退回和驳回均由 BPM 模型管理，业务代码只消费通用流程状态事件。


### 拆解草稿恢复（2026-09-21）

中世健员工工作台的爆款账号与爆款内容拆解入口先读取当前用户、当前素材类型的草稿，分页读取完整后供用户选择继续填写；新建必须显式点击“新建拆解”。草稿读取失败显示错误并可刷新重试，不将失败当作空列表。编辑入口按服务端 UPDATE 动作控制，后端继续独立授权。

保存草稿保留当前编辑器与已创建记录标识，不清空表单；再次进入可从草稿选择器恢复。切换记录、新建或关闭时若存在未保存修改，会提示确认。编辑 DRAFT 使用该草稿 currentVersion.fields，查看版本使用版本字段；仅新建及从非草稿生成新版本使用当前发布模板，和后端模板选择保持一致。未修改旧字段随 values 一并保存。

此次不修改业务权限、数据库记录、已发布模板或草稿必填契约；不会自动修复此前已丢失的字段值。

### 拆解草稿管理（2026-10-19）

爆款内容、账号拆解工具栏新增“草稿管理”，弹窗分页展示当前用户、当前素材类型的草稿标题、素材编号和更新时间。打开、刷新管理窗口不丢弃正在编辑的内容；列表失败显示错误和重试，不作为空列表处理。

`DELETE /admin-api/zsjos/material/{id}/draft?expectedVersion={version}` 使用独立按钮权限 `zsjos:material:delete-draft`，前端根据服务端 `DELETE_DRAFT` 动作显示删除入口。后端累计校验功能权限、本人归属、租户、主记录及当前版本均为 DRAFT、无生效版本、未启动审批或所引用的本版本审批轮次已为 CANCELLED，以及乐观锁；管理或全量读取能力不授予删除他人草稿。保存、提交与删除共用主记录行锁，状态或版本变化后拒绝删除。

每条删除操作必须二次确认，确认框展示标题和素材编号；删除当前编辑的记录额外提示将关闭编辑器并丢弃未保存修改，取消或请求失败保留编辑内容，删除其他记录不影响当前编辑器。新建后首次保存的记录也按返回标识识别。成功后同步移除选择器与弹窗列表中的记录，并刷新列表。删除是主记录逻辑删除，保留历史版本、文件绑定及存储文件，不处理已生效素材、在审记录、驳回记录或生效素材的修订草稿。

2026-10-25：撤回后的 DRAFT 保留原流程引用，删除时核实对应本版本审批轮次为 CANCELLED；流程引用非空不再直接阻止删除，缺失或未撤回轮次仍拒绝。无需清理历史流程引用或修复业务数据。“素材库 → 我的素材”的卡片及只读详情也提供“删除草稿”，同时检查服务端 DELETE_DRAFT 动作与配置权限，二次确认显示标题和编号；请求期间禁止重复提交，失败保留记录并显示原因以便重试，成功关闭详情及版本窗口并刷新列表，旧列表/详情响应不得恢复已删记录。正在编辑时禁用卡片删除入口，先退出编辑再删除。全部、收藏不提供删除入口。

先执行 `V333__material_draft_delete_permission.sql`，再部署后端与 Workbench。迁移仅新增按钮元数据及两套版本登记，可重复执行并恢复缺失登记，不改角色授权或业务数据；管理员须在系统角色管理配置此权限。Vue Admin 不新增员工草稿管理页面，原有读取/修改接口保持兼容。回滚可禁用按钮并回滚应用，保留审计和历史数据。

## 当前租户管理员读取

管理界面中，当前租户管理员可读取他人草稿、历史版本及素材审批历史。管理列表保持 mine、收藏、状态等用户筛选；响应选取草稿不改变 availableActions 的原始管理判断。该能力不扩大素材浏览范围。推荐账号读取扩大，内容引用等生产操作候选保留原规则。编辑、提交、停用、恢复与审批不继承读取能力。

## 浏览与管理的发布边界（2026-10-20）

素材浏览的全部、收藏对所有账号（含创建人、素材管理员、租户管理员）统一只展示主状态为 EFFECTIVE、类型启用且有效生效版本存在的素材。草稿、审批中、驳回且尚未生效及停用素材不进入这两个页签；已发布素材产生新修订时，继续显示原生效版本。

2026-10-21 恢复：“我的素材”是本人素材查看入口，使用原 `/zsjos/material/page?mine=true`，展示自己的草稿、审批中、已驳回及已生效素材，并支持 versionStatus 当前修订状态筛选。详情和版本记录使用原 `/{id}` 与 `/{id}/version/list`，显示当前修订、审核人、最近提交时间、驳回原因；流程轨迹仍要求 `bpm:process-instance:query`。管理员在本人列表也必须满足 mine=true 的归属限制。切换页签关闭并清空详情和版本记录，全部、收藏继续使用生效专用接口。

全部、收藏使用独立的 `GET /admin-api/zsjos/material/browse/page`、`GET /admin-api/zsjos/material/browse/{id}`、`GET /admin-api/zsjos/material/browse/{id}/version/list`，沿用 `zsjos:material:query` 或 `zsjos:material:manage` 功能权限及既有对象读取校验。分页前强制生效范围，搜索只匹配素材编号和生效版本的标题、摘要、可检索内容；favorite、类型和排序条件继续生效，status/versionStatus 不能扩大到草稿。直接访问未生效或停用素材的浏览详情、版本记录会被拒绝；版本记录只返回 EFFECTIVE 版本。创建、编辑、撤回和审批继续在相应管理入口处理；状态筛选仅在我的素材提供。

原 `/zsjos/material/page`、`/{id}`、`/{id}/version/list` 供我的素材、Vue Admin 素材管理和既有拆解草稿管理使用，保留草稿可见性与生命周期行为；素材审批仍在独立审批界面处理。无需 SQL、角色授权或数据修复。全部、收藏依赖已新增的浏览接口；2026-10-21 的本人查看恢复只需更新 Workbench。下文个人审核筛选和版本读取契约适用于我的素材，写操作通过管理入口处理。

## 素材浏览适配筛选（2026-10-21）

Workbench 桌面工具栏按“全部／收藏／我的素材 → 短搜索框 → 适配账号类型 → 适配业务定位 → 适配账号期段 → 素材类型 → 大图／紧凑 → 更多操作”单行排列，不新增第二行适配筛选区。搜索框自动填满固定筛选控件之外的剩余宽度，不设置最大宽度上限。三个字段保留完整名称，长选中标签及下拉选项允许换行，不以省略号代替；桌面容器不足时工具栏局部横向滚动，手机端自然换行。更多操作提供清空适配筛选、刷新素材，原个人审核状态筛选保留。

三个单选筛选分别使用 System 字典 `zsjos_persona_type`、`zsjos_material_profession`、`zsjos_media_account_stage`，提交已有可选参数 `accountType`、`profession`、`accountStage`。不同维度取交集；素材某维度的多值中任意值命中即可，明确保存的“不限”仍可匹配具体值，未记录的维度不推断为不限。选择变化自动从第一页重新查询，翻页携带条件，切换三个页签保留条件；与关键词、素材类型、收藏和个人审核状态叠加。字典失败禁用受影响控件并沿用错误重试，不提供静态业务选项。

全部／收藏按生效版本维度筛选；`/material/page?mine=true` 的普通个人查询按当前草稿版本、无草稿时按生效版本筛选，与列表显示版本一致。维度关联显式保留租户和逻辑删除条件，推荐查询继续使用生效版本。没有期段维度的账号模板不从 S1—S6 建议正文推断期段。Vue Admin 现有请求不携带这三个参数，未设置筛选时查询行为不变。本次需更新 Workbench 和 ZSJOS 后端，无需数据库字段、字典种子、角色授权或历史数据回填。

## 素材浏览与个人审核进度（2026-09-24，浏览行为由上节替代）

Workbench 浏览页采用响应式封面卡片；“我的素材”采用紧凑摘要卡，展示当前修订版本状态、当前审核人、最近提交时间和驳回原因。主页、最火作品及重复组中的 HTTPS 链接统一使用 ResourceLink 展示、复制与安全打开。

“全部”和“我的素材”请求 `GET /admin-api/zsjos/material/page` 时携带 `sortBy=createTime`，服务端分页前按素材首次保存（上传）时间 `create_time DESC, id DESC` 排序，最新上传在前；同一时间以 ID 倒序保持分页顺序稳定。编辑、审核和互动引起的更新时间变化以及置顶、优先级均不影响这两个页签的上传顺序。筛选、翻页沿用该排序。`sortBy` 是可选参数，仅接受 `createTime`；省略时保留 `pinned DESC, priority DESC, update_time DESC, id DESC`，因此收藏、推荐及未传此参数的 Admin 管理列表排序不变。

`GET /admin-api/zsjos/material/page?mine=true&versionStatus=IN_APPROVAL` 按当前草稿版本（不存在时按生效版本）筛选，保留本人、租户、逻辑删除限制。`versionStatus` 可取 DRAFT、IN_APPROVAL、EFFECTIVE、REJECTED，仅在 mine=true 时生效；原有 status 仍筛选素材主状态，两者不互相替代。主记录为 EFFECTIVE 的在审修订也能进入个人审批中列表。流程没有独立的“已读／开始处理”状态，因此 IN_APPROVAL 展示为“待审核／审批中”。

版本响应新增 `pendingApproverNames: string[]`，仅审批中通过 BPM 公共任务 API 获取实际当前指派人（含加签），姓名通过 System 公共 API 解析；不按部门名称、角色或管理权限推断。未分配／已不可用人员明确显示占位信息，已结束版本为空数组；此字段不构成审批权限或历史审核人快照。原有素材和版本读取授权继续适用。

已通过记录有 UPDATE 动作及 `zsjos:material:update` 时展示“修改并重新送审”；复用 `PUT /admin-api/zsjos/material/{id}` 创建修订草稿，再以 `zsjos:material:submit` 调用 `POST /admin-api/zsjos/material/{id}/submit`。旧生效版本直到新版本通过前保持可用。

审批中仅当前版本的提交人且具有 `bpm:process-instance:cancel` 时返回 CANCEL 动作。Workbench 同时检查动作和权限，填写原因后调用既有 `DELETE /admin-api/bpm/process-instance/cancel-by-start-user`（JSON `{id, reason}`）；BPM 独立校验真实发起人、运行状态及流程是否允许撤回，拒绝原因原样反馈，不自动调整配置或授权。该入口不是管理员代撤回。

新的 BPM CANCEL 事件将版本恢复 DRAFT，主记录无生效版本时恢复 DRAFT、有生效版本时保持 EFFECTIVE；审批轮次记为 CANCELLED，并保留撤回原因和结束时间。重复事件无重复变更；旧审批轮次和历史取消记录不回写。重新提交创建新轮次。Admin 消费的原有字段、状态枚举及接口均保留；新增字段可忽略，Admin 未增加员工端专用按钮。本次不修改审批人策略、菜单授权或 SQL。


个人素材的关键词与当前修订展示使用同一个版本；深链仅在目标变化时消费，切换标签不重新打开已关闭的详情；只读流程面板按独立评论权限决定是否请求评论。


2026-10-22：爆款账号/爆款内容的审批通过与驳回，无论从素材接口还是通用 BPM 接口进入，都累计校验 `zsjos:material-approval:query` 及对应的 `approve`/`reject` 按钮权限，并重验当前受派任务、准确流程轮次和待审批版本。通用 BPM 权限或任务受派身份不替代业务按钮授权。业务状态仍只通过既有 BPM 结果事件更新；不授予真实角色权限。BPM 待办和已办响应增加顶层及 `processInstance` 内的 `processDefinitionKey`，取引擎记录的真实 key，不拆解定义 ID。Workbench 据此分流业务表单，Admin 可忽略新增字段，其通用决策接口同样受服务端业务校验保护。

### 拆解详情操作入口（2026-10-23）

素材库「我的素材」的账号/内容拆解详情顶部保留「撤回审批」；撤回成功后可「继续编辑」，修改后提交新审批轮次。草稿可继续编辑，已通过或驳回版本可「修改并重新送审」。编辑入口累计检查服务端 UPDATE 动作与 `zsjos:material:update`，撤回沿用上述 CANCEL 与 BPM 权限契约。全部、收藏仍为只读浏览。即使当前审批状态筛选排除了撤回后的草稿，详情也会刷新为可编辑状态。
