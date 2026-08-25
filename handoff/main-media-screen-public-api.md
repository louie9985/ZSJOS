# main workstream

- Workstream ID: main-media-screen-public-api
- Goal: Implement unauthenticated IP-whitelisted read-only media screen APIs.
- Non-goals: Frontend screen, write endpoints, cross-tenant aggregation, new dependencies.
- Branch: main
- Worktree: D:\ZSJ-OS
- Base commit: 02961f62360e96a85c734c0b87c34a43dea77309
- Target branch: main
- Ownership scope: backend/yudao-framework/yudao-spring-boot-starter-web/src/main/java/cn/iocoder/yudao/framework/web/config/WebProperties.java; backend/yudao-framework/yudao-spring-boot-starter-web/src/main/java/cn/iocoder/yudao/framework/web/config/YudaoWebAutoConfiguration.java; backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/public/**; backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/service/mediascreen/**; backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/framework/mediascreen/**; backend/yudao-server/src/main/resources/application.yaml; handoff/main-media-screen-public-api.md
- Owner: root
- Dependencies: Existing System APIs, Redis starter, Lead DAL.
- Integration order: framework public prefix, ZSJOS security/query code, application config, tests.
- Verification plan: focused tests, module test/compile, server package.
- Ownership amendment: backend/yudao-framework/yudao-spring-boot-starter-security/** was added to ensure public APIs bypass Authorization parsing entirely.

## Delivery entries

## Active repair: media screen configuration and public API contract

- Workstream ID: `main-media-screen-public-api-repair`
- Goal: repair media-screen configuration binding, return stable public API access errors, add regression coverage, and provide reusable API/development/deployment documentation.
- Non-goals: hardcode a tenant or client IP, weaken the IP-to-tenant allowlist, change statistics semantics, add historical snapshot persistence, restart/deploy the shared backend, change branches, commit, push, or modify unrelated worktree changes.
- Branch: `main`
- Worktree: `D:\ZSJ-OS`
- Base commit: `00d9dbec11411bcac0e2eba6f597d76c72b9230a` plus existing uncommitted user changes
- Target branch: `main`
- Ownership scope: media-screen public Controller, access/configuration framework, and focused tests under `backend/yudao-module-zsjos`; `backend/yudao-server/src/main/resources/application.yaml`; media-screen API/operations documentation; directly affected architecture documentation; this handoff record.
- Owner: Codex `/root`
- Dependencies: existing Yudao web, security, tenant, JSON, Redis, System public APIs, and ZSJOS Lead DAL; no new dependency.
- Integration order: register scope -> repair configuration prefix and validation -> stabilize filter errors -> add binding/access tests -> document API and deployment -> focused/full verification -> append delivery evidence.
- Verification plan: focused media-screen tests; full `yudao-module-zsjos` tests; server package; configuration/JAR inspection; controlled local HTTP checks without changing shared service state; documentation/static diff review.

### Scope extension 2026-08-25 17:30 Asia/Shanghai

- Added ownership scope: `backend/yudao-server/src/main/resources/application-local.yaml` and the directly affected local-development exception in `docs/operations/media-screen-deployment.md`.
- Reason: the user explicitly accepted the exposure risk and requested tenant `1` to allow all IPv4 clients in the active `local` profile, while retaining the common configuration's fail-closed default.
- Non-goals remain: no service stop/start/restart, no production/shared-environment authorization change, and no unrelated configuration edits.

## Delivery 2026-08-24 22:00 Asia/Shanghai
- Branch: main
- Worktree: D:\ZSJ-OS
- HEAD commit: 02961f62360e96a85c734c0b87c34a43dea77309
- User goal: Add unauthenticated, IP-whitelisted, read-only media screen APIs under /public-api.
- Key decisions: Added a dedicated public controller prefix; bypassed Authorization parsing for /public-api; enforced configured IP/CIDR plus IP-to-tenant mapping; reused ZSJOS Lead DAL, System user/department APIs, MaintenanceModeApi, and existing Redis starter; defaulted feature off with empty allowlist; returned unavailable historical snapshots when no persisted snapshot source exists.
- Execution result: Implemented public API mapping, security filter, typed configuration, read-only aggregation service, Redis TTL cache, maintenance status endpoint, history contract, and focused IP/CIDR tests. No frontend or database writes were added.
- Changed files: WebProperties.java; YudaoWebAutoConfiguration.java; TokenAuthenticationFilter.java; YudaoSecurityAutoConfiguration.java; application.yaml; LeadMapper.java; MediaScreenProperties.java; MediaScreenAccessFilter.java; MediaScreenController.java; MediaScreenRespVO.java; MediaScreenQueryService.java; MediaScreenAccessFilterTest.java.
- Verification evidence: mvn -f backend/pom.xml -pl yudao-module-zsjos -am -DskipTests compile passed; mvn -f backend/pom.xml -pl yudao-module-zsjos -Dtest=MediaScreenAccessFilterTest test passed with 3 tests; server dependency graph compile and jar creation passed through yudao-server compile, but Spring Boot repackage could not rename an existing locked ackend/yudao-server/target/yudao-server.jar.original.
- Dependency/integration impact: No new Maven/npm dependency. Production deployment must configure yudao.media-screen and the reverse proxy trust boundary before enabling. Existing unrelated worktree changes remain untouched.
- Remaining work: Add/consume a persisted media-screen snapshot API/table when historical snapshots are available; run real HTTP/Redis/MySQL contract checks in an environment with services; resolve the locked target artifact before full package verification.

## Delivery 2026-08-25 16:29:04 Asia/Shanghai

- Workstream ID: `main-media-screen-public-api-repair`
- Branch: `main`
- Worktree: `D:\ZSJ-OS`
- HEAD commit: `00d9dbec11411bcac0e2eba6f597d76c72b9230a` (no commit created)
- User goal: completely repair the media-screen public API failure seen through the port-3009 development frontend and provide reusable interface and development documentation for other teams.
- Key decisions: retained the existing `yudao.media-screen` YAML/runtime contract and corrected Java binding to it; kept the feature fail-closed with no committed tenant/IP allowlist; required at least one tenant/client CIDR when enabled; preserved the public GET plus IP-to-tenant authorization model; returned stable JSON errors for disabled, invalid tenant, forbidden client, and unsupported method cases; converted media-screen Controller validation failures to stable `code=400`; documented that historical snapshots remain unavailable rather than fabricating history.
- Execution or analysis result: repaired the configuration-prefix mismatch that forced `enabled=false`; prevented enabled-but-empty allowlists from starting; eliminated the misleading HTTP 503 plus `code=401` body for the repaired runtime; verified that the ordinary `tenant-id` header cannot override the filter's validated query tenant; added complete API, Vite/Nginx integration, deployment, environment-variable, verification, troubleshooting, and rollback documentation. The currently running 48080 process was intentionally not restarted, so it still serves the old HTTP 503 plus `code=401` behavior.
- Changed files: `backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/controller/pub/mediascreen/MediaScreenController.java`; `backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/framework/mediascreen/MediaScreenAccessFilter.java`; `backend/yudao-module-zsjos/src/main/java/cn/iocoder/yudao/module/zsjos/framework/mediascreen/MediaScreenProperties.java`; `backend/yudao-module-zsjos/src/test/java/cn/iocoder/yudao/module/zsjos/controller/pub/mediascreen/MediaScreenControllerTest.java`; `backend/yudao-module-zsjos/src/test/java/cn/iocoder/yudao/module/zsjos/framework/mediascreen/MediaScreenAccessFilterTest.java`; `backend/yudao-module-zsjos/src/test/java/cn/iocoder/yudao/module/zsjos/framework/mediascreen/MediaScreenPropertiesTest.java`; `backend/yudao-server/src/main/resources/application.yaml`; `docs/api/media-screen-public-api.md`; `docs/operations/media-screen-deployment.md`; `docs/architecture/system-overview.md`; `docs/architecture/data-and-permission-flow.md`; this handoff file.
- Verification evidence: focused media-screen tests passed 16/16 after compiling all 771 production and 105 test sources; full `yudao-module-zsjos` tests passed 556/556; generated Spring configuration metadata contains the `yudao.media-screen` group; the 28-module server Reactor compiled all production sources and built the ordinary server JAR, but Spring Boot executable repackaging could not rename the Windows-locked `target/yudao-server.jar`; scoped `git diff --check` reported line-ending warnings only and no whitespace errors; a real request to the unrestarted `192.168.2.17:48080` confirmed it still returns the pre-deployment HTTP 503 plus `code=401` body.
- Dependency or integration impact: no new dependency, database/schema/data operation, committed real tenant/IP value, permission mutation, branch/worktree operation, commit, push, deployment, or service restart. Existing unrelated dirty-worktree changes were preserved. The current Java process still owns the old executable JAR handle while the on-disk `backend/yudao-server/target/yudao-server.jar` is the 25 KB ordinary JAR produced before the locked repackage step.
- Remaining work: obtain explicit approval and confirmed tenant/client CIDR, stop PID 41820 on `192.168.2.17:48080`, rebuild the executable Spring Boot JAR, configure and enable `yudao.media-screen`, restart the service, then run the documented 503/400/403/405/200 matrix through both the backend and port-3009 proxy. Persisted historical snapshot support remains a separate future feature.

## Delivery 2026-08-25 17:16:00 Asia/Shanghai

- Workstream ID: `main-media-screen-public-api-repair`
- Branch: `main`
- Worktree: `D:\ZSJ-OS`
- HEAD commit: `00d9dbec11411bcac0e2eba6f597d76c72b9230a` (no commit created)
- User goal: directly complete the media-screen public API repair while keeping the port-3009 frontend contract unchanged.
- Key decisions: aligned the repository default with the documented fail-closed contract by restoring `yudao.media-screen.enabled=false`; kept real tenant and client CIDR values out of source control; restored `limits.max-history-days` under `yudao.media-screen` after the adjacent positioning configuration had displaced it.
- Execution or analysis result: verified that the newly started backend process PID 28268 is running the repaired executable JAR and now returns HTTP 503 with `code=503` and `媒体大屏服务未开启`, proving that the old misleading `code=401` behavior is gone. The remaining 503 is the expected disabled-state response, not a frontend route failure.
- Changed files: `backend/yudao-server/src/main/resources/application.yaml`; this handoff file.
- Verification evidence: focused `MediaScreenControllerTest`, `MediaScreenAccessFilterTest`, and `MediaScreenPropertiesTest` passed 16/16; executable JAR inspection showed Spring Boot loader entries and a size of 503,775,011 bytes; direct HTTP request to `192.168.2.17:48080` returned the repaired disabled-state JSON contract; scoped `git diff --check` reported line-ending warnings only.
- Dependency or integration impact: no dependency, database, permission, branch/worktree, commit, push, or service-operation change. No real tenant or IP authorization value was committed. The running shared service remains disabled pending separate approval for runtime configuration and restart.
- Remaining work: confirm the actual port-3009 proxy/client CIDR (candidate observed previously: `192.168.2.15/32`), authorize updating the shared runtime for tenant 1, then restart PID 28268 and run the documented 503/400/403/405/200 verification matrix through both 48080 and 3009.

## Delivery 2026-08-25 17:28:13 Asia/Shanghai

- Workstream ID: `main-media-screen-public-api-repair`
- Branch: `main`
- Worktree: `D:\ZSJ-OS`
- HEAD commit: `00d9dbec11411bcac0e2eba6f597d76c72b9230a` (no commit created)
- User goal: enable the local media-screen public API for tenant `1`, allow every IPv4 client, and leave the backend restart to the user.
- Key decisions: after the user explicitly accepted the public-data exposure risk, configured the active `local` profile with tenant `1` and CIDR `0.0.0.0/0`; retained the common profile's fail-closed `enabled=false`; documented the broad CIDR as a local-development exception that must not be copied to shared or production environments; did not stop or restart the running backend.
- Execution or analysis result: the source configuration now binds to `enabled=true`, tenant `1`, and `0.0.0.0/0` under the local profile. Added a regression test that loads the actual common and local YAML files and executes the media-screen startup validation. The currently running PID 28268 still returns the expected pre-restart HTTP 503 because its executable JAR predates this local-profile change.
- Changed files: `backend/yudao-server/src/main/resources/application-local.yaml`; `backend/yudao-module-zsjos/src/test/java/cn/iocoder/yudao/module/zsjos/framework/mediascreen/MediaScreenPropertiesTest.java`; `docs/operations/media-screen-deployment.md`; this handoff file.
- Verification evidence: focused Controller, access-filter, and configuration tests passed 17/17, including real YAML binding; scoped `git diff --check` reported line-ending warnings only and no whitespace errors; read-only HTTP verification confirmed PID 28268 remains online and unchanged at HTTP 503 before the requested manual rebuild/restart.
- Dependency or integration impact: no new dependency, database/schema/data operation, branch/worktree operation, commit, push, artifact publication, or service operation. Any IPv4 host that can reach port 48080 will be able to read tenant 1 media-screen data after rebuild and restart.
- Remaining work: the user must stop PID 28268, rebuild the executable JAR so the changed `application-local.yaml` is packaged, start the backend, and run the documented success/error matrix through 48080 and the port-3009 proxy.
