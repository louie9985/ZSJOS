# 报名履约学员信息收集表

中世健学员信息收集表归属于报名履约任务。每个任务一份，绑定 Person、订单商品和生成时的产品分类／字段快照；相同学员不同报名互不串用。销售客资入口及旧版 16 字段协议已下线。上线前必须核实不存在旧版实例／答案／配置，发现记录时停止转换并保留数据。

## 学员档案

2026-10-19 扩展：档案分为“考试与证书信息”和“报名收集资料”。前者绑定当前 serviceRelationId，无需生成或提交收集表即可维护；查看与维护继续分别使用现有 student-archive:read/update，服务对象可见性仍由后端独立校验。内部字段不进入公开收集表。Workbench 采用报名信息在上、考试信息在下的分组卡片布局，报名字段和考试字段都通过字段旁的修改标记维护，历史记录可展开查看。

2026-10-22：Workbench 两个区域的可编辑字段常驻显示修改图标，点击后在字段原位置编辑并保存或取消，不再打开字段编辑弹窗，也无需填写修改原因。编辑期间禁用同区域的其他修改入口及刷新，保存失败保留输入以便重试。切换学员、筛选、排序、翻页、刷新、关闭详情及离开页面前检查两区未保存内容；保存或上传中阻止切换，未保存输入须确认放弃，浏览器关闭提供原生提醒；敏感信息权限和身份证派生的年龄、性别限制继续有效。字段可见性设置保留独立弹窗。报名和考试档案保存接口的 `reason` 均改为选填，缺省值存为空串，传入时仍限制为 500 字；原始提交、历史原因、操作人、时间、版本和幂等校验保留，Admin 既有携带原因的请求继续兼容。需配套发布后端及 Workbench 后生效。

| 内部字段 | 输入 |
| --- | --- |
| 对接渠道 | 文本 |
| 报名费 | 非负数字，最多两位小数，按十进制文本精确保留 |
| 准考证、理论、技能、发证单位 | 同一字段同时保存文本、HTTP/HTTPS 链接和 JPG/JPEG/PNG/PDF/DOC/DOCX 附件 |
| 论文/综合评审、证书图片 | 同一字段同时保存文本、HTTP/HTTPS 链接和 JPG/JPEG/PNG 附件 |
| 评定结果、证书邮寄单号 | 文本 |
| 证书邮寄地址 | 内部可维护文本；未手工维护时读取本次报名已提交的收集地址（含已有档案修订）；手工保存后以内部版本为准 |

本次不包含批量上传或 OCR。附件逐个上传，单个不超过 10MB，每字段最多九个引用；链接每字段最多九条。移除只改变新版本引用，历史引用和文件保留；取消编辑的未引用附件保留，不自动清理。文本与链接可以单独维护；材料读取、上传、移除和邮寄地址维护累加 `zsjos:student-archive:sensitive-read` 权限。收集表之外的内部字段不会回写订单、收费流水或学员主档，报名费仅为档案记录。

新增 `/admin-api/zsjos/student/service/{relationId}/archive/extra`：GET 返回后端字段定义、复合值、附件元数据、维护记录和可维护状态；POST 接收 revision、idempotencyKey、reason、changes（各字段为 text、links、attachmentIds）；POST `/attachments` 接收 fieldKey/file；GET `/attachments/{id}` 返回受保护的下载字节；POST `/visibility` 接收可见性 revision 和完整字段布尔映射。来源、字段和租户必须一致。扩展修订与收集资料修订各自编号，服务行锁与唯一版本/幂等键防止覆盖。Excel 档案导出包含两个区域的文本、链接及可读取附件名称。

考试字段可见性按字段配置，不是整块考试信息开关。关闭某字段的“教务可见性”后，后端从普通读取响应中移除该字段及其值；具备 `zsjos:exam-calendar:query` 的考务查询权限仍可读取全部字段，设置修改要求 `zsjos:exam-calendar:manage`。角色名称不写入前端或迁移，管理员在 System 角色管理中将这两个权限配置给考务主管、考务专员及管理员。

V332 新增空的 `zsjos_student_archive_extra_revision` 与 `zsjos_student_archive_attachment`，V335 新增租户级 `zsjos_student_archive_extra_config` 可见性配置表，前置分别为 V327 和 V332；不改历史迁移、不重写历史资料、不分配角色。修订值使用现有框架加密，文件复用收集材料 AES-GCM 与 Infra 存储，目录 `student-archive-private/{serviceRelationId}/`；不暴露永久 URL。受控验证：`python -B script/sql/mysql/tools/test_student_archive_extra.py`，并覆盖 V335 的首次、重放、revision 冲突及字段过滤。业务数据库应用和匹配服务部署仍须单独执行。

