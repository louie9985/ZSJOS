# 产检假实际出勤修正

- 环境：local（环境变量及环境文件均未设置，使用默认值）
- 工作流：prenatal-attendance-20261030；owner：Codex
- 目标：用户确认产检假计入实际出勤，保留独立假别。
- 非目标：其他假别、数据库、历史报表批量重算、部署。
- 分支：main；工作树：D:\ZSJ-OS；base：208abf277065706cbee45164113903eded9e6303
- 所有权：frontend/workbench/src/features/zsjkqt/core/attendanceConfig.ts、normalizationAudit.test.ts；docs/api/zsjkqt.md；docs/testing/zsjkqt/business-audit-20261026.md；本记录。
- 依赖：用户本轮口径确认；目标分支/集成顺序：None。
- 验证：考勤通 core 测试、TypeScript 检查、限定 diff 检查。

## 交付
- 已纳入产检假出勤状态，并同步 API 契约和审计更新。
- 考勤核心 13 个测试文件、193 个用例全部通过；npm run typecheck 通过；git diff --check 通过。
- 未部署、未批量重算历史草稿；纯计算规则变更，无布局或交互变更。真实账号页面/导出尚未核验。
- 所有权已释放。
