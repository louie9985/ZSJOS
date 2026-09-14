## Workstream Registration - 2026-09-13 00:00:00 +08:00

- Workstream ID: `main-material-library-fix`
- Goal: 修复爆款内容提交标题校验、素材库爆款内容编辑入口及提交权限显示。
- Non-goals: 不改数据库、分支、提交、推送或外部状态；保留其他工作树改动。
- Branch: `main`; Worktree: `D:\ZSJ-OS`; Owner: Codex `/root/material_library`。
- Ownership scope: MaterialService.java; MaterialLibraryPage.tsx; ViralAccountMaterialForm.tsx; this handoff.
- Verification plan: Workbench typecheck/build and focused source checks; backend module compile.

## Delivery Entry - 2026-09-13 00:00:00 +08:00

- User goal: 修复素材库爆款内容提交流程与入口。
- Key decisions: 提交完整性校验使用已持久化版本标题，避免丢失 work_title；编辑入口按素材类型选择账号或内容；提交审批按钮受服务端权限投影控制。
- Changed files: backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/material/MaterialService.java; frontend/workbench/src/pages/MaterialLibraryPage.tsx; frontend/workbench/src/components/ViralAccountMaterialForm.tsx。
- Verification evidence: Pending.
- Dependency or integration impact: 无新增依赖、数据库或外部状态变更。
- Remaining work: 运行验证并回报。
- Verification evidence: `mvn -f backend/pom.xml -pl yudao-module-zsjos -am -DskipTests compile` passed; `npm run typecheck` in frontend/workbench passed.
- Remaining work: 创建后提交失败时的素材 ID 保留与原地重试仍需产品侧确认；当前本次修复覆盖确定性标题校验、内容草稿编辑入口及 submit 权限入口。
