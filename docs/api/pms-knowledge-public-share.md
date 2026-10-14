# PMS 知识库公开分享

在线文档支持显式勾选公开附件，旧分享及新上传附件默认不公开；接口、权限例外和迁移顺序见 [文档附件](pms-knowledge-attachments.md)。

知识库文档、分享令牌和有效状态仍由 PMS 持有；只对现有 PMS 分享能力调整地址和匿名阅读投影，不调用 System 公告内部实现。管理入口为 Vue Admin；Workbench 没有独立知识库分享 API 消费者，嵌入 Admin 的入口沿用同一组件。

## 地址生成

- 原规则：管理浏览器 `window.location.origin` + `/pms/kb/document/share/{token}`，通过 Vue 页面再请求正文。
- 新规则：后端返回 `url = {public-base-url}/public-api/pms/kb/document-share/page?token={token}`，输入框、复制及二维码统一使用此字段。
- 配置优先级：`pms.knowledge-share.public-base-url` → `system.notice-share.public-base-url` → `ZSJOS_PUBLIC_H5_BASE_URL`。可单独设置 `PMS_KNOWLEDGE_SHARE_PUBLIC_BASE_URL`；不配置 PMS 时复用公告公开地址。仅接受带主机的 HTTP(S) 地址，可带部署路径前缀，去掉尾部 `/`，拒绝用户信息、查询串和 fragment。
- 开启前检查配置，无效时返回 `1053130062`，不写入分享。查询已有分享时若配置不可用，仍返回分享记录但不返回 URL，界面保留关闭操作，不回退到管理端域名。
- 原有 32 位 UUID 随机令牌不变；读取分享不会更换令牌。关闭后新旧链接都失效，重新开启生成新令牌；不增加到期时间。内部分享成员通知仍走原管理端相对路由。

已有 active 分享无需数据库修改或重新开启。部署后重新打开分享弹窗、复制链接或下载二维码即可取得新格式。已经发送的 `/pms/kb/document/share/{token}` 保持原 Vue 路由兼容。

本地 `application-local.yaml` 将 `pms.knowledge-share.public-base-url` 配置为
`http://127.0.0.1:48080`，直接访问后端公开页面；修改配置后需重启对应后端生效。
局域网成员访问时应改为后端所在电脑的可达 IP。`10086` 仅在 H5 服务已将
`/public-api` 代理至后端时适用。此 PMS 专用配置优先于公告和 H5 地址回退。

## 阅读接口和状态

`GET/HEAD /public-api/pms/kb/document-share/page?token=...` 返回 UTF-8 HTML；这是文档导航响应，不使用 JSON 包装。初始 HTML 已包含文档标题、正文摘要和 Open Graph 元信息，不需要 JavaScript 或登录，供聊天预览读取。富文本只保留公开阅读安全标签和 HTTP(S) 资源，保留表格结构，不改写数据库正文；编辑器自定义样式不直接透传。

文件文档页面显示标题及“打开／下载文件”。该链接指向 `GET /public-api/pms/kb/document-share/file?token=...`，重新校验分享和文档状态，再通过 Infra FileApi 生成 600 秒签名地址并重定向。图片/PDF 由浏览器打开，其他类型由浏览器下载或交给外部应用；不会在新 HTML 页面加载 Admin 的 FilePreview。已签发的文件 URL 遵循存储签名有效期，关闭分享不能撤回已下载内容。

沿用已有 PMS 分享功能权限和对象权限。公开入口只凭分享令牌读取正常状态文档，沿用 PMS 匿名租户处理；当前部署只有租户 1，不新增跨租户能力。关闭、删除或回收导致 410；文件类型不符或没有安全的文件地址为 404；临时服务故障为 503 并提供重试。HEAD 无正文，长度描述 GET 内容。

## 部署和验收

### 内部成员站内信模板

