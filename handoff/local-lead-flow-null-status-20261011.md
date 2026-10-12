# 客资流转历史空状态修复

## Registration - 2026-10-11
- ID: local-lead-flow-null-status-20261011; owner: Codex /root; environment: local (environment variable unset, environment file absent).
- Branch: main; worktree: D:/ZSJ-OS; base commit: c82c8605d86aef5c8f4cd328600bc7c3efc2bf90; target branch/integration order: None.
- Goal: prevent null event status labels from failing the entire flow-history read, preserving the known side of each transition.
- Non-goals: historical data repair, status semantics changes, permission changes, frontend changes, service restart, deployment, commits or pushes.
- Ownership: backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/lead/LeadFlowHistoryService.java; corresponding src/test/java/cn/iocoder/yudao/module/zsjos/service/lead/LeadFlowHistoryServiceTest.java; docs/api/zsjos-lead-flow-history.md; this record.
- Dependencies: existing event projection and Mockito/JUnit test infrastructure; no new dependencies. Existing unrelated local work is preserved.
- Verification plan: focused service regressions for missing before/after statuses, unchanged and unknown statuses; existing service and flow permission contract tests through the Maven reactor; scoped diff checks. Authenticated runtime acceptance requires the updated backend to be running.

## Delivery - 2026-10-11 23:36 Asia/Shanghai
- Environment/branch/worktree/base: unchanged from registration; HEAD unchanged at c82c8605d86aef5c8f4cd328600bc7c3efc2bf90.
- Result: both leadStatusLabel and assignmentStatusLabel return null before looking up a null key in immutable maps. Existing recognized labels, unknown-code behavior, event inclusion and transition semantics remain intact.
- Changed files: the service, its test, the API document and this record, as registered. The API document clarifies missing-side handling without inventing historical states.
- Verification: Maven reactor command `mvn -f backend/pom.xml -pl yudao-module-zsjos -am -Dtest=LeadFlowHistoryServiceTest,LeadFlowHistoryPermissionContractTest -Dsurefire.failIfNoSpecifiedTests=false test` completed BUILD SUCCESS; 26 tests, 0 failures/errors/skips, including 14 new parameter cases. Production and test source compilation passed. Scoped git diff --check passed.
- Verification log: %TEMP%/zsjos-lead-flow-null-status-test.log. Initial log redirection to a nonexistent .cache directory failed before Maven ran; rerun with the temporary log succeeded.
- Dependency/integration impact: None; no new dependency, schema, permission or response-field changes. No database writes, service restart, commit or push.
- Remaining verification: the reported authenticated request has not been replayed against a restarted backend. Runtime acceptance remains pending updated backend execution. Ownership released.
