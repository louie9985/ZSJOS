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
| POST `/zsjos/media-account/{id}/profile/diagnosis` | 启动诊断及 7/14/28 模板，阶段、状态、瓶颈及证据等 → 新版本 | 同上 |
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
- 系统来源 `sourceType` 为 ACCOUNT、STUDENT 或 PENDING；人工为 MANUAL。当前已接入账号编号、姓名、联系方式和现存状态/定位标签快照。陪跑天数、期段计算、经营指标等待后续来源和统计口径；不伪造零值；定位内容按下述单一来源契约展示。
- 更新仅合并 changes；null 清除本字段；未提交字段不覆盖。保留已禁用历史数据和快照。账号行锁加版本 CAS、租户拦截与幂等记录保障一致性；旧版本冲突时必须刷新后重试，不自动覆盖另一人的修改。
- 历史和复盘只提供追加与读取，不提供编辑/删除 API。S0—S6、7/14/28 诊断都是独立记录类型。从首个证据完整定位卡生效日起按北京时间自然日计算，每个周期开始即生成对应编导任务，可提前填写；截止时间为锚点日期加 7、14、28 天及其倍数的 00:00。同一天的模板任务独立存在。普通换版不重置，显式重新定位后重新起算；责任编导变更时未完成任务转给当前编导；诊断不经过审批。

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

V209 通过 SOURCE `script/sql/mysql/permissions/media-account-profile-query.sql` 维护查询按钮和相关租户套餐项。2026-09-17 起该文件不再新增、继承或恢复角色映射；角色权限统一通过 System 角色管理配置。已有数据库的执行仍须遵循迁移兼容性和环境审批要求，不重放其他业务升级。

SQL 不修改学员、账号、旧菜单或角色授权，重复执行不会重新补回被管理员撤销的 query 权限。回退通过审核后的菜单/套餐配置变更完成，保留历史引用。管理员在 System 配置权限后由既有权限管理机制更新缓存；Admin 与 Workbench 继续消费同一服务端授权。

### 本次开发库验收结果

2026-09-11，经确认已将权限补齐脚本同步至开发库，新增查询按钮并给三个已有账号能力的角色补齐授权；精确失效查询权限缓存。受影响用户的真实权限响应已返回 query，原账号档案 GET 与历史 GET 均返回 code=0。只读验证确认档案表及四个可空列已部署，中文按钮 HEX 正确。完整新库基线仍受前述 V076 阻断，不以本次接口成功代替新库发布验收。

## 账号页三列展示（2026-09-11 用户截图纠正）

主页图独立左置，桌面主体固定三个一级栏目：账号定位卡、账号状态、账号复盘记录。定位卡包含历史定位与采访；账号状态内部两组容纳资料与状态/经营指标；档案记录时间线归入复盘列。维护 Modal 导航及缺失项定位使用相同三类，手机顺序叠放。

这是 Workbench 的展示投影：PROFILE/METRICS 归入 STATUS，positioning_history 归入 POSITIONING，cover 独立在主页区；保留服务端字段、责任、版本和快照协议。账号页移除旧维护历史、独立定位卡/内容/拍剪区块及其入口，不删除持久化记录；Admin 配置界面及其他业务页面不在本次布局改动范围。

旧版确认后同步 pc_* 字段的规则已由下节单一来源及手动应用契约取代，确认不再向账号档案复制定位内容。账号状态和当前瓶颈改用字典 `zsjos_media_account_current_status`、`zsjos_media_account_primary_problem`，暂时责任待配置并允许为空。V211 按用户授权清理全部账号运营旧数据，保留账号主体、学员、服务关系和兼职数据。

### 编导定位卡单一来源（2026-09-17）

定位访谈完成后，编导只通过学员概览的“填写定位卡”录入。唯一模板为 `positioning_card` 场景；初始新版包含 37 个项目和 10 个参考素材字段。账号档案配置不再承载 POSITIONING、pc_*、positioning_history、positioning_snapshot 字段；旧配置归档、管理员旧草稿和业务值保留。后端字段责任策略同时拒绝这些旧字段的维护、追加及上传，不能通过直接调用档案 API 绕过。

