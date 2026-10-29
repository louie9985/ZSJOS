# 内容审批阶段时长：审计、补全与恢复

## 边界与前提

工具为 `script/sql/mysql/repairs/content_stage_timing.py`，默认只读，仅处理租户 1。复用现有账号起点、版本字段和审批 JSON，不改 schema、历史迁移、版本账本、角色授权或审批状态。真实写入、恢复、部署和服务操作须分别获得针对具体环境及影响的授权；本地隔离验证不代表获得授权。

先部署维护阶段起点的后端，再发布前端。缺失旧数据仍可正常读取，展示“阶段时长未记录”。不要先用今天或账号创建时间填空。

## 证据和数据范围

按内部账号 ID 关联诊断，不按姓名或平台账号匹配。诊断链必须以唯一启动诊断开始，记录阶段与 stage 快照一致，时间、ID、结果版本顺序一致。不同诊断事件发生于相同时刻、删除的诊断、缺记录/阶段、损坏 JSON、来源冲突或未来记录均不恢复。连续相同 code 不重置起点，返回旧 code 则建立新段。现有历史无法证明系统外或未记录的更早阶段事实，本工具仅恢复可验证的系统诊断链。

周期综合诊断按 [综合表契约](../api/weekly-account-diagnosis.md) 一次更新账号、追加多条模板历史；附属记录可与主记录同秒或稍后写入并共用结果版本。工具仅在完整模板集合、节点、周期、账号/租户/操作人、阶段和完整快照、weeklyFingerprint、附属指纹及由主幂等键派生的附属幂等键全部吻合时，将其识别为同一次事件，起点取主记录时间，保留全部证据 ID。缺少附属记录或任一关联冲突仍拒绝，不修改诊断历史。

当前账号还要求最终诊断阶段与账号阶段一致。已有非空起点不覆盖。历史审批按自身 submittedAt 和冻结阶段定位当时区段；与诊断处于同一数据库秒的提交跳过。草稿不修复，缺提交时间有单独跳过原因。核验批次账号集合及已有学员关联，不重建账号集合、标签或其他历史资料。可补全部计时字段均为空的快照，或仅有整数 sStageDurationAsOf 且严格等于 submittedAt 毫秒值的快照：后者是新版后端在起点未知时的正常输出，补全时保留该截止值。其他部分已有值、截止值不符仍留待人工核查；兼容旧 context.account 并对双投影执行相同检查。

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

测试服务器使用 `python3 script/sql/mysql/repairs/test_content_stage_timing.py --mysql --test-environment`。必须按环境标识解析为 test；从固定 `zsjos-mysql-1` 的受保护配置读取连接，源库 `zsjos` 仅用于复制表结构，所有样例写入独立 `stage_timing_verify_*` 库。测试证据保存于权限为 0700 的系统临时目录，不写入仓库。纯测试额外覆盖综合诊断关联、同秒/跨秒附属记录、缺失/冲突拒绝和两种快照投影的截止值一致性。

2026-10-21 本地只读审计：100 个账号、136 个审批批次为恢复候选；363 条账号或批次账号项被跳过。此数量仅是当次本地快照，不代表远程环境，也不代表已经修复。证据在访问受控的本地临时目录，未纳入版本库。真实数据补全、后端部署、服务重启及真实账号验收尚未执行。

## 2026-10-28 测试环境执行记录

用户在测试库只读诊断后授权先回填可直接恢复的记录，再单独处理异常。目标为测试环境 `zsjos`、租户 1，顺序为原工具审计/备份/回填，再兼容综合诊断及截止值占位的工具验证/审计/备份/回填。仅更新账号起点、恢复来源标记和审批计时快照，相关版本各增加一；未改阶段、标签、审批状态、诊断、权限、schema 或账本。没有部署或启停服务。

- 第一批：148 个账号、176 个批次；第二批：13 个账号、64 个批次。候选以各次新审计为准，期间正常业务继续操作，故与此前只读诊断时数量略有差异。
- 第一批提交后逐行验证 324 行、代表性中文 HEX 通过；第二批逐行验证 77 行、64 个批次阶段标签 HEX 通过。两批提交后重复审计均为零候选。原逻辑 6 项、修正后 9 项纯测试通过，两次独立 MySQL 的首次/重复/失败回滚/恢复/并发冲突拒绝均通过。
- 第二批核验时 214 个账号中，176 个已设置阶段的账号全部具有起点；其余 38 个账号没有阶段也没有诊断。当前在审原缺失的三条恢复为截至提交时的 6、7、35 天，后续阶段变化不影响审批冻结值。
- 未恢复项单独记录：243 个审批账号项的提交早于首次诊断，20 个审批账号项完全没有诊断，这 263 项的冻结阶段均为空；另 1 个已取消批次缺少 submittedAt。共涉及 212 个批次，不能用后来的诊断、账号创建时间或今天推算历史。`zsjos_account_stage_log` 无可替代证据。草稿不属于历史修复范围。
- 当前在审唯一缺时长的批次为 `CRB-C4F0739432A4415D`：冻结阶段和账号当前阶段均为空，账号只有资料维护历史，没有诊断。需按正常业务完成诊断；已有审批仍保留提交时未知事实，若需展示新阶段，应通过正常的新轮次提交冻结。
- 受保护证据：`/tmp/zsjos-stage-timing-20261028-phase1-{audit,apply}/`、`/tmp/zsjos-stage-timing-20261028-phase2-{audit,apply}/`；各 apply 目录的 `before-verified.json` 保存恢复前像，第二批另有 `exceptions.json` 和 `verification.json`。备份不可提交 Git 或外发；恢复仍须核对目标和修复后行摘要，发生后续业务修改时不能整行回滚。
- 隔离验证库保留为 `stage_timing_verify_20261028_140822`、`stage_timing_verify_20261028_141228`。本次证据为真实 MySQL 数据核验；未做登录后的浏览器验收，不将数据库核验等同于页面验收。
