# Workstream: main-positioning-json-import-standard

- Goal: 对齐标准提示词 JSON 与定位卡导入链路
- Non-goals: 不改变字段、字典、权限、素材和附件业务规则；不修改数据库
- Branch: fix/media-account-create-columns-v204
- Worktree: D:\ZSJ-OS
- Base commit: f338087e9aaa8b8d881cd0aee80427d8773ab284
- Target branch: fix/media-account-create-columns-v204
- Scope: frontend/workbench/src/services/positioningJsonImport.ts; frontend/workbench/src/services/positioningJsonImport.test.ts; directly affected documentation if needed
- Owner: main
- Dependencies: current server-published positioning template and dictionaries
- Integration order: implementation, focused tests, typecheck/build
- Verification plan: positioning JSON tests; Workbench typecheck/build where available

## Delivery: 2026-09-13 20:13:38 Asia/Shanghai
- Branch: fix/media-account-create-columns-v204
- Worktree: D:\ZSJ-OS
- HEAD: f338087e9aaa8b8d881cd0aee80427d8773ab284
- User goal: 对齐标准提示词 JSON 与定位卡导入链路
- Key decisions: 保持扁平 field-key JSON 契约；兼容 UTF-8 BOM 与 `json Markdown 包裹产生的传输噪声；不放宽字段和值校验。
- Result: 定位卡 JSON 解析器可接受标准 JSON 及常见 AI 包裹输出，仍严格按服务端模板和字典校验。
- Changed files: frontend/workbench/src/services/positioningJsonImport.ts; frontend/workbench/src/services/positioningJsonImport.test.ts
- Verification: npm test -- --run src/services/positioningJsonImport.test.ts (10 passed); npm run typecheck (passed)
- Dependency/integration impact: None; no database, API, permission, or dictionary changes.
- Remaining work: None.
