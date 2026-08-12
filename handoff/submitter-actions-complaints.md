# Workstream: submitter-actions-complaints

- ID: `submitter-actions-complaints`
- Goal: Implement phase four of the approved lifecycle plan: restrict ordinary submission identities, add a sales self-sourced entry, preserve submitter historical rights, and add daily urge, profile supplement/strict contact edit, and independent sales complaint capabilities.
- Non-goals: Repurchase/order classification and concurrency, customer merge, refund/cashback/export/maintenance, WeCom/email delivery, expanded submitter visibility into sales follow-up/order approval data, or changes to the existing three-round appeal policy.
- Branch: `codex/submitter-actions-complaints`
- Worktree: `D:\ZSJ-OS-worktrees\submitter-actions-complaints`
- Base commit: `299458eacd7a6e3fe1b3f043f8a53aa1b4e78f12`
- Target branch: `main`
- Ownership scope: ZSJOS Lead submission authorization and entry contracts, submitter actions and audit persistence, complaint records/BPM or queue integration, focused backend tests, additive MySQL V040/bootstrap/menu/permission artifacts, Workbench/Admin UI directly required by phase four, and directly affected API/permission/state-machine documentation.
- Owner: Codex `/root`
- Dependencies: Integrated lifecycle phases one through three; System user/post/department APIs; existing assignment/self-sourced, basic-info/contact duplicate checks, notification, object-permission, file-reference, task, and BPM facilities.
- Integration order: Phase four follows phase three and must merge before phase five.
- Verification plan: Focused authorization, identity, submitter-history, urge idempotency/day-boundary, strict contact conflict, complaint queue/concurrency/permission/BPM tests; ZSJOS compile/tests; SQL static consistency; frontend typecheck/build/tests where environment permits; browser and real request checks when an environment is available.
- Status: `ready-to-merge`

## Delivery Entries

### 2026-08-13 03:04:25 +08:00

- Branch: `codex/submitter-actions-complaints`
- Worktree: `D:\ZSJ-OS-worktrees\submitter-actions-complaints`
- HEAD commit: `299458eacdc93623c0c8deb75c37d191320d01d3` (pre-delivery commit)
- User goal: Complete lifecycle phase four and continue automatically through integration toward phase five.
- Key decisions: Ordinary submission is limited to stable new-media/manager/partner identities; sales self-sourced entry is separate and direct-owned; duplicate review snapshots and preserves the original submission channel; historical submitter rights follow immutable source user while the account/business subject remains enabled; complaints are a locked shared business queue outside BPM; founded complaints notify sales and the current direct leader without side effects.
- Execution or analysis result: Implemented backend submission authorization, self-sourced dispatch, submitter supplement/urge/complaint actions, complaint locking, validated evidence, notification scenes, current-owner-team public-sea read alignment, server-owned menus/permissions, Workbench pages/actions, V040, bootstrap baselines, API/permission/state documentation, and focused tests.
- Changed files: ZSJOS Lead controllers/services/DOs/mappers/VOs/constants/tests; Workbench submission/Lead detail/complaint page/API/routes; `script/sql/mysql` V040/bootstrap/schema/seed; directly affected API, permission, and state-machine docs; this handoff.
- Verification evidence: Backend reactor compile passed; 57 focused backend tests passed; Workbench typecheck passed; 17 test files / 78 tests passed; Workbench production build passed; `git diff --check` passed; V040 references and baseline table/column presence checked statically in migration, bootstrap, core schema, and seed files.
- Dependency or integration impact: Requires integrated phases one through three and migration V040 after V039. No role grants are inferred. No database migration, remote push, or shared service change was executed.
- Remaining work: Commit and merge phase four, rerun integration checks, then implement phase five. Browser desktop/mobile interaction remains unverified because the available browser binding exposed no open-tab operation and had no existing tab.
