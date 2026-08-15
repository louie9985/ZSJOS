# 成交订单双中心会签部署

流程定义文件为 `script/bpm/zsjos_sales_order_dual_approval.bpmn20.xml`，流程 Key 固定为 `zsjos_sales_order_dual_approval`。管理员必须在 BPM 管理页面受控创建模型、载入文件、发布并启用，不使用全局自动部署。流程文件包含完整 BPMN DI 图形坐标，可在 Admin 设计器中预览和维护。

发布前按顺序完成：

1. 先集成并执行 V021、V022，再依次执行 `V023__sales_order_dual_approval.sql`、`V024__zsjos_bpm_readonly_forms.sql`、`V025__sales_order_workbench_views.sql`、V026-V028，最后执行 `V029__sales_order_approval_filter_scheme.sql`。缺少 V022 的环境不得继续执行或发布；V029 依赖 V005 的筛选方案表和 V023 的流程定义元数据。
2. 在 V054 之后执行 `V055__sales_order_supervisor_confirmation.sql`，并在迁移链到达 V060 后执行 `V061__sales_order_supervisor_menu_id_collision.sql`。V061 将因 V048/V055 ID 冲突而缺失的主管确认菜单安装到 6856，不自动授予任何角色。确认 `zsjos_order_approval_config` 的报名履约与财务根部门 ID 有效，且排除根部门及子部门负责人后，两个中心均至少有一名启用的普通审批人。需要申请主管确认的审批人，其直属部门必须配置启用且不同于本人的 `leaderUserId`。
3. 在 Admin 进入“工作流程 → 流程管理 → 流程模型”，新建 BPMN 模型：流程标识填写 `zsjos_sales_order_dual_approval`，流程名称填写“成交订单双中心会签”，设为不可见、发起范围为全员，并指定流程管理员。
4. 在“表单设计”选择“成交会签流程关联信息”。该表单只读，只展示 `orderId`、`leadId` 和 `roundNo`；订单仍由 ZSJOS 工作台提交，不从 Admin 通用流程入口手工发起。
5. 在“流程设计”点击“打开文件”，选择 `script/bpm/zsjos_sales_order_dual_approval.bpmn20.xml`。不要使用模型列表顶部只接受 JSON 模型包的“导入模型”。
6. 确认 `registrationReview` 与 `financeReview` 为并行任务组、已启用加签（模型 XML 为 `flowable:signEnable=true`），每组首个普通处理结果结束同组其余任务。保存并发布为新流程版本；不要迁移、重启或改写在途流程实例。
7. 确认销售拥有 `zsjos:sales-order:create`。将“成交审批”及 `zsjos:sales-order:review` 分配给普通审批人；将 ID 6856 的独立“主管确认”及 `zsjos:sales-order:supervisor-confirm` 显式分配给需要处理主管任务的用户。V055 和 V061 均不按角色名称自动授权。
8. 先发布并启用加签流程新版本，再部署创建新轮次的应用代码。上线前轮次的 `supervisor_confirmation_enabled=0`，继续绑定旧流程版本；上线后新轮次写入 `1` 并使用新版本。
9. 用受控订单验证：销售提交后两中心均产生普通待办；一中心申请主管确认后该中心锁定而另一中心可继续；主管确认恢复本中心普通审批，主管不确认使整轮驳回；两方普通审批通过后订单生效；并行驳回、终止和取消能取消未完成主管记录。

审批业务授权不依赖角色名称，但要求三层累计通过：普通审批接口要求 `zsjos:sales-order:review`、配置部门范围和本人普通 BPM 任务；主管接口要求 `zsjos:sales-order:supervisor-confirm`、申请记录指定主管、本人 BPM 加签任务及订单对象关系。菜单权限不能扩大部门、对象或任务范围。

V023、V024 和 V025 不删除或重写业务数据。V025 仅增加本人订单入口、查询索引和审批轮次非通过原因快照。回滚只能停用菜单、技术表单和流程定义，并保留订单、订单项、审批轮次及 BPM 历史。部署或回滚前必须备份数据库；实际迁移、BPM 发布和服务重启均需单独确认。

V029 只为现有租户补充 `reviewer` 客资筛选方案的草稿、已发布版本和版本记录，使用幂等存在性检查，不覆盖管理员已维护的筛选配置，也不插入订单、任务或文件数据。回滚限于受控环境删除本迁移新增且未被管理员引用的 reviewer 配置行；不得通过删除历史版本或业务记录回滚。实际 SQL 执行、BPM 发布、服务重启和权限变更均不包含在代码交付中，发布前应运行 `script/sql/mysql/verify-bootstrap.sql` 及 schema 差异检查。
