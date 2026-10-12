# 需求详情内审批

## Follow-up delivery — business content presentation
- Both feedback record and approval task details now use FeedbackBusinessContent: labels above values, long content/attachments full width, snapshot values retained. Removed duplicate feedback/approval metadata from task body and redundant summary from requirement body; existing business replies/results and submitter actions retained when applicable. Right-side workflow remains unchanged.
- Attachments use existing AttachmentCard, without modifying its shared implementation. Existing authorized response supplies URL/MIME/name; missing URLs produce common attachment errors.
- Verification: typecheck passed; AttachmentCard/feedback API/route tests 16/16 passed; full feedback-inline-approval-browser.py passed desktop/mobile, common attachment preview, task decisions on synthetic data, history/readonly/stale/error and urge regression. Desktop/mobile screenshots inspected at existing temp output paths. Scoped diff check passed. No live business decisions or external-state changes.
- Documentation updated; follow-up ownership released.

## Follow-up registration — 2026-10-12
- Same local/main execution context and base. User requests business-only content, field presentation refinement and common attachment cards. Reclaim prior owned feedback UI/docs/tests; add src/components/FeedbackBusinessContent.tsx. Shared AttachmentCard and generic BusinessContentCard remain unchanged. Scope both feedback detail and approval task detail. Verify typecheck, focused tests and desktop/mobile browser including common attachment preview. No live decisions.

## Registration — 2026-10-12
- Environment: local (ZSJOS_AGENT_ENV unset; /etc/zsjos/agent-environment absent).
- Workstream: local-feedback-inline-approval-20261012; owner: current Codex chat.
- Goal:需求详情左侧展示提交快照与附件，右侧展示历史轮次、流程和本人当前待办审批操作；桌面与手机适配。
- Non-goals: 改变审批链、权限授权、历史数据、Admin 页面或共享 BPM 组件行为；不执行真实审批决定。
- Branch: main; worktree: D:\ZSJ-OS; base: c82c8605d86aef5c8f4cd328600bc7c3efc2bf90.
- Ownership: frontend/workbench/src/pages/FeedbackPage.tsx; src/components/FeedbackApprovalPanel.tsx; new src/components/FeedbackApprovalActions.tsx; src/styles/pages/feedback.css; test/feedback-approval.tsx; test/feedback-inline-approval-browser.py; docs/api/feedback-management.md; this handoff.
- Dependencies: existing feedback round authorization/progress and BPM get-todo/decision APIs; reuse BpmApprovalActions without new dependencies. Prior feedback work released ownership; preserve unrelated working changes.
- Verification: Workbench focused tests/typecheck, synthetic browser approve/reject/round/read-only/error/stale-task cases at desktop/mobile widths, live local account read-only detail/form inspection. No database or role writes, commits, branches or service changes.
- Integration target/order: None.

## Registration update
- 用户明确入口为审批中心/待办任务“详情”。追加 ownership: frontend/workbench/src/components/bpm/BpmApprovalDetail.tsx; src/pages/BpmApprovalCenterPage.tsx; test/feedback-task-detail.tsx; test/feedback-task-detail.html。仅反馈业务类型改为左右布局，复用服务端任务所属轮次业务卡、流程和操作，不引入新的反馈权限要求；其他流程保持原页面。

## Delivery — 2026-10-12
- 审批中心待办“详细”直接呈现宽需求审批详情：左侧服务端任务所属轮次业务卡/图片附件，右侧审批流程、流转记录、评论和现有 BPM 操作；不再需要跳转业务详情。已办只读；内容读取失败、流程读取失败或 todoTask 与选中任务不同均不展示决策动作。切换任务按任务 ID 重建详情，避免串用旧任务状态。
- 反馈页详情同步主侧 9:3 布局，轮次快照在左、流程及操作在右；较窄容器上下堆叠。复用 get-todo 实时校验与 BpmApprovalActions，累计 query/update 权限，不因审批人只读业务内容而隐藏其当前任务的审批；历史轮次、其他人任务、跨用户只读范围不开放决策。原催办边界与重试幂等保留。
- Verification: 最终 npm run typecheck 通过；定向 Vitest 23/24 通过，唯一失败为未修改的内容生产 guard 仍查找 businessAdvance={advanceAction}，当前源码已使用历史轮次条件表达式；未调整无关断言。
- Real Chrome synthetic verification: frontend/workbench/test/feedback-inline-approval-browser.py 全部通过。覆盖实际审批中心表格“详细”/移动卡片入口、1440/390 分栏与堆叠、附件预览、通过/拒绝请求及准确任务 ID、无处理权限、返回其他 todoTask、业务读取失败重试、已办详情只读；反馈页历史轮次、任务读取失败重试、非本人、无授权、催办失败原幂等键重试/冷却、缺流程及免审批。
- 测试页即时切换已办标签出现原列表清空，未更改原标签切换逻辑；已办详情验收改为直接进入已办路由，不声称本次覆盖或修复标签切换。
- Live local tenant 1: 使用用户指定主管账号，从实际 /bpm/task/todo 表格点击“详细”，确认 REQ-20261012-0001 可见、主侧分栏正常、审批按钮启用且可打开意见表单；关闭表单，没有提交真实审批，也未变更权限、数据库、共享服务。
- Screenshots (synthetic only): C:\Users\EDY\AppData\Local\Temp\zsjos-feedback-inline-approval\task-1440.png and task-390.png; inspected desktop and mobile images. API contract documentation updated; scoped diff checks passed. No build required for unchanged production routes/dependencies/assets/build configuration; existing Vite served runtime verification.
- Delivery: current main/worktree retained; no commit/push. Ownership released.
