# Latest archive matching

## Registration 2026-11-09 Beijing time

- Environment local, ZSJOS_AGENT_ENV unset and /etc/zsjos/agent-environment absent. Owner current Codex chat. Workstream local-exam-live-archive-match-20261109.
- Branch main, worktree D:/ZSJ-OS, base 7a7cf5e14bef1ff193de5c4cc39af8a5255a7d5c. Integration None.
- Goal match new unbound exam submissions against latest effective service archives within active task roster, including historical null/stale member hashes.
- Ownership ExamCollectionMatchingService.java, ExamCollectionMatchingTest.java, docs/api/exam-material-collection.md and this record. Preserve existing documentation changes.
- Non-goals retrospective reassignment, database writes, deployment, frontend/API changes and publication. Existing confirmed/archive bindings remain stable.
- Dependencies existing archive reader, token hashing and test facilities. Verification focused current-source compilation and matching tests, scoped diff check.

## Delivery 2026-11-09 Beijing time

- Automatic first binding reads latest effective archive via StudentServiceArchiveStore.current for distinct active roster services and compares current identity HMAC. Historical null or stale member hashes no longer affect selection. Existing bindings and archived associations retain their explicit correction boundary.
- Updated API documentation with the user-confirmed override of notification-time matching. Existing exception rows retain the manual resolution entry point; fresh submissions run live archive matching.
- Verification current changed service/test sources compiled with javac using repository classes/dependencies, JUnit launcher passed all 7 matching tests. Covers empty/stale snapshots, changed/empty current identity, unique/ambiguous matches, confirmed bindings and closed service. Scoped git diff --check passed. Test outputs isolated in C:/Users/EDY/AppData/Local/Temp/exam-live-match-20261109.
- Full Maven reactor, running-service HTTP acceptance and deployment unverified. No database mutation, service restart or Git publication. Per-service archive reads are used by the existing archive reader. Ownership released.
