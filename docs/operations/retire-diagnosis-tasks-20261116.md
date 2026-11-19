# 清理现有账号诊断待办（2026-11-16）

UTF-8。仅适用于已确认的 test 环境、`zsjos` 数据库、租户 1。本次只取消现有任务，
**不重启后端、不部署、不停用自动生成**；后续周期仍可产生新任务。

用户最初确认清理未完成任务并停止生成，随后明确收窄为“不要重启后端，先只清理任务”。
本记录以后一条指令为准，停发代码已撤回。运行包、服务配置和前端均保持原状。

## 数据范围和写入顺序

冻结的前镜像包含 188 个账号的 986 条未完成 `media_account_diagnosis` 任务：
7 天 373 条、14 天 305 条、28 天 308 条。只更新 `zsjos_business_task` 中这些既定 ID，
将 `pending` 改为 `cancelled`，填写取消时间、原因，递增版本及更新审计元数据。
1,167 条已完成任务、333 条原已取消任务、其他业务任务及其他租户不变。

事务内按任务 ID 升序锁定并比较完整前镜像；每条取消后插入一条同事务的
`zsjos_business_event`，人类操作者为 NULL，记录 `pending → cancelled`。
不删除或插入任务，不改任务账号、负责人、截止时间、载荷、幂等键；不修改账号档案历史、
诊断统计、固定成员或既有通知阶段。没有 DDL、迁移或版本台账、角色授权变更。
任务和事件表不存在外键依赖；取消与审计作为一个事务提交，任何执行或校验异常均回滚。

## 前提、执行与重复性

执行器：[retire_diagnosis_tasks_20261116.py](../../script/sql/mysql/repairs/retire_diagnosis_tasks_20261116.py)。
复用既有 PyMySQL 连接及私有证据工具，不新增依赖。只允许解析为 test 的环境，
连接限定当前本机 MySQL 容器和数据库；凭证不写入日志。MySQL 连接显式使用
`utf8mb4`、`SET NAMES utf8mb4` 和北京时间会话。

私有证据根目录为 `/opt/zsjos-runtime/backups/retire-diagnosis-tasks-20261116/`，
目录权限 0700、文件 0600。按以下顺序操作；已存在的证据目录不覆盖：

```bash
python3 script/sql/mysql/repairs/retire_diagnosis_tasks_20261116.py \
  --evidence-dir /opt/zsjos-runtime/backups/retire-diagnosis-tasks-20261116/prepared
python3 script/sql/mysql/repairs/retire_diagnosis_tasks_20261116.py \
  --plan-dir /opt/zsjos-runtime/backups/retire-diagnosis-tasks-20261116/prepared \
  --evidence-dir /opt/zsjos-runtime/backups/retire-diagnosis-tasks-20261116/applied --apply
python3 script/sql/mysql/repairs/retire_diagnosis_tasks_20261116.py \
  --plan-dir /opt/zsjos-runtime/backups/retire-diagnosis-tasks-20261116/prepared \
  --evidence-dir /opt/zsjos-runtime/backups/retire-diagnosis-tasks-20261116/verified --verify
```

准备阶段保存完整前镜像与固定计划，并在同结构 MySQL 会话临时表上执行实际写入器。
临时任务、事件表遮蔽同名真实表，演练不改真实数据。应用阶段重做演练，真实前置状态
一致后才锁行写入。独立连接逐字段验证提交后镜像、保护表及中文原因的 UTF-8 HEX，
原计划重放应写入 0 条。重复执行必须复用原计划及幂等事件；已有取消后被修改的任务拒绝覆盖。
不能把后续新增任务自动纳入原清理计划。

## 验证和恢复限制

MySQL 8.4.11 演练覆盖：首次取消 986、重复 0、在第 1/493/986 条注入失败后全部回滚、
部分提交 3 条后恢复其余 983 条、取消前后并发版本变化拒绝覆盖、补偿恢复 986 条且保留审计。
保护比较包括完整任务表、账号档案条目、诊断统计、固定成员及通知阶段表。

提交前异常由事务回滚；若提交结果不确定，先用原计划 `--verify` 检查任务及事件，
不重新生成计划。提交后的业务恢复需要另外确认，使用私有原始镜像逐行比较当前状态后，
以补偿更新恢复原状态和取消字段、递增版本并追加反向审计，不删除原取消事件。
执行器仅在临时表演练补偿路径，没有对真实库开放恢复开关。

实际取消结果与独立复核记录在本文件交付记录中。未使用登录身份执行 HTTP/浏览器验收；
Workbench 诊断待办和 Vue Admin 今日待办均查询服务端 `pending` 状态，取消任务不再符合待办条件。
旧调度仍每五分钟运行，保留的幂等键避免同周期原任务重建，但新周期仍可能生成新任务。

停发候选包及 30 项隔离测试证据因范围收窄仅保留在私有目录，未发布、未替换运行包，
不作为本次数据清理交付内容。


## 实际交付

北京时间 2026-11-16 11:54:46–11:54:49，已在同一事务取消全部 986 条目标任务并追加
986 条 SYSTEM 状态事件。独立连接及第二次独立复核均通过：待处理 0、已取消 1,319
（原 333 加本次 986）、已完成 1,167；原计划重放 0 写入，完整保护表和非目标任务均一致，
中文取消原因和审计原因 UTF-8 HEX 全部通过。证据位于上述私有目录的
`prepared/`、`applied/`、`verified/`。

后端 MainPID 仍为 2597740，启动时间仍为 2026-11-06 00:41:57 CST，部署包 SHA-256
与清理前一致。没有重启、发布或修改运行配置；停发源码变更已完全撤回。
Python AST、UTF-8、文档链接、空白及作用域差异检查通过。HTTP/浏览器验证未执行，
本次结论基于实际数据库提交、独立回读和现有两端服务端待办筛选契约。