学员详情增加“学员档案”标签页，Workbench 与 Admin 共用 `GET /admin-api/zsjos/student/service/{relationId}/archive` 读取报名收集资料。通过真实服务关系定位收集表，并再次核对 Person 和订单商品；同一学员不同报名分别展示，不因另一条可见课程开放范围外资料。无收集表或尚未提交时，仅报名收集资料区域显示空态。

新增独立配置权限 `zsjos:student-archive:read`（查看学员档案）、`zsjos:student-archive:update`（维护学员档案）。维护同时需要查看和维护权限，并通过当前服务对象可见性校验。标签页不再由原收集表 read 权限控制；履约详情的原资料查看入口保留。敏感字段及附件累加独立的 `zsjos:student-archive:sensitive-read`，导出累加 `zsjos:student-archive:export`；报名信息表权限不能替代档案权限。管理员通过系统角色管理分配，迁移不写角色授权。

报名收集资料的修订仅面向已提交档案：`GET /archive/edit` 返回可编辑原值及真实字典选项，`POST /archive` 接收 revision、idempotencyKey、reason、changes。新字典/地区选择重新校验并保存标签快照；未修改项保留历史快照，未改身份证不重算年龄。身份证修改同时派生性别和当次年龄。按冻结字段校验必填、格式及图片归属/数量。没有敏感权限不能读取或修改敏感字段和图片。

原始答案和材料引用不可变，最新档案写入加密的 `zsjos_student_archive_revision` 修订快照，保存版本、维护人、时间及原因。表单加锁、唯一版本和幂等键防止重复与覆盖。`GET /archive/sensitive`、`GET /archive/images/{id}`、`POST /archive/images`、`GET /archive/export` 提供当前档案敏感读取、图片维护及导出。材料遵循本份表单冻结的允许格式，经 Infra 加密存储；维护图片标记 archive_only，不进入原始收集表投影。移除仅改变新版本引用，历史材料保留；取消维护产生的未引用图片暂保留，不能清理任何历史引用。升级使用 V327，不自动更新主档或产生审批。

## 履约与链接

公开填写页支持服务端自动保存草稿：停止编辑约 700ms 后串行保存，后台可见性切换时也尝试立即保存；保存期间继续编辑会排队保存最新内容。顶部显示待保存、保存中、已保存或错误，普通失败可重试；版本冲突停止旧版本保存，提供“重新加载最新草稿”，提示先另行保留本地内容，确认后读取服务端最新草稿，不自动合并或覆盖其他页面内容；仍未保存或附件上传中离开会提醒。浏览器被强制关闭、断网或进程终止前尚未收到保存成功的修改不保证恢复。前端不把个人信息或链接令牌写入 localStorage/sessionStorage。

`GET /public-api/zsjos/student-info-form/detail` 在有效 DRAFT 状态增加 `draft: {revision, savedAt, values, selections}`；已提交、过期或撤销状态不返回草稿。`PUT /public-api/zsjos/student-info-form/draft` 接收 `{revision, values}`，复用 X-Student-Info-Token、租户上下文、订单→任务→表单锁和状态校验，响应同一草稿对象；请求/响应日志关闭、Cache-Control 为 no-store。未填写完整的文字可保存，字典保存 code/type/label 快照；未知字段、过长内容、非法字典值仍拒绝。年龄/性别/户籍所在地由身份证恢复派生，附件继续独立上传，草稿不保存这些客户端派生值或附件 ID。版本冲突返回 1900090011，保留本页内容并阻止继续正式提交，避免多页静默覆盖。

草稿以现有 EncryptTypeHandler 加密存储在表单的 draft_snapshot 中，draft_revision/draft_saved_at 仅保存版本和时间。不写正式答案、不进入 Admin/Workbench 已提交档案或导出；正式提交仍完整校验并冻结一份答案，同时清空草稿内容。未修改字典选择沿用草稿标签快照，正式提交仍验证当前可选性。V342 前置为两本台账的 V323，仅新增这三个草稿列，无历史内容回填或角色授权；部署顺序为 V342 → 匹配后端 → H5。新旧 Admin/Workbench 无草稿编辑调用，既有详情与提交记录投影保持不变。校验入口：`python script/sql/mysql/tools/test_student_information_draft.py`，本地增量同步加 `--sync-local`；保留结构备份和隔离验证库。

