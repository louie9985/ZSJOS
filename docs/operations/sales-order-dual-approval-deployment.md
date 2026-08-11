# 成交订单双中心会签部署

流程定义文件为 `script/bpm/zsjos_sales_order_dual_approval.bpmn20.xml`，流程 Key 固定为 `zsjos_sales_order_dual_approval`。管理员必须在 BPM 管理页面受控创建模型、载入文件、发布并启用，不使用全局自动部署。流程文件包含完整 BPMN DI 图形坐标，可在 Admin 设计器中预览和维护。

发布前按顺序完成：

1. 先集成并执行 V021、V022，再依次执行 `V023__sales_order_dual_approval.sql`、`V024__zsjos_bpm_readonly_forms.sql` 和 `V025__sales_order_workbench_views.sql`。缺少 V022 的环境不得继续执行或发布。
2. 确认 `zsjos_order_approval_config` 的报名履约与财务根部门 ID 有效，且两个部门或子部门均至少有一名启用用户。
3. 在 Admin 进入“工作流程 → 流程管理 → 流程模型”，新建 BPMN 模型：流程标识填写 `zsjos_sales_order_dual_approval`，流程名称填写“成交订单双中心会签”，设为不可见、发起范围为全员，并指定流程管理员。
4. 在“表单设计”选择“成交会签流程关联信息”。该表单只读，只展示 `orderId`、`leadId` 和 `roundNo`；订单仍由 ZSJOS 工作台提交，不从 Admin 通用流程入口手工发起。
5. 在“流程设计”点击“打开文件”，选择 `script/bpm/zsjos_sales_order_dual_approval.bpmn20.xml`。不要使用模型列表顶部只接受 JSON 模型包的“导入模型”。
6. 保存并发布，确认 `registrationReview` 与 `financeReview` 为并行任务组，每组首个处理结果结束同组其余任务。
7. 确认销售拥有 `zsjos:sales-order:create`。V025 将“我的订单”复制给当前已经拥有“录入成交”的角色，不按角色名称推断；将“成交审批”菜单分配给需要在工作台看到审批池的中心人员。
8. 用受控订单验证：销售提交后两中心均产生待办；任一驳回进入补正；重提产生新流程实例；两方通过后订单生效。

审批接口的业务授权不依赖角色名称或 `zsjos:sales-order:review` 权限：服务端实时读取 `zsjos_order_approval_config`，仅允许两个配置根部门及其子部门的启用人员处理本人 BPM 任务。菜单权限只控制工作台入口可见性，不能扩大审批对象范围。

V023、V024 和 V025 不删除或重写业务数据。V025 仅增加本人订单入口、查询索引和审批轮次非通过原因快照。回滚只能停用菜单、技术表单和流程定义，并保留订单、订单项、审批轮次及 BPM 历史。部署或回滚前必须备份数据库；实际迁移、BPM 发布和服务重启均需单独确认。
