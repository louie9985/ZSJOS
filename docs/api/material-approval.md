# 素材审批

部门主管从 Workbench `/zsjos/material-library/approvals` 查看爆款账号/内容的本人待办及已办。审批人在 BPM SIMPLE 中维护，角色菜单授权不替代 BPM 任务归属。素材浏览不扩大为管理权限。

## API

均使用 ADMIN 鉴权及当前租户。根路径 `/admin-api/zsjos/material-approval`。
- GET `/types`：返回实际素材类型的 code/name，仅支持已接入的两类爆款流程。
- GET `/page`：typeCode、done（默认 false）、pageNo、pageSize；分页从 BPM 本人待办或已办读取。
- GET `/get`：versionId、taskId、done；返回本次任务和对应提交版本、字段/字典标签快照、附件。
- POST `/approve`、`/reject`：versionId、taskId、reason（必填且至多 1000 字）。

页面与读接口要求 `zsjos:material-approval:query`；决定另要求 `:approve` 或 `:reject`。对象级校验要求本人的 BPM 任务、业务键 `material-version:<versionId>`、匹配的爆款流程类型和持久化审批轮次。决定按素材、版本顺序加锁，校验当前待审批版本后调用 BPM API；原有结果监听器推进素材状态。无法替别人审批，重复决定由当前任务检查拒绝。没有管理角色绕过任务归属的例外。

## 审批流展示

`/get` 返回的 `task` 直接透传 BPM 任务对象，含 `processInstanceId`。审核详情在字段快照与通过/驳回之间内嵌只读流程面板，展示节点次序、当前节点、处理人与审批意见；素材库详情在在途版本（`IN_APPROVAL`）展示同款面板，供编导查看自己的件走到哪一步。

面板取数走 BPM 的 `GET /bpm/process-instance/get-approval-detail`（节点顺序与状态由后端 `activityNodes` 给出），因此**额外要求 `bpm:process-instance:query`**，展示流程评论另需 `bpm:task:query`。这两个权限不在 `zsjos:material-approval:*` 范围内：审核角色必须由管理员在 System 中单独授予，否则面板不渲染。前端按返回的权限列表门控，缺权限时不发这个必然被拒的请求；后端仍是唯一强制点，前端隐藏不等于授权。租户 1 的审核人与编导经既有角色已具备该权限，换环境或新租户需重新确认。

面板为只读：转办、委派、加签、抄送、退回以及通过/驳回全部关闭，结论一律走 `zsjos/material-approval/{approve,reject}`，保证审批轮次由业务接口写入。Vue Admin 的素材详情抽屉使用同一份 `activityNodes`，节点数据一致；它在取数失败时显示可重试的错误，不表现为"暂无审批记录"。

已办详情显示当前提交快照及本次任务意见/处理时间。历史轮次如果版本已重新编辑或提交，接口返回 1900020042，不把当前内容冒充旧快照。未重提的已有在途任务无需重新发起。多级审批由 BPM 推进；节点若配置超出 BPM 公共决定 API 支持的特殊输入，保留其失败信息，不自动通过或跳过。

统一 BPM 业务跳转支持两类爆款流程，跳到新页面并携带 taskId/versionId/typeCode/done。Vue Admin 保持原 BPM 表单；新页面为员工端专属，新增 API 不改变原素材查询契约。

## 初始化与部署

执行 V233（前置 V194 素材目录及版本登记，顺序在 V232 之后）。仅添加三项菜单元数据及版本记录，不自动给所有角色授权，不修改素材、流程和审批人。重复执行无新增重复行。通过 System 角色菜单接口给明确选定的审批角色授权页面和两个按钮，并保留原授权；租户套餐须包含这三个菜单。回滚先撤销授权/停用入口，保留业务和 BPM 历史。

后端重新启动后才提供新接口。验证应覆盖本人/非本人、待办/已办、历史快照失效、重复决定、通过/驳回及原监听器结果回写；不要自动审批真实业务任务来做冒烟验证。
