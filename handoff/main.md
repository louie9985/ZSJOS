# Main Workstream

- Workstream ID: main
- Goal: fix partner optimistic-lock runtime failures and implement type-safe, idempotent partner logout with authoritative H5 local-session cleanup, while preserving the prior main-workstream changes
- Non-goals: change database schema or data, rewrite V072 or any applied migration, change normal employee authentication behavior, add dependencies, modify unrelated business domains, or restart shared services
- Branch: main
- Worktree: D:\ZSJ-OS
- Base commit: 69741138b4
- Target branch: main
- Ownership scope: common MyBatis interceptor configuration and focused test; System OAuth2 token public API/service and focused tests; ZSJOS partner account/auth services, error code and focused tests; H5 request/auth/profile logout flow; directly affected partner API and authentication-flow documentation; this handoff file. Existing overlapping edits are preserved.
- Owner: Codex root
- Dependencies: existing MyBatis-Plus 3.5.16, Yudao OAuth2 token persistence/cache APIs, ZSJOS PARTNER principal, and existing H5 Axios/Vue Router/Vant stack; no new package dependency
- Integration order: optimistic-lock interceptor and checked account updates; typed OAuth2 revocation and partner logout; H5 refresh/logout routing; tests and documentation; focused and dependency-graph verification
- Verification plan: focused MyBatis, OAuth2, partner account/auth tests; ZSJOS module tests; server dependency-graph package; H5 typecheck/build and browser checks where runtime credentials are available; scoped diff/whitespace validation

## Delivery 2026-08-16 18:00:49 +08:00

- Branch: main
- Worktree: D:\ZSJ-OS
- HEAD commit: 69741138b4c3fee9dc051fbdddf87f30a9ba5e49
- User goal: implement independent partner accounts and unified authorization, beginning with the current all-403 partner H5 failure
- Key decisions: preserve historical migrations; repair V063 by permission identity; logically delete only accidental work-plan grants; keep withdrawal review ungranted; do not leave a partially wired PARTNER principal implementation in the runtime
- Execution or analysis result: completed the forward permission repair and verification contract; investigated the System principal refactor and identified URL-derived app-api user types and user-role keys as required cross-framework changes; independent account runtime remains pending
- Changed files: handoff/main.md; script/sql/mysql/migrations/V068__repair_partner_permissions.sql; script/sql/mysql/bootstrap.sql; script/sql/mysql/verify-bootstrap.sql; script/sql/mysql/migrations/README.md; docs/architecture/data-and-permission-flow.md
- Verification evidence: `zsjos-db check` PASS; V068 partner permission check PASS in controlled `zsjos-db test-fresh`; System dependency graph compile PASS during the reverted compatibility experiment; fresh test retains unrelated existing failures for lead_filter_versions, module_schema_versions, and V064
- Dependency or integration impact: V068 must be applied before relying on H5 partner feature permissions; no production or local shared database was modified
- Remaining work: implement the independent System partner account table, PARTNER token routing across ZSJOS and approved System app APIs, subject-typed role relations/cache keys, profile and audit adapters, ZSJOS partner-account mapping, migration rehearsal, API tests, and H5 verification

## Delivery 2026-08-16 18:18:01 +08:00

- Branch: main
- Worktree: D:\ZSJ-OS
- HEAD commit: 69741138b4c3fee9dc051fbdddf87f30a9ba5e49
- User goal: fix the slow initial load when accessing the admin frontend through port 80
- Key decisions: bind the local Vite server to the IPv6 unspecified address so Windows provides dual-stack access; disable Baidu analytics only in local mode; leave backend, port, proxy and business behavior unchanged
- Execution or analysis result: Vite automatically reloaded its existing PID 20220 after the configuration change and now listens on `[::]:80`; localhost no longer incurs IPv6-to-IPv4 fallback; local pages no longer inject the Baidu analytics script
- Changed files: frontend/admin/vite.config.ts; frontend/admin/.env.local; handoff/main.md
- Verification evidence: actual port 80 returned HTTP 200 for localhost, 127.0.0.1 and 192.168.2.38 with 0.0004-0.0005 second connect times and about 0.010-0.012 second totals; browser loaded the Zhongshijian login page to complete state with no hm.baidu.com script; `pnpm build:local` PASS in 24.78 seconds; `git diff --check` PASS; `pnpm ts:check` remains blocked by eight existing errors outside the changed files; `pnpm lint` remains blocked by existing style errors outside the changed files
- Dependency or integration impact: no dependency, backend, API, proxy, port or production analytics configuration changes; local Vite development now accepts IPv4, IPv6 and LAN access
- Remaining work: unrelated existing admin typecheck and lint failures remain; the independent partner-account runtime refactor from the prior delivery is still pending

