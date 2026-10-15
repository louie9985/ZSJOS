# 公告列表选中状态修复

## Registration — 2026-10-15
- ID: local-notice-selection-20261015; owner: current Codex session.
- Environment: local (fallback); branch: main; worktree: D:/ZSJ-OS; base: 315efd729f2e049fc2639194567d86077ae91184.
- Goal: make the selected employee announcement visually distinguishable after clicking.
- Non-goals: API, permissions, data, other pages, deployment, Git publication.
- Ownership: frontend/workbench/src/styles/pages/announcements.css; docs/frontend/notice-center.md; this record.
- Dependencies: existing selectedId/active class and theme tokens; no dependency on concurrent changes. Target branch/integration order: None.
- Verification: existing stylesheet guards, Workbench typecheck, existing synthetic notice-center browser fixture on desktop/mobile using local Vite 5174, visual screenshots and hover/selection checks.

## Delivery — 2026-10-15
- Changed announcement active style from container-equivalent sunken color to primary background, a 3px primary left marker and primary semibold title. Neutral hover is distinct from selection; keyboard focus has a primary outline. Existing selectedId/active state remains the source. Updated notice-center documentation.
- Verification: npm run typecheck PASS. Existing notice-center-scroll-browser.py ran against the already-running local Vite 5174 by substituting the port in memory; desktop 1440 and mobile 390 PASS (selection, independent scroll, cursor loading/retry/end, detail failure/denial/retry, fast switching and deep links).
- Additional browser checks PASS at 1440/390: exactly one selected row; selection remains visible after pointer exit and mobile drawer close; active background differs from container and nonselected hover; marker width 3px; real Tab navigation shows focus outline. Initial programmatic focus assertion was invalid after mouse input because :focus-visible depends on input modality; actual Tab navigation passed. Screenshots: C:/Users/EDY/AppData/Local/Temp/notice-selection-check/selected-1440.png and selected-390.png. Screenshot rendering through the agent image tool did not produce a visible preview; browser computed-style/interaction checks completed, manual visual inspection remains unverified.
- Style guards: 28/30 PASS; existing unrelated failures are hardcoded font sizes in calendar-time-grid.css/content-review-attachments.css and undefined --crm-color-fill-tertiary in calendar-colors.css. No announcement file was reported. git diff --check PASS.
- Boundaries: synthetic browser responses only; shared environment deployment and authenticated live acceptance unverified. No service start/stop, data write, dependency, commit or push. Ownership released.
