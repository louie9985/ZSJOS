# 已关闭客资的展示与历史核查

工作台客资管理的收件箱标签、表格有效性状态列及详情概览，在服务端明确返回 `status=closed` 时显示“已关闭”。详情该项标题为“客资状态”，使用既有灰色状态样式。其他客资仍显示服务端有效性判定结果。

这是展示层兼容：旧版后端 `qualificationStatus` 对关闭记录仍可能返回 `pending`。此调整不改接口字段、筛选配置、权限、可用动作或数据库状态；不依据负责人、时间或其他字段推断是否关闭。

验证入口：`npm test -- src/services/leadManagement.test.ts src/components/LeadDetailOverview.lifecycle.test.ts`、`npm run typecheck`；已有 Vite 5174 服务下执行 `python test/closed-lead-status-browser.py`，使用合成响应检查桌面/移动端收件箱、表格与概览。夹具不访问真实业务数据。

## 历史恢复边界

2026-10-12 本地 `ruoyi-vue-pro` 租户 1 的 102 条未删除已关闭客资，通过明确的 `legacy-parttimecrm-*` / `legacy-parttimecrm3-*` 导入标记全部匹配 `ptml.leads_lead`。旧记录均已关闭、未分配且负责人为空，未发现旧分配日志。

旧 `leads_status_event` 保留 103 条事件，覆盖全部 102 条客资；全部有时间和原因，28 条有操作人及姓名快照，未记录附件。恢复需先匹配现有业务事件和分配历史，保留原时间、原因和状态转换，避免重复；身份必须通过明确的历史映射核实，不凭同号或同名补人。缺失的分配、接单和跟进事实不能制造。

按现有 `related_object_refs.legacySourceTable/legacySourceId` 比对，9 条关闭事件已有来源关联且原因与旧记录一致；其余 94 条事件涉及 93 条客资，属于待恢复候选。新库另有 101 条 `action_type=other`、原因为 `legacy assignment history baseline` 的记录，双方负责人均为空，是导入基线而非旧系统实际分配。

本任务仅只读核查历史，不执行数据恢复。服务器当前数据及恢复写入需在明确目标后另行预检；上述缺口基于本地快照，不能直接作为服务器写入清单。
