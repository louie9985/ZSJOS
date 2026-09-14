# main-student-collaboration-access

- Workstream ID: `main-student-collaboration-access`
- Goal: 按协作组边界收紧账号、学员服务和账号创建权限。
- Non-goals: 不改任务通知、素材库、前端大改或数据库迁移；不提交 Git。
- Branch/worktree: `main` / `D:\ZSJ-OS`; owner `/root/collab_data_impl`。
- Scope: MediaAccountObjectPermissionProvider, MediaAccountMapper candidate/participant queries, MediaAccountService create, StudentServiceObjectPermissionProvider, StudentContactServiceImpl account-create paths, focused tests/docs.
- Extended scope: MediaStudentService account filtering; dal/mysql/account/MediaAccountCandidateScopeTest.java; StudentContactServiceImplTest; docs/architecture/data-and-permission-flow.md.
- Verification: focused source checks and Maven compile/test where feasible.

## Delivery Entry - 2026-09-13 16:36 +08:00
- Branch/worktree: `main` / `D:\ZSJ-OS`; HEAD `f338087e9aaa8b8d881cd0aee80427d8773ab284` (uncommitted).
- Goal: 收紧账号、学员详情和创建权限到所选服务关系/协作组边界。
- Decisions: 移除按 person 的任意关系读扩权；账号有 createServiceRelationId 时必须匹配同租户、同学员、active、accepted 来源关系；运营可在本组创建，账号负责人取所选关系，创建人单独记录；旧账号无来源关系仅兼容显式负责人，未宣称完整隔离。
- Changed files: MediaAccountObjectPermissionProvider.java; MediaAccountMapper.java; MediaAccountService.java; StudentServiceObjectPermissionProvider.java; StudentContactServiceImpl.java; MediaStudentService.java; MediaAccountServiceTest.java; StudentServiceObjectPermissionProviderTest.java; MediaStudentServiceTest.java; handoff/main-student-collaboration-access.md。
- Verification: `mvn -f backend/pom.xml -pl yudao-module-zsjos '-Dtest=MediaAccountObjectPermissionProviderTest,MediaAccountServiceTest,StudentServiceObjectPermissionProviderTest,MediaStudentServiceTest' test` session 5235 exited 0; 23 tests, 0 failures/errors. `mvn ... -DskipTests compile` exited 0. No shared DB, permissions, branch, commit or push operations.
- Integration impact: 详情先按账号对象 read 权限过滤，再装配定位/内容/工单；候选查询 source relation 与账号 person/tenant 对齐；StudentContact create action exposes operator in same accepted relation. Existing query-all read/maintenance bypass remains read-only.
- Remaining: root to review SQL semantics and integrate positioning/interview permission fixes; full cross-group API/browser and database-backed tests remain unverified.

## Delivery Entry - 2026-09-13 16:45 +08:00
- Added exact-source permission tests and student detail foreign-account filtering test; updated architecture boundary documentation.
- Initial focused suite (26 tests) passed before final test fixture additions. Re-run including StudentContactServiceImplTest and MediaAccountCandidateScopeTest was blocked by unrelated existing missing audit classes (`BpmProcessInstanceAuditHook`, `ExecutionAuditHook`, `ExecutionAuditContext`) in shared worktree; no claim of full suite pass.
- Candidate test was kept MySQL predicate aware with H2 `b\'0\'` adaptation; verify after audit dependency issue is resolved.
- Remaining: list/calendar queries still use stored owner/director data-scope and are not fully source-relation scoped; legacy accounts remain compatibility exception; positioning/interview object scope is separate.

## Delivery Entry - 2026-09-13 16:46 +08:00
- Focused verification rerun with reactor (`mvn -f backend/pom.xml -pl yudao-module-zsjos -am '-Dtest=MediaAccountObjectPermissionProviderTest,MediaAccountServiceTest,StudentServiceObjectPermissionProviderTest,MediaStudentServiceTest,StudentContactServiceImplTest,MediaAccountCandidateScopeTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`) passed: 53 tests, 0 failures/errors. This run includes the corrected H2 candidate predicate test and operator action coverage.
- Existing audit dependency issue did not reproduce in reactor build; no shared database or external state changed.
- Remaining risks unchanged: account list/calendar data-scope still uses stored owners; legacy accounts without source relation are compatibility-only; positioning/interview scopes pending next stage. Full all-migration replay and browser verification unverified.

## 2026-09-13 17:50 CST delivery
- Branch/worktree/HEAD: current local main worktree; no branch or commit changes.
- User goal: unify student-level positioning entry while preserving account-level execution snapshots.
- Decisions: positioning drafts may be created with student + service relation and nullable account; submission remains blocked until account binding; account-specific execution continues to require account snapshots.
- Changed files: PositioningCardSaveReqVO.java, PositioningCardMapper.java, PositioningCardService.java, api.ts, 00-bootstrap-schema.sql, schema/core.sql, V219__positioning_card_student_draft.sql, BusinessTaskServiceImpl.java.
- Verification: Workbench typecheck passed; backend compile rerun after removing stale `return map` compile error (process still running at handoff time); shared database untouched.
- Remaining: bind the draft to the first created account, add focused accountless-draft tests, complete notification/channel audit and browser/API acceptance.

## 2026-09-13 17:57 CST delivery
- Added atomic account creation binding: nullable co-creating positioning drafts for the same service relation/student are attached to the newly inserted account in the same transaction.
- Added mapper update guarded by tenant, relation, student, null account and draft status.
- Backend reactor compile: SUCCESS. Workbench typecheck: SUCCESS. Shared database untouched; no Git commit.
- Remaining: focused service tests for binding and duplicate account cases; notification/channel matrix and runtime browser acceptance.

## 2026-09-13 18:13 CST verification
- Added focused regression test `createAllowsStudentDraftBeforeAccountExists` covering nullable account draft creation and relation/student ownership.
- `mvn -pl yudao-module-zsjos -Dtest=PositioningCardServiceTest test`: 21 tests, 0 failures/errors.

## 2026-09-13 18:16 CST final static verification
- `python script/sql/mysql/tools/zsjos_db.py check`: PASS.
- Targeted `git diff --check`: no whitespace errors in changed positioning, account, task, notification, test, migration, or notification-matrix files.
- Runtime/browser remains unverified because local service on port 48080 is unresponsive; no service restart performed.

## 2026-09-13 18:15 CST regression verification
- PositioningCardServiceTest, PositioningCardObjectPermissionProviderTest, PositioningInterviewServiceTest: 49 tests, 0 failures/errors.
