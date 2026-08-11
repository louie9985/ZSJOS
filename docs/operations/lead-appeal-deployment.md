# 客资三级申诉发布说明

申诉流程定义文件为 `script/bpm/zsjos_lead_appeal_review.bpmn20.xml`，流程 Key 固定为 `zsjos_lead_appeal_review`。BPMN 必须由管理员在 BPM 管理页面受控创建模型、载入文件并发布，不启用全局自动部署。流程文件包含完整 BPMN DI 图形坐标，可在 Admin 设计器中预览和维护。

执行顺序：

1. 在受控环境先执行增量迁移 `script/sql/mysql/migrations/V015__lead_three_level_appeal.sql` 和 `V016__complete_lead_notify_templates.sql`。确认 V022 已集成且 V023、V024、V025 已按序执行后，再执行 `V026__lead_appeal_reviewer_snapshot.sql` 并运行 `script/sql/mysql/verify-bootstrap.sql`；缺少 V022 的环境不得执行 V024 或 V026。
2. 为每个租户配置恰好一名启用的 `boss` 角色用户；迁移只在角色编码缺失时创建角色，不自动分配账号。
3. 在 Admin 进入“工作流程 → 流程管理 → 流程模型”，新建 BPMN 模型：流程标识填写 `zsjos_lead_appeal_review`，流程名称填写“客资申诉复核”，设为不可见、发起范围为全员，并指定流程管理员。
4. 在“表单设计”选择“客资申诉流程关联信息”。该表单只读，只展示 `appealId`、`leadId`、`roundNo` 和 `reviewStage`；申诉仍由 ZSJOS 工作台提交，不从 Admin 通用流程入口手工发起。
5. 在“流程设计”点击“打开文件”，选择 `script/bpm/zsjos_lead_appeal_review.bpmn20.xml`。不要使用模型列表顶部只接受 JSON 模型包的“导入模型”。
6. 保存并发布，确认流程 Key、任务 Key `appealReview` 和多人或签（任一人通过或驳回即结束）配置正确。

迁移只增加申诉字段、索引、状态字典、菜单、授权、系统技术表单和审批人快照，不删除或重写业务数据。审批人按照提交时的客资负责人及其组织关系解析并冻结；第一轮从负责人所在部门开始逐级向上查找最近的启用主管账号，该账号还必须持有销售主管申诉审批权限，且不能是客资负责人本人。后续客资转交不改变已提交轮次。已存在申诉历史的环境不回填快照，只有快照为 `NULL` 的历史待办以 BPM 实际 assignee 为准；非空但无效或为空数组的快照会拒绝审批。已存在申诉历史的环境中，幂等键列允许历史空值，MySQL 唯一索引仍允许多条空值；后续请求必须携带非空幂等键。若流程定义缺失，申诉提交事务整体回滚并返回可识别错误，同时服务端记录 BPM 原始异常。回滚采用禁用菜单、技术表单和流程定义，保留申诉与业务事件审计数据，不通过删列恢复。

申诉无时限、无自动升级、最多三轮；第二和第三轮均由提交人手动重新提交。
