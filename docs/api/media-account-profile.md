# 账号运营档案（V209）

创建入口在新媒体学员概览，编导点击后直接创建空账号并打开大表单。运营没有创建入口；服务端仍验证创建权限、服务关系、责任编导、访谈完成、版本和幂等键。无现任运营时负责人保持空，不回退为编导。

## 接口

所有路径使用 ADMIN 认证和当前租户，下面省略 `/admin-api`。

| 方法及路径 | 请求 / 返回 | 权限 |
| --- | --- | --- |
| POST `/zsjos/media-account/create` | `studentPersonId, serviceRelationId, version, idempotencyKey, detailValues:{}` → 账号 ID | `zsjos:media-account:create` + 服务关系 `create-account` |
| GET `/zsjos/media-account/{id}/profile` | account、config、values、snapshots、editableFields、missingFields、missingByOwner、sourceNotes、files、责任人姓名 | `zsjos:media-account:query` + 对象 read |
| PUT 同上 | `version, configVersionId, idempotencyKey, changes` → 新版本 | `:edit` 或 `:maintenance` + 对象 edit + 字段责任 |
| POST `/zsjos/media-account/{id}/profile/records` | `version, configVersionId, idempotencyKey, fieldKey, content, fileIds` → 新版本 | 同上 |
| POST `/zsjos/media-account/{id}/profile/diagnosis` | 7/14/28 模板及阶段、状态、配合、瓶颈、证据、结论、措施、重点观测数据、重新定位 → 新版本 | 同上 |
| GET `/zsjos/media-account/{id}/profile/history` | `pageNo,pageSize` → PageResult，保存时字段/字典快照及附件 | `:query` 或 `:maintenance` + 对象 read |
| POST `/zsjos/media-account/{id}/profile/files` | multipart `fieldKey,file` → 文件 ID、名称、类型、大小、短期访问地址 | 同更新，并检查该字段为图片/记录 |
| POST `/zsjos/media-account-field-config/draft/reconcile` | `id,version` → true | `zsjos:media-account-field-config:update` |

权限短写的完整前缀为 `zsjos:media-account`。沿用现有 `edit/maintenance/query/query-all` 服务端菜单配置，未增加另一套 `maintain/history` 权限，不按角色名称判断责任。Admin 仅维护字段配置；员工档案归 Workbench，Admin 不复制员工页面。

旧 `PUT /zsjos/media-account/{id}` 与 `PUT .../maintenance` 返回 `1900011020`（请使用账号档案维护）。它们不能再修改自动状态。旧维护历史和日历读取保留；原阶段推进接口仍退役。当前仓库唯一活动账号创建入口为 MediaStudentsPage；退役 MediaFeaturePage 的账号页面不注册路由。

## 字段规则

字段定义沿用 `key,label,type,sort,enabled,dictType,searchable`，补充 `ownerType,group,requiredForCreate,requiredForComplete,sourceType,snapshotPolicy`。`label/sort/dictType` 对应产品方案中的 title/order/dictionaryType，兼容现有两端协议。

