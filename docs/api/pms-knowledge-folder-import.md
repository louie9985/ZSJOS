# 中世健知识库文件夹导入

## 使用方式

知识库列表的「从文件夹创建」会使用所选文件夹名称作为可修改的库名，沿用知识库简介、封面、公开/私有、管理员及普通成员设置。选择文件夹后先检查清单，再创建并导入。所选根文件夹本身不重复成为库内目录。

已有知识库首页或可编辑文件夹页面的「上传文件夹」将所选文件夹完整放到当前目录。唯一同名目录合并；同名文件、文件与目录名称冲突及含糊目标跳过，不覆盖。文件文档标题沿用旧入口规则去掉最后扩展名，因此同目录 `介绍.pdf` 与 `介绍.docx` 视为同名。未授权目标不暴露详细名称或元数据。

「我的导入记录」只显示本人任务。页面关闭后浏览器不会继续读取本地文件，回来选择原文件夹再继续。成功、跳过条目不再上传；未完成文件按路径、大小及 SHA-256 校验，缺失或变化的文件单独显示失败，新增文件不加入旧任务。恢复粒度是整个文件，不是分片。

暂停只停止派发新文件，在途请求可以完成。结束任务保留全部成功内容。空文件夹无法通过目录选择读取；无可导入文件时不创建空库。文件按原格式保存，ZIP/RAR 作为普通文件处理，不解压、不转换在线文档。

## API 与权限

前缀 `/admin-api/pms/kb/import`，使用现有 ADMIN 身份、租户上下文（实际租户 1）和 CommonResult。

| 接口 | 参数 / 结果 |
| --- | --- |
| GET /policy | 返回服务端文件类型、数量、大小、层级及并发策略 |
| POST /prepare | Request 清单，返回逐项 PENDING/SKIPPED 及原因；只读 |
| POST /create | 同一 Request，返回持久任务及条目；requestKey 幂等 |
| GET /page | pageNo/pageSize；仅本人任务汇总 |
| GET /get | id；本人任务及最多 1000 条结果 |
| GET /item-page | id/pageNo/pageSize；本人任务条目分页 |
| POST /upload | multipart taskId/itemId/file；返回任务结果 |
| POST /resume | taskId/files；核实原清单并标记可继续条目 |
| POST /finish | 查询参数 id；结束任务，不删除成功内容 |

Request 包含 mode=NEW/EXISTING、requestKey、rootName、files。NEW 仅带 library 基本信息（不能传 id/templateId），EXISTING 仅带 libraryId/folderId，根目录 folderId=0。每条 files 包含 relativePath（含所选根目录）、fileSize、lastModified、digest。不可导入的格式、空文件、超限文件可不计算 digest，服务端在预检中跳过。时间为浏览器毫秒值，仅辅助展示；内容摘要才是恢复依据。

查询接口需要 `pms:kb:library:query`；变更接口需要 `pms:kb:library:update`（现有文件夹及文件创建也是这个标识）。新建模式额外检查 `pms:kb:library:create`。所有写入持续检查知识库及目标内容权限，沿用已有成员和内容权限继承。不新增角色判断，不修改角色菜单授权。

任务查询不返回存储文件标识、内部租约或文件地址。任务与条目查询通过租户插件及任务创建人双重约束。

错误码 1045090000=清单/文件校验失败、1045090001=无权操作任务、1045090002=目标失效、1045090003=正在处理、1045090004=存储或登记失败、1045090005=任务已结束。已有知识库权限错误沿用原业务错误码。前端保留服务端错误，自动重试仅针对瞬时网络及 502/503/504，最多两次；权限及目标错误停止派发。

## 持久化、并发与限制

新增 `pms_knowledge_import_task` 和 `pms_knowledge_import_item`；任务的 owner/requestKey 和条目的 task/pathHash 分别在租户内唯一。任务状态保存 OPEN/FINISHED；完成状态及成功/跳过/失败数量以条目即时聚合为准，避免并发回写计数覆盖。

上传先取得条目租约，再在数据库事务外存储文件；存储标识持久化后才登记目录和文档。登记失败重试复用已存储文件。租约有效期 15 分钟，过期可接管；token 和递增版本使旧请求不能覆盖接管结果。关闭页面后若服务端仍在处理，等待在途请求结束或租约到期后再继续。数据库写入保持短事务，按知识库行锁串行化普通创建与批量导入登记，文件传输不持库锁。

文件存储和业务数据库不是分布式事务。进程恰好在存储返回后、文件标识持久化前退出时，可能留下未引用存储对象；不能声称原子回滚或自动清理全部孤立文件。不得通过批量删除知识库或文件目录“回滚”任务。

服务端配置前缀 `pms.knowledge-import`：默认单文件 100 MiB、单任务 2 GiB、1000 个文件、20 层路径，前端并发 3。可向下调整这些限制；上调超出现有接口硬上限会在配置校验中拒绝。默认格式复用当前知识库范围：doc/docx/xls/xlsx/ppt/pptx/txt/pdf/rar/zip/png/jpg/jpeg。无新 npm/Maven 依赖。

## 部署与验证

在当前 Core 与 PMS V002 前置条件下执行 `script/sql/mysql/migrations/pms/V003__knowledge_folder_import.sql`，再部署后端及 Admin 构建。Workbench 使用既有 Admin 嵌入页面，不需要增加 React 页面或路由。迁移只创建两个空表和 PMS V003 版本记录，不写 Core 版本或实际权限。

MySQL DDL 隐式提交，不能按整体事务回滚处理。迁移可重复执行，缺前置或结构错误不会记录新成功版本；发生部分建表时保留已有对象，检查结构并修复后重跑。现有 V001/V002 未改写。

受控验证命令：`python script/sql/mysql/verify/pms-folder-import.py`。仅创建保留的独立模拟库，检查首次、重复、部分失败恢复、故障无成功标记、中文 HEX、PMS V001→V003 链及目标 schema 一致性，不写业务库。

UI 夹具：Admin 目录运行 `pnpm exec vite --config test/pms-folder-import.vite.ts`，仓库根目录运行 `python frontend/admin/test/pms-folder-import-browser.py`。它使用真实组件、摘要与队列和模拟接口，不替代已登录 Admin / Workbench 的部署验收。

实际环境验收应覆盖两种入口、成员权限、100 MB 边界、存储访问、代理请求限制、断网及重启恢复。源码测试通过不代表业务库已迁移或共享后端已重启。
