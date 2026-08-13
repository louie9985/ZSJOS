# Lifecycle Hotfix V042

- Workstream ID: `lifecycle-hotfix-v042`
- Goal: repair legacy Lead inbox status JSON and prevent unauthorized BPM task requests from Today Tasks.
- Non-goals: grant BPM permissions, change BPM authorization, rewrite V037, or alter unrelated lifecycle behavior.
- Branch: `codex/lifecycle-hotfix-v042`
- Worktree: `D:\ZSJ-OS-worktrees\lifecycle-hotfix-v042`
- Base commit: `742bfa535b03783a5eb365249db5a50bbbe27c88`
- Target branch: `main`
- Ownership scope: `frontend/workbench/src/main.tsx`, `frontend/workbench/src/pages/TodayTasksPage.tsx`, the Today Tasks layout rule, focused frontend tests, Lead inbox filter compatibility service/tests, `script/sql/mysql` V042/baseline/verification files, directly affected documentation, and this handoff file.
- Owner: Codex `/root`
- Dependencies: V037 through V041 must be applied before V042.
- Integration order: apply after the lifecycle phases already integrated into `main`.
- Verification plan: focused frontend tests, typecheck, production build, migration static checks, repeatability against controlled MySQL, database verification, and real local database read-back.
- Final commit: this delivery commit
- Verification evidence:
  - Backend reactor build plus `LeadInboxFilterConfigServiceImplTest`: 11 tests passed.
  - Workbench Today Tasks permission tests: 2 passed; TypeScript check and production build passed.
  - Full Workbench suite: 17 suites and 71 tests passed; the unrelated `loginFormCache` suite could not resolve `jsencrypt` through the temporary cross-worktree dependency junction.
  - V042 applied twice to local `ruoyi-vue-pro`; the second execution preserved both filter JSON hashes and update timestamps.
  - Database read-back found V042 in both version tables and no `converted` scalar in active submitter/owner draft or published configurations.
  - A 107,629,463-byte logical backup was created at `D:\ZSJ-OS-worktrees\lifecycle-hotfix-v042\backups\ruoyi-vue-pro-before-v042-20260813-102356.sql` before migration.
- Unresolved risks:
  - Authenticated desktop/mobile browser verification was unavailable on the isolated Vite origin; only login-page reachability was verified.
  - `zsjos_db.py check` remains blocked by pre-existing Core baseline omissions for `zsjos_lead_claim_daily_counter` and `zsjos_lead_transfer_request`.
  - Full `verify-bootstrap.sql` reports pre-existing local seed/version failures; the new `lead_filter_status_v042` check passes.
  - User 231 gained `bpm:task:query` from an administrator-created role-menu grant at 10:04:12, outside this workstream; this workstream did not modify permissions.
- Dependency state: V037 through V041 are present in the local database; V042 is now applied locally.
- Status: ready-to-merge

## Delivery Entries

### 2026-08-13 10:34:35 +08:00

- Branch: `codex/lifecycle-hotfix-v042`
- Worktree: `D:\ZSJ-OS-worktrees\lifecycle-hotfix-v042`
- HEAD commit: this delivery commit
- User goal: diagnose and repair the missing Lead migration state, invalid inbox filter configuration, and unauthorized BPM task requests.
- Key decisions: preserve BPM authorization; gate the BPM panel with server permissions; add forward-only V042 instead of rewriting V037; preserve immutable filter history and normalize it only when read or republished.
- Execution or analysis result: implementation complete; V042 applied successfully to the confirmed local database after logical backup.
- Changed files: Lead inbox filter service/tests; Today Tasks page, shell wiring, layout and test; V042, bootstrap seed/order and verification SQL; permission-flow and migration documentation; this handoff.
- Verification evidence: backend 11/11; frontend focused 2/2, typecheck and build passed; V042 repeatability and read-back passed; details recorded above.
- Dependency or integration impact: adds Core migration V042; no new library dependency; requires integration after phase-five lifecycle commits.
- Remaining work: merge into `main`, rerun affected integration checks, and record the merge commit.
