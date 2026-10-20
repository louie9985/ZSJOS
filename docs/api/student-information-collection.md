# 报名履约学员信息收集表

中世健学员信息收集表归属于报名履约任务。每个任务一份，绑定 Person、订单商品和生成时的产品分类／字段快照；相同学员不同报名互不串用。销售客资入口及旧版 16 字段协议已下线。上线前必须核实不存在旧版实例／答案／配置，发现记录时停止转换并保留数据。

## 学员档案

2026-10-19 扩展：档案分为“考试与证书信息”和“报名收集资料”。前者绑定当前 serviceRelationId，无需生成或提交收集表即可维护；查看与维护继续分别使用现有 student-archive:read/update，服务对象可见性仍由后端独立校验。内部字段不进入公开收集表。Workbench 采用报名信息在上、考试信息在下的分组卡片布局，报名字段和考试字段都通过字段旁的修改标记维护，历史记录可展开查看。

| 内部字段 | 输入 |
| --- | --- |
| 对接渠道 | 文本 |
| 报名费 | 非负数字，最多两位小数，按十进制文本精确保留 |
| 准考证、理论、技能、发证单位 | 同一字段同时保存文本、HTTP/HTTPS 链接和 JPG/JPEG/PNG/PDF/DOC/DOCX 附件 |
| 论文/综合评审、证书图片 | 同一字段同时保存文本、HTTP/HTTPS 链接和 JPG/JPEG/PNG 附件 |
| 评定结果、证书邮寄单号 | 文本 |
| 证书邮寄地址 | 内部可维护文本；未手工维护时读取本次报名已提交的收集地址（含已有档案修订）；手工保存后以内部版本为准 |

本次不包含批量上传或 OCR。附件逐个上传，单个不超过 10MB，每字段最多九个引用；链接每字段最多九条。移除只改变新版本引用，历史引用和文件保留；取消编辑的未引用附件保留，不自动清理。文本与链接可以单独维护；材料读取、上传、移除和邮寄地址维护累加原 sensitive-read 权限。收集表之外的内部字段不会回写订单、收费流水或学员主档，报名费仅为档案记录。

新增 `/admin-api/zsjos/student/service/{relationId}/archive/extra`：GET 返回后端字段定义、复合值、附件元数据、维护记录和可维护状态；POST 接收 revision、idempotencyKey、reason、changes（各字段为 text、links、attachmentIds）；POST `/attachments` 接收 fieldKey/file；GET `/attachments/{id}` 返回受保护的下载字节；POST `/visibility` 接收可见性 revision 和完整字段布尔映射。来源、字段和租户必须一致。扩展修订与收集资料修订各自编号，服务行锁与唯一版本/幂等键防止覆盖。Excel 档案导出包含两个区域的文本、链接及可读取附件名称。

考试字段可见性按字段配置，不是整块考试信息开关。关闭某字段的“教务可见性”后，后端从普通读取响应中移除该字段及其值；具备 `zsjos:exam-calendar:query` 的考务查询权限仍可读取全部字段，设置修改要求 `zsjos:exam-calendar:manage`。角色名称不写入前端或迁移，管理员在 System 角色管理中将这两个权限配置给考务主管、考务专员及管理员。

V332 新增空的 `zsjos_student_archive_extra_revision` 与 `zsjos_student_archive_attachment`，V335 新增租户级 `zsjos_student_archive_extra_config` 可见性配置表，前置分别为 V327 和 V332；不改历史迁移、不重写历史资料、不分配角色。修订值使用现有框架加密，文件复用收集材料 AES-GCM 与 Infra 存储，目录 `student-archive-private/{serviceRelationId}/`；不暴露永久 URL。受控验证：`python -B script/sql/mysql/tools/test_student_archive_extra.py`，并覆盖 V335 的首次、重放、revision 冲突及字段过滤。业务数据库应用和匹配服务部署仍须单独执行。