- AUTO 红色：任何用户不能从 profile 更新；DIRECTOR 蓝色：当前责任编导；OPERATOR 黄色：当前负责人运营；UNASSIGNED：只读，不计入缺失。责任与菜单、对象权限累积校验；负责人可能同时承担两种责任。
- 所有业务字段 `requiredForCreate=false`。`requiredForComplete` 只提醒，不阻止空白或部分保存；禁用、自动、未分配和 record 字段不计入缺失。`false` 与 `0` 是有效值。
- `group`：PROFILE、POSITIONING、STATUS、METRICS、REVIEW。`type`：text、textarea、number、date、select、multi_select、boolean、image、record、url。文本上限 2000 字（账号名称和主页 ID 为 255 字）；记录正文 10000 字；链接限 HTTP(S)。
- 选择项来自 System 字典，未变化的选择保留历史快照，不重新解析已改名/停用的标签。新选择验证当前字典。实体字段后续通过所属业务 API 接入，不能以本地选项代替。
- 图片上传 PNG/JPEG/WebP，记录附件另支持 PDF；单个 20 MB、每条记录最多 20 个。上传验证类型头与对象权限，绑定验证当前租户/账号/上传人命名空间；历史展示文件 ID 与上传时名称，访问使用 Infra 短期签名。
- `cover`（主页截图）默认 OPERATOR，由当前责任运营上传、替换和删除，仍须通过菜单权限及账号对象权限校验。头像和背景属于运营。运营 `work_format` 和编导 `content_format` 独立保存。
- 系统来源 `sourceType` 为 ACCOUNT、STUDENT 或 PENDING；人工为 MANUAL。当前已接入账号编号、姓名、联系方式和现存状态/定位标签快照。陪跑天数、期段计算、经营指标等待后续来源和统计口径；不伪造零值或自动同步定位卡。
- 更新仅合并 changes；null 清除本字段；未提交字段不覆盖。保留已禁用历史数据和快照。账号行锁加版本 CAS、租户拦截与幂等记录保障一致性；旧版本冲突时必须刷新后重试，不自动覆盖另一人的修改。
- 历史和复盘只提供追加与读取，不提供编辑/删除 API。S0—S6、7/14/28 诊断都是独立记录类型。账号创建后按第 1 天起算，在第 7、14、28 天及其倍数生成对应编导任务；同一天的模板任务独立存在，当天未完成次日进入逾期提醒。维护暂停区间不计入周期，责任编导变更时未完成任务转给当前编导；诊断不经过审批。

## 稳定错误

版本冲突与配置版本冲突沿用 `MEDIA_ACCOUNT_VERSION_CONFLICT`、`MEDIA_ACCOUNT_FIELD_CONFIG_VERSION_CONFLICT`；字段责任越权为 `MEDIA_ACCOUNT_PERMISSION_DENIED`；未知/禁用字段为 `MEDIA_ACCOUNT_FIELD_CONFIG_INVALID`。新错误：`1900011018` 同幂等键不同请求，`1900011019` 附件不合法，`1900011020` 旧写接口退役。前端显示服务端可行动错误，保留未保存输入供用户处理。

## 管理与升级

V209 先执行，再部署后端与两端代码。新增账号历史表并将平台/昵称/运营负责人列改为可空；现有账号值与字典快照不更新。为每个已有已发布配置的租户新建发布版本：已知字段补责任与分区，保留已配置名称/类型/字典；未知旧字段置为待确认。已有草稿原文保留，早于当前发布版的草稿不能发布；管理员点击“保留草稿并合并最新字段”，检查责任后再发布。

发布节奏和内容主要形式只建空字典类型，管理员维护具体选项。无业务字典值的自动填充。V209 开发基线修正增加“查看账号档案”按钮（原 query 标识，位于新媒体学员下面）；仅为已有有效 edit、maintenance 或 query-all 授权的角色补齐读取权限，并为已包含父页面的租户套餐补齐按钮。新增用户授权仍由管理员维护，不按角色名称或用户 ID 推断。版本化 SQL 的前提为 V208，执行入口是 `script/sql/mysql/bootstrap.sql`（新库）或 `migrations/V209__media_account_profile.sql`（已有库）。升级可重跑；回退需同时回退应用和发布配置，保留历史表避免丢失审计。

## 操作说明

编导在概览点击新增账号，账号标签立即出现，业务字段为空。关闭空表单不删除已创建账号。账号头部和大表单展示两方待补数量；点击责任提醒展开该方字段，再点字段切换分区并定位。维护表右侧编辑本人字段，其他字段保留可读状态。资料可随时部分保存；有未保存修改时提供继续填写、放弃修改、保存并返回。复盘区使用“填写记录”追加历史，附件打开或下载。系统统计等待来源期间不计入待补。

## 验证与部署状态

源码测试、两端构建、两端浏览器验收与隔离 MySQL 的基线/升级重放均已执行。浏览器使用测试入口和合成接口响应，不访问或修改真实学员。真实开发后端与开发库尚未切换到 V209；只读结构比对显示 4 个可空列与档案历史表尚未同步，部署后必须再完成真实账号请求和附件存储联调。

仓库级 `zsjos_db.py check` 被现有 core 清单引用不存在的 `yudao-module-crm` 阻断；此改造不修改该模块清单。V209 的完整前置 bootstrap、已有账号快照保留、旧草稿保留、重复执行和 UTF-8 HEX 已通过独立 MySQL 检查。后续发布仍需解决仓库级清单阻塞。