公开填写页的字典选择按视口宽度适配：大于 600px 使用字段旁的下拉列表，点击立即选中，支持鼠标滚轮、方向键、回车、Esc、Tab 和点击外部关闭；超过十项时显示搜索。600px 及以下保留原底部弹层、短列表滚轮选择和长列表搜索后的确认/取消交互。选项与提交值均沿用后端表单快照，不新增静态业务选项。

基本信息、学历与报考、职业与学习目的、证明材料四个分类使用内联 SVG 装饰图标；仅显示含字段的分类，首次进入或切换收集链接时默认全部展开。学员可手动折叠、重新展开，已输入内容保留；缺少必填项时仍自动展开对应分类。

生成链接后由履约人员实际发送，勾选“我已将收集表链接发送给该学员”即时保存操作人和时间。完成报名履约只检查已发送，不等待学员提交；财务、清单、分班等其他前置条件保持原规则。待处理任务适用，终态任务不追补、不重新打开。

H5 地址依次读取 zsjos.student-info.public-base-url、zsjos.positioning.public-base-url、ZSJOS_PUBLIC_H5_BASE_URL；配置为可从学员设备访问的 H5 根地址。链接路由 /student-info-form#token=…，32 字节安全随机令牌，摘要检索、原文加密；公开 API 通过 X-Student-Info-Token 接收，不在查询字符串传令牌。

本地配置入口为 `backend/yudao-server/src/main/resources/application-local.yaml` 的 `zsjos.student-info.public-base-url`，默认使用当前开发机局域网 H5 地址 `http://192.168.2.17:10086`。可通过 `ZSJOS_STUDENT_INFO_PUBLIC_BASE_URL` 单独覆盖，或通过 `ZSJOS_PUBLIC_H5_BASE_URL` 统一指定 H5 地址；单独覆盖优先。更换网络后须更新地址，外网学员应使用可公网访问的 HTTPS 域名。该配置由生成链接的后端读取，不在表单字段配置页面维护；修改配置需由对应后端加载后才生效。

链接展示使用顶部中世健教育 Logo、标题“学员报名信息收集表”和填写说明的二维码分享卡片；Workbench 与 Admin 均可复制完整的 800×1080 PNG 图片到剪贴板，也可保存图片。复制成功提示可粘贴发送，不支持剪贴板或授权失败时提示保存图片，并允许重试。二维码编码实际完整链接，生成时间、有效期和管理操作保留在卡片外。H5 字典采用主题选择弹层：短列表使用 Vant Picker，超过十项时使用可搜索选择列表；选择后确认，取消保留原值。选项仍来自后端字典。

公开收集表的年龄、性别和户籍所在地由有效的18位身份证自动识别，始终只读，不提供手动选择。年龄按周岁计算，性别标签取 System 字典，户籍地按前六位地址码匹配 System 地区树并保存完整地区路径及标签快照；该地址码不保证反映后续户籍迁移。身份证清空或无效时清空联动结果；历史地址码匹配失败提示联系报名老师维护地区配置，不推测相邻地区。提交时后端独立重算年龄、性别和地区路径，不能通过请求伪造派生值。档案维护保留其独立的历史快照和修订规则。

收集表链接复用统一组件：Workbench 使用 ResourceLink 资源卡片，Admin 使用只读 ResourceLinkInput 并通过 append 插槽提供复制按钮。两端均支持新标签页打开与复制，完整保留链接的 token 片段。

链接有效期 30 天。重复生成保留当前链接；重新生成轮换令牌、保留原表单快照并清除发送确认，需重新发送。撤销同样清除发送确认。已提交不可轮换或撤销。正常完成履约后仍可填写，任务取消／结束或订单终止／被替代后未提交链接失效。提交同事务按订单→任务→表单加锁，成功只保存一份；重复提交返回成功，不追加答案。公开已提交页面不回显个人答案或图片。

## 后台配置与字段

管理端保留草稿、版本、发布及产品预览。固定 32 项字段，允许维护启用、默认必填、分组、排序、备注、字典来源和材料数量；单文件大小展示统一的服务端 100MB 策略，不再逐字段设置，不支持新增任意业务字段。

