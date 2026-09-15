# 爆款审核表单：标准设计器录入

在 BPM 表单管理中新建表单，打开表单设计器的 JSON 录入窗口：

| 表单名称 | 渲染规则 JSON | 表单配置 JSON |
| --- | --- | --- |
| 爆款账号拆解审核表单 | viral-account-rules.json | viral-account-options.json |
| 爆款内容拆解审核表单 | viral-content-rules.json | viral-content-options.json |

将对应文件的完整内容分别粘贴到“渲染规则”和“表单配置”，确认后保存表单，状态选启用。渲染规则是对象数组，表单配置是对象；不是模型 JSON，也不是 API 的字符串数组 `fields`，无需再次加引号或转义。

在对应流程模型的表单设置选择“流程表单”，选择刚保存的表单并保存模型。已有同 Key 模型直接编辑，不重复导入或删除。通过界面选择表单会关联当前租户的 formId，无需手工修改仓库模型。

五个字段为 materialNo、materialVersionNo、materialTitle、materialSummary、materialContent，全部只读，值由素材提交服务传入。设计器预览为空属于正常情况；此文件不包含测试业务数据。materialContent 是已存版本的文本快照，完整动态字段、图片与附件仍需在素材详情查看。审批意见使用 BPM 任务意见，不作为素材可编辑字段。

此方式不使用 importForm 扩展。若需导入现有模型 JSON，应使用移除 importForm 的本地副本，再在模型界面选择已创建表单，避免支持扩展的新后端重复创建表单。角色必须在 BPM 审核节点选择当前租户真实有效的角色，原模型的角色参数 0 不是可用审批人。

仅生成文件不代表已创建、关联或发布租户表单。运行后端还需具备素材快照变量填充实现，才能在实际审批中显示标题、摘要和内容。