## Delivery 2026-08-16 19:53:38 +08:00

- Branch: main
- Worktree: D:\ZSJ-OS
- HEAD commit: 69741138b4c3fee9dc051fbdddf87f30a9ba5e49
- User goal: fix the post-login Vue Router exception caused by the invalid `partner-portal` route after V068
- Key decisions: add forward-only V069 instead of rewriting V068; logically retire the app-api-only `ZsjosPartnerPortal` admin menu; migrate the guard from deprecated `next()` callbacks to returned navigation results and always release loading state
- Execution or analysis result: implemented the V069 migration, bootstrap and verification wiring, migration documentation, and Vue Router 5 guard update; migration was not executed because no local MySQL client/database process was available
- Changed files: script/sql/mysql/migrations/V069__remove_invalid_partner_admin_route.sql; script/sql/mysql/bootstrap.sql; script/sql/mysql/migrations/README.md; script/sql/mysql/verify-bootstrap.sql; frontend/admin/src/permission.ts; handoff/main.md
- Verification evidence: `pnpm build:local` PASS in 24.19 seconds; focused ESLint and Prettier checks for `src/permission.ts` PASS; browser regression loaded `http://127.0.0.1/login?redirect=/index`; `git diff --check` PASS; full `pnpm ts:check` remains blocked by eight existing errors outside the changed files; SQL execution remains unverified because MySQL is unavailable locally
- Dependency or integration impact: V069 must be applied to each existing database before the invalid menu is removed; fresh bootstrap now sources V069; no business rows or H5 permissions are changed
- Remaining work: execute V069 in a controlled database, verify `get-permission-info` and partner/employee login after migration, and complete the independent partner-account runtime refactor

## Delivery 2026-08-16 21:12:35 +08:00

- Branch: main
- Worktree: D:\ZSJ-OS
- HEAD commit: 69741138b4c3fee9dc051fbdddf87f30a9ba5e49
- User goal: implement the complete H5 API contract and all-role ZSJOS menu-permission repair, retaining the WeCom login path as an unavailable entry without connecting OAuth or a backend login endpoint
- Key decisions: use anonymous tenant-only clients for System dictionary/area data; keep partner identities as ADMIN users for `/app-api/zsjos/**`; unify HTTP/business 401 recovery into one raw-Axios refresh flight; expose personal messages through role plus ownership checks rather than copied menu permissions; define V071 grants by stable role/permission codes; treat the explicit zero-menu list as eighteen roles; keep V071 generated-only and preserve all applied migrations and real account state
- Execution or analysis result: aligned all active H5 calls and DTOs, corrected Lead actions/supplement/product/identifier behavior and withdrawal fields, retained the inert WeCom entry, added retryable remote states and desktop H5 sizing, added the System partner-message Controller and tests, added the repeatable V071/bootstrap/verification chain, and documented the complete 34-role target matrix. The latest WeCom decision changes the surface from 38 callable endpoints to 37 callable contracts plus one unavailable retained entry.
- Changed files: frontend/h5/.env.development; frontend/h5/src/api/{auth,cashback,lead,message,reference,request,withdrawal}.ts; frontend/h5/src/composables/{useAuth,useDict,usePageList}.ts; frontend/h5/src/main.ts; frontend/h5/src/pages/{earnings,home,lead,login,messages,withdrawal} affected Vue files; frontend/h5/src/router/index.ts; frontend/h5/src/stores/user.ts; frontend/h5/src/styles/base.css; frontend/h5/src/utils/{format,storage}.ts; frontend/h5/兼职端API接口.md; backend System AppAreaNodeRespVO and partner message Controller/test; script/sql/mysql/bootstrap.sql, V071, migration README, migration test runner and verify-bootstrap.sql; directly affected API/architecture/menu/migration documentation; handoff/main.md
- Verification evidence: H5 `npm run build` PASS; mobile 390x844 and desktop 1440x900 browser checks PASS with no horizontal overflow, centered 540px desktop canvas, retained WeCom toast and unchanged login URL; System partner-message focused tests PASS 6/6; Maven package for System, BPM and ZSJOS dependency chain PASS; static audit mapped all 37 active H5 HTTP contracts to Controller routes and confirmed no source calls to `/zsjos/lead/area-tree` or `/zsjos/auth/wecom-login`; local 48080 public dictionary and area requests returned HTTP 200 and unauthenticated message returned business code 401; `zsjos-db check` PASS; guardrails PASS; V071 executed twice in isolated fresh MySQL and every V071 permission, duplicate, zero-role and menu-parent check passed; scoped `git diff --check` PASS
- Dependency or integration impact: no dependency, branch, commit, applied-migration checksum, real account permission, business row, BPM instance or existing database was changed. Deploying the compiled backend requires the normal application restart; applying V071 to an existing database still requires separate approval and a reviewed role-menu snapshot.
- Remaining work: the currently running 48080 backend is an older process and still returns area nodes without `selectionCode`/`leafSelectable`, so runtime contract verification must be repeated after an approved deployment/restart; authenticated partner end-to-end flows and concurrent refresh require a non-sensitive test account/environment; fresh and upgrade SQL suites retain pre-existing `lead_filter_versions` and `module_schema_versions` failures outside V071; no `spring-security-test` dependency was added, so non-partner method-security behavior is evidenced by the class annotation/framework and unauthenticated real request rather than a dedicated Spring security integration test

