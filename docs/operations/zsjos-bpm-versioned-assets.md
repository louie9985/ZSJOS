# ZSJOS BPM 版本化资产发布

`script/bpm/manifest.json` 是业务流程资产的交付清单，包括 ZSJOS 业务流程和 EAM 资产流转审批。仓库语义版本与 Flowable 自动生成的定义版本分别登记，不能假设编号相同。已发布目录不可覆盖；流程变更必须新增语义版本目录并更新清单与 SHA-256。

资产版本必须使用严格的 SemVer，且目录版本、文件路径和清单登记的 Process Key 必须一致。新审批流程统一使用 Simple 设计器资产 `process-model.json`；历史 BPMN 资产保留为兼容版本，不再作为推荐版本。发布前运行 `python script/bpm/validate_manifest.py`，确认资产格式、Process Key、任务 Key、候选人变量、推荐版本和 SHA-256 全部通过；BPMN 历史资产还会检查 BPMN DI。CI 会以目标分支基线校验已发布资产的路径和校验和不可变；本地可使用 `--base-ref <ref>` 执行同样的基线检查。管理员在 BPM 管理页面人工载入清单推荐文件并发布，不启用应用启动自动部署。已有同 Key 模型时打开并更新为 SIMPLE 后重新发布，不重复导入；没有模型时才导入。导入模型时，后端忽略文件中的管理员用户编号并将当前导入人设为模型管理员，避免跨环境复制用户关系。重新发布后新实例使用最新定义，运行中的旧实例继续使用其原定义。

每次发布记录资产版本、SHA-256、Flowable 定义 ID、Flowable 版本、部署时间和操作人。发布后核对 Process Key 和任务 Key，并以受控业务请求创建一个新实例验证待办。新定义只服务新实例，不迁移、不重启、不改写在途实例。

EAM 资产流转推荐资产为 Simple 模型 `eam_asset_transfer/1.0.0/process-model.json`，最低数据库版本 EAM V010。模型分类使用服务端 BPM 分类编码 `general-module`；导入模型时必须选择当前租户中存在且启用的分类，不能依赖跨环境复制的分类 ID。任务以稳定 task key 线性编排，并通过节点跳过表达式实现类型分流：领用/借用执行申请部门负责人并跳过调拨节点；调拨跳过申请部门负责人，执行转出部门负责人，并在部门不同时执行接收部门负责人；随后进入资产管理员确认和接收人签收。

## 推荐审批资产清单

当前为迁移目标清单，不代表已经通过目标环境发布验收。提现和合作方申诉必须先部署包含 SIMPLE 外部主体兼容扩展的 BPM 后端，再发布新定义。旧后端会拒绝 SIMPLE 的策略 36 发起节点。新后端同时校验已发布 SIMPLE 快照的根节点、节点类型、候选策略和唯一开始入口；只将系统生成的提交活动直接流转并保存活动历史，不创建 Partner 审批任务，也不查询同编号的内部用户。业务节点的策略 36/37/38 仍拒绝外部启动。`startUserSelectAssignees` 经内部用户有效性校验后写入引擎，覆盖表单伪造的同名变量。

提交活动不支持退回外部发起人编辑，或子流程要求发起人手动确认；遇到这些配置明确报错，不自动重新提交。应沿用业务支持的取消后重提路径。旧 BPMN 定义与已运行实例保持不变。

`BpmExternalSimpleEngineTest` 使用隔离 H2/Flowable 实际执行两份资产，覆盖新旧版本并存、外部提交活动历史、内部多人或签、完成和取消、同号 ADMIN/Partner 身份隔离，以及候选人缺失/禁用/未知任务和伪造变量。System 与定义存储使用测试替身，候选策略 35 使用真实实现；没有执行生产业务回调或资金操作。部署后仍需验证真实租户、业务状态回调、拒绝/重复事件以及各端审批页面。

