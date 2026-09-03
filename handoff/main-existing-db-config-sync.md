# Workstream: main-existing-db-config-sync

- Status: active
- Goal: 补齐已有数据库配置同步脚本中的客资分类和来源渠道字典值。
- Non-goals: 不删除或覆盖业务数据，不导入敏感配置、账号凭据或 Flowable 内部表，不创建分支或提交。
- Branch: main
- Worktree: D:\ZSJ-OS
- Base commit: fbf530ebd4a1f6dcd6864436c23e0345b540ecd3
- Target branch: main
- Ownership scope: script/sql/mysql/sync-existing-server-config.sql; docs/operations/zsjos-existing-db-initialization.md; this handoff file
- Owner: Codex /root
- Dependencies: existing System dictionary schema and current database snapshot
- Integration order: SQL values -> documentation -> syntax/idempotence verification
- Verification plan: fresh/targeted MySQL execution, repeated execution count check, UTF-8 HEX verification, git diff --check

### 2026-09-03 21:23:09 +08:00

- Branch: `main`
- Worktree: `D:\ZSJ-OS`
- HEAD commit: `fbf530ebd4a1f6dcd6864436c23e0345b540ecd3`
- User goal: 确认初始化脚本导入全部可安全导出的字典和配置值，并补齐客资分类、来源渠道字典值。
- Key decisions: 从当前数据库快照导入 `zsjos_lead_category` 5 条和 `zsjos_lead_source_channel` 5 条；按 `dict_type + value` 幂等插入；不导入敏感配置、账号凭据、业务实例或 Flowable 内部表。
- Result: 更新已有数据库同步 SQL 和操作文档；移除不属于当前快照的客资分类“其他”。
- Changed files: `script/sql/mysql/sync-existing-server-config.sql`; `docs/operations/zsjos-existing-db-initialization.md`; this handoff file.
- Verification: 新增字典 SQL 片段在 MySQL 8.4 当前库执行成功；UTF-8 查询及 `HEX(label)` 校验通过；现有两个字典均保持 5 条有效数据；`git diff --check` 无错误。完整脚本通过 Docker 管道执行时因容器未挂载仓库路径，`SOURCE` 相对路径无法解析，未宣称完整脚本执行通过。
- Dependency / integration impact: 无新增依赖；未修改 BPM/Flowable 表；目标服务器仍需从仓库根目录使用 mysql 客户端执行，或按部署环境调整 SOURCE 路径。
- Remaining work: 在目标服务器备份后执行完整同步脚本，并单独按 manifest 导入 BPM 流程资产。

### 2026-09-03 21:31:00 +08:00

- Branch: `main`
- Worktree: `D:\ZSJ-OS`
- HEAD commit: `fbf530ebd4a1f6dcd6864436c23e0345b540ecd3`
- User goal: 检查并直接导入数据库中的全部其他字典值。
- Key decisions: 从当前 MySQL 快照导出全部有效 `system_dict_type`（324 条）和 `system_dict_data`（1561 条）；仅包含字典表，使用 `INSERT IGNORE`，不导出账号、凭据、Token、业务实例或 Flowable 表。
- Result: 新增完整字典快照文件并接入已有同步入口；原有客资分类/来源渠道补齐语句保留，快照作为全量兜底。
- Changed files: `script/sql/mysql/dictionary-data/current-database-dictionary-snapshot.sql`; `script/sql/mysql/sync-existing-server-config.sql`; `script/sql/mysql/dictionary-data/README.md`; this handoff file.
- Verification: 快照在 MySQL 8.4 当前库执行成功；执行后有效字典类型/条目计数保持 `324/1561`，证明可重复导入；文件设置 `utf8mb4`，`git diff --check` 无错误。
- Dependency / integration impact: 无新增依赖；同步脚本现在会导入当前快照中的全部有效字典，包括 `zsjos_lead_category` 和 `zsjos_lead_source_channel`。
- Remaining work: 在目标服务器备份后从仓库根目录执行同步脚本，并确认目标库字典计数及代表性中文 `HEX(label)`。
