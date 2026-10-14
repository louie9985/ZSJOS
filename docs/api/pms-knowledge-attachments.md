# 知识库文档附件

在线富文本文档在编辑正文下方添加附件，阅读页位于正文之后、互动之前。最多 10 个，每个非空且不超过 100 MB；支持 PNG/JPG/JPEG/GIF/WebP、Office、PDF、ZIP，以及剪贴板截图。独立文件条目和模板不增加附件。图片/PDF 使用现有预览，Office/ZIP 下载后查看。

## 权限与保存

用户明确确认：有文档阅读权限即可下载文档附件，包括仅预览协作者；此例外不改变独立文件条目的下载权限。上传、修改仍要求 pms:kb:library:update 和文档可写；读取要求 pms:kb:library:query 和文档可读。回收站通过现有回收站授权入口访问。

POST /admin-api/pms/kb/document/attachment/upload 接收 documentId 和 file；GET /attachment/url 接收 documentId/fileId；GET /attachment/recycle-url 额外接收 recordId。后两者相对于 /admin-api/pms/kb/document。访问时重新授权后签发 600 秒地址。

文档创建/更新增加 attachmentFileIds；省略保留原关联，空数组清空。现有 UI 先创建标题，再编辑正文和附件；上传绑定已存在的文档。正文和附件关联事务保存。服务端从 Infra 获取真实文件元数据，并检查当前用户和文档上传目录；其他文档文件不得引用。详情返回 attachments（fileId/fileName/mimeType/fileSize）。

PMS pms_knowledge_attachment 保存关联、元数据快照、顺序和 shared 标记。公开勾选存放在关联上，无需更改原分享表；关联移除后再次添加默认不公开。文档移动保留关联并使用最新文档权限；回收保留，恢复重新可用；永久删除清理关联，不删除 Infra 文件。未保存上传对象清理不在本次范围。

## 分享

开启分享请求增加 attachmentFileIds，默认空；PUT /admin-api/pms/kb/document-share/settings 同时更新成员与公开附件。原 update-member-list 保留，不改变附件选择。公开 HTML 和旧 Vue 分享路由仅展示选中附件，新附件不自动公开。GET /public-api/pms/kb/document-share/attachment?token=...&fileId=... 每次校验令牌、文档状态和当前公开关联。关闭分享后停止签发，已签发地址至多保留原有效期。

## 交付与验证

依次执行 PMS V001→V002→V003→V004，再部署后端和 Admin；V004 只新增空附件表，不回填历史、不修改角色授权。DDL 非事务回滚，失败检查真实结构后修复重放，回退代码保留数据。使用 script/sql/mysql/verify/pms-attachments.py 验证首次、重复、前置失败、部分恢复、中文 HEX、PMS 完整链和目标 schema 比较。

Java 专项覆盖附件授权、归属、关联生命周期和公开入口；frontend/admin/test/pms-attachments-browser.py 使用生产组件和合成 HTTP 验证桌面/手机上传、失败重试、保存回显和分享选择。合成验证不代表已部署或真实存储验收。