| 分组 | 字段 |
| --- | --- |
| 基本信息 | 姓名、身份证号码、性别、年龄、民族、户籍所在地、手机号码、是否复训、现学历、礼品邮寄地址、证书邮寄地址 |
| 学历与报考 | 毕业院校、毕业专业、毕业时间、报名层次、拟报名院校、拟报名专业 |
| 职业与学习目的 | 工作单位、从事职业、目前持有证书、拟报名学习的目的 |
| 证明材料 | 证件照片、身份证正反面、毕业证、学历认证报备、报名申请表、工作证明、社保流水、劳动合同、前置证书、前置证书的网查截图、承诺书 |

预设必填：姓名、身份证号码、户籍所在地、手机号码、现学历、毕业专业、毕业时间、从事职业、目前持有证书、学习目的。普通必填可后台调整。身份证使用 18 位校验；性别和年龄由身份证计算并提交时冻结。民族选项来自 `zsjos_student_info_ethnicity` 字典，汉族（value `01`）置顶，其余选项保留管理员配置顺序。毕业时间保持文本，没有证书可填“无”。地区来自 System 地区树，保存完整路径快照。

后台选择学历提升产品分类 ID，包含全部下级分类；生成时按真实产品归类计算。命中时报名层次、拟报名院校、拟报名专业显示且必填，未命中隐藏。分类名称不参与判断。已发出的字段／条件不追随后续配置或分类变化。发布时拒绝停用条件必填字段、空字典和冲突配置。

性别绑定 system_user_sex。V324 根据用户确认新增四个专用字典并由新配置预设自动绑定：zsjos_student_info_ethnicity（56 个民族）、zsjos_student_info_retraining（是／否）、zsjos_student_info_education_level（博士／硕士／本科／大专／中专／高中／初中／其他）、zsjos_student_info_registration_level（高起专／专升本／统考硕士研究生／同等学历申硕）。仅补充缺失选项，保留管理员标签、停用、删除及自定义选项；不重绑定已保存配置，不自动发布或授予权限。前端从字典 API 读取，提交保存编码和标签快照。

## 材料附件与邮寄地址

2026-10-19 按本次用户要求扩展材料格式，替代此前仅图片的限制：

| 字段 | 格式 |
| --- | --- |
| 证件照片、身份证正反面、毕业证、前置证书、前置证书的网查截图 | JPG/JPEG/PNG |
| 学历认证报备 | PDF |
| 报名申请表 | JPG/JPEG/PNG/PDF/DOC/DOCX |
| 工作证明、社保流水、劳动合同、承诺书 | JPG/JPEG/PNG/PDF |

新增礼品邮寄地址、证书邮寄地址，顺序为礼品在前、证书在后，默认选填、按敏感信息保护；新预设共32项。已有配置在配置界面升级为32项；新增字段仍需保存发布后才进入新表单，不自动发布、不重写已经发出的字段快照或历史提交。所有未提交（DRAFT）的表单在运行时按上表返回当前附件格式，并按2026-10-22要求采用统一100MB大小上限；覆盖旧快照缺少 allowedExtensions、仅图片格式或旧大小的限制，字段数量、必填和既有材料不变，已提交表单保留历史快照。新生成表单也保存当前附件策略。字段仍使用兼容的 images 类型和 /images 接口，allowedExtensions 是服务端附件格式契约，不是可编辑业务字典。图片显示预览，PDF/Word 提供受保护的下载入口。此行为需要部署匹配的后端及前端后生效，仅修改源码不会改变正在运行的服务。

身份证正反面两个独立位置，各最多一张；其他每类包括证件照片支持多个附件，默认最多九个，后台 2～9 个。2026-10-22 起，公开收集表单个附件统一不超过 100MB（100×1024×1024 字节），不再按字段配置更小上限；配置页面展示服务端返回的统一大小。新表单和既有 DRAFT 表单运行时采用此策略，不改已提交快照。初始材料均选填；身份证设必填时必须正反面齐全。独立的考试/证书档案附件维护仍按其原有 10MB 契约。本次不调整图片最多 4000 万像素或 DOCX XML 单条解压 20MB 的内容校验边界。

H5 附件上传和下载请求超时为10分钟，PDF/Word 在点击时获取文件字节，避免进入表单就预取全部大文件；图片继续加载缩略图。后端全局 multipart 配置为单文件1024MB、单请求2048MB，业务层独立执行收集表100MB上限；部署代理的请求体限制和超时也须覆盖目标大小，本地模拟传输验证不代表部署代理验收。