学员详情增加“学员档案”标签页，Workbench 与 Admin 共用 `GET /admin-api/zsjos/student/service/{relationId}/archive` 读取报名收集资料。通过真实服务关系定位收集表，并再次核对 Person 和订单商品；同一学员不同报名分别展示，不因另一条可见课程开放范围外资料。无收集表或尚未提交时，仅报名收集资料区域显示空态。

新增独立配置权限 `zsjos:student-archive:read`（查看学员档案）、`zsjos:student-archive:update`（维护学员档案）。维护同时需要查看和维护权限，并通过当前服务对象可见性校验。标签页不再由原收集表 read 权限控制；履约详情的原资料查看入口保留。敏感字段、图片和导出分别累加既有 sensitive-read、export 权限。管理员通过系统角色管理分配，迁移不写角色授权。

报名收集资料的修订仅面向已提交档案：`GET /archive/edit` 返回可编辑原值及真实字典选项，`POST /archive` 接收 revision、idempotencyKey、reason、changes。新字典/地区选择重新校验并保存标签快照；未修改项保留历史快照，未改身份证不重算年龄。身份证修改同时派生性别和当次年龄。按冻结字段校验必填、格式及图片归属/数量。没有敏感权限不能读取或修改敏感字段和图片。

原始答案和材料引用不可变，最新档案写入加密的 `zsjos_student_archive_revision` 修订快照，保存版本、维护人、时间及原因。表单加锁、唯一版本和幂等键防止重复与覆盖。`GET /archive/sensitive`、`GET /archive/images/{id}`、`POST /archive/images`、`GET /archive/export` 提供当前档案敏感读取、图片维护及导出。材料遵循本份表单冻结的允许格式，经 Infra 加密存储；维护图片标记 archive_only，不进入原始收集表投影。移除仅改变新版本引用，历史材料保留；取消维护产生的未引用图片暂保留，不能清理任何历史引用。升级使用 V327，不自动更新主档或产生审批。

## 履约与链接

生成链接后由履约人员实际发送，勾选“我已将收集表链接发送给该学员”即时保存操作人和时间。完成报名履约只检查已发送，不等待学员提交；财务、清单、分班等其他前置条件保持原规则。待处理任务适用，终态任务不追补、不重新打开。

H5 地址依次读取 zsjos.student-info.public-base-url、zsjos.positioning.public-base-url、ZSJOS_PUBLIC_H5_BASE_URL；配置为可从学员设备访问的 H5 根地址。链接路由 /student-info-form#token=…，32 字节安全随机令牌，摘要检索、原文加密；公开 API 通过 X-Student-Info-Token 接收，不在查询字符串传令牌。

本地配置入口为 `backend/yudao-server/src/main/resources/application-local.yaml` 的 `zsjos.student-info.public-base-url`，默认使用当前开发机局域网 H5 地址 `http://192.168.2.17:10086`。可通过 `ZSJOS_STUDENT_INFO_PUBLIC_BASE_URL` 单独覆盖，或通过 `ZSJOS_PUBLIC_H5_BASE_URL` 统一指定 H5 地址；单独覆盖优先。更换网络后须更新地址，外网学员应使用可公网访问的 HTTPS 域名。该配置由生成链接的后端读取，不在表单字段配置页面维护；修改配置需由对应后端加载后才生效。

链接展示使用顶部中世健教育 Logo、标题“学员报名信息收集表”和填写说明的二维码分享卡片；Workbench 与 Admin 均可复制完整的 800×1080 PNG 图片到剪贴板，也可保存图片。复制成功提示可粘贴发送，不支持剪贴板或授权失败时提示保存图片，并允许重试。二维码编码实际完整链接，生成时间、有效期和管理操作保留在卡片外。H5 字典采用主题选择弹层：短列表使用 Vant Picker，超过十项时使用可搜索选择列表；选择后确认，取消保留原值。选项仍来自后端字典。