账号概览的定位卡栏目调用 `GET /zsjos/positioning-card/account-overview?accountId=`，需要定位卡 query、账号 read 和卡 read 权限。`effective` 仅返回账号当前应用关系对应的不可变提交快照；兼容字段 `current=null`、`history=[]`。草稿、审核与历史提交移至课程服务的 `service-overview`。账号使用 `application-options` 和 `apply` 选择已确认版本，新版确认不自动换版。未应用时显示空状态，不从旧账号档案字段拼造内容。参见 [独立定位卡与账号应用契约](positioning-service-application.md)。

保存草稿使用该卡所属模板的最新发布版本；旧模板独有值留在原草稿值 JSON 中供追溯，不展示为新字段，不参与新提交必填校验。模板 ID 不随默认模板切换；提交历史不改写。最终确认时的六字段摘要同步仅用于账号状态摘要，不作为完整定位卡来源。

开发基线 V210 改为调用 `script/sql/mysql/positioning-single-source.sql`。已执行 V210 的本地开发库仅执行该修正脚本；按租户发布新定位模板和清理后的账号配置版本，保留全部旧版本，重跑不新增版本。不得自动重算部署环境迁移校验和；已部署环境另行审查升级方式。回退通过配置管理重新发布旧版本，不删除业务历史。

2026-09-17 本地开发库：定位模板 2 发布版本 2（47 字段），账号配置发布版本 7（42 字段）。备份 `backups/mysql/positioning-config-20260917160628.sql`；原有两张卡草稿及提交记录指纹未变化。受控数据库重放、重复执行、与开发库配置比较、中文 HEX 验证通过。代码专项测试和浏览器合成数据验收不代替运行后端重启后的真实账号验收。

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


### 账号定位摘要展示恢复（2026-09-18）
账号主页与维护表的定位栏目恢复历史逐项行布局（字段名称、实际值、来源标识），不嵌入完整定位卡四列录入表，不显示填写提示。固定展示学员姓名、联系方式、学员加入目标、当前学习/资格阶段、主赛道、辅助赛道、学员配合等级、连续可拍摄时间、出镜意愿、表达能力等级、专业优势和案例资产、信任证据、执行主要风险、历史定位/采访记录，共 14 项。
姓名和联系方式沿用已授权学员详情；11 项定位摘要分别投影已应用提交的 pc_join_goal、pc_learning_stage、pc_primary_track、pc_secondary_track、pc_cooperation、pc_shoot_time、pc_appearance、pc_expression、pc_assets、pc_trust、pc_risk，字典标签使用该提交快照。它们不是第二份可编辑模板，不恢复旧档案字段写入或确认后自动覆盖规则。无已应用版本显示空状态，不从新草稿或历史档案值补造内容。
历史区调用本课程服务的 service-overview 展示保留的提交记录及采访稿附件，访谈入口依据 zsjos:student:positioning-interview-query 显示并以只读方式打开。历史接口失败单独提示重试，不隐藏当前应用摘要。更换版本继续显式确认，候选完整正文仅展示当前选中版本。

### 定位卡附件地址兼容（2026-09-18）

工作台快照附件使用既有 `GET /zsjos/positioning-card/{id}/attachments/{fileId}?snapshot=true&submissionId={submissionId}`。草稿快照省略 `submissionId`；普通上传后读取不传这两个参数。更新后的后端对快照请求校验附件属于指定草稿或提交轮次；旧 `/snapshot/attachments` 路由保留兼容。权限拒绝不降级重试其他地址。

当前运行旧后端已提供既有附件路由，按原有定位卡对象权限和文件路径归属校验；它不识别新增快照参数，不能作为提交轮次隔离验收证据。新的轮次校验仍需后端更新后验证，不能将前端地址修复视为凭证及快照接口已部署。

### 本地数据库同步修正（2026-09-18）

