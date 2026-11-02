# 报名资料收集状态样式

- 环境：local（环境变量及环境文件未设置，按规则回退）。
- 工作流：local-collection-status-20261102；负责人：Codex。
- 分支：main；工作树：D:\ZSJ-OS；基线：0ecc74d4d13bcfbc0361ecdbc2bbd73327359425。
- 目标：已有档案状态统一使用带底色标签，待处理与已取消区分颜色。
- 非目标：业务状态、权限、接口及数据库修改。
- 所有权：frontend/workbench/src/pages/StudentInfoTasksPage.tsx、本记录。
- 依赖：现有 Ant Design Tag；目标分支/集成顺序：None。
- 验证：typecheck、局部差异检查及可用浏览器检查。

## 交付
- 表格、收件箱及详情统一使用 Ant Design 带底色标签：无已有资料 blue、可直接复用 green、资料不完整 orange；待处理 gold、已取消 default。保留文字说明。
- `npm --prefix frontend/workbench run typecheck` 通过，内容及 diff 空白检查通过。
- 浏览器访问 5174 的目标路由后未进入资料收集页，当前会话回到首页；尚未验证目标页实际视觉及移动端效果。
- 未提交、未部署；释放本工作流文件所有权。