分享服务通过 System `NotifyMessageSendApi` 直接使用 `pms_knowledge_document_shared`，
参数为 `operatorName`、`documentTitle`、`publicUrl`、`documentRoute`，保留旧 `route` 参数兼容；不经过通知规则或企业微信通道。分享人名称使用分享时的用户昵称，空时使用账号名。开启分享通知所选成员，
保存成员只通知本次新增成员；操作人自己被排除。通知失败不阻断分享保存，不会自动补发。

升级已有环境：Core 初始化/迁移完成且模块账本已有 PMS V001 后，执行
`script/sql/mysql/migrations/pms/V002__knowledge_document_share_notify_template.sql`。
启用 `pms` 模块的现有数据库迁移工具会自动发现 V002；也可使用 UTF-8 MySQL 客户端
在目标库单独执行该文件。执行前保存该模板和 PMS 模块账本的查询结果；不要重跑全部历史迁移。
新环境按 Core → PMS V001 → PMS V002 顺序执行，既有 Core 与 PMS V001 文件保持不变。

V002 仅补齐缺失的一条 System 全局模板（该表没有 `tenant_id`），用于当前唯一租户 1 的
ADMIN 用户；不创建通知规则，不修改成员、角色权限或历史消息。模板为启用的 `in_app` 类型，
发送渠道名称“中世健消息中心”，标题“收到知识库文档分享”，摘要和正文包含分享人及文档名称。
Workbench 使用通用 ResourceLink 仅提供“公开查看”，支持复制和新标签页；Admin 使用 ElLink 仅打开同一个对外分享地址。不提供知识库内部跳转，也不依赖 `documentRoute` 参数；该参数保留兼容但不再用于展示。公开令牌校验不变，历史消息不反推分享人或链接。

模板和 `zsjos_module_schema_version` 的 `pms/V002` 记录在同一事务内写入，失败回滚；
不写 Core `zsjos_schema_version`。重复执行保留管理员文案、停用状态及版本记录，仅升级本次开发生成且仍与旧默认文案完全一致的模板，
也可恢复“仅模板存在”或“仅版本记录存在”的不完整执行；已删除、重复或参数冲突的模板会报错，
需要管理员核实。SQL 直接执行登记文件名摘要，现有 runner 按其规范登记实际文件摘要；
不自动对账历史已应用文件的校验值。回退可在 System 模板管理停用该模板，保留消息及版本历史。

校验入口：`python -B script/sql/mysql/tools/test_pms_share_notify_template.py`。
它在本地现有 MySQL 中创建并保留隔离验证库，覆盖前序 PMS V001、首次执行、重复执行、
部分执行恢复、管理员配置保留、前置/参数/账本/后置失败以及中文 `HEX()`。
上线需在目标库核对模板启用、参数和中文编码，再由业务用户给新增成员分享并检查实际站内信。
缺失查询结果不进入模板缓存，单纯补齐缺失模板无需为此重启服务；本地验证不代表远端送达验收。

### 公开页部署

需要发布后端与 Admin。公开域名及其路径前缀必须将 `/public-api/pms/kb/document-share/**` 转发给启用 PMS 的后端，保留 HTML、状态码及重定向 Location；仅有 H5 静态托管不能提供该入口。图片和文件资源也必须能从访问者网络访问。

公开响应使用 `no-store`、`no-referrer`、`nosniff`、禁止脚本的 CSP 和 `noindex/noarchive`。查询参数 token 是阅读凭据，代理、APM 和访问日志不得记录查询令牌；PMS 分享和文档 Mapper 应使用 INFO 或更高日志级别，避免 SQL DEBUG/TRACE 暴露令牌及正文。应用公开页禁用请求/响应业务访问日志。

定向测试覆盖地址配置、已有令牌链接、配置缺失可关闭、生命周期、HTML/HEAD、XSS、文件重定向和失效状态；浏览器使用隔离合成 API 与 Controller 导出 HTML 验证，不代表真实部署验收。上线后需检查外网 URL、反向代理日志脱敏、匿名访问及企业微信真实粘贴预览；客户端旧预览缓存可能继续显示旧标题。
