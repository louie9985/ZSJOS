# 审批中心同页审批

## 工作流登记
- 时间：2026-10-15（北京时间）。环境：local（环境变量及环境文件均未指定，采用约定默认值）。
- ID：local-approval-layout-20261015；owner：当前 Codex 会话。
- 分支：main；工作树：D:/ZSJ-OS；base：315efd729f2e049fc2639194567d86077ae91184。
- 目标：实施用户确认的方案三，业务内容在左，流程、记录、评论连续展示，右栏内联审批及草稿保护。
- 非目标：Admin、后端、数据库、权限配置、业务审批规则、部署及真实审批。
- 所有权：Workbench BpmApprovalCenterPage、components/bpm 下本任务新增组件/辅助函数及测试、BpmApprovalDetail、BpmApprovalActions、审批中心专用 CSS；本任务浏览器 fixtures；审批中心说明及菜单覆盖文档；本记录。不修改共享 api.ts、BpmProcessPanel 或其他业务页面。
- 依赖：既有 BPM/API、Ant Design、签名与附件设施；保留其他工作流未提交修改。
- 验证：定向 Vitest、typecheck、真实组件隔离浏览器 fixture（桌面/移动/抽屉/主题/错误及权限）。
- 目标分支及集成顺序：None。

## 交付（2026-10-15）
- 通用及需求审批共用无页签侧栏：业务材料在左，节点、按任务 ID 归并的流转记录、评论在右；加签按父任务关联，未覆盖任务继续展示。保留需求任务所属轮次业务卡。
- 内联通过／拒绝、意见和签名入口，既有复杂动作弹窗及默认调用模式保留；按服务端按钮、任务匹配与权限控制。独立评论、已办只读结果、业务卡/记录/评论失败提示与重试。
- 9:3 分栏及详情容器 1100px 窄屏切换，单一详情挂载；页面内存保存草稿，取消切换/关闭/返回/链接离开时保留输入，刷新及跨断点重挂载保留意见、签名和评论。提交失败保留输入，终结或移交动作成功保留当前结果并提供下一条。
- 验证：npm run typecheck 通过；定向 Vitest 23 项中 22 通过。唯一失败为已有内容审核 guard 要求 businessAdvance={advanceAction}，ContentReviewBatchPage 与 HEAD 无差异，HEAD 中亦不存在该调用；本次不修复无关页面。
- 浏览器：test/approval-center-layout-browser.py 使用既有本地 Vite 5174 和合成 adapter 全部通过；1920/1440/1024/768/390、需求审批、表格抽屉、无权限、任务不匹配、深色主题和长内容、签名必需、确认取消、单次提交、拒绝失败保留与重试、关闭/返回/链接保护、评论提交、默认动作弹窗、移动确认层级、跨断点草稿保留、旧请求隔离及各资源重试。截图位于 C:/Users/EDY/AppData/Local/Temp/zsjos-approval-layout/。
- scoped git diff --check 通过；仅保留本任务源码、测试及说明，移除本任务编辑脚本和未使用组件；没有修改共享 api.ts、BpmProcessPanel、Admin 或后端。
- 未验证：登录真实账号下的接口结果、真实签名上传和所有复杂动作的端到端写入；未执行真实审批、部署、服务操作、提交或推送。浏览器证据为实际 React 组件隔离 fixture，不是目标环境验收。
- 完成并释放本记录所列文件所有权；保留其他工作流修改。
