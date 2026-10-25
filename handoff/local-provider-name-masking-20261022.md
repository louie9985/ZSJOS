# 员工提供方姓名快照脱敏

## Registration — 2026-10-22 (Asia/Shanghai)
- ID: local-provider-name-masking-20261022
- Environment: local (environment variable and environment file absent; fallback)
- Goal: 员工提供方姓名快照复用来源身份脱敏策略。
- Non-goals: 数据修复、权限配置、前端布局、部署或重启。
- Branch/worktree/base: main / D:\ZSJ-OS / 404387de566fcb9abf4b264f0e005bf0566ea209
- Owner: Codex current chat
- Ownership: LeadManagementServiceImpl.java 的提供方响应投影；LeadManagementServiceImplTest.java 的身份展示测试；docs/architecture/data-and-permission-flow.md 的提供方脱敏说明；本记录。
- Existing changes: 上述 Service 和测试已有待办及下属客资查询修改，保留；历史相关 workstream 已交付。
- Dependencies: 复用 LeadIdentityMaskingService.snapshotName；无新增依赖。
- Verification: focused LeadManagementServiceImplTest and LeadIdentityMaskingServiceTest; scoped diff checks; inspect Workbench/Admin field consumers.
- Target branch/integration order: None

## Delivery — 2026-10-22 10:19 (Asia/Shanghai)
- Branch/worktree/HEAD: unchanged from registration.
- User goal: 修复提供方未与来源人员同步脱敏的问题。
- Decisions/result: 员工提供方按 providerOwnerId 复用 snapshotName 与 SOURCE 关系策略；兼职处理保持；明文权限和指定分配例外保留。姓名快照不以当前昵称替换，原始对象不回写。
- Changed files: 注册范围内的 Service、测试、架构说明及本记录。已有其他修改保留。
- Verification: Maven reactor compiled successfully. Focused identity cases: 11 tests passed (5 LeadManagementServiceImplTest methods + 6 LeadIdentityMaskingServiceTest cases); git diff --check passed. Full two-class run: 66 tests, 64 passed, 2 errors in educationRestoreProjectionNeedsPermissionAndManagedOwner due to existing LeadRestoreOwnerPolicy.teamTransferPolicy null injection, outside modified path; left unchanged.
- Consumer verification: Workbench services/api.ts and LeadDetailOverview.tsx; Admin api/zsjos/leadManagement/index.ts and views/zsjos/lead/index.vue both request the same backend and render providerOwnerNameSnapshot directly. API field shape unchanged. Authenticated HTTP and real-account browser acceptance not performed; runtime still requires backend deployment/restart by its operator.
- Evidence logs: C:\Users\EDY\AppData\Local\Temp\provider-name-masking-focused-test.log; provider-name-masking-suite-test.log in same directory.
- Dependencies/integration: None added; no database changes, grants, commits, push or service operations.
- Remaining work: No implementation work within scope; unrelated restore test setup errors reported. Ownership released.