快照附件前端现已恢复 `/snapshot/attachments/{fileId}?submissionId=...`，前述旧地址兼容说明不再代表工作台调用方式。后端部署前必须执行 `script/sql/mysql/migrations/V261__positioning_confirmation_evidence.sql`（前置 V258），否则学员详情查询会因缺少 `evidence_required`、`evidence_json` 失败。不能只编译、重启应用。

本地开发数据库已补执行并验证 V261；其他环境不得据此假定已同步。该脚本只追加凭证字段、按钮元数据和版本记录，不授予角色权限；旧提交保留 `evidence_required=0`、`evidence_json=NULL`。重复执行不重复建列或插入按钮；回退应用时保留字段及凭证，不删除历史数据。

### 头像、背景设置改为文本（2026-09-18）

账号档案 `avatar`（头像设置）、`background`（背景设置）使用 `textarea`，填写设计要求和说明，每项最多 2000 字。工作台复用服务端类型驱动输入，主页文字保留换行；管理员配置页沿用多行文本类型。名称、顺序、责任、必填规则不变，`cover` 仍为图片。接口路径不变，两个值为文本或清空时 null；附件上传接口依据配置拒绝这两个文本字段。

这是无既有业务值前提下的开发基线修正：V209 默认类型已修正并引用 `media-account-appearance-text.sql`；已有本地库仅执行该修正脚本，不重跑 V209。脚本发现任一账号存在非空目标值即中止，不做转换。已发布配置归档并产生新版本，现存草稿修正两项类型并递增并发版本，其他字段逐项保留，旧草稿发布仍遵守现有版本检查。重复执行无新增版本。执行时暂停配置编辑；恢复可通过配置 API 重新发布前一版本，不删除历史或重置权限。

本地已执行修正并验证重复执行、字段逐项比较及中文 HEX。独立 MySQL 验证库验证首次执行及重放。全量新库 bootstrap 未在本次运行，不构成全量发布验收证据。

### 维护表文本输入性能（2026-09-18）

文本输入控件独立维护即时显示值，每次输入同步更新待提交值；整表只在空值/已修改状态转换、失焦或其他操作时同步渲染。保存和关闭确认直接读取最新待提交值，不依赖失焦或定时防抖。定位卡展示通过稳定回调和记忆化隔离；重新打开编辑器及切换账号重建文本输入状态。接口及后端保存语义不变。

### 主页链接校验修正（2026-09-18）

主页链接仍要求完整 HTTP(S) 地址。缺少协议、相对路径、非法 URI、无主机名及非 HTTP(S) 协议均返回既有字段值校验错误，不再因空协议触发 NullPointerException；协议匹配不区分大小写。没有自动补协议或改写输入。

### 确认凭证上传路径修正（2026-09-18）

确认凭证使用 `zsjos/positioning-evidence/{tenant}/{card}/{submission}/{operator}` 作为 Infra 目录，末尾不再追加 `/`。Infra 路径校验禁止空路径段，之前的末尾斜杠会导致上传直接返回“文件路径不正确”。提交凭证时仍按完整目录前缀校验文件归属。

### 本系统兼职账号汇总指标（2026-09-18）

六项 METRICS 由学员当前有效绑定的本系统 Partner 汇总，同一学员下多个媒体账号显示相同指标；不是抖音/小红书账号归因统计，也不是平台抓取。沿用账号档案查询菜单权限和对象读取权限，不返回客资/订单明细，不增加角色授权。

- 累计/本月客资数：当前状态为 valid、converted、won 的有效客资；以 submitted_at 判断提交月份，不用 counted_at 或有效判定时间替代。累计统计全部已提交数据，本月从北京时间月初至本次查询时间。
- 累计/本月成交率：相应提交期间有效客资中 status=won 的客资数 / 有效客资数。一条客资只计一次；重复订单不增加分子。分母为 0 返回 0。比例保留四位小数，页面显示两位百分数。状态后续变化会更新当前统计，不是月末冻结快照。
- 累计成交金额：关联该 Partner 客资的已生效订单 payable_amount 合计；本月按订单 effective_at 归属，不限定其客资必须本月提交。包含复购订单，不扣退款；非生效订单、逻辑删除记录及其他租户数据排除。缺少真实提交/生效时间不推算。
- 未绑定/绑定对象不可读取：sourceStatus=WAITING_PARTNER_ACCOUNT，六个 values 不填造零；页面说明尚未绑定。绑定存在但无业务数据：sourceStatus=READY，六项为零，不再 WAITING_PARTNER_DATA。

