# 兼职客资指定分配与教务接单

## 配置

在已有用户关系场景管理中打开“兼职客资指定分配”（`partner_lead_specified_assignment`），选择兼职、唯一接单人员和销售／教务承接身份。Vue 管理端和 React 工作台使用相同接口与权限。来源主体 `sourceType=partner`，来源 ID 为 Partner ID；旧场景默认 `system_user`。场景创建后主体类型不可修改，禁止把兼职 ID 解析为系统员工。

目标账号须启用并具有 `zsjos:lead:accept`。身份由关系配置明确记录，不从部门、岗位或角色名推断。关系保存支持追加、替换、移除；替换为空可清空。保存锁定当前租户的场景行，检查最终有效人员最多一位，同一事务修改并记录日志。已有其他场景继续支持多目标关系。

管理员还需按业务需要配置本人客资查询、详情、跟进、判定、订单创建及审批相关权限。配置关系不授予任何权限；SQL 不写角色授权或真实人员关系。

## Partner API

`GET /part-api/zsjos/lead/assignment-options` 返回 `{configured,specifiedAvailable,reason}`。场景未启用或无关系时 configured=false，隐藏分配方式；存在失效或多人关系时 configured=true、specifiedAvailable=false，显示安全原因。返回体不包含目标人员、部门或身份信息。请求失败必须展示重试，不能视作未配置。

`POST /part-api/zsjos/lead/create` 接受 `dispatchMode=auto|specified`，缺省为 auto。Partner 客户端禁止传 specifiedSalesUserId；specifiedOwnerIdentity 无条件丢弃，由后端解析当前关系填充。自动分配沿用销售轮询；指定分配保存 pendingOwnerIdentity 快照并创建无截止时间的待接单任务。创建响应隐藏 pendingAssigneeUserId。

错误码：1045090001 未配置/场景停用；1045090002 多人配置；1045090003 接单人停用、失效或撤权；1045090004 承接身份缺失或不合法；1045090005 客户端传入目标。前端保留表单和附件、展示服务端安全文案并刷新配置；只有用户明确选择才切换自动分配，不静默改派。

## 接单、复核与历史

指定客资可稍后接单，不可拒单，无自动超时转派；教务使用原接单弹窗。接单重新校验账号和接单权限，归属、接单历史、后续跟进及成交使用派单身份快照。自动轮询资格与指定接单权限分开，教务不因新增权限进入销售轮询池。教务本人继续完成跟进、判定、成交，原 BPM 审批边界不变。

查重复核 submissionSnapshot 保留后端解析的目标和身份；批准新建时校验原目标仍可承接，不读取当前关系替换历史目标。目标不可用则复核操作报错并保持原状态，管理员恢复目标资格后重试。重复激活仍使用原客资归属。成功提交及待复核幂等重试不因配置变化重派；仍检查当前 Partner 身份与账号状态。

H5 仅在提交环节隐藏接单人员，后续详情和消息保留已有隐私投影。历史记录不回填身份，老员工场景不改变候选规则。

## 数据库与验证

V263 依赖 V262 后的关系场景、关系、Lead 和版本表，新增 source_type、关系 owner_identity、Lead pending_owner_identity，来源岗位允许空值。按已有场景租户初始化空 Partner 场景，不修改管理员已有配置，不创建任何业务选项或真实关系。先执行 SQL 再加载新版后端与前端；旧版应用不应承接新场景指定请求。回滚应用时保留新增列和历史快照，管理员停用场景，不提供破坏性回滚。

验证入口：`python script/sql/mysql/tools/test_partner_specified_assignment.py`（可选 `--sync-local` 同步本地开发库）、`python script/verify-partner-assignment-ui.py`。浏览器脚本使用隔离的合成 API 响应，不写真实账号或客资；真实环境权限与完整成交链路须另行验收。
