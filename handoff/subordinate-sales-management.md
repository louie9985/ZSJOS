# Workstream Handoff: subordinate-sales-management

- Workstream ID: `subordinate-sales-management`
- Status: `ready-to-merge`
- Goal: Implement the approved server-authorized subordinate-sales management list, detail tabs, status controls, metrics, and reason-required partial-success batch lead operations.
- Non-goals: Execute database migrations, add a standalone public-sea menu, implement newcomer-pool routing, add follow-up completion rate, change external/shared services, push, or merge into `main`.
- Branch: `codex/subordinate-sales-management`
- Worktree: `D:\ZSJ-OS-worktrees\subordinate-sales-management`
- Base commit: `90b2c3dfd8c08590840af9dd146bbaf23b20234a`
- Target branch: `codex/subordinate-sales-integration`
- Ownership scope: ZSJOS subordinate-sales backend APIs/services/data model/tests; required System public user API extension; Workbench subordinate-sales UI/services/tests; V035 migration and synchronized fresh bootstrap/verification artifacts; directly affected architecture/API/state-machine documentation; narrow ZSJOS durable rule; this handoff file.
- Owner: Codex `/root`
- Dependencies: Integrated aging collaboration pool, invalid-lead task cleanup, and lead-inbox lazy-loading behavior at base commit `90b2c3dfd8`.
- Integration order: System public API and persistence contract, ZSJOS services/controllers/tests, SQL/menu grants, Workbench UI/tests, documentation, then full verification.
- Verification plan: Focused System and ZSJOS Maven tests and compile; backend contract/authorization tests; database static/repeatability checks without execution; Workbench unit tests/typecheck/build; desktop and mobile browser checks against a local development server; `git diff --check` and final scope review.
- Final feature commit: `b11223b44f44b9a01e3e61912fc04d788fc5a09a`
- Unresolved risks: Authenticated browser and real authorized/unauthorized HTTP verification require an applied V035 migration, test account, and available MySQL/Redis environment; migration execution remains intentionally excluded.
- Dependency state: Base dependencies are integrated at `90b2c3dfd8`; this workstream is self-contained and ready for integration into `codex/subordinate-sales-integration` after deployment review.

## Delivery Entries

### 2026-08-12 15:48:06 +08:00

- Branch: `codex/subordinate-sales-management`
- Worktree: `D:\ZSJ-OS-worktrees\subordinate-sales-management`
- HEAD commit: `90b2c3dfd8b50d18165e9ae129f659829f67ce60`
- User goal: Implement the approved subordinate-sales management capability and replace drawer-based navigation with an inbox layout that shows employee summaries on the left and persistent sales or lead detail on the right.
- Key decisions: Authorization uses menu permissions plus live responsible-department descendant scope; manual public sea preserves lead owner/status/assignment state; all mutations require a trimmed reason; batches are limited to 200 and partially succeed per lead; the Workbench uses a desktop two-pane inbox and mobile list/detail switching with no drawer in `SubordinateSalesPage`.
- Execution or analysis result: Added System public user account support, ZSJOS query/statistics/status/batch APIs, audit and manual-public-sea persistence, server-owned menu permissions, Workbench inbox UI and API bindings, tests, migration/bootstrap verification artifacts, and directly affected documentation. The V035 migration was not executed.
- Changed files: System public user API DTO/interface/implementation; ZSJOS subordinate controller/VO/service/DAL/DO/constants and focused tests; lead dispatch/management/status services and mappers; `backend/yudao-module-zsjos/AGENTS.md`; `frontend/workbench/src/pages/SubordinateSalesPage.tsx`, service/API/route/registry/style/test files and TypeScript build metadata; V035 plus bootstrap/schema/seed/verification tooling; subordinate-sales API, permission-flow, state-machine, migration, and this handoff documentation.
- Verification evidence: Workbench `npm run typecheck` passed; 71 Vitest tests passed; production build passed with the existing large-chunk warning; 14 focused ZSJOS tests passed; `yudao-server` and its 25-module dependency graph packaged successfully; `python script/sql/mysql/tools/zsjos_db.py check` passed; `git diff --check` passed; browser checks at 1440x900 and 390x844 found no horizontal overflow or console errors on the available login shell.
- Dependency or integration impact: Adds System public API fields/mutation overload, ZSJOS tables and menu permissions through repeatable V035, and a new server-authorized Workbench route. Target integration branch remains `codex/subordinate-sales-integration`; no migration, push, or merge was performed.
- Remaining work: Apply V035 only through the separately approved deployment process, then verify authorized and unauthorized HTTP requests and the authenticated inbox page with real department, permission, dictionary, task, lead, order, MySQL, Redis, and token-revocation data. Browser verification of the protected feature remains pending because no test account was available and V035 was intentionally not executed.
