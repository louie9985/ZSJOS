# Workstream Handoff: form-required-markers

- Workstream ID: `form-required-markers`
- Status: `implemented-uncommitted`
- Goal: Ensure every required field on active Zhongshijian workbench and administration business forms displays a red required marker beside its label.
- Non-goals: Changing validation semantics, submission behavior, backend contracts, upstream ERP/MES/IoT forms, or unrelated visual styling.
- Branch: `codex/form-required-markers`
- Worktree: `D:\ZSJ-OS-worktrees\form-required-markers`
- Base commit: `6ec2a9b53c3e8c831caa0902c88969fc69011331`
- Target branch: `main`
- Ownership scope: `frontend/workbench` form views/components and focused tests; `frontend/admin/src/views/zsjos` form views/components and focused tests; directly necessary shared form styling/configuration; this handoff file.
- Owner: Codex form-required-markers workstream
- Dependencies: Existing Ant Design 6 and Element Plus form required-marker behavior; current server-owned form options and validation rules.
- Integration order: Merge after any active workstream that owns an overlapping form file; otherwise independent.
- Verification plan: Static required-rule/marker audit, focused tests, workbench test/typecheck/build, admin typecheck/lint/build, and desktop/mobile browser checks of representative static, conditional, dynamic, and grouped-required forms.

## Entries

### 2026-08-14 10:53:59 CST

- Branch: `codex/form-required-markers`
- Worktree: `D:\ZSJ-OS-worktrees\form-required-markers`
- HEAD commit: `6ec2a9b53c3e8c831caa0902c88969fc69011331`
- User goal: Ensure every required field in active Zhongshijian workbench and administration business forms displays a red `*` beside its label.
- Key decisions: Scoped changes to `frontend/workbench` and ZSJOS-owned `frontend/admin/src/views/zsjos` forms; preserved validation and submission behavior; used conditional markers for either-or contact fields and approval-dependent reasons; added native labels for required inline product attribute fields; excluded upstream ERP/MES/IoT forms and non-form table selections.
- Execution or analysis result: Audited required rules and manual submit guards, added required markers to 23 UI source files and a shared workbench section-marker style, and preserved unrelated workspace changes. Browser routes were checked unauthenticated and correctly redirected to login.
- Changed files: `frontend/workbench/src/{components/LeadBasicInfoModal.tsx,components/SalesOrderEntryModal.tsx,pages/ExternalRepurchasePage.tsx,pages/LeadAgingPoolPage.tsx,pages/LeadManagementPage.tsx,pages/LeadSubmissionPage.tsx,pages/MySalesOrderPage.tsx,pages/SalesOrderApprovalPage.tsx,pages/WorkPlanPage.tsx,styles.css}`; ZSJOS Vue form files under `frontend/admin/src/views/zsjos/{components,impersonation,leadAgingPool,leadAppeal,leadComplaint,leadDuplicateReview,mySalesOrder,partner,product,salesOrderApproval,subordinateSales,withdrawal,workPlan,workPlanConfig}`; this handoff file.
- Verification evidence: Workbench `npm run typecheck` passed; workbench production build passed; 20 test files / 82 tests passed, with only the pre-existing `loginFormCache.test.ts` blocked by missing `D:/ZSJ-OS/frontend/workbench/node_modules/jsencrypt/lib/index.js`; admin changed-file ESLint passed; admin Vite `env.local` production build passed with the existing Lightning CSS `*zoom` warning; `git diff --check` passed; browser checks at desktop and `390x844` found nonblank pages, no horizontal overflow, and no page console errors, but authenticated business-form visual states were not exercised.
- Dependency or integration impact: No package, API, backend, database, permission, or shared service changes. Temporary ignored `node_modules` junction was used for workbench verification only; no lockfiles were changed.
- Remaining work: Run authenticated desktop/mobile browser checks against representative workbench and admin forms; resolve the pre-existing `jsencrypt` installation issue if a fully green workbench test run is required. No commit, merge, push, or publication performed.

### 2026-08-14 16:00:00 +08:00
- Branch: `codex/form-required-markers`
- Worktree: `D:\ZSJ-OS-worktrees\form-required-markers`
- HEAD commit: `6ec2a9b53c3e8c831caa0902c88969fc69011331`
- User goal: Commit and integrate all completed workstreams into local main.
- Key decisions: Preserve validation behavior and apply marker-only changes after overlapping Lead and order workstreams.
- Execution or analysis result: Integration authorization received; workstream marked ready to merge.
- Changed files: Existing required-marker implementation and this handoff.
- Verification evidence: Existing Workbench/Admin test, lint, typecheck, build, browser smoke, and diff-check evidence remains applicable; integrated checks will be rerun on main.
- Dependency or integration impact: UI-only; overlaps several Lead/order pages and shared Workbench styles.
- Remaining work: Create the feature commit, record it, merge after overlapping business workstreams, and run integrated verification.
- Status: `ready-to-merge`
