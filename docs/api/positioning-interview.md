# 中仕健学员定位访谈

定位访谈主流程为：资料预审 → 预约定位访谈 → 开始／继续定位访谈 → 保存草稿 → 完成定位访谈。
完成命令本身不自动创建定位卡、账号、内容或拍剪任务。完成后，当前责任编导可通过服务端投影的
`CREATE_MEDIA_ACCOUNT` 显式新增账号；账号后续能力仍使用各自既有权限和状态机。旧表与历史记录保留。

## 主体、授权与状态

业务主体是 tenant + studentPersonId；serviceRelationId 是当前责任编导的授权上下文。
每个业务接口累计检查功能权限、student-service 对象权限、租户、有效且已接收的服务关系和当前责任编导。
写入锁定学员，再锁定并重新校验服务关系，避免等待锁期间的责任转移导致越权。
草稿 version 独立增长；完成才增长服务关系业务版本。

新状态为 precheck、positioning_interview、positioning_interview_completed。旧阶段只作历史展示。
按学员查询当前访谈，不按课程或账号重复创建。完成记录只读，本次不提供再次开启访谈的操作。
数据库预留 completed/superseded 多轮历史容纳能力，唯一生成列限制同一租户学员只有一个活动草稿。

## HTTP 契约

下列路径前缀均为 `/admin-api/zsjos/student/service/{relationId}`：

| 方法与后缀 | 行为 | 功能权限后缀（zsjos:student:） |
| --- | --- | --- |
| POST /precheck/draft、/precheck/submit | 原预审契约；预约时间晚于当前北京时间 | director-precheck |
| GET /positioning-interview/context、/positioning-interview | 当前草稿／完成快照与旧采访 | positioning-interview-query |
| POST /positioning-interview/draft | 部分保存；允许无稿、无确认 | positioning-interview |
| POST /positioning-interview/complete | 校验并完成，只读封存 | positioning-interview-complete |
| POST /positioning-interview/attachments | multipart file，经 Infra 存储及业务绑定 | positioning-interview |
| GET /positioning-interview/attachments/{fileId} | 授权后返回 300 秒签名 URL | positioning-interview-query |
| DELETE /positioning-interview/attachments/{fileId}?version=N&idempotencyKey=KEY | 只移除草稿文件绑定；重新读取版本 | positioning-interview |

写请求包含 studentPersonId、serviceRelationId、templateVersionId、version、idempotencyKey、
collectedAt（YYYY-MM-DD）、items（fieldKey/status/remark）和 attachmentIds。
返回 context 包含上述主体、模板 fields、items、attachments、statusOptions、availableActions、
studentName/studentNo、interviewAt、completedAt、legacyInterviewSnapshotJson。
文件元数据使用 fileId/fileName/mimeType/fileSize；下载额外返回 url。

状态协议固定为 COMMUNICATED_DOCUMENT（已沟通，见文稿）、NOT_COMMUNICATED（未沟通）、
CLIENT_REFUSED（客户拒绝回答），初始为空。客户端使用服务端状态标签。
普通启用必填行必须选择状态；学员姓名／编号是只读系统值，采集时间是日期控件。
“未沟通”及“客户拒绝回答”允许完成，完成必须有至少一份本次访谈稿；已沟通项共同引用本轮稿件。
支持文档（pdf/doc/docx/xls/xlsx/ppt/pptx/csv/txt/md/rtf）、图片（jpg/jpeg/png/webp/gif/bmp/heic/heif）、
音频（mp3/wav/m4a/aac/flac/ogg/amr）与视频（mp4/mov/webm/mkv/avi），每份最多 20MB，最多 20 份引用。
入库前同时校验扩展名白名单与 Tika 内容探测结果，伪装成受支持扩展名的内容按 004 拒绝；入库 MIME 取探测结果而非客户端声明。
文件校验创建者、学生、租户及业务目录。
完成保存字段与状态标签、模板定义、系统值及文件元数据快照；历史不读取当前字典补造标签。

