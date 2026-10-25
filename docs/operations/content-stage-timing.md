# 内容审批阶段时长：审计、补全与恢复

## 边界与前提

工具为 `script/sql/mysql/repairs/content_stage_timing.py`，默认只读，仅处理租户 1。复用现有账号起点、版本字段和审批 JSON，不改 schema、历史迁移、版本账本、角色授权或审批状态。真实写入、恢复、部署和服务操作须分别获得针对具体环境及影响的授权；本地隔离验证不代表获得授权。

先部署维护阶段起点的后端，再发布前端。缺失旧数据仍可正常读取，展示“阶段时长未记录”。不要先用今天或账号创建时间填空。

## 证据和数据范围

按内部账号 ID 关联诊断，不按姓名或平台账号匹配。诊断链必须以唯一启动诊断开始，记录阶段与 stage 快照一致，时间、ID、结果版本顺序一致。相同时刻的诊断、删除的诊断、缺记录/阶段、损坏 JSON、来源冲突或未来记录均不恢复。连续相同 code 不重置起点，返回旧 code 则建立新段。现有历史无法证明系统外或未记录的更早阶段事实，本工具仅恢复可验证的系统诊断链。

当前账号还要求最终诊断阶段与账号阶段一致。已有非空起点不覆盖。历史审批按自身 submittedAt 和冻结阶段定位当时区段；与诊断处于同一数据库秒的提交跳过。草稿不修复，缺提交时间有单独跳过原因。核验批次账号集合及已有学员关联，不重建账号集合、标签或其他历史资料。只补全部计时字段均为空的快照；部分已有值留待人工核查，兼容旧 context.account 并保持双投影一致。

账号恢复仅填起点及 detail_values_json 中技术保留的 `_stageTimingRecovery` 来源标记；审批仅增加四个计时字段。每个实际变化的账号/批次版本加一。诊断记录本身保持不变。审批来源固定为 HISTORY_RECONSTRUCTED；审批引用的证据 ID 只包含提交前已有记录。

## 运行

从受保护的本地环境或机密管理工具提供 MYSQL_HOST、MYSQL_PORT、MYSQL_USER、MYSQL_PASSWORD、MYSQL_DATABASE；不得把凭证写入命令、文档或日志。运行前明确目标端点和数据库。

```powershell
python script/sql/mysql/repairs/content_stage_timing.py --expected-database <目标库> --output <新证据目录>
```

默认 START TRANSACTION READ ONLY。audit.json 只包含内部关联 ID、证据 ID、拟补计时、跳过原因及 SHA256，不含账号名称、联系方式或诊断内容。目录必须为新目录；Linux 使用 0700/0600，Windows 在写文件前移除继承并只授予当前用户权限。数据库连接采用 utf8mb4 和 +08:00。

人工审核目标端点、账号/批次数量、拟补字段和审计摘要并获得执行确认后：

```powershell
python script/sql/mysql/repairs/content_stage_timing.py --expected-database <目标库> --output <另一新目录> --apply --approved-plan-sha256 <审计SHA256>
```

摘要覆盖端点、数据库、完整来源和目标前像及变更值。执行采用 REPEATABLE READ，按账号、诊断、批次顺序锁定租户 1 的审计范围，核验完整摘要、版本后才写入；同范围业务操作可能短暂等待。设置 10 秒锁等待，锁失败或任何来源变化须回滚、重新审计，不自动重试旧清单。

写入前保存 before.json，写入及后置查询验证后保存 before-verified.json，再提交事务。备份含敏感前像，必须存放在访问受控目录，禁止提交 Git、复制到报告或转发。任何失败均回滚业务 DML；目录和备份保留供核查。若提交连接中断，先只读核查实际值，不根据备份文件存在判断已成功。再次审计应没有重复候选。

## 恢复

恢复仅使用同次修复生成的 before-verified.json，先核实修复实际已提交。以文件 SHA256 作为恢复审批摘要：

```powershell
(Get-FileHash <before-verified.json路径> -Algorithm SHA256).Hash.ToLowerInvariant()
python script/sql/mysql/repairs/content_stage_timing.py --expected-database <目标库> --output <恢复新目录> --restore <before-verified.json路径> --approved-plan-sha256 <文件SHA256>
```

恢复绑定相同主机、端口和数据库；逐行锁定并校验修复后完整行摘要。任一行后来有任何变化，整次恢复拒绝并回滚，转人工核查。恢复只还原本次涉及字段，版本再加一，不回退版本、不整行覆盖后续数据。自动 update_time 仍由数据库维护。恢复后重新查询字段及中文 HEX 并审核结果。

## 验证入口与本地记录

```powershell
python script/sql/mysql/repairs/test_content_stage_timing.py
python script/sql/mysql/repairs/test_content_stage_timing.py --mysql
```

--mysql 只连接固定本地 yudao-mysql，从实际表结构创建独立 stage_timing_verify_时间戳 库，保留库和合成证据；不写业务库。覆盖首次执行、重复零变化、DML 中途失败回滚、来源变化拒绝、受控恢复、恢复目标变化拒绝、租户/删除范围和中文 HEX。

2026-10-21 本地只读审计：100 个账号、136 个审批批次为恢复候选；363 条账号或批次账号项被跳过。此数量仅是当次本地快照，不代表远程环境，也不代表已经修复。证据在访问受控的本地临时目录，未纳入版本库。真实数据补全、后端部署、服务重启及真实账号验收尚未执行。
