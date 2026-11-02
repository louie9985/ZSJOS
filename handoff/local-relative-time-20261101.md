# Relative time comparisons
- Environment: local (environment variable and environment file absent).
- Workstream: relative-time-20261101; owner: current Codex chat.
- Goal: composable relative date operands in both advanced filter clients and backend.
- Non-goals: schema, permissions, deployment, unrelated work.
- Branch: main; worktree: D:\ZSJ-OS; base: 0ecc74d4d13bcfbc0361ecdbc2bbd73327359425.
- Ownership: AdvancedFilterService.java (preserve existing intersection changes); new RelativeTimeOperand.java and focused test; both advanced-filter UI components and new date operand components/helpers; docs/frontend/advanced-filter-metadata.md; this record; isolated tmp verification files.
- Dependencies: existing filter protocol, component libraries. No new dependencies.
- Verification: focused Java regression, frontend type checks/tests, browser if available.
- Integration/target branch: None.

## Delivery
- Implemented independent relative time operands for both React/Vue date comparisons and interval boundaries. Preserved fixed timestamps, old period filters and pre-existing intersection edits.
- No dependency, schema, permission, account or business-data changes. No commit, push or deployment.
- Verification: isolated current-source Java compile + 31 tests passed; Workbench typecheck passed; 11 existing filter tests + 2 relative operand tests passed.
- Browser: actual new React/Vue components, isolated Vite fixtures, 1440/390 widths; relative-mode switch and -7 day payload passed, screenshots inspected. This is component verification, not live-account/template API acceptance.
- Admin typecheck: changed files clean; blocked globally by pre-existing unused handleRefresh at src/views/mes/pro/task/edit/index.vue:86.
- Existing AdvancedFilterContract suite: 2 passed/4 failed on pre-existing scene lists (student_service/education_team) and page binding expectations; those contracts/files were not changed here.
- Native ego-browser unavailable; used installed Playwright. No shared service restarted. Task-owned Vite processes stopped after verification.
- Detailed protocol: docs/frontend/advanced-filter-metadata.md. Isolated artifacts: frontend/workbench/tmp/relative-time/ and frontend/admin/tmp/relative-time/.
- Ownership released.
