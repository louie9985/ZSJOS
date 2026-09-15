# Workstream: main-content-review-form

- Goal: 优化内容审核草稿编辑表单的尺寸、字典字段、图片预览、参考作品选择和防误关闭交互。
- Non-goals: 不修改后端接口、数据库、权限模型或其他页面。
- Branch: main
- Worktree: D:\ZSJ-OS
- Base commit: 436ba84cd8ab5471356e3faa62db9ab44e3c76e9
- Target branch: main
- Ownership scope: frontend/workbench/src/pages/ContentReviewBatchPage.tsx；frontend/workbench/src/components/ContentApprovalDraft.tsx；相关样式与设计文档
- Owner: main-content-review-form
- Dependencies: 现有字典 API、素材参考作品 API、上传 API
- Integration order: 先实现页面，再执行 Workbench 检查
- Verification plan: npm run typecheck；npm run build

## Delivery 2026-09-14 20:10 (Beijing time)
- Branch: main
- Worktree: D:\ZSJ-OS
- HEAD commit: 436ba84cd8ab5471356e3faa62db9ab44e3c76e9
- User goal: 优化内容审核草稿表单尺寸、字典字段、图片预览、防误关闭。
- Key decisions: 编辑 Modal 禁止遮罩关闭并扩大至 1100px；账号平台/阶段/状态和作品字段读取字典；上传后保存预览地址。
- Changed files: frontend/workbench/src/pages/ContentReviewBatchPage.tsx; docs/superpowers/specs/2026-09-14-content-review-form-design.md
- Verification evidence: npm run typecheck 通过。
- Dependency or integration impact: 复用现有字典、素材和上传 API；无新增依赖。
- Remaining work: 参考作品卡片选择器尚未替换，当前仍为下拉框；需继续实现并补充 build/浏览器验证。

## Delivery 2026-09-14 20:18 (Beijing time)
- Branch: main
- Worktree: D:\ZSJ-OS
- HEAD commit: 436ba84cd8ab5471356e3faa62db9ab44e3c76e9
- User goal: 完成参考作品卡片选择器。
- Key decisions: 以现有 referenceTargets 数据渲染卡片 Modal，点击卡片回填版本 ID；遮罩不可关闭。
- Changed files: frontend/workbench/src/pages/ContentReviewBatchPage.tsx
- Verification evidence: npm run typecheck、npm run build 均通过。
- Dependency or integration impact: 无新增依赖，复用素材 API。
- Remaining work: 浏览器人工验收未执行。

## Delivery 2026-09-14 20:25 (Beijing time)
- Branch: main
- Worktree: D:\ZSJ-OS
- HEAD commit: 436ba84cd8ab5471356e3faa62db9ab44e3c76e9
- User goal: 修复上传图片后不显示预览。
- Key decisions: 修正 Form.useWatch 对 Form.List 的完整 works 路径监听。
- Changed files: frontend/workbench/src/components/ContentApprovalDraft.tsx
- Verification evidence: npm run typecheck 通过。
- Dependency or integration impact: 无。
- Remaining work: 浏览器人工验收未执行。

## Delivery 2026-09-14 20:35 (Beijing time)
- 修复预览组件依赖 Form.List 监听不刷新的问题，增加 CoverUploadField 本地预览状态，上传成功立即渲染。
- Verification: npm run typecheck 通过。

## Delivery 2026-09-15
- 本地 ruoyi-vue-pro 已幂等创建字典类型 zsjos_content_purpose、zsjos_content_format，HEX 校验中文通过。
- 初始化脚本 00-bootstrap-schema.sql 已补充空字典类型；未新增业务字典项，遵循管理员维护约束。

## Delivery 2026-09-15
- 内容审批表单新增保存草稿、保存并提交审批两个按钮；提交按钮复用现有 batch submit API。
- Verification: npm run typecheck 通过。