`GET /zsjos/media-account/{id}/profile` 的 values 返回 total_leads、month_leads、total_conversion、month_conversion、total_amount、month_amount；保留 partnerMetrics 聚合对象，sourceNotes 给出统计口径。全为只读自动字段，管理员配置只控制字段展示元数据，不能以人工值覆盖聚合结果。Workbench 格式化比例和金额；Admin 字段配置继续使用现有 ACCOUNT 来源，无新增枚举或接口。

开发基线 V209 的六个 sourceType 改为 ACCOUNT 并引用 `media-account-partner-metrics.sql`。已有本地库仅执行该定向脚本：归档已发布版并新增配置版本，草稿原地更新 sourceType 和并发版本；保留其他字段全部属性、旧版本及业务数据。可重复执行；前提 V209，配置编辑暂停；回退通过配置 API 重新发布前版。其他已部署环境需单独审核升级，不静默改历史校验和。本地租户1发布版8→9，角色映射数量保持10517，中文标签HEX已核验。

### 自动字段来源接入（2026-09-18）

账号定位、专业定位、内容主要形式和定位轮数从当前账号手动应用的定位卡提交快照读取；使用提交时字典标签，不重新解析当前字典。陪跑天数按账号创建日为第 1 天、扣除有效暂停日并按北京时间计算。当前期段、账号状态、当前瓶颈来自启动诊断或后续最新周期诊断，不由定位卡相近字段替代。未应用定位卡、缺少创建时间或未提交诊断时分别返回明确来源提示。内容主要形式的账号档案配置已改为 AUTO/text，并通过定向脚本修正已发布配置和草稿。


### 启动诊断与周期诊断（2026-09-18）

- 顺序：手动应用有效定位卡 → 责任编导填写启动诊断 → 后续 7/14/28 天周期诊断。启动诊断不默认 S0，不创建或推进 S0–S6 工单；周期采用下述证据完整定位卡锚点，不再采用创建日起算或扣除暂停日；陪跑天数口径不变。
- 复用 POST `/zsjos/media-account/{id}/profile/diagnosis` 和维护权限。服务端同时检查租户、账号对象编辑资格及 `directorUserId`；维护权限本身不允许责任运营代替编导诊断。不新增权限授权或表列。
- GET profile 增加 `diagnosisStarted`、`canStartDiagnosis`、`canSubmitDiagnosis`。工作台按服务端资格显示入口。Vue 管理端没有调用本诊断接口的页面，本次不复制员工诊断页面。
- 启动请求 `templateType=diagnosis_initial`、`cycle=0`；阶段、账号状态、主要瓶颈及其证据、一句话结论、是否重新定位必填。证据与结论最长 2000 字。次要瓶颈选填，选择后其证据必填；只有证据而没有选项的请求也会被拒绝。其余周期字段启动时可省略。阶段/状态/瓶颈使用管理员字典并保存当时标签，包括选填的次要瓶颈。
- 周期请求继续使用 `diagnosis_7d`、`diagnosis_14d`、`diagnosis_28d`（兼容已有 `adjustment_28d` 命令），`cycle>=1`，配合等级及证据、次要瓶颈及证据、改进措施和重点观测数据必填；先完成启动诊断。
- 提交在同一事务内校验账号与配置版本、更新账号当前阶段/状态/瓶颈代码及标签，并追加 `kind=DIAGNOSIS` 历史记录。启动诊断不完成周期任务；周期诊断只完成指定模板与周期的任务。失败整体回滚。
- 幂等键重复且请求相同返回原版本；不同内容返回幂等冲突。已成功旧请求重试不会覆盖更新的诊断。已有诊断不允许再次启动；应用新的定位卡不重置启动事实。
- 无诊断时自动字段为空，来源提示“待完成启动诊断”；有记录则为“启动诊断”或“最新周期诊断”。历史时间线展示当次证据、结论和冻结标签，不能用当前字典名称替换。
- 稳定错误码：1900011021 已启动；1900011022 尚未应用有效定位卡；1900011023 尚未完成启动诊断。前端原样呈现可操作原因。账号、配置版本及权限错误沿用既有错误码。
- 历史兼容边界：旧 `RECORD` 型复盘保留原文，不凭文字推断诊断事实或回填状态。没有 `DIAGNOSIS` 记录的账号需要明确填写一次启动诊断。
- 部署：重新构建部署后端与工作台；本次没有数据库脚本、角色授权、依赖变更或服务重启。

