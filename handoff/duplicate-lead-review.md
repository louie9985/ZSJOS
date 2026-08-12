# Workstream: duplicate-lead-review

- ID: `duplicate-lead-review`
- Goal: Implement phase two of the approved lifecycle plan: duplicate Lead detection, shared review queue, four structured decisions, reactivation, and strict contact-change duplicate prevention.
- Non-goals: Claim-pool/public-sea timeout changes, submitter complaints and reminders, repurchase/order concurrency, customer merge, standalone customer/opportunity management, and any production database execution.
- Branch: `codex/duplicate-lead-review`
- Worktree: `D:\ZSJ-OS-worktrees\duplicate-lead-review`
- Base commit: `8afabffadb3886601318071c0df6e6d7e0ca8a0c`
- Target branch: `main`
- Ownership scope: ZSJOS duplicate-match/review domain, Lead submission and contact-change integration, reactivation/qualification restoration, focused permissions and tests, additive MySQL V038/bootstrap/menu artifacts, workbench review page/API/route, and directly affected lifecycle/API/navigation/deployment documentation.
- Owner: Codex `/root`
- Dependencies: Phase-one unified Person/Lead/Opportunity model at V037; System public user/department APIs; Infra public file API; existing ZSJOS object-permission and qualification boundaries.
- Integration order: Phase two follows lifecycle-domain-unification and must merge before phases three through five.
- Verification plan: Focused backend service, controller-permission, object-scope, state-transition, tenant and concurrent-decision tests; ZSJOS module tests/compile; SQL static consistency, migration continuity and repeatability review; workbench unit tests, typecheck and production build; desktop/mobile browser checks when a runnable local environment is available; real HTTP and MySQL checks reported separately if unavailable.
- Status: `ready-to-merge`

## Delivery Entries

### 2026-08-13 00:06:29 +08:00

- Branch: `main`
- Worktree: `D:\ZSJ-OS`
- HEAD commit: `3ce30a9e6951201a771f1d785b7363aa91fecd22`
- User goal: Automatically commit and merge completed phase two, then continue the approved later phases without separate Git confirmations.
- Key decisions: Preserved phase two as an auditable feature commit and a non-fast-forward integration merge; no remote push or database execution was included.
- Execution or analysis result: `codex/duplicate-lead-review` commit `cf1bedebf3` was merged into `main` as `3ce30a9e69`. The integration tree is clean and phase two is merged.
- Changed files: This handoff integration entry only; the merged implementation files are recorded in the preceding entry.
- Verification evidence: On merged `main`, SQL consistency check passed; `LeadDuplicateMatcherTest` and `LeadDuplicateReviewControllerPermissionTest` passed 4/4 through the Reactor; Workbench TypeScript typecheck passed.
- Dependency or integration impact: `main` now owns schema baseline V038. Phase three must use V039 or later and start from `3ce30a9e6951201a771f1d785b7363aa91fecd22` after this handoff commit.
- Remaining work: Commit this integration log, remove the merged phase-two worktree/branch, and create the isolated phase-three workstream. Production MySQL, authenticated HTTP, database-backed concurrency, and responsive browser checks remain unexecuted.

### 2026-08-13 00:02:09 +08:00

- Branch: `codex/duplicate-lead-review`
- Worktree: `D:\ZSJ-OS-worktrees\duplicate-lead-review`
- HEAD commit: `8afabffadb3886601318071c0df6e6d7e0ca8a0c`
- User goal: Continue phase two of the approved lifecycle plan, then automatically continue phases three through five without separate branch, commit, or merge confirmations.
- Key decisions: Active same-field mobile/WeChat matches are rejected without a task; weak, historical, or Person-only matches create a shared tenant review task; the four decisions are fixed and validated against the captured candidates; review decisions lock the task so the first submission wins; reactivation overwrites current Person/Lead data, records a real assignment history, assigns an in-scope enabled salesperson, returns to first-follow-up pending, leaves the old Opportunity lost until requalification, and clears old manual public-sea collaboration; contact edits reuse the same matcher and reject every strong or weak match; historical LeadActivation remains read-only compatibility data; review permissions are independent and no role receives automatic grants.
- Execution or analysis result: Implemented duplicate matching, review persistence and APIs, independent feature/object permissions, structured outcomes, attachment audit snapshots, strict contact-change validation, reactivation state reset, V038 schema/menu migration, workbench queue/detail/decision UI, submission result handling, and directly affected API/state-machine documentation. No database migration, push, shared service change, or production request was performed.
- Changed files: ZSJOS duplicate-review Controller/VO/DO/Mapper/Service/permission provider and matcher; Lead submission/basic-info integration; focused tests; Workbench duplicate-review page, API/types/routes and submission feedback; MySQL V038, schema/bootstrap/menu/version artifacts; lifecycle and submission API documentation; this handoff file.
- Verification evidence: Backend Reactor focused tests passed 14/14 (`LeadDuplicateMatcherTest`, controller permission, submission, and basic-info tests); backend compile passed as part of the test reactor; Workbench tests passed 78/78; TypeScript typecheck passed; production build passed with the existing large-chunk warning; `python script/sql/mysql/tools/zsjos_db.py check` passed manifests, migration order, desired schema, Java mappings, baseline versions, and verification; `git diff --check` passed. Browser connection succeeded but the available browser session had no local tab and its exposed interface could not open one, so desktop/mobile visual rendering and authenticated real-request checks remain unverified.
- Dependency or integration impact: Adds V038 after lifecycle V037 and introduces menu permissions `zsjos:lead-duplicate-review:query`, `process`, and `manage-all` without role binding. Phase three must start from the committed and merged phase-two baseline. Existing npm lockfile dependencies only were installed with `npm ci --ignore-scripts`; no dependency or lockfile changed.
- Remaining work: Commit and merge phase two, rerun focused integration checks on `main`, then create the isolated phase-three workstream. Before production rollout, execute V038 only after a controlled database review and explicitly assign the independent review permissions. Real MySQL, authenticated HTTP, concurrency against MySQL, and responsive review-page browser checks remain outstanding environment checks.
