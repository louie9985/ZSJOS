# Workstream Handoff: lead-appeal-routing

- Workstream ID: `lead-appeal-routing`
- Status: `active`
- Goal: Make the server-authorized sales-manager appeal inbox reachable and reliable, correct stale-route behavior after account changes, and verify the local tenant configuration and BPM deployment required for first-round appeals.
- Non-goals: Submitting lead `8` or creating appeal/business records; changing database schema; changing unrelated menus, roles, departments, or users; modifying the existing dirty `main` worktree; pushing or merging the branch; publishing artifacts.
- Branch: `codex/lead-appeal-routing`
- Worktree: `D:\ZSJ-OS-worktrees\lead-appeal-routing`
- Base commit: `d3459e2c064d02121315d233c0f52bfa0ec25d16`
- Target branch: `main`
- Ownership scope: `frontend/workbench/src/main.tsx`; `frontend/workbench/src/constants.ts` if retained by the final route-binding design; focused frontend route/menu registry and tests under `frontend/workbench/src/`; `backend/yudao-module-bpm/src/main/java/cn/iocoder/yudao/module/bpm/api/task/BpmProcessTaskApiImpl.java`; focused BPM API tests under `backend/yudao-module-bpm/src/test/`; directly affected appeal permission/routing documentation; this handoff file.
- Owner: Codex `lead-appeal-routing` workstream.
- Dependencies: Existing system permission/menu APIs, `yudao-module-bpm` public task API, deployed process definition key `zsjos_lead_appeal_review`, and tenant `1` organization configuration. No new code dependency is planned.
- Integration order: Integrate after the existing `main` workstream reaches a committed point or resolve any target-branch conflicts explicitly; this workstream does not depend on its uncommitted changes.
- Verification plan: Focused frontend unit tests, full workbench test/typecheck/production build, desktop and mobile browser checks with the authorized sales-manager account, focused BPM API unit tests and module compilation, authenticated pending/handled inbox requests including empty states, unauthorized request coverage, read-only process-definition verification, confirmed department-leader update, local backend rebuild/restart, and post-restart runtime checks. Do not submit lead `8`.

## Entries

### 2026-08-10 21:25:22 +08:00

- Branch: `codex/lead-appeal-routing`
- Worktree: `D:\ZSJ-OS-worktrees\lead-appeal-routing`
- HEAD commit: `d3459e2c064d02121315d233c0f52bfa0ec25d16`
- User goal: Migrate the server-issued sales-manager appeal menu to a real frontend page, verify the sales-manager account permissions and reviewer routing, correct the failed appeal prerequisites, and execute the confirmed verification plan without submitting lead `8`.
- Key decisions: Bind server menu component metadata through a frontend component registry; redirect inaccessible stale routes to the first authorized internal route; return empty BPM task pages before issuing a Flowable query with an empty process-instance set; configure department `1021` leader as user `233`; do not grant additional permissions, submit an appeal, initialize schema, or deploy a process definition without an account authorized for BPM model management.
- Execution or analysis result: The appeal page is reachable from the server menu and renders a normal empty state on the rebuilt backend. The authorized account has `zsjos:lead:appeal:query` and `zsjos:lead:appeal:review-sales-manager`. Department `1021` now resolves user `233` as reviewer. Flowable schema exists, but process definition key `zsjos_lead_appeal_review` has zero deployed versions. The supplied sales-manager account has no BPM model create/deploy permission, so process deployment remains blocked by missing authorized administration access.
- Changed files: `backend/yudao-module-bpm/src/main/java/cn/iocoder/yudao/module/bpm/api/task/BpmProcessTaskApiImpl.java`; `backend/yudao-module-bpm/src/test/java/cn/iocoder/yudao/module/bpm/api/task/BpmProcessTaskApiImplTest.java`; `frontend/workbench/src/main.tsx`; `frontend/workbench/src/services/menu.ts`; `frontend/workbench/src/services/menu.test.ts`; `frontend/workbench/src/services/menuComponentRegistry.ts`; `frontend/workbench/src/services/menuComponentRegistry.test.ts`; `frontend/workbench/tsconfig.tsbuildinfo`; `docs/api/zsjos-lead-appeal.md`; `docs/architecture/data-and-permission-flow.md`; `handoff/lead-appeal-routing.md`. Local verification-only `.env.local` is uncommitted.
- Verification evidence: Focused BPM tests passed (`2`); full workbench tests passed (`49`); frontend typecheck passed; frontend production build passed; Maven BPM reactor test/build passed; full `yudao-server` reactor package passed; rebuilt server started on `48081`; authenticated appeal inbox returned `code=0`, `total=0`; unauthenticated inbox returned business code `401`; browser login redirected to `/zsjos/appeals`, rendered `申诉处理` with `暂无申诉` and no system-error alert against the rebuilt backend; stale `/zsjos/tasks/today` redirected to `/zsjos/appeals`; database read-back confirmed department `1021` leader user `233`; Flowable tables exist and the target process-definition count is `0`; `git diff --check` passed.
- Dependency or integration impact: No new dependencies. Integration must preserve server-owned menu metadata. The main-worktree backend on `48080` is controlled by an existing development terminal and automatically restarts; isolated runtime verification used `48081`. Database tenant `1` has one scoped organization update (`system_dept.id=1021`, `leader_user_id=233`).
- Remaining work: Obtain an administrator session with `bpm:model:create` and `bpm:model:deploy`, or separately authorize a temporary scoped permission assignment, then import/deploy `script/bpm/zsjos_lead_appeal_review.bpmn20.xml`, verify the active tenant-scoped definition and repeat the no-submit runtime checks. After that, remove verification-only `.env.local`, update this record with final verification, commit the isolated branch, and leave push/merge unperformed unless separately authorized.

