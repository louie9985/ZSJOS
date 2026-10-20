# 班级统计视图预览

- Context: local; main; D:/ZSJ-OS; owner this session. Existing worktree changes are preserved; no branch, worktree, commit, push, deployment or shared runtime operation.
- Goal: add an isolated Workbench browser preview of the class-management page with multiple list layouts for product discussion.
- Non-goals: no production route, backend API, permission change, database change, static production option source, or replacement of the existing class-management page.
- Ownership: `frontend/workbench/test/class-statistics-preview.html`, `frontend/workbench/test/class-statistics-preview.tsx`, and `frontend/workbench/src/styles/pages/class-statistics-preview.css`.
- Data boundary: the preview uses explicitly labelled synthetic sample class data only; it demonstrates class browsing and view presentation, not a production statistics contract.
- Verification: Workbench typecheck/build where applicable, focused source checks, and browser preview inspection if the local Vite server is available.
- Delivery: revised the preview to show all class-management concerns: search, project/homeroom/status filters, class count, create/refresh actions, and three list layouts (cards, table, grouped by homeroom). `npm run typecheck` passed; `npm run build` passed; Vite served the preview with HTTP 200 at `http://127.0.0.1:5174/test/class-statistics-preview.html`. No production page, API, database, permission or runtime data was changed.
