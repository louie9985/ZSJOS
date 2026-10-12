# PMS 知识库公开分享

知识库文档、分享令牌和有效状态仍由 PMS 持有；只对现有 PMS 分享能力调整地址和匿名阅读投影，不调用 System 公告内部实现。管理入口为 Vue Admin；Workbench 没有独立知识库分享 API 消费者，嵌入 Admin 的入口沿用同一组件。

## 地址生成

- 原规则：管理浏览器 `window.location.origin` + `/pms/kb/document/share/{token}`，通过 Vue 页面再请求正文。
- 新规则：后端返回 `url = {public-base-url}/public-api/pms/kb/document-share/page?token={token}`，输入框、复制及二维码统一使用此字段。
- 配置优先级：`pms.knowledge-share.public-base-url` → `system.notice-share.public-base-url` → `ZSJOS_PUBLIC_H5_BASE_URL`。可单独设置 `PMS_KNOWLEDGE_SHARE_PUBLIC_BASE_URL`；不配置 PMS 时复用公告公开地址。仅接受带主机的 HTTP(S) 地址，可带部署路径前缀，去掉尾部 `/`，拒绝用户信息、查询串和 fragment。
- 开启前检查配置，无效时返回 `1053130062`，不写入分享。查询已有分享时若配置不可用，仍返回分享记录但不返回 URL，界面保留关闭操作，不回退到管理端域名。
- 原有 32 位 UUID 随机令牌不变；读取分享不会更换令牌。关闭后新旧链接都失效，重新开启生成新令牌；不增加到期时间。内部分享成员通知仍走原管理端相对路由。

已有 active 分享无需数据库修改或重新开启。部署后重新打开分享弹窗、复制链接或下载二维码即可取得新格式。已经发送的 `/pms/kb/document/share/{token}` 保持原 Vue 路由兼容。

## 阅读接口和状态

`GET/HEAD /public-api/pms/kb/document-share/page?token=...` 返回 UTF-8 HTML；这是文档导航响应，不使用 JSON 包装。初始 HTML 已包含文档标题、正文摘要和 Open Graph 元信息，不需要 JavaScript 或登录，供聊天预览读取。富文本只保留公开阅读安全标签和 HTTP(S) 资源，保留表格结构，不改写数据库正文；编辑器自定义样式不直接透传。

文件文档页面显示标题及“打开／下载文件”。该链接指向 `GET /public-api/pms/kb/document-share/file?token=...`，重新校验分享和文档状态，再通过 Infra FileApi 生成 600 秒签名地址并重定向。图片/PDF 由浏览器打开，其他类型由浏览器下载或交给外部应用；不会在新 HTML 页面加载 Admin 的 FilePreview。已签发的文件 URL 遵循存储签名有效期，关闭分享不能撤回已下载内容。

沿用已有 PMS 分享功能权限和对象权限。公开入口只凭分享令牌读取正常状态文档，沿用 PMS 匿名租户处理；当前部署只有租户 1，不新增跨租户能力。关闭、删除或回收导致 410；文件类型不符或没有安全的文件地址为 404；临时服务故障为 503 并提供重试。HEAD 无正文，长度描述 GET 内容。

## 部署和验收

需要发布后端与 Admin。公开域名及其路径前缀必须将 `/public-api/pms/kb/document-share/**` 转发给启用 PMS 的后端，保留 HTML、状态码及重定向 Location；仅有 H5 静态托管不能提供该入口。图片和文件资源也必须能从访问者网络访问。

公开响应使用 `no-store`、`no-referrer`、`nosniff`、禁止脚本的 CSP 和 `noindex/noarchive`。查询参数 token 是阅读凭据，代理、APM 和访问日志不得记录查询令牌；PMS 分享和文档 Mapper 应使用 INFO 或更高日志级别，避免 SQL DEBUG/TRACE 暴露令牌及正文。应用公开页禁用请求/响应业务访问日志。

定向测试覆盖地址配置、已有令牌链接、配置缺失可关闭、生命周期、HTML/HEAD、XSS、文件重定向和失效状态；浏览器使用隔离合成 API 与 Controller 导出 HTML 验证，不代表真实部署验收。上线后需检查外网 URL、反向代理日志脱敏、匿名访问及企业微信真实粘贴预览；客户端旧预览缓存可能继续显示旧标题。
