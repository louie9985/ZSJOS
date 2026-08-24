# ZSJOS BPM 版本化资产发布

`script/bpm/manifest.json` 是 ZSJOS 流程资产的交付清单：成交双中心审批、客资三级申诉、公海正式转派、提现财务审批和学员联系延期审批。仓库语义版本与 Flowable 自动生成的定义版本分别登记，不能假设编号相同。已发布目录不可覆盖；流程变更必须新增语义版本目录并更新清单与 SHA-256。

资产版本必须使用严格的 SemVer，且目录版本、文件路径和清单登记的 Process Key 必须一致。发布前运行 `python script/bpm/validate_manifest.py`，确认 XML、Process Key、任务 Key、会签/处理人变量、BPMN DI、推荐版本和 SHA-256 全部通过。CI 会以目标分支基线校验已发布资产的路径和校验和不可变；本地可使用 `--base-ref <ref>` 执行同样的基线检查。管理员在 BPM 管理页面人工载入清单推荐文件并发布，不启用应用启动自动部署。

每次发布记录资产版本、SHA-256、Flowable 定义 ID、Flowable 版本、部署时间和操作人。发布后核对 Process Key 和任务 Key，并以受控业务请求创建一个新实例验证待办。新定义只服务新实例，不迁移、不重启、不改写在途实例。

学员联系延期流程还依赖 V095 创建的 `zsjos-system-form:student-contact-extension` 动态表单。Admin 创建模型时必须选择“流程表单”并绑定该租户下名称为“学员联系延期审批表单”的表单，不能留空或绑定普通 BPM 发起表单。字段由 ZSJOS 启动变量填充并设为只读；主管审批意见使用 BPM 任务的必填“审批意见”字段，状态监听器将其快照到延期记录的 `decisionReason`。
