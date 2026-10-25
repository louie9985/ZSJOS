# Workstream local-positioning-attachment-1gb

- Environment: local (ZSJOS_AGENT_ENV and /etc/zsjos/agent-environment absent).
- Goal: positioning interview and shared positioning-card attachment groups total at most 1 GiB (1,073,741,824 bytes), enforced in UI and backend.
- Non-goals: storage transport rewrite, deployment, service restart, SQL/data/permission changes, Git integration.
- Branch: main; worktree: D:\ZSJ-OS; base: 404387de566fcb9abf4b264f0e005bf0566ea209; integration: None.
- Owner: current Codex chat. Dependencies: existing Infra FileApi and upload path; preserve all pre-existing dirty work.
- Ownership: this record; PositioningAttachmentPicker.tsx and test; PositioningInterviewDialog.tsx; PositioningCardAttachments.tsx; new attachment-budget utility/tests; PositioningInterviewService.java and test; PositioningCardService.java and test; new PositioningAttachmentLimits.java and test; additive error constant in ZsjosErrorCodeConstants.java; docs/api/positioning-interview.md; frontend/workbench/docs/api-contract.md.
- Verification: focused Vitest and Maven tests, Workbench typecheck/build, browser attachment group boundary/multi-selection check at desktop/mobile widths, scoped diff check.
- Total scope: current interview retained uploads, including pending uploads in its relation/user directory; positioning-card total per attachment field on draft save. Removed bindings do not consume the current group's budget; historical snapshots remain untouched.

- Ownership update: frontend/workbench/test/positioning-budget.{html,tsx} and positioning-budget-browser.py for isolated local browser verification.

## Delivery 2026-10-21

- Implemented a shared 1 GiB retained-group budget: front-end includes stored and pending sizes; synchronous reservations prevent multi-selection from bypassing size/count limits. Pending removal updates the owning form and releases budget. Interview and positioning-card messages now state total size.
- Interview upload aggregates the current interview and relation-scoped pending uploads under existing student/relation locks, rejecting over-budget files before getBytes; save/complete also validate totals. Card upload permits a file up to the budget; draft snapshot validates each attachment field using authoritative Infra sizes. New stable error 1900090007 distinguishes total excess. No historical rows/snapshots modified.
- Documentation: docs/api/positioning-interview.md and frontend/workbench/docs/api-contract.md synchronized. Existing unrelated changes, including the pre-existing error-constant addition and build caches, preserved.
- Verification: 11 focused Vitest tests passed; Workbench typecheck and production build passed (existing chunk-size advisory; output isolated in TEMP/zsjos-positioning-budget-dist). Maven reactor focused tests BUILD SUCCESS: 62 tests, zero failures/errors (3 budget, 27 card, 32 interview). One missing test assertion import was corrected before the successful final run.
- Real Chrome isolated fixture passed pending and immediate upload modes at 1440/390 widths: cumulative 700+324 MiB accepted, additional byte rejected in the same selection, removal restores allowance, empty file rejected. Both screenshots visually inspected. Browser uses tiny bodies with mocked size metadata, not a physical 1 GiB transfer. Evidence: TEMP/positioning-budget-{1440,390}*.png; TEMP/zsjos-positioning-budget-{maven,build}.log. Dedicated Vite 5219 stopped.
- Boundary: local source/logic verification complete; no deployment/restart, proxy change, real-account acceptance or full-size storage transfer/load test. Existing FileApi byte-array storage still reads valid files into JVM memory; runtime heap, proxy and network behavior for a true 1 GiB transfer remain unverified. Admin only configures templates and has no consumer of these upload endpoints; no shared auth/menu/tenant contract changed.
- Ownership released. No commits/push/branch/SQL/dependency changes.
