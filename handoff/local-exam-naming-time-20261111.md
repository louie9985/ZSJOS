# 文件命名增加提交时间

- 环境 local（环境变量及环境文件未指定），工作流 local-exam-naming-time-20261111，owner Codex。
- 分支 main，工作树 D:\ZSJ-OS，基线 7a7cf5e14bef1ff193de5c4cc39af8a5255a7d5c。
- 目标为导出命名增加 submittedAt。所有权为 ExamCollectionExportNaming.java、ExamCollectionExportService.java、ExamCollectionExportTest.java、ExamExportDrawer.tsx、docs/api/exam-material-collection.md 和本记录。
- 范围为可选命名变量及原始提交/当前档案时间解析，无数据库或权限变更。依赖现有导出流程。集成目标及顺序 None。
- 验证为专项 Maven 测试、Workbench typecheck、浏览器及 diff 检查。

## 交付

- 命名选项新增提交时间 submittedAt，格式 yyyyMMdd_HHmmss，支持默认规则与材料覆盖规则。当前档案与完整信息表采用同一最近提交来源，缺失显示未记录。
- 专项 Maven 测试 10 项通过，Workbench typecheck 通过，git diff --check 通过。浏览器使用已有合成 HTTP fixture，验证选项显示、选中及保存请求包含 submittedAt。
- 新增测试初次缺少状态，补全 ARCHIVED 后重跑通过。真实账号下载、窄屏视觉及部署验收未执行。Ego Lite 提示有更新，本次使用现有版本完成。
- 所有权释放。

## 格式调整登记

- 沿用现有 local/main 工作流、基线及所有权，owner Codex。按用户要求改为 yyyy_MM_dd HH_mm_ss，范围限服务格式、前端示例、测试、API 文档和本记录。验证为专项测试、类型检查及浏览器示例检查。

## 格式调整交付

- 提交时间按用户示例改为 yyyy_MM_dd HH_mm_ss，示例 2026_11_08 14_51_55。前端、服务、原始提交/当前档案及覆盖规则测试、API 文档同步。
- 专项 Maven 测试通过（10 项），Workbench typecheck 通过，scoped diff 检查通过。浏览器访问本地 fixture 超时，本次页面验证未完成。所有权释放。