稳定错误：1900090001 表单校验；002 状态；003 版本冲突；004 文件归属；005 缺少稿件；006 幂等冲突。
未授权使用既有 STUDENT_PERMISSION_DENIED。草稿/完成重复请求保留同一幂等键和请求内容。

## Admin 与 Workbench

Admin 与 Workbench 统一通过“编导业务配置 → 定位访谈大纲配置”配置表单，
正式路径保留 `/zsjos/director-config/interview-template`，请求 `/admin-api/zsjos/positioning-interview-template`，场景为
director_positioning_interview；list、详情、draft/copy、draft PUT、publish 使用既有采访模板管理权限。
发布版本不可编辑；修改先复制草稿。字段名称、访谈注意、排序、停用、必填、备注、附件及可见性由后台维护。
studentIdentity/text 和 collectedAt/date 是系统字段，普通行是确认项，提示中的复选文案仅是提示文本。
旧 `/zsjos/director-interview-template/**` 配置接口和 `/student/service/{id}/interview/draft|submit`
已移除。旧 director_interview 模板与业务快照仅保留历史数据；无历史记录时不加载旧模板。
模板服务也拒绝旧场景的复制、保存和发布。定位卡 `/zsjos/positioning-template/**` 独立保留。
两端配置按查询、修改、发布权限分别控制，读取已发布或历史版本只读，修改需复制草稿、保存后发布。

媒体学员保留既有左侧列表、头像姓名头部和概览资料布局。概览展示责任关系、预约、进度、稿件数及
统一操作栏；每个真实账号以账号昵称作为与“概览”同级的标签，承载既有账号维护、定位卡、内容与拍剪能力。
“新增账号”只依据服务端 `CREATE_MEDIA_ACCOUNT` 动作显示，请求携带服务关系版本与幂等键。
独立弹窗桌面端每行横向展示字段、访谈注意和访谈确认；小屏依次纵向排列字段名称、访谈注意、确认选项和备注，不横向滚动。
支持草稿、校验、上传失败重试、版本错误重载及完成只读。

## 初始化与验收

V203 在 V202 后执行，建立三张表和 52 项预置大纲。不覆盖已有模板，不授予任何角色权限。
新按钮须由管理员通过既有权限配置授予；菜单可见不能替代责任校验。
若开发库存在之前 V203 创建的 21 字段占位版本，同一 UTF-8 MySQL 会话先执行 V203，
再执行 `script/sql/mysql/repair-positioning-interview-placeholder.sql`，只为未编辑的占位版发布版本 2，原字段保留。
正式环境不得用该开发修正覆盖自定义配置。验证脚本为 `verify-positioning-interview.sql`。

浏览器验收脚本 `script/verify-positioning-interview-ui.cjs` 使用纯测试入口和合成 API，
覆盖 52 行、草稿、必选校验、互斥、上传、完成、只读及桌面／移动布局；不向开发业务 API 写入数据。
真实服务重启、开发库同步和实际角色授权须按仓库的共享状态规则执行，合成验收不替代真实环境联调。

### 2026-09-11 开发基线收尾

V203 在既有表单初始化之外，将当前采访配置菜单及保存/发布按钮重命名为定位访谈大纲，
并将 `zsjos:student:director-interview` 旧采访操作设为停用、隐藏。保留稳定模板权限标识
`zsjos:director-interview-template:query/update/publish`、菜单 ID 和路径，不更改任何角色授权关系。
该修改适用于尚在开发的基线；已部署环境需按升级规则单独处理，不能重写其历史迁移。
开发库已有 V203 时只执行文件中标记的菜单修正段（UTF-8/utf8mb4，先备份四个菜单字段），
不得为这次菜单修正重跑其他数据初始化。顺序、重复执行及完整空库验证见
`script/sql/mysql/tools/test_positioning_interview_retirement.py`；双端交互验收见
`script/verify-positioning-interview-config.py`。历史模板版本、业务记录和权限关系保持不变。
