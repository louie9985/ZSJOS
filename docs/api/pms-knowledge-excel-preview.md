# 知识库 Excel 只读预览

知识库支持 XLS/XLSX：独立文件、正文附件、回收站、公开 HTML 分享及兼容 Vue 分享页共用 PMS 解析服务。Workbench 嵌入 Admin 时沿用同一实现。仅返回可见工作表，支持切换和滚动；保留横向、纵向和矩形合并单元格；不还原图片、图表及打印版式，不提供编辑。

## 接口与权限

| GET 接口 | 参数 | 授权 |
| --- | --- | --- |
| `/admin-api/pms/kb/document/excel/preview` | documentId、可选 fileId、sheetIndex | `pms:kb:library:query` + 知识库及文档可读 |
| `/admin-api/pms/kb/document/excel/recycle-preview` | recordId、documentId、可选 fileId、sheetIndex | `pms:kb:library:delete` + 既有回收站对象校验 |
| `/admin-api/pms/kb/document-share/excel-preview` | token、可选 fileId、sheetIndex | 兼容匿名 Vue 页，每次校验分享和当前公开附件 |
| `/public-api/pms/kb/document-share/excel` | token、可选 fileId、sheetIndex | 同上，GET/HEAD 返回 UTF-8 HTML |

`sheetIndex=-1` 默认选择首个可见工作表，其他值为原始零基索引；隐藏及 very-hidden 工作表不可请求。没有 fileId 时预览独立文件，否则要求附件关联于授权文档。JSON 返回 `CommonResult<{sheets:[{index,name}],sheetIndex,rows:string[][],columnCount,truncated,notices:string[],merges:[{row,column,rowSpan,colSpan}]}>`。空表返回空行或零列。HTML 用安全文本构造表格和链接，继续使用禁止脚本的 CSP。所有预览响应设置 `Cache-Control: no-store`，不缓存解析结果，不记录接口请求/响应正文或令牌。

仅租户 1，沿用既有 ADMIN 和匿名分享租户处理。附件仍遵守“可阅读即可下载”的已确认例外，独立文件下载权限不变。已读取内容不能因分享关闭被收回。

## 文件来源与限制

附件通过 Infra 登记 fileId 读取。独立文件使用业务记录的 content 地址，经内部 `FileApi.getRegisteredFileByUrl` 精确匹配未删除文件登记，再通过其原始存储 configId/path 读取。地址不作为 HTTP 请求目标；Java 再次执行区分大小写的相等校验。旧域名、未登记或不完全匹配的历史地址不猜测、不迁移，提示不可预览并保留原有访问入口。

- 文件最多 10 MiB；每实例同时最多 2 次存储读取/解析，繁忙立即拒绝，可重试。
- 每表最多前 500 行、100 列及 200 万字符，超出明确提示截断；按需请求工作表，但每次读取和解析整个文件。
- OOXML 预检最多 2048 项、总解压 32 MiB、单项 16 MiB；大于 1 MiB 的项压缩比不得超过 100。保留 POI 自身校验，不修改共享全局设置。
- 数字/日期按文件格式展示；公式只读保存的缓存，不计算、不执行宏、不访问外链。缺少 OOXML 缓存结果显示 `[公式结果未保存]`。旧 XLS 必有缓存记录，无法判断是否过时；所有已保存缓存都可能过时。
- `1053130100..106` 分别为文件不可读取、类型不支持、大小/解压超限、加密、损坏、繁忙、工作表不可预览。现有权限/分享/附件错误码保持含义。公开 HTML 分享失效 410、文件不可用 404、繁忙 503、解析业务拒绝 422。

## 验证和发布

Java 专项：`PmsKnowledgeExcelParserTest`、`PmsKnowledgeExcelPreviewServiceTest`、`PmsKnowledgeExcelShareTest`、`FileRegisteredUrlTest`，并回归既有附件服务和公开页 Controller。真实生成 XLS/XLSX 样例覆盖格式、缓存、空表、隐藏表、截断、加密和压缩膨胀；MockMvc 验证公开 HTML/JSON/HEAD 和安全头。

Admin 定向类型检查：`node node_modules/vue-tsc/bin/vue-tsc.js --noEmit -p test/knowledge-excel.tsconfig.json`。使用 `test/knowledge-excel.vite.ts` 在专用端口 5329 启动测试页，执行 `python test/knowledge-excel-browser.py` 验证 1440/390 宽度、请求竞态、重试、附件以及 PDF/图片回归。合成接口不代表真实账号、文件存储或部署验收。

部署需同时发布后端和 Admin，无 SQL、权限分配或新依赖。确认公开代理允许新增 `/public-api/pms/kb/document-share/excel`。上线后用真实授权账号、仅预览协作者及匿名分享分别验证文件预览、关闭分享、跨文档附件拒绝及下载入口。回退前后端代码即可，不修改业务数据。

合并区域使用零基坐标，rowSpan/colSpan 为裁切后跨度；左上角保存显示内容，覆盖格不重复展示。合并范围用于补齐文件中未实体存储的空行/空列；超出 500 行/100 列或字符截断后的部分按可见边界裁切，继续显示截断提示。无 merges 字段的旧响应按普通格兼容。
