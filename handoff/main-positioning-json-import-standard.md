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

## Delivery: 2026-09-14 11:46:49 Asia/Shanghai
- Branch: main
- Worktree: D:\ZSJ-OS
- HEAD: 60b15fd459e3b353a696cca210c7867a288b4e3b
- User goal: 将标准定位卡提示词放入系统并提供复制按钮
- Key decisions: 在定位卡 JSON 导入弹窗内嵌标准提示词；通过浏览器剪贴板复制；失败时提示手动复制；保留现有导入校验和权限边界。
- Result: 新增系统标准提示词常量与复制操作，提示词覆盖当前字段 key、多选字典 value、不可导入字段及输出约束。
- Changed files: frontend/workbench/src/pages/MediaStudentsPage.tsx
- Verification: positioningJsonImport focused tests passed (10/10); npm run typecheck未通过，失败来自既有 AccountProfilePanel 缺失 API 类型/组件及 MediaStudentsPage 既有字段类型错误，与本次提示词功能无关。
- Dependency/integration impact: None; no new dependency, database, API, permission, or dictionary changes.
- Remaining work: None for requested feature; existing typecheck errors remain.

## Delivery: 2026-09-14 12:05:26 Asia/Shanghai
- Branch: main
- Worktree: D:\ZSJ-OS
- HEAD: 60b15fd459e3b353a696cca210c7867a288b4e3b
- User goal: 修复 Vite 内部服务器解析错误
- Key decisions: 根因是提示词模板字符串中包含未转义反引号；移除该 Markdown 字符，仅修正字符串内容。
- Result: Workbench 构建恢复成功。
- Changed files: frontend/workbench/src/pages/MediaStudentsPage.tsx
- Verification: npm run build passed; Vite production bundle generated.
- Dependency/integration impact: None.
- Remaining work: None.