### 诊断表单修正（2026-09-18）

- 周期表单提供 7天账号数据诊断、14天验证指标诊断、28天调整触发条件三种命令模板。`templateType` 和 `cycle` 来自选中的服务端待办，不作为可填写字段；页面以“本次填写”文字展示表单类型，周期编号内部保留。栏目入口选择该类最早未完成待办，汇总与今日待办直接传递 taskId。
- 修订已有周期诊断时，模板与轮次只读并保留原值；服务端检查最新记录 ID 及原轮次，禁止把修订提交到其他周期任务。未改动的次要瓶颈选择保留原标签快照；新选择产生新的快照。旧记录不补造历史标签。
- 启动诊断支持清空次要瓶颈，清空同时移除未提交证据和校验错误；周期诊断的次要瓶颈及证据仍必填。使用现有诊断接口和历史 JSON，无数据库结构变更。
- 两个弹窗桌面宽度 1080px、视口两侧至少 16px，正文内部滚动且提交区固定；768px 及以下单列。主要/次要瓶颈分别与自身证据成组，基础信息、配合情况、结论及改进内容分区。
- 浏览器验收入口：`test/account-positioning-summary.html?panel&diagnosis`，配套 `test/account-diagnosis.py` 使用实际组件和隔离模拟传输，不写入真实业务数据。


### 定位卡要求与周期跟进（2026-09-18）

- 生效同步六项：pc_days7/14/28 → diagnosis_7d/14d/28d_requirement；pc_student_duties → student_commitments；pc_company_duties → company_commitments；pc_internal_goal → delivery_goals。要求与来源元数据存入现有账号 detail_values_json，三个诊断记录字段仍为 record，不另建人工编辑要求字段。
- 新来源需有学员同意时间、有效凭证提交时间及 confirmed/superseded 状态。来源限定同租户、同学员、同服务；历史无凭证版本可继续按原契约应用，但不据此启动新诊断周期。缺少锚点显示待核实，不推算时间。
- GET profile 新增 positioningRequirements 和 diagnosisContext（anchorAt、roundKey、submissionId、submissionNo、syncedAt、effectiveAt）。三项诊断要求及承担事项自动覆盖，包括清空；PROFILE 保存覆盖前快照。两项承担事项仅责任编导可手改；delivery_goals 前后端只读，配置发布也固定 AUTO/ACCOUNT。
- 首轮锚点为首个证据完整来源生效时间；若账号晚于定位卡创建，以账号存在后的时间起算。7/14/28 天任务在各自周期开始即生成，5 分钟补偿调度，保留逾期未完成项；不会提前生成尚未开始的下一周期。首日生成三类首轮任务；第 7 天开放第二轮 7 天任务。dueAt 仅代表截止时间（锚点日期加周期天数的北京时间 00:00），不作为填写开放条件；超过截止时间且未完成才逾期，逾期可补填。普通换版不重置 roundKey。显式重新定位结束后旧待办不可提交，新证据完整版本重置锚点和 roundKey；旧待办仅取消未完成项，历史完成项保留。
- 任务幂等键包含 accountId、roundKey、template 和 cycle。任务首次生成时冻结三项要求和来源。后续换版只更新账号当前要求，不修改旧任务和已提交跟进事实。
- POST profile/diagnosis 新增可选 taskId；新周期记录必须匹配当前责任编导、当前轮次、已生成且未完成的任务，并锁定任务。允许截止前提交，保存该任务要求快照后只完成它；完成项不会被补偿调度重建。修订沿用原周期及要求快照，不再次完成任务。旧记录无快照显示“历史版本未留存”。错误 1900011024 表示尚未生成、已处理或轮次结束。
- GET `/zsjos/media-account/diagnosis/reminders` 从截止前 24 小时开始返回当天未展示且未完成的任务，包含逾期任务；距截止超过 24 小时不提醒，已完成不提醒。工作台在线轮询约每分钟检查，离线时在下次进入工作台检查，不承诺离线推送。提醒展示具体表单、截止日期时分和北京时间；GET `/zsjos/media-account/diagnosis/tasks?accountId=...` 返回账号可填写任务（包含截止前任务），按截止时间、taskId 升序排列；POST `/zsjos/media-account/diagnosis/acknowledge` 接收 `{taskIds: [...]}`，逐项验证责任和对象权限并记录每日展示事件，绝不完成任务。沿用账号 edit/maintenance 权限，不新增角色授权。列表字段包括 taskId/accountId/studentPersonId/accountName/title/templateType/cycle/dueAt/payload。
- 工作台进入后汇总弹窗，打开即记录当天已展示；允许稍后处理。下次自然日仍未完成会再次提醒。7/14/28 同日分别填写，汇总入口直接打开对应模板。账号主页及维护表显示当前要求、最新完整跟进及栏目历史，正文滚动、底部操作固定。
- Vue 管理端仅消费现有字段配置接口，AUTO/ACCOUNT 为既有配置值；无新增员工诊断页面。账号定位卡主体继续手动应用；其切换不会反向覆盖自动来源的六项字段。