不得使用 `import-existing-config.ps1` 批量发布本次迁移目标；逐项按下文验收后人工发布。Python 资产检查和 Java 转换测试只证明静态结构，不代表运行或业务回调验证通过。

原未登记的提现 BPMN 导出 JSON 移至 `script/bpm/archive/zsjos_partner_withdrawal-1.0.0-bpmn-export.json` 保存，供历史追溯，不作为 SIMPLE 导入资产；已登记的历史 XML 和哈希不变。

| Process Key | 推荐版本 | SIMPLE 结构 | 稳定任务 Key |
| --- | --- | --- | --- |
| `eam_asset_transfer` | `1.0.0` | 条件跳过的线性审批/履行 | `departmentLeaderReview`, `sourceDepartmentReview`, `targetDepartmentReview`, `assetAdministratorReview`, `receiverSign` |
| `zsjos_class_transfer` | `1.0.0` | 单级主管审批，发起时预选审批人 | `originalSupervisorReview` |
| `zsjos_feedback_requirement_approval` | `2.0.0` | 可选部门负责人后董事长终审 | `departmentLeaderReview`, `chairmanReview` |
| `zsjos_sales_order_dual_approval` | `2.1.0` | 双中心并行会签 | `registrationReview`, `financeReview` |
| `zsjos_lead_appeal_review` | `2.0.0` | 单级多人或签 | `appealReview` |
| `zsjos_lead_transfer_request` | `2.0.0` | 单级多人或签 | `ownerManagerReview` |
| `zsjos_partner_withdrawal` | `2.0.0` | 单级多人或签 | `financeReview` |
| `zsjos_student_contact_extension` | `2.0.0` | 单级多人或签 | `deliverySupervisorReview` |
| `zsjos_media_reposition` | `2.0.0` | 单级多人或签 | `operatorReviewer` |
| `zsjos_media_rebind` | `2.0.0` | 单级多人或签 | `managerReviewer` |
| `zsjos_media_over_entitlement` | `2.0.0` | 单级多人或签 | `entitlementReviewer` |
| `zsjos_media_positioning_ip` | `2.0.0` | 单级多人或签 | `ipReviewer` |
| `zsjos_production_content_review` | `1.1.0` | 编导（发起人自选）后终审（角色） | `directorReview`, `finalReview` |

`zsjos_payment_refund_approval` 当前被退款服务启动并监听，但仓库没有对应 BPM 资产，启动请求也没有传入审批人，仓库代码与文档中无法确定权威审批人来源（实际租户配置尚未核对）。该流程不能通过名称推断财务角色、岗位或用户；补齐资产前必须先确定服务端候选人来源及任务 Key。
发布前必须确认每个租户已将 `eam:transfer:inspect` 授予实际资产管理员；该权限同时是服务端
解析资产管理员候选人的来源。管理端进入“审批管理 -> 流程模型 -> 导入模型”，上传该 JSON，
确认类型为 Simple、流程标识为 `eam_asset_transfer` 后发布并保持启用。旧 `eam-transfer` 不再
发布新版本，仅允许已有实例完成。

退役流程属于版本不可变规则的显式例外：经业务确认永久下线且确认无在途或历史实例后，可以从交付清单和基线中删除该流程资产，并通过 BPM 服务及 Flowable `RepositoryService` 边界级联删除模型、定义和部署。不得通过零散删除 `ACT_*` 表记录来退役流程。2026-08-26 已按此规则退役新媒体毕业流程 `zsjos_media_graduation`；学员联系延期流程 `zsjos_student_contact_extension` 独立保留。

学员联系延期流程还依赖 V095 创建的 `zsjos-system-form:student-contact-extension` 动态表单。Admin 创建模型时必须选择“流程表单”并绑定该租户下名称为“学员联系延期审批表单”的表单，不能留空或绑定普通 BPM 发起表单。字段由 ZSJOS 启动变量填充并设为只读；主管审批意见使用 BPM 任务的必填“审批意见”字段，状态监听器将其快照到延期记录的 `decisionReason`。