## 查询权限修复（2026-09-11 开发基线）

V113 已逻辑删除旧账号页面 6970，连带移除了 `zsjos:media-account:query` 的唯一有效菜单；V209 档案 GET 仍需要该标识。修复创建独立按钮，不恢复旧页面，不以 edit/maintenance 替代 Controller 的 query 校验，对象 read 校验不变。Workbench 在缺少 query 时显示无权限状态且不请求档案/历史；管理员维护菜单后需刷新服务端权限响应。

当前为未发布开发基线，修正原 V209 并 SOURCE `script/sql/mysql/permissions/media-account-profile-query.sql`，不另加版本。已执行 V209 的开发库从仓库根目录单独执行该文件即可，避免重跑其他业务升级。执行前审查该脚本目标角色/套餐；真实授权同步必须单独确认。重复执行不新增重复按钮或授权，但仍保留写权限的角色如被手工撤销 query，重放会重新补齐，因此上线后不应把此脚本用作周期同步。

SQL 只新增按钮、缺失角色映射及相关套餐项，不修改学员、账号或旧菜单。撤销时只撤销本次新建映射/套餐项，保留被后续配置引用的菜单。SQL 直写后需精确失效 `permission_menu_ids:{tenant}:zsjos:media-account:query` 以及新增菜单的 `menu_role_ids` 缓存；不清空 Redis。Admin 继续通过 System 菜单/角色配置消费此按钮，不增加员工档案页面。

验证入口：`python script/sql/mysql/tools/test_v209_replay.py` 和 `script/sql/mysql/verify-media-account-profile.sql`；完整重放当前阻断于既有 V076（缺少菜单 6856），不能声称新库全链路通过。独立检查 `python script/sql/mysql/tools/test_profile_query_permission.py` 覆盖任意角色代码按既有能力迁移、无关角色/套餐不授权、旧页面保持退役、重放去重和中文 HEX。真实开发库授权同步与真实 HTTP 验证的状态以本次 handoff 交付记录为准；上文首次交付的部署状态仅为当时记录。

### 本次开发库验收结果

2026-09-11，经确认已将权限补齐脚本同步至开发库，新增查询按钮并给三个已有账号能力的角色补齐授权；精确失效查询权限缓存。受影响用户的真实权限响应已返回 query，原账号档案 GET 与历史 GET 均返回 code=0。只读验证确认档案表及四个可空列已部署，中文按钮 HEX 正确。完整新库基线仍受前述 V076 阻断，不以本次接口成功代替新库发布验收。

## 账号页三列展示（2026-09-11 用户截图纠正）

主页图独立左置，桌面主体固定三个一级栏目：账号定位卡、账号状态、账号复盘记录。定位卡包含历史定位与采访；账号状态内部两组容纳资料与状态/经营指标；档案记录时间线归入复盘列。维护 Modal 导航及缺失项定位使用相同三类，手机顺序叠放。

这是 Workbench 的展示投影：PROFILE/METRICS 归入 STATUS，positioning_history 归入 POSITIONING，cover 独立在主页区；保留服务端字段、责任、版本和快照协议。账号页移除旧维护历史、独立定位卡/内容/拍剪区块及其入口，不删除持久化记录；Admin 配置界面及其他业务页面不在本次布局改动范围。

定位卡最终确认后，`pc_account_position`、`pc_profession`、`pc_content_form`、`pc_student_duties`、`pc_company_duties`、`pc_internal_goal` 在同一事务内写入账号档案；任何同步失败都会回滚定位卡确认。账号状态和当前瓶颈改用字典 `zsjos_media_account_current_status`、`zsjos_media_account_primary_problem`，暂时责任待配置并允许为空。V211 按用户授权清理全部账号运营旧数据，保留账号主体、学员、服务关系和兼职数据。

### 编导定位卡正式提交

定位卡正式提交使用独立 POSITIONING 记录类型；资料草稿保存不会生成该记录。每次正式提交冻结当时的定位字段配置、字典标签、附件元数据及素材版本，旧记录只读。materials 字段引用完整有效的爆款账号或爆款内容版本；平台和适用阶段为可调整的默认筛选，推荐数量仅作提示。