### 周期诊断提前填写与截止展示（2026-09-18）

- 用户确认周期诊断允许提前填写；上述周期生成和提交规则替代原先“到期才可填”的规则。三个栏目及新任务填写弹窗显示服务端 dueAt 的完整日期、时分和北京时间，不由浏览器推算业务期限。截止前显示可提前填写，截止后显示可补填；任务读取支持加载、错误重试、空状态及手动刷新。
- 栏目选择该类型最早未完成任务；“填写周期诊断”总入口选择所有类型中最早未完成任务，避免仅剩 14/28 天任务时误报为空。提交完成后刷新任务列表；模板和轮次仍由任务确定并只读。
- 不修改既有任务的截止时间、历史完成事实或权限配置，无 SQL 迁移。后端更新运行后由既有五分钟调度补齐当前周期任务；需部署更新后的后端，单独刷新前端不能启用新规则。Vue 管理端没有此任务接口的消费者。


### 本地配置历史恢复（2026-09-22）

数据库导入或恢复后，应比较实际 published/draft 字段 JSON，不能仅凭 V209 版本标记判断开发期配置修正已生效。本次按 handoff 的已确认要求依次重放 `media-account-cover-operator.sql`、`media-account-appearance-text.sql`、`media-account-partner-metrics.sql`、`media-account-content-format-auto.sql`、`media-account-diagnosis-fields.sql`，恢复主页图运营责任、头像/背景文字类型、六项统计来源、内容形式自动读取及内部交付目标只读责任。前提为 V209 表结构存在；仅针对已审查的本地配置，不作为生产自动升级流程。

执行前备份字段配置及受影响账号，核对头像/背景非空值。原文字修正脚本仍拒绝自动转换既有值；本次经用户明确批准，仅清空一个账号的两个当前图片值并递增账号并发版本，保留文件及历史。不得将图片 ID 当作设计说明。配置脚本保留前版 JSON 并发布新版本；相同前提下重复执行不新增版本。恢复前版配置需通过配置 API 重新发布；恢复当前业务值需依据备份和当前并发状态单独审查，不能整体覆盖后续编辑。

`media-account-content-format-auto.sql` 的 DELIMITER、CALL、DROP 必须分行，避免 MySQL 客户端把调用吞入分隔符声明。本次受控重放与本地第 9 版字段逐项一致，重复执行及中文标签 HEX 检查通过；认证后的页面上传/保存仍须在实际用户会话验证。
