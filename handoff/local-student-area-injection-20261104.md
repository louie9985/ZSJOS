# local-student-area-injection-20261104
- Environment: local (fallback); owner: Codex; branch: main; worktree: D:\ZSJ-OS; base: f6712f0bebcb061664029f8e102e0ee634329406.
- Goal: correct StudentInfoFormService Resource bean selection; preserve existing changes.
- Non-goals: business logic, database changes, service restart, deployment.
- Ownership: StudentInfoFormService.java injection annotation only; StudentInfoFormInjectionTest.java; this record. Prior identity-area workstream delivered.
- Dependencies: existing identity-area implementation; target branch/integration order: None.
- Verification: Spring resource injection regression with both beans present; focused Maven test and server package.

## Delivery
- Explicit Resource name now selects studentIdentityAreaService, preserving all pre-existing business edits.
- Spring CommonAnnotationBeanPostProcessor regression: 1 test passed, 0 failures/errors; conflicting field-name bean present.
- Server reactor package passed: mvn -f backend/pom.xml -pl yudao-server -am -DskipTests package -q. Updated backend/yudao-server/target/yudao-server.jar.
- Scoped git diff --check passed. No dependencies added.
- Full application startup remains unverified: no shared service restart or database operation performed. No commit/push. Ownership released.
