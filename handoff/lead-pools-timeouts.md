# Workstream: lead-pools-timeouts

- ID: `lead-pools-timeouts`
- Goal: Implement phase three of the approved lifecycle plan: separate pre-qualification claim pool from post-qualification opportunity public sea, align timeout/release rules, and separate collaborator configuration from formal transfer.
- Non-goals: Submitter identity/reminder/complaint behavior, repurchase/order concurrency, duplicate-review changes beyond integration compatibility, customer merge, standalone opportunity management, and production database execution.
- Branch: `codex/lead-pools-timeouts`
- Worktree: `D:\ZSJ-OS-worktrees\lead-pools-timeouts`
- Base commit: `3898ecc19230a124502c52d890607394c8ce47b3`
- Target branch: `main`
- Ownership scope: ZSJOS Lead claim-pool and timeout behavior, Opportunity public-sea persistence/services/permissions/concurrency, formal transfer and collaborator separation, focused tests, additive MySQL V039/bootstrap/menu artifacts, Workbench public-sea/claim-pool UI integration, and directly affected lifecycle/API/navigation/deployment documentation.
- Owner: Codex `/root`
- Dependencies: Unified Person/Lead/Opportunity lifecycle through V037; duplicate-review V038; existing assignment, qualification, aging collaboration, subordinate-sales, order lock, task, System department/user, and ZSJOS object-permission facilities.
- Integration order: Phase three follows duplicate-lead-review and must merge before phases four and five.
- Verification plan: Focused backend timeout, claim-limit, public-sea visibility, collaborator, transfer, lock, order-freeze and object-permission tests; ZSJOS compile/tests; SQL static consistency and migration review; Workbench tests, typecheck, build, and browser checks when available; real MySQL/HTTP checks reported separately.
- Status: `ready-to-merge`

## Delivery Entries

### 2026-08-13 01:42:01 +08:00

- Branch: `codex/lead-pools-timeouts`
- Worktree: `D:\ZSJ-OS-worktrees\lead-pools-timeouts`
- HEAD commit: `3898ecc19230a124502c52d890607394c8ce47b3` (pre-delivery commit)
- User goal: Complete phase three of the approved lifecycle plan and continue serially without separate branch, commit, or merge confirmations.
- Key decisions: Claim pool remains pre-qualification and unowned; Opportunity public sea keeps formal Lead/Opportunity ownership; collaborator configuration remains visible to the whole current owner team and is distinct from formal transfer; public-sea visibility, manager authority, and candidates follow the formal owner's current department while the entry department is audit-only; active approval freezes public-sea assignment, exit, transfer requests, and duplicate order entry; same-team self-transfer uses BPM process `zsjos_lead_transfer_request`; daily proactive claims default to five per Beijing date; pre-qualification no-progress uses warning plus grace before claim-pool release.
- Execution or analysis result: Implemented claim quota counters, no-progress scanning, Opportunity follow-up based public-sea timing, owner/collaborator concurrency boundaries, formal transfer request records and BPM callback, current-team visibility, server-owned permissions/actions, admin/workbench controls, additive V039 migration, and directly affected API/architecture/state-machine documentation.
- Changed files: 51 repository files across `backend/yudao-module-zsjos`, focused tests, `frontend/admin`, `frontend/workbench`, `script/sql/mysql/bootstrap.sql`, `script/sql/mysql/migrations/V039__lead_pools_and_claim_limit.sql`, lifecycle/API/permission documentation, and this handoff.
- Verification evidence: Reactor compile passed; 60 focused backend tests passed with zero failures/errors; Workbench `npm run typecheck` and production build passed; Admin changed-file ESLint and production build passed; V039 static additive/repeatability contract and single bootstrap reference passed; Lead error codes are unique; `git diff --check` passed. The full reactor remains affected by the pre-existing unrelated Infra `CodegenEngineUniappTest.testExecute_treeSearch` failure. The Workbench full test suite remains affected by the existing main dependency tree missing `jsencrypt/lib/index.js`; 69 other tests previously passed.
- Dependency or integration impact: Requires additive V039 execution during a controlled deployment and a deployed BPM definition `zsjos_lead_transfer_request` with task key `ownerManagerReview`. No migration was executed and no shared service was changed. Phase four must start from the committed and integrated phase-three result.
- Remaining work: Commit and merge to `main`, rerun integration checks, then implement phases four and five in serial workstreams. Real MySQL execution, authenticated HTTP, Redis/concurrency integration, BPM deployment, and desktop/mobile browser verification remain unverified environment tasks.

### 2026-08-13 01:44:43 +08:00

- Branch: `main`
- Worktree: `D:\ZSJ-OS`
- HEAD commit: `901bb63d58f8c6ce5af0bb9736299b228d417412`
- User goal: Integrate completed phase three and continue automatically to later phases.
- Key decisions: Merged the isolated phase-three commit without rewriting history; retained V039 as an unexecuted additive deployment artifact; retained the BPM definition as an explicit deployment prerequisite.
- Execution or analysis result: Phase-three commit `44a7258989` merged through `901bb63d58`; integration verification passed on `main`.
- Changed files: `handoff/lead-pools-timeouts.md` only for this integration delivery entry; the merged product changes are listed in the preceding entry.
- Verification evidence: The same 60 focused backend tests passed on `main`; Workbench typecheck and production build passed; Admin changed-file ESLint and production build passed; V039 single-reference/non-destructive static check and `git diff --check` passed.
- Dependency or integration impact: Phase four may now branch from `901bb63d58`. No remote push, migration execution, BPM deployment, or shared-service mutation occurred.
- Remaining work: Commit this merged handoff entry, remove the phase-three worktree/branch, then implement phases four and five. External-environment verification risks remain unchanged.
- Status: `merged`
