# 公告对外分享

System 持有公告、分享状态和附件选择；PMS 仅提供交互设计参考，没有代码调用或运行时依赖。Admin 和 Workbench 共用管理接口；新分享使用 System 服务端输出的匿名 HTML 阅读页，原 H5 匿名路由继续支持历史链接。

## 管理与生命周期

管理接口前缀为 `/admin-api/system/notice-share`，同时要求 `system:notice:query` 和 `system:notice:share`。权限元数据不自动授予任何角色；租户套餐和角色由管理员管理。

| 方法与路径 | 输入 | 行为 |
| --- | --- | --- |
| GET /get | noticeId | 状态、版本、附件选择、开启/关闭时间及有效分享 URL |
| POST /open | noticeId、attachmentIds 数组 | 仅 PUBLISHED 公告可开启；重复开启拒绝；默认空数组 |
| PUT /close | noticeId、version | 关闭当前版本，旧页面不能关闭重新开启后的分享 |

`attachmentIds` 与现有公告附件 VO 的 `infraFileId` 一致。服务端核对当前租户、当前公告的有效关联与 Infra 文件存在性，不能提交其他公告或租户的文件。最多选择 10 个，去重保存。无效文件返回公告附件错误，不以静态前端列表授权。

所有操作沿用 CommonResult。GET 未开启返回 active=false、attachmentIds=[]，version 可为空；开启时返回 active=true、version、attachmentIds、url。地址配置缺失时 GET 仍返回 active=true 且 url 为空，以便管理员关闭已有分享。公开范围不能原地编辑；关闭后重新开启时附件默认不选，生成新的 256 位随机令牌。token 全局唯一、区分大小写，公告分享记录按租户和公告唯一，version 在开启和关闭时递增。数据库保存令牌以支持有权管理员重新复制当前链接；令牌不进入日志。

开启、关闭及公告下线采用同一公告行锁。下线同事务关闭分享；公开读取仍校验公告状态。复制公告不继承分享。已发布正文沿用既有不可修改契约。

## 公开读取

新生成和重新查询的分享地址为 `{public-base-url}/public-api/system/notice-share/page?token=...`。服务器必须从页面请求识别公告，因此新入口使用查询参数中的随机令牌；不是内部公告 ID。已有 active 记录无需修改、重新开启或旋转令牌，管理端重新复制即可取得新格式。旧 `/notice/share#token=...` 继续通过 H5 阅读，关闭/重新开启对两种格式使用同一生命周期校验。

H5 JSON 请求仍使用 `X-Notice-Share-Token` 请求头；兼容查询参数 token，两者同时存在时请求头优先。新的 HTML 导航页及附件导航使用查询参数，反向代理/APM 必须隐藏查询参数，禁止记录完整链接。

| 方法与路径（前缀 /public-api/system/notice-share） | 输入 | 返回 |
| --- | --- | --- |
| GET /get | 分享令牌 | title、content、publishTime、attachments |
| GET /attachment-url | 分享令牌、attachmentId | 当前授权后的短期文件 URL 字符串 |
| GET /page | token 查询参数 | UTF-8 HTML；title、og:title、description、og:description 和公开正文在首次响应中完成渲染 |
| HEAD /page | token 查询参数 | 相同授权/状态及响应头，无正文；允许预览抓取方匿名探测 |
| GET /file | token、attachmentId 查询参数 | 重新校验后 302 跳转到短期文件 URL；失败返回可读 HTML |

`/page`、`/file` 是文档与浏览器导航接口，明确不使用 CommonResult JSON 包装。新页面不执行 JavaScript、不自动跳转、不按爬虫 User-Agent 区分内容。标题/摘要/文件名通过 HTML 节点转义；摘要来自已清理正文的纯文本，最多 160 个 Unicode 码点加省略号。只显示已选择附件，点击在新窗口校验并打开；文件失败不替换原公告窗口。不设置封面图或承诺客户端一定采用 Open Graph 元信息。

HTML 失效响应为 410，附件不可用为 404，缺失/错误附件参数为 400，临时故障为 503 并提供重试；这些状态的网页标题为通用说明，不回显原正文、令牌或异常详情。JSON 接口既有错误码保持不变。

