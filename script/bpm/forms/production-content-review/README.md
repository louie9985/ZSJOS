# 生产内容批审流程表单配置

在 BPM 动态表单设计器中新建“生产内容审核批次表单”：

- 将 `production-content-rules.json` 粘贴到渲染规则；
- 将 `production-content-options.json` 粘贴到表单配置；
- 所有字段保持只读；
- 保存并启用后，在 `zsjos_production_content_review` 模型中选择该表单。

字段来自生产内容批审启动变量。`accountId`、`operatorUserId`、`directorUserId` 是内部 ID，仅用于 BPM 表单内部定位，不得作为用户可见业务编号。批次编号使用 `contentReviewBatchNo`。逐条内容结论和审批意见由 ZSJOS 批审界面与 BPM 任务意见维护，不在表单中伪造。
