# 中世健系统异步审计租户修复

## Registration — 2026-10-14 Asia/Shanghai
- ID: local-async-audit-tenant-20261014; owner: Codex /root; environment: local (fallback).
- Branch: main; worktree: D:/ZSJ-OS; base: e07efc9d6baa5d40b5ec07b78f6743b0d1b2915e. Target branch / integration order: None.
- Goal: repair tenantless ASYNC audit persistence under the confirmed single-tenant contract, with scoped context restoration.
- Non-goals: task business tenant defaults, executor changes, task identity instrumentation, schema/data changes, dependencies, historical backfill, deployment/restart, Git operations.
- Ownership: ZsjosExecutionAuditHook.java; ZsjosExecutionAuditHookTest.java; BusinessAuditTenantMapperTest.java; docs/api/async-export-and-business-audit.md; this record. Prior Flowable workstream released ownership; preserve its existing edits.
- Dependencies: existing TenantFrameworkService, TenantUtils, shared async decorator and audit runner. No new dependencies or frontend contract changes.
- Contract decision: user requests repair with one tenant (1). Extend the existing SYSTEM_JOB audit-only singleton resolution to ASYNC; retain explicit/ambient tenant precedence and Flowable business job validation. Synchronize the previous no-ASYNC-default documentation.
- Verification: focused hook tests for success/failure/invalid configuration/context restoration; real decorated executor FutureTask plus tenant-intercepted Mapper/H2 persistence; existing audit service and Flowable tests. Serialize shared Maven output and check current build activity first. H2 is not target MySQL/live acceptance.

## Verification isolation update — 2026-10-14
- First shared-output reactor run passed 11 BPM tests but failed ZSJOS test compilation on missing unrelated LeadDuplicateMatcher.Candidate and SupervisorLeadActionPolicy.Action classes. Both class files subsequently appeared with timestamps after the failed build, indicating overlapping shared build output changes.
- Verification scope adds an ignored source snapshot at D:/ZSJ-OS/backend/target/async-audit-verification/backend, with independent Maven output. Preserve the primary checkout and running service; no Git worktree or branch operation. Keep backend/async-audit-tenant-tests.log and backend/async-audit-tenant-isolated-tests.log as ignored build evidence.

## Delivery — 2026-10-14 15:00 Asia/Shanghai
- Result: tenantless ASYNC audit callbacks now resolve and validate the configured sole tenant, enter TenantUtils scope only for persistence, and restore tenant/ignore context. Explicit execution and ambient tenants retain precedence. Invalid tenant configuration rejects persistence. Redis paths and Flowable business job validation are unchanged.
- Files: registered production hook, hook tests, Mapper integration test, API documentation and this record. Existing unrelated edits preserved; no staging/commit/branch operations, database changes or service restart.
- Verification: isolated 21-module Maven reactor BUILD SUCCESS. 29 tests passed, zero failures/errors/skips: 14 hook, 3 audit service, 1 Mapper/real Spring FutureTask pipeline, 7 Flowable timer, 2 Flowable configuration, 2 async alias tests. The pipeline test starts a task without a tenant, checks actual tenant-intercepted H2 audit row tenant_id=1/status=SUCCESS, and verifies restored worker context. Failure/disabled/ambiguous tenant cases and business result preservation covered.
- Command: mvn -f backend/target/async-audit-verification/backend/pom.xml -pl yudao-module-zsjos -am test -Dtest=ZsjosExecutionAuditHookTest,BusinessAuditTenantMapperTest,BusinessAuditServiceImplTest,BpmTimerTenantTest,BpmAsyncConfigurationTest,AsyncExecutorAliasTest -Dsurefire.failIfNoSpecifiedTests=false. Evidence: backend/async-audit-tenant-isolated-tests.log; snapshot module target/surefire-reports. Changed Java snapshot matches checkout sources. Scoped diff and strict UTF-8 checks passed.
- Remaining: no packaged release, service restart, or live target MySQL acceptance in this task. Deploy/restart the updated backend with target authorization, then verify the recurring tenantless async audit succeeds. Source/H2 checks do not establish live resolution. Task identity instrumentation and historical backfill remain outside scope. Ownership released.
