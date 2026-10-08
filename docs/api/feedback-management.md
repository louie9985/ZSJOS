# 需求与反馈 API

## 边界与身份

需求、BUG 和技术支持共用 ZSJOS 反馈领域模型，并复用通用工单的编号关联、附件校验、乐观锁和历史能力。所有接口位于 ADMIN API 前缀下，使用当前登录员工、租户上下文和标准 `CommonResult` 包装。员工接口默认读取本人提交的数据，已授权管理员可按下述显式范围只读查看；管理接口除菜单权限外，还按反馈类型累计校验对象权限。

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

### 审批人的操作路径

审批人在工作台的审批中心（`/bpm/task/todo`）处理需求反馈，不需要反馈管理菜单。审批内容卡由 `FeedbackContentProvider` 按 businessKey 解析，规则如下：

- **可见性**：持有对应类型管理权限、提交人本人、该轮次 `approval_context_json` 里记录的指定审批人，或该轮 BPM 实际任务 assignee/owner 参与人可查看。参与其他轮次不授予本轮读取权限；转办、委派和加签后的实际任务参与关系通过 BPM 公共 API 校验。
- **轮次**：卡片按**该任务所属轮次**的快照渲染，不是最新一轮。`businessKey` 的四段里第二段是 `workOrderId`（不是 feedbackId），第四段是轮次；轮次解析要按段取，`split(":")` 取巧会静默命中另一条记录。
- **申请内容**：按 `round.formSnapshotJson` + `round.valueSnapshotJson` 渲染用户填写的字段，字典标签用提交时冻结的那份，不查当前字典。
- **附件**：申请附件与处理结果附件都下发预签名地址和真实 MIME（`contentType`）。前端 `AttachmentGrid` 只看 MIME 决定走图片预览还是下载链接，缺 MIME 时图片会静默降级成文字链接。单个文件签名失败只丢该附件，不影响其余内容与分组。
- **深链**：只有提交人拿到 `/zsjos/feedback?feedbackId=`，审批人不下发 route——反馈页按 `read-own` 放行，审批人点进去只会看到无权页。

反馈详情中的 image/upload 字段（含满意度表单）、沟通附件及处理结果附件，在完成既有访问权限校验后，按文件 ID 调用 Infra FileApi 重新生成读取 URL。动态表单保留历史文件名、类型及大小快照，不把新 URL 写回数据库，也不回退到历史临时签名地址。单个文件不存在或签名失败时返回空 URL，保留其元数据及其他反馈内容；重新获取详情会再次尝试签名。Vue 管理端和 React 工作台继续消费原有附件结构，无需更改接口字段。

详情展示签名采用批量优先策略：每份动态表单的 image/upload 文件统一去重签名，处理结果和全部沟通附件合并去重签名，并复用对应文件元数据。Infra 批量接口整体失败时，逐个文件降级签名，单文件失败保留空 URL；不跨请求缓存临时地址。此优化不改变前端字段结构。

## 通知

四个场景为 `zsjos.feedback.employee_replied`、`zsjos.feedback.admin_replied`、`zsjos.feedback.completed` 和 `zsjos.feedback.survey_requested`。消息动作使用受控业务详情，深链为 `/zsjos/feedback?feedbackId={id}`，进入详情时仍重新校验菜单权限和本人数据范围。

## 当前租户管理员读取

个人列表新增可选 readScope=SELF|ALL|USER、targetUserId，默认本人。管理员 ALL/USER 为只读投影；查看他人不标记已读、不回复或重提。ADMIN 与 PARTNER 同号用户不能获得对方的本人动作。基础功能权限保留，详见 tenant-admin-read-all.md。

## 审批进度和手动催办（V294）

员工最近反馈、个人列表及管理列表的 `approvalSummary` 为审批中需求提供 `availability` 和 `currentTasks`（任务、节点、当前审批人、到达时间）。服务端对已授权列表批量查询 BPM，不按每条记录加载完整流程。工单 `assigneeName` 仍表示审批通过后的处理人。

| 方法 | 路径 | 权限与对象边界 |
| --- | --- | --- |
| GET | `/zsjos/feedback/{id}/approval?roundNo=` | `zsjos:feedback:read` + `read-own`，包含已有租户管理员只读边界 |
| GET | `/zsjos/feedback/{id}/approver-view/approval?roundNo=` | 同一功能权限 + `read-approver`；只返回该用户获授权的轮次 |
| GET | `/zsjos/feedback-management/{id}/approval?roundNo=` | `zsjos:feedback:query-admin` + 对应反馈类型 `read-admin` |
| POST | `/zsjos/feedback/{id}/urge` | `zsjos:feedback:requirement:urge` + `urge-own`，仅 ADMIN 提交人本人 |

省略 `roundNo` 返回当前读取身份可见的最新轮次。响应包括 `rounds`、`roundNo`、`latestRoundNo`、`version`、该轮 `fields/values` 快照、`progress`（节点、任务、时间、意见、候选人）、`lastUrgedAt/nextUrgeAt/canUrge`。`availability` 为 `AVAILABLE`、`NOT_REQUIRED` 或 `UNAVAILABLE`；仅快照明确关闭审批时返回无需审批，缺失流程不能伪装成免审批。

BPM 通过 `BpmProcessProgressApi` 提供不含流程变量和写命令的内部 DTO。ZSJOS 先做业务授权，BPM 再校验租户；调用者无需新增通用流程查询权限。审批人轮次读取除原冻结指定审批人外，接受该轮 BPM 任务实际 assignee/owner 的参与关系，以覆盖转办、委派及加签；不以参与其他轮次放行本轮。审批中心内容卡使用相同轮次边界。

催办请求为 `{version, roundNo, idempotencyKey}`。对反馈行加锁后验证身份、最新轮次、审批状态及每轮 30 分钟间隔；新轮次不继承旧轮次冷却。BPM 实时可处理任务为接收人来源，排除挂起、等待前序及已通过等待加签完成的任务，按启用员工去重。无接收人、审批已结束、旧版本、错误轮次、冷却中、缺少持久通知规则分别返回稳定错误码 `1_900_016_022/020/012/019/021/023`。

通知场景 `zsjos.feedback.approval_urged` 默认站内通知，固定接收人和任务引用在事件中冻结，使用 System 的事务内 durable outbox；成功表示已持久受理，不表示接收人已经阅读或渠道已送达。轮次催办时间、工单 `APPROVAL_URGE` 历史及事件在同一事务提交；失败回滚，重试同一幂等键不重复发布。没有自动催办或流程推进副作用。

消息从已授权的 `templateParams.taskId` 定位 `/bpm/task/todo?taskId=`，通过新增 `GET /bpm/task/get-todo?id=` 重新验证当前登录人的可处理任务，不受列表第一页限制；结束或转办后保留消息详情并提示，不打开其他任务。两端历史表单使用选中轮次快照和重新签名的附件 URL。
