# Delivery performance Bean wiring

## Registration
- ID: local-delivery-bean-wiring-20261018; owner: Codex /root.
- Environment: local (environment variable unset and environment file absent).
- Branch: main; worktree: D:/ZSJ-OS; base: 6bf0ce81a182b0a5ea040c8536a1ae7fad485e5e.
- Goal: fix delivery statistics startup failure caused by Resource name resolution selecting the sales order details service.
- Non-goals: business-rule changes, database changes, deployment, restart, Git publication.
- Ownership: DeliveryPerformanceStatisticsService.java; new DeliveryPerformanceWiringTest.java in the same service test package; this record. Existing unrelated changes are preserved.
- Dependencies: existing Spring context and Mockito test facilities; no new dependency.
- Verification: focused Spring context regression with both sales and delivery services present, module compilation in a separate source snapshot; scoped diff checks.
- Target branch/integration order: None.

## Delivery - 2026-10-18 (Asia/Shanghai)
- Explicitly select deliveryOrderDetailsService at the statistics service Resource injection point. The sales orderDetailsService remains separate; no business/API contract changes.
- Added DeliveryPerformanceWiringTest: a real Spring context registers both concrete order-detail services and the statistics service, with mocked downstream dependencies. It verifies the injected instance is the delivery service and the sales Bean remains independently available.
- First test run exposed fixture initialization of mocked concrete dependencies; these were registered as initialized singletons. Final regression: 1 test, 0 failures, 0 errors, 0 skipped.
- Verification used a separate source copy at D:/ZSJ-OS/tmp/delivery-bean-wiring-20261018/backend to preserve shared build outputs. Module reactor test/compile passed (verification-final.log); server reactor package with -DskipTests passed (server-package.log). Source scoped diff check passed.
- Commands: mvn -f backend/pom.xml -pl yudao-module-zsjos -am test -Dtest=DeliveryPerformanceWiringTest -Dsurefire.failIfNoSpecifiedTests=false; mvn -f backend/pom.xml -pl yudao-server -am package -DskipTests (both in the verification directory).
- Full application startup, real API requests and deployment are unverified. No service restart, database write, commit or push was performed. Ownership released.