## Delivery 2026-08-16 21:19:08 +08:00

- Branch: main
- Worktree: D:\ZSJ-OS
- HEAD commit: 69741138b4c3fee9dc051fbdddf87f30a9ba5e49
- User goal: fix V071 failure `[HY000][1267] Illegal mix of collations` when role and permission codes use different `utf8mb4` collations
- Key decisions: compare stable role and permission codes with binary exact equality on both operands; reproduce the reported mismatch by changing only an isolated test database default to `utf8mb4_0900_ai_ci` after the unicode-collated bootstrap; preserve the V071 authorization matrix and do not execute it against an existing database
- Execution or analysis result: updated all V071 comparisons between temporary grant codes and System role/menu codes; extended the fresh integration test so V071 is rerun with mixed table/default collations and remains repeatable
- Changed files: script/sql/mysql/migrations/V071__repair_h5_and_role_permissions.sql; script/sql/mysql/tools/zsjos_db.py; handoff/main.md
- Verification evidence: `zsjos-db.ps1 check` PASS; `test-guardrails` PASS; mixed-collation `test-fresh` passed V071 execution and all V071 permission checks, then retained only the pre-existing `lead_filter_versions` and `module_schema_versions` failures; `python -m py_compile script/sql/mysql/tools/zsjos_db.py` PASS; scoped diff whitespace checks reported no errors
- Dependency or integration impact: no dependency, role matrix, business row, account grant, existing database, branch, or external service was changed; the corrected idempotent V071 may be rerun after a separately approved existing-database execution
- Remaining work: resolve the unrelated fresh-suite `lead_filter_versions` and `module_schema_versions` baseline failures separately; applying V071 to any existing database still requires explicit confirmation and a reviewed role-menu snapshot

## Delivery 2026-08-16 21:22:50 +08:00

- Branch: main
- Worktree: D:\ZSJ-OS
- HEAD commit: 69741138b4c3fee9dc051fbdddf87f30a9ba5e49
- User goal: remove the unsafe-query warning for the V071 role-menu restore UPDATE
- Key decisions: retain the existing inner-join target restriction and add an explicit WHERE on the same role-menu ID plus the expected logical-deleted state; do not disable client safety checks or change the authorization matrix
- Execution or analysis result: the V071 restore statement now visibly limits updates to deleted relations selected in `tmp_v071_restore`, preserving idempotent behavior while satisfying WHERE-based SQL safety inspection
- Changed files: script/sql/mysql/migrations/V071__repair_h5_and_role_permissions.sql; handoff/main.md
- Verification evidence: `zsjos-db.ps1 check` PASS; `test-guardrails` PASS; mixed-collation `test-fresh` passed V071 execution, repeat execution, and V071 permission checks, retaining only the pre-existing `lead_filter_versions` and `module_schema_versions` failures; scoped whitespace checks reported no errors
- Dependency or integration impact: no dependency, permission target, business row, real account, existing database, branch, or external service was changed
- Remaining work: applying the corrected V071 to any existing database still requires explicit confirmation and a reviewed role-menu snapshot; unrelated fresh-suite baseline failures remain

