# Student detail scope workstream

- ID: main-student-detail-scope
- Owner: Codex /root
- Goal: Apply the approved managed department scope to student detail and by-service detail, without leaking other service relations.
- Non-goals: JRebel, restarts, SQL/data, new permissions, mutation authorization, frontend redesign.
- Branch / target branch: main
- Worktree: D:/ZSJ-OS
- Base commit: 436ba84cd8ab5471356e3faa62db9ab44e3c76e9
- Ownership: MyStudentServiceImpl.java; StudentServiceObjectPermissionProvider.java; their tests under registration; docs/api/student-detail-scope.md; this handoff.
- Dependencies: Existing PermissionApi, AdminUserApi, DeliveryClassScopeService, ServiceRelationMapper; no new dependency.
- Integration order: Scope lookup and service object read check, focused regression tests, documentation, verification.
- Verification: Focused Maven tests and compile; read-only live endpoint check if a usable authenticated runtime is available; scoped diff check.
- Approved design: Managed detail uses the same owner department set as managed list. Ordinary detail retains existing owner/collaborator selection. By-service detail additionally checks that the exact requested relation is in the visible set. Managed read does not grant commands.
- Status: in-progress

## Delivery entry - 2026-09-14 21:59:42 +08:00

- Branch: main
- Worktree: D:/ZSJ-OS
- HEAD: 436ba84cd8ab5471356e3faa62db9ab44e3c76e9
- User goal: Fix student detail scope first using the approved managed-read design.
- Decisions: Reuse list owner-scope resolution in both detail paths; keep exact relation membership validation; add managed read to student-service object permission without granting commands. Preserve all pre-existing edits.
- Result: Local live managed and owner requests to student detail and by-service detail succeeded; list includes the same student. Unauthenticated request returned code 401.
- Changed files: backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/registration/MyStudentServiceImpl.java; backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/registration/StudentServiceObjectPermissionProvider.java; backend/yudao-module-zsjos/src/test/java/cn/iocoder/yudao/module/zsjos/service/registration/MyStudentServiceImplTest.java; backend/yudao-module-zsjos/src/test/java/cn/iocoder/yudao/module/zsjos/service/registration/StudentServiceObjectPermissionProviderTest.java; docs/api/student-detail-scope.md; this handoff.
- Verification: Maven reactor test with -Dtest=MyStudentServiceImplTest,StudentServiceObjectPermissionProviderTest and -Dsurefire.failIfNoSpecifiedTests=false passed (21 tests, zero failures/errors), including compilation; scoped git diff --check passed. Initial PowerShell argument parsing error was corrected by quoting the Maven property. Real localhost requests used the existing local mock authentication configuration and persisted user permission data, without changing accounts or permissions. Admin and Workbench API consumers inspected separately; response shape unchanged.
- Dependencies/integration: No new dependencies, SQL, commits, branch changes or explicit runtime restart. Existing JRebel runtime picked up the change. Unrelated worktree changes preserved.
- Remaining work: Live out-of-department scenario lacks an existing valid service sample; covered by tests. Browser UI checks not run (no frontend changes). JRebel StackOverflow issue remains a separate task.
- Status: completed

## Scope extension - contact detail follow-up

- Owner: Codex /root; branch main; worktree D:/ZSJ-OS.
- Added ownership: StudentContactServiceImpl.java and StudentContactServiceImplTest.java under studentcontact.
- Goal: Complete the already approved student detail read chain; contact-context and contact-records still reject managed readers internally.
- Plan: Reuse the existing student-service read provider only when no direct relation exists; preserve command ownership checks. Verify focused tests and all page detail requests with persisted local manager context.

## Delivery entry - 2026-09-14 22:18:00 +08:00

- User goal: Correct delivery manager student detail authorization to reuse existing department manager permission.
- Result: Managed student reads now recognize `zsjos:delivery-class:query` alongside the hidden compatibility permission and continue using System department scope; write actions unchanged.
- Changed files: MyStudentServiceImpl.java; StudentObjectPermissionProvider.java; StudentServiceObjectPermissionProvider.java; MyStudentServiceImplTest.java; StudentServiceObjectPermissionProviderTest.java.
- Verification: focused Maven tests passed after updating strict varargs stubs; no database or account permission changes.
- Remaining: runtime process restart/browser verification requires separate service restart authorization.

## Delivery entry - 2026-09-14 22:30:00 +08:00

- User goal: Allow delivery supervisors to perform all role-permitted student operations within their department data scope.
- Changes: student-service object permission now grants owner action checks to managed-scope users; contact context treats a managed-scope readable relation as operational for action projection. Existing role/button permission checks remain in force.
- Verification: compile attempted; blocked by pre-existing malformed UTF-8/source corruption in unrelated ContentReviewController.java and ContentReviewConfigService.java. No database or external state changed.
- Remaining: class-transfer command still has an explicit owner guard and requires a follow-up adjustment before claiming all operations are complete; runtime restart/browser verification pending.
