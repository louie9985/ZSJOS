# 自拓录单跟进时间快捷填写

- Environment local（ZSJOS_AGENT_ENV 未设置，环境标识文件不存在时使用 local）
- Workstream local-self-sourced-follow-up-shortcuts-20261111
- Goal 销售、学习规划师自拓录单复用现有跟进时间快捷项。
- Scope frontend/workbench/src/pages/LeadSubmissionPage.tsx、frontend/workbench/docs/follow-up-form.md、本记录。
- Owner Codex
- Branch main
- Worktree D:\ZSJ-OS
- Base 7a7cf5e14bef1ff193de5c4cc39af8a5255a7d5c
- Dependencies 现有 leadFollowUp 时间计算和 follow-up-time-shortcuts 样式。
- Verification Workbench typecheck、现有时间计算测试、浏览器交互检查。
- Target branch / integration order None

## 交付

- 已在两类自拓共用录单页加入既有九项时间快捷按钮，复用时间计算与网格样式。手工修改、清空或切换提供方后取消高亮，提交期间禁用快捷按钮。
- npm run typecheck 通过；leadFollowUp.test.ts 12 项、lead-submission-steps.guard.test.ts 7 项通过；scoped git diff --check 通过。
- 浏览器夹具使用本地 5193，Ego space 82。导航超时后检查仍为空 root，未完成实际渲染和点击验证。真实账号提交与持久化未验证。
- 文档中并存其他工作流的已成交必填与取消入口调整，保留其修改。本轮仅维护自拓自动首跟段落。
- 本轮文件写入完成，释放所有权。代码未提交或部署。

