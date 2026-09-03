## Workstream Registration - 2026-09-04 00:00:00 +08:00

- Workstream ID: `main-existing-db-role-rebuild`
- Goal: 在用户确认后重建租户 1、121、122 的角色菜单权限，并交付可重复初始化脚本。
- Non-goals: 不修改用户、角色、用户角色绑定、字典、业务数据、BPM 模型/实例及 Flowable 表。
- Branch: `main`
- Worktree: `D:\ZSJ-OS`
- Base commit: `16e026a672dfd4ab087d9a84179ccd544df30a99`
- Target branch: `main`
- Ownership scope: `system_role_menu` 权限关系、本地数据库权限备份/修复 SQL、权限初始化文档。
- Owner: Codex `/root`
- Dependencies: 本地 Docker MySQL `yudao-mysql`、数据库 `ruoyi-vue-pro`、现有 `system_role`/`system_menu` 数据、仓库权限矩阵。
- Integration order: 只读审计 -> 建立备份表 -> 事务逻辑清理 -> 插入新角色菜单关系 -> 计数/差集/越权验证 -> 生成初始化脚本和交付记录。
- Verification plan: 事务内备份行数、角色权限白名单/禁止项、重复权限、父菜单关系、普通用户绑定、脚本重复执行幂等性。

## Delivery Entry - 2026-09-04 00:45:44 +08:00

- Beijing time: `2026-09-04 00:45:44 +08:00`
- Branch: `main`
- Worktree: `D:\ZSJ-OS`
- HEAD commit: `8eb48ee957226d199c6eb3a6ae88bb21ff3be151`
- User goal: 确认后完整重建本地租户角色菜单权限，并交付可重复初始化脚本。
- Key decisions: 采用 V071 精确 allowlist；普通用户使用通用权限白名单；岗位业务权限从审核前快照恢复并过滤已确认越权项；旧关系只逻辑删除；不改用户角色绑定、字典业务数据以外的业务数据、BPM/Flowable 表。
- Execution/analysis result: 本地 Docker MySQL `ruoyi-vue-pro` 已执行重建。有效关系计数为租户 1=`1622`、121=`199`、122=`197`；备份表 `zsjos_role_menu_backup_20260904` 计数为 1=`6485`、121=`518`、122=`481`。重复 permission 检查为 `0`，确认的内容/运营客资、部门 BPM 模型、交付任务规则越权检查为 `0`。
- Changed files: `script/sql/mysql/sync-existing-server-config.sql`; `docs/operations/zsjos-existing-db-initialization.md`; this handoff entry.
- Verification evidence: 通过 MySQL `utf8mb4` 客户端复跑脚本角色段两次，计数稳定且重复关系为 `0`；检查租户覆盖、备份行数和逻辑删除状态。完整 `verify-bootstrap.sql` 仍有与本次权限重建无关的历史 schema/业务数据 FAIL，未将其误报为本次通过。
- Dependency/integration impact: 脚本依赖仓库根目录可读的 `script/sql/mysql` SOURCE 文件和现有 `system_role/system_menu`；BPM 流程继续使用 `script/bpm/import-existing-config.ps1`/管理 API，不能通过 SQL 导入 Flowable 定义。
- Remaining work: 服务器执行前需备份并确认目标租户；正式账号是否补 `normal_user` 仍需单独决定，本次未修改 `system_user_role`。服务器 BPM 模型需按 manifest 逐项导入并记录发布结果。
