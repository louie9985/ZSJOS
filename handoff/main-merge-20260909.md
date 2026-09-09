# Workstream Registration - 2026-09-09 00:00:00 +08:00

- Workstream ID: `main-merge-20260909`
- Goal: 合并远程 `origin/main` 新提交与当前本地未提交改动，保留双方内容。
- Non-goals: 不提交、不推送、不处理运行日志、缓存和临时 JAR。
- Branch: `main`
- Worktree: `D:\ZSJ-OS`
- Base commit: `cf391285ec`
- Target branch: `main`
- Ownership scope: 本次 Git 合并产生的冲突文件及本交接记录。
- Owner: Codex `/root`
- Dependencies: 用户确认合并双方内容。
- Integration order: 临时保存本地改动 -> 拉取远程 -> 恢复本地改动 -> 合并冲突 -> 静态检查 -> 等待后续提交确认。
- Verification plan: 冲突标记扫描、`git diff --check`、状态和提交关系核对。
-
## Delivery Entry - 2026-09-09 16:00:00 +08:00

- Workstream ID: `main-merge-20260909`; Branch: `main`; Worktree: `D:\ZSJ-OS`; HEAD: `a9f7e2a7c0` (远程代码已拉取，本次未创建新提交)。
- User goal: 合并双方内容，保留远程提交与本地未提交改动。
- Key decisions: Admin 路由相关冲突保留本地动态路由重匹配逻辑；Workbench 保留远程调班能力并合入本地课程属性筛选、考期联动和 `selectedAttrs` 请求参数；保留课程日历与统一订单 API/路由常量；生成的 `tsconfig.tsbuildinfo` 采用远程版本；不处理日志、缓存和临时 JAR。
- Execution or analysis result: 8 个冲突文件均已解决并加入暂存区；冲突标记扫描无命中；本地新增课程日历、订单管理等调用所需的类型、API 和常量已补齐。
- Changed files: `frontend/admin/src/permission.ts`; `frontend/admin/src/utils/authenticatedLanding.ts`; `frontend/admin/tests/authenticatedLanding.test.ts`; `frontend/workbench/src/constants.ts`; `frontend/workbench/src/pages/DeliveryClassPage.tsx`; `frontend/workbench/src/services/api.ts`; `frontend/workbench/tsconfig.tsbuildinfo`; `handoff/main.md`; `handoff/main-merge-20260909.md`。
- Verification evidence: `npm run typecheck`（`frontend/workbench`）通过；`git diff --check` 通过；全仓冲突标记扫描无命中；`git diff --name-only --diff-filter=U` 无输出。
- Dependency or integration impact: 无新增依赖、数据库或共享服务操作；未提交、未推送；原始本地恢复 stash `codex-pre-merge-20260909` 仍保留作为回退副本。
- Remaining work: 等待用户明确授权后再提交或推送；需决定是否清理生成物和保留 stash。
- Status: `resolved-awaiting-commit`

## Delivery Entry - 2026-09-09 16:05:00 +08:00

- Workstream ID: `main-merge-20260909`; Branch: `main`; Worktree: `D:\ZSJ-OS`; HEAD: `b22332ae6b`。
- User goal: 提交并推送已完成的双方内容合并结果。
- Key decisions: 创建普通合并提交并推送 `origin/main`；不纳入日志占位文件、Workbench 缓存和临时 JAR。
- Execution or analysis result: 提交 `b22332ae6b` 已成功推送，远程 `main` 从 `a9f7e2a7c0` 更新至该提交。
- Changed files: `handoff/main-merge-20260909.md`。
- Verification evidence: 推送命令成功；`git rev-list --left-right --count HEAD...origin/main` 为 `0 0`；无未合并文件。
- Dependency or integration impact: 远程仓库已更新；未执行数据库或共享服务操作；合并前 stash 仍保留。
- Remaining work: `LOG_FILE_IS_UNDEFINED`、`frontend/workbench/.cache/`、`jrebel-classpath-44060.jar` 仍为本地未跟踪/未提交产物。
- Status: `pushed`

## Delivery Entry - 2026-09-09 16:10:00 +08:00

- Workstream ID: `main-merge-20260909`; Branch: `main`; Worktree: `D:\ZSJ-OS`; HEAD: `9543adb9d6`。
- User goal: 提交并推送当前新增改动。
- Key decisions: 提交素材库迁移从 `V191` 调整为 `V194` 的源码、文档和校验更新；保留迁移文件重命名及相关 SQL 变更；不纳入日志、缓存和临时 JAR。
- Execution or analysis result: 已创建提交 `9543adb9d6`，准备推送至 `origin/main`。
- Changed files: 素材库迁移 SQL、数据库校验脚本、核心 schema、迁移 README、运维文档、菜单覆盖文档及本交接记录。
- Verification evidence: `git diff --cached --check` 通过；提交成功。
- Dependency or integration impact: 未执行数据库迁移或共享服务操作。
- Remaining work: 推送提交并核对远程同步状态。
- Status: `commit-created`
