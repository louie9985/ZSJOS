# 素材审批

部门主管从 Workbench `/zsjos/material-library/approvals` 查看爆款账号/内容的本人待办及已办。审批人在 BPM SIMPLE 中维护，角色菜单授权不替代 BPM 任务归属。素材浏览不扩大为管理权限。

## API

均使用 ADMIN 鉴权及当前租户。根路径 `/admin-api/zsjos/material-approval`。
- GET `/types`：返回实际素材类型的 code/name，仅支持已接入的两类爆款流程。
- GET `/page`：typeCode、done（默认 false）、pageNo、pageSize；分页从 BPM 本人待办或已办读取。
- GET `/get`：versionId、taskId、done；返回本次任务和对应提交版本、字段/字典标签快照、附件。
- POST `/approve`、`/reject`：versionId、taskId、reason（必填且至多 1000 字）。

页面与读接口要求 `zsjos:material-approval:query`；决定另要求 `:approve` 或 `:reject`。对象级校验要求本人的 BPM 任务、业务键 `material-version:<versionId>`、匹配的爆款流程类型和持久化审批轮次。决定按素材、版本顺序加锁，校验当前待审批版本后调用 BPM API；原有结果监听器推进素材状态。无法替别人审批，重复决定由当前任务检查拒绝。没有管理角色绕过任务归属的例外。

已办详情显示当前提交快照及本次任务意见/处理时间。历史轮次如果版本已重新编辑或提交，接口返回 1900020042，不把当前内容冒充旧快照。未重提的已有在途任务无需重新发起。多级审批由 BPM 推进；节点若配置超出 BPM 公共决定 API 支持的特殊输入，保留其失败信息，不自动通过或跳过。

统一 BPM 业务跳转支持两类爆款流程，跳到新页面并携带 taskId/versionId/typeCode/done。Vue Admin 保持原 BPM 表单；新页面为员工端专属，新增 API 不改变原素材查询契约。

## 初始化与部署

执行 V233（前置 V194 素材目录及版本登记，顺序在 V232 之后）。仅添加三项菜单元数据及版本记录，不自动给所有角色授权，不修改素材、流程和审批人。重复执行无新增重复行。通过 System 角色菜单接口给明确选定的审批角色授权页面和两个按钮，并保留原授权；租户套餐须包含这三个菜单。回滚先撤销授权/停用入口，保留业务和 BPM 历史。

后端重新启动后才提供新接口。验证应覆盖本人/非本人、待办/已办、历史快照失效、重复决定、通过/驳回及原监听器结果回写；不要自动审批真实业务任务来做冒烟验证。
