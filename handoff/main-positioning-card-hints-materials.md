# 定位卡提示与素材选择
- Workstream ID: main-positioning-card-hints-materials
- Goal: 让编导在定位卡表单中明确查看填写提示，并可从素材库选择参考素材；补齐管理端模板预览提示。
- Non-goals: 不新增后端素材业务、不修改权限模型、不修改数据库。
- Branch: main
- Worktree: D:\ZSJ-OS
- Base commit: 60b15fd459e3b353a696cca210c7867a288b4e3b
- Target branch: main
- Ownership scope: frontend/workbench/src/components/PositioningCardFields.tsx; frontend/workbench/src/components/PositioningCardMaterialPicker.tsx; frontend/admin/src/views/zsjos/directorTemplate/index.vue; handoff/main-positioning-card-hints-materials.md
- Owner: main
- Dependencies: existing positioning template and material APIs
- Integration order: sequential on main
- Verification plan: workbench typecheck; admin typecheck; focused source checks

## Delivery — 2026-09-14 16:10 +08:00
- Branch: main; Worktree: D:\ZSJ-OS; HEAD: 60b15fd459e3b353a696cca210c7867a288b4e3b3.
- User goal: 补齐定位卡填写提示和素材库选择功能可见性。
- Key decisions: Workbench 继续消费服务端模板 description 与 material_picker；提示改为明确可点击查看；素材选择器外部回显值变化时同步临时选择状态。
- Execution result: 定位卡四列表单现在以“查看填写提示”入口展示长提示；素材选择器保留现有分页、筛选、预览、多选、追加/移除与权限控制，并修正弹窗关闭期间外部值同步。
- Changed files: frontend/workbench/src/components/PositioningCardFields.tsx; frontend/workbench/src/components/PositioningCardMaterialPicker.tsx; handoff/main-positioning-card-hints-materials.md.
- Verification evidence: Workbench `npm run typecheck` passed. Admin `pnpm ts:check` remains blocked by pre-existing unrelated errors, including ElMessageBox imports and gift TreeOptionProps.
- Dependency or integration impact: None; existing backend APIs and permissions only.
- Remaining work: Browser acceptance against a tenant with published template and material query permission.