空文件与大小超限分别提示：0 字节文件提示“文件为空，请保存有效内容后重新上传”（错误码 `1900090012`），超限提示“单个附件不能超过 {上限}MB”（错误码 `1900090013`）。H5 选中空文件时直接拦截，不发起上传、不占用附件名额；后端公开收集上传及共用的报名收集资料档案上传也分别校验，返回具体原因与实际字段上限。允许格式、大小上限和内容校验不变。

PDF/Word 按桌面文件形式显示：上方为红色 PDF 或蓝色 W 的 SVG 文件图标，下方为两行内的文件名，长名称省略、悬停可查看完整名称，点击下载保留服务端名称；图片缩略图与全屏预览不变。DOCX 内容校验复用现有 Excel 运行时的内存 ZIP 中央目录读取，兼容 STORED 条目带 data descriptor 的有效 ZIP，不将明文写入临时磁盘。仍按字段限制扩展名，核对 OOXML 必需条目，并限制 ZIP 条目数与单条解压大小；损坏 ZIP 返回明确的重新保存提示。该校验由公开收集表及两端档案上传共用，接口与权限不变。

V341（前置为两本台账中的 V323）将收集附件 `content_type` 从 VARCHAR(32) 扩至 VARCHAR(128)，以容纳 71 字符的 DOCX MIME。历史 V323 不重写；新库也须继续执行到 V341，不能只初始化旧基线。迁移仅扩容字段并记录两本台账，不改业务行或授权；DDL 不可事务回滚，部分失败后检查实际列和两本台账再重放。已有长类型值后不可缩列。验证入口：`python script/sql/mysql/tools/test_student_attachment_content_type.py`；本地范围同步加 `--sync-local`，保留结构备份。上线必须包含 V341 和匹配后端构建/重启，前端更新不会加载新的后端校验器。

前端保留材料多选、图片缩略图/预览、文档下载、进度、失败重试和删除。收集表直接打开时独立加载图片预览样式；图片在深色遮罩上按比例全屏查看，可点击关闭按钮或轻触图片关闭，预览层不继承普通弹窗的白色玻璃背景。附件上传队列复用 H5 现有兼容编号生成方法，支持本地局域网 HTTP 与 HTTPS 访问，不依赖仅安全上下文可用的 crypto.randomUUID。正在上传或存在失败项时阻止提交。后端按字段校验扩展名和内容类型，图片继续使用 ImageIO 校验可解码内容与最多 4000 万像素，文档校验 PDF/OLE/OOXML 文件特征，并校验配置大小／数量以及表单归属；答案中的附件 ID 必须覆盖当前已上传引用，不允许引用其他表单。

Infra 只持有 AES-GCM 加密文件，目录 student-info-private/{formId}/…；不返回永久存储 URL。公开读图同时检查 token 和待提交状态，内部读图要求 read 与 sensitive-read 并校验报名对象权限。密钥派生自 mybatis-plus.encryptor.password，必须持久保存并备份，不能随部署变更。存储成功而事务失败时删除补偿；用户删除先移除业务引用，提交后删除 Infra 文件。独立 Quartz handler studentInfoImageCleanupJob 每次最多扫描 100 份过期七天以上的未提交表单，按真实绑定和文件目录再次检查后清理；默认不创建或启动计划，管理员按受控部署配置。

上传限流每 token 每分钟 30 次、每连接来源 IP 每分钟 120 次；日志不记录 token、答案或完整身份证／手机号；响应 no-store。文字答案继续使用框架加密，普通 /detail 响应保持脱敏；敏感读取和导出权限独立。表单不回写主档、不触发 BPM。

## API

管理 API 使用 ADMIN 认证、tenant-id: 1。统一基础路径 /admin-api/zsjos/registration/{caseId}/student-info-form：

| 方法与后缀 | 行为与权限后缀 |
| --- | --- |
| POST 基础路径 | 生成；create |
| GET /link | 链接；link-read |
| POST /regenerate、/revoke | 轮换／撤销；regenerate／revoke |
| POST /sent | 确认或撤销发送；sent |
| GET /detail | 脱敏资料；read |
| GET /sensitive | 完整资料；read + sensitive-read |
| GET /export | Excel；read + export，无敏感权限继续脱敏 |
| GET /images/{imageId} | 图片字节；read + sensitive-read |

