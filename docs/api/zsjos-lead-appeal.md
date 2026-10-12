# 客资三级申诉 API

统一前缀为 `/admin-api/zsjos/lead/appeal`，所有请求使用当前登录用户与租户上下文。

## 工作台申诉处理详情

`GET /{appealId}/review-detail?taskId=...` 返回 `{ lead, context }`。Controller 要求
`zsjos:lead:appeal:query`；Service 还验证对应复核阶段权限、复核人快照，以及 BPM 当前用户待办或
已办任务的业务键、实例和节点。客户端传来的 taskId 仅用于定位，不授予权限。

已有普通客资对象读取权限时，`lead` 复用客资详情投影及其授权标签。仅有申诉任务读取权时，
仅返回客资编号、姓名、状态、申诉摘要和 `visibleTabs: ['appeals']`，不返回联系方式或普通操作。
这不会改变普通客资列表、详情或跟进接口的可见范围。`context` 提供该客资的各判定周期申诉记录；
只有所选当前周期、当前用户真实待办的记录携带可裁决 taskId，已办及其他轮次不携带。
处理场景的 `canSubmit` 为 false；申诉发起仍走既有普通申诉上下文。

工作台 `/zsjos/appeals` 复用客资详情，并默认进入“申诉记录”、定位所选申诉。原判依据和申诉材料
对照展示，历史按无效判定周期分组。裁决理由与附件在页内填写，继续调用原 overturn/uphold 接口，
保留幂等键、图片限制与不可撤回确认。提交成功保留当前详情并刷新记录和列表；失败保留内容，重试
同一结论复用幂等键。附件展示使用通用 AttachmentCard，上传使用通用 DeferredAttachmentPicker；
预览/下载重新读取授权地址。没有新增依赖、角色授权或数据库变更。

新读取接口仅由 Workbench 使用，Admin/H5 的原有读取、提交、裁决契约不变。需先部署包含该接口
且满足当前申诉周期数据库前置条件的后端，再上线新版 Workbench；源码测试不代表已完成部署。

| 方法 | 路径 | 权限 |
|---|---|---|
| `GET` | `/lead/{leadId}/list` | `zsjos:lead:appeal:query` 或提交权限；提交人/负责人对象权限 |
| `POST` | `/lead/{leadId}/submit` | `zsjos:lead:appeal:create`；仅客资提交人、当前无效状态、下一合法轮次 |
| `GET` | `/inbox-page?handled=false|true` | `zsjos:lead:appeal:query`；BPM 待办/已办任务归属 |
| `PUT` | `/{appealId}/overturn` | 对应复核权限、BPM 任务和客资对象权限 |
| `PUT` | `/{appealId}/uphold` | 对应复核权限、BPM 任务和客资对象权限 |
| `POST` | `/attachment/upload` | 提交或复核权限；仅 JPG/PNG/WebP |

`GET /inbox-page` 将调用人具备权限的申诉阶段作为 BPM 流程变量过滤条件，并由 BPM 同时按当前租户、任务归属、流程与任务 Key 原生分页，因此 `total` 只统计调用人仍有权查看的申诉任务。返回后仍复核审批人快照、阶段、任务流程实例和 Lead 关系；不一致视为流程数据完整性故障并返回稳定的流程不可用错误，不会静默丢行后返回错误总数。每轮申诉使用独立记录和流程实例，已办历史按所属轮次展示。当前用户没有符合条件的待办或已办时返回 `list=[]`、`total=0`，空结果不是服务异常。

提交和裁决请求均必须携带非空 `idempotencyKey`；同一次申诉或裁决意图的快速重复点击、上传失败和请求重试必须复用该键，只有服务端确认成功后才生成下一意图的键。裁决还必须携带当前 BPM `taskId` 与理由。每个环节最多 9 张图片，图片引用只保存基础设施文件 ID 和不可变元数据快照，读取时返回私有预签名地址。

状态按 `sales_manager_reviewing`、`quality_reviewing`、`chairman_reviewing`、`overturned`、`upheld`、`withdrawn` 解释。`upheld` 仅表示当前轮维持无效，第三轮 `upheld` 才是最终无效；没有期限、自动升级或第四轮申诉。

