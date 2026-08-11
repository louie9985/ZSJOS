# Workbench Approval, Upload, and Form UX

- Workstream ID: `workbench-approval-upload`
- Goal: Reuse the submitted-lead layout for sales-order approvals, add reviewer filter schemes, defer workbench uploads until submit, add upload progress/removal, label business form fields, and replace SKU-only deal-course selection with category/course/attribute selection.
- Non-goals: Admin image-upload migration, physical deletion of uploaded files, COS provider changes, database execution, BPM deployment, service restart, permission changes, and publication.
- Branch: `codex/workbench-approval-upload`
- Worktree: `D:\ZSJ-OS-worktrees\workbench-approval-upload`
- Base commit: `a02d1012bb`
- Target branch: `main`
- Ownership scope: `frontend/workbench` business forms, approval page and typed APIs; `frontend/admin` lead-filter audience/capability UI; `backend/yudao-module-bpm` task-query public filters; `backend/yudao-module-zsjos` approval filter APIs and service/query logic; additive SQL migration/bootstrap/schema/verification; affected API/architecture/operations documentation; focused tests.
- Owner: Codex `/root`
- Dependencies: Existing sales-order dual-approval, lead-filter, Infra file, product catalog, and BPM APIs in base commit `a02d1012bb`; V021-V028 migration chain remains an integration prerequisite.
- Integration order: Integrate this workstream after the committed baseline; rerun frontend, BPM, ZSJOS, and SQL checks on the integration branch.
- Verification plan: Focused TypeScript/service tests; workbench `npm test`, `npm run typecheck`, `npm run build`; BPM and ZSJOS focused Maven tests/compile; SQL diff/continuity/repeatability checks; browser checks at desktop/mobile widths when runtime is available.
- Status: `ready-to-merge`
- Final commit: `1f026e9958a360b1073119a15171c85f9decd01c`
- Verification evidence: Workbench tests, typecheck and production build passed; focused BPM/ZSJOS compile and tests passed; `git diff --check` passed; V029 was inspected without execution.
- Unresolved risks: Live COS/BPM behavior and authenticated desktop/mobile approval and lead-submission UI checks require an authorized runtime; Admin full typecheck remains blocked by baseline auto-import errors; V029 execution requires release approval and V021-V028 continuity.

## Delivery Entry

- Beijing time: `2026-08-11 15:49:34 +08:00`
- Branch: `codex/workbench-approval-upload`
- Worktree: `D:\ZSJ-OS-worktrees\workbench-approval-upload`
- HEAD commit: `a02d1012bba4c1d3d75e016a2539e6788f57e8a0` (implementation remains uncommitted as requested)
- User goal: Implement approval-person workbench layout/filtering, deferred attachment upload with progress/removal, visible business-field labels, and category/course/attribute sales-order entry.
- Key decisions: Reused the submitted-lead inbox structure; added reviewer audience and additive V029 defaults; kept COS upload and Infra APIs unchanged; local attachment deletion only removes current-form references; preserved legacy `handled` API compatibility; documented migration as forward-only and unexecuted.
- Execution result: Backend BPM task filtering, ZSJOS approval profile/search, Admin reviewer audience, workbench approval layout, shared course picker, deferred attachment state machine, labels, SQL bootstrap/migration/schema/verification artifacts, and API/architecture/operations documentation were updated.
- Changed files: `backend/yudao-module-bpm`, `backend/yudao-module-zsjos`, `frontend/admin`, `frontend/workbench`, `script/sql/mysql`, `docs/api/zsjos-sales-order.md`, `docs/architecture/data-and-permission-flow.md`, `docs/operations/sales-order-dual-approval-deployment.md`, and this handoff file.
- Verification evidence: Workbench `npm test` passed (13 files, 56 tests); `npm run typecheck` passed; `npm run build` passed; focused BPM/ZSJOS Maven compile/tests passed earlier (14 tests); `git diff --check` passed; SQL inspected read-only for V029 idempotence and bootstrap/schema/verify references. Admin `vue-tsc` is unverified because the repository baseline emits widespread missing auto-import errors in unchanged files; the pnpm wrapper also stopped at the repository's ignored-build-script policy.
- Dependency or integration impact: No new npm/Maven dependency; no database migration executed; V029 must follow V021-V028 and be verified on the integration branch; no BPM deployment, service restart, permission change, branch publication, or external state mutation performed.
- Remaining work: Authenticated desktop/mobile browser QA, live COS/BPM request verification, full Admin baseline typecheck remediation, and migration execution/verification require an environment and release approval.

## Delivery Entry

- Beijing time: `2026-08-11 16:02:13 +08:00`
- Branch: `codex/workbench-approval-upload`
- Worktree: `D:\ZSJ-OS-worktrees\workbench-approval-upload`
- HEAD commit: `a02d1012bba4c1d3d75e016a2539e6788f57e8a0`
- User goal: Stabilize intended-course selector, dropdown, and selected-course detail widths so long course content cannot resize the layout.
- Key decisions: Use two equal responsive selection columns plus a fixed 180px add-action column on desktop; collapse to one full-width column at 768px and below; force popup width to match its trigger; constrain dynamic text inside fixed-width containers.
- Execution result: Replaced content-sensitive Ant Row/Col/Space layout in `LeadIntendedProductEditor` with dedicated responsive grids, full-width controls and cards, ellipsis/wrapping rules, and native titles for complete long-text access. The shared component therefore behaves consistently in lead submission and lead basic-info editing.
- Changed files: `frontend/workbench/src/components/LeadIntendedProductEditor.tsx`, `frontend/workbench/src/styles.css`, and this handoff file.
- Verification evidence: Workbench `npm test` passed (13 files, 56 tests); `npm run typecheck` passed; `npm run build` passed; `git diff --check` passed. Browser navigation to `/zsjos/leads/submit` was redirected to the authorized appeal page because the current session lacks the submission menu, so authenticated desktop/mobile visual verification remains unavailable.
- Dependency or integration impact: No dependency, API, schema, permission, or business-data changes. Existing uncommitted workstream changes were preserved.
- Remaining work: Verify short and very long course names, dropdown widths, multiple selected-course cards, and the shared edit modal at desktop/mobile widths using an account authorized for lead submission.

## Integration Readiness

- Beijing time: `2026-08-11 16:07:52 +08:00`
- Branch: `codex/workbench-approval-upload`
- Worktree: `D:\ZSJ-OS-worktrees\workbench-approval-upload`
- HEAD commit: `1f026e9958a360b1073119a15171c85f9decd01c`
- User goal: Consolidate all local workstreams into `main` and clean up merged branches and worktrees.
- Key decisions: Treat `1f026e9958` as the final implementation commit; preserve the main worktree policy edits in their own commit; merge with history retained; do not push or execute migrations.
- Execution result: The implementation commit is complete and the workstream is marked `ready-to-merge` pending integration-branch verification.
- Changed files: `handoff/workbench-approval-upload.md`.
- Verification evidence: Workstream verification is recorded above; worktree is clean except for this readiness update.
- Dependency or integration impact: V029 remains additive and unexecuted; live COS/BPM and authorized browser validation remain release-environment responsibilities.
- Remaining work: Commit this readiness record, merge into `main`, rerun affected checks, mark the workstream `merged`, and remove merged worktrees/branches.
