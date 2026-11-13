# 考试资料完整信息表

- 工作流 local-exam-export-table-20261111，环境 local，owner Codex。
- 分支 main，工作树 D:\ZSJ-OS，基线 7a7cf5e14bef1ff193de5c4cc39af8a5255a7d5c。
- 目标为材料 ZIP 增加动态完整信息 Excel，范围沿用现有筛选，缺失元数据明确标记。
- 所有权范围为 ExamCollectionExportService.java、ExamCollectionExportTest.java、docs/api/exam-material-collection.md 和本记录。
- 边界为现有导出服务，不涉及数据库、前端、权限修改、提交或部署。依赖现有 Excel 库。
- 集成目标和顺序 None。验证为专项导出测试、工作簿回读及 diff 检查。

## 交付

- ZIP 增加考试资料完整信息表.xlsx，动态字段来自任务冻结配置，附件单元格对应实际 ZIP 相对路径。
- 当前有效资料与原始提交均生成信息行，无附件人员保留。时间与编号明确采用提交记录，耗时、实名提交者无来源时标注未记录。
- 指纹增加字段和行内容，表格变化需要重新预览。
- 验证命令 mvn -f backend/pom.xml -pl yudao-module-zsjos -am -Dtest=ExamCollectionExportTest -Dsurefire.failIfNoSpecifiedTests=false test -q 成功。9 tests，0 failures，0 errors，0 skipped。覆盖 ZIP 字节、Excel 回读、中文、附件路径、纯信息表和长身份证号文本保存。
- scoped diff 检查通过。真实 HTTP / 页面下载与部署验收未执行。所有权释放。

## 注册更新 — 导出命名

- 沿用 local/main 上述基线与 owner。按用户要求调整 ZIP 为任务名称加北京时间导出时间 yyyyMMddHHmm，Excel 为 (任务名称)表格视图.xlsx。
- 所有权新增 ExamCollectionExportController.java、frontend/workbench/src/services/examCollection.ts、对应命名回归测试。保留现有未提交修改。
- 验证为导出专项测试、响应头与前端下载文件名回归、Workbench typecheck。集成 None。

## 命名交付

- ZIP 下载名称为任务名称(北京时间导出分钟).zip，Excel 为 (任务名称)表格视图.xlsx。文件系统禁用字符及超长任务名沿用现有安全处理。
- Controller 输出 UTF-8 filename*，工作台从 Content-Disposition 解码名称，任务标题加入指纹。
- 后端 ExamCollectionExportTest 11 项通过，包含 ZIP 名称、实际 Excel entry 回读及既有提交时间命名回归。前端 examCollection.export.test.ts 1 项通过，验证服务端中文名称传至下载 anchor。npm run typecheck 通过，scoped diff 检查通过。
- 时间后缀按导出时北京时间解释。真实浏览器下载和部署环境验收未执行。所有权释放。

## 注册更新 — 表格图片与文档

- 沿用 local/main 上述上下文和 owner，仅修改 ExamCollectionExportService.java、ExamCollectionExportTest.java、API 文档及本记录。
- 用户明确要求表格仅含收集字段，移除管理元数据。图片嵌入 Excel，文档采用指向同包文件的相对引用，按完整 ZIP 解压后打开。
- 使用现有 POI / ImageIO，不增加依赖。验证工作簿图片、超链接、字段范围、多附件及既有导出回归。

## 表格图片与文档交付

- Excel 列仅来自任务收集字段及本次材料筛选，管理元数据已移除。保留已确认 ZIP 和 Excel 命名。
- 图片通过既有 ImageIO 解码并生成内嵌 PNG 缩略图，按比例限制在单元格内，原附件保持 ZIP 路径。文档单元格显示导出文件名并带相对文件超链接，完整解压后使用。
- 同字段多个附件展开为相邻编号列，保证每个文档都有独立可点击引用；冻结首行及文本格式身份证号保留。
- 验证命令沿用 mvn 专项入口，ExamCollectionExportTest 共 12 项通过，0 failures / errors / skipped，覆盖图片数据、锚点行列、仅收集字段、PDF 引用对应 ZIP entry、多附件、长身份证号、既有权限/指纹/命名回归。scoped diff 检查通过。
- Excel/WPS 实际视觉及点击验收、真实 HTTP 与部署验收尚未执行。相对文档链接的使用方式为完整解压 ZIP。所有权释放。
