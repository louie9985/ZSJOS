# 生产内容审批：自选编导与学员临时读取

2026-10-13。适用于租户 1 新提交及退回重提，替代内容批审原有的运营—编导绑定选人规则。既有在途、历史批次及其他用户关系场景不变。

## 选择审核编导

流程仍是编导、终审两个单人节点串联，拒绝结束本轮。编导节点采用 BPM 发起人自选策略（35）；SIMPLE 与 BPMN 设计器的“自选角色范围”保存管理员选择的 System 角色 ID，复用 `candidateParam` 逗号分隔正整数协议。多个角色取成员并集；空参数仅对既有其他流程保持不限制范围的兼容语义，生产内容新提交拒绝空角色范围。

`GET /admin-api/zsjos/content-review/batch/{batchId}/submit-context` 要求提交功能权限与本人草稿对象权限，返回 `processDefinitionId/processDefinitionVersion/directorTaskKey/roleIds/candidates/previousDirectorUserId`。候选人为启用角色中的启用员工，同时具有：

- `zsjos:content-review:query`
- `zsjos:content-review:director-review`
- `zsjos:media-student:query-my`
- `zsjos:media-account:query`
- `zsjos:positioning-card:query`
- `zsjos:student:positioning-interview-query`

发起人本人不可选。角色、用户和权限均通过 System 公共 API 读取；不按角色名称猜测、不写角色菜单授权。

`POST /batch/{batchId}/submit` 请求为 `{expectedVersion,directorUserId,processDefinitionId}`。取消草稿仍仅需 `expectedVersion`。提交再次验证当前定义版本、候选资格、运营及账号归属，流程版本变化要求刷新。BPM 公共启动路径也校验配置角色范围，通用入口不能伪造角色外人员。

### 表单内预选编导（2026-10-21）

学员内容审批的新建、继续草稿、编辑与退回重提，在顶部提示下方、发布账号上方显示“审批编导”搜索单选框，整批账号和作品共用一人。新建不默认选人；草稿回填保存的人选；退回重提优先回填仍有效的上一轮人选，允许更换。提交按钮表达对当前表单人选的确认，保存成功并读取最新版本后直接提交，不再弹出选人窗口。列表中学员草稿的“提交审批”打开同一编辑表单；旧非学员批次保留原选人提交入口。

`GET /admin-api/zsjos/content-review/submit-context` 的 `studentPersonId` 与 `batchId` 必须且只能提供一个。要求 `zsjos:content-review:submit`；新建场景校验学员可见且当前运营至少拥有该学员一个账号，批次场景校验当前租户、本人提交对象权限、未被后续轮次替代及可编辑／重提状态。返回结构及候选计算沿用旧批次接口；退回批次返回本轮原编导作为重提默认值，已有草稿只回填自己的保存选择，不从上一轮补齐，以保留用户显式清空的意图。此 GET 不创建草稿、流程或访问授权。旧批次候选接口保留兼容。

学员草稿创建／保存／修订请求和批次详情增加可选 `draftReviewerSelection: {directorUserId,processDefinitionId}`。草稿写入 `context_snapshot_json.draftReviewerSelection`，独立于正式 `director_user_id` 和人员关系快照；只有草稿详情投影该字段。显式 `null` 清除选择；旧客户端省略字段保存已有草稿时保留原选择。旧草稿不补人选，也不推断账号责任编导。保存仅记录预选意向，不验证当前流程候选资格、不启动 BPM、不授予预选人资料访问权；人员资格与正式人员快照以提交时校验为准。

保存草稿不要求选人，原有作品必填校验仍保留。候选加载失败／为空时允许继续填写与保存，提交时必须具备已加载的有效选择。回填人选不可用或流程定义已变化时清空选择并提示重新选择；提交复核发现这两种情况时在原字段刷新候选、保留内容，不自动重试提交。清空和改选参与草稿保存判断；响应丢失仍按批次状态恢复，不重复启动审批。

业务将一个指定人映射到已发布流程的真实编导节点 ID。账号责任编导与本次审核编导分开，账号、学员及服务归属不改变。仍保留现有批次账号选择约束及原有终审规则。