权限前缀为 zsjos:student-info-form:。修改体包含 version、idempotencyKey；轮换／撤销／确认还传 formId，确认传 sent。修改返回 Link（含 registrationVersion）；事务幂等键核对操作人、任务、命令及请求。链接查询与修改仍受报名对象权限约束；仅可读学员服务的人员不能获取公开填写令牌。资料读取按当前服务对象权限。

配置基础路径 /admin-api/zsjos/student-info-form/config：GET、POST /draft、POST /publish、POST /preview；fields 每项新增 group、maxImages、maxSizeMb，版本／保存新增 educationCategoryIds，预览可传真实 productId。管理员还需已有产品和字典查询权限以加载参考数据。

公开基础路径 /public-api/zsjos/student-info-form：GET /detail、POST /submit、POST /images（multipart: fieldKey、side、file）、GET/DELETE /images/{id}。图片提交为 values 中对应字段的图片 ID 数组，身份证 side 为 front／back，其余为空。公开端无员工／合作方登录，不支持短信认证或服务端文字草稿。

报名详情新增 informationSent、informationSentAt、informationSentByUserId、informationSubmissionStatus；完成资格仍由服务端返回。服务投影增加 registrationCaseId；摘要和详情均按该任务读取表单，历史无来源服务显示无表单，不按 Person 或客资猜测。

## 数据库与启用

V323 依赖两份台账的 V322。保留历史 V183、bootstrap 及原台账校验和，通过追加升级同步新结构；schema/core.sql 为当前目标结构。新增表单绑定／快照／发送列、配置分类列、任务唯一索引、图片关联表以及 sent 权限元数据，移动既有操作按钮至报名履约菜单；不更改 system_role_menu、不删除业务数据。

执行前备份相关表和菜单／台账。DDL 隐式提交，失败须核实真实结构再重放；版本记录仅在全部后置检查通过后的事务内写入。回滚保留数据与加密密钥，旧客资级代码不兼容新流程，不可直接降级运行。

验证入口：python -B script/sql/mysql/tools/test_registration_student_information.py；本地授权应用加 --sync-local。脚本保留隔离数据库，覆盖首次、重放、部分失败、错误类型、缺前置版本、旧实例保护、台账写入失败、UTF-8 HEX 和目标结构比对。read-only 检查为 verify-student-information-collection.sql。

上线顺序：备份／核实无旧数据 → V323 → V324 字典迁移 → V327 档案权限与原修订 → V332 考试与证书档案 → V335 考试字段可见性配置 → 匹配后端与 Workbench 发布 → 配置 H5 HTTPS 根地址／代理与加密密钥 → 管理员核对预设字典、选择学历分类并发布表单 → 分配生成、链接、发送和资料权限及考试查询/管理权限 → 真实账号生成、发送、履约完成及学员填写验收。数据库迁移本身不发布表单、不自动分配权限。V324 验证入口为 python -B script/sql/mysql/tools/test_student_information_dictionaries.py，本地授权同步加 --sync-local。仅本地测试不代表目标环境验收。


2026-10-22：公开正式提交增加必填 revision，必须等于锁定表单的 draftRevision；陈旧页面返回 1900090011，不写答案或清空新草稿。H5 提交前串行保存当前草稿并携带返回版本；后端与 H5 配套发布。旧令牌请求结束不会吞掉新表单的自动保存。

## Workbench 报名资料默认完整展示（2026-10-23）

报名履约“学员报名资料”弹窗和个人背景“完整学员资料”抽屉共用 StudentInfoPanel，按冻结 fields 的 group/sort 分区展示；dict/area 值使用浅底色标签呈现服务端历史显示值，textarea、长文本和材料完整展开。资料信息独立展示提交时间和表单版本，不改变字段、报名关联及权限协议。

打开或刷新时先读取 /detail，依据服务端 canReadSensitive 决定已提交资料是否自动继续读取 /sensitive；具备权限时默认展示全部完整字段。两步加载结束后统一渲染，已移除手动查看/隐藏按钮、脱敏缓存和显隐状态。无敏感权限或未提交时直接使用 /detail，不请求完整接口。任一步失败均显示可重试错误并清空旧资料；不以脱敏回退掩盖完整读取失败。关闭、刷新、切换报名及父入口失去 read 权限时仍使旧请求失效并卸载附件。