V211 已于 2026-09-12 在开发库执行。执行前备份位于 `backups/mysql/v211-media-account-operation-backup-20260912.sql`；受影响账号运营记录已清理，账号主体和学员-兼职绑定保留。该操作不可逆，生产环境需另行备份与审批。

## 主页图运营责任（2026-09-14 开发基线修正）

修正 V209 默认配置，并在其末尾执行 `script/sql/mysql/media-account-cover-operator.sql`。已执行 V209 的开发库仅执行该修正文件，不重跑权限或其他业务迁移。脚本为已发布配置中责任为 UNASSIGNED 的 cover 图片字段发布新版本，仅修改 ownerType 为 OPERATOR；保留所有其他字段属性、旧版本 JSON、管理员草稿和账号图片数据。已分配、缺失及已删除配置不受影响，重复执行不再发布版本。执行期间暂停管理员配置编辑与发布；旧草稿沿用现有合并最新字段流程。回退通过配置 API 将上一版本内容重新发布，不删除历史。

新库使用修正后的 V209 默认值；这是尚未发布的开发基线修正，不新增编号迁移。两端继续消费现有 ownerType 与 editableFields 协议，运营责任不按角色名称推断。

## 账号交付弹窗与作品历史（2026-09-14）

本次已确认口径替代此前“账号页不展示内容区块”的对应限制：账号资料区下方展示该账号的已发布作品历史，原内容生产编辑、拍剪区块不恢复。

- 操作区统一右对齐，依次为“申请延期 / 填写交付确认 / 维护账号表 / 填写周期诊断”；按钮按服务端权限展示，换行仍右对齐。
- 交付确认表单默认不渲染，只在点击“填写交付确认”后打开 Modal。计划加载、无计划、无可提交阶段、提交错误分别展示；成功后关闭并刷新账号数据。
- 延期使用独立 Modal，填写正整数天数与必填原因（最多 1000 字），取消 3 天业务上限；仍遵守数据库可表示的日期范围。`POST /zsjos/student-delivery/defer` 保留原请求结构，requestedBy / supervisorUserId 兼容字段不再作为可信身份来源；服务端取登录用户、校验阶段责任编导，并通过 System 用户及部门 API 解析编导所在部门的有效负责人。部门或负责人无效时失败，不创建申请。
- 延期继续通过既有 BPM `zsjos_student_contact_extension`、`deliverySupervisorReview` 节点执行，businessKey 为 `student-delivery-defer:<id>`；交付表保存独立申请及原截止时间。申请阶段进入 DEFER_PENDING，审批通过后顺延截止时间；拒绝或取消保留原截止时间。重复终态回调不重复延期，运行态回调不改变业务结果。未部署流程时事务失败，不把申请当成已生效。
- 沿用现有流程资产及其“学员联系延期”名称，本次未发布或改名共享流程。BPM 变量额外提供交付阶段、原/目标截止时间、说明、发起人及提交时间，需在可用流程环境完成真实审批验收。
- Workbench 按 `zsjos:student-delivery:query/submit/defer` 控制入口；后端 defer Controller 权限和 `student-delivery-stage/defer` 对象权限累积执行。仅当前责任编导可发起，客户端不能指定其他发起人或审批人。
- 已发布作品来自既有学员详情完整 contents 列表，按 accountId 和 published 状态筛选，按发布时间倒序，每页 12 项。网格按可用宽度自动排列；封面读取当前内容版本绑定文件的短期 previewUrl，无图片时显示“暂无封面”。详情/封面读取沿用 `zsjos:content:query` 及服务端对象授权，失败显示原因并可重试。未接入点赞等互动数据，不补造统计值；本功能不是外部平台自动抓取。
- Vue 管理端只消费交付周期配置接口，不调用 defer，不改其配置协议。本次无 SQL、新依赖、账号授权或共享服务配置变更。

本次开发库已发布租户 1 配置版本 5，cover.ownerType=OPERATOR；前一版本 JSON 保留，其他字段逐项一致，重复执行无新增版本，主页截图标签 HEX 为 E4B8BBE9A1B5E688AAE59BBE。定向 MySQL 重放、后端 17 项专项测试、Workbench 3 项测试、typecheck 和生产构建通过。完整前置初始化检查因当前仓库重复 V228 编号阻断；浏览器无登录会话，真实上传与桌面/移动页面验收未完成，不能据此宣称完整新库发布验收通过。
