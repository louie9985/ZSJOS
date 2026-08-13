# Employee Avatar Unification Workstream

- Workstream ID: `employee-avatar-unification`
- Goal: Unify System-owned employee avatar rendering in the Workbench, allow administrators to manage each user's avatar, and add a global configurable default employee avatar.
- Non-goals: Create ZSJOS-owned user/avatar persistence, backfill `system_users.avatar`, add tenant-specific defaults, add realtime avatar events, change business-object avatars, apply migrations, restart services, commit, merge, or push without separate confirmation.
- Branch: `codex/employee-avatar-unification`
- Worktree: `D:\ZSJ-OS-worktrees\employee-avatar-unification`
- Base commit: `870ed7e862d44330cb52beca0a0370b7aa5efa62`
- Target branch: `main`
- Ownership scope: Infra default-avatar configuration API and tests; System permission response and administrator user avatar UI; ZSJOS subordinate-sales avatar projection; Workbench employee-avatar component/context and scoped employee avatar surfaces; additive MySQL V044/baseline/verification artifacts; directly affected API, architecture, and migration documentation; this handoff file.
- Owner: Codex `/root`
- Dependencies: Integrate after order lifecycle V043; this workstream owns `V044__default_employee_avatar.sql`.
- Integration order: V042 lifecycle hotfix, V043 order lifecycle fixes, then V044 employee avatars; rerun migration continuity and affected backend/frontend checks on the integration branch.
- Verification plan: Focused Infra/System/ZSJOS tests; backend compile/package as appropriate; Admin scoped lint/type/build checks; Workbench unit tests, typecheck, and production build; SQL manifest/schema/bootstrap/verification checks and repeatability review; desktop/mobile browser checks for header, assignment, and subordinate-sales employee avatars when runnable services are available.
- Status: `ready-to-merge`

## Delivery Entry - 2026-08-13 11:18:00 +08:00

- Beijing time: `2026-08-13 11:18:00 +08:00`
- Branch: `codex/employee-avatar-unification`
- Worktree: `D:\ZSJ-OS-worktrees\employee-avatar-unification`
- HEAD commit: `870ed7e862d44330cb52beca0a0370b7aa5efa62` (no task commit created)
- User goal: Implement unified employee avatars across Admin and Workbench, including administrator-managed personal avatars and a global default employee avatar.
- Key decisions: Keep personal avatars in `system_users.avatar`; store only the global fallback in fixed Infra key `zsjos.user.default-avatar`; render `personal > default > nickname initial`; use additive repeatable migration V043 because V042 is owned by another workstream; do not add realtime propagation or change non-employee avatars.
- Execution or analysis result: Added dedicated Infra read/update APIs and permissions, System permission-info projection, ZSJOS subordinate-sales avatar projection, Admin personal/default avatar management, Workbench shared fallback component and scoped integrations, repeatable SQL/baseline updates, tests, and directly affected documentation. No migration was applied and no database or shared service was changed.
- Changed files: Infra Config API/controller/service/VO/tests; System Auth controller/permission VO/test; ZSJOS subordinate-sales VO/service/test; Admin Infra API and System user views; Workbench employee-avatar component/test, shell, assignment and subordinate-sales pages, API types and contract; MySQL V043/bootstrap/schema/verification/migration README; architecture documentation; this handoff file.
- Verification evidence: Infra focused tests passed (20); System Auth focused test passed (1); ZSJOS subordinate-sales focused tests passed (3); affected backend Reactor compile passed; Admin scoped ESLint and Prettier passed; Admin `build:local` passed with the existing LightningCSS `*zoom` warning; Admin full `ts:check` remains blocked by repository-wide missing auto-import declarations, and the four target files only reported the same `ref`/`computed`/`useMessage` class of existing errors; Workbench avatar tests passed (5), `npm run typecheck` passed, and `npm run build` passed with the existing large-chunk warning; Workbench full suite previously passed 75 tests with one unrelated suite blocked by missing `jsencrypt/lib/index.js`; `git diff --check` passed; V043 static repeatability/baseline/order assertions passed; repository database check remains blocked by pre-existing missing Core mappings for `zsjos_lead_claim_daily_counter` and `zsjos_lead_transfer_request`; unauthenticated desktop 1280x720 and mobile 390x844 browser checks showed no horizontal overflow.
- Dependency or integration impact: Must integrate after `codex/lifecycle-hotfix-v042`; then rerun migration continuity, SQL verification, affected backend tests/compile, Admin checks, Workbench tests/typecheck/build, and authenticated browser checks. Workbench task-local preview remains available at `http://127.0.0.1:5186/` (process 27428) and was not stopped.
- Remaining work: Obtain authorization to commit; record final commit and change status to `ready-to-merge`; integrate after V042; run controlled migration validation without mutating shared data; verify authenticated header, assignment, subordinate-sales list/detail, personal-avatar clearing, default-avatar clearing, authorization rejection, and broken remote images against a runnable signed-in environment.

## Delivery Entry - 2026-08-13 12:45:00 +08:00

