# 中世健公海正式转派审批推进修复

适用范围：租户 1 的 `zsjos_lead_transfer_request` 模型，以及内部转派申请记录 1、2。
本文不授权模型发布、实例恢复、服务重启或账号权限调整。禁止直接更新 ACT_* 表。

## 原因与修正

旧部署的主管节点 ownerManagerReview 使用策略 60 / `${coll_userList}`，业务启动只传入
PROCESS_START_USER_SELECT_ASSIGNEES 中冻结的主管，缺少旧表达式变量。
发起人自动通过先写状态再完成任务，后续候选人解析失败，造成“通过但仍是待办”。
源码 2.0.0 模型已使用策略 35。真实 Spring 事务提交回调与 Flowable 测试验证该策略可推进，
因此本次不修改通用事务代码，也不通过过滤待办隐藏异常。

prepare_transfer_model_repair.py 仅修改主管策略为 35、清空 candidateParam 及派生 bpmnXml，
其余管理员配置完整保留。拒绝非预期模型、节点、条件分支或候选人策略。
不新增表、字段、迁移、依赖或权限，不重新计算历史主管。

## 已准备的证据

当前用户 TEMP/zsjos-transfer-starter-20261023：
- model-before.json：当前模型预映像；model-update.json：最小修正载荷；model-revision.json：版本与部署信息。
- 1/target-recovery-preflight.json 与 2/target-recovery-preflight.json：精确实例、任务、原定义 XML SHA256、冻结主管及阻断项。
- maintenance-classes：已编译维护入口；javac.args：编译参数。均不含凭据。

2026-10-23 只读结果：申请 2（KZ202610191403550027）静态预检为 CANDIDATE。
申请 1（LD202607080001）为 BLOCKED：现负责人不同于申请时原负责人。
申请 1 不得恢复或重新选主管，需要单独业务决定；本次保持原状。
CANDIDATE 不等于运行引擎校验通过，也不构成执行授权。

## 执行前提与顺序

1. 获得对本地 ruoyi-vue-pro 中模型发布及申请 2 恢复的明确授权，并由用户指定审计操作账号。
   账号必须启用、属于租户 1、是模型管理者并具有 bpm:model:update、bpm:model:deploy、bpm:task:update。
   冻结主管必须启用并具备 bpm:task:update；实际审批仍由 BPM 所有权及业务规则独立约束。
   不得自行选择账号或调整角色授权。
2. 再次只读预检并导出模型，重新生成更新载荷；比较版本、原定义 XML 指纹和冻结主管。
   保存目标数据库备份，确认恢复方式；不要把状态标记当作成功依据。
3. 在目标现有应用上下文调用 TransferMaintenanceRunner 的 preflight。不得启动第二个应用。
   模型载荷必须使用 BpmModelService.exportModel 的结果；SQL 提取包是审阅依据，最终须与服务导出完整比较。
4. rehearse 模式在原引擎事务中完成旧发起人任务、验证仅生成冻结主管任务，再在 beforeCommit 主动回滚。
   必须看到 REHEARSED_AND_ROLLED_BACK 且原实例恢复 READY。任何异常停止，不继续发布。
5. publish 模式通过既有模型服务 updateModel/deployModel 发布最小修正。
   它锁模型并比较完整预映像、验证载荷只改候选人契约，确认新定义策略 35。
   新定义仅影响新启动；原实例继续使用旧定义。
6. recover 模式仅允许白名单实例，要求成功演练和发布证据，检查有效新定义策略 35。
   同一引擎事务中锁定并复核 Lead 和申请、原负责人、pending 状态、未结束状态及冻结主管。
   复用 BpmStartUserTaskRecovery 校验原定义指纹、简单拓扑、SYSTEM 自动处理快照、任务状态、
   主管历史为空、无引擎作业；只补 coll_userList，然后完成原任务。提交前再次检查业务状态和负责人。
7. 确认原发起人历史结束、唯一主管任务可处理、申请仍 pending 且负责人未变化。
   重复调用恢复应返回 ALREADY_RECOVERED，不重复评论或创建任务。

维护入口属性：directory（该目标专用证据目录）、result（本次输出文件）、mode、operator、classes。
目标目录需包含 model-before.json、model-update.json 和 target-recovery-preflight.json；各模式输出分别
使用 preflight-result.txt、rehearse-result.txt、publish-result.txt、recover-result.txt。
TransferMaintenanceAgent 使用现有应用类加载器与 Spring 上下文，只加载维护 runner 与既有恢复 helper；
复用 AppealMaintenanceAttach 传入 PID、审阅过的 agent JAR、属性文件。agent JAR 的 Agent-Class 为
maintenance.TransferMaintenanceAgent。禁止注册后台任务、开放恢复 HTTP 接口或自动启动恢复。
编译时使用现有 BPM 与各模块 target/classes 及 Maven 已解析依赖，UTF-8 编码；不安装新依赖。

## 验收与回滚限制

- 隔离 Flowable/H2：旧策略失败、实际自动通过回调、新策略推进、保留旧实例、恢复幂等、权限/租户/
  快照变化拒绝、后置条件失败回滚及业务复核失败零变更。
- 转派业务：通过触发正式转派；拒绝/取消不转派；重复终态事件无副作用。
- 工作台与 Vue 管理端均消费既有 BPM todo/done 和流程详情接口。发布后用真实账号检查本人发起人待办消失、
  已办记录、主管待办、进度和操作按钮；本地测试不等于真实账号验收。
- 恢复不代替主管决定，不重建原实例，不改历史自动审批记录。
  发布后需要撤回时通过模型服务审阅后重新发布旧配置；这不能撤回已推进的实例。
  恢复事务提交前异常可回滚，提交后不得直接反向修改引擎表；另行评估引擎补偿和备份恢复影响。

## 本轮验证限制

Python 6 项测试通过；维护入口 javac 编译通过；BPM 7 项真实引擎测试通过。
ZSJOS 联编被既有 ZsjkqtService.java:226 对 tools.jackson.databind.JsonNode.isContainerNode() 的调用阻断，
转派服务测试未执行，未修改该无关源码。目标运行引擎演练、发布、恢复和两端真实浏览器验收均尚未执行。
