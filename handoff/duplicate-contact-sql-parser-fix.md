# Workstream: duplicate-contact-sql-parser-fix

- ID: `duplicate-contact-sql-parser-fix`
- Goal: Restore duplicate-contact lookup by making its case-sensitive MySQL comparison compatible with the MyBatis-Plus tenant SQL parser.
- Non-goals: Change duplicate-detection rules, database schema or data, tenant behavior, dependencies, or unrelated lead behavior.
- Branch: `main` (user-designated transitional workstream)
- Worktree: `D:\ZSJ-OS`
- Base commit: `18af4ec8cfa18e23273589d1551ebbe91cdd6781`
- Target branch: `main`
- Ownership scope: `PersonMapper` duplicate-candidate SQL, its focused regression test, and this handoff record.
- Owner: Codex `/root`
- Dependencies: Existing MyBatis-Plus/JSqlParser tenant interceptor and MySQL case-sensitive binary comparison semantics.
- Integration order: Direct transitional fix on `main`; no separate merge step.
- Verification plan: Generate every non-empty dynamic SQL variant, parse it with the repository JSqlParser, assert case-sensitive comparison clauses and parameter counts, then run focused ZSJOS tests and compile.
- Status: `implemented-uncommitted`

## Delivery Entries

### 2026-08-13 16:21:38 +08:00

- Branch: `main`
- Worktree: `D:\ZSJ-OS`
- HEAD commit: `18af4ec8cfa18e23273589d1551ebbe91cdd6781`
- User goal: Diagnose and directly fix the duplicate-contact query failure on `main` without creating a branch or worktree.
- Key decisions: Kept exact case-sensitive mobile/WeChat comparison semantics; replaced JSqlParser-incompatible MySQL unary `BINARY` expressions with equivalent `CAST(... AS BINARY)` expressions; added a regression test that expands the production MyBatis annotation for every non-empty parameter combination and parses the resulting SQL with the repository JSqlParser; did not change schema, data, tenant interception, dependencies, or unrelated failing tests.
- Execution or analysis result: The tenant interceptor can now parse duplicate-candidate SQL for mobile-only, WeChat-only, and combined requests while preserving same-field and cross-field exact matching. The change remains uncommitted on the user-designated transitional `main` workstream.
- Changed files: `backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/dal/mysql/lead/PersonMapper.java`; `backend/yudao-module-zsjos/src/test/java/cn/iocoder/yudao/module/zsjos/dal/mysql/lead/PersonMapperSqlTest.java`; `handoff/duplicate-contact-sql-parser-fix.md`.
- Verification evidence: Focused Reactor tests passed 4/4 (`PersonMapperSqlTest` and `LeadDuplicateMatcherTest`); ZSJOS dependency graph package with tests skipped passed; `git diff --check` passed. Full Reactor test was blocked before ZSJOS by the unrelated existing `CodegenEngineUniappTest.testExecute_treeSearch` assertion failure. A ZSJOS-package-wide test run executed 188 tests and found two unrelated existing failures: stale permission expectations in `LeadSubmissionControllerPermissionTest` and a missing `salesOrderPermissionService` mock injection in `LeadManagementServiceImplTest`. No real MySQL or authenticated HTTP request was executed.
- Dependency or integration impact: None; no dependency, schema, migration, API, or business-contract change.
- Remaining work: Restart or redeploy the application before runtime verification, then retry lead submission against MySQL with tenant interception enabled. Resolve the three unrelated existing full-suite failures separately. Commit only if explicitly requested.
