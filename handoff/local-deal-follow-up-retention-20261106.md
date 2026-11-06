# 成交后保留下次跟进任务

## Registration
- Environment 为 local，环境变量及 /etc/zsjos/agent-environment 未设置，使用默认值。
- Workstream 为 local-deal-follow-up-retention-20261106，Owner 为 Codex /root。
- Goal 为成交生效后保留下次跟进任务及时间，用户填写原因并二次确认后手动取消。
- 范围边界为现有首次跟进任务收尾、权限、其他生命周期规则。历史已取消任务不恢复。
- Branch 为 main，Worktree 为 D:\ZSJ-OS，Base commit 为 e23674d600e61ac87f3b6c8a13a8d81051d7b6a0。
- Ownership 为 SalesOrderServiceImpl.java、SalesOrderServiceImplTest.java、LeadDetailModal.tsx、docs/api/zsjos-lead-submission-dispatch.md、frontend/workbench/docs/follow-up-form.md 及本记录。
- Dependencies 为现有订单审批结果入口和手动取消接口。现有文档成交取消规则由本次用户明确要求覆盖。
- Verification 为聚焦 Maven 成交及取消测试、Workbench typecheck 和取消交互核验。
- Target branch / Integration order 为 None。
- 浏览器验证范围补充为 frontend/workbench/tmp/deal-follow-up-retention/，仅使用合成 API 响应验证现有组件。

## Delivery — 2026-11-06
- 成交审批生效保留待处理下次跟进提醒及 Lead、Opportunity 的下次跟进时间。首次跟进任务仍按原规则收尾。历史取消记录不恢复。
- 工作台取消按钮打开明确的二次确认弹窗，填写原因并点击“确认取消”才提交。API 与工作台表单文档已同步。
- Maven 聚焦 SalesOrderServiceImplTest、LeadNextFollowUpCancellationTest 共 94 项通过，失败及错误为 0，包含成交时间保留、提醒不自动取消和手动取消校验。
- Workbench npm run typecheck 及 git diff --check 通过。
- Ego-browser 合成 API 夹具验证通过，覆盖打开/返回零请求、空白原因禁用确认、失败重试、确认成功后刷新详情。浏览器脚本位于 C:\Users\EDY\ego\deal-follow-up-20261106，夹具位于登记的 tmp 目录。
- 真实账号、部署后接口及线上审批流程尚未验收。未执行数据库变更、服务操作、提交或发布。
- 既有其他工作流改动均保留，本工作流所有权在交付后释放。

## 文案修订 — 2026-11-06
- 复用上述登记及文件范围，仅重新领取 LeadDetailModal.tsx、直接相关 API 文档和本记录。
- 按用户指定将二次确认正文替换为“请确认该客资没有继续跟进的必要，再取消下次跟进任务”。验证采用精确文本检查和 scoped diff 检查。
- 精确文本检查及 git diff --check 通过。本次为纯文案修订，交付后释放所有权。