## Delivery 2026-08-16 21:37:44 +08:00

- Branch: main
- Worktree: D:\ZSJ-OS
- HEAD commit: 69741138b4c3fee9dc051fbdddf87f30a9ba5e49
- User goal: fix the H5 frontend development server to port 10086
- Key decisions: configure the port in the existing Vite server block and enable `strictPort` so an occupied port causes startup failure instead of silently selecting another port; preserve the existing proxy target, preview command, dependencies, and H5 behavior
- Execution or analysis result: changed the H5 development port from 5175 to 10086; the already-running H5 Vite process reloaded the configuration and now serves the application on `localhost:10086`; the temporary verification process was stopped after testing
- Changed files: frontend/h5/vite.config.ts; handoff/main.md
- Verification evidence: H5 `npm run build` PASS; the retained Vite process listens on `[::1]:10086`; `http://localhost:10086/` returned HTTP 200 with the application root; a concurrent startup failed with `Port 10086 is already in use`, confirming strict-port behavior; scoped `git diff --check` PASS with a line-ending warning only
- Dependency or integration impact: no dependency, API proxy, backend, database, account, permission, business data, branch, or external shared service changed
- Remaining work: None

## Delivery 2026-08-17 17:51:16 +08:00

- Branch: main
- Worktree: D:\ZSJ-OS
- HEAD commit: 84474ae6083a64343f5b39b397e5143b233523ae
- User goal: fix partner login/update optimistic-lock failures and make expired-token logout type-safe, idempotent, and locally authoritative in the partner H5
- Key decisions: register the existing MyBatis-Plus optimistic-lock interceptor globally; require every versioned partner-account update to affect exactly one row before token side effects; add an OAuth2 revocation overload guarded by expected user type; treat logout audit as best effort; let normal H5 requests refresh once while logout requests never refresh and always clear local state after confirmation
- Execution or analysis result: implemented stable concurrent-modification handling for partner login, enable/disable, mobile, and password updates; PARTNER logout now removes persisted expired access/refresh tokens and caches without touching ADMIN/MEMBER tokens, and missing tokens are idempotent success; H5 logout separates user cancellation from server failure, suppresses expired-token refresh/toast on logout, clears all authentication state in `finally`, and uses centralized login redirection with return targets only for passive session expiry; synchronized API and authentication-flow documentation
- Changed files: backend/yudao-framework/yudao-common OAuth2TokenCommonApi; backend/yudao-framework/yudao-spring-boot-starter-mybatis YudaoMybatisAutoConfiguration and focused test; backend/yudao-module-system OAuth2TokenApiImpl, OAuth2TokenService, OAuth2TokenServiceImpl and focused test; backend/yudao-module-zsjos ZsjosErrorCodeConstants, PartnerAccountService/Impl, PartnerAuthServiceImpl and focused tests; frontend/h5/src/api/request.ts; frontend/h5/src/api/auth.ts; frontend/h5/src/pages/profile/index.vue; docs/api/partner-app-api.md; frontend/h5/兼职端API接口.md; docs/architecture/data-and-permission-flow.md; handoff/main.md
- Verification evidence: MyBatis configuration test PASS 1/1; typed OAuth2 revocation tests PASS 3/3; partner account/auth tests PASS 11/11; H5 `npm run build` PASS; browser check on `localhost:10086` confirmed the login page renders without console errors and unauthenticated `/profile` preserves `/profile` as the return target; server dependency graph compiled all 28 modules and package PASS with `spring-boot.repackage.skip=true`; scoped `git diff --check` PASS. The complete `yudao-module-zsjos -am test` reactor remains blocked before System/BPM/ZSJOS by the unrelated existing `CodegenEngineUniappTest.testExecute_treeSearch` failure in Infra. Standard executable-JAR repackage compiled all modules but could not rename the currently running locked `yudao-server.jar`.
- Dependency or integration impact: no dependency, database schema/data, migration, account permission, branch, or shared-service state changed; deployment requires replacing/restarting the backend before the new interceptor and revocation implementation are active
- Remaining work: after an approved backend deployment/restart and with a non-sensitive partner test account, run real HTTP/mobile regressions for login version increment, valid/expired/repeated logout, wrong subject type, refresh success/failure, network failure, and active-logout no-return-target behavior; resolve the unrelated Infra codegen test baseline separately
