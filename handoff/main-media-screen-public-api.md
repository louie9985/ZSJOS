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
