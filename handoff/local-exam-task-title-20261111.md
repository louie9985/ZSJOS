# 考试收集任务标题展示

## 登记 2026-11-11

- 工作流 ID 为 local-exam-task-title-20261111，owner 为 Codex。
- 环境为 local，ZSJOS_AGENT_ENV 与环境文件均未提供值，采用默认值。
- 目标为任务详情顶部仅用任务名称标识任务，移除正文与描述展示。
- 范围为 frontend/workbench/src/pages/ExamCollectionPage.tsx、docs/api/exam-material-collection.md 和本记录。
- 分支为 main，工作树为 D:\ZSJ-OS，基点为 7a7cf5e14bef1ff193de5c4cc39af8a5255a7d5c。
- 目标分支、集成顺序和依赖为 None。
- 保留现有未提交改动。公开填写表单、创建预览、接口与数据库不在修改范围内。
- 验证计划为局部差异检查、Workbench typecheck 和可用浏览器环境检查。

## 交付 2026-11-11

- 已移除任务详情的正文与描述渲染及无用导入，任务名称继续作为标题。
- 已同步业务契约。保留原有任务统计与操作入口。
- npm run typecheck 通过，局部 git diff --check 通过。
- 浏览器仅获得新建空白页，未发现可验证当前源码的本地 Workbench 开发服务，实际页面视觉效果待验证。
- HEAD 与登记相同，提交、部署和集成为 None。文件写入所有权释放。
