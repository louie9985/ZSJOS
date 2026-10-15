# 客资转派入口合并

## Registration — 2026-10-15 Beijing
- Workstream: local-lead-transfer-entry-20261015; owner: /root; environment: local (ZSJOS_AGENT_ENV unset; environment file absent).
- Branch: main; worktree: D:/ZSJ-OS; base: 8df3f65eb379b142c0ce0cb84b21e621e59b79b9; target branch/integration: None.
- Goal: merge overlapping qualification and supervisor transfer buttons into one server-authorized Workbench entry.
- Non-goals: backend permissions/business rules, owner transfer semantics, database repair, other operation buttons, deployment, dependencies, branch/worktree/commit/push operations.
- Ownership: frontend/workbench/src/components/LeadDetail.tsx; frontend/workbench/test/lead-transfer-entry.html, lead-transfer-entry.tsx and lead-transfer-entry-browser.mjs; docs/api/zsjos-subordinate-sales.md; this record. Existing concurrent Lead flow changes remain untouched.
- Dependencies: existing server-projected availableActions and existing qualification/supervisor transfer APIs; existing local Vite and bundled Playwright/Chrome for synthetic browser checks.
- Verification plan: Workbench typecheck, related existing tests, desktop/mobile browser checks for overlapping/single/missing/disabled permissions and transfer submission endpoint, scoped diff checks. Fixture checks are not live authenticated acceptance.

## Delivery — 2026-10-15 11:44 Beijing
- Context: registration environment/branch/worktree unchanged; HEAD unchanged at 8df3f65eb379b142c0ce0cb84b21e621e59b79b9; owner /root.
- User goal: remove the two duplicate transfer buttons on recycle-pending Lead details.
- Decision/result: combine QUALIFICATION_TRANSFER and SUPERVISOR_TRANSFER into one displayed transfer action. Prefer the enabled supervisor command, fall back to the enabled qualification command, preserve disabled state if neither is enabled, and show no entry if neither is projected. Existing manager/student read-only filtering and owner-transfer semantics remain unchanged. Candidate queries and submissions continue to use the selected command's established API and backend checks.
- Changed files: frontend/workbench/src/components/LeadDetail.tsx; frontend/workbench/test/lead-transfer-entry.html; frontend/workbench/test/lead-transfer-entry.tsx; frontend/workbench/test/lead-transfer-entry-browser.mjs; docs/api/zsjos-subordinate-sales.md; this record.
- Verification: npm run typecheck PASS; three focused existing Vitest files PASS (11 tests); bundled Playwright/Chrome at 1440px and 390px PASS (20 scenario runs covering both/single actions, disabled-command fallback, both disabled, no permission, student-readonly and manager filtering). Submission checks confirm one POST, matching candidate/query and submission APIs, correct target field and idempotency key. Screenshots C:/Users/EDY/AppData/Local/Temp/lead-transfer-entry-1440.png and lead-transfer-entry-390.png visually inspected: one transfer button, no layout overlap. Scoped git diff --check PASS. No production build required for this local component-only change; no bundling/routes/dependencies/assets/configuration change.
- Dependencies/integration impact: None; existing local Vite used without service lifecycle changes, backend/database untouched, no branch/worktree/commit/push operations. Preserved concurrent backend Lead-flow work and pre-existing build-info changes.
- Remaining work/limits: live authenticated runtime acceptance and deployment unverified; browser transport uses isolated synthetic responses and performs no real Lead transfer. File ownership released on delivery.
