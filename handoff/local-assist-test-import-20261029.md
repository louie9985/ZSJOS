# Registration — local-assist-test-import-20261029
- Environment: local (identity variable/file absent); owner: Codex /root.
- Branch: main; worktree: D:/ZSJ-OS; base: ba3e687e678ce92b67f16615244e47007d0892d4; integration: None.
- Goal: fix missing assertNull import in LeadSubmitterActionServiceTest.
- Non-goals: business behavior, other pending changes, database, deployment.
- Ownership: this record and backend/yudao-module-zsjos/src/test/java/cn/iocoder/yudao/module/zsjos/service/lead/LeadSubmitterActionServiceTest.java (previous home-task-split ownership released).
- Dependencies: existing JUnit; verification: scoped diff and focused Maven test with reactor dependencies.

## Delivery
- Added only the missing JUnit assertNull static import; preserved pending test changes.
- Scoped git diff --check passed. Reactor test compilation succeeded; focused test execution: 9 tests, 7 passed, 2 failed. Both dueAt assertions passed.
- Remaining failures concern historical partner-owner fallback (no task created) and missing-owner rejection (no exception thrown), outside this import repair. No business behavior changed.
- Command: mvn -f backend/pom.xml -pl yudao-module-zsjos -am test -Dtest=LeadSubmitterActionServiceTest -Dsurefire.failIfNoSpecifiedTests=false.
- Ownership released. No commit, deployment or database changes.
