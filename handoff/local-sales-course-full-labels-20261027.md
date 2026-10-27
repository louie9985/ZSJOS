# Workstream: sales-course-full-labels-20261027

- Environment: local (environment variable and marker absent)
- Goal: 成交课程选择器的选项和已选长文本完整换行。
- Non-goals: API、数据库、权限、发布、其他页面重构。
- Branch: main; Worktree: D:\ZSJ-OS
- Base/HEAD: 8279212fb97129ba6b006dbb0452ad843dfde4b5
- Owner: Codex root
- Scope: SalesOrderCoursePicker.tsx; styles/components/attachment.css; test/sales-course-full-labels.* and browser script; docs/ui-guidelines.md; this handoff.
- Dependencies: existing Ant Design and local Vite; Target branch/integration order: None
- Verification: existing focused unit tests/style guard; production component browser fixture at desktop/mobile widths, popup and selected label geometry, selection and keyboard behavior.

## Delivery — 2026-10-27 16:30 Beijing
- Context: registration branch/worktree/HEAD unchanged; owner Codex root; status delivered, ownership released.
- Result: 修复成交课程选择器遗漏的长文本展示。下拉选项换行、停用 Select 固定行高虚拟列表、选中值按 Ant Design 当前 ant-select-content 结构换行并自动增高。课程分类弹层同样换行。
- Changed files: frontend/workbench/src/components/SalesOrderCoursePicker.tsx; frontend/workbench/src/styles/components/attachment.css; frontend/workbench/docs/ui-guidelines.md; frontend/workbench/test/sales-course-full-labels.html; frontend/workbench/test/sales-course-full-labels.tsx; frontend/workbench/test/sales-course-full-labels-browser.py; this record.
- Verification: Chromium production-component fixture passes at 1440/390 widths: 12 long SKU options, selected text bounds, last-option selection and value propagation, category wrapping, Escape, no page horizontal overflow. Screenshots inspected. Existing SalesOrderCoursePicker tests 2/2 pass; style guard 26/30 pass, 4 existing failures in unrelated class-statistics-preview/calendar/content-review/bpm CSS. Scoped git diff --check passes.
- Dependencies/integration: None. No API, DB, permissions, dependency or release changes.
- Remaining: Not deployed; real-account order modal acceptance not performed. Fixture verifies actual shared picker with synthetic catalog. Global unrelated style violations remain untouched.