反馈需求审批资产为 `zsjos_feedback_requirement_approval/2.0.0/process-model.json`，最低迁移 V149，Process Key 为 `zsjos_feedback_requirement_approval`。ZSJOS 在启动前通过 System 公共 API 解析提交人部门负责人和唯一启用的 `boss` 角色用户，并将人员变量传入 BPM；资产不硬编码用户或角色 ID。hasDepartmentLeader=false 时跳过 `departmentLeaderReview`，随后进入 `chairmanReview`。业务键固定为 `feedback:{workOrderId}:round:{roundNo}`，每次驳回重提创建新的轮次和流程实例，不覆盖历史快照。发布后必须在反馈设置中绑定已发布且启用的定义；清单导入、发布和启用仍由管理员人工执行。


## 已有模型更新与验收

现有导入接口拒绝重复 Key。导出 `GET /admin-api/bpm/model/export?id=MODEL_ID` 的结果后，运行：
`python script/bpm/prepare_model_update.py --export existing-model.json --asset script/bpm/PROCESS_KEY/2.0.0/process-model.json --model-id MODEL_ID`。
工具只向标准输出生成待审阅 JSON，不访问网络、不发布流程。由当前模型管理员将结果作为 UTF-8 JSON 请求体发送到 `PUT /admin-api/bpm/model/update`，复核后单独发布。请求仍受既有 `bpm:model:update` 和模型管理员校验约束。保留原导出作为回退依据；不得调用清理模型或删除实例接口。

合并仅替换 `type=20`、`simpleModel` 和 `bpmnXml=null`，保留现有表单、表单 ID、分类、管理员、取消配置与发起范围。SIMPLE 中的业务审批节点保留候选策略 35，由 ZSJOS 服务在启动时按任务 Key 传入 `startUserSelectAssignees`；`coll_userList` 等引擎变量不作为可由用户填写的审批人来源。新环境的订单、申诉、延期模型必须先选择对应系统只读表单，表单 ID 不跨租户复制；其他模型的表单和分类同样需在目标环境复核。

爆款账号/内容审批使用服务端固定 Key `zsjos_viral_account_review` / `zsjos_viral_content_review`，各提供 `1.0.0/process-model.json`，内嵌表单在导入时由 BPM 创建。发布前必须在 BPM 配置租户审核角色，见 `viral-material-review-deployment.md`。其他素材类型保留管理员配置的流程 Key，不能仅凭静态清单断言已迁移。

生产内容批审使用服务端固定 Key `zsjos_production_content_review`，推荐资产为 `1.1.0/process-model.json`，最低迁移 V194，分类固定为 `zsjos_content_review`（由 `MaterialTenantInitializer` 在应用启动后为每个租户创建并启用）。流程骨架被业务表结构固定：`zsjos_content_review_batch_item` 每条只有一组编导结论和一组终审结论，因此必须恰好两个串行节点，且两个节点都必须是单人执行。服务端 `validTaskSequence` 会校验这一点，任何多人审批方式（或签、会签、依次审批）都会让提交失败并返回“生产内容审核配置尚未完成或已失效”。

**发布前后必须核对节点编码，而不是只看流程标识。** 设计器的“审批节点”默认生成 `Activity_<uuid>` 形式的元素编码和“审批人”名称；只有从 `1.1.0` 资产载入才会得到 `directorReview` / `finalReview`。用 Simple 设计器手工拖拽出这两个节点、或复制其它模型后只改流程标识，都会保留随机编码，`validTaskSequence` 随即失败，运营提交统一报“生产内容审核配置尚未完成或已失效”。发布后按部署产物复核：

```sql
-- 期望只剩 StartUserNode / directorReview / finalReview 三个 userTask，且无 Activity_ 前缀
SELECT ID_, KEY_, VERSION_, SUSPENSION_STATE_, CATEGORY_
  FROM ACT_RE_PROCDEF WHERE KEY_ = 'zsjos_production_content_review';
```

