# Workstream Handoff: dual-workbench-menu-ui

- Workstream ID: `dual-workbench-menu-ui`
- Status: `active`
- Goal: Complete Vue Admin and React Workbench interfaces for every visible page menu directly under the Workbench root menu `6735`.
- Non-goals: Creating pages for button permission nodes or the hidden lead-management menu; adding backend aggregation APIs; changing business tables, role grants, menu IDs, paths, permissions, or ordering; redesigning existing usable pages.
- Branch: `codex/dual-workbench-menu-ui`
- Worktree: `D:\ZSJ-OS-worktrees\dual-workbench-menu-ui`
- Base commit: `80dd44a5e3706599fa3d1d45581b4fb8149e1574`
- Target branch: `main`
- Ownership scope: `frontend/admin/src/api/zsjos`, new or directly shared ZSJOS Admin views, `frontend/workbench/src/pages`, Workbench route/component/service registration, Workbench tests for those registrations and services, `script/sql/mysql` menu migration/bootstrap/verification documentation, directly affected frontend/API documentation, and this handoff file.
- Owner: Codex workstream `dual-workbench-menu-ui`
- Dependencies: Existing System menu/permission response and existing ZSJOS/System/BPM HTTP APIs at the base commit; no dependency on uncommitted work from another workstream.
- Integration order: Integrate after any workstream that changes the same Workbench menu rows or shared frontend API files; otherwise merge as one self-contained frontend/menu change, then rerun both frontend builds and SQL checks on the integration branch.
- Verification plan: Admin focused tests, typecheck, scoped lint and local build; Workbench tests, typecheck and production build; SQL migration/bootstrap static and repeatability review; authenticated browser checks at desktop and mobile widths when an environment is available; `git diff --check`.

## Entries

### 2026-08-13 14:48:00 +08:00

- Branch: `codex/dual-workbench-menu-ui`
- Worktree: `D:\ZSJ-OS-worktrees\dual-workbench-menu-ui`
- HEAD commit: `80dd44a5e3706599fa3d1d45581b4fb8149e1574`
- User goal: Implement the approved dual-frontend completion plan for every visible page menu under Workbench root menu `6735`.
- Key decisions: Kept existing usable pages unchanged; implemented Admin pages as Element Plus lists with dialogs/drawers; implemented React configuration pages as tables; used only existing System, BPM, and ZSJOS APIs and backend-owned permissions/options; kept menu IDs, paths, permissions, sorting, and role grants unchanged; did not execute SQL or start/stop services.
- Execution or analysis result: Added 12 Vue Admin pages and shared list/submission/repurchase components; added five React configuration pages and registered aging-pool/subordinate-sales renderability; added permission-controlled processing, order termination, BPM todo, and subordinate batch actions; added repeatable V045 plus fresh-bootstrap and verification synchronization; removed an invalid React `agingPool` filter audience after confirming the backend contract only accepts submitter/owner/reviewer.
- Changed files: `frontend/admin/src/api/zsjos/workbenchMenus.ts`; new task-owned views under `frontend/admin/src/views/zsjos/{components,externalRepurchase,leadAppeal,leadComplaint,leadDuplicateReview,leadInbox,leadSelfSourced,leadSubmission,mySalesOrder,salesOrderApproval,subordinateSales,todayTask}`; `frontend/workbench/src/{constants.ts,constants.test.ts,main.tsx,pages/ConfigurationPages.tsx,services/api.ts}`; `script/sql/mysql/{01-bootstrap-system-seed.sql,02-bootstrap-zsjos-seed.sql,verify-bootstrap.sql,migrations/README.md,migrations/V045__dual_frontend_workbench_menu_components.sql}`; this handoff file.
- Verification evidence: Workbench `npm test` passed 20 files/86 tests; `npm run typecheck` passed; `npm run build` passed with the existing large-chunk warning. Admin task-file ESLint passed; task-file Prettier completed; Vite local production build passed with the existing legacy `*zoom` CSS warning. Direct Vue typecheck found no errors in the final task files when filtered, but full-project typecheck failed because generated auto-import declarations are absent and the baseline reports thousands of unresolved `ref/computed/useMessage` names. V045 static parsing confirmed exactly eight unique matching CASE/WHERE IDs; `git diff --check` passed. `zsjos_db.py check` ran but failed on pre-existing Core schema mapping gaps for `zsjos_lead_claim_daily_counter` and `zsjos_lead_transfer_request`. Existing `localhost:5174` was inspected read-only and belongs to the primary worktree, so it was not used as branch UI evidence.
- Dependency or integration impact: No dependency or backend/API contract changes; integrate V045 and bootstrap updates with the frontend files in one change. Rerun both builds and SQL checks after integration, especially if another workstream edits Workbench menu metadata or Core schema manifests.
- Remaining work: Authenticated desktop/mobile browser verification of this branch requires separate confirmation to start its Admin and Workbench dev servers. Full Admin `pnpm ts:check` remains blocked by dependency build-script policy/absent auto-import declarations, and the repository SQL check remains blocked by the unrelated Core schema mapping gaps. No commit, push, migration execution, or role authorization was performed.

