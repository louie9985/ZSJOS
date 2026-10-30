# 众师简回收客资身份回填（2026-10-30）

## 范围与授权

仅测试环境 `zsjos-mysql-1 / zsjos / tenant 1` 中以下五条客资：
LD202606180010、LD202606250017、LD202606260025、LD202606260047、LD202606280011。
它们当前均为 submitted / recycle_pending，2026-09-22 的最近回收流水缺少身份。
第一条的紧邻历史是导入 other 基线，其余四条的紧邻 accept 流水也缺少身份。

用户明确授权本次按原负责人账号部门判定：“销售部门就是销售，学生交付中心就是教务”。
核查五条原负责人账号均属于“销售转化一部 → 销售转化中心”，因此全部回填 sales。
部门值通过 UTF-8 连接及 HEX 核实。这是对禁止按当前部门推断历史身份规则的一次性、
有明确用户决策的数据修复例外；不能当作已经找回事件发生时的身份快照，
不能推广为运行时判断或其他历史数据的自动回填规则。

## 变更与执行顺序

脚本：`script/sql/mysql/repairs/repair_recycled_identity_20261030.py`。
只修改五条固定的最新 recycle 流水的 owner_identity_snapshot、updater、update_time。
专用 updater 标记记录修复来源；原始事件时间、原因、操作人及更早历史保持不变。
Lead 当前 owner_identity 仍为空，这是无主回收状态的正常设计；不恢复负责人、不重新分配，
不改变 Lead 状态、版本、任务、订单、权限或通知。不处理另五条已归属但缺少当前身份的客资。

依赖现有 MySQL 8、pymysql、仓库连接器与用户/部门记录，无依赖新增、DDL 或版本台账写入。
执行前锁定目标 Lead 和历史，核对业务编号、技术键、原负责人、部门、状态、版本及最新回收周期；
重复执行只接受本修复标记的 sales 值，其他身份或业务变化拒绝。只更新目标表，无插入或删除顺序。
在写入前持久化私有备份；全部五条在同一事务内提交。备份仅含历史前后镜像、必要部门证据及
完整 Lead 行的哈希，不输出客户资料或账号敏感字段。

```bash
PYTHONDONTWRITEBYTECODE=1 python3 script/sql/mysql/repairs/repair_recycled_identity_20261030.py test
PYTHONDONTWRITEBYTECODE=1 python3 script/sql/mysql/repairs/repair_recycled_identity_20261030.py apply --evidence-dir /opt/zsjos-runtime/backups/recycled-identity-20261030
PYTHONDONTWRITEBYTECODE=1 python3 script/sql/mysql/repairs/repair_recycled_identity_20261030.py verify --evidence-dir /opt/zsjos-runtime/backups/recycled-identity-20261030
```

test 必须在本次修复前运行：复制相关记录到连接私有的临时表，执行同一修复函数，
覆盖首次 5 条、重复 0 条、写入第 1/3/5 条失败回滚、已完成一条时恢复剩余四条，以及
身份冲突、最新动作改变、部门改变、Lead 版本改变的拒绝。临时表随连接关闭消失。
再次 apply 必须使用新的私有证据目录；在业务记录没有后续变化时返回零更新。

提交前比较完整历史前后镜像的字段白名单及 Lead 哈希，检查中文原因 HEX；提交后新连接复核。
后端仍按 LeadTeamTransferPolicy 的最新回收快照解析身份；这次修复不改变前后端协议。
真实转派、恢复负责人仍须满足其他权限、接收资格和状态前置条件。

## 恢复限制

事务提交前任何失败均回滚；提交后的恢复须单独核对并授权，使用私有 before.json 对五条历史
执行受保护的补偿更新，不能覆盖后续业务写入。禁止无条件重放备份或修改历史迁移与台账。
此脚本不提供自动回滚业务状态的入口。

## 验证与交付

执行结果由同名环境工作流记录 `handoff/test-recycled-identity-20261030.md` 登记。
数据库检查能证明快照满足身份校验，不能代替实际账号下的后续转派操作或浏览器验收。

2026-10-30 09:58:36（北京时间）已提交五条历史更新，Lead 更新零条。
MySQL 临时表首轮、重复、三个故障回滚、部分恢复及四类拒绝验证全部通过；
提交后新连接与完整预期后镜像一致，实际再次执行零更新。
独立扫描当前全部 11 条回收待处理客资，全部可解析为 sales，身份不明零条。
五条身份 HEX 与原始中文原因逐条 HEX 校验通过，Lead 完整行哈希保持不变。
另五条已归属身份缺失记录不在本次范围，数量仍为五条。
证据分别保存在上述私有目录及其 `-repeat` 同级目录。
没有执行浏览器或真实转派动作；未声称其他接收资格、权限或状态条件已通过。
