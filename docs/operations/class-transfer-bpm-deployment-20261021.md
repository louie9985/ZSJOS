# 中世健调班审批部署记录

- 部署时间（北京时间）：2026-10-21T11:19:25+08:00。
- 环境：`test`，租户 `1`；当前分支 `main`，提交 `b1ecfffc9f2e19fa4d58d455cb4e4c3e7c033f11`。
- 操作：按用户“查找调班审批并部署”的请求，通过现有 ADMIN 会话调用 BPM 导入、发布接口；操作账号内部 ID 为 `1`。
- 流程：`zsjos_class_transfer`（班级调班审批），SIMPLE 资产版本 `1.0.0`。
- 资产：[process-model.json](../../script/bpm/zsjos_class_transfer/1.0.0/process-model.json)。SHA-256：`09928c6b9fa9e0b78a03a4bd49c6d25cd4c92d6c1179ba7c6d5cc0737c4017f8`。
- 模型 ID：`21e74504-ccfe-11f1-810f-8e534d5dae38`。
- 部署 ID：`36b42117-ccfe-11f1-810f-8e534d5dae38`。
- 流程定义 ID：`zsjos_class_transfer:1:379bc1fa-ccfe-11f1-810f-8e534d5dae38`；Flowable 版本 `1`，状态启用。

## 范围与流程

部署前目标租户不存在同 key 的模型或定义，调班业务申请为零。使用现有启用分类 `general-module`，导入资产后调用 `POST /admin-api/bpm/model/deploy` 发布。未执行初始化 SQL、修改角色权限或重启服务。

正式班之间由当前服务负责人通过班级调班业务入口提交，节点 `originalSupervisorReview` 由后端传入申请人当前部门主管，候选策略 `35`。主管必须启用、不能是申请人本人且持有 `bpm:task:update`。资产保持 `visible=false`，不在审批中心通用发起列表展示；应从班级调班入口发起。通过后的迁移及陈旧申请校验沿用[班级管理与调班 API](../api/delivery-class-management.md)。

## 验证与限制

- 目标资产的清单校验和、任务 key、候选配置校验通过。
- 导入与发布接口成功，认证后的模型和流程定义接口回读成功；数据库只读回查确认租户、唯一模型、定义版本及 `SUSPENSION_STATE_=1`。
- 已部署 BPMN 包含 `originalSupervisorReview` 和候选策略 `35`；中文名称通过 utf8mb4 连接读取并校验 `HEX(NAME_)`。
- 调班业务申请和流程实例数部署前后均为零，未触发真实调班。未执行真实提交、主管审批或双端浏览器端到端验证。
- 导入后的 API 为 `assignEmptyHandler` 补充 `userIds:null`，首次严格对象比较因此停止在发布前；确认只是序列化空值后，复用同一模型完成发布，无重复导入。
- 全量清单检查存在无关既有问题：`zsjos_sales_order_dual_approval/1.0.0/process.bpmn20.xml` 校验和不匹配；未修改该资产，本次调班资产定向校验通过。

受限目录证据：`/opt/zsjos-runtime/backups/class-transfer-bpm-20261021/`，包含部署前后范围快照、导入/发布结果、模型、流程定义、部署 BPMN 和校验结果，不含会话令牌。

## 重复执行与恢复

重复验收只调用只读接口；已存在同 key 的模型不得再次导入，未修改时无需再次发布。未来修改应先导出备份既有模型，再通过 BPM API 更新及发布新版本。若需撤回本次可发起能力，应单独确认后挂起该定义，并先检查是否已有实例；不直接删除 Flowable 表或历史。