### 2026-08-13 15:06:08 +08:00

- Branch: `codex/dual-workbench-menu-ui`
- Worktree: `D:\ZSJ-OS-worktrees\dual-workbench-menu-ui`
- HEAD commit: `80dd44a5e3706599fa3d1d45581b4fb8149e1574`
- User goal: Continue the approved implementation until the React configuration pages include the complete planned table/dialog/drawer maintenance flows.
- Key decisions: Extended only the existing task-owned React configuration page and typed API service; retained backend-returned filter conditions, template fields, department scopes, and preset tasks when editing summarized metadata; used backend category trees and product attributes instead of static production options; added confirmations for delete, status, publish, rollback, and disable actions.
- Execution or analysis result: Added filter-draft group editing; product create/edit/delete/status; product-category tree create/edit; SKU create/edit/delete/status with server-owned attribute selectors; dynamic product-attribute/value maintenance; and work-plan template create/draft-edit while preserving unexposed version configuration. Corrected category Cascader paths to submit leaf IDs and parent IDs required by backend VOs.
- Changed files: `frontend/workbench/src/pages/ConfigurationPages.tsx`, `frontend/workbench/src/services/api.ts`, and this handoff file.
- Verification evidence: Final Workbench `npm run typecheck` passed; `npm test` passed 20 files/86 tests; `npm run build` passed with the existing large-chunk warning; `git diff --check` passed. The generated tracked `tsconfig.tsbuildinfo` was restored after the build.
- Dependency or integration impact: No new dependencies or backend contracts. Configuration writes remain gated by the exact existing button permissions.
- Remaining work: Same as the prior entry: authenticated browser verification of this branch requires separately approved service startup; Admin full-project typecheck and repository SQL check retain the documented unrelated baseline blockers. No commit, push, migration execution, or role authorization was performed.

### 2026-08-13 15:08:52 +08:00

- Branch: `codex/dual-workbench-menu-ui`
- Worktree: `D:\ZSJ-OS-worktrees\dual-workbench-menu-ui`
- HEAD commit: `80dd44a5e3706599fa3d1d45581b4fb8149e1574`
- User goal: Finish the remaining Vue order action after the final acceptance cross-check.
- Key decisions: Reused the existing sales-order detail snapshot as the source for resubmission, exposed only the fields that need routine correction, preserved all other required values and attachment references, and generated a fresh idempotency key. Did not duplicate the large React lead-management action surface into the two Vue inbox list pages because those actions span separate existing business components and were outside the approved “existing pages not redesigned” boundary.
- Execution or analysis result: Added permission- and `canRevise`-gated order correction/resubmission to Vue “我的订单”, alongside the previously implemented termination flow.
- Changed files: `frontend/admin/src/api/zsjos/workbenchMenus.ts`, `frontend/admin/src/views/zsjos/mySalesOrder/index.vue`, and this handoff file.
- Verification evidence: Task-file Prettier and ESLint passed; final Admin local production build passed with the existing legacy `*zoom` CSS warning; `git diff --check` passed.
- Dependency or integration impact: None beyond the existing `/zsjos/sales-order/{id}/resubmit` contract.
- Remaining work: Same environment-level verification blockers as the prior entry. No commit, push, migration execution, service startup, or role authorization was performed.