附件公开投影只有 id（Infra file ID）、fileName、mimeType、fileSize，不返回未选择附件或管理字段。标题、正文和发布时间来自已发布公告；不新增另一份文章或历史快照。公开投影复用已有 Jsoup 库，采用独立严格标签/协议白名单，清除脚本、事件、iframe、表单及非 HTTP(S)/相对资源地址；保留表格、图片、视频及正常链接。通用 XssCleaner 会移除视频标签，因此公开投影不再次调用它；原有公告保存清理规则不变，已经被移除的历史媒体不会恢复。正文媒体仍使用原有绝对地址，不为外网分享自动迁移历史文件。

公开入口允许匿名，令牌定位是唯一跨租户查询；后续显式启用分享所属租户过滤，并检查租户存在、启用和未过期、分享当前令牌和开启状态、公告发布状态。外来 tenant-id 不能授予其他数据访问权。公开接口不读取接收人、组织、阅读人员，不调用 mark-read。

表格公开投影在清理前逐属性验证 table 及其后代的内联样式，仅保留有限尺寸、布局、颜色、边框、对齐及基础字体格式；不使用框架中直接放行任意 style 的通用规则。清理后生成 `notice-table-scroll` 包装和像素尺寸下限，colgroup、colspan、rowspan 保持有效；百分比宽度仍由容器计算。JSON `/get` 与 HTML `/page` 复用该投影，重复清理保持幂等，不写回已保存的正文。实现与验证见[公告正文表格](../frontend/notice-tables.md)。

文件地址通过 Infra FileApi 按文件 ID 和原配置签发，期限参数为 600 秒。不同存储实现可能返回公开地址；关闭分享只拒绝后续获取，不能收回已经取得的地址或文件。文件不存在显示附件不可用，正文仍可阅读。

## 错误与缓存

- 1002008007：分享内容已失效（令牌无效/关闭、公告下线/删除、租户不可用）。
- 1002008008：分享已开启，刷新配置。
- 1002008009：分享版本冲突，刷新配置。
- 1002008010：外部 H5 地址配置无效；开启拒绝，已有分享仍可关闭。
- 1002008011：分享附件不可用。
- 原 1002008003/1002008004：公告状态不允许/附件引用无效。
- 临时服务故障返回 500，支持重试；无权限沿用 403。

公开响应设置 Cache-Control: no-store、Referrer-Policy: no-referrer、X-Content-Type-Options: nosniff。HTML 另有 noindex/noarchive 和 CSP：禁止脚本、嵌入、表单及 base URL，允许已清理的 HTTP(S) 图片/视频和固定页面样式。专用异常处理不向通用异常日志传递查询令牌；仅记录故障类型，访问日志禁用参数和响应体。反向代理还须按部署说明隐藏查询参数。聊天客户端仍可能保留旧预览，服务端不承诺撤回已生成的卡片或已保存内容。

无到期时间、提取码、指定访客认证、朋友圈卡片、分享海报或外部阅读统计。内部接收范围不构成公开链接的访问限制。

## 验证入口

- System 定向测试：NoticeShareServiceImplTest、NoticeShareControllerTest、NoticeSharePageControllerTest，并回归 NoticeServiceImplTest、NoticeControllerTest、NoticeReadStatisticsTest。
- MySQL：`python script/sql/mysql/tools/test_notice_public_share.py`，仅创建自有验证数据库，保留供核查。
- 浏览器：`python frontend/workbench/test/notice-share-browser.py`，自有 5197/5198/5199 端口，Admin/Workbench 使用合成接口、H5 使用真实匿名路由加拦截响应。
- 服务端页面浏览器：`python frontend/h5/tests/notice-share-page-browser.py --fixtures <System模块>/target/notice-share-page`，读取 Controller 测试导出的实际 HTML，在临时本地服务验证无 JavaScript 的标题、正文、附件与错误页面。
- 对外域名、微信内置浏览器和真实账号权限属于部署环境验收；夹具不能替代。
