# 需求与反馈 API

## 边界与身份

需求、BUG 和技术支持共用 ZSJOS 反馈领域模型，并复用通用工单的编号关联、附件校验、乐观锁和历史能力。所有接口位于 ADMIN API 前缀下，使用当前登录员工、租户上下文和标准 `CommonResult` 包装。员工接口只允许读取本人提交的数据；管理接口除菜单权限外，还按反馈类型累计校验对象权限。

通用工单使用 `businessType` 隔离：历史及普通工单为 `GENERIC`，本功能为 `FEEDBACK`。原通用工单列表和详情不会返回反馈记录。

## 员工端接口

| 方法 | 路径 | 权限 | 用途 |
| --- | --- | --- | --- |
| `GET` | `/zsjos/feedback/portal` | `zsjos:feedback:query` | 三类入口状态与本人最近 5 条反馈 |
| `GET` | `/zsjos/feedback/form?type=` | `zsjos:feedback:query` | 当前动态表单、标题字段和配置版本 |
| `POST` | `/zsjos/feedback/requirement/create` | `zsjos:feedback:requirement:create` | 提交需求 |
| `POST` | `/zsjos/feedback/bug/create` | `zsjos:feedback:bug:create` | 提交 BUG |
| `POST` | `/zsjos/feedback/support/create` | `zsjos:feedback:support:create` | 提交技术支持 |
| `POST` | `/zsjos/feedback/{id}/resubmit` | `zsjos:feedback:requirement:create` | 修改并重提审批驳回的需求 |
| `GET` | `/zsjos/feedback/my-page` | `zsjos:feedback:query` | 本人记录分页，支持类型和状态筛选 |
| `GET` | `/zsjos/feedback/{id}` | `zsjos:feedback:read` | 本人详情 |
| `PUT` | `/zsjos/feedback/{id}/read` | `zsjos:feedback:read` | 标记本人未读状态 |
| `POST` | `/zsjos/feedback/{id}/reply` | `zsjos:feedback:reply-self` | 员工回复 |
| `POST` | `/zsjos/feedback/{id}/survey` | `zsjos:feedback:survey:submit` | 提交一次满意度 |
| `POST` | `/zsjos/feedback/file/upload` | 对应创建、回复或完成权限 | 上传并登记反馈附件 |

创建请求包含 `values`、读取表单时取得的 `configVersion` 和 `idempotencyKey`。重提及所有状态写命令还必须携带当前 `version`。服务端只接受当前表单字段，并对必填、类型、字典值、评分范围和附件归属重新校验；配置或记录版本变化会返回明确冲突，不会静默套用旧配置。

## 管理端接口

| 方法 | 路径 | 权限 | 用途 |
| --- | --- | --- | --- |
| `GET` | `/zsjos/feedback-management/requirement/page` | `zsjos:feedback:requirement:manage` | 需求分页 |
| `GET` | `/zsjos/feedback-management/bug/page` | `zsjos:feedback:bug:manage` | BUG 分页 |
| `GET` | `/zsjos/feedback-management/support/page` | `zsjos:feedback:support:manage` | 技术支持分页 |
| `GET` | `/zsjos/feedback-management/{id}` | `zsjos:feedback:query-admin` | 管理详情；同时要求对应类型管理权限 |
| `PUT` | `/zsjos/feedback-management/{id}/assign` | `zsjos:feedback:assign` | 分派或改派 |
| `POST` | `/zsjos/feedback-management/{id}/reply` | `zsjos:feedback:reply` | 后台回复 |
| `POST` | `/zsjos/feedback-management/{id}/complete` | `zsjos:feedback:complete` | 填写处理结果并完成 |
| `POST` | `/zsjos/feedback-management/{id}/survey` | `zsjos:feedback:survey` | 发起一次满意度调研 |
| `GET` | `/zsjos/feedback-management/settings/list` | `zsjos:feedback:settings` | 当前四类设置 |
| `PUT` | `/zsjos/feedback-management/settings` | `zsjos:feedback:settings:save` | 保存单类设置 |
| `GET` | `/zsjos/feedback-management/settings/candidates?type=` | 设置或分派权限 | 对应类型的启用处理候选人 |
| `GET` | `/zsjos/feedback-management/settings/form-options` | `zsjos:feedback:settings` | BPM 动态表单兼容性和标题候选字段 |
| `GET` | `/zsjos/feedback-management/settings/process-options` | `zsjos:feedback:settings` | 已发布且启用的流程定义 |

未分派记录不能回复或完成；首次分派进入处理中，处理中允许改派。完成命令要求非空处理结果。完成后双方仍可回复，但不改变状态。管理操作不要求操作者等于当前处理人，但候选人和操作者必须分别满足启用状态、类型管理权限及相应按钮权限。

## 状态、审批与快照

员工端状态为 `APPROVING`、`APPROVAL_REJECTED`、`WAITING`、`IN_PROGRESS`、`COMPLETED`，分别展示为审批中、审批驳回、待处理、处理中、已完成。需求开启审批时使用 `zsjos_feedback_requirement_approval`，业务键为 `feedback:{workOrderId}:round:{roundNo}`；审批通过进入待处理，驳回后只能由提交人按原编号重提。

每轮提交保存表单定义、字段值、字典标签、人员名称和审批上下文快照。详情和通知展示历史快照，不重新解析当前字典标签。满意度每条已完成反馈最多发起一次，提交人只能提交一次且不能修改。

## 通知

四个场景为 `zsjos.feedback.employee_replied`、`zsjos.feedback.admin_replied`、`zsjos.feedback.completed` 和 `zsjos.feedback.survey_requested`。消息动作使用受控业务详情，深链为 `/zsjos/feedback?feedbackId={id}`，进入详情时仍重新校验菜单权限和本人数据范围。