### 2026-08-10 21:31:00 +08:00

- Branch: `codex/lead-appeal-routing`
- Worktree: `D:\ZSJ-OS-worktrees\lead-appeal-routing`
- HEAD commit: `d3459e2c064d02121315d233c0f52bfa0ec25d16`
- User goal: Stop extra local development ports while retaining port `5174`.
- Key decisions: Stopped only this workstream's isolated frontend processes on `5175` and `5176` and isolated backend on `48081`; retained the existing workbench on `5174` and its required backend on `48080`; left unrelated project and system listeners untouched.
- Execution or analysis result: Cleanup completed. Ports `5175`, `5176`, and `48081` no longer have listeners. Port `5174` remains listening, and `48080` remains available as its backend dependency.
- Changed files: `handoff/lead-appeal-routing.md`.
- Verification evidence: Post-cleanup listener query confirmed `5174` and `48080` only among the workstream application ports.
- Dependency or integration impact: None.
- Remaining work: None for port cleanup.

### 2026-08-10 21:38:00 +08:00

- Branch: `codex/lead-appeal-routing`
- Worktree: `D:\ZSJ-OS-worktrees\lead-appeal-routing`
- HEAD commit: `d3459e2c064d02121315d233c0f52bfa0ec25d16`
- User goal: Log in as the sales-manager account, determine why the appeal page is still unavailable, and explain the required workflow configuration.
- Key decisions: Treat the current `5174` and `48080` processes as the main worktree runtime; perform read-only account/menu/API/database checks; do not modify permissions or submit lead `8`.
- Execution or analysis result: Login succeeded for user `233` (sales manager). Server menu `system_menu.id=6804` has path `appeals`, component `zsjos/leadAppeal/index`, and query permission. The main worktree frontend still renders its placeholder for that component, so `/zsjos/appeals` shows “页面尚未迁移”. The main backend returns `500` for the empty appeal inbox because the isolated BPM empty-page fix is not integrated there. Tenant `1` still has zero active deployments for `zsjos_lead_appeal_review`.
- Changed files: `handoff/lead-appeal-routing.md` only.
- Verification evidence: Browser login at `http://localhost:5174` showed the account label “销售主管1” and the placeholder page; authenticated API permission response contained the two appeal permissions; authenticated inbox API on `48080` returned `500`; read-only SQL confirmed menu metadata, department leader `1021 -> 233`, and process-definition count `0`.
- Dependency or integration impact: The isolated worktree already contains the migrated frontend and BPM fix, but those changes are not present in `main`/`5174`. BPMN candidate strategy `35` is start-user-selected; the appeal service supplies the resolved reviewer IDs when starting the process.
- Remaining work: Integrate the isolated frontend/backend changes into the runtime serving `5174/48080`, then use a separately authorized BPM operator to create/import and deploy the process model with key `zsjos_lead_appeal_review`; verify the active definition and inbox again.

### 2026-08-10 21:46:00 +08:00

- Branch: `codex/lead-appeal-routing`
- Worktree: `D:\ZSJ-OS-worktrees\lead-appeal-routing`
- HEAD commit: `d3459e2c064d02121315d233c0f52bfa0ec25d16`
- User goal: Apply the isolated frontend migration to the main worktree serving port `5174` and rebuild the frontend.
- Key decisions: Applied only the frontend migration files from this workstream to `D:\ZSJ-OS`; preserved all pre-existing main-worktree backend, SQL, migration, and handoff changes; did not merge backend BPM changes.
- Execution or analysis result: Main worktree now resolves server component `zsjos/leadAppeal/index` to the real `LeadAppealPage` and redirects inaccessible stale routes to the first authorized internal route. The existing Vite server on `5174` hot-reloaded the changes.
- Changed files: `D:\ZSJ-OS\frontend\workbench\src\main.tsx`; `D:\ZSJ-OS\frontend\workbench\src\services\menu.ts`; `D:\ZSJ-OS\frontend\workbench\src\services\menu.test.ts`; `D:\ZSJ-OS\frontend\workbench\src\services\menuComponentRegistry.ts`; `D:\ZSJ-OS\frontend\workbench\src\services\menuComponentRegistry.test.ts`; generated `D:\ZSJ-OS\frontend\workbench\tsconfig.tsbuildinfo`.
- Verification evidence: Workbench tests passed (`49`); `npm run typecheck` passed; `npm run build` passed; browser at `http://localhost:5174/zsjos/appeals` showed the actual `申诉处理` page with tabs and empty-state content instead of the migration placeholder. The page still shows a system-error alert because the main backend on `48080` has not received the separate BPM empty-page fix.
- Dependency or integration impact: No new dependencies. Main worktree remains dirty with unrelated user changes; no staging, commit, branch, push, or merge operation was performed.
- Remaining work: Integrate and restart the backend BPM fix, then deploy `zsjos_lead_appeal_review` with authorized BPM operator credentials before final submit-chain verification.