公开收集表的年龄、性别和户籍所在地由有效的18位身份证自动识别，始终只读，不提供手动选择。年龄按周岁计算，性别标签取 System 字典，户籍地按前六位地址码匹配 System 地区树并保存完整地区路径及标签快照；该地址码不保证反映后续户籍迁移。身份证清空或无效时清空联动结果；历史地址码匹配失败提示联系报名老师维护地区配置，不推测相邻地区。提交时后端独立重算年龄、性别和地区路径，不能通过请求伪造派生值。档案维护保留其独立的历史快照和修订规则。

收集表链接复用统一组件：Workbench 使用 ResourceLink 资源卡片，Admin 使用只读 ResourceLinkInput 并通过 append 插槽提供复制按钮。两端均支持新标签页打开与复制，完整保留链接的 token 片段。

链接有效期 30 天。重复生成保留当前链接；重新生成轮换令牌、保留原表单快照并清除发送确认，需重新发送。撤销同样清除发送确认。已提交不可轮换或撤销。正常完成履约后仍可填写，任务取消／结束或订单终止／被替代后未提交链接失效。提交同事务按订单→任务→表单加锁，成功只保存一份；重复提交返回成功，不追加答案。公开已提交页面不回显个人答案或图片。

## 后台配置与字段

管理端保留草稿、版本、发布及产品预览。固定 32 项字段，允许维护启用、默认必填、分组、排序、备注、字典来源和材料数量／大小，不支持新增任意业务字段。

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

新增礼品邮寄地址、证书邮寄地址，顺序为礼品在前、证书在后，默认选填、按敏感信息保护；新预设共32项。已有配置在配置界面升级为32项；新增字段仍需保存发布后才进入新表单，不自动发布、不重写已经发出的字段快照或历史提交。按2026-10-19最新要求，所有未提交（DRAFT）的表单在运行时按上表返回当前附件格式，覆盖旧快照缺少 allowedExtensions 或仅图片的限制；字段数量、大小、必填和既有材料不变，已提交表单保留历史快照。新生成表单也保存当前附件格式。字段仍使用兼容的 images 类型和 /images 接口，allowedExtensions 是服务端附件格式契约，不是可编辑业务字典。图片显示预览，PDF/Word 提供受保护的下载入口。此行为需要部署匹配的后端及前端后生效，仅修改源码不会改变正在运行的服务。

身份证正反面两个独立位置，各最多一张；其他每类包括证件照片支持多个附件，默认最多九个，后台 2～9 个。单个默认 10 MB，后台 1～10 MB。初始材料均选填；身份证设必填时必须正反面齐全。

前端保留材料多选、图片缩略图/预览、文档下载、进度、失败重试和删除。正在上传或存在失败项时阻止提交。后端按字段校验扩展名和内容类型，图片继续使用 ImageIO 校验可解码内容与最多 4000 万像素，文档校验 PDF/OLE/OOXML 文件特征，并校验配置大小／数量以及表单归属；答案中的附件 ID 必须覆盖当前已上传引用，不允许引用其他表单。

Infra 只持有 AES-GCM 加密文件，目录 student-info-private/{formId}/…；不返回永久存储 URL。公开读图同时检查 token 和待提交状态，内部读图要求 read 与 sensitive-read 并校验报名对象权限。密钥派生自 mybatis-plus.encryptor.password，必须持久保存并备份，不能随部署变更。存储成功而事务失败时删除补偿；用户删除先移除业务引用，提交后删除 Infra 文件。独立 Quartz handler studentInfoImageCleanupJob 每次最多扫描 100 份过期七天以上的未提交表单，按真实绑定和文件目录再次检查后清理；默认不创建或启动计划，管理员按受控部署配置。

上传限流每 token 每分钟 30 次、每连接来源 IP 每分钟 120 次；日志不记录 token、答案或完整身份证／手机号；响应 no-store。文字答案继续使用框架加密，默认脱敏，敏感读取和导出权限独立。表单不回写主档、不触发 BPM。

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
