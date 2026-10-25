# Workstream registration

- ID: class-optional-exam-20261022; status: active; owner: Codex /root.
- Environment: local (ZSJOS_AGENT_ENV unset; /etc/zsjos/agent-environment absent).
- Branch: main; worktree: D:/ZSJ-OS; base: 404387de566fcb9abf4b264f0e005bf0566ea209.
- Goal: allow creating normal classes without exam dates and filling dates through existing editors.
- Non-goals: SQL/data changes, system-class changes, permissions, deployment, dependencies, Git integration.
- Target branch / integration order / dependencies: None.
- Ownership: deliveryclass Save/BatchCreate request VOs, DeliveryClassServiceImpl validation/update, DeliveryClassDatesTest/BatchCreateTest/ServiceImplTest, Workbench DeliveryClassPage.tsx and services api.ts/deliveryClassGroups.ts, Admin class-management.vue, focused optional-exam browser regression, docs/api/delivery-class-management.md, this record. Preserve all existing unrelated dirty changes.
- Verification: focused Java validation/create/batch/update tests, frontend static checks, browser creation without dates and later editing on existing isolated transport fixtures; scoped diff checks.

## Delivery — 2026-10-22 14:22 +08:00

- Branch/worktree/base: registration unchanged; HEAD 404387de566fcb9abf4b264f0e005bf0566ea209; environment local.
- User goal: 创建班级考期非必选，允许后续补填。
- Decisions/result: single, batch and grouped creation accept omitted dates; persist no fabricated exam snapshot and retain normal class identity. Existing editors can fill dates later; unchanged dates preserve historical associations/ranges/unparseable snapshots. Admin previously always preserved exam dates even when changed; now sends preservation only for unchanged dates. Explicit clearing writes null snapshot through the update wrapper so MyBatis null-skipping cannot retain stale dates. Existing permissions, planner eligibility, system-class restrictions and transactional batch semantics preserved.
- Changed files: DeliveryClassSaveReqVO.java; DeliveryClassBatchCreateReqVO.java; DeliveryClassServiceImpl.java; DeliveryClassDatesTest.java; DeliveryClassBatchCreateTest.java; DeliveryClassServiceImplTest.java; frontend/workbench/src/pages/DeliveryClassPage.tsx; frontend/workbench/src/services/api.ts; frontend/workbench/src/services/deliveryClassGroups.ts; frontend/admin/src/views/zsjos/class-management.vue; frontend/workbench/test/class-optional-exam-browser.py; docs/api/delivery-class-management.md; this handoff. Existing unrelated dirty changes retained.
- Verification: reactor Maven focused tests passed, 38 tests across Dates (2), BatchCreate (9), ServiceImpl (16), GroupService (11); compilation included affected backend code. Workbench typecheck passed on final inputs; 13 existing transport/review regression tests passed. Chrome isolated production components passed no-date creation, later fill and metadata-only preservation on Workbench and Admin at 1440/390. Mobile screenshot inspected. Scoped git diff --check passed.
- Verification limitation: Admin full pnpm ts:check blocked by existing duplicate identifiers in src/api/zsjos/withdrawal/index.ts lines 15-16 and 40-45; no class-page diagnostics. No dependency/route/bundling changes, so production build not applicable. Browser uses synthetic isolated API responses; no real-account/backend acceptance or persisted business-data writes claimed.
- Evidence: private Temp zsjos-optional-exam-{java,workbench,admin,vitest,browser}.log and zsjos-optional-exam-browser/*.png. Initial test compile issue from inherited fluent setter inference corrected; test-only button labels corrected to current grouped-page labels before final passing runs.
- Dependency/integration impact: None; SQL columns already nullable, no migration required. No external shared-service, account, permission, database, branch, commit, push or deployment changes.
- Remaining work: deployment and real-account acceptance not performed; unrelated Admin type errors remain. Status completed; ownership released.