裁决权限按轮次和状态固定映射：第一轮要求销售主管复核权限，第二轮要求质检复核权限，第三轮要求董事长复核权限。请求轮次、持久化轮次、当前状态和权限任一不匹配均拒绝，不能用其他轮次权限代替。

## 流程契约校验与空身份字段

新申诉通过 BPM `BpmDefinitionReadApi` 读取当前发布定义，要求定义启用、Key 匹配，且恰好存在一个
`appealReview` 节点并使用“发起人自选（业务提交时指定）”审批人策略。校验发生在申诉记录和通知事件
写入之前；创建流程时固定传入已校验的 `processDefinitionId`，避免模型并发发布后启动到另一版本。
配置缺失或不兼容返回既有 `LEAD_APPEAL_PROCESS_UNAVAILABLE`，不留下新申诉或通知。
这项检查防止已知审批人契约漂移，不代表所有事务完成后的流程异常都能回滚提交事务。

详情与列表允许未裁决记录的 `reviewerUserId/reviewerUserName` 为空，也允许 Partner 申诉的员工申请人
字段为空。历史列表仍经过身份脱敏投影；不以当前审批人快照冒充已经作出裁决的处理人。
HTTP 路径、请求字段、权限和响应结构不变，Workbench 与 Partner H5 无需新增字段。


## 每次无效判定的独立申诉周期（2026-10-12）

本节替代原先按整条客资累计三轮及按全部历史阻断主管直接改判的限制。
每个实际 `lead_qualified_invalid` 业务事件构成独立周期。周期内第 1/2/3 轮分别由负责人主管、
质控、董事长处理；只有上一轮维持无效才能手动升级，第三轮维持无效后该周期结束。
改判有效后再次判无效产生新事件，新周期重新从主管首轮开始。转派、刷新、重复请求和
同周期继续申诉不产生新周期。进行中的任意申诉仍阻止新申诉、再次判无效及主管直接改判。

主管直接改判只检查当前周期：无申诉或唯一首轮维持无效时允许，其他本周期结果与高层复核
仍阻止绕过；已结束的旧周期只保留历史，不阻断新周期。申诉裁决在 Lead → Appeal 锁顺序下
复核所属判定仍为当前周期，旧待办不得修改新判定。幂等重放保持原结果，不跨周期创建新记录。

申诉记录增加 `invalidEventId`、`currentCycle`。原列表继续返回完整历史且按周期、轮次排序。
员工端 `GET /admin-api/zsjos/lead/appeal/lead/{leadId}/context`、Partner 端对应 `/part-api/zsjos/lead/appeal/lead/{leadId}/context`
返回 `{invalidEventId,nextRoundNo,canSubmit,records}`；记录读取沿用原对象和功能权限，提交能力由
服务端依据提交人身份、权限、客资状态及周期计算。POST 提交必须带读取到的 `invalidEventId`；
缺失或过期的判定标识拒绝，不能把旧页面申请自动绑定新判定。工作台和 H5 必须同步更新，
Admin 既有裁决接口不变，历史轮次字段仍表示周期内轮次。

V311 给申诉增加周期键并把唯一约束改为 `(tenant_id,lead_id,invalid_event_id,round_no)`。
历史仅通过唯一的 `lead_appeal_submitted` 事件中 `appealId` 精确关联，再取该事件之前同租户、
同客资的最后一次无效判定事件；不依赖秒级时间猜测。无法关联的历史保留键 0，
若客资已有新判定事件而同时存在未关联历史，保守阻止申诉/直接改判，需单独核查，不能虚构历史。

上线顺序：暂停申诉写入 → V311 → 新后端与工作台/H5 → 真实登录验收。既有流程模型不变，
无需重新发布，也不恢复或裁决其他 BPM 实例。旧客户端缺少周期标识会被拒绝，不能混合上线。
受控 MySQL 验证命令：`python -B script/sql/mysql/tools/test_lead_appeal_cycles.py`。
