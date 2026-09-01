# Partner Open Request Workstream

- Workstream ID: partner-open-request
- Goal: Implement employee Workbench proxy application and BPM approval for Partner H5 first-login invitation generation.
- Non-goals: No external HTTP webhook, no direct Partner/PartnerAccount creation on approval, no real role or permission assignment changes outside SQL initialization metadata.
- Branch: main
- Worktree: D:\ZSJ-OS
- Base commit: e8674481cf5fabb2f9e0c0cfcc8ee9a952234f6a
- Target branch: main
- Ownership scope: backend/yudao-module-zsjos, frontend/workbench, script/sql/mysql, focused tests and directly affected docs.
- Owner: Codex
- Dependencies: Existing System permission/user/department APIs, BPM public APIs, ZSJOS Partner invitation service, System business notification API.
- Integration order: backend schema and service contract, BPM target and notifications, Workbench typed API and page, focused tests, verification.
- Verification plan: focused backend tests for invitation command and open request service/BPM target; Workbench typecheck/build; targeted frontend tests where practical; SQL syntax and bootstrap source review.
- Status: active

## Delivery Entry - 2026-09-01 11:52:28 +08:00

- Branch: main
- Worktree: D:\ZSJ-OS
- HEAD commit: e8674481cf5fabb2f9e0c0cfcc8ee9a952234f6a
- User goal: Implement the confirmed proxy application and BPM approval flow for opening Partner H5 first-login invitation access.
- Key decisions: Approval pass generates a Partner invitation only and does not directly create Partner or PartnerAccount records; reviewers are resolved from `zsjos:partner-open-request:review`; assigned employee candidates are filtered through the enabled `new_media_operator` role; notifications use the existing System business notification boundary; external HTTP webhook is not added.
- Execution or analysis result: Added the `zsjos_partner_open_request` business table, admin API, service state machine, BPM status listener, notification scene provider, Workbench route/page, BPM task target routing, BPM asset registration, SQL initialization and migration metadata, and focused tests. The invitation command entry now runs in a new transaction when called from the approval callback so failed invitation creation can still persist `open_failed` on the application.
- Changed files: backend/yudao-module-zsjos service/controller/DAL/VO/test files for Partner open request and invitation command; frontend/workbench route, API, page, CSS and menu test files; script/sql/mysql bootstrap/schema/migration files; script/bpm manifest and `zsjos_partner_open_request` BPMN; docs/api/zsjos-bpm-business-task-target.md; docs/frontend/zsjos-menu-coverage.md; frontend/workbench/tsconfig.tsbuildinfo.
- Verification evidence: `mvn -f backend/pom.xml -pl yudao-module-zsjos -am "-Dtest=PartnerInvitationServiceImplTest,PartnerOpenRequestServiceImplTest,ZsjosBpmBusinessTaskTargetServiceImplTest" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed with 18 tests. `npx vitest run src/services/menu.test.ts` passed with 19 tests. `npm run typecheck` passed. `npm run build` passed with the existing Vite chunk-size warning. `git diff --check` passed. The new BPM asset hash, process key, task key, and BPMN DI were validated by a focused checksum/XML check.
- Dependency or integration impact: No new Maven or npm dependencies. Requires deployment/import of BPM process key `zsjos_partner_open_request` and SQL migration V177/menu permission configuration before use. Existing H5 activation remains the final account creation path.
- Remaining work: Full `python script/bpm/validate_manifest.py` is blocked by a pre-existing checksum mismatch for `zsjos_feedback_requirement_approval/1.0.0/process.bpmn20.xml`. Full `npm test -- --reporter=basic` is blocked by five pre-existing guard assertion mismatches in `src/pages/messageinboxpage.guard.test.ts` and `src/pages/media-students.guard.test.ts`; this workstream's menu test now passes. Browser/login verification and real database migration execution were not run because no authenticated backend/database environment was available in this turn. Unrelated workspace changes observed but not owned by this workstream: `frontend/admin/.env`, `frontend/admin/.env.dev`, and `script/shell/deploy-production.sh`.
