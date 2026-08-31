# ZSJOS BPM 版本化资产发布

`script/bpm/manifest.json` 是业务流程资产的交付清单，包括 ZSJOS 业务流程和 EAM 资产流转审批。仓库语义版本与 Flowable 自动生成的定义版本分别登记，不能假设编号相同。已发布目录不可覆盖；流程变更必须新增语义版本目录并更新清单与 SHA-256。

资产版本必须使用严格的 SemVer，且目录版本、文件路径和清单登记的 Process Key 必须一致。BPMN 资产使用 `process.bpmn20.xml`，Simple 设计器资产使用可由“导入模型”直接上传的 `process-model.json`。发布前运行 `python script/bpm/validate_manifest.py`，确认资产格式、Process Key、任务 Key、候选人变量、推荐版本和 SHA-256 全部通过；BPMN 资产还会检查 BPMN DI。CI 会以目标分支基线校验已发布资产的路径和校验和不可变；本地可使用 `--base-ref <ref>` 执行同样的基线检查。管理员在 BPM 管理页面人工载入清单推荐文件并发布，不启用应用启动自动部署。导入模型时，后端忽略文件中的管理员用户编号并将当前导入人设为模型管理员，避免跨环境复制用户关系。

每次发布记录资产版本、SHA-256、Flowable 定义 ID、Flowable 版本、部署时间和操作人。发布后核对 Process Key 和任务 Key，并以受控业务请求创建一个新实例验证待办。新定义只服务新实例，不迁移、不重启、不改写在途实例。

EAM 资产流转推荐资产为 Simple 模型 `eam_asset_transfer/1.0.0/process-model.json`，最低数据库版本 EAM V010。模型分类使用服务端 BPM 分类编码 `general-module`；导入模型时必须选择当前租户中存在且启用的分类，不能依赖跨环境复制的分类 ID。任务以稳定 task key 线性编排，并通过节点跳过表达式实现类型分流：领用/借用执行申请部门负责人并跳过调拨节点；调拨跳过申请部门负责人，执行转出部门负责人，并在部门不同时执行接收部门负责人；随后进入资产管理员确认和接收人签收。
发布前必须确认每个租户已将 `eam:transfer:inspect` 授予实际资产管理员；该权限同时是服务端
解析资产管理员候选人的来源。管理端进入“审批管理 -> 流程模型 -> 导入模型”，上传该 JSON，
确认类型为 Simple、流程标识为 `eam_asset_transfer` 后发布并保持启用。旧 `eam-transfer` 不再
发布新版本，仅允许已有实例完成。

退役流程属于版本不可变规则的显式例外：经业务确认永久下线且确认无在途或历史实例后，可以从交付清单和基线中删除该流程资产，并通过 BPM 服务及 Flowable `RepositoryService` 边界级联删除模型、定义和部署。不得通过零散删除 `ACT_*` 表记录来退役流程。2026-08-26 已按此规则退役新媒体毕业流程 `zsjos_media_graduation`；学员联系延期流程 `zsjos_student_contact_extension` 独立保留。

学员联系延期流程还依赖 V095 创建的 `zsjos-system-form:student-contact-extension` 动态表单。Admin 创建模型时必须选择“流程表单”并绑定该租户下名称为“学员联系延期审批表单”的表单，不能留空或绑定普通 BPM 发起表单。字段由 ZSJOS 启动变量填充并设为只读；主管审批意见使用 BPM 任务的必填“审批意见”字段，状态监听器将其快照到延期记录的 `decisionReason`。

反馈需求审批资产为 `zsjos_feedback_requirement_approval/1.0.0`，最低迁移 V149，Process Key 为 `zsjos_feedback_requirement_approval`。ZSJOS 在启动前通过 System 公共 API 解析提交人部门负责人和唯一启用的 `boss` 角色用户，并将人员变量传入 BPM；资产不硬编码用户或角色 ID。hasDepartmentLeader=false 时跳过 `departmentLeaderReview`，随后进入 `chairmanReview`。业务键固定为 `feedback:{workOrderId}:round:{roundNo}`，每次驳回重提创建新的轮次和流程实例，不覆盖历史快照。发布后必须在反馈设置中绑定已发布且启用的定义；清单导入、发布和启用仍由管理员人工执行。