批次 `director_user_id` 保存本轮审核编导；`relation_snapshot_json.source=STARTER_ROLE_SELECTION` 表明新选择方式，另保存选择人员姓名、角色 ID 和流程定义 ID，不再写虚假的关系 scene/relationId。账号责任人保留原快照。`context_snapshot_json.reviewAccountScopes` 保存本次账号、创建服务关系、协作组和责任人边界，旧数据不回填。

## 只读学员上下文

审批响应增加 `canReadStudentForReview`。指定编导通过既有 `/zsjos/media-students?personId=...&accountId=...&contentReviewBatchId=...` 打开该页面的只读分支。该分支不加载普通学员列表、写操作、财务、客资、邀请绑定和其他协作组数据。原负责人从普通入口仍按原有权限操作。

界面必须复用正在使用的 `StudentDetail`、`AccountProfilePanel`、`ServicePositioningCard`、`AccountPositioningHistory` 和访谈/定位弹窗：保持学员标题、概览和账号页签、账号定位/状态/复盘分组及右侧摘要栏。审批上下文只切换这些组件的读取接口并关闭写操作；不得用另一套简化表单替换当前详情布局。账号复盘中尚未授权的数据不额外读取，已发布作品、财务和其他业务入口不因界面复用而开放。

读取前缀为 `/admin-api/zsjos/content-review/batch/{batchId}/student-read`：

| 路径 | 内容 |
| --- | --- |
| `?personId=...` | 学员基本信息、本次账号列表 |
| `/accounts/{accountId}/profile` | 复用账号档案投影，移除兼职获客/成交指标及写动作 |
| `/accounts/{accountId}/positioning` | 本账号及对应服务关系的已提交定位记录 |
| `/accounts/{accountId}/positioning-overview` | 供现有概览组件使用的受限定位投影；实际应用版本取账号应用记录，所有创建/选择/提交动作关闭 |
| `/services/{relationId}/interview` | 对应服务关系访谈，清空写动作 |
| `/accounts/{accountId}/file` | 授权附件元数据；参数 kind=profile/interview/positioning/evidence、fileId，定位附件及确认凭证另带 submissionId |
| `/accounts/{accountId}/file-content` | 同参数，重新授权后通过 Infra 原存储读取字节，响应 no-store |

上述路径只有 GET。各服务声明 `content-review-student/read` 对象权限并验证实际资源关联。指定编导、本轮新规则标记、功能权限、租户、批次已提交且处于编导/终审阶段、BPM 实例仍运行且 businessKey 匹配、学员/账号/服务/协作组/责任人关联均须通过。已知权限失败返回稳定错误；基础设施故障不伪装成空列表。

批次 ID 不是授权令牌。其他账号、服务、学员、文件不能通过参数替换访问。账号归属或协作组变化使原上下文失效。整轮通过、退回、取消及实例停止后禁止新的读取；新轮次不会恢复旧编导的授权。普通对象权限、列表范围和写接口未放宽。

附件内容通过认证 API 获取后在浏览器生成本地 Blob URL；不向调用者发放存储签名链接，刷新预览/下载重新校验；组件卸载或替换时释放 Blob。已在浏览器展示或已下载的内容不能被远程收回。历史审批仍展示原有提交快照，不重建学员历史资料。

## 错误与兼容

| 错误码 | 含义 |
| --- | --- |
| 1900012140 | 缺少审核人或定义版本，请选择并确认 |
| 1900012141 | 定义版本变化，请刷新 |
| 1900012142 | 编导自选角色范围未配置 |
| 1900012143 | 选中人失效、权限不足或自审 |
| 1900012144 | 临时读取失效或越出本次范围 |
| 1009003090 | BPM 自选角色参数无效或角色不可用 |
| 1009003091 | BPM 所选人员不在当前角色范围 |

客户端未知错误仍使用既有 code/msg；旧提交客户端不再回退绑定选人。审批响应丢失继续查询原批次，不重复建立流程。无需业务数据库结构迁移；BPM JSON/XML 保存既有参数，批次复用现有 JSON 字段。

上线与目标环境验收见 [部署说明](../operations/content-review-self-selection.md)。


### 2026-10-14 审查修复
附件按需读取、批量元数据、并发颜色更新与通知兼容的范围及验证见 [修复记录](../operations/review-fixes-20261014.md)。