同时确认扩展表 `bpm_process_definition_info.category` 取 `zsjos_content_review`（服务端按该行判定分类，Flowable 的 `ACT_RE_PROCDEF.CATEGORY_` 仅作展示），以及同一 `KEY_` 下只有一行 `SUSPENSION_STATE_=1`。

两个节点的审批人来源不同，这是有意的：

- `directorReview` 保持候选策略 35（发起人自选）。责任编导由服务端按 `content_director_operator` 关系解析——以提交运营为 `target_user_id` 反查唯一启用关系的 `source_user_id`，要求编导与运营账号均启用，并要求批次内每个账号的 `director_user_id` 与之一致。**在 BPM 为该节点配置的审批人不会生效**，会被启动时传入的 `startUserSelectAssignees` 覆盖。要改变编导归属请维护关系数据，不要改模型。
- `finalReview` 使用候选策略 10（角色），`candidateParam` 在资产中为占位值 `0`。导入后必须在 BPM 绑定本环境的终审角色，并将多人审批方式设为“随机挑选一人审批”。服务端不传该节点的审批人，完全按模型解析。总监岗位补齐前可先绑董事长角色，补齐后改绑并重新发布，不动流程结构与流程标识。

`1.0.0` 保留为兼容版本但不再推荐：它把 `finalReview` 也配成策略 35，而服务端只传 `directorReview` 的审批人，发起时必然因终审人缺失而失败。已按 `1.0.0` 导入的环境需按“已有模型更新与验收”的流程更新为 `1.1.0` 并重新发布；运行中的旧实例保留原定义继续走完。

收录到素材库的“生产内容”类型（`production_content`）由 `MaterialTypeServiceImpl.ensureDefaultSchema` 在启动时自动建模并发布，字段全部非必填——收录只在终审通过时由服务端写入，没有人工补填入口，任何必填字段都会让缺少来源的那条内容收录失败而不是阻塞批审。内容与账号快照到模板字段的映射是服务端固定契约，`ensureDefaultConfig` 会为映射仍为空的存量租户补齐，已由管理员配置过的映射不覆盖。退款审批尚无仓库资产及明确候选人契约，应保持“流程不可用”的失败行为，待业务规则确定后补齐；不得配置自动通过以绕过缺项。

文本校验兼容既有原始字节、LF、CRLF 哈希；实际内容变化仍失败。历史版本元数据保持不变，新版本使用 UTF-8/LF。新模型在 Admin 中编辑/保存/导出后须复核任务 Key；在 Workbench 中验证待办、业务表单及批准/拒绝投影；运行中旧实例保留原定义并继续验证完成路径。

## 成交订单双中心通过意见选填（所有审批轮次）

成交订单的 `registrationReview`、`financeReview` 普通节点不区分定义版本，通过意见统一选填。BPM 通过公共业务策略扩展解析有效意见要求，ZSJOS 仅对成交订单上述节点覆盖；任务查询和实际执行使用同一规则。主管子任务仍必填，驳回原因仍必填，其他流程沿用原配置。不得通过补写“同意”绕过校验。

部署本次后端后，双端继续消费 `approvalReasonRequired`；已有待审订单无需发布模型、迁移、重启或修改定义即可空意见通过。历史记录与已填写意见保持原样。推荐资产 2.1.0 仍保留供后续新定义发布，旧资产和 SHA-256 不改写，模型发布不是本次行为生效的前提。

部署验收使用受控旧定义与新定义订单，验证两中心空意见通过、空白驳回拒绝、主管空意见拒绝、权限/会签条件保持不变，并核对运行实例定义 ID 未改变。真实账号验收独立于隔离引擎测试。

恢复必填规则须回退或调整后端业务策略，仅发布必填模型不能覆盖当前业务策略；回退不会补写历史意见或改写实例。模型的独立发布仍按本文件版本化流程记录与验收。