- Beijing time: `2026-08-13 12:45:00 +08:00`
- Branch: `codex/employee-avatar-unification`
- Worktree: `D:\ZSJ-OS-worktrees\employee-avatar-unification`
- HEAD commit: `870ed7e862d44330cb52beca0a0370b7aa5efa62` (no task commit created)
- User goal: Apply the code-review repair plan for employee avatar selection coverage, Admin fallback labeling, and missing default-avatar configuration concurrency.
- Key decisions: Added a shared `EmployeeSelect` that keeps avatar-bearing employee records and renders personal/default/initial fallbacks; accepted the common `SalesUser` shape so no synthetic status field is added; runtime default-avatar update now only updates the V043-provisioned fixed key and raises `CONFIG_NOT_EXISTS` when the key is absent; Admin distinguishes “使用默认” from “昵称首字”.
- Execution or analysis result: Integrated avatar rendering into specified-sales submission, duplicate-review assignment, qualification transfer, subordinate-sales batch transfer/collaboration, dispatch relation candidates/selected users, and bound-sales tags. Added missing-config Infra test and preserved fixed-key isolation. No migration, database mutation, service restart, commit, or push was performed.
- Changed files: `ConfigServiceImpl.java`, `ConfigServiceImplTest.java`, Admin system user list, new `EmployeeSelect.tsx`, Workbench LeadSubmission/LeadDuplicateReview/LeadQualificationException/LeadAssignment/SubordinateSales pages, and this handoff file.
- Verification evidence: Workbench `npm run typecheck` passed; Workbench production `npm run build` passed with the existing large-chunk warning; avatar-focused tests passed (5); full Workbench suite passed 76 tests with one unrelated existing `jsencrypt/lib/index.js` missing-module suite failure; Infra `ConfigServiceImplTest` passed through Reactor with `-Dsurefire.failIfNoSpecifiedTests=false`; Admin scoped ESLint and Prettier passed; `git diff --check` passed; `verify-bootstrap.sql` contains an exact count assertion requiring one active V043 default-avatar config; tracked Workbench build cache was restored to HEAD.
- Dependency or integration impact: No new dependency. Existing V043/V042 integration ordering is unchanged. Authenticated browser verification of the newly added dropdown renderers remains pending because no signed-in session was available; the existing Workbench preview remains at `http://127.0.0.1:5186/`.
- Remaining work: Run authenticated desktop/mobile browser checks for all employee selectors and broken-image fallback; integrate after V042 and rerun migration continuity plus affected checks; obtain separate authorization before commit, merge, push, or migration execution.

## Delivery Entry - 2026-08-13 13:10:00 +08:00

- Beijing time: `2026-08-13 13:10:00 +08:00`
- Branch: `codex/employee-avatar-unification`
- Worktree: `D:\ZSJ-OS-worktrees\employee-avatar-unification`
- HEAD commit: `cc78eca3197c37a7a03ca53063b338c42e5525d1`
- User goal: Commit and integrate all active workstreams into `main`.
- Key decisions: Renumbered the employee avatar migration from V043 to V044 because order lifecycle review fixes own V043; historical delivery entries remain immutable and this entry records the correction.
- Execution or analysis result: Committed the verified employee avatar implementation as `cc78eca319`; workstream is ready for merge after order lifecycle V043.
- Changed files: V044 migration naming/version references, current handoff metadata, and this delivery entry; implementation files are recorded in preceding entries and commit.
- Verification evidence: Version search found no active SQL/bootstrap reference to `V043__default_employee_avatar`; `git diff --check` passed before commit; prior backend/frontend tests and builds remain applicable.
- Dependency or integration impact: Merge current `main` containing order V043 into this branch, preserve both migrations, and resolve shared bootstrap/schema/Workbench files before final integration.
- Remaining work: Merge latest `main`, resolve overlaps, rerun checks, merge to `main`, and record final merged status.

## Delivery Entry - 2026-08-13 13:18:00 +08:00

- Beijing time: `2026-08-13 13:18:00 +08:00`
- Branch: `main`
- Worktree: `D:\ZSJ-OS`
- HEAD commit: `7e364c4dc5080c80008f24cc442250d90a2d27a6`
- User goal: Merge all active branches and registered worktrees into `main`, then clean up the merged worktrees and local branches.
- Key decisions: Preserved order lifecycle as V043 and renumbered default employee avatar to V044; resolved shared bootstrap verification to require both versions and exactly 23 module versions; retained both order lifecycle behavior and employee avatar rendering in overlapping Workbench files.
- Execution or analysis result: Merged current `main` into `codex/employee-avatar-unification` as `a2b5d3dd72`, then merged the verified avatar branch into `main` as `7e364c4dc5`. Status is `merged`.
- Changed files: This handoff delivery entry; integrated implementation and migration files are recorded in preceding entries and merge commits.
- Verification evidence: Combined Infra/System/ZSJOS Reactor focused tests passed (44); Workbench avatar tests passed (5), all executable tests passed (76), typecheck and production build passed; the unrelated `loginFormCache.test.ts` suite remains unloadable because `jsencrypt/lib/index.js` is missing; Admin target ESLint and Prettier checks and `build:local` passed; `git diff --check` passed; schema baselines are byte-identical; bootstrap sources V042, V043, then V044.
- Dependency or integration impact: No new npm or Maven dependency. V044 depends on V043 being integrated first. No push, database migration, real configuration mutation, or preview service restart was performed.
- Remaining work: Authenticated desktop/mobile browser checks and real MySQL migration execution remain pending for an authorized environment; local registered worktree and merged branch cleanup follows this handoff commit.
