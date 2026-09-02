# Workstream Registration - 2026-09-02 12:30:00 +08:00

- Workstream ID: `main-merge-20260902`
- Goal: 合并无共同祖先的本地 `main` 与 `origin/main`，保留双方提交并按用户确认解决冲突。
- Non-goals: 不创建提交、不推送、不处理嵌套前端子仓库。
- Branch: `main`
- Worktree: `D:\ZSJ-OS`
- Base commit: `069ad45ef11e9c8d6fac2707a2c160641ebfe3f3`
- Target branch: `main`
- Ownership scope: 当前合并冲突文件及本交接记录。
- Owner: Codex `/root`
- Dependencies: 用户确认生产环境使用 PID + nohup；pay/crm/ai 关闭；EAM tenant_id=1；V045 使用 baseline。
- Integration order: 按文件语义合并 -> 检查冲突标记与语法 -> 等待用户确认后再提交/推送。
- Verification plan: `git diff --check`、冲突标记扫描、脚本/Python/SQL 静态检查。
- Status: `resolved-awaiting-commit`

## Delivery Entry - 2026-09-02 12:45:00 +08:00

- Beijing time: 2026-09-02 12:45:00
- Branch: `main`
- Worktree: `D:\ZSJ-OS`
- HEAD commit: `069ad45ef11e9c8d6fac2707a2c160641ebfe3f3` (merge in progress, not committed)
- User goal: 保留本地与远程两端提交，合并无共同祖先的历史。
- Key decisions: 生产部署采用远程 PID + nohup；pay/crm/ai 保持关闭；EAM V006 使用 tenant_id=1；V045 使用 release_version=baseline；大型 SQL 与数据库工具采用包含双方扩展的结构化版本；未处理嵌套前端子仓库。
- Execution or analysis result: 12 个冲突文件已解决并全部暂存，合并状态保留以待用户确认后创建合并提交。
- Changed files: `.gitignore`; `backend/pom.xml`; `backend/yudao-server/pom.xml`; `deploy/production/.env.example`; `handoff/main.md`; `script/shell/deploy-production.sh`; `script/sql/mysql/00-bootstrap-schema.sql`; `script/sql/mysql/migrations/V045__dual_frontend_workbench_menu_components.sql`; `script/sql/mysql/migrations/eam/V006__eam_category_baseline.sql`; `script/sql/mysql/schema/core.sql`; `script/sql/mysql/schema/eam.sql`; `script/sql/mysql/tools/zsjos_db.py`; `handoff/main-merge-20260902.md`。
- Verification evidence: 无冲突标记；`git diff --cached --check` 通过；`python -m py_compile script/sql/mysql/tools/zsjos_db.py` 通过。
- Dependency or integration impact: 后续生产部署继续依赖 PID 文件和 nohup；未执行真实数据库迁移、服务启停、提交或推送。
- Remaining work: 用户确认后执行 `git commit`；如需同步云端，再单独确认并执行 `git push`。
