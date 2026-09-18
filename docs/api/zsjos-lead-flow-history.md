# 客资流转记录 API

## 查询接口

```text
GET /admin-api/zsjos/lead/{leadId}/flow-history
```

`leadId` 是路由和对象关系使用的内部主键，不作为用户可见“客资编号”。客资详情仍以
`leadNo` 展示业务编号。调用者必须同时满足：

- 功能权限 `zsjos:lead-detail:flow-read`；
- 当前租户；
- 现有统一 Lead 对象读取关系。

功能权限不扩大客资对象范围。跨租户、无关用户或只有菜单权限的请求不能读取记录。

### 身份展示与脱敏

流转记录按当前查看人实时投影身份名称，不修改事件、分配历史或其他历史快照。详情域统一使用
Lead 身份投影规则：自动/普通派单且已归属、提交人与负责人不同的客资，负责人查看时仅隐藏提交人身份，
员工提交人查看时仅隐藏负责人身份；判定人、待接销售、回收来源、操作人及原/新负责人仅在其员工 ID
等于当前查看人的对方时显示中文脱敏值，第三方查看人不脱敏。合作方身份与员工用户使用独立 ID 命名空间；拥有
`zsjos:lead:query-all` 或负责人所属部门主管权限的用户显示完整员工名称。系统操作显示“系统”，
无法解析的员工显示“未知账号”，兼职名称同样按该上下文投影。指定派单不启用相互脱敏。

## 响应字段

新版 `lead_submitter_supplemented` 节点从 `append_v1` 事件快照展示本次补充备注。
旧版节点的 `before.remark` 不代表当次填写内容，不展示为当次备注。存在旧版补充事件时，
“客资提交”节点不再以当前 Lead 备注冒充首次备注；有依据的旧文本统一在详情 `remarkHistory` 中展示为历史备注。
新版补充节点的操作人显示为“提交人”，来源由快照的主体类型确定；Partner 操作不会因 ADMIN 用户 ID 为空而被标为系统操作。

接口返回按 `occurredAt DESC`、原始数值记录 ID 倒序排列的数组：

| 字段 | 说明 |
|---|---|
| `id` | 稳定投影键，格式为 `lead:{id}`、`event:{id}`、`assignment:{id}` 或 `aging:{id}`；仅用于列表稳定性 |
| `occurredAt` | 实际发生时间 |
| `businessObject` | 客资、客资分配、公海、客资跟进或客资申诉 |
| `flowNode` | 稳定事件码映射的中文节点 |
| `source` | 员工工作台、兼职端、系统任务、自动分配、指定派单或公海处理 |
| `operator` | 员工事件解析当前 System 账号昵称，兼职提交解析当前 Partner 名称；不可解析时为“未知账号” |
| `fromOwner` / `toOwner` | 原归属和新归属销售名称 |
| `leadStatusBefore` / `leadStatusAfter` | 客资状态变化；无变化时为空 |
| `assignmentStatusBefore` / `assignmentStatusAfter` | 分配状态变化；无变化时为空 |
| `reason` | 来源记录保存的业务原因；判无效优先使用事件中的原因标签快照 |
| `remark` | 来源记录保存的说明或备注；提交、有效性判定和跟进记录按已有持久化事实映射 |
| `attachments` | 来源事件保存的证据引用 |

`attachments` 包含 `infraFileId`、`originalName`、`contentType`、`previewUrl`、
`previewable` 和 `available`。只有图片和 PDF 会签发 10 分钟预览地址；接口不提供下载动作。
无法签名的引用返回 `available=false`，其他文件返回 `previewable=false`，前端分别显示
“附件不可用”和“暂不支持预览”。

## 合并与去重

兼职昵称仅用于 H5 排行榜；流转记录仍使用业务姓名。销售负责人查看兼职姓名时沿用服务端脱敏，即使指定派单也脱敏；完整身份授权和持久化姓名不变。见[姓名与昵称契约](partner-profile-nickname.md)。

- Lead 的 `submittedAt`（缺失时使用创建时间）投影为“客资提交”。
- `zsjos_business_event` 投影判定、挂起、恢复、申诉、跟进和关联分配事件。
- `zsjos_lead_assignment_history` 投影派单、接单、拒单、超时、抢单、转派、回收和释放。
- `zsjos_lead_aging_pool_event` 投影公海进入、协作人分配/变更和退出。
- 业务事件的 `relatedObjectRefs.assignmentHistoryId` 指向分配历史时，只保留业务事件投影。

合并只读取现有持久化事实，不回填或猜测缺失历史。现有来源表没有统一的操作人或销售
姓名快照，因此删除账号后无法保证还原历史姓名；接口不会用显示名称推断身份或来源。
历史 `lead_appeal_overturned` 事件即使保存的是申诉审核状态，也按该稳定事件语义投影为
客资“无效 → 有效”；提交申诉和维持原判不投影客资状态变化。历史 `converted` 客资状态码
兼容显示为“有效”，但不会改写原事件记录。

## 页签契约

详情响应的 `visibleTabs` 仅在权限检查通过时包含 `flow-history`。Workbench 可接受
`/zsjos/leads/manage?leadId={内部客资ID}&tab=flow-history`，但必须再次消费服务端
`visibleTabs`；无权限时回退 `overview`。现有业务通知仍进入申诉、投诉、跟进或概览，
不会自动改为流转记录页签。