所有附件改用通用 AttachmentCard 展示文件名、类型和大小，替代文件名拼接及批量预取；各卡片点击预览/下载时才调用既有 /images/{id} 鉴权字节接口，沿用响应元数据 contentType 创建临时 blob URL，不访问 Infra 永久地址。关闭、刷新和切换报名撤销临时 URL，迟到附件响应同样撤销；无 canReadSensitive 不提供附件操作。PDF/图片预览及文档下载沿用通用组件能力。canReadSensitive/canExport 仍来自服务端，默认完整展示不改变导出授权或后台权限。Admin、公开收集表与服务关系档案维护不受此展示改动影响。

## Workbench 学员档案默认完整阅读（2026-10-23）

学员管理“学员档案”标签页按冻结字段 group/sort 分区展示报名收集资料，dict/area 历史显示值以浅底色标签展示。逐项编辑、原提交快照、维护版本/历史和考试字段可见性保持既有契约。

打开、刷新及保存后的客户端读取流程为 GET /archive → 根据 information.canReadSensitive 与 SUBMITTED 状态自动 GET /archive/sensitive，再展示最终结果；不再提供敏感查看/隐藏按钮。自动敏感读取失败显示独立错误/重试，避免将普通脱敏响应误报为完整资料。无敏感权限不调用 /sensitive，继续服从后端返回的脱敏及附件限制。对象/服务关系校验保持不变；敏感及导出权限按下文 2026-10-25 规则独立配置，Admin 的既有调用路径保持兼容。此展示规则不改变报名资料弹窗/个人背景抽屉的显式查看与隐藏。

报名材料及考试材料的阅读和行内编辑使用通用 AttachmentCard，初始只加载元数据，按需通过原 /images/{id} 或 /extra/attachments/{id} 读取字节。材料始终绑定当前 serviceRelationId；刷新、课程服务切换及组件卸载时释放 Blob 和预览，忽略或清理旧响应。不会改走永久文件 URL、重写历史材料或授予权限。


## 学员档案权限与报名履约分离（2026-10-25）

学员档案以服务关系为边界，独立使用 `zsjos:student-archive:sensitive-read`（查看学员档案敏感资料及附件）和 `zsjos:student-archive:export`（导出学员档案）。两个按钮均挂在学员管理页面，与已有 `read` / `update` 同级；不再要求勾选报名履约公共池下的敏感读取、导出按钮。

| 档案接口 | 累计功能权限 |
| --- | --- |
| GET /archive | student-archive:read |
| GET /archive/sensitive、GET /archive/images/{imageId} | student-archive:read + student-archive:sensitive-read |
| POST /archive/images | student-archive:read + student-archive:update + student-archive:sensitive-read |
| GET /archive/export | student-archive:read + student-archive:export；未授予档案敏感读取时导出脱敏值 |

档案编辑读取及保存敏感字段/附件、考试/证书扩展档案附件及邮寄地址同样使用档案敏感权限；原服务关系对象授权、租户隔离、附件归属和生命周期限制保持不变。仅有报名信息表敏感/导出权限不再授权档案读取；仅有档案权限也不能读取报名信息表接口、公开填写令牌或公共池。报名履约接口继续使用原 `zsjos:student-info-form:*`。

Workbench 与 Vue Admin 均继续消费档案响应 `information.canReadSensitive` / `canExport`，接口路径和结构不变，不在前端推断角色或复用报名权限。前述展示说明中的敏感/导出授权自本次起按本节分离。

升级：正常 Core 顺序 V349 → V350__student_archive_read_permissions.sql → 匹配后端 → 管理员在角色管理的学员管理下显式选择新按钮。V350 前置为 V327 两本台账及既有档案 read/update 按钮，只新增两个菜单定义及 V350 台账，保留原有角色授权、报名按钮和业务数据；不自动继承或补授。旧报名授权不再作为档案敏感/导出授权，需在上线窗口明确配置档案角色。已部署 V327 不重写。重放保留管理员名称/启停/排序和旧版本校验和；失败检查实际菜单和两本台账再恢复，不仅依据版本号跳过。回滚应配套后端并保留菜单及台账，权限调整由管理员完成。

验证入口：`python -B script/sql/mysql/tools/test_student_archive_read_permissions.py`，使用本地 MySQL 隔离库，覆盖首次、重放、部分元数据/台账恢复、缺前置、菜单身份冲突、台账写入失败、UTF-8 HEX 及角色授权不变。
