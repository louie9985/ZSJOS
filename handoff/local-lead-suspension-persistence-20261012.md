# Lead suspension persistence repair

## Registration — 2026-10-12 Beijing
- Owner: /root; environment: local (ZSJOS_AGENT_ENV unset; environment file absent); branch: main; worktree: D:/ZSJ-OS; base: c82c8605d86aef5c8f4cd328600bc7c3efc2bf90; integration: None.
- Goal: persist suspension clearing on restore, transfer and valid qualification; repair the user-identified test Lead only if its connection and current state can be verified.
- Non-goals: no permission, order validation, dependency, branch, commit, deployment or service lifecycle changes.
- Ownership: LeadMapper.java; suspension-clearing call sites in LeadQualificationServiceImpl.java; LeadQualificationServiceImplTest.java; new LeadSuspensionMapperTest.java; docs/operations/lead-suspension-repair.md; this record. Prior appeal-cycle work delivered; preserve its modifications.
- Dependencies: existing transactional Lead lock, MyBatis-Plus update wrapper and tenant/logical-delete facilities. Test environment connection not yet available.
- Verification: focused service and real mapper/H2 persistence tests, scoped diff; read-only target inspection before any bounded data correction.

## Delivery — 2026-10-12 16:00 Beijing
- Context: registration metadata and HEAD unchanged; owner /root. User clarified that actual test-state investigation is unnecessary; delivery is limited to source repair, no environment data mutation.
- Added LeadMapper.updateClearingSuspension: one entity UPDATE with explicit suspended_at NULL. Restore, transfer and judgeValid use it under their existing transaction and Lead lock. Order guards and global null-update strategy unchanged; earlier local edits preserved.
- Changed files: registered mapper, qualification service/test, new LeadSuspensionMapperTest, operations document and this record. No dependencies or integration operations.
- Verification: Maven reactor compilation and 14 focused tests passed (13 qualification service, 1 actual MyBatis-Plus/H2 mapper test); persistence checks cover submitted/valid transitions, rollback, unrelated rows and logical deletion. Scoped diff whitespace check passed. Initial editing typo corrected; mapper fixture audit columns added after the first persistence test exposed the incomplete fixture.
- Remaining boundary: no test database repair, deployment, restart or authenticated order submission performed. Existing stale rows require separate data correction; source changes do not retroactively clear them. Ownership released.
